package com.butcher.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.butcher.ButcherClient;
import net.fabricmc.loader.api.FabricLoader;

import com.butcher.ledger.Facts;
import com.butcher.ledger.Ledger;
import com.butcher.ledger.LedgerData;
import com.butcher.ledger.Stamp;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What the player has actually seen, kept between sessions.
 *
 * <h2>Why bother</h2>
 *
 * A Scatha pet is roughly a one-in-two-thousand drop. Over a session that is
 * indistinguishable from never, and the only thing that makes a grind of that shape
 * bearable is being able to see it accumulating. A dry streak you can read is a
 * different experience from one you can only feel.
 *
 * <h2>Counted here, not derived</h2>
 *
 * Every number is incremented at the moment the thing happens, rather than worked out
 * later from the drops. That matters for the dry streak in particular: it has to survive
 * a relaunch, and there is nothing to recompute it from once the session is gone.
 *
 * Saved to {@code config/butcher/stats.json} and written through on every change.
 * The writes are tiny, they only happen a couple of times per kill, and losing the file
 * to a crash would be far more annoying than the cost of saving it.
 */
public final class ButcherStats {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("butcher").resolve("stats.json");

    /** Kills the detector accepted, whatever they dropped. */
    public int kills;
    /** Cutscenes actually played, which excludes any the player interrupted. */
    public int cutscenes;

    public int petsRare;
    public int petsEpic;
    public int petsLegendary;
    public int gemstones;
    public int dwarvenOs;

    /** Runs that ended with the Scatha ducking the last swing. */
    public int misses;
    /** Every cut the cleaver has landed, across every run. */
    public long chops;

    /** Kills since the last pet, and the worst that has ever got. */
    public int dryStreak;
    public int worstDryStreak;

    // ---------------------------------------------------------------- Vanguard
    // Counted separately from the Scatha's numbers all the way down. They are different
    // grinds with different odds - a pet is one in two thousand, a Vanguard legendary is
    // about one corpse in fifteen - and averaging them together would make both
    // meaningless.

    /** Vanguard corpses whose loot message was read. */
    public int corpses;
    /** Every roll across every corpse - 5 to 8 per corpse. */
    public long corpseRolls;
    /** Drops in each tier, across every corpse. */
    public int corpseCommons;
    public int corpseUncommons;
    public int corpseLegendaries;
    /** Frostbitten Dyes. Counted apart because it is the one number worth bragging about. */
    public int corpseSpecials;
    /**
     * How many of each drop, by name - what lets the Locket's and the Dye's endings say
     * "your 3rd". A map rather than a field per item, so an item Hypixel adds later is
     * counted without a change here. Null in a stats file written before this existed.
     */
    public java.util.Map<String, Integer> corpseItems = new java.util.HashMap<>();
    /** Corpses since the last legendary, and the worst that has ever got. */
    public int corpseDryStreak;
    public int worstCorpseDryStreak;

    // ---------------------------------------------------------------- Diana
    // Her own grind, counted apart again: kills of the four creatures that play the cutscene, and
    // her drops by name - which also lets an ending say "your 3rd".

    /** Kills that played the Chopping Block, by creature. Null in a file written before this existed. */
    public java.util.Map<String, Integer> dianaKills = new java.util.HashMap<>();
    /** Diana drops, by name. */
    public java.util.Map<String, Integer> dianaDrops = new java.util.HashMap<>();
    /** Kills since the last drop, and the worst that has ever got. */
    public int dianaDryStreak;
    public int worstDianaDryStreak;

    /** One Chopping Block played for real: a kill of {@code creature}, and {@code drop} if something dropped. */
    public void onDiana(String creature, String drop) {
        if (refused("diana", drop)) return;
        if (dianaKills == null) dianaKills = new java.util.HashMap<>();
        if (dianaDrops == null) dianaDrops = new java.util.HashMap<>();
        if (creature != null) dianaKills.merge(creature, 1, Integer::sum);
        if (drop != null) {
            dianaDrops.merge(drop, 1, Integer::sum);
            worstDianaDryStreak = Math.max(worstDianaDryStreak, dianaDryStreak);
            dianaDryStreak = 0;
        } else {
            dianaDryStreak++;
            worstDianaDryStreak = Math.max(worstDianaDryStreak, dianaDryStreak);
        }
        LedgerData l = ledger();
        Ledger.run(l, drop != null);
        long at = System.currentTimeMillis();
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        Ledger.kill(l, "diana", at, zone);
        if (creature != null) Ledger.kill(l, "diana:" + creature, at, zone);
        if (drop != null) {
            long now = at;
            long kills = 0;
            for (int n : dianaKills.values()) kills += n;
            Ledger.drop(l, "diana", drop, kills, now, java.time.ZoneId.systemDefault());
            long shared = dianaShareAt.getAsLong();
            if (shared > 0 && now - shared <= SHARE_MS) Ledger.award(l, "diana_shared_custody", kills);
            // Nobody Expects It: a drop within the day's first N Inquisitors (the user: 5, and it resets every day).
            Stamp early = Ledger.byId("diana_nobody_expects");
            long inquisitors = Ledger.killsToday(l, "diana:INQUISITOR", now, zone);
            if ("INQUISITOR".equals(creature) && inquisitors > 0 && inquisitors <= (early == null ? 5 : early.number(5))) {
                Ledger.award(l, "diana_nobody_expects", dianaKills.getOrDefault("INQUISITOR", 0));
            }
        }
        stampCheck();
        save();
    }

    public int dianaKillCount(String creature) {
        return dianaKills == null ? 0 : dianaKills.getOrDefault(creature, 0);
    }

    /** How many of this drop the player has had, the current one included. */
    public int dianaDropCount(String drop) {
        return dianaDrops == null ? 0 : dianaDrops.getOrDefault(drop, 0);
    }

    /** Wipe only Diana's numbers. */
    public void resetDiana() {
        dianaKills = new java.util.HashMap<>();
        dianaDrops = new java.util.HashMap<>();
        dianaDryStreak = 0;
        worstDianaDryStreak = 0;
        save();
    }

    // ---------------------------------------------------------------- the later sources
    // Hellion ("blaze"), the Seraph ("seraph"), Eruption ("lava") and Fin ("shark"): each counts its real runs,
    // its drops by name and its dry streak, so every reveal can say "your Nth" (the review, A8).

    public static final class Source {
        public int kills;
        public java.util.Map<String, Integer> drops = new java.util.HashMap<>();
        public int dry, worstDry;
    }

    public java.util.Map<String, Source> sources = new java.util.HashMap<>();

    public Source source(String key) {
        if (sources == null) sources = new java.util.HashMap<>();
        Source s = sources.computeIfAbsent(key, k -> new Source());
        if (s.drops == null) s.drops = new java.util.HashMap<>();
        return s;
    }

    /** One real run of {@code key}: {@code drop} the win's name, or null for a bust. */
    public void onSource(String key, String drop) {
        if (refused(key, drop)) return;
        Source s = source(key);
        s.kills++;
        if (drop != null) {
            s.drops.merge(drop, 1, Integer::sum);
            s.worstDry = Math.max(s.worstDry, s.dry);
            s.dry = 0;
        } else {
            s.dry++;
            s.worstDry = Math.max(s.worstDry, s.dry);
        }
        LedgerData l = ledger();
        Ledger.run(l, drop != null);
        Ledger.kill(l, key, System.currentTimeMillis(), java.time.ZoneId.systemDefault());
        if (drop != null) Ledger.drop(l, key, drop, s.kills, System.currentTimeMillis(), java.time.ZoneId.systemDefault());
        stampCheck();
        save();
    }

    /** A drop from a source with no kill to count (the Ghosts): only the drop is tallied. */
    public void onSourceDrop(String key, String drop) {
        if (refused(key, drop)) return;
        source(key).drops.merge(drop, 1, Integer::sum);
        LedgerData l = ledger();
        Ledger.run(l, true);
        Ledger.drop(l, key, drop, 0, System.currentTimeMillis(), java.time.ZoneId.systemDefault());
        stampCheck();
        save();
    }

    public int sourceDropCount(String key, String drop) {
        return source(key).drops.getOrDefault(drop, 0);
    }

    public void resetSource(String key) {
        if (sources != null) sources.remove(key);
        save();
    }

    // ---------------------------------------------------------------- the Ledger

    /** The Ledger's own data: the stamps, and the few numbers only it keeps. Survives every stats reset. */
    public LedgerData ledger;

    /** Who hears about a stamp earned or raised a grade (the pop-up). Called on the thread that counted it. */
    public static volatile java.util.function.Consumer<Stamp> stamped;

    /** When the last Diana loot share line came (DianaWatcher), for "Shared Custody"; -1 for never. */
    public static volatile java.util.function.LongSupplier dianaShareAt = () -> -1;
    /** A Diana win this soon after a loot share line is a loot-shared one. */
    private static final long SHARE_MS = 10_000;

    /** The Ledger, read in from the stats the first time: everything they already prove is stamped, undated. */
    public LedgerData ledger() {
        if (ledger == null) ledger = new LedgerData();
        ledger.fix();
        if (!ledger.backfilled) {
            ledger.backfilled = true;
            long runs = cutscenes + corpses;
            if (dianaKills != null) for (int n : dianaKills.values()) runs += n;
            if (sources != null) {
                for (java.util.Map.Entry<String, Source> e : sources.entrySet()) {
                    runs += "ghost".equals(e.getKey()) ? total(e.getValue().drops) : e.getValue().kills;
                }
            }
            ledger.cutscenes = Math.max(ledger.cutscenes, runs);
            // Only an upper bound - the first pet came on this kill or before - so it can earn Beginner's Luck only
            // when it is already within reach.
            if (pets() > 0 && ledger.firstPetKill <= 0) ledger.firstPetKill = kills;
            Ledger.refresh(facts(), ledger, System.currentTimeMillis(), true);
            save();
        }
        return ledger;
    }

    private static long total(java.util.Map<String, Integer> m) {
        long n = 0;
        if (m != null) for (int v : m.values()) n += v;
        return n;
    }

    /** What the stats say, for the Ledger's rules. */
    public Facts facts() {
        Facts f = new Facts();
        f.scathaKills = kills;
        f.petsRare = petsRare;
        f.petsEpic = petsEpic;
        f.petsLegendary = petsLegendary;
        f.scathaWorstDry = Math.max(worstDryStreak, dryStreak);
        f.scathaMisses = misses;
        f.dwarvenOs = dwarvenOs;
        f.corpseRolls = corpseRolls;
        if (corpseItems != null) f.corpseItems.putAll(corpseItems);
        if (dianaKills != null) f.dianaKillsBy.putAll(dianaKills);
        f.corpses = corpses;
        f.corpseLegendaries = corpseLegendaries;
        f.corpseDyes = corpseSpecials;
        f.lockets = itemCount("Shattered Locket");
        f.corpseWorstDry = Math.max(worstCorpseDryStreak, corpseDryStreak);
        if (dianaKills != null) for (int n : dianaKills.values()) f.dianaKills += n;
        if (dianaDrops != null) f.dianaDrops.putAll(dianaDrops);
        if (sources != null) {
            for (java.util.Map.Entry<String, Source> e : sources.entrySet()) {
                Source s = e.getValue();
                f.kills.put(e.getKey(), (long) s.kills);
                f.worstDry.put(e.getKey(), (long) Math.max(s.worstDry, s.dry));
                if (s.drops != null) f.drops.put(e.getKey(), new java.util.HashMap<>(s.drops));
            }
        }
        return f;
    }

    // ---------------------------------------------------------------- the fail-safes (2026-10-16)

    /** The same win from the same source this soon after the last is one line seen twice, not two drops. */
    static final long DUPLICATE_MS = 2_000;
    /** More counted events than this in one second is a detection bug, not luck. */
    static final int FLOOD = 20;
    private static final java.util.ArrayDeque<Long> counted = new java.util.ArrayDeque<>();
    private static String lastWin;
    private static long lastWinAt = -1;

    /**
     * Whether this run must not be counted: a duplicate of the win just counted, or one of a flood. Either is logged
     * and dropped, so a detection bug can never fill the stats and the Ledger with drops nobody got.
     */
    private static boolean refused(String source, String drop) {
        long now = System.currentTimeMillis();
        if (drop != null && !"vanguard".equals(source)) {
            String key = source + "|" + drop;
            if (key.equals(lastWin) && now - lastWinAt >= 0 && now - lastWinAt < DUPLICATE_MS) {
                note("§eSafety: " + drop + " (" + source + ") counted twice within " + DUPLICATE_MS / 1000
                        + " s - the second is not counted");
                return true;
            }
            lastWin = key;
            lastWinAt = now;
        }
        while (!counted.isEmpty() && now - counted.peekFirst() > 1_000) counted.pollFirst();
        if (counted.size() >= FLOOD) {
            note("§cSafety: more than " + FLOOD + " kills or drops in a second - not counted (a detection bug?)");
            return true;
        }
        counted.addLast(now);
        return false;
    }

    private static void note(String line) {
        try {
            ButcherClient.decisions.note(line);
        } catch (Throwable ignored) {
            // Offline (LedgerTest): there is no client.
        }
    }

    /**
     * /butcher ledger recheck: the Ledger against the stats again. Whatever they prove and the book lacks is stamped,
     * undated and without a pop-up, as the first backfill was. Never takes anything away.
     *
     * @return how many stamps or grades were added
     */
    public int recheck() {
        LedgerData l = ledger();
        int before = Ledger.earnedGrades(l);
        Ledger.refresh(facts(), l, System.currentTimeMillis(), true);
        int added = Ledger.earnedGrades(l) - before;
        if (added > 0) save();
        return added;
    }

    // ---------------------------------------------------------------- /butcher test ledger (developers only)
    // These change the real Ledger in stats.json: undo with "take" or "wipe" (the daily backups are there too).

    /** Puts {@code s} in the book at grade {@code g} (clamped to its grades), dated now, as if just earned. */
    public void testGive(Stamp s, int g) {
        LedgerData l = ledger();
        LedgerData.Entry e = l.stamps.computeIfAbsent(s.id(), k -> new LedgerData.Entry());
        e.grade = Math.max(1, Math.min(g, s.grades()));
        e.at = System.currentTimeMillis();
        e.value = s.tiered() ? s.need(e.grade) : 0;
        l.seen.remove(s.id());
        save();
    }

    /** Takes a stamp out of the book, its event flag too. The next check puts it back if the stats prove it. */
    public boolean testTake(String id) {
        LedgerData l = ledger();
        boolean had = l.stamps.remove(id) != null;
        l.flags.remove(id);
        l.seen.remove(id);
        save();
        return had;
    }

    /** Every stamp at its top grade (the Seraph's chapter, under maintenance, left out). */
    public int testFill() {
        int n = 0;
        for (Stamp s : Ledger.STAMPS) {
            if (s.chapter() == com.butcher.ledger.Chapter.SERAPH) continue;
            testGive(s, s.grades());
            n++;
        }
        return n;
    }

    /** An empty Ledger, then the backfill from the stats again - as on the first launch with it. */
    public void testWipe() {
        ledger = null;
        ledger();
        save();
    }

    /** Every stamp shows fresh ink again. */
    public void testUnseen() {
        ledger().seen.clear();
        save();
    }

    /** Earns whatever the numbers now reach and tells the pop-up. The caller saves. */
    private void stampCheck() {
        java.util.List<Stamp> fresh = Ledger.refresh(facts(), ledger(), System.currentTimeMillis(), false);
        java.util.function.Consumer<Stamp> out = stamped;
        if (out != null) for (Stamp s : fresh) out.accept(s);
    }

    /** A real cutscene skipped (the skip key). */
    public void onSkip() {
        ledger().skips++;
        stampCheck();
        save();
    }

    /** One drop worth this much earns the House's Big Ticket. */
    static final long BIG_TICKET = 10_000_000;

    /** A real drop revealed with its price: the House's Tycoon. */
    public void onCoins(long coins) {
        if (coins <= 0) return;
        ledger().coins += coins;
        if (coins >= BIG_TICKET) Ledger.award(ledger(), "house_big_ticket", 0);
        Stamp change = Ledger.byId("house_loose_change");
        if (coins < (change == null ? 500_000 : change.number(500_000))) Ledger.award(ledger(), "house_loose_change", 0);
        stampCheck();
        save();
    }

    /** A real Vanguard corpse's worth, once every price is in. */
    public void onCorpseWorth(long net, long items) {
        Ledger.corpseWorth(ledger(), net, items, corpses);
        stampCheck();
        save();
    }

    /** An event stamp seen happening somewhere only the caller knows about (a Jawbus win, a legendary Megalodon). */
    public void award(String id, long kill) {
        Ledger.award(ledger(), id, kill);
        stampCheck();
        save();
    }

    /** The book showed these stamps: their fresh ink dries. */
    public void seen(java.util.Collection<String> ids) {
        if (ledger().seen.addAll(ids)) save();
    }

    private static ButcherStats instance;

    public static ButcherStats get() {
        if (instance == null) instance = load();
        return instance;
    }

    public int pets() {
        return petsRare + petsEpic + petsLegendary;
    }

    /** Every drop ever pulled out of a corpse, whatever it was. */
    public int corpseDrops() {
        return corpseCommons + corpseUncommons + corpseLegendaries + corpseSpecials;
    }

    // ------------------------------------------------------------------ counting

    public void onKill() {
        kills++;
        Ledger.scathaKill(ledger(), System.currentTimeMillis(), java.time.ZoneId.systemDefault());
        save();
    }

    public void onCutscene() {
        cutscenes++;
        save();
    }

    public void onChop() {
        chops++;
        // Not saved here: a chop happens five times a run and the number is cosmetic.
        // It rides along with the next real event instead.
    }

    public void onMiss() {
        misses++;
        save();
    }

    /**
     * A drop was revealed.
     *
     * @param rarity "rare"/"epic"/"legendary" for a pet, otherwise null
     * @param blocked true for Dwarven O's rather than gemstones
     */
    public void onDrop(String rarity, boolean blocked) {
        if (refused("scatha", rarity)) return;
        if (rarity != null) {
            switch (rarity) {
                case "legendary" -> petsLegendary++;
                case "epic" -> petsEpic++;
                default -> petsRare++;
            }
            // A pet ends the streak. Record how bad it got before resetting, otherwise
            // the worst streak would only ever be the one currently running.
            worstDryStreak = Math.max(worstDryStreak, dryStreak);
            dryStreak = 0;
        } else {
            if (blocked) dwarvenOs++; else gemstones++;
            dryStreak++;
            worstDryStreak = Math.max(worstDryStreak, dryStreak);
        }
        LedgerData l = ledger();
        Ledger.run(l, rarity != null);
        if (rarity != null) Ledger.drop(l, "scatha", rarity, kills, System.currentTimeMillis(), java.time.ZoneId.systemDefault());
        Ledger.scathaResult(l, rarity, blocked, kills);
        stampCheck();
        save();
    }

    /**
     * One Vanguard corpse was opened and read.
     *
     * Takes the tier counts rather than the haul so that {@code config} does not have to
     * know what a {@code CorpseHaul} is - the stats file outlives any one feature's types.
     */
    public void onCorpse(int commons, int uncommons, int legendaries, int specials) {
        // Never de-duplicated: corpses are read one loot message each, and two can be opened close together.
        if (refused("vanguard", null)) return;
        corpses++;
        corpseRolls += commons + uncommons + legendaries + specials;
        corpseCommons += commons;
        corpseUncommons += uncommons;
        corpseLegendaries += legendaries;
        corpseSpecials += specials;
        // A Dye ends a dry streak too - it is better than a legendary, not other than one.
        if (legendaries + specials > 0) {
            // Record how bad it got before resetting, or the worst streak would only ever
            // be the one currently running.
            worstCorpseDryStreak = Math.max(worstCorpseDryStreak, corpseDryStreak);
            corpseDryStreak = 0;
        } else {
            corpseDryStreak++;
            worstCorpseDryStreak = Math.max(worstCorpseDryStreak, corpseDryStreak);
        }
        LedgerData l = ledger();
        boolean win = legendaries + specials > 0;
        Ledger.run(l, win);
        Ledger.kill(l, "vanguard", System.currentTimeMillis(), java.time.ZoneId.systemDefault());
        if (win) Ledger.drop(l, "vanguard", specials > 0 ? "Frostbitten Dye" : "legendary", corpses,
                System.currentTimeMillis(), java.time.ZoneId.systemDefault());
        if (legendaries + specials >= 2) Ledger.award(l, "vg_brain_freeze", corpses);
        stampCheck();
        save();
    }

    /** Wipe only the Scatha numbers, leaving the corpse ones alone. */
    public void resetScatha() {
        ButcherStats fresh = new ButcherStats();
        kills = fresh.kills;
        cutscenes = fresh.cutscenes;
        petsRare = fresh.petsRare;
        petsEpic = fresh.petsEpic;
        petsLegendary = fresh.petsLegendary;
        gemstones = fresh.gemstones;
        dwarvenOs = fresh.dwarvenOs;
        misses = fresh.misses;
        chops = fresh.chops;
        dryStreak = fresh.dryStreak;
        worstDryStreak = fresh.worstDryStreak;
        save();
    }

    /** Wipe only the Vanguard numbers. */
    public void resetVanguard() {
        ButcherStats fresh = new ButcherStats();
        corpses = fresh.corpses;
        corpseRolls = fresh.corpseRolls;
        corpseCommons = fresh.corpseCommons;
        corpseUncommons = fresh.corpseUncommons;
        corpseLegendaries = fresh.corpseLegendaries;
        corpseSpecials = fresh.corpseSpecials;
        corpseItems = fresh.corpseItems;
        corpseDryStreak = fresh.corpseDryStreak;
        worstCorpseDryStreak = fresh.worstCorpseDryStreak;
        save();
    }

    /** Adds one corpse's drops to the per-item counts. Saved by the {@link #onCorpse} that follows. */
    public void countItems(java.util.List<String> names) {
        if (corpseItems == null) corpseItems = new java.util.HashMap<>();
        for (String n : names) corpseItems.merge(n, 1, Integer::sum);
    }

    /** How many of this item the player has had from corpses, the current one included. */
    public int itemCount(String name) {
        return corpseItems == null ? 0 : corpseItems.getOrDefault(name, 0);
    }

    public void reset() {
        ButcherStats fresh = new ButcherStats();
        kills = fresh.kills;
        cutscenes = fresh.cutscenes;
        petsRare = fresh.petsRare;
        petsEpic = fresh.petsEpic;
        petsLegendary = fresh.petsLegendary;
        gemstones = fresh.gemstones;
        dwarvenOs = fresh.dwarvenOs;
        misses = fresh.misses;
        chops = fresh.chops;
        dryStreak = fresh.dryStreak;
        worstDryStreak = fresh.worstDryStreak;
        corpses = fresh.corpses;
        corpseRolls = fresh.corpseRolls;
        corpseCommons = fresh.corpseCommons;
        corpseUncommons = fresh.corpseUncommons;
        corpseLegendaries = fresh.corpseLegendaries;
        corpseSpecials = fresh.corpseSpecials;
        corpseItems = fresh.corpseItems;
        corpseDryStreak = fresh.corpseDryStreak;
        worstCorpseDryStreak = fresh.worstCorpseDryStreak;
        dianaKills = fresh.dianaKills;
        dianaDrops = fresh.dianaDrops;
        dianaDryStreak = fresh.dianaDryStreak;
        worstDianaDryStreak = fresh.worstDianaDryStreak;
        save();
    }

    // ------------------------------------------------------------------ storage

    private static ButcherStats load() {
        try {
            if (Files.exists(PATH)) {
                backUp();
                ButcherStats read = GSON.fromJson(Files.readString(PATH), ButcherStats.class);
                if (read != null) {
                    // Before the Ledger first touches the file, a copy of it as it was - kept for good.
                    if (read.ledger == null) copyOnce(PATH.resolveSibling("stats.json.pre-ledger"));
                    return read;
                }
            }
        } catch (Exception e) {
            // Moved aside, never overwritten: the first save would otherwise wipe every streak for good.
            ButcherClient.LOGGER.warn("[Butcher] could not read stats.json; starting fresh and keeping it as stats.json.broken", e);
            try {
                Files.move(PATH, PATH.resolveSibling("stats.json.broken"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
            }
        }
        return new ButcherStats();
    }

    /** Daily backups kept beside stats.json: one per day the game is started, the newest few. */
    static final int BACKUPS = 3;

    /** Copies stats.json to stats.json.YYYY-MM-DD.bak once a day, and keeps only the newest {@link #BACKUPS}. */
    private static void backUp() {
        try {
            copyOnce(PATH.resolveSibling("stats.json." + java.time.LocalDate.now() + ".bak"));
            java.util.List<Path> old = new java.util.ArrayList<>();
            try (java.util.stream.Stream<Path> files = Files.list(PATH.getParent())) {
                files.filter(f -> f.getFileName().toString().matches("stats\\.json\\.\\d{4}-\\d{2}-\\d{2}\\.bak"))
                        .forEach(old::add);
            }
            old.sort(java.util.Comparator.comparing(f -> f.getFileName().toString()));
            for (int i = 0; i < old.size() - BACKUPS; i++) Files.deleteIfExists(old.get(i));
        } catch (Exception e) {
            ButcherClient.LOGGER.warn("[Butcher] could not back up stats.json", e);
        }
    }

    private static void copyOnce(Path to) {
        try {
            if (!Files.exists(to)) Files.copy(PATH, to);
        } catch (Exception e) {
            ButcherClient.LOGGER.warn("[Butcher] could not copy stats.json to " + to.getFileName(), e);
        }
    }

    public void save() {
        try {
            PatternConfig.writeAtomically(PATH, GSON.toJson(this));
        } catch (Exception e) {
            // Never worth interrupting a cutscene over. The numbers are a nicety.
            ButcherClient.LOGGER.warn("[Butcher] could not write stats.json", e);
        }
    }
}
