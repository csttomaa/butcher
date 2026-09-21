package com.scathabutcher.anim;

import com.scathabutcher.ScathaButcherClient;
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
            Identifier.fromNamespaceAndPath(ScathaButcherClient.MOD_ID, "textures/gui/prekill_veil.png");

    /** How long the dark takes to close all the way in. */
    private static final long RAMP_MS = 1100;

    /** When the current hide began, or -1 when nothing is being hidden. */
    private static long startedAt = -1;

    /**
     * How much of the HUD should still be visible, 1 down to 0, on the same ramp the
     * veil uses.
     *
     * The alternative to the veil rather than a part of it: with
     * {@code fadeHudDuringCutscene} on, {@code GuiHudMixin} fades the HUD itself by this
     * much and {@link #render} draws nothing. Sharing the ramp is what keeps the two
     * interchangeable - switching between them changes how the hide looks, never when it
     * happens or how long it takes.
     */
    public static float hudAlpha() {
        if (startedAt < 0) return 1f;
        float t = Math.min(1f, (System.currentTimeMillis() - startedAt) / (float) RAMP_MS);
        return 1f - t * t * (3f - 2f * t);
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

        // With the HUD fade on, the HUD is already receding on this exact ramp and the
        // veil would be a second, redundant darkening on top of it.
        if (ScathaButcherClient.config != null && ScathaButcherClient.config.fadeHudDuringCutscene) return;

        float t = Math.min(1f, (now - startedAt) / (float) RAMP_MS);
        // eased in, so it creeps at first and then closes quickly
        float p = t * t * (3f - 2f * t);
        int alpha = Math.round(255 * p);
        if (alpha < 3) return;

        Minecraft client = Minecraft.getInstance();
        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();
        g.blit(RenderPipelines.GUI_TEXTURED, VEIL, 0, 0, 0, 0, sw, sh, sw, sh, alpha << 24);
    }
}
