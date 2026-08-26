package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import com.aurora.client.util.AuroraKey;
import net.minecraft.client.Minecraft;

/**
 * Hold-to-zoom with time-based quadratic ease-in-out, plus live
 * scroll-wheel zoom adjustment while the key is held.
 *
 * <p>While zoomed, scrolling the MouseHandler wheel multiplies the zoom level by
 * {@link #scrollMultiplier}. The multiplier resets to 1.0 when the zoom
 * key is released, so the next zoom starts at the config-defined level.
 */
public class ZoomFeature implements Feature {
    public static final String ID = "zoom";

    // Live divisor read by GameRendererMixin every frame.
    private static volatile double currentDivisor = 1.0;

    // Active flag read by MouseMixin to decide whether to intercept scroll.
    private static volatile boolean activeFlag = false;

    // Scroll-wheel multiplier stacked on top of config zoom level.
    private static volatile double scrollMultiplier = 1.0;
    private static final double MULT_MIN = 1.0;
    private static final double MULT_MAX = 8.0;
    private static final double MULT_STEP = 1.12; // per scroll click (geometric)

    // Animation state.
    private static long   animStartMillis = 0L;
    private static double animFromDivisor = 1.0;
    private static double animToDivisor   = 1.0;
    private static double animDurationMs  = 200.0;

    private boolean zooming;
    private Double originalSensitivity;

    @Override public String id() { return ID; }

    @Override
    public void onTick(Minecraft client) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.zoomEnabled || client == null || client.options == null) {
            if (zooming) stopZoom(client);
            beginAnim(1.0, cfg);
            return;
        }

        boolean down = AuroraKey.isDown(cfg.zoomKey);
        if (down && !zooming) {
            startZoom(client);
            beginAnim(effectiveTargetDivisor(cfg), cfg);
        } else if (!down && zooming) {
            stopZoom(client);
            beginAnim(1.0, cfg);
        }
    }

    /** Called by MouseMixin on scroll when active. Positive v = scroll up = zoom in. */
    public static void onScroll(double vertical) {
        if (!activeFlag) return;
        if (vertical > 0) {
            scrollMultiplier = Math.min(MULT_MAX, scrollMultiplier * MULT_STEP);
        } else if (vertical < 0) {
            scrollMultiplier = Math.max(MULT_MIN, scrollMultiplier / MULT_STEP);
        }

        // Retarget the easing curve so the zoom glides to the new level.
        AuroraConfig cfg = AuroraConfig.get();
        double newTarget = effectiveTargetDivisor(cfg);
        if (Math.abs(newTarget - animToDivisor) > 0.0005) {
            double now = sampleCurve();
            animFromDivisor = now;
            animToDivisor   = newTarget;
            animStartMillis = System.currentTimeMillis();
            // Use a shorter duration for scroll adjustments so it feels responsive.
            animDurationMs  = Math.max(60.0, cfg.zoomSmoothness * 400.0);
        }

        // Also scale sensitivity to match the new zoom level.
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.options != null) {
            try {
                double baseSens = originalSensCache != null ? originalSensCache
                        : client.options.sensitivity().get();
                double factor = Math.max(1.0, newTarget);
                client.options.sensitivity().set(baseSens / factor);
            } catch (Exception ignored) {}
        }
    }

    public static boolean isActive() {
        return activeFlag;
    }

    private static double effectiveTargetDivisor(AuroraConfig cfg) {
        double base = Math.max(1.01, cfg.zoomLevel);
        return Math.min(80.0, base * scrollMultiplier);
    }

    private void beginAnim(double target, AuroraConfig cfg) {
        if (Math.abs(target - animToDivisor) < 0.0005) return;
        double now = sampleCurve();
        animFromDivisor  = now;
        animToDivisor    = target;
        animStartMillis  = System.currentTimeMillis();
        animDurationMs   = Math.max(10.0, cfg.zoomSmoothness * 1000.0);
        currentDivisor   = now;
    }

    public static double getCurrentFovDivisor() {
        double v = sampleCurve();
        currentDivisor = v;
        return v;
    }

    private static double sampleCurve() {
        long elapsed = System.currentTimeMillis() - animStartMillis;
        if (elapsed <= 0) return animFromDivisor;
        if (elapsed >= animDurationMs) return animToDivisor;
        double t = elapsed / animDurationMs;
        return animFromDivisor + (animToDivisor - animFromDivisor) * easeInOutQuad(t);
    }

    private static double easeInOutQuad(double t) {
        if (t < 0.5) return 2.0 * t * t;
        double u = -2.0 * t + 2.0;
        return 1.0 - (u * u) / 2.0;
    }

    // Keep a stable reference to the pre-zoom sensitivity so scroll
    // adjustments can recompute the scaled value correctly.
    private static volatile Double originalSensCache = null;

    private void startZoom(Minecraft client) {
        zooming = true;
        activeFlag = true;
        try {
            originalSensitivity = client.options.sensitivity().get();
            originalSensCache   = originalSensitivity;
            double factor = Math.max(1.0, AuroraConfig.get().zoomLevel);
            client.options.sensitivity().set(originalSensitivity / factor);
        } catch (Exception ignored) {}
    }

    private void stopZoom(Minecraft client) {
        zooming = false;
        activeFlag = false;
        scrollMultiplier = 1.0; // reset for next zoom session
        if (originalSensitivity != null && client != null && client.options != null) {
            try {
                client.options.sensitivity().set(originalSensitivity);
            } catch (Exception ignored) {}
        }
        originalSensitivity = null;
        originalSensCache = null;
    }
}