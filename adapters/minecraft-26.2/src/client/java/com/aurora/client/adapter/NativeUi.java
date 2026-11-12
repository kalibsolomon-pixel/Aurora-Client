package com.aurora.client.adapter;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Version-owned GUI access; shared screens keep one behavior implementation. */
public final class NativeUi {
    private NativeUi() {}
    public static Screen screen(Minecraft client) { return client.gui.screen(); }
    public static void setScreen(Minecraft client, Screen screen) { client.gui.setScreen(screen); }
    public static net.minecraft.client.gui.screens.Overlay overlay(Minecraft client) { return client.gui.overlay(); }
    public static void setOverlay(Minecraft client, net.minecraft.client.gui.screens.Overlay overlay) { client.gui.setOverlay(overlay); }
    /** Minecraft's options screen over {@code parent} (26.1 and 26.2 take an in-game flag; 26.3 does not). */
    public static Screen optionsScreen(Minecraft client, Screen parent) { return new net.minecraft.client.gui.screens.options.OptionsScreen(parent, client.options, false); }
    public static RenderTarget mainTarget(Minecraft client) { return client.gameRenderer.mainRenderTarget(); }
    /** Opens a folder or file in the system's file manager. */
    public static void openPath(java.nio.file.Path path) { net.minecraft.util.Util.getPlatform().openPath(path); }
}
