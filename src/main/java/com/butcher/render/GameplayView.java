package com.butcher.render;

import com.butcher.ButcherClient;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The game itself, live, in a small window in a corner of a cutscene - so a player can see
 * what is happening around them (and keep an eye on the next mob) while it plays.
 *
 * <h2>Where it comes from</h2>
 *
 * Ported from SkyMaxx's {@code GameplayView} (MIT licence, Copyright (c) 2026 SkyMaxx), which the
 * user asked for by name. The idea is theirs; three names changed on the way to 26.2 - the main
 * render target is {@code gameRenderer.mainRenderTarget()}, {@code GuiRenderer.render()} takes no
 * argument, and a texture's format is a {@code GpuFormat} - each checked with javap.
 *
 * <h2>How it works</h2>
 *
 * {@code mixin/GameRendererMixin} calls {@link #capture} each frame just before the GUI is drawn,
 * when the main render target holds the world and nothing else: its colour texture is copied into
 * one of ours. The cutscene then draws ours with {@link #draw}, which is also what asks for the
 * next frame's copy - so nothing is copied unless a cutscene is actually showing the window.
 * Anything going wrong turns it off for the session rather than risk the frame.
 */
public final class GameplayView {
    private GameplayView() {}

    /** Share of the screen's width the window takes. */
    private static final float WIDTH = 0.24f;
    private static final int MARGIN = 6;

    private static GpuTexture texture;
    private static GpuTextureView view;
    private static boolean captureWanted, broken;

    /** Which corner, from the config: 0 off, 1 top right, 2 top left, 3 bottom right, 4 bottom left. */
    public static int corner() {
        return ButcherClient.config == null ? 1 : ButcherClient.config.gameplayCorner;
    }

    /**
     * Draws the window.
     *
     * @param appear 0..1, how far it has slid in; 0 draws nothing
     * @param edge   the frame's colour, ARGB - each cutscene frames it in its own palette
     */
    public static void draw(GuiGraphicsExtractor g, float appear, int edge) {
        int corner = corner();
        if (broken || corner == 0) {
            release();
            return;
        }
        if (appear <= 0.01f) return;
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) return;
        try {
            if (texture == null || texture.isClosed() || texture.getWidth(0) != main.width || texture.getHeight(0) != main.height) {
                release();
                GpuTexture src = main.getColorTexture();
                if (src == null) return;
                GpuDevice device = RenderSystem.getDevice();
                texture = device.createTexture(() -> "Butcher gameplay view",
                        GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                        src.getFormat(), main.width, main.height, 1, 1);
                view = device.createTextureView(texture);
            }
        } catch (Throwable t) {
            ButcherClient.LOGGER.warn("[Butcher] could not make the gameplay view; turning it off", t);
            broken = true;
            release();
            return;
        }
        captureWanted = true;

        int sw = g.guiWidth(), sh = g.guiHeight();
        float a = 1f - (1f - appear) * (1f - appear) * (1f - appear);
        int w = Math.round(sw * WIDTH);
        int h = Math.round(w * main.height / (float) Math.max(1, main.width));
        boolean right = corner == 1 || corner == 3, top = corner == 1 || corner == 2;
        // It slides in from its own edge.
        float slide = (1f - a) * (w + MARGIN * 2);
        int x = Math.round(right ? sw - w - MARGIN + slide : MARGIN - slide);
        int y = top ? MARGIN + 2 : sh - h - MARGIN - 2;
        g.nextStratum();
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xF0000000);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, edge);
        g.fill(x, y, x + w, y + h, 0xFF000000);
        // v runs 1 to 0: GPU textures are stored bottom row first.
        g.blit(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x, y, x + w, y + h, 0f, 1f, 1f, 0f);
    }

    /** Copies the world into the window's texture. Called from the mixin, before the GUI is drawn. */
    public static void capture() {
        if (!captureWanted) return;
        captureWanted = false;
        if (broken || texture == null || texture.isClosed()) return;
        try {
            GpuTexture src = Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTexture();
            if (src == null) return;
            int w = Math.min(src.getWidth(0), texture.getWidth(0)), h = Math.min(src.getHeight(0), texture.getHeight(0));
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(src, texture, 0, 0, 0, 0, 0, w, h);
        } catch (Throwable t) {
            ButcherClient.LOGGER.warn("[Butcher] could not copy the game into the gameplay view; turning it off", t);
            broken = true;
        }
    }

    /** Frees the texture - when a cutscene closes, or the window is switched off. */
    public static void release() {
        captureWanted = false;
        try { if (view != null) view.close(); } catch (Throwable ignored) { }
        view = null;
        try { if (texture != null) texture.close(); } catch (Throwable ignored) { }
        texture = null;
    }
}
