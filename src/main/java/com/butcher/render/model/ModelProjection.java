package com.butcher.render.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@link Mesh} into a depth-sorted list of 2D {@link Face}s the GUI layer can draw.
 *
 * <h2>The projection</h2>
 *
 * Yaw about Y, then pitch about X, then drop Z. No perspective divide - that is the whole
 * trick. Without it a quad's projection stays a parallelogram, which means it can be drawn
 * as an ordinary texture blit through a 2D affine matrix (see {@link Mesh}). Orthographic
 * also happens to be the right look here: it reads as a diorama rather than a camera, which
 * is what the cutscene wants.
 *
 * Depth is kept per face and used for a painter's sort. There is no depth buffer in the GUI
 * layer, so faces are simply drawn back to front. That is exact for convex shapes and good
 * enough for the boxy, well-separated pieces this mod uses.
 *
 * <h2>The shading</h2>
 *
 * Ported from SkyMaxx's ModelProjection, which is where the crisp look of that mod comes
 * from. A key light and a weaker fill light give a continuous brightness, and that
 * brightness is then <em>quantised into six bands</em> rather than used directly. Smooth
 * shading over flat-coloured boxes looks like a render; banded shading looks drawn. Each
 * band gets a smoothstep "shoulder" near its top so the steps do not hard-edge as a face
 * turns.
 *
 * Back faces are darkened rather than culled, so you can see into an open shape - matters
 * for a cut worm segment, where the camera should be able to see the hollow.
 */
public final class ModelProjection {

    // Key light: over the left shoulder, high, slightly in front.
    private static final float KX = -0.42f, KY = 0.62f, KZ = 0.66f;
    // Fill: low and opposite, so downward faces do not go to pure black.
    private static final float FX = 0.25f, FY = -0.85f, FZ = 0.45f;
    private static final float AMBIENT = 0.30f;
    private static final float KEY_WEIGHT = 0.55f;
    private static final float FILL_WEIGHT = 0.30f;

    /** How many discrete shades the continuous lighting collapses to. */
    public static final int BANDS = 6;
    /**
     * The six shades, as multipliers on the texture.
     *
     * The floor is high on purpose. These bands multiply into an already near-black skin -
     * the Scatha is about #2B2B39 at its lightest - so a band at 0.4 takes a face to roughly
     * #111116 and the shape stops reading at all. Against a dark cave that is invisible.
     * Banding a dark texture needs a narrow range near the top, not the full 0-1 sweep that
     * suits a bright one.
     */
    private static final float[] LEVELS = {0.62f, 0.70f, 0.78f, 0.86f, 0.93f, 1.00f};
    /** Fraction of a band spent ramping to the next one; the rest is flat. */
    private static final float SHOULDER = 0.55f;

    /** A face this close to edge-on is invisible and would project to a sliver. */
    private static final float FACING_EPSILON = 0.02f;
    /** Back faces keep this much of their brightness. */
    private static final float INTERIOR_SHADE = 0.55f;

    private ModelProjection() {}

    /**
     * Where the camera is and how it is pointed.
     *
     * <h3>Why a position and not just angles</h3>
     *
     * This used to be three loose floats - yaw, pitch, scale - and a model was placed by
     * its own offset. That is enough to spin something on the spot and nothing else: with
     * no camera position there is no dolly, no deliberate reframing and no parallax,
     * because every shot is locked to the scene origin.
     *
     * SkyMaxx carries a real {@code camX/camY/camZ} and subtracts it from each piece
     * before projecting ({@code BoaScene.snake}), which is what lets its camera push in on
     * the strike. Same idea here: the camera's position is subtracted from every model's
     * offset, so moving the camera moves the whole scene coherently and anything anchored
     * to a world point ({@link #projectPoint}) comes along with it.
     *
     * Immutable, and the {@code with*} helpers return copies, so a shot can be written as
     * a chain from one base camera without anybody holding a mutable camera between frames.
     *
     * @param yawDeg   spin about the vertical axis
     * @param pitchDeg tilt towards the viewer
     * @param size     model units to screen pixels
     * @param x        camera position in world units...
     * @param y        ...
     * @param z        ...
     */
    public record Camera(float yawDeg, float pitchDeg, float rollDeg, float size,
                         float x, float y, float z) {

        /** A camera at the scene origin - what the old three-float call site meant. */
        public static Camera of(float yawDeg, float pitchDeg, float size) {
            return new Camera(yawDeg, pitchDeg, 0f, size, 0f, 0f, 0f);
        }

        public Camera at(float x, float y, float z) {
            return new Camera(yawDeg, pitchDeg, rollDeg, size, x, y, z);
        }

        /** Moves the camera, in world units. */
        public Camera dolly(float dx, float dy, float dz) {
            return new Camera(yawDeg, pitchDeg, rollDeg, size, x + dx, y + dy, z + dz);
        }

        /** Turns the camera without moving it. */
        public Camera turn(float dYawDeg, float dPitchDeg) {
            return new Camera(yawDeg + dYawDeg, pitchDeg + dPitchDeg, rollDeg, size, x, y, z);
        }

        /**
         * Tilts everything in the plane of the screen.
         *
         * <h3>Why this belongs here and not on the GUI pose stack</h3>
         *
         * The cleaver's swing used to be {@code pose.rotate(angle)} around the blade's
         * pivot, wrapped around the flush. It rendered as three hairlines: handle, ferrule
         * and blade each collapsed to a 1-D streak, with the length axis surviving and the
         * cross-section gone. Everything else checked out - mesh, texture, UV rects, depth
         * sort, anchor arithmetic and the projection itself all render a correct cleaver
         * offline - so the fault was in composing our per-face affine matrix underneath a
         * pose that already carried a rotation.
         *
         * Rather than keep guessing at that interaction, the roll moved in here. The
         * projection is orthographic, so a screen-plane tilt is just a 2D rotation of the
         * three vectors a face is already made of, applied after everything else. Same
         * result, no dependency on what the GUI's transform stack does underneath us, and
         * it can be checked without launching the game.
         */
        public Camera roll(float degrees) {
            return new Camera(yawDeg, pitchDeg, rollDeg + degrees, size, x, y, z);
        }

        /** Scales the lens. Orthographic, so this is the only kind of zoom there is. */
        public Camera zoom(float factor) {
            return new Camera(yawDeg, pitchDeg, rollDeg, size * factor, x, y, z);
        }

        /** Eased blend towards {@code other}; {@code t} 0 keeps this one. */
        public Camera lerp(Camera other, float t) {
            if (t <= 0f) return this;
            if (t >= 1f) return other;
            return new Camera(
                    yawDeg + (other.yawDeg - yawDeg) * t,
                    pitchDeg + (other.pitchDeg - pitchDeg) * t,
                    rollDeg + (other.rollDeg - rollDeg) * t,
                    size + (other.size - size) * t,
                    x + (other.x - x) * t,
                    y + (other.y - y) * t,
                    z + (other.z - z) * t);
        }
    }

    /**
     * Projects {@code mesh} and appends its visible faces to {@code out}.
     *
     * @param yawDeg   spin about the vertical axis
     * @param pitchDeg tilt towards the viewer
     * @param size     model units to screen pixels
     * @param offX     offset applied in model space before rotation, so a piece can be moved
     * @param offY     ...
     * @param offZ     ...
     * @param pool     reused Face store; call {@link FacePool#reset()} once per frame
     */
    public static void project(Mesh mesh, float yawDeg, float pitchDeg, float size,
                               float offX, float offY, float offZ,
                               List<Face> out, FacePool pool) {
        project(mesh, 0f, yawDeg, pitchDeg, size, offX, offY, offZ, out, pool);
    }

    /**
     * Projects {@code mesh} and appends its visible faces to {@code out}.
     *
     * @param modelYawDeg spin about the model's OWN origin, applied before it is moved into
     *     place. This is separate from the camera yaw because the offset is added to each
     *     vertex before the camera rotation: folding the two together would swing the piece
     *     around the scene origin instead of turning it where it stands. A worm segment
     *     turning to face along its body needs exactly this and nothing else.
     * @param yawDeg   camera spin about the vertical axis
     * @param pitchDeg camera tilt towards the viewer
     * @param size     model units to screen pixels
     * @param pool     reused Face store; call {@link FacePool#reset()} once per frame
     */
    public static void project(Mesh mesh, float modelYawDeg, float yawDeg, float pitchDeg, float size,
                               float offX, float offY, float offZ,
                               List<Face> out, FacePool pool) {
        project(mesh, Camera.of(yawDeg, pitchDeg, size), modelYawDeg,
                offX, offY, offZ, false, false, out, pool);
    }

    /**
     * Projects {@code mesh} through {@code cam} and appends its visible faces to {@code out}.
     *
     * @param modelYawDeg spin about the model's own origin, before it is moved into place
     * @param offX        where the model sits in the world...
     * @param offY        ...
     * @param offZ        ...
     * @param blanked     draw {@linkplain Mesh.Quad#front front} faces with their
     *     {@linkplain Mesh.Quad#blank blank} rect instead, and drop the ones that have none.
     *     One mesh, two states, no second model.
     * @param cull        skip faces turned away from the camera entirely.
     *
     *     <p>Off by default, and that is deliberate rather than an oversight: back faces are
     *     normally <em>darkened</em> ({@link #INTERIOR_SHADE}) rather than dropped, so you
     *     can see into an open shape - which is the whole point of a worm segment the
     *     cleaver has just opened. Culling is for closed, opaque pieces, where the far side
     *     is never visible and projecting it is wasted work and wasted sort.
     * @param pool        reused Face store; call {@link FacePool#reset()} once per frame
     */
    public static void project(Mesh mesh, Camera cam, float modelYawDeg,
                               float offX, float offY, float offZ,
                               boolean blanked, boolean cull,
                               List<Face> out, FacePool pool) {
        double yaw = Math.toRadians(cam.yawDeg());
        double pitch = Math.toRadians(cam.pitchDeg());
        double my = Math.toRadians(modelYawDeg);
        float size = cam.size();
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float cm = (float) Math.cos(my), sm = (float) Math.sin(my);
        float roll = cam.rollDeg();
        double rollRad = Math.toRadians(roll);
        float cr = (float) Math.cos(rollRad), sr = (float) Math.sin(rollRad);

        // The camera's position is simply subtracted from where the model sits. Both are in
        // world units and the rotation happens afterwards, so this is all "moving the
        // camera" can mean in an orthographic scene.
        offX -= cam.x();
        offY -= cam.y();
        offZ -= cam.z();

        for (Mesh.Quad q : mesh.quads()) {
            int u0 = q.u0, v0 = q.v0, uw = q.uw, vh = q.vh;
            if (blanked && q.front) {
                if (q.blank == null) continue;
                u0 = q.blank[0]; v0 = q.blank[1]; uw = q.blank[2]; vh = q.blank[3];
            }
            // Model-space spin first. Position, both edges and the normal all turn; only
            // the position is translated afterwards, which is the whole point of doing
            // this before the offset rather than after.
            float qox = q.ox * cm + q.oz * sm, qoz = -q.ox * sm + q.oz * cm;
            float qux = q.ux * cm + q.uz * sm, quz = -q.ux * sm + q.uz * cm;
            float qvx = q.vx * cm + q.vz * sm, qvz = -q.vx * sm + q.vz * cm;
            float qnx = q.nx * cm + q.nz * sm, qnz = -q.nx * sm + q.nz * cm;

            // Rotate the normal far enough to know whether this face is visible, and
            // skip it before doing any of the more expensive work if it is edge-on.
            float nz1 = -qnx * sy + qnz * cy;
            float nz2 = -q.ny * sp + nz1 * cp;
            if (Math.abs(nz2) <= FACING_EPSILON) continue;

            boolean interior = nz2 < 0f;
            if (cull && interior) continue;

            Face face = pool.take(out);
            face.interior = interior;
            face.facing = Math.abs(nz2);
            face.front = q.front && !interior;
            face.u0 = u0; face.v0 = v0; face.uw = uw; face.vh = vh;

            // Origin: translate in world space, then yaw, then pitch, then drop Z.
            float ox = (qox + offX) * cy + (qoz + offZ) * sy;
            float oz = -(qox + offX) * sy + (qoz + offZ) * cy;
            float oy = q.oy + offY;
            face.ox = ox * size;
            face.oy = -(oy * cp + oz * sp) * size;

            // Edges are directions, so they rotate but do not translate.
            float ux = qux * cy + quz * sy;
            float uz = -qux * sy + quz * cy;
            face.ux = ux * size;
            face.uy = -(q.uy * cp + uz * sp) * size;

            float vx = qvx * cy + qvz * sy;
            float vz = -qvx * sy + qvz * cy;
            face.vx = vx * size;
            face.vy = -(q.vy * cp + vz * sp) * size;

            // Depth of the face centre, for the painter's sort. Larger is nearer.
            float cxm = qox + (qux + qvx) * 0.5f + offX;
            float cym = q.centreY() + offY;
            float czm = qoz + (quz + qvz) * 0.5f + offZ;
            float cz = -cxm * sy + czm * cy;
            face.depth = -cym * sp + cz * cp;

            // Screen-plane tilt, last. The projection is orthographic, so rolling the
            // camera is exactly a 2D rotation of the corner and the two edges - nothing
            // about depth, shading or facing changes, which is why it can be done here
            // rather than as a third axis in the rotation above.
            if (roll != 0f) {
                float ox2 = face.ox * cr - face.oy * sr, oy2 = face.ox * sr + face.oy * cr;
                float ux2 = face.ux * cr - face.uy * sr, uy2 = face.ux * sr + face.uy * cr;
                float vx2 = face.vx * cr - face.vy * sr, vy2 = face.vx * sr + face.vy * cr;
                face.ox = ox2; face.oy = oy2;
                face.ux = ux2; face.uy = uy2;
                face.vx = vx2; face.vy = vy2;
            }

            // Make the projected parallelogram wind forwards.
            //
            // A quad whose projected edges give a negative determinant is back-facing to
            // the GPU and is never rasterised, however correct its geometry, texture and
            // depth are. That is what made the cleaver hollow: 12 of its 18 surviving
            // faces wound backwards, including the blade at 5785 px^2, while the only
            // ones that wound forwards were the thin side strips - so all that appeared
            // was a wireframe of the blade's own edges.
            //
            // Whether a box's faces wind forwards depends on the order its edges were
            // given in. PlayerHeadMesh happens to get it right everywhere (0 backwards
            // faces, which is why the worm never had this problem) and BoxMesh's skewed
            // boxes get it wrong for one pair. Fixing it per builder would leave the
            // trap armed for the next mesh anyone writes, so it is corrected here for
            // every mesh: reverse the u edge and start from the far corner, which is the
            // same parallelogram wound the other way.
            //
            // The cost is that the face's texture is mirrored along u. For the flat
            // material patches this affects that is invisible, and a mirrored texture is
            // in any case strictly better than no face at all.
            if (face.ux * face.vy - face.uy * face.vx < 0f) {
                face.ox += face.ux;
                face.oy += face.uy;
                face.ux = -face.ux;
                face.uy = -face.uy;
            }

            float wnx = qnx * cy + qnz * sy;
            shade(face, wnx, q.ny, nz1, interior);
        }
    }

    /** Convenience overload with no positional offset. */
    public static void project(Mesh mesh, float yawDeg, float pitchDeg, float size,
                               List<Face> out, FacePool pool) {
        project(mesh, yawDeg, pitchDeg, size, 0f, 0f, 0f, out, pool);
    }

    /**
     * Projects a single world point to screen offsets, using the same camera as
     * {@link #project}.
     *
     * Needed because anything positioned in screen space but anchored to something in the
     * scene - the cleaver's pivot sitting on the segment it is about to cut - has to move
     * with the camera. Multiplying the world x by the scale is only right at yaw 0.
     *
     * @return {x, y} offsets from the scene origin, in pixels
     */
    public static float[] projectPoint(float x, float y, float z,
                                       float yawDeg, float pitchDeg, float size) {
        return projectPoint(x, y, z, Camera.of(yawDeg, pitchDeg, size));
    }

    /** As above, through a camera that may also have moved. */
    public static float[] projectPoint(float x, float y, float z, Camera cam) {
        double yaw = Math.toRadians(cam.yawDeg());
        double pitch = Math.toRadians(cam.pitchDeg());
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float wx = x - cam.x(), wy = y - cam.y(), wz = z - cam.z();
        float rx = wx * cy + wz * sy;
        float rz = -wx * sy + wz * cy;
        float px = rx * cam.size();
        float py = -(wy * cp + rz * sp) * cam.size();
        if (cam.rollDeg() != 0f) {
            double r = Math.toRadians(cam.rollDeg());
            float c = (float) Math.cos(r), s2 = (float) Math.sin(r);
            return new float[]{px * c - py * s2, px * s2 + py * c};
        }
        return new float[]{px, py};
    }

    /**
     * Back-to-front, so nearer faces are drawn last and cover what is behind them.
     * The GUI layer has no depth buffer, so this ordering is the only thing keeping
     * the model from turning inside out.
     */
    public static void sortByDepth(List<Face> faces) {
        faces.sort((a, b) -> Float.compare(a.depth, b.depth));
    }

    private static void shade(Face face, float nx, float ny, float nz, boolean interior) {
        float key = (nx * KX + ny * KY + nz * KZ) * 0.5f + 0.5f;   // remapped to 0..1, never fully dark
        float fill = Math.max(0f, nx * FX + ny * FY + nz * FZ);
        float lit = AMBIENT + KEY_WEIGHT * key + FILL_WEIGHT * fill;
        if (interior) lit *= INTERIOR_SHADE;
        lit = clamp01(lit);
        face.tone = lit;

        // Quantise. The fractional position inside a band is flat for the first
        // (1 - SHOULDER) of it and then smoothsteps into the next level, which keeps
        // the banding readable without letting it crawl as a face slowly turns.
        float scaled = lit * BANDS;
        int band = (int) scaled;
        if (band >= BANDS) band = BANDS - 1;
        float within = scaled - band;
        float t = clamp01((within - (1f - SHOULDER)) / SHOULDER);
        t = t * t * (3f - 2f * t);
        float low = LEVELS[band];
        float high = LEVELS[Math.min(BANDS - 1, band + 1)];
        face.band = band;
        face.brightness = low + (high - low) * t;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /**
     * One quad, projected. {@code (ox,oy)} is the corner and {@code (ux,uy)}/{@code (vx,vy)}
     * are the projected edges - i.e. exactly the six numbers of a 2D affine matrix that maps
     * the unit square onto this parallelogram.
     */
    public static final class Face {
        public float ox, oy;
        public float ux, uy;
        public float vx, vy;
        public int u0, v0, uw, vh;
        public float depth;
        /** Quantised 0..1 multiplier to apply to the texture. */
        public float brightness;
        /** Which of the {@link #BANDS} bands this landed in. */
        public int band;
        /** Pre-quantisation lighting, if something wants a smooth value. */
        public float tone;
        /** True when this is the back side of a quad, seen from inside the shape. */
        public boolean interior;
        /** True when this is a {@linkplain Mesh.Quad#front front} face and we are seeing it. */
        public boolean front;
        /** |cos| between the normal and the view direction; 0 is edge-on. */
        public float facing;
    }

    /**
     * Reused Face store. The cutscene projects a few hundred faces every frame at 60+fps;
     * allocating those fresh each time would be pure garbage. Taken from SkyMaxx, which
     * does the same thing for the same reason.
     */
    public static final class FacePool {
        private final ArrayList<Face> store = new ArrayList<>();
        private int used;

        /** Call once per frame, before any projecting. */
        public void reset() { used = 0; }

        Face take(List<Face> out) {
            if (used == store.size()) store.add(new Face());
            Face face = store.get(used++);
            face.interior = false;
            face.front = false;
            out.add(face);
            return face;
        }
    }
}
