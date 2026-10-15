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

    // =====================================================================
    // The Labyrinth - Diana's cutscene
    // =====================================================================

    /** The walls coming up out of the ground: a low grinding rumble, and blocks settling. */
    public static Voice[] labRise(long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(tone(55f, 46f, 0.85f, 0.26f, 0.06f, new float[]{1f, 0.5f, 0.3f}, 0.6f));
        out.add(noise(180f, 0.8f, 0.8f, 0.20f, 0.10f, 0.5f, seed));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 7; i++) {
            float at = 0.12f + i * 0.075f + rng.nextFloat() * 0.03f;
            out.add(after(at, noise(700f + rng.nextFloat() * 500f, 2f, 0.08f, 0.10f + i * 0.012f, 0.001f, 0.5f, seed + 10 + i)));
        }
        out.add(after(0.62f, tone(80f, 52f, 0.3f, 0.35f, 0.003f, new float[]{1f, 0.4f}, 0.5f)));
        return out.toArray(new Voice[0]);
    }

    /** The spark catching at the entrance, and the rush of air under the whole run. */
    public static Voice[] labIgnite(float runSeconds) {
        return new Voice[]{
                noise(420f, 1f, 0.25f, 0.16f, 0.015f, 0.6f, 4301L),
                tone(1318.5f, 1318.5f, 0.7f, 0.08f, 0.01f, new float[]{1f, 0.3f}, 0.9f),
                noise(1600f, 1.1f, runSeconds, 0.045f, runSeconds * 0.85f, 0.8f, 4302L),
        };
    }

    /**
     * A corner: a bright pluck, climbing the A minor pentatonic as the run goes on - the
     * further through, the higher, so the run is heard winding up.
     */
    public static Voice[] labCorner(float progress) {
        int[] steps = {0, 3, 5, 7, 10};
        int n = Math.min(14, Math.round(progress * 14));
        float hz = 440f * (float) Math.pow(2, (steps[n % 5] + 12 * (n / 5)) / 12.0);
        return new Voice[]{
                tone(hz, hz, 0.2f, 0.22f, 0.002f, new float[]{1f, 0.4f, 0.15f, 0.05f}, 0.7f),
                noise(5200f, 2f, 0.03f, 0.06f, 0.001f, 0.3f, 4310L + n),
        };
    }

    /** A dead end: the spark knocking into stone, and a little falling note - wrong way. */
    public static Voice[] labKnock(long seed) {
        return new Voice[]{
                tone(150f, 90f, 0.18f, 0.40f, 0.002f, new float[]{1f, 0.3f}, 0.4f),
                noise(620f, 1.5f, 0.12f, 0.22f, 0.001f, 0.4f, seed),
                after(0.05f, tone(392f, 262f, 0.22f, 0.10f, 0.005f, new float[]{1f, 0.35f}, 0.6f)),
        };
    }

    /** A brazier catching as the spark passes: a small soft whoosh. */
    public static Voice[] labTorch(long seed) {
        return new Voice[]{noise(950f, 1.1f, 0.3f, 0.05f, 0.03f, 0.7f, seed)};
    }

    /**
     * The gate slamming down: a heavy stone thud, a crack, and the bronze ringing on with an
     * inharmonic partial, as the Thaw's steel does. The same on every outcome.
     */
    public static Voice[] labSlam() {
        return new Voice[]{
                tone(72f, 38f, 0.7f, 0.62f, 0.002f, new float[]{1f, 0.5f, 0.25f}, 0.7f),
                noise(260f, 0.9f, 0.4f, 0.38f, 0.001f, 0.6f, 4320L),
                noise(3200f, 1.2f, 0.14f, 0.16f, 0.001f, 0.5f, 4321L),
                tone(415f, 415f, 1.3f, 0.11f, 0.002f, new float[]{1f, 0f, 0.4f}, 0.88f),
                tone(415f * 2.76f, 415f * 2.76f, 0.9f, 0.05f, 0.002f, new float[]{1f}, 0.88f),
        };
    }

    /** A win: the chamber filling with light - a swell rising to the moment the drop arrives. */
    public static Voice[] labRising(float seconds) {
        float[] soft = {1f, 0.4f, 0.2f, 0.1f};
        return new Voice[]{
                new Shaped(261.63f, 523.25f, seconds, 0.16f, Shaped.RISE, soft, 0.85f, 0f),
                new Shaped(392f, 784f, seconds, 0.10f, Shaped.RISE, soft, 0.85f, 0f),
                noise(6000f, 2f, seconds, 0.03f, seconds * 0.9f, 0.9f, 4330L),
        };
    }

    /** The drop arriving: bells and the fanfare. */
    public static Voice[] labArrive() {
        Voice[] bells = chime(5, 523.25f);
        Voice[] fan = fanfare();
        Voice[] out = new Voice[bells.length + fan.length + 1];
        int k = 0;
        for (Voice v : bells) out[k++] = v;
        for (Voice v : fan) out[k++] = after(0.12f, v);
        out[k] = noise(9000f, 3f, 2.0f, 0.06f, 0.05f, 0.97f, 4340L);
        return out;
    }

    /** A bust: the empty chamber - a low falling breath, the light going out of it. */
    public static Voice[] labEmpty() {
        return new Voice[]{
                tone(110f, 58f, 1.0f, 0.26f, 0.01f, new float[]{1f, 0.4f}, 0.7f),
                noise(500f, 0.7f, 0.9f, 0.07f, 0.05f, 0.7f, 4350L),
        };
    }

    /** A brazier going out. */
    public static Voice[] labSnuff(long seed) {
        return new Voice[]{noise(1700f, 1.4f, 0.14f, 0.05f, 0.006f, 0.5f, seed)};
    }

    /** A bust's end: the maze sinking back into the ground. */
    public static Voice[] labSink(long seed) {
        return new Voice[]{
                tone(60f, 40f, 0.55f, 0.20f, 0.04f, new float[]{1f, 0.5f}, 0.6f),
                noise(170f, 0.8f, 0.5f, 0.14f, 0.05f, 0.5f, seed),
        };
    }

    /**
     * The Minos Relic's walls lifting away into dust: a soft stone grind under a long fall of
     * tiny glassy tinkles on the A minor pentatonic, thinning out as the last walls go.
     */
    public static Voice[] relicDust(float seconds, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(noise(160f, 0.8f, seconds * 0.8f, 0.10f, seconds * 0.3f, 0.6f, seed));
        float[] scale = {1760f, 2093f, 2349.3f, 2637f, 3136f, 3520f, 4186f};
        java.util.Random rng = new java.util.Random(seed);
        int n = 34;
        for (int i = 0; i < n; i++) {
            float at = seconds * (float) Math.pow(i / (float) n, 0.8f);
            float hz = scale[rng.nextInt(scale.length)];
            float amp = 0.15f * (1f - 0.6f * i / n);
            out.add(after(at, tone(hz, hz, 0.35f, amp, 0.001f, new float[]{1f, 0.2f}, 0.92f)));
        }
        return out.toArray(new Voice[0]);
    }

    /**
     * A ring of the Labyrinth tearing loose: a deep thud, the crack of stone, and debris
     * rattling off it. Each ring a little lower than the last, so the eruption is heard rolling in.
     */
    public static Voice[] relicBurst(int ring, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        float hz = 92f - ring * 6f;
        out.add(tone(hz, hz * 0.5f, 0.45f, 0.42f, 0.002f, new float[]{1f, 0.45f, 0.2f}, 0.6f));
        out.add(noise(900f, 1f, 0.25f, 0.28f, 0.001f, 0.5f, seed));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 7; i++) {
            out.add(after(0.05f + rng.nextFloat() * 0.4f,
                    noise(1500f + rng.nextFloat() * 1800f, 2.2f, 0.05f, 0.07f, 0.001f, 0.5f, seed + 1 + i)));
        }
        return out.toArray(new Voice[0]);
    }

    /** The vortex: wind rising and closing in, two tones climbing under it. Cut on the ignition. */
    public static Voice[] relicWind(float seconds) {
        float[] soft = {1f, 0.35f, 0.15f};
        return new Voice[]{
                noise(650f, 0.8f, seconds, 0.12f, seconds * 0.85f, 0.8f, 4401L),
                noise(2400f, 1.4f, seconds, 0.05f, seconds * 0.95f, 0.85f, 4402L),
                new Shaped(110f, 330f, seconds, 0.10f, Shaped.RISE, soft, 0.8f, 0f),
                new Shaped(164.8f, 494.4f, seconds, 0.07f, Shaped.RISE, soft, 0.8f, 0f),
        };
    }

    /** The moon igniting where the blocks met: a deep boom and a bright chime over it. */
    public static Voice[] relicIgnite() {
        Voice[] bells = chime(4, 659.25f);
        Voice[] out = new Voice[bells.length + 3];
        int k = 0;
        out[k++] = tone(58f, 38f, 1.4f, 0.55f, 0.002f, new float[]{1f, 0.5f, 0.2f}, 0.8f);
        out[k++] = noise(7000f, 2.5f, 1.6f, 0.07f, 0.01f, 0.95f, 4410L);
        out[k++] = noise(300f, 0.9f, 0.6f, 0.2f, 0.002f, 0.7f, 4411L);
        for (Voice v : bells) out[k++] = v;
        return out;
    }

    /**
     * The maze thinking (Brain Food): a low chord whose voices sit a few hertz apart, so it beats
     * - a slow throb, like something alive working - with a thin high shimmer over it.
     */
    public static Voice[] brainHum(float seconds) {
        float[] soft = {1f, 0.4f, 0.18f};
        return new Voice[]{
                swell(110f, seconds, 0.13f, soft),
                swell(114.5f, seconds, 0.10f, soft),
                swell(164.8f, seconds, 0.07f, soft),
                swell(168.9f, seconds, 0.05f, soft),
                noise(5200f, 2.2f, seconds, 0.025f, seconds * 0.6f, 0.9f, 4501L),
        };
    }

    /** Chimera's book rising: soft glassy notes drifting down the pentatonic, like an enchanting table. */
    public static Voice[] chiShimmer(float seconds, long seed) {
        float[] notes = {1760f, 2093f, 2349.3f, 2637f, 3136f, 3520f};
        java.util.Random rng = new java.util.Random(seed);
        Voice[] out = new Voice[10];
        for (int i = 0; i < out.length; i++) {
            float hz = notes[rng.nextInt(notes.length)];
            out[i] = after(seconds * i / out.length, tone(hz, hz, 0.7f, 0.17f, 0.01f, new float[]{1f, 0.25f}, 0.95f));
        }
        return out;
    }

    /** The book opening: a leather creak, then pages riffling over. */
    public static Voice[] chiOpen(long seed) {
        Voice[] out = new Voice[14];
        out[0] = noise(260f, 3f, 0.45f, 0.14f, 0.12f, 0.5f, seed);
        out[1] = tone(140f, 96f, 0.4f, 0.10f, 0.05f, new float[]{1f, 0.6f, 0.4f}, 0.4f);
        for (int i = 2; i < out.length; i++) {
            out[i] = after(0.25f + (i - 2) * 0.05f, noise(2600f + i * 90f, 1.6f, 0.06f, 0.07f, 0.004f, 0.4f, seed + i));
        }
        return out;
    }

    /** The lion: a deep rough growl, rising and falling. */
    public static Voice[] chiLion(long seed) {
        return new Voice[]{
                new Shaped(62f, 78f, 1.2f, 0.30f, Shaped.PAD, new float[]{1f, 0.85f, 0.7f, 0.55f, 0.45f, 0.35f}, 0.5f, 23f),
                noise(190f, 0.7f, 1.1f, 0.22f, 0.15f, 0.5f, seed),
        };
    }

    /** The goat: two quick wavering bleats. */
    public static Voice[] chiGoat() {
        float[] vowel = {1f, 0.6f, 0.5f, 0.3f, 0.2f};
        return new Voice[]{
                new Shaped(560f, 520f, 0.42f, 0.42f, Shaped.PAD, vowel, 0.6f, 11f),
                after(0.5f, new Shaped(600f, 540f, 0.5f, 0.38f, Shaped.PAD, vowel, 0.6f, 12f)),
        };
    }

    /** The serpent: a long hiss, swelling in. */
    public static Voice[] chiSerpent(long seed) {
        return new Voice[]{
                noise(6800f, 1.3f, 1.3f, 0.034f, 0.35f, 0.7f, seed),
                noise(3600f, 2.4f, 1.0f, 0.011f, 0.3f, 0.7f, seed + 1),
        };
    }

    /** The book slamming shut on the heads: a hard flat clap, a boom under it, a bright chime over. */
    public static Voice[] chiSlam() {
        Voice[] bells = chime(4, 880f);
        Voice[] out = new Voice[bells.length + 3];
        int k = 0;
        out[k++] = noise(900f, 0.8f, 0.22f, 0.42f, 0.001f, 0.5f, 4601L);
        out[k++] = tone(92f, 48f, 0.6f, 0.5f, 0.002f, new float[]{1f, 0.5f, 0.2f}, 0.7f);
        out[k++] = noise(5200f, 2f, 1.2f, 0.05f, 0.01f, 0.95f, 4602L);
        for (Voice v : bells) out[k++] = after(0.05f, v);
        return out;
    }

    /** Diana's moon rising: a slow, breathy A minor seventh pad, two voices a note, like the Locket's choir. */
    public static Voice[] relicMoon(float seconds) {
        float[] chord = {220f, 261.63f, 329.63f, 392f, 523.25f};
        float[] vowel = {1f, 0.5f, 0.35f, 0.15f, 0.08f};
        Voice[] out = new Voice[chord.length * 2];
        for (int i = 0; i < chord.length; i++) {
            out[i * 2] = new Shaped(chord[i], chord[i], seconds, 0.055f, Shaped.PAD, vowel, 0.95f, 5f + i * 0.3f);
            out[i * 2 + 1] = new Shaped(chord[i] * 1.004f, chord[i] * 1.004f, seconds, 0.04f, Shaped.PAD, vowel, 0.95f, 4.4f + i * 0.4f);
        }
        return out;
    }

    // ================================================================== The Chopping Block

    /** The head thudding down onto the block: a dull heavy thump, the wood knocking under it. */
    public static Voice[] chopLand(long seed) {
        return new Voice[]{
                tone(96f, 54f, 0.35f, 0.50f, 0.002f, new float[]{1f, 0.45f, 0.2f}, 0.45f),
                noise(420f, 0.9f, 0.18f, 0.30f, 0.001f, 0.4f, seed),
                tone(620f, 540f, 0.09f, 0.10f, 0.001f, new float[]{1f, 0.5f}, 0.3f),
        };
    }

    /** The cleaver coming down: a short swelling swish, brighter each blow. */
    public static Voice[] chopWhoosh(int blow, long seed) {
        float hz = 1100f + blow * 260f;
        return new Voice[]{
                noise(hz, 1.1f, 0.14f, 0.09f + blow * 0.015f, 0.11f, 0.3f, seed),
                noise(hz * 2.6f, 2f, 0.12f, 0.03f, 0.1f, 0.3f, seed + 1),
        };
    }

    /**
     * A chop: the blade biting in - a wet crunch - and the wood thocking under it, with the steel
     * ringing on after, a step higher each blow. The same on every outcome.
     */
    public static Voice[] chopHit(int blow, long seed) {
        float ring = 880f * (float) Math.pow(2, blow * 2 / 12.0);
        // Harder each time: the first is the quietest, the split (chopSplit) the loudest.
        float k = 0.62f + 0.19f * blow;
        return new Voice[]{
                noise(320f, 1f, 0.13f, 0.36f * k, 0.001f, 0.35f, seed),
                noise(2400f, 1.5f, 0.08f, 0.13f * k, 0.001f, 0.35f, seed + 1),
                tone(190f + blow * 12f, 112f, 0.2f, 0.34f * k, 0.001f, new float[]{1f, 0.4f, 0.15f}, 0.45f),
                tone(ring, ring, 0.45f, 0.05f, 0.002f, new float[]{1f}, 0.8f),
                tone(ring * 2.76f, ring * 2.76f, 0.3f, 0.02f, 0.002f, new float[]{1f}, 0.8f),
        };
    }

    /**
     * The split: the cleaver going right through and into the block - a heavy boom, the crack of
     * the head coming apart, the steel ringing long. The same on every outcome; what comes after
     * is what tells.
     */
    public static Voice[] chopSplit() {
        return new Voice[]{
                tone(70f, 36f, 0.8f, 0.60f, 0.002f, new float[]{1f, 0.5f, 0.25f}, 0.7f),
                noise(250f, 0.8f, 0.45f, 0.40f, 0.001f, 0.6f, 4701L),
                noise(3000f, 1.2f, 0.2f, 0.20f, 0.001f, 0.5f, 4702L),
                tone(587.3f, 587.3f, 1.3f, 0.10f, 0.002f, new float[]{1f, 0f, 0.35f}, 0.88f),
                tone(587.3f * 2.76f, 587.3f * 2.76f, 0.9f, 0.045f, 0.002f, new float[]{1f}, 0.88f),
        };
    }

    /** A bust: the halves crumbling off the block - dry little clicks thinning out, rubble under them. */
    public static Voice[] chopCrumble(long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(noise(700f, 0.8f, 0.6f, 0.10f, 0.02f, 0.4f, seed));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 14; i++) {
            float at = 0.7f * (float) Math.pow(i / 14f, 1.4f);
            out.add(after(at, noise(1800f + rng.nextFloat() * 2200f, 2.2f, 0.04f, 0.09f * (1f - 0.5f * i / 14f), 0.001f, 0.3f, seed + 1 + i)));
        }
        return out.toArray(new Voice[0]);
    }

    // ================================================================== The Fatal Sting

    /** The tail rising out of the split: a low rumble with a grinding edge. */
    public static Voice[] stingRise(float seconds, long seed) {
        return new Voice[]{
                new Shaped(52f, 68f, seconds, 0.4f, Shaped.RISE, new float[]{1f, 0.6f, 0.35f, 0.2f}, 0.6f, 0f),
                noise(240f, 1.2f, seconds, 0.2f, seconds * 0.5f, 0.5f, seed),
        };
    }

    /** A plate of chitin locking into place: a hard dry click, higher as the tail climbs, lower as it sinks. */
    public static Voice[] stingClick(int plate, boolean sinking, long seed) {
        float hz = (sinking ? 1400f : 900f) * (float) Math.pow(2, (sinking ? -plate : plate) * 1.5 / 12.0);
        return new Voice[]{
                noise(3600f, 2.4f, 0.035f, 0.12f, 0.001f, 0.25f, seed),
                tone(hz, hz * 0.94f, 0.06f, 0.07f, 0.001f, new float[]{1f, 0.5f, 0.3f}, 0.3f),
        };
    }

    /** The tail arching over, or drawing back for the strike: a creak under tension. */
    public static Voice[] stingCreak(float seconds, float fromHz, float toHz, long seed) {
        return new Voice[]{
                new Shaped(fromHz, toHz, seconds, 0.14f, Shaped.RISE, new float[]{1f, 0.7f, 0.6f, 0.5f, 0.4f, 0.3f}, 0.3f, 17f),
                noise(900f, 3f, seconds, 0.1f, seconds * 0.6f, 0.3f, seed),
        };
    }

    /** The sway: a long, low hiss, rising and falling. */
    public static Voice[] stingHiss(float seconds, long seed) {
        return new Voice[]{
                noise(6400f, 1.2f, seconds, 0.05f, seconds * 0.35f, 0.6f, seed),
                noise(3300f, 2.2f, seconds, 0.018f, seconds * 0.5f, 0.6f, seed + 1),
        };
    }

    /** A drop of venom on the board: a plip, then it sizzles. */
    public static Voice[] stingDrip(long seed) {
        return new Voice[]{
                tone(1500f, 2800f, 0.06f, 0.14f, 0.001f, new float[]{1f, 0.25f}, 0.5f),
                after(0.04f, noise(7200f, 1.4f, 0.55f, 0.05f, 0.02f, 0.5f, seed)),
        };
    }

    /** The strike coming: a whoosh swelling into the hit. */
    public static Voice[] stingWhoosh(float seconds, long seed) {
        return new Voice[]{
                noise(1700f, 0.9f, seconds + 0.04f, 0.15f, seconds, 0.3f, seed),
                noise(4200f, 1.6f, seconds + 0.04f, 0.05f, seconds, 0.3f, seed + 1),
        };
    }

    /**
     * The barb hitting the glass: a whip-crack, a heavy thud, the glass shattering - a spray of
     * bright shards and inharmonic pings - and the wet slap of the venom.
     */
    public static Voice[] stingImpact(long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(noise(2800f, 0.7f, 0.03f, 0.27f, 0.001f, 0.4f, seed));
        out.add(tone(96f, 44f, 0.5f, 0.3f, 0.001f, new float[]{1f, 0.5f, 0.2f}, 0.6f));
        out.add(noise(620f, 1f, 0.16f, 0.13f, 0.002f, 0.4f, seed + 1));
        java.util.Random r = new java.util.Random(seed);
        for (int i = 0; i < 14; i++) {
            float at = 0.005f + (float) Math.pow(r.nextFloat(), 1.6) * 0.35f;
            out.add(after(at, noise(4200f + r.nextFloat() * 6000f, 3f, 0.03f + r.nextFloat() * 0.05f,
                    0.04f + 0.03f * r.nextFloat(), 0.001f, 0.6f, seed + 10 + i)));
        }
        float[] pings = {2310f, 3170f, 3940f, 4870f, 5620f};
        for (int i = 0; i < pings.length; i++) {
            out.add(after(0.01f + i * 0.018f, tone(pings[i], pings[i], 0.6f, 0.025f, 0.001f, new float[]{1f}, 0.85f)));
        }
        return out.toArray(new Voice[0]);
    }

    /** The barb driven into the board: a hard wooden thock, the crunch of it going in, and a wet splash. */
    public static Voice[] stingStab(long seed) {
        return new Voice[]{
                tone(150f, 85f, 0.25f, 0.42f, 0.001f, new float[]{1f, 0.45f, 0.15f}, 0.45f),
                noise(380f, 1f, 0.12f, 0.3f, 0.001f, 0.35f, seed),
                noise(2600f, 1.4f, 0.06f, 0.14f, 0.001f, 0.35f, seed + 1),
                after(0.02f, noise(900f, 0.9f, 0.22f, 0.2f, 0.004f, 0.45f, seed + 2)),
                after(0.03f, tone(1300f, 2600f, 0.07f, 0.1f, 0.001f, new float[]{1f, 0.25f}, 0.5f)),
        };
    }

    /** The glass healing: the shatter played backwards - pings and tinkles gathering to nothing. */
    public static Voice[] stingHeal(float seconds, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(reverseSwell(seconds)[0]);
        java.util.Random r = new java.util.Random(seed);
        float[] notes = {3520f, 3136f, 2637f, 2349.3f, 2093f, 1760f};
        for (int i = 0; i < 10; i++) {
            float at = seconds * (float) Math.pow(i / 10f, 0.7f);
            float hz = notes[r.nextInt(notes.length)];
            out.add(after(at, tone(hz, hz, 0.25f, 0.1f, 0.001f, new float[]{1f, 0.2f}, 0.85f)));
        }
        return out.toArray(new Voice[0]);
    }

    // ================================================================== Wet Paint

    /** Time stopping: everything pitches down to nothing, like a tape run to a halt. */
    public static Voice[] paintFreeze(long seed) {
        float[] rich = {1f, 0.6f, 0.45f, 0.3f, 0.2f};
        return new Voice[]{
                new Shaped(440f, 38f, 0.7f, 0.34f, Shaped.PAD, rich, 0.5f, 0f),
                new Shaped(660f, 57f, 0.7f, 0.2f, Shaped.PAD, rich, 0.5f, 0f),
                noise(320f, 0.8f, 0.35f, 0.25f, 0.005f, 0.5f, seed),
        };
    }

    /** Stopped time: a low hum and one thin high tone, the ringing of a silent room. */
    public static Voice[] paintDrone(float seconds) {
        return new Voice[]{
                swell(55f, seconds, 0.17f, new float[]{1f, 0.3f}),
                swell(2637f, seconds, 0.025f, new float[]{1f}),
                noise(1200f, 0.7f, seconds, 0.015f, seconds * 0.4f, 0.9f, 4801L),
        };
    }

    /** The drop letting go of the blade: a thick wet blup. */
    public static Voice[] paintBlup() {
        return new Voice[]{
                tone(300f, 820f, 0.11f, 0.5f, 0.004f, new float[]{1f, 0.35f, 0.1f}, 0.5f),
                noise(900f, 2f, 0.06f, 0.1f, 0.004f, 0.4f, 4802L),
        };
    }

    /** The splash: a wet slap, a low thump under it, and a bright chime - the colour coming back. */
    public static Voice[] paintSplash(long seed) {
        Voice[] bells = chime(4, 659.25f);
        Voice[] out = new Voice[bells.length + 4];
        int k = 0;
        out[k++] = noise(850f, 0.8f, 0.45f, 0.3f, 0.002f, 0.5f, seed);
        out[k++] = noise(2600f, 1.1f, 0.3f, 0.12f, 0.002f, 0.5f, seed + 1);
        out[k++] = tone(82f, 44f, 0.5f, 0.36f, 0.002f, new float[]{1f, 0.5f, 0.2f}, 0.6f);
        out[k++] = tone(520f, 1400f, 0.12f, 0.08f, 0.004f, new float[]{1f, 0.3f}, 0.6f);
        for (Voice v : bells) out[k++] = after(0.05f, v);
        return out;
    }

    /** The wave rolling out: a rush that brightens as it goes, over a rising chord. */
    public static Voice[] paintWave(float seconds, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            float at = seconds * i / 7f;
            out.add(after(at, noise(500f * (float) Math.pow(1.6, i), 0.9f, seconds * 0.45f, 0.06f, seconds * 0.15f, 0.7f, seed + i)));
        }
        float[] soft = {1f, 0.4f, 0.2f, 0.1f};
        out.add(new Shaped(220f, 440f, seconds, 0.1f, Shaped.RISE, soft, 0.85f, 0f));
        out.add(new Shaped(329.63f, 659.25f, seconds, 0.07f, Shaped.RISE, soft, 0.85f, 0f));
        return out.toArray(new Voice[0]);
    }

    /** Time starting again: the tape spinning back up. */
    public static Voice[] paintResume() {
        float[] rich = {1f, 0.6f, 0.45f, 0.3f};
        return new Voice[]{
                new Shaped(45f, 330f, 0.32f, 0.5f, Shaped.RISE, rich, 0.4f, 0f),
        };
    }

    /** A paint drop landing on the board. */
    public static Voice[] paintPlip(int i) {
        float hz = 900f * (float) Math.pow(2, (i * 5 % 12) / 12.0);
        return new Voice[]{
                tone(hz, hz * 1.9f, 0.05f, 0.4f, 0.001f, new float[]{1f, 0.25f}, 0.5f),
        };
    }

    // ================================================================== The Golden Fleece

    /** The fleece unravelling: a harp glissando up the pentatonic, plucked, ringing on. */
    public static Voice[] fleeceHarp(float seconds, boolean down) {
        float[] steps = {0, 2, 4, 7, 9, 12, 14, 16, 19, 21, 24, 26, 28, 31};
        Voice[] out = new Voice[steps.length];
        float[] string = {1f, 0.5f, 0.28f, 0.14f, 0.07f};
        for (int i = 0; i < steps.length; i++) {
            int k = down ? steps.length - 1 - i : i;
            float hz = 293.66f * (float) Math.pow(2, steps[k] / 12.0);
            out[i] = after(seconds * i / steps.length, tone(hz, hz, 1.1f, 0.26f, 0.002f, string, 0.9f));
        }
        return out;
    }

    /** Soft air under the drifting strands. */
    public static Voice[] fleeceAir(float seconds, long seed) {
        float[] soft = {1f, 0.3f, 0.1f};
        return new Voice[]{
                noise(2800f, 0.7f, seconds, 0.035f, seconds * 0.4f, 0.9f, seed),
                swell(293.66f, seconds, 0.042f, soft),
                swell(440f, seconds, 0.028f, soft),
        };
    }

    /**
     * The winding: a soft whirr - a spinning wheel - its turns coming faster and higher until they
     * run together.
     */
    public static Voice[] fleeceWind(float seconds, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        float t = 0f;
        int i = 0;
        while (t < seconds && i < 60) {
            float u = t / seconds;
            out.add(after(t, noise(700f + 2200f * u, 1.6f, 0.07f, 0.05f + 0.06f * u, 0.02f, 0.6f, seed + i)));
            t += 0.2f * (1f - 0.8f * u);
            i++;
        }
        out.add(new Shaped(146.83f, 293.66f, seconds, 0.09f, Shaped.RISE, new float[]{1f, 0.4f, 0.2f}, 0.85f, 0f));
        return out.toArray(new Voice[0]);
    }

    /** The ball blooming into the block: a soft bright chime and a breath. */
    public static Voice[] fleeceBloom(long seed) {
        Voice[] bells = chime(4, 587.33f);
        Voice[] out = new Voice[bells.length + 1];
        for (int i = 0; i < bells.length; i++) out[i] = bells[i];
        out[bells.length] = noise(4200f, 0.8f, 0.5f, 0.07f, 0.04f, 0.9f, seed);
        return out;
    }

    /** The block settling: a soft, muffled thump - wool - and a few sparkles. */
    public static Voice[] fleeceSettle(long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(tone(110f, 68f, 0.3f, 0.3f, 0.01f, new float[]{1f, 0.3f}, 0.5f));
        out.add(noise(330f, 0.7f, 0.25f, 0.12f, 0.01f, 0.5f, seed));
        float[] notes = {2349.3f, 2637f, 3136f, 3520f, 2093f};
        for (int i = 0; i < 9; i++) {
            float hz = notes[(i * 3) % notes.length];
            out.add(after(0.06f + i * 0.11f, tone(hz, hz, 0.35f, 0.07f, 0.001f, new float[]{1f, 0.2f}, 0.9f)));
        }
        return out.toArray(new Voice[0]);
    }

    // ================================================================== The Heart of the Beast

    /** Under the heartbeats: a low rumble rising as they quicken. */
    public static Voice[] heartRumble(float seconds, long seed) {
        return new Voice[]{
                new Shaped(38f, 62f, seconds, 0.36f, Shaped.RISE, new float[]{1f, 0.5f, 0.25f}, 0.6f, 0f),
                noise(140f, 0.9f, seconds, 0.12f, seconds * 0.8f, 0.6f, seed),
        };
    }

    /** The wings unfurling: leather stretching, a creak, and a snap as they go taut. */
    public static Voice[] wingUnfurl(float seconds, long seed) {
        return new Voice[]{
                noise(520f, 2.2f, seconds, 0.16f, seconds * 0.7f, 0.4f, seed),
                new Shaped(95f, 150f, seconds, 0.12f, Shaped.RISE, new float[]{1f, 0.7f, 0.6f, 0.5f, 0.4f}, 0.3f, 13f),
                after(seconds, noise(1500f, 1.2f, 0.07f, 0.15f, 0.001f, 0.5f, seed + 1)),
                after(seconds, tone(120f, 70f, 0.25f, 0.15f, 0.002f, new float[]{1f, 0.4f}, 0.5f)),
        };
    }

    /** One great flap: a deep whoosh of air and the thump of it. */
    public static Voice[] wingGust(long seed) {
        return new Voice[]{
                noise(620f, 0.7f, 0.75f, 0.32f, 0.12f, 0.6f, seed),
                noise(1900f, 1f, 0.6f, 0.12f, 0.1f, 0.6f, seed + 1),
                after(0.1f, tone(70f, 40f, 0.4f, 0.4f, 0.004f, new float[]{1f, 0.4f}, 0.5f)),
        };
    }

    /** The wings folding round the heart: a leathery swish closing in, and a soft clap as they meet. */
    public static Voice[] wingWrap(float seconds, long seed) {
        return new Voice[]{
                noise(900f, 1.4f, seconds, 0.14f, seconds * 0.8f, 0.4f, seed),
                after(seconds, noise(700f, 1.1f, 0.12f, 0.25f, 0.001f, 0.4f, seed + 1)),
        };
    }

    /** A heartbeat heard through the wings: low and dull, a double thud. */
    public static Voice[] heartMuffled() {
        return new Voice[]{
                tone(52f, 40f, 0.18f, 0.8f, 0.004f, new float[]{1f, 0.2f}, 0.3f),
                after(0.19f, tone(48f, 38f, 0.16f, 0.58f, 0.004f, new float[]{1f, 0.2f}, 0.3f)),
        };
    }

    // ================================================================== Midas

    /**
     * Gold creeping over everything: a low metallic groan, and a patter of tiny bright tinks - a
     * pixel turning at a time - thickening as it spreads.
     */
    public static Voice[] midasCreep(float seconds, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(new Shaped(73f, 98f, seconds, 0.4f, Shaped.RISE, new float[]{1f, 0f, 0.5f, 0f, 0.3f, 0.2f}, 0.7f, 3f));
        java.util.Random r = new java.util.Random(seed);
        for (int i = 0; i < 40; i++) {
            float at = seconds * (float) Math.sqrt(i / 40f);
            float hz = 2400f + r.nextFloat() * 3600f;
            out.add(after(at, tone(hz, hz, 0.18f, 0.14f, 0.001f, new float[]{1f, 0f, 0.3f}, 0.8f)));
        }
        return out.toArray(new Voice[0]);
    }

    /** The golden halves shutting: a heavy metal clank, ringing inharmonically. */
    public static Voice[] midasClank(long seed) {
        return new Voice[]{
                noise(1400f, 1.2f, 0.07f, 0.35f, 0.001f, 0.5f, seed),
                tone(196f, 190f, 1.1f, 0.3f, 0.001f, new float[]{1f, 0.4f}, 0.8f),
                tone(196f * 2.76f, 196f * 2.76f, 0.8f, 0.12f, 0.001f, new float[]{1f}, 0.8f),
                tone(196f * 5.4f, 196f * 5.4f, 0.5f, 0.05f, 0.001f, new float[]{1f}, 0.8f),
                tone(80f, 50f, 0.35f, 0.3f, 0.002f, new float[]{1f, 0.4f}, 0.5f),
        };
    }

    /** The shine sweeping the statue: one bright glint. */
    public static Voice[] midasShine() {
        return new Voice[]{
                tone(3136f, 3136f, 0.9f, 0.55f, 0.002f, new float[]{1f, 0.3f}, 0.92f),
                after(0.06f, tone(4186f, 4186f, 0.7f, 0.35f, 0.002f, new float[]{1f}, 0.92f)),
        };
    }

    /** The crown settling: a chord of bells. */
    public static Voice[] midasCrowned() {
        return chime(5, 523.25f);
    }

    /**
     * The statue collapsing into coins: a rumble, then an avalanche of clinks - hundreds of coins
     * falling on each other - thinning out as they fall away.
     */
    public static Voice[] midasAvalanche(long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(tone(60f, 36f, 0.9f, 0.4f, 0.004f, new float[]{1f, 0.5f, 0.2f}, 0.6f));
        out.add(noise(500f, 0.8f, 0.8f, 0.2f, 0.02f, 0.6f, seed));
        java.util.Random r = new java.util.Random(seed);
        for (int i = 0; i < 90; i++) {
            float at = 1.8f * (float) Math.pow(i / 90f, 1.7);
            float hz = 3000f + r.nextFloat() * 4200f;
            float amp = 0.07f * (1f - 0.6f * i / 90f);
            out.add(after(at, tone(hz, hz * 0.98f, 0.12f, amp, 0.001f, new float[]{1f, 0f, 0.45f}, 0.6f)));
            if (i % 3 == 0) out.add(after(at, noise(5200f, 2.5f, 0.03f, amp * 0.8f, 0.001f, 0.5f, seed + i)));
        }
        return out.toArray(new Voice[0]);
    }

    /** Fin's bust: the cleaver plunging into the water where the fin was - a wet plunk and a gurgle. */
    /** Fin: the line whipping taut as the hook is yanked - a rising zip, a swish and the line's low twang. */
    public static Voice[] finYank(long seed) {
        return new Voice[]{
                tone(420f, 1700f, 0.11f, 0.18f, 0.002f, new float[]{1f, 0.3f}, 0.5f),
                noise(3200f, 1.2f, 0.14f, 0.16f, 0.002f, 0.5f, seed),
                after(0.03f, tone(196f, 190f, 0.45f, 0.13f, 0.002f, new float[]{1f, 0.5f, 0.3f, 0.15f}, 0.5f)),
        };
    }

    public static Voice[] finPlunge(long seed) {
        return new Voice[]{
                tone(240f, 520f, 0.09f, 0.35f, 0.003f, new float[]{1f, 0.35f, 0.1f}, 0.5f),
                noise(900f, 0.8f, 0.35f, 0.22f, 0.002f, 0.5f, seed),
                noise(2600f, 1.1f, 0.2f, 0.08f, 0.002f, 0.5f, seed + 1),
                after(0.18f, tone(420f, 900f, 0.05f, 0.18f, 0.001f, new float[]{1f, 0.25f}, 0.5f)),
                after(0.32f, tone(520f, 1100f, 0.05f, 0.13f, 0.001f, new float[]{1f, 0.25f}, 0.5f)),
        };
    }

    // ================================================================== The Seraph's Glyph (Enderman slayer)
    //
    // C# minor, an End drone under it, and the enderman's own "vwoop" for every teleport. Each hit on the
    // shield is a thud and a glass clink whose pitch follows the beacon's colour, so the cycle is heard
    // as well as seen; a win turns to C# major.

    /** The clink of each stained glass: black C#5 (dull), white E5, light blue G#5, magenta B5, purple C#6. */
    private static final float[] SERAPH_GLASS = {554.37f, 659.25f, 830.61f, 987.77f, 1108.73f};

    /** The teleport: a sweep down and back up with a breath of air - the "vwoop". {@code pitch} scales it. */
    public static Voice[] seraphVwoop(float pitch, long seed) {
        float k = Math.max(0.4f, pitch);
        return new Voice[]{
                tone(520f * k, 170f * k, 0.13f, 0.30f, 0.004f, new float[]{1f, 0.5f, 0.25f}, 0.5f),
                after(0.07f, tone(210f * k, 720f * k, 0.16f, 0.24f, 0.004f, new float[]{1f, 0.4f, 0.15f}, 0.6f)),
                noise(1700f * k, 1.2f, 0.22f, 0.10f, 0.03f, 0.5f, seed),
        };
    }

    /** The portal swirl closing in before it appears: a rising rush. */
    public static Voice[] seraphSwirl(float seconds, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            out.add(after(seconds * i / 6f, noise(500f * (float) Math.pow(1.5, i), 1f, seconds * 0.5f, 0.05f + 0.01f * i, seconds * 0.3f, 0.6f, seed + i)));
        }
        return out.toArray(new Voice[0]);
    }

    /** The End under the whole build-up: a low C# with a tritone on it, a thin high whine, faint sparkles. */
    public static Voice[] seraphDrone(float seconds, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(new Shaped(69.30f, 69.30f, seconds, 0.17f, Shaped.RISE, new float[]{1f, 0.5f, 0.3f}, 0.6f, 0f));
        out.add(new Shaped(98f, 98f, seconds, 0.06f, Shaped.RISE, new float[]{1f, 0.3f}, 0.6f, 0f));
        out.add(new Shaped(2217.46f, 2217.46f, seconds, 0.012f, Shaped.RISE, new float[]{1f}, 0.9f, 5f));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 26; i++) {
            float at = rng.nextFloat() * seconds * 0.95f;
            float hz = 2500f + rng.nextFloat() * 3500f;
            out.add(after(at, tone(hz, hz, 0.12f, 0.025f, 0.001f, new float[]{1f}, 0.9f)));
        }
        return out.toArray(new Voice[0]);
    }

    /** The obsidian pillars grinding up out of the ground. */
    public static Voice[] seraphPillars(long seed) {
        return new Voice[]{
                noise(120f, 0.8f, 0.7f, 0.16f, 0.25f, 0.4f, seed),
                tone(55f, 49f, 0.7f, 0.18f, 0.1f, new float[]{1f, 0.4f}, 0.5f),
        };
    }

    /** The crystals' beams locking on: a glassy hum, E and G#, pulsing - it lasts until the reveal. */
    public static Voice[] seraphCrystalHum(float seconds) {
        float[] glass = {1f, 0.2f, 0.4f};
        return new Voice[]{
                new Shaped(659.25f, 659.25f, seconds, 0.03f, Shaped.RISE, glass, 0.85f, 4f),
                new Shaped(830.61f, 830.61f, seconds, 0.02f, Shaped.RISE, glass, 0.85f, 5.5f),
        };
    }

    /**
     * A hit on the Malevolent Hitshield: a dull thud and a glass clink in the colour the beacon has just
     * turned. A semitone higher each round of five, and harder each hit.
     */
    public static Voice[] seraphHit(int glass, int round, float progress, long seed) {
        float hz = SERAPH_GLASS[Math.max(0, Math.min(4, glass))] * (float) Math.pow(2, round / 12.0);
        float k = 0.7f + 0.4f * progress;
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(tone(120f, 75f, 0.15f, 0.30f * k, 0.002f, new float[]{1f, 0.4f, 0.15f}, 0.4f));
        out.add(noise(380f, 0.9f, 0.08f, 0.16f * k, 0.001f, 0.35f, seed));
        float ring = glass == 0 ? 0.18f : 0.5f;
        out.add(tone(hz, hz, ring, 0.12f * k, 0.001f, new float[]{1f, 0f, 0.3f}, 0.85f));
        out.add(tone(hz * 2.76f, hz * 2.76f, ring * 0.6f, 0.04f * k, 0.001f, new float[]{1f}, 0.85f));
        out.add(noise(5200f, 2.5f, 0.03f, 0.08f * k, 0.0005f, 0.4f, seed + 1));
        return out.toArray(new Voice[0]);
    }

    /** The angry enderman: a harsh, wavering scream, low under everything, growing to the reveal. */
    public static Voice[] seraphScream(float seconds, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            float at = seconds * i / 6f;
            out.add(after(at, noise(1050f + 120f * i, 4f, seconds / 5f, 0.025f + 0.008f * i, seconds / 12f, 0.6f, seed + i)));
        }
        out.add(new Shaped(185f, 196f, seconds, 0.05f, Shaped.RISE, new float[]{1f, 0.7f, 0.5f, 0.4f, 0.3f}, 0.5f, 7f));
        return out.toArray(new Voice[0]);
    }

    /** The last stretch: C# minor rising, with the D rubbing against it, cut dead at the reveal. */
    public static Voice[] seraphBuild(float seconds) {
        float[] rich = {1f, 0.5f, 0.3f, 0.18f, 0.1f};
        return new Voice[]{
                new Shaped(69.30f, 138.59f, seconds, 0.26f, Shaped.RISE, rich, 0.6f, 0f),
                new Shaped(103.83f, 207.65f, seconds, 0.17f, Shaped.RISE, rich, 0.6f, 0f),
                new Shaped(73.42f, 146.83f, seconds, 0.08f, Shaped.RISE, rich, 0.6f, 0f),
                noise(1800f, 1f, seconds, 0.07f, seconds * 0.95f, 0.8f, 7301L),
                noise(400f, 0.7f, seconds, 0.11f, seconds * 0.95f, 0.6f, 7302L),
        };
    }

    /** Broken Heart Radiation: a buzzing hum of lasers, a tritone apart, with a zap as each group fires. */
    public static Voice[] seraphLasers(float seconds, long seed) {
        float[] buzz = {1f, 0f, 0.33f, 0f, 0.2f, 0f, 0.14f};
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(new Shaped(138.59f, 146.83f, seconds, 0.10f, Shaped.RISE, buzz, 0.5f, 9f));
        out.add(new Shaped(196f, 207.65f, seconds, 0.07f, Shaped.RISE, buzz, 0.5f, 11f));
        for (int g = 0; g < 4; g++) {
            out.add(after(seconds * g * 0.12f / 0.85f, noise(3000f, 1.5f, 0.12f, 0.12f, 0.002f, 0.5f, seed + g)));
            out.add(after(seconds * g * 0.12f / 0.85f, tone(1760f, 880f, 0.1f, 0.06f, 0.001f, new float[]{1f, 0.5f}, 0.5f)));
        }
        return out.toArray(new Voice[0]);
    }

    /** The colours flickering in the hang: tiny glass ticks. */
    public static Voice[] seraphFlicker(int i) {
        float hz = SERAPH_GLASS[i % 5] * 2f;
        return new Voice[]{tone(hz, hz, 0.04f, 0.045f, 0.0005f, new float[]{1f, 0.2f}, 0.6f)};
    }

    /** The wind-up and the throw: a rush up, then a heavier one down. */
    public static Voice[] seraphThrow(boolean down, long seed) {
        return new Voice[]{
                noise(down ? 900f : 1300f, 1.1f, down ? 0.18f : 0.12f, down ? 0.14f : 0.08f, down ? 0.14f : 0.1f, 0.4f, seed),
        };
    }

    /** A bust: the beacon powering down - a falling glassy chime - and a dull thud. */
    public static Voice[] seraphPowerDown(long seed) {
        return new Voice[]{
                tone(80f, 40f, 0.45f, 0.4f, 0.002f, new float[]{1f, 0.4f}, 0.5f),
                noise(300f, 0.8f, 0.25f, 0.18f, 0.001f, 0.4f, seed),
                tone(1108.73f, 1108.73f, 0.25f, 0.10f, 0.002f, new float[]{1f, 0.3f}, 0.85f),
                after(0.12f, tone(987.77f, 987.77f, 0.25f, 0.09f, 0.002f, new float[]{1f, 0.3f}, 0.85f)),
                after(0.24f, tone(830.61f, 700f, 0.45f, 0.08f, 0.002f, new float[]{1f, 0.3f}, 0.85f)),
                after(0.1f, noise(2200f, 0.9f, 0.6f, 0.06f, 0.01f, 0.6f, seed + 1)),
        };
    }

    /** A win: the beacon powering up - a rising chime and a shimmer - as it lands with a boom. */
    public static Voice[] seraphPowerUp(int glass, long seed) {
        float top = SERAPH_GLASS[Math.max(1, Math.min(4, glass))];
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(tone(62f, 30f, 0.9f, 0.42f, 0.002f, new float[]{1f, 0.5f, 0.25f}, 0.7f));
        out.add(noise(220f, 0.8f, 0.45f, 0.24f, 0.001f, 0.6f, seed));
        float[] arp = {554.37f, 659.25f, 830.61f, 1108.73f};
        for (int i = 0; i < arp.length; i++) {
            out.add(after(0.05f + i * 0.06f, tone(arp[i], arp[i], 0.6f, 0.10f, 0.002f, new float[]{1f, 0.3f, 0.1f}, 0.88f)));
        }
        out.add(after(0.3f, new Shaped(top * 2f, top * 2f, 1.6f, 0.03f, Shaped.PAD, new float[]{1f}, 0.95f, 6f)));
        return out.toArray(new Voice[0]);
    }

    /** The glass shattering. */
    public static Voice[] seraphShatter(long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(noise(6500f, 1f, 0.3f, 0.18f, 0.001f, 0.6f, seed));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 18; i++) {
            float at = 0.6f * (float) Math.pow(rng.nextFloat(), 1.6f);
            float hz = 2200f + rng.nextFloat() * 4200f;
            out.add(after(at, tone(hz, hz, 0.06f + rng.nextFloat() * 0.12f, 0.035f + 0.035f * rng.nextFloat(), 0.001f, new float[]{1f, 0.3f}, 0.85f)));
        }
        return out.toArray(new Voice[0]);
    }

    /** The two crystals going up, one just after the other. */
    public static Voice[] seraphCrystalsBlow(long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        for (int i = 0; i < 2; i++) {
            float at = i * 0.12f;
            out.add(after(at, tone(58f, 26f, 0.9f, 0.34f, 0.002f, new float[]{1f, 0.5f, 0.3f}, 0.7f)));
            out.add(after(at, noise(160f, 0.6f, 0.8f, 0.24f, 0.002f, 0.7f, seed + i)));
            out.add(after(at, noise(2600f, 0.8f, 0.4f, 0.10f, 0.001f, 0.6f, seed + 10 + i)));
        }
        return out.toArray(new Voice[0]);
    }

    /** The enderman's death: a harsh scream falling away, then the poof. */
    public static Voice[] seraphDeath(long seed) {
        return new Voice[]{
                new Shaped(620f, 130f, 0.9f, 0.10f, Shaped.PAD, new float[]{1f, 0.7f, 0.5f, 0.4f, 0.3f}, 0.5f, 8f),
                noise(1100f, 4f, 0.8f, 0.06f, 0.05f, 0.5f, seed),
        };
    }

    public static Voice[] seraphPoof(long seed) {
        return new Voice[]{noise(900f, 0.8f, 0.3f, 0.10f, 0.02f, 0.5f, seed)};
    }

    /** A win, before the drop shows: a chord rising into C# major. */
    public static Voice[] seraphRise(float seconds) {
        float[] soft = {1f, 0.45f, 0.25f, 0.12f};
        return new Voice[]{
                new Shaped(138.59f, 277.18f, seconds, 0.15f, Shaped.RISE, soft, 0.85f, 0f),
                new Shaped(207.65f, 415.30f, seconds, 0.10f, Shaped.RISE, soft, 0.85f, 0f),
                new Shaped(174.61f, 349.23f, seconds, 0.07f, Shaped.RISE, soft, 0.85f, 0f),
                noise(900f, 0.7f, seconds, 0.06f, seconds * 0.9f, 0.6f, 7310L),
        };
    }

    /** The drop shown: a gong, a C# major brass chord and bells; the big ones get a second chord and a shimmer. */
    public static Voice[] seraphArrive(boolean big, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        float[] brass = {1f, 0.8f, 0.65f, 0.5f, 0.4f, 0.3f, 0.2f};
        out.add(tone(69.30f, 69.30f, 2.6f, 0.30f, 0.004f, new float[]{1f, 0f, 0.5f, 0f, 0.3f}, 0.85f));
        out.add(tone(69.30f * 2.76f, 69.30f * 2.76f, 1.8f, 0.07f, 0.004f, new float[]{1f}, 0.85f));
        for (float hz : new float[]{138.59f, 174.61f, 207.65f, 277.18f}) out.add(tone(hz, hz, 1.2f, 0.10f, 0.02f, brass, 0.7f));
        for (Voice v : chime(5, 554.37f)) out.add(after(0.1f, v));
        out.add(noise(9000f, 3f, 2.0f, 0.05f, 0.05f, 0.97f, seed));
        if (big) {
            for (float hz : new float[]{185f, 233.08f, 277.18f, 369.99f}) out.add(after(0.38f, tone(hz, hz, 1.4f, 0.10f, 0.02f, brass, 0.75f)));
            java.util.Random rng = new java.util.Random(seed);
            for (int i = 0; i < 36; i++) {
                float at = 0.4f + 1.6f * rng.nextFloat();
                float hz = 3000f + rng.nextFloat() * 3000f;
                out.add(after(at, tone(hz, hz, 0.05f + 0.06f * rng.nextFloat(), 0.035f, 0.0005f, new float[]{1f, 0.3f}, 0.8f)));
            }
        }
        return out.toArray(new Voice[0]);
    }

    /** Under the drop while it is shown: C# major held and breathing out. */
    public static Voice[] seraphGlow(float seconds, boolean big, long seed) {
        float[] soft = {1f, 0.35f, 0.15f};
        java.util.List<Voice> out = new java.util.ArrayList<>();
        float amp = big ? 0.085f : 0.05f;
        for (float hz : new float[]{138.59f, 174.61f, 207.65f, 277.18f}) out.add(new Shaped(hz, hz, seconds, amp, Shaped.PAD, soft, 0.9f, 4.5f));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 12; i++) {
            float at = rng.nextFloat() * seconds * 0.85f;
            float hz = 2400f + rng.nextFloat() * 3000f;
            out.add(after(at, tone(hz, hz, 0.12f, 0.02f, 0.001f, new float[]{1f}, 0.9f)));
        }
        return out.toArray(new Voice[0]);
    }

    /** A drop of blood landing. */
    public static Voice[] seraphPlip(int i) {
        float hz = 700f * (float) Math.pow(2, (i * 5 % 12) / 12.0);
        return new Voice[]{tone(hz, hz * 1.8f, 0.05f, 0.25f, 0.001f, new float[]{1f, 0.25f}, 0.5f)};
    }

    /** The espresso: a hiss of steam, a gurgling pour, a little ding. */
    public static Voice[] seraphEspresso(long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(noise(5200f, 0.8f, 1.6f, 0.06f, 0.2f, 0.6f, seed));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 9; i++) {
            float at = 0.6f + i * 0.09f + rng.nextFloat() * 0.03f;
            float hz = 300f + rng.nextFloat() * 300f;
            out.add(after(at, tone(hz, hz * 1.6f, 0.06f, 0.14f, 0.002f, new float[]{1f, 0.3f}, 0.4f)));
        }
        out.add(after(1.6f, tone(1760f, 1760f, 0.6f, 0.09f, 0.001f, new float[]{1f, 0.4f}, 0.85f)));
        return out.toArray(new Voice[0]);
    }

    /** The head popping off, then thudding down; the skin spreading over it, a rising shimmer to a chime. */
    public static Voice[] seraphHeadOff(long seed) {
        return new Voice[]{tone(420f, 900f, 0.08f, 0.25f, 0.002f, new float[]{1f, 0.3f}, 0.4f), noise(1500f, 1.2f, 0.08f, 0.08f, 0.001f, 0.4f, seed)};
    }

    public static Voice[] seraphSpread(float seconds, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(tone(110f, 60f, 0.3f, 0.35f, 0.002f, new float[]{1f, 0.4f}, 0.4f));
        out.add(noise(400f, 0.9f, 0.18f, 0.18f, 0.001f, 0.4f, seed));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 28; i++) {
            float at = 0.2f + seconds * i / 28f;
            float hz = 1200f + i * 60f + rng.nextFloat() * 200f;
            out.add(after(at, tone(hz, hz, 0.08f, 0.03f, 0.001f, new float[]{1f}, 0.85f)));
        }
        return out.toArray(new Voice[0]);
    }

    /** Every voice at {@code gain}: a cue list's level, set where it is built (pure - the offline tools use it too). */
    public static Voice[] gained(Voice[] voices, float gain) {
        Voice[] out = new Voice[voices.length];
        for (int i = 0; i < voices.length; i++) {
            Voice inner = voices[i];
            out[i] = (left, right, send, frames, rate) -> {
                float[] l = new float[frames], r = new float[frames], s2 = new float[frames];
                boolean alive = inner.render(l, r, s2, frames, rate);
                for (int k = 0; k < frames; k++) {
                    left[k] += l[k] * gain;
                    right[k] += r[k] * gain;
                    send[k] += s2[k] * gain;
                }
                return alive;
            };
        }
        return out;
    }

    /** Wisp: the Ghost rising out of the Mist - a hollow falling wail and a breath of air. */
    public static Voice[] ghostWail(long seed) {
        return new Voice[]{
                tone(392f, 261.6f, 0.75f, 0.045f, 0.18f, new float[]{1f, 0f, 0.35f, 0f, 0.12f}, 0.7f),
                tone(588f, 392f, 0.6f, 0.018f, 0.2f, new float[]{1f}, 0.7f),
                noise(1400f, 0.6f, 0.55f, 0.03f, 0.2f, 0.6f, seed)};
    }

    /** Wisp: it comes apart - a crackle of the charge and a soft puff. */
    public static Voice[] ghostBurst(long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) for (Voice v : eruptionZap(seed + i)) out.add(after(0.035f * i, v));
        out.add(noise(2600f, 0.8f, 0.3f, 0.06f, 0.002f, 0.5f, seed + 9));
        return out.toArray(new Voice[0]);
    }

    /** Time stuttering: a short tape stop. */
    public static Voice[] seraphStutter() {
        return new Voice[]{new Shaped(330f, 90f, 0.18f, 0.22f, Shaped.PAD, new float[]{1f, 0.6f, 0.4f}, 0.5f, 0f)};
    }

    /** The afterimages falling in: a breath sucked back, then the shock - a boom and a crash of shimmer. */
    public static Voice[] seraphImplode(float seconds) {
        float[] rich = {1f, 0.5f, 0.3f};
        return new Voice[]{
                new Shaped(138.59f, 554.37f, seconds, 0.14f, Shaped.REVERSE, rich, 0.6f, 0f),
                noise(1500f, 0.8f, seconds, 0.08f, seconds * 0.95f, 0.6f, 7320L),
        };
    }

    public static Voice[] seraphShock(long seed) {
        return new Voice[]{
                tone(52f, 26f, 1.3f, 0.48f, 0.002f, new float[]{1f, 0.5f, 0.25f}, 0.7f),
                noise(200f, 0.8f, 0.6f, 0.26f, 0.001f, 0.6f, seed),
                noise(5200f, 1f, 0.7f, 0.13f, 0.002f, 0.85f, seed + 1),
                new Shaped(277.18f, 1108.73f, 0.35f, 0.06f, Shaped.RISE, new float[]{1f, 0.3f}, 0.8f, 0f),
        };
    }

    /** The End stone breaking up and drifting: a long low rumble. */
    public static Voice[] seraphRumble(float seconds, long seed) {
        return new Voice[]{noise(90f, 0.7f, seconds, 0.12f, seconds * 0.2f, 0.5f, seed), noise(240f, 0.8f, seconds, 0.05f, seconds * 0.3f, 0.5f, seed + 1)};
    }

    /** The judgement's stillness: everything gone but one high ringing note. */
    public static Voice[] seraphStill(float seconds) {
        return new Voice[]{swell(2217.46f, seconds, 0.025f, new float[]{1f}), swell(69.30f, seconds, 0.08f, new float[]{1f, 0.3f})};
    }

    /** Glass cracking: a run of sharp clicks, thinning out. */
    public static Voice[] seraphCrack(float seconds, float amp, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        java.util.Random rng = new java.util.Random(seed);
        int n = Math.round(seconds * 40f);
        for (int i = 0; i < n; i++) {
            float at = seconds * (float) Math.pow(i / (float) n, 0.8);
            out.add(after(at, noise(3000f + rng.nextFloat() * 4000f, 3f, 0.02f, amp * (0.6f + 0.4f * rng.nextFloat()), 0.0005f, 0.6f, seed + i)));
        }
        out.add(noise(1200f, 0.9f, seconds, amp * 0.5f, seconds * 0.6f, 0.7f, seed + 999));
        return out.toArray(new Voice[0]);
    }

    /**
     * The judgement falling: the deepest boom, a dragon's roar dropping away, and a choir opening on C#
     * major that holds while it dissolves and the core comes down.
     */
    public static Voice[] seraphJudgement(float choirSeconds, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(tone(36.71f, 20f, 2.2f, 0.6f, 0.004f, new float[]{1f, 0.6f, 0.3f}, 0.75f));
        out.add(noise(140f, 0.6f, 1.6f, 0.32f, 0.01f, 0.7f, seed));
        out.add(noise(2600f, 0.9f, 0.5f, 0.16f, 0.002f, 0.8f, seed + 2));
        out.add(tone(69.30f, 69.30f, 3.5f, 0.28f, 0.004f, new float[]{1f, 0f, 0.5f, 0f, 0.3f}, 0.9f));
        out.add(after(0.15f, new Shaped(220f, 70f, 1.6f, 0.12f, Shaped.PAD, new float[]{1f, 0.8f, 0.7f, 0.6f, 0.5f, 0.4f}, 0.6f, 6f)));
        out.add(after(0.15f, noise(500f, 1.5f, 1.4f, 0.10f, 0.1f, 0.6f, seed + 1)));
        float[] soft = {1f, 0.4f, 0.2f, 0.1f};
        for (float hz : new float[]{138.59f, 174.61f, 207.65f, 277.18f, 349.23f, 415.30f}) {
            out.add(after(0.25f, new Shaped(hz, hz, choirSeconds, 0.07f, Shaped.PAD, soft, 0.92f, 5f)));
        }
        return out.toArray(new Voice[0]);
    }

    // ---- the Eye (the Judgement Core) and the Dragon (the Byzantium Dye)

    /** Everything stopping: a tape winding down to nothing and a thin ringing left in the silence. */
    public static Voice[] seraphFreeze(long seed) {
        float[] rich = {1f, 0.6f, 0.45f, 0.3f, 0.2f};
        return new Voice[]{
                new Shaped(330f, 26f, 0.6f, 0.28f, Shaped.PAD, rich, 0.5f, 0f),
                new Shaped(220f, 18f, 0.6f, 0.16f, Shaped.PAD, rich, 0.5f, 0f),
                after(0.3f, tone(2217.46f, 2217.46f, 2.2f, 0.03f, 0.2f, new float[]{1f}, 0.95f)),
        };
    }

    /** The core alive inside the stopped world: a low throb that keeps on. */
    public static Voice[] seraphCoreHum(float seconds) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(swell(55f, seconds, 0.14f, new float[]{1f, 0.5f, 0.2f}));
        out.add(swell(82.41f, seconds, 0.05f, new float[]{1f, 0.3f}));
        for (float at = 0.1f; at < seconds - 0.2f; at += 0.62f) {
            out.add(after(at, tone(62f, 48f, 0.3f, 0.16f, 0.02f, new float[]{1f, 0.4f}, 0.5f)));
        }
        return out.toArray(new Voice[0]);
    }

    /** The lids parting: a low creak and a wet unseal. */
    public static Voice[] seraphEyeOpen(long seed) {
        return new Voice[]{
                new Shaped(90f, 140f, 0.32f, 0.14f, Shaped.RISE, new float[]{1f, 0.7f, 0.5f, 0.4f}, 0.4f, 13f),
                after(0.12f, tone(260f, 520f, 0.08f, 0.2f, 0.003f, new float[]{1f, 0.35f}, 0.4f)),
                noise(700f, 1.4f, 0.25f, 0.07f, 0.08f, 0.4f, seed),
        };
    }

    /** The eye flicking to one side: a short low swish. */
    public static Voice[] seraphGlance(long seed) {
        return new Voice[]{noise(420f, 1.1f, 0.16f, 0.12f, 0.03f, 0.4f, seed), tone(110f, 95f, 0.15f, 0.1f, 0.01f, new float[]{1f, 0.4f}, 0.4f)};
    }

    /** The stare's heavy pulse: a deep heartbeat-boom felt more than heard. */
    public static Voice[] seraphHeartboom(long seed) {
        return new Voice[]{
                tone(46f, 24f, 1.2f, 0.62f, 0.002f, new float[]{1f, 0.6f, 0.3f}, 0.75f),
                noise(120f, 0.7f, 0.7f, 0.3f, 0.002f, 0.7f, seed),
                after(0.24f, tone(42f, 24f, 0.9f, 0.4f, 0.004f, new float[]{1f, 0.5f}, 0.75f)),
                tone(1108.73f, 900f, 0.8f, 0.04f, 0.01f, new float[]{1f}, 0.9f),
        };
    }

    /**
     * The dragon's roar: a growl of rich low harmonics, wavering, over a rough breath of noise. {@code rising}
     * swells it in (the roar growing through the stopped world), else it is struck at once.
     */
    public static Voice[] seraphRoar(float seconds, boolean rising, float amp, long seed) {
        float[] growl = {1f, 0.85f, 0.7f, 0.6f, 0.5f, 0.42f, 0.35f, 0.3f};
        int shape = rising ? Shaped.RISE : Shaped.PAD;
        return new Voice[]{
                new Shaped(78f, 62f, seconds, 0.22f * amp, shape, growl, 0.6f, 6.5f),
                new Shaped(117f, 92f, seconds, 0.10f * amp, shape, growl, 0.6f, 8f),
                noise(380f, 1.3f, seconds, 0.12f * amp, rising ? seconds * 0.9f : seconds * 0.15f, 0.6f, seed),
                noise(1500f, 1f, seconds, 0.05f * amp, rising ? seconds * 0.9f : seconds * 0.2f, 0.6f, seed + 1),
        };
    }

    /** A wingbeat: a heavy downward whump of air. */
    public static Voice[] seraphFlap(float amp, long seed) {
        return new Voice[]{noise(240f, 0.8f, 0.32f, 0.18f * amp, 0.06f, 0.5f, seed), noise(900f, 1f, 0.22f, 0.05f * amp, 0.05f, 0.5f, seed + 1)};
    }

    /** Landing: the ground taking its weight - a slam, a rumble, the frozen Seraph crumbling. */
    public static Voice[] seraphLand(long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(tone(48f, 24f, 1.1f, 0.55f, 0.002f, new float[]{1f, 0.6f, 0.3f}, 0.7f));
        out.add(noise(150f, 0.7f, 0.9f, 0.32f, 0.002f, 0.6f, seed));
        out.add(noise(90f, 0.6f, 1.6f, 0.14f, 0.2f, 0.5f, seed + 1));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 16; i++) {
            float at = 0.15f + 0.6f * (float) Math.pow(i / 16f, 1.3f);
            out.add(after(at, noise(1600f + rng.nextFloat() * 2500f, 2.2f, 0.04f, 0.07f, 0.001f, 0.3f, seed + 10 + i)));
        }
        return out.toArray(new Voice[0]);
    }

    /** The breath: a roaring hiss of dragon's breath, sizzling, for as long as it pours. */
    public static Voice[] seraphBreath(float seconds, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(noise(1700f, 0.7f, seconds, 0.16f, 0.15f, 0.7f, seed));
        out.add(noise(4200f, 0.9f, seconds, 0.06f, 0.2f, 0.7f, seed + 1));
        out.add(new Shaped(98f, 92f, seconds, 0.08f, Shaped.PAD, new float[]{1f, 0.8f, 0.6f, 0.5f}, 0.6f, 7f));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 30; i++) {
            float at = rng.nextFloat() * seconds * 0.95f;
            out.add(after(at, noise(3000f + rng.nextFloat() * 3000f, 3f, 0.03f, 0.05f, 0.001f, 0.4f, seed + 5 + i)));
        }
        return out.toArray(new Voice[0]);
    }

    /** The paint pooling: thick glugs, lower as it spreads. */
    public static Voice[] seraphGlug(int i) {
        float hz = 260f - i * 12f;
        return new Voice[]{tone(hz, hz * 1.7f, 0.09f, 0.22f, 0.004f, new float[]{1f, 0.4f, 0.15f}, 0.5f)};
    }

    // ================================================================== Hellion (the blaze slayer)
    //
    // Its own sound, not the Chopping Block's: fire, steel on a shield, glass, bone dice. Everything
    // pitched sits in E phrygian (E F G A B C D) - the Demonlord's dark mode - with the win's swell
    // turning to E major (G sharp) as the drop comes out.

    /** The roots of the four attunements' rings: Ashen a dead low E, Spirit B, Auric G, Crystal high E. */
    private static final float[] HELLION_RING = {164.81f, 493.88f, 392f, 659.25f};

    /** The scene opening: the blaze catching with a deep breath, the pillars grinding up out of the ground. */
    public static Voice[] hellionOpen(long seed) {
        return new Voice[]{
                noise(190f, 0.7f, 1.1f, 0.22f, 0.18f, 0.6f, seed),
                tone(55f, 41.2f, 1.0f, 0.30f, 0.05f, new float[]{1f, 0.5f, 0.25f}, 0.6f),
                after(0.15f, noise(130f, 0.9f, 0.8f, 0.15f, 0.35f, 0.4f, seed + 1)),
                after(0.2f, noise(620f, 1.3f, 1.3f, 0.06f, 0.6f, 0.7f, seed + 2)),
                new Shaped(82.41f, 77.78f, 1.4f, 0.10f, Shaped.PAD, new float[]{1f, 0.6f, 0.4f, 0.3f, 0.2f}, 0.6f, 5f),
        };
    }

    /**
     * The fire under the whole build-up: a low roar and a hiss that keep growing, and crackles that come
     * thicker and louder as the shield speeds up. Cut dead at the reveal.
     */
    public static Voice[] hellionFire(float seconds, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(noise(240f, 0.6f, seconds, 0.11f, seconds * 0.85f, 0.5f, seed));
        out.add(noise(3600f, 0.8f, seconds, 0.025f, seconds * 0.9f, 0.7f, seed + 1));
        java.util.Random rng = new java.util.Random(seed);
        int n = 90;
        for (int i = 0; i < n; i++) {
            float u = (float) Math.pow((i + rng.nextFloat()) / n, 0.65);
            float at = u * seconds * 0.98f;
            float amp = 0.03f + 0.09f * u * (0.4f + 0.6f * rng.nextFloat());
            out.add(after(at, noise(1800f + rng.nextFloat() * 4200f, 2.5f, 0.012f + rng.nextFloat() * 0.02f, amp, 0.0005f, 0.35f, seed + 10 + i)));
        }
        return out.toArray(new Voice[0]);
    }

    /** A ring of rods sweeping past the front: a short whup, higher, shorter and louder the faster they go. */
    public static Voice[] hellionRodPass(float speed, long seed) {
        float k = Math.max(0f, Math.min(1f, speed));
        return new Voice[]{
                noise(650f + 1100f * k, 1.3f, 0.13f - 0.06f * k, 0.035f + 0.07f * k, 0.04f - 0.02f * k, 0.3f, seed),
        };
    }

    /**
     * A dagger striking the shield: the thwack of the blade, then the shield ringing in the attunement's
     * own voice - Ashen a dead thunk, Spirit a pale glass bell, Auric a warm gold bell, Crystal an icy
     * chime. A semitone higher each round and harder each step. The cycle is the same on every run, so
     * these tell nothing.
     */
    public static Voice[] hellionClang(int attunement, int round, float progress, long seed) {
        float hz = HELLION_RING[attunement] * (float) Math.pow(2, round / 12.0);
        float k = 0.7f + 0.4f * progress;
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(noise(4200f, 1.6f, 0.03f, 0.20f * k, 0.0005f, 0.3f, seed));
        out.add(noise(950f, 1f, 0.06f, 0.15f * k, 0.001f, 0.3f, seed + 1));
        switch (attunement) {
            case 0 -> {
                out.add(tone(hz, hz * 0.93f, 0.22f, 0.30f * k, 0.001f, new float[]{1f, 0.3f, 0.1f}, 0.4f));
                out.add(noise(300f, 0.8f, 0.16f, 0.16f * k, 0.001f, 0.4f, seed + 2));
            }
            case 1 -> {
                out.add(tone(hz, hz, 0.75f, 0.13f * k, 0.001f, new float[]{1f, 0f, 0.25f}, 0.85f));
                out.add(tone(hz * 2.76f, hz * 2.76f, 0.45f, 0.05f * k, 0.001f, new float[]{1f}, 0.85f));
            }
            case 2 -> {
                out.add(tone(hz, hz, 0.95f, 0.15f * k, 0.001f, new float[]{1f, 0.5f, 0.3f, 0.15f}, 0.85f));
                out.add(tone(hz * 2.01f, hz * 2.01f, 0.6f, 0.05f * k, 0.001f, new float[]{1f}, 0.85f));
            }
            default -> {
                out.add(tone(hz, hz, 0.6f, 0.11f * k, 0.001f, new float[]{1f, 0f, 0f, 0.3f}, 0.9f));
                out.add(tone(hz * 2.76f, hz * 2.76f, 0.4f, 0.05f * k, 0.001f, new float[]{1f}, 0.9f));
                out.add(tone(hz * 5.4f, hz * 5.4f, 0.25f, 0.025f * k, 0.001f, new float[]{1f}, 0.9f));
            }
        }
        return out.toArray(new Voice[0]);
    }

    /** The hang: the shield flickering between colours - a tiny glassy tick on each flicker. */
    public static Voice[] hellionFlicker(int i) {
        float hz = i % 2 == 0 ? 2637f : 3136f;
        return new Voice[]{tone(hz, hz, 0.04f, 0.05f, 0.0005f, new float[]{1f, 0.2f}, 0.6f)};
    }

    /**
     * The last stretch before the answer: the fire and a low chord rising together, E and B with the
     * phrygian F rubbing against them, cut dead at the reveal.
     */
    public static Voice[] hellionBuild(float seconds) {
        float[] rich = {1f, 0.5f, 0.3f, 0.18f, 0.1f};
        return new Voice[]{
                new Shaped(82.41f, 164.81f, seconds, 0.26f, Shaped.RISE, rich, 0.6f, 0f),
                new Shaped(123.47f, 246.94f, seconds, 0.17f, Shaped.RISE, rich, 0.6f, 0f),
                new Shaped(87.31f, 174.61f, seconds, 0.08f, Shaped.RISE, rich, 0.6f, 0f),
                noise(1800f, 1f, seconds, 0.07f, seconds * 0.95f, 0.8f, 7101L),
                noise(400f, 0.7f, seconds, 0.11f, seconds * 0.95f, 0.6f, 7102L),
        };
    }

    /**
     * A bust: the fire goes out - a hiss of steam and a dull thud, the breath falling away under it -
     * then the rods clattering down and the head crumbling to ash.
     */
    public static Voice[] hellionSnuff(long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(noise(5200f, 0.9f, 0.9f, 0.17f, 0.002f, 0.5f, seed));
        out.add(noise(2400f, 0.8f, 0.6f, 0.12f, 0.002f, 0.5f, seed + 1));
        out.add(tone(78f, 36f, 0.7f, 0.45f, 0.002f, new float[]{1f, 0.5f, 0.2f}, 0.6f));
        out.add(new Shaped(164.81f, 82.41f, 0.9f, 0.12f, Shaped.PAD, new float[]{1f, 0.4f, 0.2f}, 0.7f, 0f));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 9; i++) {
            float at = 0.22f + i * 0.06f + rng.nextFloat() * 0.04f;
            float hz = 1100f + rng.nextFloat() * 900f;
            out.add(after(at, tone(hz, hz * 0.97f, 0.09f, 0.10f - 0.006f * i, 0.001f, new float[]{1f, 0.4f, 0.2f}, 0.3f)));
            out.add(after(at, noise(2600f, 2f, 0.03f, 0.06f, 0.0008f, 0.3f, seed + 10 + i)));
        }
        out.add(after(0.3f, noise(700f, 0.8f, 0.5f, 0.07f, 0.05f, 0.4f, seed + 2)));
        for (int i = 0; i < 10; i++) {
            float at = 0.35f + 0.5f * (float) Math.pow(i / 10f, 1.4f);
            out.add(after(at, noise(1800f + rng.nextFloat() * 2000f, 2.2f, 0.035f, 0.05f * (1f - 0.5f * i / 10f), 0.001f, 0.3f, seed + 30 + i)));
        }
        return out.toArray(new Voice[0]);
    }

    /**
     * A win: the shield shatters - a boom, the glass crack and a scatter of shards tinkling down, the
     * fire blown outwards - and the colour it landed on rings on long.
     */
    public static Voice[] hellionShatter(int attunement, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(tone(62f, 30f, 1.0f, 0.42f, 0.002f, new float[]{1f, 0.5f, 0.25f}, 0.7f));
        out.add(noise(240f, 0.8f, 0.5f, 0.26f, 0.001f, 0.6f, seed));
        out.add(noise(6500f, 1f, 0.3f, 0.17f, 0.001f, 0.6f, seed + 1));
        out.add(noise(900f, 0.9f, 0.7f, 0.18f, 0.03f, 0.6f, seed + 2));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 22; i++) {
            float at = 0.7f * (float) Math.pow(rng.nextFloat(), 1.6f);
            float hz = 2200f + rng.nextFloat() * 4000f;
            out.add(after(at, tone(hz, hz, 0.06f + rng.nextFloat() * 0.15f, 0.035f + 0.04f * rng.nextFloat(), 0.001f, new float[]{1f, 0.3f}, 0.85f)));
        }
        float hz = HELLION_RING[attunement] * 1.19f;
        out.add(tone(hz, hz, 2.2f, 0.12f, 0.002f, new float[]{1f, 0.4f, 0.2f}, 0.9f));
        out.add(tone(hz * 2.76f, hz * 2.76f, 1.3f, 0.04f, 0.002f, new float[]{1f}, 0.9f));
        return out.toArray(new Voice[0]);
    }

    /** A win, after the shatter: the fire roaring back up and a chord rising into E major as the drop comes. */
    public static Voice[] hellionRise(float seconds) {
        float[] soft = {1f, 0.45f, 0.25f, 0.12f};
        return new Voice[]{
                new Shaped(164.81f, 329.63f, seconds, 0.15f, Shaped.RISE, soft, 0.85f, 0f),
                new Shaped(246.94f, 493.88f, seconds, 0.10f, Shaped.RISE, soft, 0.85f, 0f),
                new Shaped(207.65f, 415.30f, seconds, 0.07f, Shaped.RISE, soft, 0.85f, 0f),
                noise(800f, 0.7f, seconds, 0.08f, seconds * 0.9f, 0.6f, 7110L),
        };
    }

    /**
     * The drop shown: a low gong, an E major brass chord and bells. The jackpots (the High Class Dice,
     * the Flame Dye) get a second, higher chord and a shower of coins.
     */
    public static Voice[] hellionArrive(boolean jackpot, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        float[] brass = {1f, 0.8f, 0.65f, 0.5f, 0.4f, 0.3f, 0.2f};
        out.add(tone(82.41f, 82.41f, 2.6f, 0.30f, 0.004f, new float[]{1f, 0f, 0.5f, 0f, 0.3f}, 0.85f));
        out.add(tone(82.41f * 2.76f, 82.41f * 2.76f, 1.8f, 0.07f, 0.004f, new float[]{1f}, 0.85f));
        for (float hz : new float[]{164.81f, 207.65f, 246.94f, 329.63f}) {
            out.add(tone(hz, hz, 1.2f, 0.10f, 0.02f, brass, 0.7f));
        }
        for (Voice v : chime(5, 659.25f)) out.add(after(0.1f, v));
        out.add(noise(9000f, 3f, 2.0f, 0.05f, 0.05f, 0.97f, seed));
        if (jackpot) {
            for (float hz : new float[]{220f, 277.18f, 329.63f, 440f}) {
                out.add(after(0.38f, tone(hz, hz, 1.4f, 0.10f, 0.02f, brass, 0.75f)));
            }
            java.util.Random rng = new java.util.Random(seed);
            for (int i = 0; i < 40; i++) {
                float at = 0.4f + 1.6f * rng.nextFloat();
                float hz = 3000f + rng.nextFloat() * 3000f;
                out.add(after(at, tone(hz, hz, 0.05f + 0.06f * rng.nextFloat(), 0.04f, 0.0005f, new float[]{1f, 0.3f}, 0.7f)));
            }
        }
        return out.toArray(new Voice[0]);
    }

    /**
     * Under the drop while it is on show: an E major chord held and breathing out, and the fire settled
     * to a low murmur - so the showcase is not silent once the gong has died away.
     */
    public static Voice[] hellionGlow(float seconds, long seed) {
        float[] soft = {1f, 0.35f, 0.15f};
        java.util.List<Voice> out = new java.util.ArrayList<>();
        for (float hz : new float[]{164.81f, 207.65f, 246.94f, 329.63f}) {
            out.add(new Shaped(hz, hz, seconds, 0.05f, Shaped.PAD, soft, 0.9f, 4.5f));
        }
        out.add(noise(260f, 0.6f, seconds, 0.05f, seconds * 0.3f, 0.6f, seed));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 14; i++) {
            float at = rng.nextFloat() * seconds * 0.85f;
            out.add(after(at, noise(2200f + rng.nextFloat() * 3000f, 2.5f, 0.015f, 0.03f + 0.02f * rng.nextFloat(), 0.0005f, 0.4f, seed + 1 + i)));
        }
        return out.toArray(new Voice[0]);
    }

    /** The die thrown out of the core: a quick rising whoosh. */
    public static Voice[] hellionThrow(long seed) {
        return new Voice[]{
                noise(1300f, 1.2f, 0.25f, 0.10f, 0.18f, 0.3f, seed),
                noise(3200f, 2f, 0.2f, 0.03f, 0.15f, 0.3f, seed + 1),
        };
    }

    /**
     * The die on the floor: a bone clack and a hollow knock, with a thud under it when it lands hard.
     * {@code hard} 1 for the landing, ~0.45 for tipping flat, ~0.25 for rolling over an edge.
     */
    public static Voice[] hellionDieHit(float hard, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        float k = 0.45f + 0.55f * hard;
        out.add(noise(3400f, 3f, 0.025f, 0.22f * k, 0.0005f, 0.35f, seed));
        out.add(tone(1180f, 1100f, 0.07f, 0.16f * k, 0.0008f, new float[]{1f, 0.6f, 0.2f}, 0.35f));
        out.add(tone(620f, 600f, 0.05f, 0.08f * k, 0.0008f, new float[]{1f, 0.3f}, 0.35f));
        if (hard > 0.4f) {
            out.add(tone(120f, 70f, 0.22f, 0.40f * hard, 0.002f, new float[]{1f, 0.4f}, 0.4f));
            out.add(noise(300f, 0.8f, 0.15f, 0.20f * hard, 0.001f, 0.4f, seed + 1));
        }
        return out.toArray(new Voice[0]);
    }

    /** The Flame Dye: time stopping - the fire and everything with it winding down like a tape, and ice. */
    public static Voice[] hellionFreeze(long seed) {
        float[] rich = {1f, 0.6f, 0.45f, 0.3f, 0.2f};
        return new Voice[]{
                new Shaped(330f, 28f, 0.55f, 0.30f, Shaped.PAD, rich, 0.5f, 0f),
                new Shaped(220f, 19f, 0.55f, 0.18f, Shaped.PAD, rich, 0.5f, 0f),
                noise(320f, 0.8f, 0.3f, 0.20f, 0.004f, 0.5f, seed),
                tone(2637f, 2637f, 1.4f, 0.05f, 0.02f, new float[]{1f}, 0.95f),
                tone(3951f, 3951f, 1.2f, 0.03f, 0.03f, new float[]{1f}, 0.95f),
        };
    }

    /** Time stopped: a low hum and one thin high tone. The heartbeats are their own cues (HEARTS). */
    public static Voice[] hellionStill(float seconds) {
        return new Voice[]{
                swell(41.2f, seconds, 0.16f, new float[]{1f, 0.3f}),
                swell(2637f, seconds, 0.018f, new float[]{1f}),
        };
    }

    /** A heartbeat in stopped time, a little harder each one. */
    public static Voice[] hellionHeart(int k) {
        float amp = 0.30f + 0.09f * k;
        return new Voice[]{
                tone(58f, 42f, 0.2f, amp, 0.004f, new float[]{1f, 0.3f}, 0.45f),
                after(0.17f, tone(55f, 40f, 0.18f, amp * 0.7f, 0.004f, new float[]{1f, 0.3f}, 0.45f)),
                noise(180f, 0.7f, 0.15f, amp * 0.4f, 0.003f, 0.4f, 7200L + k),
        };
    }

    /** The cold being pulled into the ember before a beat: a breath sucked in. */
    public static Voice[] hellionPull(float seconds) {
        float[] rich = {1f, 0.5f, 0.3f};
        return new Voice[]{
                new Shaped(110f, 220f, seconds, 0.12f, Shaped.REVERSE, rich, 0.6f, 0f),
                noise(1500f, 0.8f, seconds, 0.06f, seconds * 0.95f, 0.6f, 7210L),
        };
    }

    /**
     * The Flame Dye's supernova, under the ignition: the deepest boom of the lot, a long roar, a hiss of
     * sparks - and a choir opening on A major, the dye's own key turning bright.
     */
    public static Voice[] hellionSupernova(long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(after(0.1f, tone(41.2f, 22f, 2.0f, 0.5f, 0.004f, new float[]{1f, 0.6f, 0.3f}, 0.75f)));
        out.add(after(0.1f, noise(150f, 0.6f, 1.6f, 0.28f, 0.01f, 0.7f, seed)));
        out.add(after(0.15f, noise(4200f, 0.8f, 1.3f, 0.12f, 0.005f, 0.8f, seed + 1)));
        float[] soft = {1f, 0.4f, 0.2f, 0.1f};
        for (float hz : new float[]{220f, 277.18f, 329.63f, 440f, 554.37f}) {
            out.add(after(0.2f, new Shaped(hz, hz, 3.2f, 0.065f, Shaped.PAD, soft, 0.92f, 5f)));
        }
        return out.toArray(new Voice[0]);
    }

    /**
     * The High Class Dice's bullet time: everything drops away down a tape, leaving a low hum, a thin
     * high tone holding the tension and the player's heartbeat, twice.
     */
    public static Voice[] hellionSlow(float seconds) {
        float[] rich = {1f, 0.6f, 0.45f, 0.3f, 0.2f};
        return new Voice[]{
                new Shaped(220f, 50f, 0.45f, 0.24f, Shaped.PAD, rich, 0.5f, 0f),
                swell(41.2f, seconds, 0.17f, new float[]{1f, 0.3f}),
                swell(1318.5f, seconds, 0.03f, new float[]{1f}),
                noise(300f, 0.7f, seconds, 0.05f, seconds * 0.5f, 0.6f, 7220L),
                after(0.25f, tone(58f, 42f, 0.2f, 0.34f, 0.004f, new float[]{1f, 0.3f}, 0.45f)),
                after(0.42f, tone(55f, 40f, 0.18f, 0.24f, 0.004f, new float[]{1f, 0.3f}, 0.45f)),
                after(0.95f, tone(58f, 42f, 0.2f, 0.38f, 0.004f, new float[]{1f, 0.3f}, 0.45f)),
                after(1.12f, tone(55f, 40f, 0.18f, 0.27f, 0.004f, new float[]{1f, 0.3f}, 0.45f)),
        };
    }

    /** A pip lighting gold: a bell climbing E major, E G# B up two octaves, with a coin's clink on it. */
    public static Voice[] hellionPip(int i) {
        int[] steps = {0, 4, 7, 12, 16, 19};
        float hz = 659.25f * (float) Math.pow(2, steps[Math.max(0, Math.min(5, i))] / 12.0);
        return new Voice[]{
                tone(hz, hz, 0.55f, 0.15f + 0.02f * i, 0.001f, new float[]{1f, 0.35f, 0.12f}, 0.85f),
                tone(hz * 2.76f, hz * 2.76f, 0.25f, 0.04f, 0.001f, new float[]{1f}, 0.85f),
                noise(6000f, 3f, 0.03f, 0.06f, 0.0005f, 0.4f, 7230L + i),
        };
    }

    /** The jackpot: a boom, a crash of shimmer and a rush upwards as everything goes off at once. */
    public static Voice[] hellionJackpot(long seed) {
        return new Voice[]{
                tone(55f, 28f, 1.2f, 0.48f, 0.002f, new float[]{1f, 0.5f, 0.25f}, 0.7f),
                noise(200f, 0.8f, 0.6f, 0.28f, 0.001f, 0.6f, seed),
                noise(5200f, 1f, 0.6f, 0.14f, 0.002f, 0.85f, seed + 1),
                new Shaped(330f, 1320f, 0.3f, 0.06f, Shaped.RISE, new float[]{1f, 0.3f}, 0.8f, 0f),
                noise(1200f, 1f, 0.35f, 0.08f, 0.25f, 0.5f, seed + 2),
        };
    }

    /** The Flame Dye catching: a match struck, then the whole scene going up - a rushing whoosh and a boom. */
    public static Voice[] hellionIgnite(long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(noise(3200f, 2.2f, 0.05f, 0.25f, 0.0008f, 0.4f, seed));
        out.add(noise(1500f, 1.2f, 0.18f, 0.15f, 0.002f, 0.4f, seed + 1));
        for (int i = 0; i < 6; i++) {
            out.add(after(0.06f + i * 0.05f, noise(400f * (float) Math.pow(1.55, i), 0.9f, 0.55f, 0.09f, 0.08f, 0.6f, seed + 2 + i)));
        }
        out.add(after(0.12f, tone(58f, 30f, 1.1f, 0.55f, 0.003f, new float[]{1f, 0.5f, 0.25f}, 0.7f)));
        out.add(after(0.12f, noise(260f, 0.6f, 2.5f, 0.14f, 0.15f, 0.6f, seed + 10)));
        return out.toArray(new Voice[0]);
    }

    // ================================================================== Eruption (lava fishing)

    /** D, the root everything in Eruption rings on: D phrygian through the build-up, D major for a win. */
    private static final float ERUPT_D = 73.42f;

    /** The creature going under: a heavy gloop sliding down, the lava closing over it with a slurp. */
    public static Voice[] eruptionSink(long seed) {
        return new Voice[]{
                tone(110f, 46f, 1.1f, 0.34f, 0.01f, new float[]{1f, 0.5f, 0.2f}, 0.6f),
                noise(260f, 0.9f, 1.2f, 0.16f, 0.05f, 0.6f, seed),
                after(0.5f, noise(520f, 1.6f, 0.5f, 0.10f, 0.08f, 0.5f, seed + 1)),
                after(0.9f, tone(180f, 90f, 0.35f, 0.14f, 0.004f, new float[]{1f, 0.3f}, 0.5f)),
        };
    }

    /** The crust closing in from the shore: rock clicking and creaking as it sets, thickening. */
    public static Voice[] eruptionCrust(float seconds, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(noise(180f, 0.7f, seconds, 0.07f, seconds * 0.6f, 0.6f, seed));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 34; i++) {
            float u = (i + rng.nextFloat()) / 34f;
            out.add(after(u * seconds, noise(1400f + rng.nextFloat() * 2600f, 2.4f, 0.02f, 0.04f + 0.05f * u, 0.0005f, 0.35f, seed + 2 + i)));
        }
        return out.toArray(new Voice[0]);
    }

    /** The vent grinding up through the crust and bulging into place. */
    public static Voice[] eruptionVent(long seed) {
        return new Voice[]{
                noise(150f, 0.8f, 0.7f, 0.20f, 0.3f, 0.5f, seed),
                noise(900f, 1.2f, 0.6f, 0.07f, 0.35f, 0.5f, seed + 1),
                after(0.55f, tone(70f, 40f, 0.5f, 0.38f, 0.003f, new float[]{1f, 0.5f, 0.2f}, 0.6f)),
                after(0.55f, noise(300f, 0.8f, 0.3f, 0.18f, 0.002f, 0.5f, seed + 2)),
        };
    }

    /** Under the whole build-up: the lake's low rumble, growing, and a hiss that grows with it. Cut at the reveal. */
    public static Voice[] eruptionRumble(float seconds, long seed) {
        return new Voice[]{
                noise(90f, 0.6f, seconds, 0.20f, seconds * 0.8f, 0.6f, seed),
                swell(ERUPT_D / 2f, seconds, 0.12f, new float[]{1f, 0.4f}),
                noise(3000f, 0.8f, seconds, 0.025f, seconds * 0.9f, 0.6f, seed + 1),
        };
    }

    /**
     * One breath of the vent: a lava bubble popping, pitched by the heat it climbs to (black low and dull, white
     * high and bright), D phrygian. The breathing is the same on every run, so these tell nothing.
     */
    public static Voice[] eruptionBreath(int level, float progress, long seed) {
        int[] steps = {0, 1, 3, 5, 7};
        float hz = ERUPT_D * 2f * (float) Math.pow(2, steps[Math.max(0, Math.min(4, level))] / 12.0);
        float k = 0.7f + 0.4f * progress;
        return new Voice[]{
                tone(hz * 0.7f, hz * 1.35f, 0.12f, 0.20f * k, 0.002f, new float[]{1f, 0.3f}, 0.4f),
                noise(600f + 300f * level, 1.4f, 0.07f, 0.10f * k, 0.001f, 0.4f, seed),
                tone(hz * 2f, hz * 2f, 0.5f, 0.05f * k * (0.4f + 0.15f * level), 0.003f, new float[]{1f, 0.4f, 0.15f}, 0.8f),
        };
    }

    /** The hang: the heat flickering through every level - a tiny sizzle tick each time. */
    public static Voice[] eruptionFlicker(int i) {
        float hz = i % 2 == 0 ? 2349f : 2794f;
        return new Voice[]{tone(hz, hz * 0.97f, 0.035f, 0.045f, 0.0005f, new float[]{1f, 0.3f}, 0.5f),
                noise(5000f, 2f, 0.02f, 0.03f, 0.0005f, 0.3f, 7300L + i)};
    }

    /** The last stretch: D and A with the phrygian E-flat grinding against them, and a rising roar. Cut at the reveal. */
    public static Voice[] eruptionBuild(float seconds) {
        float[] rich = {1f, 0.5f, 0.3f, 0.18f, 0.1f};
        return new Voice[]{
                new Shaped(ERUPT_D, ERUPT_D * 2f, seconds, 0.24f, Shaped.RISE, rich, 0.6f, 0f),
                new Shaped(ERUPT_D * 1.5f, ERUPT_D * 3f, seconds, 0.15f, Shaped.RISE, rich, 0.6f, 0f),
                new Shaped(ERUPT_D * 1.0595f, ERUPT_D * 2.119f, seconds, 0.08f, Shaped.RISE, rich, 0.6f, 0f),
                noise(250f, 0.7f, seconds, 0.14f, seconds * 0.95f, 0.6f, 7310L),
                noise(2200f, 1f, seconds, 0.06f, seconds * 0.95f, 0.8f, 7311L),
        };
    }

    /** A bust: the heat dies - a long hiss of steam, the vent cracking as it cools and slumping, grit settling. */
    public static Voice[] eruptionCool(long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(noise(5200f, 0.9f, 1.3f, 0.15f, 0.003f, 0.5f, seed));
        out.add(noise(2000f, 0.8f, 0.9f, 0.09f, 0.003f, 0.5f, seed + 1));
        out.add(new Shaped(ERUPT_D * 2f, ERUPT_D, 1.0f, 0.12f, Shaped.PAD, new float[]{1f, 0.4f, 0.2f}, 0.7f, 0f));
        out.add(after(0.35f, tone(80f, 38f, 0.6f, 0.36f, 0.003f, new float[]{1f, 0.5f, 0.2f}, 0.6f)));
        out.add(after(0.35f, noise(400f, 0.8f, 0.4f, 0.14f, 0.002f, 0.5f, seed + 2)));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 12; i++) {
            float at = 0.4f + 0.7f * (float) Math.pow(i / 12f, 1.3f);
            out.add(after(at, noise(1500f + rng.nextFloat() * 2500f, 2.2f, 0.03f, 0.05f * (1f - 0.5f * i / 12f), 0.001f, 0.3f, seed + 10 + i)));
        }
        return out.toArray(new Voice[0]);
    }

    /** A bust's vent slumping: a dull stone thud, rubble settling and a last hiss of steam. */
    public static Voice[] eruptionSlump(long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(tone(70f, 42f, 0.5f, 0.3f, 0.004f, new float[]{1f, 0.4f, 0.15f}, 0.6f));
        out.add(noise(300f, 0.9f, 0.35f, 0.14f, 0.003f, 0.5f, seed));
        out.add(after(0.06f, noise(4200f, 0.8f, 0.7f, 0.06f, 0.02f, 0.5f, seed + 1)));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 5; i++)
            out.add(after(0.08f + 0.06f * i + 0.03f * rng.nextFloat(), noise(700f + rng.nextFloat() * 900f, 1.8f, 0.05f, 0.06f, 0.001f, 0.4f, seed + 2 + i)));
        return out.toArray(new Voice[0]);
    }

    /** The guaranteed drop's line coming in under a bust: one small coin of a sound. */
    public static Voice[] eruptionConsolation() {
        return new Voice[]{tone(587.33f, 587.33f, 0.5f, 0.07f, 0.002f, new float[]{1f, 0.3f, 0.1f}, 0.8f),
                after(0.09f, tone(880f, 880f, 0.6f, 0.06f, 0.002f, new float[]{1f, 0.3f}, 0.8f))};
    }

    /**
     * The eruption: the deepest boom, the vent bursting apart, the fountain roaring up and the bombs landing
     * round the screen one after another. Hotter landings are brighter at the top.
     */
    public static Voice[] eruptionBoom(int level, float amp, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(tone(48f, 24f, 1.6f, 0.42f * amp, 0.002f, new float[]{1f, 0.6f, 0.3f}, 0.75f));
        out.add(noise(140f, 0.6f, 1.2f, 0.24f * amp, 0.002f, 0.7f, seed));
        out.add(noise(1100f, 0.9f, 0.5f, 0.16f * amp, 0.002f, 0.6f, seed + 1));
        out.add(noise(2400f + 900f * level, 0.8f, 0.4f, (0.08f + 0.02f * level) * amp, 0.002f, 0.6f, seed + 2));
        // The fountain: a roar that rises and falls back.
        out.add(after(0.1f, noise(420f, 0.7f, 1.6f, 0.14f * amp, 0.25f, 0.6f, seed + 3)));
        // Rock and bombs landing, spread out over a second.
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 14; i++) {
            float at = 0.9f + 0.05f * i + 0.3f * rng.nextFloat();
            out.add(after(at, noise(300f + rng.nextFloat() * 400f, 1f, 0.12f, 0.10f * amp, 0.001f, 0.4f, seed + 10 + i)));
            out.add(after(at, noise(2600f, 2f, 0.05f, 0.05f * amp, 0.0008f, 0.3f, seed + 30 + i)));
        }
        return out.toArray(new Voice[0]);
    }

    /** The quench: water on hot metal - a sharp hiss falling away and a sizzle under it. */
    public static Voice[] eruptionQuench(long seed) {
        return new Voice[]{
                noise(6200f, 0.8f, 0.9f, 0.14f, 0.004f, 0.5f, seed),
                noise(3200f, 1.2f, 0.7f, 0.08f, 0.01f, 0.5f, seed + 1),
                new Shaped(1800f, 600f, 0.6f, 0.03f, Shaped.PAD, new float[]{1f}, 0.6f, 0f),
        };
    }

    /** The drop climbing out of the lava: D major rising under it, and the lava's roar settling. */
    public static Voice[] eruptionRise(float seconds) {
        float[] soft = {1f, 0.45f, 0.25f, 0.12f};
        return new Voice[]{
                new Shaped(ERUPT_D * 2f, ERUPT_D * 4f, seconds, 0.14f, Shaped.RISE, soft, 0.85f, 0f),
                new Shaped(ERUPT_D * 2.52f, ERUPT_D * 5.04f, seconds, 0.09f, Shaped.RISE, soft, 0.85f, 0f),
                new Shaped(ERUPT_D * 3f, ERUPT_D * 6f, seconds, 0.07f, Shaped.RISE, soft, 0.85f, 0f),
                noise(600f, 0.7f, seconds, 0.07f, seconds * 0.9f, 0.6f, 7320L),
        };
    }

    /** The drop shown: a low gong, a D major brass chord and bells; the big ones a second, higher chord. */
    public static Voice[] eruptionArrive(boolean big, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        float[] brass = {1f, 0.8f, 0.65f, 0.5f, 0.4f, 0.3f, 0.2f};
        out.add(tone(ERUPT_D, ERUPT_D, 2.6f, 0.30f, 0.004f, new float[]{1f, 0f, 0.5f, 0f, 0.3f}, 0.85f));
        out.add(tone(ERUPT_D * 2.76f, ERUPT_D * 2.76f, 1.8f, 0.07f, 0.004f, new float[]{1f}, 0.85f));
        for (float hz : new float[]{146.83f, 185f, 220f, 293.66f}) out.add(tone(hz, hz, 1.2f, 0.10f, 0.02f, brass, 0.7f));
        for (Voice v : chime(big ? 5 : 3, 587.33f)) out.add(after(0.1f, v));
        out.add(noise(9000f, 3f, 2.0f, 0.05f, 0.05f, 0.97f, seed));
        if (big) {
            for (float hz : new float[]{220f, 277.18f, 329.63f, 440f}) out.add(after(0.38f, tone(hz, hz, 1.4f, 0.10f, 0.02f, brass, 0.75f)));
        }
        return out.toArray(new Voice[0]);
    }

    /** Under the showcase: D major held and breathing, the lava murmuring and popping now and then. */
    public static Voice[] eruptionGlow(float seconds, long seed) {
        float[] soft = {1f, 0.35f, 0.15f};
        java.util.List<Voice> out = new java.util.ArrayList<>();
        for (float hz : new float[]{146.83f, 185f, 220f, 293.66f}) out.add(new Shaped(hz, hz, seconds, 0.05f, Shaped.PAD, soft, 0.9f, 4.5f));
        out.add(noise(200f, 0.6f, seconds, 0.05f, seconds * 0.3f, 0.6f, seed));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 9; i++) {
            float at = rng.nextFloat() * seconds * 0.85f;
            float hz = 180f + rng.nextFloat() * 160f;
            out.add(after(at, tone(hz * 0.7f, hz * 1.3f, 0.1f, 0.06f, 0.002f, new float[]{1f, 0.3f}, 0.4f)));
        }
        return out.toArray(new Voice[0]);
    }

    /** A bolt into the vent: the crack right on top, the thunder rolling after it. */
    public static Voice[] eruptionThunder(float amp, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(noise(4200f, 0.7f, 0.18f, 0.26f * amp, 0.0005f, 0.6f, seed));
        out.add(noise(1500f, 0.8f, 0.3f, 0.20f * amp, 0.001f, 0.6f, seed + 1));
        out.add(tone(60f, 34f, 1.4f, 0.32f * amp, 0.003f, new float[]{1f, 0.5f, 0.2f}, 0.8f));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 6; i++) {
            out.add(after(0.15f + 0.22f * i + 0.1f * rng.nextFloat(), noise(110f + 60f * rng.nextFloat(), 0.7f, 0.5f, 0.16f * amp * (1f - i / 7f), 0.03f, 0.8f, seed + 2 + i)));
        }
        return out.toArray(new Voice[0]);
    }

    /** Flash I's leftover charge: a short electric zap. */
    public static Voice[] eruptionZap(long seed) {
        java.util.Random rng = new java.util.Random(seed);
        float hz = 900f + rng.nextFloat() * 700f;
        return new Voice[]{noise(5200f, 2.5f, 0.05f, 0.07f, 0.0005f, 0.3f, seed),
                tone(hz, hz * 1.4f, 0.06f, 0.04f, 0.0005f, new float[]{1f, 0f, 0.5f, 0f, 0.3f}, 0.3f)};
    }

    /** The pincer's claw clacking shut: a hard chitin click and a spray of sparks. */
    public static Voice[] eruptionClack(int i, long seed) {
        float k = 0.8f + 0.15f * i;
        return new Voice[]{
                noise(3800f, 3f, 0.025f, 0.26f * k, 0.0005f, 0.3f, seed),
                tone(1450f + 120f * i, 1300f, 0.06f, 0.16f * k, 0.0005f, new float[]{1f, 0.6f, 0.2f}, 0.3f),
                tone(160f, 90f, 0.12f, 0.20f * k, 0.002f, new float[]{1f, 0.3f}, 0.4f),
                after(0.02f, noise(7000f, 2f, 0.2f, 0.05f, 0.001f, 0.5f, seed + 1)),
        };
    }

    /** A hammer blow on the handle at the forge: an anvil's ring, a whole tone up each blow. */
    public static Voice[] eruptionAnvil(int i, long seed) {
        float hz = 587.33f * (float) Math.pow(2, 2 * i / 12.0);
        return new Voice[]{
                noise(3000f, 1.5f, 0.03f, 0.24f, 0.0005f, 0.3f, seed),
                tone(hz, hz, 1.2f, 0.16f, 0.001f, new float[]{1f, 0.3f, 0.15f}, 0.85f),
                tone(hz * 2.76f, hz * 2.76f, 0.7f, 0.06f, 0.001f, new float[]{1f}, 0.85f),
                tone(hz * 5.4f, hz * 5.4f, 0.35f, 0.03f, 0.001f, new float[]{1f}, 0.85f),
                after(0.01f, noise(6000f, 2f, 0.25f, 0.05f, 0.001f, 0.4f, seed + 1)),
        };
    }

    /** The vial's radiation: Geiger clicks, sparse and then thick, over {@code seconds}. */
    public static Voice[] eruptionGeiger(float seconds, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        java.util.Random rng = new java.util.Random(seed);
        float t = 0f;
        int i = 0;
        while (t < seconds && i < 160) {
            float u = t / seconds;
            out.add(after(t, noise(4500f, 3f, 0.006f, 0.12f, 0.0002f, 0.15f, seed + i)));
            t += (0.22f - 0.19f * u) * (0.3f + 1.4f * rng.nextFloat());
            i++;
        }
        out.add(swell(ERUPT_D * 4f * 1.0595f, seconds, 0.03f, new float[]{1f, 0.3f}));
        return out.toArray(new Voice[0]);
    }

    /** Jawbus rising out of the green lava: iron groaning, a great slosh, a heavy clank as it stops. */
    public static Voice[] eruptionGolem(float seconds, long seed) {
        return new Voice[]{
                noise(240f, 0.8f, seconds, 0.20f, seconds * 0.5f, 0.6f, seed),
                new Shaped(55f, 82f, seconds, 0.18f, Shaped.RISE, new float[]{1f, 0.7f, 0.5f, 0.3f}, 0.6f, 3f),
                after(seconds * 0.9f, tone(140f, 70f, 0.5f, 0.32f, 0.002f, new float[]{1f, 0.6f, 0.3f, 0.2f}, 0.7f)),
                after(seconds * 0.9f, noise(1200f, 1.5f, 0.15f, 0.12f, 0.001f, 0.5f, seed + 1)),
        };
    }

    /** The vial pulled out of its chest: glass ringing, a high bright whoosh. */
    public static Voice[] eruptionFree(long seed) {
        return new Voice[]{
                tone(1318.5f, 1318.5f, 0.9f, 0.09f, 0.002f, new float[]{1f, 0f, 0.3f}, 0.85f),
                tone(1318.5f * 2.76f, 1318.5f * 2.76f, 0.5f, 0.03f, 0.002f, new float[]{1f}, 0.85f),
                noise(2400f, 1.2f, 0.35f, 0.08f, 0.2f, 0.5f, seed),
        };
    }

    /** The crab under the shell: a peek (two clicks), then its legs skittering off fast. */
    public static Voice[] eruptionScuttle(float peekToRun, float runSeconds, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(noise(4200f, 3f, 0.02f, 0.10f, 0.0005f, 0.3f, seed));
        out.add(after(0.12f, noise(4600f, 3f, 0.02f, 0.10f, 0.0005f, 0.3f, seed + 1)));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 26; i++) {
            float at = peekToRun + i * runSeconds / 26f;
            float fade = 1f - i / 26f;
            out.add(after(at, noise(3000f + rng.nextFloat() * 2500f, 3f, 0.012f, 0.09f * fade, 0.0003f, 0.25f, seed + 2 + i)));
        }
        return out.toArray(new Voice[0]);
    }

    /** The End Times' ground breaking: a deep rumble and rock splitting along the cracks. */
    public static Voice[] eruptionGroundCrack(long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(tone(46f, 30f, 1.6f, 0.38f, 0.01f, new float[]{1f, 0.5f}, 0.7f));
        out.add(noise(120f, 0.6f, 1.5f, 0.22f, 0.05f, 0.7f, seed));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 9; i++) {
            out.add(after(0.05f + 0.07f * i + 0.04f * rng.nextFloat(), noise(900f + rng.nextFloat() * 1500f, 1.6f, 0.08f, 0.12f, 0.001f, 0.5f, seed + 2 + i)));
        }
        return out.toArray(new Voice[0]);
    }

    /** The book catching fire: a whump and a fire that keeps crackling for {@code seconds}. */
    public static Voice[] eruptionBookFire(float seconds, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(noise(800f, 0.9f, 0.4f, 0.20f, 0.01f, 0.5f, seed));
        out.add(tone(90f, 60f, 0.4f, 0.22f, 0.004f, new float[]{1f, 0.4f}, 0.5f));
        out.add(noise(1600f, 0.7f, seconds, 0.05f, 0.2f, 0.6f, seed + 1));
        java.util.Random rng = new java.util.Random(seed);
        int n = Math.round(seconds * 14);
        for (int i = 0; i < n; i++) {
            out.add(after(rng.nextFloat() * seconds, noise(2000f + rng.nextFloat() * 4000f, 2.5f, 0.012f, 0.03f + 0.04f * rng.nextFloat(), 0.0005f, 0.35f, seed + 3 + i)));
        }
        return out.toArray(new Voice[0]);
    }

    /** Kuudra: the carmine spreading through stopped time - a low choir opening, D minor, and a held breath. */
    public static Voice[] eruptionCarmine(float seconds) {
        float[] soft = {1f, 0.4f, 0.2f, 0.1f};
        java.util.List<Voice> out = new java.util.ArrayList<>();
        float[] hz = {ERUPT_D, ERUPT_D * 1.5f, ERUPT_D * 2f, ERUPT_D * 2.378f, ERUPT_D * 3f};
        for (int i = 0; i < hz.length; i++) out.add(after(0.15f * i, new Shaped(hz[i], hz[i], seconds, 0.07f, Shaped.PAD, soft, 0.92f, 4.5f)));
        out.add(new Shaped(ERUPT_D * 4f, ERUPT_D * 2f, seconds, 0.04f, Shaped.REVERSE, new float[]{1f, 0.3f}, 0.8f, 0f));
        return out.toArray(new Voice[0]);
    }

    /** Kuudra: the sea heaving up - a long deep surge with the waves breaking. */
    public static Voice[] eruptionSwell(float seconds, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(new Shaped(36f, 55f, seconds, 0.30f, Shaped.RISE, new float[]{1f, 0.6f, 0.3f}, 0.7f, 0f));
        out.add(noise(160f, 0.6f, seconds, 0.22f, seconds * 0.6f, 0.7f, seed));
        for (int i = 0; i < 4; i++) out.add(after(0.35f + i * seconds / 4f, noise(700f, 0.8f, 0.6f, 0.10f, 0.15f, 0.6f, seed + 1 + i)));
        return out.toArray(new Voice[0]);
    }

    /** A tentacle bursting up out of the lava: a splash and a wet heavy whoosh. */
    public static Voice[] eruptionTentacle(float amp, long seed) {
        return new Voice[]{
                noise(500f, 0.8f, 0.4f, 0.20f * amp, 0.002f, 0.5f, seed),
                noise(1600f, 1f, 0.3f, 0.09f * amp, 0.002f, 0.5f, seed + 1),
                tone(95f, 55f, 0.45f, 0.26f * amp, 0.003f, new float[]{1f, 0.4f}, 0.6f),
                after(0.08f, noise(900f, 1.1f, 0.5f, 0.10f * amp, 0.2f, 0.5f, seed + 2)),
        };
    }

    /** Kuudra's eyes opening under the sea: two heavy beats and a growl so low it is felt. */
    public static Voice[] eruptionEye(long seed) {
        return new Voice[]{
                tone(52f, 36f, 0.35f, 0.42f, 0.004f, new float[]{1f, 0.3f}, 0.5f),
                after(0.3f, tone(50f, 34f, 0.3f, 0.32f, 0.004f, new float[]{1f, 0.3f}, 0.5f)),
                after(0.5f, new Shaped(41f, 34f, 1.8f, 0.24f, Shaped.PAD, new float[]{1f, 0.8f, 0.6f, 0.5f, 0.4f, 0.3f}, 0.7f, 7f)),
                after(0.5f, noise(140f, 0.7f, 1.8f, 0.16f, 0.4f, 0.7f, seed)),
        };
    }

    /** Kuudra's roar as it offers the dye: a huge low bellow, rough, falling at the end. */
    public static Voice[] eruptionRoar(float seconds, long seed) {
        float[] rough = {1f, 0.8f, 0.7f, 0.6f, 0.5f, 0.45f, 0.4f, 0.3f};
        return new Voice[]{
                new Shaped(62f, 44f, seconds, 0.30f, Shaped.PAD, rough, 0.75f, 9f),
                new Shaped(93f, 66f, seconds, 0.14f, Shaped.PAD, rough, 0.75f, 11f),
                noise(380f, 0.7f, seconds, 0.24f, 0.12f, 0.7f, seed),
                noise(1500f, 0.9f, seconds * 0.8f, 0.09f, 0.15f, 0.7f, seed + 1),
        };
    }

    // ================================================================== the Mythological Dye: "The Whole Hunt"

    /** G, the Dye's root (its score is G dorian). */
    private static final float HUNT_G = 98f;
    private static final int[] HUNT_DORIAN = {0, 2, 3, 5, 7, 9, 10};

    /** G dorian, {@code degree} steps up from {@code root}, across octaves. */
    private static float huntNote(float root, int degree) {
        int oct = Math.floorDiv(degree, 7), d = Math.floorMod(degree, 7);
        return root * (float) Math.pow(2, oct + HUNT_DORIAN[d] / 12.0);
    }

    /** The split going wrong: a low groan swelling under the board, wood creaking, the olive light humming. */
    public static Voice[] huntGroan(float seconds, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(new Shaped(HUNT_G / 2f, HUNT_G / 2f * 1.06f, seconds, 0.30f, Shaped.RISE, new float[]{1f, 0.6f, 0.4f, 0.25f}, 0.6f, 6f));
        out.add(noise(140f, 0.7f, seconds, 0.16f, seconds * 0.8f, 0.6f, seed));
        out.add(new Shaped(HUNT_G * 8f, HUNT_G * 8.5f, seconds, 0.035f, Shaped.RISE, new float[]{1f, 0.3f}, 0.8f, 9f));
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 12; i++) {
            float at = seconds * (0.1f + 0.85f * (i + rng.nextFloat()) / 12f);
            out.add(after(at, noise(500f + rng.nextFloat() * 700f, 2.2f, 0.06f, 0.07f + 0.05f * i / 12f, 0.002f, 0.4f, seed + 1 + i)));
        }
        return out.toArray(new Voice[0]);
    }

    /** The cleaver wrenched out of the head: steel scraping free, a ring, and a whoosh as it spins away. */
    public static Voice[] huntWrench(long seed) {
        return new Voice[]{
                noise(2800f, 2f, 0.28f, 0.16f, 0.01f, 0.4f, seed),
                noise(5200f, 2.5f, 0.2f, 0.07f, 0.01f, 0.4f, seed + 1),
                tone(1180f, 1560f, 0.7f, 0.10f, 0.004f, new float[]{1f, 0f, 0.4f, 0f, 0.2f}, 0.85f),
                tone(1180f * 2.76f, 1560f * 2.76f, 0.4f, 0.03f, 0.004f, new float[]{1f}, 0.85f),
                after(0.12f, noise(900f, 1.2f, 0.5f, 0.12f, 0.2f, 0.4f, seed + 2)),
        };
    }

    /** The geyser: a blast, then the roar of the column standing for {@code seconds}, hissing at the top. */
    public static Voice[] huntGeyser(float seconds, long seed) {
        return new Voice[]{
                tone(52f, 24f, 1.4f, 0.48f, 0.002f, new float[]{1f, 0.6f, 0.3f}, 0.75f),
                noise(170f, 0.6f, 0.9f, 0.30f, 0.002f, 0.7f, seed),
                noise(1300f, 0.9f, 0.4f, 0.14f, 0.002f, 0.6f, seed + 1),
                after(0.15f, noise(420f, 0.7f, seconds, 0.12f, 0.3f, 0.6f, seed + 2)),
                after(0.15f, noise(3400f, 0.9f, seconds, 0.04f, 0.4f, 0.7f, seed + 3)),
        };
    }

    /** A head landing in the ring: a heavy wet thud and a bell a step higher up G dorian each time. */
    public static Voice[] huntThud(int step, boolean king, long seed) {
        float hz = huntNote(HUNT_G * 2f, step);
        float k = king ? 1.4f : 1f;
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(tone(king ? 70f : 96f, king ? 34f : 52f, king ? 0.6f : 0.3f, 0.32f * k, 0.002f, new float[]{1f, 0.4f, 0.2f}, 0.5f));
        out.add(noise(1100f, 1.1f, 0.16f, 0.14f * k, 0.001f, 0.4f, seed));
        out.add(noise(300f, 0.8f, 0.2f, 0.12f * k, 0.002f, 0.4f, seed + 1));
        out.add(tone(hz, hz, king ? 1.6f : 0.9f, 0.10f, 0.002f, new float[]{1f, 0.35f, 0.15f}, 0.85f));
        out.add(tone(hz * 2.76f, hz * 2.76f, 0.5f, 0.03f, 0.002f, new float[]{1f}, 0.85f));
        if (king) {
            for (float f : new float[]{HUNT_G, HUNT_G * 1.5f, HUNT_G * 2f}) out.add(tone(f, f, 1.8f, 0.07f, 0.01f, new float[]{1f, 0.7f, 0.5f, 0.3f}, 0.8f));
        }
        return out.toArray(new Voice[0]);
    }

    /** The ring spinning up: a whirl rising for {@code seconds}. */
    public static Voice[] huntWhirl(float seconds, long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(new Shaped(HUNT_G, HUNT_G * 4f, seconds, 0.10f, Shaped.RISE, new float[]{1f, 0.5f, 0.3f}, 0.7f, 5f));
        // Whooshes coming round faster and faster.
        float t = 0f;
        int i = 0;
        while (t < seconds && i < 40) {
            float u = t / seconds;
            out.add(after(t, noise(500f + 1500f * u, 1.3f, 0.14f - 0.07f * u, 0.05f + 0.07f * u, 0.05f - 0.03f * u, 0.3f, seed + i)));
            t += 0.42f - 0.32f * u;
            i++;
        }
        return out.toArray(new Voice[0]);
    }

    /** Diana's bow: the twang off the moon and the arrow's whistle. */
    public static Voice[] huntBow(long seed) {
        return new Voice[]{
                tone(330f, 290f, 0.45f, 0.18f, 0.001f, new float[]{1f, 0.7f, 0.5f, 0.3f, 0.2f}, 0.6f),
                noise(2600f, 2.5f, 0.06f, 0.14f, 0.0005f, 0.3f, seed),
                after(0.04f, new Shaped(1900f, 2600f, 0.6f, 0.04f, Shaped.PAD, new float[]{1f}, 0.7f, 0f)),
                after(0.04f, noise(4200f, 2f, 0.6f, 0.04f, 0.2f, 0.5f, seed + 1)),
        };
    }

    /** The arrow through head {@code i}: a pluck climbing G dorian, and the head bursting into paint. */
    public static Voice[] huntPluck(int i, boolean king, long seed) {
        float hz = huntNote(HUNT_G * 4f, i);
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(tone(hz, hz, 0.5f, 0.15f, 0.001f, new float[]{1f, 0.5f, 0.25f, 0.1f}, 0.8f));
        out.add(noise(1500f, 1.2f, 0.12f, 0.10f, 0.001f, 0.4f, seed));
        out.add(noise(500f, 0.9f, 0.1f, 0.08f, 0.001f, 0.4f, seed + 1));
        if (king) {
            out.add(tone(60f, 30f, 0.8f, 0.36f, 0.002f, new float[]{1f, 0.5f}, 0.6f));
            out.add(tone(hz * 2f, hz * 2f, 1.2f, 0.08f, 0.002f, new float[]{1f, 0.3f}, 0.9f));
        }
        return out.toArray(new Voice[0]);
    }

    /**
     * The shockwave: the deepest boom of Diana's endings, a rush of paint, and a choir opening on G major - the
     * dorian's minor third lifted for the reveal.
     */
    public static Voice[] huntShock(long seed) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        out.add(tone(44f, 22f, 2.0f, 0.5f, 0.003f, new float[]{1f, 0.6f, 0.3f}, 0.75f));
        out.add(noise(160f, 0.6f, 1.4f, 0.26f, 0.004f, 0.7f, seed));
        out.add(noise(4000f, 0.8f, 1.1f, 0.10f, 0.004f, 0.8f, seed + 1));
        float[] soft = {1f, 0.4f, 0.2f, 0.1f};
        for (float hz : new float[]{196f, 246.94f, 293.66f, 392f, 493.88f}) {
            out.add(after(0.15f, new Shaped(hz, hz, 3.4f, 0.065f, Shaped.PAD, soft, 0.92f, 5f)));
        }
        return out.toArray(new Voice[0]);
    }

    // ================================================================== the score

    /**
     * An expensive ending's own score, from the split to the reveal: a plucked arpeggio up and down
     * its scale from {@code root}, the notes coming faster and louder towards the end, over a low
     * pulse on the root. Each ending plays it in its own key and mode, so each has its own voice.
     */
    public static Voice[] score(float root, int[] scale, float seconds) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        float[] pluck = {1f, 0.45f, 0.2f, 0.08f};
        float t = 0f;
        int i = 0;
        int[] pattern = {0, 2, 4, 2, 5, 4, 2, 1};
        while (t < seconds - 0.05f && i < 200) {
            float u = t / seconds;
            int deg = pattern[i % pattern.length] + (i / pattern.length % 2) * 2;
            int octave = deg / scale.length, step = scale[deg % scale.length];
            float hz = root * 2f * (float) Math.pow(2, (step + 12 * octave) / 12.0);
            out.add(after(t, tone(hz, hz, 0.45f, 0.085f + 0.12f * u, 0.002f, pluck, 0.85f)));
            if (i % 4 == 0) out.add(after(t, tone(root / 2f, root / 2f, 0.5f, 0.17f + 0.13f * u, 0.004f, new float[]{1f, 0.3f}, 0.6f)));
            t += 0.34f - 0.24f * u;
            i++;
        }
        return out.toArray(new Voice[0]);
    }

    /** Neither a win nor a bust: something was there, but not what. A soft open chord. */
    public static Voice[] labUnknown() {
        float[] soft = {1f, 0.3f, 0.1f};
        return new Voice[]{
                swell(220f, 1.6f, 0.14f, soft),
                swell(329.63f, 1.6f, 0.10f, soft),
                swell(440f, 1.6f, 0.07f, soft),
        };
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
