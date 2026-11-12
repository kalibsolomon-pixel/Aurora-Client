package com.aurora.client.mixin;

import com.aurora.client.util.SpriteAnimationClock;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GUI Item Baking (ADR 0009): counts the steps at which animated sprites can change. */
@Mixin(TextureManager.class)
public abstract class TextureManagerAnimationMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void aurora$countAnimationStep(CallbackInfo ci) {
        SpriteAnimationClock.advance();
    }
}
