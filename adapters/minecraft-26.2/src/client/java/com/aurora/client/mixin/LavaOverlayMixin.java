package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.PackTweaksMath;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person fire/lava screen overlay control on Minecraft 26.2:
 * hides the overlay when requested and supports custom vertical offset
 * (low fire) and opacity.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class LavaOverlayMixin {

    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void aurora$cancelFireOverlay(PoseStack matrices, SubmitNodeCollector vcp,
                                                 TextureAtlasSprite sprite, CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.fireOverlayHide
                || PackTweaksMath.clampFireOpacity(cfg.fireOverlayOpacity) <= 0.001) {
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "submitFire",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V")
    )
    private static void aurora$offsetFireHeight(SubmitNodeCollector collector, PoseStack matrices,
                                                RenderType renderType,
                                                SubmitNodeCollector.CustomGeometryRenderer renderer,
                                                Operation<Void> original) {
        double offset = PackTweaksMath.clampFireHeight(AuroraConfig.get().fireOverlayHeight);
        if (Math.abs(offset) < 1e-4) {
            original.call(collector, matrices, renderType, renderer);
            return;
        }
        matrices.pushPose();
        matrices.translate(0.0f, (float) offset, 0.0f);
        try {
            original.call(collector, matrices, renderType, renderer);
        } finally {
            matrices.popPose();
        }
    }

    @ModifyConstant(method = "buildFireQuad", constant = @Constant(intValue = -436207617))
    private static int aurora$fireOpacity(int vanillaColor) {
        double opacity = PackTweaksMath.clampFireOpacity(AuroraConfig.get().fireOverlayOpacity);
        if (Math.abs(opacity - PackTweaksMath.DEFAULT_FIRE_OPACITY) < 1e-4) return vanillaColor;
        return PackTweaksMath.fireColor(opacity);
    }
}
