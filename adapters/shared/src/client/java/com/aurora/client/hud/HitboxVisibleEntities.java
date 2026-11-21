package com.aurora.client.hud;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.world.entity.Entity;

import java.util.Arrays;

/**
 * Per-frame list of entities actually extracted by the main world renderer
 * (after vanilla render-distance/frustum culling, Sodium section culling,
 * EntityCulling, and Aurora's {@code perfEntityOcclusion}).
 *
 * <p>Populated on the render thread by {@code HitboxVisibleEntitiesMixin}
 * during {@code extractVisibleEntities} so {@link HitboxRenderer} matches
 * vanilla {@code F3+B} visibility without re-scanning every loaded entity or
 * revealing occluded entities behind walls. Reuses a single flat array with
 * zero per-frame allocation in steady state.
 */
public final class HitboxVisibleEntities {
    private static Entity[] entities = new Entity[64];
    private static int count;
    private static boolean extracting;
    private static boolean hasSnapshot;

    private HitboxVisibleEntities() {}

    public static void beginFrame() {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.hitboxEnabled && !cfg.hitboxTargetEnabled) {
            if (count > 0 || hasSnapshot || extracting) clear();
            return;
        }
        if (count > 0) Arrays.fill(entities, 0, count, null);
        count = 0;
        extracting = true;
        hasSnapshot = false;
    }

    public static void record(Entity entity) {
        if (!extracting || entity == null) return;
        if (count == entities.length) {
            entities = Arrays.copyOf(entities, entities.length * 2);
        }
        entities[count++] = entity;
    }

    public static void endFrame() {
        if (extracting) {
            extracting = false;
            hasSnapshot = true;
        }
    }

    public static boolean hasSnapshot() {
        return hasSnapshot;
    }

    public static int count() {
        return count;
    }

    public static Entity get(int index) {
        return entities[index];
    }

    public static void clear() {
        if (count > 0) Arrays.fill(entities, 0, count, null);
        count = 0;
        extracting = false;
        hasSnapshot = false;
    }
}
