package com.scathabutcher.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.scathabutcher.ScathaButcherClient;
import com.scathabutcher.render.HudFilter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Wraps the HUD's own submission, so the cutscene can decide how much of it to show.
 *
 * <h2>Why wrap rather than draw over</h2>
 *
 * Drawing on top can only ever add; it cannot take the hotbar away, and it cannot make
 * the HUD partly transparent. Wrapping the call that BUILDS the HUD can do both -
 * skipping {@code original.call} suppresses it entirely, and calling it with
 * {@link HudFilter} armed submits the whole thing at a reduced alpha.
 *
 * That is what retires the pre-kill veil: instead of laying a dark sprite over the
 * hotbar to stop the player reading their inventory before the reveal, the hotbar
 * itself fades out and back.
 *
 * <h2>Safety</h2>
 *
 * The filter is armed and disarmed around one call, with the disarm in a finally, so a
 * HUD element that throws mid-frame cannot leave every later frame faded. And
 * {@link ScathaButcherClient#hudAlpha()} returns 1 whenever no cutscene is running, in
 * which case this method does nothing but forward the call.
 */
@Mixin(value = Gui.class, priority = 5000)
public class GuiHudMixin {

    @WrapMethod(method = "extractRenderState")
    private void scathabutcher$ownTheHud(DeltaTracker delta, boolean a, boolean b, Operation<Void> original) {
        float alpha = ScathaButcherClient.hudAlpha();
        if (alpha >= 0.999f) {
            original.call(delta, a, b);
            return;
        }

        // original.call is ALWAYS made. It was skipped here when the fade reached zero,
        // on the assumption this method only draws the HUD. It does not: it also calls
        // Screen.extractRenderStateWithTooltipAndSubtitles, GuiRenderState.reset and
        // applyCursor. Skipping it therefore stopped the cutscene - which IS a Screen -
        // from ever being drawn, left the cursor state stale, and froze the game behind
        // a faded HUD until the player pressed escape. Whatever this wrap wants, it can
        // only ever be done by filtering what the call submits, never by not making it.
        HudFilter.begin(alpha);
        try {
            original.call(delta, a, b);
        } finally {
            HudFilter.end();
        }
    }
}
