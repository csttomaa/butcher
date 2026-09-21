package com.scathabutcher.render.model;

import java.util.Random;

/**
 * Gemstone crystals, as they grow in the Crystal Hollows: clusters of long angular shards
 * leaning out of the rock at different pitches.
 *
 * <h2>Shape</h2>
 *
 * In-game these are translucent slabs, blocky and axis-heavy. Copying that literally gives
 * a stack of cubes, so each shard here is a {@link BoxMesh#skewBox sheared} box instead -
 * long on one axis, slim on the other two, and leaning. Shearing rather than tapering is
 * forced by the renderer (a tapered side would be a trapezoid, and the affine blit only
 * makes parallelograms), but it costs nothing: what reads as "crystal" is the lean and the
 * varied lengths, not a point at the tip.
 *
 * A cluster is a few shards from one root, fanning out and getting shorter towards the
 * edges, plus a couple of stubs at the base so it grows out of the ground rather than
 * balancing on it.
 *
 * <h2>Colour</h2>
 *
 * The mesh carries no colour at all. One greyish facet texture is drawn for every gem type
 * and the tint passed to {@link com.scathabutcher.render.MeshRenderer#add} does the rest -
 * which is also where the translucency comes from, since that tint carries alpha. Same
 * trick the cave gems already use, and the reason a new gem colour costs one constant.
 */
public final class CrystalMesh {

    /** Texture is one small facet sheet shared by every shard. */
    public static final int TEX = 32;

    private CrystalMesh() {}

    /**
     * A cluster of shards growing upward from the origin.
     *
     * @param shards how many main spikes
     * @param height length of the tallest, in model units
     * @param seed   fixes the arrangement, so the same gem looks the same every frame
     */
    public static Mesh cluster(int shards, float height, long seed) {
        Random rng = new Random(seed);
        BoxMesh mesh = new BoxMesh(TEX, TEX);

        for (int i = 0; i < shards; i++) {
            // Fan the spikes around the root, longest in the middle of the fan.
            float around = (float) (Math.PI * 2 * i / shards + rng.nextFloat() * 0.5);
            float lean = 0.18f + rng.nextFloat() * 0.45f;             // how far off vertical
            float len = height * (0.55f + rng.nextFloat() * 0.45f);
            float thick = height * (0.10f + rng.nextFloat() * 0.07f);

            float dx = (float) Math.cos(around) * lean;
            float dz = (float) Math.sin(around) * lean;
            // Spikes start a little way out from the root so the cluster has a footprint.
            float rootOut = height * 0.06f * (i % 2 == 0 ? 1f : 1.6f);
            shard(mesh, dx * rootOut, 0f, dz * rootOut, dx, dz, len, thick, rng);
        }

        // Stubs around the base, so the cluster looks grown rather than placed.
        int stubs = 2 + rng.nextInt(3);
        for (int i = 0; i < stubs; i++) {
            float around = (float) (Math.PI * 2 * rng.nextFloat());
            float out = height * (0.10f + rng.nextFloat() * 0.14f);
            float len = height * (0.16f + rng.nextFloat() * 0.18f);
            float thick = height * (0.07f + rng.nextFloat() * 0.05f);
            shard(mesh, (float) Math.cos(around) * out, 0f, (float) Math.sin(around) * out,
                    (float) Math.cos(around) * 0.8f, (float) Math.sin(around) * 0.8f, len, thick, rng);
        }

        return mesh.build();
    }

    /**
     * One shard: a slim box whose long axis leans by (leanX, leanZ) per unit of height.
     *
     * The two cross-section edges are deliberately not square to each other or to the lean,
     * which is what stops a cluster of these reading as a bundle of rectangles.
     */
    private static void shard(BoxMesh mesh, float x, float y, float z,
                              float leanX, float leanZ, float len, float thick, Random rng) {
        // Long axis.
        float bx = leanX * len, by = len, bz = leanZ * len;
        // Cross-section: one edge roughly across the lean, the other roughly along it, both
        // nudged so opposite faces are not parallel to the world axes.
        float twist = rng.nextFloat() * 0.6f - 0.3f;
        float ax = thick, ay = thick * twist * 0.4f, az = thick * twist;
        float cx = -thick * twist, cy = thick * twist * 0.3f, cz = thick * (0.8f + rng.nextFloat() * 0.4f);

        mesh.skewBox(x - ax * 0.5f - cx * 0.5f, y, z - az * 0.5f - cz * 0.5f,
                ax, ay, az, bx, by, bz, cx, cy, cz,
                0, 0, TEX / 4, TEX / 2, TEX / 4);
    }
}
