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
     *   <li>{@code fightStatsTotalFights} / {@code fightStatsPlaytimeSeconds}
     *       — the lifetime fight counters inherited from BetterHitreg; the
     *       same kind of per-machine telemetry as playtime.</li>
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
            "playtimeTotalMs",
            "playtimePerWorld",
            "fightStatsTotalFights",
            "fightStatsPlaytimeSeconds",
            "migratedHitregProperties",
            "migratedEffectExpiryExclusions"
    );

    private ProfileFieldSet() {}

    /**
     * @return {@code true} if the named non-static instance field should be
     *         captured into / restored from a profile.
     */
    public static boolean isProfileScoped(String fieldName) {
        return !EXCLUDED.contains(fieldName);
    }
}