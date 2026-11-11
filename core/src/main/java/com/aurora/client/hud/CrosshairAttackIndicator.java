package com.aurora.client.hud;

import com.aurora.client.config.AuroraConfig.CrosshairStyle;

/** Vanilla placement, with clearance applied only when the crosshair overlaps it. */
public final class CrosshairAttackIndicator {
    private CrosshairAttackIndicator() {}

    public record Layout(float scale, float top) {}

    public static Layout layout(int indicatorSizePercent, CrosshairStyle style, int size, int thickness,
                                int gap, int canvasWidth, int canvasHeight) {
        float scale = Math.clamp(indicatorSizePercent, 25, 300) / 100.0f;
        float bottom = switch (style == null ? CrosshairStyle.CROSS : style) {
            case DOT -> (size + thickness - 1) / 2.0f;
            case CIRCLE, SQUARE -> size + 0.5f;
            case CROSS -> size + gap + (thickness % 2) - 0.5f;
            case CUSTOM -> size * 1.5f * Math.max(1, canvasHeight)
                    / Math.max(Math.max(1, canvasWidth), Math.max(1, canvasHeight));
        };
        // Vanilla draws at screen center + 9. Small crosshairs must not pull
        // the indicator upward, nor should a narrow remaining gap push it down.
        return new Layout(scale, bottom > 9 ? bottom + 2 : 9);
    }
}
