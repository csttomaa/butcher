package com.butcher.gui;

import com.butcher.ButcherClient;
import com.butcher.config.PatternConfig;
import com.butcher.gui.menu.MenuView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;


/**
 * The "/butcher" settings menu, as a Screen: everything it shows and does is {@link MenuView}, which knows nothing of
 * Minecraft; this only draws for it through {@code GuiGraphicsExtractor} and hands it the mouse.
 */
public class ButcherMenuScreen extends Screen {

    /** The mod's icon (32 x 32 pixel art, registered in ButcherClient). */
    public static final String TEX_ICON = GuiDraw.TEX_ICON;

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

    private final GuiDraw draw = new GuiDraw(net.minecraft.client.Minecraft.getInstance().font);

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

    @Override protected void init() { }
    @Override public boolean isPauseScreen() { return false; }

    /** Our own backdrop is drawn by the view; the vanilla blurred one would sit under it. */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) { }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        draw.g = g;
        try {
            view.render(draw, width, height, mouseX, mouseY);
        } finally {
            draw.g = null;
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
