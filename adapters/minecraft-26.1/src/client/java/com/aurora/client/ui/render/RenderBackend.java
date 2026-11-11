package com.aurora.client.ui.render;

import com.mojang.blaze3d.systems.RenderSystem;

/** Test the active device, never a texture subclass or the presence of a mod. */
public final class RenderBackend {
    private RenderBackend() {}

    /** 26.1 wraps every backend in a public GpuDevice; the OpenGL one names itself "OpenGL". */
    private static final String OPENGL = "OpenGL";

    public static boolean isOpenGl() {
        return OPENGL.equals(RenderSystem.getDevice().getBackendName());
    }
}
