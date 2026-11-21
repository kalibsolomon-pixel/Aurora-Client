package com.aurora.client.util;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-render-state snapshot and per-entity temporal smoothing track for
 * dropped item physics ({@code ItemEntityRendererExtractMixin} →
 * {@code ItemPhysicsPose}).
 *
 * <p>Vanilla allocates a fresh {@link ItemEntityRenderState} each frame, so
 * temporal state ({@code groundT}, {@code settled}, {@code lastNanos}) is
 * tracked per {@link ItemEntity} in {@link #TRACKS} and copied into the
 * current frame's {@link ItemEntityRenderState}-keyed {@link #MAP} for the
 * submit phase. Both maps use weak keys so retired entities and render states
 * age out automatically, and {@link #clearTracks()} resets entity tracks on
 * world join/respawn.
 */
public final class AuroraItemPhysicsSnapshots {

    public static final class Snapshot {
        public final boolean onGround;
        /** {@code Vec3.lengthSqr()} of the entity's current delta movement. */
        public final double motionLenSq;
        /**
         * Smoothed ground-contact factor, 0 = fully airborne, 1 = fully
         * settled. Driven by {@link ItemPhysicsSmoothing} across frames for
         * each {@link ItemEntity}.
         */
        public final float groundT;
        /**
         * Sticky "has fully come to rest at least once" flag. Set true once
         * {@link #groundT} reaches 1 (or if first observed grounded) and then
         * carried forward for that entity so settled-then-falling items stay
         * flat instead of snapping back into an airborne tumble.
         */
        public final boolean settled;

        public Snapshot(boolean onGround, double motionLenSq, float groundT, boolean settled) {
            this.onGround = onGround;
            this.motionLenSq = motionLenSq;
            this.groundT = groundT;
            this.settled = settled;
        }
    }

    private static final class Track {
        Level level;
        float groundT;
        boolean settled;
        long lastNanos;
    }

    private static final Map<ItemEntityRenderState, Snapshot> MAP =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ItemEntity, Track> TRACKS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private AuroraItemPhysicsSnapshots() {}

    public static void updateAndPut(ItemEntity entity, ItemEntityRenderState state,
                                    boolean grounded, double motionLenSq, long nowNanos) {
        if (entity == null || state == null) return;
        float groundT;
        boolean settled;
        Level level = entity.level();
        synchronized (TRACKS) {
            Track track = TRACKS.get(entity);
            boolean hasPrev = track != null && track.level == level;
            ItemPhysicsSmoothing.Step step = ItemPhysicsSmoothing.advance(
                    hasPrev,
                    hasPrev ? track.groundT : 0f,
                    hasPrev && track.settled,
                    grounded,
                    hasPrev ? nowNanos - track.lastNanos : 0L);
            if (track == null) {
                track = new Track();
                TRACKS.put(entity, track);
            }
            track.level = level;
            track.groundT = step.groundT();
            track.settled = step.settled();
            track.lastNanos = nowNanos;
            groundT = step.groundT();
            settled = step.settled();
        }
        MAP.put(state, new Snapshot(grounded, motionLenSq, groundT, settled));
    }

    public static void put(ItemEntityRenderState state, Snapshot snap) {
        if (state != null && snap != null) MAP.put(state, snap);
    }

    public static Snapshot get(ItemEntityRenderState state) {
        return state == null ? null : MAP.get(state);
    }

    public static void clearTracks() {
        TRACKS.clear();
    }
}
