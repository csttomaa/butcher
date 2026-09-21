package com.butcher.anim;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.butcher.ButcherClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * The pet-drop popup: the Scatha head sweeping up into frame and spinning.
 *
 * This is the totem of undying animation. Minecraft already ships it - it is what
 * plays when a totem saves your life - and it accepts any ItemStack, so handing it
 * a PLAYER_HEAD wearing the Scatha skin produces exactly the popup Scatha-Pro
 * shows. Theirs turned out to be a copy of this same vanilla animation with a few
 * knobs added (adjustable length, an alternative rotation curve, an angled pose);
 * the motion, the 40-tick length and even the random screen offset are vanilla's,
 * unchanged.
 *
 * Which means there is no renderer in this class and no mixin behind it - just one
 * call into the game's own code. That replaced a full port of their custom
 * world-space renderer: a pose stack, a projection matrix buffer, a submit-node
 * collector, a feature dispatcher, manual depth clearing and a GameRenderer mixin
 * to drive it all. None of that was ever needed.
 *
 * The head is a real 3D model with the real skin, so it stays crisp at any size -
 * Minecraft fetches and caches the texture itself.
 */
public final class PetHeadPopup {
    private PetHeadPopup() {}

    /** The Scatha head skin, lifted from Scatha-Pro's Constants.generateScathaPetItem. */
    private static final String SKIN_BASE64 =
            "ewogICJ0aW1lc3RhbXAiIDogMTYyMDQ0NTc2NDQ1MSwKICAicHJvZmlsZUlkIiA6ICJmNDY0NTcxNDNkMTU0ZmEwOTkxNjBlNGJmNzI3ZGNiOSIsCiAgInByb2ZpbGVOYW1lIiA6ICJSZWxhcGFnbzA1IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2RmMDNhZDk2MDkyZjNmNzg5OTAyNDM2NzA5Y2RmNjlkZTZiNzI3YzEyMWIzYzJkYWVmOWZmYTFjY2FlZDE4NmMiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==";

    /** Play the popup, if it's switched on. Vanilla runs it for 40 ticks (2 seconds). */
    public static void showIfEnabled() {
        if (ButcherClient.config == null || !ButcherClient.config.petHeadPopup) return;
        ItemStack head = buildScathaHead();
        if (head == null) return;
        Minecraft.getInstance().gameRenderer.displayItemActivation(head);
    }

    private static ItemStack buildScathaHead() {
        try {
            ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
            GameProfile profile = new GameProfile(Util.NIL_UUID, "",
                    new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", SKIN_BASE64))));
            stack.applyComponents(DataComponentPatch.builder()
                    .set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile))
                    .build());
            return stack;
        } catch (RuntimeException e) {
            // A head we can't build is not worth killing a drop cutscene over.
            return null;
        }
    }
}
