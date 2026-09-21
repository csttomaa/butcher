package com.scathabutcher.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the private fields of a text render state.
 *
 * {@link com.scathabutcher.render.HudFilter} fades the HUD by rebuilding each element
 * with a scaled alpha, which means reading what the original was built from. A text
 * state exposes only its pose and scissor publicly; everything that actually decides
 * what the text looks like is private, so there is no way to copy one without this.
 *
 * Accessors only - this adds no behaviour and changes nothing on its own.
 */
@Mixin(GuiTextRenderState.class)
public interface GuiTextRenderStateAccessor {
    @Accessor("font") Font scathabutcher$font();
    @Accessor("text") FormattedCharSequence scathabutcher$text();
    @Accessor("x") int scathabutcher$x();
    @Accessor("y") int scathabutcher$y();
    @Accessor("color") int scathabutcher$color();
    @Accessor("backgroundColor") int scathabutcher$backgroundColor();
    @Accessor("dropShadow") boolean scathabutcher$dropShadow();
    @Accessor("includeEmpty") boolean scathabutcher$includeEmpty();
}
