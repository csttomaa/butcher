package com.butcher.audio;

import com.butcher.ButcherClient;
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
 * It is also the one thing here that must survive {@link ButcherClient}'s own
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
        int pct = ButcherClient.config == null ? 75 : ButcherClient.config.cutsceneVolume;
        ENGINE.setMaster(volume * Math.max(0f, Math.min(1.5f, pct / 100f)));
    }

    private static boolean enabled() {
        return ButcherClient.config == null || ButcherClient.config.cutsceneAudio;
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
        int pct = ButcherClient.config == null ? 100
                : ButcherClient.config.petFanfareVolume;
        float gain = Math.max(0f, Math.min(1.5f, pct / 100f));
        if (gain <= 0.001f) return;
        for (Voice v : Voices.fanfare()) play(gained(v, gain));
    }

    /** Wraps a voice so one cue can be louder or quieter than the rest. */
    /** A skipped or cut-in cutscene: a short tape stop, so the cut sounds meant (the review, Tier 2). */
    public static void onSkip() {
        for (Voice v : Voices.seraphStutter()) play(v);
    }

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

    // ------------------------------------------------------------------ the Thaw

    public static void onThawOpen(long introMs) {
        for (Voice v : Voices.thawOpen(Math.max(0.4f, introMs / 1000f))) play(v);
    }

    /** @param colour 0 blue, 1 violet, 2 gold */
    public static void onThawFlare(int colour, long windupMs) {
        for (Voice v : Voices.thawFlare(colour, Math.max(0.12f, windupMs / 1000f))) play(v);
    }

    /** @param flareColour the flare this strike followed - its ring lands on that chord */
    public static void onThawStrike(int strike, int total, int flareColour) {
        float progress = total <= 1 ? 0f : Math.min(1f, strike / (float) (total - 1));
        for (Voice v : Voices.thawStrike(flareColour, progress, 5503L * (strike + 1))) play(v);
    }

    /** @param tier 0 common, 1 uncommon */
    public static void onThawLand(int tier, int slot) {
        play(Voices.thawLand(tier, slot));
    }

    public static void onThawCrash() {
        for (Voice v : Voices.thawCrash(6607L)) play(v);
    }

    /** The legendary's detonation. On its own slider, in the Vanguard menu group. */
    public static void onThawExplode() {
        float gain = thawFinaleGain();
        if (gain <= 0.001f) return;
        for (Voice v : Voices.thawExplode(7719L)) play(gained(v, gain));
    }

    public static void onThawDrain(long drainMs) {
        for (Voice v : Voices.thawDrain(Math.max(0.3f, drainMs / 1000f), 8821L)) play(v);
    }

    /** The Dye's pink flood - the rarest sound in the mod. Same slider as the detonation. */
    public static void onThawFlood() {
        float gain = thawFinaleGain();
        if (gain <= 0.001f) return;
        for (Voice v : Voices.thawFlood(9931L)) play(gained(v, gain));
    }

    /** The last strike's white glow, before its colour climbs. */
    public static void onThawClimbWhite(long ms) {
        for (Voice v : Voices.thawClimbWhite(Math.max(0.1f, ms / 1000f))) play(v);
    }

    /** The riser under the last strike's colour cycle, ending exactly {@code ms} from now. */
    public static void onThawBuildUp(long ms) {
        for (Voice v : Voices.thawBuildUp(Math.max(0.3f, ms / 1000f))) play(v);
    }

    /** One colour of the cycle. {@code step} counts up, and the ping rises with it. */
    public static void onThawCycleTick(int step) {
        play(Voices.thawCycleTick(step));
    }

    /** The cycle landing on the true colour. {@code holdMs} is how long until the hit. */
    public static void onThawClimb(int colour, long holdMs) {
        for (Voice v : Voices.thawClimb(colour, Math.max(0.15f, holdMs / 1000f))) play(v);
    }

    /** Impact on the Locket or the Dye: time slows down. */
    public static void onThawSlowMotion(long ms) {
        for (Voice v : Voices.slowMotion(Math.max(0.4f, ms / 1000f), 1207L)) play(v);
    }

    public static void onThawHeartbeat(int beat) {
        for (Voice v : Voices.heartbeat(1301L + beat)) play(v);
    }

    /** The Dye's swell, played backwards into the burst. Ends exactly {@code ms} from now. */
    public static void onThawReverseSwell(long ms) {
        float gain = thawFinaleGain();
        if (gain <= 0.001f) return;
        for (Voice v : Voices.reverseSwell(Math.max(0.2f, ms / 1000f))) play(gained(v, gain));
    }

    /** The Locket's music box, from the first note to the snap {@code ms} later. */
    public static void onThawMusicBox(long ms) {
        float gain = thawFinaleGain();
        if (gain <= 0.001f) return;
        for (Voice v : Voices.musicBox(Math.max(0.5f, ms / 1000f))) play(gained(v, gain));
    }

    public static void onThawLocketSnap() {
        float gain = thawFinaleGain();
        if (gain <= 0.001f) return;
        for (Voice v : Voices.locketSnap(1409L)) play(gained(v, gain));
    }

    public static void onThawLetter(int index) {
        play(Voices.letterTick(index));
    }

    public static void onThawCoin(int index) {
        play(Voices.coinTick(index));
    }

    public static void onThawCoinLand() {
        for (Voice v : Voices.coinLand()) play(v);
    }

    // ------------------------------------------------------------------ the Labyrinth

    public static void onLabRise(long seed) { for (Voice v : Voices.labRise(seed)) play(v); }

    public static void onLabIgnite(long runMs) { for (Voice v : Voices.labIgnite(Math.max(0.3f, runMs / 1000f))) play(v); }

    public static void onLabCorner(float progress) { for (Voice v : Voices.labCorner(progress)) play(v); }

    public static void onLabKnock(long seed) { for (Voice v : Voices.labKnock(seed)) play(v); }

    public static void onLabTorch(long seed) { for (Voice v : Voices.labTorch(seed)) play(v); }

    /** The last stretch's riser - the Thaw's, cut dead by the slam. */
    public static void onLabBuildUp(long ms) { for (Voice v : Voices.thawBuildUp(Math.max(0.2f, ms / 1000f))) play(v); }

    public static void onLabCycleTick(int step) { play(Voices.thawCycleTick(step)); }

    public static void onLabSlam() { for (Voice v : Voices.labSlam()) play(v); }

    public static void onLabRising(long ms) { for (Voice v : Voices.labRising(Math.max(0.2f, ms / 1000f))) play(v); }

    /** The drop arriving. On the Diana volume slider. */
    public static void onLabArrive() {
        int pct = ButcherClient.config == null ? 100 : ButcherClient.config.dianaVolume;
        float gain = Math.max(0f, Math.min(1.5f, pct / 100f));
        if (gain <= 0.001f) return;
        for (Voice v : Voices.labArrive()) play(gained(v, gain));
    }

    public static void onLabEmpty() { for (Voice v : Voices.labEmpty()) play(v); }

    // ---- Fin: the Fishing Festival announcement, on its own volume slider

    private static float finGain() {
        int pct = ButcherClient.config == null ? 100 : ButcherClient.config.fishingVolume;
        return Math.max(0f, Math.min(1.5f, pct / 100f));
    }

    private static float ghostGain() {
        int pct = ButcherClient.config == null ? 100 : ButcherClient.config.ghostVolume;
        return Math.max(0f, Math.min(1.5f, pct / 100f));
    }

    /** Wisp: the Ghost rising. */
    public static void onGhostRise(long seed) {
        float gain = ghostGain();
        if (gain > 0.001f) for (Voice v : Voices.ghostWail(seed)) play(gained(v, gain));
    }

    /** Wisp: the Ghost coming apart. */
    public static void onGhostBurst(long seed) {
        float gain = ghostGain();
        if (gain > 0.001f) for (Voice v : Voices.ghostBurst(seed)) play(gained(v, gain));
    }

    /** Wisp: the drop - a short chime, lower and minor-ish against Fin's. */
    public static void onGhostReveal() {
        float gain = ghostGain();
        if (gain > 0.001f) for (Voice v : Voices.chime(3, 220f)) play(gained(v, gain));
    }

    /** The fin breaking the surface. */
    public static void onFinRise() {
        float gain = finGain();
        if (gain <= 0.001f) return;
        for (Voice v : Voices.paintBlup()) play(gained(v, gain));
    }

    /** The hook yanked up: the line whipping taut, and the water going up. */
    public static void onFinChop(long seed) {
        float gain = finGain();
        if (gain <= 0.001f) return;
        for (Voice v : Voices.finYank(seed)) play(gained(v, gain));
        for (Voice v : Voices.paintSplash(seed)) play(gained(v, gain * 0.8f));
    }

    /** The bust: the hook yanked up empty, and the little falling notes. */
    public static void onFinMiss(long seed) {
        float gain = finGain();
        if (gain <= 0.001f) return;
        for (Voice v : Voices.miss(seed)) play(gained(v, gain));
        for (Voice v : Voices.finYank(seed)) play(gained(v, gain * 0.8f));
    }

    /** The drop out of the splash: a short rising chime, one note longer for a legendary. */
    public static void onFinReveal(boolean legendary) {
        float gain = finGain();
        if (gain <= 0.001f) return;
        for (Voice v : Voices.chime(legendary ? 4 : 3, 293.66f)) play(gained(v, gain));
    }

    public static void onLabSnuff(long seed) { for (Voice v : Voices.labSnuff(seed)) play(v); }

    public static void onLabSink(long seed) { for (Voice v : Voices.labSink(seed)) play(v); }

    public static void onLabUnknown() { for (Voice v : Voices.labUnknown()) play(v); }

    // The Minos Relic's ending. Its reverse swell, letters and arrival reuse the Thaw's and the win's.

    public static void onRelicDust(long ms, long seed) { for (Voice v : Voices.relicDust(Math.max(0.3f, ms / 1000f), seed)) play(v); }

    public static void onRelicBurst(int ring, long seed) { for (Voice v : Voices.relicBurst(ring, seed)) play(v); }

    public static void onRelicWind(long ms) { for (Voice v : Voices.relicWind(Math.max(0.3f, ms / 1000f))) play(v); }

    public static void onRelicIgnite() { for (Voice v : Voices.relicIgnite()) play(v); }

    public static void onBrainHum(long ms) { for (Voice v : Voices.brainHum(Math.max(0.3f, ms / 1000f))) play(v); }

    public static void onChiShimmer(long ms, long seed) { for (Voice v : Voices.chiShimmer(Math.max(0.3f, ms / 1000f), seed)) play(v); }

    public static void onChiOpen(long seed) { for (Voice v : Voices.chiOpen(seed)) play(v); }

    /** One of the three heads getting loose: 0 lion, 1 goat, 2 serpent. */
    public static void onChiHead(int k, long seed) {
        Voice[] vs = k == 0 ? Voices.chiLion(seed) : k == 1 ? Voices.chiGoat() : Voices.chiSerpent(seed);
        for (Voice v : vs) play(v);
    }

    public static void onChiSlam() { for (Voice v : Voices.chiSlam()) play(v); }

    // The Chopping Block.
    public static void onChopLand(long seed) { for (Voice v : Voices.chopLand(seed)) play(v); }

    public static void onChopWhoosh(int blow, long seed) { for (Voice v : Voices.chopWhoosh(blow, seed)) play(v); }

    public static void onChopHit(int blow, long seed) { for (Voice v : Voices.chopHit(blow, seed)) play(v); }

    public static void onChopSplit() { for (Voice v : Voices.chopSplit()) play(v); }

    public static void onChopCrumble(long seed) { for (Voice v : Voices.chopCrumble(seed)) play(v); }

    // The Fatal Sting.
    public static void onStingRise(long ms, long seed) { for (Voice v : Voices.stingRise(Math.max(0.3f, ms / 1000f), seed)) play(v); }

    public static void onStingClick(int plate, boolean sinking, long seed) { for (Voice v : Voices.stingClick(plate, sinking, seed)) play(v); }

    public static void onStingCreak(long ms, float fromHz, float toHz, long seed) { for (Voice v : Voices.stingCreak(Math.max(0.1f, ms / 1000f), fromHz, toHz, seed)) play(v); }

    public static void onStingHiss(long ms, long seed) { for (Voice v : Voices.stingHiss(Math.max(0.3f, ms / 1000f), seed)) play(v); }

    public static void onStingDrip(long seed) { for (Voice v : Voices.stingDrip(seed)) play(v); }

    public static void onStingWhoosh(long ms, long seed) { for (Voice v : Voices.stingWhoosh(Math.max(0.05f, ms / 1000f), seed)) play(v); }

    public static void onStingImpact(long seed) { for (Voice v : Voices.stingImpact(seed)) play(v); }

    public static void onStingStab(long seed) { for (Voice v : Voices.stingStab(seed)) play(v); }

    public static void onStingHeal(long ms, long seed) { for (Voice v : Voices.stingHeal(Math.max(0.3f, ms / 1000f), seed)) play(v); }

    // Wet Paint.







    // The Golden Fleece.
    public static void onFleeceHarp(long ms, boolean down) { for (Voice v : Voices.fleeceHarp(Math.max(0.2f, ms / 1000f), down)) play(v); }

    public static void onFleeceAir(long ms, long seed) { for (Voice v : Voices.fleeceAir(Math.max(0.3f, ms / 1000f), seed)) play(v); }

    public static void onFleeceWind(long ms, long seed) { for (Voice v : Voices.fleeceWind(Math.max(0.3f, ms / 1000f), seed)) play(v); }

    public static void onFleeceBloom(long seed) { for (Voice v : Voices.fleeceBloom(seed)) play(v); }

    public static void onFleeceSettle(long seed) { for (Voice v : Voices.fleeceSettle(seed)) play(v); }

    // The Heart of the Beast.
    public static void onHeartRumble(long ms, long seed) { for (Voice v : Voices.heartRumble(Math.max(0.3f, ms / 1000f), seed)) play(v); }

    public static void onWingUnfurl(long ms, long seed) { for (Voice v : Voices.wingUnfurl(Math.max(0.2f, ms / 1000f), seed)) play(v); }

    public static void onWingGust(long seed) { for (Voice v : Voices.wingGust(seed)) play(v); }

    public static void onWingWrap(long ms, long seed) { for (Voice v : Voices.wingWrap(Math.max(0.2f, ms / 1000f), seed)) play(v); }

    public static void onHeartMuffled() { for (Voice v : Voices.heartMuffled()) play(v); }

    /** An expensive ending's score: see Voices.score. Ends {@code ms} from now, at the reveal. */
    public static void onScore(float root, int[] scale, long ms) {
        for (Voice v : Voices.score(root, scale, Math.max(0.5f, ms / 1000f))) play(v);
    }

    // Midas.
    public static void onMidasCreep(long ms, long seed) { for (Voice v : Voices.midasCreep(Math.max(0.3f, ms / 1000f), seed)) play(v); }

    public static void onMidasClank(long seed) { for (Voice v : Voices.midasClank(seed)) play(v); }

    public static void onMidasShine() { for (Voice v : Voices.midasShine()) play(v); }

    public static void onMidasCrowned() { for (Voice v : Voices.midasCrowned()) play(v); }

    public static void onMidasAvalanche(long seed) { for (Voice v : Voices.midasAvalanche(seed)) play(v); }

    public static void onRelicMoon(long ms) {
        int pct = ButcherClient.config == null ? 100 : ButcherClient.config.dianaVolume;
        float gain = Math.max(0f, Math.min(1.5f, pct / 100f));
        if (gain <= 0.001f) return;
        for (Voice v : Voices.relicMoon(Math.max(0.5f, ms / 1000f))) play(gained(v, gain));
    }

    private static float thawFinaleGain() {
        int pct = ButcherClient.config == null ? 100 : ButcherClient.config.vanguardFinaleVolume;
        return Math.max(0f, Math.min(1.5f, pct / 100f));
    }

    /** Hellion's cues come ready-made from {@link HellionSound}'s schedule. */
    public static void playHellion(Voice[] voices) {
        playSlayer(voices, ButcherClient.config == null ? 100 : ButcherClient.config.blazeVolume);
    }

    /** The Seraph's cues, on its own volume. */
    public static void playSeraph(Voice[] voices) {
        playSlayer(voices, ButcherClient.config == null ? 100 : ButcherClient.config.seraphVolume);
    }

    /** Diana's ending cues, on the Diana volume (the win volume). */
    public static void playDiana(Voice[] voices) {
        playSlayer(voices, ButcherClient.config == null ? 100 : ButcherClient.config.dianaVolume);
    }

    /** Eruption's cues (lava fishing), on its own volume. */
    public static void playEruption(Voice[] voices) {
        playSlayer(voices, ButcherClient.config == null ? 100 : ButcherClient.config.lavaVolume);
    }

    private static void playSlayer(Voice[] voices, int pct) {
        float gain = Math.max(0f, Math.min(1.5f, pct / 100f));
        if (gain <= 0.001f) return;
        for (Voice v : voices) play(Math.abs(gain - 1f) < 0.01f ? v : gained(v, gain));
    }

    /** While true nothing new starts: a skipped cutscene runs the cues it jumps over without sounding them. */
    private static boolean muted;

    /** Runs {@code r} with every cue it starts dropped. */
    public static void muted(Runnable r) {
        boolean was = muted;
        muted = true;
        try {
            r.run();
        } finally {
            muted = was;
        }
    }

    private static void play(Voice voice) {
        if (!enabled() || muted) return;
        ENGINE.play(voice);
    }
}
