package com.aurora.client.feature;

import net.minecraft.client.Minecraft;

/**
 * Minimal feature contract. Features are long-lived singletons managed by
 * {@link FeatureManager}. They may register keybinds in {@link #onRegister()}
 * and run per-tick logic in {@link #onTick(Minecraft)}.
 */
public interface Feature {
    String id();

    default String displayName() {
        return id();
    }

    /** Called once at client init, after config load. */
    default void onRegister() {}

    /** Called every client tick while the client is running. */
    default void onTick(Minecraft client) {}
}
