package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Enables optional drop shadow on floating nametag text on Minecraft 26.3.
 */
@Mixin(SubmitNodeCollection.class)
public abstract class NameTagShadowMixin {

    @ModifyArg(
            method = "nameTag",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Content$Text;<init>(FFLnet/minecraft/util/FormattedCharSequence;ZIII)V"
            ),
            index = 3
    )
    private static boolean aurora$nameTagShadow(boolean original) {
        return AuroraConfig.get().nametagStyleTextShadow ? true : original;
    }
}
