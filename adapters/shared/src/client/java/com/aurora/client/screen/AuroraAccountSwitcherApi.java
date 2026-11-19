package com.aurora.client.screen;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

/**
 * A thin reflective shim for In-Game Account Switcher (IAS, mod id {@code ias},
 * LGPL, by VidTu): Aurora does not depend on it. IAS adds its own button only
 * to vanilla's title and multiplayer screens, so Aurora's title screen gives it
 * an entry point instead: the account card opens IAS's account list, and IAS
 * does all the signing in. Aurora never sees an account or a token (owner
 * 2026-11-12: "similar functionality to IAS ... inside the mod unchanged").
 */
public final class AuroraAccountSwitcherApi {

    private static final String ACCOUNT_SCREEN = "ru.vidtu.ias.screen.AccountScreen";
    /** IAS's multiplayer button is a plain ImageButton carrying this literal message (IAS 9.0.8). */
    private static final String BUTTON_MESSAGE = "In-Game Account Switcher";

    private AuroraAccountSwitcherApi() {}

    /** Whether IAS is installed and its account screen can be found. */
    public static boolean available() {
        if (!FabricLoader.getInstance().isModLoaded("ias")) return false;
        try {
            Class.forName(ACCOUNT_SCREEN, false, AuroraAccountSwitcherApi.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Whether {@code button} is the account button IAS adds to the multiplayer
     * screen, so Aurora can repaint it. Only its look changes: IAS keeps the
     * widget, its position, tooltip and press action.
     */
    public static boolean isMultiplayerButton(net.minecraft.client.gui.components.AbstractButton button) {
        return button.getClass() == net.minecraft.client.gui.components.ImageButton.class
                && BUTTON_MESSAGE.equals(button.getMessage().getString())
                && FabricLoader.getInstance().isModLoaded("ias");
    }

    /** IAS's account list over {@code parent}, or null when IAS is not installed or the screen cannot be built. */
    public static Screen open(Screen parent) {
        if (!FabricLoader.getInstance().isModLoaded("ias")) return null;
        try {
            Class<?> accounts = Class.forName(ACCOUNT_SCREEN);
            return (Screen) accounts.getConstructor(Screen.class).newInstance(parent);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
