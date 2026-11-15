package com.aurora.client.util;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Overlay row selection shared by the entity-body and armor/trim hit-color
 * hooks so equipment flashes in step with the body in every mode.
 *
 * <p>Vanilla's hurt flash always samples row {@code RED_OVERLAY_V}. The
 * health-gradient mode remaps that row to one of the eight gradient rows
 * (0 = 0% health .. 7 = 100%) using the snapshots captured by
 * {@code LivingEntityRendererExtractMixin}; every other mode returns the
 * vanilla row and relies on the row contents alone.
 */
public final class HitColorOverlayRows {

    private HitColorOverlayRows() {}

    /**
     * The v coordinate a hurt entity's overlay sample should use.
     *
     * @param vanillaRow the row vanilla would use ({@code OverlayTexture.v(hasRedOverlay)})
     * @return the vanilla row, or a gradient bucket row
     */
    public static int hurtRow(LivingEntityRenderState state, int vanillaRow) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.hitColorEnabled
                || cfg.hitColorMode != AuroraConfig.HitColorMode.HEALTH_GRADIENT
                || !cfg.hitColorHealthGradient) {
            return vanillaRow;
        }
        if (!state.hasRedOverlay) return vanillaRow;
        AuroraHealthSnapshots.Snapshot snap = AuroraHealthSnapshots.get(state);
        if (snap == null) return vanillaRow;
        return HitColorModes.bucketRow(
                HitColorModes.healthFraction(snap.health, snap.maxHealth, snap.absorption));
    }
}
