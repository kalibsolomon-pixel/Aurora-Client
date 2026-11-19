package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Classic Damage Tilt: vanilla's own hurt roll, drawn the way 1.7/1.8 drew it.
 *
 * <p>{@code bobHurt} turns the view by the entity's {@code hurtDir}, rolls it,
 * and turns it back, so modern clients roll toward the hit. 1.8 reset that
 * angle to zero on every hurt it was told about, so on a server the camera
 * always rolled the same way. Reading the angle as zero restores that; the
 * roll's size and timing stay vanilla's.
 *
 * <p>Strength multiplies vanilla's own Damage Tilt accessibility option, so a
 * player who turned that down keeps it down. (Until 2026-11-18 this mixin
 * added a second, knockback-guessed roll on top of the vanilla one.)
 */
@Mixin(GameRenderer.class)
public abstract class DamageTiltMixin {

    @ModifyExpressionValue(method = "bobHurt", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/state/level/CameraEntityRenderState;hurtDir:F"))
    private float aurora$fixedHurtDirection(float hurtDir) {
        return aurora$classic() ? 0.0F : hurtDir;
    }

    @ModifyExpressionValue(method = "bobHurt", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;damageTiltStrength:D"))
    private double aurora$scaleTilt(double vanillaStrength) {
        return aurora$classic() ? vanillaStrength * Math.max(0.0, AuroraConfig.get().damageTiltStrength) : vanillaStrength;
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean aurora$classic() {
        AuroraConfig cfg = AuroraConfig.get();
        return cfg.smoothAnimationsEnabled && cfg.damageTiltEnabled;
    }
}
