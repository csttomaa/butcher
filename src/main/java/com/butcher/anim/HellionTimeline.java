package com.butcher.anim;

/**
 * "Hellion" - the Inferno Demonlord's animation, tier IV only (the user, 2026-10-02). Pure: everything
 * here is a function of time, the drop and a seed, so it can be checked offline.
 *
 * A blaze - the vanilla model, head and twelve rods - is the Demonlord's core. Its Hellion Shield, the
 * fight's own mechanic, cycles through the four attunements (Ashen, Spirit, Auric, Crystal - Skyblocker's
 * colours), each step a dagger clang, faster and faster; the rods snap in and it hangs; at
 * {@link #REVEAL} the shield lands on one colour - the answer. <b>Everything before {@link #REVEAL} is the
 * same for every drop</b> (HellionTest hashes it). Ashen is a bust: the fire goes out, the rods fall, the
 * head crumbles into ash. Any other colour shatters the shield and the drop comes out of the core.
 * Two fire pillars at the sides burn in the shield's colour the whole way.
 *
 * Only drops worth 500k or more play it (the user's floor); the rest are a bust.
 */
public final class HellionTimeline {

    /** The four attunements, in the Hellion Shield's own order. */
    public enum Attunement {
        ASHEN(0x6A6A6A), SPIRIT(0xF4F4F4), AURIC(0xFFD23A), CRYSTAL(0x56E2FF);

        public final int rgb;

        Attunement(int rgb) {
            this.rgb = rgb;
        }
    }

    /** How a win ends. */
    public enum Ending { BUST, SHATTER, DICE, HIGH_CLASS, DYE }

    /** What dropped. {@code art} is a head skin's name (HeadSkins.SLAYER) or a vanilla item icon. */
    public enum Drop {
        BUST("", 0, Attunement.ASHEN, Ending.BUST, null, null, false, 0),
        DUPLEX("Duplex I", 0xFF55FF, Attunement.SPIRIT, Ending.SHATTER, "ENCHANTMENT_ULTIMATE_REITERATE_1", "enchanted_book", false, 54),
        SCORCHED_CRYSTAL("Scorched Power Crystal", 0xFFAA00, Attunement.SPIRIT, Ending.SHATTER, "SCORCHED_POWER_CRYSTAL", "Scorched Power Crystal", true, 32),
        SUBZERO("Subzero Inverter", 0xFFAA00, Attunement.SPIRIT, Ending.SHATTER, "SUBZERO_INVERTER", "paper", false, 1100),
        WILSONS("Wilson's Engineering Plans", 0xFFAA00, Attunement.SPIRIT, Ending.SHATTER, "WILSON_ENGINEERING_PLANS", "paper", false, 1100),
        PARAGON("Paragon Shard", 0xFF55FF, Attunement.CRYSTAL, Ending.SHATTER, "SHARD_PARAGON", "Paragon Shard", true, 400),
        ARCHFIEND_DICE("Archfiend Dice", 0xAA00AA, Attunement.AURIC, Ending.DICE, "ARCHFIEND_DICE", "Archfiend Dice", true, 95),
        HIGH_CLASS_DICE("High Class Archfiend Dice", 0xFFAA00, Attunement.AURIC, Ending.HIGH_CLASS, "HIGH_CLASS_ARCHFIEND_DICE", "High Class Archfiend Dice", true, 385),
        FLAME_DYE("Flame Dye", 0xFFAA00, Attunement.AURIC, Ending.DYE, "DYE_FLAME", "Flame Dye", true, 500_000);

        public final String title;
        public final int colour;
        public final Attunement lands;
        public final Ending ending;
        /** For its price: the bazaar product, else the lowest BIN. */
        public final String id;
        /** A head skin's name when {@link #head}, else a vanilla item icon (textures/item/...); null for none. */
        public final String art;
        public final boolean head;
        /** About one in this many tier IV bosses (wiki, 2026-10-02); 0 for none. */
        public final int oneIn;

        Drop(String title, int colour, Attunement lands, Ending ending, String id, String art, boolean head, int oneIn) {
            this.title = title;
            this.colour = colour;
            this.lands = lands;
            this.ending = ending;
            this.id = id;
            this.art = art;
            this.head = head;
            this.oneIn = oneIn;
        }

        public boolean win() {
            return this != BUST;
        }
    }

    // ------------------------------------------------------------------ the beats, ms

    /** The blaze appears. */
    public static final long APPEAR = 700;
    /**
     * The shield's cycle: each step a clang. Four full rounds, the first step 420 ms and each 9% shorter
     * than the one before - slow, then faster and faster. The user, after the first version: "way too
     * fast", and the rods can take 4.5-5 s, this one is not rushed.
     */
    public static final long[] STEPS = steps(16, APPEAR, 420f, 0.91f);
    /** The last step's end: the shield hangs on a flicker, the rods at full speed. */
    public static final long HANG = STEPS[STEPS.length - 1] + Math.round(420f * Math.pow(0.91f, STEPS.length - 1));
    /** The answer. Nothing before this may depend on the drop. */
    public static final long REVEAL = 4800;
    /** A bust is over by this - short after the reveal, so the player is back to the next boss. */
    public static final long BUST_END = REVEAL + 900;
    /** The drop out of the core, up to where it stands. */
    public static final long ARRIVE = REVEAL + 800;

    private static long[] steps(int n, long start, float first, float ratio) {
        long[] out = new long[n];
        float t = start, d = first;
        for (int i = 0; i < n; i++) {
            out[i] = Math.round(t);
            t += d;
            d *= ratio;
        }
        return out;
    }
    public static final long FADE = 400;

    private final Drop drop;
    private final long seed;

    public HellionTimeline(Drop drop, long seed) {
        this.drop = drop;
        this.seed = seed;
    }

    public Drop drop() { return drop; }
    public long seed() { return seed; }

    public long total() {
        return switch (drop.ending) {
            case BUST -> BUST_END;
            case SHATTER -> arrive() + 3200;
            case DICE -> arrive() + 3600;
            case HIGH_CLASS -> arrive() + 5500;
            case DYE -> arrive() + 6500;
        };
    }

    /** When the die is thrown from the core, in the dice endings - once the head has imploded. */
    public static final long DICE_LAUNCH = REVEAL + 220;
    /** The Flame Dye: everything physical stops dead this long after the reveal... */
    public static final long FREEZE = REVEAL + 260;
    /** ...and starts again here, as the flame takes it - after three heartbeats of stopped time. */
    public static final long IGNITE = REVEAL + 2400;
    /** The heartbeats in the Flame Dye's stopped time: each one swells the ember at the core. */
    public static final long[] HEARTS = {FREEZE + 400, FREEZE + 1120, FREEZE + 1840};

    // ---- the High Class Dice: bullet time on the last tip onto its 6, then the pips light one by one

    /** The die's own clock (ms after the throw) slows to {@link #SLOW_RATE} between these two. */
    public static final long SLOW_FROM = 1150, SLOW_TO = 1560;
    public static final float SLOW_RATE = 0.35f;
    private static final long SLOW_REAL = Math.round((SLOW_TO - SLOW_FROM) / SLOW_RATE);
    /** One pip lights every this many ms once it is flat. */
    public static final long PIP_STEP = 130;

    /** When the drop stands where it is shown: after the die settles in the dice endings. */
    public long arrive() {
        return switch (drop.ending) {
            case DICE -> DICE_LAUNCH + HellionDice.settleMs() + 250;
            case HIGH_CLASS -> jackpot() + 200;
            case DYE -> IGNITE + 1400;
            default -> ARRIVE;
        };
    }

    /**
     * The clock the physical things run on - rods, shards, blocks. The same as {@code t} but for the
     * Flame Dye, which stops it from {@link #FREEZE} to {@link #IGNITE} (never before the reveal, so the
     * rule holds), as Diana's Dye stops time.
     */
    public long phys(long t) {
        if (drop.ending != Ending.DYE || t < FREEZE) return t;
        if (t < IGNITE) return FREEZE;
        return FREEZE + (t - IGNITE);
    }

    /** How frozen the Flame Dye's world is, 0..1 - for the greying. */
    public float frozen(long t) {
        if (drop.ending != Ending.DYE || t < FREEZE) return 0f;
        if (t < IGNITE) return ease((t - FREEZE) / 250f);
        return 1f - ease((t - IGNITE) / 500f);
    }

    /** The Flame Dye's ignition: 0 until it, a burst to 1 and down to a steady 0.4. */
    public float ignite(long t) {
        if (drop.ending != Ending.DYE || t < IGNITE) return 0f;
        float k = (t - IGNITE) / 1000f;
        return 0.4f + 0.6f * (float) Math.exp(-k * 2.2f) * Math.min(1f, k * 8f);
    }

    /**
     * Where the die is in its baked throw at {@code t}: ms after the throw, the same as real time but for
     * the High Class Dice, whose last tip onto its 6 plays in bullet time.
     */
    public long diceMs(long t) {
        long r = t - DICE_LAUNCH;
        if (r <= 0) return 0;
        if (drop.ending != Ending.HIGH_CLASS || r <= SLOW_FROM) return r;
        if (r <= SLOW_FROM + SLOW_REAL) return SLOW_FROM + Math.round((r - SLOW_FROM) * SLOW_RATE);
        return SLOW_TO + (r - SLOW_FROM - SLOW_REAL);
    }

    /** The real time (since the start) at which the die is {@code trackMs} into its throw. */
    public long diceReal(long trackMs) {
        if (drop.ending != Ending.HIGH_CLASS || trackMs <= SLOW_FROM) return DICE_LAUNCH + trackMs;
        if (trackMs <= SLOW_TO) return DICE_LAUNCH + SLOW_FROM + Math.round((trackMs - SLOW_FROM) / SLOW_RATE);
        return DICE_LAUNCH + SLOW_FROM + SLOW_REAL + (trackMs - SLOW_TO);
    }

    /** How deep in bullet time, 0..1, easing in and out - for the camera, the dim and the hush. */
    public float slowMo(long t) {
        if (drop.ending != Ending.HIGH_CLASS) return 0f;
        long a = DICE_LAUNCH + SLOW_FROM, b = a + SLOW_REAL;
        if (t < a - 150 || t > b + 250) return 0f;
        return ease((t - (a - 150)) / 150f) * (1f - ease((t - b) / 250f));
    }

    /** The die lying flat on its 6, in real time: the tip's clack. */
    public long flatAt() {
        java.util.List<Long> b = HellionDice.bounces();
        return diceReal(b.size() > 1 ? b.get(1) : HellionDice.settleMs());
    }

    /** When pip {@code i} (0..5) lights. */
    public long pipAt(int i) {
        return flatAt() + 160 + i * PIP_STEP;
    }

    /** How many of the six pips are lit (the High Class Dice only). */
    public int pipsLit(long t) {
        if (drop.ending != Ending.HIGH_CLASS) return 0;
        int n = 0;
        for (int i = 0; i < 6; i++) if (t >= pipAt(i)) n++;
        return n;
    }

    /** The High Class Dice's jackpot: the sixth pip lit, and everything goes. */
    public long jackpot() {
        return pipAt(5) + 90;
    }

    /**
     * The pillars erupting: the Flame Dye's ignition, or the High Class jackpot - a burst to 1 and down
     * to a steady 0.4; 0 otherwise.
     */
    public float erupt(long t) {
        if (drop.ending == Ending.DYE) return ignite(t);
        if (drop.ending != Ending.HIGH_CLASS || t < jackpot()) return 0f;
        float k = (t - jackpot()) / 1000f;
        return 0.4f + 0.6f * (float) Math.exp(-k * 2.2f) * Math.min(1f, k * 8f);
    }

    /** How many letters of the name are stamped: the jackpots spell it out, the rest show it whole. */
    public int letters(long t) {
        if (!banner()) return 999;
        return Math.max(0, (int) ((t - arrive() - 50) / 45) + 1);
    }

    /** Whether the drop is a die that rolls - it is drawn by its throw, not risen out of the core. */
    public boolean rolls() {
        return drop.ending == Ending.DICE || drop.ending == Ending.HIGH_CLASS;
    }

    /** Everything fades in and out with this. */
    public float master(long t) {
        return ease(t / 200f) * (1f - ease((t - (total() - FADE)) / (float) FADE));
    }

    /** How dark the game behind is. */
    public float dim(long t) {
        return (0.8f + 0.12f * slowMo(t)) * ease(t / 250f) * (1f - ease((t - (total() - FADE)) / (float) FADE));
    }

    // ------------------------------------------------------------------ the shield

    /** Which step of the cycle {@code t} is in, -1 before it starts. */
    public static int step(long t) {
        int k = -1;
        for (int i = 0; i < STEPS.length; i++) if (t >= STEPS[i]) k = i;
        return k;
    }

    /**
     * The shield's attunement now: the fixed cycle before the reveal (Ashen, Spirit, Auric, Crystal,
     * twice), a flicker through all four in the hang - the same for every drop - then the answer.
     */
    public Attunement attunement(long t) {
        if (t >= REVEAL) return drop.lands;
        if (t >= HANG) return Attunement.values()[(int) ((t / 45) % 4)];
        int k = step(t);
        return Attunement.values()[Math.max(0, k) % 4];
    }

    /** A flash on each clang, 1 at the hit and gone in about 150 ms. Same for every drop. */
    public static float clang(long t) {
        int k = step(t);
        if (k < 0 || t >= HANG) return 0f;
        return (float) Math.exp(-(t - STEPS[k]) / 85.0);
    }

    /**
     * How fast the rods spin, as a multiple of vanilla: faster and faster through the whole build-up,
     * hardest at the end (1 + 5x^2, so 6x vanilla at the top - the user found 10x too fast), stopped
     * dead at the reveal. They keep their rings - the user: they should not close in, just spin faster.
     */
    public static float spin(long t) {
        if (t >= REVEAL) return 0f;
        float x = t / (float) REVEAL;
        return 1f + SPIN_TOP * x * x;
    }

    /** How much faster than vanilla the rods spin at the top, less one. */
    public static final float SPIN_TOP = 5f;

    /** The blaze appearing: 0..1. */
    public static float appear(long t) {
        return backOut(Math.min(1f, t / (float) APPEAR));
    }

    // ------------------------------------------------------------------ after the reveal

    /** 0..1 through a bust's burn-out, -1 before the reveal or on a win. */
    public float burnOut(long t) {
        if (drop.win() || t < REVEAL) return -1f;
        return Math.min(1f, (t - REVEAL) / (float) (BUST_END - FADE - REVEAL + 100));
    }

    /** 0..1 through a win's shatter (the shield, the rods blown out, the head going), -1 before. */
    public float shatter(long t) {
        if (!drop.win() || t < REVEAL) return -1f;
        return Math.min(1f, (phys(t) - REVEAL) / 700f);
    }

    /** The drop coming out of the core, 0..1. The dice roll out instead (HellionDice). */
    public float itemRise(long t) {
        long from = drop.ending == Ending.DYE ? IGNITE : REVEAL + 120;
        if (!drop.win() || rolls() || t < from) return 0f;
        return backOut(Math.min(1f, (t - from) / (float) (arrive() - from)));
    }

    public float nameAlpha(long t) {
        return drop.win() ? ease((t - arrive() - 50) / 300f) : 0f;
    }

    /** When the price starts counting up. */
    public long countStart() {
        return arrive() + 650;
    }

    /** "PRAY TO RNGESUS DROP!" - Hypixel's own words for its rarest drops - stamped on the jackpots. */
    public boolean banner() {
        return drop.ending == Ending.HIGH_CLASS || drop.ending == Ending.DYE;
    }

    /** The banner's stamp: 0 before, slams in from big. */
    public float bannerStamp(long t) {
        if (!banner() || t < arrive() - 150) return 0f;
        return Math.min(1f, (t - arrive() + 150) / 180f);
    }

    public static final long COUNT_MS = 1000;

    private long price = -1;

    /** The drop's price, fixed by the screen when the count starts; -1 until then (or none). */
    public long price() { return price; }

    public void price(long coins) { this.price = coins; }

    private String priceSource;

    /** The price and where it came from ("bazaar instant sell", "lowest BIN"), shown small under it (the review, A6). */
    public void price(long coins, String source) {
        this.price = coins;
        this.priceSource = source;
    }

    public String priceSource() { return priceSource; }

    public float lineAlpha(long t) {
        return drop.win() ? ease((t - arrive() - 400) / 300f) : 0f;
    }

    /** The two side pillars: how tall, 0..1.4 (an eruption on a win, guttering on a bust). */
    public float pillar(long t) {
        float base = ease(Math.min(1f, (t - 100) / 500f));
        float build = 0.15f * ease(t / (float) REVEAL);
        if (t < REVEAL) return base * (0.75f + build) + 0.12f * clang(t);
        float k = (phys(t) - REVEAL) / 600f;
        if (!drop.win()) return Math.max(0f, (0.9f) * (1f - ease(Math.min(1f, k))));
        return 0.9f + 0.5f * (float) Math.exp(-k * 1.5f) * Math.min(1f, k * 6f) + 0.1f;
    }

    // ------------------------------------------------------------------ easing

    public static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    public static float backOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float c = 1.6f, u = t - 1f;
        return 1f + (c + 1f) * u * u * u + c * u * u;
    }
}
