package com.butcher;

/**
 * Animations pulled from players while they are being reworked. One switch each: while it is on, the animation's
 * detection does not run, nothing of it plays (its pre-kill hide and dropped-item hiding included), and the menu shows
 * its card taped over. The developer commands still play it for the accounts in {@link Dev}.
 *
 * No Minecraft in here, so the menu (and its offline preview) can read it too.
 */
public final class Maintenance {
    private Maintenance() {}

    /** The Seraph's Glyph (the Voidgloom Seraph): being reworked before players see it (the user, 2026-10-15). */
    public static final boolean SERAPH = true;
}
