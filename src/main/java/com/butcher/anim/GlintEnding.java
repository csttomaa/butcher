package com.butcher.anim;

import static com.butcher.anim.ChopTimeline.*;

/**
 * Chimera I's ending, "The Glint" - pure, like the rest of {@link ChopTimeline}. The user's pick
 * (option B) after the three beasts were let go: the enchantment itself, with nothing but the
 * game's own visual language - its item glint and its enchanting table's glyphs.
 *
 * <pre>
 *  SPLIT        the Inquisitor's head falls open
 *  +150 - 1100  the glint spills out of the split over the halves and the board
 *  +800 - 2800  the enchanting glyphs drift up out of the head and circle it; the glint creeps
 *               in from the edges of the screen
 *  +2800 - 3600 everything is pulled into one point above the head: the glyphs spiral in,
 *               faster and tighter, the glint drains out of the world after them
 *  +3600        a flash: the enchanted book (ARRIVE +3700)
 * </pre>
 */
public final class GlintEnding {

    public static final long SEEP = SPLIT + 150, RISE = SPLIT + 800, SPIRAL = SPLIT + 2800, CONVERGE = SPLIT + 3600,
            ARRIVE = SPLIT + 3700, END = ARRIVE + 5000;
    public static final int GLYPHS = 84;
    /** The point everything is drawn into, where the book appears. */
    public static final float POINT_Y = 11f;

    private final ChopTimeline tl;

    GlintEnding(ChopTimeline tl) {
        this.tl = tl;
    }

    /** How strongly a surface at (x, y, z) glints: spreading out from the split, drained by the spiral. */
    public float glint(float x, float y, float z, long t) {
        float d = (float) Math.sqrt(x * x + y * y + z * z);
        float on = ease((t - SEEP - d * 70f) / 260f);
        float pulse = 0.85f + 0.15f * (float) Math.sin(t / 140.0 - d * 0.6);
        return 0.8f * on * pulse * drain(t);
    }

    /** The glint left in the world as the spiral pulls it out, 1 .. 0. */
    public float drain(long t) {
        return 1f - ease((t - SPIRAL - 200) / (float) (CONVERGE - SPIRAL - 200));
    }

    /** How far in from the screen's edges the glint has crept, 0..1. */
    public float edges(long t) {
        return ease((t - RISE) / 1400f) * drain(t);
    }

    /** Where the glint texture is scrolled to, texels: it slides diagonally, as the game's does. */
    public static int[] scroll(long t) {
        int u = (int) ((t / 14) % 96), v = (int) ((t / 22) % 96);
        return new int[]{u, v};
    }

    /**
     * Glyph {@code k}: {x, y, z, size, which letter 0..25, alpha}, or null. Out of the head, up and
     * round it in a slow ring; then spiralling in to the point.
     */
    public float[] glyph(int k, long t) {
        java.util.Random r = new java.util.Random(tl.seed() * 53 + k * 211L);
        long born = RISE + (long) (r.nextFloat() * 1600);
        if (t < born || t >= CONVERGE) return null;
        float sec = (t - born) / 1000f;
        double a0 = r.nextDouble() * Math.PI * 2;
        float ring = 6f + r.nextFloat() * 5f, height = 3f + r.nextFloat() * 8f;
        float rise = ease(sec / 0.9f);
        double a = a0 + sec * (0.6 + 0.4 * r.nextFloat());
        float rad = ring * rise, y = 2f + (height - 2f) * rise + 0.6f * (float) Math.sin(sec * 2.3 + k);
        // The spiral: tighter and faster, into the point.
        float in = easeIn((t - SPIRAL) / (float) (CONVERGE - SPIRAL));
        if (in > 0f) {
            a += in * in * 9.0;
            rad *= 1f - in;
            y += (POINT_Y - y) * in;
        }
        float alpha = ease(sec / 0.3f);
        return new float[]{(float) Math.cos(a) * rad, y, (float) Math.sin(a) * rad, 0.9f + 0.4f * r.nextFloat(), r.nextInt(26), alpha};
    }

    /** The flash as everything meets, 0..1. */
    public float flash(long t) {
        return t < CONVERGE ? 0f : (float) Math.exp(-(t - CONVERGE) / 200.0);
    }

    // ------------------------------------------------------------------ camera

    public float zoom(long t) {
        return 1.26f - 0.2f * ease((t - RISE) / 1200f) + 0.3f * easeIn((t - SPIRAL) / (float) (CONVERGE - SPIRAL))
                - 0.3f * ease((t - CONVERGE) / 700f);
    }

    public float[] focus(long t) {
        float y = HEAD + 3f + 3f * ease((t - RISE) / 1500f);
        y += (POINT_Y - 1f - y) * ease((t - SPIRAL) / (float) (ARRIVE - SPIRAL));
        return new float[]{0f, y, 0f};
    }

    public float yaw(long t) {
        return -20f * ease((t - SPLIT) / 3500f);
    }
}
