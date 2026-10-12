package com.butcher.mixin;

import com.butcher.ButcherClient;
import com.butcher.render.HudFade;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where the HUD fade ({@link HudFade}) is on: from the moment the frame's GUI extractor exists
 * until the overlay and screen are drawn - so the whole HUD pass, and also what mods draw just
 * around it. SkyHanni draws its overlays there (MixinGui_GameRenderer: before and after the
 * {@code Hud.extractRenderState} call), outside the HUD pass, which is why fading only inside it
 * left them up. Screens, menus and toasts come after the window and are never faded.
 *
 * Our cutscenes are drawn right where the window closes, with the fade off, on a stratum of
 * their own - after the HUD, before any screen. 0.2.37 drew them inside the pass as HUD elements,
 * and they played their sounds and showed nothing.
 *
 * 26.2's order in {@code Gui.extractRenderState(DeltaTracker, boolean, boolean)}, read with javap:
 * new GuiGraphicsExtractor, Hud.extractRenderState, then {@code this.overlay}, then the screen.
 */
@Mixin(Gui.class)
public class GuiFadeMixin {

    private static final String EXTRACT = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V";

    @Inject(method = EXTRACT, at = @At("HEAD"))
    private void butcher$reset(DeltaTracker delta, boolean a, boolean b, CallbackInfo ci) {
        HudFade.end();
        HudFade.frame(null, null);
    }

    @Inject(method = EXTRACT, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/renderer/state/gui/GuiRenderState;II)V",
            shift = At.Shift.AFTER))
    private void butcher$beginFade(DeltaTracker delta, boolean a, boolean b, CallbackInfo ci) {
        float alpha = ButcherClient.hudAlpha();
        if (alpha < 0.996f) HudFade.begin(alpha);
    }

    @Inject(method = EXTRACT, at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/Gui;overlay:Lnet/minecraft/client/gui/screens/Overlay;",
            opcode = org.objectweb.asm.Opcodes.GETFIELD, ordinal = 0))
    private void butcher$endFadeAndDrawOurs(DeltaTracker delta, boolean a, boolean b, CallbackInfo ci) {
        HudFade.end();
        if (HudFade.graphics() != null) ButcherClient.drawOwnHud(HudFade.graphics(), HudFade.delta());
        HudFade.frame(null, null);
    }
}
