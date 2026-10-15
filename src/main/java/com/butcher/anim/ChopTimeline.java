package com.butcher.anim;

/**
 * "The Chopping Block" - Diana's cutscene - as a pure function of time, verdict, creature and seed.
 *
 * The head of what was just killed - the Sphinx, the Inquisitor, the Manticore, King Minos, or
 * for a Minos Relic the Champion - thuds onto a butcher's block over the dimmed game, and the
 * Butcher's cleaver chops into it: three blows, harder and faster, the head cracking wider and
 * throwing chips, light leaking out of the cracks. On the last wind-up the light cycles colours.
 * Then the cleaver comes down through it and the head splits.
 *
 * <h2>The rule</h2>
 *
 * Nothing before {@link #SPLIT} depends on the verdict. The head is the creature's, which the
 * player already knows - they killed it - and everything else comes from the seed, picked before
 * anyone knows what dropped. The colour cycle is the same fixed sequence every time.
 * {@code tools/ChopTest} checks every millisecond of it, down to the draw calls, per creature.
 *
 * <pre>
 *     0 -  560  the head drops onto the block
 *  1250, 1850, 2400   three chops
 *  2700 - 3300  the last wind-up: the cracks cycle colours
 *  3300         the split - the first moment anything may differ
 *  3300 -       a bust: the halves fall open and crumble to nothing - over at 4400
 *               a win: the halves fall open, light pours up, the drop rises; special endings
 * </pre>
 *
 * World units are texels: the head is eight across, sitting on the block's top at y = 0.
 */
public final class ChopTimeline {

    public enum Verdict {
        BUST, RELIC, BRAIN_FOOD, CHIMERA, DYE, FATEFUL_STINGER, MANTI_CORE, CROWN_OF_GREED, SHIMMERING_WOOL, UNKNOWN;

        public boolean win() {
            return this != BUST && this != UNKNOWN;
        }
    }

    /** The five endings built after the first three, each its own class: which one a verdict plays. */
    public enum Ending { STING, HUNT, FLEECE, HEART, MIDAS, GLINT }

    /** Whose head is on the block. */
    public enum Creature {
        SPHINX, INQUISITOR, MANTICORE, KING_MINOS, CHAMPION;

        /** The head a verdict's own creature wears, where it has one; else a kill creature by seed. */
        public static Creature of(Verdict v, long seed) {
            return switch (v) {
                case RELIC -> CHAMPION;
                case BRAIN_FOOD -> SPHINX;
                case CHIMERA -> INQUISITOR;
                case FATEFUL_STINGER, MANTI_CORE -> MANTICORE;
                case CROWN_OF_GREED, SHIMMERING_WOOL -> KING_MINOS;
                default -> values()[(int) Math.floorMod(seed, 4)];
            };
        }
    }

    public static final long LAND = 560;
    public static final long[] STRIKES = {1250, 1850, 2400};
    public static final long CYCLE = 2700, SPLIT = 3300;
    public static final long ARRIVE = SPLIT + 1000;
    /** How long a swing takes from the top to the blow, and how long the blade stays in. */
    public static final long SWING_MS = 130, BITE_MS = 140;

    public static final int SPARK = 0xFFE9B0;
    public static final int COLD_ASH = 0x3C4250, DRAINED = 0x8C94A4, NEUTRAL = 0x8A8A80, MOONSTONE = 0xBFC8DD,
            WISTERIA = 0x9F7BEA, THULIAN = 0xDE6FA1, VIRIDIAN = 0x3A8C6E, CARMINE = 0xC8003C, OLIVE = 0x6F6F0C;
    /** The game's enchantment pink - Chimera's name, as its lore shows it. */
    public static final int ENCHANT_PINK = 0xFF55FF;

    static final int[] CYCLE_COLOURS = {MOONSTONE, WISTERIA, THULIAN, VIRIDIAN, CARMINE, MOONSTONE, WISTERIA, THULIAN};
    public static final long[] CYCLE_STEPS = cycleSteps();

    /** Half the head's width, and the block's top half-extents and height. */
    public static final float HEAD = 4f, BLOCK_W = 9f, BLOCK_D = 7f, BLOCK_H = 4f;

    private final Verdict verdict;
    private final Creature creature;
    private final long seed;
    private final java.util.Random rng;
    /** Where each blow lands along the head's width, and how deep it bites. */
    // The first bite a little deeper: at 1.2 the blade lay on the head and read as resting (the review, D3).
    private final float[] strikeX = new float[4], bite = {1.8f, 2.8f, 3.8f, 8.4f};
    private final float headYaw;
    private final int side;

    public ChopTimeline(Verdict verdict, Creature creature, long seed) {
        this.verdict = verdict;
        this.creature = creature;
        this.seed = seed;
        this.rng = new java.util.Random(seed * 0x9E3779B97F4A7C15L + 7);
        for (int i = 0; i < 3; i++) strikeX[i] = (rng.nextFloat() - 0.5f) * 3.2f;
        strikeX[3] = (rng.nextFloat() - 0.5f) * 0.8f;
        headYaw = (rng.nextFloat() - 0.5f) * 0.5f;
        side = rng.nextBoolean() ? 1 : -1;
        this.lines = merged(defaultLines(verdict, creature));
        this.sting = new StingEnding(this);
        this.hunt = new HuntEnding(this);
        this.fleece = new FleeceEnding(this);
        this.heart = new HeartEnding(this);
        this.midas = new MidasEnding(this);
        this.glint = new GlintEnding(this);
    }

    private final GlintEnding glint;

    public GlintEnding glint() { return glint; }

    private final MidasEnding midas;

    public MidasEnding midas() { return midas; }

    private final HeartEnding heart;

    public HeartEnding heart() { return heart; }

    /** The halves blown about after the split: {sideways, up, spin, alpha} - only the Manti-core's gust does. */
    public float[] halfBlow(long t) {
        return ending() == Ending.HEART ? heart.blow(t) : new float[]{0f, 0f, 0f, 1f};
    }

    private final FleeceEnding fleece;

    public FleeceEnding fleece() { return fleece; }

    private final StingEnding sting;
    private final HuntEnding hunt;

    public StingEnding sting() { return sting; }
    public HuntEnding hunt() { return hunt; }

    /** Which of the newer endings this verdict plays, or null. */
    public Ending ending() {
        if (simple) return null;
        return switch (verdict) {
            case FATEFUL_STINGER -> Ending.STING;
            case DYE -> Ending.HUNT;
            case SHIMMERING_WOOL -> Ending.FLEECE;
            case MANTI_CORE -> Ending.HEART;
            case CROWN_OF_GREED -> Ending.MIDAS;
            case CHIMERA -> Ending.GLINT;
            default -> null;
        };
    }

    public Verdict verdict() { return verdict; }

    // The player's settings for it (the menu's Diana section), fixed when it opens.
    private boolean simple, bannerOn = true;
    private float scale = 1f;

    /**
     * @param simple every drop gets the plain ending - the drop rising out of the split - instead
     *               of its own; nothing before the split changes either way
     * @param banner the "RARE DROP!" banner over the expensive drops' names
     * @param scale  how big the whole scene is drawn, 1 as designed
     */
    public ChopTimeline options(boolean simple, boolean banner, float scale) {
        this.simple = simple;
        this.bannerOn = banner;
        this.scale = scale;
        return this;
    }

    public float scale() { return scale; }
    public Creature creature() { return creature; }
    public long seed() { return seed; }
    public int side() { return side; }
    public float headYaw() { return headYaw; }

    public long total() {
        if (simple && verdict.win()) return 8000;
        return switch (verdict) {
            case BUST -> 4400;
            case UNKNOWN -> 5200;
            case RELIC -> RELIC_END;
            case BRAIN_FOOD -> BRAIN_END;
            case CHIMERA -> GlintEnding.END;
            case FATEFUL_STINGER -> StingEnding.END;
            case DYE -> HuntEnding.END;
            case SHIMMERING_WOOL -> FleeceEnding.END;
            case MANTI_CORE -> HeartEnding.END;
            case CROWN_OF_GREED -> MidasEnding.END;
            default -> 8000;
        };
    }

    // ================================================================== the head and the block

    /** How far above its rest the head is: dropped from high, a small bounce as it lands. */
    public float headDrop(long t) {
        if (t >= LAND + 260) return 0f;
        if (t < LAND) {
            float u = clamp(t / (float) LAND);
            return 30f * (1f - u * u);
        }
        return 1.4f * bump(t - LAND, 260);
    }

    /** Which chop has landed last: -1 before the first, 3 on the split. */
    public int blows(long t) {
        int n = -1;
        for (int i = 0; i < 3; i++) if (t >= STRIKES[i]) n = i;
        if (t >= SPLIT) n = 3;
        return n;
    }

    /** When blow {@code k} lands: 0..2 the chops, 3 the split. */
    public static long blowTime(int k) {
        return k < 3 ? STRIKES[k] : SPLIT;
    }

    /** Where blow {@code k} lands along the head's width, in head units. */
    public float strikeX(int k) {
        return strikeX[k];
    }

    /** How deep blow {@code k} goes, down from the top of the head. */
    public float bite(int k) {
        return bite[k];
    }

    /**
     * The cleaver: {x, y of its edge's middle, angle (radians; 0 level, negative raised), alpha}.
     * Raised over the head, it swings down onto its mark, bites and stays a moment, and is pulled
     * up again for the next - faster each time; the last wind-up is the longest.
     */
    public float[] cleaver(long t) {
        float in = ease((t - 650) / 350f);
        if (in <= 0f) return null;
        float raised = -1.35f;
        int next = 0;
        while (next < 4 && t >= blowTime(next)) next++;
        long prev = next == 0 ? 1000 : blowTime(next - 1);
        int prevK = Math.max(0, next - 1);
        float x, y, angle;
        if (next < 4) {
            long at = blowTime(next);
            float tx = strikeX[next], ty = 2f * HEAD - bite[next];
            if (t >= at - SWING_MS) {
                float u = easeIn((t - (at - SWING_MS)) / (float) SWING_MS);
                angle = raised * (1f - u);
                x = tx;
                y = ty + 2f * (1f - u);
            } else {
                // Pulled out of the last blow, raised, carried over to the next mark.
                float out = next == 0 ? 1f : ease((t - prev - BITE_MS) / 220f);
                float px = next == 0 ? tx : strikeX[prevK], py = next == 0 ? ty + 2f : 2f * HEAD - bite[prevK];
                angle = raised * out;
                x = px + (tx - px) * out;
                y = py + (ty + 2f - py) * out;
                // The last wind-up trembles.
                if (next == 3 && t >= CYCLE) angle += 0.03f * (float) Math.sin(t / 18.0) * ease((t - CYCLE) / 300f);
            }
        } else {
            // Through the head and into the block. A bust leaves it there; anything else pulls it away.
            angle = 0f;
            x = strikeX[3];
            y = 2f * HEAD - bite[3];
            if (verdict == Verdict.DYE && !simple) {
                // Stuck in the glowing cut, then wrenched out and spun away.
                float[] h = hunt.cleaver(t, x, y, raised);
                return h == null ? null : new float[]{h[0], h[1], h[2], in * h[3]};
            }
            if (verdict != Verdict.BUST) {
                float away = ease((t - SPLIT - 250) / 400f);
                angle = raised * 0.6f * away;
                y += 6f * away;
                return new float[]{x, y, angle, in * (1f - away)};
            }
        }
        return new float[]{x, y, angle, in};
    }

    /** Screen shake, pixels: each blow, the split hardest. */
    public float[] shake(long real) {
        // The blows shake on the scene's clock, so a stopped scene is a still one.
        long t = sceneTime(real);
        float a = 0f;
        for (int k = 0; k < 4; k++) {
            long at = blowTime(k);
            if (t >= at && t < at + 350) a = Math.max(a, (k == 3 ? 4.5f : 1.6f + k * 0.7f) * (float) Math.exp(-(t - at) / 90.0));
        }
        if (t >= LAND && t < LAND + 300) a = Math.max(a, 2.2f * (float) Math.exp(-(t - LAND) / 80.0));
        float sx = a * (float) Math.sin(t * 0.33), sy = a * (float) Math.cos(t * 0.41);
        // A kick down on each blow, the way the blade drives: shake alone has no direction (the review, D3).
        for (int k = 0; k < 4; k++) {
            long at = blowTime(k);
            if (t >= at && t < at + 220) sy += 2f * (float) Math.exp(-(t - at) / 55.0);
        }
        if (ending() == Ending.STING && t >= SPLIT) {
            float[] e = sting.shake(real);
            sx += e[0];
            sy += e[1];
        }
        if (ending() == Ending.HUNT && t >= SPLIT) {
            float h = hunt.shake(t);
            sx += h * (float) Math.sin(t * 0.37);
            sy += h * (float) Math.cos(t * 0.29);
        }
        return new float[]{sx, sy};
    }

    /** How bright the light in the cracks is: a little after each blow, more each time, all of it on the last wind-up. */
    public float crackLight(long t) {
        int n = blows(t);
        if (n < 0) return 0f;
        float base = 0.2f + 0.2f * Math.min(n, 2);
        float flare = (float) Math.exp(-(t - blowTime(Math.min(n, 3))) / 200.0) * 0.6f;
        if (t >= CYCLE && t < SPLIT) base += 0.5f * ease((t - CYCLE) / 400f);
        return Math.min(1.4f, base + flare);
    }

    /** The colour in the cracks: warm light, then the fixed cycle, then the truth. */
    public int crackColour(long t) {
        if (t < CYCLE) return SPARK;
        if (t < SPLIT) return cycleColour(t);
        return verdict == Verdict.BUST ? DRAINED : truth(t);
    }

    static int cycleColour(long t) {
        int k = CYCLE_STEPS.length - 1;
        while (k > 0 && t < CYCLE_STEPS[k]) k--;
        long end = k + 1 < CYCLE_STEPS.length ? CYCLE_STEPS[k + 1] : SPLIT;
        int from = k == 0 ? SPARK : CYCLE_COLOURS[k - 1];
        return mix(from, CYCLE_COLOURS[k], ease((t - CYCLE_STEPS[k]) / (0.45f * (end - CYCLE_STEPS[k]))));
    }

    private static long[] cycleSteps() {
        int n = CYCLE_COLOURS.length;
        double r = Math.pow(61.0 / 166.0, 1.0 / (n - 1));
        double first = (SPLIT - CYCLE) * (1 - r) / (1 - Math.pow(r, n));
        long[] out = new long[n];
        double at = CYCLE;
        for (int i = 0; i < n; i++) {
            out[i] = Math.round(at);
            at += first * Math.pow(r, i);
        }
        return out;
    }

    public int truth(long t) {
        return switch (verdict) {
            case BUST -> COLD_ASH;
            case UNKNOWN -> NEUTRAL;
            case RELIC -> MOONSTONE;
            case BRAIN_FOOD -> WISTERIA;
            case CHIMERA -> THULIAN;
            case FATEFUL_STINGER, MANTI_CORE -> VIRIDIAN;
            case CROWN_OF_GREED, SHIMMERING_WOOL -> CARMINE;
            case DYE -> OLIVE;
        };
    }

    // ================================================================== after the split

    /**
     * How far the two halves have fallen open, radians, and how much of them is left: {angle, alpha}.
     * A bust's halves tip over and crumble away; a win's lie open; the Relic's stay together (it
     * throbs, then comes apart in pieces - see {@link #chunk}); Brain Food's part only a little.
     */
    public float[] halves(long t) {
        if (t < SPLIT) return new float[]{0f, 1f};
        float u = (t - SPLIT) / 1000f;
        if (simple && verdict.win()) return new float[]{1.35f * easeOutBack(u / 0.3f), 1f};
        return switch (verdict) {
            case RELIC -> new float[]{0.05f * bump(t - SPLIT, 300), t < RELIC_PEEL ? 1f : 0f};
            case BRAIN_FOOD -> new float[]{0.16f * easeOutBack(u / 0.4f), t < BRAIN_BURST ? 1f : 0f};
            case BUST -> new float[]{1.35f * easeOutBack(u / 0.3f), 1f - ease((t - SPLIT - 450) / 350f)};
            case SHIMMERING_WOOL -> new float[]{1.35f * easeOutBack(u / 0.3f), fleece.halves(t)};
            // The halves jolt with every beat of the heart between them.
            case MANTI_CORE -> new float[]{1.35f * easeOutBack(u / 0.3f) - 0.12f * heart.beat(t), 1f};
            // Open and gilded, then burst into coins.
            case CROWN_OF_GREED -> new float[]{1.35f * easeOutBack(u / 0.3f), midas.standing(t) ? 1f : 0f};
            // Shut on a glowing cut until the geyser throws them open.
            case DYE -> new float[]{hunt.halvesAngle(t), 1f};
            default -> new float[]{1.35f * easeOutBack(u / 0.3f), 1f};
        };
    }

    /** A bust's crumble: how far through it is, 0..1, or -1 before it starts. */
    public float crumble(long t) {
        if (verdict != Verdict.BUST || t < SPLIT + 350) return -1f;
        return clamp((t - SPLIT - 350) / 700f);
    }

    /** The light pouring up out of the split, 0..1, in {@link #truth}. */
    public float splitLight(long t) {
        if (t < SPLIT) return 0f;
        if (simple && verdict.win()) return ease((t - SPLIT) / 250f);
        return switch (verdict) {
            case BUST -> 0.5f * (float) Math.exp(-(t - SPLIT) / 180.0);
            case UNKNOWN -> 0.6f * ease((t - SPLIT) / 300f);
            case RELIC -> 0.35f;
            // The light dies as the gold shuts the head into a statue.
            case CROWN_OF_GREED -> ease((t - SPLIT) / 250f) * (1f - ease((t - MidasEnding.CREEP) / 700f));
            default -> ease((t - SPLIT) / 250f);
        };
    }

    /** The drop rising out of the split, 0..1. Normal wins only - the specials bring theirs their own way. */
    public float itemRise(long t) {
        if (!verdict.win() || special() || t < SPLIT + 100) return 0f;
        float u = clamp((t - SPLIT - 100) / (float) (ARRIVE - SPLIT - 100));
        return 1f - (1f - u) * (1f - u) * (1f - u);
    }

    public float itemAlpha(long t) {
        return verdict.win() && !special() ? ease((t - SPLIT - 100) / 200f) : 0f;
    }

    /** How high the drop rises above the block, in texels. */
    public static final float ITEM_TOP = 15f;

    public long arrive() {
        if (simple) return ARRIVE;
        return switch (verdict) {
            case RELIC -> RELIC_ARRIVE;
            case BRAIN_FOOD -> BRAIN_ARRIVE;
            case CHIMERA -> GlintEnding.ARRIVE;
            case FATEFUL_STINGER -> StingEnding.ARRIVE;
            case DYE -> HuntEnding.ARRIVE;
            case SHIMMERING_WOOL -> FleeceEnding.ARRIVE;
            case MANTI_CORE -> HeartEnding.ARRIVE;
            case CROWN_OF_GREED -> MidasEnding.ARRIVE;
            default -> ARRIVE;
        };
    }

    public float rays(long t) {
        return verdict.win() ? ease((t - arrive() + 60) / 260f) : 0f;
    }

    public float nameAlpha(long t) {
        if (special()) return t >= arrive() + 150 ? 1f : 0f;
        return verdict.win() ? ease((t - ARRIVE - 50) / 300f) : 0f;
    }

    public boolean special() {
        return unmade() || thinks() || ending() != null;
    }

    /**
     * The clock the block, head, cleaver and chips run on. The same as {@code t} but for the
     * Dye, which stops it (never before the split, so the rule holds).
     */
    public long sceneTime(long t) {
        return t;
    }

    // ================================================================== the Minos Relic: "The Maze Unmade", on the block
    //
    // The Champion's head is split but holds together; it throbs three times, moonstone pulsing
    // through its cracks. Then it comes apart into its own pixels - 4 x 4 x 4 chunks - peeling off
    // from the outside in, pulled into a vortex that climbs to one point high above; the block
    // cracks and falls away into the dark; the pixels fuse into Diana's moon, which spins up like a
    // wheel flickering moon and Relic, and stops as the Relic.

    public static final long[] RELIC_BEATS = {SPLIT + 150, SPLIT + 700, SPLIT + 1250};
    public static final long RELIC_PEEL = 5000, RELIC_CONVERGE = 8400, RELIC_FALL = 8600, RELIC_SPIN = 9500,
            RELIC_ARRIVE = 11500, RELIC_END = 16800;
    public static final float MOON_X = 0f, MOON_Y = 30f, MOON_Z = -4f, MOON_SIZE = 12f;
    static final float RELIC_REST_TURN = -0.6f;

    public boolean unmade() {
        return verdict == Verdict.RELIC && !simple;
    }

    /** How hard the split head throbs with the heartbeats, 0..1. */
    public float throbRelic(long t) {
        if (!unmade()) return 0f;
        float a = 0f;
        for (long b : RELIC_BEATS) a = Math.max(a, bump(t - b, 360));
        return a;
    }

    /** When chunk (i, j, k) of the 4 x 4 x 4 head comes loose: the outside first. */
    public long chunkLaunch(int i, int j, int k) {
        float di = Math.abs(i - 1.5f), dj = Math.abs(j - 1.5f), dk = Math.abs(k - 1.5f);
        float outer = Math.max(di, Math.max(dj, dk)) / 1.5f;
        return RELIC_PEEL + Math.round((1f - outer) * 1800) + (hash(i * 16 + j * 4 + k, 3) & 255);
    }

    /**
     * Chunk (i, j, k) in flight: {x, y, z, three tumble angles, scale, glow, alpha}, or null while
     * it is still part of the head. It bursts outwards, then the vortex takes it up to the moon.
     */
    public float[] chunk(int i, int j, int k, long t) {
        long at = chunkLaunch(i, j, k);
        if (!unmade() || t < at) return null;
        float tau = (t - at) / 1000f;
        float cx = (i - 1.5f) * 2f, cy = HEAD + (j - 1.5f) * 2f, cz = (k - 1.5f) * 2f;
        float r0 = (float) Math.sqrt(cx * cx + cz * cz) + 0.5f;
        float dx = cx / r0, dz = cz / r0;
        float up = 1f - (float) Math.pow(1f - Math.min(1f, tau / 0.7f), 3);
        float ax = cx + dx * 4f * up, ay = cy + 5f * up, az = cz + dz * 4f * up;
        long vStart = at + 400;
        float q = clamp((t - vStart) / (float) (RELIC_CONVERGE - vStart));
        float qe = (float) Math.pow(q, 1.6);
        float rA = r0 + 4f;
        float theta = (float) Math.atan2(cz, cx) + 7.5f * q * q * side;
        float rad = rA * (1f - qe);
        float yA = cy + 5f;
        float bx = MOON_X + (float) Math.cos(theta) * rad, by = yA + (MOON_Y - yA) * qe, bz = MOON_Z + (float) Math.sin(theta) * rad;
        float w = ease((tau - 0.3f) / 0.5f);
        int h = hash(i * 16 + j * 4 + k, 9);
        float spin = 1f + 2.5f * q;
        return new float[]{
                ax + (bx - ax) * w, ay + (by - ay) * w, az + (bz - az) * w,
                ((h & 15) - 7.5f) * 0.9f * tau * spin, ((h >> 4 & 15) - 7.5f) * 0.9f * tau * spin, ((h >> 8 & 15) - 7.5f) * 0.9f * tau * spin,
                1f - 0.7f * qe * qe, (float) Math.sqrt(q), 1f - ease((q - 0.9f) / 0.1f)};
    }

    /** The block falling away once the head has gone: {how far down, how much is left}. */
    public float[] blockFall(long t) {
        // The Crown's golden board stays under the burst, and sinks as the crown is presented.
        // The Dye's shockwave blows the board and the halves away.
        if (ending() == Ending.HUNT) {
            if (t < HuntEnding.SHOCK) return new float[]{0f, 1f};
            return new float[]{-22f * easeIn((t - HuntEnding.SHOCK) / 500f), 1f - ease((t - HuntEnding.SHOCK) / 320f)};
        }
        if (ending() == Ending.MIDAS) {
            long at = MidasEnding.ARRIVE - 300;
            return t < at ? new float[]{0f, 1f} : new float[]{-14f * easeIn((t - at) / 900f), 1f - ease((t - at) / 600f)};
        }
        // A normal win: once the drop is up, the block and the halves sink away under it, so the
        // drop, its name and its price stand alone.
        if (verdict.win() && !unmade()) {
            long at = thinks() ? BRAIN_ARRIVE : ending() == Ending.GLINT ? GlintEnding.ARRIVE - 200
                    : ending() == Ending.STING ? StingEnding.PULL + 100
                    : ending() == Ending.FLEECE ? FleeceEnding.WIND + 500
                    : ending() == Ending.HEART ? HeartEnding.FLAPPED : ARRIVE + 300;
            if (t < at) return new float[]{0f, 1f};
            return new float[]{-14f * easeIn((t - at) / 900f), 1f - ease((t - at) / 600f)};
        }
        if (!unmade() || t < RELIC_FALL) return new float[]{0f, 1f};
        return new float[]{-30f * easeIn((t - RELIC_FALL) / 1400f), 1f - ease((t - RELIC_FALL - 500) / 900f)};
    }

    public float moonForm(long t) {
        return unmade() ? easeOutBack((t - RELIC_CONVERGE) / 500f) : 0f;
    }

    public float moonFlash(long t) {
        return unmade() && t >= RELIC_CONVERGE ? (float) Math.exp(-(t - RELIC_CONVERGE) / 250.0) : 0f;
    }

    /** The moon's turn, {tilt, spin, roll}: slow, then spun up like a wheel to stop on the Relic, three-quarters on. */
    public float[] moonAngles(long t) {
        float slow = t / 1000f * 0.45f;
        if (!unmade() || t < RELIC_SPIN) return new float[]{0.32f, slow, 0.18f};
        float settle = ease((t - RELIC_SPIN) / 1600f);
        float start = RELIC_SPIN / 1000f * 0.45f;
        double yaw = Math.toRadians(yaw(RELIC_ARRIVE));
        double end = Math.atan2(-Math.cos(yaw), -Math.sin(yaw)) + RELIC_REST_TURN;
        double turns = end - start;
        while (turns < Math.PI * 2 * 4) turns += Math.PI * 2;
        while (turns > Math.PI * 2 * 5) turns -= Math.PI * 2;
        float u = clamp((t - RELIC_SPIN) / (float) (RELIC_ARRIVE - RELIC_SPIN));
        float e = u < 0.5f ? 4f * u * u * u : 1f - (float) Math.pow(-2f * u + 2f, 3) / 2f;
        float spin = start + (float) turns * e;
        if (t >= RELIC_ARRIVE) spin += 0.12f * (float) Math.sin((t - RELIC_ARRIVE) / 700.0) * ease((t - RELIC_ARRIVE) / 600f);
        return new float[]{0.32f * (1f - settle), spin, 0.18f * (1f - settle)};
    }

    public boolean relicRevealed(long t) {
        return unmade() && t >= RELIC_ARRIVE;
    }

    public float revealFlash(long t) {
        return unmade() && t >= RELIC_ARRIVE ? (float) Math.exp(-(t - RELIC_ARRIVE) / 220.0) : 0f;
    }

    /** Which of the moon's faces are the Relic's by now, a bit per face {+X, -X, +Y, -Y, +Z, -Z}. */
    public int relicFaces(long t) {
        if (!unmade() || t < RELIC_SPIN) return 0;
        if (t >= RELIC_ARRIVE) return 0b111111;
        float u = (t - RELIC_SPIN) / (float) (RELIC_ARRIVE - RELIC_SPIN);
        int faces = 0;
        if (u > 0.2f) faces |= 0b000011;
        if (u > 0.45f) faces |= 0b110000;
        if (u > 0.7f) faces |= 0b001100;
        return faces;
    }

    // ================================================================== Brain Food: "Every Path at Once", in the head
    //
    // The Sphinx's head is split and eases a little apart. From the split, glowing veins spread over
    // the head and down across the block like neurons, each branch flashing as it lights, the ends
    // throwing off rings; brain waves pulse through them while sparks run home along them; the light
    // drains into the split; the head bursts, and the Brain Food rises out of it inside a ring of the
    // digits of pi.

    // A cheap drop (about 1M), so a short ending - see cheap().
    public static final long BRAIN_FLOOD = SPLIT + 150, BRAIN_THINK = SPLIT + 1050, BRAIN_DRAIN = SPLIT + 1450,
            BRAIN_BURST = SPLIT + 2000, BRAIN_ARRIVE = SPLIT + 2600, BRAIN_END = BRAIN_ARRIVE + 3600;
    public static final long FLOOD_MS = 900;
    public static final int SPARKS = 26;
    public static final String PI = "3.14159265358979323846264338327950288419716939937510582097494459";

    public boolean thinks() {
        return verdict == Verdict.BRAIN_FOOD && !simple;
    }

    private float[][] veins;

    /**
     * The veins: a branching tree of segments over the front and top of the head and out across
     * the block, grown from the split once per run. Each is {x0, y0, z0, x1, y1, z1, depth, parent}
     * in head-local texels, depth 0..1 the order it lights in.
     */
    public float[][] veins() {
        if (veins != null) return veins;
        java.util.List<float[]> out = new java.util.ArrayList<>();
        java.util.Random r = new java.util.Random(seed * 31 + 5);
        // Roots along the split, on the front face and the top.
        java.util.ArrayDeque<float[]> grow = new java.util.ArrayDeque<>();
        for (int k = 0; k < 4; k++) grow.add(new float[]{0f, 2f + k * 1.6f, HEAD + 0.05f, 0f, -1f, 0f, 0});
        for (int k = 0; k < 3; k++) grow.add(new float[]{0f, 2f * HEAD + 0.05f, -2f + k * 2f, 0f, -1f, 0f, 0});
        // And out of the base of the head across the block, a beat later.
        for (int k = 0; k < 5; k++) grow.add(new float[]{-3f + k * 1.5f, 0.05f, HEAD + 0.4f, 0f, -1f, 0f, 1});
        for (int k = 0; k < 2; k++) grow.add(new float[]{(k == 0 ? -1 : 1) * (HEAD + 0.4f), 0.05f, 1f, 0f, -1f, 0f, 1});
        while (!grow.isEmpty() && out.size() < 190) {
            float[] g = grow.poll();
            float x = g[0], y = g[1], z = g[2];
            int gen = (int) g[6];
            int parent = (int) g[4];
            // A few tries at a direction that stays on its surface; a branch that finds none ends.
            float[] next = null;
            for (int attempt = 0; attempt < 5 && next == null; attempt++) next = step(r, x, y, z);
            if (next == null) continue;
            int idx = out.size();
            out.add(new float[]{x, y, z, next[0], next[1], next[2], gen, parent});
            int kids = gen < 3 ? 2 : (r.nextFloat() < 0.8f ? 1 + r.nextInt(2) : 0);
            if (gen >= 8) kids = 0;
            for (int c = 0; c < kids; c++) grow.add(new float[]{next[0], next[1], next[2], 0f, idx, 0f, gen + 1});
        }
        int maxGen = 1;
        for (float[] v : out) maxGen = Math.max(maxGen, (int) v[6]);
        veins = out.toArray(new float[0][]);
        for (float[] v : veins) v[6] = v[6] / maxGen;
        return veins;
    }

    /** One vein segment from (x, y, z) along its surface - the head's top, its front, or the block's top - or null. */
    private static float[] step(java.util.Random r, float x, float y, float z) {
        double dir = (r.nextBoolean() ? 1 : -1) * (0.3 + r.nextFloat() * 1.1);
        float len = 1.1f + r.nextFloat() * 1.1f;
        if (y >= 2f * HEAD) {
            float sx = x == 0 ? (r.nextBoolean() ? 1 : -1) : Math.signum(x);
            float nx = x + (float) Math.cos(dir) * len * sx, nz = z + (float) Math.sin(dir) * len;
            return Math.abs(nx) > HEAD - 0.2f || Math.abs(nz) > HEAD - 0.2f ? null : new float[]{nx, y, nz};
        }
        if (y > 0.3f) {
            float nx = x + Math.signum(x == 0 ? (float) dir : x) * (float) Math.abs(Math.cos(dir)) * len;
            float ny = y - (float) Math.abs(Math.sin(dir)) * len * 0.8f;
            if (Math.abs(nx) > HEAD - 0.2f) return null;
            // Down off the front of the head and onto the block.
            return ny < 0f ? new float[]{nx, 0.05f, HEAD + 0.6f} : new float[]{nx, ny, z};
        }
        // Out across the block's top, away from the head.
        float ang = (float) Math.atan2(z, x == 0 ? 0.01f : x) + (r.nextFloat() - 0.5f) * 1.6f;
        float nx = x + (float) Math.cos(ang) * len * 1.3f, nz = z + (float) Math.sin(ang) * len * 1.3f;
        if (Math.abs(nx) > BLOCK_W - 0.5f || Math.abs(nz) > BLOCK_D - 0.5f) return null;
        if (Math.abs(nx) < HEAD + 0.2f && Math.abs(nz) < HEAD + 0.2f) return null;
        return new float[]{nx, 0.05f, nz};
    }

    /** How brightly vein {@code i} glows: lit in order, waves after, drained back into the split. */
    public float vein(int i, long t) {
        if (!thinks()) return 0f;
        float d = veins()[i][6];
        long at = BRAIN_FLOOD + Math.round(d * FLOOD_MS);
        if (t < at) return 0f;
        float flash = (float) Math.exp(-(t - at) / 200.0) * 0.9f;
        float wave = 0f;
        if (t >= BRAIN_THINK) {
            float w = (float) Math.sin(d * 9 - (t - BRAIN_THINK) / 95.0);
            wave = 0.6f * Math.max(0f, w) * Math.max(0f, w) * ease((t - BRAIN_THINK) / 400f);
        }
        long gone = BRAIN_DRAIN + Math.round((1f - d) * 300);
        float drain = 1f - ease((t - gone) / 180f);
        return (0.5f + wave + flash) * drain;
    }

    /** Whether vein {@code i} ends in nothing - it throws off a ring when it lights. */
    public boolean veinEnd(int i) {
        float[][] v = veins();
        for (float[] w : v) if ((int) w[7] == i && w != v[i]) return false;
        return true;
    }

    /** Spark {@code k} running home along the veins: {x, y, z} head-local, or null. */
    public float[] brainSpark(int k, long t) {
        if (!thinks() || t < BRAIN_THINK || t > BRAIN_DRAIN + 300) return null;
        float[][] v = veins();
        java.util.List<Integer> ends = new java.util.ArrayList<>();
        for (int i = 0; i < v.length; i++) if (veinEnd(i)) ends.add(i);
        if (ends.isEmpty()) return null;
        int i = ends.get((k * 7 + (int) (seed & 15)) % ends.size());
        long start = BRAIN_THINK + (k * 173L) % 500;
        if (t < start) return null;
        float travelled = (t - start) / 1000f * 12f;
        while (true) {
            float[] s = v[i];
            float len = (float) Math.sqrt((s[3] - s[0]) * (s[3] - s[0]) + (s[4] - s[1]) * (s[4] - s[1]) + (s[5] - s[2]) * (s[5] - s[2]));
            if (travelled < len) {
                float f = 1f - travelled / len;
                return new float[]{s[0] + (s[3] - s[0]) * f, s[1] + (s[4] - s[1]) * f, s[2] + (s[5] - s[2]) * f};
            }
            travelled -= len;
            int p = (int) s[7];
            if (s[6] == 0f || p < 0 || p >= v.length || p == i) return null;
            i = p;
        }
    }

    public float throb(long t) {
        if (!thinks() || t < BRAIN_BURST) return 1f;
        long ph = (t - BRAIN_BURST) % 900;
        return 1f + 0.07f * bump(ph, 160) + 0.04f * bump(ph - 200, 160);
    }

    public int piDigits(long t) {
        if (!thinks() || t < BRAIN_BURST + 100) return 0;
        return (int) Math.min(PI.length(), (t - BRAIN_BURST - 100) / 22 + 1);
    }

    /** Brain Food rising out of the burst head, 0..1. */
    public float brainRise(long t) {
        if (!thinks() || t < BRAIN_BURST) return 0f;
        float u = clamp((t - BRAIN_BURST) / (float) (BRAIN_ARRIVE - BRAIN_BURST));
        return 1f - (1f - u) * (1f - u) * (1f - u);
    }

    // ================================================================== text and price

    public static final long LETTER_MS = 70;

    public int letters(long t, int length) {
        if (!special()) return length;
        if (t < arrive() + 250) return 0;
        return (int) Math.min(length, (t - arrive() - 250) / LETTER_MS + 1);
    }

    public long letterTime(int i) {
        return arrive() + 250 + i * LETTER_MS;
    }

    public float lineAlpha(int k, long t) {
        if (!special()) return 0f;
        return cheap() ? ease((t - (arrive() + 550 + k * 250L)) / 250f) : ease((t - (arrive() + 1200 + k * 450L)) / 350f);
    }

    /**
     * The cheap drops - Brain Food (~1M), the Crown of Greed (~1.8M), the Fateful Stinger (~2.9M),
     * checked 2026-09-29 - get short endings, and their lines and price come in quicker. The user:
     * the drops that are not that expensive do not need that much animation.
     */
    /**
     * The expensive drops' banner, stamped in over the name: Hypixel's own words for it - the chat
     * says "RARE DROP!" for Diana's drops and "WOW!" for the Dye - or null for the cheap ones.
     */
    public String banner() {
        if (!bannerOn || !special() || cheap()) return null;
        return verdict == Verdict.DYE ? "WOW!" : "RARE DROP!";
    }

    /** The banner's colour: Hypixel's gold, or the Dye's pink. */
    public int bannerColour() {
        return verdict == Verdict.DYE ? 0xFF55FF : 0xFFAA00;
    }

    /** The banner slamming down onto the screen: {scale, alpha}, or null before it. */
    public float[] bannerStamp(long t) {
        long at = arrive() + 120;
        if (t < at) return null;
        return new float[]{1f + 1.3f * (float) Math.exp(-(t - at) / 85.0), ease((t - at) / 70f)};
    }

    /** How big the drop is shown, by its worth: see ChopScene.showScale. */
    public float showScale() {
        if (!verdict.win()) return 1f;
        if (verdict == Verdict.DYE) return 1.3f;
        return cheap() ? 1f : 1.25f;
    }

    public boolean cheap() {
        return verdict == Verdict.BRAIN_FOOD || verdict == Verdict.CROWN_OF_GREED || verdict == Verdict.FATEFUL_STINGER;
    }

    private String[] lines;

    /**
     * The odds line and the count line. Odds are the Empyrean drop chances before Magic Find, from
     * the wiki (hypixelskyblock.minecraft.wiki, checked 2026-09-28); the count is filled in by the
     * screen from stats once those are kept.
     */
    private static String[] defaultLines(Verdict v, Creature c) {
        return switch (v) {
            case RELIC -> new String[]{"1 in ~2,500 Minos Champions", "Your 1st Minos Relic"};
            case BRAIN_FOOD -> new String[]{"1 in ~200 Sphinxes", "Your 1st Brain Food"};
            case CHIMERA -> new String[]{"1 in ~80 Minos Inquisitors", "Your 1st Chimera I"};
            case FATEFUL_STINGER -> new String[]{"1 in ~200 Manticores", "Your 1st Fateful Stinger"};
            case MANTI_CORE -> new String[]{"1 in ~500 Manticores", "Your 1st Manti-core"};
            case CROWN_OF_GREED -> new String[]{"1 in ~50 King Minos kills", "Your 1st Crown of Greed"};
            case SHIMMERING_WOOL -> new String[]{"1 in ~500 King Minos kills", "Your 1st Shimmering Wool"};
            case DYE -> new String[]{switch (c) {
                case MANTICORE -> "1 in ~10,000 Manticores";
                case KING_MINOS -> "1 in ~10,000 King Minos kills";
                case SPHINX -> "1 in ~50,000 Sphinxes";
                case INQUISITOR -> "1 in ~50,000 Minos Inquisitors";
                case CHAMPION -> "1 in ~250,000 Minos Champions";
            }, "Your 1st Mythological Dye"};
            default -> new String[0];
        };
    }

    /**
     * The odds and the count on one line, "1 in ~80 Minos Inquisitors · your 1st": six lines stacked under a
     * small icon was a wall of text (the review, D9).
     */
    private static String[] merged(String[] two) {
        if (two.length < 2) return two;
        return new String[]{two[0] + " \u00b7 your 1st"};
    }

    public void lines(String... lines) { this.lines = lines; }

    public String[] lines() { return lines; }

    public static final long COUNT_MS = 1100;
    private long price = -1;
    private String priceLabel = "";

    public void price(long coins, String label) {
        this.price = coins;
        this.priceLabel = label;
    }

    public long price() { return price; }
    public String priceLabel() { return priceLabel; }

    public long countStart() {
        if (!special()) return ARRIVE + 750;
        return cheap() ? arrive() + 1150 : arrive() + 2300;
    }

    public float count(long t) {
        if (!verdict.win() || t < countStart()) return -1f;
        float u = clamp((t - countStart()) / (float) COUNT_MS);
        return 1f - (1f - u) * (1f - u) * (1f - u);
    }

    // ================================================================== the frame

    /** How dark the game behind is: dimmed in, as the other cutscenes, and back out at the end. */
    public float dim(long t) {
        float in = ease(t / 250f);
        float out = 1f - ease((t - (total() - 450)) / 450f);
        return 0.82f * in * out;
    }

    /** How much colour the world has: 1 (the Dye's stone, which drained it, is gone). */
    public float saturation(long t) {
        return 1f;
    }

    /** Everything drawn fades in and out with the dim. */
    public float master(long t) {
        return ease(t / 200f) * (1f - ease((t - (total() - 400)) / 400f));
    }

    public float flash(long t) {
        float f = 0f;
        if (t >= SPLIT) f += 0.18f * (float) Math.exp(-(t - SPLIT) / 80.0);
        if (ending() == Ending.STING && t >= StingEnding.IMPACT) f += 0.22f * (float) Math.exp(-(t - StingEnding.IMPACT) / 60.0);
        if (ending() == Ending.HUNT && t >= HuntEnding.GEYSER) f += 0.2f * (float) Math.exp(-(t - HuntEnding.GEYSER) / 90.0);
        if (ending() == Ending.HUNT && t >= HuntEnding.SHOCK) f += 0.3f * (float) Math.exp(-(t - HuntEnding.SHOCK) / 110.0);
        return f;
    }

    /** Where the flash is: the stab, for the Stinger's; else the head. */
    public float[] flashAt(long t) {
        if (ending() == Ending.STING && t >= StingEnding.IMPACT) return new float[]{StingEnding.STAB_X, 0.5f, StingEnding.STAB_Z};
        return new float[]{0f, HEAD, 0f};
    }

    public float arrivalBurst(long t) {
        return verdict.win() && t >= arrive() ? (float) Math.exp(-(t - arrive()) / 180.0) : 0f;
    }

    // ================================================================== the camera

    /** Degrees round the block: a three-quarter view from the seed's side, drifting, orbiting a win. */
    public float yaw(long t) {
        float yaw = 44f - 8f * ease(t / (float) SPLIT);
        if (t >= SPLIT && simple && verdict.win()) {
            yaw += -24f * ease((t - SPLIT) / 3500f);
        } else if (t >= SPLIT) {
            yaw += switch (verdict) {
                case BUST -> -2f * ease((t - SPLIT) / 1000f);
                case RELIC -> -30f * ease((t - RELIC_PEEL) / 6000f);
                case BRAIN_FOOD -> -18f * ease((t - SPLIT) / 5000f);
                case CHIMERA -> glint.yaw(t);
                case FATEFUL_STINGER -> sting.yaw(t);
                case DYE -> hunt.yaw(t);
                case SHIMMERING_WOOL -> fleece.yaw(t);
                case MANTI_CORE -> heart.yaw(t);
                case CROWN_OF_GREED -> midas.yaw(t);
                default -> -24f * ease((t - SPLIT) / 3500f);
            };
        }
        return side * yaw;
    }

    public float pitch(long t) {
        float p = -24f;
        if (t >= CYCLE && t < SPLIT) p -= 4f * ease((t - CYCLE) / 600f);
        if (t >= SPLIT) p -= 4f;
        if (ending() == Ending.STING) p += sting.pitch(t);
        if (ending() == Ending.FLEECE) p += fleece.pitch(t);
        if (ending() == Ending.HUNT) p += hunt.pitch(t);
        if (unmade()) {
            p += 18f * ease((t - RELIC_PEEL) / 2600f);
            p += (-8f - p) * ease((t - RELIC_FALL) / 1200f);
        }
        return p;
    }

    public float zoom(long t) {
        float z = 1f + 0.12f * ease((t - STRIKES[0]) / 1500f) + 0.14f * easeInQuad((t - CYCLE) / (float) (SPLIT - CYCLE));
        if (t < SPLIT) return z;
        z = 1.26f;
        if (unmade()) {
            z -= 0.45f * ease((t - RELIC_PEEL) / 2600f);
            return z + (0.85f - z) * ease((t - RELIC_FALL) / 1800f);
        }
        if (ending() == Ending.GLINT) return glint.zoom(t);
        if (ending() == Ending.STING) return sting.zoom(t);
        if (ending() == Ending.HUNT) return hunt.zoom(t);
        if (ending() == Ending.FLEECE) return fleece.zoom(t);
        if (ending() == Ending.HEART) return heart.zoom(t);
        if (ending() == Ending.MIDAS) return midas.zoom(t);
        return switch (verdict) {
            case BUST -> z - 0.06f * ease((t - SPLIT) / 1000f);
            default -> z + 0.1f * ease((t - SPLIT) / 1200f);
        };
    }

    /** What the camera looks at, texels. */
    public float[] focus(long t) {
        float y = HEAD + 3f;
        if (t >= SPLIT && ending() == Ending.STING) return sting.focus(t);
        if (t >= SPLIT && ending() == Ending.HUNT) return hunt.focus(t);
        if (t >= SPLIT && ending() == Ending.FLEECE) return fleece.focus(t);
        if (t >= SPLIT && ending() == Ending.HEART) return heart.focus(t);
        if (t >= SPLIT && ending() == Ending.MIDAS) return midas.focus(t);
        if (t >= SPLIT && ending() == Ending.GLINT) return glint.focus(t);
        if (t >= SPLIT) {
            if (verdict.win() && !special()) y += ITEM_TOP * 0.45f * itemRise(t);
            if (thinks()) y += ITEM_TOP * 0.4f * brainRise(t);
            if (unmade()) {
                float climb = ease((t - RELIC_PEEL) / 2600f), hero = ease((t - RELIC_FALL) / 1200f);
                y += (MOON_Y * 0.72f - y) * climb;
                y += (MOON_Y - 3f - y) * hero;
                return new float[]{0f, y, MOON_Z * hero};
            }
        }
        return new float[]{0f, y, 0f};
    }

    // ================================================================== maths

    private int hash(int a, int b) {
        int h = (int) (a * 73856093 ^ b * 19349663 ^ seed * 83492791);
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7FFFFFFF;
    }

    static float clamp(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    static float ease(float t) { t = clamp(t); return t * t * (3 - 2 * t); }
    static float easeIn(float t) { t = clamp(t); return t * t * t; }
    static float easeInQuad(float t) { t = clamp(t); return t * t; }

    static float easeOutBack(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float c = 1.9f, u = t - 1f;
        return 1f + (c + 1f) * u * u * u + c * u * u;
    }

    static float bump(long dt, long len) {
        if (dt <= 0 || dt >= len) return 0f;
        float u = dt / (float) len;
        return (float) Math.sin(Math.PI * Math.pow(u, 0.5));
    }

    public static int mix(int a, int b, float t) {
        t = clamp(t);
        int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }
}
