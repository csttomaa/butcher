package com.butcher.render.art;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Textures for the props in the cutscene: the gem crystals and the cleaver.
 *
 * Both are deliberately colourless-ish. {@link com.butcher.render.MeshRenderer}'s
 * per-model tint multiplies into the texture and carries alpha, so one grey facet sheet
 * serves every gem colour and every degree of translucency - a new gem is a constant, not
 * an asset. That is the same greyscale-plus-tint trick the cave sprites already use, and
 * the reason the gems were split from the rock in the first place.
 */
public final class PropArt {

    // Names these are registered under in TextureBank. Kept here, beside the drawing,
    // so the scene can name a texture without depending on the mod entrypoint.
    public static final String TEX_CRYSTAL = "crystal_facets";
    public static final String TEX_GEM_GLOW = "gem_glow";
    public static final String TEX_CLEAVER = "cleaver";
    public static final String TEX_STONE = "cave_stone";

    private PropArt() {}

    // =====================================================================
    // crystals
    // =====================================================================

    /**
     * A facet sheet for gem shards: near-white, with lengthwise banding and a bright lip.
     *
     * Kept light on purpose. It is multiplied by a saturated tint, so anything already
     * dark here comes out muddy - the colour has to come from the tint and the *form* from
     * the shading, not from the texture.
     */
    public static BufferedImage crystalFacets() {
        int s = CrystalSize.TEX;
        BufferedImage img = Paint.image(s, s);
        Graphics2D g = Paint.graphics(img);
        Random rng = new Random(9182);

        g.setColor(new Color(0xD8D4E8));
        g.fillRect(0, 0, s, s);

        // Lengthwise bands: the internal fractures you see through a gem block.
        for (int i = 0; i < 18; i++) {
            int x = rng.nextInt(s);
            int w = 1 + rng.nextInt(2);
            float k = 0.78f + rng.nextFloat() * 0.3f;
            g.setColor(Paint.shade(new Color(0xD8D4E8), k));
            g.fillRect(x, 0, w, s);
        }
        // A couple of brighter slivers, so a rotating shard flashes.
        for (int i = 0; i < 3; i++) {
            g.setColor(new Color(0xFFFFFF));
            g.fillRect(rng.nextInt(s), 0, 1, s);
        }
        // Darker towards the bottom, so the base of a shard sits into the rock.
        int from = (int) (s * 0.62f);
        for (int y = from; y < s; y++) {
            // Clamped: the int truncation above can start a pixel before the float
            // boundary, which makes the first t negative and Color reject the alpha.
            float t = Math.max(0f, Math.min(1f, (y - s * 0.62f) / (s * 0.38f)));
            g.setColor(new Color(0, 0, 0, (int) (110 * t)));
            g.fillRect(0, y, s, 1);
        }
        g.dispose();
        return img;
    }

    /** A soft round glow, tinted per gem, stacked behind a cluster so it reads as lit. */
    public static BufferedImage gemGlow() {
        int s = 64;
        BufferedImage img = Paint.image(s, s);
        // Radial falloff written directly: a blurred shape would drag black in from the
        // transparent surround, which is the trap CLAUDE.md records for the cave art.
        float c = s / 2f;
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float dx = (x + 0.5f - c) / c, dy = (y + 0.5f - c) / c;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = Math.max(0f, 1f - d);
                a = a * a * a;
                img.setRGB(x, y, ((int) (a * 255) << 24) | 0xFFFFFF);
            }
        }
        return img;
    }

    /** Sizes shared with {@link com.butcher.render.model.CrystalMesh}. */
    public static final class CrystalSize {
        public static final int TEX = com.butcher.render.model.CrystalMesh.TEX;
        private CrystalSize() {}
    }

    // =====================================================================
    // cleaver
    // =====================================================================

    /**
     * One strip sheet holding steel, wood and brass side by side, at the UV origins
     * {@link com.butcher.render.model.CleaverMesh} names.
     */
    public static BufferedImage cleaver() {
        int s = com.butcher.render.model.CleaverMesh.TEX;
        BufferedImage img = Paint.image(s, s);
        Graphics2D g = Paint.graphics(img);
        Random rng = new Random(4471);

        // ---- steel ------------------------------------------------------
        int[] st = com.butcher.render.model.CleaverMesh.STEEL;
        int u = st[0];
        for (int y = 0; y < s; y++) {
            float t = y / (float) s;
            // Bright along the top third, falling away - a blade catches light on its flat.
            Color c = t < 0.35f
                    ? Paint.mix(new Color(0xE9ECF4), new Color(0xA8AEC0), t / 0.35f)
                    : Paint.mix(new Color(0xA8AEC0), new Color(0x5A6072), (t - 0.35f) / 0.65f);
            g.setColor(c);
            g.fillRect(u, y, st[2], 1);
        }
        // Forge marks: faint diagonal streaks, so the flat is not dead flat.
        for (int i = 0; i < 14; i++) {
            int x = u + rng.nextInt(st[2]);
            int y = rng.nextInt(st[3]);
            g.setColor(new Color(255, 255, 255, 26 + rng.nextInt(30)));
            g.drawLine(x, y, x + 2 + rng.nextInt(3), y + 1);
        }
        // The honed edge patch, near white.
        int[] ed = com.butcher.render.model.CleaverMesh.EDGE;
        g.setColor(new Color(0xF6F8FF));
        g.fillRect(ed[0], ed[1], ed[2], ed[3]);

        // ---- wood -------------------------------------------------------
        int[] wd = com.butcher.render.model.CleaverMesh.WOOD;
        u = wd[0];
        g.setColor(new Color(0x6B4630));
        g.fillRect(u, wd[1], wd[2], wd[3]);
        for (int i = 0; i < 16; i++) {
            int x = u + rng.nextInt(wd[2]);
            g.setColor(Paint.shade(new Color(0x6B4630), 0.72f + rng.nextFloat() * 0.5f));
            g.fillRect(x, wd[1], 1, wd[3]);
        }

        // ---- brass ------------------------------------------------------
        int[] br = com.butcher.render.model.CleaverMesh.BRASS;
        for (int y = 0; y < br[3]; y++) {
            float t = y / (float) br[3];
            g.setColor(Paint.mix(new Color(0xD8B15E), new Color(0x7A5C22), t));
            g.fillRect(br[0], br[1] + y, br[2], 1);
        }

        g.dispose();
        return img;
    }

    // =====================================================================
    // cave
    // =====================================================================

    /**
     * A stone face for the cavern walls, drawn light enough to be tinted down per surface.
     *
     * The cave is built from the same box meshes as everything else rather than from one
     * painted backdrop, so it parallaxes correctly when the camera orbits - a flat image
     * behind a rotating subject is the thing that most gives away a fake 3D scene.
     */
    public static BufferedImage stone() {
        int s = 64;
        BufferedImage img = Paint.image(s, s);
        Graphics2D g = Paint.graphics(img);
        Random rng = new Random(2024);

        g.setColor(new Color(0x8A8A93));
        g.fillRect(0, 0, s, s);
        // Blocky mottling on a 4px grid, which keeps it reading as Minecraft stone.
        for (int y = 0; y < s; y += 4) {
            for (int x = 0; x < s; x += 4) {
                float k = 0.82f + rng.nextFloat() * 0.32f;
                g.setColor(Paint.shade(new Color(0x8A8A93), k));
                g.fillRect(x, y, 4, 4);
            }
        }
        // A few darker cracks.
        g.setColor(new Color(0x5C5C66));
        for (int i = 0; i < 7; i++) {
            Path2D p = new Path2D.Float();
            float x = rng.nextInt(s), y = rng.nextInt(s);
            p.moveTo(x, y);
            for (int k = 0; k < 3; k++) {
                x += rng.nextInt(13) - 6;
                y += rng.nextInt(13) - 6;
                p.lineTo(x, y);
            }
            Paint.ink(g, p, 1f);
        }
        g.dispose();
        return img;
    }
}
