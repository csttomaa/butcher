import com.butcher.render.art.PropArt;
import com.butcher.render.model.CleaverMesh;
import com.butcher.render.model.Mesh;
import com.butcher.render.model.ModelProjection;
import com.butcher.render.model.ModelProjection.Camera;
import com.butcher.render.model.ModelProjection.Face;
import com.butcher.render.model.ModelProjection.FacePool;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Draws the mod's icon from the mod's own assets.
 *
 * The cleaver here is the real {@link CleaverMesh} put through the real
 * {@link ModelProjection} and painted with the real {@link PropArt#cleaver()} sheet -
 * not a picture of one. So the icon cannot drift away from what the mod actually draws,
 * and re-running this after changing the blade updates it.
 *
 * Run from the project root:
 *   javac -d out src/main/java/com/butcher/render/model/{Mesh,BoxMesh,ModelProjection,CleaverMesh,CrystalMesh}.java \
 *                src/main/java/com/butcher/render/art/{Paint,PropArt}.java tools/IconGen.java
 *   java -cp out IconGen src/main/resources/assets/butcher/icon.png
 */
public final class IconGen {

    private static final int SIZE = 256;

    // Cave slate, so the icon sits in the same world the cutscene does.
    private static final Color DEEP = new Color(0x141821);
    private static final Color MID = new Color(0x27303F);
    private static final Color RIM = new Color(0x39455A);

    static BufferedImage tex;

    public static void main(String[] args) throws Exception {
        tex = PropArt.cleaver();
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        background(g);
        slash(g);
        cleaver(g);
        vignette(g);
        g.dispose();

        ImageIO.write(img, "PNG", new File(args[0]));
        System.out.println("wrote " + args[0] + " (" + SIZE + "x" + SIZE + ")");
    }

    /** A lit hollow: brighter behind the blade, falling away to the corners. */
    private static void background(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, MID, 0, SIZE, DEEP));
        g.fillRect(0, 0, SIZE, SIZE);

        // A soft pool of light where the cleaver will sit, so the subject is not simply
        // pasted onto a flat field.
        g.setPaint(new RadialGradientPaint(
                new java.awt.geom.Point2D.Float(SIZE * 0.44f, SIZE * 0.44f), SIZE * 0.52f,
                new float[]{0f, 1f},
                new Color[]{new Color(0x4A5A74), new Color(0x004A5A74, true)}));
        g.fillRect(0, 0, SIZE, SIZE);

        // Stone grain. Coarse and low-contrast: at icon size anything finer turns to
        // noise and anything stronger competes with the blade.
        Random rng = new Random(20260921L);
        for (int i = 0; i < 520; i++) {
            int x = rng.nextInt(SIZE), y = rng.nextInt(SIZE);
            int r = 3 + rng.nextInt(9);
            int a = 6 + rng.nextInt(12);
            g.setColor(new Color(RIM.getRed(), RIM.getGreen(), RIM.getBlue(), a));
            g.fill(new Ellipse2D.Float(x - r, y - r * 0.6f, r * 2, r * 1.2f));
        }
    }

    /**
     * The cut mark, behind the blade.
     *
     * The same near-vertical lean the cutscene deals at random, fixed at the middle of
     * that range - it is the mod's one graphic idea, and an icon should show what the
     * thing does, not only what it contains.
     */
    private static void slash(Graphics2D g) {
        double angle = Math.toRadians(-108);
        g.translate(SIZE * 0.54, SIZE * 0.46);
        g.rotate(angle);
        for (int pass = 0; pass < 3; pass++) {
            float len = SIZE * (1.24f - pass * 0.08f);
            float thick = 40f - pass * 15f;
            int alpha = 40 + pass * 72;
            g.setPaint(new GradientPaint(-len / 2, 0, new Color(255, 255, 255, 0),
                    0, 0, new Color(255, 250, 225, alpha), true));
            g.fill(new java.awt.geom.Ellipse2D.Float(-len / 2, -thick / 2, len, thick));
        }
        g.rotate(-angle);
        g.translate(-SIZE * 0.56, -SIZE * 0.50);
    }

    /** The real mesh, through the real projection. */
    private static void cleaver(Graphics2D g) {
        Mesh mesh = CleaverMesh.build();
        FacePool pool = new FacePool();
        List<Face> faces = new ArrayList<>();
        // Angled like a blade caught mid-swing rather than laid out flat.
        Camera cam = Camera.of(14f, -18f, 5.15f).roll(-36f);
        ModelProjection.project(mesh, cam, 0f, 0, 0, 0, false, true, faces, pool);
        faces.sort((a, b) -> Float.compare(a.depth, b.depth));

        float cx = SIZE * 0.275f, cy = SIZE * 0.685f;

        // The same screen-space silhouette the cutscene uses, so the blade separates
        // from the background at small sizes the way it does in game.
        for (float[] o : new float[][]{{-1,0},{1,0},{0,-1},{0,1},{-.7f,-.7f},{.7f,-.7f},{-.7f,.7f},{.7f,.7f}}) {
            for (Face f : faces) face(g, f, cx + o[0] * 3.2f, cy + o[1] * 3.2f, 0f, true);
        }
        for (Face f : faces) face(g, f, cx, cy, f.brightness, false);
    }

    /** One projected quad, drawn through its affine transform - as MeshRenderer does. */
    private static void face(Graphics2D g, Face f, float cx, float cy, float brightness, boolean ink) {
        if (f.uw <= 0 || f.vh <= 0) return;
        BufferedImage sub = tex.getSubimage(f.u0, f.v0, f.uw, f.vh);
        BufferedImage lit = new BufferedImage(sub.getWidth(), sub.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < sub.getHeight(); y++) {
            for (int x = 0; x < sub.getWidth(); x++) {
                int p = sub.getRGB(x, y);
                int a = p >>> 24;
                if (ink) { lit.setRGB(x, y, a << 24); continue; }
                int r = (int) (((p >> 16) & 255) * brightness);
                int gg = (int) (((p >> 8) & 255) * brightness);
                int b = (int) ((p & 255) * brightness);
                lit.setRGB(x, y, a << 24 | r << 16 | gg << 8 | b);
            }
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(lit, new AffineTransform(f.ux / f.uw, f.uy / f.uw, f.vx / f.vh, f.vy / f.vh,
                f.ox + cx, f.oy + cy), null);
    }

    /** Darkened corners, so the icon reads as a shape rather than a filled square. */
    private static void vignette(Graphics2D g) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float fx = (x + 0.5f) / SIZE * 2f - 1f;
                float fy = (y + 0.5f) / SIZE * 2f - 1f;
                float r = (float) Math.sqrt(fx * fx + fy * fy) / 1.41421f;
                float t = (r - 0.42f) / 0.58f;
                if (t <= 0f) continue;
                t = Math.min(1f, t);
                int a = Math.round(t * t * 150);
                g.setColor(new Color(0, 0, 0, a));
                g.fillRect(x, y, 1, 1);
            }
        }
    }
}
