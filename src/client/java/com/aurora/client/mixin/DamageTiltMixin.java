package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reintroduces the classic 1.7/1.8-era damage tilt — a brief roll of the
 * camera in response to being hit. Vanilla animates a small forward pitch
 * on damage; this hooks in just after that and adds a roll component for a
 * more pronounced "you got hit" feedback.
 *
 * <p>The roll direction is <b>directional</b> (classic behaviour): we derive
 * a sign from the player's horizontal knockback velocity relative to their
 * facing, so hits from the right roll the camera one way and hits from the
 * left roll it the other. If a clean direction can't be read (e.g. the
 * player wasn't knocked sideways), the roll falls back to a fixed direction
 * so the effect never silently disappears.
 */
@Mixin(GameRenderer.class)
public abstract class DamageTiltMixin {

    @Inject(
            method = "bobHurt",
            at = @At("TAIL"),
            require = 1
    )
    private void aurora$applyRollTilt(PoseStack matrices, float tickDelta, CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.smoothAnimationsEnabled || !cfg.damageTiltEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() == null) return;
        if (!(mc.getCameraEntity() instanceof LivingEntity entity)) return;

        float hurtTime = entity.hurtTime - tickDelta;
        if (hurtTime <= 0f) return;

        // Vanilla maxHurtTime is 10
        float t = hurtTime / 10f;
        // Fast in, slow out
        t = t * t;
        // Roll magnitude — peaks ~5° at hurtTime==10, decays fast
        float magnitude = 5f * t * (float) cfg.damageTiltStrength;

        // Determine roll direction (sign) from knockback relative to facing.
        // Hits coming from the player's right knock them left, and vice-versa.
        // We compute the lateral (strafe) component of velocity and use its
        // sign to mirror that: roll toward the side the hit came from.
        float sign = computeRollSign(entity);

        float roll = magnitude * sign;
        matrices.mulPose(Axis.ZP.rotationDegrees(roll));
    }

    /**
     * Compute the sign (+1/-1) for the camera roll based on which side the
     * damage came from. Falls back to +1 when no clear direction can be
     * derived, so the effect is always visible.
     */
    private static float computeRollSign(LivingEntity entity) {
        try {
            Vec3 vel = entity.getDeltaMovement();
            double dx = vel.x;
            double dz = vel.z;
            double hSpeed = Math.sqrt(dx * dx + dz * dz);
            // Only use velocity as a direction cue if there's meaningful
            // knockback motion; otherwise default sign.
            if (hSpeed < 1e-4) return 1f;

            // Player's right vector (Mojang yaw convention: 0 = +Z, increasing
            // clockwise viewed from above). rightX = cos(yaw), rightZ = -sin(yaw).
            float yaw = entity.getYRot() * ((float) Math.PI / 180f);
            float rightX = (float) (Math.cos(yaw));
            float rightZ = (float) (-Math.sin(yaw));

            // Project knockback velocity onto the right vector.
            // Positive ⇒ knocked to the player's right ⇒ hit came from the left.
            double lateral = (dx * rightX + dz * rightZ) / hSpeed;

            if (lateral > 0.15) return -1f;   // knocked right ⇒ hit from left ⇒ roll left
            if (lateral < -0.15) return 1f;   // knocked left  ⇒ hit from right ⇒ roll right
        } catch (Throwable ignored) {
            // Never let a tilt-direction computation crash the render path.
        }
        return 1f;
    }
}
