package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import net.minecraft.client.Minecraft;

/** Reconfigure the surface when Aurora's VSync override changes, preserving the vanilla preference. */
public class LowLatencyFeature implements Feature {
    public static final String ID = "low_latency";
    private boolean forcedNoVSync;
    @Override public String id() { return ID; }
    @Override public void onRegister() {}
    @Override public void onTick(Minecraft client) {
        if (client == null) return;
        AuroraConfig cfg = AuroraConfig.get();
        boolean wantNoVSync = cfg.lowLatencyRender && cfg.disableVSync;
        if (wantNoVSync != forcedNoVSync) {
            forcedNoVSync = wantNoVSync;
            client.invalidateSurfaceConfiguration();
        }
    }
}
