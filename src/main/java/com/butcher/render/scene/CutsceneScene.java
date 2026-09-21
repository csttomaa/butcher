package com.butcher.render.scene;

import com.butcher.render.model.Mesh;
import com.butcher.render.model.PlayerHeadMesh;
import com.butcher.render.model.ScathaModels;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the cleaver cutscene puts in front of the camera, as a plain list of placed
 * meshes.
 *
 * <h2>Why it is a description rather than draw calls</h2>
 *
 * Nothing here touches Minecraft. {@link #build} returns {@link Item}s - a mesh, a texture
 * name, a tint and a position - and it is the caller's job to hand them to
 * {@link com.butcher.render.MeshRenderer}. That keeps the scene renderable offline:
 * the same list can be rasterised to a PNG with AWT and looked at, which is the only way
 * this has been checked at all, since a Scatha kill cannot be summoned to order.
 *
 * <h2>The cave is geometry, not a backdrop</h2>
 *
 * The old cutscene drew one painted `cave_wide.png` behind everything. That is fine for a
 * fixed camera and falls apart the moment the camera moves - a flat image behind a turning
 * subject is the single thing that most gives away a fake 3D scene. So the cavern is built
 * from the same boxes as everything else: floor, back wall, side walls and a few ledges.
 * It parallaxes for free, and the gem clusters can sit *in* it rather than being painted
 * on top of it.
 */
public final class CutsceneScene {

    /**
     * One placed mesh.
     *
     * {@code yaw} is per-item, which is what lets each segment face along the body curve.
     * {@code tint} is per-item too, so a single segment can fade or flash on its own -
     * SkyMaxx needs a {@code tier} id threaded through the depth sort for that, because it
     * projects every segment in one batch; queueing them separately gets it for free.
     */
    public record Item(Mesh mesh, String texture, int tint,
                       float x, float y, float z, float yaw, boolean outlined) {}

    // ---- world layout, in model units ------------------------------------
    /**
     * The worm lies along X centred on the origin; everything else is placed around it.
     *
     * The hall is sized to the worm rather than the other way round. The first pass built a
     * 220-unit cavern around a 46-unit worm and the subject became a sliver in the middle
     * of an empty room - the camera wants to be *inside* the cave, with the walls running
     * off the edges of the frame, not looking at a box from outside it.
     */
    public static final float FLOOR_Y = -16f;
    /** Top surface of the floor slab - what everything stands on. */
    public static final float GROUND_Y = FLOOR_Y + 6f;

    // Lazily built, then shared - the meshes never change, only where they are placed.
    private static Mesh segment;
    private static Mesh cleaver;

    /**
     * Drops the cached meshes, so /butcher reload picks up a model file that has just
     * been added or edited. Without this the first build of each mesh would be the only
     * one for the rest of the session.
     */
    public static void reload() {
        segment = null;
        cleaver = null;
        head = null;
    }

    private CutsceneScene() {}

    /**
     * Cleaver size relative to the rest of the scene.
     *
     * The mesh is built at a comfortable size to author, not at the size it should appear:
     * at 1:1 the blade is as long as three worm segments and reads as a guillotine. This
     * is the one number that decides whether it looks like a butcher's tool or a prop.
     */
    public static final float CLEAVER_SCALE = 0.52f;

    public static Mesh cleaverMesh() {
        if (cleaver == null) cleaver = com.butcher.render.model.CleaverMesh.build();
        return cleaver;
    }

    /**
     * The whole scene for one frame.
     *
     * @param partsRemoved how many segments the cleaver has already taken off the front
     * @param parts        total segments including the head
     * @param hideHead     true once the head has lifted away for the reveal
     * @param headTexture  TextureBank name for the head segment
     * @param bodyTexture  TextureBank name for a body segment
     *
     * The two texture names are passed in rather than looked up here, so this class stays
     * free of the skin machinery - and therefore of Fabric - and can be rendered offline.
     */
    public static List<Item> build(int partsRemoved, int parts, boolean hideHead,
                                   String headTexture, String bodyTexture,
                                   float seconds, float thrash) {
        ensureMeshes();
        List<Item> out = new ArrayList<>(16);
        worm(out, partsRemoved, parts, hideHead, headTexture, bodyTexture, seconds, thrash);
        return out;
    }

    // =====================================================================
    // the worm
    // =====================================================================

    private static void worm(List<Item> out, int partsRemoved, int parts, boolean hideHead,
                             String headTexture, String bodyTexture,
                             float seconds, float thrash) {
        float pitch = ScathaModels.segmentPitch();
        // Half a segment above the floor, so it rests on the block rather than through it.
        float rest = (PlayerHeadMesh.HEAD + 2 * PlayerHeadMesh.HAT_INFLATE) / 2f;

        ScathaBody.Pose[] body = ScathaBody.poses(parts, pitch, seconds, thrash);
        for (int i = partsRemoved; i < parts; i++) {
            boolean isHead = i == parts - 1;
            if (isHead && hideHead) continue;

            ScathaBody.Pose p = body[i];
            out.add(new Item(segment, isHead ? headTexture : bodyTexture, 0xFFFFFFFF,
                    p.x(), rest + p.y(), p.z(), p.yaw(), true));
        }
    }

    // =====================================================================
    // meshes
    // =====================================================================

    private static void ensureMeshes() {
        if (segment == null) segment = ScathaModels.segment();
    }

    /**
     * The shared worm-segment mesh.
     *
     * Public so the cutscene can draw a loose piece - the part arcing away after a chop -
     * from the SAME cached mesh as the body. It used to keep its own copy, which meant a
     * reload replaced one and not the other and the flying segment could be a different
     * shape from the worm it came off.
     */
    public static Mesh segmentMesh() {
        ensureMeshes();
        return segment;
    }

    /** The head mesh, cached, for the pet reveal. */
    private static Mesh head;

    public static Mesh headMesh() {
        if (head == null) head = ScathaModels.head();
        return head;
    }

    /** Reach of the cleaver, used to place its pivot clear of the worm. */
    public static float cleaverReach() {
        return com.butcher.render.model.CleaverMesh.REACH;
    }

    /** Where along X the cut for {@code partIndex} lands, in world units. */
    public static float partX(int partIndex, int parts) {
        return (partIndex - (parts - 1) / 2f) * ScathaModels.segmentPitch();
    }

    /** Distance between segments, in model units. */
    public static float pitch() {
        return ScathaModels.segmentPitch();
    }

    /** Half the worm's length, for framing the camera. */
    public static float wormHalfLength(int parts) {
        return ScathaModels.segmentPitch() * (parts - 1) / 2f + PlayerHeadMesh.HEAD;
    }
}
