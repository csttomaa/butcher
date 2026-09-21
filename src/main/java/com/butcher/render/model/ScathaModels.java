package com.butcher.render.model;

import com.butcher.render.ScathaSkins;
import com.butcher.render.TextureBank;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The meshes the cutscene draws.
 *
 * Every piece is a vanilla player head, because that is literally what a Crystal Hollows
 * worm is made of - one armour stand per segment, each wearing a head whose skin is the
 * worm's texture. So the head and the body segments are the same mesh; only the skin
 * differs, and both skins come from the mob itself (see {@link ScathaSkins}).
 *
 * That also settles two things earlier versions got wrong by hand. Every segment is the
 * same size, because the real ones are. And the stepped silhouette along the worm's back
 * is not modelled at all - it is the hat layer of the player head, transparent except
 * where it sticks out.
 */
public final class ScathaModels {

    private ScathaModels() {}

    /**
     * One worm segment: a player head with its hat layer.
     *
     * Head and body are identical geometry. Which skin goes on it is the caller's choice,
     * via {@link ScathaSkins.Part}.
     */
    public static Mesh segment() {
        Mesh custom = mesh(ScathaSkins.Part.BODY);
        return custom != null ? custom : PlayerHeadMesh.head();
    }

    /** Kept as a separate name for readability at the call site; the same mesh. */
    public static Mesh head() {
        Mesh custom = mesh(ScathaSkins.Part.HEAD);
        return custom != null ? custom : PlayerHeadMesh.head();
    }

    /**
     * The texture bank name to draw this part with.
     *
     * A loaded model's own embedded skin wins, because its UV rects are pixel coordinates
     * into that image and no other - hand it the captured mob skin and every face would
     * sample the wrong part of the sheet. With no model, or a model that brought no
     * texture, this is exactly {@link ScathaSkins#textureName}.
     */
    public static String textureName(ScathaSkins.Part part) {
        Custom custom = custom(part);
        return custom != null && custom.texture() != null
                ? custom.texture() : ScathaSkins.textureName(part);
    }

    // ------------------------------------------------------- user-supplied models

    private static final Logger LOG = LoggerFactory.getLogger("butcher");

    /** Where a player drops their own model. Re-read after {@code /butcher reload}. */
    public static Path modelDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("butcher").resolve("models");
    }

    /** A model that was loaded from disk, and the bank name of the skin it brought. */
    private record Custom(Mesh mesh, String texture) {}

    /**
     * What was found on disk, keyed by part.
     *
     * A null value that is <em>present</em> means "looked, found nothing usable, stop
     * looking". Without that, a missing or broken model would be searched for and re-parsed
     * on every frame of the cutscene.
     */
    private static final java.util.EnumMap<ScathaSkins.Part, Custom> LOADED =
            new java.util.EnumMap<>(ScathaSkins.Part.class);

    /** Forgets what was loaded, so the next frame picks up an edited or newly added file. */
    public static void reload() {
        LOADED.clear();
    }

    private static Mesh mesh(ScathaSkins.Part part) {
        Custom custom = custom(part);
        return custom == null ? null : custom.mesh();
    }

    private static Custom custom(ScathaSkins.Part part) {
        if (LOADED.containsKey(part)) return LOADED.get(part);
        Custom loaded = read(part);
        LOADED.put(part, loaded);
        return loaded;
    }

    private static Custom read(ScathaSkins.Part part) {
        String stem = part == ScathaSkins.Part.HEAD ? "scatha_head" : "scatha_body";
        for (String extension : new String[]{".gltf", ".glb"}) {
            Path file = modelDir().resolve(stem + extension);
            if (!Files.isRegularFile(file)) continue;
            try {
                // posed = false: the animation places every segment itself, so a model
                // baked into a pose would fight ScathaBody rather than help it.
                // The Path form resolves a sibling .bin and texture folder.
                GltfLoader.Loaded loaded = GltfLoader.load(file, null, false);
                String texture = stem + "_model";
                TextureBank.register(texture, loaded::skin);
                LOG.info("[Butcher] loaded {} ({} faces)",
                        file.getFileName(), loaded.mesh().quads().size());
                return new Custom(loaded.mesh(), texture);
            } catch (Exception e) {
                // Loud, because a model that silently fails to load looks exactly like one
                // that was never put in the folder.
                LOG.warn("[Butcher] could not load {}: {}", file, e.getMessage());
                return null;
            }
        }
        return null;
    }

    /**
     * How far apart consecutive segments sit along the body.
     *
     * Comfortably more than a segment is wide, so there is real air at every joint. The
     * real mob is visibly articulated, and the gaps are where the cleaver has somewhere to
     * land - a solid tube gives it nothing to cut between.
     *
     * This only works because {@link com.butcher.render.scene.ScathaBody} spaces the
     * segments by arc length along the body curve rather than along X. Spaced along X, the
     * chord between two centres grows wherever the body bends, so the joints yawn open on
     * the curves and close on the straights and the worm reads as a string of loose
     * blocks. Along the curve the gap is the same everywhere, whatever the wave is doing.
     *
     * The multiplier was 1.09 and the segments still read as one overlapping mass on
     * screen, because the hat layer adds half a unit of overhang per side that the bare
     * HEAD size does not account for and the projection foreshortens the gap further.
     */
    public static float segmentPitch() {
        return (PlayerHeadMesh.HEAD + 2 * PlayerHeadMesh.HAT_INFLATE) * 1.26f;
    }
}
