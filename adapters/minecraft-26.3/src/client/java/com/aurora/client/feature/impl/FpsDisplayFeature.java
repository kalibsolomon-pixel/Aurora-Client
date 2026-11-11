package com.aurora.client.feature.impl;

import com.aurora.client.feature.Feature;

/**
 * No-op marker feature. Actual FPS rendering lives in
 * {@link com.aurora.client.hud.HudRenderer}; this exists so the feature
 * appears in the registry (for future keybind toggles, config screens, etc.).
 */
public class FpsDisplayFeature implements Feature {
    public static final String ID = "fps_display";

    @Override
    public String id() {
        return ID;
    }
}
