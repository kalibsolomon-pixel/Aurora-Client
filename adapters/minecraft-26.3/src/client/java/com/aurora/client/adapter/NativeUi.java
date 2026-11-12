package com.aurora.client.adapter;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Version-owned GUI access; shared screens keep one behavior implementation. */
public final class NativeUi {
    private NativeUi() {}
    public static Screen screen(Minecraft client) { return client.gui.screen(); }
    public static void setScreen(Minecraft client, Screen screen) { client.gui.setScreen(screen); }
    public static RenderTarget mainTarget(Minecraft client) { return client.gameRenderer.mainRenderTarget(); }
    /** Opens a folder or file in the system's file manager (26.3 moved it to Blaze3D). */
    public static void openPath(java.nio.file.Path path) { com.mojang.blaze3d.Blaze3D.openPath(path); }
}
