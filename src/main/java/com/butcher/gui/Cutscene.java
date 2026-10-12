package com.butcher.gui;

/**
 * What ButcherClient needs from the newer cutscenes (Chopping Block, Hellion, the Seraph's Glyph, Eruption) to skip one,
 * or to make room for a better one: a known win cuts in over a bust or a kill still waiting for its drop (0.2.84 - the
 * review: a Carmine Dye landing during a Scuttler's bust used to be thrown away).
 */
public interface Cutscene {

    boolean isActive();

    boolean isTest();

    /** A kill still waiting to hear what it dropped. */
    boolean pending();

    /** Showing a drop known to be a win - nothing cuts in over it. */
    boolean showsWin();

    /**
     * The skip key: a win jumps to its showcase with the price already counted; a bust, a kill still waiting, or a
     * showcase already up ends here.
     */
    void skip();

    /** Ends it now, the sound cut too: something better has come. */
    void interrupt();
}
