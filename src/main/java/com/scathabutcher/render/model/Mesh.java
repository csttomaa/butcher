package com.scathabutcher.render.model;

import java.util.List;

/**
 * A 3D model, stored as flat quads ready for {@link ModelProjection}.
 *
 * <h2>Why this exists at all</h2>
 *
 * The GUI layer hands out a {@link org.joml.Matrix3x2fStack} - a 2D transform - and for a
 * long time this project read that as "no 3D from the HUD", which is why the cleaver's
 * rotation is baked into fourteen pre-rendered frames and the pet head "spins" by having
 * its width pinched.
 *
 * That conclusion was wrong, and SkyMaxx is the proof. A 2D <em>affine</em> transform can
 * map a rectangle onto any parallelogram, and the perspective-free projection of a flat
 * quad <em>is</em> a parallelogram. So a quad can be drawn by handing the GUI its texture
 * rect and an affine matrix built from the projected edges - real rotation, from the HUD,
 * with the blit call this project already uses everywhere.
 *
 * Hence this representation. Each quad is a corner plus the two edge vectors leaving it,
 * rather than four corners: those three vectors <em>are</em> the affine transform, so
 * projection is a rotation of three vectors and nothing else. The normal rides along for
 * shading and for deciding which side is facing the camera.
 *
 * Deliberately free of Minecraft imports. Everything here is arithmetic, which means the
 * whole pipeline can be rendered to a PNG offline and looked at - see the harness in
 * tools/ - instead of being debugged by relaunching the game and killing a worm.
 */
public final class Mesh {

    /** One textured quad: a corner, the two edges leaving it, its normal, and its texture rect. */
    public static final class Quad {
        /** The corner the two edge vectors leave from. */
        public final float ox, oy, oz;
        /** First edge. Maps to the texture rect's width. */
        public final float ux, uy, uz;
        /** Second edge. Maps to the texture rect's height. */
        public final float vx, vy, vz;
        /** Outward normal, unit length. */
        public final float nx, ny, nz;
        /** Texture rect in pixels. */
        public final int u0, v0, uw, vh;

        /**
         * True if this quad is the piece's "front" - the face carrying whatever detail
         * distinguishes it, an eye or a mouth. Only front faces can be blanked.
         */
        public final boolean front;
        /**
         * An alternative texture rect, or null. When a model is projected
         * {@linkplain ModelProjection#project blanked}, front faces draw this rect
         * instead - a patch of plain skin somewhere else on the same sheet.
         *
         * This is how one mesh serves two states without a second model: a worm segment
         * whose head end has been cut off should show bare flesh, not an eye. Stored as
         * {@code {u0, v0, uw, vh}}.
         */
        public final int[] blank;

        public Quad(float ox, float oy, float oz,
                    float ux, float uy, float uz,
                    float vx, float vy, float vz,
                    float nx, float ny, float nz,
                    int u0, int v0, int uw, int vh) {
            this(ox, oy, oz, ux, uy, uz, vx, vy, vz, nx, ny, nz, u0, v0, uw, vh, false, null);
        }

        public Quad(float ox, float oy, float oz,
                    float ux, float uy, float uz,
                    float vx, float vy, float vz,
                    float nx, float ny, float nz,
                    int u0, int v0, int uw, int vh,
                    boolean front, int[] blank) {
            this.ox = ox; this.oy = oy; this.oz = oz;
            this.ux = ux; this.uy = uy; this.uz = uz;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.nx = nx; this.ny = ny; this.nz = nz;
            this.u0 = u0; this.v0 = v0; this.uw = uw; this.vh = vh;
            this.front = front;
            this.blank = blank == null ? null : blank.clone();
        }

        /** The same quad with {@link #front} set and an optional blank rect. */
        public Quad asFront(int[] blankRect) {
            return new Quad(ox, oy, oz, ux, uy, uz, vx, vy, vz, nx, ny, nz,
                    u0, v0, uw, vh, true, blankRect);
        }

        public float centreX() { return ox + (ux + vx) * 0.5f; }
        public float centreY() { return oy + (uy + vy) * 0.5f; }
        public float centreZ() { return oz + (uz + vz) * 0.5f; }
    }

    private final List<Quad> quads;
    private final int texWidth;
    private final int texHeight;
    private final float minY, maxY, minX, maxX, minZ, maxZ;
    private final float radius;

    Mesh(List<Quad> quads, int texWidth, int texHeight) {
        this.quads = List.copyOf(quads);
        this.texWidth = texWidth;
        this.texHeight = texHeight;

        float lowY = Float.MAX_VALUE, highY = -Float.MAX_VALUE;
        float lowX = Float.MAX_VALUE, highX = -Float.MAX_VALUE;
        float lowZ = Float.MAX_VALUE, highZ = -Float.MAX_VALUE;
        float radiusSq = 0f;
        for (Quad q : quads) {
            // The four corners are the origin plus each combination of the two edges.
            for (int corner = 0; corner < 4; corner++) {
                float a = (corner & 1) == 0 ? 0f : 1f;
                float b = (corner & 2) == 0 ? 0f : 1f;
                float x = q.ox + q.ux * a + q.vx * b;
                float y = q.oy + q.uy * a + q.vy * b;
                float z = q.oz + q.uz * a + q.vz * b;
                lowX = Math.min(lowX, x); highX = Math.max(highX, x);
                lowY = Math.min(lowY, y); highY = Math.max(highY, y);
                lowZ = Math.min(lowZ, z); highZ = Math.max(highZ, z);
                radiusSq = Math.max(radiusSq, x * x + z * z);
            }
        }
        this.minX = lowX; this.maxX = highX;
        this.minY = lowY; this.maxY = highY;
        this.minZ = lowZ; this.maxZ = highZ;
        this.radius = (float) Math.sqrt(radiusSq);
    }

    public List<Quad> quads() { return quads; }
    public int texWidth() { return texWidth; }
    public int texHeight() { return texHeight; }

    public float minX() { return minX; }
    public float maxX() { return maxX; }
    public float minY() { return minY; }
    public float maxY() { return maxY; }
    public float minZ() { return minZ; }
    public float maxZ() { return maxZ; }

    public float width()  { return maxX - minX; }
    public float height() { return maxY - minY; }
    public float depth()  { return maxZ - minZ; }
    /** Distance from the Y axis to the furthest corner - how much room a full spin needs. */
    public float radius() { return radius; }
    public float centreY() { return (minY + maxY) * 0.5f; }
}
