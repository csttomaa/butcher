package com.butcher.render.art;

import java.awt.image.BufferedImage;

/**
 * The textures "Eruption" makes from the game's own, at runtime (never bundled - the game's are read through its
 * resource manager, so a resource pack's lava and magma are the ones used).
 */
public final class LavaArt {
    private LavaArt() {}

    /**
     * The magma block's bright cracks as a white mask: alpha from how bright each texel is above the block's dark
     * body. Laid over the magma and tinted with the heat, the cracks glow whatever the vent's heat is - and a bust's
     * go dark.
     */
    public static BufferedImage crackMask(BufferedImage magma) {
        int w = magma.getWidth(), h = magma.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = magma.getRGB(x, y);
                int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
                float lum = (0.5f * r + 0.4f * g + 0.1f * b) / 255f;
                // Only the bright cracks: a wide ramp took in the block's mid-tones and drew pale blotches.
                float a = Math.max(0f, Math.min(1f, (lum - 0.42f) / 0.26f));
                int ai = Math.round(a * 255);
                out.setRGB(x, y, ai == 0 ? 0 : ai << 24 | 0xFFFFFF);
            }
        }
        return out;
    }

    /** The lava, grey and bright: tinted by the heat, it shows any temperature, white-hot included. */
    public static BufferedImage greyLava(BufferedImage lava) {
        int w = lava.getWidth(), h = lava.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = lava.getRGB(x, y);
                int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
                float lum = (0.45f * r + 0.45f * g + 0.1f * b) / 255f;
                int v = Math.round(255 * Math.min(1f, 0.3f + 0.9f * lum));
                out.setRGB(x, y, 0xFF000000 | v << 16 | v << 8 | v);
            }
        }
        return out;
    }

    /** An icon's pixels as ARGB (16 x 16 expected, any size taken). */
    public static int[] pixels(BufferedImage img) {
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    }

    /**
     * An animated block texture (a vertical strip of square frames, any size - a resource pack's too) as exactly
     * {@code frames} frames of 16 x 16, nearest-sampled: frame k is the source's frame k (wrapping if it has fewer).
     * The scene's frame rects assume 16 px frames.
     */
    public static BufferedImage strip(BufferedImage src, int frames) {
        BufferedImage out = new BufferedImage(16, 16 * frames, BufferedImage.TYPE_INT_ARGB);
        if (src == null) return out;
        int w = src.getWidth(), have = Math.max(1, src.getHeight() / Math.max(1, w));
        for (int k = 0; k < frames; k++) {
            int f = k % have;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    out.setRGB(x, k * 16 + y, src.getRGB(x * w / 16, f * w + y * w / 16));
                }
            }
        }
        return out;
    }
}
