package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Enables optional drop shadow on floating nametag text on Minecraft 26.1.2.
 */
@Mixin(NameTagFeatureRenderer.class)
public abstract class NameTagShadowMixin {

    @ModifyArg(
            method = "renderTranslucent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"
            ),
            index = 4
    )
    private boolean aurora$nameTagShadow(boolean original) {
        return AuroraConfig.get().nametagStyleTextShadow ? true : original;
    }
}
