package com.aurora.client.util;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Per-entity-render-state snapshots of health data, captured by Aurora's
 * extractRenderState mixin so the render hook can label health on any
 * {@link net.minecraft.world.entity.LivingEntity} without needing access
 * to the live entity reference inside the (post-1.21.5) state-only render
 * path.
 *
 * <p>Backed by a {@link WeakHashMap} so retired render states age out as
 * the renderer releases them. The map is read on the render thread only;
 * wrapping in {@link Collections#synchronizedMap} is defensive against
 * Fabric API hooks that may touch it from other threads.
 */
public final class AuroraHealthSnapshots {

    public static final class Snapshot {
        public final float health;
        public final float maxHealth;
        public final float absorption;
        public final UUID uuid;

        public Snapshot(float health, float maxHealth, float absorption, UUID uuid) {
            this.health = health;
            this.maxHealth = maxHealth;
            this.absorption = absorption;
            this.uuid = uuid;
        }
    }

    private static final Map<EntityRenderState, Snapshot> MAP =
            Collections.synchronizedMap(new WeakHashMap<>());

    private AuroraHealthSnapshots() {}

    public static void put(EntityRenderState state, Snapshot snap) {
        if (state != null && snap != null) MAP.put(state, snap);
    }

    public static Snapshot get(EntityRenderState state) {
        return state == null ? null : MAP.get(state);
    }
}
