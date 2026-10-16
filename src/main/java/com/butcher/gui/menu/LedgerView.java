package com.butcher.gui.menu;

import com.butcher.Maintenance;
import com.butcher.config.ButcherStats;
import com.butcher.ledger.Chapter;
import com.butcher.ledger.Facts;
import com.butcher.ledger.Grade;
import com.butcher.ledger.Ledger;
import com.butcher.ledger.LedgerData;
import com.butcher.ledger.Stamp;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Butcher's Ledger: a leather-bound book of stamps, one chapter per animation, shown on its own over the darkened
 * game (MenuView hides the menu while it is up).
 *
 * It drops in shut, a little turned like a book put down on a desk, and swings open. The left page is the chapter's
 * title page - or, once a stamp is clicked, that stamp's own page - and the right page the chapter's stamps, four to a
 * row. Coloured divider tabs stand out of the right edge, one per chapter, and a red ribbon on the left page shuts the
 * book and puts it away. The pages turn: click or drag a folded corner, or scroll, and a sheet lifts off the page and
 * over the spine, showing the next page on its back. Turning runs through the whole book, page after page and chapter
 * after chapter. Pages are ruled like a ledger, with a red margin and somebody's pencil notes.
 *
 * Laid out at a fixed size ({@link #W} x {@link #H}) and scaled to the screen in quarter steps, so it looks the same
 * at every GUI scale; the mouse is turned back into book coordinates the same way, tilt and all. No Minecraft in here,
 * like the rest of the menu, so it renders offline.
 */
final class LedgerView {

    /** The book's own size, before it is scaled to fit. */
    static final int W = 412, H = 250;
    private static final int COVER = 5, PAGE_W = 190, SPINE = 10;
    private static final int LEFT = COVER, SPINE_X = COVER + PAGE_W, RIGHT = SPINE_X + SPINE, BOOK_R = RIGHT + PAGE_W + COVER;
    private static final int TAB_X = BOOK_R - 6, TAB_W = 15, TAB_H = 24, TAB_GAP = 2, TAB_TOP = 10;
    private static final int COLS = 4, ROWS = 3, PER_PAGE = COLS * ROWS;
    /** A folded page corner, and how far round it a click or a drag picks the page up. */
    private static final int CORNER = 7, CORNER_HOVER = 12, CORNER_HIT = 26;

    /** How far the book sits from straight when it is still: a book put down on a desk. */
    static final float TILT = -0.02f;
    private static final long ENTER_MS = 460, LEAVE_MS = 300;
    /** Seconds for a whole page turn, and how far a dragged page must go to turn rather than fall back. */
    private static final float TURN_TIME = 0.5f, TURN_COMMIT = 0.3f;

    private static final int LEATHER = 0xFF5A2D1B, LEATHER_DARK = 0xFF3A1C10, LEATHER_EDGE = 0xFF2A140B;
    private static final int GOLD = 0xFFE3B456, GOLD_DIM = 0xFF9C7430;
    private static final int PAPER = StampArt.PAPER, PAPER_SHADE = 0xFFDCCFAF, RULE = 0x2A6B5432, MARGIN = 0x55B0302A;
    private static final int INK = 0xFF3A2A1A, PENCIL = StampArt.PENCIL, RED_INK = 0xFFB0302A, RIBBON = 0xFF9E1E24;
    private static final int COFFEE = 0x6B4A2A;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    // The book is where it was left: the chapter, the page, the stamp being read.
    private static boolean open;
    private static Chapter chapter = Chapter.HOUSE;
    private static String selected;
    private static int page;
    /** 0 = shut, 1 = open; eased towards {@link #open}. */
    private static float openness;
    /** When it came on screen and when it was sent away (-1: not). */
    private static long enteredAt = -1, leftAt = -1;
    /** The ribbon shut it: once the cover is down, the book is put away. */
    private static boolean shutByRibbon, leaveWanted;

    /** A place in the book: a chapter and one of its pages of stamps. */
    private record Spot(Chapter chapter, int page) { }

    /** A page being turned: from where, to where, which way, how far (0..1), and whether a hand holds it. */
    private static Spot turnFrom, turnTo;
    private static boolean turnForward, turnHeld, turnMoved;
    private static float turnP, turnTarget;
    private static double pressX, pressY;

    private long lastFrame = System.nanoTime();
    // The transform of the last frame, for the mouse.
    private float cx, cy, k = 1f, tilt;

    /** On screen: it drops in shut and swings open. */
    void enter() {
        enteredAt = System.currentTimeMillis();
        leftAt = -1;
        open = true;
        openness = 0f;
        shutByRibbon = leaveWanted = false;
        turnFrom = turnTo = null;
    }

    /** Sent away (Esc, the back button): it drops out of sight. */
    void leave() {
        if (leftAt >= 0) return;
        markSeen();
        leftAt = System.currentTimeMillis();
        finishTurn(true);
    }

    /** Gone after {@link #leave}: the menu can come back. */
    boolean gone() {
        return leftAt >= 0 && System.currentTimeMillis() - leftAt >= LEAVE_MS;
    }

    /** The ribbon shut it and the cover is down: time to put it away. */
    boolean wantsLeave() {
        return leaveWanted && leftAt < 0;
    }

    // ------------------------------------------------------------------ drawing

    /** Draws the book over a {@code sw} x {@code sh} screen, between a strip at the top and one at the bottom. */
    void draw(Draw d, int sw, int sh, int top, int bottom, int mx, int my) {
        long nowNs = System.nanoTime();
        float dt = Math.min(0.1f, (nowNs - lastFrame) / 1e9f);
        lastFrame = nowNs;
        long now = System.currentTimeMillis();
        if (enteredAt < 0) enter();
        float enter = clamp01((now - enteredAt) / (float) ENTER_MS);
        float in = backOut(enter);
        float out = leftAt < 0 ? 0f : ease(clamp01((now - leftAt) / (float) LEAVE_MS));

        // It opens once it has landed; the ribbon's shut puts it away once the cover is down.
        if (enter > 0.65f) {
            float target = open ? 1f : 0f;
            openness += Math.signum(target - openness) * Math.min(Math.abs(target - openness), dt / 0.42f);
        }
        if (!open && openness <= 0f && shutByRibbon) leaveWanted = true;
        turnTick(dt);

        // As big as the screen allows, in quarter steps so the text keeps an even size.
        float fit = Math.min((sw - 24f) / W, (sh - top - bottom - 8f) / H);
        float scale = Math.max(0.75f, Math.min(4f, (float) Math.floor(fit * 4f) / 4f));
        if (scale > fit) scale = Math.max(0.5f, fit);
        cx = sw / 2f;
        cy = top + (sh - top - bottom) / 2f + (1f - in) * sh * 0.55f + out * sh * 0.75f;
        float o = ease(openness);
        // Turned a little at rest; more while it lands, leaves, opens or a page goes over.
        tilt = TILT - (1f - in) * 0.1f + out * 0.09f + 0.012f * (float) Math.sin(Math.PI * o)
                + (turnFrom == null ? 0f : (turnForward ? 0.008f : -0.008f) * (float) Math.sin(Math.PI * turnP));
        k = scale * (0.9f + 0.1f * Math.min(1f, in)) * (1f - 0.08f * out);

        float[] b = toBook(mx, my);
        d.push();
        d.translate(cx, cy);
        d.rotate(tilt);
        d.scale(k);
        d.translate(-W / 2f, -H / 2f);
        float sx = closedShift() * (1f - o);
        d.translate(sx, 0);
        ButcherStats st = ButcherStats.get();
        book(d, st.ledger(), st.facts(), o, b[0], b[1]);
        d.pop();
    }

    /** A point on the screen, in the book's own coordinates (the shut book's shift taken off). */
    private float[] toBook(double mx, double my) {
        double x = mx - cx, y = my - cy, c = Math.cos(tilt), s = Math.sin(tilt);
        float bx = (float) ((x * c + y * s) / k + W / 2.0), by = (float) ((-x * s + y * c) / k + H / 2.0);
        return new float[]{bx - closedShift() * (1f - ease(openness)), by};
    }

    /** How far the shut book moves to sit in the middle (only the front cover shows). */
    private static float closedShift() {
        return W / 2f - (SPINE_X + BOOK_R) / 2f;
    }

    private void book(Draw d, LedgerData data, Facts facts, float o, float mx, float my) {
        // Shadow under the whole book, as wide as what is showing.
        int leftEdge = o < 0.5f ? SPINE_X : Math.round(SPINE_X - (SPINE_X) * ((o - 0.5f) * 2f));
        d.fill(leftEdge + 4, 5, BOOK_R + 5, H + 4, 0x60000000);
        boolean live = o >= 0.999f && turnFrom == null && leftAt < 0;

        if (o > 0.001f) {
            // The back cover and the right page, always under everything.
            d.fill(SPINE_X - 2, 0, BOOK_R, H, LEATHER);
            d.fill(BOOK_R - 2, 0, BOOK_R, H, LEATHER_EDGE);
            tabs(d, data, mx, my, live);
            paper(d, RIGHT, true);
            if (o > 0.5f) at(baseRight(), () -> rightPage(d, data, mx, my, live));
        }
        if (o >= 0.5f) {
            // The front cover has swung over: the left page grows out of the spine.
            float kk = (o - 0.5f) * 2f;
            int w = Math.round((PAGE_W + COVER) * kk);
            d.fill(SPINE_X - w, 0, SPINE_X + 2, H, LEATHER);
            d.fill(SPINE_X - w, 0, SPINE_X - w + 2, H, LEATHER_EDGE);
            if (kk > 0.98f) {
                paper(d, LEFT, false);
                spine(d);
                at(baseLeft(), () -> {
                    ribbon(d, mx, my, live);
                    leftPage(d, data, facts, mx, my, live);
                });
                corners(d, mx, my, live);
                sheet(d, data, facts);
            } else {
                int pw = Math.max(0, w - COVER);
                d.fill(SPINE_X - pw, COVER, SPINE_X, H - COVER, PAPER);
                d.fill(SPINE_X - pw, COVER, SPINE_X - pw + 2, H - COVER, PAPER_SHADE);
                spine(d);
            }
        } else {
            // The front cover, closed or lifting: hinged at the spine, narrowing as it turns towards the reader.
            float kk = 1f - o * 2f;
            int w = Math.round((BOOK_R - SPINE_X) * kk);
            cover(d, data, SPINE_X, w, mx, my, kk > 0.999f);
        }
    }

    // ------------------------------------------------------------------ turning the pages

    private static Spot here() {
        return new Spot(chapter, page);
    }

    /** What the left page shows: while a page turns forward, the old one until the sheet covers it. */
    private static Spot baseLeft() {
        return turnFrom == null ? here() : turnForward ? turnFrom : turnTo;
    }

    private static Spot baseRight() {
        return turnFrom == null ? here() : turnForward ? turnTo : turnFrom;
    }

    /** Runs {@code r} with the book turned to {@code s} (a stamp being read only stays open on its own spread). */
    private static void at(Spot s, Runnable r) {
        Chapter c0 = chapter;
        int p0 = page;
        String sel0 = selected;
        boolean same = s.chapter() == c0 && s.page() == p0;
        chapter = s.chapter();
        page = s.page();
        if (!same) selected = null;
        try {
            r.run();
        } finally {
            chapter = c0;
            page = p0;
            selected = sel0;
        }
    }

    private static int pages(Chapter c) {
        if (c == Chapter.SERAPH && Maintenance.SERAPH) return 1;
        return Math.max(1, (Ledger.chapter(c).size() + PER_PAGE - 1) / PER_PAGE);
    }

    /** The next page of stamps, or the next chapter's first; null at the back of the book. */
    private static Spot next(Spot s) {
        if (s.page() + 1 < pages(s.chapter())) return new Spot(s.chapter(), s.page() + 1);
        Chapter[] all = Chapter.values();
        int i = s.chapter().ordinal() + 1;
        return i < all.length ? new Spot(all[i], 0) : null;
    }

    private static Spot prev(Spot s) {
        if (s.page() > 0) return new Spot(s.chapter(), s.page() - 1);
        Chapter[] all = Chapter.values();
        int i = s.chapter().ordinal() - 1;
        return i >= 0 ? new Spot(all[i], pages(all[i]) - 1) : null;
    }

    /** Starts a page over towards {@code to}; held, it follows the mouse until let go. */
    private static void turn(Spot to, boolean forward, boolean held) {
        if (to == null) return;
        finishTurn(true);
        if (to.equals(here())) return;
        turnFrom = here();
        turnTo = to;
        turnForward = forward;
        turnHeld = held;
        turnMoved = false;
        turnP = 0f;
        turnTarget = held ? 0f : 1f;
    }

    /** Moves a page that is not held towards where it is going; one that gets there is turned (or back). */
    private static void turnTick(float dt) {
        if (turnFrom == null || turnHeld) return;
        turnP += Math.signum(turnTarget - turnP) * Math.min(Math.abs(turnTarget - turnP), dt / TURN_TIME);
        if (turnP == turnTarget) finishTurn(turnTarget >= 1f);
    }

    /** Ends a turn at once: over ({@code done}, as it was going) or back where it was. */
    private static void finishTurn(boolean done) {
        if (turnFrom == null) return;
        if (done && turnTarget >= 1f) {
            if (turnTo.chapter() != chapter) markSeen();
            chapter = turnTo.chapter();
            page = turnTo.page();
            selected = null;
        }
        turnFrom = turnTo = null;
        turnHeld = false;
    }

    /**
     * The sheet in the air: off the page it was, narrowing towards the spine as it stands up, then down over the other
     * side with the next page on its back - each face drawn squeezed sideways, which is what a page edge-on looks like.
     */
    private void sheet(Draw d, LedgerData data, Facts facts) {
        if (turnFrom == null) return;
        float p = clamp01(turnP);
        boolean firstHalf = p < 0.5f;
        float w = PAGE_W * Math.abs((float) Math.cos(Math.PI * p));
        boolean onRight = turnForward == firstHalf;
        Spot face = firstHalf ? turnFrom : turnTo;
        int anchor = onRight ? RIGHT : SPINE_X;
        if (w >= 0.5f) {
            d.push();
            d.translate(anchor, 0);
            d.scale(w / PAGE_W, 1f);
            d.translate(-anchor, 0);
            if (onRight) {
                paper(d, RIGHT, true);
                at(face, () -> rightPage(d, data, -1, -1, false));
            } else {
                paper(d, LEFT, false);
                at(face, () -> leftPage(d, data, facts, -1, -1, false));
            }
            // Darker the more it stands up off the page.
            int shade = Math.round(110 * (1f - w / PAGE_W));
            d.fill(onRight ? RIGHT : LEFT, COVER, onRight ? RIGHT + PAGE_W : SPINE_X, H - COVER, shade << 24);
            d.pop();
        }
        // Its free edge throws a shadow on the page underneath.
        int edge = Math.round(onRight ? RIGHT + w : SPINE_X - w);
        int sa = Math.round(80 * (float) Math.sin(Math.PI * p));
        for (int i = 0; i < 7; i++) {
            int a = sa * (7 - i) / 7;
            int x = onRight ? edge + i : edge - i - 1;
            if (onRight ? x >= RIGHT + PAGE_W : x < LEFT) break;
            d.fill(x, COVER, x + 1, H - COVER, a << 24);
        }
        if (w >= 1f) d.fill(onRight ? edge - 1 : edge, COVER, onRight ? edge : edge + 1, H - COVER, 0x50FFFFFF);
    }

    /** The folded corners that turn the page: bottom right goes on, bottom left goes back. */
    private void corners(Draw d, float mx, float my, boolean live) {
        Spot nx = next(here()), pv = prev(here());
        if (turnFrom != null) return;
        if (nx != null) corner(d, true, live && onCorner(mx, my, true), nx);
        if (pv != null) corner(d, false, live && onCorner(mx, my, false), pv);
    }

    private static boolean onCorner(float mx, float my, boolean right) {
        if (my < H - COVER - CORNER_HIT || my >= H) return false;
        return right ? mx >= RIGHT + PAGE_W - CORNER_HIT && mx < BOOK_R : mx >= 0 && mx < LEFT + CORNER_HIT;
    }

    private static void corner(Draw d, boolean right, boolean hover, Spot to) {
        int f = hover ? CORNER_HOVER : CORNER;
        int y1 = H - COVER;
        for (int j = 0; j < f; j++) {
            int y = y1 - f + j;
            if (right) {
                int x1 = RIGHT + PAGE_W;
                d.fill(x1 - j, y, x1, y + 1, LEATHER);                       // the corner folded away
                d.fill(x1 - f, y, x1 - j, y + 1, j == 0 ? PAPER : PAPER_SHADE); // the flap folded over
                d.fill(x1 - j - 1, y, x1 - j, y + 1, 0x40000000);              // its crease
            } else {
                int x0 = LEFT;
                d.fill(x0, y, x0 + j, y + 1, LEATHER);
                d.fill(x0 + j, y, x0 + f, y + 1, j == 0 ? PAPER : PAPER_SHADE);
                d.fill(x0 + j, y, x0 + j + 1, y + 1, 0x40000000);
            }
        }
        // Under the flap, a pencilled arrow - and where it goes, while the mouse is on it.
        String arrow = right ? ">" : "<";
        int ax = right ? RIGHT + PAGE_W - f - 8 : LEFT + f + 3, ay = y1 - 10;
        d.text(arrow, ax, ay, hover ? INK : PENCIL, false, false);
        if (hover) {
            String where = to.chapter() != chapter ? to.chapter().title : "Page " + (to.page() + 1);
            float s = 0.8f;
            int w = Math.round(d.width(where, false) * s);
            d.push();
            d.translate(right ? ax - 4 - w : ax + 10, ay + 1);
            d.scale(s);
            d.text(where, 0, 0, INK, false, false);
            d.pop();
        }
    }

    /** A page: cream, ruled like a ledger, a red margin, a shadow towards the spine. */
    private static void paper(Draw d, int x, boolean right) {
        d.fill(x, COVER, x + PAGE_W, H - COVER, PAPER);
        for (int y = COVER + 22; y < H - COVER - 6; y += 11) d.fill(x + 6, y, x + PAGE_W - 6, y + 1, RULE);
        int margin = right ? x + 10 : x + PAGE_W - 11;
        d.fill(margin, COVER + 4, margin + 1, H - COVER - 4, MARGIN);
        for (int i = 0; i < 6; i++) {
            int a = 0x30 - i * 7;
            int sx = right ? x + i : x + PAGE_W - 1 - i;
            d.fill(sx, COVER, sx + 1, H - COVER, a << 24);
        }
    }

    private static void spine(Draw d) {
        d.fill(SPINE_X, 0, RIGHT, H, LEATHER_DARK);
        d.fill(SPINE_X + 1, 0, SPINE_X + 2, H, 0x40000000);
        d.fill(RIGHT - 2, 0, RIGHT - 1, H, 0x40000000);
        for (int y = 14; y < H - 10; y += 34) d.fill(SPINE_X + 2, y, RIGHT - 2, y + 2, GOLD_DIM);
    }

    // ------------------------------------------------------------------ the cover

    private void cover(Draw d, LedgerData data, int x, int w, float mx, float my, boolean full) {
        if (w <= 0) return;
        boolean hover = full && mx >= x && mx < x + w && my >= 0 && my < H;
        d.fill(x, 0, x + w, H, hover ? 0xFF65331F : LEATHER);
        d.fill(x, 0, x + Math.min(w, 10), H, LEATHER_DARK);                // the spine side, rounded over
        d.fill(x + w - 2, 0, x + w, H, LEATHER_EDGE);
        d.fill(x, 0, x + w, 1, 0x30FFFFFF);
        d.fill(x, H - 1, x + w, H, LEATHER_EDGE);
        if (!full) return;
        // A tooled gold border.
        int sx = x + w - 20;
        int bx0 = x + 16, bx1 = sx - 4, by0 = 10, by1 = H - 10;
        frame(d, bx0, by0, bx1, by1, GOLD_DIM);
        frame(d, bx0 + 3, by0 + 3, bx1 - 3, by1 - 3, 0x80E3B456);
        int cx = (bx0 + bx1) / 2;
        // The mod's own mark, pressed in.
        d.modIcon(cx - 16, 34, 32);
        centered(d, "THE BUTCHER'S", cx, 76, GOLD, 1f, true);
        centered(d, "LEDGER", cx, 88, GOLD, 2.2f, true);
        d.fill(cx - 40, 112, cx + 40, 113, GOLD_DIM);
        int earned = Ledger.earnedGrades(data), all = Ledger.totalGrades();
        centered(d, earned + " / " + all + " stamps", cx, 120, 0xFFD9C7A0, 1f, false);
        int fresh = unseen(data, null);
        if (fresh > 0) centered(d, fresh + (fresh == 1 ? " new stamp" : " new stamps"), cx, 132, GOLD, 1f, false);
        // The strap and its buckle.
        d.fill(sx, 0, sx + 12, H, 0xFF472214);
        d.fill(sx, 0, sx + 1, H, 0x40000000);
        d.fill(sx - 2, H / 2 - 9, sx + 14, H / 2 + 9, GOLD_DIM);
        d.fill(sx, H / 2 - 7, sx + 12, H / 2 + 7, 0xFF472214);
        d.fill(sx + 5, H / 2 - 7, sx + 7, H / 2 + 7, GOLD);
        centered(d, hover ? "Click to open" : "Inspected and passed", cx, by1 - 22, hover ? GOLD : 0xFF9C8460, 1f, false);
    }

    // ------------------------------------------------------------------ the divider tabs

    private void tabs(Draw d, LedgerData data, float mx, float my, boolean live) {
        Chapter[] all = Chapter.values();
        for (int i = 0; i < all.length; i++) {
            Chapter c = all[i];
            int y = TAB_TOP + i * (TAB_H + TAB_GAP);
            boolean cur = c == chapter, hover = live && tabAt(mx, my) == c;
            int x0 = TAB_X, x1 = TAB_X + TAB_W + (cur ? 4 : hover ? 2 : 0);
            d.fill(x0 + 1, y + 1, x1 + 1, y + TAB_H + 1, 0x40000000);
            d.fill(x0, y, x1, y + TAB_H, cur ? c.colour : mix(c.colour, 0xFF2A2018, hover ? 0.15f : 0.35f));
            d.fill(x1 - 1, y, x1, y + TAB_H, 0x40000000);
            d.fill(x0, y, x1, y + 1, 0x50FFFFFF);
            if (c == Chapter.SERAPH && Maintenance.SERAPH) {
                for (int k = 0; k < TAB_H; k += 4) d.fill(x0 + 2, y + k, x1 - 2, y + k + 2, 0x90F2C21B);
            }
            d.push();
            d.translate(x0 + 8 + (cur ? 2 : 0), y + TAB_H / 2f);
            d.scale(0.62f);
            d.item(c.icon, -8, -8);
            d.pop();
            if (unseen(data, c) > 0) d.fill(x1 - 4, y + 2, x1 - 2, y + 4, RED_INK);
        }
    }

    private static Chapter tabAt(float mx, float my) {
        Chapter[] all = Chapter.values();
        if (mx < TAB_X + 4 || mx >= TAB_X + TAB_W + 4) return null;
        for (int i = 0; i < all.length; i++) {
            int y = TAB_TOP + i * (TAB_H + TAB_GAP);
            if (my >= y && my < y + TAB_H) return all[i];
        }
        return null;
    }

    // ------------------------------------------------------------------ the right page: the stamps

    private void rightPage(Draw d, LedgerData data, float mx, float my, boolean live) {
        int x = RIGHT;
        List<Stamp> stamps = Ledger.chapter(chapter);
        String head = chapter.title.toUpperCase(Locale.ROOT);
        int hw = d.width(head, true);
        int cx = x + PAGE_W / 2;
        d.text(head, cx - hw / 2, COVER + 7, chapter.ink, true, false);
        d.fill(x + 14, COVER + 11, cx - hw / 2 - 5, COVER + 12, mix(chapter.ink, PAPER, 0.5f));
        d.fill(cx + hw / 2 + 5, COVER + 11, x + PAGE_W - 8, COVER + 12, mix(chapter.ink, PAPER, 0.5f));

        if (chapter == Chapter.SERAPH && Maintenance.SERAPH) {
            StampArt.hazardTape(d, cx, H / 2f - 10, PAGE_W / 2 - 4, -0.12f, "UNDER MAINTENANCE");
            StampArt.hazardTape(d, cx, H / 2f + 22, PAGE_W / 2 - 4, 0.09f, "UNDER MAINTENANCE");
            return;
        }

        int pages = pages(chapter);
        page = Math.min(page, pages - 1);
        Stamp hover = live ? stampAt(mx, my) : null;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= stamps.size()) break;
            Stamp s = stamps.get(idx);
            int[] c = cell(i);
            int g = Ledger.grade(s, data);
            boolean hidden = s.hidden() && g == 0;
            if (s == hover || s.id().equals(selected)) {
                d.fill(c[0] - 19, c[1] - 18, c[0] + 19, c[1] + 40, s.id().equals(selected) ? 0x26604020 : 0x14604020);
            }
            if (g > 0) StampArt.earned(d, c[0], c[1], 15, s, g, 1f, StampArt.tilt(s));
            else StampArt.empty(d, c[0], c[1], 15, s, hidden, Math.round(mx), Math.round(my));
            String name = hidden ? "???" : s.name();
            small(d, name, c[0], c[1] + 19, 36, g > 0 ? INK : PENCIL);
            if (g > 0 && !data.seen.contains(s.id())) {
                d.push();
                d.translate(c[0] + 8, c[1] - 19);
                d.rotate(0.25f);
                d.scale(0.7f);
                d.text("NEW", 0, 0, RED_INK, true, false);
                d.pop();
            }
        }
        if (pages > 1) {
            String p = "page " + (page + 1) + " of " + pages;
            int pw = d.width(p, false);
            d.text(p, cx - pw / 2, H - COVER - 12, PENCIL, false, false);
        }
        int earned = 0;
        for (Stamp s : stamps) if (Ledger.grade(s, data) > 0) earned++;
        tally(d, earned, x + 16, H - COVER - 13);
    }

    /** Somebody counting their stamps in the margin, five-bar gates and all. */
    private static void tally(Draw d, int n, int x, int y) {
        int col = 0xB0000000 | (PENCIL & 0xFFFFFF);
        for (int i = 0; i < Math.min(n, 20); i++) {
            int group = i / 5, inGroup = i % 5, gx = x + group * 13;
            if (inGroup < 4) {
                d.fill(gx + inGroup * 2, y + (i % 2), gx + inGroup * 2 + 1, y + 8, col);
            } else {
                for (int j = 0; j < 9; j++) d.fill(gx - 1 + j, y + 6 - j * 6 / 8, gx + j, y + 7 - j * 6 / 8, col);
            }
        }
    }

    /** A cup was put down on some title pages: a faint ring in the empty lower half, the same every time. */
    private static void coffee(Draw d) {
        int seed = chapter.ordinal() * 7 + 2;
        if (seed % 3 == 0) return;
        int rcx = SPINE_X - 52 - seed % 13, rcy = H - COVER - 66 - seed % 7;
        StampArt.ring(d, rcx, rcy, 14, 2, 0x2E000000 | COFFEE);
        StampArt.ring(d, rcx + 1, rcy + 1, 13, 1, 0x16000000 | COFFEE);
        d.fill(rcx + 11, rcy + 12, rcx + 13, rcy + 14, 0x24000000 | COFFEE);
    }

    /** A grid cell's stamp centre: {x, y}. */
    private static int[] cell(int i) {
        int col = i % COLS, row = i / COLS;
        int cw = (PAGE_W - 22) / COLS;
        return new int[]{RIGHT + 14 + cw * col + cw / 2, COVER + 42 + row * 66};
    }

    private Stamp stampAt(float mx, float my) {
        List<Stamp> stamps = Ledger.chapter(chapter);
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= stamps.size()) break;
            int[] c = cell(i);
            if (mx >= c[0] - 19 && mx < c[0] + 19 && my >= c[1] - 18 && my < c[1] + 40) return stamps.get(idx);
        }
        return null;
    }

    // ------------------------------------------------------------------ the left page

    private void leftPage(Draw d, LedgerData data, Facts f, float mx, float my, boolean live) {
        Stamp s = selected == null ? null : Ledger.byId(selected);
        if (s != null && s.chapter() == chapter) stampPage(d, data, f, s, mx, my);
        else titlePage(d, data, f);
    }

    /** The red ribbon that keeps the place - and shuts the book. */
    private static void ribbon(Draw d, float mx, float my, boolean live) {
        int x = SPINE_X - 16;
        boolean hover = live && mx >= x - 2 && mx < x + 10 && my >= 0 && my < 40;
        int len = hover ? 40 : 34;
        d.fill(x + 1, 1, x + 9, len + 1, 0x40000000);
        d.fill(x, 0, x + 8, len, hover ? 0xFFC0282E : RIBBON);
        d.fill(x, len, x + 3, len + 3, hover ? 0xFFC0282E : RIBBON);
        d.fill(x + 5, len, x + 8, len + 3, hover ? 0xFFC0282E : RIBBON);
        if (hover) {
            d.push();
            d.translate(x - 4 - d.width("Put away", false) * 0.85f, 6);
            d.scale(0.85f);
            d.text("Put away", 0, 0, RED_INK, false, false);
            d.pop();
        }
    }

    private void titlePage(Draw d, LedgerData data, Facts f) {
        int x = LEFT + 14, right = SPINE_X - 22;
        coffee(d);
        d.push();
        d.translate(x, COVER + 12);
        d.scale(1.5f);
        d.item(chapter.icon, 0, 0);
        d.pop();
        d.push();
        d.translate(x + 30, COVER + 13);
        d.scale(1.3f);
        d.text(chapter.title, 0, 0, chapter.ink, true, false);
        d.pop();
        // Underlined twice in pencil, by someone pleased with the heading.
        squiggle(d, x + 30, COVER + 26, Math.round(d.width(chapter.title, true) * 1.3f), chapter.ordinal());
        d.text(chapter.subtitle, x + 30, COVER + 31, PENCIL, false, false);
        d.fill(x, COVER + 45, right, COVER + 46, mix(chapter.ink, PAPER, 0.4f));

        int y = COVER + 53;
        List<Stamp> stamps = Ledger.chapter(chapter);
        if (chapter == Chapter.SERAPH && Maintenance.SERAPH) {
            y = wrap(d, "Being sharpened. Its stamps come back with it.", x, y, right - x, PENCIL);
            note(d, x, right);
            return;
        }
        int earned = 0, grades = 0, all = 0;
        for (Stamp s : stamps) {
            int g = Ledger.grade(s, data);
            if (g > 0) earned++;
            grades += g;
            all += s.grades();
        }
        y = line(d, "Stamps", earned + " / " + stamps.size(), x, right, y);
        y = line(d, "Grades", grades + " / " + all, x, right, y);
        y += 4;
        for (String[] l : stats(data, f)) y = line(d, l[0], l[1], x, right, y);
        closest(d, data, f, stamps, x, right, y + 8);
        note(d, x, right);
    }

    // ------------------------------------------------------------------ the pencil

    /** The chapter's note at the foot of its title page, in pencil and a little crooked, with a doodle beside it. */
    private static void note(Draw d, int x, int right) {
        String quip = switch (chapter) {
            case HOUSE -> "the house always wins";
            case SCATHA -> "worms. so many worms.";
            case VANGUARD -> "bring a warmer coat";
            case DIANA -> "dig. dig. dig.";
            case HELLION -> "pack fire resistance!!";
            case ERUPTION -> "do NOT swim here";
            case FIN -> "need a bigger boat";
            case WISP -> "boo.";
            default -> "back soon";
        };
        d.push();
        d.translate(x + 2, H - COVER - 30);
        d.rotate(-0.06f);
        d.text(quip, 0, 0, 0xC0000000 | (PENCIL & 0xFFFFFF), false, false);
        d.fill(0, 10, Math.min(right - x - 34, d.width(quip, false)), 11, 0x50000000 | (PENCIL & 0xFFFFFF));
        d.pop();
        String[] art = DOODLES[Math.min(DOODLES.length - 1, chapter.ordinal())];
        int w = 0;
        for (String r : art) w = Math.max(w, r.length());
        int dx = right - w * 2 - 2, dy = H - COVER - 22 - art.length * 2;
        for (int r = 0; r < art.length; r++) {
            for (int c = 0; c < art[r].length(); c++) {
                if (art[r].charAt(c) == '#') {
                    d.fill(dx + c * 2, dy + r * 2, dx + c * 2 + 2, dy + r * 2 + 2, 0x90000000 | (PENCIL & 0xFFFFFF));
                }
            }
        }
    }

    /** A wavy pencil underline, never quite the same twice. */
    private static void squiggle(Draw d, int x, int y, int w, int seed) {
        int col = 0x90000000 | (PENCIL & 0xFFFFFF);
        for (int i = 0; i < w; i++) {
            int dy = (int) Math.round(Math.sin(i * 0.55 + seed) * 1.2);
            d.fill(x + i, y + dy, x + i + 1, y + dy + 1, col);
            if (i > w / 3) d.fill(x + i + 2, y + 3 + dy / 2, x + i + 3, y + 4 + dy / 2, 0x50000000 | (PENCIL & 0xFFFFFF));
        }
    }

    /** One doodle per chapter, in Chapter order: dice, a worm, a snowflake, the moon, a flame, a volcano, a fin, a ghost,
     *  an eye. */
    private static final String[][] DOODLES = {
            {"#########", "#.......#", "#.#...#.#", "#.......#", "#...#...#", "#.......#", "#.#...#.#", "#.......#", "#########"},
            {"...........##", "..##......#.#", ".#..#....#...", "#....#..#....", "......##....."},
            {"....#....", ".#..#..#.", "..#.#.#..", "...###...", "#########", "...###...", "..#.#.#..", ".#..#..#.", "....#...."},
            {"...###...", ".##......", ".#.......", "#........", "#........", "#........", ".#.......", ".##......", "...###..."},
            {"....#....", "...##....", "...#.#...", "..#..#...", "..#...#.#", ".#.#..#.#", ".#.##.#.#", ".#......#", "..######."},
            {"..#.#.#....", "...#.#.....", "...#.#.....", "..#...#....", ".#.....#...", "#.......#..", "###########"},
            {"....#......", "...##......", "..#.#......", ".#..#......", "#...#......", "#####......", ".#.#.#.#.#.", "#.#.#.#.#.#"},
            {"..#####..", ".#.....#.", "#..#.#..#", "#.......#", "#.......#", "#.#.#.#.#", ".#.#.#.#."},
            {"...#####...", ".##.....##.", "#...###...#", "#..#####..#", "#...###...#", ".##.....##.", "...#####..."},
    };

    /** The counted stamp nearest its next grade, with its bar: something to go for. */
    private static void closest(Draw d, LedgerData data, Facts f, List<Stamp> stamps, int x, int right, int y) {
        Stamp best = null;
        float bestK = -1;
        long bestV = 0, bestNeed = 0;
        for (Stamp s : stamps) {
            if (isEvent(s) || (s.hidden() && Ledger.grade(s, data) == 0)) continue;
            int g = Ledger.grade(s, data);
            if (g >= s.grades()) continue;
            long need = s.need(g + 1), v = Ledger.value(s, f, data);
            if (need <= 1) continue;
            float k = v / (float) need;
            if (k > bestK) {
                bestK = k;
                best = s;
                bestV = v;
                bestNeed = need;
            }
        }
        if (best == null) return;
        int g = Ledger.grade(best, data);
        String label = "Closest: " + best.name() + (best.tiered() ? " (" + Grade.of(g + 1).title + ")" : "");
        if (d.width(label, false) > right - x) label = "Closest: " + best.name();
        while (d.width(label, false) > right - x && label.length() > 12) label = label.substring(0, label.length() - 2) + ".";
        d.text(label, x, y, PENCIL, false, false);
        bar(d, bestV, bestNeed, x, right, y + 11, best.chapter().ink);
    }

    /** The numbers under each chapter's title. */
    private List<String[]> stats(LedgerData data, Facts f) {
        List<String[]> out = new ArrayList<>();
        switch (chapter) {
            case HOUSE -> {
                out.add(row("Cutscenes", data.cutscenes));
                out.add(row("Worst run of busts", data.worstBustRun));
                out.add(row("Coins", data.coins));
                out.add(row("Skipped", data.skips));
            }
            case SCATHA -> {
                out.add(row("Kills", f.scathaKills));
                out.add(row("Pets", f.pets()));
                out.add(row("Worst dry run", f.scathaWorstDry));
            }
            case VANGUARD -> {
                out.add(row("Corpses", f.corpses));
                out.add(row("Legendaries", f.corpseLegendaries));
                out.add(row("Worst dry run", f.corpseWorstDry));
            }
            case DIANA -> {
                long drops = 0;
                for (int n : f.dianaDrops.values()) drops += n;
                out.add(row("Kills", f.dianaKills));
                out.add(row("Drops", drops));
            }
            case HELLION -> source(out, f, "blaze", "Bosses");
            case ERUPTION -> source(out, f, "lava", "Kills");
            case FIN -> source(out, f, "shark", "Sharks");
            case WISP -> out.add(row("Drops", f.drops("ghost")));
            default -> { }
        }
        return out;
    }

    private static void source(List<String[]> out, Facts f, String key, String kills) {
        out.add(row(kills, f.kills(key)));
        out.add(row("Drops", f.drops(key)));
        out.add(row("Worst dry run", f.worstDry(key)));
    }

    private static String[] row(String label, long n) {
        return new String[]{label, String.format(Locale.ROOT, "%,d", n)};
    }

    /** One stamp's own page: the stamp large, its name, its grade, what it is for and how far along it is. */
    private void stampPage(Draw d, LedgerData data, Facts f, Stamp s, float mx, float my) {
        int x = LEFT + 14, right = SPINE_X - 22, cx = LEFT + (PAGE_W - 12) / 2;
        boolean backHover = mx >= x - 2 && mx < x + 40 && my >= COVER + 6 && my < COVER + 18;
        d.text("< Back", x, COVER + 8, backHover ? INK : PENCIL, false, false);

        int g = Ledger.grade(s, data);
        boolean hidden = s.hidden() && g == 0;
        if (g > 0) StampArt.earned(d, cx, COVER + 48, 24, s, g, 1f, StampArt.tilt(s));
        else StampArt.empty(d, cx, COVER + 48, 24, s, hidden, Math.round(mx), Math.round(my));

        int y = COVER + 78;
        centered(d, hidden ? "???" : s.name(), cx, y, INK, 1.2f, true);
        y += 15;
        String gradeLine;
        int gradeCol;
        if (g == 0) {
            gradeLine = "Not stamped yet";
            gradeCol = PENCIL;
        } else if (s.tiered()) {
            Grade gr = Grade.of(g);
            gradeLine = gr.title + " - " + g + " of " + s.grades();
            gradeCol = darker(gr.ring);
        } else {
            gradeLine = "Stamped";
            gradeCol = s.chapter().ink;
        }
        centered(d, gradeLine, cx, y, gradeCol, 1f, false);
        y += 14;

        if (hidden) {
            wrap(d, "A hidden stamp. It shows what it is for once you have earned it.", x, y, right - x, PENCIL);
            return;
        }
        y = wrap(d, describe(s), x, y, right - x, INK) + 3;
        long v = Ledger.value(s, f, data);
        if (s.tiered()) {
            for (int i = 1; i <= s.grades(); i++) {
                Grade gr = Grade.of(i);
                boolean got = g >= i;
                String n = String.format(Locale.ROOT, "%,d", s.need(i));
                d.fill(x, y + 2, x + 4, y + 6, got ? gr.ring : 0x40000000 | (PENCIL & 0xFFFFFF));
                d.text(gr.title, x + 8, y, got ? INK : PENCIL, false, false);
                d.text(n, right - d.width(n, false), y, got ? INK : PENCIL, false, false);
                y += 10;
            }
            if (g < s.grades()) y = bar(d, v, s.need(g + 1), x, right, y + 2, s.chapter().ink);
        } else if (s.tiers().length > 0 && g == 0 && !isEvent(s)) {
            y = bar(d, v, s.need(1), x, right, y + 2, s.chapter().ink);
        }
        LedgerData.Entry e = data.stamps.get(s.id());
        if (e != null) {
            String when = e.at == 0 ? "Pre-ledger - earned before the Ledger"
                    : "Stamped " + DATE.format(Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault()));
            wrap(d, when, x, H - COVER - 38, right - x, PENCIL);
            if (isEvent(s) && e.value > 0) {
                d.text("On kill #" + String.format(Locale.ROOT, "%,d", e.value), x, H - COVER - 18, PENCIL, false, false);
            }
        }
    }

    /** What a stamp is for, with its number where the sentence needs one. */
    private static String describe(Stamp s) {
        String desc = s.desc();
        if (!s.tiered() && s.tiers().length > 0 && !isEvent(s)) {
            return desc + ": " + String.format(Locale.ROOT, "%,d", s.need(1)) + ".";
        }
        if (s.id().equals("scatha_beginners_luck")) return "A pet within your first " + s.need(1) + " kills.";
        if (s.id().equals("scatha_back_to_back")) return "Two pets within " + s.need(1) + " kills.";
        if (s.id().equals("vg_in_the_black")) return "A corpse worth " + String.format(Locale.ROOT, "%,d", s.need(1)) + " coins or more.";
        return desc.endsWith(".") ? desc : desc + ".";
    }

    /** The stamps with a number that is not a count to fill. */
    private static boolean isEvent(Stamp s) {
        return switch (s.id()) {
            case "house_hot_streak", "house_night_shift", "house_jackpot_777", "scatha_back_to_back", "scatha_beginners_luck",
                 "vg_negative_equity", "vg_in_the_black", "diana_shared_custody", "lava_jaw_dropping", "fin_bigger_fish" -> true;
            default -> false;
        };
    }

    private static int bar(Draw d, long v, long need, int x, int right, int y, int ink) {
        float k = need <= 0 ? 1f : Math.min(1f, v / (float) need);
        d.fill(x, y, right, y + 5, 0x30000000 | (PENCIL & 0xFFFFFF));
        d.fill(x, y, x + Math.round((right - x) * k), y + 5, ink);
        String t = String.format(Locale.ROOT, "%,d / %,d", Math.min(v, need), need);
        d.push();
        d.translate(right - d.width(t, false) * 0.8f, y + 7);
        d.scale(0.8f);
        d.text(t, 0, 0, PENCIL, false, false);
        d.pop();
        return y + 17;
    }

    // ------------------------------------------------------------------ the mouse

    /** @return whether the click was the book's */
    boolean click(double mx, double my) {
        if (leftAt >= 0) return true;
        float[] b = toBook(mx, my);
        float bx = b[0], by = b[1];
        if (!open) {
            if (bx >= SPINE_X && bx < BOOK_R && by >= 0 && by < H) {
                open = true;
                shutByRibbon = false;
                return true;
            }
            return false;
        }
        if (openness < 0.99f || turnFrom != null) return true;
        // The ribbon shuts it - and puts it away.
        int rx = SPINE_X - 16;
        if (bx >= rx - 2 && bx < rx + 10 && by >= 0 && by < 40) {
            markSeen();
            open = false;
            shutByRibbon = true;
            selected = null;
            return true;
        }
        // A folded corner: picked up, it follows the mouse; let go where it was, it turns by itself.
        for (boolean right : new boolean[]{true, false}) {
            if (!onCorner(bx, by, right)) continue;
            Spot to = right ? next(here()) : prev(here());
            if (to == null) continue;
            turn(to, right, true);
            pressX = mx;
            pressY = my;
            return true;
        }
        Chapter t = tabAt(bx, by);
        if (t != null) {
            if (t != chapter) turn(new Spot(t, 0), t.ordinal() > chapter.ordinal(), false);
            else if (page != 0) turn(new Spot(t, 0), false, false);
            return true;
        }
        if (selected != null && bx >= LEFT + 12 && bx < LEFT + 56 && by >= COVER + 6 && by < COVER + 18) {
            selected = null;
            return true;
        }
        Stamp s = stampAt(bx, by);
        if (s != null) {
            selected = s.id();
            if (Ledger.grade(s, ButcherStats.get().ledger()) > 0) ButcherStats.get().seen(List.of(s.id()));
            return true;
        }
        return bx >= 0 && bx < W && by >= 0 && by < H;
    }

    /** A held page follows the mouse: how far across the book it is is how far over the page is. */
    boolean drag(double mx, double my) {
        if (turnFrom == null || !turnHeld) return false;
        if (Math.abs(mx - pressX) + Math.abs(my - pressY) > 3) turnMoved = true;
        float bx = toBook(mx, my)[0];
        float span = RIGHT + PAGE_W - LEFT;
        float p = turnForward ? (RIGHT + PAGE_W - bx) / span : (bx - LEFT) / span;
        turnP = Math.max(0f, Math.min(0.98f, p));
        return true;
    }

    /** Let go: a click on the corner turns the page, a drag turns it if it got far enough, else it falls back. */
    boolean release() {
        if (turnFrom == null || !turnHeld) return false;
        turnHeld = false;
        turnTarget = !turnMoved || turnP > TURN_COMMIT ? 1f : 0f;
        return true;
    }

    /** The wheel turns the pages too: down for on, up for back. */
    boolean scroll(double amount) {
        if (!open || openness < 0.99f || turnFrom != null || leftAt >= 0) return true;
        if (amount < 0) turn(next(here()), true, false);
        else if (amount > 0) turn(prev(here()), false, false);
        return true;
    }

    /** The chapter on show has been looked at: its fresh ink dries. */
    private static void markSeen() {
        LedgerData data = ButcherStats.get().ledger();
        List<String> ids = new ArrayList<>();
        for (Stamp s : Ledger.chapter(chapter)) if (Ledger.grade(s, data) > 0) ids.add(s.id());
        if (!ids.isEmpty()) ButcherStats.get().seen(ids);
    }

    /** Earned stamps not looked at yet, in one chapter or (null) all of them. */
    private static int unseen(LedgerData data, Chapter c) {
        int n = 0;
        for (Stamp s : Ledger.STAMPS) {
            if (c != null && s.chapter() != c) continue;
            if (Ledger.grade(s, data) > 0 && !data.seen.contains(s.id())) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ text

    private static int line(Draw d, String label, String value, int x, int right, int y) {
        d.text(label, x, y, INK, false, false);
        d.text(value, right - d.width(value, false), y, INK, false, false);
        return y + 11;
    }

    private static void centered(Draw d, String s, int cx, int y, int col, float k, boolean bold) {
        d.push();
        d.translate(cx - d.width(s, bold) * k / 2f, y);
        d.scale(k);
        d.text(s, 0, 0, col, bold, false);
        d.pop();
    }

    /** A stamp's name under it: small, centred, three lines at most. */
    private static void small(Draw d, String s, int cx, int y, int maxW, int col) {
        float k = 0.75f;
        List<String> lines = lines(d, s, Math.round(maxW / k));
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            String l = lines.get(i);
            d.push();
            d.translate(cx - d.width(l, false) * k / 2f, y + i * 7.5f);
            d.scale(k);
            d.text(l, 0, 0, col, false, false);
            d.pop();
        }
    }

    private static int wrap(Draw d, String s, int x, int y, int w, int col) {
        for (String l : lines(d, s, w)) {
            d.text(l, x, y, col, false, false);
            y += 10;
        }
        return y;
    }

    private static List<String> lines(Draw d, String s, int w) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = cur.length() == 0 ? word : cur + " " + word;
            if (d.width(next, false) > w && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(next);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private static void frame(Draw d, int x0, int y0, int x1, int y1, int col) {
        d.fill(x0, y0, x1, y0 + 1, col);
        d.fill(x0, y1 - 1, x1, y1, col);
        d.fill(x0, y0, x0 + 1, y1, col);
        d.fill(x1 - 1, y0, x1, y1, col);
    }

    private static float clamp01(float t) {
        return Math.max(0f, Math.min(1f, t));
    }

    /** Overshoots a touch and settles: the book landing. */
    private static float backOut(float t) {
        t = clamp01(t) - 1f;
        return 1f + t * t * (2.2f * t + 1.2f);
    }

    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3 - 2 * t);
    }

    private static int mix(int a, int b, float t) {
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return 0xFF000000 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
    }

    private static int darker(int c) {
        return mix(c, 0xFF000000, 0.25f);
    }
}
