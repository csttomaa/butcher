package com.butcher.gui;

import com.butcher.ButcherClient;
import com.butcher.detect.vanguard.VanguardLoot;
import com.butcher.config.ButcherStats;
import com.butcher.config.PatternConfig;
import com.butcher.config.PatternConfig.AnimationMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The "/butcher" settings menu: a tab bar over a scrolling list of collapsible
 * sections.
 *
 * Everything is drawn and hit-tested by hand rather than built from vanilla
 * widgets - Button/Slider keep changing shape between Minecraft versions, and
 * this whole file only needs GuiGraphicsExtractor's fill/text/scissor
 * primitives, which are already confirmed working.
 *
 * The list is rebuilt from the config every frame ({@link #buildRows}) instead
 * of being held as widget state. That keeps the rows and the click targets from
 * ever drifting apart: the same method produces what you see and what you hit,
 * so a row that appears because a section was expanded is clickable on that
 * very frame. Sliders are the one thing that needs to survive a rebuild, so
 * they carry a stable id and a drag looks itself up again by that id.
 */
public class ButcherMenuScreen extends Screen {
    private static final int PANEL_W = 340;
    /** Tallest the frame is allowed to get; it shrinks on short screens (high GUI scale). */
    /**
     * Tallest the panel is allowed to get.
     *
     * Was 236, which on most screens meant scrolling almost immediately and every
     * section crowding the next. Taller costs nothing - the clamp below still keeps it
     * inside the window - and the settings stop feeling like a list squeezed through a
     * letterbox.
     */
    private static final int PANEL_H_MAX = 320;
    private static final int TAB_H = 22;
    private static final int PAD = 10;

    private static final String[] TAB_NAMES = {"Animations", "Sound", "General", "Stats"};
    private static final int TAB_COUNT = TAB_NAMES.length;
    private static final int TAB_GAMBLING = 0;
    private static final int TAB_SOUND = 1;
    private static final int TAB_GENERAL = 2;
    private static final int TAB_STATS = 3;

    // ---- row metrics ----
    private static final int HEADER_H = 20;
    private static final int ROW_H = 18;
    private static final int SECTION_H = 16;
    private static final int GAP = 4;
    private static final int LINE_H = 10;
    private static final int SCROLLBAR_W = 6;

    /**
     * How far either side of the bar still counts as grabbing it.
     *
     * The bar is six pixels wide and the thumb can be sixteen tall; at a high GUI scale
     * that is a small target for a click-and-drag, and this menu has to be usable by
     * someone whose scroll wheel does not work - which is the whole reason it is draggable.
     */
    private static final int SCROLLBAR_GRAB = 5;

    // ---- setting ranges ----
    private static final int SCOPE_MIN_MS = 3000, SCOPE_MAX_MS = 16000, SCOPE_STEP = 100;
    private static final int GUILL_MIN_MS = 800, GUILL_MAX_MS = 5000, GUILL_STEP = 50;
    private static final int HP_MIN = 1, HP_MAX = 10;

    /** How long the panel takes to fade and settle into place when the menu opens. */
    private static final long OPEN_MS = 190;
    private static final float OPEN_RISE = 10f;

    // ---- palette ----
    private static final int COL_BACKDROP = 0xB4000000;
    private static final int COL_PANEL = 0xF00C0E0C;
    private static final int COL_BORDER = 0xFF3A3F38;
    private static final int COL_ROW = 0xFF1B1E1B;
    private static final int COL_ROW_HOVER = 0xFF272B27;
    private static final int COL_SLIDER_FILL = 0xFF33463D;
    private static final int COL_GOLD = 0xFFE0A030;
    private static final int COL_GOLD_DIM = 0xFF8A6420;
    private static final int COL_SEL_BORDER = 0xFF7FB2F0;
    private static final int COL_SEL_FILL = 0xFF1A2734;
    private static final int COL_TEXT = 0xFFEDEDED;
    private static final int COL_TEXT_DIM = 0xFF9AA09A;
    /** The hairline after a section heading. Dim enough to organise without drawing the eye. */
    private static final int COL_RULE = 0xFF2E332E;
    /** The thin bar down the left of an open group, tying its rows together. */
    private static final int COL_ACCENT = 0xFF3E5A48;
    private static final int COL_TEXT_DISABLED = 0xFF5E645E;

    private int tab = TAB_GAMBLING;
    /**
     * Which collapsible groups are open, by id.
     *
     * A set rather than a boolean per group: there is one group today, four by the time
     * the Vanguard and sea creatures have their own, and a field each would have to be
     * threaded through the click routing individually. Empty means everything is closed,
     * which is the right first impression - the menu opens as a short list of headings
     * rather than a wall of settings.
     */
    private final java.util.Set<String> openGroups = new java.util.HashSet<>();

    private boolean isOpen(String id) { return openGroups.contains(id); }

    private float scroll = 0f;
    private int contentHeight = 0;

    private String draggingId = null;
    /** True while the scrollbar thumb is being dragged. */
    private boolean draggingBar = false;
    private final List<Placed> placed = new ArrayList<>();

    private final long openedAt = System.currentTimeMillis();

    public ButcherMenuScreen() {
        super(Component.literal("Butcher"));
    }

    @Override protected void init() { }
    @Override public boolean isPauseScreen() { return false; }

    /** 0 while the menu is still appearing, 1 once it has settled. */
    private float openT() {
        return ease(clamp01((System.currentTimeMillis() - openedAt) / (float) OPEN_MS));
    }

    private int panelX() { return (this.width - PANEL_W) / 2; }

    /** Leaves room for the title above and a margin below, so the frame always fits the window. */
    private int panelH() { return Math.max(120, Math.min(PANEL_H_MAX, this.height - 44)); }

    /** Includes the opening rise, so hit-testing and drawing always agree on where the panel is. */
    private int panelY() {
        return (this.height - panelH()) / 2 + Math.round((1f - openT()) * OPEN_RISE);
    }

    private int contentTop() { return panelY() + TAB_H; }
    private int contentBottom() { return panelY() + panelH(); }
    private int rowsLeft() { return panelX() + PAD; }
    private int rowsWidth() {
        int w = PANEL_W - PAD * 2;
        return contentHeight > viewHeight() ? w - SCROLLBAR_W - 3 : w;
    }
    private int viewHeight() { return panelH() - TAB_H - PAD * 2; }

    private int scrollbarX() { return panelX() + PANEL_W - PAD + 2; }

    private boolean scrollable() { return contentHeight > viewHeight(); }

    /**
     * Put the thumb's centre at {@code my}.
     *
     * Shared by the click and the drag so a click on the track jumps to where you clicked
     * and then keeps tracking the mouse from there, rather than jumping once and sticking.
     */
    private void scrollbarTo(double my) {
        int viewTop = contentTop() + PAD, viewH = viewHeight();
        int thumbH = Math.max(16, Math.round(viewH * (viewH / (float) contentHeight)));
        int travel = viewH - thumbH;
        if (travel <= 0) return;
        float f = (float) ((my - viewTop - thumbH / 2.0) / travel);
        scroll = clampScroll(Math.max(0f, Math.min(1f, f)) * (contentHeight - viewH), viewH);
    }

    // Minecraft 26.2 replaced the old immediate-mode Screen.render(...) with a
    // "render state extraction" pass: extractRenderState(...) is the new
    // per-frame draw hook, and extractBackground(...) is the separate hook that
    // used to paint the blurred menu background - overridden as a no-op since we
    // paint our own backdrop.
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) { }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        Font font = Minecraft.getInstance().font;
        float open = openT();

        g.fill(0, 0, this.width, this.height, scaleAlpha(COL_BACKDROP, open));

        int px = panelX(), py = panelY();

        // ---- title, sitting above the frame ----
        Component title = Component.literal("BUTCHER").withStyle(ChatFormatting.BOLD);
        g.text(font, title, px + (PANEL_W - font.width(title)) / 2, py - 24, COL_GOLD, true);

        // ---- tab bar ----
        int tabW = PANEL_W / TAB_COUNT;
        for (int i = 0; i < TAB_COUNT; i++) {
            int tx0 = px + i * tabW;
            int tx1 = (i == TAB_COUNT - 1) ? px + PANEL_W : tx0 + tabW;
            boolean active = i == tab;
            boolean hover = !active && within(mouseX, mouseY, tx0, py, tx1, py + TAB_H);

            if (active) {
                // the active tab is capped with a gold edge and left open at the
                // bottom, so it reads as one shape with the panel below it
                g.fill(tx0, py, tx1, py + 2, COL_GOLD);
                g.fill(tx0, py + 2, tx1, py + TAB_H, COL_PANEL);
                g.fill(tx0, py + 2, tx0 + 1, py + TAB_H, COL_BORDER);
                g.fill(tx1 - 1, py + 2, tx1, py + TAB_H, COL_BORDER);
            }
            Component label = Component.literal(TAB_NAMES[i]);
            int lw = font.width(label);
            g.text(font, label, tx0 + (tx1 - tx0 - lw) / 2, py + TAB_H / 2 - 4,
                    active ? COL_TEXT : (hover ? COL_TEXT_DIM : COL_TEXT_DISABLED), false);
        }

        // ---- content frame ----
        int cy0 = contentTop(), cy1 = contentBottom();
        g.fill(px, cy0, px + PANEL_W, cy1, COL_PANEL);
        g.fill(px, cy0, px + PANEL_W, cy0 + 1, COL_BORDER);
        g.fill(px, cy1 - 1, px + PANEL_W, cy1, COL_BORDER);
        g.fill(px, cy0, px + 1, cy1, COL_BORDER);
        g.fill(px + PANEL_W - 1, cy0, px + PANEL_W, cy1, COL_BORDER);

        // ---- rows ----
        layout(font);
        int viewTop = cy0 + PAD, viewH = viewHeight();
        scroll = clampScroll(scroll, viewH);

        g.enableScissor(px + 1, viewTop, px + PANEL_W - 1, viewTop + viewH);
        int x = rowsLeft(), w = rowsWidth();
        for (Placed p : placed) {
            int ry = viewTop + p.y() - Math.round(scroll);
            if (ry + p.row().height() < viewTop || ry > viewTop + viewH) continue;  // off-list, skip
            p.row().render(g, font, x, ry, w, mouseX, mouseY);
        }
        g.disableScissor();

        // ---- scrollbar ----
        if (contentHeight > viewH) {
            int barX = scrollbarX();
            boolean overBar = mouseX >= barX - SCROLLBAR_GRAB && mouseX <= barX + SCROLLBAR_W + SCROLLBAR_GRAB
                    && mouseY >= viewTop && mouseY <= viewTop + viewH;
            g.fill(barX, viewTop, barX + SCROLLBAR_W, viewTop + viewH, 0x40000000);
            int thumbH = Math.max(16, Math.round(viewH * (viewH / (float) contentHeight)));
            int travel = viewH - thumbH;
            int thumbY = viewTop + Math.round(travel * (scroll / (float) Math.max(1, contentHeight - viewH)));
            // Lit while hovered or held, so it reads as something you can take hold of
            // rather than a progress indicator.
            g.fill(barX, thumbY, barX + SCROLLBAR_W, thumbY + thumbH,
                    (draggingBar || overBar) ? 0xFFFFD87A : COL_GOLD);
        }

        // The fade itself. Everything above is drawn at full strength and then
        // veiled, because text colour can't be faded reliably - a styled
        // Component carries its own colour and ignores the one passed to
        // g.text(), so dimming the arguments would fade the panel and leave the
        // labels sitting there at full brightness.
        if (open < 1f) {
            int veil = Math.round(255 * (1f - open)) << 24;
            g.fill(px, py - 30, px + PANEL_W, cy1, veil);
        }
    }

    // ================================================================ row model

    private interface Row {
        int height();
        void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY);
        /** @return true if this row consumed the click. */
        default boolean click(double mx, double my, int x, int y, int w) { return false; }
        /** Sliders only: the stable id a drag re-finds itself by after the list is rebuilt. */
        default String dragId() { return null; }
        default void dragTo(double mx, int x, int w) { }
    }

    private record Placed(Row row, int y) { }

    private void layout(Font font) {
        placed.clear();
        int y = 0;
        for (Row r : buildRows(font)) {
            placed.add(new Placed(r, y));
            y += r.height() + GAP;
        }
        contentHeight = Math.max(0, y - GAP);
    }

    private List<Row> buildRows(Font font) {
        List<Row> rows = new ArrayList<>();
        PatternConfig cfg = ButcherClient.config;
        if (tab == TAB_SOUND) return soundRows(font, cfg);
        if (tab == TAB_GENERAL) return generalRows(font, cfg);
        if (tab == TAB_STATS) return statsRows(font);
        if (tab != TAB_GAMBLING) {
            rows.add(new NoticeRow("Nothing here yet.", font, rowsWidth()));
            return rows;
        }

        boolean on = cfg.animationMode != AnimationMode.NONE;
        rows.add(new HeaderRow("gambling", "Scatha - the cleaver", on, isOpen("gambling")));
        if (!isOpen("gambling")) return vanguardRows(rows, font, cfg);

        boolean cleaver = cfg.animationMode != AnimationMode.GUILLOTINE;

        rows.add(new SectionRow("The cutscene"));
        rows.add(new ToggleRow("The cleaver cutscene", on, () -> {
            if (cfg.animationMode == AnimationMode.NONE) {
                cfg.animationMode = cfg.lastStyle == AnimationMode.NONE ? AnimationMode.SCOPE : cfg.lastStyle;
            } else {
                cfg.lastStyle = cfg.animationMode;
                cfg.animationMode = AnimationMode.NONE;
            }
            cfg.save();
        }));
        // The guillotine style is deliberately not offered here.
        //
        // It still exists and still works - AnimationMode.GUILLOTINE, ButcherHudRenderer
        // and the whole HUD path are untouched - but it has had none of the work the
        // cleaver has, and putting a visibly worse option next to the good one only
        // invites someone to pick it once and judge the mod on it. Re-add this row to
        // bring it back; nothing else has to change.
        rows.add(new SectionRow("Pacing"));
        if (cleaver) {
            rows.add(new SliderRow("scope", "Full run", cfg.scopeDurationMs, SCOPE_MIN_MS, SCOPE_MAX_MS, SCOPE_STEP,
                    v -> { cfg.scopeDurationMs = v; }, ms -> String.format("%.2fs", ms / 1000f)));
            rows.add(new NoticeRow("A full five-cut run. Shorter runs finish sooner.", font, rowsWidth()));
        } else {
            rows.add(new SliderRow("guill", "Blade fall", cfg.animationDurationMs, GUILL_MIN_MS, GUILL_MAX_MS, GUILL_STEP,
                    v -> { cfg.animationDurationMs = v; }, ms -> String.format("%.2fs", ms / 1000f)));
            rows.add(new NoticeRow("Start to finish.", font, rowsWidth()));
        }

        rows.add(new SectionRow("Hiding the drop"));
        rows.add(new SliderRow("hp", "Hide below", cfg.hideBelowHp, HP_MIN, HP_MAX, 1,
                v -> { cfg.hideBelowHp = v; }, hp -> hp + " \u2764"));
        rows.add(new NoticeRow("Covers your hotbar and chat before the kill, so nothing spoils the drop.", font, rowsWidth()));

        rows.add(new SectionRow("Pet payoff"));
        rows.add(new ToggleRow("Firework", cfg.petDropFirework, () -> {
            cfg.petDropFirework = !cfg.petDropFirework;
            cfg.save();
        }));
        rows.add(new ToggleRow("Pet head popup", cfg.petHeadPopup, () -> {
            cfg.petHeadPopup = !cfg.petHeadPopup;
            cfg.save();
        }));
        rows.add(new NoticeRow("Both fire as the drawn head vanishes.", font, rowsWidth()));

        if (cleaver) {
            rows.add(new SectionRow("Getting away"));
            rows.add(new SliderRow("escape", "Miss chance", cfg.escapeChance, 0, 100, 5,
                    v -> { cfg.escapeChance = v; }, v -> v + "%"));
            rows.add(new NoticeRow("How often it ducks the last swing. Never on a pet.", font, rowsWidth()));

            rows.add(new SectionRow("The look"));
            rows.add(new SliderRow("vig", "Vignette", cfg.vignetteStrength, 0, 100, 5,
                    v -> { cfg.vignetteStrength = v; }, v -> v + "%"));
            rows.add(new SliderRow("zoom", "Zoom per cut", cfg.zoomStrength, 0, 200, 10,
                    v -> { cfg.zoomStrength = v; }, v -> v + "%"));
            rows.add(new SliderRow("shake", "Screen shake", cfg.shakeStrength, 0, 200, 10,
                    v -> { cfg.shakeStrength = v; }, v -> v + "%"));
            rows.add(new NoticeRow("These build as a run goes on. Set shake to 0 if it bothers you.", font, rowsWidth()));
        }

        return vanguardRows(rows, font, cfg);
    }

    /**
     * What the grind has actually produced.
     *
     * Deliberately leads with the dry streak rather than the totals. A Scatha pet is
     * about a one-in-two-thousand drop: the number anyone actually wants is "how long
     * has it been", and burying that under a list of counters gets it wrong.
     */
    /**
     * The Vanguard's group, under the Scatha's on the same tab.
     *
     * Deliberately short. The cutscene's feel - shake, vignette, volume, the achievement
     * sting - is shared with the Scatha's and set there, so this only holds what is
     * genuinely the Thaw's own.
     */
    private List<Row> vanguardRows(List<Row> rows, Font font, PatternConfig cfg) {
        rows.add(new HeaderRow("vanguard", "Vanguard Thaw", cfg.vanguardAnimation, isOpen("vanguard")));
        if (!isOpen("vanguard")) return dianaRows(rows, font, cfg);
        rows.add(new SectionRow("The cutscene"));
        rows.add(new ToggleRow("Vanguard Thaw", cfg.vanguardAnimation, () -> {
            cfg.vanguardAnimation = !cfg.vanguardAnimation;
            cfg.save();
        }));
        rows.add(new ToggleRow("Hide the loot message", cfg.vanguardHideChat, () -> {
            cfg.vanguardHideChat = !cfg.vanguardHideChat;
            cfg.save();
        }));
        rows.add(new NoticeRow("The haul goes to chat when the cutscene ends.", font, rowsWidth()));

        rows.add(new SectionRow("Pacing"));
        rows.add(new SliderRow("vg_speed", "Speed", cfg.vanguardSpeed, 50, 200, 10,
                v -> { cfg.vanguardSpeed = v; }, v -> v + "%"));
        rows.add(new ToggleRow("Quick Thaw", cfg.vanguardQuick, () -> {
            cfg.vanguardQuick = !cfg.vanguardQuick;
            cfg.save();
        }));
        rows.add(new NoticeRow("A corpse with nothing above common skips to its last strike. Anything better plays in full - so a Thaw that keeps going is a good one.",
                font, rowsWidth()));
        rows.add(new ToggleRow("Flare before each strike", cfg.vanguardFlare, () -> {
            cfg.vanguardFlare = !cfg.vanguardFlare;
            cfg.save();
        }));
        rows.add(new NoticeRow("Each flare is the true colour of its drop, and the last one climbs. Off makes every ending a surprise.",
                font, rowsWidth()));

        rows.add(new SectionRow("Look and sound"));
        rows.add(new SliderRow("vg_name", "Item name size", cfg.vanguardNameSize, 60, 160, 10,
                v -> { cfg.vanguardNameSize = v; }, v -> v + "%"));
        rows.add(new SliderRow("vg_finale", "Ending volume", cfg.vanguardFinaleVolume, 0, 150, 5,
                v -> { cfg.vanguardFinaleVolume = v; }, v -> v + "%"));
        rows.add(new NoticeRow("The legendary, Locket and Dye endings. Try them with /butcher test vanguard.",
                font, rowsWidth()));
        rows.add(new ToggleRow("Profit window after the Thaw", cfg.vanguardProfit, () -> {
            cfg.vanguardProfit = !cfg.vanguardProfit;
            cfg.save();
        }));
        return dianaRows(rows, font, cfg);
    }

    /**
     * Diana's cutscene, "The Chopping Block": the switch, which creatures play it, how its endings
     * look and sound. Detection waits on her real chat messages; the test command plays it now.
     */
    private List<Row> dianaRows(List<Row> rows, Font font, PatternConfig cfg) {
        rows.add(new HeaderRow("diana", "Diana - The Chopping Block", cfg.dianaAnimation, isOpen("diana")));
        // Each group is its own: a closed one must not hide the ones after it (it hid Fin and the slayers).
        if (!isOpen("diana")) return fishingRows(rows, font, cfg);
        rows.add(new SectionRow("The cutscene"));
        rows.add(new ToggleRow("The Chopping Block", cfg.dianaAnimation, () -> {
            cfg.dianaAnimation = !cfg.dianaAnimation;
            cfg.save();
        }));
        rows.add(new SliderRow("diana_hide", "Hide the HUD below", cfg.dianaHidePct, 0, 20, 1,
                v -> { cfg.dianaHidePct = v; }, v -> v == 0 ? "Off" : v + "% HP"));
        rows.add(new NoticeRow("The HUD and chat fade out when your creature (or one you're getting loot share on) gets this low, so nothing gives the drop away. Try the cutscene with /butcher test diana.",
                font, rowsWidth()));

        rows.add(new SectionRow("What plays it"));
        rows.add(dianaToggle("Sphinx kills", cfg.dianaSphinx, () -> cfg.dianaSphinx = !cfg.dianaSphinx, cfg));
        rows.add(dianaToggle("Minos Inquisitor kills", cfg.dianaInquisitor, () -> cfg.dianaInquisitor = !cfg.dianaInquisitor, cfg));
        rows.add(dianaToggle("Manticore kills", cfg.dianaManticore, () -> cfg.dianaManticore = !cfg.dianaManticore, cfg));
        rows.add(dianaToggle("King Minos kills", cfg.dianaKingMinos, () -> cfg.dianaKingMinos = !cfg.dianaKingMinos, cfg));
        rows.add(dianaToggle("Minos Relic (from a Champion)", cfg.dianaChampion, () -> cfg.dianaChampion = !cfg.dianaChampion, cfg));
        rows.add(dianaToggle("Mythological Dye (from anything)", cfg.dianaDye, () -> cfg.dianaDye = !cfg.dianaDye, cfg));

        rows.add(new SectionRow("The endings"));
        rows.add(dianaToggle("Every drop's own ending", cfg.dianaFullEndings, () -> cfg.dianaFullEndings = !cfg.dianaFullEndings, cfg));
        rows.add(new NoticeRow("Off gives every drop the same short ending: it rises out of the split.", font, rowsWidth()));
        rows.add(dianaToggle("RARE DROP! banner", cfg.dianaBanner, () -> cfg.dianaBanner = !cfg.dianaBanner, cfg));
        rows.add(dianaToggle("Show the price", cfg.dianaPrice, () -> cfg.dianaPrice = !cfg.dianaPrice, cfg));
        rows.add(new SliderRow("diana_size", "Size", cfg.dianaSize, 60, 140, 10,
                v -> { cfg.dianaSize = v; }, v -> v + "%"));

        rows.add(new SectionRow("Sound"));
        rows.add(dianaToggle("Music on the big drops", cfg.dianaMusic, () -> cfg.dianaMusic = !cfg.dianaMusic, cfg));
        rows.add(new SliderRow("diana_volume", "Volume", cfg.dianaVolume, 0, 150, 5,
                v -> { cfg.dianaVolume = v; }, v -> v + "%"));
        bustsRow(rows, font, cfg.dianaBusts, v -> cfg.dianaBusts = v, cfg);
        tryRows(rows, "diana", names(com.butcher.anim.ChopTimeline.Verdict.values()), font);
        return fishingRows(rows, font, cfg);
    }

    /** The slayers: one group, each boss its own sub-group under it. */
    private List<Row> slayerRows(List<Row> rows, Font font, PatternConfig cfg) {
        rows.add(new HeaderRow("slayers", "Slayers", null, isOpen("slayers")));
        if (!isOpen("slayers")) return rows;
        blazeRows(rows, font, cfg);
        seraphRows(rows, font, cfg);
        return rows;
    }

    /** The blaze slayer: Hellion on every kill of your own tier IV Inferno Demonlord. */
    private void blazeRows(List<Row> rows, Font font, PatternConfig cfg) {
        rows.add(new HeaderRow("blaze", "Inferno Demonlord - Hellion", cfg.blazeAnimation, isOpen("blaze"), SUB_INDENT));
        if (!isOpen("blaze")) return;
        rows.add(new SectionRow("The cutscene"));
        rows.add(dianaToggle("Hellion", cfg.blazeAnimation, () -> cfg.blazeAnimation = !cfg.blazeAnimation, cfg));
        rows.add(new NoticeRow("Plays on every kill of your own tier IV Demonlord, the moment it dies; the drop decides the ending. Try it with /butcher test slayer blaze.",
                font, rowsWidth()));
        rows.add(new SliderRow("blaze_hide", "Hide the HUD below", cfg.blazeHidePct, 0, 30, 1,
                v -> { cfg.blazeHidePct = v; }, v -> v == 0 ? "Off" : v + "% HP"));
        rows.add(new NoticeRow("The HUD and chat fade out when your Demonlord gets this low, so nothing gives the drop away.",
                font, rowsWidth()));
        rows.add(new SectionRow("The endings"));
        rows.add(dianaToggle("PRAY TO RNGESUS DROP! banner", cfg.blazeBanner, () -> cfg.blazeBanner = !cfg.blazeBanner, cfg));
        rows.add(dianaToggle("Show the price", cfg.blazePrice, () -> cfg.blazePrice = !cfg.blazePrice, cfg));
        rows.add(new SliderRow("blaze_volume", "Volume", cfg.blazeVolume, 0, 150, 5,
                v -> { cfg.blazeVolume = v; }, v -> v + "%"));
        bustsRow(rows, font, cfg.blazeBusts, v -> cfg.blazeBusts = v, cfg);
        tryRows(rows, "blaze", names(com.butcher.anim.HellionTimeline.Drop.values()), font);
    }

    /** The enderman slayer: the Seraph's Glyph. Detection waits on real records; the test command plays it now. */
    private void seraphRows(List<Row> rows, Font font, PatternConfig cfg) {
        rows.add(new HeaderRow("seraph", "Voidgloom Seraph - The Seraph's Glyph", cfg.seraphAnimation, isOpen("seraph"), SUB_INDENT));
        if (!isOpen("seraph")) return;
        rows.add(new SectionRow("The cutscene"));
        rows.add(dianaToggle("The Seraph's Glyph", cfg.seraphAnimation, () -> cfg.seraphAnimation = !cfg.seraphAnimation, cfg));
        rows.add(new NoticeRow("Plays on every kill of your own tier IV Seraph (the one spawned by you), the moment it dies; the drop decides the ending. Try it with /butcher test slayer enderman.",
                font, rowsWidth()));
        rows.add(new SliderRow("seraph_hide", "Hide the HUD below", cfg.seraphHidePct, 0, 30, 1,
                v -> { cfg.seraphHidePct = v; }, v -> v == 0 ? "Off" : v + "% HP"));
        rows.add(new SectionRow("The endings"));
        rows.add(dianaToggle("PRAY TO RNGESUS DROP! banner", cfg.seraphBanner, () -> cfg.seraphBanner = !cfg.seraphBanner, cfg));
        rows.add(dianaToggle("Show the price", cfg.seraphPrice, () -> cfg.seraphPrice = !cfg.seraphPrice, cfg));
        rows.add(new SliderRow("seraph_volume", "Volume", cfg.seraphVolume, 0, 150, 5,
                v -> { cfg.seraphVolume = v; }, v -> v + "%"));
        bustsRow(rows, font, cfg.seraphBusts, v -> cfg.seraphBusts = v, cfg);
        tryRows(rows, "seraph", names(com.butcher.anim.SeraphTimeline.Drop.values()), font);
    }

    /** The Fishing Festival: one short pop-up for the Great White Shark's two big drops, and its bust. */
    private List<Row> fishingRows(List<Row> rows, Font font, PatternConfig cfg) {
        rows.add(new HeaderRow("fishing", "Fishing Festival - Fin", cfg.fishingAnnounce, isOpen("fishing")));
        if (!isOpen("fishing")) return lavaRows(rows, font, cfg);
        rows.add(new SectionRow("The pop-up"));
        rows.add(dianaToggle("Announce a Megalodon or a Great White Shark Tooth", cfg.fishingAnnounce,
                () -> cfg.fishingAnnounce = !cfg.fishingAnnounce, cfg));
        rows.add(dianaToggle("The bust - a shark of yours dies with nothing", cfg.fishingBust,
                () -> cfg.fishingBust = !cfg.fishingBust, cfg));
        rows.add(dianaToggle("Show the price", cfg.fishingPrice, () -> cfg.fishingPrice = !cfg.fishingPrice, cfg));
        rows.add(new NoticeRow("The pets show what George pays for them. A quick pop-up over the game - nothing is hidden. Try it with /butcher test shark.",
                font, rowsWidth()));
        rows.add(new SliderRow("fishing_volume", "Volume", cfg.fishingVolume, 0, 150, 5,
                v -> { cfg.fishingVolume = v; }, v -> v + "%"));
        List<String> fin = new ArrayList<>(names(com.butcher.detect.SharkDrops.Drop.values()));
        fin.add(0, "BUST");
        tryRows(rows, "shark", fin, font);
        return lavaRows(rows, font, cfg);
    }

    /** Lava fishing: Eruption on the rare Crimson Isle creatures. Hands on to the slayers either way. */
    private List<Row> lavaRows(List<Row> rows, Font font, PatternConfig cfg) {
        rows.add(new HeaderRow("lava", "Lava Fishing - Eruption", cfg.lavaAnimation, isOpen("lava")));
        if (!isOpen("lava")) return slayerRows(rows, font, cfg);
        rows.add(new SectionRow("The cutscene"));
        rows.add(dianaToggle("Eruption", cfg.lavaAnimation, () -> cfg.lavaAnimation = !cfg.lavaAnimation, cfg));
        rows.add(new NoticeRow("Plays on every kill of your own Lord Jawbus, Thunder, Ragnarok or Fiery Scuttler, the moment it dies - the heat it lands on is the drop. Plhlegblast only plays for the Carmine Dye. Try it with /butcher test lava.",
                font, rowsWidth()));
        rows.add(dianaToggle("Lord Jawbus", cfg.lavaJawbus, () -> cfg.lavaJawbus = !cfg.lavaJawbus, cfg));
        rows.add(dianaToggle("Thunder", cfg.lavaThunder, () -> cfg.lavaThunder = !cfg.lavaThunder, cfg));
        rows.add(dianaToggle("Ragnarok", cfg.lavaRagnarok, () -> cfg.lavaRagnarok = !cfg.lavaRagnarok, cfg));
        rows.add(dianaToggle("Fiery Scuttler", cfg.lavaScuttler, () -> cfg.lavaScuttler = !cfg.lavaScuttler, cfg));
        rows.add(dianaToggle("Loot-shared kills", cfg.lavaLootShare, () -> cfg.lavaLootShare = !cfg.lavaLootShare, cfg));
        rows.add(new SliderRow("lava_hide", "Hide the HUD below", cfg.lavaHidePct, 0, 20, 1,
                v -> { cfg.lavaHidePct = v; }, v -> v == 0 ? "Off" : v + "% HP"));
        rows.add(new NoticeRow("The HUD and chat fade out when your creature gets this low, so nothing gives the drop away.",
                font, rowsWidth()));
        rows.add(new SectionRow("The endings"));
        rows.add(dianaToggle("RARE DROP! banner", cfg.lavaBanner, () -> cfg.lavaBanner = !cfg.lavaBanner, cfg));
        rows.add(dianaToggle("Show the price", cfg.lavaPrice, () -> cfg.lavaPrice = !cfg.lavaPrice, cfg));
        rows.add(new SliderRow("lava_volume", "Volume", cfg.lavaVolume, 0, 150, 5,
                v -> { cfg.lavaVolume = v; }, v -> v + "%"));
        bustsRow(rows, font, cfg.lavaBusts, v -> cfg.lavaBusts = v, cfg);
        tryRows(rows, "lava", names(com.butcher.anim.EruptionTimeline.Drop.values()), font);
        return slayerRows(rows, font, cfg);
    }

    /** "Busts": the whole cutscene, a short one (straight to the bust's ending), or none (wins only). */
    private void bustsRow(List<Row> rows, Font font, int value, java.util.function.IntConsumer set, PatternConfig cfg) {
        String[] styles = {"Whole cutscene", "Short", "None - wins only"};
        int v = Math.max(0, Math.min(2, value));
        rows.add(new ValueRow("Busts", styles[v], v != 0, () -> {
            set.accept((v + 1) % 3);
            cfg.save();
        }));
        if (v == 1) rows.add(new NoticeRow("A bust jumps to its own ending as soon as it is known - so one that keeps going is a win.", font, rowsWidth()));
        if (v == 2) rows.add(new NoticeRow("A kill opens nothing; only a win plays, a moment later, when its drop line comes.", font, rowsWidth()));
    }

    /** Every constant's name, for the "Try it" picker. */
    private static List<String> names(Enum<?>[] values) {
        List<String> out = new ArrayList<>();
        for (Enum<?> e : values) out.add(e.name());
        return out;
    }

    /** What each group's "Try it" will play, by group. */
    private static final java.util.Map<String, Integer> TRY = new java.util.HashMap<>();

    /** "Try it": pick an outcome, then play it as /butcher test does - never counted (the review, M6). */
    private void tryRows(List<Row> rows, String group, List<String> outcomes, Font font) {
        rows.add(new SectionRow("Try it"));
        int i = Math.floorMod(TRY.getOrDefault(group, 0), outcomes.size());
        String pick = outcomes.get(i);
        rows.add(new ValueRow("Outcome", pretty(pick), true, () -> TRY.put(group, i + 1)));
        rows.add(new ValueRow("Play it", "\u25B6 " + pretty(pick), true, () -> {
            if (minecraft != null) minecraft.gui.setScreen(null);
            ButcherClient.playTest(group, pick);
        }));
    }

    private static String pretty(String name) {
        String s = name.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private ToggleRow dianaToggle(String label, boolean value, Runnable flip, PatternConfig cfg) {
        return new ToggleRow(label, value, () -> {
            flip.run();
            cfg.save();
        });
    }

    /**
     * The stats tab: one collapsible group per drop source.
     *
     * Flat, every number was on screen at once and a Scatha dry streak sat three rows
     * above a corpse count it has nothing to do with. They are separate grinds with
     * separate odds - a pet is one in two thousand, a Vanguard legendary about one corpse
     * in fifteen - and reading them as one list makes both meaningless.
     */
    private List<Row> statsRows(Font font) {
        List<Row> rows = new ArrayList<>();
        ButcherStats st = ButcherStats.get();

        rows.add(new HeaderRow("stats-scatha", "Scatha", null, isOpen("stats-scatha")));
        if (isOpen("stats-scatha")) {
            scathaStats(rows, st);
            resetRow(rows, font, "stats-scatha", "Reset Scatha stats", st::resetScatha);
        }

        rows.add(new HeaderRow("stats-vanguard", "Vanguard Corpses", null, isOpen("stats-vanguard")));
        if (isOpen("stats-vanguard")) {
            vanguardStats(rows, st, font);
            resetRow(rows, font, "stats-vanguard", "Reset Vanguard stats", st::resetVanguard);
        }

        rows.add(new HeaderRow("stats-diana", "Diana", null, isOpen("stats-diana")));
        if (isOpen("stats-diana")) {
            dianaStats(rows, st, font);
            resetRow(rows, font, "stats-diana", "Reset Diana stats", st::resetDiana);
        }

        // The later sources (0.2.85): the same three things each - the dry streak, the drops, the kills.
        sourceGroup(rows, st, font, "blaze", "Hellion - Inferno Demonlord IV", "Bosses killed",
                titles(com.butcher.anim.HellionTimeline.Drop.values()));
        sourceGroup(rows, st, font, "seraph", "The Seraph - Voidgloom Seraph IV", "Bosses killed",
                titles(com.butcher.anim.SeraphTimeline.Drop.values()));
        sourceGroup(rows, st, font, "lava", "Eruption - lava fishing", "Rare creatures killed",
                titles(com.butcher.anim.EruptionTimeline.Drop.values()));
        sourceGroup(rows, st, font, "shark", "Fin - Great White Sharks", "Sharks killed",
                titles(com.butcher.detect.SharkDrops.Drop.values()));

        return rows;
    }

    /** Every drop's display name, rarest first as the enum lists them, the bust left out. */
    private static String[] titles(Enum<?>[] drops) {
        List<String> out = new ArrayList<>();
        for (Enum<?> d : drops) {
            try {
                String t = (String) d.getClass().getField("title").get(d);
                if (t != null && !t.isEmpty() && !out.contains(t)) out.add(t);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        java.util.Collections.reverse(out);
        return out.toArray(new String[0]);
    }

    private void sourceGroup(List<Row> rows, ButcherStats st, Font font, String key, String title, String killWord, String[] drops) {
        String id = "stats-" + key;
        rows.add(new HeaderRow(id, title, null, isOpen(id)));
        if (!isOpen(id)) return;
        ButcherStats.Source s = st.source(key);
        rows.add(new SectionRow("The dry streak"));
        rows.add(new StatRow("Kills since a drop", Integer.toString(s.dry), COL_GOLD));
        rows.add(new StatRow("Worst it has been", Integer.toString(s.worstDry), COL_TEXT_DIM));
        rows.add(new SectionRow("Drops"));
        for (String d : drops) rows.add(new StatRow(d, Integer.toString(s.drops.getOrDefault(d, 0)), COL_TEXT));
        rows.add(new SectionRow("Everything else"));
        rows.add(new StatRow(killWord, String.format("%,d", s.kills), COL_TEXT_DIM));
        if (s.kills == 0) rows.add(new NoticeRow("Nothing yet. Only real kills are counted; tests never are.", font, rowsWidth()));
        resetRow(rows, font, id, "Reset these stats", () -> st.resetSource(key));
    }

    private void scathaStats(List<Row> rows, ButcherStats st) {
        rows.add(new SectionRow("The dry streak"));
        rows.add(new StatRow("Kills since a pet", Integer.toString(st.dryStreak), COL_GOLD));
        rows.add(new StatRow("Worst it has been", Integer.toString(st.worstDryStreak), COL_TEXT_DIM));
        if (st.kills > 0) {
            rows.add(new StatRow("Pets per kill", st.pets() == 0
                    ? "none yet" : String.format("1 in %,d", st.kills / Math.max(1, st.pets())),
                    COL_TEXT_DIM));
        }

        rows.add(new SectionRow("Pets"));
        rows.add(new StatRow("Legendary", Integer.toString(st.petsLegendary), 0xFFFFAA00));
        rows.add(new StatRow("Epic", Integer.toString(st.petsEpic), 0xFFAA00AA));
        rows.add(new StatRow("Rare", Integer.toString(st.petsRare), 0xFF5555FF));

        rows.add(new SectionRow("Everything else"));
        rows.add(new StatRow("Scathas killed", String.format("%,d", st.kills), COL_TEXT));
        rows.add(new StatRow("Cutscenes played", String.format("%,d", st.cutscenes), COL_TEXT_DIM));
        rows.add(new StatRow("Gemstones", String.format("%,d", st.gemstones), COL_TEXT_DIM));
        rows.add(new StatRow("Dwarven O's", String.format("%,d", st.dwarvenOs), COL_TEXT_DIM));
        rows.add(new StatRow("Got away", String.format("%,d", st.misses), COL_TEXT_DIM));
        rows.add(new StatRow("Cuts landed", String.format("%,d", st.chops), COL_TEXT_DIM));
    }

    private void vanguardStats(List<Row> rows, ButcherStats st, Font font) {
        rows.add(new SectionRow("The dry streak"));
        rows.add(new StatRow("Corpses since a legendary", Integer.toString(st.corpseDryStreak), COL_GOLD));
        rows.add(new StatRow("Worst it has been", Integer.toString(st.worstCorpseDryStreak), COL_TEXT_DIM));
        // The table says roughly one corpse in fifteen carries a legendary, so showing the
        // expectation next to the streak is what turns a number into "am I due".
        rows.add(new StatRow("Expected", String.format("1 in %.0f",
                1f / VanguardLoot.legendaryChance(VanguardLoot.AVG_ROLLS)), COL_TEXT_DIM));

        rows.add(new SectionRow("Drops"));
        // Always shown, even at zero. A counter you have never moved is the point of this
        // one - it is the Frostbitten Dye, and most players will never move it.
        rows.add(new StatRow("Frostbitten Dye", Integer.toString(st.corpseSpecials),
                VanguardLoot.Tier.SPECIAL.glow()));
        rows.add(new StatRow("Legendary", Integer.toString(st.corpseLegendaries), 0xFFFFAA00));
        rows.add(new StatRow("Uncommon", Integer.toString(st.corpseUncommons), 0xFF55FFFF));
        rows.add(new StatRow("Common", Integer.toString(st.corpseCommons), COL_TEXT_DIM));

        rows.add(new SectionRow("Everything else"));
        rows.add(new StatRow("Corpses opened", String.format("%,d", st.corpses), COL_TEXT));
        rows.add(new StatRow("Rolls seen", String.format("%,d", st.corpseRolls), COL_TEXT_DIM));
        if (st.corpses > 0) {
            rows.add(new StatRow("Rolls per corpse",
                    String.format("%.2f", st.corpseRolls / (float) st.corpses), COL_TEXT_DIM));
        }
        if (st.corpses == 0) {
            rows.add(new NoticeRow("Nothing yet. Open a Vanguard corpse, or try /butcher test vanguard random.",
                    font, rowsWidth()));
        }
    }

    /** Diana's own numbers: the dry streak, kills by creature, drops by name - most valuable first. */
    private void dianaStats(List<Row> rows, ButcherStats st, Font font) {
        rows.add(new SectionRow("The dry streak"));
        rows.add(new StatRow("Kills since a drop", Integer.toString(st.dianaDryStreak), COL_GOLD));
        rows.add(new StatRow("Worst it has been", Integer.toString(st.worstDianaDryStreak), COL_TEXT_DIM));

        rows.add(new SectionRow("Drops"));
        // Always shown, even at zero: most of these are one in hundreds.
        String[][] drops = {
                {"Shimmering Wool", "FFAA00"}, {"Manti-core", "FFAA00"}, {"Minos Relic", "AA00AA"}, {"Chimera I", "FF55FF"},
                {"Mythological Dye", "FFAA00"}, {"Fateful Stinger", "FFAA00"}, {"Crown of Greed", "FFAA00"}, {"Brain Food", "AA00AA"}};
        for (String[] d : drops) {
            rows.add(new StatRow(d[0], Integer.toString(st.dianaDropCount(d[0])), 0xFF000000 | Integer.parseInt(d[1], 16)));
        }

        rows.add(new SectionRow("Kills"));
        String[][] kills = {{"SPHINX", "Sphinxes"}, {"INQUISITOR", "Minos Inquisitors"}, {"MANTICORE", "Manticores"},
                {"KING_MINOS", "King Minos"}, {"CHAMPION", "Minos Champions (Relic)"}};
        int total = 0;
        for (String[] k : kills) {
            int n = st.dianaKillCount(k[0]);
            total += n;
            rows.add(new StatRow(k[1], String.format("%,d", n), COL_TEXT_DIM));
        }
        if (total == 0) {
            rows.add(new NoticeRow("Nothing yet. Only real kills are counted; tests never are.",
                    font, rowsWidth()));
        }
    }

    /**
     * Which reset is armed, and when it armed.
     *
     * Two clicks to wipe a set of stats, so a misclick cannot throw away a long streak -
     * and the armed state expires, so a menu left open on "click again to confirm" cannot
     * still be waiting when someone comes back to it an hour later and clicks to scroll.
     */
    private String resetArmed;
    private long resetArmedAt;

    /** How long a "click again to confirm" stays live. */
    private static final long RESET_ARM_MS = 4000;

    private boolean isArmed(String id) {
        if (!id.equals(resetArmed)) return false;
        if (System.currentTimeMillis() - resetArmedAt > RESET_ARM_MS) {
            resetArmed = null;
            return false;
        }
        return true;
    }

    /**
     * The reset row that sits at the bottom of each source's own stats.
     *
     * One per section rather than a single global wipe: the Scatha dry streak and the
     * corpse dry streak are separate grinds, and someone starting a fresh corpse count has
     * no reason to lose two thousand Scatha kills with it.
     */
    private void resetRow(List<Row> rows, Font font, String id, String label, Runnable wipe) {
        rows.add(new SectionRow("Start again"));
        boolean armed = isArmed(id);
        rows.add(new ToggleRow(armed ? "Click again to confirm" : label, armed, () -> {
            if (isArmed(id)) {
                wipe.run();
                resetArmed = null;
            } else {
                resetArmed = id;
                resetArmedAt = System.currentTimeMillis();
            }
        }));
    }

    /** Everything about what the cutscene sounds like. */
    private List<Row> soundRows(Font font, PatternConfig cfg) {
        List<Row> rows = new ArrayList<>();
        rows.add(new SectionRow("Levels"));
        rows.add(new SliderRow("vol", "Cutscene volume", cfg.cutsceneVolume, 0, 150, 5,
                v -> { cfg.cutsceneVolume = v; }, v -> v + "%"));
        rows.add(new NoticeRow("On top of Minecraft's master volume.", font, rowsWidth()));

        rows.add(new SectionRow("Each animation"));
        rows.add(new SliderRow("snd_vanguard", "Vanguard Thaw (its endings)", cfg.vanguardFinaleVolume, 0, 150, 5,
                v -> { cfg.vanguardFinaleVolume = v; }, v -> v + "%"));
        rows.add(new SliderRow("snd_diana", "Diana - The Chopping Block", cfg.dianaVolume, 0, 150, 5,
                v -> { cfg.dianaVolume = v; }, v -> v + "%"));
        rows.add(new SliderRow("snd_lava", "Lava fishing - Eruption", cfg.lavaVolume, 0, 150, 5,
                v -> { cfg.lavaVolume = v; }, v -> v + "%"));
        rows.add(new SliderRow("snd_blaze", "Inferno Demonlord - Hellion", cfg.blazeVolume, 0, 150, 5,
                v -> { cfg.blazeVolume = v; }, v -> v + "%"));
        rows.add(new SliderRow("snd_seraph", "Voidgloom Seraph", cfg.seraphVolume, 0, 150, 5,
                v -> { cfg.seraphVolume = v; }, v -> v + "%"));
        rows.add(new SliderRow("snd_fishing", "Fishing Festival - Fin", cfg.fishingVolume, 0, 150, 5,
                v -> { cfg.fishingVolume = v; }, v -> v + "%"));
        rows.add(new NoticeRow("The same sliders as in each animation's own group. The Scatha plays at the cutscene volume above.", font, rowsWidth()));

        rows.add(new SectionRow("Switches"));
        rows.add(new ToggleRow("Cutscene sound", cfg.cutsceneAudio, () -> {
            cfg.cutsceneAudio = !cfg.cutsceneAudio;
            cfg.save();
        }));
        rows.add(new ToggleRow("Silence the game", cfg.silenceGameAudio, () -> {
            cfg.silenceGameAudio = !cfg.silenceGameAudio;
            cfg.save();
        }));
        rows.add(new ToggleRow("Pet fanfare", cfg.petFanfare, () -> {
            cfg.petFanfare = !cfg.petFanfare;
            cfg.save();
        }));
        if (cfg.petFanfare) {
            rows.add(new SliderRow("fanfare", "Fanfare volume", cfg.petFanfareVolume, 0, 150, 5,
                    v -> { cfg.petFanfareVolume = v; }, v -> v + "%"));
        }
        rows.add(new NoticeRow("Every sound is made as the cutscene plays. The game stays quiet so nothing spoils the reveal.", font, rowsWidth()));
        return rows;
    }

    /** The bits that are not about one cutscene in particular. */
    private List<Row> generalRows(Font font, PatternConfig cfg) {
        List<Row> rows = new ArrayList<>();
        rows.add(new SectionRow("Hiding the HUD"));
        rows.add(new ToggleRow("Take the HUD away", cfg.hideHud, () -> {
            cfg.hideHud = !cfg.hideHud;
            cfg.save();
        }));
        rows.add(new NoticeRow("Hides the HUD and chat just before any tracked kill - a Scatha, Diana's creatures, your slayer boss, your lava creature - and for the whole cutscene.", font, rowsWidth()));
        rows.add(new ToggleRow("Keep health and hotbar", cfg.keepVitals, () -> {
            cfg.keepVitals = !cfg.keepVitals;
            cfg.save();
        }));
        rows.add(new NoticeRow("Your hotbar, hearts, hunger, armour and XP stay on screen, over the cutscene, so a fight is never blind.", font, rowsWidth()));
        rows.add(new NoticeRow("Hold Backspace to skip a cutscene: a win jumps to its showcase, a bust ends. Rebind it in Controls, under Butcher.", font, rowsWidth()));

        rows.add(new SectionRow("Effects"));
        rows.add(new ToggleRow("Calmer effects", cfg.calmEffects, () -> {
            cfg.calmEffects = !cfg.calmEffects;
            cfg.save();
        }));
        rows.add(new NoticeRow("No screen shake and softer flashes, in every animation.", font, rowsWidth()));
        rows.add(new ToggleRow("Chat summary", cfg.chatSummary, () -> {
            cfg.chatSummary = !cfg.chatSummary;
            cfg.save();
        }));
        rows.add(new NoticeRow("One line in chat as each cutscene ends: what dropped, its price, which one of yours it is.", font, rowsWidth()));

        rows.add(new SectionRow("Prices"));
        rows.add(new ToggleRow("Live prices", cfg.livePrices, () -> {
            cfg.livePrices = !cfg.livePrices;
            cfg.save();
        }));
        rows.add(new NoticeRow("Bazaar prices from api.hypixel.net, auction prices from sky.coflnet.com. Off hides every price and the Vanguard profit window.",
                font, rowsWidth()));
        long[] worth = PatternConfig.MIN_WORTH;
        rows.add(new SliderRow("min_worth", "Only play drops worth at least", cfg.minWorth, 0, worth.length - 1, 1,
                v -> { cfg.minWorth = v; }, v -> v == 0 ? "Any" : com.butcher.market.CorpseValue.coins(worth[v])));
        rows.add(new NoticeRow("A win worth less plays as a bust - Diana, the slayers and lava fishing. A price not fetched yet always plays.", font, rowsWidth()));

        rows.add(new SectionRow("Gameplay window"));
        String[] corners = {"Off", "Top right", "Top left", "Bottom right", "Bottom left"};
        int corner = Math.max(0, Math.min(4, cfg.gameplayCorner));
        rows.add(new ValueRow("During a cutscene", corners[corner], corner != 0, () -> {
            cfg.gameplayCorner = (cfg.gameplayCorner + 1) % corners.length;
            cfg.save();
        }));
        rows.add(new NoticeRow("The game, live, in a corner while a cutscene plays - so you can see what is around you. Click to move it or turn it off.", font, rowsWidth()));

        rows.add(new SectionRow("Drop records"));
        rows.add(new ToggleRow("Record drop events", cfg.recordDrops, () -> {
            cfg.recordDrops = !cfg.recordDrops;
            cfg.save();
        }));
        rows.add(new NoticeRow("Writes every real kill, corpse and Diana chat line to config/butcher-drops.jsonl, so a rare drop that goes wrong can be replayed. Stays on your computer. /butcher records shows the count.", font, rowsWidth()));

        rows.add(new SectionRow("Art"));
        rows.add(new ToggleRow("Write art to config", cfg.writeGeneratedArt, () -> {
            cfg.writeGeneratedArt = !cfg.writeGeneratedArt;
            cfg.save();
        }));
        rows.add(new NoticeRow("Writes the art to config/ so you can edit it. Your edits are never overwritten.", font, rowsWidth()));

        rows.add(new SectionRow("Skins and models"));
        rows.add(new NoticeRow("Drop head.png or body.png in config/butcher/skins/, or a .gltf in models/.", font, rowsWidth()));
        rows.add(new NoticeRow("/butcher log, skins, reload.", font, rowsWidth()));

        resetRow(rows, font, "settings", "Reset all settings", ButcherClient::resetSettings);
        rows.add(new NoticeRow("Every setting back to its default. Your stats stay.", font, rowsWidth()));
        return rows;
    }

    // ---------------------------------------------------------------- row kinds

    /** The collapsible bar. Its ON/OFF is a readout, not a switch - clicking anywhere opens it. */
    /** How far a sub-group's header sits in from its parent's (the slayers' bosses). */
    private static final int SUB_INDENT = 14;

    private static final class HeaderRow implements Row {
        private final String id, label; private final Boolean on; private final boolean open;
        private final int indent;
        /** @param on ON/OFF readout, or null for a group that is not a feature toggle. */
        HeaderRow(String id, String label, Boolean on, boolean open) {
            this(id, label, on, open, 0);
        }

        /** A sub-group's header, set in by {@code indent}. */
        HeaderRow(String id, String label, Boolean on, boolean open, int indent) {
            this.id = id; this.label = label; this.on = on; this.open = open; this.indent = indent;
        }
        String id() { return id; }

        @Override public int height() { return HEADER_H; }

        @Override
        public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY) {
            x += indent;
            w -= indent;
            boolean hover = within(mouseX, mouseY, x, y, x + w, y + height());
            g.fill(x, y, x + w, y + height(), open ? COL_SEL_FILL : (hover ? COL_ROW_HOVER : COL_ROW));
            if (open) g.outline(x, y, w, height(), COL_SEL_BORDER);

            Component c = on == null
                    ? Component.literal(label)
                    : Component.literal(label + "  ").append(Component.literal(on ? "ON" : "OFF")
                            .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
            int tw = font.width(c);
            int cx = x + w / 2;
            g.text(font, c, cx - tw / 2, y + height() / 2 - 4, COL_TEXT, false);
            marker(g, cx - tw / 2 - 9, y + height() / 2 - 2, open);
        }

        @Override
        public boolean click(double mx, double my, int x, int y, int w) {
            return within(mx, my, x + indent, y, x + w, y + height());
        }
    }

    /** Gold group label, no bar - just a heading between blocks of rows. */
    /**
     * A section heading: a short gold accent, the label, then a rule to the right edge.
     *
     * The rule is what turns a list of rows into a page. Without it every section
     * header was just another line of text and the settings read as one long column
     * with occasional gold in it.
     */
    private record SectionRow(String label) implements Row {
        @Override public int height() { return SECTION_H + 2; }
        @Override
        public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY) {
            int ty = y + height() - 9;
            // A stub of gold to the left of the label, like a bullet with intent.
            g.fill(x + 1, ty, x + 3, ty + 7, COL_GOLD);
            Component c = Component.literal(label);
            g.text(font, c, x + 8, ty, COL_GOLD, false);
            int ruleX = x + 8 + font.width(c) + 6;
            int ruleY = ty + 3;
            if (ruleX < x + w - 2) g.fill(ruleX, ruleY, x + w - 2, ruleY + 1, COL_RULE);
        }
    }

    /** Wrapped grey explanation, no bar. Height is fixed at build time from the wrap. */
    private static final class NoticeRow implements Row {
        private final List<String> lines;
        NoticeRow(String text, Font font, int width) { this.lines = wrap(font, text, width - 4); }
        @Override public int height() { return lines.size() * LINE_H; }
        @Override
        public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY) {
            int ly = y;
            for (String line : lines) {
                g.text(font, Component.literal(line), x + 2, ly, COL_TEXT_DIM, false);
                ly += LINE_H;
            }
        }
    }

    /** "Label: ON" - the whole bar is the switch. */
    private static final class ToggleRow implements Row {
        private final String label; private final boolean on; private final Runnable toggle;
        ToggleRow(String label, boolean on, Runnable toggle) { this.label = label; this.on = on; this.toggle = toggle; }
        @Override public int height() { return ROW_H; }

        @Override
        public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY) {
            boolean hover = within(mouseX, mouseY, x, y, x + w, y + height());
            g.fill(x, y, x + w, y + height(), hover ? COL_ROW_HOVER : COL_ROW);
            Component c = Component.literal(label + ": ")
                    .append(Component.literal(on ? "ON" : "OFF")
                            .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
            g.text(font, c, x + w / 2 - font.width(c) / 2, y + height() / 2 - 4, COL_TEXT, false);
        }

        @Override
        public boolean click(double mx, double my, int x, int y, int w) {
            if (!within(mx, my, x, y, x + w, y + height())) return false;
            toggle.run();
            return true;
        }
    }

    /**
     * A read-only "label ............ value" line.
     *
     * Right-aligned values so the numbers form a column the eye can run down, which is
     * the whole reason to have a stats page rather than a paragraph.
     */
    private static final class StatRow implements Row {
        private final String label, value;
        private final int colour;
        StatRow(String label, String value, int colour) {
            this.label = label; this.value = value; this.colour = colour;
        }
        @Override public int height() { return ROW_H - 3; }

        @Override
        public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY) {
            boolean hover = within(mouseX, mouseY, x, y, x + w, y + height());
            g.fill(x, y, x + w, y + height(), hover ? COL_ROW_HOVER : COL_ROW);
            Component l = Component.literal(label);
            Component v = Component.literal(value);
            int ty = y + height() / 2 - 4;
            g.text(font, l, x + 6, ty, COL_TEXT_DIM, false);
            g.text(font, v, x + w - 6 - font.width(v), ty, colour, false);
        }
    }

    /** "Label: Value" - clicking cycles to the next value. */
    private static final class ValueRow implements Row {
        private final String label, value; private final boolean lit; private final Runnable cycle;
        ValueRow(String label, String value, boolean lit, Runnable cycle) {
            this.label = label; this.value = value; this.lit = lit; this.cycle = cycle;
        }
        @Override public int height() { return ROW_H; }

        @Override
        public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY) {
            boolean hover = within(mouseX, mouseY, x, y, x + w, y + height());
            g.fill(x, y, x + w, y + height(), hover ? COL_ROW_HOVER : COL_ROW);
            Component c = Component.literal(label + ": ")
                    .append(Component.literal(value).withStyle(lit ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
            g.text(font, c, x + w / 2 - font.width(c) / 2, y + height() / 2 - 4, COL_TEXT, false);
        }

        @Override
        public boolean click(double mx, double my, int x, int y, int w) {
            if (!within(mx, my, x, y, x + w, y + height())) return false;
            cycle.run();
            return true;
        }
    }

    /** A bar that fills from the left. Click or drag anywhere along it to set the value. */
    private static final class SliderRow implements Row {
        private final String id, label, text;
        private final int value, min, max, step;
        private final java.util.function.IntConsumer setter;

        SliderRow(String id, String label, int value, int min, int max, int step,
                  java.util.function.IntConsumer setter, java.util.function.IntFunction<String> format) {
            this.id = id; this.label = label;
            this.value = Math.max(min, Math.min(max, value));
            this.min = min; this.max = max; this.step = step;
            this.setter = setter;
            this.text = format.apply(this.value);
        }

        @Override public int height() { return ROW_H; }
        @Override public String dragId() { return id; }

        @Override
        public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY) {
            boolean hover = within(mouseX, mouseY, x, y, x + w, y + height());
            g.fill(x, y, x + w, y + height(), hover ? COL_ROW_HOVER : COL_ROW);
            float t = (value - min) / (float) (max - min);
            int fillW = Math.round(w * t);
            if (fillW > 0) g.fill(x, y, x + fillW, y + height(), COL_SLIDER_FILL);
            if (fillW > 1) g.fill(x + fillW - 1, y, x + fillW, y + height(), COL_GOLD_DIM);

            Component c = Component.literal(label + ": ")
                    .append(Component.literal(text).withStyle(ChatFormatting.YELLOW));
            g.text(font, c, x + w / 2 - font.width(c) / 2, y + height() / 2 - 4, COL_TEXT, false);
        }

        @Override
        public boolean click(double mx, double my, int x, int y, int w) {
            if (!within(mx, my, x, y, x + w, y + height())) return false;
            dragTo(mx, x, w);
            return true;
        }

        @Override
        public void dragTo(double mx, int x, int w) {
            float t = clamp01((float) ((mx - x) / (double) w));
            int v = Math.round(min + t * (max - min));
            setter.accept(Math.max(min, Math.min(max, Math.round(v / (float) step) * step)));
        }
    }

    /** The little gold triangle in front of a header: pointing right when shut, down when open. */
    private static void marker(GuiGraphicsExtractor g, int x, int y, boolean open) {
        if (open) {
            g.fill(x - 2, y, x + 4, y + 1, COL_GOLD);
            g.fill(x - 1, y + 1, x + 3, y + 2, COL_GOLD);
            g.fill(x, y + 2, x + 2, y + 3, COL_GOLD);
        } else {
            g.fill(x, y - 3, x + 1, y + 4, COL_GOLD);
            g.fill(x + 1, y - 2, x + 2, y + 3, COL_GOLD);
            g.fill(x + 2, y - 1, x + 3, y + 2, COL_GOLD);
            g.fill(x + 3, y, x + 4, y + 1, COL_GOLD);
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        double mx = event.x(), my = event.y();
        int px = panelX(), py = panelY();

        int tabW = PANEL_W / TAB_COUNT;
        if (my >= py && my < py + TAB_H && mx >= px && mx < px + PANEL_W) {
            int i = Math.min(TAB_COUNT - 1, (int) ((mx - px) / tabW));
            if (i != tab) { tab = i; scroll = 0f; }
            return true;
        }

        int viewTop = contentTop() + PAD, viewH = viewHeight();

        // The bar first: it overlaps the right-hand end of the rows, and a click there
        // means the bar, not the row behind it.
        int barX = scrollbarX();
        if (scrollable() && mx >= barX - SCROLLBAR_GRAB && mx <= barX + SCROLLBAR_W + SCROLLBAR_GRAB
                && my >= viewTop && my <= viewTop + viewH) {
            draggingBar = true;
            scrollbarTo(my);
            return true;
        }

        if (!within(mx, my, px, viewTop, px + PANEL_W, viewTop + viewH)) {
            return super.mouseClicked(event, doubleClick);
        }

        int x = rowsLeft(), w = rowsWidth();
        for (Placed p : placed) {
            int ry = viewTop + p.y() - Math.round(scroll);
            if (!p.row().click(mx, my, x, ry, w)) continue;
            if (p.row() instanceof HeaderRow header) {
                if (!openGroups.remove(header.id())) openGroups.add(header.id());
                // Scrolled-down content that has just collapsed leaves the view showing
                // nothing at all, so a close always returns to the top.
                if (!isOpen(header.id())) scroll = 0f;
            } else if (p.row().dragId() != null) {
                draggingId = p.row().dragId();
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingBar && event.button() == 0) {
            scrollbarTo(event.y());
            return true;
        }
        if (draggingId != null && event.button() == 0) {
            int x = rowsLeft(), w = rowsWidth();
            for (Placed p : placed) {
                if (draggingId.equals(p.row().dragId())) { p.row().dragTo(event.x(), x, w); return true; }
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingBar && event.button() == 0) {
            draggingBar = false;
            return true;
        }
        if (draggingId != null && event.button() == 0) {
            draggingId = null;
            ButcherClient.config.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int viewH = viewHeight();
        if (contentHeight > viewH) {
            scroll = clampScroll(scroll - (float) scrollY * 14f, viewH);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private float clampScroll(float v, int viewH) {
        return Math.max(0f, Math.min(Math.max(0, contentHeight - viewH), v));
    }

    // ---------------------------------------------------------------- helpers

    /** Greedy word wrap at a pixel width. */
    private static List<String> wrap(Font font, String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = cur.isEmpty() ? word : cur + " " + word;
            if (cur.isEmpty() || font.width(Component.literal(candidate)) <= width) {
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

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float ease(float t) { return t * t * (3 - 2 * t); }

    private static int scaleAlpha(int argb, float factor) {
        int a = Math.round(((argb >>> 24) & 0xFF) * clamp01(factor));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static boolean within(double mx, double my, int x0, int y0, int x1, int y1) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }
}
