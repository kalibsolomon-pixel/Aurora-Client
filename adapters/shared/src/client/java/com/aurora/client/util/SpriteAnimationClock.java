package com.aurora.client.util;

/**
 * Counts texture animation steps: animated sprites change only when
 * {@code TextureManager.tick()} runs ({@code TextureManagerAnimationMixin}),
 * so a GUI item drawn at the current count still shows the current frame.
 * Read and written on the client thread only.
 */
public final class SpriteAnimationClock {
    private SpriteAnimationClock() {}

    private static long ticks;

    public static long ticks() { return ticks; }

    public static void advance() { ticks++; }
}
