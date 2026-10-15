package com.terraformersmc.modmenu.api;

/**
 * A compile-time copy of Mod Menu's API, the one method Butcher uses - so the build needs neither Mod Menu nor its
 * maven. {@code build.gradle} leaves the whole {@code com/terraformersmc} package out of the jar: in game the real
 * interface is Mod Menu's own, and without Mod Menu nothing loads it.
 */
public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return screen -> null;
    }
}
