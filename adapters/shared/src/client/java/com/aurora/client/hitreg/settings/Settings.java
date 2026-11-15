package com.aurora.client.hitreg.settings;

import com.aurora.client.config.AuroraConfig;

/**
 * Settings facade for the Better Hitreg core (from BetterHitreg by Jass,
 * integrated with permission). Upstream this class owned a
 * {@code java.util.Properties} file and rewrote it synchronously on the
 * main thread on every change; here every value lives on
 * {@link AuroraConfig} (fields prefixed {@code hitreg…}). Only the custom
 * client-side hitreg remains (2026-11-15), so only its delay is read here.
 *
 * <p>Master gate: every gameplay-affecting read (including
 * {@link Toggle#toggled()}) is ANDed with {@link AuroraConfig#hitregEnabled},
 * so turning the feature card off makes every mixin dormant without
 * touching their code.
 */
public final class Settings {
    private Settings() {}

    static AuroraConfig cfg() {
        return AuroraConfig.get();
    }

    /** The feature card's master switch. */
    public static boolean masterEnabled() {
        return cfg().hitregEnabled;
    }

    /** {@code hitreg} — feedback delay in ms; 0 means "next frame", not "off". */
    public static int getHitreg() {
        return Math.max(0, cfg().hitregDelayMs);
    }
}
