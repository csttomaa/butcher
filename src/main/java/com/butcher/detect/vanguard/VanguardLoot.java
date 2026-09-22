package com.butcher.detect.vanguard;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What a Vanguard Frozen Corpse can give up, and how often.
 *
 * <h2>Where these numbers come from</h2>
 *
 * Two independent sources that agree, which is the only reason they are trusted enough to
 * drive the animation's pacing.
 *
 * SkyOcean ships the same table as per-item weights in its remote repo. The wiki publishes
 * it split by stack size, with a percentage against each row. Collapsing the wiki's rows
 * to one weight per item reproduces SkyOcean's file exactly - Glacite Amalgamation's
 * 160 + 80 + 40 for its 1x/2x/4x rows is the 280 below - and the weights here sum to
 * {@link #TOTAL_WEIGHT}, against which 160 is 4.55%, which is the figure the wiki prints.
 * Two sources, one arithmetic check, no guessing.
 *
 * <h2>Tiers</h2>
 *
 * Three, because the flare only has three colours to say things with. The cut is not
 * arbitrary: {@code LEGENDARY} is exactly the five items SkyOcean itself singles out as
 * "valuables", and they are the only ones a player would tell someone about. Their
 * combined weight is 36 in 3516, a touch over 1% per roll - which works out at about one
 * corpse in fifteen carrying at least one. That rate is the whole reason this feature is
 * worth building: it is rare enough to be a moment and common enough to actually see.
 *
 * <h2>Names</h2>
 *
 * Keyed by the name as it appears in chat, because that is all the loot message gives us.
 *
 * <b>Verified against the NEU item repository</b> rather than guessed - the copy Firmament
 * keeps extracted on disk, which carries each item's real {@code displayname}. Twenty-four
 * of the twenty-five were already right; the twenty-fifth is the Shattered Locket, whose
 * item id is {@code SHATTERED_PENDANT} (which is what SkyOcean's repo file calls it) while
 * its display name is "Shattered Locket". Both are in the table.
 *
 * The four flawless gemstones are printed with a symbol in front - "☠ Flawless Onyx
 * Gemstone", "☘" for citrine and peridot, "☂" for aquamarine. They are matched
 * without it, because {@link #key} throws away everything that is not a letter, a digit or
 * a space. That is the reason it does.
 *
 * A name that still fails to match resolves as {@link Tier#COMMON} and the miss is
 * narrated into the decision log, so a rename on Hypixel's side degrades the flare rather
 * than breaking the parse.
 *
 * <h2>Colours</h2>
 *
 * Each item's SkyBlock rarity colour, read out of the same NEU repository as the names -
 * the leading formatting code on its {@code displayname}. Held here rather than parsed out
 * of the loot message, because four of these arrive inside a sack and never appear in chat
 * with a colour at all.
 */
public final class VanguardLoot {
    private VanguardLoot() {}

    /**
     * How good a drop is, in the only terms the animation needs.
     *
     * <b>Declared worst to best, and the order is load-bearing.</b> {@code CorpseHaul}
     * sorts and compares on {@link Enum#ordinal()}, so the reveal order and "what is the
     * best thing in this haul" both fall out of the order below. Insert a tier in the
     * middle and both follow automatically; insert one in the wrong place and the climb
     * ends on the wrong item.
     *
     * {@code SPECIAL} exists for the Frostbitten Dye alone. It sits above LEGENDARY rather
     * than inside it because it is a different kind of event: the other four legendaries
     * turn up about once in fifteen corpses between them, and the Dye is some twenty-five
     * times rarer than any single one of them. Most players will never see it. Giving it
     * the gold treatment would spend the same fanfare on a thing that happens weekly and a
     * thing that happens once.
     */
    public enum Tier {
        COMMON(0xFFBFC6D4),
        UNCOMMON(0xFF55FFFF),
        LEGENDARY(0xFFFFAA00),
        SPECIAL(0xFFFF5FC8);

        private final int glow;

        Tier(int glow) { this.glow = glow; }

        /**
         * The colour the animation lights up in for this tier.
         *
         * Not the item's own colour - that is {@link Entry#colour()} and is its SkyBlock
         * rarity. This is the glow behind it, and there are four of these to the table's
         * many, because it is the escalation the player is being asked to read.
         *
         * The Dye's is pink and deliberately not gold: gold is the "you won" colour here,
         * and the Dye needs to be legible at a glance as something else entirely.
         */
        public int glow() { return glow; }
    }

    /**
     * One row of the table.
     *
     * {@code tier} and {@code colour} measure different things and are deliberately not
     * derived from one another. The tier is how rare the drop is <i>out of a corpse</i>
     * and drives the animation's escalation; the colour is the item's SkyBlock rarity and
     * only decides how it is painted. They disagree sharply in places - a Flawless
     * Gemstone is dark purple and is also the commonest thing in the pool at 6.83%, four
     * ways over. Tying the escalation to the colour would fire a big reveal on roughly one
     * corpse in three, which is how a payoff stops being one.
     */
    public record Entry(String name, int weight, Tier tier, int colour) {
        /** Chance this item is the result of any one roll. */
        public float perRoll() { return weight / (float) TOTAL_WEIGHT; }
    }

    /** The weights below add up to this. Every percentage in the mod divides by it. */
    public static final int TOTAL_WEIGHT = 3516;

    /** Rolls per corpse: 5 to 8, averaging 6.5 - before the perks that add about 0.4. */
    public static final int MIN_ROLLS = 5, MAX_ROLLS = 8;
    public static final float AVG_ROLLS = 6.5f;

    /** What an item we have never heard of is painted. Vanilla's "common item" white. */
    public static final int UNKNOWN_COLOUR = 0xFFFFFFFF;

    private static final Map<String, Entry> BY_NAME = new LinkedHashMap<>();

    private static void add(String name, int weight, Tier tier, int colour) {
        BY_NAME.put(key(name), new Entry(name, weight, tier, colour));
    }

    static {
        // In a tier of its own - see Tier.SPECIAL.
        add("Frostbitten Dye",                    1, Tier.SPECIAL,   0xFF00AAAA);   // dark aqua

        // The four that matter. 35 of 3516 between them.
        add("Shattered Locket",                   5, Tier.LEGENDARY, 0xFFFFAA00);   // gold; item id is SHATTERED_PENDANT
        add("Caged Wisp",                        10, Tier.LEGENDARY, 0xFFFFAA00);   // gold
        add("Frozen Scute",                      10, Tier.LEGENDARY, 0xFFFF55FF);   // light purple
        add("Skeleton Key",                      10, Tier.LEGENDARY, 0xFFFFAA00);   // gold
        add("Umber Plate",                       30, Tier.UNCOMMON, 0xFF5555FF);   // blue
        add("Tungsten Plate",                    30, Tier.UNCOMMON, 0xFF5555FF);   // blue
        add("Mithril Plate",                     60, Tier.UNCOMMON, 0xFF5555FF);   // blue
        add("Dwarven O's Metallic Minis",       120, Tier.UNCOMMON, 0xFFFFAA00);   // gold
        add("Opal Crystal",                     120, Tier.UNCOMMON, 0xFFFFFFFF);   // white
        add("Onyx Crystal",                     120, Tier.UNCOMMON, 0xFF555555);   // dark gray
        add("Peridot Crystal",                  120, Tier.UNCOMMON, 0xFF00AA00);   // dark green
        add("Citrine Crystal",                  120, Tier.UNCOMMON, 0xFFAA0000);   // dark red
        add("Aquamarine Crystal",               120, Tier.UNCOMMON, 0xFF5555FF);   // blue
        add("Umber Key",                        140, Tier.UNCOMMON, 0xFFAA00AA);   // dark purple
        add("Tungsten Key",                     140, Tier.UNCOMMON, 0xFFAA00AA);   // dark purple
        add("Ice Cold I",                       160, Tier.COMMON, 0xFFFFFFFF);   // white
        add("Flawless Onyx Gemstone",           240, Tier.COMMON, 0xFFAA00AA);   // dark purple
        add("Flawless Citrine Gemstone",        240, Tier.COMMON, 0xFFAA00AA);   // dark purple
        add("Flawless Peridot Gemstone",        240, Tier.COMMON, 0xFFAA00AA);   // dark purple
        add("Flawless Aquamarine Gemstone",     240, Tier.COMMON, 0xFFAA00AA);   // dark purple
        add("Suspicious Scrap",                 240, Tier.COMMON, 0xFF5555FF);   // blue
        add("Blue Goblin Egg",                  240, Tier.COMMON, 0xFF00AAAA);   // dark aqua
        add("Refined Umber",                    240, Tier.COMMON, 0xFFAA00AA);   // dark purple
        add("Refined Tungsten",                 240, Tier.COMMON, 0xFFAA00AA);   // dark purple
        add("Glacite Amalgamation",             280, Tier.COMMON, 0xFFAA00AA);   // dark purple
    }

    /**
     * Lower-cased, punctuation-free, single-spaced.
     *
     * Hypixel's chat names carry formatting codes and the odd apostrophe or roman numeral;
     * matching on the letters alone means "Dwarven O's Metallic Minis" still resolves when
     * the apostrophe turns out to be a different character than the one typed here.
     */
    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").trim();
    }

    /** The table row for a chat name, or null when nothing matches. */
    public static Entry find(String chatName) {
        return BY_NAME.get(key(chatName));
    }

    /** The row for a chat name, falling back to a common of unknown weight. */
    public static Entry findOrCommon(String chatName) {
        Entry known = find(chatName);
        return known != null ? known : new Entry(chatName, 0, Tier.COMMON, UNKNOWN_COLOUR);
    }

    /** Every row, in the order declared above - rarest first. */
    public static Iterable<Entry> all() { return BY_NAME.values(); }

    /**
     * Chance that a corpse of {@code rolls} rolls contains at least one legendary.
     *
     * Counts {@link Tier#SPECIAL} as well. The Dye is better than a legendary, not other
     * than one, so a corpse that drops it has plainly ended a dry streak - and leaving it
     * out would make the figure the streak is measured against quietly wrong.
     */
    public static float legendaryChance(float rolls) {
        float perRoll = 0f;
        for (Entry e : BY_NAME.values()) {
            if (e.tier.ordinal() >= Tier.LEGENDARY.ordinal()) perRoll += e.perRoll();
        }
        return 1f - (float) Math.pow(1f - perRoll, rolls);
    }
}
