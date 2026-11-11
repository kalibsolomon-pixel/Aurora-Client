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
}
