package com.butcher.detect;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The two Ghost drops worth an announcement (the user, 2026-10-12): Sorrow and Plasma, from the Ghosts in the Dwarven
 * Mines' Mist. Read from the drop's chat line; pure, so it can be checked offline.
 *
 * <b>The real line</b> (a friend's records, 2026-10-12, 32 Sorrow and 38 Plasma): {@code RARE DROP! Sorrow (+325 ✯ Magic
 * Find)}, the same for Plasma - the item bare, no brackets. Any "... DROP!" prefix is still accepted. The wiki gives
 * Sorrow 0.12% and Plasma 0.1%. A Ghost has no nametag or health bar - the drop line is all there is.
 */
public final class GhostDrops {
    private GhostDrops() {}

    public enum Drop {
        SORROW("Sorrow", 0x5555FF, "SORROW"),
        PLASMA("Plasma", 0x5555FF, "PLASMA");

        public final String title;
        /** Its rarity colour: both are RARE (blue). */
        public final int colour;
        /** Its bazaar product id. */
        public final String priceId;

        Drop(String title, int colour, String priceId) {
            this.title = title;
            this.colour = colour;
            this.priceId = priceId;
        }
    }

    /** "RARE DROP! ...", "VERY RARE DROP! ..." - the drop itself after it. */
    private static final Pattern DROP = Pattern.compile("^[A-Z][A-Z ]*DROP! (.+)$");

    /** What a chat line says dropped, or null. Colour codes are stripped first. */
    public static Drop of(String line) {
        if (line == null) return null;
        String s = line.replaceAll("§.", "").strip();
        Matcher m = DROP.matcher(s);
        if (!m.find()) return null;
        String what = m.group(1);
        // The name first, alone - "Sorrow (+120% Magic Find)" - not inside another item's name.
        if (what.matches("^(\\d+x )?Sorrow\\b.*")) return Drop.SORROW;
        if (what.matches("^(\\d+x )?Plasma\\b.*")) return Drop.PLASMA;
        return null;
    }
}
