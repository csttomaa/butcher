package com.butcher.render;

import com.butcher.anim.EruptionTimeline;
import com.butcher.anim.EruptionTimeline.Creature;
import com.butcher.anim.EruptionTimeline.Drop;
import com.butcher.anim.EruptionTimeline.Ending;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Random;

import static com.butcher.anim.EruptionTimeline.ease;

/**
 * "Eruption" - lava fishing's animation, drawn onto a ChopScene Canvas: the screen in game, the preview offline,
 * the same code (the Hellion and Seraph way).
 *
 * <b>Three layers.</b> The lava floor is drawn as quads of the game's own animated lava, and everything on it -
 * the crust, the heat in the ring round the vent, the carmine, the green, the terracotta - goes through the gild,
 * texel by texel, in flat colours so whole faces merge (0.2.67's lesson). Every 3D thing - the creature, the vent,
 * Kuudra and its tentacles, the drop itself - is rasterised whole with a depth buffer ({@link ModelRaster}), the
 * lava's surface written in as an occluder so whatever sinks is cut off exactly at the waterline. Particles -
 * bubbles, embers, the fountain, lava bombs, steam, sparks, lightning - are squares on the PixelLayer.
 */
public final class EruptionScene {
    private EruptionScene() {}

    // ================================================================== the models

    /**
     * The game's own models, posed (LavaRigs in game and in the preview; null in the pure tests, which draw
     * no models). Every method returns polygons: four corners of {x, y, z, u, v} in blocks, then the texture
     * slot and flags (see LavaRigs).
     */
    public interface Rigs {
        List<float[]> golem(float walk, float yaw, float armRaise);

        List<float[]> guardian(float spikes, float tail, float yaw, float lookX, float lookY);

        List<float[]> squid(float tentacle, float xBody, float yaw);

        List<float[]> rider(float yaw, float rear, float armRaise);

        List<float[]> skull(float yaw, float pitch);

        List<float[]> magmaCube(float squish, float yaw);
    }

    public static Rigs rigs;

    /** The 3D layer. Its slots are set by the screen or the preview: see SLOT_*. */
    public static final ModelRaster RASTER = new ModelRaster();
    /** The creature's own texture (golem, guardian, squid, Ragnarok's skin, the Scuttler's head). */
    public static final int SLOT_CREATURE = 0;
    public static final int SLOT_HORSE = 1, SLOT_SADDLE = 2;
    /** The magma block's three frames (16 x 48) and their bright cracks as a white mask. */
    public static final int SLOT_MAGMA = 3, SLOT_CRACKS = 4;
    /** The lava, grey - tinted by the heat for the crater. */
    public static final int SLOT_LAVA_GREY = 5;
    /** The magma cube: Kuudra and its tentacles. */
    public static final int SLOT_CUBE = 6;
    /** The drop, recoloured every frame by the quench (QUENCH_*). */
    public static final int SLOT_DROP = 7;

    /** The drop's own pixels (its head skin, 64 x 64, or its item icon, 16 x 16), set by the screen or preview. */
    public static int[] dropPixels;
    public static int dropW, dropH;

    // ================================================================== GPU textures

    /** The game's lava, 16 x 320 (20 frames), read at runtime and never bundled. */
    public static final String TEX_LAVA = "eruption_lava";
    public static final String TEX_WHITE = SeraphScene.TEX_WHITE;
    static final int LAVA_FRAMES = 20;
    /** lava_still.png.mcmeta: frames 0..19 and back, two ticks each. */
    static int lavaFrame(long ms) {
        int i = (int) ((ms / 100) % 38);
        return i < 20 ? i : 38 - i;
    }

    // ================================================================== geometry (world units, 16 a block)

    /** The vent sits at the origin; the lava's surface is y 0. */
    static final float RING = 62f;
    /** The lake: an ellipse round the vent, its far shore a third of the way down the screen, its near one off it. */
    static final float POOL_X = 230f, POOL_Z = 150f;
    /** Whether a tile (by its centre) is in the lake: the ellipse, with a ragged edge. */
    static boolean inLake(float x, float z) {
        // The tile grid the floor is laid on: looked up, it is asked per texel.
        int tx = (int) Math.floor((x + POOL_X + 16f) / 16f), tz = (int) Math.floor((z + POOL_Z + 16f) / 16f);
        if (tx >= 0 && tz >= 0 && tx < LAKE_W && tz < LAKE_H && Math.abs((x + POOL_X + 16f) - (tx * 16f + 8f)) < 0.01f
                && Math.abs((z + POOL_Z + 16f) - (tz * 16f + 8f)) < 0.01f) return LAKE[tz * LAKE_W + tx];
        return inLakeExact(x, z);
    }

    private static final int LAKE_W = (int) Math.ceil((2 * POOL_X + 32f) / 16f) + 1, LAKE_H = (int) Math.ceil((2 * POOL_Z + 32f) / 16f) + 1;
    private static final boolean[] LAKE = new boolean[LAKE_W * LAKE_H];
    static {
        for (int j = 0; j < LAKE_H; j++) for (int i = 0; i < LAKE_W; i++) LAKE[j * LAKE_W + i] = inLakeExact(-POOL_X - 16f + i * 16f + 8f, -POOL_Z - 16f + j * 16f + 8f);
    }

    private static boolean inLakeExact(float x, float z) {
        float k = (x * x) / (POOL_X * POOL_X) + (z * z) / (POOL_Z * POOL_Z);
        int hsh = Math.round(x / 16f) * 73856093 ^ Math.round(z / 16f) * 19349663;
        return k < 0.9f + ((hsh >>> 8) & 255) / 255f * 0.18f;
    }
    public static int unitFor(int h) {
        return Math.max(2, Math.round(h / 120f));
    }

    // ================================================================== the frame

    /**
     * Pixel squares go to two layers (PixelLayers in game, drawn as one image each): the back one - embers,
     * bubbles, the fountain, bolts, rays - is put down before the 3D layer ({@link #flushBack}), the front one -
     * steam, sparks, the carmine rain - after it, before the words ({@link #flushFront}). Offline both are the
     * canvas, drawn at once, so the calls' order is the same order.
     */
    public static Runnable flushBack, flushFront;

    public static void draw(ChopScene.Canvas c, CrystalVeins.Fill back, CrystalVeins.Fill front, EruptionTimeline tl, long t, int w, int h) {
        float master = tl.master(t);
        c.fill(0, 0, w, h, ChopScene.alpha(tl.dim(t)) | 0x0A0606);
        if (master <= 0.01f) return;
        ChopScene.sceneAlpha = master;
        ChopScene.lightCount = 0;
        ChopScene.gild = null;
        ChopScene.glintHook = null;
        ChopScene.insideOut = false;
        ChopScene.yShift = 0f;
        camera(tl, t, w, h);
        long pt = tl.phys(t);
        float g = unitFor(h);

        darkSky(c, tl, t, w, h);
        floor(c, tl, t, pt, w, h);
        // The heat's glow over the vent, under the 3D layer.
        heatGlow(c, tl, t);
        backEffects(c, back, tl, t, pt, w, h, g);
        if (flushBack != null) flushBack.run();
        models(c, tl, t, pt, w, h);
        frontEffects(c, front, tl, t, pt, w, h, g);
        if (flushFront != null) flushFront.run();
        showcase(c, tl, t, w, h, g);
        ChopScene.sceneAlpha = 1f;
    }

    static void camera(EruptionTimeline tl, long t, int w, int h) {
        float sway = 2.5f * (float) Math.sin(t / 2100.0);
        double yaw = Math.toRadians(22f + sway);
        double pitch = Math.toRadians(-24f);
        ChopScene.cy = (float) Math.cos(yaw);
        ChopScene.sy = (float) Math.sin(yaw);
        ChopScene.cp = (float) Math.cos(pitch);
        ChopScene.sp = (float) Math.sin(pitch);
        // A slow push in through the build-up, the cone shaking the camera in the hang.
        float push = 1f + 0.1f * ease(t / (float) EruptionTimeline.REVEAL);
        ChopScene.size = h * 1.2f / 128f * push;
        ChopScene.fx = 0f;
        ChopScene.fy = 14f;
        ChopScene.fz = 0f;
        ChopScene.sx0 = w / 2f;
        ChopScene.sy0 = h * 0.6f;
        float shake = tl.shudder(t) * 1.6f + 3.5f * tl.bolt(t);
        long erupt = tl.eruptAt();
        if (erupt >= 0 && t >= erupt && t < erupt + 500) shake += 4f * (1f - (t - erupt) / 500f);
        if (tl.ending() == Ending.KUUDRA) shake += 2.5f * tl.eye(t) * (1f - tl.eye(t)) * 4f + 2f * tl.swell(t) * (1f - tl.swell(t)) * 4f;
        if (ChopScene.calm) shake = 0f;
        if (shake > 0f) {
            ChopScene.sx0 += shake * (float) Math.sin(t * 0.13);
            ChopScene.sy0 += shake * (float) Math.cos(t * 0.17);
        }
        // Kuudra: the camera pulls back and up as the sea and the tentacles rise.
        if (tl.ending() == Ending.KUUDRA) {
            // Pulled further back, and it stays back: coming in again cut Kuudra's head off at the top of the frame
            // (the review, E17).
            float k = ease((t - EruptionTimeline.K_SWELL) / 1500f) * (1f - 0.2f * ease((t - EruptionTimeline.K_RISE) / 1600f));
            ChopScene.size *= 1f - 0.32f * k;
            ChopScene.fy += 18f * k;
        }
        // Meltdown: shaking harder as it goes critical, a blast at the detonation; pulled back to take in the cloud.
        if (tl.ending() == Ending.VIAL) {
            float crit = tl.critical(t);
            float blast = t >= EruptionTimeline.V_FREE ? 9f * (float) Math.exp(-(t - EruptionTimeline.V_FREE) / 260.0) : 0f;
            ChopScene.sx0 += (2.5f * crit + blast) * (float) Math.sin(t * 0.21);
            ChopScene.sy0 += (2.5f * crit + blast) * (float) Math.cos(t * 0.17);
            float back = ease((t - EruptionTimeline.V_GOLEM) / 900f) * (1f - ease((t - tl.arrive() + 400) / 900f));
            ChopScene.size *= 1f - 0.18f * back - 0.12f * ease((t - EruptionTimeline.V_FREE) / 700f) * (1f - ease((t - tl.arrive() + 400) / 900f));
            ChopScene.fy += 16f * back;
        }
        // The showcase: the drop's resting point centred, the block of words round it in the middle.
        float s = tl.drop().win() ? ease((t - tl.riseFrom()) / (float) Math.max(600, tl.arrive() - tl.riseFrom())) : 0f;
        if (tl.drop().win() && s > 0f) {
            float[] p = dropRest(tl);
            float ts = Math.min(1.35f, h / 300f);
            float above = tl.banner() ? 24f * ts : 0f, below = 45f * ts;
            ChopScene.fx += (p[0] - ChopScene.fx) * s;
            ChopScene.fy += (p[1] - ChopScene.fy) * s;
            ChopScene.fz += (p[2] - ChopScene.fz) * s;
            ChopScene.sx0 += (w / 2f - ChopScene.sx0) * s;
            ChopScene.sy0 += (h / 2f - (below - above) / 2f - ChopScene.sy0) * s;
        }
    }

    // ================================================================== the floor

    /**
     * The lava pool, composed on the CPU into one image a texel a world unit and drawn with one affine blit - the
     * floor is a flat plane and the projection orthographic, so the whole of it is one parallelogram. As ~400 gilded
     * tiles it cost up to 13,000 GUI quads a frame. Each texel is what {@code ChopScene.box} plus the gild drew:
     * the game's lava (lit as a top face is, 0.9415), or the gild's flat colour with the lava over it as relief.
     * Only the part of the lake on the screen is composed.
     */
    static void floor(ChopScene.Canvas c, EruptionTimeline tl, long t, long pt, int w, int h) {
        if (lavaPixels != null) {
            ChopScene.Gild gild = floorGild(tl, t);
            int frame = lavaFrame(pt);
            // World to screen on the plane y = 0: P = O + x U + z V.
            ChopScene.project(0f, 0f, 0f);
            float ox = ChopScene.P[0], oy = ChopScene.P[1];
            ChopScene.project(1f, 0f, 0f);
            float ux = ChopScene.P[0] - ox, uy = ChopScene.P[1] - oy;
            ChopScene.project(0f, 0f, 1f);
            float vx = ChopScene.P[0] - ox, vy = ChopScene.P[1] - oy;
            float det = ux * vy - uy * vx;
            if (Math.abs(det) > 1e-6f) {
                // The screen's corners back on the plane: the world rect that covers the screen.
                float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
                float[][] corners = {{0, 0}, {w, 0}, {0, h}, {w, h}};
                for (float[] s : corners) {
                    float dx = s[0] - ox, dy = s[1] - oy;
                    float x = (dx * vy - dy * vx) / det, z = (ux * dy - uy * dx) / det;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z);
                }
                // The whole plane in view: the lake runs out into a cooling lava field to every edge, fading into the
                // dark as it goes back - no shore, no wall, nothing of the game showing over it (the user, 0.2.78).
                int x0 = (int) Math.floor(Math.max(minX - 1, -700f)), x1 = (int) Math.ceil(Math.min(maxX + 1, 700f));
                int z0 = (int) Math.floor(Math.max(minZ - 1, -900f)), z1 = (int) Math.ceil(Math.min(maxZ + 1, 400f));
                int iw = x1 - x0, ih = z1 - z0;
                if (iw > 0 && ih > 0) {
                    if (floorPx == null || floorPx.length < iw * ih) floorPx = new int[iw * ih];
                    int alpha = Math.round(255 * ChopScene.sceneAlpha);
                    for (int j = 0; j < ih; j++) {
                        int z = z0 + j;
                        int lv = Math.floorMod(z, 16);
                        // Distance into the dark: nothing within the lake's near half, black far back.
                        float fog = Math.max(0f, Math.min(0.92f, (-z - 70f) / 190f));
                        for (int i = 0; i < iw; i++) {
                            int x = x0 + i;
                            int lu = Math.floorMod(x, 16);
                            int texel = lavaPixels[(frame * 16 + lv) * 16 + lu];
                            int cell = x * 73856093 ^ z * 19349663;
                            int col = gild.colour(x + 0.5f, 0f, z + 0.5f, cell, 0, 0, 0);
                            int rgb;
                            if (col == 0) {
                                rgb = scale(texel, 0.9415f);
                            } else if ((col >>> 24) == RECOLOUR) {
                                // Lava of another colour: the texel's own brightness on that colour's ramp.
                                float lum = (0.45f * (texel >> 16 & 255) + 0.45f * (texel >> 8 & 255) + 0.1f * (texel & 255)) / 255f;
                                rgb = scale(col & 0xFFFFFF, Math.min(1.25f, 0.25f + 1.05f * lum));
                                if (lum > 0.8f) rgb = EruptionTimeline.mix(rgb, 0xFFFFFF, (lum - 0.8f) * 1.5f);
                            } else {
                                int relief = col >>> 24;
                                int tint = EruptionTimeline.mix(col & 0xFFFFFF, 0xFFFFFF, 0.55f);
                                int over = multiply(texel, tint);
                                rgb = EruptionTimeline.mix(col & 0xFFFFFF, over, relief / 255f);
                            }
                            if (fog > 0f) rgb = EruptionTimeline.mix(rgb, 0x050304, fog);
                            floorPx[j * iw + i] = alpha << 24 | rgb;
                        }
                    }
                    c.imageAffine(TEX_FLOOR, floorPx, iw, ih, ox + ux * x0 + vx * z0, oy + uy * x0 + vy * z0, ux * iw, uy * iw, vx * ih, vy * ih);
                }
            }
        }
    }

    /** The lava's own pixels (16 x 320, the game's 20 frames), set by the screen or the preview. */
    public static int[] lavaPixels;
    private static int[] floorPx;
    public static final String TEX_FLOOR = "eruption_floor";
    /** A gild alpha that means: recolour the lava itself (its brightness on this colour), not paint over it. */
    static final int RECOLOUR = 0xFE;

    private static int scale(int argb, float k) {
        int r = Math.min(255, Math.round((argb >> 16 & 255) * k)), g = Math.min(255, Math.round((argb >> 8 & 255) * k)),
                b = Math.min(255, Math.round((argb & 255) * k));
        return r << 16 | g << 8 | b;
    }

    private static int multiply(int a, int b) {
        int r = (a >> 16 & 255) * (b >> 16 & 255) / 255, g = (a >> 8 & 255) * (b >> 8 & 255) / 255, bl = (a & 255) * (b & 255) / 255;
        return r << 16 | g << 8 | bl;
    }

    /** The shore: blocks of basalt standing on the lake's rim (the tiles just outside it next to one inside), on screen only. */
    /**
     * The shore: blackstone round the lake's rim (the tiles just outside it next to one inside). Low at the sides and
     * in front; behind the lake it climbs into a cliff that runs off the top of the screen and fades into the dark
     * as it rises - a low wall there left the dimmed game showing above it (the user, 0.2.75).
     */
    /** Where the crust's front is, from the vent: far beyond the pool, closing to the ring (or shut on a bust). */
    static float crustFront(EruptionTimeline tl, long t) {
        float k = EruptionTimeline.crust(t);
        float front = 340f + (RING - 340f) * k;
        float cool = tl.cool(t);
        if (cool > 0f) front = RING * (1f - cool);
        return front;
    }

    static ChopScene.Gild floorGild(EruptionTimeline tl, long t) {
        float front = crustFront(tl, t);
        // The heat shows in the ring once the vent is up; before that the lava is its own colour.
        float showHeat = ease((t - EruptionTimeline.VENT_FROM) / 600f);
        int heat = tl.heatColour(t);
        // Kuudra's carmine reaches 105 units, not the whole view: past it the cold crust stays - all red was a wash
        // (the review, E16).
        float carmine = Math.min(105f, tl.carmine(t) * 320f), green = tl.green(t) * 300f;
        float frozen = tl.frozen(t);
        float terra = tl.terracotta(t);
        float crackR = tl.ending() == Ending.END_TIMES ? 360f * ease((t - EruptionTimeline.T_CRACK) / 700f) : 0f;
        Ending e = tl.ending();
        return (x, y, z, cell, now, sx, sy) -> {
            float d = (float) Math.sqrt(x * x + z * z);
            float n = ((cell * 2654435761L) >>> 16 & 255) / 255f * 10f;
            float dd = d + n;
            boolean crust = dd > front;
            if (e == Ending.KUUDRA) {
                // The carmine takes the whole sea, crust and all, from the vent out: the lava's own pattern
                // still moving in it. Ahead of it, while time is stopped, everything is cold grey.
                if (dd < carmine) return RECOLOUR << 24 | (dd > carmine - 9f ? 0xFF3A62 : 0xB0102A);
                if (frozen > 0.01f) return Math.round(0x50 + 0x30 * frozen) << 24 | EruptionTimeline.mix(crust ? 0x161213 : heat, 0x4A4A52, frozen);
            }
            if (e == Ending.VIAL && dd < green) {
                // The ring goes green lava; out in the crust only veins of it run, bright, through the black rock.
                // The lava itself turned: its own pattern showing through, moving, in green.
                if (!crust) return RECOLOUR << 24 | (dd > green - 8f ? 0x9CFF50 : 0x48D020);
                // Thin seams glowing through the crust, a dark green halo round them.
                if (mask(x, z, 2)) return 0x40 << 24 | (dd > green - 14f ? 0xE8FFC0 : 0x9CFF50);
                if (mask(x, z, 4)) return 0x90 << 24 | 0x1C4A10;
            }
            if (e == Ending.END_TIMES && crackR > 0f && crust && d < crackR && mask(x, z, 1)) {
                return 0x28 << 24 | (d > crackR - 12f ? 0xFFF0B0 : 0xFF8A20);
            }
            if (e == Ending.END_TIMES && terra > 0f && crust) {
                // Ragnarok's ground heating in rings from the vent: lime, yellow, orange, red terracotta, a block at a time.
                // Bounded to a disc that grows to ~105 units, so the ground heats round the vent instead of the whole
                // view turning olive, then mustard (the review, E14).
                float reach = 30f + 75f * Math.min(1f, terra);
                float stage = d + n < reach ? terra - d / 70f : -1f;
                if (stage > 0f) {
                    int s = Math.min(3, (int) stage);
                    int[] terracotta = {0x677534, 0xBA8523, 0xA15325, 0x8F3D2E};
                    int tile = Math.round(x / 16f) * 31 + Math.round(z / 16f) * 17;
                    return 0x48 << 24 | EruptionTimeline.mix(terracotta[s], 0x000000, (tile & 3) * 0.06f);
                }
            }
            if (crust) {
                // The crust, and just inside its front a rim still glowing as it cools.
                if (dd < front + 5f && front > RING + 2f) return 0x50 << 24 | 0xFF6A10;
                // Cooled crust: near-black rock, the lava's own pattern faint in it as relief.
                return 0x38 << 24 | 0x161213;
            }
            if (showHeat > 0.01f && d < RING + 8f) return Math.round(0x70 * showHeat) << 24 | heat;
            return 0;
        };
    }

    /** The lake's extent in texels, for the masks. */
    private static final int MASK_X0 = (int) (-POOL_X - 16f), MASK_Z0 = (int) (-POOL_Z - 16f),
            MASK_W = (int) (2 * POOL_X + 32f), MASK_H = (int) (2 * POOL_Z + 32f);
    /** Per texel: bit 1 a crack (the End Times), bit 2 a vein (the Vial), bit 4 a vein's halo. Fixed shapes, so worked out once. */
    private static byte[] masks;

    static boolean mask(float x, float z, int bit) {
        if (masks == null) {
            byte[] m = new byte[MASK_W * MASK_H];
            for (int j = 0; j < MASK_H; j++) {
                for (int i = 0; i < MASK_W; i++) {
                    float wx = MASK_X0 + i + 0.5f, wz = MASK_Z0 + j + 0.5f;
                    float d = (float) Math.sqrt(wx * wx + wz * wz);
                    int b = 0;
                    if (crack(wx, wz, d)) b |= 1;
                    float v = vein(wx, wz);
                    if (v < 0.014f) b |= 2;
                    else if (v < 0.032f) b |= 4;
                    m[j * MASK_W + i] = (byte) b;
                }
            }
            masks = m;
        }
        int i = (int) Math.floor(x) - MASK_X0, j = (int) Math.floor(z) - MASK_Z0;
        if (i < 0 || j < 0 || i >= MASK_W || j >= MASK_H) return false;
        return (masks[j * MASK_W + i] & bit) != 0;
    }

    /** The End Times' cracks: nine jagged rays out from the vent, a texel or two wide, forking once. */
    static boolean crack(float x, float z, float d) {
        double ang = Math.atan2(z, x);
        for (int i = 0; i < 9; i++) {
            double base = i * Math.PI * 2 / 9 + 0.3 * Math.sin(i * 7.1);
            double wob = 0.05 * Math.sin(d * 0.09 + i * 3) + 0.03 * Math.sin(d * 0.23 + i);
            double da = Math.abs(Math.IEEEremainder(ang - base - wob, Math.PI * 2));
            if (da * d < 1.6) return true;
            if (d > 90f + 20f * (i % 3)) {
                double fork = base + (i % 2 == 0 ? 0.22 : -0.22) * Math.min(1.0, (d - 90 - 20 * (i % 3)) / 60.0);
                double df = Math.abs(Math.IEEEremainder(ang - fork - wob, Math.PI * 2));
                if (df * d < 1.2) return true;
            }
        }
        return false;
    }

    /** Thin winding seams through the crust: near 0 on a seam. */
    static float vein(float x, float z) {
        float a = (float) Math.sin(x * 0.061 + 1.9 * Math.sin(z * 0.043) + 0.6 * Math.sin(x * 0.17))
                + (float) Math.sin(z * 0.067 - 1.4 * Math.sin(x * 0.037) + 0.5 * Math.sin(z * 0.19));
        return Math.abs(a) * 0.5f;
    }

    /** The glow of the heat over the vent and the ring. */
    static void heatGlow(ChopScene.Canvas c, EruptionTimeline tl, long t) {
        float vent = EruptionTimeline.vent(t);
        if (vent <= 0.01f) return;
        float h = Math.max(0f, Math.min(4f, tl.heat(t)));
        ChopScene.project(0f, 20f * vent, 0f);
        float a = (0.15f + 0.12f * h) * Math.min(1f, vent) + 0.3f * EruptionTimeline.pop(t);
        if (tl.cool(t) > 0f) a *= 1f - tl.cool(t);
        if (t > EruptionTimeline.REVEAL) a *= tl.ending() == Ending.KUUDRA ? 0.35f : 0.6f;
        ChopScene.glow(c, ChopScene.P[0], ChopScene.P[1], (60f + 16f * h) * ChopScene.size, tl.heatColour(t), a);
    }

    // ================================================================== the 3D layer

    /** One rasterised polygon in world units. */
    static final class Poly {
        final float[] p = new float[12];
        final float[] uv = new float[4];
        int slot;
        float lit = 1f;
        int tint = 0xFFFFFF, add;
        boolean overlay;
        /** A box face's outward normal (null for a rig's polygon): faces turned away are not rasterised. */
        float[] normal;
        /** A second texture over it in the same pass (-1 none), its tint and added light. */
        int second = -1, secondTint = 0xFFFFFF, secondAdd;
        /**
         * Drawn as GUI quads instead of rasterised (the vent's blocks, Kuudra): plain cubes sort correctly back to
         * front by {@code group} (one per block), and they are the biggest things on the screen - rasterising
         * them cost most of a frame.
         */
        boolean gpu;
        int group;
    }

    static final List<Poly> POLYS = new ArrayList<>();

    static void models(ChopScene.Canvas c, EruptionTimeline tl, long t, long pt, int w, int h) {
        POLYS.clear();
        nextGroup = 0;
        creature(tl, t, pt);
        cone(tl, t, pt);
        pillars(tl, t);
        if (tl.ending() == Ending.VIAL) golemBehind(tl, t);
        if (tl.ending() == Ending.KUUDRA) kuudra(tl, t, pt);
        dropModel(tl, t);
        rasterise(c, tl, t, w, h);
    }

    /** The kill's creature sinking: real vanilla models, cut off at the waterline by the lava's occluder. */
    static void creature(EruptionTimeline tl, long t, long pt) {
        if (rigs == null) return;
        float sink = EruptionTimeline.sink(t);
        if (sink >= 1f) return;
        float k = pt / 1000f;
        Creature cr = tl.creature();
        // Everything turns slowly as it goes down, struggling.
        float yaw = 18f + 10f * (float) Math.sin(k * 1.7);
        switch (cr) {
            case JAWBUS -> add(rigs.golem(k * 2.5f, yaw, 0.9f * sink + 0.3f * (float) Math.sin(k * 6)), 1f,
                    0f, -46f * sink, 0f, SLOT_CREATURE);
            case THUNDER -> add(rigs.guardian(0.5f + 0.5f * (float) Math.sin(k * 3), k * 2f, yaw, 0f, 0.3f), 1f,
                    0f, 18f - 50f * sink, 0f, SLOT_CREATURE);
            case PLHLEGBLAST -> add(rigs.squid(0.4f + 0.6f * (float) Math.abs(Math.sin(k * 2.4)), -10f, yaw), 2.6f,
                    0f, 6f - 64f * sink, 0f, SLOT_CREATURE);
            case RAGNAROK -> add(rigs.rider(yaw, 0.5f * (float) Math.abs(Math.sin(k * 2.2)) + 0.3f * sink, 0.4f), 1.05f,
                    0f, -52f * sink, 0f, -1);
            case SCUTTLER -> {
                // The Scuttler and its friends in tow: one big head, four small ones round it, all going under.
                add(rigs.skull(yaw, 8f * (float) Math.sin(k * 5)), 2.4f, 0f, 9f - 34f * sink, 0f, SLOT_CREATURE);
                float[][] friends = {{-26, 10}, {24, 14}, {-16, -22}, {20, -20}};
                for (int i = 0; i < friends.length; i++) {
                    float s = EruptionTimeline.sink(t - 120L * (i + 1));
                    float bob = 2f * (float) Math.sin(k * 6 + i);
                    add(rigs.skull(yaw + 40f * i, 10f * (float) Math.sin(k * 4 + i)), 1.15f,
                            friends[i][0], 4f + bob - 22f * s, friends[i][1], SLOT_CREATURE);
                }
            }
        }
    }

    /** Polygons from a rig, scaled and placed; slot -1 keeps the rig's own slots (Ragnarok: skin, horse, saddle). */
    static void add(List<float[]> rig, float scale, float x, float y, float z, int slot) {
        float s = 16f * scale;
        // A magma cube is eight stacked slices round an inner cube: at rest only the outside shows, so the
        // slices' tops and bottoms inside the stack, and the inner cube, are left out (Kuudra's tentacles are
        // ninety of them).
        float topY = -Float.MAX_VALUE, botY = Float.MAX_VALUE;
        if (slot == SLOT_CUBE) {
            for (float[] q : rig) {
                for (int k = 0; k < 4; k++) {
                    topY = Math.max(topY, q[k * 5 + 1]);
                    botY = Math.min(botY, q[k * 5 + 1]);
                }
            }
        }
        for (float[] q : rig) {
            if (slot == SLOT_CUBE && q.length >= 25) {
                if (q[21] == 3f) continue;
                float nl = (float) Math.sqrt(q[22] * q[22] + q[23] * q[23] + q[24] * q[24]);
                if (nl > 1e-6f && Math.abs(q[23]) / nl > 0.95f) {
                    float py = (q[1] + q[6] + q[11] + q[16]) / 4f;
                    if (py < topY - 1e-3f && py > botY + 1e-3f) continue;
                }
            }
            Poly p = new Poly();
            int org = -1, ua = -1, va = -1;
            float uMin = 9, uMax = -9, vMin = 9, vMax = -9;
            for (int k = 0; k < 4; k++) {
                uMin = Math.min(uMin, q[k * 5 + 3]);
                uMax = Math.max(uMax, q[k * 5 + 3]);
                vMin = Math.min(vMin, q[k * 5 + 4]);
                vMax = Math.max(vMax, q[k * 5 + 4]);
            }
            for (int k = 0; k < 4; k++) {
                boolean u0 = Math.abs(q[k * 5 + 3] - uMin) < 1e-5f, v0 = Math.abs(q[k * 5 + 4] - vMin) < 1e-5f;
                boolean u1 = Math.abs(q[k * 5 + 3] - uMax) < 1e-5f, v1 = Math.abs(q[k * 5 + 4] - vMax) < 1e-5f;
                if (u0 && v0) org = k;
                else if (u1 && v0) ua = k;
                else if (u0 && v1) va = k;
            }
            if (org < 0 || ua < 0 || va < 0 || uMax - uMin <= 0f || vMax - vMin <= 0f) continue;
            int[] ks = {org, ua, va};
            for (int i = 0; i < 3; i++) {
                p.p[i * 3] = x + q[ks[i] * 5] * s;
                p.p[i * 3 + 1] = y + q[ks[i] * 5 + 1] * s;
                p.p[i * 3 + 2] = z + q[ks[i] * 5 + 2] * s;
            }
            p.uv[0] = uMin;
            p.uv[1] = vMin;
            p.uv[2] = uMax;
            p.uv[3] = vMax;
            p.slot = slot >= 0 ? slot : (int) q[20];
            // Culled like the game's own cutout entities - except heads, which vanilla draws without culling
            // (their hat layer's inside shows through its holes).
            if (q.length >= 25 && q[21] != 2f && slot != SLOT_DROP) p.normal = new float[]{q[22], q[23], q[24]};
            p.lit = light(p);
            POLYS.add(p);
        }
    }

    /** Lit from above and a little from the camera's side, as vanilla's two lights roughly do. */
    static float light(Poly p) {
        float ax = p.p[3] - p.p[0], ay = p.p[4] - p.p[1], az = p.p[5] - p.p[2];
        float bx = p.p[6] - p.p[0], by = p.p[7] - p.p[1], bz = p.p[8] - p.p[2];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-6f) return 0.8f;
        return 0.6f + 0.4f * Math.max(Math.abs(ny / nl) * 0.85f, Math.abs((nx * 0.3f + nz * 0.7f) / nl) * 0.7f);
    }

    /** A textured box as polygons: centre, half sizes, axes; uv per face {u0, v0, u1, v1} normalised (null skips). */
    static void box(float cx, float cy, float cz, float hx, float hy, float hz, float[] X, float[] Y, float[] Z,
                    float[][] uv, int slot, int tint, int add, boolean overlay) {
        float[][] axes = {X, Y, Z};
        float[] half = {hx, hy, hz};
        for (int axis = 0; axis < 3; axis++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                int fi = axis * 2 + (sign > 0 ? 0 : 1);
                if (uv[fi] == null) continue;
                int ua = axis == 1 ? 0 : (axis == 0 ? 2 : 0), va = axis == 1 ? 2 : 1;
                float[] n = axes[axis], U = axes[ua], V = axes[va];
                float hn = half[axis] * sign, hu = half[ua], hv = half[va];
                // Corner at -U -V (top-left in texture: U to the right, V down, so V runs from +V to -V).
                Poly p = new Poly();
                float ox = cx + n[0] * hn - U[0] * hu + V[0] * hv, oy = cy + n[1] * hn - U[1] * hu + V[1] * hv,
                        oz = cz + n[2] * hn - U[2] * hu + V[2] * hv;
                p.p[0] = ox;
                p.p[1] = oy;
                p.p[2] = oz;
                p.p[3] = ox + U[0] * 2 * hu;
                p.p[4] = oy + U[1] * 2 * hu;
                p.p[5] = oz + U[2] * 2 * hu;
                p.p[6] = ox - V[0] * 2 * hv;
                p.p[7] = oy - V[1] * 2 * hv;
                p.p[8] = oz - V[2] * 2 * hv;
                System.arraycopy(uv[fi], 0, p.uv, 0, 4);
                p.normal = new float[]{n[0] * sign, n[1] * sign, n[2] * sign};
                p.slot = slot;
                p.tint = tint;
                p.add = add;
                p.overlay = overlay;
                p.lit = light(p);
                POLYS.add(p);
            }
        }
    }

    static final float[] AX = {1, 0, 0}, AY = {0, 1, 0}, AZ = {0, 0, 1};

    /** The vent: a cone of magma blocks pushing up through the crust, its cracks the heat, a crater of lava on top. */
    static void cone(EruptionTimeline tl, long t, long pt) {
        float vent = EruptionTimeline.vent(t);
        if (vent <= 0.001f) return;
        float rise = (1f - Math.min(1f, vent)) * -44f;
        float cool = Math.max(0f, tl.cool(t));
        rise -= 10f * cool;
        if (tl.ending() == Ending.KUUDRA) rise -= 120f * tl.swell(t);
        if (rise < -60f) return;
        float erupt = tl.erupt(t);
        float sh = tl.shudder(t);
        float jx = sh * 0.8f * (float) Math.sin(t * 0.21), jz = sh * 0.8f * (float) Math.cos(t * 0.19);
        int heat = tl.heatColour(t);
        if (tl.ending() == Ending.KUUDRA) heat = EruptionTimeline.mix(EruptionTimeline.mix(heat, 0x6A6A72, tl.frozen(t)), 0xE0204A, tl.carmine(t));
        float hv = Math.max(0f, Math.min(4f, tl.heat(t))) / 4f;
        int frame = (int) ((pt / 400) % 3);
        float v0 = frame / 3f, v1 = (frame + 1) / 3f;
        float[][] magma = {{0, v0, 1, v1}, {0, v0, 1, v1}, {0, v0, 1, v1}, null, {0, v0, 1, v1}, {0, v0, 1, v1}};
        // The cracks glow the heat; a bust's go dark as it cools.
        int add = Math.round(255 * (0.25f + 0.5f * hv) * (1f - cool)) << 24 | heat;
        // A cone: half-sunk boulders of cooled blackstone round its foot, a ring of blackstone and magma, a shoulder
        // of magma, the crater block on top. (A flat skirt of magma round it read as a platform - the user.)
        java.util.List<float[]> blocks = new java.util.ArrayList<>();
        int[][] rocks = {{-2, 0}, {2, 0}, {0, 2}, {0, -2}, {-2, 1}, {2, -1}};
        for (int[] q : rocks) blocks.add(new float[]{q[0] * 15f, -5f, q[1] * 15f, 0});
        for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) blocks.add(new float[]{i * 16f, 4f, j * 16f, Math.abs(i) + Math.abs(j) == 2 ? 0 : 1});
        for (int i = -1; i <= 1; i += 2) for (int j = -1; j <= 1; j += 2) blocks.add(new float[]{i * 8f, 18f, j * 8f, 1});
        blocks.add(new float[]{0f, 32f, 0f, 1});
        float[][] stone = {{0, 0, 1, 1}, {0, 0, 1, 1}, {0, 0, 1, 1}, null, {0, 0, 1, 1}, {0, 0, 1, 1}};
        Random r = new Random(7);
        for (int b = 0; b < blocks.size(); b++) {
            float[] p = blocks.get(b);
            float x = p[0] + jx, y = p[1] + rise, z = p[2] + jz;
            float[] X = AX, Y = AY, Z = AZ;
            float hs = b == blocks.size() - 1 ? 7f : 8f;
            if (erupt >= 0f) {
                // Blown apart: each block bursts into four half-size chunks thrown out fast and high, tumbling,
                // gone off the screen or back into the lava in well under a second - the column takes over.
                if (p[1] < 0f) continue;
                boolean rock = p[3] == 0;
                float s = erupt * 1.4f;
                for (int piece = 0; piece < 4; piece++) {
                    double ang = Math.atan2(p[2] + 0.01, p[0] + 0.01) + (r.nextFloat() - 0.5f) * 1.6f;
                    float sp = 230f + 180f * r.nextFloat();
                    float px = x + (float) Math.cos(ang) * sp * s, pz = z + (float) Math.sin(ang) * sp * s * 0.6f;
                    float py = y + (240f + 140f * r.nextFloat()) * s - 520f * s * s;
                    if (py < -12f) continue;
                    float spin = s * (6f + 6f * r.nextFloat());
                    float ca = (float) Math.cos(spin), sa = (float) Math.sin(spin);
                    float[] PX = {ca, sa, 0}, PY = {-sa, ca, 0};
                    int from = POLYS.size();
                    if (rock) {
                        box(px, py, pz, hs / 2f, hs / 2f, hs / 2f, PX, PY, AZ, stone, SLOT_STONE, 0xFFFFFF, 0, false);
                    } else {
                        box(px, py, pz, hs / 2f, hs / 2f, hs / 2f, PX, PY, AZ, magma, SLOT_MAGMA, 0xFFFFFF, 0, false);
                        cracks(from, heat, add);
                    }
                    onGpu(from);
                }
                continue;
            }
            int from = POLYS.size();
            if (p[3] == 0) {
                box(x, y, z, hs, hs, hs, X, Y, Z, stone, SLOT_STONE, 0xFFFFFF, 0, false);
            } else {
                box(x, y, z, hs, hs, hs, X, Y, Z, magma, SLOT_MAGMA, 0xFFFFFF, 0, false);
                cracks(from, heat, add);
            }
            // The crater: the top block's top is lava, grey, tinted by the heat.
            if (b == blocks.size() - 1 && erupt < 0f) {
                // The game's lava, moving, in the heat's colour.
                int lf = lavaFrame(pt);
                float[][] top = {null, null, {0, lf / 20f, 1, (lf + 1) / 20f}, null, null, null};
                // A narrow opening, a quarter of the block's top (the user: smaller).
                box(x, y + 0.6f, z, 3.5f, hs, 3.5f, X, Y, Z, top, SLOT_LAVA_GREY, heat, Math.round(200 * (1f - cool)) << 24 | heat, false);
            }
            onGpu(from);
        }
    }

    /**
     * Basalt columns standing out of the lava either side of the vent, as on the Crimson Isle's Basalt Deltas
     * (the user's pick for the empty sides, 0.2.79): stacks of whole blocks, half sunk, at stepped heights. Placed by
     * where they stand on the screen (screen x ~ 0.93 x + 0.37 z at yaw 22). They go down into Kuudra's swelling sea.
     * {screen x, depth, blocks high}.
     */
    static final float[][] PILLARS = {{-60, -40, 2}, {-76, -8, 1}, {-54, -76, 2}, {-84, -62, 3}, {-66, 12, 1},
            {62, -46, 2}, {78, -12, 3}, {58, -84, 1}, {88, -70, 2}, {68, 8, 1}};

    static void pillars(EruptionTimeline tl, long t) {
        float sink = tl.ending() == Ending.KUUDRA ? 140f * tl.swell(t) : 0f;
        float[][] side = {{0, 0, 1, 1}, {0, 0, 1, 1}, null, null, {0, 0, 1, 1}, {0, 0, 1, 1}};
        float[][] top = {null, null, {0, 0, 1, 1}, null, null, null};
        for (int i = 0; i < PILLARS.length; i++) {
            float z = PILLARS[i][1], x = (PILLARS[i][0] - 0.37f * z) / 0.93f;
            int blocks = (int) PILLARS[i][2];
            // Sunk a little, by its own amount, so the tops do not line up.
            float foot = -6f - 3f * (i % 3) - sink;
            for (int b = 0; b < blocks; b++) {
                float cy = foot + 8f + 16f * b;
                if (cy + 8f < -1f) continue;
                int from = POLYS.size();
                // In shadow: only the lava below lights them (drawGpu's glow by height).
                box(x, cy, z, 8f, 8f, 8f, AX, AY, AZ, side, SLOT_BASALT, 0x8A8088, 0, false);
                if (b == blocks - 1) box(x, cy, z, 8f, 8f, 8f, AX, AY, AZ, top, SLOT_BASALT_TOP, 0x9A9098, 0, false);
                onGpu(from);
            }
        }
    }

    private static int nextGroup;

    /** The polygons added since {@code from} go to the GPU as one block. */
    private static void onGpu(int from) {
        int g = nextGroup++;
        for (int i = from; i < POLYS.size(); i++) {
            POLYS.get(i).gpu = true;
            POLYS.get(i).group = g;
        }
    }

    /** The magma block's glowing cracks over the faces added since {@code from}, in the same raster pass. */
    private static void cracks(int from, int heat, int add) {
        for (int i = from; i < POLYS.size(); i++) {
            Poly p = POLYS.get(i);
            p.second = SLOT_CRACKS;
            p.secondTint = heat;
            p.secondAdd = add;
        }
    }

    /** The vial's ending: Lord Jawbus himself rising half out of the green lava behind the vent, the vial in his chest. */
    static void golemBehind(EruptionTimeline tl, long t) {
        float k = tl.golem(t);
        if (k <= 0f || rigs == null || tl.creature() != Creature.JAWBUS) return;
        // Up to the waist, the vial lit in his chest; he is blown apart when it goes off (the debris is pixels).
        if (t >= EruptionTimeline.V_FREE + 40) return;
        float y = GOLEM_FEET + 54f * k;
        // His arms come up as the vial throbs; he shakes harder with every beat.
        float crit = tl.critical(t);
        float arms = 0.5f * k + 0.6f * tl.vialPulse(t) + 1.2f * crit;
        float shake = 1.8f * crit;
        add(rigs.golem(0f, 6f * crit * (float) Math.sin(t / 37.0), Math.min(2.5f, arms)), 1.6f,
                GOLEM_X + shake * (float) Math.sin(t / 23.0), y, GOLEM_Z + shake * (float) Math.cos(t / 29.0), SLOT_CREATURE);
    }

    /** Behind the vent and straight up the screen from it (the camera looks from yaw 22). */
    static final float GOLEM_X = 32f;
    static final float GOLEM_FEET = -84f, GOLEM_Z = -80f, GOLEM_CHEST = 52f;

    /** Kuudra itself: how far back it rises, and its size (a magma cube's scale - 8 units a step). */
    static final float KUUDRA_X = 50f, KUUDRA_Z = -125f, KUUDRA_SCALE = 9f;

    /**
     * Kuudra: the giant magma cube rising out of the carmine sea far behind the vent, eight tentacles bursting up
     * round the edges and curling in over the lake, and the ninth, in the middle, bringing the dye up.
     */
    static void kuudra(EruptionTimeline tl, long t, long pt) {
        if (rigs == null) return;
        float eye = tl.eye(t);
        if (eye > 0f) {
            float half = KUUDRA_SCALE * 4f;
            float y = -half - 6f + (half + 6f) * eye;
            int from = POLYS.size();
            add(rigs.magmaCube(0.12f * (float) Math.sin(t / 340.0) * eye, 0f), KUUDRA_SCALE, KUUDRA_X, y, KUUDRA_Z, SLOT_CUBE);
            onGpu(from);
        }
        int n = 8;
        for (int i = 0; i < n; i++) {
            float k = tl.tentacle(t, i, n);
            if (k <= 0f) continue;
            float[] at = TENTACLES[i];
            // Placed by where they stand on the screen (the camera looks from yaw 22: screen x ~ 0.93 x + 0.37 z).
            float bz = at[1], bx = (at[0] - 0.37f * bz) / 0.93f;
            tentacle(bx, bz, -Math.signum(at[0]), k, 11, 1.9f, t / 1000f + i * 1.3f, 0.85f + 0.1f * (i % 3), 30f * i);
        }
        // The ninth: up through the middle, the dye on its tip.
        float c = centralRise(t);
        if (c > 0f) {
            float[] tip = centralTip(tl, t);
            float bx = 0f, by = -12f, bz = 6f;
            int segs = 8;
            for (int s = 0; s < segs; s++) {
                float u = (s + 0.5f) / segs;
                float x = bx + (tip[0] - bx) * u + 5f * (float) Math.sin(u * Math.PI) * (float) Math.sin(t / 420.0);
                float y = by + (tip[1] - 6f - by) * u;
                float z = bz + (tip[2] - bz) * u - 6f * (float) Math.sin(u * Math.PI);
                segment(x, y, z, 1.9f - 0.9f * u, 20f * s + 8f * (float) Math.sin(t / 300.0 + s));
            }
        }
    }

    /**
     * A tentacle of magma-cube segments from its base under the lava: each segment's direction turns a little more
     * towards {@code inward} (the lake's middle), so it rises and curls over. {@code k} is how far it is out.
     */
    /** The eight tentacles' places: {screen x in world units from the middle, depth}. Outer pairs first. */
    static final float[][] TENTACLES = {{-108, -40}, {108, -40}, {-84, -105}, {84, -105}, {-124, 40}, {124, 40}, {-62, -110}, {62, -110}};

    static void tentacle(float bx, float bz, float inward, float k, int segs, float size0, float clock, float reach, float spin) {
        float x = bx, y = -24f, z = bz;
        double ang = 0.0;
        float curl = (0.26f + 0.05f * (float) Math.sin(clock * 1.7)) * reach;
        float wave = 0.18f * (float) Math.sin(clock * 2.3);
        int shown = Math.max(1, Math.round(segs * Math.min(1f, k)));
        for (int s = 0; s < shown; s++) {
            float u = s / (float) (segs - 1);
            float size = size0 * (1f - 0.6f * u);
            float step = 14f * size / size0 * Math.min(1.15f, k);
            ang += curl * u * 1.4f + wave * (float) Math.sin(clock * 1.9 + s * 0.6) * 0.4f;
            x += inward * (float) Math.sin(ang) * step;
            y += (float) Math.cos(ang) * step;
            z += 2.2f * (float) Math.sin(clock + s * 0.5);
            segment(x, y, z, size, spin + 14f * s);
        }
    }

    /** The magma cube's dark rock (its top slice's top), the whole face of every tentacle segment. */
    private static final float[] ROCK = {8f / 64f, 0f, 16f / 64f, 8f / 64f};
    private static final float[][] ROCK_FACES = {ROCK, ROCK, ROCK, ROCK, ROCK, ROCK};

    /**
     * One tentacle segment: a cube of the magma cube's rock, {@code size} as a magma cube's (half 4 units a step),
     * turned {@code yaw} degrees - a single box, drawn on the GPU (ninety of the full eight-slice model were 8,000
     * quads).
     */
    static void segment(float x, float y, float z, float size, float yaw) {
        double a = Math.toRadians(yaw);
        float c = (float) Math.cos(a), sn = (float) Math.sin(a);
        float hs = 4f * size;
        int from = POLYS.size();
        box(x, y, z, hs, hs, hs, new float[]{c, 0, -sn}, AY, new float[]{sn, 0, c}, ROCK_FACES, SLOT_CUBE, 0xFFFFFF, 0, false);
        onGpu(from);
    }

    /** The central tentacle coming up, 0..1. */
    static float centralRise(long t) {
        return t < EruptionTimeline.K_RISE ? 0f
                : EruptionTimeline.backOut((t - EruptionTimeline.K_RISE) / (float) (EruptionTimeline.K_RISE_TO - EruptionTimeline.K_RISE));
    }

    /** The central tentacle's tip (Kuudra's offering), or null before it rises. */
    static float[] centralTip(EruptionTimeline tl, long t) {
        if (t < EruptionTimeline.K_RISE) return null;
        float k = centralRise(t);
        float sway = 4f * (float) Math.sin(t / 500.0);
        return new float[]{sway, -10f + 67f * k, 0f};
    }

    // ================================================================== the drop

    /** Where the drop rests for its showcase, world units: over the vent. */
    static final float REST_Y = 44f;

    static float[] dropRest(EruptionTimeline tl) {
        // Kuudra's dye rests below its eyes - at 70 it sat across one (the review, E18).
        return new float[]{0f, tl.ending() == Ending.KUUDRA ? 56f : REST_Y, 0f};
    }

    /** The drop's pose now: {x, y, z, yaw, pitch, roll, scale}, by its ending's own path, grown by its worth as it rises. */
    static float[] dropPose(EruptionTimeline tl, long t) {
        float[] p = dropPath(tl, t);
        p[6] *= 1f + (worthScale(tl) - 1f) * tl.rise(t);
        return p;
    }

    /**
     * The drop at the showcase, by what it is worth (the review, A4): the 175M Carmine Dye was the size of a 4M Flash I.
     */
    static float worthScale(EruptionTimeline tl) {
        return switch (tl.ending()) {
            case KUUDRA -> 1.25f;
            case END_TIMES, VIAL -> 1.3f;
            case SHELL, FORGE -> 1.15f;
            default -> 1f;
        };
    }

    static float[] dropPath(EruptionTimeline tl, long t) {
        float[] rest = dropRest(tl);
        float rise = tl.rise(t);
        float bob = 2.5f * (float) Math.sin(t / 380.0);
        float y = -12f + (rest[1] + 12f) * rise + bob * rise;
        float yaw = 25f + 330f * (1f - EruptionTimeline.ease(rise)) + 10f * (float) Math.sin(t / 900.0);
        float pitch = -8f, roll = 0f, scale = 1f;
        switch (tl.ending()) {
            case PINCER -> {
                // It snaps up out of the lava, clacks twice, then rises to show itself.
                float snap = EruptionTimeline.backOut((t - EruptionTimeline.P_SNAP) / 260f);
                y = -14f + 34f * snap + (rest[1] - 20f) * rise;
                for (long at : new long[]{EruptionTimeline.P_CLACKS0, EruptionTimeline.P_CLACKS1}) {
                    float k = (t - at) / 160f;
                    if (k >= 0f && k < 1f) scale *= 1f + 0.18f * (float) Math.sin(k * Math.PI);
                }
                yaw = 25f + 40f * (float) Math.sin(t / 300.0) * (1f - rise);
            }
            case FORGE -> {
                // Drawn up out of the forge blade-first: upright (the icon's diagonal straight up), turning slowly.
                y = -30f + (rest[1] + 30f) * EruptionTimeline.ease((t - tl.riseFrom()) / (float) (tl.riseTo() - tl.riseFrom())) + bob;
                roll = 45f;
                yaw = 20f + 25f * (float) Math.sin(t / 700.0);
                for (long at : EruptionTimeline.FORGE_BLOWS) {
                    float k = (t - at) / 140f;
                    if (k >= 0f && k < 1f) y -= 3f * (float) Math.sin(k * Math.PI);
                }
            }
            case VIAL -> {
                // In Jawbus's chest; blown up into the cloud as he goes; then down out of it to be shown.
                float golemY = GOLEM_FEET + 54f * tl.golem(t) + GOLEM_CHEST;
                float[] chest = {GOLEM_X, golemY, GOLEM_Z + 16f};
                float[] high = {GOLEM_X * 0.6f, 52f, GOLEM_Z * 0.55f};
                float spin = 15f + 20f * (float) Math.sin(t / 500.0) + 360f * EruptionTimeline.ease((t - EruptionTimeline.V_FREE) / 900f);
                // Big enough to read in his chest, just in front of it (the review, E19).
                if (t < EruptionTimeline.V_FREE) return new float[]{chest[0], chest[1], chest[2], spin, -8f, 0f, 1.05f};
                if (t < EruptionTimeline.V_DESCEND) {
                    float up = EruptionTimeline.backOut((t - EruptionTimeline.V_FREE) / 600f);
                    return new float[]{chest[0] + (high[0] - chest[0]) * up, chest[1] + (high[1] - chest[1]) * up, chest[2] + (high[2] - chest[2]) * up,
                            spin, -8f, 0f, 1.05f - 0.05f * up};
                }
                float down = EruptionTimeline.ease((t - EruptionTimeline.V_DESCEND) / (float) (tl.arrive() - EruptionTimeline.V_DESCEND));
                return new float[]{high[0] + (rest[0] - high[0]) * down, high[1] + (rest[1] - high[1]) * down + bob * down,
                        high[2] + (rest[2] - high[2]) * down, spin, -8f, 0f, 1f};
            }
            case KUUDRA -> {
                float[] tip = centralTip(tl, t);
                if (tip != null) return new float[]{tip[0], tip[1] + 13f, tip[2], 20f + 15f * (float) Math.sin(t / 800.0), -8f, 0f, 1.5f};
            }
            default -> { }
        }
        return new float[]{rest[0], y, rest[2], yaw, pitch, roll, scale};
    }

    /** The drop as a model: its head (the skull rig, its skin quenched) or its item, extruded as the game does. */
    static void dropModel(EruptionTimeline tl, long t) {
        Drop d = tl.drop();
        if (!d.win() || dropPixels == null) return;
        boolean showing = tl.ending() == Ending.VIAL ? tl.golem(t) > 0f : tl.rise(t) > 0f || (tl.ending() == Ending.PINCER && t >= EruptionTimeline.P_SNAP);
        if (tl.ending() == Ending.KUUDRA) showing = centralTip(tl, t) != null;
        if (!showing) return;
        quenchTexture(tl, t);
        float[] p = dropPose(tl, t);
        if (d.head) {
            if (rigs != null) add(rigs.skull(p[3], p[4]), 1.7f * p[6], p[0], p[1], p[2], SLOT_DROP);
        } else {
            item(p[0], p[1], p[2], 30f * p[6], p[3], p[4], p[5]);
        }
        if (tl.ending() == Ending.SHELL) crab(tl, t, p);
    }

    /**
     * An item as the game shows one dropped: its icon extruded a pixel deep - the front, the back, and a strip on
     * every pixel's edge that borders a hole - turned to {@code yaw}/{@code pitch}/{@code roll} degrees.
     */
    static void item(float cx, float cy, float cz, float size, float yaw, float pitch, float roll) {
        int w0 = dropW, h0 = dropH;
        float px = size / w0, half = size / 2f, dz = px;
        double ya = Math.toRadians(yaw), pa = Math.toRadians(pitch), ra = Math.toRadians(roll);
        float cyw = (float) Math.cos(ya), syw = (float) Math.sin(ya), cpw = (float) Math.cos(pa), spw = (float) Math.sin(pa),
                crw = (float) Math.cos(ra), srw = (float) Math.sin(ra);
        // R = Ry(yaw) * Rx(pitch) * Rz(roll), as columns X, Y, Z.
        float[] X = rot(cyw, syw, cpw, spw, crw, srw, 1, 0, 0), Y = rot(cyw, syw, cpw, spw, crw, srw, 0, 1, 0),
                Z = rot(cyw, syw, cpw, spw, crw, srw, 0, 0, 1);
        itemQuad(cx, cy, cz, X, Y, Z, -half, half, dz / 2, size, 0, 0, 0, -size, 0, 0, 0, 1, 1);
        itemQuad(cx, cy, cz, X, Y, Z, -half, half, -dz / 2, size, 0, 0, 0, -size, 0, 0, 0, 1, 1);
        for (int j = 0; j < h0; j++) {
            for (int i = 0; i < w0; i++) {
                if ((dropPixels[j * w0 + i] >>> 24) == 0) continue;
                float u0 = i / (float) w0, v0 = j / (float) h0, u1 = (i + 1f) / w0, v1 = (j + 1f) / h0;
                float x0 = -half + i * px, yt = half - j * px;
                if (i == 0 || (dropPixels[j * w0 + i - 1] >>> 24) == 0)
                    itemQuad(cx, cy, cz, X, Y, Z, x0, yt, dz / 2, 0, 0, -dz, 0, -px, 0, u0, v0, u1, v1);
                if (i == w0 - 1 || (dropPixels[j * w0 + i + 1] >>> 24) == 0)
                    itemQuad(cx, cy, cz, X, Y, Z, x0 + px, yt, -dz / 2, 0, 0, dz, 0, -px, 0, u0, v0, u1, v1);
                if (j == 0 || (dropPixels[(j - 1) * w0 + i] >>> 24) == 0)
                    itemQuad(cx, cy, cz, X, Y, Z, x0, yt, -dz / 2, px, 0, 0, 0, 0, dz, u0, v0, u1, v1);
                if (j == h0 - 1 || (dropPixels[(j + 1) * w0 + i] >>> 24) == 0)
                    itemQuad(cx, cy, cz, X, Y, Z, x0, yt - px, dz / 2, px, 0, 0, 0, 0, -dz, u0, v0, u1, v1);
            }
        }
    }

    private static float[] rot(float cy, float sy, float cp, float sp, float cr, float sr, float x, float y, float z) {
        float x1 = x * cr - y * sr, y1 = x * sr + y * cr, z1 = z;
        float y2 = y1 * cp - z1 * sp, z2 = y1 * sp + z1 * cp;
        return new float[]{x1 * cy + z2 * sy, y2, -x1 * sy + z2 * cy};
    }

    /** One quad of the item, its corner and two edges in the item's own frame. */
    private static void itemQuad(float cx, float cy, float cz, float[] X, float[] Y, float[] Z,
                                 float lx, float ly, float lz, float ux, float uy, float uz, float vx, float vy, float vz,
                                 float u0, float v0, float u1, float v1) {
        Poly p = new Poly();
        float[] o = {cx + X[0] * lx + Y[0] * ly + Z[0] * lz, cy + X[1] * lx + Y[1] * ly + Z[1] * lz, cz + X[2] * lx + Y[2] * ly + Z[2] * lz};
        float[] U = {X[0] * ux + Y[0] * uy + Z[0] * uz, X[1] * ux + Y[1] * uy + Z[1] * uz, X[2] * ux + Y[2] * uy + Z[2] * uz};
        float[] V = {X[0] * vx + Y[0] * vy + Z[0] * vz, X[1] * vx + Y[1] * vy + Z[1] * vz, X[2] * vx + Y[2] * vy + Z[2] * vz};
        p.p[0] = o[0];
        p.p[1] = o[1];
        p.p[2] = o[2];
        p.p[3] = o[0] + U[0];
        p.p[4] = o[1] + U[1];
        p.p[5] = o[2] + U[2];
        p.p[6] = o[0] + V[0];
        p.p[7] = o[1] + V[1];
        p.p[8] = o[2] + V[2];
        p.uv[0] = u0;
        p.uv[1] = v0;
        p.uv[2] = u1;
        p.uv[3] = v1;
        p.slot = SLOT_DROP;
        p.lit = light(p);
        POLYS.add(p);
    }

    /** The Scuttler Shell's ending: the crab peeks out from under its shell, then scuttles off and leaves it. */
    static void crab(EruptionTimeline tl, long t, float[] shell) {
        if (rigs == null || t < EruptionTimeline.S_PEEK || t > EruptionTimeline.S_GONE) return;
        float peek = EruptionTimeline.ease((t - EruptionTimeline.S_PEEK) / 400f);
        float run = Math.max(0f, (t - EruptionTimeline.S_RUN) / (float) (EruptionTimeline.S_GONE - EruptionTimeline.S_RUN));
        float x = shell[0] + 14f * peek + 260f * run * run;
        float y = shell[1] - 20f + 6f * peek - 30f * run + 2f * (float) Math.abs(Math.sin(t / 60.0)) * run;
        add(rigs.skull(70f + 20f * (float) Math.sin(t / 80.0), 10f * (float) Math.sin(t / 70.0)), 1.2f, x, y, shell[2] + 4f, SLOT_CREATURE);
    }

    private static int[] quenched;

    /**
     * The drop's texture as the quench has it now: every texel its heat colour, white-hot, until the cooling
     * reaches it - from the edges in, with a ragged front - and then its own colour.
     */
    static void quenchTexture(EruptionTimeline tl, long t) {
        int n = dropW * dropH;
        if (quenched == null || quenched.length != n) quenched = new int[n];
        float q = tl.quench(t);
        int heat = tl.drop().heat.rgb;
        if (tl.ending() == Ending.KUUDRA) heat = EruptionTimeline.mix(EruptionTimeline.CARMINE, 0xFF4060, 0.4f);
        if (tl.ending() == Ending.VIAL) heat = 0x7CFF40;
        // White heat ramps through a warm yellow: through white itself the item came out cold grey.
        int mid = (heat >> 16 & 255) + (heat >> 8 & 255) + (heat & 255) > 690 ? 0xFFC060 : heat;
        for (int yy = 0; yy < dropH; yy++) {
            for (int xx = 0; xx < dropW; xx++) {
                int i = yy * dropW + xx;
                int c = dropPixels[i];
                if ((c >>> 24) == 0) {
                    quenched[i] = 0;
                    continue;
                }
                // The texel's distance from its tile's edge (8-px faces on a skin, the whole icon otherwise).
                int tile = dropW == 64 ? 8 : dropW;
                int lx = xx % tile, ly = yy % tile;
                float edge = Math.min(Math.min(lx, tile - 1 - lx), Math.min(ly, tile - 1 - ly)) / (tile / 2f);
                float noise = ((i * 2654435761L) >>> 20 & 255) / 255f * 0.35f;
                float when = 0.15f + 0.6f * edge + noise;
                float k = Math.max(0f, Math.min(1f, (q * 1.25f - when) / 0.25f + 0.0f));
                // The texel's own brightness on a heat ramp - dark red, the heat, then white-hot - so the item's pattern
                // still reads while it glows. Mixing white into every texel turned each drop into a blank pale block
                // for a second (the review, E11).
                int r = c >> 16 & 255, gg = c >> 8 & 255, b = c & 255;
                float lum = (0.3f * r + 0.59f * gg + 0.11f * b) / 255f;
                lum = Math.min(1f, Math.max(0f, lum + 0.06f * (float) Math.sin(t / 90.0 + i)));
                int hot = lum < 0.5f
                        ? EruptionTimeline.mix(EruptionTimeline.mix(mid, 0x000000, 0.7f), mid, lum * 2f)
                        : EruptionTimeline.mix(mid, 0xFFFFF0, (lum - 0.5f) * 1.3f);
                int rgb = EruptionTimeline.mix(hot, c & 0xFFFFFF, k);
                quenched[i] = (c & 0xFF000000) | rgb;
            }
        }
        RASTER.texture(SLOT_DROP, quenched, dropW, dropH);
    }

    // ================================================================== rasterise

    static void rasterise(ChopScene.Canvas c, EruptionTimeline tl, long t, int w, int h) {
        if (POLYS.isEmpty()) return;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float[][] scr = new float[POLYS.size()][];
        // Whatever is wholly under the lava is gone (the occluder would hide it anyway), and an upright face half
        // in it is cut at the surface, so no pixel under the waterline is ever filled.
        POLYS.removeIf(p -> !clipToSurface(p));
        // The big plain cubes first, as GUI quads; everything rasterised is drawn over them.
        List<Poly> gpu = new ArrayList<>();
        for (Poly p : POLYS) if (p.gpu) gpu.add(p);
        if (!gpu.isEmpty()) {
            POLYS.removeIf(p -> p.gpu);
            drawGpu(c, tl, t, gpu);
        }
        if (POLYS.isEmpty()) return;
        for (int i = 0; i < POLYS.size(); i++) {
            Poly p = POLYS.get(i);
            float[] o = new float[12];
            for (int k = 0; k < 3; k++) {
                ChopScene.project(p.p[k * 3], p.p[k * 3 + 1], p.p[k * 3 + 2]);
                o[k * 2] = ChopScene.P[0];
                o[k * 2 + 1] = ChopScene.P[1];
                o[6 + k] = ChopScene.depth(p.p[k * 3], p.p[k * 3 + 1], p.p[k * 3 + 2]);
                o[9 + k] = p.p[k * 3 + 1];
            }
            // The fourth corner, for the bounds.
            float fx = o[2] + o[4] - o[0], fy = o[3] + o[5] - o[1];
            minX = Math.min(minX, Math.min(Math.min(o[0], o[2]), Math.min(o[4], fx)));
            maxX = Math.max(maxX, Math.max(Math.max(o[0], o[2]), Math.max(o[4], fx)));
            minY = Math.min(minY, Math.min(Math.min(o[1], o[3]), Math.min(o[5], fy)));
            maxY = Math.max(maxY, Math.max(Math.max(o[1], o[3]), Math.max(o[5], fy)));
            scr[i] = o;
        }
        float ink = Math.max(1.5f, ChopScene.size * 1.3f);
        float pad = ink + 2f;
        if (!RASTER.begin(Math.max(0f, minX - pad), Math.max(0f, minY - pad), Math.min(w, maxX + pad), Math.min(h, maxY + pad), rasterScale)) return;
        // The lava's surface hides whatever is under it: inside the lake, no pixel below y 0 is drawn.
        float cool = Math.max(0f, tl.cool(t));
        RASTER.glow(0f, 18f, mixHot(tl, t), 0.5f * (1f - cool));
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < POLYS.size(); i++) {
                Poly p = POLYS.get(i);
                if (p.overlay != (pass == 1)) continue;
                if (p.normal != null && facesAway(p)) continue;
                float[] o = scr[i];
                RASTER.secondSlot = p.second;
                RASTER.secondTint = p.secondTint;
                RASTER.secondAdd = p.secondAdd;
                RASTER.cutBelow = inLake((p.p[0] + p.p[3] + p.p[6]) / 3f, (p.p[2] + p.p[5] + p.p[8]) / 3f) ? 0f : Float.NaN;
                RASTER.face(o[0], o[1], o[2] - o[0], o[3] - o[1], o[4] - o[0], o[5] - o[1],
                        o[6], o[7] - o[6], o[8] - o[6], o[9], o[10] - o[9], o[11] - o[9],
                        p.uv[0], p.uv[1], p.uv[2], p.uv[3], p.slot, p.lit, p.tint, p.add, p.overlay);
            }
        }
        RASTER.noGlow();
        RASTER.cutBelow = Float.NaN;
        RASTER.secondSlot = -1;
        RASTER.ink(Math.max(1, Math.round(ink * RASTER.scale)));
        RASTER.fade(ChopScene.sceneAlpha);
        c.image(TEX_RASTER, RASTER.px, RASTER.w, RASTER.h, RASTER.x, RASTER.y, RASTER.w / RASTER.scale, RASTER.h / RASTER.scale);
    }

    /**
     * Keeps a polygon above the lava: false if it is wholly under it (inside the lake); a face with one edge
     * level and the other climbing is cut at y 0. Others stay whole - the occluder still cuts them exactly.
     */
    static boolean clipToSurface(Poly p) {
        float y0 = p.p[1], uy = p.p[4] - p.p[1], vy = p.p[7] - p.p[1];
        float top = Math.max(Math.max(y0, y0 + uy), Math.max(y0 + vy, y0 + uy + vy));
        if (top >= -0.01f) {
            float bottom = Math.min(Math.min(y0, y0 + uy), Math.min(y0 + vy, y0 + uy + vy));
            if (bottom >= 0f) return true;
            // One edge level, the other not: keep the part of the climbing edge above 0.
            if (Math.abs(uy) < 1e-4f && Math.abs(vy) > 1e-4f) cut(p, 6, 2, 3, y0, vy);
            else if (Math.abs(vy) < 1e-4f && Math.abs(uy) > 1e-4f) cut(p, 3, 0, 1, y0, uy);
            return true;
        }
        // Under the surface - but only the lake has one.
        float cx = (p.p[0] + p.p[3] + p.p[6]) / 3f, cz = (p.p[2] + p.p[5] + p.p[8]) / 3f;
        return !inLake(cx, cz);
    }

    /**
     * Cuts a polygon along its climbing edge (corner index {@code e}: 3 for U, 6 for V) to the part above y 0;
     * {@code uvA}/{@code uvB} are that edge's texture start and end in {@code uv}.
     */
    private static void cut(Poly p, int e, int uvA, int uvB, float y0, float dy) {
        float k0 = Math.max(0f, Math.min(1f, -y0 / dy));
        float a = dy > 0 ? k0 : 0f, b = dy > 0 ? 1f : k0;
        if (b - a >= 0.999f) return;
        float ex = p.p[e] - p.p[0], ey = p.p[e + 1] - p.p[1], ez = p.p[e + 2] - p.p[2];
        int other = e == 3 ? 6 : 3;
        float sx = ex * a, sy = ey * a, sz = ez * a;
        // Move the origin (and the other corner with it) along the edge, and shorten the edge.
        p.p[0] += sx; p.p[1] += sy; p.p[2] += sz;
        p.p[other] += sx; p.p[other + 1] += sy; p.p[other + 2] += sz;
        p.p[e] = p.p[0] + ex * (b - a); p.p[e + 1] = p.p[1] + ey * (b - a); p.p[e + 2] = p.p[2] + ez * (b - a);
        // The texture runs along the edge in uv[uvA] .. uv[uvB] (u for U, v for V).
        int ia = e == 3 ? 0 : 1, ib = e == 3 ? 2 : 3;
        float t0 = p.uv[ia], t1 = p.uv[ib];
        p.uv[ia] = t0 + (t1 - t0) * a;
        p.uv[ib] = t0 + (t1 - t0) * b;
    }

    /** The GUI textures the GPU-drawn slots read (registered by the client, put in the preview's map). */
    public static final String TEX_MAGMA = "eruption_magma", TEX_CRACKS = "eruption_cracks", TEX_LAVA_GREY = "eruption_lava_grey",
            TEX_CUBE = "eruption_cube", TEX_BLACKSTONE = "eruption_blackstone";
    /** The vent's cooled rock: a GPU-only slot (the raster has eight). */
    static final int SLOT_STONE = 8;
    /** The basalt pillars at the sides: GPU-only slots, the column's side and its top. */
    static final int SLOT_BASALT = 9, SLOT_BASALT_TOP = 10;
    /** The bust's guaranteed drop, adopted from Hypixel's pack per creature ("eruption_sure_jawbus"...). */
    public static final String TEX_SURE = "eruption_sure_";
    public static final String TEX_BASALT_SIDE = "eruption_basalt_side", TEX_BASALT_TOP = "eruption_basalt_top";

    private static String gpuTexture(int slot) {
        return switch (slot) {
            case SLOT_MAGMA -> TEX_MAGMA;
            case SLOT_CRACKS -> TEX_CRACKS;
            case SLOT_LAVA_GREY -> TEX_LAVA_GREY;
            case SLOT_CUBE -> TEX_CUBE;
            case SLOT_STONE -> TEX_BLACKSTONE;
            case SLOT_BASALT -> TEX_BASALT_SIDE;
            case SLOT_BASALT_TOP -> TEX_BASALT_TOP;
            default -> null;
        };
    }

    private static final int[][] GPU_SIZE = {null, null, null, {16, 48}, {16, 48}, {16, 320}, {64, 64}, null, {16, 16}, {16, 16}, {16, 16}};

    /**
     * Draws plain cubes as GUI quads: blocks far to near, each block's faces turned to the camera; an outline pass
     * first (four stamps of every face in black, so only the silhouette shows), then the faces, then the second
     * texture (the cracks) over them. The lava's glow, which the raster does per pixel, is a tint by height here.
     */
    static void drawGpu(ChopScene.Canvas c, EruptionTimeline tl, long t, List<Poly> polys) {
        List<Poly> faces = new ArrayList<>();
        Map<Integer, Float> depth = new HashMap<>();
        Map<Integer, Integer> count = new HashMap<>();
        for (Poly p : polys) {
            if (p.normal != null && facesAway(p)) continue;
            if (gpuTexture(p.slot) == null) continue;
            faces.add(p);
            float cx = (p.p[3] + p.p[6]) / 2f, cy = (p.p[4] + p.p[7]) / 2f, cz = (p.p[5] + p.p[8]) / 2f;
            depth.merge(p.group, ChopScene.depth(cx, cy, cz), Float::sum);
            count.merge(p.group, 1, Integer::sum);
        }
        faces.sort((a, b) -> Float.compare(depth.get(a.group) / count.get(a.group), depth.get(b.group) / count.get(b.group)));
        float[][] q = new float[faces.size()][];
        for (int i = 0; i < faces.size(); i++) {
            Poly p = faces.get(i);
            ChopScene.project(p.p[0], p.p[1], p.p[2]);
            float px = ChopScene.P[0], py = ChopScene.P[1];
            ChopScene.project(p.p[3], p.p[4], p.p[5]);
            float qx = ChopScene.P[0] - px, qy = ChopScene.P[1] - py;
            ChopScene.project(p.p[6], p.p[7], p.p[8]);
            float rx = ChopScene.P[0] - px, ry = ChopScene.P[1] - py;
            // The game's GUI drops a quad wound the other way (the preview draws it anyway): turn it round, as
            // ChopScene.emit does - start at the far end of the first edge and run it backwards.
            if (qx * ry - qy * rx < 0f) {
                px += qx;
                py += qy;
                qx = -qx;
                qy = -qy;
            }
            q[i] = new float[]{px, py, qx, qy, rx, ry};
        }
        int alpha = Math.round(255 * ChopScene.sceneAlpha) << 24;
        float ink = Math.max(1.5f, ChopScene.size * 1.3f);
        float[][] stamps = {{-ink, 0}, {ink, 0}, {0, -ink}, {0, ink}};
        // The outline is four stamps per face, and at partial alpha they piled up: in the fade-out the cubes stayed
        // nearly solid black-edged shapes over a floor already gone (the review, E10). The ink fades far faster.
        float sa = ChopScene.sceneAlpha;
        int inkAlpha = Math.round(255 * sa * sa * sa * sa) << 24;
        if (inkAlpha >>> 24 >= 3) {
            for (int i = 0; i < faces.size(); i++) {
                Poly p = faces.get(i);
                int[] r = rect(p, p.slot);
                for (float[] o : stamps) c.quad(gpuTexture(p.slot), q[i][0] + o[0], q[i][1] + o[1], q[i][2], q[i][3], q[i][4], q[i][5], r[0], r[1], r[2], r[3], inkAlpha);
            }
        }
        float cool = Math.max(0f, tl.cool(t));
        int hot = mixHot(tl, t);
        for (int i = 0; i < faces.size(); i++) {
            Poly p = faces.get(i);
            int[] r = rect(p, p.slot);
            int tint = p.tint;
            int addA = p.add >>> 24;
            // The crater's lava: the heat itself, only a touch lighter - mixing it far towards white turned it to sand.
            if (addA > 0) tint = EruptionTimeline.mix(tint, 0xFFFFFF, addA / 255f * 0.15f);
            // The magma block a little darker, so its cracks glow against it.
            else if (p.second >= 0) tint = EruptionTimeline.mix(tint, 0x000000, 0.35f);
            float lit = Math.max(0f, Math.min(1f, p.lit));
            tint = (int) ((tint >> 16 & 255) * lit) << 16 | (int) ((tint >> 8 & 255) * lit) << 8 | (int) ((tint & 255) * lit);
            // The lava's glow on what stands in it, fading with height.
            float cy = (p.p[4] + p.p[7]) / 2f;
            float glow = 0.55f * (1f - cool) * Math.max(0f, 1f - cy / 14f);
            if (glow > 0f) tint = EruptionTimeline.mix(tint, hot, glow);
            c.quad(gpuTexture(p.slot), q[i][0], q[i][1], q[i][2], q[i][3], q[i][4], q[i][5], r[0], r[1], r[2], r[3], alpha | tint);
            if (p.second >= 0) {
                int[] r2 = rect(p, p.second);
                // The cracks in the heat's own colour (not pushed towards white: that read as peach), as strong as
                // the heat is - so a bust's go out as it cools.
                int a2 = p.secondAdd >>> 24;
                int ca = Math.round(255 * ChopScene.sceneAlpha * Math.min(1f, 0.35f + 1.3f * a2 / 255f));
                c.quad(gpuTexture(p.second), q[i][0], q[i][1], q[i][2], q[i][3], q[i][4], q[i][5], r2[0], r2[1], r2[2], r2[3], ca << 24 | p.secondTint);
            }
        }
    }

    /** A polygon's texel rect in a GPU slot's texture. */
    private static int[] rect(Poly p, int slot) {
        int[] sz = GPU_SIZE[slot];
        int u0 = Math.round(p.uv[0] * sz[0]), v0 = Math.round(p.uv[1] * sz[1]);
        int u1 = Math.round(p.uv[2] * sz[0]), v1 = Math.round(p.uv[3] * sz[1]);
        return new int[]{u0, v0, Math.max(1, u1 - u0), Math.max(1, v1 - v0)};
    }

    /** Whether a box face is turned away from the camera: its outward normal takes it further away. */
    static boolean facesAway(Poly p) {
        float cx = p.p[0], cy = p.p[1], cz = p.p[2];
        return ChopScene.depth(cx + p.normal[0], cy + p.normal[1], cz + p.normal[2]) - ChopScene.depth(cx, cy, cz) < -1e-4f;
    }

    /** The lava's glow colour on what stands in it: its own orange until the heat shows, then the heat. */
    static int mixHot(EruptionTimeline tl, long t) {
        float show = ease((t - EruptionTimeline.VENT_FROM) / 600f);
        int c = EruptionTimeline.mix(0xFF7A20, tl.heatColour(t), show);
        if (tl.ending() == Ending.VIAL) c = EruptionTimeline.mix(c, 0x7CFF40, tl.green(t));
        if (tl.ending() == Ending.KUUDRA) c = EruptionTimeline.mix(EruptionTimeline.mix(c, 0x6A6A72, tl.frozen(t)), 0xE0204A, tl.carmine(t));
        return c;
    }

    /** Image pixels a GUI pixel for the 3D layer: the screen sets its GUI scale (capped at 2), the preview 2. */
    public static float rasterScale = 2f;
    public static final String TEX_RASTER = "eruption_raster";

    // ================================================================== the effects

    /** Behind the 3D layer: what is on or above the lava, and the showcase's rays. */
    static void backEffects(ChopScene.Canvas c, CrystalVeins.Fill layer, EruptionTimeline tl, long t, long pt, int w, int h, float g) {
        float a = ChopScene.sceneAlpha;
        embers(layer, tl, t, pt, w, h, g, a);
        bubbles(layer, tl, t, pt, g, a);
        if (tl.cool(t) > 0f) smoke(layer, tl, t, g, a);
        if (tl.erupt(t) >= 0f) fountain(c, layer, tl, t, w, h, g, a);
        if (tl.ending() == Ending.FLASH || tl.ending() == Ending.END_TIMES) bolts(c, layer, tl, t, w, h, g, a);
        if (tl.ending() == Ending.VIAL) geiger(c, layer, tl, t, w, h, g, a);
        if (tl.ending() == Ending.KUUDRA) swellRings(layer, tl, t, g, a);
        if (tl.ending() == Ending.VIAL) mushroom(c, layer, tl, t, g, a);
        smoke(layer, tl, t, pt, w, h, g, a);
        if (tl.drop().win()) {
            float show = ease((t - tl.arrive() + 300) / 500f);
            if (show > 0f) {
                float[] rest = dropRest(tl);
                ChopScene.project(rest[0], rest[1], rest[2]);
                int col = tl.ending() == Ending.KUUDRA ? EruptionTimeline.CARMINE : tl.ending() == Ending.VIAL ? 0x7CFF40 : tl.drop().heat.rgb;
                rays(c, layer, ChopScene.P[0], ChopScene.P[1], h, t, show, col, g);
            }
        }
    }

    /** Over the 3D layer: steam off the quench, the forge's sparks, the carmine rain. */
    static void frontEffects(ChopScene.Canvas c, CrystalVeins.Fill layer, EruptionTimeline tl, long t, long pt, int w, int h, float g) {
        float a = ChopScene.sceneAlpha;
        if (t >= tl.quenchFrom() && t < tl.quenchTo() + 1200) steam(layer, tl, t, g, a);
        if (tl.ending() == Ending.FORGE) forge(c, layer, tl, t, g, a);
        if (tl.ending() == Ending.PINCER) clacks(c, layer, tl, t, g, a);
        if (tl.ending() == Ending.FLASH) arcs(c, layer, tl, t, g, a);
        if (tl.ending() == Ending.END_TIMES) bookFire(layer, tl, t, g, a);
        if (tl.ending() == Ending.VIAL) meltdown(c, layer, tl, t, g, a);
        if (tl.ending() == Ending.KUUDRA) carmineRain(layer, tl, t, w, h, g, a);
    }

    static void sq(CrystalVeins.Fill layer, float x, float y, float s, float g, int argb) {
        layer.fill(Math.round(x / g) * g, Math.round(y / g) * g, s, s, argb);
    }

    /** Embers rising off the lava across the whole width - the sides' fill, in the lava's colour. */
    static void embers(CrystalVeins.Fill layer, EruptionTimeline tl, long t, long pt, int w, int h, float g, float a) {
        Random r = new Random(tl.seed() * 31 + 5);
        float sec = pt / 1000f;
        int col = tl.ending() == Ending.KUUDRA && tl.carmine(t) > 0.3f ? 0xE0204A : mixHot(tl, t);
        int count = 70 + (tl.erupt(t) >= 0f ? 40 : 0);
        for (int i = 0; i < count; i++) {
            float x0 = r.nextFloat() * w, speed = 0.25f + 0.35f * r.nextFloat(), ph = r.nextFloat();
            float life = (sec * speed + ph) % 1f;
            float x = x0 + 14f * (float) Math.sin(sec * 1.3f + i);
            float y = h * (0.98f - 0.9f * life);
            float al = a * (1f - life) * Math.min(1f, life * 6f);
            if (al <= 0.02f) continue;
            sq(layer, x, y, g * (i % 5 == 0 ? 2 : 1), g, Math.round(255 * al) << 24 | EruptionTimeline.mix(col, 0xFFE080, r.nextFloat() * 0.4f));
        }
    }

    /** Bubbles popping on the ring at each breath, the size of the heat. */
    static void bubbles(CrystalVeins.Fill layer, EruptionTimeline tl, long t, long pt, float g, float a) {
        int step = EruptionTimeline.step(t);
        if (step < 0 || t >= EruptionTimeline.REVEAL + 400 || tl.cool(t) > 0.5f) return;
        int heat = tl.heatColour(t);
        for (int k = Math.max(0, step - 2); k <= step; k++) {
            long since = t - EruptionTimeline.STEPS[k];
            if (since < 0 || since > 500) continue;
            Random r = new Random(k * 97L + 11);
            for (int i = 0; i < 6; i++) {
                double ang = r.nextFloat() * Math.PI * 2;
                float rad = RING * (0.7f + 0.5f * r.nextFloat());
                float life = since / 500f;
                ChopScene.project((float) Math.cos(ang) * rad, 2f + 10f * life, (float) Math.sin(ang) * rad);
                float s = g * (life < 0.3f ? 2 : 1);
                sq(layer, ChopScene.P[0], ChopScene.P[1] - life * 6 * g, s, g, Math.round(255 * a * (1f - life)) << 24 | heat);
            }
        }
    }

    static void smoke(CrystalVeins.Fill layer, EruptionTimeline tl, long t, float g, float a) {
        float k = tl.cool(t);
        Random r = new Random(tl.seed() * 7 + 3);
        for (int i = 0; i < 40; i++) {
            float ph = r.nextFloat(), life = Math.min(1f, (t - EruptionTimeline.REVEAL) / 1400f + ph * 0.4f);
            float x = (r.nextFloat() - 0.5f) * 50f, z = (r.nextFloat() - 0.5f) * 40f;
            ChopScene.project(x, 20f + 60f * life, z);
            float al = a * (1f - life) * 0.6f * Math.min(1f, k * 3f);
            if (al <= 0.02f) continue;
            sq(layer, ChopScene.P[0], ChopScene.P[1], g * (1 + (int) (life * 2.5f)), g, Math.round(255 * al) << 24 | 0x6A6468);
        }
    }

    /** The eruption: a column of lava, and bombs arcing out to the edges of the screen where they splash. */
    static void fountain(ChopScene.Canvas c, CrystalVeins.Fill layer, EruptionTimeline tl, long t, int w, int h, float g, float a) {
        long from = tl.eruptAt();
        float sec = (t - from) / 1000f;
        int col = tl.drop().heat.rgb;
        if (tl.ending() == Ending.VIAL) col = 0x7CFF40;
        // The flash where it goes off.
        if (sec < 0.5f) {
            ChopScene.project(0f, 30f, 0f);
            ChopScene.glow(c, ChopScene.P[0], ChopScene.P[1], 140f * ChopScene.size, col, 0.8f * (1f - sec / 0.5f));
        }
        Random r = new Random(tl.seed() * 13 + 1);
        // The column: squares shot straight up, falling back.
        for (int i = 0; i < 220; i++) {
            float ph = r.nextFloat() * 1.1f, life = sec - ph;
            if (life < 0f || life > 1.6f) continue;
            float vx = (r.nextFloat() - 0.5f) * 40f, vz = (r.nextFloat() - 0.5f) * 30f, vy = 150f + 120f * r.nextFloat();
            float y = vy * life - 160f * life * life;
            if (y < 0f) continue;
            ChopScene.project(vx * life, 20f + y, vz * life);
            int cc = EruptionTimeline.mix(col, 0xFF4010, r.nextFloat() * 0.5f);
            sq(layer, ChopScene.P[0], ChopScene.P[1], g * (r.nextFloat() < 0.45f ? 2 : 1), g, Math.round(255 * a * Math.min(1f, (1.6f - life) * 2f)) << 24 | cc);
        }
        // The bombs: big squares arcing to the screen's edges, a splash where each lands.
        for (int i = 0; i < 14; i++) {
            float side = i % 2 == 0 ? -1f : 1f;
            float delay = 0.05f * i, life = sec - delay;
            if (life < 0f) continue;
            float fly = 0.9f + 0.3f * r.nextFloat();
            float tx = side * (w * (0.32f + 0.17f * r.nextFloat())), ty = h * (0.62f + 0.28f * r.nextFloat());
            ChopScene.project(0f, 40f, 0f);
            float x0 = ChopScene.P[0], y0 = ChopScene.P[1];
            if (life < fly) {
                float k = life / fly;
                float x = x0 + tx * k, y = y0 + (ty + y0 * 0 - y0) * k - h * 0.55f * 4 * k * (1 - k);
                int s = (int) (2 * g);
                sq(layer, x, y, s + g, g, Math.round(255 * a) << 24 | 0x2A1810);
                sq(layer, x + g * 0.5f, y + g * 0.5f, s, g, Math.round(255 * a) << 24 | EruptionTimeline.mix(col, 0xFF3010, 0.3f));
            } else if (life < fly + 0.6f) {
                float k = (life - fly) / 0.6f;
                float x = x0 + tx, y = y0 + ty;
                for (int j = 0; j < 7; j++) {
                    double ang = Math.PI * (0.1 + 0.8 * j / 6.0);
                    float dist = g * (3 + 10 * k);
                    sq(layer, x + (float) Math.cos(ang) * dist * (j % 2 == 0 ? 1 : -1), y - (float) Math.sin(ang) * dist * (1 - k) + k * k * 20 * g / 3,
                            g, g, Math.round(255 * a * (1f - k)) << 24 | col);
                }
            }
        }
    }

    /** Lightning: the Flash's bolt into the vent, the End Times' three. Pixel zigzags from the top of the screen. */
    static void bolts(ChopScene.Canvas c, CrystalVeins.Fill layer, EruptionTimeline tl, long t, int w, int h, float g, float a) {
        long[] at = tl.ending() == Ending.FLASH ? new long[]{EruptionTimeline.F_BOLT}
                : tl.ending() == Ending.END_TIMES ? new long[]{EruptionTimeline.T_BOLTS0, EruptionTimeline.T_BOLTS1, EruptionTimeline.T_BOLTS2} : new long[0];
        for (int b = 0; b < at.length; b++) {
            long since = t - at[b];
            if (since < 0 || since > 260) continue;
            float k = 1f - since / 260f;
            Random r = new Random(at[b] * 31 + b);
            float tx = b == at.length - 1 ? 0f : (b == 0 ? -70f : 60f);
            ChopScene.project(tx, 24f, tx * 0.2f);
            float x = ChopScene.P[0] + (r.nextFloat() - 0.5f) * w * 0.3f, y = -g;
            float ex = ChopScene.P[0], ey = ChopScene.P[1];
            int col = tl.ending() == Ending.END_TIMES ? 0xFFD0C0 : 0xE0F0FF;
            int segs = 12;
            for (int s = 0; s < segs; s++) {
                float u = (s + 1f) / segs;
                float nx = x + (ex - x) * (1f / (segs - s)) + (s == segs - 1 ? 0 : (r.nextFloat() - 0.5f) * 8 * g);
                float ny = y + (ey - y) / (segs - s);
                int steps = Math.max(1, (int) (Math.hypot(nx - x, ny - y) / g));
                for (int q = 0; q <= steps; q++) {
                    float px = x + (nx - x) * q / steps, py = y + (ny - y) * q / steps;
                    sq(layer, px - 2 * g, py - g, g * 6, g * 3, Math.round(90 * a * k) << 24 | col);
                    sq(layer, px - g, py, g * 2, g, Math.round(255 * a * k) << 24 | 0xFFFFFF);
                }
                // A branch off every third joint, short and fading.
                if (s % 3 == 1) {
                    float bx = x, by = y;
                    float dir = r.nextFloat() < 0.5f ? -1f : 1f;
                    for (int q = 0; q < 3; q++) {
                        float cx = bx + dir * (2 + r.nextFloat() * 4) * g, cy = by + (3 + r.nextFloat() * 4) * g;
                        int st = Math.max(1, (int) (Math.hypot(cx - bx, cy - by) / g));
                        for (int j = 0; j <= st; j++)
                            sq(layer, bx + (cx - bx) * j / st, by + (cy - by) * j / st, g, g, Math.round(200 * a * k * (1f - q / 3f)) << 24 | 0xFFFFFF);
                        bx = cx;
                        by = cy;
                    }
                }
                x = nx;
                y = ny;
            }
            ChopScene.glow(c, ex, ey, 90f * ChopScene.size, col, 0.9f * k);
        }
    }

    /** Steam off the quench: pale squares rising off the drop. */
    static void steam(CrystalVeins.Fill layer, EruptionTimeline tl, long t, float g, float a) {
        float[] rest = dropRest(tl);
        float rise = tl.rise(t);
        float y0 = -10f + (rest[1] + 10f) * rise;
        long from = tl.quenchFrom();
        Random r = new Random(tl.seed() * 41 + 9);
        for (int i = 0; i < 60; i++) {
            float ph = r.nextFloat() * 0.9f;
            float life = (t - from) / 1000f - ph;
            if (life < 0f || life > 1.1f) continue;
            float k = life / 1.1f;
            float x = rest[0] + (r.nextFloat() - 0.5f) * 30f + 12f * k * (r.nextFloat() - 0.5f);
            float z = rest[2] + (r.nextFloat() - 0.5f) * 20f;
            ChopScene.project(x, y0 + 8f + 50f * k, z);
            sq(layer, ChopScene.P[0], ChopScene.P[1], g * (1 + (int) (k * 2.5f)), g, Math.round(200 * a * (1f - k)) << 24 | 0xE8E4E0);
        }
    }

    /** A burst of pixel sparks from (x, y), {@code since} ms after it went off, over {@code life} ms. */
    static void burst(CrystalVeins.Fill layer, float x, float y, long since, long life, long seed, int n, float speed, int hot, int cool, float g, float a) {
        if (since < 0 || since > life) return;
        float k = since / (float) life;
        Random r = new Random(seed);
        for (int i = 0; i < n; i++) {
            double ang = r.nextFloat() * Math.PI * 2;
            float sp = speed * (0.4f + 0.6f * r.nextFloat());
            float d = sp * (1f - (1f - k) * (1f - k));
            float px = x + (float) Math.cos(ang) * d, py = y + (float) Math.sin(ang) * d * 0.8f + 40f * k * k;
            sq(layer, px, py, g * (r.nextFloat() < 0.25f && k < 0.4f ? 2 : 1), g, Math.round(255 * a * (1f - k)) << 24 | (i % 3 == 0 ? hot : cool));
        }
    }

    /** The drop's place on the screen now. */
    static float[] dropScreen(EruptionTimeline tl, long t) {
        float[] p = dropPose(tl, t);
        ChopScene.project(p[0], p[1], p[2]);
        return new float[]{ChopScene.P[0], ChopScene.P[1]};
    }

    /** The pincer: a spray of sparks off its claws on each clack. */
    static void clacks(ChopScene.Canvas c, CrystalVeins.Fill layer, EruptionTimeline tl, long t, float g, float a) {
        long[] at = {EruptionTimeline.P_SNAP + 120, EruptionTimeline.P_CLACKS0, EruptionTimeline.P_CLACKS1};
        for (int i = 0; i < at.length; i++) {
            long since = t - at[i];
            if (since < 0 || since > 450) continue;
            float[] s = dropScreen(tl, t);
            if (since < 90) ChopScene.glow(c, s[0], s[1], 50f * ChopScene.size, 0xFF8040, 0.7f * (1f - since / 90f));
            burst(layer, s[0], s[1], since, 450, at[i] * 7 + i, 34 + 8 * i, 34f * ChopScene.size, 0xFFF0C0, 0xFF6A20, g, a);
        }
    }

    /**
     * Flash I: the bolt's charge still in the book as it rises - short arcs crackling round it, and a strobe of
     * glows on it, faster and faster until it settles.
     */
    static void arcs(ChopScene.Canvas c, CrystalVeins.Fill layer, EruptionTimeline tl, long t, float g, float a) {
        long from = tl.riseFrom(), to = tl.arrive() + 900;
        if (t < from || t > to) return;
        float[] s = dropScreen(tl, t);
        float reach = 22f * ChopScene.size;
        long slot = (t - from) / 110;
        for (int k = 0; k < 2; k++) {
            Random r = new Random(slot * 31 + k * 7 + tl.seed());
            if (r.nextFloat() < 0.35f) continue;
            double ang = r.nextFloat() * Math.PI * 2;
            float x = s[0] + (float) Math.cos(ang) * reach * 0.45f, y = s[1] + (float) Math.sin(ang) * reach * 0.45f;
            double dir = ang + (r.nextFloat() - 0.5f) * 1.2f;
            int segs = 3 + r.nextInt(3);
            for (int q = 0; q < segs; q++) {
                float len = reach * (0.09f + 0.08f * r.nextFloat());
                dir += (r.nextFloat() - 0.5f) * 1.6f;
                float nx = x + (float) Math.cos(dir) * len, ny = y + (float) Math.sin(dir) * len;
                int steps = Math.max(1, (int) (len / g));
                for (int j = 0; j <= steps; j++) {
                    float px = x + (nx - x) * j / steps, py = y + (ny - y) * j / steps;
                    sq(layer, px - g, py - g, g * 3, g, Math.round(110 * a) << 24 | 0x6AB8FF);
                    sq(layer, px, py, g, g, Math.round(255 * a) << 24 | 0xFFFFFF);
                }
                x = nx;
                y = ny;
            }
        }
        // The strobe: a flicker of pale blue on the book, every 220 ms down to every 90.
        float u = (t - from) / (float) (to - from);
        long period = Math.round(220 - 130 * u);
        long ph = (t - from) % period;
        if (ph < 40) ChopScene.glow(c, s[0], s[1], 60f * ChopScene.size, 0xA8D8FF, 0.5f * a * (1f - u * 0.6f));
    }

    /** The Burnt Texts: the book burning - ragged pixel flames licking up off its top, embers off them. */
    static void bookFire(CrystalVeins.Fill layer, EruptionTimeline tl, long t, float g, float a) {
        if (t < EruptionTimeline.T_BOOK) return;
        float[] s = dropScreen(tl, t);
        float on = ease((t - EruptionTimeline.T_BOOK) / 500f);
        float half = 9f * ChopScene.size * dropPose(tl, t)[6];
        int cols = Math.max(4, Math.round(2f * half / g));
        long tick = t / 70;
        for (int i = 0; i < cols; i++) {
            float x = s[0] - half + i * (2f * half / cols);
            Random r = new Random(tick * 131 + i * 17);
            float edge = 1f - Math.abs((i + 0.5f) / cols * 2f - 1f);
            int tall = Math.round(on * (2 + edge * 7 + r.nextFloat() * 4));
            for (int j = 0; j < tall; j++) {
                if (j > 1 && r.nextFloat() < 0.18f) continue;
                float k = j / (float) Math.max(1, tall);
                int col = k < 0.3f ? 0xFFF4C0 : k < 0.6f ? 0xFFB030 : k < 0.85f ? 0xF05A10 : 0x9A1E08;
                sq(layer, x, s[1] - half - (j + 1) * g, g, g, Math.round(255 * a * (1f - 0.4f * k)) << 24 | col);
            }
        }
        // Embers off the flames.
        Random r = new Random(tl.seed() * 5 + 1);
        float sec = (t - EruptionTimeline.T_BOOK) / 1000f;
        for (int i = 0; i < 18; i++) {
            float ph = r.nextFloat(), sp = 0.6f + 0.6f * r.nextFloat(), dx = (r.nextFloat() - 0.5f) * 2f * half;
            float life = (sec * sp + ph) % 1f;
            sq(layer, s[0] + dx + 6f * g * (float) Math.sin(sec * 3 + i) * life, s[1] - half - life * 30f * g, g, g,
                    Math.round(220 * a * on * (1f - life)) << 24 | (i % 2 == 0 ? 0xFFC040 : 0xFF6A20));
        }
    }

    /** The forge: three hammer blows on the rising handle, each a burst of sparks. */
    static void forge(ChopScene.Canvas c, CrystalVeins.Fill layer, EruptionTimeline tl, long t, float g, float a) {
        float[] rest = dropRest(tl);
        for (int b = 0; b < EruptionTimeline.FORGE_BLOWS.length; b++) {
            long since = t - EruptionTimeline.FORGE_BLOWS[b];
            if (since < 0 || since > 600) continue;
            float k = since / 600f;
            float[] at = dropScreen(tl, t);
            float x = at[0], y = at[1] - 8f * ChopScene.size;
            if (since < 120) ChopScene.glow(c, x, y, 80f * ChopScene.size, 0xFFC060, 0.9f * (1f - since / 120f));
            Random r = new Random(b * 17L + 3);
            for (int i = 0; i < 34; i++) {
                double ang = r.nextFloat() * Math.PI * 2;
                float sp = (40f + 90f * r.nextFloat()) * ChopScene.size / 8f;
                float px = x + (float) Math.cos(ang) * sp * k * 8f, py = y + (float) Math.sin(ang) * sp * k * 8f + 60f * k * k;
                sq(layer, px, py, g, g, Math.round(255 * a * (1f - k)) << 24 | (i % 3 == 0 ? 0xFFF0C0 : 0xFFA030));
            }
        }
    }

    /** The vial: green pulse rings spreading over the lava, faster and faster, with the Geiger clicks. */
    static void geiger(ChopScene.Canvas c, CrystalVeins.Fill layer, EruptionTimeline tl, long t, int w, int h, float g, float a) {
        if (t < EruptionTimeline.V_GREEN) return;
        float sec = (t - EruptionTimeline.V_GREEN) / 1000f;
        for (int ring = 0; ring < 6; ring++) {
            float life = (sec * 0.8f + ring / 6f) % 1f;
            float rad = 20f + 200f * life;
            int n = (int) (12 + rad / 4);
            for (int i = 0; i < n; i++) {
                double ang = i * Math.PI * 2 / n;
                ChopScene.project((float) Math.cos(ang) * rad, 1f, (float) Math.sin(ang) * rad);
                sq(layer, ChopScene.P[0], ChopScene.P[1], g, g, Math.round(200 * a * (1f - life)) << 24 | 0x7CFF40);
            }
        }
        // The vial's own glow, wherever it is - in his chest, throbbing with each beat.
        if (tl.golem(t) > 0f) {
            float[] s = dropScreen(tl, t);
            float pulse = tl.vialPulse(t);
            ChopScene.glow(c, s[0], s[1], (40f + 50f * pulse) * ChopScene.size, 0x6CFF30, 0.3f + 0.5f * pulse + 0.1f * (float) Math.sin(t / 120.0));
        }
        vialColumn(c, layer, tl, t, h, g, a);
        vialBurst(c, layer, tl, t, g, a);
    }

    /** The freed vial's column of green light: a glow up to the top of the screen, pixels streaming up it. */
    static void vialColumn(ChopScene.Canvas c, CrystalVeins.Fill layer, EruptionTimeline tl, long t, int h, float g, float a) {
        float col = tl.vialColumn(t);
        if (col <= 0.01f) return;
        float[] s = dropScreen(tl, t);
        float x = s[0], y = s[1];
        float wdt = 30f * ChopScene.size;
        c.sprite(ChopScene.TEX_GLOW_NAME, x, y / 2f, wdt, y + 40f * ChopScene.size, 0f, ChopScene.alpha(0.55f * col * a) | 0x6CFF30);
        c.sprite(ChopScene.TEX_GLOW_NAME, x, y / 2f, wdt * 0.35f, y + 30f * ChopScene.size, 0f, ChopScene.alpha(0.6f * col * a) | 0xD8FFB0);
        Random r = new Random(tl.seed() * 3 + 77);
        float sec = (t - EruptionTimeline.V_FREE) / 1000f;
        for (int i = 0; i < 40; i++) {
            float ph = r.nextFloat(), sp = 0.7f + 0.8f * r.nextFloat(), dx = (r.nextFloat() - 0.5f) * wdt * 0.6f;
            float life = (sec * sp + ph) % 1f;
            sq(layer, x + dx, y - life * (y + 20f), g * (i % 4 == 0 ? 2 : 1), g, Math.round(230 * col * a * (1f - life)) << 24 | (i % 3 == 0 ? 0xE8FFC0 : 0x7CFF40));
        }
    }

    /** The vial bursting out of him: a flash, and a ring of radiation racing out over the lake. */
    static void vialBurst(ChopScene.Canvas c, CrystalVeins.Fill layer, EruptionTimeline tl, long t, float g, float a) {
        float b = tl.vialBurst(t);
        if (b < 0f || b >= 1f) return;
        float[] s = dropScreen(tl, t);
        if (b < 0.25f) ChopScene.glow(c, s[0], s[1], 90f * ChopScene.size, 0xE8FFC0, 0.9f * (1f - b / 0.25f));
        float rad = 20f + 320f * (1f - (1f - b) * (1f - b));
        // A solid shockwave, not a dotted line: enough squares round it that they touch, four rows deep (bright front,
        // green behind it), and dust kicked up just inside (the review, E20).
        int[] cols = {0xE8FFC0, 0x9CFF50, 0x6CD030, 0x3C9A1C};
        for (int ring = 0; ring < 4; ring++) {
            float rr = rad - ring * 3.5f;
            if (rr <= 0f) continue;
            int n = (int) Math.ceil(2 * Math.PI * rr * ChopScene.size / g * 1.15f) + 8;
            for (int i = 0; i < n; i++) {
                double ang = i * Math.PI * 2 / n;
                ChopScene.project((float) Math.cos(ang) * rr, 1f, (float) Math.sin(ang) * rr);
                sq(layer, ChopScene.P[0], ChopScene.P[1], g, g, Math.round(255 * a * (1f - b) * (1f - ring * 0.18f)) << 24 | cols[ring]);
            }
        }
        Random dust = new Random(tl.seed() * 7 + 3);
        for (int i = 0; i < 140; i++) {
            double ang = dust.nextFloat() * Math.PI * 2;
            float rr = rad - 6f - dust.nextFloat() * 18f;
            if (rr <= 0f) continue;
            ChopScene.project((float) Math.cos(ang) * rr, 1f + dust.nextFloat() * 6f * (1f - b), (float) Math.sin(ang) * rr);
            sq(layer, ChopScene.P[0], ChopScene.P[1], g * (i % 3 == 0 ? 2 : 1), g, Math.round(170 * a * (1f - b)) << 24 | (i % 2 == 0 ? 0x4A4A3A : 0x6A6A52));
        }
    }

    /**
     * The mushroom cloud standing up behind where Jawbus was: a stem of green pixels, and a cap that rolls - squares
     * going round a ring, over the top and under - brightest at its heart, smoke at its edges.
     */
    static void mushroom(ChopScene.Canvas c, CrystalVeins.Fill layer, EruptionTimeline tl, long t, float g, float a) {
        float k = tl.cloud(t);
        if (k < 0f) return;
        float fade = 1f - 0.45f * ease((t - tl.arrive()) / 1200f);
        float cx = GOLEM_X * 0.6f, cz = GOLEM_Z * 0.55f, top = 16f + 42f * k, rad = 9f + 30f * k;
        ChopScene.project(cx, top, cz);
        if (k < 0.3f) ChopScene.glow(c, ChopScene.P[0], ChopScene.P[1], (60f + 120f * k) * ChopScene.size, 0xD8FFB0, 0.9f * (1f - k / 0.3f));
        ChopScene.glow(c, ChopScene.P[0], ChopScene.P[1], rad * 3f * ChopScene.size, 0x6CFF30, 0.35f * fade);
        Random r = new Random(tl.seed() * 11 + 5);
        float sec = (t - EruptionTimeline.V_FREE) / 1000f;
        // The stem: a solid column, denser and wider at the foot, bright at its heart (90 loose squares read as
        // confetti - the review, E20).
        for (int i = 0; i < 300; i++) {
            float yy = r.nextFloat() * top, ang = (float) (r.nextFloat() * Math.PI * 2);
            float sr = (2.5f + 4f * (1f - yy / top)) * (float) Math.sqrt(r.nextFloat());
            ChopScene.project(cx + (float) Math.cos(ang + sec) * sr, yy, cz + (float) Math.sin(ang + sec) * sr);
            int col = sr < 1.6f ? 0xE8FFC0 : yy / top > 0.8f ? 0xB8FF70 : (i % 3 == 0 ? 0x9CFF50 : 0x5CB828);
            sq(layer, ChopScene.P[0], ChopScene.P[1], g * 2, g, Math.round(235 * a * fade) << 24 | col);
        }
        // The cap: a rolling ring, dense, its underside in shadow.
        for (int i = 0; i < 760; i++) {
            float u = (float) (r.nextFloat() * Math.PI * 2), v0 = (float) (r.nextFloat() * Math.PI * 2);
            float v = v0 + sec * 2.2f;
            float tube = rad * 0.42f * (0.7f + 0.3f * r.nextFloat()), ringR = rad * 0.62f;
            float rr = ringR + (float) Math.cos(v) * tube;
            float x = cx + (float) Math.cos(u) * rr, z = cz + (float) Math.sin(u) * rr * 0.9f, y = top + (float) Math.sin(v) * tube * 0.8f;
            ChopScene.project(x, y, z);
            float up = (float) Math.sin(v);
            int col = up > 0.55f ? 0xE8FFC0 : up > -0.1f ? 0x8CFF48 : up > -0.6f ? 0x3C9A1C : 0x2A3A22;
            sq(layer, ChopScene.P[0], ChopScene.P[1], g * (i % 5 == 0 ? 3 : 2), g, Math.round(240 * a * fade) << 24 | col);
        }
    }

    /**
     * Meltdown, in front: arcs crackling over Jawbus at every beat, and at the detonation the pieces of him -
     * iron and vine and green fire - blown out across the screen.
     */
    static void meltdown(ChopScene.Canvas c, CrystalVeins.Fill layer, EruptionTimeline tl, long t, float g, float a) {
        float[] chest = dropScreen(tl, Math.min(t, EruptionTimeline.V_FREE - 1));
        for (int b = 0; b < EruptionTimeline.V_PULSES.length; b++) {
            long since = t - EruptionTimeline.V_PULSES[b];
            if (since < 0 || since > 160 || t >= EruptionTimeline.V_FREE) continue;
            Random r = new Random(b * 131L + tl.seed());
            for (int k = 0; k < 3 + b; k++) {
                double dir = r.nextFloat() * Math.PI * 2;
                float x = chest[0], y = chest[1];
                for (int q = 0; q < 6; q++) {
                    float len = ChopScene.size * (3f + 3f * r.nextFloat());
                    dir += (r.nextFloat() - 0.5f) * 1.4f;
                    float nx = x + (float) Math.cos(dir) * len, ny = y + (float) Math.sin(dir) * len;
                    int steps = Math.max(1, (int) (len / g));
                    for (int j = 0; j <= steps; j++) {
                        sq(layer, x + (nx - x) * j / steps, y + (ny - y) * j / steps, g, g, Math.round(255 * a * (1f - since / 160f)) << 24 | (j % 2 == 0 ? 0xF0FFE0 : 0x8CFF48));
                    }
                    x = nx;
                    y = ny;
                }
            }
            ChopScene.glow(c, chest[0], chest[1], 40f * ChopScene.size, 0x8CFF48, 0.6f * (1f - since / 160f));
        }
        long since = t - EruptionTimeline.V_FREE;
        if (since < 0 || since > 1600) return;
        float s = since / 1000f;
        Random r = new Random(tl.seed() * 17 + 3);
        int[] cols = {0xD8D8D0, 0xB8B8B0, 0x8C8C84, 0x4A7A2A, 0x8CFF48, 0xE8FFC0};
        for (int i = 0; i < 110; i++) {
            double ang = r.nextFloat() * Math.PI * 2;
            float sp = ChopScene.size * (25f + 70f * r.nextFloat());
            float x = chest[0] + (float) Math.cos(ang) * sp * s, y = chest[1] + (float) Math.sin(ang) * sp * s * 0.8f + 40f * ChopScene.size * s * s;
            sq(layer, x, y, g * (i % 3 == 0 ? 3 : 2), g, Math.round(255 * a * (1f - s / 1.6f)) << 24 | cols[i % cols.length]);
        }
    }

    /**
     * Smoke and ash over the sides (the user's pick, 0.2.79): dark plumes rising off the field by the pillars and
     * drifting up off the screen, ash drifting down across it. Thicker through the build-up - the same on every run -
     * then a win clears it; a bust leaves it hanging.
     */
    static void smoke(CrystalVeins.Fill layer, EruptionTimeline tl, long t, long pt, int w, int h, float g, float a) {
        float thick = 0.35f + 0.65f * ease(t / (float) EruptionTimeline.REVEAL);
        if (tl.drop().win()) thick *= 1f - ease((t - EruptionTimeline.REVEAL - 200) / 1200f);
        if (thick <= 0.02f) return;
        float sec = pt / 1000f;
        // The plumes: from points beside the pillars, widening as they rise, leaning with a slow wind.
        float[][] sources = {{-70, -36}, {-60, -82}, {72, -36}, {64, -86}};
        Random r = new Random(tl.seed() * 19 + 7);
        int[] greys = {0x3E383C, 0x504A4E, 0x625C60, 0x746E72};
        for (int p = 0; p < sources.length; p++) {
            float z = sources[p][1], x = (sources[p][0] - 0.37f * z) / 0.93f;
            for (int i = 0; i < 120; i++) {
                float ph = r.nextFloat(), sp = 0.12f + 0.1f * r.nextFloat();
                float life = (sec * sp + ph) % 1f;
                float spread = 4f + 30f * life;
                float ox = (r.nextFloat() - 0.5f) * spread + 26f * life * life, oz = (r.nextFloat() - 0.5f) * spread * 0.6f;
                ChopScene.project(x + ox, 6f + 150f * life, z + oz);
                float al = a * thick * 0.85f * (1f - life * 0.8f) * Math.min(1f, life * 8f);
                if (al <= 0.02f) continue;
                int s = life < 0.25f ? 2 : life < 0.6f ? 3 : 4;
                sq(layer, ChopScene.P[0], ChopScene.P[1], g * s, g, Math.round(255 * al) << 24 | greys[(i + p) % greys.length]);
            }
        }
        // Ash: pale grey flecks drifting down the whole screen, side to side.
        for (int i = 0; i < 60; i++) {
            float x0 = r.nextFloat() * w, sp = 0.05f + 0.06f * r.nextFloat(), ph = r.nextFloat();
            float life = (sec * sp + ph) % 1f;
            float x = x0 + 18f * (float) Math.sin(sec * 0.9f + i);
            float y = h * (-0.05f + 1.1f * life);
            float al = a * thick * 0.7f * Math.min(1f, (1f - life) * 4f);
            if (al <= 0.02f) continue;
            sq(layer, x, y, g, g, Math.round(255 * al) << 24 | (i % 3 == 0 ? 0x9A9496 : 0x6E686A));
        }
    }

    /** Kuudra's sea heaving: rings of pale carmine running out from the middle, one every half second, as it swells. */
    static void swellRings(CrystalVeins.Fill layer, EruptionTimeline tl, long t, float g, float a) {
        if (t < EruptionTimeline.K_SWELL) return;
        float sec = (t - EruptionTimeline.K_SWELL) / 1000f;
        for (int ring = 0; ring < 5; ring++) {
            float life = sec * 0.55f - ring * 0.25f;
            life = life - (float) Math.floor(life);
            if (sec * 0.55f < ring * 0.25f) continue;
            float rad = 30f + 260f * life;
            int n = (int) (16 + rad / 3);
            for (int i = 0; i < n; i++) {
                double ang = i * Math.PI * 2 / n;
                ChopScene.project((float) Math.cos(ang) * rad, 1f, (float) Math.sin(ang) * rad * 0.9f);
                sq(layer, ChopScene.P[0], ChopScene.P[1], g * 2, g, Math.round(150 * a * (1f - life)) << 24 | 0xFF4A70);
            }
        }
    }

    /** Kuudra's ending: carmine embers raining down the whole screen once it holds the dye up. */
    static void carmineRain(CrystalVeins.Fill layer, EruptionTimeline tl, long t, int w, int h, float g, float a) {
        if (t < EruptionTimeline.K_RISE) return;
        Random r = new Random(tl.seed() * 61 + 7);
        float sec = (t - EruptionTimeline.K_RISE) / 1000f;
        for (int i = 0; i < 70; i++) {
            float x0 = r.nextFloat() * w, sp = 0.15f + 0.25f * r.nextFloat(), ph = r.nextFloat();
            float life = (sec * sp + ph) % 1f;
            float x = x0 + 10f * (float) Math.sin(sec * 2 + i);
            float y = h * (-0.05f + 1.05f * life);
            sq(layer, x, y, g, g, Math.round(230 * a * Math.min(1f, sec)) << 24 | (i % 4 == 0 ? 0xFF6080 : EruptionTimeline.CARMINE));
        }
    }

    /** The End Times: the sky darkening to a deep red at the edges - a vignette, never a wash over the middle. */
    static void darkSky(ChopScene.Canvas c, EruptionTimeline tl, long t, int w, int h) {
        float d = tl.dark(t);
        int sky = 0x2A0204;
        if (tl.ending() == Ending.VIAL) {
            d = tl.critical(t);
            sky = 0x1E7A0C;
        }
        if (tl.ending() == Ending.KUUDRA) {
            d = tl.swell(t) * (1f - ease((t - (tl.total() - EruptionTimeline.FADE)) / (float) EruptionTimeline.FADE));
            sky = 0x1A0008;
        }
        if (d <= 0.01f) return;
        int bands = 10;
        for (int i = 0; i < bands; i++) {
            float k = (bands - i) / (float) bands;
            float edge = 0.18f * (1f - i / (float) bands);
            int argb = Math.round(150 * d * k * k) << 24 | sky;
            c.fill(0, 0, w, h * edge, argb);
            c.fill(0, h * (1f - edge), w, h * edge, argb);
            c.fill(0, 0, w * edge * 0.7f, h, argb);
            c.fill(w * (1f - edge * 0.7f), 0, w * edge * 0.7f, h, argb);
        }
    }

    // ================================================================== the showcase

    static void showcase(ChopScene.Canvas c, EruptionTimeline tl, long t, int w, int h, float g) {
        float ts = Math.min(1.35f, h / 300f);
        Drop d = tl.drop();
        if (!d.win()) {
            // A bust still shows what every kill of this creature drops anyway.
            String sure = EruptionTimeline.guaranteed(tl.creature());
            float na = tl.nameAlpha(t) * ChopScene.sceneAlpha;
            if (sure != null && na > 0.02f) {
                ChopScene.project(0f, 58f, 0f);
                // The item itself, Hypixel's own icon, when its pack is loaded (the review, E8).
                c.sprite(TEX_SURE + tl.creature().name().toLowerCase(java.util.Locale.ROOT), ChopScene.P[0], ChopScene.P[1] - 15f * ts,
                        16f * ts, 16f * ts, 0f, ChopScene.alpha(na) | 0xFFFFFF);
                c.text(sure, ChopScene.P[0], ChopScene.P[1], 1.2f * ts, ChopScene.alpha(na) | 0xFFAA00);
                // Its price as it is - no count-up, grey: counting coins up is a win's.
                long price = tl.price();
                if (price > 0 && t >= tl.countStart())
                    c.text(String.format("%,d coins", price), ChopScene.P[0], ChopScene.P[1] + 13f * ts, ts, ChopScene.alpha(na) | 0xB8B8B8);
            }
            return;
        }
        float[] rest = dropRest(tl);
        ChopScene.project(rest[0], rest[1], rest[2]);
        float x = ChopScene.P[0], y = ChopScene.P[1];
        float half = (d.head ? 9f : 16f) * ChopScene.size * dropPose(tl, Math.max(t, tl.arrive()))[6];
        // Clear of the item's top: seen from above, a big icon reaches past its own half-size.
        banner(c, tl, t, x, y - half * (d.head ? 1.2f : 1.45f) - 20f * ts, h);
        labels(c, tl, t, x, y + half, h);
    }

    /** Hard-edged pixel rays of the landing heat behind the drop. */
    static void rays(ChopScene.Canvas c, CrystalVeins.Fill layer, float x, float y, int h, long t, float alpha, int rgb, float g) {
        int n = 12;
        double turn = t / 4000.0;
        for (int i = 0; i < n; i++) {
            double ang = turn + i * Math.PI * 2 / n;
            float len = h * 0.42f;
            int steps = (int) (len / g);
            for (int s = 3; s < steps; s++) {
                float k = s / (float) steps;
                float px = x + (float) Math.cos(ang) * s * g, py = y + (float) Math.sin(ang) * s * g;
                sq(layer, px, py, g, g, Math.round(110 * alpha * (1f - k) * ChopScene.sceneAlpha) << 24 | rgb);
            }
        }
        ChopScene.glow(c, x, y, h * 0.5f, rgb, 0.4f * alpha);
    }

    static void banner(ChopScene.Canvas c, EruptionTimeline tl, long t, float x, float y, int h) {
        if (!showBanner) return;
        float k = tl.bannerStamp(t);
        if (k <= 0f) return;
        float ts = Math.min(1.35f, h / 300f);
        float scale = ts * (1.5f + 1.4f * (1f - ease(k)));
        float a = Math.min(1f, k * 2.5f) * ChopScene.sceneAlpha;
        c.text(tl.bannerText(), x, y, scale, ChopScene.alpha(a) | tl.bannerColour());
    }

    static void labels(ChopScene.Canvas c, EruptionTimeline tl, long t, float x, float below, int h) {
        float na = tl.nameAlpha(t) * ChopScene.sceneAlpha;
        float ts = Math.min(1.35f, h / 300f);
        if (na > 0.02f) c.name(x, below + 8f * ts, 1.6f * ts, ChopScene.alpha(na) | tl.drop().colour, tl.letters(t));
        float la = tl.lineAlpha(t) * ChopScene.sceneAlpha;
        if (la > 0.02f) {
            if (tl.drop().oneIn > 0) {
                String of = tl.drop() == Drop.CARMINE ? "rare lava sea creatures" : tl.creature().title + " kills";
                c.text("1 in ~" + String.format("%,d", tl.drop().oneIn) + " " + of + (nthNote == null ? "" : " \u00b7 " + nthNote), x, below + 22f * ts, ts, ChopScene.alpha(la) | 0xC8C8C8);
            }
            long price = tl.price();
            if (price > 0 && t >= tl.countStart()) {
                float k = Math.min(1f, (t - tl.countStart()) / (float) EruptionTimeline.COUNT_MS);
                long shown = Math.round(price * (1f - (1f - k) * (1f - k) * (1f - k)));
                c.text(String.format("%,d coins", shown), x, below + 35f * ts, 1.25f * ts, ChopScene.alpha(la) | 0xFFD84A);
                if (tl.priceSource() != null)
                    c.text(tl.priceSource(), x, below + 45f * ts, 0.7f * ts, ChopScene.alpha(la) | 0x9A9A9A);
            }
        }
    }

    /** The menu's banner switch, set by the screen each frame. */
    public static boolean showBanner = true;
    /** "your 3rd" after the odds, set by the screen each frame; null for a test. */
    public static String nthNote;
}
