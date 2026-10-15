import com.butcher.anim.ChopTimeline;
import com.butcher.anim.ChopTimeline.Creature;
import com.butcher.anim.ChopTimeline.Verdict;
import com.butcher.render.ChopScene;
import com.butcher.render.art.ChopArt;
import com.butcher.render.art.PropArt;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders "The Chopping Block" offline through the very same {@link ChopScene#draw} the game
 * uses, with Java2D standing in for the GUI. What this shows is what the game draws; change the
 * scene, not a copy of it here.
 *
 *   javac -d out -sourcepath src/main/java tools/ChopPreview.java
 *   java -Dskins=<dir> -Dvanilla=<dir> -cp out ChopPreview <outdir> <seed> [VERDICT[:CREATURE] ...]
 *   java -Dat=0,1250,3300 ... (single moments)      -Dbg=<png> (a game screenshot to dim behind)
 *
 * -Dskins is a directory of the heads' skins as PNGs, named <ID>_skin.png - the creatures
 * (SPHINX, INQUISITOR, MANTICORE, KING_MINOS, CHAMPION) and the drops (MINOS_RELIC...), made
 * from the NEU repo's blobs and never bundled. -Dvanilla holds the game's enchanted_book.png,
 * golden_helmet.png and yellow_wool.png.
 *
 * <h2>Drawn as the game draws it</h2>
 *
 * The scene is laid out at 640 x 360 GUI pixels - a 1080p screen at GUI scale 3 - but rasterised
 * at the screen's own 1920 x 1080 (-Dscale, default 3), because that is what the game does: a
 * textured quad is drawn at full resolution, not in GUI pixels. Lines are the game's filled
 * rectangles with hard edges, not anti-aliased strokes. An earlier preview drew smooth strokes at
 * 640 x 360 on a flat fake sky, and effects that looked fine there looked cheap in the game.
 * Use -Dbg with a real screenshot.
 */
public final class ChopPreview {

    static int W = Integer.getInteger("w", 640), H = Integer.getInteger("h", 360), S = Integer.getInteger("scale", 3);

    static final com.butcher.render.model.Mesh HEAD = com.butcher.render.model.PlayerHeadMesh.head();

    static final Map<Verdict, String> NAMES = Map.of(
            Verdict.RELIC, "Minos Relic", Verdict.BRAIN_FOOD, "Brain Food", Verdict.CHIMERA, "Chimera I",
            Verdict.DYE, "Mythological Dye", Verdict.FATEFUL_STINGER, "Fateful Stinger", Verdict.MANTI_CORE, "Manti-core",
            Verdict.CROWN_OF_GREED, "Crown of Greed", Verdict.SHIMMERING_WOOL, "Shimmering Wool");

    static BufferedImage read(String dir, String name) {
        if (dir == null) return null;
        try {
            File f = new File(dir, name);
            return f.exists() ? ImageIO.read(f) : null;
        } catch (Exception e) {
            return null;
        }
    }

    static Object[] itemArt(Verdict v) {
        String skins = System.getProperty("skins"), van = System.getProperty("vanilla");
        String head = switch (v) {
            case RELIC -> "MINOS_RELIC"; case BRAIN_FOOD -> "BRAIN_FOOD"; case FATEFUL_STINGER -> "FATEFUL_STINGER";
            case MANTI_CORE -> "MANTI_CORE"; case DYE -> "DYE_MYTHOLOGICAL"; default -> null;
        };
        if (head != null) {
            BufferedImage img = read(skins, head + "_skin.png");
            return img == null ? null : new Object[]{"head", img};
        }
        String flat = switch (v) {
            case CHIMERA -> "enchanted_book"; case CROWN_OF_GREED -> "golden_helmet"; case SHIMMERING_WOOL -> "yellow_wool"; default -> null;
        };
        BufferedImage img = flat == null ? null : read(van, flat + ".png");
        return img == null ? null : new Object[]{v == Verdict.SHIMMERING_WOOL ? "block" : "flat", img};
    }

    public static void main(String[] args) throws Exception {
        String out = args[0];
        long seed = Long.parseLong(args[1]);
        int fps = Integer.getInteger("fps", 30);
        Map<String, BufferedImage> tex = new HashMap<>();
        tex.put(ChopArt.TEX_SHEET, ChopArt.sheet());
        tex.put(ChopArt.TEX_GLOW, ChopArt.glow());
        tex.put(ChopArt.TEX_RAYS, ChopArt.rays());
        tex.put(ChopArt.TEX_RING, ChopArt.ring());
        tex.put(ChopArt.TEX_SPLAT, ChopArt.splat());
        tex.put(ChopArt.TEX_DROP, ChopArt.drop());
        tex.put(ChopArt.TEX_VIGNETTE, ChopArt.vignette());
        tex.put(ChopArt.TEX_YARN, ChopArt.yarn());
        tex.put(ChopArt.TEX_WING, ChopArt.wing());
        tex.put(ChopArt.TEX_PIXDROP, ChopArt.pixDrop());
        tex.put(ChopArt.TEX_PIXSPLAT, ChopArt.pixSplat());
        tex.put(com.butcher.render.art.ThawArt.TEX_COIN, com.butcher.render.art.ThawArt.coin());
        BufferedImage armor = read(System.getProperty("vanilla"), "humanoid_gold.png");
        if (armor != null) tex.put(ChopArt.TEX_ARMOR, armor);
        tex.put(PropArt.TEX_CLEAVER, PropArt.cleaver());
        tex.put(ChopArt.TEX_GLINT, ChopArt.glint(read(System.getProperty("vanilla"), "enchanted_glint_item.png")));
        for (int k = 0; k < 26; k++) {
            BufferedImage g = read(System.getProperty("vanilla"), "sga_" + (char) ('a' + k) + ".png");
            if (g != null) tex.put(ChopArt.glyphTex(k), g);
        }
        String skins = System.getProperty("skins");
        for (Creature c : Creature.values()) {
            BufferedImage img = read(skins, c.name() + "_skin.png");
            if (img != null) tex.put("creature_" + c.name(), img);
        }
        // The whole bestiary, for the Dye's parade.
        for (String key : com.butcher.anim.HuntEnding.BESTIARY) {
            BufferedImage img = read(skins, key + "_skin.png");
            if (img != null) tex.put("bestiary_" + key, img);
        }
        BufferedImage bg = System.getProperty("bg") == null ? null : ImageIO.read(new File(System.getProperty("bg")));

        java.util.List<String> runs = new java.util.ArrayList<>();
        for (int i = 2; i < args.length; i++) runs.add(args[i]);
        if (runs.isEmpty()) runs.addAll(java.util.List.of("BUST", "MANTI_CORE"));
        String only = System.getProperty("at");
        for (String run : runs) {
            String[] p = run.split(":");
            Verdict v = Verdict.valueOf(p[0]);
            Creature c = p.length > 1 ? Creature.valueOf(p[1]) : Creature.of(v, seed);
            ChopTimeline tl = new ChopTimeline(v, c, seed);
            // A stand-in price, so the line can be judged.
            if (v.win()) tl.price(28_800_000L, "lowest BIN on the auction house");
            File dir = new File(out, v.name() + "_" + c.name() + "_" + seed);
            dir.mkdirs();
            Object[] art = itemArt(v);
            if (art != null && "head".equals(art[0])) tex.put("drop", (BufferedImage) art[1]);
            else tex.remove("drop");
            long[] times;
            if (only != null) {
                String[] q = only.split(",");
                times = new long[q.length];
                for (int k = 0; k < q.length; k++) times[k] = Long.parseLong(q[k].trim());
            } else {
                int n = (int) (tl.total() * fps / 1000) + 1;
                times = new long[n];
                for (int k = 0; k < n; k++) times[k] = k * 1000L / fps;
            }
            int i = 0;
            for (long t : times) {
                BufferedImage img = new BufferedImage(W * S, H * S, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                g.scale(S, S);
                background(g, bg);
                ChopScene.draw(new Java2D(g, tex, NAMES.getOrDefault(v, ""), art), tl, t, W, H);
                g.dispose();
                ImageIO.write(img, "PNG", new File(dir, only != null ? String.format("t_%05d.png", t) : String.format("f_%04d.png", i)));
                i++;
            }
            System.out.println(v + " on a " + c + ": " + i + " frames");
        }
    }

    /** The game behind: a screenshot if given, else a stand-in sky and ground. */
    static void background(Graphics2D g, BufferedImage bg) {
        if (bg != null) {
            g.drawImage(bg, 0, 0, W, H, null);
            return;
        }
        g.setPaint(new GradientPaint(0, 0, new Color(0x6E9BD8), 0, H * 0.6f, new Color(0xB9D3F0)));
        g.fillRect(0, 0, W, H);
        g.setPaint(new GradientPaint(0, H * 0.6f, new Color(0x5E9A3A), 0, H, new Color(0x3C6A24)));
        g.fillRect(0, (int) (H * 0.6f), W, H);
    }

    /** ChopScene's primitives, drawn with Java2D. */
    static final class Java2D implements ChopScene.Canvas {
        final Graphics2D g;
        final Map<String, BufferedImage> tex;
        final String itemName;
        final Object[] art;

        Java2D(Graphics2D g, Map<String, BufferedImage> tex, String itemName, Object[] art) {
            this.g = g; this.tex = tex; this.itemName = itemName; this.art = art;
        }

        @Override
        public void image(String key, int[] argb, int iw, int ih, float x, float y, float w, float h) {
            if (iw <= 0 || ih <= 0) return;
            BufferedImage img = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, iw, ih, argb, 0, iw);
            Object before = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(img, new java.awt.geom.AffineTransform(w / iw, 0, 0, h / ih, x, y), null);
            if (before != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, before);
        }

        public void imageAffine(String key, int[] argb, int iw, int ih, float ox, float oy, float ux, float uy, float vx, float vy) {
            if (iw <= 0 || ih <= 0) return;
            BufferedImage img = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, iw, ih, argb, 0, iw);
            Object before = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(img, new java.awt.geom.AffineTransform(ux / iw, uy / iw, vx / ih, vy / ih, ox, oy), null);
            if (before != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, before);
        }

        public void quad(String name, float ox, float oy, float ux, float uy, float vx, float vy, int u0, int v0, int uw, int vh, int argb) {
            BufferedImage src = tex.get(name);
            if (src == null || uw <= 0 || vh <= 0) return;
            BufferedImage lit = new BufferedImage(uw, vh, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < vh; y++) for (int x = 0; x < uw; x++) {
                int sx = u0 + x, sy = v0 + y;
                if (sx >= src.getWidth() || sy >= src.getHeight()) continue;
                lit.setRGB(x, y, multiply(src.getRGB(sx, sy), argb));
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            // A hair over-size - a third of a screen pixel: the game's rasteriser leaves no seams
            // between neighbours, Java2D does.
            // Only for opaque faces: see-through quads overlapping would double up and draw a grid.
            float ex = (argb >>> 24) == 255 ? 0.35f / S : 0f;
            float lu = (float) Math.hypot(ux, uy), lv = (float) Math.hypot(vx, vy);
            float gu = lu < 1e-3f ? 0 : ex / lu, gv = lv < 1e-3f ? 0 : ex / lv;
            g.drawImage(lit, new AffineTransform(ux * (1 + 2 * gu) / uw, uy * (1 + 2 * gu) / uw, vx * (1 + 2 * gv) / vh, vy * (1 + 2 * gv) / vh,
                    ox - ux * gu - vx * gv, oy - uy * gu - vy * gv), null);
        }

        public void sprite(String name, float cx, float cy, float w, float h, float rot, int argb) {
            BufferedImage src = tex.get(name);
            if (src == null || w < 0.5f || h < 0.5f) return;
            BufferedImage t = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < src.getHeight(); y++) for (int x = 0; x < src.getWidth(); x++) t.setRGB(x, y, multiply(src.getRGB(x, y), argb));
            AffineTransform at = new AffineTransform();
            at.translate(cx, cy);
            at.rotate(Math.toRadians(rot));
            at.scale(w / src.getWidth(), h / src.getHeight());
            at.translate(-src.getWidth() / 2.0, -src.getHeight() / 2.0);
            // The game samples GUI textures nearest-neighbour: pixel art stays hard-edged. Only the
            // big soft glows (128 px) are smooth either way.
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, src.getWidth() <= 32
                    ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(t, at, null);
        }

        /** As ChopScreen draws it: a filled rectangle, half a width past each end, hard-edged. */
        public void line(float x0, float y0, float x1, float y1, float width, int argb) {
            float dx = x1 - x0, dy = y1 - y0, len = (float) Math.hypot(dx, dy);
            if (len < 0.05f || (argb >>> 24) < 3) return;
            float ux = dx / len, uy = dy / len, nx = -uy * width / 2f, ny = ux * width / 2f;
            float ax = x0 - ux * width / 2f, ay = y0 - uy * width / 2f, bx = x1 + ux * width / 2f, by = y1 + uy * width / 2f;
            java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
            p.moveTo(ax + nx, ay + ny);
            p.lineTo(bx + nx, by + ny);
            p.lineTo(bx - nx, by - ny);
            p.lineTo(ax - nx, ay - ny);
            p.closePath();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setColor(new Color(argb, true));
            g.fill(p);
        }

        public void fill(float x, float y, float w, float h, int argb) {
            g.setColor(new Color(argb, true));
            g.fill(new java.awt.geom.Rectangle2D.Float(x, y, w, h));
        }

        public String creatureSkin(Creature creature) {
            String name = "creature_" + creature.name();
            return tex.containsKey(name) ? name : null;
        }

        public String dropSkin() {
            return tex.containsKey("drop") ? "drop" : null;
        }

        public String bestiarySkin(String key) {
            return tex.containsKey("bestiary_" + key) ? "bestiary_" + key : null;
        }

        public void item(float cx, float cy, float size, float alpha, float yaw) {
            if (art == null) return;
            String kind = (String) art[0];
            BufferedImage img = (BufferedImage) art[1];
            if (kind.equals("head")) head(img, cx, cy, size, yaw, alpha);
            else if (kind.equals("block")) block(img, cx, cy, size, alpha);
            else {
                if (alpha < 0.5f) return;
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(img, Math.round(cx - size / 2f), Math.round(cy - size / 2f), Math.round(size), Math.round(size), null);
            }
        }

        /** A player-head item, as DianaItems draws it: PlayerHeadMesh through the real projection. */
        void head(BufferedImage skin, float cx, float cy, float size, float yaw, float alpha) {
            java.util.List<com.butcher.render.model.ModelProjection.Face> faces = new java.util.ArrayList<>();
            float span = com.butcher.render.model.PlayerHeadMesh.HEAD + 2 * com.butcher.render.model.PlayerHeadMesh.HAT_INFLATE;
            com.butcher.render.model.ModelProjection.project(HEAD, com.butcher.render.model.ModelProjection.Camera.of(yaw, -22f, size / (span * 1.4f)),
                    0f, 0, 0, 0, false, true, faces, new com.butcher.render.model.ModelProjection.FacePool());
            com.butcher.render.model.ModelProjection.sortByDepth(faces);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int a8 = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
            for (com.butcher.render.model.ModelProjection.Face f : faces) {
                if (f.uw <= 0 || f.vh <= 0) continue;
                BufferedImage lit = new BufferedImage(f.uw, f.vh, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < f.vh; y++) for (int x = 0; x < f.uw; x++) {
                    int p = skin.getRGB(f.u0 + x, f.v0 + y);
                    int al = (p >>> 24) * a8 / 255;
                    int r = (int) ((p >> 16 & 255) * f.brightness), gg = (int) ((p >> 8 & 255) * f.brightness), bb = (int) ((p & 255) * f.brightness);
                    lit.setRGB(x, y, al << 24 | r << 16 | gg << 8 | bb);
                }
                g.drawImage(lit, new AffineTransform(f.ux / f.uw, f.uy / f.uw, f.vx / f.vh, f.vy / f.vh, f.ox + cx, f.oy + cy), null);
            }
        }

        /** A block item (the wool): top, left and right, shaded. */
        void block(BufferedImage img, float cx, float cy, float size, float alpha) {
            if (alpha < 0.5f) return;
            float s = size * 0.5f, h = s * 0.5f;
            float[] shade = {1f, 0.8f, 0.6f};
            float[][] vec = {
                    {cx - s, cy - s + h, s, -h, s, h},
                    {cx - s, cy - s + h, s, h, 0, s},
                    {cx, cy, s, -h, 0, s},
            };
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for (int k = 0; k < 3; k++) {
                BufferedImage lit = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                    int p = img.getRGB(x, y);
                    lit.setRGB(x, y, 0xFF000000 | (int) ((p >> 16 & 255) * shade[k]) << 16 | (int) ((p >> 8 & 255) * shade[k]) << 8 | (int) ((p & 255) * shade[k]));
                }
                float[] f = vec[k];
                g.drawImage(lit, new AffineTransform(f[2] / 16f, f[3] / 16f, f[4] / 16f, f[5] / 16f, f[0], f[1]), null);
            }
        }

        public void name(float cx, float cy, float scale, int argb, int letters) {
            int tw = width(itemName, scale);
            if (letters > 0) plate(cx, cy, scale, tw, argb);
            draw(itemName.substring(0, Math.min(letters, itemName.length())), cx - tw / 2f, cy, scale, argb);
        }

        public void text(String text, float cx, float cy, float scale, int argb) {
            plate(cx, cy, scale, width(text, scale), argb);
            draw(text, cx - width(text, scale) / 2f, cy, scale, argb);
        }

        /** The screens' dark plate behind a line (here the width is already in screen pixels). */
        void plate(float cx, float cy, float scale, int screenW, int argb) {
            int a = Math.round((argb >>> 24) * 0.42f);
            if (a < 4) return;
            float w = screenW + 8 * scale, h = 12f * scale;
            fill(Math.round(cx - w / 2f), Math.round(cy - h / 2f), Math.round(w), Math.round(h), a << 24 | 0x0B0709);
        }

        int width(String s, float scale) {
            s = s.replaceAll("\u00a7.", "");
            g.setFont(new Font("SansSerif", Font.BOLD, Math.round(9 * scale)));
            return g.getFontMetrics().stringWidth(s);
        }

        void draw(String s, float x, float cy, float scale, int argb) {
            if ((argb >>> 24) < 6) return;
            // The game reads legacy formatting codes (the banner's bold); here they are simply dropped.
            s = s.replaceAll("\u00a7.", "");
            g.setFont(new Font("SansSerif", Font.BOLD, Math.round(9 * scale)));
            g.setColor(new Color(0, 0, 0, argb >>> 24));
            g.drawString(s, x + 1, cy + 4 * scale + 1);
            g.setColor(new Color(argb, true));
            g.drawString(s, x, cy + 4 * scale);
        }

        static int multiply(int p, int tint) {
            int a = (p >>> 24) * (tint >>> 24) / 255;
            int r = (p >> 16 & 255) * (tint >> 16 & 255) / 255;
            int gg = (p >> 8 & 255) * (tint >> 8 & 255) / 255;
            int b = (p & 255) * (tint & 255) / 255;
            return a << 24 | r << 16 | gg << 8 | b;
        }
    }
}
