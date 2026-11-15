package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.EntityMovementSmoother;
import com.aurora.client.feature.impl.ThrottleDetector;
import com.aurora.client.util.AuroraHealthSnapshots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two unrelated hooks both riding the bridge
 * {@code extractRenderState(T, S, float)} that all
 * {@code LivingEntityRenderer} subclasses share via inheritance:
 *
 * <ol>
 *   <li><b>Health capture</b> — records health data off the live entity so
 *       {@link PlayerHealthLabelMixin} can draw the numeric indicator on any
 *       living entity. The post-1.21.5 render path only receives a
 *       render-state snapshot (no entity reference), so we stash what we
 *       need here, keyed by render-state identity. The Hit Color
 *       health-gradient mode rides the same snapshots (its overlay row is
 *       chosen from them), and while it is active every living entity is
 *       captured — including players regardless of the label filter —
 *       because the flash needs their health too.</li>
 *
 *   <li><b>Movement smoothing</b> — eases the entity's rendered position
 *       toward the tick-interpolated target every frame using a
 *       frame-rate-independent exponential approach. This rounds off the
 *       per-tick "kinks" that make knockback arcs look disjointed (velocity
 *       changes abruptly between ticks, and vanilla's linear lerp connects
 *       the samples with a straight line). It also absorbs the
 *       freeze-then-leap cadence of servers that bundle/throttle movement
 *       packets. The smoothing strength auto-scales with the detected
 *       server bundling factor. The local player is always skipped.</li>
 * </ol>
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

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"),
            require = 0)
    private void aurora$smoothMovement(LivingEntity entity,
                                       LivingEntityRenderState state,
                                       float partialTick,
                                       CallbackInfo ci) {
        if (entity == null || state == null) return;
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.smoothAnimationsEnabled || !cfg.entityMovementSmoothingEnabled) return;

        // Never smooth the local player or a spectated camera entity — the
        // camera follows them, so easing them would lag the view.
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player || entity == mc.getCameraEntity()) return;

        // Skip passengers and ridden vehicles — a rider's position is derived
        // from its vehicle, so easing either one alone detaches the pair.
        if (entity.isPassenger() || entity.isVehicle()) return;

        // tau (the time constant) scales with both user strength and the
        // auto-detected server bundling factor. Larger tau = slower, smoother
        // glide. On a healthy server (factor ~1.0) the tau stays low so we
        // just round off knockback kinks; on a throttled server it grows to
        // bridge the frozen-tick gaps.
        float bundling = ThrottleDetector.bundlingFactor();
        // User strength 0..1 maps to a base tau range. 0.0 ≈ passthrough-
        // ish (tiny tau, near-instant tracking), 1.0 ≈ very smooth glide.
        double baseTau = 0.02 + cfg.entityMovementSmoothingStrength * 0.12;
        double tau = baseTau * Math.max(1.0, bundling);

        // Snap on teleport-class jumps (respawn, dimension change, ender
        // pearl, etc.) so we don't slide across the world.
        double snap = 8.0;

        EntityMovementSmoother.State s = EntityMovementSmoother.get(entity.getUUID());
        EntityMovementSmoother.smooth(s, state.x, state.y, state.z, tau, snap);

        // Write the eased position back into the render state for vanilla to
        // render. The camera-relative offset is preserved because we only
        // touched the world-space position; the renderer subtracts the camera
        // position itself downstream.
        state.x = s.x;
        state.y = s.y;
        state.z = s.z;
    }
}
