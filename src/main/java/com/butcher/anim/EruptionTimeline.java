package com.butcher.anim;

/**
 * "Eruption" - lava fishing's animation (the rare Crimson Isle sea creatures). Pure: the whole schedule for
 * one kill from its creature, its drop and a seed; the scene and the screen only ask it what is happening now.
 *
 * <b>The tell is temperature.</b> The creature sinks into a lava pool, a crust closes over it, a vent rises,
 * and its heat breathes up and down a fixed scale - black, dull red, orange, yellow, white-hot - faster and
 * faster, the same on every run; at {@link #REVEAL} it lands on the drop's value tier: black is a bust (it
 * cools), and the hotter the landing the more the drop is worth. The creature is the kill's own, which the
 * player already knows, so it may change everything before the reveal; the drop may change nothing
 * (tools/EruptionTest hashes every frame and sample, per creature).
 *
 * Researched 2026-10-05 (hypixelskyblock.minecraft.wiki, the NEU repo, the user's own game logs; prices from
 * the bazaar and Coflnet that day). Decided with the user: the guaranteed drops (Jawbus's Magma Lord
 * Fragment, the Scuttler's crab stick) are not the reveal - a bust shows them under the cooled vent;
 * loot-shared kills play like any other; the Carmine Dye gets the grandest ending (Kuudra).
 */
public final class EruptionTimeline {

    /** The heat scale, coldest first: the tell. Index = value tier. */
    public enum Heat {
        BLACK(0x1C1A1C), RED(0x8A1A0C), ORANGE(0xE2620F), YELLOW(0xFFC93A), WHITE(0xFFF4DC);

        public final int rgb;

        Heat(int rgb) {
            this.rgb = rgb;
        }
    }

    /** The Carmine Dye's own colour, #960018 (the wiki). */
    public static final int CARMINE = 0x960018;

    public enum Model { GOLEM, ELDER_GUARDIAN, SQUID, HEAD }

    /** The rare lava creatures. {@code skin} is the NEU bestiary head (lava_heads.json), {@code model} what is drawn. */
    public enum Creature {
        JAWBUS("Lord Jawbus", Model.GOLEM, 100e6, 0xFF55FF),
        THUNDER("Thunder", Model.ELDER_GUARDIAN, 35e6, 0xFFAA00),
        RAGNAROK("Ragnarok", Model.HEAD, 125e6, 0xFF55FF),
        SCUTTLER("Fiery Scuttler", Model.HEAD, 10e6, 0xFFAA00),
        PLHLEGBLAST("Plhlegblast", Model.SQUID, 500e6, 0xFF55FF);

        public final String title;
        public final Model model;
        public final double maxHp;
        /** Its rarity colour: Mythic pink, Legendary gold. */
        public final int colour;

        Creature(String title, Model model, double maxHp, int colour) {
            this.title = title;
            this.model = model;
            this.maxHp = maxHp;
            this.colour = colour;
        }
    }

    public enum Ending { BUST, PLAIN, PINCER, FLASH, FORGE, VIAL, SHELL, END_TIMES, KUUDRA }

    /**
     * What the animation plays: the drops worth it (prices 2026-10-05), each with its heat, its ending, its
     * creature (null: any), its price ID and how it is drawn (a NEU head skin, else a vanilla item icon).
     */
    public enum Drop {
        BUST("", Heat.BLACK, Ending.BUST, null, null, null, false, 0, 0xAAAAAA),
        CHAIN("Chain of the End Times", Heat.RED, Ending.PLAIN, Creature.RAGNAROK, "CHAIN_END_TIMES", "CHAIN_END_TIMES", true, 100, 0x5555FF),
        PINCER("Severed Pincer", Heat.RED, Ending.PINCER, Creature.SCUTTLER, "SEVERED_PINCER", "SEVERED_PINCER", true, 500, 0x5555FF),
        FLASH("Flash I", Heat.ORANGE, Ending.FLASH, Creature.THUNDER, "ENCHANTMENT_ULTIMATE_FLASH_1", "enchanted_book", false, 67, 0xFF55FF),
        BRIMSTONE("Brimstone Handle", Heat.ORANGE, Ending.FORGE, Creature.RAGNAROK, "BRIMSTONE_HANDLE", "stick", false, 200, 0x5555FF),
        VIAL("Radioactive Vial", Heat.YELLOW, Ending.VIAL, Creature.JAWBUS, "RADIOACTIVE_VIAL", "RADIOACTIVE_VIAL", true, 200, 0xFF55FF),
        SHELL("Scuttler Shell", Heat.YELLOW, Ending.SHELL, Creature.SCUTTLER, "SCUTTLER_SHELL", "SCUTTLER_SHELL", true, 1000, 0xFF55FF),
        BURNT_TEXTS("Burnt Texts", Heat.WHITE, Ending.END_TIMES, Creature.RAGNAROK, "BURNT_TEXTS", "BURNT_TEXTS", true, 400, 0xFF55FF),
        CARMINE("Carmine Dye", Heat.WHITE, Ending.KUUDRA, null, "DYE_CARMINE", "DYE_CARMINE", true, 50_000, 0xFFAA00);

        public final String title;
        public final Heat heat;
        public final Ending ending;
        /** The only creature that drops it, or null for any (the dye). */
        public final Creature from;
        public final String id;
        /** A head's NEU item ID when {@link #head}, else a vanilla item icon (textures/item/...). */
        public final String art;
        public final boolean head;
        /** About one in this many kills of its creature (the wiki's chance, before Magic Find). */
        public final int oneIn;
        /** Its name's colour: the item's rarity. */
        public final int colour;

        Drop(String title, Heat heat, Ending ending, Creature from, String id, String art, boolean head, int oneIn, int colour) {
            this.title = title;
            this.heat = heat;
            this.ending = ending;
            this.from = from;
            this.id = id;
            this.art = art;
            this.head = head;
            this.oneIn = oneIn;
            this.colour = colour;
        }

        public boolean win() {
            return this != BUST;
        }

        /** Whether {@code c} can drop it. */
        public boolean from(Creature c) {
            return this == BUST || from == null || from == c;
        }
    }

    /** What every kill of a creature drops anyway, shown under a bust (not the reveal - the user's call). */
    public static String guaranteed(Creature c) {
        return switch (c) {
            case JAWBUS -> "Magma Lord Fragment";
            case SCUTTLER -> "Scorched Crab Stick";
            default -> null;
        };
    }

    /**
     * The guaranteed drop's icon in Hypixel's own resource pack (NEU: both are paper in vanilla, with an ItemModel in
     * hypixel_skyblock). Drawn only when that pack is loaded; without it the bust shows the name and price alone.
     */
    public static String guaranteedIcon(Creature c) {
        return switch (c) {
            case JAWBUS -> "textures/item/uncategorized/magma_lord_fragment.png";
            case SCUTTLER -> "textures/item/fishing/misc/scorched_crab_stick.png";
            default -> null;
        };
    }

    public static String guaranteedId(Creature c) {
        return switch (c) {
            case JAWBUS -> "MAGMA_LORD_FRAGMENT";
            case SCUTTLER -> "SCORCHED_CRAB_STICK";
            default -> null;
        };
    }

    // ------------------------------------------------------------------ the build-up, ms (every run)

    /** The creature is fully up; it starts to sink. */
    public static final long SINK_FROM = 350, SINK_TO = 1700;
    /** The crust closes over the pool from the edges. */
    public static final long CRUST_FROM = 1450, CRUST_TO = 2150;
    /** The vent pushes up through the crust. */
    public static final long VENT_FROM = 1800, VENT_TO = 2500;
    /** The breathing: one heat step each, shorter each time. */
    public static final long BREATH_FROM = 2350;
    public static final long[] STEPS = steps(BREATH_FROM, 330f, 0.935f, 4300);
    /** The hold: the cone shudders, the heat flickers through every level. */
    public static final long HANG = 4300;
    public static final long REVEAL = 4800;
    /** The fade at the very end of every run. */
    public static final long FADE = 400;

    /** Breath step start times from {@code from}, {@code first} ms long, each {@code k} times the last, until {@code end}. */
    static long[] steps(long from, float first, float k, long end) {
        java.util.List<Long> out = new java.util.ArrayList<>();
        float d = first;
        long t = from;
        while (t + d * 0.6f < end) {
            out.add(t);
            t += Math.round(d);
            d *= k;
        }
        long[] a = new long[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    /** The level each breath step climbs or falls to: up the scale and down again, and again. */
    public static int stepLevel(int i) {
        int p = i % 8;
        return p <= 4 ? p : 8 - p;
    }

    private final Creature creature;
    private final Drop drop;
    private final long seed;

    public EruptionTimeline(Creature creature, Drop drop, long seed) {
        this.creature = creature;
        this.drop = drop == null ? Drop.BUST : drop;
        this.seed = seed;
    }

    public Creature creature() { return creature; }
    public Drop drop() { return drop; }
    public Ending ending() { return drop.ending; }
    public long seed() { return seed; }

    // ------------------------------------------------------------------ the endings' beats, from the reveal

    public static final long R = REVEAL;
    /** Every win but the End Times and Kuudra erupts at once. */
    public static final long ERUPT = R + 150;
    // Flash: the bolt hits the vent first.
    public static final long F_BOLT = R + 120;
    // The forge: three hammer blows.
    public static final long[] FORGE_BLOWS = {R + 1600, R + 2000, R + 2400};
    // The vial: the green spreading, the golem rising behind, the vial freed.
    // "Meltdown": the lake goes green, Jawbus rises with the vial in his chest, it goes critical - six beats, faster -
    // and detonates (V_FREE): he is blown apart, a mushroom cloud stands up behind, the vial comes down out of it.
    public static final long V_GREEN = R + 200, V_GREEN_TO = R + 1600, V_GOLEM = R + 900, V_GOLEM_UP = R + 1900, V_FREE = R + 3300,
            V_SINK = V_FREE + 1, V_SINK_TO = V_FREE + 80, V_DESCEND = V_FREE + 900;
    /** The vial throbbing in Jawbus's chest before it detonates, faster each time. */
    public static final long[] V_PULSES = {R + 1950, R + 2300, R + 2600, R + 2850, R + 3050, R + 3190};
    // The shell: up, the crab peeks, the crab runs.
    public static final long S_UP = R + 300, S_UP_TO = R + 1500, S_PEEK = R + 1700, S_RUN = R + 2200, S_GONE = R + 2900;
    // The pincer: it snaps up out of the lava and clacks twice.
    public static final long P_SNAP = R + 450, P_CLACKS0 = R + 900, P_CLACKS1 = R + 1250;
    // The end times: the sky darkens, three bolts, the ground heats, the book.
    public static final long T_DARK = R, T_BOLTS0 = R + 700, T_BOLTS1 = R + 1050, T_BOLTS2 = R + 1400,
            T_ERUPT = R + 1450, T_HEAT = R + 1500, T_HEAT_TO = R + 3500, T_BOOK = R + 3000, T_CRACK = R + 4200;
    // Kuudra: the stall, time stops, the carmine, the swell, the tentacles, the eye, the offering.
    public static final long K_STALL = R, K_FREEZE = R + 600, K_CARMINE = R + 800, K_CARMINE_TO = R + 2400,
            K_RESUME = R + 2400, K_SWELL = R + 2400, K_SWELL_TO = R + 3600, K_TENTACLES = R + 3200,
            K_TENTACLES_TO = R + 4800, K_EYE = R + 5000, K_EYE_TO = R + 5800, K_RISE = R + 6000, K_RISE_TO = R + 7400;

    /** A bust's last beat: the crater block slumps into the vent with a puff of steam (the review, E9). */
    public static final long SLUMP = R + 520;

    /** The slump, 0..1 over 220 ms; 0 on a win. */
    public float slump(long t) {
        if (drop.win()) return 0f;
        return ease((t - SLUMP) / 220f);
    }

    /** When the drop is shown: name, odds and price follow it. */
    public long arrive() {
        return switch (drop.ending) {
            case BUST -> R + 700;
            case PLAIN -> R + 1900;
            case PINCER -> R + 2300;
            case FLASH -> R + 2300;
            case FORGE -> R + 3000;
            case VIAL -> R + 4700;
            case SHELL -> R + 3500;
            case END_TIMES -> R + 4600;
            case KUUDRA -> R + 8200;
        };
    }

    public long total() {
        return switch (drop.ending) {
            // A second shorter than it was: it sat for 2.5 s after the vent went dark (the review, E9).
            case BUST -> guaranteed(creature) != null ? R + 1800 : R + 1100;
            case PLAIN, PINCER -> arrive() + 3200;
            case FLASH -> arrive() + 3500;
            case FORGE -> arrive() + 3800;
            case VIAL, SHELL -> arrive() + 5000;
            case END_TIMES -> arrive() + 6000;
            case KUUDRA -> arrive() + 8000;
        };
    }

    /** The drop's own climb out of the lava, 0..1: when it starts and when it is fully up. */
    public long riseFrom() {
        return switch (drop.ending) {
            case BUST -> Long.MAX_VALUE;
            case PLAIN, FLASH -> R + 500;
            case PINCER -> R + 1400;
            case FORGE -> R + 400;
            case VIAL -> V_FREE;
            case SHELL -> S_UP;
            case END_TIMES -> T_BOOK;
            case KUUDRA -> K_RISE;
        };
    }

    public long riseTo() {
        return switch (drop.ending) {
            case BUST -> Long.MAX_VALUE;
            case PLAIN, FLASH -> R + 1300;
            case PINCER -> R + 1900;
            case FORGE -> R + 1400;
            case VIAL -> V_FREE + 500;
            case SHELL -> S_UP_TO;
            case END_TIMES -> T_BOOK + 1000;
            case KUUDRA -> K_RISE_TO;
        };
    }

    /** The quench: the drop goes from glowing heat to its own colours, texel by texel. */
    public long quenchFrom() {
        return switch (drop.ending) {
            case BUST -> Long.MAX_VALUE;
            case PLAIN -> R + 1300;
            case PINCER -> R + 1900;
            case FLASH -> R + 1400;
            case FORGE -> R + 2500;
            case VIAL -> R + 4150;
            case SHELL -> S_GONE;
            case END_TIMES -> R + 4000;
            case KUUDRA -> R + 7400;
        };
    }

    public long quenchTo() {
        return quenchFrom() == Long.MAX_VALUE ? Long.MAX_VALUE : quenchFrom() + (drop.ending == Ending.KUUDRA ? 800 : 650);
    }

    // ------------------------------------------------------------------ the clock

    /** The physical clock - lava, bubbles, embers. It stops only for Kuudra (time stops while the carmine spreads). */
    public long phys(long t) {
        if (drop.ending != Ending.KUUDRA || t <= K_FREEZE) return t;
        return t < K_RESUME ? K_FREEZE : K_FREEZE + (t - K_RESUME);
    }

    /** How stopped time is, 0..1 (Kuudra only): for the cold grey the frozen world takes on. */
    public float frozen(long t) {
        if (drop.ending != Ending.KUUDRA) return 0f;
        return ease((t - K_FREEZE) / 300f) * (1f - ease((t - K_RESUME) / 400f));
    }

    // ------------------------------------------------------------------ the build-up

    /** The creature's sinking, 0 up to 1 gone under. */
    public static float sink(long t) {
        return ease((t - SINK_FROM) / (float) (SINK_TO - SINK_FROM));
    }

    /** The crust's reach, 0 (open lava) to 1 (closed but for the ring round the vent). */
    public static float crust(long t) {
        return ease((t - CRUST_FROM) / (float) (CRUST_TO - CRUST_FROM));
    }

    /** The vent cone pushing up, 0..1 (back-out, it bulges and settles). */
    public static float vent(long t) {
        return backOut((t - VENT_FROM) / (float) (VENT_TO - VENT_FROM));
    }

    /** Which breath step {@code t} is in, -1 before. */
    public static int step(long t) {
        int k = -1;
        for (int i = 0; i < STEPS.length; i++) if (t >= STEPS[i]) k = i;
        return k;
    }

    /** A pop at each breath step: 1 at the step and gone in ~200 ms. Same for every drop. */
    public static float pop(long t) {
        int k = step(t);
        if (k < 0 || t >= HANG) return 0f;
        return (float) Math.exp(-(t - STEPS[k]) / 110.0);
    }

    /** The hang's fixed flicker: a level per 55 ms, a sequence that visits every level and favours none. */
    private static final int[] FLICKER = {3, 0, 4, 1, 2, 4, 0, 3, 1, 4, 2, 0, 3, 1};

    /**
     * The heat now, continuous 0 (black) .. 4 (white): the breathing before the hang, the flicker in it, the
     * landing after the reveal. Kuudra's stall holds it at white and shakes it; its carmine is {@link #carmine}.
     */
    public float heat(long t) {
        if (t < BREATH_FROM) return 0.6f * ease((t - VENT_FROM) / 700f);
        if (t < HANG) {
            int k = step(t);
            if (k < 0) return 0.6f;
            float from = k == 0 ? 0.6f : stepLevel(k - 1), to = stepLevel(k);
            long len = k + 1 < STEPS.length ? STEPS[k + 1] - STEPS[k] : HANG - STEPS[k];
            return from + (to - from) * ease((t - STEPS[k]) / (0.6f * len));
        }
        if (t < REVEAL) return FLICKER[(int) ((t - HANG) / 55) % FLICKER.length];
        float land = drop.heat.ordinal();
        float prev = FLICKER[(int) ((REVEAL - 1 - HANG) / 55) % FLICKER.length];
        float h = prev + (land - prev) * ease((t - REVEAL) / 120f);
        if (drop.ending == Ending.BUST) return h * (1f - ease((t - REVEAL - 120) / 700f));
        return h;
    }

    /** The heat as a colour, blended between the two levels round it. */
    public int heatColour(long t) {
        float h = Math.max(0f, Math.min(4f, heat(t)));
        int lo = (int) Math.floor(h), hi = Math.min(4, lo + 1);
        return mix(Heat.values()[lo].rgb, Heat.values()[hi].rgb, h - lo);
    }

    /** How far the Carmine Dye's colour has taken the lava, 0..1 (its reach, from the vent out). */
    public float carmine(long t) {
        if (drop.ending != Ending.KUUDRA) return 0f;
        return ease((t - K_CARMINE) / (float) (K_CARMINE_TO - K_CARMINE));
    }

    /** The cone's shudder in the hang (and Kuudra's stall), 0..1. Same for every drop before the reveal. */
    public float shudder(long t) {
        if (t >= HANG && t < REVEAL) return ease((t - HANG) / 400f);
        if (drop.ending == Ending.KUUDRA && t >= REVEAL && t < K_FREEZE) return 1f;
        return 0f;
    }

    // ------------------------------------------------------------------ after the reveal

    /** A bust's cooling, 0..1 (the crust seals and the cone slumps). -1 on a win or before. */
    public float cool(long t) {
        if (drop.win() || t < REVEAL) return -1f;
        return ease((t - REVEAL) / 900f);
    }

    /** The eruption, 0..1 over its first 1.4 s; -1 before it (or never: Kuudra). */
    public float erupt(long t) {
        long from = switch (drop.ending) {
            case BUST, KUUDRA -> Long.MAX_VALUE;
            case END_TIMES -> T_ERUPT;
            default -> ERUPT;
        };
        if (t < from) return -1f;
        return Math.min(1f, (t - from) / 1400f);
    }

    /** When the eruption went off, or -1. */
    public long eruptAt() {
        return switch (drop.ending) {
            case BUST, KUUDRA -> -1;
            case END_TIMES -> T_ERUPT;
            default -> ERUPT;
        };
    }

    public float rise(long t) {
        if (t < riseFrom()) return 0f;
        return backOut((t - riseFrom()) / (float) (riseTo() - riseFrom()));
    }

    /** 0 glowing hot .. 1 its own colours. */
    public float quench(long t) {
        if (t < quenchFrom()) return 0f;
        return Math.min(1f, (t - quenchFrom()) / (float) (quenchTo() - quenchFrom()));
    }

    /** The End Times' ground heating through lime, yellow, orange, red: 0..4 (Ragnarok's own phase two). */
    public float terracotta(long t) {
        if (drop.ending != Ending.END_TIMES) return 0f;
        return 4f * Math.max(0f, Math.min(1f, (t - T_HEAT) / (float) (T_HEAT_TO - T_HEAT)));
    }

    /** The End Times' sky, 0..1 dark. */
    public float dark(long t) {
        if (drop.ending != Ending.END_TIMES) return 0f;
        return ease((t - T_DARK) / 600f) * (1f - ease((t - (total() - FADE)) / (float) FADE));
    }

    /** A bolt's flash, 1 at the strike and gone in ~180 ms; the Flash's one and the End Times' three. */
    public float bolt(long t) {
        long[] at = switch (drop.ending) {
            case FLASH -> new long[]{F_BOLT};
            case END_TIMES -> new long[]{T_BOLTS0, T_BOLTS1, T_BOLTS2};
            default -> new long[0];
        };
        float b = 0f;
        for (long a : at) if (t >= a) b = Math.max(b, (float) Math.exp(-(t - a) / 90.0));
        return b;
    }

    /** The radioactive green taking the lava, 0..1. */
    public float green(long t) {
        if (drop.ending != Ending.VIAL) return 0f;
        return ease((t - V_GREEN) / (float) (V_GREEN_TO - V_GREEN));
    }

    /** Jawbus rising behind the vent with the vial in its chest, 0..1. */
    public float golem(long t) {
        if (drop.ending != Ending.VIAL) return 0f;
        return backOut((t - V_GOLEM) / (float) (V_GOLEM_UP - V_GOLEM));
    }

    /** The vial's throb in the chest, 0..1 - a beat at each of V_PULSES. */
    public float vialPulse(long t) {
        if (drop.ending != Ending.VIAL) return 0f;
        float p = 0f;
        for (long at : V_PULSES) if (t >= at) p = Math.max(p, (float) Math.exp(-(t - at) / 110.0));
        return t >= V_FREE ? 0f : p;
    }

    /** The radiation burst as the vial breaks free: 0 at the burst .. 1 spent; -1 before. */
    public float vialBurst(long t) {
        if (drop.ending != Ending.VIAL || t < V_FREE) return -1f;
        return Math.min(1f, (t - V_FREE) / 1100f);
    }

    /** The mushroom cloud: 0 at the detonation .. 1 fully risen; -1 before. */
    public float cloud(long t) {
        if (drop.ending != Ending.VIAL || t < V_FREE) return -1f;
        float u = Math.min(1f, (t - V_FREE) / 1700f);
        return 1f - (1f - u) * (1f - u) * (1f - u);
    }

    /** How critical the vial is before it goes off, 0..1: rising through the beats, for the screen's green edges. */
    public float critical(long t) {
        if (drop.ending != Ending.VIAL || t >= V_FREE + 600) return 0f;
        float build = ease((t - V_GOLEM_UP) / (float) (V_FREE - V_GOLEM_UP));
        float after = t >= V_FREE ? 1f - (t - V_FREE) / 600f : 1f;
        return Math.min(1f, (0.35f * build + 0.65f * vialPulse(t) + (t >= V_FREE ? 1f : 0f)) * after);
    }

    /** The column of green light the freed vial stands in, 0..1. */
    public float vialColumn(long t) {
        if (drop.ending != Ending.VIAL || t < V_FREE) return 0f;
        return ease((t - V_FREE) / 250f) * (1f - 0.45f * ease((t - arrive() - 600) / 1500f));
    }

    /** The swell of the carmine sea up the screen, 0..1. */
    public float swell(long t) {
        if (drop.ending != Ending.KUUDRA) return 0f;
        return ease((t - K_SWELL) / (float) (K_SWELL_TO - K_SWELL));
    }

    /** Tentacle {@code i} of {@code n} bursting out, 0..1 - staggered across the window. */
    public float tentacle(long t, int i, int n) {
        if (drop.ending != Ending.KUUDRA) return 0f;
        long span = K_TENTACLES_TO - K_TENTACLES - 700;
        long from = K_TENTACLES + span * i / Math.max(1, n - 1);
        return backOut((t - from) / 700f);
    }

    /** Kuudra's eye opening beneath the surface, 0..1. */
    public float eye(long t) {
        if (drop.ending != Ending.KUUDRA) return 0f;
        return ease((t - K_EYE) / (float) (K_EYE_TO - K_EYE));
    }

    // ------------------------------------------------------------------ the showcase

    /** The banner: "RARE DROP!" on the big ones, Hypixel's "WOW!" on the dye. */
    public boolean banner() {
        return switch (drop.ending) {
            case VIAL, SHELL, END_TIMES, KUUDRA -> true;
            default -> false;
        };
    }

    public String bannerText() {
        return drop.ending == Ending.KUUDRA ? "WOW!" : "RARE DROP!";
    }

    public int bannerColour() {
        return drop.ending == Ending.KUUDRA ? 0xFF55FF : 0xFFAA00;
    }

    public float bannerStamp(long t) {
        if (!banner() || t < arrive() - 150) return 0f;
        return Math.min(1f, (t - arrive() + 150) / 180f);
    }

    /** Letters of the name stamped: the big ones spell it out. */
    public int letters(long t) {
        if (!banner()) return 999;
        return Math.max(0, (int) ((t - arrive() - 50) / 45) + 1);
    }

    public float nameAlpha(long t) {
        if (!drop.win()) return guaranteed(creature) != null ? ease((t - REVEAL - 700) / 300f) : 0f;
        return ease((t - arrive() - 50) / 300f);
    }

    public float lineAlpha(long t) {
        return drop.win() ? ease((t - arrive() - 400) / 300f) : 0f;
    }

    public long countStart() {
        return drop.win() ? arrive() + 650 : REVEAL + 900;
    }

    public static final long COUNT_MS = 1000;

    private long price = -1;

    public long price() { return price; }

    public void price(long coins) { this.price = coins; }

    private String priceSource;

    /** The price and where it came from ("bazaar instant sell", "lowest BIN"), shown small under it (the review, A6). */
    public void price(long coins, String source) {
        this.price = coins;
        this.priceSource = source;
    }

    public String priceSource() { return priceSource; }

    /** Everything fades in and out with this. */
    public float master(long t) {
        return ease(t / 200f) * (1f - ease((t - (total() - FADE)) / (float) FADE));
    }

    /** How dark the game behind is. */
    public float dim(long t) {
        return 0.8f * ease(t / 250f) * (1f - ease((t - (total() - FADE)) / (float) FADE));
    }

    // ------------------------------------------------------------------ helpers

    public static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    public static float backOut(float t) {
        // Exactly 0 before it starts: the polynomial gives a float crumb there, and "> 0" is how a beat is tested.
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float c = 1.6f, u = t - 1f;
        return 1f + (c + 1f) * u * u * u + c * u * u;
    }

    public static int mix(int a, int b, float k) {
        k = Math.max(0f, Math.min(1f, k));
        int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * k);
        int g = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * k);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * k);
        return r << 16 | g << 8 | bl;
    }
}
