package com.butcher.detect;

import com.butcher.ButcherClient;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Writes down every real drop event, verbatim, to {@code config/butcher-drops.jsonl}.
 *
 * <h2>Why</h2>
 *
 * The rare drops are the whole point of this mod and happen almost never - a Scatha pet is
 * one kill in two thousand, a Minos Relic one Champion in several thousand. When one comes
 * out wrong there is no reproducing it. So every event is recorded as it happens, with the
 * exact chat lines around it and the detector's reasoning, and kept forever: append-only,
 * never pruned. The one real Relic becomes a permanent test case (see diana.md, section 10).
 *
 * <h2>What is recorded</h2>
 *
 * One JSON object per line, each with {@code time}, {@code kind} and {@code version}:
 *
 * <ul>
 *   <li>{@code scatha_kill} - the verdict, the chat and the decision log around it</li>
 *   <li>{@code vanguard_corpse} - the parsed haul, and the chat it was read from</li>
 *   <li>{@code diana_line} - any chat line that looks like Diana's event, captured before
 *       Diana is even detected, because her detection must be written against real strings
 *       rather than the ones we think Hypixel sends</li>
 * </ul>
 *
 * Test commands are never recorded. Writing happens on a thread of its own, so a slow disk
 * never costs a frame. The file is local and goes nowhere; it can hold other players' chat
 * from around an event, which is why it only ever keeps the lines near one.
 */
public final class DropRecorder {
    private DropRecorder() {}

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("butcher-drops.jsonl");
    private static final Gson GSON = new Gson();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "butcher-drop-recorder");
        t.setDaemon(true);
        return t;
    });

    /** Recent chat lines, oldest first: {when, text}. Only the last minute is kept. */
    private record ChatLine(long at, String text) {}
    private static final ArrayDeque<ChatLine> recent = new ArrayDeque<>();
    private static final long KEEP_MS = 60_000;

    /**
     * Anything that looks like it belongs to the Mythological Ritual: burrows, the creatures,
     * their signature drops, and the prefixes the spawn lines are said to use. Broad on
     * purpose - a line captured needlessly costs a few bytes, a line missed costs a real
     * string we then have to guess.
     */
    private static final Pattern DIANA = Pattern.compile(
            "(?i)griffin|burrow|mytholog|minos|inquisitor|sphinx|manticore|manti-core|harpy|gaia construct"
                    + "|minotaur|nymph|cretan|lynx|siamese|chimera|daedalus|crown of greed|shimmering wool"
                    + "|shelmet|antique remed|crochet tiger|washed-up souvenir|brain food|fateful stinger"
                    + "|ancient claw|hilt of revelations|minos relic|diana|danger!|yikes!|woah!|\\boi!|uh oh!"
                    + "|good grief!|loot share");

    /**
     * Slayer quests: boss names, the quest lines, the RNG meter and the louder drop tiers - kept so a
     * Slayer animation can be written against real lines (on hold since 2026-10-02).
     */
    private static final Pattern SLAYER = Pattern.compile(
            "(?i)slayer|rng meter|revenant|tarantula|broodfather|sven|packmaster|voidgloom|seraph"
                    + "|demonlord|riftstalker|bloodfiend|RARE DROP!|INSANE DROP|RNGESUS|boss slain"
                    + "|LOOT SHARE|glyph|nukekubi|hitshield|extraordinary|incarnate|quest complete");

    private static final Pattern FISHING = Pattern.compile(
            "(?i)shark|megalodon|great white|fishing festival|PET DROP"
                    // Lava fishing (2026-10-05, for "Eruption"): its five rare creatures, their drops, the dye,
                    // and the lines around a kill - charm, loot share, double hook, every DROP! line.
                    + "|jawbus|thunder|ragnarok|scuttler|plhlegblast|carmine|radioactive vial|burnt texts"
                    + "|brimstone handle|chain of the end times|severed pincer|flash i\\b|bobbin|squid boots"
                    + "|magma lord|crab stick|has spawned|has arrived|emerges|end times|waddles up|appeared"
                    + "|double hook|CHARM!|LOOT SHARE|DROP!|WOW!|RNGESUS|sea creature");

    private static boolean enabled() {
        return ButcherClient.config == null || ButcherClient.config.recordDrops;
    }

    /** Every incoming chat line passes through here, before anything decides to hide it. */
    public static void chatLine(String text) {
        long now = System.currentTimeMillis();
        synchronized (recent) {
            recent.addLast(new ChatLine(now, text));
            while (!recent.isEmpty() && now - recent.peekFirst().at() > KEEP_MS) recent.pollFirst();
        }
        if (enabled() && DIANA.matcher(text).find()) {
            JsonObject o = new JsonObject();
            o.addProperty("line", text);
            record("diana_line", o);
        }
        // The Fishing Festival's lines, for the same reason: SharkDrops was written before a real
        // Megalodon or tooth line had been seen.
        if (enabled() && SLAYER.matcher(text).find()) {
            JsonObject o = new JsonObject();
            o.addProperty("line", text);
            record("slayer_line", o);
        }
        if (enabled() && FISHING.matcher(text).find()) {
            JsonObject o = new JsonObject();
            o.addProperty("line", text);
            record("fishing_line", o);
        }
    }

    /** The chat lines from the last {@code ms} milliseconds, oldest first, verbatim. */
    public static JsonArray chatSince(long ms) {
        long from = System.currentTimeMillis() - ms;
        JsonArray out = new JsonArray();
        synchronized (recent) {
            for (Iterator<ChatLine> it = recent.iterator(); it.hasNext(); ) {
                ChatLine l = it.next();
                if (l.at() >= from) out.add(l.text());
            }
        }
        return out;
    }

    /** The detector's last {@code n} decisions, colour codes stripped. */
    public static JsonArray decisions(int n) {
        List<String> all = ButcherClient.decisions.recent();
        JsonArray out = new JsonArray();
        for (int i = Math.max(0, all.size() - n); i < all.size(); i++) out.add(DecisionLog.strip(all.get(i)));
        return out;
    }

    /** Appends one record. {@code fields} is copied, so the caller may keep using it. */
    public static void record(String kind, JsonObject fields) {
        if (!enabled()) return;
        JsonObject o = new JsonObject();
        o.addProperty("time", Instant.now().toString());
        o.addProperty("kind", kind);
        o.addProperty("version", FabricLoader.getInstance().getModContainer("butcher")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"));
        for (var e : fields.entrySet()) o.add(e.getKey(), e.getValue().deepCopy());
        String line = GSON.toJson(o) + "\n";
        WRITER.execute(() -> {
            try {
                Files.createDirectories(FILE.getParent());
                Files.writeString(FILE, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception e) {
                ButcherClient.LOGGER.warn("[Butcher] could not write {}", FILE, e);
            }
        });
    }

    /** Where the records are, for "/butcher records". */
    public static Path file() {
        return FILE;
    }

    /** How many records the file holds, and how many are Diana lines. Reads the file; call rarely. */
    public static int[] counts() {
        int all = 0, diana = 0;
        try {
            if (Files.exists(FILE)) {
                for (String l : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                    if (l.isBlank()) continue;
                    all++;
                    if (l.contains("\"kind\":\"diana_line\"")) diana++;
                }
            }
        } catch (Exception ignored) {
        }
        return new int[]{all, diana};
    }
}
