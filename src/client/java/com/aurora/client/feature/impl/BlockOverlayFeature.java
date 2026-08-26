package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import net.minecraft.client.Minecraft;

import java.awt.Color;

/**
 * Provides block-overlay colors each frame. The outline and fill colors
 * share a single mode (SOLID vs RAINBOW) and rainbow speed — when rainbow
 * is on, both cycle through the same hue, each preserving its configured
 * alpha so users can dial transparency independently.
 *
 * <p>Outline rendering is recolored by VertexRenderingMixin (which calls
 * {@link #currentOutlineColor()}). Fill rendering is done by
 * BlockOverlayRenderer (which calls {@link #currentFillColor()}).
 */
public class BlockOverlayFeature implements Feature {
    public static final String ID = "block_overlay";

    @Override public String id() { return ID; }
    @Override public void onRegister() {}
    @Override public void onTick(Minecraft client) {}

    /** True if the master is on AND the outline sub-toggle is on. */
    public static boolean isOutlineVisible() {
        AuroraConfig cfg = AuroraConfig.get();
        return cfg.blockOverlayEnabled && cfg.blockOverlayOutlineEnabled;
    }

    /** True if the master is on AND the fill sub-toggle is on. */
    public static boolean isFillVisible() {
        AuroraConfig cfg = AuroraConfig.get();
        return cfg.blockOverlayEnabled && cfg.blockOverlayFillEnabled;
    }

    /** ARGB color for the wireframe outline this frame. */
    public static int currentOutlineColor() {
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.blockOverlayMode == AuroraConfig.BlockOverlayMode.RAINBOW) {
            return rainbowWithAlpha(cfg.blockOverlayOutlineColor, cfg.blockOverlayRainbowSpeed);
        }
        return cfg.blockOverlayOutlineColor;
    }

    /** ARGB color for the face fill this frame. */
    public static int currentFillColor() {
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.blockOverlayMode == AuroraConfig.BlockOverlayMode.RAINBOW) {
            return rainbowWithAlpha(cfg.blockOverlayFillColor, cfg.blockOverlayRainbowSpeed);
        }
        return cfg.blockOverlayFillColor;
    }

    private static int rainbowWithAlpha(int baseArgb, float speed) {
        float hue = (float) ((System.currentTimeMillis() / 1000.0) * speed % 1.0);
        int rgb = Color.HSBtoRGB(hue, 1.0f, 1.0f);
        int alpha = (baseArgb >>> 24) & 0xFF;
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }
}