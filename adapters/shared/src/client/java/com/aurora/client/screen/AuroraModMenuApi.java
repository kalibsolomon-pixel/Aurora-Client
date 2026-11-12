package com.aurora.client.screen;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

/**
 * A thin reflective shim for Mod Menu: Aurora does not depend on it, and the
 * title screen's Mods button opens its list when it is installed. Without it
 * the button is disabled and says so (owner 2026-11-12: "Mods should open up
 * ModMenu"; Aurora has its own button).
 */
public final class AuroraModMenuApi {

    private static final String MODS_SCREEN = "com.terraformersmc.modmenu.gui.ModsScreen";

    private AuroraModMenuApi() {}

    /** Whether Mod Menu is installed and its list screen can be found. */
    public static boolean available() {
        if (!FabricLoader.getInstance().isModLoaded("modmenu")) return false;
        try {
            Class.forName(MODS_SCREEN, false, AuroraModMenuApi.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Mod Menu's list over {@code parent}, or null when it is not installed or cannot be built. */
    public static Screen open(Screen parent) {
        if (!FabricLoader.getInstance().isModLoaded("modmenu")) return null;
        try {
            Class<?> modsScreen = Class.forName(MODS_SCREEN);
            return (Screen) modsScreen.getConstructor(Screen.class).newInstance(parent);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /** Mod Menu's list, or Aurora's own screen when Mod Menu is absent (tools/gui's audit). */
    public static Screen openOrFallback(Screen parent) {
        Screen mods = open(parent);
        return mods != null ? mods : AuroraScreen.create();
    }
}
