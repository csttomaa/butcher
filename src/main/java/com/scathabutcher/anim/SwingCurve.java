package com.scathabutcher.anim;

import java.util.ArrayList;
import java.util.List;

/**
 * A value moving through time along a splined path, with vibration and a landing recoil.
 *
 * <h2>Why not just an ease</h2>
 *
 * The cleaver's swing used to be one cubic ease per phase - {@code easeOutCubic} on the
 * wind-up, {@code easeIn} on the fall, a sine wobble while buried. Each is fine on its
 * own and together they read as three separate moves, because an ease can only say "get
 * from here to there smoothly". It cannot say <em>where the blade is a third of the way
 * down</em>, which is exactly the information that makes a chop look like a chop: the
 * blade hangs at the top, barely moves for the first third of the fall, then whips.
 *
 * A spline can say that, because the shape is keyframes rather than a formula. This is
 * SkyMaxx's {@code SpinCurve} idea - Hermite segments, tangents computed from the
 * neighbouring keys, plus a decaying judder - rearranged for a blade instead of a slot
 * reel. Theirs is built around a long spin coming to rest and rocking at its turning
 * points; a cleaver has one violent arrival and a recoil, so the rocking is gone and a
 * spring-damper takes its place.
 *
 * <h2>The three layers</h2>
 *
 * <ol>
 *   <li><b>The spline.</b> Cubic Hermite between keys. Tangents are the slope through
 *       each key's neighbours (Catmull-Rom), so the path is smooth without anyone working
 *       out derivatives by hand. A key marked {@linkplain Builder#flat flat} gets a zero
 *       tangent - that is what stops the curve sailing past a hold.
 *   <li><b>Judder.</b> A sinusoid under a decaying envelope, for a blade that is
 *       vibrating rather than gliding.
 *   <li><b>Spring.</b> A damped oscillation that starts at one instant - the landing -
 *       and rings down. This is the recoil, and it is deliberately separate from the
 *       spline: the spline says where the blade is <em>meant</em> to be, the spring says
 *       how much it overshot getting there.
 * </ol>
 *
 * Times are in whatever unit the caller uses - the cutscene passes milliseconds, so key
 * times are simply the phase lengths and nothing has to be normalised.
 *
 * Immutable once built, and cheap to evaluate: a binary search and some arithmetic, no
 * allocation. It is sampled every frame.
 */
public final class SwingCurve {

    private final float[] times;
    private final float[] values;
    private final float[] tangents;

    private final float judderAmplitude;
    private final float judderFrequency;   // cycles across the whole curve
    private final float judderDecay;

    private final float springAt;
    private final float springAmplitude;
    private final float springFrequency;   // cycles per time unit
    private final float springDecay;       // e-folds per time unit

    private SwingCurve(float[] times, float[] values, float[] tangents,
                       float judderAmplitude, float judderFrequency, float judderDecay,
                       float springAt, float springAmplitude, float springFrequency, float springDecay) {
        this.times = times;
        this.values = values;
        this.tangents = tangents;
        this.judderAmplitude = judderAmplitude;
        this.judderFrequency = judderFrequency;
        this.judderDecay = judderDecay;
        this.springAt = springAt;
        this.springAmplitude = springAmplitude;
        this.springFrequency = springFrequency;
        this.springDecay = springDecay;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** When the curve ends. */
    public float duration() {
        return times[times.length - 1];
    }

    /** The value at {@code t}, clamped at both ends. Spline plus judder plus spring. */
    public float at(float t) {
        float base = spline(t);
        return base + judder(t) + spring(t);
    }

    /**
     * How fast the value is changing at {@code t}, per time unit.
     *
     * A central difference rather than the analytic derivative, because the judder and
     * the spring are added on top of the spline and differentiating all three separately
     * would be three more things to keep in step for no benefit.
     */
    public float velocity(float t) {
        float h = Math.max(1e-4f, duration() * 0.004f);
        float lo = Math.max(0f, t - h);
        float hi = Math.min(duration(), t + h);
        float dt = hi - lo;
        return dt <= 0f ? 0f : (at(hi) - at(lo)) / dt;
    }

    // ------------------------------------------------------------------ the layers

    private float spline(float t) {
        if (t <= times[0]) return values[0];
        int last = times.length - 1;
        if (t >= times[last]) return values[last];

        int seg = segmentFor(t);
        float t0 = times[seg], t1 = times[seg + 1];
        float h = t1 - t0;
        if (h <= 0f) return values[seg];

        float s = (t - t0) / h;
        float s2 = s * s, s3 = s2 * s;
        // The four Hermite basis functions. h10 and h11 are scaled by the segment length
        // because the tangents are in value-per-time, not value-per-segment.
        float h00 = 2f * s3 - 3f * s2 + 1f;
        float h10 = s3 - 2f * s2 + s;
        float h01 = -2f * s3 + 3f * s2;
        float h11 = s3 - s2;
        return h00 * values[seg] + h10 * h * tangents[seg]
                + h01 * values[seg + 1] + h11 * h * tangents[seg + 1];
    }

    /**
     * Vibration that fades out across the curve.
     *
     * The envelope is normalised by its own peak so {@code judderAmplitude} is the actual
     * maximum deflection, rather than a number that has to be re-tuned every time the
     * decay changes.
     */
    private float judder(float t) {
        if (judderAmplitude <= 0f) return 0f;
        float total = duration();
        if (total <= 0f) return 0f;
        float u = Math.max(0f, Math.min(1f, t / total));
        float remaining = 1f - u;
        float envelope = (float) Math.exp(-judderDecay * remaining) * remaining;
        float peak = (float) (Math.exp(-1.0) / judderDecay);
        return judderAmplitude * (envelope / peak)
                * (float) Math.sin(Math.PI * 2 * judderFrequency * u * u);
    }

    /**
     * The recoil: a damped oscillation that starts the moment the blade lands.
     *
     * Starts at zero and returns to zero, so it only ever adds overshoot - it can never
     * move where the blade nominally is. Before {@code springAt} it contributes nothing,
     * which is what makes it safe to add over the whole curve.
     */
    private float spring(float t) {
        if (springAmplitude == 0f || t < springAt) return 0f;
        float since = t - springAt;
        return springAmplitude
                * (float) Math.exp(-springDecay * since)
                * (float) Math.sin(Math.PI * 2 * springFrequency * since);
    }

    private int segmentFor(float t) {
        int lo = 0, hi = times.length - 1;
        while (lo < hi - 1) {
            int mid = (lo + hi) >>> 1;
            if (times[mid] <= t) lo = mid; else hi = mid;
        }
        return lo;
    }

    // ------------------------------------------------------------------ building

    public static final class Builder {
        private record Key(float time, float value, boolean flat) {}

        private final List<Key> keys = new ArrayList<>();
        private float judderAmplitude, judderFrequency = 3f, judderDecay = 7.5f;
        private float springAt, springAmplitude, springFrequency, springDecay;

        private Builder() {}

        /** A key with a tangent worked out from its neighbours. */
        public Builder key(float time, float value) {
            keys.add(new Key(time, value, false));
            return this;
        }

        /**
         * A key the curve arrives at and leaves with zero slope.
         *
         * Use it at a turning point - the top of the wind-up, the bottom of the cut.
         * Without it the computed tangent carries the curve straight through and the
         * blade visibly overshoots, which at the bottom means going through the block.
         */
        public Builder flat(float time, float value) {
            keys.add(new Key(time, value, true));
            return this;
        }

        /**
         * Vibration across the whole curve.
         *
         * @param amplitude peak deflection, in the curve's own units
         * @param cycles    how many oscillations across the curve
         * @param decay     how sharply it fades; larger dies sooner
         */
        public Builder judder(float amplitude, float cycles, float decay) {
            this.judderAmplitude = amplitude;
            this.judderFrequency = cycles;
            this.judderDecay = decay;
            return this;
        }

        /**
         * A damped oscillation starting at {@code time} - the landing recoil.
         *
         * @param amplitude how far it kicks on the first swing
         * @param frequency oscillations per time unit
         * @param decay     e-folds per time unit; larger settles sooner
         */
        public Builder spring(float time, float amplitude, float frequency, float decay) {
            this.springAt = time;
            this.springAmplitude = amplitude;
            this.springFrequency = frequency;
            this.springDecay = decay;
            return this;
        }

        public SwingCurve build() {
            if (keys.size() < 2) throw new IllegalStateException("a curve needs at least two keys");
            keys.sort((a, b) -> Float.compare(a.time(), b.time()));

            int n = keys.size();
            float[] times = new float[n];
            float[] values = new float[n];
            for (int i = 0; i < n; i++) {
                times[i] = keys.get(i).time();
                values[i] = keys.get(i).value();
            }
            // Two keys at the same instant would divide by zero in the segment below, so
            // nudge them apart rather than rejecting a caller's rounded-off timings.
            for (int i = 1; i < n; i++) {
                if (times[i] <= times[i - 1]) times[i] = times[i - 1] + 1e-4f;
            }

            float[] tangents = new float[n];
            for (int i = 0; i < n; i++) {
                if (keys.get(i).flat()) { tangents[i] = 0f; continue; }
                if (i == 0) {
                    tangents[i] = (values[1] - values[0]) / Math.max(1e-5f, times[1] - times[0]);
                } else if (i == n - 1) {
                    tangents[i] = (values[n - 1] - values[n - 2]) / Math.max(1e-5f, times[n - 1] - times[n - 2]);
                } else {
                    tangents[i] = (values[i + 1] - values[i - 1]) / Math.max(1e-5f, times[i + 1] - times[i - 1]);
                }
            }

            return new SwingCurve(times, values, tangents,
                    judderAmplitude, judderFrequency, judderDecay,
                    springAt, springAmplitude, springFrequency, springDecay);
        }
    }
}
