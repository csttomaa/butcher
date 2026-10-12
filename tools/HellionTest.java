import com.butcher.anim.ChopTimeline;
import com.butcher.anim.HellionDice;
import com.butcher.anim.HellionTimeline;
import com.butcher.anim.HellionTimeline.Drop;
import com.butcher.render.ChopScene;
import com.butcher.render.HellionScene;

/**
 * Offline checks for "Hellion" (the blaze slayer's animation). The one that matters: nothing drawn
 * before the reveal depends on the drop - every draw call of every frame is hashed, for every drop,
 * on several seeds, and they must agree.
 *
 *   javac -d out -sourcepath "src/main/java;tools" tools/HellionTest.java && java -cp out HellionTest
 */
public final class HellionTest {
    static int passed, failed;

    static void check(String what, boolean ok) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    /** Hashes every primitive drawn. */
    static final class Hash implements ChopScene.Canvas {
        long h = 1125899906842597L;

        void mix(float... v) {
            for (float f : v) h = 31 * h + Float.floatToIntBits(Math.round(f * 100f) / 100f);
        }

        void mix(String s) { h = 31 * h + (s == null ? 0 : s.hashCode()); }

        public void quad(String tex, float ox, float oy, float ux, float uy, float vx, float vy, int u0, int v0, int uw, int vh, int argb) {
            mix(tex); mix(ox, oy, ux, uy, vx, vy, u0, v0, uw, vh, argb);
        }
        public void sprite(String tex, float cx, float cy, float w, float h2, float rot, int argb) { mix(tex); mix(cx, cy, w, h2, rot, argb); }
        public void line(float x0, float y0, float x1, float y1, float width, int argb) { mix(x0, y0, x1, y1, width, argb); }
        public void fill(float x, float y, float w, float h2, int argb) { mix(x, y, w, h2, argb); }
        public void item(float cx, float cy, float size, float alpha, float yaw) { mix(cx, cy, size, alpha, yaw); mix("item"); }
        public void name(float cx, float cy, float scale, int argb, int letters) { mix(cx, cy, scale, argb, letters); mix("name"); }
        public void text(String text, float cx, float cy, float scale, int argb) { mix(text); mix(cx, cy, scale, argb); }
        public String creatureSkin(ChopTimeline.Creature creature) { return null; }
        public String dropSkin() { return null; }
    }

    static long frames(Drop d, long seed, long from, long to) {
        HellionTimeline tl = new HellionTimeline(d, seed);
        Hash hash = new Hash();
        for (long t = from; t < to; t += 16) HellionScene.draw(hash, hash::fill, tl, t, 640, 360);
        return hash.h;
    }

    public static void main(String[] a) {
        long[] seeds = {1, 7, 42, 99_991, 123_456_789};
        for (long seed : seeds) {
            long ref = frames(Drop.BUST, seed, 0, HellionTimeline.REVEAL);
            for (Drop d : Drop.values()) {
                check("before the reveal, " + d + " draws the same as a bust (seed " + seed + ")",
                        frames(d, seed, 0, HellionTimeline.REVEAL) == ref);
            }
        }
        // ...and the reveal itself is where they differ.
        check("a win draws differently from a bust after the reveal",
                frames(Drop.DUPLEX, 7, HellionTimeline.REVEAL, HellionTimeline.REVEAL + 400)
                        != frames(Drop.BUST, 7, HellionTimeline.REVEAL, HellionTimeline.REVEAL + 400));

        HellionTimeline bust = new HellionTimeline(Drop.BUST, 7);
        check("a bust is over within 1 s of the reveal (" + bust.total() + ")", bust.total() - HellionTimeline.REVEAL <= 1000);
        check("the build-up takes 4.5-5 s, the user's ask (" + HellionTimeline.REVEAL + ")",
                HellionTimeline.REVEAL >= 4500 && HellionTimeline.REVEAL <= 5000);
        check("the shield's last step ends before the reveal", HellionTimeline.HANG < HellionTimeline.REVEAL);
        for (Drop d : Drop.values()) {
            HellionTimeline tl = new HellionTimeline(d, 7);
            check(d + " fades out by its end", tl.master(tl.total() - 1) < 0.02f);
            check(d + " lands on its own attunement", tl.attunement(HellionTimeline.REVEAL) == d.lands);
            check(d + ": only a bust lands on Ashen", (d.lands == HellionTimeline.Attunement.ASHEN) == !d.win());
        }
        // The shield's steps are in order, all before the reveal.
        long prev = -1;
        boolean ordered = true;
        for (long s : HellionTimeline.STEPS) {
            ordered &= s > prev && s < HellionTimeline.REVEAL;
            prev = s;
        }
        check("the shield's steps are in order and before the reveal", ordered);
        // The dice: the baked throw loaded, each die at rest on its 6 (its own +Y face up), and only then shown.
        check("the dice track loaded (" + HellionDice.settleMs() + " ms)", HellionDice.settleMs() > 1500 && HellionDice.settleMs() < 3000);
        for (boolean hc : new boolean[]{false, true}) {
            float[] rest = HellionDice.pose(hc, 10_000);
            // The 6 is the back face (-Z) on the Archfiend Dice and the right side (-X) on the High Class one.
            check((hc ? "High Class" : "Archfiend") + " rests with its 6 up", hc ? rest[6] < -0.98f : rest[8] < -0.98f);
            check((hc ? "High Class" : "Archfiend") + " rests on the floor", Math.abs(rest[1] + 4f) < 0.3f);
            float[] start = HellionDice.pose(hc, 0);
            check((hc ? "High Class" : "Archfiend") + " is thrown from the core", Math.abs(start[1] - 4f) < 0.5f);
        }
        check("the die hits the floor, then tips flat (" + HellionDice.bounces() + ")", HellionDice.bounces().size() == 2);
        for (Drop d : new Drop[]{Drop.ARCHFIEND_DICE, Drop.HIGH_CLASS_DICE}) {
            HellionTimeline tl = new HellionTimeline(d, 1);
            check(d + " names the drop only after the die stops", tl.arrive() >= HellionTimeline.DICE_LAUNCH + HellionDice.settleMs());
        }
        // The Flame Dye: time stands still between the freeze and the ignition, and nothing before it.
        HellionTimeline dye = new HellionTimeline(Drop.FLAME_DYE, 1);
        check("the Flame Dye's clock stops", dye.phys(HellionTimeline.FREEZE + 10) == dye.phys(HellionTimeline.IGNITE - 10));
        check("the Flame Dye's clock runs normally before the freeze", dye.phys(HellionTimeline.REVEAL) == HellionTimeline.REVEAL);
        check("the Flame Dye's clock resumes without a jump", Math.abs(dye.phys(HellionTimeline.IGNITE + 1) - dye.phys(HellionTimeline.IGNITE - 1)) <= 2);
        check("the freeze comes after the reveal", HellionTimeline.FREEZE > HellionTimeline.REVEAL);
        check("only the jackpots carry the banner", dye.banner() && new HellionTimeline(Drop.HIGH_CLASS_DICE, 1).banner()
                && !new HellionTimeline(Drop.ARCHFIEND_DICE, 1).banner());
        // The sound: everything before the reveal renders to the very same samples for every drop.
        for (long seed : new long[]{3, 77}) {
            float[] base = null;
            for (Drop d : Drop.values()) {
                java.util.List<com.butcher.audio.HellionSound.Cue> before = new java.util.ArrayList<>();
                for (com.butcher.audio.HellionSound.Cue c : com.butcher.audio.HellionSound.schedule(new HellionTimeline(d, seed))) {
                    if (c.at < HellionTimeline.REVEAL) before.add(c);
                }
                float[] pcm = HellionSoundtrack.render(before, HellionTimeline.REVEAL / 1000f);
                if (base == null) base = pcm;
                else check("before the reveal, " + d + " sounds the same as a bust (seed " + seed + ")", java.util.Arrays.equals(base, pcm));
            }
        }
        for (Drop d : Drop.values()) {
            java.util.List<com.butcher.audio.HellionSound.Cue> cues = com.butcher.audio.HellionSound.schedule(new HellionTimeline(d, 5));
            boolean cutAtReveal = false, inOrder = true;
            for (int i = 0; i < cues.size(); i++) {
                if (cues.get(i).at == HellionTimeline.REVEAL && cues.get(i).cut) cutAtReveal = true;
                if (i > 0 && cues.get(i).at < cues.get(i - 1).at) inOrder = false;
            }
            check(d + ": the reveal cuts the build-up dead", cutAtReveal);
            check(d + ": its cues are in order", inOrder);
        }
        // The High Class Dice's bullet time: its clock never jumps or runs backwards, the real and
        // track times agree both ways, and the pips come after it lies flat, the jackpot after the sixth.
        HellionTimeline hcl = new HellionTimeline(Drop.HIGH_CLASS_DICE, 1);
        boolean smooth = true;
        long prevMs = -1;
        for (long t = HellionTimeline.DICE_LAUNCH; t < hcl.total(); t += 5) {
            long ms = hcl.diceMs(t);
            if (ms < prevMs || (prevMs >= 0 && ms - prevMs > 6)) smooth = false;
            prevMs = ms;
        }
        check("the High Class die's clock is smooth through bullet time", smooth);
        boolean inverse = true;
        for (long ms = 0; ms < 2100; ms += 25) inverse &= Math.abs(hcl.diceMs(hcl.diceReal(ms)) - ms) <= 1;
        check("bullet time maps back to the track", inverse);
        check("the pips light after it lies flat", hcl.pipAt(0) > hcl.flatAt());
        check("the jackpot comes after the sixth pip", hcl.jackpot() > hcl.pipAt(5) && hcl.pipsLit(hcl.jackpot()) == 6);
        check("only the High Class Dice has bullet time", new HellionTimeline(Drop.ARCHFIEND_DICE, 1).slowMo(HellionTimeline.DICE_LAUNCH + 1300) == 0f
                && hcl.slowMo(HellionTimeline.DICE_LAUNCH + 1300) > 0.9f);
        check("the Flame Dye has three heartbeats inside stopped time",
                HellionTimeline.HEARTS.length == 3 && HellionTimeline.HEARTS[0] > HellionTimeline.FREEZE
                        && HellionTimeline.HEARTS[2] < HellionTimeline.IGNITE);
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
