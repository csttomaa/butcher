import com.butcher.anim.SeraphTimeline.Drop;
import com.butcher.detect.DecisionLog;
import com.butcher.detect.SeraphDrops;
import com.butcher.detect.SeraphWatcher;

import java.io.BufferedReader;
import java.io.FileReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline checks for the Voidgloom Seraph's detection, and a replay of a real records file through the real watcher.
 *
 *   java -Drecords=<butcher-drops.jsonl> -Dself=<the recorder's name> -cp "<mc classpath>;build/classes/java/main;out" SeraphDetectTest
 *
 * Settled against a friend's session (2026-10-05): their three tier IV kills among other players' bosses.
 */
public final class SeraphDetectTest {
    static int passed, failed;

    static void check(String what, boolean ok) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    public static void main(String[] a) throws Exception {
        // Drop lines, as they came.
        check("a rune is a bust", SeraphDrops.of("VERY RARE DROP! (◆ Endersnake Rune I) (+221%  Magic Find)") == Drop.BUST);
        check("Null Atom is a bust", SeraphDrops.of("VERY RARE DROP! (Null Atom) (+216%  Magic Find)") == Drop.BUST);
        check("a stack is a bust", SeraphDrops.of("RARE DROP! (60x Twilight Arrow Poison) (+221%  Magic Find)") == Drop.BUST);
        check("a Judgement Core", SeraphDrops.of("CRAZY RARE DROP! (Judgement Core) (+221%  Magic Find)") == Drop.JUDGEMENT_CORE);
        check("the Upgrader", SeraphDrops.of("CRAZY RARE DROP! (Exceedingly Rare Ender Artifact Upgrader) (+221%  Magic Find)") == Drop.ARTIFACT_UPGRADE);
        check("an Enchant Rune", SeraphDrops.of("VERY RARE DROP! (◆ Enchant Rune I) (+221%  Magic Find)") == Drop.ENCHANT_RUNE);
        check("the Byzantium Dye", SeraphDrops.of("PRAY TO RNGESUS DROP! (Byzantium Dye) (+221%  Magic Find)") == Drop.BYZANTIUM_DYE);
        check("an unbracketed mini-boss drop is not ours", SeraphDrops.of("RARE DROP! Enchanted Ender Pearl (+319  Magic Find)") == null);
        check("guild chat quoting a drop is not ours", SeraphDrops.of("Guild > [MVP++] Someone: VERY RARE DROP! (Judgement Core)") == null);

        String file = System.getProperty("records");
        if (file != null) replay(file, System.getProperty("self", "andreflooo"));

        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    /** Plays a records file's Seraph nametags and slayer lines back through the watcher at their times. */
    static void replay(String file, String self) throws Exception {
        List<String> events = new ArrayList<>();
        DecisionLog log = new DecisionLog();
        SeraphWatcher w = new SeraphWatcher(log, new SeraphWatcher.Listener() {
            public void kill() { events.add("kill"); }
            public void resolve(Drop d, String line) { events.add("resolve " + d); }
            public void killKnown(Drop d, String line) { events.add("known " + d); }
        });
        w.self = self;
        Map<Integer, SeraphWatcher.Tag> present = new LinkedHashMap<>();
        long last = -1;
        try (BufferedReader in = new BufferedReader(new FileReader(file, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(line).getAsJsonObject();
                String kind = o.get("kind").getAsString();
                long now = Instant.parse(o.get("time").getAsString()).toEpochMilli();
                // Ticks in between, so the wait for a drop runs out as it would.
                if (last >= 0) for (long t = last + 50; t < now; t += 50) w.afterTick(t);
                last = now;
                if (kind.equals("slayer_line")) {
                    w.onChatLine(o.get("line").getAsString(), now);
                } else if (kind.equals("slayer_mob")) {
                    int id = o.get("entity").getAsInt();
                    String name = o.get("name").getAsString();
                    double d = o.has("distance") ? o.get("distance").getAsDouble() : 0;
                    String ev = o.get("event").getAsString();
                    // A nametag's last name, then (if it went) its going: the game would have read that name first.
                    present.put(id, new SeraphWatcher.Tag(id, name, d * d));
                    w.observe(new ArrayList<>(present.values()), false, now);
                    if (ev.equals("gone")) {
                        present.remove(id);
                        w.observe(new ArrayList<>(present.values()), false, now + 1);
                    }
                }
                w.afterTick(now);
            }
        }
        System.out.println("replay: " + events);
        // The recorder's own kills in that file: the 19:05 one read 0 and dropped a Null Atom (a bust); the 19:06 one
        // went at 0; the 14:55 one (Endersnake Rune) was before its nametag was ever in range of the records.
        long kills = events.stream().filter(e -> e.equals("kill") || e.startsWith("known")).count();
        check("every kill of the recorder's own opened (" + events + ")", kills >= 2);
        check("no other player's boss opened anything", kills <= 3);
        check("every opened kill was resolved", events.stream().filter(e -> e.equals("kill")).count()
                == events.stream().filter(e -> e.startsWith("resolve")).count());
        check("none of them was a win (the file has none)", events.stream().noneMatch(e -> e.contains(" ") && !e.endsWith("BUST")));
    }
}
