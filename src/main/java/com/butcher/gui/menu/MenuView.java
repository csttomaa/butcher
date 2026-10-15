package com.butcher.gui.menu;

import com.butcher.config.ButcherStats;
import com.butcher.config.PatternConfig;
import com.butcher.config.PatternConfig.AnimationMode;
import com.butcher.detect.vanguard.VanguardLoot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * The "/butcher" menu: everything it shows, how it lays out and how it answers the mouse - with no Minecraft in it.
 *
 * {@code gui/ButcherMenuScreen} is a thin Screen that hands this a {@link Draw} over the game's GUI and forwards the
 * mouse; a preview tool can hand it a Java2D one and render the real menu offline. The row list is rebuilt from the
 * config every frame, so what is drawn and what is clickable can never drift apart; sliders and switches carry a stable
 * id so a drag or an animation survives the rebuild.
 *
 * <h2>The look</h2>
 *
 * A dark panel with a gold trim over a dimmed game with slow embers rising; the title with the mod's icon; tabs with
 * pixel icons and a gold underline that slides between them. Each animation is a card in its own colour with a real
 * item for an icon and its on/off switch on the card itself, so a source can be switched off without opening it; an
 * open card's rows hang off a bar in that colour. Switches are pills whose knob slides, sliders a track with a knob,
 * choices "&lt; value &gt;", actions real buttons. The list scrolls smoothly and fades at its edges.
 */
public final class MenuView {

    /** What the menu needs from the game. */
    public interface Host {
        PatternConfig config();

        /** Closes the menu and plays an outcome as "Try it" does. */
        void playTest(String group, String name);

        /** Whether "Try it" can play: cutscenes draw over the game, so not from the title screen (Mod Menu). */
        default boolean canTry() { return true; }

        void resetSettings();

        /** A Diana verdict's drop name ("Minos Relic"), or "" for none. */
        String dianaName(String verdict);

        String version();
    }

    private final Host host;

    public MenuView(Host host) {
        this.host = host;
    }

    // ------------------------------------------------------------------ layout

    private static final int PANEL_W = 360, PANEL_H_MAX = 340;
    private static final int TITLE_H = 40, TAB_H = 26, FOOTER_H = 16, PAD = 10;
    private static final int GAP = 3;
    private static final int SCROLLBAR_W = 3, SCROLLBAR_GRAB = 6;

    private static final String[] TAB_NAMES = {"Animations", "Sound", "General", "Stats"};
    private static final int TAB_ANIM = 0, TAB_SOUND = 1, TAB_GENERAL = 2, TAB_STATS = 3;

    // ------------------------------------------------------------------ palette

    static final int GOLD = 0xFFE8B04A, GOLD_BRIGHT = 0xFFFFD98A, GOLD_DIM = 0xFF7A5A22;
    static final int TEXT = 0xFFECEFF2, DIM = 0xFF9AA3AD, FAINT = 0xFF646C76;
    static final int PANEL_TOP = 0xF41B1F26, PANEL_BOTTOM = 0xF40A0C10;
    static final int ROW = 0x14FFFFFF, ROW_HOVER = 0x28FFFFFF;
    static final int TRACK = 0xFF2A3038, SWITCH_OFF = 0xFF3A414A;
    static final int RED = 0xFFE0503A;

    /** Each animation's own colour: its card, its bar, its switches and sliders. */
    static final int SCATHA = 0xFFB27BE6, VANGUARD = 0xFF72C6F2, DIANA = 0xFFE4573F, FIN = 0xFF3FB0D0,
            WISP = 0xFFA9E4EE, LAVA = 0xFFFF8C2E, SLAYERS = 0xFFD04848, BLAZE = 0xFFFFA238, SERAPH = 0xFFA36BF5;

    // ------------------------------------------------------------------ state (kept while the game runs)

    /** The tab and the open cards survive closing the menu: it opens where it was left. */
    private static int tab = TAB_ANIM;
    private static final Set<String> openGroups = new HashSet<>();
    /** What each group's "Try it" will play. */
    private static final Map<String, Integer> TRY = new HashMap<>();

    private float scroll, scrollTarget;
    private int contentHeight;
    private String draggingId;
    private boolean draggingBar;
    private final List<Placed> placed = new ArrayList<>();
    private int width = 1, height = 1;

    private final long openedAt = System.currentTimeMillis();
    private long lastFrame = System.nanoTime();
    private float dt;
    /** Eased values by id: switch knobs, the tab underline. */
    private final Map<String, Float> anim = new HashMap<>();
    private float underline = -1f;

    /** Which reset is waiting for its second click, and since when. */
    private String resetArmed;
    private long resetArmedAt;
    private static final long RESET_ARM_MS = 4000;

    // ------------------------------------------------------------------ geometry

    private float openT() {
        return ease(clamp01((System.currentTimeMillis() - openedAt) / 220f));
    }

    private int panelW() { return Math.max(220, Math.min(PANEL_W, width - 16)); }
    private int maxPanelH() { return Math.max(140, Math.min(PANEL_H_MAX, height - TITLE_H - 12)); }

    /** The panel is as tall as its content (up to the screen), easing as cards open and shut. */
    private float shownH = -1f;

    private int panelH() { return Math.max(150, Math.min(maxPanelH(), Math.round(shownH < 0 ? maxPanelH() : shownH))); }

    /** Everything in the panel that is not the list. */
    private static final int CHROME = TAB_H + 6 + FOOTER_H + 4 + 6;
    private int panelX() { return (width - panelW()) / 2; }

    private int panelY() {
        int top = (height - TITLE_H - maxPanelH()) / 2;
        return top + TITLE_H + Math.round((1f - openT()) * 10f);
    }

    private int viewTop() { return panelY() + TAB_H + 6; }
    private int viewBottom() { return panelY() + panelH() - FOOTER_H - 4; }
    private int viewH() { return viewBottom() - viewTop(); }
    private boolean scrollable() { return contentHeight > viewH(); }
    private int rowsLeft() { return panelX() + PAD; }
    private int rowsWidth() { return panelW() - PAD * 2 - (scrollable() ? SCROLLBAR_W + 5 : 0); }
    private int barX() { return panelX() + panelW() - PAD + 1; }

    private float clampScroll(float v) {
        return Math.max(0f, Math.min(Math.max(0, contentHeight - viewH()), v));
    }

    // ------------------------------------------------------------------ drawing

    /** Draws a frame. {@code width} and {@code height} are the GUI's size. */
    public void render(Draw d, int width, int height, int mouseX, int mouseY) {
        this.width = width;
        this.height = height;
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float open = openT();

        layout(d);
        float wanted = Math.max(150, Math.min(maxPanelH(), contentHeight + CHROME));
        shownH = shownH < 0 ? wanted : shownH + (wanted - shownH) * Math.min(1f, dt * 14f);
        if (Math.abs(shownH - wanted) < 0.5f) shownH = wanted;

        backdrop(d, open);
        int px = panelX(), py = panelY(), pw = panelW(), ph = panelH();
        title(d, px, py, pw);
        panel(d, px, py, pw, ph);
        tabs(d, px, py, pw, mouseX, mouseY);

        scrollTarget = clampScroll(scrollTarget);
        scroll += (scrollTarget - scroll) * Math.min(1f, dt * 16f);
        if (Math.abs(scrollTarget - scroll) < 0.3f) scroll = scrollTarget;
        scroll = clampScroll(scroll);

        int vt = viewTop(), vb = viewBottom();
        d.clip(px + 1, vt, px + pw - 1, vb);
        int left = rowsLeft(), w = rowsWidth();
        for (Placed p : placed) {
            int ry = vt + p.y - Math.round(scroll);
            int h = p.row.height();
            if (ry + h + GAP < vt || ry > vb) continue;
            int x = left + p.row.indent, rw = w - p.row.indent;
            if (p.row.grouped) {
                // The open card's bar, unbroken from row to row.
                d.fill(x + 1, ry - GAP, x + 3, ry + h, (p.row.accent & 0xFFFFFF) | 0x90000000);
                x += 9;
                rw -= 9;
            }
            p.row.draw(d, x, ry, rw, mouseX, mouseY);
        }
        d.endClip();
        edgeFades(d, px, pw, vt, vb);
        scrollbar(d, mouseX, mouseY);
        footer(d, px, py, pw, ph);

        // The opening fade: everything drawn at full strength, then veiled (styled text keeps its own colour).
        if (open < 1f) d.fill(px - 2, py - TITLE_H, px + pw + 2, py + ph + 2, Math.round(255 * (1f - open)) << 24);
    }

    private void backdrop(Draw d, float open) {
        d.fill(0, 0, width, height, Math.round(0xB8 * open) << 24);
        // A vignette: nested frames, each a little darker towards the edges.
        int step = Math.max(2, Math.min(width, height) / 28);
        for (int i = 0; i < 8; i++) {
            int a = Math.round((8 - i) * 9 * open);
            int in = i * step;
            int c = a << 24;
            d.fill(in, in, width - in, in + step, c);
            d.fill(in, height - in - step, width - in, height - in, c);
            d.fill(in, in + step, in + step, height - in - step, c);
            d.fill(width - in - step, in + step, width - in, height - in - step, c);
        }
        // Embers, rising slowly round the panel.
        double t = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < 42; i++) {
            double speed = 9 + rnd(i, 3) * 16;
            double y = height + 10 - ((t * speed + rnd(i, 7) * (height + 20)) % (height + 20));
            double x = rnd(i, 11) * width + Math.sin(t * (0.4 + rnd(i, 13)) + i) * 6;
            float life = (float) (1 - y / height);
            float flicker = 0.55f + 0.45f * (float) Math.sin(t * (2 + rnd(i, 17) * 3) + i * 1.3);
            int a = Math.round(200 * open * flicker * Math.max(0f, Math.min(1f, 1.3f - life)));
            if (a < 8) continue;
            int s = rnd(i, 19) > 0.7 ? 2 : 1;
            int col = rnd(i, 23) > 0.5 ? 0xFFB040 : 0xFF7A28;
            d.fill((int) x, (int) y, (int) x + s, (int) y + s, a << 24 | col);
        }
    }

    private void title(Draw d, int px, int py, int pw) {
        String name = "BUTCHER";
        int tw = d.width(name, true) * 2;
        int icon = 24, gap = 6;
        int total = icon + gap + tw;
        int x = px + (pw - total) / 2, y = py - TITLE_H + 2;
        d.modIcon(x, y, icon);
        d.push();
        d.translate(x + icon + gap, y + 5);
        d.scale(2f);
        d.text(name, 1, 1, 0xFF3A2408, true, false);
        d.text(name, 0, 0, GOLD, true, false);
        d.pop();
        String sub = "Rare-drop cutscenes for SkyBlock";
        d.text(sub, px + (pw - d.width(sub, false)) / 2, y + icon + 4, DIM, false, true);
    }

    private void panel(Draw d, int px, int py, int pw, int ph) {
        // Shadow, frame, a vertical gradient, then the gold trim with a glint running along the top.
        d.fill(px - 1 + 3, py + 3, px + pw + 3, py + ph + 3, 0x50000000);
        d.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF000000);
        int bands = 16;
        for (int i = 0; i < bands; i++) {
            int y0 = py + ph * i / bands, y1 = py + ph * (i + 1) / bands;
            d.fill(px, y0, px + pw, y1, lerpColor(PANEL_TOP, PANEL_BOTTOM, i / (float) (bands - 1)));
        }
        int trim = 0x55E8B04A;
        d.fill(px, py, px + pw, py + 1, GOLD_DIM);
        d.fill(px, py + ph - 1, px + pw, py + ph, trim);
        d.fill(px, py, px + 1, py + ph, trim);
        d.fill(px + pw - 1, py, px + pw, py + ph, trim);
        float cycle = (System.currentTimeMillis() % 3600) / 3600f;
        int gx = px + Math.round((pw + 120) * cycle) - 60;
        for (int k = 0; k < 60; k++) {
            int xx = gx + k;
            if (xx < px || xx >= px + pw) continue;
            float f = 1f - Math.abs(k - 30) / 30f;
            d.fill(xx, py, xx + 1, py + 1, Math.round(255 * f) << 24 | (GOLD_BRIGHT & 0xFFFFFF));
        }
    }

    /** 7 x 7 pixel icons for the tabs: play, speaker, gear, bars. */
    private static final String[][] TAB_ICONS = {
            {"#......", "###....", "#####..", "#######", "#####..", "###....", "#......"},
            {"...#...", "..##.#.", "####..#", "####..#", "####..#", "..##.#.", "...#..."},
            {"..#.#..", ".#####.", "##...##", ".#...#.", "##...##", ".#####.", "..#.#.."},
            {"......#", "......#", "....#.#", "....#.#", "..#.#.#", "..#.#.#", "#.#.#.#"},
    };

    private void tabs(Draw d, int px, int py, int pw, int mx, int my) {
        int tabW = pw / TAB_NAMES.length;
        float target = tab * tabW;
        underline = underline < 0 ? target : underline + (target - underline) * Math.min(1f, dt * 18f);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int x0 = px + i * tabW, x1 = i == TAB_NAMES.length - 1 ? px + pw : x0 + tabW;
            boolean active = i == tab, hover = !active && within(mx, my, x0, py, x1, py + TAB_H);
            if (hover) d.fill(x0, py + 1, x1, py + TAB_H, 0x14FFFFFF);
            int col = active ? TEXT : hover ? 0xFFC8CED4 : DIM;
            String label = TAB_NAMES[i];
            int lw = d.width(label, false);
            int cx = (x0 + x1) / 2, total = 7 + 5 + lw;
            int ix = cx - total / 2, iy = py + TAB_H / 2 - 4;
            glyph(d, TAB_ICONS[i], ix, iy, active ? GOLD : col);
            d.text(label, ix + 12, iy, col, false, active);
        }
        int ux = px + Math.round(underline);
        d.fill(ux + 12, py + TAB_H - 2, ux + tabW - 12, py + TAB_H, GOLD);
        d.fill(px + 1, py + TAB_H, px + pw - 1, py + TAB_H + 1, 0x30FFFFFF);
    }

    private static void glyph(Draw d, String[] rows, int x, int y, int argb) {
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                if (rows[r].charAt(c) == '#') d.fill(x + c, y + r, x + c + 1, y + r + 1, argb);
            }
        }
    }

    /** The list's top and bottom fade into the panel when there is more beyond them. */
    private void edgeFades(Draw d, int px, int pw, int vt, int vb) {
        int n = 10;
        for (int i = 0; i < n; i++) {
            int a = Math.round(230 * (1f - i / (float) n));
            if (scroll > 0.5f) {
                int c = bgAt(vt + i);
                d.fill(px + 1, vt + i, px + pw - 1, vt + i + 1, (a << 24) | (c & 0xFFFFFF));
            }
            if (scroll < contentHeight - viewH() - 0.5f) {
                int c = bgAt(vb - 1 - i);
                d.fill(px + 1, vb - 1 - i, px + pw - 1, vb - i, (a << 24) | (c & 0xFFFFFF));
            }
        }
    }

    private int bgAt(int y) {
        return lerpColor(PANEL_TOP, PANEL_BOTTOM, clamp01((y - panelY()) / (float) panelH()));
    }

    private void scrollbar(Draw d, int mx, int my) {
        if (!scrollable()) return;
        int x = barX(), vt = viewTop(), vh = viewH();
        boolean over = mx >= x - SCROLLBAR_GRAB && mx <= x + SCROLLBAR_W + SCROLLBAR_GRAB && my >= vt && my <= vt + vh;
        d.fill(x, vt, x + SCROLLBAR_W, vt + vh, 0x1CFFFFFF);
        int thumbH = Math.max(18, Math.round(vh * (vh / (float) contentHeight)));
        int thumbY = vt + Math.round((vh - thumbH) * (scroll / Math.max(1f, contentHeight - vh)));
        d.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, draggingBar || over ? GOLD_BRIGHT : 0xC0E8B04A);
    }

    private void footer(Draw d, int px, int py, int pw, int ph) {
        int y = py + ph - FOOTER_H;
        d.fill(px + 1, y, px + pw - 1, y + 1, 0x24FFFFFF);
        String version = "v" + host.version();
        int vw = d.width(version, false);
        d.text(version, px + pw - PAD - vw, y + 5, FAINT, false, false);
        String hint = "Click to change · Right-click back · Esc to close";
        if (d.width(hint, false) > pw - PAD * 3 - vw) hint = "Right-click goes back";
        d.text(hint, px + PAD, y + 5, FAINT, false, false);
    }

    // ------------------------------------------------------------------ rows

    private record Placed(Row row, int y) { }

    /** What the group being built is: its colour, whether its rows hang off its bar, how far in. */
    private int curAccent = GOLD;
    private boolean curGrouped;
    private int curIndent;
    private String curGroup = "";
    /** Measures text while the rows are built (notices wrap to the width). */
    private Draw measure;

    private abstract class Row {
        final int accent = curAccent;
        final boolean grouped = curGrouped;
        final int indent = curIndent;

        abstract int height();

        abstract void draw(Draw d, int x, int y, int w, int mx, int my);

        boolean click(double mx, double my, int x, int y, int w) { return false; }

        boolean rightClick(double mx, double my, int x, int y, int w) { return false; }

        String dragId() { return null; }

        void dragTo(double mx, int x, int w) { }
    }

    private void layout(Draw d) {
        measure = d;
        placed.clear();
        int y = 0;
        for (Row r : buildRows()) {
            placed.add(new Placed(r, y));
            y += r.height() + GAP;
        }
        contentHeight = Math.max(0, y - GAP);
    }

    /** Starts a card: its header now, its rows (if open) after - in its colour, on its bar. */
    private boolean group(List<Row> rows, String id, String title, String sub, String icon, int accent, Boolean on, Runnable toggle) {
        curAccent = accent;
        curGrouped = false;
        curGroup = id;
        rows.add(new HeaderRow(id, title, sub, icon, on, toggle));
        curGrouped = isOpen(id);
        return curGrouped;
    }

    private void loose() {
        curAccent = GOLD;
        curGrouped = false;
        curIndent = 0;
        curGroup = "";
    }

    private boolean isOpen(String id) { return openGroups.contains(id); }

    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        PatternConfig cfg = host.config();
        loose();
        switch (tab) {
            case TAB_SOUND -> soundRows(rows, cfg);
            case TAB_GENERAL -> generalRows(rows, cfg);
            case TAB_STATS -> statsRows(rows);
            default -> {
                // One card after another, each building only itself: chained, a closed card's early return hid the
                // next (Wisp vanished whenever Fin was shut).
                scathaRows(rows, cfg);
                vanguardRows(rows, cfg);
                dianaRows(rows, cfg);
                fishingRows(rows, cfg);
                ghostRows(rows, cfg);
                lavaRows(rows, cfg);
                slayerRows(rows, cfg);
            }
        }
        loose();
        return rows;
    }

    // ------------------------------------------------------------------ the Animations tab

    private void scathaRows(List<Row> rows, PatternConfig cfg) {
        boolean on = cfg.animationMode != AnimationMode.NONE;
        if (!group(rows, "gambling", "Scatha - The Cleaver", "Every Scatha kill: a pet, gemstones or Dwarven O's", "amethyst_shard", SCATHA, on, () -> {
            if (cfg.animationMode == AnimationMode.NONE) {
                cfg.animationMode = cfg.lastStyle == AnimationMode.NONE ? AnimationMode.SCOPE : cfg.lastStyle;
            } else {
                cfg.lastStyle = cfg.animationMode;
                cfg.animationMode = AnimationMode.NONE;
            }
            cfg.save();
        })) return;
        boolean cleaver = cfg.animationMode != AnimationMode.GUILLOTINE;
        // The guillotine style is deliberately not offered: it has had none of the cleaver's work. It still exists
        // (AnimationMode.GUILLOTINE) for a config file that asks for it.
        rows.add(new SectionRow("Pacing"));
        if (cleaver) {
            rows.add(new SliderRow("scope", "Full run", cfg.scopeDurationMs, 3000, 16000, 100,
                    v -> cfg.scopeDurationMs = v, ms -> String.format("%.1fs", ms / 1000f)));
            rows.add(new NoticeRow("A full five-cut run. Shorter runs finish sooner."));
        } else {
            rows.add(new SliderRow("guill", "Blade fall", cfg.animationDurationMs, 800, 5000, 50,
                    v -> cfg.animationDurationMs = v, ms -> String.format("%.2fs", ms / 1000f)));
        }
        rows.add(new SectionRow("Hiding the drop"));
        rows.add(new SliderRow("hp", "Hide below", cfg.hideBelowHp, 1, 10, 1, v -> cfg.hideBelowHp = v, hp -> hp + " ❤"));
        rows.add(new NoticeRow("Covers your hotbar and chat before the kill, so nothing spoils the drop."));
        rows.add(new SectionRow("Pet payoff"));
        rows.add(toggle("Firework", cfg.petDropFirework, () -> cfg.petDropFirework = !cfg.petDropFirework, cfg));
        rows.add(toggle("Pet head popup", cfg.petHeadPopup, () -> cfg.petHeadPopup = !cfg.petHeadPopup, cfg));
        rows.add(new NoticeRow("Both fire as the drawn head vanishes."));
        if (cleaver) {
            rows.add(new SectionRow("Getting away"));
            rows.add(new SliderRow("escape", "Miss chance", cfg.escapeChance, 0, 100, 5, v -> cfg.escapeChance = v, v -> v + "%"));
            rows.add(new NoticeRow("How often it ducks the last swing. Never on a pet."));
            rows.add(new SectionRow("The look"));
            rows.add(new SliderRow("vig", "Vignette", cfg.vignetteStrength, 0, 100, 5, v -> cfg.vignetteStrength = v, v -> v + "%"));
            rows.add(new SliderRow("zoom", "Zoom per cut", cfg.zoomStrength, 0, 200, 10, v -> cfg.zoomStrength = v, v -> v + "%"));
            rows.add(new SliderRow("shake", "Screen shake", cfg.shakeStrength, 0, 200, 10, v -> cfg.shakeStrength = v, v -> v + "%"));
            rows.add(new NoticeRow("These build as a run goes on. Set shake to 0 if it bothers you."));
        }
        tryRows(rows, "scatha", List.of("RANDOM", "GEMSTONE", "DWARVEN_OS", "PET_RARE", "PET_EPIC", "PET_LEGENDARY"),
                MenuView::scathaOutcome);
    }

    private void vanguardRows(List<Row> rows, PatternConfig cfg) {
        if (!group(rows, "vanguard", "Vanguard Corpses - The Thaw", "Every Vanguard corpse you open, then its profit", "blue_ice", VANGUARD, cfg.vanguardAnimation,
                () -> { cfg.vanguardAnimation = !cfg.vanguardAnimation; cfg.save(); })) return;
        rows.add(new SectionRow("The cutscene"));
        rows.add(toggle("Hide the loot message", cfg.vanguardHideChat, () -> cfg.vanguardHideChat = !cfg.vanguardHideChat, cfg));
        rows.add(new NoticeRow("The haul goes to chat when the cutscene ends."));
        rows.add(new SectionRow("Pacing"));
        rows.add(new SliderRow("vg_speed", "Speed", cfg.vanguardSpeed, 50, 200, 10, v -> cfg.vanguardSpeed = v, v -> v + "%"));
        rows.add(toggle("Quick Thaw", cfg.vanguardQuick, () -> cfg.vanguardQuick = !cfg.vanguardQuick, cfg));
        rows.add(new NoticeRow("A corpse with nothing above common skips to its last strike. Anything better plays in full - so a Thaw that keeps going is a good one."));
        rows.add(toggle("Flare before each strike", cfg.vanguardFlare, () -> cfg.vanguardFlare = !cfg.vanguardFlare, cfg));
        rows.add(new NoticeRow("Each flare is the true colour of its drop, and the last one climbs. Off makes every ending a surprise."));
        rows.add(new SectionRow("Look and sound"));
        rows.add(new SliderRow("vg_name", "Item name size", cfg.vanguardNameSize, 60, 160, 10, v -> cfg.vanguardNameSize = v, v -> v + "%"));
        rows.add(new SliderRow("vg_finale", "Ending volume", cfg.vanguardFinaleVolume, 0, 150, 5, v -> cfg.vanguardFinaleVolume = v, v -> v + "%"));
        rows.add(new NoticeRow("The legendary, Locket and Dye endings."));
        rows.add(toggle("Profit window after the Thaw", cfg.vanguardProfit, () -> cfg.vanguardProfit = !cfg.vanguardProfit, cfg));
        rows.add(new NoticeRow("Every drop priced, minus the Skeleton Key. Needs Live prices (General tab)."));
        tryRows(rows, "vanguard", List.of("RANDOM", "COMMON", "UNCOMMON", "LEGENDARY", "LOCKET", "DYE"), MenuView::vanguardOutcome);
    }

    private void dianaRows(List<Row> rows, PatternConfig cfg) {
        if (!group(rows, "diana", "Diana - The Chopping Block", "Sphinx, Inquisitor, Manticore, King Minos, Relic, Dye", "golden_shovel", DIANA, cfg.dianaAnimation,
                () -> { cfg.dianaAnimation = !cfg.dianaAnimation; cfg.save(); })) return;
        rows.add(new SectionRow("Hiding the drop"));
        rows.add(new SliderRow("diana_hide", "Hide the HUD below", cfg.dianaHidePct, 0, 20, 1,
                v -> cfg.dianaHidePct = v, v -> v == 0 ? "Off" : v + "% HP"));
        rows.add(new NoticeRow("The HUD and chat fade out when your creature (or one you're getting loot share on) gets this low, so nothing gives the drop away."));
        rows.add(new SectionRow("What plays it"));
        rows.add(toggle("Sphinx kills", cfg.dianaSphinx, () -> cfg.dianaSphinx = !cfg.dianaSphinx, cfg));
        rows.add(toggle("Minos Inquisitor kills", cfg.dianaInquisitor, () -> cfg.dianaInquisitor = !cfg.dianaInquisitor, cfg));
        rows.add(toggle("Manticore kills", cfg.dianaManticore, () -> cfg.dianaManticore = !cfg.dianaManticore, cfg));
        rows.add(toggle("King Minos kills", cfg.dianaKingMinos, () -> cfg.dianaKingMinos = !cfg.dianaKingMinos, cfg));
        rows.add(toggle("Minos Relic (from a Champion)", cfg.dianaChampion, () -> cfg.dianaChampion = !cfg.dianaChampion, cfg));
        rows.add(toggle("Mythological Dye (from anything)", cfg.dianaDye, () -> cfg.dianaDye = !cfg.dianaDye, cfg));
        rows.add(new SectionRow("The endings"));
        rows.add(toggle("Every drop's own ending", cfg.dianaFullEndings, () -> cfg.dianaFullEndings = !cfg.dianaFullEndings, cfg));
        rows.add(new NoticeRow("Off gives every drop the same short ending: it rises out of the split."));
        rows.add(toggle("RARE DROP! banner", cfg.dianaBanner, () -> cfg.dianaBanner = !cfg.dianaBanner, cfg));
        rows.add(toggle("Show the price", cfg.dianaPrice, () -> cfg.dianaPrice = !cfg.dianaPrice, cfg));
        rows.add(new SliderRow("diana_size", "Size", cfg.dianaSize, 60, 140, 10, v -> cfg.dianaSize = v, v -> v + "%"));
        rows.add(new SectionRow("Sound"));
        rows.add(toggle("Music on the big drops", cfg.dianaMusic, () -> cfg.dianaMusic = !cfg.dianaMusic, cfg));
        rows.add(new SliderRow("diana_volume", "Volume", cfg.dianaVolume, 0, 150, 5, v -> cfg.dianaVolume = v, v -> v + "%"));
        bustsRow(rows, cfg.dianaBusts, v -> cfg.dianaBusts = v, cfg);
        tryRows(rows, "diana", names(com.butcher.anim.ChopTimeline.Verdict.values()), host::dianaName);
    }

    private void fishingRows(List<Row> rows, PatternConfig cfg) {
        if (!group(rows, "fishing", "Fishing Festival - Fin", "A Megalodon pet or a Great White Shark Tooth", "tropical_fish", FIN, cfg.fishingAnnounce,
                () -> { cfg.fishingAnnounce = !cfg.fishingAnnounce; cfg.save(); })) return;
        rows.add(new SectionRow("The pop-up"));
        rows.add(new NoticeRow("A Megalodon pet or a Great White Shark Tooth. A quick pop-up over the game - nothing is hidden."));
        rows.add(toggle("The bust - your shark dies with nothing", cfg.fishingBust, () -> cfg.fishingBust = !cfg.fishingBust, cfg));
        rows.add(toggle("Show the price", cfg.fishingPrice, () -> cfg.fishingPrice = !cfg.fishingPrice, cfg));
        rows.add(new NoticeRow("The pets show what George pays for them."));
        rows.add(new SliderRow("fishing_volume", "Volume", cfg.fishingVolume, 0, 150, 5, v -> cfg.fishingVolume = v, v -> v + "%"));
        List<String> fin = new ArrayList<>(names(com.butcher.detect.SharkDrops.Drop.values()));
        fin.add(0, "BUST");
        tryRows(rows, "shark", fin, n -> switch (n) {
            case "MEGALODON_EPIC" -> "Epic Megalodon";
            case "MEGALODON_LEGENDARY" -> "Legendary Megalodon";
            default -> com.butcher.detect.SharkDrops.Drop.valueOf(n).title;
        });
    }

    private void ghostRows(List<Row> rows, PatternConfig cfg) {
        if (!group(rows, "ghost", "Dwarven Mines Ghosts - Wisp", "A Ghost's Sorrow or Plasma", "ghast_tear", WISP, cfg.ghostAnnounce,
                () -> { cfg.ghostAnnounce = !cfg.ghostAnnounce; cfg.save(); })) return;
        rows.add(new SectionRow("The pop-up"));
        rows.add(new NoticeRow("A Ghost rises out of the Mist, comes apart, and its Sorrow or Plasma pops out. A quick pop-up over the game - nothing is hidden."));
        rows.add(toggle("Show the price", cfg.ghostPrice, () -> cfg.ghostPrice = !cfg.ghostPrice, cfg));
        rows.add(new SliderRow("ghost_volume", "Volume", cfg.ghostVolume, 0, 150, 5, v -> cfg.ghostVolume = v, v -> v + "%"));
        tryRows(rows, "ghost", names(com.butcher.detect.GhostDrops.Drop.values()),
                n -> com.butcher.detect.GhostDrops.Drop.valueOf(n).title);
    }

    private void lavaRows(List<Row> rows, PatternConfig cfg) {
        if (!group(rows, "lava", "Lava Fishing - Eruption", "Jawbus, Thunder, Ragnarok, Scuttler, the Carmine Dye", "magma_cream", LAVA, cfg.lavaAnimation,
                () -> { cfg.lavaAnimation = !cfg.lavaAnimation; cfg.save(); })) return;
        rows.add(new SectionRow("What plays it"));
        rows.add(new NoticeRow("Every kill of your own creature, the moment it dies - the heat it lands on is the drop. Plhlegblast only plays for the Carmine Dye."));
        rows.add(toggle("Lord Jawbus", cfg.lavaJawbus, () -> cfg.lavaJawbus = !cfg.lavaJawbus, cfg));
        rows.add(toggle("Thunder", cfg.lavaThunder, () -> cfg.lavaThunder = !cfg.lavaThunder, cfg));
        rows.add(toggle("Ragnarok", cfg.lavaRagnarok, () -> cfg.lavaRagnarok = !cfg.lavaRagnarok, cfg));
        rows.add(toggle("Fiery Scuttler", cfg.lavaScuttler, () -> cfg.lavaScuttler = !cfg.lavaScuttler, cfg));
        rows.add(toggle("Loot-shared kills", cfg.lavaLootShare, () -> cfg.lavaLootShare = !cfg.lavaLootShare, cfg));
        rows.add(new SectionRow("Hiding the drop"));
        rows.add(new SliderRow("lava_hide", "Hide the HUD below", cfg.lavaHidePct, 0, 20, 1,
                v -> cfg.lavaHidePct = v, v -> v == 0 ? "Off" : v + "% HP"));
        rows.add(new NoticeRow("The HUD and chat fade out when your creature gets this low, so nothing gives the drop away."));
        rows.add(new SectionRow("The endings"));
        rows.add(toggle("RARE DROP! banner", cfg.lavaBanner, () -> cfg.lavaBanner = !cfg.lavaBanner, cfg));
        rows.add(toggle("Show the price", cfg.lavaPrice, () -> cfg.lavaPrice = !cfg.lavaPrice, cfg));
        rows.add(new SliderRow("lava_volume", "Volume", cfg.lavaVolume, 0, 150, 5, v -> cfg.lavaVolume = v, v -> v + "%"));
        bustsRow(rows, cfg.lavaBusts, v -> cfg.lavaBusts = v, cfg);
        tryRows(rows, "lava", names(com.butcher.anim.EruptionTimeline.Drop.values()),
                n -> com.butcher.anim.EruptionTimeline.Drop.valueOf(n).title);
    }

    /** The slayers: one card, each boss its own card inside it. */
    private void slayerRows(List<Row> rows, PatternConfig cfg) {
        if (!group(rows, "slayers", "Slayers", com.butcher.Maintenance.SERAPH ? "Hellion (the Seraph's Glyph is on its way)" : "Hellion and the Seraph's Glyph", "diamond_sword", SLAYERS, null, null)) return;
        curIndent = 0;
        int outer = curIndent;
        curIndent = outer + 9;
        if (group(rows, "blaze", "Inferno Demonlord - Hellion", "Every kill of your own tier IV Demonlord", "blaze_rod", BLAZE, cfg.blazeAnimation,
                () -> { cfg.blazeAnimation = !cfg.blazeAnimation; cfg.save(); })) {
            rows.add(new NoticeRow("Plays on every kill of your own tier IV Demonlord, the moment it dies; the drop decides the ending."));
            rows.add(new SectionRow("Hiding the drop"));
            rows.add(new SliderRow("blaze_hide", "Hide the HUD below", cfg.blazeHidePct, 0, 30, 1,
                    v -> cfg.blazeHidePct = v, v -> v == 0 ? "Off" : v + "% HP"));
            rows.add(new NoticeRow("The HUD and chat fade out when your Demonlord gets this low, so nothing gives the drop away."));
            rows.add(new SectionRow("The endings"));
            rows.add(toggle("Drop banner (RARE DROP! and up)", cfg.blazeBanner, () -> cfg.blazeBanner = !cfg.blazeBanner, cfg));
            rows.add(toggle("Show the price", cfg.blazePrice, () -> cfg.blazePrice = !cfg.blazePrice, cfg));
            rows.add(new SliderRow("blaze_volume", "Volume", cfg.blazeVolume, 0, 150, 5, v -> cfg.blazeVolume = v, v -> v + "%"));
            bustsRow(rows, cfg.blazeBusts, v -> cfg.blazeBusts = v, cfg);
            tryRows(rows, "blaze", names(com.butcher.anim.HellionTimeline.Drop.values()),
                    n -> com.butcher.anim.HellionTimeline.Drop.valueOf(n).title);
        }
        if (com.butcher.Maintenance.SERAPH) {
            // Being reworked: the card is taped shut and none of it runs (ButcherClient.seraphOn).
            curAccent = SERAPH;
            curGrouped = false;
            curGroup = "seraph";
            rows.add(new TapedRow("Voidgloom Seraph - The Seraph's Glyph", "Back soon - still being sharpened", "ender_pearl"));
        } else if (group(rows, "seraph", "Voidgloom Seraph - The Seraph's Glyph", "Every kill of your own tier IV Seraph", "ender_pearl", SERAPH, cfg.seraphAnimation,
                () -> { cfg.seraphAnimation = !cfg.seraphAnimation; cfg.save(); })) {
            rows.add(new NoticeRow("Plays on every kill of your own tier IV Seraph (the one spawned by you), the moment it dies; the drop decides the ending."));
            rows.add(new SectionRow("Hiding the drop"));
            rows.add(new SliderRow("seraph_hide", "Hide the HUD below", cfg.seraphHidePct, 0, 30, 1,
                    v -> cfg.seraphHidePct = v, v -> v == 0 ? "Off" : v + "% HP"));
            rows.add(new NoticeRow("The HUD and chat fade out when your Seraph gets this low, so nothing gives the drop away."));
            rows.add(new SectionRow("The endings"));
            rows.add(toggle("Drop banner (RARE DROP! and up)", cfg.seraphBanner, () -> cfg.seraphBanner = !cfg.seraphBanner, cfg));
            rows.add(toggle("Show the price", cfg.seraphPrice, () -> cfg.seraphPrice = !cfg.seraphPrice, cfg));
            rows.add(new SliderRow("seraph_volume", "Volume", cfg.seraphVolume, 0, 150, 5, v -> cfg.seraphVolume = v, v -> v + "%"));
            bustsRow(rows, cfg.seraphBusts, v -> cfg.seraphBusts = v, cfg);
            tryRows(rows, "seraph", names(com.butcher.anim.SeraphTimeline.Drop.values()),
                    n -> com.butcher.anim.SeraphTimeline.Drop.valueOf(n).title);
        }
        curIndent = outer;
    }

    /** "Busts": the whole cutscene, a short one (straight to the bust's ending), or none (wins only). */
    private void bustsRow(List<Row> rows, int value, IntConsumer set, PatternConfig cfg) {
        String[] styles = {"Whole cutscene", "Short", "None - wins only"};
        int v = Math.max(0, Math.min(2, value));
        rows.add(new ValueRow("Busts", styles[v], v != 0, () -> { set.accept((v + 1) % 3); cfg.save(); },
                () -> { set.accept((v + 2) % 3); cfg.save(); }));
        if (v == 1) rows.add(new NoticeRow("A bust jumps to its own ending as soon as it is known - so one that keeps going is a win."));
        if (v == 2) rows.add(new NoticeRow("A kill opens nothing; only a win plays, a moment later, when its drop line comes."));
    }

    /** "Try it": pick an outcome, then play it - the same cutscene a real drop gets, never counted in the stats. */
    private void tryRows(List<Row> rows, String group, List<String> outcomes, Function<String, String> title) {
        rows.add(new SectionRow("Try it"));
        int i = Math.floorMod(TRY.getOrDefault(group, 0), outcomes.size());
        String pick = outcomes.get(i);
        String name = outcomeName(pick, title);
        rows.add(new ValueRow("Outcome", name, true, () -> TRY.put(group, i + 1), () -> TRY.put(group, i - 1)));
        if (host.canTry()) {
            rows.add(new ButtonRow("▶  Play " + name, curAccent, () -> host.playTest(group, pick)));
        } else {
            rows.add(new NoticeRow("Join a world to try it - the cutscenes play over the game."));
        }
    }

    private static String outcomeName(String pick, Function<String, String> title) {
        if (pick.equals("BUST")) return "Bust";
        String t = null;
        try {
            t = title.apply(pick);
        } catch (RuntimeException ignored) {
        }
        return t == null || t.isEmpty() ? pretty(pick) : t;
    }

    private static String pretty(String name) {
        String s = name.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String scathaOutcome(String pick) {
        return switch (pick) {
            case "RANDOM" -> "Random (real odds)";
            case "DWARVEN_OS" -> "Dwarven O's";
            case "PET_RARE" -> "Rare pet";
            case "PET_EPIC" -> "Epic pet";
            case "PET_LEGENDARY" -> "Legendary pet";
            default -> "Gemstones";
        };
    }

    private static String vanguardOutcome(String pick) {
        return switch (pick) {
            case "RANDOM" -> "Random (real odds)";
            case "UNCOMMON" -> "An uncommon";
            case "LEGENDARY" -> "A legendary";
            case "LOCKET" -> "Shattered Locket";
            case "DYE" -> "Frostbitten Dye";
            default -> "All commons";
        };
    }

    /** "500k", "2M", "100M": the price floor's steps. */
    private static String coins(long c) {
        if (c >= 1_000_000) return (c % 1_000_000 == 0 ? String.valueOf(c / 1_000_000) : String.format("%.1f", c / 1e6)) + "M";
        if (c >= 1_000) return c / 1_000 + "k";
        return String.valueOf(c);
    }

    private static List<String> names(Enum<?>[] values) {
        List<String> out = new ArrayList<>();
        for (Enum<?> e : values) out.add(e.name());
        return out;
    }

    private ToggleRow toggle(String label, boolean value, Runnable flip, PatternConfig cfg) {
        return new ToggleRow(label, value, () -> {
            flip.run();
            cfg.save();
        });
    }

    // ------------------------------------------------------------------ the Sound tab

    private void soundRows(List<Row> rows, PatternConfig cfg) {
        rows.add(new SectionRow("Levels"));
        rows.add(new SliderRow("vol", "Cutscene volume", cfg.cutsceneVolume, 0, 150, 5, v -> cfg.cutsceneVolume = v, v -> v + "%"));
        rows.add(new NoticeRow("On top of Minecraft's master volume. The Scatha plays at this level."));
        rows.add(new SectionRow("Each animation"));
        soundSlider(rows, "snd_vanguard", "Vanguard Thaw (its endings)", VANGUARD, cfg.vanguardFinaleVolume, v -> cfg.vanguardFinaleVolume = v);
        soundSlider(rows, "snd_diana", "Diana - The Chopping Block", DIANA, cfg.dianaVolume, v -> cfg.dianaVolume = v);
        soundSlider(rows, "snd_lava", "Lava fishing - Eruption", LAVA, cfg.lavaVolume, v -> cfg.lavaVolume = v);
        soundSlider(rows, "snd_blaze", "Inferno Demonlord - Hellion", BLAZE, cfg.blazeVolume, v -> cfg.blazeVolume = v);
        if (!com.butcher.Maintenance.SERAPH) {
            soundSlider(rows, "snd_seraph", "Voidgloom Seraph", SERAPH, cfg.seraphVolume, v -> cfg.seraphVolume = v);
        }
        soundSlider(rows, "snd_fishing", "Fishing Festival - Fin", FIN, cfg.fishingVolume, v -> cfg.fishingVolume = v);
        soundSlider(rows, "snd_ghost", "Dwarven Mines Ghosts - Wisp", WISP, cfg.ghostVolume, v -> cfg.ghostVolume = v);
        rows.add(new NoticeRow("The same sliders as in each animation's own card."));
        rows.add(new SectionRow("Switches"));
        rows.add(toggle("Cutscene sound", cfg.cutsceneAudio, () -> cfg.cutsceneAudio = !cfg.cutsceneAudio, cfg));
        rows.add(toggle("Silence the game", cfg.silenceGameAudio, () -> cfg.silenceGameAudio = !cfg.silenceGameAudio, cfg));
        rows.add(toggle("Pet fanfare", cfg.petFanfare, () -> cfg.petFanfare = !cfg.petFanfare, cfg));
        if (cfg.petFanfare) {
            rows.add(new SliderRow("fanfare", "Fanfare volume", cfg.petFanfareVolume, 0, 150, 5, v -> cfg.petFanfareVolume = v, v -> v + "%"));
        }
        rows.add(new NoticeRow("Every sound is made as the cutscene plays. The game stays quiet so nothing spoils the reveal."));
    }

    private void soundSlider(List<Row> rows, String id, String label, int accent, int value, IntConsumer set) {
        int before = curAccent;
        curAccent = accent;
        rows.add(new SliderRow(id, label, value, 0, 150, 5, set, v -> v + "%"));
        curAccent = before;
    }

    // ------------------------------------------------------------------ the General tab

    private void generalRows(List<Row> rows, PatternConfig cfg) {
        rows.add(new SectionRow("Hiding the HUD"));
        rows.add(toggle("Take the HUD away", cfg.hideHud, () -> cfg.hideHud = !cfg.hideHud, cfg));
        rows.add(new NoticeRow("Hides the HUD and chat just before any tracked kill - a Scatha, Diana's creatures, your slayer boss, your lava creature - and for the whole cutscene."));
        rows.add(toggle("Keep health and hotbar", cfg.keepVitals, () -> cfg.keepVitals = !cfg.keepVitals, cfg));
        rows.add(new NoticeRow("Your hotbar, hearts, hunger, armour and XP stay on screen, over the cutscene, so a fight is never blind."));
        rows.add(new NoticeRow("Hold Backspace to skip a cutscene: a win jumps to its showcase, a bust ends. Rebind it in Controls, under Butcher."));

        rows.add(new SectionRow("Effects"));
        rows.add(toggle("Calmer effects", cfg.calmEffects, () -> cfg.calmEffects = !cfg.calmEffects, cfg));
        rows.add(new NoticeRow("No screen shake and softer flashes, in every animation."));
        rows.add(toggle("Chat summary", cfg.chatSummary, () -> cfg.chatSummary = !cfg.chatSummary, cfg));
        rows.add(new NoticeRow("One line in chat as each cutscene ends: what dropped, its price, which one of yours it is."));

        rows.add(new SectionRow("Prices"));
        rows.add(toggle("Live prices", cfg.livePrices, () -> cfg.livePrices = !cfg.livePrices, cfg));
        rows.add(new NoticeRow("Bazaar prices from api.hypixel.net, auction prices from sky.coflnet.com. Off hides every price and the Vanguard profit window."));
        long[] worth = PatternConfig.MIN_WORTH;
        rows.add(new SliderRow("min_worth", "Only play drops worth at least", cfg.minWorth, 0, worth.length - 1, 1,
                v -> cfg.minWorth = v, v -> v == 0 ? "Any" : coins(worth[v])));
        rows.add(new NoticeRow("A win worth less plays as a bust - Diana, the slayers and lava fishing. A price not fetched yet always plays."));

        rows.add(new SectionRow("Gameplay window"));
        String[] corners = {"Off", "Top right", "Top left", "Bottom right", "Bottom left"};
        int corner = Math.max(0, Math.min(4, cfg.gameplayCorner));
        rows.add(new ValueRow("During a cutscene", corners[corner], corner != 0,
                () -> { cfg.gameplayCorner = (corner + 1) % corners.length; cfg.save(); },
                () -> { cfg.gameplayCorner = (corner + corners.length - 1) % corners.length; cfg.save(); }));
        rows.add(new NoticeRow("The game, live, in a corner while a cutscene plays - so you can see what is around you."));

        rows.add(new SectionRow("Drop records"));
        rows.add(toggle("Record drop events", cfg.recordDrops, () -> cfg.recordDrops = !cfg.recordDrops, cfg));
        rows.add(new NoticeRow("For bug reports: writes every real kill and drop, with the chat around it, to config/butcher-drops.jsonl so one that went wrong can be replayed. Stays on your computer; send the file only if you choose to. /butcher records shows the count."));

        rows.add(new SectionRow("Art"));
        rows.add(toggle("Write art to config", cfg.writeGeneratedArt, () -> cfg.writeGeneratedArt = !cfg.writeGeneratedArt, cfg));
        rows.add(new NoticeRow("Writes the art to config/butcher/textures/ so you can edit it. Your edits are never overwritten."));
        rows.add(new NoticeRow("Your own Scatha skin: head.png or body.png in config/butcher/skins/. A .gltf in config/butcher/models/ replaces the worm. /butcher log, skins and reload help."));

        rows.add(new SectionRow("Start again"));
        rows.add(resetButton("settings", "Reset all settings", host::resetSettings));
        rows.add(new NoticeRow("Every setting back to its default. Your stats stay."));
    }

    // ------------------------------------------------------------------ the Stats tab

    private void statsRows(List<Row> rows) {
        ButcherStats st = ButcherStats.get();
        if (group(rows, "stats-scatha", "Scatha", streak(st.dryStreak, "kill", "since a pet"), "amethyst_shard", SCATHA, null, null)) {
            rows.add(new SectionRow("The dry streak"));
            rows.add(new HeroRow(st.dryStreak, "kills since a pet"));
            rows.add(new StatRow("Worst it has been", Integer.toString(st.worstDryStreak), DIM));
            if (st.kills > 0) {
                rows.add(new StatRow("Pets per kill", st.pets() == 0 ? "none yet"
                        : String.format("1 in %,d", st.kills / Math.max(1, st.pets())), DIM));
            }
            rows.add(new SectionRow("Pets"));
            rows.add(new StatRow("Legendary", Integer.toString(st.petsLegendary), 0xFFFFAA00));
            rows.add(new StatRow("Epic", Integer.toString(st.petsEpic), 0xFFC860E8));
            rows.add(new StatRow("Rare", Integer.toString(st.petsRare), 0xFF7A96FF));
            rows.add(new SectionRow("Everything else"));
            rows.add(new StatRow("Scathas killed", String.format("%,d", st.kills), TEXT));
            rows.add(new StatRow("Cutscenes played", String.format("%,d", st.cutscenes), DIM));
            rows.add(new StatRow("Gemstones", String.format("%,d", st.gemstones), DIM));
            rows.add(new StatRow("Dwarven O's", String.format("%,d", st.dwarvenOs), DIM));
            rows.add(new StatRow("Got away", String.format("%,d", st.misses), DIM));
            rows.add(new StatRow("Cuts landed", String.format("%,d", st.chops), DIM));
            rows.add(resetButton("stats-scatha", "Reset Scatha stats", st::resetScatha));
        }
        if (group(rows, "stats-vanguard", "Vanguard Corpses", streak(st.corpseDryStreak, "corpse", "since a legendary"), "blue_ice", VANGUARD, null, null)) {
            rows.add(new SectionRow("The dry streak"));
            rows.add(new HeroRow(st.corpseDryStreak, "corpses since a legendary"));
            rows.add(new StatRow("Worst it has been", Integer.toString(st.worstCorpseDryStreak), DIM));
            rows.add(new StatRow("Expected", String.format("1 in %.0f", 1f / VanguardLoot.legendaryChance(VanguardLoot.AVG_ROLLS)), DIM));
            rows.add(new SectionRow("Drops"));
            rows.add(new StatRow("Frostbitten Dye", Integer.toString(st.corpseSpecials), VanguardLoot.Tier.SPECIAL.glow()));
            rows.add(new StatRow("Legendary", Integer.toString(st.corpseLegendaries), 0xFFFFAA00));
            rows.add(new StatRow("Uncommon", Integer.toString(st.corpseUncommons), 0xFF55FFFF));
            rows.add(new StatRow("Common", Integer.toString(st.corpseCommons), DIM));
            rows.add(new SectionRow("Everything else"));
            rows.add(new StatRow("Corpses opened", String.format("%,d", st.corpses), TEXT));
            rows.add(new StatRow("Rolls seen", String.format("%,d", st.corpseRolls), DIM));
            if (st.corpses > 0) rows.add(new StatRow("Rolls per corpse", String.format("%.2f", st.corpseRolls / (float) st.corpses), DIM));
            else rows.add(new NoticeRow("Nothing yet. Only real corpses are counted; Try it never is."));
            rows.add(resetButton("stats-vanguard", "Reset Vanguard stats", st::resetVanguard));
        }
        if (group(rows, "stats-diana", "Diana", streak(st.dianaDryStreak, "kill", "since a drop"), "golden_shovel", DIANA, null, null)) {
            rows.add(new SectionRow("The dry streak"));
            rows.add(new HeroRow(st.dianaDryStreak, "kills since a drop"));
            rows.add(new StatRow("Worst it has been", Integer.toString(st.worstDianaDryStreak), DIM));
            rows.add(new SectionRow("Drops"));
            String[][] drops = {
                    {"Shimmering Wool", "FFAA00"}, {"Manti-core", "FFAA00"}, {"Minos Relic", "C860E8"}, {"Chimera I", "FF55FF"},
                    {"Mythological Dye", "FFAA00"}, {"Fateful Stinger", "FFAA00"}, {"Crown of Greed", "FFAA00"}, {"Brain Food", "C860E8"}};
            for (String[] dr : drops) {
                rows.add(new StatRow(dr[0], Integer.toString(st.dianaDropCount(dr[0])), 0xFF000000 | Integer.parseInt(dr[1], 16)));
            }
            rows.add(new SectionRow("Kills"));
            String[][] kills = {{"SPHINX", "Sphinxes"}, {"INQUISITOR", "Minos Inquisitors"}, {"MANTICORE", "Manticores"},
                    {"KING_MINOS", "King Minos"}, {"CHAMPION", "Minos Champions (Relic)"}};
            int total = 0;
            for (String[] k : kills) {
                int n = st.dianaKillCount(k[0]);
                total += n;
                rows.add(new StatRow(k[1], String.format("%,d", n), DIM));
            }
            if (total == 0) rows.add(new NoticeRow("Nothing yet. Only real kills are counted; Try it never is."));
            rows.add(resetButton("stats-diana", "Reset Diana stats", st::resetDiana));
        }
        sourceGroup(rows, st, "blaze", "Hellion - Inferno Demonlord IV", "blaze_rod", BLAZE, "kill", "Bosses killed",
                titles(com.butcher.anim.HellionTimeline.Drop.values()));
        if (!com.butcher.Maintenance.SERAPH) {
            sourceGroup(rows, st, "seraph", "The Seraph - Voidgloom Seraph IV", "ender_pearl", SERAPH, "kill", "Bosses killed",
                    titles(com.butcher.anim.SeraphTimeline.Drop.values()));
        }
        sourceGroup(rows, st, "lava", "Eruption - lava fishing", "magma_cream", LAVA, "kill", "Rare creatures killed",
                titles(com.butcher.anim.EruptionTimeline.Drop.values()));
        sourceGroup(rows, st, "shark", "Fin - Great White Sharks", "tropical_fish", FIN, "shark", "Sharks killed",
                titles(com.butcher.detect.SharkDrops.Drop.values()));
        if (group(rows, "stats-ghost", "Wisp - Dwarven Mines Ghosts", ghostTotal(st) + (ghostTotal(st) == 1 ? " drop" : " drops") + " so far", "ghast_tear", WISP, null, null)) {
            ButcherStats.Source s = st.source("ghost");
            rows.add(new SectionRow("Drops"));
            int total = 0;
            for (String dr : titles(com.butcher.detect.GhostDrops.Drop.values())) {
                int n = s.drops.getOrDefault(dr, 0);
                total += n;
                rows.add(new StatRow(dr, Integer.toString(n), 0xFF7A96FF));
            }
            if (total == 0) rows.add(new NoticeRow("Nothing yet. Only real drops are counted; Try it never is."));
            rows.add(resetButton("stats-ghost", "Reset these stats", () -> st.resetSource("ghost")));
        }
    }

    private void sourceGroup(List<Row> rows, ButcherStats st, String key, String title, String icon, int accent,
                             String unit, String killWord, String[] drops) {
        String id = "stats-" + key;
        if (!group(rows, id, title, streak(st.source(key).dry, unit, "since a drop"), icon, accent, null, null)) return;
        ButcherStats.Source s = st.source(key);
        rows.add(new SectionRow("The dry streak"));
        rows.add(new HeroRow(s.dry, unit + "s since a drop"));
        rows.add(new StatRow("Worst it has been", Integer.toString(s.worstDry), DIM));
        rows.add(new SectionRow("Drops"));
        for (String dr : drops) rows.add(new StatRow(dr, Integer.toString(s.drops.getOrDefault(dr, 0)), TEXT));
        rows.add(new SectionRow("Everything else"));
        rows.add(new StatRow(killWord, String.format("%,d", s.kills), DIM));
        if (s.kills == 0) rows.add(new NoticeRow("Nothing yet. Only real kills are counted; Try it never is."));
        rows.add(resetButton(id, "Reset these stats", () -> st.resetSource(key)));
    }

    /** "312 kills since a pet". */
    private static String streak(int n, String unit, String since) {
        return String.format("%,d", n) + " " + unit + (n == 1 ? "" : "s") + " " + since;
    }

    private static int ghostTotal(ButcherStats st) {
        int n = 0;
        for (int v : st.source("ghost").drops.values()) n += v;
        return n;
    }

    /** Every drop's display name, rarest first as the enum lists them, the bust left out. */
    private static String[] titles(Enum<?>[] drops) {
        List<String> out = new ArrayList<>();
        for (Enum<?> e : drops) {
            try {
                String t = (String) e.getClass().getField("title").get(e);
                if (t != null && !t.isEmpty() && !out.contains(t)) out.add(t);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        java.util.Collections.reverse(out);
        return out.toArray(new String[0]);
    }

    /** Two clicks to wipe, so a misclick cannot throw away a long streak; the second must come within 4 s. */
    private ButtonRow resetButton(String id, String label, Runnable wipe) {
        boolean armed = isArmed(id);
        ButtonRow b = new ButtonRow(armed ? "Click again to confirm" : label, RED, () -> {
            if (isArmed(id)) {
                wipe.run();
                resetArmed = null;
            } else {
                resetArmed = id;
                resetArmedAt = System.currentTimeMillis();
            }
        });
        b.loud = armed;
        return b;
    }

    private boolean isArmed(String id) {
        if (!id.equals(resetArmed)) return false;
        if (System.currentTimeMillis() - resetArmedAt > RESET_ARM_MS) {
            resetArmed = null;
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ row kinds

    /** A card: icon, name, its switch (if it is one), and the chevron. Clicking the switch flips it; anywhere else opens it. */
    private final class HeaderRow extends Row {
        final String id, title, sub, icon;
        final Boolean on;
        final Runnable toggle;

        HeaderRow(String id, String title, String sub, String icon, Boolean on, Runnable toggle) {
            this.id = id; this.title = title; this.sub = sub; this.icon = icon; this.on = on; this.toggle = toggle;
        }

        @Override int height() { return 30; }

        int switchX(int x, int w) { return x + w - 44; }

        boolean onSwitch(double mx, double my, int x, int y, int w) {
            return on != null && within(mx, my, switchX(x, w) - 4, y, switchX(x, w) + 24, y + height());
        }

        @Override
        void draw(Draw d, int x, int y, int w, int mx, int my) {
            boolean open = isOpen(id), hover = within(mx, my, x, y, x + w, y + height());
            int h = height();
            d.fill(x, y, x + w, y + h, open ? withAlpha(accent, 0x2A) : hover ? 0x22FFFFFF : 0x12FFFFFF);
            outline(d, x, y, w, h, open ? withAlpha(accent, 0xB0) : hover ? 0x40FFFFFF : 0x1CFFFFFF);
            boolean off = on != null && !on;
            d.fill(x, y, x + 3, y + h, off ? withAlpha(accent, 0x70) : accent);
            // The icon in a little well of the card's colour.
            d.fill(x + 7, y + 6, x + 27, y + 24, withAlpha(accent, 0x30));
            d.item(icon, x + 9, y + 7);
            if (off) d.fill(x + 7, y + 6, x + 27, y + 24, 0x90101216);
            int tx = x + 33, maxW = (on != null ? switchX(x, w) - 8 : x + w - 20) - tx;
            int titleCol = off ? DIM : open || hover ? 0xFFFFFFFF : TEXT;
            d.text(fit(d, title, maxW), tx, y + 6, titleCol, false, true);
            if (sub != null) {
                String s = off ? "Switched off" : sub;
                d.text(fit(d, s, maxW), tx, y + 17, off ? FAINT : lerpColor(accent, DIM, 0.55f), false, false);
            }
            if (on != null) {
                float t = approach("hdr:" + id, on ? 1f : 0f);
                pill(d, switchX(x, w), y + 10, t, accent, hover && onSwitch(mx, my, x, y, w));
            }
            chevron(d, x + w - 13, y + h / 2, open, open || hover ? accent : DIM);
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            return within(mx, my, x, y, x + w, y + height());
        }
    }

    /**
     * A card that is shut for now: drawn faded, with hazard tape across it reading "UNDER MAINTENANCE". It has no
     * switch and does not open. The tape sways a little while the mouse is on it.
     */
    private final class TapedRow extends Row {
        static final int TAPE = 0xFFF2C21B, INK = 0xFF16161A;
        final String title, sub, icon;

        TapedRow(String title, String sub, String icon) { this.title = title; this.sub = sub; this.icon = icon; }

        @Override int height() { return 30; }

        @Override
        void draw(Draw d, int x, int y, int w, int mx, int my) {
            int h = height();
            boolean hover = within(mx, my, x, y, x + w, y + h);
            // The card underneath, faded as a switched-off one is.
            d.fill(x, y, x + w, y + h, 0x0CFFFFFF);
            outline(d, x, y, w, h, 0x18FFFFFF);
            d.fill(x, y, x + 3, y + h, withAlpha(accent, 0x50));
            d.fill(x + 7, y + 6, x + 27, y + 24, withAlpha(accent, 0x20));
            d.item(icon, x + 9, y + 7);
            d.fill(x + 7, y + 6, x + 27, y + 24, 0xA0101216);
            int tx = x + 33, maxW = x + w - 12 - tx;
            d.text(fit(d, title, maxW), tx, y + 6, FAINT, false, true);
            d.text(fit(d, sub, maxW), tx, y + 17, withAlpha(FAINT, 0xB0), false, false);

            // The tape: a band a little longer than the card, turned a few degrees, slanted black stripes on yellow.
            float sway = approach("tape:" + title, hover ? 1f : 0f)
                    * (float) Math.sin(System.currentTimeMillis() / 140.0) * 0.012f;
            int half = w / 2 + 4, top = -6, bottom = 7, period = 12;
            d.push();
            d.translate(x + w / 2f, y + h / 2f + 1);
            d.rotate(-0.04f + sway);
            d.fill(-half + 1, top + 2, half + 1, bottom + 2, 0x70000000);   // its shadow on the card
            d.fill(-half, top, half, bottom, TAPE);
            for (int r = top; r < bottom; r++) {
                int shift = bottom - r;                                     // each row a pixel along: "/" stripes
                for (int sx = -half - period; sx < half; sx += period) {
                    int a = Math.max(-half, sx + shift), b = Math.min(half, sx + shift + period / 2);
                    if (a < b) d.fill(a, r, b, r + 1, INK);
                }
            }
            d.fill(-half, top, half, top + 1, 0x40FFFFFF);                  // a sheen along its top edge
            // The words on a plate of their own, so the stripes do not run through them.
            String label = "UNDER MAINTENANCE";
            int lw = d.width(label, true), px = lw / 2 + 6;
            d.fill(-px, top + 1, px, bottom - 1, TAPE);
            d.fill(-px, top + 1, -px + 1, bottom - 1, INK);
            d.fill(px - 1, top + 1, px, bottom - 1, INK);
            d.text(label, -lw / 2, top + 3, INK, true, false);
            d.pop();
        }
    }

    /** A section heading: a short bar in the card's colour, the label in capitals, a rule to the edge. */
    private final class SectionRow extends Row {
        final String label;

        SectionRow(String label) { this.label = label.toUpperCase(java.util.Locale.ROOT); }

        @Override int height() { return 15; }

        @Override
        void draw(Draw d, int x, int y, int w, int mx, int my) {
            int ty = y + 6;
            int col = lerpColor(accent, 0xFFFFFFFF, 0.25f);
            d.text(label, x + 2, ty, col, false, false);
            int rx = x + 2 + d.width(label, false) + 6;
            if (rx < x + w) d.fill(rx, ty + 4, x + w, ty + 5, withAlpha(accent, 0x40));
        }
    }

    /** Wrapped explanation in a quiet colour. */
    private final class NoticeRow extends Row {
        final List<String> lines;

        NoticeRow(String text) {
            int w = rowsWidth() - indent - (grouped ? 9 : 0) - 10;
            this.lines = wrap(measure, text, w);
        }

        @Override int height() { return lines.size() * 10 + 1; }

        @Override
        void draw(Draw d, int x, int y, int w, int mx, int my) {
            int ly = y + 1;
            for (String line : lines) {
                d.text(line, x + 4, ly, FAINT, false, false);
                ly += 10;
            }
        }
    }

    /** A labelled switch; the whole row is the target. */
    private final class ToggleRow extends Row {
        final String label, key;
        final boolean on;
        final Runnable flip;

        ToggleRow(String label, boolean on, Runnable flip) {
            this.label = label; this.on = on; this.flip = flip;
            this.key = curGroup + ":" + label;
        }

        @Override int height() { return 18; }

        @Override
        void draw(Draw d, int x, int y, int w, int mx, int my) {
            boolean hover = within(mx, my, x, y, x + w, y + height());
            d.fill(x, y, x + w, y + height(), hover ? ROW_HOVER : ROW);
            d.text(fit(d, label, w - 44), x + 8, y + 5, hover ? 0xFFFFFFFF : TEXT, false, false);
            pill(d, x + w - 28, y + 4, approach(key, on ? 1f : 0f), accent, hover);
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            if (!within(mx, my, x, y, x + w, y + height())) return false;
            flip.run();
            return true;
        }
    }

    /** Label and value above a track with a knob. Click or drag anywhere on the row. */
    private final class SliderRow extends Row {
        final String id, label, text;
        final int value, min, max, step;
        final IntConsumer setter;

        SliderRow(String id, String label, int value, int min, int max, int step, IntConsumer setter, IntFunction<String> format) {
            this.id = id; this.label = label;
            this.value = Math.max(min, Math.min(max, value));
            this.min = min; this.max = max; this.step = step; this.setter = setter;
            this.text = format.apply(this.value);
        }

        @Override int height() { return 24; }
        @Override String dragId() { return id; }

        @Override
        void draw(Draw d, int x, int y, int w, int mx, int my) {
            boolean active = id.equals(draggingId), hover = active || within(mx, my, x, y, x + w, y + height());
            d.fill(x, y, x + w, y + height(), hover ? ROW_HOVER : ROW);
            int vw = d.width(text, false);
            d.text(fit(d, label, w - vw - 24), x + 8, y + 4, hover ? 0xFFFFFFFF : TEXT, false, false);
            d.text(text, x + w - 8 - vw, y + 4, lerpColor(accent, 0xFFFFFFFF, 0.35f), false, false);
            int t0 = x + 8, t1 = x + w - 8, ty = y + 16;
            float f = (value - min) / (float) Math.max(1, max - min);
            int kx = t0 + Math.round((t1 - t0) * approach("sl:" + id, f));
            d.fill(t0, ty, t1, ty + 2, TRACK);
            d.fill(t0, ty, kx, ty + 2, accent);
            int kc = hover ? 0xFFFFFFFF : lerpColor(accent, 0xFFFFFFFF, 0.55f);
            d.fill(kx - 2, ty - 3, kx + 3, ty + 5, 0xFF000000);
            d.fill(kx - 1, ty - 2, kx + 2, ty + 4, kc);
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            if (!within(mx, my, x, y, x + w, y + height())) return false;
            dragTo(mx, x, w);
            return true;
        }

        @Override
        void dragTo(double mx, int x, int w) {
            float t = clamp01((float) ((mx - (x + 8)) / Math.max(1.0, w - 16)));
            int v = Math.round(min + t * (max - min));
            setter.accept(Math.max(min, Math.min(max, Math.round(v / (float) step) * step)));
        }
    }

    /** "Label   < Value >": click for the next value, right-click for the one before. */
    private final class ValueRow extends Row {
        final String label, value;
        final boolean lit;
        final Runnable next, back;

        ValueRow(String label, String value, boolean lit, Runnable next, Runnable back) {
            this.label = label; this.value = value; this.lit = lit; this.next = next; this.back = back;
        }

        @Override int height() { return 18; }

        @Override
        void draw(Draw d, int x, int y, int w, int mx, int my) {
            boolean hover = within(mx, my, x, y, x + w, y + height());
            d.fill(x, y, x + w, y + height(), hover ? ROW_HOVER : ROW);
            String shown = fit(d, value, w - d.width(label, false) - 44);
            int vw = d.width(shown, false), right = x + w - 8;
            d.text(">", right - 5, y + 5, hover ? accent : FAINT, false, false);
            d.text(shown, right - 11 - vw, y + 5, lit ? lerpColor(accent, 0xFFFFFFFF, 0.35f) : DIM, false, false);
            d.text("<", right - 19 - vw, y + 5, hover ? accent : FAINT, false, false);
            d.text(label, x + 8, y + 5, hover ? 0xFFFFFFFF : TEXT, false, false);
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            if (!within(mx, my, x, y, x + w, y + height())) return false;
            next.run();
            return true;
        }

        @Override
        boolean rightClick(double mx, double my, int x, int y, int w) {
            if (back == null || !within(mx, my, x, y, x + w, y + height())) return false;
            back.run();
            return true;
        }
    }

    /** A real button: filled in its colour, the label centred. */
    private final class ButtonRow extends Row {
        final String label;
        final int colour;
        final Runnable action;
        boolean loud;

        ButtonRow(String label, int colour, Runnable action) {
            this.label = label; this.colour = colour; this.action = action;
        }

        @Override int height() { return 20; }

        @Override
        void draw(Draw d, int x, int y, int w, int mx, int my) {
            boolean hover = within(mx, my, x, y, x + w, y + height());
            float pulse = loud ? 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 90.0) : 0f;
            int fillA = loud ? 0x60 + Math.round(0x50 * pulse) : hover ? 0x70 : 0x38;
            d.fill(x, y, x + w, y + height(), withAlpha(colour, fillA));
            outline(d, x, y, w, height(), withAlpha(colour, hover || loud ? 0xFF : 0xA0));
            d.fill(x + 1, y + 1, x + w - 1, y + 2, 0x30FFFFFF);
            String s = fit(d, label, w - 12);
            d.text(s, x + (w - d.width(s, true)) / 2, y + 6, 0xFFFFFFFF, true, true);
        }

        @Override
        boolean click(double mx, double my, int x, int y, int w) {
            if (!within(mx, my, x, y, x + w, y + height())) return false;
            action.run();
            return true;
        }
    }

    /** The number that matters most - the dry streak - large, in the card's colour, with what it counts beside it. */
    private final class HeroRow extends Row {
        final String value, label;

        HeroRow(int value, String label) { this.value = String.format("%,d", value); this.label = label; }

        @Override int height() { return 30; }

        @Override
        void draw(Draw d, int x, int y, int w, int mx, int my) {
            d.fill(x, y, x + w, y + height(), withAlpha(accent, 0x1C));
            d.fill(x, y, x + 2, y + height(), accent);
            d.push();
            d.translate(x + 10, y + 7);
            d.scale(2f);
            d.text(value, 0, 0, lerpColor(accent, 0xFFFFFFFF, 0.3f), true, true);
            d.pop();
            int vx = x + 10 + d.width(value, true) * 2 + 8;
            d.text(label, vx, y + 12, DIM, false, false);
        }
    }

    /** A read-only "label ........ value" line. */
    private final class StatRow extends Row {
        final String label, value;
        final int colour;

        StatRow(String label, String value, int colour) { this.label = label; this.value = value; this.colour = colour; }

        @Override int height() { return 15; }

        @Override
        void draw(Draw d, int x, int y, int w, int mx, int my) {
            boolean hover = within(mx, my, x, y, x + w, y + height());
            d.fill(x, y, x + w, y + height(), hover ? ROW_HOVER : ROW);
            d.text(label, x + 8, y + 4, DIM, false, false);
            d.text(value, x + w - 8 - d.width(value, false), y + 4, colour, false, false);
        }
    }

    // ------------------------------------------------------------------ pieces

    /** A switch: a rounded track that fills with the colour, and a knob that slides. {@code t} 0 off .. 1 on. */
    private static void pill(Draw d, int x, int y, float t, int accent, boolean hover) {
        int w = 20, h = 10;
        int track = lerpColor(SWITCH_OFF, accent, t);
        d.fill(x + 1, y, x + w - 1, y + h, track);
        d.fill(x, y + 1, x + w, y + h - 1, track);
        d.fill(x + 1, y + h - 1, x + w - 1, y + h, 0x30000000);
        int kx = x + 1 + Math.round(t * (w - 10));
        int knob = hover ? 0xFFFFFFFF : 0xFFE6E9EC;
        d.fill(kx + 1, y + 1, kx + 7, y + h - 1, knob);
        d.fill(kx, y + 2, kx + 8, y + h - 2, knob);
    }

    /** A small triangle: right when shut, down when open. */
    private static void chevron(Draw d, int cx, int cy, boolean open, int argb) {
        if (open) {
            for (int i = 0; i < 4; i++) d.fill(cx - 3 + i, cy - 1 + i, cx + 4 - i, cy + i, argb);
        } else {
            for (int i = 0; i < 4; i++) d.fill(cx - 1 + i, cy - 3 + i, cx + i, cy + 4 - i, argb);
        }
    }

    private static void outline(Draw d, int x, int y, int w, int h, int argb) {
        d.fill(x, y, x + w, y + 1, argb);
        d.fill(x, y + h - 1, x + w, y + h, argb);
        d.fill(x, y + 1, x + 1, y + h - 1, argb);
        d.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }

    /** Eases a value towards its target, by id, frame-rate independent. */
    private float approach(String key, float target) {
        Float cur = anim.get(key);
        float v = cur == null ? target : cur + (target - cur) * Math.min(1f, dt * 14f);
        if (Math.abs(v - target) < 0.002f) v = target;
        anim.put(key, v);
        return v;
    }

    /** {@code s} cut to fit {@code w} pixels, with "..." if anything was cut. */
    private static String fit(Draw d, String s, int w) {
        if (d.width(s, false) <= w) return s;
        String dots = "...";
        int end = s.length();
        while (end > 0 && d.width(s.substring(0, end) + dots, false) > w) end--;
        return s.substring(0, end).stripTrailing() + dots;
    }

    private static List<String> wrap(Draw d, String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = cur.isEmpty() ? word : cur + " " + word;
            if (cur.isEmpty() || d.width(candidate, false) <= width) {
                cur.setLength(0);
                cur.append(candidate);
            } else {
                lines.add(cur.toString());
                cur.setLength(0);
                cur.append(word);
            }
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        return lines;
    }

    // ------------------------------------------------------------------ input

    /** @return whether the menu used the click */
    public boolean mouseClicked(double mx, double my, int button) {
        int px = panelX(), py = panelY(), pw = panelW();
        int vt = viewTop(), vb = viewBottom();
        if (button == 1) {
            if (!within(mx, my, px, vt, px + pw, vb)) return false;
            int x0 = rowsLeft(), w = rowsWidth();
            for (Placed p : placed) {
                int[] g = geometry(p, x0, w);
                if (p.row.rightClick(mx, my, g[0], vt + p.y - Math.round(scroll), g[1])) return true;
            }
            return true;
        }
        if (button != 0) return false;
        if (my >= py && my < py + TAB_H && mx >= px && mx < px + pw) {
            int i = Math.min(TAB_NAMES.length - 1, (int) ((mx - px) / (pw / TAB_NAMES.length)));
            if (i != tab) {
                tab = i;
                scroll = scrollTarget = 0f;
            }
            return true;
        }
        int bx = barX();
        if (scrollable() && mx >= bx - SCROLLBAR_GRAB && mx <= bx + SCROLLBAR_W + SCROLLBAR_GRAB && my >= vt && my <= vb) {
            draggingBar = true;
            barTo(my);
            return true;
        }
        if (!within(mx, my, px, vt, px + pw, vb)) return within(mx, my, px, py - TITLE_H, px + pw, py + panelH());
        int x0 = rowsLeft(), w = rowsWidth();
        for (Placed p : placed) {
            int[] g = geometry(p, x0, w);
            int ry = vt + p.y - Math.round(scroll);
            if (p.row instanceof HeaderRow h && h.click(mx, my, g[0], ry, g[1])) {
                if (h.onSwitch(mx, my, g[0], ry, g[1])) {
                    h.toggle.run();
                } else if (!openGroups.remove(h.id)) {
                    openGroups.add(h.id);
                }
                return true;
            }
            if (!p.row.click(mx, my, g[0], ry, g[1])) continue;
            if (p.row.dragId() != null) draggingId = p.row.dragId();
            return true;
        }
        return true;
    }

    /** Where a row is drawn: {x, width}, the same sums the drawing makes. */
    private static int[] geometry(Placed p, int left, int w) {
        int x = left + p.row.indent, rw = w - p.row.indent;
        if (p.row.grouped) {
            x += 9;
            rw -= 9;
        }
        return new int[]{x, rw};
    }

    public boolean mouseDragged(double mx, double my, int button) {
        if (button != 0) return false;
        if (draggingBar) {
            barTo(my);
            return true;
        }
        if (draggingId == null) return false;
        int x0 = rowsLeft(), w = rowsWidth();
        for (Placed p : placed) {
            if (draggingId.equals(p.row.dragId())) {
                int[] g = geometry(p, x0, w);
                p.row.dragTo(mx, g[0], g[1]);
                return true;
            }
        }
        return true;
    }

    /** @return whether a slider was let go (its value is then saved) */
    public boolean mouseReleased(double mx, double my, int button) {
        if (button != 0) return false;
        if (draggingBar) {
            draggingBar = false;
            return true;
        }
        if (draggingId != null) {
            draggingId = null;
            host.config().save();
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!scrollable()) return false;
        scrollTarget = clampScroll(scrollTarget - (float) amount * 22f);
        return true;
    }

    private void barTo(double my) {
        int vt = viewTop(), vh = viewH();
        int thumbH = Math.max(18, Math.round(vh * (vh / (float) contentHeight)));
        int travel = vh - thumbH;
        if (travel <= 0) return;
        float f = clamp01((float) ((my - vt - thumbH / 2.0) / travel));
        scroll = scrollTarget = clampScroll(f * (contentHeight - vh));
    }

    // ------------------------------------------------------------------ helpers

    static boolean within(double mx, double my, int x0, int y0, int x1, int y1) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    static float ease(float t) { return t * t * (3 - 2 * t); }

    static int withAlpha(int argb, int a) { return (a << 24) | (argb & 0xFFFFFF); }

    static int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16
                | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
    }

    /** A steady pseudo-random 0..1 per particle and salt. */
    private static double rnd(int i, int salt) {
        long h = (i * 0x9E3779B97F4A7C15L) ^ (salt * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53);
    }
}
