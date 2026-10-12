package com.butcher.audio;

import com.butcher.anim.EruptionTimeline;
import com.butcher.anim.EruptionTimeline.Drop;
import com.butcher.anim.EruptionTimeline.Ending;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Eruption's whole soundtrack as one list of timed cues, worked out once from the timeline - the screen plays
 * what is due and {@code tools/EruptionSoundtrack} renders the same list to a WAV. Pure.
 *
 * <b>Every cue before {@link EruptionTimeline#REVEAL} is the same for every drop</b> (EruptionTest renders them
 * and compares): the creature going under, the crust, the vent, the lake's rumble, a bubble per breath pitched by
 * its heat, the build and the flicker. At the reveal everything is cut and the outcome speaks.
 */
public final class EruptionSound {
    private EruptionSound() {}

    public static final class Cue {
        public final long at;
        public final boolean cut;
        public final Voice[] voices;

        Cue(long at, boolean cut, Voice[] voices) {
            this.at = at;
            this.cut = cut;
            this.voices = voices;
        }
    }

    private static final int[] PHRYGIAN_DOMINANT = {0, 1, 4, 5, 7, 8, 10};
    private static final int[] HARMONIC_MINOR = {0, 2, 3, 5, 7, 8, 11};

    public static List<Cue> schedule(EruptionTimeline tl) {
        long seed = tl.seed();
        long R = EruptionTimeline.REVEAL;
        List<Cue> out = new ArrayList<>();

        // ---- before the reveal: the same for every drop
        out.add(new Cue(0, false, Voices.eruptionRumble(R / 1000f, seed)));
        out.add(new Cue(EruptionTimeline.SINK_FROM, false, Voices.eruptionSink(seed + 1)));
        out.add(new Cue(EruptionTimeline.CRUST_FROM, false,
                Voices.eruptionCrust((EruptionTimeline.CRUST_TO - EruptionTimeline.CRUST_FROM) / 1000f, seed + 2)));
        out.add(new Cue(EruptionTimeline.VENT_FROM, false, Voices.eruptionVent(seed + 3)));
        long[] steps = EruptionTimeline.STEPS;
        for (int i = 0; i < steps.length; i++) {
            out.add(new Cue(steps[i], false, Voices.eruptionBreath(EruptionTimeline.stepLevel(i), i / (float) Math.max(1, steps.length - 1), seed + 100 + i)));
        }
        long build = EruptionTimeline.HANG - 400;
        out.add(new Cue(build, false, Voices.eruptionBuild((R - build) / 1000f)));
        int f = 0;
        for (long t = EruptionTimeline.HANG; t < R; t += 55) out.add(new Cue(t, false, Voices.eruptionFlicker(f++)));

        // ---- the reveal
        Drop d = tl.drop();
        Ending e = d.ending;
        if (!d.win()) {
            out.add(new Cue(R, true, Voices.eruptionCool(seed + 600)));
            if (EruptionTimeline.guaranteed(tl.creature()) != null) out.add(new Cue(R + 700, false, Voices.eruptionConsolation()));
            return sorted(out);
        }
        long arrive = tl.arrive();
        int level = d.heat.ordinal();
        switch (e) {
            case KUUDRA -> {
                // The stall: everything cut but the shudder, held white.
                out.add(new Cue(R, true, Voices.eruptionRumble((EruptionTimeline.K_FREEZE - R) / 1000f, seed + 610)));
                out.add(new Cue(EruptionTimeline.K_FREEZE, true, Voices.hellionFreeze(seed + 611)));
                out.add(new Cue(EruptionTimeline.K_FREEZE + 300, false,
                        Voices.hellionStill((EruptionTimeline.K_RESUME - EruptionTimeline.K_FREEZE - 300) / 1000f)));
                out.add(new Cue(EruptionTimeline.K_CARMINE, false,
                        Voices.eruptionCarmine((EruptionTimeline.K_RESUME - EruptionTimeline.K_CARMINE) / 1000f + 2.5f)));
                out.add(new Cue(EruptionTimeline.K_SWELL, false,
                        Voices.eruptionSwell((EruptionTimeline.K_SWELL_TO - EruptionTimeline.K_SWELL) / 1000f + 0.6f, seed + 612)));
                int n = 8;
                long span = EruptionTimeline.K_TENTACLES_TO - EruptionTimeline.K_TENTACLES - 700;
                for (int i = 0; i < n; i++) {
                    out.add(new Cue(EruptionTimeline.K_TENTACLES + span * i / (n - 1), false, Voices.eruptionTentacle(0.7f + 0.04f * i, seed + 620 + i)));
                }
                out.add(new Cue(EruptionTimeline.K_SWELL, false,
                        Voices.score(ERUPT_D_SCORE, HARMONIC_MINOR, (EruptionTimeline.K_RISE - EruptionTimeline.K_SWELL) / 1000f)));
                out.add(new Cue(EruptionTimeline.K_EYE, false, Voices.eruptionEye(seed + 630)));
                out.add(new Cue(EruptionTimeline.K_RISE, false, Voices.eruptionTentacle(1.1f, seed + 631)));
                out.add(new Cue(EruptionTimeline.K_RISE, false, Voices.eruptionRise((arrive - EruptionTimeline.K_RISE) / 1000f)));
                out.add(new Cue(arrive - 250, false, Voices.eruptionRoar(2.2f, seed + 632)));
            }
            case END_TIMES -> {
                // The sky goes dark and the lake holds its breath; three bolts, then it goes up.
                out.add(new Cue(R, true, Voices.eruptionRumble((EruptionTimeline.T_ERUPT - R) / 1000f, seed + 640)));
                long[] bolts = {EruptionTimeline.T_BOLTS0, EruptionTimeline.T_BOLTS1, EruptionTimeline.T_BOLTS2};
                for (int i = 0; i < bolts.length; i++) out.add(new Cue(bolts[i], false, Voices.eruptionThunder(0.55f + 0.08f * i, seed + 641 + i)));
                out.add(new Cue(EruptionTimeline.T_ERUPT, false, Voices.eruptionBoom(level, 0.7f, seed + 650)));
                out.add(new Cue(EruptionTimeline.T_HEAT, false,
                        Voices.score(ERUPT_D_SCORE, PHRYGIAN_DOMINANT, (arrive - EruptionTimeline.T_HEAT) / 1000f)));
                out.add(new Cue(EruptionTimeline.T_BOOK, false, Voices.eruptionBookFire((tl.total() - EruptionTimeline.T_BOOK) / 1000f, seed + 651)));
                out.add(new Cue(EruptionTimeline.T_BOOK, false, Voices.eruptionRise((arrive - EruptionTimeline.T_BOOK) / 1000f)));
                out.add(new Cue(EruptionTimeline.T_CRACK, false, Voices.eruptionGroundCrack(seed + 652)));
            }
            default -> {
                out.add(new Cue(R, true, Voices.eruptionVent(seed + 600)));
                if (e == Ending.FLASH) out.add(new Cue(EruptionTimeline.F_BOLT, false, Voices.eruptionThunder(0.8f, seed + 601)));
                out.add(new Cue(EruptionTimeline.ERUPT, false, Voices.eruptionBoom(level, e == Ending.FLASH ? 0.85f : 1f, seed + 602)));
                long from = tl.riseFrom();
                if (from < arrive && e != Ending.VIAL) out.add(new Cue(from, false, Voices.eruptionRise((arrive - from) / 1000f)));
            }
        }
        switch (e) {
            case PINCER -> {
                long[] at = {EruptionTimeline.P_SNAP + 120, EruptionTimeline.P_CLACKS0, EruptionTimeline.P_CLACKS1};
                for (int i = 0; i < at.length; i++) out.add(new Cue(at[i], false, Voices.eruptionClack(i, seed + 700 + i)));
            }
            case FLASH -> {
                int i = 0;
                for (long t = tl.riseFrom(); t < arrive + 900; t += 160 + (i * 37 % 90)) out.add(new Cue(t, false, Voices.eruptionZap(seed + 710 + i++)));
            }
            case FORGE -> {
                for (int i = 0; i < EruptionTimeline.FORGE_BLOWS.length; i++) {
                    out.add(new Cue(EruptionTimeline.FORGE_BLOWS[i], false, Voices.eruptionAnvil(i, seed + 720 + i)));
                }
            }
            case VIAL -> {
                out.add(new Cue(EruptionTimeline.V_GREEN, false, Voices.eruptionGeiger((EruptionTimeline.V_FREE - EruptionTimeline.V_GREEN) / 1000f, seed + 730)));
                out.add(new Cue(EruptionTimeline.V_GOLEM, false,
                        Voices.eruptionGolem((EruptionTimeline.V_GOLEM_UP - EruptionTimeline.V_GOLEM) / 1000f, seed + 731)));
                // Critical: a heartbeat and a crackle at every beat, louder each time.
                for (int i = 0; i < EruptionTimeline.V_PULSES.length; i++) {
                    out.add(new Cue(EruptionTimeline.V_PULSES[i], false, Voices.hellionHeart(Math.min(3, i / 2))));
                    out.add(new Cue(EruptionTimeline.V_PULSES[i], false, Voices.eruptionZap(seed + 740 + i)));
                }
                out.add(new Cue(EruptionTimeline.V_PULSES[2], false, Voices.hellionPull((EruptionTimeline.V_FREE - EruptionTimeline.V_PULSES[2]) / 1000f)));
                // The detonation: everything cut, the biggest boom there is, the cloud roaring up, a choir.
                out.add(new Cue(EruptionTimeline.V_FREE, true, Voices.eruptionBoom(4, 0.91f, seed + 750)));
                out.add(new Cue(EruptionTimeline.V_FREE, false, Voices.hellionSupernova(seed + 751)));
                out.add(new Cue(EruptionTimeline.V_FREE + 150, false, Voices.eruptionSwell(1.8f, seed + 752)));
                out.add(new Cue(EruptionTimeline.V_DESCEND, false, Voices.eruptionGeiger((arrive + 1200 - EruptionTimeline.V_DESCEND) / 1000f, seed + 753)));
                out.add(new Cue(EruptionTimeline.V_DESCEND, false, Voices.eruptionRise((arrive - EruptionTimeline.V_DESCEND) / 1000f)));
            }
            case SHELL -> out.add(new Cue(EruptionTimeline.S_PEEK, false, Voices.eruptionScuttle(
                    (EruptionTimeline.S_RUN - EruptionTimeline.S_PEEK) / 1000f, (EruptionTimeline.S_GONE - EruptionTimeline.S_RUN) / 1000f, seed + 740)));
            default -> { }
        }
        if (tl.quenchFrom() < tl.total()) out.add(new Cue(tl.quenchFrom(), false, Voices.eruptionQuench(seed + 800)));
        boolean big = tl.banner();
        out.add(new Cue(arrive, false, Voices.eruptionArrive(big, seed + 810)));
        out.add(new Cue(arrive + 300, false, Voices.eruptionGlow((tl.total() - arrive) / 1000f + 1.0f, seed + 900)));
        return sorted(out);
    }

    /** The score's root: D, an octave up from the rumble's. */
    private static final float ERUPT_D_SCORE = 146.83f;

    /** By time; at the same moment a cut goes first, so it does not silence what it was cut for. */
    private static List<Cue> sorted(List<Cue> cues) {
        cues.sort(Comparator.comparingLong((Cue c) -> c.at).thenComparing(c -> !c.cut));
        return cues;
    }
}
