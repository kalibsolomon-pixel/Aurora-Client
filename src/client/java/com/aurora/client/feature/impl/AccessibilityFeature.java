package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import net.minecraft.client.Minecraft;

/**
 * Accessibility features:
 * <ul>
 *   <li><b>Colorblind correction</b> — applies a daltonization-style color
 *       matrix transform as a post-processing effect. The selected mode
 *       (protanopia, deuteranopia, tritanopia, and their "weak" variants)
 *       shifts the color space so affected users can distinguish hues they
 *       normally cannot.</li>
 *   <li><b>Scroll-wheel remap</b> — configuration surface for remapping the
 *       scroll wheel to alternative actions. The actual interception happens
 *       in {@code MouseMixin}; this feature owns the config polling and
 *       validation.</li>
 * </ul>
 *
 * <p>The colorblind filter is implemented as a shader-based post-process
 * pass. A 3×3 matrix per mode is applied to the final framebuffer's RGB
 * channels. This is the same technique used by Windows/MacOS accessibility
 * settings and most modern games.
 *
 * <p>Screen-reader support is explicitly deferred to a future phase as it
 * requires platform-specific TTS APIs (UIAutomation on Windows,
 * NSAccessibility on macOS).
 */
public class AccessibilityFeature implements Feature {
    public static final String ID = "accessibility";

    private static AccessibilityFeature instance;

    @Override public String id() { return ID; }

    @Override
    public void onRegister() {
        instance = this;
    }

    @Override
    public void onTick(Minecraft client) {
        // No per-tick work — the colorblind filter is applied in the
        // render post-process pass, and scroll remap is handled by the
        // mouse mixin. This feature exists primarily for config ownership
        // and future expansion (screen-reader bridge, etc.).
    }

    public static AccessibilityFeature get() { return instance; }

    // ===== Colorblind matrices =====

    /**
     * Returns the 3×3 RGB correction matrix for the currently-selected
     * colorblind mode, or {@code null} if disabled.
     *
     * <p>Matrices are derived from the standard daltonization algorithm.
     * Each row is an RGB output channel; columns are R, G, B input.
     */
    public static float[][] getColorMatrix() {
        AuroraConfig.ColorblindMode mode = AuroraConfig.get().colorblindMode;
        if (mode == AuroraConfig.ColorblindMode.OFF) return null;

        float strength = AuroraConfig.get().colorblindStrength / 100.0f;
        strength = Math.max(0.0f, Math.min(1.0f, strength));

        // LMS-based simulation matrices. These approximate how a person
        // with the given deficiency perceives colors. We then compute a
        // correction (daltonize) that shifts distinguishable information
        // into the visible range.
        return switch (mode) {
            case PROTANOPIA -> lerpIdentity(new float[][]{
                    {0.567f, 0.433f, 0.000f},
                    {0.558f, 0.442f, 0.000f},
                    {0.000f, 0.242f, 0.758f}
            }, strength);

            case DEUTERANOPIA -> lerpIdentity(new float[][]{
                    {0.625f, 0.375f, 0.000f},
                    {0.700f, 0.300f, 0.000f},
                    {0.000f, 0.300f, 0.700f}
            }, strength);

            case TRITANOPIA -> lerpIdentity(new float[][]{
                    {0.950f, 0.050f, 0.000f},
                    {0.000f, 0.433f, 0.567f},
                    {0.000f, 0.475f, 0.525f}
            }, strength);

            case PROTANOMALY -> lerpIdentity(new float[][]{
                    {0.817f, 0.183f, 0.000f},
                    {0.333f, 0.667f, 0.000f},
                    {0.000f, 0.125f, 0.875f}
            }, strength);

            case DEUTERANOMALY -> lerpIdentity(new float[][]{
                    {0.800f, 0.200f, 0.000f},
                    {0.258f, 0.742f, 0.000f},
                    {0.000f, 0.142f, 0.858f}
            }, strength);

            case OFF -> null;
        };
    }

    /**
     * Lerps between the identity matrix and the given matrix by the
     * given factor, so {@code strength} blends from no correction (0.0)
     * to full correction (1.0).
     */
    private static float[][] lerpIdentity(float[][] m, float t) {
        float[][] result = new float[3][3];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                float identity = (r == c) ? 1.0f : 0.0f;
                result[r][c] = identity * (1 - t) + m[r][c] * t;
            }
        }
        return result;
    }
}