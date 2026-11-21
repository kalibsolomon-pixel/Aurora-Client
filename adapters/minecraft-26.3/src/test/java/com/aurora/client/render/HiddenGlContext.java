package com.aurora.client.render;

import org.lwjgl.opengl.GL;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLVideo;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Invisible OpenGL 3.3 core context for GPU shader tests on SDL lines (26.3). */
final class HiddenGlContext implements AutoCloseable {
    private final long window;
    private final long context;

    private HiddenGlContext(long window, long context) {
        this.window = window;
        this.context = context;
    }

    static HiddenGlContext open(String title) {
        assumeTrue(SDLInit.SDL_Init(SDLInit.SDL_INIT_VIDEO), "No SDL display available");
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_MAJOR_VERSION, 3);
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_MINOR_VERSION, 3);
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_PROFILE_MASK, SDLVideo.SDL_GL_CONTEXT_PROFILE_CORE);
        long window = SDLVideo.SDL_CreateWindow(title, 16, 16, SDLVideo.SDL_WINDOW_OPENGL | SDLVideo.SDL_WINDOW_HIDDEN);
        if (window == 0) {
            SDLInit.SDL_Quit();
            assumeTrue(false, "No OpenGL window available");
        }
        long context = SDLVideo.SDL_GL_CreateContext(window);
        if (context == 0) {
            SDLVideo.SDL_DestroyWindow(window);
            SDLInit.SDL_Quit();
            assumeTrue(false, "No OpenGL context available");
        }
        SDLVideo.SDL_GL_MakeCurrent(window, context);
        GL.createCapabilities();
        return new HiddenGlContext(window, context);
    }

    @Override
    public void close() {
        GL.setCapabilities(null);
        if (context != 0) SDLVideo.SDL_GL_DestroyContext(context);
        if (window != 0) SDLVideo.SDL_DestroyWindow(window);
        SDLInit.SDL_Quit();
    }
}
