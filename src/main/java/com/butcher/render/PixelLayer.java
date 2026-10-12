package com.butcher.render;

import com.butcher.ButcherClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * A whole screen of pixel art drawn as ONE image: the frost and the crystal veins paint their cells
 * into a small buffer - one texel per grid cell, about 213 x 120 - which is uploaded and blitted once,
 * scaled up with no smoothing, so every cell is still a crisp square.
 *
 * Why: they used to send every cell to the GUI as its own filled rectangle, twice with the outline.
 * At full growth that was thousands of rectangles a frame, more with every cut - the user's frame
 * rate fell with each slice of the Scatha (2026-10-02). Now it is one blit, whatever the growth.
 *
 * Both ThawFrost.Fill and CrystalVeins.Fill, so the pure classes - and their offline previews - are
 * unchanged. Everything they draw must be squares on the grid; that is what they already draw.
 * Render thread only. One instance per purpose, reused across cutscenes.
 */
public final class PixelLayer implements ThawFrost.Fill, CrystalVeins.Fill {

    private final String name;
    private Identifier id;
    private DynamicTexture texture;
    private NativeImage image;
    private int gw, gh, unit = 1;
    private int[] buf = new int[0];
    private boolean drawn;

    public PixelLayer(String name) {
        this.name = name;
    }

    /** Starts a frame for a {@code w x h} GUI screen on a grid of {@code unit} GUI pixels. */
    public void begin(int w, int h, int unit) {
        this.unit = Math.max(1, unit);
        int nw = (w + this.unit - 1) / this.unit, nh = (h + this.unit - 1) / this.unit;
        if (nw != gw || nh != gh || texture == null) {
            gw = nw;
            gh = nh;
            // No close here: registering under the same id closes the old texture itself (26.2's
            // TextureManager.register -> safeClose), and closing it twice is not safe.
            image = new NativeImage(gw, gh, false);
            texture = new DynamicTexture(() -> name, image);
            id = Identifier.fromNamespaceAndPath(ButcherClient.MOD_ID, "layer/" + name);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            buf = new int[gw * gh];
        }
        java.util.Arrays.fill(buf, 0);
        drawn = false;
    }

    /** A square (or rect) of whole cells, blended over what is already there. */
    @Override
    public void fill(float x, float y, float w, float h, int argb) {
        int a = argb >>> 24;
        if (a == 0) return;
        int x0 = Math.round(x / unit), y0 = Math.round(y / unit);
        int x1 = Math.max(x0 + 1, Math.round((x + w) / unit)), y1 = Math.max(y0 + 1, Math.round((y + h) / unit));
        for (int cy = Math.max(0, y0); cy < Math.min(gh, y1); cy++) {
            for (int cx = Math.max(0, x0); cx < Math.min(gw, x1); cx++) {
                int i = cy * gw + cx;
                buf[i] = over(argb, buf[i]);
                drawn = true;
            }
        }
    }

    /** Uploads the frame and draws it over the whole screen. */
    public void end(GuiGraphicsExtractor g) {
        if (!drawn || texture == null) return;
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                int c = buf[y * gw + x];
                // ARGB to the ABGR NativeImage wants.
                image.setPixelABGR(x, y, (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16));
            }
        }
        texture.upload();
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.scale(unit, unit);
        g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, gw, gh, gw, gh);
        pose.popMatrix();
    }

    /** Source over destination, both straight (non-premultiplied) ARGB. */
    private static int over(int src, int dst) {
        int sa = src >>> 24, da = dst >>> 24;
        if (sa == 255 || da == 0) return src;
        float s = sa / 255f, d = da / 255f * (1f - s);
        float a = s + d;
        int r = Math.round((((src >> 16) & 255) * s + ((dst >> 16) & 255) * d) / a);
        int gg = Math.round((((src >> 8) & 255) * s + ((dst >> 8) & 255) * d) / a);
        int b = Math.round(((src & 255) * s + (dst & 255) * d) / a);
        return (Math.round(a * 255) << 24) | (r << 16) | (gg << 8) | b;
    }
}
