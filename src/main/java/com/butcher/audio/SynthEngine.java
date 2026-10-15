package com.butcher.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates the cutscene's sound rather than shipping audio files.
 *
 * <h2>Why synthesise</h2>
 *
 * Every cue here is a function of the moment it fires - the chop's pitch drops as the
 * body gets shorter, the reveal's chime is built from the rarity. Baked files cannot do
 * that without shipping one file per variation, and the ones that would have to be
 * shipped are exactly the ones this project has no way of producing: there is no
 * recording booth, and stock effects would need licensing. A few hundred lines of
 * arithmetic covers it and adds nothing to the jar.
 *
 * Taken from SkyMaxx, which drives its whole mod this way. This is the small version -
 * the engine, a reverb and a handful of voices, without their cue-scheduling director.
 *
 * <h2>How it runs</h2>
 *
 * A daemon thread owns a {@link SourceDataLine} and fills it {@link #BLOCK} frames at a
 * time. Everything the game calls - {@link #play}, {@link #setMaster}, {@link #silence} -
 * only leaves a message; the audio thread picks it up at the top of the next block. That
 * is the whole concurrency design, and it is deliberately that blunt: the audio thread
 * has about eleven milliseconds per block, and anything that could make it wait on the
 * game thread turns a slow tick into an audible dropout.
 *
 * <h2>Failing quietly is a feature</h2>
 *
 * Audio output is the one thing here that can be absent or broken on a player's machine
 * for reasons nothing in this mod controls - no device, an exclusive-mode driver, a
 * headset unplugged mid-game. Every failure path sets {@link #broken()} and stops, and a
 * broken engine is simply silent. A cutscene must never fail to play because its sound
 * could not start.
 */
public final class SynthEngine {

    private static final Logger LOG = LoggerFactory.getLogger("butcher");

    public static final float SAMPLE_RATE = 44100f;
    /** Frames per block. About 11.6ms - short enough to stay responsive, long enough to be cheap. */
    public static final int BLOCK = 512;
    /** Line buffer, in bytes. Four blocks' worth of slack against a scheduling hiccup. */
    private static final int LINE_BUFFER = 32768;
    /** Past this many voices the oldest is dropped, so a runaway cue cannot starve the thread. */
    private static final int MAX_VOICES = 96;
    /** Queued-but-not-yet-started voices. A cap, so a stuck audio thread cannot eat memory. */
    private static final int MAX_PENDING = 256;
    /** Give up on the line after this many consecutive write failures. */
    private static final int FAILURES_BEFORE_GIVING_UP = 8;

    private final ConcurrentLinkedQueue<Voice> pending = new ConcurrentLinkedQueue<>();
    private final List<Voice> active = new ArrayList<>();
    private final Reverb reverb = new Reverb(SAMPLE_RATE);

    private final float[] left = new float[BLOCK];
    private final float[] right = new float[BLOCK];
    private final float[] send = new float[BLOCK];
    private final byte[] out = new byte[BLOCK * 4];   // 16-bit stereo

    /**
     * Bumped by the game thread to ask for silence, read by the audio thread.
     *
     * A counter rather than a boolean: a flag the audio thread has to clear can be set
     * and cleared again between two blocks and simply never be seen. Comparing a count
     * against the last one handled cannot miss a request.
     */
    private final AtomicInteger silenceRequests = new AtomicInteger();
    private int silenceHandled;

    private volatile float master = 0.85f;
    private volatile boolean running;
    private volatile boolean broken;
    private Thread thread;
    private SourceDataLine line;

    /** Master gain, 0..1. Set from Minecraft's own volume slider - see {@link ButcherAudio}. */
    public void setMaster(float value) {
        this.master = value < 0f ? 0f : (value > 1f ? 1f : value);
    }

    /** True once audio has failed and been given up on. A broken engine is silent, not fatal. */
    public boolean broken() {
        return broken;
    }

    public boolean running() {
        return running;
    }

    /** Opens the output line and starts the audio thread. Safe to call when already running. */
    public synchronized void start() {
        if (running || broken) return;
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                broken = true;
                LOG.info("[Butcher] no audio output available; cutscene sound is off");
                return;
            }
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, LINE_BUFFER);
            line.start();
        } catch (Throwable t) {
            broken = true;
            LOG.info("[Butcher] could not open audio output; cutscene sound is off ({})", t.toString());
            return;
        }

        running = true;
        thread = new Thread(this::run, "Butcher-Synth");
        thread.setDaemon(true);   // never keeps the game alive at shutdown
        try {
            thread.setPriority(Thread.MAX_PRIORITY);
        } catch (Throwable ignored) {
            // A security manager or a restricted host can refuse this. Not worth failing over:
            // at normal priority the only cost is a slightly higher chance of a dropout.
        }
        thread.start();
    }

    /** Stops the thread and closes the line. Safe to call more than once. */
    public synchronized void shutdown() {
        running = false;
        if (thread != null) {
            try {
                thread.join(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
        if (line != null) {
            try {
                line.stop();
                line.close();
            } catch (Throwable ignored) {
                // Closing a line that the driver has already taken away throws. Nothing
                // useful to do about it, and we are on the way out regardless.
            }
            line = null;
        }
        pending.clear();
    }

    /** Starts a voice. Called from the game thread; it begins at the next block. */
    public void play(Voice voice) {
        if (broken || !running || voice == null) return;
        if (pending.size() < MAX_PENDING) pending.add(voice);
    }

    /** Stops everything at once and clears the reverb tail. */
    public void silence() {
        silenceRequests.incrementAndGet();
        pending.clear();
    }

    // ------------------------------------------------------------------ the audio thread

    private void run() {
        int failures = 0;
        while (running) {
            try {
                renderBlock();
                line.write(out, 0, out.length);
                failures = 0;
            } catch (Throwable t) {
                if (++failures >= FAILURES_BEFORE_GIVING_UP) {
                    LOG.warn("[Butcher] audio kept failing; turning cutscene sound off", t);
                    broken = true;
                    running = false;
                } else {
                    // One bad block is not worth losing audio over, but whatever caused it
                    // may be a voice in a bad state - so drop them all and carry on clean.
                    active.clear();
                }
            }
        }
    }

    /** Mixes one block into {@link #out} as little-endian 16-bit stereo. */
    private void renderBlock() {
        int requested = silenceRequests.get();
        if (requested != silenceHandled) {
            silenceHandled = requested;
            active.clear();
            reverb.reset();
        }

        Voice queued;
        while ((queued = pending.poll()) != null) {
            if (active.size() >= MAX_VOICES) active.remove(0);
            active.add(queued);
        }

        Arrays.fill(left, 0f);
        Arrays.fill(right, 0f);
        Arrays.fill(send, 0f);

        for (int i = active.size() - 1; i >= 0; i--) {
            Voice voice = active.get(i);
            boolean alive;
            try {
                alive = voice.render(left, right, send, BLOCK, SAMPLE_RATE);
            } catch (Throwable t) {
                // A misbehaving voice takes itself out rather than the whole engine.
                alive = false;
            }
            if (!alive) active.remove(i);
        }

        reverb.process(send, left, right, BLOCK);

        float gain = master;
        int at = 0;
        for (int i = 0; i < BLOCK; i++) {
            int l = pcm(softClip(left[i] * gain));
            int r = pcm(softClip(right[i] * gain));
            out[at++] = (byte) (l & 0xFF);
            out[at++] = (byte) (l >> 8 & 0xFF);
            out[at++] = (byte) (r & 0xFF);
            out[at++] = (byte) (r >> 8 & 0xFF);
        }
    }

    /**
     * Rounds off anything approaching full scale instead of letting it wrap.
     *
     * Hard clipping a sum that goes over 1.0 produces a flat top, which is heard as a
     * harsh buzz; this bends the last of the range smoothly so an overloaded moment
     * merely sounds loud. It is a safety net, not a mixing stage - voices are expected to
     * stay within {@link Voice#HEADROOM}.
     */
    public static float softClip(float v) {
        // Untouched up to half scale, then bent smoothly towards 1 - continuous and with no corner anywhere. The old
        // cubic gave 0.83 just under 1.0 and jumped to 1.0 just over it: every loud moment with the volume sliders
        // turned up crossed that step and clicked.
        float a = Math.abs(v);
        if (a <= KNEE) return v;
        float y = KNEE + (1f - KNEE) * (float) Math.tanh((a - KNEE) / (1f - KNEE));
        return v < 0f ? -y : y;
    }

    private static final float KNEE = 0.5f;

    private static int pcm(float v) {
        int s = Math.round(v * 32767f);
        return s > 32767 ? 32767 : (s < -32768 ? -32768 : s);
    }

    /**
     * Renders {@code seconds} of a voice into an interleaved stereo float array, without
     * any audio device.
     *
     * This exists so the cues can be checked offline - amplitude envelope, peak level,
     * whether anything clips, whether the sound actually ends - which is the only way to
     * verify a synthesiser in a project that cannot listen to it. Not used at runtime.
     */
    public static float[] renderOffline(Voice voice, float seconds) {
        int frames = (int) (seconds * SAMPLE_RATE);
        float[] result = new float[frames * 2];
        float[] l = new float[BLOCK], r = new float[BLOCK], s = new float[BLOCK];
        Reverb verb = new Reverb(SAMPLE_RATE);

        boolean alive = true;
        for (int at = 0; at < frames; at += BLOCK) {
            Arrays.fill(l, 0f);
            Arrays.fill(r, 0f);
            Arrays.fill(s, 0f);
            if (alive) alive = voice.render(l, r, s, BLOCK, SAMPLE_RATE);
            verb.process(s, l, r, BLOCK);
            for (int i = 0; i < BLOCK && at + i < frames; i++) {
                result[(at + i) * 2] = softClip(l[i]);
                result[(at + i) * 2 + 1] = softClip(r[i]);
            }
        }
        return result;
    }
}
