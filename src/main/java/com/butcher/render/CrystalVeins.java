package com.butcher.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Scatha's screen fill (the user, 2026-10-02, "variant A"): gemstone crystals in the Crystal
 * Hollows' colours grow in from the edges of the screen, a batch with every cut. At the reveal they
 * shatter like glass for a gemstone, flare in the pet's rarity colour and turn to point at the head
 * for a pet, and sink back into the edges when the worm ducks the last swing.
 *
 * Pixel art: each crystal is a prism rasterised onto a grid of cells a few GUI pixels across - a lit
 * face, a dark face, a bright ridge between them and a brighter tip - with a dark outline. Each crystal
 * has an <b>arrival</b>, 0..1, and snaps up over a short window once the growth passes it.
 *
 * Pure: a grid, a seed and a fill call, so the same code draws in game and offline.
 */
public final class CrystalVeins {

    public interface Fill {
        void fill(float x, float y, float w, float h, int argb);
    }

    public enum End { NONE, SHATTER, FLARE, SINK }

    /** The Crystal Hollows' gemstones: ruby, amber, sapphire, jade, amethyst, topaz. */
    private static final int[] GEMS = {0xE04A4A, 0xF2A33A, 0x4A7BE0, 0x3FBF6B, 0xA04AE0, 0xF2E04A};

    private record Crystal(float bx, float by, float angle, int length, int width, int gem, float arrival,
                           float r1, float r2) {}

    private final long seed;
    private List<Crystal> crystals;
    private int builtW = -1, builtH = -1, unit = 2;

    public CrystalVeins(long seed) {
        this.seed = seed;
    }

    /** The grid cell, in GUI pixels, for a screen {@code h} tall. */
    public static int unitFor(int h) {
        return Math.max(2, Math.round(h / 120f));
    }

    /**
     * @param growth  how many have grown, 0..1
     * @param end     what the reveal does to them, from {@code endT} 0..1
     * @param flareRgb the pet's rarity colour, for {@link End#FLARE}
     * @param tx      where a pet's head is, for the crystals to turn towards
     * @param alpha   the cutscene's own fade
     */
    public void draw(Fill f, int w, int h, float growth, End end, float endT, int flareRgb,
                     float tx, float ty, float alpha) {
        if (alpha < 0.02f || growth <= 0f) return;
        if (crystals == null || w != builtW || h != builtH) build(w, h);
        float u = unit;
        float diag = (float) Math.hypot(w, h);
        float cx = w / 2f, cy = h / 2f;
        for (Crystal c : crystals) {
            float k = (growth - c.arrival) / 0.16f;
            if (k <= 0f) continue;
            // Snaps up and settles: a quick overshoot, so each one reads as growing, not fading in.
            float grown = backOut(Math.min(1f, k));
            float angle = c.angle;
            float sink = 0f, a = alpha;
            float lean = 0f, flare = 0f;
            switch (end) {
                case FLARE -> {
                    flare = Math.min(1f, endT / 0.25f);
                    lean = ease(Math.min(1f, endT / 0.45f));
                    float to = (float) Math.atan2(ty - c.by * u, tx - c.bx * u);
                    angle = lerpAngle(c.angle, to, 0.55f * lean);
                }
                case SINK -> {
                    sink = ease(endT);
                    a *= 1f - endT;
                }
                default -> { }
            }
            float len = Math.max(1f, c.length * grown * (1f - sink));
            int gem = c.gem;
            if (flare > 0f) gem = mix(gem, flareRgb, 0.75f * flare);
            raster(c, angle, len);
            float flash = end == End.SHATTER ? Math.max(0f, 1f - endT / 0.1f) : 0f;
            // Outline first, then the cells over it.
            for (int pass = 0; pass < 2; pass++) {
                int count = pass == 0 ? edgeCount : insideCount;
                int[] xs = pass == 0 ? edgeX : insideX, ys = pass == 0 ? edgeY : insideY;
                for (int q = 0; q < count; q++) {
                    float x = xs[q] * u, y = ys[q] * u;
                    float ea = a;
                    float cs = pass == 1 ? insideS[q] : 0f, ct = pass == 1 ? insideT[q] : 0f;
                    if (end == End.SHATTER && endT > 0.06f) {
                        float qq = (endT - 0.06f) / 0.94f;
                        float dx = cx - x, dy = cy - y;
                        float dl = Math.max(1f, (float) Math.hypot(dx, dy));
                        // Each crystal breaks in a few chunks along its length, not into dust.
                        float along = (float) Math.hypot(xs[q] + 0.5f - c.bx, ys[q] + 0.5f - c.by);
                        float chunk = (float) Math.floor(along / 3f) * 0.37f + c.r1;
                        float pop = diag * 0.05f * (0.5f + (chunk % 1f)) * (1f - (1f - qq) * (1f - qq));
                        x += dx / dl * pop + ((chunk * 7.3f) % 1f - 0.5f) * diag * 0.06f * qq;
                        y += dy / dl * pop + qq * qq * h * (0.7f + 0.6f * ((chunk * 3.1f) % 1f));
                        ea *= 1f - Math.max(0f, (qq - 0.45f) / 0.55f);
                    }
                    if (ea < 0.02f) continue;
                    int rgb;
                    if (pass == 0) {
                        rgb = 0x0E0A12;
                        ea *= 0.9f;
                    } else {
                        rgb = shade(gem, ct, cs, len);
                        if (flare > 0f) rgb = mix(rgb, 0xFFFFFF, 0.25f * flare * (0.6f + 0.4f * (float) Math.sin(endT * 18 + c.r2 * 6)));
                        if (flash > 0f) rgb = mix(rgb, 0xFFFFFF, flash);
                    }
                    cell(f, x, y, u, (Math.round(ea * 255) << 24) | rgb);
                }
            }
        }
    }

    // ---- one crystal, rasterised: every grid cell whose centre is inside the prism, and the cells
    // round it that are not (the outline). Reused buffers - drawn on one thread only.
    private int[] insideX = new int[256], insideY = new int[256], edgeX = new int[256], edgeY = new int[256];
    private float[] insideS = new float[256], insideT = new float[256];
    private int insideCount, edgeCount;
    private boolean[] mask = new boolean[0];

    private void raster(Crystal c, float angle, float len) {
        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
        float px = -sa, py = ca;
        float halfW = c.width / 2f, tip = Math.min(len, c.width * 0.9f);
        float[][] corners = {{0, -halfW}, {0, halfW}, {len, -halfW}, {len, halfW}};
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] k : corners) {
            float x = c.bx + ca * k[0] + px * k[1], y = c.by + sa * k[0] + py * k[1];
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        int x0 = (int) Math.floor(minX) - 1, y0 = (int) Math.floor(minY) - 1;
        int bw = (int) Math.ceil(maxX) - x0 + 2, bh = (int) Math.ceil(maxY) - y0 + 2;
        if (mask.length < bw * bh) mask = new boolean[bw * bh];
        else java.util.Arrays.fill(mask, 0, bw * bh, false);
        insideCount = 0;
        edgeCount = 0;
        for (int j = 0; j < bh; j++) {
            for (int i = 0; i < bw; i++) {
                float vx = x0 + i + 0.5f - c.bx, vy = y0 + j + 0.5f - c.by;
                float s = vx * ca + vy * sa, t = vx * px + vy * py;
                if (s < 0f || s > len) continue;
                float limit = s > len - tip ? halfW * (len - s) / tip : halfW;
                if (Math.abs(t) > limit) continue;
                mask[j * bw + i] = true;
                grow();
                insideX[insideCount] = x0 + i;
                insideY[insideCount] = y0 + j;
                insideS[insideCount] = s;
                insideT[insideCount] = t / Math.max(0.5f, halfW);
                insideCount++;
            }
        }
        for (int j = 0; j < bh; j++) {
            for (int i = 0; i < bw; i++) {
                if (mask[j * bw + i]) continue;
                boolean near = (i > 0 && mask[j * bw + i - 1]) || (i < bw - 1 && mask[j * bw + i + 1])
                        || (j > 0 && mask[(j - 1) * bw + i]) || (j < bh - 1 && mask[(j + 1) * bw + i]);
                if (!near) continue;
                if (edgeCount == edgeX.length) {
                    edgeX = java.util.Arrays.copyOf(edgeX, edgeCount * 2);
                    edgeY = java.util.Arrays.copyOf(edgeY, edgeCount * 2);
                }
                edgeX[edgeCount] = x0 + i;
                edgeY[edgeCount] = y0 + j;
                edgeCount++;
            }
        }
    }

    private void grow() {
        if (insideCount < insideX.length) return;
        int n = insideCount * 2;
        insideX = java.util.Arrays.copyOf(insideX, n);
        insideY = java.util.Arrays.copyOf(insideY, n);
        insideS = java.util.Arrays.copyOf(insideS, n);
        insideT = java.util.Arrays.copyOf(insideT, n);
    }

    /** The lit face, the dark face, the ridge between, and a brighter tip. */
    private static int shade(int gem, float t, float s, float len) {
        int rgb = t < -0.3f ? mix(gem, 0xFFFFFF, 0.28f) : t > 0.3f ? mix(gem, 0x000000, 0.32f) : mix(gem, 0xFFFFFF, 0.5f);
        if (s >= len - 2f) rgb = mix(rgb, 0xFFFFFF, 0.35f);
        return rgb;
    }

    private static void cell(Fill f, float x, float y, float u, int argb) {
        if ((argb >>> 24) < 4) return;
        float gx = Math.round(x / u) * u, gy = Math.round(y / u) * u;
        f.fill(gx, gy, u, u, argb);
    }

    // ================================================================== placing them

    private void build(int w, int h) {
        builtW = w;
        builtH = h;
        unit = unitFor(h);
        int gw = w / unit, gh = h / unit;
        Random r = new Random(seed * 0x5DEECE66DL + 23);
        crystals = new ArrayList<>();
        // Clusters in the four corners, bigger at the bottom, then singles along the sides and top.
        float[][] clusters = {{0, gh, -0.75f, 1.1f}, {gw, gh, -2.39f, 1.1f}, {0, 0, 0.75f, 0.8f}, {gw, 0, 2.39f, 0.8f}};
        int i = 0;
        List<float[]> spots = new ArrayList<>();
        for (float[] k : clusters) {
            for (int j = 0; j < 7; j++) {
                float along = r.nextFloat() * 12f;
                boolean onSide = r.nextBoolean();
                float bx = k[0] == 0 ? (onSide ? 0 : along) : (onSide ? gw : gw - along);
                float by = k[1] == 0 ? (onSide ? along : 0) : (onSide ? gh - along : gh);
                float ang = k[2] + (r.nextFloat() - 0.5f) * 0.9f;
                spots.add(new float[]{bx, by, ang, k[3] * (15 + r.nextInt(13)), 5 + r.nextInt(3)});
            }
        }
        for (int j = 0; j < 6; j++) {
            float y = gh * (0.25f + 0.5f * r.nextFloat());
            spots.add(new float[]{0, y, (r.nextFloat() - 0.5f) * 0.8f, 8 + r.nextInt(8), 4 + r.nextInt(2)});
            spots.add(new float[]{gw, gh * (0.25f + 0.5f * r.nextFloat()), (float) Math.PI + (r.nextFloat() - 0.5f) * 0.8f, 8 + r.nextInt(8), 4 + r.nextInt(2)});
        }
        for (int j = 0; j < 5; j++) {
            spots.add(new float[]{gw * (0.15f + 0.7f * r.nextFloat()), 0, (float) Math.PI / 2 + (r.nextFloat() - 0.5f) * 0.8f, 6 + r.nextInt(6), 3});
        }
        // Arrivals spread evenly, shuffled, so every cut grows a batch from all round the screen.
        int n = spots.size();
        List<Integer> order = new ArrayList<>();
        for (int j = 0; j < n; j++) order.add(j);
        java.util.Collections.shuffle(order, r);
        for (float[] s : spots) {
            float arrival = 0.02f + 0.82f * order.get(i++) / (float) n;
            crystals.add(new Crystal(s[0], s[1], s[2], Math.round(s[3]), Math.round(s[4]),
                    GEMS[r.nextInt(GEMS.length)], arrival, r.nextFloat(), r.nextFloat()));
        }
    }

    private static float ease(float t) { return t * t * (3f - 2f * t); }

    private static float backOut(float t) {
        float c = 1.4f, u = t - 1f;
        return 1f + (c + 1f) * u * u * u + c * u * u;
    }

    private static float lerpAngle(float a, float b, float t) {
        float d = (float) Math.atan2(Math.sin(b - a), Math.cos(b - a));
        return a + d * t;
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
