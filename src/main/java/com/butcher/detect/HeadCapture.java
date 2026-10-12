package com.butcher.detect;

import com.butcher.ButcherClient;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.HashSet;
import java.util.Set;

/**
 * Captures the skin of a head item we need but cannot find anywhere else - the Paragon Shard is in
 * neither the NEU repo nor Hypixel's item list (2026-10-02). Any open chest-style menu that shows it -
 * the bazaar's page for it is enough, nobody has to own one - gives up its "textures" property, which is
 * written to the drop records as {@code head_capture} (and noted in chat once), to be bundled from there.
 */
public final class HeadCapture {
    private HeadCapture() {}

    /** The heads still wanted, by their item name. */
    private static final Set<String> WANTED = Set.of("Paragon Shard");
    private static final Set<String> captured = new HashSet<>();

    public static void tick(Minecraft client) {
        if (captured.size() == WANTED.size()) return;
        if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) return;
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack == null || stack.isEmpty()) continue;
            String name = stack.getHoverName().getString().replaceAll("§.", "").strip();
            if (!WANTED.contains(name) || captured.contains(name)) continue;
            String texture = texturesProperty(stack);
            if (texture == null) continue;
            captured.add(name);
            JsonObject o = new JsonObject();
            o.addProperty("name", name);
            o.addProperty("texture", texture);
            DropRecorder.record("head_capture", o);
            ButcherClient.decisions.note("§aCaptured the " + name + "'s texture for the animations");
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal("§6[Butcher] §7captured the §f" + name + "§7's texture - thanks!"));
            }
        }
    }

    private static String texturesProperty(ItemStack stack) {
        ResolvableProfile profile = stack.getComponents().get(DataComponents.PROFILE);
        if (profile == null) return null;
        GameProfile game = profile.partialProfile();
        if (game == null) return null;
        for (Property property : game.properties().get("textures")) {
            String value = property.value();
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
