package com.scathabutcher.anim;

import com.scathabutcher.ScathaButcherClient;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Sets off a real in-world firework when a Scatha pet drops.
 *
 * This is Scatha-Pro's exact recipe, read off their compiled ScathaPetDrop class:
 * a single SMALL_BALL explosion one block in front of the player's eyes, with no
 * velocity, no trail, twinkle on, and one colour taken from the pet's rarity.
 * It's a genuine client-side firework in the world, not an overlay drawn on the
 * HUD - which is why it still looks right in a screenshot.
 *
 * One deliberate difference in timing. Scatha-Pro sets it off the moment the drop
 * registers, because their pet popup is an overlay drawn over the live world, so
 * you see the burst going off behind it. Our cutscenes paint a full-screen cavern
 * over everything, so a firework lit at that moment would be completely hidden and
 * burnt out by the time the screen cleared. We fire it as the cutscene closes
 * instead, so the burst is there in the world the instant you're looking at it
 * again.
 */
public final class PetDropFirework {
    private PetDropFirework() {}

    public static void burst(String rarity) {
        if (ScathaButcherClient.config == null || !ScathaButcherClient.config.petDropFirework) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.level() == null) return;

        Vec3 at = player.getEyePosition().add(player.getForward().scale(1.0));
        player.level().createFireworks(at.x, at.y, at.z, 0.0, 0.0, 0.0,
                List.of(new FireworkExplosion(
                        FireworkExplosion.Shape.SMALL_BALL,
                        IntList.of(rarityColor(rarity)),   // one colour: the pet's rarity
                        IntList.of(),                      // no fade colours
                        false,                             // no trail
                        true)));                           // twinkle
    }

    /** Matches the rarity colours the reveal text already uses, so the burst reads as the same rarity. */
    private static int rarityColor(String rarity) {
        return switch (rarity == null ? "" : rarity) {
            case "legendary" -> 0xFFAA00;   // gold
            case "epic" -> 0xFF55FF;        // light purple
            case "rare" -> 0x5555FF;        // blue
            default -> 0xFFFFFF;
        };
    }
}
