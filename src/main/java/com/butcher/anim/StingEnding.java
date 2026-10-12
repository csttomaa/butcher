package com.butcher.anim;

import static com.butcher.anim.ChopTimeline.*;

/**
 * The Fateful Stinger's ending, "The Sting" - pure, like the rest of {@link ChopTimeline}.
 *
 * A cheap drop (about 3M), so one short idea (the user: the cheap ones do not need that much
 * animation, and the first version - a whole tail, a strike at the camera, cracked glass - was
 * too grand). Everything liquid is pixel art: squares, like the game's own particles.
 *
 * <pre>
 *  SPLIT        the Manticore's head falls open
 *  +250 - 700   the barb shoots up out of the split and hangs over it, point down
 *  +700 - 1450  it draws back, trembling; venom beads on the point, a drop falls and hisses
 *  +1450 - 1530 it stabs down into the board: a thunk, a crack in the wood, venom splashing
 *  +1900 - 2400 it pulls out and rises
 *  +2400        it turns into the Fateful Stinger (TURN), presented at +2700 (ARRIVE)
 * </pre>
 */
public final class StingEnding {

    public static final long RISE = SPLIT + 250, RISEN = SPLIT + 700, STAB = SPLIT + 1450, IMPACT = SPLIT + 1530,
            PULL = SPLIT + 1900, TURN = SPLIT + 2400, ARRIVE = SPLIT + 2700, END = ARRIVE + 3600;
    /** A drop falling off the point while it hangs, and landing. */
    public static final long DRIP = SPLIT + 1050, DRIP_LAND = SPLIT + 1250;
    /** Pixel particles in the splash. */
    public static final int SPLASH = 44;
    /** Where the barb stabs: just in front of the split. */
    public static final float STAB_X = 0f, STAB_Z = 1.2f, HERO_Y = 12f;

    /** The venom: the truth's viridian, lit up; its darker and lighter shades for the pixels. */
    public static final int VENOM = 0x52E39A, VENOM_DARK = 0x1F8A5A, VENOM_LIGHT = 0xB8FFD8;

    private final ChopTimeline tl;

    StingEnding(ChopTimeline tl) {
        this.tl = tl;
    }

    public long seed() {
        return tl.seed();
    }

    /** The point of the barb: {x, y, z}. */
    public float[] tip(long t) {
        float y;
        if (t < RISEN) y = -4f + 12f * easeOutBack((t - RISE) / (float) (RISEN - RISE));
        else if (t < STAB) y = 8f + 1.4f * ease((t - RISEN) / (float) (STAB - RISEN));
        else if (t < IMPACT) y = 9.4f * (1f - easeIn((t - STAB) / (float) (IMPACT - STAB)));
        else if (t < PULL) y = 0f;
        else y = HERO_Y * ease((t - PULL) / (float) (TURN - PULL));
        return new float[]{STAB_X, y, STAB_Z};
    }

    /**
     * How far the barb is drawn back, radians, side on to the camera: back through the wind-up,
     * trembling, snapped straight for the stab.
     */
    public float tilt(long t) {
        if (t < RISEN || t >= STAB + 40) return 0f;
        float back = 0.55f * ease((t - RISEN) / (float) (STAB - 150 - RISEN));
        float tremble = 0.035f * (float) Math.sin(t / 17.0) * ease((t - RISEN - 300) / 300f);
        if (t >= STAB - 150) back *= 1f - ease((t - (STAB - 150)) / 190f);
        return back + tremble;
    }

    /** How much of the barb is above the board, 0..1: it comes up out of the split. */
    public float shown(long t) {
        return clamp((tip(t)[1] + 3.5f) / 4f);
    }

    /** The venom bead on the point, 0..1. */
    public float bead(long t) {
        if (t < RISEN) return 0f;
        if (t < DRIP) return ease((t - RISEN) / (float) (DRIP - RISEN));
        if (t < STAB) return 0.4f * ease((t - DRIP - 100) / 250f);
        return 0f;
    }

    /** The drop falling: {x, y, z}, or null. */
    public float[] drip(long t) {
        if (t < DRIP || t >= DRIP_LAND) return null;
        float[] p = tip(DRIP);
        float u = (t - DRIP) / (float) (DRIP_LAND - DRIP);
        return new float[]{p[0], p[1] - 0.5f - (p[1] - 0.5f) * u * u, p[2]};
    }

    /** Splash pixel {@code k}: {x, y, z, size, shade 0..2, landed}, or null. */
    public float[] splash(int k, long t) {
        if (t < IMPACT) return null;
        float sec = (t - IMPACT) / 1000f;
        java.util.Random r = new java.util.Random(tl.seed() * 41 + k * 131L);
        double a = r.nextDouble() * Math.PI * 2;
        float out = 4f + r.nextFloat() * 9f, up = 5f + r.nextFloat() * 10f;
        float x = STAB_X + (float) Math.cos(a) * out * sec, z = STAB_Z + (float) Math.sin(a) * out * sec;
        float y = 0.2f + up * sec - 34f * sec * sec;
        boolean onBoard = Math.abs(x) < BLOCK_W && Math.abs(z) < BLOCK_D;
        if (y < 0f && onBoard) y = 0.02f;
        if (y < -30f) return null;
        return new float[]{x, y, z, 0.5f + 0.5f * r.nextFloat(), r.nextInt(3), y <= 0.02f ? 1f : 0f};
    }

    /** The stain on the board, growing out from the stab. */
    public float stain(long t) {
        return t < IMPACT ? 0f : 1f - (float) Math.exp(-(t - IMPACT) / 90.0);
    }

    /** The crack the stab leaves in the board, 0..1. */
    public float crack(long t) {
        return t < IMPACT ? 0f : 1f - (float) Math.exp(-(t - IMPACT) / 50.0);
    }

    /** The barb turning into the Fateful Stinger: 0 barb, 1 the drop. */
    public float becomes(long t) {
        return ease((t - (TURN - 100)) / 200f);
    }

    // ------------------------------------------------------------------ camera

    public float zoom(long t) {
        float z = 1.26f + 0.18f * ease((t - RISEN) / (float) (STAB - RISEN));
        if (t >= IMPACT) z += 0.2f * (float) Math.exp(-(t - IMPACT) / 160.0);
        z -= 0.12f * ease((t - PULL) / 700f);
        return z;
    }

    public float[] focus(long t) {
        float y = HEAD + 3f + 2f * ease((t - RISE) / 700f);
        y -= 3.5f * ease((t - STAB + 100) / 250f) * (1f - ease((t - PULL) / 600f));
        y += (HERO_Y - 1f - y) * ease((t - PULL) / 800f);
        return new float[]{0f, y, 0f};
    }

    public float yaw(long t) {
        return -10f * ease((t - SPLIT) / 2500f);
    }

    public float pitch(long t) {
        return 0f;
    }

    public float[] shake(long t) {
        if (t < IMPACT || t > IMPACT + 500) return new float[]{0f, 0f};
        float a = 5f * (float) Math.exp(-(t - IMPACT) / 110.0);
        return new float[]{a * (float) Math.sin(t * 0.37), a * (float) Math.cos(t * 0.53)};
    }
}
