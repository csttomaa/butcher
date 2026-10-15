package com.butcher.anim;

/**
 * A cutscene's own clock: milliseconds of the scene since it opened, run at the player's speed setting for that
 * animation (the menu's "Speed", 50-200%).
 *
 * Everything a cutscene times - its picture, its cue list, its skip target - reads this, so the whole animation
 * plays faster or slower together. A skip jumps the scene time; the real time it opened is kept apart for what
 * needs the wall clock (the kill's time, seeds).
 */
public final class SceneClock {
    private final float speed;
    private final long openedAt = System.currentTimeMillis();
    /** The scene time at {@link #origin}, and the wall time it was set. A jump moves both. */
    private long base, origin = openedAt;

    /** @param percent the speed, 100 = as authored */
    public SceneClock(int percent) {
        this.speed = Math.max(0.25f, Math.min(4f, percent / 100f));
    }

    /** Scene milliseconds since the cutscene opened. */
    public long t() {
        return base + (long) ((System.currentTimeMillis() - origin) * speed);
    }

    /** Moves the scene to {@code sceneMs} (a skip). */
    public void jump(long sceneMs) {
        origin = System.currentTimeMillis();
        base = sceneMs;
    }

    /** The wall-clock time the cutscene opened. */
    public long openedAt() {
        return openedAt;
    }

    public float speed() {
        return speed;
    }
}
