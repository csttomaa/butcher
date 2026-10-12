package com.butcher.detect;

import com.butcher.anim.SeraphTimeline.Drop;

import java.util.Locale;

/**
 * Reads the Voidgloom Seraph's drop lines (pure, tools/SeraphDetectTest). The same format as the Demonlord's, seen
 * in a friend's records (2026-10-05): "VERY RARE DROP! (◆ Endersnake Rune I) (+221% ✯ Magic Find)", "VERY RARE
 * DROP! (Null Atom) (+216% ✯ Magic Find)" - the item in the first brackets after the tier ({@link HellionDrops#item}).
 */
public final class SeraphDrops {
    private SeraphDrops() {
    }

    /** One of {@link Drop}'s wins, {@link Drop#BUST} for any other slayer drop, or null if the line is no drop line. */
    public static Drop of(String plain) {
        String item = HellionDrops.item(plain);
        if (item == null) {
            // The dye, if it is told the Diana way ("WOW! <player> found a Byzantium Dye") - not seen yet.
            if (plain != null && plain.trim().startsWith("WOW!") && plain.contains("Byzantium Dye")) return Drop.BYZANTIUM_DYE;
            return null;
        }
        String low = item.toLowerCase(Locale.ROOT);
        if (low.contains("byzantium dye")) return Drop.BYZANTIUM_DYE;
        if (low.contains("judgement core")) return Drop.JUDGEMENT_CORE;
        if (low.contains("end stone idol") || low.contains("endstone idol")) return Drop.END_STONE_IDOL;
        if (low.contains("ender artifact upgrade")) return Drop.ARTIFACT_UPGRADE;
        if (low.contains("enchant rune")) return Drop.ENCHANT_RUNE;
        if (low.contains("paragon")) return Drop.PARAGON;
        if (low.contains("summoning eye")) return Drop.SUMMONING_EYE;
        if (low.contains("etherwarp merger")) return Drop.ETHERWARP_MERGER;
        if (low.contains("blood chalice")) return Drop.BLOOD_CHALICE;
        if (low.contains("espresso")) return Drop.ESPRESSO;
        if (low.contains("sinful dice")) return Drop.SINFUL_DICE;
        if (low.contains("void conqueror")) return Drop.VOID_SKIN;
        return Drop.BUST;
    }

    /** The four that were under the 500k floor when the endings were built (2026-10-03): they play only above it. */
    public static boolean floored(Drop d) {
        return d == Drop.BLOOD_CHALICE || d == Drop.ESPRESSO || d == Drop.SINFUL_DICE || d == Drop.VOID_SKIN;
    }
}
