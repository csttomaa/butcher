package com.butcher.anim;

import static com.butcher.anim.ChopTimeline.*;

/**
 * The Mythological Dye's ending, "The Whole Hunt" - pure, like the rest of {@link ChopTimeline}. It replaced
 * "Wet Paint" (0.2.75): that was the third dye to stop time, after the Flame Dye and the Byzantium Dye, and every
 * animation's dye is to have its own, grandest ending. This one erupts.
 *
 * <pre>
 *  SPLIT         the split goes wrong: the cut fills with olive light instead of opening, the board shudders
 *                and cracks
 *  +300 (YANK)   the cleaver is wrenched out of the head and spins away
 *  +700 (HUSH)   half a second of nothing
 *  +1200         the geyser: olive paint blasts out of the split, three times the block's height; the halves are
 *                thrown open; it rains olive on the board and runs down the halves
 *  +1700         the parade: the whole bestiary, one head at a time out of the geyser - the seven lesser
 *                creatures, then the Sphinx, the Champion, the Manticore and the Inquisitor - each landing in a ring
 *                round the block and painted olive from its splash; King Minos last, bigger, from the top of the
 *                screen
 *  +5200 (SPIN)  the ring turns, faster and faster; the camera tilts up and Diana's moon rises behind it
 *  +6400 (ARROW) a silver arrow from the moon threads through every head in turn, King Minos last; each bursts
 *  +8600 (SUCK)  everything is pulled into the split
 *  +9200 (SHOCK) a shockwave of olive light blows the board and the halves away
 *  +9700         the Mythological Dye in the column, under WOW!
 * </pre>
 *
 * Nothing here runs before the split, so ChopTest's rule holds. The heads are the real NEU bestiary skins
 * ({@link #BESTIARY}, HeadSkins.CREATURES).
 */
public final class HuntEnding {

    public static final long YANK = SPLIT + 300, HUSH = SPLIT + 700, GEYSER = SPLIT + 1200, PARADE = SPLIT + 1700,
            LAUNCH_GAP = 220, FLIGHT = 600, KING_DROP = PARADE + 11 * LAUNCH_GAP + 200, KING_FALL = 700,
            SPIN = SPLIT + 5200, MOON = SPLIT + 5300, MOON_UP = 1100, ARROW = SPLIT + 6400, FIRST_HIT = ARROW + 350,
            HIT_GAP = 150, SUCK = SPLIT + 8600, SHOCK = SPLIT + 9200, ARRIVE = SPLIT + 9700, END = ARRIVE + 5800;

    /** The paint's colours: the dye's own olive, its lights and shadows. */
    public static final int PAINT = OLIVE, PAINT_LIGHT = 0xB3B34A, PAINT_DEEP = 0x44440A, GLOW = 0xD8D86A;

    /**
     * The bestiary, in the order they come out of the geyser: the seven lesser creatures, the four great ones,
     * King Minos last. HeadSkins.CREATURES keys.
     */
    public static final String[] BESTIARY = {"MINOS_HUNTER", "SIAMESE_LYNX", "GAIA_CONSTRUCT", "HARPY", "CRETAN_BULL",
            "STRANDED_NYMPH", "MINOTAUR", "SPHINX", "CHAMPION", "MANTICORE", "INQUISITOR", "KING_MINOS"};
    public static final int HEADS = BESTIARY.length, KING = HEADS - 1;

    /** The ring: its radius, the height the heads hover at, a head's half-size and King Minos's. */
    public static final float RING_R = 20f, RING_Y = 3f, HEAD_HALF = 2.5f, KING_HALF = 3.6f;
    /** The geyser's full height and where the heads leave it. */
    public static final float GEYSER_TOP = 26f, MOUTH_Y = 20f;
    /** Diana's moon: where it rests behind the ring, and its half-size. */
    public static final float MOON_Z = -22f, MOON_TOP = 22f, MOON_HALF = 4f;
    /** Where the dye rests in the column. */
    public static final float ITEM_Y = 14f;

    private final ChopTimeline tl;
    private float kingAngle = Float.NaN;
    private final int[] slot = new int[HEADS];
    private final int[] hitOrder = new int[HEADS];

    HuntEnding(ChopTimeline tl) {
        this.tl = tl;
        // King Minos takes the slot nearest the camera (see kingAngle); the rest share the ring evenly from him round.
        slot[KING] = 0;
        for (int k = 0; k < KING; k++) slot[k] = 1 + (k * 5) % KING;
        // The arrow goes round the ring in turn, starting just past King Minos, and ends on him.
        int n = 0;
        for (int s = 1; s < HEADS; s++) {
            for (int k = 0; k < KING; k++) if (slot[k] == s) hitOrder[n++] = k;
        }
        hitOrder[n] = KING;
    }

    // ------------------------------------------------------------------ the split going wrong

    /** How hard everything shudders before the geyser, 0..1, rising. */
    public float shudder(long t) {
        if (t < SPLIT || t >= GEYSER + 200) return 0f;
        if (t < HUSH) return 0.35f + 0.65f * ease((t - SPLIT) / (float) (HUSH - SPLIT));
        // The hush: almost still, a fine tremble.
        return 0.15f;
    }

    /** The board's cracks: how far they have run, 0..1. */
    public float cracks(long t) {
        return ease((t - SPLIT - 150) / 550f);
    }

    /** The halves' angle open: shut and trembling until the geyser throws them open. */
    public float halvesAngle(long t) {
        if (t < GEYSER) return 0.025f * (float) Math.sin(t / 23.0) * shudder(t);
        return 1.35f * easeOutBack((t - GEYSER) / 260f);
    }

    /** The cleaver after the split: {x, y, angle, alpha} - in the head, then wrenched up and spun away. */
    public float[] cleaver(long t, float x0, float y0, float raised) {
        if (t < YANK) return new float[]{x0, y0, 0.02f * (float) Math.sin(t / 19.0), 1f};
        float u = (t - YANK) / 700f;
        if (u >= 1f) return null;
        float side = tl.side();
        float x = x0 + side * 22f * u * u, y = y0 + 26f * u - 10f * u * u;
        float angle = raised * ease(u * 3f) - side * 9f * u * u;
        return new float[]{x, y, angle, 1f - ease((u - 0.6f) / 0.4f)};
    }

    // ------------------------------------------------------------------ the geyser

    /** The geyser's height now: up in a blast, standing (with a flutter) through the parade, gone into the suck. */
    public float geyser(long t) {
        if (t < GEYSER || t >= SUCK + 400) return 0f;
        float up = 1f - (float) Math.pow(1f - Math.min(1f, (t - GEYSER) / 250f), 3);
        float h = GEYSER_TOP * up * (1f + 0.05f * (float) Math.sin(t / 70.0));
        // It eases down while the moon rises, and is pulled back down at the end.
        h *= 1f - 0.35f * ease((t - SPIN) / 1200f);
        h *= 1f - ease((t - SUCK) / 400f);
        return h;
    }

    /** Raindrop {@code i} of {@link #RAIN}: {x, y, z} while it falls, or null. */
    public static final int RAIN = 90;

    public float[] rain(int i, long t) {
        long at = GEYSER + 150 + i * 60L;
        if (at > SPIN + 800) return null;
        float u = (t - at) / 900f;
        if (u < 0f || u >= 1f) return null;
        float[] land = rainLand(i);
        float top = GEYSER_TOP * (0.75f + 0.25f * frac(i, 3));
        float y = top + (land[1] - top) * u * u + 6f * u * (1f - u);
        return new float[]{land[0] * u, y, land[2] * u};
    }

    /** Where raindrop {@code i} lands: on the board (y 0) or past it (y -30, off its edge). */
    public float[] rainLand(int i) {
        float a = (float) (frac(i, 7) * Math.PI * 2), r = 2f + 14f * (float) Math.sqrt(frac(i, 11));
        float x = (float) Math.cos(a) * r, z = (float) Math.sin(a) * r;
        boolean on = Math.abs(x) < BLOCK_W - 0.3f && Math.abs(z) < BLOCK_D - 0.3f;
        return new float[]{x, on ? 0f : -30f, z};
    }

    public long rainLanded(int i) {
        return GEYSER + 150 + i * 60L + 900;
    }

    /**
     * The board's stain: whether a texel of the board's top at (x, z) has been hit by paint yet. Each drop that
     * lands on it spreads a splat that grows a little.
     */
    public boolean stained(float x, float z, long t) {
        if (t < GEYSER) return false;
        for (int i = 0; i < RAIN; i++) {
            long l = rainLanded(i);
            if (t < l) break;
            float[] p = rainLand(i);
            if (p[1] < 0f) continue;
            float r = 0.9f + 0.7f * ease((t - l) / 400f) + 0.4f * frac(i, 13);
            float dx = x - p[0], dz = z - p[2];
            if (dx * dx + dz * dz < r * r) return true;
        }
        // Round the split itself, where it blasted out.
        float r0 = 2.6f * ease((t - GEYSER) / 300f);
        return x * x + z * z < r0 * r0;
    }

    /**
     * Paint running down the halves from their tops: whether a texel at height y (column x) is wet yet. Each
     * column runs at its own speed, so it drips rather than drops as a sheet.
     */
    public boolean dripped(float x, float y, float z, long t) {
        if (t < GEYSER + 200) return false;
        float speed = 4f + 5f * frac(Math.round(x * 2f) * 31 + Math.round(z * 2f) * 7, 17);
        float reach = 2f * HEAD + 0.5f - speed * (t - GEYSER - 200) / 1000f;
        return y > reach;
    }

    // ------------------------------------------------------------------ the parade

    public long launch(int k) {
        return k == KING ? KING_DROP : PARADE + k * LAUNCH_GAP;
    }

    public long landing(int k) {
        return launch(k) + (k == KING ? KING_FALL : FLIGHT);
    }

    public float half(int k) {
        return k == KING ? KING_HALF : HEAD_HALF;
    }

    /** The ring's turn, radians: still, then spun up faster and faster. */
    public float ringTurn(long t) {
        if (t < SPIN) return 0f;
        float s = (t - SPIN) / 1000f;
        // Accelerating hard to the arrow, then a steady 4.5 rad/s through the hits.
        float a = 1.6f, top = 4.5f, tTop = top / a;
        float turn = s < tTop ? 0.5f * a * s * s : 0.5f * a * tTop * tTop + top * (s - tTop);
        return tl.side() * turn;
    }

    /** King Minos's slot: the one nearest the camera as he lands. Worked out on first use - it needs the camera. */
    private float kingAngle() {
        if (Float.isNaN(kingAngle)) {
            double yaw = Math.toRadians(tl.yaw(KING_DROP + KING_FALL));
            kingAngle = (float) Math.atan2(Math.cos(yaw), -Math.sin(yaw));
        }
        return kingAngle;
    }

    /** Head {@code k}'s place in the ring at {@code t}: {x, y, z, its yaw (radians, face outward)}. */
    public float[] ringPlace(int k, long t) {
        double a = kingAngle() + slot[k] * Math.PI * 2 / HEADS + ringTurn(t);
        float r = RING_R + (k == KING ? 1.5f : 0f);
        float x = (float) Math.cos(a) * r, z = (float) Math.sin(a) * r;
        float bob = 0.4f * (float) Math.sin(t / 380.0 + k);
        return new float[]{x, RING_Y + (k == KING ? 1f : 0f) + bob, z, (float) Math.atan2(Math.cos(a), Math.sin(a))};
    }

    /**
     * Head {@code k} now: {x, y, z, yaw, pitch, roll, half, alpha}, or null before it is launched or after the arrow
     * has burst it. Out of the geyser's mouth tumbling, arcing to its place (King Minos falling from the top of the
     * screen), landing with a squash.
     */
    public float[] head(int k, long t) {
        long at = launch(k), land = landing(k);
        if (t < at || t >= hit(k)) return null;
        float[] place = ringPlace(k, Math.max(t, land));
        float h = half(k);
        if (t < land) {
            float u = (t - at) / (float) (land - at);
            float x, y, z;
            if (k == KING) {
                // From high above the ring onto his place, a little spin.
                x = place[0];
                z = place[2];
                y = place[1] + 40f * (1f - u * u);
                return new float[]{x, y, z, place[3] + 1.5f * (1f - u), 0f, 0f, h, 1f};
            }
            float g = Math.min(1f, (t - at) / 120f);
            x = place[0] * u;
            z = place[2] * u;
            y = MOUTH_Y + (place[1] - MOUTH_Y) * u + 9f * 4f * u * (1f - u);
            float spin = (1f - u) * (3f + (k % 3));
            return new float[]{x, y, z, place[3] + spin * 2.1f, spin * 1.3f, spin * 0.6f, h * (0.4f + 0.6f * g), 1f};
        }
        // A squash as it lands, then hovering.
        float squash = bump(t - land, 220);
        return new float[]{place[0], place[1] - 0.6f * squash, place[2], place[3], 0f, 0f, h * (1f + 0.12f * squash), 1f};
    }

    /** How much of head {@code k} the splash has painted, 0..1, from its landing. */
    public float painted(int k, long t) {
        return ease((t - landing(k)) / (k == KING ? 650f : 480f));
    }

    // ------------------------------------------------------------------ Diana's shot

    /** The moon rising behind the ring: its height, or NaN before it. */
    public float moonY(long t) {
        if (t < MOON) return Float.NaN;
        float u = easeOutBack((t - MOON) / (float) MOON_UP);
        return -14f + (MOON_TOP + 14f) * u;
    }

    public float moonAlpha(long t) {
        return ease((t - MOON) / 400f) * (1f - ease((t - SUCK) / 500f));
    }

    /** When the arrow passes through head {@code k}. */
    public long hit(int k) {
        for (int n = 0; n < HEADS; n++) if (hitOrder[n] == k) return FIRST_HIT + n * HIT_GAP + (k == KING ? 120 : 0);
        return Long.MAX_VALUE;
    }

    /** The heads in the order the arrow takes them. */
    public int hitOrder(int n) {
        return hitOrder[n];
    }

    /**
     * The arrow: {x, y, z, dx, dy, dz} - its point and the way it is flying - or null. Off the moon at ARROW, through
     * each head where it is at the moment it is hit, then down into the split.
     */
    public float[] arrow(long t) {
        if (t < ARROW || t >= SUCK + 150) return null;
        float[] prev = {0f, MOON_TOP, MOON_Z};
        long prevAt = ARROW;
        for (int n = 0; n <= HEADS; n++) {
            float[] next;
            long at;
            if (n < HEADS) {
                int k = hitOrder[n];
                at = hit(k);
                float[] p = ringPlace(k, at);
                next = new float[]{p[0], p[1], p[2]};
            } else {
                at = SUCK + 150;
                next = new float[]{0f, 2f, 0f};
            }
            if (t < at) {
                float u = (t - prevAt) / (float) (at - prevAt);
                float dx = next[0] - prev[0], dy = next[1] - prev[1], dz = next[2] - prev[2];
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-4f;
                return new float[]{prev[0] + dx * u, prev[1] + dy * u, prev[2] + dz * u, dx / len, dy / len, dz / len};
            }
            prev = next;
            prevAt = at;
        }
        return null;
    }

    /** Head {@code k}'s burst: {x, y, z, seconds since} while its splats fly, or null. */
    public float[] burst(int k, long t) {
        long at = hit(k);
        if (t < at || t > at + 900) return null;
        float[] p = ringPlace(k, at);
        return new float[]{p[0], p[1], p[2], (t - at) / 1000f};
    }

    // ------------------------------------------------------------------ the end

    /** Everything being pulled into the split, 0..1. */
    public float suck(long t) {
        return easeIn((t - SUCK) / (float) (SHOCK - SUCK));
    }

    /** The shockwave, 0 at its start .. 1 gone; -1 before. */
    public float shock(long t) {
        if (t < SHOCK) return -1f;
        return Math.min(1f, (t - SHOCK) / 900f);
    }

    /** The dye rising in the column: {y, alpha}, or null before it. */
    public float[] item(long t) {
        if (t < SHOCK + 100) return null;
        float u = ease((t - SHOCK - 100) / (float) (ARRIVE - SHOCK - 100));
        return new float[]{2f + (ITEM_Y - 2f) * u, ease((t - SHOCK - 100) / 250f)};
    }

    /** The column of olive light the dye rises in: 0..1. */
    public float column(long t) {
        if (t < SHOCK) return 0f;
        return ease((t - SHOCK) / 200f) * (1f - 0.6f * ease((t - ARRIVE - 400) / 1500f));
    }

    // ------------------------------------------------------------------ camera

    public float zoom(long t) {
        float z = 1.26f;
        z -= 0.16f * ease((t - SPLIT) / 700f);
        z -= 0.15f * ease((t - GEYSER) / 900f);
        z -= 0.15f * ease((t - SPIN) / 1400f);
        z += 0.35f * ease((t - SHOCK) / 900f);
        return z;
    }

    /**
     * Looking down on the ring as it fills (from the side it would read as a heap), up at the moon as it rises
     * behind it, level again for the dye. Added to the scene's own -28.
     */
    public float pitch(long t) {
        return -14f * ease((t - GEYSER) / 1100f) + 12f * ease((t - SPIN) / 1400f) + 2f * ease((t - SUCK) / 1100f);
    }

    public float[] focus(long t) {
        float y = HEAD + 3f;
        y -= 1f * ease((t - GEYSER) / 900f);
        y += 6f * ease((t - SPIN) / 1400f);
        y += (ITEM_Y - y) * ease((t - SUCK) / 1300f);
        return new float[]{0f, y, 0f};
    }

    public float yaw(long t) {
        return -14f * ease((t - GEYSER) / 4000f) + 8f * ease((t - SHOCK) / 3000f);
    }

    /** Screen shake: the shudder, the geyser, each landing, King Minos, the shockwave. */
    public float shake(long t) {
        float a = 2.2f * shudder(t);
        if (t >= GEYSER) a = Math.max(a, 5f * (float) Math.exp(-(t - GEYSER) / 140.0));
        for (int k = 0; k < HEADS; k++) {
            long l = landing(k);
            if (t >= l && t < l + 300) a = Math.max(a, (k == KING ? 5f : 1.1f) * (float) Math.exp(-(t - l) / (k == KING ? 150.0 : 70.0)));
        }
        if (t >= SHOCK) a = Math.max(a, 6f * (float) Math.exp(-(t - SHOCK) / 160.0));
        return a;
    }

    // ------------------------------------------------------------------ maths

    /** A fixed fraction 0..1 for index {@code i} and salt {@code s}: seeded by the run. */
    float frac(int i, int s) {
        long h = (i * 0x9E3779B97F4A7C15L) ^ (s * 0xC2B2AE3D27D4EB4FL) ^ (tl.seed() * 0x165667B19E3779F9L);
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
}
