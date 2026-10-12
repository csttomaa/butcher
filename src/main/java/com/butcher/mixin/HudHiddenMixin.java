package com.butcher.mixin;

import com.butcher.ButcherClient;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the HUD is hidden for a kill or a cutscene, {@code Hud.isHidden()} - F1 - answers yes.
 *
 * For SBO: it draws its overlays, the Diana loot tracker among them, at the very end of
 * {@code Gui.extractRenderState}, after screens - outside everything GuiFadeMixin covers - and
 * skips them only when this says the HUD is hidden. The tracker counts a drop before the Chopping
 * Block reaches its split, so it gave the answer away (the user, 2026-10-02).
 *
 * Only the method answers; the field is untouched, so real F1 is unchanged and so is the HUD pass,
 * which reads the field. Vanilla asks the method too, so for as long as the hide lasts it also does
 * what F1 does there: no toasts, no mob or item-frame name tags, no F3 screen.
 */
@Mixin(Hud.class)
public class HudHiddenMixin {

    @Inject(method = "isHidden", at = @At("HEAD"), cancellable = true)
    private void butcher$hiddenWhileWeHide(CallbackInfoReturnable<Boolean> cir) {
        if (ButcherClient.shouldHideHud()) cir.setReturnValue(true);
    }
}
