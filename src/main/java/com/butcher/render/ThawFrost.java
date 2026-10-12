package com.butcher.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Thaw's screen freezing (the user, 2026-10-02): frost grows in from the corners and along the
 * edges of the screen, a step with every pick strike, glinting in the strike's flare colour; when the
 * run lands it melts away on a loss and shatters outward on a win.
 *
 * Pixel art: squares on a grid (one cell is a few GUI pixels), grown as frost ferns - branches walking
 * in from each corner, a dense rime in the corner itself, short spikes creeping along the edges. Every
 * cell has an <b>arrival</b>, 0..1: it shows once the growth has reached it, so the frost visibly
 * spreads rather than fading in. The bottom middle stays clear - the tray of drops is there.
 *
 * Pure: it only knows a grid, a seed and a fill call, so the same code draws in game and offline.
 * The growth comes from the strikes landed, which happen at the same moments whatever the drop -
 * so like everything else in the Thaw, nothing here tells the outcome before the landing.
 */
public final class ThawFrost {

    /** The one drawing call it needs. */
    public interface Fill {
        void fill(float x, float y, float w, float h, int argb);
    }

    /** What happens to it once the run lands. */
    public enum End { NONE, MELT, CRACK, SHATTER }

    private record Cell(int x, int y, float arrival, int shade, float r1, float r2) {}

    private final long seed;
    private List<Cell> cells;
    private int builtW = -1, builtH = -1, unit = 1;

    public ThawFrost(long seed) {
        this.seed = seed;
    }

    /** The grid cell, in GUI pixels, for a screen {@code h} tall. */
    public static int unitFor(int h) {
        return Math.max(2, Math.round(h / 110f));
    }

    /**
     * @param growth  how far it has spread, 0..1
     * @param glintRgb the colour the newest frost catches - the last strike's flare
     * @param glint   how strongly, 0..1, fading after each strike
     * @param end     melt, crack or shatter - {@link End#NONE} before the landing
     * @param endT    how far through that, 0..1
     * @param alpha   the cutscene's own fade
     */
    public void draw(Fill f, int w, int h, float growth, int glintRgb, float glint, End end, float endT, float alpha) {
        if (alpha < 0.02f || growth <= 0f) return;
        if (cells == null || w != builtW || h != builtH) build(w, h);
        float u = unit;
        float cx = w / 2f, cy = h / 2f;
        float diag = (float) Math.hypot(w, h);
        for (Cell c : cells) {
            if (c.arrival > growth) continue;
            float x = c.x * u, y = c.y * u;
            float a = alpha * (c.shade == 0 ? 0.88f : c.shade == 1 ? 0.74f : 0.58f);
            int rgb = c.shade == 0 ? 0xE6F6FF : c.shade == 1 ? 0xBFE6FF : 0x8FD0F5;
            // The front of the growth catches the strike's colour.
            float front = growth - c.arrival;
            if (glint > 0.01f && front < 0.12f) {
                float k = glint * (1f - front / 0.12f);
                rgb = mix(rgb, lift(glintRgb), 0.8f * k);
                a = Math.min(1f, a + 0.25f * k);
            }
            switch (end) {
                case MELT -> {
                    // Whole columns slide together, so it reads as dripping, not static.
                    float col = (float) ((Math.sin(c.x * 12.9898 + seed) * 43758.5453) % 1.0);
                    float drip = endT * endT * h * 0.3f * (0.5f + Math.abs(col));
                    y += drip;
                    a *= 1f - endT;
                }
                case CRACK, SHATTER -> {
                    // Like glass: a flash of white, then the pieces pop a little inwards and fall.
                    // (Flying outwards would take frost that is already at the edges straight off the
                    // screen, and nothing would be seen.) The Reveal's crack only drops the outer frost.
                    boolean breaks = end == End.SHATTER || c.arrival > 0.45f;
                    float flash = Math.max(0f, 1f - endT / 0.12f);
                    rgb = mix(rgb, 0xFFFFFF, flash);
                    if (breaks && endT > 0.08f) {
                        float k = (endT - 0.08f) / 0.92f;
                        float dx = cx - x, dy = cy - y;
                        float len = Math.max(1f, (float) Math.hypot(dx, dy));
                        float pop = diag * 0.06f * (0.5f + c.r2) * (1f - (1f - k) * (1f - k));
                        x += dx / len * pop + (c.r1 - 0.5f) * diag * 0.05f * k;
                        y += dy / len * pop + k * k * h * (0.7f + 0.6f * c.r1);
                        a *= 1f - Math.max(0f, (k - 0.45f) / 0.55f);
                    } else if (!breaks) {
                        a *= 1f - endT;
                    }
                }
                default -> { }
            }
            if (a < 0.02f) continue;
            float gx = Math.round(x / u) * u, gy = Math.round(y / u) * u;
            f.fill(gx, gy, u, u, (Math.round(a * 255) << 24) | rgb);
        }
    }

    // ================================================================== growing the pattern

    private void build(int w, int h) {
        builtW = w;
        builtH = h;
        unit = unitFor(h);
        int gw = w / unit + 1, gh = h / unit + 1;
        Random r = new Random(seed * 0x5DEECE66DL + 11);
        // One arrival per cell: the earliest any growth reaches it.
        float[][] arr = new float[gw][gh];
        int[][] shade = new int[gw][gh];
        for (float[] col : arr) java.util.Arrays.fill(col, 2f);
        int reach = Math.round(gh * 0.42f);
        int[][] corners = {{0, 0}, {gw - 1, 0}, {0, gh - 1}, {gw - 1, gh - 1}};
        for (int[] k : corners) {
            boolean bottom = k[1] > 0;
            int span = bottom ? Math.round(reach * 0.75f) : reach;
            // The dense rime in the corner.
            int rime = Math.round(span * 0.22f);
            for (int dx = 0; dx <= rime; dx++) {
                for (int dy = 0; dy <= rime; dy++) {
                    float d = (float) Math.hypot(dx, dy);
                    float edge = d / rime;
                    if (edge > 0.7f + 0.3f * r.nextFloat()) continue;
                    // Holes, more of them towards the outside, and the shades mixed - rime, not paint.
                    if (r.nextFloat() < 0.12f + 0.3f * edge) continue;
                    int sh = edge < 0.45f ? (r.nextFloat() < 0.75f ? 0 : 1) : (r.nextFloat() < 0.6f ? 1 : 2);
                    put(arr, shade, k[0] == 0 ? dx : k[0] - dx, k[1] == 0 ? dy : k[1] - dy, 0.3f * edge, sh);
                }
            }
            // Ferns walking in towards the middle.
            float toward = (float) Math.atan2(gh / 2f - k[1], gw / 2f - k[0]);
            for (int b = 0; b < 9; b++) {
                float a = toward + (b - 4) * 0.22f + (r.nextFloat() - 0.5f) * 0.2f;
                fern(arr, shade, r, k[0], k[1], a, span * (0.7f + 0.3f * r.nextFloat()), 0f, span, 0);
            }
        }
        // A ragged band of frost creeping along the top and the sides, out from the corners - its depth
        // wanders smoothly, with now and then a longer spike.
        band(arr, shade, r, gw, true, 0, 1, gw);
        band(arr, shade, r, gh, false, 0, 1, gh);
        band(arr, shade, r, gh, false, gw - 1, -1, gh);
        // Along the bottom only near the corners - the tray is in the middle.
        band(arr, shade, r, gw, true, gh - 1, -1, gw);
        cells = new ArrayList<>();
        for (int x = 0; x < gw; x++) {
            for (int y = 0; y < gh; y++) {
                if (arr[x][y] > 1f) continue;
                cells.add(new Cell(x, y, arr[x][y], shade[x][y], r.nextFloat(), r.nextFloat()));
            }
        }
    }

    /** A branch: walks, wavers, and now and then forks; arrival runs with the distance from the corner. */
    private void fern(float[][] arr, int[][] shade, Random r, float x, float y, float a, float len,
                      float travelled, float span, int depth) {
        float steps = len;
        for (int i = 0; i < steps; i++) {
            x += (float) Math.cos(a);
            y += (float) Math.sin(a);
            a += (r.nextFloat() - 0.5f) * 0.35f;
            float arrival = 0.12f + 0.88f * (travelled + i) / (span * 1.15f);
            put(arr, shade, Math.round(x), Math.round(y), arrival, depth == 0 && i < steps * 0.5f ? 1 : 2);
            if (depth < 2 && r.nextFloat() < 0.11f) {
                float side = r.nextBoolean() ? 1f : -1f;
                fern(arr, shade, r, x, y, a + side * (0.7f + r.nextFloat() * 0.4f),
                        (steps - i) * (0.35f + r.nextFloat() * 0.3f), travelled + i, span, depth + 1);
            }
        }
    }

    /**
     * A ragged band along one edge: {@code horizontal} runs along x at row {@code at}, else along y at
     * column {@code at}; {@code inward} is +1 or -1. Its depth wanders (a smoothed walk), with holes and
     * the odd longer spike. Arrival grows from the corners towards the middle of the edge. The bottom
     * edge (inward -1, horizontal) leaves its middle clear for the tray.
     */
    private void band(float[][] arr, int[][] shade, Random r, int length, boolean horizontal, int at, int inward, int n) {
        boolean bottom = horizontal && inward < 0;
        float depth = 1.5f;
        for (int i = 0; i < n; i++) {
            if (bottom && Math.abs(i - n / 2f) < n * 0.3f) continue;
            depth += (r.nextFloat() - 0.5f) * 1.2f;
            depth = Math.max(0.8f, Math.min(5f, depth));
            int d = Math.round(depth) + (r.nextFloat() < 0.06f ? 3 + r.nextInt(4) : 0);
            float along = Math.min(i, length - 1 - i) / (length / 2f);
            for (int j = 0; j < d; j++) {
                if (j > 0 && r.nextFloat() < 0.18f) continue;
                int x = horizontal ? i : at + inward * j, y = horizontal ? at + inward * j : i;
                put(arr, shade, x, y, Math.min(1f, 0.22f + 0.72f * along + j * 0.015f), j == 0 ? 1 : 2);
            }
        }
    }

    private static void put(float[][] arr, int[][] shade, int x, int y, float arrival, int s) {
        if (x < 0 || y < 0 || x >= arr.length || y >= arr[0].length) return;
        if (arrival < arr[x][y]) {
            arr[x][y] = arrival;
            shade[x][y] = s;
        }
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** The flare colours are deep; frost catching them is lighter. */
    private static int lift(int rgb) {
        return mix(rgb & 0xFFFFFF, 0xFFFFFF, 0.25f);
    }
}
