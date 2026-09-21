package com.butcher.render;

import com.butcher.render.model.Mesh;
import com.butcher.render.model.ModelProjection;
import com.butcher.render.model.ModelProjection.Camera;
import com.butcher.render.model.ModelProjection.Face;
import com.butcher.render.model.ModelProjection.FacePool;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws projected {@link Mesh}es through the ordinary GUI blit.
 *
 * <h2>The affine blit</h2>
 *
 * {@link GuiGraphicsExtractor#pose()} is a {@link Matrix3x2fStack} - a <em>2D</em>
 * transform - and this project long read that as "the GUI cannot do 3D", which is why the
 * cleaver's rotation is baked into fourteen frames. But a 2D affine map takes a rectangle
 * to any parallelogram, and an orthographic projection of a flat quad is exactly a
 * parallelogram. So:
 *
 * <pre>
 *   pose.pushMatrix();
 *   pose.mul(new Matrix3x2f(ux/uw, uy/uw, vx/vh, vy/vh, ox, oy));
 *   blit(..., 0, 0, u0, v0, uw, vh, texW, texH, tint);
 *   pose.popMatrix();
 * </pre>
 *
 * draws the quad's texture rect rotated, sheared and scaled into place. Nothing exotic -
 * the same blit call used everywhere else in this mod, with a matrix in front of it.
 * Borrowed from SkyMaxx, which renders its whole tiki totem and Titanoboa this way.
 *
 * <h2>Why models are queued rather than drawn</h2>
 *
 * {@link #add} only records; all the projecting happens in {@link #flush}. That buys two
 * things. Every face from every model is sorted together before anything is drawn, which
 * matters because the GUI layer has no depth buffer and back-to-front ordering across the
 * whole scene is the only thing stopping a near segment being painted over by a far one.
 * And the same queue can be replayed more than once - which is how the outline works.
 *
 * <h2>The outline</h2>
 *
 * A Scatha is nearly black, and against a dark cave it disappears. The fix is the oldest
 * one there is: draw the whole silhouette in black a few pixels out in each direction
 * first, then the model on top.
 *
 * The offsets are applied in <em>screen</em> space rather than by scaling the model up,
 * which matters for something as long as a worm - scaling expands about the origin, so the
 * outline would be hairline at the head and fat at the tail. Eight offsets rather than
 * four, because at four the diagonals of a rotating box show visible notches.
 */
public final class MeshRenderer {

    /** Where the silhouette is stamped, in units of the outline width. */
    private static final float[][] OUTLINE_OFFSETS = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1},
            {-0.71f, -0.71f}, {0.71f, -0.71f}, {-0.71f, 0.71f}, {0.71f, 0.71f},
    };

    private record Queued(Mesh mesh, String texture, float modelYaw, Camera cam,
                          float offX, float offY, float offZ, int tint,
                          boolean outlined, boolean blanked, boolean cull) {}

    private final List<Queued> queue = new ArrayList<>();

    /**
     * The camera the next {@link #add} calls will use.
     *
     * Held here rather than passed per model because a scene has one camera and repeating
     * it at every call site is how the pieces of a shot drift apart. {@link #begin} resets
     * it to a plain origin camera, so forgetting to set one cannot silently inherit the
     * last frame's.
     */
    private Camera camera = Camera.of(0f, 0f, 1f);

    // Per-frame scratch, reused so a 60fps cutscene allocates nothing.
    private final FacePool pool = new FacePool();
    private final List<Face> faces = new ArrayList<>();
    private final List<Queued> owners = new ArrayList<>();
    private final List<Integer> order = new ArrayList<>();
    private final Matrix3x2f matrix = new Matrix3x2f();

    /** Drops everything queued. Call once at the start of each frame. */
    public void begin() {
        queue.clear();
        camera = Camera.of(0f, 0f, 1f);
    }

    /** Sets the camera every following {@link #add} is projected through. */
    public void camera(Camera cam) {
        this.camera = cam;
    }

    /** The camera in force, so a caller can anchor screen-space art to a world point. */
    public Camera camera() {
        return camera;
    }

    /**
     * Queues one model.
     *
     * @param texture name registered with {@link TextureBank}
     * @param tint    ARGB multiplied into the texture, on top of the face's own shading.
     *                {@code 0xFFFFFFFF} for none; the alpha is what fades a piece out.
     */
    public void add(Mesh mesh, String texture, float yawDeg, float pitchDeg, float size,
                    float offX, float offY, float offZ, int tint) {
        add(mesh, texture, 0f, yawDeg, pitchDeg, size, offX, offY, offZ, tint, true);
    }

    /**
     * @param outlined false to leave this model out of the silhouette pass.
     *
     *     The outline is eight offset copies of the whole shape, so it fills the interior
     *     as well as ringing the edge. Behind an opaque model that fill is invisible;
     *     behind a translucent one - a gem crystal - it shows straight through and turns it
     *     into a black lump. Anything see-through opts out.
     */
    public void add(Mesh mesh, String texture, float yawDeg, float pitchDeg, float size,
                    float offX, float offY, float offZ, int tint, boolean outlined) {
        add(mesh, texture, 0f, yawDeg, pitchDeg, size, offX, offY, offZ, tint, outlined);
    }

    /**
     * @param modelYawDeg spin about this model's own origin, before it is moved into place -
     *     how a worm segment turns to face along its body without orbiting the scene.
     */
    public void add(Mesh mesh, String texture, float modelYawDeg, float yawDeg, float pitchDeg, float size,
                    float offX, float offY, float offZ, int tint, boolean outlined) {
        queue.add(new Queued(mesh, texture, modelYawDeg,
                new Camera(yawDeg, pitchDeg, camera.rollDeg(), size, camera.x(), camera.y(), camera.z()),
                offX, offY, offZ, tint, outlined, false, false));
    }

    /**
     * Queues one model through the renderer's own {@linkplain #camera(Camera) camera}.
     *
     * @param blanked  draw front faces with their blank rect instead - a cut segment
     *                 showing bare flesh rather than an eye
     * @param cull     drop faces turned away from the camera. Safe only for closed, opaque
     *                 pieces: an opened worm segment needs its far wall to stay.
     */
    public void add(Mesh mesh, String texture, float modelYawDeg,
                    float offX, float offY, float offZ, int tint,
                    boolean outlined, boolean blanked, boolean cull) {
        queue.add(new Queued(mesh, texture, modelYawDeg, camera,
                offX, offY, offZ, tint, outlined, blanked, cull));
    }

    /** Sorts every queued face back to front and draws them at {@code (originX, originY)}. */
    public void flush(GuiGraphicsExtractor g, float originX, float originY) {
        flush(g, originX, originY, 0, 0f);
    }

    /**
     * As {@link #flush(GuiGraphicsExtractor, float, float)}, preceded by a silhouette pass.
     *
     * @param outlineArgb  colour of the outline; alpha 0 skips the pass entirely
     * @param outlineWidth how far out to stamp it, in screen pixels
     */
    public void flush(GuiGraphicsExtractor g, float originX, float originY,
                      int outlineArgb, float outlineWidth) {
        if (queue.isEmpty()) return;

        project();
        if (faces.isEmpty()) return;
        sort();

        if (outlineWidth > 0f && (outlineArgb >>> 24) != 0) {
            for (float[] o : OUTLINE_OFFSETS) {
                draw(g, originX + o[0] * outlineWidth, originY + o[1] * outlineWidth, outlineArgb);
            }
        }
        draw(g, originX, originY, 0);
    }

    private void project() {
        pool.reset();
        faces.clear();
        owners.clear();
        for (Queued q : queue) {
            int before = faces.size();
            ModelProjection.project(q.mesh(), q.cam(), q.modelYaw(),
                    q.offX(), q.offY(), q.offZ(), q.blanked(), q.cull(), faces, pool);
            for (int i = before; i < faces.size(); i++) owners.add(q);
        }
    }

    private void sort() {
        order.clear();
        for (int i = 0; i < faces.size(); i++) order.add(i);
        order.sort((a, b) -> Float.compare(faces.get(a).depth, faces.get(b).depth));
    }

    /** @param forceArgb non-zero to draw every face in that flat colour (the outline pass). */
    private void draw(GuiGraphicsExtractor g, float originX, float originY, int forceArgb) {
        if (forceArgb == 0) drawn = 0;
        Matrix3x2fStack pose = g.pose();
        for (int idx : order) {
            Face f = faces.get(idx);
            if (f.uw <= 0 || f.vh <= 0) continue;

            Queued q = owners.get(idx);
            if (forceArgb != 0 && !q.outlined()) continue;
            Identifier id = TextureBank.id(q.texture());
            if (id == null) continue;

            // The silhouette is drawn through the same texture, so anything transparent in
            // the skin stays transparent in the outline and the shape stays exact.
            int argb = forceArgb != 0 ? withAlphaOf(forceArgb, q.tint()) : shadeTint(q.tint(), f.brightness);
            if ((argb >>> 24) == 0) continue;

            // The three projected vectors ARE the affine transform: the texture rect's
            // width maps onto (ux,uy), its height onto (vx,vy), from corner (ox,oy).
            matrix.set(f.ux / f.uw, f.uy / f.uw,
                       f.vx / f.vh, f.vy / f.vh,
                       f.ox + originX, f.oy + originY);
            if (forceArgb == 0) drawn++;
            pose.pushMatrix();
            pose.mul(matrix);
            if (debugSolid && forceArgb == 0) {
                g.fill(0, 0, f.uw, f.vh, DEBUG_COLOURS[idx % DEBUG_COLOURS.length]);
            } else {
                g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0,
                        f.u0, f.v0, f.uw, f.vh,
                        TextureBank.width(q.texture()), TextureBank.height(q.texture()), argb);
            }
            pose.popMatrix();
        }
    }

    /** Outline colour, faded along with whatever the piece itself is faded to. */
    private static int withAlphaOf(int outline, int tint) {
        int a = (outline >>> 24 & 0xFF) * (tint >>> 24 & 0xFF) / 255;
        return a << 24 | (outline & 0xFFFFFF);
    }

    /** Folds the face's cel-shaded brightness into the caller's tint, leaving alpha alone. */
    private static int shadeTint(int argb, float brightness) {
        int a = argb >>> 24 & 0xFF;
        int r = (int) ((argb >> 16 & 0xFF) * brightness);
        int g = (int) ((argb >> 8 & 0xFF) * brightness);
        int b = (int) ((argb & 0xFF) * brightness);
        return a << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    /** How many models are queued. */
    public int size() {
        return queue.size();
    }

    /**
     * How many faces the last {@link #flush} actually blitted, outline pass excluded.
     *
     * Purely diagnostic. "The geometry projects to a sensible size" and "the geometry
     * reached the screen" are different claims, and only the second one explains
     * something being invisible.
     */
    public int lastDrawn() {
        return drawn;
    }

    /**
     * Draw every face as a flat colour instead of a texture.
     *
     * One experiment that splits the remaining possibilities in half. The cleaver's
     * thin side faces draw and its large flat ones do not, with the same mesh, the same
     * verified-opaque texture and twelve faces reported as blitted - so either the
     * geometry those faces describe is wrong, or the blit of them is. Filling goes
     * through the identical pose and matrix and touches no texture at all: if the blade
     * fills in, the geometry is fine and the blit is at fault; if it stays hollow, the
     * geometry is.
     *
     * {@code g.fill} is transformed by the pose exactly as the blit is, so this really
     * does draw the same parallelogram rather than an approximation of it.
     */
    public static boolean debugSolid;

    /** Distinct colours per face index, so overlapping faces can be told apart. */
    private static final int[] DEBUG_COLOURS = {
            0xFFE05050, 0xFF50E050, 0xFF5070E0, 0xFFE0C040,
            0xFFE050C0, 0xFF40D0D0, 0xFFE09040, 0xFF9060E0,
    };

    private int drawn;
}
