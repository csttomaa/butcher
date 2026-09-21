package com.scathabutcher.anim;

import com.scathabutcher.ScathaButcherClient;
import com.scathabutcher.detect.DropDetector.DropResult;
import com.scathabutcher.detect.DropDetector.Outcome;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Draws the butcher cutscene as a full-screen HUD overlay, on top of
 * everything else (including chat) - which is what makes the chat
 * unreadable while this is playing.
 *
 * The backdrop is a dim, torch-lit cave (flat gradient-shaded silhouette
 * art: layered rock-wall bands, hanging stalactites, a couple of flickering
 * torches with a soft glow, small silhouette bats drifting near the
 * ceiling, and embers rising off the torches) instead of a flat dark
 * overlay. It fades in/out using the same trick as before: the cave art is
 * always drawn opaque, then washed with a near-black tint whose alpha runs
 * inverse to the phase fade, so the scene reads as fading in and out even
 * though the art itself is drawn at fixed opacity.
 *
 * Sequence:
 *  RAISE  - the guillotine frame bounces up into place from below screen
 *           (jelly/spring overshoot), while the whole Scatha (the real
 *           crawl/idle animation, ported frame-by-frame from the reference
 *           GIF) slides in from off-screen left, fast at first and easing
 *           to a stop with its head in the neck hole.
 *  SWING  - the blade falls from its rest position. It's drawn BEHIND the
 *           Scatha, so it disappears behind the body/head as it descends
 *           rather than covering it.
 *  IMPACT - blade reaches the hole. PET: the cut happens. BLOCKED: the
 *           blade is stopped short by the gemstone/Dwarven O's, with a
 *           spark and a little bounce.
 *  RESULT - PET: the severed head drifts from the hole to screen-center,
 *           growing larger (closer to camera), spinning the whole time -
 *           this is Scatha-Pro's own "scatha_spin" reveal animation (the
 *           same 20-frame texture, reused as-is), not a hand-rolled effect.
 *           The now-headless body is simply not drawn any more.
 *           BLOCKED: the whole Scatha just sits there in the hole,
 *           unharmed, still idling.
 *  FADE   - everything holds while the dim backdrop fades back out.
 */
public final class ButcherHudRenderer {
    private ButcherHudRenderer() {}

    private static final String NS = ScathaButcherClient.MOD_ID;
    private static final Identifier FRAME = Identifier.fromNamespaceAndPath(NS, "textures/gui/guillotine_frame.png");
    private static final Identifier BLADE = Identifier.fromNamespaceAndPath(NS, "textures/gui/blade.png");
    // Real item art rather than the hand-drawn placeholder these used to use -
    // shared with the cleaver cutscene so both modes name the drop the same way.
    private static final Identifier GEM = Identifier.fromNamespaceAndPath(NS, "textures/gui/butcher/gemstones.png");
    private static final Identifier BLOCK_BRAN = Identifier.fromNamespaceAndPath(NS, "textures/gui/butcher/block_bran.png");
    private static final Identifier SPARK = Identifier.fromNamespaceAndPath(NS, "textures/gui/spark.png");

    // ---- cave backdrop: a dim torch-lit cave replaces the old flat dim overlay ----
    private static final Identifier CAVE_BG = Identifier.fromNamespaceAndPath(NS, "textures/gui/cave/cave_bg.png");
    private static final Identifier TORCH_GLOW = Identifier.fromNamespaceAndPath(NS, "textures/gui/cave/torch_glow.png");
    private static final Identifier[] BAT_FRAMES = {
            Identifier.fromNamespaceAndPath(NS, "textures/gui/cave/bat_00.png"),
            Identifier.fromNamespaceAndPath(NS, "textures/gui/cave/bat_01.png"),
    };
    private static final Identifier EMBER = Identifier.fromNamespaceAndPath(NS, "textures/gui/cave/ember.png");
    private static final long BAT_FLAP_MS = 260;
    // torch positions as fractions of cave_bg.png's own 960x540 canvas, so they scale with it.
    // Y is the flame's center (base_y=452, flame top=fy-34, fy=base_y-70), not the base of the stick.
    private static final float[] TORCH_X_FRAC = {150f / 960f, 810f / 960f};
    private static final float TORCH_Y_FRAC = 368f / 540f;
    private static final int BAT_COUNT = 3;
    private static final int EMBER_COUNT = 7;

    // Scatha-Pro's own pet-drop reveal texture, reused wholesale: a 20-frame
    // spin of the head, rather than us hand-rolling a different effect.
    private static final int SPIN_FRAME_COUNT = 20;
    private static final long SPIN_MS_PER_FRAME = 70; // ~1.4s per full rotation
    private static final Identifier[] SPIN_FRAMES = new Identifier[SPIN_FRAME_COUNT];
    static {
        for (int i = 0; i < SPIN_FRAME_COUNT; i++) {
            SPIN_FRAMES[i] = Identifier.fromNamespaceAndPath(NS, String.format("textures/gui/spin/frame_%02d.png", i));
        }
    }

    // The whole-Scatha idle/crawl animation, ported frame-by-frame from the
    // reference GIF (42 frames, originally played at 30ms/frame).
    private static final int WALK_FRAME_COUNT = 42;
    private static final long WALK_MS_PER_FRAME = 30;
    private static final Identifier[] WALK_FRAMES = new Identifier[WALK_FRAME_COUNT];
    static {
        for (int i = 0; i < WALK_FRAME_COUNT; i++) {
            WALK_FRAMES[i] = Identifier.fromNamespaceAndPath(NS, String.format("textures/gui/walk/frame_%02d.png", i));
        }
    }
    // Native size of each walk frame, and the pixel position within that
    // frame that the head's "business end" (the mouth/eye front face) sits
    // at - measured directly off the source art. This is the point we align
    // with the guillotine's neck hole.
    private static final float WALK_FRAME_W = 573f;
    private static final float WALK_FRAME_H = 337f;
    // The walk frames are horizontally mirrored from the source GIF so the
    // Scatha leads with its head while sliding in from the left instead of
    // appearing to crawl in backwards - the anchor X is mirrored to match
    // (573 - 65).
    private static final float WALK_HEAD_ANCHOR_X = 508f;
    private static final float WALK_HEAD_ANCHOR_Y = 280f;
    private static final float WALK_HEAD_REF_SIZE = 140f; // ~ the head cube's on-frame pixel size, for scaling

    // Measured off guillotine_frame.png / blade.png at their native pixel size.
    private static final float IMG_FRAME_W = 380f;
    private static final float IMG_FRAME_H = 601f;
    private static final float IMG_HOLE_CX = 228.048f;
    private static final float IMG_HOLE_CY = 450.317f;
    private static final float IMG_HOLE_R = 33.714f;
    private static final float IMG_BLADE_OFF_X = 172.902f;
    private static final float IMG_BLADE_OFF_Y = 99.214f;
    private static final float IMG_BLADE_W = 130.038f;
    private static final float IMG_BLADE_H = 111.736f;

    public static void render(GuiGraphicsExtractor context, float partialTick) {
        AnimationManager anim = ScathaButcherClient.animationManager;
        if (!anim.isPlaying()) return;

        Minecraft client = Minecraft.getInstance();
        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();

        DropResult result = anim.getCurrent();
        boolean pet = result != null && result.outcome == Outcome.PET;
        AnimationManager.Phase phase = anim.getPhase();
        float pp = anim.phaseProgress();

        float fade = backdropAlpha(phase, pp);
        if (fade <= 0.02f) return;

        // ---- cave backdrop: drawn opaque, then washed with the same dark tint that used
        // to be the whole background - the wash's alpha runs inverse to `fade`, so at the
        // start of RAISE the cave is almost fully hidden behind near-black (matching the old
        // "instant dark overlay" look for a moment) and it's gradually revealed as the wash
        // fades away, then covered again the same way during FADE. This gets a real fade
        // in/out of the scene itself without needing a tinted/alpha blit. ----
        drawTex(context, CAVE_BG, 0, 0, sw, sh);

        float caveScale = sh / 540f;
        long caveT = anim.elapsedMs();
        for (float xf : TORCH_X_FRAC) {
            float tx = xf * sw;
            float ty = TORCH_Y_FRAC * sh;
            float flicker = 1f + (float) Math.sin(caveT / 90.0 + xf * 37.0) * 0.10f
                    + (float) Math.sin(caveT / 47.0 + xf * 91.0) * 0.05f;
            float glowSize = 150f * caveScale * flicker;
            drawTex(context, TORCH_GLOW, tx - glowSize / 2f, ty - glowSize / 2f, glowSize, glowSize);
        }

        for (int i = 0; i < BAT_COUNT; i++) {
            float seed = i * 71.3f;
            float baseX = (0.18f + 0.32f * i) * sw;
            float baseY = (0.19f + 0.06f * ((i * 2) % 3)) * sh;
            float driftX = (float) Math.sin(caveT / 900.0 + seed) * 30f * caveScale;
            float driftY = (float) Math.sin(caveT / 650.0 + seed * 1.7f) * 8f * caveScale;
            int batFrame = (int) ((caveT + (long) (seed * 100)) / BAT_FLAP_MS) % BAT_FRAMES.length;
            float batSize = 34f * caveScale;
            drawTex(context, BAT_FRAMES[batFrame], baseX + driftX - batSize / 2f, baseY + driftY - batSize / 2f, batSize, batSize);
        }

        for (int i = 0; i < EMBER_COUNT; i++) {
            float seed = i * 13.7f;
            float originXf = TORCH_X_FRAC[i % TORCH_X_FRAC.length] + ((i % 3) - 1) * 0.02f;
            float riseMs = 2200f + (i * 137) % 900;
            float cycle = ((caveT + (long) (seed * 500)) % (long) riseMs) / riseMs;
            float ex = (originXf * sw) + (float) Math.sin(cycle * Math.PI * 2 + seed) * 10f * caveScale;
            float ey = (TORCH_Y_FRAC * sh - 20f * caveScale) - cycle * 160f * caveScale;
            float emberAlpha = (float) Math.sin(cycle * Math.PI); // fades in, peaks mid-rise, fades out
            if (emberAlpha <= 0.03f) continue;
            float emberSize = (6f + 3f * emberAlpha) * caveScale;
            drawTex(context, EMBER, ex - emberSize / 2f, ey - emberSize / 2f, emberSize, emberSize);
        }

        int bgAlpha = (int) (235 * (1f - fade)) << 24;
        context.fill(0, 0, sw, sh, bgAlpha | 0x08060A);

        // ---- guillotine frame: bounces up from below during RAISE ----
        float displayScale = (0.88f * sh) / IMG_FRAME_H;
        float fw = IMG_FRAME_W * displayScale;
        float fh = IMG_FRAME_H * displayScale;
        float fx = sw * 0.5f - IMG_HOLE_CX * displayScale;
        float fyRest = sh * 0.03f;

        float raiseLinear = phase == AnimationManager.Phase.RAISE ? pp : 1f;
        float frameBounce = backOut(raiseLinear);
        float dropBelow = sh * 0.42f;
        float fy = fyRest + dropBelow * (1f - frameBounce);

        drawTex(context, FRAME, fx, fy, fw, fh);

        float holeCx = sw * 0.5f;
        float holeCy = fy + IMG_HOLE_CY * displayScale;
        float holeR = IMG_HOLE_R * displayScale;

        float bladeW = IMG_BLADE_W * displayScale;
        float bladeH = IMG_BLADE_H * displayScale;
        float bladeX = fx + IMG_BLADE_OFF_X * displayScale;
        float bladeRestY = fy + IMG_BLADE_OFF_Y * displayScale;
        float bladeCutY = holeCy - bladeH * 0.32f;
        float bladeBlockedY = holeCy - holeR * 1.55f;

        float headSize = holeR * 2f * 0.95f;

        // ---- blade (drawn BEHIND the creature/head - see class doc) ----
        float bladeY;
        boolean showGem = false;
        boolean showSpark = false;
        switch (phase) {
            case RAISE -> bladeY = bladeRestY;
            case SWING -> {
                float t = easeIn(pp);
                bladeY = lerp(bladeRestY, pet ? bladeCutY : bladeBlockedY, t);
            }
            case IMPACT -> {
                if (pet) {
                    bladeY = lerp(holeCy - bladeH * 0.55f, bladeCutY, pp);
                } else {
                    bladeY = bladeBlockedY - (float) Math.sin(pp * Math.PI) * 4f * displayScale;
                    showGem = true;
                    showSpark = true;
                }
            }
            case RESULT, FADE -> {
                if (pet) {
                    bladeY = bladeCutY;
                } else {
                    bladeY = bladeBlockedY;
                    showGem = true;
                }
            }
            default -> bladeY = bladeRestY;
        }
        drawTex(context, BLADE, bladeX, bladeY, bladeW, bladeH);

        // ---- creature / severed head (drawn ON TOP of the blade) ----
        boolean headDetached = pet && (phase == AnimationManager.Phase.RESULT || phase == AnimationManager.Phase.FADE);
        if (headDetached) {
            drawFloatingHead(context, anim, holeCx, holeCy, headSize, sw, sh, phase, pp);
        } else {
            drawWalkingCreature(context, anim, holeCx, holeCy, headSize, sw, raiseLinear, phase, pp);
        }

        if (showGem) {
            float gemSize = holeR * 1.1f;
            Identifier dropped = result.outcome == Outcome.BLOCKED ? BLOCK_BRAN : GEM;
            drawTex(context, dropped, holeCx - gemSize / 2f, bladeBlockedY + bladeH - gemSize * 0.4f, gemSize, gemSize);
        }
        if (showSpark) {
            float sparkSize = holeR * 1.6f;
            drawTex(context, SPARK, holeCx - sparkSize / 2f, holeCy - sparkSize / 2f, sparkSize, sparkSize);
        }
    }

    /** The whole Scatha, idling/crawling in place, sliding in from off-screen left while raiseLinear < 1. */
    private static void drawWalkingCreature(GuiGraphicsExtractor context, AnimationManager anim, float holeCx, float holeCy,
                                             float headSize, int sw, float raiseLinear, AnimationManager.Phase phase, float pp) {
        float creatureEase = easeOutCubic(raiseLinear);
        float startOffset = -(sw * 0.75f + headSize * 2f);
        float slideOffsetX = startOffset * (1f - creatureEase);

        // a little "thump" pop when the blade lands on it (both outcomes)
        float pop = 1f;
        if (phase == AnimationManager.Phase.IMPACT) {
            pop = 1f + (float) Math.sin(pp * Math.PI) * 0.08f;
        }

        float scale = (headSize / WALK_HEAD_REF_SIZE) * pop;
        float anchorX = holeCx + slideOffsetX;
        float anchorY = holeCy;

        int frame = (int) ((anim.elapsedMs() / WALK_MS_PER_FRAME) % WALK_FRAME_COUNT);
        float drawW = WALK_FRAME_W * scale;
        float drawH = WALK_FRAME_H * scale;
        float drawX = anchorX - WALK_HEAD_ANCHOR_X * scale;
        float drawY = anchorY - WALK_HEAD_ANCHOR_Y * scale;
        drawTex(context, WALK_FRAMES[frame], drawX, drawY, drawW, drawH);
    }

    /** The severed head: drifts from the hole to screen-center, grows a bit, and spins in place
     *  using Scatha-Pro's own 20-frame "scatha_spin" reveal animation, reused wholesale. */
    private static void drawFloatingHead(GuiGraphicsExtractor context, AnimationManager anim, float holeCx, float holeCy,
                                          float headSize, int sw, int sh, AnimationManager.Phase phase, float pp) {
        float flyT;
        if (phase == AnimationManager.Phase.RESULT) {
            flyT = easeOutCubic(Math.min(1f, pp / 0.4f));
        } else {
            flyT = 1f;
        }

        float endX = sw * 0.5f;
        float endY = sh * 0.52f;
        float cx = lerp(holeCx, endX, flyT);
        float cy = lerp(holeCy, endY, flyT);

        int frame = (int) ((anim.elapsedMs() / SPIN_MS_PER_FRAME) % SPIN_FRAME_COUNT);
        // Ends at 40% of screen height, matching Scatha-Pro's own pet popup - the 64x64
        // sprite blown right up, pixels left chunky rather than smoothed.
        float size = lerp(headSize, sh * 0.40f, flyT);
        drawTex(context, SPIN_FRAMES[frame], cx - size / 2f, cy - size / 2f, size, size);
    }


    /** Smooth fade across the *entire* RAISE / FADE phase, not just a sliver of it. */
    private static float backdropAlpha(AnimationManager.Phase phase, float pp) {
        return switch (phase) {
            case NONE -> 0f;
            case RAISE -> ease(pp);
            case FADE -> 1f - ease(pp);
            default -> 1f;
        };
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float ease(float t) { return t * t * (3 - 2 * t); }
    private static float easeIn(float t) { return t * t; }
    private static float easeOutCubic(float t) { float u = 1f - t; return 1f - u * u * u; }

    /** Classic "back out" overshoot easing - lands past 1.0 briefly before settling, for a jelly/spring bounce. */
    private static float backOut(float t) {
        float s = 1.70158f;
        float u = t - 1f;
        return u * u * ((s + 1f) * u + s) + 1f;
    }

    // ---- rendering primitives ----

    private static void drawTex(GuiGraphicsExtractor context, Identifier tex, float x, float y, float w, float h) {
        context.blit(RenderPipelines.GUI_TEXTURED, tex, Math.round(x), Math.round(y), 0, 0,
                Math.round(w), Math.round(h), Math.round(w), Math.round(h));
    }
}
