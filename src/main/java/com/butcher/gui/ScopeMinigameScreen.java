package com.butcher.gui;

import com.butcher.ButcherClient;
import com.butcher.detect.DropDetector.DropResult;
import com.butcher.detect.DropDetector.Outcome;
import com.butcher.anim.SwingCurve;
import com.butcher.audio.ButcherAudio;
import com.butcher.render.MeshRenderer;
import com.butcher.render.TextureBank;
import com.butcher.render.art.CaveArt;
import com.butcher.render.ScathaSkins;
import com.butcher.render.art.PropArt;
import com.butcher.render.model.CleaverMesh;
import com.butcher.render.model.ModelProjection;
import com.butcher.render.model.PlayerHeadMesh;
import com.butcher.render.model.ScathaModels;
import com.butcher.render.scene.CutsceneScene;
import com.butcher.render.scene.ScathaBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.Random;

/**
 * The butcher's-cleaver drop animation: a full-screen cavern, the Scatha laid
 * out in six parts, and a cleaver that swings down and takes them off one at a
 * time.
 *
 * How deep the cleaver gets IS the drop. The Scatha is six parts - five body
 * parts and, last of all, the head - and the run is dealt a number of chops up
 * front, worked backwards from what Hypixel already decided:
 *
 *   5 chops   cut through all five body parts, leaving the head    -> PET
 *   1-4       stop somewhere short of it                           -> gemstone or Dwarven O's
 *
 * Only the pet has a fixed count. Anything else draws at random from one to
 * four, so the number of cuts never tells you WHICH consolation drop is coming -
 * and since nothing on screen counts them, it can't tell you a pet is coming
 * either until the last part comes off.
 *
 * Every outcome reveals the same way: the drop lifts off the block, arcs into
 * the middle of the screen and shrinks away to nothing. Only the pet head spins
 * on its way out, and only the pet hands off to the game - the moment it
 * vanishes, the real Scatha pet head is fired through vanilla's
 * totem-of-undying animation, so the flat drawing appears to turn into the
 * actual item. See {@link com.butcher.anim.PetHeadPopup}.
 *
 * There is no input of any kind: the whole thing plays itself.
 */
public class ScopeMinigameScreen extends Screen {
    private enum Phase { INTRO, READY, RAISE, FALL, IMPACT, REVEAL, FLASH_OUT, FADE_OUT, DONE }

    // ---- timings (ms) at the reference speed ----
    // Every one of these is multiplied by the same factor at construction time, so
    // the "Cleaver Cutscene Speed" slider stretches or squeezes the whole thing
    // evenly instead of changing which parts feel fast. See BASE_TOTAL_MS below.
    // Long enough for the opening to be staged - fade up, outcrops in, Scatha on -
    // rather than everything landing at once.
    private static final long INTRO_MS      = 1100;
    private static final long READY_MS      = 420;
    private static final long RAISE_MS      = 300;
    private static final long FALL_MS       = 170;
    private static final long IMPACT_MS     = 420;
    /** Pet payoff: head lifts, spins, shrinks away. Short and punchy - the totem takes over right after. */
    private static final long PET_REVEAL_MS = 1500;
    /** Same move for a gemstone or Dwarven O's, but held longer - nothing follows it, so this IS the payoff. */
    private static final long DROP_REVEAL_MS = 2600;
    /** White blink between the head vanishing and the screen handing back to the game. */
    private static final long FLASH_MS      = 150;
    private static final long FADE_OUT_MS   = 400;
    /** Sparks are done well before the cleaver lifts - they're struck, not lingering. */
    private static final long SPARK_MS      = 260;
    /** How long the cleaver takes to withdraw once the last chop is done. */
    private static final long CLEAVER_EXIT_MS = 320;
    /** Longest the screen will wait to be told the drop before assuming a gemstone. */
    private static final long RESOLVE_DEADLINE_MS = 2600;

    /**
     * What a full five-cut pet run adds up to at the reference speed, and therefore
     * what the config's scopeDurationMs is measured against. A run that ends in fewer
     * cuts is shorter by the chops it didn't take - the slider sets the pace, not a
     * stopwatch every run has to hit.
     */
    public static final long BASE_TOTAL_MS =
            INTRO_MS + READY_MS + 5 * (RAISE_MS + FALL_MS + IMPACT_MS) + PET_REVEAL_MS + FLASH_MS;
    /** Guard rails on the scale factor, so a hand-edited config can't stall or skip the cutscene. */
    private static final float MIN_SPEED_SCALE = 0.35f, MAX_SPEED_SCALE = 3.5f;

    /** How tall the flying head gets at the top of its arc, as a fraction of screen height. */
    private static final float HEAD_REVEAL_FRAC = 0.34f;
    /** Same, for a gemstone pile or a Dwarven O's block - a little smaller, since neither is the prize. */
    private static final float ITEM_REVEAL_FRAC = 0.28f;
    /** Full turns the head makes on its way out. */
    private static final float HEAD_TURNS = 3f;

    private static final int PARTS = 6;        // five body parts plus the head

    private static final String NS = ButcherClient.MOD_ID;
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath(NS, path); }

    // Generated by CaveArt at first use; see ButcherClient for the registrations.
    private static Identifier cave()      { return TextureBank.id(ButcherClient.TEX_CAVE); }
    private static final Identifier TORCH_GLOW = id("textures/gui/cave/torch_glow.png");
    private static final Identifier EMBER = id("textures/gui/cave/ember.png");
    private static final Identifier[] BAT_FRAMES = { id("textures/gui/cave/bat_00.png"), id("textures/gui/cave/bat_01.png") };

    // Gemstone outcrops that slide in from the edges and frame the whole run.
    // Two sprites per side, drawn at the same rect: the crystals are grey and get
    // their colour from the blit tint, and the rock goes over the top of them
    // untinted - which is exactly why it's a separate texture, since one sprite
    // would mean the stone changing colour along with the gems.
    private static final float GEM_TEX_RATIO = CaveArt.GEM_W / (float) CaveArt.GEM_H;

    /**
     * Where the twinkles sit, as fractions of the outcrop's rect - each one is
     * partway up a crystal's bright ridge. Produced by the texture generator
     * alongside the sprites, so they can't drift out of step with the art. The
     * right-hand sprite is the left one mirrored, so its x is 1 - x.
     */
    private static final float[][] SPARKLE_AT = CaveArt.SPARKLES;
    /** One full twinkle cycle per anchor. */
    private static final long SPARKLE_PERIOD_MS = 2600;
    /** Fraction of that cycle a twinkle is actually visible - the rest is the wait. */
    private static final float SPARKLE_DUTY = 0.30f;
    /** Outcrop height as a fraction of screen height. The rock runs to the bottom edge. */
    private static final float GEM_FRAC = 0.62f;
    /** The two clusters are always different colours - see the constructor. */
    private static final int[] GEM_COLOURS = {
            0xFFE04A4A,  // red
            0xFF7BE06B,  // light green
            0xFF6BC4E0,  // light blue
            0xFFE8D24A,  // yellow
            0xFFE87BC0,  // pink
            0xFFA86BE0,  // purple
            0xFFE0903A,  // orange
    };

    private static final Identifier SEG_HEAD = id("textures/gui/butcher/seg_head.png");
    private static final Identifier CUT_FLASH = id("textures/gui/butcher/cut_flash.png");

    private static final Identifier BLOCK_BRAN = id("textures/gui/butcher/block_bran.png");
    private static final Identifier GEMSTONES = id("textures/gui/butcher/gemstones.png");

    // The swing used to be a 14-frame sprite sheet, because the GUI pose stack is 2D
    // and rotating a sprite from Java looked impossible. It is not - see
    // render/model - so the cleaver is a real mesh now. The frame COUNT survives as
    // the swing's timeline: every phase still works out a frame 0..13 and that index
    // is what gets mapped to a blade angle, so the pacing is unchanged.
    private static final int CLEAVER_FRAMES = 14;
    /** Where the middle of the cutting edge sits in the cleaver frame at the moment of impact. */
    private static final float CLEAVER_EDGE_FX = 0.2364f;
    private static final float CLEAVER_EDGE_FY = 0.6964f;
    /** Cleaver frame size as a fraction of the smaller screen dimension. */
    private static final float CLEAVER_FRAC = 0.72f;

    private static final int SPARK_FRAMES = 6;
    private static final Identifier[] SPARKS = new Identifier[SPARK_FRAMES];
    static {
        for (int i = 0; i < SPARK_FRAMES; i++) {
            SPARKS[i] = id(String.format("textures/gui/butcher/spark_%d.png", i));
        }
    }
    /**
     * Flame centres in the generated backdrop, as fractions of its own canvas. Read
     * straight off {@link CaveArt} rather than copied, so the flicker, glow and embers
     * animated here can never drift from where the torches were actually painted.
     */
    private static final float[][] TORCH_FRAC = CaveArt.TORCHES;
    private static final long BAT_FLAP_MS = 260;
    private static final int BAT_COUNT = 4, EMBER_COUNT = 9;

    private final Random rng = new Random();
    private final long seedMs = System.currentTimeMillis();

    /**
     * The drop, once it is known. The screen now opens the instant the Scatha
     * dies, BEFORE the watcher has worked out what fell - see {@link #resolve}.
     */
    private DropResult result;
    private boolean resolved;

    /** Reused across frames so a 60fps cutscene allocates nothing per frame. */
    private final MeshRenderer meshes = new MeshRenderer();
    private int chops;                 // 1..5, worked backwards from the real drop
    private boolean reachesHead;       // true only when the pet dropped
    private final long openedAt = System.currentTimeMillis();

    private final int gemLeftColour, gemRightColour;

    // Every phase length, already scaled by the menu's speed setting.
    private final long introMs, readyMs, raiseMs, fallMs, impactMs;
    private final long petRevealMs, dropRevealMs, flashMs, fadeOutMs, sparkMs, cleaverExitMs;

    private Phase phase = Phase.INTRO;
    private long phaseStart = System.currentTimeMillis();
    private int partsRemoved = 0;      // parts already chopped off the left end
    /** The part currently tumbling away, or -1. Set the instant the blade lands, not after it lifts. */
    private int flyingPart = -1;
    /** Where the last cut landed, so the cleaver can lift out of it rather than blinking away. */
    private int lastCutPart = 0;
    private long revealStart = 0;
    private boolean payoffFired = false;
    private boolean active = true;

    /** @param result the drop, or null to open now and be told later via {@link #resolve}. */
    public ScopeMinigameScreen(DropResult result) {
        super(Component.literal("Butcher"));
        if (result != null) resolve(result);

        // Fresh pair of gemstone colours every run. The second index is picked from
        // the OTHER six rather than re-rolled, so the two can never come up the same
        // and no retry loop is needed.
        int a = rng.nextInt(GEM_COLOURS.length);
        int b = (a + 1 + rng.nextInt(GEM_COLOURS.length - 1)) % GEM_COLOURS.length;
        this.gemLeftColour = GEM_COLOURS[a];
        this.gemRightColour = GEM_COLOURS[b];

        // One factor, applied to everything, so changing the speed can't reshape the
        // animation - only how fast it runs.
        int wanted = ButcherClient.config == null
                ? (int) BASE_TOTAL_MS : ButcherClient.config.scopeDurationMs;
        float scale = Math.max(MIN_SPEED_SCALE, Math.min(MAX_SPEED_SCALE, wanted / (float) BASE_TOTAL_MS));
        this.introMs = scaled(INTRO_MS, scale);
        this.readyMs = scaled(READY_MS, scale);
        this.raiseMs = scaled(RAISE_MS, scale);
        this.fallMs = scaled(FALL_MS, scale);
        this.impactMs = scaled(IMPACT_MS, scale);
        this.petRevealMs = scaled(PET_REVEAL_MS, scale);
        this.dropRevealMs = scaled(DROP_REVEAL_MS, scale);
        this.flashMs = scaled(FLASH_MS, scale);
        this.fadeOutMs = scaled(FADE_OUT_MS, scale);
        this.sparkMs = scaled(SPARK_MS, scale);
        this.cleaverExitMs = scaled(CLEAVER_EXIT_MS, scale);

        // The cavern's low swell, started with the screen rather than on the first frame
        // so it is already under way by the time the fade-up finishes.
        ButcherAudio.onOpen(this.introMs);
        com.butcher.config.ButcherStats.get().onCutscene();
    }

    private static long scaled(long base, float scale) {
        return Math.max(16L, Math.round(base * scale));
    }

    /**
     * Hand the screen the drop once the inventory watcher has worked it out.
     *
     * The screen deliberately opens before this is known. Gemstones - by far the
     * commonest drop - can only be identified by ELIMINATION, i.e. by the kill
     * window closing with no pet and no Dwarven O's in the inventory, so waiting
     * for the result meant the cutscene always started a full two seconds after
     * the Scatha died. Opening straight away and filling the outcome in during
     * the fade-in costs nothing: the opening is the same however the run ends,
     * and the first cut is the earliest moment the count actually matters.
     */
    public void resolve(DropResult result) {
        if (resolved) return;
        resolved = true;
        this.result = result;
        Outcome outcome = result == null ? Outcome.GEMSTONE : result.outcome;
        this.chops = switch (outcome) {
            case PET -> 5;                          // all five body parts come off, the head is yours
            // Dwarven O's and gemstones share the same 1-4 range on purpose: if the
            // block were always four cuts, four cuts would announce it.
            case BLOCKED, GEMSTONE -> 1 + rng.nextInt(4);
        };
        this.reachesHead = outcome == Outcome.PET;

        // Does it get away?
        //
        // Only ever on a non-pet run, because the drop has already been decided by
        // Hypixel and the cutscene is not allowed to contradict it - a pet in the
        // inventory cannot be shown escaping. What this changes is how a NON-pet run
        // ends: instead of the blade simply stopping short, the Scatha ducks the last
        // swing and is gone. Same outcome, far better ending, and it means five chops
        // are no longer the only way a run can finish - so a long run stops being a
        // guarantee that the pet is coming.
        int chance = ButcherClient.config == null
                ? DEFAULT_ESCAPE_CHANCE : ButcherClient.config.escapeChance;
        this.escapes = !reachesHead && rng.nextInt(100) < Math.max(0, Math.min(100, chance));
        // Which way it ducks. Either reads fine; picking at random stops the miss
        // looking like a scripted beat once you have seen a few.
        this.dodgeUp = rng.nextBoolean();
    }

    /** Used only if the config could not be read. */
    private static final int DEFAULT_ESCAPE_CHANCE = 45;

    /** How far the worm ducks out of the blade's path, in world units. */
    private static final float DODGE_UNITS = 7.5f;
    /** How far right it travels before it is gone. */
    private static final float ESCAPE_UNITS = 95f;

    /** True when the last swing is a miss and the worm bolts. */
    private boolean escapes;
    private boolean dodgeUp;
    /**
     * True only when the blade genuinely missed - the worm ducked it.
     *
     * Distinct from {@link #escapeAt}, which is now set at the end of EVERY run because
     * the Scatha always leaves. The two want opposite things from the cleaver: a blade
     * that hit something is drawn back out of it, a blade that hit nothing carries
     * through and drops. And they want different words on screen.
     */
    private boolean missedSwing;

    /** Where the drop was shaken loose - tracked until POP_AT, then held. */
    private float revealFromX, revealFromY;
    /** How far into the escape the item comes free. */
    private static final float POP_AT = 0.22f;

    /** How much of the reveal the cavern takes to dim all the way back. */
    private static final float DIM_IN = 0.50f;
    /** The same, for an escape: longer, and on a smoothstep rather than a cubic. */
    private static final float DIM_IN_ESCAPE = 0.86f;

    /** When the worm started getting away, for the dodge and the "Missed..." card. */
    private long escapeAt = -1;
    /** How long the escape takes before the screen lets go. */
    private static final long ESCAPE_MS = 1500;

    private void decisionsNoteMiss() {
        ButcherClient.decisions.note("§eThe Scatha ducked the last swing and got away");
    }

    /** Whether the blade is on its last swing - the one that can be dodged. */
    private boolean finalSwing() {
        return escapes && partsRemoved >= chops - 1;
    }

    /**
     * How far the fleeing worm has moved, in world units, as {@code {x, y}}.
     *
     * Shared so the drop can be launched from the body's ACTUAL position rather than
     * from where it used to be. The reveal used to start at the segment's resting
     * coordinates, which by then were empty cave - the item appeared out of nowhere a
     * little behind the animal instead of coming out of it.
     *
     * Two separate moves with different shapes on purpose: the duck is fast and eases
     * out, because it is a reaction; the run starts slow and accelerates, because it is
     * a decision. One curve for both made it look dragged off rather than leaving.
     */
    private float[] escapeOffset(long now) {
        if (escapeAt < 0 || reachesHead) return new float[]{0f, 0f};
        float e = escapeProgress(now);
        float duck = easeOutCubic(Math.min(1f, e / 0.18f));
        float y = (dodgeUp ? DODGE_UNITS : -DODGE_UNITS) * duck;
        float run = easeIn(Math.min(1f, Math.max(0f, (e - 0.10f) / 0.90f)));
        return new float[]{run * ESCAPE_UNITS, y};
    }

    /** 0 before the dodge, 1 once the worm is gone. */
    /**
     * True for a run that ended with the worm bolting - ducked the blade, or simply left
     * once the last cut landed. False for the pet, whose head never goes anywhere.
     */
    private boolean escaped() { return escapeAt >= 0 && !reachesHead; }

    private float escapeProgress(long now) {
        if (escapeAt < 0) return 0f;
        return Math.min(1f, (now - escapeAt) / (float) ESCAPE_MS);
    }

    public boolean isResolved() { return resolved; }

    public boolean isActive() { return active; }

    @Override protected void init() { }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) { }
    /** Nothing here reacts to the mouse - the whole sequence plays itself. */
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) { return true; }

    // ================================================================= phases
    private void advance(long now) {
        long since = now - phaseStart;
        switch (phase) {
            case INTRO -> { if (since >= introMs) enter(Phase.READY, now); }
            case READY -> {
                // The cleaver doesn't start until the drop is known, since the number
                // of cuts comes straight out of it. In practice the watcher has
                // answered long before the intro finishes; the deadline is only here
                // so a missed answer can't leave the screen hanging forever.
                if (!resolved && now - openedAt < RESOLVE_DEADLINE_MS) break;
                if (!resolved) resolve(DropResult.gemstone());
                if (since >= readyMs) enter(Phase.RAISE, now);
            }
            case RAISE -> {
                if (since >= raiseFor(chopIndex())) {
                    ButcherAudio.onSwing(fallFor(chopIndex()), partsRemoved);
                    enter(Phase.FALL, now);
                }
            }
            case FALL -> {
                if (since >= fallFor(chopIndex()) && finalSwing()) {
                    // The blade goes through empty air. Nothing is removed, nothing
                    // flies off, and the run ends here rather than at a reveal.
                    // Ducked it. The blade carries through, the worm bolts - and the
                    // drop still comes out of it on the way past, because getting away
                    // means it did not leave its HEAD behind, not that it left nothing.
                    escapeAt = now;
                    missedSwing = true;
                    lastImpactAt = now;
                    ButcherAudio.onMiss();
                    com.butcher.config.ButcherStats.get().onMiss();
                    decisionsNoteMiss();
                    enter(Phase.REVEAL, now);
                    revealStart = now;
                    break;
                }
                if (since >= fallFor(chopIndex())) {
                    // The blade has landed. The part it took off stops being part of
                    // the worm right here, on the same frame as the flash - not when
                    // the cleaver eventually lifts back out - and the part behind it
                    // becomes the freshly-cut face at the same instant.
                    flyingPart = partsRemoved;
                    lastCutPart = partsRemoved;
                    partsRemoved++;
                    lastImpactAt = now;      // sets the body thrashing; see thrash(now)
                    ButcherAudio.onImpact(partsRemoved - 1, chops);
                    com.butcher.config.ButcherStats.get().onChop();
                    enter(Phase.IMPACT, now);
                }
            }
            case IMPACT -> {
                // The last impact is cut short.
                //
                // Every other chop holds so the blade can sit in the cut and the body
                // can settle. On the final one there is nothing to wait for - the run
                // is over and the reveal is what everyone is here for - so holding the
                // full length just froze the cleaver and the head in place for half a
                // second before anything moved again.
                long hold = partsRemoved >= chops
                        ? (long) (impactFor(chopIndex()) * FINAL_HOLD) : impactFor(chopIndex());
                if (since >= hold) {
                    flyingPart = -1;
                    if (partsRemoved >= chops) {
                        // Whatever is left of it bolts, drop and all. A Scatha that
                        // simply stops existing when the last cut lands looks like the
                        // animation ran out; one that leaves has been somewhere.
                        escapeAt = now;
                    }
                    if (partsRemoved < chops) {
                        // The drone that fills the gap, sized to the wind-up and fall
                        // that are about to happen, so it resolves exactly on the blade.
                        ButcherAudio.onBuild(partsRemoved, chops,
                                raiseFor(partsRemoved) + fallFor(partsRemoved));
                        enter(Phase.RAISE, now);
                    }
                    else {
                        // Deliberately here and not a moment earlier: the chime says how
                        // good the drop was, so playing it before the reveal would hand
                        // the answer over exactly the way a chat line would.
                        ButcherAudio.onReveal(reachesHead && result != null ? result.rarity : null);
                        com.butcher.config.ButcherStats.get().onDrop(
                                reachesHead && result != null ? result.rarity : null,
                                result != null && result.outcome == Outcome.BLOCKED);
                        enter(Phase.REVEAL, now);
                        revealStart = now;
                    }
                }
            }
            case REVEAL -> {
                if (since >= revealLengthMs()) {
                    if (reachesHead) {
                        // Fired as the head disappears, a beat BEFORE this screen closes,
                        // so the real pet is already rising by the time the player sees
                        // the world again - the drawing turning into the item.
                        firePetPayoff();
                        enter(Phase.FLASH_OUT, now);
                    } else {
                        enter(Phase.FADE_OUT, now);
                    }
                }
            }
            case FLASH_OUT -> { if (since >= flashMs) { phase = Phase.DONE; close(); } }
            case FADE_OUT -> { if (since >= fadeOutMs) { phase = Phase.DONE; close(); } }
            default -> { }
        }
    }

    private long revealLengthMs() { return reachesHead ? petRevealMs : dropRevealMs; }

    private void enter(Phase p, long now) { phase = p; phaseStart = now; }
    private boolean chopping() { return phase == Phase.RAISE || phase == Phase.FALL || phase == Phase.IMPACT; }
    /** Which part the cleaver is lined up on - during IMPACT that's the one it just took. */
    private int targetPart() { return phase == Phase.IMPACT && flyingPart >= 0 ? flyingPart : partsRemoved; }
    private boolean revealing() { return phase == Phase.REVEAL || phase == Phase.FLASH_OUT || phase == Phase.FADE_OUT; }

    // ================================================================= render
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        long now = System.currentTimeMillis();
        advance(now);

        int sw = this.width, sh = this.height;
        long since = now - phaseStart;

        // Shake the whole picture, not the scene camera.
        //
        // Moving the 3D camera only moves the worm: the cave, the outcrops and the
        // overlays all stayed nailed down, so an impact looked like the animal twitching
        // rather than the room taking a hit. Translating the GUI pose moves everything
        // drawn from here on, which is what a screen shake actually is.
        float shakeX = 0f, shakeY = 0f;
        // Nothing to hit, nothing to shake. A missed swing and the run-off are both
        // moments where the blade met air, so a knock there reads as an impact that did
        // not happen - and shaking the frame while the Scatha is leaving fights the
        // zoom, which is the thing meant to carry the exit.
        float th = escapeAt >= 0 ? 0f : thrash(now);
        if (th > 0f && lastImpactAt >= 0) {
            float ms = now - lastImpactAt;
            float force = SCREEN_SHAKE * Math.min(sw, sh) * th * (1f + SHAKE_GROWTH * tension())
                    * knob(ButcherClient.config == null ? 100 : ButcherClient.config.shakeStrength);
            shakeX = force * (float) Math.sin(ms / 21.0);
            shakeY = force * 0.7f * (float) Math.sin(ms / 14.0 + 1.1);
        }
        // Everything full-screen is drawn this much bigger on every side.
        //
        // Translating the whole picture slides the far edge inwards and leaves a strip
        // of whatever was underneath - which, over a cutscene, is nothing. Overdrawing
        // by the largest offset the shake can ever reach means the edge being uncovered
        // is always still inside the art. Computed from the constants rather than from
        // this frame's offset, so the margin does not itself change as the shake decays.
        shakeMargin = Math.round(SCREEN_SHAKE * Math.min(sw, sh) * (1f + SHAKE_GROWTH)
                * knob(ButcherClient.config == null ? 100 : ButcherClient.config.shakeStrength))
                + 2;

        Matrix3x2fStack pose = g.pose();
        boolean shaking = shakeX != 0f || shakeY != 0f;
        if (shaking) {
            pose.pushMatrix();
            pose.translate(shakeX, shakeY);
        }

        // ---- cavern, edge to edge ----
        // The cave and the outcrops stay 2D on purpose. Only the Scatha is modelled; a
        // painted backdrop behind it is cheaper, holds far more detail per pixel than
        // box geometry, and the camera move is small enough that nothing gives it away.
        if (plainBackdrop()) {
            // Nothing but a dim wash, brought up and taken away on the same beats the
            // cave would have used, so the pacing is unchanged and only the scenery is
            // missing. See PatternConfig.plainBackdrop.
            float veil = phase == Phase.INTRO ? ease(Math.min(1f, since / (float) introMs))
                    : phase == Phase.FADE_OUT ? 1f - ease(Math.min(1f, since / (float) fadeOutMs))
                    : 1f;
            fillFull(g, sw, sh, (int) (222 * veil) << 24 | 0x0A0C12);
        } else {
            drawBackdrop(g, sw, sh);
            drawCaveAtmosphere(g, sw, sh, now);
            drawGemstones(g, sw, sh, now, since);
        }


        float unit = Math.min(sw, sh);
        float partSize = unit * 0.20f;
        float spacing = partSize * 0.78f;
        float chainLeft = sw / 2f - spacing * PARTS / 2f;
        float baseY = sh * 0.635f;

        // The opening is staged rather than everything arriving at once: the cave
        // fades up out of the black the pre-kill veil left behind, then the outcrops
        // slide in, then the Scatha crawls on. Only after all that does the cleaver
        // get to work.
        float slideIn = phase == Phase.INTRO
                ? easeOutCubic(segment(since, introMs, 0.34f, 1f)) : 1f;
        float slideX = lerp(-sw * 0.75f, 0f, slideIn);

        drawScene3D(g, sw, sh, now, since, slideX);
        drawVignette(g, sw, sh, now);
        drawImpactFx(g, sw, sh, now, since, partSize, spacing, chainLeft + slideX, baseY, slideX);
        drawCleaver(g, sw, sh, since, partSize, spacing, chainLeft + slideX, baseY);

        if (revealing()) {
            drawReveal(g, sw, sh, now, partSize, spacing, chainLeft, baseY);
        }
        // After the reveal, not before it. The reveal dims the whole screen to make the
        // drop carry the frame, and that dim was landing on top of the card and greying
        // it out - the one line the player is meant to read.
        if (escapeAt >= 0 && !reachesHead) drawEndCard(g, sw, sh, now);

        // black held over the top of the whole scene and lifted, so the cutscene
        // grows out of the dark the veil left rather than cutting to it
        if (phase == Phase.INTRO) {
            float f = 1f - segment(since, introMs, 0f, 0.42f);
            if (f > 0.004f) fillFull(g, sw, sh, (Math.round(255 * f) << 24));
        }

        if (phase == Phase.FLASH_OUT) {
            int a = (int) (235 * ease(Math.min(1f, since / (float) flashMs)));
            fillFull(g, sw, sh, (a << 24) | 0xFFFFFF);
        }
        // Nothing is drawn over the top on the way out any more.
        //
        // It used to fade to solid black and then a separate HUD element faded that
        // black off again - two fades and a hard handover in between, to end up exactly
        // where a single one would have. The backdrop's own veil already thins out
        // during FADE_OUT, so the cutscene simply becomes transparent and the game is
        // behind it the whole time.

        // The fades above are deliberately INSIDE the shake: a full-screen wash that
        // does not move with everything else would show a gap at the shaken edge.
        if (shaking) pose.popMatrix();
    }

    /** Pull a colour toward white, so a twinkle reads as a glint rather than a blob of its own hue. */
    private static int lighten(int argb, float amount) {
        int out = 0xFF000000;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int c = (argb >>> shift) & 0xFF;
            out |= (Math.round(c + (255 - c) * amount) & 0xFF) << shift;
        }
        return out;
    }

    // ------------------------------------------------- the six-part Scatha
    private float partCenterX(float chainLeft, float spacing, int index) {
        return chainLeft + spacing * (index + 0.5f);
    }

    /**
     * Where segment {@code index} actually is on screen.
     *
     * The worm is geometry now, so the old chain arithmetic no longer says where anything
     * is: it described the sprite row that used to be drawn there. Everything that has to
     * land ON the worm - the slash, the sparks, the blade, the drop lifting off - has to
     * ask the body where it is, or it fires at the position of a worm that is not there.
     */
    private float[] partOnScreen(int index, int sw, int sh, long now, float slideX) {
        float pitch = CutsceneScene.pitch();
        ScathaBody.Pose[] body = ScathaBody.poses(PARTS, pitch, (now - openedAt) / 1000f, thrash(now));
        ScathaBody.Pose p = body[Math.max(0, Math.min(PARTS - 1, index))];
        float rest = (PlayerHeadMesh.HEAD + 2 * PlayerHeadMesh.HAT_INFLATE) / 2f;
        // Includes the escape, so a segment's screen position is where it actually is
        // while the worm is bolting - not where it was standing before it left.
        float[] esc = escapeOffset(now);
        return scenePoint(p.x() + esc[0], rest + p.y() + esc[1], p.z(), sw, sh, now, slideX);
    }

    /**
     * The 2D flourishes that sit on top of the cut: the slash, the white frame and the
     * sparks. The worm itself is geometry now (see {@link #drawScene3D}); these stay flat
     * because they are camera-facing effects, not objects in the cave.
     */
    private void drawImpactFx(GuiGraphicsExtractor g, int sw, int sh, long now, long since,
                              float partSize, float spacing, float chainLeft, float baseY,
                              float slideX) {
        // the slash, the screen flash and the sparks struck off the block
        if (phase == Phase.IMPACT) {
            float t = Math.min(1f, since / (float) impactMs);
            float[] hit = partOnScreen(targetPart(), sw, sh, now, slideX);
            float x = hit[0];
            baseY = hit[1];
            if (t < 0.55f) {
                float k = t / 0.55f;
                float fw = partSize * lerp(2.2f, 3.4f, k);
                float fh = fw * (192f / 512f) * lerp(1.2f, 0.25f, k);
                // Slashed, not laid across.
                //
                // The flash sprite is a horizontal streak and was drawn as one, so every
                // cut left the same level line through the middle of a segment. Turning
                // it near-vertical and varying the lean per chop is what makes a cut read
                // as a stroke someone made rather than a mark that appeared.
                Matrix3x2fStack pose = g.pose();
                pose.pushMatrix();
                pose.translate(x, baseY);
                // Negated: screen Y points down, so a positive rotation here runs
                // clockwise. Without this the angles come out mirrored about the
                // horizontal - 70 degrees would appear where 110 should be.
                pose.rotate((float) -Math.toRadians(slashAngle(chopIndex())));
                drawTex(g, CUT_FLASH, -fw / 2f, -fh / 2f, fw, fh);
                pose.popMatrix();
            }
            if (since < 60) fillFull(g, sw, sh, 0x44FFFFFF);

            // The struck sparks are gone. They belong to a blade hitting stone, and the
            // cleaver is going through an animal - they read as the cut landing on the
            // block rather than on the Scatha, which is the opposite of the point.
        }
    }

    // ------------------------------------------------- cleaver
    private void drawCleaver(GuiGraphicsExtractor g, int sw, int sh, long since,
                             float partSize, float spacing, float chainLeft, float baseY) {
        // Once the last chop is done the cleaver withdraws for a moment rather than
        // vanishing mid-cut, so it leaves the frame under its own steam.
        boolean missing = missedSwing
                && System.currentTimeMillis() - escapeAt < MISS_FOLLOW_MS;
        boolean exiting = phase == Phase.REVEAL && (since < cleaverExitMs || missing);
        if (!chopping() && !exiting) return;
        float cs = Math.min(sw, sh) * CLEAVER_FRAC;

        // Every frame is drawn about the same pivot, so anchoring the sprite by its
        // cutting edge once is enough - the blade swings into exactly this point.
        float cutX = partCenterX(chainLeft, spacing, exiting ? lastCutPart : targetPart());
        float cutY = baseY;
        float left = cutX - cs * CLEAVER_EDGE_FX;
        float top = cutY - cs * CLEAVER_EDGE_FY;

        // The blade angle comes off one spline covering the whole chop - wind-up, hang,
        // whip, arrest, recoil - rather than a cubic ease per phase. See SwingCurve for
        // why: an ease cannot say where the blade is a THIRD of the way down, and that is
        // the part that makes a chop read as a chop.
        float swing;
        float dy = 0f;
        switch (phase) {
            case RAISE, FALL, IMPACT -> {
                int ci = chopIndex();
                float into = switch (phase) {
                    case RAISE -> since;
                    case FALL -> raiseFor(ci) + since;
                    default -> raiseFor(ci) + fallFor(ci) + since;
                };
                swing = chopCurve().at(into);
                dy = chopLift().at(into) * cs;
                // The blade trembles at the top before it comes down.
                //
                // Held perfectly still, the wind-up reads as a pause in the animation.
                // A small high-frequency wobble that grows as the hold goes on reads as
                // effort - someone bracing - and it makes the whip that follows land
                // harder by contrast. Only during the wind-up: once the blade is moving
                // there is nothing to brace against.
                if (phase == Phase.RAISE) {
                    float held = Math.min(1f, since / (float) Math.max(1L, raiseFor(ci)));
                    float grip = Math.max(0f, (held - 0.45f) / 0.55f);
                    swing += TREMBLE_DEG * grip * grip
                            * (float) Math.sin(since / TREMBLE_PERIOD_MS * Math.PI * 2);
                }
                // The first chop of the run drops in from off the top, already cocked.
                if (partsRemoved == 0 && phase == Phase.RAISE) {
                    dy += lerp(-cs * 0.85f, 0f, easeOutCubic(Math.min(1f, since / (float) raiseMs)));
                }
            }
            default -> {
                if (missedSwing) {
                    // Nothing stopped it. A blade that meets no resistance carries
                    // through and keeps going - pulling it neatly back out of thin air
                    // is what made the miss look like a cancelled animation rather than
                    // a swing that failed.
                    float t = Math.min(1f, (System.currentTimeMillis() - escapeAt)
                            / (float) MISS_FOLLOW_MS);
                    swing = lerp(CLEAVER_BURIED_DEG, MISS_END_DEG, easeOutCubic(t));
                    dy = easeIn(t) * cs * 1.6f;     // and down, out of frame
                } else {
                    // REVEAL after a hit: hauled back out of the block and off the top.
                    float t = easeIn(Math.min(1f, since / (float) cleaverExitMs));
                    swing = lerp(CLEAVER_BURIED_DEG, CLEAVER_BURIED_DEG - 22f, t);
                    dy = -t * cs * 1.15f;
                }
            }
        }

        // Anchored to the segment it is cutting, in scene space, so it tracks the orbit.
        int part = exiting ? lastCutPart : targetPart();
        long now = System.currentTimeMillis();
        float slide = chainLeft - (sw / 2f - spacing * PARTS / 2f);
        float[] at = partOnScreen(part, sw, sh, now, slide);

        // Glide to the next segment rather than appearing at it.
        //
        // targetPart() steps the instant a cut lands, so the whole cleaver used to jump
        // a segment's width between two frames - the blade looked like it was being
        // teleported into position rather than carried there. Easing across the wind-up
        // means the move happens while the arm is going back up, which is when a real
        // one would happen.
        if (!exiting && phase == Phase.RAISE && part > 0) {
            float[] prev = partOnScreen(part - 1, sw, sh, now, slide);
            float carry = 1f - ease(Math.min(1f, since / (float) Math.max(1L, raiseFor(chopIndex()))));
            at[0] += (prev[0] - at[0]) * carry;
            at[1] += (prev[1] - at[1]) * carry;
        }
        // Land the edge ON the segment, not above it.
        //
        // This used to lift the anchor by most of a segment's height, from back when the
        // anchor was the middle of a sprite. It is now the point the blade's cutting
        // edge arrives at, so the same offset simply held the blade a segment clear of
        // the worm for the whole swing - the diagnostic had it at y 187 with the body at
        // 303, a fifth of the screen adrift. Just enough bias to bite into the top of
        // the segment rather than pass through its centre.
        at[1] -= sceneScale(sw) * PlayerHeadMesh.HEAD * 0.28f;

        meshes.begin();
        // The scene camera, re-based to the origin and rolled by the swing.
        //
        // The roll used to be pose.rotate() around this anchor, with the mesh drawn
        // underneath it. That rendered the whole cleaver as three hairlines - handle,
        // ferrule and blade each collapsed to a streak - while every other part of the
        // pipeline checked out offline. Rolling the camera instead keeps the entire
        // transform inside ModelProjection, where it is a 2D rotation of vectors we
        // already have and can be tested without launching the game.
        //
        // .at(0,0,0) because the anchor below has already accounted for where the camera
        // is; subtracting its position again would shift the blade twice.
        meshes.camera(camera(now, sw)
                .at(0f, 0f, 0f)
                .zoom(CutsceneScene.CLEAVER_SCALE)
                .roll(swing));
        meshes.add(CutsceneScene.cleaverMesh(), PropArt.TEX_CLEAVER,
                0f, 0f, 0f, 0f, 0xFFFFFFFF, true, false, true);
        // Where the wrist goes.
        //
        // The mesh's origin is the pivot and the blade sits REACH units out along +X, so
        // drawing it AT the cut point would put the blade a whole reach to the right of
        // the segment. Instead the wrist is placed so that at the buried angle - the end
        // of the swing - the blade's far end lands exactly on the cut. Fixed for the
        // whole swing, so the blade sweeps down INTO the segment rather than dragging
        // its tip along the cut point the entire way.
        float reach = CleaverMesh.REACH * meshes.camera().size();
        double buried = Math.toRadians(CLEAVER_BURIED_DEG);
        float wristX = at[0] - (float) (reach * Math.cos(buried));
        float wristY = at[1] + dy - (float) (reach * Math.sin(buried));

        // What the last real draw actually asked for, for "/butcher debug".
        //
        // Every static check on the cleaver passes - mesh, texture, projected face size -
        // and it still does not appear, so the remaining unknown is what these numbers
        // are at the moment of the call rather than in a harness.
        meshes.flush(g, wristX, wristY, 0xFF000000, Math.max(1.5f, sw / 700f));

        // Only chop frames are worth recording. The first report came from the REVEAL
        // exit, where the blade is FLYING OFF SCREEN on purpose (wrist y -178 on a
        // 505-tall screen) - true, and useless. What matters is where it is while it is
        // supposed to be visible, and whether any face actually got blitted.
        if (chopping()) {
            float camSize = meshes.camera().size();
            lastCleaverReport = String.format(java.util.Locale.ROOT,
                    "phase %s swing %.1f dy %.1f at %.0f,%.0f wrist %.0f,%.0f "
                            + "size %.2f reach %.0f screen %dx%d queued %d DRAWN %d %s",
                    phase, swing, dy, at[0], at[1], wristX, wristY,
                    camSize, reach, sw, sh, meshes.size(), meshes.lastDrawn(),
                    (wristX < -200 || wristX > sw + 200 || wristY < -200 || wristY > sh + 200)
                            ? "<< OFF SCREEN" : "on screen");
        }
    }

    /**
     * A one-line record of the most recent cleaver draw, read back by /butcher debug.
     *
     * Static so it survives the screen closing - the cutscene is gone by the time
     * anyone can type a command.
     */
    public static volatile String lastCleaverReport = "(no cleaver drawn yet)";

    /**
     * How much longer each chop takes than the reference pace.
     *
     * The first cut is brisk and the last one is drawn out, which is most of what makes
     * a run feel like it is building rather than repeating. The multipliers are chosen
     * to average about 1, so a five-chop run still lands close to the length the speed
     * slider asks for - the pacing changes shape, not total duration.
     */
    private static final float CHOP_FAST = 0.72f, CHOP_SLOW = 1.42f;

    /** Which chop the phase machine is currently in the middle of. */
    private int chopIndex() {
        return phase == Phase.IMPACT ? Math.max(0, partsRemoved - 1) : partsRemoved;
    }

    /** The pacing multiplier for chop {@code i}. */
    private float chopStretch(int i) {
        if (chops <= 1) return 1f;
        return lerp(CHOP_FAST, CHOP_SLOW, Math.min(1f, i / (float) (chops - 1)));
    }

    private long raiseFor(int i) { return Math.max(16L, (long) (raiseMs * chopStretch(i))); }
    private long fallFor(int i) { return Math.max(16L, (long) (fallMs * chopStretch(i))); }
    private long impactFor(int i) { return Math.max(16L, (long) (impactMs * chopStretch(i))); }

    /**
     * Which way each chop's slash leans, in degrees from horizontal.
     *
     * Dealt once per run so a cut does not change angle while it is on screen, and so
     * two consecutive chops are visibly different strokes rather than the same one
     * repeated.
     *
     * Constrained to 70..120 degrees: near vertical, never level, and never so far over
     * that it stops looking like a downward stroke. Because the mark is a straight line
     * through the hit point, its two ends are automatically 180 degrees apart - one in
     * the upper-left or upper-right quadrant and the other diagonally opposite it.
     */
    private float[] slashAngles;

    private float slashAngle(int chop) {
        if (slashAngles == null) {
            slashAngles = new float[PARTS];
            for (int i = 0; i < slashAngles.length; i++) {
                slashAngles[i] = 70f + rng.nextFloat() * 50f;
            }
        }
        return slashAngles[Math.max(0, Math.min(slashAngles.length - 1, chop))];
    }

    /** Fraction of a normal impact the LAST chop holds for before the reveal starts. */
    private static final float FINAL_HOLD = 0.3f;

    /** How far the blade wobbles while braced at the top, and how fast. */
    private static final float TREMBLE_DEG = 1.7f;
    private static final float TREMBLE_PERIOD_MS = 52f;

    /** Where the blade ends up when it swings through nothing, and how long that takes. */
    private static final float MISS_END_DEG = 62f;
    private static final long MISS_FOLLOW_MS = 620;

    /**
     * Blade angle at the top of the wind-up, and at the end of the cut.
     *
     * The cut used to finish at -4, which is very nearly level - so the edge went
     * through the middle of a segment sideways and stopped there, like a knife laid
     * across it. Finishing well past level makes it a slash that passes THROUGH the
     * piece and keeps going, which is the only way a single cut reads as having
     * separated anything.
     */
    private static final float CLEAVER_RAISED_DEG = -96f;
    private static final float CLEAVER_BURIED_DEG = 30f;

    /**
     * How far into the fall the blade has still barely moved.
     *
     * This one number is most of what the spline buys over an ease. A real swing hangs
     * at the top and then goes all at once; a cubic ease-in spreads the motion evenly
     * enough that the blade appears to be lowered rather than swung.
     */
    private static final float HANG_FRACTION = 0.34f;
    /** How much of the arc is covered during that hang. */
    private static final float HANG_TRAVEL = 0.12f;

    /** Degrees the blade rebounds on landing, and how fast that rings down. */
    private static final float RECOIL_DEG = 7.5f;
    private static final float RECOIL_HZ = 0.011f;      // cycles per millisecond
    private static final float RECOIL_DECAY = 0.006f;   // e-folds per millisecond

    private SwingCurve chopAngle;
    private SwingCurve chopLift;
    /** Which chop the cached curves were built for; they are rebuilt when it changes. */
    private int curvesFor = -1;

    /**
     * The blade's angle through one chop, in milliseconds from the start of RAISE.
     *
     * Built once and cached rather than per frame: the phase lengths are fixed at
     * construction, so the shape never changes during a run. Only the starting angle
     * differs between the first chop and the rest, and that is handled by starting the
     * curve from the buried angle - on chop zero the blade is off-screen anyway.
     */
    private SwingCurve chopCurve() {
        int i = chopIndex();
        if (chopAngle == null || curvesFor != i) {
            curvesFor = i;
            chopLift = null;
            long raiseMs = raiseFor(i), fallMs = fallFor(i), impactMs = impactFor(i);
            float land = raiseMs + fallMs;
            chopAngle = SwingCurve.builder()
                    // Pulling out of the last cut.
                    .key(0f, CLEAVER_BURIED_DEG)
                    // The top of the wind-up: flat, so the blade settles there instead of
                    // sailing past and unwinding backwards.
                    .flat(raiseMs, CLEAVER_RAISED_DEG)
                    // The hang. Barely any travel for the first third of the fall.
                    .key(raiseMs + fallMs * HANG_FRACTION,
                            CLEAVER_RAISED_DEG + (CLEAVER_BURIED_DEG - CLEAVER_RAISED_DEG) * HANG_TRAVEL)
                    // Arrested by the block.
                    .flat(land, CLEAVER_BURIED_DEG)
                    .flat(land + impactMs, CLEAVER_BURIED_DEG)
                    .spring(land, RECOIL_DEG, RECOIL_HZ, RECOIL_DECAY)
                    .build();
        }
        return chopAngle;
    }

    /**
     * The blade's vertical shove through one chop, as a fraction of the cleaver's size.
     *
     * Separate from the angle because it is a different quantity in different units, and
     * because the recoil on the angle and the jolt on the position want their own decay -
     * a blade that bounces up and rotates back in lockstep looks hinged.
     */
    private SwingCurve chopLift() {
        if (chopLift == null) {
            int i = chopIndex();
            long raiseMs = raiseFor(i), fallMs = fallFor(i), impactMs = impactFor(i);
            float land = raiseMs + fallMs;
            chopLift = SwingCurve.builder()
                    .key(0f, 0f)
                    // Lifts clear of the cut on the way up.
                    .flat(raiseMs * 0.55f, -0.10f)
                    .key(raiseMs, 0f)
                    .flat(land, 0f)
                    .flat(land + impactMs, 0f)
                    .spring(land, 0.055f, RECOIL_HZ * 0.8f, RECOIL_DECAY * 1.4f)
                    .build();
        }
        return chopLift;
    }


    // ------------------------------------------------- the 3D scene

    /**
     * Camera tilt.
     *
     * NEGATIVE looks down; positive looks up. That is not obvious and it was wrong here
     * for a long time: with a positive pitch the projection culls a box's top face and
     * keeps its bottom, so the whole scene was being viewed from underneath. Raising the
     * number made it worse rather than better. Check it with
     * {@code nz2 = -ny*sin(pitch)} - at pitch +27 the top face has nz2 -0.45 and is
     * dropped, at -20 it is +0.34 and drawn.
     */
    private static final float CAM_PITCH = -20f;
    /** The camera drifts across this arc over the whole run - a slow orbit, not a spin. */
    private static final float ORBIT_FROM = -15f, ORBIT_TO = 17f;
    /** Fraction of the screen width the worm should span. */
    private static final float WORM_ON_SCREEN = 0.52f;
    /** Where the scene's origin sits vertically. */
    private static final float SCENE_Y = 0.60f;

    /**
     * How far round the orbit the camera is, from the moment the screen opened.
     *
     * Deliberately driven by wall time rather than by phase, so the drift is continuous
     * across cuts. Tying it to the chops would make the camera lurch every time the blade
     * lands, which reads as a mistake rather than as a move.
     */
    private float cameraYaw(long now) {
        // Measured against the configured run length, so the arc is the same shape
        // whatever speed the cutscene is set to.
        return lerp(ORBIT_FROM, ORBIT_TO, ease(runFraction(now)));
    }

    /** Wall time of the last blade landing, for {@link #thrash}. */
    private long lastImpactAt = -1;
    /** How long a spasm takes to die away. */
    private static final float THRASH_DECAY_MS = 620f;

    /**
     * How hard the body is writhing, 0 at rest and 1 mid-spasm.
     *
     * One scalar that scales the whole wave, the way SkyMaxx layers thrash / rear / dive
     * on top of its base motion. It spikes when the blade lands and decays, which is what
     * makes a cut land as something done TO the animal rather than an edit of the frame.
     */
    private float thrash(long now) {
        if (lastImpactAt < 0 || escapeAt >= 0) return 0f;
        float t = (now - lastImpactAt) / THRASH_DECAY_MS;
        if (t >= 1f) return 0f;
        float u = 1f - t;
        return u * u;
    }

    private float sceneScale(int sw) {
        return sw * WORM_ON_SCREEN / Math.max(1f, CutsceneScene.wormHalfLength(PARTS) * 2f);
    }

    /** Lens scale the run opens at, before any chop has landed. */
    private static final float ZOOM_BASE = 0.94f;
    /** How much closer the camera gets with every chop. The build-up. */
    private static final float ZOOM_PER_CHOP = 0.085f;
    /** How long a chop's push-in takes to settle, as a fraction of the impact. */
    private static final float ZOOM_SETTLE = 1.15f;
    /** Where the lens snaps back to when the drop is revealed. */
    private static final float ZOOM_REVEAL = 0.74f;
    /** How fast it gets there. Short - the pull-back should feel like a release. */
    private static final long ZOOM_OUT_MS = 420;

    /**
     * The same pull-back, much slower, when the run ends with the Scatha getting away.
     *
     * A reveal that pulls back in 420ms is right when there is a drop arriving to look
     * at - the snap hands the frame over. An escape has nothing arriving: the worm is
     * leaving and the camera is letting it go, and at 420ms that read as a cut rather
     * than a move. Slow enough here to actually follow the thing out of frame.
     */
    private static final long ZOOM_OUT_ESCAPE_MS = 980;
    /** How far past its target a zoom overshoots before settling. 0 is no bounce. */
    private static final float BOUNCE = 3.1f;
    /**
     * Peak camera shake when the blade lands, in world units.
     *
     * Was 0.9 at two fairly high frequencies, which combined with the body's own spasm
     * to make the whole picture buzz for half a second after every chop. A knock should
     * be one shove that settles, not a vibration.
     */
    private static final float SHAKE = 0.16f;
    /** Peak whole-screen shake, as a fraction of the smaller screen dimension. */
    private static final float SCREEN_SHAKE = 0.017f;
    /** How much harder the last chop hits the camera than the first. */
    private static final float SHAKE_GROWTH = 1.6f;

    /**
     * How far a cut piece is thrown sideways, how high it pops, and how far it falls.
     *
     * Tuned so the piece is clear of the frame before it is allowed to vanish. It used
     * to fade out while still plainly on screen, which reads as the game giving up on
     * it - worse than never animating it at all. The fall is now big enough to carry it
     * past the bottom edge within the impact, and the fade is held back until it is
     * nearly there.
     */
    private static final float CUT_THROW = 16f;
    private static final float CUT_LIFT = 15f;
    private static final float CUT_FALL = 86f;
    /** How far a cut piece turns on its way down. */
    private static final float CUT_SPIN_DEG = 220f;

    /**
     * The one camera the whole scene is drawn through.
     *
     * <h3>Why this is a position and not three loose floats</h3>
     *
     * It used to be {@code cameraYaw(now)}, a constant pitch and a scale, passed separately
     * to every {@code meshes.add} and to {@link #scenePoint}. That works right up until the
     * camera needs to <em>move</em>: with no position, the only shot available is a spin
     * about the scene origin, so the worm cannot be tracked as it shortens, nothing can
     * push in, and an impact cannot shove the frame.
     *
     * Everything that draws into the scene - the worm, the cleaver, the sparks anchored to
     * a part - now takes this same camera, which is the only thing keeping them stuck to
     * each other while it moves.
     *
     * Three moves are layered on the orbit:
     *
     * <ul>
     *   <li><b>Track.</b> The camera slides part of the way towards the segment about to be
     *       cut. Only part of the way, and eased, so the action stays near the middle
     *       without the body appearing to slide out from under itself.
     *   <li><b>Push.</b> A slow zoom across the run. Orthographic, so this is a lens scale
     *       rather than a dolly, but it reads the same at this depth.
     *   <li><b>Shake.</b> A decaying sinusoid off {@link #thrash}, so the blade landing
     *       moves the camera as well as the animal.
     * </ul>
     */
    private ModelProjection.Camera camera(long now, int sw) {
        ModelProjection.Camera cam =
                ModelProjection.Camera.of(cameraYaw(now), CAM_PITCH, sceneScale(sw))
                        .zoom(zoomFactor(now));

        // Keep what is LEFT of the worm centred.
        //
        // The body's world positions never move - the cutscene just stops drawing the
        // segments that have been taken off - so as the run goes on, the surviving part
        // of the worm sits further and further right of the scene origin. It used to
        // track the segment being cut instead, which made that worse: by the last chop
        // the animal was well off to one side.
        //
        // Eased from the previous centre rather than snapped, because partsRemoved is a
        // step and a step in the camera is a visible lurch on every cut.
        float target = remainingCentre(partsRemoved);
        float from = remainingCentre(Math.max(0, partsRemoved - 1));
        float settle = lastImpactAt < 0 ? 1f
                : Math.min(1f, (now - lastImpactAt) / (float) Math.max(1L, impactMs));
        float aim = lerp(from, target, ease(settle));

        float shake = 0f, lift = 0f;
        float th = thrash(now);
        if (th > 0f) {
            // A knock, not a drift. At the frequency this ran before, half a cycle took
            // most of a second and the whole scene visibly slid sideways after every cut
            // before coming back - which read as the worm moving, not as an impact.
            // Harder every slice. The knock is the loudest thing the camera does, so
            // scaling it with the run is the most direct way to make the last cut feel
            // heavier than the first.
            float ms = now - lastImpactAt;
            float force = SHAKE * (1f + SHAKE_GROWTH * tension());
            shake = force * th * (float) Math.sin(ms / 18.0);
            lift = force * 0.5f * th * (float) Math.sin(ms / 13.0);
        }
        return cam.at(aim + shake, lift, 0f);
    }

    /**
     * The middle of the segments still attached, in world units.
     *
     * Only the ones from {@code removed} up to the head are still drawn, so that span -
     * not the whole original body - is what should sit in the middle of the screen.
     */
    private static float remainingCentre(int removed) {
        int first = Math.min(removed, PARTS - 1);
        return (CutsceneScene.partX(first, PARTS) + CutsceneScene.partX(PARTS - 1, PARTS)) / 2f;
    }

    /**
     * How close the camera is right now.
     *
     * <h3>A step per chop, not a slow slide</h3>
     *
     * The push-in used to be one smooth ramp across the whole run, which is a move you
     * stop noticing about two seconds in. Stepping it at every impact ties the camera to
     * the thing building the tension: each cut visibly gains ground, and because the
     * steps are discrete you feel the run getting closer rather than merely being
     * closer.
     *
     * <h3>Overshoot is what makes it read as a push rather than a fade</h3>
     *
     * Each step lands with {@link #easeOutBack}, which goes slightly past the new zoom
     * and settles back. A plain ease between two scales is indistinguishable from a
     * cross-fade; the overshoot is the entire difference between "the camera moved" and
     * "the picture got bigger".
     *
     * <h3>And then it lets go</h3>
     *
     * When the drop is revealed the lens pulls back past where it started, fast, with
     * the same bounce. After five chops of creeping inwards, releasing is the payoff -
     * and it hands the frame back to the drop, which needs the room.
     */
    private float zoomFactor(long now) {
        float per = ZOOM_PER_CHOP * knob(
                ButcherClient.config == null ? 100 : ButcherClient.config.zoomStrength);
        float target = ZOOM_BASE + per * partsRemoved;
        float from = ZOOM_BASE + per * Math.max(0, partsRemoved - 1);
        float settle = lastImpactAt < 0 ? 1f
                : Math.min(1f, (now - lastImpactAt) / (float) Math.max(1L, (long) (impactMs * ZOOM_SETTLE)));
        float zoom = lerp(from, target, easeOutBack(settle));

        if (revealing() && revealStart > 0) {
            long span = escaped() ? ZOOM_OUT_ESCAPE_MS : ZOOM_OUT_MS;
            float out = Math.min(1f, (now - revealStart) / (float) span);
            zoom = lerp(zoom, ZOOM_REVEAL, easeOutBack(out));
        }
        return zoom;
    }

    /**
     * Eases to 1 but overshoots on the way, then settles back.
     *
     * The standard "back" curve. {@link #BOUNCE} is how far past it goes: at 1.7 it
     * overshoots about 10%, which is enough to feel like momentum and little enough not
     * to look like a wobble.
     */
    private static float easeOutBack(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float c1 = BOUNCE, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /**
     * How far into the run we are, 0 at the first chop and 1 at the last.
     *
     * Drives everything that is meant to tighten as the cutscene goes on. Measured in
     * chops rather than in time, because the number of chops is the drop and the length
     * of the run is not.
     */
    private float tension() {
        return chops <= 1 ? 1f : Math.min(1f, partsRemoved / (float) (chops - 1));
    }

    /** How far past every screen edge to draw, so a shake cannot expose the outside. */
    private int shakeMargin;

    /** A full-screen fill that survives the shake. */
    private void fillFull(GuiGraphicsExtractor g, int sw, int sh, int argb) {
        g.fill(-shakeMargin, -shakeMargin, sw + shakeMargin, sh + shakeMargin, argb);
    }

    /** A percentage setting as a multiplier, clamped so a hand-edited config cannot break the shot. */
    private static float knob(int percent) {
        return Math.max(0f, Math.min(3f, percent / 100f));
    }

    /** How far through the configured run length we are, 0..1. */
    private float runFraction(long now) {
        int wanted = ButcherClient.config == null
                ? (int) BASE_TOTAL_MS : ButcherClient.config.scopeDurationMs;
        return Math.max(0f, Math.min(1f, (now - openedAt) / (float) Math.max(1, wanted)));
    }

    /** Draws the cavern, the gems and whatever is left of the Scatha. */
    private void drawScene3D(GuiGraphicsExtractor g, int sw, int sh, long now, long since, float slideX) {
        // On a pet the head IS the drop, so it must not run off - it lifts away as the
        // reveal instead. Everything else does leave.
        boolean headGone = reachesHead && revealing();
        float seconds = (now - openedAt) / 1000f;

        // The dodge, and then the bolt.
        //
        // Two separate moves with different shapes on purpose: the duck is fast and
        // eases out, because it is a reaction; the run is slow to start and accelerates,
        // because it is a decision. Doing both with one curve made it look like the worm
        // was being dragged off rather than leaving.
        // A pet leaves nothing behind to flee - the head is the prize and it rises.
        float escY = 0f, escX = 0f;
        if (escapeAt >= 0 && !reachesHead) {
            float e = escapeProgress(now);
            float duck = easeOutCubic(Math.min(1f, e / 0.18f));
            escY = (dodgeUp ? DODGE_UNITS : -DODGE_UNITS) * duck;
            float run = easeIn(Math.min(1f, Math.max(0f, (e - 0.10f) / 0.90f)));
            escX = run * ESCAPE_UNITS;
        }

        meshes.begin();
        meshes.camera(camera(now, sw));
        for (CutsceneScene.Item item : CutsceneScene.build(
                partsRemoved, PARTS, headGone,
                ScathaModels.textureName(ScathaSkins.Part.HEAD),
                ScathaModels.textureName(ScathaSkins.Part.BODY),
                seconds, thrash(now))) {
            // Culled: a worm segment is a closed box, so its far wall is never seen and
            // projecting it only costs a face in the global sort.
            // Fade as it leaves, so the tail end of the escape is not a hard cut.
            int tint = item.tint();
            if (escapeAt >= 0 && !reachesHead) {
                float gone = Math.max(0f, (escapeProgress(now) - 0.72f) / 0.28f);
                int a = Math.round(((tint >>> 24) & 0xFF) * (1f - gone));
                tint = (a & 0xFF) << 24 | (tint & 0xFFFFFF);
            }
            meshes.add(item.mesh(), item.texture(), item.yaw(),
                    item.x() + escX, item.y() + escY, item.z(), tint, item.outlined(), false, true);
        }
        // The part that just came off, arcing away. Placed in world units alongside
        // everything else rather than as a sprite, so it is lit, outlined and
        // depth-sorted against the cave like the segment it used to be.
        if (phase == Phase.IMPACT && flyingPart >= 0) {
            float t = Math.min(1f, since / (float) impactMs);
            float fly = easeOutCubic(t);
            // Thrown clear and falling. The horizontal kick used to be 26 units - most of
            // a body length - which read as the piece teleporting rather than being flung,
            // so it is now a short shove and gravity does the rest.
            float fx = CutsceneScene.partX(flyingPart, PARTS) - fly * CUT_THROW;
            float fy = CutsceneScene.GROUND_Y + PlayerHeadMesh.HEAD / 2f
                    + fly * CUT_LIFT - t * t * CUT_FALL;
            // Opaque until the last quarter, so it is off the bottom of the screen by
            // the time it starts to go.
            float keep = t < 0.75f ? 1f : 1f - (t - 0.75f) / 0.25f;
            int fade = Math.round(255 * keep) << 24 | 0xFFFFFF;
            // Tumbling. A cut piece that keeps its bearing looks placed; one that spins
            // looks thrown. Eased out, so it whips away from the blade and slows as it
            // falls rather than rotating at a constant rate like a turntable.
            float spin = CUT_SPIN_DEG * fly;
            meshes.add(CutsceneScene.segmentMesh(), ScathaModels.textureName(ScathaSkins.Part.BODY),
                    spin, fx, fy, -fly * 7f, fade, true, false, true);
        }

        // The Scatha is nearly black and the cavern behind it is grey stone, so the
        // silhouette is doing real work here rather than being decoration.
        meshes.flush(g, sw / 2f + slideX, sh * SCENE_Y, 0xFF000000, Math.max(1.5f, sw / 640f));
    }

    /** How dark the frame's edges get by the last chop. */
    private static final float VIGNETTE_MAX = 0.82f;
    /** How much of that is already there before a single cut has landed. */
    private static final float VIGNETTE_BASE = 0.12f;

    /**
     * Closes the frame in as the run goes on.
     *
     * The other half of the build-up, and the cheaper half: the zoom says the camera is
     * getting closer, the vignette says there is less and less of anything else worth
     * looking at. Together they narrow the picture onto the worm without moving it.
     *
     * Follows {@link #tension} - chops taken, not time elapsed - so a one-chop run stays
     * open and a five-chop run really does close down. And it eases across each impact
     * rather than stepping, because a step in a full-screen darkening is a flicker.
     *
     * Drawn over the scene but under the reveal, so the reveal's own dim still reads on
     * top of it.
     */
    private void drawVignette(GuiGraphicsExtractor g, int sw, int sh, long now) {
        float settle = lastImpactAt < 0 ? 1f
                : Math.min(1f, (now - lastImpactAt) / (float) Math.max(1L, impactMs));
        float was = chops <= 1 ? 1f : Math.min(1f, Math.max(0, partsRemoved - 1) / (float) (chops - 1));
        float t = lerp(was, tension(), ease(settle));

        float strength = (VIGNETTE_BASE + (VIGNETTE_MAX - VIGNETTE_BASE) * t) * knob(
                ButcherClient.config == null ? 100 : ButcherClient.config.vignetteStrength);
        // Fade the whole thing with the opening, so it does not pop in over the black.
        if (phase == Phase.INTRO) strength *= ease(Math.min(1f, (now - phaseStart) / (float) introMs));
        int alpha = Math.round(255 * Math.max(0f, Math.min(1f, strength)));
        if (alpha < 4) return;

        Identifier id = TextureBank.id(ButcherClient.TEX_VIGNETTE);
        if (id == null) return;
        drawTexTinted(g, id, -shakeMargin, -shakeMargin,
                sw + shakeMargin * 2, sh + shakeMargin * 2, alpha << 24);
    }

    private static boolean plainBackdrop() {
        return ButcherClient.config != null && ButcherClient.config.plainBackdrop;
    }

    /**
     * The cavern, scaled to cover the screen without distorting it.
     *
     * It used to be blitted straight to {@code (0,0,sw,sh)}. The art is 16:9, so on a
     * 21:9 ultrawide that is a 33% horizontal stretch - every stalactite splayed, every
     * torch pool an oval, and all the fine rock detail smeared sideways until the wall
     * reads as a flat gradient. It looked like the cave had no detail; it had plenty,
     * just pulled out of shape.
     *
     * Scaled by whichever axis needs the most and centred, so the proportions are always
     * right and the overflow falls off an edge instead. Centred rather than top-aligned
     * because the interesting half is the middle - the ceiling teeth and the floor mound
     * are both closer to the centre than to the edges, so trimming symmetrically loses
     * least.
     */
    private void drawBackdrop(GuiGraphicsExtractor g, int sw, int sh) {
        float tw = CaveArt.CAVE_W, th = CaveArt.CAVE_H;
        int fw = sw + shakeMargin * 2, fh = sh + shakeMargin * 2;
        float scale = Math.max(fw / tw, fh / th);
        float w = tw * scale, h = th * scale;
        drawTex(g, cave(), (sw - w) / 2f, (sh - h) / 2f, w, h);
    }

    /**
     * Where a point on the backdrop lands on screen, as {@code {x, y}}.
     *
     * Everything animated on top of the cave - the torch flicker, the glow, the embers -
     * is positioned by a fraction of the BACKDROP, and the backdrop no longer fills the
     * screen edge to edge (see {@link #drawBackdrop}). Multiplying those fractions by the
     * screen size, which is what this used to do, leaves the effects floating away from
     * the torches they belong to on any aspect ratio but 16:9. This is the one conversion
     * they all go through, so they cannot drift apart again.
     */
    private float[] onBackdrop(float fx, float fy, int sw, int sh) {
        float tw = CaveArt.CAVE_W, th = CaveArt.CAVE_H;
        float scale = Math.max(sw / tw, sh / th);
        float w = tw * scale, h = th * scale;
        return new float[]{(sw - w) / 2f + fx * w, (sh - h) / 2f + fy * h};
    }

    /** Screen position of a point in the scene, following the camera. */
    private float[] scenePoint(float x, float y, float z, int sw, int sh, long now, float slideX) {
        float[] p = ModelProjection.projectPoint(x, y, z, camera(now, sw));
        return new float[]{sw / 2f + slideX + p[0], sh * SCENE_Y + p[1]};
    }


    // ------------------------------------------------- gemstones
    /**
     * The two outcrops slide in from their own sides as the cutscene opens and then
     * stay put, framing the bottom corners for the rest of the run. They arrive a
     * little quicker than the worm does, so the stage is set before the Scatha
     * crawls onto it.
     *
     * Crystals go down first and the rock on top of them at the same rect, so the
     * rock buries their roots and they read as growing out of the stone rather than
     * standing in front of it.
     */
    private void drawGemstones(GuiGraphicsExtractor g, int sw, int sh, long now, long since) {
        float h = sh * GEM_FRAC;
        float w = h * GEM_TEX_RATIO;
        float y = sh - h;           // the rock runs right down to the bottom edge

        float t = phase == Phase.INTRO
                ? easeOutCubic(segment(since, introMs, 0.26f, 0.92f)) : 1f;

        // both rest slightly off their own edge, so they read as carrying on past the screen
        float leftRest = -w * 0.18f;
        float rightRest = sw - w * 0.82f;
        float leftX = lerp(leftRest - w * 1.15f, leftRest, t);
        float rightX = lerp(rightRest + w * 1.15f, rightRest, t);

        // The wide spill first, behind everything. Without it the light the cluster
        // throws ends exactly at the edge of the outcrop's own sprite, which draws a
        // straight vertical line down the cave - see CaveArt.gemBloom.
        float bw = w * 2.0f;
        drawTexTinted(g, TextureBank.id(ButcherClient.TEX_BLOOM),
                leftX - bw * 0.30f, sh - bw * 0.78f, bw, bw, gemLeftColour);
        drawTexTinted(g, TextureBank.id(ButcherClient.TEX_BLOOM),
                rightX + w - bw * 0.70f, sh - bw * 0.78f, bw, bw, gemRightColour);

        // crystals, then the rock over their roots, then the light they throw back
        // onto it - the rock has to sit between the two, and can't be tinted itself
        drawTexTinted(g, TextureBank.id(ButcherClient.TEX_GEM_L), leftX, y, w, h, gemLeftColour);
        drawTex(g, TextureBank.id(ButcherClient.TEX_ROCK_L), leftX, y, w, h);
        drawTexTinted(g, TextureBank.id(ButcherClient.TEX_GLOW_L), leftX, y, w, h, gemLeftColour);

        drawTexTinted(g, TextureBank.id(ButcherClient.TEX_GEM_R), rightX, y, w, h, gemRightColour);
        drawTex(g, TextureBank.id(ButcherClient.TEX_ROCK_R), rightX, y, w, h);
        drawTexTinted(g, TextureBank.id(ButcherClient.TEX_GLOW_R), rightX, y, w, h, gemRightColour);

        drawSparkles(g, leftX, y, w, h, now, t, gemLeftColour, false);
        drawSparkles(g, rightX, y, w, h, now, t, gemRightColour, true);
    }

    /**
     * The twinkle. Each anchor runs its own cycle, dark for most of it and flaring
     * briefly, so the crystals catch the light at different moments instead of all
     * blinking together. Size and alpha rise and fall on the same curve - the alpha
     * comes from the blit tint, which carries one.
     */
    private void drawSparkles(GuiGraphicsExtractor g, float x, float y, float w, float h,
                              long now, float slideIn, int gemColour, boolean mirrored) {
        if (slideIn < 1f) return;   // nothing twinkles until the outcrop has landed
        int colour = lighten(gemColour, 0.45f);
        float base = Math.min(w, h) * 0.16f;

        for (int i = 0; i < SPARKLE_AT.length; i++) {
            float offset = (i * 0.37f) + (mirrored ? 0.53f : 0f);
            float p = ((now / (float) SPARKLE_PERIOD_MS) + offset) % 1f;
            if (p > SPARKLE_DUTY) continue;

            float k = (float) Math.sin(Math.PI * (p / SPARKLE_DUTY));
            float fx = mirrored ? 1f - SPARKLE_AT[i][0] : SPARKLE_AT[i][0];
            float cx = x + w * fx;
            float cy = y + h * SPARKLE_AT[i][1];
            float s = base * (0.45f + 0.55f * k);
            int argb = (Math.round(255 * k) << 24) | (colour & 0xFFFFFF);
            drawTexTinted(g, TextureBank.id(ButcherClient.TEX_SPARKLE), cx - s / 2f, cy - s / 2f, s, s, argb);
        }
    }

    /**
     * Torch flicker, bats under the ceiling and embers off the flames, keyed to the
     * torch positions painted into the backdrop. Brightness is varied by sprite SIZE
     * rather than alpha, since the blit path has no alpha tint.
     */
    private void drawCaveAtmosphere(GuiGraphicsExtractor g, int sw, int sh, long now) {
        long t = now - seedMs;
        float unit = Math.min(sw, sh);

        for (int i = 0; i < TORCH_FRAC.length; i++) {
            float[] at = onBackdrop(TORCH_FRAC[i][0], TORCH_FRAC[i][1], sw, sh);
            float tx = at[0], ty = at[1];
            float flicker = 1f + (float) Math.sin(t / 90.0 + i * 37.0) * 0.10f
                    + (float) Math.sin(t / 47.0 + i * 91.0) * 0.05f;
            float gs = unit * 0.34f * flicker;
            drawTex(g, TORCH_GLOW, tx - gs / 2f, ty - gs / 2f, gs, gs);
        }

        for (int i = 0; i < BAT_COUNT; i++) {
            float seed = i * 71.3f;
            float baseX = (0.16f + 0.23f * i) * sw;
            float baseY = (0.185f + 0.045f * ((i * 2) % 3)) * sh;
            float driftX = (float) Math.sin(t / 900.0 + seed) * sw * 0.055f;
            float driftY = (float) Math.sin(t / 650.0 + seed * 1.7f) * sh * 0.022f;
            int frame = (int) ((t + (long) (seed * 100)) / BAT_FLAP_MS) % BAT_FRAMES.length;
            float bs = unit * 0.055f;
            drawTex(g, BAT_FRAMES[frame], baseX + driftX - bs / 2f, baseY + driftY - bs / 2f, bs, bs);
        }

        for (int i = 0; i < EMBER_COUNT; i++) {
            float seed = i * 13.7f;
            float[] torch = TORCH_FRAC[i % TORCH_FRAC.length];
            float riseMs = 2200f + (i * 137) % 900;
            float cycle = ((t + (long) (seed * 500)) % (long) riseMs) / riseMs;
            float fade = (float) Math.sin(cycle * Math.PI);
            if (fade <= 0.05f) continue;
            float[] from = onBackdrop(torch[0] + ((i % 3) - 1) * 0.008f, torch[1] - 0.025f, sw, sh);
            float ex = from[0] + (float) Math.sin(cycle * Math.PI * 2 + seed) * sw * 0.008f;
            float ey = from[1] - cycle * sh * 0.17f;
            float es = unit * (0.008f + 0.006f * fade);
            drawTex(g, EMBER, ex - es / 2f, ey - es / 2f, es, es);
        }
    }

    /** How much bigger the end card is than normal text. */
    private static final float CARD_SCALE = 2.4f;

    /** How far into the escape the card stops sitting still and takes off. */
    private static final float CARD_LAUNCH_AT = 0.56f;
    /**
     * How far it dips before it goes, as a "back" constant.
     *
     * 1.6 rather than {@link #BOUNCE}'s 3.1: at 3.1 the wind-up is a quarter of the
     * whole travel and the line visibly sags before it moves, which looks like it fell
     * over. At 1.6 the dip is about 9% - read as a crouch, not a stumble.
     */
    private static final float CARD_ANTICIPATION = 1.6f;
    /** Extra travel past the top edge, so it is properly gone rather than clipped. */
    private static final float CARD_CLEARANCE = 0.14f;

    /**
     * The line that says how the run ended.
     *
     * Two different events, so two different sentences. "Missed..." is only honest when
     * the blade actually missed - the worm ducked it. When the cut landed and the Scatha
     * simply left afterwards, nothing was missed, so it says it ran off instead.
     *
     * Drawn through a scaled pose rather than at font size, because the font has one
     * size and this needs to carry a full-screen moment. Scaling around the text's own
     * centre keeps it centred: scaling the pose alone would push it right and down by a
     * factor of the scale, which is the usual way this goes wrong.
     */
    private void drawEndCard(GuiGraphicsExtractor g, int sw, int sh, long now) {
        float e = escapeProgress(now);
        float in = ease(Math.min(1f, e / 0.14f));
        float out = e > 0.75f ? 1f - ease((e - 0.75f) / 0.25f) : 1f;

        // The dim still comes and goes by fading - it has nowhere to fly to. Only the
        // text leaves by moving.
        if (!revealing()) {
            int d = (int) (110 * in * out);
            if (d > 1) fillFull(g, sw, sh, d << 24);
        }

        int alpha = Math.round(255 * in);
        if (alpha < 6) return;
        int colour = alpha << 24 | 0xD8D2C2;

        Component text = Component.literal(missedSwing ? "Missed..." : "It ran off...");

        // Well above the drop, which rises to about 0.46 of the screen and is up to a
        // third of its height tall. At 0.38 the two were sitting on top of each other.
        float cx = sw / 2f;
        float rest = sh * 0.17f;

        // It arrives with the overshoot everything else here arrives with - punched in
        // slightly too big and settling back - rather than simply appearing at size.
        float pop = easeOutBack(Math.min(1f, e / 0.20f));
        float scale = CARD_SCALE * lerp(0.58f, 1f, pop);

        // And it leaves by being thrown off the top, gathering itself downwards first.
        // It used to dissolve on the spot, which put two fades on screen at once - this
        // one and the cavern's - and the eye has nothing to follow through a fade.
        float launch = Math.max(0f, (e - CARD_LAUNCH_AT) / (1f - CARD_LAUNCH_AT));
        float cy = rest - easeInBack(Math.min(1f, launch), CARD_ANTICIPATION)
                * (rest + sh * CARD_CLEARANCE);
        // Scaled about its own centre, the glyphs sit roughly 12px below cy at this
        // size, so that is when the last of it has left the screen.
        if (cy + 14f < 0f) return;

        int w = font.width(text);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        // Scale about the point the text should be centred on, then draw it centred
        // there in unscaled coordinates - so the centre stays put whatever the scale.
        pose.translate(cx, cy);
        pose.scale(scale, scale);
        pose.translate(-cx, -cy);
        g.text(font, text, Math.round(cx - w / 2f), Math.round(cy - 4f), colour, true);
        pose.popMatrix();
    }

    // ------------------------------------------------- reveal
    private void drawReveal(GuiGraphicsExtractor g, int sw, int sh, long now,
                            float partSize, float spacing, float chainLeft, float baseY) {
        float rp = Math.min(1f, (now - revealStart) / (float) revealLengthMs());
        float cx = sw / 2f, cy = sh * 0.46f;

        // Every outcome gets the same move: the drop lifts off the block, arcs into
        // the middle of the screen, grows, and shrinks away to nothing. Only three
        // things differ - what the sprite is, where it starts, and whether it turns.
        // Where it lifts off from. Taken from the scene, not from the old flat layout:
        // partCenterX works out where a part WOULD be in the original six-across chain,
        // which by the last cut is nowhere near where the head actually is - the camera
        // has been re-centring on what is left of the worm the whole time. So the head
        // used to jump back to its starting position and rise from there.
        Identifier tex;
        float originX, originY, endFrac;
        int fromPart = reachesHead ? PARTS - 1 : lastCutPart;
        float[] from = partOnScreen(fromPart, sw, sh, now, 0f);
        // While the worm is bolting, the drop leaves FROM it - so it reads as being
        // shaken loose rather than appearing where the animal used to be. Frozen at the
        // moment the reveal started, because a source that keeps sliding right drags
        // the whole arc with it.
        if (escapeAt >= 0 && !reachesHead) {
            // Ride the body until it has visibly got moving, THEN let go. Pinning on the
            // first frame meant the item was already detached before the worm had taken
            // a step, so it read as appearing behind the animal rather than being
            // shaken out of it.
            if (escapeProgress(now) < POP_AT) {
                revealFromX = from[0];
                revealFromY = from[1];
            }
            originX = revealFromX;
            originY = revealFromY;
        } else {
            originX = from[0];
            originY = from[1];
        }
        if (reachesHead) {
            tex = SEG_HEAD;          // unused for the pet: the head is a real mesh below
            endFrac = HEAD_REVEAL_FRAC;
        } else {
            // whatever fell out of the last cut, so it comes from where the blade landed
            tex = result != null && result.outcome == Outcome.BLOCKED ? BLOCK_BRAN : GEMSTONES;
            endFrac = ITEM_REVEAL_FRAC;
        }

        // Held back while the item is still riding the worm, so the pop is its own beat
        // rather than the start of the flight to the middle of the screen.
        float launched = escapeAt >= 0 && !reachesHead
                ? Math.max(0f, (escapeProgress(now) - POP_AT) / (1f - POP_AT)) : 1f;
        float travel = easeOutCubic(Math.min(1f, rp / 0.50f)) * Math.min(1f, launched * 1.6f);
        float shrink = rp > 0.78f ? easeIn((rp - 0.78f) / 0.22f) : 0f;

        // Dim the cavern back so the drop carries the frame.
        //
        // Deliberately not `travel`, which it used to be. easeOutCubic is heavily
        // front-loaded - 87% of it is done by the time the drop is a quarter of the way
        // across - and on the escape ending, where the drop pops out late and there is a
        // worm to watch leave, that dumped most of the darkening into the first third of
        // a second. The drop still flies on `travel`; only the light goes on its own,
        // slower, evenly-paced ramp.
        float dim = escaped()
                ? ease(Math.min(1f, rp / DIM_IN_ESCAPE))
                : easeOutCubic(Math.min(1f, rp / DIM_IN));
        dim *= Math.min(1f, launched * 1.6f);
        fillFull(g, sw, sh, (int) (170 * dim) << 24);

        float ix = lerp(originX, cx, travel);
        float iy = lerp(originY, cy, travel) - (float) Math.sin(travel * Math.PI) * sh * 0.06f;
        // The hop out of the body: a short upward kick right as it comes free, on top of
        // the long arc. Without it the item slides out sideways instead of being thrown.
        if (escapeAt >= 0 && !reachesHead) {
            float hop = Math.max(0f, 1f - Math.abs(escapeProgress(now) - POP_AT) / 0.16f);
            iy -= hop * hop * sh * 0.055f;
        }
        float is = lerp(partSize, sh * endFrac, travel) * (1f - shrink);
        if (is < 1f) return;   // shrunk away; nothing left to draw

        if (reachesHead) {
            // The pet head is the real model, turning on its own axis.
            //
            // It used to be the flat seg_head.png "spinning" by having its width pinched
            // to nothing and opened out again - the only rotation a 2D blit can fake, and
            // it always read as a card flipping rather than a head turning. There is a
            // head mesh and a projection now, so it simply turns: same arc, same timing,
            // same number of turns, just actual geometry.
            float spin = (float) Math.pow(rp, 1.5) * HEAD_TURNS * 360f;
            meshes.begin();
            // Sized so the mesh ends up the same height on screen the sprite did, and
            // pitched a little further over than the scene camera so the turn reads.
            meshes.camera(ModelProjection.Camera.of(spin, REVEAL_PITCH,
                    is / Math.max(1f, PlayerHeadMesh.HEAD + 2 * PlayerHeadMesh.HAT_INFLATE)));
            meshes.add(CutsceneScene.headMesh(), ScathaModels.textureName(ScathaSkins.Part.HEAD),
                    0f, 0f, 0f, 0f, 0xFFFFFFFF, true, false, true);
            meshes.flush(g, ix, iy, 0xFF000000, Math.max(1.5f, sw / 640f));
            return;
        }

        drawTex(g, tex, ix - is / 2f, iy - is / 2f, is, is);
    }

    /** The reveal looks down on the head a little less steeply than the scene does. */
    private static final float REVEAL_PITCH = -12f;
    /** Firework and pet-head popup. Deliberately fired while this screen is still up. */
    private void firePetPayoff() {
        if (payoffFired) return;
        payoffFired = true;
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            // Vanilla's advancement sting, on the beat the head vanishes and the real
            // pet starts rising. A pet is about a one-in-two-thousand drop; borrowing a
            // sound every player already reads as "you achieved something" does more
            // than any amount of new noise could.
            if (ButcherClient.config == null || ButcherClient.config.petFanfare) {
                ButcherAudio.onPetFanfare();
            }
            com.butcher.anim.PetDropFirework.burst(result == null ? null : result.rarity);
            com.butcher.anim.PetHeadPopup.showIfEnabled();
        });
    }

    private void close() {
        if (!active) return;
        active = false;
        // A run that FINISHED is allowed to keep ringing.
        //
        // The pet sting fires on the beat the head vanishes, which is a fraction of a
        // second before the screen closes - so cutting the audio here killed it almost
        // as soon as it started, and the totem and firework played over silence. A
        // cutscene that was interrupted is a different matter: there the sound is
        // orphaned and should stop at once.
        if (phase == Phase.DONE) ButcherAudio.letRing();
        else ButcherAudio.silence();
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.gui.screen() == this) client.gui.setScreen(null);
        });
    }

    @Override
    public void onClose() {
        active = false;
        // Also here, not only in close(): this is the path taken when something else
        // takes the screen away, which is exactly when a stuck sound would be worst.
        // Same exception - if the run reached its end, whatever is still sounding is
        // meant to be, and close() has already said so.
        if (phase != Phase.DONE) ButcherAudio.silence();
        super.onClose();
    }

    /** Progress 0..1 through the {@code from}..{@code to} slice of a span - how the
     *  opening beats are staggered inside one INTRO. */
    private static float segment(long since, long span, float from, float to) {
        float f = since / (float) span;
        return Math.max(0f, Math.min(1f, (f - from) / (to - from)));
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    /**
     * Eases in, dipping the wrong way first - the wind-up before something is thrown.
     *
     * {@link #easeOutBack} read backwards. The overshoot constant is passed in rather
     * than taken from {@link #BOUNCE}, because 3.1 is tuned for an arrival, where the
     * overshoot lands on top of the target and settles; used as an anticipation it dips
     * a quarter of the whole travel, which reads as the text falling over before it
     * goes rather than gathering itself.
     */
    private static float easeInBack(float t, float back) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t * t * ((back + 1f) * t - back);
    }

    private static float ease(float t) { return t * t * (3 - 2 * t); }
    private static float easeIn(float t) { return t * t; }
    private static float easeOutCubic(float t) { float u = 1f - t; return 1f - u * u * u; }

    private static void drawTex(GuiGraphicsExtractor context, Identifier tex, float x, float y, float w, float h) {
        context.blit(RenderPipelines.GUI_TEXTURED, tex, Math.round(x), Math.round(y), 0, 0,
                Math.round(w), Math.round(h), Math.round(w), Math.round(h));
    }

    /**
     * Same blit with a colour multiplied into the texture. The shorter overload above
     * is this one with 0xFFFFFFFF for the tint - verified in 26.2's own bytecode,
     * where it hands {@code -1} to the full form - so a grey sprite can be recoloured
     * at draw time instead of needing one texture per colour.
     */
    private static void drawTexTinted(GuiGraphicsExtractor context, Identifier tex,
                                      float x, float y, float w, float h, int argb) {
        context.blit(RenderPipelines.GUI_TEXTURED, tex, Math.round(x), Math.round(y), 0, 0,
                Math.round(w), Math.round(h), Math.round(w), Math.round(h), argb);
    }
}
