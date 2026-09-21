package com.scathabutcher.render.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link Mesh} out of axis-aligned boxes, laid out and UV-mapped the way
 * Minecraft and Blockbench do it.
 *
 * This is the hand-authored route into the renderer. {@link GltfLoader} is the other one,
 * for anything actually modelled in Blockbench; both produce the same {@link Mesh}, so
 * nothing downstream cares which was used. Boxes are enough for a Scatha - it is a
 * segmented worm, and every segment is a cuboid - and being able to write the model as a
 * few lines of code rather than a binary asset means there is no file to keep in sync with
 * the code that positions it, which is exactly the trap the texture scripts in tools/ set.
 *
 * <h2>The UV layout</h2>
 *
 * A box of width w, height h and depth d unwraps into the standard cross:
 *
 * <pre>
 *        u0+d        u0+d+w      u0+2d+w
 *   v0    +-----------+-----------+          down / up  (d deep, w wide)
 *   v0+d  +-----+-----+-----+-----+          east / north / west / south
 *   v0+d+h
 * </pre>
 *
 * which is what Blockbench writes and what a vanilla entity texture expects, so a skin
 * drawn for one works in the other.
 */
public final class BoxMesh {

    private final List<Mesh.Quad> quads = new ArrayList<>();
    private final int texWidth;
    private final int texHeight;

    public BoxMesh(int texWidth, int texHeight) {
        this.texWidth = texWidth;
        this.texHeight = texHeight;
    }

    /**
     * Adds a box.
     *
     * @param x  ,y,z  the minimum corner, in model units
     * @param w  ,h,d  size along each axis
     * @param u0 ,v0   top-left of the box's UV cross in the texture
     */
    public BoxMesh box(float x, float y, float z, float w, float h, float d, int u0, int v0) {
        return box(x, y, z, w, h, d, u0, v0, Math.round(w), Math.round(h), Math.round(d));
    }

    /**
     * Adds a box whose UV cross is sized independently of its geometry, for when a long
     * segment should reuse a short strip of texture rather than stretch one pixel across it.
     */
    public BoxMesh box(float x, float y, float z, float w, float h, float d,
                       int u0, int v0, int tw, int th, int td) {
        float x1 = x + w, y1 = y + h, z1 = z + d;

        // Sides. Each is given as: corner, edge across (texture u), edge down (texture v).
        // Texture v runs downward, so the "down" edge is -Y for every side face.
        // north (-Z): seen from -Z, u runs +X
        quad(x,  y1, z,   w, 0, 0,   0, -h, 0,   0, 0, -1,   u0 + td,           v0 + td, tw, th);
        // south (+Z): seen from +Z, u runs -X
        quad(x1, y1, z1, -w, 0, 0,   0, -h, 0,   0, 0,  1,   u0 + td + tw + td, v0 + td, tw, th);
        // west (-X): seen from -X, u runs -Z
        quad(x,  y1, z1,  0, 0, -d,  0, -h, 0,  -1, 0,  0,   u0,                v0 + td, td, th);
        // east (+X): seen from +X, u runs +Z
        quad(x1, y1, z,   0, 0,  d,  0, -h, 0,   1, 0,  0,   u0 + td + tw,      v0 + td, td, th);
        // up (+Y): seen from above, u runs +X, v runs +Z
        quad(x,  y1, z,   w, 0, 0,   0, 0,  d,   0, 1,  0,   u0 + td + tw,      v0,      tw, td);
        // down (-Y): seen from below, u runs +X, v runs -Z
        quad(x,  y,  z1,  w, 0, 0,   0, 0, -d,   0, -1, 0,   u0 + td,           v0,      tw, td);
        return this;
    }

    /**
     * Adds a single flat quad. Useful for a cut face, a blade edge or anything that should
     * not have thickness.
     */
    public BoxMesh quad(float ox, float oy, float oz,
                        float ux, float uy, float uz,
                        float vx, float vy, float vz,
                        float nx, float ny, float nz,
                        int u0, int v0, int uw, int vh) {
        quads.add(new Mesh.Quad(ox, oy, oz, ux, uy, uz, vx, vy, vz, nx, ny, nz, u0, v0, uw, vh));
        return this;
    }

    /**
     * Adds an already-built quad, front flag and blank rect included.
     *
     * For anything that produces quads by itself rather than by describing boxes - which
     * currently means {@link GltfLoader}, whose faces come out of the file fully formed.
     */
    public BoxMesh quad(Mesh.Quad q) {
        quads.add(q);
        return this;
    }


    /**
     * Adds a sheared box: a corner plus three edge vectors that need not be axis-aligned
     * or even perpendicular.
     *
     * This is how anything angular and off-axis gets built - crystal shards, a blade held
     * at a tilt. It stays on the fast path because all six faces of a parallelepiped are
     * parallelograms, and a parallelogram is exactly what {@link Mesh.Quad} and the affine
     * blit can draw. A *tapered* shard would need trapezoid faces, which an affine map
     * cannot produce from a rectangle, so shapes here shear rather than taper.
     *
     * @param ax ,ay,az  first edge  - mapped to the box's "width" for UV purposes
     * @param bx ,by,bz  second edge - its "height"
     * @param cx ,cy,cz  third edge  - its "depth"
     */
    public BoxMesh skewBox(float ox, float oy, float oz,
                           float ax, float ay, float az,
                           float bx, float by, float bz,
                           float cx, float cy, float cz,
                           int u0, int v0, int tw, int th, int td) {
        // Each face is given as: corner, edge across (texture u), edge down (texture v),
        // and the outward normal, which is the cross product flipped to point away from
        // the box's interior - the third edge tells us which way that is.
        // -c and +c faces (spanned by a, b)
        face(ox, oy, oz,                ax, ay, az,  bx, by, bz,  -cx, -cy, -cz,  u0 + td,           v0 + td, tw, th);
        face(ox + cx, oy + cy, oz + cz, ax, ay, az,  bx, by, bz,   cx,  cy,  cz,  u0 + td + tw + td, v0 + td, tw, th);
        // -b and +b faces (spanned by a, c)
        face(ox, oy, oz,                ax, ay, az,  cx, cy, cz,  -bx, -by, -bz,  u0 + td,           v0,      tw, td);
        face(ox + bx, oy + by, oz + bz, ax, ay, az,  cx, cy, cz,   bx,  by,  bz,  u0 + td + tw,      v0,      tw, td);
        // -a and +a faces (spanned by b, c)
        face(ox, oy, oz,                cx, cy, cz,  bx, by, bz,  -ax, -ay, -az,  u0,                v0 + td, td, th);
        face(ox + ax, oy + ay, oz + az, cx, cy, cz,  bx, by, bz,   ax,  ay,  az,  u0 + td + tw,      v0 + td, td, th);
        return this;
    }

    /** One face of a sheared box, with its normal oriented away from {@code out}. */
    private void face(float ox, float oy, float oz,
                      float ux, float uy, float uz,
                      float vx, float vy, float vz,
                      float outX, float outY, float outZ,
                      int u0, int v0, int uw, int vh) {
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        if (nx * outX + ny * outY + nz * outZ < 0f) { nx = -nx; ny = -ny; nz = -nz; }
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-6f) { nx /= len; ny /= len; nz /= len; }
        quads.add(new Mesh.Quad(ox, oy, oz, ux, uy, uz, vx, vy, vz, nx, ny, nz, u0, v0, uw, vh));
    }

    /**
     * As {@link #skewBox}, but every face samples the same texture rect.
     *
     * A full UV cross needs {@code 2*(tw+td)} texels of width, which makes packing several
     * materials into one sheet fiddly - get it wrong and a box quietly samples its
     * neighbour's strip, which is how the cleaver first came out with a wooden blade. When
     * a part is one uniform material there is nothing to unwrap, so all six faces take the
     * same patch and the packing problem disappears.
     */
    public BoxMesh skewBoxFlat(float ox, float oy, float oz,
                               float ax, float ay, float az,
                               float bx, float by, float bz,
                               float cx, float cy, float cz,
                               int u0, int v0, int uw, int vh) {
        face(ox, oy, oz,                ax, ay, az,  bx, by, bz,  -cx, -cy, -cz,  u0, v0, uw, vh);
        face(ox + cx, oy + cy, oz + cz, ax, ay, az,  bx, by, bz,   cx,  cy,  cz,  u0, v0, uw, vh);
        face(ox, oy, oz,                ax, ay, az,  cx, cy, cz,  -bx, -by, -bz,  u0, v0, uw, vh);
        face(ox + bx, oy + by, oz + bz, ax, ay, az,  cx, cy, cz,   bx,  by,  bz,  u0, v0, uw, vh);
        face(ox, oy, oz,                cx, cy, cz,  bx, by, bz,  -ax, -ay, -az,  u0, v0, uw, vh);
        face(ox + ax, oy + ay, oz + az, cx, cy, cz,  bx, by, bz,   ax,  ay,  az,  u0, v0, uw, vh);
        return this;
    }

    /** Axis-aligned {@link #skewBoxFlat}. */
    public BoxMesh boxFlat(float x, float y, float z, float w, float h, float d,
                           int u0, int v0, int uw, int vh) {
        return skewBoxFlat(x, y, z, w, 0, 0, 0, h, 0, 0, 0, d, u0, v0, uw, vh);
    }

    /** Number of quads so far - handy in tests and when checking a model was built at all. */
    public int size() { return quads.size(); }

    public Mesh build() {
        if (quads.isEmpty()) throw new IllegalStateException("mesh has no faces");
        return new Mesh(quads, texWidth, texHeight);
    }
}
