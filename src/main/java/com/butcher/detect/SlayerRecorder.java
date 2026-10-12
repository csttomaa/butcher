package com.butcher.detect;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Records slayer bosses' nametags as {@code slayer_mob}, for detection to be written against - capture
 * only, like DropRecorder's {@code slayer_line}. The user does not do slayers; a friend will run the mod
 * with records on, and these lines are what is needed: the boss's own nametag (its HP, and how the
 * hitshield's "Hits" shows), the "Spawned by" line under it (whose boss it is), and the moment it goes.
 *
 * Every armour stand within 40 blocks naming a Voidgloom Seraph or an Inferno Demonlord, or carrying
 * "Spawned by", is recorded when first seen, whenever its name changes (at most once a second, so a
 * falling HP bar does not flood the file), and when it disappears, with its last name and distance.
 */
public final class SlayerRecorder {
    private SlayerRecorder() {}

    private static final double RADIUS = 40;
    /** HP changes at most four times a second; any other change to the name (an immune phase) at once. */
    private static final long THROTTLE_MS = 250;
    private static final String[] WATCH = {"Voidgloom Seraph", "Inferno Demonlord", "Spawned by", "Seraph", "Demonlord"};
    /**
     * Lava fishing's rare creatures (2026-10-05, for "Eruption"), recorded as {@code lava_mob}. When one is first
     * seen, everything standing round it is recorded once as {@code lava_model} - entity types, heads and their
     * skins - because Ragnarok's and the Fiery Scuttler's in-game models are not documented anywhere.
     */
    private static final String[] LAVA = {"Lord Jawbus", "Thunder", "Ragnarok", "Fiery Scuttler", "Plhlegblast"};

    private static final class Seen {
        String name = "";
        String kind = "slayer_mob";
        long lastRecorded;
        double dist, dy;
    }

    /** The name without its numbers: two names of the same shape differ only in HP or hits. */
    private static String shape(String name) {
        return name.replaceAll("[0-9.,]+[kKmMbB]?", "#");
    }

    private static final Map<Integer, Seen> SEEN = new HashMap<>();

    public static void tick(LocalPlayer player) {
        ClientLevel level = Minecraft.getInstance().level;
        if (player == null || level == null) {
            SEEN.clear();
            return;
        }
        long now = System.currentTimeMillis();
        Set<Integer> here = new HashSet<>();
        AABB box = AABB.ofSize(player.position(), RADIUS * 2, RADIUS * 2, RADIUS * 2);
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, box)) {
            Component custom = stand.getCustomName();
            if (custom == null) continue;
            String name = custom.getString();
            String kind = kindOf(name);
            // Once seen, an entity is followed whatever its name becomes: Jawbus's nametag drops its health while it
            // floats up immune (the user, 2026-10-11), and that is exactly what has to be on record.
            if (kind == null && SEEN.containsKey(stand.getId())) kind = SEEN.get(stand.getId()).kind;
            if (kind == null) continue;
            here.add(stand.getId());
            double dist = Math.sqrt(stand.distanceToSqr(player));
            Seen s = SEEN.get(stand.getId());
            if (s == null) {
                s = new Seen();
                SEEN.put(stand.getId(), s);
                s.name = name;
                s.kind = kind;
                s.dist = dist;
                s.dy = stand.getY() - player.getY();
                s.lastRecorded = now;
                record(kind, "seen", stand.getId(), name, dist, stand.getY() - player.getY());
                if (kind.equals("lava_mob")) recordModel(level, stand, name);
                continue;
            }
            s.dist = dist;
            s.dy = stand.getY() - player.getY();
            if (!name.equals(s.name) && (now - s.lastRecorded >= THROTTLE_MS || !shape(name).equals(shape(s.name)))) {
                s.name = name;
                s.lastRecorded = now;
                record(s.kind, "changed", stand.getId(), name, dist, s.dy);
            } else if (!name.equals(s.name)) {
                s.name = name;
            }
        }
        for (Iterator<Map.Entry<Integer, Seen>> it = SEEN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Seen> e = it.next();
            if (here.contains(e.getKey())) continue;
            record(e.getValue().kind, "gone", e.getKey(), e.getValue().name, e.getValue().dist, e.getValue().dy);
            it.remove();
        }
    }

    /** "lava_mob" for a lava creature's nametag (one with a health bar), "slayer_mob" for a slayer's, else null. */
    private static String kindOf(String name) {
        if (name.contains("\u2764")) for (String w : LAVA) if (name.contains(w)) return "lava_mob";
        for (String w : WATCH) if (name.contains(w)) return "slayer_mob";
        return null;
    }

    private static void record(String kind, String event, int id, String name, double dist, double dy) {
        JsonObject o = new JsonObject();
        o.addProperty("event", event);
        o.addProperty("entity", id);
        o.addProperty("name", name);
        o.addProperty("distance", Math.round(dist * 10) / 10.0);
        // Height above the player: the floating phases go up.
        o.addProperty("dy", Math.round(dy * 10) / 10.0);
        DropRecorder.record(kind, o);
    }

    /** Every entity within a few blocks of a lava creature's nametag: its type, size, and any head it wears. */
    private static void recordModel(ClientLevel level, ArmorStand tag, String name) {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        com.google.gson.JsonArray parts = new com.google.gson.JsonArray();
        AABB near = tag.getBoundingBox().inflate(6, 10, 6);
        for (net.minecraft.world.entity.Entity e : level.getEntities(tag, near)) {
            JsonObject p = new JsonObject();
            p.addProperty("type", String.valueOf(net.minecraft.world.entity.EntityType.getKey(e.getType())));
            p.addProperty("dx", Math.round((e.getX() - tag.getX()) * 10) / 10.0);
            p.addProperty("dy", Math.round((e.getY() - tag.getY()) * 10) / 10.0);
            p.addProperty("dz", Math.round((e.getZ() - tag.getZ()) * 10) / 10.0);
            p.addProperty("width", Math.round(e.getBbWidth() * 100) / 100.0);
            p.addProperty("height", Math.round(e.getBbHeight() * 100) / 100.0);
            p.addProperty("invisible", e.isInvisible());
            if (e.getCustomName() != null) p.addProperty("custom", e.getCustomName().getString());
            if (e instanceof net.minecraft.world.entity.LivingEntity le) {
                for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                    net.minecraft.world.item.ItemStack it = le.getItemBySlot(slot);
                    if (it == null || it.isEmpty()) continue;
                    String desc = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it.getItem()));
                    String skin = com.butcher.render.ScathaSkins.texturesProperty(it);
                    p.addProperty(slot.getName(), skin == null ? desc : desc + " skin=" + skin);
                }
            }
            parts.add(p);
        }
        o.add("parts", parts);
        DropRecorder.record("lava_model", o);
    }
}
