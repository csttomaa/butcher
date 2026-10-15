package com.butcher;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.util.Set;
import java.util.UUID;

/**
 * Who may use the developer commands: {@code /butcher test} and {@code /butcher debug}.
 *
 * Everyone else gets the game's own "Unknown or incomplete command" for them, and they are left out
 * of tab completion, as if they did not exist. Players preview the cutscenes with the menu's "Try it"
 * rows instead, which play the same thing.
 *
 * This is a tidy-up, not security: it is a client mod, and anyone who rebuilds the jar can take it out.
 * Account UUIDs rather than names, because a name can change and a UUID never does. A development
 * run ({@code gradlew runClient}) is always allowed.
 */
public final class Dev {
    private Dev() {}

    /** Minecraft account UUIDs allowed the developer commands, with dashes. */
    private static final Set<UUID> WHITELIST = Set.of(
            // UUID.fromString("00000000-0000-0000-0000-000000000000"),   // add the developers' accounts here
    );

    /** Whether the player running the game is allowed the developer commands. */
    public static boolean allowed() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        UUID id = mc.player.getGameProfile().id();
        return id != null && WHITELIST.contains(id);
    }
}
