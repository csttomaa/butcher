package com.butcher.gui.menu;

/**
 * The handful of drawing calls the menu makes, so {@link MenuView} has no Minecraft in it.
 *
 * The game implements it over {@code GuiGraphicsExtractor} (ButcherMenuScreen); a preview tool can implement it
 * with Java2D and render the very same menu offline. Coordinates are GUI pixels; colours are ARGB.
 */
public interface Draw {

    /** A filled rectangle from (x0, y0) to (x1, y1), exclusive. */
    void fill(int x0, int y0, int x1, int y1, int argb);

    /** A line of text with its top-left at (x, y), in the game's font. */
    void text(String s, int x, int y, int argb, boolean bold, boolean shadow);

    /** How wide {@link #text} draws {@code s}. */
    int width(String s, boolean bold);

    /** A vanilla item's 16 x 16 icon, by its registry path ("amethyst_shard"). */
    void item(String id, int x, int y);

    /** The mod's own icon, {@code size} pixels square. */
    void modIcon(int x, int y, int size);

    /** Clips everything drawn until {@link #endClip} to the rectangle. */
    void clip(int x0, int y0, int x1, int y1);

    void endClip();

    /** Saves the transform; {@link #translate}, {@link #scale} and {@link #rotate} apply until {@link #pop}. */
    void push();

    void translate(float x, float y);

    void scale(float s);

    /** Stretches what follows by {@code sx} across and {@code sy} down - a page turning edge-on. */
    void scale(float sx, float sy);

    /** Turns what follows by {@code radians} about the current origin (positive is clockwise on screen). */
    void rotate(float radians);

    void pop();
}
