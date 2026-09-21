package com.scathabutcher.mixin;

import com.scathabutcher.ScathaButcherClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Refuses to swap the screen out from under a cutscene.
 *
 * <h2>What this replaces</h2>
 *
 * The cutscene is a {@link Screen}, and the game will happily replace it: Escape opens
 * the pause menu, E the inventory, T the chat box. The old answer was to notice
 * afterwards and put it back, from the end-of-tick handler - which works, but only ever
 * after the fact. For up to a tick the pause menu is genuinely open and the cutscene is
 * genuinely gone, and the player sees the flicker. Worse, "put it back" fights anything
 * with a legitimate reason to change the screen, and the last writer each tick wins.
 *
 * Refusing the change in the first place has neither problem. Nothing ever opens, so
 * there is nothing to undo and nothing to flicker, and the cutscene stops being in a
 * tug-of-war with the rest of the game.
 *
 * <h2>Two things this must not do</h2>
 *
 * <ul>
 *   <li><b>It must not block closing.</b> A null screen is the cutscene ending - that is
 *       how {@code ScopeMinigameScreen.close} hands control back - so cancelling it
 *       would trap the player behind a cutscene that has finished. Setting the SAME
 *       screen again is likewise let through, since it changes nothing.
 *   <li><b>It must not outlive the cutscene.</b> The guard is
 *       {@code currentScope.isActive()}, which is false the instant the screen finishes,
 *       rather than anything this mixin holds itself. A flag here could get stuck on and
 *       lock the player out of their own game, which is about the worst bug this mod
 *       could have.
 * </ul>
 *
 * <h2>It is Gui.setScreen on 26.2, not Minecraft.setScreen</h2>
 *
 * The reference mod targets {@code Minecraft.setScreen}. That method does not exist in
 * 26.2 - screen ownership moved to {@link Gui}, and what is left on {@code Minecraft} is
 * {@code setScreenAndShow}. Checked with {@code javap} before writing this, which is
 * what CLAUDE.md asks for and exactly the kind of rename that otherwise fails silently.
 */
@Mixin(Gui.class)
public class GuiScreenMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void scathabutcher$keepCutscene(Screen screen, CallbackInfo ci) {
        if (screen == null) return;                       // the cutscene closing
        if (screen == ScathaButcherClient.activeCutscene()) return;   // no change
        if (ScathaButcherClient.cutsceneOwnsScreen()) ci.cancel();
    }
}
