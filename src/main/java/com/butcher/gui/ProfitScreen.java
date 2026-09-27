package com.butcher.gui;

import com.butcher.detect.vanguard.CorpseHaul;
import com.butcher.market.CorpseValue;
import com.butcher.market.CorpseValue.Line;
import com.butcher.market.CorpseValue.Result;
import com.butcher.market.CorpseValue.Source;
import com.butcher.render.MeshRenderer;
import com.butcher.render.VanguardItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * The corpse's profit or loss, shown when the Thaw ends.
 *
 * Every drop on its own line - its real 3D icon, its name in its rarity colour, where it was
 * priced and what it is worth - then the Skeleton Key it cost, and the net in green or red.
 * Most valuable first, so a Locket is at the top rather than lost among the gemstones.
 *
 * Priced again every frame from {@link CorpseValue}, so a price that is still on its way
 * shows as "..." and fills in when it lands; the answers normally arrive during the Thaw.
 *
 * Any key or click closes it. It is not a pause screen, and nothing in it can be misclicked.
 */
public class ProfitScreen extends Screen {

    private final CorpseHaul haul;
    private final long openedAt = System.currentTimeMillis();
    private final MeshRenderer meshes = new MeshRenderer();

    private static final int PANEL_W = 290, ROW = 13, PAD = 10;
    private static final int GREEN = 0x55FF55, RED = 0xFF5555, GREY = 0x8A97A8, LIGHT = 0xDDE6F0, GOLD = 0xFFAA00;

    public ProfitScreen(CorpseHaul haul) {
        super(Component.literal("Corpse profit"));
        this.haul = haul;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) { }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        Result r = CorpseValue.of(haul);
        float in = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
        in = in * in * (3 - 2 * in);

        int rows = r.lines().size();
        int h = PAD + 22 + rows * ROW + 8 + ROW * 2 + 6 + 20 + 8 + 20 + PAD;
        int x0 = (width - PANEL_W) / 2, y0 = (height - h) / 2;

        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        // Drops in from a little above as it fades up.
        pose.translate(0f, (1f - in) * -12f);

        g.fill(0, 0, width, height, argb(0.45f * in, 0x000000));
        g.fill(x0, y0, x0 + PANEL_W, y0 + h, argb(0.92f * in, 0x0B1320));
        border(g, x0, y0, PANEL_W, h, argb(in, 0x3A5A7A));

        int y = y0 + PAD;
        text(g, "Vanguard Corpse", x0 + PAD, y, argb(in, GOLD));
        String sub = haul.rolls() + " drops";
        text(g, sub, x0 + PANEL_W - PAD - font.width(sub), y, argb(in, GREY));
        y += 14;
        g.fill(x0 + PAD, y, x0 + PANEL_W - PAD, y + 1, argb(0.6f * in, 0x3A5A7A));
        y += 8;

        for (Line line : r.lines()) {
            CorpseHaul.Drop d = line.drop();
            VanguardItems.draw(g, meshes, d, x0 + PAD + 5f, y + 4f, 10f, VanguardItems.REST_YAW,
                    Math.round(255 * in), false);
            String name = d.name() + (d.amount() > 1 ? " §8x" + String.format("%,d", d.amount()) : "");
            text(g, name, x0 + PAD + 14, y, argb(in, d.entry().colour()));

            String value, each = "";
            int colour = LIGHT;
            switch (line.source()) {
                case PENDING -> { value = "..."; colour = GREY; }
                case UNSELLABLE -> { value = "0"; colour = GREY; each = "not sellable"; }
                case UNKNOWN -> { value = "?"; colour = GREY; each = "no price"; }
                default -> {
                    value = CorpseValue.coins(line.total());
                    each = (line.source() == Source.BAZAAR ? "BZ" : "AH")
                            + (d.amount() > 1 ? " " + CorpseValue.coins(Math.round(line.unit())) + " ea" : "");
                }
            }
            int vx = x0 + PANEL_W - PAD - font.width(value);
            text(g, value, vx, y, argb(in, colour));
            if (!each.isEmpty()) {
                small(g, each, vx - 4 - Math.round(font.width(each) * 0.75f), y + 2, argb(in, GREY));
            }
            y += ROW;
        }

        y += 2;
        g.fill(x0 + PAD, y, x0 + PANEL_W - PAD, y + 1, argb(0.6f * in, 0x3A5A7A));
        y += 6;
        row(g, x0, y, "Drops", r.complete() ? CorpseValue.coins(r.items()) : "...", LIGHT, in);
        y += ROW;
        String key = r.key() >= 0 ? "-" + CorpseValue.coins(r.key())
                : r.keySource() == Source.PENDING ? "..." : "?";
        row(g, x0, y, "Skeleton Key §8(bazaar buy)", key, RED, in);
        y += ROW + 6;

        // The verdict, big.
        boolean known = r.complete() && r.key() >= 0;
        String label = !known ? "Working it out..." : r.net() >= 0 ? "Profit" : "Loss";
        String net = !known ? "" : (r.net() >= 0 ? "+" : "") + CorpseValue.coins(r.net());
        int netColour = !known ? GREY : r.net() >= 0 ? GREEN : RED;
        pose.pushMatrix();
        pose.translate(x0 + PAD, y);
        pose.scale(1.5f, 1.5f);
        g.text(font, Component.literal(label), 0, 0, argb(in, netColour), true);
        pose.popMatrix();
        if (!net.isEmpty()) {
            pose.pushMatrix();
            pose.translate(x0 + PANEL_W - PAD - font.width(net) * 1.5f, y);
            pose.scale(1.5f, 1.5f);
            g.text(font, Component.literal(net), 0, 0, argb(in, netColour), true);
            pose.popMatrix();
        }
        y += 20;

        small(g, "Bazaar: instant-sell (Hypixel)   Auctions: lowest BIN (Coflnet)", x0 + PAD, y, argb(in, GREY));
        y += 9;
        small(g, "Any key or click to close", x0 + PAD, y, argb(in, GREY));

        pose.popMatrix();
    }

    private void row(GuiGraphicsExtractor g, int x0, int y, String label, String value, int colour, float in) {
        text(g, label, x0 + PAD, y, argb(in, LIGHT));
        text(g, value, x0 + PANEL_W - PAD - font.width(value), y, argb(in, colour));
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int argb) {
        // The font renderer treats alpha below 4 as opaque; stop drawing near zero instead.
        if ((argb >>> 24) < 6) return;
        g.text(font, Component.literal(s), x, y, argb, true);
    }

    /** Three-quarter size, for tags and footnotes. */
    private void small(GuiGraphicsExtractor g, String s, int x, int y, int argb) {
        if ((argb >>> 24) < 6) return;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(0.75f, 0.75f);
        g.text(font, Component.literal(s), 0, 0, argb, false);
        pose.popMatrix();
    }

    private static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
        g.fill(x, y, x + w, y + 1, argb);
        g.fill(x, y + h - 1, x + w, y + h, argb);
        g.fill(x, y, x + 1, y + h, argb);
        g.fill(x + w - 1, y, x + w, y + h, argb);
    }

    private static int argb(float a, int rgb) {
        int alpha = Math.round(255 * Math.max(0f, Math.min(1f, a)));
        return alpha << 24 | (rgb & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ closing

    private boolean closed;

    /** Whether it has been closed or replaced - what lets the next queued corpse play. */
    public boolean isClosed() { return closed; }

    @Override
    public void removed() {
        closed = true;
        super.removed();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        onClose();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        onClose();
        return true;
    }
}
