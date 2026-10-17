package com.butcher.ledger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Ledger as it is saved (inside stats.json, under "ledger"): the stamps earned, the few numbers only the Ledger
 * keeps, and the last drops (what the luck stamps look back over). Public fields for Gson; no logic.
 *
 * Resetting stats leaves it alone: a stamp, once earned, stays.
 */
public final class LedgerData {

    /** One earned stamp. */
    public static final class Entry {
        /** 1-based grade reached (a single stamp: 1). */
        public int grade;
        /** When the current grade was reached; 0 for a stamp backdated from the stats ("pre-ledger"). */
        public long at;
        /** The number that earned it: the count reached, or the kill it came on (0 if unknown). */
        public long value;
    }

    /** One rare drop, as the luck stamps need it. */
    public static final class Drop {
        public String source, title;
        /** That source's kill count at the drop (0 for one with no kills, the Ghosts). */
        public long kill;
        public long at;
    }

    public Map<String, Entry> stamps = new LinkedHashMap<>();
    /** Events that happened and are kept: an event stamp's id, and the kill it came on (0 if none). Ledger.award. */
    public Map<String, Long> flags = new LinkedHashMap<>();
    /** Stamps the player has looked at in the book - the rest show fresh ink. */
    public Set<String> seen = new HashSet<>();

    /** Real cutscenes and pop-ups of every kind (backfilled from the stats). */
    public long cutscenes;
    /** Busts in a row across every animation, and the worst it has been. */
    public long bustRun, worstBustRun;
    public long skips;
    /** Coins of every drop revealed with a price. */
    public long coins;
    /** The Scatha kill the first pet came on (0 = none yet; backfilled as an upper bound). */
    public long firstPetKill;
    /** Today's Scatha kills, for Beginner's Luck (the user: it resets every day): the day (local epoch day) and the
     *  kills on it. A new day starts the count again. */
    public long scathaDay = -1, scathaDayKills;
    /** Today's kills per animation ("blaze", "diana", "diana:INQUISITOR"...), for the stamps that reset every day
     *  (Natural Talent, Nobody Expects It): the day they are for (local epoch day) and the counts. Ledger.kill. */
    public long killsDay = -1;
    public Map<String, Long> dayKills = new LinkedHashMap<>();
    /** Dwarven O's busts in a row (Cereal Killer). */
    public long cerealRun;
    /** The last rare drops, newest last, at most {@link Ledger#RECENT}. */
    public List<Drop> recent = new ArrayList<>();
    /** The stats have been read into the Ledger once (the "pre-ledger" stamps). */
    public boolean backfilled;

    /**
     * Gson leaves fields of an older file null: puts back the empty ones. And a hand-edited or half-written file is
     * made sane rather than trusted: no grade above what its stamp has (or below 1), no negative counter, no empty
     * entry, the recent drops capped. A stamp id the table no longer has is kept (a later version may know it again)
     * - the book only draws the ones it knows.
     */
    public LedgerData fix() {
        if (stamps == null) stamps = new LinkedHashMap<>();
        if (flags == null) flags = new LinkedHashMap<>();
        if (seen == null) seen = new HashSet<>();
        if (recent == null) recent = new ArrayList<>();
        if (dayKills == null) dayKills = new LinkedHashMap<>();
        dayKills.values().removeIf(v -> v == null || v < 0);
        stamps.values().removeIf(e -> e == null || e.grade <= 0);
        for (Map.Entry<String, Entry> me : stamps.entrySet()) {
            Entry e = me.getValue();
            Stamp s = Ledger.byId(me.getKey());
            if (s != null) e.grade = Math.min(e.grade, s.grades());
            if (e.at < 0) e.at = 0;
            if (e.value < 0) e.value = 0;
        }
        flags.values().removeIf(v -> v == null);
        flags.replaceAll((k, v) -> Math.max(0L, v));
        seen.removeIf(s -> s == null);
        recent.removeIf(d -> d == null || d.at < 0);
        while (recent.size() > Ledger.RECENT) recent.remove(0);
        cutscenes = Math.max(0, cutscenes);
        bustRun = Math.max(0, bustRun);
        worstBustRun = Math.max(bustRun, Math.max(0, worstBustRun));
        skips = Math.max(0, skips);
        coins = Math.max(0, coins);
        firstPetKill = Math.max(0, firstPetKill);
        scathaDayKills = Math.max(0, scathaDayKills);
        cerealRun = Math.max(0, cerealRun);
        return this;
    }
}
