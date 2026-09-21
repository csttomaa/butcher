package com.scathabutcher.render.art;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * The cavern backdrop and the two gemstone outcrops, drawn at runtime.
 *
 * <h2>Why this is Java and not a script in tools/</h2>
 *
 * These used to be PNGs produced by {@code gen_cave.py} and {@code gen_gems.py}, which
 * carried the hazard CLAUDE.md records: the torch positions existed twice - once in the
 * script that painted the glow, once in the Java that animates flicker and embers over it
 * - and moving one without the other left the effects hanging in mid-air. The sparkle
 * anchors were worse: the script printed them and they were pasted across by hand.
 *
 * Here {@link #TORCHES} and {@link #SPARKLES} are the only copies. The art reads them to
 * decide where to paint and the cutscene reads the same fields to decide where to
 * animate, so the two cannot drift. Touch-ups are edit-and-reload - {@code /butcher
 * reload} drops the TextureBank cache and everything here is redrawn.
 *
 * <h2>The scene is shaded, not stacked</h2>
 *
 * The obvious way to build a backdrop is to fill a gradient and then paint shapes on top
 * of it. It does not work: every flat {@code fill()} erases whatever grain and light were
 * underneath, and the result is vector-flat no matter how carefully the shapes are
 * arranged - which is exactly how the first version of this file came out.
 *
 * So the cavern is defined as <em>silhouette functions</em> - {@link #ceilingAt},
 * {@link #horizonAt}, {@link #rubbleAt} - and every pixel asks which surface it belongs
 * to and how far it sits from that surface's lit edge. Colour, grain, torchlight and rim
 * light are then applied to all of them in one pass. Nothing is painted over anything, so
 * nothing ends up flat.
 *
 * Two supporting rules:
 *
 * <b>A light field, not a wash.</b> Warmth and haze are multiplied by {@link #lightAt},
 * which is bright only close to a flame and falls off steeply. Applied evenly they read as
 * a filter over a picture; applied through the field they read as light in a room.
 *
 * <b>Nothing is blurred.</b> Every soft edge is a falloff computed per pixel. Blurring an
 * image with transparency drags the black of its transparent pixels into the soft edge and
 * turns a warm glow into grey smear.
 */
public final class CaveArt {

    // ---- canvas sizes, matching what the cutscene expects ------------------
    public static final int CAVE_W = 1920, CAVE_H = 1080;
    public static final int GEM_W = 512, GEM_H = 850;
    public static final int SPARKLE_SIZE = 192;

    /**
     * Flame centres, as fractions of the backdrop.
     *
     * The one copy: the pools below are computed from these, and the cutscene's flicker,
     * halo and embers are animated at the same fractions.
     */
    public static final float[][] TORCHES = {
            {0.085f, 0.545f}, {0.288f, 0.512f}, {0.500f, 0.556f}, {0.712f, 0.509f}, {0.915f, 0.550f}
    };

    /**
     * Twinkle anchors on a gem outcrop, as fractions of its rect.
     *
     * Taken straight from the crystal geometry below, so they cannot land off the art.
     */
    public static final float[][] SPARKLES;

    /**
     * How far a flame throws, as a fraction of canvas width, and how hard it falls off.
     *
     * Kept tight on purpose. A generous reach makes five torches at similar heights merge
     * into one horizontal smear across the middle of the frame - a sunset rather than
     * torchlight. Separate pools with dark between them is the whole look.
     */
    private static final float TORCH_REACH = 0.135f;
    private static final float TORCH_FALLOFF = 2.9f;

    // ---- palette: cool stone, warm flame ------------------------------------
    private static final Color CEIL_DEEP  = new Color(0x0E1016);
    private static final Color CEIL_FACE  = new Color(0x1E222E);
    private static final Color WALL_FAR   = new Color(0x2A3040);
    private static final Color WALL_NEAR  = new Color(0x394154);
    private static final Color FLOOR_FAR  = new Color(0x424A5E);
    private static final Color FLOOR_NEAR = new Color(0x2E3444);
    private static final Color RUBBLE     = new Color(0x232836);
    private static final Color RIM        = new Color(0x8892A8);
    private static final Color FLAME      = new Color(0xFFB45C);

    private CaveArt() {}

    // =====================================================================
    // crystal geometry, shared by the art and the sparkle anchors
    // =====================================================================

    /** One crystal: base centre, tip, and half-width at the base, in fractions of the rect. */
    private record Shard(float bx, float by, float tx, float ty, float hw) {}

    /**
     * The cluster, tallest towards the middle.
     *
     * Written out rather than randomised: the outcrops should be designed, and the sparkle
     * anchors derived below have to be stable between runs.
     */
    private static final Shard[] CLUSTER = {
            new Shard(0.26f, 0.84f, 0.15f, 0.40f, 0.115f),
            new Shard(0.45f, 0.88f, 0.39f, 0.14f, 0.140f),
            new Shard(0.62f, 0.86f, 0.70f, 0.28f, 0.120f),
            new Shard(0.78f, 0.90f, 0.90f, 0.50f, 0.100f),
            new Shard(0.14f, 0.91f, 0.05f, 0.62f, 0.082f),
            new Shard(0.55f, 0.93f, 0.58f, 0.66f, 0.075f),
    };

    static {
        SPARKLES = new float[4][2];
        int[] tallest = {1, 2, 0, 3};
        for (int i = 0; i < 4; i++) {
            Shard s = CLUSTER[tallest[i]];
            SPARKLES[i][0] = s.tx() + (s.bx() - s.tx()) * 0.24f;
            SPARKLES[i][1] = s.ty() + (s.by() - s.ty()) * 0.24f;
        }
    }

    // =====================================================================
    // the cavern
    // =====================================================================

    /**
     * The top edge of the rock mound, as {fraction across, fraction down}.
     *
     * Irregular on purpose - evenly alternating high and low points read as a sawtooth cut
     * out of paper rather than as broken stone. Shared by the rock and its glow so the
     * light cannot start above or below the surface it is lying on.
     */
    private static final float[][] ROCK_RIDGE = {
            {0.00f, 0.884f}, {0.09f, 0.841f}, {0.15f, 0.858f}, {0.27f, 0.796f},
            {0.34f, 0.812f}, {0.39f, 0.788f}, {0.52f, 0.847f}, {0.61f, 0.836f},
            {0.69f, 0.869f}, {0.78f, 0.805f}, {0.88f, 0.826f}, {1.00f, 0.872f}};

    /** Ceiling teeth: {centre, half-width, depth}, all fractions. */
    private static final float[][] TEETH = {
            {0.02f, 0.045f, 0.30f}, {0.09f, 0.030f, 0.17f}, {0.155f, 0.052f, 0.38f},
            {0.23f, 0.026f, 0.13f}, {0.30f, 0.044f, 0.27f}, {0.375f, 0.034f, 0.20f},
            {0.45f, 0.056f, 0.41f}, {0.535f, 0.028f, 0.15f}, {0.60f, 0.048f, 0.31f},
            {0.675f, 0.032f, 0.19f}, {0.75f, 0.050f, 0.35f}, {0.83f, 0.030f, 0.16f},
            {0.90f, 0.042f, 0.26f}, {0.97f, 0.036f, 0.21f},
    };

    /** Foreground rubble: {centre, half-width, height above the bottom edge}. */
    private static final float[][] RUBBLE_HUMPS = {
            {0.05f, 0.11f, 0.13f}, {0.20f, 0.09f, 0.09f}, {0.33f, 0.12f, 0.16f},
            {0.50f, 0.10f, 0.11f}, {0.66f, 0.13f, 0.17f}, {0.81f, 0.09f, 0.10f},
            {0.95f, 0.11f, 0.14f},
    };

    /** Lowest point of the ceiling rock above {@code fx}, as a fraction of height. */
    private static float ceilingAt(float fx) {
        float y = 0.055f + 0.02f * noise(fx * 6f, 11.3f);
        for (float[] t : TEETH) {
            float d = Math.abs(fx - t[0]) / t[1];
            if (d < 1f) {
                // Concave taper: fat at the root, drawn quickly to a point.
                float taper = 1f - (float) Math.pow(d, 0.62);
                y = Math.max(y, t[2] * taper);
            }
        }
        return y;
    }

    /** Where floor meets wall above {@code fx}. */
    private static float horizonAt(float fx) {
        return 0.615f + (noise(fx * 4.2f, 3.1f) - 0.5f) * 0.045f;
    }

    /** Top of the foreground rubble above {@code fx}; 1 means no rubble here. */
    private static float rubbleAt(float fx) {
        float y = 1.02f;
        for (float[] r : RUBBLE_HUMPS) {
            float d = Math.abs(fx - r[0]) / r[1];
            if (d < 1f) {
                float dome = (float) Math.sqrt(1f - d * d);
                y = Math.min(y, 1f - r[2] * dome);
            }
        }
        return y;
    }

    /**
     * Ridged noise: thin bright filaments rather than soft blobs.
     *
     * Ordinary value noise gives cloudy variation, which at a distance averages out to the
     * flat wall this started as. Folding it about its midpoint and sharpening turns it into
     * veins - a line feature the eye can actually catch - which is what stops a large area
     * of stone reading as one surface.
     */
    private static float veins(float x, float y, float sharpness) {
        float n = noise(x, y);
        return (float) Math.pow(1f - Math.abs(2f * n - 1f), sharpness);
    }

    /**
     * Loose stones scattered on a surface, as a signed shade adjustment.
     *
     * A jittered grid with one stone per cell, all the same size and brightness, produces
     * a polka-dot lattice - the eye finds the grid immediately. Three things break it up:
     * most cells hold no stone at all, the radius varies per cell, and each stone is lit
     * from above rather than being a uniform disc.
     *
     * @return positive on a stone's lit crown, negative in the shadow it sits in, 0 on
     *         bare ground
     */
    private static float pebbleShade(float fx, float fy, float scale) {
        float sx = fx * scale, sy = fy * scale;
        int cx = (int) Math.floor(sx), cy = (int) Math.floor(sy);
        float best = 1f, bestDy = 0f;

        for (int oy = -1; oy <= 1; oy++) {
            for (int ox = -1; ox <= 1; ox++) {
                int gx = cx + ox, gy = cy + oy;
                if (hash(gx + 55, gy + 91) < 0.52f) continue;      // most cells are bare
                float rad = 0.16f + 0.28f * hash(gx + 7, gy + 3);  // and sizes vary
                float jx = gx + 0.12f + 0.76f * hash(gx, gy);
                float jy = gy + 0.12f + 0.76f * hash(gx + 977, gy + 311);
                float dx = sx - jx, dy = sy - jy;
                float d = (float) Math.sqrt(dx * dx + dy * dy) / rad;
                if (d < 1f && d < best) { best = d; bestDy = dy / rad; }
            }
        }
        if (best >= 1f) return 0f;
        float dome = 1f - best;
        // Lit above the centre, shadowed below it, so each stone has a form.
        return dome * dome * (0.45f - bestDy * 1.1f);
    }

    /** Wet streaks running down the wall from the tip of each ceiling tooth. */
    private static float dripAt(float fx, float fy) {
        float most = 0f;
        for (float[] t : TEETH) {
            float across = Math.abs(fx - t[0]) / (t[1] * 0.40f);
            if (across >= 1f) continue;
            float below = fy - t[2];
            if (below <= 0f) continue;
            float fade = Math.max(0f, 1f - below / 0.34f);
            most = Math.max(most, (1f - across) * fade * fade);
        }
        return most;
    }

    /** Combined warm light at a point, 0 in the dark and 1 on top of a flame. */
    private static float lightAt(float fx, float fy) {
        float sum = 0f;
        for (float[] t : TORCHES) {
            float dx = fx - t[0];
            // Y compressed a little, so a pool spreads along the wall rather than being round.
            float dy = (fy - t[1]) * 1.25f;
            float d = (float) Math.sqrt(dx * dx + dy * dy) / TORCH_REACH;
            if (d < 1f) sum += (float) Math.pow(1f - d, TORCH_FALLOFF);
        }
        return Math.min(1f, sum);
    }

    /** Deterministic value noise, three octaves - painterly variation without a texture. */
    private static float noise(float x, float y) {
        float v = 0f, amp = 0.5f, freq = 1f, norm = 0f;
        for (int o = 0; o < 3; o++) {
            v += amp * smoothNoise(x * freq, y * freq);
            norm += amp;
            amp *= 0.5f;
            freq *= 2.3f;
        }
        return v / norm;
    }

    private static float smoothNoise(float x, float y) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = hash(x0, y0), b = hash(x0 + 1, y0);
        float c = hash(x0, y0 + 1), d = hash(x0 + 1, y0 + 1);
        float top = a + (b - a) * fx;
        float bot = c + (d - c) * fx;
        return top + (bot - top) * fy;
    }

    private static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        return ((h ^ (h >> 16)) & 0xFFFF) / 65535f;
    }

    /**
     * The cavern the cutscene plays in front of.
     *
     * One pass over every pixel: decide which surface it is on, shade that surface, then
     * apply grain, torchlight, rim light and depth to all of them together.
     */
    public static BufferedImage backdrop() {
        BufferedImage img = Paint.image(CAVE_W, CAVE_H);
        int[] row = new int[CAVE_W];

        // Columns change slowly, so the silhouettes are evaluated once per column.
        float[] ceil = new float[CAVE_W], horiz = new float[CAVE_W], rub = new float[CAVE_W];
        for (int x = 0; x < CAVE_W; x++) {
            float fx = x / (float) CAVE_W;
            ceil[x] = ceilingAt(fx);
            horiz[x] = horizonAt(fx);
            rub[x] = rubbleAt(fx);
        }

        for (int y = 0; y < CAVE_H; y++) {
            float fy = y / (float) CAVE_H;
            for (int x = 0; x < CAVE_W; x++) {
                float fx = x / (float) CAVE_W;
                float light = lightAt(fx, fy);

                float r, g, b;
                float rimAmount = 0f;

                if (fy < ceil[x]) {
                    // --- ceiling rock. Darkest thing in the frame; it reads as mass.
                    float into = fy / Math.max(0.001f, ceil[x]);      // 0 at the roof, 1 at the tip
                    Color c = Paint.mix(CEIL_DEEP, CEIL_FACE, (float) Math.pow(into, 0.55));
                    float grain = 0.74f + noise(fx * 26f, fy * 20f) * 0.52f;
                    float v = veins(fx * 30f, fy * 24f, 14f) * 0.22f;
                    r = c.getRed() * grain + 26 * v;
                    g = c.getGreen() * grain + 28 * v;
                    b = c.getBlue() * grain + 34 * v;
                    // A tooth catches light on its underside, from the torches below.
                    rimAmount = (float) Math.pow(into, 6) * 0.9f;

                } else if (fy > rub[x]) {
                    // --- foreground rubble. Near, dark, and it frames the block.
                    float into = (fy - rub[x]) / Math.max(0.001f, 1.02f - rub[x]);
                    float grain = 0.70f + noise(fx * 30f, fy * 26f) * 0.52f;
                    Color c = Paint.mix(RUBBLE, FLOOR_NEAR, 0.25f);
                    r = c.getRed() * grain; g = c.getGreen() * grain; b = c.getBlue() * grain;

                    float ps = pebbleShade(fx, fy, 38f) * 22f;
                    r += ps; g += ps * 1.06f; b += ps * 1.26f;
                    rimAmount = (float) Math.pow(1f - into, 9) * 1.15f;

                } else if (fy > horiz[x]) {
                    // --- floor, running towards the camera and darkening as it comes.
                    float into = (fy - horiz[x]) / Math.max(0.001f, 1f - horiz[x]);
                    Color c = Paint.mix(FLOOR_FAR, FLOOR_NEAR, (float) Math.pow(into, 0.75));
                    // Grain stretched along the floor plane, so it reads as receding ground.
                    float grain = 0.80f + noise(fx * 14f, fy * 40f) * 0.42f;
                    r = c.getRed() * grain; g = c.getGreen() * grain; b = c.getBlue() * grain;

                    // Loose stones, larger as the ground comes towards the camera.
                    float ps = pebbleShade(fx, fy, 30f + 26f * into) * 26f;
                    r += ps; g += ps * 1.06f; b += ps * 1.24f;
                    rimAmount = (float) Math.pow(1f - into, 11) * 0.62f;

                } else {
                    // --- back wall between the ceiling and the floor.
                    float span = Math.max(0.001f, horiz[x] - ceil[x]);
                    float into = (fy - ceil[x]) / span;
                    Color c = Paint.mix(WALL_FAR, WALL_NEAR, (float) Math.pow(into, 0.8));
                    float grain = 0.78f + noise(fx * 18f, fy * 15f) * 0.46f;
                    // Broad strata, then damp patches, then veins: three scales of
                    // variation. One alone still averages out to a flat wall at a glance.
                    float strata = 0.92f + 0.16f * noise(fx * 3.4f, 7.7f);
                    float damp = 1f - 0.20f * (float) Math.pow(noise(fx * 5.2f, fy * 4.1f), 2.2);
                    // Sharp and faint. Broad, bright filaments near a flame stop reading
                    // as mineral in the rock and start reading as electricity over it.
                    float v = veins(fx * 22f, fy * 17f, 13f);
                    float fine = veins(fx * 64f, fy * 48f, 17f) * 0.45f;

                    r = c.getRed() * grain * strata * damp;
                    g = c.getGreen() * grain * strata * damp;
                    b = c.getBlue() * grain * strata * damp;

                    // Veins pick up the torchlight, so they read as mineral rather than paint.
                    float vlit = (v + fine) * (0.26f + 0.50f * light);
                    r += 17 * vlit; g += 19 * vlit; b += 24 * vlit;

                    // Damp streaks under the ceiling teeth.
                    float drip = dripAt(fx, fy) * 0.34f;
                    r *= 1f - drip; g *= 1f - drip * 0.94f; b *= 1f - drip * 0.80f;
                }

                // --- torchlight, through the field -------------------------
                float lit = 1f + 1.45f * light;
                r *= lit; g *= lit; b *= lit;
                float warm = light * 0.66f;
                r += (FLAME.getRed() - r) * warm;
                g += (FLAME.getGreen() - g) * warm * 0.70f;
                b += (FLAME.getBlue() - b) * warm * 0.36f;

                // --- rim light on every lit edge ---------------------------
                if (rimAmount > 0.002f) {
                    float k = rimAmount * (0.22f + 0.95f * light);
                    r += (RIM.getRed() - r) * Math.min(0.85f, k);
                    g += (RIM.getGreen() - g) * Math.min(0.85f, k);
                    b += (RIM.getBlue() - b) * Math.min(0.85f, k);
                }

                // --- warm air, thickest near the flames --------------------
                float haze = (float) Math.pow(light, 1.6) * 0.22f;
                if (haze > 0.003f) {
                    r += (FLAME.getRed() - r) * haze;
                    g += (FLAME.getGreen() - g) * haze * 0.8f;
                    b += (FLAME.getBlue() - b) * haze * 0.5f;
                }

                // --- vignette ----------------------------------------------
                float vx = (fx - 0.5f) * 2f, vy = (fy - 0.5f) * 2f;
                float vig = 1f - 0.34f * (float) Math.pow(Math.min(1f, (vx * vx + vy * vy) * 0.62f), 1.4);
                r *= vig; g *= vig; b *= vig;

                row[x] = 0xFF000000 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
            }
            img.setRGB(0, y, CAVE_W, 1, row, 0, CAVE_W);
        }

        flames(img);
        return img;
    }

    /**
     * The flame itself: a smooth radial core written per pixel.
     *
     * Stacking translucent ellipses of decreasing size - the obvious way - leaves visible
     * concentric rings, which is the tell that a glow was assembled rather than lit.
     */
    private static void flames(BufferedImage img) {
        int reach = (int) (CAVE_W * 0.055f);
        for (float[] t : TORCHES) {
            int cx = (int) (t[0] * CAVE_W), cy = (int) (t[1] * CAVE_H);
            for (int y = Math.max(0, cy - reach); y < Math.min(CAVE_H, cy + reach); y++) {
                for (int x = Math.max(0, cx - reach); x < Math.min(CAVE_W, cx + reach); x++) {
                    float dx = (x - cx) / (float) reach;
                    float dy = (y - cy) / (float) reach * 0.78f;   // flame is taller than wide
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d >= 1f) continue;

                    float core = (float) Math.pow(1f - d, 3.4);
                    int p = img.getRGB(x, y);
                    float r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                    r += (255 - r) * Math.min(1f, core * 1.5f);
                    g += (243 - g) * Math.min(1f, core * 1.25f);
                    b += (208 - b) * Math.min(1f, core * 0.85f);
                    img.setRGB(x, y, 0xFF000000 | clamp(r) << 16 | clamp(g) << 8 | clamp(b));
                }
            }
        }
    }

    // =====================================================================
    // the gemstone outcrops
    // =====================================================================

    /**
     * The crystals, in greyscale.
     *
     * Greyscale on purpose: the cutscene multiplies a colour into this, so any hue painted
     * here would fight the tint. It is also why the crystals are a separate sprite from the
     * rock they grow out of - one tinted image cannot hold both.
     *
     * Each shard is three flat facets with a dark seam between them, not a gradient. Flat
     * facets are what make a crystal read as cut; smooth shading over the same silhouette
     * reads as a rubbery spike.
     */
    public static BufferedImage gems(boolean mirrored) {
        BufferedImage img = Paint.image(GEM_W, GEM_H);
        Graphics2D g = Paint.graphics(img);

        // Back to front, so the tall middle shards sit over the small outer ones.
        int[] order = {4, 5, 3, 0, 2, 1};
        for (int i : order) shard(g, CLUSTER[i], mirrored);

        g.dispose();
        return img;
    }

    /**
     * One crystal: a faceted hexagonal prism, not a triangle.
     *
     * The first attempt drew each shard as a tapering spike with a highlight down the
     * middle, and they read as darts - the silhouette of a gem is a prism with parallel
     * sides and a short pyramid cap, and the taper is what made them look like arrows.
     *
     * Three vertical facets of clearly separated value do the rest. Flat facets with a
     * crisp seam between them are what make a crystal read as cut; a gradient over the
     * same silhouette reads as a rubbery spike however carefully it is shaded.
     */
    private static void shard(Graphics2D g, Shard s, boolean mirrored) {
        float bx = x(s.bx(), mirrored) * GEM_W, by = s.by() * GEM_H;
        float tx = x(s.tx(), mirrored) * GEM_W, ty = s.ty() * GEM_H;
        float hw = s.hw() * GEM_W;

        // Top quarter is the cap; the rest has near-parallel sides.
        final float CAP = 0.26f;
        float shoulderY = ty + (by - ty) * CAP;
        float sx = bx + (tx - bx) * (1f - CAP);
        float shw = hw * 0.90f;

        // Greyscale: the cutscene multiplies a colour through this.
        int[] faceVal = {0xA2A2A2, 0xE6E6E6, 0x606060};
        int[] capVal  = {0xB4B4B4, 0xF2F2F2, 0x6E6E6E};
        if (mirrored) {   // light comes from the same side of the screen for both outcrops
            faceVal = new int[]{0x606060, 0xE6E6E6, 0xA2A2A2};
            capVal  = new int[]{0x6E6E6E, 0xF2F2F2, 0xB4B4B4};
        }

        float[] cuts = {0f, 0.34f, 0.68f, 1f};
        for (int i = 0; i < 3; i++) {
            float a = cuts[i], b = cuts[i + 1];

            Path2D body = new Path2D.Float();
            body.moveTo(bx - hw + 2 * hw * a, by);
            body.lineTo(bx - hw + 2 * hw * b, by);
            body.lineTo(sx - shw + 2 * shw * b, shoulderY);
            body.lineTo(sx - shw + 2 * shw * a, shoulderY);
            body.closePath();
            g.setColor(new Color(faceVal[i]));
            g.fill(body);

            Path2D cap = new Path2D.Float();
            cap.moveTo(sx - shw + 2 * shw * a, shoulderY);
            cap.lineTo(sx - shw + 2 * shw * b, shoulderY);
            cap.lineTo(tx, ty);
            cap.closePath();
            g.setColor(new Color(capVal[i]));
            g.fill(cap);
        }

        // Darker towards the root, so the crystal has depth rather than one flat value.
        for (int i = 0; i < 18; i++) {
            float t = i / 18f;
            float yTop = by - (by - shoulderY) * (0.55f * (1f - t));
            g.setColor(new Color(0, 0, 0, 9));
            Path2D band = new Path2D.Float();
            band.moveTo(bx - hw, by);
            band.lineTo(bx + hw, by);
            band.lineTo(bx + hw, yTop);
            band.lineTo(bx - hw, yTop);
            band.closePath();
            g.fill(band);
        }

        // Seams between the facets, and the silhouette edge.
        g.setStroke(new java.awt.BasicStroke(Math.max(1.1f, hw * 0.045f),
                java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x2E2E2E));
        for (int i = 1; i <= 2; i++) {
            float a = cuts[i];
            g.draw(new java.awt.geom.Line2D.Float(
                    bx - hw + 2 * hw * a, by, sx - shw + 2 * shw * a, shoulderY));
            g.draw(new java.awt.geom.Line2D.Float(
                    sx - shw + 2 * shw * a, shoulderY, tx, ty));
        }
        Path2D edge = new Path2D.Float();
        edge.moveTo(bx - hw, by);
        edge.lineTo(sx - shw, shoulderY);
        edge.lineTo(tx, ty);
        edge.lineTo(sx + shw, shoulderY);
        edge.lineTo(bx + hw, by);
        g.setColor(new Color(0x262626));
        g.draw(edge);

        // One specular streak high on the bright facet.
        float ax = mirrored ? 0.78f : 0.22f;
        g.setStroke(new java.awt.BasicStroke(Math.max(2f, hw * 0.16f),
                java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 215));
        g.draw(new java.awt.geom.Line2D.Float(
                sx - shw + 2 * shw * (ax + 0.20f), shoulderY + (by - shoulderY) * 0.06f,
                sx - shw + 2 * shw * (ax + 0.26f), shoulderY + (by - shoulderY) * 0.48f));
    }

    /**
     * The stone the crystals grow out of, opaque, drawn over their roots so they read as
     * growing from it rather than standing in front of it.
     */
    public static BufferedImage rock(boolean mirrored) {
        BufferedImage img = Paint.image(GEM_W, GEM_H);

        float[][] ridge = ROCK_RIDGE;

        // Per pixel, so the mound carries the same grain and rim treatment as the cavern
        // rather than being a flat cut-out sitting in front of it.
        for (int py = 0; py < GEM_H; py++) {
            float fy = py / (float) GEM_H;
            for (int px = 0; px < GEM_W; px++) {
                float fx = x(px / (float) GEM_W, mirrored);
                float top = ridgeAt(ridge, fx);
                if (fy < top) continue;

                float into = (fy - top) / Math.max(0.001f, 1f - top);
                float grain = 0.78f + noise(fx * 22f, fy * 18f) * 0.42f;
                Color c = Paint.mix(new Color(0x2C323E), new Color(0x13161D),
                        (float) Math.pow(into, 0.7));
                float r = c.getRed() * grain, g = c.getGreen() * grain, b = c.getBlue() * grain;

                // Lit lip along the top edge.
                // Softer and wider than a hairline: a hard bright edge along the whole
                // ridge is what makes a shape look cut out rather than lit.
                float rim = (float) Math.pow(1f - into, 13) * 0.72f;
                r += (RIM.getRed() - r) * Math.min(0.55f, rim);
                g += (RIM.getGreen() - g) * Math.min(0.55f, rim);
                b += (RIM.getBlue() - b) * Math.min(0.55f, rim);

                img.setRGB(px, py, 0xFF000000 | clamp(r) << 16 | clamp(g) << 8 | clamp(b));
            }
        }
        return img;
    }

    /** Linear interpolation down the rock's ridge line. */
    private static float ridgeAt(float[][] ridge, float fx) {
        for (int i = 0; i < ridge.length - 1; i++) {
            if (fx >= ridge[i][0] && fx <= ridge[i + 1][0]) {
                float t = (fx - ridge[i][0]) / (ridge[i + 1][0] - ridge[i][0]);
                return ridge[i][1] + (ridge[i + 1][1] - ridge[i][1]) * t;
            }
        }
        return ridge[ridge.length - 1][1];
    }

    /**
     * Soft coloured bounce on the rock around each crystal root.
     *
     * Two things here were cutting the light off with a visible straight line.
     *
     * The first was a horizontal ramp - the glow simply started at a fixed height up the
     * sprite, which drew a level edge straight across the rock. It now follows the rock's
     * own ridge, so the light sits on the surface it is bouncing off.
     *
     * The second is the sprite's own boundary: this image is only as wide as the outcrop,
     * so any glow still alive at its edge stops dead there. The falloff is now forced to
     * zero before every edge, and the light that should spill out across the cave floor is
     * a separate, much larger {@link #gemBloom} drawn behind the outcrop instead.
     */
    public static BufferedImage rockGlow(boolean mirrored) {
        BufferedImage img = Paint.image(GEM_W, GEM_H);
        float[][] ridge = ROCK_RIDGE;

        for (int py = 0; py < GEM_H; py++) {
            float fy = py / (float) GEM_H;
            for (int px = 0; px < GEM_W; px++) {
                float fxRaw = px / (float) GEM_W;
                float fx = x(fxRaw, mirrored);

                // Summed, not maxed. Taking the strongest of six separate falloffs leaves
                // six visible discs on the rock; adding them and rolling off the total
                // merges into one band of bounce, which is what light off a cluster does.
                float sum = 0f;
                for (Shard sh : CLUSTER) {
                    float cx = x(sh.bx(), mirrored) * GEM_W;
                    float cy = sh.by() * GEM_H;
                    float dx = (px - cx) / (GEM_W * 0.34f);
                    float dy = (py - cy) / (GEM_H * 0.15f);
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d < 1f) sum += (float) Math.pow(1f - d, 1.7);
                }
                float best = 1f - (float) Math.exp(-sum * 1.25f);
                if (best <= 0.002f) continue;

                // Light lies ON the rock, so it starts at the rock's own edge.
                float top = ridgeAt(ridge, fx);
                float below = Math.min(1f, Math.max(0f, (fy - top) / 0.055f));

                // ...and is gone before the sprite runs out, or the sprite's edge becomes
                // the edge of the light.
                float edge = Math.min(Math.min(fxRaw, 1f - fxRaw) / 0.10f, (1f - fy) / 0.08f);
                edge = Math.min(1f, Math.max(0f, edge));

                int a = (int) (96 * best * below * edge);
                if (a > 0) img.setRGB(px, py, (a << 24) | 0xFFFFFF);
            }
        }
        return img;
    }

    /**
     * The wide, faint halo the cluster throws into the cave around it.
     *
     * Drawn behind the outcrop at roughly twice its width, so the light reaches past the
     * rock instead of ending at the sprite's border. Deliberately very low alpha: this is
     * the spill, and the bright part of the light is {@link #rockGlow}.
     */
    public static BufferedImage gemBloom() {
        int w = 640, h = 640;
        BufferedImage img = Paint.image(w, h);
        float cx = w / 2f, cy = h * 0.62f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float dx = (x - cx) / (w * 0.50f);
                float dy = (y - cy) / (h * 0.44f);
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d >= 1f) continue;
                int a = (int) (58 * Math.pow(1f - d, 2.6));
                if (a > 0) img.setRGB(x, y, (a << 24) | 0xFFFFFF);
            }
        }
        return img;
    }

    /** A four-point star, for the twinkles. */
    /** Size of the vignette sheet. Low, because it is nothing but a smooth ramp. */
    public static final int VIGNETTE = 256;

    /**
     * A soft black frame, transparent in the middle.
     *
     * Drawn over the whole screen and faded up as the run goes on, so the picture
     * closes in on the worm chop by chop. Written per pixel for the usual reason: a
     * stack of translucent ellipses leaves visible rings, and blurring an RGBA image
     * would drag black out of the transparent centre (see the notes in CLAUDE.md).
     *
     * The falloff is measured on an ellipse in SCREEN fractions rather than on a circle,
     * so the corners darken evenly whatever the aspect ratio - on a 21:9 monitor a
     * circular vignette leaves the left and right edges barely touched.
     */
    public static BufferedImage vignette() {
        int s = VIGNETTE;
        BufferedImage img = Paint.image(s, s);
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float fx = (x + 0.5f) / s * 2f - 1f;
                float fy = (y + 0.5f) / s * 2f - 1f;
                float r = (float) Math.sqrt(fx * fx + fy * fy) / 1.41421f;
                // Nothing at all across the middle, then a smooth climb to the corners.
                float t = (r - 0.34f) / 0.66f;
                if (t <= 0f) continue;
                t = t > 1f ? 1f : t;
                float a = t * t * (3f - 2f * t);
                img.setRGB(x, y, Math.round(a * 255f) << 24);
            }
        }
        return img;
    }

    public static BufferedImage sparkle() {
        int s = SPARKLE_SIZE;
        BufferedImage img = Paint.image(s, s);
        float c = s / 2f;

        // Halo written per pixel; stacked ellipses would ring.
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float dx = (x + 0.5f - c) / c, dy = (y + 0.5f - c) / c;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d >= 1f) continue;
                int a = (int) (150 * Math.pow(1f - d, 3.2));
                if (a > 0) img.setRGB(x, y, (a << 24) | 0xFFFFFF);
            }
        }

        Graphics2D g = Paint.graphics(img);
        Path2D star = new Path2D.Float();
        float lg = c * 0.95f, sm = c * 0.11f;
        star.moveTo(c, c - lg);
        star.lineTo(c + sm, c - sm);
        star.lineTo(c + lg, c);
        star.lineTo(c + sm, c + sm);
        star.lineTo(c, c + lg);
        star.lineTo(c - sm, c + sm);
        star.lineTo(c - lg, c);
        star.lineTo(c - sm, c - sm);
        star.closePath();
        g.setColor(new Color(255, 255, 255, 240));
        g.fill(star);
        g.dispose();
        return img;
    }


    /** Mirrors an x fraction for the right-hand outcrop. */
    private static float x(float fx, boolean mirrored) {
        return mirrored ? 1f - fx : fx;
    }

    private static int clamp(float v) {
        int i = (int) v;
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }
}
