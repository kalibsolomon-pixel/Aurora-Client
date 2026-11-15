package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.AuroraHealthSnapshots;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>Health capture</b> on the bridge {@code extractRenderState(T, S, float)}
 * that all {@code LivingEntityRenderer} subclasses share via inheritance:
 * records health data off the live entity so {@link PlayerHealthLabelMixin}
 * can draw the numeric indicator on any living entity. The post-1.21.5
 * render path only receives a render-state snapshot (no entity reference),
 * so we stash what we need here, keyed by render-state identity. The Hit
 * Color health-gradient mode rides the same snapshots (its overlay row is
 * chosen from them), and while it is active every living entity is captured
 * (including players regardless of the label filter) because the flash
 * needs their health too.
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
        AuroraConfig cfg = AuroraConfig.get();
        boolean healthLabels = cfg.playerHealthIndicators;
        boolean hitGradient = cfg.hitColorEnabled
                && cfg.hitColorMode == AuroraConfig.HitColorMode.HEALTH_GRADIENT
                && cfg.hitColorHealthGradient;
        if (!healthLabels && !hitGradient) return;
        // No snapshot means no label / vanilla-color flash (PlayerHealthLabelMixin,
        // HitColorOverlayRefreshMixin). Players are excluded for labels unless
        // asked for; the gradient mode needs their health as well, so when it is
        // active players are captured regardless.
        if (entity instanceof Player && healthLabels && !hitGradient && !cfg.playerHealthShowPlayers) return;
        AuroraHealthSnapshots.put(state, new AuroraHealthSnapshots.Snapshot(
                entity.getHealth(),
                entity.getMaxHealth(),
                entity.getAbsorptionAmount(),
                entity.getUUID()
        ));
    }
}
