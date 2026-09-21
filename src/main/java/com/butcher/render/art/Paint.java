package com.butcher.render.art;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;

/**
 * Small shared helpers for the runtime-drawn art.
 *
 * <h2>Why the textures are drawn in Java and not shipped as PNGs</h2>
 *
 * Every texture this mod used to have was produced by a script in tools/ and committed as
 * a PNG. That arrangement has a standing hazard, written up in CLAUDE.md: values duplicated
 * between a script and the Java that positions its output have to be moved together, and
 * editing the PNG instead of the script silently loses the change on the next regeneration.
 *
 * SkyMaxx has no such problem because it ships almost no images - it draws them with
 * {@link Graphics2D} at startup and uploads the results. One source of truth, no
 * regeneration step, and the numbers live next to the code that uses them. Same approach
 * here.
 *
 * The drawing itself is vector: filled and stroked shapes with antialiasing, then a dark
 * ink outline. That outline is what stops a box model reading as flat-shaded cardboard -
 * it gives every face a drawn edge, which together with the banded shading in
 * {@link com.butcher.render.model.ModelProjection} is the whole cel-shaded look.
 */
public final class Paint {

    /** The one outline colour, used everywhere so the whole model looks drawn by one hand. */
    public static final Color INK = new Color(0x1A1520);

    private Paint() {}

    /** A fresh transparent ARGB image. */
    public static BufferedImage image(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    /** Antialiased, quality-biased Graphics2D over {@code img}. Caller disposes. */
    public static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        return g;
    }

    /** Outlines {@code shape} in ink. */
    public static void ink(Graphics2D g, Shape shape, float width) {
        g.setColor(INK);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(shape);
    }

    /** Fills then outlines, the usual pairing. */
    public static void fillInk(Graphics2D g, Shape shape, Color fill, float inkWidth) {
        g.setColor(fill);
        g.fill(shape);
        ink(g, shape, inkWidth);
    }

    /**
     * Draws a 1px ink border just inside a UV rect.
     *
     * Every box face gets one. Without it, two faces meeting at a corner are two flat
     * colours abutting, and the silhouette of the shape disappears wherever the shading
     * happens to put them at similar brightness.
     */
    public static void edge(Graphics2D g, int x, int y, int w, int h) {
        edge(g, x, y, w, h, 1f);
    }

    /**
     * As {@link #edge(Graphics2D, int, int, int, int)}, {@code width} texels thick.
     *
     * Thickness matters more than it looks. A skin authored at several texels per model
     * unit is drawn onto far fewer screen pixels than it has texels, so a one-texel border
     * lands on a fraction of a pixel and disappears - which is exactly what happened to the
     * first version of the Scatha, and why it read as a smooth tube instead of a stack of
     * armour plates. Scale the ink with the texel density and the plates come back.
     */
    public static void edge(Graphics2D g, int x, int y, int w, int h, float width) {
        g.setColor(INK);
        g.setStroke(new BasicStroke(width));
        float half = width / 2f;
        g.draw(new java.awt.geom.Rectangle2D.Float(x + half, y + half, w - width, h - width));
    }

    /** Multiplies a colour's brightness, keeping alpha. */
    public static Color shade(Color c, float mul) {
        return new Color(
                clamp((int) (c.getRed() * mul)),
                clamp((int) (c.getGreen() * mul)),
                clamp((int) (c.getBlue() * mul)),
                c.getAlpha());
    }

    /** Linear blend between two colours. */
    public static Color mix(Color a, Color b, float t) {
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return new Color(
                clamp((int) (a.getRed() + (b.getRed() - a.getRed()) * t)),
                clamp((int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t)),
                clamp((int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t)),
                clamp((int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t)));
    }

    /** Clears a shape to full transparency - for punching a hole rather than painting over. */
    public static void knockOut(Graphics2D g, Shape shape) {
        java.awt.Composite old = g.getComposite();
        g.setComposite(AlphaComposite.Clear);
        g.fill(shape);
        g.setComposite(old);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
