package com.butcher.render.art;

import java.awt.image.BufferedImage;

/**
 * Hellion's textures made from the game's own: the fire.
 *
 * The fire is read from the player's game at runtime ({@code textures/block/fire_0.png}, through the
 * resource manager, so a resource pack's fire is the one used) and never bundled. It cannot simply be
 * adopted by its name, as the terracotta is: the game hands an animated block texture over as its first
 * frame only, 16 x 16, and the scene's frame rects then sampled a half-pixel sliver of it, stretched -
 * the streaks the user saw in game (0.2.59). So the strip is read here and normalised.
 */
public final class HellionArt {
    private HellionArt() {}

    /** The frames every strip is normalised to, 16 x 16 each, stacked: 16 x 512. */
    public static final int FRAMES = 32, FRAME = 16;

    /**
     * Any fire strip - vanilla's 16 x 512, or a pack's at another size or frame count - as 32 square
     * 16 px frames, nearest-neighbour, each slot taking the source frame at the same point in its cycle.
     * Null in, a blank strip out.
     */
    public static BufferedImage fireStrip(BufferedImage src) {
        BufferedImage out = new BufferedImage(FRAME, FRAME * FRAMES, BufferedImage.TYPE_INT_ARGB);
        if (src == null || src.getWidth() <= 0) return out;
        int sw = src.getWidth();
        int frames = Math.max(1, src.getHeight() / sw);
        for (int f = 0; f < FRAMES; f++) {
            int from = f * frames / FRAMES;
            for (int y = 0; y < FRAME; y++) {
                for (int x = 0; x < FRAME; x++) {
                    int sx = x * sw / FRAME, sy = from * sw + y * sw / FRAME;
                    out.setRGB(x, f * FRAME + y, src.getRGB(sx, Math.min(src.getHeight() - 1, sy)));
                }
            }
        }
        return out;
    }

    /**
     * The same fire in greys, so a tint gives it any colour outright - the shield's (the user: "change its
     * colour literally to match the current shield"). Brightness is kept, stretched so the hottest texel
     * is white, with a little lift so the dark red edges do not go to black under a dark tint.
     */
    public static BufferedImage greyFire(BufferedImage strip) {
        int w = strip.getWidth(), h = strip.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        float max = 1f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = strip.getRGB(x, y);
                if ((c >>> 24) < 8) continue;
                max = Math.max(max, lum(c));
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = strip.getRGB(x, y);
                int a = c >>> 24;
                if (a == 0) continue;
                float v = (float) Math.pow(lum(c) / max, 0.75);
                int g = Math.round(40f + 215f * v);
                out.setRGB(x, y, a << 24 | g << 16 | g << 8 | g);
            }
        }
        return out;
    }

    private static float lum(int c) {
        return 0.30f * (c >> 16 & 255) + 0.59f * (c >> 8 & 255) + 0.11f * (c & 255);
    }
}
