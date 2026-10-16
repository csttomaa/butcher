package com.butcher.gui;

import com.butcher.ButcherClient;
import com.butcher.anim.SeraphTimeline;
import com.butcher.anim.SeraphTimeline.Drop;
import com.butcher.audio.ButcherAudio;
import com.butcher.audio.SeraphSound;
import com.butcher.render.ChopScene;
import com.butcher.render.HeadSkins;
import com.butcher.render.SeraphScene;
import com.butcher.render.MeshRenderer;
import com.butcher.render.DragonRig;
import com.butcher.render.PixelLayer;
import com.butcher.render.RasterImage;
import com.butcher.render.TextureBank;
import com.butcher.render.model.Mesh;
import com.butcher.render.model.ModelProjection;
import com.butcher.render.model.PlayerHeadMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/**
 * "The Seraph's Glyph" - the Voidgloom Seraph's animation (tier IV), in the HUD like the other
 * cutscenes. Thin, as HellionScreen is: {@link SeraphScene} draws every frame (the same code as the
 * offline preview); this supplies the GUI primitives, the clock, the sounds and the price.
 *
 * The sounds follow the clock and never the drop before {@link SeraphTimeline#REVEAL}.
 */
public class SeraphScreen extends Screen implements ChopScene.Canvas, Cutscene {

    private SeraphTimeline tl;
    private Drop drop;
    private final long seed;
    /** Opened on a kill with the drop not known yet: the drop line fills it in ({@link #resolve}). */
    private boolean pending;
    private long lastT = -1;
    private final boolean test;
    /** Short busts apply: every real run, and a test from the menu's "Try it" (not /butcher test). */
    private final boolean followsBusts = com.butcher.ButcherClient.tryingIt;
    /** Not final: the skip key moves it back, so the clock jumps to the showcase. */
    /** The scene's clock, at the player's speed for this animation (the menu's Speed). */
    private final com.butcher.anim.SceneClock clock = new com.butcher.anim.SceneClock(
            com.butcher.ButcherClient.config == null ? 100 : com.butcher.ButcherClient.config.seraphSpeed);
    private boolean active = true;

    private GuiGraphicsExtractor g;
    private final Matrix3x2f matrix = new Matrix3x2f();
    private final MeshRenderer meshes = new MeshRenderer();
    private static final Mesh HEAD = PlayerHeadMesh.head();
    private static final float HEAD_SPAN = PlayerHeadMesh.HEAD + 2 * PlayerHeadMesh.HAT_INFLATE;
    /** The particles, lasers, cracks' edges and the rest of the pixel effects, drawn as one image (PixelLayer). */
    private static final PixelLayer PIXELS = new PixelLayer("seraph_pixels");
    /** The Dragon's paint pool: on the ground, under everything else, so its own layer drawn first. */
    private static final PixelLayer GROUND = new PixelLayer("seraph_ground");
    /** The rasterised Ender Dragon, one image a frame. */
    private static final RasterImage DRAGON_IMAGE = new RasterImage("seraph_dragon");
    private boolean dragonTextures;

    // Each sound once, whatever frames fall.
    private java.util.List<SeraphSound.Cue> cues;
    private int cueAt;
    private boolean priced, landed;
    private int coinTicks;

    /** {@code drop} null: a real kill whose drop is still to come - it plays as a bust until resolved. */
    public SeraphScreen(Drop drop, boolean test) {
        super(Component.literal("Butcher"));
        this.pending = drop == null;
        this.drop = drop == null ? Drop.BUST : drop;
        this.test = test;
        this.seed = System.nanoTime() ^ clock.openedAt();
        this.tl = new SeraphTimeline(this.drop, seed);
        this.cues = SeraphSound.schedule(tl);
        requestPrice();
        if (!test && this.drop.win()) tier = com.butcher.detect.HellionDrops.freshTier();
        count();
        shortBust();
    }

    private void requestPrice() {
        if (drop.win() && drop.priceId != null && (ButcherClient.config == null || ButcherClient.config.livePrices)) {
            com.butcher.market.Bazaar.request();
            com.butcher.market.LivePrice.request(drop.priceId);
        }
    }

    /** The drop line's tier word, when a real line told this drop (null for tests and busts). */
    private String tier;

    /** "your 3rd", once counted; null for tests and busts. */
    private String nth;
    private boolean counted;

    /** Counted once the outcome is known - never for a test. */
    private void count() {
        if (counted || test || pending) return;
        counted = true;
        com.butcher.config.ButcherStats st = com.butcher.config.ButcherStats.get();
        st.onSource(SOURCE, drop.win() ? drop.title : null);
        if (drop.win()) nth = "your " + ChopScreen.ordinal(st.sourceDropCount(SOURCE, drop.title));
    }

    private static final String SOURCE = "seraph";

    public boolean isActive() { return active; }
    public boolean isTest() { return test; }
    public boolean pending() { return pending; }
    /** Whether this run is in the stats already - so a late bust report for its kill is not counted twice. */
    public boolean counted() { return counted; }

    /**
     * The kill's drop, now known: the timeline is rebuilt with the same seed. Nothing drawn or heard before
     * {@link SeraphTimeline#REVEAL} depends on the drop (SeraphTest), so this cannot show if it comes before it.
     */
    public boolean resolve(Drop d) {
        if (!pending) return d == null || d == drop;
        pending = false;
        long t = clock.t();
        if (t >= SeraphTimeline.REVEAL - 250 || d == null || d == drop) {
            // Too late to change what this one shows: it plays as the bust it is. A win that came now still needs
            // a screen of its own - the caller gives it one.
            count();
            shortBust();
            return d == null || d == drop;
        }
        drop = d;
        tl = new SeraphTimeline(drop, seed);
        cues = SeraphSound.schedule(tl);
        cueAt = 0;
        while (cueAt < cues.size() && cues.get(cueAt).at <= lastT) cueAt++;
        requestPrice();
        if (!test && this.drop.win()) tier = com.butcher.detect.HellionDrops.freshTier();
        count();
        shortBust();
        return true;
    }

    @Override protected void init() { }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) { }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        long t = clock.t();
        if (t >= tl.total()) {
            summary();
            close();
            return;
        }
        if (pending && t >= SeraphTimeline.REVEAL - 250) {
            pending = false;
            count();
            shortBust();
        }
        sounds(t);
        lastT = t;
        // The game's own textures - enderman, beacon, obsidian, glass, beams, End Crystal, End sky, End
        // stone - adopted every frame (free once there; /butcher reload forgets them). None is animated.
        for (int i = 0; i < SeraphScene.ADOPT.length; i++) {
            TextureBank.adopt(SeraphScene.ADOPT[i], Identifier.withDefaultNamespace(SeraphScene.ADOPT_PATH[i]),
                    SeraphScene.ADOPT_SIZE[i][0], SeraphScene.ADOPT_SIZE[i][1]);
        }
        if (tl.ending() == SeraphTimeline.Ending.DRAGON) {
            // The real Ender Dragon: the game's own model posed by its own code (DragonRig), rasterised on the
            // CPU with a depth buffer (ModelRaster), so its textures are read as pixels - through the resource
            // manager, so a resource pack's dragon is the one drawn. Once per cutscene.
            if (!dragonTextures) {
                SeraphScene.RASTER.texture(0, ButcherClient.readGameTexture("textures/entity/enderdragon/dragon.png"));
                SeraphScene.RASTER.texture(1, ButcherClient.readGameTexture("textures/entity/enderdragon/dragon_eyes.png"));
                dragonTextures = true;
            }
            if (SeraphScene.dragon == null) SeraphScene.dragon = new DragonRig();
            // As many image pixels a GUI pixel as the screen has, up to 2: the cost goes with the pixel count
            // (~2-4 ms at 2), and the dragon is about one texel a GUI pixel anyway.
            SeraphScene.rasterScale = Math.max(1, Math.min(2, Minecraft.getInstance().getWindow().getGuiScale()));
        }
        SeraphScene.bannerWord = tier;
        SeraphScene.nthNote = nth;
        ChopScene.calm = (ButcherClient.config != null && ButcherClient.config.calmEffects) || com.butcher.Safety.slow();
        SeraphScene.showBanner = ButcherClient.config == null || ButcherClient.config.seraphBanner;
        this.g = g;
        GROUND.begin(width, height, SeraphScene.unitFor(height));
        SeraphScene.under = GROUND;
        SeraphScene.flushUnder = () -> GROUND.end(g);
        PIXELS.begin(width, height, SeraphScene.unitFor(height));
        try {
            SeraphScene.draw(this, PIXELS, tl, t, width, height);
        } finally {
            SeraphScene.under = null;
            SeraphScene.flushUnder = null;
        }
        PIXELS.end(g);
        this.g = null;
        // The game itself, live, in a corner (SkyMaxx's gameplay view) - drawn last, over everything.
        com.butcher.render.GameplayView.draw(g, pipFade(), 0xFF9B4DCA);
    }

    /** The gameplay window's fade-in as the cutscene opens. */
    private float pipFade() {
        return Math.min(1f, (System.currentTimeMillis() - clock.openedAt()) / 400f);
    }


    // ================================================================== sound

    /** How late a sound cue may still play. */
    private static final long STALE_CUE_MS = 1_000;

    private void sounds(long t) {
        // The Seraph's own soundtrack, worked out once (SeraphSound) - play whatever has come due.
        while (cueAt < cues.size() && t >= cues.get(cueAt).at) {
            SeraphSound.Cue cue = cues.get(cueAt++);
            if (cue.cut) ButcherAudio.silence();
            // Frames stopped for a while (the window minimised, a long hitch): a cue that old would only land on top
            // of every other one due at once.
            if (t - cue.at > STALE_CUE_MS) continue;
            ButcherAudio.playSeraph(cue.voices);
        }
        if (drop.win()) {
            if (!priced && t >= tl.countStart()) {
                priced = true;
                fixPrice();
            }
            if (tl.price() > 0 && t >= tl.countStart() && t < tl.countStart() + SeraphTimeline.COUNT_MS) {
                int due = (int) ((t - tl.countStart()) / 70);
                while (coinTicks <= due) ButcherAudio.onThawCoin(coinTicks++);
            }
            if (!landed && tl.price() > 0 && t >= tl.countStart() + SeraphTimeline.COUNT_MS) {
                landed = true;
                ButcherAudio.onThawCoinLand();
            }
        }
    }

    /** The bazaar's instant sell, else the lowest BIN - fixed when the count starts. */
    private void fixPrice() {
        if (drop.priceId == null) return;
        if (ButcherClient.config != null && !ButcherClient.config.seraphPrice) return;
        if (ButcherClient.config != null && !ButcherClient.config.livePrices) return;
        Double sell = com.butcher.market.Bazaar.sell(drop.priceId);
        if (sell != null && sell > 0) {
            tl.price(Math.round(sell), "bazaar instant sell");
            return;
        }
        Long bin = com.butcher.market.LivePrice.cached(drop.priceId);
        if (bin != null && bin > 0) tl.price(bin, "lowest BIN on the auction house");
    }

    // ================================================================== the canvas

    @Override
    public void quad(String tex, float ox, float oy, float ux, float uy, float vx, float vy, int u0, int v0, int uw, int vh, int argb) {
        Identifier id = TextureBank.id(tex);
        if (id == null || uw <= 0 || vh <= 0) return;
        matrix.set(ux / uw, uy / uw, vx / vh, vy / vh, ox, oy);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.mul(matrix);
        g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, u0, v0, uw, vh, TextureBank.width(tex), TextureBank.height(tex), argb);
        pose.popMatrix();
    }

    @Override
    public void image(String key, int[] argb, int iw, int ih, float x, float y, float w, float h) {
        DRAGON_IMAGE.draw(g, argb, iw, ih, x, y, w, h);
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

    @Override public String creatureSkin(com.butcher.anim.ChopTimeline.Creature creature) { return null; }
    /** The dice are drawn as boxes with their own skin, so the scene needs the texture itself. */
    @Override public String dropSkin() { return drop.head ? HeadSkins.SLAYER.texture(drop.art) : null; }

    private static final float[][] INK = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-0.71f, -0.71f}, {0.71f, -0.71f}, {-0.71f, 0.71f}, {0.71f, 0.71f}};

    /** The drop: a real 3D head with its NEU skin, or the vanilla icon, outlined; the Paragon Shard a pixel shard. */
    @Override
    public void item(float cx, float cy, float size, float alpha, float yaw) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        if (a <= 3 || size < 2f) return;
        if (drop.head) {
            String skin = HeadSkins.SLAYER.texture(drop.art);
            if (skin == null) return;
            meshes.begin();
            meshes.camera(ModelProjection.Camera.of(yaw, -22f, size / (HEAD_SPAN * 1.4f)));
            meshes.add(HEAD, skin, 0f, 0f, 0f, 0f, (a << 24) | 0xFFFFFF, true, false, true);
            meshes.flush(g, cx, cy, a << 24, Math.max(1.5f, size / 16f));
            return;
        }
        if (drop.art == null) {
            shard(cx, cy, size, a);
            return;
        }
        Identifier tex = Identifier.withDefaultNamespace("textures/item/" + drop.art + ".png");
        float s = size / 16f, ink = Math.max(1.5f, size / 16f);
        int x = Math.round(cx - size / 2f), y = Math.round(cy - size / 2f);
        Matrix3x2fStack pose = g.pose();
        for (float[] o : INK) {
            pose.pushMatrix();
            pose.translate(x + o[0] * ink, y + o[1] * ink);
            pose.scale(s, s);
            g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, 16, 16, 16, 16, a << 24);
            pose.popMatrix();
        }
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(s, s);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, 16, 16, 16, 16, (a << 24) | 0xFFFFFF);
        pose.popMatrix();
    }

    /** A stand-in for the Paragon Shard until its real look is known: a pink pixel crystal. */
    private void shard(float cx, float cy, float size, int a) {
        String[] rows = {"...#...", "..###..", ".##+##.", ".#+++#.", ".##+##.", "..###..", "...#..."};
        float u = Math.max(1f, Math.round(size / 8f));
        float left = cx - 3.5f * u, top = cy - 3.5f * u;
        for (int pass = 0; pass < 2; pass++) {
            for (int j = 0; j < rows.length; j++) {
                for (int i = 0; i < rows[j].length(); i++) {
                    char ch = rows[j].charAt(i);
                    if (ch == '.') continue;
                    if (pass == 0) fill(left + (i - 1) * u, top + (j - 1) * u, 3 * u, 3 * u, (Math.round(a * 0.85f) << 24) | 0x0A0606);
                    else fill(left + i * u, top + j * u, u, u, (a << 24) | (ch == '+' ? 0xFFC8F4 : 0xE04ACB));
                }
            }
        }
    }

    @Override
    public void name(float cx, float cy, float scale, int argb, int letters) {
        String full = drop.title;
        int n = Math.min(letters, full.length());
        if (n <= 0) return;
        plate(cx, cy, scale, font.width(full), argb);
        if (n >= full.length()) {
            outlined(Component.literal(full), cx, cy, scale, argb);
            return;
        }
        // The jackpots stamp it letter by letter: the first n, standing where they will in the whole name.
        Component part = Component.literal(full.substring(0, n));
        float shift = (font.width(part) - font.width(full)) / 2f * scale;
        outlined(part, cx + shift, cy, scale, argb);
    }

    @Override
    public void text(String text, float cx, float cy, float scale, int argb) {
        plate(cx, cy, scale, font.width(text), argb);
        outlined(Component.literal(text), cx, cy, scale, argb);
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

    private void outlined(Component text, float cx, float cy, float scale, int argb) {
        if ((argb >>> 24) < 6) return;
        int alpha = argb >>> 24;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.scale(scale, scale);
        int x = Math.round(-font.width(text) / 2f), y = -4;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                if (ox != 0 || oy != 0) g.text(font, text, x + ox, y + oy, alpha << 24, false);
            }
        }
        g.text(font, text, x, y, argb, false);
        pose.popMatrix();
    }

    // ================================================================== busts, short; the summary

    /** "Busts: short" (the menu): a bust known in time jumps to its own ending, silently. Wins are never touched. */
    private boolean shortened;

    private void shortBust() {
        if (shortened || (test && !followsBusts) || pending || drop.win() || ButcherClient.bustStyle("seraph") != 1) return;
        shortened = true;
        long t = clock.t(), target = SeraphTimeline.REVEAL - 450;
        if (t >= target) return;
        clock.jump(target);
        ButcherAudio.silence();
        ButcherAudio.muted(() -> sounds(target));
    }

    /** "Chat summary" (the menu): one line when it ends - what dropped, its price, which one of yours. */
    private boolean summarised;

    private void summary() {
        if (summarised || pending) return;
        summarised = true;
        // The Ledger's Tycoon: what this drop was worth, once.
        if (!test && drop.win() && tl.price() > 0) com.butcher.config.ButcherStats.get().onCoins(tl.price());
        String what = drop.win() ? drop.title : "no rare drop";
        String price = drop.win() && tl.price() > 0 ? " - " + String.format("%,d", tl.price()) + " coins" : "";
        String extra = test ? " (test)" : nth != null ? " (" + nth + ")" : "";
        ButcherClient.summary("Voidgloom Seraph" + ": " + what + price + extra);
    }

    // ================================================================== skipping

    @Override
    public boolean showsWin() {
        return active && !pending && drop.win();
    }

    @Override
    public void skip() {
        if (!active) return;
        long t = clock.t();
        long target = Math.max(tl.arrive(), tl.countStart() + SeraphTimeline.COUNT_MS);
        if (pending || !drop.win() || t >= target || target >= tl.total()) {
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
        com.butcher.render.CutsceneHud.hide(this);
    }

    @Override
    public void onClose() {
        close();
    }
}
