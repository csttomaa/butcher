package com.butcher.render;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Models drawn the way the game draws them: every polygon rasterised into one image with a real depth buffer,
 * then that image drawn once. First written for the Ender Dragon (0.2.68 - a centre sort drew far parts over
 * near ones, "you can kind of see through him"); now every big model goes through it.
 *
 * <ul>
 *   <li><b>Cutout</b>, as vanilla draws entities: a texel is there (alpha above 0) or a hole.</li>
 *   <li><b>Texture slots</b>: a model may use several (Ragnarok's skin, its horse, the saddle).</li>
 *   <li><b>Overlays</b> (an eyes layer, a glowing crack mask) draw only on the surface already there.</li>
 *   <li><b>Occluders</b> write depth and no colour: the lava's surface, so whatever has sunk under it is cut off
 *       at the waterline - a sinking creature is clipped exactly, with its outline along the cut.</li>
 *   <li><b>The lava's glow</b> ({@link #glow}): parts near the surface take its heat colour, fading with height.</li>
 *   <li><b>The outline</b> is the covered area grown by the ink width - a true silhouette.</li>
 * </ul>
 * Pure Java: the preview and the game run the very same pixels.
 */
public final class ModelRaster {

    private final int[][] tex = new int[8][];
    private final int[] texW = new int[8], texH = new int[8];

    /** Sets slot {@code slot}'s texture from an image (the game reads them through its resource manager). */
    public void texture(int slot, BufferedImage img) {
        if (img == null) {
            tex[slot] = null;
            return;
        }
        texW[slot] = img.getWidth();
        texH[slot] = img.getHeight();
        tex[slot] = img.getRGB(0, 0, texW[slot], texH[slot], null, 0, texW[slot]);
    }

    public void texture(int slot, int[] argb, int w, int h) {
        tex[slot] = argb;
        texW[slot] = w;
        texH[slot] = h;
    }

    public boolean has(int slot) {
        return tex[slot] != null;
    }

    /** The finished image, straight ARGB, {@code w x h}, covering the GUI rect from ({@code x}, {@code y}) at {@code scale}. */
    public int[] px = new int[0];
    public int w, h;
    public float x, y, scale;
    private float[] z = new float[0];
    private boolean[] cover = new boolean[0], across = new boolean[0];
    /** The most image pixels a frame may cost: the scale drops for a model that fills a large screen. */
    public static final int BUDGET = 600_000;

    /** The surface glow: parts within {@code range} world units above {@code y} take {@code rgb}, at most {@code strength}. */
    private float glowY = Float.NaN, glowRange = 1f, glowStrength;
    private int glowRgb;

    public void glow(float surfaceY, float range, int rgb, float strength) {
        glowY = surfaceY;
        glowRange = Math.max(0.01f, range);
        glowRgb = rgb;
        glowStrength = strength;
    }

    public void noGlow() {
        glowY = Float.NaN;
    }

    /**
     * Pixels of the next faces whose world height is below this are not drawn (NaN: none) - a surface the camera
     * looks down on hides exactly what is under it, so this does an occluder's work without rasterising one.
     */
    public float cutBelow = Float.NaN;

    /**
     * A second texture laid over the next faces in the same pass (NaN-free: -1 for none): its texels, where not
     * clear, are drawn in {@code secondTint} (times the texel) plus {@code secondAdd} over the face - the magma's
     * glowing cracks over the block, without rasterising every face twice.
     */
    public int secondSlot = -1, secondTint = 0xFFFFFF, secondAdd;

    /** Starts a frame covering the GUI rect (x0, y0)-(x1, y1), at {@code scale} image pixels a GUI pixel. */
    public boolean begin(float x0, float y0, float x1, float y1, float scale) {
        x = (float) Math.floor(x0);
        y = (float) Math.floor(y0);
        float area = Math.max(1f, (x1 - x) * (y1 - y));
        scale = Math.min(scale, (float) Math.sqrt(BUDGET / area));
        this.scale = scale = Math.max(0.5f, scale);
        w = Math.max(0, (int) Math.ceil((x1 - x) * scale));
        h = Math.max(0, (int) Math.ceil((y1 - y) * scale));
        if (w == 0 || h == 0) return false;
        int n = w * h;
        if (px.length < n) {
            px = new int[n];
            z = new float[n];
            cover = new boolean[n];
            across = new boolean[n];
        }
        Arrays.fill(px, 0, n, 0);
        Arrays.fill(z, 0, n, Float.NEGATIVE_INFINITY);
        Arrays.fill(cover, 0, n, false);
        return true;
    }

    /**
     * One polygon: the parallelogram from corner (ox, oy) along (ux, uy) and (vx, vy), in GUI pixels; its depth at
     * the corner and along each edge (larger is nearer); its world height likewise (for the glow; NaN for none);
     * the texel rect it shows, normalised 0..1. {@code lit} darkens it. {@code tint} multiplies its colour (RGB),
     * {@code add} adds light (RGB, its alpha byte how much). {@code overlay}: draw only on the surface already there.
     */
    public void face(float ox, float oy, float ux, float uy, float vx, float vy, float d0, float du, float dv,
                     float wy0, float wyu, float wyv, float u0, float v0, float u1, float v1, int slot,
                     float lit, int tint, int add, boolean overlay) {
        int[] src = tex[slot];
        if (src == null) return;
        int sw = texW[slot], sh = texH[slot];
        ox = (ox - x) * scale;
        oy = (oy - y) * scale;
        ux *= scale;
        uy *= scale;
        vx *= scale;
        vy *= scale;
        float det = ux * vy - uy * vx;
        if (Math.abs(det) < 1e-4f) return;
        float inv = 1f / det;
        int minX = Math.max(0, (int) Math.floor(Math.min(Math.min(ox, ox + ux), Math.min(ox + vx, ox + ux + vx))));
        int maxX = Math.min(w - 1, (int) Math.ceil(Math.max(Math.max(ox, ox + ux), Math.max(ox + vx, ox + ux + vx))));
        int minY = Math.max(0, (int) Math.floor(Math.min(Math.min(oy, oy + uy), Math.min(oy + vy, oy + uy + vy))));
        int maxY = Math.min(h - 1, (int) Math.ceil(Math.max(Math.max(oy, oy + uy), Math.max(oy + vy, oy + uy + vy))));
        float tu0 = u0 * sw, tv0 = v0 * sh, tdu = (u1 - u0) * sw, tdv = (v1 - v0) * sh;
        int tr = (int) (Math.max(0f, Math.min(1f, lit)) * (tint >> 16 & 255)), tg = (int) (Math.max(0f, Math.min(1f, lit)) * (tint >> 8 & 255)),
                tb = (int) (Math.max(0f, Math.min(1f, lit)) * (tint & 255));
        int aa = add >>> 24, ar = (add >> 16 & 255) * aa / 255, ag = (add >> 8 & 255) * aa / 255, ab = (add & 255) * aa / 255;
        boolean glowing = !Float.isNaN(glowY) && !Float.isNaN(wy0) && glowStrength > 0f;
        boolean cutting = !Float.isNaN(cutBelow) && !Float.isNaN(wy0);
        float cut = cutBelow;
        int gr = glowRgb >> 16 & 255, gg = glowRgb >> 8 & 255, gb = glowRgb & 255;
        int[] src2 = secondSlot >= 0 && !overlay ? tex[secondSlot] : null;
        int sw2 = src2 == null ? 1 : texW[secondSlot], sh2 = src2 == null ? 1 : texH[secondSlot];
        float tu2 = u0 * sw2, tv2 = v0 * sh2, tdu2 = (u1 - u0) * sw2, tdv2 = (v1 - v0) * sh2;
        int s2r = secondTint >> 16 & 255, s2g = secondTint >> 8 & 255, s2b = secondTint & 255;
        int s2a = secondAdd >>> 24, s2ar = (secondAdd >> 16 & 255) * s2a / 255, s2ag = (secondAdd >> 8 & 255) * s2a / 255,
                s2ab = (secondAdd & 255) * s2a / 255;
        float sdx = vy * inv, tdx = -uy * inv;
        for (int py = minY; py <= maxY; py++) {
            float dy = py + 0.5f - oy;
            float dx0 = 0.5f - ox;
            float s0 = (dx0 * vy - dy * vx) * inv, t0 = (ux * dy - uy * dx0) * inv;
            // Where both s and t lie in [0, 1) along this row - no allocation, this runs per row per face.
            float lo = minX, hi = maxX + 1;
            if (Math.abs(sdx) < 1e-9f) {
                if (s0 < 0f || s0 >= 1f) continue;
            } else {
                float ea = -s0 / sdx, eb = (1f - s0) / sdx;
                lo = Math.max(lo, Math.min(ea, eb));
                hi = Math.min(hi, Math.max(ea, eb));
            }
            if (Math.abs(tdx) < 1e-9f) {
                if (t0 < 0f || t0 >= 1f) continue;
            } else {
                float ea = -t0 / tdx, eb = (1f - t0) / tdx;
                lo = Math.max(lo, Math.min(ea, eb));
                hi = Math.min(hi, Math.max(ea, eb));
            }
            if (lo > hi) continue;
            int xa = Math.max(minX, (int) Math.ceil(lo - 1e-4f)), xb = Math.min(maxX, (int) Math.floor(hi + 1e-4f));
            int row = py * w;
            for (int pxl = xa; pxl <= xb; pxl++) {
                float s = s0 + sdx * pxl, t = t0 + tdx * pxl;
                if (s < 0f || s >= 1f || t < 0f || t >= 1f) continue;
                int i = row + pxl;
                if (cutting && wy0 + s * wyu + t * wyv < cut) continue;
                float d = d0 + s * du + t * dv;
                if (overlay) {
                    if (!cover[i] || d < z[i] - 0.6f) continue;
                } else if (d <= z[i]) continue;
                int tx = Math.min(sw - 1, Math.max(0, (int) (tu0 + s * tdu)));
                int ty = Math.min(sh - 1, Math.max(0, (int) (tv0 + t * tdv)));
                int c = src[ty * sw + tx];
                int a = c >>> 24;
                if (a == 0) continue;
                int cr = (c >> 16 & 255) * tr / 255 + ar, cg = (c >> 8 & 255) * tg / 255 + ag, cb = (c & 255) * tb / 255 + ab;
                if (glowing) {
                    float wy = wy0 + s * wyu + t * wyv;
                    float k = glowStrength * (1f - Math.max(0f, Math.min(1f, (wy - glowY) / glowRange)));
                    if (k > 0f) {
                        cr += (int) ((gr - cr * 0.4f) * k);
                        cg += (int) ((gg - cg * 0.4f) * k);
                        cb += (int) ((gb - cb * 0.4f) * k);
                    }
                }
                int out = 0xFF000000 | Math.min(255, Math.max(0, cr)) << 16 | Math.min(255, Math.max(0, cg)) << 8 | Math.min(255, Math.max(0, cb));
                if (src2 != null) {
                    int x2 = Math.min(sw2 - 1, Math.max(0, (int) (tu2 + s * tdu2)));
                    int y2 = Math.min(sh2 - 1, Math.max(0, (int) (tv2 + t * tdv2)));
                    int c2 = src2[y2 * sw2 + x2];
                    int a2 = c2 >>> 24;
                    if (a2 > 0) {
                        int r2 = Math.min(255, (c2 >> 16 & 255) * s2r / 255 + s2ar), g2 = Math.min(255, (c2 >> 8 & 255) * s2g / 255 + s2ag),
                                b2 = Math.min(255, (c2 & 255) * s2b / 255 + s2ab);
                        out = blend(a2 << 24 | r2 << 16 | g2 << 8 | b2, out);
                    }
                }
                if (overlay) {
                    px[i] = blend(a << 24 | (out & 0xFFFFFF), px[i]);
                    continue;
                }
                z[i] = d;
                cover[i] = true;
                px[i] = out;
            }
        }
    }

    /** A surface that hides what is behind (below) it and draws nothing: the lava, cutting off what has sunk. */
    public void occluder(float ox, float oy, float ux, float uy, float vx, float vy, float d0, float du, float dv) {
        ox = (ox - x) * scale;
        oy = (oy - y) * scale;
        ux *= scale;
        uy *= scale;
        vx *= scale;
        vy *= scale;
        float det = ux * vy - uy * vx;
        if (Math.abs(det) < 1e-4f) return;
        float inv = 1f / det;
        int minX = Math.max(0, (int) Math.floor(Math.min(Math.min(ox, ox + ux), Math.min(ox + vx, ox + ux + vx))));
        int maxX = Math.min(w - 1, (int) Math.ceil(Math.max(Math.max(ox, ox + ux), Math.max(ox + vx, ox + ux + vx))));
        int minY = Math.max(0, (int) Math.floor(Math.min(Math.min(oy, oy + uy), Math.min(oy + vy, oy + uy + vy))));
        int maxY = Math.min(h - 1, (int) Math.ceil(Math.max(Math.max(oy, oy + uy), Math.max(oy + vy, oy + uy + vy))));
        float sdx = vy * inv, tdx = -uy * inv;
        for (int py = minY; py <= maxY; py++) {
            float dy = py + 0.5f - oy;
            float dx0 = 0.5f - ox;
            float s0 = (dx0 * vy - dy * vx) * inv, t0 = (ux * dy - uy * dx0) * inv;
            // Where both s and t lie in [0, 1) along this row - no allocation, this runs per row per face.
            float lo = minX, hi = maxX + 1;
            if (Math.abs(sdx) < 1e-9f) {
                if (s0 < 0f || s0 >= 1f) continue;
            } else {
                float ea = -s0 / sdx, eb = (1f - s0) / sdx;
                lo = Math.max(lo, Math.min(ea, eb));
                hi = Math.min(hi, Math.max(ea, eb));
            }
            if (Math.abs(tdx) < 1e-9f) {
                if (t0 < 0f || t0 >= 1f) continue;
            } else {
                float ea = -t0 / tdx, eb = (1f - t0) / tdx;
                lo = Math.max(lo, Math.min(ea, eb));
                hi = Math.min(hi, Math.max(ea, eb));
            }
            if (lo > hi) continue;
            int xa = Math.max(minX, (int) Math.ceil(lo - 1e-4f)), xb = Math.min(maxX, (int) Math.floor(hi + 1e-4f));
            int row = py * w;
            for (int pxl = xa; pxl <= xb; pxl++) {
                float s = s0 + sdx * pxl, t = t0 + tdx * pxl;
                if (s < 0f || s >= 1f || t < 0f || t >= 1f) continue;
                int i = row + pxl;
                float d = d0 + s * du + t * dv;
                if (d > z[i]) z[i] = d;
            }
        }
    }

    /** The outline: every empty pixel within {@code r} image pixels of the model turns black. */
    public void ink(int r) {
        if (r <= 0) return;
        int n = w * h;
        Arrays.fill(across, 0, n, false);
        for (int yy = 0; yy < h; yy++) {
            int run = -1_000_000;
            for (int xx = 0; xx < w; xx++) {
                if (cover[yy * w + xx]) run = xx;
                if (xx - run <= r) across[yy * w + xx] = true;
            }
            run = 1_000_000;
            for (int xx = w - 1; xx >= 0; xx--) {
                if (cover[yy * w + xx]) run = xx;
                if (run - xx <= r) across[yy * w + xx] = true;
            }
        }
        // Then down the columns - walked row by row, a last-seen row per column, so memory is read in order.
        if (lastRow.length < w) lastRow = new int[w];
        Arrays.fill(lastRow, 0, w, -1_000_000);
        for (int yy = 0; yy < h; yy++) {
            int row = yy * w;
            for (int xx = 0; xx < w; xx++) {
                if (across[row + xx]) lastRow[xx] = yy;
                if (yy - lastRow[xx] <= r && !cover[row + xx] && (px[row + xx] >>> 24) == 0) px[row + xx] = 0xFF000000;
            }
        }
        Arrays.fill(lastRow, 0, w, 1_000_000);
        for (int yy = h - 1; yy >= 0; yy--) {
            int row = yy * w;
            for (int xx = 0; xx < w; xx++) {
                if (across[row + xx]) lastRow[xx] = yy;
                if (lastRow[xx] - yy <= r && !cover[row + xx] && (px[row + xx] >>> 24) == 0) px[row + xx] = 0xFF000000;
            }
        }
    }

    private int[] lastRow = new int[0];

    /** Fades the whole image. */
    public void fade(float alpha) {
        if (alpha >= 0.999f) return;
        int n = w * h, k = Math.round(Math.max(0f, alpha) * 256);
        for (int i = 0; i < n; i++) {
            int c = px[i];
            if (c == 0) continue;
            px[i] = ((c >>> 24) * k >> 8) << 24 | (c & 0xFFFFFF);
        }
    }

    /** Whether anything at all was drawn. */
    public boolean any() {
        int n = w * h;
        for (int i = 0; i < n; i++) if (px[i] != 0) return true;
        return false;
    }

    private static int blend(int src, int dst) {
        int sa = src >>> 24;
        if (sa == 255) return src;
        int ia = 255 - sa;
        // x / 255, rounded, for x up to 255 * 255, as a multiply and a shift.
        int r = (((src >> 16 & 255) * sa + (dst >> 16 & 255) * ia) + 128) * 257 >> 16;
        int g = (((src >> 8 & 255) * sa + (dst >> 8 & 255) * ia) + 128) * 257 >> 16;
        int b = (((src & 255) * sa + (dst & 255) * ia) + 128) * 257 >> 16;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
