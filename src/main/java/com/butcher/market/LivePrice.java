package com.butcher.market;

import com.butcher.ButcherClient;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An item's current lowest BIN, from the auction house, fetched in the background.
 *
 * <h2>Where it comes from</h2>
 *
 * Coflnet's public price API - {@code sky.coflnet.com/api/item/price/<ITEM_ID>/bin} - which
 * answers with the lowest and second-lowest BIN listing for an item. Checked by hand for the
 * Shattered Locket before this was written: about 505 million coins, with one on the auction
 * house, where the NPC would pay 5 million. That hundredfold gap is the whole reason to ask
 * the market rather than print the NPC price.
 *
 * Hypixel's own API cannot answer this directly - it only offers the full list of auctions,
 * dozens of pages, to be scanned for the cheapest BIN. Moulberry's lowest-BIN file, which
 * NEU reads, was down when this was written.
 *
 * <h2>It must never get in the way</h2>
 *
 * The request runs on the HTTP client's own thread and nothing ever waits on it. The Thaw
 * asks for the price when it opens, several seconds before it needs it, and simply reads
 * whatever is there when the moment comes: the live price if it arrived, the NPC price if it
 * did not. A slow or dead site costs a less impressive number, never a stall.
 *
 * Only the item's ID is sent - nothing about the player. Answers are kept for ten minutes, so
 * two Lockets in a row (in a dream) cost one request.
 */
public final class LivePrice {
    private LivePrice() {}

    /** What is known about an item's price. {@code live} is false for the NPC fallback. */
    public record Quote(long coins, boolean live) {}

    private record Cached(long coins, long at) {}

    private static final long FRESH_MS = 10 * 60 * 1000;
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    /** Starts a fetch for {@code itemId} unless a fresh answer is already held or on its way. */
    public static void request(String itemId) {
        if (ButcherClient.config != null && !ButcherClient.config.livePrices) return;
        Cached c = CACHE.get(itemId);
        if (c != null && System.currentTimeMillis() - c.at() < FRESH_MS) return;
        if (IN_FLIGHT.putIfAbsent(itemId, true) != null) return;

        HttpRequest req = HttpRequest.newBuilder(
                        URI.create("https://sky.coflnet.com/api/item/price/" + itemId + "/bin"))
                .timeout(Duration.ofSeconds(6))
                .header("User-Agent", "Butcher (Minecraft mod)")
                .GET().build();
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
            IN_FLIGHT.remove(itemId);
            if (err != null || res == null || res.statusCode() != 200) {
                ButcherClient.decisions.note("§8Could not fetch the lowest BIN for " + itemId + " - "
                        + (err != null ? err.getClass().getSimpleName() : "HTTP " + (res == null ? "?" : res.statusCode()))
                        + "; the NPC price will be shown");
                return;
            }
            try {
                JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                long lowest = o.get("lowest").getAsLong();
                if (lowest > 0) {
                    CACHE.put(itemId, new Cached(lowest, System.currentTimeMillis()));
                    ButcherClient.decisions.note("§aLowest BIN for " + itemId + ": §f"
                            + String.format("%,d", lowest) + " coins");
                }
            } catch (Exception e) {
                ButcherClient.decisions.note("§8The price site answered for " + itemId
                        + " but not in a shape we read; the NPC price will be shown");
            }
        });
    }

    /** The lowest BIN if one has arrived, or null. */
    public static Long cached(String itemId) {
        Cached c = CACHE.get(itemId);
        return c == null ? null : c.coins();
    }

    /** Whether a fetch for this item is still on its way. */
    public static boolean pending(String itemId) {
        return IN_FLIGHT.containsKey(itemId);
    }

    /** The best price known right now: the live one if it arrived, otherwise {@code npcCoins}. */
    public static Quote get(String itemId, long npcCoins) {
        Cached c = CACHE.get(itemId);
        return c != null ? new Quote(c.coins(), true) : new Quote(npcCoins, false);
    }
}
