package com.butcher.render.model;

/**
 * The butcher's cleaver, as real geometry.
 *
 * <h2>Why this replaces the sprite sheet</h2>
 *
 * The cleaver used to be fourteen pre-rendered PNGs, one per frame of the swing, because
 * the GUI layer was believed to be unable to rotate anything. It can (see
 * {@link ModelProjection}), so the blade is now a model and the swing is just an angle.
 * That removes the whole linked-set problem CLAUDE.md warns about - all fourteen frames
 * shared one pivot, so repainting any single frame broke the arc - and it means the blade
 * can be lit, tinted and depth-sorted against the worm like everything else.
 *
 * <h2>Layout</h2>
 *
 * Built lying along X with the edge facing -Y, centred on the pivot the swing rotates
 * about, which is the butt of the handle rather than the blade. Swinging about the handle
 * is what gives the arc its weight; rotating about the blade's own centre reads as a
 * spinning prop.
 *
 * The blade is sheared rather than square: the spine leans back from the edge, so the
 * profile is a wedge. That plus the separate bolster is most of what makes it read as a
 * cleaver and not a grey rectangle.
 */
public final class CleaverMesh {

    /** Texture holding steel, wood and brass strips. */
    public static final int TEX = 64;

    // Material patches in the sheet, as {u, v, w, h}. Every face of a part samples its
    // whole patch - see BoxMesh.skewBoxFlat for why these are patches and not UV crosses.
    public static final int[] STEEL = {0, 0, 24, 28};
    public static final int[] EDGE  = {0, 30, 24, 4};
    public static final int[] WOOD  = {24, 0, 20, 32};
    public static final int[] BRASS = {44, 0, 20, 32};

    /** Distance from the pivot to the blade's far corner - what the swing radius is set from. */
    public static final float REACH = 34f;

    private CleaverMesh() {}

    /**
     * The whole cleaver, pivot at the origin.
     *
     * Handle runs out along +X; the blade hangs off the far end with its edge downward, so
     * a rotation about Z sweeps the edge through an arc.
     *
     * <h3>Proportions are the whole job</h3>
     *
     * The first version made the blade 15.5 long by 15.5 tall - square - and sheared it
     * back, so on screen it was a white rhombus on a stick. A cleaver does not read from
     * its material or its shading; it reads from being a BROAD, low rectangle with a flat
     * bottom, much longer than it is tall. This one is 20 by 11.5, and the spine leans
     * only slightly rather than turning the silhouette into a diamond.
     *
     * <h3>One box for the blade, on purpose</h3>
     *
     * Nothing here can taper: the affine blit maps a rectangle onto a parallelogram, so a
     * trapezoid is not available (see Mesh). A second, shorter box stacked on top was
     * tried as a fake bevel and read as two plates overlapping rather than one blade with
     * a cut corner - worse than the square tip it was meant to soften. A butcher's
     * cleaver is a rectangle anyway; the honest shape beats a suggestion of a better one.
     */
    public static Mesh build() {
        BoxMesh mesh = new BoxMesh(TEX, TEX);

        // ---- pommel: a small block at the butt, so the handle has an end ----
        mesh.boxFlat(0.4f, -2.6f, -1.9f, 1.6f, 5.2f, 3.8f, BRASS[0], BRASS[1], BRASS[2], BRASS[3]);

        // ---- handle ---------------------------------------------------------
        mesh.skewBoxFlat(2f, -2.2f, -1.6f,
                10.5f, 0.5f, 0f,     // along +X, rising a touch towards the bolster
                0f, 4.4f, 0f,
                0f, 0f, 3.2f,
                WOOD[0], WOOD[1], WOOD[2], WOOD[3]);

        // ---- bolster: the thick collar between grip and blade --------------
        mesh.boxFlat(12.5f, -3.2f, -2.1f, 2.4f, 6.6f, 4.2f, BRASS[0], BRASS[1], BRASS[2], BRASS[3]);

        // ---- blade: broad, low, flat-bottomed ------------------------------
        // Long and shallow is what says "cleaver". The spine leans back by a couple of
        // units over its whole height - enough to catch the light differently from the
        // face, not enough to turn the outline into a diamond.
        mesh.skewBoxFlat(14.5f, -7.4f, -0.9f,
                19.5f, 0f, 0f,        // length, out to the tip
                -1.6f, 11.5f, 0f,     // height, leaning back towards the handle
                0f, 0f, 1.8f,         // thickness
                STEEL[0], STEEL[1], STEEL[2], STEEL[3]);

        // ---- the edge itself: a thin bright strip under the blade ----------
        // Its own box so it can be nearly white in the texture and read as a honed edge
        // from any angle, instead of relying on a highlight painted onto the blade face.
        mesh.skewBoxFlat(14.3f, -8.7f, -0.75f,
                19.9f, 0f, 0f,
                0f, 1.5f, 0f,
                0f, 0f, 1.5f,
                EDGE[0], EDGE[1], EDGE[2], EDGE[3]);

        // ---- spine: a brass strip along the back of the blade --------------
        // Gives the top of the silhouette a line of its own, so the blade does not end
        // in the same flat steel it is made of, and marks which way up it is at a glance.
        mesh.skewBoxFlat(13.0f, 3.5f, -0.8f,
                19.5f, 0f, 0f,
                0f, 1.0f, 0f,
                0f, 0f, 1.6f,
                BRASS[0], BRASS[1], BRASS[2], BRASS[3]);

        return mesh.build();
    }
}
