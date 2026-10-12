package com.butcher.detect;

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
import java.util.Map;
import java.util.Set;

/**
 * The player's own Great White Sharks dying with nothing worth announcing - Fin's bust (0.2.59, the
 * user's ask). A win needs none of this: the drop line opens Fin by itself (SharkDrops).
 *
 * Written without a single real fishing line or nametag (none captured yet), on SkyMaxx's rules for
 * sea creatures (its SeaCreatureDetector, MIT, read with javap) and the Diana watcher's nametag reading:
 *
 * <ul>
 *   <li><b>Ours</b>: a Great White Shark nametag first seen within {@link #CATCH_REACH} blocks from
 *       {@link #CATCH_BEFORE_MS} before to {@link #CATCH_AFTER_MS} after the catch line - Hypixel's
 *       "Hide no longer, a Great White Shark has tracked your scent and thirsts for your blood!",
 *       matched loosely (any game line naming the shark that is not a drop). A double hook gives two.
 *   <li><b>Dead</b>: its health reads 0, or the nametag goes while at half health or less, close by,
 *       seen a few ticks, and the player did not teleport (SkyMaxx's 0.5).
 *   <li><b>A bust</b>: no Megalodon or tooth line from {@link #DROP_BEFORE_MS} before the death to
 *       {@link #DROP_AFTER_MS} after it. Then Fin plays its bust.
 * </ul>
 *
 * Every nametag is recorded as {@code fishing_mob} when first seen and at death, and every decision is
 * narrated, so the first real session can settle the guesses.
 */
public final class SharkWatcher {

    public interface Listener {
        /** One of the player's own Great White Sharks died and dropped nothing worth announcing. */
        void bust();
    }

    private final DecisionLog log;
    private final Listener listener;

    public SharkWatcher(DecisionLog log, Listener listener) {
        this.log = log;
        this.listener = listener;
    }

    static final String NAME = "Great White Shark";
    private static final double SCAN_RADIUS = 48, CATCH_REACH = 16, VANISH_RADIUS = 32, TELEPORT = 8;
    private static final long CATCH_BEFORE_MS = 2_000, CATCH_AFTER_MS = 4_000;
    private static final long DROP_BEFORE_MS = 1_500, DROP_AFTER_MS = 1_500;
    private static final double VANISH_HP_FRACTION = 0.5;
    private static final int MIN_TICKS = 6;

    private static final class Shark {
        int id;
        long firstSeen;
        boolean owned, dead;
        int ticksSeen;
        double hp = -1, maxHp = -1, distSq;
        String name = "";
    }

    private final Map<Integer, Shark> sharks = new HashMap<>();
    /** Catch lines not yet matched to a nametag (a double hook leaves two). */
    private final java.util.ArrayDeque<Long> catches = new java.util.ArrayDeque<>();
    private long lastDropAt = -1;
    /** Deaths waiting for a drop line: when each died. */
    private final java.util.ArrayDeque<Long> pending = new java.util.ArrayDeque<>();
    private Vec3 lastPos;

    /** Every game chat line. */
    public void onChatLine(String text) {
        if (text == null) return;
        String s = DianaWatcher.plain(text);
        long now = System.currentTimeMillis();
        if (SharkDrops.of(s) != null) {
            lastDropAt = now;
            if (!pending.isEmpty()) log.note("§7Fishing: the drop came - no bust");
            pending.clear();
            return;
        }
        if (s.contains(NAME) && !s.contains("DROP!") && !s.contains(":") && !s.startsWith("CHARM") && !s.startsWith("LOOT SHARE")) {
            catches.addLast(now);
            while (catches.size() > 2) catches.pollFirst();
            log.note("§7Fishing: caught a Great White Shark? \"" + s + "\"");
            // A nametag that showed up just before the line is claimed now.
            for (Shark sh : sharks.values()) claim(sh);
        }
    }

    public void tick(LocalPlayer player) {
        long now = System.currentTimeMillis();
        scan(player, now);
        // A death with no drop line in time is a bust.
        while (!pending.isEmpty() && now - pending.peekFirst() > DROP_AFTER_MS) {
            pending.pollFirst();
            log.note("§aFishing: your Great White Shark dropped nothing - bust");
            listener.bust();
        }
        while (!catches.isEmpty() && now - catches.peekFirst() > CATCH_AFTER_MS + 1000) catches.pollFirst();
    }

    private void scan(LocalPlayer player, long now) {
        ClientLevel level = Minecraft.getInstance().level;
        if (player == null || level == null) {
            sharks.clear();
            lastPos = null;
            return;
        }
        Vec3 pos = player.position();
        boolean teleported = lastPos != null && pos.distanceToSqr(lastPos) > TELEPORT * TELEPORT;
        lastPos = pos;

        Set<Integer> seen = new HashSet<>();
        AABB box = AABB.ofSize(pos, SCAN_RADIUS * 2, SCAN_RADIUS * 2, SCAN_RADIUS * 2);
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, box)) {
            Component custom = stand.getCustomName();
            if (custom == null) continue;
            String name = custom.getString();
            if (!name.contains(NAME)) continue;
            seen.add(stand.getId());
            Shark sh = sharks.get(stand.getId());
            if (sh == null) {
                sh = new Shark();
                sh.id = stand.getId();
                sh.firstSeen = now;
                sh.distSq = stand.distanceToSqr(player);
                sh.name = name;
                sharks.put(sh.id, sh);
                claim(sh);
                record("seen", sh);
            }
            sh.distSq = stand.distanceToSqr(player);
            sh.name = name;
            sh.ticksSeen++;
            Double hp = DianaWatcher.hpOf(name);
            if (hp != null) {
                sh.hp = hp;
                sh.maxHp = Math.max(sh.maxHp, hp);
            }
            if (!sh.dead && hp != null && hp <= 0) {
                sh.dead = true;
                died(sh, now, "its health reads 0");
            }
        }
        for (Iterator<Shark> it = sharks.values().iterator(); it.hasNext(); ) {
            Shark sh = it.next();
            if (seen.contains(sh.id)) continue;
            it.remove();
            if (sh.dead || !sh.owned) continue;
            String why = null;
            if (teleported) why = "you teleported";
            else if (sh.ticksSeen < MIN_TICKS) why = "it was only seen " + sh.ticksSeen + " ticks";
            else if (sh.hp < 0 || sh.maxHp <= 0) why = "its health was never read";
            else if (sh.hp > sh.maxHp * VANISH_HP_FRACTION) why = "it was still at " + Math.round(100 * sh.hp / sh.maxHp) + "%";
            else if (sh.distSq > VANISH_RADIUS * VANISH_RADIUS) why = "it was " + Math.round(Math.sqrt(sh.distSq)) + " blocks away";
            if (why != null) {
                log.note("§8Fishing: your Great White Shark's nametag went - not a kill: " + why);
                continue;
            }
            sh.dead = true;
            died(sh, now, "its nametag went at " + Math.round(100 * sh.hp / sh.maxHp) + "% health");
        }
    }

    /** Ours if it first appeared close by around a catch line. */
    private void claim(Shark sh) {
        if (sh.owned || sh.dead) return;
        for (Iterator<Long> it = catches.iterator(); it.hasNext(); ) {
            long c = it.next();
            if (sh.firstSeen < c - CATCH_BEFORE_MS || sh.firstSeen > c + CATCH_AFTER_MS) continue;
            if (sh.distSq > CATCH_REACH * CATCH_REACH) continue;
            it.remove();
            sh.owned = true;
            log.note("§7Fishing: claimed your Great White Shark (\"" + sh.name + "\")");
            return;
        }
    }

    private void died(Shark sh, long now, String how) {
        record("died", sh);
        if (!sh.owned) {
            log.note("§8Fishing: a Great White Shark died, not yours - " + how);
            return;
        }
        if (lastDropAt >= 0 && now - lastDropAt <= DROP_BEFORE_MS) {
            log.note("§7Fishing: your Great White Shark died - " + how + "; its drop already showed");
            return;
        }
        log.note("§7Fishing: your Great White Shark died - " + how + "; waiting for a drop line");
        pending.addLast(now);
    }

    private static void record(String event, Shark sh) {
        JsonObject o = new JsonObject();
        o.addProperty("event", event);
        o.addProperty("owned", sh.owned);
        o.addProperty("name", sh.name);
        o.addProperty("distance", Math.round(Math.sqrt(sh.distSq)));
        DropRecorder.record("fishing_mob", o);
    }
}
