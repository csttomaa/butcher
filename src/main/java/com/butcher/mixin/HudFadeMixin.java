package com.butcher.mixin;

import com.butcher.ButcherClient;
import com.butcher.render.HudFade;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Once the HUD has faded all the way out, the HUD pass is skipped altogether - vanilla's elements
 * and every mod's registered ones - SkyMaxx's {@code GuiHudMixin} (MIT). The fade, what other mods
 * draw around this call, and our own cutscenes are {@link GuiFadeMixin}'s.
 *
 * Also keeps this frame's extractor and delta for GuiFadeMixin, which draws the cutscenes after
 * the pass and has no other way to reach them - taken at HEAD, before any skip, and even under F1.
 *
 * <b>It is {@code Hud.extractRenderState} on 26.2</b>, called from
 * {@code Gui.extractRenderState(DeltaTracker, boolean, boolean)}.
 */
@Mixin(Hud.class)
public class HudFadeMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"), cancellable = true)
    private void butcher$skipHiddenHud(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        HudFade.frame(graphics, delta);
        if (ButcherClient.hudAlpha() <= 0.004f) ci.cancel();
    }
}
