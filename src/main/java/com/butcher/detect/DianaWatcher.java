package com.butcher.detect;

import com.butcher.anim.ChopTimeline.Creature;
import com.butcher.anim.ChopTimeline.Verdict;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Diana's kills and drops.
 *
 * <b>A kill</b> has no chat message (checked against 500 captured lines, 2026-09-30: an
 * Inquisitor dug and killed left only "Good Grief! You dug out a Minos Inquisitor!"). So it is
 * SBO's way: the creature's nametag armour stand carries its health, and the kill is the tick that
 * health reads 0 - positive evidence, unlike the Scatha's vanish. As a fallback a nametag that goes
 * while its mob was nearly dead, close by and without a teleport, counts too.
 *
 * <b>Whose it is</b>: a creature the player dug up - the dig line names it, and the nametag of that
 * creature that appears next to the player around that moment is the one - or one the player got
 * loot share on. Hypixel says so in chat as it dies ("LOOT SHARE You received 2 King Minos Shards
 * for assisting _Monk3y!", 40 ms <i>before</i> the nametag read 0 on 2026-09-30; older lines say only
 * "You received loot for assisting X!"), so a loot share line within {@link #SHARE_MS} of a
 * creature's death, either side, makes it ours. Somebody else's Sphinx dying next to the player
 * with no loot share (seen the same day) is not.
 *
 * <b>A drop</b> is a chat line: "RARE DROP! &lt;item&gt; (+N Magic Find)" for the Relic, Brain Food,
 * the Chimera book, the Stinger, the Manti-core, the Crown and the Wool, and "WOW! &lt;player&gt;
 * found a Mythological Dye!" for the Dye - which names a player, so it has to be us. A treasure
 * ("RARE DROP! You dug out a Griffin Feather!") is not a drop.
 *
 * Every decision is narrated into the DecisionLog, and each creature's nametag is recorded as it
 * was first seen and as it died, so a miss can be read back instead of guessed at.
 */
public final class DianaWatcher {

    public interface Listener {
        /** One of the player's own four creatures has just died; its drop, if any, is not known yet. */
        void kill(Creature creature);

        /** A drop worth the cutscene has been announced. */
        void drop(Verdict verdict, String line);
    }

    private final DecisionLog log;
    private final Listener listener;

    public DianaWatcher(DecisionLog log, Listener listener) {
        this.log = log;
        this.listener = listener;
    }

    /** The four kills that play the cutscene, by the name their nametag and dig line use. */
    private static final Map<Creature, String> NAMES = new HashMap<>();
    static {
        NAMES.put(Creature.INQUISITOR, "Minos Inquisitor");
        NAMES.put(Creature.KING_MINOS, "King Minos");
        NAMES.put(Creature.MANTICORE, "Manticore");
        NAMES.put(Creature.SPHINX, "Sphinx");
    }

    private static final Pattern DUG = Pattern.compile("You dug out (?:an? )?(.+?)!");

    /**
     * The current health in a nametag: the number before the "/" (SBO's reading, "40M/40M"), or
     * before the heart where there is no maximum.
     */
    private static final Pattern HP = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)([kKmMbB]?)(?=\\s*(?:/|❤))");

    /** How far round the player nametags are read. */
    private static final double SCAN_RADIUS = 48;
    /** A dug creature appears next to the player - how near it must first be seen to be ours. */
    private static final double CLAIM_RADIUS = 20;
    /** And when, relative to the dig line: the nametag may arrive a little before the chat. */
    private static final long CLAIM_BEFORE_MS = 3_000, CLAIM_AFTER_MS = 15_000;
    /** The vanish fallback: this nearly dead, this close, no teleport. */
    private static final double VANISH_HP_FRACTION = 0.05, VANISH_RADIUS = 32, TELEPORT = 10;

    private static final class Mob {
        int id;
        Creature creature;
        long firstSeen;
        boolean owned, dead;
        int ticksSeen;
        /** When this one last held the HUD hidden (arm), or 0. */
        long armedAt;
        double hp = -1, maxHp = -1, distSq;
        String name = "";
    }

    private final Map<Integer, Mob> mobs = new HashMap<>();
    private final Map<Creature, Long> dugAt = new HashMap<>();
    private Vec3 lastPos;

    /** How far apart a loot share line and a death can be and still be the same kill. */
    private static final long SHARE_MS = 2_000;
    private static final Pattern SHARE = Pattern.compile("^LOOT SHARE You received (.*?)for assisting");
    /**
     * "CHARM! You charmed the Empyrean Sphinx and received 1 Sphinx Shard!" - the player's own hit
     * took it, 60 ms before its nametag read 0 (2026-09-30). Treated like a loot share naming it.
     */
    private static final Pattern CHARM = Pattern.compile("^CHARM! You charmed the (.+?) and received");
    /** The last loot share line: when, and the creature it names (null for the older wording). */
    private long sharedAt = -1;
    private Creature sharedCreature;
    /** Deaths of creatures that were not ours, kept briefly in case their loot share line is late. */
    private final java.util.ArrayDeque<Mob> unclaimedDeaths = new java.util.ArrayDeque<>();
    private final Map<Mob, Long> diedAt = new HashMap<>();

    /** The last loot share line of any kind, never consumed - for arming, SkyMaxx's way. */
    private long lastAnyShareAt = -1;

    /** When the last loot share line came, -1 for never: the Ledger's "Shared Custody". */
    public long lastShareAt() {
        return lastAnyShareAt;
    }
    /** Arming holds this long after its reason last held (SkyMaxx: 2.5 s). */
    private static final long ARM_MS = 2_500;
    /** Having had loot share this recently means any nearby creature may be one (SkyMaxx: 5 min). */
    private static final long SHARING_MS = 300_000;
    /** How near a creature must be to arm: SkyMaxx arms inside 0.8 of its kill radius. */
    private static final double ARM_RADIUS = 16;
    private volatile long armedUntil = 0;

    /**
     * Whether a creature that may be ours is about to die, so the HUD and chat should go now. Only
     * reads a time: it is asked from the sound mixin and every HUD element, and must never walk
     * the mobs while the tick changes them. {@link #arm} decides it, once a tick.
     */
    public boolean armed() {
        return System.currentTimeMillis() < armedUntil;
    }

    /**
     * SkyMaxx's {@code aboutToDie}: ours, or anyone's while we have been getting loot share; seen
     * for 6 ticks; close by; at or below {@code belowHp}, with a maximum above it (one that starts
     * below would hide the whole fight). Held for {@link #ARM_MS} after it last held, which also
     * covers the moment between the death and the cutscene opening, and a late loot share line.
     */
    private void arm(double pct) {
        long now = System.currentTimeMillis();
        if (pct > 0) {
            boolean sharing = lastAnyShareAt >= 0 && now - lastAnyShareAt <= SHARING_MS;
            for (Mob m : mobs.values()) {
                if (m.dead || !(m.owned || sharing)) continue;
                if (m.ticksSeen < 6 || m.distSq > ARM_RADIUS * ARM_RADIUS) continue;
                if (m.hp < 0 || m.maxHp <= 0 || m.hp > m.maxHp * pct / 100.0) continue;
                if (now >= armedUntil) {
                    log.note("§7Diana: a " + NAMES.get(m.creature) + " is at " + fmt(m.hp) + " - hiding the HUD");
                }
                armedUntil = now + ARM_MS;
                m.armedAt = now;
            }
        }
    }

    /** Some lines reach us with their colour codes still in the text. */
    static String plain(String s) {
        return s.replaceAll("§.", "").strip();
    }

    // ------------------------------------------------------------------ chat

    /** Every game (not player) chat line, stripped of formatting. */
    public void onChatLine(String text) {
        if (text == null) return;
        String s = plain(text);
        Matcher share = SHARE.matcher(s);
        Matcher charm = CHARM.matcher(s);
        boolean shared = share.find();
        if (shared || charm.find()) {
            long now = System.currentTimeMillis();
            sharedAt = now;
            if (shared) lastAnyShareAt = now;
            sharedCreature = creatureIn(shared ? share.group(1) : charm.group(1));
            // Its creature may already have died a moment ago.
            for (Iterator<Mob> it = unclaimedDeaths.iterator(); it.hasNext(); ) {
                Mob m = it.next();
                if (now - diedAt.getOrDefault(m, 0L) > SHARE_MS) continue;
                if (sharedCreature != null && sharedCreature != m.creature) continue;
                it.remove();
                diedAt.remove(m);
                sharedAt = -1;
                log.note("§aDiana: loot share on a " + NAMES.get(m.creature) + " that just died - \"" + s + "\"");
                listener.kill(m.creature);
                return;
            }
            return;
        }
        Matcher dug = DUG.matcher(s);
        if (dug.find() && !s.startsWith("RARE DROP!")) {
            String what = dug.group(1);
            for (Map.Entry<Creature, String> e : NAMES.entrySet()) {
                if (!what.equalsIgnoreCase(e.getValue())) continue;
                long now = System.currentTimeMillis();
                dugAt.put(e.getKey(), now);
                log.note("§7Diana: you dug out a §f" + e.getValue() + "§7 - watching for its nametag");
                // Its nametag may already be here.
                for (Mob m : mobs.values()) claim(m, now);
            }
            return;
        }
        Verdict v = dropOf(s, ownName());
        if (v != null) {
            log.note("§7Diana: drop §f" + com.butcher.render.DianaItems.name(v) + "§7 from \"" + s + "\"");
            listener.drop(v, s);
        }
    }

    /**
     * What a chat line says dropped, or null. Pure, so it can be checked offline against the
     * captured lines.
     */
    public static Verdict dropOf(String line, String me) {
        String s = plain(line);
        String lower = s.toLowerCase(Locale.ROOT);
        if (s.startsWith("RARE DROP!") && !s.contains("You dug out")) {
            if (lower.contains("mythological dye")) return Verdict.DYE;
            if (lower.contains("chimera")) return Verdict.CHIMERA;
            for (Verdict v : Verdict.values()) {
                if (!v.win() || v == Verdict.DYE || v == Verdict.CHIMERA) continue;
                if (lower.contains(com.butcher.render.DianaItems.name(v).toLowerCase(Locale.ROOT))) return v;
            }
            return null;
        }
        if (s.startsWith("WOW!") && lower.contains("found a mythological dye")) {
            // Hypixel tells everyone nearby; only ours plays.
            if (me == null || s.contains(" " + me + " ") || s.contains("] " + me) || s.startsWith("WOW! " + me)) {
                return Verdict.DYE;
            }
        }
        return null;
    }

    private static String ownName() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? null : p.getName().getString();
    }

    // ------------------------------------------------------------------ nametags

    /** Once a client tick: read the nametags, then decide whether the HUD should be going. */
    /** @param hidePct the pre-kill hide, as a percentage of each creature's own health (0 off) */
    public void tick(LocalPlayer player, double hidePct) {
        scan(player);
        arm(hidePct);
    }

    private void scan(LocalPlayer player) {
        ClientLevel level = Minecraft.getInstance().level;
        if (player == null || level == null) {
            mobs.clear();
            lastPos = null;
            return;
        }
        long now = System.currentTimeMillis();
        Vec3 pos = player.position();
        boolean teleported = lastPos != null && pos.distanceToSqr(lastPos) > TELEPORT * TELEPORT;
        lastPos = pos;

        Set<Integer> seen = new HashSet<>();
        AABB box = AABB.ofSize(pos, SCAN_RADIUS * 2, SCAN_RADIUS * 2, SCAN_RADIUS * 2);
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, box)) {
            Component custom = stand.getCustomName();
            if (custom == null) continue;
            String name = custom.getString();
            Creature c = creatureIn(name);
            if (c == null) continue;
            seen.add(stand.getId());
            Mob m = mobs.get(stand.getId());
            if (m == null) {
                m = new Mob();
                m.id = stand.getId();
                m.creature = c;
                m.firstSeen = now;
                m.distSq = stand.distanceToSqr(player);
                mobs.put(m.id, m);
                claim(m, now);
                record("seen", m, name);
            }
            m.distSq = stand.distanceToSqr(player);
            m.name = name;
            m.ticksSeen++;
            Double hp = hpOf(name);
            if (hp != null) {
                m.hp = hp;
                m.maxHp = Math.max(m.maxHp, hp);
            }
            if (!m.dead && hp != null && hp <= 0) {
                m.dead = true;
                died(m, "its health reads 0");
            }
        }

        // Gone this tick.
        for (Iterator<Mob> it = mobs.values().iterator(); it.hasNext(); ) {
            Mob m = it.next();
            if (seen.contains(m.id)) continue;
            it.remove();
            if (m.dead || !m.owned) continue;
            String why = null;
            if (teleported) why = "you teleported";
            else if (m.hp < 0 || m.maxHp <= 0) why = "its health was never read";
            else if (m.hp > m.maxHp * VANISH_HP_FRACTION) why = "it was at " + fmt(m.hp) + " of " + fmt(m.maxHp);
            else if (m.distSq > VANISH_RADIUS * VANISH_RADIUS) why = "it was " + Math.round(Math.sqrt(m.distSq)) + " blocks away";
            if (why != null) {
                log.note("§8Diana: your " + NAMES.get(m.creature) + "'s nametag went - not a kill: " + why);
                continue;
            }
            m.dead = true;
            died(m, "its nametag went at " + fmt(m.hp) + " health");
        }
    }

    /** Ours if it first showed up close by, around when we dug one of these. */
    private void claim(Mob m, long now) {
        if (m.owned || m.dead) return;
        Long dug = dugAt.get(m.creature);
        if (dug == null) return;
        if (m.firstSeen < dug - CLAIM_BEFORE_MS || m.firstSeen > dug + CLAIM_AFTER_MS) return;
        if (m.distSq > CLAIM_RADIUS * CLAIM_RADIUS) return;
        m.owned = true;
        dugAt.remove(m.creature);
        log.note("§7Diana: claimed your §f" + NAMES.get(m.creature) + "§7 (\"" + m.name + "\")");
    }

    private void died(Mob m, String how) {
        long now = System.currentTimeMillis();
        if (!m.owned && sharedAt >= 0 && now - sharedAt <= SHARE_MS
                && (sharedCreature == null || sharedCreature == m.creature)) {
            m.owned = true;
            sharedAt = -1;
            log.note("§7Diana: you got loot share on (or charmed) this " + NAMES.get(m.creature));
        }
        // It hid the HUD, so the cutscene must follow - a party member's Inquisitor killed next to
        // us gave no loot share line at all (2026-10-01), and the HUD went for nothing.
        if (!m.owned && m.armedAt > 0 && now - m.armedAt <= ARM_MS) {
            m.owned = true;
            log.note("§7Diana: this " + NAMES.get(m.creature) + " hid the HUD, so it plays");
        }
        record("died", m, m.name);
        if (!m.owned) {
            log.note("§8Diana: a " + NAMES.get(m.creature) + " died, not yours (yet) - " + how);
            // Its loot share line may still be on its way.
            unclaimedDeaths.addLast(m);
            diedAt.put(m, now);
            while (!unclaimedDeaths.isEmpty() && now - diedAt.getOrDefault(unclaimedDeaths.peekFirst(), 0L) > SHARE_MS) {
                diedAt.remove(unclaimedDeaths.pollFirst());
            }
            return;
        }
        log.note("§aDiana: your " + NAMES.get(m.creature) + " died - " + how);
        listener.kill(m.creature);
    }

    private static Creature creatureIn(String name) {
        // King Minos before the other Minos names, which it does not contain anyway.
        for (Map.Entry<Creature, String> e : NAMES.entrySet()) {
            if (name.contains(e.getValue())) return e.getKey();
        }
        return null;
    }

    static Double hpOf(String name) {
        Matcher m = HP.matcher(name);
        if (!m.find()) return null;
        try {
            double v = Double.parseDouble(m.group(1).replace(",", ""));
            return switch (m.group(2).toLowerCase(Locale.ROOT)) {
                case "k" -> v * 1e3;
                case "m" -> v * 1e6;
                case "b" -> v * 1e9;
                default -> v;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String fmt(double hp) {
        if (hp >= 1e6) return String.format(Locale.ROOT, "%.1fM", hp / 1e6);
        if (hp >= 1e3) return String.format(Locale.ROOT, "%.1fk", hp / 1e3);
        return String.valueOf(Math.round(hp));
    }

    /** The nametag as it was, so detection can be tuned against the real thing. */
    private static void record(String event, Mob m, String name) {
        JsonObject o = new JsonObject();
        o.addProperty("event", event);
        o.addProperty("creature", m.creature.name());
        o.addProperty("owned", m.owned);
        o.addProperty("name", name);
        o.addProperty("distance", Math.round(Math.sqrt(m.distSq)));
        DropRecorder.record("diana_mob", o);
    }
}
