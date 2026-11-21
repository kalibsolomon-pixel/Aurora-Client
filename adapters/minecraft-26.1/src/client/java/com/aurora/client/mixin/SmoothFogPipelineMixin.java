package com.aurora.client.mixin;

import com.aurora.client.render.SmoothFogPipelines;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Smooth Fog's draw half (26.1). Priority 1050 ensures it runs after
 * {@link GradientDitherPipelineMixin} so both effects compose in one pass.
 */
@Mixin(value = RenderPass.class, priority = 1050)
public abstract class SmoothFogPipelineMixin {
    @ModifyVariable(method = "setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V", at = @At("HEAD"), argsOnly = true)
    private RenderPipeline aurora$smoothFog(RenderPipeline pipeline) {
        return SmoothFogPipelines.select(pipeline);
    }
}
