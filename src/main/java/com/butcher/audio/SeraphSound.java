package com.butcher.audio;

import com.butcher.anim.HellionDice;
import com.butcher.anim.SeraphDragon;
import com.butcher.anim.SeraphTimeline;
import com.butcher.anim.SeraphTimeline.Drop;
import com.butcher.anim.SeraphTimeline.Ending;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Seraph's whole soundtrack as one list of timed cues, worked out once from the timeline - the screen
 * plays what is due and {@code tools/SeraphSoundtrack} renders the same list to a WAV. Pure.
 *
 * <b>Every cue before {@link SeraphTimeline#REVEAL} is the same for every drop</b> (SeraphTest renders
 * them and compares): the End drone, the crystals' hum, a thud and a glass clink per hit in the colour of
 * the moment, a vwoop on each teleport, the angry scream, the lasers, the throw. At the reveal everything
 * is cut and the outcome speaks.
 */
public final class SeraphSound {
    private SeraphSound() {}

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

    public static List<Cue> schedule(SeraphTimeline tl) {
        long seed = tl.seed();
        long R = SeraphTimeline.REVEAL;
        List<Cue> out = new ArrayList<>();

        // ---- before the reveal: the same for every drop
        out.add(new Cue(0, false, Voices.seraphSwirl(SeraphTimeline.TELEPORT_IN / 1000f, seed)));
        out.add(new Cue(SeraphTimeline.TELEPORT_IN, false, Voices.seraphVwoop(1f, seed + 1)));
        out.add(new Cue(100, false, Voices.seraphPillars(seed + 2)));
        out.add(new Cue(0, false, Voices.seraphDrone(R / 1000f, seed + 3)));
        long hum = SeraphTimeline.TELEPORT_IN + 250;
        out.add(new Cue(hum, false, Voices.seraphCrystalHum((R - hum) / 1000f)));
        long[] steps = SeraphTimeline.STEPS;
        for (int i = 0; i < steps.length; i++) {
            int glass = SeraphTimeline.CYCLE[i % SeraphTimeline.CYCLE.length].ordinal();
            out.add(new Cue(steps[i], false, Voices.seraphHit(glass, i / 5, i / (float) (steps.length - 1), seed + 100 + i)));
        }
        for (int k = 0; k < SeraphTimeline.DODGE_STEPS.length; k++) {
            out.add(new Cue(steps[SeraphTimeline.DODGE_STEPS[k]] + 20, false, Voices.seraphVwoop(0.9f + 0.12f * k, seed + 200 + k)));
        }
        long scream = steps[10];
        out.add(new Cue(scream, false, Voices.seraphScream((R - scream) / 1000f, seed + 300)));
        long build = SeraphTimeline.HANG - 400;
        out.add(new Cue(build, false, Voices.seraphBuild((R - build) / 1000f)));
        out.add(new Cue(SeraphTimeline.HANG, false, Voices.seraphLasers((SeraphTimeline.THROW + 60 - SeraphTimeline.HANG) / 1000f, seed + 400)));
        int f = 0;
        for (long t = SeraphTimeline.HANG; t < SeraphTimeline.THROW; t += 45) out.add(new Cue(t, false, Voices.seraphFlicker(f++)));
        out.add(new Cue(SeraphTimeline.THROW, false, Voices.seraphThrow(false, seed + 500)));
        out.add(new Cue(SeraphTimeline.RELEASE, false, Voices.seraphThrow(true, seed + 501)));

        // ---- the reveal
        Drop d = tl.drop();
        if (!d.win()) {
            // +5 dB: the bust was 6 dB softer than the other animations' busts (the review, sound).
            out.add(new Cue(R, true, Voices.gained(Voices.seraphPowerDown(seed + 600), 1.78f)));
            out.add(new Cue(R + 250, false, Voices.gained(Voices.seraphVwoop(0.7f, seed + 601), 1.78f)));
            return sorted(out);
        }
        out.add(new Cue(R, true, Voices.seraphPowerUp(d.lands.ordinal(), seed + 600)));
        long arrive = tl.arrive();
        Ending e = d.ending;
        boolean dies = e != Ending.JACKPOT && e != Ending.EYE && e != Ending.DRAGON;
        if (dies) {
            out.add(new Cue(SeraphTimeline.SHATTER, false, Voices.seraphShatter(seed + 610)));
            out.add(new Cue(SeraphTimeline.CRYSTALS_BLOW, false, Voices.seraphCrystalsBlow(seed + 620)));
            out.add(new Cue(SeraphTimeline.DEATH, false, Voices.seraphDeath(seed + 630)));
            out.add(new Cue(SeraphTimeline.POOF, false, Voices.seraphPoof(seed + 640)));
            out.add(new Cue(R + 300, false, Voices.seraphRise((arrive - R - 300) / 1000f)));
        }
        switch (e) {
            case CHALICE -> {
                for (int i = 0; i < 8; i++) out.add(new Cue(R + 900 + i * 160L, false, Voices.seraphPlip(i)));
                for (int i = 0; i < 6; i++) out.add(new Cue(arrive + 1300 + i * 140L, false, Voices.seraphPlip(i + 3)));
            }
            case ESPRESSO -> out.add(new Cue(arrive - 100, false, Voices.seraphEspresso(seed + 700)));
            case SKIN -> {
                out.add(new Cue(SeraphTimeline.HEAD_OFF, false, Voices.seraphHeadOff(seed + 710)));
                out.add(new Cue(SeraphTimeline.HEAD_LANDS, false,
                        Voices.seraphSpread((SeraphTimeline.SKIN_DONE - SeraphTimeline.HEAD_LANDS) / 1000f, seed + 711)));
            }
            case DICE -> {
                long launch = SeraphTimeline.DICE_LAUNCH;
                out.add(new Cue(launch, false, Voices.hellionThrow(seed + 720)));
                List<Long> hits = HellionDice.bounces();
                for (int i = 0; i < hits.size(); i++) out.add(new Cue(launch + hits.get(i), false, Voices.hellionDieHit(i == 0 ? 1f : 0.45f, seed + 721 + i)));
            }
            case JACKPOT -> {
                out.add(new Cue(R + 100, false, Voices.score(138.59f, PHRYGIAN_DOMINANT, (SeraphTimeline.SHOCK - R - 100) / 1000f)));
                out.add(new Cue(SeraphTimeline.SHATTER, false, Voices.seraphShatter(seed + 610)));
                out.add(new Cue(SeraphTimeline.STUTTER, false, Voices.seraphStutter()));
                out.add(new Cue(SeraphTimeline.VANISH, false, Voices.seraphVwoop(0.8f, seed + 730)));
                for (int k = 0; k < SeraphTimeline.AFTERIMAGES; k++) {
                    out.add(new Cue(SeraphTimeline.afterAt(k), false, Voices.seraphVwoop(0.9f + 0.09f * k, seed + 740 + k)));
                }
                out.add(new Cue(SeraphTimeline.IMPLODE, false, Voices.seraphImplode((SeraphTimeline.SHOCK - SeraphTimeline.IMPLODE) / 1000f)));
                out.add(new Cue(SeraphTimeline.SHOCK, false, Voices.seraphShock(seed + 750)));
                out.add(new Cue(SeraphTimeline.SHOCK, false, Voices.seraphCrystalsBlow(seed + 751)));
                out.add(new Cue(SeraphTimeline.SHOCK + 100, false, Voices.seraphRumble(4f, seed + 752)));
            }
            case EYE -> {
                long fr = SeraphTimeline.E_FREEZE;
                out.add(new Cue(fr, true, Voices.seraphFreeze(seed + 760)));
                out.add(new Cue(SeraphTimeline.E_CRACK, false,
                        Voices.seraphCrack((SeraphTimeline.E_BURST - SeraphTimeline.E_CRACK) / 1000f, 0.09f, seed + 770)));
                out.add(new Cue(SeraphTimeline.E_BURST, false, Voices.seraphShatter(seed + 771)));
                out.add(new Cue(SeraphTimeline.E_BURST, false, Voices.seraphCoreHum((SeraphTimeline.E_RISE - SeraphTimeline.E_BURST) / 1000f)));
                out.add(new Cue(SeraphTimeline.E_OPEN, false, Voices.seraphEyeOpen(seed + 772)));
                out.add(new Cue(SeraphTimeline.E_LEFT, false, Voices.seraphGlance(seed + 773)));
                out.add(new Cue(SeraphTimeline.E_RIGHT, false, Voices.seraphGlance(seed + 774)));
                out.add(new Cue(SeraphTimeline.E_PULSE, false, Voices.seraphHeartboom(seed + 775)));
                out.add(new Cue(SeraphTimeline.E_PULL, false,
                        Voices.seraphImplode((SeraphTimeline.E_PULLED - SeraphTimeline.E_PULL) / 1000f)));
                for (int k = 0; k < 3; k++) {
                    out.add(new Cue(SeraphTimeline.E_PULL + 150 + k * 280L, false, Voices.seraphVwoop(0.7f - 0.1f * k, seed + 780 + k)));
                }
                out.add(new Cue(SeraphTimeline.E_RISE, false, Voices.seraphRise((arrive - SeraphTimeline.E_RISE) / 1000f)));
            }
            case DRAGON -> {
                out.add(new Cue(SeraphTimeline.D_DEEPEN, false, Voices.seraphRise(0.5f)));
                out.add(new Cue(SeraphTimeline.D_FREEZE, true, Voices.seraphFreeze(seed + 760)));
                // The roar growing through the stopped world as it comes.
                out.add(new Cue(SeraphTimeline.D_FREEZE + 200, false,
                        Voices.seraphRoar((SeraphTimeline.D_IN + 1600 - SeraphTimeline.D_FREEZE - 200) / 1000f, true, 1f, seed + 761)));
                // A wingbeat on every downstroke of its real flap clock.
                long flight = SeraphDragon.flightMs();
                int lastBeat = 0;
                for (long ms = 0; ms <= flight; ms += 25) {
                    int beat = (int) Math.floor(com.butcher.render.SeraphScene.flapAt(ms) + 0.25f);
                    if (beat > lastBeat) {
                        float near = 1f - Math.abs(ms - 2600f) / 2600f;
                        out.add(new Cue(SeraphTimeline.D_IN + ms, false, Voices.seraphFlap(0.5f + 0.7f * Math.max(0f, near), seed + 800 + beat)));
                        lastBeat = beat;
                    }
                }
                // The close pass in front: a roar in your face.
                out.add(new Cue(SeraphTimeline.D_IN + 2300, false, Voices.seraphRoar(1.4f, false, 0.9f, seed + 762)));
                out.add(new Cue(SeraphTimeline.D_LAND, false, Voices.seraphLand(seed + 763)));
                out.add(new Cue(SeraphTimeline.D_BREATH - 150, false, Voices.seraphRoar(0.5f, false, 0.5f, seed + 764)));
                out.add(new Cue(SeraphTimeline.D_BREATH, false,
                        Voices.seraphBreath((SeraphTimeline.D_BREATH_END - SeraphTimeline.D_BREATH) / 1000f + 0.3f, seed + 765)));
                for (int i = 0; i < 9; i++) out.add(new Cue(SeraphTimeline.D_POOL + i * 280L, false, Voices.seraphGlug(i)));
                out.add(new Cue(SeraphTimeline.D_BREATH, false,
                        Voices.score(138.59f, HARMONIC_MINOR, (SeraphTimeline.D_BURST - SeraphTimeline.D_BREATH) / 1000f)));
                out.add(new Cue(SeraphTimeline.D_BURST, false, Voices.paintSplash(seed + 766)));
                // The glass after the splash and its bells: all three together peaked at 1.2.
                Voice[] glass = Voices.seraphShatter(seed + 767);
                for (int i = 0; i < glass.length; i++) glass[i] = Voices.after(0.22f, glass[i]);
                out.add(new Cue(SeraphTimeline.D_BURST, false, glass));
                out.add(new Cue(SeraphTimeline.D_RISE, false, Voices.seraphRise((arrive - SeraphTimeline.D_RISE) / 1000f)));
                // The rearing roar as the dye is shown.
                out.add(new Cue(arrive - 150, false, Voices.seraphRoar(2.4f, false, 1.15f, seed + 768)));
            }
            default -> { }
        }
        boolean big = e == Ending.JACKPOT || e == Ending.EYE || e == Ending.DRAGON;
        out.add(new Cue(arrive, false, Voices.seraphArrive(big, seed + 800)));
        out.add(new Cue(arrive + 300, false, Voices.seraphGlow((tl.total() - arrive) / 1000f + 1.0f, big, seed + 900)));
        return sorted(out);
    }

    /** By time; at the same moment a cut goes first, so it does not silence what it was cut for. */
    private static List<Cue> sorted(List<Cue> cues) {
        cues.sort(Comparator.comparingLong((Cue c) -> c.at).thenComparing(c -> !c.cut));
        return cues;
    }
}
