package com.butcher.render;

import com.butcher.anim.HellionDice;
import com.butcher.anim.HellionTimeline;
import com.butcher.anim.HellionTimeline.Attunement;
import com.butcher.anim.HellionTimeline.Ending;

import java.util.Random;

import static com.butcher.anim.HellionTimeline.ease;

/**
 * Draws a frame of "Hellion" onto a {@link ChopScene.Canvas} - the same routine for the game and the
 * offline preview, on the Chopping Block's box renderer (its camera, boxes and ink outline).
 *
 * The blaze is the vanilla model: an 8-cube head and twelve 2x2x8 rods in three rings of four, radius
 * 9, 7 and 5, at heights -2, 2 and 11 (model space, y down), turning at the vanilla rates - the middle
 * ring the other way - each rod bobbing on its own cosine (BlazeModel, read with javap). Its texture is
 * the game's own, adopted at runtime, never bundled.
 *
 * Effects are pixel art: squares on a grid one model-texel across, and the pillars' flames on a
 * PixelLayer (one image a frame - see PixelLayer for why).
 */
public final class HellionScene {
    private HellionScene() {}

    /** The vanilla blaze texture (64 x 32), adopted from the game. */
    public static final String TEX_BLAZE = "hellion_blaze";

    // Faces in ChopScene's order {+X, -X, +Y, -Y, +Z, -Z}; the front of the head on +Z, towards the camera.
    static final int[][] HEAD_R = {{16, 8, 8, 8}, {0, 8, 8, 8}, {8, 0, 8, 8}, {16, 0, 8, 8}, {8, 8, 8, 8}, {24, 8, 8, 8}};
    static final int[][] ROD_R = {{4, 18, 2, 8}, {0, 18, 2, 8}, {2, 16, 2, 2}, {4, 16, 2, 2}, {2, 18, 2, 8}, {6, 18, 2, 8}};
    static final String[] BLAZE6 = {TEX_BLAZE, TEX_BLAZE, TEX_BLAZE, TEX_BLAZE, TEX_BLAZE, TEX_BLAZE};

    /** World height of the model's y 0 (the head's centre) - model y runs down, world y up. */
    static final float HEAD_Y = 4f;

    /** The cell grid the pillars and the pixel effects sit on, in GUI pixels. */
    public static int unitFor(int h) {
        return Math.max(2, Math.round(h / 120f));
    }

    /**
     * @param pillars where the side pillars' flame cells go - a PixelLayer in game
     */
    /** Draws the pixel layer at once, before the showcase's words, so nothing on it covers them. Set by the screen. */
    public static Runnable flushLayer;

    public static void draw(ChopScene.Canvas c, CrystalVeins.Fill pillars, HellionTimeline tl, long t, int w, int h) {
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

        Attunement att = tl.attunement(t);
        float clang = HellionTimeline.clang(t);

        pillars(c, pillars, tl, t, w, h, att, clang, master);

        // A glow of the shield's colour behind the blaze.
        ChopScene.project(0f, HEAD_Y, 0f);
        float burn = tl.burnOut(t), shatter = tl.shatter(t);
        float glowA = (0.30f + 0.35f * clang) * (burn >= 0f ? 1f - burn : 1f) * (shatter >= 0f ? 1f - 0.6f * shatter : 1f);
        ChopScene.glow(c, ChopScene.P[0], ChopScene.P[1], 46f * ChopScene.size, att.rgb, glowA);

        blaze(c, tl, t, att, clang);
        sigils(c, tl, t, att, clang, w, h);
        if (shatter >= 0f) shards(c, tl, t, w, h);
        if (burn >= 0f) ash(c, tl, t, h);

        if (tl.drop().win()) {
            if (tl.drop().ending == Ending.DYE) flames(c, pillars, tl, t, w, h);
            if (tl.rolls()) die(c, pillars, tl, t, w, h);
            else item(c, pillars, tl, t, w, h);
        }
        ChopScene.sceneAlpha = 1f;
    }

    // ================================================================== camera

    /** How far in the pillars are pulled while the camera is zoomed (1 = where they stand). */
    static float pillarPull = 1f;

    static void camera(HellionTimeline tl, long t, int w, int h) {
        pillarPull = 1f;
        double yaw = Math.toRadians(26f + 7f * (float) Math.sin(t / 1600.0));
        double pitch = Math.toRadians(-14f);
        ChopScene.cy = (float) Math.cos(yaw);
        ChopScene.sy = (float) Math.sin(yaw);
        ChopScene.cp = (float) Math.cos(pitch);
        ChopScene.sp = (float) Math.sin(pitch);
        float appear = HellionTimeline.appear(t);
        // A slight push in through the cycle, and a kick on each clang.
        float push = 1f + 0.10f * ease(t / (float) HellionTimeline.REVEAL) + 0.025f * HellionTimeline.clang(t);
        ChopScene.size = h * 0.62f / 30f * appear * push;
        ChopScene.fx = 0f;
        ChopScene.fy = 0.5f;
        ChopScene.fz = 0f;
        ChopScene.sx0 = w / 2f;
        ChopScene.sy0 = h * 0.47f;
        if (tl.rolls()) {
            // As the die stops, the camera leans over it and closes in - after the reveal, so it tells
            // nothing. The High Class one leans in through its bullet time, and further.
            boolean hc = tl.drop().ending == Ending.HIGH_CLASS;
            long from = hc ? HellionTimeline.DICE_LAUNCH + HellionTimeline.SLOW_FROM
                    : HellionTimeline.DICE_LAUNCH + HellionDice.settleMs() - 650;
            float dur = hc ? tl.flatAt() - from : 950f;
            float k = ease((t - from) / dur);
            // The jackpot pulls back out again, so the pillars erupting gold are in the shot.
            float back = hc ? ease((t - tl.jackpot()) / 650f) : 0f;
            if (k > 0f) {
                float[] rest = HellionDice.pose(hc, Long.MAX_VALUE / 4);
                // Leans to -40, not -48: from straight above the die went flat, one square face (the review, H6).
                double p2 = Math.toRadians(-14f - 26f * k + 12f * back);
                ChopScene.cp = (float) Math.cos(p2);
                ChopScene.sp = (float) Math.sin(p2);
                float on = k * (1f - 0.75f * back);
                ChopScene.fx = rest[0] * on;
                ChopScene.fy = 0.5f + (rest[1] - 0.5f) * on;
                ChopScene.fz = rest[2] * on;
                float zoom = 1f + (hc ? 0.5f : 0.35f) * k - 0.42f * back;
                ChopScene.size *= zoom;
                // The pillars stay on screen while the camera is in close: drawn nearer by as much as it zooms
                // (the review, H8 - at the showcase they were cut by the screen's edges).
                pillarPull = Math.min(1f, 0.92f / zoom);
                ChopScene.sy0 = h * (0.47f - 0.05f * k);
            }
            if (hc) {
                // The jackpot hits: the screen shakes for a moment.
                long since = t - tl.jackpot();
                if (since >= 0 && since < 420) {
                    float amp = ChopScene.calm ? 0f : h * 0.014f * (1f - since / 420f);
                    ChopScene.sx0 += amp * (float) Math.sin(since * 0.13);
                    ChopScene.sy0 += amp * (float) Math.cos(since * 0.17);
                }
            }
        }
    }

    // ================================================================== the blaze

    /** Vanilla ticks of age, integrated over the build-up's speeding-up spin, frozen at the reveal. */
    static float ticks(long t) {
        float r = HellionTimeline.REVEAL;
        float x = Math.min(1f, t / r);
        // The integral of 1 + k (u / R)^2: t + (k / 3) R x^3.
        float ms = Math.min(t, r) + HellionTimeline.SPIN_TOP / 3f * r * x * x * x;
        return ms / 50f;
    }

    private static final float[][] PART = new float[13][];
    private static final Integer[] ORDER = new Integer[13];

    static void blaze(ChopScene.Canvas c, HellionTimeline tl, long t, Attunement att, float clang) {
        float age = ticks(t) + (tl.seed() % 97);
        float tight = 1f;
        float burn = tl.burnOut(t), shatter = tl.shatter(t);
        Random r = new Random(tl.seed() * 41 + 7);

        // Where each part is: {x, y, z, rotX, rotY, rotZ, alpha, scale}, world space (y up).
        float f = age * (float) Math.PI * -0.1f;
        for (int i = 0; i < 4; i++) {
            float y = -2f + (float) Math.cos((i * 2 + age) * 0.25f);
            PART[i] = rod((float) Math.cos(f) * 9f * tight, y, (float) Math.sin(f) * 9f * tight);
            f++;
        }
        f = 0.7853982f + age * (float) Math.PI * 0.03f;
        for (int i = 4; i < 8; i++) {
            float y = 2f + (float) Math.cos((i * 2 + age) * 0.25f);
            PART[i] = rod((float) Math.cos(f) * 7f * tight, y, (float) Math.sin(f) * 7f * tight);
            f++;
        }
        f = 0.47123894f + age * (float) Math.PI * -0.05f;
        for (int i = 8; i < 12; i++) {
            float y = 11f + (float) Math.cos(i * 1.5f + age * 0.5f);
            PART[i] = rod((float) Math.cos(f) * 5f * tight, y, (float) Math.sin(f) * 5f * tight);
            f++;
        }
        PART[12] = new float[]{0f, HEAD_Y, 0f, 0f, 0f, 0f, 1f, 1f};

        // After the reveal: a bust's rods drop and tumble, a win's are blown outwards.
        for (int i = 0; i < 12; i++) {
            float[] p = PART[i];
            float vx = (r.nextFloat() - 0.5f) * 8f, vz = (r.nextFloat() - 0.5f) * 8f, spinV = (r.nextFloat() - 0.5f) * 14f;
            if (burn >= 0f) {
                float s = burn * 1.1f;
                p[0] += vx * 0.4f * s;
                p[2] += vz * 0.4f * s;
                p[1] -= 40f * s * s;
                p[3] += spinV * s;
                p[5] += spinV * 0.6f * s;
                p[6] = 1f - ease((burn - 0.55f) / 0.45f);
            } else if (shatter >= 0f) {
                float s = shatter * 0.8f;
                float d = (float) Math.hypot(p[0], p[2]) + 0.01f;
                p[0] += p[0] / d * 60f * s;
                p[2] += p[2] / d * 60f * s;
                p[1] += (p[1] - HEAD_Y) * 2f * s + 8f * s;
                p[3] += spinV * 2f * s;
                p[5] += spinV * s;
                p[6] = 1f - ease((shatter - 0.35f) / 0.4f);
            }
        }
        // The head: crumbles away on a bust (the ash takes over), implodes into the drop on a win.
        float[] head = PART[12];
        if (burn >= 0f) head[6] = 1f - ease((burn - 0.15f) / 0.35f);
        if (shatter >= 0f) {
            head[7] = 1f - ease(shatter / 0.35f);
            head[6] = head[7] > 0.05f ? 1f : 0f;
        }

        // Back to front - no depth buffer, so one sort of the thirteen parts.
        for (int i = 0; i < 13; i++) ORDER[i] = i;
        java.util.Arrays.sort(ORDER, (a, b) -> Float.compare(
                ChopScene.depth(PART[a][0], PART[a][1], PART[a][2]), ChopScene.depth(PART[b][0], PART[b][1], PART[b][2])));

        // Fire colour: its own, pushed towards the shield's on each clang; ashen grey as it burns out.
        float tint = 0.45f * clang + (t >= HellionTimeline.HANG && t < HellionTimeline.REVEAL ? 0.25f : 0f);
        float rr = 1f + (((att.rgb >> 16) & 255) / 255f - 1f) * tint;
        float gg = 1f + (((att.rgb >> 8) & 255) / 255f - 1f) * tint;
        float bb = 1f + ((att.rgb & 255) / 255f - 1f) * tint;
        float glow = 0.18f + 0.45f * clang;
        if (burn >= 0f) {
            float grey = ease(burn / 0.35f);
            rr += (0.42f - rr) * grey;
            gg += (0.40f - gg) * grey;
            bb += (0.40f - bb) * grey;
            glow *= 1f - grey;
        }
        if (shatter >= 0f) glow = Math.min(1f, glow + 0.8f * (1f - shatter));
        float frozen = tl.frozen(t), lit = tl.ignite(t);
        if (lit > 0f) {
            rr = 1f;
            gg = 0.62f;
            bb = 0.3f;
            glow = Math.max(glow, 0.5f * lit);
        }
        if (frozen > 0f) {
            // A tint can only multiply, so a yellow rod cannot go grey - it goes cold and dark instead.
            rr += (0.40f - rr) * frozen;
            gg += (0.50f - gg) * frozen;
            bb += (0.74f - bb) * frozen;
            glow *= 1f - 0.9f * frozen;
        }

        for (int pass = 0; pass < 2; pass++) {
            ChopScene.inkWidth = pass == 0 ? Math.max(1.5f, ChopScene.size / 3f) : 0f;
            for (int k = 0; k < 13; k++) {
                float[] p = PART[ORDER[k]];
                if (p[6] < 0.02f || p[7] < 0.02f) continue;
                boolean isHead = ORDER[k] == 12;
                float hx = isHead ? 4f : 1f, hy = isHead ? 4f : 4f, hz = isHead ? 4f : 1f;
                float grow = pass == 0 ? 1.06f : 1f;
                ChopScene.box(p[0], p[1], p[2], p[3], p[4], p[5], hx * p[7] * grow, hy * p[7] * grow, hz * p[7] * grow,
                        BLAZE6, isHead ? HEAD_R : ROD_R, rr, gg, bb, glow, p[6], c);
            }
        }
        ChopScene.inkWidth = 0f;
        // Burning rods glow as they fly.
        if (lit > 0f) {
            for (int k = 0; k < 12; k++) {
                float[] p = PART[k];
                if (p[6] < 0.05f) continue;
                ChopScene.project(p[0], p[1], p[2]);
                ChopScene.glow(c, ChopScene.P[0], ChopScene.P[1], 10f * ChopScene.size, 0xFF7A1A, 0.55f * lit * p[6]);
            }
        }
    }

    /** A rod's centre in world space from its vanilla offset (the box runs 0..2, 0..8, 0..2 from it; y down). */
    private static float[] rod(float x, float y, float z) {
        return new float[]{x + 1f, HEAD_Y - (y + 4f), z + 1f, 0f, 0f, 0f, 1f, 1f};
    }

    // ================================================================== the shield

    /**
     * A 5x7 rune per attunement, one alphabet: single strokes of the same weight, read off the Elder Futhark -
     * thurisaz, algiz, othala, dagaz. The old set (a diamond, an X, a plus, a target) read as UI icons, a close
     * button and a crosshair among them (the review, H1).
     */
    private static final String[][] RUNES = {
            {"#....", "##...", "#.#..", "#..#.", "#.#..", "##...", "#...."},   // Ashen: thurisaz
            {"#.#.#", "#.#.#", ".###.", "..#..", "..#..", "..#..", "..#.."},   // Spirit: algiz
            {"..#..", ".#.#.", "#...#", ".#.#.", "..#..", ".#.#.", "#...#"},   // Auric: othala
            {"#...#", "##.##", "#.#.#", "#...#", "#.#.#", "##.##", "#...#"}};  // Crystal: dagaz

    static void sigils(ChopScene.Canvas c, HellionTimeline tl, long t, Attunement now, float clang, int w, int h) {
        float shatter = tl.shatter(t), burn = tl.burnOut(t);
        if (shatter > 0.05f) return;
        float u = Math.max(1f, Math.round(ChopScene.size * 0.55f));
        float turn = ticks(t) * 0.09f;
        for (Attunement a : Attunement.values()) {
            float ang = turn + a.ordinal() * (float) Math.PI / 2f;
            float x = (float) Math.cos(ang) * 14f, z = (float) Math.sin(ang) * 14f;
            float y = HEAD_Y + 1f;
            if (burn >= 0f) y -= 30f * burn * burn;
            ChopScene.project(x, y, z);
            boolean on = a == now;
            float scale = on ? 1.3f + 0.35f * clang : 0.85f;
            int rgb = burn >= 0f ? 0x5A5A5A : (on ? a.rgb : dim(a.rgb));
            // The current one large and lit, the rest at half.
            float alpha = (on ? 1f : 0.5f) * (burn >= 0f ? 1f - burn : 1f) * ChopScene.sceneAlpha;
            if (on && burn < 0f) ChopScene.glow(c, ChopScene.P[0], ChopScene.P[1], u * scale * 9f, a.rgb, 0.3f * alpha);
            rune(c, RUNES[a.ordinal()], ChopScene.P[0], ChopScene.P[1], u * scale, rgb, alpha);
        }
    }

    private static void rune(ChopScene.Canvas c, String[] rows, float cx, float cy, float u, int rgb, float alpha) {
        if (alpha < 0.02f) return;
        float s = Math.max(1f, Math.round(u));
        int cols = rows[0].length(), lines = rows.length;
        float left = Math.round(cx - cols / 2f * s), top = Math.round(cy - lines / 2f * s);
        // Outline: each lit cell's neighbours in near-black, then the cells.
        for (int pass = 0; pass < 2; pass++) {
            for (int j = 0; j < lines; j++) {
                for (int i = 0; i < cols; i++) {
                    if (rows[j].charAt(i) != '#') continue;
                    if (pass == 0) c.fill(left + (i - 1) * s, top + (j - 1) * s, 3 * s, 3 * s, ChopScene.alpha(0.85f * alpha) | 0x0A0606);
                    else c.fill(left + i * s, top + j * s, s, s, ChopScene.alpha(alpha) | rgb);
                }
            }
        }
    }

    // ================================================================== after the reveal

    /** The shield bursting into shards of the colour it landed on. */
    static void shards(ChopScene.Canvas c, HellionTimeline tl, long t, int w, int h) {
        float k = tl.shatter(t);
        if (k >= 1f) return;
        ChopScene.project(0f, HEAD_Y + 1f, 0f);
        float ox = ChopScene.P[0], oy = ChopScene.P[1];
        float u = Math.max(1f, Math.round(ChopScene.size * 0.6f));
        float frozen = tl.frozen(t);
        int rgb = mix(tl.drop().lands.rgb, ICE, frozen);
        Random r = new Random(tl.seed() * 3 + 11);
        float reach = (float) Math.hypot(w, h) * 0.55f;
        for (int i = 0; i < 56; i++) {
            float a = r.nextFloat() * (float) Math.PI * 2f;
            float sp = reach * (0.35f + 0.65f * r.nextFloat());
            float x = ox + (float) Math.cos(a) * sp * k, y = oy + (float) Math.sin(a) * sp * k * 0.8f + h * 0.5f * k * k;
            float s = u * (1 + r.nextInt(2));
            int col = i % 4 == 0 ? mix(0xFFFFFF, ICE, frozen) : rgb;
            square(c, x, y, s, u, ChopScene.alpha((1f - ease((k - 0.5f) / 0.5f)) * ChopScene.sceneAlpha) | col);
        }
        // The flash where the core was.
        ChopScene.glow(c, ox, oy, 60f * ChopScene.size * (0.5f + k), mix(0xFFFFFF, ICE, frozen), 0.8f * (1f - k) * (1f - 0.6f * frozen));
    }

    /** A bust's head crumbling into grey ash that drifts down. */
    static void ash(ChopScene.Canvas c, HellionTimeline tl, long t, int h) {
        float k = tl.burnOut(t);
        if (k < 0.1f) return;
        float q = (k - 0.1f) / 0.9f;
        float u = Math.max(1f, Math.round(ChopScene.size * 0.6f));
        Random r = new Random(tl.seed() * 5 + 3);
        for (int i = 0; i < 46; i++) {
            float x0 = (r.nextFloat() - 0.5f) * 8f, y0 = HEAD_Y + (r.nextFloat() - 0.5f) * 8f, z0 = (r.nextFloat() - 0.5f) * 8f;
            float fall = 10f + r.nextFloat() * 22f;
            ChopScene.project(x0 + (r.nextFloat() - 0.5f) * 6f * q, y0 - fall * q * q, z0);
            int grey = 0x585858 + 0x101010 * r.nextInt(4);
            square(c, ChopScene.P[0], ChopScene.P[1], u, u, ChopScene.alpha((1f - ease((q - 0.45f) / 0.55f)) * ChopScene.sceneAlpha) | grey);
        }
    }

    // ================================================================== the drop

    static void item(ChopScene.Canvas c, CrystalVeins.Fill layer, HellionTimeline tl, long t, int w, int h) {
        float rise = tl.itemRise(t);
        if (rise <= 0f) return;
        boolean dye = tl.drop().ending == Ending.DYE;
        ChopScene.project(0f, HEAD_Y, 0f);
        float x = ChopScene.P[0], y0 = ChopScene.P[1];
        float y = y0 - h * 0.06f * rise;
        float size = h * (dye ? 0.21f : 0.16f) * Math.min(1f, 0.3f + rise);
        float yaw = 32f + 10f * (float) Math.sin(t / 700.0);
        if (dye) {
            // The showcase: fire rays turning behind it, and a fire glow.
            float show = ease((t - tl.arrive() + 300) / 500f);
            if (show > 0f) rays(c, x, y, h, t, show, true);
            ChopScene.glow(c, x, y, 60f * ChopScene.size, 0xFF7A1A, 0.45f * Math.min(1f, rise));
        }
        c.item(x, y, size, ChopScene.sceneAlpha * Math.min(1f, rise * 2f), yaw);
        float ts = Math.min(1.35f, h / 300f);
        if (flushLayer != null) flushLayer.run();
        banner(c, tl, t, x, y - size * 0.62f - 12f * ts, h);
        labels(c, tl, t, x, y + size * 0.62f, h);
    }

    /** The drop's name, its odds and its price counting up, centred on {@code x} from {@code below} down. */
    static void labels(ChopScene.Canvas c, HellionTimeline tl, long t, float x, float below, int h) {
        float na = tl.nameAlpha(t) * ChopScene.sceneAlpha;
        float ts = Math.min(1.35f, h / 300f);
        int colour = tl.drop().colour;
        // The Flame Dye's name burns: its colour runs through the fire.
        if (tl.drop().ending == Ending.DYE) {
            float f = 0.5f + 0.5f * (float) Math.sin(t / 180.0);
            colour = f < 0.5f ? mix(FIRE[1], FIRE[2], f * 2f) : mix(FIRE[2], FIRE[3], (f - 0.5f) * 2f);
        }
        if (na > 0.02f) c.name(x, below + 8f * ts, 1.6f * ts, ChopScene.alpha(na) | colour, tl.letters(t));
        float la = tl.lineAlpha(t) * ChopScene.sceneAlpha;
        if (la > 0.02f) {
            if (tl.drop().oneIn > 0) {
                c.text("1 in ~" + String.format("%,d", tl.drop().oneIn) + " tier IV bosses" + (nthNote == null ? "" : " \u00b7 " + nthNote), x, below + 22f * ts, ts, ChopScene.alpha(la) | 0xC8C8C8);
            }
            long price = tl.price();
            if (price > 0 && t >= tl.countStart()) {
                float k = Math.min(1f, (t - tl.countStart()) / (float) HellionTimeline.COUNT_MS);
                long shown = Math.round(price * (1f - (1f - k) * (1f - k) * (1f - k)));
                c.text(String.format("%,d coins", shown), x, below + 35f * ts, 1.25f * ts, ChopScene.alpha(la) | 0xFFD84A);
                if (tl.priceSource() != null)
                    c.text(tl.priceSource(), x, below + 45f * ts, 0.7f * ts, ChopScene.alpha(la) | 0x9A9A9A);
            }
        }
    }

    /**
     * Pixel rays turning slowly behind a drop: squares stepped out along each, fading with distance. Gold
     * for the High Class Dice, the fire's colours for the Flame Dye.
     */
    static void rays(ChopScene.Canvas c, float x, float y, int h, long t, float alpha, boolean fire) {
        float u = Math.max(1f, Math.round(ChopScene.size * 0.6f));
        int n = 12;
        float turn = t / 2600f;
        float len = h * 0.55f;
        for (int i = 0; i < n; i++) {
            float ang = turn + i * (float) Math.PI * 2f / n;
            float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
            for (float r = h * 0.07f; r < len; r += u * 1.5f) {
                float fall = 1f - r / len;
                int col = fire ? FIRE[Math.min(4, (int) ((1f - fall) * 4f))] : (i % 2 == 0 ? GOLD : 0xFFE68A);
                square(c, x + cs * r, y + sn * r * 0.8f, u * (1 + (fall > 0.6f ? 1 : 0)), u,
                        ChopScene.alpha(alpha * ChopScene.sceneAlpha * fall * fall * 0.75f) | col);
            }
        }
    }

    // ================================================================== the dice

    /** Hypixel's own colour for "PRAY TO RNGESUS DROP!" (dark purple, bold in chat). */
    static final int RNGESUS = 0xAA00AA;
    /** What the Flame Dye's frozen moment greys things towards - a cold blue-grey. */
    static final int ICE = 0x9AA6B4;
    static final int GOLD = 0xFFC83A;
    /** The die is a player head: half-size 4, the hat half a unit bigger. */
    static final float DIE = 4f, DIE_HAT = 4.25f;
    static final int[][] HAT_R = {{48, 8, 8, 8}, {32, 8, 8, 8}, {40, 0, 8, 8}, {48, 0, 8, 8}, {40, 8, 8, 8}, {56, 8, 8, 8}};
    /**
     * The High Class die's six pips, on its 6 face (the -X side, skin rect 0,8): the top-left texel of
     * each 2x2 pip, in reading order - read off the NEU skin.
     */
    static final int[][] PIPS = {{1, 1}, {3, 1}, {5, 1}, {1, 5}, {3, 5}, {5, 5}};

    /**
     * The Archfiend Dice endings: the die is thrown out of the imploding core and tumbles to a stop on
     * its 6 - the throw is Blender's rigid-body simulation, baked (HellionDice). Sparks and a thud on each
     * hard bounce, a shadow under it so the floor is felt, then the camera leans over it.
     *
     * The High Class one is the jackpot (0.2.61, the user: the best drop before the dye, make it hype):
     * its last tip onto the 6 plays in bullet time, then the six pips light gold one by one and on the
     * sixth everything goes - a shake, a gold shockwave, the pillars erupting gold, rays, and a fountain
     * of coins.
     */
    static void die(ChopScene.Canvas c, CrystalVeins.Fill layer, HellionTimeline tl, long t, int w, int h) {
        if (t < HellionTimeline.DICE_LAUNCH) return;
        long ms = tl.diceMs(t);
        boolean hc = tl.drop().ending == Ending.HIGH_CLASS;
        float[] p = HellionDice.pose(hc, ms);
        // Rows of the rotation are world axes; its columns are the die's own axes in the world.
        float[] X = {p[3], p[6], p[9]}, Y = {p[4], p[7], p[10]}, Z = {p[5], p[8], p[11]};
        float grow = 0.25f + 0.75f * ease((t - HellionTimeline.DICE_LAUNCH) / 180f);
        float a = ChopScene.sceneAlpha;
        float u = Math.max(1f, Math.round(ChopScene.size * 0.6f));
        int spark = hc ? 0xFFAA00 : 0xE0301E;

        // Where it rests, for the jackpot and the text.
        float[] rest = HellionDice.pose(hc, Long.MAX_VALUE / 4);
        ChopScene.project(rest[0], rest[1], rest[2]);
        float cx = ChopScene.P[0], cyy = ChopScene.P[1];
        float half = DIE * 1.6f * ChopScene.size;
        long jp = t - tl.jackpot();
        if (hc && jp >= 0) {
            // Behind it: gold rays and a glow that stays.
            rays(c, cx, cyy, h, t, ease(jp / 400f), false);
            ChopScene.glow(c, cx, cyy, 46f * ChopScene.size, GOLD, 0.15f + 0.35f * (float) Math.exp(-jp / 500.0));
        }

        // Its shadow on the floor (y -8): darker and tighter the lower it is.
        float above = Math.max(0f, p[1] + 8f - DIE);
        float spread = 1f + above / 14f, dark = 0.5f / (1f + above / 6f);
        for (int j = -2; j <= 2; j++) {
            for (int i = -4; i <= 4; i++) {
                float ex = i / 4.5f, ez = j / 2.5f;
                if (ex * ex + ez * ez > 1f) continue;
                ChopScene.project(p[0] + i * 1.1f * spread, -8f, p[2] + j * 1.6f * spread);
                square(c, ChopScene.P[0], ChopScene.P[1], u * 1.5f, u, ChopScene.alpha(dark * a * grow) | 0x050303);
            }
        }

        // The die itself - base and hat, outlined.
        String skin = c.dropSkin();
        float glow = 0.75f * (1f - ease((t - HellionTimeline.DICE_LAUNCH) / 350f));
        if (hc && jp >= 0) glow = Math.max(glow, 0.35f * (float) Math.exp(-jp / 300.0));
        if (skin != null) {
            String[] six = {skin, skin, skin, skin, skin, skin};
            for (int pass = 0; pass < 2; pass++) {
                ChopScene.inkWidth = pass == 0 ? Math.max(1.5f, ChopScene.size / 3f) : 0f;
                float g = pass == 0 ? 1.04f : 1f;
                ChopScene.boxAxes(p[0], p[1], p[2], X, Y, Z, DIE * grow * g, DIE * grow * g, DIE * grow * g,
                        six, HEAD_R, 1f, 1f, 1f, glow, 1f, c);
                ChopScene.boxAxes(p[0], p[1], p[2], X, Y, Z, DIE_HAT * grow * g, DIE_HAT * grow * g, DIE_HAT * grow * g,
                        six, HAT_R, 1f, 1f, 1f, glow, 1f, c);
            }
            ChopScene.inkWidth = 0f;
            if (hc) pips(c, tl, t, p, X, Y, Z, skin);
        } else {
            ChopScene.project(p[0], p[1], p[2]);
            c.item(ChopScene.P[0], ChopScene.P[1], DIE * 2.4f * ChopScene.size * grow, a, 32f);
        }

        // Sparks off the floor on each hard bounce - on the die's own clock, so bullet time slows them too.
        for (long b : HellionDice.bounces()) {
            long since = ms - b;
            if (since < 0 || since > 500) continue;
            float k = since / 500f;
            float[] q = HellionDice.pose(hc, b);
            ChopScene.project(q[0], -8f, q[2]);
            float ox = ChopScene.P[0], oy = ChopScene.P[1];
            Random r = new Random(tl.seed() * 31 + b);
            for (int i = 0; i < 14; i++) {
                float ang = (float) Math.PI * (1.05f + 0.9f * r.nextFloat());
                float sp = h * (0.06f + 0.08f * r.nextFloat());
                float x = ox + (float) Math.cos(ang) * sp * k * 1.6f, y = oy + (float) Math.sin(ang) * sp * k + h * 0.12f * k * k;
                int col = i % 3 == 0 ? 0xFFF3B0 : spark;
                square(c, x, y, u, u, ChopScene.alpha((1f - k) * a) | col);
            }
            ChopScene.glow(c, ox, oy, 26f * ChopScene.size, spark, 0.45f * (1f - k));
        }

        if (hc && jp >= 0) jackpot(c, layer, tl, t, cx, cyy - half * 0.4f, w, h);

        float ts = Math.min(1.35f, h / 300f);
        // The pixel layer (coins, fire) goes down now, so the words are drawn over it (the review, H7).
        if (flushLayer != null) flushLayer.run();
        banner(c, tl, t, cx, cyy - half - 16f * ts, h);
        labels(c, tl, t, cx, cyy + half + 4f * ts, h);
    }

    /**
     * The High Class die's pips lighting gold, one at a time: each pip's own texels drawn again over the
     * face tinted gold (black stays black, the white pip turns gold), flashing white as it lights.
     */
    static void pips(ChopScene.Canvas c, HellionTimeline tl, long t, float[] p, float[] X, float[] Y, float[] Z, String skin) {
        int lit = tl.pipsLit(t);
        if (lit <= 0) return;
        float hh = DIE;
        for (int i = 0; i < lit; i++) {
            int pu = PIPS[i][0], pv = PIPS[i][1];
            float flash = (float) Math.exp(-(t - tl.pipAt(i)) / 180.0);
            // The -X face: its texture runs along +Z from the -Z edge, and down -Y from the +Y edge.
            float out = hh + 0.06f;
            float ox = p[0] - X[0] * out - Z[0] * hh + Y[0] * hh + Z[0] * hh * pu / 4f - Y[0] * hh * pv / 4f;
            float oy = p[1] - X[1] * out - Z[1] * hh + Y[1] * hh + Z[1] * hh * pu / 4f - Y[1] * hh * pv / 4f;
            float oz = p[2] - X[2] * out - Z[2] * hh + Y[2] * hh + Z[2] * hh * pu / 4f - Y[2] * hh * pv / 4f;
            float s = hh / 2f;
            int tint = mix(GOLD, 0xFFFFFF, 0.65f * flash);
            // Each lit pip a little inside its own square, so neighbouring pips keep a dark gap between them - drawn
            // edge to edge, a row of three read as one gold bar (the review, H6).
            float in = s * 0.14f, sz = s - 2f * in;
            float qx = ox + (Z[0] - Y[0]) * in, qy = oy + (Z[1] - Y[1]) * in, qz = oz + (Z[2] - Y[2]) * in;
            ChopScene.worldQuad(skin, new int[]{pu, 8 + pv, 2, 2}, qx, qy, qz, Z[0] * sz, Z[1] * sz, Z[2] * sz,
                    -Y[0] * sz, -Y[1] * sz, -Y[2] * sz, ChopScene.alpha(ChopScene.sceneAlpha) | tint, c);
            ChopScene.project(ox + (Z[0] - Y[0]) * s / 2f, oy + (Z[1] - Y[1]) * s / 2f, oz + (Z[2] - Y[2]) * s / 2f);
            ChopScene.glow(c, ChopScene.P[0], ChopScene.P[1], ChopScene.size * (5f + 8f * flash), GOLD, 0.3f + 0.5f * flash);
        }
    }

    /**
     * The jackpot: a gold shockwave and a burst of gold and red off the die, and a fountain of coins -
     * spinning pixel coins, on the pixel layer - thrown up out of it for a second and a half.
     */
    static void jackpot(ChopScene.Canvas c, CrystalVeins.Fill layer, HellionTimeline tl, long t, float cx, float cy, int w, int h) {
        long since = t - tl.jackpot();
        float u = Math.max(1f, Math.round(ChopScene.size * 0.6f));
        float a = ChopScene.sceneAlpha;
        // The shockwave: a ring of squares racing out.
        float k = Math.min(1f, since / 550f);
        if (k < 1f) {
            float reach = (float) Math.hypot(w, h) * 0.55f;
            for (int i = 0; i < 64; i++) {
                float ang = i / 64f * (float) Math.PI * 2f;
                float r = reach * ease(k);
                square(c, cx + (float) Math.cos(ang) * r, cy + (float) Math.sin(ang) * r * 0.7f, u * 2f, u,
                        ChopScene.alpha((1f - k) * a) | (i % 2 == 0 ? GOLD : 0xFFF3B0));
            }
            ChopScene.glow(c, cx, cy, 50f * ChopScene.size * (0.6f + 0.6f * k), 0xFFE68A, 0.6f * (1f - k));
        }
        // The burst.
        float b = Math.min(1f, since / 900f);
        if (b < 1f) {
            Random r = new Random(tl.seed() * 7 + 29);
            for (int i = 0; i < 64; i++) {
                float ang = r.nextFloat() * (float) Math.PI * 2f;
                float sp = h * (0.22f + 0.3f * r.nextFloat());
                float x = cx + (float) Math.cos(ang) * sp * ease(b), y = cy + (float) Math.sin(ang) * sp * ease(b) * 0.75f + h * 0.08f * b * b;
                int col = i % 3 == 0 ? 0xFFF3B0 : (i % 3 == 1 ? 0xFFAA00 : 0xE0301E);
                square(c, x, y, u * (1 + i % 2), u, ChopScene.alpha((1f - b) * a) | col);
            }
        }
        // The fountain.
        float g = unitFor(h);
        Random r = new Random(tl.seed() * 13 + 1);
        for (int i = 0; i < 120; i++) {
            float vx = (r.nextFloat() - 0.5f) * h * 1.1f, vy = -h * (1.0f + 0.6f * r.nextFloat()), spin = 9f + r.nextFloat() * 8f;
            float s = (since - i * 15) / 1000f;
            if (s < 0f || s > 1.8f) continue;
            float x = cx + vx * s, y = cy + vy * s + h * 2.1f * s * s;
            if (y > h + 4) continue;
            float fade = a * (s > 1.5f ? 1f - (s - 1.5f) / 0.3f : 1f);
            coin(layer, x, y, g, Math.abs((float) Math.cos(s * spin + i)), fade);
        }
    }

    /** A pixel coin, 4 cells round, turned edge-on as {@code face} goes to 0 - gold, a rim, a glint. */
    private static final String[] COIN = {".##.", "#++#", "#++#", ".##."};

    static void coin(CrystalVeins.Fill layer, float x, float y, float g, float face, float alpha) {
        int wide = face > 0.66f ? 4 : (face > 0.25f ? 2 : 1);
        int from = (4 - wide) / 2;
        float left = Math.round(x / g - wide / 2f) * g, top = Math.round(y / g - 2f) * g;
        int a = Math.round(alpha * 255) << 24;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < wide; col++) {
                char ch = wide == 1 ? '#' : COIN[row].charAt(from + col);
                if (ch == '.') continue;
                int rgb = ch == '+' ? (row == 1 && col == from ? 0xFFF6C8 : 0xFFD24A) : 0xB8860B;
                if (wide == 1) rgb = 0xE0A82A;
                layer.fill(left + col * g, top + row * g, g, g, a | rgb);
            }
        }
    }

    /** "PRAY TO RNGESUS DROP!" stamped in from big, with a flash behind it. */
    /** The menu's banner switch, set by the screen each frame. */
    public static boolean showBanner = true;
    /** "your 3rd" after the odds, set by the screen each frame; null for a test. */
    public static String nthNote;
    /** The drop line's own tier word and its colour, set by the screen each frame; null: Hypixel's top word. */
    public static String bannerWord;

    static void banner(ChopScene.Canvas c, HellionTimeline tl, long t, float x, float y, int h) {
        if (!showBanner) return;
        float k = tl.bannerStamp(t);
        if (k <= 0f) return;
        float ts = Math.min(1.35f, h / 300f);
        float scale = ts * (1.5f + 1.4f * (1f - ease(k)));
        float a = Math.min(1f, k * 2.5f) * ChopScene.sceneAlpha;
        String word = bannerWord == null ? "PRAY TO RNGESUS DROP!" : bannerWord;
        int rgb = com.butcher.detect.HellionDrops.tierColour(word);
        if (k < 1f) ChopScene.glow(c, x, y, 40f * ts * 4f, rgb, 0.6f * (1f - k));
        c.text(word, x, y, scale, ChopScene.alpha(a) | rgb);
    }

    // ================================================================== the Flame Dye

    /** Fire, light to dark: white-hot, yellow, orange, red, ember. */
    private static final int[] FIRE = {0xFFF6C8, 0xFFD040, 0xFF8A1A, 0xE0401A, 0x7A1A0A};

    /**
     * The Flame Dye - the rarest thing Hellion can show (one in half a million), so the most (0.2.61, the
     * user: extravagant, extraordinary). Time stops just after the core breaks and everything hangs cold;
     * an ember at the core swells on three heartbeats, each pulling the cold in. Then a supernova: two
     * rings of fire, the frozen rods catching and flying off as fireballs, the pillars erupting, a curtain
     * of fire rising along the whole bottom of the screen with embers going up over everything, and a
     * vortex of flame spiralling into the dye as it rises. It stands in turning fire rays, its name
     * burning in letter by letter.
     */
    static void flames(ChopScene.Canvas c, CrystalVeins.Fill layer, HellionTimeline tl, long t, int w, int h) {
        ember(c, tl, t, h);
        float ig = tl.ignite(t);
        if (ig <= 0f) return;
        long since = t - HellionTimeline.IGNITE;
        ChopScene.project(0f, HEAD_Y, 0f);
        float ox = ChopScene.P[0], oy = ChopScene.P[1];
        float u = Math.max(1f, Math.round(ChopScene.size * 0.6f));
        float a = ChopScene.sceneAlpha;
        ChopScene.light(0f, HEAD_Y, 0f, 40f, 0xFF7A20, ig);
        ChopScene.glow(c, ox, oy, 56f * ChopScene.size, 0xFF6A1A, 0.5f * ig);

        curtain(layer, tl, t, w, h);

        // The supernova: a flash where the ember was, and two rings of fire racing out.
        float k = Math.min(1f, since / 650f);
        Random r = new Random(tl.seed() * 17 + 3);
        if (since < 500) ChopScene.glow(c, ox, oy, 80f * ChopScene.size, 0xFFE0A0, 0.7f * (1f - since / 500f));
        float reach = (float) Math.hypot(w, h) * 0.6f;
        for (int ring = 0; ring < 2; ring++) {
            float kr = Math.min(1f, (since - ring * 140) / (ring == 0 ? 650f : 500f));
            if (kr <= 0f || kr >= 1f) continue;
            for (int i = 0; i < 80; i++) {
                float ang = i / 80f * (float) Math.PI * 2f + (ring * 0.04f);
                float sp = reach * ease(kr) * (0.85f + 0.15f * r.nextFloat());
                float x = ox + (float) Math.cos(ang) * sp, y = oy + (float) Math.sin(ang) * sp * 0.7f;
                square(c, x, y, u * (ring == 0 ? 1 + i % 2 : 1), u, ChopScene.alpha((1f - kr) * a) | (ring == 0 ? FIRE[1 + i % 3] : FIRE[i % 2]));
            }
        }

        // The standing column: each flame pixel rises, cools and goes out, and starts again.
        float sec = since / 1000f;
        float start = Math.min(1f, since / 160f);
        for (int i = 0; i < 150; i++) {
            float phase = r.nextFloat(), speed = 0.9f + 0.6f * r.nextFloat(), side = r.nextFloat() - 0.5f;
            float life = (sec * speed + phase) % 1f;
            float width = h * 0.26f * (1f - 0.7f * life) * (0.6f + 0.4f * ig);
            float x = ox + side * width + (float) Math.sin(sec * 7f + i) * u;
            float y = oy + h * 0.06f - life * h * (0.32f + 0.2f * ig);
            int col = FIRE[Math.min(4, (int) (life * 5f))];
            float al = start * a * (1f - life * life) * Math.min(1f, ig * 1.6f);
            square(c, x, y, u * (life < 0.5f ? 2 : 1), u, ChopScene.alpha(al) | col);
        }

        // The vortex: three arms of flame spiralling in to where the dye forms.
        long end = tl.arrive() + 400;
        if (since > 150 && t < end) {
            float rise = tl.itemRise(t);
            float tx = ox, ty = oy - h * 0.06f * rise;
            float fade = Math.min(1f, (since - 150) / 300f) * (1f - ease((t - (end - 400)) / 400f));
            float R = h * 0.42f;
            for (int arm = 0; arm < 3; arm++) {
                for (int j = 0; j < 22; j++) {
                    float ph = (j / 22f + sec * 0.8f) % 1f;
                    float rr = R * (float) Math.pow(1f - ph, 1.3);
                    float ang = arm * (float) Math.PI * 2f / 3f + 5.5f * ph + sec * 2.4f;
                    float x = tx + (float) Math.cos(ang) * rr, y = ty + (float) Math.sin(ang) * rr * 0.6f;
                    int col = FIRE[Math.max(0, Math.min(4, (int) ((1f - ph) * 4.5f)))];
                    square(c, x, y, u * (ph > 0.6f ? 2 : 1), u, ChopScene.alpha(fade * a * (0.4f + 0.6f * ph)) | col);
                }
            }
        }
    }

    /**
     * The stopped moment: an ember at the core that swells on each heartbeat, and before each beat a ring
     * of cold squares pulled in to it, warming as they go.
     */
    static void ember(ChopScene.Canvas c, HellionTimeline tl, long t, int h) {
        if (t < HellionTimeline.FREEZE + 250 || t > HellionTimeline.IGNITE + 60) return;
        ChopScene.project(0f, HEAD_Y, 0f);
        float ox = ChopScene.P[0], oy = ChopScene.P[1];
        float u = Math.max(1f, Math.round(ChopScene.size * 0.6f));
        float a = ChopScene.sceneAlpha;
        int beats = 0;
        long last = -1;
        for (long hb : HellionTimeline.HEARTS) {
            if (t >= hb) {
                beats++;
                last = hb;
            }
            // The pull before each beat.
            float k = (t - (hb - 450)) / 450f;
            if (k >= 0f && k < 1f) {
                float R = h * 0.36f * (1f - ease(k));
                for (int i = 0; i < 24; i++) {
                    float ang = i / 24f * (float) Math.PI * 2f + k * 0.8f;
                    int col = mix(ICE, FIRE[2], ease(k));
                    square(c, ox + (float) Math.cos(ang) * R, oy + (float) Math.sin(ang) * R * 0.7f, u, u,
                            ChopScene.alpha(a * (0.3f + 0.7f * k)) | col);
                }
            }
        }
        float pulse = last < 0 ? 0f : (float) Math.exp(-(t - last) / 220.0);
        float appear = Math.min(1f, (t - HellionTimeline.FREEZE - 250) / 200f);
        int rad = 1 + beats + Math.round(2f * pulse);
        for (int j = -rad; j <= rad; j++) {
            for (int i = -rad; i <= rad; i++) {
                int d = Math.abs(i) + Math.abs(j);
                if (d > rad) continue;
                int col = d <= rad / 3 ? FIRE[0] : (d <= 2 * rad / 3 ? FIRE[1] : FIRE[3]);
                square(c, ox + i * u, oy + j * u, u, u, ChopScene.alpha(a * appear) | col);
            }
        }
        ChopScene.glow(c, ox, oy, ChopScene.size * (12f + 6f * beats + 14f * pulse), 0xFF7A1A, appear * (0.3f + 0.45f * pulse));
    }

    /**
     * The curtain of fire along the whole bottom of the screen, on the pixel layer: columns of flame
     * flickering at their own heights, white-hot at the foot and dark at the tips, and embers rising off
     * it over everything.
     */
    static void curtain(CrystalVeins.Fill layer, HellionTimeline tl, long t, int w, int h) {
        long since = t - HellionTimeline.IGNITE;
        float k = ease(since / 700f) * ChopScene.sceneAlpha;
        if (k <= 0.01f) return;
        float g = unitFor(h);
        int cols = (int) Math.ceil(w / g);
        Random r = new Random(tl.seed() * 3 + 77);
        for (int col = 0; col < cols; col++) {
            float jitter = r.nextFloat();
            float n = 0.5f + 0.25f * (float) Math.sin(col * 0.7f + t * 0.006f) + 0.25f * (float) Math.sin(col * 0.29f - t * 0.0042f + jitter * 6f);
            int rows = Math.round(h * 0.22f * k * (0.45f + 0.55f * n) / g);
            for (int row = 0; row < rows; row++) {
                float f = row / (float) Math.max(1, rows);
                int colour = FIRE[Math.min(4, (int) (f * 5f))];
                float al = k * (1f - f * f) * 0.92f;
                layer.fill(col * g, h - (row + 1) * g, g, g, (Math.round(al * 255) << 24) | colour);
            }
        }
        float sec = since / 1000f;
        for (int i = 0; i < 70; i++) {
            float x0 = r.nextFloat() * w, speed = 0.35f + 0.4f * r.nextFloat(), ph = r.nextFloat();
            float life = (sec * speed + ph) % 1f;
            float x = x0 + (float) Math.sin(sec * 2f + i) * g * 2f, y = h - life * h * 1.05f;
            int colour = FIRE[Math.min(4, 1 + (int) (life * 4f))];
            float al = k * (1f - life);
            layer.fill(Math.round(x / g) * g, Math.round(y / g) * g, g, g, (Math.round(al * 255) << 24) | colour);
        }
    }

    // ================================================================== the pillars

    /** The fight's own fire pillar: four terracotta blocks, top to bottom yellow, orange, red, brown. */
    public static final String[] TERRACOTTA = {"hellion_tc_brown", "hellion_tc_red", "hellion_tc_orange", "hellion_tc_yellow"};
    /** The vanilla names, bottom block first - adopted from the game at runtime. */
    public static final String[] TERRACOTTA_VANILLA = {"brown_terracotta", "red_terracotta", "orange_terracotta", "yellow_terracotta"};
    /** The game's animated fire (16 x 512, 32 frames) and its soul fire - Crystal burns blue. */
    public static final String TEX_FIRE = "hellion_fire", TEX_FIRE_GREY = "hellion_fire_grey";

    /**
     * The fire's colour is the shield's, outright (the user, 0.2.59): the grey fire tinted. Ashen is
     * lifted a little - its own dark grey multiplied into the fire left it nearly black.
     */
    static int fireTint(Attunement att) {
        return att == Attunement.ASHEN ? 0x8E8E8E : att.rgb;
    }

    /** A block's half-size and where the pillars stand, in world units. */
    static final float BLOCK = 2.8f, PILLAR_X = 33f, GROUND = -15f;
    private static final float PILLAR_COS = (float) Math.cos(Math.toRadians(26)), PILLAR_SIN = (float) Math.sin(Math.toRadians(26));

    /**
     * How far out the pillars stand: well clear of the rods (the user, 0.2.58), but pulled in on a
     * narrow window so they never leave the screen - 33 on 16:9, about 25 on 4:3 (screen units).
     */
    static float pillarX(int w, int h) {
        return Math.min(PILLAR_X, 18.5f * w / Math.max(1, h));
    }

    /**
     * Vanilla's floor fire (models/block/template_fire_floor.json, read from the 26.2 jar): four planes,
     * two across each axis 0.8 px either side of the middle, each turned 22.5 degrees about the block's
     * middle with "rescale" (1 / cos 22.5 on the other two axes), 22.4 px tall - so they lean across one
     * another and the flame stands about 1.4 blocks high. {axis 0 = x / 2 = z, offset, angle}.
     */
    private static final float[][] FIRE_PLANES = {{2, 8.8f, -22.5f}, {2, 7.2f, 22.5f}, {0, 8.8f, -22.5f}, {0, 7.2f, 22.5f}};
    private static final float[][] FIRE_CORNERS = new float[4][];

    static {
        // Each plane's corners in block pixels: top-left, top-right, bottom-left.
        for (int i = 0; i < 4; i++) {
            float[] pl = FIRE_PLANES[i];
            boolean zPlane = pl[0] == 2;
            double a = Math.toRadians(pl[2]), cs = Math.cos(a), sn = Math.sin(a), k = 1.0 / Math.cos(a);
            float[][] pts = new float[3][];
            float[][] src = {{0f, 22.4f}, {16f, 22.4f}, {0f, 0f}}; // (along the plane, height)
            for (int j = 0; j < 3; j++) {
                double d = pl[1] - 8.0, y = src[j][1] - 8.0;
                double ry, rd;
                if (zPlane) { // rotation about x: (y, z)
                    ry = y * cs - d * sn;
                    rd = y * sn + d * cs;
                } else {      // rotation about z: (x, y)
                    rd = d * cs - y * sn;
                    ry = d * sn + y * cs;
                }
                float yy = (float) (ry * k + 8.0), dd = (float) (rd * k + 8.0), along = src[j][0];
                pts[j] = zPlane ? new float[]{along, yy, dd} : new float[]{dd, yy, along};
            }
            FIRE_CORNERS[i] = new float[]{pts[0][0], pts[0][1], pts[0][2], pts[1][0], pts[1][1], pts[1][2], pts[2][0], pts[2][1], pts[2][2]};
        }
    }

    private static final Integer[] FIRE_ORDER = {0, 1, 2, 3};

    /**
     * The fire on a pillar's top, as the game draws it: the four leaning planes of the floor-fire model,
     * double sided, sorted back to front. {@code grow} stretches it upwards (the clang flare, the Dye's
     * eruption) and {@code wide} outwards, about the block's middle.
     */
    static void fire(ChopScene.Canvas c, String tex, int[] rect, float cx, float cz, float baseY, float grow, float wide, int argb) {
        float p = BLOCK / 8f;
        float[] depth = new float[4];
        for (int i = 0; i < 4; i++) {
            float[] q = FIRE_CORNERS[i];
            depth[i] = ChopScene.depth(cx + ((q[0] + q[6]) / 2f - 8f) * p * wide, baseY + 6f * p * grow, cz + ((q[2] + q[8]) / 2f - 8f) * p * wide);
        }
        java.util.Arrays.sort(FIRE_ORDER, (a, b) -> Float.compare(depth[a], depth[b]));
        for (int i : FIRE_ORDER) {
            float[] q = FIRE_CORNERS[i];
            float ox = cx + (q[0] - 8f) * p * wide, oy = baseY + q[1] * p * grow, oz = cz + (q[2] - 8f) * p * wide;
            float ux = (q[3] - q[0]) * p * wide, uy = (q[4] - q[1]) * p * grow, uz = (q[5] - q[2]) * p * wide;
            float vx = (q[6] - q[0]) * p * wide, vy = (q[7] - q[1]) * p * grow, vz = (q[8] - q[2]) * p * wide;
            ChopScene.worldQuad(tex, rect, ox, oy, oz, ux, uy, uz, vx, vy, vz, argb, c);
        }
    }
    private static final int[] FULL = {0, 0, 16, 16};
    private static final int[][] FACES = {FULL, FULL, FULL, FULL, FULL, FULL};

    /**
     * Two of the fight's fire pillars, one each side - the user's screenshot (2026-10-02): yellow, orange,
     * red and brown terracotta with fire on top and a nametag, "6s 8 Hits". Here the seconds count down
     * to 0 at the reveal and the hits drop with the dagger clangs, as the real pillar loses them to
     * hits. The fire burns in the shield's colour (soul fire for Crystal) and flares on each clang. At
     * the reveal a win blows them apart; a bust puts the fire out and sinks them, greyed.
     */
    static void pillars(ChopScene.Canvas c, CrystalVeins.Fill embers, HellionTimeline tl, long t, int w, int h,
                        Attunement att, float clang, float alpha) {
        // The jackpots keep their pillars - they erupt later, on the ignition or the jackpot.
        float burn = tl.burnOut(t), shatter = tl.drop().ending == Ending.DYE || tl.drop().ending == Ending.HIGH_CLASS ? -1f : tl.shatter(t);
        float frozen = tl.frozen(t), ig = tl.ignite(t), er = tl.erupt(t);
        long pt = tl.phys(t);
        float rise = ease(Math.min(1f, (t - 150) / 550f));
        if (rise <= 0f) return;
        Random r = new Random(tl.seed() * 13 + 5);
        for (int side = -1; side <= 1; side += 2) {
            // Along the camera's own sideways (its resting yaw, 26 degrees), so both stand level on
            // screen - square across the world x, the far one rose up the screen as the gap widened.
            float px = side * pillarX(w, h) * pillarPull * PILLAR_COS, pz = side * pillarX(w, h) * pillarPull * PILLAR_SIN;
            // The pillar rises out of the ground as the scene opens.
            float base = GROUND - (1f - rise) * BLOCK * 8f;
            float top = base + BLOCK * 8f;
            // Blocks: back to front does not matter here - one column, drawn bottom up, faces culled.
            for (int pass = 0; pass < 2; pass++) {
                ChopScene.inkWidth = pass == 0 ? Math.max(1.5f, ChopScene.size / 3f) : 0f;
                for (int i = 0; i < 4; i++) {
                    float x = px, y = base + BLOCK + i * BLOCK * 2f, z = pz, ax = 0f, ay = 0f, az = 0f, a = 1f;
                    float shade = 1f;
                    if (shatter >= 0f) {
                        float k = shatter * 0.9f;
                        float vx = side * (6f + r.nextFloat() * 10f), vy = 10f + r.nextFloat() * 14f, vz = (r.nextFloat() - 0.5f) * 10f;
                        x += vx * k * 3f;
                        y += vy * k * 3f - 60f * k * k;
                        z += vz * k * 3f;
                        ax = (r.nextFloat() - 0.5f) * 9f * k;
                        az = (r.nextFloat() - 0.5f) * 9f * k;
                        a = 1f - ease((shatter - 0.4f) / 0.5f);
                    } else if (burn >= 0f) {
                        y -= BLOCK * 8f * ease(burn) * (0.6f + 0.1f * i);
                        shade = 1f - 0.55f * ease(burn / 0.4f);
                        a = 1f - ease((burn - 0.5f) / 0.5f);
                    }
                    if (a < 0.02f) continue;
                    float glow = 0.12f * clang + (shatter >= 0f ? 0.5f * (1f - shatter) : 0f) + 0.35f * er;
                    if (frozen > 0f) shade *= 1f - 0.3f * frozen;
                    float grow = pass == 0 ? 1.05f : 1f;
                    ChopScene.box(x, y, z, ax, 0f, az, BLOCK * grow, BLOCK * grow, BLOCK * grow,
                            all(TERRACOTTA[i]), FACES, shade, shade, shade, glow, a, c);
                }
            }
            ChopScene.inkWidth = 0f;

            // The fire on top: the game's own fire, on the game's own floor-fire model, animated. A bust snuffs
            // them in turn, left then right, each with a puff of smoke - they used to just shrink away together
            // (the review, H5).
            long snuffAt = HellionTimeline.REVEAL + (side < 0 ? 230 : 350);
            // On the top block wherever it is: a bust sinks the pillars, and the fire floated where they had been.
            float fireBase = burn >= 0f ? top - BLOCK * 8f * ease(burn) * 0.9f : top;
            float fireH = (burn >= 0f ? 1f - ease((t - snuffAt) / 110f) : 1f) * (shatter >= 0f ? 1f - ease(shatter / 0.25f) : 1f);
            if (burn >= 0f && t >= snuffAt && t < snuffAt + 900) snuffPuff(embers, px, fireBase, pz, t - snuffAt, side, h, alpha);
            if (fireH > 0.02f) {
                // Vanilla's own size; only a small lick upwards on each clang (the user: no taller).
                float grow = (1f + 0.1f * clang + 2.2f * er) * fireH, wide = 1f + 0.35f * er;
                float fh = BLOCK * 2f * 1.4f * grow;
                int frame = (int) ((pt / 50) % 32);
                int[] rect = {0, frame * 16, 16, 16};
                // The shield's colour, on the grey fire; the Flame Dye's eruption is the real fire.
                String tex = ig > 0f ? TEX_FIRE : TEX_FIRE_GREY;
                int tint = ig > 0f ? 0xFFFFFF : fireTint(att);
                tint = mix(tint, 0x6E84A8, frozen);
                int argb = ChopScene.alpha(alpha * fireH) | tint;
                // A soft glow of the fire's own colour behind it, so it lights the dark round it.
                ChopScene.project(px, top + fh * 0.4f, pz);
                int glowRgb = ig > 0f ? 0xFF8A2A : fireTint(att);
                ChopScene.glow(c, ChopScene.P[0], ChopScene.P[1], BLOCK * 7f * ChopScene.size * (0.8f + 0.2f * grow),
                        mix(glowRgb, 0x6E84A8, frozen), (0.22f + 0.15f * clang) * alpha * fireH * (1f - 0.7f * frozen));
                fire(c, tex, rect, px, pz, fireBase, grow, wide, argb);
                // A warm core in the bottom fifth whatever the tint: grey and white fire alone read as smoke
                // (the review, H2). The game's own fire, its bottom five rows at their own size, so its tongues lick
                // up into the tinted flame instead of ending in a flat band.
                if (ig <= 0f) {
                    int[] base5 = {0, frame * 16 + 11, 16, 5};
                    fire(c, TEX_FIRE, base5, px, pz, fireBase, grow * 5f / 16f, wide * 0.92f,
                            ChopScene.alpha(0.65f * alpha * fireH * (1f - frozen)) | 0xFFD8B0);
                }
                // Its light, on the blaze's sides that face it.
                ChopScene.light(px, top + 2f, pz, 30f, att.rgb, 0.35f + 0.4f * clang);
                // Embers off the top, pixel squares.
                float u = unitFor(h);
                ChopScene.project(px, top + fh * 0.7f, pz);
                float ex = ChopScene.P[0], ey = ChopScene.P[1];
                float sec = pt / 1000f;
                for (int e = 0; e < 7; e++) {
                    float life = (sec * (0.7f + r.nextFloat() * 0.5f) + r.nextFloat()) % 1f;
                    float x = ex + (r.nextFloat() - 0.5f) * 10f * u + (float) Math.sin(sec * 3 + e) * u;
                    float y = ey - life * h * 0.12f;
                    float a = alpha * fireH * (1f - life) * 0.9f;
                    embers.fill(Math.round(x / u) * u, Math.round(y / u) * u, u, u,
                            (Math.round(a * 255) << 24) | mix(ig > 0f ? 0xFFC04A : fireTint(att), 0xFFFFFF, 0.3f));
                }
            }

            // The nametag, as the fight shows it: seconds left, then hits left.
            if (t < HellionTimeline.REVEAL + 160 && rise > 0.9f) {
                // The last second in tenths, red, landing on 0.0 on the reveal frame - whole seconds rounded up
                // never reached 0 (the review, H3).
                long ms = Math.max(0, HellionTimeline.REVEAL - t);
                boolean last = ms < 1000;
                int steps = Math.max(0, HellionTimeline.step(t) + 1);
                int hits = Math.max(0, 8 - steps / 2);
                ChopScene.project(px, top + BLOCK * 3.4f, pz);
                float ts = Math.min(1.2f, h / 300f);
                String secs = last ? (ms / 1000) + "." + (ms / 100 % 10) + "s" : (long) Math.ceil(ms / 1000.0) + "s";
                String hit = hits + " Hits";
                float cw = 6f * ts;
                float wTot = (secs.length() + 1 + hit.length()) * cw;
                c.fill(ChopScene.P[0] - wTot / 2f - 2f, ChopScene.P[1] - 6f * ts, wTot + 4f, 11f * ts,
                        ChopScene.alpha(0.45f * alpha) | 0x000000);
                c.text(secs, ChopScene.P[0] - wTot / 2f + secs.length() * cw / 2f, ChopScene.P[1], ts,
                        ChopScene.alpha(alpha) | (last ? 0xFF5555 : 0xFFFF55));
                // White, red only for a moment as a hit comes off - it used to flip with every clang (the review, H4).
                c.text(hit, ChopScene.P[0] + wTot / 2f - hit.length() * cw / 2f, ChopScene.P[1], ts,
                        ChopScene.alpha(alpha) | (clang > 0.3f && steps % 2 == 0 && steps > 0 ? 0xFF5555 : 0xFFFFFF));
            }
        }
    }

    /** A snuffed pillar fire's puff: grey squares off its top, growing and drifting up and out. */
    static void snuffPuff(CrystalVeins.Fill layer, float px, float top, float pz, long since, int side, int h, float alpha) {
        float k = since / 900f;
        float u = unitFor(h);
        Random r = new Random(side * 31L + 9);
        for (int i = 0; i < 14; i++) {
            float ph = r.nextFloat() * 0.3f, rx = r.nextFloat(), ry = r.nextFloat();
            float q = Math.max(0f, Math.min(1f, (k - ph) / 0.7f));
            if (q <= 0f || q >= 1f) continue;
            float ox = (rx - 0.5f) * BLOCK * (1.2f + 2.5f * q) + side * BLOCK * 1.2f * q;
            float oy = BLOCK * (0.4f + ry * 0.6f + 5f * q);
            ChopScene.project(px + ox, top + oy, pz);
            int s = 1 + (int) (q * 2.5f);
            float a = alpha * (1f - q) * 0.75f;
            layer.fill(Math.round(ChopScene.P[0] / u) * u, Math.round(ChopScene.P[1] / u) * u, u * s, u * s,
                    (Math.round(a * 255) << 24) | (i % 3 == 0 ? 0x4A4648 : 0x8A8688));
        }
    }

    private static String[] all(String tex) {
        return new String[]{tex, tex, tex, tex, tex, tex};
    }

    // ================================================================== bits

    private static void square(ChopScene.Canvas c, float x, float y, float s, float grid, int argb) {
        if ((argb >>> 24) < 4) return;
        c.fill(Math.round(x / grid) * grid - s / 2f, Math.round(y / grid) * grid - s / 2f, s, s, argb);
    }

    private static int dim(int rgb) {
        return mix(rgb, 0x202020, 0.45f);
    }

    private static int darker(int rgb) {
        return mix(rgb, 0x1A0A04, 0.55f);
    }

    static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
