package com.butcher.ledger;

/**
 * The Ledger's chapters, one per animation and one for the House (what crosses them), in the book's order.
 * {@code colour} is the animation's own menu colour (the divider tab); {@code ink} a darker one that reads on the
 * cream page (the stamps). {@code icon} is a vanilla item's registry path.
 */
public enum Chapter {
    HOUSE("The House", "Every animation", 0xFFE8B04A, 0xFF9A6A18, "gold_nugget"),
    SCATHA("Scatha", "The Cleaver", 0xFFB27BE6, 0xFF6A3FA0, "amethyst_shard"),
    VANGUARD("Vanguard", "The Thaw", 0xFF72C6F2, 0xFF2F7FB0, "blue_ice"),
    DIANA("Diana", "The Chopping Block", 0xFFE4573F, 0xFFA8301E, "golden_shovel"),
    HELLION("Hellion", "Inferno Demonlord IV", 0xFFFFA238, 0xFFB8601A, "blaze_rod"),
    ERUPTION("Eruption", "Lava fishing", 0xFFFF8C2E, 0xFFA8381A, "magma_cream"),
    FIN("Fin", "Fishing Festival", 0xFF3FB0D0, 0xFF1F7896, "tropical_fish"),
    WISP("Wisp", "Dwarven Mines Ghosts", 0xFFA9E4EE, 0xFF3E8C9A, "ghast_tear"),
    SERAPH("The Seraph", "Voidgloom Seraph IV", 0xFFA36BF5, 0xFF6A3CB0, "ender_pearl");

    public final String title, subtitle, icon;
    public final int colour, ink;

    Chapter(String title, String subtitle, int colour, int ink, String icon) {
        this.title = title;
        this.subtitle = subtitle;
        this.colour = colour;
        this.ink = ink;
        this.icon = icon;
    }
}
