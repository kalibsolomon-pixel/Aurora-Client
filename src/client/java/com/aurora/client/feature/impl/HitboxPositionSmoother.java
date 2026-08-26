package com.aurora.client.feature.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-entity smoothed render position for Aurora's custom hitbox
 * visualization. Keyed by entity UUID, bounded with an LRU so memory stays
 * sane in busy lobbies.
 *
 * <p>Unlike vanilla tick interpolation ({@code Mth.lerp(tickDelta, xOld, x)}),
 * which assumes the server reports every entity's position each tick, this
 * eases the rendered position toward the tick-interpolated target every
 * frame using a frame-rate-independent exponential approach. That absorbs
 * the "freeze for N ticks then leap" cadence produced by servers whose
 * anticheat bundles or throttles entity movement packets (e.g. competitive
 * PvP servers such as minemen.club), where an entity's {@code xOld}/{@code x}
 * only change every few ticks.
 *
 * <p>The easing factor ({@code alpha}) and teleport-snap threshold are
 * supplied by the caller based on the auto-detected packet-bundling factor
 * (see {@link PacketBundlingDetector}), so no manual tuning or config is
 * required. On a healthy server the factor stays ~1.0, alpha reaches 1.0,
 * and {@link #smooth} is a pure passthrough — no visual difference from the
 * vanilla interpolation path.
 */
public final class HitboxPositionSmoother {

    private static final int MAX_TRACKED = 512;

    public static final class State {
        public double x;
        public double y;
        public double z;
        public boolean initialized = false;
    }

    private static final Map<UUID, State> states = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, State> eldest) {
            return size() > MAX_TRACKED;
        }
    };

    private HitboxPositionSmoother() {}

    /** Returns the smoothing state for an entity, allocating lazily if absent. */
    public static State get(UUID id) {
        return states.computeIfAbsent(id, k -> new State());
    }

    public static void clearAll() {
        states.clear();
    }

    /**
     * Eases the cached position toward the target ({@code tx,ty,tz}) by
     * {@code alpha} (0..1), snapping instantly on teleport-class jumps
     * (distance > {@code snapThreshold}) and on first observation of an
     * entity. When {@code alpha >= 1} the target is copied verbatim.
     *
     * @param s             the per-entity state (from {@link #get})
     * @param tx            target X (already tick-interpolated)
     * @param ty            target Y
     * @param tz            target Z
     * @param alpha         frame-rate-independent easing factor; 1.0 = snap
     * @param snapThreshold blocks; jumps larger than this snap instead of ease
     */
    public static void smooth(State s, double tx, double ty, double tz,
                              double alpha, double snapThreshold) {
        if (!s.initialized) {
            s.x = tx; s.y = ty; s.z = tz;
            s.initialized = true;
            return;
        }
        if (alpha >= 1.0) {
            s.x = tx; s.y = ty; s.z = tz;
            return;
        }
        double dx = tx - s.x;
        double dy = ty - s.y;
        double dz = tz - s.z;
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq > snapThreshold * snapThreshold) {
            // Teleport / respawn / dimension change — snap, don't ease.
            s.x = tx; s.y = ty; s.z = tz;
        } else {
            s.x += dx * alpha;
            s.y += dy * alpha;
            s.z += dz * alpha;
        }
    }
}