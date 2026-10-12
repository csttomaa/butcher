import com.butcher.anim.ChopTimeline;
import com.butcher.anim.ChopTimeline.Creature;
import com.butcher.anim.ChopTimeline.Verdict;
import com.butcher.render.ChopScene;

/**
 * Offline checks for "The Chopping Block", and above all that nothing before the split depends
 * on what dropped - checked on the draw calls themselves, per creature, so the scene cannot leak
 * the verdict any more than the timeline can.
 *
 *   javac -d out -sourcepath src/main/java tools/ChopTest.java
 *   java -cp out ChopTest
 */
public final class ChopTest {

    static int passed, failed;

    public static void main(String[] args) {
        honest();
        timing();
        relic();
        brain();
        sting();
        hunt();
        fleece();
        heart();
        midas();
        glint();
        banner();
        simple();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static void check(boolean ok, String what) {
        if (ok) passed++;
        else { failed++; System.out.println("FAIL: " + what); }
    }

    // ------------------------------------------------------------------ the rule

    /** Every draw call of every frame before the split, hashed - the same for every verdict. */
    static void honest() {
        for (Creature c : Creature.values()) {
            for (long seed : new long[]{1, 7, 42, 1234567, -99}) {
                long first = 0;
                boolean same = true;
                Verdict differs = null;
                for (Verdict v : Verdict.values()) {
                    ChopTimeline tl = new ChopTimeline(v, c, seed);
                    Recorder r = new Recorder();
                    for (long t = 0; t < ChopTimeline.SPLIT; t += 7) ChopScene.draw(r, tl, t, 640, 360);
                    if (v == Verdict.BUST) first = r.hash;
                    else if (r.hash != first) { same = false; differs = v; }
                }
                check(same, c + " seed " + seed + ": the frames before the split are the same for every verdict"
                        + (differs == null ? "" : " (" + differs + " differs)"));
            }
        }
        // And the sound's clock: the colour cycle is fixed.
        long[] steps = ChopTimeline.CYCLE_STEPS;
        boolean rising = steps[0] == ChopTimeline.CYCLE;
        for (int i = 1; i < steps.length; i++) rising &= steps[i] > steps[i - 1] && steps[i] < ChopTimeline.SPLIT;
        check(rising, "the colour cycle's steps run from the wind-up to before the split");
        boolean shortening = true;
        for (int i = 2; i < steps.length; i++) shortening &= steps[i] - steps[i - 1] <= steps[i - 1] - steps[i - 2] + 1;
        check(shortening, "the cycle's steps get shorter");
    }

    // ------------------------------------------------------------------ the clock

    static void timing() {
        check(new ChopTimeline(Verdict.BUST, Creature.SPHINX, 3).total() <= 4500, "a bust is over in 4.5 s");
        for (Verdict v : Verdict.values()) {
            ChopTimeline tl = new ChopTimeline(v, Creature.of(v, 3), 3);
            boolean fades = tl.master(tl.total() - 1) < 0.02f && tl.dim(tl.total() - 1) < 0.02f;
            check(fades, v + ": the frame fades back to the game by its end");
        }
        // The cleaver: continuous (no frame jumps), and never raised during a bite.
        for (Verdict v : new Verdict[]{Verdict.BUST, Verdict.RELIC}) {
            ChopTimeline tl = new ChopTimeline(v, Creature.of(v, 9), 9);
            float[] prev = null;
            float worst = 0f;
            for (long t = 700; t < ChopTimeline.SPLIT + 900; t += 4) {
                float[] cl = tl.cleaver(t);
                if (cl == null) { prev = null; continue; }
                if (prev != null) worst = Math.max(worst, Math.abs(cl[2] - prev[2]));
                prev = cl;
            }
            check(worst < 0.45f, v + ": the cleaver never jumps more than 0.45 rad in 4 ms (" + worst + ")");
        }
        ChopTimeline tl = new ChopTimeline(Verdict.BUST, Creature.SPHINX, 5);
        boolean level = true;
        for (int k = 0; k < 3; k++) level &= Math.abs(tl.cleaver(ChopTimeline.STRIKES[k] + 20)[2]) < 0.01f;
        check(level, "the blade is level in the head on each blow");
        boolean shakes = true;
        for (int k = 0; k < 3; k++) shakes &= Math.abs(tl.shake(ChopTimeline.STRIKES[k] + 5)[0]) + Math.abs(tl.shake(ChopTimeline.STRIKES[k] + 5)[1]) > 0.3f;
        check(shakes, "every blow shakes the frame");
    }

    // ------------------------------------------------------------------ the Minos Relic

    static void relic() {
        for (long seed : new long[]{1, 2, 3, 99}) {
            ChopTimeline tl = new ChopTimeline(Verdict.RELIC, Creature.CHAMPION, seed);
            boolean home = true, loose = true, still = true;
            float jump = 0f;
            for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) for (int k = 0; k < 4; k++) {
                long at = tl.chunkLaunch(i, j, k);
                loose &= at < ChopTimeline.RELIC_CONVERGE - 800;
                still &= tl.chunk(i, j, k, ChopTimeline.RELIC_PEEL - 1) == null;
                float[] end = tl.chunk(i, j, k, ChopTimeline.RELIC_CONVERGE);
                float d = (float) Math.sqrt(sq(end[0] - ChopTimeline.MOON_X) + sq(end[1] - ChopTimeline.MOON_Y) + sq(end[2] - ChopTimeline.MOON_Z));
                home &= d < 1.5f && end[8] < 0.05f;
                // Where it comes loose it is exactly where it sat in the head.
                float[] first = tl.chunk(i, j, k, at);
                float cx = (i - 1.5f) * 2f, cy = ChopTimeline.HEAD + (j - 1.5f) * 2f, cz = (k - 1.5f) * 2f;
                jump = Math.max(jump, (float) Math.sqrt(sq(first[0] - cx) + sq(first[1] - cy) + sq(first[2] - cz)));
                float[] prev = first;
                for (long t = at + 8; t < ChopTimeline.RELIC_CONVERGE; t += 8) {
                    float[] f = tl.chunk(i, j, k, t);
                    jump = Math.max(jump, (float) Math.sqrt(sq(f[0] - prev[0]) + sq(f[1] - prev[1]) + sq(f[2] - prev[2])));
                    prev = f;
                }
            }
            check(still, "seed " + seed + ": nothing comes loose during the heartbeats");
            check(loose, "seed " + seed + ": every piece is loose well before the moon forms");
            check(home, "seed " + seed + ": every piece has reached the moon, and gone, when it ignites");
            check(jump < 1.2f, "seed " + seed + ": no piece jumps (" + jump + " texels in 8 ms at most)");
            check(tl.relicFaces(ChopTimeline.RELIC_SPIN - 1) == 0 && tl.relicFaces(ChopTimeline.RELIC_ARRIVE) == 0b111111,
                    "seed " + seed + ": the moon starts all moon and ends all Relic");
        }
    }

    // ------------------------------------------------------------------ Brain Food

    static void brain() {
        for (long seed : new long[]{1, 2, 3, 99}) {
            ChopTimeline tl = new ChopTimeline(Verdict.BRAIN_FOOD, Creature.SPHINX, seed);
            float[][] v = tl.veins();
            boolean joined = true, onBlock = false, ordered = true;
            for (int i = 0; i < v.length; i++) {
                int p = (int) v[i][7];
                if (p >= 0 && v[i][6] > 0f) {
                    float[] q = v[p];
                    joined &= Math.abs(q[3] - v[i][0]) < 1e-4 && Math.abs(q[4] - v[i][1]) < 1e-4 && Math.abs(q[5] - v[i][2]) < 1e-4;
                    ordered &= q[6] < v[i][6];
                }
                onBlock |= v[i][4] < 0.2f && (Math.abs(v[i][3]) > ChopTimeline.HEAD || Math.abs(v[i][5]) > ChopTimeline.HEAD);
            }
            check(v.length >= 60, "seed " + seed + ": a dense network (" + v.length + " veins)");
            check(joined, "seed " + seed + ": every vein grows from the end of its parent");
            check(ordered, "seed " + seed + ": every vein lights after its parent");
            check(onBlock, "seed " + seed + ": the veins spread out over the block");
            boolean dark = true;
            for (int i = 0; i < v.length; i++) dark &= tl.vein(i, ChopTimeline.BRAIN_BURST) < 0.02f;
            check(dark, "seed " + seed + ": the light has drained before the burst");
        }
    }

    // ------------------------------------------------------------------ Chimera I

    // ------------------------------------------------------------------ the Fateful Stinger

    static void sting() {
        for (long seed : new long[]{1, 2, 3, 99}) {
            ChopTimeline tl = new ChopTimeline(Verdict.FATEFUL_STINGER, Creature.MANTICORE, seed);
            com.butcher.anim.StingEnding e = tl.sting();
            float worst = 0f;
            float[] prev = null;
            for (long t = com.butcher.anim.StingEnding.RISE; t < com.butcher.anim.StingEnding.TURN; t += 8) {
                float[] p = e.tip(t);
                boolean stab = t > com.butcher.anim.StingEnding.STAB - 8 && t <= com.butcher.anim.StingEnding.IMPACT + 8;
                boolean rise = t < com.butcher.anim.StingEnding.RISE + 120;
                if (prev != null && !stab && !rise) worst = Math.max(worst, Math.abs(p[1] - prev[1]));
                prev = p;
            }
            check(worst < 0.9f, "seed " + seed + ": the barb never jumps outside the stab (" + worst + ")");
            check(e.tip(com.butcher.anim.StingEnding.IMPACT)[1] < 0.01f, "seed " + seed + ": the stab reaches the board");
            check(e.bead(com.butcher.anim.StingEnding.IMPACT) == 0f, "seed " + seed + ": the bead is gone when it stabs");
            int landed = 0;
            for (int k = 0; k < com.butcher.anim.StingEnding.SPLASH; k++) {
                float[] q = e.splash(k, com.butcher.anim.StingEnding.IMPACT + 800);
                if (q == null || q[5] > 0f) landed++;
            }
            check(landed == com.butcher.anim.StingEnding.SPLASH, "seed " + seed + ": every splash pixel has landed or fallen away within 0.8 s");
            check(Math.abs(e.tip(com.butcher.anim.StingEnding.TURN)[1] - com.butcher.anim.StingEnding.HERO_Y) < 0.01f,
                    "seed " + seed + ": the barb is up where the drop will be when it turns");
        }
        check(new ChopTimeline(Verdict.FATEFUL_STINGER, Creature.MANTICORE, 1).total() < 11000, "a cheap drop's ending is under 11 s");
    }

    // ------------------------------------------------------------------ the Mythological Dye

    static void hunt() {
        for (long seed : new long[]{1, 5, 42}) {
            ChopTimeline tl = new ChopTimeline(Verdict.DYE, Creature.of(Verdict.DYE, seed), seed);
            com.butcher.anim.HuntEnding e = tl.hunt();
            int heads = com.butcher.anim.HuntEnding.HEADS, king = com.butcher.anim.HuntEnding.KING;
            // The split goes wrong: shut, glowing, the cleaver wrenched away before the geyser.
            check(Math.abs(tl.halves(com.butcher.anim.HuntEnding.GEYSER - 1)[0]) < 0.05f, "seed " + seed + ": the halves stay shut until the geyser");
            check(tl.halves(com.butcher.anim.HuntEnding.GEYSER + 600)[0] > 1.2f, "seed " + seed + ": the geyser throws them open");
            check(tl.cleaver(com.butcher.anim.HuntEnding.GEYSER) == null, "seed " + seed + ": the cleaver is gone before the geyser");
            check(e.geyser(com.butcher.anim.HuntEnding.HUSH + 100) == 0f && e.geyser(com.butcher.anim.HuntEnding.GEYSER + 400) > 20f,
                    "seed " + seed + ": nothing in the hush, then the geyser stands");
            // The parade: each head lands in its own place round the ring, one after another, King Minos last.
            java.util.Set<Long> places = new java.util.HashSet<>();
            boolean ordered = true, onRing = true;
            long prevLand = -1;
            for (int k = 0; k < heads; k++) {
                long land = e.landing(k);
                ordered &= land > prevLand;
                prevLand = land;
                float[] p = e.ringPlace(k, land);
                float r = (float) Math.hypot(p[0], p[2]);
                onRing &= r > com.butcher.anim.HuntEnding.RING_R - 0.1f && r < com.butcher.anim.HuntEnding.RING_R + 2f;
                places.add(Math.round(Math.atan2(p[2], p[0]) * 100) * 1000L);
                float[] q = e.head(k, land + 50);
                check(q != null && Math.abs(q[0] - p[0]) < 0.05f && Math.abs(q[2] - p[2]) < 0.05f, "seed " + seed + ": head " + k + " is in its place once it lands");
            }
            check(ordered, "seed " + seed + ": the heads land one after another, King Minos last");
            check(onRing, "seed " + seed + ": every head lands on the ring");
            check(places.size() == heads, "seed " + seed + ": no two heads share a place");
            check(e.landing(king) < com.butcher.anim.HuntEnding.SPIN, "seed " + seed + ": all have landed before the ring turns");
            // Diana's shot: every head hit once, in turn, King Minos last; the arrow is where each head is as it hits.
            long prevHit = -1;
            boolean inTurn = true, through = true;
            for (int n = 0; n < heads; n++) {
                int k = e.hitOrder(n);
                long at = e.hit(k);
                inTurn &= at > prevHit;
                prevHit = at;
                float[] p = e.ringPlace(k, at), a = e.arrow(at);
                through &= a != null && Math.hypot(a[0] - p[0], Math.hypot(a[1] - p[1], a[2] - p[2])) < 0.5f;
                check(e.head(k, at - 1) != null && e.head(k, at) == null, "seed " + seed + ": head " + k + " bursts the moment it is hit");
            }
            check(inTurn, "seed " + seed + ": the arrow takes the heads in turn");
            check(e.hitOrder(heads - 1) == king, "seed " + seed + ": King Minos is the last");
            check(through, "seed " + seed + ": the arrow goes through each head where it is");
            float worst = 0f;
            float[] prev = null;
            for (long t = com.butcher.anim.HuntEnding.ARROW; t < com.butcher.anim.HuntEnding.SUCK; t += 8) {
                float[] a = e.arrow(t);
                if (prev != null && a != null) worst = Math.max(worst, (float) Math.hypot(a[0] - prev[0], Math.hypot(a[1] - prev[1], a[2] - prev[2])));
                prev = a;
            }
            check(worst < 3f, "seed " + seed + ": the arrow flies without jumping (" + worst + " texels in 8 ms at most)");
            check(prevHit < com.butcher.anim.HuntEnding.SUCK, "seed " + seed + ": every head is hit before the pull");
            // The reveal.
            float[] it = e.item(com.butcher.anim.HuntEnding.ARRIVE);
            check(Math.abs(it[0] - com.butcher.anim.HuntEnding.ITEM_Y) < 0.01f && it[1] > 0.99f, "seed " + seed + ": the dye is up in the column when it is shown");
            check(tl.blockFall(com.butcher.anim.HuntEnding.SHOCK + 400)[1] < 0.01f, "seed " + seed + ": the shockwave has blown the board away");
            // The sound: in order, silent in the hush, cut for the geyser and the shockwave.
            java.util.List<com.butcher.audio.HuntSound.Cue> cues = com.butcher.audio.HuntSound.schedule(tl);
            boolean cueOrder = true;
            int cuts = 0;
            for (int i = 0; i < cues.size(); i++) {
                if (i > 0 && cues.get(i).at < cues.get(i - 1).at) cueOrder = false;
                if (cues.get(i).cut) cuts++;
                check(cues.get(i).voices.length == 0 || cues.get(i).at < com.butcher.anim.HuntEnding.HUSH || cues.get(i).at >= com.butcher.anim.HuntEnding.GEYSER,
                        "seed " + seed + ": nothing sounds in the hush (" + cues.get(i).at + ")");
            }
            check(cueOrder && cuts == 3, "seed " + seed + ": its cues are in order, cut at the hush, the geyser and the shockwave");
        }
        ChopTimeline dye = new ChopTimeline(Verdict.DYE, Creature.SPHINX, 1);
        check(dye.total() > ChopTimeline.RELIC_END, "the Dye's is the longest Diana ending (" + dye.total() + ")");
        check(dye.total() < 20000, "and under 20 s");
    }

    // ------------------------------------------------------------------ Shimmering Wool

    static void fleece() {
        for (long seed : new long[]{1, 2, 3, 99}) {
            ChopTimeline tl = new ChopTimeline(Verdict.SHIMMERING_WOOL, Creature.KING_MINOS, seed);
            com.butcher.anim.FleeceEnding e = tl.fleece();
            float worst = 0f;
            for (int k = 0; k < com.butcher.anim.FleeceEnding.FIBRES; k++) {
                float[][] prev = null;
                for (long t = com.butcher.anim.FleeceEnding.UNRAVEL; t < com.butcher.anim.FleeceEnding.SQUARE; t += 8) {
                    float[][] p = e.fibre(k, t);
                    if (p != null && prev != null) {
                        for (int j = 0; j < p.length; j++) {
                            worst = Math.max(worst, (float) Math.sqrt(sq(p[j][0] - prev[j][0]) + sq(p[j][1] - prev[j][1]) + sq(p[j][2] - prev[j][2])));
                        }
                    }
                    prev = p;
                }
            }
            check(worst < 1.0f, "seed " + seed + ": no fibre jumps (" + worst + " texels in 8 ms at most)");
            boolean wound = true;
            for (int k = 0; k < com.butcher.anim.FleeceEnding.FIBRES; k++) {
                for (float[] q : e.fibre(k, com.butcher.anim.FleeceEnding.SQUARE)) {
                    float d = (float) Math.sqrt(sq(q[0]) + sq(q[1] - com.butcher.anim.FleeceEnding.SPIN_Y) + sq(q[2]));
                    wound &= d < com.butcher.anim.FleeceEnding.BALL * 1.35f;
                }
            }
            check(wound, "seed " + seed + ": every fibre is wound onto the ball when it squares off");
        }
        ChopTimeline tl = new ChopTimeline(Verdict.SHIMMERING_WOOL, Creature.KING_MINOS, 4);
        com.butcher.anim.FleeceEnding e = tl.fleece();
        check(e.halves(com.butcher.anim.FleeceEnding.WIND) < 0.01f, "the halves have unravelled away before the winding");
        check(Math.abs(e.cubeY(com.butcher.anim.FleeceEnding.ARRIVE) - com.butcher.anim.FleeceEnding.SPIN_Y) < 0.01f, "the wool settles on the spindle");
    }

    // ------------------------------------------------------------------ the Manti-core

    static void heart() {
        ChopTimeline tl = new ChopTimeline(Verdict.MANTI_CORE, Creature.MANTICORE, 6);
        com.butcher.anim.HeartEnding e = tl.heart();
        long[] b = com.butcher.anim.HeartEnding.BEATS;
        boolean quicker = true;
        for (int i = 2; i < b.length; i++) quicker &= b[i] - b[i - 1] < b[i - 1] - b[i - 2];
        check(quicker, "the heart beats faster and faster");
        check(b[b.length - 1] < com.butcher.anim.HeartEnding.FLAP, "the last beat comes before the flap");
        // The wings move smoothly through everything but the fling open, which is meant to snap.
        float worst = 0f;
        float[] prev = null;
        for (long t = com.butcher.anim.HeartEnding.UNFOLD; t < com.butcher.anim.HeartEnding.ARRIVE; t += 8) {
            float[] w = e.wing(t);
            if (prev != null) for (int k = 0; k < w.length; k++) worst = Math.max(worst, Math.abs(w[k] - prev[k]));
            prev = w;
        }
        check(worst < 0.2f, "the wings never jump before they are flung open (" + worst + " per 8 ms at most)");
        check(e.wing(com.butcher.anim.HeartEnding.WRAPPED)[0] > Math.PI / 2, "folded, each wing has crossed in front of the heart");
        check(e.blow(com.butcher.anim.HeartEnding.FLAPPED + 400)[3] < 0.01f, "the gust has blown the halves away");
        check(e.wingAlpha(tl.total() - 1) < 0.01f, "the wings have gone to smoke by the end");
    }

    // ------------------------------------------------------------------ the Crown of Greed

    static void midas() {
        ChopTimeline tl = new ChopTimeline(Verdict.CROWN_OF_GREED, Creature.KING_MINOS, 8);
        com.butcher.anim.MidasEnding e = tl.midas();
        // Every point of the open halves is gold before they burst.
        long latest = 0;
        for (float x = -8f; x <= 8f; x += 0.5f)
            for (float z = -4f; z <= 4f; z += 0.5f)
                for (float y = 0f; y <= 4f; y += 1f)
                    latest = Math.max(latest, e.gilded(x, y, z, (int) (x * 131 + z * 17 + y)));
        check(latest < com.butcher.anim.MidasEnding.SHINE, "the halves are all gold before the shine (" + latest + ")");
        check(e.standing(com.butcher.anim.MidasEnding.COLLAPSE - 1) && !e.standing(com.butcher.anim.MidasEnding.COLLAPSE), "the halves go to coins all at once");
        check(Math.abs(e.crown(com.butcher.anim.MidasEnding.ARRIVE)[0] - com.butcher.anim.MidasEnding.HERO_Y) < 0.05f, "the crown is up in the middle when it is presented");
        int gone = 0;
        for (int k = 0; k < com.butcher.anim.MidasEnding.COINS; k++) if (e.coin(k, com.butcher.anim.MidasEnding.ARRIVE + 1500) == null) gone++;
        check(gone == com.butcher.anim.MidasEnding.COINS, "every coin has fallen away before the price is read (" + gone + ")");
        check(tl.total() < 11000, "a cheap drop's ending is under 11 s");
    }

    // ------------------------------------------------------------------ Chimera I

    static void glint() {
        ChopTimeline tl = new ChopTimeline(Verdict.CHIMERA, Creature.INQUISITOR, 11);
        com.butcher.anim.GlintEnding e = tl.glint();
        float worst = 0f;
        int alive = 0;
        for (int k = 0; k < com.butcher.anim.GlintEnding.GLYPHS; k++) {
            float[] q = e.glyph(k, com.butcher.anim.GlintEnding.CONVERGE - 1);
            if (q == null) continue;
            alive++;
            worst = Math.max(worst, (float) Math.sqrt(sq(q[0]) + sq(q[1] - com.butcher.anim.GlintEnding.POINT_Y) + sq(q[2])));
        }
        check(alive > 0 && worst < 0.3f, "every glyph has spiralled into the point when they meet (" + worst + ")");
        check(e.drain(com.butcher.anim.GlintEnding.CONVERGE) < 0.01f, "the glint has drained out of the world by then");
        check(e.glint(0f, 2f, 0f, ChopTimeline.SPLIT) == 0f, "nothing glints before the split");
    }

    // ------------------------------------------------------------------ the banner

    static void banner() {
        boolean right = true;
        for (Verdict v : Verdict.values()) {
            ChopTimeline tl = new ChopTimeline(v, Creature.of(v, 1), 1);
            String b = tl.banner();
            boolean expensive = v == Verdict.RELIC || v == Verdict.SHIMMERING_WOOL || v == Verdict.MANTI_CORE || v == Verdict.CHIMERA || v == Verdict.DYE;
            right &= expensive == (b != null);
            if (b != null) right &= tl.bannerStamp(tl.arrive()) == null;
        }
        check(right, "only the expensive drops get the banner, and never before the drop is there");
    }

    // ------------------------------------------------------------------ the menu's plain endings

    static void simple() {
        boolean plain = true, same = true;
        for (Verdict v : Verdict.values()) {
            ChopTimeline a = new ChopTimeline(v, Creature.of(v, 3), 3).options(true, true, 1f);
            if (v.win()) plain &= a.ending() == null && !a.unmade() && !a.thinks() && a.total() == 8000 && a.banner() == null;
            // Nothing before the split may change with the setting either.
            ChopTimeline b = new ChopTimeline(v, Creature.of(v, 3), 3);
            Recorder ra = new Recorder(), rb = new Recorder();
            for (long t = 0; t < ChopTimeline.SPLIT; t += 11) {
                ChopScene.draw(ra, a, t, 640, 360);
                ChopScene.draw(rb, b, t, 640, 360);
            }
            same &= ra.hash == rb.hash;
        }
        check(plain, "the plain-endings setting gives every drop the plain ending, and no banner");
        check(same, "and nothing before the split changes with it");
    }

    static float sq(float x) { return x * x; }

    /** A canvas that only hashes what it is asked to draw. */
    static final class Recorder implements ChopScene.Canvas {
        long hash = 17;

        void mix(Object... parts) {
            for (Object o : parts) {
                long h = o instanceof Float f ? Math.round(f * 100) : o == null ? 0 : o.hashCode();
                hash = hash * 31 + h;
            }
        }

        public void quad(String tex, float ox, float oy, float ux, float uy, float vx, float vy, int u0, int v0, int uw, int vh, int argb) {
            mix("q", tex, ox, oy, ux, uy, vx, vy, u0, v0, uw, vh, argb);
        }
        public void sprite(String tex, float cx, float cy, float w, float h, float rot, int argb) { mix("s", tex, cx, cy, w, h, rot, argb); }
        public void line(float x0, float y0, float x1, float y1, float width, int argb) { mix("l", x0, y0, x1, y1, width, argb); }
        public void fill(float x, float y, float w, float h, int argb) { mix("f", x, y, w, h, argb); }
        public void item(float cx, float cy, float size, float alpha, float yaw) { mix("i", cx, cy, size, alpha, yaw); }
        public void name(float cx, float cy, float scale, int argb, int letters) { mix("n", cx, cy, scale, argb, letters); }
        public void text(String text, float cx, float cy, float scale, int argb) { mix("t", text, cx, cy, scale, argb); }
        public String creatureSkin(Creature creature) { return "skin_" + creature; }
        public String dropSkin() { return "drop"; }
    }
}
