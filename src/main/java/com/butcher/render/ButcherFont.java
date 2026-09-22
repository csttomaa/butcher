package com.butcher.render;

import com.butcher.ButcherClient;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * The mod's own typeface.
 *
 * <h2>Why not the game's</h2>
 *
 * Minecraft's font is a 5x7 bitmap, and everything drawn in it reads as part of the game's
 * own furniture. That is exactly wrong for a full-screen cutscene, where the text is the
 * only thing on screen that is trying to be a title card.
 *
 * <h2>Which one, and why it was measured rather than picked</h2>
 *
 * Oswald, chosen against Rubik and Outfit by rendering all three at the size Minecraft
 * actually rasterises - about 9 to 11 pixels - rather than at a size that flatters them.
 * That test decided it: at 11px Rubik's strokes thin out until "corpses since" runs
 * together, and Outfit's variable default instance is so light it nearly vanishes. Oswald
 * is condensed with a tall x-height, so it stays crisp at the size it is actually used and
 * fits more into the menu's fixed panel width.
 *
 * SIL Open Font License; the licence ships beside the file in {@code assets/butcher/font/}
 * as that licence requires.
 *
 * <h2>Using it</h2>
 *
 * {@link #of} for a new line of text, {@link #style} for a Component that already exists.
 * Widths come out right on their own: {@code Font.width} measures the Component including
 * its style, so any layout that already centres or wraps by measuring keeps working. A
 * layout that hardcoded a pixel width for a known string would not - there are none here.
 *
 * If the font fails to load, the game falls back to the default and everything still
 * draws; nothing here can leave text unrendered.
 */
public final class ButcherFont {
    private ButcherFont() {}

    /** The provider in {@code assets/butcher/font/ui.json}. */
    public static final Identifier FONT = Identifier.fromNamespaceAndPath(ButcherClient.MOD_ID, "ui");

    // 26.2 takes a FontDescription rather than a bare Identifier - Style.withFont's
    // signature changed, and FontDescription.Resource is the resource-pack case.
    private static final Style STYLE = Style.EMPTY.withFont(new FontDescription.Resource(FONT));

    /** A line of text in the mod's font. */
    public static MutableComponent of(String text) {
        return Component.literal(text).withStyle(STYLE);
    }

    /** The same, for a Component that has already been built. */
    public static MutableComponent style(MutableComponent text) {
        return text.withStyle(STYLE);
    }

    /** A line in the mod's font, bold. */
    public static MutableComponent bold(String text) {
        return Component.literal(text).withStyle(STYLE.withBold(true));
    }
}
