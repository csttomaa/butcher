package com.butcher.mixin;

import com.butcher.ButcherClient;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ItemPickupParticleGroup;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Walking over a hidden slayer drop still plays vanilla's pickup: the item flies into the player. While dropped items
 * are hidden ({@link DroppedItemHideMixin}) that flight draws nothing either; the pickup itself is untouched.
 */
@Mixin(ItemPickupParticleGroup.class)
public class PickupHideMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void butcher$hidePickups(Frustum frustum, Camera camera, float partialTick,
                                     CallbackInfoReturnable<ParticleGroupRenderState> cir) {
        if (ButcherClient.hideDroppedItems()) cir.setReturnValue((collector, cameraState) -> { });
    }
}
