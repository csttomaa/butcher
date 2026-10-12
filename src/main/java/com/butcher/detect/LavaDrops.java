package com.butcher.detect;

import com.butcher.anim.EruptionTimeline.Creature;
import com.butcher.anim.EruptionTimeline.Drop;

import java.util.Locale;

/**
 * Lava fishing's chat lines, read. Pure (tools/LavaDetectTest).
 *
 * From the user's own game logs (2026-10-05): drops arrive as {@code RARE DROP! Enchanted Book (Flash I) (+157 ✯
 * Magic Find)} - the item <i>not</i> in brackets, unlike a slayer's - charms as {@code CHARM! You charmed the Thunder
 * and received 2 Thunder Shards!}, loot share as {@code LOOT SHARE You received 2 Thunder Shards for assisting X!}
 * or {@code LOOT SHARE You received loot for assisting X!}. The catch lines are Hypixel's own (Feesh's constants)
 * or, with Feesh installed, its local rewrite {@code A Thunder has spawned!} - both are read.
 *
 * The Carmine Dye's own line has not been seen by anyone we can ask; any drop or WOW! line naming it is taken.
 */
public final class LavaDrops {
    private LavaDrops() {}

    private static final String[] PREFIXES = {"RARE DROP!", "VERY RARE DROP!", "CRAZY RARE DROP!", "PRAY TO RNGESUS DROP!",
            "INSANE DROP!", "LEGENDARY DROP!", "WOW!"};

    /** A drop line's drop: a win, {@link Drop#BUST} for any other drop, or null for a line that is not a drop. */
    public static Drop of(String line) {
        if (line == null) return null;
        String s = DianaWatcher.plain(line).trim();
        boolean drop = false;
        for (String p : PREFIXES) {
            if (s.startsWith(p)) {
                drop = true;
                break;
            }
        }
        if (!drop) return null;
        String l = s.toLowerCase(Locale.ROOT);
        // Diana's dye and her drops share the prefix; they are not ours.
        if (l.contains("mythological dye")) return null;
        if (l.contains("carmine dye")) return Drop.CARMINE;
        if (s.startsWith("WOW!")) return null;
        if (l.contains("burnt texts")) return Drop.BURNT_TEXTS;
        if (l.contains("scuttler shell")) return Drop.SHELL;
        if (l.contains("radioactive vial")) return Drop.VIAL;
        if (l.contains("brimstone handle")) return Drop.BRIMSTONE;
        if (l.contains("flash i)") || l.contains("flash 1)")) return Drop.FLASH;
        if (l.contains("severed pincer")) return Drop.PINCER;
        if (l.contains("chain of the end times")) return Drop.CHAIN;
        return Drop.BUST;
    }

    /** The creature a nametag names (it must carry health: "... Lord Jawbus 1M/2M❤"), or null. */
    public static Creature ofNametag(String name) {
        if (name == null || !name.contains("/") || !name.contains("❤")) return null;
        return named(DianaWatcher.plain(name));
    }

    /** The creature a text names, or null. */
    static Creature named(String s) {
        if (s.contains("Jawbus")) return Creature.JAWBUS;
        if (s.contains("Ragnarok")) return Creature.RAGNAROK;
        if (s.contains("Fiery Scuttler")) return Creature.SCUTTLER;
        if (s.contains("Plhlegblast")) return Creature.PLHLEGBLAST;
        if (s.contains("Thunder")) return Creature.THUNDER;
        return null;
    }

    /**
     * The creature a catch line announces, or null. Hypixel's own lines, and Feesh's rewrite of them ("A Thunder
     * has spawned!", "DOUBLE ... has spawned!") - any game line naming one of ours with "spawned", "emerges",
     * "arrived", "is here", "waddles up" or "appeared", that is not a drop, a charm, a loot share or a death.
     */
    public static Creature ofCatch(String line) {
        if (line == null) return null;
        String s = DianaWatcher.plain(line).trim();
        if (s.contains("DROP!") || s.startsWith("CHARM!") || s.startsWith("LOOT SHARE") || s.contains("killed by")
                || s.contains("Shard") || s.startsWith("WOW!")) return null;
        String l = s.toLowerCase(Locale.ROOT);
        if (!(l.contains("spawned") || l.contains("emerges") || l.contains("has arrived") || l.contains("is here")
                || l.contains("waddles up") || l.contains("appeared"))) return null;
        return named(s);
    }

    /** "CHARM! You charmed the Thunder and received ...": the creature, or null. */
    public static Creature ofCharm(String line) {
        if (line == null) return null;
        String s = DianaWatcher.plain(line).trim();
        if (!s.startsWith("CHARM!") || !s.contains("You charmed")) return null;
        int recv = s.indexOf(" and received");
        return named(recv > 0 ? s.substring(0, recv) : s);
    }

    /**
     * A loot share line: the creature it names ("2 Thunder Shards for assisting"), {@link #SHARE_UNNAMED} when it
     * names none ("loot for assisting X"), or null for a line that is not a loot share.
     */
    public static Object ofShare(String line) {
        if (line == null) return null;
        String s = DianaWatcher.plain(line).trim();
        if (!s.startsWith("LOOT SHARE") || !s.contains("for assisting")) return null;
        String head = s.substring(0, s.indexOf("for assisting"));
        Creature c = named(head);
        return c != null ? c : SHARE_UNNAMED;
    }

    public static final Object SHARE_UNNAMED = new Object();
}
