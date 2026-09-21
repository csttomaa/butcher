package com.butcher.detect;

import com.butcher.ButcherClient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A short, plain-English record of what the detectors decided and why.
 *
 * Neither half of the detection can be tested on demand: a Scatha pet drops
 * roughly once in two thousand kills, and a kill itself can only be had by
 * going and mining one. When something comes out wrong the only evidence is
 * whatever was written down at the time, so both detectors narrate
 * themselves here - every kill they accept, every kill they *refuse* and the
 * reason, and every item they matched or ignored.
 *
 * Read it back in-game with "/butcher log". Lines carry Minecraft colour
 * codes so the shape of a run is readable at a glance: green for something
 * accepted, red for something deliberately thrown away, grey for context.
 *
 * Borrowed wholesale from SkyMaxx, which keeps the same rolling log for the
 * same reason - its detector spends far more code refusing bad kills than
 * accepting good ones, and without a log a refusal is indistinguishable from
 * a bug.
 */
public final class DecisionLog {
    /** Kept small on purpose: this is the story of the last kill or two, not a history. */
    private static final int KEEP = 40;

    private final Deque<String> lines = new ArrayDeque<>();

    /** Records one decision. {@code text} may contain § colour codes. */
    public void note(String text) {
        synchronized (lines) {
            lines.addLast(text);
            while (lines.size() > KEEP) {
                lines.removeFirst();
            }
        }
        ButcherClient.LOGGER.info("[Butcher] {}", strip(text));
    }

    /** Oldest first. */
    public List<String> recent() {
        synchronized (lines) {
            return new ArrayList<>(lines);
        }
    }

    public void clear() {
        synchronized (lines) {
            lines.clear();
        }
    }

    /** Drops § colour codes, for the copy that goes to the log file. */
    public static String strip(String in) {
        StringBuilder out = new StringBuilder(in.length());
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if (c == '§' && i + 1 < in.length()) {
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
