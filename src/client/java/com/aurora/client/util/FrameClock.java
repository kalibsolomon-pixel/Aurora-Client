package com.aurora.client.util;

import org.lwjgl.sdl.SDLTimer;

/** The SDL epoch used by vanilla RenderSystem frame deadlines (not System.nanoTime). */
public final class FrameClock {
    private FrameClock() {}

    public static double seconds() {
        return SDLTimer.SDL_GetTicksNS() * 1.0e-9;
    }
}
