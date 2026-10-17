package com.butcher.gui.menu;

import com.butcher.ledger.Grade;
import com.butcher.ledger.Stamp;

/**
 * The stamp pop-up's picture, frame by frame ({@code t} ms from its start) - pure, so it renders offline. The game's
 * side (the queue, when it may show, the sound) is render/StampPopup.
 */
public final class StampCard {
    private StampCard() {}

    // ---- timing, ms from the start
    public static final long FALL = 170, WORDS = 230, NAME = 320, FADE = 2500, END = 2950;

    public static void paint(Draw d, int w, int h, long t, Stamp s, int grade) {
        float fade = t < FADE ? 1f : 1f - (t - FADE) / (float) (END - FADE);
        int cx = w / 2, cy = Math.round(h * 0.22f) + 22, r = 22;
        // The fall: from big and turned, down onto the page; a small jolt as it lands.
        float fall = Math.min(1f, t / (float) FALL);
        float k = 1f + 1.6f * (1f - fall) * (1f - fall);
        float tilt = StampArt.tilt(s) + 0.5f * (1f - fall);
        float jolt = t < FALL ? 0f : (float) (Math.exp(-(t - FALL) / 70.0) * Math.sin((t - FALL) / 18.0) * 3);
        // A paper card behind it all, so it reads over any world.
        int cardA = Math.round(0xE0 * fade * Math.min(1f, t / 120f));
        if (cardA > 4) {
            int cw = 80, ch = 100;
            d.fill(cx - cw + 2, cy - 30 + 2, cx + cw + 2, cy + ch - 30 + 2, (cardA / 3) << 24);
            d.fill(cx - cw, cy - 30, cx + cw, cy + ch - 30, (cardA << 24) | (StampArt.PAPER & 0xFFFFFF));
            d.fill(cx - cw, cy - 30, cx + cw, cy - 29, (cardA << 24) | (s.chapter().colour & 0xFFFFFF));
        }
        d.push();
        d.translate(cx, cy + jolt);
        d.scale(k);
        StampArt.earned(d, 0, 0, r, s, grade, fall * fade, tilt);
        d.pop();
        // Ink flecks thrown out as it lands.
        if (t >= FALL && t < FALL + 420) {
            float f = (t - FALL) / 420f;
            int a = Math.round(255 * (1f - f) * fade);
            for (int i = 0; i < 14; i++) {
                double ang = i * Math.PI * 2 / 14 + i * 0.37;
                double dist = r + 4 + f * (10 + (i % 4) * 5);
                int x = cx + (int) Math.round(Math.cos(ang) * dist), y = cy + (int) Math.round(Math.sin(ang) * dist);
                d.fill(x, y, x + 2, y + 2, (a << 24) | (s.chapter().ink & 0xFFFFFF));
            }
        }
        int textA = Math.round(255 * fade);
        if (t >= WORDS && textA > 8) {
            String head = s.hidden() ? "SECRET STAMP!" : "STAMPED!";
            words(d, head, cx, cy + r + 6, 1.3f, s.chapter().ink, textA, true);
        }
        if (t >= NAME && textA > 8) {
            words(d, s.name(), cx, cy + r + 20, 1f, 0xFF3A2A1A, textA, true);
            Grade gr = s.tiered() ? Grade.of(grade) : null;
            String line = gr != null ? gr.title + " - " + s.chapter().title : s.chapter().title;
            words(d, line, cx, cy + r + 31, 1f, gr != null ? gr.ring : StampArt.PENCIL, textA, false);
        }
    }

    private static void words(Draw d, String s, int cx, int y, float k, int col, int a, boolean bold) {
        d.push();
        d.translate(cx - d.width(s, bold) * k / 2f, y);
        d.scale(k);
        d.text(s, 0, 0, (Math.max(5, a) << 24) | (col & 0xFFFFFF), bold, false);
        d.pop();
    }

    /**
     * The card that stands for every stamp past the third in one go (a backfill-sized burst would otherwise keep the
     * screen busy for a minute): no stamp, just the count, pointing at the book.
     */
    public static void paintMore(Draw d, int w, int h, long t, int n) {
        float fade = t < FADE ? 1f : 1f - (t - FADE) / (float) (END - FADE);
        int cx = w / 2, cy = Math.round(h * 0.22f) + 22;
        int cardA = Math.round(0xE0 * fade * Math.min(1f, t / 120f));
        if (cardA > 4) {
            int cw = 80, top = cy - 18, bottom = cy + 26;
            d.fill(cx - cw + 2, top + 2, cx + cw + 2, bottom + 2, (cardA / 3) << 24);
            d.fill(cx - cw, top, cx + cw, bottom, (cardA << 24) | (StampArt.PAPER & 0xFFFFFF));
            d.fill(cx - cw, top, cx + cw, top + 1, (cardA << 24) | 0xA0302A);
        }
        int textA = Math.round(255 * fade);
        if (textA > 8) {
            words(d, "+" + n + " more stamp" + (n == 1 ? "" : "s"), cx, cy - 10, 1.3f, 0xFFA0302A, textA, true);
            words(d, "in the Ledger - /butcher", cx, cy + 8, 1f, StampArt.PENCIL, textA, false);
        }
    }
}
