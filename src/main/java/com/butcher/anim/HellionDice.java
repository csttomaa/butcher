package com.butcher.anim;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The die's roll in the Archfiend Dice endings - simulated in Blender (5.2, rigid body: a 2 m cube tossed
 * from the core with a spin, onto a floor, 60 fps, settling after ~2 s), baked frame by frame and played
 * back here. The user asked for Blender only where code is weak; a tumble that reads as real is that.
 *
 * {@code assets/butcher/hellion_dice.json}: per frame x, y, z then the 3x3 rotation (row-major), in mod
 * coordinates (y up, camera at +z, 1 m = 4 units, so the die is the 8-unit head size). Each die has its
 * own track - the same throw, with the texture turned so that its <b>6</b> lands face up: the back face on
 * the Archfiend Dice, the right side on the High Class one (pips counted off the NEU skins).
 *
 * Pure (no Minecraft, no Gson - a few lines of parsing), so the preview and the tests load it too.
 */
public final class HellionDice {
    private HellionDice() {}

    private static float[][] archfiend, highclass;
    private static int fps = 60, settle = 124;

    private static synchronized void load() {
        if (archfiend != null) return;
        try (InputStream in = HellionDice.class.getResourceAsStream("/assets/butcher/hellion_dice.json")) {
            String s = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            fps = intField(s, "\"fps\":");
            settle = intField(s, "\"settle\":");
            archfiend = track(s, "\"archfiend\":");
            highclass = track(s, "\"highclass\":");
        } catch (Exception e) {
            // A missing track is a die that sits still at the core, not a crash.
            archfiend = highclass = new float[][]{{0f, 4f, 0f, 1, 0, 0, 0, 1, 0, 0, 0, 1}};
        }
    }

    /** How long after the throw the die comes to rest, ms. */
    public static long settleMs() {
        load();
        return Math.round(settle * 1000.0 / fps);
    }

    /**
     * The die {@code ms} after the throw: {x, y, z, r00..r22}. Linear between frames (60 fps), held at
     * the last one.
     */
    public static float[] pose(boolean highClass, long ms) {
        load();
        float[][] tr = highClass ? highclass : archfiend;
        float f = Math.max(0f, ms * fps / 1000f);
        int i = Math.min(tr.length - 1, (int) f);
        int j = Math.min(tr.length - 1, i + 1);
        float k = Math.min(1f, f - i);
        float[] out = new float[12];
        for (int c = 0; c < 12; c++) out[c] = tr[i][c] + (tr[j][c] - tr[i][c]) * k;
        return out;
    }

    /** When it hits the floor hard enough to hear, ms after the throw - the low points of its fall. */
    public static List<Long> bounces() {
        load();
        List<Long> out = new ArrayList<>();
        float[][] tr = archfiend;
        for (int i = 2; i < tr.length - 1; i++) {
            float before = tr[i - 2][1] - tr[i][1];
            if (tr[i][1] < tr[i - 1][1] && tr[i][1] <= tr[i + 1][1] && tr[i][1] < -1.6f && before > 0.25f) {
                out.add(Math.round(i * 1000.0 / fps));
            }
        }
        // Then the clack as it tips over flat onto a face (the centre at its half-size above the floor).
        long last = out.isEmpty() ? 0 : out.get(out.size() - 1);
        for (int i = 0; i < tr.length; i++) {
            long ms = Math.round(i * 1000.0 / fps);
            if (ms > last + 150 && tr[i][1] < -3.75f) {
                out.add(ms);
                break;
            }
        }
        return out;
    }

    /**
     * When the die rolls over an edge on the floor, ms after the throw: the moments the face pointing
     * up changes while it is down low (spinning in the air does not count).
     */
    public static List<Long> tumbles(boolean highClass) {
        load();
        float[][] tr = highClass ? highclass : archfiend;
        List<Long> out = new ArrayList<>();
        int last = -1;
        for (int i = 0; i < tr.length; i++) {
            // Row 1 of the rotation: how far up each of the die's own axes points.
            int up = 0;
            float best = 0f;
            for (int a = 0; a < 3; a++) {
                float v = tr[i][6 + a];
                if (Math.abs(v) > Math.abs(best)) {
                    best = v;
                    up = a;
                }
            }
            int face = up * 2 + (best > 0 ? 0 : 1);
            if (tr[i][1] < -2.2f && last >= 0 && face != last) out.add(Math.round(i * 1000.0 / fps));
            last = face;
        }
        return out;
    }

    // ------------------------------------------------------------------ parsing

    private static int intField(String s, String key) {
        int i = s.indexOf(key) + key.length(), j = i;
        while (j < s.length() && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '-')) j++;
        return Integer.parseInt(s.substring(i, j));
    }

    private static float[][] track(String s, String key) {
        int i = s.indexOf(key) + key.length();
        int depth = 0, end = i;
        for (int k = i; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '[') depth++;
            else if (c == ']' && --depth == 0) {
                end = k;
                break;
            }
        }
        List<float[]> rows = new ArrayList<>();
        String body = s.substring(i + 1, end);
        for (String row : body.split("\\],\\s*\\[")) {
            String[] nums = row.replace("[", "").replace("]", "").split(",");
            float[] r = new float[12];
            for (int n = 0; n < 12; n++) r[n] = Float.parseFloat(nums[n].trim());
            rows.add(r);
        }
        return rows.toArray(new float[0][]);
    }
}
