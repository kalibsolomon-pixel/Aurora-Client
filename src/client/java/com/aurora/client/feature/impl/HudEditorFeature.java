package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import com.aurora.client.screen.HudEditorScreen;
import com.aurora.client.util.AuroraKey;
import net.minecraft.client.Minecraft;

/**
 * Registers the RShift keybind that opens the in-game HUD editor.
 * Only opens when the player isn't already in another screen.
 */
public class HudEditorFeature implements Feature {
    public static final String ID = "hud_editor";

    private final AuroraKey.EdgeDetector edge = new AuroraKey.EdgeDetector();

    @Override public String id() { return ID; }

    @Override
    public void onTick(Minecraft client) {
        if (client == null) return;
        int key = AuroraConfig.get().hudEditorKey;
        if (edge.justPressed(key)) {
            if (client.screen == null && client.player != null) {
                client.setScreen(new HudEditorScreen());
            }
        }
    }
}