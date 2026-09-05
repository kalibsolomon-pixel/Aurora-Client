package com.aurora.client.theme;

/**
 * The rendering technique Aurora's panel backgrounds use — the theme's
 * second <b>non-color token</b>, alongside {@link ThemeRoundness}.
 *
 * <p>This is deliberately NOT a new look. It selects between the two paths
 * the UI already has:
 * <ul>
 *   <li>{@link #FROSTED} — the glass material: the backdrop behind each
 *       panel is captured, blurred and composited by
 *       {@code BlurPanelRenderer}, then tinted by the caller's ordinary
 *       {@code WINDOW_FILL} fill.</li>
 *   <li>{@link #TRANSPARENT} — {@code BlurPanelRenderer.renderPanel}
 *       declines up front, so every glass consumer takes the flat
 *       translucent fill it is already required to draw when the renderer
 *       declines (the fallback contract). No call site needs to know this
 *       setting exists.</li>
 * </ul>
 *
 * <p>Because Transparent decides before any GL work, it also skips the
 * capture/blur/readback cost entirely (~1-2 ms per panel per frame) — a
 * real win on lower-end hardware, though the setting exists as a look
 * preference first.
 *
 * <p>Orthogonal to the other theme settings by design: Corner Style still
 * shapes the flat fills and Background Opacity is still the single alpha
 * application point in both styles. {@link #FROSTED} is the default so
 * existing users see no change until they opt in.
 *
 * <p>Unknown or missing persisted values must never crash:
 * {@link #fromName} coerces them to {@link #FROSTED}.
 */
public enum GlassStyle {
    /** Blurred glass material — Aurora's established look. */
    FROSTED("Frosted"),
    /** Flat translucent fills; the blur pipeline never runs. */
    TRANSPARENT("Transparent");

    private final String displayName;

    GlassStyle(String displayName) {
        this.displayName = displayName;
    }

    /** Human-friendly option label for the segmented control. */
    public String displayName() {
        return displayName;
    }

    /**
     * Parse a persisted style name, falling back to {@link #FROSTED} for
     * null, blank, or unrecognized values (same contract as
     * {@link ThemeRoundness#fromName}).
     */
    public static GlassStyle fromName(String name) {
        if (name != null) {
            for (GlassStyle s : values()) {
                if (s.name().equalsIgnoreCase(name)) return s;
            }
        }
        return FROSTED;
    }
}
