package com.aurora.client.ui.render;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.systems.RenderSystem;

/** Test the active device, never a texture subclass or the presence of a mod. */
public final class RenderBackend {
    private RenderBackend() {}

    public static boolean isOpenGl() {
        return RenderSystem.getDevice() instanceof GlDevice;
    }
}
