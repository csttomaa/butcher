package com.butcher.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Settings for the "/butcher" menu, stored as JSON at
 * "config/butcher.json". Edit this file and run "/butcher reload"
 * in-game to pick up changes without restarting Minecraft (or use "/butcher"
 * to open the in-game menu for the same settings).
 *
 * Drop detection itself is NOT chat-based (an earlier version of this mod
 * tried regex-matching chat lines, but there is no chat broadcast for a
 * Scatha kill or its drop at all - confirmed by decompiling Scatha-Pro's own
 * jar). See {@link com.butcher.detect.ScathaWormTracker} and
 * {@link com.butcher.detect.HotbarDropWatcher} for how it actually
 * works: nearby armor-stand nametag scanning for the kill, then inventory
 * item NBT diffing for the drop.
 */
public class PatternConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("butcher.json");

    /** Total length of the guillotine cutscene, in milliseconds. */
    public int animationDurationMs = 2600;

    /**
     * Length of a FULL five-cut cleaver cutscene, in milliseconds. Every phase of
     * that animation - the slide-in, each wind-up, fall and impact, and the reveal -
     * is scaled by the same factor, so moving this only ever changes the pace, never
     * the shape. Runs that end in fewer cuts are proportionally shorter.
     */
    public int scopeDurationMs = 7600;

    /** Which drop animation (if any) plays - only one can be active at a time. */
    public enum AnimationMode { NONE, GUILLOTINE, SCOPE }

    /** Which drop animation is active, toggled from the /butcher menu. Turning one on turns the other off. */
    public AnimationMode animationMode = AnimationMode.SCOPE;

    /**
     * The style to come back to when Scatha Gambling is switched off and on again.
     * The menu presents "on/off" and "which cutscene" as two separate rows, but the
     * setting behind them is one enum, so the chosen style has to be parked
     * somewhere while the mode itself sits at NONE.
     */
    public AnimationMode lastStyle = AnimationMode.SCOPE;

    /**
     * Cover the hotbar, held item and chat once a nearby Scatha is at or below this
     * many hearts, so a drop can't be spotted before the cutscene reveals it. A
     * Scatha's nametag counts down from 10, so this is 1-10.
     */
    public int hideBelowHp = 3;

    /** Set off a real firework, coloured by the pet's rarity, as a pet-drop cutscene closes.
     *  No menu row for this one - edit the JSON and run "/butcher reload". */
    public boolean petDropFirework = true;

    /** Play the Scatha-head popup - vanilla's totem-of-undying animation, 2 seconds - as a
     *  pet-drop cutscene closes. Its length is fixed by the game, so there's no timing knob. */
    public boolean petHeadPopup = true;

    /**
     * Refuse the game's own sounds while a cutscene - or the pre-kill veil - owns the
     * screen.
     *
     * On by default because a sound cue spoils the reveal the same way a chat line does:
     * Hypixel's drop chime can land while the cleaver is still working up the body, and
     * then the number of chops no longer hides anything. A handful of mining noises are
     * dropped rather than queued for the few seconds this lasts.
     */
    public boolean silenceGameAudio = true;

    /**
     * Play the cutscene's own synthesised sound - the swing, the impacts, the reveal
     * chime.
     *
     * Generated at runtime rather than shipped as files, and written to its own audio
     * line, so it is unaffected by {@link #silenceGameAudio} above. It follows
     * Minecraft's master volume slider, and turning that to zero silences it too.
     */
    public boolean cutsceneAudio = true;

    /**
     * Take the vanilla HUD off the screen during the pre-kill hide and the cutscene.
     *
     * The hotbar is the leak this closes. A pet lands in it before the cutscene even
     * opens, and the cutscene's own backdrop is a dim rather than a blackout, so
     * without this the player can read the answer through it and the reveal is spoiled
     * before the cleaver has moved.
     *
     * The dark veil stays either way - it darkens the world on the way in, which this
     * does not. The two do different halves of the same job, and the HUD only goes once
     * the veil is dark enough to cover it going.
     *
     * ON by default. The version of this that faded each HUD element one at a time was
     * off by default and never worked; {@code HudMixin} simply does not build the HUD
     * on the frames it should not be there, which is what the game itself does for F1.
     */
    public boolean hideHud = true;

    /**
     * Write every generated texture to {@code config/butcher/textures/} so it can
     * be edited, and prefer an edited file over the generated one.
     *
     * The art is drawn in Java, which is fine for whoever is writing the mod and no use
     * at all to whoever is playing it. This puts the PNGs somewhere reachable. A manifest
     * of hashes is what stops the mod overwriting your edits on the next launch - see
     * {@link com.butcher.render.ArtOverrides}.
     */
    public boolean writeGeneratedArt = true;

    /**
     * Drop the cavern and the gemstone outcrops, and play the cutscene over a plain
     * dimmed backdrop that fades in and out with it.
     *
     * ON by default. The cave and the outcrops were the busiest things on screen and
     * they fought the animation rather than framing it - the worm, the blade and the
     * timing all read better against nothing. The cavern art is still there and still
     * generated (see CaveArt); set this false to bring it back.
     */
    public boolean plainBackdrop = true;

    /**
     * Percent chance that a non-pet run ends with the Scatha ducking the last swing and
     * bolting, instead of the blade stopping short.
     *
     * Never applies to a pet: Hypixel has already decided the drop and the cutscene is
     * not allowed to contradict it. This only changes how a run you were never going to
     * win ends - and it means a long run is no longer a promise that the pet is coming,
     * which is the whole point of the chop count being hidden.
     *
     * 0 turns it off entirely; 100 makes every non-pet run a miss.
     */
    public int escapeChance = 45;

    /**
     * Cutscene volume, as a percentage, on top of Minecraft's master slider.
     *
     * Separate from the game's own volume rather than instead of it: the synth writes to
     * its own audio line, so without this there would be no way to have the cutscene
     * quieter or louder than everything else.
     */
    public int cutsceneVolume = 75;

    /** Vanilla's advancement sting when a pet drops. */
    public boolean petFanfare = true;

    /**
     * How loud that sting is, as a percentage.
     *
     * Its own slider rather than riding the cutscene volume, because it is the only
     * sound here that does not come from our synth - it goes through Minecraft's mixer
     * - and because it is the one moment anyone might want louder than everything else.
     */
    public int petFanfareVolume = 100;

    /**
     * How strongly the run builds, each as a percentage of the built-in amount.
     *
     * Three separate knobs rather than one "intensity", because they fail differently:
     * the vignette is pure decoration, the zoom changes how much of the scene you can
     * see, and the shake is the one people actually get motion sick from. Anyone who
     * wants the effect without the shake should not have to give up the other two.
     */
    public int vignetteStrength = 100;
    public int zoomStrength = 100;
    public int shakeStrength = 100;

    public static PatternConfig loadOrCreate() {
        try {
            if (Files.exists(PATH)) {
                String json = Files.readString(PATH);
                PatternConfig loaded = GSON.fromJson(json, PatternConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[Butcher] Failed to read config, falling back to defaults: " + e);
        }
        PatternConfig fresh = new PatternConfig();
        fresh.save();
        return fresh;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[Butcher] Failed to save config: " + e);
        }
    }
}
