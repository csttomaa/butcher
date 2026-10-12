package com.butcher.render;

import com.butcher.ButcherClient;
import com.butcher.audio.ButcherAudio;
import com.butcher.detect.SharkDrops.Drop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * "Fin" - the Fishing Festival announcement: a Megalodon pet or a Great White Shark Tooth dropped.
 *
 * Deliberately small and quick, the user's brief: no dim, no HUD hiding, no kill animation, about
 * 2.5 s, in the lower middle of the screen. A fin rises out of a strip of water, the Butcher's
 * cleaver drops on it, the water splashes and the drop pops out with its name and price.
 *
 * Every shape is pixel art drawn as squares (the rule since 0.2.29); only the drop itself is a
 * real item - the Megalodon's NEU skin on a 3D head, the tooth's own vanilla icon (a ghast tear),
 * adopted from the game at runtime, never bundled.
 *
 * <b>The bust</b> (0.2.59): one of the player's own Great White Sharks died with neither
 * (SharkWatcher). The fin rises, the cleaver comes down - and the fin ducks under at the last moment,
 * the blade splashes into empty water, a few bubbles come up. About 1.6 s; no text.
 *
 * <b>The pets' price is George's</b> (the user, 0.2.59): what the pet sells to George for, fixed -
 * {@link #GEORGE_LEGENDARY} and {@link #GEORGE_EPIC} - not the auction house.
 *
 * Not a cutscene: it does not count as one, so the HUD, chat and game sounds are left alone.
 * Drawn after the HUD by {@link ButcherClient#drawOwnHud}.
 */
public final class FinPopup {
    private FinPopup() {}

    // ---- timing, ms from the start
    static final long RISE = 260, FALL = 800, CHOP = 920, POP = 1020, POPPED = 1300, FADE = 2600, END = 3000;
    /** The bust: the fin ducks from here, and it is all over sooner. */
    static final long DUCK = CHOP - 150, BUST_FADE = 1350, BUST_END = 1650;

    /** What George pays for the Megalodon pet (the user, 2026-10-02). */
    public static final long GEORGE_LEGENDARY = 2_500_000, GEORGE_EPIC = 500_000;

    /** GUI pixels per art pixel. */
    private static final int P = 2;

    private static Drop drop;
    /** Playing the bust (drop is null then). */
    private static boolean bust;
    private static long startedAt = -1;
    private static long seed;
    private static boolean rose, chopped, revealed;
    private static long price = -1;


    /** Plays it for a drop. A second drop while one shows starts it again. */
    public static void show(Drop d) {
        drop = d;
        bust = false;
        startedAt = System.currentTimeMillis();
        seed = startedAt * 31 + d.ordinal();
        rose = chopped = revealed = false;
        price = -1;
        // Asked for now, read at the reveal - never waited on.
        if ((ButcherClient.config == null || ButcherClient.config.livePrices) && !d.pet()) {
            com.butcher.market.Bazaar.request();
        }
    }

    /** Plays the bust - unless a drop is showing, which it never interrupts. */
    public static void showBust() {
        if (active() && !bust) return;
        drop = null;
        bust = true;
        startedAt = System.currentTimeMillis();
        seed = startedAt * 31 + 7;
        rose = chopped = revealed = false;
        price = -1;
    }

    public static boolean active() {
        if (bust) return System.currentTimeMillis() - startedAt < BUST_END;
        return drop != null && System.currentTimeMillis() - startedAt < END;
    }

    public static void draw(GuiGraphicsExtractor g) {
        if (!active()) {
            drop = null;
            bust = false;
            return;
        }
        long t = System.currentTimeMillis() - startedAt;
        sounds(t);
        int w = g.guiWidth(), h = g.guiHeight();
        int cx = w / 2;
        // Above the hotbar, the bars over it and some air.
        int waterY = h - 64;
        long fadeAt = bust ? BUST_FADE : FADE, end = bust ? BUST_END : END;
        float fade = t < fadeAt ? 1f : 1f - (t - fadeAt) / (float) (end - fadeAt);
        int alpha = Math.round(255 * Math.max(0f, Math.min(1f, fade)));
        if (alpha < 6) return;
        if (bust) {
            water(g, cx, waterY, t, alpha);
            if (t < CHOP) fin(g, cx, waterY, t, alpha);
            missedCleaver(g, cx, waterY, t, alpha);
            if (t >= CHOP) {
                splash(g, cx, waterY, t, alpha);
                bubbles(g, cx, waterY, t, alpha);
            }
            return;
        }

        water(g, cx, waterY, t, alpha);
        if (t < CHOP) fin(g, cx, waterY, t, alpha);
        else halves(g, cx, waterY, t, alpha);
        cleaver(g, cx, waterY, t, alpha);
        if (t >= CHOP) splash(g, cx, waterY, t, alpha);
        if (t >= POP) item(g, cx, waterY, t, alpha);
    }

    // ================================================================== art

    /** A strip of water, two rows, a ripple running along it. In from the middle over 150 ms. */
    private static void water(GuiGraphicsExtractor g, int cx, int y, long t, int alpha) {
        int half = Math.round(15 * Math.min(1f, t / 150f));
        for (int i = -half; i <= half; i++) {
            boolean crest = Math.floorMod(i + (int) (t / 90), 5) == 0;
            int top = crest ? 0xBFEAFF : 0x5FB8E8;
            px(g, cx + i * P - P / 2, y, top, alpha);
            px(g, cx + i * P - P / 2, y + P, 0x2A78B8, alpha);
        }
    }

    private static final String[] FIN = {
            "...#....",
            "...##...",
            "..+##...",
            "..+###..",
            ".+####..",
            ".+#####.",
            "+######.",
            "+#######",
            "+#######",
    };

    /** The fin rising out of the water, with a wobble; nothing below the water line is drawn. */
    private static void fin(GuiGraphicsExtractor g, int cx, int waterY, long t, int alpha) {
        float r = ease(Math.min(1f, t / (float) RISE));
        // The bust: it sees the blade coming and is gone under the water before it lands.
        if (bust && t > DUCK) r *= 1f - ease(Math.min(1f, (t - DUCK) / 110f));
        int sink = Math.round((1f - r) * FIN.length) * P;
        int wobble = Math.round((float) Math.sin(t / 55.0) * (t < CHOP - 60 ? 1 : 0)) * P;
        int left = cx - 4 * P + wobble, top = waterY - FIN.length * P + sink;
        sprite(g, FIN, left, top, waterY, alpha, 0, FIN[0].length());
    }

    /** Cut down the middle: the two halves fall apart and sink. */
    private static void halves(GuiGraphicsExtractor g, int cx, int waterY, long t, int alpha) {
        float k = Math.min(1f, (t - CHOP) / 260f);
        if (k >= 1f) return;
        int apart = Math.round(k * 6) * P, drop = Math.round(k * k * FIN.length) * P;
        int top = waterY - FIN.length * P + drop;
        int a = Math.round(alpha * (1f - k));
        sprite(g, FIN, cx - 4 * P - apart, top, waterY, a, 0, 4);
        sprite(g, FIN, cx - 4 * P + apart, top, waterY, a, 4, 8);
    }

    private static final String[] CLEAVER = {
            "########....",
            "#LLLLLL#....",
            "#LLLLLL#oooo",
            "#LLLLLL#hhhh",
            "#LLLLLL#oooo",
            "EEEEEEEE....",
    };

    /** The cleaver drops straight down onto the fin, rests a moment, then lifts away. */
    private static void cleaver(GuiGraphicsExtractor g, int cx, int waterY, long t, int alpha) {
        if (t < FALL - 120 || t > CHOP + 150) return;
        int rest = waterY - 5 * P - CLEAVER.length * P;
        int y;
        int a = alpha;
        if (t < CHOP) {
            float k = (t - (FALL - 120)) / (float) (CHOP - (FALL - 120));
            y = Math.round(rest - 46 + 46 * k * k);
            a = Math.round(alpha * Math.min(1f, k * 3f));
        } else if (t < CHOP + 50) {
            y = rest;
        } else {
            // Up and gone before the drop comes out of the water under it.
            float k = (t - CHOP - 50) / 100f;
            y = Math.round(rest - 24 * k);
            a = Math.round(alpha * (1f - k));
        }
        sprite(g, CLEAVER, cx - 4 * P, y, Integer.MAX_VALUE, a, 0, CLEAVER[0].length());
    }

    /**
     * The bust's cleaver: the same fall, but nothing stops it - it goes on into the water (cut off at the
     * surface), rests a beat half under, and lifts away.
     */
    private static void missedCleaver(GuiGraphicsExtractor g, int cx, int waterY, long t, int alpha) {
        if (t < FALL - 120 || t > CHOP + 330) return;
        int rest = waterY - 5 * P - CLEAVER.length * P;
        int under = waterY - 4 * P;
        int y;
        int a = alpha;
        if (t < CHOP) {
            float k = (t - (FALL - 120)) / (float) (CHOP - (FALL - 120));
            y = Math.round(rest - 46 + (under - rest + 46) * k * k);
            a = Math.round(alpha * Math.min(1f, k * 3f));
        } else if (t < CHOP + 180) {
            y = under;
        } else {
            float k = (t - CHOP - 180) / 150f;
            y = Math.round(under - 30 * k);
            a = Math.round(alpha * (1f - k));
        }
        sprite(g, CLEAVER, cx - 4 * P, y, waterY, a, 0, CLEAVER[0].length());
    }

    /** A few bubbles coming up where the shark went down, popping at the surface. */
    private static void bubbles(GuiGraphicsExtractor g, int cx, int waterY, long t, int alpha) {
        java.util.Random rng = new java.util.Random(seed + 5);
        for (int i = 0; i < 4; i++) {
            long at = CHOP + 120 + i * 110 + rng.nextInt(60);
            float k = (t - at) / 260f;
            int x = cx + (rng.nextInt(9) - 4) * P;
            if (k < 0f || k > 1f) continue;
            int y = waterY - Math.round(k * 5) * P;
            int c = k > 0.8f ? 0xE8F7FF : 0x9FD8F5;
            px(g, x, y, c, Math.round(alpha * (1f - k * 0.5f)));
            if (k > 0.8f) {
                px(g, x - P, y, 0xE8F7FF, alpha / 2);
                px(g, x + P, y, 0xE8F7FF, alpha / 2);
            }
        }
    }

    /** A burst of water squares off the chop, falling back. */
    private static void splash(GuiGraphicsExtractor g, int cx, int waterY, long t, int alpha) {
        float s = (t - CHOP) / 1000f;
        if (s > 0.7f) return;
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 16; i++) {
            float vx = (rng.nextFloat() * 2f - 1f) * 70f;
            float vy = -(70f + rng.nextFloat() * 90f);
            float x = cx + vx * s, y = waterY + vy * s + 320f * s * s;
            if (y > waterY + P) continue;
            int c = rng.nextInt(3) == 0 ? 0xE8F7FF : 0x7FCBF2;
            px(g, Math.round(x / P) * P, Math.round(y / P) * P, c, Math.round(alpha * (1f - s / 0.7f)));
        }
    }

    /** The drop, popping out of the splash, with its name and price under it. */
    private static void item(GuiGraphicsExtractor g, int cx, int waterY, long t, int alpha) {
        float k = Math.min(1f, (t - POP) / (float) (POPPED - POP));
        float y = waterY - 6 - 30 * backOut(k);
        float size = 26f * Math.min(1f, 0.4f + k);
        if (drop.pet()) head(g, cx, y, size, t, alpha);
        else icon(g, cx, y, size, alpha);
        if (t < POPPED) return;
        float in = Math.min(1f, (t - POPPED) / 200f);
        int ta = Math.round(alpha * in);
        Font font = Minecraft.getInstance().font;
        text(g, font, Component.literal(drop.title).withStyle(net.minecraft.ChatFormatting.BOLD),
                cx, Math.round(y + size / 2 + 6), (ta << 24) | drop.colour);
        if (price > 0 && (ButcherClient.config == null || ButcherClient.config.fishingPrice)) {
            String label = com.butcher.market.CorpseValue.coins(price) + " coins" + (drop.pet() ? " at George" : "");
            text(g, font, Component.literal(label),
                    cx, Math.round(y + size / 2 + 17), (ta << 24) | 0xC8B27A);
        }
    }

    private static com.butcher.render.MeshRenderer meshes;
    private static final com.butcher.render.model.Mesh HEAD = com.butcher.render.model.PlayerHeadMesh.head();
    private static final float HEAD_SPAN = com.butcher.render.model.PlayerHeadMesh.HEAD
            + 2 * com.butcher.render.model.PlayerHeadMesh.HAT_INFLATE;

    /**
     * The Megalodon as a real 3D head with its NEU skin, held three-quarters on with a gentle sway -
     * its skin wraps round the cube (eyes on the sides, the mouth across the front corner), so a full
     * spin showed its back. It looked "weirdly put" until 0.2.53 because PlayerHeadMesh had the two
     * side textures swapped; the game's own item renderer drew it right but flat and pixelated.
     */
    private static void head(GuiGraphicsExtractor g, float cx, float cy, float size, long t, int alpha) {
        String skin = HeadSkins.FISHING.texture("Megalodon");
        if (skin == null) return;
        if (meshes == null) meshes = new com.butcher.render.MeshRenderer();
        float yaw = 35f + 9f * (float) Math.sin((t - POP) / 520.0);
        meshes.begin();
        meshes.camera(com.butcher.render.model.ModelProjection.Camera.of(yaw, -22f, size / (HEAD_SPAN * 1.4f)));
        meshes.add(HEAD, skin, 0f, 0f, 0f, 0f, (alpha << 24) | 0xFFFFFF, true, false, true);
        meshes.flush(g, cx, cy, alpha << 24, Math.max(1.5f, size / 16f));
    }

    private static final float[][] INK = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-0.71f, -0.71f}, {0.71f, -0.71f}, {-0.71f, 0.71f}, {0.71f, 0.71f}};

    /** The tooth's own 16 px icon, blown up and outlined - the game's ghast tear, adopted. */
    private static void icon(GuiGraphicsExtractor g, float cx, float cy, float size, int alpha) {
        Identifier tex = Identifier.withDefaultNamespace("textures/item/ghast_tear.png");
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

    /**
     * A pixel-art sprite from rows of characters, columns {@code [from, to)}, with a one-pixel
     * dark outline; nothing at or below {@code clipY} (the water line) is drawn.
     */
    private static void sprite(GuiGraphicsExtractor g, String[] rows, int left, int top, int clipY,
                               int alpha, int from, int to) {
        if (alpha < 6) return;
        int outline = 0x14181C;
        for (int pass = 0; pass < 2; pass++) {
            for (int r = 0; r < rows.length; r++) {
                for (int c = from; c < to; c++) {
                    char ch = rows[r].charAt(c);
                    if (ch == '.') continue;
                    int x = left + c * P, y = top + r * P;
                    if (pass == 0) {
                        for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                            int ox = x + o[0] * P, oy = y + o[1] * P;
                            if (oy + P <= clipY) px(g, ox, oy, outline, alpha);
                        }
                    } else if (y + P <= clipY) {
                        px(g, x, y, colour(ch), alpha);
                    }
                }
            }
        }
    }

    private static int colour(char ch) {
        return switch (ch) {
            case '+' -> 0x9DB2C2;   // the fin's lit edge
            case '#' -> 0x55687A;   // the fin, or the cleaver's rim
            case 'L' -> 0xC9D1D9;   // steel
            case 'E' -> 0xF2F6FA;   // the edge
            case 'h' -> 0x7A4E2C;   // handle
            case 'o' -> 0x4A2E18;   // handle, shaded
            default -> 0xFF00FF;
        };
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
        if (!rose && t >= 0) {
            rose = true;
            ButcherAudio.onFinRise();
        }
        if (!chopped && t >= CHOP) {
            chopped = true;
            if (bust) ButcherAudio.onFinMiss(seed);
            else ButcherAudio.onFinChop(seed);
        }
        if (bust) return;
        if (!revealed && t >= POP) {
            revealed = true;
            fixPrice();
            ButcherAudio.onFinReveal(drop == Drop.MEGALODON_LEGENDARY);
        }
    }

    /** George's fixed price for the pet; the bazaar's instant sell for the tooth, if it came. */
    private static void fixPrice() {
        if (drop.pet()) {
            price = drop == Drop.MEGALODON_LEGENDARY ? GEORGE_LEGENDARY : GEORGE_EPIC;
            return;
        }
        if (ButcherClient.config != null && !ButcherClient.config.livePrices) return;
        Double sell = com.butcher.market.Bazaar.sell(drop.priceId);
        if (sell != null && sell > 0) price = Math.round(sell);
    }
}
