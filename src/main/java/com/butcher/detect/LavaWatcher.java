package com.butcher.detect;

import com.butcher.anim.EruptionTimeline.Creature;
import com.butcher.anim.EruptionTimeline.Drop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lava fishing's rare creatures (Lord Jawbus, Thunder, Ragnarok, the Fiery Scuttler, Plhlegblast): a kill of one
 * that is ours opens Eruption with the outcome pending, and its drop line fills it in.
 *
 * Written before any real nametag was recorded (0.2.71 records them as {@code lava_mob}), on the rules that worked
 * for Diana and the Demonlord, so <b>check them against the first records</b>:
 *
 * <ul>
 *   <li><b>Ours</b>: the creature's nametag first seen within {@link #CLAIM_REACH} blocks from
 *       {@link #CLAIM_BEFORE_MS} before to {@link #CLAIM_AFTER_MS} after its catch line (Hypixel's own, or Feesh's
 *       "has spawned!" rewrite). Anyone else's creature - a party member's, loot-shared - plays <b>only when a win
 *       of its own comes to us</b> (a loot share line within {@link #SHARE_MS} of its death, and its drop): 0.2.74-0.2.80
 *       also claimed them by loot share alone, or by having armed the hide, and in a lava party nearly every Jawbus
 *       and Thunder that opened was someone else's (the user, 2026-10-06). Only our own arm the hide.</li>
 *   <li><b>The kill</b>: its HP reads 0 (Diana's and the Demonlord's both did), or its nametag goes at
 *       {@link #GONE_FRACTION} of its health or less, close by, with no teleport. A charm ("CHARM! You charmed the
 *       Thunder") is a kill too - elusive creatures are charmed, not killed, on current Hypixel.</li>
 *   <li><b>The drop</b>: the user's logs put the drop line in the same second as the kill. A win within
 *       {@link #DROP_WAIT_MS} resolves it; none, and it is a bust. A win that came first (up to
 *       {@link #EARLY_MS} before) is the outcome already.</li>
 *   <li><b>Plhlegblast only plays for the Carmine Dye</b> (the user's call): its kills open nothing, but a
 *       Carmine Dye line opens Eruption with the outcome known.</li>
 * </ul>
 *
 * Every decision is narrated into the DecisionLog.
 */
public final class LavaWatcher {

    public interface Listener {
        /** A creature of ours died: open Eruption with the outcome pending. */
        void kill(Creature creature, String why);

        /** The pending kill's outcome: a win, or {@link Drop#BUST}. */
        void resolve(Drop drop, String line);

        /** A kill whose outcome is already known (the drop line came first, or the Carmine Dye): open with it. */
        void killKnown(Creature creature, Drop drop, String line);
    }

    private final DecisionLog log;
    private final Listener listener;
    /** Whether loot-shared kills play (config.lavaLootShare), set by the client each tick. */
    public boolean lootShare = true;

    public LavaWatcher(DecisionLog log, Listener listener) {
        this.log = log;
        this.listener = listener;
    }

    private static final double SCAN_RADIUS = 48, CLAIM_REACH = 20, DEATH_REACH = 32, TELEPORT = 12, ARM_RADIUS = 24;
    private static final long CLAIM_BEFORE_MS = 2_000, CLAIM_AFTER_MS = 6_000, SHARE_MS = 2_000, DROP_WAIT_MS = 3_000,
            EARLY_MS = 1_500, ARM_MS = 2_500, MIN_SEEN_MS = 300;
    private static final double GONE_FRACTION = 0.05;

    private static final class Mob {
        int id;
        Creature creature;
        long firstSeen;
        boolean owned, dead, immune;
        double hp = -1, maxHp = -1, distSq;
    }

    private final Map<Integer, Mob> mobs = new HashMap<>();
    /** Catch lines not yet matched to a nametag: creature, when. */
    private final List<Object[]> catches = new ArrayList<>();
    private long lastShareAt = -1;
    /** What the last loot share line named: a creature, or SHARE_UNNAMED. */
    private Object lastShareWhat;
    /** Our own creature whose nametag went short of a kill, and when: a loot share for it just after makes it one. */
    private Creature ownGone;
    private long ownGoneAt = -1;
    /** The last loot share line that named its creature, and when: with a drop of that creature's, it is a kill. */
    private Creature sharedCreature;
    private long sharedAt = -1;

    private long pendingSince = -1;
    private Creature pendingCreature;
    private Drop bestDrop;
    private String bestLine;
    private long bestAt = -1;
    /** The last creature of ours that died, and when: who a Carmine Dye with no kill pending came from. */
    private Creature lastDead;
    private long lastDeadAt = -1;
    private Vec3 lastPos;

    // ------------------------------------------------------------------ chat

    public void onChatLine(String text) {
        onChatLine(text, System.currentTimeMillis());
    }

    public void onChatLine(String text, long now) {
        if (text == null) return;
        String s = DianaWatcher.plain(text).trim();

        Creature caught = LavaDrops.ofCatch(s);
        if (caught != null) {
            catches.add(new Object[]{caught, now});
            log.note("§7Lava: you caught " + caught.title + " - claiming its nametag (\"" + s + "\")");
            for (Mob m : mobs.values()) claimByCatch(m, now);
            return;
        }

        Creature charmed = LavaDrops.ofCharm(s);
        if (charmed != null) {
            // A charm is not ownership: the user's 23:19 charm (2026-10-05) was on a party member's Thunder.
            Mob m = nearest(charmed, true);
            if (m != null && m.owned) {
                m.dead = true;
                died(charmed, "you charmed it", now);
            } else if (m != null && !m.dead) {
                m.dead = true;
                death(m, "you charmed it", now);
            } else {
                log.note("§8Lava: you charmed a " + charmed.title + " that is not yours - nothing opens");
            }
            return;
        }

        Object share = LavaDrops.ofShare(s);
        if (share != null) {
            lastShareAt = now;
            lastShareWhat = share;
            // Our own creature's nametag just went short of the 5%: a share for its kind says someone else landed the
            // last hit (the user's Thunder, 2026-10-05 23:38: gone at 2.6M, "2 Thunder Shards for assisting ..." with it).
            if (ownGone != null && now - ownGoneAt <= SHARE_MS && (share == ownGone || share == LavaDrops.SHARE_UNNAMED)) {
                Creature c = ownGone;
                ownGone = null;
                died(c, "its nametag went and a loot share for its kill came", now);
            }
            if (!lootShare) {
                log.note("§8Lava: loot share - loot-shared kills are off in the menu");
                return;
            }
            if (share instanceof Creature named) {
                sharedCreature = named;
                sharedAt = now;
                // The drop line can come first: a win of this creature's just now is its kill.
                if (pendingSince < 0 && bestDrop != null && bestDrop.win() && bestDrop != Drop.CARMINE && bestDrop.from(named) && now - bestAt <= EARLY_MS) {
                    log.note("§aLava: loot share for a " + named.title + " that dropped " + bestDrop.title + " - opening (\"" + s + "\")");
                    listener.killKnown(named, bestDrop, bestLine);
                    bestDrop = null;
                    return;
                }
            }
            return;
        }

        Drop d = LavaDrops.of(s);
        if (d != null) {
            boolean better = bestDrop == null || bestDrop == Drop.BUST || (d != Drop.BUST && d.ordinal() > bestDrop.ordinal());
            if (better || now - bestAt > DROP_WAIT_MS) {
                bestDrop = d;
                bestLine = s;
                bestAt = now;
            }
            if (d == Drop.BUST) return;
            log.note("§7Lava: drop line - " + d.title + " (\"" + s + "\")");
            if (pendingSince >= 0) {
                if (d.from(pendingCreature)) {
                    pendingSince = -1;
                    listener.resolve(d, s);
                } else {
                    log.note("§8Lava: " + d.title + " cannot come from " + pendingCreature.title + " - not this kill's");
                }
                return;
            }
            // Someone else's creature that just died, a loot share with it, and now its win: that kill.
            if (lootShare && d != Drop.CARMINE && foreignDead != null && now - foreignDeadAt <= SHARE_MS && d.from(foreignDead)
                    && lastShareAt >= 0 && Math.abs(lastShareAt - foreignDeadAt) <= SHARE_MS) {
                log.note("§aLava: " + d.title + " from someone's " + foreignDead.title + ", by loot share - opening");
                listener.killKnown(foreignDead, d, s);
                bestDrop = null;
                foreignDead = null;
                return;
            }
            // A loot share that named its creature just before: the drop is that kill's, even if its nametag was never
            // seen (out of range). Seen in the user's records: "2 Thunder Shards for assisting ..." 36 ms before Flash I.
            if (d != Drop.CARMINE && sharedCreature != null && now - sharedAt <= SHARE_MS && d.from(sharedCreature)) {
                log.note("§aLava: " + d.title + " right after a loot share for a " + sharedCreature.title + " - opening");
                listener.killKnown(sharedCreature, d, s);
                bestDrop = null;
                sharedCreature = null;
                return;
            }
            // The dye from any of them: plays even with no kill pending (Plhlegblast's only way in).
            if (d == Drop.CARMINE) {
                Creature from = lastDead != null && now - lastDeadAt <= 10_000 ? lastDead : Creature.PLHLEGBLAST;
                log.note("§aLava: the Carmine Dye - opening (from " + from.title + ")");
                listener.killKnown(from, d, s);
                bestDrop = null;
            }
        }
    }

    // ------------------------------------------------------------------ the world

    public void tick(LocalPlayer player, double hidePct) {
        long now = System.currentTimeMillis();
        scan(player, now);
        arm(hidePct, now);
        afterTick(now);
    }

    public void afterTick(long now) {
        if (pendingSince >= 0 && now - pendingSince > DROP_WAIT_MS) {
            pendingSince = -1;
            log.note("§7Lava: no rare drop in " + DROP_WAIT_MS / 1000 + " s after the kill - bust");
            listener.resolve(Drop.BUST, null);
        }
        catches.removeIf(u -> now - (long) u[1] > CLAIM_AFTER_MS);
    }

    /** One creature nametag this tick: its entity, its name, how far (squared). */
    public record Tag(int id, String name, double distSq) {
    }

    private void scan(LocalPlayer player, long now) {
        ClientLevel level = Minecraft.getInstance().level;
        if (player == null || level == null) {
            mobs.clear();
            lastPos = null;
            return;
        }
        Vec3 pos = player.position();
        boolean teleported = lastPos != null && pos.distanceToSqr(lastPos) > TELEPORT * TELEPORT;
        lastPos = pos;
        List<Tag> tags = new ArrayList<>();
        AABB box = AABB.ofSize(pos, SCAN_RADIUS * 2, SCAN_RADIUS * 2, SCAN_RADIUS * 2);
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, box)) {
            Component custom = stand.getCustomName();
            if (custom == null) continue;
            String name = custom.getString();
            // A creature already followed stays followed whatever its nametag says: Jawbus floats up immune with no health
            // on it for a few seconds (2026-10-05 records: both stands gone at 18M, the same ids back 4 s later at 20M), and
            // dropping it from the scan made it look gone - and opened Eruption on a creature still alive.
            if (LavaDrops.ofNametag(name) == null && !mobs.containsKey(stand.getId())) continue;
            tags.add(new Tag(stand.getId(), name, stand.distanceToSqr(player)));
        }
        observe(tags, teleported, now);
    }

    /** The pure step: every lava creature's nametag present this tick. */
    public void observe(List<Tag> tags, boolean teleported, long now) {
        if (teleported && !mobs.isEmpty()) log.note("§8Lava: you teleported - nametags dropped");
        Set<Integer> seen = new HashSet<>();
        for (Tag tag : tags) {
            seen.add(tag.id());
            Mob m = mobs.get(tag.id());
            // An immune nametag has no health on it: never read a number out of it.
            Double hp = LavaDrops.ofNametag(tag.name()) == null ? null : DianaWatcher.hpOf(tag.name());
            if (m == null) {
                m = new Mob();
                m.id = tag.id();
                m.creature = LavaDrops.ofNametag(tag.name());
                m.firstSeen = now;
                m.distSq = tag.distSq();
                if (hp != null) m.hp = hp;
                Double max = HellionWatcher.maxOf(tag.name());
                if (max != null) m.maxHp = max;
                mobs.put(m.id, m);
                claimByCatch(m, now);
            }
            m.distSq = tag.distSq();
            if (hp != null) m.hp = hp;
            boolean immune = LavaDrops.ofNametag(tag.name()) == null;
            if (immune != m.immune) {
                m.immune = immune;
                if (m.owned) log.note(immune ? "§8Lava: your " + m.creature.title + " shows no health (\"" + tag.name() + "\") - immune, not dead"
                        : "§8Lava: your " + m.creature.title + " shows its health again at " + fmt(m.hp));
            }
            if (!m.dead && hp != null && hp <= 0) {
                m.dead = true;
                death(m, "its nametag reads 0", now);
            }
        }
        for (Iterator<Mob> it = mobs.values().iterator(); it.hasNext(); ) {
            Mob m = it.next();
            if (seen.contains(m.id)) continue;
            it.remove();
            if (m.dead || teleported) continue;
            if (now - m.firstSeen < MIN_SEEN_MS || m.distSq > DEATH_REACH * DEATH_REACH) continue;
            if (m.hp >= 0 && m.maxHp > 0 && m.hp <= m.maxHp * GONE_FRACTION) {
                m.dead = true;
                death(m, "its nametag went at " + fmt(m.hp) + " of " + fmt(m.maxHp), now);
            } else if (m.owned && bestDrop != null && bestDrop.win() && bestDrop.from(m.creature) && now - bestAt <= EARLY_MS) {
                // Its own win just came: the last HP read was simply stale (a hard last hit).
                m.dead = true;
                death(m, "its nametag went at " + fmt(m.hp) + " right after its " + bestDrop.title, now);
            } else if (m.owned && lastShareAt >= 0 && now - lastShareAt <= SHARE_MS
                    && (lastShareWhat == m.creature || lastShareWhat == LavaDrops.SHARE_UNNAMED)) {
                m.dead = true;
                death(m, "its nametag went at " + fmt(m.hp) + " with a loot share for its kill", now);
            } else if (m.owned) {
                ownGone = m.creature;
                ownGoneAt = now;
                log.note("§8Lava: your " + m.creature.title + "'s nametag went at " + fmt(m.hp) + " - not a kill unless a loot share for it follows");
            }
        }
    }

    private void claimByCatch(Mob m, long now) {
        if (m.owned || m.dead) return;
        for (Iterator<Object[]> it = catches.iterator(); it.hasNext(); ) {
            Object[] c = it.next();
            long at = (long) c[1];
            if (c[0] != m.creature || m.firstSeen < at - CLAIM_BEFORE_MS || m.firstSeen > at + CLAIM_AFTER_MS) continue;
            if (m.distSq > CLAIM_REACH * CLAIM_REACH) continue;
            it.remove();
            m.owned = true;
            log.note("§7Lava: your " + m.creature.title + " - first seen " + Math.round(Math.sqrt(m.distSq)) + " blocks away at its catch line");
            return;
        }
    }

    /**
     * A nametag died. Ours (claimed by our catch line) opens the cutscene. Anyone else's - in a lava party that is
     * most of them, all loot-shared (the user's session, 2026-10-05: every Jawbus and Thunder that opened was a
     * party member's) - opens nothing by itself: only a win of its own coming to us by loot share plays it.
     */
    private void death(Mob m, String how, long now) {
        if (m.owned) {
            died(m.creature, how, now);
            return;
        }
        foreignDead = m.creature;
        foreignDeadAt = now;
        // A win that came just before it (the drop line can beat the nametag), shared to us: that is its kill.
        if (lootShare && bestDrop != null && bestDrop.win() && bestDrop != Drop.CARMINE && bestDrop.from(m.creature)
                && now - bestAt <= EARLY_MS && lastShareAt >= 0 && now - lastShareAt <= SHARE_MS) {
            log.note("§aLava: someone's " + m.creature.title + " died and its " + bestDrop.title + " came to you by loot share - opening");
            listener.killKnown(m.creature, bestDrop, bestLine);
            bestDrop = null;
            return;
        }
        log.note("§8Lava: a " + m.creature.title + " died that is not yours - " + how + "; it plays only if its drop comes to you");
    }

    /** The last creature not ours that died, and when: a shared win just after is its. */
    private Creature foreignDead;
    private long foreignDeadAt = -1;

    private void died(Creature c, String how, long now) {
        if (pendingSince >= 0 && now - pendingSince < 1_000 && pendingCreature == c) return;
        lastDead = c;
        lastDeadAt = now;
        if (c == Creature.PLHLEGBLAST) {
            log.note("§7Lava: your Plhlegblast died - " + how + "; it only plays for the Carmine Dye");
            if (bestDrop == Drop.CARMINE && now - bestAt <= EARLY_MS) listener.killKnown(c, Drop.CARMINE, bestLine);
            return;
        }
        if (bestDrop != null && bestDrop.win() && bestDrop.from(c) && now - bestAt <= EARLY_MS) {
            log.note("§aLava: your " + c.title + " died - " + how + "; it dropped " + bestDrop.title);
            listener.killKnown(c, bestDrop, bestLine);
            bestDrop = null;
            return;
        }
        log.note("§aLava: your " + c.title + " died - " + how + "; opening, waiting for the drop");
        pendingSince = now;
        pendingCreature = c;
        bestDrop = null;
        bestLine = null;
        listener.kill(c, how);
    }

    private Mob nearest(Creature c, boolean alive) {
        Mob best = null;
        for (Mob m : mobs.values()) {
            if (m.creature != c || (alive && m.dead)) continue;
            if (best == null || m.distSq < best.distSq) best = m;
        }
        return best;
    }

    // ------------------------------------------------------------------ the pre-kill hide

    private volatile long armedUntil;

    /** Whether a creature of ours is nearly dead: the HUD and chat fade (Diana's rule). Cheap, any thread. */
    public boolean armed() {
        return System.currentTimeMillis() < armedUntil;
    }

    /** Whether the hide is armed at {@code now} (for the offline tests, which run on their own clock). */
    public boolean armedAt(long now) {
        return now < armedUntil;
    }

    /** Arms the hide for any creature of ours at or below {@code pct}% of its health. Public for the offline tests. */
    public void arm(double pct, long now) {
        if (pct <= 0) return;
        // Only our own: hiding the HUD for every party member's creature hid it half the time.
        for (Mob m : mobs.values()) {
            if (m.dead || !m.owned || m.creature == Creature.PLHLEGBLAST) continue;
            if (m.distSq > ARM_RADIUS * ARM_RADIUS || now - m.firstSeen < MIN_SEEN_MS) continue;
            if (m.hp < 0 || m.maxHp <= 0 || m.hp > m.maxHp * pct / 100.0) continue;
            if (now >= armedUntil) log.note("§7Lava: " + m.creature.title + " is at " + fmt(m.hp) + " - hiding the HUD");
            armedUntil = now + ARM_MS;
        }
    }

    private static String fmt(double hp) {
        return hp >= 1e6 ? Math.round(hp / 1e5) / 10.0 + "M" : hp >= 1e3 ? Math.round(hp / 100) / 10.0 + "k" : String.valueOf(Math.round(hp));
    }
}
