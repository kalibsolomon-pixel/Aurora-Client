package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.PackTweaksMath;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person fire/lava screen overlay control on Minecraft 26.1.2:
 * hides the overlay when requested (Pack Tweaks Hide Fire Overlay)
 * and supports custom vertical offset (low fire) and opacity.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class LavaOverlayMixin {

    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void aurora$cancelFireOverlay(PoseStack matrices, MultiBufferSource vcp,
                                                 TextureAtlasSprite sprite, CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.fireOverlayHide
                || PackTweaksMath.clampFireOpacity(cfg.fireOverlayOpacity) <= 0.001) {
            ci.cancel();
        }
    }

    @ModifyConstant(method = "renderFire", constant = @Constant(floatValue = -0.3f))
    private static float aurora$fireHeight(float vanillaY) {
        double offset = PackTweaksMath.clampFireHeight(AuroraConfig.get().fireOverlayHeight);
        return Math.abs(offset) < 1e-4 ? vanillaY : vanillaY + (float) offset;
    }

    @ModifyConstant(method = "renderFire", constant = @Constant(floatValue = 0.9f))
    private static float aurora$fireOpacity(float vanillaAlpha) {
        return (float) PackTweaksMath.clampFireOpacity(AuroraConfig.get().fireOverlayOpacity);
    }
}
