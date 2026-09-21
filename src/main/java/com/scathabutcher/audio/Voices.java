package com.scathabutcher.audio;

/**
 * The sound-making primitives, and the cutscene's cues built out of them.
 *
 * <h2>Two ingredients</h2>
 *
 * Almost every sound worth making here is one of two things, or both at once:
 *
 * <ul>
 *   <li><b>A tone</b> - something with a pitch. Metal ringing, a chime, the hum before a
 *       reveal. Built from a few harmonics of a sine, because a bare sine sounds like a
 *       hearing test and anything richer than a handful of partials is wasted under a
 *       reverb.
 *   <li><b>Noise</b> - something without one. The chop itself, stone, impact. Filtered
 *       white noise: the filter is what turns a hiss into a material. Low and resonant
 *       is a thud; bright and short is a crack.
 * </ul>
 *
 * The envelope matters more than either. A sound's attack is most of what identifies it -
 * a chop is defined by getting loud in two milliseconds, and the same noise with a slow
 * attack is wind.
 *
 * <h2>Everything is deterministic given its seed</h2>
 *
 * Voices take their randomness from a seeded generator rather than {@code Math.random},
 * so a cue can be reproduced exactly. That is what makes the offline checks meaningful:
 * a test that renders a different sound each run cannot assert anything about it.
 */
public final class Voices {

    private Voices() {}

    // =====================================================================
    // primitives
    // =====================================================================

    /**
     * A pitched sound with an attack-decay envelope and an optional pitch slide.
     *
     * Harmonic weights decide the character: {@code {1}} is a pure sine, a series falling
     * off quickly is flute-like, adding odd harmonics gets reedy. They are normalised, so
     * adding one does not make the voice louder.
     */
    public static Voice tone(float hz, float endHz, float seconds, float amplitude,
                             float attack, float[] harmonics, float reverb) {
        return new Tone(hz, endHz, seconds, amplitude, attack, harmonics, reverb);
    }

    /** A filtered noise burst - the material sounds. {@code q} above ~2 starts to ring. */
    public static Voice noise(float centreHz, float q, float seconds, float amplitude,
                              float attack, float reverb, long seed) {
        return new Noise(centreHz, q, seconds, amplitude, attack, reverb, seed);
    }

    /** Plays a voice after a delay, so one cue can be several sounds in sequence. */
    public static Voice after(float seconds, Voice voice) {
        return new Delayed(seconds, voice);
    }

    // =====================================================================
    // the cutscene's cues
    // =====================================================================

    /**
     * The blade coming down: a short rising whoosh.
     *
     * Bandpassed noise sweeping upwards, which is what air moving faster sounds like. The
     * sweep is the whole cue - flat-pitched noise reads as static, not as movement.
     */
    public static Voice swing(float seconds, long seed) {
        return new Swoosh(seconds, seed);
    }

    /**
     * The blade landing.
     *
     * Three things at once, because an impact is never one sound: a low thud for the mass,
     * a mid crack for the block splitting, and a short metallic ring for the blade. The
     * ring's pitch rises as the worm gets shorter, which is the one bit of state the cue
     * carries - a purely cosmetic tell that something is being got through.
     *
     * @param progress 0 on the first chop, 1 on the last
     */
    public static Voice[] impact(float progress, long seed) {
        float ring = ringPitch(progress);
        return new Voice[]{
                // Mass. Low, fast attack, no reverb send - a thud in a cave is still a thud.
                noise(96f, 1.1f, 0.24f, 0.26f, 0.001f, 0.15f, seed),
                // The block. Broad and brief.
                noise(1150f, 0.9f, 0.10f, 0.15f, 0.001f, 0.35f, seed + 1),
                // The blade. Metal, so it rings: a tone, not noise.
                after(0.004f, tone(ring, ring * 0.92f, 0.42f, 0.34f, 0.002f,
                        new float[]{1f, 0.5f, 0.32f, 0.16f}, 0.65f)),
        };
    }

    /**
     * What the blade rings at on the {@code progress}-th chop of a run.
     *
     * <h3>Why a scale and not a slider</h3>
     *
     * This used to be {@code 620 + 340 * progress} - a straight sweep. Any two chops
     * were then some arbitrary interval apart, mostly dissonant ones, and since each
     * ring is a tone with four harmonics the clashes are audible: the run came out
     * sounding out of tune rather than rising.
     *
     * Snapping to a minor pentatonic fixes it for the same reason it fixes the reveal
     * chime. The scale has no semitones and no tritone, so every pair of notes in it is
     * consonant however many chops a run happens to take - and a run is dealt one to
     * five chops at random, so which notes get played is not known in advance.
     *
     * Minor rather than the major used for the reveal: the chops are the work, the
     * chime is the payoff, and they should not sound like the same phrase.
     */
    private static float ringPitch(float progress) {
        // A4-ish root. Minor pentatonic: root, min3, 4th, 5th, min7.
        float root = 440f;
        float[] steps = {1f, 6f / 5f, 4f / 3f, 3f / 2f, 9f / 5f};
        int i = Math.round(progress * (steps.length - 1));
        return root * steps[Math.max(0, Math.min(steps.length - 1, i))];
    }

    /**
     * The reveal chime, coloured by how good the drop was.
     *
     * A rising arpeggio over a held root. The number of notes is the whole message - more
     * notes for a better drop - and it is deliberately the LAST thing in the cutscene, so
     * it cannot give anything away early.
     *
     * @param notes    how many notes in the run, 2..5
     * @param rootHz   the bottom note
     */
    public static Voice[] chime(int notes, float rootHz) {
        notes = Math.max(2, Math.min(5, notes));
        // A major pentatonic, which has no semitones in it and therefore cannot sound sour
        // whichever prefix of it gets played.
        float[] steps = {1f, 9f / 8f, 5f / 4f, 3f / 2f, 5f / 3f};
        Voice[] out = new Voice[notes + 1];
        float[] bell = {1f, 0.42f, 0.28f, 0.14f, 0.08f};
        for (int i = 0; i < notes; i++) {
            float hz = rootHz * steps[i] * 2f;
            out[i] = after(i * 0.085f, tone(hz, hz, 0.85f - i * 0.05f, 0.40f, 0.004f, bell, 0.7f));
        }
        // A held root underneath, so the run lands on something rather than in mid-air.
        out[notes] = tone(rootHz, rootHz, 1.3f, 0.28f, 0.03f, new float[]{1f, 0.3f, 0.1f}, 0.8f);
        return out;
    }

    /**
     * The drone that fills the gap between one chop and the next.
     *
     * Silence between cuts is what made the run feel like separate events rather than a
     * build-up. This holds a low, slightly detuned pair of tones for the whole wait and
     * swells towards the end, so the next chop always lands on something already rising.
     *
     * The two tones are a few cents apart on purpose. An exactly unison pair is just a
     * louder single tone; detuning them makes them beat against each other slowly, which
     * is the cheapest way to make a held note sound uneasy rather than merely present.
     *
     * @param progress 0 on the first gap, 1 on the last - raises the pitch as the run
     *                 tightens, so the tension is audible and not only visual
     * @param seconds  how long until the next blade lands
     */
    public static Voice[] build(float progress, float seconds) {
        // Same minor pentatonic the chops ring on, an octave down: the drone is the
        // harmonic floor those rings sit on, so it has to belong to the same scale.
        float[] steps = {1f, 6f / 5f, 4f / 3f, 3f / 2f, 9f / 5f};
        int i = Math.round(progress * (steps.length - 1));
        float hz = 110f * steps[Math.max(0, Math.min(steps.length - 1, i))];
        float amp = 0.26f + 0.20f * progress;
        float[] shape = {1f, 0.34f, 0.12f, 0.05f};
        return new Voice[]{
                swell(hz, seconds, amp, shape),
                swell(hz * 1.006f, seconds, amp * 0.8f, shape),
        };
    }

    /** A held tone that fades in, sustains and fades out - not the struck shape. */
    private static Voice swell(float hz, float seconds, float amplitude, float[] harmonics) {
        return new Swell(hz, seconds, amplitude, harmonics);
    }

    /**
     * The blade going through empty air, and the animal leaving.
     *
     * A whiff rather than an impact: the swoosh carries on instead of being cut short,
     * and there is no thud at the end of it - the absence of the landing IS the cue.
     * Under it, a short falling pair of notes, which is about as close as two tones can
     * get to sounding disappointed.
     */
    public static Voice[] miss(long seed) {
        return new Voice[]{
                // Longer than a normal swing, because nothing stops it.
                swing(0.42f, seed),
                // A minor third downwards. Falling intervals read as deflation; this is
                // the same scale the chops ring on, so it still belongs to the run.
                after(0.10f, tone(440f, 440f, 0.55f, 0.30f, 0.01f,
                        new float[]{1f, 0.4f, 0.16f}, 0.7f)),
                after(0.30f, tone(440f / 1.2f, 440f / 1.2f, 0.95f, 0.30f, 0.01f,
                        new float[]{1f, 0.4f, 0.16f}, 0.8f)),
        };
    }

    /**
     * The sting for a pet.
     *
     * Vanilla's advancement sound was used for this, and it had to be exempted from our
     * own mute to be heard at all - but it still went through Minecraft's mixer, so its
     * level was the game's to decide and the mod's slider could only ever make it
     * quieter than whatever the master was set to. Making it here puts it on the same
     * footing as every other cue: one volume control, ours.
     *
     * A major triad arpeggiated upward and then struck together, with a bright shimmer
     * over the top. Major and rising, where the chops are minor and the miss falls -
     * this is the one moment in the whole cutscene that is allowed to sound pleased.
     */
    public static Voice[] fanfare() {
        float root = 523.25f;                      // C5
        float[] steps = {1f, 5f / 4f, 3f / 2f, 2f};  // root, major 3rd, 5th, octave
        float[] bell = {1f, 0.34f, 0.22f, 0.10f, 0.05f};
        Voice[] out = new Voice[steps.length + 3];
        for (int i = 0; i < steps.length; i++) {
            float hz = root * steps[i];
            out[i] = after(i * 0.070f, tone(hz, hz, 1.05f - i * 0.04f, 0.34f, 0.003f, bell, 0.75f));
        }
        // The whole chord together on the last note, which is what turns a run of notes
        // into an arrival.
        out[steps.length] = after(steps.length * 0.070f,
                tone(root * 2f, root * 2f, 1.5f, 0.26f, 0.004f, bell, 0.85f));
        out[steps.length + 1] = after(steps.length * 0.070f,
                tone(root * 3f, root * 3f, 1.3f, 0.14f, 0.004f, bell, 0.85f));
        // A high shimmer, quiet and long, so the sting has a tail rather than a stop.
        out[steps.length + 2] = after(steps.length * 0.070f + 0.05f,
                tone(root * 4f, root * 4f, 1.6f, 0.07f, 0.02f, new float[]{1f, 0.2f}, 0.95f));
        return out;
    }

    /** A low swell as the cavern fades up. Sets the space before anything happens in it. */
    public static Voice open(float seconds) {
        return tone(58f, 66f, seconds, 0.62f, seconds * 0.45f,
                new float[]{1f, 0.55f, 0.22f, 0.08f}, 0.9f);
    }

    // =====================================================================
    // implementations
    // =====================================================================

    /**
     * Attack-then-decay shape, shared by every voice here.
     *
     * The decay is exponential rather than linear because that is how things actually
     * stop: a linear fade has an audible corner where it reaches zero, and a struck
     * object does not stop like that.
     */
    private static float envelope(float t, float total, float attack) {
        if (t >= total) return 0f;
        if (t < attack) return attack <= 0f ? 1f : t / attack;
        float after = (t - attack) / Math.max(1e-4f, total - attack);
        // Taper the last tenth to zero, so the voice ends silent rather than on a step.
        float tail = after > 0.9f ? (1f - after) / 0.1f : 1f;
        return (float) Math.exp(-4.2f * after) * tail;
    }

    private static final class Tone implements Voice {
        private final float hz, endHz, total, amplitude, attack, reverb;
        private final float[] harmonics;
        private final float norm;
        private double phase;
        private float t;

        Tone(float hz, float endHz, float total, float amplitude, float attack,
             float[] harmonics, float reverb) {
            this.hz = hz;
            this.endHz = endHz;
            this.total = total;
            this.amplitude = amplitude * HEADROOM;
            this.attack = attack;
            this.harmonics = harmonics == null || harmonics.length == 0 ? new float[]{1f} : harmonics;
            this.reverb = reverb;
            // Normalise by the root-sum-of-squares, not by the plain sum. Harmonics are
            // at different frequencies, so they only all peak together for an instant at
            // the very start of a cycle; dividing by the sum assumes that happens
            // constantly and makes every multi-harmonic voice far quieter than asked for.
            float energy = 0f;
            for (float h : this.harmonics) energy += h * h;
            this.norm = energy <= 0f ? 1f : 1f / (float) Math.sqrt(energy);
        }

        @Override
        public boolean render(float[] left, float[] right, float[] send, int frames, float sampleRate) {
            float step = 1f / sampleRate;
            for (int i = 0; i < frames; i++) {
                if (t >= total) return false;
                float env = envelope(t, total, attack);
                float f = hz + (endHz - hz) * (t / total);

                float v = 0f;
                for (int h = 0; h < harmonics.length; h++) {
                    v += harmonics[h] * (float) Math.sin(phase * (h + 1));
                }
                v *= norm * env * amplitude;

                left[i] += v;
                right[i] += v;
                send[i] += v * reverb;

                phase += Math.PI * 2 * f * step;
                if (phase > Math.PI * 2) phase -= Math.PI * 2;
                t += step;
            }
            return true;
        }
    }

    /**
     * A sustained tone: in, hold, swell, out.
     *
     * {@link Tone} is struck - it attacks and decays exponentially, which is right for a
     * bell and wrong for a drone, where the note would have effectively gone by the time
     * the next chop arrived. This one holds its level and only rises towards the end,
     * which is what turns a held note into anticipation.
     */
    private static final class Swell implements Voice {
        private final float hz, total, amplitude;
        private final float[] harmonics;
        private final float norm;
        private double phase;
        private float t;

        Swell(float hz, float total, float amplitude, float[] harmonics) {
            this.hz = hz;
            this.total = Math.max(0.05f, total);
            this.amplitude = amplitude * HEADROOM;
            this.harmonics = harmonics;
            float energy = 0f;
            for (float h : harmonics) energy += h * h;
            this.norm = energy <= 0f ? 1f : 1f / (float) Math.sqrt(energy);
        }

        @Override
        public boolean render(float[] left, float[] right, float[] send, int frames, float sampleRate) {
            float step = 1f / sampleRate;
            for (int i = 0; i < frames; i++) {
                if (t >= total) return false;
                float u = t / total;
                // Quick fade in, long hold, a rise into the last stretch, then a hard
                // stop - the chop itself is the release, so this must not linger past it.
                float in = Math.min(1f, u / 0.12f);
                float out = u > 0.94f ? (1f - u) / 0.06f : 1f;
                float rise = 1f + 0.55f * Math.max(0f, (u - 0.55f) / 0.45f);
                float env = in * out * rise * amplitude;

                float v = 0f;
                for (int h = 0; h < harmonics.length; h++) {
                    v += harmonics[h] * (float) Math.sin(phase * (h + 1));
                }
                v *= norm * env;
                left[i] += v;
                right[i] += v;
                send[i] += v * 0.55f;

                phase += Math.PI * 2 * hz * step;
                if (phase > Math.PI * 2) phase -= Math.PI * 2;
                t += step;
            }
            return true;
        }
    }

    /**
     * Band-passed white noise.
     *
     * A state-variable filter, which gives a bandpass output directly and stays stable at
     * the high resonance a metallic sound wants. One per channel with slightly different
     * seeds, because identical noise in both ears collapses to a point in the middle of
     * the head; decorrelating them is what makes it sound like it is in a room.
     */
    /** Gain put back after a bandpass, which loses most of white noise's energy. */
    private static final float BANDPASS_MAKEUP = 7.5f;

    private static final class Noise implements Voice {
        private final float centreHz, q, total, amplitude, attack, reverb;
        private long state;
        private float lowL, bandL, lowR, bandR;
        private float t;

        Noise(float centreHz, float q, float total, float amplitude, float attack,
              float reverb, long seed) {
            this.centreHz = centreHz;
            this.q = Math.max(0.3f, q);
            this.total = total;
            // A bandpass throws away everything outside its band, so white noise in comes
            // out a fraction of the amplitude it went in at - and more so at low Q, where
            // the filter is not resonating to make any of it back. Without this the
            // impacts measured about -30dB and would have been inaudible under the game.
            this.amplitude = amplitude * HEADROOM * BANDPASS_MAKEUP * Math.max(1f, 1.6f / this.q);
            this.attack = attack;
            this.reverb = reverb;
            this.state = seed == 0 ? 0x9E3779B97F4A7C15L : seed;
        }

        /** xorshift - fast, deterministic, and plenty random for noise. */
        private float white() {
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            return (state >> 40) / 8388608f;   // -1..1
        }

        @Override
        public boolean render(float[] left, float[] right, float[] send, int frames, float sampleRate) {
            float step = 1f / sampleRate;
            float f = 2f * (float) Math.sin(Math.PI * Math.min(0.45f, centreHz / sampleRate));
            float damp = 1f / q;

            for (int i = 0; i < frames; i++) {
                if (t >= total) return false;
                float env = envelope(t, total, attack) * amplitude;

                lowL += f * bandL;
                float highL = white() - lowL - damp * bandL;
                bandL += f * highL;

                lowR += f * bandR;
                float highR = white() - lowR - damp * bandR;
                bandR += f * highR;

                float l = bandL * env, r = bandR * env;
                left[i] += l;
                right[i] += r;
                send[i] += (l + r) * 0.5f * reverb;
                t += step;
            }
            return true;
        }
    }

    /** Noise sweeping upward in pitch - the blade moving. */
    private static final class Swoosh implements Voice {
        private final float total;
        private long state;
        private float low, band;
        private float t;

        Swoosh(float total, long seed) {
            this.total = total;
            this.state = seed == 0 ? 0x2545F4914F6CDD1DL : seed;
        }

        private float white() {
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            return (state >> 40) / 8388608f;
        }

        @Override
        public boolean render(float[] left, float[] right, float[] send, int frames, float sampleRate) {
            float step = 1f / sampleRate;
            for (int i = 0; i < frames; i++) {
                if (t >= total) return false;
                float u = t / total;
                // Loudest near the end, where the blade is moving fastest.
                float env = u * u * (float) Math.exp(-1.2f * u) * HEADROOM * 2.6f;
                float centre = 380f + 1750f * u * u;

                float f = 2f * (float) Math.sin(Math.PI * Math.min(0.45f, centre / sampleRate));
                low += f * band;
                float high = white() - low - 0.85f * band;
                band += f * high;

                float v = band * env;
                left[i] += v;
                right[i] += v * 0.95f;
                send[i] += v * 0.25f;
                t += step;
            }
            return true;
        }
    }

    /**
     * Holds a voice back, so one cue can be a short sequence.
     *
     * <h3>Sample-accurate, and it has to be</h3>
     *
     * The obvious implementation - count whole blocks and then hand over - rounds every
     * delay up to a multiple of the block length, which is 11.6ms. That is not a rounding
     * error, it is the difference between two sounds being simultaneous and being
     * separate: the impact's metallic ring is meant to start 4ms after the thud, and
     * quantised to a block it arrives three times later and stops reading as one event.
     * The chime's notes drift the same way, and an arpeggio with uneven spacing sounds
     * like a mistake.
     *
     * So the inner voice is rendered into scratch and written at the right sample offset,
     * with the overhang carried into the next block. The scratch is allocated once, here,
     * on whichever thread built the voice - never on the audio thread.
     */
    private static final class Delayed implements Voice {
        private final Voice inner;
        private int remaining;                 // samples still to wait
        private final float[] sl, sr, ss;      // scratch for one block of the inner voice
        private final float[] cl, cr, cs;      // the part that did not fit, for next time
        private int carried;
        private boolean innerAlive = true;

        Delayed(float seconds, Voice inner) {
            this.inner = inner;
            this.remaining = Math.max(0, Math.round(seconds * SynthEngine.SAMPLE_RATE));
            int block = SynthEngine.BLOCK;
            this.sl = new float[block];
            this.sr = new float[block];
            this.ss = new float[block];
            this.cl = new float[block];
            this.cr = new float[block];
            this.cs = new float[block];
        }

        @Override
        public boolean render(float[] left, float[] right, float[] send, int frames, float sampleRate) {
            int at = 0;

            // Whatever overhung the end of the last block goes at the head of this one.
            if (carried > 0) {
                int n = Math.min(carried, frames);
                for (int i = 0; i < n; i++) {
                    left[i] += cl[i];
                    right[i] += cr[i];
                    send[i] += cs[i];
                }
                // Shuffle anything still left over down to the front.
                int rest = carried - n;
                for (int i = 0; i < rest; i++) {
                    cl[i] = cl[i + n];
                    cr[i] = cr[i + n];
                    cs[i] = cs[i + n];
                }
                carried = rest;
                at = n;
            }

            if (remaining >= frames - at) {
                remaining -= frames - at;
                return innerAlive || carried > 0;
            }
            at += remaining;
            remaining = 0;
            if (!innerAlive) return carried > 0;

            java.util.Arrays.fill(sl, 0f);
            java.util.Arrays.fill(sr, 0f);
            java.util.Arrays.fill(ss, 0f);
            innerAlive = inner.render(sl, sr, ss, frames, sampleRate);

            int fits = frames - at;
            for (int i = 0; i < fits; i++) {
                left[at + i] += sl[i];
                right[at + i] += sr[i];
                send[at + i] += ss[i];
            }
            // The rest of the inner block lands at the start of the next one.
            int over = frames - fits;
            for (int i = 0; i < over; i++) {
                cl[carried + i] = sl[fits + i];
                cr[carried + i] = sr[fits + i];
                cs[carried + i] = ss[fits + i];
            }
            carried += over;

            return innerAlive || carried > 0;
        }
    }
}
