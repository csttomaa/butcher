package com.butcher.ledger;

/**
 * One stamp: what it is called, what earns it, and the numbers it needs.
 *
 * A tiered stamp has up to four {@code tiers} (Select, Choice, Prime, A5 Wagyu) and its grade is how many of them
 * {@link Ledger#value} has reached. A single stamp has one grade: its one number, or - with no number - simply having
 * happened (value 1). A hidden one shows as "???" until it is earned.
 */
public record Stamp(String id, Chapter chapter, String name, String desc, boolean tiered, long[] tiers, boolean hidden) {

    /** How many grades it has: a single stamp has one. */
    public int grades() {
        return tiered ? tiers.length : 1;
    }

    /** What grade {@code g} (1-based) needs. */
    public long need(int g) {
        if (tiers.length == 0) return 1;
        return tiers[Math.min(tiers.length, Math.max(1, g)) - 1];
    }

    /** The one number a single stamp is about (its threshold), or {@code fallback} if it has none. */
    public long number(long fallback) {
        return tiers.length == 0 ? fallback : tiers[0];
    }
}
