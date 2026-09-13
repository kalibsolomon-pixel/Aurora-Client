package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import net.minecraft.client.Minecraft;

/**
 * Enforces the Disable VSync option at the GLFW level. Vanilla asserts the
 * swap interval through {@code Window.updateVsync} from three places: the
 * {@code Minecraft} constructor, the vsync option's observer (any Video
 * Settings change), and — the one this class used to miss — {@code
 * Window.updateDisplay}, which re-asserts {@code updateVsync(this.vsync)}
 * whenever a pending fullscreen (F11) transition lands.
 *
 * <p>Historical bug (fixed 2026-09-13): this feature called
 * {@code glfwSwapInterval(0)} directly, leaving {@code Window.vsync} holding
 * the vanilla option's value — so an F11 transition silently re-enabled
 * vsync (measured: 234.9 fps → 60.0 vblank-quantized, still 60.0 after
 * toggling back) and the feature never noticed, because its own forced
 * state had not changed. The fix routes every Aurora write through {@code
 * Window.updateVsync} — vanilla's tracked field stays identical to the
 * driver state we force, so the fullscreen re-assert now lands on interval
 * 0 — and re-asserts on every tick while active, so the option observer
 * (the one vanilla writer that can still fire mid-forcing) is overwritten
 * within a tick. When Aurora's no-vsync is turned back off (or this
 * feature's master), the vanilla option's own value is restored rather
 * than forcing vsync on, which would override a user's vanilla vsync-off
 * preference.
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
        try {
            if (wantNoVSync) {
                // Re-assert every tick, through Window.updateVsync: the field
                // write keeps vanilla's fullscreen-transition re-assert on our
                // side, and the per-tick retry overwrites the vanilla option
                // observer's writes within one tick.
                client.getWindow().updateVsync(false);
                forcedNoVSync = true;
            } else if (forcedNoVSync) {
                // Leaving the forced state: hand control back to the vanilla
                // option instead of asserting vsync on.
                client.getWindow().updateVsync(client.options.enableVsync().get());
                forcedNoVSync = false;
            }
        } catch (Throwable ignored) {
            // Skip if context isn't current on this thread; will retry next tick.
        }
    }
}
