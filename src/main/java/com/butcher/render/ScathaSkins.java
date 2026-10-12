package com.butcher.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.butcher.ButcherClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Gets hold of the Scatha's real texture.
 *
 * <h2>Why it has to be captured rather than shipped</h2>
 *
 * Crystal Hollows worms are not a custom entity: each segment is an armour stand wearing a
 * player head, and that head's skin <em>is</em> the worm's texture. It is an ordinary
 * Mojang skin, owned by Hypixel, not something in the game's assets or in this mod's. Two
 * earlier attempts to hand-paint an approximation produced something that looked fine and
 * looked nothing like a Scatha, which is the whole reason this class exists.
 *
 * So the texture is taken from the animal itself. Whenever a tracked Scatha is nearby,
 * every armour stand around it is checked for a head item carrying a profile, and the skin
 * the game has already resolved for that profile is kept. That is by definition exact, it
 * costs no download of our own, and it stays correct if Hypixel ever reskins the mob.
 *
 * <h2>Telling the head from the body</h2>
 *
 * A worm has one head segment and several identical body segments, with different skins.
 * Rather than guess from position - the segments move, and the nametag stand is not
 * necessarily either of them - they are told apart by how often each is seen: the skin that
 * turns up many times per worm is the body, the one that turns up once is the head. A few
 * seconds near a single worm settles it, and it only gets more certain from there.
 *
 * <h2>Getting it sooner</h2>
 *
 * Nothing is known until the player has actually stood near a Scatha. Two things soften
 * that: whatever is learned is written to disk and reloaded on the next launch, and a PNG
 * dropped into {@code config/butcher/skins/} is used in preference to anything
 * captured, so a skin obtained another way can just be handed over.
 */
public final class ScathaSkins {

    /** Which part of the worm a skin belongs to. */
    public enum Part {
        HEAD("scatha_head_skin", "head.png"),
        BODY("scatha_body_skin", "body.png");

        final String textureName;
        final String overrideFile;

        Part(String textureName, String overrideFile) {
            this.textureName = textureName;
            this.overrideFile = overrideFile;
        }
    }

    /** How far from a tracked Scatha an armour stand can be and still count as part of it. */
    private static final double CAPTURE_RADIUS_SQ = 16.0 * 16.0;
    /** Ignore a candidate until it has been seen this often, so one stray head cannot win. */
    private static final int MIN_SIGHTINGS = 4;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("butcher");
    private static final Path CACHE = DIR.resolve("skins.json");
    private static final Path OVERRIDES = DIR.resolve("skins");

    /** Serialised form: the base64 "textures" property of each part, and the tally so far. */
    private static final class Saved {
        String head;
        String body;
        Map<String, Integer> tally = new LinkedHashMap<>();
    }

    private static Saved saved = new Saved();
    private static boolean loaded;

    private static final Map<Part, Identifier> resolved = new HashMap<>();
    private static final Map<Part, Boolean> fromOverride = new HashMap<>();

    private ScathaSkins() {}

    // =====================================================================
    // capture
    // =====================================================================

    /**
     * Offers one armour stand's head item as a candidate.
     *
     * @param distanceSq how far it is from the tracked Scatha, so unrelated heads standing
     *                   around the cave are not counted
     */
    public static boolean offer(ItemStack headItem, double distanceSq) {
        if (distanceSq > CAPTURE_RADIUS_SQ) return false;
        String textures = texturesProperty(headItem);
        if (textures == null) return false;

        load();
        int count = saved.tally.merge(textures, 1, Integer::sum);
        // Only reclassify on a change, so this is a no-op on almost every tick.
        if (count == MIN_SIGHTINGS || count % 64 == 0) {
            classify();
        }
        return true;
    }

    /** Whether any candidate skin has ever been seen - drives the "why not" diagnostics. */
    public static boolean anyCandidate() {
        load();
        return !saved.tally.isEmpty();
    }

    /** Reads the base64 "textures" property off a player-head item, or null if it is not one. */
    public static String texturesProperty(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResolvableProfile profile = stack.getComponents().get(DataComponents.PROFILE);
        if (profile == null) return null;
        GameProfile game = profile.partialProfile();
        if (game == null) return null;
        for (Property property : game.properties().get("textures")) {
            String value = property.value();
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    /**
     * Picks the head and body skins out of the tally: most-seen is the body (there are
     * several per worm), next is the head (there is one).
     */
    private static void classify() {
        String best = null, second = null;
        int bestCount = 0, secondCount = 0;
        for (Map.Entry<String, Integer> e : saved.tally.entrySet()) {
            if (e.getValue() < MIN_SIGHTINGS) continue;
            if (e.getValue() > bestCount) {
                second = best; secondCount = bestCount;
                best = e.getKey(); bestCount = e.getValue();
            } else if (e.getValue() > secondCount) {
                second = e.getKey(); secondCount = e.getValue();
            }
        }
        // A worm that turns out to be a single armour stand - or one whose head and body
        // share a skin - yields only one candidate. Use it for both rather than leaving the
        // head on the placeholder forever; if a distinct second skin does show up later,
        // the next classify() corrects it.
        if (best != null && second == null) {
            second = best;
            secondCount = bestCount;
        }

        boolean changed = false;
        if (best != null && !best.equals(saved.body)) {
            saved.body = best;
            resolved.remove(Part.BODY);
            changed = true;
            note("body", bestCount);
        }
        if (second != null && !second.equals(saved.head)) {
            saved.head = second;
            resolved.remove(Part.HEAD);
            changed = true;
            note("head", secondCount);
        }
        if (changed) save();
    }

    private static void note(String part, int count) {
        ButcherClient.decisions.note(
                "§bCaptured the Scatha's real " + part + " texture from a nearby worm §8(seen "
                        + count + " times); it will be reused from now on");
    }

    // =====================================================================
    // use
    // =====================================================================

    /** The texture to draw this part with, or null if nothing is known yet. */
    public static Identifier texture(Part part) {
        Identifier cached = resolved.get(part);
        if (cached != null) return cached;

        Identifier override = override(part);
        if (override != null) {
            resolved.put(part, override);
            fromOverride.put(part, true);
            return override;
        }

        load();
        String textures = part == Part.HEAD ? saved.head : saved.body;
        if (textures == null) textures = bundled(part);
        if (textures == null) return null;

        // Polled every call, never cached. See lookup().
        Supplier<PlayerSkin> lookup = lookup(textures);
        if (lookup == null) return null;
        try {
            PlayerSkin skin = lookup.get();
            return skin == null ? null : skin.body().texturePath();
        } catch (Throwable t) {
            return null;
        }
    }

    /** One skin lookup per profile, kept so it can be polled rather than re-created. */
    private static final Map<String, Supplier<PlayerSkin>> lookups = new HashMap<>();

    /**
     * The game's own lookup for a profile, created once and then asked every frame.
     *
     * <h3>Why this must not be resolved once and cached</h3>
     *
     * {@code SkinManager.createLookup} does not download anything on the spot. It hands
     * back a supplier that returns <em>the default skin</em> - Steve - until the real
     * texture has finished downloading, and the real one after that. Polling it is the
     * intended use; it is exactly what vanilla's entity renderers do every frame.
     *
     * This used to call {@code get()} once and cache the Identifier that came back. That
     * call almost always happens on the first frame the worm is drawn, which is long
     * before a fresh download completes - so the cutscene cached Steve and kept him for
     * the rest of the session. The Scatha is near-black with red eyes; what was actually
     * on screen was a pale tan player head, and no amount of re-capturing the skin could
     * have fixed it, because the capture was never the part that was wrong.
     */
    private static Supplier<PlayerSkin> lookup(String textures) {
        Supplier<PlayerSkin> existing = lookups.get(textures);
        if (existing != null) return existing;
        try {
            PropertyMap properties = new PropertyMap(com.google.common.collect.ImmutableMultimap.of(
                    "textures", new Property("textures", textures)));
            // The UUID only has to be stable, so the skin cache keys on it consistently.
            GameProfile profile = new GameProfile(
                    UUID.nameUUIDFromBytes(textures.getBytes()), "ScathaSegment", properties);
            Supplier<PlayerSkin> made = Minecraft.getInstance().getSkinManager().createLookup(profile, false);
            lookups.put(textures, made);
            return made;
        } catch (Throwable t) {
            ButcherClient.LOGGER.error("[Butcher] could not look up a Scatha skin", t);
            return null;
        }
    }

    // ---------------------------------------------------------------- the shipped default

    /** The two profiles shipped in the jar, read once. Null entries mean "none shipped". */
    private static Saved defaults;
    private static boolean defaultsRead;

    /**
     * The skin to use before this profile has ever seen a Scatha.
     *
     * <h3>Why ship one at all</h3>
     *
     * Capture is per-profile, because it is cached in that profile's config directory. So
     * every new instance started out drawing the hand-painted placeholder until the player
     * happened to stand next to a worm - and on a profile used only to test the cutscene,
     * that might be never. The mod looked broken in exactly the situation where someone is
     * most likely to be judging it.
     *
     * <h3>What is actually in the jar</h3>
     *
     * Not an image. {@code default_skins.json} holds the same base64 {@code GameProfile}
     * blobs the capture writes - a reference to a skin hosted on Mojang's own texture
     * server, which the game then downloads and caches through the usual player-skin path.
     * So this ships a pointer to Hypixel's texture, not a copy of it, which is also what
     * Scatha-Pro does.
     *
     * <h3>It is a floor, never a ceiling</h3>
     *
     * Consulted only when nothing has been captured, so a live worm always wins. That
     * matters because a bundled default goes stale the moment Hypixel changes the mob:
     * the capture path keeps working, quietly replaces this, and nobody has to ship a new
     * jar. {@code /butcher skins forget} drops the capture and lands back here rather than
     * on the placeholder.
     */
    private static String bundled(Part part) {
        if (!defaultsRead) {
            defaultsRead = true;
            try (InputStream in = ScathaSkins.class.getResourceAsStream(
                    "/assets/" + ButcherClient.MOD_ID + "/default_skins.json")) {
                if (in != null) {
                    defaults = GSON.fromJson(
                            new String(in.readAllBytes(), StandardCharsets.UTF_8), Saved.class);
                }
            } catch (Exception e) {
                // A missing or broken default is not worth a crash - it only means falling
                // back to the placeholder, which is what used to happen anyway.
                ButcherClient.LOGGER.warn(
                        "[Butcher] could not read the bundled Scatha skins", e);
            }
        }
        if (defaults == null) return null;
        return part == Part.HEAD ? defaults.head : defaults.body;
    }

    /** Whether this part is currently coming from the jar rather than from a real worm. */
    public static boolean isBundled(Part part) {
        load();
        String captured = part == Part.HEAD ? saved.head : saved.body;
        return captured == null && bundled(part) != null;
    }

    /**
     * The texture to draw with, falling back to the placeholder until the real one has been
     * captured. Never null, so the cutscene always has something to show.
     */
    public static Identifier textureOrPlaceholder(Part part) {
        Identifier real = texture(part);
        if (real != null) return real;
        return TextureBank.id(part == Part.HEAD ? PLACEHOLDER_HEAD : PLACEHOLDER_BODY);
    }

    /** Names the placeholders are registered under in {@link TextureBank}. */
    public static final String PLACEHOLDER_HEAD = "scatha_placeholder_head";
    public static final String PLACEHOLDER_BODY = "scatha_placeholder_body";

    /**
     * The TextureBank name to draw this part with: the captured skin if there is one,
     * otherwise the placeholder. Registers the captured texture under a stable name the
     * first time it is asked for, so callers can name a texture without caring which it is.
     */
    public static String textureName(Part part) {
        Identifier real = texture(part);
        if (real == null) return part == Part.HEAD ? PLACEHOLDER_HEAD : PLACEHOLDER_BODY;
        TextureBank.adopt(part.textureName, real);
        return part.textureName;
    }

    /** True once both parts can be drawn with the real thing. */
    public static boolean ready() {
        return texture(Part.HEAD) != null && texture(Part.BODY) != null;
    }

    /** Whether this part came from a PNG the player supplied rather than from the mob. */
    public static boolean isOverride(Part part) {
        return Boolean.TRUE.equals(fromOverride.get(part));
    }


    /** A PNG the player dropped in, if there is one. Always wins over what was captured. */
    private static Identifier override(Part part) {
        Path file = OVERRIDES.resolve(part.overrideFile);
        if (!Files.isRegularFile(file)) return null;
        String name = part.textureName + "_override";
        TextureBank.register(name, () -> {
            try {
                BufferedImage img = ImageIO.read(file.toFile());
                if (img == null) throw new IOException("not an image: " + file);
                return img;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return TextureBank.id(name);
    }

    // =====================================================================
    // persistence
    // =====================================================================

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(CACHE)) {
                Saved read = GSON.fromJson(Files.readString(CACHE), Saved.class);
                if (read != null) {
                    saved = read;
                    if (saved.tally == null) saved.tally = new LinkedHashMap<>();
                }
            }
        } catch (IOException | RuntimeException e) {
            ButcherClient.LOGGER.warn("[Butcher] could not read {}, starting fresh", CACHE, e);
            saved = new Saved();
        }
    }

    private static void save() {
        try {
            Files.createDirectories(DIR);
            Files.writeString(CACHE, GSON.toJson(saved));
        } catch (IOException e) {
            ButcherClient.LOGGER.warn("[Butcher] could not write {}", CACHE, e);
        }
    }

    /** Throws away everything learned, for "/butcher skins forget". */
    public static void forget() {
        saved = new Saved();
        resolved.clear();
        fromOverride.clear();
        loaded = true;
        save();
    }

    /** A one-line summary for "/butcher log" and the menu. */
    public static String status() {
        load();
        StringBuilder sb = new StringBuilder();
        for (Part part : Part.values()) {
            // "shipped with the mod" has to be distinguishable from "captured here", or
            // a profile that has never seen a worm looks identical to one that has - and
            // the whole point of the capture is that it stays right when Hypixel does not.
            String state = texture(part) == null
                    ? "§cnot captured yet"
                    : isOverride(part) ? "§afrom your PNG"
                    : isBundled(part) ? "§eshipped with the mod"
                    : "§acaptured from the mob";
            sb.append("§7").append(part.name().toLowerCase()).append(": ").append(state).append("  ");
        }
        sb.append("§8(").append(saved.tally.size()).append(" candidate skin(s) seen)");
        return sb.toString();
    }

    /** Where to drop a PNG to override a captured skin. */
    public static Path overrideDir() {
        return OVERRIDES;
    }
}
