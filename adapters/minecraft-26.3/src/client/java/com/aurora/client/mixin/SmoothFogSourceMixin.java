package com.aurora.client.mixin;

import com.aurora.client.render.SmoothFogPipelines;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smooth Fog's shader half (26.3): patches {@code linear_fog_value} behind {@code AURORA_SMOOTH_FOG}. */
@Mixin(ShaderManager.class)
public abstract class SmoothFogSourceMixin {
    @ModifyArg(method = "loadShader(Lnet/minecraft/resources/Identifier;Lnet/minecraft/server/packs/resources/Resource;Lcom/mojang/renderpearl/api/pipeline/ShaderType;Lcom/google/common/collect/ImmutableMap$Builder;)V",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableMap$Builder;put(Ljava/lang/Object;Ljava/lang/Object;)Lcom/google/common/collect/ImmutableMap$Builder;"),
            index = 1)
    private static Object aurora$addSmoothFog(Object source, @Local(argsOnly = true) Identifier location) {
        return SmoothFogPipelines.patchSource(location, source);
    }

    @Inject(method = "apply(Lcom/mojang/renderpearl/api/device/GpuDevice;Lnet/minecraft/client/renderer/ShaderManager$PendingResults;)V", at = @At("TAIL"))
    private void aurora$compileSmoothFogTwins(CallbackInfo ci) {
        SmoothFogPipelines.onShadersReloaded();
    }
}
