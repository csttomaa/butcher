package com.butcher.audio;

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

    // =====================================================================
    // the Thaw's cues
    // =====================================================================

    /**
     * The block rising into view: a cold, hollow swell with a high breath of air over it.
     *
     * Higher and thinner than the cavern's {@link #open} - that one is a cave, this is ice -
     * with a quiet shimmer on top that sounds like frost settling.
     */
    public static Voice[] thawOpen(float seconds) {
        return new Voice[]{
                tone(98f, 110f, seconds * 1.2f, 0.40f, seconds * 0.5f,
                        new float[]{1f, 0.25f, 0.30f, 0.06f}, 0.9f),
                noise(6200f, 2.2f, seconds * 1.1f, 0.05f, seconds * 0.6f, 0.95f, 4104L),
        };
    }

    /**
     * The root of each flare's swell: G2, C3 and E3.
     *
     * Together a C major triad, and every one of them also in the A minor pentatonic the
     * strikes and the rest of the cues ring on - so a flare can never clash with anything
     * sounding over it.
     *
     * @param colour 0 blue, 1 violet, 2 gold
     */
    private static float flareRoot(int colour) {
        return switch (colour) {
            case 2 -> 164.81f;
            case 1 -> 130.81f;
            default -> 98.0f;
        };
    }

    /**
     * The flare: a swell that rises under the pick as it winds up.
     *
     * <h3>Brighter by its overtones, not its pitch</h3>
     *
     * Gold used to be the highest of the three, up at 330Hz, and it sat badly: a thin high
     * tone swelling for a second and then cut dead by a short, sharp hit reads as the sound
     * being interrupted rather than paying off. Now all three sit low, a detuned pair under
     * each so it has body, and what separates them is how many overtones they carry - blue
     * is nearly a pure tone, gold has a stack of bright partials and a fifth and an octave
     * over it. It sounds richer and more important without sounding shrill.
     *
     * The colour is still audible as well as visible, because a tell the player can only
     * see is a tell they can miss while watching the pick.
     *
     * @param colour 0 blue, 1 violet, 2 gold
     */
    public static Voice[] thawFlare(int colour, float seconds) {
        float hz = flareRoot(colour);
        float[] shape = switch (colour) {
            case 2 -> new float[]{1f, 0.8f, 0.65f, 0.5f, 0.38f, 0.26f, 0.16f};
            case 1 -> new float[]{1f, 0.5f, 0.3f, 0.15f};
            default -> new float[]{1f, 0.25f, 0.06f};
        };
        // Gold a touch lower than the formula would give: it has five voices stacked, and at
        // the full 0.30 it came out 10dB over blue - standing out is the point, jumping is not.
        float amp = colour == 2 ? 0.24f : 0.20f + 0.05f * colour;
        if (colour < 2) {
            return new Voice[]{
                    swell(hz, seconds, amp, shape),
                    swell(hz * 1.004f, seconds, amp * 0.7f, shape),
            };
        }
        return new Voice[]{
                swell(hz, seconds, amp, shape),
                swell(hz * 1.004f, seconds, amp * 0.7f, shape),
                swell(hz * 1.5f, seconds, amp * 0.22f, new float[]{1f, 0.3f}),
                swell(hz * 2f, seconds, amp * 0.15f, new float[]{1f, 0.2f}),
                noise(5200f, 3f, seconds, 0.035f, seconds * 0.7f, 0.9f, 8812L),
        };
    }

    /**
     * The pick going into the ice - a real, heavy strike, not a tap.
     *
     * <h3>What a pick on ice actually sounds like</h3>
     *
     * Four things, and the old cue only had the first two, all of them brief:
     * <ul>
     * <li>the crunch of the ice fracturing, high and broad;
     * <li>the thud of a heavy head arriving, low;
     * <li>the steel ringing - a long tone plus an inharmonic partial at 2.76 times it,
     *     which is what makes struck metal sound like metal and not like a bell;
     * <li>and a tail of loose ice crumbling off afterwards, a scatter of tiny ticks.
     * </ul>
     * The ring and the crumble are what make it last, and what make a strike feel like it
     * has weight.
     *
     * <h3>It lands on the flare's chord</h3>
     *
     * The ring is two octaves above the flare that preceded it, so the swell does not get
     * cut off by an unrelated note - it resolves into the hit. That was the other half of
     * the gold flare sounding wrong.
     *
     * @param flareColour 0 blue, 1 violet, 2 gold - the flare this strike followed
     * @param progress    0 on the first strike, 1 on the last; later strikes crunch harder
     */
    public static Voice[] thawStrike(int flareColour, float progress, long seed) {
        float ring = flareRoot(flareColour) * 4f;
        float crunch = 0.26f + 0.08f * progress;
        Voice[] out = new Voice[5 + 6];
        out[0] = noise(2700f, 1.2f, 0.26f, crunch, 0.001f, 0.45f, seed);
        out[1] = noise(80f, 1.1f, 0.34f, 0.26f, 0.001f, 0.15f, seed + 1);
        out[2] = noise(900f, 0.9f, 0.16f, 0.14f, 0.001f, 0.30f, seed + 2);
        out[3] = after(0.004f, tone(ring, ring * 0.998f, 1.1f, 0.19f, 0.001f,
                new float[]{1f, 0.55f, 0.3f, 0.18f, 0.08f}, 0.75f));
        out[4] = after(0.004f, tone(ring * 2.76f, ring * 2.74f, 0.38f, 0.07f, 0.001f,
                new float[]{1f}, 0.7f));
        // The crumble: loose ice pattering off for a third of a second after the hit.
        for (int k = 0; k < 6; k++) {
            float at = 0.07f + k * 0.055f + ((seed >>> k) & 7) * 0.004f;
            out[5 + k] = after(at, noise(3600f + k * 300f, 2.2f, 0.05f, 0.06f * (1f - k / 7f),
                    0.001f, 0.6f, seed + 10 + k));
        }
        return out;
    }

    /**
     * An item landing in its slot.
     *
     * A single plucked note, pitched up the major pentatonic by the slot it lands in - so a
     * full tray is a rising phrase. Commons are soft and short; an uncommon rings brighter
     * and longer. Legendaries do not use this: they have the whole ending to themselves.
     *
     * @param tier  0 common, 1 uncommon
     * @param index which slot, from 0
     */
    public static Voice thawLand(int tier, int index) {
        float[] steps = {1f, 9f / 8f, 5f / 4f, 3f / 2f, 5f / 3f, 2f, 9f / 4f, 5f / 2f, 3f};
        float hz = 523.25f * steps[Math.max(0, Math.min(steps.length - 1, index))];
        return tier == 0
                ? tone(hz, hz, 0.22f, 0.10f, 0.002f, new float[]{1f, 0.3f, 0.1f}, 0.5f)
                : tone(hz, hz, 0.55f, 0.20f, 0.002f, new float[]{1f, 0.42f, 0.28f, 0.12f}, 0.7f);
    }

    /**
     * An all-commons corpse coming to nothing.
     *
     * A dull knock and a minor third falling - the same deflation as the cleaver's miss,
     * pitched lower and played quieter. It should sound like a shrug, not a punishment.
     */
    public static Voice[] thawCrash(long seed) {
        return new Voice[]{
                noise(140f, 0.9f, 0.30f, 0.20f, 0.002f, 0.2f, seed),
                noise(1600f, 0.8f, 0.18f, 0.08f, 0.002f, 0.3f, seed + 1),
                after(0.08f, tone(294f, 294f, 0.45f, 0.18f, 0.01f, new float[]{1f, 0.3f, 0.1f}, 0.6f)),
                after(0.26f, tone(247f, 247f, 0.80f, 0.16f, 0.01f, new float[]{1f, 0.3f, 0.1f}, 0.7f)),
        };
    }

    /**
     * The block detonating for a legendary.
     *
     * Broad noise high and low - the shatter and the boom - with a long bright hiss of
     * falling fragments, and then {@link #fanfare} on top. The fanfare is what makes it an
     * arrival rather than just an explosion.
     */
    public static Voice[] thawExplode(long seed) {
        Voice[] fan = fanfare();
        Voice[] out = new Voice[fan.length + 3];
        out[0] = noise(3400f, 0.7f, 0.65f, 0.30f, 0.001f, 0.6f, seed);
        out[1] = noise(62f, 1.0f, 0.55f, 0.34f, 0.002f, 0.3f, seed + 1);
        out[2] = noise(8200f, 2.5f, 1.4f, 0.07f, 0.01f, 0.95f, seed + 2);
        for (int i = 0; i < fan.length; i++) out[3 + i] = after(0.05f, fan[i]);
        return out;
    }

    /**
     * The colour draining out of everything, for the Dye.
     *
     * A filtered whoosh falling away and a low tone sinking under it - and then nothing.
     * The silence after this is part of the cue: the concept holds the drain "a beat too
     * long", and it only lands if the sound has genuinely stopped.
     */
    public static Voice[] thawDrain(float seconds, long seed) {
        return new Voice[]{
                noise(1800f, 1.6f, seconds * 0.55f, 0.16f, 0.02f, 0.9f, seed),
                tone(220f, 110f, seconds * 0.6f, 0.26f, 0.02f, new float[]{1f, 0.2f}, 0.85f),
        };
    }

    /**
     * The colour flooding back, pink.
     *
     * The only cue in the mod built on a major seventh: a high arpeggio that climbs past
     * where the fanfare stops, over the fanfare itself. Everything else here is minor or
     * plain major; this is meant to sound like nothing the player has heard from it before.
     */
    public static Voice[] thawFlood(long seed) {
        float root = 659.25f;                                   // E5
        float[] steps = {1f, 5f / 4f, 3f / 2f, 15f / 8f, 2f, 5f / 2f};
        float[] bell = {1f, 0.3f, 0.22f, 0.1f, 0.06f};
        Voice[] fan = fanfare();
        Voice[] out = new Voice[steps.length + fan.length + 1];
        for (int i = 0; i < steps.length; i++) {
            float hz = root * steps[i];
            out[i] = after(i * 0.055f, tone(hz, hz, 1.4f - i * 0.1f, 0.22f, 0.003f, bell, 0.9f));
        }
        for (int i = 0; i < fan.length; i++) out[steps.length + i] = after(0.12f, fan[i]);
        out[out.length - 1] = noise(9000f, 3f, 2.2f, 0.07f, 0.05f, 0.97f, seed);
        return out;
    }

    // =====================================================================
    // the climb, and the two big endings
    // =====================================================================

    /**
     * The last strike's white glow, before the colour starts to climb: a thin, high breath
     * of air with nothing tonal in it. It must not hint at a colour, so it has no pitch.
     */
    public static Voice[] thawClimbWhite(float seconds) {
        return new Voice[]{
                noise(7400f, 2.4f, seconds + 0.25f, 0.025f, seconds * 0.7f, 0.95f, 3301L),
        };
    }

    /**
     * The build-up under the last strike's colour cycle: a low tone and its fifth sliding up
     * an octave, a high shimmer sliding with them, and air rising under it all - each getting
     * louder the longer it runs, and cut dead on the landing so the landing note stands alone.
     * The same whatever the drop, like the cycle it sits under.
     */
    public static Voice[] thawBuildUp(float seconds) {
        float[] body = {1f, 0.45f, 0.25f, 0.12f};
        return new Voice[]{
                new Shaped(98f, 196f, seconds, 0.30f, Shaped.RISE, body, 0.7f, 0f),
                new Shaped(147f, 294f, seconds, 0.20f, Shaped.RISE, body, 0.7f, 0f),
                new Shaped(784f, 1568f, seconds, 0.07f, Shaped.RISE, new float[]{1f, 0.2f}, 0.9f, 6f),
                new Shaped(1175f, 2349f, seconds, 0.04f, Shaped.RISE, new float[]{1f}, 0.9f, 7f),
                noise(2400f, 1.4f, seconds, 0.05f, seconds * 0.95f, 0.9f, 3319L),
        };
    }

    /**
     * A glassy tick for each colour of the cycle, a semitone higher every step, so the cycle
     * is heard winding up as well as seen.
     */
    public static Voice thawCycleTick(int step) {
        float hz = 1046.5f * (float) Math.pow(2, Math.min(step, 12) / 12.0);
        return tone(hz, hz, 0.16f, 0.33f, 0.002f, new float[]{1f, 0.3f, 0.1f}, 0.8f);
    }

    /**
     * The landing: that colour's own flare note, held until the pick comes down, with a
     * glassy ping two octaves over it. It is the chord of the flare it lands on, so a player
     * who has heard the earlier strikes knows by ear which colour it stopped on.
     *
     * @param colour 0 blue, 1 violet, 2 gold
     */
    public static Voice[] thawClimb(int colour, float holdSeconds) {
        float hz = flareRoot(colour);
        float[] shape = switch (colour) {
            case 2 -> new float[]{1f, 0.8f, 0.65f, 0.5f, 0.38f, 0.26f};
            case 1 -> new float[]{1f, 0.5f, 0.3f, 0.15f};
            default -> new float[]{1f, 0.25f, 0.06f};
        };
        float ping = hz * 4f;
        Voice[] out = new Voice[colour == 2 ? 4 : 3];
        out[0] = swell(hz, holdSeconds, 0.42f, shape);
        out[1] = swell(hz * 1.006f, holdSeconds, 0.30f, shape);
        out[2] = tone(ping, ping, 0.9f, 0.30f, 0.003f, new float[]{1f, 0.3f, 0.12f}, 0.85f);
        if (colour == 2) out[3] = noise(9000f, 3f, 0.9f, 0.018f, 0.01f, 0.95f, 3313L);
        return out;
    }

    /**
     * The moment of impact on one of the two big drops, where time slows down: a deep
     * falling boom and a muffled rush, as if the room itself had been pitched down.
     */
    public static Voice[] slowMotion(float seconds, long seed) {
        return new Voice[]{
                tone(92f, 34f, seconds, 0.46f, 0.008f, new float[]{1f, 0.45f, 0.2f}, 0.8f),
                noise(320f, 0.8f, seconds * 0.9f, 0.20f, 0.01f, 0.9f, seed),
                noise(2600f, 1.2f, 0.35f, 0.10f, 0.002f, 0.7f, seed + 1),
        };
    }

    /** A heartbeat: lub-dub. Low enough to be felt more than heard. */
    public static Voice[] heartbeat(long seed) {
        return new Voice[]{
                tone(62f, 44f, 0.20f, 0.72f, 0.004f, new float[]{1f, 0.35f}, 0.3f),
                noise(110f, 1.1f, 0.12f, 0.20f, 0.002f, 0.2f, seed),
                after(0.21f, tone(56f, 40f, 0.18f, 0.52f, 0.004f, new float[]{1f, 0.35f}, 0.3f)),
                after(0.21f, noise(100f, 1.1f, 0.10f, 0.14f, 0.002f, 0.2f, seed + 1)),
        };
    }

    /**
     * A swell played backwards - silent, then rising faster and faster, and cut dead. Placed
     * so the cut lands exactly on the Dye's burst, which makes the burst feel sucked in
     * rather than simply arriving.
     */
    public static Voice[] reverseSwell(float seconds) {
        float root = 329.63f;                                   // E4, under the flood's E5
        float[] soft = {1f, 0.35f, 0.18f, 0.08f};
        return new Voice[]{
                new Shaped(root, root, seconds, 0.40f, Shaped.REVERSE, soft, 0.9f, 0f),
                new Shaped(root * 1.5f, root * 1.5f, seconds, 0.26f, Shaped.REVERSE, soft, 0.9f, 0f),
                new Shaped(root * 2f, root * 2f, seconds, 0.18f, Shaped.REVERSE, soft, 0.9f, 0f),
        };
    }

    /**
     * The Locket's music box: a small tune on a plucked tine, played while the pieces orbit.
     * A tine is a stiff bar, so its overtones are not in the harmonic series - the 2.76x
     * partial is what makes it read as a music box and not a piano. Minor, and rising, so it
     * asks a question the fanfare answers.
     *
     * @param seconds how long it has, from the first note to the snap
     */
    public static Voice[] musicBox(float seconds) {
        float[] tune = {880f, 1046.5f, 1318.5f, 1760f, 1568f, 1318.5f, 1174.7f, 1318.5f,
                1568f, 1760f};
        Voice[] out = new Voice[tune.length * 2];
        // Gaps shrink from 1.25 to 0.75 of the average - it speeds up as the pieces close
        // in, the way a wound spring runs - so the average is what fits the time given.
        float gap = seconds / (tune.length - 0.5f);
        float at = 0f;
        for (int i = 0; i < tune.length; i++) {
            float hz = tune[i];
            out[i * 2] = after(at, tone(hz, hz, 0.9f, 0.42f, 0.002f, new float[]{1f, 0.12f}, 0.8f));
            out[i * 2 + 1] = after(at, tone(hz * 2.76f, hz * 2.76f, 0.25f, 0.13f, 0.001f, null, 0.8f));
            at += gap * (1.25f - 0.5f * i / (float) (tune.length - 1));
        }
        return out;
    }

    /**
     * The Locket snapping whole: a bright click, the fanfare, and a choir - a slow A major
     * pad of breathy voices, a little vibrato in each, holding under everything after.
     */
    public static Voice[] locketSnap(long seed) {
        Voice[] fan = fanfare();
        float[] chord = {220f, 277.18f, 329.63f, 440f, 554.37f};
        float[] vowel = {1f, 0.55f, 0.42f, 0.18f, 0.12f, 0.05f};
        Voice[] out = new Voice[fan.length + chord.length * 2 + 2];
        int k = 0;
        out[k++] = noise(5200f, 3.5f, 0.18f, 0.06f, 0.001f, 0.8f, seed);
        out[k++] = tone(2093f, 2093f, 1.2f, 0.12f, 0.002f, new float[]{1f, 0.3f, 0.1f}, 0.9f);
        for (Voice v : fan) out[k++] = after(0.04f, v);
        for (int i = 0; i < chord.length; i++) {
            // Two voices a few cents apart per note - a choir, not an organ.
            out[k++] = new Shaped(chord[i], chord[i], 4.2f, 0.06f, Shaped.PAD, vowel, 0.95f, 5.2f + i * 0.3f);
            out[k++] = new Shaped(chord[i] * 1.004f, chord[i] * 1.004f, 4.2f, 0.045f, Shaped.PAD, vowel, 0.95f,
                    4.6f + i * 0.4f);
        }
        return out;
    }

    /** One letter of a big drop's name being stamped out. Rises a little each letter. */
    public static Voice letterTick(int index) {
        float hz = 1760f * (float) Math.pow(2, Math.min(index, 20) / 36.0);
        return tone(hz, hz * 0.97f, 0.05f, 0.30f, 0.001f, new float[]{1f, 0.3f}, 0.5f);
    }

    /** A coin on the counter as the price ticks up. */
    public static Voice coinTick(int index) {
        float hz = 2637f * (float) Math.pow(2, (index % 12) / 48.0);
        return tone(hz, hz, 0.11f, 0.26f, 0.001f, new float[]{1f, 0f, 0f, 0f, 0.35f}, 0.6f);
    }

    /** The counter arriving on the price. */
    public static Voice[] coinLand() {
        return chime(4, 659.25f);
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
     * A tone with an envelope other than struck-and-decaying.
     *
     * {@link #PAD} fades in and out slowly and holds between - a choir. {@link #REVERSE} is
     * a struck envelope played backwards: nothing, then an exponential rise, then a hard
     * stop, which is what a reversed cymbal or a "sucked in" swell sounds like.
     */
    private static final class Shaped implements Voice {
        static final int PAD = 0, REVERSE = 1, RISE = 2;
        private final float hz, endHz, total, amplitude, reverb, vibrato;
        private final int shape;
        private final float[] harmonics;
        private final float norm;
        private double phase;
        private float t;

        Shaped(float hz, float endHz, float total, float amplitude, int shape,
               float[] harmonics, float reverb, float vibratoHz) {
            this.hz = hz;
            this.endHz = endHz;
            this.total = Math.max(0.05f, total);
            this.amplitude = amplitude * HEADROOM;
            this.shape = shape;
            this.harmonics = harmonics == null || harmonics.length == 0 ? new float[]{1f} : harmonics;
            this.reverb = reverb;
            this.vibrato = vibratoHz;
            float energy = 0f;
            for (float h : this.harmonics) energy += h * h;
            this.norm = energy <= 0f ? 1f : 1f / (float) Math.sqrt(energy);
        }

        private float env(float u) {
            if (shape == RISE) {
                // Gets louder the whole way, faster towards the end; cut in the last 1.5%.
                float cut = u > 0.985f ? (1f - u) / 0.015f : 1f;
                return (0.12f + 0.88f * u * u) * cut;
            }
            if (shape == REVERSE) {
                // The mirror of envelope(): an exponential rise, and the last 1.5% a ramp to
                // zero so the cut does not click.
                float rise = (float) Math.exp(-4.2f * (1f - u));
                float cut = u > 0.985f ? (1f - u) / 0.015f : 1f;
                return rise * cut;
            }
            float in = Math.min(1f, u / 0.18f);
            float out = u > 0.55f ? (1f - u) / 0.45f : 1f;
            return in * in * out * out;
        }

        @Override
        public boolean render(float[] left, float[] right, float[] send, int frames, float sampleRate) {
            float step = 1f / sampleRate;
            for (int i = 0; i < frames; i++) {
                if (t >= total) return false;
                float u = t / total;
                float f = hz + (endHz - hz) * u;
                if (vibrato > 0f) f *= 1f + 0.004f * (float) Math.sin(t * vibrato * Math.PI * 2);
                float v = 0f;
                for (int h = 0; h < harmonics.length; h++) {
                    v += harmonics[h] * (float) Math.sin(phase * (h + 1));
                }
                v *= norm * env(u) * amplitude;
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
