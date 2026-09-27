package com.butcher.render.art;

import com.butcher.render.model.ThawMeshes;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Every texture in the Thaw, drawn at runtime like the rest of {@code render/art/}.
 *
 * <h2>Pixel art, deliberately</h2>
 *
 * Antialiasing is switched off for everything here. The sheets are tiny - the whole front
 * of the slab is 32x44 - and are blown up several times on screen with nearest filtering,
 * the same way a block texture is. Antialiased edges at that scale come out as grey fur
 * around every crack; hard single pixels come out looking like Minecraft.
 *
 * <h2>Cracks accumulate</h2>
 *
 * Each crack stage is its own texture, and stage {@code n} is stage {@code n-1} plus more.
 * That is guaranteed by construction rather than by care: one seeded crack pattern is grown
 * once, each segment is stamped with the stage it appears at, and a stage draws every
 * segment born at or before it. So a crack can never jump or vanish between strikes, which
 * is the one thing that would give away that these are separate pictures.
 *
 * Three variants, picked per corpse, so two corpses in a row do not split identically.
 */
public final class ThawArt {

    /** How many crack stages the front face goes through before it shatters. */
    public static final int STAGES = 8;
    /** How many different crack patterns there are to pick from. */
    public static final int VARIANTS = 3;

    public static final String TEX_CORPSE = "thaw_corpse";
    public static final String TEX_PICK = "thaw_pick";
    public static final String TEX_RAY = "thaw_ray";
    public static final String TEX_SHARD = "thaw_shard";

    /** The ice sheet for a crack pattern at a stage. */
    public static String iceName(int variant, int stage) {
        return "thaw_ice_" + variant + "_" + stage;
    }

    private ThawArt() {}

    // ------------------------------------------------------------------ ice

    /** Top of the frost gradient, and the bottom of it. Cold, pale, slightly green-blue. */
    private static final Color FROST_TOP = new Color(214, 242, 255);
    private static final Color FROST_BOTTOM = new Color(132, 196, 232);

    /**
     * One crack stage of one variant: every facet of the chunk in one sheet.
     *
     * Each facet's patch is frosted, then cut to the facet's real outline - everything
     * outside it made fully transparent, which is what turns the parallelogram the renderer
     * draws into a chipped, many-sided face. A bright rim runs just inside every outline: a
     * bevel, which is most of what makes a translucent shape read as solid.
     *
     * The front's frost thins as the stage rises - from about 60% opaque to about 20% - so
     * the Vanguard comes up out of the ice strike by strike. The rest stay thick: they are
     * what keeps it reading as a block and not a sheet of glass.
     */
    public static BufferedImage ice(int variant, int stage) {
        int s = ThawMeshes.ICE_TEX;
        BufferedImage img = Paint.image(s, s);
        float cleared = stage / (float) (STAGES - 1);

        List<ThawMeshes.IceFacet> facets = ThawMeshes.iceFacets();
        for (int i = 0; i < facets.size(); i++) {
            ThawMeshes.IceFacet f = facets.get(i);
            // Lids a touch thicker than walls, as the old top was.
            int alpha = f.front() ? 150 - (int) (95 * cleared) : (f.origin()[10] > 0.5f ? 205 : 196);
            frost(img, f, alpha, 5000L + variant * 131L + i * 17L);
        }

        // The cracks, which only the front gets, drawn in its patch's own pixels.
        ThawMeshes.IceFacet front = ThawMeshes.frontFacet();
        int[] fr = front.rect();
        Graphics2D g = pixel(img);
        // One texel wide: thinner on screen than the old cracks, deliberately. There is a
        // Vanguard behind them now, and at full thickness the last stages were a white web
        // over him.
        for (Crack c : cracks(variant)) {
            if (c.stage > stage) continue;
            // A soft cyan halo under each line first, either side, so a crack reads as a
            // fracture through something thick rather than a scratch on it.
            g.setColor(new Color(170, 232, 255, 80));
            line(g, fr, c.x0, c.y0 + 1, c.x1, c.y1 + 1);
            line(g, fr, c.x0 + 1, c.y0, c.x1 + 1, c.y1);
            g.setColor(new Color(255, 255, 255, c.stage == stage ? 255 : 225));
            line(g, fr, c.x0, c.y0, c.x1, c.y1);
        }
        g.dispose();

        // Once it is badly broken, a few chips knocked clean out around the impact, which
        // is what sells that it is being struck rather than just ageing.
        if (stage >= 4) {
            float[] hit = ThawMeshes.strikePixel();
            Random rng = new Random(8800L + variant * 31L + stage);
            int chips = (stage - 3) * 2;
            int spread = Math.round(4 * K), size = Math.max(1, Math.round(K));
            for (int i = 0; i < chips; i++) {
                int cx = Math.round(hit[0]) + rng.nextInt(spread * 2 + 1) - spread;
                int cy = Math.round(hit[1]) + rng.nextInt(spread * 2 + 1) - spread;
                for (int dy = 0; dy < size; dy++) for (int dx = 0; dx < size; dx++) {
                    img.setRGB(fr[0] + clampU(cx + dx, fr), fr[1] + clampV(cy + dy, fr), 0x40E0F6FF);
                }
            }
        }

        // Last, cut the front back to its outline: a crack must not run off the edge of
        // the ice into the air around it.
        cut(img, front);

        // The far facets, flipped left to right in the sheet. A face seen from behind can
        // only be drawn by mirroring its texture - the GPU will not rasterise it the other way
        // round (see ModelProjection) - so the renderer mirrors it, and painting it mirrored
        // here cancels that out. With a plain rectangle the flip was invisible; with a
        // chipped outline it put every far facet's shape in the wrong place, poking out of
        // the block and leaving gaps between the facets seen through the ice.
        for (ThawMeshes.IceFacet f : facets) {
            if (!ThawMeshes.facesViewer(f)) flip(img, f.rect());
        }
        return img;
    }

    /** Mirrors one patch of the sheet left to right, in place. */
    private static void flip(BufferedImage img, int[] r) {
        for (int y = r[1]; y < r[1] + r[3]; y++) {
            for (int a = r[0], b = r[0] + r[2] - 1; a < b; a++, b--) {
                int t = img.getRGB(a, y);
                img.setRGB(a, y, img.getRGB(b, y));
                img.setRGB(b, y, t);
            }
        }
    }

    /**
     * Frosts one facet: a gradient by height in the chunk, streaks, speckle, a few glints,
     * then cut to the outline with a bright bevel just inside it.
     *
     * The gradient runs on the facet's real height in the chunk, not down its own patch,
     * so neighbouring facets meet at the same shade instead of each restarting from the top.
     *
     * @param alpha how opaque the frost is, 0..255
     */
    private static void frost(BufferedImage img, ThawMeshes.IceFacet f, int alpha, long seed) {
        Random rng = new Random(seed);
        int[] p = f.rect();
        int u0 = p[0], v0 = p[1], w = p[2], h = p[3];
        float top = ThawMeshes.ICE_H / 2f, span = ThawMeshes.ICE_H;

        // Streaks: a handful of columns a touch brighter or darker, the grain ice freezes in.
        float[] column = new float[w];
        for (int x = 0; x < w; x++) column[x] = 1f;
        for (int i = 0; i < Math.max(1, w / 3); i++) {
            int x = rng.nextInt(w);
            column[x] *= 0.90f + rng.nextFloat() * 0.22f;
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float[] at = f.worldAt(x + 0.5f, y + 0.5f);
                float t = Math.max(0f, Math.min(1f, (top - at[1]) / span));
                Color base = Paint.mix(FROST_TOP, FROST_BOTTOM, t * t);
                float k = column[x] * (0.95f + rng.nextFloat() * 0.10f);
                int r = clamp((int) (base.getRed() * k));
                int gr = clamp((int) (base.getGreen() * k));
                int b = clamp((int) (base.getBlue() * k));
                img.setRGB(u0 + x, v0 + y, argb(alpha, r, gr, b));
            }
        }

        // Diagonal glints: the light catching inside the block. Few, and short.
        for (int i = 0; i < 3; i++) {
            int x = rng.nextInt(w), y = rng.nextInt(h);
            for (int j = 0; j < (3 + rng.nextInt(4)) * K; j++) {
                int gx = x + j, gy = y - j;
                if (gx >= w || gy < 0) break;
                img.setRGB(u0 + gx, v0 + gy, argb(Math.min(255, alpha + 50), 245, 253, 255));
            }
        }

        // The bevel: a bright line just inside the outline and a fainter one inside that.
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float cx = x + 0.5f, cy = y + 0.5f;
                if (!f.contains(cx, cy)) continue;
                float d = f.edgeDistance(cx, cy);
                // Thin, and only a little stronger than the frost: every facet has one,
                // and the far ones show through the near ones, so a bold rim everywhere
                // turned the chunk into a wireframe.
                if (d < 1.0f) img.setRGB(u0 + x, v0 + y, argb(Math.min(255, alpha + 45), 246, 253, 255));
                else if (d < 2.2f) img.setRGB(u0 + x, v0 + y, argb(Math.min(255, alpha + 20), 236, 249, 255));
            }
        }
        cut(img, f);
    }

    /**
     * Cuts a facet's patch to its outline.
     *
     * Soft and slightly wide rather than hard. A hard cut at pixel centres rounds every edge
     * inwards by up to a pixel, so two facets that share an edge in the model met with a gap
     * between them, stepped like a staircase - the faces looked as if they did not connect.
     * So edges are kept out to one pixel beyond their true line, fading over that pixel:
     * neighbours overlap by a hair instead of parting, and the stairs soften.
     *
     * Each edge is decided on its own. The one kind that is trimmed instead, a pixel inside
     * its line, is a far facet's edge on the block's outline - where it meets a near facet.
     * Widened, those poked out past the near facets as a stepped grey fringe. Every other
     * edge, far facet to far facet included, is widened: the far facets show through the
     * ice, so they have to meet each other as cleanly as the near ones do.
     */
    private static void cut(BufferedImage img, ThawMeshes.IceFacet f) {
        int[] p = f.rect();
        List<ThawMeshes.IceFacet> all = ThawMeshes.iceFacets();
        boolean near = ThawMeshes.facesViewer(f);
        int edges = f.polygon().length / 2;
        float[] grow = new float[edges];
        for (int e = 0; e < edges; e++) {
            int o = f.across()[e];
            boolean outline = o >= 0 && ThawMeshes.facesViewer(all.get(o)) != near;
            grow[e] = !near && outline ? -1f : 1f;
        }
        for (int y = 0; y < p[3]; y++) {
            for (int x = 0; x < p[2]; x++) {
                float cx = x + 0.5f, cy = y + 0.5f;
                float signed = Float.MAX_VALUE;
                for (int e = 0; e < edges; e++) signed = Math.min(signed, f.insideEdge(e, cx, cy) + grow[e]);
                float keep = Math.max(0f, Math.min(1f, signed));
                if (keep >= 1f) continue;
                int c = img.getRGB(p[0] + x, p[1] + y);
                if (keep <= 0f) { img.setRGB(p[0] + x, p[1] + y, 0); continue; }
                // Just outside the edge a pixel may still be empty (the frost fill covers the
                // whole patch, but only the inside got a rim) - give it the rim's colour.
                int a = Math.round((c >>> 24) * keep);
                img.setRGB(p[0] + x, p[1] + y, (a << 24) | (c & 0xFFFFFF));
            }
        }
    }

    // ------------------------------------------------------------------ cracks

    /**
     * How much finer the ice sheet is than the one the cracks were drawn for. Their lengths,
     * steps and thickness are all multiplied by it, so they look as they always did.
     */
    private static final float K = ThawMeshes.ICE_DENSITY / 1.45f;

    /** One straight piece of a crack, in front-patch pixels, and the stage it appears at. */
    private record Crack(int x0, int y0, int x1, int y1, int stage) {}

    private static final List<List<Crack>> PATTERNS = new ArrayList<>();

    /**
     * The crack pattern for a variant, grown once and kept.
     *
     * Each strike opens a new cluster near the last impact - the pick lands in roughly the
     * same place each time, but not exactly - and every later strike lengthens what is
     * already there. Branches wander by a pixel either side as they go, which is the
     * difference between a crack and a line.
     */
    private static synchronized List<Crack> cracks(int variant) {
        while (PATTERNS.size() <= variant) PATTERNS.add(null);
        List<Crack> cached = PATTERNS.get(variant);
        if (cached != null) return cached;

        Random rng = new Random(71_003L * (variant + 1));
        List<Crack> out = new ArrayList<>();
        ThawMeshes.IceFacet front = ThawMeshes.frontFacet();
        float[] hit = ThawMeshes.strikePixel();

        for (int stage = 1; stage < STAGES; stage++) {
            float px = hit[0] + (rng.nextInt(7) - 3) * K;
            float py = hit[1] + (rng.nextInt(7) - 3) * K;
            int rays = 3 + rng.nextInt(2) + (stage > 4 ? 1 : 0);
            for (int r = 0; r < rays; r++) {
                double ang = rng.nextDouble() * Math.PI * 2;
                // Later strikes crack further: the block is weaker every time.
                float len = (5f + rng.nextFloat() * 6f + stage * 1.6f) * K * 0.8f;
                grow(out, rng, px, py, ang, len, stage, 0, front);
            }
        }
        PATTERNS.set(variant, out);
        return out;
    }

    private static void grow(List<Crack> out, Random rng, float x, float y, double ang, float len,
                             int stage, int depth, ThawMeshes.IceFacet f) {
        float step = (2.5f + rng.nextFloat() * 1.5f) * K;
        float travelled = 0f;
        while (travelled < len) {
            ang += (rng.nextFloat() - 0.5f) * 0.9f;
            float nx = x + (float) Math.cos(ang) * step;
            float ny = y + (float) Math.sin(ang) * step;
            // Ran off the face: stop at its edge, not at its bounding box.
            if (!f.contains(nx, ny) || f.edgeDistance(nx, ny) < 1.5f * K) return;
            out.add(new Crack(Math.round(x), Math.round(y), Math.round(nx), Math.round(ny), stage));
            x = nx;
            y = ny;
            travelled += step;
            // Fork now and then, but not recursively forever.
            if (depth < 2 && rng.nextFloat() < 0.28f) {
                grow(out, rng, x, y, ang + (rng.nextBoolean() ? 0.8 : -0.8),
                        len * 0.45f, stage, depth + 1, f);
            }
        }
    }

    // ------------------------------------------------------------------ the Vanguard

    private static final int SHADOW = 0x10141F;
    private static final int RIM = 0x46638A;
    /** The visor's slit: the one thing on him that catches any light. */
    private static final int VISOR_GLINT = 0x33608C;

    /**
     * The Vanguard's skin: a shadow, not a painted man.
     *
     * He was drawn in his real armour colours for a build, and the user preferred the frozen
     * silhouette - a dark shape behind frost is eerier than a figure you can see every pixel
     * of. What makes it a Vanguard now is the shape: the posed 3D body, the helmet, the
     * pauldrons. The paint only helps that along: near-black with a faint grain, the upward
     * faces a little lighter, as if light came down through the ice onto his helmet and
     * shoulders, and a dim glint in the T of the visor. A rim round every face was tried and
     * drew him as a wireframe.
     *
     * Laid out on the standard 64x64 player sheet, because {@link ThawMeshes#vanguard} maps
     * every box onto that cross.
     */
    public static BufferedImage corpse() {
        int s = ThawMeshes.VANGUARD_TEX;
        BufferedImage img = Paint.image(s, s);
        Random rng = new Random(9091);

        // Solid all over. He was see-through, legs fading out, and read as a ghost of a
        // Vanguard rather than one.
        shadowBox(img, rng, 0, 0, 8, 8, 8, 255, 255);        // head
        shadowBox(img, rng, 16, 16, 8, 12, 4, 255, 255);     // body
        shadowBox(img, rng, 40, 16, 4, 12, 4, 255, 255);     // right arm
        shadowBox(img, rng, 32, 48, 4, 12, 4, 255, 255);     // left arm
        shadowBox(img, rng, 0, 16, 4, 12, 4, 255, 255);      // right leg
        shadowBox(img, rng, 16, 48, 4, 12, 4, 255, 255);     // left leg
        int[] pd = ThawMeshes.PAULDRON;
        litFace(img, rng, pd[0], pd[1], pd[2], pd[3], 255);

        // The visor: the eye slit and the nose guard, on the helmet's front (8,8).
        // Edge to edge and all the way down, as on the real helmet - no border of shadow
        // round it. The front is the 8x8 patch at (8, 8).
        int glint = argb(255, VISOR_GLINT >> 16 & 255, VISOR_GLINT >> 8 & 255, VISOR_GLINT & 255);
        for (int x = 8; x < 16; x++) img.setRGB(x, 8 + 3, glint);
        for (int y = 4; y < 8; y++) {
            img.setRGB(11, 8 + y, glint);
            img.setRGB(12, 8 + y, glint);
        }
        return img;
    }

    /** Every face of one box's cross, with alpha running from {@code aTop} to {@code aBottom} down the sides. */
    private static void shadowBox(BufferedImage img, Random rng, int u, int v, int w, int h, int d, int aTop, int aBottom) {
        litFace(img, rng, u + d, v, w, d, aTop);                            // top
        shadowFace(img, rng, u + d + w, v, w, d, aBottom, aBottom);         // bottom
        shadowFace(img, rng, u, v + d, d, h, aTop, aBottom);                // sides and front and back
        shadowFace(img, rng, u + d, v + d, w, h, aTop, aBottom);
        shadowFace(img, rng, u + d + w, v + d, d, h, aTop, aBottom);
        shadowFace(img, rng, u + 2 * d + w, v + d, w, h, aTop, aBottom);
    }

    /** One face: shadow with a faint grain, alpha running from {@code aTop} to {@code aBottom}. */
    private static void shadowFace(BufferedImage img, Random rng, int x0, int y0, int w, int h, int aTop, int aBottom) {
        for (int y = 0; y < h; y++) {
            int a = h <= 1 ? aTop : aTop + (aBottom - aTop) * y / (h - 1);
            for (int x = 0; x < w; x++) {
                float k = 0.85f + rng.nextFloat() * 0.3f;
                img.setRGB(x0 + x, y0 + y, argb(a, (int) ((SHADOW >> 16 & 255) * k),
                        (int) ((SHADOW >> 8 & 255) * k), (int) ((SHADOW & 255) * k)));
            }
        }
    }

    /** An upward face: the shadow, lifted a third of the way towards the cold rim colour. */
    private static void litFace(BufferedImage img, Random rng, int x0, int y0, int w, int h, int a) {
        Color lit = Paint.mix(new Color(SHADOW), new Color(RIM), 0.35f);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float k = 0.9f + rng.nextFloat() * 0.2f;
                img.setRGB(x0 + x, y0 + y, argb(a, (int) (lit.getRed() * k), (int) (lit.getGreen() * k), (int) (lit.getBlue() * k)));
            }
        }
    }

    // ------------------------------------------------------------------ pick

    /** Wood, leather, cold steel, a bright edge and dark iron, at the patches ThawMeshes names. */
    public static BufferedImage pick() {
        int s = ThawMeshes.PICK_TEX;
        BufferedImage img = Paint.image(s, s);
        Graphics2D g = pixel(img);
        Random rng = new Random(3307);

        int[] wd = ThawMeshes.WOOD;
        g.setColor(new Color(0x7A5234));
        g.fillRect(wd[0], wd[1], wd[2], wd[3]);
        for (int i = 0; i < 12; i++) {
            g.setColor(Paint.shade(new Color(0x7A5234), 0.7f + rng.nextFloat() * 0.5f));
            g.fillRect(wd[0] + rng.nextInt(wd[2]), wd[1], 1, wd[3]);
        }

        int[] le = ThawMeshes.LEATHER;
        for (int y = 0; y < le[3]; y++) {
            // Wrapped, so it bands.
            g.setColor(y % 4 < 2 ? new Color(0x3B2A22) : new Color(0x52392C));
            g.fillRect(le[0], le[1] + y, le[2], 1);
        }

        int[] st = ThawMeshes.STEEL;
        for (int y = 0; y < st[3]; y++) {
            float t = y / (float) st[3];
            // Glacite-cold steel: a faint blue in it, so it belongs to the tunnels.
            Color c = t < 0.4f
                    ? Paint.mix(new Color(0xE6EEF8), new Color(0x9FB0C6), t / 0.4f)
                    : Paint.mix(new Color(0x9FB0C6), new Color(0x55627A), (t - 0.4f) / 0.6f);
            g.setColor(c);
            g.fillRect(st[0], st[1] + y, st[2], 1);
        }
        for (int i = 0; i < 10; i++) {
            g.setColor(new Color(255, 255, 255, 40 + rng.nextInt(40)));
            int x = st[0] + rng.nextInt(st[2]), y = st[1] + rng.nextInt(st[3]);
            g.fillRect(x, y, 2, 1);
        }

        int[] ed = ThawMeshes.EDGE;
        g.setColor(new Color(0xF4FAFF));
        g.fillRect(ed[0], ed[1], ed[2], ed[3]);

        int[] dk = ThawMeshes.DARK;
        g.setColor(new Color(0x3A4150));
        g.fillRect(dk[0], dk[1], dk[2], dk[3]);
        g.setColor(new Color(0x5A6376));
        g.fillRect(dk[0], dk[1], dk[2], 2);
        g.dispose();
        return img;
    }

    // ------------------------------------------------------------------ light

    /**
     * One ray of light: bright at its root, gone at its tip, soft across its width.
     *
     * White, and tinted at draw time - the same strip is the gold burst for a legendary and
     * the pink one for the Dye. Written pixel by pixel on a transparent ground, never
     * blurred, for the reason CLAUDE.md gives: blurring an RGBA image drags the black of the
     * transparent pixels into the soft edge and turns a glow grey.
     */
    public static BufferedImage ray() {
        int w = 64, h = 16;
        BufferedImage img = Paint.image(w, h);
        for (int y = 0; y < h; y++) {
            float across = Math.abs((y + 0.5f) / h * 2f - 1f);
            float soft = (float) Math.exp(-across * across * 4.5f);
            for (int x = 0; x < w; x++) {
                float along = 1f - x / (float) (w - 1);
                int a = (int) (255 * soft * along * along);
                img.setRGB(x, y, (a << 24) | 0xFFFFFF);
            }
        }
        return img;
    }

    /** A chip of ice: a small bright diamond, tinted and spun at draw time. */
    public static BufferedImage shard() {
        int s = 8;
        BufferedImage img = Paint.image(s, s);
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float d = Math.abs(x + 0.5f - s / 2f) + Math.abs(y + 0.5f - s / 2f) * 0.7f;
                if (d > s / 2f) continue;
                boolean rim = d > s / 2f - 1.2f;
                img.setRGB(x, y, rim ? argb(255, 255, 255, 255) : argb(210, 196, 236, 255));
            }
        }
        return img;
    }

    // ------------------------------------------------------------------ light and paint

    /**
     * The flare's aura: a broad, soft pool of light, white and tinted at draw time.
     *
     * The flare first used the gems' glow, which falls off with the cube of the distance -
     * at half its radius it is down to an eighth. Right for a tight bloom on a gem; wrong
     * here, where the middle of it is hidden behind the ice, so all that showed of the
     * flare was a faint rim and the colours were hard to tell apart. This one is
     * {@code (1 - d^2)^2}: still more than half as bright at half the radius, and soft all
     * the way to zero at the edge.
     */
    public static BufferedImage aura() {
        int s = 128;
        BufferedImage img = Paint.image(s, s);
        float c = s / 2f;
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float dx = (x + 0.5f - c) / c, dy = (y + 0.5f - c) / c;
                float d2 = dx * dx + dy * dy;
                if (d2 >= 1f) continue;
                float a = (1f - d2) * (1f - d2);
                img.setRGB(x, y, ((int) (a * 255) << 24) | 0xFFFFFF);
            }
        }
        return img;
    }

    /**
     * A coin, face on, for the Locket's rain. Drawn gold rather than white - a tinted white
     * disc reads as a sequin, and the rim and the embossed mark are what make it money.
     * Spun at draw time by squeezing its width.
     */
    public static BufferedImage coin() {
        int s = 16;
        BufferedImage img = Paint.image(s, s);
        float c = s / 2f;
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float dx = x + 0.5f - c, dy = y + 0.5f - c;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > c - 0.5f) continue;
                int rgb;
                if (d > c - 2f) rgb = (dx + dy < 0) ? 0xFFF3B0 : 0xB07A12;        // bevelled rim
                else if (d > c - 3f) rgb = 0xD99A1E;
                else rgb = (dx + dy < -2) ? 0xFFD95A : 0xF2B930;                  // face, lit top-left
                img.setRGB(x, y, 0xFF000000 | rgb);
            }
        }
        // The mark in the middle: a small raised square, like SkyBlock's coin icon.
        for (int y = 6; y <= 9; y++) {
            for (int x = 6; x <= 9; x++) {
                boolean edge = x == 6 || y == 6;
                img.setRGB(x, y, 0xFF000000 | (edge ? 0xFFF3B0 : 0xC98A18));
            }
        }
        return img;
    }

    /** How many different paint splats there are. */
    public static final int SPLATS = 4;

    public static String splatName(int variant) {
        return "thaw_splat_" + variant;
    }

    /**
     * A splat of paint, for the Dye's burst: a round body, a ring of droplets thrown off it,
     * and a few spikes where it hit hardest. White, tinted pink at draw time.
     *
     * Hard-edged on purpose. A soft blob reads as light or smoke; paint has a meniscus, and
     * a clean edge with a slightly lighter lip just inside it is what says liquid.
     */
    public static BufferedImage splat(int variant) {
        int s = 64;
        BufferedImage img = Paint.image(s, s);
        Graphics2D g = pixel(img);
        Random rng = new Random(7700L + variant * 97L);
        float c = s / 2f;
        g.setColor(Color.WHITE);
        // Body: overlapping lobes, so it is not a circle.
        for (int i = 0; i < 7; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            float off = 3f + rng.nextFloat() * 6f;
            float r = 9f + rng.nextFloat() * 6f;
            float x = c + (float) Math.cos(a) * off, y = c + (float) Math.sin(a) * off;
            g.fill(new java.awt.geom.Ellipse2D.Float(x - r, y - r, r * 2, r * 2));
        }
        // Spikes: thin wedges shot outward.
        for (int i = 0; i < 5 + rng.nextInt(3); i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            float len = 20f + rng.nextFloat() * 9f, w = 1.6f + rng.nextFloat() * 1.8f;
            java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
            float px = (float) -Math.sin(a) * w, py = (float) Math.cos(a) * w;
            p.moveTo(c + px, c + py);
            p.lineTo(c + (float) Math.cos(a) * len, c + (float) Math.sin(a) * len);
            p.lineTo(c - px, c - py);
            p.closePath();
            g.fill(p);
            // A droplet at the end of some of them.
            if (rng.nextBoolean()) {
                float r = 1.5f + rng.nextFloat() * 1.5f;
                float dx = c + (float) Math.cos(a) * (len + 2.5f), dy = c + (float) Math.sin(a) * (len + 2.5f);
                g.fill(new java.awt.geom.Ellipse2D.Float(dx - r, dy - r, r * 2, r * 2));
            }
        }
        // Loose droplets around it.
        for (int i = 0; i < 9; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            float d = 17f + rng.nextFloat() * 12f, r = 0.8f + rng.nextFloat() * 1.8f;
            float x = c + (float) Math.cos(a) * d, y = c + (float) Math.sin(a) * d;
            g.fill(new java.awt.geom.Ellipse2D.Float(x - r, y - r, r * 2, r * 2));
        }
        g.dispose();
        // Shade: the body a little darker than its lip, so the tint comes out two-toned.
        for (int y = 1; y < s - 1; y++) {
            for (int x = 1; x < s - 1; x++) {
                if ((img.getRGB(x, y) >>> 24) == 0) continue;
                boolean lip = (img.getRGB(x - 1, y) >>> 24) == 0 || (img.getRGB(x + 1, y) >>> 24) == 0
                        || (img.getRGB(x, y - 1) >>> 24) == 0 || (img.getRGB(x, y + 1) >>> 24) == 0;
                float dx = x - c, dy = y - c;
                boolean shine = dx * dx + dy * dy < 30 && dx < -1 && dy < -1;
                img.setRGB(x, y, lip ? 0xFFFFFFFF : (shine ? 0xFFFFFFFF : 0xFFD8D8D8));
            }
        }
        return img;
    }

    /**
     * One drip running down from a splat: a stem that is drawn stretched, with the round
     * bead at its bottom. White, tinted at draw time. Stretched vertically on screen, so it
     * is drawn narrow and tall here.
     */
    public static BufferedImage drip() {
        int w = 8, h = 32;
        BufferedImage img = Paint.image(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float cx = x + 0.5f - w / 2f;
                boolean in;
                if (y < h - w) {
                    // The stem widens slowly towards the bead.
                    float half = 1.4f + 1.2f * y / (float) (h - w);
                    in = Math.abs(cx) <= half;
                } else {
                    float cy = y + 0.5f - (h - w / 2f);
                    in = cx * cx + cy * cy <= (w / 2f) * (w / 2f);
                }
                if (in) img.setRGB(x, y, Math.abs(cx) < 1f && y > 2 ? 0xFFFFFFFF : 0xFFD8D8D8);
            }
        }
        return img;
    }

    public static final String TEX_AURA = "thaw_aura";
    public static final String TEX_COIN = "thaw_coin";
    public static final String TEX_DRIP = "thaw_drip";

    // ------------------------------------------------------------------ glacite powder

    /**
     * Glacite Powder's icon: a small heap of pale blue powder, 16x16, in the manner of the
     * old light blue dye. Painted here rather than copied from the game - see
     * {@link com.butcher.render.VanguardItems#TEX_GLACITE_POWDER} for how to use the real one.
     */
    public static BufferedImage glacitePowder() {
        int s = 16;
        BufferedImage img = Paint.image(s, s);
        Random rng = new Random(4404);
        // The heap's outline: widest near the bottom, a lumpy top with two mounds.
        int[] top = {0, 0, 9, 7, 6, 5, 5, 6, 6, 5, 4, 5, 6, 8, 0, 0};
        int[] bottom = {0, 0, 11, 12, 12, 13, 13, 13, 13, 13, 13, 12, 12, 11, 0, 0};
        int outline = 0xFF1F4F96, dark = 0xFF3C74C8, mid = 0xFF5F95E0, light = 0xFF8DB9F2, shine = 0xFFCFE6FF;
        for (int x = 2; x <= 13; x++) {
            for (int y = top[x]; y <= bottom[x]; y++) {
                boolean edge = y == top[x] || y == bottom[x] || x == 2 || x == 13;
                int c;
                if (edge) c = y == top[x] && y < 7 ? mid : outline;
                else {
                    // Lit from the top left: lighter up there, darker towards the bottom right.
                    float lit = 1f - (y - 4) / 10f - (x - 2) / 30f + (rng.nextFloat() - 0.5f) * 0.35f;
                    c = lit > 0.78f ? light : lit > 0.5f ? mid : dark;
                }
                img.setRGB(x, y, c);
            }
        }
        // A few bright grains.
        int[][] grains = {{5, 7}, {9, 6}, {7, 9}, {10, 8}, {4, 10}};
        for (int[] p : grains) img.setRGB(p[0], p[1], shine);
        return img;
    }

    // ------------------------------------------------------------------ helpers

    private static Graphics2D pixel(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    /** A line clipped to one patch, in that patch's own coordinates. */
    private static void line(Graphics2D g, int[] p, int x0, int y0, int x1, int y1) {
        java.awt.Shape old = g.getClip();
        g.setClip(p[0], p[1], p[2], p[3]);
        g.drawLine(p[0] + x0, p[1] + y0, p[0] + x1, p[1] + y1);
        g.setClip(old);
    }

    private static int clampU(int x, int[] p) { return Math.max(0, Math.min(p[2] - 1, x)); }
    private static int clampV(int y, int[] p) { return Math.max(0, Math.min(p[3] - 1, y)); }

    private static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
