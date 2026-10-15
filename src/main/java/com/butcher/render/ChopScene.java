package com.butcher.render;

import com.butcher.anim.ChopTimeline;
import com.butcher.render.art.ChopArt;
import com.butcher.render.art.PropArt;
import com.butcher.render.model.CleaverMesh;
import com.butcher.render.model.Mesh;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws one frame of "The Chopping Block" onto a {@link Canvas} - the one drawing routine for both
 * the game ({@code gui/ChopScreen}) and the offline preview ({@code tools/ChopPreview}).
 *
 * Everything is real 3D through the orthographic projection the rest of the mod uses (yaw, pitch,
 * drop Z - see ModelProjection): boxes turned by three angles and drawn face by face, only the
 * faces towards the camera, pieces back to front. World units are texels; the head is eight
 * across, sitting on the block's top at y = 0. The game shows through behind, dimmed.
 */
public final class ChopScene {
    private ChopScene() {}

    /** The primitives the scene needs. Coordinates are GUI pixels. */
    public interface Canvas {
        /** A rect of texture {@code tex} (its own size) mapped onto a parallelogram: corner and two edges. */
        void quad(String tex, float ox, float oy, float ux, float uy, float vx, float vy, int u0, int v0, int uw, int vh, int argb);
        /** A whole texture, centred, {@code w x h}, turned {@code rot} degrees. */
        void sprite(String tex, float cx, float cy, float w, float h, float rot, int argb);
        void line(float x0, float y0, float x1, float y1, float width, int argb);
        void fill(float x, float y, float w, float h, int argb);
        /** The drop's own item, centred; {@code size} in pixels; a head turned to {@code yaw}. */
        void item(float cx, float cy, float size, float alpha, float yaw);
        void name(float cx, float cy, float scale, int argb, int letters);
        void text(String text, float cx, float cy, float scale, int argb);
        /** The texture of a creature's own head skin (64 px), or null while it is not ready. */
        String creatureSkin(ChopTimeline.Creature creature);
        /** The texture of the drop's own head skin (64 px) - the Relic's - or null. */
        String dropSkin();

        /** Any mythological creature's head skin by its HeadSkins.CREATURES key (the Dye's parade), or null. */
        default String bestiarySkin(String key) {
            return null;
        }
        /**
         * A ready-made image, straight ARGB, {@code iw x ih} (the first {@code iw * ih} of {@code argb}), drawn over
         * the GUI rect ({@code x}, {@code y}, {@code w}, {@code h}) with no smoothing - for what is rasterised whole
         * (the Ender Dragon). {@code key} names the texture it goes through.
         */
        default void image(String key, int[] argb, int iw, int ih, float x, float y, float w, float h) {
        }

        /**
         * {@link #image}, drawn onto any parallelogram: its top-left corner at ({@code ox}, {@code oy}), its top edge
         * ({@code ux}, {@code uy}) and its left edge ({@code vx}, {@code vy}) - for a flat plane composed on the CPU
         * (Eruption's lava), which an orthographic camera turns into one affine image.
         */
        default void imageAffine(String key, int[] argb, int iw, int ih, float ox, float oy, float ux, float uy, float vx, float vy) {
        }
    }

    static final String SHEET = ChopArt.TEX_SHEET;
    static final String TEX_GLOW_NAME = ChopArt.TEX_GLOW;

    // Per-frame camera state. Drawn from one thread only.
    static float cy, sy, cp, sp, size, fx, fy, fz, sx0, sy0;
    static final float[] P = new float[2];
    /** While set, every face drawn gets the gold laid over it, cell by cell, wherever it has reached. */
    static Gild gild;
    /** While set, boxes draw only the faces turned away - the inside of something hollow. */
    static boolean insideOut;
    /**
     * While set, every face drawn gets the enchantment glint over it - the game's own glint
     * texture, scrolled, tinted - as strongly as this says for the face's middle.
     */
    static Glint glintHook;

    interface Glint {
        /** How strongly the face whose middle is (x, y, z) glints, 0..1, and where the texture has scrolled to. */
        float alpha(float x, float y, float z);

        int[] scroll();
    }

    /**
     * What is laid over a texel of a face whose middle is (x, y, z) - the Crown's gold, the Dye's
     * stone and paint: 0 for nothing, else a colour whose alpha byte is how strongly the face's own
     * texture shows through it as a relief (gold 34%, stone less, so a pink cut does not tint it).
     * (sx, sy) is where it is on screen, for sweeps.
     */
    interface Gild {
        int colour(float x, float y, float z, int cell, long now, float sx, float sy);
    }
    /** The real clock, for the few scene parts that need it while the scene's own is stopped. */
    static long realT;
    /** At most this many texels a side are coloured by the gild (the Seraph's pillars use 8: they are many blocks). */
    static int gildCells = 16;

    /**
     * Lights for this frame - {x, y, z, radius, rgb, strength} - set by each ending before the scene
     * is drawn: every face facing one near it is tinted towards its colour, so the heart's glow
     * lights the halves round it and the venom's lights the board. Tints can only darken a
     * texture, so a light draws a face towards its colour rather than brightening it past itself.
     */
    static final float[][] LIGHTS = new float[6][6];
    static int lightCount;

    static void light(float x, float y, float z, float radius, int rgb, float strength) {
        if (strength <= 0.01f || lightCount >= LIGHTS.length) return;
        float[] l = LIGHTS[lightCount++];
        l[0] = x; l[1] = y; l[2] = z; l[3] = radius; l[4] = Float.intBitsToFloat(rgb); l[5] = strength;
    }
    static float faceAlpha = 1f, sceneAlpha = 1f, inkWidth = 0f, yShift = 0f;
    static final float[][] INK = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-0.71f, -0.71f}, {0.71f, -0.71f}, {-0.71f, 0.71f}, {0.71f, 0.71f}};

    /**
     * The drop at the showcase, by what it is worth (the review, A4): a 26M Chimera was drawn the size of a 1M Brain
     * Food. Cheap drops 1x, the expensive ones 1.25x, the Dye 1.45x. Set by draw() from the timeline; the item only
     * shows after the split, so nothing before it changes.
     */
    public static float showScale = 1f;
    /** Where the drop was last drawn and how big - the rays come out of it (ChopFill.rays, a frame behind). */
    static float itemX = Float.NaN, itemY, itemSize;

    /** The drop, drawn - and remembered, so its light comes from the item itself, not the block. */
    static void showItem(Canvas c, float x, float y, float size, float alpha, float yaw) {
        itemX = x;
        itemY = y;
        itemSize = size;
        c.item(x, y, size, alpha, yaw);
    }
    /** "Calmer effects" (the menu): no screen shake and softer flashes, in every animation. Set by the screens. */
    public static boolean calm;

    /** The screen's height this frame, for keeping a showcase on it. */
    static int screenH = 360;

    public static void draw(Canvas c, ChopTimeline tl, long t, int w, int h) {
        screenH = h;
        showScale = tl.showScale();
        // Before the drop is shown there is no item to shine from: forget the last cutscene's.
        if (t < tl.arrive() - 400) itemX = Float.NaN;
        float master = tl.master(t);
        // The game behind, dimmed - as the other cutscenes - with the corners a little darker.
        float dim = tl.dim(t);
        c.fill(0, 0, w, h, alpha(dim) | 0x05040A);
        if (master <= 0.01f) return;
        sceneAlpha = master;
        camera(tl, t, w, h);
        c.sprite(ChopArt.TEX_GLOW, w / 2f, h * 0.55f, w * 1.1f, h * 1.3f, 0f, alpha(0.35f * master) | 0x2A1838);

        if (tl.unmade()) sky(tl, t, w, h, c);

        // The block, the head and the cleaver run on the scene's own clock, which the Dye stops.
        long ts = tl.sceneTime(t);
        realT = t;
        lightCount = 0;
        // A win's light reaching the screen's edges - behind everything (ChopFill).
        ChopFill.rays(tl, t, ts, w, h, c);
        if (t >= ChopTimeline.SPLIT) {
            // What pours out of the split lights what is round it.
            light(0f, 3f, 0f, 11f, tl.crackColour(ts), 0.6f * tl.splitLight(ts) * tl.saturation(t));
            ChopSpecials.lights(tl, t);
        }
        float[] fall = tl.blockFall(t);
        yShift = fall[0];
        // The 3D scene is never faded in: a box at part alpha shows its own faces through it, and the board came in
        // see-through. It pops in solid instead, a little small, with a puff of dust (the review, D2).
        float solid = 1f - ease01((t - (tl.total() - 400)) / 400f);
        blockPop = 0.85f + 0.15f * backOut01(ts / 120f);
        sceneAlpha = solid * fall[1];
        if (sceneAlpha > 0.01f) {
            ChopSpecials.beforeBlock(tl, t, c);
            gild = ChopSpecials.gild(tl, t);
            glintHook = ChopSpecials.glint(tl, t);
            block(c);
            gild = null;
            ChopSpecials.onBlock(tl, t, c);
            splitGlowBehind(tl, ts, c);
            // Chips behind the head go before it, the rest after the blade.
            gild = ChopSpecials.gild(tl, t);
            chips(tl, ts, false, c);
            head(tl, ts, c);
            gild = null;
            glintHook = null;
            cracks(tl, ts, c);
            smear(tl, ts, c);
            cleaver(tl, ts, c);
            gild = ChopSpecials.gild(tl, t);
            chips(tl, ts, true, c);
            gild = null;
            crumble(tl, ts, c);
        }
        yShift = 0f;
        sceneAlpha = master;
        // Debris out to the screen's edges on every blow, and a bust's ash (ChopFill).
        ChopFill.debris(tl, ts, w, h, c);
        ChopFill.dust(tl, ts, c);
        ChopFill.ash(tl, ts, w, h, c);

        // The motes rise behind the drop and its words: over them they crossed the text (the user, 0.3.0).
        ChopFill.motes(tl, t, w, h, c);
        if (tl.unmade()) relic(tl, t, c);
        else if (tl.thinks()) brain(tl, t, c);
        else if (tl.ending() != null) ChopSpecials.draw(tl, t, w, h, c);
        else drop(tl, t, c);

        // A flash is a burst of light where it happens - never a white fill over the whole screen,
        // which over the dimmed game reads as grey fog (the user's screenshots, twice).
        float flash = tl.flash(t);
        if (flash > 0.004f) {
            float[] at = tl.flashAt(t);
            project(at[0], at[1], at[2]);
            glow(c, P[0], P[1], 38f * size, 0xFFFFFF, Math.min(1f, flash * 3.5f));
        }
        sceneAlpha = 1f;
    }

    // ================================================================== camera

    static void camera(ChopTimeline tl, long t, int w, int h) {
        double yaw = Math.toRadians(tl.yaw(t)), pitch = Math.toRadians(tl.pitch(t));
        cy = (float) Math.cos(yaw); sy = (float) Math.sin(yaw);
        cp = (float) Math.cos(pitch); sp = (float) Math.sin(pitch);
        // About 30 texels - block, head and the raised cleaver - fill a little over half the height.
        size = h * 0.7f / 30f * tl.zoom(t) * tl.scale();
        float[] f = tl.focus(t);
        fx = f[0]; fy = f[1]; fz = f[2];
        float[] shake = calm ? new float[]{0f, 0f} : tl.shake(t);
        sx0 = w / 2f + shake[0];
        sy0 = h * 0.5f + shake[1];
    }

    static void project(float x, float y, float z) {
        float wx = x - fx, wy = y + yShift - fy, wz = z - fz;
        float rx = wx * cy + wz * sy;
        float rz = -wx * sy + wz * cy;
        P[0] = sx0 + rx * size;
        P[1] = sy0 - (wy * cp + rz * sp) * size;
    }

    /** Larger is nearer. */
    static float depth(float x, float y, float z) {
        float rz = -(x - fx) * sy + (z - fz) * cy;
        return -(y - fy) * sp + rz * cp;
    }

    // ================================================================== boxes

    /**
     * A box turned by three angles (radians; X, then Y, then Z), its faces {+X, -X, +Y, -Y, +Z, -Z}
     * each a rect of its own texture (null skips the face), only those facing the camera drawn.
     * Lit by a key light and pushed towards white by {@code glow}; with {@link #inkWidth} set it
     * draws only a black silhouette, stamped round, for the outline.
     */
    static void box(float cxw, float cyw, float czw, float ax, float ay, float az,
                            float hx, float hy, float hz, String[] texes, int[][] rects,
                            float r, float g, float b, float glow, float alpha, Canvas c) {
        float ca = (float) Math.cos(ax), sa = (float) Math.sin(ax);
        float cb = (float) Math.cos(ay), sb = (float) Math.sin(ay);
        float cc = (float) Math.cos(az), sc = (float) Math.sin(az);
        float[] X = {cb * cc, cb * sc, -sb};
        float[] Y = {sa * sb * cc - ca * sc, sa * sb * sc + ca * cc, sa * cb};
        float[] Z = {ca * sb * cc + sa * sc, ca * sb * sc - sa * cc, ca * cb};
        boxAxes(cxw, cyw, czw, X, Y, Z, hx, hy, hz, texes, rects, r, g, b, glow, alpha, c);
    }

    /**
     * {@link #box} with the box's own axes given outright - three unit vectors, right-handed - for
     * things placed by direction rather than by angles (a tail segment along its spine).
     */
    static void boxAxes(float cxw, float cyw, float czw, float[] X, float[] Y, float[] Z,
                        float hx, float hy, float hz, String[] texes, int[][] rects,
                        float r, float g, float b, float glow, float alpha, Canvas c) {
        float[][] axes = {X, Y, Z};
        float[] half = {hx, hy, hz};
        float before = faceAlpha;
        faceAlpha = alpha;
        for (int axis = 0; axis < 3; axis++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                int fi = axis * 2 + (sign > 0 ? 0 : 1);
                if (rects[fi] == null) continue;
                float[] n = axes[axis];
                float nx = n[0] * sign, ny = n[1] * sign, nz = n[2] * sign;
                float facing = -ny * sp + (-nx * sy + nz * cy) * cp;
                if (insideOut ? facing >= -0.02f : facing <= 0.02f) continue;
                int ua = axis == 1 ? 0 : (axis == 0 ? 2 : 0), va = axis == 1 ? 2 : 1;
                float[] U = axes[ua], V = axes[va];
                float uh = half[ua], vh = half[va];
                // Faces run their texture along +U seen from outside; the -X and -Z faces are
                // seen from the other side, so their U runs the other way.
                float us = (axis == 0 && sign > 0) || (axis == 2 && sign < 0) ? -1f : 1f;
                float fx0 = cxw + nx * half[axis], fy0 = cyw + ny * half[axis], fz0 = czw + nz * half[axis];
                float vsgn = axis == 1 ? 1f : -1f;
                float ox = fx0 - U[0] * uh * us - V[0] * vh * vsgn, oy = fy0 - U[1] * uh * us - V[1] * vh * vsgn,
                        oz = fz0 - U[2] * uh * us - V[2] * vh * vsgn;
                project(ox, oy, oz);
                float px = P[0], py = P[1];
                project(ox + U[0] * 2 * uh * us, oy + U[1] * 2 * uh * us, oz + U[2] * 2 * uh * us);
                float qx = P[0] - px, qy = P[1] - py;
                project(ox + V[0] * 2 * vh * vsgn, oy + V[1] * 2 * vh * vsgn, oz + V[2] * 2 * vh * vsgn);
                float rx = P[0] - px, ry = P[1] - py;
                int[] rect = rects[fi];
                if (inkWidth > 0f) {
                    for (float[] o : INK) emit(c, texes[fi], px + o[0] * inkWidth, py + o[1] * inkWidth, qx, qy, rx, ry, rect, 0xFF000000);
                    continue;
                }
                float lambert = Math.max(0f, nx * -0.4f + ny * 0.8f + nz * 0.45f);
                float k = 0.55f + 0.45f * lambert;
                float fr = r * k + (1f - r * k) * glow, fg = g * k + (1f - g * k) * glow, fb = b * k + (1f - b * k) * glow;
                if (lightCount > 0) {
                    // The lights near this face, and facing it, draw it towards their colour.
                    float wsum = 0f, lr = 0f, lg = 0f, lb = 0f;
                    for (int li = 0; li < lightCount; li++) {
                        float[] l = LIGHTS[li];
                        float dx = l[0] - fx0, dy = l[1] - fy0, dz = l[2] - fz0, d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (d >= l[3]) continue;
                        float fall = (1f - d / l[3]);
                        float face = d < 1e-3f ? 1f : Math.max(0f, 0.25f + 0.75f * (nx * dx + ny * dy + nz * dz) / d);
                        float wgt = fall * fall * face * l[5];
                        int rgb = Float.floatToRawIntBits(l[4]);
                        wsum += wgt;
                        lr += wgt * (rgb >> 16 & 255) / 255f; lg += wgt * (rgb >> 8 & 255) / 255f; lb += wgt * (rgb & 255) / 255f;
                    }
                    if (wsum > 0.005f) {
                        float a = Math.min(0.5f, wsum);
                        fr += (lr / wsum - fr) * a; fg += (lg / wsum - fg) * a; fb += (lb / wsum - fb) * a;
                    }
                }
                emit(c, texes[fi], px, py, qx, qy, rx, ry, rect, 0xFF000000 | channel(fr) << 16 | channel(fg) << 8 | channel(fb));
                if (glintHook != null) {
                    float ga = glintHook.alpha(ox + (U[0] * uh * us + V[0] * vh * vsgn), oy + (U[1] * uh * us + V[1] * vh * vsgn),
                            oz + (U[2] * uh * us + V[2] * vh * vsgn));
                    if (ga > 0.01f) {
                        int[] sc = glintHook.scroll();
                        emit(c, ChopArt.TEX_GLINT, px, py, qx, qy, rx, ry, new int[]{sc[0], sc[1], 32, 32},
                                alpha(ga) | ChopTimeline.ENCHANT_PINK);
                    }
                }
                if (gild != null) {
                    float wu = 2 * uh * us, wv = 2 * vh * vsgn;
                    ChopSpecials.gildFace(gild, texes[fi], rect, ox, oy, oz, U[0] * wu, U[1] * wu, U[2] * wu, V[0] * wv, V[1] * wv, V[2] * wv,
                            px, py, qx, qy, rx, ry, Math.min(gildCells, rect[2]), Math.min(gildCells, rect[3]), fi, k, c);
                }
            }
        }
        faceAlpha = before;
    }

    static void emit(Canvas c, String tex, float ox, float oy, float ux, float uy, float vx, float vy, int[] rect, int tint) {
        float fade = faceAlpha * sceneAlpha;
        if (fade < 1f) {
            if (fade <= 0.01f) return;
            tint = (tint & 0xFFFFFF) | Math.round(fade * (tint >>> 24)) << 24;
        }
        if (ux * vy - uy * vx < 0f) {
            ox += ux; oy += uy;
            ux = -ux; uy = -uy;
        }
        c.quad(tex, ox, oy, ux, uy, vx, vy, rect[0], rect[1], rect[2], rect[3], tint);
    }

    /**
     * A flat textured quad in the world - corner and two edges - projected and drawn, from either
     * side. Decals on the board, wings, anything that is one plane.
     */
    static void worldQuad(String tex, int[] rect, float ox, float oy, float oz, float ux, float uy, float uz,
                          float vx, float vy, float vz, int tint, Canvas c) {
        project(ox, oy, oz);
        float px = P[0], py = P[1];
        project(ox + ux, oy + uy, oz + uz);
        float qx = P[0] - px, qy = P[1] - py;
        project(ox + vx, oy + vy, oz + vz);
        float rx = P[0] - px, ry = P[1] - py;
        if (inkWidth > 0f) {
            for (float[] o : INK) emit(c, tex, px + o[0] * inkWidth, py + o[1] * inkWidth, qx, qy, rx, ry, rect, 0xFF000000);
            return;
        }
        emit(c, tex, px, py, qx, qy, rx, ry, rect, tint);
    }

    static String[] all(String tex) {
        return new String[]{tex, tex, tex, tex, tex, tex};
    }

    // ================================================================== the block and the head

    static void block(Canvas c) {
        // A board, not a stump: only the top rows of the bark strip down its sides.
        int[] top = ChopArt.BLOCK_TOP, side = {ChopArt.BLOCK_SIDE[0], ChopArt.BLOCK_SIDE[1], 16, Math.max(2, Math.round(16 * ChopTimeline.BLOCK_H / 18f))};
        float hy = ChopTimeline.BLOCK_H / 2f * blockPop;
        box(0f, -hy, 0f, 0f, 0f, 0f, ChopTimeline.BLOCK_W * blockPop, hy, ChopTimeline.BLOCK_D * blockPop, all(SHEET),
                new int[][]{side, side, top, side, side, side}, 0.95f, 0.9f, 0.88f, 0f, 1f, c);
    }

    /** The skin rects of a head, whole: {+X, -X, +Y, -Y, +Z, -Z} = left, right, top, bottom, front, back. */
    static final int[][] HEAD = {{16, 8, 8, 8}, {0, 8, 8, 8}, {8, 0, 8, 8}, {16, 0, 8, 8}, {8, 8, 8, 8}, {24, 8, 8, 8}};
    static final int[][] HAT = {{48, 8, 8, 8}, {32, 8, 8, 8}, {40, 0, 8, 8}, {48, 0, 8, 8}, {40, 8, 8, 8}, {56, 8, 8, 8}};

    /** The head: whole until the split, then two halves falling open about their outer bottom edges. */
    static void head(ChopTimeline tl, long t, Canvas c) {
        String skin = c.creatureSkin(tl.creature());
        float drop = tl.headDrop(t);
        float H = ChopTimeline.HEAD;
        if (t < ChopTimeline.SPLIT) {
            wholeHead(skin, 0f, H + drop, 0f, H, 0f, 1f, c);
            return;
        }
        float[] hv = tl.halves(t);
        if (hv[1] <= 0.01f) return;
        float th = hv[0];
        float[] blow = tl.halfBlow(t);
        if (blow[3] <= 0.01f) return;
        // Left half: pivots on (-4, 0); right half on (4, 0). Far one first.
        float lcx = -H + (H / 2f) * (float) Math.cos(th) - H * (float) Math.sin(th), lcy = (H / 2f) * (float) Math.sin(th) + H * (float) Math.cos(th);
        float rcx = H - (H / 2f) * (float) Math.cos(th) + H * (float) Math.sin(th), rcy = lcy;
        lcx -= blow[0];
        rcx += blow[0];
        lcy += blow[1];
        rcy += blow[1];
        th += blow[2];
        boolean leftFirst = depth(lcx, lcy, 0f) < depth(rcx, rcy, 0f);
        for (int pass = 0; pass < 2; pass++) {
            boolean left = (pass == 0) == leftFirst;
            if (left) halfHead(skin, lcx, lcy, th, true, hv[1] * blow[3], tl, t, c);
            else halfHead(skin, rcx, rcy, -th, false, hv[1] * blow[3], tl, t, c);
        }
    }

    static void wholeHead(String skin, float x, float y, float z, float half, float az, float alpha, Canvas c) {
        if (skin == null) {
            box(x, y, z, 0f, 0f, az, half, half, half, all(SHEET), new int[][]{ChopArt.FLESH, ChopArt.FLESH, ChopArt.FLESH, ChopArt.FLESH, ChopArt.FLESH, ChopArt.FLESH},
                    0.6f, 0.5f, 0.6f, 0f, alpha, c);
            return;
        }
        inkWidth = Math.max(1.5f, size / 3f);
        box(x, y, z, 0f, 0f, az, half * 1.0625f, half * 1.0625f, half * 1.0625f, all(skin), HEAD, 1f, 1f, 1f, 0f, alpha, c);
        inkWidth = 0f;
        box(x, y, z, 0f, 0f, az, half, half, half, all(skin), HEAD, 1f, 1f, 1f, 0f, alpha, c);
        box(x, y, z, 0f, 0f, az, half * 1.0625f, half * 1.0625f, half * 1.0625f, all(skin), HAT, 1f, 1f, 1f, 0f, alpha, c);
    }

    /** Half the head: its outer skin, and the cut face - flesh, or a brain for the Sphinx's - lit by the split. */
    static void halfHead(String skin, float x, float y, float az, boolean left, float alpha, ChopTimeline tl, long t, Canvas c) {
        float H = ChopTimeline.HEAD;
        String sk = skin == null ? SHEET : skin;
        int[] cut = tl.thinks() ? ChopArt.BRAIN_CUT : ChopArt.FLESH;
        String[] tex = left ? new String[]{SHEET, sk, sk, sk, sk, sk} : new String[]{sk, SHEET, sk, sk, sk, sk};
        int[][] rects = left
                ? new int[][]{cut, skin == null ? cut : HEAD[1], half(HEAD[2], 0), half(HEAD[3], 0), half(HEAD[4], 0), half(HEAD[5], 1)}
                : new int[][]{skin == null ? cut : HEAD[0], cut, half(HEAD[2], 1), half(HEAD[3], 1), half(HEAD[4], 1), half(HEAD[5], 0)};
        // The hat layer: half a texel proud on the outside, none on the cut.
        float out = left ? -1f : 1f, hp = H / 16f;
        float hx = x + out * hp / 2f * (float) Math.cos(az), hy = y + out * hp / 2f * (float) Math.sin(az);
        int[][] hat = skin == null ? null : left
                ? new int[][]{null, HAT[1], half(HAT[2], 0), half(HAT[3], 0), half(HAT[4], 0), half(HAT[5], 1)}
                : new int[][]{HAT[0], null, half(HAT[2], 1), half(HAT[3], 1), half(HAT[4], 1), half(HAT[5], 0)};
        inkWidth = Math.max(1.5f, size / 3f);
        box(hx, hy, 0f, 0f, 0f, az, H / 2f + hp / 2f, H + hp, H + hp, tex, rects, 1f, 1f, 1f, 0f, alpha, c);
        inkWidth = 0f;
        int light = tl.crackColour(t);
        float lit = tl.splitLight(t) * tl.saturation(realT);
        box(x, y, 0f, 0f, 0f, az, H / 2f, H, H, tex, rects, 1f, 1f, 1f, 0.15f * lit, alpha, c);
        if (hat != null) box(hx, hy, 0f, 0f, 0f, az, H / 2f + hp / 2f, H + hp, H + hp, all(skin), hat, 1f, 1f, 1f, 0f, alpha, c);
        if (lit > 0.02f) {
            // The cut face glows with what is inside.
            project(x + (left ? H / 2f : -H / 2f) * (float) Math.cos(az), y, 0f);
            glow(c, P[0], P[1], 5f * size, light, 0.45f * lit * alpha);
        }
    }

    /** One half (0 left, 1 right) of an 8-wide skin rect. */
    static int[] half(int[] rect, int which) {
        return new int[]{rect[0] + which * rect[2] / 2, rect[1], rect[2] / 2, rect[3]};
    }

    /** The cut face showing through, light pouring up out of the split, behind the halves. */
    static void splitGlowBehind(ChopTimeline tl, long t, Canvas c) {
        float lit = tl.splitLight(t) * tl.saturation(realT);
        if (lit <= 0.01f) return;
        int col = tl.crackColour(t);
        project(0f, ChopTimeline.HEAD, 0f);
        glow(c, P[0], P[1], 22f * size, col, 0.8f * lit);
        glow(c, P[0], P[1], 9f * size, mix(col, 0xFFFFFF, 0.4f), 0.7f * lit);
        project(0f, ChopTimeline.HEAD * 3f, 0f);
        float top = P[1];
        project(0f, 0f, 0f);
        c.sprite(ChopArt.TEX_GLOW, P[0], (top + P[1]) / 2f, 7f * size, Math.abs(P[1] - top) + 14f * size, 0f, alpha(Math.min(1f, 0.9f * lit) * sceneAlpha) | col);
        c.sprite(ChopArt.TEX_GLOW, P[0], (top + P[1]) / 2f, 2.5f * size, Math.abs(P[1] - top) + 10f * size, 0f, alpha(Math.min(1f, 0.8f * lit) * sceneAlpha) | mix(col, 0xFFFFFF, 0.6f));
    }

    // ================================================================== the cleaver

    static final Mesh CLEAVER = CleaverMesh.build();
    static final float CLEAVER_SCALE = 0.42f;

    /**
     * The Butcher's cleaver, the real mesh, swinging on its pommel. Its blade lies in the plane of
     * the cut - across the head's width at {@code strikeX}, the edge running front to back along
     * the slot it opens on the top - with the handle to the back, so the halves it makes fall away
     * to either side of it, and the chips fly out off both faces of the blade.
     *
     * The blade goes into the head, not in front of it: while it is level and in, only the part of
     * each face above the head's top (or, once through, above the board) is drawn.
     */
    /** The board's size as it pops in, 0.85 to 1 (with a little overshoot) over its first 120 ms. */
    static float blockPop = 1f;

    static float backOut01(float x) {
        x = Math.max(0f, Math.min(1f, x));
        float s = 1.7f, u = x - 1f;
        return 1f + (s + 1f) * u * u * u + s * u * u;
    }

    /**
     * A smear on the down-swing: the blade where it was one and two frames ago, faint and without its outline, for
     * the last 60 ms before each blow - the first blow read as the cleaver resting on the head (the review, D3).
     */
    static void smear(ChopTimeline tl, long t, Canvas c) {
        for (int k = 0; k < 4; k++) {
            long at = ChopTimeline.blowTime(k);
            if (t < at - 60 || t >= at + 20) continue;
            for (int g = 2; g >= 1; g--) {
                long back = t - 28L * g;
                if (back < at - ChopTimeline.SWING_MS) continue;
                cleaverAt(tl, back, c, g == 1 ? 0.35f : 0.18f, false);
            }
        }
    }

    static void cleaver(ChopTimeline tl, long t, Canvas c) {
        cleaverAt(tl, t, c, 1f, true);
    }

    static void cleaverAt(ChopTimeline tl, long t, Canvas c, float alphaMul, boolean outline) {
        float[] cl = tl.cleaver(t);
        if (cl == null || cl[3] <= 0.01f) return;
        float s = CLEAVER_SCALE;
        // The pommel, placed so that level, the middle of the edge sits on the mark.
        float pz = -24f * s, py = cl[1] + 8.7f * s;
        float ca = (float) Math.cos(cl[2]), sa = (float) Math.sin(cl[2]);
        boolean level = Math.abs(cl[2]) < 0.004f;
        // Through the head only once the halves have parted enough to show it; then into the board.
        float clip = level ? (t < ChopTimeline.SPLIT + 110 ? 2f * ChopTimeline.HEAD : 0f) : Float.NEGATIVE_INFINITY;
        List<float[]> faces = new ArrayList<>();
        for (Mesh.Quad q : CLEAVER.quads()) {
            float[] o = cleaverPoint(q.ox, q.oy, q.oz, cl[0], py, pz, ca, sa, s);
            float[] u = cleaverVec(q.ux, q.uy, q.uz, ca, sa, s), v = cleaverVec(q.vx, q.vy, q.vz, ca, sa, s), n = cleaverVec(q.nx, q.ny, q.nz, ca, sa, 1f);
            float facing = -n[1] * sp + (-n[0] * sy + n[2] * cy) * cp;
            if (facing <= 0.02f) continue;
            float[] f = {0f, o[0], o[1], o[2], u[0], u[1], u[2], v[0], v[1], v[2], n[0], n[1], n[2], q.u0, q.v0, q.uw, q.vh};
            if (clip > Float.NEGATIVE_INFINITY && !clipAbove(f, clip)) continue;
            f[0] = depth(f[1] + (f[4] + f[7]) / 2f, f[2] + (f[5] + f[8]) / 2f, f[3] + (f[6] + f[9]) / 2f);
            faces.add(f);
        }
        faces.sort((a, b) -> Float.compare(a[0], b[0]));
        faceAlpha = cl[3] * alphaMul;
        for (int pass = outline ? 0 : 1; pass < 2; pass++) {
            for (float[] f : faces) {
                project(f[1], f[2], f[3]);
                float ox = P[0], oy = P[1];
                project(f[1] + f[4], f[2] + f[5], f[3] + f[6]);
                float ux = P[0] - ox, uy = P[1] - oy;
                project(f[1] + f[7], f[2] + f[8], f[3] + f[9]);
                float vx = P[0] - ox, vy = P[1] - oy;
                int[] rect = {(int) f[13], (int) f[14], (int) f[15], (int) f[16]};
                if (pass == 0) {
                    float ink = Math.max(1.2f, size / 5f);
                    for (float[] o : INK) emit(c, PropArt.TEX_CLEAVER, ox + o[0] * ink, oy + o[1] * ink, ux, uy, vx, vy, rect, 0xFF000000);
                } else {
                    float lambert = Math.max(0f, f[10] * -0.4f + f[11] * 0.8f + f[12] * 0.45f);
                    float k = 0.6f + 0.4f * lambert;
                    emit(c, PropArt.TEX_CLEAVER, ox, oy, ux, uy, vx, vy, rect, 0xFF000000 | channel(k) << 16 | channel(k) << 8 | channel(k));
                }
            }
        }
        faceAlpha = 1f;
    }

    /**
     * Cuts a face down to the part above {@code y}, texture and all; false if none is left. Only
     * for a level blade, where every face has one edge flat, so the cut is parallel to it and what
     * is left is still a parallelogram. f = {depth, o, u, v, n, u0, v0, uw, vh}.
     */
    static boolean clipAbove(float[] f, float y) {
        float oy = f[2], uy = f[5], vy = f[8];
        boolean uFlat = Math.abs(uy) < 1e-3f, vFlat = Math.abs(vy) < 1e-3f;
        if (uFlat && vFlat) return oy >= y;
        // The edge that climbs, and which texture axis it is.
        boolean alongV = uFlat;
        float ey = alongV ? vy : uy;
        int e = alongV ? 7 : 4, t0 = alongV ? 14 : 13, tl = alongV ? 16 : 15;
        float a = (y - oy) / ey;
        float lo = ey > 0f ? Math.max(0f, a) : 0f, hi = ey > 0f ? 1f : Math.min(1f, a);
        if (hi - lo <= 0.02f) return false;
        if (lo <= 0f && hi >= 1f) return true;
        float len = f[tl];
        int skip = Math.round(lo * len), keep = Math.max(1, Math.round((hi - lo) * len));
        f[1] += f[e] * lo; f[2] += f[e + 1] * lo; f[3] += f[e + 2] * lo;
        f[e] *= hi - lo; f[e + 1] *= hi - lo; f[e + 2] *= hi - lo;
        f[t0] += skip;
        f[tl] = keep;
        return true;
    }

    /**
     * Mesh space to world. The mesh runs from its pommel out along +X, edge down, thin along Z;
     * here that length runs front to back (+Z, from the pommel behind the head), the thickness
     * across (X, centred on the mark), and the swing turns it about the X axis through the pommel.
     */
    static float[] cleaverPoint(float x, float y, float z, float mx, float py, float pz, float ca, float sa, float s) {
        float[] v = cleaverVec(x, y, z, ca, sa, s);
        return new float[]{mx + v[0], py + v[1], pz + v[2]};
    }

    static float[] cleaverVec(float x, float y, float z, float ca, float sa, float s) {
        // In the swing's plane: a along the blade, b up; a negative angle raises the tip.
        float a = x * s, b = y * s;
        float ra = a * ca + b * sa, rb = -a * sa + b * ca;
        return new float[]{z * s, rb, ra};
    }

    // ================================================================== cracks, chips, crumble

    /**
     * The cracks: from each blow's mark down the head's front and across its top, dark, with the
     * light from inside leaking through them - warm, then the cycle, then the truth.
     */
    static void cracks(ChopTimeline tl, long t, Canvas c) {
        int n = tl.blows(t);
        if (n < 0 || t >= ChopTimeline.SPLIT) return;
        float H = ChopTimeline.HEAD;
        int col = tl.crackColour(t);
        float light = tl.crackLight(t);
        java.util.Random r = new java.util.Random(tl.seed() * 7 + 3);
        for (int k = 0; k <= Math.min(n, 2); k++) {
            float grown = Math.min(1f, (t - ChopTimeline.blowTime(k)) / 120f);
            float len = (0.35f + 0.25f * k + 0.2f * Math.min(n - k, 2)) * grown;
            float x = tl.strikeX(k), y = 2f * H;
            float px0, py0;
            project(x, y, H + 0.02f);
            px0 = P[0];
            py0 = P[1];
            int steps = 6;
            for (int i = 1; i <= steps; i++) {
                float f = i / (float) steps;
                if (f > len) break;
                x += (r.nextFloat() - 0.5f) * 1.3f;
                x = Math.max(-H + 0.3f, Math.min(H - 0.3f, x));
                y = 2f * H - f * 2f * H * 0.95f;
                project(x, y, H + 0.02f);
                c.line(px0, py0, P[0], P[1], Math.max(2.5f, size * 0.55f), alpha(light * 0.5f * sceneAlpha) | col);
                c.line(px0, py0, P[0], P[1], Math.max(1f, size * 0.18f), alpha(0.95f * sceneAlpha) | 0x140810);
                c.line(px0, py0, P[0], P[1], Math.max(0.8f, size * 0.09f), alpha(Math.min(1f, light) * sceneAlpha) | mix(col, 0xFFFFFF, 0.4f));
                px0 = P[0];
                py0 = P[1];
            }
            // The slot the blade cut across the top.
            project(tl.strikeX(k), 2f * H + 0.02f, H);
            float ax = P[0], ay = P[1];
            project(tl.strikeX(k), 2f * H + 0.02f, -H * (0.3f + 0.7f * grown));
            c.line(ax, ay, P[0], P[1], Math.max(1.2f, size * 0.25f), alpha(0.9f * sceneAlpha) | 0x140810);
            c.line(ax, ay, P[0], P[1], Math.max(0.8f, size * 0.1f), alpha(light * sceneAlpha) | col);
        }
        project(0f, 2f * H, 0f);
        glow(c, P[0], P[1], 6f * size, col, 0.35f * light);
    }

    /**
     * Chips of the head's own skin, burst out of the slot each blow opens: off both faces of the
     * blade (along X), up, and down onto the board, where they come to rest and fade - or off its
     * edge. Drawn in two passes, those behind the head before it and the rest after the blade.
     */
    static void chips(ChopTimeline tl, long t, boolean front, Canvas c) {
        String skin = c.creatureSkin(tl.creature());
        float H = ChopTimeline.HEAD;
        float headDepth = depth(0f, H, 0f);
        for (int k = 0; k < 4; k++) {
            long at = ChopTimeline.blowTime(k);
            float sec = (t - at) / 1000f;
            boolean painted = false;
            if (sec < 0f || (sec > 1.1f && !painted)) continue;
            int n = k == 3 ? 20 : 8 + 2 * k;
            float power = 0.8f + 0.15f * k;
            java.util.Random r = new java.util.Random(tl.seed() * 13 + k);
            for (int i = 0; i < n; i++) {
                float side = i % 2 == 0 ? -1f : 1f;
                float vx = side * (9f + r.nextFloat() * 8f) * power, vy = (7f + r.nextFloat() * 7f) * power, vz = (r.nextFloat() - 0.5f) * 5f;
                float z0 = (r.nextFloat() - 0.5f) * 1.8f * H;
                float x = tl.strikeX(k) + vx * sec, z = z0 + vz * sec;
                float y = 2f * H + 0.3f + vy * sec - 38f * sec * sec;
                // Over the head they skim its top; past it, the board catches them.
                // (After the split there is no head top to land on - the halves have fallen open.)
                boolean onHead = k < 3 && Math.abs(x) < H && Math.abs(z) < H;
                boolean onBoard = Math.abs(x) < ChopTimeline.BLOCK_W && Math.abs(z) < ChopTimeline.BLOCK_D;
                float cs = 0.45f + r.nextFloat() * 0.35f;
                float floor = onHead ? 2f * H + cs : onBoard ? cs : -40f;
                float spin = sec * (7f + i % 5);
                if (y <= floor) {
                    y = floor;
                    spin = 0.6f * (7f + i % 5) * Math.min(sec, 0.35f);
                }
                if ((depth(x, y, z) > headDepth || y > 2f * H) != front) continue;
                if (painted) {
                    project(x, y, z);
                    int paint = ChopTimeline.OLIVE;
                    if (y <= floor && floor > -40f) {
                        // A splat of pixel paint on the board, flattened by the angle it is seen at.
                        float sw = cs * 3.4f * size;
                        c.sprite(ChopArt.TEX_PIXSPLAT, P[0], P[1], sw, sw * Math.max(0.25f, Math.abs(sp)), 0f, alpha(0.95f * sceneAlpha) | paint);
                    } else {
                        float dw = cs * 2.1f * size;
                        c.sprite(ChopArt.TEX_PIXDROP, P[0], P[1], dw, dw * 1.25f, 0f, alpha(sceneAlpha) | paint);
                    }
                    continue;
                }
                float fade = 1f - ease01((sec - 0.7f) / 0.4f);
                int[] rect = skin == null ? ChopArt.FLESH : new int[]{8 + r.nextInt(7), r.nextBoolean() ? r.nextInt(7) : 8 + r.nextInt(7), 1, 1};
                String tex = skin == null ? SHEET : skin;
                box(x, y, z, spin, spin * 0.7f, spin * 0.4f, cs, cs, cs, all(tex), new int[][]{rect, rect, rect, rect, rect, rect},
                        1f, 1f, 1f, 0f, fade, c);
            }
        }
    }

    /** A bust: the halves crumble into pixel-sized cubes that fall off the block and fade. */
    static void crumble(ChopTimeline tl, long t, Canvas c) {
        float u = tl.crumble(t);
        if (u < 0f) return;
        String skin = c.creatureSkin(tl.creature());
        java.util.Random r = new java.util.Random(tl.seed() * 17 + 9);
        for (int i = 0; i < 40; i++) {
            float side = i % 2 == 0 ? -1f : 1f;
            float x0 = side * (3f + r.nextFloat() * 5f), y0 = r.nextFloat() * 3f, z0 = (r.nextFloat() - 0.5f) * 7f;
            float vx = side * (1f + r.nextFloat() * 5f), vz = (r.nextFloat() - 0.5f) * 4f;
            float sec = u * 1.1f;
            float x = x0 + vx * sec, z = z0 + vz * sec;
            float floor = Math.abs(x) < ChopTimeline.BLOCK_W && Math.abs(z) < ChopTimeline.BLOCK_D ? 0.5f : -40f;
            float y = Math.max(floor, y0 + 4f * sec - 26f * sec * sec);
            int[] rect = skin == null ? ChopArt.FLESH : new int[]{r.nextInt(30), 8 + r.nextInt(6), 2, 2};
            String tex = skin == null ? SHEET : skin;
            float cs = 0.4f + r.nextFloat() * 0.3f;
            box(x, y, z, sec * 3f, sec * 2f, 0f, cs, cs, cs, all(tex), new int[][]{rect, rect, rect, rect, rect, rect},
                    0.9f, 0.9f, 0.9f, 0f, 1f - ease01((u - 0.5f) / 0.5f), c);
        }
    }

    // ================================================================== a normal win

    static void drop(ChopTimeline tl, long t, Canvas c) {
        float ia = tl.itemAlpha(t);
        if (ia <= 0.01f) return;
        int truth = tl.truth(t);
        float rise = tl.itemRise(t);
        float iy3 = ChopTimeline.HEAD + ChopTimeline.ITEM_TOP * rise;
        project(0f, iy3, 0f);
        float ix = P[0], iy = P[1];
        float rays = tl.rays(t);
        if (rays > 0.01f) {
            float spin = (t - ChopTimeline.ARRIVE) / 40f;
            c.sprite(ChopArt.TEX_RAYS, ix, iy, 26f * size * rays, 26f * size * rays, spin, alpha(0.6f * rays * sceneAlpha) | truth);
            c.sprite(ChopArt.TEX_RAYS, ix, iy, 16f * size * rays, 16f * size * rays, -spin * 1.6f, alpha(0.35f * rays * sceneAlpha) | 0xFFFFFF);
        }
        glow(c, ix, iy, 14f * size, truth, 0.75f * ia);
        float burst = tl.arrivalBurst(t);
        if (burst > 0.01f) glow(c, ix, iy, 26f * size * (1.3f - 0.3f * burst), 0xFFFFFF, 0.8f * burst);
        glow(c, ix, iy, 6f * size, mix(truth, 0xFFFFFF, 0.6f), 0.85f * ia);
        float itemSize = Math.max(24f, 8f * size) * ChopScene.showScale;
        ChopScene.showItem(c, ix, iy, itemSize, ia * sceneAlpha, spinOf(tl, t));
        float na = tl.nameAlpha(t) * sceneAlpha;
        float scale = textScale();
        if (na > 0.02f) c.name(ix, iy + itemSize * 0.8f + 6f * scale, scale * 1.5f, alpha(na) | mix(truth, 0xFFFFFF, 0.45f), Integer.MAX_VALUE);
        price(tl, t, ix, iy + itemSize * 0.8f + 26f * scale, scale, c);
    }

    static float spinOf(ChopTimeline tl, long t) {
        float since = (t - tl.arrive()) / 1000f;
        if (since < 0f) return 32f + since * 260f;
        return 32f + since * 40f;
    }

    static float textScale() {
        return Math.max(1f, Math.min(1.35f, size / 5f));
    }

    /** The drop's price counting up under it: bazaar instant sell or lowest BIN, and where it came from. */
    static void price(ChopTimeline tl, long t, float cx, float y, float scale, Canvas c) {
        float p = tl.count(t);
        if (p < 0f || tl.price() < 0) return;
        long shown = Math.round(tl.price() * (double) p);
        long landed = tl.countStart() + ChopTimeline.COUNT_MS;
        float kick = t >= landed ? 1f + 0.18f * (float) Math.exp(-(t - landed) / 120.0) : 1f;
        c.text(String.format("%,d coins", shown), cx, y, scale * 1.5f * kick, alpha(sceneAlpha) | 0xFFD24A);
        float la = Math.min(1f, (t - tl.countStart()) / 300f) * sceneAlpha;
        c.text(tl.priceLabel(), cx, y + 13f * scale, scale * 0.8f, alpha(la) | 0x9FB3C8);
    }

    /** The name, stamped letter by letter, and a special ending's two lines, and the price, under (cx, top). */
    static void specialText(ChopTimeline tl, long t, float cx, float top, int nameColour, int lineColour, Canvas c) {
        float scale = textScale();
        int letters = tl.letters(t, Integer.MAX_VALUE);
        String banner = tl.banner();
        if (banner != null) {
            // Room for the banner between the drop and its name: everything below moves down.
            top += 22f * scale;
            float[] stamp = tl.bannerStamp(t);
            if (stamp != null) {
                float jolt = (stamp[0] - 1f) * 3f;
                // Clear of the drop above and of the name below (the user: a little higher).
                c.text("\u00a7l" + banner, cx + jolt * (float) Math.sin(t * 0.9), top - 17f * scale, scale * 1.2f * stamp[0],
                        alpha(stamp[1] * sceneAlpha) | tl.bannerColour());
            }
        }
        if (letters > 0) c.name(cx, top, scale * 1.6f, alpha(sceneAlpha) | nameColour, letters);
        String[] lines = tl.lines();
        for (int k = 0; k < lines.length; k++) {
            float la = tl.lineAlpha(k, t) * sceneAlpha;
            if (la > 0.02f) c.text(lines[k], cx, top + (16f + k * 11f) * scale, scale, alpha(la) | (k == 0 ? lineColour : 0xFFFFFF));
        }
        price(tl, t, cx, top + (16f + lines.length * 11f + 8f) * scale, scale, c);
    }

    // ================================================================== the Minos Relic

    static final int[] R_FRONT = {8, 8, 8, 8}, R_BACK = {24, 8, 8, 8}, R_RIGHT = {0, 8, 8, 8},
            R_LEFT = {16, 8, 8, 8}, R_TOP = {8, 0, 8, 8}, R_BOTTOM = {16, 0, 8, 8};
    static final int[] H_FRONT = {40, 8, 8, 8}, H_BACK = {56, 8, 8, 8}, H_RIGHT = {32, 8, 8, 8},
            H_LEFT = {48, 8, 8, 8}, H_TOP = {40, 0, 8, 8}, H_BOTTOM = {48, 0, 8, 8};

    static void sky(ChopTimeline tl, long t, int w, int h, Canvas c) {
        float form = Math.max(0f, Math.min(1f, tl.moonForm(t)));
        if (form <= 0.001f) return;
        project(ChopTimeline.MOON_X, ChopTimeline.MOON_Y, ChopTimeline.MOON_Z);
        float mx = P[0], my = P[1], mr = Math.max(0f, tl.moonForm(t)) * ChopTimeline.MOON_SIZE * 0.55f * size;
        double wheel = (t - ChopTimeline.RELIC_CONVERGE) / 1000.0 * 0.05;
        float cw = (float) Math.cos(wheel), swl = (float) Math.sin(wheel);
        for (int i = 0; i < 70; i++) {
            float ox = (rnd(i, 11) - 0.5f) * w * 1.6f, oy = (rnd(i, 23) - 0.5f) * h * 1.6f;
            float sx = mx + ox * cw - oy * swl, sy2 = my + ox * swl + oy * cw;
            if (sx < -10 || sy2 < -10 || sx > w + 10 || sy2 > h + 10) continue;
            float tw = 0.55f + 0.45f * (float) Math.sin(t / 400.0 + i * 1.7);
            float delay = rnd(i, 51) * 0.6f;
            float a = Math.max(0f, Math.min(1f, (form - delay) / 0.4f));
            glow(c, sx, sy2, (3f + rnd(i, 37) * 5f), 0xDDE4F4, 0.8f * a * tw);
        }
        if (mr < 1f) return;
        glow(c, mx, my, mr * 7f, ChopTimeline.MOONSTONE, 0.35f * form);
        glow(c, mx, my, mr * 3.4f, ChopTimeline.MOONSTONE, 0.55f * form);
        if (tl.relicRevealed(t)) {
            float rays = Math.min(1f, (t - tl.arrive()) / 350f);
            float spin = (t - tl.arrive()) / 40f;
            c.sprite(ChopArt.TEX_RAYS, mx, my, mr * 9f * rays, mr * 9f * rays, spin, alpha(0.55f * rays * sceneAlpha) | ChopTimeline.MOONSTONE);
            c.sprite(ChopArt.TEX_RAYS, mx, my, mr * 6f * rays, mr * 6f * rays, -spin * 1.6f, alpha(0.35f * rays * sceneAlpha) | 0xFFFFFF);
        }
        orbitingStars(tl, t, form, false, c);
        moonHead(tl, Math.max(0f, tl.moonForm(t)), t, c);
        orbitingStars(tl, t, form, true, c);
    }

    static void moonHead(ChopTimeline tl, float scale, long t, Canvas c) {
        if (scale <= 0.01f) return;
        float half = ChopTimeline.MOON_SIZE * 0.5f * scale;
        float[] ang = tl.moonAngles(t);
        String relic = c.dropSkin();
        int turned = relic == null ? 0 : tl.relicFaces(t);
        int[][] relicRects = {R_FRONT, R_BACK, R_TOP, R_BOTTOM, R_RIGHT, R_LEFT};
        int[][] relicHat = {H_FRONT, H_BACK, H_TOP, H_BOTTOM, H_RIGHT, H_LEFT};
        String[] tex = new String[6], hatTex = new String[6];
        int[][] base = new int[6][], hat = new int[6][];
        for (int f = 0; f < 6; f++) {
            boolean r = (turned >> f & 1) != 0;
            tex[f] = r ? relic : SHEET;
            base[f] = r ? relicRects[f] : ChopArt.MOON_FACES[f];
            hatTex[f] = r ? relic : SHEET;
            hat[f] = r ? relicHat[f] : ChopArt.MOON_HAT;
        }
        float hh = half * (1f + 1f / 16f);
        float mx = ChopTimeline.MOON_X, my = ChopTimeline.MOON_Y, mz = ChopTimeline.MOON_Z;
        inkWidth = Math.max(1.5f, half * size / 11f);
        box(mx, my, mz, ang[0], ang[1], ang[2], half, half, half, tex, base, 1f, 1f, 1f, 0f, 1f, c);
        box(mx, my, mz, ang[0], ang[1], ang[2], hh, hh, hh, hatTex, hat, 1f, 1f, 1f, 0f, 1f, c);
        inkWidth = 0f;
        box(mx, my, mz, ang[0], ang[1], ang[2], half, half, half, tex, base, 0.92f, 0.93f, 1f, tl.relicRevealed(t) ? 0.05f : 0.08f, 1f, c);
        box(mx, my, mz, ang[0], ang[1], ang[2], hh, hh, hh, hatTex, hat, 1f, 1f, 1f, 0.05f, 1f, c);
    }

    static void orbitingStars(ChopTimeline tl, long t, float form, boolean front, Canvas c) {
        float mx = ChopTimeline.MOON_X, my = ChopTimeline.MOON_Y, mz = ChopTimeline.MOON_Z;
        float moonDepth = depth(mx, my, mz);
        float[][] rings = {{9.5f, 0.35f, 0.55f, 14}, {12.5f, -0.5f, -0.35f, 18}, {16f, 0.15f, 0.25f, 22}};
        for (int r = 0; r < rings.length; r++) {
            float rad = rings[r][0] * Math.max(0.2f, form), tiltX = rings[r][1], speed = rings[r][2];
            int n = (int) rings[r][3];
            float ct = (float) Math.cos(tiltX), st = (float) Math.sin(tiltX);
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n + t / 1000.0 * speed + r;
                float lx = (float) Math.cos(a) * rad, lz = (float) Math.sin(a) * rad;
                float wy = my + lz * st, wz = mz + lz * ct, wx = mx + lx;
                if ((depth(wx, wy, wz) > moonDepth) != front) continue;
                project(wx, wy, wz);
                float tw = 0.55f + 0.45f * (float) Math.sin(t / 180.0 + i * 1.7 + r);
                float appear = Math.max(0f, Math.min(1f, form * 1.6f - i / (float) n * 0.6f));
                int col = (i + r) % 5 == 0 ? 0xFFE68A : ((i + r) % 4 == 0 ? 0x7CF0C6 : 0xE6ECFF);
                glow(c, P[0], P[1], (1.4f + 0.7f * tw) * size, col, 0.9f * appear * tw);
                glow(c, P[0], P[1], 0.5f * size, 0xFFFFFF, appear);
            }
        }
    }

    /**
     * The Relic's ending over the block: the head throbbing, then coming apart into chunks - each a
     * 2 x 2 x 2 piece of the real skin - flying into the vortex; the moon igniting where they meet.
     */
    static void relic(ChopTimeline tl, long t, Canvas c) {
        String skin = c.creatureSkin(tl.creature());
        float throb = tl.throbRelic(t);
        if (throb > 0.01f && t < ChopTimeline.RELIC_PEEL) {
            project(0f, ChopTimeline.HEAD, 0f);
            glow(c, P[0], P[1], 18f * size * (0.8f + 0.4f * throb), ChopTimeline.MOONSTONE, 0.6f * throb);
            c.sprite(ChopArt.TEX_RING, P[0], P[1], 26f * size * throb, 26f * size * throb, 0f, alpha(0.5f * throb * sceneAlpha) | ChopTimeline.MOONSTONE);
        }
        // The pieces: those still in the head are drawn as the head; those loose, in flight.
        if (t >= ChopTimeline.RELIC_PEEL && t < ChopTimeline.RELIC_CONVERGE + 100) {
            List<float[]> flying = new ArrayList<>();
            for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) for (int k = 0; k < 4; k++) {
                float[] f = tl.chunk(i, j, k, t);
                float x, y, z;
                if (f == null) {
                    x = (i - 1.5f) * 2f; y = ChopTimeline.HEAD + (j - 1.5f) * 2f; z = (k - 1.5f) * 2f;
                    f = new float[]{x, y, z, 0f, 0f, 0f, 1f, 0f, 1f};
                }
                if (f[8] <= 0.01f) continue;
                flying.add(new float[]{depth(f[0], f[1], f[2]), i, j, k});
            }
            flying.sort((a, b) -> Float.compare(a[0], b[0]));
            for (float[] fl : flying) {
                int i = (int) fl[1], j = (int) fl[2], k = (int) fl[3];
                float[] f = tl.chunk(i, j, k, t);
                if (f == null) f = new float[]{(i - 1.5f) * 2f, ChopTimeline.HEAD + (j - 1.5f) * 2f, (k - 1.5f) * 2f, 0f, 0f, 0f, 1f, 0f, 1f};
                chunkBox(skin, i, j, k, f, c);
                if (f[7] > 0.15f) {
                    project(f[0], f[1], f[2]);
                    glow(c, P[0], P[1], 4f * size * f[6], mix(ChopTimeline.MOONSTONE, 0xFFFFFF, 0.5f), 0.7f * (f[7] - 0.15f) * f[8]);
                }
            }
        }
        project(ChopTimeline.MOON_X, ChopTimeline.MOON_Y, ChopTimeline.MOON_Z);
        float mx = P[0], my = P[1];
        float flash = tl.moonFlash(t);
        if (flash > 0.01f) {
            glow(c, mx, my, 50f * size * (1.3f - 0.3f * flash), 0xFFFFFF, 0.9f * flash);
            float ring = 1f - flash;
            c.sprite(ChopArt.TEX_RING, mx, my, 70f * size * ring, 70f * size * ring, 0f, alpha(0.8f * flash * sceneAlpha) | ChopTimeline.MOONSTONE);
        }
        float reveal = tl.revealFlash(t);
        if (reveal > 0.01f) {
            glow(c, mx, my, 45f * size * (1.3f - 0.3f * reveal), 0xFFFFFF, 0.85f * reveal);
            float ring = 1f - reveal;
            c.sprite(ChopArt.TEX_RING, mx, my, 80f * size * ring, 80f * size * ring, 0f, alpha(0.9f * reveal * sceneAlpha) | 0xFFFFFF);
        }
        if (tl.relicRevealed(t)) {
            specialText(tl, t, mx, my + ChopTimeline.MOON_SIZE * 0.72f * size, mix(ChopTimeline.MOONSTONE, 0xFFFFFF, 0.5f), 0xC9CFDD, c);
        }
    }

    /**
     * One 2 x 2 x 2 chunk of the head, all of it the colour of the skin where it came from - the
     * head's own pixels flying, not flesh - shrinking a little once it is loose.
     */
    static void chunkBox(String skin, int i, int j, int k, float[] f, Canvas c) {
        int fu = i * 2, fv = (3 - j) * 2, fz = k * 2;
        int[] rect;
        if (skin == null) rect = ChopArt.FLESH;
        else if (k == 3) rect = new int[]{8 + fu, 8 + fv, 2, 2};
        else if (j == 3) rect = new int[]{8 + fu, fz, 2, 2};
        else if (i == 0) rect = new int[]{k * 2, 8 + fv, 2, 2};
        else if (i == 3) rect = new int[]{16 + (3 - k) * 2, 8 + fv, 2, 2};
        else if (k == 0) rect = new int[]{24 + (3 - i) * 2, 8 + fv, 2, 2};
        else rect = new int[]{8 + fu, 8 + fv, 2, 2};
        String tex = skin == null ? SHEET : skin;
        boolean loose = f[3] != 0f || f[4] != 0f || f[5] != 0f;
        float s = f[6] * (loose ? 0.8f : 1f);
        box(f[0], f[1], f[2], f[3], f[4], f[5], s, s, s, all(tex), new int[][]{rect, rect, rect, rect, rect, rect},
                1f, 1f, 1f, f[7] * 0.85f, f[8], c);
    }

    // ================================================================== Brain Food

    /**
     * Head-local to world: a point on the head turns with its half as the halves ease apart, each
     * about its outer bottom edge (see {@link #head}); a point on the block stays put.
     */
    static float[] veinWorld(float x, float y, float z, ChopTimeline tl, long t) {
        float H = ChopTimeline.HEAD;
        return veinWorld(x, y, z, x < 0f || (x == 0f && y < 2f * H) ? -1f : 1f, tl, t);
    }

    /** {@link #veinWorld} on a given half (-1 left, 1 right), whichever side of the split x is. */
    static float[] veinWorld(float x, float y, float z, float side, ChopTimeline tl, long t) {
        if (y <= 0.2f) return new float[]{x, y, z};
        float H = ChopTimeline.HEAD, th = tl.halves(t)[0];
        float px = side * H, a = -side * th;
        float dx = x - px, c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[]{px + dx * c - y * s, dx * s + y * c, z};
    }

    /** Half a texel: the veins' pixel, twice the skin's resolution, square like everything else here. */
    static final float VEIN_PX = 0.5f;

    /**
     * The veins as pixel art painted onto the surfaces they run over - the head's front and top and
     * the board - not as lines floating in front of them (which drew in the game as translucent
     * bars). Each vein is walked in half-pixel steps and every grid square it touches is lit, the
     * brightest vein through a square winning; a square is drawn with a darker one-sided halo behind
     * it, pixel-art style, and only if its surface faces the camera. Sparks are single white squares.
     */
    static void pixelVeins(ChopTimeline tl, long t, Canvas c) {
        float H = ChopTimeline.HEAD;
        float[][] v = tl.veins();
        java.util.HashMap<Long, Float> lit = new java.util.HashMap<>();
        for (int i = 0; i < v.length; i++) {
            float g = tl.vein(i, t);
            if (g <= 0.02f) continue;
            float len = (float) Math.sqrt(sq(v[i][3] - v[i][0]) + sq(v[i][4] - v[i][1]) + sq(v[i][5] - v[i][2]));
            int n = Math.max(1, (int) Math.ceil(len / (VEIN_PX * 0.5f)));
            for (int k = 0; k <= n; k++) {
                float f = k / (float) n;
                long key = veinKey(v[i][0] + (v[i][3] - v[i][0]) * f, v[i][1] + (v[i][4] - v[i][1]) * f, v[i][2] + (v[i][5] - v[i][2]) * f, H);
                Float was = lit.get(key);
                if (was == null || was < g) lit.put(key, g);
            }
        }
        for (int k = 0; k < ChopTimeline.SPARKS; k++) {
            float[] s = tl.brainSpark(k, t);
            if (s != null) lit.put(veinKey(s[0], s[1], s[2], H), 3f);
        }
        int deep = 0x5B3FA8, mind = ChopTimeline.WISTERIA;
        for (int pass = 0; pass < 2; pass++) {
            for (java.util.Map.Entry<Long, Float> e : lit.entrySet()) {
                long key = e.getKey();
                int surface = (int) (key >>> 40);
                int cu = (int) ((key >>> 20) & 0xFFFFF) - 5000, cv = (int) (key & 0xFFFFF) - 5000;
                float g = e.getValue();
                float grow = pass == 0 ? VEIN_PX * 0.35f : 0f;
                float u0 = cu * VEIN_PX - grow, v0 = cv * VEIN_PX - grow, span = VEIN_PX + 2 * grow;
                // Corner and edges on the surface, head-local; then turned with the half they are on.
                float[] o, du, dv;
                if (surface == 0) {        // the head's front, z = H: across x, up y
                    o = new float[]{u0, v0, H + 0.03f}; du = new float[]{span, 0, 0}; dv = new float[]{0, span, 0};
                } else {                   // the head's top, or the board: across z, then x (so the normal is up)
                    float y = surface == 1 ? 2f * H + 0.03f : 0.03f;
                    o = new float[]{u0, y, v0}; du = new float[]{0, 0, span}; dv = new float[]{span, 0, 0};
                }
                // The half a pixel is on is decided by its middle, so none is stretched across the split.
                float mx = o[0] + (du[0] + dv[0]) / 2f;
                float side = mx < 0f ? -1f : 1f;
                float[] wo = veinWorld(o[0], o[1], o[2], side, tl, t);
                float[] wu = veinWorld(o[0] + du[0], o[1] + du[1], o[2] + du[2], side, tl, t);
                float[] wv = veinWorld(o[0] + dv[0], o[1] + dv[1], o[2] + dv[2], side, tl, t);
                float ux = wu[0] - wo[0], uy = wu[1] - wo[1], uz = wu[2] - wo[2], vx = wv[0] - wo[0], vy = wv[1] - wo[1], vz = wv[2] - wo[2];
                float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                if (-ny * sp + (-nx * sy + nz * cy) * cp <= 0f) continue;
                int col;
                float a;
                if (g > 2f) { col = 0xFFFFFF; a = 1f; }
                else if (pass == 0) { col = deep; a = Math.min(1f, 0.55f * g); }
                else { col = mix(mind, 0xF4ECFF, Math.min(1f, Math.max(0f, g - 0.45f))); a = Math.min(1f, g); }
                worldQuad(SHEET, ChopArt.WHITE, wo[0], wo[1], wo[2], ux, uy, uz, vx, vy, vz, alpha(a) | col, c);
            }
        }
    }

    /** The pixel square a head-local point on a vein falls in: its surface and grid position. */
    static long veinKey(float x, float y, float z, float H) {
        int surface;
        float u, v;
        if (y > 0.2f && z > H - 0.05f && y < 2f * H - 0.02f) { surface = 0; u = x; v = y; }
        else if (y >= 2f * H - 0.02f) { surface = 1; u = z; v = x; }
        else { surface = 2; u = z; v = x; }
        long cu = (long) Math.floor(u / VEIN_PX) + 5000, cv = (long) Math.floor(v / VEIN_PX) + 5000;
        return ((long) surface << 40) | (cu << 20) | cv;
    }

    static float sq(float x) {
        return x * x;
    }

    static void brain(ChopTimeline tl, long t, Canvas c) {
        int mind = ChopTimeline.WISTERIA, hot = mix(mind, 0xFFFFFF, 0.55f);
        if (t >= ChopTimeline.SPLIT && t < ChopTimeline.BRAIN_BURST) {
            pixelVeins(tl, t, c);
            project(0f, ChopTimeline.HEAD, 0f);
            float drainGlow = ease01((t - ChopTimeline.BRAIN_DRAIN) / 500f);
            glow(c, P[0], P[1], 12f * size, mind, 0.2f + 0.55f * drainGlow);
        }
        if (t < ChopTimeline.BRAIN_BURST) return;
        // The burst: the halves fly apart and the Brain Food rises out of them.
        float bu = (float) Math.exp(-(t - ChopTimeline.BRAIN_BURST) / 220.0);
        float ru = Math.min(1f, (t - ChopTimeline.BRAIN_BURST) / 900f);
        project(0f, ChopTimeline.HEAD, 0f);
        glow(c, P[0], P[1], 34f * size * (1.3f - 0.3f * bu), 0xFFFFFF, 0.85f * bu);
        c.sprite(ChopArt.TEX_RING, P[0], P[1], 50f * size * ru, 50f * size * ru, 0f, alpha(0.85f * (1f - ru) * sceneAlpha) | mind);
        burstChips(tl, t, ChopTimeline.BRAIN_BURST, c);

        float rise = tl.brainRise(t);
        float iy3 = ChopTimeline.HEAD + ChopTimeline.ITEM_TOP * rise;
        project(0f, iy3, 0f);
        float ix = P[0], iy = P[1];
        float itemDepth = depth(0f, iy3, 0f);
        float throb = tl.throb(t);
        int digits = tl.piDigits(t);
        piRings(tl, t, iy3, digits, itemDepth, false, c);
        float rays = Math.min(1f, Math.max(0f, (t - tl.arrive() + 60) / 260f));
        if (rays > 0.01f) {
            float spin = (t - tl.arrive()) / 40f;
            c.sprite(ChopArt.TEX_RAYS, ix, iy, 26f * size * rays, 26f * size * rays, spin, alpha(0.55f * rays * sceneAlpha) | mind);
            c.sprite(ChopArt.TEX_RAYS, ix, iy, 16f * size * rays, 16f * size * rays, -spin * 1.6f, alpha(0.35f * rays * sceneAlpha) | 0xFFFFFF);
        }
        glow(c, ix, iy, 15f * size * throb, mind, 0.75f);
        glow(c, ix, iy, 6f * size, 0xFFFFFF, 0.55f);
        float itemSize = Math.max(24f, 10f * size) * ChopScene.showScale * throb;
        ChopScene.showItem(c, ix, iy, itemSize, sceneAlpha, spinOf(tl, t));
        piRings(tl, t, iy3, digits, itemDepth, true, c);
        specialText(tl, t, ix, iy + itemSize * 0.8f + 6f * textScale(), mix(mind, 0xFFFFFF, 0.45f), 0xD3C6F2, c);
    }

    /** The head's halves blown outwards on a burst, as chips of the skin. */
    static void burstChips(ChopTimeline tl, long t, long at, Canvas c) {
        float u = (t - at) / 1000f;
        if (u < 0f || u > 1f) return;
        String skin = c.creatureSkin(tl.creature());
        java.util.Random r = new java.util.Random(tl.seed() * 19 + 1);
        for (int i = 0; i < 36; i++) {
            double a = r.nextFloat() * Math.PI * 2;
            float sp0 = 10f + r.nextFloat() * 16f, up = 4f + r.nextFloat() * 14f;
            float x = (float) Math.cos(a) * sp0 * u, z = (float) Math.sin(a) * sp0 * u, y = ChopTimeline.HEAD + up * u - 18f * u * u;
            int[] rect = skin == null ? ChopArt.FLESH : new int[]{r.nextInt(30), 8 + r.nextInt(6), 2, 2};
            String tex = skin == null ? SHEET : skin;
            float cs = 0.5f + r.nextFloat() * 0.5f, spin = u * (5f + i % 5);
            box(x, y, z, spin, spin * 0.6f, spin * 0.3f, cs, cs, cs, all(tex), new int[][]{rect, rect, rect, rect, rect, rect},
                    1f, 1f, 1f, 0f, 1f - u * u, c);
        }
    }

    static void piRings(ChopTimeline tl, long t, float cyw, int digits, float itemDepth, boolean front, Canvas c) {
        if (digits <= 0) return;
        String pi = ChopTimeline.PI;
        float fade = 1f - (float) Math.exp(-(t - ChopTimeline.BRAIN_BURST) / 600.0);
        float leave = ease01((t - tl.arrive()) / 700f);
        fade *= (1f - leave) * sceneAlpha;
        if (fade <= 0.01f) return;
        float rad = 8f + 12f * leave, tilt = 0.3f, speed = 0.6f + 1.2f * leave;
        float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
        int n = 30;
        for (int i = 0; i < n && i < digits && i < pi.length(); i++) {
            double ang = i * Math.PI * 2 / n + t / 1000.0 * speed;
            float lx = (float) Math.cos(ang) * rad, lz = (float) Math.sin(ang) * rad;
            float wx = lx, wy = cyw + lz * st, wz = lz * ct;
            float d = depth(wx, wy, wz) - itemDepth;
            if ((d > 0f) != front) continue;
            project(wx, wy, wz);
            float near = Math.max(0f, Math.min(1f, 0.5f + d / (rad * 2f)));
            float scale = textScale() * (0.75f + 0.55f * near);
            c.text(String.valueOf(pi.charAt(i)), P[0], P[1], scale, alpha((0.35f + 0.65f * near) * fade) | mix(0x8F7BC2, 0xFFFFFF, near));
        }
    }

    // ================================================================== helpers

    static void glow(Canvas c, float x, float y, float d, int rgb, float a) {
        a *= sceneAlpha;
        if (calm) a *= 0.55f;
        if (a <= 0.01f || d < 1f) return;
        c.sprite(ChopArt.TEX_GLOW, x, y, d, d, 0f, alpha(Math.min(1f, a)) | (rgb & 0xFFFFFF));
    }

    static int alpha(float a) {
        return Math.max(0, Math.min(255, Math.round(a * 255))) << 24;
    }

    static int channel(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    static int mix(int a, int b, float t) {
        return ChopTimeline.mix(a, b, t);
    }

    static float ease01(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3 - 2 * t);
    }

    static float rnd(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }
}
