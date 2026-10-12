import com.butcher.anim.SeraphTimeline;
import com.butcher.anim.SeraphTimeline.Drop;
import com.butcher.render.SeraphScene;
import com.butcher.render.art.ChopArt;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders frames of "The Seraph's Glyph" offline, through the real SeraphScene on ChopPreview's
 * game-accurate canvas: 640 x 360 GUI px rasterised at -Dscale (3), small textures nearest, a dimmed
 * screenshot behind (-Dbg).
 *
 *   java -Dvanilla=<dir with the game's textures> -Dskins=<dir with ID_skin.png> -Dbg=<screenshot>
 *        -Dat=0,700,1400 -cp "out;src/main/resources" SeraphPreview <outdir> <seed> DROP...
 *
 * The game's textures are read from the user's own client jar for the preview only - the mod adopts
 * them at runtime and never bundles them.
 */
public final class SeraphPreview {

    public static void main(String[] args) throws Exception {
        String out = args[0];
        long seed = Long.parseLong(args[1]);
        int fps = Integer.getInteger("fps", 30);
        String van = System.getProperty("vanilla"), skins = System.getProperty("skins");
        Map<String, BufferedImage> tex = new HashMap<>();
        for (int i = 0; i < SeraphScene.ADOPT.length; i++) {
            String path = SeraphScene.ADOPT_PATH[i];
            tex.put(SeraphScene.ADOPT[i], argb(ImageIO.read(new File(van, path.substring(path.lastIndexOf('/') + 1)))));
        }
        BufferedImage white = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) white.setRGB(x, y, 0xFFFFFFFF);
        tex.put(SeraphScene.TEX_WHITE, white);
        tex.put(ChopArt.TEX_GLOW, ChopArt.glow());
        tex.put(ChopArt.TEX_SHEET, ChopArt.sheet());
        // The real dragon, posed by the game's own model (the client jar is on this tool's classpath).
        tex.put(SeraphScene.TEX_DRAGON, argb(ImageIO.read(new File(van, "dragon.png"))));
        tex.put(SeraphScene.TEX_DRAGON_EYES, argb(ImageIO.read(new File(van, "dragon_eyes.png"))));
        SeraphScene.dragon = new com.butcher.render.DragonRig();
        SeraphScene.RASTER.texture(0, tex.get(SeraphScene.TEX_DRAGON));
        SeraphScene.RASTER.texture(1, tex.get(SeraphScene.TEX_DRAGON_EYES));
        // The dragon is rasterised at 2 image pixels a GUI pixel, as the game caps it.
        SeraphScene.rasterScale = 2f;
        BufferedImage bg = System.getProperty("bg") == null ? null : ImageIO.read(new File(System.getProperty("bg")));
        int W = ChopPreview.W, H = ChopPreview.H, S = ChopPreview.S;
        for (int i = 2; i < args.length; i++) {
            Drop d = Drop.valueOf(args[i]);
            SeraphTimeline tl = new SeraphTimeline(d, seed);
            if (d.win()) tl.price(d == Drop.BYZANTIUM_DYE ? 775_000_000L : 190_000_000L);
            Object[] art = null;
            tex.remove("drop");
            if (d.art != null) {
                BufferedImage img = d.head ? read(skins, d.id + "_skin.png") : read(van, d.art + ".png");
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
                SeraphScene.draw(canvas, canvas::fill, tl, t, W, H);
                g.dispose();
                ImageIO.write(img, "PNG", new File(dir, only != null ? String.format("t_%05d.png", t) : String.format("f_%04d.png", n)));
                n++;
            }
            System.out.println(d + ": " + n + " frames");
        }
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
