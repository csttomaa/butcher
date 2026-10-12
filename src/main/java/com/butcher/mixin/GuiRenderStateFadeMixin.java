package com.butcher.mixin;

import com.butcher.render.HudFade;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While {@link HudFade} is in effect, every state added to the GUI's list is swapped for a faded
 * copy - SkyMaxx's {@code GuiRenderStateFadeMixin} (MIT), ported. Does nothing otherwise: one
 * boolean read per state.
 */
@Mixin(GuiRenderState.class)
public class GuiRenderStateFadeMixin {

    @Inject(method = "addGuiElement", at = @At("HEAD"), cancellable = true)
    private void butcher$fadeElement(GuiElementRenderState state, CallbackInfo ci) {
        HudFade.added++;
        if (!HudFade.filtering()) return;
        ci.cancel();
        GuiElementRenderState faded = HudFade.fade(state);
        GuiRenderState self = (GuiRenderState) (Object) this;
        if (faded != null) HudFade.readd(() -> self.addGuiElement(faded));
    }

    @Inject(method = "addBlitToCurrentLayer", at = @At("HEAD"), cancellable = true)
    private void butcher$fadeBlit(BlitRenderState state, CallbackInfo ci) {
        HudFade.added++;
        if (!HudFade.filtering()) return;
        ci.cancel();
        GuiElementRenderState faded = HudFade.fade(state);
        GuiRenderState self = (GuiRenderState) (Object) this;
        if (faded instanceof BlitRenderState blit) HudFade.readd(() -> self.addBlitToCurrentLayer(blit));
    }

    @Inject(method = "addText", at = @At("HEAD"), cancellable = true)
    private void butcher$fadeText(GuiTextRenderState state, CallbackInfo ci) {
        HudFade.added++;
        if (!HudFade.filtering()) return;
        ci.cancel();
        GuiTextRenderStateAccessor t = (GuiTextRenderStateAccessor) (Object) state;
        int colour = HudFade.scale(t.butcher$color());
        // The font draws alpha below 4 fully opaque (CLAUDE.md) - stop before that.
        if (((colour >>> 24) & 0xFF) < 5) return;
        GuiTextRenderState faded = new GuiTextRenderState(t.butcher$font(), t.butcher$text(), state.pose,
                t.butcher$x(), t.butcher$y(), colour, HudFade.scale(t.butcher$backgroundColor()),
                t.butcher$dropShadow(), t.butcher$includeEmpty(), state.scissor);
        GuiRenderState self = (GuiRenderState) (Object) this;
        HudFade.readd(() -> self.addText(faded));
    }

    @Inject(method = "addItem", at = @At("HEAD"), cancellable = true)
    private void butcher$dropItem(GuiItemRenderState state, CallbackInfo ci) {
        if (HudFade.filtering() && HudFade.dropsUncoloured()) ci.cancel();
    }

    @Inject(method = "addPicturesInPictureState", at = @At("HEAD"), cancellable = true)
    private void butcher$dropPicture(PictureInPictureRenderState state, CallbackInfo ci) {
        if (HudFade.filtering() && HudFade.dropsUncoloured()) ci.cancel();
    }
}
