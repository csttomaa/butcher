package com.butcher.render;

import com.butcher.anim.ChopTimeline;

import java.util.Random;

import static com.butcher.render.ChopScene.P;
import static com.butcher.render.ChopScene.alpha;
import static com.butcher.render.ChopScene.ease01;
import static com.butcher.render.ChopScene.project;

/**
 * The Chopping Block filling the screen (the user, 2026-10-02: the sides felt empty; of the ideas, "the
 * screen filling option" - visual only, nothing to read, and no walls, since it plays over the real game).
 *
 * <ul>
 *   <li><b>Debris</b> - every blow throws pixel splinters of the oak and hot sparks out from where it
 *       landed, as far as the screen's edges, more each blow and the most at the split. Seeded only, on
 *       the scene clock: the same whatever dropped (ChopTest), and the Dye's stopped time stops it too.
 *   <li><b>Rays</b> - a win's light reaching the edges: hard-edged pixel rays in the drop's colour from
 *       the split, stepped down in strength as they go - never a fill over the screen.
 *   <li><b>Motes</b> - sparse squares of the same colour drifting up across the whole width until the end.
 *   <li><b>Ash</b> - a bust's, spreading a little wider than the crumble, and short.
 * </ul>
 * The Relic and the Dye take the whole screen with their own sky and freeze, so they get no rays or motes.
 * Pixel art only: squares on a grid one head-texel across, and hard-edged lines.
 */
final class ChopFill {
    private ChopFill() {}

    private static final int[] WOOD = {0x8A5A2B, 0xB98546, 0x5E3A1A, 0xA06A34};
    private static final int[] SPARK = {0xFFE9A8, 0xFFC35A, 0xFFF6D8};
    private static final int[] DEBRIS = {12, 16, 20, 44};
    private static final float LIFE = 1.15f;

    /** One head-texel on screen - the grid everything here sits on. */
    private static float px() {
        return Math.max(1f, Math.round(ChopScene.size * 0.7f));
    }

    private static boolean hasLight(ChopTimeline tl) {
        return tl.verdict().win() && !tl.unmade();
    }

    // ================================================================== debris: every blow, to the edges

    /** Drawn over the scene. {@code ts} is the scene's clock. */
    static void debris(ChopTimeline tl, long ts, int w, int h, ChopScene.Canvas c) {
        float u = px();
        float reach = (float) Math.hypot(w, h);
        for (int k = 0; k < 4; k++) {
            float sec = (ts - ChopTimeline.blowTime(k)) / 1000f;
            if (sec < 0f || sec > LIFE) continue;
            project(tl.strikeX(k), 2f * ChopTimeline.HEAD + 0.5f, 0f);
            float ox = P[0], oy = P[1];
            Random r = new Random(tl.seed() * 31 + 101 + k);
            float power = k == 3 ? 1.15f : 0.75f + 0.1f * k;
            for (int i = 0; i < DEBRIS[k]; i++) {
                boolean spark = i % 3 == 0;
                // Out and up, to either side - never at the viewer.
                float a = (float) Math.toRadians(-172 + r.nextFloat() * 164);
                float speed = reach * power * (0.45f + r.nextFloat() * 0.55f);
                float x = ox + (float) Math.cos(a) * speed * sec;
                float y = oy + (float) Math.sin(a) * speed * sec + 1.1f * h * sec * sec;
                float life = spark ? 0.55f : LIFE;
                if (sec > life) continue;
                float fade = 1f - ease01((sec - life * 0.6f) / (life * 0.4f));
                int rgb = spark ? SPARK[r.nextInt(SPARK.length)] : WOOD[r.nextInt(WOOD.length)];
                float s = spark ? u : u * (1 + r.nextInt(3));
                square(c, x, y, s, u, alpha(fade * ChopScene.sceneAlpha) | rgb);
            }
        }
    }

    // ================================================================== rays: a win's light, to the edges

    /** Drawn behind the block. */
    static void rays(ChopTimeline tl, long t, long ts, int w, int h, ChopScene.Canvas c) {
        // Only once the drop is being shown - not when the head splits (the user, 2026-10-02).
        long show = tl.arrive() - 150;
        if (!hasLight(tl) || t < show) return;
        float in = ease01((t - show) / 450f);
        // Strong as the drop arrives, then a quieter glow behind it.
        float settle = 1f - 0.4f * ease01((t - tl.arrive() - 900) / 900f);
        float strength = 0.32f * in * settle * ChopScene.sceneAlpha;
        if (strength < 0.01f) return;
        // Out of the drop itself, and only round it: from the block's middle to the screen's edges they ran behind the
        // words too, and read as coming from the whole showcase (the user, 2026-10-11). Where the item is not drawn
        // yet, from the block as before.
        float ox, oy, size;
        if (!Float.isNaN(ChopScene.itemX) && ChopScene.itemSize > 0f) {
            ox = ChopScene.itemX;
            oy = ChopScene.itemY;
            size = ChopScene.itemSize;
        } else {
            project(0f, tl.ending() == ChopTimeline.Ending.HUNT ? com.butcher.anim.HuntEnding.ITEM_Y : ChopTimeline.HEAD, 0f);
            ox = P[0];
            oy = P[1];
            size = 24f;
        }
        int rgb = tl.crackColour(ts) & 0xFFFFFF;
        float u = px();
        float turn = (t - ChopTimeline.SPLIT) / 1000f * 0.10f;
        int n = 12;
        for (int i = 0; i < n; i++) {
            float a = (float) (i * Math.PI * 2 / n) + turn + (i % 2) * 0.09f;
            float width = u * (i % 2 == 0 ? 2 : 1f);
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            // A halo just round the item, 0.85 of its size past its middle; the ones pointing down stop short of the words under it.
            float reach = size * (sa > 0.25f ? 0.3f + 0.4f * (1f - sa) : 0.85f);
            float start = size * 0.3f;
            // Stepped: each band a little weaker, so it reads as pixel light, not a gradient smear.
            int steps = 7;
            for (int sIdx = 0; sIdx < steps; sIdx++) {
                float r0 = start + reach * sIdx / steps, r1 = start + reach * (sIdx + 1) / steps;
                float a0 = strength * (1f - sIdx / (float) steps) * (i % 2 == 0 ? 1f : 0.6f);
                if (a0 < 0.012f) continue;
                c.line(ox + ca * r0, oy + sa * r0, ox + ca * r1, oy + sa * r1, width, alpha(a0) | rgb);
            }
        }
    }

    // ================================================================== motes: the colour across the width

    /** Drawn over everything but the text. */
    static void motes(ChopTimeline tl, long t, int w, int h, ChopScene.Canvas c) {
        // The Dye's are part of its reveal, not of the split.
        long from = tl.ending() == ChopTimeline.Ending.HUNT ? tl.arrive() - 150 : ChopTimeline.SPLIT + 250;
        if (!hasLight(tl) || t < from) return;
        float in = ease01((t - from) / 500f);
        float a = in * ChopScene.sceneAlpha;
        if (a < 0.02f) return;
        int rgb = lighten(tl.crackColour(t) & 0xFFFFFF, 0.35f);
        float u = px();
        Random r = new Random(tl.seed() * 7 + 3);
        float sec = (t - from) / 1000f;
        for (int i = 0; i < 40; i++) {
            float x0 = r.nextFloat() * w;
            float speed = h * (0.05f + r.nextFloat() * 0.07f);
            float phase = r.nextFloat();
            // Each rises from below the screen and wraps, so the width stays evenly filled.
            float y = h * 1.05f - ((phase * h * 1.1f + speed * sec) % (h * 1.1f));
            float x = x0 + (float) Math.sin(sec * 1.3f + i) * u * 3f;
            float twinkle = 0.45f + 0.55f * (float) Math.abs(Math.sin(sec * 2.2f + i * 1.7f));
            square(c, x, y, u, u, alpha(0.75f * a * twinkle) | rgb);
        }
    }

    // ================================================================== ash: a bust's, short

    /** Drawn over the scene. */
    static void ash(ChopTimeline tl, long ts, int w, int h, ChopScene.Canvas c) {
        float k = tl.crumble(ts);
        if (k < 0f) return;
        project(0f, ChopTimeline.HEAD, 0f);
        float ox = P[0], oy = P[1];
        float u = px();
        Random r = new Random(tl.seed() * 19 + 5);
        float sec = k * 0.7f;
        for (int i = 0; i < 26; i++) {
            float a = (float) Math.toRadians(-180 + r.nextFloat() * 180);
            float speed = w * (0.12f + r.nextFloat() * 0.22f);
            float x = ox + (float) Math.cos(a) * speed * sec;
            float y = oy + (float) Math.sin(a) * speed * sec * 0.6f + 0.25f * h * sec * sec;
            int grey = 0x6A6A6A + (r.nextInt(3) * 0x101010);
            square(c, x, y, u, u, alpha(0.55f * (1f - k) * ChopScene.sceneAlpha) | grey);
        }
    }

    // ================================================================== bits

    /** A square on the texel grid - snapped, so it never lands between pixels. */
    private static void square(ChopScene.Canvas c, float x, float y, float s, float grid, int argb) {
        if ((argb >>> 24) < 4) return;
        float gx = Math.round(x / grid) * grid, gy = Math.round(y / grid) * grid;
        c.fill(gx - s / 2f, gy - s / 2f, s, s, argb);
    }

    private static int lighten(int rgb, float k) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r += Math.round((255 - r) * k);
        g += Math.round((255 - g) * k);
        b += Math.round((255 - b) * k);
        return (r << 16) | (g << 8) | b;
    }
}
