package com.butcher.gui;

import com.butcher.ButcherClient;
import com.butcher.anim.ChopTimeline;
import com.butcher.anim.ChopTimeline.Creature;
import com.butcher.anim.ChopTimeline.Verdict;
import com.butcher.audio.ButcherAudio;
import com.butcher.render.ChopScene;
import com.butcher.render.HeadSkins;
import com.butcher.render.TextureBank;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/**
 * "The Chopping Block" - Diana's cutscene.
 *
 * Deliberately thin. Every frame is drawn by {@link ChopScene#draw}, the same routine
 * {@code tools/ChopPreview} renders offline; this screen only supplies its primitives through
 * the GUI, keeps the clock, plays the sounds, and closes when the timeline ends. Drawn in the
 * HUD (CutsceneHud), never opened, so the player keeps control.
 *
 * The sounds follow the timeline's clock and the seed, never the verdict, until the split - so a
 * win cannot be heard coming any more than it can be seen.
 */
public class ChopScreen extends Screen implements ChopScene.Canvas, Cutscene {

    private ChopTimeline tl;
    private Verdict verdict;
    private Component itemName;
    private final long seed;
    private final Creature creature;
    /** Opened on a kill, before anyone knows what it dropped - see {@link #resolve}. */
    private boolean pending;
    private final com.butcher.render.MeshRenderer meshes = new com.butcher.render.MeshRenderer();
    private final boolean test;
    /** Short busts apply: every real run, and a test from the menu's "Try it" (not /butcher test). */
    private final boolean followsBusts = com.butcher.ButcherClient.tryingIt;
    /** Not final: the skip key moves it back, so the clock jumps to the showcase. */
    /** The scene's clock, at the player's speed for this animation (the menu's Speed). */
    private final com.butcher.anim.SceneClock clock = new com.butcher.anim.SceneClock(
            com.butcher.ButcherClient.config == null ? 100 : com.butcher.ButcherClient.config.dianaSpeed);
    private boolean active = true;

    private GuiGraphicsExtractor g;
    private final Matrix3x2f matrix = new Matrix3x2f();

    // Each sound once, whenever the frames happen to fall.
    private boolean landedHead, builtUp, split, afterSplit, crumbled, arrived, priced, landed;
    private int whooshes, hits, cycleStep, letter, line, coinTicks;
    // The Minos Relic's ending.
    private boolean dust, wind, ignited, fell, swell;
    private int beat, clicks;
    // Brain Food's ending.
    private boolean hum, drained, burst;
    private int floodBlips, sparkTicks;
    // The Fatal Sting.
    private boolean stRise, stCurl, stDrip, stWhoosh, stImpact, stTurn;
    // Wet Paint.
    // The Golden Fleece.
    private boolean flHarp, flWind, flBloom, flSettle;
    private int flLines;
    // The Heart of the Beast.
    private boolean htFuse, htFused, htRumble, htUnfurl, htGust, htWrap, htSwell, htOpen;
    private int htBeat, htMuffled;
    // Midas.
    private boolean mdCreep, mdShine, mdCrowned, mdFall;
    // The Glint.
    private boolean glSeep, glSpiral, glMeet;
    private int glPlucks;
    // Every expensive ending's own score.
    private boolean scored;

    /**
     * The latest a kill's drop can be told to the screen. After it the kill is a bust: the split
     * is where the outcome first shows, and it must not wait.
     */
    public static final long RESOLVE_BY = ChopTimeline.SPLIT - 250;

    /**
     * @param verdict  what dropped - drawn as its real item (DianaItems); null for a kill whose drop
     *                 is not known yet, told later through {@link #resolve}
     * @param creature whose head is on the block - the kill's, or the drop's own creature
     * @param test     opened by /butcher test: plays the same, counts nothing
     */
    public ChopScreen(Verdict verdict, Creature creature, boolean test) {
        super(Component.literal("Butcher"));
        // The seed is picked here, before anything about the verdict is looked at.
        this.seed = System.nanoTime() ^ clock.openedAt();
        this.test = test;
        this.pending = verdict == null;
        Verdict v = verdict == null ? Verdict.BUST : verdict;
        this.creature = creature == null ? Creature.of(v, seed) : creature;
        settle(v);
        shortBust();
    }

    /** Whether this is a kill still waiting to hear what it dropped. */
    public boolean pending() {
        return pending && active;
    }

    /**
     * What the kill dropped. Nothing drawn or heard before the split depends on the verdict -
     * ChopTest hashes every frame before it, per creature and seed - so the timeline is simply
     * built again with the same seed and creature, and the screen cannot change until the split.
     * Counted here, not at the open, so the count line includes this one.
     */
    public boolean resolve(Verdict v) {
        if (!pending) return v == verdict;
        // Past the deadline (no frame was drawn to notice it - the HUD hidden with F1, say): it is the bust it
        // already is, and a win that came now gets a screen of its own from the caller.
        boolean late = clock.t() >= RESOLVE_BY;
        if (late && v != Verdict.BUST) {
            resolve(Verdict.BUST);
            return false;
        }
        pending = false;
        if (!test) {
            com.butcher.config.ButcherStats.get().onDiana(creature.name(),
                    v.win() ? com.butcher.render.DianaItems.name(v) : null);
        }
        settle(v);
        shortBust();
        return true;
    }

    /** Everything that depends on what dropped. */
    private void settle(Verdict verdict) {
        this.tl = new ChopTimeline(verdict, creature, seed);
        this.verdict = verdict;
        this.itemName = com.butcher.render.DianaItems.title(verdict);
        com.butcher.config.PatternConfig cfg = ButcherClient.config;
        if (cfg != null) tl.options(!cfg.dianaFullEndings, cfg.dianaBanner, Math.max(0.6f, Math.min(1.4f, cfg.dianaSize / 100f)));
        // The count line: which of these this is, from the stats (counted before this opened), or
        // that a test is not counted.
        String[] lines = tl.lines();
        String name = com.butcher.render.DianaItems.name(verdict);
        if (lines.length == 1 && name != null && lines[0].contains(" \u00b7 ")) {
            String odds = lines[0].substring(0, lines[0].indexOf(" \u00b7 "));
            int n = com.butcher.config.ButcherStats.get().dianaDropCount(name);
            tl.lines(odds + " \u00b7 " + (test || n <= 0 ? "test run" : "your " + ordinal(n)));
        }
        // The drop's price, asked for now so it is there when it is shown - never waited on.
        String id = com.butcher.render.DianaItems.id(verdict);
        if (id != null && verdict.win() && ButcherClient.config != null && ButcherClient.config.livePrices) {
            com.butcher.market.Bazaar.request();
            com.butcher.market.LivePrice.request(id);
        }
    }

    /** 1st, 2nd, 3rd, 4th ... 11th, 12th, 13th ... 21st. */
    public static String ordinal(int n) {
        int last2 = n % 100, last = n % 10;
        String suffix = last2 >= 11 && last2 <= 13 ? "th" : last == 1 ? "st" : last == 2 ? "nd" : last == 3 ? "rd" : "th";
        return String.format("%,d", n) + suffix;
    }

    /**
     * Fixes the price the moment the count starts, so it cannot jump mid-count: the bazaar's
     * instant sell if the item is a bazaar product, else the lowest BIN, else what an NPC pays.
     */
    private void fixPrice() {
        if (ButcherClient.config != null && !ButcherClient.config.dianaPrice) return;
        String id = com.butcher.render.DianaItems.id(verdict);
        boolean live = ButcherClient.config == null || ButcherClient.config.livePrices;
        if (id != null && live) {
            Double sell = com.butcher.market.Bazaar.sell(id);
            if (sell != null) {
                tl.price(Math.round(sell), "bazaar instant sell");
                return;
            }
            Long bin = com.butcher.market.LivePrice.cached(id);
            if (bin != null && bin > 0) {
                tl.price(bin, "lowest BIN on the auction house");
                return;
            }
        }
        long npc = com.butcher.render.DianaItems.npcPrice(verdict);
        if (npc > 0) tl.price(npc, live ? "NPC sell price - the live price could not be fetched" : "NPC sell price");
    }

    public boolean isActive() { return active; }
    public boolean isTest() { return test; }

    @Override protected void init() { }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) { }

    /** The gameplay window slides in over the first 400 ms. */
    private float pipFade() {
        return Math.min(1f, (System.currentTimeMillis() - clock.openedAt()) / 400f);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        long t = clock.t();
        // Nothing was said in time: the kill dropped nothing worth the cutscene.
        if (pending && t >= RESOLVE_BY) resolve(Verdict.BUST);
        if (t >= tl.total()) {
            summary();
            close();
            return;
        }
        sounds(t);
        // The game's own golden armour for the Crown - adopted here, every frame (free once it is
        // there), because /butcher reload forgets adopted textures.
        TextureBank.adopt(com.butcher.render.art.ChopArt.TEX_ARMOR,
                Identifier.withDefaultNamespace("textures/entity/equipment/humanoid/gold.png"), 64, 32);
        // And the enchanting table's glyphs, the game's own particles, for Chimera's.
        if (tl.ending() == ChopTimeline.Ending.GLINT) {
            for (int k = 0; k < 26; k++) {
                TextureBank.adopt(com.butcher.render.art.ChopArt.glyphTex(k),
                        Identifier.withDefaultNamespace("textures/particle/sga_" + (char) ('a' + k) + ".png"), 8, 8);
            }
        }
        this.g = g;
        ChopScene.calm = (ButcherClient.config != null && ButcherClient.config.calmEffects) || com.butcher.Safety.slow();
        ChopScene.draw(this, tl, t, width, height);
        bustLine(t);
        this.g = null;
        // The game itself, live, in a corner (SkyMaxx's gameplay view) - drawn last, over everything.
        com.butcher.render.GameplayView.draw(g, pipFade(), 0xFFD4A62A);
    }

    /**
     * A bust says one thing, briefly: whose kill it was and how long the dry streak is now (the review, D6) - so the
     * bust still means something, and the streak is there without opening the menu.
     */
    private void bustLine(long t) {
        if (test || pending || verdict != Verdict.BUST) return;
        long from = ChopTimeline.SPLIT + 450, to = tl.total() - 250;
        if (t < from || t > to) return;
        float a = Math.min(1f, (t - from) / 200f) * Math.min(1f, (to - t) / 250f);
        int dry = com.butcher.config.ButcherStats.get().dianaDryStreak;
        String who = switch (creature) {
            case SPHINX -> "Sphinx";
            case INQUISITOR -> "Minos Inquisitor";
            case MANTICORE -> "Manticore";
            case KING_MINOS -> "King Minos";
            case CHAMPION -> "Minos Champion";
        };
        String line = who + " - " + String.format("%,d", dry) + (dry == 1 ? " kill" : " kills") + " without a drop";
        text(line, width / 2f, height * 0.8f, Math.max(1f, Math.min(1.35f, height / 300f)), Math.round(255 * a) << 24 | 0xB8B8B8);
    }

    // ================================================================== sound

    private void sounds(long t) {
        long seed = tl.seed();
        if (!landedHead && t >= ChopTimeline.LAND) {
            landedHead = true;
            ButcherAudio.onChopLand(seed);
        }
        if (t < ChopTimeline.SPLIT + 50) {
            while (whooshes < 4 && t >= ChopTimeline.blowTime(whooshes) - ChopTimeline.SWING_MS) {
                ButcherAudio.onChopWhoosh(whooshes, seed + 10 + whooshes);
                whooshes++;
            }
            while (hits < 3 && t >= ChopTimeline.STRIKES[hits]) {
                ButcherAudio.onChopHit(hits, seed + 20 + hits);
                hits++;
            }
            if (!builtUp && t >= ChopTimeline.CYCLE) {
                builtUp = true;
                ButcherAudio.onLabBuildUp(ChopTimeline.SPLIT - t);
            }
            while (cycleStep < ChopTimeline.CYCLE_STEPS.length && t >= ChopTimeline.CYCLE_STEPS[cycleStep]) {
                ButcherAudio.onLabCycleTick(cycleStep++);
            }
        }
        if (!split && t >= ChopTimeline.SPLIT) {
            split = true;
            // The riser is cut dead, so the split stands alone.
            ButcherAudio.silence();
            ButcherAudio.onChopSplit();
        }
        Verdict v = tl.verdict();
        if (!afterSplit && t >= ChopTimeline.SPLIT + 120) {
            afterSplit = true;
            if (tl.special()) ButcherAudio.onLabRising(700);
            else if (v.win()) ButcherAudio.onLabRising(ChopTimeline.ARRIVE - t);
            else if (v == Verdict.BUST) ButcherAudio.onLabEmpty();
            else ButcherAudio.onLabUnknown();
        }
        if (!crumbled && tl.crumble(t) >= 0f) {
            crumbled = true;
            ButcherAudio.onChopCrumble(seed + 30);
        }
        if (tl.unmade()) relicSounds(t);
        if (tl.thinks()) brainSounds(t);
        if (tl.ending() == ChopTimeline.Ending.STING) stingSounds(t);
        if (tl.ending() == ChopTimeline.Ending.HUNT) huntSounds(t);
        if (tl.ending() == ChopTimeline.Ending.FLEECE) fleeceSounds(t);
        if (tl.ending() == ChopTimeline.Ending.HEART) heartSounds(t);
        if (tl.ending() == ChopTimeline.Ending.MIDAS) midasSounds(t);
        if (tl.ending() == ChopTimeline.Ending.GLINT) glintSounds(t);
        // The Dye's score starts with the geyser: the split goes quiet first.
        if (!scored && tl.special() && !tl.cheap() && t >= (tl.verdict() == Verdict.DYE ? com.butcher.anim.HuntEnding.GEYSER + 100 : ChopTimeline.SPLIT + 150)
                && (ButcherClient.config == null || ButcherClient.config.dianaMusic)) {
            scored = true;
            score(t);
        }
        if (tl.special()) specialText(t);
        if (v.win()) {
            if (!priced && t >= tl.countStart()) {
                priced = true;
                fixPrice();
            }
            if (tl.price() >= 0 && t >= tl.countStart() && t < tl.countStart() + ChopTimeline.COUNT_MS) {
                int due = (int) ((t - tl.countStart()) / 70);
                while (coinTicks <= due) ButcherAudio.onThawCoin(coinTicks++);
            }
            if (!landed && tl.price() >= 0 && t >= tl.countStart() + ChopTimeline.COUNT_MS) {
                landed = true;
                ButcherAudio.onThawCoinLand();
            }
            if (!arrived && t >= tl.arrive()) {
                arrived = true;
                ButcherAudio.onLabArrive();
            }
        }
    }

    private void relicSounds(long t) {
        while (beat < ChopTimeline.RELIC_BEATS.length && t >= ChopTimeline.RELIC_BEATS[beat]) {
            ButcherAudio.onThawHeartbeat(beat++);
        }
        if (!dust && t >= ChopTimeline.RELIC_PEEL) {
            dust = true;
            ButcherAudio.onRelicBurst(0, tl.seed() + 500);
            ButcherAudio.onRelicDust(2600, tl.seed() + 400);
        }
        if (!wind && t >= ChopTimeline.RELIC_PEEL + 600) {
            wind = true;
            ButcherAudio.onRelicWind(ChopTimeline.RELIC_CONVERGE - t);
        }
        if (!ignited && t >= ChopTimeline.RELIC_CONVERGE) {
            ignited = true;
            // The wind is cut dead where the pieces fuse; only the ignition rings on.
            ButcherAudio.silence();
            ButcherAudio.onRelicIgnite();
            ButcherAudio.onRelicMoon(tl.total() - t);
        }
        if (!fell && t >= ChopTimeline.RELIC_FALL) {
            fell = true;
            ButcherAudio.onLabSink(tl.seed() + 600);
        }
        // The wheel: a click every quarter turn, so it is heard winding up and braking.
        if (t >= ChopTimeline.RELIC_SPIN && t < ChopTimeline.RELIC_ARRIVE) {
            int quarter = (int) Math.floor(tl.moonAngles(t)[1] / (Math.PI / 2));
            int first = (int) Math.floor(tl.moonAngles(ChopTimeline.RELIC_SPIN)[1] / (Math.PI / 2));
            while (clicks < quarter - first) ButcherAudio.onLabCycleTick(Math.min(12, clicks++ / 2));
        }
        // A swell played backwards, cut dead on the Relic's arrival - the Thaw's Dye trick.
        if (!swell && t >= ChopTimeline.RELIC_ARRIVE - 1500) {
            swell = true;
            ButcherAudio.onThawReverseSwell(ChopTimeline.RELIC_ARRIVE - t);
        }
    }

    private void brainSounds(long t) {
        // A rising pluck for each wave of veins lighting.
        int blips = 16;
        while (floodBlips < blips && t >= ChopTimeline.BRAIN_FLOOD + floodBlips * ChopTimeline.FLOOD_MS / blips) {
            ButcherAudio.onLabCorner(floodBlips / (float) (blips - 1));
            floodBlips++;
        }
        if (!hum && t >= ChopTimeline.BRAIN_THINK) {
            hum = true;
            ButcherAudio.onBrainHum(ChopTimeline.BRAIN_DRAIN - t + 400);
        }
        // Glassy ticks for the sparks running home.
        while (sparkTicks < 6 && t >= ChopTimeline.BRAIN_THINK + 65L * sparkTicks + (sparkTicks % 3) * 20L) {
            ButcherAudio.onLabCycleTick(4 + (sparkTicks * 5) % 9);
            sparkTicks++;
        }
        if (!drained && t >= ChopTimeline.BRAIN_DRAIN) {
            drained = true;
            // Sucked in - a swell played backwards, cut dead on the burst.
            ButcherAudio.onThawReverseSwell(ChopTimeline.BRAIN_BURST - t);
        }
        if (!burst && t >= ChopTimeline.BRAIN_BURST) {
            burst = true;
            ButcherAudio.silence();
            ButcherAudio.onRelicIgnite();
        }
    }

    /** The expensive ending's score, in its own key: moonlit A minor for the Relic, and so on. */
    private void score(long t) {
        int[] minor = {0, 2, 3, 5, 7, 8, 10}, major = {0, 2, 4, 5, 7, 9, 11}, phrygian = {0, 1, 3, 5, 7, 8, 10},
                lydian = {0, 2, 4, 6, 7, 9, 11}, dorian = {0, 2, 3, 5, 7, 9, 10};
        float root;
        int[] scale;
        switch (tl.verdict()) {
            case RELIC -> { root = 220f; scale = minor; }
            case SHIMMERING_WOOL -> { root = 293.66f; scale = major; }
            case MANTI_CORE -> { root = 164.81f; scale = phrygian; }
            case CHIMERA -> { root = 185f; scale = lydian; }
            case DYE -> { root = 196f; scale = dorian; }
            default -> { return; }
        }
        // The Dye's runs to the pull into the split; the rest to the reveal.
        long until = tl.verdict() == Verdict.DYE ? com.butcher.anim.HuntEnding.SUCK : tl.arrive();
        if (until - t > 300) ButcherAudio.onScore(root, scale, until - t);
    }

    private void glintSounds(long t) {
        long seed = tl.seed();
        if (!glSeep && t >= com.butcher.anim.GlintEnding.SEEP) {
            glSeep = true;
            ButcherAudio.onChiShimmer(1200, seed + 1300);
        }
        // A soft pluck as glyphs come off the head, like the enchanting table's.
        while (glPlucks < 12 && t >= com.butcher.anim.GlintEnding.RISE + glPlucks * 160L) {
            ButcherAudio.onLabCorner(((glPlucks * 7) % 12) / 11f);
            glPlucks++;
        }
        if (!glSpiral && t >= com.butcher.anim.GlintEnding.SPIRAL) {
            glSpiral = true;
            ButcherAudio.onLabBuildUp(com.butcher.anim.GlintEnding.CONVERGE - t);
            ButcherAudio.onFleeceWind(com.butcher.anim.GlintEnding.CONVERGE - t, seed + 1310);
        }
        if (!glMeet && t >= com.butcher.anim.GlintEnding.CONVERGE) {
            glMeet = true;
            ButcherAudio.silence();
            ButcherAudio.onChiSlam();
        }
    }

    private void midasSounds(long t) {
        long seed = tl.seed();
        if (!mdCreep && t >= com.butcher.anim.MidasEnding.CREEP) {
            mdCreep = true;
            ButcherAudio.onMidasCreep(900, seed + 1200);
        }
        if (!mdShine && t >= com.butcher.anim.MidasEnding.SHINE + 120) {
            mdShine = true;
            ButcherAudio.onMidasShine();
        }
        if (!mdFall && t >= com.butcher.anim.MidasEnding.COLLAPSE) {
            mdFall = true;
            ButcherAudio.silence();
            ButcherAudio.onMidasAvalanche(seed + 1220);
        }
        if (!mdCrowned && t >= com.butcher.anim.MidasEnding.TOPPED - 150) {
            mdCrowned = true;
            ButcherAudio.onMidasCrowned();
        }
    }

    private void heartSounds(long t) {
        long seed = tl.seed();
        // The cells flying in: drawn together with a backwards swell, fused with a thud.
        if (!htFuse && t >= ChopTimeline.SPLIT + 100) {
            htFuse = true;
            ButcherAudio.onThawReverseSwell(750);
        }
        if (!htFused && t >= ChopTimeline.SPLIT + 850) {
            htFused = true;
            ButcherAudio.onHeartMuffled();
        }
        long[] beats = com.butcher.anim.HeartEnding.BEATS;
        while (htBeat < beats.length && t >= beats[htBeat]) ButcherAudio.onThawHeartbeat(Math.min(2, htBeat++ / 3));
        if (!htRumble && t >= ChopTimeline.SPLIT + 300) {
            htRumble = true;
            ButcherAudio.onHeartRumble(com.butcher.anim.HeartEnding.FLAP - t, seed + 1100);
        }
        if (!htUnfurl && t >= com.butcher.anim.HeartEnding.UNFOLD) {
            htUnfurl = true;
            ButcherAudio.onWingUnfurl(com.butcher.anim.HeartEnding.SPREAD - t, seed + 1110);
        }
        if (!htGust && t >= com.butcher.anim.HeartEnding.FLAP) {
            htGust = true;
            ButcherAudio.onWingGust(seed + 1120);
        }
        if (!htWrap && t >= com.butcher.anim.HeartEnding.FLAPPED) {
            htWrap = true;
            ButcherAudio.onWingWrap(com.butcher.anim.HeartEnding.WRAPPED - t, seed + 1130);
        }
        // Inside the cocoon: the heart heard through the wings.
        while (htMuffled < 2 && t >= com.butcher.anim.HeartEnding.WRAPPED + 60 + htMuffled * 330L) {
            htMuffled++;
            ButcherAudio.onHeartMuffled();
        }
        if (!htSwell && t >= com.butcher.anim.HeartEnding.ARRIVE - 700) {
            htSwell = true;
            ButcherAudio.onThawReverseSwell(com.butcher.anim.HeartEnding.ARRIVE - t);
        }
        if (!htOpen && t >= com.butcher.anim.HeartEnding.ARRIVE) {
            htOpen = true;
            ButcherAudio.silence();
            ButcherAudio.onRelicIgnite();
            ButcherAudio.onWingGust(seed + 1140);
            // The beast's own roar as it is let out.
            ButcherAudio.onChiHead(0, seed + 1150);
        }
    }

    private void fleeceSounds(long t) {
        long seed = tl.seed();
        if (!flHarp && t >= com.butcher.anim.FleeceEnding.UNRAVEL) {
            flHarp = true;
            ButcherAudio.onFleeceHarp(1300, false);
            ButcherAudio.onFleeceAir(com.butcher.anim.FleeceEnding.WIND - t + 600, seed + 1000);
        }
        // A pluck as each line of the ram in the sky is drawn, climbing.
        int lines = com.butcher.anim.FleeceEnding.LINES.length;
        while (flLines < lines && t >= com.butcher.anim.FleeceEnding.SKY_IN + flLines * com.butcher.anim.FleeceEnding.LINE_MS) {
            ButcherAudio.onLabCorner(flLines++ / (float) (lines - 1));
        }
        if (!flWind && t >= com.butcher.anim.FleeceEnding.WIND) {
            flWind = true;
            ButcherAudio.onFleeceWind(com.butcher.anim.FleeceEnding.SQUARE - t, seed + 1010);
        }
        if (!flBloom && t >= com.butcher.anim.FleeceEnding.SQUARE + 120) {
            flBloom = true;
            ButcherAudio.silence();
            ButcherAudio.onFleeceBloom(seed + 1020);
        }
        if (!flSettle && t >= com.butcher.anim.FleeceEnding.ARRIVE) {
            flSettle = true;
            ButcherAudio.onFleeceSettle(seed + 1030);
        }
    }

    private java.util.List<com.butcher.audio.HuntSound.Cue> huntCues;
    private int huntAt;

    /** The Dye's sounds after the split: its own cue list (HuntSound), played as they come due. */
    private void huntSounds(long t) {
        if (huntCues == null) huntCues = com.butcher.audio.HuntSound.schedule(tl);
        while (huntAt < huntCues.size() && t >= huntCues.get(huntAt).at) {
            com.butcher.audio.HuntSound.Cue cue = huntCues.get(huntAt++);
            if (cue.cut) ButcherAudio.silence();
            ButcherAudio.playDiana(cue.voices);
        }
    }

    private void stingSounds(long t) {
        long seed = tl.seed();
        if (!stRise && t >= com.butcher.anim.StingEnding.RISE) {
            stRise = true;
            ButcherAudio.onStingWhoosh(com.butcher.anim.StingEnding.RISEN - t - 200, seed + 800);
        }
        if (!stCurl && t >= com.butcher.anim.StingEnding.RISEN - 60) {
            stCurl = true;
            ButcherAudio.onStingClick(0, false, seed + 810);
            ButcherAudio.onStingCreak(com.butcher.anim.StingEnding.STAB - t, 90f, 210f, seed + 850);
            ButcherAudio.onStingHiss(com.butcher.anim.StingEnding.STAB - t, seed + 860);
        }
        if (!stDrip && t >= com.butcher.anim.StingEnding.DRIP_LAND) {
            stDrip = true;
            ButcherAudio.onStingDrip(seed + 870);
        }
        if (!stWhoosh && t >= com.butcher.anim.StingEnding.STAB - 20) {
            stWhoosh = true;
            ButcherAudio.onStingWhoosh(com.butcher.anim.StingEnding.IMPACT - t, seed + 890);
        }
        if (!stImpact && t >= com.butcher.anim.StingEnding.IMPACT) {
            stImpact = true;
            ButcherAudio.silence();
            ButcherAudio.onStingStab(seed + 900);
        }
        if (!stTurn && t >= com.butcher.anim.StingEnding.TURN) {
            stTurn = true;
            ButcherAudio.onFleeceBloom(seed + 910);
        }
    }

    /** A special ending's name stamped out letter by letter, and its lines coming in. */
    private void specialText(long t) {
        int length = itemName == null ? 0 : itemName.getString().length();
        while (letter < length && t >= tl.letterTime(letter)) {
            if (itemName.getString().charAt(letter) != ' ') ButcherAudio.onThawLetter(letter);
            letter++;
        }
        String[] lines = tl.lines();
        while (line < lines.length && tl.lineAlpha(line, t) > 0.05f) ButcherAudio.onLabCycleTick(9 + 2 * line++);
    }

    // ================================================================== the canvas

    @Override
    public void quad(String tex, float ox, float oy, float ux, float uy, float vx, float vy, int u0, int v0, int uw, int vh, int argb) {
        Identifier id = TextureBank.id(tex);
        if (id == null || uw <= 0 || vh <= 0) return;
        // The three projected vectors ARE the affine transform - see MeshRenderer.
        matrix.set(ux / uw, uy / uw, vx / vh, vy / vh, ox, oy);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.mul(matrix);
        g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, u0, v0, uw, vh, TextureBank.width(tex), TextureBank.height(tex), argb);
        pose.popMatrix();
    }

    @Override
    public void sprite(String tex, float cx, float cy, float w, float h, float rot, int argb) {
        if ((argb >>> 24) < 2 || w < 0.5f || h < 0.5f) return;
        Identifier id = TextureBank.id(tex);
        if (id == null) return;
        int tw = TextureBank.width(tex), th = TextureBank.height(tex);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        if (rot != 0f) pose.rotate((float) Math.toRadians(rot));
        pose.scale(w / tw, h / th);
        pose.translate(-tw / 2f, -th / 2f);
        g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0, 0, tw, th, tw, th, argb);
        pose.popMatrix();
    }

    @Override
    public void line(float x0, float y0, float x1, float y1, float width, int argb) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.05f || (argb >>> 24) < 3) return;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x0, y0);
        pose.rotate((float) Math.atan2(dy, dx));
        // Longer than the segment by half the width each end, so the halves join like round caps.
        pose.translate(-width / 2f, -width / 2f);
        pose.scale(len + width, width);
        g.fill(0, 0, 1, 1, argb);
        pose.popMatrix();
    }

    @Override
    public void fill(float x, float y, float w, float h, int argb) {
        if ((argb >>> 24) < 3) return;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(w, h);
        g.fill(0, 0, 1, 1, argb);
        pose.popMatrix();
    }

    @Override
    public String creatureSkin(Creature creature) {
        return HeadSkins.CREATURES.texture(creature.name());
    }

    @Override
    public String bestiarySkin(String key) {
        return HeadSkins.CREATURES.texture(key);
    }

    @Override
    public String dropSkin() {
        return HeadSkins.DIANA.texture(com.butcher.render.DianaItems.name(verdict));
    }

    @Override
    public void item(float cx, float cy, float size, float alpha, float yaw) {
        // A slow float, so a held showcase is never a still frame (the review, A5); heads also sway a little.
        long now = clock.t();
        cy += size * 0.05f * (float) Math.sin(now / 430.0);
        yaw += 7f * (float) Math.sin(now / 900.0);
        com.butcher.render.DianaItems.draw(g, meshes, verdict, cx, cy, size, yaw,
                Math.round(255 * Math.max(0f, Math.min(1f, alpha))), tl.truth(clock.t()));
    }

    @Override
    public void name(float cx, float cy, float scale, int argb, int letters) {
        if (itemName == null) return;
        String full = itemName.getString();
        if (letters < full.length()) {
            // Stamped out letter by letter, but centred on the whole name so it does not slide.
            outlined(Component.literal(full.substring(0, Math.max(0, letters))), font.width(itemName), cx, cy, scale, argb);
        } else {
            outlined(itemName, font.width(itemName), cx, cy, scale, argb);
        }
    }

    @Override
    public void text(String text, float cx, float cy, float scale, int argb) {
        Component c = Component.literal(text);
        outlined(c, font.width(c), cx, cy, scale, argb);
    }

    /**
     * A dark plate behind a line of text, the way vanilla backs a nametag: outlines alone were lost over bright lava
     * and beams (the review, A3). {@code textW} is the whole line's width, so a name being typed does not grow one.
     */
    private void plate(float cx, float cy, float scale, int textW, int argb) {
        int a = Math.round((argb >>> 24) * 0.42f);
        if (a < 4) return;
        float w = (textW + 8) * scale, h = 12f * scale;
        fill(Math.round(cx - w / 2f), Math.round(cy - h / 2f), Math.round(w), Math.round(h), a << 24 | 0x0B0709);
    }

    private void outlined(Component text, int fullWidth, float cx, float cy, float scale, int argb) {
        // The font treats alpha below 4 as opaque (CLAUDE.md), so a fade stops short of it.
        if ((argb >>> 24) < 6) return;
        plate(cx, cy, scale, fullWidth, argb);
        int alpha = argb >>> 24;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.scale(scale, scale);
        int x = Math.round(-fullWidth / 2f), y = -4;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                if (ox != 0 || oy != 0) g.text(font, text, x + ox, y + oy, alpha << 24, false);
            }
        }
        g.text(font, text, x, y, argb, false);
        pose.popMatrix();
    }

    // ================================================================== busts, short; the summary

    private boolean shortened, summarised;

    /** "Busts: short": a bust known before the split jumps to just before it, silently. */
    private void shortBust() {
        if (shortened || (test && !followsBusts) || pending || verdict.win() || ButcherClient.bustStyle("diana") != 1) return;
        shortened = true;
        long t = clock.t(), target = ChopTimeline.SPLIT - 450;
        if (t >= target) return;
        clock.jump(target);
        ButcherAudio.silence();
        ButcherAudio.muted(() -> sounds(target));
    }

    private void summary() {
        if (summarised || pending) return;
        summarised = true;
        // The Ledger's Tycoon: what this drop was worth, once.
        if (!test && verdict.win() && tl.price() > 0) com.butcher.config.ButcherStats.get().onCoins(tl.price());
        String who = switch (creature) {
            case SPHINX -> "Sphinx";
            case INQUISITOR -> "Minos Inquisitor";
            case MANTICORE -> "Manticore";
            case KING_MINOS -> "King Minos";
            case CHAMPION -> "Minos Champion";
        };
        String name = com.butcher.render.DianaItems.name(verdict);
        String what = verdict.win() && name != null ? name : "no rare drop";
        String price = verdict.win() && tl.price() > 0 ? " - " + String.format("%,d", tl.price()) + " coins" : "";
        String extra = test ? " (test)" : "";
        if (!test && verdict.win() && name != null)
            extra = " (your " + ordinal(com.butcher.config.ButcherStats.get().dianaDropCount(name)) + ")";
        ButcherClient.summary(who + ": " + what + price + extra);
    }

    // ================================================================== skipping

    @Override
    public boolean showsWin() {
        return active && !pending && verdict.win();
    }

    @Override
    public void skip() {
        if (!active) return;
        long t = clock.t();
        long target = Math.max(tl.arrive(), tl.countStart() + ChopTimeline.COUNT_MS);
        if (pending || !verdict.win() || t >= target || target >= tl.total()) {
            summary();
            interrupt();
            return;
        }
        // The clock jumps; every cue on the way is run silently, so flags, counters and the price are where the
        // showcase expects them and nothing skipped fires at once.
        clock.jump(target);
        ButcherAudio.silence();
        ButcherAudio.muted(() -> sounds(target));
        ButcherAudio.onSkip();
        summary();
    }

    @Override
    public void interrupt() {
        if (!active) return;
        // A kill still waiting for its drop is handed back: its drop line (or its absence) is counted for it.
        if (pending && !test) ButcherClient.dianaKillUnshown(creature, clock.openedAt());
        ButcherAudio.silence();
        ButcherAudio.onSkip();
        close();
    }

    // ================================================================== leaving

    private void close() {
        com.butcher.render.GameplayView.release();
        if (!active) return;
        active = false;
        ButcherAudio.letRing();
        // Drawn in the HUD, not opened (CutsceneHud): taking it off is all closing is.
        com.butcher.render.CutsceneHud.hide(this);
    }

    @Override
    public void onClose() {
        com.butcher.render.CutsceneHud.hide(this);
        com.butcher.render.GameplayView.release();
        boolean finished = clock.t() >= tl.total();
        active = false;
        if (!finished) ButcherAudio.silence();
        super.onClose();
    }
}
