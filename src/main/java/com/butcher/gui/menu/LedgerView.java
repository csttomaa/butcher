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
 * The Ledger tab: a leather-bound butcher's ledger of stamps, one chapter per animation.
 *
 * Shut, it is a cover with the title in gold and how many stamps are in it. Opened (the cover swings over to the
 * left), it is a spread: the left page is the chapter's title page - or, once a stamp is clicked, that stamp's own
 * page - and the right page the chapter's stamps, four to a row. Coloured divider tabs stand out of the right edge, one
 * per chapter, and a red ribbon on the left page shuts the book again. Pages are ruled like a ledger, with a red margin.
 *
 * Laid out at a fixed size ({@link #W} x {@link #H}) and scaled to the space the menu gives it, so it looks the same
 * at every GUI scale; the mouse is turned back into book coordinates the same way. No Minecraft in here, like the rest
 * of the menu, so it renders offline.
 */
final class LedgerView {

    /** The book's own size, before it is scaled to fit. */
    static final int W = 360, H = 228;
    private static final int COVER = 5, PAGE_W = 166, SPINE = 10;
    private static final int LEFT = COVER, SPINE_X = COVER + PAGE_W, RIGHT = SPINE_X + SPINE, BOOK_R = RIGHT + PAGE_W + COVER;
    private static final int TAB_X = BOOK_R - 6, TAB_W = 15, TAB_H = 21, TAB_GAP = 2, TAB_TOP = 10;
    private static final int COLS = 4, ROWS = 3, PER_PAGE = COLS * ROWS;

    private static final int LEATHER = 0xFF5A2D1B, LEATHER_DARK = 0xFF3A1C10, LEATHER_EDGE = 0xFF2A140B;
    private static final int GOLD = 0xFFE3B456, GOLD_DIM = 0xFF9C7430;
    private static final int PAPER = StampArt.PAPER, PAPER_SHADE = 0xFFDCCFAF, RULE = 0x2A6B5432, MARGIN = 0x55B0302A;
    private static final int INK = 0xFF3A2A1A, PENCIL = StampArt.PENCIL, RED_INK = 0xFFB0302A, RIBBON = 0xFF9E1E24;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    // The book is where it was left, like the tab and the open cards.
    private static boolean open;
    private static Chapter chapter = Chapter.HOUSE;
    private static String selected;
    private static int page;
    /** 0 = shut, 1 = open; eased towards {@link #open}. */
    private static float openness;
    private static long flipAt = -1;

    private long lastFrame = System.nanoTime();
    // The transform of the last frame, for the mouse.
    private float ox, oy, scale = 1f;

    // ------------------------------------------------------------------ drawing

    void draw(Draw d, int ax, int ay, int aw, int ah, int mx, int my) {
        long nowNs = System.nanoTime();
        float dt = Math.min(0.1f, (nowNs - lastFrame) / 1e9f);
        lastFrame = nowNs;
        float target = open ? 1f : 0f;
        openness += Math.signum(target - openness) * Math.min(Math.abs(target - openness), dt / 0.42f);

        scale = Math.min(aw / (float) W, ah / (float) H);
        scale = Math.min(scale, 1.6f);
        ox = ax + (aw - W * scale) / 2f;
        oy = ay + (ah - H * scale) / 2f;
        float bx = (mx - ox) / scale, by = (my - oy) / scale;

        ButcherStats st = ButcherStats.get();
        LedgerData data = st.ledger();
        Facts facts = st.facts();

        float o = ease(openness);
        // Shut, the cover sits in the middle; open, the spread does.
        float sx = closedShift() * (1f - o);
        d.push();
        d.translate(ox, oy);
        d.scale(scale);
        d.translate(sx, 0);
        float px = bx - sx;
        book(d, data, facts, o, px, by);
        d.pop();
    }

    /** How far the shut book moves to sit in the middle (only the front cover shows). */
    private static float closedShift() {
        return W / 2f - (SPINE_X + BOOK_R) / 2f;
    }

    private void book(Draw d, LedgerData data, Facts facts, float o, float mx, float my) {
        // Shadow under the whole book, as wide as what is showing.
        int leftEdge = o < 0.5f ? SPINE_X : Math.round(SPINE_X - (SPINE_X) * ((o - 0.5f) * 2f));
        d.fill(leftEdge + 3, 4, BOOK_R + 4, H + 3, 0x50000000);

        if (o > 0.001f) {
            // The back cover and the right page, always under everything.
            d.fill(SPINE_X - 2, 0, BOOK_R, H, LEATHER);
            d.fill(BOOK_R - 2, 0, BOOK_R, H, LEATHER_EDGE);
            tabs(d, data, mx, my, o >= 0.999f);
            paper(d, RIGHT, true);
            if (o > 0.5f) rightPage(d, data, mx, my, o >= 0.999f);
        }
        if (o >= 0.5f) {
            // The front cover has swung over: the left page grows out of the spine.
            float k = (o - 0.5f) * 2f;
            int w = Math.round((PAGE_W + COVER) * k);
            d.fill(SPINE_X - w, 0, SPINE_X + 2, H, LEATHER);
            d.fill(SPINE_X - w, 0, SPINE_X - w + 2, H, LEATHER_EDGE);
            if (k > 0.98f) {
                paper(d, LEFT, false);
                spine(d);
                leftPage(d, data, facts, mx, my);
            } else {
                int pw = Math.max(0, w - COVER);
                d.fill(SPINE_X - pw, COVER, SPINE_X, H - COVER, PAPER);
                d.fill(SPINE_X - pw, COVER, SPINE_X - pw + 2, H - COVER, PAPER_SHADE);
                spine(d);
            }
        } else {
            // The front cover, closed or lifting: hinged at the spine, narrowing as it turns towards the reader.
            float k = 1f - o * 2f;
            int w = Math.round((BOOK_R - SPINE_X) * k);
            cover(d, data, SPINE_X, w, mx, my, k > 0.999f);
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
            flip(d);
            return;
        }

        int pages = Math.max(1, (stamps.size() + PER_PAGE - 1) / PER_PAGE);
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
                d.fill(c[0] - 18, c[1] - 17, c[0] + 18, c[1] + 33, s.id().equals(selected) ? 0x26604020 : 0x14604020);
            }
            if (g > 0) StampArt.earned(d, c[0], c[1], 14, s, g, 1f, StampArt.tilt(s));
            else StampArt.empty(d, c[0], c[1], 14, s, hidden, Math.round(mx), Math.round(my));
            String name = hidden ? "???" : s.name();
            small(d, name, c[0], c[1] + 18, 34, g > 0 ? INK : PENCIL);
            if (g > 0 && !data.seen.contains(s.id())) {
                d.push();
                d.translate(c[0] + 8, c[1] - 17);
                d.rotate(0.25f);
                d.scale(0.55f);
                d.text("NEW", 0, 0, RED_INK, true, false);
                d.pop();
            }
        }
        if (pages > 1) {
            String p = "< " + (page + 1) + " / " + pages + " >";
            int pw = d.width(p, false);
            d.text(p, cx - pw / 2, H - COVER - 14, PENCIL, false, false);
        }
        int earned = 0;
        for (Stamp s : stamps) if (Ledger.grade(s, data) > 0) earned++;
        String count = earned + " / " + stamps.size();
        d.push();
        d.translate(x + PAGE_W - 10 - d.width(count, false) * 0.7f, H - COVER - 11);
        d.scale(0.7f);
        d.text(count, 0, 0, PENCIL, false, false);
        d.pop();
        flip(d);
    }

    /** A page turning over the right page as the chapter changes: paper narrowing towards the spine. */
    private static void flip(Draw d) {
        if (flipAt < 0) return;
        float f = (System.currentTimeMillis() - flipAt) / 260f;
        if (f >= 1f) {
            flipAt = -1;
            return;
        }
        int w = Math.round(PAGE_W * (1f - ease(f)));
        if (w <= 0) return;
        d.fill(RIGHT, COVER, RIGHT + w, H - COVER, PAPER);
        d.fill(RIGHT + w - 2, COVER, RIGHT + w, H - COVER, PAPER_SHADE);
        d.fill(RIGHT + w, COVER, RIGHT + w + 4, H - COVER, 0x30000000);
    }

    /** A grid cell's stamp centre: {x, y}. */
    private static int[] cell(int i) {
        int col = i % COLS, row = i / COLS;
        int cw = (PAGE_W - 22) / COLS;
        return new int[]{RIGHT + 14 + cw * col + cw / 2, COVER + 38 + row * 62};
    }

    private Stamp stampAt(float mx, float my) {
        List<Stamp> stamps = Ledger.chapter(chapter);
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= stamps.size()) break;
            int[] c = cell(i);
            if (mx >= c[0] - 18 && mx < c[0] + 18 && my >= c[1] - 17 && my < c[1] + 33) return stamps.get(idx);
        }
        return null;
    }

    // ------------------------------------------------------------------ the left page

    private void leftPage(Draw d, LedgerData data, Facts f, float mx, float my) {
        ribbon(d, mx, my);
        Stamp s = selected == null ? null : Ledger.byId(selected);
        if (s != null && s.chapter() == chapter) stampPage(d, data, f, s, mx, my);
        else titlePage(d, data, f);
    }

    /** The red ribbon that keeps the place - and shuts the book. */
    private static void ribbon(Draw d, float mx, float my) {
        int x = SPINE_X - 16;
        boolean hover = mx >= x - 2 && mx < x + 10 && my >= 0 && my < 40;
        int len = hover ? 40 : 34;
        d.fill(x + 1, 1, x + 9, len + 1, 0x40000000);
        d.fill(x, 0, x + 8, len, hover ? 0xFFC0282E : RIBBON);
        d.fill(x, len, x + 3, len + 3, hover ? 0xFFC0282E : RIBBON);
        d.fill(x + 5, len, x + 8, len + 3, hover ? 0xFFC0282E : RIBBON);
        if (hover) {
            d.push();
            d.translate(x - 4 - d.width("Close", false) * 0.7f, 6);
            d.scale(0.7f);
            d.text("Close", 0, 0, RED_INK, false, false);
            d.pop();
        }
    }

    private void titlePage(Draw d, LedgerData data, Facts f) {
        int x = LEFT + 14, right = SPINE_X - 22;
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
        d.text(chapter.subtitle, x + 30, COVER + 27, PENCIL, false, false);
        d.fill(x, COVER + 42, right, COVER + 43, mix(chapter.ink, PAPER, 0.4f));

        int y = COVER + 50;
        List<Stamp> stamps = Ledger.chapter(chapter);
        if (chapter == Chapter.SERAPH && Maintenance.SERAPH) {
            y = wrap(d, "Being sharpened. Its stamps come back with it.", x, y, right - x, PENCIL);
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
        String hint = "Click a stamp to read it.";
        d.text(hint, LEFT + (PAGE_W - d.width(hint, false)) / 2, H - COVER - 16, PENCIL, false, false);
    }

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
            wrap(d, when, x, H - COVER - 26, right - x, PENCIL);
            if (isEvent(s) && e.value > 0) {
                d.text("On kill #" + String.format(Locale.ROOT, "%,d", e.value), x, H - COVER - 14, PENCIL, false, false);
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
        d.translate(right - d.width(t, false) * 0.7f, y + 7);
        d.scale(0.7f);
        d.text(t, 0, 0, PENCIL, false, false);
        d.pop();
        return y + 16;
    }

    // ------------------------------------------------------------------ the mouse

    /** @return whether the click was the book's */
    boolean click(double mx, double my) {
        float o = ease(openness);
        float bx = (float) ((mx - ox) / scale) - closedShift() * (1f - o), by = (float) ((my - oy) / scale);
        if (!open) {
            if (bx >= SPINE_X && bx < BOOK_R && by >= 0 && by < H) {
                open = true;
                return true;
            }
            return false;
        }
        if (openness < 0.99f) return true;
        // The ribbon shuts it.
        int rx = SPINE_X - 16;
        if (bx >= rx - 2 && bx < rx + 10 && by >= 0 && by < 40) {
            markSeen();
            open = false;
            selected = null;
            return true;
        }
        Chapter t = tabAt(bx, by);
        if (t != null) {
            if (t != chapter) {
                markSeen();
                chapter = t;
                selected = null;
                page = 0;
                flipAt = System.currentTimeMillis();
            }
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
        // The page counter: left half back, right half on.
        List<Stamp> stamps = Ledger.chapter(chapter);
        int pages = Math.max(1, (stamps.size() + PER_PAGE - 1) / PER_PAGE);
        if (pages > 1 && by >= H - COVER - 18 && bx >= RIGHT && bx < RIGHT + PAGE_W) {
            page = Math.floorMod(page + (bx < RIGHT + PAGE_W / 2f ? -1 : 1), pages);
            flipAt = System.currentTimeMillis();
            return true;
        }
        return bx >= 0 && bx < W && by >= 0 && by < H;
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
        float k = 0.62f;
        List<String> lines = lines(d, s, Math.round(maxW / k));
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            String l = lines.get(i);
            d.push();
            d.translate(cx - d.width(l, false) * k / 2f, y + i * 6.5f);
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
