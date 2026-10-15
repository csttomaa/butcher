package com.butcher.gui;

import com.butcher.ButcherClient;
import com.butcher.anim.EruptionTimeline;
import com.butcher.anim.EruptionTimeline.Creature;
import com.butcher.anim.EruptionTimeline.Drop;
import com.butcher.audio.ButcherAudio;
import com.butcher.audio.EruptionSound;
import com.butcher.render.ChopScene;
import com.butcher.render.EruptionScene;
import com.butcher.render.HeadSkins;
import com.butcher.render.LavaRigs;
import com.butcher.render.PixelLayer;
import com.butcher.render.RasterImage;
import com.butcher.render.TextureBank;
import com.butcher.render.art.LavaArt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

import java.awt.image.BufferedImage;

/**
 * "Eruption" - lava fishing's animation, in the HUD like the other cutscenes. Thin, as HellionScreen is:
 * {@link EruptionScene} draws every frame (the same code as the offline preview); this supplies the GUI
 * primitives, the game's textures, the clock, the sounds and the price.
 *
 * Every model is rasterised on the CPU ({@link EruptionScene#RASTER}), so its textures are pixels, read through
 * the resource manager (a resource pack's are used) or, for the SkyBlock heads, fetched from their skins
 * ({@link HeadSkins#pixels}). The sounds follow the clock and never the drop before {@link EruptionTimeline#REVEAL}.
 */
public class EruptionScreen extends Screen implements ChopScene.Canvas, Cutscene {

    private EruptionTimeline tl;
    private Drop drop;
    private final Creature creature;
    private final long seed;
    /** Opened on a kill with the drop not known yet: the drop line fills it in ({@link #resolve}). */
    private boolean pending;
    private final boolean test;
    /** Not final: the skip key moves it back, so the clock jumps to the showcase. */
    private long openedAt = System.currentTimeMillis();
    private boolean active = true;

    private GuiGraphicsExtractor g;
    private final Matrix3x2f matrix = new Matrix3x2f();
    private static final PixelLayer BACK = new PixelLayer("eruption_back");
    private static final PixelLayer FRONT = new PixelLayer("eruption_front");
    private static final RasterImage IMAGE = new RasterImage("eruption_models");
    private static final RasterImage FLOOR = new RasterImage("eruption_floor");

    private boolean texturesSet, creatureSet;
    /** Whether Hypixel's pack has the bust's guaranteed drop (asked once). */
    private Boolean sureIcon;
    private String dropPixelsFor;

    private java.util.List<EruptionSound.Cue> cues;
    private int cueAt;
    private long lastT = -1;
    private boolean priced, landed;
    private int coinTicks;

    /** {@code drop} null: a real kill whose drop is still to come - it plays as a bust until resolved. */
    public EruptionScreen(Creature creature, Drop drop, boolean test) {
        super(Component.literal("Butcher"));
        this.creature = creature;
        this.pending = drop == null;
        this.drop = drop == null ? Drop.BUST : drop;
        this.test = test;
        this.seed = System.nanoTime() ^ openedAt;
        this.tl = new EruptionTimeline(creature, this.drop, seed);
        this.cues = EruptionSound.schedule(tl);
        requestPrice();
        // Start the skins coming now: the creature's for the sinking, every head drop's for the reveal.
        skinName(creature);
        for (Drop d : Drop.values()) if (d.head && d.from(creature)) HeadSkins.LAVA.pixels(d.art);
        count();
        shortBust();
    }

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

    private static final String SOURCE = "lava";

    public boolean isActive() { return active; }
    public boolean isTest() { return test; }
    public boolean pending() { return pending; }
    /** Whether this run is in the stats already - so a late bust report for its kill is not counted twice. */
    public boolean counted() { return counted; }
    public Creature creature() { return creature; }

    /**
     * The kill's drop, now known: the timeline is rebuilt with the same seed. Nothing drawn or heard before
     * {@link EruptionTimeline#REVEAL} depends on the drop (tools/EruptionTest hashes every frame and sample),
     * so this cannot show - as long as it comes before the reveal.
     */
    public boolean resolve(Drop d) {
        if (!pending) return d == null || d == drop;
        pending = false;
        long t = System.currentTimeMillis() - openedAt;
        if (t >= EruptionTimeline.REVEAL - 250 || d == null || d == drop) {
            // Too late to change what this one shows: it plays as the bust it is. A win that came now still needs
            // a screen of its own - the caller gives it one.
            count();
            shortBust();
            return d == null || d == drop;
        }
        drop = d;
        tl = new EruptionTimeline(creature, drop, seed);
        cues = EruptionSound.schedule(tl);
        cueAt = 0;
        while (cueAt < cues.size() && cues.get(cueAt).at <= lastT) cueAt++;
        requestPrice();
        count();
        shortBust();
        return true;
    }

    private void requestPrice() {
        if (ButcherClient.config != null && !ButcherClient.config.livePrices) return;
        String id = drop.win() ? drop.id : EruptionTimeline.guaranteedId(creature);
        if (id == null) return;
        com.butcher.market.Bazaar.request();
        com.butcher.market.LivePrice.request(id);
    }

    @Override protected void init() { }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) { }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        long t = System.currentTimeMillis() - openedAt;
        if (t >= tl.total()) {
            summary();
            close();
            return;
        }
        if (pending && t >= EruptionTimeline.REVEAL - 250) {
            pending = false;
            count();
            shortBust();
        }
        sounds(t);
        textures();
        EruptionScene.nthNote = nth;
        ChopScene.calm = ButcherClient.config != null && ButcherClient.config.calmEffects;
        EruptionScene.showBanner = ButcherClient.config == null || ButcherClient.config.lavaBanner;
        EruptionScene.rasterScale = Math.max(1, Math.min(2, Minecraft.getInstance().getWindow().getGuiScale()));
        this.g = g;
        int unit = EruptionScene.unitFor(height);
        BACK.begin(width, height, unit);
        FRONT.begin(width, height, unit);
        EruptionScene.flushBack = () -> BACK.end(g);
        EruptionScene.flushFront = () -> FRONT.end(g);
        try {
            EruptionScene.draw(this, BACK, FRONT, tl, t, width, height);
        } finally {
            EruptionScene.flushBack = null;
            EruptionScene.flushFront = null;
        }
        this.g = null;
        // The game itself, live, in a corner (SkyMaxx's gameplay view) - drawn last, over everything.
        com.butcher.render.GameplayView.draw(g, pipFade(), 0xFFE2620F);
    }

    /** The gameplay window's fade-in as the cutscene opens. */
    private float pipFade() {
        return Math.min(1f, (System.currentTimeMillis() - openedAt) / 400f);
    }


    // ================================================================== textures

    /** The game's own, read once per cutscene; the heads as their skins arrive. */
    private void textures() {
        // Static block textures adopted by name (free once there; /butcher reload forgets them).
        TextureBank.adopt(EruptionScene.TEX_BLACKSTONE, Identifier.withDefaultNamespace("textures/block/blackstone.png"), 16, 16);
        TextureBank.adopt(EruptionScene.TEX_BASALT_SIDE, Identifier.withDefaultNamespace("textures/block/basalt_side.png"), 16, 16);
        TextureBank.adopt(EruptionScene.TEX_BASALT_TOP, Identifier.withDefaultNamespace("textures/block/basalt_top.png"), 16, 16);
        TextureBank.adopt(EruptionScene.TEX_CUBE, Identifier.withDefaultNamespace("textures/entity/slime/magmacube.png"), 64, 64);
        String sure = EruptionTimeline.guaranteedIcon(creature);
        if (sure != null && !drop.win()) {
            Identifier icon = Identifier.fromNamespaceAndPath("hypixel_skyblock", sure);
            // Only from Hypixel's pack when it is loaded: adopting a missing texture would draw the purple-black square.
            if (sureIcon == null) sureIcon = Minecraft.getInstance().getResourceManager().getResource(icon).isPresent();
            if (sureIcon) TextureBank.adopt(EruptionScene.TEX_SURE + creature.name().toLowerCase(java.util.Locale.ROOT), icon, 16, 16);
        }
        if (!texturesSet) {
            texturesSet = true;
            if (EruptionScene.rigs == null) EruptionScene.rigs = new LavaRigs();
            BufferedImage magma = LavaArt.strip(ButcherClient.readGameTexture("textures/block/magma.png"), 3);
            BufferedImage lava = LavaArt.strip(ButcherClient.readGameTexture("textures/block/lava_still.png"), 20);
            EruptionScene.RASTER.texture(EruptionScene.SLOT_MAGMA, magma);
            EruptionScene.RASTER.texture(EruptionScene.SLOT_CRACKS, LavaArt.crackMask(magma));
            EruptionScene.RASTER.texture(EruptionScene.SLOT_LAVA_GREY, LavaArt.greyLava(lava));
            EruptionScene.lavaPixels = LavaArt.pixels(lava);
            set(EruptionScene.SLOT_CUBE, "textures/entity/slime/magmacube.png");
            set(EruptionScene.SLOT_HORSE, "textures/entity/horse/horse_skeleton.png");
            set(EruptionScene.SLOT_SADDLE, "textures/entity/equipment/skeleton_horse_saddle/saddle.png");
            switch (creature) {
                case JAWBUS -> set(EruptionScene.SLOT_CREATURE, "textures/entity/iron_golem/iron_golem.png");
                case THUNDER -> set(EruptionScene.SLOT_CREATURE, "textures/entity/guardian/guardian_elder.png");
                case PLHLEGBLAST -> set(EruptionScene.SLOT_CREATURE, "textures/entity/squid/squid.png");
                default -> { }
            }
        }
        if (!creatureSet) {
            String skin = skinName(creature);
            if (skin == null) {
                creatureSet = true;
            } else {
                BufferedImage img = HeadSkins.LAVA.pixels(skin);
                if (img != null) {
                    EruptionScene.RASTER.texture(EruptionScene.SLOT_CREATURE, img);
                    creatureSet = true;
                }
            }
        }
        // The drop's pixels: its head skin when it is here, else its vanilla icon.
        if (!drop.win()) {
            EruptionScene.dropPixels = null;
        } else if (!drop.art.equals(dropPixelsFor)) {
            BufferedImage img = drop.head ? HeadSkins.LAVA.pixels(drop.art) : ButcherClient.readGameTexture("textures/item/" + drop.art + ".png");
            EruptionScene.dropPixels = null;
            if (img != null) {
                EruptionScene.dropPixels = LavaArt.pixels(img);
                EruptionScene.dropW = img.getWidth();
                EruptionScene.dropH = img.getHeight();
                dropPixelsFor = drop.art;
            }
        }
    }

    private void set(int slot, String path) {
        BufferedImage img = ButcherClient.readGameTexture(path);
        if (img != null) EruptionScene.RASTER.texture(slot, img);
    }

    /** The NEU bestiary head a creature is drawn with, or null for the vanilla models. */
    private static String skinName(Creature c) {
        String name = switch (c) {
            case RAGNAROK -> "Ragnarok";
            case SCUTTLER -> "Fiery Scuttler";
            default -> null;
        };
        if (name != null) HeadSkins.LAVA.pixels(name);
        return name;
    }

    // ================================================================== sound

    /** How late a sound cue may still play. */
    private static final long STALE_CUE_MS = 1_000;

    private void sounds(long t) {
        while (cueAt < cues.size() && t >= cues.get(cueAt).at) {
            EruptionSound.Cue cue = cues.get(cueAt++);
            if (cue.cut) ButcherAudio.silence();
            // Frames stopped for a while (the window minimised, a long hitch): a cue that old would only land on top
            // of every other one due at once.
            if (t - cue.at > STALE_CUE_MS) continue;
            ButcherAudio.playEruption(cue.voices);
        }
        lastT = t;
        if (!priced && t >= tl.countStart()) {
            priced = true;
            fixPrice();
        }
        // A bust's price is only shown: the coins and their landing are a win's.
        if (!drop.win()) return;
        if (tl.price() > 0 && t >= tl.countStart() && t < tl.countStart() + EruptionTimeline.COUNT_MS) {
            int due = (int) ((t - tl.countStart()) / 70);
            while (coinTicks <= due) ButcherAudio.onThawCoin(coinTicks++);
        }
        if (!landed && tl.price() > 0 && t >= tl.countStart() + EruptionTimeline.COUNT_MS) {
            landed = true;
            ButcherAudio.onThawCoinLand();
        }
    }

    /** The bazaar's instant sell, else the lowest BIN - fixed when the count starts. A bust prices what it always drops. */
    private void fixPrice() {
        String id = drop.win() ? drop.id : EruptionTimeline.guaranteedId(creature);
        if (id == null) return;
        if (ButcherClient.config != null && (!ButcherClient.config.lavaPrice || !ButcherClient.config.livePrices)) return;
        Double sell = com.butcher.market.Bazaar.sell(id);
        if (sell != null && sell > 0) {
            tl.price(Math.round(sell), "bazaar instant sell");
            return;
        }
        Long bin = com.butcher.market.LivePrice.cached(id);
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
        IMAGE.draw(g, argb, iw, ih, x, y, w, h);
    }

    @Override
    public void imageAffine(String key, int[] argb, int iw, int ih, float ox, float oy, float ux, float uy, float vx, float vy) {
        FLOOR.drawAffine(g, argb, iw, ih, ox, oy, ux, uy, vx, vy);
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
    @Override public String dropSkin() { return null; }
    /** Unused: the drop is a model in the 3D layer. */
    @Override public void item(float cx, float cy, float size, float alpha, float yaw) { }

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
        if (shortened || test || pending || drop.win() || ButcherClient.bustStyle("lava") != 1) return;
        shortened = true;
        long t = System.currentTimeMillis() - openedAt, target = EruptionTimeline.REVEAL - 450;
        if (t >= target) return;
        openedAt -= target - t;
        ButcherAudio.silence();
        ButcherAudio.muted(() -> sounds(target));
    }

    /** "Chat summary" (the menu): one line when it ends - what dropped, its price, which one of yours. */
    private boolean summarised;

    private void summary() {
        if (summarised || pending) return;
        summarised = true;
        String what = drop.win() ? drop.title : "no rare drop";
        String price = drop.win() && tl.price() > 0 ? " - " + String.format("%,d", tl.price()) + " coins" : "";
        String extra = test ? " (test)" : nth != null ? " (" + nth + ")" : "";
        ButcherClient.summary(creature.title + ": " + what + price + extra);
    }

    // ================================================================== skipping

    @Override
    public boolean showsWin() {
        return active && !pending && drop.win();
    }

    @Override
    public void skip() {
        if (!active) return;
        long t = System.currentTimeMillis() - openedAt;
        long target = Math.max(tl.arrive(), tl.countStart() + EruptionTimeline.COUNT_MS);
        if (pending || !drop.win() || t >= target || target >= tl.total()) {
            summary();
            interrupt();
            return;
        }
        // The clock jumps; every cue on the way is run silently, so flags, counters and the price are where the
        // showcase expects them and nothing skipped fires at once.
        openedAt -= target - t;
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
