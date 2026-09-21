package com.scathabutcher.audio;

import com.scathabutcher.ScathaButcherClient;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

/**
 * The cutscene's sound, as events rather than as samples.
 *
 * The screen says what happened - the blade swung, it landed, the drop was a legendary
 * pet - and this decides what that sounds like. Keeping the two apart means the cutscene
 * never mentions a frequency and this never mentions a phase.
 *
 * <h2>Volume belongs to the player</h2>
 *
 * The synth writes to its own audio line, completely outside Minecraft's sound system,
 * so none of the game's volume sliders reach it by default. That would be obnoxious: a
 * player who has turned Minecraft down, or all the way off, has said what they want.
 * {@link #tick()} reads the master slider every tick and hands it to the engine, so the
 * cutscene is as loud as the rest of the game and silent when the game is.
 *
 * It is also the one thing here that must survive {@link ScathaButcherClient}'s own
 * silencing: {@code s-mute} refuses the GAME's sounds while a cutscene is up, and if
 * that also stopped the cutscene's own sound there would be nothing left to hear.
 * Different paths entirely - the mute is a mixin on SoundEngine, this is a separate line.
 */
public final class ButcherAudio {

    private static final SynthEngine ENGINE = new SynthEngine();


    private ButcherAudio() {}

    /** Opens the audio line. Called once at start-up; harmless if audio is unavailable. */
    public static void start() {
        if (enabled()) ENGINE.start();
    }

    public static void shutdown() {
        ENGINE.shutdown();
    }

    /**
     * Keeps the engine's volume in step with the game's, and starts or stops it as the
     * setting is toggled. Cheap enough to call every client tick.
     */
    public static void tick() {
        if (!enabled()) {
            if (ENGINE.running()) {
                ENGINE.silence();
                ENGINE.shutdown();
            }
            return;
        }
        if (!ENGINE.running() && !ENGINE.broken()) ENGINE.start();

        Minecraft mc = Minecraft.getInstance();
        float volume = mc == null || mc.options == null
                ? 1f : mc.options.getFinalSoundSourceVolume(SoundSource.MASTER);
        int pct = ScathaButcherClient.config == null ? 75 : ScathaButcherClient.config.cutsceneVolume;
        ENGINE.setMaster(volume * Math.max(0f, Math.min(1.5f, pct / 100f)));
    }

    private static boolean enabled() {
        return ScathaButcherClient.config == null || ScathaButcherClient.config.cutsceneAudio;
    }

    /** Drops every sound in flight - the cutscene was interrupted. */
    public static void silence() {
        ENGINE.silence();
    }

    /**
     * Lets whatever is sounding finish on its own.
     *
     * The counterpart to {@link #silence()}, and the right call when a run reached its
     * end rather than being cut off: the pet sting starts as the screen is closing and
     * needs another second and a half, over the totem and the firework. Every voice
     * ends itself, so there is nothing to clean up - this is deliberately a no-op with
     * a name, so the two cases read differently at the call site instead of one of them
     * being a missing line.
     */
    public static void letRing() {
    }

    // ------------------------------------------------------------------ the cues

    /** The cavern fading up. */
    public static void onOpen(long introMs) {
        play(Voices.open(Math.max(0.4f, introMs / 1000f)));
    }

    /** The blade starting down. {@code fallMs} is how long it has to get there. */
    public static void onSwing(long fallMs, int chop) {
        play(Voices.swing(Math.max(0.05f, fallMs / 1000f * 1.35f), 7717L * (chop + 1)));
    }

    /**
     * The blade landing.
     *
     * @param chop  which cut this is, from 0
     * @param total how many cuts the run will take
     */
    public static void onImpact(int chop, int total) {
        float progress = total <= 1 ? 0f : Math.min(1f, chop / (float) (total - 1));
        for (Voice v : Voices.impact(progress, 4211L * (chop + 1))) play(v);
    }

    /**
     * The reveal. More notes for a better drop - and this fires at the reveal, never
     * before it, so it cannot spoil what is coming.
     *
     * @param petRarity "legendary", "epic", "rare", or null when it was not a pet
     */
    public static void onReveal(String petRarity) {
        int notes;
        float root;
        if (petRarity == null) {
            notes = 2;          // a gemstone or Dwarven O's: acknowledged, not celebrated
            root = 294f;
        } else {
            notes = switch (petRarity) {
                case "legendary" -> 5;
                case "epic" -> 4;
                default -> 3;
            };
            root = 330f;
        }
        for (Voice v : Voices.chime(notes, root)) play(v);
    }

    /**
     * The held tone that fills the wait before the next chop.
     *
     * @param chop    which cut is coming, from 0
     * @param total   how many the run will take
     * @param waitMs  how long until it lands
     */
    public static void onBuild(int chop, int total, long waitMs) {
        float progress = total <= 1 ? 0f : Math.min(1f, chop / (float) (total - 1));
        for (Voice v : Voices.build(progress, Math.max(0.1f, waitMs / 1000f))) play(v);
    }

    /** The sting for a pet drop, on our own line like everything else. */
    public static void onPetFanfare() {
        int pct = ScathaButcherClient.config == null ? 100
                : ScathaButcherClient.config.petFanfareVolume;
        float gain = Math.max(0f, Math.min(1.5f, pct / 100f));
        if (gain <= 0.001f) return;
        for (Voice v : Voices.fanfare()) play(gained(v, gain));
    }

    /** Wraps a voice so one cue can be louder or quieter than the rest. */
    private static Voice gained(Voice inner, float gain) {
        return (left, right, send, frames, rate) -> {
            // Rendered into scratch and added at gain, because a Voice writes straight
            // into the mix and there is nowhere else to scale it.
            float[] l = new float[frames], r = new float[frames], s2 = new float[frames];
            boolean alive = inner.render(l, r, s2, frames, rate);
            for (int i = 0; i < frames; i++) {
                left[i] += l[i] * gain;
                right[i] += r[i] * gain;
                send[i] += s2[i] * gain;
            }
            return alive;
        };
    }

    /** The blade missing, and the Scatha getting away. */
    public static void onMiss() {
        for (Voice v : Voices.miss(9091L)) play(v);
    }

    private static void play(Voice voice) {
        if (!enabled()) return;
        ENGINE.play(voice);
    }
}
