package com.aurora.client.screen;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

/**
 * Mod Menu integration is disabled for the 1.21.11 port: as of port date the
 * only published modmenu artifact is a beta we have not adopted. This class
 * stays as a thin reflective shim so the title screen's "Mods" button still
 * works if/when a compatible modmenu lands in the runtime classpath.
 */
public final class AuroraModMenuApi {

    private AuroraModMenuApi() {}

    /**
     * Called by {@link AuroraTitleScreen}'s "Mods" button.
     */
    public static Screen openOrFallback(Screen parent) {
        if (FabricLoader.getInstance().isModLoaded("modmenu")) {
            try {
                Class<?> modsScreen = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
                return (Screen) modsScreen.getConstructor(Screen.class).newInstance(parent);
            } catch (ReflectiveOperationException e) {
                // Fall through to Aurora settings screen.
            }
        }
        return AuroraScreen.create();
    }
}