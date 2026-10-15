package com.terraformersmc.modmenu.api;

import net.minecraft.client.gui.screens.Screen;

/** A compile-time copy of Mod Menu's interface, never packaged - see {@link ModMenuApi}. */
@FunctionalInterface
public interface ConfigScreenFactory<S extends Screen> {
    S create(Screen parent);
}
