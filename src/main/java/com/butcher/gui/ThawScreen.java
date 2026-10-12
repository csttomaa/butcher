package com.butcher.gui;

import com.butcher.ButcherClient;
import com.butcher.anim.ThawTimeline;
import com.butcher.anim.ThawTimeline.Finale;
import com.butcher.anim.ThawTimeline.Flare;
import com.butcher.anim.ThawTimeline.Strike;
import com.butcher.audio.ButcherAudio;
import com.butcher.config.ButcherStats;
import com.butcher.detect.vanguard.CorpseHaul;
import com.butcher.detect.vanguard.VanguardLoot;
import com.butcher.detect.vanguard.VanguardLoot.Tier;
import com.butcher.market.LivePrice;
import com.butcher.render.MeshRenderer;
import com.butcher.render.TextureBank;
import com.butcher.render.VanguardItems;
import com.butcher.render.art.PropArt;
import com.butcher.render.art.ThawArt;
import com.butcher.render.model.Mesh;
import com.butcher.render.model.ModelProjection;
import com.butcher.render.model.ModelProjection.Camera;
import com.butcher.render.model.ThawMeshes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Thaw: a Vanguard Frozen Corpse, opened one pick strike at a time.
 *
 * <h2>What is on screen</h2>
 *
 * A slab of ice with a Vanguard frozen inside it, and a pick. Every roll the corpse made is
 * one strike: the ice flares a colour, the pick winds up and comes down, the ice cracks a
 * little further, and one drop flies out into its slot in the tray along the bottom. The
 * tray's slots are laid out from the start, so the player can always see how many rolls are
 * left - the concept's pips, doing a second job.
 *
 * The drops come out worst first. The loot message says what the corpse gave; nothing says
 * which roll gave what, so every corpse is played as a climb that ends on its best item -
 * without misrepresenting a single thing in it.
 *
 * <h2>Four endings, one approach</h2>
 *
 * Every run looks the same until the last strike lands. Then {@link ThawTimeline.Finale}
 * decides: an all-commons corpse goes dull and ends on a shrug; an uncommon pops out and
 * gets its moment; a legendary detonates the block and takes the screen; and the
 * Frostbitten Dye drains the colour out of everything, holds it a beat too long, and floods
 * it back pink. Making the approach identical and only the payoff different is most of what
 * makes a thing feel like gambling.
 *
 * <h2>Where the timing lives</h2>
 *
 * Not here. {@link ThawTimeline} works out the whole schedule up front, from the haul, and
 * this screen only asks it what is happening at the current moment. That split is what let
 * the pacing be tested for every haul shape without the game.
 *
 * <h2>Everything real</h2>
 *
 * The block, the figure in it, the pick and the items are all 3D models drawn through the
 * same projection as the Scatha cutscene. The items are the real SkyBlock items, with their
 * real skins - see {@link VanguardItems}.
 */
public class ThawScreen extends Screen {

    // ------------------------------------------------------------------ layout
    // Fractions of the screen. Tuned by rendering frames offline (tools/ThawPreview) rather
    // than by eye in game, which is why they are the same numbers the preview uses.

    /**
     * The block's centre, as a fraction of the screen - the centre of what is actually drawn,
     * not of the model. See {@link #drawIce} for why that difference matters.
     */
    private static final float ICE_CY = 0.42f, ICE_ON_SCREEN = 0.46f;
    private static final float ICE_YAW = ThawMeshes.VIEW_YAW, ICE_PITCH = ThawMeshes.VIEW_PITCH;
    private static final float PICK_SCALE = 0.80f, PICK_YAW = -20f, PICK_PITCH = -8f;
    /** The pick's roll when its prong is in the ice, and at the top of its wind-up. */
    private static final float STRIKE_ROLL = -128f, RAISED_ROLL = -38f;
    private static final float TRAY_Y = 0.885f, SLOT = 0.085f, SLOT_GAP = 0.22f;
    /** Item names and the closing line. Low enough to clear the block even at full push-in. */
    private static final float CAPTION_Y = 0.77f;
    /** Where a finale's item comes to rest, and how big it gets there. */
    private static final float SHOW_Y = 0.40f;

    // ------------------------------------------------------------------ colours

    private static final int BACKDROP = 0x08101A;
    private static final int FLARE_GOLD = Tier.LEGENDARY.glow();
    private static final int FLARE_VIOLET = 0xFFB05CFF;
    private static final int FLARE_BLUE = 0xFF4F76B8;
    private static final int DYE_PINK = 0xFFFF5CC8;
    private static final int[] PAINT = {0xFFFF5CC8, 0xFFFF8AD8, 0xFFE0409F, 0xFFFFB3E6};
    private static final int COIN_GOLD = 0xFFFFC43A;

    /** How fast time runs in the special endings' slow motion. */
    private static final float SLOW_RATE = 0.25f;
    /** How long each letter of a big drop's name takes to stamp out. */
    private static final long LETTER_MS = 60;
    /** The Locket's NPC sell price: what is shown if the live price never arrives. */
    private static final long LOCKET_NPC = 5_000_000L;

    // The Locket's ending, in ms from the moment the last pick lands.
    private static final long LOCKET_ORBIT = 450, LOCKET_SNAP = 2600;
    /** How long the price counter takes to climb to the price. */
    private static final long COUNT_MS = 1500;

    // ------------------------------------------------------------------ state

    private final CorpseHaul haul;
    private final ThawTimeline tl;
    private final List<Strike> strikes;
    private final int n;
    private final int variant;
    /** Corpses since the last legendary, as it stood before this one was counted. */
    private final int streakBefore;
    /** Not final: "Quick Thaw" moves it back, so a bust's strikes are skipped. */
    private long openedAt = System.currentTimeMillis();
    private boolean quicked;
    private final Random rng;
    private final MeshRenderer meshes = new MeshRenderer();

    /** The Vanguard, in this corpse's pose. */
    private final Mesh figure;
    /** He is painted as a shadow; nothing is multiplied into it. */
    private static final int FIGURE_TINT = 0xFFFFFF;
    private static final Mesh PICK = ThawMeshes.pick();

    private boolean active = true;
    private boolean announced;

    // What has already fired, so each sound and burst happens exactly once however the
    // frames happen to fall.
    private int flared = -1, struck = -1, landed = -1;
    private boolean finaleFired, floodFired, trayFilledFired;
    /** The last step of the final strike's cycle that has sounded: -1 none, 0 the opening glow. */
    private int climbed = -1;
    private boolean beat1Fired, beat2Fired, swellFired, orbitFired, snapFired, countLanded;
    private int lettersSounded, coinTicks;
    /** The Locket's price, fixed the moment the counter starts so it cannot jump mid-count. */
    private LivePrice.Quote quote;

    // The special endings' pieces, all rolled once up front so nothing reshuffles per frame.
    private record Segment(float x0, float y0, float x1, float y1, float at0, float at1) {}
    private final List<Segment> crack = new ArrayList<>();
    private record Splat(float fx, float fy, float size, float rot, int variant, int argb, long delay,
                         boolean drips, float dripLen) {}
    private final List<Splat> splats = new ArrayList<>();
    private record Mote(float fx, float speed, float phase, float size, float sway, int argb) {}
    private final List<Mote> motes = new ArrayList<>();
    private record Coin(float fx, float drift, long delay, float vy, float size, float spin, float phase) {}
    private final List<Coin> coins = new ArrayList<>();

    /** Where the strike point was on the last frame, so bursts start from the ice. */
    private float strikeX, strikeY, iceX, iceY, iceSize;

    private float shakeMargin;

    /** An ice chip in flight. Positions are computed from the spawn, not integrated. */
    private record Shard(float x, float y, float vx, float vy, float spin, float spinV,
                         float size, long born, long life, int argb) {}
    private final List<Shard> shards = new ArrayList<>();

    public ThawScreen(CorpseHaul haul, int streakBefore) {
        super(Component.literal("Butcher"));
        this.haul = haul;
        this.rng = new Random(System.nanoTime());
        this.tl = new ThawTimeline(haul,
                ButcherClient.config == null ? 100 : ButcherClient.config.vanguardSpeed);
        this.strikes = tl.strikes();
        this.n = strikes.size();
        this.variant = rng.nextInt(ThawArt.VARIANTS);
        // A pose per corpse, one way round or the other, so a run of corpses is not the
        // same frozen man every time.
        ThawMeshes.Pose pose = ThawMeshes.POSES[rng.nextInt(ThawMeshes.POSES.length)];
        this.figure = ThawMeshes.vanguard(rng.nextBoolean() ? pose.mirrored() : pose);
        this.streakBefore = streakBefore;
        if (tl.finale() == Finale.SPECIAL) rollDye();
        if (tl.finale() == Finale.LOCKET) {
            rollCoins();
            // Normally already asked for when the loot message was read; asking again is
            // free, and covers any way in that skipped that.
            LivePrice.request(ButcherClient.LOCKET_ID);
        }
        ButcherAudio.onThawOpen(tl.intro());
    }

    public boolean isActive() { return active; }

    @Override protected void init() { }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) { }

    // ================================================================== frame

    /** When this screen opened, for the gameplay window's slide in. */
    private final long pipOpened = System.currentTimeMillis();

    /** The gameplay window slides in over the first 400 ms. */
    private float pipFade() {
        return Math.min(1f, (System.currentTimeMillis() - pipOpened) / 400f);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        long t = System.currentTimeMillis() - openedAt;
        if (t >= tl.total()) {
            close();
            return;
        }
        int sw = this.width, sh = this.height;
        // "Quick Thaw" (the menu; the review, T5): a corpse with nothing above common skips from the end of the intro
        // to its last strike, the strikes between run silently. Wins play whole - so a Thaw that keeps going is one.
        if (!quicked && ButcherClient.config != null && ButcherClient.config.vanguardQuick && tl.finale() == Finale.CRASH
                && t >= tl.intro()) {
            quicked = true;
            long target = tl.finaleStart() - 1500;
            if (t < target) {
                openedAt -= target - t;
                t = target;
                long at = target;
                ButcherAudio.silence();
                ButcherAudio.muted(() -> fireEvents(at, sw, sh));
            }
        }
        fireEvents(t, sw, sh);

        // The veil and every element fade up together at the start and out together at the
        // end, so the cutscene grows out of the game and dissolves back into it.
        float in = ease(clamp01(t / (float) Math.max(1L, tl.intro() * 6 / 10)));
        float out = t >= tl.fadeStart() ? 1f - ease(clamp01((t - tl.fadeStart()) / (float) tl.fadeLength())) : 1f;
        float master = in * out;

        // ---- the whole picture shakes, not just the models ----
        float[] shake = shake(t, sh);
        shakeMargin = sh * 0.05f + 2f;
        Matrix3x2fStack pose = g.pose();
        // The Dye's heartbeat: the whole frame thumps outward with each beat.
        float beat = heartbeat(t);
        boolean moved = shake[0] != 0f || shake[1] != 0f || beat > 0.001f;
        if (moved) {
            pose.pushMatrix();
            pose.translate(shake[0], shake[1]);
            if (beat > 0.001f) {
                float k = 1f + 0.022f * beat;
                pose.translate(sw / 2f, sh / 2f);
                pose.scale(k, k);
                pose.translate(-sw / 2f, -sh / 2f);
            }
        }

        long v = vt(t);
        fillFull(g, sw, sh, argb(0.87f * master, BACKDROP));
        drawVignette(g, sw, sh, t, master);

        drawFlare(g, sw, sh, t, master);
        drawIce(g, sw, sh, t, master);
        drawShards(g, v, master);
        drawPick(g, sw, sh, v, master);
        drawFrost(g, sw, sh, t, master);

        drawTray(g, sw, sh, t, master);
        drawFlights(g, sw, sh, t, master);
        drawCaption(g, sw, sh, t, master);
        drawFinale(g, sw, sh, t, master);
        drawOutro(g, sw, sh, t, master);
        drawLetterbox(g, sw, sh, t, master);

        if (moved) pose.popMatrix();
        // The game itself, live, in a corner (SkyMaxx's gameplay view) - drawn last, over everything.
        com.butcher.render.GameplayView.draw(g, pipFade(), 0xFF8FB8D8);
    }

    /**
     * The clock the physical things run on - the chips, the pick's bite, the block's jolt.
     *
     * The same as real time, except in the two special endings: from the moment the last
     * pick lands it runs at a quarter speed for {@link ThawTimeline#slowMotion()}, which is
     * the slow motion. After that the Locket's picks back up to full speed, and the Dye's
     * stops dead - a freeze-frame, chips hanging in the air - until the paint bursts.
     *
     * Only the physical things use it. Sounds, the tray and the ending's own moments stay on
     * real time, so slowing the picture down never stretches the run.
     */
    private long vt(long t) {
        if (!tl.special() || n == 0 || t < tl.finaleStart()) return t;
        long d = t - tl.finaleStart(), slow = tl.slowMotion();
        float v = SLOW_RATE * Math.min(d, slow);
        if (d > slow) {
            if (tl.finale() == Finale.LOCKET) v += d - slow;
            else if (d > tl.specialDrain()) v += d - tl.specialDrain();
        }
        return tl.finaleStart() + Math.round(v);
    }

    // ================================================================== the screen freezing

    /** Frost in from the corners and edges, a step per strike (render/ThawFrost). */
    private final com.butcher.render.ThawFrost frost = new com.butcher.render.ThawFrost(openedAt);
    /** Drawn as one image, not a rectangle per cell - see PixelLayer. */
    private static final com.butcher.render.PixelLayer FROST_LAYER = new com.butcher.render.PixelLayer("thaw_frost");

    /**
     * The screen freezing (the user, 2026-10-02). It grows a little through the intro, then a step at
     * every strike - the strikes land at the same moments whatever the haul, so it gives nothing away -
     * and the newest frost catches that strike's flare. At the landing it melts on a crash, cracks on a
     * reveal, and shatters on a legendary, the Locket (in its slow motion) or the Dye (when the paint
     * bursts).
     */
    private void drawFrost(GuiGraphicsExtractor g, int sw, int sh, long t, float master) {
        if (n == 0) return;
        float growth = 0.12f * ease(clamp01(t / (float) Math.max(1L, tl.intro())));
        long lastLand = -1;
        int glintRgb = FLARE_BLUE;
        for (Strike s : strikes) {
            if (t < s.lands()) break;
            growth += 0.88f * ease(clamp01((t - s.lands()) / 260f)) / n;
            lastLand = s.lands();
            glintRgb = flareColour(s.lands() - 1);
        }
        float glint = lastLand < 0 ? 0f : (float) Math.exp(-(t - lastLand) / 450.0);

        com.butcher.render.ThawFrost.End end = com.butcher.render.ThawFrost.End.NONE;
        float endT = 0f;
        long landed = strikes.get(n - 1).lands();
        if (t >= landed) {
            Finale f = tl.finale();
            end = switch (f) {
                case CRASH -> com.butcher.render.ThawFrost.End.MELT;
                case REVEAL -> com.butcher.render.ThawFrost.End.CRACK;
                default -> com.butcher.render.ThawFrost.End.SHATTER;
            };
            long start = f == Finale.SPECIAL ? tl.finaleStart() + tl.specialDrain() : landed + 80;
            long length = switch (f) {
                case CRASH -> 1200;
                case REVEAL -> 900;
                default -> 1100;
            };
            // The Locket's breaks in its slow motion.
            long clock = f == Finale.LOCKET ? vt(t) : t;
            endT = clamp01((clock - start) / (float) length);
            if (clock < start) end = com.butcher.render.ThawFrost.End.NONE;
        }
        FROST_LAYER.begin(sw, sh, com.butcher.render.ThawFrost.unitFor(sh));
        frost.draw(FROST_LAYER, sw, sh, Math.min(1f, growth), glintRgb, glint, end, endT, master);
        FROST_LAYER.end(g);
    }

    // ================================================================== events

    /** Sounds and bursts, each fired once, on the frame its moment is first passed. */
    private void fireEvents(long t, int sw, int sh) {
        for (Strike s : strikes) {
            int i = s.index();
            if (i > flared && t >= s.start()) {
                flared = i;
                // The last strike does not flare a colour - it climbs, below.
                if (flareOn() && !s.last()) ButcherAudio.onThawFlare(s.flare().ordinal(), s.windup());
            }
            if (s.last() && flareOn()) {
                int step = tl.climbStep(s, t);
                while (climbed < step && t < s.lands()) {
                    climbed++;
                    if (climbed == 0) {
                        // One riser under the whole cycle, cut dead on the landing.
                        long landing = tl.stepStart(s, ThawTimeline.LANDING);
                        ButcherAudio.onThawClimbWhite(tl.stepStart(s, 1) - t);
                        ButcherAudio.onThawBuildUp(landing - t);
                    } else if (climbed < ThawTimeline.LANDING) {
                        ButcherAudio.onThawCycleTick(climbed);
                    } else {
                        ButcherAudio.onThawClimb(s.truth() - 1, s.lands() - t);
                    }
                }
            }
            if (i > struck && t >= s.lands()) {
                struck = i;
                // With the flare off there is no chord to land on, so every ring sits on
                // the middle one.
                ButcherAudio.onThawStrike(i, n, flareOn() ? s.flare().ordinal() : 1);
                long v = vt(t);
                if (s.last() && tl.finale() == Finale.LOCKET) {
                    // The block goes, in slow motion.
                    burst(iceX, iceY, 70, sh * 0.0085f, v, 0xFFFFF2C8);
                } else {
                    burst(strikeX, strikeY, 7 + i * 2, sh * 0.0035f, v, 0xFFE8F8FF);
                }
            }
            if (i > landed && tl.goesStraightToTray(s) && t >= s.lands() + tl.flight()) {
                landed = i;
                Tier tier = s.drop().tier();
                ButcherAudio.onThawLand(tier == Tier.COMMON ? 0 : 1, i);
            }
        }

        if (!finaleFired && n > 0 && t >= tl.finaleStart()) {
            finaleFired = true;
            switch (tl.finale()) {
                case CRASH -> {
                    ButcherAudio.onThawCrash();
                    // A little rubble off the bottom of the block, and nothing else.
                    burst(iceX, iceY + iceSize * 0.42f, 10, sh * 0.0022f, t, 0xFFA8B8C8);
                }
                case REVEAL -> burst(strikeX, strikeY, 18, sh * 0.0045f, t, 0xFFE8F8FF);
                case EXPLODE -> {
                    ButcherAudio.onThawExplode();
                    burst(iceX, iceY, 70, sh * 0.0085f, t, 0xFFE8F8FF);
                }
                case LOCKET -> ButcherAudio.onThawSlowMotion(tl.slowMotion());
                case SPECIAL -> {
                    ButcherAudio.onThawSlowMotion(tl.slowMotion());
                    ButcherAudio.onThawDrain(tl.specialDrain());
                }
            }
        }
        if (n == 0 || t < tl.finaleStart()) return;
        long u = t - tl.finaleStart();

        if (tl.finale() == Finale.SPECIAL) {
            long drain = tl.specialDrain();
            if (!beat1Fired && u >= beatAt(0)) { beat1Fired = true; ButcherAudio.onThawHeartbeat(0); }
            if (!beat2Fired && u >= beatAt(1)) { beat2Fired = true; ButcherAudio.onThawHeartbeat(1); }
            // Starts in the silence after the heartbeats and is cut dead on the burst.
            long swellFrom = Math.round(drain * 0.70);
            if (!swellFired && u >= swellFrom) {
                swellFired = true;
                ButcherAudio.onThawReverseSwell(drain - u);
            }
            if (!floodFired && u >= drain) {
                floodFired = true;
                ButcherAudio.onThawFlood();
                burst(iceX, iceY, 80, sh * 0.009f, vt(t), 0xFFFFC2EA);
            }
            soundLetters(t, tl.finaleStart() + drain);
        }

        if (tl.finale() == Finale.LOCKET) {
            if (!orbitFired && u >= tl.paced(LOCKET_ORBIT)) {
                orbitFired = true;
                ButcherAudio.onThawMusicBox(tl.paced(LOCKET_SNAP - LOCKET_ORBIT));
            }
            if (!snapFired && u >= tl.paced(LOCKET_SNAP)) {
                snapFired = true;
                ButcherAudio.onThawLocketSnap();
            }
            long moment = tl.finaleStart() + tl.paced(LOCKET_SNAP);
            soundLetters(t, moment);
            long countAt = countStart(moment);
            if (t >= countAt && quote == null) {
                quote = LivePrice.get(ButcherClient.LOCKET_ID, LOCKET_NPC);
            }
            long countEnd = countAt + tl.paced(COUNT_MS);
            if (t >= countAt && t < countEnd) {
                // A tick every 70ms of counting, whatever the frame rate.
                int due = (int) ((t - countAt) / 70);
                while (coinTicks <= due) ButcherAudio.onThawCoin(coinTicks++);
            }
            if (!countLanded && t >= countEnd) {
                countLanded = true;
                ButcherAudio.onThawCoinLand();
            }
        }

        // The best item settling into the tray at the end of a winning run.
        if (!trayFilledFired && tl.finale() != Finale.CRASH && t >= tl.outroStart() + FINALE_TO_TRAY) {
            trayFilledFired = true;
            ButcherAudio.onThawLand(1, n - 1);
        }
    }

    /** Ticks for each letter of a special drop's name, as it is stamped out. */
    private void soundLetters(long t, long moment) {
        String name = strikes.get(n - 1).drop().name();
        int shown = lettersShown(t, moment, name);
        while (lettersSounded < shown) {
            // Spaces are silent - a tick on nothing reads as a stutter.
            if (name.charAt(lettersSounded) != ' ') ButcherAudio.onThawLetter(lettersSounded);
            lettersSounded++;
        }
    }

    /** Throws {@code count} chips outwards from a point, away from the pick. */
    private void burst(float x, float y, int count, float speed, long t, int argb) {
        for (int i = 0; i < count; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            float v = speed * (0.4f + rng.nextFloat());
            // Biased up and to the left, away from where the pick came in from.
            float vx = (float) Math.cos(a) * v - speed * 0.25f;
            float vy = (float) Math.sin(a) * v - speed * 0.55f;
            shards.add(new Shard(x, y, vx, vy, rng.nextFloat() * 6.28f, (rng.nextFloat() - 0.5f) * 0.02f,
                    3f + rng.nextFloat() * 4f, t, 520 + rng.nextInt(520), argb));
        }
    }

    // ================================================================== the block

    /** How many crack stages the front face has reached. */
    private int crackStage(long t) {
        int hits = tl.landedBy(t);
        if (hits == 0) return 0;
        if (hits >= n) return ThawArt.STAGES - 1;
        return Math.min(ThawArt.STAGES - 1, Math.round(hits / (float) n * (ThawArt.STAGES - 1)));
    }

    /** Whether the block has been blown apart and should no longer be drawn. */
    private boolean shattered(long t) {
        return switch (tl.finale()) {
            case EXPLODE, LOCKET -> t >= tl.finaleStart() + 40;
            case SPECIAL -> t >= tl.finaleStart() + tl.specialDrain();
            default -> false;
        };
    }

    /** The last strike to have landed by {@code t}, or null. */
    private Strike lastLanded(long t) {
        Strike last = null;
        for (Strike s : strikes) if (t >= s.lands()) last = s;
        return last;
    }

    /** 1 at the moment a pick lands, decaying to 0. Drives the block's jolt. */
    private float kick(long t) {
        Strike s = lastLanded(t);
        if (s == null) return 0f;
        // On the physical clock, so the jolt of the last hit plays out in slow motion.
        return (float) Math.exp(-(vt(t) - s.lands()) / 130.0);
    }

    /** Whether the Dye's drain has everything frozen in place. */
    private boolean drained(long t) {
        return tl.finale() == Finale.SPECIAL && t >= tl.finaleStart()
                && t < tl.finaleStart() + tl.specialDrain();
    }

    private Camera iceCamera(long t, int sh) {
        // On the physical clock: slowed at the big endings' impact, and frozen during the
        // Dye's drain - the held beat only reads if nothing at all is moving.
        long clock = vt(t);
        float sway = (float) Math.sin(clock / 1300.0) * 3f;
        float k = kick(t);
        float yaw = ICE_YAW + sway + k * 4f * (float) Math.sin((clock - (lastLanded(t) == null ? 0 : lastLanded(t).lands())) / 28.0);
        // A slow push-in over the run, like the cleaver's: the frame closes on the block
        // as the strikes add up.
        float push = 1f + 0.07f * (tl.landedBy(t) / (float) Math.max(1, n));
        float size = sh * ICE_ON_SCREEN * fit() / ThawMeshes.ICE_H * push * (1f - 0.03f * k);
        return Camera.of(yaw, ICE_PITCH, size);
    }

    private void drawIce(GuiGraphicsExtractor g, int sw, int sh, long t, float master) {
        Camera cam = iceCamera(t, sh);
        long clock = vt(t);

        // Rises out of the dark on the way in, overshooting and settling.
        float rise = easeOutBack(clamp01(t / (float) Math.max(1L, tl.intro())));
        float bob = (float) Math.sin(clock / 650.0) * sh * 0.006f;
        // Centred on what is DRAWN, not on the model's origin. The block is turned so one
        // side shows, which moves its visible mass off the origin - placing the origin at
        // the middle of the screen still left the block looking shifted against the tray
        // and the words, which are centred properly. So the on-screen box is measured and
        // that is put in the middle instead. Measured at the resting pose, not this
        // frame's sway, or the correction would itself make the block wobble sideways.
        float[] centre = drawnCentre(sh);
        iceX = sw / 2f - centre[0];
        iceY = sh * ICE_CY - centre[1] + bob + (1f - rise) * sh * 0.35f;
        iceSize = ThawMeshes.ICE_H * cam.size();

        // Where the pick lands on the front face, projected through this frame's camera, so
        // the pick and the bursts stay on the block as it sways.
        float[] p = ModelProjection.projectPoint(ThawMeshes.STRIKE_X, ThawMeshes.STRIKE_Y, ThawMeshes.STRIKE_Z, cam);
        strikeX = iceX + p[0];
        strikeY = iceY + p[1];

        if (shattered(t)) return;

        // The ice's colour: a little of the flare in it while it glows, and drained to a
        // dull grey-blue as an all-commons run comes to nothing.
        int r = 255, gr = 255, b = 255;
        float glow = Math.min(1f, flareEnvelope(t));
        int fc = flareColour(t);
        r = mix(r, fc >> 16 & 255, glow * 0.18f);
        gr = mix(gr, fc >> 8 & 255, glow * 0.18f);
        b = mix(b, fc & 255, glow * 0.18f);
        if (tl.finale() == Finale.CRASH && t >= tl.finaleStart()) {
            float dull = ease(clamp01((t - tl.finaleStart()) / 420f));
            r = mix(r, 150, dull);
            gr = mix(gr, 160, dull);
            b = mix(b, 176, dull);
        }
        int alpha = Math.round(235 * master);
        int iceTint = alpha << 24 | r << 16 | gr << 8 | b;

        // Three passes, not one sort: the far facets, the Vanguard, the near facets. See
        // ThawMeshes.iceFacing for why. The ice has no outline pass (that would fill the
        // inside black); he is solid, so his far faces are culled.
        String sheet = ThawArt.iceName(variant, crackStage(t));
        meshes.begin();
        meshes.camera(cam);
        meshes.add(ThawMeshes.iceFacing(false, cam.yawDeg(), cam.pitchDeg()), sheet, 0f, 0f, 0f, 0f, iceTint,
                false, false, false);
        meshes.flush(g, iceX, iceY);
        meshes.begin();
        meshes.camera(cam);
        meshes.add(figure, ThawArt.TEX_CORPSE, 0f, 0f, 0f, 0f, Math.round(255 * master) << 24 | FIGURE_TINT,
                false, false, true);
        meshes.flush(g, iceX, iceY);
        meshes.begin();
        meshes.camera(cam);
        meshes.add(ThawMeshes.iceFacing(true, cam.yawDeg(), cam.pitchDeg()), sheet, 0f, 0f, 0f, 0f, iceTint,
                false, false, false);
        meshes.flush(g, iceX, iceY);
    }

    /**
     * How much the block and pick shrink on a screen narrower than 16:9.
     *
     * Everything is sized from the screen's height, and the pick sits a fixed distance to
     * the right of the block - so on a 4:3 screen, or a narrow window, the raised pick ran
     * off the right edge. Scaling both together by the width shortfall keeps the whole
     * picture on screen and keeps its proportions. 1 at 16:9 and wider.
     */
    private float fit() {
        float aspect = this.width / (float) Math.max(1, this.height);
        return Math.min(1f, aspect / 1.78f);
    }

    /** The middle of the block's on-screen box at its resting pose, relative to its origin. */
    private float[] drawnCentre(int sh) {
        Camera rest = Camera.of(ICE_YAW, ICE_PITCH, sh * ICE_ON_SCREEN * fit() / ThawMeshes.ICE_H);
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] v : ThawMeshes.iceVertices()) {
            float[] p = ModelProjection.projectPoint(v[0], v[1], v[2], rest);
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
        }
        return new float[]{(minX + maxX) / 2f, (minY + maxY) / 2f};
    }

    // ================================================================== the flare

    /** The strike whose flare is showing at {@code t}: winding up, or just landed. */
    private Strike flaring(long t) {
        for (Strike s : strikes) {
            if (t >= s.start() && t < s.lands() + 260) return s;
        }
        return null;
    }

    private static boolean flareOn() {
        return ButcherClient.config == null || ButcherClient.config.vanguardFlare;
    }

    /** The name size setting, as a multiplier. */
    private static float nameScale() {
        int pct = ButcherClient.config == null ? 100 : ButcherClient.config.vanguardNameSize;
        return Math.max(0.4f, Math.min(2f, pct / 100f));
    }

    /** How lit the flare is, 0..1. */
    private float flareEnvelope(long t) {
        if (!flareOn() || drained(t) || shattered(t)) return 0f;
        Strike s = flaring(t);
        if (s == null) return 0f;
        if (t >= s.lands()) return (float) Math.exp(-(t - s.lands()) / 110.0);
        long u = t - s.start();
        if (s.last()) {
            // Up to a steady glow while the pick is raised, brightening through the cycle,
            // and full on the landing.
            float up = ease(clamp01(u / (float) Math.max(1L, s.climbStart() - s.start())));
            int step = tl.climbStep(s, t);
            if (step >= ThawTimeline.LANDING) return 1f;
            float through = step <= 0 ? 0f : step / (float) ThawTimeline.LANDING;
            return up * (0.72f + 0.2f * through);
        }
        return ease(clamp01(u / (float) Math.max(1L, (long) (s.windup() * 0.55f))));
    }

    private int flareColour(long t) {
        Strike s = flaring(t);
        if (s == null) return FLARE_BLUE;
        if (s.last()) return cycleColour(s, t);
        return switch (s.flare()) {
            case GOLD -> FLARE_GOLD;
            case VIOLET -> FLARE_VIOLET;
            default -> FLARE_BLUE;
        };
    }

    /**
     * The last strike's colour: fading from the step before into this one, never cut.
     * Each fade takes most of its step, so the fast end of the cycle is a continuous shimmer
     * of colour rather than a flicker.
     */
    private int cycleColour(Strike s, long t) {
        int step = t >= s.lands() ? ThawTimeline.LANDING : tl.climbStep(s, t);
        if (step <= 0) return FLARE_BLUE;
        int to = colourOf(ThawTimeline.stepColour(s, step));
        int from = colourOf(ThawTimeline.stepColour(s, step - 1));
        long start = tl.stepStart(s, step);
        long length = step < ThawTimeline.LANDING ? tl.stepStart(s, step + 1) - start : 200;
        float f = ease(clamp01((t - start) / Math.max(1f, length * 0.7f)));
        return mixColour(from, to, f);
    }

    private static int colourOf(int id) {
        return switch (id) {
            case 3 -> FLARE_GOLD;
            case 2 -> FLARE_VIOLET;
            default -> FLARE_BLUE;
        };
    }

    private static int mixColour(int a, int b, float f) {
        return 0xFF000000 | mix(a >> 16 & 255, b >> 16 & 255, f) << 16
                | mix(a >> 8 & 255, b >> 8 & 255, f) << 8 | mix(a & 255, b & 255, f);
    }

    private void drawFlare(GuiGraphicsExtractor g, int sw, int sh, long t, float master) {
        float e = Math.min(1f, flareEnvelope(t)) * master;
        if (e <= 0.01f) return;
        Identifier glow = TextureBank.id(PropArt.TEX_GEM_GLOW);
        if (glow == null) return;
        int c = flareColour(t);
        // Twice: a wide pool of colour, and a hot core close behind the block.
        float wide = sh * 1.35f, core = sh * 0.80f;
        blit(g, glow, iceX - wide / 2f, iceY - wide / 2f, wide, wide, argb(e * 0.95f, c));
        blit(g, glow, iceX - core / 2f, iceY - core / 2f, core, core, argb(e, c));
    }

    // ================================================================== the pick

    private void drawPick(GuiGraphicsExtractor g, int sw, int sh, long t, float master) {
        float angle;
        float slide = 0f;
        float fade = 1f;
        float lift = 0f;

        // The pick leaves once its work is done - except during the Dye's drain, where it
        // stays buried in the ice, dead still, until the colour comes back. {@code t} here is
        // the physical clock (see vt), so the drain's end is where that clock resumes.
        long exitAt = n == 0 ? 0 : strikes.get(n - 1).lands()
                + (tl.finale() == Finale.SPECIAL ? vt(tl.finaleStart() + tl.specialDrain()) - tl.finaleStart() : 160);
        if (t >= exitAt + 450) return;

        Strike s = tl.strikeAt(t);
        if (t < tl.intro() || n == 0) {
            angle = RAISED_ROLL;
            slide = 1f - easeOutCubic(clamp01((t - tl.intro() * 0.25f) / (tl.intro() * 0.75f)));
        } else if (t >= exitAt) {
            // Done: lifted up and away, so the ending has the frame to itself.
            float u = clamp01((t - exitAt) / 450f);
            angle = lerp(STRIKE_ROLL, RAISED_ROLL + 25f, easeOutCubic(u));
            lift = easeIn(u);
            fade = 1f - u;
        } else if (s == null) {
            angle = STRIKE_ROLL;
        } else {
            long u = t - s.start();
            float from = s.index() == 0 ? RAISED_ROLL : STRIKE_ROLL;
            // The last strike's wind-up is long because of the climb, not because the pick
            // is heavier: it goes up at the usual pace and then holds there, straining.
            long raise = s.last() ? Math.max(1L, s.climbStart() - s.start()) : s.windup();
            if (u < s.windup()) {
                angle = lerp(from, RAISED_ROLL, easeOutCubic(clamp01(u / (raise * 0.7f))));
                // Trembling at the top of the last wind-up - held, straining, about to go.
                if (s.last() && u > raise) {
                    float strain = clamp01((u - raise) / 300f);
                    angle += (float) Math.sin(u / 24.0) * 1.8f * strain;
                }
            } else if (u < s.windup() + s.fall()) {
                // Accelerating all the way in: the release is the fast part.
                float f = (u - s.windup()) / (float) s.fall();
                angle = lerp(RAISED_ROLL, STRIKE_ROLL, f * f);
            } else {
                // Bites in, bounces back a touch, settles.
                float v = u - s.windup() - s.fall();
                angle = STRIKE_ROLL - 7f * (float) Math.exp(-v / 85.0) * (float) Math.cos(v / 30.0);
            }
        }

        float pickSize = sh * ICE_ON_SCREEN * fit() / ThawMeshes.ICE_H * PICK_SCALE;
        // The pivot is solved from the strike pose, so that whatever the ice is doing the
        // prong's point lands exactly on the strike point.
        Camera strikeCam = Camera.of(PICK_YAW, PICK_PITCH, pickSize).roll(STRIKE_ROLL);
        float[] tip = ModelProjection.projectPoint(ThawMeshes.PICK_TIP_X, ThawMeshes.PICK_TIP_Y, 0f, strikeCam);
        float pivotX = strikeX - tip[0] + slide * sw * 0.55f;
        float pivotY = strikeY - tip[1] - lift * sh * 0.25f;

        int a = Math.round(255 * master * fade);
        if (a <= 3) return;
        meshes.begin();
        meshes.camera(Camera.of(PICK_YAW, PICK_PITCH, pickSize).roll(angle));
        meshes.add(PICK, ThawArt.TEX_PICK, 0f, 0f, 0f, 0f, a << 24 | 0xFFFFFF, true, false, true);
        meshes.flush(g, pivotX, pivotY, a << 24, Math.max(1.5f, sw / 700f));
    }

    // ================================================================== bits of ice

    private void drawShards(GuiGraphicsExtractor g, long t, float master) {
        Identifier tex = TextureBank.id(ThawArt.TEX_SHARD);
        if (tex == null) return;
        Matrix3x2fStack pose = g.pose();
        float gravity = 0.0011f * height / 360f;
        shards.removeIf(s -> t - s.born() > s.life());
        for (Shard s : shards) {
            float u = t - s.born();
            float life = u / s.life();
            float x = s.x() + s.vx() * u;
            float y = s.y() + s.vy() * u + 0.5f * gravity * u * u;
            float a = (1f - life * life) * master;
            if (a <= 0.02f) continue;
            float size = s.size() * (1f - 0.4f * life);
            pose.pushMatrix();
            pose.translate(x, y);
            pose.rotate(s.spin() + s.spinV() * u);
            blit(g, tex, -size / 2f, -size / 2f, size, size, argb(a, s.argb()));
            pose.popMatrix();
        }
    }

    // ================================================================== the tray

    private float slotSize(int sh) { return sh * SLOT; }

    private float slotX(int i, int sw, int sh) {
        float s = slotSize(sh), gap = s * SLOT_GAP;
        float width = n * s + (n - 1) * gap;
        return sw / 2f - width / 2f + i * (s + gap) + s / 2f;
    }

    private float slotY(int sh) { return sh * TRAY_Y; }

    /** When the item for strike {@code s} is sitting in its slot, or -1 if it never goes there straight. */
    private long arrives(Strike s) {
        return tl.goesStraightToTray(s) ? s.lands() + tl.flight() : tl.outroStart() + FINALE_TO_TRAY;
    }

    /** How long a winning run's best item takes to get from the middle of the screen to its slot. */
    private static final long FINALE_TO_TRAY = 480;

    private void drawTray(GuiGraphicsExtractor g, int sw, int sh, long t, float master) {
        float s = slotSize(sh);
        float y = slotY(sh);
        for (Strike st : strikes) {
            int i = st.index();
            // The slots arrive one after another during the intro, left to right.
            float appear = easeOutBack(clamp01((t - tl.intro() * 0.35f - i * 45f) / 220f));
            if (appear <= 0.01f) continue;
            float x = slotX(i, sw, sh);
            float half = s / 2f * appear;
            long arrived = arrives(st);
            boolean filled = t >= arrived;
            Tier tier = st.drop().tier();

            int border = filled ? tierColour(tier) : 0xFF6F95B5;
            // A filled legendary or better glows behind its slot for the rest of the run.
            if (filled && tier.ordinal() >= Tier.LEGENDARY.ordinal()) {
                Identifier glow = TextureBank.id(PropArt.TEX_GEM_GLOW);
                if (glow != null) {
                    float gs = s * 2.6f * (1f + 0.06f * (float) Math.sin(t / 240.0));
                    blit(g, glow, x - gs / 2f, y - gs / 2f, gs, gs, argb(0.9f * master, tier.glow()));
                }
            }
            g.fill(Math.round(x - half), Math.round(y - half), Math.round(x + half), Math.round(y + half),
                    argb(0.34f * master, 0x0A1622));
            outline(g, x - half, y - half, half * 2f, half * 2f, argb((filled ? 0.95f : 0.45f) * master, border));

            if (filled) {
                // A little pop as it lands, then still.
                float pop = 1f + 0.22f * (float) Math.exp(-(t - arrived) / 90.0);
                float size = s * 0.78f * pop;
                VanguardItems.draw(g, meshes, st.drop(), x, y, size, VanguardItems.REST_YAW,
                        Math.round(255 * master), true);
                // The two big drops keep a sheen running across their slot for the rest of
                // the run, so the tray still says which corpse this was.
                if (st.drop().tier() == Tier.SPECIAL || isLocket(st.drop())) {
                    shimmer(g, x, y, half, t - arrived,
                            st.drop().tier() == Tier.SPECIAL ? DYE_PINK : FLARE_GOLD, master);
                }
                // The guard is not cosmetic: the font renderer treats an alpha below 4 as
                // fully opaque, so a count faded nearly to nothing would flash back on.
                if (st.drop().amount() > 1 && master > 0.03f) {
                    String count = Integer.toString(st.drop().amount());
                    g.text(font, Component.literal(count), Math.round(x + half - font.width(count) - 1),
                            Math.round(y + half - 9), argb(master, 0xFFFFFF), true);
                }
            }
        }
    }

    /** Items in the air between the ice and their slots. */
    private void drawFlights(GuiGraphicsExtractor g, int sw, int sh, long t, float master) {
        float s = slotSize(sh);
        for (Strike st : strikes) {
            if (!tl.goesStraightToTray(st)) continue;
            if (t < st.lands() || t >= arrives(st)) continue;
            float p = clamp01((t - st.lands()) / (float) tl.flight());
            float ex = slotX(st.index(), sw, sh), ey = slotY(sh);
            // A thrown arc: out of the ice, up, and down into the slot.
            float cx = lerp(strikeX, ex, 0.5f), cy = Math.min(strikeY, ey) - sh * 0.16f;
            float q = easeOutCubic(p);
            float x = bezier(strikeX, cx, ex, q), y = bezier(strikeY, cy, ey, q);
            float size = lerp(s * 0.35f, s * 0.78f, easeOutCubic(p));
            float yaw = VanguardItems.REST_YAW + (1f - q) * 540f;
            VanguardItems.draw(g, meshes, st.drop(), x, y, size, yaw, Math.round(255 * master), true);
        }
    }

    // ================================================================== words

    /** The name of the drop that just came out, under the block. */
    private void drawCaption(GuiGraphicsExtractor g, int sw, int sh, long t, float master) {
        if (t >= tl.finaleStart() && tl.finale() != Finale.CRASH) return;
        Strike s = lastLanded(t);
        if (s == null || !tl.goesStraightToTray(s)) return;
        long u = t - s.lands();
        if (u > 1000) return;
        float pop = easeOutBack(clamp01(u / 220f));
        float a = u < 700 ? 1f : 1f - (u - 700) / 300f;
        if (a <= 0.02f) return;
        outlinedText(g, dropLabel(s.drop()), sw / 2f, sh * CAPTION_Y, 2.1f * nameScale() * pop,
                argb(a * master, s.drop().entry().colour()));
    }

    private static Component dropLabel(CorpseHaul.Drop d) {
        return Component.literal(d.amount() > 1 ? d.name() + " x" + d.amount() : d.name());
    }

    // ================================================================== the endings

    private void drawFinale(GuiGraphicsExtractor g, int sw, int sh, long t, float master) {
        if (n == 0 || t < tl.finaleStart()) return;
        long u = t - tl.finaleStart();
        Strike last = strikes.get(n - 1);

        switch (tl.finale()) {
            case CRASH -> {
                // Nothing to show off. The ice has already gone dull; this is the silence.
            }
            case REVEAL -> showItem(g, sw, sh, t, u, last, 0.20f, Tier.UNCOMMON.glow(), false, master);
            case EXPLODE -> {
                // The white of the detonation, gone in a quarter of a second.
                float flash = 1f - clamp01(u / 260f);
                if (flash > 0f) fillFull(g, sw, sh, argb(0.85f * flash * master, 0xFFFFFF));
                drawRays(g, sw, sh, t, u, FLARE_GOLD, master);
                showItem(g, sw, sh, t, u, last, 0.29f, FLARE_GOLD, true, master);
            }
            case LOCKET -> {
                long snap = tl.paced(LOCKET_SNAP);
                // The block going. In slow motion, so the flash lingers rather than blinks.
                float flash = 1f - clamp01(u / (float) tl.paced(700));
                if (flash > 0f) fillFull(g, sw, sh, argb(0.55f * flash * master, 0xFFF2C8));
                if (u < snap) {
                    drawPieces(g, sw, sh, u, snap, master);
                } else {
                    long v = u - snap;
                    float snapFlash = 1f - clamp01(v / 450f);
                    if (snapFlash > 0f) fillFull(g, sw, sh, argb(0.9f * snapFlash * master, 0xFFF0B0));
                    drawRays(g, sw, sh, t, v, FLARE_GOLD, master);
                    drawCoins(g, sw, sh, t, v, master);
                    showItem(g, sw, sh, t, v, last, SPECIAL_FRAC, FLARE_GOLD, true, master);
                    drawSpecialText(g, sw, sh, t, tl.finaleStart() + snap, last, FLARE_GOLD, master);
                }
            }
            case SPECIAL -> {
                long drain = tl.specialDrain();
                if (u < drain) {
                    // The colour goes out of everything - the tray included, since this is
                    // drawn over it. Not a fade to black: a fade to grey, held, because what
                    // is being taken away is the colour, not the picture.
                    float d = ease(clamp01(u / (tl.slowMotion() * 0.8f)));
                    fillFull(g, sw, sh, argb(0.80f * d * master, GREY));
                    // The only colour left on screen.
                    drawCrack(g, u, drain, master);
                } else {
                    long v = u - drain;
                    // The colour floods back: the grey lifts, under a pink flash.
                    float grey = 1f - ease(clamp01(v / 600f));
                    if (grey > 0f) fillFull(g, sw, sh, argb(0.80f * grey * master, GREY));
                    float flash = 1f - clamp01(v / 420f);
                    if (flash > 0f) fillFull(g, sw, sh, argb(0.75f * flash * master, 0xFFB8E4));
                    drawSplats(g, sw, sh, t, v, master);
                    drawRays(g, sw, sh, t, v, DYE_PINK, master);
                    showItem(g, sw, sh, t, v, last, SPECIAL_FRAC, DYE_PINK, true, master);
                    drawMotes(g, sw, sh, v, master);
                    drawSpecialText(g, sw, sh, t, tl.finaleStart() + drain, last, DYE_PINK, master);
                }
            }
        }
    }

    /** The grey the Dye drains everything to. */
    private static final int GREY = 0x2E2F33;
    /** How big the two special drops get in the middle of the screen. */
    private static final float SPECIAL_FRAC = 0.26f;
    // The special endings' lines of text, under the item.
    private static final float NAME_Y = 0.575f, RARITY_Y = 0.655f, NTH_Y = 0.70f,
            PRICE_Y = 0.765f, PRICE_LABEL_Y = 0.81f;

    /**
     * A winning run's best drop, lifted out of the ice into the middle of the screen.
     *
     * @param u     time since this item's moment began
     * @param frac  how big it gets, as a fraction of screen height
     */
    private void showItem(GuiGraphicsExtractor g, int sw, int sh, long t, long u, Strike st,
                          float frac, int glowColour, boolean big, float master) {
        // After the ending it flies down into its slot; drawTray takes over once it lands.
        long toTray = tl.outroStart();
        float cx = sw / 2f, cy = sh * SHOW_Y;
        Finale f = tl.finale();
        // The Locket is already in the middle: its pieces came together there.
        float fromX = f == Finale.LOCKET ? cx : (big ? iceX : strikeX);
        float fromY = f == Finale.LOCKET ? cy : (big ? iceY : strikeY);

        float rise = easeOutCubic(clamp01(u / 520f));
        float x = lerp(fromX, cx, rise), y = lerp(fromY, cy, rise) - (float) Math.sin(rise * Math.PI) * sh * 0.04f;
        float size = sh * frac * easeOutBack(clamp01(u / 620f));
        // Fast spin out of the ice, slowing to a gentle sway once it is up - except the
        // Dye, whose bottle keeps turning for the whole ending.
        float spin = f == Finale.SPECIAL
                ? (1f - rise) * 1440f + u * 0.09f
                : (1f - rise) * 900f + (float) Math.sin(u / 520.0) * 12f;

        float outP = 0f;
        if (t >= toTray) {
            outP = clamp01((t - toTray) / (float) FINALE_TO_TRAY);
            float q = ease(outP);
            x = lerp(cx, slotX(st.index(), sw, sh), q);
            y = lerp(cy, slotY(sh), q);
            size = lerp(sh * frac, slotSize(sh) * 0.78f, q);
            if (outP >= 1f) return;
        }

        // Dim the scene a little so the item carries the frame, and light it from behind.
        float stage = (1f - outP) * master;
        if (!big) fillFull(g, sw, sh, argb(0.35f * rise * stage, 0x000000));
        Identifier glow = TextureBank.id(tl.special() ? ThawArt.TEX_AURA : PropArt.TEX_GEM_GLOW);
        if (glow != null) {
            float gs = size * (tl.special() ? 3.4f : big ? 4.2f : 3.2f) * (1f + 0.05f * (float) Math.sin(u / 200.0));
            blit(g, glow, x - gs / 2f, y - gs / 2f, gs, gs, argb(stage * (tl.special() ? 0.85f : 1f), glowColour));
        }
        VanguardItems.draw(g, meshes, st.drop(), x, y, size, VanguardItems.REST_YAW + spin,
                Math.round(255 * master), true);
        if (f == Finale.LOCKET) cardShine(g, x, y, size, u, stage);

        // Its name, under it, while it is up. The special endings stamp theirs out instead.
        if (tl.special()) return;
        float nameA = clamp01((u - 220) / 260f) * (1f - outP);
        if (nameA > 0.02f) {
            int colour = big ? glowColour : st.drop().entry().colour();
            outlinedText(g, dropLabel(st.drop()), cx, cy + sh * frac * 0.62f + 10f,
                    (big ? 2.8f : 2.2f) * nameScale(),
                    argb(nameA * master, colour));
        }
    }

    // ================================================================== the special endings

    private static boolean isLocket(CorpseHaul.Drop d) {
        return "Shattered Locket".equals(d.name());
    }

    private long nameStart(long moment) { return moment + tl.paced(350); }

    /** How many letters of the name are showing at {@code t}. */
    private int lettersShown(long t, long moment, String name) {
        long start = nameStart(moment);
        if (t < start) return 0;
        return (int) Math.min(name.length(), 1 + (t - start) / Math.max(1L, tl.paced(LETTER_MS)));
    }

    private long nameEnd(long moment, String name) {
        return nameStart(moment) + tl.paced(LETTER_MS) * name.length();
    }

    private long rarityAt(long moment, String name) { return nameEnd(moment, name) + tl.paced(250); }
    private long nthAt(long moment, String name) { return rarityAt(moment, name) + tl.paced(350); }

    /** When the Locket's price counter starts. */
    private long countStart(long moment) {
        return nthAt(moment, strikes.get(n - 1).drop().name()) + tl.paced(300);
    }

    /**
     * The name, stamped out letter by letter, then the odds, then the player's own count,
     * and for the Locket its price counting up.
     *
     * @param moment when the item arrived - the snap for the Locket, the burst for the Dye
     */
    private void drawSpecialText(GuiGraphicsExtractor g, int sw, int sh, long t, long moment,
                                 Strike st, int colour, float master) {
        float out = t >= tl.outroStart() ? 1f - clamp01((t - tl.outroStart()) / 300f) : 1f;
        float a = master * out;
        if (a <= 0.03f) return;
        String name = st.drop().name();
        float cx = sw / 2f;

        int shown = lettersShown(t, moment, name);
        if (shown > 0) {
            outlinedText(g, name, shown, cx, sh * NAME_Y, 2.8f * nameScale(), argb(a, colour));
        }

        long rarity = rarityAt(moment, name);
        if (t >= rarity) {
            float pop = easeOutBack(clamp01((t - rarity) / 220f));
            outlinedText(g, Component.literal(oddsLine(st.drop())), cx, sh * RARITY_Y, 1.3f * pop,
                    argb(a * clamp01((t - rarity) / 150f), 0xC8D2E0));
        }
        long nth = nthAt(moment, name);
        if (t >= nth) {
            float pop = easeOutBack(clamp01((t - nth) / 220f));
            outlinedText(g, Component.literal(countLine(name)), cx, sh * NTH_Y, 1.3f * pop,
                    argb(a * clamp01((t - nth) / 150f), 0xFFFFFF));
        }

        if (tl.finale() != Finale.LOCKET) return;
        long count = countStart(moment);
        if (t < count || quote == null) return;
        float p = easeOutCubic(clamp01((t - count) / (float) tl.paced(COUNT_MS)));
        long shownCoins = Math.round(quote.coins() * (double) p);
        // A little kick when it arrives on the number.
        long landed = count + tl.paced(COUNT_MS);
        float kick = t >= landed ? 1f + 0.18f * (float) Math.exp(-(t - landed) / 120.0) : 1f;
        outlinedText(g, Component.literal(String.format("%,d coins", shownCoins)), cx, sh * PRICE_Y,
                2.0f * kick, argb(a, FLARE_GOLD));
        String label = quote.live() ? "lowest BIN on the auction house"
                : ButcherClient.config != null && ButcherClient.config.livePrices
                ? "NPC sell price - the live price could not be fetched"
                : "NPC sell price";
        outlinedText(g, Component.literal(label), cx, sh * PRICE_LABEL_Y, 0.85f,
                argb(a * clamp01((t - count) / 300f), 0x9FB3C8));
    }

    /**
     * "1 in ~540 corpses": how often a corpse has at least one of this, from the drop table.
     * Uses the player's own average rolls per corpse once there are enough corpses to trust
     * it, and the middle of 5-8 before that.
     */
    private static String oddsLine(CorpseHaul.Drop d) {
        ButcherStats stats = ButcherStats.get();
        double rolls = stats.corpses >= 10 ? stats.corpseRolls / (double) stats.corpses : 6.5;
        double perCorpse = 1 - Math.pow(1 - d.entry().perRoll(), rolls);
        if (perCorpse <= 0) return "";
        long oneIn = Math.round(1 / perCorpse);
        if (oneIn >= 100) oneIn = Math.round(oneIn / 10.0) * 10;
        return String.format("1 in ~%,d corpses", oneIn);
    }

    /** "Your first ever Frostbitten Dye!", or "Your 3rd Shattered Locket". */
    private static String countLine(String name) {
        int count = Math.max(1, ButcherStats.get().itemCount(name));
        if (count == 1) return "Your first ever " + name + "!";
        return "Your " + ordinal(count) + " " + name;
    }

    private static String ordinal(int n) {
        int mod100 = n % 100;
        String suffix = mod100 >= 11 && mod100 <= 13 ? "th" : switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return n + suffix;
    }

    /** Black bars closing in from top and bottom for the two big endings: this is a scene now. */
    private void drawLetterbox(GuiGraphicsExtractor g, int sw, int sh, long t, float master) {
        if (!tl.special() || n == 0 || t < tl.finaleStart()) return;
        float in = easeOutCubic(clamp01((t - tl.finaleStart()) / 500f));
        float out = t >= tl.outroStart() ? 1f - ease(clamp01((t - tl.outroStart()) / 450f)) : 1f;
        // 7% leaves the tray clear of the bottom bar.
        float h = sh * 0.07f * in * out;
        if (h < 0.5f) return;
        int m = Math.round(shakeMargin);
        int c = argb(master, 0x000000);
        g.fill(-m, -m, sw + m, Math.round(h), c);
        g.fill(-m, Math.round(sh - h), sw + m, sh + m, c);
    }

    // ---- the Locket

    /**
     * The Locket's pieces: out of the exploding block, onto an orbit, and round faster and
     * tighter until they meet in the middle, with a gold light growing where they will.
     */
    private void drawPieces(GuiGraphicsExtractor g, int sw, int sh, long u, long snap, float master) {
        Identifier shard = TextureBank.id(ThawArt.TEX_SHARD), aura = TextureBank.id(ThawArt.TEX_AURA);
        if (shard == null || aura == null) return;
        long orbit = tl.paced(LOCKET_ORBIT);
        float cx = sw / 2f, cy = sh * SHOW_Y;
        float fly = easeOutCubic(clamp01((u - 120) / (float) Math.max(1L, orbit)));
        if (fly <= 0f) return;
        float p = clamp01((u - orbit) / (float) Math.max(1L, snap - orbit));
        float radius = sh * 0.24f * (float) Math.pow(1f - p, 1.4);
        double turn = Math.PI * 2 * (0.15 * u / 1000.0 + 0.5 * p + 1.6 * p * p * p);

        float glowS = sh * (0.22f + 0.6f * p * p);
        blit(g, aura, cx - glowS / 2f, cy - glowS / 2f, glowS, glowS, argb(master * fly * (0.25f + 0.6f * p * p), FLARE_GOLD));

        Matrix3x2fStack pose = g.pose();
        int pieces = 6;
        for (int i = 0; i < pieces; i++) {
            double a = turn + i * Math.PI * 2 / pieces;
            // A flattened ellipse, so the orbit reads as a ring seen at an angle.
            float ox = (float) Math.cos(a) * radius, oy = (float) Math.sin(a) * radius * 0.55f;
            float px = lerp(iceX, cx + ox, fly), py = lerp(iceY, cy + oy, fly);
            float size = sh * 0.05f * (1f + 0.3f * p);
            float halo = size * 3f;
            blit(g, aura, px - halo / 2f, py - halo / 2f, halo, halo, argb(0.55f * master, FLARE_GOLD));
            pose.pushMatrix();
            pose.translate(px, py);
            pose.rotate((float) (a * 1.7 + u / 180.0));
            blit(g, shard, -size / 2f, -size / 2f, size, size, argb(master, COIN_GOLD));
            pose.popMatrix();
        }
    }

    /** Rolls the Locket's coin rain: two waves, a few coins a frame. */
    private void rollCoins() {
        Random r = new Random(rng.nextLong());
        for (int i = 0; i < 90; i++) {
            coins.add(new Coin(0.03f + r.nextFloat() * 0.94f, (r.nextFloat() - 0.5f) * 0.00008f,
                    (long) (r.nextFloat() * r.nextFloat() * 2800), r.nextFloat() * 0.00025f,
                    0.024f + r.nextFloat() * 0.018f, 0.006f + r.nextFloat() * 0.01f, r.nextFloat() * 6.28f));
        }
    }

    private void drawCoins(GuiGraphicsExtractor g, int sw, int sh, long t, long v, float master) {
        Identifier tex = TextureBank.id(ThawArt.TEX_COIN);
        if (tex == null) return;
        float out = t >= tl.outroStart() ? 1f - clamp01((t - tl.outroStart()) / 400f) : 1f;
        float a = master * out;
        if (a <= 0.02f) return;
        for (Coin c : coins) {
            long vv = v - tl.paced(c.delay());
            if (vv < 0) continue;
            float size = sh * c.size();
            float y = -size + c.vy() * sh * vv + 0.5f * 0.0000009f * sh * vv * vv;
            if (y > sh + size) continue;
            float x = c.fx() * sw + c.drift() * sw * vv;
            // Spun by squeezing its width: a coin edge-on is a line.
            float w = size * Math.max(0.12f, Math.abs((float) Math.cos(c.spin() * vv + c.phase())));
            blit(g, tex, x - w / 2f, y - size / 2f, w, size, argb(a, 0xFFFFFF));
        }
    }

    /**
     * A trading card's shine: a bright diagonal band sweeping across the item every couple
     * of seconds. Clipped to a box a little inside the item, so it reads as on the item and
     * not on the air around it.
     */
    private void cardShine(GuiGraphicsExtractor g, float x, float y, float size, long u, float a) {
        long first = 600, period = 2200;
        if (u < first || a <= 0.02f) return;
        float p = ((u - first) % period) / 420f;
        if (p >= 1f) return;
        float half = size * 0.40f;
        g.enableScissor(Math.round(x - half), Math.round(y - half), Math.round(x + half), Math.round(y + half));
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x + lerp(-size, size, ease(p)), y);
        pose.rotate(0.45f);
        g.fill(Math.round(-size * 0.09f), Math.round(-size), Math.round(size * 0.09f), Math.round(size), argb(0.45f * a, 0xFFFFFF));
        g.fill(Math.round(size * 0.14f), Math.round(-size), Math.round(size * 0.18f), Math.round(size), argb(0.35f * a, 0xFFFFFF));
        pose.popMatrix();
        g.disableScissor();
    }

    /** The sheen across a special drop's tray slot, and two sparkles on its corners. */
    private void shimmer(GuiGraphicsExtractor g, float x, float y, float half, long since, int colour, float master) {
        float p = Math.floorMod(since, 1800L) / 520f;
        if (p < 1f) {
            g.enableScissor(Math.round(x - half), Math.round(y - half), Math.round(x + half), Math.round(y + half));
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            pose.translate(x + lerp(-half * 2.2f, half * 2.2f, ease(p)), y);
            pose.rotate(0.5f);
            g.fill(Math.round(-half * 0.35f), Math.round(-half * 2f), Math.round(half * 0.35f), Math.round(half * 2f),
                    argb(0.30f * master, colour));
            g.fill(Math.round(-half * 0.12f), Math.round(-half * 2f), Math.round(half * 0.12f), Math.round(half * 2f),
                    argb(0.40f * master, 0xFFFFFF));
            pose.popMatrix();
            g.disableScissor();
        }
        Identifier sparkle = TextureBank.id(ButcherClient.TEX_SPARKLE);
        if (sparkle == null) return;
        for (int i = 0; i < 2; i++) {
            float tw = 0.5f + 0.5f * (float) Math.sin(since / 260.0 + i * 2.4);
            float s = half * (0.55f + 0.25f * tw);
            float sx = x + (i == 0 ? half * 0.72f : -half * 0.78f), sy = y + (i == 0 ? -half * 0.72f : half * 0.62f);
            blit(g, sparkle, sx - s / 2f, sy - s / 2f, s, s, argb(master * tw, colour));
        }
    }

    // ---- the Dye

    /** Rolls the Dye's crack, splats and sparkles once, so nothing reshuffles per frame. */
    private void rollDye() {
        Random r = new Random(rng.nextLong());

        // The crack, in units of the block's height from the strike point: six branches
        // wandering outwards, some forking. {@code at} is how far along the spread each
        // segment is, so it grows from the point of impact outwards.
        List<float[]> raw = new ArrayList<>();
        for (int b = 0; b < 6; b++) {
            growCrack(raw, r, 0f, 0f, b * Math.PI * 2 / 6 + r.nextGaussian() * 0.3, 0f, 0);
        }
        float far = 0f;
        for (float[] s : raw) far = Math.max(far, s[5]);
        for (float[] s : raw) crack.add(new Segment(s[0], s[1], s[2], s[3], s[4] / far, s[5] / far));

        // Splats, kept out of the middle, where the bottle is about to appear.
        while (splats.size() < 16) {
            float fx = 0.04f + r.nextFloat() * 0.92f, fy = 0.06f + r.nextFloat() * 0.80f;
            if (Math.abs(fx - 0.5f) < 0.17f && Math.abs(fy - SHOW_Y) < 0.26f) continue;
            splats.add(new Splat(fx, fy, 0.10f + r.nextFloat() * 0.20f, r.nextFloat() * 6.28f,
                    r.nextInt(ThawArt.SPLATS), PAINT[r.nextInt(PAINT.length)], r.nextInt(140),
                    r.nextFloat() < 0.55f, 0.12f + r.nextFloat() * 0.18f));
        }

        for (int i = 0; i < 46; i++) {
            motes.add(new Mote(r.nextFloat(), 0.00008f + r.nextFloat() * 0.0001f, r.nextFloat(),
                    0.012f + r.nextFloat() * 0.018f, 0.01f + r.nextFloat() * 0.02f,
                    r.nextInt(3) == 0 ? 0xFFFFE6F6 : PAINT[r.nextInt(PAINT.length)]));
        }
    }

    /** One branch of the crack: x0, y0, x1, y1, distance at start, distance at end. */
    private static void growCrack(List<float[]> out, Random r, float x, float y, double angle, float dist, int depth) {
        int steps = depth == 0 ? 7 : 3;
        for (int k = 0; k < steps; k++) {
            float len = 0.045f + r.nextFloat() * 0.04f;
            angle += (r.nextFloat() - 0.5f) * 0.9f;
            float x1 = x + (float) Math.cos(angle) * len, y1 = y + (float) Math.sin(angle) * len;
            out.add(new float[]{x, y, x1, y1, dist, dist + len});
            if (depth == 0 && r.nextFloat() < 0.35f) {
                growCrack(out, r, x1, y1, angle + (r.nextBoolean() ? 0.8 : -0.8), dist + len, 1);
            }
            x = x1;
            y = y1;
            dist += len;
        }
    }

    /**
     * The thin pink crack spreading through the grey block from where the pick went in -
     * slowly at first, then faster, right up to the burst. The one thing on screen that
     * still has colour.
     */
    private void drawCrack(GuiGraphicsExtractor g, long u, long drain, float master) {
        long from = Math.round(drain * 0.72), to = Math.round(drain * 0.985);
        if (u < from) return;
        float p = easeIn(clamp01((u - from) / (float) Math.max(1L, to - from)));
        float scale = iceSize;
        Identifier aura = TextureBank.id(ThawArt.TEX_AURA);
        if (aura != null) {
            float s = iceSize * (0.15f + 0.9f * p);
            blit(g, aura, strikeX - s / 2f, strikeY - s / 2f, s, s, argb(master * (0.35f + 0.4f * p), DYE_PINK));
        }
        for (Segment s : crack) {
            float f = clamp01((p - s.at0()) / Math.max(1e-4f, s.at1() - s.at0()));
            if (f <= 0f) continue;
            float x0 = strikeX + s.x0() * scale, y0 = strikeY + s.y0() * scale;
            float x1 = strikeX + lerp(s.x0(), s.x1(), f) * scale, y1 = strikeY + lerp(s.y0(), s.y1(), f) * scale;
            stroke(g, x0, y0, x1, y1, 3.2f, argb(0.55f * master, DYE_PINK));
            stroke(g, x0, y0, x1, y1, 1.2f, argb(master, 0xFFE0F6));
        }
    }

    /** The paint burst: splats slapped across the screen, some running down in drips. */
    private void drawSplats(GuiGraphicsExtractor g, int sw, int sh, long t, long v, float master) {
        float fade = 1f - clamp01((v - tl.paced(3000)) / (float) tl.paced(1200));
        float a = master * fade * 0.92f;
        if (a <= 0.02f) return;
        Identifier drip = TextureBank.id(ThawArt.TEX_DRIP);
        Matrix3x2fStack pose = g.pose();
        for (Splat s : splats) {
            long vv = v - s.delay();
            if (vv < 0) continue;
            float size = sh * s.size() * easeOutBack(clamp01(vv / 170f));
            float x = s.fx() * sw, y = s.fy() * sh;
            if (s.drips() && drip != null) {
                float len = sh * s.dripLen() * easeOutCubic(clamp01((vv - 200) / (float) tl.paced(2600)));
                float w = sh * s.size() * 0.13f;
                if (len > 1f) {
                    float top = y + sh * s.size() * 0.12f;
                    g.fill(Math.round(x - w * 0.2f), Math.round(top), Math.round(x + w * 0.2f), Math.round(top + len),
                            argb(a, s.argb()));
                    blit(g, drip, x - w / 2f, top + len - w * 3f, w, w * 4f, argb(a, s.argb()));
                }
            }
            Identifier tex = TextureBank.id(ThawArt.splatName(s.variant()));
            if (tex == null) continue;
            pose.pushMatrix();
            pose.translate(x, y);
            pose.rotate(s.rot());
            blit(g, tex, -size / 2f, -size / 2f, size, size, argb(a, s.argb()));
            pose.popMatrix();
        }
    }

    /** Pink sparkles rising up the screen - snow falling the wrong way - for the rest of the run. */
    private void drawMotes(GuiGraphicsExtractor g, int sw, int sh, long v, float master) {
        Identifier tex = TextureBank.id(ButcherClient.TEX_SPARKLE);
        if (tex == null) return;
        float in = clamp01(v / 600f);
        for (Mote m : motes) {
            float size = sh * m.size();
            float span = sh + size * 2f;
            float travelled = (m.phase() * span + m.speed() * sh * v) % span;
            float y = sh + size - travelled;
            float x = m.fx() * sw + (float) Math.sin(v / 700.0 + m.phase() * 6.0) * m.sway() * sw;
            float tw = 0.6f + 0.4f * (float) Math.sin(v / 180.0 + m.phase() * 20.0);
            blit(g, tex, x - size / 2f, y - size / 2f, size, size, argb(master * in * tw, m.argb()));
        }
    }

    /**
     * The Dye's heartbeat, as a 0..1 thump the frame pulses with: two beats, each a lub and
     * a quieter dub, in the grey before the crack.
     */
    private float heartbeat(long t) {
        if (tl.finale() != Finale.SPECIAL || n == 0 || t < tl.finaleStart()) return 0f;
        long u = t - tl.finaleStart();
        float k = 0f;
        for (int b = 0; b < 2; b++) {
            long at = beatAt(b);
            if (u >= at) k += (float) Math.exp(-(u - at) / 90.0);
            if (u >= at + 210) k += 0.7f * (float) Math.exp(-(u - at - 210) / 90.0);
        }
        return Math.min(1f, k);
    }

    private long beatAt(int beat) {
        return Math.round(tl.specialDrain() * (beat == 0 ? 0.38 : 0.58));
    }

    /** Slowly turning rays of light behind a big reveal. */
    private void drawRays(GuiGraphicsExtractor g, int sw, int sh, long t, long u, int colour, float master) {
        Identifier ray = TextureBank.id(ThawArt.TEX_RAY);
        if (ray == null) return;
        float a = clamp01(u / 300f) * master;
        if (t >= tl.outroStart()) a *= 1f - clamp01((t - tl.outroStart()) / 400f);
        if (a <= 0.01f) return;
        float cx = sw / 2f, cy = sh * SHOW_Y;
        Matrix3x2fStack pose = g.pose();
        int rays = 12;
        float turn = u / 5200f * 6.283f;
        for (int i = 0; i < rays; i++) {
            float len = sh * (0.55f + 0.12f * (float) Math.sin(i * 2.3 + u / 600.0));
            float w = sh * (i % 2 == 0 ? 0.10f : 0.065f);
            pose.pushMatrix();
            pose.translate(cx, cy);
            pose.rotate(turn + i * 6.283f / rays);
            blit(g, ray, 0f, -w / 2f, len, w, argb(a * (i % 2 == 0 ? 0.55f : 0.35f), colour));
            pose.popMatrix();
        }
    }

    /** After the ending: the haul in its tray, and the one number the player cares about. */
    private void drawOutro(GuiGraphicsExtractor g, int sw, int sh, long t, float master) {
        if (t < tl.outroStart()) return;
        float a = ease(clamp01((t - tl.outroStart()) / 300f)) * master;
        if (a <= 0.02f) return;
        String line;
        int colour;
        if (haul.hasLegendary()) {
            line = streakBefore == 0 ? "Two in a row!"
                    : "First legendary in " + (streakBefore + 1) + " corpses";
            colour = haul.hasSpecial() ? Tier.SPECIAL.glow() : FLARE_GOLD;
        } else {
            int streak = streakBefore + 1;
            line = streak + (streak == 1 ? " corpse" : " corpses") + " since your last legendary";
            colour = 0x9FB3C8;
        }
        outlinedText(g, Component.literal(line), sw / 2f, sh * CAPTION_Y, 1.5f, argb(a, colour));
    }

    // ================================================================== vignette, shake

    private void drawVignette(GuiGraphicsExtractor g, int sw, int sh, long t, float master) {
        Identifier id = TextureBank.id(ButcherClient.TEX_VIGNETTE);
        if (id == null) return;
        float k = knob(ButcherClient.config == null ? 100 : ButcherClient.config.vignetteStrength);
        float close = 0.30f + 0.40f * (tl.landedBy(t) / (float) Math.max(1, n));
        float m = shakeMargin;
        blit(g, id, -m, -m, sw + 2 * m, sh + 2 * m, argb(close * k * master, 0x02060C));
    }

    /**
     * How far the whole frame is knocked this frame.
     *
     * Every strike knocks it a little harder than the last, and the endings knock it hard
     * or not at all: a detonation shakes the room, a crash just thuds. Nothing shakes
     * during the Dye's drain - the stillness is the point.
     */
    private float[] shake(long t, int sh) {
        float k = knob(ButcherClient.config == null ? 100 : ButcherClient.config.shakeStrength);
        float x = 0f, y = 0f;
        Strike s = lastLanded(t);
        if (s != null) {
            // On the physical clock: slow at the big endings' impact, still in the Dye's freeze.
            float dt = vt(t) - s.lands();
            float force = sh * 0.006f * (1f + 1.4f * s.index() / (float) Math.max(1, n - 1))
                    * (float) Math.exp(-dt / 110.0);
            x += force * (float) Math.sin(dt / 19.0);
            y += force * 0.7f * (float) Math.sin(dt / 13.0 + 1.1);
        }
        long big = switch (tl.finale()) {
            case EXPLODE -> tl.finaleStart();
            case LOCKET -> tl.finaleStart() + tl.paced(LOCKET_SNAP);
            case SPECIAL -> tl.finaleStart() + tl.specialDrain();
            default -> -1;
        };
        if (tl.finale() == Finale.LOCKET && n > 0 && t >= tl.finaleStart()) {
            // The block blowing apart, rolling slowly through the slow motion.
            float dt = vt(t) - tl.finaleStart();
            float force = sh * 0.018f * (float) Math.exp(-dt / 260.0);
            x += force * (float) Math.sin(dt / 23.0);
            y += force * 0.8f * (float) Math.sin(dt / 17.0 + 0.6);
        }
        if (big >= 0 && t >= big) {
            float dt = t - big;
            float force = sh * 0.026f * (float) Math.exp(-dt / 260.0);
            x += force * (float) Math.sin(dt / 23.0);
            y += force * 0.8f * (float) Math.sin(dt / 17.0 + 0.6);
        }
        return new float[]{x * k, y * k};
    }

    // ================================================================== leaving

    private void close() {
        com.butcher.render.GameplayView.release();
        if (!active) return;
        active = false;
        ButcherAudio.letRing();
        announce();
        // Drawn in the HUD, not opened (CutsceneHud): taking it off is all closing is.
        com.butcher.render.CutsceneHud.hide(this);
    }

    @Override
    public void onClose() {
        com.butcher.render.CutsceneHud.hide(this);
        com.butcher.render.GameplayView.release();
        // Escape, or something else taking the screen. The sound is orphaned if the run
        // did not finish, so it stops - but the haul is still announced: the loot message
        // was hidden for this cutscene, and skipping the cutscene must not lose it.
        boolean finished = System.currentTimeMillis() - openedAt >= tl.total();
        active = false;
        if (!finished) ButcherAudio.silence();
        announce();
        super.onClose();
    }

    /** Puts the haul into chat once, now that the cutscene has shown it. */
    private void announce() {
        if (announced) return;
        announced = true;
        // The window first: once it is claimed, the summary below is held along with the
        // rest of the chat and comes out when the window closes, instead of fading behind it.
        ButcherClient.onThawFinished(haul);
        ButcherClient.announceHaul(haul);
    }

    // ================================================================== helpers

    private int tierColour(Tier tier) {
        return tier == Tier.COMMON ? 0xFF9FB3C8 : tier.glow();
    }

    private void fillFull(GuiGraphicsExtractor g, int sw, int sh, int argb) {
        int m = Math.round(shakeMargin);
        g.fill(-m, -m, sw + m, sh + m, argb);
    }

    private static void blit(GuiGraphicsExtractor g, Identifier tex, float x, float y, float w, float h, int argb) {
        int iw = Math.max(1, Math.round(w)), ih = Math.max(1, Math.round(h));
        g.blit(RenderPipelines.GUI_TEXTURED, tex, Math.round(x), Math.round(y), 0, 0, iw, ih, iw, ih, argb);
    }

    private static void outline(GuiGraphicsExtractor g, float x, float y, float w, float h, int argb) {
        int x0 = Math.round(x), y0 = Math.round(y), x1 = Math.round(x + w), y1 = Math.round(y + h);
        g.fill(x0, y0, x1, y0 + 1, argb);
        g.fill(x0, y1 - 1, x1, y1, argb);
        g.fill(x0, y0, x0 + 1, y1, argb);
        g.fill(x1 - 1, y0, x1, y1, argb);
    }

    /** Text centred on a point, scaled about that point so it stays centred. */
    private void scaledText(GuiGraphicsExtractor g, Component text, float cx, float cy, float scale, int argb) {
        if ((argb >>> 24) < 6 || scale <= 0.01f) return;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.scale(scale, scale);
        pose.translate(-cx, -cy);
        g.text(font, text, Math.round(cx - font.width(text) / 2f), Math.round(cy - 4f), argb, true);
        pose.popMatrix();
    }

    /**
     * Text with a solid black outline all the way round, for the drop names.
     *
     * The vanilla drop shadow sits one pixel down and right, which on pale ice is not
     * enough to separate a dark-purple name from the frost behind it. This stamps the text
     * in black at all eight neighbouring offsets first - in scaled units, so the outline
     * thickens with the text rather than turning into a hairline at large sizes - and then
     * draws the colour on top with no shadow at all.
     */
    private void outlinedText(GuiGraphicsExtractor g, Component text, float cx, float cy, float scale, int argb) {
        int alpha = argb >>> 24;
        if (alpha < 6 || scale <= 0.01f) return;
        int ink = alpha << 24;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.scale(scale, scale);
        pose.translate(-cx, -cy);
        int x = Math.round(cx - font.width(text) / 2f), y = Math.round(cy - 4f);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) g.text(font, text, x + dx, y + dy, ink, false);
            }
        }
        g.text(font, text, x, y, argb, false);
        pose.popMatrix();
    }

    /**
     * The first {@code shown} letters of {@code full}, placed where they sit in the whole
     * word - so the name types out left to right instead of re-centring on every letter.
     */
    private void outlinedText(GuiGraphicsExtractor g, String full, int shown, float cx, float cy, float scale, int argb) {
        int alpha = argb >>> 24;
        if (alpha < 6 || scale <= 0.01f || shown <= 0) return;
        Component part = Component.literal(full.substring(0, Math.min(shown, full.length())));
        int ink = alpha << 24;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.scale(scale, scale);
        pose.translate(-cx, -cy);
        int x = Math.round(cx - font.width(full) / 2f), y = Math.round(cy - 4f);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) g.text(font, part, x + dx, y + dy, ink, false);
            }
        }
        g.text(font, part, x, y, argb, false);
        pose.popMatrix();
    }

    /** A straight line of any angle and thickness: a unit fill, stretched and turned. */
    private static void stroke(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float width, int argb) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.1f) return;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x0, y0);
        pose.rotate((float) Math.atan2(dy, dx));
        pose.scale(len, width);
        pose.translate(0f, -0.5f);
        g.fill(0, 0, 1, 1, argb);
        pose.popMatrix();
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.round(255 * clamp01(alpha));
        return a << 24 | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        return Math.round(a + (b - a) * clamp01(t));
    }

    private static float knob(int percent) {
        return Math.max(0f, Math.min(2f, percent / 100f));
    }

    private static float bezier(float a, float b, float c, float t) {
        float u = 1f - t;
        return u * u * a + 2 * u * t * b + t * t * c;
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float ease(float t) { return t * t * (3 - 2 * t); }
    private static float easeIn(float t) { return t * t; }
    private static float easeOutCubic(float t) { float u = 1f - t; return 1f - u * u * u; }

    /** Overshoots and settles - the arrival every piece of this cutscene makes. */
    private static float easeOutBack(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float c1 = 2.2f, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
}
