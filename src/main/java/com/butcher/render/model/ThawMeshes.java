package com.butcher.render.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The geometry of the Thaw: the chunk of ice, the Vanguard frozen inside it, and the pick.
 *
 * <h2>The ice is a chunk, not a box</h2>
 *
 * It is cut out of space by planes: front, back, two leaning sides, a slanted top, a flat
 * bottom, and a handful of chips taken off corners and edges at odd angles. The corners of
 * the shape are wherever three of those planes meet, and each plane that survives becomes
 * one facet. Adding a chip is one line.
 *
 * The renderer can only draw parallelograms - an affine blit maps a rectangle and nothing
 * else - and a facet of a chipped block is a pentagon, a triangle, a heptagon. So each
 * facet is drawn as its bounding parallelogram, and its real outline is cut into the
 * texture's alpha by {@link com.butcher.render.art.ThawArt#ice}. The texture decides the
 * shape; the geometry only decides where the shape sits.
 *
 * <h2>The Vanguard is inside the ice, not painted on it</h2>
 *
 * A real figure, built from boxes like a player - head, body, two arms with pauldrons, two
 * legs - shaped like the Vanguard and painted as a shadow, frozen in one of several poses
 * picked per corpse. The far facets are drawn, then him, then the near facets, so he sits
 * between two layers of frost, and as the front clears stage by stage he comes up out of
 * it. The far facets matter more than they look: they are the pale ground a shadow needs
 * to be seen against - without them he was dark on dark and vanished.
 *
 * <h2>Which way is the front</h2>
 *
 * The camera sits at +Z (see CLAUDE.md), so the face the player looks at is the +Z one, and
 * the Vanguard faces +Z too. Faces are written as corner, the edge the texture's u runs
 * along, the edge its v runs down, and the outward normal, with {@code v = u x n}: the front
 * is u along +X and v down -Y, which keeps every texture the right way round.
 *
 * <h2>The pick</h2>
 *
 * Built like {@link CleaverMesh}: lying along +X from the pivot at the butt of the handle, so
 * a roll about Z swings the head through an arc and the whole swing is one angle. The
 * striking prong hangs off the head towards +Y. Neither prong can taper - the affine blit
 * maps rectangles to parallelograms and nothing else - so each is three shrinking, sheared
 * boxes stepping back towards the handle, which is also how a Minecraft pickaxe is drawn.
 */
public final class ThawMeshes {

    // ================================================================== ice

    /**
     * The chunk's nominal size, in model units: what the screen scales it by. The real
     * shape is a little smaller everywhere, because of the chips.
     */
    public static final float ICE_W = 24f, ICE_H = 32f, ICE_D = 16f;

    /** The ice sheet: every facet's patch, packed. One per crack stage, all the same layout. */
    public static final int ICE_TEX = 256;
    /**
     * Texture pixels per model unit on every facet, so frost grain matches across edges.
     *
     * About twice what the old square block used. Its edges ran along the texture's grid,
     * so coarse pixels cost nothing; a chipped facet's edges run at any angle, and at the
     * old density every slanted edge came out as a staircase with slivers sticking out.
     */
    public static final float ICE_DENSITY = 3f;

    /**
     * The angle the block is seen from at rest. ThawScreen sways it a few degrees either side
     * of this; the ice art uses it to know which facets are the far ones.
     */
    public static final float VIEW_YAW = 24f, VIEW_PITCH = -13f;

    /** Where the pick lands, on the front facet. Where the cracks start. */
    public static final float STRIKE_X = 5.0f, STRIKE_Y = 4.0f, STRIKE_Z = ICE_D / 2f;

    /**
     * The planes the chunk is cut from, as {nx, ny, nz, d}: the ice is every point no further
     * than {@code d} from the centre along the direction {@code n}. Directions need not be unit
     * length; d is a true distance either way, so a chip's number says how deep it cuts.
     */
    private static final float[][] PLANES = {
            {0f, 0f, 1f, ICE_D / 2f},                 // front - flat, the pick's face
            {0.04f, 0.03f, -1f, ICE_D / 2f},          // back, a little off square
            {-1f, 0.08f, 0.02f, 12f},                 // left, leaning in towards the top
            {1f, 0.12f, -0.03f, 11.5f},               // right, leaning in more
            {0f, -1f, 0f, ICE_H / 2f},                // bottom - flat, it stands on it
            {0.18f, 1f, 0.1f, 14.8f},                 // top, sloping down to the right
            // Chips: a few, and broad. Many small ones made it read as a cut gem.
            {1f, 1.1f, 1f, 17.2f},                    // top right front
            {-1f, 1f, -0.8f, 18.8f},                  // top left back
            {-1f, -1f, 1f, 19.3f},                    // bottom left front
            {1f, 0f, -1f, 12.3f},                     // right back edge
            {1f, -1f, 1f, 19.4f},                     // bottom right front, small
    };

    /**
     * One facet of the chunk.
     *
     * @param rect    its patch in the ice sheet, {u0, v0, w, h}
     * @param polygon its real outline in that patch's own pixels, {x0, y0, x1, y1, ...}
     * @param origin  the parallelogram's corner in model units, then the u edge, the v edge
     *                and the normal: {ox, oy, oz, ux, uy, uz, vx, vy, vz, nx, ny, nz}
     * @param front   the face the pick strikes and the cracks run across
     * @param across  for each edge of the outline - edge {@code i} runs from corner {@code i-1}
     *                to corner {@code i} - the index of the facet on the other side of it
     */
    public record IceFacet(int[] rect, float[] polygon, float[] origin, boolean front, int[] across) {

        /**
         * How far a patch pixel is inside edge {@code i}'s line, in pixels; negative outside.
         * For a convex outline, "inside every edge's line" is exactly "inside the facet", and
         * moving one line alone moves just that edge - which is how each edge gets its own cut.
         */
        public float insideEdge(int i, float px, float py) {
            int n = polygon.length / 2;
            int j = (i + n - 1) % n;
            float ax = polygon[j * 2], ay = polygon[j * 2 + 1];
            float bx = polygon[i * 2], by = polygon[i * 2 + 1];
            float dx = bx - ax, dy = by - ay;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-6f) return Float.MAX_VALUE;
            float side = (dx * (py - ay) - dy * (px - ax)) / len;
            // Which side is "inside" depends on which way round the corners were sorted;
            // the centroid is always inside, so measure against that.
            float cx = 0, cy = 0;
            for (int k = 0; k < n; k++) { cx += polygon[k * 2]; cy += polygon[k * 2 + 1]; }
            cx /= n; cy /= n;
            float ref = dx * (cy - ay) - dy * (cx - ax);
            return ref >= 0 ? side : -side;
        }

        /** The model-space point under a pixel of this facet's patch. */
        public float[] worldAt(float px, float py) {
            float a = px / rect[2], b = py / rect[3];
            return new float[]{
                    origin[0] + origin[3] * a + origin[6] * b,
                    origin[1] + origin[4] * a + origin[7] * b,
                    origin[2] + origin[5] * a + origin[8] * b};
        }

        /** Whether a point in the patch's pixels is inside the facet's outline. */
        public boolean contains(float px, float py) {
            boolean in = false;
            int n = polygon.length / 2;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                float xi = polygon[i * 2], yi = polygon[i * 2 + 1];
                float xj = polygon[j * 2], yj = polygon[j * 2 + 1];
                if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) in = !in;
            }
            return in;
        }

        /** How far a patch pixel is from the facet's outline, in pixels. */
        public float edgeDistance(float px, float py) {
            float best = Float.MAX_VALUE;
            int n = polygon.length / 2;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                float ax = polygon[j * 2], ay = polygon[j * 2 + 1];
                float bx = polygon[i * 2], by = polygon[i * 2 + 1];
                float dx = bx - ax, dy = by - ay;
                float t = Math.max(0f, Math.min(1f, ((px - ax) * dx + (py - ay) * dy) / Math.max(1e-6f, dx * dx + dy * dy)));
                float ex = ax + dx * t - px, ey = ay + dy * t - py;
                best = Math.min(best, (float) Math.sqrt(ex * ex + ey * ey));
            }
            return best;
        }
    }

    private static final List<IceFacet> FACETS = new ArrayList<>();
    private static final List<float[]> VERTICES = new ArrayList<>();

    static {
        buildChunk();
    }

    /** Every facet, in a fixed order. The front is the one with {@code front()} set. */
    public static List<IceFacet> iceFacets() { return FACETS; }

    /** The chunk's corners, for measuring what is actually drawn. */
    public static List<float[]> iceVertices() { return VERTICES; }

    /** The front facet. */
    public static IceFacet frontFacet() {
        for (IceFacet f : FACETS) if (f.front()) return f;
        throw new IllegalStateException("the ice has no front");
    }

    /** Where the pick lands, in the front facet's patch pixels. */
    public static float[] strikePixel() {
        IceFacet f = frontFacet();
        float[] o = f.origin();
        // The front is u along +X and v down -Y, so this is a straight division.
        float px = (STRIKE_X - o[0]) / o[3] * f.rect()[2];
        float py = (STRIKE_Y - o[1]) / o[7] * f.rect()[3];
        return new float[]{px, py};
    }

    /** Whether a model-space point is inside the ice, with {@code margin} to spare. */
    public static boolean insideIce(float x, float y, float z, float margin) {
        for (float[] p : PLANES) {
            float len = (float) Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
            if ((p[0] * x + p[1] * y + p[2] * z) / len > p[3] - margin) return false;
        }
        return true;
    }

    /** The chunk as a mesh: one quad per facet, its outline left to the texture. */
    public static Mesh ice() {
        BoxMesh m = new BoxMesh(ICE_TEX, ICE_TEX);
        for (IceFacet f : FACETS) {
            float[] o = f.origin();
            int[] r = f.rect();
            m.quad(o[0], o[1], o[2], o[3], o[4], o[5], o[6], o[7], o[8], o[9], o[10], o[11],
                    r[0], r[1], r[2], r[3]);
        }
        return m.build();
    }

    private static final java.util.Map<Integer, Mesh> BY_FACING = new java.util.HashMap<>();

    /** Whether a facet faces the camera at the resting view. */
    public static boolean facesViewer(IceFacet f) {
        double yaw = Math.toRadians(VIEW_YAW), pitch = Math.toRadians(VIEW_PITCH);
        float[] o = f.origin();
        float nz1 = (float) (-o[9] * Math.sin(yaw) + o[11] * Math.cos(yaw));
        return -o[10] * (float) Math.sin(pitch) + nz1 * (float) Math.cos(pitch) > 0f;
    }

    /**
     * Only the facets turned towards the camera, or only those turned away.
     *
     * The Vanguard is drawn first and the near facets over him, rather than trusting the
     * global depth sort. That sort goes by each face's centre, and a small part like a
     * pauldron next to a big facet whose centre is further forward got drawn after it,
     * popping out of the frost. For a convex shape with something inside it,
     * far-then-inside-then-near is exactly right, always.
     *
     * The split changes only as the block sways, so each one is built once and kept.
     */
    public static Mesh iceFacing(boolean towardCamera, float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        float sy = (float) Math.sin(yaw), cy = (float) Math.cos(yaw);
        float sp = (float) Math.sin(pitch), cp = (float) Math.cos(pitch);
        int mask = 0;
        for (int i = 0; i < FACETS.size(); i++) {
            float[] o = FACETS.get(i).origin();
            // The same test ModelProjection uses to decide a face is turned away.
            float nz1 = -o[9] * sy + o[11] * cy;
            float nz2 = -o[10] * sp + nz1 * cp;
            if ((nz2 > 0f) == towardCamera) mask |= 1 << i;
        }
        synchronized (BY_FACING) {
            Mesh cached = BY_FACING.get(mask);
            if (cached != null) return cached;
            BoxMesh m = new BoxMesh(ICE_TEX, ICE_TEX);
            for (int i = 0; i < FACETS.size(); i++) {
                if ((mask & (1 << i)) == 0) continue;
                float[] o = FACETS.get(i).origin();
                int[] r = FACETS.get(i).rect();
                m.quad(o[0], o[1], o[2], o[3], o[4], o[5], o[6], o[7], o[8], o[9], o[10], o[11],
                        r[0], r[1], r[2], r[3]);
            }
            Mesh built = m.build();
            BY_FACING.put(mask, built);
            return built;
        }
    }

    /** Intersects the planes, keeps the corners inside all of them, and lays out the facets. */
    private static void buildChunk() {
        int n = PLANES.length;
        float[][] pl = new float[n][4];
        for (int i = 0; i < n; i++) {
            float len = (float) Math.sqrt(PLANES[i][0] * PLANES[i][0] + PLANES[i][1] * PLANES[i][1]
                    + PLANES[i][2] * PLANES[i][2]);
            for (int k = 0; k < 3; k++) pl[i][k] = PLANES[i][k] / len;
            pl[i][3] = PLANES[i][3];
        }

        // Every corner: three planes meeting at a point that no other plane cuts away.
        for (int a = 0; a < n; a++) for (int b = a + 1; b < n; b++) for (int c = b + 1; c < n; c++) {
            float[] p = meet(pl[a], pl[b], pl[c]);
            if (p == null) continue;
            boolean inside = true;
            for (float[] q : pl) {
                if (q[0] * p[0] + q[1] * p[1] + q[2] * p[2] > q[3] + 1e-3f) { inside = false; break; }
            }
            if (!inside) continue;
            boolean seen = false;
            for (float[] v : VERTICES) {
                if (Math.abs(v[0] - p[0]) + Math.abs(v[1] - p[1]) + Math.abs(v[2] - p[2]) < 1e-3f) { seen = true; break; }
            }
            if (!seen) VERTICES.add(p);
        }

        // One facet per plane that kept at least three corners.
        List<float[]> built = new ArrayList<>();       // {w, h} per facet, for packing
        List<Object[]> pending = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            float[] q = pl[i];
            List<float[]> on = new ArrayList<>();
            for (float[] v : VERTICES) {
                if (Math.abs(q[0] * v[0] + q[1] * v[1] + q[2] * v[2] - q[3]) < 1e-3f) on.add(v);
            }
            if (on.size() < 3) continue;

            float[] nrm = {q[0], q[1], q[2]};
            // v runs "down the face": down the slope for a wall, towards the front for a lid.
            float[] v;
            if (Math.abs(nrm[1]) > 0.9f) {
                v = flatten(new float[]{0f, 0f, nrm[1] > 0 ? 1f : -1f}, nrm);
            } else {
                v = flatten(new float[]{0f, -1f, 0f}, nrm);
            }
            float[] u = cross(nrm, v);

            float cx = 0, cy = 0, cz = 0;
            for (float[] p : on) { cx += p[0]; cy += p[1]; cz += p[2]; }
            cx /= on.size(); cy /= on.size(); cz /= on.size();
            final float fcx = cx, fcy = cy, fcz = cz;
            // Order the corners round the facet.
            on.sort((p1, p2) -> Double.compare(
                    Math.atan2(dot(sub(p1, fcx, fcy, fcz), v), dot(sub(p1, fcx, fcy, fcz), u)),
                    Math.atan2(dot(sub(p2, fcx, fcy, fcz), v), dot(sub(p2, fcx, fcy, fcz), u))));

            float umin = Float.MAX_VALUE, umax = -Float.MAX_VALUE, vmin = Float.MAX_VALUE, vmax = -Float.MAX_VALUE;
            float[] uv = new float[on.size() * 2];
            for (int k = 0; k < on.size(); k++) {
                float[] d = sub(on.get(k), cx, cy, cz);
                uv[k * 2] = dot(d, u);
                uv[k * 2 + 1] = dot(d, v);
                umin = Math.min(umin, uv[k * 2]); umax = Math.max(umax, uv[k * 2]);
                vmin = Math.min(vmin, uv[k * 2 + 1]); vmax = Math.max(vmax, uv[k * 2 + 1]);
            }
            float uw = umax - umin, vh = vmax - vmin;
            int pw = Math.max(2, Math.round(uw * ICE_DENSITY)), ph = Math.max(2, Math.round(vh * ICE_DENSITY));
            float[] poly = new float[uv.length];
            for (int k = 0; k < on.size(); k++) {
                poly[k * 2] = (uv[k * 2] - umin) / uw * pw;
                poly[k * 2 + 1] = (uv[k * 2 + 1] - vmin) / vh * ph;
            }
            float[] origin = {
                    cx + u[0] * umin + v[0] * vmin, cy + u[1] * umin + v[1] * vmin, cz + u[2] * umin + v[2] * vmin,
                    u[0] * uw, u[1] * uw, u[2] * uw,
                    v[0] * vh, v[1] * vh, v[2] * vh,
                    nrm[0], nrm[1], nrm[2]};
            built.add(new float[]{pw, ph});
            pending.add(new Object[]{poly, origin, i == 0, new ArrayList<>(on)});
        }

        // Shelf-pack the patches into the sheet, tallest first, a pixel of gutter each.
        Integer[] order = new Integer[built.size()];
        for (int k = 0; k < order.length; k++) order[k] = k;
        java.util.Arrays.sort(order, (a, b) -> Float.compare(built.get(b)[1], built.get(a)[1]));
        int[][] rects = new int[built.size()][];
        int x = 0, y = 0, shelf = 0;
        for (int k : order) {
            int w = (int) built.get(k)[0], h = (int) built.get(k)[1];
            if (x + w > ICE_TEX) { x = 0; y += shelf + 1; shelf = 0; }
            if (y + h > ICE_TEX) throw new IllegalStateException("ice facets do not fit the sheet");
            rects[k] = new int[]{x, y, w, h};
            x += w + 1;
            shelf = Math.max(shelf, h);
        }
        // Who is across each edge: the other facet that has both of its corners.
        for (int k = 0; k < pending.size(); k++) {
            Object[] p = pending.get(k);
            @SuppressWarnings("unchecked") List<float[]> corners = (List<float[]>) p[3];
            int count = corners.size();
            int[] across = new int[count];
            for (int e = 0; e < count; e++) {
                float[] a = corners.get((e + count - 1) % count), b = corners.get(e);
                across[e] = -1;
                for (int o = 0; o < pending.size() && across[e] < 0; o++) {
                    if (o == k) continue;
                    @SuppressWarnings("unchecked") List<float[]> other = (List<float[]>) pending.get(o)[3];
                    if (has(other, a) && has(other, b)) across[e] = o;
                }
            }
            FACETS.add(new IceFacet(rects[k], (float[]) p[0], (float[]) p[1], (Boolean) p[2], across));
        }
    }

    private static boolean has(List<float[]> corners, float[] v) {
        for (float[] c : corners) {
            if (Math.abs(c[0] - v[0]) + Math.abs(c[1] - v[1]) + Math.abs(c[2] - v[2]) < 1e-3f) return true;
        }
        return false;
    }

    private static float[] meet(float[] a, float[] b, float[] c) {
        float[] bc = cross(b, c), ca = cross(c, a), ab = cross(a, b);
        float det = a[0] * bc[0] + a[1] * bc[1] + a[2] * bc[2];
        if (Math.abs(det) < 1e-5f) return null;
        return new float[]{
                (a[3] * bc[0] + b[3] * ca[0] + c[3] * ab[0]) / det,
                (a[3] * bc[1] + b[3] * ca[1] + c[3] * ab[1]) / det,
                (a[3] * bc[2] + b[3] * ca[2] + c[3] * ab[2]) / det};
    }

    /** {@code d} with its component along {@code n} removed, normalised. */
    private static float[] flatten(float[] d, float[] n) {
        float k = dot(d, n);
        float[] r = {d[0] - n[0] * k, d[1] - n[1] * k, d[2] - n[2] * k};
        float len = (float) Math.sqrt(dot(r, r));
        return new float[]{r[0] / len, r[1] / len, r[2] / len};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float dot(float[] a, float[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    private static float[] sub(float[] p, float x, float y, float z) { return new float[]{p[0] - x, p[1] - y, p[2] - z}; }

    // ================================================================== the Vanguard

    /** The Vanguard's skin sheet: the standard 64x64 player layout. */
    public static final int VANGUARD_TEX = 64;
    /** Model units per skin pixel. A player is 32 pixels tall, so he stands about 21 units. */
    public static final float FIGURE_SCALE = 0.78f;
    /** Where his feet are, and how far back from the chunk's middle he stands. */
    public static final float FIGURE_FEET_Y = -ICE_H / 2f + 1.6f, FIGURE_Z = -1.5f;
    /** A patch of plain armour for the pauldrons, which are too small for a cross of their own. */
    public static final int[] PAULDRON = {48, 32, 8, 8};

    /**
     * A frozen pose: angles in degrees. Limbs: {@code raise} swings forward towards the
     * camera from hanging straight down, {@code spread} out to the side. {@code lean} tips the
     * upper body back (negative is forward), {@code twist} turns it, and the whole figure can
     * {@code tilt} over sideways, as if he was caught falling.
     */
    public record Pose(String name,
                       float lean, float twist, float tilt,
                       float headPitch, float headYaw, float headRoll,
                       float rightRaise, float rightSpread, float leftRaise, float leftSpread,
                       float rightLeg, float rightLegSpread, float leftLeg, float leftLegSpread) {

        /** The same pose the other way round: left for right, turned the other way. */
        public Pose mirrored() {
            return new Pose(name, lean, -twist, -tilt, headPitch, -headYaw, -headRoll,
                    leftRaise, leftSpread, rightRaise, rightSpread,
                    leftLeg, leftLegSpread, rightLeg, rightLegSpread);
        }
    }

    /**
     * The poses a Vanguard can be frozen in. One is picked per corpse, and may be mirrored,
     * so two corpses in a row rarely look alike. Each was checked to stay inside the ice.
     */
    public static final Pose[] POSES = {
            // Arms thrown up over his face against whatever froze him.
            new Pose("shielding", 6f, 10f, 0f, 14f, 0f, 0f,
                    115f, -28f, 100f, -34f, 16f, 4f, -12f, 4f),
            // One hand reaching up and out, the other trailing - trying to climb out.
            new Pose("reaching", 3f, -12f, 3f, -14f, 8f, 0f,
                    148f, 14f, 12f, 22f, -8f, 6f, 10f, 8f),
            // Caught mid-swing, pick overhead (the pick is long gone).
            new Pose("mid-swing", 3f, 18f, 0f, -12f, -6f, 0f,
                    152f, 8f, 144f, -6f, 22f, 2f, -16f, 3f),
            // Curled against the cold, arms wrapped round himself, head down.
            new Pose("huddled", -5f, -8f, 0f, 20f, 0f, 6f,
                    38f, -30f, 44f, -26f, 26f, -2f, -4f, 3f),
            // Knocked backwards, arms flung wide, one leg kicked up.
            new Pose("falling", 6f, 6f, 4f, 0f, 14f, 12f,
                    60f, 26f, 48f, 22f, 32f, 5f, -6f, 9f),
            // Turned away, one arm up by his head as if bracing for a blow from the side.
            new Pose("bracing", 2f, 26f, -4f, 4f, -24f, -4f,
                    146f, 30f, 18f, 12f, 8f, 5f, -10f, 5f),
    };

    /**
     * The Vanguard in a pose, standing inside the chunk.
     *
     * Built like a player model: every part is a box with a pivot, turned about that pivot,
     * and the upper body - head, arms, pauldrons - turns again with the torso's lean and
     * twist. Written in skin pixels with the feet at the origin, then scaled into the ice.
     */
    public static Mesh vanguard(Pose p) {
        BoxMesh m = new BoxMesh(VANGUARD_TEX, VANGUARD_TEX);
        float[] root = rot(0f, 0f, p.tilt());
        float[] hips = {0f, 12f, 0f};
        float[] body = mul(rot(0f, p.twist(), 0f), rot(-p.lean(), 0f, 0f));

        // Legs hang from the hips and do not follow the torso's lean.
        part(m, root, null, null, new float[]{-2f, 12f, 0f},
                rot(-p.rightLeg(), 0f, -p.rightLegSpread()), -2f, -12f, -2f, 4, 12, 4, 0, 16);
        part(m, root, null, null, new float[]{2f, 12f, 0f},
                rot(-p.leftLeg(), 0f, p.leftLegSpread()), -2f, -12f, -2f, 4, 12, 4, 16, 48);

        // The torso turns about the hips; everything above it turns with it.
        part(m, root, hips, body, hips, IDENTITY, -4f, 0f, -2f, 8, 12, 4, 16, 16);
        part(m, root, hips, body, new float[]{0f, 24f, 0f},
                rot(p.headPitch(), p.headYaw(), p.headRoll()), -4f, 0f, -4f, 8, 8, 8, 0, 0);

        // Arms from the shoulders. The figure faces +Z, so his right arm is on -X.
        float[] rightArm = rot(-p.rightRaise(), 0f, -p.rightSpread());
        float[] leftArm = rot(-p.leftRaise(), 0f, p.leftSpread());
        float[] rs = {-6f, 22f, 0f}, ls = {6f, 22f, 0f};
        part(m, root, hips, body, rs, rightArm, -2f, -10f, -2f, 4, 12, 4, 40, 16);
        part(m, root, hips, body, ls, leftArm, -2f, -10f, -2f, 4, 12, 4, 32, 48);
        // Pauldrons: a slab of armour over each shoulder, a little proud of the arm.
        flatPart(m, root, hips, body, rs, rightArm, -2.6f, -1.8f, -2.6f, 5.2f, 4.4f, 5.2f, PAULDRON);
        flatPart(m, root, hips, body, ls, leftArm, -2.6f, -1.8f, -2.6f, 5.2f, 4.4f, 5.2f, PAULDRON);
        return m.build();
    }

    /** Every corner of the Vanguard in a pose, in model units - for checking he fits the ice. */
    public static List<float[]> vanguardCorners(Pose p) {
        List<float[]> out = new ArrayList<>();
        for (Mesh.Quad q : vanguard(p).quads()) {
            out.add(new float[]{q.ox, q.oy, q.oz});
            out.add(new float[]{q.ox + q.ux, q.oy + q.uy, q.oz + q.uz});
            out.add(new float[]{q.ox + q.vx, q.oy + q.vy, q.oz + q.vz});
            out.add(new float[]{q.ox + q.ux + q.vx, q.oy + q.uy + q.vy, q.oz + q.uz + q.vz});
        }
        return out;
    }

    private static final float[] IDENTITY = {1, 0, 0, 0, 1, 0, 0, 0, 1};

    /**
     * One box of the figure, with the standard skin cross at {@code (tu, tv)}.
     *
     * @param hips  the torso's pivot, or null for a part that does not follow the torso
     * @param body  the torso's turn, or null likewise
     * @param pivot this part's own joint, in figure pixels
     * @param r     this part's turn about that joint
     */
    private static void part(BoxMesh m, float[] root, float[] hips, float[] body, float[] pivot, float[] r,
                             float x, float y, float z, int w, int h, int d, int tu, int tv) {
        int[][] rects = {
                {tu + d, tv + d, w, h},            // front, +Z
                {tu + 2 * d + w, tv + d, w, h},    // back, -Z
                {tu, tv + d, d, h},                // his right, -X
                {tu + d + w, tv + d, d, h},        // his left, +X
                {tu + d, tv, w, d},                // top
                {tu + d + w, tv, w, d},            // bottom
        };
        emitBox(m, root, hips, body, pivot, r, x, y, z, w, h, d, rects);
    }

    /** A box whose every face shows the same patch. */
    private static void flatPart(BoxMesh m, float[] root, float[] hips, float[] body, float[] pivot, float[] r,
                                 float x, float y, float z, float w, float h, float d, int[] rect) {
        int[][] rects = {rect, rect, rect, rect, rect, rect};
        emitBox(m, root, hips, body, pivot, r, x, y, z, w, h, d, rects);
    }

    private static void emitBox(BoxMesh m, float[] root, float[] hips, float[] body, float[] pivot, float[] r,
                                float x, float y, float z, float w, float h, float d, int[][] rect) {
        float x1 = x + w, y1 = y + h, z1 = z + d;
        // Corner, u edge, v edge, normal - with v = u x n on every face.
        float[][] faces = {
                {x, y1, z1, w, 0, 0, 0, -h, 0, 0, 0, 1},
                {x1, y1, z, -w, 0, 0, 0, -h, 0, 0, 0, -1},
                {x, y1, z, 0, 0, d, 0, -h, 0, -1, 0, 0},
                {x1, y1, z1, 0, 0, -d, 0, -h, 0, 1, 0, 0},
                {x, y1, z, w, 0, 0, 0, 0, d, 0, 1, 0},
                {x, y, z1, w, 0, 0, 0, 0, -d, 0, -1, 0},
        };
        for (int i = 0; i < 6; i++) {
            float[] f = faces[i];
            float[] o = place(root, hips, body, pivot, r, f[0], f[1], f[2]);
            float[] u = turn(root, body, r, f[3], f[4], f[5]);
            float[] v = turn(root, body, r, f[6], f[7], f[8]);
            float[] n = turn(root, body, r, f[9], f[10], f[11]);
            float s = FIGURE_SCALE;
            m.quad(o[0], o[1], o[2], u[0] * s, u[1] * s, u[2] * s, v[0] * s, v[1] * s, v[2] * s,
                    n[0], n[1], n[2], rect[i][0], rect[i][1], rect[i][2], rect[i][3]);
        }
    }

    /** A point of a part, from the part's own space to the ice's. */
    private static float[] place(float[] root, float[] hips, float[] body, float[] pivot, float[] r,
                                 float x, float y, float z) {
        float[] p = apply(r, x, y, z);
        p = new float[]{p[0] + pivot[0], p[1] + pivot[1], p[2] + pivot[2]};
        if (body != null) {
            float[] q = apply(body, p[0] - hips[0], p[1] - hips[1], p[2] - hips[2]);
            p = new float[]{q[0] + hips[0], q[1] + hips[1], q[2] + hips[2]};
        }
        p = apply(root, p[0], p[1], p[2]);
        return new float[]{p[0] * FIGURE_SCALE, p[1] * FIGURE_SCALE + FIGURE_FEET_Y, p[2] * FIGURE_SCALE + FIGURE_Z};
    }

    /** A direction of a part, turned but not moved. */
    private static float[] turn(float[] root, float[] body, float[] r, float x, float y, float z) {
        float[] p = apply(r, x, y, z);
        if (body != null) p = apply(body, p[0], p[1], p[2]);
        return apply(root, p[0], p[1], p[2]);
    }

    /** A rotation matrix, row-major: yaw about Y, then pitch about X, then roll about Z. */
    private static float[] rot(float pitchDeg, float yawDeg, float rollDeg) {
        double a = Math.toRadians(pitchDeg), b = Math.toRadians(yawDeg), c = Math.toRadians(rollDeg);
        float[] rx = {1, 0, 0, 0, (float) Math.cos(a), (float) -Math.sin(a), 0, (float) Math.sin(a), (float) Math.cos(a)};
        float[] ry = {(float) Math.cos(b), 0, (float) Math.sin(b), 0, 1, 0, (float) -Math.sin(b), 0, (float) Math.cos(b)};
        float[] rz = {(float) Math.cos(c), (float) -Math.sin(c), 0, (float) Math.sin(c), (float) Math.cos(c), 0, 0, 0, 1};
        return mul(ry, mul(rx, rz));
    }

    private static float[] mul(float[] a, float[] b) {
        float[] o = new float[9];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
            o[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
        }
        return o;
    }

    private static float[] apply(float[] m, float x, float y, float z) {
        return new float[]{m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z};
    }

    // ================================================================== pick

    public static final int PICK_TEX = 64;
    public static final int[] WOOD = {0, 0, 20, 32};
    public static final int[] LEATHER = {20, 0, 8, 32};
    public static final int[] STEEL = {28, 0, 24, 28};
    public static final int[] EDGE = {28, 30, 24, 4};
    public static final int[] DARK = {52, 0, 12, 32};

    /**
     * The point of the striking prong, pivot at the origin. What the swing is aimed with.
     *
     * On +Y, not -Y. The pick comes over the top from the right and lands on the block's
     * upper right, and with the prong on -Y that pose points it back over the swinger's
     * shoulder - the tip still touched the target, because the pivot is solved from it, but
     * the head arrived back-first. The preview's roll table is how that was caught.
     */
    public static final float PICK_TIP_X = 23.2f, PICK_TIP_Y = 17.0f;

    private ThawMeshes() {}

    /**
     * The pickaxe, pivot at the butt of the handle, handle along +X, striking prong at +Y.
     */
    public static Mesh pick() {
        BoxMesh m = new BoxMesh(PICK_TEX, PICK_TEX);

        // ---- handle ------------------------------------------------------
        m.boxFlat(0f, -1.3f, -1.3f, 25.5f, 2.6f, 2.6f, WOOD[0], WOOD[1], WOOD[2], WOOD[3]);
        // Grip wrap near the butt, a touch proud of the wood so the silhouette steps.
        m.boxFlat(0.4f, -1.7f, -1.7f, 7f, 3.4f, 3.4f,
                LEATHER[0], LEATHER[1], LEATHER[2], LEATHER[3]);
        // A pommel, so the handle has an end rather than being cut off.
        m.boxFlat(-0.8f, -2.0f, -2.0f, 1.4f, 4.0f, 4.0f, DARK[0], DARK[1], DARK[2], DARK[3]);

        // ---- the head's collar, where the handle goes through ------------
        m.boxFlat(23.4f, -3.2f, -2.1f, 5.2f, 6.4f, 4.2f, DARK[0], DARK[1], DARK[2], DARK[3]);

        // ---- striking prong, upwards in model space, curving back to the handle ----
        // Three boxes, each narrower and leaning further back than the last. The lean is
        // what makes it read as a curved prong rather than a spike.
        m.skewBoxFlat(24.0f, 3.0f, -1.6f,
                3.9f, 0f, 0f,
                -0.5f, 5.8f, 0f,
                0f, 0f, 3.2f,
                STEEL[0], STEEL[1], STEEL[2], STEEL[3]);
        m.skewBoxFlat(23.8f, 8.6f, -1.3f,
                3.1f, 0f, 0f,
                -0.9f, 4.9f, 0f,
                0f, 0f, 2.6f,
                STEEL[0], STEEL[1], STEEL[2], STEEL[3]);
        m.skewBoxFlat(23.3f, 13.3f, -0.9f,
                2.1f, 0f, 0f,
                -1.1f, 3.7f, 0f,
                0f, 0f, 1.8f,
                EDGE[0], EDGE[1], EDGE[2], EDGE[3]);

        // ---- the back prong: shorter and blunter, the other way ----------
        m.skewBoxFlat(24.0f, -3.0f, -1.6f,
                3.9f, 0f, 0f,
                -0.5f, -5.0f, 0f,
                0f, 0f, 3.2f,
                STEEL[0], STEEL[1], STEEL[2], STEEL[3]);
        m.skewBoxFlat(23.7f, -7.8f, -1.2f,
                2.9f, 0f, 0f,
                -1.0f, -3.6f, 0f,
                0f, 0f, 2.4f,
                STEEL[0], STEEL[1], STEEL[2], STEEL[3]);

        return m.build();
    }
}
