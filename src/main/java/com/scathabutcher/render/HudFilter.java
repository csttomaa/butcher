package com.scathabutcher.render;

import com.scathabutcher.mixin.GuiTextRenderStateAccessor;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;

/**
 * Fades the whole vanilla HUD, at the point where it is described rather than drawn.
 *
 * <h2>Why not just draw something over it</h2>
 *
 * That is what {@code PreKillHider} does: a dark sprite on top. It works, and it is
 * honest about what it is - but it can only ever darken towards one colour, it also
 * darkens anything drawn after it, and the hotbar stays fully opaque underneath a grey
 * wash rather than actually receding.
 *
 * In 26.2 the HUD is not drawn directly; every element is handed to a
 * {@code GuiRenderState} first and rendered in a batch afterwards. That gap is the
 * opportunity: catching each element on the way in and putting back a copy with its
 * alpha scaled fades the HUD itself, rather than covering it. Elements that would be
 * invisible are dropped outright, so at low alpha there is genuinely less to draw.
 *
 * Taken from SkyMaxx's {@code HudFilter} / {@code GuiRenderStateFadeMixin}.
 *
 * <h2>The re-entry guard is not optional</h2>
 *
 * The mixin cancels {@code addGuiElement} and then calls it again with a faded copy.
 * Without {@link #readd}, that second call is cancelled and re-faded too, forever, and
 * the game hangs on the first frame the filter is on. {@link #filtering()} answering
 * false while re-adding is the only thing standing between this and an infinite loop.
 *
 * Single-threaded by construction - the render thread is the only caller - so the state
 * here is plain fields rather than thread locals.
 */
public final class HudFilter {

    /** Below this alpha an element is dropped instead of drawn. */
    private static final int FAINTEST = 8;

    private static boolean active;
    private static boolean reentering;
    private static float alpha = 1f;

    private HudFilter() {}

    /** Starts fading, at {@code a} of full opacity. */
    public static void begin(float a) {
        active = true;
        alpha = a < 0f ? 0f : (a > 1f ? 1f : a);
    }

    /** Stops fading. Always called from a finally, so a thrown frame cannot leave it on. */
    public static void end() {
        active = false;
        alpha = 1f;
    }

    /** Whether elements should currently be intercepted. */
    public static boolean filtering() {
        return active && !reentering;
    }

    /** Re-adds a faded element without the filter catching it again. */
    public static void readd(Runnable add) {
        reentering = true;
        try {
            add.run();
        } finally {
            reentering = false;
        }
    }

    /** Scales an ARGB colour's alpha by the current fade. */
    public static int scale(int argb) {
        int a = Math.round((argb >>> 24) * alpha);
        return (a & 0xFF) << 24 | (argb & 0xFFFFFF);
    }

    /** Whether a colour is still worth drawing once faded. */
    public static boolean visible(int argb) {
        return (argb >>> 24) >= FAINTEST;
    }

    /**
     * A copy of {@code element} at the current alpha, or null if it would be invisible.
     *
     * Only the element types whose colour can actually be reached are handled. Anything
     * else is returned unchanged rather than dropped: an element this does not know how
     * to fade should still be drawn, because a HUD with holes in it is a worse failure
     * than one that does not fade evenly. Text is handled by the mixin itself, which has
     * the record's fields to hand.
     */
    public static GuiElementRenderState fade(GuiElementRenderState element) {
        if (element instanceof BlitRenderState blit) {
            int colour = scale(blit.color());
            if (!visible(colour)) return null;
            return new BlitRenderState(blit.pipeline(), blit.textureSetup(), blit.pose(),
                    blit.x0(), blit.y0(), blit.x1(), blit.y1(),
                    blit.u0(), blit.u1(), blit.v0(), blit.v1(),
                    colour, blit.scissorArea(), blit.bounds());
        }
        if (element instanceof ColoredRectangleRenderState rect) {
            int top = scale(rect.col1());
            int bottom = scale(rect.col2());
            if (!visible(top) && !visible(bottom)) return null;
            return new ColoredRectangleRenderState(rect.pipeline(), rect.textureSetup(), rect.pose(),
                    rect.x0(), rect.y0(), rect.x1(), rect.y1(),
                    top, bottom, rect.scissorArea(), rect.bounds());
        }
        return element;
    }

    /**
     * A copy of {@code text} at the current alpha, or null if it would be invisible.
     *
     * Everything but the pose and the scissor is private on the record, so it is read
     * back through {@link com.scathabutcher.mixin.GuiTextRenderStateAccessor}. The
     * background colour is faded too - left alone, text boxes stay solid while their
     * text disappears, which looks like a rendering fault rather than a fade.
     */
    public static GuiTextRenderState fade(GuiTextRenderState text) {
        GuiTextRenderStateAccessor in = (GuiTextRenderStateAccessor) (Object) text;
        int colour = scale(in.scathabutcher$color());
        int background = scale(in.scathabutcher$backgroundColor());
        if (!visible(colour)) return null;
        return new GuiTextRenderState(in.scathabutcher$font(), in.scathabutcher$text(), text.pose,
                in.scathabutcher$x(), in.scathabutcher$y(), colour, background,
                in.scathabutcher$dropShadow(), in.scathabutcher$includeEmpty(), text.scissor);
    }
}
