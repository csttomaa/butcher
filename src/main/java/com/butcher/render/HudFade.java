package com.butcher.render;

import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.TiledBlitRenderState;

/**
 * Fades the vanilla HUD out instead of cutting it - SkyMaxx's {@code HudFilter} (MIT), ported.
 *
 * The GUI builds a list of render states every frame, and each one carries its colour. While
 * {@link #begin} is in effect, {@code mixin/GuiRenderStateFadeMixin} takes every state added to
 * the list, and adds in its place a copy whose colours are multiplied by {@link #alpha} - blits,
 * filled rectangles, tiled blits and text. Items and picture-in-picture states (the hotbar's
 * items, the player in the inventory) cannot be tinted, so they are simply dropped once the fade
 * is past half way.
 *
 * Begun around the whole HUD pass (mixin/HudFadeMixin) - every mod's elements, which is what
 * SkyMaxx does and what hiding only vanilla's missed (Skyblocker's bars and sack lines stayed up).
 * Our own elements draw inside {@link #untouched}.
 */
public final class HudFade {
    private HudFade() {}

    private static boolean active;
    private static float alpha = 1f;
    /** Set while a faded copy is being added, so it goes through untouched. */
    private static boolean readding;
    /** Above 0 while our own elements draw inside the faded pass. */
    private static int untouched;
    /** Every state offered to the GUI's list, faded or not - a cheap check that something drew. */
    public static int added;

    /** This frame's GUI extractor and delta, kept from the HUD pass for drawing our cutscenes after it. */
    private static net.minecraft.client.gui.GuiGraphicsExtractor frameGraphics;
    private static net.minecraft.client.DeltaTracker frameDelta;

    public static void frame(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        frameGraphics = graphics;
        frameDelta = delta;
    }

    public static net.minecraft.client.gui.GuiGraphicsExtractor graphics() { return frameGraphics; }

    public static net.minecraft.client.DeltaTracker delta() { return frameDelta; }

    /** Draws something inside the faded HUD pass at full strength - our own cutscenes. */
    public static void untouched(Runnable draw) {
        untouched++;
        try {
            draw.run();
        } finally {
            untouched--;
        }
    }

    public static void begin(float a) {
        active = true;
        alpha = Math.max(0f, Math.min(1f, a));
        readding = false;
    }

    public static void end() {
        active = false;
        alpha = 1f;
        readding = false;
    }

    /** Whether a state being added right now should be replaced by a faded copy. */
    public static boolean filtering() {
        return active && !readding && untouched == 0;
    }

    /** Past half way, what cannot be faded is dropped. */
    public static boolean dropsUncoloured() {
        return alpha < 0.5f;
    }

    /** An ARGB colour with its alpha multiplied by the fade. */
    public static int scale(int argb) {
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** A faded copy of a state, or null to drop it. */
    public static GuiElementRenderState fade(GuiElementRenderState s) {
        if (alpha <= 0.004f) return null;
        if (s instanceof BlitRenderState b) {
            return new BlitRenderState(b.pipeline(), b.textureSetup(), b.pose(), b.x0(), b.y0(), b.x1(), b.y1(),
                    b.u0(), b.u1(), b.v0(), b.v1(), scale(b.color()), b.scissorArea(), b.bounds());
        }
        if (s instanceof ColoredRectangleRenderState r) {
            return new ColoredRectangleRenderState(r.pipeline(), r.textureSetup(), r.pose(), r.x0(), r.y0(), r.x1(), r.y1(),
                    scale(r.col1()), scale(r.col2()), r.scissorArea(), r.bounds());
        }
        if (s instanceof TiledBlitRenderState t) {
            return new TiledBlitRenderState(t.pipeline(), t.textureSetup(), t.pose(), t.tileWidth(), t.tileHeight(),
                    t.x0(), t.y0(), t.x1(), t.y1(), t.u0(), t.u1(), t.v0(), t.v1(), scale(t.color()),
                    t.scissorArea(), t.bounds());
        }
        // Anything else cannot be recoloured: kept while mostly visible, dropped after.
        return dropsUncoloured() ? null : s;
    }

    /** Adds a faded copy without it being faded again. */
    public static void readd(Runnable add) {
        readding = true;
        try {
            add.run();
        } finally {
            readding = false;
        }
    }
}
