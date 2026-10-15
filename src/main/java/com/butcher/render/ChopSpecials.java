package com.butcher.render;

import com.butcher.anim.ChopTimeline;
import com.butcher.anim.FleeceEnding;
import com.butcher.anim.GlintEnding;
import com.butcher.anim.HeartEnding;
import com.butcher.anim.MidasEnding;
import com.butcher.anim.HuntEnding;
import com.butcher.anim.StingEnding;
import com.butcher.render.art.ChopArt;

import java.util.ArrayList;
import java.util.List;

import static com.butcher.render.ChopScene.*;

/**
 * The five endings built after the Relic, Brain Food and Chimera - drawn with ChopScene's own
 * machinery (its projection, {@code boxAxes}, glows, text) so they cannot look like a different
 * mod. Each draws after the scene, except where a hook says otherwise.
 */
final class ChopSpecials {
    private ChopSpecials() {}

    /** Each ending's lights for this frame (ChopScene.light), before anything is drawn. */
    static void lights(ChopTimeline tl, long t) {
        if (tl.ending() == null) return;
        switch (tl.ending()) {
            case STING -> {
                StingEnding e = tl.sting();
                light(0f, 0.6f, 0f, 10f, StingEnding.VENOM, 0.5f * (1f - e.becomes(t)) * ease01((t - ChopTimeline.SPLIT - 100) / 300f));
                if (t >= StingEnding.IMPACT) light(StingEnding.STAB_X, 1f, StingEnding.STAB_Z, 9f, StingEnding.VENOM_LIGHT,
                        (float) Math.exp(-(t - StingEnding.IMPACT) / 300.0));
            }
            case HEART -> {
                HeartEnding e = tl.heart();
                if (t < tl.arrive()) {
                    float[] h = e.heart(t);
                    light(h[0], h[1], h[2], 12f, HeartEnding.CORE, 0.35f + 0.5f * e.beat(t));
                }
            }
            case FLEECE -> {
                FleeceEnding e = tl.fleece();
                light(0f, 4f, 0f, 10f, FleeceEnding.GOLD, 0.45f * e.halves(t) * ease01((t - FleeceEnding.UNRAVEL) / 500f));
            }
            case GLINT -> {
                GlintEnding e = tl.glint();
                light(0f, 3f, 0f, 11f, ChopTimeline.ENCHANT_PINK, 0.45f * e.drain(t) * ease01((t - GlintEnding.SEEP) / 400f));
            }
            case MIDAS -> {
                if (t >= MidasEnding.COLLAPSE) light(0f, tl.midas().crown(t)[0], 0f, 9f, MidasEnding.GOLD_LIGHT, 0.5f);
            }
            default -> { }
        }
    }

    /** Under the board, before anything else of the scene. */
    static void beforeBlock(ChopTimeline tl, long t, ChopScene.Canvas c) {
        if (t >= ChopTimeline.SPLIT && tl.ending() == ChopTimeline.Ending.HUNT) huntBehind(tl, t, c);
    }

    // Nothing here draws before the split - not even something invisible: ChopTest hashes every
    // draw call up to it, and a zero-size box is still a draw call that tells.

    /** Right after the board, before the head: things lying on the board's top. */
    static void onBlock(ChopTimeline tl, long t, ChopScene.Canvas c) {
        if (t < ChopTimeline.SPLIT) return;
        if (tl.ending() == ChopTimeline.Ending.STING) stingDecal(tl, t, c);
        if (tl.ending() == ChopTimeline.Ending.HUNT) huntCracks(tl, t, c);
        if (tl.ending() == ChopTimeline.Ending.HEART) beatRings(tl, t, c);
    }

    /**
     * Keeps a showcase on the screen: the drop at most a quarter of the screen high, lifted so its name, lines and
     * price clear the bottom edge. The Dye at 1.45x on a short window ran its words off the screen (the user, 0.3.0).
     * {y, size}.
     */
    static float[] fit(float y, float size) {
        int h = ChopScene.screenH;
        float ts = textScale();
        size = Math.min(size, h * 0.26f);
        float bottom = y + size * 0.62f + 6f * ts + 74f * ts;
        if (bottom > h - 6f) y -= bottom - (h - 6f);
        return new float[]{y, size};
    }

    static void draw(ChopTimeline tl, long t, int w, int h, ChopScene.Canvas c) {
        if (t < ChopTimeline.SPLIT) return;
        switch (tl.ending()) {
            case STING -> sting(tl, t, w, h, c);
            case HUNT -> hunt(tl, t, w, h, c);
            case FLEECE -> fleece(tl, t, c);
            case HEART -> heart(tl, t, w, h, c);
            case MIDAS -> midas(tl, t, w, h, c);
            case GLINT -> glint(tl, t, w, h, c);
            default -> { }
        }
    }

    // ================================================================== Chimera I: the glint

    /** The glint on the board and the halves, while there is any. */
    static ChopScene.Glint glint(ChopTimeline tl, long t) {
        if (tl.ending() != ChopTimeline.Ending.GLINT || t < GlintEnding.SEEP || t >= GlintEnding.CONVERGE) return null;
        GlintEnding e = tl.glint();
        int[] sc = GlintEnding.scroll(t);
        return new ChopScene.Glint() {
            public float alpha(float x, float y, float z) {
                return e.glint(x, y, z, t);
            }

            public int[] scroll() {
                return sc;
            }
        };
    }

    private static final int[] GLYPH_TINTS = {ChopTimeline.ENCHANT_PINK, 0xC77DFF, 0xF4D9FF, 0xE070F0};

    private static void glint(ChopTimeline tl, long t, int w, int h, ChopScene.Canvas c) {
        GlintEnding e = tl.glint();
        int pink = ChopTimeline.ENCHANT_PINK;
        // The enchantment filling the air: a soft violet glow in from the edges, breathing. (Tiles of
        // the glint texture along the edges drew as a hard, seamed frame.)
        float edges = e.edges(t);
        if (edges > 0.01f) {
            float breathe = 0.8f + 0.2f * (float) Math.sin(t / 300.0);
            // Corners only, and faint: at 55% it grew into a thick purple frame over the whole edge (the review, D7).
            c.sprite(ChopArt.TEX_VIGNETTE, w / 2f, h / 2f, w * 1.22f, h * 1.22f, 0f, alpha(0.25f * edges * breathe * sceneAlpha) | 0x7A2E9E);
        }
        // The enchanting glyphs: out of the head, round it, then spiralling into the point.
        project(0f, GlintEnding.POINT_Y, 0f);
        float cxp = P[0], cyp = P[1];
        float spiral = t >= GlintEnding.SPIRAL && t < GlintEnding.CONVERGE ? (t - GlintEnding.SPIRAL) / (float) (GlintEnding.CONVERGE - GlintEnding.SPIRAL) : 0f;
        if (spiral > 0f) glow(c, cxp, cyp, (6f + 16f * spiral) * size, pink, 0.2f + 0.6f * spiral);
        for (int k = 0; k < GlintEnding.GLYPHS; k++) {
            float[] q = e.glyph(k, t);
            if (q == null) continue;
            project(q[0], q[1], q[2]);
            float s = q[3] * size * 1.45f;
            int tint = GLYPH_TINTS[k % GLYPH_TINTS.length];
            glow(c, P[0], P[1], s * 2.4f, tint, 0.3f * q[5]);
            c.sprite(ChopArt.glyphTex((int) q[4]), P[0], P[1], s, s, 0f, alpha(q[5] * sceneAlpha) | tint);
        }
        // The flash where it all meets, and the book.
        float flash = e.flash(t);
        if (flash > 0.01f) {
            glow(c, cxp, cyp, 40f * size * (1.3f - 0.3f * flash), 0xFFFFFF, 0.9f * flash);
            c.sprite(ChopArt.TEX_RING, cxp, cyp, 50f * size * (1f - flash), 50f * size * (1f - flash), 0f, alpha(0.9f * flash * sceneAlpha) | pink);
            c.sprite(ChopArt.TEX_RING, cxp, cyp, 30f * size * (1f - flash), 30f * size * (1f - flash), 0f, alpha(0.7f * flash * sceneAlpha) | 0xFFFFFF);
        }
        if (t >= GlintEnding.CONVERGE) {
            float rays = tl.rays(t);
            if (rays > 0.01f) {
                float spin = (t - tl.arrive()) / 40f;
                c.sprite(ChopArt.TEX_RAYS, cxp, cyp, 28f * size * rays, 28f * size * rays, spin, alpha(0.55f * rays * sceneAlpha) | pink);
                c.sprite(ChopArt.TEX_RAYS, cxp, cyp, 18f * size * rays, 18f * size * rays, -spin * 1.6f, alpha(0.35f * rays * sceneAlpha) | 0xFFFFFF);
            }
            float pulse = 0.5f + 0.5f * (float) Math.sin((t - GlintEnding.CONVERGE) / 260.0);
            glow(c, cxp, cyp, 16f * size, pink, 0.35f + 0.25f * pulse);
            // A few glyphs still circling the book, slowly - the enchantment settled in it.
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3 + (t - GlintEnding.CONVERGE) / 900.0;
                float r = 7f * size, bob = (float) Math.sin((t - GlintEnding.CONVERGE) / 400.0 + k) * 0.8f * size;
                float ga = ease01((t - GlintEnding.CONVERGE - 300 - k * 80) / 400f);
                c.sprite(ChopArt.glyphTex((k * 7) % 26), cxp + (float) Math.cos(a) * r, cyp + (float) Math.sin(a) * r * 0.35f + bob,
                        size * 0.9f, size * 0.9f, 0f, alpha(0.8f * ga * sceneAlpha) | GLYPH_TINTS[k % 4]);
            }
            float itemSize = Math.max(24f, 10f * size) * ChopScene.showScale;
            { float[] fitted = fit(cyp, itemSize); cyp = fitted[0]; itemSize = fitted[1]; }
            float grow = easeOutBackLocal((t - GlintEnding.CONVERGE) / 350f);
            ChopScene.showItem(c, cxp, cyp, itemSize * grow, sceneAlpha, 0f);
            specialText(tl, t, cxp, cyp + itemSize * 0.62f + 6f * textScale(), ChopTimeline.ENCHANT_PINK, 0xF0C8DA, c);
        }
    }

    // ================================================================== the Crown of Greed

    /** The gold for the scene's board and head, while it is spreading and until they are coins. */
    static ChopScene.Gild gild(ChopTimeline tl, long t) {
        if (tl.ending() == ChopTimeline.Ending.HUNT) return huntGild(tl, t);
        if (tl.ending() != ChopTimeline.Ending.MIDAS || t < MidasEnding.CREEP) return null;
        MidasEnding e = tl.midas();
        float shine = e.shine(t), span = sx0 * 2f;
        return (x, y, z, cell, now, sx, sy) -> {
            long at = e.gilded(x, y, z, cell);
            if (now < at) return 0;
            int h = cell * 0x9E3779B1;
            float flash = (float) Math.exp(-(now - at) / 140.0);
            if (shine >= 0f) {
                float band = shine * (span + 200f) - 100f, d = Math.abs(sx + sy * 0.5f - band);
                if (d < 16f) flash = Math.max(flash, 0.6f * (1f - d / 16f));
            }
            return 0x57000000 | mix(GOLDS[(h >>> 28) & 3], 0xFFF4C8, 0.85f * flash);
        };
    }

    /** Gold, as tints: multiplied into the face's own texture, so what was on it shows through as a gilded relief. */
    private static final int[] GOLDS = {0xF5C542, 0xE0A526, 0xFFD95C, 0xCC9420};

    /**
     * The gold laid over one face, texel by texel: once the gold has reached a texel's middle it is
     * drawn again, tinted gold - a face becomes a gilded face, wood gilded wood - flashing white
     * the moment it turns, shaded like the face, and caught by the shine.
     */
    /**
     * Colours a face texel by texel through the gild. Each texel's colour is worked out first, then runs of
     * the same colour - along a row, and the same run down consecutive rows - are drawn as one quad pair:
     * the same pixels, but a face the gild has wholly taken (the Seraph's grey world) is two quads, not
     * two per texel. Drawn one by one, the Dragon's grey reached ~37,000 quads a frame and killed the frame
     * rate in game.
     */
    static void gildFace(ChopScene.Gild gild, String tex, int[] rect, float ox, float oy, float oz, float ux, float uy, float uz,
                         float vx, float vy, float vz, float px, float py, float qx, float qy, float rx, float ry, int n, int m,
                         int face, float k, ChopScene.Canvas c) {
        long now = realT;
        int[] cols = new int[n * m];
        for (int j = 0; j < m; j++) {
            for (int i = 0; i < n; i++) {
                float cu = (i + 0.5f) / n, cv = (j + 0.5f) / m;
                int cell = face * 4099 + j * 61 + i * 7 + Math.round(ox * 131 + oz * 71) * 13;
                float cx = px + qx * cu + rx * cv, cyy = py + qy * cu + ry * cv;
                cols[j * n + i] = gild.colour(ox + ux * cu + vx * cv, oy + uy * cu + vy * cv, oz + uz * cu + vz * cv, cell, now, cx, cyy);
            }
        }
        float lit = Math.min(1f, k + 0.15f);
        boolean[] done = new boolean[n * m];
        for (int j = 0; j < m; j++) {
            for (int i = 0; i < n; i++) {
                int col = cols[j * n + i];
                if (col == 0 || done[j * n + i]) continue;
                // The run along this row, then as many rows below as carry the very same run.
                int i1 = i + 1;
                while (i1 < n && cols[j * n + i1] == col && !done[j * n + i1]) i1++;
                int j1 = j + 1;
                rows:
                while (j1 < m) {
                    for (int q = i; q < i1; q++) if (cols[j1 * n + q] != col || done[j1 * n + q]) break rows;
                    j1++;
                }
                for (int jj = j; jj < j1; jj++) for (int q = i; q < i1; q++) done[jj * n + q] = true;
                int cr = Math.round((col >> 16 & 255) * lit), cg = Math.round((col >> 8 & 255) * lit), cb = Math.round((col & 255) * lit);
                int u0 = rect[0] + i * rect[2] / n, u1 = rect[0] + i1 * rect[2] / n;
                int v0 = rect[1] + j * rect[3] / m, v1 = rect[1] + j1 * rect[3] / m;
                int[] texel = {u0, v0, Math.max(1, u1 - u0), Math.max(1, v1 - v0)};
                float ex = px + qx * i / n + rx * j / m, ey = py + qy * i / n + ry * j / m;
                float wq = (i1 - i) / (float) n, hq = (j1 - j) / (float) m;
                // Solid colour, and the face's own texture faintly over it: the features as a relief.
                emit(c, ChopScene.SHEET, ex, ey, qx * wq, qy * wq, rx * hq, ry * hq, ChopArt.WHITE, 0xFF000000 | cr << 16 | cg << 8 | cb);
                int relief = col >>> 24;
                if (relief > 2) emit(c, tex, ex, ey, qx * wq, qy * wq, rx * hq, ry * hq, texel, relief << 24 | mix(col & 0xFFFFFF, 0xFFFFFF, 0.55f));
            }
        }
    }

    private static final int[][] HELM = {{16, 8, 8, 8}, {0, 8, 8, 8}, {8, 0, 8, 8}, {16, 0, 8, 8}, {8, 8, 8, 8}, {24, 8, 8, 8}};
    private static final int[][] HELM_HAT = {{48, 8, 8, 8}, {32, 8, 8, 8}, {40, 0, 8, 8}, {48, 0, 8, 8}, {40, 8, 8, 8}, {56, 8, 8, 8}};

    private static void midas(ChopTimeline tl, long t, int w, int h, ChopScene.Canvas c) {
        MidasEnding e = tl.midas();
        float[] cr = e.crown(t);
        float cyw = cr[0];
        project(0f, cyw, 0f);
        float hx = P[0], hy = P[1];
        float crownDepth = depth(0f, cyw, 0f);
        coins(e, t, crownDepth, false, c);
        // The burst: a flash of gold where the halves were.
        float burst0 = t >= MidasEnding.COLLAPSE ? (float) Math.exp(-(t - MidasEnding.COLLAPSE) / 200.0) : 0f;
        if (burst0 > 0.01f) {
            project(0f, 2f, 0f);
            glow(c, P[0], P[1], 34f * size * (1.2f - 0.2f * burst0), MidasEnding.GOLD_LIGHT, 0.8f * burst0);
        }
        // The crown, a real 3D golden helmet, until it is presented; then the game's own icon.
        if (cr[2] > 0.01f && t < tl.arrive() && cyw > -1.5f) {
            glow(c, hx, hy, 20f * size, MidasEnding.GOLD, 0.35f);
            String[] tex = all(ChopArt.TEX_ARMOR);
            insideOut = true;
            box(0f, cyw, 0f, 0f, cr[1], 0f, 4.95f, 4.95f, 4.95f, tex, HELM, 0.45f, 0.4f, 0.3f, 0f, 1f, c);
            insideOut = false;
            inkWidth = Math.max(1.5f, size / 3f);
            box(0f, cyw, 0f, 0f, cr[1], 0f, 5f, 5f, 5f, tex, HELM, 1f, 1f, 1f, 0f, 1f, c);
            inkWidth = 0f;
            box(0f, cyw, 0f, 0f, cr[1], 0f, 5f, 5f, 5f, tex, HELM, 1f, 1f, 1f, 0.05f, 1f, c);
            box(0f, cyw, 0f, 0f, cr[1], 0f, 5.5f, 5.5f, 5.5f, tex, HELM_HAT, 1f, 1f, 1f, 0.05f, 1f, c);
        }
        coins(e, t, crownDepth, true, c);
        if (t >= tl.arrive()) {
            float rays = tl.rays(t);
            if (rays > 0.01f) {
                float spin = (t - tl.arrive()) / 40f;
                c.sprite(ChopArt.TEX_RAYS, hx, hy, 26f * size * rays, 26f * size * rays, spin, alpha(0.5f * rays * sceneAlpha) | ChopTimeline.CARMINE);
                c.sprite(ChopArt.TEX_RAYS, hx, hy, 17f * size * rays, 17f * size * rays, -spin * 1.6f, alpha(0.4f * rays * sceneAlpha) | MidasEnding.GOLD_LIGHT);
            }
            float burst = tl.arrivalBurst(t);
            if (burst > 0.01f) glow(c, hx, hy, 30f * size * (1.3f - 0.3f * burst), 0xFFFFFF, 0.8f * burst);
            rain(tl, t, w, h, c);
            glow(c, hx, hy, 15f * size, MidasEnding.GOLD, 0.4f);
            float itemSize = Math.max(24f, 10f * size) * ChopScene.showScale;
            { float[] fitted = fit(hy, itemSize); hy = fitted[0]; itemSize = fitted[1]; }
            float grow = easeOutBackLocal((t - tl.arrive()) / 350f);
            ChopScene.showItem(c, hx, hy, itemSize * grow, sceneAlpha, 0f);
            specialText(tl, t, hx, hy + itemSize * 0.62f + 6f * textScale(), 0xFFD86A, 0xF2E2B0, c);
        }
    }

    /** The avalanche: the statue's coins, spinning, flung out and falling. */
    private static void coins(MidasEnding e, long t, float crownDepth, boolean front, ChopScene.Canvas c) {
        if (t < MidasEnding.COLLAPSE) return;
        for (int k = 0; k < MidasEnding.COINS; k++) {
            float[] q = e.coin(k, t);
            if (q == null) continue;
            if ((depth(q[0], q[1], q[2]) > crownDepth) != front) continue;
            project(q[0], q[1], q[2]);
            coin(c, P[0], P[1], 1.5f * size, q[3], k);
        }
    }

    /** A coin on screen, turning about its vertical axis - a disc seen from its edge to its face. */
    private static void coin(ChopScene.Canvas c, float x, float y, float d, float spin, int k) {
        float face = Math.abs((float) Math.cos(spin));
        float wd = d * Math.max(0.14f, face);
        c.sprite(com.butcher.render.art.ThawArt.TEX_COIN, x, y, wd + 1.5f, d + 1.5f, 0f, alpha(0.9f * sceneAlpha) | 0x3A2400);
        c.sprite(com.butcher.render.art.ThawArt.TEX_COIN, x, y, wd, d, 0f, alpha(sceneAlpha) | (face < 0.3f ? 0xC08A20 : 0xFFFFFF));
        if (face > 0.93f && k % 3 == 0) glow(c, x - d * 0.2f, y - d * 0.2f, d * 0.9f, 0xFFFFFF, 0.7f);
    }

    /** Coins raining down the screen while the price counts up. */
    private static void rain(ChopTimeline tl, long t, int w, int h, ChopScene.Canvas c) {
        long from = tl.countStart() - 250, to = tl.countStart() + ChopTimeline.COUNT_MS + 500;
        if (t < from) return;
        for (int k = 0; k < 46; k++) {
            long start = from + (long) (rnd(k, 3) * (to - from));
            float sec = (t - start) / 1000f;
            if (sec < 0f) continue;
            float speed = h * (0.55f + 0.5f * rnd(k, 5));
            float y = -12f + speed * sec + 0.4f * h * sec * sec;
            if (y > h + 12f) continue;
            float x = rnd(k, 7) * w;
            coin(c, x, y, 7f + 5f * rnd(k, 9), sec * (6f + 5f * rnd(k, 11)), k);
        }
    }

    // ================================================================== the Manti-core

    private static void heart(ChopTimeline tl, long t, int w, int h, ChopScene.Canvas c) {
        HeartEnding e = tl.heart();
        int core = HeartEnding.CORE;
        float[] at = e.heart(t);
        float beat = e.beat(t);
        double yaw = Math.toRadians(tl.yaw(t));
        float[] R = {(float) Math.cos(yaw), 0f, (float) Math.sin(yaw)}, F = {(float) -Math.sin(yaw), 0f, (float) Math.cos(yaw)};
        float heartDepth = depth(at[0], at[1], at[2]);
        float wingA = e.wingAlpha(t);
        float[] pose = e.wing(t);
        // Each wing: three jointed panels, each {origin, span edge, chord edge}.
        float[] bends = {0f, e.bend(1, t), e.bend(2, t)};
        float[][][][] wings = new float[2][][][];
        for (int k = 0; k < 2; k++) wings[k] = wingPanels(k == 0 ? 1f : -1f, at, pose, bends, R, F);
        if (t < tl.arrive()) {
            project(at[0], at[1], at[2]);
            glow(c, P[0], P[1], 16f * size * (1f + 0.3f * beat), core, 0.3f + 0.35f * beat);
            // The cells flying in and fusing: lion, bat and scorpion, from all round.
            String[] cellTex = all(ChopScene.SHEET);
            for (int k = 0; k < HeartEnding.CELLS; k++) {
                float[] q = e.cell(k, t);
                if (q == null) continue;
                int[] r = {ChopArt.CELLS[0] + (k * 5) % 12, ChopArt.CELLS[1] + (k * 7) % 12, 3, 3};
                int[][] rects = {r, r, r, r, r, r};
                inkWidth = Math.max(1.2f, size / 4f);
                box(q[0], q[1], q[2], q[4], q[4] * 0.7f, 0f, q[3], q[3], q[3], cellTex, rects, 1f, 1f, 1f, 0f, 1f, c);
                inkWidth = 0f;
                box(q[0], q[1], q[2], q[4], q[4] * 0.7f, 0f, q[3], q[3], q[3], cellTex, rects, 1f, 1f, 1f, 0.15f, 1f, c);
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 2; k++) {
                float[][][] panels = wings[k];
                float[][] mid = panels[1];
                float d = depth(mid[0][0] + mid[1][0] * 0.5f + mid[2][0] * 0.5f, mid[0][1] + mid[1][1] * 0.5f + mid[2][1] * 0.5f,
                        mid[0][2] + mid[1][2] * 0.5f + mid[2][2] * 0.5f);
                if ((d > heartDepth) != (pass == 1) || wingA <= 0.01f) continue;
                drawWing(panels, wingA, c);
                if (pass == 1) wingCracks(panels, e.cracks(t), k, c);
            }
            if (pass == 0 && t < tl.arrive()) {
                // The heart itself: a box of mashed cells, turning, swelling with each beat.
                float s = e.heartSize(t);
                String[] tex = all(ChopScene.SHEET);
                int[][] rects = {ChopArt.CELLS, ChopArt.CELLS, ChopArt.CELLS, ChopArt.CELLS, ChopArt.CELLS, ChopArt.CELLS};
                float spin = t / 1000f * 0.6f;
                inkWidth = Math.max(1.5f, size / 3f);
                box(at[0], at[1], at[2], 0.55f, 0.78f + spin, 0.35f, s, s, s, tex, rects, 1f, 1f, 1f, 0f, 1f, c);
                inkWidth = 0f;
                box(at[0], at[1], at[2], 0.55f, 0.78f + spin, 0.35f, s, s, s, tex, rects, 1f, 1f, 1f, 0.3f * beat, 1f, c);
                project(at[0], at[1], at[2]);
                glow(c, P[0], P[1], 5f * size * s, 0xFFFFFF, 0.25f + 0.5f * beat);
            }
        }
        // The cocoon's seam: light leaking where the wing tips meet.
        float seam = e.seam(t);
        if (seam > 0.01f) {
            float[][] ta = wings[0][2], tb = wings[1][2];
            float mx = (ta[0][0] + ta[1][0] + tb[0][0] + tb[1][0]) / 2f, my = (ta[0][1] + ta[1][1] + tb[0][1] + tb[1][1]) / 2f,
                    mz = (ta[0][2] + ta[1][2] + tb[0][2] + tb[1][2]) / 2f;
            project(mx, my - HeartEnding.CHORD * 0.3f, mz);
            c.sprite(ChopArt.TEX_GLOW, P[0], P[1], 3f * size, HeartEnding.CHORD * size * 1.3f, 0f, alpha(0.9f * seam * sceneAlpha) | core);
            c.sprite(ChopArt.TEX_GLOW, P[0], P[1], 1f * size, HeartEnding.CHORD * size * 0.9f, 0f, alpha(0.9f * seam * sceneAlpha) | 0xFFFFFF);
        }
        // The gust: streaks of wind, dust thrown off the board.
        float gust = e.gust(t);
        if (gust > 0.01f) {
            float cx = w / 2f, cy = h * 0.55f;
            float u = (t - HeartEnding.FLAP - 60) / 650f;
            for (int k = 0; k < 26; k++) {
                float side = k % 2 == 0 ? 1f : -1f;
                float y = cy + (rnd(k, 5) - 0.5f) * h * 0.7f;
                float x0 = cx + side * (0.08f + 0.5f * u + 0.2f * rnd(k, 7)) * w, len = (0.08f + 0.12f * rnd(k, 9)) * w;
                c.line(x0, y, x0 + side * len, y + (rnd(k, 11) - 0.5f) * 8f, 1f + 1.5f * rnd(k, 13), alpha(0.4f * gust) | 0xD8F2E6);
            }
        }
        // The wings going to smoke once they are flung open.
        if (t >= tl.arrive() && t < tl.arrive() + 900) {
            float u = (t - tl.arrive()) / 900f;
            for (int k = 0; k < 2; k++) {
                for (float[][] q : wings[k]) {
                    for (int j = 0; j < 3; j++) {
                        float s = (j + 0.5f) / 3f;
                        float x = q[0][0] + q[1][0] * s + q[2][0] * 0.4f, y = q[0][1] + q[1][1] * s + q[2][1] * 0.4f + u * 3f, z = q[0][2] + q[1][2] * s + q[2][2] * 0.4f;
                        project(x, y, z);
                        glow(c, P[0], P[1], (3f + 5f * u) * size, 0x2E4A3E, 0.5f * (1f - u));
                    }
                }
            }
        }
        // The reveal.
        if (t >= tl.arrive()) {
            float[] rv = e.reveal();
            project(rv[0], rv[1], rv[2]);
            float ix = P[0], iy = P[1];
            float rays = tl.rays(t);
            if (rays > 0.01f) {
                float spin = (t - tl.arrive()) / 40f;
                c.sprite(ChopArt.TEX_RAYS, ix, iy, 28f * size * rays, 28f * size * rays, spin, alpha(0.55f * rays * sceneAlpha) | ChopTimeline.VIRIDIAN);
                c.sprite(ChopArt.TEX_RAYS, ix, iy, 17f * size * rays, 17f * size * rays, -spin * 1.6f, alpha(0.35f * rays * sceneAlpha) | 0xFFD37A);
            }
            float burst = tl.arrivalBurst(t);
            if (burst > 0.01f) {
                glow(c, ix, iy, 36f * size * (1.3f - 0.3f * burst), 0xFFFFFF, 0.85f * burst);
                c.sprite(ChopArt.TEX_RING, ix, iy, 60f * size * (1f - burst), 60f * size * (1f - burst), 0f, alpha(0.85f * burst * sceneAlpha) | core);
            }
            glow(c, ix, iy, 15f * size, core, 0.45f);
            float itemSize = Math.max(24f, 10f * size) * ChopScene.showScale;
            { float[] fitted = fit(iy, itemSize); iy = fitted[0]; itemSize = fitted[1]; }
            float grow = easeOutBackLocal((t - tl.arrive()) / 350f);
            ChopScene.showItem(c, ix, iy, itemSize * grow, sceneAlpha, 32f + (t - tl.arrive()) / 1000f * 40f);
            specialText(tl, t, ix, iy + itemSize * 0.62f + 6f * textScale(), mix(ChopTimeline.VIRIDIAN, 0xFFFFFF, 0.5f), 0xBFE8D2, c);
        }
    }

    private static float easeOutBackLocal(float u) {
        if (u <= 0f) return 0f;
        if (u >= 1f) return 1f;
        float k = 1.9f, v = u - 1f;
        return 1f + (k + 1f) * v * v * v + k * v * v;
    }

    /** Where a wing's joints are, as fractions of its texture's width (root to tip). */
    private static final float[] JOINTS = {0f, 22f / 64f, 44f / 64f, 1f};

    /**
     * A wing's three panels: the root beside the heart, each panel's span edge turned round the
     * vertical by the joints' bends on top of the wing's own spread, and its chord edge down the
     * trailing side. Right wing ({@code side} 1) and left (-1).
     */
    private static float[][][] wingPanels(float side, float[] at, float[] pose, float[] bends, float[] R, float[] F) {
        float rise = pose[1], tilt = pose[2], s = pose[3], cs = pose[4], hood = pose[5];
        float C = HeartEnding.CHORD * cs * (hood > 0f ? 1f : s);
        float[] o = {at[0] + side * R[0] * 0.9f, at[1] + 1.4f + C * 0.5f * hood, at[2] + side * R[2] * 0.9f};
        float[][][] out = new float[3][][];
        float phi = pose[0];
        for (int k = 0; k < 3; k++) {
            phi += bends[k];
            float cph = (float) Math.cos(phi), sph = (float) Math.sin(phi);
            float[] d = {side * R[0] * cph + F[0] * sph, 0f, side * R[2] * cph + F[2] * sph};
            float cr = (float) Math.cos(rise), sr = (float) Math.sin(rise);
            float[] span = {d[0] * cr, sr, d[2] * cr};
            float[] down = {-span[0] * span[1], -(1f - span[1] * span[1]), -span[2] * span[1]};
            norm(down);
            float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
            float[] chord = {down[0] * ct + F[0] * st, down[1] * ct, down[2] * ct + F[2] * st};
            norm(chord);
            float L = HeartEnding.SPAN * s * (JOINTS[k + 1] - JOINTS[k]);
            out[k] = new float[][]{o.clone(), {span[0] * L, span[1] * L, span[2] * L}, {chord[0] * C, chord[1] * C, chord[2] * C}};
            o = new float[]{o[0] + span[0] * L, o[1] + span[1] * L, o[2] + span[2] * L};
        }
        return out;
    }

    private static void norm(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (l < 1e-5f) return;
        v[0] /= l; v[1] /= l; v[2] /= l;
    }

    /**
     * A wing drawn panel by panel, root to tip whichever way each faces: a panel whose tip end is to
     * the screen's right of its root end is drawn from the root with the texture as painted, one
     * the other way from its tip with the mirrored half - never back to front, which the GUI would
     * not draw. Outlined as one: every panel's ink first, then every panel.
     */
    private static void drawWing(float[][][] panels, float alpha, ChopScene.Canvas c) {
        float before = faceAlpha;
        faceAlpha = alpha;
        for (int pass = 0; pass < 2; pass++) {
            inkWidth = pass == 0 ? Math.max(1.3f, size / 3.5f) : 0f;
            for (int k = 0; k < panels.length; k++) {
                float[] o = panels[k][0], u = panels[k][1], v = panels[k][2];
                project(o[0], o[1], o[2]);
                float rx = P[0];
                project(o[0] + u[0], o[1] + u[1], o[2] + u[2]);
                boolean toRight = P[0] >= rx;
                int a = Math.round(JOINTS[k] * 64), b = Math.round(JOINTS[k + 1] * 64);
                int[] rect = toRight ? new int[]{ChopArt.WING_R[0] + a, 0, b - a, 64} : new int[]{ChopArt.WING_L[0] + 64 - b, 0, b - a, 64};
                float ox = o[0], oy = o[1], oz = o[2], ux = u[0], uy = u[1], uz = u[2];
                if (!toRight) {
                    ox += ux; oy += uy; oz += uz;
                    ux = -ux; uy = -uy; uz = -uz;
                }
                worldQuad(ChopArt.TEX_WING, rect, ox, oy, oz, ux, uy, uz, v[0], v[1], v[2], pass == 0 ? 0xFF000000 : 0xFFFFFFFF, c);
            }
        }
        inkWidth = 0f;
        faceAlpha = before;
    }

    /**
     * Cracks of light spreading over a wing of the cocoon before it bursts: pixel squares along a few
     * wandering paths across each panel, viridian going white, growing longer.
     */
    private static void wingCracks(float[][][] panels, float grow, int wing, ChopScene.Canvas c) {
        if (grow <= 0.01f) return;
        for (int k = 0; k < panels.length; k++) {
            float[] o = panels[k][0], u = panels[k][1], v = panels[k][2];
            java.util.Random r = new java.util.Random(wing * 31 + k * 7 + 3);
            float px = 0.045f;
            for (int path = 0; path < 3; path++) {
                float cu = 0.2f + 0.6f * r.nextFloat(), cv = 0.15f + 0.3f * r.nextFloat();
                double dir = Math.PI / 2 + (r.nextFloat() - 0.5f) * 1.2;
                int n = Math.round(14 * grow);
                for (int j = 0; j < n; j++) {
                    dir += (r.nextFloat() - 0.5f) * 0.9;
                    cu += (float) Math.cos(dir) * px;
                    cv += (float) Math.sin(dir) * px * 1.4f;
                    if (cu < 0.05f || cu > 0.95f || cv < 0.05f || cv > 0.8f) break;
                    float x = o[0] + u[0] * cu + v[0] * cv, y = o[1] + u[1] * cu + v[1] * cv, z = o[2] + u[2] * cu + v[2] * cv;
                    project(x, y, z);
                    float sq = Math.max(1.5f, size * 0.5f);
                    int col = mix(HeartEnding.CORE, 0xFFFFFF, grow * 0.7f);
                    c.fill(P[0] - sq / 2f, P[1] - sq / 2f, sq, sq, alpha(sceneAlpha) | col);
                    if (j % 4 == 0) glow(c, P[0], P[1], 3f * size, HeartEnding.CORE, 0.5f * grow);
                }
            }
        }
    }

    /** Each heartbeat's ring, rolling out across the board. */
    private static void beatRings(ChopTimeline tl, long t, ChopScene.Canvas c) {
        HeartEnding e = tl.heart();
        for (int i = 0; i < HeartEnding.BEATS.length; i++) {
            float u = e.ring(i, t);
            if (u < 0f) continue;
            float r = 1.5f + 11f * (1f - (1f - u) * (1f - u));
            worldQuad(ChopArt.TEX_RING, new int[]{0, 0, 128, 128}, -r, 0.06f, -r, 2 * r, 0f, 0f, 0f, 0f, 2 * r,
                    alpha(0.8f * (1f - u)) | HeartEnding.CORE, c);
        }
    }

    // ================================================================== Shimmering Wool

    private static void fleece(ChopTimeline tl, long t, ChopScene.Canvas c) {
        FleeceEnding e = tl.fleece();
        ram(e, t, c);
        project(0f, FleeceEnding.SPIN_Y, 0f);
        float bx = P[0], by = P[1];
        float ballDepth = depth(0f, FleeceEnding.SPIN_Y, 0f);
        float ball = e.ballShown(t), ballA = e.ballAlpha(t);
        if (ball > 0.05f) glow(c, bx, by, ball * 7f * size, FleeceEnding.GOLD, 0.35f * Math.max(ballA, e.cubeAlpha(t)));
        // Fibres behind the ball, the ball, fibres in front.
        fibres(e, t, ballDepth, false, c);
        spindle(e, t, c);
        if (ball > 0.05f && ballA > 0.01f) {
            float ink = Math.max(1.5f, size / 3f);
            c.sprite(ChopArt.TEX_YARN, bx, by, 2f * ball * size + 2f * ink, 2f * ball * size + 2f * ink, e.spin(t) * 9f, alpha(ballA * sceneAlpha) | 0x000000);
            c.sprite(ChopArt.TEX_YARN, bx, by, 2f * ball * size, 2f * ball * size, e.spin(t) * 9f, alpha(ballA * sceneAlpha) | 0xFFFFFF);
            glow(c, bx - ball * size * 0.35f, by - ball * size * 0.4f, ball * size * 0.9f, 0xFFFFFF, 0.45f * ballA);
        }
        fibres(e, t, ballDepth, true, c);

        float cubeA = e.cubeAlpha(t);
        if (cubeA > 0.01f) {
            project(0f, e.cubeY(t), 0f);
            float ix = P[0], iy = P[1];
            float rays = tl.rays(t);
            if (rays > 0.01f) {
                float spin = (t - tl.arrive()) / 40f;
                c.sprite(ChopArt.TEX_RAYS, ix, iy, 26f * size * rays, 26f * size * rays, spin, alpha(0.5f * rays * sceneAlpha) | ChopTimeline.CARMINE);
                c.sprite(ChopArt.TEX_RAYS, ix, iy, 16f * size * rays, 16f * size * rays, -spin * 1.6f, alpha(0.35f * rays * sceneAlpha) | FleeceEnding.GOLD_LIGHT);
            }
            float itemSize = Math.max(24f, 9f * size) * ChopScene.showScale;
            { float[] fitted = fit(iy, itemSize); iy = fitted[0]; itemSize = fitted[1]; }
            // Shafts of light falling on it from above.
            float shafts = e.shafts(t);
            if (shafts > 0.01f) {
                for (int k = -1; k <= 1; k++) {
                    float len = itemSize * 3.4f;
                    // Leaning 20 degrees, falling from the upper right onto it; the bright middle of
                    // each soft beam just above it.
                    c.sprite(ChopArt.TEX_GLOW, ix + k * itemSize * 0.45f + len * 0.12f, iy - len * 0.33f, itemSize * (k == 0 ? 1f : 0.55f), len,
                            20f, alpha((k == 0 ? 0.45f : 0.28f) * shafts * sceneAlpha) | FleeceEnding.GOLD_LIGHT);
                }
            }
            // The soft landing: a puff of fluff spreading out and fading.
            float puff = t >= FleeceEnding.ARRIVE ? (t - FleeceEnding.ARRIVE) / 900f : -1f;
            if (puff >= 0f && puff < 1f) {
                for (int k = 0; k < 12; k++) {
                    double a = k * Math.PI * 2 / 12 + rnd(k, 3);
                    float rr = itemSize * (0.35f + 0.55f * (1f - (1f - puff) * (1f - puff)));
                    float px = ix + (float) Math.cos(a) * rr, py = iy + itemSize * 0.35f + (float) Math.sin(a) * rr * 0.35f - puff * itemSize * 0.15f;
                    glow(c, px, py, itemSize * (0.25f + 0.2f * puff), 0xFFF6DC, 0.55f * (1f - puff));
                }
            }
            ChopScene.showItem(c, ix, iy, itemSize * e.squash(t) * e.cubeGrow(t), cubeA * sceneAlpha, 32f + e.spin(t) * 57.3f * 0.08f);
            float flash = e.squareFlash(t);
            if (flash > 0.01f) {
                glow(c, ix, iy, itemSize * 2.6f * (1.3f - 0.3f * flash), FleeceEnding.GOLD_LIGHT, 0.85f * flash);
                c.sprite(ChopArt.TEX_RING, ix, iy, itemSize * 3.2f * (1f - flash), itemSize * 3.2f * (1f - flash), 0f, alpha(0.7f * flash * sceneAlpha) | FleeceEnding.GOLD_LIGHT);
            }
            float sheen = e.sheen(t);
            if (sheen >= 0f) {
                float sx = ix + (sheen - 0.5f) * itemSize * 0.9f;
                c.sprite(ChopArt.TEX_GLOW, sx, iy, itemSize * 0.22f, itemSize * 0.95f, 25f, alpha(0.55f * (float) Math.sin(sheen * Math.PI) * sceneAlpha) | 0xFFFFFF);
            }
            // Sparkles popping round it, one after another.
            if (t >= FleeceEnding.ARRIVE) {
                for (int k = 0; k < 9; k++) {
                    long at = FleeceEnding.ARRIVE + 60 + k * 110L;
                    float u = (t - at) / 520f;
                    if (u < 0f || u > 1f) continue;
                    double a = rnd(k, 21) * Math.PI * 2;
                    float rr = itemSize * (0.55f + 0.35f * rnd(k, 23));
                    sparkle(c, ix + (float) Math.cos(a) * rr, iy + (float) Math.sin(a) * rr * 0.8f, itemSize * 0.22f * (float) Math.sin(u * Math.PI), FleeceEnding.GOLD_LIGHT);
                }
            }
            glow(c, ix, iy, itemSize * 1.8f, FleeceEnding.GOLD, 0.2f * cubeA);
            specialText(tl, t, ix, iy + itemSize * 0.62f + 6f * textScale(), 0xF2D27A, 0xF0E2C0, c);
        }
    }

    /**
     * The Golden Fleece's ram, drawn in the sky star by star, a line at a time, then its stars
     * streaming down into the spindle as the winding starts. Always square to the camera.
     */
    private static void ram(FleeceEnding e, long t, ChopScene.Canvas c) {
        if (t < FleeceEnding.SKY_IN || t > FleeceEnding.WIND + 1000) return;
        float rx = cy, rz = sy;
        float sky = e.skyAlpha(t);
        if (sky > 0.01f) {
            for (int k = 0; k < FleeceEnding.LINES.length; k++) {
                float part = e.line(k, t);
                if (part <= 0f) continue;
                float[] a = e.starAt(FleeceEnding.LINES[k][0], t, rx, rz), b = e.starAt(FleeceEnding.LINES[k][1], t, rx, rz);
                project(a[0], a[1], a[2]);
                float ax = P[0], ay = P[1];
                project(a[0] + (b[0] - a[0]) * part, a[1] + (b[1] - a[1]) * part, a[2] + (b[2] - a[2]) * part);
                c.line(ax, ay, P[0], P[1], Math.max(2.5f, size * 0.45f), alpha(0.18f * sky * sceneAlpha) | FleeceEnding.GOLD);
                c.line(ax, ay, P[0], P[1], Math.max(1f, size * 0.14f), alpha(0.75f * sky * sceneAlpha) | FleeceEnding.GOLD_LIGHT);
            }
        }
        for (int i = 0; i < FleeceEnding.STARS.length; i++) {
            float lit = e.star(i, t);
            if (lit <= 0.01f) continue;
            float[] p = e.starAt(i, t, rx, rz);
            project(p[0], p[1], p[2]);
            float tw = 0.75f + 0.25f * (float) Math.sin(t / 130.0 + i * 1.9);
            glow(c, P[0], P[1], 4f * size * lit, FleeceEnding.GOLD, 0.45f * lit);
            sparkle(c, P[0], P[1], 1.3f * size * lit * tw, FleeceEnding.GOLD_LIGHT);
        }
    }

    /** The spindle the fleece is wound on: a wooden shaft through the ball, a golden whorl below it, turning. */
    private static void spindle(FleeceEnding e, long t, ChopScene.Canvas c) {
        float s = e.spindle(t);
        if (s <= 0.01f) return;
        float turn = e.spin(t);
        String[] tex = all(ChopScene.SHEET);
        int[][] wood = {ChopArt.BLOCK_SIDE, ChopArt.BLOCK_SIDE, ChopArt.BLOCK_TOP, ChopArt.BLOCK_TOP, ChopArt.BLOCK_SIDE, ChopArt.BLOCK_SIDE};
        float y = FleeceEnding.SPIN_Y;
        inkWidth = Math.max(1.3f, size / 3.5f);
        box(0f, y, 0f, 0f, turn, 0f, 0.35f * s, 6.5f * s, 0.35f, tex, wood, 1f, 1f, 1f, 0f, s, c);
        box(0f, y - 5.2f * s, 0f, 0f, turn, 0f, 2.3f * s, 0.45f * s, 2.3f * s, tex, wood, 1f, 1f, 1f, 0f, s, c);
        inkWidth = 0f;
        box(0f, y, 0f, 0f, turn, 0f, 0.35f * s, 6.5f * s, 0.35f, tex, wood, 0.9f, 0.75f, 0.55f, 0f, s, c);
        box(0f, y - 5.2f * s, 0f, 0f, turn, 0f, 2.3f * s, 0.45f * s, 2.3f * s, tex, wood, 1f, 0.82f, 0.35f, 0.25f, s, c);
    }

    /** The fibres - those behind the ball or in front of it - with a glint running along each. */
    private static void fibres(FleeceEnding e, long t, float ballDepth, boolean front, ChopScene.Canvas c) {
        float core = Math.max(1f, size * 0.16f), halo = Math.max(2.5f, size * 0.55f);
        for (int k = 0; k < FleeceEnding.FIBRES; k++) {
            float[][] pts = e.fibre(k, t);
            if (pts == null) continue;
            float px = 0f, py = 0f;
            int glintAt = (int) ((t / 90 + k * 5) % (FleeceEnding.POINTS * 2));
            for (int j = 0; j < pts.length; j++) {
                project(pts[j][0], pts[j][1], pts[j][2]);
                float x = P[0], y = P[1];
                boolean isFront = depth(pts[j][0], pts[j][1], pts[j][2]) > ballDepth;
                if (j > 0 && isFront == front) {
                    c.line(px, py, x, y, halo, alpha(0.16f * sceneAlpha) | FleeceEnding.GOLD);
                    c.line(px, py, x, y, core, alpha(0.95f * sceneAlpha) | (k % 3 == 0 ? FleeceEnding.GOLD_LIGHT : FleeceEnding.GOLD));
                    if (j == glintAt) glow(c, x, y, size * 1.6f, FleeceEnding.GOLD_LIGHT, 0.85f);
                }
                px = x;
                py = y;
            }
        }
    }

    /** A four-pointed glint. */
    private static void sparkle(ChopScene.Canvas c, float x, float y, float r, int rgb) {
        if (r < 0.5f) return;
        c.line(x - r, y, x + r, y, Math.max(1f, r * 0.12f), alpha(0.9f * sceneAlpha) | rgb);
        c.line(x, y - r, x, y + r, Math.max(1f, r * 0.12f), alpha(0.9f * sceneAlpha) | rgb);
        glow(c, x, y, r * 1.2f, 0xFFFFFF, 0.8f);
    }

    // ================================================================== the Mythological Dye: "The Whole Hunt"

    private static final int[] PAINTS = {HuntEnding.PAINT, HuntEnding.PAINT_LIGHT, HuntEnding.PAINT_DEEP, 0x7E7E14};

    /** One paint pixel: a square of {@code u} screen pixels at (x, y). */
    private static void square(ChopScene.Canvas c, float x, float y, float u, int argb) {
        c.fill(Math.round(x - u / 2f), Math.round(y - u / 2f), u, u, argb);
    }

    /** The screen grid for the paint's pixels: a little under a head texel. */
    private static float paintPx() {
        return Math.max(1f, Math.round(size * 0.6f));
    }

    /** Behind the board: the moon, and the ring's heads on the far side of the block. */
    private static void huntBehind(ChopTimeline tl, long t, ChopScene.Canvas c) {
        HuntEnding e = tl.hunt();
        huntMoon(e, t, c);
        float ref = depth(0f, HuntEnding.RING_Y, 0f);
        for (int k = 0; k < HuntEnding.HEADS; k++) {
            float[] q = e.head(k, t);
            if (q != null && depth(q[0], q[1], q[2]) < ref && q[1] < 2f * ChopTimeline.HEAD + 4f) ringHead(e, k, q, t, c);
        }
    }

    /** On the board's top: the cracks the olive light shows through. */
    private static void huntCracks(ChopTimeline tl, long t, ChopScene.Canvas c) {
        HuntEnding e = tl.hunt();
        float run = e.cracks(t);
        if (run <= 0.01f) return;
        java.util.Random r = new java.util.Random(tl.seed() * 7 + 19);
        float px = 0.5f;
        float pulse = 0.75f + 0.25f * (float) Math.sin(t / 60.0);
        int col = mix(HuntEnding.GLOW, 0xFFFFFF, 0.25f * pulse);
        for (int k = 0; k < 9; k++) {
            double a = k * Math.PI * 2 / 9 + r.nextFloat() * 0.5;
            float x = 0f, z = 0f;
            int n = 7 + r.nextInt(6);
            for (int j = 0; j < n && j < run * (n + 1); j++) {
                a += (r.nextFloat() - 0.5f) * 0.8f;
                x += (float) Math.cos(a) * px * 1.3f;
                z += (float) Math.sin(a) * px * 1.3f;
                if (Math.abs(x) > ChopTimeline.BLOCK_W - 0.5f || Math.abs(z) > ChopTimeline.BLOCK_D - 0.5f) break;
                float sx = (float) Math.floor(x / px) * px, sz = (float) Math.floor(z / px) * px;
                worldQuad(ChopScene.SHEET, ChopArt.WHITE, sx, 0.03f, sz, 0f, 0f, px, px, 0f, 0f, alpha(0.95f * pulse) | col, c);
            }
        }
    }

    /** The board and the halves taking the paint: rain on the board, runs down the halves. */
    static ChopScene.Gild huntGild(ChopTimeline tl, long t) {
        if (t < HuntEnding.GEYSER) return null;
        HuntEnding e = tl.hunt();
        return (x, y, z, cell, now, sx, sy) -> {
            boolean wet = y <= 0.08f ? (y > -0.08f && e.stained(x, z, now)) : e.dripped(x, y, z, now);
            return wet ? 0x40000000 | HuntEnding.PAINT : 0;
        };
    }

    /** The parade's heads, a geyser of paint, the arrow, the bursts, the shockwave and the dye. */
    private static void hunt(ChopTimeline tl, long t, int w, int h, ChopScene.Canvas c) {
        HuntEnding e = tl.hunt();
        float u = paintPx();

        // The geyser, out of the split.
        float gh = e.geyser(t);
        if (gh > 0.5f) {
            project(0f, gh * 0.5f, 0f);
            glow(c, P[0], P[1], gh * 0.9f * size, HuntEnding.GLOW, 0.4f);
            java.util.Random r = new java.util.Random(t / 40 * 31 + tl.seed());
            float gu = Math.max(2f, Math.round(size * 0.85f));
            for (float y = 0f; y < gh; y += 0.7f) {
                float wob = 0.45f * (float) Math.sin(y * 0.6f + t / 80.0);
                // Wider at the foot, where it bursts out of the split.
                int half = y < 3f ? 4 : 3;
                for (int i = -half; i <= half; i++) {
                    if (Math.abs(i) == half && r.nextFloat() < 0.55f) continue;
                    project(i * 0.7f + wob, y, 0f);
                    int col = Math.abs(i) <= 1 ? (r.nextFloat() < 0.5f ? 0xE4E49A : HuntEnding.PAINT_LIGHT) : PAINTS[r.nextInt(PAINTS.length)];
                    square(c, P[0], P[1], gu, alpha(sceneAlpha) | col);
                }
            }
            // The crown of spray at the top, falling back.
            for (int i = 0; i < 44; i++) {
                float life = ((t / 1000f) * 1.4f + i * 0.137f) % 1f;
                double a = i * 2.399;
                float rr = 1.5f + 8f * life;
                project((float) Math.cos(a) * rr, gh + 4f * life - 10f * life * life, (float) Math.sin(a) * rr);
                square(c, P[0], P[1], u * (i % 3 == 0 ? 2f : 1f), alpha((1f - life) * sceneAlpha) | PAINTS[i % 3]);
            }
        }
        // The rain.
        for (int i = 0; i < HuntEnding.RAIN; i++) {
            float[] q = e.rain(i, t);
            if (q == null) continue;
            project(q[0], q[1], q[2]);
            pixDrop(c, P[0], P[1], 0.75f * size, PAINTS[i % 3]);
        }

        // The heads in front of the block, and those still flying out of the geyser.
        float ref = depth(0f, HuntEnding.RING_Y, 0f);
        for (int k = 0; k < HuntEnding.HEADS; k++) {
            float[] q = e.head(k, t);
            if (q == null || (depth(q[0], q[1], q[2]) < ref && q[1] < 2f * ChopTimeline.HEAD + 4f)) continue;
            ringHead(e, k, q, t, c);
        }
        // Each landing's splash.
        for (int k = 0; k < HuntEnding.HEADS; k++) {
            long land = e.landing(k);
            float sec = (t - land) / 1000f;
            if (sec < 0f || sec > 0.5f || t >= e.hit(k)) continue;
            float[] p = e.ringPlace(k, land);
            int n = k == HuntEnding.KING ? 22 : 12;
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n;
                float rr = (e.half(k) + 1f) * (0.6f + 1.6f * sec / 0.5f);
                project(p[0] + (float) Math.cos(a) * rr, p[1] - e.half(k) + 4f * sec - 14f * sec * sec, p[2] + (float) Math.sin(a) * rr);
                square(c, P[0], P[1], u, alpha((1f - sec / 0.5f) * sceneAlpha) | PAINTS[i % 3]);
            }
        }

        // The arrow, and the bursts it leaves.
        for (int k = 0; k < HuntEnding.HEADS; k++) {
            float[] b = e.burst(k, t);
            if (b == null) continue;
            project(b[0], b[1], b[2]);
            if (b[3] < 0.12f) glow(c, P[0], P[1], (k == HuntEnding.KING ? 16f : 9f) * size, 0xFFFFFF, 0.8f * (1f - b[3] / 0.12f));
            int n = k == HuntEnding.KING ? 60 : 30;
            glow(c, P[0], P[1], (k == HuntEnding.KING ? 14f : 8f) * size, HuntEnding.GLOW, 0.5f * (1f - b[3] / 0.9f));
            java.util.Random r = new java.util.Random(k * 977L + tl.seed());
            for (int i = 0; i < n; i++) {
                float vx = (r.nextFloat() - 0.5f) * 26f, vy = 4f + r.nextFloat() * 12f, vz = (r.nextFloat() - 0.5f) * 26f;
                float s = b[3];
                project(b[0] + vx * s, b[1] + vy * s - 22f * s * s, b[2] + vz * s);
                square(c, P[0], P[1], u * (i % 3 == 0 ? 2 : 1.4f), alpha((1f - s / 0.9f) * sceneAlpha) | PAINTS[i % 4]);
            }
        }
        float[] ar = e.arrow(t);
        if (ar != null) arrow(e, ar, t, c);

        // Everything pulled down into the split.
        float suck = e.suck(t);
        if (suck > 0f && t < HuntEnding.SHOCK) {
            for (int i = 0; i < 60; i++) {
                double a = i * 2.399 + suck * 7.0 * tl.side();
                float rr = (8f + 14f * ((i * 37) % 100) / 100f) * (1f - suck);
                project((float) Math.cos(a) * rr, 2f + (i % 7) * 2.5f * (1f - suck), (float) Math.sin(a) * rr);
                square(c, P[0], P[1], u, alpha(sceneAlpha) | PAINTS[i % 4]);
            }
            project(0f, 2f, 0f);
            glow(c, P[0], P[1], (6f + 14f * suck) * size, HuntEnding.GLOW, 0.5f * suck);
        }

        // The shockwave and the dye in its column.
        float sh = e.shock(t);
        project(0f, 2f, 0f);
        float ox = P[0], oy = P[1];
        if (sh >= 0f && sh < 1f) {
            float rad = (10f + 70f * sh) * size;
            c.sprite(ChopArt.TEX_RING, ox, oy, rad, rad * Math.max(0.3f, Math.abs(sp)) * 1.4f, 0f, alpha(0.9f * (1f - sh) * sceneAlpha) | HuntEnding.PAINT_LIGHT);
            c.sprite(ChopArt.TEX_RING, ox, oy, rad * 0.7f, rad * 0.7f, 0f, alpha(0.6f * (1f - sh) * sceneAlpha) | 0xFFFFFF);
            for (int i = 0; i < 48; i++) {
                double a = i * Math.PI * 2 / 48;
                float rr = 4f + 40f * sh;
                project((float) Math.cos(a) * rr, 1f + 2f * sh, (float) Math.sin(a) * rr);
                square(c, P[0], P[1], u * 2f, alpha((1f - sh) * sceneAlpha) | PAINTS[i % 3]);
            }
        }
        float col = e.column(t);
        if (col > 0.01f) {
            project(0f, HuntEnding.ITEM_Y + 14f, 0f);
            float top = P[1];
            c.sprite(ChopArt.TEX_GLOW, ox, (top + oy) / 2f, 9f * size, Math.abs(oy - top) + 16f * size, 0f, alpha(0.85f * col * sceneAlpha) | HuntEnding.PAINT_LIGHT);
            c.sprite(ChopArt.TEX_GLOW, ox, (top + oy) / 2f, 3f * size, Math.abs(oy - top) + 10f * size, 0f, alpha(0.75f * col * sceneAlpha) | 0xF4F4C8);
        }
        float[] it = e.item(t);
        if (it != null) {
            project(0f, it[0], 0f);
            float ix = P[0], iy = P[1];
            float itemSize = Math.max(24f, 10f * size) * ChopScene.showScale;
            { float[] fitted = fit(iy, itemSize); iy = fitted[0]; itemSize = fitted[1]; }
            glow(c, ix, iy, 16f * size, HuntEnding.GLOW, 0.45f * it[1]);
            float burst = tl.arrivalBurst(t);
            if (burst > 0.01f) {
                glow(c, ix, iy, 30f * size * (1.3f - 0.3f * burst), 0xFFFFFF, 0.8f * burst);
                c.sprite(ChopArt.TEX_RING, ix, iy, 40f * size * (1f - burst), 40f * size * (1f - burst), 0f, alpha(0.8f * burst * sceneAlpha) | HuntEnding.PAINT_LIGHT);
            }
            ChopScene.showItem(c, ix, iy, itemSize * (0.6f + 0.4f * it[1]), it[1] * sceneAlpha, 32f + (t - HuntEnding.SHOCK) / 1000f * 60f);
            specialText(tl, t, ix, iy + itemSize * 0.62f + 6f * textScale(), 0xD2D27A, 0xE2E2B8, c);
        }
        // Paint running down from the top of the screen once it is shown.
        topDrips(tl, t, w, c);
    }

    /** A head of the bestiary: the real skin, base and hat, outlined, and painted olive from the bottom up as it lands. */
    private static void ringHead(HuntEnding e, int k, float[] q, long t, ChopScene.Canvas c) {
        String skin = c.bestiarySkin(HuntEnding.BESTIARY[k]);
        float half = q[6];
        float paint = e.painted(k, t);
        String[] tex = all(skin == null ? ChopScene.SHEET : skin);
        int[][] base = skin == null ? new int[][]{ChopArt.FLESH, ChopArt.FLESH, ChopArt.FLESH, ChopArt.FLESH, ChopArt.FLESH, ChopArt.FLESH} : ChopScene.HEAD;
        float hh = half * 1.0625f;
        inkWidth = Math.max(1.5f, size / 3.5f);
        box(q[0], q[1], q[2], q[4], q[3], q[5], hh, hh, hh, tex, base, 1f, 1f, 1f, 0f, q[7], c);
        inkWidth = 0f;
        if (paint > 0f) {
            float bottom = q[1] - half, height = 2f * half;
            gild = (x, y, z, cell, now, sx, sy) -> {
                int hsh = cell * 0x9E3779B1;
                hsh ^= hsh >>> 15;
                float noise = ((hsh >>> 8) & 255) / 255f;
                float f = (y - bottom) / height;
                if (f > paint * 1.2f - 0.15f * noise) return 0;
                // The front of the paint still wet and shining, the rest settled.
                boolean front = f > paint * 1.2f - 0.15f * noise - 0.12f && paint < 1f;
                // Mostly the head's own skin through it: every creature still reads under the olive.
                return (front ? 0xA0000000 : 0xF8000000) | (front ? HuntEnding.PAINT_LIGHT : HuntEnding.PAINT);
            };
        }
        box(q[0], q[1], q[2], q[4], q[3], q[5], half, half, half, tex, base, 1f, 1f, 1f, 0f, q[7], c);
        if (skin != null) box(q[0], q[1], q[2], q[4], q[3], q[5], hh, hh, hh, tex, ChopScene.HAT, 1f, 1f, 1f, 0f, q[7], c);
        gild = null;
    }

    /** Diana's moon rising behind the ring: her moon head from the Relic, still. */
    private static void huntMoon(HuntEnding e, long t, ChopScene.Canvas c) {
        float my = e.moonY(t);
        float a = e.moonAlpha(t);
        if (Float.isNaN(my) || a <= 0.01f) return;
        float mz = HuntEnding.MOON_Z, half = HuntEnding.MOON_HALF;
        project(0f, my, mz);
        glow(c, P[0], P[1], half * 7f * size, ChopTimeline.MOONSTONE, 0.35f * a);
        glow(c, P[0], P[1], half * 3.2f * size, ChopTimeline.MOONSTONE, 0.5f * a);
        float spin = 0.6f + (t - HuntEnding.MOON) / 1000f * 0.25f;
        float hh = half * (1f + 1f / 16f);
        inkWidth = Math.max(1.5f, half * size / 11f);
        box(0f, my, mz, 0.25f, spin, 0.12f, half, half, half, all(ChopScene.SHEET), ChopArt.MOON_FACES, 1f, 1f, 1f, 0f, a, c);
        inkWidth = 0f;
        box(0f, my, mz, 0.25f, spin, 0.12f, half, half, half, all(ChopScene.SHEET), ChopArt.MOON_FACES, 0.92f, 0.93f, 1f, 0.08f, a, c);
        int[][] hat = {ChopArt.MOON_HAT, ChopArt.MOON_HAT, ChopArt.MOON_HAT, ChopArt.MOON_HAT, ChopArt.MOON_HAT, ChopArt.MOON_HAT};
        box(0f, my, mz, 0.25f, spin, 0.12f, hh, hh, hh, all(ChopScene.SHEET), hat, 1f, 1f, 1f, 0.05f, a, c);
    }

    /** The silver arrow: a shaft of pixels with a bright head, and its trail, pointing the way it flies. */
    private static void arrow(HuntEnding e, float[] ar, long t, ChopScene.Canvas c) {
        float u = Math.max(2f, Math.round(size * 0.7f));
        // The trail: where it was over the last third of a second, a line of silver pixels fading.
        float[] last = null;
        for (int i = 0; i <= 16; i++) {
            float[] p = e.arrow(t - i * 20L);
            if (p == null) break;
            project(p[0], p[1], p[2]);
            if (last != null) {
                int steps = Math.max(1, Math.round((float) Math.hypot(P[0] - last[0], P[1] - last[1]) / u));
                for (int j = 0; j < steps; j++) {
                    float f = j / (float) steps;
                    square(c, last[0] + (P[0] - last[0]) * f, last[1] + (P[1] - last[1]) * f, u,
                            alpha(0.7f * (1f - i / 17f) * sceneAlpha) | (i < 4 ? 0xE6ECFF : ChopTimeline.MOONSTONE));
                }
            }
            last = new float[]{P[0], P[1]};
        }
        project(ar[0], ar[1], ar[2]);
        float hx = P[0], hy = P[1];
        project(ar[0] - ar[3] * 7f, ar[1] - ar[4] * 7f, ar[2] - ar[5] * 7f);
        float tx = P[0], ty = P[1];
        float len = (float) Math.hypot(hx - tx, hy - ty);
        int n = Math.max(2, Math.round(len / u));
        for (int i = 0; i <= n; i++) {
            float f = i / (float) n;
            square(c, tx + (hx - tx) * f, ty + (hy - ty) * f, u, alpha(sceneAlpha) | (i >= n - 1 ? 0xFFFFFF : 0xD8DEEE));
        }
        // Fletching: two olive pixels either side of the tail.
        float nx = -(hy - ty) / Math.max(1f, len), ny = (hx - tx) / Math.max(1f, len);
        square(c, tx + nx * u, ty + ny * u, u, alpha(sceneAlpha) | HuntEnding.PAINT_LIGHT);
        square(c, tx - nx * u, ty - ny * u, u, alpha(sceneAlpha) | HuntEnding.PAINT_LIGHT);
        glow(c, hx, hy, 7f * size, 0xE6ECFF, 0.8f);
    }

    /** Paint running down from the top edge of the screen once the dye is shown, drops falling off the ends. */
    private static void topDrips(ChopTimeline tl, long t, int w, ChopScene.Canvas c) {
        long at = tl.arrive() - 200;
        if (t < at) return;
        float u = Math.max(2f, Math.round(size * 0.7f));
        java.util.Random r = new java.util.Random(tl.seed() * 3 + 41);
        int cols = Math.max(12, Math.round(w / (u * 5f)));
        float sec = (t - at) / 1000f;
        for (int i = 0; i < cols; i++) {
            float x = (i + 0.2f + 0.6f * r.nextFloat()) * w / cols;
            float delay = r.nextFloat() * 1.2f, speed = u * (6f + 10f * r.nextFloat());
            float max = u * (3f + 9f * r.nextFloat());
            float s = sec - delay;
            if (s <= 0f) continue;
            float len = Math.min(max, speed * s);
            int col = PAINTS[i % 3];
            for (float y = 0f; y < len; y += u) square(c, x, y + u / 2f, u, alpha(0.95f * sceneAlpha) | col);
            // The bulb at the end, and a drop letting go now and then.
            square(c, x, len + u / 2f, u * 1.5f, alpha(0.95f * sceneAlpha) | col);
            if (len >= max) {
                float fall = ((s - max / speed) % 1.4f) / 1.4f;
                float dy = len + fall * fall * 400f;
                pixDrop(c, x, dy, u * 1.6f, col);
            }
        }
    }

    // ================================================================== the Fateful Stinger

    private static void sting(ChopTimeline tl, long t, int w, int h, ChopScene.Canvas c) {
        StingEnding e = tl.sting();
        int venom = StingEnding.VENOM;
        // The barb and its splash go down with the board.
        yShift = tl.blockFall(t)[0];
        float becomes = e.becomes(t);
        if (t < StingEnding.TURN + 150) {
            float[] tip = e.tip(t);
            float shown = e.shown(t);
            if (shown > 0.01f && tip[1] > -3f) {
                project(0f, 0.3f, 0f);
                glow(c, P[0], P[1], 14f * size, venom, 0.35f * (1f - becomes) * ease01((t - ChopTimeline.SPLIT - 100) / 300f));
                barb(tip, e.tilt(t), shown, 1f - becomes, c);
            }
            // The bead on the point: a pixel drop, the one bright thing on the barb.
            float bead = e.bead(t);
            if (bead > 0.02f && becomes < 0.5f) {
                project(tip[0], tip[1] - 0.35f - 0.35f * bead, tip[2]);
                pixDrop(c, P[0], P[1], (0.5f + 0.7f * bead) * size, StingEnding.VENOM);
            }
        }
        float[] drip = e.drip(t);
        if (drip != null) {
            project(drip[0], drip[1], drip[2]);
            pixDrop(c, P[0], P[1], 0.6f * size, StingEnding.VENOM);
        }
        // The splash: square pixels, the game's own particles, landing and lying on the board.
        int[] shades = {StingEnding.VENOM_DARK, StingEnding.VENOM, StingEnding.VENOM_LIGHT};
        for (int k = 0; k < StingEnding.SPLASH; k++) {
            float[] q = e.splash(k, t);
            if (q == null) continue;
            float fade = q[5] > 0f ? 1f - ease01((t - StingEnding.IMPACT - 700) / 500f) : 1f;
            if (fade <= 0.01f) continue;
            project(q[0], q[1], q[2]);
            float px = Math.max(1f, q[3] * size);
            c.fill(P[0] - px / 2f, P[1] - px / 2f, px, q[5] > 0f ? Math.max(1f, px * 0.5f) : px, alpha(fade * sceneAlpha) | shades[(int) q[4]]);
        }
        yShift = 0f;

        // The drop: the barb turns into it at the top of its rise.
        if (becomes > 0.01f) {
            project(0f, StingEnding.HERO_Y, StingEnding.STAB_Z * (1f - ease01((t - StingEnding.TURN) / 300f)));
            float ix = P[0], iy = P[1];
            float turned = t >= StingEnding.TURN ? (float) Math.exp(-(t - StingEnding.TURN) / 150.0) : 0f;
            if (turned > 0.01f) glow(c, ix, iy, 20f * size, 0xFFFFFF, 0.75f * turned);
            float rays = tl.rays(t);
            if (rays > 0.01f) {
                float spin = (t - tl.arrive()) / 40f;
                c.sprite(ChopArt.TEX_RAYS, ix, iy, 24f * size * rays, 24f * size * rays, spin, alpha(0.5f * rays * sceneAlpha) | ChopTimeline.VIRIDIAN);
            }
            glow(c, ix, iy, 13f * size, venom, 0.45f * becomes);
            float itemSize = Math.max(24f, 9f * size) * ChopScene.showScale;
            { float[] fitted = fit(iy, itemSize); iy = fitted[0]; itemSize = fitted[1]; }
            ChopScene.showItem(c, ix, iy, itemSize * (0.6f + 0.4f * becomes), becomes * sceneAlpha, 32f + (t - StingEnding.TURN) / 1000f * 40f);
            specialText(tl, t, ix, iy + itemSize * 0.62f + 6f * textScale(), mix(ChopTimeline.VIRIDIAN, 0xFFFFFF, 0.5f), 0xBFE8D2, c);
        }
    }

    /**
     * The barb: its spike, point first, the bulb above it and two tail plates curling up behind -
     * all real chitin boxes, outlined as one. Drawn back a little (the wind-up) about its point.
     */
    private static void barb(float[] tip, float tilt, float shown, float alpha, ChopScene.Canvas c) {
        // The camera's right and its way towards the viewer, level.
        float[] R = {cy, 0f, sy}, F = {-sy, 0f, cy};
        List<float[]> pieces = new ArrayList<>();
        // Big enough to read at a glance: a barb half the head's height.
        float g = (0.3f + 0.7f * shown) * 1.45f;
        // Down the spike to the point; each part further up is turned a little more round the curl.
        float[] at = tip.clone();
        float[][] parts = {{2, 0f, 1.2f, 0.45f}, {1, 0.1f, 1.3f, 1.3f}, {0, 0.55f, 1.3f, 1.05f}, {0, 1.05f, 1.2f, 0.9f}};
        for (float[] part : parts) {
            float a = tilt + part[1];
            // Pointing towards the tip: down, swung towards the viewer's right by the angle.
            float[] d = {R[0] * (float) Math.sin(a), -(float) Math.cos(a), R[2] * (float) Math.sin(a)};
            float half = part[2] * g;
            float cx = at[0] - d[0] * half, cy = at[1] - d[1] * half, cz = at[2] - d[2] * half;
            if (cy > -0.5f) pieces.add(new float[]{0, part[0], cx, cy, cz, d[0], d[1], d[2], F[0], F[1], F[2], part[3] * g, half, part[3] * g * 0.92f, alpha});
            at = new float[]{cx - d[0] * half * 0.85f, cy - d[1] * half * 0.85f, cz - d[2] * half * 0.85f};
        }
        for (float[] q : pieces) q[0] = depth(q[2], q[3], q[4]);
        pieces.sort((a, b) -> Float.compare(a[0], b[0]));
        String[] tex = all(ChopScene.SHEET);
        inkWidth = Math.max(1.4f, size / 3.2f);
        for (float[] q : pieces) piece(q, tex, c);
        inkWidth = 0f;
        for (float[] q : pieces) piece(q, tex, c);
    }

    private static void piece(float[] q, String[] tex, ChopScene.Canvas c) {
        float[] Y = {q[5], q[6], q[7]}, X = {q[8], q[9], q[10]};
        float[] Z = {X[1] * Y[2] - X[2] * Y[1], X[2] * Y[0] - X[0] * Y[2], X[0] * Y[1] - X[1] * Y[0]};
        int[][] rects = switch ((int) q[1]) {
            case 0 -> new int[][]{ChopArt.CHITIN, ChopArt.CHITIN, ChopArt.CHITIN_CAP, ChopArt.CHITIN_CAP, ChopArt.CHITIN, ChopArt.CHITIN};
            case 1 -> new int[][]{ChopArt.BULB, ChopArt.BULB, ChopArt.CHITIN_CAP, ChopArt.CHITIN_CAP, ChopArt.BULB, ChopArt.BULB};
            default -> new int[][]{ChopArt.SPIKE, ChopArt.SPIKE, ChopArt.SPIKE, ChopArt.SPIKE, ChopArt.SPIKE, ChopArt.SPIKE};
        };
        boxAxes(q[2], q[3], q[4], X, Y, Z, q[11], q[12], q[13], tex, rects, 1f, 1f, 1f, 0f, q[14], c);
    }

    /** A drop of liquid as pixel art - a small hard-edged drop sprite, tinted - centred on (x, y). */
    static void pixDrop(ChopScene.Canvas c, float x, float y, float w, int rgb) {
        c.sprite(ChopArt.TEX_PIXDROP, x, y, w, w * 1.25f, 0f, alpha(sceneAlpha) | rgb);
    }

    /** On the board, under the head: the crack the stab left, and the venom soaking into the wood. */
    private static void stingDecal(ChopTimeline tl, long t, ChopScene.Canvas c) {
        StingEnding e = tl.sting();
        float crack = e.crack(t);
        if (crack <= 0.01f) return;
        float x0 = StingEnding.STAB_X, z0 = StingEnding.STAB_Z;
        java.util.Random r = new java.util.Random(tl.seed() * 7 + 11);
        float px = 0.5f;
        for (int k = 0; k < 7; k++) {
            double a = k * Math.PI * 2 / 7 + r.nextFloat() * 0.5;
            float x = x0, z = z0;
            int n = 3 + r.nextInt(4);
            for (int j = 0; j < n && j < crack * (n + 1); j++) {
                a += (r.nextFloat() - 0.5f) * 0.9f;
                x += (float) Math.cos(a) * px;
                z += (float) Math.sin(a) * px;
                float sx = (float) Math.floor(x / px) * px, sz = (float) Math.floor(z / px) * px;
                worldQuad(ChopScene.SHEET, ChopArt.WHITE, sx, 0.03f, sz, 0f, 0f, px, px, 0f, 0f, alpha(0.9f) | 0x2A1A0E, c);
            }
        }
        float r0 = 1.7f * e.stain(t);
        if (r0 > 0.05f) {
            worldQuad(ChopArt.TEX_PIXSPLAT, new int[]{0, 0, 16, 16}, x0 - r0, 0.04f, z0 - r0, 0f, 0f, 2 * r0, 2 * r0, 0f, 0f,
                    alpha(0.95f) | StingEnding.VENOM, c);
        }
    }
}
