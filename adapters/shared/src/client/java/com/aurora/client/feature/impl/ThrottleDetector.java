package com.aurora.client.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Native entity sampling for the core movement-cadence policy.
 * Its result feeds entity movement smoothing, not hitbox interpolation, so it
 * samples on the client tick whenever smoothing is on, whatever the hitbox
 * settings. Cadence is measured in game ticks: one sample per tick is all it
 * needs. */
public final class ThrottleDetector {
    private static final MovementCadence CADENCE = new MovementCadence();
    private static Object lastLevel;
    private ThrottleDetector() {}

    /** End-of-client-tick hook; gated by the entity movement smoothing switches. */
    public static void tick(Minecraft client) {
        com.aurora.client.config.AuroraConfig cfg = com.aurora.client.config.AuroraConfig.get();
        if (!cfg.smoothAnimationsEnabled || !cfg.entityMovementSmoothingEnabled) return;
        sample(client);
    }

    public static void sample(Minecraft client) {
        if (client == null || client.level == null) return;
        if (client.level != lastLevel) {
            lastLevel = client.level;
            CADENCE.clear();
            return;
        }
        long tick = client.level.getGameTime();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == null || entity.isSpectator() || entity == client.player) continue;
            if (entity.isPassenger() || entity.getVehicle() != null) continue;
            double dx = entity.getX() - entity.xOld;
            double dy = entity.getY() - entity.yOld;
            double dz = entity.getZ() - entity.zOld;
            if (dx * dx + dy * dy + dz * dz < 1e-6) continue;
            CADENCE.observe(entity.getUUID(), tick, entity.getX(), entity.getY(), entity.getZ());
        }
        CADENCE.finishTick();
    }

    public static float bundlingFactor() { return CADENCE.factor(); }
    public static void onWorldChange() {
        CADENCE.clear();
        lastLevel = null;
    }
}
