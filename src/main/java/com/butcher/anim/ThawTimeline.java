package com.butcher.anim;

import com.butcher.detect.vanguard.CorpseHaul;
import com.butcher.detect.vanguard.VanguardLoot.Tier;

import java.util.ArrayList;
import java.util.List;

/**
 * When everything in the Thaw happens. Worked out once, up front, from the haul.
 *
 * <h2>Why a separate class</h2>
 *
 * The Thaw has five endings, five to nine strikes, and a
 * climbing flare on its last strike, and every one of those is a number that wants checking.
 * Held here, with nothing from Minecraft in it, the whole schedule can be run for every haul
 * shape offline - {@code tools/ThawTest} - and the screen is left with nothing to do but ask
 * "what is happening at this moment" and draw it.
 *
 * <h2>The flare never lies</h2>
 *
 * The first version let ordinary strikes flare gold now and then, so gold would mean "maybe".
 * In play that did the opposite of what it was for: a signal that sometimes lies stops meaning
 * anything, and once a player has been teased a few times, gold stops being exciting. So every
 * flare is now the honest colour of the drop its strike produces - blue for a common, violet
 * for an uncommon, gold for a legendary.
 *
 * The tension moved to the last strike, which does not flare its own colour. It
 * <b>cycles</b> - blue, violet, gold, blue, violet, gold... - three times round, fading from
 * each colour into the next and speeding up as it goes, before it lands on the truth. The
 * cycle is the same length and the same colours whatever the drop, so nothing before the
 * landing gives the ending away. (The first version
 * climbed blue, violet, gold one rung at a time and stopped at the truth; a common stopped
 * after one rung, so how long the climb ran was itself the answer.) And because drops come
 * out worst first, the earlier honest flares build the run up without giving the end away.
 *
 * <h2>Every run strikes at the same pace</h2>
 *
 * An earlier version played an all-commons corpse at under half speed, so losses were over
 * quickly. But the pace was set from the start, which meant the very first strike already
 * told the player how the run would end - a quick pick meant nothing was coming. Now every
 * run strikes at the same, winning pace, and only the ending itself - after the last strike
 * has landed - is shorter for a loss than for a win.
 */
public final class ThawTimeline {

    /** How a run ends. Decided by the best drop in the haul. */
    public enum Finale {
        /** Nothing but commons. The ice goes dull and the run ends on a thud. */
        CRASH,
        /** Something uncommon. It pops out and gets a moment, but no fireworks. */
        REVEAL,
        /** A legendary. The block detonates and the item takes the screen. */
        EXPLODE,
        /**
         * The Shattered Locket. Its own ending, well above EXPLODE: slow motion, the locket
         * coming out in pieces and snapping together, coins, its live price.
         */
        LOCKET,
        /**
         * The Frostbitten Dye. Everything drains to grey and freezes, a heartbeat, a pink
         * crack, and then the colour floods back as paint.
         */
        SPECIAL
    }

    /** The colour the ice glows before a strike. Always the truth. */
    public enum Flare { BLUE, VIOLET, GOLD }

    /**
     * One strike: what comes out, the colour it flares, and when.
     *
     * Times are milliseconds from the moment the screen opened. Only the last strike cycles:
     * {@code climbStart} is when its opening glow begins and {@code truth} the colour it lands
     * on - 1 blue, 2 violet, 3 gold. Both are 0 on every other strike.
     */
    public record Strike(int index, CorpseHaul.Drop drop, Flare flare,
                         long start, long windup, long fall, long impact, boolean last,
                         long climbStart, int truth) {
        /** When the pick hits the ice and the item comes out. */
        public long lands() { return start + windup + fall; }
        public long end() { return lands() + impact; }
    }

    // ------------------------------------------------------------------ the numbers
    // Multiplied by the player's speed setting, and by nothing else.

    public static final long INTRO = 700;
    /**
     * Pick drawn back while the ice flares. A real pick is heavy, and heaving it back is
     * where the tension lives - a short wind-up read as tapping at the ice.
     */
    public static final long WINDUP = 380;
    /** The pick coming down. Still the fast part - it is the release, not the build. */
    public static final long FALL = 125;
    /** From contact to the next wind-up: the ring of the steel, the ice crumbling, the item leaving. */
    public static final long IMPACT = 380;

    /** The last strike's opening glow, blue, before the colours start to cycle. */
    public static final long CLIMB_OPEN = 340;
    /**
     * The colours the last strike cycles through before it lands, in order, as colour ids:
     * 1 blue, 2 violet, 3 gold. Three times round from the opening blue, and back to blue at
     * the end - so a legendary's landing is a jump from blue to gold, the most visible change
     * the cycle can make, and a common's is the cycle simply stopping where it is.
     */
    static final int[] CYCLE = {2, 3, 1, 2, 3, 1, 2, 3, 1};
    /**
     * How long each colour of the cycle lasts. Shorter and shorter, so it builds like a
     * roulette wheel winding up - and the same for every drop, so the pace gives nothing away.
     */
    static final long[] CYCLE_STEPS = {380, 350, 320, 285, 250, 220, 190, 165, 145};
    /** The held breath on the true colour, after the landing and before the pick comes down. */
    public static final long CLIMB_BEAT = 420;
    /** The step index of the landing, the step after the whole cycle. */
    public static final int LANDING = CYCLE.length + 1;

    /** How long the ending itself plays. */
    public static long finaleLength(Finale f) {
        return switch (f) {
            case CRASH -> 600;
            case REVEAL -> 1500;
            case EXPLODE -> 2500;
            case LOCKET -> 7200;
            // The drain, the heartbeat, the crack - then the flood.
            case SPECIAL -> 9000;
        };
    }

    /**
     * How much of the Dye's ending is spent grey and frozen before the paint bursts:
     * the slow-motion impact, the colour draining, a heartbeat, silence, and the pink
     * crack spreading through the ice.
     */
    public static final long SPECIAL_DRAIN = 3200;

    /** How long the special endings run in slow motion from the moment of impact. */
    public static final long SLOW_MOTION = 1100;

    /** The haul sitting in its tray after the ending, with the streak line under it. */
    public static long outroLength(Finale f) {
        return switch (f) {
            case CRASH -> 700;
            case REVEAL -> 1300;
            case EXPLODE -> 1700;
            case LOCKET, SPECIAL -> 2000;
        };
    }

    public static final long FADE_OUT = 420;

    /** How long an item takes to fly from the ice to its slot. */
    public static final long FLIGHT = 520;

    // ------------------------------------------------------------------ the schedule

    private final List<Strike> strikes = new ArrayList<>();
    private final Finale finale;
    private final long finaleStart, outroStart, fadeStart, total;
    /** The player's speed setting as a duration multiplier: 2 is half speed. */
    private final float pace;

    public ThawTimeline(CorpseHaul haul) {
        this(haul, 100);
    }

    /**
     * @param speedPercent the menu's speed setting; 100 is the reference pace, 200 twice as
     *                     fast. Stretches every part of the run evenly, so changing it
     *                     changes how fast the Thaw plays, never its shape.
     */
    public ThawTimeline(CorpseHaul haul, int speedPercent) {
        this.pace = 100f / Math.max(25, Math.min(400, speedPercent));
        List<CorpseHaul.Drop> order = haul.revealOrder();
        this.finale = finaleFor(haul);

        int n = order.size();
        long t = scaled(INTRO);
        for (int i = 0; i < n; i++) {
            boolean last = i == n - 1;
            // Each strike a little slower than the one before, the way the cleaver's chops
            // stretch - the run builds rather than repeats. Averages about 1.
            float stretch = n <= 1 ? 1f : 0.86f + 0.30f * i / (float) (n - 1);
            long raise = scaled((long) (WINDUP * stretch));
            Tier tier = order.get(i).tier();
            int truth = last ? truthFor(tier) : 0;
            long climb = 0;
            if (last) {
                climb = scaled(CLIMB_OPEN) + scaled(CLIMB_BEAT);
                for (long step : CYCLE_STEPS) climb += scaled(step);
            }
            long fall = scaled(FALL);
            long impact = scaled((long) (IMPACT * stretch));
            strikes.add(new Strike(i, order.get(i), flareOf(tier), t, raise + climb, fall, impact, last,
                    last ? t + raise : 0, truth));
            t += raise + climb + fall + impact;
        }
        // The ending starts the moment the last pick lands, not after its impact phase: the
        // last strike's payoff IS the ending, and a gap there reads as the animation stalling.
        long lastLand = strikes.isEmpty() ? t : strikes.get(n - 1).lands();
        this.finaleStart = lastLand;
        this.outroStart = finaleStart + paced(finaleLength(finale));
        this.fadeStart = outroStart + paced(outroLength(finale));
        this.total = fadeStart + paced(FADE_OUT);
    }

    private long scaled(long ms) {
        return Math.max(16L, Math.round(ms * pace));
    }

    /**
     * The speed setting alone, without the 16ms floor {@link #scaled} keeps for strike
     * phases. Public so the screen can pace the moments inside an ending the same way.
     */
    public long paced(long ms) {
        return Math.round(ms * pace);
    }

    /** The ending a haul earns: its best tier, except that a Shattered Locket has its own. */
    public static Finale finaleFor(CorpseHaul haul) {
        Tier best = haul.best();
        if (best == Tier.SPECIAL) return Finale.SPECIAL;
        CorpseHaul.Drop head = haul.headline();
        if (best == Tier.LEGENDARY && head != null && "Shattered Locket".equals(head.name())) {
            return Finale.LOCKET;
        }
        return switch (best) {
            case LEGENDARY -> Finale.EXPLODE;
            case UNCOMMON -> Finale.REVEAL;
            default -> Finale.CRASH;
        };
    }

    /** The honest colour for a drop. */
    public static Flare flareOf(Tier tier) {
        return switch (tier) {
            case COMMON -> Flare.BLUE;
            case UNCOMMON -> Flare.VIOLET;
            default -> Flare.GOLD;
        };
    }

    /** The colour id the last strike lands on: 1 blue, 2 violet, 3 gold. */
    static int truthFor(Tier tier) {
        return switch (tier) {
            case COMMON -> 1;
            case UNCOMMON -> 2;
            default -> 3;
        };
    }

    // ------------------------------------------------------------------ asking it

    public List<Strike> strikes() { return strikes; }
    public Finale finale() { return finale; }
    public long intro() { return strikes.isEmpty() ? finaleStart : strikes.get(0).start(); }
    public long finaleStart() { return finaleStart; }
    public long outroStart() { return outroStart; }
    public long fadeStart() { return fadeStart; }
    public long total() { return total; }
    public long flight() { return scaled(FLIGHT); }
    /** How long the Dye's grey, frozen stretch lasts at this run's speed. */
    public long specialDrain() { return paced(SPECIAL_DRAIN); }
    /** How long the special endings stay in slow motion, at this run's speed. */
    public long slowMotion() { return paced(SLOW_MOTION); }
    public long fadeLength() { return paced(FADE_OUT); }

    /** Whether this run gets one of the two big endings - the Locket's or the Dye's. */
    public boolean special() {
        return finale == Finale.LOCKET || finale == Finale.SPECIAL;
    }

    /**
     * Which step of the last strike's cycle is showing at {@code t}.
     *
     * @return -1 before the cycle or on any other strike; 0 for the opening blue; 1 to 9
     *         through the cycle; {@link #LANDING} once it has landed on the true colour
     */
    public int climbStep(Strike s, long t) {
        if (!s.last() || t < s.climbStart()) return -1;
        for (int k = LANDING; k > 0; k--) {
            if (t >= stepStart(s, k)) return k;
        }
        return 0;
    }

    /** When step {@code k} of the last strike's cycle begins: 0 is the opening blue. */
    public long stepStart(Strike s, int k) {
        long at = s.climbStart();
        if (k <= 0) return at;
        at += scaled(CLIMB_OPEN);
        for (int i = 0; i < k - 1 && i < CYCLE_STEPS.length; i++) at += scaled(CYCLE_STEPS[i]);
        return at;
    }

    /** The colour id showing at step {@code k}: 1 blue, 2 violet, 3 gold. */
    public static int stepColour(Strike s, int k) {
        if (k <= 0) return 1;
        if (k >= LANDING) return s.truth();
        return CYCLE[k - 1];
    }

    /** The strike under way at {@code t}: winding up, falling or just landed. Null outside the strikes. */
    public Strike strikeAt(long t) {
        for (Strike s : strikes) {
            if (t >= s.start() && t < s.end()) return s;
        }
        return null;
    }

    /** How many picks have hit the ice by {@code t}. */
    public int landedBy(long t) {
        int n = 0;
        for (Strike s : strikes) if (t >= s.lands()) n++;
        return n;
    }

    /**
     * Whether a strike's item goes straight to the tray when it lands.
     *
     * Everything does except the last drop of a winning run, which is the ending's to show
     * off first - it goes to the middle of the screen, and only settles into its slot when
     * the ending is over.
     */
    public boolean goesStraightToTray(Strike s) {
        return !s.last() || finale == Finale.CRASH;
    }
}
