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
    /** The last rare drops, newest last, at most {@link Ledger#RECENT}. */
    public List<Drop> recent = new ArrayList<>();
    /** The stats have been read into the Ledger once (the "pre-ledger" stamps). */
    public boolean backfilled;

    /** Gson leaves fields of an older file null: puts back the empty ones. */
    public LedgerData fix() {
        if (stamps == null) stamps = new LinkedHashMap<>();
        if (flags == null) flags = new LinkedHashMap<>();
        if (seen == null) seen = new HashSet<>();
        if (recent == null) recent = new ArrayList<>();
        return this;
    }
}
