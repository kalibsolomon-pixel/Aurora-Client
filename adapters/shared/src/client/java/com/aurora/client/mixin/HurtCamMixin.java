package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.PackTweaksMath;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pack Tweaks Hurt Camera control: disable camera shake on damage altogether
 * or scale its strength independently of 1.8 Classic Damage Tilt.
 */
@Mixin(GameRenderer.class)
public abstract class HurtCamMixin {

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void aurora$maybeCancelHurtCam(CallbackInfo ci) {
        if (!AuroraConfig.get().hurtCamEnabled) {
            ci.cancel();
        }
    }

    @ModifyExpressionValue(
            method = "bobHurt",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;damageTiltStrength:D")
    )
    private double aurora$scaleHurtCam(double vanillaStrength) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.hurtCamEnabled) return 0.0;
        double scale = PackTweaksMath.clampHurtCamStrength(cfg.hurtCamStrength);
        if (Math.abs(scale - 1.0) < 1e-4) return vanillaStrength;
        return vanillaStrength * scale;
    }
}
