package com.butcher.anim;

import static com.butcher.anim.ChopTimeline.*;

/**
 * The Crown of Greed's ending, "Midas" - pure, like the rest of {@link ChopTimeline}. A cheap
 * drop (about 1.8M), so a short one: the user asked for less animation on the cheap drops.
 *
 * <pre>
 *  SPLIT        King Minos's head falls open
 *  +200 - 1100  gold washes out of the cut over the halves and the board, pixel by pixel
 *  +1150 - 1550 a shine sweeps across it
 *  +1700        the golden halves burst into coins, and the Crown of Greed - the game's own
 *               golden helmet - pops up out of the split, turning
 *  +2600        it becomes the vanilla icon and is presented (ARRIVE), coins raining down while
 *               its price counts up
 * </pre>
 *
 * The gold is a time per point in the world - {@link #gilded} - so it spreads from the cut by
 * distance.
 */
public final class MidasEnding {

    public static final long CREEP = SPLIT + 200, SHINE = SPLIT + 1150, COLLAPSE = SPLIT + 1700, TOPPED = SPLIT + 2250,
            ARRIVE = SPLIT + 2600, END = ARRIVE + 3600;
    /** How fast the gold spreads, ms per texel of distance from the cut. */
    public static final float MS_PER_TEXEL = 60f;
    public static final int GOLD = 0xF5C542, GOLD_LIGHT = 0xFFE88A, GOLD_DEEP = 0xB07A14;
    /** Where the crown ends up. */
    public static final float HERO_Y = 11f;
    public static final int COINS = 90;

    private final ChopTimeline tl;

    MidasEnding(ChopTimeline tl) {
        this.tl = tl;
    }

    /** When the point (x, y, z) turns to gold: outwards from the cut, a little ragged. */
    public long gilded(float x, float y, float z, int cell) {
        float d = (float) Math.sqrt(x * x + (y - 0.3f) * (y - 0.3f) + z * z);
        int h = (cell * 73856093) ^ (int) (tl.seed() * 19349663);
        h ^= h >>> 13;
        return CREEP + Math.round(d * MS_PER_TEXEL) + ((h & 0x7FFFFFFF) % 120);
    }

    /** The shine sweeping across the gold: where the band is, 0..1 across the screen, or -1. */
    public float shine(long t) {
        if (t < SHINE || t > SHINE + 400) return -1f;
        return (t - SHINE) / 400f;
    }

    /** The crown: {y, turn (radians), alpha}. It pops up out of the split, turning once. */
    public float[] crown(long t) {
        if (t < COLLAPSE) return new float[]{-2f, 0f, 0f};
        float u = (t - COLLAPSE) / (float) (TOPPED - COLLAPSE);
        float y = -1f + (HERO_Y + 1f) * easeOutBack(u);
        float turn = (float) (Math.PI * 2) * ease(u) + 0.3f * (float) Math.sin((t - TOPPED) / 500.0) * ease((t - TOPPED) / 300f);
        return new float[]{y, turn, 1f};
    }

    /** Are the golden halves still there? They burst into coins at COLLAPSE. */
    public boolean standing(long t) {
        return t < COLLAPSE;
    }

    /** Coin {@code k} of the burst: {x, y, z, spin}, or null. From the two open halves. */
    public float[] coin(int k, long t) {
        if (t < COLLAPSE) return null;
        java.util.Random r = new java.util.Random(tl.seed() * 31 + k * 7919L);
        float side = k % 2 == 0 ? -1f : 1f;
        float x0 = side * (2f + r.nextFloat() * 5f), y0 = r.nextFloat() * 3f, z0 = (r.nextFloat() - 0.5f) * 8f;
        float sec = (t - COLLAPSE - (k % 4) * 15) / 1000f;
        if (sec < 0f) sec = 0f;
        float vx = side * (2f + r.nextFloat() * 6f), vz = (r.nextFloat() - 0.5f) * 6f, vy = 7f + r.nextFloat() * 10f;
        float y = y0 + vy * sec - 30f * sec * sec;
        if (y < -45f) return null;
        return new float[]{x0 + vx * sec, y, z0 + vz * sec, (r.nextFloat() * 6f + 3f) * sec + r.nextFloat() * 6f};
    }

    // ------------------------------------------------------------------ camera

    public float zoom(long t) {
        return 1.26f + 0.1f * ease((t - CREEP) / 900f) - 0.22f * ease((t - COLLAPSE) / 700f);
    }

    public float[] focus(long t) {
        float y = HEAD + 3f;
        y += (HERO_Y - 1f - y) * ease((t - COLLAPSE) / (float) (ARRIVE - COLLAPSE));
        return new float[]{0f, y, 0f};
    }

    public float yaw(long t) {
        return -14f * ease((t - SPLIT) / 3000f);
    }
}
