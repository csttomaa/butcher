package com.butcher.render;

import com.butcher.ButcherClient;
import com.butcher.detect.vanguard.CorpseHaul;
import com.butcher.render.art.PropArt;
import com.butcher.render.model.CrystalMesh;
import com.butcher.render.model.Mesh;
import com.butcher.render.model.ModelProjection;
import com.butcher.render.model.PlayerHeadMesh;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws the things that come out of a Vanguard corpse, as the game itself would.
 *
 * <h2>Two kinds of item</h2>
 *
 * Twenty-one of the twenty-six drops are player heads, and are drawn as real 3D heads with
 * their real SkyBlock skins - the same {@link PlayerHeadMesh} the Scatha's pet head turns
 * on, so an item spinning out of the ice looks like the one in the player's inventory and
 * not like an icon of it. Their skins are the base64 texture blobs from the NEU item
 * repository, bundled in {@code assets/butcher/vanguard_heads.json} - nothing is guessed
 * and nothing is downloaded from anywhere the game would not already fetch it from.
 *
 * The other five are vanilla items in SkyBlock clothing - a tripwire hook is the Skeleton
 * Key - and go through the game's own item renderer, so they look exactly as they do in a
 * chest. Which vanilla item each one is came from the same repository's {@code ItemModel}.
 *
 * <h2>Never Steve</h2>
 *
 * A skin lookup returns the default skin until its download finishes (see ScathaSkins for
 * the whole saga). A frozen corpse spitting out a row of Steve heads would be the worst
 * possible look, so an unfinished lookup is drawn as a crystal in the item's own colour
 * instead, and the real head takes over on the first frame it is ready. {@link #prewarm}
 * starts every lookup when the player first joins a world, and the skins are in the local
 * cache anyway, so in practice the crystal is only ever seen if something is wrong. The
 * skins are loaded by {@link HeadSkins}, shared with Diana's drops.
 */
public final class VanguardItems {
    private VanguardItems() {}

    private static final Mesh HEAD = PlayerHeadMesh.head();
    /** Head plus hat layer, edge to edge, in model units. */
    private static final float HEAD_SPAN = PlayerHeadMesh.HEAD + 2 * PlayerHeadMesh.HAT_INFLATE;
    /**
     * How much wider a head is turned three-quarters than seen face-on: about cos32 + sin32.
     *
     * Heads used to be sized as if face-on, so at the resting angle they came out a third
     * wider than asked and spilled over the edges of their tray slots - which made the whole
     * tray look lopsided. Dividing by this makes {@code size} the width actually drawn.
     */
    private static final float TURNED = 1.4f;

    private static final Mesh CRYSTAL = CrystalMesh.cluster(3, 12f, 4401L);

    /**
     * The resting three-quarter view: the item's front face and one side.
     *
     * 32, not 212. PlayerHeadMesh's own note says its face points at -Z and the camera sits
     * at +Z, which reads as though yaw 0 should show the back - but rendering real item
     * skins at 0, 32 and 212 settled it: at 0 the Caged Wisp shows its face, at 212 its bars.
     * Rendered, not reasoned, the same way the head's UVs were settled.
     */
    public static final float REST_YAW = 32f;
    private static final float PITCH = -22f;

    // ------------------------------------------------------------------ heads

    /**
     * Starts every head's skin lookup - the Vanguard's and Diana's. Cheap, idempotent; call
     * once there is a player. The skins themselves are {@link HeadSkins}.
     */
    public static void prewarm() {
        HeadSkins.VANGUARD.prewarm();
        HeadSkins.DIANA.prewarm();
        HeadSkins.CREATURES.prewarm();
    }

    private static String headTexture(String name) {
        return HeadSkins.VANGUARD.texture(name);
    }

    // ------------------------------------------------------------------ vanilla items

    private static final Map<String, ItemStack> VANILLA = new HashMap<>();

    private static ItemStack vanilla(String name) {
        if (VANILLA.isEmpty()) {
            VANILLA.put("Skeleton Key", new ItemStack(Items.TRIPWIRE_HOOK));
            VANILLA.put("Umber Key", glinting(new ItemStack(Items.DEAD_BUSH)));
            VANILLA.put("Tungsten Key", glinting(new ItemStack(Items.LEVER)));
            VANILLA.put("Ice Cold I", new ItemStack(Items.ENCHANTED_BOOK));
            VANILLA.put("Blue Goblin Egg", new ItemStack(Items.GLOW_SQUID_SPAWN_EGG));
        }
        return VANILLA.get(name);
    }

    // ------------------------------------------------------------------ flat sprites

    /**
     * Glacite Powder's icon: the old light blue dye, the heap of powder the game used before
     * 1.14, which is what the user wanted it to look like.
     *
     * Mojang's own sprite is not shipped in the jar - handing out a mod with the game's art in
     * it is not ours to do. What ships is a heap of powder painted by
     * {@link com.butcher.render.art.ThawArt#glacitePowder}; the real one can be dropped into
     * {@code config/butcher/textures/glacite_powder.png}, which {@link ArtOverrides} prefers.
     */
    public static final String TEX_GLACITE_POWDER = "glacite_powder";

    private static String sprite(String name) {
        return "Glacite Powder".equals(name) ? TEX_GLACITE_POWDER : null;
    }

    private static ItemStack glinting(ItemStack stack) {
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draws one drop centred on {@code (cx, cy)}, about {@code size} pixels across.
     *
     * @param yawDeg  which way a head is turned - {@link #REST_YAW} to sit still
     * @param alpha   0..255; heads and the crystal fade, vanilla items simply stop drawing
     *                below half, since the item renderer takes no alpha
     * @param outline whether to stamp the black silhouette behind a head, as the rest of
     *                the cutscene's models have
     */
    public static void draw(GuiGraphicsExtractor g, MeshRenderer meshes, CorpseHaul.Drop drop,
                            float cx, float cy, float size, float yawDeg, int alpha, boolean outline) {
        if (alpha <= 3 || size < 2f) return;
        String name = drop.name();

        String sprite = sprite(name);
        if (sprite != null) {
            Identifier id = TextureBank.id(sprite);
            if (id != null) {
                // Flat, as an item icon is, and faded like the heads rather than cut off.
                int w = Math.max(1, TextureBank.width(sprite)), h = Math.max(1, TextureBank.height(sprite));
                Matrix3x2fStack pose = g.pose();
                pose.pushMatrix();
                pose.translate(cx - size / 2f, cy - size / 2f);
                pose.scale(size / w, size / h);
                g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, w, h, w, h, (alpha << 24) | 0xFFFFFF);
                pose.popMatrix();
                return;
            }
        }

        ItemStack stack = vanilla(name);
        if (stack != null) {
            if (alpha < 128) return;
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            float s = size / 16f;
            pose.translate(cx - 8f * s, cy - 8f * s);
            pose.scale(s, s);
            g.item(stack, 0, 0);
            pose.popMatrix();
            return;
        }

        int tint = (alpha << 24) | 0xFFFFFF;
        float outlineWidth = outline ? Math.max(1f, size / 26f) : 0f;
        String head = headTexture(name);
        meshes.begin();
        if (head != null) {
            meshes.camera(ModelProjection.Camera.of(yawDeg, PITCH, size / (HEAD_SPAN * TURNED)));
            meshes.add(HEAD, head, 0f, 0f, 0f, 0f, tint, true, false, true);
        } else {
            // Not a head we know, or its skin is still on the way: a crystal in the item's
            // own colour, which reads as "something valuable" without pretending to be it.
            int c = drop.entry().colour();
            meshes.camera(ModelProjection.Camera.of(yawDeg, PITCH, size / 14f));
            // The cluster grows up from y=0; drop it half its height so it sits centred.
            meshes.add(CRYSTAL, PropArt.TEX_CRYSTAL, 0f, 0f, -6f, 0f,
                    (alpha << 24) | (c & 0xFFFFFF), true, false, true);
        }
        meshes.flush(g, cx, cy, (alpha << 24), outlineWidth);
    }

    /** Whether a drop is drawn as a head at all - the tray uses it to leave room for the hat. */
    public static boolean isHead(CorpseHaul.Drop drop) {
        return vanilla(drop.name()) == null && sprite(drop.name()) == null;
    }
}
