package com.butcher.anim;

import static com.butcher.anim.ChopTimeline.*;

/**
 * Shimmering Wool's ending, "The Golden Fleece" - pure, like the rest of {@link ChopTimeline}.
 * The quiet one, on purpose: no impacts, nothing thrown.
 *
 * <pre>
 *  SPLIT        King Minos's head falls open
 *  +300 - 2300  golden fibres unravel out of it and drift up, swaying, like strands in a
 *               breeze; the halves thin away into them
 *  +2300 - 4200 the fibres are drawn round a spindle point above the board and wound onto it,
 *               tighter and faster, into a growing ball; the board sinks away beneath
 *  +4200 - 4700 the ball squares off into the block of wool
 *  +4700        it settles, softly (ARRIVE): a squash, a puff of fluff, sparkles, a sheen
 * </pre>
 *
 * A fibre is a curve of {@link #POINTS} points, computed whole every frame from its own seed,
 * so it cannot jump between phases: the free drift and the winding are blended, not switched.
 */
public final class FleeceEnding {

    public static final long UNRAVEL = SPLIT + 300, WIND = SPLIT + 2300, WOUND = SPLIT + 4200, SQUARE = SPLIT + 4200,
            ARRIVE = SPLIT + 4700, END = SPLIT + 9900;
    public static final int FIBRES = 34, POINTS = 12;
    /** The spindle: where the fibres are wound, and where the wool ends up. */
    public static final float SPIN_Y = 12f, BALL = 3.4f;
    public static final int GOLD = 0xF2C94C, GOLD_LIGHT = 0xFFF1B0, GOLD_DEEP = 0xB8861E;

    private final ChopTimeline tl;

    FleeceEnding(ChopTimeline tl) {
        this.tl = tl;
    }

    /** Fibre {@code k}'s own numbers: {base x, base z, phase, length, turns offset, lean}. */
    private float[] fibre(int k) {
        java.util.Random r = new java.util.Random(tl.seed() * 977 + k * 31L);
        float side = k % 3 == 0 ? 0f : (k % 3 == 1 ? -1f : 1f);
        // From the split (side 0) or the cut face of either half.
        float bx = side == 0f ? (r.nextFloat() - 0.5f) * 1.2f : side * (2.4f + r.nextFloat() * 2.2f);
        float bz = (r.nextFloat() - 0.5f) * 6f;
        return new float[]{bx, bz, r.nextFloat() * 6.28f, 10f + r.nextFloat() * 7f, r.nextFloat() * 6.28f, (r.nextFloat() - 0.5f) * 0.8f};
    }

    // ------------------------------------------------------------------ the ram in the sky

    /**
     * The Golden Fleece's ram as a constellation, in the sky's plane (x to the viewer's right, y
     * up), texels: its stars, and the lines between them in the order they are drawn - horn, head,
     * back, legs.
     */
    public static final float[][] STARS = {
            {6.8f, 2.4f}, {5.9f, 4.1f}, {4.9f, 5.0f}, {3.7f, 5.5f}, {2.9f, 4.7f}, {3.5f, 3.9f}, {4.3f, 4.4f},
            {4.1f, 2.4f}, {0.2f, 3.3f}, {-4.8f, 3.0f}, {-6.1f, 3.8f}, {-4.4f, 0.8f}, {2.1f, 0.8f},
            {2.6f, -2.6f}, {-3.9f, -2.6f}, {4.7f, 1.2f}};
    public static final int[][] LINES = {
            {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}, {0, 1}, {0, 15}, {15, 7}, {7, 1}, {7, 8}, {8, 9}, {9, 10},
            {9, 11}, {11, 12}, {12, 15}, {12, 13}, {11, 14}};
    /** Where the constellation hangs, and how big it is. */
    public static final float SKY_Y = 20f, SKY_SCALE = 1.6f;
    public static final long SKY_IN = SPLIT + 700, LINE_MS = 70;

    /** How much of line {@code k} is drawn, 0..1. */
    public float line(int k, long t) {
        return clamp((t - (SKY_IN + k * LINE_MS)) / 160f);
    }

    /** How lit star {@code i} is: it lights with the first line that reaches it. */
    public float star(int i, long t) {
        for (int k = 0; k < LINES.length; k++) {
            if (LINES[k][0] == i || LINES[k][1] == i) return ease((t - (SKY_IN + k * LINE_MS)) / 200f);
        }
        return 0f;
    }

    /**
     * Where star {@code i} is, {x, y, z}, in a sky plane facing a camera whose right is {rx, rz}:
     * up in its constellation, then streaming down into the spindle as the winding starts.
     */
    public float[] starAt(int i, long t, float rx, float rz) {
        float[] s = STARS[i];
        float x = rx * s[0] * SKY_SCALE, y = SKY_Y + s[1] * SKY_SCALE, z = rz * s[0] * SKY_SCALE;
        float fall = easeIn((t - (WIND + i * 25L)) / 600f);
        return new float[]{x * (1f - fall), y + (SPIN_Y - y) * fall, z * (1f - fall)};
    }

    /** The constellation's lines fading as its stars fall. */
    public float skyAlpha(long t) {
        return 1f - ease((t - WIND) / 300f);
    }

    /** The spindle: how much of it is there, 0..1 - it appears as the winding starts, and goes into the wool. */
    public float spindle(long t) {
        return ease((t - WIND - 100) / 400f) * (1f - ease((t - SQUARE) / 250f));
    }

    /** Shafts of light falling on the wool as it lands, 0..1. */
    public float shafts(long t) {
        return t < ARRIVE - 150 ? 0f : ease((t - ARRIVE + 150) / 300f) * (1f - ease((t - ARRIVE - 1400) / 900f));
    }

    /** When fibre {@code k} starts to come out. */
    private static long out(int k) {
        return UNRAVEL + (k * 53L) % 1400;
    }

    /** How far wound the fibres are, 0 (drifting free) .. 1 (all on the ball). */
    public float wind(long t) {
        return ease((t - WIND) / (float) (WOUND - WIND - 300));
    }

    /** The ball's radius. */
    public float ball(long t) {
        float u = clamp((t - WIND - 300) / (float) (WOUND - WIND - 300));
        return BALL * (float) Math.sqrt(u);
    }

    /**
     * How far the ball has spun round, radians: slowly at first, faster and faster as it winds
     * (1 to 14 rad/s), then easing to a stop as it squares off.
     */
    public float spin(long t) {
        float d = (WOUND - WIND) / 1000f;
        if (t <= WIND) return (t - WIND) / 1000f;
        if (t <= WOUND) {
            float u = (t - WIND) / (float) (WOUND - WIND);
            return d * (u + 13f * u * u * u / 3f);
        }
        return d * (1f + 13f / 3f) + 14f * 0.4f * (1f - (float) Math.exp(-(t - WOUND) / 400.0));
    }

    /**
     * Fibre {@code k} at {@code t}: POINTS x {x, y, z}, or null before it comes out or once it is
     * all wound in.
     */
    public float[][] fibre(int k, long t) {
        long at = out(k);
        if (t < at || t >= SQUARE + 60) return null;
        float[] f = fibre(k);
        float grow = ease((t - at) / 900f);
        float len = f[3] * grow;
        float sec = t / 1000f;
        float w = wind(t);
        float R = BALL + 4.5f * (1f - w), ballR = Math.max(0.5f, ball(t));
        float spin = spin(t);
        float[][] pts = new float[POINTS][];
        for (int j = 0; j < POINTS; j++) {
            float s = j / (float) (POINTS - 1);
            // Free: up from the base in a breeze - waves running up each strand, freer towards its
            // end, and all of them leaning the same way as the breeze comes and goes.
            float amp = 3.2f * (float) Math.pow(s, 1.3);
            float breeze = (0.6f + 0.4f * (float) Math.sin(sec * 0.7)) * s * s * len * 0.3f;
            float fx = f[0] + f[5] * s * len * 0.5f + breeze + amp * (float) Math.sin(sec * 2.2 - s * 4.0 + f[2]);
            float fy = 0.6f + s * len * (1f - 0.12f * s);
            float fz = f[1] * (1f - 0.35f * s) + amp * 0.8f * (float) Math.cos(sec * 1.6 - s * 3.0 + f[2] * 1.3);
            // Wound: a turn of a helix round the spindle, shrinking onto the ball's surface.
            double ang = f[4] + s * 2.4 + spin;
            float r = ballR + (R - ballR) * (1f - s) * (1f - w);
            float tilt = (float) Math.sin(f[4] * 3 + s * 1.5) * 0.9f;
            float wx = (float) Math.cos(ang) * r, wz = (float) Math.sin(ang) * r;
            float wy = SPIN_Y + tilt * r * 0.6f;
            // Each point is pulled in in turn, the free end first.
            float pull = clamp(w * 1.6f - (1f - s) * 0.6f);
            pull = pull * pull * (3 - 2 * pull);
            pts[j] = new float[]{fx + (wx - fx) * pull, fy + (wy - fy) * pull, fz + (wz - fz) * pull};
        }
        return pts;
    }

    /** How much of the halves is left - they thin away into the fibres. */
    public float halves(long t) {
        return 1f - ease((t - (UNRAVEL + 600)) / 1400f);
    }

    /** The ball's opacity as it squares into the block, and the block's. */
    public float ballAlpha(long t) {
        return t < WIND + 300 || t > SQUARE + 380 ? 0f : 1f;
    }

    /** The ball's drawn size: it shrinks away into the block as that grows round it. */
    public float ballShown(long t) {
        return ball(t) * (1f - ease((t - SQUARE) / 380f));
    }

    public float cubeAlpha(long t) {
        return ease((t - SQUARE) / 200f);
    }

    /** The block blooming out of the shrinking ball: its size factor. */
    public float cubeGrow(long t) {
        return 0.35f + 0.65f * easeOutBack((t - SQUARE) / 420f);
    }

    /** The flash that covers the ball turning into the block, 0..1. */
    public float squareFlash(long t) {
        return t >= SQUARE + 120 ? (float) Math.exp(-(t - SQUARE - 120) / 180.0) : 0f;
    }

    /** Where the wool is: at the spindle, dropping a touch as it settles, with a soft bounce. */
    public float cubeY(long t) {
        float settle = ease((t - SQUARE) / (float) (ARRIVE - SQUARE));
        return SPIN_Y + 0.8f * (1f - settle) + 0.25f * bump(t - ARRIVE, 380) * -1f;
    }

    /** The soft landing's squash: the block's size factor. */
    public float squash(long t) {
        return 1f - 0.08f * bump(t - ARRIVE, 300) + 0.04f * bump(t - ARRIVE - 300, 300);
    }

    /** The sheen sweeping across the block, 0..1 across, or -1. */
    public float sheen(long t) {
        long at = ARRIVE + 500;
        if (t < at || t > at + 700) return -1f;
        return (t - at) / 700f;
    }

    // ------------------------------------------------------------------ camera

    public float zoom(long t) {
        return 1.26f - 0.28f * ease((t - UNRAVEL) / 2000f) + 0.5f * ease((t - WIND) / 1900f) - 0.3f * ease((t - ARRIVE) / 1200f);
    }

    public float[] focus(long t) {
        float y = HEAD + 3f;
        // Up to take in the ram in the sky, down onto the spindle as it winds.
        y += (SPIN_Y + 3f - y) * ease((t - UNRAVEL) / 1800f);
        y += (SPIN_Y - 1f - y) * ease((t - WIND) / 900f);
        return new float[]{0f, y, 0f};
    }

    public float yaw(long t) {
        return -22f * ease((t - SPLIT) / 6000f);
    }

    public float pitch(long t) {
        return 8f * ease((t - WIND) / 2500f);
    }
}
