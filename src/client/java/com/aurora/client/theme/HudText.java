package com.aurora.client.theme;

/**
 * The HUD layer's shared <b>informational-text</b> color — the single
 * resolver for the mod-wide {@code AuroraConfig.hudColor} field read by
 * every plain-readout HUD module (Info, CPS, Stats, Totem Pops, Reach,
 * Armor durability text, Ping's unknown-latency fallback, Keystrokes key
 * labels).
 *
 * <p><b>Semantics (R6 extension of the Part 2 pilot):</b> {@code 0} is the
 * follow-accent sentinel and the factory default — HUD text takes the theme's
 * {@link ThemeToken#ACCENT}, the same resolution the Keystrokes pilot gave
 * {@code keystrokesAccentColor}. Any non-zero ARGB is an explicit override and
 * wins verbatim; that includes legacy configs that persisted the old default
 * {@code 0xFFFFFFFF} (white) before the sentinel existed — existing users see
 * no change until they reset the field or pick a new color. The only value
 * reinterpreted is a literal {@code 0}, which previously rendered fully
 * transparent (invisible text) and was never a usable choice.
 *
 * <p>Deliberately <i>not</i> part of {@link HudStatus}: that class is the
 * fixed-hue, never-accent status palette; this one is accent-following by
 * design. Like the status palette, HUD modules stay never-glass — this is
 * purely a color source.
 */
public final class HudText {

    private HudText() {}

    /**
     * Resolve the configured HUD text color: {@code 0} follows the theme
     * accent, any non-zero value is returned verbatim.
     */
    public static int color(int configured) {
        return configured == 0 ? ThemeManager.color(ThemeToken.ACCENT) : configured;
    }
}
