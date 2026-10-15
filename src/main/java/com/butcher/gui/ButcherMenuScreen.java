package com.butcher.gui;

import com.butcher.ButcherClient;
import com.butcher.config.PatternConfig;
import com.butcher.gui.menu.Draw;
import com.butcher.gui.menu.MenuView;
import com.butcher.render.TextureBank;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * The "/butcher" settings menu, as a Screen: everything it shows and does is {@link MenuView}, which knows nothing of
 * Minecraft; this only draws for it through {@code GuiGraphicsExtractor} and hands it the mouse.
 */
public class ButcherMenuScreen extends Screen {

    /** The mod's icon, scaled down smoothly once (a 256 px icon blitted at 24 px with nearest sampling is jagged). */
    public static final String TEX_ICON = "menu_icon";

    private final MenuView view = new MenuView(new MenuView.Host() {
        @Override public PatternConfig config() { return ButcherClient.config; }

        @Override
        public void playTest(String group, String name) {
            if (minecraft != null) minecraft.gui.setScreen(null);
            ButcherClient.playTest(group, name);
        }

        @Override public boolean canTry() { return minecraft != null && minecraft.level != null && minecraft.player != null; }

        @Override public void resetSettings() { ButcherClient.resetSettings(); }

        @Override
        public String dianaName(String verdict) {
            return com.butcher.render.DianaItems.name(com.butcher.anim.ChopTimeline.Verdict.valueOf(verdict));
        }

        @Override
        public String version() {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(ButcherClient.MOD_ID)
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        }
    });

    private GuiGraphicsExtractor g;
    private final Map<String, ItemStack> items = new HashMap<>();

    private final Draw draw = new Draw() {
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
    };

    /** Where Esc goes back to: Mod Menu's list when opened from there, otherwise nothing (back to the game). */
    private final Screen parent;

    public ButcherMenuScreen() {
        this(null);
    }

    public ButcherMenuScreen(Screen parent) {
        super(Component.literal("Butcher"));
        this.parent = parent;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private static Component component(String s, boolean bold) {
        return bold ? Component.literal(s).withStyle(ChatFormatting.BOLD) : Component.literal(s);
    }

    /** The cards' icons: vanilla items, by registry path. */
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
            default -> Items.PAPER;
        }));
    }

    @Override protected void init() { }
    @Override public boolean isPauseScreen() { return false; }

    /** Our own backdrop is drawn by the view; the vanilla blurred one would sit under it. */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) { }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        this.g = g;
        try {
            view.render(draw, width, height, mouseX, mouseY);
        } finally {
            this.g = null;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return view.mouseClicked(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return view.mouseDragged(event.x(), event.y(), event.button()) || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return view.mouseReleased(event.x(), event.y(), event.button()) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return view.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
