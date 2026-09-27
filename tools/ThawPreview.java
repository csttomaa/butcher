import com.butcher.render.art.PropArt;
import com.butcher.render.art.ThawArt;
import com.butcher.render.model.Mesh;
import com.butcher.render.model.ModelProjection;
import com.butcher.render.model.ModelProjection.Camera;
import com.butcher.render.model.ModelProjection.Face;
import com.butcher.render.model.ModelProjection.FacePool;
import com.butcher.render.model.ThawMeshes;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders frames of the Thaw offline, through the real meshes, the real projection and the
 * real textures - so the look can be judged without launching the game.
 *
 * The same idea as IconGen: nothing here is a picture of the scene, it IS the scene, drawn
 * through Java2D instead of the GUI blit. Faces are drawn with nearest-neighbour sampling
 * because that is what the game does with these textures.
 *
 *   javac -d out <ThawMeshes, Mesh, BoxMesh, ModelProjection, ThawArt, PropArt, Paint, CrystalMesh> tools/ThawPreview.java
 *   java -cp out ThawPreview <outdir>
 */
public final class ThawPreview {

    static int W = 640, H = 360;   // a 1080p screen at GUI scale 3; 4:3 via -Dwide=false

    // Scene layout, as fractions of the screen - the same numbers ThawScreen uses.
    static final float ICE_CY = 0.42f, ICE_ON_SCREEN = 0.46f, CAPTION_Y = 0.77f, TRAY_Y = 0.885f;
    static final float YAW = ThawMeshes.VIEW_YAW, PITCH = ThawMeshes.VIEW_PITCH;

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : ".";
        BufferedImage[] ice = new BufferedImage[ThawArt.STAGES];
        for (int s = 0; s < ThawArt.STAGES; s++) ice[s] = ThawArt.ice(0, s);
        BufferedImage corpse = ThawArt.corpse(), pick = ThawArt.pick(), glow = PropArt.gemGlow();

        if ("false".equals(System.getProperty("wide"))) W = 480;
        if (Integer.getInteger("h") != null) { H = Integer.getInteger("h"); W = H * 16 / 9; }
        float size = H * ICE_ON_SCREEN * Math.min(1f, W / (float) H / 1.78f) / ThawMeshes.ICE_H;
        // Centred on the drawn box, as ThawScreen does, then pushed in to the end-of-run zoom.
        Camera rest = Camera.of(YAW, PITCH, size);
        float minX = 1e9f, maxX = -1e9f, minY = 1e9f, maxY = -1e9f;
        for (float[] vtx : ThawMeshes.iceVertices()) {
            float[] q = ModelProjection.projectPoint(vtx[0], vtx[1], vtx[2], rest);
            minX = Math.min(minX, q[0]); maxX = Math.max(maxX, q[0]); minY = Math.min(minY, q[1]); maxY = Math.max(maxY, q[1]);
        }
        float cx = W / 2f - (minX + maxX) / 2f, cy = H * ICE_CY - (minY + maxY) / 2f;
        Camera cam = Camera.of(YAW, PITCH, size * 1.07f);

        // Where the pick lands, in screen space: the strike point on the front face.
        float[] t = ModelProjection.projectPoint(ThawMeshes.STRIKE_X, ThawMeshes.STRIKE_Y, ThawMeshes.STRIKE_Z, cam);
        float targetX = cx + t[0], targetY = cy + t[1];
        System.out.printf("strike target on screen: %.0f, %.0f%n", targetX, targetY);

        // ---- find a pick layout: for each strike angle, where would the pivot have to be?
        float pickSize = size * 0.80f;
        System.out.println("roll   pivot(x,y)     handle points   prong points");
        for (int roll = -180; roll < 180; roll += 15) {
            Camera pc = Camera.of(-20f, -8f, pickSize).roll(roll);
            float[] tip = ModelProjection.projectPoint(ThawMeshes.PICK_TIP_X, ThawMeshes.PICK_TIP_Y, 0, pc);
            float[] mid = ModelProjection.projectPoint(12f, 0, 0, pc);
            float[] collar = ModelProjection.projectPoint(26f, 0, 0, pc);
            float px = targetX - tip[0], py = targetY - tip[1];
            System.out.printf("%5d  (%5.0f,%5.0f)   (%5.1f,%5.1f)   (%5.1f,%5.1f)%n", roll, px, py,
                    mid[0] / 12f / pickSize, mid[1] / 12f / pickSize,
                    (tip[0] - collar[0]) / 17f / pickSize, (tip[1] - collar[1]) / 17f / pickSize);
        }

        float strike = Float.parseFloat(System.getProperty("strike", "-128"));
        float raised = Float.parseFloat(System.getProperty("raised", "-38"));
        Camera strikeCam = Camera.of(-20f, -8f, pickSize).roll(strike);
        float[] tip = ModelProjection.projectPoint(ThawMeshes.PICK_TIP_X, ThawMeshes.PICK_TIP_Y, 0, strikeCam);
        float pivotX = targetX - tip[0], pivotY = targetY - tip[1];

        frame(out + "/thaw_1_settle.png", ice[0], corpse, pick, glow, cam, cx, cy, pickSize,
                raised, pivotX, pivotY, 0, 0.18f);
        frame(out + "/thaw_2_flare.png", ice[3], corpse, pick, glow, cam, cx, cy, pickSize,
                raised - 20, pivotX, pivotY, 0xFFFFAA00, 1.0f);
        frame(out + "/thaw_3_strike.png", ice[5], corpse, pick, glow, cam, cx, cy, pickSize,
                strike, pivotX, pivotY, 0xFFAA55FF, 0.55f);
        frame(out + "/thaw_4_late.png", ice[7], corpse, pick, glow, cam, cx, cy, pickSize,
                strike, pivotX, pivotY, 0xFF6688AA, 0.30f);
        System.out.println("wrote 4 frames to " + out);

        // ---- every pose, and whether any of it pokes out of the ice
        BufferedImage sheet = new BufferedImage(W * 3, H * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = sheet.createGraphics();
        for (int i = 0; i < ThawMeshes.POSES.length; i++) {
            ThawMeshes.Pose pose = ThawMeshes.POSES[i];
            for (ThawMeshes.Pose p : new ThawMeshes.Pose[]{pose, pose.mirrored()}) {
                int outside = 0;
                float[] worst = null;
                for (float[] c : ThawMeshes.vanguardCorners(p)) {
                    if (!ThawMeshes.insideIce(c[0], c[1], c[2], 0.4f)) {
                        outside++;
                        worst = c;
                    }
                }
                System.out.printf("pose %-10s%s corners outside the ice: %d%s%n", p.name(), p == pose ? "" : " (mirrored)", outside,
                        worst == null ? "" : String.format("  e.g. at %.1f, %.1f, %.1f", worst[0], worst[1], worst[2]));
            }
            figure = ThawMeshes.vanguard(pose);
            String tmp = out + "/pose_tmp.png";
            frame(tmp, ice[Integer.getInteger("stage", 5)], corpse, pick, glow, cam, cx, cy, pickSize,
                    raised, pivotX, pivotY, 0, 0.18f);
            sg.drawImage(ImageIO.read(new File(tmp)), (i % 3) * W, (i / 3) * H, null);
            sg.setColor(Color.WHITE);
            sg.drawString(pose.name(), (i % 3) * W + 8, (i / 3) * H + 16);
            new File(tmp).delete();
        }
        sg.dispose();
        ImageIO.write(sheet, "PNG", new File(out + "/poses.png"));
        System.out.println("wrote poses.png");

    }

    /** The tint multiplied into the ice this frame, as ThawScreen does with the flare. */
    static int iceTint = 0xFFFFFF;

    static void frame(String path, BufferedImage iceTex, BufferedImage corpseTex, BufferedImage pickTex,
                      BufferedImage glowTex, Camera cam, float cx, float cy, float pickSize,
                      float roll, float pivotX, float pivotY, int glowArgb, float glowAlpha) throws Exception {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        // What the game shows behind the cutscene: the world under a cold dim.
        g.setColor(new Color(0x2B3A2E));
        g.fillRect(0, 0, W, H);
        g.setColor(new Color(0x08, 0x10, 0x1A, 222));
        g.fillRect(0, 0, W, H);

        // A little of the flare in the ice, as ThawScreen does.
        iceTint = glowArgb != 0 ? mixTint(glowArgb, 0.18f * glowAlpha) : 0xFFFFFF;
        // Flare glow behind the block.
        if (glowArgb != 0) {
            // Twice, wide then tight: one soft pool of colour and a hot core behind the block.
            BufferedImage tinted = tint(glowTex, glowArgb, glowAlpha);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int gs = (int) (H * 1.35f);
            g.drawImage(tinted, (int) cx - gs / 2, (int) cy - gs / 2, gs, gs, null);
            gs = (int) (H * 0.80f);
            g.drawImage(tinted, (int) cx - gs / 2, (int) cy - gs / 2, gs, gs, null);
        }

        // The ice and the figure, sorted together - that is what puts the figure inside.
        List<Face> faces = new ArrayList<>();
        List<BufferedImage> texOf = new ArrayList<>();
        List<Float> alphaOf = new ArrayList<>();
        FacePool pool = new FacePool();
        // As ThawScreen: the far facets, him, then the near facets.
        tinted = iceTex;
        project(ThawMeshes.iceFacing(false, cam.yawDeg(), cam.pitchDeg()), cam, faces, texOf, alphaOf, iceTex, 0.92f, pool);
        drawSorted(g, faces, texOf, alphaOf, cx, cy);
        faces.clear(); texOf.clear(); alphaOf.clear();
        ModelProjection.project(figure, cam, 0f, 0, 0, 0, false, true, faces, pool);
        BufferedImage cold = multiply(corpseTex, FIGURE_TINT);
        for (int i = 0; i < faces.size(); i++) { texOf.add(cold); alphaOf.add(1f); }
        drawSorted(g, faces, texOf, alphaOf, cx, cy);
        faces.clear(); texOf.clear(); alphaOf.clear();
        project(ThawMeshes.iceFacing(true, cam.yawDeg(), cam.pitchDeg()), cam, faces, texOf, alphaOf, iceTex, 0.92f, pool);
        drawSorted(g, faces, texOf, alphaOf, cx, cy);

        // The pick, in front, with its outline stamped behind it as MeshRenderer does.
        List<Face> pf = new ArrayList<>();
        List<BufferedImage> pt = new ArrayList<>();
        List<Float> pa = new ArrayList<>();
        Camera pc = Camera.of(-20f, -8f, pickSize).roll(roll);
        project(ThawMeshes.pick(), pc, pf, pt, pa, pickTex, 1f, new FacePool());
        float ow = Math.max(1.5f, W / 700f);
        for (float[] o : new float[][]{{-1,0},{1,0},{0,-1},{0,1},{-.7f,-.7f},{.7f,-.7f},{-.7f,.7f},{.7f,.7f}}) {
            drawSortedInk(g, pf, pt, pivotX + o[0] * ow, pivotY + o[1] * ow);
        }
        drawSorted(g, pf, pt, pa, pivotX, pivotY);

        // Tray guide: where the item slots will sit, so the framing is judged with them.
        int slot = (int) (H * 0.085f), n = 7, gap = (int) (slot * 0.22f);
        int trayW = n * slot + (n - 1) * gap, tx = (W - trayW) / 2, ty = (int) (H * TRAY_Y) - slot / 2;
        // The caption band: an item name at 2.1x the 9px font, centred on CAPTION_Y.
        int capH = (int) (9 * 2.1f), capW = (int) (150 * 2.1f);
        g.setColor(new Color(255, 200, 80, 90));
        g.fillRect((W - capW) / 2, (int) (H * CAPTION_Y) - capH / 2, capW, capH);
        // The screen's centre line.
        g.setColor(new Color(255, 80, 80, 150));
        g.drawLine(W / 2, 0, W / 2, H);
        for (int i = 0; i < n; i++) {
            g.setColor(new Color(255, 255, 255, 18));
            g.fillRect(tx + i * (slot + gap), ty, slot, slot);
            g.setColor(new Color(170, 220, 255, 70));
            g.drawRect(tx + i * (slot + gap), ty, slot - 1, slot - 1);
        }
        g.dispose();
        ImageIO.write(img, "PNG", new File(path));
    }

    static void project(Mesh mesh, Camera cam, List<Face> faces, List<BufferedImage> texOf,
                        List<Float> alphaOf, BufferedImage tex, float alpha, FacePool pool) {
        int before = faces.size();
        ModelProjection.project(mesh, cam, 0f, 0, 0, 0, false, false, faces, pool);
        for (int i = before; i < faces.size(); i++) { texOf.add(tex); alphaOf.add(alpha); }
    }

    static void drawSorted(Graphics2D g, List<Face> faces, List<BufferedImage> texOf,
                           List<Float> alphaOf, float ox, float oy) {
        Integer[] order = new Integer[faces.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Float.compare(faces.get(a).depth, faces.get(b).depth));
        for (int i : order) face(g, faces.get(i), texOf.get(i), alphaOf.get(i), ox, oy, false);
    }

    static void drawSortedInk(Graphics2D g, List<Face> faces, List<BufferedImage> texOf, float ox, float oy) {
        for (int i = 0; i < faces.size(); i++) face(g, faces.get(i), texOf.get(i), 1f, ox, oy, true);
    }

    static void face(Graphics2D g, Face f, BufferedImage tex, float alpha, float ox, float oy, boolean ink) {
        if (f.uw <= 0 || f.vh <= 0) return;
        BufferedImage sub = tex.getSubimage(f.u0, f.v0, f.uw, f.vh);
        BufferedImage lit = new BufferedImage(f.uw, f.vh, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < f.vh; y++) for (int x = 0; x < f.uw; x++) {
            int p = sub.getRGB(x, y);
            int a = (int) ((p >>> 24) * alpha);
            if (ink) { lit.setRGB(x, y, (p >>> 24) << 24); continue; }
            int t = tex == tinted ? iceTint : 0xFFFFFF;
            int r = Math.min(255, (int) (((p >> 16) & 255) * f.brightness * (t >> 16 & 255) / 255f));
            int gg = Math.min(255, (int) (((p >> 8) & 255) * f.brightness * (t >> 8 & 255) / 255f));
            int b = Math.min(255, (int) ((p & 255) * f.brightness * (t & 255) / 255f));
            lit.setRGB(x, y, a << 24 | r << 16 | gg << 8 | b);
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(lit, new AffineTransform(f.ux / f.uw, f.uy / f.uw, f.vx / f.vh, f.vy / f.vh,
                f.ox + ox, f.oy + oy), null);
    }

    static BufferedImage tinted;
    /** The Vanguard as he is drawn this frame. */
    static Mesh figure = ThawMeshes.vanguard(ThawMeshes.POSES[0]);
    /** As ThawScreen tints him: cold, a little dim. */
    static final int FIGURE_TINT = 0xFFFFFF;

    static BufferedImage multiply(BufferedImage src, int rgb) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) for (int x = 0; x < src.getWidth(); x++) {
            int p = src.getRGB(x, y);
            int r = (p >> 16 & 255) * (rgb >> 16 & 255) / 255, g = (p >> 8 & 255) * (rgb >> 8 & 255) / 255,
                    b = (p & 255) * (rgb & 255) / 255;
            out.setRGB(x, y, (p & 0xFF000000) | r << 16 | g << 8 | b);
        }
        return out;
    }

    static int mixTint(int argb, float k) {
        int r = Math.round(255 + ((argb >> 16 & 255) - 255) * k);
        int g = Math.round(255 + ((argb >> 8 & 255) - 255) * k);
        int b = Math.round(255 + ((argb & 255) - 255) * k);
        return r << 16 | g << 8 | b;
    }

    static BufferedImage tint(BufferedImage src, int argb, float alpha) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int tr = argb >> 16 & 255, tg = argb >> 8 & 255, tb = argb & 255;
        for (int y = 0; y < src.getHeight(); y++) for (int x = 0; x < src.getWidth(); x++) {
            int p = src.getRGB(x, y);
            int a = (int) ((p >>> 24) * alpha);
            out.setRGB(x, y, a << 24 | tr << 16 | tg << 8 | tb);
        }
        return out;
    }
}
