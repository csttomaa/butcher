import com.butcher.anim.EruptionTimeline.Creature;
import com.butcher.anim.EruptionTimeline.Drop;
import com.butcher.detect.DecisionLog;
import com.butcher.detect.LavaDrops;
import com.butcher.detect.LavaWatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline checks for lava fishing's detection, against the real lines from the user's own records
 * (2026-10-05, 0.2.69 - no lava nametags yet, those came in 0.2.71).
 *
 *   javac -d out -cp <fabric+mc classpath> -sourcepath "src/main/java;tools" tools/LavaDetectTest.java
 */
public final class LavaDetectTest {
    static int passed, failed;

    static void check(String what, boolean ok) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    public static void main(String[] a) {
        // The drop lines, as Hypixel sends them: no brackets round the item.
        check("Flash I", LavaDrops.of("RARE DROP! Enchanted Book (Flash I) (+436  Magic Find)") == Drop.FLASH);
        check("a common lava drop is a bust", LavaDrops.of("RARE DROP! Moogma Leggings (+281  Magic Find)") == Drop.BUST);
        check("Pitchin' Koi is a bust", LavaDrops.of("RARE DROP! Pitchin' Koi (+231  Magic Find)") == Drop.BUST);
        check("a Nether Star is a bust", LavaDrops.of("RARE DROP! Nether Star") == Drop.BUST);
        check("Charm I is not Flash I", LavaDrops.of("RARE DROP! Enchanted Book (Charm I) (+100  Magic Find)") == Drop.BUST);
        check("a loot share line is not a drop", LavaDrops.of("LOOT SHARE You received loot for assisting EvilJordan00!") == null);
        check("a slayer's bracketed drop is not a lava win", LavaDrops.of("VERY RARE DROP! (◆ Endersnake Rune I) (+12%  Magic Find)") == Drop.BUST);
        // Loot share, with its colour codes as it arrived.
        check("a loot share naming one Thunder Shard", LavaDrops.ofShare("§e§lLOOT SHARE §fYou received a §9Thunder §fShard for assisting §bNicolasmv7§f!") == Creature.THUNDER);
        check("two Thunder Shards", LavaDrops.ofShare("§e§lLOOT SHARE §fYou received §b2 §9Thunder §fShards for assisting §bfran01§f!") == Creature.THUNDER);
        check("Lord Jawbus Shards", LavaDrops.ofShare("§e§lLOOT SHARE §fYou received §b2 §6Lord Jawbus §fShards for assisting §bMarkoo_04§f!") == Creature.JAWBUS);
        check("loot for assisting names nobody", LavaDrops.ofShare("LOOT SHARE You received loot for assisting EvilJordan00!") == LavaDrops.SHARE_UNNAMED);
        check("a player called Thunder-something names nobody", LavaDrops.ofShare("LOOT SHARE You received loot for assisting ThunderBoy!") == LavaDrops.SHARE_UNNAMED);
        // Catch lines: Hypixel's, and Feesh's rewrite.
        check("Thunder's catch line", LavaDrops.ofCatch("You hear a massive rumble as Thunder emerges.") == Creature.THUNDER);
        check("Feesh's rewrite", LavaDrops.ofCatch("A Thunder has spawned!") == Creature.THUNDER);
        check("Jawbus's catch line", LavaDrops.ofCatch("You have angered a legendary creature... Lord Jawbus has arrived.") == Creature.JAWBUS);
        check("a shard line is not a catch", LavaDrops.ofCatch("You received 2 Thunder Shards") == null);
        check("a death message is not a catch", LavaDrops.ofCatch("☠ You were killed by Ragnarok.") == null);
        check("a charm", LavaDrops.ofCharm("CHARM! You charmed the Thunder and received 2 Thunder Shards!") == Creature.THUNDER);

        // The real sequence: the share line, then 36 ms later Flash I - no nametag seen. It opens, known.
        List<String> opened = new ArrayList<>();
        LavaWatcher w = new LavaWatcher(new DecisionLog(), new LavaWatcher.Listener() {
            public void kill(Creature c, String why) { opened.add("kill " + c); }
            public void resolve(Drop d, String line) { opened.add("resolve " + d); }
            public void killKnown(Creature c, Drop d, String line) { opened.add("known " + c + " " + d); }
        });
        long t = 1_000_000;
        w.onChatLine("§e§lLOOT SHARE §fYou received a §9Thunder §fShard for assisting §bNicolasmv7§f!", t);
        w.onChatLine("§e§lLOOT SHARE §fYou received §b2 §9Thunder §fShards for assisting §bNicolasmv7§f!", t + 12_000);
        w.onChatLine("RARE DROP! Enchanted Book (Flash I) (+436  Magic Find)", t + 12_036);
        check("a loot-shared Thunder's Flash I opens Eruption, known (" + opened + ")", opened.equals(List.of("known THUNDER FLASH")));
        // The other way round: the drop first, the share line just after.
        opened.clear();
        w.onChatLine("RARE DROP! Enchanted Book (Flash I) (+436  Magic Find)", t + 60_000);
        w.onChatLine("LOOT SHARE You received 2 Thunder Shards for assisting fran01!", t + 60_040);
        check("the drop before its share line opens too (" + opened + ")", opened.equals(List.of("known THUNDER FLASH")));
        // A drop no creature of the share could give is not claimed by it.
        opened.clear();
        w.onChatLine("LOOT SHARE You received 2 Lord Jawbus Shards for assisting Markoo_04!", t + 120_000);
        w.onChatLine("RARE DROP! Enchanted Book (Flash I) (+436  Magic Find)", t + 120_030);
        check("Flash I is not Jawbus's (" + opened + ")", opened.isEmpty());
        // Busts alone, and an unnamed share, open nothing.
        opened.clear();
        w.onChatLine("LOOT SHARE You received loot for assisting EvilJordan00!", t + 200_000);
        w.onChatLine("RARE DROP! Moogma Leggings (+281  Magic Find)", t + 200_010);
        check("a common drop after a share opens nothing (" + opened + ")", opened.isEmpty());

        // The false triggers (the user, 2026-10-06): a party member's Jawbus, loot-shared, dying 6 blocks away.
        String jaw = "﴾ [Lv600]  Lord Jawbus 69.3M/100M❤ ﴿ ✯", jawLow = "﴾ [Lv600]  Lord Jawbus 2.1M/100M❤ ﴿ ✯",
                jawDead = "﴾ [Lv600]  Lord Jawbus 0/100M❤ ﴿ ✯";
        long u = t + 400_000;
        opened.clear();
        w.observe(List.of(new LavaWatcher.Tag(50, jaw, 36)), false, u);
        w.observe(List.of(new LavaWatcher.Tag(50, jawLow, 36)), false, u + 20_000);
        w.arm(5, u + 20_000);
        check("someone else's Jawbus does not arm the hide", !w.armedAt(u + 20_050));
        w.onChatLine("§e§lLOOT SHARE §fYou received §b2 §6Lord Jawbus §fShards for assisting §bslurpee6§f!", u + 21_000);
        w.observe(List.of(new LavaWatcher.Tag(50, jawDead, 36)), false, u + 21_040);
        w.onChatLine("RARE DROP! Magma Lord Fragment (+281  Magic Find)", u + 22_000);
        w.afterTick(u + 26_000);
        check("someone else's loot-shared Jawbus with no rare drop opens nothing (" + opened + ")", opened.isEmpty());
        // The same, through the vanish instead of the 0.
        opened.clear();
        w.observe(List.of(new LavaWatcher.Tag(51, jaw, 36)), false, u + 40_000);
        w.observe(List.of(new LavaWatcher.Tag(51, jawLow, 36)), false, u + 60_000);
        w.onChatLine("LOOT SHARE You received loot for assisting NightCry!", u + 60_020);
        w.observe(List.of(), false, u + 60_050);
        w.afterTick(u + 65_000);
        check("someone else's Jawbus vanishing opens nothing (" + opened + ")", opened.isEmpty());
        // But a win of its own coming to us by loot share plays it, known.
        opened.clear();
        String thu = "[Lv400]  Thunder 3M/35M❤", thuDead = "[Lv400]  Thunder 0/35M❤";
        w.observe(List.of(new LavaWatcher.Tag(52, thu, 36)), false, u + 80_000);
        w.onChatLine("LOOT SHARE You received loot for assisting andreflooo!", u + 81_000);
        w.observe(List.of(new LavaWatcher.Tag(52, thuDead, 36)), false, u + 81_030);
        w.onChatLine("RARE DROP! Enchanted Book (Flash I) (+436  Magic Find)", u + 81_900);
        check("someone else's Thunder whose Flash I comes to us opens, known (" + opened + ")", opened.equals(List.of("known THUNDER FLASH")));
        // And our own still opens on the kill, pending, and arms the hide.
        opened.clear();
        w.onChatLine("You have angered a legendary creature... Lord Jawbus has arrived.", u + 100_000);
        w.observe(List.of(new LavaWatcher.Tag(53, jaw, 25)), false, u + 100_500);
        w.observe(List.of(new LavaWatcher.Tag(53, jawLow, 25)), false, u + 120_000);
        w.arm(5, u + 120_000);
        check("our own Jawbus arms the hide", w.armedAt(u + 120_050));
        w.observe(List.of(new LavaWatcher.Tag(53, jawDead, 25)), false, u + 121_000);
        w.afterTick(u + 125_000);
        check("our own Jawbus opens on the kill and busts (" + opened + ")", opened.equals(List.of("kill JAWBUS", "resolve BUST")));
        // A charm on a party member's Thunder (the user, 23:19) opens nothing; on ours it is the kill.
        opened.clear();
        w.observe(List.of(new LavaWatcher.Tag(54, thu, 36)), false, u + 140_000);
        w.onChatLine("§b§lCHARM! §7You charmed the §cThunder §7and received §a2 §9Thunder Shards§7!", u + 141_000);
        w.afterTick(u + 145_000);
        check("a charm on someone else's Thunder opens nothing (" + opened + ")", opened.isEmpty());
        w.onChatLine("You hear a massive rumble as Thunder emerges.", u + 160_000);
        w.observe(List.of(new LavaWatcher.Tag(55, thu, 36)), false, u + 160_300);
        w.onChatLine("§b§lCHARM! §7You charmed the §cThunder §7and received §a2 §9Thunder Shards§7!", u + 170_000);
        w.afterTick(u + 174_000);
        check("a charm on our own Thunder opens it (" + opened + ")", opened.equals(List.of("kill THUNDER", "resolve BUST")));
        // Our Thunder gone at 7% with a Thunder loot share in the same second (23:38): a party member's last hit.
        opened.clear();
        String thuMid = "[Lv400]  Thunder 2.6M/35M❤";
        w.onChatLine("You hear a massive rumble as Thunder emerges.", u + 180_000);
        w.observe(List.of(new LavaWatcher.Tag(57, thuMid, 25)), false, u + 180_200);
        w.observe(List.of(), false, u + 187_000);
        w.onChatLine("§e§lLOOT SHARE §fYou received §b2 §9Thunder §fShards for assisting §bAntoniaaa§f!", u + 187_300);
        w.afterTick(u + 191_000);
        check("our Thunder gone at 7% then a Thunder loot share is our kill (" + opened + ")", opened.equals(List.of("kill THUNDER", "resolve BUST")));
        // Jawbus floats up immune: the same entity, no health on its nametag for 4 s (2026-10-05 records). At 3%, 0.2.82
        // took that for its death. It must open nothing, and the real 0 afterwards must still open the kill.
        opened.clear();
        String jawImm = "﴾ [Lv600]  Lord Jawbus ﴿ ✯", jaw3 = "﴾ [Lv600]  Lord Jawbus 3M/100M❤ ﴿ ✯";
        w.onChatLine("You have angered a legendary creature... Lord Jawbus has arrived.", u + 300_000);
        w.observe(List.of(new LavaWatcher.Tag(60, jaw, 25)), false, u + 300_200);
        w.observe(List.of(new LavaWatcher.Tag(60, jaw3, 25)), false, u + 320_000);
        for (int k = 1; k <= 80; k++) w.observe(List.of(new LavaWatcher.Tag(60, jawImm, 400)), false, u + 320_000 + 50L * k);
        w.afterTick(u + 324_500);
        check("Jawbus floating up immune opens nothing (" + opened + ")", opened.isEmpty());
        w.observe(List.of(new LavaWatcher.Tag(60, jaw3, 36)), false, u + 324_100);
        w.observe(List.of(new LavaWatcher.Tag(60, jawDead, 36)), false, u + 326_000);
        w.afterTick(u + 330_000);
        check("its real death after the float still opens (" + opened + ")", opened.equals(List.of("kill JAWBUS", "resolve BUST")));
        // Our Ragnarok's win, then its nametag gone at 6% (the records' stale HP, 20:21): still its kill.
        opened.clear();
        String rag = "﴾ [Lv500]  Ragnarok 7.6M/125M❤ ﴿";
        w.onChatLine("The sky darkens and the air thickens. The end times are upon us: Ragnarok is here.", u + 200_000);
        w.observe(List.of(new LavaWatcher.Tag(56, rag, 49)), false, u + 200_100);
        w.onChatLine("RARE DROP! Burnt Texts (+384  Magic Find)", u + 240_000);
        w.observe(List.of(), false, u + 240_090);
        check("our Ragnarok gone just after its Burnt Texts opens, known (" + opened + ")", opened.equals(List.of("known RAGNAROK BURNT_TEXTS")));

        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
