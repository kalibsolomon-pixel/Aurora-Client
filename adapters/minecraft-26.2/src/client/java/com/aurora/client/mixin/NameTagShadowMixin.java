package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Enables optional drop shadow on floating nametag text on Minecraft 26.2.
 */
@Mixin(NameTagFeatureRenderer.class)
public abstract class NameTagShadowMixin {

    @ModifyArg(
            method = "prepareText",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"
            ),
            index = 4
    )
    private static boolean aurora$nameTagShadow(boolean original) {
        return AuroraConfig.get().nametagStyleTextShadow ? true : original;
    }
}
