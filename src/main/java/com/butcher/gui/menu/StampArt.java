package com.butcher.gui.menu;

import com.butcher.ledger.Grade;
import com.butcher.ledger.Stamp;

/**
 * How a Ledger stamp looks, through {@link Draw} - shared by the book and the in-game pop-up, and drawable offline.
 *
 * A round ink stamp, the way a butcher stamps a carcass "Inspected and Passed": an outer ring (the grade's colour on a
 * tiered stamp), a thin paper ring inside it, the ink disc with the chapter's item in the middle, and the grade as
 * pips under it. Real ink is never even, so a few pixels of each stamp are missing - the same ones every time, picked
 * by the stamp's id. Every stamp sits a little crooked, again by its id.
 */
public final class StampArt {
    private StampArt() {}

    /** The cream of the Ledger's pages, and the pencil drawn on them. */
    public static final int PAPER = 0xFFEDE3CA, PENCIL = 0xFF8C7B5E;

    /** How crooked this stamp sits, in radians - fixed per stamp. */
    public static float tilt(Stamp s) {
        return (float) ((hash(s.id(), 1) - 0.5) * 0.36);
    }

    /**
     * An earned stamp centred on (cx, cy), radius {@code r}.
     *
     * @param grade 1-based; a tiered stamp's ring and pips show it
     * @param ink   0..1, how much ink - the pop-up fades it in
     */
    public static void earned(Draw d, int cx, int cy, int r, Stamp s, int grade, float ink, float tilt) {
        int a = Math.round(255 * clamp(ink));
        if (a < 6) return;
        int inkCol = s.chapter().ink;
        Grade g = s.tiered() ? Grade.of(grade) : null;
        int ring = g != null ? g.ring : inkCol;
        d.push();
        d.translate(cx, cy);
        d.rotate(tilt);
        disc(d, 0, 0, r, alpha(ring, a));
        disc(d, 0, 0, r - 2, alpha(PAPER, a));
        disc(d, 0, 0, r - 3, alpha(inkCol, Math.round(a * 0.92f)));
        ring(d, 0, 0, r - 5, 1, alpha(PAPER, Math.round(a * 0.55f)));
        // The chapter's item in the middle, as big as the stamp allows.
        if (a > 200) {
            float k = Math.max(0.5f, (r - (s.tiered() ? 9 : 7)) / 9f);
            d.push();
            d.translate(0, s.tiered() ? -2 : 0);
            d.scale(k);
            d.item(s.chapter().icon, -8, -8);
            d.pop();
        }
        // Worn ink: a few flecks of paper showing through, the same every time.
        for (int i = 0; i < r; i++) {
            double ang = hash(s.id(), 10 + i) * Math.PI * 2, rad = (0.35 + 0.6 * hash(s.id(), 40 + i)) * (r - 3);
            int x = (int) Math.round(Math.cos(ang) * rad), y = (int) Math.round(Math.sin(ang) * rad);
            if (Math.abs(x) < 7 && Math.abs(y) < 7) continue;
            d.fill(x, y, x + 1, y + 1, alpha(PAPER, Math.round(a * 0.7f)));
        }
        if (s.tiered()) {
            int n = s.grades(), gap = 4, w = (n - 1) * gap;
            for (int i = 0; i < n; i++) {
                int px = -w / 2 + i * gap, py = r - 6;
                d.fill(px - 1, py - 1, px + 2, py + 2, alpha(i < grade ? ring : PAPER, i < grade ? a : Math.round(a * 0.5f)));
            }
        }
        d.pop();
    }

    /** A stamp not earned yet: a dashed pencil circle, a "?" if it is hidden. */
    public static void empty(Draw d, int cx, int cy, int r, Stamp s, boolean hidden, int mx, int my) {
        int col = alpha(PENCIL, 0xB0);
        int dashes = Math.max(10, r * 2);
        for (int i = 0; i < dashes; i += 2) {
            double a0 = i * Math.PI * 2 / dashes;
            for (int k = 0; k < 3; k++) {
                double ang = a0 + k * Math.PI * 2 / dashes / 3;
                int x = cx + (int) Math.round(Math.cos(ang) * r), y = cy + (int) Math.round(Math.sin(ang) * r);
                d.fill(x, y, x + 1, y + 1, col);
            }
        }
        if (hidden) {
            int w = d.width("?", true);
            d.text("?", cx - w / 2 + 1, cy - 4, alpha(PENCIL, 0xD0), true, false);
        } else if (s.tiered()) {
            int n = s.grades(), gap = 4, w = (n - 1) * gap;
            for (int i = 0; i < n; i++) {
                int px = cx - w / 2 + i * gap, py = cy + r - 7;
                d.fill(px - 1, py - 1, px + 2, py + 2, alpha(PENCIL, 0x60));
            }
        }
    }

    // ------------------------------------------------------------------ hazard tape

    static final int TAPE = 0xFFF2C21B, TAPE_INK = 0xFF16161A;

    /**
     * Yellow and black hazard tape centred on (cx, cy), {@code half} long either side, turned {@code angle}, with
     * {@code label} on a plate of its own so the stripes do not run through it. The Seraph's card and chapter.
     */
    public static void hazardTape(Draw d, float cx, float cy, int half, float angle, String label) {
        int top = -6, bottom = 7, period = 12;
        d.push();
        d.translate(cx, cy);
        d.rotate(angle);
        d.fill(-half + 1, top + 2, half + 1, bottom + 2, 0x70000000);      // its shadow
        d.fill(-half, top, half, bottom, TAPE);
        for (int r = top; r < bottom; r++) {
            int shift = bottom - r;                                        // each row a pixel along: "/" stripes
            for (int sx = -half - period; sx < half; sx += period) {
                int a = Math.max(-half, sx + shift), b = Math.min(half, sx + shift + period / 2);
                if (a < b) d.fill(a, r, b, r + 1, TAPE_INK);
            }
        }
        d.fill(-half, top, half, top + 1, 0x40FFFFFF);                     // a sheen along its top edge
        int lw = d.width(label, true), px = lw / 2 + 6;
        d.fill(-px, top + 1, px, bottom - 1, TAPE);
        d.fill(-px, top + 1, -px + 1, bottom - 1, TAPE_INK);
        d.fill(px - 1, top + 1, px, bottom - 1, TAPE_INK);
        d.text(label, -lw / 2, top + 3, TAPE_INK, true, false);
        d.pop();
    }

    // ------------------------------------------------------------------ shapes

    /** A filled circle, a row at a time. */
    public static void disc(Draw d, int cx, int cy, int r, int argb) {
        if (r <= 0) return;
        for (int y = -r; y <= r; y++) {
            int w = (int) Math.floor(Math.sqrt(r * r - y * y + r * 0.8));
            d.fill(cx - w, cy + y, cx + w + 1, cy + y + 1, argb);
        }
    }

    /** A ring {@code t} pixels thick at radius {@code r} (outer edge). */
    public static void ring(Draw d, int cx, int cy, int r, int t, int argb) {
        int ri = r - t;
        for (int y = -r; y <= r; y++) {
            int wo = (int) Math.floor(Math.sqrt(r * r - y * y + r * 0.8));
            if (Math.abs(y) > ri) {
                d.fill(cx - wo, cy + y, cx + wo + 1, cy + y + 1, argb);
                continue;
            }
            int wi = (int) Math.floor(Math.sqrt(ri * ri - y * y + ri * 0.8));
            d.fill(cx - wo, cy + y, cx - wi, cy + y + 1, argb);
            d.fill(cx + wi + 1, cy + y, cx + wo + 1, cy + y + 1, argb);
        }
    }

    // ------------------------------------------------------------------ helpers

    static int alpha(int argb, int a) {
        int base = argb >>> 24;
        int out = Math.max(0, Math.min(255, base * a / 255));
        return (out << 24) | (argb & 0xFFFFFF);
    }

    static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** A number in 0..1 fixed by the stamp and a salt. */
    static double hash(String id, int salt) {
        long h = id.hashCode() * 0x9E3779B97F4A7C15L ^ (salt * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53);
    }
}
