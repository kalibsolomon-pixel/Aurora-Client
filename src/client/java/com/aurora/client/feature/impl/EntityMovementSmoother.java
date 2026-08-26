package com.aurora.client.feature.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Frame-rate-independent position smoother for the rendered <b>body</b> of
 * other entities (mobs, players, items). This is distinct from
 * {@link HitboxPositionSmoother}, which only smooths Aurora's custom hitbox
 * wireframe.
 *
 * <p>Vanilla Minecraft renders entities by linearly interpolating between
 * {@code xOld} and {@code x} each frame using {@code tickDelta}. This assumes
 * the server reports a new position every tick and that velocity is roughly
 * constant across a tick — both of which break in practice:
 *
 * <ul>
 *   <li><b>Knockback arcs</b> — when a mob is hit, its velocity spikes for one
 *       tick then drops sharply the next (drag + gravity). Vanilla's linear
 *       lerp connects these two tick samples with a straight line, producing a
 *       visible "kink" or disjointed segment at the tick boundary as the arc
 *       changes slope. Easing the rendered position toward the tick target
 *       each frame rounds off that kink.</li>
 *   <li><b>Throttled / bundled servers</b> — competitive PvP servers coalesce
 *       movement updates so an entity's {@code xOld}/{@code x} stay identical
 *       for several ticks then jump in one step. Vanilla interpolation
 *       "freezes" then "leaps". A frame-rate-independent exponential approach
 *       glides smoothly across the gap.</li>
 * </ul>
 *
 * <p>The easing factor ({@code alpha}) is computed per-frame from the
 * frame delta time and a time constant {@code tau} that scales with both a
 * user-configured strength and the auto-detected server bundling factor from
 * {@link ThrottleDetector}. On a healthy server with low user strength, alpha
 * approaches 1.0 and the smoother is nearly a passthrough (just enough to
 * round off knockback kinks). On a throttled server or with high strength,
 * alpha drops and the rendered position glides.
 *
 * <h2>Exclusions</h2>
 * The local player is never smoothed — its position is driven by client-side
 * input every tick, so easing it would add input lag against the camera.
 * Passengers and ridden vehicles are also skipped (their position is derived
 * from the vehicle/owner).
 */
public final class EntityMovementSmoother {

    private static final int MAX_TRACKED = 512;

    public static final class State {
        public double x;
        public double y;
        public double z;
        public boolean initialized = false;
        /** Last frame time (nanos) this entity was smoothed, for dt. */
        public long lastFrameNanos = 0L;
    }

    private static final Map<UUID, State> states = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, State> eldest) {
            return size() > MAX_TRACKED;
        }
    };

    private EntityMovementSmoother() {}

    /** Returns the smoothing state for an entity, allocating lazily if absent. */
    public static State get(UUID id) {
        return states.computeIfAbsent(id, k -> new State());
    }

    public static void clearAll() {
        states.clear();
    }

    /**
     * Eases the cached position toward the target ({@code tx,ty,tz}) using a
     * frame-rate-independent exponential approach:
     *
     * <pre>{@code
     * alpha = 1 - exp(-dt / tau)
     * pos += (target - pos) * alpha
     * }</pre>
     *
     * <p>{@code tau} (the time constant, in seconds) controls how long the
     * ease takes: larger tau = slower, smoother glide; smaller tau = snappier
     * tracking. The caller supplies tau derived from user strength and the
     * detected server bundling factor.
     *
     * <p>Snaps instantly (copies target verbatim) on:
     * <ul>
     *   <li>first observation of an entity (so it doesn't slide in from origin)</li>
     *   <li>teleport-class jumps ({@code distance > snapThreshold}) — respawn,
     *       dimension change, ender pearl, etc.</li>
     * </ul>
     *
     * @param s             the per-entity state (from {@link #get})
     * @param tx            target X (already tick-interpolated by vanilla)
     * @param ty            target Y
     * @param tz            target Z
     * @param tau           time constant in seconds; smaller = snappier
     * @param snapThreshold blocks; jumps larger than this snap instead of ease
     * @return {@code true} if the position was eased (caller should write
     *         {@code s.x/y/z} back into the render state), {@code false} if it
     *         snapped (target copied verbatim — still fine to write back)
     */
    public static boolean smooth(State s, double tx, double ty, double tz,
                                 double tau, double snapThreshold) {
        if (!s.initialized) {
            s.x = tx; s.y = ty; s.z = tz;
            s.initialized = true;
            s.lastFrameNanos = System.nanoTime();
            return false;
        }

        long now = System.nanoTime();
        double dtSec;
        if (s.lastFrameNanos == 0L) {
            dtSec = 0.05; // one-tick fallback
        } else {
            dtSec = (now - s.lastFrameNanos) / 1_000_000_000.0;
            // Clamp so a pause/hitch doesn't over-shoot.
            if (dtSec > 0.25) dtSec = 0.25;
            if (dtSec < 1e-5) dtSec = 1e-5;
        }
        s.lastFrameNanos = now;

        double dx = tx - s.x;
        double dy = ty - s.y;
        double dz = tz - s.z;
        double distSq = dx * dx + dy * dy + dz * dz;

        // Teleport / respawn / dimension change — snap, don't ease.
        if (distSq > snapThreshold * snapThreshold) {
            s.x = tx; s.y = ty; s.z = tz;
            return false;
        }

        // Frame-rate-independent exponential approach.
        double safeTau = Math.max(tau, 1e-4);
        double alpha = 1.0 - Math.exp(-dtSec / safeTau);
        if (alpha > 1.0) alpha = 1.0;
        if (alpha < 0.0) alpha = 0.0;

        s.x += dx * alpha;
        s.y += dy * alpha;
        s.z += dz * alpha;
        return true;
    }
}