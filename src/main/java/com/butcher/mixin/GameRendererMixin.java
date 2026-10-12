package com.butcher.mixin;

import com.butcher.render.GameplayView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands {@link GameplayView} the world at the one moment the main render target holds it and
 * nothing else: after the level is drawn, just before the GUI goes on top. Ported from SkyMaxx
 * (MIT); on 26.2 {@code GuiRenderer.render()} takes no argument - checked with javap against
 * {@code GameRenderer.render(DeltaTracker, boolean)}, which calls it once per frame.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
    private void butcher$captureGameplay(DeltaTracker delta, boolean tick, CallbackInfo ci) {
        GameplayView.capture();
    }
}
