package com.butcher.render;

import com.butcher.anim.HellionDice;
import com.butcher.anim.SeraphDragon;
import com.butcher.anim.SeraphTimeline;
import com.butcher.anim.SeraphTimeline.Ending;
import com.butcher.anim.SeraphTimeline.Glass;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.butcher.anim.SeraphTimeline.ease;

/**
 * Draws a frame of "The Seraph's Glyph" onto a {@link ChopScene.Canvas} - the game and the offline preview
 * share it, on the Chopping Block's box renderer (its camera, boxes and ink outline).
 *
 * <b>The Seraph is the vanilla enderman</b>, read with javap from the 26.2 jar (EndermanModel): an 8-cube
 * head with the "hat" as an inner jaw (8-cube shrunk half a pixel, at 0,16), an 8x12x4 body, and 2x30x2
 * arms and legs (the left ones mirrored), texture 64 x 32; the eyes are the enderman_eyes layer, drawn
 * full bright. Creepy lifts the head 5 px and leaves the jaw. <b>It carries the beacon exactly as an
 * enderman carries a block</b>: both arms xRot -0.5 (zRot +/-0.05), and the block placed by
 * CarriedBlockLayer - translate (0, 0.6875, -0.75), rotate X 20, Y 45, translate (0.25, 0.1875, 0.25),
 * scale (-0.5, -0.5, 0.5), rotate Y 90 - half a block, in front of it at hand height. The beacon is the
 * vanilla block model (models/block/beacon.json: glass shell, obsidian base, beacon core).
 *
 * Model space is y down with the front on -Z; the world here is y up with the front on +Z, the camera at
 * +Z: world = (x, 1 - y + FEET + 23, -z), a half turn about X, so every model rotation keeps its X angle and
 * negates its Y and Z (conjugation by diag(1, -1, -1)).
 *
 * The End Crystals are the vanilla model too (EndCrystalModel): outer glass, inner glass at 0.875, the
 * cube at 0.875 squared, each turned 60 degrees about the (1, 0, 1) diagonal and spinning, on the base,
 * texture 128 x 64 (a double-size sheet for a 64 x 32 layout).
 */
public final class SeraphScene {
    private SeraphScene() {}

    // ---- textures (all adopted from the game at runtime, never bundled)
    public static final String TEX_ENDERMAN = "seraph_enderman", TEX_EYES = "seraph_enderman_eyes",
            TEX_BEACON = "seraph_beacon", TEX_OBSIDIAN = "seraph_obsidian", TEX_GLASS = "seraph_glass",
            TEX_BEAM = "seraph_beacon_beam", TEX_CRYSTAL = "seraph_end_crystal", TEX_CRYSTAL_BEAM = "seraph_crystal_beam",
            TEX_END_SKY = "seraph_end_sky", TEX_END_STONE = "seraph_end_stone", TEX_WHITE = "seraph_white";
    /** The vanilla paths, in the same order as {@link #ADOPT}. */
    public static final String[] ADOPT = {TEX_ENDERMAN, TEX_EYES, TEX_BEACON, TEX_OBSIDIAN, TEX_GLASS, TEX_BEAM, TEX_CRYSTAL,
            TEX_CRYSTAL_BEAM, TEX_END_SKY, TEX_END_STONE};
    public static final String[] ADOPT_PATH = {"textures/entity/enderman/enderman.png", "textures/entity/enderman/enderman_eyes.png",
            "textures/block/beacon.png", "textures/block/obsidian.png", "textures/block/glass.png", "textures/entity/beacon/beacon_beam.png",
            "textures/entity/end_crystal/end_crystal.png", "textures/entity/end_crystal/end_crystal_beam.png",
            "textures/environment/end_sky.png", "textures/block/end_stone.png"};
    public static final int[][] ADOPT_SIZE = {{64, 32}, {64, 32}, {16, 16}, {16, 16}, {16, 16}, {16, 16}, {128, 64}, {16, 256}, {128, 128}, {16, 16}};

    // ---- the Ender Dragon (the Byzantium Dye's ending), posed by the game's own model

    /** Poses the vanilla dragon: game and preview both hand in a {@link DragonRig}. Null draws no dragon. */
    public interface DragonPoser {
        /** Every polygon: four corners {x, y, z, u, v} in blocks about the dragon's origin, then a flag. */
        List<float[]> pose(DragonPose p);
    }

    /** What the dragon's model is fed: the flap clock, its flight history, sitting or not, and our extras. */
    public static final class DragonPose {
        public float flapTime, jawOpen, headPitch, extraPitch;
        /** Newest first, a sample a tick: y in blocks, yaw in degrees (vanilla's DragonFlightHistory). */
        public final double[] histY = new double[64];
        public final float[] histYaw = new float[64];
        public boolean sitting, landing;
        public double distanceToEgg = 100.0;
    }

    public static DragonPoser dragon;
    /** The ground layer (the Dragon's paint pool), and how to put it down before the rest; null in the preview. */
    public static CrystalVeins.Fill under;
    public static Runnable flushUnder;
    public static final String TEX_DRAGON = "seraph_dragon", TEX_DRAGON_EYES = "seraph_dragon_eyes";

    /** World y of the ground the Seraph stands on. */
    static final float FEET = -23f;
    /**
     * Where the glyph lands: in front of its feet, in the middle of the shot - so every drop is shown in
     * the middle (the user, 0.2.64: a spot to its side made the showcase drift off to one side).
     */
    static final float BX = 0f, BZ = 18f;
    /** The camera's resting yaw: the pillars stand along its sideways so they sit level on screen. */
    static final float YAW0 = 20f;

    /** The cell grid the pixel effects sit on, in GUI pixels. */
    public static int unitFor(int h) {
        return Math.max(2, Math.round(h / 120f));
    }

    /**
     * A faint end-sky glow along the horizon, in steps: the Seraph, its beam and the dragon are black on a black sky,
     * and nothing showed their shapes (the review, S1). Six bands from the ground up, a third of the screen high at
     * most - light behind the figures, not a wash over them.
     */
    static void horizon(ChopScene.Canvas c, int w, int h) {
        ChopScene.project(0f, FEET, 0f);
        float ground = ChopScene.P[1];
        float band = h * 0.055f;
        for (int i = 0; i < 6; i++) {
            float y = ground - band * (i + 1);
            if (y + band < 0f || y > h) continue;
            float a = 0.20f * (1f - i / 6f) * ChopScene.sceneAlpha;
            c.fill(0, Math.round(y), w, Math.round(band) + 1, ChopScene.alpha(a) | 0x3A2160);
        }
    }

    public static void draw(ChopScene.Canvas c, CrystalVeins.Fill layer, SeraphTimeline tl, long t, int w, int h) {
        screenW = w;
        screenH = h;
        screen(w, h);
        float master = tl.master(t);
        c.fill(0, 0, w, h, ChopScene.alpha(tl.dim(t)) | 0x07040A);
        if (master <= 0.01f) return;
        ChopScene.sceneAlpha = master;
        ChopScene.lightCount = 0;
        ChopScene.gild = null;
        ChopScene.glintHook = null;
        ChopScene.insideOut = false;
        ChopScene.yShift = 0f;
        camera(tl, t, w, h);
        horizon(c, w, h);

        Glass glass = tl.glass(t);
        long pt = tl.phys(t);
        if (t >= SeraphTimeline.REVEAL && tl.beam(t) > 0.05f) ChopScene.light(BX, FEET + 30f, BZ, 70f, beamColour(tl, t), 0.6f);

        Ending e = tl.ending();
        // The Dragon's stopped world: grey, texel by texel, with the dye's paint creeping over it.
        if (e == Ending.DRAGON && tl.grey(t) > 0f) ChopScene.gild = worldGild(tl, t);
        if (e == Ending.DRAGON) {
            // The pool lies on the ground under everything: its own layer, put down before the rest.
            pool(under != null ? under : c::fill, tl, t, h);
            if (flushUnder != null) flushUnder.run();
        }
        // The dragon is drawn whole, before the pillars while it is behind them, after them once in front.
        boolean dragonBehind = e == Ending.DRAGON && dragonBehind(tl, t);
        if (dragonBehind) {
            ChopScene.Gild g0 = ChopScene.gild;
            ChopScene.gild = null;
            dragon(c, layer, tl, t, h);
            ChopScene.gild = g0;
        }
        pillars(c, tl, t, pt);
        if (e == Ending.JACKPOT) islands(c, tl, t);
        crystalBeams(c, tl, t, pt);
        // The glyph lands in front of it, so it and its beam are drawn over the Seraph.
        seraph(c, tl, t, pt, glass);
        ChopScene.gild = null;
        if (e == Ending.DRAGON && !dragonBehind) dragon(c, layer, tl, t, h);
        groundBeacon(c, tl, t, pt, glass);
        if (e == Ending.EYE) {
            // The red rays behind the core, before it: drawn with the words they covered it (the review, S6).
            if (t >= SeraphTimeline.E_RISE) {
                float show = ease((t - tl.arrive() + 300) / 500f);
                float[] cp = eyeCorePos(tl, t);
                ChopScene.project(cp[0], cp[1], cp[2]);
                if (show > 0f) rays(c, ChopScene.P[0], ChopScene.P[1], h, t, show, 0xD42F2F);
            }
            eye(c, layer, tl, t, h);
        }
        if (e == Ending.JACKPOT) afterimages(c, tl, t);
        if (e == Ending.SKIN) skinHead(c, tl, t, h);
        if (e == Ending.DICE) die(c, tl, t, h);

        particles(layer, tl, t, pt, w, h);
        if (t >= SeraphTimeline.HANG && t < SeraphTimeline.REVEAL) lasers(layer, tl, t, h);
        effects(c, layer, tl, t, w, h);
        nametag(c, tl, t, h);
        if (tl.drop().win()) drop(c, layer, tl, t, w, h);
        ChopScene.sceneAlpha = 1f;
    }

    // ================================================================== camera

    static void camera(SeraphTimeline tl, long t, int w, int h) {
        double yaw = Math.toRadians(YAW0 + 4f * (float) Math.sin(t / 1800.0));
        double pitch = Math.toRadians(-10f);
        ChopScene.cy = (float) Math.cos(yaw);
        ChopScene.sy = (float) Math.sin(yaw);
        ChopScene.cp = (float) Math.cos(pitch);
        ChopScene.sp = (float) Math.sin(pitch);
        float push = 1f + 0.08f * ease(t / (float) SeraphTimeline.REVEAL) + 0.02f * SeraphTimeline.hit(t);
        ChopScene.size = h * 0.64f / 46f * (0.85f + 0.15f * SeraphTimeline.appear(t)) * push;
        ChopScene.fx = 0f;
        ChopScene.fy = 2f;
        ChopScene.fz = 0f;
        ChopScene.sx0 = w / 2f;
        ChopScene.sy0 = h * 0.54f;

        // A win's camera drifts over towards the glyph, where the drop will stand.
        float pan = tl.pan(t);
        Ending e = tl.ending();
        if (pan > 0f && e != Ending.EYE && e != Ending.DRAGON) {
            float tx = BX, tz = BZ;
            if (e == Ending.SKIN) {
                tx = SKIN_HX;
                tz = SKIN_HZ;
            }
            if (e == Ending.DICE) {
                float[] rest = HellionDice.pose(false, Long.MAX_VALUE / 4);
                tx = BX + rest[0];
                tz = BZ + rest[2];
            }
            // No sideways drift: centred on the drop's own depth, so it stays in the middle of the screen.
            ChopScene.fz = tz * pan;
            ChopScene.size *= 1f + 0.12f * pan;
            if (e == Ending.SKIN || e == Ending.DICE) {
                // These end on the ground: look down at them, closer, with room for the words.
                ChopScene.fy = 2f + (FEET + 2f - 2f) * pan;
                ChopScene.size *= 1f + 0.35f * pan;
                ChopScene.sy0 = h * (0.54f - 0.12f * pan);
            }
        }
        if (e == Ending.EYE) {
            // In on the core as it forms, centred on it; back out a little as it rises.
            float k = ease((t - SeraphTimeline.E_BURST) / 600f) * (1f - 0.5f * ease((t - SeraphTimeline.E_RISE) / 700f));
            ChopScene.fz = BZ * k;
            ChopScene.fy = 2f + (FEET + 14f - 2f) * k;
            // 1.3x, not 1.55x: closer, the Seraph beside the core filled a third of the screen as a black slab (the
            // review, S5).
            ChopScene.size *= 1f + 0.3f * k;
        }
        if (e == Ending.DRAGON) {
            // Out wide for the dragon, and up a little to keep it in.
            float k = ease((t - SeraphTimeline.D_IN + 300) / 1500f);
            float rear = tl.rear(t);
            ChopScene.size *= (1f - 0.42f * k) * (1f - REAR_ZOOM * rear);
            ChopScene.fy = 2f + 22f * k + REAR_RISE * rear;
            ChopScene.fz = BZ * 0.3f * k;
        }
        // The showcase: as the drop comes in, the camera eases its focus onto where the drop comes to rest
        // and pins that point so the whole block - banner, drop, name, odds, price - sits in the middle of the
        // screen. The sway turns about the focus, so it no longer carries the drop off centre (the user, twice).
        float[] show = showTarget(tl);
        if (show != null) {
            float k = ease((t - tl.arrive() + 900) / 900f);
            if (k > 0f) {
                float ts = Math.min(1.35f, h / 300f);
                float above = tl.banner() && showBanner ? 24f * ts : 0f, below = 45f * ts;
                ChopScene.fx += (show[0] - ChopScene.fx) * k;
                ChopScene.fy += (show[1] - ChopScene.fy) * k;
                ChopScene.fz += (show[2] - ChopScene.fz) * k;
                ChopScene.sx0 += (w / 2f - ChopScene.sx0) * k;
                ChopScene.sy0 += (h / 2f - (below - above) / 2f - ChopScene.sy0) * k;
            }
        }
        // The jackpot's shock shakes the screen.
        if (e == Ending.JACKPOT) {
            long since = t - SeraphTimeline.SHOCK;
            if (since >= 0 && since < 450) {
                float amp = ChopScene.calm ? 0f : h * 0.014f * (1f - since / 450f);
                ChopScene.sx0 += amp * (float) Math.sin(since * 0.13);
                ChopScene.sy0 += amp * (float) Math.cos(since * 0.17);
            }
        }
    }

    // ================================================================== matrices (3x3, row-major)

    static float[] rx(double a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[]{1, 0, 0, 0, c, -s, 0, s, c};
    }

    static float[] ry(double a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[]{c, 0, s, 0, 1, 0, -s, 0, c};
    }

    static float[] rz(double a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[]{c, -s, 0, s, c, 0, 0, 0, 1};
    }

    static float[] axisAngle(float x, float y, float z, double a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a), k = 1 - c;
        return new float[]{
                c + x * x * k, x * y * k - z * s, x * z * k + y * s,
                y * x * k + z * s, c + y * y * k, y * z * k - x * s,
                z * x * k - y * s, z * y * k + x * s, c + z * z * k};
    }

    static float[] mul(float[] a, float[] b) {
        float[] o = new float[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                o[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
        }
        return o;
    }

    static float[] apply(float[] m, float x, float y, float z) {
        return new float[]{m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z};
    }

    static final float[] I3 = {1, 0, 0, 0, 1, 0, 0, 0, 1};

    /** Model rotation to world: conjugate by diag(1, -1, -1). */
    static float[] toWorld(float[] m) {
        float[] f = {1, -1, -1};
        float[] o = new float[9];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) o[i * 3 + j] = f[i] * f[j] * m[i * 3 + j];
        return o;
    }

    static float[] col(float[] m, int j) {
        return new float[]{m[j], m[3 + j], m[6 + j]};
    }

    // ================================================================== the vanilla enderman

    /**
     * Box UVs of a vanilla cube from its texOffs and size, in ChopScene's face order {+X, -X, +Y, -Y, +Z, -Z}
     * with the front on +Z: the entity's left (model +X) is texOffs + d + w, its right texOffs, its top
     * (model -Y) texOffs + d at row 0. A mirrored cube swaps the two sides (the texels within a face are
     * not flipped - the enderman's limbs are near uniform).
     */
    static int[][] uv(int u, int v, int w, int h, int d, boolean mirror) {
        int[] left = {u + d + w, v + d, d, h}, right = {u, v + d, d, h};
        return new int[][]{mirror ? right : left, mirror ? left : right, {u + d, v, w, d}, {u + d + w, v, w, d},
                {u + d, v + d, w, h}, {u + 2 * d + w, v + d, w, h}};
    }

    static final int[][] HEAD_UV = uv(0, 0, 8, 8, 8, false), JAW_UV = uv(0, 16, 8, 8, 8, false),
            BODY_UV = uv(32, 16, 8, 12, 4, false), LIMB_UV = uv(56, 0, 2, 30, 2, false), LIMB_UV_M = uv(56, 0, 2, 30, 2, true);
    /** The player head layout, for skins (the Void Conqueror skin, the dice). */
    static final int[][] SKULL = uv(0, 0, 8, 8, 8, false), SKULL_HAT = uv(32, 0, 8, 8, 8, false);

    /** One box of a rig, ready to sort and draw. */
    static final class Part {
        float x, y, z, hx, hy, hz, r = 1, g = 1, b = 1, glow, alpha = 1, bias;
        float[] X, Y, Z;
        String tex;
        int[][] rects;
        boolean ink = true;
        /** A beacon to draw in this slot instead of a box (it sorts as one piece). */
        Beacon beacon;
    }

    /** How the Seraph stands this frame. */
    static final class Pose {
        float x, yaw, roll, drop, creepy, headX, bodyX, alpha = 1f, r = 1f, g = 1f, b = 1f, shakeX, shakeZ;
        float[] armX = {-0.5f, -0.5f}, armZ = {0.05f, -0.05f}, legX = {0f, 0f};
        boolean headless, carry = true;
        /** Per-part fade from the feet up (the judgement's dissolve): 0 none, 1 all gone. */
        float dissolve;
    }

    /** The world transform of the whole rig: rotate (yaw, then the death roll about its own facing) about its feet, then place. */
    static float[] rigTransform(Pose p) {
        return mul(ry(p.yaw), rz(p.roll));
    }

    static float[] rigPoint(Pose p, float[] g, float lx, float ly, float lz) {
        // Local is relative to the feet; world adds the place.
        float[] q = apply(g, lx, ly - FEET, lz);
        return new float[]{p.x + p.shakeX + q[0], FEET - p.drop + q[1], p.shakeZ + q[2]};
    }

    /** A model part's box as a world Part. */
    static Part modelBox(Pose p, float[] g, float px, float py, float pz, float rx, float ry, float rz,
                         float bx, float by, float bz, float sw, float sh, float sd, float inflate, String tex, int[][] rects) {
        float[] r = mul(rz(rz), mul(ry(ry), rx(rx)));
        float cx = bx + sw / 2f, cy = by + sh / 2f, cz = bz + sd / 2f;
        float[] cm = apply(r, cx, cy, cz);
        // Model to world: y flipped about the line that puts model y 24 on the ground, z negated.
        float wx = px + cm[0], wy = FEET + 24f - (py + cm[1]), wz = -(pz + cm[2]);
        float[] rw = mul(g, toWorld(r));
        Part part = new Part();
        float[] pos = rigPoint(p, g, wx, wy, wz);
        part.x = pos[0];
        part.y = pos[1];
        part.z = pos[2];
        part.X = col(rw, 0);
        part.Y = col(rw, 1);
        part.Z = col(rw, 2);
        part.hx = sw / 2f + inflate;
        part.hy = sh / 2f + inflate;
        part.hz = sd / 2f + inflate;
        part.tex = tex;
        part.rects = rects;
        part.alpha = p.alpha;
        part.r = p.r;
        part.g = p.g;
        part.b = p.b;
        return part;
    }

    /** The vanilla carried-block transform (CarriedBlockLayer), in model px: centre of the block and its rotation. */
    static final float[] CARRY_ROT, CARRY_CENTRE;

    static {
        float[] rot = mul(rx(Math.toRadians(20)), ry(Math.toRadians(45)));
        float[] t2 = apply(rot, 0.25f * 16, 0.1875f * 16, 0.25f * 16);
        // scale(-0.5, -0.5, 0.5) is a half size and a half turn about Z.
        float[] lin = mul(rot, mul(new float[]{-1, 0, 0, 0, -1, 0, 0, 0, 1}, ry(Math.toRadians(90))));
        CARRY_ROT = lin;
        float[] c8 = apply(lin, 8 * 0.5f, 8 * 0.5f, 8 * 0.5f);
        CARRY_CENTRE = new float[]{0f + t2[0] + c8[0], 0.6875f * 16 + t2[1] + c8[1], -0.75f * 16 + t2[2] + c8[2]};
    }

    /** Builds the Seraph's boxes for a pose. */
    static List<Part> rig(Pose p, Beacon carried) {
        List<Part> out = new ArrayList<>();
        float[] g = rigTransform(p);
        float headY = -13f - 5f * p.creepy;
        // Head, jaw (the hat, half a pixel in), the eyes layer, full bright.
        if (!p.headless) {
            Part jaw = modelBox(p, g, 0, -13f, 0, p.headX, 0, 0, -4, -8, -4, 8, 8, 8, -0.5f, TEX_ENDERMAN, JAW_UV);
            jaw.bias = -0.3f;
            out.add(jaw);
            Part head = modelBox(p, g, 0, headY, 0, p.headX, 0, 0, -4, -8, -4, 8, 8, 8, 0f, TEX_ENDERMAN, HEAD_UV);
            out.add(head);
            Part eyes = modelBox(p, g, 0, headY, 0, p.headX, 0, 0, -4, -8, -4, 8, 8, 8, 0.02f, TEX_EYES, HEAD_UV);
            eyes.glow = 1f;
            eyes.ink = false;
            eyes.bias = 0.2f;
            out.add(eyes);
        }
        out.add(modelBox(p, g, 0, -14f, 0, p.bodyX, 0, 0, -4, 0, -2, 8, 12, 4, 0f, TEX_ENDERMAN, BODY_UV));
        out.add(modelBox(p, g, -5f, -12f, 0, p.armX[0], 0, p.armZ[0], -1, -2, -1, 2, 30, 2, 0f, TEX_ENDERMAN, LIMB_UV));
        out.add(modelBox(p, g, 5f, -12f, 0, p.armX[1], 0, p.armZ[1], -1, -2, -1, 2, 30, 2, 0f, TEX_ENDERMAN, LIMB_UV_M));
        out.add(modelBox(p, g, -2f, -5f, 0, p.legX[0], 0, 0, -1, 0, -1, 2, 30, 2, 0f, TEX_ENDERMAN, LIMB_UV));
        out.add(modelBox(p, g, 2f, -5f, 0, p.legX[1], 0, 0, -1, 0, -1, 2, 30, 2, 0f, TEX_ENDERMAN, LIMB_UV_M));
        if (p.dissolve > 0f) {
            // From the feet up: each part goes as the dissolve passes its height.
            for (Part part : out) {
                float hgt = (part.y - (FEET - p.drop)) / 46f;
                part.alpha *= 1f - ease((p.dissolve * 1.5f - hgt) / 0.35f);
            }
        }
        if (carried != null && p.carry) {
            Part slot = new Part();
            float[] cm = CARRY_CENTRE;
            float[] pos = rigPoint(p, g, cm[0], FEET + 24f - cm[1], -cm[2]);
            float[] rw = mul(g, toWorld(CARRY_ROT));
            carried.x = pos[0];
            carried.y = pos[1];
            carried.z = pos[2];
            carried.X = col(rw, 0);
            carried.Y = col(rw, 1);
            carried.Z = col(rw, 2);
            carried.scale = 0.5f;
            slot.x = pos[0];
            slot.y = pos[1];
            slot.z = pos[2];
            slot.beacon = carried;
            out.add(slot);
        }
        return out;
    }

    /** Sorts a rig back to front and draws it: the ink silhouette first, then the faces. */
    static void drawRig(ChopScene.Canvas c, List<Part> parts, boolean ink) {
        parts.sort((a, b) -> Float.compare(ChopScene.depth(a.x, a.y, a.z) + a.bias, ChopScene.depth(b.x, b.y, b.z) + b.bias));
        if (ink) {
            ChopScene.inkWidth = Math.max(1.5f, ChopScene.size / 2.5f);
            for (Part p : parts) {
                if (p.beacon != null) {
                    beaconInk(c, p.beacon);
                    continue;
                }
                if (!p.ink || p.alpha < 0.6f) continue;
                ChopScene.boxAxes(p.x, p.y, p.z, p.X, p.Y, p.Z, p.hx, p.hy, p.hz, ChopScene.all(p.tex), p.rects, 1, 1, 1, 0, p.alpha, c);
            }
            ChopScene.inkWidth = 0f;
        }
        for (Part p : parts) {
            if (p.beacon != null) {
                drawBeacon(c, p.beacon);
                continue;
            }
            if (p.alpha < 0.02f) continue;
            ChopScene.boxAxes(p.x, p.y, p.z, p.X, p.Y, p.Z, p.hx, p.hy, p.hz, ChopScene.all(p.tex), p.rects, p.r, p.g, p.b, p.glow, p.alpha, c);
        }
    }

    // ================================================================== the glyph (a vanilla beacon)

    static final class Beacon {
        float x, y, z, scale = 1f, alpha = 1f, obsidian;
        float[] X = {1, 0, 0}, Y = {0, 1, 0}, Z = {0, 0, 1};
        int glass = -1;
    }

    private static final int[][] BASE_UV = {{2, 13, 12, 3}, {2, 13, 12, 3}, {2, 2, 12, 12}, {2, 2, 12, 12}, {2, 13, 12, 3}, {2, 13, 12, 3}};
    private static final int[][] CORE_UV = {{3, 2, 10, 11}, {3, 2, 10, 11}, {3, 3, 10, 10}, {3, 3, 10, 10}, {3, 2, 10, 11}, {3, 2, 10, 11}};
    private static final int[] FULL = {0, 0, 16, 16};
    private static final int[][] FULL6 = {FULL, FULL, FULL, FULL, FULL, FULL};

    /** A point of the beacon's own model (block px, 0..16) in the world. */
    static float[] beaconAt(Beacon b, float bx, float by, float bz) {
        float s = b.scale, ox = (bx - 8f) * s, oy = (by - 8f) * s, oz = (bz - 8f) * s;
        return new float[]{b.x + b.X[0] * ox + b.Y[0] * oy + b.Z[0] * oz, b.y + b.X[1] * ox + b.Y[1] * oy + b.Z[1] * oz,
                b.z + b.X[2] * ox + b.Y[2] * oy + b.Z[2] * oz};
    }

    static void beaconInk(ChopScene.Canvas c, Beacon b) {
        if (b.alpha < 0.6f) return;
        float hs = 8f * b.scale;
        ChopScene.boxAxes(b.x, b.y, b.z, b.X, b.Y, b.Z, hs, hs, hs, ChopScene.all(TEX_GLASS), FULL6, 1, 1, 1, 0, b.alpha, c);
    }

    /** The beacon: obsidian base, the core, then the glass shell - stained the colour of the moment. */
    static void drawBeacon(ChopScene.Canvas c, Beacon b) {
        if (b.alpha < 0.02f) return;
        float s = b.scale;
        float[] base = beaconAt(b, 8f, 1.55f, 8f), core = beaconAt(b, 8f, 8.5f, 8f);
        String coreTex = b.obsidian > 0.5f ? TEX_OBSIDIAN : TEX_BEACON;
        ChopScene.boxAxes(base[0], base[1], base[2], b.X, b.Y, b.Z, 6f * s, 1.45f * s, 6f * s, ChopScene.all(TEX_OBSIDIAN), BASE_UV, 1, 1, 1, 0, b.alpha, c);
        ChopScene.boxAxes(core[0], core[1], core[2], b.X, b.Y, b.Z, 5f * s, 5.5f * s, 5f * s, ChopScene.all(coreTex), CORE_UV, 1, 1, 1,
                b.obsidian > 0.5f ? 0f : 0.35f, b.alpha, c);
        float hs = 8f * s;
        if (b.obsidian > 0.02f) {
            // A bust's glyph turns to plain obsidian.
            ChopScene.boxAxes(b.x, b.y, b.z, b.X, b.Y, b.Z, hs, hs, hs, ChopScene.all(TEX_OBSIDIAN), FULL6, 1, 1, 1, 0, b.alpha * b.obsidian, c);
            if (b.obsidian >= 0.98f) return;
        }
        float a = b.alpha * (1f - b.obsidian);
        if (b.glass >= 0) {
            // Stained glass: the pane tinted, and a wash of its colour across each face.
            float rr = (b.glass >> 16 & 255) / 255f, gg = (b.glass >> 8 & 255) / 255f, bb = (b.glass & 255) / 255f;
            ChopScene.boxAxes(b.x, b.y, b.z, b.X, b.Y, b.Z, hs, hs, hs, ChopScene.all(TEX_WHITE), FULL6, rr, gg, bb, 0.1f, a * 0.7f, c);
            ChopScene.boxAxes(b.x, b.y, b.z, b.X, b.Y, b.Z, hs, hs, hs, ChopScene.all(TEX_GLASS), FULL6, rr, gg, bb, 0.3f, a, c);
        } else {
            ChopScene.boxAxes(b.x, b.y, b.z, b.X, b.Y, b.Z, hs, hs, hs, ChopScene.all(TEX_GLASS), FULL6, 1, 1, 1, 0, a, c);
        }
    }

    /**
     * A beam: a ribbon square to the camera, the texture tiled along it and scrolling (a beacon's runs
     * 16 texels every 0.4 of a block, as vanilla's does). Drawn in pieces, each a tile or the part of one.
     */
    static void beam(ChopScene.Canvas c, String tex, int texW, int texH, float x0, float y0, float z0, float x1, float y1, float z1,
                     float halfWidth, float tile, float scroll, int argb) {
        float len = (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
        if (len < 0.5f || (argb >>> 24) < 4) return;
        ChopScene.project(x0, y0, z0);
        float ax = ChopScene.P[0], ay = ChopScene.P[1];
        ChopScene.project(x1, y1, z1);
        float bx = ChopScene.P[0], by = ChopScene.P[1];
        float dx = bx - ax, dy = by - ay, dl = (float) Math.sqrt(dx * dx + dy * dy);
        if (dl < 0.5f) return;
        float nx = -dy / dl * halfWidth * ChopScene.size, ny = dx / dl * halfWidth * ChopScene.size;
        float off = ((scroll % tile) + tile) % tile;
        for (float s = -off; s < len; s += tile) {
            float a = Math.max(0f, s), b = Math.min(len, s + tile);
            if (b <= a) continue;
            int v0 = Math.round((a - s) / tile * texH), v1 = Math.round((b - s) / tile * texH);
            if (v1 <= v0) continue;
            float fa = a / len, fb = b / len;
            float sx = ax + dx * fa, sy = ay + dy * fa, ex = ax + dx * fb, ey = ay + dy * fb;
            // u across the width, v along the beam (from its start).
            c.quad(tex, sx - nx, sy - ny, 2 * nx, 2 * ny, ex - sx, ey - sy, 0, v0, texW, v1 - v0, argb);
        }
    }

    // ================================================================== the Seraph

    static final float SKIN_HX = 0f, SKIN_HZ = 26f;

    static Beacon carriedBeacon(SeraphTimeline tl, long t) {
        Beacon b = new Beacon();
        b.glass = tl.glass(t).rgb;
        return b;
    }

    static void seraph(ChopScene.Canvas c, SeraphTimeline tl, long t, long pt, Glass glass) {
        if (!tl.seraphShown(t)) return;
        Pose p = pose(tl, t, pt);
        float tk = SeraphTimeline.throwK(t);
        Beacon carried = tk < 0.5f ? carriedBeacon(tl, t) : null;
        List<Part> parts = rig(p, carried);
        if (carried != null && tk > 0f) {
            // The hold: it stays in its hands, in the vanilla carry, shaking harder as it charges.
            float k = liftK(t);
            carried.x += 0.7f * k * (float) Math.sin(t * 0.09);
            carried.y += 0.7f * k * (float) Math.cos(t * 0.07);
            for (Part part : parts) if (part.beacon != null) {
                part.x = carried.x;
                part.y = carried.y;
                part.z = carried.z;
            }
        }
        float pull = tl.eyePull(t);
        if (pull > 0f) {
            pullInto(parts, new float[]{p.x, FEET + 23f, 0f}, pull, 0f);
            // Being drawn into it: dark, and no outline as it goes.
            for (Part part : parts) {
                part.ink = false;
                part.alpha *= 1f - ease((pull - 0.75f) / 0.25f);
            }
        }
        drawRig(c, parts, p.alpha > 0.6f && pull < 0.05f);
        if (carried != null && t >= SeraphTimeline.APPEAR) {
            // The glyph's stub of beam while it is held: the colour of the moment, fading upwards.
            float[] top = beaconAt(carried, 8f, 16f, 8f);
            // The tell: a solid stub of beam in the colour of the moment, over a wider glow of it.
            beam(c, TEX_WHITE, 4, 4, top[0], top[1], top[2], top[0], top[1] + 20f, top[2], 2.6f, 20f, 0f,
                    ChopScene.alpha(0.35f * ChopScene.sceneAlpha) | (glass == Glass.BLACK ? BLACK_RIM : glass.rgb));
            // Black on the night sky could not be seen at all (the review, S4): a bright purple edge round it.
            if (glass == Glass.BLACK) beam(c, TEX_WHITE, 4, 4, top[0], top[1], top[2], top[0], top[1] + 20f, top[2], 1.5f + 0.45f, 20f, 0f,
                    ChopScene.alpha(0.9f * ChopScene.sceneAlpha) | BLACK_RIM);
            beam(c, TEX_BEAM, 16, 16, top[0], top[1], top[2], top[0], top[1] + 20f, top[2], 1.5f, 6.4f, -pt / 30f,
                    ChopScene.alpha(ChopScene.sceneAlpha) | mix(glass.rgb, 0xFFFFFF, glass == Glass.BLACK ? 0f : 0.2f));
            ChopScene.project(carried.x, carried.y, carried.z);
            ChopScene.glow(c, ChopScene.P[0], ChopScene.P[1], ChopScene.size * (10f + 8f * SeraphTimeline.hit(t)), glass.rgb,
                    0.35f + 0.3f * SeraphTimeline.hit(t));
        }
    }

    /** The Eye's pupil: where everything is pulled to (the core's face, towards the camera). */
    static float[] pupil() {
        return new float[]{BX, FEET + 14f, BZ + 6.5f};
    }

    /**
     * Pulls boxes into the pupil on a tightening spiral: their middle swings round the pupil, closing in and
     * dropping towards it, and they shrink as they go - {@code pull} 0..1, {@code phase} offsets the swirl.
     */
    static void pullInto(List<Part> parts, float[] centre, float pull, float phase) {
        float[] pu = pupil();
        float k = ease(pull);
        float dx = centre[0] - pu[0], dy = centre[1] - pu[1], dz = centre[2] - pu[2];
        double ang = k * Math.PI * 3.0 + phase;
        float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
        float shrink = (float) Math.pow(1f - k, 1.3);
        float nx = pu[0] + (dx * cs - dz * sn) * shrink, ny = pu[1] + dy * shrink, nz = pu[2] + (dx * sn + dz * cs) * shrink;
        float[] spin = ry(ang * 0.6);
        float sc = Math.max(0.02f, 1f - k);
        for (Part part : parts) {
            float ox = part.x - centre[0], oy = part.y - centre[1], oz = part.z - centre[2];
            float[] o = apply(spin, ox, oy, oz);
            part.x = nx + o[0] * sc;
            part.y = ny + o[1] * sc;
            part.z = nz + o[2] * sc;
            part.hx *= sc;
            part.hy *= sc;
            part.hz *= sc;
            part.X = apply(spin, part.X[0], part.X[1], part.X[2]);
            part.Y = apply(spin, part.Y[0], part.Y[1], part.Y[2]);
            part.Z = apply(spin, part.Z[0], part.Z[1], part.Z[2]);
            if (part.beacon != null) part.beacon.scale *= sc;
        }
    }

    /** How far into the hold, 0..1: the glyph charges over LIFT_MS, then shakes in its hands until the release. */
    static float liftK(long t) {
        return ease((t - SeraphTimeline.THROW) / (float) SeraphTimeline.LIFT_MS);
    }

    static Pose pose(SeraphTimeline tl, long t, long pt) {
        Pose p = new Pose();
        p.x = SeraphTimeline.seraphX(Math.min(t, SeraphTimeline.REVEAL));
        float hit = SeraphTimeline.hit(t);
        p.creepy = SeraphTimeline.creepy(Math.min(t, SeraphTimeline.REVEAL + 1));
        Ending e = tl.ending();
        p.bodyX = -0.12f * hit;
        p.headX = -0.15f * hit;
        // Hurt: vanilla's red flash on each hit.
        p.g = p.b = 1f - 0.45f * hit;
        float sway = 0.03f * (float) Math.sin(pt / 600.0);
        p.armZ = new float[]{0.05f + sway, -0.05f - sway};
        // Shaking when angry, as an angry enderman does.
        if (p.creepy > 0.5f && t < SeraphTimeline.REVEAL) {
            p.shakeX = 0.35f * (float) Math.sin(t * 1.7) * p.creepy;
            p.shakeZ = 0.35f * (float) Math.cos(t * 2.3) * p.creepy;
        }
        // The throw. A vanilla enderman never lifts its arms (the user): they stay in the carry pose
        // (xRot -0.5) through the hold, and only push a little forward as the glyph leaves them.
        float tk = SeraphTimeline.throwK(t);
        if (tk > 0f) {
            float push = t >= SeraphTimeline.RELEASE ? (float) Math.sin(Math.PI * Math.min(1f, (t - SeraphTimeline.RELEASE) / 320f)) : 0f;
            float a = -0.5f - 0.3f * push;
            p.armX = new float[]{a, a};
            p.carry = tk < 0.5f;
            p.bodyX = -0.08f * push;
        }
        if (t >= SeraphTimeline.REVEAL) {
            p.carry = false;
            p.armX = new float[]{-0.4f, -0.4f};
        }
        if (t < SeraphTimeline.TELEPORT_IN + 120) p.alpha = ease((t - SeraphTimeline.TELEPORT_IN) / 120f);

        if (e == Ending.DRAGON && t >= SeraphTimeline.D_FREEZE) p.g = p.b = p.r = 1f;
        float death = tl.death(t);
        if (death >= 0f) {
            // Vanilla's death: a red flash and a fall onto its side, 90 degrees.
            p.roll = (float) Math.toRadians(90) * death;
            p.g = p.b = 0.4f;
            if (tl.ending() == Ending.SKIN && t >= SeraphTimeline.HEAD_OFF) p.headless = true;
        }
        // A bust: it blinks away a quarter of a second after the reveal.
        return p;
    }

    // ================================================================== the glyph on the ground, and its beam

    static int beamColour(SeraphTimeline tl, long t) {
        int rgb = tl.drop().lands.rgb;
        if (tl.ending() == Ending.CHALICE) rgb = mix(rgb, 0xC81E1E, ease((t - SeraphTimeline.REVEAL - 400) / 500f));
        if (tl.ending() == Ending.DRAGON) {
            rgb = mix(rgb, SeraphTimeline.BYZANTIUM, tl.deepen(t));
            // Grey with the stopped world, until the breath brings its colour back.
            rgb = mix(rgb, 0x6E6A70, tl.grey(t) * (1f - tl.paint(t)));
        }
        return rgb;
    }

    static void groundBeacon(ChopScene.Canvas c, SeraphTimeline tl, long t, long pt, Glass glass) {
        float tk = SeraphTimeline.throwK(t);
        if (tk < 0.5f) return;
        Beacon b = new Beacon();
        b.glass = glass.rgb;
        if (t < SeraphTimeline.REVEAL) {
            // In flight from the hands to the spot, turning, growing to a full block.
            float k = (tk - 0.5f) / 0.5f;
            float[] from = carryPoint(tl, SeraphTimeline.RELEASE, SeraphTimeline.RELEASE);
            float gx = BX, gy = FEET + 8f, gz = BZ;
            b.x = from[0] + (gx - from[0]) * k;
            b.z = from[2] + (gz - from[2]) * k;
            b.y = from[1] + (gy - from[1]) * k + 14f * (float) Math.sin(Math.PI * k);
            b.scale = 0.5f + 0.5f * k;
            float[] r = mul(ry(2.4f * k), rx(0.9f * (1f - k)));
            b.X = col(r, 0);
            b.Y = col(r, 1);
            b.Z = col(r, 2);
            drawBeacon(c, b);
            return;
        }
        b.x = BX;
        b.y = FEET + 8f;
        b.z = BZ;
        float[] yaw = ry(Math.toRadians(-12));
        b.X = col(yaw, 0);
        b.Y = col(yaw, 1);
        b.Z = col(yaw, 2);
        float bust = tl.bust(t);
        if (bust >= 0f) b.obsidian = ease(bust / 0.4f);
        b.glass = tl.drop().lands.rgb;
        float beam = tl.beam(t);
        if (tl.beaconStands(t)) {
            // Landed: it bounces a little into place.
            float land = (t - SeraphTimeline.REVEAL) / 120f;
            if (land < 1f) b.y += 2f * (float) Math.sin(Math.PI * land) * (1f - land);
            // The Dragon's stopped world greys the beacon too, and the breath's paint takes it.
            if (tl.ending() == Ending.DRAGON && tl.grey(t) > 0f) ChopScene.gild = worldGild(tl, t);
            ChopScene.inkWidth = Math.max(1.5f, ChopScene.size / 2.5f);
            beaconInk(c, b);
            ChopScene.inkWidth = 0f;
            drawBeacon(c, b);
            ChopScene.gild = null;
        }
        if (beam > 0.01f) {
            int rgb = beamColour(tl, t);
            float top = FEET + 16f + 420f * beam;
            float wid = tl.beamWidth(t);
            float scroll = -pt / 25f;
            // The outer glow, wide and faint, then the core - vanilla's two layers.
            boolean black = rgb == Glass.BLACK.rgb;
            beam(c, TEX_BEAM, 16, 16, BX, FEET + 16f, BZ, BX, top, BZ, 4.6f * wid, 6.4f, scroll * 0.5f,
                    ChopScene.alpha((black ? 0.4f : 0.22f) * ChopScene.sceneAlpha) | (black ? BLACK_RIM : rgb));
            // The bust's black beam gets a bright purple edge: black on the night sky could not be seen (the review, S4).
            if (black) beam(c, TEX_WHITE, 4, 4, BX, FEET + 16f, BZ, BX, top, BZ, 2.4f * wid + 0.5f, 20f, 0f,
                    ChopScene.alpha(0.9f * ChopScene.sceneAlpha) | BLACK_RIM);
            beam(c, TEX_BEAM, 16, 16, BX, FEET + 16f, BZ, BX, top, BZ, 2.4f * wid, 6.4f, scroll,
                    ChopScene.alpha(ChopScene.sceneAlpha) | mix(rgb, 0xFFFFFF, rgb == Glass.BLACK.rgb ? 0f : 0.15f));
            ChopScene.project(BX, FEET + 18f, BZ);
            if (tl.drop().win()) ChopScene.glow(c, ChopScene.P[0], ChopScene.P[1], ChopScene.size * 24f * wid, rgb, 0.45f);
        }
    }

    // ================================================================== the pillars and the End Crystals

    /** The edge drawn round a black beam, so the bust's tell shows against the night. */
    static final int BLACK_RIM = 0x9A5CF0;

    /**
     * How far out the pillars stand: as far as the screen allows with 6% of it clear at the edge, at the camera's
     * framing at the reveal. At 48 the right crystal was half off a 16:9 screen (the review, S2).
     */
    static float pillarX(int w, int h) {
        float halfW = w / (2f * Math.max(1, h) * 0.64f / 46f * 1.1f);
        return Math.max(26f, Math.min(48f, halfW * 0.88f - 19f));
    }

    private static int pillarW = 640, pillarH = 360;
    static final float PTOP = FEET + 6f;
    private static final float PILLAR_COS = (float) Math.cos(Math.toRadians(YAW0)), PILLAR_SIN = (float) Math.sin(Math.toRadians(YAW0));

    static float[] pillarAt(int side) {
        float px = pillarX(pillarW, pillarH);
        return new float[]{side * px * PILLAR_COS, PTOP, side * px * PILLAR_SIN};
    }

    static final int[][] CRYSTAL_UV = scale2(uv(0, 0, 8, 8, 8, false)), CORE_CUBE_UV = scale2(uv(32, 0, 8, 8, 8, false)),
            CRYSTAL_BASE_UV = scale2(uv(0, 16, 12, 4, 12, false));

    static int[][] scale2(int[][] r) {
        int[][] o = new int[r.length][];
        for (int i = 0; i < r.length; i++) o[i] = new int[]{r[i][0] * 2, r[i][1] * 2, r[i][2] * 2, r[i][3] * 2};
        return o;
    }

    /** Where a crystal's heart is now (it bobs). */
    static float[] crystalAt(int side, long pt, float sink) {
        float[] p = pillarAt(side);
        float age = pt / 50f + (side > 0 ? 17f : 0f);
        float bob = 3f * (float) Math.sin(age * 0.2f);
        return new float[]{p[0], p[1] + 6f + 14f + bob - 40f * sink, p[2]};
    }

    static void pillars(ChopScene.Canvas c, SeraphTimeline tl, long t, long pt) {
        float rise = ease((t - 100) / 500f);
        if (rise <= 0f) return;
        float blow = tl.crystalsBlow(t), sink = Math.max(0f, tl.crystalsSink(t));
        for (int side = -1; side <= 1; side += 2) {
            float[] top = pillarAt(side);
            float lift = (1f - rise) * 70f;
            // Obsidian, two blocks across, running down out of the shot.
            ChopScene.gildCells = 8;
            for (int pass = 0; pass < 2; pass++) {
                ChopScene.inkWidth = pass == 0 ? Math.max(1.5f, ChopScene.size / 2.5f) : 0f;
                for (int row = 0; row < 5; row++) {
                    for (int a = -1; a <= 1; a += 2) {
                        for (int b2 = -1; b2 <= 1; b2 += 2) {
                            float x = top[0] + a * 8f, z = top[2] + b2 * 8f, y = top[1] - 8f - row * 16f - lift;
                            ChopScene.box(x, y, z, 0, 0, 0, 8f, 8f, 8f, ChopScene.all(TEX_OBSIDIAN), FULL6, 0.9f, 0.9f, 0.95f, 0f, 1f, c);
                        }
                    }
                }
            }
            ChopScene.inkWidth = 0f;
            ChopScene.gildCells = 16;
            if (blow >= 0f) continue;
            float ca = 1f - sink;
            if (ca < 0.02f) continue;
            // The crystal's base (the bedrock plinth) and the crystal: core, inner glass, outer glass.
            // Three quarters of vanilla's size: next to the Seraph they were oversized (the review, S2).
            ChopScene.box(top[0], top[1] + 3f - lift - 40f * sink, top[2], 0, 0, 0, 9f, 3f, 9f, ChopScene.all(TEX_CRYSTAL),
                    CRYSTAL_BASE_UV, 1, 1, 1, 0, ca, c);
            float[] ctr = crystalAt(side, pt, sink);
            ctr[1] -= lift;
            float pull = tl.eyePull(t);
            if (pull > 0f) {
                List<Part> one = new ArrayList<>();
                Part q = new Part();
                q.x = ctr[0];
                q.y = ctr[1];
                q.z = ctr[2];
                q.X = new float[]{1, 0, 0};
                q.Y = new float[]{0, 1, 0};
                q.Z = new float[]{0, 0, 1};
                q.hx = q.hy = q.hz = 1f;
                one.add(q);
                pullInto(one, ctr.clone(), pull, side * 1.3f);
                ctr = new float[]{q.x, q.y, q.z};
                ca *= Math.max(0f, q.hx) * (1f - ease((pull - 0.8f) / 0.2f));
                if (ca < 0.02f) continue;
            }
            float age = pt / 50f + (side > 0 ? 17f : 0f);
            double spin = Math.toRadians(age * 3f);
            float s45 = (float) Math.sin(Math.PI / 4);
            float[] diag = axisAngle(s45, 0, s45, Math.toRadians(60));
            float[] outer = mul(ry(spin), diag);
            float[] inner = mul(outer, mul(diag, ry(spin)));
            float[] cube = mul(inner, mul(diag, ry(spin)));
            float[][] rots = {toWorld(cube), toWorld(inner), toWorld(outer)};
            float shrinkC = Math.max(0.02f, 1f - ease(tl.eyePull(t)));
            float[] halves = {6f * 0.765625f * shrinkC, 6f * 0.875f * shrinkC, 6f * shrinkC};
            String[] texes = ChopScene.all(TEX_CRYSTAL);
            int[][][] uvs = {CORE_CUBE_UV, CRYSTAL_UV, CRYSTAL_UV};
            for (int k = 0; k < 3; k++) {
                float[] r = rots[k];
                float hs = halves[k];
                ChopScene.boxAxes(ctr[0], ctr[1], ctr[2], col(r, 0), col(r, 1), col(r, 2), hs, hs, hs, texes, uvs[k], 1, 1, 1,
                        k == 0 ? 0.25f : 0.1f, ca * (k == 0 ? 1f : 0.85f), c);
            }
        }
    }

    /** Where the crystals' beams go: the glyph in its hands, then on the ground. */
    static float[] beamTarget(SeraphTimeline tl, long t, long pt) {
        if (SeraphTimeline.throwK(t) >= 0.5f) return new float[]{BX, FEET + 8f, BZ};
        return carryPoint(tl, t, pt);
    }

    /** Where the carried glyph is: vanilla's carried-block spot on the rig. */
    static float[] carryPoint(SeraphTimeline tl, long t, long pt) {
        Pose p = pose(tl, t, pt);
        float[] g = rigTransform(p);
        float[] cm = CARRY_CENTRE;
        return rigPoint(p, g, cm[0], FEET + 24f - cm[1], -cm[2]);
    }

    static void crystalBeams(ChopScene.Canvas c, SeraphTimeline tl, long t, long pt) {
        if (t < SeraphTimeline.TELEPORT_IN + 250) return;
        float a = ease((t - SeraphTimeline.TELEPORT_IN - 250) / 300f);
        if (tl.crystalsBlow(t) >= 0f) return;
        if (!tl.drop().win() && t >= SeraphTimeline.REVEAL) a *= 1f - ease((t - SeraphTimeline.REVEAL - 100) / 150f);
        float wide = 1f + 0.5f * SeraphTimeline.hit(t);
        if (tl.ending() == Ending.EYE) a *= 1f - ease((t - SeraphTimeline.E_PULL) / 300f);
        if (a < 0.02f) return;
        int beamRgb = mix(0xF0D8FF, 0x9A9A9A, tl.grey(t));
        float[] to = beamTarget(tl, t, pt);
        // Two cells wide over a solid core a cell wide: the texture alone, thin, read as dotted noise (the review, S3).
        float cell = unitFor(pillarH) / ChopScene.size;
        for (int side = -1; side <= 1; side += 2) {
            float[] from = crystalAt(side, pt, 0f);
            beam(c, TEX_WHITE, 4, 4, from[0], from[1], from[2], to[0], to[1], to[2], cell * wide, 20f, 0f,
                    ChopScene.alpha(0.4f * a * ChopScene.sceneAlpha) | mix(beamRgb, 0xA040F0, 0.55f));
            beam(c, TEX_WHITE, 4, 4, from[0], from[1], from[2], to[0], to[1], to[2], 0.5f * cell * wide, 20f, 0f,
                    ChopScene.alpha(0.8f * a * ChopScene.sceneAlpha) | beamRgb);
            beam(c, TEX_CRYSTAL_BEAM, 16, 256, from[0], from[1], from[2], to[0], to[1], to[2], 1.5f * cell * wide, 80f, -pt / 20f,
                    ChopScene.alpha(0.7f * a * ChopScene.sceneAlpha) | beamRgb);
        }
    }

    // ================================================================== Broken Heart Radiation

    /** The fight's laser phase: 12 beams in 4 groups of 3, 20 blocks long, sweeping round from its chest. */
    static void lasers(CrystalVeins.Fill layer, SeraphTimeline tl, long t, int h) {
        float k = SeraphTimeline.radiation(t);
        if (k < 0f) return;
        float g = unitFor(h);
        float x0 = SeraphTimeline.seraphX(t), y0 = FEET + 32f;
        float turn = (t - SeraphTimeline.HANG) / 1000f * 2.6f;
        float fade = ChopScene.sceneAlpha * (1f - ease((k - 0.85f) / 0.15f));
        for (int group = 0; group < 4; group++) {
            float on = ease((k - group * 0.12f) / 0.1f);
            if (on <= 0f) continue;
            for (int i = 0; i < 3; i++) {
                double ang = turn + group * Math.toRadians(30) + i * Math.PI * 2 / 3;
                float dx = (float) Math.cos(ang), dz = (float) Math.sin(ang);
                float reach = 320f * on;
                float step = Math.max(0.5f, g / ChopScene.size);
                for (float r = 4f; r < reach; r += step) {
                    ChopScene.project(x0 + dx * r, y0, dz * r);
                    float px = ChopScene.P[0], py = ChopScene.P[1];
                    float a = fade * (1f - r / 340f);
                    if (a < 0.02f) continue;
                    layer.fill(Math.round(px / g) * g, Math.round(py / g) * g - g, g, g * 3, (Math.round(a * 160) << 24) | 0xD02CC0);
                    layer.fill(Math.round(px / g) * g, Math.round(py / g) * g, g, g, (Math.round(a * 255) << 24) | 0xFFD8F8);
                }
            }
        }
    }

    // ================================================================== particles

    /** The End's portal particles drifting up everywhere, and bursts where the Seraph blinks. */
    static void particles(CrystalVeins.Fill layer, SeraphTimeline tl, long t, long pt, int w, int h) {
        float g = unitFor(h);
        float a = ChopScene.sceneAlpha * ease(t / 400f);
        int[] cols = {0x8A2BE2, 0xB866F0, 0x5A1E8C, 0xE0B0FF, 0x6E2BB8};
        float grey = tl.grey(t);
        if (grey > 0f) for (int i = 0; i < cols.length; i++) cols[i] = mix(cols[i], 0x8A8A90, grey);
        Random r = new Random(tl.seed() * 11 + 3);
        float sec = pt / 1000f;
        for (int i = 0; i < 80; i++) {
            float x = (r.nextFloat() - 0.5f) * 150f, z = (r.nextFloat() - 0.5f) * 50f, y0 = FEET - 20f + r.nextFloat() * 90f;
            float speed = 6f + 10f * r.nextFloat(), ph = r.nextFloat();
            float life = (sec * speed / 90f + ph) % 1f;
            float y = y0 + life * 60f;
            x += 3f * (float) Math.sin(sec * 1.5f + i);
            ChopScene.project(x, y, z);
            float al = a * (float) Math.sin(Math.PI * life) * 0.8f;
            if (al < 0.03f) continue;
            layer.fill(Math.round(ChopScene.P[0] / g) * g, Math.round(ChopScene.P[1] / g) * g, g, g, (Math.round(al * 255) << 24) | cols[i % cols.length]);
        }
        // The teleport in: the swirl closing in, then a burst.
        if (t < SeraphTimeline.TELEPORT_IN + 500) swirl(layer, tl, t, 0f, g, h);
        // Each dodge: a burst where it was and where it lands.
        for (int k = 0; k < SeraphTimeline.DODGE_STEPS.length; k++) {
            long at = SeraphTimeline.STEPS[SeraphTimeline.DODGE_STEPS[k]];
            float from = k == 0 ? 0f : SeraphTimeline.DODGE_X[k - 1];
            burst(layer, t - at, from, g, 30 + k);
            burst(layer, t - at, SeraphTimeline.DODGE_X[k], g, 40 + k);
        }
        // A bust: it blinks away.
        if (!tl.drop().win()) burst(layer, t - SeraphTimeline.REVEAL - 250, SeraphTimeline.seraphX(SeraphTimeline.REVEAL), g, 50);
        if (tl.ending() == Ending.JACKPOT) {
            burst(layer, t - SeraphTimeline.VANISH, SeraphTimeline.seraphX(SeraphTimeline.REVEAL), g, 60);
        }
    }

    static void swirl(CrystalVeins.Fill layer, SeraphTimeline tl, long t, float cx, float g, int h) {
        int[] cols = {0x8A2BE2, 0xB866F0, 0xE0B0FF};
        float k = t / (float) SeraphTimeline.TELEPORT_IN;
        if (k < 1f) {
            for (int i = 0; i < 40; i++) {
                double ang = i / 40.0 * Math.PI * 2 + k * 3;
                float rr = 40f * (1f - ease(k)) + 2f;
                ChopScene.project(cx + (float) Math.cos(ang) * rr, FEET + 10f + (i % 7) * 5f, (float) Math.sin(ang) * rr * 0.5f);
                layer.fill(Math.round(ChopScene.P[0] / g) * g, Math.round(ChopScene.P[1] / g) * g, g, g,
                        (Math.round(255 * ChopScene.sceneAlpha * k) << 24) | cols[i % 3]);
            }
        }
        burst(layer, t - SeraphTimeline.TELEPORT_IN, cx, g, 7);
    }

    /** A teleport burst at world x, {@code since} ms ago. */
    static void burst(CrystalVeins.Fill layer, long since, float cx, float g, int salt) {
        if (since < 0 || since > 500) return;
        float k = since / 500f;
        int[] cols = {0x8A2BE2, 0xB866F0, 0xE0B0FF, 0x5A1E8C};
        Random r = new Random(salt * 7919L);
        for (int i = 0; i < 36; i++) {
            float x = cx + (r.nextFloat() - 0.5f) * 10f, y = FEET + r.nextFloat() * 46f, z = (r.nextFloat() - 0.5f) * 6f;
            float vx = (r.nextFloat() - 0.5f) * 30f, vy = (r.nextFloat() - 0.3f) * 20f;
            ChopScene.project(x + vx * k, y + vy * k, z);
            float a = ChopScene.sceneAlpha * (1f - k);
            layer.fill(Math.round(ChopScene.P[0] / g) * g, Math.round(ChopScene.P[1] / g) * g, g, g, (Math.round(a * 255) << 24) | cols[i % 4]);
        }
    }

    // ================================================================== the nametag

    static void nametag(ChopScene.Canvas c, SeraphTimeline tl, long t, int h) {
        if (t < SeraphTimeline.APPEAR - 200 || t >= SeraphTimeline.REVEAL) return;
        float a = ChopScene.sceneAlpha * ease((t - SeraphTimeline.APPEAR + 200) / 200f);
        ChopScene.project(SeraphTimeline.seraphX(t), FEET + 54f, 0f);
        float ts = Math.min(1.2f, h / 300f);
        float x = ChopScene.P[0], y = ChopScene.P[1];
        String name = "Voidgloom Seraph", hits = SeraphTimeline.hitsLeft(t) + " Hits";
        float cw = 6f * ts, wTot = (name.length() + 1 + hits.length()) * cw;
        c.fill(x - wTot / 2f - 2f, y - 6f * ts, wTot + 4f, 11f * ts, ChopScene.alpha(0.45f * a));
        c.text(name, x - wTot / 2f + name.length() * cw / 2f, y, ts, ChopScene.alpha(a) | 0xAA00AA);
        c.text(hits, x + wTot / 2f - hits.length() * cw / 2f, y, ts,
                ChopScene.alpha(a) | (SeraphTimeline.hit(t) > 0.3f ? 0xFFFFFF : 0xFF55FF));
    }

    // ================================================================== effects after the reveal

    static void effects(ChopScene.Canvas c, CrystalVeins.Fill layer, SeraphTimeline tl, long t, int w, int h) {
        float g = unitFor(h);
        float u = Math.max(1f, Math.round(ChopScene.size * 0.6f));
        float a = ChopScene.sceneAlpha;
        // The hits: magenta sparks off the shield.
        float hit = SeraphTimeline.hit(t);
        if (hit > 0.05f && t < SeraphTimeline.HANG) {
            int k = SeraphTimeline.step(t);
            Random r = new Random(tl.seed() * 3 + k);
            ChopScene.project(SeraphTimeline.seraphX(t), FEET + 32f, 6f);
            float ox = ChopScene.P[0], oy = ChopScene.P[1];
            float s = 1f - hit;
            for (int i = 0; i < 12; i++) {
                double ang = r.nextFloat() * Math.PI * 2;
                float sp = h * (0.04f + 0.05f * r.nextFloat());
                square(c, ox + (float) Math.cos(ang) * sp * s, oy + (float) Math.sin(ang) * sp * s, u, u,
                        ChopScene.alpha(hit * a) | (i % 3 == 0 ? 0xFFFFFF : 0xFF55FF));
            }
        }
        if (t < SeraphTimeline.REVEAL) return;
        // A win's glass shattering where the glyph stood.
        if (tl.drop().win() && !tl.beaconStands(t)) {
            long at = tl.ending() == Ending.EYE ? SeraphTimeline.E_BURST : tl.ending() == Ending.DRAGON ? SeraphTimeline.D_BURST : SeraphTimeline.SHATTER;
            float k = Math.min(1f, (t - at) / 900f);
            if (k < 1f) {
                Random r = new Random(tl.seed() * 5 + 1);
                ChopScene.project(BX, FEET + 8f, BZ);
                float ox = ChopScene.P[0], oy = ChopScene.P[1];
                int rgb = tl.drop().lands.rgb;
                for (int i = 0; i < 44; i++) {
                    double ang = Math.PI * (1.0 + r.nextFloat());
                    float sp = h * (0.08f + 0.18f * r.nextFloat());
                    float x = ox + (float) Math.cos(ang) * sp * k * 1.3f, y = oy + (float) Math.sin(ang) * sp * k + h * 0.35f * k * k;
                    int col = i % 4 == 0 ? 0xFFFFFF : (i % 4 == 1 ? 0x1E1428 : mix(rgb, 0xFFFFFF, 0.4f));
                    square(c, x, y, u * (1 + i % 2), u, ChopScene.alpha((1f - k) * a) | col);
                }
                ChopScene.glow(c, ox, oy, ChopScene.size * 40f, mix(rgb, 0xFFFFFF, 0.5f), 0.7f * (1f - k));
            }
        }
        // The crystals exploding: vanilla's big grey-white explosion puffs, in pixels.
        float blow = tl.crystalsBlow(t);
        if (blow >= 0f && blow < 1f) {
            for (int side = -1; side <= 1; side += 2) {
                float[] ctr = crystalAt(side, tl.phys(t), 0f);
                ChopScene.project(ctr[0], ctr[1], ctr[2]);
                float ox = ChopScene.P[0], oy = ChopScene.P[1];
                Random r = new Random(tl.seed() * 13 + side);
                for (int i = 0; i < 30; i++) {
                    double ang = r.nextFloat() * Math.PI * 2;
                    float rr = h * (0.03f + 0.12f * r.nextFloat()) * ease(blow);
                    float s = g * (2 + r.nextInt(3)) * (1f - 0.6f * blow);
                    int col = i % 3 == 0 ? 0xFFFFFF : (i % 3 == 1 ? 0xBDBDBD : 0x8A8A8A);
                    layer.fill(Math.round((ox + (float) Math.cos(ang) * rr) / g) * g, Math.round((oy + (float) Math.sin(ang) * rr - h * 0.05f * blow) / g) * g,
                            s, s, (Math.round(a * 255 * (1f - blow)) << 24) | col);
                }
                ChopScene.glow(c, ox, oy, ChopScene.size * 30f, 0xFFF0E0, 0.8f * (1f - blow));
            }
        }
        // The death poof: vanilla's white smoke.
        long poof = t - SeraphTimeline.POOF;
        if (tl.death(t) >= 0f && poof >= 0 && poof < 700) {
            float k = poof / 700f;
            Random r = new Random(tl.seed() * 17 + 9);
            float x0 = SeraphTimeline.seraphX(SeraphTimeline.REVEAL);
            for (int i = 0; i < 28; i++) {
                // Along the fallen body, lying to its side.
                float along = r.nextFloat() * 46f;
                ChopScene.project(x0 - along + (r.nextFloat() - 0.5f) * 6f, FEET + 4f + r.nextFloat() * 6f + 14f * k, (r.nextFloat() - 0.5f) * 8f);
                float s = g * (1 + r.nextInt(2));
                layer.fill(Math.round(ChopScene.P[0] / g) * g, Math.round(ChopScene.P[1] / g) * g, s, s,
                        (Math.round(a * 230 * (1f - k)) << 24) | (i % 2 == 0 ? 0xF4F4F4 : 0xC8C8C8));
            }
        }
        Ending e = tl.ending();
        if (e == Ending.CHALICE) chalice(c, layer, tl, t, g, h);
        if (e == Ending.ESPRESSO) espresso(layer, tl, t, g, h);
        if (e == Ending.JACKPOT) jackpot(c, layer, tl, t, w, h, g);
        if (e == Ending.DRAGON) dragonEffects(c, layer, tl, t, w, h, g);
    }

    // ================================================================== the drop and its words

    /** Where a drop that comes up the beam stands, 0..1 of the way. */
    /** Where the drop comes to rest, for the camera to centre on; null when there is no drop to show. */
    static float[] showTarget(SeraphTimeline tl) {
        if (!tl.drop().win()) return null;
        return switch (tl.ending()) {
            case EYE -> new float[]{BX, FEET + 8f + 6f + 14f, BZ};
            case SKIN -> new float[]{SKIN_HX, FEET + 4f, SKIN_HZ};
            case DICE -> diceRest();
            default -> new float[]{BX, FEET + 8f + (tl.ending() == Ending.DRAGON ? 42f : 22f), BZ};
        };
    }

    static float[] dropPoint(SeraphTimeline tl, long t) {
        float rise = tl.itemRise(t);
        // The dye rises higher out of its pool, so its words clear the paint and the bottom of the shot.
        float up = tl.ending() == Ending.DRAGON ? 42f : 22f;
        return new float[]{BX, FEET + 8f + up * rise, BZ};
    }

    static void drop(ChopScene.Canvas c, CrystalVeins.Fill layer, SeraphTimeline tl, long t, int w, int h) {
        Ending e = tl.ending();
        float ts = Math.min(1.35f, h / 300f);
        if (e == Ending.SKIN || e == Ending.DICE) {
            float[] at = e == Ending.SKIN ? new float[]{SKIN_HX, FEET + 4f, SKIN_HZ} : diceRest();
            ChopScene.project(at[0], at[1], at[2]);
            float half = 4f * 1.25f * ChopScene.size;
            labels(c, tl, t, ChopScene.P[0], ChopScene.P[1] + half + 4f * ts, h);
            return;
        }
        if (e == Ending.EYE) {
            // The core is drawn by eye(); only its words here, under it.
            if (t < SeraphTimeline.E_RISE) return;
            float[] cp = eyeCorePos(tl, t);
            ChopScene.project(cp[0], cp[1], cp[2]);
            float half = 6f * 1.3f * ChopScene.size;
            banner(c, tl, t, ChopScene.P[0], ChopScene.P[1] - half - 14f * ts, h);
            labels(c, tl, t, ChopScene.P[0], ChopScene.P[1] + half + 4f * ts, h);
            return;
        }
        boolean shown = tl.itemRise(t) > 0f;
        if (!shown) return;
        float[] p = dropPoint(tl, t);
        ChopScene.project(p[0], p[1], p[2]);
        float x = ChopScene.P[0], y = ChopScene.P[1];
        float grow = Math.min(1f, 0.3f + tl.itemRise(t));
        float size = h * (e == Ending.DRAGON ? 0.2f : (e == Ending.JACKPOT ? 0.18f : 0.15f)) * grow;
        float yaw = 32f + 10f * (float) Math.sin(t / 700.0);
        if (e == Ending.DRAGON) {
            float show = ease((t - tl.arrive() + 300) / 500f);
            if (show > 0f) rays(c, x, y, h, t, show, SeraphTimeline.BYZANTIUM);
            ChopScene.glow(c, x, y, size * 1.4f, SeraphTimeline.BYZANTIUM, 0.6f);
        }
        if (e == Ending.JACKPOT) {
            float show = ease((t - tl.arrive() + 300) / 500f);
            if (show > 0f) rays(c, x, y, h, t, show, beamColour(tl, t));
        }
        if (e == Ending.CHALICE) {
            float fill = ease((t - tl.arrive()) / 1200f);
            ChopScene.glow(c, x, y, size * 1.2f, 0xC81E1E, 0.55f * fill);
        }
        c.item(x, y, size, ChopScene.sceneAlpha * Math.min(1f, grow * 2f), yaw);
        banner(c, tl, t, x, y - size * 0.62f - 12f * ts, h);
        labels(c, tl, t, x, y + size * 0.62f, h);
    }

    static void labels(ChopScene.Canvas c, SeraphTimeline tl, long t, float x, float below, int h) {
        float na = tl.nameAlpha(t) * ChopScene.sceneAlpha;
        float ts = Math.min(1.35f, h / 300f);
        if (na > 0.02f) c.name(x, below + 8f * ts, 1.6f * ts, ChopScene.alpha(na) | tl.drop().colour, tl.letters(t));
        float la = tl.lineAlpha(t) * ChopScene.sceneAlpha;
        if (la > 0.02f) {
            if (tl.drop().oneIn > 0) {
                c.text("1 in ~" + String.format("%,d", tl.drop().oneIn) + " tier IV bosses" + (nthNote == null ? "" : " \u00b7 " + nthNote), x, below + 22f * ts, ts, ChopScene.alpha(la) | 0xC8C8C8);
            }
            long price = tl.price();
            if (price > 0 && t >= tl.countStart()) {
                float k = Math.min(1f, (t - tl.countStart()) / (float) SeraphTimeline.COUNT_MS);
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
    /** The drop line's own tier word and its colour, set by the screen each frame; null: Hypixel's top word. */
    public static String bannerWord;

    static void banner(ChopScene.Canvas c, SeraphTimeline tl, long t, float x, float y, int h) {
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

    /** Pixel rays turning behind a drop in the beam's colour. */
    static void rays(ChopScene.Canvas c, float x, float y, int h, long t, float alpha, int rgb) {
        float u = Math.max(1f, Math.round(ChopScene.size * 0.6f));
        int n = 12;
        float turn = t / 2600f, len = h * 0.55f;
        for (int i = 0; i < n; i++) {
            float ang = turn + i * (float) Math.PI * 2f / n;
            float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
            for (float r = h * 0.08f; r < len; r += u * 1.5f) {
                float fall = 1f - r / len;
                int col = i % 2 == 0 ? rgb : mix(rgb, 0xFFFFFF, 0.45f);
                square(c, x + cs * r, y + sn * r * 0.8f, u * (fall > 0.6f ? 2 : 1), u, ChopScene.alpha(alpha * ChopScene.sceneAlpha * fall * fall * 0.7f) | col);
            }
        }
    }

    // ================================================================== the small endings

    /** The Handy Blood Chalice: the beam reddens, blood drips up it from the fallen Seraph into the chalice, which brims over. */
    static void chalice(ChopScene.Canvas c, CrystalVeins.Fill layer, SeraphTimeline tl, long t, float g, int h) {
        long from = SeraphTimeline.REVEAL + 700, to = tl.arrive() + 1200;
        if (t < from) return;
        float[] cup = dropPoint(tl, t);
        ChopScene.project(cup[0], cup[1], cup[2]);
        float cx = ChopScene.P[0], cy = ChopScene.P[1];
        Random r = new Random(tl.seed() * 23 + 5);
        float x0 = SeraphTimeline.seraphX(SeraphTimeline.REVEAL);
        for (int i = 0; i < 30; i++) {
            long born = from + i * 55L;
            float k = (t - born) / 700f;
            if (k < 0f || k > 1f || born > to) continue;
            ChopScene.project(x0 - r.nextFloat() * 30f, FEET + 4f, (r.nextFloat() - 0.5f) * 8f);
            float sx = ChopScene.P[0], sy = ChopScene.P[1];
            float e = ease(k);
            float x = sx + (cx - sx) * e, y = sy + (cy - sy) * e - h * 0.06f * (float) Math.sin(Math.PI * k);
            layer.fill(Math.round(x / g) * g, Math.round(y / g) * g, g, g * 2, (Math.round(255 * ChopScene.sceneAlpha) << 24) | (i % 3 == 0 ? 0xE83A3A : 0x9E1010));
        }
        // Brimming over at the end: drops run down from the rim.
        long over = t - (tl.arrive() + 1300);
        for (int i = 0; i < 6 && over >= 0; i++) {
            float k = (over - i * 140) / 600f;
            if (k < 0f || k > 1f) continue;
            float x = cx + (i - 2.5f) * g * 2f, y = cy + h * 0.06f + k * k * h * 0.2f;
            layer.fill(Math.round(x / g) * g, Math.round(y / g) * g, g, g * 2, (Math.round(255 * (1f - k) * ChopScene.sceneAlpha) << 24) | 0xB01818);
        }
    }

    /** The Pocket Espresso Machine: steam curling up off it, and a pour. */
    static void espresso(CrystalVeins.Fill layer, SeraphTimeline tl, long t, float g, int h) {
        if (t < tl.arrive() - 200) return;
        float[] p = dropPoint(tl, t);
        ChopScene.project(p[0], p[1], p[2]);
        float cx = ChopScene.P[0], cy = ChopScene.P[1] - h * 0.08f;
        float sec = (t - tl.arrive()) / 1000f;
        for (int col = 0; col < 3; col++) {
            for (int i = 0; i < 9; i++) {
                float life = (sec * 0.8f + i / 9f + col * 0.31f) % 1f;
                float x = cx + (col - 1) * g * 4f + (float) Math.sin(life * 9f + col) * g * 2f, y = cy - life * h * 0.16f;
                float a = ChopScene.sceneAlpha * (1f - life) * 0.8f;
                layer.fill(Math.round(x / g) * g, Math.round(y / g) * g, g * 2, g * 2, (Math.round(a * 255) << 24) | (i % 2 == 0 ? 0xF2F2F2 : 0xD0D0D0));
            }
        }
        long pour = t - (tl.arrive() + 600);
        if (pour >= 0 && pour < 900) {
            float k = pour / 900f;
            float y0 = ChopScene.P[1] + h * 0.07f;
            ChopScene.project(p[0], FEET, p[2]);
            float ground = ChopScene.P[1];
            float bottom = y0 + (ground - y0) * Math.min(1f, k * 3f);
            float top = y0 + (ground - y0) * ease((k - 0.6f) / 0.4f);
            for (float y = top; y < bottom; y += g) {
                layer.fill(Math.round(cx / g) * g, Math.round(y / g) * g, g, g, (Math.round(255 * ChopScene.sceneAlpha) << 24) | 0x5A3418);
            }
        }
    }

    /** The Void Conqueror skin: the head comes off as it falls, rolls to a stop, and the skin spreads over it texel by texel. */
    static void skinHead(ChopScene.Canvas c, SeraphTimeline tl, long t, int h) {
        if (t < SeraphTimeline.HEAD_OFF) return;
        float x, y, z;
        float[] rot;
        if (t < SeraphTimeline.HEAD_LANDS) {
            float k = (t - SeraphTimeline.HEAD_OFF) / (float) (SeraphTimeline.HEAD_LANDS - SeraphTimeline.HEAD_OFF);
            float x0 = SeraphTimeline.seraphX(SeraphTimeline.REVEAL) - 14f, y0 = FEET + 30f;
            x = x0 + (SKIN_HX - x0) * k;
            z = SKIN_HZ * k;
            y = y0 + (FEET + 4f - y0) * k * k + 18f * (float) Math.sin(Math.PI * k);
            rot = mul(ry(5f * k), rx(3f * k));
        } else {
            float k = (t - SeraphTimeline.HEAD_LANDS) / 250f;
            x = SKIN_HX;
            z = SKIN_HZ;
            float open0 = 5f * (1f - ease((t - SeraphTimeline.HEAD_LANDS) / (float) (SeraphTimeline.SKIN_DONE - SeraphTimeline.HEAD_LANDS)));
            y = FEET + 4f + open0 + (k < 1f ? 3f * (float) Math.sin(Math.PI * k) * (1f - k) : 0f);
            // Face to the camera, a little turned (the camera looks from the -X side of +Z).
            rot = ry(Math.toRadians(-32));
        }
        float[] X = col(rot, 0), Y = col(rot, 1), Z = col(rot, 2);
        String skin = c.dropSkin();
        long spread = t - SeraphTimeline.HEAD_LANDS;
        boolean done = t >= SeraphTimeline.SKIN_DONE;
        // The jaw comes off with it, hanging open as it was (creepy), and closes up into the head as the
        // skin takes it (the user, 0.2.64).
        float open = 5f * (1f - ease(spread / (float) (SeraphTimeline.SKIN_DONE - SeraphTimeline.HEAD_LANDS)));
        float jx = x - Y[0] * open, jy = y - Y[1] * open, jz = z - Y[2] * open;
        ChopScene.inkWidth = Math.max(1.5f, ChopScene.size / 2.5f);
        if (open > 0.05f) ChopScene.boxAxes(jx, jy, jz, X, Y, Z, 3.5f, 3.5f, 3.5f, ChopScene.all(TEX_ENDERMAN), JAW_UV, 1, 1, 1, 0, 1f, c);
        ChopScene.boxAxes(x, y, z, X, Y, Z, 4f, 4f, 4f, ChopScene.all(TEX_ENDERMAN), HEAD_UV, 1, 1, 1, 0, 1f, c);
        ChopScene.inkWidth = 0f;
        if (open > 0.05f) ChopScene.boxAxes(jx, jy, jz, X, Y, Z, 3.5f, 3.5f, 3.5f, ChopScene.all(TEX_ENDERMAN), JAW_UV, 1, 1, 1, 0, 1f, c);
        if (!done || skin == null) {
            ChopScene.boxAxes(x, y, z, X, Y, Z, 4f, 4f, 4f, ChopScene.all(TEX_ENDERMAN), HEAD_UV, 1, 1, 1, 0, 1f, c);
            ChopScene.boxAxes(x, y, z, X, Y, Z, 4.02f, 4.02f, 4.02f, ChopScene.all(TEX_EYES), HEAD_UV, 1, 1, 1, 1f, 1f, c);
        }
        if (skin == null) return;
        if (done) {
            float glowK = (float) Math.exp(-(t - SeraphTimeline.SKIN_DONE) / 400.0);
            ChopScene.boxAxes(x, y, z, X, Y, Z, 4f, 4f, 4f, ChopScene.all(skin), SKULL, 1, 1, 1, 0.4f * glowK, 1f, c);
            ChopScene.boxAxes(x, y, z, X, Y, Z, 4.25f, 4.25f, 4.25f, ChopScene.all(skin), SKULL_HAT, 1, 1, 1, 0.4f * glowK, 1f, c);
            ChopScene.project(x, y, z);
            ChopScene.glow(c, ChopScene.P[0], ChopScene.P[1], ChopScene.size * 20f, 0xB050F0, 0.25f + 0.4f * glowK);
            return;
        }
        if (spread < 0) return;
        // The spread: each texel of the faces turned to the camera, in order of its distance from the top-front corner.
        float k = spread / (float) (SeraphTimeline.SKIN_DONE - SeraphTimeline.HEAD_LANDS);
        float[][] axes = {X, Y, Z};
        for (int axis = 0; axis < 3; axis++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                int fi = axis * 2 + (sign > 0 ? 0 : 1);
                float[] n = axes[axis];
                float nx = n[0] * sign, ny = n[1] * sign, nz = n[2] * sign;
                float facing = -ny * ChopScene.sp + (-nx * ChopScene.sy + nz * ChopScene.cy) * ChopScene.cp;
                if (facing <= 0.02f) continue;
                int ua = axis == 1 ? 0 : (axis == 0 ? 2 : 0), va = axis == 1 ? 2 : 1;
                float[] U = axes[ua], V = axes[va];
                // boxAxes' own convention: the face's texture runs along U*us from its corner, and along V*vs.
                float us = (axis == 0 && sign > 0) || (axis == 2 && sign < 0) ? -1f : 1f;
                float vs = axis == 1 ? 1f : -1f;
                float cx0 = x + nx * 4.03f - U[0] * 4f * us - V[0] * 4f * vs, cy0 = y + ny * 4.03f - U[1] * 4f * us - V[1] * 4f * vs,
                        cz0 = z + nz * 4.03f - U[2] * 4f * us - V[2] * 4f * vs;
                int[] rect = SKULL[fi];
                for (int j = 0; j < 8; j++) {
                    for (int i = 0; i < 8; i++) {
                        float d = (float) Math.hypot(i - 3.5f, j - (axis == 1 ? 7f : 0f)) / 10f + (axis == 1 ? 0f : 0.25f);
                        float order = d + 0.15f * (((i * 7 + j * 13 + fi * 5) % 10) / 10f);
                        if (order > k * 1.4f) continue;
                        // The texel's corner on the face.
                        float ox = cx0 + U[0] * us * i + V[0] * vs * j, oy = cy0 + U[1] * us * i + V[1] * vs * j,
                                oz = cz0 + U[2] * us * i + V[2] * vs * j;
                        ChopScene.worldQuad(skin, new int[]{rect[0] + i, rect[1] + j, 1, 1}, ox, oy, oz, U[0] * us, U[1] * us, U[2] * us,
                                V[0] * vs, V[1] * vs, V[2] * vs, ChopScene.alpha(ChopScene.sceneAlpha) | 0xFFFFFF, c);
                        // The skin's pattern is mostly in its hat layer, half a unit out - that texel too.
                        int[] hr = SKULL_HAT[fi];
                        float hk = 4.25f / 4f, hox = x + nx * 4.28f - U[0] * 4.25f * us - V[0] * 4.25f * vs + (U[0] * us * i + V[0] * vs * j) * hk,
                                hoy = y + ny * 4.28f - U[1] * 4.25f * us - V[1] * 4.25f * vs + (U[1] * us * i + V[1] * vs * j) * hk,
                                hoz = z + nz * 4.28f - U[2] * 4.25f * us - V[2] * 4.25f * vs + (U[2] * us * i + V[2] * vs * j) * hk;
                        ChopScene.worldQuad(skin, new int[]{hr[0] + i, hr[1] + j, 1, 1}, hox, hoy, hoz, U[0] * us * hk, U[1] * us * hk, U[2] * us * hk,
                                V[0] * vs * hk, V[1] * vs * hk, V[2] * vs * hk, ChopScene.alpha(ChopScene.sceneAlpha) | 0xFFFFFF, c);
                    }
                }
            }
        }
    }

    static float[] diceRest() {
        float[] r = HellionDice.pose(false, Long.MAX_VALUE / 4);
        return new float[]{BX + r[0], r[1] - 15f, BZ + r[2]};
    }

    /** The Sinful Dice: thrown out of the glyph's spot, tumbling to a stop - the Archfiend Dice's baked throw. */
    static void die(ChopScene.Canvas c, SeraphTimeline tl, long t, int h) {
        if (t < SeraphTimeline.DICE_LAUNCH) return;
        long ms = t - SeraphTimeline.DICE_LAUNCH;
        float[] p = HellionDice.pose(false, ms);
        float[] X = {p[3], p[6], p[9]}, Y = {p[4], p[7], p[10]}, Z = {p[5], p[8], p[11]};
        float x = BX + p[0], y = p[1] - 15f, z = BZ + p[2];
        float grow = 0.25f + 0.75f * ease(ms / 180f);
        String skin = c.dropSkin();
        if (skin == null) {
            ChopScene.project(x, y, z);
            c.item(ChopScene.P[0], ChopScene.P[1], 10f * ChopScene.size * grow, ChopScene.sceneAlpha, 32f);
            return;
        }
        for (int pass = 0; pass < 2; pass++) {
            ChopScene.inkWidth = pass == 0 ? Math.max(1.5f, ChopScene.size / 2.5f) : 0f;
            float s = 4f * grow * (pass == 0 ? 1.04f : 1f);
            ChopScene.boxAxes(x, y, z, X, Y, Z, s, s, s, ChopScene.all(skin), SKULL, 1, 1, 1, 0.75f * (1f - ease(ms / 350f)), 1f, c);
            ChopScene.boxAxes(x, y, z, X, Y, Z, s * 1.0625f, s * 1.0625f, s * 1.0625f, ChopScene.all(skin), SKULL_HAT, 1, 1, 1, 0, 1f, c);
        }
        ChopScene.inkWidth = 0f;
    }

    // ================================================================== the jackpot

    /** Where afterimage {@code k} stands on its ring round the glyph. */
    static float[] afterPos(int k) {
        double ang = Math.toRadians(22.5 + k * 45);
        return new float[]{BX + (float) Math.cos(ang) * 34f, BZ + (float) Math.sin(ang) * 22f, (float) ang};
    }

    static void afterimages(ChopScene.Canvas c, SeraphTimeline tl, long t) {
        if (t < SeraphTimeline.AFTER_FROM || t >= SeraphTimeline.SHOCK) return;
        float imp = Math.max(0f, tl.implode(t));
        List<Part> all = new ArrayList<>();
        for (int k = 0; k < SeraphTimeline.AFTERIMAGES; k++) {
            if (t < SeraphTimeline.afterAt(k)) continue;
            float[] at = afterPos(k);
            Pose p = new Pose();
            float pull = ease(imp);
            p.x = at[0] + (BX - at[0]) * pull;
            p.shakeZ = at[1] + (BZ - at[1]) * pull;
            // Facing the glyph.
            p.yaw = (float) Math.atan2(BX - at[0], BZ - at[1]) + 2f * imp;
            p.creepy = 1f;
            p.carry = false;
            p.armX = new float[]{-0.4f, -0.4f};
            float flick = 0.8f + 0.2f * (float) Math.sin(t * 0.05 + k);
            p.alpha = 0.75f * flick * (1f - imp);
            p.r = 1f;
            p.g = 0.5f;
            p.b = 1f;
            p.drop = 0f;
            for (Part part : rig(p, null)) {
                // Shrink towards the glyph as they implode.
                float s = 1f - 0.8f * pull;
                part.hx *= s;
                part.hy *= s;
                part.hz *= s;
                part.x = p.x + (part.x - p.x) * s;
                part.y = FEET + (part.y - FEET) * s;
                part.z = p.shakeZ + (part.z - p.shakeZ) * s;
                part.ink = false;
                // Lit from within, magenta - an echo, not a body.
                part.glow = Math.max(part.glow, 0.45f);
                all.add(part);
            }
        }
        drawRig(c, all, false);
    }

    /** Floating islands: the End stone ground breaking up and drifting upward after the shock. */
    static void islands(ChopScene.Canvas c, SeraphTimeline tl, long t) {
        if (t < SeraphTimeline.SHOCK) return;
        float k = (t - SeraphTimeline.SHOCK) / 1000f;
        Random r = new Random(tl.seed() * 29 + 3);
        for (int i = 0; i < 14; i++) {
            // Mostly behind, a few to the sides - never over the drop.
            float x = (r.nextFloat() - 0.5f) * 160f, z = -50f + r.nextFloat() * 40f, s = 2f + r.nextFloat() * 3.5f;
            if (Math.abs(x - BX) < 18f) x += x < BX ? -22f : 22f;
            float speed = 7f + 9f * r.nextFloat(), spin = (r.nextFloat() - 0.5f) * 1.5f;
            float y = FEET - s + Math.min(1f, k * 3f) * s * 1.2f + speed * k;
            float a = Math.min(1f, k * 4f) * (1f - ease((y - 60f) / 30f));
            if (a < 0.02f) continue;
            ChopScene.inkWidth = Math.max(1.5f, ChopScene.size / 2.5f);
            ChopScene.box(x, y, z, spin * k * 0.6f, spin * k, 0, s, s, s, ChopScene.all(TEX_END_STONE), FULL6, 1, 1, 1, 0, a, c);
            ChopScene.inkWidth = 0f;
            ChopScene.box(x, y, z, spin * k * 0.6f, spin * k, 0, s, s, s, ChopScene.all(TEX_END_STONE), FULL6, 0.72f, 0.72f, 0.78f, 0, a, c);
        }
    }

    static void jackpot(ChopScene.Canvas c, CrystalVeins.Fill layer, SeraphTimeline tl, long t, int w, int h, float g) {
        // The stutter: a flash of magenta where the beam stands.
        long st = t - SeraphTimeline.STUTTER;
        if (st >= 0 && st < 300) {
            ChopScene.project(BX, FEET + 30f, BZ);
            ChopScene.glow(c, ChopScene.P[0], ChopScene.P[1], ChopScene.size * 40f, 0xC74EBD, 0.6f * (1f - st / 300f));
        }
        // Each afterimage blinking in: a burst at its spot.
        for (int k = 0; k < SeraphTimeline.AFTERIMAGES; k++) {
            float[] at = afterPos(k);
            long since = t - SeraphTimeline.afterAt(k);
            if (since < 0 || since > 400) continue;
            float kk = since / 400f;
            Random r = new Random(k * 104729L);
            for (int i = 0; i < 18; i++) {
                ChopScene.project(at[0] + (r.nextFloat() - 0.5f) * 14f * (1 + kk), FEET + r.nextFloat() * 46f, at[1] + (r.nextFloat() - 0.5f) * 6f);
                layer.fill(Math.round(ChopScene.P[0] / g) * g, Math.round(ChopScene.P[1] / g) * g, g, g,
                        (Math.round(255 * (1f - kk) * ChopScene.sceneAlpha) << 24) | (i % 2 == 0 ? 0xE0B0FF : 0x8A2BE2));
            }
        }
        // The shock: a ring racing out, and a flash.
        long sh = t - SeraphTimeline.SHOCK;
        if (sh >= 0 && sh < 600) {
            float k = sh / 600f;
            ChopScene.project(BX, FEET + 10f, BZ);
            float ox = ChopScene.P[0], oy = ChopScene.P[1];
            float reach = (float) Math.hypot(w, h) * 0.6f * ease(k);
            for (int i = 0; i < 72; i++) {
                double ang = i / 72.0 * Math.PI * 2;
                layer.fill(Math.round((ox + (float) Math.cos(ang) * reach) / g) * g, Math.round((oy + (float) Math.sin(ang) * reach * 0.6f) / g) * g,
                        g * 2, g, (Math.round(255 * (1f - k) * ChopScene.sceneAlpha) << 24) | (i % 2 == 0 ? 0xC74EBD : 0xFFE0FA));
            }
            ChopScene.glow(c, ox, oy, ChopScene.size * 55f * (0.6f + 0.6f * k), 0xF0C8F0, 0.7f * (1f - k));
        }
    }

    // ================================================================== the Eye (the Judgement Core)

    /** The Judgement Core's front face, read off its NEU skin: the white texel the lids are made of. */
    private static final int LID_U = 9, LID_V = 8;

    /** Where the core stands: where the beacon's core was, lifting a little as it forms, then rising to be shown. */
    static float[] eyeCorePos(SeraphTimeline tl, long t) {
        float y = FEET + 8f + 6f * tl.eyeForm(t) + 14f * tl.itemRise(t);
        return new float[]{BX, y, BZ};
    }

    /**
     * "The Eye Opens" (the user, 0.2.66). Everything froze at the reveal but this: the glyph's glass cracks
     * and bursts; what is left is the Judgement Core itself, its real skin, turned to the camera. The eye -
     * a three-texel slit between the white lids of its hat layer - opens a row at a time, glances left,
     * then right, then stares straight out with a heavy pulse; the Seraph and both crystals are pulled
     * into the pupil, and it rises with its name.
     */
    static void eye(ChopScene.Canvas c, CrystalVeins.Fill layer, SeraphTimeline tl, long t, int h) {
        float g = unitFor(h);
        // The glass cracking before it bursts: jagged white lines across the glyph, spreading.
        float crack = tl.eyeCrack(t);
        if (crack > 0f && t < SeraphTimeline.E_BURST) {
            ChopScene.project(BX, FEET + 8f, BZ);
            float ox = ChopScene.P[0], oy = ChopScene.P[1], reach = 9f * ChopScene.size;
            Random r = new Random(tl.seed() * 19 + 4);
            for (int b = 0; b < 7; b++) {
                double ang = r.nextFloat() * Math.PI * 2;
                float x = ox, y = oy;
                int steps = 3 + r.nextInt(3);
                for (int k = 0; k < steps; k++) {
                    if ((b * 5 + k) / 40f > crack) break;
                    ang += (r.nextFloat() - 0.5f) * 1.2f;
                    float len = reach / steps * (0.8f + 0.4f * r.nextFloat());
                    float nx = x + (float) Math.cos(ang) * len, ny = y + (float) Math.sin(ang) * len;
                    for (float d = 0; d < len; d += g) {
                        float px = x + (nx - x) * d / len, py = y + (ny - y) * d / len;
                        layer.fill(Math.round(px / g) * g, Math.round(py / g) * g, g, g, (Math.round(255 * ChopScene.sceneAlpha) << 24) | 0xFFFFFF);
                    }
                    x = nx;
                    y = ny;
                }
            }
            ChopScene.glow(c, ox, oy, ChopScene.size * 20f, 0xD42F2F, 0.3f * crack);
        }
        if (t < SeraphTimeline.E_BURST) return;

        float form = tl.eyeForm(t), pulse = tl.eyePulse(t);
        float[] cp = eyeCorePos(tl, t);
        float hs = (5f + 1f * form) * (1f + 0.18f * pulse);
        // Square to the camera: the camera looks from the -X side of +Z.
        float[] r = ry(Math.toRadians(-YAW0));
        float[] X = col(r, 0), Y = col(r, 1), Z = col(r, 2);
        ChopScene.project(cp[0], cp[1], cp[2]);
        ChopScene.glow(c, ChopScene.P[0], ChopScene.P[1], ChopScene.size * (22f + 30f * pulse), 0xD42F2F, 0.25f + 0.55f * pulse);
        // The beacon's own core, still there for a moment, shrinking as the Judgement Core grows out of it.
        if (form < 1f) {
            float s = 5f * (1f - form);
            ChopScene.boxAxes(cp[0], cp[1], cp[2], X, Y, Z, s, s * 1.1f, s, ChopScene.all(TEX_BEACON), CORE_UV, 1, 1, 1, 0.4f, 1f - form, c);
        }
        String skin = c.dropSkin();
        if (skin == null) {
            c.item(ChopScene.P[0], ChopScene.P[1], hs * 2.6f * ChopScene.size * form, ChopScene.sceneAlpha, 0f);
            return;
        }
        float hsF = hs * form;
        if (hsF < 0.1f) return;
        ChopScene.inkWidth = Math.max(1.5f, ChopScene.size / 2.5f);
        ChopScene.boxAxes(cp[0], cp[1], cp[2], X, Y, Z, hsF * 1.0625f, hsF * 1.0625f, hsF * 1.0625f, ChopScene.all(skin), SKULL, 1, 1, 1, 0, 1f, c);
        ChopScene.inkWidth = 0f;
        ChopScene.boxAxes(cp[0], cp[1], cp[2], X, Y, Z, hsF, hsF, hsF, ChopScene.all(skin), SKULL, 1, 1, 1, 0.12f * pulse, 1f, c);

        // The eye, on the front face's base layer: the lids over its slit (rows 3-5, columns 1-6), and the
        // iris and pupil shifted a texel when it glances.
        float open = tl.eyeOpen(t);
        int look = tl.eyeLook(t);
        float step = 2f * hsF / 8f;
        float out = hsF + 0.02f;
        float ox = cp[0] + Z[0] * out - X[0] * hsF + Y[0] * hsF, oy = cp[1] + Z[1] * out - X[1] * hsF + Y[1] * hsF,
                oz = cp[2] + Z[2] * out - X[2] * hsF + Y[2] * hsF;
        int argb = ChopScene.alpha(ChopScene.sceneAlpha) | 0xFFFFFF;
        for (int row = 3; row <= 5; row++) {
            boolean lid = open < 1f / 3f || (open < 2f / 3f && row != 4);
            for (int colI = 1; colI <= 6; colI++) {
                int su, sv;
                if (lid) {
                    su = LID_U;
                    sv = LID_V;
                } else if (look != 0) {
                    su = 8 + Math.max(1, Math.min(6, colI - look));
                    sv = 8 + row;
                } else {
                    continue;
                }
                float tx = ox + X[0] * step * colI - Y[0] * step * row, ty = oy + X[1] * step * colI - Y[1] * step * row,
                        tz = oz + X[2] * step * colI - Y[2] * step * row;
                ChopScene.worldQuad(skin, new int[]{su, sv, 1, 1}, tx, ty, tz, X[0] * step, X[1] * step, X[2] * step,
                        -Y[0] * step, -Y[1] * step, -Y[2] * step, argb, c);
            }
        }
        // The hat layer last: the frame and the white lids round the slit.
        float hh = hsF * 1.0625f;
        ChopScene.boxAxes(cp[0], cp[1], cp[2], X, Y, Z, hh, hh, hh, ChopScene.all(skin), SKULL_HAT, 1, 1, 1, 0.12f * pulse, 1f, c);

        // The heavy pulse: a ring of dark red racing out from the pupil.
        long since = t - SeraphTimeline.E_PULSE;
        if (since >= 0 && since < 700) {
            float k = since / 700f;
            float[] pu = pupil();
            ChopScene.project(pu[0], pu[1], pu[2]);
            float px = ChopScene.P[0], py = ChopScene.P[1], reach = h * 0.7f * ease(k);
            for (int i = 0; i < 64; i++) {
                double a = i / 64.0 * Math.PI * 2;
                layer.fill(Math.round((px + (float) Math.cos(a) * reach) / g) * g, Math.round((py + (float) Math.sin(a) * reach * 0.7f) / g) * g,
                        g * 2, g, (Math.round(255 * (1f - k) * ChopScene.sceneAlpha) << 24) | (i % 2 == 0 ? 0x8C0000 : 0x1A0505));
            }
        }
    }

    // ================================================================== the Dragon (the Byzantium Dye)

    /** World units to a block for the dragon and its path: the vanilla dragon at 0.6, so it fits the shot. */
    static final float DS = 16f * 0.6f;
    /** The sign of a polygon's screen-space area when it faces the camera (settled in the preview: the eyes show). */
    static final float CULL_SIGN = -1f;
    /**
     * Vanilla flap phases: the wings held spread once it is down, and fully open for the rearing roar (0.5 is the
     * widest span; 0.55 lifts the tips a little). Settled in the preview - head-on they read as bare bones.
     */
    static final float WINGS_SPREAD = 0.5f, REAR_WINGS = 0.55f;
    /** The head lowered over the beacon for the breath (radians; positive is down in the rig). */
    static final float HEAD_LOWER = 0.3f;
    /**
     * The rearing roar: nose up this many degrees (positive is up in the rig - negative bows the head to the ground),
     * the head bent back down at the camera, lifted (blocks) so the hindquarters stay on the ground, and the camera
     * pushing in a little and dropping, so the head, the drop and its price all fit.
     */
    // The camera pulls back a little now instead of pushing in, and the dragon rears further back (REAR_BACK 9): close
    // up its wings crossed the banner and the dye sat under its belly (the review, S9).
    static final float REAR_PITCH = 50f, REAR_HEAD = 0.35f, REAR_LIFT = 4.6f, REAR_ZOOM = 0.24f, REAR_RISE = 6f;
    /**
     * Where it rears: straight behind the drop on the camera's line of sight, {@code REAR_BACK} blocks back from the
     * beacon. It hops there with one wingbeat as it rears. Still three-quarters on (head-on, the wings go edge-on).
     */
    static final float REAR_BACK = 9f, REAR_HOP = 1.1f, REAR_YAW = 225f;
    static final float REAR_X = (float) (BX / DS + REAR_BACK * Math.sin(Math.toRadians(YAW0))),
            REAR_Z = (float) (BZ / DS - REAR_BACK * Math.cos(Math.toRadians(YAW0)));

    /** The dragon's body, blocks: the baked flight, then the hop to its rearing spot (lifted so its hindquarters stay down). */
    static float[] dragonPos(SeraphTimeline tl, long t) {
        long ms = Math.max(0, tl.dragonMs(t));
        float[] p = SeraphDragon.pos(ms);
        float r = tl.rear(t);
        if (r <= 0f) return p;
        return new float[]{p[0] + (REAR_X - p[0]) * r, p[1] + REAR_LIFT * r + REAR_HOP * (float) Math.sin(Math.PI * r), p[2] + (REAR_Z - p[2]) * r};
    }

    static float dragonYaw(SeraphTimeline tl, long t) {
        long ms = Math.max(0, tl.dragonMs(t));
        float r = tl.rear(t), y = SeraphDragon.yaw(ms);
        float d = ((REAR_YAW - y) % 360f + 540f) % 360f - 180f;
        return y + d * r;
    }

    /** Whether the dragon's body is further from the camera than the pillars' line. */
    static boolean dragonBehind(SeraphTimeline tl, long t) {
        if (tl.dragonMs(t) < 0) return false;
        float[] p = dragonPos(tl, t);
        return ChopScene.depth(p[0] * DS, FEET + p[1] * DS, p[2] * DS) < ChopScene.depth(0f, FEET, 0f);
    }

    /** The dragon's mouth last frame, in the world, for the breath. */
    static float[] mouth;

    /**
     * The stopped world, texel by texel: grey stone spreading out from the glyph as time stops, then the
     * Byzantium paint creeping over it from the pool - over the ground, up the pillars and the crystals.
     */
    static ChopScene.Gild worldGild(SeraphTimeline tl, long t) {
        float greyReach = 260f * tl.grey(t), paintReach = 120f * tl.paint(t);
        return (x, y, z, cell, now, sx, sy) -> {
            float d = (float) Math.hypot(x - BX, z - BZ) + Math.max(0f, y - FEET) * 0.7f;
            float n = ((cell * 2654435761L) >>> 16 & 255) / 255f * 9f;
            if (paintReach > 0f && d + n < paintReach) {
                // One shade, so a painted face merges into a single quad pair (ChopSpecials.gildFace); the
                // texture shows through as relief. Mottled shades per texel cost a quad pair each.
                return 0x70 << 24 | SeraphTimeline.BYZANTIUM;
            }
            // The texture over the grey at 82%: at 50% everything frozen read as untextured grey cubes, missing
            // textures rather than stopped time (the review, S7).
            if (d + n < greyReach) return 0xD0 << 24 | 0x8A8A90;
            return 0;
        };
    }

    /** The pool of paint on the ground round the glyph, under everything (the ground layer). */
    static void pool(CrystalVeins.Fill under, SeraphTimeline tl, long t, int h) {
        float paint = tl.paint(t);
        if (paint <= 0f || under == null) return;
        float g = unitFor(h);
        float reach = 120f * paint;
        float cell = Math.max(0.8f, g / ChopScene.size);
        float sheen = t / 700f;
        for (float x = -reach; x <= reach; x += cell) {
            for (float z = -reach * 0.8f; z <= reach * 0.8f; z += cell) {
                float wx = BX + x, wz = BZ + z;
                float d = (float) Math.hypot(x, z / 0.8f);
                int hsh = (Math.round(wx / cell) * 73856093) ^ (Math.round(wz / cell) * 19349663);
                float n = ((hsh >>> 8) & 255) / 255f * 10f;
                if (d + n > reach) continue;
                int col = SeraphTimeline.BYZANTIUM;
                if (d + n > reach - 5f) col = 0x4A1A42;
                else if (((hsh >>> 4) & 15) == 0 || Math.abs((float) Math.sin(x * 0.08f + z * 0.05f + sheen)) > 0.985f) col = 0xB65AA6;
                ChopScene.project(wx, FEET + 0.3f, wz);
                under.fill(Math.round(ChopScene.P[0] / g) * g, Math.round(ChopScene.P[1] / g) * g, g, g,
                        (Math.round(235 * ChopScene.sceneAlpha) << 24) | col);
            }
        }
    }

    /** The flap clock: brisk in flight, quicker as it hovers in, slow once down. */
    public static float flapAt(long ms) {
        long flight = SeraphDragon.flightMs();
        float ticks = ms / 50f, f;
        float fl = Math.min(ticks, (flight - 1200) / 50f);
        f = fl * 0.06f;
        if (ticks > fl) f += Math.min(ticks - fl, 24f) * 0.1f;
        if (ticks > fl + 24f) f += (ticks - fl - 24f) * 0.02f;
        return f;
    }

    /**
     * The vanilla Ender Dragon, posed by the game's own model and animation (DragonRig) along its baked
     * flight, rasterised whole with a depth buffer (ModelRaster) and drawn as one image: a light per face,
     * the eyes layer on the head, a silhouette outline.
     */
    static void dragon(ChopScene.Canvas c, CrystalVeins.Fill layer, SeraphTimeline tl, long t, int h) {
        long ms = tl.dragonMs(t);
        if (ms < 0 || dragon == null) return;
        long flight = SeraphDragon.flightMs();
        SeraphScene.DragonPose dp = new SeraphScene.DragonPose();
        // The flight history vanilla bends the neck and tail from: the flight, then the hop.
        for (int i = 0; i < dp.histY.length; i++) {
            long at = Math.max(SeraphTimeline.D_IN, t - i * 50L);
            dp.histY[i] = dragonPos(tl, at)[1];
            dp.histYaw[i] = dragonYaw(tl, at);
        }
        dp.flapTime = flapAt(ms);
        boolean landed = ms >= flight;
        dp.landing = !landed && ms > flight - 1200;
        float rear = tl.rear(t);
        // Down, it stands rather than sits: vanilla's sitting pose puts the head at the ground, below the
        // beacon's top, where the beacon hides it. Standing, the head is 2 blocks up, over the beacon.
        dp.sitting = false;
        dp.distanceToEgg = landed ? 0.0 : 100.0;
        if (landed) {
            // Wings held spread once down, breathing a little.
            float f = dp.flapTime, k = ease((ms - flight) / 450f);
            float spread = (float) Math.floor(f) + WINGS_SPREAD + 0.03f * (1f - rear) * (float) Math.sin((ms - flight) / 260.0);
            if (spread < f - 0.5f) spread += 1f;
            dp.flapTime = f + (spread - f) * k;
        }
        // It lowers its head over the beacon, breathes, and lifts it again.
        float lower = ease((t - SeraphTimeline.D_HEAD) / 600f) * (1f - ease((t - SeraphTimeline.D_BREATH_END - 200) / 500f));
        dp.headPitch = HEAD_LOWER * lower;
        if (t >= SeraphTimeline.D_BREATH - 250 && t < SeraphTimeline.D_BREATH_END + 300) dp.jawOpen = 0.55f;
        if (rear > 0f) {
            // The rearing roar: nose up, head bent back down at the camera, jaws wide, wings swept up and open.
            dp.extraPitch = REAR_PITCH * rear;
            dp.headPitch += REAR_HEAD * rear;
            dp.jawOpen = Math.max(dp.jawOpen, 0.85f * rear);
            // One full wingbeat for the hop, ending swept up and open.
            dp.flapTime += rear * (1f + REAR_WINGS - WINGS_SPREAD);
        }
        List<float[]> polys = dragon.pose(dp);
        float[] pos = dragonPos(tl, t);
        float bx = pos[0] * DS, by = FEET + pos[1] * DS, bz = pos[2] * DS;
        float yawR = (float) Math.toRadians(dragonYaw(tl, t));
        float fx = (float) Math.sin(yawR), fz = (float) -Math.cos(yawR);

        // Project, light and sort every polygon.
        int n = polys.size();
        float[][] scr = new float[n][];
        float bestFwd = -1e9f;
        float[] mouthNow = null;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float[] q = polys.get(i);
            float[] o = new float[16];
            float depth = 0f;
            for (int k = 0; k < 4; k++) {
                float wx = bx + q[k * 5] * DS, wy = by + q[k * 5 + 1] * DS, wz = bz + q[k * 5 + 2] * DS;
                ChopScene.project(wx, wy, wz);
                o[k * 2] = ChopScene.P[0];
                o[k * 2 + 1] = ChopScene.P[1];
                o[12 + k] = ChopScene.depth(wx, wy, wz);
                depth += o[12 + k];
                minX = Math.min(minX, o[k * 2]);
                maxX = Math.max(maxX, o[k * 2]);
                minY = Math.min(minY, o[k * 2 + 1]);
                maxY = Math.max(maxY, o[k * 2 + 1]);
                if (q[20] >= 2f) {
                    float fwd = wx * fx + wz * fz;
                    if (fwd > bestFwd) {
                        bestFwd = fwd;
                        mouthNow = new float[]{wx, wy, wz};
                    }
                }
            }
            // The face's light: vanilla's two lights, roughly - lit from above, a little from the side.
            float ax = q[5] - q[0], ay = q[6] - q[1], az = q[7] - q[2], cx2 = q[15] - q[0], cy2 = q[16] - q[1], cz2 = q[17] - q[2];
            float nx = ay * cz2 - az * cy2, ny = az * cx2 - ax * cz2, nz = ax * cy2 - ay * cx2, nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            float lit = 0.6f;
            if (nl > 1e-6f) lit = 0.55f + 0.45f * Math.max(Math.abs(ny / nl) * 0.8f, Math.abs((nx * 0.3f + nz * 0.7f) / nl) * 0.6f);
            o[8] = depth / 4f;
            o[9] = lit;
            o[10] = i;
            // Faces turned away are always behind a front face of the same box, so the depth test would throw
            // them away anyway; skipping them halves the work. Not the wings: their membranes are single sheets.
            float area = (o[2] - o[0]) * (o[5] - o[1]) - (o[3] - o[1]) * (o[4] - o[0]);
            o[11] = q.length > 21 && q[21] > 0f || area * CULL_SIGN > 0f ? 1f : 0f;
            scr[i] = o;
        }
        if (mouthNow != null) mouth = mouthNow;
        float alpha = ChopScene.sceneAlpha * Math.min(1f, ms / 300f);
        if (alpha <= 0.01f) return;
        // Rasterised whole, with a depth buffer (ModelRaster): the faces, the eyes on them, then the outline.
        float ink = Math.max(1.5f, ChopScene.size / 2.2f);
        float pad = ink + 2f;
        if (!RASTER.begin(Math.max(0f, minX - pad), Math.max(0f, minY - pad), Math.min(screenW, maxX + pad), Math.min(screenH, maxY + pad),
                rasterScale)) return;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < n; i++) {
                float[] q = polys.get(i), o = scr[i];
                if (o[11] == 0f) continue;
                if (pass == 1 && q[20] < 1f) continue;
                rasterPoly(q, o, pass == 1);
            }
        }
        RASTER.ink(Math.max(1, Math.round(ink * RASTER.scale)));
        RASTER.fade(alpha);
        c.image(TEX_DRAGON_RASTER, RASTER.px, RASTER.w, RASTER.h, RASTER.x, RASTER.y, RASTER.w / RASTER.scale, RASTER.h / RASTER.scale);
    }

    /** The dragon's rasteriser: slot 0 its texture, slot 1 its eyes layer (set by the screen or the preview). */
    public static final ModelRaster RASTER = new ModelRaster();
    /** Image pixels a GUI pixel for the dragon: the screen sets its GUI scale (capped), the preview its own. */
    public static float rasterScale = 2f;
    static int screenW = 640, screenH = 360;
    public static final String TEX_DRAGON_RASTER = "seraph_dragon_raster";

    /** One of the dragon's polygons into the raster: corners matched to their UVs, so its texture lands the right way round. */
    static void rasterPoly(float[] q, float[] o, boolean eyeLayer) {
        float uMin = 9f, uMax = -9f, vMin = 9f, vMax = -9f;
        for (int k = 0; k < 4; k++) {
            uMin = Math.min(uMin, q[k * 5 + 3]);
            uMax = Math.max(uMax, q[k * 5 + 3]);
            vMin = Math.min(vMin, q[k * 5 + 4]);
            vMax = Math.max(vMax, q[k * 5 + 4]);
        }
        int org = -1, ua = -1, va = -1;
        for (int k = 0; k < 4; k++) {
            boolean u0 = Math.abs(q[k * 5 + 3] - uMin) < 1e-5f, v0 = Math.abs(q[k * 5 + 4] - vMin) < 1e-5f;
            boolean u1 = Math.abs(q[k * 5 + 3] - uMax) < 1e-5f;
            boolean v1 = Math.abs(q[k * 5 + 4] - vMax) < 1e-5f;
            if (u0 && v0) org = k;
            else if (u1 && v0) ua = k;
            else if (u0 && v1) va = k;
        }
        if (org < 0 || ua < 0 || va < 0 || uMax - uMin <= 0f || vMax - vMin <= 0f) return;
        float px = o[org * 2], py = o[org * 2 + 1];
        RASTER.face(px, py, o[ua * 2] - px, o[ua * 2 + 1] - py, o[va * 2] - px, o[va * 2 + 1] - py,
                o[12 + org], o[12 + ua] - o[12 + org], o[12 + va] - o[12 + org], Float.NaN, 0f, 0f,
                uMin, vMin, uMax, vMax, eyeLayer ? 1 : 0, eyeLayer ? 1f : o[9], 0xFFFFFF, 0, eyeLayer);
    }

    /** The Dragon's effects: the Seraph turned to grey dust, the breath, the burst, the falling embers. */
    static void dragonEffects(ChopScene.Canvas c, CrystalVeins.Fill layer, SeraphTimeline tl, long t, int w, int h, float g) {
        float a = ChopScene.sceneAlpha;
        // The landing gust: the frozen Seraph crumbles into grey dust; dust kicked up round its feet.
        long land = t - SeraphTimeline.D_LAND;
        if (land >= 0 && land < 1200) {
            float k = land / 1200f;
            Random r = new Random(tl.seed() * 43 + 1);
            float x0 = SeraphTimeline.seraphX(SeraphTimeline.REVEAL);
            for (int i = 0; i < 90; i++) {
                float px = x0 + (r.nextFloat() - 0.5f) * 12f, py = FEET + r.nextFloat() * 46f, pz = (r.nextFloat() - 0.5f) * 6f;
                float vx = (r.nextFloat() - 0.2f) * 40f, vy = r.nextFloat() * 18f - 4f;
                ChopScene.project(px + vx * k, py + vy * k - 20f * k * k, pz + 25f * k);
                layer.fill(Math.round(ChopScene.P[0] / g) * g, Math.round(ChopScene.P[1] / g) * g, g, g,
                        (Math.round(255 * (1f - k) * a) << 24) | (i % 3 == 0 ? 0x5A5A60 : 0x9A9AA0));
            }
            for (int i = 0; i < 40; i++) {
                double ang = r.nextFloat() * Math.PI * 2;
                float rr = 30f + 60f * ease(k) * r.nextFloat();
                ChopScene.project((float) Math.cos(ang) * rr, FEET + 2f + 6f * k, -60f + (float) Math.sin(ang) * rr * 0.5f);
                layer.fill(Math.round(ChopScene.P[0] / g) * g, Math.round(ChopScene.P[1] / g) * g, g * 2, g,
                        (Math.round(200 * (1f - k) * a) << 24) | 0x8A8A90);
            }
        }
        // The breath: a stream of dragon's breath from its jaws onto the glyph.
        if (mouth != null && t >= SeraphTimeline.D_BREATH && t < SeraphTimeline.D_BREATH_END + 400) {
            float on = Math.min(1f, (t - SeraphTimeline.D_BREATH) / 200f) * (1f - ease((t - SeraphTimeline.D_BREATH_END) / 400f));
            float[] to = {BX, FEET + 16f, BZ};
            Random r = new Random(tl.seed() * 47 + 3);
            float sec = (t - SeraphTimeline.D_BREATH) / 1000f;
            int[] cols = {0xC64BD8, 0xE070F0, SeraphTimeline.BYZANTIUM, 0xF0B8FF, 0x9A2BB0};
            for (int i = 0; i < 260; i++) {
                float ph = r.nextFloat(), speed = 0.9f + 0.6f * r.nextFloat(), sx = (r.nextFloat() - 0.5f) * 30f, sz = (r.nextFloat() - 0.5f) * 30f;
                // Starting a little past the jaws: the layer is drawn over the scene, and puffs at the mouth hid the head.
                float life = 0.12f + 0.88f * ((sec * speed * 1.4f + ph) % 1f);
                // A cone from the jaws, spreading, billowing out along the ground where it lands.
                float spread = life * life;
                float x = mouth[0] + (to[0] - mouth[0]) * life + sx * spread, y = mouth[1] + (to[1] - mouth[1]) * life - 6f * life * life + 4f * spread,
                        z = mouth[2] + (to[2] - mouth[2]) * life + sz * spread;
                ChopScene.project(x, y, z);
                float s = g * (1 + (life > 0.35f ? 1 : 0) + (life > 0.75f ? 1 : 0));
                layer.fill(Math.round(ChopScene.P[0] / g) * g, Math.round(ChopScene.P[1] / g) * g, s, s,
                        (Math.round(255 * on * a * (1f - 0.5f * life)) << 24) | cols[i % cols.length]);
            }
            // The cloud it leaves: big slow puffs swelling round the glyph and lingering.
            for (int i = 0; i < 46; i++) {
                float born = r.nextFloat() * 1.6f, ang = r.nextFloat() * 6.283f, dist = 6f + r.nextFloat() * 26f;
                float k = (sec - born) / 1.4f;
                if (k < 0f || k > 1f) continue;
                ChopScene.project(to[0] + (float) Math.cos(ang) * dist * (0.4f + 0.6f * k), to[1] - 6f + 10f * k + r.nextFloat() * 8f,
                        to[2] + (float) Math.sin(ang) * dist * 0.7f * (0.4f + 0.6f * k));
                float s = g * (2 + Math.round(2f * k));
                layer.fill(Math.round(ChopScene.P[0] / g) * g, Math.round(ChopScene.P[1] / g) * g, s, s,
                        (Math.round(170 * on * a * (1f - k)) << 24) | cols[i % cols.length]);
            }
            ChopScene.project(mouth[0], mouth[1], mouth[2]);
            ChopScene.glow(c, ChopScene.P[0], ChopScene.P[1], ChopScene.size * 20f, 0xC64BD8, 0.5f * on);
            ChopScene.project(to[0], to[1], to[2]);
            ChopScene.glow(c, ChopScene.P[0], ChopScene.P[1], ChopScene.size * 50f, SeraphTimeline.BYZANTIUM, 0.65f * on);
        }
        // The glyph bursting with paint.
        long burst = t - SeraphTimeline.D_BURST;
        if (burst >= 0 && burst < 900) {
            float k = burst / 900f;
            ChopScene.project(BX, FEET + 8f, BZ);
            float ox = ChopScene.P[0], oy = ChopScene.P[1];
            Random r = new Random(tl.seed() * 53 + 2);
            for (int i = 0; i < 70; i++) {
                double ang = Math.PI * (1.0 + r.nextFloat());
                float sp = h * (0.1f + 0.3f * r.nextFloat());
                float x = ox + (float) Math.cos(ang) * sp * k * 1.4f, y = oy + (float) Math.sin(ang) * sp * k + h * 0.5f * k * k;
                layer.fill(Math.round(x / g) * g, Math.round(y / g) * g, g * (1 + i % 2), g * (1 + i % 2),
                        (Math.round(255 * (1f - k) * a) << 24) | (i % 3 == 0 ? 0xB65AA6 : SeraphTimeline.BYZANTIUM));
            }
            ChopScene.glow(c, ox, oy, ChopScene.size * 60f, SeraphTimeline.BYZANTIUM, 0.8f * (1f - k));
        }
        // Embers raining down once the dye stands: Byzantium and pink, all across.
        long rain = t - tl.arrive() + 300;
        if (rain >= 0) {
            float sec = rain / 1000f, on = Math.min(1f, sec * 2f);
            Random r = new Random(tl.seed() * 59 + 7);
            for (int i = 0; i < 110; i++) {
                float x0 = r.nextFloat() * w, speed = 0.25f + 0.35f * r.nextFloat(), ph = r.nextFloat();
                float life = (sec * speed + ph) % 1f;
                float x = x0 + (float) Math.sin(sec * 1.7f + i) * g * 3f, y = -g + life * (h + 2 * g);
                int col = i % 4 == 0 ? 0xFFE0FA : (i % 4 == 1 ? 0xE070F0 : SeraphTimeline.BYZANTIUM);
                layer.fill(Math.round(x / g) * g, Math.round(y / g) * g, g, g, (Math.round(255 * on * a * (1f - 0.4f * life)) << 24) | col);
            }
        }
    }

    // ================================================================== bits

    static void square(ChopScene.Canvas c, float x, float y, float s, float grid, int argb) {
        if ((argb >>> 24) < 4) return;
        c.fill(Math.round(x / grid) * grid - s / 2f, Math.round(y / grid) * grid - s / 2f, s, s, argb);
    }

    static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** The screen size, for the pillars' spread; set by the caller each frame. */
    public static void screen(int w, int h) {
        pillarW = w;
        pillarH = h;
    }
}
