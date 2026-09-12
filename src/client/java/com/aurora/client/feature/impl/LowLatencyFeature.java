package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Enforces the Disable VSync option at the GLFW level. Vanilla applies
 * glfwSwapInterval exactly twice: at startup and whenever the vanilla
 * vsync option changes (Window.updateVsync, called from the Minecraft
 * constructor and the option's observer) — never per frame — so a swap
 * interval set here sticks until the user touches the vanilla option.
 * When Aurora's no-vsync is turned back off (or this feature's master),
 * the vanilla option's own value is restored rather than forcing vsync
 * on, which would override a user's vanilla vsync-off preference.
 *
 * <p>Tearing warning: VSync off on a non-VRR monitor causes screen tearing.
 * Users with G-Sync or FreeSync get the latency win with no visible cost.
 */
public class LowLatencyFeature implements Feature {
    public static final String ID = "low_latency";

    /** True while we have forced swap interval 0 and owe a restore. */
    private boolean forcedNoVSync = false;

    @Override public String id() { return ID; }
    @Override public void onRegister() {}

    @Override
    public void onTick(Minecraft client) {
        if (client == null) return;
        AuroraConfig cfg = AuroraConfig.get();

        boolean wantNoVSync = cfg.lowLatencyRender && cfg.disableVSync;
        if (wantNoVSync == forcedNoVSync) return;

        try {
            if (wantNoVSync) {
                GLFW.glfwSwapInterval(0);
            } else {
                // Leaving the forced state: hand control back to the
                // vanilla option instead of asserting vsync on.
                GLFW.glfwSwapInterval(client.options.enableVsync().get() ? 1 : 0);
            }
        } catch (Throwable ignored) {
            // Skip if context isn't current on this thread; will retry next tick.
            return;
        }

        forcedNoVSync = wantNoVSync;
    }
}
