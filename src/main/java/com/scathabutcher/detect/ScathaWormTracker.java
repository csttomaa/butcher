package com.scathabutcher.detect;

import com.scathabutcher.ScathaButcherClient;
import com.scathabutcher.render.ScathaSkins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Figures out "did the player just kill a Scatha" the same way Scatha-Pro's
 * own WormDetector/DetectedWorm classes do it (found by decompiling their
 * jar): Crystal Hollows worms are rendered as invisible ArmorStand entities
 * with a custom nametag showing a health bar. Scanning nearby armor stands
 * and checking that nametag text for " Scatha" (vs " Stoneworm" for a
 * regular worm) is how the game itself tells them apart - there is no chat
 * broadcast for this at all.
 *
 * <h2>A vanishing armor stand is not a kill</h2>
 *
 * The naive version of this - and what this class used to do - is to diff
 * the set of matching armor stands against last tick's and call anything
 * missing a kill. That fires on every way an entity can leave: walking out
 * of range, the chunk unloading, a server-side teleport, a relog, the worm
 * burrowing away. All of those opened a cutscene that then reported a
 * gemstone nobody got.
 *
 * SkyMaxx's SeaCreatureDetector solves this by spending most of its code
 * *refusing* kills, and {@link #whyNotDeath} is that idea ported across:
 * a vanished Scatha only counts once it has survived every check. The checks
 * are deliberately one-sided. Missing a real kill costs the player the
 * reveal on a drop they actually got, which is far worse than an occasional
 * spurious one, so each test only fires on strong evidence that the worm did
 * not die - never on a hunch.
 *
 * Every refusal is written to the {@link DecisionLog} with its reason, since
 * a kill that silently does not happen is otherwise indistinguishable from
 * the detector being broken.
 */
public final class ScathaWormTracker {
    private static final double SCAN_RADIUS = 40.0;
    // U+2764 HEAVY BLACK HEART - Hypixel renders worm health bars with this,
    // which is how Scatha-Pro itself confirms a nametag is a worm health bar
    // before checking which worm type it belongs to.
    private static final char HEALTH_BAR_MARKER = '❤';

    // Confirmed in-game: a Scatha's nametag looks like "[Lv10]  Scatha 10❤"
    // and counts down as it takes damage (e.g. "1❤" right before it dies) -
    // so the number directly before the heart is its current HP. Commas and
    // a decimal point are tolerated in case Hypixel ever formats it that way.
    private static final Pattern HP_PATTERN = Pattern.compile("([\\d,]+(?:\\.\\d+)?)\\s*" + HEALTH_BAR_MARKER);

    // ---- the refusal thresholds (see whyNotDeath) --------------------------
    /** Below this many ticks of continuous tracking, a vanish is noise rather than a kill. */
    private static final int MIN_SIGHTING_TICKS = 6;
    /** Entities stream in for a while after a world change; nothing that vanishes before this counts. */
    private static final int SETTLE_TICKS = 60;
    /** A player jump of more than 8 blocks in one tick is a teleport, and everything around them vanishes. */
    private static final double TELEPORT_DISTANCE_SQ = 64.0;
    /** More than this many Scathas vanishing on the same tick is the world unloading, not a killing spree. */
    private static final int BULK_VANISH = 2;
    /** ...as is the level's entity count falling by half in one tick. */
    private static final double BULK_UNLOAD_FRACTION = 0.5;
    /** Ignore an entity-count collapse until the level holds at least this many entities. */
    private static final int BULK_UNLOAD_FLOOR = 20;
    /** A Scatha whose nametag stopped matching this long ago is stale; its removal proves nothing. */
    private static final int NAME_LOST_GRACE_TICKS = 40;
    /** How long a still-loaded entity that no longer matches is kept around before being forgotten. */
    private static final int FORGET_NAMELESS_TICKS = 400;
    /**
     * A Scatha dies where you are fighting it. One that disappears further away than this went out
     * of range. Generous on purpose - the scan box reaches 40 blocks and worms move fast.
     */
    private static final double KILL_RADIUS = 28.0;
    /**
     * Inside this range the "it never took damage" test is skipped entirely.
     *
     * That test exists to catch a worm burrowing off and despawning, and it is the one check here
     * that could plausibly reject a real kill: the nametag is only sampled 20 times a second, so a
     * Scatha killed faster than that is seen at full health and then simply gone, with no damaged
     * frame in between. A geared player one-shots these. Burrowing away happens at a distance;
     * a kill happens in your face - so close up, a vanish is taken at face value.
     */
    private static final double POINT_BLANK_RADIUS = 12.0;

    private final DecisionLog log;

    private final Map<Integer, Sighting> sightings = new HashMap<>();
    private long lastKillTime = -1;
    private int lowestTrackedHp = Integer.MAX_VALUE;

    private long tickCount;
    private int ticksInWorld;
    private int lastEntityCount;
    private Vec3 lastPlayerPos;

    // Debug-only state: throttles the "raw nametag" dump so it doesn't spam
    // the log every tick, and makes sure we only log the tracked-count once
    // per change instead of every tick.
    /** Capture looks this far from a tracked Scatha; generous, since a worm is long. */
    private static final double CAPTURE_DEBUG_RADIUS_SQ = 16.0 * 16.0;

    // ---- ownership (d-owned) ----------------------------------------------
    /**
     * How long after a swing a health drop still counts as ours, in ticks.
     *
     * Generous on purpose. The nametag is a server-driven display sampled at 20Hz and
     * the swing is local, so the two arrive out of step by an unpredictable amount;
     * anything tight enough to be "precise" would mostly just miss. Half a second is
     * short enough that a neighbour's kill three seconds later is not credited to us.
     */
    private static final int SWING_GRACE_TICKS = 10;
    /**
     * How close a worm has to be for our swing to have plausibly reached it.
     *
     * Well beyond melee range, because the swing we can see is the arm animation, not a
     * hit - a miss looks identical - and Scathas are often hit as they surface at a
     * distance. It only has to be tight enough to not credit us with a kill happening
     * across the cavern.
     */
    private static final double OWN_HIT_RADIUS = 16.0;
    /** How close another player must be before we start asking who a kill belonged to. */
    private static final double RIVAL_RADIUS = 48.0;

    /** Last tick on which the local player's arm was swinging; -1 until they ever do. */
    private long lastSwingTick = -1;
    /** Whether anyone else was in range this tick - see {@link #whyNotDeath}. */
    private boolean othersNearby;

    /**
     * Whether the player swung recently enough for a health drop to be theirs.
     *
     * The {@code lastSwingTick < 0} guard is load-bearing, not defensive noise. This was
     * first written with the field starting at {@link Long#MIN_VALUE}, and
     * {@code tick - Long.MIN_VALUE} overflows to a large negative number - which is
     * "recent", so a player who had never swung at all was credited with every kill in
     * the cavern. Exactly backwards, and silent.
     */
    private boolean swungRecently(long tick) {
        return lastSwingTick >= 0 && tick - lastSwingTick <= SWING_GRACE_TICKS;
    }

    private long lastCaptureDumpMs = 0;
    private boolean capturePuzzleReported = false;
    private long lastRawDumpMs = 0;
    private int lastLoggedTrackedCount = -1;

    public ScathaWormTracker(DecisionLog log) {
        this.log = log;
    }

    public void tick(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            reset();
            return;
        }

        ticksInWorld++;
        long tick = ++tickCount;
        long now = System.currentTimeMillis();

        // Conditions that make EVERY vanish this tick untrustworthy, gathered
        // before the scan so each candidate death can be judged against them.
        Vec3 playerPos = player.position();
        boolean teleported = lastPlayerPos != null && playerPos.distanceToSqr(lastPlayerPos) > TELEPORT_DISTANCE_SQ;
        lastPlayerPos = playerPos;
        int entityCount = level.getEntityCount();
        boolean bulkUnload = lastEntityCount > BULK_UNLOAD_FLOOR && entityCount < lastEntityCount * BULK_UNLOAD_FRACTION;
        lastEntityCount = entityCount;

        // ---- 0. who is around, and are we swinging --------------------------
        // Both sampled before the scan, because the scan's ownership test reads them.
        if (player.swinging) lastSwingTick = tick;

        othersNearby = false;
        for (net.minecraft.client.player.AbstractClientPlayer other : level.players()) {
            if (other == player) continue;
            // Hypixel's lobbies are full of NPC-ish armour-stand entities, but a real
            // rival is an actual player entity, so the plain list is the right one.
            if (other.distanceToSqr(player) <= RIVAL_RADIUS * RIVAL_RADIUS) {
                othersNearby = true;
                break;
            }
        }

        // ---- 1. scan nearby armor stands ------------------------------------
        AABB scanBox = AABB.ofSize(playerPos, SCAN_RADIUS * 2, SCAN_RADIUS * 2, SCAN_RADIUS * 2);
        List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, scanBox);
        Set<Integer> matched = new HashSet<>();
        int currentLowestHp = Integer.MAX_VALUE;

        debugDump(stands, now);

        for (ArmorStand stand : stands) {
            if (!isScatha(stand)) continue;

            Sighting sighting = sightings.computeIfAbsent(stand.getId(), id -> {
                Sighting fresh = new Sighting();
                fresh.id = id;
                return fresh;
            });
            sighting.ticksSeen++;
            sighting.lastMatchTick = tick;
            sighting.lastDistanceSq = stand.distanceToSqr(player);

            Integer hp = extractHp(stand.getCustomName().getString());
            if (hp != null) {
                if (hp > sighting.maxHp) sighting.maxHp = hp;
                if (sighting.lastHp >= 0 && hp < sighting.lastHp) {
                    sighting.tookDamage = true;
                    // This is the whole ownership test: health went down, and we had
                    // swung recently enough for it to plausibly be us. See swungRecently.
                    if (swungRecently(tick) && sighting.lastDistanceSq <= OWN_HIT_RADIUS * OWN_HIT_RADIUS) {
                        if (!sighting.owned) {
                            log.note("§7Claimed a Scatha - it lost health while you were swinging");
                        }
                        sighting.owned = true;
                    }
                }
                sighting.lastHp = hp;
                if (hp < currentLowestHp) currentLowestHp = hp;
            }
            matched.add(stand.getId());
        }

        lowestTrackedHp = currentLowestHp;

        // While a Scatha is in front of us, grab its real texture. A worm segment is an
        // armour stand wearing a player head, so the skin on those heads IS the mob's
        // texture - see ScathaSkins for why that is the only way to get an exact one.
        captureSkins(level, stands, matched, now);

        if (matched.size() != lastLoggedTrackedCount) {
            ScathaButcherClient.LOGGER.info("[ScathaButcher/debug] tracked Scatha armor stands: {}", matched.size());
            lastLoggedTrackedCount = matched.size();
        }

        // ---- 2. separate "really gone" from "merely stopped matching" --------
        // An entity that is still loaded but whose nametag no longer reads as a
        // Scatha has not died - Hypixel rewrites these nametags constantly. Only
        // an entity the client has actually dropped is a candidate.
        List<Sighting> gone = new ArrayList<>();
        Iterator<Map.Entry<Integer, Sighting>> it = sightings.entrySet().iterator();
        while (it.hasNext()) {
            Sighting sighting = it.next().getValue();
            if (matched.contains(sighting.id)) continue;

            Entity still = level.getEntity(sighting.id);
            if (still != null) {
                sighting.lastDistanceSq = still.distanceToSqr(player);
                if (tick - sighting.lastMatchTick > FORGET_NAMELESS_TICKS) {
                    it.remove();
                }
            } else {
                it.remove();
                gone.add(sighting);
            }
        }

        if (gone.isEmpty()) return;

        // ---- 3. judge each candidate ----------------------------------------
        boolean killed = false;
        for (Sighting sighting : gone) {
            String refused = whyNotDeath(sighting, gone.size(), teleported, bulkUnload, tick);
            if (refused == null) {
                killed = true;
                log.note("§aA Scatha died" + describe(sighting));
                com.scathabutcher.config.ButcherStats.get().onKill();
            } else if (worthMentioning(sighting)) {
                // Only narrate near misses. A worm quietly leaving render range on
                // the far side of the cave is not a decision anyone needs to read.
                log.note("§8A Scatha went out of sight, not counted: " + refused + describe(sighting));
            }
        }

        if (killed) {
            lastKillTime = now;
        }
    }

    /**
     * Offers the head item of every armour stand standing close to a tracked Scatha, so the
     * worm's own skin can be lifted off it. Cheap: a distance check and a component lookup,
     * and only while a Scatha is actually being tracked.
     */
    private void captureSkins(ClientLevel level, List<ArmorStand> stands, Set<Integer> matched, long now) {
        if (matched.isEmpty()) return;

        int withItem = 0, withProfile = 0, considered = 0;

        for (ArmorStand stand : stands) {
            // Distance to the nearest Scatha nametag. A stand that CARRIES the nametag is
            // its own zero - the first version of this skipped those outright, assuming the
            // nametag always sits on a separate marker stand. It does not: Hypixel hangs the
            // health bar on the head segment, so skipping them skipped the one head whose
            // skin was actually wanted, and nothing was ever captured.
            double nearest = matched.contains(stand.getId()) ? 0.0 : Double.MAX_VALUE;
            if (nearest != 0.0) {
                for (ArmorStand other : stands) {
                    if (!matched.contains(other.getId())) continue;
                    nearest = Math.min(nearest, stand.distanceToSqr(other));
                }
            }
            if (nearest > CAPTURE_DEBUG_RADIUS_SQ) continue;

            considered++;
            ItemStack head = stand.getItemBySlot(EquipmentSlot.HEAD);
            if (head != null && !head.isEmpty()) withItem++;
            if (ScathaSkins.offer(head, nearest)) withProfile++;
        }

        // Nothing has ever been captured and we are standing next to a Scatha: say what is
        // actually out there, rather than leaving "no candidates seen yet" unexplained.
        if (!ScathaSkins.anyCandidate() && now - lastCaptureDumpMs > 4000) {
            lastCaptureDumpMs = now;
            String types = nearbyTypes(level, stands, matched);
            ScathaButcherClient.LOGGER.info(
                    "[ScathaButcher/debug] skin capture: {} armor stand(s) near a Scatha, {} wearing something, {} with a profile. Nearby entity types: {}",
                    considered, withItem, withProfile, types);
            // Once into the decision log too, so it can be read with /butcher log rather
            // than by digging through latest.log. Once only - this repeats every 4s.
            if (!capturePuzzleReported) {
                capturePuzzleReported = true;
                log.note("§cNo Scatha skin found on any nearby armor stand §8- " + considered
                        + " in range, " + withItem + " wearing something, " + withProfile + " with a profile. Nearby: " + types);
            }
        }
    }

    /**
     * Distinct entity types within capture range of a tracked Scatha.
     *
     * Only used when capture has found nothing. If the worm segments turn out not to be
     * armor stands at all, this is the line that says so - the scan only ever looks at
     * ArmorStand, so anything else is invisible to it.
     */
    private static String nearbyTypes(ClientLevel level, List<ArmorStand> stands, Set<Integer> matched) {
        ArmorStand anchor = null;
        for (ArmorStand stand : stands) {
            if (matched.contains(stand.getId())) { anchor = stand; break; }
        }
        if (anchor == null) return "(none)";

        double r = Math.sqrt(CAPTURE_DEBUG_RADIUS_SQ);
        AABB box = AABB.ofSize(anchor.position(), r * 2, r * 2, r * 2);
        Map<String, Integer> counts = new java.util.TreeMap<>();
        for (Entity e : level.getEntities(anchor, box, e -> true)) {
            counts.merge(e.getType().toString(), 1, Integer::sum);
        }
        return counts.isEmpty() ? "(none)" : counts.toString();
    }

    /**
     * Returns null if this vanish is a death, or a short human-readable reason why it is not.
     *
     * Ordered cheapest and most decisive first. Every branch describes a way an entity leaves
     * the client that has nothing to do with dying.
     */
    private String whyNotDeath(Sighting sighting, int vanishedThisTick, boolean teleported, boolean bulkUnload, long tick) {
        if (teleported) {
            return "you moved too far in one tick";
        }
        if (bulkUnload || vanishedThisTick > BULK_VANISH) {
            return "the world unloaded around it";
        }
        if (ticksInWorld < SETTLE_TICKS) {
            return "the world was still loading";
        }
        if (sighting.ticksSeen < MIN_SIGHTING_TICKS) {
            return "only tracked for " + sighting.ticksSeen + " ticks";
        }
        if (tick - sighting.lastMatchTick > NAME_LOST_GRACE_TICKS) {
            return "its nametag had stopped matching long before";
        }
        if (sighting.lastDistanceSq > KILL_RADIUS * KILL_RADIUS) {
            return String.format(Locale.ROOT, "%.0f blocks away", Math.sqrt(sighting.lastDistanceSq));
        }
        // Somebody else's kill.
        //
        // Only asked when there IS somebody else. Crystal Hollows is a shared server and
        // another player mining the next tunnel over used to fire our cutscene for their
        // Scatha; but alone in a tunnel there is nobody whose kill it could be, and
        // demanding proof would only throw away real ones. The checks here are
        // deliberately one-sided - missing a kill costs the player a reveal they earned -
        // so this refuses only when it can point at the player it probably belongs to.
        if (!sighting.owned && othersNearby) {
            return "another player was nearby and you were never seen hitting it";
        }
        // The one health test worth making. We cannot compute a true fraction - the
        // nametag carries current HP only, so "max" is just the highest we happened
        // to see - but a worm that vanished without ever losing a single point was
        // never being fought. Skipped at point-blank range, where a kill too fast for
        // the 20Hz nametag sample would otherwise be thrown away (see the constant).
        if (sighting.maxHp > 0 && !sighting.tookDamage
                && sighting.lastDistanceSq > POINT_BLANK_RADIUS * POINT_BLANK_RADIUS) {
            return "it never took damage (still at " + sighting.lastHp + "❤)";
        }
        return null;
    }

    /** Near misses are worth narrating; a worm leaving render range across the cave is not. */
    private static boolean worthMentioning(Sighting sighting) {
        double reach = KILL_RADIUS * 1.5;
        return sighting.ticksSeen >= MIN_SIGHTING_TICKS && sighting.lastDistanceSq <= reach * reach;
    }

    private static String describe(Sighting sighting) {
        return String.format(Locale.ROOT, " §8(tracked %dt, %.0f blocks, last %s❤%s)",
                sighting.ticksSeen,
                Math.sqrt(sighting.lastDistanceSq),
                sighting.lastHp < 0 ? "?" : Integer.toString(sighting.lastHp),
                sighting.tookDamage ? (sighting.owned ? ", yours" : ", was damaged") : "");
    }

    private void debugDump(List<ArmorStand> stands, long now) {
        // Every ~3s, dump the raw nametag text of any nearby armor stand that even
        // loosely looks worm-related, so we can confirm what Hypixel is actually
        // sending (heart char present? "Scatha" spelled how we expect?).
        if (now - lastRawDumpMs <= 3000) return;
        lastRawDumpMs = now;

        StringBuilder dump = new StringBuilder();
        int total = 0;
        for (ArmorStand stand : stands) {
            Component nameComponent = stand.getCustomName();
            if (nameComponent == null) continue;
            String raw = nameComponent.getString();
            String lower = raw.toLowerCase(Locale.ROOT);
            if (lower.contains("scatha") || lower.contains("worm")) {
                if (total > 0) dump.append(" | ");
                dump.append('"').append(raw).append('"');
                total++;
            }
        }
        if (total > 0) {
            ScathaButcherClient.LOGGER.info("[ScathaButcher/debug] {} nearby worm-ish armor stand nametag(s): {}", total, dump);
        } else {
            ScathaButcherClient.LOGGER.info("[ScathaButcher/debug] no nearby armor stands with a worm/scatha nametag found ({} armor stands scanned in range)", stands.size());
        }
    }

    private static boolean isScatha(ArmorStand stand) {
        Component nameComponent = stand.getCustomName();
        if (nameComponent == null) return false;
        String name = nameComponent.getString();
        if (name.indexOf(HEALTH_BAR_MARKER) < 0) return false; // not a worm health-bar nametag at all
        return name.contains(" Scatha");
    }

    private static Integer extractHp(String name) {
        Matcher m = HP_PATTERN.matcher(name);
        if (!m.find()) return null;
        try {
            return (int) Math.round(Double.parseDouble(m.group(1).replace(",", "")));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Epoch millis of the last confirmed Scatha death, or -1 if none yet. */
    public long getLastKillTime() {
        return lastKillTime;
    }

    public boolean killedRecently(long windowMs) {
        return lastKillTime > 0 && (System.currentTimeMillis() - lastKillTime) < windowMs;
    }

    /** True if any currently-tracked, still-alive Scatha nearby is at or below {@code hpThreshold} HP. */
    public boolean lowHealthScathaNearby(int hpThreshold) {
        return lowestTrackedHp <= hpThreshold;
    }

    /**
     * Forgets everything. Called when there is no world, so that re-entering one starts the
     * settle counter again rather than treating the whole previous world as having just died.
     */
    public void reset() {
        sightings.clear();
        lowestTrackedHp = Integer.MAX_VALUE;
        ticksInWorld = 0;
        lastEntityCount = 0;
        lastPlayerPos = null;
        lastLoggedTrackedCount = -1;
    }

    private static final class Sighting {
        int id;
        int ticksSeen;
        long lastMatchTick;
        double lastDistanceSq;
        int lastHp = -1;
        int maxHp = -1;
        boolean tookDamage;
        /** True once this worm was seen losing health while we were swinging at it. */
        boolean owned;
    }
}
