package com.butcher.gui;

import com.butcher.gui.menu.Draw;
import com.butcher.render.TextureBank;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link Draw} over the game's GUI: what the menu and the Ledger's stamp pop-up draw through in game. Set {@link #g}
 * for the frame before drawing (and clear it after).
 */
public final class GuiDraw implements Draw {

    /** The mod's icon, scaled once (ButcherClient registers it). */
    public static final String TEX_ICON = "menu_icon";

    public GuiGraphicsExtractor g;
    private final Font font;
    private final Map<String, ItemStack> items = new HashMap<>();

    public GuiDraw(Font font) {
        this.font = font;
    }

    @Override public void fill(int x0, int y0, int x1, int y1, int argb) { g.fill(x0, y0, x1, y1, argb); }

    @Override
    public void text(String s, int x, int y, int argb, boolean bold, boolean shadow) {
        g.text(font, component(s, bold), x, y, argb, shadow);
    }

    @Override public int width(String s, boolean bold) { return font.width(component(s, bold)); }

    @Override public void item(String id, int x, int y) { g.item(stack(id), x, y); }

    @Override
    public void modIcon(int x, int y, int size) {
        Identifier id = TextureBank.id(TEX_ICON);
        if (id == null) return;
        int tw = TextureBank.width(TEX_ICON), th = TextureBank.height(TEX_ICON);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / (float) tw, size / (float) th);
        g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, tw, th, tw, th, 0xFFFFFFFF);
        g.pose().popMatrix();
    }

    @Override public void clip(int x0, int y0, int x1, int y1) { g.enableScissor(x0, y0, x1, y1); }
    @Override public void endClip() { g.disableScissor(); }
    @Override public void push() { g.pose().pushMatrix(); }
    @Override public void translate(float x, float y) { g.pose().translate(x, y); }
    @Override public void scale(float s) { g.pose().scale(s, s); }
    @Override public void rotate(float radians) { g.pose().rotate(radians); }
    @Override public void pop() { g.pose().popMatrix(); }

    private static Component component(String s, boolean bold) {
        return bold ? Component.literal(s).withStyle(ChatFormatting.BOLD) : Component.literal(s);
    }

    /** The cards', tabs' and stamps' icons: vanilla items, by registry path. */
    private ItemStack stack(String id) {
        return items.computeIfAbsent(id, k -> new ItemStack(switch (k) {
            case "amethyst_shard" -> Items.AMETHYST_SHARD;
            case "blue_ice" -> Items.BLUE_ICE;
            case "golden_shovel" -> Items.GOLDEN_SHOVEL;
            case "tropical_fish" -> Items.TROPICAL_FISH;
            case "ghast_tear" -> Items.GHAST_TEAR;
            case "magma_cream" -> Items.MAGMA_CREAM;
            case "diamond_sword" -> Items.DIAMOND_SWORD;
            case "blaze_rod" -> Items.BLAZE_ROD;
            case "ender_pearl" -> Items.ENDER_PEARL;
            case "gold_nugget" -> Items.GOLD_NUGGET;
            default -> Items.PAPER;
        }));
    }
}
