package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import com.aurora.client.util.WorldScope;
import net.minecraft.client.Minecraft;

/**
 * Accumulates real wall-clock time spent in-world. Two counters are
 * maintained simultaneously:
 * <ul>
 *   <li>A global total ({@link AuroraConfig#playtimeTotalMs}).</li>
 *   <li>A per-scope map ({@link AuroraConfig#playtimePerWorld}) keyed
 *       by {@link WorldScope#current()} so each server / SP world tracks
 *       its own time.</li>
 * </ul>
 *
 * <p>Time advances only when {@code mc.level != null} and the game is
 * not paused — pause menus, the title screen, and dimension transitions
 * never count against playtime.
 *
 * <p>Persistence piggybacks on the existing {@code Aurora-ConfigSave}
 * shutdown hook plus a 60s autosave so a crash loses at most a minute.
 */
public class PlaytimeFeature implements Feature {
    public static final String ID = "playtime";

    /** Last wall-clock millis we accounted for; sentinel until first tick. */
    private long lastWallMs = -1L;
    /** Wall-clock millis of the last autosave (so we flush every 60s). */
    private long lastAutoSaveMs = 0L;
    /** How often to flush the running counters to disk while playing. */
    private static final long AUTOSAVE_INTERVAL_MS = 60_000L;

    @Override public String id() { return ID; }

    @Override
    public void onTick(Minecraft client) {
        long now = System.currentTimeMillis();

        // Reset baseline whenever play is paused / no level — we don't
        // want the gap between "level unloaded" and "level loaded again"
        // to land in either world's bucket.
        if (client == null || client.level == null || client.isPaused()) {
            lastWallMs = -1L;
            return;
        }

        if (lastWallMs < 0L) {
            lastWallMs = now;
            return;
        }

        long delta = now - lastWallMs;
        lastWallMs = now;
        // Guard against a stale baseline (e.g. system sleep) — anything
        // longer than 5s clearly isn't real play, drop it.
        if (delta <= 0L || delta > 5_000L) return;

        AuroraConfig cfg = AuroraConfig.get();
        cfg.playtimeTotalMs += delta;

        String scope = WorldScope.current();
        if (!"none".equals(scope)) {
            cfg.playtimePerWorld.merge(scope, delta, Long::sum);
        }

        if (now - lastAutoSaveMs >= AUTOSAVE_INTERVAL_MS) {
            lastAutoSaveMs = now;
            AuroraConfig.save();
        }
    }
}
