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

    /** The ownership rules a friend's records settled (2026-10-12), played through the real watcher. */
    static void watcherChecks() {
        java.util.List<String> got = new java.util.ArrayList<>();
        com.butcher.detect.HellionWatcher.Listener l = new com.butcher.detect.HellionWatcher.Listener() {
            @Override public void kill() { got.add("open"); }
            @Override public void resolve(Drop d, String line) { got.add("resolve " + d); }
            @Override public void killKnown(Drop d, String line) { got.add("known " + d); }
        };
        String nm = "[Lv825]  Inferno Demonlord IV ";

        // The boss that comes with the quest: no spawn line, full health, five blocks off, the same tick.
        com.butcher.detect.HellionWatcher w = new com.butcher.detect.HellionWatcher(new com.butcher.detect.DecisionLog(), l);
        w.onChatLine("  SLAYER QUEST STARTED!", 1000);
        tick(w, 1030, new com.butcher.detect.HellionWatcher.Tag(1, "[Lv825] Inferno Demonlord IV 150M/150M❤", 25));
        tick(w, 30000, new com.butcher.detect.HellionWatcher.Tag(1, nm + "0/150M❤", 9));
        w.onChatLine("VERY RARE DROP! (Archfiend Dice) (+236%  Magic Find)", 31200);
        check("a boss spawned with the quest is ours, and its dice play", got.equals(java.util.List.of("open", "resolve ARCHFIEND_DICE")));

        // Ours leaves for a demon phase; another player's boss 21 blocks off comes in at the same health and dies first.
        got.clear();
        w = new com.butcher.detect.HellionWatcher(new com.butcher.detect.DecisionLog(), l);
        w.onChatLine("  SLAYER QUEST STARTED!", 0);
        w.onChatLine("SLAYER BOSS! The Inferno Demonlord IV spawned!", 20000);
        tick(w, 20030, new com.butcher.detect.HellionWatcher.Tag(10, nm + "150M/150M❤", 36));
        tick(w, 30000, new com.butcher.detect.HellionWatcher.Tag(10, nm + "98.8M/150M❤ ✯", 9));
        tick(w, 30050);
        tick(w, 36000, new com.butcher.detect.HellionWatcher.Tag(20, nm + "96.3M/150M❤", 21 * 21));
        tick(w, 44000, new com.butcher.detect.HellionWatcher.Tag(20, nm + "0/150M❤", 22 * 22));
        check("another player's boss 21 blocks off is not ours", got.isEmpty());
        tick(w, 45000, new com.butcher.detect.HellionWatcher.Tag(30, nm + "98.8M/150M❤", 16));
        tick(w, 70000, new com.butcher.detect.HellionWatcher.Tag(30, nm + "0/150M❤", 16));
        check("ours, back four blocks off, is ours", got.equals(java.util.List.of("open")));
        // A Shuriken's mark (the trailing star) on someone else's boss changes nothing.
        got.clear();
        w = new com.butcher.detect.HellionWatcher(new com.butcher.detect.DecisionLog(), l);
        w.onChatLine("  SLAYER QUEST STARTED!", 0);
        w.onChatLine("SLAYER BOSS! The Inferno Demonlord IV spawned!", 20000);
        tick(w, 20030, new com.butcher.detect.HellionWatcher.Tag(10, nm + "150M/150M❤", 36));
        tick(w, 25000, new com.butcher.detect.HellionWatcher.Tag(10, nm + "120M/150M❤", 36),
                new com.butcher.detect.HellionWatcher.Tag(40, nm + "40M/150M❤ ✯", 20 * 20));
        tick(w, 28000, new com.butcher.detect.HellionWatcher.Tag(10, nm + "110M/150M❤", 36),
                new com.butcher.detect.HellionWatcher.Tag(40, nm + "0/150M❤ ✯", 20 * 20));
        check("someone else's boss with a Shuriken mark is not ours", got.isEmpty());

        // Never claimed, but the drop line is ours: it plays at quest complete.
        got.clear();
        w = new com.butcher.detect.HellionWatcher(new com.butcher.detect.DecisionLog(), l);
        w.onChatLine("  SLAYER QUEST STARTED!", 0);
        w.onChatLine("VERY RARE DROP! (Scorched Power Crystal) (+236%  Magic Find)", 60000);
        w.onChatLine("  SLAYER QUEST COMPLETE!", 60250);
        check("an unclaimed kill's own win still plays", got.equals(java.util.List.of("known SCORCHED_CRYSTAL")));
    }

    static void tick(com.butcher.detect.HellionWatcher w, long now, com.butcher.detect.HellionWatcher.Tag... tags) {
        w.observe(java.util.List.of(tags), false, now);
        w.afterTick(now);
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
        // The wins, as a friend's records showed them (2026-10-12).
        is("VERY RARE DROP! (Archfiend Dice) (+236%  Magic Find)", Drop.ARCHFIEND_DICE, null);
        is("VERY RARE DROP! (High Class Archfiend Dice) (+236%  Magic Find)", Drop.HIGH_CLASS_DICE, null);
        is("VERY RARE DROP! (Duplex I) (+235%  Magic Find)", Drop.DUPLEX, null);
        // The rest, in that format (not seen yet).
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

        watcherChecks();

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
