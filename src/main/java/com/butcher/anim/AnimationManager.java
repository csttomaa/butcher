package com.butcher.anim;

import com.butcher.ButcherClient;
import com.butcher.detect.DropDetector.DropResult;
import com.butcher.detect.DropDetector.Outcome;

/**
 * Drives the cutscene timeline. Time-based (not tick-based) so the animation
 * plays smoothly regardless of the server's tick rate / lag.
 */
public class AnimationManager {
    public enum Phase { NONE, RAISE, SWING, IMPACT, RESULT, FADE }

    private DropResult current;
    private long startTime;
    private long durationMs = 2600;

    // phase boundaries, as a fraction of the total duration.
    // RAISE got a lot more room than before: it now covers both the
    // guillotine bouncing up into place AND the whole Scatha sliding in
    // from off-screen, and both need enough time on screen to actually
    // read as "decelerating into place" rather than a blink.
    private static final float RAISE_END = 0.32f;
    private static final float SWING_END = 0.48f;
    private static final float IMPACT_END = 0.58f;
    private static final float RESULT_END = 0.90f;
    // FADE runs from RESULT_END to 1.0f

    public void start(DropResult result) {
        this.current = result;
        this.durationMs = Math.max(500, ButcherClient.config.animationDurationMs);
        this.startTime = System.currentTimeMillis();
    }

    public void tick() {
        if (current != null && elapsedFraction() >= 1f) {
            DropResult finished = current;
            current = null;
            // Fired as the cutscene clears rather than when the reveal starts - the
            // cutscene's own backdrop covers the world, so a firework lit mid-animation
            // would never be seen. See PetDropFirework.
            if (finished.outcome == Outcome.PET) {
                PetDropFirework.burst(finished.rarity);
                PetHeadPopup.showIfEnabled();
            }
        }
    }

    /** Ends it at once, unannounced (/butcher stop, or after an error). */
    public void stop() {
        current = null;
    }

    public boolean isPlaying() {
        return current != null;
    }

    public DropResult getCurrent() {
        return current;
    }

    public float elapsedFraction() {
        if (current == null) return 1f;
        long elapsed = System.currentTimeMillis() - startTime;
        return Math.min(1f, elapsed / (float) durationMs);
    }

    /** Milliseconds since this cutscene started (0 when nothing is playing). Used to drive frame-based animations like the head spin. */
    public long elapsedMs() {
        if (current == null) return 0L;
        return System.currentTimeMillis() - startTime;
    }

    public Phase getPhase() {
        if (current == null) return Phase.NONE;
        float f = elapsedFraction();
        if (f < RAISE_END) return Phase.RAISE;
        if (f < SWING_END) return Phase.SWING;
        if (f < IMPACT_END) return Phase.IMPACT;
        if (f < RESULT_END) return Phase.RESULT;
        return Phase.FADE;
    }

    /** 0..1 progress within the current phase. */
    public float phaseProgress() {
        float f = elapsedFraction();
        float start;
        float end;
        switch (getPhase()) {
            case RAISE -> { start = 0f; end = RAISE_END; }
            case SWING -> { start = RAISE_END; end = SWING_END; }
            case IMPACT -> { start = SWING_END; end = IMPACT_END; }
            case RESULT -> { start = IMPACT_END; end = RESULT_END; }
            case FADE -> { start = RESULT_END; end = 1f; }
            default -> { return 0f; }
        }
        return end <= start ? 1f : Math.max(0f, Math.min(1f, (f - start) / (end - start)));
    }
}
