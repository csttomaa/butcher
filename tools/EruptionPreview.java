import com.butcher.anim.EruptionTimeline;
import com.butcher.anim.EruptionTimeline.Creature;
import com.butcher.anim.EruptionTimeline.Drop;
import com.butcher.render.EruptionScene;
import com.butcher.render.art.ChopArt;
import com.butcher.render.art.LavaArt;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders frames of "Eruption" offline through the real EruptionScene on ChopPreview's game-accurate canvas, with
 * the game's own models posed by its own code (LavaRigs: the client jar must be on the classpath, as for the
 * Seraph's dragon).
 *
 *   java -Dvanilla=<the game's textures> -Dskins=<NEU skins, NAME_skin.png> -Dbg=<screenshot> -Dat=0,700
 *        -cp "<mc classpath>;out;src/main/resources" EruptionPreview <outdir> <seed> CREATURE DROP...
 */
public final class EruptionPreview {

    public static void main(String[] args) throws Exception {
        String out = args[0];
        long seed = Long.parseLong(args[1]);
        Creature creature = Creature.valueOf(args[2]);
        int fps = Integer.getInteger("fps", 30);
        String van = System.getProperty("vanilla"), skins = System.getProperty("skins");
        Map<String, BufferedImage> tex = new HashMap<>();
        BufferedImage lava = argb(ImageIO.read(new File(van, "lava_still.png")));
        tex.put(EruptionScene.TEX_LAVA, lava);
        BufferedImage white = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) white.setRGB(x, y, 0xFFFFFFFF);
        tex.put(EruptionScene.TEX_WHITE, white);
        tex.put(ChopArt.TEX_GLOW, ChopArt.glow());
        tex.put(ChopArt.TEX_SHEET, ChopArt.sheet());
        tex.put(EruptionScene.TEX_BLACKSTONE, argb(ImageIO.read(new File(van, "blackstone.png"))));
        tex.put(EruptionScene.TEX_BASALT_SIDE, argb(ImageIO.read(new File(van, "basalt_side.png"))));
        tex.put(EruptionScene.TEX_BASALT_TOP, argb(ImageIO.read(new File(van, "basalt_top.png"))));
        EruptionScene.rigs = new com.butcher.render.LavaRigs();
        BufferedImage magma = argb(ImageIO.read(new File(van, "magma.png")));
        EruptionScene.RASTER.texture(EruptionScene.SLOT_MAGMA, magma);
        EruptionScene.RASTER.texture(EruptionScene.SLOT_CRACKS, LavaArt.crackMask(magma));
        EruptionScene.RASTER.texture(EruptionScene.SLOT_LAVA_GREY, LavaArt.greyLava(lava));
        EruptionScene.lavaPixels = LavaArt.pixels(lava);
        tex.put(EruptionScene.TEX_MAGMA, magma);
        tex.put(EruptionScene.TEX_CRACKS, LavaArt.crackMask(magma));
        tex.put(EruptionScene.TEX_LAVA_GREY, LavaArt.greyLava(lava));
        tex.put(EruptionScene.TEX_CUBE, argb(ImageIO.read(new File(van, "magmacube.png"))));
        EruptionScene.RASTER.texture(EruptionScene.SLOT_CUBE, argb(ImageIO.read(new File(van, "magmacube.png"))));
        EruptionScene.RASTER.texture(EruptionScene.SLOT_HORSE, argb(ImageIO.read(new File(van, "horse_skeleton.png"))));
        EruptionScene.RASTER.texture(EruptionScene.SLOT_SADDLE, argb(ImageIO.read(new File(van, "skeleton_saddle.png"))));
        String creatureTex = switch (creature) {
            case JAWBUS -> van + "/iron_golem.png";
            case THUNDER -> van + "/guardian_elder.png";
            case PLHLEGBLAST -> van + "/squid.png";
            case RAGNAROK -> skins + "/Ragnarok_skin.png";
            case SCUTTLER -> skins + "/Fiery_Scuttler_skin.png";
        };
        EruptionScene.RASTER.texture(EruptionScene.SLOT_CREATURE, argb(ImageIO.read(new File(creatureTex))));
        EruptionScene.rasterScale = 2f;
        BufferedImage bg = System.getProperty("bg") == null ? null : ImageIO.read(new File(System.getProperty("bg")));
        int W = ChopPreview.W, H = ChopPreview.H, S = ChopPreview.S;
        for (int i = 3; i < args.length; i++) {
            Drop d = Drop.valueOf(args[i]);
            EruptionTimeline tl = new EruptionTimeline(creature, d, seed);
            tl.price(prices(d, creature));
            if (d.win()) {
                BufferedImage img = d.head ? argb(ImageIO.read(new File(skins, d.art + "_skin.png"))) : argb(ImageIO.read(new File(van, d.art + ".png")));
                EruptionScene.dropPixels = LavaArt.pixels(img);
                EruptionScene.dropW = img.getWidth();
                EruptionScene.dropH = img.getHeight();
            } else {
                EruptionScene.dropPixels = null;
            }
            File dir = new File(out, creature.name() + "_" + d.name() + "_" + seed);
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
                ChopPreview.Java2D canvas = new ChopPreview.Java2D(g, tex, d.title, null);
                EruptionScene.draw(canvas, canvas::fill, canvas::fill, tl, t, W, H);
                g.dispose();
                ImageIO.write(img, "PNG", new File(dir, only != null ? String.format("t_%05d.png", t) : String.format("f_%04d.png", n)));
                n++;
            }
            System.out.println(creature + " " + d + ": " + n + " frames");
        }
    }

    /** Prices as of 2026-10-05, for the preview's counter. */
    static long prices(Drop d, Creature c) {
        return switch (d) {
            case BUST -> c == Creature.JAWBUS ? 4_471_331L : (c == Creature.SCUTTLER ? 442_727L : 0L);
            case CHAIN -> 1_302_383L;
            case PINCER -> 2_278_632L;
            case FLASH -> 4_821_835L;
            case BRIMSTONE -> 6_000_003L;
            case VIAL -> 30_000_001L;
            case SHELL -> 30_435_003L;
            case BURNT_TEXTS -> 87_000_000L;
            case CARMINE -> 175_000_000L;
        };
    }

    static BufferedImage argb(BufferedImage in) {
        BufferedImage o = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.drawImage(in, 0, 0, null);
        g.dispose();
        return o;
    }
}
