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
 * {@code hitreg…} / {@code fightStats…} fields. Same shape as
 * {@code ThemeMigrator}: never throws, logs what it did, and is guarded by
 * {@link AuroraConfig#migratedHitregProperties} so it fires exactly once —
 * including on a fresh install with no file, where it simply arms the
 * guard.
 *
 * <p>Exact upstream semantics are preserved: keys are matched on the exact
 * property string (the file mixes {@code safeRegsOnly} with
 * {@code RenderServerHitbox}), {@code hitreg=0} is a real value and not
 * "off" ({@code toggle} is the switch), {@code metronome} is copied as-is
 * (the reader treats values below 10 as off), muffle/sharpen are 0..1
 * decimals, colors are bare hex plus a separate 0..255 opacity. Upstream's
 * {@code Commands.setHitreg} "toggled" typo is not reproduced — the command
 * is gone. {@code tutorial}, {@code alertFights} and the five retired UI
 * chrome colors are read and deliberately dropped.
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

        // Configure
        Integer hitreg = intOf(props, "hitreg", log);
        if (hitreg != null) { cfg.hitregDelayMs = Math.max(0, hitreg); applied++; }
        Double muffle = doubleOf(props, "muffle_amount", log);
        if (muffle != null) { cfg.hitregMuffleAmount = clamp01(muffle); applied++; }
        Double sharpen = doubleOf(props, "sharpen_amount", log);
        if (sharpen != null) { cfg.hitregSharpenAmount = clamp01(sharpen); applied++; }
        Integer metronome = intOf(props, "metronome", log);
        if (metronome != null) { cfg.hitregMetronome = Math.max(0, metronome); applied++; }
        Integer grid = intOf(props, "floor_grid_size", log);
        if (grid != null) { cfg.hitregFloorGridSize = Math.max(0, grid); applied++; }

        // Tracked (lifetime telemetry)
        Integer fights = intOf(props, "total_fights", log);
        if (fights != null) { cfg.fightStatsTotalFights = Math.max(0, fights); applied++; }
        Long playtime = longOf(props, "fight_playtime_(seconds)", log);
        if (playtime != null) { cfg.fightStatsPlaytimeSeconds = Math.max(0L, playtime); applied++; }

        // Toggles — exact key match, casing included.
        for (Toggle t : Toggle.values()) {
            String raw = props.getProperty(t.key());
            if (raw == null) continue;
            t.set(Boolean.parseBoolean(raw.trim()));
            applied++;
        }

        // Colors — "<name>_color" bare hex + "<name>_opacity" 0..255 → one ARGB.
        for (Color c : Color.values()) {
            String hex = props.getProperty(c.colorKey());
            Integer opacity = intOf(props, c.opacityKey(), log);
            if (hex == null && opacity == null) continue;
            int alpha = opacity != null ? opacity : c.opacity();
            c.set(c.argb(hex, alpha));
            applied++;
        }

        // Known keys with no home, dropped on purpose.
        if (props.containsKey("tutorial")) log.debug("[Hitreg] dropped 'tutorial' (first-run chat tips are gone)");
        if (props.containsKey("alertFights")) log.debug("[Hitreg] dropped 'alertFights' (chat summary is gone; fights are tracked via hitregTrackFights)");
        for (String ui : new String[] {"background", "border", "text", "hovered", "highlighted"}) {
            if (props.containsKey(ui + "_color") || props.containsKey(ui + "_opacity")) {
                log.debug("[Hitreg] dropped UI color '{}' (the hand-drawn menu is gone)", ui);
            }
        }
        return applied;
    }

    private static Integer intOf(Properties p, String key, Logger log) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty()) return null;
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { log.warn("[Hitreg] ignoring non-integer '{}' = '{}'", key, v); return null; }
    }

    private static Long longOf(Properties p, String key, Logger log) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty()) return null;
        try { return Long.parseLong(v.trim()); }
        catch (NumberFormatException e) { log.warn("[Hitreg] ignoring non-integer '{}' = '{}'", key, v); return null; }
    }

    private static Double doubleOf(Properties p, String key, Logger log) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty()) return null;
        try { return Double.parseDouble(v.trim()); }
        catch (NumberFormatException e) { log.warn("[Hitreg] ignoring non-numeric '{}' = '{}'", key, v); return null; }
    }

    private static double clamp01(double d) {
        if (Double.isNaN(d)) return 0.0;
        return Math.max(0.0, Math.min(1.0, d));
    }
}
