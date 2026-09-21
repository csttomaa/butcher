package com.butcher.render;

import com.butcher.ButcherClient;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Lets a player replace any generated texture by editing the PNG, and never overwrites
 * an edit.
 *
 * <h2>The problem this solves</h2>
 *
 * CLAUDE.md's warning about {@code tools/} is the whole reason this exists: "edit the
 * script and re-run it rather than touching the PNG, or the next regeneration wipes the
 * change." That is a fine rule for whoever is writing the mod and a useless one for
 * whoever is playing it - they have no script, and the art is drawn in Java where they
 * cannot reach it at all.
 *
 * So every generated texture is written out to {@code config/butcher/textures/}
 * on first use. Edit one in any image editor, {@code /butcher reload}, and it is used
 * instead. Delete it and the generated one comes back.
 *
 * <h2>Telling an edit from our own output</h2>
 *
 * The obvious version - "if a file exists, use it" - breaks the moment the generated art
 * changes: our own file from a previous version is sitting there and is indistinguishable
 * from something the player drew, so the improvement never appears and there is no way to
 * get it back.
 *
 * So a manifest records the SHA-256 of every file this wrote. A file whose hash still
 * matches is ours and gets replaced; a file whose hash does not is the player's and is
 * left alone and loaded. That is the distinction SkyMaxx's {@code generated.properties}
 * makes, and it is the only part of this that needs to be careful.
 *
 * Failure here is never fatal. A read-only config directory, a corrupt PNG, a disk full -
 * all of them fall back to the generated image and log once. The art is not worth taking
 * a cutscene down for.
 */
public final class ArtOverrides {

    private static final Path DIR =
            FabricLoader.getInstance().getConfigDir().resolve("butcher").resolve("textures");
    private static final Path MANIFEST = DIR.resolve("generated.properties");

    /** name -> hash of the PNG this mod last wrote for it. */
    private static final Map<String, String> OURS = new HashMap<>();
    private static boolean loaded;
    private static boolean broken;

    private ArtOverrides() {}

    /**
     * The image to use for {@code name}: the player's, if they have edited it, otherwise
     * {@code generated} - which is also written out so there is something to edit.
     */
    public static BufferedImage resolve(String name, BufferedImage generated) {
        if (broken || !enabled()) return generated;
        try {
            loadManifest();
            Path file = DIR.resolve(name + ".png");
            byte[] onDisk = Files.isRegularFile(file) ? Files.readAllBytes(file) : null;

            if (onDisk != null && !isOurs(name, onDisk)) {
                BufferedImage custom = ImageIO.read(new java.io.ByteArrayInputStream(onDisk));
                if (custom != null) {
                    ButcherClient.LOGGER.info(
                            "[Butcher] using your own {}.png ({}x{})",
                            name, custom.getWidth(), custom.getHeight());
                    return custom;
                }
                // Unreadable: fall through and treat it as ours, so a truncated file
                // gets repaired rather than permanently shadowing the real texture.
                ButcherClient.LOGGER.warn(
                        "[Butcher] {}.png could not be read; regenerating it", name);
            }

            byte[] encoded = encode(generated);
            if (encoded != null && (onDisk == null || !sameBytes(onDisk, encoded))) {
                Files.createDirectories(DIR);
                Files.write(file, encoded);
                remember(name, encoded);
                saveManifest();
            } else if (encoded != null && !OURS.containsKey(name)) {
                // Already on disk byte-for-byte, but not in the manifest - the manifest
                // was deleted, or this is an upgrade. Claim it, or it would be mistaken
                // for the player's work forever.
                remember(name, encoded);
                saveManifest();
            }
        } catch (Throwable t) {
            broken = true;
            ButcherClient.LOGGER.warn(
                    "[Butcher] texture overrides are off: {}", t.toString());
        }
        return generated;
    }

    /** Where to put a replacement PNG. */
    public static Path directory() {
        return DIR;
    }

    /** Re-reads the manifest on the next resolve, for "/butcher reload". */
    public static void reload() {
        loaded = false;
        broken = false;
        OURS.clear();
    }

    private static boolean enabled() {
        return ButcherClient.config == null || ButcherClient.config.writeGeneratedArt;
    }

    private static boolean isOurs(String name, byte[] bytes) {
        String known = OURS.get(name);
        return known != null && known.equals(sha256(bytes));
    }

    private static void remember(String name, byte[] bytes) {
        OURS.put(name, sha256(bytes));
    }

    private static void loadManifest() throws IOException {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(MANIFEST)) return;
        Properties props = new Properties();
        try (var in = Files.newInputStream(MANIFEST)) {
            props.load(in);
        }
        for (String key : props.stringPropertyNames()) OURS.put(key, props.getProperty(key));
    }

    private static void saveManifest() throws IOException {
        Properties props = new Properties();
        OURS.forEach(props::setProperty);
        Files.createDirectories(DIR);
        try (var out = Files.newOutputStream(MANIFEST)) {
            props.store(out, "SHA-256 of every PNG Butcher wrote here."
                    + " A file whose hash still matches is one of ours and will be replaced"
                    + " when the mod's art changes; a file whose hash does not match is"
                    + " treated as yours and left alone. To go back to the built-in art,"
                    + " DELETE THE PNG - not the line here, which would have the opposite"
                    + " effect and make the mod treat that file as yours forever.");
        }
    }

    private static byte[] encode(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            return ImageIO.write(image, "PNG", out) ? out.toByteArray() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean sameBytes(byte[] a, byte[] b) {
        return java.util.Arrays.equals(a, b);
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                    .append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Exception e) {
            // SHA-256 is required of every JVM, so this cannot happen - but falling back
            // to a length tag keeps the caller simple and merely makes the check weaker.
            return "len:" + bytes.length;
        }
    }
}
