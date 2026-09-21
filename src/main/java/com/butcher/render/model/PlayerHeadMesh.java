package com.butcher.render.model;

/**
 * A vanilla player head - base cube plus hat layer - mapped onto a standard 64x64 skin.
 *
 * <h2>Why this is the right model for a Scatha</h2>
 *
 * Crystal Hollows worms are not a custom entity. Each segment is an armour stand wearing a
 * player head, and the head's skin is the worm's texture. That single fact explains the
 * reference render completely: the jagged, stepped silhouette along the worm's back - the
 * thing two hand-built approximations kept failing to reproduce - is not modelled geometry
 * at all. It is the <em>hat layer</em>: a second cube, inflated slightly, whose skin is
 * transparent everywhere except the few pixels that stick out.
 *
 * So instead of inventing a shape and painting it, the correct model is the one the game
 * already uses, and the correct texture is the skin the game already has. See
 * {@link com.butcher.render.ScathaSkins} for where that skin comes from.
 *
 * <h2>The UV layout</h2>
 *
 * Minecraft's cube unwrap, which is what every skin is authored against:
 *
 * <pre>
 *          top     bottom
 *   right  front   left   back
 * </pre>
 *
 * with the head at (0,0) and the hat at (32,0). Model space has the face pointing at -Z,
 * which is the convention every vanilla entity model uses.
 */
public final class PlayerHeadMesh {

    /** Every player skin is this size; anything else is a legacy 64x32 and unsupported here. */
    public static final int SKIN_SIZE = 64;
    /** A head is eight units on a side. */
    public static final int HEAD = 8;
    /** How far the hat layer stands off the base cube, per side, in model units. */
    public static final float HAT_INFLATE = 0.5f;

    private PlayerHeadMesh() {}

    /** Base cube and hat layer together, centred on the origin. */
    public static Mesh head() {
        BoxMesh mesh = new BoxMesh(SKIN_SIZE, SKIN_SIZE);
        cube(mesh, HEAD, 0f, 0, 0);
        cube(mesh, HEAD, HAT_INFLATE, 32, 0);
        return mesh.build();
    }

    /** Just the base cube - for anywhere the hat's overhang would get in the way. */
    public static Mesh bare() {
        BoxMesh mesh = new BoxMesh(SKIN_SIZE, SKIN_SIZE);
        cube(mesh, HEAD, 0f, 0, 0);
        return mesh.build();
    }

    /**
     * One cube of the head, centred on the origin and inflated by {@code grow} on each side.
     *
     * The faces are written out one at a time rather than going through
     * {@link BoxMesh#box} because Minecraft's slot order is its own: top and bottom are
     * swapped relative to the obvious layout, and so are left and right. Getting that
     * wrong does not fail loudly - it just puts the face on the back of the head.
     */
    private static void cube(BoxMesh mesh, int size, float grow, int u, int v) {
        float h = size / 2f + grow;
        int s = size;

        // This renderer views from +Z (yaw 0 shows a model's +Z side), so the skin's FRONT
        // slot goes on +Z, not on -Z as a vanilla entity model would have it. The u-edge
        // directions below were settled empirically with a labelled skin - get one backwards
        // and the face renders mirrored rather than failing, which is easy to miss.
        // front: +Z
        mesh.quad(-h,  h,  h,   2 * h, 0, 0,    0, -2 * h, 0,   0, 0,  1,   u + s,     v + s, s, s);
        // back: -Z
        mesh.quad( h,  h, -h,  -2 * h, 0, 0,    0, -2 * h, 0,   0, 0, -1,   u + 3 * s, v + s, s, s);
        // right: +X
        mesh.quad( h,  h,  h,   0, 0, -2 * h,   0, -2 * h, 0,   1, 0,  0,   u,         v + s, s, s);
        // left: -X
        mesh.quad(-h,  h, -h,   0, 0, 2 * h,    0, -2 * h, 0,  -1, 0,  0,   u + 2 * s, v + s, s, s);
        // top: +Y. In the unwrap the top slot sits directly above the front slot and shares
        // an edge with it, so v runs toward the front of the head.
        mesh.quad(-h,  h, -h,   2 * h, 0, 0,    0, 0, 2 * h,    0, 1,  0,   u + s,     v,     s, s);
        // bottom: -Y, which is the mirrored one - v runs toward the back.
        mesh.quad(-h, -h,  h,   2 * h, 0, 0,    0, 0, -2 * h,   0, -1, 0,   u + 2 * s, v,     s, s);
    }
}
