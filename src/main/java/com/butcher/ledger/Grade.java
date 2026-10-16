package com.butcher.ledger;

/** The grades of a tiered stamp - the grades of beef, lowest first. Index = grade - 1. */
public enum Grade {
    SELECT("Select", 0xFFB06A3A),
    CHOICE("Choice", 0xFF9AA4AE),
    PRIME("Prime", 0xFFD8A23A),
    A5("A5 Wagyu", 0xFFD0406E);

    public final String title;
    /** The ring a stamp of this grade wears. */
    public final int ring;

    Grade(String title, int ring) {
        this.title = title;
        this.ring = ring;
    }

    /** The grade for a 1-based grade number, or null for 0. */
    public static Grade of(int g) {
        return g <= 0 ? null : values()[Math.min(values().length, g) - 1];
    }
}
