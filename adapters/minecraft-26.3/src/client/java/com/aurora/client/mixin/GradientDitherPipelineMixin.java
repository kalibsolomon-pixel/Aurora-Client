package com.aurora.client.mixin;

import com.aurora.client.render.GradientDitherPipelines;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Gradient Dithering's draw half: every pipeline lookup (vanilla's and Sodium's) resolves a
 * world pipeline to its dithered twin while the feature is on. Iris reads the same argument at
 * RETURN to substitute its pack programs, and with a pack in use the twin is never chosen.
 */
@Mixin(RenderSystem.class)
public abstract class GradientDitherPipelineMixin {
    @ModifyVariable(method = "getCompiledPipelineNullable(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;",
            at = @At("HEAD"), argsOnly = true)
    private static RenderPipeline aurora$dither(RenderPipeline pipeline) {
        return GradientDitherPipelines.select(pipeline);
    }
}
