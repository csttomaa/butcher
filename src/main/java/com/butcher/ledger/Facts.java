package com.butcher.ledger;

import java.util.HashMap;
import java.util.Map;

/**
 * What the stats say, in one place the Ledger can read without knowing where it came from: ButcherStats fills one
 * (ButcherStats.facts), and the offline test fills one by hand. Plain fields, no logic.
 */
public final class Facts {
    // The Scatha.
    public long scathaKills, petsRare, petsEpic, petsLegendary, scathaWorstDry, scathaMisses;
    // Vanguard.
    public long corpses, corpseLegendaries, corpseDyes, lockets, corpseWorstDry;
    // Diana: kills of every creature together, drops by name.
    public long dianaKills;
    public Map<String, Integer> dianaDrops = new HashMap<>();
    // The later sources ("blaze", "lava", "shark", "ghost", "seraph"): kills, worst dry run, drops by name.
    public Map<String, Long> kills = new HashMap<>();
    public Map<String, Long> worstDry = new HashMap<>();
    public Map<String, Map<String, Integer>> drops = new HashMap<>();

    public long pets() {
        return petsRare + petsEpic + petsLegendary;
    }

    public long kills(String source) {
        return kills.getOrDefault(source, 0L);
    }

    public long worstDry(String source) {
        return worstDry.getOrDefault(source, 0L);
    }

    public long drops(String source, String title) {
        Map<String, Integer> m = drops.get(source);
        return m == null ? 0 : m.getOrDefault(title, 0);
    }

    public long drops(String source) {
        Map<String, Integer> m = drops.get(source);
        long n = 0;
        if (m != null) for (int v : m.values()) n += v;
        return n;
    }

    public long dianaDrop(String name) {
        return dianaDrops.getOrDefault(name, 0);
    }
}
