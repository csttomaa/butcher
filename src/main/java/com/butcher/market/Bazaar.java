package com.butcher.market;

import com.butcher.ButcherClient;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Every bazaar product's instant prices, from Hypixel's own public bazaar endpoint.
 *
 * {@code api.hypixel.net/v2/skyblock/bazaar} needs no key and answers for all ~2,200
 * products at once, so one request prices a whole corpse. From each product only
 * {@code quick_status} is kept:
 *
 * <ul>
 *   <li>{@code sellPrice} - what an instant sell pays. What a drop is worth: it is what the
 *       player gets for it right now without waiting on an order.</li>
 *   <li>{@code buyPrice} - what an instant buy costs. What the Skeleton Key cost: it is what
 *       replacing it would take.</li>
 * </ul>
 *
 * Like {@link LivePrice}, it runs on the HTTP client's thread, is never waited on, and keeps
 * its answer for five minutes. The response is a few megabytes; it is parsed there too, and
 * only the two numbers per product are kept.
 */
public final class Bazaar {
    private Bazaar() {}

    private static final long FRESH_MS = 5 * 60 * 1000;
    /** Item ID to {instant sell, instant buy}. Replaced whole, never edited, so reads need no lock. */
    private static volatile Map<String, double[]> prices = Map.of();
    private static volatile long fetchedAt;
    private static final AtomicBoolean inFlight = new AtomicBoolean();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Starts a fetch unless a fresh answer is held or one is already on its way. */
    public static void request() {
        if (ButcherClient.config != null && !ButcherClient.config.livePrices) return;
        if (!prices.isEmpty() && System.currentTimeMillis() - fetchedAt < FRESH_MS) return;
        if (!inFlight.compareAndSet(false, true)) return;

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.hypixel.net/v2/skyblock/bazaar"))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Butcher (Minecraft mod)")
                .GET().build();
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
            try {
                if (err != null || res == null || res.statusCode() != 200) {
                    ButcherClient.decisions.note("§8Could not fetch bazaar prices - "
                            + (err != null ? err.getClass().getSimpleName() : "HTTP " + (res == null ? "?" : res.statusCode())));
                    return;
                }
                JsonObject products = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonObject("products");
                Map<String, double[]> read = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : products.entrySet()) {
                    JsonObject q = e.getValue().getAsJsonObject().getAsJsonObject("quick_status");
                    if (q == null) continue;
                    read.put(e.getKey(), new double[]{q.get("sellPrice").getAsDouble(), q.get("buyPrice").getAsDouble()});
                }
                prices = read;
                fetchedAt = System.currentTimeMillis();
                ButcherClient.decisions.note("§aBazaar prices fetched: §f" + read.size() + " products");
            } catch (Exception e) {
                ButcherClient.decisions.note("§8The bazaar answered but not in a shape we read");
            } finally {
                inFlight.set(false);
            }
        });
    }

    /** Whether a fetch is still on its way - so "no price yet" can be told from "no price". */
    public static boolean pending() {
        return inFlight.get();
    }

    /** What an instant sell pays for one, or null if the bazaar does not have it (or has not answered). */
    public static Double sell(String id) {
        double[] p = prices.get(id);
        return p == null || p[0] <= 0 ? null : p[0];
    }

    /** What an instant buy costs for one, or null likewise. */
    public static Double buy(String id) {
        double[] p = prices.get(id);
        return p == null || p[1] <= 0 ? null : p[1];
    }
}
