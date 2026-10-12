package com.butcher.render;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * Plays a cutscene in the HUD layer instead of as an open {@link Screen}, so the player keeps
 * their mouse and keyboard - looking round, moving, fighting the next mob - while it plays.
 *
 * <h2>Where it comes from</h2>
 *
 * SkyMaxx (MIT) draws its animations as HUD elements for exactly this reason; the user asked for
 * it by name. Our three cutscenes are written as Screens and keep being Screens - they are simply
 * never opened: {@link #show} hands one to the HUD element registered in ButcherClient, which
 * sizes it with {@link Screen#init(int, int)} (public, and sets width, height and calls the
 * screen's own init) and draws it every frame. Nothing that listens for input ever sees it, so
 * the game does.
 *
 * The vanilla HUD is kept off the screen underneath by wrapping each vanilla HUD element (see
 * ButcherClient.hideVanillaHud), also SkyMaxx's way.
 */
public final class CutsceneHud {
    private CutsceneHud() {}

    private static Screen shown;

    /** Puts a cutscene on. It stays until it calls {@link #hide} itself. */
    public static void show(Screen screen) {
        shown = screen;
    }

    /** Takes {@code screen} off, if it is the one showing. */
    public static void hide(Screen screen) {
        if (shown == screen) shown = null;
    }

    public static Screen shown() {
        return shown;
    }

    /** The HUD element: sizes the cutscene to the GUI and draws it. */
    public static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        Screen s = shown;
        if (s == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        if (s.width != w || s.height != h) s.init(w, h);
        // No mouse: the cutscenes do not use it, and the game has it.
        s.extractRenderState(g, -1, -1, delta.getGameTimeDeltaPartialTick(true));
    }
}
