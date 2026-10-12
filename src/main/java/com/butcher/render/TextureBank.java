package com.butcher.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.butcher.ButcherClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Uploads runtime-drawn {@link BufferedImage}s to the game as textures, once, and hands
 * back the {@link Identifier} to blit them with.
 *
 * This is what lets the art live in Java instead of in committed PNGs (see
 * {@link com.butcher.render.art.Paint} for why that matters). Register a supplier
 * under a name at startup; the image is drawn and uploaded the first time something asks
 * for it, and every later call is a map lookup.
 *
 * Lazy on purpose. Drawing all of the skins costs a few milliseconds of Graphics2D work,
 * and there is no reason to spend it during mod init on a client that may never see a
 * Scatha - the first frame of the first cutscene is soon enough, and by then the texture
 * manager is definitely up.
 */
public final class TextureBank {

    private record Entry(Supplier<BufferedImage> source, Identifier id, int width, int height) {}

    private static final Map<String, Supplier<BufferedImage>> PENDING = new HashMap<>();
    private static final Map<String, Entry> UPLOADED = new HashMap<>();

    private TextureBank() {}

    /** Declares a texture. Nothing is drawn or uploaded until {@link #id} is first called. */
    public static void register(String name, Supplier<BufferedImage> source) {
        PENDING.put(name, source);
    }

    /** The Identifier to blit, drawing and uploading on first use. Null if it could not be made. */
    public static Identifier id(String name) {
        Entry entry = entry(name);
        return entry == null ? null : entry.id();
    }

    public static int width(String name) {
        Entry entry = entry(name);
        return entry == null ? 1 : entry.width();
    }

    public static int height(String name) {
        Entry entry = entry(name);
        return entry == null ? 1 : entry.height();
    }

    private static Entry entry(String name) {
        Entry existing = UPLOADED.get(name);
        if (existing != null) return existing;

        Supplier<BufferedImage> source = PENDING.get(name);
        if (source == null) {
            ButcherClient.LOGGER.error("[Butcher] no texture registered under \"{}\"", name);
            return null;
        }
        try {
            // Written out to config/butcher/textures/ and, if the player has
            // edited that file, replaced by theirs. See ArtOverrides for how an edit is
            // told apart from a file this mod wrote itself.
            BufferedImage img = ArtOverrides.resolve(name, source.get());
            Entry made = new Entry(source, upload(name, img), img.getWidth(), img.getHeight());
            UPLOADED.put(name, made);
            PENDING.remove(name);
            return made;
        } catch (Throwable t) {
            // A texture that cannot be drawn must not take the cutscene - or the HUD - down
            // with it. Callers treat a null id as "skip this piece".
            ButcherClient.LOGGER.error("[Butcher] could not build texture \"{}\"", name, t);
            PENDING.remove(name);
            return null;
        }
    }

    private static Identifier upload(String name, BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        NativeImage image = new NativeImage(w, h, false);
        // BufferedImage hands out ARGB; NativeImage wants ABGR, so red and blue swap.
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            img.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int argb = row[x];
                int a = argb >>> 24 & 0xFF;
                int r = argb >> 16 & 0xFF;
                int g = argb >> 8 & 0xFF;
                int b = argb & 0xFF;
                image.setPixelABGR(x, y, a << 24 | b << 16 | g << 8 | r);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath(ButcherClient.MOD_ID, "generated/" + name);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> name, image));
        return id;
    }

    /**
     * Registers an Identifier the game already owns under one of our names.
     *
     * The Scatha's real skin is uploaded and managed by Minecraft's own skin manager, not
     * by us, so there is nothing to draw or upload - but callers still want to refer to it
     * by a stable name alongside the textures we do generate. Cheap and idempotent.
     */
    public static void adopt(String name, Identifier id) {
        adopt(name, id, ADOPTED_SIZE, ADOPTED_SIZE);
    }

    /** {@link #adopt} for a texture that is not 64 x 64 - the game's armour layers are 64 x 32. */
    public static void adopt(String name, Identifier id, int width, int height) {
        Entry existing = UPLOADED.get(name);
        if (existing != null && existing.id().equals(id)) return;
        UPLOADED.put(name, new Entry(null, id, width, height));
    }

    /** Player skins are 64x64; adopted textures are assumed to be too. */
    private static final int ADOPTED_SIZE = 64;

    /** Forgets everything, so the next use redraws. Only for "/butcher reload". */
    public static void invalidate() {
        for (Map.Entry<String, Entry> e : UPLOADED.entrySet()) {
            // Adopted entries have no source of ours to redraw from; they come back
            // through ScathaSkins the next time it is asked for a name.
            if (e.getValue().source() != null) PENDING.put(e.getKey(), e.getValue().source());
        }
        UPLOADED.clear();
    }
}
