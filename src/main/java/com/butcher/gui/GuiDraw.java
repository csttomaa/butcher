package com.butcher.gui;

import com.butcher.gui.menu.Draw;
import com.butcher.render.TextureBank;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link Draw} over the game's GUI: what the menu and the Ledger's stamp pop-up draw through in game. Set {@link #g}
 * for the frame before drawing (and clear it after).
 */
public final class GuiDraw implements Draw {

    /** The mod's icon, scaled once (ButcherClient registers it). */
    public static final String TEX_ICON = "menu_icon";

    public GuiGraphicsExtractor g;
    private final Font font;
    private final Map<String, ItemStack> items = new HashMap<>();

    public GuiDraw(Font font) {
        this.font = font;
    }

    @Override public void fill(int x0, int y0, int x1, int y1, int argb) { g.fill(x0, y0, x1, y1, argb); }

    @Override
    public void text(String s, int x, int y, int argb, boolean bold, boolean shadow) {
        g.text(font, component(s, bold), x, y, argb, shadow);
    }

    @Override public int width(String s, boolean bold) { return font.width(component(s, bold)); }

    @Override public void item(String id, int x, int y) { g.item(stack(id), x, y); }

    @Override
    public void modIcon(int x, int y, int size) {
        Identifier id = TextureBank.id(TEX_ICON);
        if (id == null) return;
        int tw = TextureBank.width(TEX_ICON), th = TextureBank.height(TEX_ICON);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / (float) tw, size / (float) th);
        g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, tw, th, tw, th, 0xFFFFFFFF);
        g.pose().popMatrix();
    }

    @Override public void clip(int x0, int y0, int x1, int y1) { g.enableScissor(x0, y0, x1, y1); }
    @Override public void endClip() { g.disableScissor(); }
    @Override public void push() { g.pose().pushMatrix(); }
    @Override public void translate(float x, float y) { g.pose().translate(x, y); }
    @Override public void scale(float s) { g.pose().scale(s, s); }
    @Override public void scale(float sx, float sy) { g.pose().scale(sx, sy); }
    @Override public void rotate(float radians) { g.pose().rotate(radians); }
    @Override public void pop() { g.pose().popMatrix(); }

    private static Component component(String s, boolean bold) {
        return bold ? Component.literal(s).withStyle(ChatFormatting.BOLD) : Component.literal(s);
    }

    /** The cards', tabs' and stamps' icons: vanilla items by registry path, or a SkyBlock head ("head:set:name"). */
    private ItemStack stack(String id) {
        return items.computeIfAbsent(id, k -> k.startsWith("head:") ? head(k) : vanilla(k));
    }

    /**
     * A SkyBlock item that is a player head, as the game draws it in an inventory: a PLAYER_HEAD with the item's
     * real skin from a bundled NEU blob ({@code head:diana:Brain Food}; {@code head:scatha:pet} is the Scatha pet).
     * The game fetches the skin itself. Anything unknown draws as paper.
     */
    private static ItemStack head(String id) {
        String[] p = id.split(":", 3);
        String blob = null;
        if (p.length == 3) {
            if (p[1].equals("scatha")) {
                blob = com.butcher.anim.PetHeadPopup.SKIN_BASE64;
            } else {
                com.butcher.render.HeadSkins set = com.butcher.render.HeadSkins.named(p[1]);
                if (set != null) blob = set.blob(p[2]);
            }
        }
        if (blob == null) return new ItemStack(Items.PAPER);
        try {
            ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
            com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(
                    java.util.UUID.nameUUIDFromBytes(blob.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "ButcherItem",
                    new com.mojang.authlib.properties.PropertyMap(com.google.common.collect.ImmutableMultimap.of(
                            "textures", new com.mojang.authlib.properties.Property("textures", blob))));
            stack.applyComponents(net.minecraft.core.component.DataComponentPatch.builder()
                    .set(net.minecraft.core.component.DataComponents.PROFILE,
                            net.minecraft.world.item.component.ResolvableProfile.createResolved(profile))
                    .build());
            return stack;
        } catch (RuntimeException e) {
            return new ItemStack(Items.PAPER);
        }
    }

    private static ItemStack vanilla(String k) {
        return new ItemStack(switch (k) {
            case "amethyst_shard" -> Items.AMETHYST_SHARD;
            case "blue_ice" -> Items.BLUE_ICE;
            case "golden_shovel" -> Items.GOLDEN_SHOVEL;
            case "tropical_fish" -> Items.TROPICAL_FISH;
            case "ghast_tear" -> Items.GHAST_TEAR;
            case "magma_cream" -> Items.MAGMA_CREAM;
            case "diamond_sword" -> Items.DIAMOND_SWORD;
            case "blaze_rod" -> Items.BLAZE_ROD;
            case "ender_pearl" -> Items.ENDER_PEARL;
            case "gold_nugget" -> Items.GOLD_NUGGET;
            case "anvil" -> Items.ANVIL;
            case "barrel" -> Items.BARREL;
            case "beef" -> Items.BEEF;
            case "bell" -> Items.BELL;
            case "bone" -> Items.BONE;
            case "bone_meal" -> Items.BONE_MEAL;
            case "bread" -> Items.BREAD;
            case "cake" -> Items.CAKE;
            case "chest" -> Items.CHEST;
            case "clock" -> Items.CLOCK;
            case "coal" -> Items.COAL;
            case "cod" -> Items.COD;
            case "compass" -> Items.COMPASS;
            case "cooked_beef" -> Items.COOKED_BEEF;
            case "cookie" -> Items.COOKIE;
            case "diamond" -> Items.DIAMOND;
            case "echo_shard" -> Items.ECHO_SHARD;
            case "egg" -> Items.EGG;
            case "emerald" -> Items.EMERALD;
            case "enchanted_book" -> Items.ENCHANTED_BOOK;
            case "ender_eye" -> Items.ENDER_EYE;
            case "experience_bottle" -> Items.EXPERIENCE_BOTTLE;
            case "feather" -> Items.FEATHER;
            case "fire_charge" -> Items.FIRE_CHARGE;
            case "fishing_rod" -> Items.FISHING_ROD;
            case "flint_and_steel" -> Items.FLINT_AND_STEEL;
            case "glass_bottle" -> Items.GLASS_BOTTLE;
            case "glowstone_dust" -> Items.GLOWSTONE_DUST;
            case "gold_block" -> Items.GOLD_BLOCK;
            case "gold_ingot" -> Items.GOLD_INGOT;
            case "golden_helmet" -> Items.GOLDEN_HELMET;
            case "golden_sword" -> Items.GOLDEN_SWORD;
            case "green_wool" -> Items.WOOL.pick(net.minecraft.world.item.DyeColor.GREEN);
            case "gunpowder" -> Items.GUNPOWDER;
            case "heart_of_the_sea" -> Items.HEART_OF_THE_SEA;
            case "honey_bottle" -> Items.HONEY_BOTTLE;
            case "ice" -> Items.ICE;
            case "iron_block" -> Items.IRON_BLOCK;
            case "item_frame" -> Items.ITEM_FRAME;
            case "jack_o_lantern" -> Items.JACK_O_LANTERN;
            case "kelp" -> Items.KELP;
            case "lantern" -> Items.LANTERN;
            case "lapis_lazuli" -> Items.LAPIS_LAZULI;
            case "lava_bucket" -> Items.LAVA_BUCKET;
            case "lead" -> Items.LEAD;
            case "end_rod" -> Items.END_ROD;
            case "magma_block" -> Items.MAGMA_BLOCK;
            case "map" -> Items.MAP;
            case "name_tag" -> Items.NAME_TAG;
            case "nautilus_shell" -> Items.NAUTILUS_SHELL;
            case "nether_star" -> Items.NETHER_STAR;
            case "oak_boat" -> Items.OAK_BOAT;
            case "packed_ice" -> Items.PACKED_ICE;
            case "phantom_membrane" -> Items.PHANTOM_MEMBRANE;
            case "powder_snow_bucket" -> Items.POWDER_SNOW_BUCKET;
            case "prismarine_crystals" -> Items.PRISMARINE_CRYSTALS;
            case "prismarine_shard" -> Items.PRISMARINE_SHARD;
            case "pufferfish" -> Items.PUFFERFISH;
            case "rabbit_foot" -> Items.RABBIT_FOOT;
            case "red_wool" -> Items.WOOL.pick(net.minecraft.world.item.DyeColor.RED);
            case "redstone" -> Items.REDSTONE;
            case "rotten_flesh" -> Items.ROTTEN_FLESH;
            case "salmon" -> Items.SALMON;
            case "shield" -> Items.SHIELD;
            case "skeleton_skull" -> Items.SKELETON_SKULL;
            case "slime_ball" -> Items.SLIME_BALL;
            case "snowball" -> Items.SNOWBALL;
            case "soul_lantern" -> Items.SOUL_LANTERN;
            case "soul_torch" -> Items.SOUL_TORCH;
            case "spider_eye" -> Items.SPIDER_EYE;
            case "totem_of_undying" -> Items.TOTEM_OF_UNDYING;
            case "tripwire_hook" -> Items.TRIPWIRE_HOOK;
            case "turtle_helmet" -> Items.TURTLE_HELMET;
            case "water_bucket" -> Items.WATER_BUCKET;
            case "wheat" -> Items.WHEAT;
            case "writable_book" -> Items.WRITABLE_BOOK;
            case "yellow_wool" -> Items.WOOL.pick(net.minecraft.world.item.DyeColor.YELLOW);
            case "baked_potato" -> Items.BAKED_POTATO;
            case "barrier" -> Items.BARRIER;
            case "bowl" -> Items.BOWL;
            case "carved_pumpkin" -> Items.CARVED_PUMPKIN;
            case "cobweb" -> Items.COBWEB;
            case "cooked_porkchop" -> Items.COOKED_PORKCHOP;
            case "fermented_spider_eye" -> Items.FERMENTED_SPIDER_EYE;
            case "golden_apple" -> Items.GOLDEN_APPLE;
            case "lily_pad" -> Items.LILY_PAD;
            case "snow_block" -> Items.SNOW_BLOCK;
            case "spyglass" -> Items.SPYGLASS;
            case "stick" -> Items.STICK;
            case "trident" -> Items.TRIDENT;
            case "wither_skeleton_skull" -> Items.WITHER_SKELETON_SKULL;
            default -> Items.PAPER;
        });
    }
}
