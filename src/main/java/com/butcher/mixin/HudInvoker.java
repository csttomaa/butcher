package com.butcher.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's hotbar, hearts, hunger, armour and XP bar, drawn again over a cutscene ("Keep health and hotbar"). */
@Mixin(Hud.class)
public interface HudInvoker {

    @Invoker("extractHotbarAndDecorations")
    void butcher$hotbarAndDecorations(GuiGraphicsExtractor graphics, DeltaTracker delta);

    /** F1 itself: the field. The method answers true while we hide (HudHiddenMixin). */
    @Accessor("isHidden")
    boolean butcher$f1();
}
