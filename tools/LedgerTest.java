import com.butcher.ledger.Chapter;
import com.butcher.ledger.Facts;
import com.butcher.ledger.Ledger;
import com.butcher.ledger.LedgerData;
import com.butcher.ledger.Stamp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Offline checks for the Ledger's rules (com.butcher.ledger is pure Java).
 *
 *   javac -d out/ledger src/main/java/com/butcher/ledger/*.java tools/LedgerTest.java
 *   java -cp out/ledger LedgerTest
 *
 * Run from the repository root: it reads the game's drop enums as source text, to check every name a stamp looks for
 * is spelled the way the stats are written.
 */
public class LedgerTest {
    static int pass = 0, fail = 0;

    static void check(String what, boolean ok, String detail) {
        if (ok) pass++;
        else {
            fail++;
            System.out.println("FAIL " + what + (detail.isEmpty() ? "" : " - " + detail));
        }
    }

    static void check(String what, boolean ok) {
        check(what, ok, "");
    }

    /** The stamps whose value is an event, kept by award() - the rest are counted from the stats. */
    static final Set<String> EVENTS = Set.of("house_hot_streak", "house_night_shift", "house_jackpot_777",
            "scatha_back_to_back", "vg_negative_equity", "vg_in_the_black", "diana_shared_custody", "lava_jaw_dropping",
            "fin_bigger_fish", "house_big_ticket", "fin_feeding_frenzy", "wisp_double_haunting", "wisp_poltergeist",
            "wisp_matched_pair", "wisp_witching_hour");

    static final ZoneId UTC = ZoneOffset.UTC;

    static long at(int hour, int minute) {
        return at(16, hour, minute);
    }

    static long at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 10, day, hour, minute).atZone(UTC).toInstant().toEpochMilli();
    }

    public static void main(String[] a) throws Exception {
        // ---- the table
        check("59 stamps or more", Ledger.STAMPS.size() >= 59, "" + Ledger.STAMPS.size());
        Set<String> ids = new java.util.HashSet<>();
        for (Stamp s : Ledger.STAMPS) {
            check("unique id " + s.id(), ids.add(s.id()));
            if (s.tiered()) {
                check(s.id() + " tiered has tiers", s.tiers().length >= 2);
                for (int i = 1; i < s.tiers().length; i++) check(s.id() + " tiers rise", s.tiers()[i] > s.tiers()[i - 1]);
            }
        }
        for (Chapter c : Chapter.values()) {
            if (c != Chapter.SERAPH) check("chapter " + c + " has stamps", !Ledger.chapter(c).isEmpty());
        }

        // ---- every counted stamp is wired to a number: with everything at plenty, it reads more than 0
        Facts rich = rich();
        LedgerData d0 = new LedgerData();
        d0.cutscenes = 99_999;
        d0.worstBustRun = 999;
        d0.skips = 999;
        d0.coins = 99_000_000_000L;
        d0.firstPetKill = 1;
        for (Stamp s : Ledger.STAMPS) {
            if (EVENTS.contains(s.id())) {
                check(s.id() + " is 0 until it happens", Ledger.value(s, rich, d0) == 0);
                Ledger.award(d0, s.id(), 7);
                check(s.id() + " counts once awarded", Ledger.value(s, rich, d0) == 1);
            } else {
                check(s.id() + " reads the stats", Ledger.value(s, rich, d0) > 0, "value 0 with every number high");
            }
        }

        // ---- the names stamps look for are the game's own
        String hellion = Files.readString(Path.of("src/main/java/com/butcher/anim/HellionTimeline.java"));
        String eruption = Files.readString(Path.of("src/main/java/com/butcher/anim/EruptionTimeline.java"));
        String shark = Files.readString(Path.of("src/main/java/com/butcher/detect/SharkDrops.java"));
        String ghost = Files.readString(Path.of("src/main/java/com/butcher/detect/GhostDrops.java"));
        String diana = Files.readString(Path.of("src/main/java/com/butcher/render/DianaItems.java"));
        for (String n : HELLION) check("Hellion names " + n, hellion.contains("\"" + n + "\""));
        for (String n : ERUPTION) check("Eruption names " + n, eruption.contains("\"" + n + "\""));
        String vanguard = Files.readString(Path.of("src/main/java/com/butcher/detect/vanguard/VanguardLoot.java"));
        for (String n : List.of("Caged Wisp", "Frozen Scute", "Skeleton Key")) {
            check("Vanguard names " + n, vanguard.contains("\"" + n + "\""));
        }
        String chop = Files.readString(Path.of("src/main/java/com/butcher/anim/ChopTimeline.java"));
        check("Diana creature INQUISITOR", chop.contains("INQUISITOR"));
        for (com.butcher.ledger.Chapter c : com.butcher.ledger.Chapter.values()) {
            if (c == com.butcher.ledger.Chapter.SERAPH) continue;
            check("10 or more stamps in " + c, Ledger.chapter(c).size() >= 10, Ledger.chapter(c).size() + "");
        }
        for (String n : List.of("Megalodon", "Great White Shark Tooth")) check("Fin names " + n, shark.contains("\"" + n + "\""));
        for (String n : List.of("Sorrow", "Plasma")) check("Wisp names " + n, ghost.contains("\"" + n + "\""));
        for (String n : List.of("Brain Food", "Chimera I", "Fateful Stinger", "Manti-core", "Crown of Greed",
                "Shimmering Wool", "Minos Relic", "Mythological Dye")) {
            check("Diana names " + n, diana.contains("\"" + n + "\""));
        }

        // ---- backfill: the stats already on disk earn their stamps, undated and unannounced
        Stamp worm = Ledger.byId("scatha_worm_food");
        Stamp dry = Ledger.byId("scatha_dry_aged");
        Facts f = new Facts();
        f.scathaKills = worm.need(2) + 1;
        f.scathaWorstDry = dry.need(1);
        f.petsEpic = 1;
        LedgerData d = new LedgerData();
        List<Stamp> fresh = Ledger.refresh(f, d, 123, true);
        check("backfill announces nothing", fresh.isEmpty());
        check("backfill grade from kills", Ledger.grade(worm, d) == 2, "" + Ledger.grade(worm, d));
        check("backfill is undated", d.stamps.get(worm.id()).at == 0);
        check("backfill dry aged", Ledger.grade(dry, d) == 1);
        check("backfill epic pet", Ledger.grade(Ledger.byId("scatha_choice_cut"), d) == 1);
        check("no legendary pet", Ledger.grade(Ledger.byId("scatha_prime_cut"), d) == 0);

        // ---- live: a grade is raised, dated and announced once
        f.scathaKills = worm.need(3);
        fresh = Ledger.refresh(f, d, 5_000, false);
        check("raised a grade", fresh.contains(worm) && Ledger.grade(worm, d) == 3);
        check("dated", d.stamps.get(worm.id()).at == 5_000);
        check("fresh ink", !d.seen.contains(worm.id()));
        check("announced once", Ledger.refresh(f, d, 6_000, false).isEmpty());
        f.scathaKills = 0;
        Ledger.refresh(f, d, 7_000, false);
        check("never taken away", Ledger.grade(worm, d) == 3);
        check("a grade past the last tier is the last", Ledger.grade(worm, d) <= worm.grades());

        // ---- luck: hot streak, night shift, jackpot, back to back, beginner's luck
        LedgerData luck = new LedgerData();
        Ledger.drop(luck, "blaze", "Archfiend Dice", 10, at(14, 0), UTC);
        check("one drop is no streak", !luck.flags.containsKey("house_hot_streak"));
        Ledger.drop(luck, "lava", "Flash I", 5, at(14, 9), UTC);
        check("two within 10 minutes", luck.flags.containsKey("house_hot_streak"));
        LedgerData luck2 = new LedgerData();
        Ledger.drop(luck2, "blaze", "Archfiend Dice", 10, at(14, 0), UTC);
        Ledger.drop(luck2, "blaze", "Archfiend Dice", 11, at(14, 11), UTC);
        check("eleven minutes apart is no streak", !luck2.flags.containsKey("house_hot_streak"));
        check("2 PM is not the night shift", !luck2.flags.containsKey("house_night_shift"));
        Ledger.drop(luck2, "diana", "Brain Food", 3, at(17, 3, 30), UTC);
        check("3:30 AM is the night shift", luck2.flags.containsKey("house_night_shift"));
        Ledger.drop(luck2, "diana", "Brain Food", 4, at(17, 5, 0), UTC);
        Ledger.drop(luck2, "lava", "Flash I", 777, at(17, 12, 0), UTC);
        check("777th kill", luck2.flags.get("house_jackpot_777") == 777L);
        Ledger.drop(luck2, "ghost", "Sorrow", 0, at(17, 13, 0), UTC);
        check("a ghost drop has no kill to be 777th", luck2.flags.get("house_jackpot_777") == 777L);

        long within = Ledger.byId("scatha_back_to_back").number(50);
        LedgerData b2b = new LedgerData();
        Ledger.drop(b2b, "scatha", "epic", 100, at(10, 0), UTC);
        Ledger.drop(b2b, "scatha", "rare", 100 + within + 1, at(18, 0), UTC);
        check("pets too far apart", !b2b.flags.containsKey("scatha_back_to_back"));
        Ledger.drop(b2b, "scatha", "rare", 100 + within + 1 + within, at(20, 0), UTC);
        check("pets within reach", b2b.flags.containsKey("scatha_back_to_back"));
        check("first pet kill kept", b2b.firstPetKill == 100);

        Stamp beginner = Ledger.byId("scatha_beginners_luck");
        LedgerData lucky = new LedgerData();
        Ledger.drop(lucky, "scatha", "rare", beginner.number(20), at(9, 0), UTC);
        Ledger.refresh(new Facts(), lucky, 1, false);
        check("pet within the first kills", Ledger.grade(beginner, lucky) == 1);
        LedgerData late = new LedgerData();
        Ledger.drop(late, "scatha", "rare", beginner.number(20) + 1, at(9, 0), UTC);
        Ledger.refresh(new Facts(), late, 1, false);
        check("pet one kill too late", Ledger.grade(beginner, late) == 0);

        // ---- the House's runs, corpses' worth
        LedgerData runs = new LedgerData();
        for (int i = 0; i < 5; i++) Ledger.run(runs, false);
        Ledger.run(runs, true);
        Ledger.run(runs, false);
        check("bust run", runs.bustRun == 1 && runs.worstBustRun == 5 && runs.cutscenes == 7);
        Stamp black = Ledger.byId("vg_in_the_black");
        LedgerData worth = new LedgerData();
        Ledger.corpseWorth(worth, -12_000, 40_000, 3);
        check("negative equity", worth.flags.containsKey("vg_negative_equity") && !worth.flags.containsKey("vg_in_the_black"));
        Ledger.corpseWorth(worth, black.number(0), black.number(0) + 50_000, 4);
        check("in the black", worth.flags.get("vg_in_the_black") == 4L);
        check("coins counted", worth.coins == 40_000 + black.number(0) + 50_000);
        check("recent is capped", capped());

        // ---- the Fin and Wisp runs of luck
        LedgerData runs2 = new LedgerData();
        Ledger.drop(runs2, "shark", "Megalodon", 10, at(14, 0), UTC);
        check("one shark drop is no frenzy", !runs2.flags.containsKey("fin_feeding_frenzy"));
        Ledger.drop(runs2, "lava", "Flash I", 5, at(14, 2), UTC);
        check("another source's drop is no frenzy", !runs2.flags.containsKey("fin_feeding_frenzy"));
        Ledger.drop(runs2, "shark", "Great White Shark Tooth", 12, at(14, 8), UTC);
        check("two shark drops in 10 minutes", runs2.flags.containsKey("fin_feeding_frenzy"));
        LedgerData g = new LedgerData();
        Ledger.drop(g, "ghost", "Sorrow", 0, at(13, 0), UTC);
        Ledger.drop(g, "ghost", "Sorrow", 0, at(13, 20), UTC);
        check("20 minutes apart is no double haunting", !g.flags.containsKey("wisp_double_haunting"));
        check("two Sorrows are no matched pair", !g.flags.containsKey("wisp_matched_pair"));
        check("two in an hour is no poltergeist", !g.flags.containsKey("wisp_poltergeist"));
        Ledger.drop(g, "ghost", "Plasma", 0, at(13, 25), UTC);
        check("double haunting", g.flags.containsKey("wisp_double_haunting"));
        check("poltergeist", g.flags.containsKey("wisp_poltergeist"));
        check("matched pair", g.flags.containsKey("wisp_matched_pair"));
        check("1 PM is no witching hour", !g.flags.containsKey("wisp_witching_hour"));
        Ledger.drop(g, "ghost", "Sorrow", 0, at(17, 0, 30), UTC);
        check("witching hour", g.flags.containsKey("wisp_witching_hour"));

        // ---- fail-safes: a clock that went back, and a file that was edited by hand
        LedgerData clock = new LedgerData();
        Ledger.drop(clock, "blaze", "Archfiend Dice", 10, at(14, 0), UTC);
        Ledger.drop(clock, "diana", "Brain Food", 3, at(3, 30), UTC);     // earlier than the last drop
        check("clock back: no night shift", !clock.flags.containsKey("house_night_shift"));
        check("clock back: no hot streak", !clock.flags.containsKey("house_hot_streak"));
        check("clock back: the drop is still kept", clock.recent.size() == 2);
        Ledger.drop(clock, "lava", "Flash I", 777, at(3, 35), UTC);       // past the last drop again
        check("clock back: the count needs no clock", clock.flags.get("house_jackpot_777") == 777L);
        check("clock fine again: night shift", clock.flags.containsKey("house_night_shift"));
        check("clock fine again: hot streak", clock.flags.containsKey("house_hot_streak"));

        LedgerData bad = new LedgerData();
        LedgerData.Entry over = new LedgerData.Entry();
        over.grade = 99;
        over.at = -5;
        bad.stamps.put(worm.id(), over);
        LedgerData.Entry zero = new LedgerData.Entry();
        bad.stamps.put(Ledger.STAMPS.get(1).id(), zero);
        bad.stamps.put("no_such_stamp", new LedgerData.Entry());
        bad.stamps.get("no_such_stamp").grade = 2;
        bad.stamps.put("null_entry", null);
        bad.flags.put("house_night_shift", -3L);
        bad.recent.add(null);
        for (int i = 0; i < Ledger.RECENT + 9; i++) bad.recent.add(new LedgerData.Drop());
        bad.cutscenes = -4;
        bad.bustRun = 7;
        bad.worstBustRun = 2;
        bad.coins = -100;
        bad.fix();
        check("clean load: grade capped at the stamp's", bad.stamps.get(worm.id()).grade == worm.grades());
        check("clean load: no negative date", bad.stamps.get(worm.id()).at == 0);
        check("clean load: empty entries dropped", !bad.stamps.containsKey(Ledger.STAMPS.get(1).id())
                && !bad.stamps.containsKey("null_entry"));
        check("clean load: unknown ids kept", bad.stamps.containsKey("no_such_stamp"));
        check("clean load: no negative flag", bad.flags.get("house_night_shift") == 0L);
        check("clean load: recent capped, no nulls", bad.recent.size() == Ledger.RECENT && !bad.recent.contains(null));
        check("clean load: counters", bad.cutscenes == 0 && bad.coins == 0 && bad.worstBustRun == 7);
        check("clean load: the book still counts", Ledger.earnedGrades(bad) >= worm.grades());

        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    static boolean capped() {
        LedgerData d = new LedgerData();
        for (int i = 0; i < Ledger.RECENT + 25; i++) Ledger.drop(d, "lava", "Flash I", i + 1, at(12, 0) + i * 3_600_000L, UTC);
        return d.recent.size() == Ledger.RECENT && d.recent.get(d.recent.size() - 1).kill == Ledger.RECENT + 25;
    }

    /** Every number high and every drop had. */
    static final String[] HELLION = {"Duplex I", "Scorched Power Crystal", "Subzero Inverter", "Wilson's Engineering Plans",
            "Paragon Shard", "Archfiend Dice", "High Class Archfiend Dice", "Flame Dye"};
    static final String[] ERUPTION = {"Chain of the End Times", "Severed Pincer", "Flash I", "Brimstone Handle",
            "Radioactive Vial", "Scuttler Shell", "Burnt Texts", "Carmine Dye"};

    static Facts rich() {
        Facts f = new Facts();
        f.scathaKills = f.petsRare = f.petsEpic = f.petsLegendary = f.scathaWorstDry = f.scathaMisses = 99_999;
        f.corpses = f.corpseLegendaries = f.corpseDyes = f.lockets = f.corpseWorstDry = 99_999;
        f.dianaKills = 99_999;
        f.dwarvenOs = f.corpseRolls = 99_999;
        for (String n : List.of("Caged Wisp", "Frozen Scute", "Skeleton Key")) f.corpseItems.put(n, 3);
        f.dianaKillsBy.put("INQUISITOR", 99_999);
        for (String n : List.of("Brain Food", "Chimera I", "Fateful Stinger", "Manti-core", "Crown of Greed",
                "Shimmering Wool", "Minos Relic", "Mythological Dye")) f.dianaDrops.put(n, 5);
        put(f, "blaze", HELLION);
        put(f, "lava", ERUPTION);
        put(f, "shark", "Megalodon", "Great White Shark Tooth");
        put(f, "ghost", "Sorrow", "Plasma");
        f.drops.get("ghost").put("Sorrow", 99_999);
        return f;
    }

    static void put(Facts f, String source, String... titles) {
        Map<String, Integer> m = new HashMap<>();
        for (String t : titles) m.put(t, 5);
        f.drops.put(source, m);
        f.kills.put(source, 99_999L);
        f.worstDry.put(source, 99_999L);
    }
}
