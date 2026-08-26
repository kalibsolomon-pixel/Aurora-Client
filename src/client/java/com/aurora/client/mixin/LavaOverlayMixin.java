package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When the user has Lava Clarity enabled AND opted to hide the overlay,
 * cancels the orange screen overlay entirely so they can see clearly
 * through lava (in combination with extended fog).
 *
 * <p>Note: vanilla's {@code renderFire} draws the orange overlay for both
 * fire and lava — they share the same draw path. Cancelling here also
 * hides the on-fire screen tint, but only when both Lava Clarity is
 * enabled AND Hide Overlay is on.
 *
 * <p>1.21.11: renamed from {@code renderFireOverlay} to {@code renderFire};
 * a {@code TextureAtlasSprite} parameter was added.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class LavaOverlayMixin {

    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true, require = 1)
    private static void aurora$cancelLavaOverlay(PoseStack matrices, MultiBufferSource vcp, TextureAtlasSprite sprite, CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.lavaClarityEnabled) return;
        if (!cfg.lavaHideOverlay) return;
        ci.cancel();
    }
}