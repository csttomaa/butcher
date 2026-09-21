package com.butcher.anim;

import com.butcher.ButcherClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Darkness closing in over the HUD as a Scatha nears death, so the player can't
 * spot what dropped in their hotbar - or read a chat line about it - before the
 * cutscene reveals it.
 *
 * This used to be two solid black rectangles dropped over the hotbar and the
 * chat box. They did hide things, but they read as a rendering bug: two
 * hard-edged boxes appearing out of nowhere, at the exact moment the animation
 * is supposed to be building up to something.
 *
 * Instead a single soft veil fades in over about a second. Its shape does the
 * hiding - opaque across the bottom where the hotbar, status rows and chat all
 * live, fading upward to nothing, with a vignette pressing in from the edges -
 * so the middle of the screen, where the Scatha actually is, stays readable
 * right up until the cutscene takes over. The veil ends fully black at the
 * bottom and the cutscene opens from black, so the two run together as one
 * move rather than a cut.
 *
 * The fade is possible at all because the blit overload used here takes a
 * colour, and that colour carries an alpha.
 */
public final class PreKillHider {
    private PreKillHider() {}

    private static final Identifier VEIL =
            Identifier.fromNamespaceAndPath(ButcherClient.MOD_ID, "textures/gui/prekill_veil.png");

    /** How long the dark takes to close all the way in. */
    private static final long RAMP_MS = 1100;

    /** When the current hide began, or -1 when nothing is being hidden. */
    private static long startedAt = -1;

    /**
     * How much darker the veil has to get before the HUD can be taken away underneath it.
     *
     * Not 1: the veil is opaque black across the bottom well before the ramp finishes, so
     * waiting for the very end only delays the hide. Not much lower either - the whole
     * point is that the hotbar is already covered at the moment it stops being drawn, so
     * that nobody sees it go.
     */
    private static final float COVERED_AT = 0.85f;

    /** How far the veil has closed, 0 to 1, on the eased ramp it is drawn with. */
    public static float progress() {
        if (startedAt < 0) return 0f;
        float t = Math.min(1f, (System.currentTimeMillis() - startedAt) / (float) RAMP_MS);
        return t * t * (3f - 2f * t);
    }

    /**
     * True once the veil is dark enough to hide the HUD disappearing behind it.
     *
     * Read by {@link ButcherClient#shouldHideHud()}. Sharing this one ramp is what keeps
     * the veil and the HUD hide from looking like two separate events.
     */
    public static boolean closed() {
        return startedAt >= 0 && progress() >= COVERED_AT;
    }

    /**
     * Called every frame with whether the hide should be running, so the ramp can
     * start and reset itself without the caller tracking any state.
     */
    public static void render(GuiGraphicsExtractor g, boolean active) {
        long now = System.currentTimeMillis();
        if (!active) {
            startedAt = -1;
            return;
        }
        if (startedAt < 0) startedAt = now;

        // eased in, so it creeps at first and then closes quickly
        int alpha = Math.round(255 * progress());
        if (alpha < 3) return;

        Minecraft client = Minecraft.getInstance();
        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();
        g.blit(RenderPipelines.GUI_TEXTURED, VEIL, 0, 0, 0, 0, sw, sh, sw, sh, alpha << 24);
    }
}
