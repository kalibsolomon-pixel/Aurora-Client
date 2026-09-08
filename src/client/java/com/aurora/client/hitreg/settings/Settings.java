package com.aurora.client.hitreg.settings;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.StatsTrackerFeature;
import com.aurora.client.hitreg.Hitreg;

/**
 * Settings facade for the Better Hitreg core (from BetterHitreg by Jass,
 * integrated with permission). Upstream this class owned a
 * {@code java.util.Properties} file and rewrote it synchronously on the
 * main thread on every change; here every value lives on
 * {@link AuroraConfig} (fields prefixed {@code hitreg…}) and the only
 * runtime write — a completed fight — goes through Aurora's async save.
 *
 * <p>The string-keyed {@link #getInt}/{@link #getFloat} accessors survive
 * only so the timing-critical body of {@link Hitreg#tick()} stays
 * byte-identical to upstream; new code should use the named getters.
 *
 * <p>Master gate: every gameplay-affecting read (including
 * {@link Toggle#toggled()}) is ANDed with {@link AuroraConfig#hitregEnabled},
 * so turning the feature card off makes every mixin, overlay and sound
 * filter dormant without touching their code.
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

    /** {@code metronome} — click interval in ticks; the caller treats values below 10 as off. */
    public static int getMetronome() {
        return masterEnabled() ? cfg().hitregMetronome : 0;
    }

    /** {@code muffle_amount} — 0..1 (clamped again by the caller). */
    public static float getMuffleAmount() {
        return masterEnabled() ? (float) cfg().hitregMuffleAmount : 0f;
    }

    /** {@code sharpen_amount} — 0..1 (clamped again by the caller). */
    public static float getSharpenAmount() {
        return masterEnabled() ? (float) cfg().hitregSharpenAmount : 0f;
    }

    /** {@code floor_grid_size} — blocks between grid lines; 0 = off. */
    public static int getFloorGridSize() {
        return masterEnabled() ? Math.max(0, cfg().hitregFloorGridSize) : 0;
    }

    /** String-keyed shim for the verbatim {@link Hitreg#tick()} body. */
    public static int getInt(String key) {
        return switch (key) {
            case "metronome" -> getMetronome();
            case "floor_grid_size" -> getFloorGridSize();
            case "hitreg" -> getHitreg();
            default -> throw new IllegalArgumentException("Unknown hitreg int setting: " + key);
        };
    }

    /** String-keyed shim for the verbatim {@link Hitreg#tick()} body. */
    public static float getFloat(String key) {
        return switch (key) {
            case "muffle_amount" -> getMuffleAmount();
            case "sharpen_amount" -> getSharpenAmount();
            default -> throw new IllegalArgumentException("Unknown hitreg float setting: " + key);
        };
    }

    /**
     * Record a completed, tracked fight (called from {@link Hitreg#tick()}
     * once a fight of 10 s–10 min with at least one landed hit ends).
     * Increments the lifetime counters and persists asynchronously. Gated
     * on the master enable and the "track fight statistics" toggle.
     */
    public static void addFight(long duration) {
        AuroraConfig cfg = cfg();
        if (!cfg.hitregEnabled || !cfg.hitregTrackFights) return;
        cfg.fightStatsTotalFights++;
        cfg.fightStatsPlaytimeSeconds += Math.max(0L, duration);
        AuroraConfig.save();

        // Session-side record for the Stats Overlay (fights, fight time,
        // last fight's accuracies). The swing/hit counters are still intact
        // here: Hitreg.tick clears them only on the next tick once
        // `fighting` has dropped.
        StatsTrackerFeature stats = StatsTrackerFeature.get();
        if (stats != null) {
            stats.recordFight(duration, Hitreg.yourHits, Hitreg.yourSwings, Hitreg.theirHits, Hitreg.theirSwings);
        }
    }
}
