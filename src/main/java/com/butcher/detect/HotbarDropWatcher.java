package com.butcher.detect;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.butcher.ButcherClient;
import com.butcher.detect.DropDetector.DropResult;
import com.butcher.detect.DropDetector.Outcome;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Watches the player's inventory for the actual Scatha drop, the same way
 * Scatha-Pro's own ScathaDropsDetector does it (found by decompiling their
 * jar): there is no chat broadcast for a Scatha drop at all. Instead it
 * diffs item counts in the player's inventory - reading each item's real
 * SkyBlock data (the "id" and, for pets, "petInfo" fields inside its
 * CUSTOM_DATA component, i.e. its SkyBlock NBT) - looking for a new "PET"
 * item whose petInfo says "type":"SCATHA", or a new DWARVEN_OS_BLOCK_BRAN.
 *
 * <h2>Why the diff is not tied to the kill window</h2>
 *
 * The kill signal is a Scatha's armor stand going missing from the world
 * (see {@link ScathaWormTracker}), which can only ever be noticed on the
 * tick <em>after</em> it actually goes - the tracker compares this tick's
 * scan against last tick's. Hypixel, meanwhile, puts the loot in the
 * inventory as part of the same death handling, so the set-slot packet can
 * easily be applied a tick before the stand removal is spotted.
 *
 * So the drop can - and in practice does - show up in the inventory
 * <em>before</em> this class is told a Scatha died. Only diffing while the
 * kill window is open therefore loses the pet outright: the baseline is
 * refreshed every tick, so on the tick the pet lands (window still closed)
 * it is quietly absorbed into the baseline, and by the time the window
 * opens there is no longer any increase left to see. The window then closes
 * having found nothing and the drop is misreported as a gemstone.
 *
 * Scatha-Pro avoids this by keeping the two completely separate, and so do
 * we now: every tick the inventory is diffed and anything new is pushed
 * into {@link #cachedDrops} stamped with the time it was seen. A cached
 * drop stays claimable for {@link #KILL_WINDOW_MS}; a Scatha kill then
 * claims whatever is still in the cache. That covers a drop landing up to
 * a window early as well as up to a window late.
 *
 * <h2>Gemstones</h2>
 *
 * Scatha-Pro's own detector never identifies a gemstone drop directly
 * either (there's no separate "gemstone" item id to diff for, and if the
 * player has a gemstone sack the item skips their inventory entirely).
 * Instead we wait out the whole kill window, and if nothing was ever
 * claimed by the time it closes, we assume the drop was a gemstone - those
 * are the only three possible outcomes. Each kill resolves exactly once,
 * either the moment a pet/Dwarven O's is claimed or as a gemstone fallback
 * once the window fully elapses with neither.
 */
public final class HotbarDropWatcher {
    // public (not private) so ButcherClient's pre-kill hider can watch
    // the same window without duplicating the constant - ButcherClient
    // is in a different package (com.butcher, not .detect), so
    // package-private isn't enough.
    public static final long KILL_WINDOW_MS = 2000;
    private static final String ID_BLOCK_BRAN = "DWARVEN_OS_BLOCK_BRAN";
    private static final String ID_PET = "PET";
    private static final String PET_TYPE_SCATHA = "SCATHA";

    /** Best-to-worst, so the strongest new pet wins if several appear at once (as Scatha-Pro does). */
    private static final String[] RARITY_ORDER = {"LEGENDARY", "EPIC", "RARE", "UNCOMMON", "COMMON"};

    /**
     * How long a Dwarven O's sitting alone in the cache is held back before it is accepted as
     * the outcome. A kill can give an O's <em>and</em> a pet - Scatha-Pro fires those as two
     * separate events - but this mod shows one result, and the pet is the one worth showing.
     * The two normally land on the same tick, which {@link #claimCachedDrop} already resolves
     * in the pet's favour; this only covers the O's arriving a tick or two ahead of it.
     */
    private static final long EXTRA_DROP_GRACE_MS = 150;

    private static final Gson GSON = new Gson();

    private final DecisionLog log;

    public HotbarDropWatcher(DecisionLog log) {
        this.log = log;
    }

    /** A drop spotted in the inventory, waiting to be claimed by a kill (or to expire unclaimed). */
    private record CachedDrop(DropResult result, long seenAt) {}

    private Map<String, Integer> previousPetCounts = null; // rarity (as SkyBlock stores it, e.g. "LEGENDARY") -> count
    private int previousBlockBrans = -1;

    private final List<CachedDrop> cachedDrops = new ArrayList<>();

    // Tracks which kill we've already resolved an outcome for, so we fire
    // exactly once per kill (either on a claimed pet/block_bran, or as a
    // gemstone fallback once the window closes with neither).
    private long currentWindowKillTime = -1;
    private boolean currentWindowResolved = false;

    // Debug-only: log a full item-id dump once per kill window instead of
    // every tick, so we can see exactly what "id"/CUSTOM_DATA values are
    // actually present in the inventory right after a kill.
    private long lastLoggedKillTime = -1;

    /**
     * @param mayResolve whether an outcome is allowed to be announced right now. False while a
     *                   cutscene that already knows its outcome owns the screen - the scan
     *                   itself still has to run every tick to keep the baseline honest, but a
     *                   kill landing in that gap is dropped rather than played over the top.
     */
    public void tick(LocalPlayer player, ScathaWormTracker wormTracker, boolean mayResolve) {
        if (player == null) return;

        long now = System.currentTimeMillis();

        boolean killedRecently = wormTracker.killedRecently(KILL_WINDOW_MS);
        long lastKillTime = wormTracker.getLastKillTime();
        boolean freshKillWindow = killedRecently && lastKillTime != lastLoggedKillTime;
        StringBuilder debugDump = freshKillWindow ? new StringBuilder() : null;

        // ---- 1. scan the inventory -------------------------------------------
        Map<String, Integer> currentPetCounts = new HashMap<>();
        int currentBlockBrans = 0;

        Inventory inventory = player.getInventory();
        NonNullList<ItemStack> items = inventory.getNonEquipmentItems();
        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) continue;
            CompoundTag data = getSkyBlockData(stack);
            if (data == null) continue;

            String id = data.getString("id").orElse(null);
            if (debugDump != null && id != null) {
                if (debugDump.length() > 0) debugDump.append(", ");
                debugDump.append(id).append('x').append(stack.getCount());
            }
            if (ID_BLOCK_BRAN.equals(id)) {
                currentBlockBrans += stack.getCount();
            } else if (ID_PET.equals(id)) {
                String petInfoRaw = data.getString("petInfo").orElse(null);
                if (petInfoRaw != null) {
                    String[] typeAndTier = parsePetInfo(petInfoRaw);
                    if (typeAndTier != null && PET_TYPE_SCATHA.equals(typeAndTier[0])) {
                        currentPetCounts.merge(typeAndTier[1], stack.getCount(), Integer::sum);
                    }
                }
            }
        }

        if (freshKillWindow) {
            ButcherClient.LOGGER.info("[Butcher/debug] kill window opened - previousBlockBrans={} currentBlockBrans={} previousPetCounts={} currentPetCounts={} cachedDrops={} allItemIds=[{}]",
                    previousBlockBrans, currentBlockBrans, previousPetCounts, currentPetCounts, cachedDrops.size(), debugDump);
            lastLoggedKillTime = lastKillTime;
        }

        // ---- 2. diff against last tick and cache anything new ----------------
        // Unconditional, on purpose: see the class doc. The drop routinely lands
        // before the kill is noticed, so this must not be gated on the window.
        if (previousBlockBrans >= 0 && currentBlockBrans > previousBlockBrans) {
            cachedDrops.add(new CachedDrop(DropResult.blocked(), now));
            log.note("§eA Dwarven O's appeared in your inventory §8(" + previousBlockBrans + " → " + currentBlockBrans
                    + "); held for " + KILL_WINDOW_MS + "ms in case a Scatha claims it");
        }
        if (previousPetCounts != null) {
            String bestNewRarity = null;
            for (Map.Entry<String, Integer> entry : currentPetCounts.entrySet()) {
                if (entry.getValue() > previousPetCounts.getOrDefault(entry.getKey(), 0)
                        && rarityRank(entry.getKey()) > rarityRank(bestNewRarity)) {
                    bestNewRarity = entry.getKey();
                }
            }
            if (bestNewRarity != null) {
                cachedDrops.add(new CachedDrop(DropResult.pet(bestNewRarity), now));
                log.note("§eA " + bestNewRarity + " Scatha pet appeared in your inventory§8; held for "
                        + KILL_WINDOW_MS + "ms in case a Scatha claims it");
            }
        }

        previousBlockBrans = currentBlockBrans;
        previousPetCounts = currentPetCounts;

        // ---- 3. drop anything that went unclaimed for a whole window ----------
        cachedDrops.removeIf(drop -> now - drop.seenAt() >= KILL_WINDOW_MS);

        // ---- 4. resolve the kill ---------------------------------------------
        if (killedRecently && lastKillTime != currentWindowKillTime) {
            // a new kill/charm just started its window - reset resolution state for it
            currentWindowKillTime = lastKillTime;
            currentWindowResolved = false;
        }

        if (killedRecently && !currentWindowResolved) {
            if (!mayResolve) {
                // a cutscene that already knows its outcome owns the screen - this
                // kill is dropped outright rather than queued up behind it.
                currentWindowResolved = true;
                log.note("§cThis kill is being dropped §8- a cutscene that already knows its outcome owns the screen");
            } else {
                // Last tick this kill will get - take whatever is cached rather than let the
                // grace period below push a real result past the window and into the gemstone
                // fallback.
                boolean windowEnding = now - lastKillTime >= KILL_WINDOW_MS - EXTRA_DROP_GRACE_MS;
                DropResult claimed = claimCachedDrop(now, windowEnding);
                if (claimed != null) {
                    currentWindowResolved = true;
                    log.note("§aThe kill takes the " + describe(claimed) + " §8(seen "
                            + (now - lastKillTime) + "ms after the kill was detected)");
                    trigger(claimed);
                }
            }
        } else if (!killedRecently && lastKillTime > 0 && lastKillTime == currentWindowKillTime
                && !currentWindowResolved && previousPetCounts != null) {
            // the window for this kill just closed and nothing was ever claimed -
            // the only remaining possibility is a gemstone (likely swallowed
            // straight into a gemstone sack before it ever touched the
            // inventory), the same inference Scatha-Pro makes.
            currentWindowResolved = true;
            log.note("§6Kill window closed with no pet and no Dwarven O's seen §8- inferring a gemstone "
                    + "(the only remaining outcome; a gemstone sack swallows it before the inventory ever sees it)");
            if (mayResolve) trigger(DropResult.gemstone());
        }
    }

    /**
     * Takes the best drop currently sitting in the cache and empties it, or returns null if
     * there is nothing to claim yet. A pet beats a Dwarven O's: a kill yields one headline
     * result and the pet is always the one worth showing.
     *
     * @param forceClaim accept an O's straight away instead of holding it for
     *                   {@link #EXTRA_DROP_GRACE_MS} in case a pet is right behind it.
     */
    private DropResult claimCachedDrop(long now, boolean forceClaim) {
        if (cachedDrops.isEmpty()) return null;
        DropResult best = null;
        long oldestSeenAt = Long.MAX_VALUE;
        for (CachedDrop drop : cachedDrops) {
            if (best == null || (best.outcome != Outcome.PET && drop.result().outcome == Outcome.PET)) {
                best = drop.result();
            }
            oldestSeenAt = Math.min(oldestSeenAt, drop.seenAt());
        }
        if (!forceClaim && best.outcome != Outcome.PET && now - oldestSeenAt < EXTRA_DROP_GRACE_MS) {
            return null; // hold briefly - a pet may still be on its way in behind the O's
        }
        cachedDrops.clear();
        return best;
    }

    /** Position in {@link #RARITY_ORDER}, highest-first; -1 for null so anything real beats it. */
    private static int rarityRank(String rarity) {
        if (rarity == null) return -1;
        for (int i = 0; i < RARITY_ORDER.length; i++) {
            if (RARITY_ORDER[i].equals(rarity)) return RARITY_ORDER.length - i;
        }
        return 0; // a rarity we don't know about is still a real pet - just outranked by a known one
    }

    /** "LEGENDARY pet", "Dwarven O's", "gemstone" - for the decision log. */
    private static String describe(DropResult result) {
        return switch (result.outcome) {
            case PET -> result.rarity + " pet";
            case BLOCKED -> "Dwarven O's";
            case GEMSTONE -> "gemstone";
        };
    }

    private static void trigger(DropResult result) {
        ButcherClient.trigger(result);
    }

    private static CompoundTag getSkyBlockData(ItemStack stack) {
        CustomData customData = stack.getComponents().get(DataComponents.CUSTOM_DATA);
        return customData != null ? customData.copyTag() : null;
    }

    /** Returns {type, tier} both upper-cased, or null if the petInfo JSON couldn't be read. */
    private static String[] parsePetInfo(String petInfoRaw) {
        try {
            JsonObject obj = GSON.fromJson(petInfoRaw, JsonObject.class);
            if (obj == null) return null;
            String type = obj.has("type") ? obj.get("type").getAsString() : null;
            String tier = obj.has("tier") ? obj.get("tier").getAsString() : null;
            if (type == null || tier == null) return null;
            return new String[]{type.toUpperCase(), tier.toUpperCase()};
        } catch (JsonSyntaxException | ClassCastException | IllegalStateException e) {
            return null;
        }
    }
}
