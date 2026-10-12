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
 * An image made on the CPU each frame - the rasterised Ender Dragon - uploaded and drawn once, unsmoothed.
 *
 * The texture only grows (to the next multiple of 128), so a dragon that changes size every frame does not
 * re-register a texture every frame; only the top-left {@code iw x ih} is written and drawn. Do not close the
 * old texture on growth: {@code TextureManager.register} closes it already (see PixelLayer).
 * Render thread only.
 */
public final class RasterImage {

    private final Identifier id;
    private DynamicTexture texture;
    private NativeImage image;
    private int cw, ch;

    public RasterImage(String name) {
        this.id = Identifier.fromNamespaceAndPath(ButcherClient.MOD_ID, "raster/" + name);
    }

    /** Draws the first {@code iw * ih} pixels of {@code argb} (straight ARGB) over the GUI rect x, y, w, h. */
    public void draw(GuiGraphicsExtractor g, int[] argb, int iw, int ih, float x, float y, float w, float h) {
        drawAffine(g, argb, iw, ih, x, y, w, 0f, 0f, h);
    }

    /** {@link #draw} onto a parallelogram: corner (ox, oy), top edge (ux, uy), left edge (vx, vy). */
    public void drawAffine(GuiGraphicsExtractor g, int[] argb, int iw, int ih, float ox, float oy, float ux, float uy, float vx, float vy) {
        if (iw <= 0 || ih <= 0) return;
        if (texture == null || iw > cw || ih > ch) {
            cw = Math.max(cw, (iw + 127) / 128 * 128);
            ch = Math.max(ch, (ih + 127) / 128 * 128);
            image = new NativeImage(cw, ch, false);
            texture = new DynamicTexture(() -> "butcher raster", image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
        for (int yy = 0; yy < ih; yy++) {
            int row = yy * iw;
            for (int xx = 0; xx < iw; xx++) {
                int c = argb[row + xx];
                // ARGB to the ABGR NativeImage wants.
                image.setPixelABGR(xx, yy, (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16));
            }
        }
        texture.upload();
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.mul(new org.joml.Matrix3x2f(ux / iw, uy / iw, vx / ih, vy / ih, ox, oy));
        g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, iw, ih, cw, ch);
        pose.popMatrix();
    }
}
