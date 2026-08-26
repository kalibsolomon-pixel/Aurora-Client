package com.aurora.client.util;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-render-state snapshot of the small bits of physics-relevant
 * {@code ItemEntity} data the renderer needs but the vanilla
 * {@link ItemEntityRenderState} does not carry: ground contact and
 * motion magnitude.
 *
 * <p>Mirrors the storage pattern of {@link AuroraHealthSnapshots} —
 * a {@link WeakHashMap} keyed by render-state identity so retired
 * states age out as the renderer releases them, wrapped in a
 * {@link Collections#synchronizedMap} for defensive thread-safety
 * (entity rendering is single-threaded today but extraction hooks
 * may not be).
 */
public final class AuroraItemPhysicsSnapshots {

    public static final class Snapshot {
        public final boolean onGround;
        /** {@code Vec3.lengthSqr()} of the entity's current delta movement. */
        public final double motionLenSq;
        /**
         * Smoothed ground-contact factor, 0 = fully airborne, 1 = fully
         * settled. Driven each extract call by
         * {@code com.aurora.client.mixin.ItemEntityRendererExtractMixin}
         * so the renderer can lerp/slerp the flat-on-ground transform
         * instead of snapping at the contact frame.
         */
        public final float groundT;
        /**
         * Sticky "has fully come to rest at least once" flag. Set true the
         * first frame {@link #groundT} reaches 1 and then carried forward
         * forever. The submit mixin uses it to suppress the airborne tumble
         * for items that were lying on the ground and then started falling
         * again (e.g. the block beneath them broke) — those must keep their
         * flat pose rather than snapping back into a spin, which read as a
         * lerping glitch. Only genuinely fresh drops (never grounded) tumble.
         */
        public final boolean settled;

        public Snapshot(boolean onGround, double motionLenSq, float groundT, boolean settled) {
            this.onGround = onGround;
            this.motionLenSq = motionLenSq;
            this.groundT = groundT;
            this.settled = settled;
        }
    }

    private static final Map<ItemEntityRenderState, Snapshot> MAP =
            Collections.synchronizedMap(new WeakHashMap<>());

    private AuroraItemPhysicsSnapshots() {}

    public static void put(ItemEntityRenderState state, Snapshot snap) {
        if (state != null && snap != null) MAP.put(state, snap);
    }

    public static Snapshot get(ItemEntityRenderState state) {
        return state == null ? null : MAP.get(state);
    }
}
