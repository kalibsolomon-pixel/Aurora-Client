package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * View Bob Amplitude: scales the camera's head-bob strength.
 *
 * <p>{@code bobView(CameraRenderState, PoseStack)} reads the camera entity's
 * {@code bob} once and every translate and tilt it applies is proportional
 * to it, so scaling that read scales the whole bob: 0 cancels it, 2 doubles
 * it. (Until 2026-11-18 this targeted the old {@code bobView(PoseStack, float)}
 * and never applied; the View Bob Curve it also drove had no partial tick
 * left to curve and was removed.)
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererBobMixin {

    @ModifyExpressionValue(
            method = "bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(value = "FIELD",
                     target = "Lnet/minecraft/client/renderer/state/level/CameraEntityRenderState;bob:F"),
            require = 0
    )
    private float aurora$scaleBob(float bob) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.smoothAnimationsEnabled) return bob;
        return bob * (float) Math.max(0.0, cfg.viewBobAmplitude);
    }
}
