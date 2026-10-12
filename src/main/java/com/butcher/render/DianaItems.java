package com.butcher.render;

import com.butcher.anim.ChopTimeline.Verdict;
import com.butcher.render.art.PropArt;
import com.butcher.render.model.CrystalMesh;
import com.butcher.render.model.Mesh;
import com.butcher.render.model.ModelProjection;
import com.butcher.render.model.PlayerHeadMesh;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

/**
 * Diana's drops, drawn as the game draws them - the way {@link VanguardItems} does the corpse's.
 *
 * Which item each one really is came from the NEU item repository: five are player heads with
 * their own SkyBlock skins (bundled as blobs in {@code assets/butcher/diana_heads.json}, loaded
 * by {@link HeadSkins}) and drawn as real 3D heads that can turn; three are vanilla items -
 * Chimera I is an enchanted book, the Crown of Greed a golden helmet, Shimmering Wool a block of
 * yellow wool - drawn by the game's own item renderer.
 */
public final class DianaItems {
    private DianaItems() {}

    private static final Mesh HEAD = PlayerHeadMesh.head();
    private static final float HEAD_SPAN = PlayerHeadMesh.HEAD + 2 * PlayerHeadMesh.HAT_INFLATE;
    private static final Mesh CRYSTAL = CrystalMesh.cluster(3, 12f, 4401L);
    private static final float PITCH = -22f;

    /** The item's own name, as SkyBlock shows it; empty for a bust or an unknown drop. */
    public static String name(Verdict v) {
        return switch (v) {
            case RELIC -> "Minos Relic";
            case BRAIN_FOOD -> "Brain Food";
            case CHIMERA -> "Chimera I";
            case DYE -> "Mythological Dye";
            case FATEFUL_STINGER -> "Fateful Stinger";
            case MANTI_CORE -> "Manti-core";
            case CROWN_OF_GREED -> "Crown of Greed";
            case SHIMMERING_WOOL -> "Shimmering Wool";
            default -> "";
        };
    }

    /** The SkyBlock item ID, for prices - the bazaar's product ID where it is a bazaar item. */
    public static String id(Verdict v) {
        return switch (v) {
            case RELIC -> "MINOS_RELIC";
            case BRAIN_FOOD -> "BRAIN_FOOD";
            case CHIMERA -> "ENCHANTMENT_ULTIMATE_CHIMERA_1";
            case DYE -> "DYE_MYTHOLOGICAL";
            case FATEFUL_STINGER -> "FATEFUL_STINGER";
            case MANTI_CORE -> "MANTI_CORE";
            case CROWN_OF_GREED -> "CROWN_OF_GREED";
            case SHIMMERING_WOOL -> "SHIMMERING_WOOL";
            default -> null;
        };
    }

    /**
     * What an NPC pays, for when no live price has come back; -1 where the wiki gives none.
     * Checked 2026-09-28: Brain Food, Chimera I and Fateful Stinger are on the bazaar, the rest
     * are auction-only.
     */
    public static long npcPrice(Verdict v) {
        return switch (v) {
            case RELIC -> 5_000_000L;
            case BRAIN_FOOD -> 1_000_000L;
            default -> -1L;
        };
    }

    public static Component title(Verdict v) {
        return Component.literal(name(v));
    }

    /** The vanilla item it is in SkyBlock, or null for the heads. */
    public static ItemStack vanilla(Verdict v) {
        return switch (v) {
            case CHIMERA -> new ItemStack(Items.ENCHANTED_BOOK);
            case CROWN_OF_GREED -> new ItemStack(Items.GOLDEN_HELMET);
            case SHIMMERING_WOOL -> new ItemStack(Items.WOOL.pick(DyeColor.YELLOW));
            default -> null;
        };
    }

    /** The vanilla icon of a flat drop: Chimera's enchanted book, the Crown's golden helmet. */
    private static Identifier flatTexture(Verdict v) {
        return switch (v) {
            case CHIMERA -> Identifier.withDefaultNamespace("textures/item/enchanted_book.png");
            case CROWN_OF_GREED -> Identifier.withDefaultNamespace("textures/item/golden_helmet.png");
            default -> null;
        };
    }

    private static final String WOOL_TEX = "diana_yellow_wool";
    /**
     * A 16-unit cube, every face the whole wool texture. Adopted textures are taken as 64 wide
     * (TextureBank), so the rect is 64 - which is the whole of the real 16px texture.
     */
    private static final Mesh WOOL = new com.butcher.render.model.BoxMesh(64, 64)
            .boxFlat(-8f, -8f, -8f, 16f, 16f, 16f, 0, 0, 64, 64).build();
    private static final float[][] INK = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-0.71f, -0.71f}, {0.71f, -0.71f}, {-0.71f, 0.71f}, {0.71f, 0.71f}};

    public static boolean isHead(Verdict v) {
        return HeadSkins.DIANA.has(name(v));
    }

    /**
     * Draws the drop centred on {@code (cx, cy)}, about {@code size} pixels across, turned to
     * {@code yawDeg} if it is a head.
     *
     * @param colour the drop's colour, for the crystal shown while a skin is still downloading
     */
    public static void draw(GuiGraphicsExtractor g, MeshRenderer meshes, Verdict v,
                            float cx, float cy, float size, float yawDeg, int alpha, int colour) {
        if (alpha <= 3 || size < 2f) return;
        if (v == Verdict.SHIMMERING_WOOL) {
            // A real block, turning, outlined like the heads.
            TextureBank.adopt(WOOL_TEX, Identifier.withDefaultNamespace("textures/block/yellow_wool.png"));
            meshes.begin();
            meshes.camera(ModelProjection.Camera.of(yawDeg, PITCH, size / (16f * 1.45f)));
            meshes.add(WOOL, WOOL_TEX, 0f, 0f, 0f, 0f, (alpha << 24) | 0xFFFFFF, true, false, true);
            meshes.flush(g, cx, cy, alpha << 24, Math.max(1.5f, size / 16f));
            return;
        }
        Identifier flat = flatTexture(v);
        if (flat != null) {
            // The item's own 16px icon, blown up, with a black outline stamped round it first -
            // the item renderer cannot be tinted, so it cannot give one.
            float s = size / 16f, ink = Math.max(1.5f, size / 16f);
            int x = Math.round(cx - size / 2f), y = Math.round(cy - size / 2f);
            Matrix3x2fStack pose = g.pose();
            for (float[] o : INK) {
                pose.pushMatrix();
                pose.translate(x + o[0] * ink, y + o[1] * ink);
                pose.scale(s, s);
                g.blit(RenderPipelines.GUI_TEXTURED, flat, 0, 0, 0f, 0f, 16, 16, 16, 16, alpha << 24);
                pose.popMatrix();
            }
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(s, s);
            g.blit(RenderPipelines.GUI_TEXTURED, flat, 0, 0, 0f, 0f, 16, 16, 16, 16, (alpha << 24) | 0xFFFFFF);
            pose.popMatrix();
            return;
        }
        int tint = (alpha << 24) | 0xFFFFFF;
        String head = HeadSkins.DIANA.texture(name(v));
        meshes.begin();
        if (head != null) {
            meshes.camera(ModelProjection.Camera.of(yawDeg, PITCH, size / (HEAD_SPAN * 1.4f)));
            meshes.add(HEAD, head, 0f, 0f, 0f, 0f, tint, true, false, true);
        } else {
            meshes.camera(ModelProjection.Camera.of(yawDeg, PITCH, size / 14f));
            meshes.add(CRYSTAL, PropArt.TEX_CRYSTAL, 0f, 0f, -6f, 0f, (alpha << 24) | (colour & 0xFFFFFF), true, false, true);
        }
        // The thick outline the Thaw's items have, a little heavier at this size.
        meshes.flush(g, cx, cy, alpha << 24, Math.max(1.5f, size / 16f));
    }
}
