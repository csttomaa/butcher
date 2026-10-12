package com.butcher.anim;

import static com.butcher.anim.ChopTimeline.*;

/**
 * The Manti-core's ending, "The Heart of the Beast" - pure, like the rest of {@link ChopTimeline}.
 * The Manti-core is "an array of cells from different creatures mashed together": here it is a
 * heart made of them, still beating inside the Manticore.
 *
 * <pre>
 *  SPLIT        the head falls open on a heart, glowing between the halves
 *  +400 - 3980  it beats, faster and faster, rising; each beat a ring across the board, the
 *               halves jolting
 *  +2200 - 3000 a pair of bat's wings unfolds behind it
 *  +4000 - 4450 one great flap: the gust blows the halves off the board
 *  +4450 - 5100 the wings fold round the heart, their tips meeting in front - a cocoon, light
 *               leaking from its seam
 *  +5600        they are flung open (ARRIVE): the Manti-core; the wings go to smoke
 * </pre>
 */
public final class HeartEnding {

    public static final long[] BEATS = {SPLIT + 400, SPLIT + 1250, SPLIT + 1950, SPLIT + 2500, SPLIT + 2950,
            SPLIT + 3300, SPLIT + 3580, SPLIT + 3800, SPLIT + 3980};
    public static final long UNFOLD = SPLIT + 2200, SPREAD = SPLIT + 3000, FLAP = SPLIT + 4000, FLAPPED = SPLIT + 4450,
            WRAPPED = SPLIT + 5100, ARRIVE = SPLIT + 5600, END = SPLIT + 10600;
    public static final float SPAN = 20f, CHORD = 13f;
    public static final int CORE = 0x6FFFC0;

    private final ChopTimeline tl;

    HeartEnding(ChopTimeline tl) {
        this.tl = tl;
    }

    /** How hard the heart is beating right now, 0..1. */
    public float beat(long t) {
        float a = 0f;
        for (int i = 0; i < BEATS.length; i++) {
            long len = 260 - i * 14L;
            a = Math.max(a, bump(t - BEATS[i], len) + 0.55f * bump(t - BEATS[i] - len / 2, len));
        }
        return Math.min(1f, a);
    }

    /** The ring each beat sends across the board: 0..1 through it, or -1. */
    public float ring(int i, long t) {
        long at = BEATS[i];
        if (t < at || t > at + 650) return -1f;
        return (t - at) / 650f;
    }

    /** Where the heart is: rising out of the halves as it beats, held in the cocoon. */
    public float[] heart(long t) {
        float y = 3f + 5f * ease((t - SPLIT - 300) / 2200f) + 3f * ease((t - FLAPPED) / 900f);
        return new float[]{0f, y, 0f};
    }

    /** The heart's size, pulsing - and growing as its cells fly in and fuse. */
    public float heartSize(long t) {
        float grow = ease((t - SPLIT - 250) / 650f);
        return (1.8f + 0.5f * ease((t - SPLIT - 1000) / 3000f)) * grow * (1f + 0.22f * beat(t));
    }

    /**
     * One wing's pose: {spread angle round the vertical (0 out to the side, positive towards the
     * camera), rise (radians up from level), chord tilt (0 hanging, positive swept forward), span
     * scale, chord scale, how far the root has climbed above the heart (0..1)}.
     */
    public float[] wing(long t) {
        float phi = -1.45f, rise = -0.7f, tilt = 0f, size = 0.35f, chord = 1f, hood = 0f;
        if (t >= UNFOLD) {
            float u = easeOutBack((t - UNFOLD) / (float) (SPREAD - UNFOLD));
            phi = -1.45f + 1.45f * u;
            rise = -0.7f + 1.15f * u;
            size = 0.35f + 0.65f * Math.min(1f, u);
        }
        if (t >= SPREAD && t < FLAP) {
            rise += 0.14f * (float) Math.sin((t - SPREAD) / 1000.0 * Math.PI * 2 * 0.9) * ease((t - SPREAD) / 300f);
        }
        if (t >= FLAP) {
            // The flap: swept forward hard, then recovered.
            float down = ease((t - FLAP) / 170f), up = ease((t - FLAP - 170) / 280f);
            tilt = 1.25f * down * (1f - up);
            rise -= 0.55f * down * (1f - up);
        }
        if (t >= FLAPPED) {
            // Folding round the heart: swung forward and curled - each joint folding further - until
            // the tips cross in front of it; short and deep, hung from above it.
            float u = ease((t - FLAPPED) / (float) (WRAPPED - FLAPPED));
            phi += (2.25f - phi) * u;
            rise += (-0.05f - rise) * u;
            size += (0.46f - size) * u;
            chord += (1.05f - chord) * u;
            hood = u;
            float tremble = t > WRAPPED && t < ARRIVE ? 0.02f * (float) Math.sin(t / 23.0) : 0f;
            phi += tremble;
        }
        if (t >= ARRIVE) {
            // Flung open, wide and square to the camera, before they go to smoke.
            float u = easeOutBack((t - ARRIVE) / 260f);
            phi += (0.05f - phi) * u;
            rise += (0.55f - rise) * u;
            size += (1.1f - size) * u;
            chord += (1f - chord) * u;
            hood *= 1f - Math.min(1f, u);
        }
        return new float[]{phi, rise, tilt, size, chord, hood};
    }

    /**
     * How far each joint of a wing folds beyond the one before it, radians round the vertical: a
     * ripple running out to the tip while the wings are spread, the tips lagging and then whipping
     * through the flap, curled round the heart in the cocoon, snapped straight when flung open.
     */
    public float bend(int joint, long t) {
        float b = 0f;
        if (t >= SPREAD - 300 && t < FLAP) b += 0.14f * (float) Math.sin((t - SPREAD) / 1000.0 * Math.PI * 2 * 0.9 - joint * 0.9);
        if (t >= FLAP && t < FLAPPED) {
            float u = (t - FLAP) / (float) (FLAPPED - FLAP);
            b += -0.45f * (float) Math.sin(u * Math.PI * 2) * (1f - u) * joint;
        }
        if (t >= FLAPPED) b += 0.28f * ease((t - FLAPPED) / (float) (WRAPPED - FLAPPED));
        if (t >= ARRIVE) b *= 1f - ease((t - ARRIVE) / 200f);
        return b;
    }

    /** The cocoon cracking with light before it bursts, 0..1. */
    public float cracks(long t) {
        if (t >= ARRIVE) return 0f;
        return clamp((t - (WRAPPED + 80)) / (float) (ARRIVE - WRAPPED - 80));
    }

    /** The cells that make the heart: cell {@code k} of {@link #CELLS}, {x, y, z, size, spin}, or null once fused. */
    public static final int CELLS = 26;

    public float[] cell(int k, long t) {
        long at = SPLIT + 50 + k * 22L;
        float u = (t - at) / 700f;
        if (u < 0f || u >= 1f) return null;
        java.util.Random r = new java.util.Random(tl.seed() * 29 + k * 97L);
        double a = r.nextDouble() * Math.PI * 2;
        float d = 5f + r.nextFloat() * 6f;
        float x0 = (float) Math.cos(a) * d, z0 = (float) Math.sin(a) * d, y0 = 0.5f + r.nextFloat() * 5f;
        float[] h = heart(t);
        float e = u * u * (3 - 2 * u);
        // In along a curve: swinging round the heart as they come.
        double swing = a + 1.6 * e;
        float rr = d * (1f - e);
        float x = h[0] + (float) Math.cos(swing) * rr, z = h[2] + (float) Math.sin(swing) * rr;
        float y = y0 + (h[1] - y0) * e + 2.5f * (float) Math.sin(e * Math.PI);
        return new float[]{x, y, z, 0.45f + 0.35f * r.nextFloat(), u * 9f + k};
    }

    /** The wings' opacity: whole until they are flung open, then gone to smoke. */
    public float wingAlpha(long t) {
        if (t < UNFOLD) return 0f;
        return ease((t - UNFOLD) / 200f) * (1f - ease((t - ARRIVE - 150) / 600f));
    }

    /** The gust: the halves blown off the board, {sideways, up, spin, alpha}. */
    public float[] blow(long t) {
        if (t < FLAP + 90) return new float[]{0f, 0f, 0f, 1f};
        float u = Math.min(1f, (t - FLAP - 90) / 700f);
        return new float[]{22f * u * u, 6f * u - 7f * u * u, 4f * u, 1f - ease((u - 0.4f) / 0.5f)};
    }

    /** The wind of the flap, 0..1. */
    public float gust(long t) {
        return t < FLAP + 60 ? 0f : bump(t - FLAP - 60, 650);
    }

    /** Light leaking from the cocoon's seam, 0..1. */
    public float seam(long t) {
        return ease((t - (WRAPPED - 250)) / 400f) * (t < ARRIVE ? 1f : 0f) * (0.75f + 0.25f * beat(t));
    }

    /** Where the Manti-core is presented: where the heart was. */
    public float[] reveal() {
        return heart(ARRIVE);
    }

    // ------------------------------------------------------------------ camera

    public float zoom(long t) {
        return 1.26f - 0.42f * ease((t - UNFOLD + 600) / 1400f) + 0.75f * ease((t - FLAPPED) / 1100f)
                - 0.5f * ease((t - ARRIVE) / 500f);
    }

    public float[] focus(long t) {
        float[] h = heart(t);
        return new float[]{0f, HEAD + 3f + (h[1] + 1f - HEAD - 3f) * ease((t - SPLIT) / 1500f), 0f};
    }

    public float yaw(long t) {
        return -16f * ease((t - SPLIT) / 3000f) + 8f * ease((t - FLAPPED) / 2000f);
    }
}
