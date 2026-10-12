package com.butcher.detect;

import com.butcher.anim.HellionTimeline.Drop;

import java.util.Locale;

/**
 * Reads the Inferno Demonlord's drop lines (pure, tested offline by tools/HellionDetectTest).
 *
 * Written against a friend's real tier IV session (2026-10-03, 13 kills - the user does not do slayers).
 * The format, settled from those lines and not from guesses:
 * <pre>
 *   VERY RARE DROP! (Scorched Power Crystal) (+236% ✯ Magic Find)
 *   RARE DROP! (Bundle of Magma Arrows) (+236% ✯ Magic Find)
 *   CRAZY RARE DROP! (◆ Lavatears Rune I) (+220% ✯ Magic Find)
 *   VERY RARE DROP! (5x Wisp's Ice-Flavored Water I Splash Potion) (+193% ✯ Magic Find)
 * </pre>
 * The item is the first bracketed group after the tier; Magic Find rides along in the second. Not every
 * slayer drop has the brackets: a mini-boss's "RARE DROP! Netherrack-Looking Sunshade (+295 ✯ Magic Find)"
 * (and Diana's lines, which share the "RARE DROP!" words) do not. Only game lines reach here; a line with
 * ": " is someone's chat (party, co-op, guild) quoting a drop, never ours.
 */
public final class HellionDrops {

    private HellionDrops() {
    }

    /** The tier word of the last drop line read, and when: the banner stamps Hypixel's real word (the review, A7). */
    private static String lastTier;
    private static long lastTierAt;

    /** The last drop line's tier word if it was read in the last 10 s, else null. */
    public static String freshTier() {
        return lastTier != null && System.currentTimeMillis() - lastTierAt < 10_000 ? lastTier : null;
    }

    /** A tier word's colour as Hypixel's chat gives it (RNGesus dark purple, crazy rare pink, very rare blue, rare gold). */
    public static int tierColour(String tier) {
        if (tier == null) return 0xAA00AA;
        return switch (tier) {
            case "CRAZY RARE DROP!" -> 0xFF55FF;
            case "VERY RARE DROP!" -> 0x5555FF;
            case "RARE DROP!" -> 0xFFAA00;
            default -> 0xAA00AA;
        };
    }

    private static final String[] TIERS = {"PRAY TO RNGESUS DROP!", "CRAZY RARE DROP!", "VERY RARE DROP!", "RARE DROP!"};

    /** The item a slayer drop line names, or null if the line is not one: "(Scorched Power Crystal)" -> "Scorched Power Crystal". */
    public static String item(String plain) {
        if (plain == null) return null;
        String s = plain.trim();
        if (s.contains(": ") || s.startsWith("[")) return null;
        for (String tier : TIERS) {
            if (!s.startsWith(tier)) continue;
            String rest = s.substring(tier.length()).trim();
            if (!rest.startsWith("(")) return null;
            lastTier = tier;
            lastTierAt = System.currentTimeMillis();
            // The bracket that closes the first one (the item may hold its own, e.g. "(Enchanted Book (Duplex I))").
            int depth = 0;
            for (int i = 0; i < rest.length(); i++) {
                char c = rest.charAt(i);
                if (c == '(') depth++;
                else if (c == ')' && --depth == 0) return clean(rest.substring(1, i));
            }
            return null;
        }
        // The dye is told in the way the Diana dye is: "WOW! <player> found a Flame Dye" - not seen yet.
        if (s.startsWith("WOW!") && s.contains("Flame Dye")) return "Flame Dye";
        return null;
    }

    /** Leading symbols ("◆ ") and a stack count ("5x ") off the name. */
    private static String clean(String name) {
        String s = name.trim();
        while (!s.isEmpty() && !Character.isLetterOrDigit(s.charAt(0))) s = s.substring(1).trim();
        if (s.matches("^\\d+x .*")) s = s.substring(s.indexOf(' ') + 1);
        return s;
    }

    /**
     * The animation's drop for a slayer drop line: one of {@link Drop}'s wins, {@link Drop#BUST} for any other
     * slayer drop (the kill dropped something, just nothing the animation plays), or null if it is no drop line.
     */
    public static Drop of(String plain) {
        String item = item(plain);
        if (item == null) return null;
        String low = item.toLowerCase(Locale.ROOT);
        // High Class first: its name holds the plain dice's.
        if (low.contains("high class archfiend dice")) return Drop.HIGH_CLASS_DICE;
        if (low.contains("archfiend dice")) return Drop.ARCHFIEND_DICE;
        if (low.contains("flame dye")) return Drop.FLAME_DYE;
        if (low.contains("duplex")) return Drop.DUPLEX;
        if (low.contains("scorched power crystal")) return Drop.SCORCHED_CRYSTAL;
        if (low.contains("subzero inverter")) return Drop.SUBZERO;
        if (low.contains("wilson")) return Drop.WILSONS;
        if (low.contains("paragon")) return Drop.PARAGON;
        return Drop.BUST;
    }
}
