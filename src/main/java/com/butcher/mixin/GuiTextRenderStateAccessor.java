package com.butcher.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The private fields of a text state, so {@link GuiRenderStateFadeMixin} can copy one faded. */
@Mixin(GuiTextRenderState.class)
public interface GuiTextRenderStateAccessor {
    @Accessor("font") Font butcher$font();
    @Accessor("text") FormattedCharSequence butcher$text();
    @Accessor("x") int butcher$x();
    @Accessor("y") int butcher$y();
    @Accessor("color") int butcher$color();
    @Accessor("backgroundColor") int butcher$backgroundColor();
    @Accessor("dropShadow") boolean butcher$dropShadow();
    @Accessor("includeEmpty") boolean butcher$includeEmpty();
}
