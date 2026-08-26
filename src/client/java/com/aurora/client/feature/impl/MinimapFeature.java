package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import com.aurora.client.util.AuroraKey;
import net.minecraft.client.Minecraft;

/**
 * Feature shell for the minimap HUD: owns only the show/hide toggle
 * keybind, independent of the fullscreen World Map key. All rendering and
 * sampling lives in {@code MinimapModule} (HUD layer); this class flips the
 * master enable exactly like every other Aurora feature toggle
 * (edge-detected, in-game only via {@link AuroraKey.EdgeDetector}, persisted
 * immediately through the async config saver).
 */
public class MinimapFeature implements Feature {
    public static final String ID = "minimap";

    private final AuroraKey.EdgeDetector toggleEdge = new AuroraKey.EdgeDetector();

    @Override public String id() { return ID; }

    @Override
    public void onTick(Minecraft client) {
        AuroraConfig cfg = AuroraConfig.get();
        if (toggleEdge.justPressed(cfg.minimapToggleKey)) {
            cfg.minimapEnabled = !cfg.minimapEnabled;
            AuroraConfig.save();
        }
    }
}
