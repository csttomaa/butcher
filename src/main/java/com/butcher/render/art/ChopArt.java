package com.butcher.render.art;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The textures of "The Chopping Block", Diana's cutscene, drawn at runtime like the rest of
 * {@code render/art/}: a 128 x 128 sheet (the block, the cut flesh, the moon's faces, runes), a
 * glow, rays and a ring, and a 64 x 64 skin per head.
 *
 * <h2>The heads</h2>
 *
 * The creatures' own heads are real skins (the NEU repo's bestiary; see HeadSkins.CREATURES).
 * Chimera's three - lion, goat, serpent - have none anywhere, so they are painted here as
 * Minecraft heads on the standard skin layout - top (8,0), bottom (16,0), right (0,8), front
 * (8,8), left (16,8), back (24,8) - from eight-by-eight character maps.
 *
 * Painted bright, as the Labyrinth's were: the light is a tint that can only darken.
 */
public final class ChopArt {
    private ChopArt() {}

    public static final String TEX_SHEET = "chop", TEX_GLOW = "chop_glow", TEX_RAYS = "chop_rays", TEX_RING = "chop_ring",
            TEX_SPLAT = "chop_splat", TEX_DROP = "chop_drop", TEX_VIGNETTE = "chop_vignette", TEX_YARN = "chop_yarn",
            TEX_WING = "chop_wing",
            /** The game's own golden armour layer (64 x 32), adopted, never bundled: the Crown of Greed is a golden helmet. */
            TEX_ARMOR = "chop_gold_armor",
            /** Liquid as pixel art: a drop, and a splat for the board - hard edges, greyscale, to be tinted. */
            TEX_PIXDROP = "chop_pixdrop", TEX_PIXSPLAT = "chop_pixsplat",
            /** The enchantment glint, made from the game's own texture on the player's machine (see glint()). */
            TEX_GLINT = "chop_glint";

    /** The enchanting table's glyph particles - the game's own sga_a..sga_z, adopted by name. */
    public static String glyphTex(int letter) {
        return "chop_sga_" + (char) ('a' + letter);
    }
    public static final int SHEET = 128;

    public static final int[] BLOCK_TOP = {0, 0, 16, 16}, BLOCK_SIDE = {16, 0, 16, 16}, FLESH = {32, 0, 16, 16},
            WHITE = {48, 0, 8, 8}, BRAIN_CUT = {56, 0, 8, 8};
    /** The Fateful Stinger's tail: a plate's side and its end, the barb's bulb and its spike. */
    public static final int[] CHITIN = {64, 0, 16, 16}, CHITIN_CAP = {80, 0, 16, 16}, BULB = {96, 0, 16, 16},
            SPIKE = {112, 0, 16, 16};
    /** The Manti-core's heart: cells of different creatures mashed together, glowing seams between. */
    public static final int[] CELLS = {32, 32, 16, 16};
    /** A bat's wing, root at the top left; and the same mirrored, root at the top right. */
    public static final int[] WING_R = {0, 0, 64, 64}, WING_L = {64, 0, 64, 64};
    /** Diana's moon as a head: six 16px faces, and a hat layer drawn over them. */
    public static final int[][] MOON_FACES = {
            {0, 16, 16, 16}, {16, 16, 16, 16}, {32, 16, 16, 16},
            {48, 16, 16, 16}, {64, 16, 16, 16}, {80, 16, 16, 16}};
    public static final int[] MOON_HAT = {96, 16, 16, 16};

    /** One of sixteen 8px runes, white on clear, for tinting. */
    public static int[] rune(int i) {
        return new int[]{(i & 3) * 8, 32 + (i >> 2 & 3) * 8, 8, 8};
    }

    // The Mythos palette, bright.
    private static final int GOLD_HI = 0xFFE68A;

    public static BufferedImage sheet() {
        BufferedImage img = Paint.image(SHEET, SHEET);
        Random rng = new Random(0xC40B);
        blockTop(img, BLOCK_TOP, rng);
        blockSide(img, BLOCK_SIDE, rng);
        flesh(img, FLESH, rng);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) put(img, WHITE, x, y, 0xFFFFFF);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            // A brain in section: pink folds.
            int c = ((x + y * 2) % 5 < 2) ? 0xC75A78 : 0xF09AB2;
            put(img, BRAIN_CUT, x, y, jitter(c, rng, 8));
        }
        for (int f = 0; f < 6; f++) moonFace(img, MOON_FACES[f], new Random(0xD1A4AL + f * 131L), f);
        moonHat(img, MOON_HAT);
        for (int i = 0; i < 16; i++) rune(img, rune(i), new Random(0xB00C + i * 17L));
        chitin(img, CHITIN, new Random(0x5717));
        chitinCap(img, CHITIN_CAP, new Random(0x5718));
        bulb(img, BULB, new Random(0x5719));
        spike(img, SPIKE);
        cells(img, CELLS, new Random(0xCE11));
        return img;
    }

    /** The block's top: oak end grain - rings round a dark heart - scored with old cuts. */
    private static void blockTop(BufferedImage img, int[] r, Random rng) {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            float dx = x - 7.5f, dy = y - 7.5f, d = (float) Math.sqrt(dx * dx + dy * dy);
            int ring = (int) (d * 1.3f) % 2;
            int c = ring == 0 ? 0xC99A62 : 0xB08250;
            if (d < 1.6f) c = 0x7A5230;
            if (x == 0 || y == 0 || x == 15 || y == 15) c = 0x6A4A2A;
            put(img, r, x, y, jitter(c, rng, 6));
        }
        // Old cleaver scores, and a dark stain.
        for (int k = 0; k < 4; k++) {
            int x0 = 2 + rng.nextInt(9), y0 = 2 + rng.nextInt(12);
            for (int i = 0; i < 4 + rng.nextInt(3); i++) if (x0 + i < 15) put(img, r, x0 + i, y0, 0x5A3A1E);
        }
        for (int k = 0; k < 7; k++) put(img, r, 9 + rng.nextInt(4), 9 + rng.nextInt(4), 0x6A2A3A);
    }

    /** The block's side: rough bark in vertical ridges. */
    private static void blockSide(BufferedImage img, int[] r, Random rng) {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int c = (x + (y / 5)) % 3 == 0 ? 0x4A331E : 0x6B4A2C;
            if (y == 0) c = 0x8A6440;
            put(img, r, x, y, jitter(c, rng, 8));
        }
    }

    /** Cut flesh: dark plum meat, glistening, a lighter rim - Mythos ichor rather than red. */
    private static void flesh(BufferedImage img, int[] r, Random rng) {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            boolean rim = x == 0 || y == 0 || x == 15 || y == 15;
            int c = rim ? 0xB0507A : ((x * 5 + y * 3) % 7 == 0 ? 0xE08AB0 : 0x7A2A52);
            put(img, r, x, y, jitter(c, rng, 10));
        }
    }

    // ================================================================== heads

    /** A painted head's texture name. */
    public static String headTex(String beast) {
        return "chop_head_" + beast.toLowerCase(java.util.Locale.ROOT);
    }

    public static final String[] BEASTS = {"LION", "GOAT", "SERPENT"};

    public static BufferedImage head(String creature) {
        // Only Chimera's three are painted; see the class note.
        BufferedImage img = Paint.image(64, 64);
        Map<Character, Integer> pal = new HashMap<>();
        String[] front, side, back, top, bottom, hatFront = null, hatSide = null, hatTop = null;
        switch (creature) {
            case "LION" -> {
                pal.put('m', 0xE0A030); pal.put('M', 0xA86A18); pal.put('f', 0xF0C060); pal.put('k', 0x1A1420);
                pal.put('n', 0x5A3A1A); pal.put('o', 0x3A1010); pal.put('w', 0xF4EFE6);
                front = new String[]{"mMmMMmMm", "MmffffmM", "mfkffkfm", "Mffnnffm", "mfoooofM", "Mfwoowfm", "mMffffMm", "MmMmmMmM"};
                side = new String[]{"mMmMMmMm", "MmMmmMmM", "mMmfmMmM", "MmffffmM", "mMffffMm", "MmMffmMm", "mMmMMmMm", "MmMmmMmM"};
                back = new String[]{"mMmMMmMm", "MmMmmMmM", "mMmMMmMm", "MmMmmMmM", "mMmMMmMm", "MmMmmMmM", "mMmMMmMm", "MmMmmMmM"};
                top = back;
                bottom = fill('f');
            }
            case "GOAT" -> {
                pal.put('w', 0xF2EEE6); pal.put('W', 0xC8C2B6); pal.put('h', 0x8A7A62); pal.put('k', 0x1A1420);
                pal.put('y', 0xE8C860); pal.put('n', 0xB8A8A0); pal.put('b', 0xD8D2C6);
                front = new String[]{"hwwwwwwh", "wwwwwwww", "wyk..kyw".replace('.', 'w'), "wwwwwwww", "wWwnnwWw", "wwwnnwww", "wwbbbbww", "wwwbbwww"};
                side = new String[]{"hwwwwwww", "wwwwwwww", "wwWwwwww", "wwwwwWww", "wwwwwwww", "wWwwwwww", "wwwwwwWw", "WWWWWWWW"};
                back = side;
                top = new String[]{"hwwwwwwh", "hwwwwwwh", "wwwwwwww", "wwwwwwww", "wwwwwwww", "wwwwwwww", "wwwwwwww", "wwwwwwww"};
                bottom = fill('W');
            }
            default -> { // SERPENT
                pal.put('g', 0x3FD2A0); pal.put('G', 0x1E8A64); pal.put('y', 0xFFE14A); pal.put('k', 0x10140F);
                pal.put('o', 0x2A0A14); pal.put('w', 0xF4EFE6); pal.put('p', 0xC8003C);
                front = new String[]{"gGgGgGgG", "GgGgGgGg", "gykggkyg", "GggggggG", "gGoooogG", "gwoppowg", "GgoooogG", "gGgGgGgG"};
                side = new String[]{"gGgGgGgG", "GgGgGgGg", "gGgGgGgG", "GgGgGgGg", "gGgGgGgG", "GgGgGgGg", "gGgGgGgG", "GgGgGgGg"};
                back = side;
                top = side;
                bottom = fill('G');
            }
        }
        Random rng = new Random(creature.hashCode());
        face(img, 8, 8, front, pal, rng);
        face(img, 0, 8, side, pal, rng);
        face(img, 16, 8, mirror(side), pal, rng);
        face(img, 24, 8, back, pal, rng);
        face(img, 8, 0, top, pal, rng);
        face(img, 16, 0, bottom, pal, rng);
        if (hatFront != null) face(img, 40, 8, hatFront, pal, rng);
        if (hatSide != null) face(img, 32, 8, hatSide, pal, rng);
        if (hatTop != null) face(img, 40, 0, hatTop, pal, rng);
        return img;
    }

    private static String[] fill(char c) {
        String row = String.valueOf(c).repeat(8);
        return new String[]{row, row, row, row, row, row, row, row};
    }

    private static String[] mirror(String[] rows) {
        String[] out = new String[rows.length];
        for (int i = 0; i < rows.length; i++) out[i] = new StringBuilder(rows[i]).reverse().toString();
        return out;
    }

    /** Paints eight rows of eight characters at (u, v); '.' is left clear. A little jitter so flats are not flat. */
    private static void face(BufferedImage img, int u, int v, String[] rows, Map<Character, Integer> pal, Random rng) {
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            char ch = rows[y].charAt(x);
            if (ch == '.') continue;
            Integer c = pal.get(ch);
            if (c == null) continue;
            img.setRGB(u + x, v + y, 0xFF000000 | jitter(c, rng, 5));
        }
    }

    // ================================================================== moon, runes, glows (from the Labyrinth)

    private static void moonFace(BufferedImage img, int[] r, Random rng, int face) {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            float ex = Math.abs(x - 7.5f) / 7.5f, ey = Math.abs(y - 7.5f) / 7.5f;
            float edge = Math.max(ex, ey);
            int c = jitter(0xECEFF7, rng, 5);
            c = mix(c, 0xC4CBDD, Math.max(0f, edge - 0.55f) * 1.3f);
            put(img, r, x, y, c);
        }
        int craters = 2 + rng.nextInt(3);
        for (int k = 0; k < craters; k++) {
            float cx = 2.5f + rng.nextFloat() * 11f, cy = 2.5f + rng.nextFloat() * 11f;
            float rad = 1.2f + rng.nextFloat() * (k == 0 ? 2.4f : 1.4f);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy, d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < rad - 0.6f) put(img, r, x, y, mix(0xB4BCD2, 0x9EA7C0, 1f - d / rad));
                else if (d < rad + 0.4f) put(img, r, x, y, (dx + dy) > 0 ? 0xFAFBFE : 0xA8B1C8);
            }
        }
        if (face % 2 == 0) {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                float dx = x - 5f, dy = y - 9f;
                if (dx * dx / 18f + dy * dy / 8f < 1f && rng.nextInt(3) > 0) {
                    int p = img.getRGB(r[0] + x, r[1] + y);
                    put(img, r, x, y, mix(p & 0xFFFFFF, 0xC7CEE0, 0.5f));
                }
            }
        }
    }

    private static void moonHat(BufferedImage img, int[] r) {
        Random rng = new Random(0x4A7);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            boolean rim = x == 0 || y == 0 || x == 15 || y == 15;
            int a = 0, c = 0;
            if (rim && rng.nextInt(3) > 0) { a = 200; c = 0xE4ECFF; }
            else if (!rim && rng.nextInt(40) == 0) { a = 255; c = GOLD_HI; }
            img.setRGB(r[0] + x, r[1] + y, a << 24 | c);
        }
    }

    /** A rune: a stem and two to four strokes, white on clear. Made up here, nothing borrowed. */
    private static void rune(BufferedImage img, int[] r, Random rng) {
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) img.setRGB(r[0] + x, r[1] + y, 0);
        int stem = 1 + rng.nextInt(6);
        for (int y = 1; y < 7; y++) put(img, r, stem, y, 0xFFFFFF);
        int strokes = 2 + rng.nextInt(3);
        for (int k = 0; k < strokes; k++) {
            int y = 1 + rng.nextInt(6), x0 = 1 + rng.nextInt(3), x1 = x0 + 2 + rng.nextInt(3);
            if (rng.nextBoolean()) {
                for (int x = x0; x <= Math.min(6, x1); x++) put(img, r, x, y, 0xFFFFFF);
            } else {
                for (int d = 0; d < 3; d++) put(img, r, Math.min(6, x0 + d), Math.min(6, y + d), 0xFFFFFF);
            }
        }
    }

    /**
     * A glow in pixel steps: 40 texels across, drawn nearest, its alpha in eight bands. It was a smooth 128-texel blur,
     * the one round soft thing next to pixel-art heads and blocks in every animation (the review, A12).
     */
    public static BufferedImage glow() {
        int s = 40;
        BufferedImage img = Paint.image(s, s);
        float c = s / 2f;
        for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) {
            float dx = (x + 0.5f - c) / c, dy = (y + 0.5f - c) / c;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float a = (float) Math.pow(Math.max(0f, 1f - d), 2.2);
            // Eight steps, rounded to the nearest: the faintest fringe goes, so a big glow is not a grey disc,
            // and the rings of a big one are not hard bands.
            a = Math.round(a * 8f) / 8f;
            img.setRGB(x, y, Math.round(Math.min(1f, a) * 255) << 24 | 0xFFFFFF);
        }
        return img;
    }

    /** The ring, at pixel resolution like the glow (40 texels, nearest): it was the only smooth circle on screen. */
    public static BufferedImage ring() {
        int s = 40;
        BufferedImage img = Paint.image(s, s);
        float c = s / 2f;
        for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) {
            float dx = (x + 0.5f - c) / c, dy = (y + 0.5f - c) / c;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float off = (d - 0.86f) / 0.08f;
            float a = (float) Math.exp(-off * off) + Math.max(0f, 0.86f - d) * 0.12f;
            if (d > 1f) a = 0f;
            img.setRGB(x, y, Math.round(Math.min(1f, a) * 255) << 24 | 0xFFFFFF);
        }
        return img;
    }

    public static BufferedImage rays() {
        int s = 128;
        BufferedImage img = Paint.image(s, s);
        float c = s / 2f;
        for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) {
            float dx = (x + 0.5f - c) / c, dy = (y + 0.5f - c) / c;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d >= 1f) continue;
            double ang = Math.atan2(dy, dx);
            float ray = (float) Math.pow(Math.abs(Math.cos(ang * 6)), 14);
            float thin = (float) Math.pow(Math.abs(Math.cos(ang * 6 + Math.PI / 2)), 30) * 0.5f;
            float a = Math.max(ray, thin) * (1f - d) * (1f - d) + Math.max(0f, 1f - d * 3.5f) * 0.6f;
            img.setRGB(x, y, Math.round(Math.min(1f, a) * 255) << 24 | 0xFFFFFF);
        }
        return img;
    }

    // ------------------------------------------------------------------ the Fateful Stinger

    // The Fateful Stinger's own maroon (its NEU skin), lit from above.
    private static final int CH_RIM = 0xB0503F, CH_BASE = 0x742A26, CH_MOTTLE = 0x5E1F1F, CH_SHINE = 0xC0685A, CH_DARK = 0x3A1212;

    /**
     * A tail plate's side: v runs from the tip end (0) to the base end (15). A lit rim where it
     * overlaps the next plate, mottled maroon with a shine running down it, dark where it tucks in.
     */
    private static void chitin(BufferedImage img, int[] r, Random rng) {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int c;
            if (y <= 1) c = CH_RIM;
            else if (y >= 13) c = mix(CH_BASE, CH_DARK, (y - 12) / 3f);
            else c = rng.nextInt(5) == 0 ? CH_MOTTLE : CH_BASE;
            if (y >= 2 && y <= 12 && (x == 4 || x == 5)) c = mix(c, CH_SHINE, 0.75f);
            if (y >= 2 && y <= 12 && (x == 11)) c = mix(c, CH_SHINE, 0.3f);
            if (x == 0 || x == 15) c = mix(c, CH_DARK, 0.45f);
            put(img, r, x, y, jitter(c, rng, 5));
        }
    }

    /** A plate's end: dark, in rings, a lit edge. */
    private static void chitinCap(BufferedImage img, int[] r, Random rng) {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            float d = Math.max(Math.abs(x - 7.5f), Math.abs(y - 7.5f));
            int c = d > 6.5f ? CH_RIM : ((int) d % 3 == 0 ? CH_BASE : CH_DARK);
            put(img, r, x, y, jitter(c, rng, 5));
        }
    }

    /** The barb's bulb: glossy maroon, the venom glands showing through it in green. */
    private static void bulb(BufferedImage img, int[] r, Random rng) {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            float dx = x - 7.5f;
            int c = mix(CH_SHINE, CH_BASE, Math.abs(dx - 2f) / 6f);
            if (y >= 14) c = CH_DARK;
            put(img, r, x, y, jitter(c, rng, 4));
        }
        // Glands: two soft green streaks running down it.
        for (int y = 3; y < 13; y++) {
            for (int gx : new int[]{5, 10}) {
                int x = gx + (int) Math.round(Math.sin(y * 0.9 + gx) * 0.7);
                put(img, r, x, y, 0x2FA070);
                if (y % 3 == 0) put(img, r, x + 1, y, 0x6FE0A8);
            }
        }
    }

    /** The spike: v 0 is the point, black; it pales to bone at the root. */
    private static void spike(BufferedImage img, int[] r) {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            float u = y / 15f;
            int c = mix(0x140C0C, 0xEADFC4, (float) Math.pow(u, 0.7));
            if (x < 3 || x > 12) c = mix(c, 0x3A2A22, 0.35f);
            if (x == 6 || x == 7) c = mix(c, 0xFFFFFF, 0.25f * u);
            put(img, r, x, y, c);
        }
    }

    /**
     * The heart's cells: a patchwork of the creatures the Manti-core was made from - the lion's
     * gold, the bat's dark, the scorpion's maroon, tan hide - stitched with glowing viridian seams.
     */
    private static void cells(BufferedImage img, int[] r, Random rng) {
        int n = 7;
        float[][] seeds = new float[n][];
        int[] palette = {0xC89A3A, 0x2C2A34, 0x7A2A26, 0x9A7448, 0xB8862E, 0x3E3440, 0x8E3A2E};
        for (int i = 0; i < n; i++) seeds[i] = new float[]{rng.nextFloat() * 16f, rng.nextFloat() * 16f};
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            float best = 1e9f, second = 1e9f;
            int who = 0;
            for (int i = 0; i < n; i++) {
                float dx = Math.min(Math.abs(x + 0.5f - seeds[i][0]), 16f - Math.abs(x + 0.5f - seeds[i][0]));
                float dy = Math.min(Math.abs(y + 0.5f - seeds[i][1]), 16f - Math.abs(y + 0.5f - seeds[i][1]));
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < best) { second = best; best = d; who = i; }
                else if (d < second) second = d;
            }
            int c = second - best < 0.9f ? 0x7CFFC8 : jitter(palette[who], rng, 10);
            if (second - best >= 0.9f && second - best < 1.6f) c = mix(c, 0x3A8C6E, 0.5f);
            put(img, r, x, y, c);
        }
    }

    /**
     * A bat's wing, the Manticore's: a leading-edge arm from the root at the top left out to the
     * tip, finger bones fanning from the wrist, a dark leathery membrane scalloped between them,
     * viridian veins. The right half of the texture is the same wing mirrored.
     */
    public static BufferedImage wing() {
        BufferedImage img = Paint.image(128, 64);
        java.awt.Graphics2D g = Paint.graphics(img);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        float[] root = {2, 6}, wrist = {26, 9}, tip = {62, 4};
        float[][] fingers = {{61, 22}, {52, 44}, {36, 59}, {16, 62}};
        float[] hip = {2, 44};
        java.awt.geom.Path2D.Float m = new java.awt.geom.Path2D.Float();
        m.moveTo(root[0], root[1]);
        m.lineTo(wrist[0], wrist[1] - 1);
        m.lineTo(tip[0], tip[1]);
        float[] prev = tip;
        for (float[] f : fingers) {
            // A scalloped trailing edge: each span bows in towards the wrist.
            float mx = (prev[0] + f[0]) / 2f, my = (prev[1] + f[1]) / 2f;
            float cx = mx + (wrist[0] - mx) * 0.28f, cy = my + (wrist[1] - my) * 0.28f;
            m.quadTo(cx, cy, f[0], f[1]);
            prev = f;
        }
        float mx = (prev[0] + hip[0]) / 2f, my = (prev[1] + hip[1]) / 2f;
        m.quadTo(mx + (wrist[0] - mx) * 0.25f, my + (wrist[1] - my) * 0.25f, hip[0], hip[1]);
        m.closePath();
        g.setPaint(new java.awt.GradientPaint(wrist[0], wrist[1], new java.awt.Color(0x2A3A33), 50, 55, new java.awt.Color(0x16201C)));
        g.fill(m);
        g.setClip(m);
        g.setStroke(new java.awt.BasicStroke(0.8f));
        g.setColor(new java.awt.Color(0x2E7A5C));
        Random rng = new Random(0xB47);
        for (float[] f : fingers) {
            for (int k = 0; k < 3; k++) {
                float s = 0.3f + rng.nextFloat() * 0.5f;
                float ax = wrist[0] + (f[0] - wrist[0]) * s, ay = wrist[1] + (f[1] - wrist[1]) * s;
                g.draw(new java.awt.geom.Line2D.Float(ax, ay, ax + (rng.nextFloat() - 0.3f) * 12f, ay + (rng.nextFloat() - 0.2f) * 10f));
            }
        }
        g.setClip(null);
        // Bones: the arm and the fingers, dark, lit along one side.
        g.setStroke(new java.awt.BasicStroke(2.6f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.setColor(new java.awt.Color(0x0C1210));
        g.draw(new java.awt.geom.Line2D.Float(root[0], root[1], wrist[0], wrist[1]));
        g.draw(new java.awt.geom.Line2D.Float(wrist[0], wrist[1], tip[0], tip[1]));
        g.setStroke(new java.awt.BasicStroke(1.7f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        for (float[] f : fingers) g.draw(new java.awt.geom.Line2D.Float(wrist[0], wrist[1], f[0], f[1]));
        g.setStroke(new java.awt.BasicStroke(0.8f));
        g.setColor(new java.awt.Color(0x5C8A76));
        g.draw(new java.awt.geom.Line2D.Float(root[0], root[1] - 0.8f, wrist[0], wrist[1] - 0.8f));
        g.draw(new java.awt.geom.Line2D.Float(wrist[0], wrist[1] - 0.8f, tip[0], tip[1] - 0.8f));
        // The claw at the wrist.
        g.setColor(new java.awt.Color(0xD8CBB0));
        g.fill(new java.awt.geom.Ellipse2D.Float(wrist[0] - 1.5f, wrist[1] - 4f, 3f, 4f));
        g.dispose();
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) img.setRGB(127 - x, y, img.getRGB(x, y));
        return img;
    }

    /** A splash of liquid: an irregular blob and a spray of droplets off it, white, to be tinted. */
    public static BufferedImage splat() {
        int s = 64;
        BufferedImage img = Paint.image(s, s);
        Random rng = new Random(0x5EA7);
        float[] lobes = new float[9];
        for (int i = 0; i < lobes.length; i++) lobes[i] = 0.75f + rng.nextFloat() * 0.5f;
        float[][] drops = new float[10][];
        for (int i = 0; i < drops.length; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            float d = 0.55f + rng.nextFloat() * 0.35f;
            drops[i] = new float[]{(float) Math.cos(a) * d, (float) Math.sin(a) * d, 0.03f + rng.nextFloat() * 0.07f};
        }
        for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) {
            float dx = (x + 0.5f) / s * 2f - 1f, dy = (y + 0.5f) / s * 2f - 1f;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            double a = Math.atan2(dy, dx);
            float f = (float) ((a + Math.PI) / (Math.PI * 2) * lobes.length);
            int i0 = (int) f % lobes.length, i1 = (i0 + 1) % lobes.length;
            float fr = f - (int) f;
            float edge = 0.42f * (lobes[i0] + (lobes[i1] - lobes[i0]) * (fr * fr * (3 - 2 * fr)));
            float cov = clamp01((edge - d) * s * 0.5f + 0.5f);
            for (float[] dr : drops) {
                float ex = dx - dr[0], ey = dy - dr[1];
                cov = Math.max(cov, clamp01((dr[2] - (float) Math.sqrt(ex * ex + ey * ey)) * s * 0.5f + 0.5f));
            }
            // A touch of depth: the middle a little brighter than the rim.
            float shade = 0.82f + 0.18f * clamp01(1f - d / Math.max(0.01f, edge));
            int v = Math.round(255 * shade);
            img.setRGB(x, y, Math.round(cov * 255) << 24 | v << 16 | v << 8 | v);
        }
        return img;
    }

    /** A drop of liquid, lit from the upper left, white to grey, to be tinted; its shine is drawn apart. */
    public static BufferedImage drop() {
        int s = 32;
        BufferedImage img = Paint.image(s, s);
        for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) {
            float dx = (x + 0.5f) / s * 2f - 1f, dy = (y + 0.5f) / s * 2f - 1f;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float cov = clamp01((0.92f - d) * s * 0.5f + 0.5f);
            float lit = clamp01(0.55f + 0.45f * (-(dx + dy) * 0.6f + (1f - d)));
            float rim = clamp01((d - 0.7f) / 0.22f);
            float v = lit * (1f - 0.35f * rim) + 0.25f * rim * clamp01(dx + dy);
            int c = Math.round(255 * clamp01(v));
            img.setRGB(x, y, Math.round(cov * 255) << 24 | c << 16 | c << 8 | c);
        }
        return img;
    }

    /** A vignette: clear in the middle, rising to solid at the edges and corners, white. */
    public static BufferedImage vignette() {
        int s = 64;
        BufferedImage img = Paint.image(s, s);
        for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) {
            float dx = (x + 0.5f) / s * 2f - 1f, dy = (y + 0.5f) / s * 2f - 1f;
            float d = (float) Math.sqrt(dx * dx * 0.8f + dy * dy);
            float a = clamp01((d - 0.5f) / 0.6f);
            a = a * a * (3 - 2 * a);
            img.setRGB(x, y, Math.round(a * 255) << 24 | 0xFFFFFF);
        }
        return img;
    }

    /**
     * A ball of golden yarn: a gold disc wound over with threads, each a great circle seen at its
     * own tilt, light and dark in turn, shaded as a sphere lit from the upper left.
     */
    public static BufferedImage yarn() {
        int s = 64;
        BufferedImage img = Paint.image(s, s);
        java.awt.Graphics2D g = Paint.graphics(img);
        float r = 30f, c = 32f;
        java.awt.Shape disc = new java.awt.geom.Ellipse2D.Float(c - r, c - r, 2 * r, 2 * r);
        g.setColor(new java.awt.Color(0xF2C23E));
        g.fill(disc);
        g.setClip(disc);
        Random rng = new Random(0xF1EE);
        g.setStroke(new java.awt.BasicStroke(1.6f));
        for (int i = 0; i < 30; i++) {
            java.awt.geom.AffineTransform before = g.getTransform();
            g.rotate(rng.nextDouble() * Math.PI, c, c);
            float squash = 0.15f + rng.nextFloat() * 0.85f;
            g.setColor(new java.awt.Color(i % 2 == 0 ? 0xFFEBA0 : 0xC8922A));
            g.draw(new java.awt.geom.Ellipse2D.Float(c - r, c - r * squash, 2 * r, 2 * r * squash));
            g.setTransform(before);
        }
        g.dispose();
        // Shade it round.
        for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) {
            int p = img.getRGB(x, y);
            if ((p >>> 24) == 0) continue;
            float dx = (x + 0.5f - c) / r, dy = (y + 0.5f - c) / r;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float lit = Math.max(0f, 0.95f + 0.35f * (-(dx + dy) * 0.55f) - 0.3f * d * d);
            int cr = Math.min(255, Math.round((p >> 16 & 255) * lit)), cg = Math.min(255, Math.round((p >> 8 & 255) * lit)),
                    cb = Math.min(255, Math.round((p & 255) * lit));
            float edge = clamp01((1f - d) * r);
            img.setRGB(x, y, Math.round(edge * 255) << 24 | cr << 16 | cg << 8 | cb);
        }
        return img;
    }

    /**
     * A drop of liquid in pixels, 8 x 10: a dark rim, a lit body, a two-pixel shine top left,
     * pointed at the top - the game's own dripping particles are drawn like this, not shaded balls.
     */
    public static BufferedImage pixDrop() {
        String[] rows = {
                "...##...",
                "...##...",
                "..#oo#..",
                "..#oo#..",
                ".#oooo#.",
                "#owoooo#",
                "#owooox#",
                "#oooxxx#",
                ".#xxxx#.",
                "..####..",
        };
        return pixels(rows, java.util.Map.of('#', 0x6A6A6A, 'o', 0xD8D8D8, 'x', 0xA8A8A8, 'w', 0xFFFFFF));
    }

    /** A splat of liquid in pixels, 16 x 16, for the board: a blob and a few flecks, two tones and a shine. */
    public static BufferedImage pixSplat() {
        String[] rows = {
                "................",
                "......#.........",
                "..#.....##....#.",
                ".......####.....",
                "....#######x....",
                "...##oooooo##...",
                ".#.#owwoooox#...",
                "...#oooooooo##..",
                "..##ooooooooox..",
                "...#oooooooox#..",
                "....#oooooxx#.#.",
                ".....#xxxx##....",
                "..#....##.......",
                "..........#.....",
                "....#...........",
                "................",
        };
        return pixels(rows, java.util.Map.of('#', 0x9A9A9A, 'o', 0xE2E2E2, 'x', 0xB8B8B8, 'w', 0xFFFFFF));
    }

    /**
     * The enchantment glint, drawn the way the GUI can draw it. The game's own glint texture is
     * plain colour, added on top of what it covers; the GUI can only blend, so drawn as it is its
     * dark streaks would darken everything. Its brightness is made the alpha instead, on white, to
     * be tinted: the bright streaks show, the dark ones do not. Made from {@code source} - the
     * game's own texture, read at runtime and never bundled - or, if that could not be read, from
     * streaks drawn here in the same slant.
     */
    public static BufferedImage glint(BufferedImage source) {
        int s = 128;
        BufferedImage img = Paint.image(s, s);
        for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) {
            float lum;
            if (source != null) {
                int p = source.getRGB(x % source.getWidth(), y % source.getHeight());
                lum = ((p >> 16 & 255) * 0.3f + (p >> 8 & 255) * 0.59f + (p & 255) * 0.11f) / 255f;
            } else {
                double d1 = Math.sin((x * 0.9 + y * 0.35) / s * Math.PI * 6), d2 = Math.sin((x * 0.2 - y) / s * Math.PI * 4);
                lum = (float) (0.35 + 0.35 * Math.pow(Math.max(0, d1), 6) + 0.3 * Math.pow(Math.max(0, d2), 8));
            }
            float a = clamp01((lum - 0.18f) * 1.6f);
            img.setRGB(x, y, Math.round(a * 255) << 24 | 0xFFFFFF);
        }
        return img;
    }

    private static BufferedImage pixels(String[] rows, java.util.Map<Character, Integer> colours) {
        BufferedImage img = Paint.image(rows[0].length(), rows.length);
        for (int y = 0; y < rows.length; y++) for (int x = 0; x < rows[y].length(); x++) {
            Integer c = colours.get(rows[y].charAt(x));
            img.setRGB(x, y, c == null ? 0 : 0xFF000000 | c);
        }
        return img;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    // ------------------------------------------------------------------ helpers

    private static void put(BufferedImage img, int[] r, int x, int y, int rgb) {
        img.setRGB(r[0] + x, r[1] + y, 0xFF000000 | rgb);
    }

    private static int jitter(int rgb, Random rng, int amount) {
        int d = rng.nextInt(amount * 2 + 1) - amount;
        int cr = Math.max(0, Math.min(255, (rgb >> 16 & 255) + d));
        int cg = Math.max(0, Math.min(255, (rgb >> 8 & 255) + d));
        int cb = Math.max(0, Math.min(255, (rgb & 255) + d));
        return cr << 16 | cg << 8 | cb;
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }
}
