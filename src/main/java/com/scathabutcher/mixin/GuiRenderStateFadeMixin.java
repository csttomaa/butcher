package com.scathabutcher.mixin;

import com.scathabutcher.render.HudFilter;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catches each HUD element as it is submitted and puts back a faded copy.
 *
 * Does nothing at all unless {@link HudFilter#filtering()} is on, which only happens
 * inside {@link GuiHudMixin}'s wrap, which only happens while a cutscene owns the
 * screen. In ordinary play every one of these injections is a single boolean read.
 *
 * Three entry points because the render state keeps three lists: general elements,
 * blits added straight to the current layer, and text. Miss one and part of the HUD
 * stays at full brightness while the rest fades, which looks worse than not fading.
 *
 * Items are deliberately left alone. A {@code GuiItemRenderState} is a whole model
 * draw rather than a coloured quad, so there is no alpha to scale; fading the hotbar's
 * background but not the items in it is the honest partial result, and better than
 * hiding the player's items outright.
 */
@Mixin(GuiRenderState.class)
public class GuiRenderStateFadeMixin {

    @Inject(method = "addGuiElement", at = @At("HEAD"), cancellable = true)
    private void scathabutcher$fadeElement(GuiElementRenderState element, CallbackInfo ci) {
        if (!HudFilter.filtering()) return;
        ci.cancel();
        GuiElementRenderState faded = HudFilter.fade(element);
        if (faded != null) {
            GuiRenderState self = (GuiRenderState) (Object) this;
            HudFilter.readd(() -> self.addGuiElement(faded));
        }
    }

    @Inject(method = "addBlitToCurrentLayer", at = @At("HEAD"), cancellable = true)
    private void scathabutcher$fadeBlit(BlitRenderState blit, CallbackInfo ci) {
        if (!HudFilter.filtering()) return;
        ci.cancel();
        if (HudFilter.fade(blit) instanceof BlitRenderState faded) {
            GuiRenderState self = (GuiRenderState) (Object) this;
            HudFilter.readd(() -> self.addBlitToCurrentLayer(faded));
        }
    }

    @Inject(method = "addText", at = @At("HEAD"), cancellable = true)
    private void scathabutcher$fadeText(GuiTextRenderState text, CallbackInfo ci) {
        if (!HudFilter.filtering()) return;
        ci.cancel();
        GuiTextRenderState faded = HudFilter.fade(text);
        if (faded != null) {
            GuiRenderState self = (GuiRenderState) (Object) this;
            HudFilter.readd(() -> self.addText(faded));
        }
    }
}
