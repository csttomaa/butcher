/*
 * Offline checks for the Thaw's schedule. Nothing in anim/ThawTimeline or detect/vanguard/
 * touches Minecraft, so every haul shape can be timed without the game.
 *
 *   javac -d out src/main/java/com/butcher/detect/vanguard/{VanguardLoot,CorpseHaul}.java \
 *         src/main/java/com/butcher/anim/ThawTimeline.java \
 *         src/main/java/com/butcher/render/model/{ThawMeshes,Mesh,BoxMesh}.java tools/ThawTest.java
 *   java -cp out ThawTest
 */
import com.butcher.anim.ThawTimeline;
import com.butcher.anim.ThawTimeline.*;
import com.butcher.detect.vanguard.*;
import com.butcher.render.model.ThawMeshes;
import java.util.*;

public class ThawTest {
    static int pass = 0, fail = 0;
    static void check(String what, boolean ok, String detail) {
        System.out.printf("  %s %-60s %s%n", ok ? "PASS" : "FAIL", what, detail);
        if (ok) pass++; else fail++;
    }
    static CorpseHaul haul(String... names) {
        List<CorpseHaul.Drop> d = new ArrayList<>();
        for (String n : names) d.add(new CorpseHaul.Drop(VanguardLoot.find(n), 1));
        return new CorpseHaul(d);
    }
    static CorpseHaul roll(Random r) {
        int n = 5 + r.nextInt(4);
        List<CorpseHaul.Drop> d = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int pick = r.nextInt(VanguardLoot.TOTAL_WEIGHT);
            for (VanguardLoot.Entry e : VanguardLoot.all()) { pick -= e.weight(); if (pick < 0) { d.add(new CorpseHaul.Drop(e, 1)); break; } }
        }
        return new CorpseHaul(d);
    }
    static final String[] FIVE = {"Glacite Amalgamation","Suspicious Scrap","Refined Umber","Blue Goblin Egg","Ice Cold I"};
    static String[] with(String last) { String[] a = Arrays.copyOf(FIVE, 6); a[5] = last; return a; }

    public static void main(String[] a) {
        System.out.println("durations, six drops:");
        Object[][] cases = {
            {"all commons", with("Refined Tungsten")},
            {"best uncommon", with("Umber Key")},
            {"best legendary", with("Skeleton Key")},
            {"the Locket", with("Shattered Locket")},
            {"the Dye", with("Frostbitten Dye")} };
        long[] totals = new long[cases.length];
        for (int i = 0; i < cases.length; i++) {
            ThawTimeline t = new ThawTimeline(haul((String[]) cases[i][1]));
            totals[i] = t.total();
            System.out.printf("  %-16s %-8s strikes end %5d  finale %5d  outro %5d  total %.1fs%n",
                cases[i][0], t.finale(), t.finaleStart(), t.outroStart() - t.finaleStart(),
                t.fadeStart() - t.outroStart(), t.total() / 1000f);
        }
        check("a loss is still the shortest run", totals[0] < totals[1] && totals[0] < totals[2], "");
        // The whole point: nothing before the last strike may depend on how the run ends.
        long lastStart = -1;
        boolean samePace = true;
        for (Object[] c : cases) {
            List<Strike> ss = new ThawTimeline(haul((String[]) c[1])).strikes();
            Strike final_ = ss.get(ss.size() - 1);
            if (lastStart < 0) lastStart = final_.climbStart();
            else if (final_.climbStart() != lastStart) samePace = false;
        }
        check("every ending strikes at the same pace until the climb", samePace, lastStart + "ms");
        check("the Locket gets its own, longer ending", new ThawTimeline(haul(with("Shattered Locket"))).finale() == Finale.LOCKET
              && totals[3] > totals[2], "");
        check("the Dye is the longest of all", totals[4] > totals[3], "");
        check("a Skeleton Key is still an ordinary explosion",
              new ThawTimeline(haul(with("Skeleton Key"))).finale() == Finale.EXPLODE, "");

        // Locket beats another legendary to the end of the reveal
        ThawTimeline both = new ThawTimeline(haul("Shattered Locket", "Skeleton Key", "Glacite Amalgamation"));
        List<Strike> bs = both.strikes();
        check("with a Locket and a Key, the Locket comes out last",
              bs.get(bs.size() - 1).drop().name().equals("Shattered Locket") && both.finale() == Finale.LOCKET,
              bs.get(bs.size() - 1).drop().name());

        ThawTimeline t = new ThawTimeline(haul(with("Skeleton Key")));
        List<Strike> s = t.strikes();
        check("strikes never overlap", ok(s), "");
        check("a legendary ending keeps its item off the tray until the end",
              !t.goesStraightToTray(s.get(s.size()-1)) && t.goesStraightToTray(s.get(0)), "");
        ThawTimeline loss = new ThawTimeline(haul(with("Refined Tungsten")));
        check("a loss sends every item straight to the tray",
              loss.strikes().stream().allMatch(loss::goesStraightToTray), "");

        // ---- the flare never lies, and the cycle lands on the truth
        Random r = new Random(1);
        int strikes = 0, honest = 0, climbs = 0, landsTrue = 0;
        java.util.Set<String> cycles = new java.util.HashSet<>();
        java.util.Set<Long> cycleLengths = new java.util.HashSet<>();
        for (int i = 0; i < 20000; i++) {
            CorpseHaul h = roll(r);
            ThawTimeline tl = new ThawTimeline(h);
            for (Strike st : tl.strikes()) {
                strikes++;
                if (st.flare() == ThawTimeline.flareOf(st.drop().tier())) honest++;
                if (!st.last()) continue;
                climbs++;
                int want = switch (st.drop().tier()) { case COMMON -> 1; case UNCOMMON -> 2; default -> 3; };
                int k = tl.climbStep(st, st.lands() - 1);
                if (k == ThawTimeline.LANDING && ThawTimeline.stepColour(st, k) == want) landsTrue++;
                StringBuilder seq = new StringBuilder();
                for (int step = 0; step < ThawTimeline.LANDING; step++) seq.append(ThawTimeline.stepColour(st, step));
                cycles.add(seq.toString());
                cycleLengths.add(tl.stepStart(st, ThawTimeline.LANDING) - st.climbStart());
            }
        }
        check("every flare is the true colour of its drop", honest == strikes, honest + "/" + strikes);
        check("the cycle always lands on the true colour", landsTrue == climbs, landsTrue + "/" + climbs);
        check("the colours before the landing are the same whatever the drop", cycles.size() == 1,
              cycles.toString());
        check("the cycle lasts the same whatever the drop", cycleLengths.size() == 1, cycleLengths + "ms");

        Strike lastLeg = s.get(s.size() - 1);
        boolean startsAgree = true;
        for (int k = 0; k <= ThawTimeline.LANDING; k++) {
            long at = t.stepStart(lastLeg, k);
            if (t.climbStep(lastLeg, at) != k || (k > 0 && t.climbStep(lastLeg, at - 1) != k - 1)) startsAgree = false;
        }
        check("stepStart is exactly where climbStep changes", startsAgree, "");
        long firstStep = t.stepStart(lastLeg, 2) - t.stepStart(lastLeg, 1);
        long lastStep = t.stepStart(lastLeg, ThawTimeline.LANDING) - t.stepStart(lastLeg, ThawTimeline.LANDING - 1);
        check("the cycle speeds up as it goes", lastStep < firstStep, firstStep + "ms -> " + lastStep + "ms");

        // ---- the ice and the man in it
        boolean allFit = true;
        StringBuilder poking = new StringBuilder();
        for (ThawMeshes.Pose pose : ThawMeshes.POSES) {
            for (ThawMeshes.Pose p : new ThawMeshes.Pose[]{pose, pose.mirrored()}) {
                for (float[] c : ThawMeshes.vanguardCorners(p)) {
                    if (!ThawMeshes.insideIce(c[0], c[1], c[2], 0.4f)) {
                        allFit = false;
                        if (poking.indexOf(p.name()) < 0) poking.append(p.name()).append(' ');
                    }
                }
            }
        }
        check("every pose stays inside the ice, both ways round", allFit, poking.toString().trim());
        float[] hit = ThawMeshes.strikePixel();
        ThawMeshes.IceFacet front = ThawMeshes.frontFacet();
        check("the pick lands on the front facet, clear of its edge",
              front.contains(hit[0], hit[1]) && front.edgeDistance(hit[0], hit[1]) > 6f,
              String.format("%.0f px from the edge", front.edgeDistance(hit[0], hit[1])));
        check("the ice has a handful of facets, not a gem's worth",
              ThawMeshes.iceFacets().size() >= 8 && ThawMeshes.iceFacets().size() <= 12,
              ThawMeshes.iceFacets().size() + " facets");

        ThawTimeline empty = new ThawTimeline(new CorpseHaul(List.of()));
        check("an empty haul still has a finite schedule", empty.total() > 0 && empty.strikes().isEmpty(), empty.total() + "ms");

        System.out.printf("%n%d passed, %d failed%n", pass, fail);
        if (fail > 0) System.exit(1);
    }
    static boolean ok(List<Strike> s) {
        for (int i = 1; i < s.size(); i++) if (s.get(i).start() < s.get(i-1).end()) return false;
        return true;
    }
}
