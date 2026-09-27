package com.butcher;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.butcher.anim.AnimationManager;
import com.butcher.anim.ButcherHudRenderer;
import com.butcher.anim.PreKillHider;
import com.butcher.config.PatternConfig;
import com.butcher.config.PatternConfig.AnimationMode;
import com.butcher.detect.DecisionLog;
import com.butcher.detect.DropDetector.DropResult;
import com.butcher.detect.HotbarDropWatcher;
import com.butcher.detect.ScathaWormTracker;
import com.butcher.detect.vanguard.CorpseHaul;
import com.butcher.detect.vanguard.VanguardLoot;
import com.butcher.detect.vanguard.VanguardWatcher;
import com.butcher.gui.ButcherMenuScreen;
import com.butcher.render.TextureBank;
import com.butcher.render.ScathaSkins;
import com.butcher.render.art.CaveArt;
import com.butcher.render.art.PropArt;
import com.butcher.render.art.ScathaArt;
import com.butcher.gui.ScopeMinigameScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class ButcherClient implements ClientModInitializer {
    public static final String MOD_ID = "butcher";
    public static final Logger LOGGER = LoggerFactory.getLogger("Butcher");

    public static PatternConfig config;
    public static final AnimationManager animationManager = new AnimationManager();
    private static ScopeMinigameScreen currentScope;

    /** The Thaw on screen, if one is. Owned exactly the way currentScope is. */
    private static com.butcher.gui.ThawScreen currentThaw;

    /**
     * Corpses read while a Thaw was already playing.
     *
     * Two corpses can be opened within seconds of each other, and the second one's loot
     * message is hidden just like the first's - so dropping it would lose it entirely. It
     * waits here and plays straight after.
     */
    private record PendingThaw(CorpseHaul haul, int streakBefore) {}
    private static final java.util.ArrayDeque<PendingThaw> thawQueue = new java.util.ArrayDeque<>();

    /** The profit window, while it is up - the next queued corpse waits for it. */
    private static com.butcher.gui.ProfitScreen currentProfit;

    private static boolean profitShowing() {
        return currentProfit != null && !currentProfit.isClosed();
    }

    /**
     * The Thaw has ended, finished or skipped: put the corpse's profit window up.
     *
     * Deferred like every other setScreen here. The Thaw has already stepped down by the time
     * this is called, so the screen-ownership mixin lets it through.
     */
    public static void onThawFinished(CorpseHaul haul) {
        if (config == null || !config.vanguardProfit || !config.livePrices) return;
        Minecraft client = Minecraft.getInstance();
        com.butcher.gui.ProfitScreen screen = new com.butcher.gui.ProfitScreen(haul);
        currentProfit = screen;
        client.execute(() -> client.gui.setScreen(screen));
    }

    private static boolean thawActive() {
        return currentThaw != null && currentThaw.isActive();
    }
    /** The kill a cutscene has already been opened for, so one death opens one screen. */
    private static long lastOpenedKill = -1;

    // Real detection, ported from decompiling Scatha-Pro's own jar: there is
    // no chat broadcast for a Scatha kill or its drop at all. Instead we
    // watch nearby armor-stand nametags for a Scatha worm dying, then diff
    // inventory item NBT for a couple of seconds afterward to see what it
    // actually dropped. See ScathaWormTracker / HotbarDropWatcher.
    /**
     * Why each detector did what it did, in plain English, readable in-game with
     * "/butcher log". Neither a Scatha kill nor a pet drop can be summoned on demand,
     * so when something comes out wrong this narration is the only evidence there is.
     */
    public static final DecisionLog decisions = new DecisionLog();

    private static final ScathaWormTracker wormTracker = new ScathaWormTracker(decisions);
    private static final HotbarDropWatcher dropWatcher = new HotbarDropWatcher(decisions);

    /**
     * Vanguard Frozen Corpses, which announce themselves in chat and so need none of the
     * machinery above - no tracker, no claim window, no inference.
     */
    public static final VanguardWatcher vanguardWatcher =
            new VanguardWatcher(decisions, ButcherClient::onCorpseHaul);

    // Below this many hearts, a tracked Scatha's death (and therefore its drop)
    // is imminent - that's the cue to start covering the hotbar/chat so the
    // player can't peek at what they got before the cutscene reveals it.
    // Set from the /butcher menu; this is only the floor if the config is
    // somehow unreadable.
    private static final int DEFAULT_HIDE_HP = 3;

    // The Scatha pet only exists in these three rarities - there is no common
    // or uncommon Scatha pet (confirmed against the SkyBlock wiki's drop
    // table: RARE 0.24%, EPIC 0.12%, LEGENDARY 0.04%).
    private static final String[] RARITIES = {"rare", "epic", "legendary"};

    // The cutscene's cavern art, drawn by CaveArt rather than shipped as PNGs.
    public static final String TEX_CAVE = "cave_backdrop";
    public static final String TEX_GEM_L = "cave_gem_left";
    public static final String TEX_GEM_R = "cave_gem_right";
    public static final String TEX_ROCK_L = "cave_rock_left";
    public static final String TEX_ROCK_R = "cave_rock_right";
    public static final String TEX_GLOW_L = "cave_glow_left";
    public static final String TEX_GLOW_R = "cave_glow_right";
    public static final String TEX_SPARKLE = "cave_sparkle";
    public static final String TEX_BLOOM = "cave_gem_bloom";
    /** Edge darkening that thickens with every chop; see ScopeMinigameScreen. */
    public static final String TEX_VIGNETTE = "vignette";


    /**
     * Moves a config left behind by the old mod id.
     *
     * The mod used to be called "scathabutcher", so its settings lived in
     * config/scathabutcher.json and everything else in config/scathabutcher/. The rename
     * moved both, which would otherwise silently orphan them - and one of them is
     * expensive to lose: skins.json holds the real Scatha texture, and getting that back
     * means going and finding a live worm again.
     *
     * Runs before anything reads config, moves rather than copies, and never touches a
     * new-name file that already exists. Once it has run there is nothing left to find,
     * so it costs two existence checks per launch after that.
     */
    private static void adoptOldConfig() {
        Path dir = FabricLoader.getInstance().getConfigDir();
        moveAside(dir.resolve("scathabutcher.json"), dir.resolve("butcher.json"));
        moveAside(dir.resolve("scathabutcher"), dir.resolve("butcher"));
    }

    private static void moveAside(Path from, Path to) {
        try {
            if (!Files.exists(from) || Files.exists(to)) return;
            Files.move(from, to);
            LOGGER.info("[Butcher] moved {} to {} after the rename", from.getFileName(), to.getFileName());
        } catch (Exception e) {
            // Never worth failing startup over - without it the mod just starts on defaults.
            LOGGER.warn("[Butcher] could not move {} to {}", from, to, e);
        }
    }

    @Override
    public void onInitializeClient() {
        adoptOldConfig();
        config = PatternConfig.loadOrCreate();
        LOGGER.info("Butcher loaded, animation mode: {}", config.animationMode);

        // Placeholders only. The Scatha's real texture belongs to Hypixel and lives on the
        // mob itself, so ScathaSkins lifts it off a live worm the first time one is seen and
        // keeps it; these are what gets drawn until then. Registering only declares them -
        // each is drawn and uploaded the first time it is actually blitted.
        TextureBank.register(ScathaSkins.PLACEHOLDER_BODY, ScathaArt::placeholderBody);
        TextureBank.register(ScathaSkins.PLACEHOLDER_HEAD, ScathaArt::placeholderHead);

        // Props for the cutscene, all drawn in Java for the same reason as the skins.
        // The crystals share one greyscale facet sheet and get their colour from the
        // per-model tint, so a new gem costs a constant rather than an image.
        // The cavern and the two gemstone outcrops. These replace the PNGs that
        // tools/gen_cave.py and gen_gems.py used to produce - see CaveArt for why.
        TextureBank.register(TEX_CAVE, CaveArt::backdrop);
        TextureBank.register(TEX_GEM_L, () -> CaveArt.gems(false));
        TextureBank.register(TEX_GEM_R, () -> CaveArt.gems(true));
        TextureBank.register(TEX_ROCK_L, () -> CaveArt.rock(false));
        TextureBank.register(TEX_ROCK_R, () -> CaveArt.rock(true));
        TextureBank.register(TEX_GLOW_L, () -> CaveArt.rockGlow(false));
        TextureBank.register(TEX_GLOW_R, () -> CaveArt.rockGlow(true));
        TextureBank.register(TEX_SPARKLE, CaveArt::sparkle);
        TextureBank.register(TEX_BLOOM, CaveArt::gemBloom);
        TextureBank.register(TEX_VIGNETTE, CaveArt::vignette);

        TextureBank.register(PropArt.TEX_CRYSTAL, PropArt::crystalFacets);
        TextureBank.register(PropArt.TEX_GEM_GLOW, PropArt::gemGlow);
        TextureBank.register(PropArt.TEX_CLEAVER, PropArt::cleaver);
        TextureBank.register(PropArt.TEX_STONE, PropArt::stone);

        // The Thaw. One ice sheet per crack pattern per stage - drawn lazily like everything
        // else here, so the twenty-four of them cost nothing until a corpse is opened.
        for (int v = 0; v < com.butcher.render.art.ThawArt.VARIANTS; v++) {
            for (int st = 0; st < com.butcher.render.art.ThawArt.STAGES; st++) {
                final int variant = v, stage = st;
                TextureBank.register(com.butcher.render.art.ThawArt.iceName(variant, stage),
                        () -> com.butcher.render.art.ThawArt.ice(variant, stage));
            }
        }
        TextureBank.register(com.butcher.render.art.ThawArt.TEX_CORPSE, com.butcher.render.art.ThawArt::corpse);
        TextureBank.register(com.butcher.render.art.ThawArt.TEX_PICK, com.butcher.render.art.ThawArt::pick);
        TextureBank.register(com.butcher.render.art.ThawArt.TEX_RAY, com.butcher.render.art.ThawArt::ray);
        TextureBank.register(com.butcher.render.art.ThawArt.TEX_SHARD, com.butcher.render.art.ThawArt::shard);
        TextureBank.register(com.butcher.render.art.ThawArt.TEX_AURA, com.butcher.render.art.ThawArt::aura);
        TextureBank.register(com.butcher.render.VanguardItems.TEX_GLACITE_POWDER,
                com.butcher.render.art.ThawArt::glacitePowder);
        TextureBank.register(com.butcher.render.art.ThawArt.TEX_COIN, com.butcher.render.art.ThawArt::coin);
        TextureBank.register(com.butcher.render.art.ThawArt.TEX_DRIP, com.butcher.render.art.ThawArt::drip);
        for (int v = 0; v < com.butcher.render.art.ThawArt.SPLATS; v++) {
            final int variant = v;
            TextureBank.register(com.butcher.render.art.ThawArt.splatName(variant),
                    () -> com.butcher.render.art.ThawArt.splat(variant));
        }

        // Draw the cutscene on top of everything else every frame. Using
        // addLast() so it renders after (i.e. visually on top of) chat and
        // every other vanilla HUD element.
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "cutscene"),
                (graphics, tickCounter) -> ButcherHudRenderer.render(graphics, 0f));

        // Darkness closing in over the hotbar and chat the moment a Scatha's HP is
        // nearly gone, right up until the cutscene takes over - which opens from
        // black, so the two run together. Called every frame rather than only while
        // active, so the hider can run its own fade in and reset itself.
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "prekill_hide"),
                (graphics, tickCounter) -> PreKillHider.render(graphics, shouldHidePreKill()));

        // Advance the animation clock, and boot the player out of the chat
        // input box if they try to open it mid-animation. Also true for the
        // scope minigame's own Screen: pressing "t" would otherwise replace
        // it with the vanilla ChatScreen, so re-assert it while it's active.
        // This is also where the real (non-chat-based) drop detection runs:
        // both watchers are cheap per-tick scans (nearby armor stands, and
        // inventory item NBT), so they just tick alongside everything else.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            animationManager.tick();
            if (animationManager.isPlaying() && client.gui.screen() instanceof ChatScreen) {
                client.gui.setScreen(null);
            }
            // Only the bookkeeping is left here. Putting the cutscene BACK when
            // something had replaced it used to live on this line, and it is now
            // GuiScreenMixin's job to stop the replacement happening at all - which is
            // better by a whole tick, since re-asserting afterwards means the pause menu
            // really did open and the player really did see it flash past.
            if (currentScope != null && !currentScope.isActive()) {
                currentScope = null;
            }

            if (config.animationMode != AnimationMode.NONE && client.player != null) {
                wormTracker.tick(client.player);
                openCutsceneOnKill(client);
                // The watcher spots a drop by comparing this tick's inventory against
                // last tick's, so it has to run on *every* tick without exception -
                // skip one and the change that happened across the gap is invisible,
                // because the baseline it would have been measured against is already
                // gone. Only announcing the result is conditional: a cutscene that is
                // still waiting to be told what fell must be fed (that is the whole
                // point of opening early), but one that already knows must not be
                // played over the top of.
                dropWatcher.tick(client.player, wormTracker, !isAnimationPlaying() || awaitingDrop());
                com.butcher.audio.ButcherAudio.tick();
            }
            // Outside the player check: a half-read loot message has to be able to time
            // out even if the player went away mid-message.
            vanguardWatcher.tick();

            if (client.player != null) {
                // Start every Vanguard item's skin download the first time there is a player,
                // so the heads are ready long before anyone opens a corpse.
                com.butcher.render.VanguardItems.prewarm();
            }
            if (currentThaw != null && !currentThaw.isActive()) currentThaw = null;
            if (!ChatHold.isEmpty() && !chatHeld()) ChatHold.release();
            if (currentThaw == null && !thawQueue.isEmpty() && !isAnimationPlaying() && !profitShowing()) {
                PendingThaw next = thawQueue.poll();
                openThaw(client, next.haul(), next.streakBefore());
            }
        });

        // Chat still gets hidden while an animation is playing (that's the
        // whole point of the cutscene), even though it's no longer what
        // triggers the animation in the first place.
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
                allowChatLine(message, true, false));
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> allowChatLine(message, false, overlay));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // Two branches, one per drop source, and every level on its own lists what is
            // under it - "/butcher test", "/butcher test scatha" and so on each print their
            // options, clickable, instead of failing with an unhelpful "incomplete command".
            LiteralArgumentBuilder<FabricClientCommandSource> petNode = ClientCommands.literal("pet")
                    .executes(ctx -> options(ctx, "test scatha pet",
                            "rare", "a rare pet", "epic", "an epic pet", "legendary", "a legendary pet"));
            for (String rarity : RARITIES) {
                petNode.then(ClientCommands.literal(rarity).executes(ctx -> {
                    triggerTest(DropResult.pet(rarity));
                    return 1;
                }));
            }

            LiteralArgumentBuilder<FabricClientCommandSource> scathaNode = ClientCommands.literal("scatha")
                    .executes(ctx -> options(ctx, "test scatha",
                            "random", "a drop rolled against the real odds",
                            "gemstone", "the gemstone ending",
                            "block", "the Dwarven O's ending",
                            "pet", "the pet ending - pick a rarity",
                            "hide", "the pre-kill HUD hide, for four seconds",
                            "firework", "the pet-drop firework on its own"))
                    .then(ClientCommands.literal("random").executes(ctx -> {
                        triggerTest(randomDrop());
                        return 1;
                    }))
                    .then(ClientCommands.literal("gemstone").executes(ctx -> {
                        triggerTest(DropResult.gemstone());
                        return 1;
                    }))
                    .then(ClientCommands.literal("block").executes(ctx -> {
                        triggerTest(DropResult.blocked());
                        return 1;
                    }))
                    .then(petNode)
                    .then(ClientCommands.literal("hide").executes(ctx -> {
                        testPreKillHide(4000);
                        ctx.getSource().sendFeedback(Component.literal(
                                "§6[Butcher] §7running the pre-kill hide for 4 seconds"
                                        + (config.hideHud
                                        ? " §8(veil, then the HUD goes)" : " §8(veil only)")));
                        return 1;
                    }))
                    .then(ClientCommands.literal("firework").executes(ctx -> {
                        // The firework is the one payoff that cannot be checked without a
                        // pet, which is a one-in-two-thousand drop - so it gets its own
                        // command rather than being untestable in practice.
                        com.butcher.anim.PetDropFirework.burst("legendary");
                        ctx.getSource().sendFeedback(Component.literal(
                                "§6[Butcher] §7firework fired §8- look straight ahead"));
                        return 1;
                    }));

            LiteralArgumentBuilder<FabricClientCommandSource> vanguardNode = ClientCommands.literal("vanguard")
                    .executes(ctx -> options(ctx, "test vanguard",
                            "random", "a corpse rolled against the real loot table",
                            "crash", "all commons",
                            "reveal", "ends on a random uncommon",
                            "explode", "ends on a random legendary",
                            "locket", "ends on the Shattered Locket, with its own ending",
                            "dye", "ends on the Frostbitten Dye"))
                    .then(ClientCommands.literal("random").executes(ctx -> {
                        CorpseHaul haul = randomCorpse();
                        ctx.getSource().sendFeedback(Component.literal(
                                "§6[Butcher] §7rolled a corpse: §f" + haul.describe()));
                        injectTest(haul);
                        return 1;
                    }))
                    // Each ending on demand. A legendary is one corpse in fifteen and the Dye
                    // one in several thousand, so without these most of the Thaw could only
                    // ever be seen by luck.
                    .then(ClientCommands.literal("crash").executes(ctx -> testThaw(ctx, VanguardLoot.Tier.COMMON)))
                    .then(ClientCommands.literal("reveal").executes(ctx -> testThaw(ctx, VanguardLoot.Tier.UNCOMMON)))
                    .then(ClientCommands.literal("explode").executes(ctx -> testThaw(ctx, VanguardLoot.Tier.LEGENDARY)))
                    .then(ClientCommands.literal("locket").executes(ctx -> testThaw(ctx, VanguardLoot.Tier.LEGENDARY, "Shattered Locket")))
                    .then(ClientCommands.literal("dye").executes(ctx -> testThaw(ctx, VanguardLoot.Tier.SPECIAL)));

            LiteralArgumentBuilder<FabricClientCommandSource> testNode = ClientCommands.literal("test")
                    .executes(ctx -> options(ctx, "test",
                            "scatha", "the cleaver cutscene",
                            "vanguard", "the Thaw"))
                    .then(scathaNode)
                    .then(vanguardNode);

            dispatcher.register(ClientCommands.literal("butcher")
                    .executes(ctx -> {
                        // Deferred to next tick: vanilla closes the chat
                        // screen (setScreen(null)) as part of the same
                        // "send chat message" call that runs this command,
                        // right after it - opening our menu here inline
                        // would just get immediately stomped back to null
                        // by that. Queuing it with execute() lets our
                        // setScreen happen after that closes, so the menu
                        // actually stays open.
                        Minecraft client = Minecraft.getInstance();
                        client.execute(() -> client.gui.setScreen(new ButcherMenuScreen()));
                        return 1;
                    })
                    .then(testNode)
                    // Neither a kill nor a drop can be summoned on demand, so when
                    // the cutscene shows the wrong thing this narration is the only
                    // record of why. Printed to chat rather than only the log file so
                    // it can be read the moment it goes wrong, without alt-tabbing.
                    .then(ClientCommands.literal("log").executes(ctx -> {
                        java.util.List<String> lines = decisions.recent();
                        if (lines.isEmpty()) {
                            ctx.getSource().sendFeedback(Component.literal(
                                    "§7[Butcher] nothing decided yet - go and find a Scatha."));
                        } else {
                            ctx.getSource().sendFeedback(Component.literal(
                                    "§6[Butcher] last " + lines.size() + " decision(s), oldest first:"));
                            for (String line : lines) {
                                ctx.getSource().sendFeedback(Component.literal("  " + line));
                            }
                        }
                        return 1;
                    }))
                    // What the mod knows about the worm's real texture, and a way to make
                    // it forget - useful if it ever latches onto the wrong nearby head.
                    .then(ClientCommands.literal("skins")
                            .executes(ctx -> {
                                ctx.getSource().sendFeedback(Component.literal("§6[Butcher] " + ScathaSkins.status()));
                                ctx.getSource().sendFeedback(Component.literal(
                                        "§7Drop head.png / body.png in §f" + ScathaSkins.overrideDir() + "§7 to supply them yourself."));
                                return 1;
                            })
                            .then(ClientCommands.literal("forget").executes(ctx -> {
                                ScathaSkins.forget();
                                ctx.getSource().sendFeedback(Component.literal(
                                        "§6[Butcher] forgot the captured Scatha textures; they will be re-learned from the next worm."));
                                return 1;
                            })))
                    .then(ClientCommands.literal("debug")
                            .then(ClientCommands.literal("solid").executes(ctx -> {
                                com.butcher.render.MeshRenderer.debugSolid =
                                        !com.butcher.render.MeshRenderer.debugSolid;
                                ctx.getSource().sendFeedback(Component.literal(
                                        "§6[Butcher] §7flat-colour faces: §f"
                                                + (com.butcher.render.MeshRenderer.debugSolid
                                                ? "ON §8- run a cutscene; if the blade fills in, the blit is at fault"
                                                : "OFF")));
                                return 1;
                            }))
                            .executes(ctx -> {
                        for (String line : cleaverDiagnostic()) {
                            ctx.getSource().sendFeedback(Component.literal(line));
                        }
                        return 1;
                    }))
                    .then(ClientCommands.literal("reload").executes(ctx -> {
                        config = PatternConfig.loadOrCreate();
                        // Redraw the generated art too, so tweaking ScathaArt can be seen
                        // without restarting the game, and drop the cached meshes so a
                        // model newly dropped into config/butcher/models is picked up.
                        TextureBank.invalidate();
                        com.butcher.render.ArtOverrides.reload();
                        com.butcher.render.model.ScathaModels.reload();
                        com.butcher.render.scene.CutsceneScene.reload();
                        ctx.getSource().sendFeedback(Component.literal("[Butcher] config reloaded from config/butcher.json"));
                        return 1;
                    })));
        });
    }

    /**
     * Everything the cleaver needs in order to appear, checked one item at a time.
     *
     * The blade renders correctly in an offline harness at the game's own camera
     * settings, and every input to it - mesh, texture, UV rects, depth sort, anchor
     * arithmetic, draw order - has been verified away from the game. It still does not
     * appear in a real cutscene, so one of these values must differ at runtime from what
     * it is on a workstation, and this prints all of them rather than guessing which.
     *
     * Deliberately reports the actual screen size too: every size in the cutscene is
     * derived from the GUI-scaled width, so a surprising value there would explain a
     * blade that is technically on screen and a few pixels tall.
     */
    private static java.util.List<String> cleaverDiagnostic() {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("§6[Butcher] cleaver check");
        out.add("§7last real draw: §f" + ScopeMinigameScreen.lastCleaverReport);
        try {
            Minecraft mc = Minecraft.getInstance();
            int sw = mc.getWindow().getGuiScaledWidth();
            int sh = mc.getWindow().getGuiScaledHeight();
            // getGuiScale() is an int on 26.2 - a %f here is an IllegalFormatConversion.
            out.add("§7screen §f" + sw + "x" + sh + " §7gui px (scale "
                    + mc.getWindow().getGuiScale() + ")");

            var mesh = com.butcher.render.scene.CutsceneScene.cleaverMesh();
            out.add(mesh == null
                    ? "§cmesh: NULL"
                    : String.format(java.util.Locale.ROOT,
                            "§7mesh: §f%d quads, %.1f x %.1f x %.1f units",
                            mesh.quads().size(), mesh.width(), mesh.height(), mesh.depth()));

            Identifier tex = TextureBank.id(PropArt.TEX_CLEAVER);
            out.add(tex == null
                    ? "§ctexture: NULL - nothing will draw at all"
                    : "§7texture: §f" + tex + " " + TextureBank.width(PropArt.TEX_CLEAVER)
                            + "x" + TextureBank.height(PropArt.TEX_CLEAVER));

            // The same numbers ScopeMinigameScreen works out for itself.
            float pitch = com.butcher.render.model.ScathaModels.segmentPitch();
            float halfLen = com.butcher.render.scene.CutsceneScene.wormHalfLength(6);
            float sceneScale = sw * 0.52f / Math.max(1f, halfLen * 2f);
            float size = sceneScale * com.butcher.render.scene.CutsceneScene.CLEAVER_SCALE;
            out.add(String.format(java.util.Locale.ROOT,
                    "§7pitch §f%.2f §7halfLen §f%.2f §7sceneScale §f%.2f §7cleaver size §f%.2f px/unit",
                    pitch, halfLen, sceneScale, size));

            if (mesh != null) {
                var pool = new com.butcher.render.model.ModelProjection.FacePool();
                var faces = new java.util.ArrayList<com.butcher.render.model.ModelProjection.Face>();
                var cam = com.butcher.render.model.ModelProjection.Camera
                        .of(0f, 17f, size).roll(-40f);
                com.butcher.render.model.ModelProjection.project(
                        mesh, cam, 0f, 0f, 0f, 0f, false, true, faces, pool);
                float w = 0, h = 0;
                for (var f : faces) {
                    w = Math.max(w, Math.abs(f.ux) + Math.abs(f.vx));
                    h = Math.max(h, Math.abs(f.uy) + Math.abs(f.vy));
                }
                out.add(String.format(java.util.Locale.ROOT,
                        "§7projected: §f%d faces, widest quad %.1f x %.1f px", faces.size(), w, h));
                if (faces.isEmpty()) out.add("§cno faces survive projection");
                else if (h < 2f || w < 2f) out.add("§cquads are collapsing to a line");
                else out.add("§athe geometry is fine at this size");
            }
        } catch (Throwable t) {
            out.add("§cdiagnostic failed: " + t);
        }
        return out;
    }

    /**
     * A drop drawn from the real table, for {@code /butcher test scatha random}.
     *
     * Weighted the way Hypixel's actually is, near enough: the pet rarities come
     * straight off the wiki's drop table (0.24 / 0.12 / 0.04 percent) and everything
     * else is the common case. Testing against a uniform pick would mean seeing a
     * legendary every third run, which tells you nothing about how the cutscene reads
     * when it is doing what it does 99.6% of the time.
     *
     * Scaled up so a pet is worth waiting for but not unreachable - roughly one run in
     * forty rather than one in two thousand.
     */
    private static DropResult randomDrop() {
        int roll = (int) (Math.random() * 1000);
        if (roll < 4) return DropResult.pet("legendary");
        if (roll < 12) return DropResult.pet("epic");
        if (roll < 25) return DropResult.pet("rare");
        if (roll < 300) return DropResult.blocked();
        return DropResult.gemstone();
    }

    private static boolean isAnimationPlaying() {
        return animationManager.isPlaying() || (currentScope != null && currentScope.isActive()) || thawActive();
    }

    /**
     * Whether the game's own sounds should be refused right now.
     *
     * Read from {@link com.butcher.mixin.SoundEngineMixin} on every sound the game
     * submits, so it must stay cheap and must never throw - a mixin at the head of
     * SoundEngine.play is on the hot path for the whole game, not just for us.
     *
     * Covers the pre-kill veil as well as the cutscene proper. The veil exists precisely
     * because the seconds before the kill are when a drop can be spoiled, and a sound cue
     * gives it away exactly the same way a chat line does.
     */
    public static boolean gameShouldBeSilent() {
        PatternConfig c = config;
        if (c == null || !c.silenceGameAudio) return false;
        return isAnimationPlaying() || shouldHidePreKill();
    }

    /**
     * The cleaver cutscene, if one is on screen. Null otherwise.
     *
     * Exposed for {@link com.butcher.mixin.GuiScreenMixin}, which has to tell
     * "something else is trying to take the screen" from "the cutscene is being set
     * again", and cannot do that without knowing which screen is ours.
     */
    public static Screen activeCutscene() {
        if (currentScope != null && currentScope.isActive()) return currentScope;
        return thawActive() ? currentThaw : null;
    }

    /**
     * Whether a cutscene currently owns the screen and should not be replaced.
     *
     * Deliberately derived from the cutscene's own {@code isActive()} rather than from a
     * flag this class sets and clears. A flag that got stuck on would lock the player
     * out of the pause menu with no way back, and there is no worse bug this mod could
     * ship - so the only thing that can keep the screen is a cutscene that is genuinely
     * still running.
     */
    public static boolean cutsceneOwnsScreen() {
        return (currentScope != null && currentScope.isActive()) || thawActive();
    }

    /**
     * Whether the vanilla HUD should be off the screen this frame.
     *
     * Read once per frame by {@link com.butcher.mixin.HudMixin}, which answers it by not
     * building the HUD at all. Returns false unless the feature is on AND something is
     * actually happening, so it costs one comparison in ordinary play.
     *
     * During the cutscene the answer is simply yes: the cutscene is a Screen, the Screen
     * is drawn over the HUD, and its backdrop is a dim rather than a blackout, so the
     * hotbar reads straight through it.
     *
     * Before the kill it waits for {@link PreKillHider#closed()}. The veil is already
     * fading in over that second, and letting it get dark first means the hotbar is
     * covered at the moment it disappears rather than blinking out under a half-lit
     * screen.
     */
    public static boolean shouldHideHud() {
        PatternConfig c = config;
        if (c == null || !c.hideHud) return false;
        if (isAnimationPlaying()) return true;
        return shouldHidePreKill() && PreKillHider.closed();
    }

    // ------------------------------------------------------------------ Vanguard

    /**
     * Whether one incoming chat line should reach the chat box.
     *
     * Offered to the Vanguard watcher first, and <b>exactly once per line</b>: it is a
     * state machine, and handing it the same line twice would count a drop twice. The two
     * message events it is wired to carry different kinds of message, so no line reaches
     * both.
     *
     * A line that belongs to a corpse's loot message is hidden because that message IS the
     * reveal - it lists the whole haul before the animation has drawn anything, so leaving
     * it in chat is handing over the answer.
     */
    private static boolean allowChatLine(Component message, boolean player, boolean overlay) {
        if (config != null && config.vanguardAnimation && vanguardWatcher.onChatLine(message.getString())) {
            return !config.vanguardHideChat;
        }
        // The action bar never reaches the chat box, so ChatHold cannot hold it; it is kept
        // off during a cutscene here, as everything used to be. It replaces itself every
        // second, so nothing is lost by not putting it back.
        if (overlay && (isAnimationPlaying() || shouldHidePreKill())) return false;
        // Everything else goes on towards the chat box. While a cutscene or the profit window
        // covers it, it is held there rather than here - see ChatHold for why.
        return true;
    }

    /**
     * Whether chat is covered right now, so new lines should wait: a cutscene, the Scatha's
     * pre-kill hide, or the profit window.
     */
    public static boolean chatHeld() {
        return isAnimationPlaying() || shouldHidePreKill() || profitShowing();
    }

    /**
     * A Vanguard corpse has been read.
     *
     * The Thaw does not exist yet, so for now this records the haul and prints it. That
     * printing is deliberate and temporary: with the loot message hidden, a corpse would
     * otherwise be completely silent, and there would be no way to tell a working parser
     * from a broken one in normal play. It comes out when the cutscene goes in.
     */
    /** The Shattered Locket's item ID on the auction house - it is still called a pendant there. */
    public static final String LOCKET_ID = "SHATTERED_PENDANT";

    /** Set while a /butcher test corpse is being fed in, so it plays but is not counted. */
    private static boolean testingCorpse;

    /** Feeds a test corpse through the real path - Thaw, profit window and all - uncounted. */
    private static void injectTest(CorpseHaul haul) {
        testingCorpse = true;
        try {
            vanguardWatcher.inject(haul);
        } finally {
            testingCorpse = false;
        }
    }

    private static void onCorpseHaul(CorpseHaul haul) {
        int commons = 0, uncommons = 0, legendaries = 0, specials = 0;
        for (CorpseHaul.Drop drop : haul.drops()) {
            switch (drop.tier()) {
                case SPECIAL -> specials++;
                case LEGENDARY -> legendaries++;
                case UNCOMMON -> uncommons++;
                default -> commons++;
            }
        }
        // Read before counting this corpse: the Thaw's closing line is about the streak this
        // corpse either extended or ended, and after onCorpse that number has moved on.
        com.butcher.config.ButcherStats stats = com.butcher.config.ButcherStats.get();
        int streakBefore = stats.corpseDryStreak;
        // A test corpse plays exactly like a real one but leaves the stats alone: the
        // numbers are the player's real grind, and a conjured Dye is not a Dye.
        if (!testingCorpse) {
            java.util.List<String> names = new java.util.ArrayList<>();
            for (CorpseHaul.Drop drop : haul.drops()) names.add(drop.name());
            stats.countItems(names);
            stats.onCorpse(commons, uncommons, legendaries, specials);
        }
        // Every price asked for now, while the strikes are still to play: the profit window
        // and the Locket's counter need them in ten seconds or so, and by then they are back.
        com.butcher.market.CorpseValue.request(haul);

        Minecraft client = Minecraft.getInstance();
        if (config == null || !config.vanguardAnimation) {
            announceHaul(haul);
            return;
        }
        if (isAnimationPlaying() || profitShowing()) {
            thawQueue.add(new PendingThaw(haul, streakBefore));
            return;
        }
        openThaw(client, haul, streakBefore);
    }

    /**
     * Opens the Thaw.
     *
     * Claimed as {@link #currentThaw} here, synchronously, and only the setScreen deferred -
     * the same split openScope makes, for the same reason: from a command, vanilla closes the
     * chat screen straight after running it, which would stomp a setScreen made now.
     */
    private static void openThaw(Minecraft client, CorpseHaul haul, int streakBefore) {
        com.butcher.gui.ThawScreen screen = new com.butcher.gui.ThawScreen(haul, streakBefore);
        currentThaw = screen;
        client.execute(() -> client.gui.setScreen(screen));
    }

    /**
     * Puts a corpse's haul into chat.
     *
     * The loot message is hidden while the Thaw plays, because it IS the reveal. But the
     * player still wants the record of what they got, so it comes back here - after the
     * animation, or straight away when the animation is switched off. The Thaw calls this
     * when it closes, whether it finished or was skipped.
     */
    public static void announceHaul(CorpseHaul haul) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        // Held lines first, so the summary is the newest. When the profit window is about
        // to open, nothing is released yet and the summary itself is held with the rest -
        // everything then arrives together when the window closes, fresh enough to read.
        if (!chatHeld()) ChatHold.release();
        StringBuilder line = new StringBuilder("§6[Butcher] §7Vanguard corpse §8(" + haul.rolls() + " rolls)§7:");
        for (CorpseHaul.Drop drop : haul.revealOrder()) {
            line.append("\n  ").append(switch (drop.tier()) {
                case SPECIAL -> "§d";
                case LEGENDARY -> "§6";
                case UNCOMMON -> "§b";
                default -> "§f";
            }).append(drop.name()).append(drop.amount() > 1 ? " §8x" + drop.amount() : "");
        }
        client.player.sendSystemMessage(Component.literal(line.toString()));
    }

    /**
     * Lists the options under a test command, one per line, each clickable.
     *
     * Clicking puts the full command in the chat box rather than running it, so a branch
     * like "pet" can be completed and nothing plays by accident. Hovering shows what it does.
     *
     * @param pairs option, description, option, description...
     */
    private static int options(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx,
                               String path, String... pairs) {
        ctx.getSource().sendFeedback(Component.literal("§6[Butcher] §7/butcher " + path + " §8- pick one:"));
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String full = "/butcher " + path + " " + pairs[i];
            Component line = Component.literal("  §e" + pairs[i] + " §8- §7" + pairs[i + 1])
                    .withStyle(st -> st
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent.SuggestCommand(full))
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                                    Component.literal("§7Click to type §f" + full))));
            ctx.getSource().sendFeedback(line);
        }
        return 1;
    }

    /**
     * A corpse that ends a particular way: commons, then one drop of {@code tier} to finish.
     *
     * The finishing drop is picked at random from everything in that tier, evenly rather than
     * by weight - the point is to see each item the ending can show off, and weighted the
     * Shattered Locket would turn up half as often as the others.
     *
     * @param tier the ending to show; COMMON for an all-commons corpse
     */
    private static int testThaw(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx,
                                VanguardLoot.Tier tier) {
        return testThaw(ctx, tier, null);
    }

    /** As above, but ending on {@code forced} rather than a random item of the tier. */
    private static int testThaw(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx,
                                VanguardLoot.Tier tier, String forced) {
        String best = forced;
        if (best == null && tier != VanguardLoot.Tier.COMMON) {
            java.util.List<VanguardLoot.Entry> pool = new java.util.ArrayList<>();
            for (VanguardLoot.Entry e : VanguardLoot.all()) if (e.tier() == tier) pool.add(e);
            best = pool.get((int) (Math.random() * pool.size())).name();
        }
        java.util.List<CorpseHaul.Drop> drops = new java.util.ArrayList<>();
        String[] commons = {"Glacite Amalgamation", "Suspicious Scrap", "Refined Umber",
                "Blue Goblin Egg", "Flawless Onyx Gemstone", "Refined Tungsten"};
        int rolls = best == null ? 6 : 5;
        for (int i = 0; i < rolls; i++) {
            VanguardLoot.Entry e = VanguardLoot.find(commons[i % commons.length]);
            drops.add(new CorpseHaul.Drop(e, i == 0 ? 4 : (i == 1 ? 16 : 1)));
        }
        if (best != null) drops.add(new CorpseHaul.Drop(VanguardLoot.find(best), 1));
        CorpseHaul haul = new CorpseHaul(drops);
        ctx.getSource().sendFeedback(Component.literal("§6[Butcher] §7test corpse: §f" + haul.describe()));
        injectTest(haul);
        return 1;
    }

    /**
     * A corpse rolled against the real table, for {@code /butcher test vanguard random}.
     *
     * Rolls properly rather than picking a pleasing set: the point of the test command is
     * to see what the animation will actually look like most of the time, and most of the
     * time that is a pile of commons. A version that dealt a legendary every run would
     * make the pacing impossible to judge.
     */
    private static CorpseHaul randomCorpse() {
        int rolls = VanguardLoot.MIN_ROLLS
                + (int) (Math.random() * (VanguardLoot.MAX_ROLLS - VanguardLoot.MIN_ROLLS + 1));
        java.util.List<CorpseHaul.Drop> drops = new java.util.ArrayList<>();
        for (int i = 0; i < rolls; i++) {
            int pick = (int) (Math.random() * VanguardLoot.TOTAL_WEIGHT);
            for (VanguardLoot.Entry entry : VanguardLoot.all()) {
                pick -= entry.weight();
                if (pick < 0) {
                    drops.add(new CorpseHaul.Drop(entry, 1));
                    break;
                }
            }
        }
        return new CorpseHaul(drops);
    }

    /** True while a cleaver cutscene is on screen but still doesn't know what dropped. */
    private static boolean awaitingDrop() {
        return currentScope != null && currentScope.isActive() && !currentScope.isResolved();
    }

    /**
     * Opens the cleaver cutscene the moment a Scatha dies, before anyone knows what
     * it gave up.
     *
     * The drop itself can't be identified that fast. A gemstone - the commonest
     * outcome by far - is only ever inferred from the kill window closing with
     * neither a pet nor a Dwarven O's in the inventory, so waiting for the answer
     * put the cutscene a full two seconds behind the kill. The opening beats are
     * identical whatever dropped, so the screen plays them while the watcher works,
     * and {@link ScopeMinigameScreen#resolve} fills in the outcome before the first
     * cut - the first moment it actually matters.
     */
    private static void openCutsceneOnKill(Minecraft client) {
        if (config.animationMode != AnimationMode.SCOPE) return;   // the guillotine still waits for its result
        long killTime = wormTracker.getLastKillTime();
        if (killTime <= 0 || killTime == lastOpenedKill) return;
        // Claimed even when it can't be shown, so a Scatha that dies while a
        // cutscene is still running is dropped outright rather than queued up and
        // played back once the screen clears.
        lastOpenedKill = killTime;
        if (isAnimationPlaying()) return;
        openScope(client, null, false);
    }

    /**
     * @param deferSetScreen put the screen up on the next tick instead of right now. Needed
     *     only for /butcher test: vanilla closes the chat screen (setScreen(null)) as part of
     *     the same "send chat message" call that runs the command, immediately afterwards,
     *     so a setScreen here would just get stomped back to null by it.
     *     <p>
     *     The kill path must NOT defer: it is already inside the client tick (which does its
     *     own setScreen calls a few lines up), and deferring would leave one frame where the
     *     pre-kill veil has let go - it keys off {@link #isAnimationPlaying()} - but the
     *     cutscene is not on screen yet, flashing the hotbar at the worst possible moment.
     *     <p>
     *     Either way the screen is constructed and claimed as {@link #currentScope} right
     *     here rather than inside the deferred task. A drop can already be sitting in the
     *     watcher's cache when the kill lands (Hypixel hands over the loot before the armor
     *     stand disappears), in which case it resolves later in this very same tick, and
     *     {@link #trigger} has to find this screen waiting for an answer instead of starting
     *     a second cutscene on top of it.
     */
    private static void openScope(Minecraft client, DropResult result, boolean deferSetScreen) {
        openScope(client, result, deferSetScreen, false);
    }

    private static void openScope(Minecraft client, DropResult result, boolean deferSetScreen, boolean test) {
        ScopeMinigameScreen screen = new ScopeMinigameScreen(result, test);
        currentScope = screen;
        if (deferSetScreen) {
            client.execute(() -> client.gui.setScreen(screen));
        } else {
            client.gui.setScreen(screen);
        }
    }

    /** True while the hotbar/chat/held-item should be hidden: a tracked Scatha is nearly dead, or its
     *  drop-detection window is still open, and the real cutscene (which covers the whole screen itself)
     *  hasn't started yet. Public so {@link com.butcher.mixin.ItemInHandRendererMixin} can read it. */
    /**
     * Forces the pre-kill hide on until this time, for {@code /butcher test scatha hide}.
     *
     * The hide only ever fires when a real Scatha is nearby and nearly dead, which makes
     * it the one feature here that cannot be tried out - "/butcher test" conjures a drop,
     * not a worm. So it looked broken to anyone who switched it on and watched nothing
     * happen.
     */
    private static long forceHideUntil = -1;

    public static void testPreKillHide(long ms) {
        forceHideUntil = System.currentTimeMillis() + ms;
    }

    public static boolean shouldHidePreKill() {
        if (config.animationMode == AnimationMode.NONE || isAnimationPlaying()) return false;
        if (System.currentTimeMillis() < forceHideUntil) return true;
        int threshold = config.hideBelowHp > 0 ? config.hideBelowHp : DEFAULT_HIDE_HP;
        return wormTracker.lowHealthScathaNearby(threshold) || wormTracker.killedRecently(HotbarDropWatcher.KILL_WINDOW_MS);
    }

    /** Plays whichever drop animation is currently active in the /butcher menu (or none). Called both
     *  by the real detectors (worm/hotbar watching) and by the /butcher test commands. */
    public static void trigger(DropResult result) {
        trigger(result, false);
    }

    /**
     * As {@link #trigger}, for {@code /butcher test scatha}: the same cutscene, but nothing it
     * does is counted in the stats. A test is not a kill, and a pet conjured by command used
     * to count towards the real pet total and reset the real dry streak.
     */
    public static void triggerTest(DropResult result) {
        trigger(result, true);
    }

    private static void trigger(DropResult result, boolean test) {
        Minecraft client = Minecraft.getInstance();
        // A cutscene opened by the kill is already on screen waiting to be told what
        // fell - hand it the answer rather than starting a second one over the top.
        if (awaitingDrop()) {
            ScopeMinigameScreen waiting = currentScope;
            client.execute(() -> waiting.resolve(result));
            return;
        }
        switch (config.animationMode) {
            case GUILLOTINE -> animationManager.start(result);
            case SCOPE -> openScope(client, result, true, test);
            case NONE -> { }
        }
    }
}
