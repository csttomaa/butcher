package com.butcher.audio;

import com.butcher.anim.HellionDice;
import com.butcher.anim.HellionTimeline;
import com.butcher.anim.HellionTimeline.Drop;
import com.butcher.anim.HellionTimeline.Ending;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Hellion's whole soundtrack as one list of timed cues, worked out once from the timeline - so the
 * screen only plays what is due, and {@code tools/HellionSoundtrack} renders exactly the same list to a
 * WAV to listen to offline. Pure: no Minecraft.
 *
 * <b>Every cue before {@link HellionTimeline#REVEAL} is the same for every drop</b> (HellionTest renders
 * them and compares): the fire, the rods sweeping past faster and faster, a dagger clang per shield step
 * in that attunement's voice, the build and the flicker ticks. At the reveal everything is cut and the
 * outcome speaks.
 */
public final class HellionSound {
    private HellionSound() {}

    /** Some voices, due at {@code at} ms; {@code cut} silences everything still sounding first. */
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

    /** One outer-ring rod passes the front every this many ms of the blaze's own (sped-up) clock. */
    private static final double ROD_PASS_MS = 250.0;

    public static List<Cue> schedule(HellionTimeline tl) {
        long seed = tl.seed();
        long R = HellionTimeline.REVEAL;
        List<Cue> out = new ArrayList<>();

        // ---- before the reveal: identical for every drop
        out.add(new Cue(0, false, Voices.hellionOpen(seed)));
        out.add(new Cue(0, false, Voices.hellionFire(R / 1000f, seed + 100)));
        // The rods: the blaze's clock runs 1 + k x^2 times fast (HellionScene.ticks), so a pass every
        // 250 ms of it comes ever closer together - from a slow whup to a whirr.
        long prev = 0;
        for (long t = 0; t < R; t += 5) {
            double x = t / (double) R;
            double eff = t + HellionTimeline.SPIN_TOP / 3.0 * R * x * x * x;
            long pass = (long) (eff / ROD_PASS_MS);
            if (pass != prev && t >= HellionTimeline.APPEAR) {
                out.add(new Cue(t, false, Voices.hellionRodPass((float) (x * x), seed + 200 + pass)));
            }
            prev = pass;
        }
        long[] steps = HellionTimeline.STEPS;
        for (int i = 0; i < steps.length; i++) {
            int att = tl.attunement(steps[i]).ordinal();
            out.add(new Cue(steps[i], false, Voices.hellionClang(att, i / 4, i / (float) (steps.length - 1), seed + 300 + i)));
        }
        long build = HellionTimeline.HANG - 400;
        out.add(new Cue(build, false, Voices.hellionBuild((R - build) / 1000f)));
        int f = 0;
        for (long t = HellionTimeline.HANG; t < R - 20; t += 45) out.add(new Cue(t, false, Voices.hellionFlicker(f++)));

        // ---- the reveal
        Drop d = tl.drop();
        if (!d.win()) {
            out.add(new Cue(R, true, Voices.hellionSnuff(seed + 400)));
            return sorted(out);
        }
        out.add(new Cue(R, true, Voices.hellionShatter(d.lands.ordinal(), seed + 400)));
        long arrive = tl.arrive();
        switch (d.ending) {
            case SHATTER -> out.add(new Cue(R + 100, false, Voices.hellionRise((arrive - R - 100) / 1000f)));
            case DICE, HIGH_CLASS -> {
                boolean hc = d.ending == Ending.HIGH_CLASS;
                long launch = HellionTimeline.DICE_LAUNCH;
                // The High Class Dice's music runs to its bullet time, which cuts it dead.
                long slow = launch + HellionTimeline.SLOW_FROM;
                long swellTo = hc ? slow : arrive;
                out.add(new Cue(R + 100, false, Voices.hellionRise((swellTo - R - 100) / 1000f)));
                if (hc) {
                    out.add(new Cue(R + 100, false, Voices.score(164.81f, PHRYGIAN_DOMINANT, (slow - R - 100) / 1000f)));
                    out.add(new Cue(slow, true, Voices.hellionSlow((tl.flatAt() - slow) / 1000f + 0.3f)));
                    for (int i = 0; i < 6; i++) out.add(new Cue(tl.pipAt(i), false, Voices.hellionPip(i)));
                    out.add(new Cue(tl.jackpot(), false, Voices.hellionJackpot(seed + 550)));
                }
                out.add(new Cue(launch, false, Voices.hellionThrow(seed + 500)));
                List<Long> hits = HellionDice.bounces();
                for (int i = 0; i < hits.size(); i++) {
                    out.add(new Cue(tl.diceReal(hits.get(i)), false, Voices.hellionDieHit(i == 0 ? 1f : 0.45f, seed + 510 + i)));
                }
                int k = 0;
                for (long ms : HellionDice.tumbles(hc)) {
                    boolean near = false;
                    for (long h : hits) near |= Math.abs(h - ms) < 70;
                    if (!near) out.add(new Cue(tl.diceReal(ms), false, Voices.hellionDieHit(0.25f, seed + 530 + k++)));
                }
            }
            case DYE -> {
                long freeze = HellionTimeline.FREEZE, ignite = HellionTimeline.IGNITE;
                out.add(new Cue(freeze, true, Voices.hellionFreeze(seed + 600)));
                out.add(new Cue(freeze + 150, false, Voices.hellionStill((ignite - freeze - 150) / 1000f)));
                long[] hearts = HellionTimeline.HEARTS;
                for (int i = 0; i < hearts.length; i++) {
                    out.add(new Cue(hearts[i] - 450, false, Voices.hellionPull(0.45f)));
                    out.add(new Cue(hearts[i], false, Voices.hellionHeart(i)));
                }
                out.add(new Cue(ignite, true, Voices.hellionIgnite(seed + 700)));
                out.add(new Cue(ignite, false, Voices.hellionSupernova(seed + 710)));
                out.add(new Cue(ignite + 150, false, Voices.hellionRise((arrive - ignite - 150) / 1000f)));
                out.add(new Cue(ignite + 150, false, Voices.score(220f, HARMONIC_MINOR, (arrive - ignite - 150) / 1000f)));
            }
            default -> { }
        }
        boolean jackpot = d.ending == Ending.HIGH_CLASS || d.ending == Ending.DYE;
        long fanfare = d.ending == Ending.HIGH_CLASS ? tl.jackpot() : arrive;
        out.add(new Cue(fanfare, false, Voices.hellionArrive(jackpot, seed + 800)));
        out.add(new Cue(arrive + 300, false, Voices.hellionGlow((tl.total() - arrive) / 1000f + 1.0f, seed + 900)));
        return sorted(out);
    }

    /** By time; at the same moment a cut goes first, so it does not silence what it was cut for. */
    private static List<Cue> sorted(List<Cue> cues) {
        cues.sort(Comparator.comparingLong((Cue c) -> c.at).thenComparing(c -> !c.cut));
        return cues;
    }
}
