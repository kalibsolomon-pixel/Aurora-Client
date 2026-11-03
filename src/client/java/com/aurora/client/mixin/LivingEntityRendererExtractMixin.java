package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.AuroraHealthSnapshots;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Health capture, riding the bridge
 * {@code extractRenderState(T, S, float)} that all
 * {@code LivingEntityRenderer} subclasses share via inheritance: records
 * health data off the live entity so {@link PlayerHealthLabelMixin} can draw
 * the numeric indicator on any living entity. The post-1.21.5 render path
 * only receives a render-state snapshot (no entity reference), so we stash
 * what we need here, keyed by render-state identity.
 *
 * <p>(A second hook here once eased the rendered body position of other
 * entities toward their tick-interpolated targets — the "Entity Movement
 * Smoothing" Animations section. It was removed 2026-11-03 by owner ruling:
 * R2-C measured it adding a median 0.114 blocks of render-position lag to
 * eligible remote entities, and the owner runs it disabled regardless.
 * Vanilla interpolation is the only path again; see AGENTS.md §8.)
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererExtractMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"),
            require = 0)
    private void aurora$captureHealth(LivingEntity entity,
                                       LivingEntityRenderState state,
                                       float partialTick,
                                       CallbackInfo ci) {
        if (entity == null || state == null) return;
        if (!AuroraConfig.get().playerHealthIndicators) return;
        AuroraHealthSnapshots.put(state, new AuroraHealthSnapshots.Snapshot(
                entity.getHealth(),
                entity.getMaxHealth(),
                entity.getAbsorptionAmount(),
                entity.getUUID()
        ));
    }
}
