package com.butcher.detect;

import com.butcher.anim.HellionTimeline.Drop;
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
import java.util.Map;
import java.util.Set;

/**
 * The player's own tier IV Inferno Demonlord: its kill opens Hellion, its drop line fills the outcome in.
 *
 * Written against a friend's real session (2026-10-03: 13 kills, four of them with nametags recorded on
 * 0.2.68), which settled what reasoning would have got wrong:
 *
 * <ul>
 *   <li><b>No "Spawned by" line was ever seen</b> under the boss, so ownership cannot come from it. Instead the
 *       quest's own line "SLAYER BOSS! The Inferno Demonlord IV spawned!" marks it, and the boss's nametag shows
 *       up within a second, 4-11 blocks away: the first T4 nametag within {@link #CLAIM_REACH} blocks from
 *       {@link #CLAIM_BEFORE_MS} before to {@link #CLAIM_AFTER_MS} after that line is ours.</li>
 *   <li><b>The nametag goes and comes back twice</b> - at about 2/3 and 1/3 of its health the boss hides while its
 *       demons fight, and a new entity appears with the same name at about the HP it left at (99.9M -> 99.9M,
 *       56.7M -> 48.5M). A T4 nametag first seen within {@link #PHASE_GAP_MS} of ours going, close by, at or a
 *       little below the HP it went at, is ours again.</li>
 *   <li><b>The kill</b>: ours reads "0/150M❤" (seen on both kills). About 1.2 s later comes the drop line, a
 *       quarter-second after that "SLAYER QUEST COMPLETE!". So Hellion opens on the 0, outcome pending, and the
 *       first win among the drop lines resolves it; "QUEST COMPLETE" with no win resolves it as a bust. If the 0
 *       was missed, "QUEST COMPLETE" after a boss of ours is the kill, with whatever dropped already known.</li>
 *   <li>"SLAYER QUEST FAILED!" (the boss killed the player) and a cancelled quest forget the boss.</li>
 * </ul>
 *
 * Tier IV only (the user's call): the nametag must name "Inferno Demonlord IV". Every decision is narrated.
 */
public final class HellionWatcher {

    public interface Listener {
        /** Our boss died: open Hellion with the outcome pending. */
        void kill();

        /** The pending kill's outcome: a win, or {@link Drop#BUST}. */
        void resolve(Drop drop, String line);

        /** A kill the nametag missed, its outcome already known: open Hellion with it. */
        void killKnown(Drop drop, String line);
    }

    private final DecisionLog log;
    private final Listener listener;

    public HellionWatcher(DecisionLog log, Listener listener) {
        this.log = log;
        this.listener = listener;
    }

    static final String NAME = "Inferno Demonlord IV";
    private static final double SCAN_RADIUS = 48, CLAIM_REACH = 16,
            // A demon phase comes back where it left, a few blocks off; neighbouring players' bosses stood at 19-26
            // blocks and were taken for it (a friend's records, 2026-10-12). The trailing ✯ on a nametag is not
            // ownership - it is a Shuriken's Magic Find mark, which anyone can put on any mob (the user).
            PHASE_REACH = 14, TELEPORT = 12, FALLBACK_REACH = 12;
    private static final long CLAIM_BEFORE_MS = 2_000, CLAIM_AFTER_MS = 5_000, PHASE_GAP_MS = 45_000;
    /**
     * A boss that comes with the quest itself: no "SLAYER BOSS!" line and no mini-bosses, its nametag at full health
     * a few blocks away within the same tick as "SLAYER QUEST STARTED!" (a friend's records, 2026-10-12: 22 of 131
     * kills, one of them an Archfiend Dice that never played). First seen within this long of the quest starting,
     * and this close, at full health.
     */
    private static final long INSTANT_MS = 1_500;
    private static final double INSTANT_REACH = 20;
    /** A drop line this long after the kill, with no "QUEST COMPLETE", still counts; then it is a bust. */
    private static final long DROP_WAIT_MS = 3_000;

    private static final class Boss {
        int id;
        long firstSeen;
        boolean owned, dead;
        double hp = -1, maxHp = -1, distSq;
        String name = "";
    }

    private final Map<Integer, Boss> bosses = new HashMap<>();
    private long spawnLineAt = -1;
    /**
     * Hypixel only started announcing the boss itself in chat that same day (the afternoon's lines have only
     * "SLAYER MINI-BOSS ... has spawned!"). Until a spawn line has been seen, a quest that is running claims
     * the first T4 nametag close by instead; once one has, only spawn lines claim.
     */
    private boolean spawnLinesExist;
    private long questStartedAt = -1;
    /** Ours went mid-fight (a demon phase): when, and at what HP. */
    private long oursWentAt = -1;
    private double oursWentHp = -1, oursMaxHp = -1;
    /** Whether a boss of ours exists this quest (seen or between phases), and the last time it was seen. */
    private boolean oursThisQuest;
    private long oursLastSeen = -1;
    /** A kill opened and waiting for its drop: when it died (-1 for none). */
    private long pendingSince = -1;
    /** The best drop seen since the kill (or in the last moments, for a kill the nametag missed). */
    private Drop bestDrop;
    private String bestLine;
    private long bestAt = -1;
    private Vec3 lastPos;

    /** Every game chat line. */
    public void onChatLine(String text) {
        onChatLine(text, System.currentTimeMillis());
    }

    /** A game chat line at {@code now} - the replay (tools/HellionReplay) feeds recorded ones. */
    public void onChatLine(String text, long now) {
        if (text == null) return;
        String s = DianaWatcher.plain(text).trim();

        if (s.startsWith("SLAYER BOSS!") && s.contains(NAME) && s.contains("spawned")) {
            spawnLineAt = now;
            spawnLinesExist = true;
            oursThisQuest = false;
            oursWentAt = -1;
            log.note("§7Blaze: your Inferno Demonlord IV spawned - claiming its nametag");
            for (Boss b : bosses.values()) claim(b, now);
            return;
        }
        if (s.startsWith("SLAYER QUEST STARTED!")) {
            questStartedAt = now;
            forget();
            // The boss may already be standing there: its nametag can arrive the same tick, before the line.
            for (Boss b : bosses.values()) claim(b, now);
            return;
        }
        if (s.startsWith("SLAYER QUEST FAILED!") || s.startsWith("Your Slayer Quest has been cancelled")) {
            if (oursThisQuest) log.note("§8Blaze: the quest ended without a kill (" + s + ")");
            forget();
            questStartedAt = -1;
            return;
        }

        Drop d = HellionDrops.of(s);
        if (d != null) {
            boolean better = bestDrop == null || bestDrop == Drop.BUST || (d != Drop.BUST && d.ordinal() > bestDrop.ordinal());
            if (better || now - bestAt > DROP_WAIT_MS) {
                bestDrop = d;
                bestLine = s;
                bestAt = now;
            }
            log.note("§7Blaze: drop line - " + (d == Drop.BUST ? "not one the animation plays" : d.title) + " (\"" + s + "\")");
            if (pendingSince >= 0 && d.win()) {
                pendingSince = -1;
                listener.resolve(d, s);
            }
            return;
        }

        if (s.startsWith("SLAYER QUEST COMPLETE!")) {
            if (pendingSince >= 0) {
                pendingSince = -1;
                log.note("§7Blaze: quest complete with no drop the animation plays - bust");
                listener.resolve(Drop.BUST, s);
            } else if (oursThisQuest && !killedThisQuest) {
                // The 0 on the nametag was missed (too far, or it went first): the kill is now, outcome known.
                Drop known = bestDrop != null && now - bestAt <= DROP_WAIT_MS ? bestDrop : Drop.BUST;
                log.note("§aBlaze: quest complete - your Demonlord died out of sight; " + (known.win() ? known.title : "bust"));
                listener.killKnown(known, bestLine);
            } else if (!killedThisQuest && bestDrop != null && bestDrop.win() && now - bestAt <= DROP_WAIT_MS) {
                // Never claimed (it spawned too far off, or without a line), but the drop line is our own - a slayer
                // drop in brackets only ever names the player's own loot. The win still plays.
                log.note("§aBlaze: quest complete with " + bestDrop.title + " - your Demonlord was never claimed; playing it");
                listener.killKnown(bestDrop, bestLine);
            }
            forget();
            questStartedAt = -1;
        }
    }

    private boolean killedThisQuest;

    /** @param hidePct the pre-kill hide, as a percentage of the boss's health (0 off) */
    public void tick(LocalPlayer player, double hidePct) {
        long now = System.currentTimeMillis();
        scan(player, now);
        arm(hidePct, now);
        afterTick(now);
    }

    /** How long the hide holds after it last held: covers the moment between the 0 and Hellion opening. */
    private static final long ARM_MS = 2_500;
    private static final double ARM_RADIUS = 24;
    private volatile long armedUntil;

    /** Whether your Demonlord is nearly dead: the HUD and chat fade (Diana's rule, SkyMaxx's way). Cheap, any thread. */
    public boolean armed() {
        return System.currentTimeMillis() < armedUntil;
    }

    private void arm(double pct, long now) {
        if (pct <= 0) return;
        for (Boss b : bosses.values()) {
            if (!b.owned || b.dead || b.distSq > ARM_RADIUS * ARM_RADIUS) continue;
            double max = b.maxHp > 0 ? b.maxHp : 150_000_000.0;
            if (b.hp < 0 || b.hp > max * pct / 100.0) continue;
            if (now >= armedUntil) log.note("§7Blaze: your Demonlord is at " + fmt(b.hp) + " - hiding the HUD");
            armedUntil = now + ARM_MS;
        }
    }

    public void afterTick(long now) {
        if (pendingSince >= 0 && now - pendingSince > DROP_WAIT_MS) {
            pendingSince = -1;
            log.note("§7Blaze: no win in " + DROP_WAIT_MS / 1000 + " s after the kill - bust");
            listener.resolve(Drop.BUST, null);
        }
    }

    /** One T4 nametag this tick: its entity, its name, how far (squared). */
    public record Tag(int id, String name, double distSq) {
    }

    private void scan(LocalPlayer player, long now) {
        ClientLevel level = Minecraft.getInstance().level;
        if (player == null || level == null) {
            bosses.clear();
            lastPos = null;
            return;
        }
        Vec3 pos = player.position();
        boolean teleported = lastPos != null && pos.distanceToSqr(lastPos) > TELEPORT * TELEPORT;
        lastPos = pos;
        java.util.List<Tag> tags = new java.util.ArrayList<>();
        AABB box = AABB.ofSize(pos, SCAN_RADIUS * 2, SCAN_RADIUS * 2, SCAN_RADIUS * 2);
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, box)) {
            Component custom = stand.getCustomName();
            if (custom == null) continue;
            String name = custom.getString();
            // A boss already followed stays followed whatever its nametag says, as Jawbus's must (it floats up immune with
            // no health shown); only a name with health on it starts following one.
            if ((!name.contains(NAME) || !name.contains("/")) && !bosses.containsKey(stand.getId())) continue;
            tags.add(new Tag(stand.getId(), name, stand.distanceToSqr(player)));
        }
        observe(tags, teleported, now);
    }

    /** The pure step: every T4 nametag present this tick. The replay calls it with recorded nametags. */
    public void observe(java.util.List<Tag> tags, boolean teleported, long now) {
        if (teleported && !bosses.isEmpty()) log.note("§8Blaze: you teleported - nametags dropped");
        Set<Integer> seen = new HashSet<>();
        for (Tag tag : tags) {
            String name = tag.name();
            seen.add(tag.id());
            Boss b = bosses.get(tag.id());
            // Health is read only off a name that shows it - never a number out of an immune or phase nametag.
            Double hp = name.contains(NAME) && name.contains("/") && name.contains("❤") ? DianaWatcher.hpOf(name) : null;
            if (b != null && hp == null && !name.equals(b.name) && b.owned)
                log.note("§8Blaze: your Demonlord shows no health (\"" + name + "\") - immune, not dead");
            if (b == null) {
                b = new Boss();
                b.id = tag.id();
                b.firstSeen = now;
                b.distSq = tag.distSq();
                b.name = name;
                if (hp != null) b.hp = hp;
                Double max = maxOf(name);
                if (max != null) b.maxHp = max;
                bosses.put(b.id, b);
                claim(b, now);
            }
            b.distSq = tag.distSq();
            b.name = name;
            if (hp != null) b.hp = hp;
            if (b.owned) oursLastSeen = now;
            if (b.owned && !b.dead && hp != null && hp <= 0) {
                b.dead = true;
                killed("its nametag reads " + name.substring(name.indexOf(NAME)), now);
            }
        }
        for (Iterator<Boss> it = bosses.values().iterator(); it.hasNext(); ) {
            Boss b = it.next();
            if (seen.contains(b.id)) continue;
            it.remove();
            if (!b.owned || b.dead) continue;
            if (teleported) continue;
            // Mid-fight: a demon phase. Remember where it went, to know it again.
            oursWentAt = now;
            oursWentHp = b.hp;
            oursMaxHp = b.maxHp;
            log.note("§8Blaze: your Demonlord's nametag went at " + fmt(b.hp) + " - a demon phase, waiting for it");
        }
    }

    /** Ours if it appeared close by around the spawn line, or came back where ours left a phase. */
    private void claim(Boss b, long now) {
        if (b.owned || b.dead) return;
        if (ownedIds.contains(b.id)) {
            own(b, "back in range at " + fmt(b.hp));
            oursWentAt = -1;
            return;
        }
        // A fresh boss at full health may stand further off at first (21 blocks in a friend's records, 2026-10-12).
        double reach = b.maxHp > 0 && b.hp >= b.maxHp * 0.99 ? INSTANT_REACH + 4 : CLAIM_REACH;
        if (spawnLineAt >= 0 && b.firstSeen >= spawnLineAt - CLAIM_BEFORE_MS && b.firstSeen <= spawnLineAt + CLAIM_AFTER_MS
                && b.distSq <= reach * reach && !oursThisQuest) {
            own(b, "first seen " + Math.round(Math.sqrt(b.distSq)) + " blocks away at the spawn line");
            spawnLineAt = -1;
            return;
        }
        if (questStartedAt >= 0 && !oursThisQuest && b.firstSeen >= questStartedAt - 500 && b.firstSeen <= questStartedAt + INSTANT_MS
                && b.maxHp > 0 && b.hp >= b.maxHp * 0.99 && b.distSq <= INSTANT_REACH * INSTANT_REACH) {
            own(b, "at full health " + Math.round(Math.sqrt(b.distSq)) + " blocks away as the quest started (it spawned with the quest)");
            return;
        }
        if (!spawnLinesExist && questStartedAt >= 0 && !oursThisQuest && b.firstSeen > questStartedAt
                && b.distSq <= FALLBACK_REACH * FALLBACK_REACH) {
            own(b, "first seen " + Math.round(Math.sqrt(b.distSq)) + " blocks away during your quest (no spawn line in this format)");
            return;
        }
        if (oursWentAt >= 0 && now - oursWentAt <= PHASE_GAP_MS && b.distSq <= PHASE_REACH * PHASE_REACH && b.hp >= 0
                && oursMaxHp > 0 && b.hp <= oursWentHp + oursMaxHp * 0.02 && b.hp >= oursWentHp - oursMaxHp * 0.15) {
            own(b, "back from a demon phase at " + fmt(b.hp));
            oursWentAt = -1;
        }
    }

    /** Every entity of ours this quest: one that walks out of range and back keeps its id, and is still ours. */
    private final Set<Integer> ownedIds = new HashSet<>();

    private void own(Boss b, String why) {
        b.owned = true;
        ownedIds.add(b.id);
        oursThisQuest = true;
        log.note("§7Blaze: your Demonlord - " + why);
    }

    private void killed(String how, long now) {
        killedThisQuest = true;
        // A win that came first (it never has, but a line can beat a nametag) is the outcome already.
        if (bestDrop != null && bestDrop.win() && now - bestAt <= 1_500) {
            log.note("§aBlaze: your Demonlord died - " + how + "; it dropped " + bestDrop.title);
            listener.killKnown(bestDrop, bestLine);
            return;
        }
        log.note("§aBlaze: your Demonlord died - " + how + "; opening, waiting for the drop");
        pendingSince = now;
        bestDrop = null;
        bestLine = null;
        listener.kill();
    }

    private void forget() {
        ownedIds.clear();
        oursThisQuest = false;
        killedThisQuest = false;
        oursWentAt = -1;
        spawnLineAt = -1;
        for (Boss b : bosses.values()) b.owned = false;
    }

    /** "150M" of "[Lv825]  Inferno Demonlord IV 99.9M/150M❤". */
    static Double maxOf(String name) {
        int slash = name.lastIndexOf('/');
        if (slash < 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = slash + 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isDigit(c) || c == '.' || c == 'k' || c == 'K' || c == 'm' || c == 'M' || c == 'b' || c == 'B') sb.append(c);
            else break;
        }
        return DianaWatcher.hpOf(sb + "/" + sb + "❤");
    }

    private static String fmt(double hp) {
        return hp >= 1e6 ? Math.round(hp / 1e5) / 10.0 + "M" : String.valueOf(Math.round(hp));
    }
}
