package com.aurora.client.theme;

/**
 * The brightness mode a theme renders in.
 *
 * <p>Stage 1 shipped a single mode ({@link #DARK}). Stage 2 adds
 * {@link #LIGHT} with its own derivation rules. An {@code auto} (OS-synced)
 * mode is deliberately omitted — Minecraft's client has no reliable OS
 * dark-mode signal, and it isn't worth the platform-specific complexity
 * for this stage. Unknown or missing values must never crash:
 * {@link #fromName} coerces them to {@link #DARK}.
 */
public enum ThemeMode {
    DARK,
    LIGHT;

    /**
     * Parse a persisted mode name, falling back to {@link #DARK} for null,
     * blank, or unrecognized values.
     */
    public static ThemeMode fromName(String name) {
        if (name != null) {
            for (ThemeMode m : values()) {
                if (m.name().equalsIgnoreCase(name)) return m;
            }
        }
        return DARK;
    }
}
