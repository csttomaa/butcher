package com.butcher.render;

import com.butcher.ButcherClient;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * SkyBlock's head items, skinned for real: a set of base64 texture blobs bundled in the jar
 * (from the NEU item repository, the same place every SkyBlock mod gets them), turned into
 * skins the game downloads and caches itself.
 *
 * Split out of {@link VanguardItems} when Diana's drops needed the same thing. One instance
 * per bundled file; {@link #texture} is what a renderer asks for.
 *
 * <h2>Never Steve</h2>
 *
 * A skin lookup returns the default skin until its download finishes. {@link #texture}
 * answers null until the real one is there - downloaded skins live under "skins/", the
 * defaults under "entity/player/" - so a caller draws something else rather than a row of
 * Steve heads. {@link #prewarm} starts every lookup early, and the skins are in the local
 * cache after the first time anyway.
 */
public final class HeadSkins {

    public static final HeadSkins VANGUARD = new HeadSkins("/assets/butcher/vanguard_heads.json", "vg_head_");
    public static final HeadSkins DIANA = new HeadSkins("/assets/butcher/diana_heads.json", "diana_head_");
    /** Diana's creatures' own heads - from the NEU repo's bestiary - keyed by ChopTimeline.Creature name. */
    public static final HeadSkins CREATURES = new HeadSkins("/assets/butcher/creature_heads.json", "creature_head_");
    /** The Fishing Festival's Megalodon pet - NEU repo, MEGALODON;4 (both rarities share the skin). */
    public static final HeadSkins FISHING = new HeadSkins("/assets/butcher/fishing_heads.json", "fishing_head_");
    /** The slayers' head drops - NEU repo: both Archfiend Dice, the Flame Dye, the Scorched Power Crystal. */
    public static final HeadSkins SLAYER = new HeadSkins("/assets/butcher/slayer_heads.json", "slayer_head_");
    /** Lava fishing: the rare creatures' bestiary heads (by display name) and the head drops (by NEU ID). */
    public static final HeadSkins LAVA = new HeadSkins("/assets/butcher/lava_heads.json", "lava_head_");

    private final String resource, bankPrefix;
    private Map<String, String> blobs;
    private final Map<String, Supplier<PlayerSkin>> lookups = new HashMap<>();
    private final Map<String, String> bankNames = new HashMap<>();
    private boolean prewarmed;

    private HeadSkins(String resource, String bankPrefix) {
        this.resource = resource;
        this.bankPrefix = bankPrefix;
    }

    private Map<String, String> blobs() {
        if (blobs != null) return blobs;
        blobs = new HashMap<>();
        try (InputStream in = HeadSkins.class.getResourceAsStream(resource)) {
            if (in == null) {
                ButcherClient.LOGGER.warn("[Butcher] {} is missing from the jar", resource);
                return blobs;
            }
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String name : o.keySet()) blobs.put(name, o.get(name).getAsString());
        } catch (Exception e) {
            ButcherClient.LOGGER.warn("[Butcher] could not read {}", resource, e);
        }
        return blobs;
    }

    public boolean has(String name) {
        return blobs().containsKey(name);
    }

    /** The bundled texture blob (base64 "textures" property) for this head, or null. */
    public String blob(String name) {
        return blobs().get(name);
    }

    /** A bundled set by its short name ("vanguard", "diana", "creatures", "fishing", "slayer", "lava"), or null. */
    public static HeadSkins named(String set) {
        return switch (set) {
            case "vanguard" -> VANGUARD;
            case "diana" -> DIANA;
            case "creatures" -> CREATURES;
            case "fishing" -> FISHING;
            case "slayer" -> SLAYER;
            case "lava" -> LAVA;
            default -> null;
        };
    }

    /** Starts every head's skin lookup. Cheap, idempotent; call once there is a player. */
    public void prewarm() {
        if (prewarmed) return;
        prewarmed = true;
        for (String name : blobs().keySet()) lookup(name);
    }

    private Supplier<PlayerSkin> lookup(String name) {
        Supplier<PlayerSkin> existing = lookups.get(name);
        if (existing != null) return existing;
        String blob = blobs().get(name);
        if (blob == null) return null;
        try {
            PropertyMap props = new PropertyMap(com.google.common.collect.ImmutableMultimap.of(
                    "textures", new Property("textures", blob)));
            GameProfile profile = new GameProfile(
                    UUID.nameUUIDFromBytes(blob.getBytes(StandardCharsets.UTF_8)), "ButcherItem", props);
            Supplier<PlayerSkin> made = Minecraft.getInstance().getSkinManager().createLookup(profile, false);
            lookups.put(name, made);
            return made;
        } catch (Throwable t) {
            ButcherClient.LOGGER.warn("[Butcher] could not start a skin lookup for {}", name, t);
            return null;
        }
    }

    /**
     * The bank name of this item's real skin, or null if it is not a head here or not ready yet.
     *
     * Polled, never cached as "ready": the lookup is asked every time, because the whole
     * point of the supplier is that its answer changes when the download lands.
     */
    public String texture(String name) {
        Supplier<PlayerSkin> lookup = lookup(name);
        if (lookup == null) return null;
        try {
            PlayerSkin skin = lookup.get();
            if (skin == null) return null;
            Identifier id = skin.body().texturePath();
            if (!id.getPath().startsWith("skins/")) return null;
            String bank = bankNames.computeIfAbsent(name, n -> bankPrefix + Integer.toHexString(n.hashCode()));
            TextureBank.adopt(bank, id);
            return bank;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ pixels

    private final Map<String, java.awt.image.BufferedImage> images = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> fetching = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** When each head's last fetch failed: it is tried again only after {@link #RETRY_MS}, not on every tick. */
    private final Map<String, Long> failedAt = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long RETRY_MS = 120_000;

    /** Starts every head's pixel fetch (cached on disk after the first time). Cheap, idempotent. */
    public void prewarmPixels() {
        for (String name : blobs().keySet()) pixels(name);
    }

    /**
     * The skin itself as pixels (64 x 64), for what is rasterised on the CPU (Eruption's models), or null until
     * it is here. Fetched once on a daemon thread from the blob's own textures.minecraft.net URL and kept in
     * config/butcher/skin_cache, so it is instant after the first time. A failed fetch waits two minutes before
     * the next try - this is asked every tick, and offline it used to start a thread and a request each time.
     */
    public java.awt.image.BufferedImage pixels(String name) {
        java.awt.image.BufferedImage img = images.get(name);
        if (img != null) return img;
        String blob = blobs().get(name);
        if (blob == null) return null;
        Long failed = failedAt.get(name);
        if (failed != null && System.currentTimeMillis() - failed < RETRY_MS) return null;
        if (!fetching.add(name)) return null;
        Thread th = new Thread(() -> {
            try {
                String json = new String(java.util.Base64.getDecoder().decode(blob.trim()), StandardCharsets.UTF_8);
                String url = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures")
                        .getAsJsonObject("SKIN").get("url").getAsString();
                String hash = url.substring(url.lastIndexOf('/') + 1);
                if (!url.startsWith("http://textures.minecraft.net/texture/") && !url.startsWith("https://textures.minecraft.net/texture/")
                        || !hash.matches("[0-9a-f]+")) return;
                java.nio.file.Path cache = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                        .resolve("butcher").resolve("skin_cache").resolve(hash + ".png");
                java.awt.image.BufferedImage read = null;
                if (java.nio.file.Files.isRegularFile(cache)) read = javax.imageio.ImageIO.read(cache.toFile());
                if (read == null) {
                    java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                            .connectTimeout(java.time.Duration.ofSeconds(8)).build();
                    java.net.http.HttpResponse<byte[]> res = http.send(java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create("https://textures.minecraft.net/texture/" + hash))
                            .timeout(java.time.Duration.ofSeconds(15)).build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                    if (res.statusCode() != 200) return;
                    read = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(res.body()));
                    if (read == null) return;
                    java.nio.file.Files.createDirectories(cache.getParent());
                    java.nio.file.Files.write(cache, res.body());
                }
                // Old 64 x 32 skins: the head is the same, the lower half just is not there.
                java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = out.createGraphics();
                g.drawImage(read, 0, 0, 64, read.getHeight() * 64 / Math.max(1, read.getWidth()), null);
                g.dispose();
                images.put(name, out);
            } catch (Throwable t) {
                if (failedAt.get(name) == null) ButcherClient.LOGGER.warn("[Butcher] could not fetch the skin of {}: {}", name, t.toString());
            } finally {
                if (!images.containsKey(name)) {
                    failedAt.put(name, System.currentTimeMillis());
                    fetching.remove(name);
                }
            }
        }, "butcher-skin-" + name);
        th.setDaemon(true);
        th.start();
        return null;
    }
}
