package com.butcher;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.butcher.anim.AnimationManager;
import com.butcher.anim.ButcherHudRenderer;
import com.butcher.config.ButcherStats;
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

    /** A profit window waiting for the player to close whatever menu they have open, and since when. */
    private static com.butcher.gui.ProfitScreen pendingProfit;
    private static long pendingProfitAt;
    /** A profit window still waiting after this long is dropped: the player has moved on. */
    private static final long PROFIT_WAIT_MS = 20_000;

    private static boolean profitShowing() {
        return pendingProfit != null || (currentProfit != null && !currentProfit.isClosed());
    }

    /**
     * The Thaw has ended, finished or skipped: put the corpse's profit window up.
     *
     * Not straight away if the player has a screen open - a chest, their inventory, a menu. Putting
     * ours over a container would close it on the client only, out of step with the server. It waits
     * (see {@link #showPendingProfit}) until nothing is open.
     */
    public static void onThawFinished(CorpseHaul haul) {
        if (config == null || !config.vanguardProfit || !config.livePrices) return;
        pendingProfit = new com.butcher.gui.ProfitScreen(haul);
        pendingProfitAt = System.currentTimeMillis();
    }

    /** Opens the waiting profit window once no screen is open; called every tick. */
    private static void showPendingProfit(Minecraft client) {
        if (pendingProfit == null) return;
        if (client.player == null || System.currentTimeMillis() - pendingProfitAt > PROFIT_WAIT_MS) {
            pendingProfit = null;
            return;
        }
        if (client.gui.screen() != null) return;
        currentProfit = pendingProfit;
        pendingProfit = null;
        client.gui.setScreen(currentProfit);
    }

    /** Diana's cutscene, "The Chopping Block", while it plays. */
    private static com.butcher.gui.ChopScreen currentChop;

    private static boolean chopActive() {
        return currentChop != null && currentChop.isActive();
    }

    /**
     * Opens "The Chopping Block" in the HUD (CutsceneHud).
     *
     * @param creature whose head goes on the block - the kill's; null picks the drop's own
     *                 creature, or any of the four for a bust
     */
    public static void openChop(com.butcher.anim.ChopTimeline.Verdict verdict,
                                com.butcher.anim.ChopTimeline.Creature creature, boolean test) {
        if (!test) {
            // The menu's Diana switches. Tests always play, whatever they say. Switched off, a kill still counts:
            // a known drop now, a kill once its drop has had time to come (unopenedDiana).
            if (config != null && !dianaPlays(verdict, creature)) {
                if (verdict == null) unopenedDiana.add(new Object[]{creature, System.currentTimeMillis()});
                else ButcherStats.get().onDiana(creature == null ? null : creature.name(),
                        verdict.win() ? com.butcher.render.DianaItems.name(verdict) : null);
                return;
            }
            // Counted before the screen is made, so its "your Nth" line includes this one. A kill
            // (verdict still null) is counted when its drop is known - ChopScreen.resolve.
            if (verdict != null) {
                ButcherStats.get().onDiana(creature == null ? null : creature.name(),
                        verdict.win() ? com.butcher.render.DianaItems.name(verdict) : null);
            }
        }
        com.butcher.gui.ChopScreen screen = new com.butcher.gui.ChopScreen(verdict, creature, test);
        currentChop = screen;
        com.butcher.render.CutsceneHud.show(screen);
    }

    /**
     * Diana kills of ours that got no screen (busts off, the creature switched off, another cutscene playing):
     * {creature, when}. Their drop line claims them; one that never comes is counted as a bust (tick).
     */
    private static final java.util.ArrayDeque<Object[]> unopenedDiana = new java.util.ArrayDeque<>();
    /** How long a kill's drop line may take - the Chopping Block's own deadline, with room to spare. */
    private static final long DIANA_DROP_WAIT_MS = com.butcher.gui.ChopScreen.RESOLVE_BY + 1_000;

    /** A Chopping Block skipped or cut into while it still waited for its drop: the kill goes back on the list. */
    public static void dianaKillUnshown(com.butcher.anim.ChopTimeline.Creature creature, long killedAt) {
        unopenedDiana.add(new Object[]{creature, killedAt});
    }

    /** Counts the unopened Diana kills whose drop never came, as busts. */
    private static void expireUnopenedDiana() {
        long now = System.currentTimeMillis();
        while (!unopenedDiana.isEmpty() && now - (long) unopenedDiana.peekFirst()[1] > DIANA_DROP_WAIT_MS) {
            com.butcher.anim.ChopTimeline.Creature c = (com.butcher.anim.ChopTimeline.Creature) unopenedDiana.pollFirst()[0];
            ButcherStats.get().onDiana(c == null ? null : c.name(), null);
        }
    }

    /** Whether the menu lets this kill or drop play the Chopping Block. */
    private static boolean dianaPlays(com.butcher.anim.ChopTimeline.Verdict verdict, com.butcher.anim.ChopTimeline.Creature creature) {
        if (!config.dianaAnimation) return false;
        if (verdict == com.butcher.anim.ChopTimeline.Verdict.DYE) return config.dianaDye;
        if (verdict == com.butcher.anim.ChopTimeline.Verdict.RELIC) return config.dianaChampion;
        if (creature == null) return true;
        return switch (creature) {
            case SPHINX -> config.dianaSphinx;
            case INQUISITOR -> config.dianaInquisitor;
            case MANTICORE -> config.dianaManticore;
            case KING_MINOS -> config.dianaKingMinos;
            case CHAMPION -> config.dianaChampion;
        };
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

    /** The player's own Great White Sharks: a kill with nothing worth announcing plays Fin's bust. */
    private static final com.butcher.detect.SharkWatcher sharkWatcher = new com.butcher.detect.SharkWatcher(
            decisions, () -> {
                ButcherStats.get().onSource("shark", null);
                if (config == null || config.fishingBust) com.butcher.render.FinPopup.showBust();
                else decisions.note("§8Fishing: bust not shown - turned off in the menu");
            });

    /** Lava fishing's rare creatures: a kill of ours opens Eruption, its drop line fills it in. */
    private static final com.butcher.detect.LavaWatcher lavaWatcher = new com.butcher.detect.LavaWatcher(
            decisions, new com.butcher.detect.LavaWatcher.Listener() {
        @Override
        public void kill(com.butcher.anim.EruptionTimeline.Creature creature, String why) {
            // Remembered even when it is switched off, so its drop is never taken for an earlier creature's.
            lastLavaKill = creature;
            lavaKillScreen = null;
            if (!lavaCreatureOn(creature)) {
                decisions.note("§8Lava: " + creature.title + " is turned off in the menu");
                return;
            }
            if (bustsOff("lava", "Lava: " + creature.title)) return;
            if (isAnimationPlaying()) {
                decisions.note("§8Lava: not opened for the kill - a cutscene is already playing (a win still plays when its drop comes)");
                return;
            }
            openEruption(creature, null, false);
            lavaKillScreen = currentEruption;
        }

        @Override
        public void resolve(com.butcher.anim.EruptionTimeline.Drop drop, String line) {
            if (drop.win() && underFloor(drop.id)) drop = com.butcher.anim.EruptionTimeline.Drop.BUST;
            boolean screen = currentEruption != null && currentEruption.isActive() && currentEruption.pending() && !currentEruption.isTest();
            if (screen && currentEruption.resolve(drop)) {
                decisions.note("§aLava: the kill's drop is " + (drop.win() ? drop.title : "nothing the animation plays - bust"));
                return;
            }
            if (drop.win() && lastLavaKill != null) {
                // Its kill never opened (something else was playing, it was skipped, or the line came too late for
                // the screen): the win plays now.
                killKnown(lastLavaKill, drop, line);
            } else if (!drop.win() && !screen && lastLavaKill != null && !killCounted(lavaKillScreen)) {
                countUnplayed("lava", null, "Lava: " + lastLavaKill.title + "'s bust");
            }
            lavaKillScreen = null;
        }

        @Override
        public void killKnown(com.butcher.anim.EruptionTimeline.Creature creature, com.butcher.anim.EruptionTimeline.Drop drop, String line) {
            com.butcher.anim.EruptionTimeline.Drop dr = drop.win() && underFloor(drop.id) ? com.butcher.anim.EruptionTimeline.Drop.BUST : drop;
            if (drop != com.butcher.anim.EruptionTimeline.Drop.CARMINE && !lavaCreatureOn(creature)) {
                countUnplayed("lava", dr.win() ? dr.title : null, "Lava: " + creature.title + " (turned off in the menu)");
                return;
            }
            if (currentEruption != null && currentEruption.isActive() && currentEruption.pending() && !currentEruption.isTest()
                    && currentEruption.creature() == creature && currentEruption.resolve(dr)) {
                return;
            }
            if (!dr.win() && bustStyle("lava") == 2) {
                countUnplayed("lava", null, "Lava: " + creature.title + "'s bust (busts are off)");
                return;
            }
            Runnable open = () -> openEruption(creature, dr, false);
            String what = "Lava: " + (dr.win() ? dr.title : creature.title + "'s bust");
            if (isAnimationPlaying()) {
                if (!makeRoom(dr.win(), what)) waitForTurn(open, dr.win(), what, () -> countUnplayed("lava", null, what));
                else open.run();
                return;
            }
            open.run();
        }
    });

    /** The last lava creature of ours that died, opened or not: whose a late win is. */
    private static com.butcher.anim.EruptionTimeline.Creature lastLavaKill;

    private static boolean lavaCreatureOn(com.butcher.anim.EruptionTimeline.Creature c) {
        if (config == null) return true;
        return switch (c) {
            case JAWBUS -> config.lavaJawbus;
            case THUNDER -> config.lavaThunder;
            case RAGNAROK -> config.lavaRagnarok;
            case SCUTTLER -> config.lavaScuttler;
            case PLHLEGBLAST -> true;
        };
    }

    /** The player's own tier IV Voidgloom Seraph: its kill opens the Seraph's Glyph, its drop line fills it in. */
    private static final com.butcher.detect.SeraphWatcher seraphWatcher = new com.butcher.detect.SeraphWatcher(
            decisions, new com.butcher.detect.SeraphWatcher.Listener() {
        @Override
        public void kill() {
            seraphKillScreen = null;
            if (bustsOff("seraph", "Seraph: the kill")) return;
            if (isAnimationPlaying()) {
                decisions.note("§8Seraph: not opened for the kill - a cutscene is already playing");
                return;
            }
            openSeraph(null, false);
            seraphKillScreen = currentSeraph;
        }

        @Override
        public void resolve(com.butcher.anim.SeraphTimeline.Drop drop, String line) {
            drop = seraphFloor(drop);
            boolean screen = currentSeraph != null && currentSeraph.isActive() && currentSeraph.pending() && !currentSeraph.isTest();
            if (screen && currentSeraph.resolve(drop)) {
                decisions.note("§aSeraph: the kill's drop is " + (drop.win() ? drop.title : "nothing the animation plays - bust"));
                return;
            }
            if (drop.win()) killKnown(drop, line);
            else if (!screen && !killCounted(seraphKillScreen)) countUnplayed("seraph", null, "Seraph: the bust");
            seraphKillScreen = null;
        }

        @Override
        public void killKnown(com.butcher.anim.SeraphTimeline.Drop drop, String line) {
            com.butcher.anim.SeraphTimeline.Drop d = seraphFloor(drop);
            if (!d.win() && bustStyle("seraph") == 2) {
                countUnplayed("seraph", null, "Seraph: the bust (busts are off)");
                return;
            }
            Runnable open = () -> openSeraph(d, false);
            String what = "Seraph: " + (d.win() ? d.title : "the bust");
            if (isAnimationPlaying()) {
                if (!makeRoom(d.win(), what)) waitForTurn(open, d.win(), what, () -> countUnplayed("seraph", null, what));
                else open.run();
                return;
            }
            open.run();
        }

        @Override
        public void spawned() {
            // The four small endings play only while their drop is worth 500k or more: have the prices in hand.
            if (config != null && !config.livePrices) return;
            com.butcher.market.Bazaar.request();
            for (com.butcher.anim.SeraphTimeline.Drop d : com.butcher.anim.SeraphTimeline.Drop.values()) {
                if (com.butcher.detect.SeraphDrops.floored(d)) com.butcher.market.LivePrice.request(d.priceId);
            }
        }
    });

    /** The 500k floor (the user's call): a floored drop worth less, or of unknown worth, plays as a bust. */
    private static com.butcher.anim.SeraphTimeline.Drop seraphFloor(com.butcher.anim.SeraphTimeline.Drop d) {
        if (d != null && d.win() && underFloor(d.priceId)) return com.butcher.anim.SeraphTimeline.Drop.BUST;
        if (d == null || !com.butcher.detect.SeraphDrops.floored(d)) return d;
        Double sell = com.butcher.market.Bazaar.sell(d.priceId);
        Long bin = com.butcher.market.LivePrice.cached(d.priceId);
        double worth = sell != null && sell > 0 ? sell : (bin != null ? bin : -1);
        if (worth >= 500_000) return d;
        decisions.note("§8Seraph: " + d.title + " is under the 500k floor (" + (worth < 0 ? "no price" : String.format("%,d", Math.round(worth))) + ") - bust");
        return com.butcher.anim.SeraphTimeline.Drop.BUST;
    }

    /** The player's own tier IV Inferno Demonlord: its kill opens Hellion, its drop line fills it in. */
    private static final com.butcher.detect.HellionWatcher hellionWatcher = new com.butcher.detect.HellionWatcher(
            decisions, new com.butcher.detect.HellionWatcher.Listener() {
        @Override
        public void kill() {
            hellionKillScreen = null;
            if (bustsOff("blaze", "Blaze: the kill")) return;
            if (isAnimationPlaying()) {
                decisions.note("§8Blaze: not opened for the kill - a cutscene is already playing");
                return;
            }
            // Opened the moment it dies, drop unknown; the drop line fills it in before the reveal.
            openHellion(null, false);
            hellionKillScreen = currentHellion;
        }

        @Override
        public void resolve(com.butcher.anim.HellionTimeline.Drop drop, String line) {
            if (drop.win() && underFloor(drop.id)) drop = com.butcher.anim.HellionTimeline.Drop.BUST;
            boolean screen = currentHellion != null && currentHellion.isActive() && currentHellion.pending() && !currentHellion.isTest();
            if (screen && currentHellion.resolve(drop)) {
                decisions.note("§aBlaze: the kill's drop is " + (drop.win() ? drop.title : "nothing the animation plays - bust"));
                return;
            }
            if (drop.win()) killKnown(drop, line);
            else if (!screen && !killCounted(hellionKillScreen)) countUnplayed("blaze", null, "Blaze: the bust");
            hellionKillScreen = null;
        }

        @Override
        public void killKnown(com.butcher.anim.HellionTimeline.Drop drop, String line) {
            com.butcher.anim.HellionTimeline.Drop dr = drop.win() && underFloor(drop.id) ? com.butcher.anim.HellionTimeline.Drop.BUST : drop;
            if (!dr.win() && bustStyle("blaze") == 2) {
                countUnplayed("blaze", null, "Blaze: the bust (busts are off)");
                return;
            }
            Runnable open = () -> openHellion(dr, false);
            String what = "Blaze: " + (dr.win() ? dr.title : "the bust");
            if (isAnimationPlaying()) {
                if (!makeRoom(dr.win(), what)) waitForTurn(open, dr.win(), what, () -> countUnplayed("blaze", null, what));
                else open.run();
                return;
            }
            open.run();
        }
    });

    /** Diana's kills (nametags) and drops (chat). */
    private static final com.butcher.detect.DianaWatcher dianaWatcher = new com.butcher.detect.DianaWatcher(
            decisions, new com.butcher.detect.DianaWatcher.Listener() {
        @Override
        public void kill(com.butcher.anim.ChopTimeline.Creature creature) {
            if (bustsOff("diana", "Diana: the kill")) {
                unopenedDiana.add(new Object[]{creature, System.currentTimeMillis()});
                return;
            }
            if (isAnimationPlaying()) {
                decisions.note("§8Diana: not opened for the kill - a cutscene is already playing");
                unopenedDiana.add(new Object[]{creature, System.currentTimeMillis()});
                return;
            }
            // Opened the moment it dies, drop unknown; the drop's chat line fills it in.
            openChop(null, creature, false);
        }

        @Override
        public void drop(com.butcher.anim.ChopTimeline.Verdict verdict0, String line) {
            com.butcher.anim.ChopTimeline.Verdict verdict = verdict0.win() && underFloor(com.butcher.render.DianaItems.id(verdict0))
                    ? com.butcher.anim.ChopTimeline.Verdict.BUST : verdict0;
            if (currentChop != null && currentChop.pending() && !currentChop.isTest() && currentChop.resolve(verdict)) {
                decisions.note("§aDiana: the kill's drop is " + com.butcher.render.DianaItems.name(verdict));
                return;
            }
            // A kill of ours that got no screen: this is its drop, and the screen it gets now counts it.
            Object[] kill = null;
            while (!unopenedDiana.isEmpty()) {
                Object[] k = unopenedDiana.pollLast();
                if (System.currentTimeMillis() - (long) k[1] <= DIANA_DROP_WAIT_MS) {
                    kill = k;
                    break;
                }
            }
            com.butcher.anim.ChopTimeline.Creature killed = kill == null ? null : (com.butcher.anim.ChopTimeline.Creature) kill[0];
            if (!verdict.win() && bustStyle("diana") == 2) {
                if (killed != null) ButcherStats.get().onDiana(killed.name(), null);
                return;
            }
            // A drop with no kill waiting: the Relic, the Dye, a loot-shared drop, or a drop whose
            // line beat the kill's nametag. The kill's head if there was one, else the drop's own
            // creature's, or any for the Dye.
            com.butcher.anim.ChopTimeline.Creature head = killed != null ? killed
                    : verdict == com.butcher.anim.ChopTimeline.Verdict.DYE ? null : com.butcher.anim.ChopTimeline.Creature.of(verdict, 0);
            Runnable open = () -> openChop(verdict, head, false);
            String what = "Diana: " + com.butcher.render.DianaItems.name(verdict);
            if (isAnimationPlaying()) {
                if (!makeRoom(verdict.win(), what)) waitForTurn(open, verdict.win(), what);
                else open.run();
                return;
            }
            open.run();
        }
    });

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
        skipKey = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(new net.minecraft.client.KeyMapping(
                "key.butcher.skip", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE,
                net.minecraft.client.KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "main"))));

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

        // The menu's title icon: the mod's own icon at its native 32 x 32 (it is 8x pixel art; halving is exact).
        TextureBank.register(ButcherMenuScreen.TEX_ICON, () -> {
            try (java.io.InputStream in = ButcherClient.class.getResourceAsStream("/assets/butcher/icon.png")) {
                return com.butcher.gui.menu.MenuIcon.scaled(in == null ? null : javax.imageio.ImageIO.read(in), com.butcher.gui.menu.MenuView.ICON_SIZE);
            } catch (Exception e) {
                return com.butcher.gui.menu.MenuIcon.scaled(null, com.butcher.gui.menu.MenuView.ICON_SIZE);
            }
        });
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
        TextureBank.register(com.butcher.render.art.ChopArt.TEX_SHEET, com.butcher.render.art.ChopArt::sheet);
        TextureBank.register(com.butcher.render.art.ChopArt.TEX_GLOW, com.butcher.render.art.ChopArt::glow);
        TextureBank.register(com.butcher.render.art.ChopArt.TEX_RAYS, com.butcher.render.art.ChopArt::rays);
        TextureBank.register(com.butcher.render.art.ChopArt.TEX_RING, com.butcher.render.art.ChopArt::ring);
        TextureBank.register(com.butcher.render.art.ChopArt.TEX_SPLAT, com.butcher.render.art.ChopArt::splat);
        TextureBank.register(com.butcher.render.art.ChopArt.TEX_DROP, com.butcher.render.art.ChopArt::drop);
        TextureBank.register(com.butcher.render.art.ChopArt.TEX_VIGNETTE, com.butcher.render.art.ChopArt::vignette);
        TextureBank.register(com.butcher.render.art.ChopArt.TEX_YARN, com.butcher.render.art.ChopArt::yarn);
        TextureBank.register(com.butcher.render.art.ChopArt.TEX_WING, com.butcher.render.art.ChopArt::wing);
        TextureBank.register(com.butcher.render.art.ChopArt.TEX_PIXDROP, com.butcher.render.art.ChopArt::pixDrop);
        TextureBank.register(com.butcher.render.art.ChopArt.TEX_PIXSPLAT, com.butcher.render.art.ChopArt::pixSplat);
        // The glint, from the game's own texture, read here at runtime - never bundled.
        TextureBank.register(com.butcher.render.art.ChopArt.TEX_GLINT, () -> {
            try (java.io.InputStream in = Minecraft.getInstance().getResourceManager()
                    .open(net.minecraft.resources.Identifier.withDefaultNamespace("textures/misc/enchanted_glint_item.png"))) {
                return com.butcher.render.art.ChopArt.glint(javax.imageio.ImageIO.read(in));
            } catch (Exception e) {
                LOGGER.warn("[Butcher] could not read the game's glint texture; drawing one instead", e);
                return com.butcher.render.art.ChopArt.glint(null);
            }
        });
        // Hellion's fire: the game's own animated strip, read at runtime (a resource pack's if there is
        // one) - adopting it by name gives only its first frame. Never bundled.
        // Eruption's lava: the game's animated strip, read at runtime (a pack's too), normalised to 20 frames of 16.
        TextureBank.register(com.butcher.render.EruptionScene.TEX_MAGMA, () -> com.butcher.render.art.LavaArt.strip(readGameTexture("textures/block/magma.png"), 3));
        TextureBank.register(com.butcher.render.EruptionScene.TEX_CRACKS, () -> com.butcher.render.art.LavaArt.crackMask(
                com.butcher.render.art.LavaArt.strip(readGameTexture("textures/block/magma.png"), 3)));
        TextureBank.register(com.butcher.render.EruptionScene.TEX_LAVA_GREY, () -> com.butcher.render.art.LavaArt.greyLava(
                com.butcher.render.art.LavaArt.strip(readGameTexture("textures/block/lava_still.png"), 20)));
        TextureBank.register(com.butcher.render.EruptionScene.TEX_LAVA, () -> com.butcher.render.art.LavaArt.strip(readGameTexture("textures/block/lava_still.png"), 20));
        TextureBank.register(com.butcher.render.HellionScene.TEX_FIRE, () -> com.butcher.render.art.HellionArt.fireStrip(readGameTexture("textures/block/fire_0.png")));
        TextureBank.register(com.butcher.render.HellionScene.TEX_FIRE_GREY, () -> com.butcher.render.art.HellionArt.greyFire(
                com.butcher.render.art.HellionArt.fireStrip(readGameTexture("textures/block/fire_0.png"))));
        TextureBank.register(com.butcher.render.SeraphScene.TEX_WHITE, () -> {
            java.awt.image.BufferedImage white = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) white.setRGB(x, y, 0xFFFFFFFF);
            return white;
        });
        TextureBank.register(com.butcher.render.VanguardItems.TEX_GLACITE_POWDER,
                com.butcher.render.art.ThawArt::glacitePowder);
        TextureBank.register(com.butcher.render.art.ThawArt.TEX_COIN, com.butcher.render.art.ThawArt::coin);
        TextureBank.register(com.butcher.render.art.ThawArt.TEX_DRIP, com.butcher.render.art.ThawArt::drip);
        for (int v = 0; v < com.butcher.render.art.ThawArt.SPLATS; v++) {
            final int variant = v;
            TextureBank.register(com.butcher.render.art.ThawArt.splatName(variant),
                    () -> com.butcher.render.art.ThawArt.splat(variant));
        }

        // The cutscenes are not HUD elements any more: mixin/GuiFadeMixin draws them itself, on
        // a stratum of their own, once the HUD pass and its fade are over (drawOwnHud). Inside
        // the pass, 0.2.37 played the sounds and drew nothing.

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
            }
            // Every animation's sound, not only the Scatha's: inside the check above, switching the
            // cleaver off left the synth unstarted and every other cutscene silent.
            com.butcher.audio.ButcherAudio.tick();
            // Outside the player check: a half-read loot message has to be able to time
            // out even if the player went away mid-message.
            vanguardWatcher.tick();

            if (client.player != null && config.dianaAnimation) dianaWatcher.tick(client.player, config.dianaHidePct);
            if (client.player != null && config.fishingAnnounce) sharkWatcher.tick(client.player);
            if (client.player != null && config.blazeAnimation) hellionWatcher.tick(client.player, config.blazeHidePct);
            if (client.player != null && seraphOn()) seraphWatcher.tick(client.player, config.seraphHidePct);
            if (client.player != null && config.lavaAnimation) {
                lavaWatcher.lootShare = config.lavaLootShare;
                lavaWatcher.tick(client.player, config.lavaHidePct);
            }
            // Slayer bosses' nametags, recorded for detection to be written against (no animation yet).
            if (client.player != null && config.recordDrops) com.butcher.detect.SlayerRecorder.tick(client.player);
            if (client.player != null) {
                // Start every Vanguard item's skin download the first time there is a player,
                // so the heads are ready long before anyone opens a corpse.
                com.butcher.render.VanguardItems.prewarm();
                com.butcher.render.HeadSkins.FISHING.prewarm();
                com.butcher.render.HeadSkins.SLAYER.prewarm();
                com.butcher.render.HeadSkins.LAVA.prewarmPixels();
                // A head we could not find in any repo, taken from whatever menu shows it (the bazaar).
                com.butcher.detect.HeadCapture.tick(client);
            }
            // Out of the world (disconnected, or back at the title screen): nothing waiting from the old
            // server may play or pour its chat into the next one.
            if (client.player == null && (!ChatHold.isEmpty() || !waiting.isEmpty() || !thawQueue.isEmpty())) {
                ChatHold.clear();
                waiting.clear();
                thawQueue.clear();
            }
            showPendingProfit(client);
            expireUnopenedDiana();
            skipTick();
            floorPrices();
            if (currentThaw != null && !currentThaw.isActive()) currentThaw = null;
            clearFinished();
            runWaiting();
            if (!ChatHold.isEmpty() && !chatHeld()) ChatHold.release();
            if (currentThaw == null && !thawQueue.isEmpty() && !isAnimationPlaying() && !profitShowing()) {
                PendingThaw next = thawQueue.poll();
                openThaw(client, next.haul(), next.streakBefore());
            }
        });

        // Chat still gets hidden while an animation is playing (that's the
        // whole point of the cutscene), even though it's no longer what
        // triggers the animation in the first place.
        // In a phase of our own, ahead of every other mod's: Fabric stops at the first listener that vetoes a
        // line, and Feesh vetoes Hypixel's sea creature catch lines (replacing them with its own local line),
        // so after it we never saw them. Our listener only hides what it always hid.
        net.minecraft.resources.Identifier first = net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "first");
        ClientReceiveMessageEvents.ALLOW_CHAT.addPhaseOrdering(first, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
        ClientReceiveMessageEvents.ALLOW_GAME.addPhaseOrdering(first, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
        ClientReceiveMessageEvents.ALLOW_CHAT.register(first, (message, signedMessage, sender, params, receptionTimestamp) ->
                allowChatLine(message, true, false));
        ClientReceiveMessageEvents.ALLOW_GAME.register(first, (message, overlay) -> allowChatLine(message, false, overlay));

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
                                        ? " §8(the HUD goes)" : " §8(HUD hiding is off)")));
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

            LiteralArgumentBuilder<FabricClientCommandSource> dianaNode = ClientCommands.literal("diana")
                    .executes(ctx -> options(ctx, "test diana",
                            "bust", "nothing inside",
                            "relic", "Minos Relic (moonstone)",
                            "brainfood", "Brain Food (wisteria)",
                            "chimera", "Chimera I (thulian pink)",
                            "dye", "Mythological Dye (olive)",
                            "stinger", "Fateful Stinger (viridian)",
                            "manticore", "Manti-core (viridian)",
                            "crown", "Crown of Greed (carmine)",
                            "wool", "Shimmering Wool (carmine)",
                            "unknown", "the neutral ending, when a drop cannot be identified"));
            for (com.butcher.anim.ChopTimeline.Verdict v : com.butcher.anim.ChopTimeline.Verdict.values()) {
                String word = switch (v) {
                    case BRAIN_FOOD -> "brainfood";
                    case FATEFUL_STINGER -> "stinger";
                    case MANTI_CORE -> "manticore";
                    case CROWN_OF_GREED -> "crown";
                    case SHIMMERING_WOOL -> "wool";
                    default -> v.name().toLowerCase(java.util.Locale.ROOT);
                };
                dianaNode.then(ClientCommands.literal(word).executes(ctx -> {
                    testChop(v);
                    return 1;
                }));
            }

            LiteralArgumentBuilder<FabricClientCommandSource> blazeNode = ClientCommands.literal("blaze")
                    .executes(ctx -> options(ctx, "test slayer blaze",
                            "bust", "burns out to ash",
                            "duplex", "Duplex I (Spirit)",
                            "crystal", "Scorched Power Crystal (Spirit)",
                            "subzero", "Subzero Inverter (Spirit)",
                            "wilsons", "Wilson's Engineering Plans (Spirit)",
                            "paragon", "Paragon Shard (Crystal)",
                            "dice", "Archfiend Dice (Auric)",
                            "highclass", "High Class Archfiend Dice (Auric)",
                            "flamedye", "Flame Dye (Auric)"));
            for (com.butcher.anim.HellionTimeline.Drop d : com.butcher.anim.HellionTimeline.Drop.values()) {
                String word = switch (d) {
                    case SCORCHED_CRYSTAL -> "crystal";
                    case ARCHFIEND_DICE -> "dice";
                    case HIGH_CLASS_DICE -> "highclass";
                    case FLAME_DYE -> "flamedye";
                    default -> d.name().toLowerCase(java.util.Locale.ROOT);
                };
                blazeNode.then(ClientCommands.literal(word).executes(ctx -> {
                    Minecraft.getInstance().execute(() -> openHellion(d, true));
                    return 1;
                }));
            }
            LiteralArgumentBuilder<FabricClientCommandSource> endermanNode = ClientCommands.literal("enderman")
                    .executes(ctx -> options(ctx, "test slayer enderman",
                            "bust", "a black beam - nothing worth it",
                            "merger", "Etherwarp Merger (white)",
                            "eye", "Summoning Eye (light blue)",
                            "paragon", "Paragon Shard (light blue)",
                            "rune", "Enchant Rune I (light blue)",
                            "chalice", "Handy Blood Chalice (its own ending)",
                            "espresso", "Pocket Espresso Machine (its own ending)",
                            "dice", "Sinful Dice (its own ending)",
                            "skin", "Void Conqueror Enderman Skin (its own ending)",
                            "upgrade", "Exceedingly Rare Ender Artifact Upgrade (the jackpot)",
                            "idol", "End Stone Idol (the jackpot)",
                            "core", "Judgement Core (the judgement)",
                            "dye", "Byzantium Dye (the judgement)"));
            for (com.butcher.anim.SeraphTimeline.Drop d : com.butcher.anim.SeraphTimeline.Drop.values()) {
                String word = switch (d) {
                    case ETHERWARP_MERGER -> "merger";
                    case SUMMONING_EYE -> "eye";
                    case ENCHANT_RUNE -> "rune";
                    case BLOOD_CHALICE -> "chalice";
                    case SINFUL_DICE -> "dice";
                    case VOID_SKIN -> "skin";
                    case ARTIFACT_UPGRADE -> "upgrade";
                    case END_STONE_IDOL -> "idol";
                    case JUDGEMENT_CORE -> "core";
                    case BYZANTIUM_DYE -> "dye";
                    default -> d.name().toLowerCase(java.util.Locale.ROOT);
                };
                endermanNode.then(ClientCommands.literal(word).executes(ctx -> {
                    Minecraft.getInstance().execute(() -> openSeraph(d, true));
                    return 1;
                }));
            }
            LiteralArgumentBuilder<FabricClientCommandSource> slayerNode = ClientCommands.literal("slayer")
                    .executes(ctx -> options(ctx, "test slayer",
                            "blaze", "Hellion - the Inferno Demonlord",
                            "enderman", "The Seraph's Glyph - the Voidgloom Seraph"))
                    .then(blazeNode)
                    .then(endermanNode);

            LiteralArgumentBuilder<FabricClientCommandSource> sharkNode = ClientCommands.literal("shark")
                    .executes(ctx -> options(ctx, "test shark",
                            "megalodon", "an epic Megalodon pet",
                            "legendary", "a legendary Megalodon pet",
                            "tooth", "a Great White Shark Tooth",
                            "bust", "a Great White Shark that dropped neither"))
                    .then(ClientCommands.literal("megalodon").executes(ctx -> {
                        com.butcher.render.FinPopup.show(com.butcher.detect.SharkDrops.Drop.MEGALODON_EPIC);
                        return 1;
                    }))
                    .then(ClientCommands.literal("legendary").executes(ctx -> {
                        com.butcher.render.FinPopup.show(com.butcher.detect.SharkDrops.Drop.MEGALODON_LEGENDARY);
                        return 1;
                    }))
                    .then(ClientCommands.literal("tooth").executes(ctx -> {
                        com.butcher.render.FinPopup.show(com.butcher.detect.SharkDrops.Drop.TOOTH);
                        return 1;
                    }))
                    .then(ClientCommands.literal("bust").executes(ctx -> {
                        com.butcher.render.FinPopup.showBust();
                        return 1;
                    }));

            LiteralArgumentBuilder<FabricClientCommandSource> ghostNode = ClientCommands.literal("ghost")
                    .executes(ctx -> options(ctx, "test ghost",
                            "sorrow", "a Ghost drops Sorrow",
                            "plasma", "a Ghost drops Plasma"))
                    .then(ClientCommands.literal("sorrow").executes(ctx -> {
                        com.butcher.render.GhostPopup.show(com.butcher.detect.GhostDrops.Drop.SORROW);
                        return 1;
                    }))
                    .then(ClientCommands.literal("plasma").executes(ctx -> {
                        com.butcher.render.GhostPopup.show(com.butcher.detect.GhostDrops.Drop.PLASMA);
                        return 1;
                    }));

            LiteralArgumentBuilder<FabricClientCommandSource> lavaNode = ClientCommands.literal("lava")
                    .executes(ctx -> options(ctx, "test lava",
                            "bust", "it cools to black (add a creature: jawbus, thunder, ragnarok, scuttler)",
                            "chain", "Chain of the End Times (red, Ragnarok)",
                            "pincer", "Severed Pincer (red, Fiery Scuttler)",
                            "flash", "Flash I (orange, Thunder)",
                            "brimstone", "Brimstone Handle (orange, Ragnarok)",
                            "vial", "Radioactive Vial (yellow, Lord Jawbus)",
                            "shell", "Scuttler Shell (yellow, Fiery Scuttler)",
                            "texts", "Burnt Texts (white, Ragnarok - the End Times)",
                            "carmine", "Carmine Dye (white - Kuudra; add a creature)"));
            for (com.butcher.anim.EruptionTimeline.Drop d : com.butcher.anim.EruptionTimeline.Drop.values()) {
                String word = d == com.butcher.anim.EruptionTimeline.Drop.BURNT_TEXTS ? "texts" : d.name().toLowerCase(java.util.Locale.ROOT);
                com.butcher.anim.EruptionTimeline.Creature own = d.from != null ? d.from
                        : (d == com.butcher.anim.EruptionTimeline.Drop.CARMINE ? com.butcher.anim.EruptionTimeline.Creature.THUNDER
                        : com.butcher.anim.EruptionTimeline.Creature.JAWBUS);
                LiteralArgumentBuilder<FabricClientCommandSource> dropNode = ClientCommands.literal(word).executes(ctx -> {
                    Minecraft.getInstance().execute(() -> openEruption(own, d, true));
                    return 1;
                });
                if (d.from == null) {
                    for (com.butcher.anim.EruptionTimeline.Creature c : com.butcher.anim.EruptionTimeline.Creature.values()) {
                        if (d == com.butcher.anim.EruptionTimeline.Drop.BUST && c == com.butcher.anim.EruptionTimeline.Creature.PLHLEGBLAST) continue;
                        dropNode.then(ClientCommands.literal(c.name().toLowerCase(java.util.Locale.ROOT)).executes(ctx -> {
                            Minecraft.getInstance().execute(() -> openEruption(c, d, true));
                            return 1;
                        }));
                    }
                }
                lavaNode.then(dropNode);
            }

            // The developer commands (test, debug) exist only for the accounts in Dev - for everyone else
            // they are not there at all, not even in tab completion. Players use the menu's "Try it".
            LiteralArgumentBuilder<FabricClientCommandSource> testNode = ClientCommands.literal("test")
                    .requires(src -> Dev.allowed())
                    .executes(ctx -> options(ctx, "test",
                            "scatha", "the cleaver cutscene",
                            "vanguard", "the Thaw",
                            "diana", "the Chopping Block",
                            "shark", "Fin, the Fishing Festival pop-up",
                            "ghost", "Wisp, a Ghost's Sorrow or Plasma",
                            "slayer", "the slayer bosses",
                            "lava", "Eruption, lava fishing"))
                    .then(scathaNode)
                    .then(vanguardNode)
                    .then(dianaNode)
                    .then(sharkNode)
                    .then(ghostNode)
                    .then(slayerNode)
                    .then(lavaNode);

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
                    .then(ClientCommands.literal("records").executes(ctx -> {
                        int[] n = com.butcher.detect.DropRecorder.counts();
                        ctx.getSource().sendFeedback(Component.literal("§6[Butcher] §7" + n[0]
                                + " record(s) in §f" + com.butcher.detect.DropRecorder.file()
                                + (config.recordDrops ? "" : " §8(recording is off - General tab, Drop records)")));
                        return 1;
                    }))
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
                            .requires(src -> Dev.allowed())
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
                        ctx.getSource().sendFeedback(Component.literal("§6[Butcher] §7settings reloaded from §fconfig/butcher.json"));
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

    /** A source's busts setting: 0 the whole cutscene, 1 short, 2 none. */
    public static int bustStyle(String source) {
        if (config == null) return 0;
        return switch (source) {
            case "diana" -> config.dianaBusts;
            case "blaze" -> config.blazeBusts;
            case "seraph" -> config.seraphBusts;
            case "lava" -> config.lavaBusts;
            default -> 0;
        };
    }

    /** "Busts: none": a kill opens nothing, and only its win (its drop line) plays. */
    private static boolean bustsOff(String source, String what) {
        if (bustStyle(source) != 2) return false;
        decisions.note("§8" + what + " - busts are off; it plays only if its drop is a win");
        return true;
    }

    /**
     * "Only play drops worth at least" (the menu): a win whose known price is under it plays as a bust. A price not
     * fetched yet lets the win play - a missing price never costs anyone a reveal.
     */
    public static boolean underFloor(String priceId) {
        if (config == null || config.minWorth <= 0 || priceId == null) return false;
        long floor = PatternConfig.MIN_WORTH[Math.min(config.minWorth, PatternConfig.MIN_WORTH.length - 1)];
        Double sell = com.butcher.market.Bazaar.sell(priceId);
        Long bin = com.butcher.market.LivePrice.cached(priceId);
        double worth = sell != null && sell > 0 ? sell : (bin != null && bin > 0 ? bin : -1);
        if (worth < 0 || worth >= floor) return false;
        decisions.note("§8" + priceId + " is worth " + String.format("%,d", Math.round(worth)) + ", under your floor - played as a bust");
        return true;
    }

    /** Asks for every animated drop's price, so the floor can judge a win the moment it lands. */
    private static long floorPricesAt;

    private static void floorPrices() {
        if (config == null || config.minWorth <= 0 || !config.livePrices) return;
        long now = System.currentTimeMillis();
        if (now - floorPricesAt < 10 * 60_000) return;
        floorPricesAt = now;
        com.butcher.market.Bazaar.request();
        for (var d : com.butcher.anim.HellionTimeline.Drop.values()) if (d.id != null) com.butcher.market.LivePrice.request(d.id);
        for (var d : com.butcher.anim.SeraphTimeline.Drop.values()) if (d.priceId != null) com.butcher.market.LivePrice.request(d.priceId);
        for (var d : com.butcher.anim.EruptionTimeline.Drop.values()) if (d.id != null) com.butcher.market.LivePrice.request(d.id);
        for (var v : com.butcher.anim.ChopTimeline.Verdict.values()) {
            String id = com.butcher.render.DianaItems.id(v);
            if (id != null) com.butcher.market.LivePrice.request(id);
        }
    }

    /** "Chat summary": one line in chat when a cutscene ends (held with the rest of chat until it is gone). */
    public static void summary(String line) {
        if (config != null && !config.chatSummary) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§6[Butcher] §f" + line));
    }

    /** The menu's "Try it": plays an outcome as /butcher test does (never counted), over whatever was playing. */
    public static void playTest(String group, String name) {
        // The Scatha and the Thaw cannot be cut into: the try waits until they are over.
        if ((currentScope != null && currentScope.isActive()) || thawActive()) {
            waitForTurn(() -> playTest(group, name), true, "Try it");
            return;
        }
        com.butcher.gui.Cutscene c = currentCutscene();
        if (c != null) c.interrupt();
        clearFinished();
        switch (group) {
            case "diana" -> openChop(com.butcher.anim.ChopTimeline.Verdict.valueOf(name), null, true);
            case "blaze" -> openHellion(com.butcher.anim.HellionTimeline.Drop.valueOf(name), true);
            case "seraph" -> {
                // Under maintenance: only the developers may see it, from the menu or the commands.
                if (!Maintenance.SERAPH || Dev.allowed()) openSeraph(com.butcher.anim.SeraphTimeline.Drop.valueOf(name), true);
            }
            case "lava" -> {
                com.butcher.anim.EruptionTimeline.Drop d = com.butcher.anim.EruptionTimeline.Drop.valueOf(name);
                com.butcher.anim.EruptionTimeline.Creature who = com.butcher.anim.EruptionTimeline.Creature.JAWBUS;
                for (var k : com.butcher.anim.EruptionTimeline.Creature.values()) {
                    if (k != com.butcher.anim.EruptionTimeline.Creature.PLHLEGBLAST && d.from(k)) {
                        who = k;
                        break;
                    }
                }
                openEruption(who, d, true);
            }
            case "scatha" -> triggerTest(switch (name) {
                case "PET_RARE" -> DropResult.pet("rare");
                case "PET_EPIC" -> DropResult.pet("epic");
                case "PET_LEGENDARY" -> DropResult.pet("legendary");
                case "DWARVEN_OS" -> DropResult.blocked();
                case "RANDOM" -> randomDrop();
                default -> DropResult.gemstone();
            });
            case "vanguard" -> injectTest(switch (name) {
                case "RANDOM" -> randomCorpse();
                case "UNCOMMON" -> endingCorpse(VanguardLoot.Tier.UNCOMMON, null);
                case "LEGENDARY" -> endingCorpse(VanguardLoot.Tier.LEGENDARY, null);
                case "LOCKET" -> endingCorpse(VanguardLoot.Tier.LEGENDARY, "Shattered Locket");
                case "DYE" -> endingCorpse(VanguardLoot.Tier.SPECIAL, null);
                default -> endingCorpse(VanguardLoot.Tier.COMMON, null);
            });
            case "ghost" -> com.butcher.render.GhostPopup.show(com.butcher.detect.GhostDrops.Drop.valueOf(name));
            case "shark" -> {
                if (name.equals("BUST")) com.butcher.render.FinPopup.showBust();
                else com.butcher.render.FinPopup.show(com.butcher.detect.SharkDrops.Drop.valueOf(name));
            }
            default -> { }
        }
    }

    /** "Reset all settings": the defaults back, stats untouched. */
    public static void resetSettings() {
        config = PatternConfig.fresh();
        decisions.note("§7Settings reset to the defaults");
    }

    /** The newer cutscene playing now, if one is: the ones that can be skipped and cut into. */
    private static com.butcher.gui.Cutscene currentCutscene() {
        if (chopActive()) return currentChop;
        if (hellionActive()) return currentHellion;
        if (seraphActive()) return currentSeraph;
        if (eruptionActive()) return currentEruption;
        return null;
    }

    /**
     * A known outcome wants the screen while something plays. A win cuts in over a bust, or over a kill still waiting
     * for its drop (nearly always a bust): that one ends now. Nothing cuts into a win, a Thaw or the Scatha.
     *
     * @return whether the screen is free now
     */
    private static boolean makeRoom(boolean win, String what) {
        if (!isAnimationPlaying()) return true;
        com.butcher.gui.Cutscene c = currentCutscene();
        if (!win || c == null || c.showsWin()) return false;
        decisions.note("§a" + what + " - cutting in over the cutscene playing");
        c.interrupt();
        clearFinished();
        return !isAnimationPlaying();
    }

    /** Known outcomes that came while another cutscene played: run, oldest first, when the screen is free. */
    private static final java.util.ArrayDeque<Object[]> waiting = new java.util.ArrayDeque<>();
    /** A bust that waits longer than this is no longer worth showing; a win always waits its turn. */
    private static final long BUST_WAIT_MS = 5_000;

    private static void waitForTurn(Runnable open, boolean win, String what) {
        waitForTurn(open, win, what, null);
    }

    /** @param dropped run if it waits too long and is dropped (a bust's stats, which no screen will count now) */
    private static void waitForTurn(Runnable open, boolean win, String what, Runnable dropped) {
        waiting.add(new Object[]{open, System.currentTimeMillis(), win, what, dropped});
        decisions.note("§7" + what + " - waiting for the cutscene playing to end");
    }

    /**
     * A real kill whose outcome is known but which no screen will show - busts switched off, its creature switched
     * off, or no room - still goes into the stats: a screen counts its own kill, and without this the dry streak of
     * someone with "Busts: none" never moved.
     */
    /** The screen each source opened for its latest kill, until that kill's outcome is in. */
    private static com.butcher.gui.Cutscene hellionKillScreen, seraphKillScreen, lavaKillScreen;

    /** Whether the screen opened for a kill has already put it in the stats (it gave up waiting and counted a bust). */
    private static boolean killCounted(com.butcher.gui.Cutscene screen) {
        if (screen instanceof com.butcher.gui.HellionScreen h) return h.counted();
        if (screen instanceof com.butcher.gui.SeraphScreen sc) return sc.counted();
        if (screen instanceof com.butcher.gui.EruptionScreen e) return e.counted();
        return false;
    }

    private static void countUnplayed(String source, String drop, String what) {
        ButcherStats.get().onSource(source, drop);
        decisions.note("§8" + what + " - not played, counted in the stats");
    }

    private static void runWaiting() {
        while (!waiting.isEmpty() && !isAnimationPlaying() && !profitShowing()) {
            Object[] w = waiting.poll();
            if (!(boolean) w[2] && System.currentTimeMillis() - (long) w[1] > BUST_WAIT_MS) {
                decisions.note("§8" + w[3] + " - waited too long, dropped");
                if (w[4] != null) ((Runnable) w[4]).run();
                continue;
            }
            ((Runnable) w[0]).run();
        }
    }

    private static void clearFinished() {
        if (currentChop != null && !currentChop.isActive()) currentChop = null;
        if (currentHellion != null && !currentHellion.isActive()) currentHellion = null;
        if (currentSeraph != null && !currentSeraph.isActive()) currentSeraph = null;
        if (currentEruption != null && !currentEruption.isActive()) currentEruption = null;
    }

    /** The skip key (default Backspace): held this long, so a stray press cannot eat a reveal. */
    private static final long SKIP_HOLD_MS = 400;
    private static net.minecraft.client.KeyMapping skipKey;
    private static long skipHeldSince = -1;
    private static boolean skipFired;

    private static void skipTick() {
        if (skipKey == null || !skipKey.isDown()) {
            skipHeldSince = -1;
            skipFired = false;
            return;
        }
        long now = System.currentTimeMillis();
        if (skipHeldSince < 0) skipHeldSince = now;
        if (skipFired || now - skipHeldSince < SKIP_HOLD_MS) return;
        skipFired = true;
        com.butcher.gui.Cutscene c = currentCutscene();
        if (c != null) {
            c.skip();
        } else if (currentScope != null && currentScope.isActive()) {
            // Skipped before its drop was known: the drop, when it comes, is counted for this run and opens nothing.
            if (!currentScope.isResolved()) {
                skippedScope = currentScope;
                skippedScopeAt = System.currentTimeMillis();
            }
            currentScope.onClose();
        } else if (thawActive()) {
            currentThaw.onClose();
        }
    }

    private static boolean isAnimationPlaying() {
        return animationManager.isPlaying() || (currentScope != null && currentScope.isActive()) || thawActive()
                || chopActive() || hellionActive() || seraphActive() || eruptionActive();
    }

    /** "Eruption", lava fishing's animation, while it plays. */
    private static com.butcher.gui.EruptionScreen currentEruption;

    private static boolean eruptionActive() {
        return currentEruption != null && currentEruption.isActive();
    }

    /** Opens Eruption in the HUD (CutsceneHud); {@code drop} null for a real kill whose drop is still to come. */
    public static void openEruption(com.butcher.anim.EruptionTimeline.Creature creature, com.butcher.anim.EruptionTimeline.Drop drop, boolean test) {
        com.butcher.gui.EruptionScreen screen = new com.butcher.gui.EruptionScreen(creature, drop, test);
        currentEruption = screen;
        com.butcher.render.CutsceneHud.show(screen);
    }

    /** "Hellion", the Inferno Demonlord's animation (tier IV), while it plays. */
    private static com.butcher.gui.HellionScreen currentHellion;

    private static boolean hellionActive() {
        return currentHellion != null && currentHellion.isActive();
    }

    /** "The Seraph's Glyph", the Voidgloom Seraph's animation (tier IV), while it plays. */
    private static com.butcher.gui.SeraphScreen currentSeraph;

    private static boolean seraphActive() {
        return currentSeraph != null && currentSeraph.isActive();
    }

    /** Opens the Seraph's Glyph in the HUD (CutsceneHud); {@code drop} null for a real kill whose drop is still to come. */
    public static void openSeraph(com.butcher.anim.SeraphTimeline.Drop drop, boolean test) {
        com.butcher.gui.SeraphScreen screen = new com.butcher.gui.SeraphScreen(drop, test);
        currentSeraph = screen;
        com.butcher.render.CutsceneHud.show(screen);
    }

    /** Opens Hellion in the HUD (CutsceneHud); {@code drop} null for a real kill whose drop is still to come. */
    public static void openHellion(com.butcher.anim.HellionTimeline.Drop drop, boolean test) {
        com.butcher.gui.HellionScreen screen = new com.butcher.gui.HellionScreen(drop, test);
        currentHellion = screen;
        com.butcher.render.CutsceneHud.show(screen);
    }

    /**
     * Whether the game's own sounds should be refused right now.
     *
     * Read from {@link com.butcher.mixin.SoundEngineMixin} on every sound the game
     * submits, so it must stay cheap and must never throw - a mixin at the head of
     * SoundEngine.play is on the hot path for the whole game, not just for us.
     *
     * Covers the pre-kill hide as well as the cutscene proper. The hide exists precisely
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
        if (chopActive()) return currentChop;
        if (hellionActive()) return currentHellion;
        if (seraphActive()) return currentSeraph;
        if (eruptionActive()) return currentEruption;
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
        return (currentScope != null && currentScope.isActive()) || thawActive() || chopActive() || hellionActive() || seraphActive() || eruptionActive();
    }

    /**
     * Whether the vanilla HUD should be off the screen this frame.
     *
     * Read by {@code mixin/HudFadeMixin} for the whole HUD pass - every mod's elements, not only
     * vanilla's. Returns false unless the feature is on AND something is actually happening, so it
     * costs one comparison in ordinary play.
     *
     * During the cutscene the answer is simply yes: the cutscene is drawn over the HUD, and
     * its backdrop can be a dim rather than a blackout, so the hotbar would read through it.
     *
     * Before a Scatha's kill it is yes the moment the pre-kill hide is up - the HUD just goes,
     * as it does for Diana. There used to be a dark veil growing over the screen first
     * ({@code PreKillHider}); the user asked for it gone.
     */
    public static boolean shouldHideHud() {
        PatternConfig c = config;
        if (c == null || !c.hideHud) return false;
        return isAnimationPlaying() || shouldHidePreKill();
    }

    /**
     * Our cutscenes, drawn by mixin/GuiFadeMixin after the HUD pass and its fade - never inside
     * it - on a fresh stratum, so nothing the HUD did can cover or fade them. Before screens, so a
     * menu still opens over a cutscene that keeps playing underneath.
     */
    public static void drawOwnHud(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                  net.minecraft.client.DeltaTracker delta) {
        net.minecraft.client.gui.screens.Screen cutscene = com.butcher.render.CutsceneHud.shown();
        boolean fin = com.butcher.render.FinPopup.active() || com.butcher.render.GhostPopup.active();
        boolean vitals = keepVitals();
        if (cutscene == null && !animationManager.isPlaying() && !fin) {
            if (vitals) drawVitals(graphics, delta);
            return;
        }
        graphics.nextStratum();
        if (fin) {
            com.butcher.render.FinPopup.draw(graphics);
            com.butcher.render.GhostPopup.draw(graphics);
        }
        int before = com.butcher.render.HudFade.added;
        ButcherHudRenderer.render(graphics, 0f);
        com.butcher.render.CutsceneHud.draw(graphics, delta);
        // Sounds without a picture was the 0.2.37 bug: say so if it ever happens again.
        if (cutscene != null && com.butcher.render.HudFade.added == before && cutscene != silentCutscene) {
            silentCutscene = cutscene;
            decisions.note("§cA cutscene drew nothing this frame - please send /butcher log");
        }
        if (vitals) drawVitals(graphics, delta);
    }

    /** Whether the hotbar and the health bars are drawn again, over whatever hides them (config "Keep health and hotbar"). */
    private static boolean keepVitals() {
        Minecraft mc = Minecraft.getInstance();
        if (config == null || !config.keepVitals || mc.player == null
                || ((com.butcher.mixin.HudInvoker) mc.gui.hud).butcher$f1()) return false;
        return shouldHideHud() && hudAlpha() < 1f;
    }

    /** Vanilla's own hotbar and decorations, on a stratum over the cutscene, unfaded. */
    private static void drawVitals(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        graphics.nextStratum();
        ((com.butcher.mixin.HudInvoker) Minecraft.getInstance().gui.hud).butcher$hotbarAndDecorations(graphics, delta);
    }

    /** The last cutscene reported as drawing nothing, so it is reported once. */
    private static net.minecraft.client.gui.screens.Screen silentCutscene;

    /** How long the HUD takes to fade out (SkyMaxx: 0.35 s). */
    private static final float HUD_FADE_SECONDS = 0.35f;
    /** When the HUD started going, or 0 while it is shown. */
    private static long hudHiddenSince = 0;

    /**
     * How much of the vanilla HUD is drawn, 1 to 0: faded out over {@link #HUD_FADE_SECONDS} once
     * {@link #shouldHideHud()} holds, eased - SkyMaxx's {@code hudAlphaWhileOwned}. It comes back
     * at once when the hide ends; the cutscene has already faded out by then.
     */
    public static float hudAlpha() {
        if (!shouldHideHud()) {
            hudHiddenSince = 0;
            return 1f;
        }
        long now = System.nanoTime();
        if (hudHiddenSince == 0) hudHiddenSince = now;
        float t = Math.min(1f, (now - hudHiddenSince) / 1e9f / HUD_FADE_SECONDS);
        return 1f - t * t * (3f - 2f * t);
    }

    /**
     * Whether the chat box must stay shut: a cutscene playing, or the Scatha's pre-kill hide up.
     * Opening chat would show its history, which is where a drop gives itself away.
     */
    public static boolean chatShut() {
        return isAnimationPlaying() || shouldHidePreKill();
    }

    // ------------------------------------------------------------------ Vanguard

    /** One of the game's own textures as an image, through the resource manager; null if it cannot be read. */
    public static java.awt.image.BufferedImage readGameTexture(String path) {
        try (java.io.InputStream in = Minecraft.getInstance().getResourceManager()
                .open(net.minecraft.resources.Identifier.withDefaultNamespace(path))) {
            return javax.imageio.ImageIO.read(in);
        } catch (Exception e) {
            LOGGER.warn("[Butcher] could not read the game's {}", path, e);
            return null;
        }
    }

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
        // Our own lines (the chat summary, test notes) are never evidence of anything: not recorded, not detected.
        if (message.getString().replaceAll("§.", "").startsWith("[Butcher]")) return true;
        if (!overlay) com.butcher.detect.DropRecorder.chatLine(message.getString());
        // Game lines only: a player typing "RARE DROP! Chimera" is not a drop.
        if (!overlay && !player && config != null && config.dianaAnimation) {
            dianaWatcher.onChatLine(message.getString());
        }
        if (!overlay && !player && config != null && config.lavaAnimation) {
            lavaWatcher.onChatLine(message.getString());
        }
        if (!overlay && !player && config != null && seraphOn()) {
            seraphWatcher.onChatLine(message.getString());
        }
        if (!overlay && !player && config != null && config.blazeAnimation) {
            hellionWatcher.onChatLine(message.getString());
        }
        if (!overlay && !player && config != null && config.fishingAnnounce) {
            sharkWatcher.onChatLine(message.getString());
            com.butcher.detect.SharkDrops.Drop shark = com.butcher.detect.SharkDrops.of(message.getString());
            if (shark != null) {
                decisions.note("§7Fishing: " + shark.title + " - \"" + message.getString() + "\"");
                ButcherStats.get().onSource("shark", shark.title);
                com.butcher.render.FinPopup.show(shark);
            }
        }
        if (!overlay && !player && config != null && config.ghostAnnounce) {
            com.butcher.detect.GhostDrops.Drop ghost = com.butcher.detect.GhostDrops.of(message.getString());
            if (ghost != null) {
                decisions.note("§7Ghost: " + ghost.title + " - \"" + message.getString() + "\"");
                ButcherStats.get().onSourceDrop("ghost", ghost.title);
                com.butcher.render.GhostPopup.show(ghost);
            }
        }
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

    /** A Vanguard corpse has been read: count it, record it, and play the Thaw (or queue it, or just announce it). */
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
        if (!testingCorpse) {
            com.google.gson.JsonObject rec = new com.google.gson.JsonObject();
            com.google.gson.JsonArray drops = new com.google.gson.JsonArray();
            for (CorpseHaul.Drop drop : haul.drops()) {
                com.google.gson.JsonObject d = new com.google.gson.JsonObject();
                d.addProperty("name", drop.name());
                d.addProperty("amount", drop.amount());
                d.addProperty("tier", drop.tier().name());
                if (drop.unknown()) d.addProperty("unknown", true);
                drops.add(d);
            }
            rec.add("drops", drops);
            rec.add("chat", com.butcher.detect.DropRecorder.chatSince(10_000));
            rec.add("decisions", com.butcher.detect.DropRecorder.decisions(10));
            com.butcher.detect.DropRecorder.record("vanguard_corpse", rec);
        }

        Minecraft client = Minecraft.getInstance();
        if (config == null || (!config.vanguardAnimation && !testingCorpse)) {
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
        com.butcher.render.CutsceneHud.show(screen);
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
     * /butcher test diana: the cutscene alone. The drop is drawn as its real SkyBlock item (see
     * DianaItems), so a test looks exactly as the real thing will.
     */
    private static void testChop(com.butcher.anim.ChopTimeline.Verdict v) {
        openChop(v, null, true);
    }

    private static int testThaw(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx,
                                VanguardLoot.Tier tier) {
        return testThaw(ctx, tier, null);
    }

    /** As above, but ending on {@code forced} rather than a random item of the tier. */
    private static int testThaw(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx,
                                VanguardLoot.Tier tier, String forced) {
        CorpseHaul haul = endingCorpse(tier, forced);
        ctx.getSource().sendFeedback(Component.literal("§6[Butcher] §7test corpse: §f" + haul.describe()));
        injectTest(haul);
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
     * @param forced the finishing drop, or null for a random one of the tier
     */
    private static CorpseHaul endingCorpse(VanguardLoot.Tier tier, String forced) {
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
        return new CorpseHaul(drops);
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
     *     pre-kill hide has let go - it keys off {@link #isAnimationPlaying()} - but the
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
        // Drawn in the HUD (CutsceneHud), never opened as a screen, so the player keeps
        // control - and so nothing can stomp it either, which is what deferring was for.
        com.butcher.render.CutsceneHud.show(screen);
    }

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

    /** Whether the Seraph's Glyph runs at all: its own switch, and not while it is under maintenance
     *  ({@link Maintenance#SERAPH}) - then nothing of it is detected, hidden or played for players. */
    static boolean seraphOn() {
        PatternConfig c = config;
        return c != null && c.seraphAnimation && !Maintenance.SERAPH;
    }

    /**
     * Whether dropped items go undrawn (mixin/DroppedItemHideMixin): from a slayer boss's pre-kill hide to the end of
     * its cutscene, so its drop on the floor cannot give the reveal away. Asked per item per frame, so it only reads.
     */
    public static boolean hideDroppedItems() {
        PatternConfig c = config;
        if (c == null) return false;
        if (c.blazeAnimation && (hellionActive() || hellionWatcher.armed())) return true;
        return seraphOn() && (seraphActive() || seraphWatcher.armed());
    }

    /** True while the hotbar/chat/held-item should be hidden: a tracked creature is nearly dead, or the Scatha's
     *  drop-detection window is still open, and the real cutscene hasn't started yet. Public so
     *  {@link com.butcher.mixin.ItemInHandRendererMixin} can read it. */
    public static boolean shouldHidePreKill() {
        if (isAnimationPlaying()) return false;
        // Diana: a creature that may be ours is nearly dead (DianaWatcher.armed, SkyMaxx's way).
        if (config.dianaAnimation && dianaWatcher.armed()) return true;
        // Hellion: your Demonlord is nearly dead (HellionWatcher.armed, the same rule).
        if (config.blazeAnimation && hellionWatcher.armed()) return true;
        if (seraphOn() && seraphWatcher.armed()) return true;
        // Eruption: a lava creature of yours is nearly dead.
        if (config.lavaAnimation && lavaWatcher.armed()) return true;
        if (System.currentTimeMillis() < forceHideUntil) return true;
        if (config.animationMode == AnimationMode.NONE) return false;
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

    /** A Scatha run skipped before its drop was known, and when: its drop goes to it, not to a new cutscene. */
    private static ScopeMinigameScreen skippedScope;
    private static long skippedScopeAt;

    private static void trigger(DropResult result, boolean test) {
        Minecraft client = Minecraft.getInstance();
        if (!test && skippedScope != null) {
            ScopeMinigameScreen skipped = skippedScope;
            skippedScope = null;
            if (!skipped.isResolved() && System.currentTimeMillis() - skippedScopeAt < HotbarDropWatcher.KILL_WINDOW_MS + 2_000) {
                skipped.resolveSkipped(result);
                decisions.note("§7Scatha: the skipped run's drop came in - counted, not played");
                if (result != null) {
                    com.google.gson.JsonObject rec = new com.google.gson.JsonObject();
                    rec.addProperty("outcome", result.outcome.name());
                    if (result.rarity != null) rec.addProperty("rarity", result.rarity);
                    rec.addProperty("skipped", true);
                    com.butcher.detect.DropRecorder.record("scatha_kill", rec);
                }
                return;
            }
        }
        if (!test && result != null) {
            com.google.gson.JsonObject rec = new com.google.gson.JsonObject();
            rec.addProperty("outcome", result.outcome.name());
            if (result.rarity != null) rec.addProperty("rarity", result.rarity);
            rec.add("chat", com.butcher.detect.DropRecorder.chatSince(15_000));
            rec.add("decisions", com.butcher.detect.DropRecorder.decisions(25));
            com.butcher.detect.DropRecorder.record("scatha_kill", rec);
        }
        // A cutscene opened by the kill is already on screen waiting to be told what
        // fell - hand it the answer rather than starting a second one over the top.
        // Never a test's: a conjured drop must not become the real kill's outcome.
        if (awaitingDrop()) {
            if (test) return;
            ScopeMinigameScreen waiting = currentScope;
            client.execute(() -> waiting.resolve(result));
            return;
        }
        // A test plays whatever the menu says, even with the cutscene switched off.
        if (test) {
            if (config.animationMode == AnimationMode.GUILLOTINE) animationManager.start(result);
            else openScope(client, result, true, true);
            return;
        }
        switch (config.animationMode) {
            case GUILLOTINE -> animationManager.start(result);
            case SCOPE -> openScope(client, result, true, test);
            case NONE -> { }
        }
    }
}
