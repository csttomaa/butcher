package com.butcher.audio;

import com.butcher.anim.ChopTimeline;
import com.butcher.anim.HuntEnding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * "The Whole Hunt"'s sounds after the split, as timed cues worked out once from the timeline (the Eruption
 * way): ChopScreen plays what is due. The build-up and the split are ChopScreen's own, as for every verdict.
 * Pure.
 */
public final class HuntSound {
    private HuntSound() {}

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

    public static List<Cue> schedule(ChopTimeline tl) {
        HuntEnding e = tl.hunt();
        long seed = tl.seed();
        List<Cue> out = new ArrayList<>();
        out.add(new Cue(ChopTimeline.SPLIT + 60, false, Voices.huntGroan((HuntEnding.HUSH - ChopTimeline.SPLIT - 60) / 1000f, seed + 1)));
        out.add(new Cue(HuntEnding.YANK, false, Voices.huntWrench(seed + 2)));
        // Half a second of nothing.
        out.add(new Cue(HuntEnding.HUSH, true, new Voice[0]));
        out.add(new Cue(HuntEnding.GEYSER, true, Voices.huntGeyser((HuntEnding.SUCK - HuntEnding.GEYSER) / 1000f, seed + 3)));
        for (int k = 0; k < HuntEnding.HEADS; k++) {
            boolean king = k == HuntEnding.KING;
            if (!king) out.add(new Cue(e.launch(k), false, Voices.paintBlup()));
            out.add(new Cue(e.landing(k), false, Voices.huntThud(king ? 11 : k, king, seed + 10 + k)));
        }
        out.add(new Cue(HuntEnding.SPIN, false, Voices.huntWhirl((HuntEnding.SUCK - HuntEnding.SPIN) / 1000f, seed + 30)));
        out.add(new Cue(HuntEnding.MOON, false, Voices.relicMoon((HuntEnding.ARROW - HuntEnding.MOON) / 1000f + 0.6f)));
        out.add(new Cue(HuntEnding.ARROW, false, Voices.huntBow(seed + 40)));
        for (int n = 0; n < HuntEnding.HEADS; n++) {
            int k = e.hitOrder(n);
            out.add(new Cue(e.hit(k), false, Voices.huntPluck(n, k == HuntEnding.KING, seed + 50 + n)));
        }
        out.add(new Cue(HuntEnding.SUCK, false, Voices.hellionPull((HuntEnding.SHOCK - HuntEnding.SUCK) / 1000f)));
        out.add(new Cue(HuntEnding.SHOCK, true, Voices.huntShock(seed + 60)));
        out.sort(Comparator.comparingLong((Cue c) -> c.at).thenComparing(c -> !c.cut));
        // +3 dB: measured against the other dyes' endings it was the quietest by 3-4 dB (the review, sound).
        List<Cue> loud = new ArrayList<>();
        for (Cue c : out) loud.add(new Cue(c.at, c.cut, Voices.gained(c.voices, 1.41f)));
        return loud;
    }
}
