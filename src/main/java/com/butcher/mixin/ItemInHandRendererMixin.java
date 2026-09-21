package com.butcher.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.butcher.ButcherClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels the first-person held-item/hand render entirely while
 * {@link ButcherClient#shouldHidePreKill()} is true, so a freshly
 * picked-up pet/item can't flash into view in the player's hand for a frame
 * before the drop cutscene reveals it.
 *
 * Our {@code PreKillHider} HUD overlay (solid black rectangles drawn over
 * the hotbar/chat) can't reach this - the held-item model is rendered in
 * first-person 3D space, not as part of the 2D GUI layer at all, so no 2D
 * overlay could ever cover it. Cancelling the render outright is the only
 * way to actually hide it.
 *
 * The class, injection point and parameter list come from Scatha-Pro's own
 * compiled `ItemInHandRendererMixin.class` (read with javap), which does
 * exactly this. The METHOD NAME had to be corrected, though: Scatha-Pro is
 * built against 26.1, where it is "renderHandsWithItems", and 26.2 renamed it
 * to "submitHandsWithItems" (confirmed by running javap over this instance's
 * own mapped 26.2 jar - 26.2's ItemInHandRenderer has no renderHandsWithItems
 * at all). With "defaultRequire": 0 in butcher.mixins.json a name that
 * doesn't resolve fails silently, so the old name meant this mixin simply
 * never ran.
 *
 * Deliberately NOT doing the other half of Scatha-Pro's technique (a
 * Mixin into Gui.extractRenderState to cancel the whole vanilla HUD): our
 * mod draws its own cutscene/hider via Fabric API's HudElementRegistry,
 * which very likely dispatches its registered elements from within that
 * same vanilla method (that's the standard way such HUD-element APIs are
 * implemented) - cancelling it at HEAD risks silently cancelling our own
 * HudElementRegistry callbacks too and breaking the whole mod's rendering,
 * for a gap (hotbar/chat) the existing PreKillHider overlay already covers.
 * This mixin only touches a class our own rendering never goes near, so
 * there's no such risk here.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void butcher$beforeSubmitHandsWithItems(float partialTick, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, LocalPlayer player, int combinedLight, CallbackInfo ci) {
        if (ButcherClient.shouldHidePreKill()) {
            ci.cancel();
        }
    }
}
