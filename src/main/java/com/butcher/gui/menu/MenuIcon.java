package com.butcher.gui.menu;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** The mod icon for the menu's title, scaled down with averaging so it stays smooth at 24 GUI pixels. */
public final class MenuIcon {
    private MenuIcon() {}

    /** {@code icon} scaled to {@code size} square in halving steps (each a clean average); a blank if it is null. */
    public static BufferedImage scaled(BufferedImage icon, int size) {
        BufferedImage cur = icon;
        if (cur == null) return new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int w = cur.getWidth();
        while (w / 2 >= size) {
            w /= 2;
            cur = resize(cur, w);
        }
        return w == size ? cur : resize(cur, size);
    }

    private static BufferedImage resize(BufferedImage in, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(in, 0, 0, size, size, null);
        g.dispose();
        return out;
    }
}
