package com.butcher.ledger;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Ledger's rules: what each stamp counts, which grade that reaches, and the luck stamps that look back over the
 * last drops. Pure - no Minecraft, no files - so tools/LedgerTest runs it offline. ButcherStats owns the data and
 * calls in here whenever a number moves; the book and the pop-up only read.
 *
 * Two kinds of stamp: a count (kills, drops, a worst dry run...) read from {@link Facts} every time, so the stats
 * already on disk earn their stamps the first time it runs; and an event (a hot streak, a drop at 3 AM...) that has
 * to be seen happening, kept in {@link LedgerData#flags} by {@link #award}.
 */
public final class Ledger {
    private Ledger() {}

    public static final List<Stamp> STAMPS = StampTable.ALL;
    /** How many rare drops the luck stamps can look back over. */
    public static final int RECENT = 60;
    /** "Hot Streak": two rare drops this close together. */
    public static final long HOT_STREAK_MS = 10 * 60_000L;

    private static final Map<String, Stamp> BY_ID = new HashMap<>();
    private static final Map<Chapter, List<Stamp>> BY_CHAPTER = new EnumMap<>(Chapter.class);

    static {
        for (Chapter c : Chapter.values()) BY_CHAPTER.put(c, new ArrayList<>());
        for (Stamp s : STAMPS) {
            BY_ID.put(s.id(), s);
            BY_CHAPTER.get(s.chapter()).add(s);
        }
    }

    public static Stamp byId(String id) {
        return BY_ID.get(id);
    }

    public static List<Stamp> chapter(Chapter c) {
        return BY_CHAPTER.get(c);
    }

    /** Every grade there is to earn: a single stamp is one, a tiered one each of its tiers. */
    public static int totalGrades() {
        int n = 0;
        for (Stamp s : STAMPS) n += s.grades();
        return n;
    }

    public static int earnedGrades(LedgerData d) {
        int n = 0;
        for (Stamp s : STAMPS) n += grade(s, d);
        return n;
    }

    /** The grade earned so far (0 = not yet). */
    public static int grade(Stamp s, LedgerData d) {
        LedgerData.Entry e = d.stamps.get(s.id());
        return e == null ? 0 : Math.min(e.grade, s.grades());
    }

    // ------------------------------------------------------------------ what each stamp counts

    /** The number a stamp is measured by. For a stamp about one thing having happened: how many times (0 = never). */
    public static long value(Stamp s, Facts f, LedgerData d) {
        return switch (s.id()) {
            case "house_open" -> d.cutscenes;
            case "house_regular" -> d.cutscenes;
            case "house_full_menu" -> f.scathaKills > 0 && f.corpses > 0 && f.dianaKills > 0 && f.kills("blaze") > 0
                    && f.kills("lava") > 0 && f.kills("shark") > 0 && f.drops("ghost") > 0 ? 1 : 0;
            case "house_always_wins" -> d.worstBustRun;
            case "house_tycoon" -> d.coins;
            case "house_places_to_be" -> d.skips;

            case "scatha_worm_food" -> f.scathaKills;
            case "scatha_rare_cut" -> f.petsRare;
            case "scatha_choice_cut" -> f.petsEpic;
            case "scatha_prime_cut" -> f.petsLegendary;
            case "scatha_full_set" -> Math.min(f.petsRare, Math.min(f.petsEpic, f.petsLegendary));
            // Reached when the first pet came within the stamp's number of kills: the value is that number then.
            case "scatha_beginners_luck" -> d.firstPetKill > 0 && d.firstPetKill <= s.number(20) ? s.number(20) : 0;
            case "scatha_dry_aged", "scatha_dry_aged_45" -> f.scathaWorstDry;
            case "scatha_slippery" -> f.scathaMisses;
            case "scatha_dozen" -> f.pets();

            case "vg_thawed_out" -> f.corpses;
            case "vg_cold_hands" -> f.corpseLegendaries;
            case "vg_heart_of_glass" -> f.lockets;
            case "vg_frostbitten" -> f.corpseDyes;
            case "vg_ice_cold" -> f.corpseWorstDry;

            case "diana_heads" -> f.dianaKills;
            case "diana_brain_food" -> f.dianaDrop("Brain Food");
            case "diana_chimera" -> f.dianaDrop("Chimera I");
            case "diana_stinger" -> f.dianaDrop("Fateful Stinger");
            case "diana_manticore" -> f.dianaDrop("Manti-core");
            case "diana_crown" -> f.dianaDrop("Crown of Greed");
            case "diana_wool" -> f.dianaDrop("Shimmering Wool");
            case "diana_relic" -> f.dianaDrop("Minos Relic");
            case "diana_dye" -> f.dianaDrop("Mythological Dye");
            case "diana_pantheon" -> {
                long min = Long.MAX_VALUE;
                for (String n : DIANA_DROPS) min = Math.min(min, f.dianaDrop(n));
                yield min;
            }

            case "hellion_fire_watch" -> f.kills("blaze");
            case "hellion_double_down" -> f.drops("blaze", "Duplex I");
            case "hellion_roll_dice" -> f.drops("blaze", "Archfiend Dice");
            case "hellion_high_roller" -> f.drops("blaze", "High Class Archfiend Dice");
            case "hellion_playing_fire" -> f.drops("blaze", "Flame Dye");
            case "hellion_ashes" -> f.worstDry("blaze");

            case "lava_magma_maker" -> f.kills("lava");
            case "lava_flash" -> f.drops("lava", "Flash I");
            case "lava_meltdown" -> f.drops("lava", "Radioactive Vial");
            case "lava_end_times" -> f.drops("lava", "Burnt Texts");
            case "lava_kuudra" -> f.drops("lava", "Carmine Dye");
            case "lava_thermal_runaway" -> f.worstDry("lava");

            case "fin_shark_week" -> f.kills("shark");
            case "fin_big_fish" -> f.drops("shark", "Megalodon");
            case "fin_tooth_fairy" -> f.drops("shark", "Great White Shark Tooth");
            case "fin_off_the_hook" -> Math.max(0, f.kills("shark") - f.drops("shark"));

            case "wisp_sorrowful" -> f.drops("ghost", "Sorrow");
            case "wisp_plasma" -> f.drops("ghost", "Plasma");
            case "wisp_spirit_level" -> f.drops("ghost");

            // Everything else is an event: it happened (award) or it did not.
            default -> d.flags.containsKey(s.id()) ? 1 : 0;
        };
    }

    /** Diana's eight drops, as the stats name them. */
    static final String[] DIANA_DROPS = {"Brain Food", "Chimera I", "Fateful Stinger", "Manti-core", "Crown of Greed",
            "Shimmering Wool", "Minos Relic", "Mythological Dye"};

    /** The grade a value reaches: how many of the stamp's numbers it has met. */
    public static int gradeFor(Stamp s, long value) {
        int g = 0;
        for (int i = 1; i <= s.grades(); i++) if (value >= s.need(i)) g = i;
        return g;
    }

    // ------------------------------------------------------------------ earning

    /**
     * Reads every stamp again and earns what the numbers now reach. {@code pre}: backdating from stats that existed
     * before the Ledger - stamped with no date, and not announced.
     *
     * @return the stamps earned or raised a grade just now, for the pop-up
     */
    public static List<Stamp> refresh(Facts f, LedgerData d, long now, boolean pre) {
        List<Stamp> fresh = new ArrayList<>();
        for (Stamp s : STAMPS) {
            if (s.chapter() == Chapter.SERAPH) continue;
            long v = value(s, f, d);
            int g = gradeFor(s, v);
            LedgerData.Entry e = d.stamps.get(s.id());
            if (g <= (e == null ? 0 : e.grade)) continue;
            if (e == null) {
                e = new LedgerData.Entry();
                d.stamps.put(s.id(), e);
            }
            e.grade = g;
            e.at = pre ? 0 : now;
            Long kill = d.flags.get(s.id());
            e.value = kill != null ? kill : v;
            d.seen.remove(s.id());
            if (!pre) fresh.add(s);
        }
        return fresh;
    }

    /** An event stamp happened: kept for good (the first time's {@code kill} is the one remembered). */
    public static void award(LedgerData d, String id, long kill) {
        d.flags.putIfAbsent(id, kill);
    }

    /** One real run of any animation, a win or not: the House's cutscene count and its bust run. */
    public static void run(LedgerData d, boolean win) {
        d.cutscenes++;
        if (win) {
            d.bustRun = 0;
        } else {
            d.bustRun++;
            d.worstBustRun = Math.max(d.worstBustRun, d.bustRun);
        }
    }

    /**
     * A rare drop: the luck stamps look at it against the last ones, then it joins them.
     *
     * @param kill that source's kill count with this kill (0 for a source with no kills)
     */
    public static void drop(LedgerData d, String source, String title, long kill, long now, ZoneId zone) {
        // The computer's clock went back (a new time zone, a fixed clock, a dead BIOS battery): nothing that needs
        // the time is trusted until it is past the last drop again - no hot streak against "future" drops, no 3 AM.
        boolean clockOk = d.recent.isEmpty() || now >= d.recent.get(d.recent.size() - 1).at;
        if (clockOk) timed(d, kill, now, zone);
        Stamp jackpot = byId("house_jackpot_777");
        if (kill > 0 && kill == (jackpot == null ? 777 : jackpot.number(777))) award(d, "house_jackpot_777", kill);
        if ("scatha".equals(source)) {
            if (d.firstPetKill <= 0) d.firstPetKill = kill;
            Stamp b2b = byId("scatha_back_to_back");
            long within = b2b == null ? 50 : b2b.number(50);
            for (LedgerData.Drop prev : d.recent) {
                if ("scatha".equals(prev.source) && prev.kill > 0 && kill > prev.kill && kill - prev.kill <= within) {
                    award(d, "scatha_back_to_back", kill);
                    break;
                }
            }
        }
        LedgerData.Drop dr = new LedgerData.Drop();
        dr.source = source;
        dr.title = title;
        dr.kill = kill;
        dr.at = now;
        d.recent.add(dr);
        while (d.recent.size() > RECENT) d.recent.remove(0);
    }

    /** The awards that read the clock: the hot streak and the night shift. */
    private static void timed(LedgerData d, long kill, long now, ZoneId zone) {
        for (LedgerData.Drop prev : d.recent) {
            if (now - prev.at <= HOT_STREAK_MS && now >= prev.at) {
                award(d, "house_hot_streak", kill);
                break;
            }
        }
        int hour = Instant.ofEpochMilli(now).atZone(zone).getHour();
        if (hour >= 3 && hour < 5) award(d, "house_night_shift", kill);
    }

    /** A Vanguard corpse's worth, once every price is in: In the Black and Negative Equity. */
    public static void corpseWorth(LedgerData d, long net, long items, long corpse) {
        Stamp black = byId("vg_in_the_black");
        if (net >= (black == null ? 10_000_000L : black.number(10_000_000L))) award(d, "vg_in_the_black", corpse);
        if (net < 0) award(d, "vg_negative_equity", corpse);
        d.coins += Math.max(0, items);
    }
}
