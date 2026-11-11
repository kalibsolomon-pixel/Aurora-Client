package com.aurora.client.feature.impl;

import com.aurora.client.feature.Feature;
import net.minecraft.client.Minecraft;

/**
 * Keeps the player sprinting while walking forward. Stub implementation â€”
 * extend with input listener wiring when desired.
 */
public class AutoSprintFeature implements Feature {
    public static final String ID = "auto_sprint";

    private boolean active = false;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void onTick(Minecraft client) {
        if (!active) return;
        if (client == null || client.player == null || client.options == null) return;
        if (client.options.keyUp.isDown() && !client.player.isSprinting()) {
            client.player.setSprinting(true);
        }
    }

    public void setActive(boolean v) { this.active = v; }
    public boolean isActive() { return active; }
}
