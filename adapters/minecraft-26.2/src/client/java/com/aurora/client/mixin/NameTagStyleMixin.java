package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.NametagStyle;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Applies custom nametag scale and background opacity on Minecraft 26.2.
 */
@Mixin(SubmitNodeCollection.class)
public abstract class NameTagStyleMixin {

    @ModifyArgs(
            method = "submitNameTag",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V")
    )
    private void aurora$scaleNameTag(Args args) {
        double scale = AuroraConfig.get().nametagStyleScale;
        if (Math.abs(scale - 1.0) < 1e-4) return;
        args.set(0, NametagStyle.scaledComponent((Float) args.get(0), scale));
        args.set(1, NametagStyle.scaledComponent((Float) args.get(1), scale));
        args.set(2, NametagStyle.scaledComponent((Float) args.get(2), scale));
    }

    @ModifyExpressionValue(
            method = "submitNameTag",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;getBackgroundOpacity(F)F")
    )
    private float aurora$customBackgroundOpacity(float original) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.nametagStyleCustomBackground) return original;
        return (float) NametagStyle.clampOpacity(cfg.nametagStyleBgOpacity);
    }
}
