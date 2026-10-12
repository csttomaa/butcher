import com.butcher.anim.HellionTimeline;
import com.butcher.anim.HellionTimeline.Drop;
import com.butcher.render.HellionScene;
import com.butcher.render.art.ChopArt;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders frames of "Hellion" (the blaze slayer's animation) offline, through the real HellionScene on
 * ChopPreview's game-accurate canvas: 640 x 360 GUI px rasterised at -Dscale (3), small textures
 * nearest, a dimmed screenshot behind (-Dbg).
 *
 *   java -Dvanilla=<dir with blaze.png, enchanted_book.png, paper.png> -Dskins=<dir with *_skin.png>
 *        -Dbg=<screenshot> -Dat=0,700,1400 -cp out HellionPreview <outdir> <seed> DROP...
 *
 * Mojang's blaze texture is read from the user's own game files for this preview only - the mod adopts
 * it from the game at runtime and never bundles it.
 */
public final class HellionPreview {

    public static void main(String[] args) throws Exception {
        String out = args[0];
        long seed = Long.parseLong(args[1]);
        int fps = Integer.getInteger("fps", 30);
        String van = System.getProperty("vanilla"), skins = System.getProperty("skins");
        Map<String, BufferedImage> tex = new HashMap<>();
        tex.put(HellionScene.TEX_BLAZE, argb(ImageIO.read(new File(van, "blaze.png"))));
        tex.put(ChopArt.TEX_GLOW, ChopArt.glow());
        for (int i = 0; i < 4; i++) tex.put(HellionScene.TERRACOTTA[i], argb(ImageIO.read(new File(van, HellionScene.TERRACOTTA_VANILLA[i] + ".png"))));
        // The same path as the game: the strip normalised, and the grey copy the shield tints.
        BufferedImage fire = com.butcher.render.art.HellionArt.fireStrip(argb(ImageIO.read(new File(van, "fire_0.png"))));
        tex.put(HellionScene.TEX_FIRE, fire);
        tex.put(HellionScene.TEX_FIRE_GREY, com.butcher.render.art.HellionArt.greyFire(fire));
        BufferedImage bg = System.getProperty("bg") == null ? null : ImageIO.read(new File(System.getProperty("bg")));
        int W = ChopPreview.W, H = ChopPreview.H, S = ChopPreview.S;
        for (int i = 2; i < args.length; i++) {
            Drop d = Drop.valueOf(args[i]);
            HellionTimeline tl = new HellionTimeline(d, seed);
            if (d.win()) tl.price(37_000_000L);
            Object[] art = null;
            tex.remove("drop");
            if (d.art != null) {
                BufferedImage img = d.head ? read(skins, skinFile(d)) : read(van, d.art + ".png");
                if (img != null) art = new Object[]{d.head ? "head" : "flat", img};
                if (img != null && d.head) tex.put("drop", img);
            }
            File dir = new File(out, d.name() + "_" + seed);
            dir.mkdirs();
            long[] times;
            String only = System.getProperty("at");
            if (only != null) {
                String[] q = only.split(",");
                times = new long[q.length];
                for (int k = 0; k < q.length; k++) times[k] = Long.parseLong(q[k].trim());
            } else {
                int n = (int) (tl.total() * fps / 1000) + 1;
                times = new long[n];
                for (int k = 0; k < n; k++) times[k] = k * 1000L / fps;
            }
            int n = 0;
            for (long t : times) {
                BufferedImage img = new BufferedImage(W * S, H * S, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                g.scale(S, S);
                ChopPreview.background(g, bg);
                ChopPreview.Java2D canvas = new ChopPreview.Java2D(g, tex, d.title, art);
                HellionScene.draw(canvas, canvas::fill, tl, t, W, H);
                g.dispose();
                ImageIO.write(img, "PNG", new File(dir, only != null ? String.format("t_%05d.png", t) : String.format("f_%04d.png", n)));
                n++;
            }
            System.out.println(d + ": " + n + " frames");
        }
    }

    static String skinFile(Drop d) {
        return d.id + "_skin.png";
    }

    static BufferedImage read(String dir, String name) {
        try {
            File f = new File(dir, name);
            return f.exists() ? argb(ImageIO.read(f)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    static BufferedImage argb(BufferedImage in) {
        BufferedImage o = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.drawImage(in, 0, 0, null);
        g.dispose();
        return o;
    }
}
