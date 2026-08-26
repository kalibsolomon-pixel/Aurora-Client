package com.aurora.client.theme;

/**
 * The corner-roundness family a theme renders with — modeled on COSMIC's
 * Appearance "Corner roundness" setting (Round / Slightly round / Square).
 *
 * <p>Stage 3's first <b>non-color token</b>: rather than a one-off config
 * value, the concrete radii live in the theme definition and are resolved
 * and projected exactly like the colors (see
 * {@link ResolvedTheme#project()} → {@code AuroraTheme.RADIUS*}). The
 * {@link #ROUND} mapping equals the historical compile-time constants
 * (10/14/6), so existing users see no change on upgrade.
 *
 * <p>Unknown or missing persisted values must never crash:
 * {@link #fromName} coerces them to {@link #ROUND}.
 */
public enum ThemeRoundness {
    /** Fully rounded — the historical Aurora look. */
    ROUND("Round"),
    /** Reduced radii — a tighter, more utilitarian look. */
    SLIGHTLY_ROUND("Slightly Round"),
    /** Sharp corners — radius 0 everywhere. */
    SQUARE("Square");

    private final String displayName;

    ThemeRoundness(String displayName) {
        this.displayName = displayName;
    }

    /** Human-friendly option label for the segmented control. */
    public String displayName() {
        return displayName;
    }

    /** Standard chamfer for panels/buttons (historical {@code RADIUS}). */
    public int radius() {
        return switch (this) {
            case ROUND -> 10;
            case SLIGHTLY_ROUND -> 6;
            case SQUARE -> 0;
        };
    }

    /** Larger chamfer for major sheets/modals (historical {@code RADIUS_LARGE}). */
    public int radiusLarge() {
        return switch (this) {
            case ROUND -> 14;
            case SLIGHTLY_ROUND -> 8;
            case SQUARE -> 0;
        };
    }

    /** Tight chamfer for compact controls (historical {@code RADIUS_SMALL}). */
    public int radiusSmall() {
        return switch (this) {
            case ROUND -> 6;
            case SLIGHTLY_ROUND -> 3;
            case SQUARE -> 0;
        };
    }

    /**
     * Parse a persisted roundness name, falling back to {@link #ROUND} for
     * null, blank, or unrecognized values (same contract as
     * {@link ThemeMode#fromName}).
     */
    public static ThemeRoundness fromName(String name) {
        if (name != null) {
            for (ThemeRoundness r : values()) {
                if (r.name().equalsIgnoreCase(name)) return r;
            }
        }
        return ROUND;
    }
}