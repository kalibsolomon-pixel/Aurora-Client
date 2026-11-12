package com.aurora.client.config.profile;

import java.util.Map;

/**
 * Reads and writes the game's own (vanilla) key bindings for profiles,
 * installed by the Minecraft adapter (the core has no Minecraft classes,
 * ADR 0003). Keys are binding names ({@code key.jump}); values are the
 * game's own saved key strings ({@code key.keyboard.space}), the format of
 * options.txt, so they survive across supported versions where the key
 * still exists. Aurora's own bindings are not included: they already live
 * in the profile's config fields.
 */
public interface ControlsBridge {
    /** The current vanilla bindings, name to saved key string. */
    Map<String, String> capture();

    /**
     * Applies the given bindings, skipping names or key strings this game
     * version does not know, and persists them the way the Controls screen
     * does.
     */
    void apply(Map<String, String> controls);
}
