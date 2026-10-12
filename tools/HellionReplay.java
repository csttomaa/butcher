import com.butcher.anim.HellionTimeline.Drop;
import com.butcher.detect.DecisionLog;
import com.butcher.detect.HellionWatcher;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plays a records file's slayer lines and Demonlord nametags back through the real HellionWatcher, at their
 * recorded times, and prints what it decides - every kill opened, every drop resolved. The nametags were
 * logged at most once a second, so between records the last name of each stand is held, as the game would
 * show it; a "gone" record is the stand's last name, seen once more before it goes.
 *
 * Needs the game's classes on the classpath (the watcher names them), as SeraphPreview does.
 *
 *   java -cp "<mc classpath>;out" HellionReplay butcher-drops.jsonl
 */
public final class HellionReplay {

    public static void main(String[] a) throws Exception {
        List<String> opened = new ArrayList<>();
        DecisionLog.tap = s -> System.out.println("      " + s);
        HellionWatcher w = new HellionWatcher(new DecisionLog(), new HellionWatcher.Listener() {
            @Override public void kill() {
                System.out.println("  >> OPEN (pending)");
                opened.add("pending");
            }

            @Override public void resolve(Drop drop, String line) {
                System.out.println("  >> RESOLVE " + drop);
                opened.add("resolve " + drop);
            }

            @Override public void killKnown(Drop drop, String line) {
                System.out.println("  >> OPEN known " + drop);
                opened.add("known " + drop);
            }
        });
        Map<Integer, HellionWatcher.Tag> present = new LinkedHashMap<>();
        long last = -1;
        for (String l : Files.readAllLines(Path.of(a[0]), StandardCharsets.UTF_8)) {
            if (l.isBlank()) continue;
            JsonObject o = JsonParser.parseString(l).getAsJsonObject();
            String kind = o.get("kind").getAsString();
            if (!kind.equals("slayer_line") && !kind.equals("slayer_mob")) continue;
            long t = Instant.parse(o.get("time").getAsString()).toEpochMilli();
            // Ticks between records, so the waits run out as they would in game.
            if (last >= 0) for (long tt = last + 50; tt < t; tt += 50) {
                w.observe(new ArrayList<>(present.values()), false, tt);
                w.afterTick(tt);
            }
            last = t;
            if (kind.equals("slayer_line")) {
                String line = o.get("line").getAsString();
                System.out.println(o.get("time").getAsString().substring(11, 23) + " chat " + line);
                w.onChatLine(line, t);
            } else {
                String event = o.get("event").getAsString(), name = o.get("name").getAsString();
                int id = o.get("entity").getAsInt();
                double d = o.get("distance").getAsDouble();
                if (!name.contains("Inferno Demonlord IV")) continue;
                System.out.println(o.get("time").getAsString().substring(11, 23) + " tag  " + event + " #" + id + " " + name + " @" + d);
                present.put(id, new HellionWatcher.Tag(id, name, d * d));
                w.observe(new ArrayList<>(present.values()), false, t);
                if (event.equals("gone")) {
                    present.remove(id);
                    w.observe(new ArrayList<>(present.values()), false, t + 1);
                }
            }
            w.afterTick(t);
        }
        for (int i = 0; i < 80; i++) w.afterTick(last + 50L * i);
        System.out.println("decisions: " + opened);
    }
}
