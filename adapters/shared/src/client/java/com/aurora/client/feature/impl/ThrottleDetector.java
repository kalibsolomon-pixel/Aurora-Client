package com.aurora.client.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Native entity sampling for the core movement-cadence policy.
 * Its result feeds entity movement smoothing, not hitbox interpolation. */
public final class ThrottleDetector {
    private static final MovementCadence CADENCE = new MovementCadence();
    private static String lastScope = "";
    private ThrottleDetector() {}

    public static void sample(Minecraft client) {
        if (client == null || client.level == null) return;
        String scope = com.aurora.client.util.WorldScope.current();
        if (!scope.equals(lastScope)) {
            lastScope = scope;
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
        CADENCE.finishFrame();
    }

    public static float bundlingFactor() { return CADENCE.factor(); }
    public static void onWorldChange() {
        CADENCE.clear();
        lastScope = "";
    }
}
