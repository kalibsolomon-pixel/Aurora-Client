package com.aurora.client.theme;

/**
 * Resolves each HUD's optional text override, falling back to the legacy global
 * color for existing configurations. Only the legacy color uses 0 as a theme
 * accent sentinel; explicit per-module ARGB values preserve all alpha values.
 */
public final class HudText {

    private HudText() {}

    /** Null inherits legacy/global policy; explicit overrides include fully transparent colors. */
    public static int color(Integer override, int legacy) {
        return override == null ? color(legacy) : override;
    }

    /**
     * Resolve the configured HUD text color: {@code 0} follows the theme
     * accent, any non-zero value is returned verbatim.
     */
    public static int color(int configured) {
        return configured == 0 ? ThemeManager.color(ThemeToken.ACCENT) : configured;
    }
}
