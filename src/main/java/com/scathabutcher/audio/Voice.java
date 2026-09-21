package com.scathabutcher.audio;

/**
 * One sound in flight.
 *
 * A voice generates its own samples a block at a time and says when it is finished. It
 * owns all of its own state - phase, envelope position, filter memory - because the
 * engine only ever asks it for the next block and never rewinds it.
 *
 * <h2>Rules a voice must follow</h2>
 *
 * <ul>
 *   <li><b>Add, never assign.</b> The buffers arrive holding other voices' output.
 *       Writing rather than adding silences everything mixed before it.
 *   <li><b>Stay inside the headroom.</b> Peak output of about {@link #HEADROOM}, so a
 *       handful of voices at once still sum to under 1.0. The engine soft-clips as a
 *       safety net, but a voice relying on that will sound crushed.
 *   <li><b>End at zero.</b> Return false only once the output has faded out. Stopping
 *       mid-waveform puts a step in the signal, and a step is a click.
 *   <li><b>Never block.</b> This runs on the audio thread, which has about 11
 *       milliseconds to fill each block. No allocation, no locks, no I/O.
 * </ul>
 */
public interface Voice {

    /** Peak amplitude a single voice should aim for. */
    float HEADROOM = 0.28f;

    /**
     * Renders the next block.
     *
     * @param left  add the left channel here
     * @param right ...and the right here
     * @param send  add anything that should be reverberated; may be ignored for a dry cue
     * @param frames how many samples of each buffer to fill
     * @param sampleRate samples per second
     * @return true to be asked again, false when the sound has finished
     */
    boolean render(float[] left, float[] right, float[] send, int frames, float sampleRate);
}
