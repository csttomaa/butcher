package com.butcher.anim;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The Ender Dragon's flight in the Byzantium Dye's ending - authored in Blender (5.2: a Bezier path and
 * an empty following it, eased), baked a sample a game tick to {@code assets/butcher/seraph_dragon.json}:
 * x, y above the ground, z towards the camera, in blocks, the scene's own axes. It swoops in from the
 * void far behind the pillars, banks once round the beacon passing close in front, and comes down behind
 * it, landing at (0, 0, -6).
 *
 * Only the path is baked. Everything else - the pitch, the bank, the neck and the tail - vanilla's own
 * dragon code works out from the flight history this hands it, as it does for the real dragon. Pure.
 */
public final class SeraphDragon {
    private SeraphDragon() {}

    private static float[][] frames;

    /**
     * Its heading once down: three-quarters to the camera (200 would be square to it - the camera looks from 20 degrees
     * round, SeraphScene.YAW0 - and head-on the dragon reads as a column), the head reaching across to the beacon.
     */
    public static final float LAND_YAW = 225f;
    /** How much of the left loop's reach is kept (see {@link #pos}). */
    static final float WEST_PULL = 0.6f;

    private static synchronized void load() {
        if (frames != null) return;
        try (InputStream in = SeraphDragon.class.getResourceAsStream("/assets/butcher/seraph_dragon.json")) {
            String s = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int i = s.indexOf("\"frames\":") + 9;
            List<float[]> rows = new ArrayList<>();
            String body = s.substring(s.indexOf('[', i) + 1, s.lastIndexOf(']'));
            for (String row : body.split("\\],\\s*\\[")) {
                String[] n = row.replace("[", "").replace("]", "").split(",");
                rows.add(new float[]{Float.parseFloat(n[0].trim()), Float.parseFloat(n[1].trim()), Float.parseFloat(n[2].trim())});
            }
            frames = rows.toArray(new float[0][]);
        } catch (Exception e) {
            // No track: it is simply there, landed.
            frames = new float[][]{{6.09f, 0f, -2.48f}};
        }
    }

    /** How long the flight takes, ms. */
    public static long flightMs() {
        load();
        return (frames.length - 1) * 50L;
    }

    /** Where it is {@code ms} into the flight, blocks; held at the landing afterwards. */
    public static float[] pos(long ms) {
        load();
        float f = Math.max(0f, ms / 50f);
        int i = Math.min(frames.length - 1, (int) f), j = Math.min(frames.length - 1, i + 1);
        float k = Math.min(1f, f - i);
        float[] a = frames[i], b = frames[j];
        float x = a[0] + (b[0] - a[0]) * k;
        // The loop round the left of the beacon reached x -9 and took the dragon off the screen's left edge (the
        // review, S8): pulled in by a quarter there. The start, the landing and everything right of the beacon are
        // untouched, and the heading follows (it is read off these positions).
        if (x < 0f) x *= WEST_PULL;
        return new float[]{x, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
    }

    /**
     * Its heading as vanilla's yaw, degrees: the dragon's head points along (sin yaw, -cos yaw) in x, z -
     * so 180 faces the camera. Taken from the path's direction, turning to face the camera over the last
     * second of the flight as it settles, and LAND_YAW once down.
     */
    public static float yaw(long ms) {
        load();
        long end = flightMs();
        float along = heading(Math.min(ms, end - 100));
        float settle = SeraphTimeline.ease((ms - (end - 1000)) / 1000f);
        return lerpAngle(along, LAND_YAW, settle);
    }

    private static float heading(long ms) {
        float[] a = pos(ms - 60), b = pos(ms + 60);
        float dx = b[0] - a[0], dz = b[2] - a[2];
        if (Math.abs(dx) + Math.abs(dz) < 1e-4f) return LAND_YAW;
        return (float) Math.toDegrees(Math.atan2(dx, -dz));
    }

    static float lerpAngle(float a, float b, float k) {
        float d = ((b - a) % 360f + 540f) % 360f - 180f;
        return a + d * k;
    }

    /** Fills the 64-tick flight history (newest first) vanilla bends the neck and tail from. */
    public static void history(long ms, double[] y, float[] yaw) {
        for (int i = 0; i < y.length; i++) {
            long at = ms - i * 50L;
            y[i] = pos(Math.max(0, at))[1];
            yaw[i] = yaw(Math.max(0, at));
        }
    }
}
