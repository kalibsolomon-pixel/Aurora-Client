package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import com.aurora.client.util.AuroraKey;
import com.aurora.client.worldmap.WorldMapClient;
import com.aurora.client.worldmap.screen.WorldMapScreen;
import net.minecraft.client.Minecraft;

/**
 * Feature shell for the fullscreen world map. All heavy lifting lives in
 * {@link WorldMapClient} (capture pipeline, region cache, storage); this
 * class only bootstraps the engine at client init and drives the open-map
 * keybind + per-tick pump from the feature manager's tick loop.
 */
public class WorldMapFeature implements Feature {
    public static final String ID = "world_map";

    private final AuroraKey.EdgeDetector openEdge = new AuroraKey.EdgeDetector();

    @Override public String id() { return ID; }

    @Override
    public void onRegister() {
        WorldMapClient.init();
    }

    @Override
    public void onTick(Minecraft client) {
        AuroraConfig cfg = AuroraConfig.get();

        // Open-map keybind — only in-game with no other screen open, and
        // only when the module card is enabled.
        if (openEdge.justPressed(cfg.worldMapKey)
                && cfg.worldMapEnabled
                && client.screen == null
                && client.player != null
                && client.level != null) {
            client.setScreen(new WorldMapScreen());
        }

        WorldMapClient.tick(client);
    }
}
