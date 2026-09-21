package com.butcher.audio;

import java.util.Arrays;

/**
 * A small Schroeder reverb: four parallel comb filters into two allpass filters.
 *
 * <h2>Why any reverb at all</h2>
 *
 * The cutscene is set in a cavern. A dry chop sounds like it happened in a box, and no
 * amount of tuning the chop itself fixes that - the space is carried entirely by what
 * happens after the sound, not by the sound. This is the cheapest structure that gives a
 * believable tail: the combs make the echo density, the allpasses smear it so the
 * individual repeats stop being audible as separate taps.
 *
 * The comb lengths are mutually prime on purpose. Lengths sharing a factor line their
 * repeats up and the tail rings on one pitch, which sounds like a pipe rather than a
 * cave.
 *
 * Damping rolls the high end off each repeat, so the tail darkens as it decays the way a
 * real one does - stone absorbs treble faster than bass.
 *
 * Runs on the audio thread, so it allocates once in the constructor and never again.
 */
public final class Reverb {

    private static final int COMBS = 4;
    private static final int ALLPASSES = 2;

    /** Comb delays in samples at 44.1kHz. Mutually prime - see above. */
    private static final int[] COMB_LENGTHS = {1687, 1801, 2053, 2251};
    private static final int[] ALLPASS_LENGTHS = {389, 127};

    /** How much of each repeat's high end is rolled off. */
    private static final float DAMP = 0.28f;
    /** How much of the tail reaches the output. */
    private static final float WET = 0.34f;
    /** Right is slightly quieter than left, which is enough to stop the tail sounding mono. */
    private static final float RIGHT_TRIM = 0.86f;

    private final float[][] combBuffer = new float[COMBS][];
    private final int[] combIndex = new int[COMBS];
    private final float[] combFeedback = new float[COMBS];
    private final float[] combDamp = new float[COMBS];

    private final float[][] allpassBuffer = new float[ALLPASSES][];
    private final int[] allpassIndex = new int[ALLPASSES];

    public Reverb(float sampleRate) {
        for (int i = 0; i < COMBS; i++) {
            int length = Math.max(16, (int) (COMB_LENGTHS[i] * sampleRate / 44100f));
            combBuffer[i] = new float[length];
            // Slightly different feedback per comb, so they do not all decay together.
            combFeedback[i] = 0.80f - i * 0.015f;
        }
        for (int i = 0; i < ALLPASSES; i++) {
            allpassBuffer[i] = new float[Math.max(8, (int) (ALLPASS_LENGTHS[i] * sampleRate / 44100f))];
        }
    }

    /** Clears the tail. Called when the engine is silenced, so a stopped cutscene stops. */
    public void reset() {
        for (float[] b : combBuffer) Arrays.fill(b, 0f);
        for (float[] b : allpassBuffer) Arrays.fill(b, 0f);
        Arrays.fill(combDamp, 0f);
        Arrays.fill(combIndex, 0);
        Arrays.fill(allpassIndex, 0);
    }

    /** Reverberates {@code send} and adds the result onto {@code left} and {@code right}. */
    public void process(float[] send, float[] left, float[] right, int frames) {
        for (int i = 0; i < frames; i++) {
            float in = send[i] * 0.22f;
            float acc = 0f;

            for (int c = 0; c < COMBS; c++) {
                float[] buffer = combBuffer[c];
                int index = combIndex[c];
                float out = buffer[index];
                // One-pole lowpass inside the feedback loop: this is the damping.
                combDamp[c] = out * (1f - DAMP) + combDamp[c] * DAMP;
                buffer[index] = in + combDamp[c] * combFeedback[c];
                combIndex[c] = (index + 1) % buffer.length;
                acc += out;
            }

            for (int a = 0; a < ALLPASSES; a++) {
                float[] buffer = allpassBuffer[a];
                int index = allpassIndex[a];
                float out = buffer[index];
                float stored = acc + out * 0.5f;
                buffer[index] = stored;
                allpassIndex[a] = (index + 1) % buffer.length;
                acc = out - stored * 0.5f;
            }

            left[i] += acc * WET;
            right[i] += acc * WET * RIGHT_TRIM;
        }
    }
}
