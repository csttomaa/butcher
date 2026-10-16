package com.butcher.render;

import com.butcher.ButcherClient;
import com.butcher.audio.ButcherAudio;
import com.butcher.detect.GhostDrops.Drop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * "Wisp" - a Ghost in the Dwarven Mines' Mist dropped Sorrow or Plasma (the user, 2026-10-12: like Fin, only for the
 * drop). Small and quick, the same place and size as Fin, about 3 s, nothing hidden:
 *
 * <ol>
 *   <li>a strip of mist drifts in;</li>
 *   <li>the Ghost rises out of it - a charged creeper, pale and see-through, flickering, the charge's blue stripes
 *       crawling round it as they do on a charged creeper;</li>
 *   <li>it comes apart into wisps that float up, with a crackle;</li>
 *   <li>the drop pops out where it was, with its name and its bazaar price.</li>
 * </ol>
 *
 * Every shape is pixel art drawn as squares; only the drop is a real icon - Hypixel's own when its resource pack is
 * loaded, else the vanilla ghast tear for Sorrow (its NEU base item) and a pixel orb for Plasma (paper in vanilla).
 * Not a cutscene: drawn after the HUD by {@link ButcherClient#drawOwnHud}, like Fin.
 */
public final class GhostPopup {
    private GhostPopup() {}

    // ---- timing, ms from the start
    static final long MIST = 220, RISE = 260, RISEN = 820, BURST = 1180, POP = 1230, POPPED = 1500, FADE = 2700, END = 3100;

    /** GUI pixels per art pixel. */
    private static final int P = 2;

    private static Drop drop;
    /** How far up from the bottom it sits: clear of the hotbar, its bars and the action bar over them (skill XP
     *  and the like, at h - 72 .. h - 63), which it used to cover. */
    private static final int LIFT = 82;
    private static float speed = 1f;
    private static long startedAt = -1, seed;
    private static boolean rose, burst, revealed;
    private static long price = -1;
    /** Whether Hypixel's pack has this drop's icon (asked once per drop). */
    private static Boolean packIcon;

    /** Plays it for a drop. A second drop while one shows starts it again. */
    public static void show(Drop d) {
        drop = d;
        startedAt = System.currentTimeMillis();
        speed = Math.max(0.5f, Math.min(2f, (com.butcher.ButcherClient.config == null ? 100 : com.butcher.ButcherClient.config.ghostSpeed) / 100f));
        seed = startedAt * 31 + d.ordinal();
        rose = burst = revealed = false;
        price = -1;
        packIcon = null;
        if (ButcherClient.config == null || ButcherClient.config.livePrices) com.butcher.market.Bazaar.request();
    }

    /** The popup's own time, at the player's speed for it (the menu's Speed). */
    private static long elapsed() {
        return (long) ((System.currentTimeMillis() - startedAt) * speed);
    }

    /** Takes it off at once (/butcher stop, or after an error). */
    public static void stop() {
        drop = null;
    }

    public static boolean active() {
        return drop != null && elapsed() < END;
    }

    public static void draw(GuiGraphicsExtractor g) {
        if (!active()) {
            drop = null;
            return;
        }
        long t = elapsed();
        sounds(t);
        int w = g.guiWidth(), h = g.guiHeight();
        int cx = w / 2, mistY = h - LIFT;
        float fade = t < FADE ? 1f : 1f - (t - FADE) / (float) (END - FADE);
        int alpha = Math.round(255 * Math.max(0f, Math.min(1f, fade)));
        if (alpha < 6) return;
        mist(g, cx, mistY, t, alpha);
        if (t >= RISE && t < BURST) ghost(g, cx, mistY, t, alpha);
        if (t >= BURST) wisps(g, cx, mistY, t, alpha);
        if (t >= POP) item(g, cx, mistY, t, alpha);
    }

    // ================================================================== art

    /** The Mist: three rows of pale squares drifting along, thinner at the ends. In from the middle. */
    private static void mist(GuiGraphicsExtractor g, int cx, int y, long t, int alpha) {
        int half = Math.round(18 * Math.min(1f, t / (float) MIST));
        for (int i = -half; i <= half; i++) {
            float edge = 1f - Math.abs(i) / 19f;
            for (int row = 0; row < 3; row++) {
                int k = Math.floorMod(i * 7 + row * 13 + (int) (t / (110 + row * 40)), 9);
                if (k > 5 - row) continue;
                int col = row == 0 ? 0xDDE8F0 : row == 1 ? 0xAEC2D2 : 0x7E94A8;
                px(g, cx + i * P - P / 2, y + row * P, col, Math.round(alpha * edge * (0.75f - 0.15f * row)));
            }
        }
    }

    /** The Ghost: a charged creeper, front on. G its pale body, d the face. */
    private static final String[] GHOST = {
            "GGGGGGGG",
            "GGGGGGGG",
            "GddGGddG",
            "GddGGddG",
            "GGGddGGG",
            "GGddddGG",
            "GGddddGG",
            "GGdGGdGG",
            "..GGGG..",
            "..GGGG..",
            "..GGGG..",
            "..GGGG..",
            ".GG..GG.",
            ".GG..GG.",
    };

    /** Where the Ghost's top-left is at {@code t}: up out of the mist, then a slow bob. */
    private static int[] ghostAt(int cx, int mistY, long t) {
        float r = ease(Math.min(1f, Math.max(0f, (t - RISE) / (float) (RISEN - RISE))));
        float bob = (float) Math.sin(t / 260.0) * 1.5f;
        int top = Math.round(mistY + P - r * (GHOST.length + 4) * P + bob);
        return new int[]{cx - 4 * P, top};
    }

    private static void ghost(GuiGraphicsExtractor g, int cx, int mistY, long t, int alpha) {
        int[] at = ghostAt(cx, mistY, t);
        // See-through, and flickering the way an invisible mob's outline does - faster just before it goes.
        float flick = 0.62f + 0.12f * (float) Math.sin(t / (t > BURST - 250 ? 22.0 : 70.0));
        int a = Math.round(alpha * flick * Math.min(1f, (t - RISE) / 250f));
        // The charge: blue stripes crawling diagonally round it, in a ring two squares out, as a charged creeper's do.
        int rows = GHOST.length, cols = GHOST[0].length();
        for (int r = -2; r < rows + 2; r++) {
            for (int c = -2; c < cols + 2; c++) {
                if (inGhost(r, c)) continue;
                if (dist(r, c) > 2) continue;
                if (Math.floorMod(r + c + (int) (t / 70), 5) != 0) continue;
                int col = Math.floorMod(r - c + (int) (t / 90), 3) == 0 ? 0xE0F8FF : 0x5FC8FF;
                if (at[1] + r * P + P > mistY + P) continue;
                px(g, at[0] + c * P, at[1] + r * P, col, Math.round(a * 0.9f));
            }
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                char ch = GHOST[r].charAt(c);
                if (ch == '.') continue;
                int y = at[1] + r * P;
                // Still coming up out of the mist: nothing below its top row is drawn.
                if (y + P > mistY + P) continue;
                px(g, at[0] + c * P, y, ch == 'd' ? 0x1E3A30 : 0xBFF2DC, a);
            }
        }
    }

    private static boolean inGhost(int r, int c) {
        return r >= 0 && r < GHOST.length && c >= 0 && c < GHOST[0].length() && GHOST[r].charAt(c) != '.';
    }

    /** Squares from the nearest body square (8-neighbour steps), up to 3. */
    private static int dist(int r, int c) {
        for (int d = 1; d <= 3; d++) {
            for (int dr = -d; dr <= d; dr++) {
                for (int dc = -d; dc <= d; dc++) {
                    if (inGhost(r + dr, c + dc)) return d;
                }
            }
        }
        return 9;
    }

    /** It comes apart: every square of it drifts up and out, fading, a few of the charge's with them. */
    private static void wisps(GuiGraphicsExtractor g, int cx, int mistY, long t, int alpha) {
        float s = (t - BURST) / 1000f;
        if (s > 0.9f) return;
        int[] at = ghostAt(cx, mistY, BURST);
        java.util.Random rng = new java.util.Random(seed);
        for (int r = 0; r < GHOST.length; r++) {
            for (int c = 0; c < GHOST[0].length(); c++) {
                char ch = GHOST[r].charAt(c);
                if (ch == '.') continue;
                float vx = (c - 3.5f) * 9f + (rng.nextFloat() - 0.5f) * 30f;
                float vy = -(30f + rng.nextFloat() * 50f) + (r - 7) * 2f;
                float x = at[0] + c * P + vx * s + (float) Math.sin(s * 9 + r) * 3f;
                float y = at[1] + r * P + vy * s;
                int col = ch == 'd' ? 0x6A8A80 : (rng.nextInt(3) == 0 ? 0xE0F8FF : 0xBFF2DC);
                px(g, Math.round(x / P) * P, Math.round(y / P) * P, col, Math.round(alpha * 0.7f * (1f - s / 0.9f)));
            }
        }
        // A last crackle of the charge where it was.
        if (s < 0.25f) {
            for (int i = 0; i < 10; i++) {
                double ang = rng.nextFloat() * Math.PI * 2;
                float d = (8 + rng.nextFloat() * 14) * (0.4f + s * 3f);
                int x = Math.round((cx + (float) Math.cos(ang) * d) / P) * P;
                int y = Math.round((at[1] + 7 * P + (float) Math.sin(ang) * d) / P) * P;
                px(g, x, y, i % 2 == 0 ? 0xE0F8FF : 0x5FC8FF, Math.round(alpha * (1f - s / 0.25f)));
            }
        }
    }

    /** The drop, popping out where the Ghost was, with its name and price under it. */
    private static void item(GuiGraphicsExtractor g, int cx, int mistY, long t, int alpha) {
        float k = Math.min(1f, (t - POP) / (float) (POPPED - POP));
        float y = mistY - 22 - 14 * backOut(k) + (float) Math.sin(t / 380.0) * (k >= 1f ? 1.2f : 0f);
        float size = 26f * Math.min(1f, 0.4f + k);
        icon(g, cx, y, size, alpha);
        if (t < POPPED) return;
        float in = Math.min(1f, (t - POPPED) / 200f);
        int ta = Math.round(alpha * in);
        Font font = Minecraft.getInstance().font;
        text(g, font, Component.literal(drop.title).withStyle(net.minecraft.ChatFormatting.BOLD),
                cx, Math.round(y + size / 2 + 6), (ta << 24) | drop.colour);
        if (price > 0 && (ButcherClient.config == null || ButcherClient.config.ghostPrice)) {
            text(g, font, Component.literal(com.butcher.market.CorpseValue.coins(price) + " coins"),
                    cx, Math.round(y + size / 2 + 17), (ta << 24) | 0xC8B27A);
        }
    }

    private static final float[][] INK = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-0.71f, -0.71f}, {0.71f, -0.71f}, {-0.71f, 0.71f}, {0.71f, 0.71f}};

    /** Plasma's stand-in when Hypixel's pack is not loaded: a ball of violet charge (it is paper in vanilla). */
    private static final String[] ORB = {
            "...cccc...",
            "..cCCCCc..",
            ".cCWWCCCc.",
            "cCWWCCCCCc",
            "cCCCCCCCCc",
            "cCCCCCCCCc",
            "cCCCCCCCCc",
            ".cCCCCCCc.",
            "..cCCCCc..",
            "...cccc...",
    };

    private static void icon(GuiGraphicsExtractor g, float cx, float cy, float size, int alpha) {
        String path = drop == Drop.SORROW ? "textures/item/uncategorized/sorrow.png" : "textures/item/uncategorized/plasma.png";
        Identifier pack = Identifier.fromNamespaceAndPath("hypixel_skyblock", path);
        if (packIcon == null) packIcon = Minecraft.getInstance().getResourceManager().getResource(pack).isPresent();
        Identifier tex = packIcon ? pack : drop == Drop.SORROW ? Identifier.withDefaultNamespace("textures/item/ghast_tear.png") : null;
        if (tex == null) {
            orb(g, cx, cy, size, alpha);
            return;
        }
        float s = size / 16f, ink = Math.max(1.5f, size / 16f);
        int x = Math.round(cx - size / 2f), y = Math.round(cy - size / 2f);
        Matrix3x2fStack pose = g.pose();
        for (float[] o : INK) {
            pose.pushMatrix();
            pose.translate(x + o[0] * ink, y + o[1] * ink);
            pose.scale(s, s);
            g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, 16, 16, 16, 16, alpha << 24);
            pose.popMatrix();
        }
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(s, s);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, 16, 16, 16, 16, (alpha << 24) | 0xFFFFFF);
        pose.popMatrix();
    }

    /** The pixel orb, outlined, a spark crawling round it. */
    private static void orb(GuiGraphicsExtractor g, float cx, float cy, float size, int alpha) {
        int q = Math.max(1, Math.round(size / ORB.length));
        int left = Math.round(cx - ORB[0].length() * q / 2f), top = Math.round(cy - ORB.length * q / 2f);
        for (int r = 0; r < ORB.length; r++) {
            for (int c = 0; c < ORB[0].length(); c++) {
                if (ORB[r].charAt(c) == '.') continue;
                g.fill(left + c * q - 1, top + r * q - 1, left + c * q + q + 1, top + r * q + q + 1, alpha << 24 | 0x14101C);
            }
        }
        long t = System.currentTimeMillis();
        for (int r = 0; r < ORB.length; r++) {
            for (int c = 0; c < ORB[0].length(); c++) {
                char ch = ORB[r].charAt(c);
                if (ch == '.') continue;
                int col = ch == 'c' ? 0x5A2CA8 : ch == 'W' ? 0xF0E0FF : 0x9A5CF0;
                if (ch == 'C' && Math.floorMod(r * 3 + c + (int) (t / 80), 11) == 0) col = 0xD8F6FF;
                g.fill(left + c * q, top + r * q, left + c * q + q, top + r * q + q, alpha << 24 | col);
            }
        }
    }

    /** Centred text with a black outline, stopping short of the font's alpha floor. */
    private static void text(GuiGraphicsExtractor g, Font font, Component c, int cx, int y, int argb) {
        int a = argb >>> 24;
        if (a < 6) return;
        int x = cx - font.width(c) / 2;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                if (ox != 0 || oy != 0) g.text(font, c, x + ox, y + oy, a << 24, false);
            }
        }
        g.text(font, c, x, y, argb, false);
    }

    private static void px(GuiGraphicsExtractor g, int x, int y, int rgb, int alpha) {
        if (alpha < 6) return;
        g.fill(x, y, x + P, y + P, (Math.min(255, alpha) << 24) | (rgb & 0xFFFFFF));
    }

    private static float ease(float t) { return t * t * (3f - 2f * t); }

    private static float backOut(float t) {
        float c = 1.7f, u = t - 1f;
        return 1f + (c + 1f) * u * u * u + c * u * u;
    }

    // ================================================================== sound and price

    private static void sounds(long t) {
        if (!rose && t >= RISE) {
            rose = true;
            ButcherAudio.onGhostRise(seed);
        }
        if (!burst && t >= BURST) {
            burst = true;
            ButcherAudio.onGhostBurst(seed);
        }
        if (!revealed && t >= POP) {
            revealed = true;
            fixPrice();
            ButcherAudio.onGhostReveal();
        }
    }

    /** The bazaar's instant sell, if it came. */
    private static void fixPrice() {
        if (ButcherClient.config != null && !ButcherClient.config.livePrices) return;
        Double sell = com.butcher.market.Bazaar.sell(drop.priceId);
        if (sell != null && sell > 0) price = Math.round(sell);
    }
}
