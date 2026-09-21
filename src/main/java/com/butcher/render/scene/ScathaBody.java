package com.butcher.render.scene;

/**
 * Where each Scatha segment sits, and which way it points, at a given moment.
 *
 * <h2>Taken from the Titanoboa</h2>
 *
 * SkyMaxx places its snake analytically rather than from keyframes: one clock drives a
 * sine running down the body, and every segment reads its position off that curve
 * ({@code BoaScene.segment}). Crucially it then takes each segment's <em>yaw from the
 * derivative of that same curve</em> ({@code BoaScene.yaw}: {@code 90 - atan(slope)}), so
 * every piece turns to face along the path it is sitting on.
 *
 * That second step is the whole difference between a chain of boxes and an animal. Without
 * it the segments stay axis-aligned and the body reads as a train of crates sliding
 * sideways, which is exactly what our first attempt looked like.
 *
 * <h2>Where it deliberately differs</h2>
 *
 * Their snake is swimming, so its head advances along X with the clock and the body
 * follows through a standing wave. Ours is lying on a butcher's block being cut apart: the
 * segments must stay put, or the worm would drift out from under the cleaver.
 *
 * So the body is stationary and the <em>wave</em> travels through it instead -
 * {@code sin(K*x - W*clock)} rather than {@code sin(K*x)} over a moving x. Same motion
 * from the animal's point of view, and the piece the blade is aimed at stays where the
 * blade is aimed.
 *
 * Amplitudes are expressed relative to the segment pitch, so the shape holds if the worm
 * is ever made longer or shorter.
 */
public final class ScathaBody {

    /** Sideways swing of the wave, as a fraction of the distance between segments. */
    private static final float SWAY = 0.34f;
    /** Vertical bob, much smaller - it is resting on a block, not swimming. */
    private static final float BOB = 0.13f;
    /** Radians of wave phase per segment. About a full wave every five segments. */
    private static final float PHASE_PER_SEGMENT = 1.28f;
    /** Phase per segment for the vertical bob - faster, so the two never lock together. */
    private static final float BOB_PHASE_PER_SEGMENT = 1.75f;
    /** How fast the wave travels down the body, in radians per second. */
    private static final float WAVE_SPEED = 2.3f;
    private static final float BOB_SPEED = 3.4f;

    /**
     * How much a spasm scales the wave up.
     *
     * Small on purpose. These were 0.9 and 0.8, which nearly doubled the amplitude the
     * instant the blade landed and then decayed it over about half a second - and since
     * the wave is also travelling, the result on screen was the whole body buzzing
     * rather than flinching. A flinch is a brief change of shape; a buzz is a rendering
     * fault. Keeping the spike small and letting the decay do the work reads as the
     * former.
     */
    private static final float THRASH_SWAY = 0.28f;
    private static final float THRASH_BOB = 0.22f;

    /** One segment's placement. {@code yaw} is degrees about the vertical axis. */
    public record Pose(float x, float y, float z, float yaw) {}

    private ScathaBody() {}

    /**
     * Every segment's placement, tail first.
     *
     * <h3>Spacing runs along the curve, not along X</h3>
     *
     * Stepping X by the segment pitch and reading the wave off it looks right until the
     * body bends: the straight-line distance between two points a pitch apart in X is
     * longer than a pitch once their Z differs, so the segments visibly pull apart at
     * every bend and close up again on the straights.
     *
     * So the curve is walked instead, accumulating arc length, and a segment is dropped
     * every {@code pitch} of it. The body then holds together whatever the wave is doing -
     * which is also why this returns the whole set at once: each position depends on the
     * one before it.
     *
     * @param count   segments, tail at 0
     * @param pitch   distance between segments, in model units
     * @param seconds a continuously rising clock
     * @param thrash  0 at rest, 1 mid-spasm; scales the whole motion up
     */
    public static Pose[] poses(int count, float pitch, float seconds, float thrash) {
        float amp = SWAY * pitch * (1f + THRASH_SWAY * thrash);
        float k = PHASE_PER_SEGMENT / pitch;
        float w = WAVE_SPEED * seconds;

        float bobAmp = BOB * pitch * (1f + THRASH_BOB * thrash);
        float bk = BOB_PHASE_PER_SEGMENT / pitch;
        float bw = BOB_SPEED * seconds;

        float[] xs = new float[count];
        float step = pitch / 32f;
        float x = -(count - 1) * pitch / 2f;
        xs[0] = x;
        for (int i = 1; i < count; i++) {
            float travelled = 0f;
            while (true) {
                float nx = x + step;
                float dz = amp * (float) Math.sin(k * nx - w) - amp * (float) Math.sin(k * x - w);
                float span = (float) Math.sqrt(step * step + dz * dz);
                // Land exactly on a pitch of arc length, partway through this step.
                //
                // This loop used to run until it had gone AT LEAST a pitch and then stop
                // at whatever x that happened to be, which quantised every segment to a
                // grid one step wide. As the wave travelled, each segment snapped between
                // neighbouring grid points instead of sliding: measured, a segment's
                // velocity changed by three times its own per-frame movement. That is
                // what the trembling was - nothing to do with the amplitude, and no
                // amount of calming the impact spasm could have touched it.
                //
                // Interpolating inside the final step makes x a continuous function of
                // the wave's phase, which is the whole requirement for smooth motion. It
                // also stops the overshoot accumulating down the chain, which is why the
                // middle segments shook worst.
                if (travelled + span >= pitch) {
                    x += step * ((pitch - travelled) / Math.max(1e-6f, span));
                    break;
                }
                travelled += span;
                x = nx;
            }
            xs[i] = x;
        }

        // Re-centre, so a bending body does not also drift sideways out of frame.
        float mid = (xs[0] + xs[count - 1]) / 2f;

        Pose[] out = new Pose[count];
        for (int i = 0; i < count; i++) {
            float px = xs[i] - mid;
            float phase = k * xs[i] - w;
            float pz = amp * (float) Math.sin(phase);
            float py = bobAmp * (float) Math.sin(bk * xs[i] - bw);

            // The yaw IS the tangent of the curve above: dz/dx of amp*sin(k*x - wt).
            // Differentiating the same expression the position came from is what keeps
            // the segments glued to the path instead of merely near it.
            float slope = amp * k * (float) Math.cos(phase);
            float yaw = 90f - (float) Math.toDegrees(Math.atan(slope));

            out[i] = new Pose(px, py, pz, yaw);
        }
        return out;
    }

    /** Half the body's length in model units, for framing. */
    public static float halfLength(int count, float pitch) {
        return pitch * (count - 1) / 2f + pitch * 0.5f;
    }
}
