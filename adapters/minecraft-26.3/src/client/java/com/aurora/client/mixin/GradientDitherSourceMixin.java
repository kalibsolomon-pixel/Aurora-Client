package com.aurora.client.mixin;

import com.aurora.client.render.GradientDitherPipelines;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gradient Dithering's shader half: adds the dither to the world fragment shaders as they load,
 * behind a define only Aurora's twin pipelines carry, so every other pipeline compiles the
 * original shader (harmless alone). After the reload it compiles vanilla's twins.
 */
@Mixin(ShaderManager.class)
public abstract class GradientDitherSourceMixin {
    @ModifyArg(method = "loadShader(Lnet/minecraft/resources/Identifier;Lnet/minecraft/server/packs/resources/Resource;Lcom/mojang/renderpearl/api/pipeline/ShaderType;Lcom/google/common/collect/ImmutableMap$Builder;)V",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableMap$Builder;put(Ljava/lang/Object;Ljava/lang/Object;)Lcom/google/common/collect/ImmutableMap$Builder;"),
            index = 1)
    private static Object aurora$addDither(Object source, @Local(argsOnly = true) Identifier location) {
        return GradientDitherPipelines.patchSource(location, source);
    }

    @Inject(method = "apply(Lcom/mojang/renderpearl/api/device/GpuDevice;Lnet/minecraft/client/renderer/ShaderManager$PendingResults;)V", at = @At("TAIL"))
    private void aurora$compileTwins(CallbackInfo ci) {
        GradientDitherPipelines.onShadersReloaded();
    }
}
