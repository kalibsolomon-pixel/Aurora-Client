package com.aurora.client.config.profile;

import java.util.Set;

/**
 * Curation policy: which {@link com.aurora.client.config.AuroraConfig}
 * fields belong to a profile vs. stay global.
 *
 * <p>Per the agreed scope, <b>everything is profile-scoped</b> (module
 * enables, all setting values, keybinds, theme colors/fonts, HUD
 * positions, particle maps, item-scale maps, waypoints) <b>except</b> a
 * small exclusion list of fields that are either self-referential or
 * non-configuration telemetry.
 *
 * <p>Static and final fields are also implicitly excluded — the
 * snapshot/restore loop in {@link ProfileManager} only walks non-static,
 * non-final public instance fields, mirroring the existing
 * {@code AuroraConfig.resetByPrefix} reflection logic.
 */
public final class ProfileFieldSet {

    /**
     * Non-static instance fields that must NOT be captured into a profile
     * (and therefore are not reset when switching profiles).
     *
     * <ul>
     *   <li>{@code activeProfile} — the pointer to which profile is loaded;
     *       restoring it would be circular (switching to a profile that
     *       rewrites which profile is active).</li>
     *   <li>{@code playtimeTotalMs} / {@code playtimePerWorld} — cumulative
     *       telemetry accumulators, not user configuration. Switching a
     *       profile must never zero out or overwrite playtime.</li>
     *   <li>{@code migratedHitregProperties} — the one-shot guard for the
     *       hitreg.properties migration. Resetting it through a profile
     *       apply would re-run the migration over the user's edits.</li>
     *   <li>{@code migratedEffectExpiryExclusions} — same discipline, for
     *       the exclusion-set → inclusion-list migration: a profile apply
     *       resetting the guard to false would point the migrator at a
     *       possibly-empty legacy set and flatten the profile's own curated
     *       inclusion list on every switch.</li>
     * </ul>
     */
    public static final Set<String> EXCLUDED = Set.of(
            "activeProfile",
            "moduleGridView",
            "playtimeTotalMs",
            "playtimePerWorld",
            "migratedHitregProperties",
            "migratedEffectExpiryExclusions",
            // "Separate controls" decides whether profiles carry bindings, so it
            // must not itself change with the profile (2026-11-12).
            "profilesOwnControls",
            "vanillaKeyModifiers",
            "searchInstant",
            "searchTypoTolerance"
    );

    /**
     * Fields Aurora has removed (3.5.1, 2026-11-15: entity movement smoothing and the
     * Better Hitreg parts other than its custom delay; 3.8.1, 2026-11-22: Smooth Camera; 3.9.0: Lava Clarity's fire switch).
     * Profiles saved before still carry them; restore skips them quietly, because nothing
     * is wrong with such a profile. Any other unknown field still logs a warning. The
     * reload overlay and title fade switches were perf* fields, which profiles never held.
     */
    public static final Set<String> RETIRED = Set.of(
            "entityMovementSmoothingEnabled", "entityMovementSmoothingStrength",
            "fightStatsPlaytimeSeconds", "fightStatsTotalFights", "hitregAlertDelays",
            "hitregAlertGhosts", "hitregAlertInconsistencies", "hitregColorCrossFar",
            "hitregColorCrossFarWithHitbox", "hitregColorCrossNear",
            "hitregColorCrossNearWithHitbox", "hitregColorFloor", "hitregColorGrid",
            "hitregColorHitboxFar", "hitregColorHitboxNear", "hitregColorJumpReset",
            "hitregColorPerfectHit", "hitregColorServerHitbox", "hitregColorTheirJumpFar",
            "hitregColorTheirJumpNear", "hitregColorTheirReachFar", "hitregColorTheirReachNear",
            "hitregColorYourJumpFar", "hitregColorYourJumpNear", "hitregColorYourReachFar",
            "hitregColorYourReachNear", "hitregFloorGridSize", "hitregMetronome",
            "hitregMuffleAmount", "hitregScoreLeftKey", "hitregScoreResetKey",
            "hitregScoreRightKey", "hitregScoreSendKey", "hitregSettingsKey",
            "hitregSharpenAmount", "hitregSwitchHandKey", "hitregTrackFights", "hitregVoidWorld",
            "lavaHideOverlay", "smoothCamera", "smoothCameraStrength"
    );

    /** @return {@code true} for a field a later Aurora removed ({@link #RETIRED}). */
    public static boolean isRetired(String fieldName) {
        return RETIRED.contains(fieldName);
    }

    /** Prefix of the performance fields (ADR 0009), all kept out of profiles. */
    public static final String PERFORMANCE_PREFIX = "perf";

    private ProfileFieldSet() {}

    /**
     * @return {@code true} if the named non-static instance field should be
     *         captured into / restored from a profile.
     */
    public static boolean isProfileScoped(String fieldName) {
        // Performance switches (ADR 0009) describe the machine, not a loadout.
        return !EXCLUDED.contains(fieldName) && !fieldName.startsWith(PERFORMANCE_PREFIX);
    }
}