package com.aurora.client.mixin;

import com.aurora.client.render.SmoothFogPipelines;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Smooth Fog's draw half (26.3). Priority 1050 ensures it runs after
 * {@link GradientDitherPipelineMixin} so both effects compose in one pass.
 */
@Mixin(value = RenderSystem.class, priority = 1050)
public abstract class SmoothFogPipelineMixin {
    @ModifyVariable(method = "getCompiledPipelineNullable(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;",
            at = @At("HEAD"), argsOnly = true)
    private static RenderPipeline aurora$smoothFog(RenderPipeline pipeline) {
        return SmoothFogPipelines.select(pipeline);
    }
}
