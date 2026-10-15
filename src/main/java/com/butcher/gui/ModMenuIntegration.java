package com.butcher.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu's "Configure" button opens the Butcher menu, and Esc goes back to Mod Menu's list.
 *
 * Only ever loaded by Mod Menu, through the "modmenu" entrypoint in fabric.mod.json: without Mod Menu installed
 * nothing touches this class, so Mod Menu is optional.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ButcherMenuScreen::new;
    }
}
