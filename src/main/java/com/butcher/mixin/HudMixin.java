package com.butcher.mixin;

import com.butcher.ButcherClient;
import com.butcher.anim.ButcherHudRenderer;
import com.butcher.anim.PreKillHider;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Takes the vanilla HUD off the screen while the cutscene owns it.
 *
 * <h2>Why this is the only place it can be done</h2>
 *
 * Drawing over the HUD cannot remove it, and fading it element by element was tried
 * here and did not work. The HUD is built in one method, so the way to not have a HUD
 * is to not call that method - which is what Scatha-Pro does for its own drop
 * animation, and this is a port of it.
 *
 * <b>It is {@code Hud.extractRenderState} on 26.2, not {@code Gui.extractRenderState}.</b>
 * The HUD moved into its own class between 26.1 and 26.2, and the two methods are not
 * interchangeable: the one left on {@code Gui} takes {@code (DeltaTracker, boolean,
 * boolean)} and additionally resets the render state, draws the {@code Screen}, replays
 * deferred subtitles and calls {@code applyCursor}. Cancelling THAT - which is what the
 * mixin this replaces was wrapping - stops the cutscene from being drawn at all and
 * leaves the cursor stale, which is exactly how it behaved: a faded HUD, no animation,
 * and no input until escape was pressed. {@code Hud.extractRenderState} is the HUD and
 * nothing but the HUD, so cancelling it is safe.
 *
 * <h2>What is kept</h2>
 *
 * <ul>
 * <li><b>Camera overlays</b> - the vignette, pumpkin, spyglass and portal overlays are
 *     re-run by hand before cancelling, as Scatha-Pro does. They belong to the view
 *     rather than to the HUD, and popping the vignette off a second before a kill is a
 *     visible change to the world the player is still looking at.
 * <li><b>Our own overlays.</b> Fabric's {@code HudElementRegistry} runs every registered
 *     element from inside this same method, so cancelling takes ours with it - including
 *     the pre-kill veil and the guillotine cutscene, which IS a HUD element rather than
 *     a Screen. Both are therefore called here instead. They cannot double-draw: on the
 *     frames this cancels, Fabric never reaches its copy, and on the frames it does not,
 *     this method returns before drawing anything.
 * </ul>
 *
 * Subtitles are dropped rather than deferred. {@code Hud} hands the subtitle overlay to
 * {@code Gui} as a {@code Runnable} to be run after the Screen; left alone, the one built
 * on the last un-hidden frame would be replayed on the first hidden one, putting a "Item
 * picked up" caption on screen at the exact moment the point is to not know that yet.
 */
@Mixin(Hud.class)
public class HudMixin {

    // Both @Shadow members are private on Hud, and are declared private here to match:
    // a shadow that widens a private target's visibility is accepted with a warning by
    // some Mixin versions and is simply not worth the coin toss on a mixin config with
    // defaultRequire 1, where anything unresolved takes the game down on load.
    @Shadow
    private void extractCameraOverlays(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        throw new AssertionError();
    }

    @Shadow
    private Runnable deferredSubtitles;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void butcher$hideHud(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (!ButcherClient.shouldHideHud()) return;

        extractCameraOverlays(graphics, delta);
        deferredSubtitles = null;

        ButcherHudRenderer.render(graphics, 0f);
        PreKillHider.render(graphics, ButcherClient.shouldHidePreKill());

        ci.cancel();
    }
}
