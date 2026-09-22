package com.butcher.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.butcher.ButcherClient;
import net.fabricmc.loader.api.FabricLoader;

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
    /** Corpses since the last legendary, and the worst that has ever got. */
    public int corpseDryStreak;
    public int worstCorpseDryStreak;

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
        save();
    }

    /**
     * One Vanguard corpse was opened and read.
     *
     * Takes the tier counts rather than the haul so that {@code config} does not have to
     * know what a {@code CorpseHaul} is - the stats file outlives any one feature's types.
     */
    public void onCorpse(int commons, int uncommons, int legendaries, int specials) {
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
        corpseDryStreak = fresh.corpseDryStreak;
        worstCorpseDryStreak = fresh.worstCorpseDryStreak;
        save();
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
        corpseDryStreak = fresh.corpseDryStreak;
        worstCorpseDryStreak = fresh.worstCorpseDryStreak;
        save();
    }

    // ------------------------------------------------------------------ storage

    private static ButcherStats load() {
        try {
            if (Files.exists(PATH)) {
                ButcherStats read = GSON.fromJson(Files.readString(PATH), ButcherStats.class);
                if (read != null) return read;
            }
        } catch (Exception e) {
            ButcherClient.LOGGER.warn("[Butcher] could not read stats.json", e);
        }
        return new ButcherStats();
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (Exception e) {
            // Never worth interrupting a cutscene over. The numbers are a nicety.
            ButcherClient.LOGGER.warn("[Butcher] could not write stats.json", e);
        }
    }
}
