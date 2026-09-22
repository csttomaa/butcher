/*
 * Offline checks for the Vanguard corpse parser.
 *
 * None of detect/vanguard/ touches Minecraft, so the whole thing runs without the game -
 * which matters, because the alternative way to test it is to find a Glacite Mineshaft,
 * find a Fairy variant, and spend a Skeleton Key.
 *
 * DecisionLog does import ButcherClient, but only for its logger, so a four-line stub
 * stands in for it.
 *
 *   mkdir -p out/stub/com/butcher && cat > out/stub/com/butcher/ButcherClient.java <<'EOF'
 *   package com.butcher;
 *   import org.slf4j.Logger; import org.slf4j.LoggerFactory;
 *   public class ButcherClient { public static final Logger LOGGER = LoggerFactory.getLogger("butcher"); }
 *   EOF
 *   SLF=$(find ~/.gradle/caches/modules-2 -name 'slf4j-api-*.jar' ! -name '*sources*' | head -1)
 *   javac -d out/vg -cp "$SLF" out/stub/com/butcher/ButcherClient.java  *         src/main/java/com/butcher/detect/DecisionLog.java  *         src/main/java/com/butcher/detect/vanguard/*.java
 *   javac -d out/vg -cp "out/vg;$SLF" tools/VgTest.java && java -cp "out/vg;$SLF" VgTest
 *
 * (";" between classpath entries on Windows, ":" elsewhere.)
 */
import com.butcher.detect.DecisionLog;
import com.butcher.detect.vanguard.*;
import java.util.*;

public class VgTest {
    static int pass = 0, fail = 0;
    static void check(String what, boolean ok, String detail) {
        System.out.printf("  %s %-52s %s%n", ok ? "PASS" : "FAIL", what, detail);
        if (ok) pass++; else fail++;
    }

    static List<CorpseHaul> caught = new ArrayList<>();
    static VanguardWatcher fresh(DecisionLog log) {
        caught.clear();
        return new VanguardWatcher(log, caught::add);
    }

    public static void main(String[] a) {
        DecisionLog log = new DecisionLog();
        String RULE = "\u25AC".repeat(64);

        // ---------------- 1. the table itself
        int total = 0; for (VanguardLoot.Entry e : VanguardLoot.all()) total += e.weight();
        check("weights sum to the published total", total == VanguardLoot.TOTAL_WEIGHT, total + " vs 3516");
        VanguardLoot.Entry amal = VanguardLoot.find("Glacite Amalgamation");
        check("160/3516 is the wiki's 4.55%",
              Math.abs(VanguardLoot.find("Ice Cold I").perRoll() - 0.0455f) < 0.0005f,
              String.format("%.4f", VanguardLoot.find("Ice Cold I").perRoll()));
        float lc = VanguardLoot.legendaryChance(VanguardLoot.AVG_ROLLS);
        check("a legendary in about 1 corpse in 15", lc > 0.055f && lc < 0.075f,
              String.format("1 in %.1f", 1/lc));
        check("name match ignores case and punctuation",
              VanguardLoot.find("dwarven os metallic minis") != null, "apostrophe-free lookup");

        // ---------------- 2. a normal corpse
        VanguardWatcher w = fresh(log);
        String[] msg = {
            "                    VANGUARD CORPSE LOOT!",
            "  Glacite Amalgamation x2",
            "  Suspicious Scrap x16",
            "  Umber Key",
            "  Skeleton Key",
            "  Refined Tungsten x2",
            "  Mithril Plate",
            RULE };
        boolean allHidden = true;
        for (String line : msg) allHidden &= w.onChatLine(line);
        check("every line of the message is hidden from chat", allHidden, "");
        check("one haul emitted", caught.size() == 1, caught.size() + " emitted");
        CorpseHaul h = caught.get(0);
        check("all six drops kept", h.rolls() == 6, h.rolls() + " drops");
        check("counts parsed", h.drops().get(1).amount() == 16, "Suspicious Scrap x" + h.drops().get(1).amount());
        check("best tier is the Skeleton Key", h.best() == VanguardLoot.Tier.LEGENDARY, h.best().name());
        List<CorpseHaul.Drop> order = h.revealOrder();
        check("reveal order ends on the legendary",
              order.get(order.size()-1).name().equals("Skeleton Key"), order.get(order.size()-1).name());
        check("reveal order starts on a common",
              order.get(0).tier() == VanguardLoot.Tier.COMMON, order.get(0).name());
        check("watcher closed", !w.parsing(), "");

        // ---------------- 3. an all-commons corpse
        w = fresh(log);
        w.onChatLine("  VANGUARD CORPSE LOOT!");
        w.onChatLine("  Glacite Amalgamation");
        w.onChatLine("  Suspicious Scrap x8");
        w.onChatLine(RULE);
        check("an all-commons haul reports itself as a loss",
              caught.get(0).allCommon() && !caught.get(0).hasLegendary(), caught.get(0).describe());

        // ---------------- 4. unknown item name
        w = fresh(log);
        w.onChatLine("  VANGUARD CORPSE LOOT!");
        w.onChatLine("  Some Item Hypixel Renamed x3");
        w.onChatLine(RULE);
        check("an unknown name still parses as a common",
              caught.size() == 1 && caught.get(0).drops().get(0).unknown()
              && caught.get(0).drops().get(0).amount() == 3, "kept with its count");

        // ---------------- 5. the separator never arrives -> timeout keeps the haul
        w = fresh(log);
        w.onChatLine("  VANGUARD CORPSE LOOT!");
        w.onChatLine("  Skeleton Key");
        for (int i = 0; i < 45; i++) w.tick();
        check("a message with no closing rule still fires on timeout",
              caught.size() == 1 && !w.parsing(), caught.size() + " emitted");

        // ---------------- 6. SkyOcean's bug: does an unclosed message eat later chat?
        w = fresh(log);
        w.onChatLine("  VANGUARD CORPSE LOOT!");
        w.onChatLine("  Skeleton Key");
        boolean ate = w.onChatLine("Player123: hey what did you get");
        w.onChatLine(RULE);
        check("a foreign line is neither eaten nor collected as a drop",
              !ate && caught.size() == 1 && caught.get(0).rolls() == 1,
              "hidden=" + ate + ", drops=" + (caught.isEmpty() ? "-" : caught.get(0).rolls()));

        // ---------------- 7. line cap
        w = fresh(log);
        w.onChatLine("  VANGUARD CORPSE LOOT!");
        boolean stillEating = true;
        for (int i = 0; i < 40; i++) stillEating = w.onChatLine("  Glacite Amalgamation");
        check("the line cap stops a runaway parse", !stillEating && !w.parsing(), "");

        // ---------------- 8. second header mid-message
        w = fresh(log);
        w.onChatLine("  VANGUARD CORPSE LOOT!");
        w.onChatLine("  Skeleton Key");
        w.onChatLine("  VANGUARD CORPSE LOOT!");
        w.onChatLine("  Glacite Amalgamation");
        w.onChatLine(RULE);
        check("a second header restarts rather than merging",
              caught.size() == 1 && caught.get(0).rolls() == 1
              && caught.get(0).drops().get(0).name().equals("Glacite Amalgamation"),
              caught.get(0).describe());

        // ---------------- 9. colour codes
        w = fresh(log);
        w.onChatLine("\u00a76\u00a7l   VANGUARD CORPSE LOOT!");
        w.onChatLine("  \u00a7aFrostbitten Dye");
        w.onChatLine("\u00a78" + RULE);
        check("formatting codes are stripped before matching",
              caught.size() == 1 && caught.get(0).hasLegendary(), caught.isEmpty() ? "nothing" : caught.get(0).describe());

        // ---------------- 10. the exact names the NEU item repo carries, symbols and all
        String[][] real = {
            {"☠ Flawless Onyx Gemstone", "LEGENDARY?no"}, {"☘ Flawless Citrine Gemstone", ""},
            {"☘ Flawless Peridot Gemstone", ""}, {"☂ Flawless Aquamarine Gemstone", ""},
            {"Dwarven O's Metallic Minis", ""}, {"Shattered Locket", ""}, {"Frostbitten Dye", ""},
            {"Blue Goblin Egg", ""}, {"Glacite Amalgamation", ""}, {"Suspicious Scrap", ""},
            {"Refined Tungsten", ""}, {"Caged Wisp", ""}, {"Frozen Scute", ""}, {"Skeleton Key", ""},
            {"Umber Key", ""}, {"Tungsten Key", ""}, {"Mithril Plate", ""}, {"Umber Plate", ""},
            {"Tungsten Plate", ""}, {"Opal Crystal", ""}, {"Onyx Crystal", ""},
            {"Peridot Crystal", ""}, {"Citrine Crystal", ""}, {"Aquamarine Crystal", ""},
            {"Refined Umber", ""}, {"Ice Cold I", ""} };
        List<String> unmatched = new ArrayList<>();
        for (String[] r : real) if (VanguardLoot.find(r[0]) == null) unmatched.add(r[0]);
        check("every NEU display name resolves", unmatched.isEmpty(),
              unmatched.isEmpty() ? real.length + " names" : unmatched.toString());
        check("the gemstone symbol prefix does not break the match",
              VanguardLoot.find("☠ Flawless Onyx Gemstone") != null
              && VanguardLoot.find("☠ Flawless Onyx Gemstone").tier() == VanguardLoot.Tier.COMMON,
              "matched without the symbol");

        // ---------------- 11. sampling another corpse type
        w = fresh(log);
        boolean hid = w.onChatLine("  LAPIS CORPSE LOOT!");
        boolean hid2 = w.onChatLine("  Flawed Onyx Gemstone x3");
        w.onChatLine(RULE);
        check("a Lapis corpse is never hidden from chat", !hid && !hid2, "both visible");
        check("a Lapis corpse emits no haul", caught.isEmpty(), caught.size() + " emitted");
        check("a Lapis corpse leaves the Vanguard parser closed", !w.parsing(), "");

        // a Vanguard corpse still works right after sampling one
        for (String line : msg) w.onChatLine(line);
        check("a Vanguard corpse still parses after a sample", caught.size() == 1
              && caught.get(0).rolls() == 6, caught.isEmpty() ? "nothing" : caught.get(0).describe());

        // ---------------- 12. the real message, exactly as a Lapis corpse sent it
        // Captured 2026-09-23 by the sampler. The Vanguard message is the same shape.
        w = fresh(log);
        String[] real_msg = {
            "  VANGUARD CORPSE LOOT!",
            "",                                   // blank padding under the header
            "  REWARDS",                          // sub-heading, not a drop
            "    Skeleton Key",
            "    Glacite Amalgamation x4",
            "     ☠ Flawless Onyx Gemstone x2",   // symbol prefix, extra indent
            "    Suspicious Scrap x16",
            "[Skyblocker] Corpse Profit: 41,308", // another mod, flush to the margin
            RULE };
        for (String line : real_msg) w.onChatLine(line);
        check("the real message shape parses", caught.size() == 1,
              caught.size() + " emitted");
        CorpseHaul r = caught.get(0);
        check("REWARDS is not collected as a drop", r.rolls() == 4, r.rolls() + " drops");
        List<String> names = new ArrayList<>();
        for (CorpseHaul.Drop d : r.drops()) names.add(d.name());
        check("no phantom entries", !names.contains("REWARDS") && !names.toString().contains("Skyblocker"),
              names.toString());
        check("every real name resolved to a known item",
              r.drops().stream().noneMatch(CorpseHaul.Drop::unknown), names.toString());
        check("comma counts parse", r.drops().get(3).amount() == 16, "Suspicious Scrap x16");
        check("the symbol-prefixed gemstone resolved",
              r.drops().get(2).name().equals("Flawless Onyx Gemstone"), r.drops().get(2).name());
        check("best is still the Skeleton Key", r.hasLegendary(), r.describe());

        // ---------------- 13. a chat-compacting mod folding repeated lines
        // Seen in a screenshot 2026-09-23: "LAPIS CORPSE LOOT! (2)", "REWARDS (2)".
        // Untreated, the header stops matching and the corpse is never noticed at all.
        w = fresh(log);
        w.onChatLine("  VANGUARD CORPSE LOOT! (2)");
        w.onChatLine("  REWARDS (2)");
        w.onChatLine("    Skeleton Key (2)");
        w.onChatLine("    Glacite Amalgamation x4 (3)");
        w.onChatLine(RULE + " (2)");
        check("a collapsed header is still recognised", caught.size() == 1,
              caught.size() + " emitted");
        if (caught.size() == 1) {
            CorpseHaul c = caught.get(0);
            check("the collapse counter is not collected as a drop", c.rolls() == 2, c.rolls() + " drops");
            check("a collapsed item name still resolves",
                  !c.drops().get(0).unknown() && c.drops().get(0).name().equals("Skeleton Key"),
                  c.drops().get(0).name());
            check("a collapsed count is still read", c.drops().get(1).amount() == 4,
                  "x" + c.drops().get(1).amount());
            check("a collapsed rule still closes the message", !w.parsing(), "");
        }

        // ---------------- 14. the second real sample, verbatim (a Lapis corpse)
        // Its items are Lapis-pool, so none are in the Vanguard table - they must still
        // parse, as unknown commons, rather than derailing anything.
        w = fresh(log);
        String[] lapis = {
            "  LAPIS CORPSE LOOT!", "  REWARDS", "    Green Goblin Egg",
            "     Fine Citrine Gemstone x2", "    Enchanted Umber x2",
            "     Flawed Aquamarine Gemstone x20", "    Glacite Powder x4,401", RULE };
        boolean anyHidden = false;
        for (String line : lapis) anyHidden |= w.onChatLine(line);
        check("the second real sample is sampled, not animated",
              caught.isEmpty() && !anyHidden && !w.parsing(), "nothing emitted, nothing hidden");

        // ---------------- 15. display colours
        int noColour = 0; String sample = "";
        for (VanguardLoot.Entry e : VanguardLoot.all()) {
            if ((e.colour() >>> 24) == 0) { noColour++; sample = e.name(); }
        }
        check("every row has an opaque display colour", noColour == 0,
              noColour == 0 ? "26 rows" : noColour + " missing, e.g. " + sample);
        check("colour and tier are independent",
              VanguardLoot.find("Flawless Onyx Gemstone").tier() == VanguardLoot.Tier.COMMON
              && VanguardLoot.find("Flawless Onyx Gemstone").colour() == 0xFFAA00AA,
              "a dark-purple common, as intended");
        check("an unknown item still gets a drawable colour",
              (VanguardLoot.findOrCommon("Whatever Hypixel Adds Next").colour() >>> 24) == 0xFF,
              "falls back to white");

        // ---------------- 16. the Dye's own tier
        VanguardLoot.Entry dye = VanguardLoot.find("Frostbitten Dye");
        check("the Dye is SPECIAL, not LEGENDARY", dye.tier() == VanguardLoot.Tier.SPECIAL,
              dye.tier().name());
        check("SPECIAL outranks LEGENDARY",
              VanguardLoot.Tier.SPECIAL.ordinal() > VanguardLoot.Tier.LEGENDARY.ordinal(),
              "so it sorts last and reads as best");
        check("the Dye glows pink, not gold",
              VanguardLoot.Tier.SPECIAL.glow() != VanguardLoot.Tier.LEGENDARY.glow()
              && VanguardLoot.Tier.SPECIAL.glow() == 0xFFFF5FC8,
              String.format("#%06X", VanguardLoot.Tier.SPECIAL.glow() & 0xFFFFFF));
        check("the Dye keeps its own dark-aqua item colour",
              dye.colour() == 0xFF00AAAA, "glow and item colour stay separate");

        // a haul with both a Dye and a Skeleton Key must end on the Dye
        w = fresh(log);
        w.onChatLine("  VANGUARD CORPSE LOOT!");
        w.onChatLine("    Skeleton Key");
        w.onChatLine("    Frostbitten Dye");
        w.onChatLine("    Glacite Amalgamation");
        w.onChatLine(RULE);
        CorpseHaul d = caught.get(0);
        check("a Dye outranks a Skeleton Key in the reveal order",
              d.headline().name().equals("Frostbitten Dye"), d.headline().name());
        check("a Dye haul still counts as having a legendary",
              d.hasLegendary() && d.hasSpecial(), "both true, as intended");
        check("the expected rate still includes the Dye",
              VanguardLoot.legendaryChance(VanguardLoot.AVG_ROLLS) > 0.06f,
              String.format("1 in %.1f", 1/VanguardLoot.legendaryChance(VanguardLoot.AVG_ROLLS)));

        // ---------------- 17. the third real sample: a "+1 bonus drop!" preamble
        // Captured 2026-09-23. The notice sits above REWARDS, indented exactly like an
        // item, so it used to be collected as a drop and inflate the roll count.
        w = fresh(log);
        String[] bonus = {
            "  VANGUARD CORPSE LOOT!",
            "  +1 bonus drop! ",              // the preamble
            "",
            "  REWARDS",
            "    Skeleton Key",
            "   ☠ Flawless Onyx Gemstone x20",
            "    Glacite Amalgamation x2",
            RULE };
        for (String line : bonus) w.onChatLine(line);
        check("the bonus-drop notice is not collected", caught.size() == 1
              && caught.get(0).rolls() == 3,
              caught.isEmpty() ? "nothing" : caught.get(0).rolls() + " drops");
        List<String> bn = new ArrayList<>();
        for (CorpseHaul.Drop bd : caught.get(0).drops()) bn.add(bd.name());
        check("no phantom bonus-drop entry", !bn.toString().contains("bonus"), bn.toString());
        check("the gemstone with its symbol still resolved",
              caught.get(0).drops().stream().noneMatch(CorpseHaul.Drop::unknown), bn.toString());

        // and the fallback: a message with no heading at all keeps everything
        w = fresh(log);
        w.onChatLine("  VANGUARD CORPSE LOOT!");
        w.onChatLine("    Skeleton Key");
        w.onChatLine("    Glacite Amalgamation");
        w.onChatLine(RULE);
        check("a message with no heading keeps its drops",
              caught.size() == 1 && caught.get(0).rolls() == 2,
              caught.isEmpty() ? "nothing" : caught.get(0).rolls() + " drops");

        System.out.printf("%n%d passed, %d failed%n", pass, fail);
        if (fail > 0) System.exit(1);
    }
}
