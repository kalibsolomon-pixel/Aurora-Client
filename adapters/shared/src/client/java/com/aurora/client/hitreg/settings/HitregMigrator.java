package com.aurora.client.hitreg.settings;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.profile.ProfileManager;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * One-way, one-shot migration of the vendored BetterHitreg config file
 * ({@code <config>/hitreg.properties}) into {@link AuroraConfig}'s
 * {@code hitreg…} fields. Same shape as
 * {@code ThemeMigrator}: never throws, logs what it did, and is guarded by
 * {@link AuroraConfig#migratedHitregProperties} so it fires exactly once —
 * including on a fresh install with no file, where it simply arms the
 * guard.
 *
 * <p>Exact upstream semantics are preserved for the kept keys: keys are
 * matched on the exact property string, and {@code hitreg=0} is a real
 * value and not "off" ({@code toggle} is the switch). Only the custom
 * hitreg's keys ({@code toggle}, {@code hitreg}, {@code safeRegsOnly},
 * {@code ignoreShieldHolders}) are migrated; every other key of the file
 * belongs to a part of the mod Aurora removed (2026-11-15) and is ignored.
 *
 * <p>Call after the active profile has been applied (see the call site in
 * {@code AuroraClient}); the result is saved to both {@code aurora.json}
 * and the active profile so a later profile apply cannot undo it.
 */
public final class HitregMigrator {
    private HitregMigrator() {}

    public static final String FILE_NAME = "hitreg.properties";

    public static void runOnce() {
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.migratedHitregProperties) return;
        Logger log = AuroraClient.LOGGER;
        Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        try {
            if (Files.isRegularFile(file)) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(file)) {
                    props.load(in);
                }
                int applied = apply(props, cfg, log);
                log.info("[Hitreg] migrated {} value(s) from {} into aurora.json ({} key(s) in file)",
                        applied, file.getFileName(), props.size());
            } else {
                log.info("[Hitreg] no {} to migrate; using defaults", FILE_NAME);
            }
        } catch (Throwable t) {
            log.warn("[Hitreg] migration of {} failed; keeping defaults for anything not applied", FILE_NAME, t);
        }
        cfg.migratedHitregProperties = true;
        try {
            AuroraConfig.save();
        } catch (Throwable t) {
            log.warn("[Hitreg] failed to save config after migration", t);
        }
        try {
            ProfileManager.getInstance().saveCurrent();
        } catch (Throwable t) {
            log.warn("[Hitreg] failed to sync active profile after migration", t);
        }
    }

    /**
     * Apply a parsed properties map onto the config. Package-private and
     * side-effect free beyond {@code cfg} so it can be exercised without a
     * filesystem. Returns how many values were applied.
     */
    static int apply(Properties props, AuroraConfig cfg, Logger log) {
        int applied = 0;

        Integer hitreg = intOf(props, "hitreg", log);
        if (hitreg != null) { cfg.hitregDelayMs = Math.max(0, hitreg); applied++; }

        // Toggles — exact key match, casing included.
        for (Toggle t : Toggle.values()) {
            String raw = props.getProperty(t.key());
            if (raw == null) continue;
            t.set(Boolean.parseBoolean(raw.trim()));
            applied++;
        }
        return applied;
    }

    private static Integer intOf(Properties p, String key, Logger log) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty()) return null;
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { log.warn("[Hitreg] ignoring non-integer '{}' = '{}'", key, v); return null; }
    }
}
