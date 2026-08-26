package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Reduces input-to-photon latency by setting the GLFW swap interval to 0
 * when the user opts into no-vsync. Vanilla calls glfwSwapInterval every
 * frame based on its own vsync option, so this feature is belt-and-suspenders
 * for users who want guaranteed no-vsync regardless of vanilla state.
 *
 * <p>Tearing warning: VSync off on a non-VRR monitor causes screen tearing.
 * Users with G-Sync or FreeSync get the latency win with no visible cost.
 */
public class LowLatencyFeature implements Feature {
    public static final String ID = "low_latency";

    private boolean appliedThisSession = false;
    private boolean lastVSyncState = true;

    @Override public String id() { return ID; }
    @Override public void onRegister() {}

    @Override
    public void onTick(Minecraft client) {
        if (client == null) return;
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.lowLatencyRender) return;

        boolean noVSync = cfg.disableVSync;
        if (noVSync == lastVSyncState && appliedThisSession) return;

        try {
            GLFW.glfwSwapInterval(noVSync ? 0 : 1);
        } catch (Throwable ignored) {
            // Skip if context isn't current on this thread; will retry next tick.
            return;
        }

        lastVSyncState = noVSync;
        appliedThisSession = true;
    }
}