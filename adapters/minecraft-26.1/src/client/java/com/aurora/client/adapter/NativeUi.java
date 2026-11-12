package com.aurora.client.adapter;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Version-owned GUI access; shared screens keep one behavior implementation. */
public final class NativeUi {
    private NativeUi() {}
    public static Screen screen(Minecraft client) { return client.screen; }
    public static void setScreen(Minecraft client, Screen screen) { client.setScreen(screen); }
    /** Minecraft's options screen over {@code parent} (26.1 and 26.2 take an in-game flag; 26.3 does not). */
    public static Screen optionsScreen(Minecraft client, Screen parent) { return new net.minecraft.client.gui.screens.options.OptionsScreen(parent, client.options, false); }
    public static RenderTarget mainTarget(Minecraft client) { return client.getMainRenderTarget(); }
    /** Opens a folder or file in the system's file manager. */
    public static void openPath(java.nio.file.Path path) { net.minecraft.util.Util.getPlatform().openPath(path); }
}
