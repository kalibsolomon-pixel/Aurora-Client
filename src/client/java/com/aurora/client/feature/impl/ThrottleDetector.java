package com.aurora.client.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Auto-detects how aggressively the current server bundles or throttles
 * entity movement packets, and exposes a {@link #bundlingFactor()} that the
 * hitbox renderer uses to scale its position smoothing.
 *
 * <p>Vanilla Minecraft's interpolation assumes every entity reports its
 * position once per game tick (20 Hz). Competitive/PvP servers (e.g.
 * minemen.club) frequently coalesce movement updates -- an entity may be
 * visibly moving yet have its {@code xOld}/{@code x} stay identical for 2-5
 * ticks, then jump in a single step. Rendered against that cadence, a
 * naively-interpolated hitbox "freezes" then "leaps", which the eye reads
 * as stuttering or large pixelated movement.
 *
 * <h2>How it works</h2>
 * <ul>
 *   <li>Each frame we scan the currently rendered moving entities and, for
 *       each one, compare its current tick position against the last tick
 *       position we recorded for it. If the raw position changed but the
 *       entity is genuinely in motion (not standing still, not teleporting),
 *       we measure how many ticks passed between this move and the previous
 *       one.</li>
 *   <li>The tick-gaps are pushed into a rolling ~32-sample window and we
 *       take the <b>median</b>. Median is robust to outliers (teleports,
 *       packet jitter) while still tracking the server's dominant cadence.</li>
 *   <li>The median gap is the {@code bundlingFactor} (1.0 = every tick,
 *       2.0 = every other tick, etc.).</li>
 * </ul>
 *
 * <h2>No config, no persistence</h2>
 * State is in-memory only and resets whenever the player changes world/
 * server (see {@link #onWorldChange()}) so a stale measurement from one
 * server never leaks into another. On a healthy server the factor stays at
 * 1.0 and the renderer takes the passthrough path.
 */
public final class ThrottleDetector {

    /** Rolling window of measured inter-move tick gaps. */
    private static final Deque<Integer> gapWindow = new ArrayDeque<>();
    private static final int WINDOW_SIZE = 32;

    /** Min samples before we trust the median over the optimistic default. */
    private static final int MIN_SAMPLES = 6;

    /** Per-entity last-observed (tick, x, y, z) for gap measurement. */
    private static final java.util.Map<java.util.UUID, long[]> lastSeen = new java.util.LinkedHashMap<>();
    private static final int MAX_ENTITIES = 256;

    /** Current {@link com.aurora.client.util.WorldScope} key, for reset detection. */
    private static String lastScope = "";

    /** Smoothed, clamped factor. Starts at 1.0 (assume healthy). */
    private static float smoothedFactor = 1.0f;

    private static final float MAX_FACTOR = 6.0f;

    private ThrottleDetector() {}

    /**
     * Called once per render frame by the hitbox renderer. Samples moving
     * entities, updates the rolling gap window, and decays the smoothed
     * factor toward the freshly-computed median.
     */
    public static void sample(Minecraft client) {
        if (client == null || client.level == null) return;

        // Reset per world/server change.
        String scope = com.aurora.client.util.WorldScope.current();
        if (!scope.equals(lastScope)) {
            lastScope = scope;
            gapWindow.clear();
            lastSeen.clear();
            smoothedFactor = 1.0f;
            return;
        }

        long tick = client.level.getGameTime();

        for (Entity e : client.level.entitiesForRendering()) {
            if (e == null || e.isSpectator()) continue;
            if (e == client.player) continue; // local player moves every tick client-side
            if (e.isPassenger() || e.getVehicle() != null) continue;

            double dx = e.getX() - e.xOld;
            double dy = e.getY() - e.yOld;
            double dz = e.getZ() - e.zOld;
            double movedSq = dx * dx + dy * dy + dz * dz;
            // Ignore entities that effectively didn't move this tick
            // (standing still, or a server that hasn't updated them). We're
            // only interested in the cadence of *actual* movement updates.
            if (movedSq < 1e-6) continue;

            java.util.UUID id = e.getUUID();
            long[] prev = lastSeen.get(id);
            if (prev != null) {
                long prevTick = prev[0];
                double pdx = e.getX() - Double.longBitsToDouble(prev[1]);
                double pdy = e.getY() - Double.longBitsToDouble(prev[2]);
                double pdz = e.getZ() - Double.longBitsToDouble(prev[3]);
                double stepSq = pdx * pdx + pdy * pdy + pdz * pdz;

                long gap = tick - prevTick;
                // Only count contiguous movement: small per-step displacement
                // (consistent with walking/running) and a sane tick gap.
                // This filters out teleports and one-off packet jitter.
                if (gap >= 1 && gap <= 20 && stepSq > 1e-6 && stepSq < 6.25) {
                    pushGap((int) gap);
                }
            }
            // Pack doubles into long[] to avoid a tiny dedicated record class.
            lastSeen.put(id, new long[]{ tick,
                    Double.doubleToRawLongBits(e.getX()),
                    Double.doubleToRawLongBits(e.getY()),
                    Double.doubleToRawLongBits(e.getZ()) });
            if (lastSeen.size() > MAX_ENTITIES) {
                // LinkedHashMap access-order=true isn't set here; just trim oldest.
                java.util.Iterator<java.util.UUID> it = lastSeen.keySet().iterator();
                if (it.hasNext()) { it.next(); it.remove(); }
            }
        }

        // Ease the reported factor toward the current median so it doesn't
        // jump abruptly when the window shifts.
        float target = computeMedianFactor();
        smoothedFactor += (target - smoothedFactor) * 0.1f;
    }

    private static void pushGap(int gap) {
        gapWindow.addLast(gap);
        while (gapWindow.size() > WINDOW_SIZE) gapWindow.removeFirst();
    }

    /**
     * Median tick-gap of the window, clamped to [1, MAX_FACTOR]. Returns
     * 1.0 if we don't yet have enough samples to trust the measurement.
     */
    private static float computeMedianFactor() {
        if (gapWindow.size() < MIN_SAMPLES) return 1.0f;
        int[] sorted = gapWindow.stream().mapToInt(Integer::intValue).sorted().toArray();
        int median = sorted[sorted.length / 2];
        if (median < 1) return 1.0f;
        return Math.min(MAX_FACTOR, median);
    }

    /**
     * The current bundling factor in [1.0, MAX_FACTOR]. 1.0 means the server
     * reports movement every tick (no smoothing needed); higher values mean
     * the server is throttling/bundling and the hitbox renderer should smooth
     * proportionally harder.
     */
    public static float bundlingFactor() {
        return smoothedFactor;
    }

    /**
     * Force a reset (used on disconnect / world change).
     *
     * <p>Also clears {@link #lastScope} so that reconnecting to the <i>same</i>
     * server address (scope string unchanged) still triggers a full reset on
     * the next {@link #sample} instead of being skipped by the scope-equality
     * guard. Without this, the self-healing path in {@code sample} would
     * believe the world never changed and leave {@code smoothedFactor} at
     * whatever stale value it had when disconnect happened.
     */
    public static void onWorldChange() {
        gapWindow.clear();
        lastSeen.clear();
        smoothedFactor = 1.0f;
        lastScope = "";
    }
}