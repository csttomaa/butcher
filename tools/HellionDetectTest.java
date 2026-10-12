import com.butcher.anim.HellionTimeline.Drop;
import com.butcher.detect.HellionDrops;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Offline checks for the Inferno Demonlord's drop lines (HellionDrops), against the real lines of a friend's
 * tier IV session (2026-10-03), Magic Find symbol stripped as the game hands it to us. With a records file
 * (-Drecords=butcher-drops.jsonl) every recorded line is also run through, and none may throw.
 *
 *   javac -d out -sourcepath "src/main/java;tools" tools/HellionDetectTest.java
 *   java -cp out [-Drecords=...] HellionDetectTest
 */
public final class HellionDetectTest {
    static int passed, failed;

    static void check(String what, boolean ok) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    static void is(String line, Drop want, String item) {
        Drop got = HellionDrops.of(line);
        check("\"" + line + "\" -> " + want + " (got " + got + ")", got == want);
        if (item != null) check("\"" + line + "\" names \"" + item + "\" (got \"" + HellionDrops.item(line) + "\")", item.equals(HellionDrops.item(line)));
    }

    public static void main(String[] a) throws Exception {
        // The real lines.
        is("VERY RARE DROP! (Scorched Power Crystal) (+236%  Magic Find)", Drop.SCORCHED_CRYSTAL, "Scorched Power Crystal");
        is("RARE DROP! (Bundle of Magma Arrows) (+236%  Magic Find)", Drop.BUST, "Bundle of Magma Arrows");
        is("VERY RARE DROP! (Inferno Demonlord Shard) (+236%  Magic Find)", Drop.BUST, "Inferno Demonlord Shard");
        is("VERY RARE DROP! (5x Wisp's Ice-Flavored Water I Splash Potion) (+236%  Magic Find)", Drop.BUST,
                "Wisp's Ice-Flavored Water I Splash Potion");
        is("VERY RARE DROP! (Mana Disintegrator) (+236%  Magic Find)", Drop.BUST, "Mana Disintegrator");
        is("VERY RARE DROP! (Kelvin Inverter) (+204%  Magic Find)", Drop.BUST, "Kelvin Inverter");
        is("CRAZY RARE DROP! (◆ Lavatears Rune I) (+220%  Magic Find)", Drop.BUST, "Lavatears Rune I");
        is("VERY RARE DROP! (3x Glowstone Distillate) (+220%  Magic Find)", Drop.BUST, "Glowstone Distillate");
        // The wins, in that format (none seen yet).
        is("PRAY TO RNGESUS DROP! (High Class Archfiend Dice) (+236%  Magic Find)", Drop.HIGH_CLASS_DICE, null);
        is("CRAZY RARE DROP! (Archfiend Dice) (+236%  Magic Find)", Drop.ARCHFIEND_DICE, null);
        is("CRAZY RARE DROP! (Enchanted Book (Duplex I)) (+236%  Magic Find)", Drop.DUPLEX, "Enchanted Book (Duplex I)");
        is("CRAZY RARE DROP! (Paragon Shard) (+236%  Magic Find)", Drop.PARAGON, null);
        is("CRAZY RARE DROP! (Subzero Inverter) (+236%  Magic Find)", Drop.SUBZERO, null);
        is("CRAZY RARE DROP! (Wilson's Engineering Plans) (+236%  Magic Find)", Drop.WILSONS, null);
        is("PRAY TO RNGESUS DROP! (Flame Dye) (+236%  Magic Find)", Drop.FLAME_DYE, null);
        // Not drops of ours: lines that only look like one.
        is("RARE DROP! Netherrack-Looking Sunshade (+295  Magic Find)", null, null);
        is("RARE DROP! Brain Food (+315  Magic Find)", null, null);
        is("RARE DROP! Enchanted Book (Chimera I)", null, null);
        is("RARE DROP! Nether Star", null, null);
        is("Party > [MVP+] EvilJordan00: [SBO] RARE DROP! Chimera #2 (+25.0m coins)", null, null);
        is("[317] ☃ [MVP+] EvilJordan00: RARE DROP! Enchanted Book (Chimera I) (+340 Magic Find)", null, null);
        is("RNG METER! Reselected the Flame Dye for Blaze Slayer! CLICK HERE to select a new drop!", null, null);
        is("[SkyHanni] Removed Flame Dye from Inferno Demonlord IV Profit Tracker.", null, null);
        is("You set your Inferno Demonlord RNG Meter to drop Flame Dye!", null, null);
        is("  SLAYER QUEST COMPLETE!", null, null);
        is(null, null, null);

        String rec = System.getProperty("records");
        if (rec != null) {
            int lines = 0, drops = 0;
            for (String l : Files.readAllLines(Path.of(rec), StandardCharsets.UTF_8)) {
                int i = l.indexOf("\"line\": \"");
                if (i < 0) i = l.indexOf("\"line\":\"");
                if (i < 0) continue;
                String line = l.substring(l.indexOf('"', i + 6) + 1, l.lastIndexOf('"'));
                lines++;
                Drop d = HellionDrops.of(line.replaceAll("§.", ""));
                if (d != null) drops++;
            }
            System.out.println(lines + " recorded lines, " + drops + " read as slayer drops");
        }
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
