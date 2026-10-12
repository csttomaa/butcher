import com.butcher.anim.ChopTimeline;
import com.butcher.anim.EruptionTimeline;
import com.butcher.anim.EruptionTimeline.Creature;
import com.butcher.anim.EruptionTimeline.Drop;
import com.butcher.anim.EruptionTimeline.Ending;
import com.butcher.audio.EruptionSound;
import com.butcher.render.ChopScene;
import com.butcher.render.EruptionScene;

/**
 * Offline checks for "Eruption" (lava fishing's animation). The one that matters: nothing drawn or heard before
 * the reveal depends on the drop - every draw call of every frame (the rasterised 3D layer's pixels included) is
 * hashed, for every drop each creature can give, on several seeds, and they must agree.
 *
 * The game's own models are posed by its own code (LavaRigs), so the client jar must be on the classpath, as for
 * the preview. The textures are stand-ins: only sameness matters here.
 *
 *   java -cp "<mc classpath>;out;src/main/resources" EruptionTest
 */
public final class EruptionTest {
    static int passed, failed;

    static void check(String what, boolean ok) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    /** Hashes every primitive drawn, the 3D layer's image pixel by pixel. */
    static final class Hash implements ChopScene.Canvas {
        long h = 1125899906842597L;
        int calls, squares;

        void mix(float... v) {
            calls++;
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
        public void imageAffine(String key, int[] argb, int iw, int ih, float ox, float oy, float ux, float uy, float vx, float vy) {
            mix(key); mix(iw, ih, ox, oy, ux, uy, vx, vy);
            for (int i = 0; i < iw * ih; i++) h = 31 * h + argb[i];
        }
        public void image(String key, int[] argb, int iw, int ih, float x, float y, float w, float hh) {
            mix(key); mix(iw, ih, x, y, w, hh);
            for (int i = 0; i < iw * ih; i++) h = 31 * h + argb[i];
        }
    }

    /** A stand-in texture: a seeded pattern, opaque. */
    static int[] pattern(int w, int h, int seed) {
        int[] px = new int[w * h];
        java.util.Random r = new java.util.Random(seed);
        for (int i = 0; i < px.length; i++) px[i] = 0xFF000000 | r.nextInt(0xFFFFFF);
        return px;
    }

    static long frames(Creature c, Drop d, long seed, long from, long to, int step) {
        EruptionTimeline tl = new EruptionTimeline(c, d, seed);
        tl.price(1_000_000);
        EruptionScene.dropPixels = pattern(64, 64, d.ordinal());
        EruptionScene.dropW = 64;
        EruptionScene.dropH = 64;
        Hash hash = new Hash();
        for (long t = from; t < to; t += step) EruptionScene.draw(hash, hash::fill, hash::fill, tl, t, 640, 360);
        return hash.h;
    }

    public static void main(String[] a) {
        EruptionScene.rigs = new com.butcher.render.LavaRigs();
        for (int slot = 0; slot < 8; slot++) EruptionScene.RASTER.texture(slot, pattern(64, 64, 100 + slot), 64, 64);
        EruptionScene.rasterScale = 1f;
        EruptionScene.lavaPixels = pattern(16, 320, 77);

        // ---- honesty: the drop changes nothing before the reveal, per creature
        long[] seeds = {1, 7, 42, 99_991};
        for (Creature c : Creature.values()) {
            for (long seed : seeds) {
                long ref = frames(c, Drop.BUST, seed, 0, EruptionTimeline.REVEAL, 48);
                for (Drop d : Drop.values()) {
                    if (d == Drop.BUST || !d.from(c)) continue;
                    check("before the reveal, " + c + " / " + d + " draws the same as a bust (seed " + seed + ")",
                            frames(c, d, seed, 0, EruptionTimeline.REVEAL, 48) == ref);
                }
            }
        }
        check("a win draws differently from a bust after the reveal",
                frames(Creature.THUNDER, Drop.FLASH, 7, EruptionTimeline.REVEAL, EruptionTimeline.REVEAL + 600, 48)
                        != frames(Creature.THUNDER, Drop.BUST, 7, EruptionTimeline.REVEAL, EruptionTimeline.REVEAL + 600, 48));

        // ---- the heat: the breathing is fixed, and it lands on the drop's tier
        long prev = -1;
        boolean ordered = true;
        for (long s : EruptionTimeline.STEPS) {
            ordered &= s > prev && s < EruptionTimeline.HANG;
            prev = s;
        }
        check("the breaths are in order and before the hang", ordered);
        check("the build-up is 4.5-5 s (" + EruptionTimeline.REVEAL + ")", EruptionTimeline.REVEAL >= 4500 && EruptionTimeline.REVEAL <= 5000);
        boolean everyLevel = true;
        boolean[] seen = new boolean[5];
        for (int i = 0; i < EruptionTimeline.STEPS.length; i++) seen[EruptionTimeline.stepLevel(i)] = true;
        for (boolean b : seen) everyLevel &= b;
        check("the breathing visits every heat, black to white", everyLevel);
        for (Drop d : Drop.values()) {
            EruptionTimeline tl = new EruptionTimeline(d.from != null ? d.from : Creature.JAWBUS, d, 3);
            check(d + " lands on its heat", Math.abs(tl.heat(EruptionTimeline.REVEAL + 300) - (d.win() ? d.heat.ordinal() : tl.heat(EruptionTimeline.REVEAL + 300))) < 0.01f);
            check(d + ": only a bust lands on black", (d.heat == EruptionTimeline.Heat.BLACK) == !d.win());
            check(d + " fades out by its end", tl.master(tl.total() - 1) < 0.02f);
            if (d.win()) {
                check(d + " is shown after the reveal", tl.arrive() > EruptionTimeline.REVEAL);
                check(d + " rises before it is shown", tl.riseFrom() < tl.arrive());
                check(d + " leaves the showcase on screen at least 3 s", tl.total() - tl.arrive() >= 3000);
            }
        }
        EruptionTimeline bust = new EruptionTimeline(Creature.THUNDER, Drop.BUST, 7);
        check("a bust is over within 1.5 s of the reveal when nothing is guaranteed (" + (bust.total() - EruptionTimeline.REVEAL) + ")",
                bust.total() - EruptionTimeline.REVEAL <= 1500);
        check("a bust has cooled to black", bust.heat(EruptionTimeline.REVEAL + 1200) < 0.05f);
        // Heat only climbs with value.
        for (int i = 1; i < Drop.values().length; i++) {
            check(Drop.values()[i] + " is no cooler than the drop before it",
                    Drop.values()[i].heat.ordinal() >= Drop.values()[i - 1].heat.ordinal());
        }

        // ---- the Carmine Dye: time stops and resumes without a jump; Plhlegblast only plays for it
        EruptionTimeline dye = new EruptionTimeline(Creature.THUNDER, Drop.CARMINE, 1);
        check("Kuudra's clock stops", dye.phys(EruptionTimeline.K_FREEZE + 10) == dye.phys(EruptionTimeline.K_RESUME - 10));
        check("Kuudra's clock runs before the freeze", dye.phys(EruptionTimeline.REVEAL) == EruptionTimeline.REVEAL);
        check("Kuudra's clock resumes without a jump", Math.abs(dye.phys(EruptionTimeline.K_RESUME + 1) - dye.phys(EruptionTimeline.K_RESUME - 1)) <= 2);
        check("the carmine has taken the lake by the swell", dye.carmine(EruptionTimeline.K_SWELL) > 0.99f);
        check("the tentacles are all out before the eye", dye.tentacle(EruptionTimeline.K_EYE, 7, 8) > 0.99f);
        check("the dye is offered after the eye opens", EruptionTimeline.K_RISE >= EruptionTimeline.K_EYE_TO);
        check("the Carmine Dye is the grandest: the longest ending",
                dye.total() >= new EruptionTimeline(Creature.RAGNAROK, Drop.BURNT_TEXTS, 1).total());
        for (Drop d : Drop.values()) check(d + ": Plhlegblast drops nothing but the dye (or a bust)",
                d.from(Creature.PLHLEGBLAST) == (d == Drop.BUST || d == Drop.CARMINE));
        check("only the Carmine Dye comes from any creature", Drop.CARMINE.from == null);

        // ---- the other endings' beats come in order
        check("the Vial: green, then Jawbus up, then freed, then he sinks",
                EruptionTimeline.V_GREEN < EruptionTimeline.V_GOLEM && EruptionTimeline.V_GOLEM_UP <= EruptionTimeline.V_FREE
                        && EruptionTimeline.V_FREE < EruptionTimeline.V_SINK);
        check("the Vial: Jawbus is under before the vial is shown", EruptionTimeline.V_SINK_TO <= new EruptionTimeline(Creature.JAWBUS, Drop.VIAL, 1).arrive());
        check("the Shell: up, the crab peeks, runs, gone",
                EruptionTimeline.S_UP_TO < EruptionTimeline.S_PEEK && EruptionTimeline.S_PEEK < EruptionTimeline.S_RUN && EruptionTimeline.S_RUN < EruptionTimeline.S_GONE);
        check("the Pincer snaps, then clacks twice", EruptionTimeline.P_SNAP < EruptionTimeline.P_CLACKS0 && EruptionTimeline.P_CLACKS0 < EruptionTimeline.P_CLACKS1);
        check("the End Times: three bolts, then it goes up",
                EruptionTimeline.T_BOLTS0 < EruptionTimeline.T_BOLTS1 && EruptionTimeline.T_BOLTS1 < EruptionTimeline.T_BOLTS2
                        && EruptionTimeline.T_BOLTS2 < EruptionTimeline.T_ERUPT);
        long[] blows = EruptionTimeline.FORGE_BLOWS;
        check("the forge's three blows are in order and before the handle is named",
                blows[0] < blows[1] && blows[1] < blows[2] && blows[2] < new EruptionTimeline(Creature.RAGNAROK, Drop.BRIMSTONE, 1).arrive());

        // ---- the sound: before the reveal, the very same samples for every drop, per creature
        for (Creature c : Creature.values()) {
            float[] base = null;
            for (Drop d : Drop.values()) {
                if (!d.from(c)) continue;
                java.util.List<EruptionSound.Cue> before = new java.util.ArrayList<>();
                for (EruptionSound.Cue cue : EruptionSound.schedule(new EruptionTimeline(c, d, 11))) {
                    if (cue.at < EruptionTimeline.REVEAL) before.add(cue);
                }
                float[] pcm = EruptionSoundtrack.render(before, EruptionTimeline.REVEAL / 1000f);
                if (base == null) base = pcm;
                else check("before the reveal, " + c + " / " + d + " sounds the same as a bust", java.util.Arrays.equals(base, pcm));
            }
        }
        for (Drop d : Drop.values()) {
            Creature c = d.from != null ? d.from : Creature.JAWBUS;
            java.util.List<EruptionSound.Cue> cues = EruptionSound.schedule(new EruptionTimeline(c, d, 5));
            boolean cutAtReveal = false, inOrder = true;
            for (int i = 0; i < cues.size(); i++) {
                if (cues.get(i).at == EruptionTimeline.REVEAL && cues.get(i).cut) cutAtReveal = true;
                if (i > 0 && cues.get(i).at < cues.get(i - 1).at) inOrder = false;
            }
            check(d + ": the reveal cuts the build-up dead", cutAtReveal);
            check(d + ": its cues are in order", inOrder);
        }

        // ---- cost: draw calls a frame (the 3D layer is one image), worst over every ending
        int worst = 0;
        long sum = 0, n = 0;
        String worstAt = "";
        for (Drop d : Drop.values()) {
            Creature c = d.from != null ? d.from : Creature.THUNDER;
            EruptionTimeline tl = new EruptionTimeline(c, d, 9);
            tl.price(1_000_000);
            for (long t = 0; t < tl.total(); t += 100) {
                Hash hash = new Hash();
                com.butcher.render.CrystalVeins.Fill layer = (x, y, w, h2, argb) -> hash.squares++;
                EruptionScene.draw(hash, layer, layer, tl, t, 640, 360);
                sum += hash.calls;
                n++;
                if (hash.calls > worst) {
                    worst = hash.calls;
                    worstAt = d + " at " + t;
                }
            }
        }
        System.out.println("GUI draw calls a frame (pixel squares go to one image): mean " + sum / Math.max(1, n) + ", worst " + worst + " (" + worstAt + ")");
        check("no frame costs more than the Seraph's worst (5,100): " + worst, worst <= 5_100);

        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
