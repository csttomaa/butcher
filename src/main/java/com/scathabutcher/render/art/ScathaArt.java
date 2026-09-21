package com.scathabutcher.render.art;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Placeholder Scatha skins, in 64x64 player-skin format.
 *
 * <h2>This is a stand-in, not the real thing</h2>
 *
 * The genuine texture belongs to Hypixel and lives on the mob, so it can only be captured
 * from a live Scatha - see {@link com.scathabutcher.render.ScathaSkins}. These images exist
 * purely so the cutscene has something to draw before that first capture happens, and they
 * are replaced the moment it does.
 *
 * Two earlier versions of this file tried to <em>be</em> the Scatha, drawing invented
 * armour plates in invented colours. That was the wrong idea: an approximation of a texture
 * nobody can check against is worse than an obvious placeholder, because it looks finished.
 * So this one is deliberately plain - the right slate, a simple maw, and nothing pretending
 * to be detail it does not have.
 *
 * The layout is the standard head unwrap, which is what
 * {@link com.scathabutcher.render.model.PlayerHeadMesh} expects:
 *
 * <pre>
 *          top     bottom                head layer at (0,0), hat layer at (32,0)
 *   right  front   left   back
 * </pre>
 */
public final class ScathaArt {

    /** Every player skin is 64x64. */
    private static final int SIZE = 64;
    /** One face of the head, in skin pixels. */
    private static final int F = 8;

    // Slate, taken off the mob's own render: nearly black, barely blue.
    private static final Color TOP    = new Color(0x3B3B4C);
    private static final Color SIDE   = new Color(0x2B2B39);
    private static final Color BOTTOM = new Color(0x16161F);
    private static final Color SEAM   = new Color(0x101018);
    private static final Color MAW    = new Color(0x8E2320);
    private static final Color MAW_LO = new Color(0x4A1210);

    private ScathaArt() {}

    /** Placeholder for a body segment: plain plated slate, no face. */
    public static BufferedImage placeholderBody() {
        return skin(false);
    }

    /** Placeholder for the head: the same, with a red maw on the front face. */
    public static BufferedImage placeholderHead() {
        return skin(true);
    }

    private static BufferedImage skin(boolean face) {
        BufferedImage img = Paint.image(SIZE, SIZE);
        Graphics2D g = Paint.graphics(img);

        // Head layer at (0,0). Slots, in skin order: top, bottom / right, front, left, back.
        plate(g, F, 0, TOP);
        plate(g, 2 * F, 0, BOTTOM);
        plate(g, 0, F, SIDE);
        plate(g, F, F, SIDE);
        plate(g, 2 * F, F, SIDE);
        plate(g, 3 * F, F, SIDE);
        if (face) maw(g, F, F);

        // Hat layer at (32,0): a thin raised rim on the sides and top. That overhang is what
        // gives the real worm its stepped back, so even the placeholder has one.
        rim(g, 32 + F, 0);
        rim(g, 32, F);
        rim(g, 32 + F, F);
        rim(g, 32 + 2 * F, F);
        rim(g, 32 + 3 * F, F);

        g.dispose();
        return img;
    }

    /** A flat plate with a darker border, so faces stay distinguishable when shaded alike. */
    private static void plate(Graphics2D g, int x, int y, Color c) {
        g.setColor(c);
        g.fillRect(x, y, F, F);
        g.setColor(SEAM);
        g.drawRect(x, y, F - 1, F - 1);
    }

    /** Two rows of short red bars - the one piece of colour anywhere on the animal. */
    private static void maw(Graphics2D g, int x, int y) {
        g.setColor(new Color(0x0E0E14));
        g.fillRect(x + 1, y + 1, F - 2, F - 2);
        for (int row = 0; row < 2; row++) {
            g.setColor(row == 0 ? MAW : MAW_LO);
            for (int col = 0; col < 3; col++) {
                g.fillRect(x + 2 + col * 2, y + 3 + row * 2, 1, 1);
            }
        }
    }

    /** One-pixel opaque border in an otherwise transparent hat slot. */
    private static void rim(Graphics2D g, int x, int y) {
        g.setColor(Paint.shade(TOP, 0.85f));
        g.drawRect(x, y, F - 1, F - 1);
    }
}
