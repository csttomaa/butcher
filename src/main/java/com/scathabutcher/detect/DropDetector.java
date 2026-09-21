package com.scathabutcher.detect;

/**
 * Holds the result of a detected Scatha drop. The actual detection lives in
 * {@link ScathaWormTracker} (was a Scatha just killed/charmed?) and
 * {@link HotbarDropWatcher} (what did the player's inventory just gain?) -
 * ported from decompiling Scatha-Pro's own jar, since there turned out to be
 * no chat broadcast for either of these to regex-match against at all.
 *
 * Three outcomes, matching the three things a Scatha can actually give you:
 * a PET, a Dwarven O's (BLOCKED - the classic "no pet" result), or a
 * gemstone. Gemstones are special: if the player has a gemstone sack, the
 * item never touches their inventory at all (it goes straight to the sack),
 * so we can't see it directly. Scatha-Pro handles this the same way: it
 * waits out the full post-kill window, and if neither a pet nor a Dwarven
 * O's showed up in that time, it assumes the drop was a gemstone (those are
 * the only three possible outcomes) - see HotbarDropWatcher.
 */
public final class DropDetector {
    private DropDetector() {}

    public enum Outcome { PET, BLOCKED, GEMSTONE }

    public static final class DropResult {
        public final Outcome outcome;
        public final String rarity; // lowercase, e.g. "legendary"; null unless outcome == PET

        private DropResult(Outcome outcome, String rarity) {
            this.outcome = outcome;
            this.rarity = rarity;
        }

        public static DropResult pet(String rarity) {
            return new DropResult(Outcome.PET, rarity == null ? "unknown" : rarity.toLowerCase());
        }

        public static DropResult blocked() {
            return new DropResult(Outcome.BLOCKED, null);
        }

        public static DropResult gemstone() {
            return new DropResult(Outcome.GEMSTONE, null);
        }
    }
}
