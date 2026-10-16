package com.butcher.gui.menu;

import com.butcher.ledger.Grade;
import com.butcher.ledger.Ledger;
import com.butcher.ledger.Stamp;

import java.util.List;
import java.util.Map;

/**
 * How a Ledger stamp looks, through {@link Draw} - shared by the book and the in-game pop-up, and drawable offline.
 *
 * An ink stamp, the way a butcher stamps a carcass "Inspected and Passed": an outer ring (the grade's colour on a
 * tiered stamp), a thin paper ring inside it, the ink with the stamp's own item in the middle, and the grade as pips
 * under it. Each stamp has its own item ({@link #ITEMS}) and one of six shapes ({@link Shape}), taken in turn along its
 * chapter so no two neighbours on a page match (the user: "so it doesnt get repetitive"). Real ink is never even, so a few pixels of each stamp are missing - the same ones every time, picked
 * by the stamp's id. Every stamp sits a little crooked, again by its id.
 */
public final class StampArt {
    private StampArt() {}

    /** The cream of the Ledger's pages, and the pencil drawn on them. */
    public static final int PAPER = 0xFFEDE3CA, PENCIL = 0xFF8C7B5E;

    /** The outline a stamp is cut to. Every row of each is one or two runs, so it fills a row at a time. */
    public enum Shape { CIRCLE, OCTAGON, SQUARE, SHIELD, DIAMOND, SCALLOP }

    /** The stamp's shape: the next one along for each stamp in its chapter, so a page never repeats side by side. */
    public static Shape shape(Stamp s) {
        List<Stamp> mates = Ledger.chapter(s.chapter());
        int i = Math.max(0, mates.indexOf(s));
        Shape[] all = Shape.values();
        return all[(i + s.chapter().ordinal() * 2) % all.length];
    }

    /** The stamp's own item: a vanilla item's registry path (GuiDraw knows them all); the chapter's if it has none. */
    public static String item(Stamp s) {
        return ITEMS.getOrDefault(s.id(), s.chapter().icon);
    }

    /** One item per stamp. Each must also be in GuiDraw's list, or it draws as paper. On 26.2 coloured items (wool,
     *  dyes) and copper ones (lightning rods...) are collections, not single items: wool is {@code WOOL.pick(colour)}
     *  in GuiDraw, and the copper family is avoided - one of them in the switch breaks GuiDraw's ItemStack. */
    static final Map<String, String> ITEMS = Map.ofEntries(
            Map.entry("house_open", "bell"), Map.entry("house_regular", "clock"),
            Map.entry("house_full_menu", "cake"), Map.entry("house_always_wins", "skeleton_skull"),
            Map.entry("house_hot_streak", "fire_charge"), Map.entry("house_tycoon", "gold_ingot"),
            Map.entry("house_places_to_be", "feather"), Map.entry("house_night_shift", "lantern"),
            Map.entry("house_jackpot_777", "emerald"), Map.entry("house_well_done", "cooked_beef"),
            Map.entry("house_collector", "item_frame"), Map.entry("house_big_ticket", "diamond"),

            Map.entry("scatha_worm_food", "rotten_flesh"), Map.entry("scatha_rare_cut", "lapis_lazuli"),
            Map.entry("scatha_choice_cut", "amethyst_shard"), Map.entry("scatha_prime_cut", "glowstone_dust"),
            Map.entry("scatha_full_set", "chest"), Map.entry("scatha_beginners_luck", "rabbit_foot"),
            Map.entry("scatha_dry_aged", "beef"), Map.entry("scatha_dry_aged_45", "bone"),
            Map.entry("scatha_slippery", "slime_ball"), Map.entry("scatha_back_to_back", "egg"),
            Map.entry("scatha_dozen", "bread"), Map.entry("scatha_cereal", "wheat"),

            Map.entry("vg_thawed_out", "ice"), Map.entry("vg_cold_hands", "packed_ice"),
            Map.entry("vg_heart_of_glass", "heart_of_the_sea"), Map.entry("vg_frostbitten", "snowball"),
            Map.entry("vg_ice_cold", "blue_ice"), Map.entry("vg_negative_equity", "redstone"),
            Map.entry("vg_in_the_black", "coal"), Map.entry("vg_skeleton_crew", "skeleton_skull"),
            Map.entry("vg_cold_storage", "barrel"), Map.entry("vg_will_o_wisp", "soul_torch"),
            Map.entry("vg_tough_skin", "shield"), Map.entry("vg_master_key", "tripwire_hook"),

            Map.entry("diana_heads", "golden_shovel"), Map.entry("diana_brain_food", "cookie"),
            Map.entry("diana_chimera", "enchanted_book"), Map.entry("diana_stinger", "spider_eye"),
            Map.entry("diana_manticore", "nether_star"), Map.entry("diana_crown", "golden_helmet"),
            Map.entry("diana_wool", "yellow_wool"), Map.entry("diana_relic", "compass"),
            Map.entry("diana_dye", "green_wool"), Map.entry("diana_pantheon", "totem_of_undying"),
            Map.entry("diana_shared_custody", "name_tag"), Map.entry("diana_inquisition", "golden_sword"),

            Map.entry("hellion_fire_watch", "blaze_rod"), Map.entry("hellion_double_down", "enchanted_book"),
            Map.entry("hellion_roll_dice", "bone"), Map.entry("hellion_high_roller", "gold_block"),
            Map.entry("hellion_playing_fire", "flint_and_steel"), Map.entry("hellion_ashes", "gunpowder"),
            Map.entry("hellion_power_trip", "redstone"), Map.entry("hellion_cold_feet", "powder_snow_bucket"),
            Map.entry("hellion_blueprints", "map"), Map.entry("hellion_paragon", "prismarine_crystals"),
            Map.entry("hellion_hellfire_club", "magma_cream"), Map.entry("hellion_seventh_circle", "nether_star"),

            Map.entry("lava_magma_maker", "magma_block"), Map.entry("lava_flash", "glowstone_dust"),
            Map.entry("lava_meltdown", "experience_bottle"), Map.entry("lava_end_times", "writable_book"),
            Map.entry("lava_kuudra", "red_wool"), Map.entry("lava_jaw_dropping", "iron_block"),
            Map.entry("lava_thermal_runaway", "lava_bucket"), Map.entry("lava_chained", "lead"),
            Map.entry("lava_pinch_me", "nautilus_shell"), Map.entry("lava_forged", "anvil"),
            Map.entry("lava_shell_shocked", "turtle_helmet"), Map.entry("lava_hot_catch", "fishing_rod"),

            Map.entry("fin_shark_week", "cod"), Map.entry("fin_big_fish", "tropical_fish"),
            Map.entry("fin_bigger_fish", "pufferfish"), Map.entry("fin_tooth_fairy", "prismarine_shard"),
            Map.entry("fin_off_the_hook", "fishing_rod"), Map.entry("fin_dentist", "bone_meal"),
            Map.entry("fin_school", "salmon"), Map.entry("fin_dry_dock", "oak_boat"),
            Map.entry("fin_feeding_frenzy", "kelp"), Map.entry("fin_both_ends", "nautilus_shell"),

            Map.entry("wisp_sorrowful", "ghast_tear"), Map.entry("wisp_plasma", "ender_eye"),
            Map.entry("wisp_spirit_level", "phantom_membrane"), Map.entry("wisp_drowning", "water_bucket"),
            Map.entry("wisp_high_voltage", "end_rod"), Map.entry("wisp_bittersweet", "honey_bottle"),
            Map.entry("wisp_double_haunting", "soul_lantern"), Map.entry("wisp_poltergeist", "echo_shard"),
            Map.entry("wisp_matched_pair", "glass_bottle"), Map.entry("wisp_witching_hour", "jack_o_lantern"));

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
        Shape sh = shape(s);
        d.push();
        d.translate(cx, cy);
        d.rotate(tilt);
        fill(d, sh, r, alpha(ring, a));
        fill(d, sh, r - 2, alpha(PAPER, a));
        fill(d, sh, r - 3, alpha(inkCol, Math.round(a * 0.92f)));
        outline(d, sh, r - 5, 1, alpha(PAPER, Math.round(a * 0.55f)));
        // The stamp's own item in the middle, as big as its shape allows.
        if (a > 200) {
            float room = sh == Shape.DIAMOND ? 0.82f : sh == Shape.SHIELD ? 0.9f : 1f;
            float k = Math.max(0.5f, (r * room - (s.tiered() ? 9 : 7)) / 9f);
            d.push();
            d.translate(0, s.tiered() ? -2 : sh == Shape.SHIELD ? -1 : 0);
            d.scale(k);
            d.item(item(s), -8, -8);
            d.pop();
        }
        // Worn ink: a few flecks of paper showing through, the same every time.
        for (int i = 0; i < r; i++) {
            double ang = hash(s.id(), 10 + i) * Math.PI * 2, rad = (0.35 + 0.6 * hash(s.id(), 40 + i)) * (r - 3);
            int x = (int) Math.round(Math.cos(ang) * rad), y = (int) Math.round(Math.sin(ang) * rad);
            if (Math.abs(x) < 7 && Math.abs(y) < 7) continue;
            if (!inside(sh, x, y, r - 4)) continue;
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
        // Its shape pencilled in, dashed: every edge pixel whose angle falls in an "on" stretch.
        Shape sh = shape(s);
        int dashes = Math.max(10, r * 2);
        for (int y = -r - 1; y <= r + 1; y++) {
            for (int x = -r - 1; x <= r + 1; x++) {
                if (!inside(sh, x, y, r) || inside(sh, x, y, r - 1)) continue;
                double ang = Math.atan2(y, x) + Math.PI;
                if ((int) (ang / (Math.PI * 2) * dashes) % 2 == 0) d.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, col);
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

    /** Whether (x, y), from the middle, is inside {@code sh} drawn at size {@code r} (y down). */
    static boolean inside(Shape sh, int x, int y, float r) {
        if (r <= 0) return false;
        float ax = Math.abs(x), ay = Math.abs(y);
        return switch (sh) {
            case CIRCLE -> x * x + y * y <= r * r + r * 0.8f;
            case OCTAGON -> ax <= r && ay <= r && ax + ay <= r * 1.38f;
            case SQUARE -> {
                float half = r * 0.9f, corner = r * 0.32f;
                float dx = Math.max(0, ax - (half - corner)), dy = Math.max(0, ay - (half - corner));
                yield ax <= half && ay <= half && dx * dx + dy * dy <= corner * corner + 0.5f;
            }
            case SHIELD -> {
                float top = -r * 0.88f, waist = r * 0.12f, half = r * 0.9f;
                if (y < top) yield false;
                if (y <= waist) yield ax <= half;
                float f = (y - waist) / (r - waist);
                yield f <= 1 && ax <= half * Math.pow(1 - f, 0.75);
            }
            case DIAMOND -> ax <= r && ay <= r && ax + ay <= r * 1.12f;
            case SCALLOP -> {
                double ang = Math.atan2(y, x), rr = r * (0.9 + 0.1 * Math.cos(12 * ang));
                yield x * x + y * y <= rr * rr + r * 0.6;
            }
        };
    }

    /** {@code sh} filled at size {@code r}, a row of runs at a time. */
    static void fill(Draw d, Shape sh, float r, int argb) {
        runs(d, sh, r, -1, argb);
    }

    /** {@code sh}'s rim, {@code t} thick, at size {@code r}. */
    static void outline(Draw d, Shape sh, float r, float t, int argb) {
        runs(d, sh, r, r - t, argb);
    }

    /** Fills the points inside {@code r} and outside {@code hole} (none if negative), merging each row into runs. */
    private static void runs(Draw d, Shape sh, float r, float hole, int argb) {
        int n = (int) Math.ceil(r) + 1;
        for (int y = -n; y <= n; y++) {
            int start = Integer.MIN_VALUE;
            for (int x = -n; x <= n + 1; x++) {
                boolean in = x <= n && inside(sh, x, y, r) && (hole < 0 || !inside(sh, x, y, hole));
                if (in && start == Integer.MIN_VALUE) start = x;
                if (!in && start != Integer.MIN_VALUE) {
                    d.fill(start, y, x, y + 1, argb);
                    start = Integer.MIN_VALUE;
                }
            }
        }
    }

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
