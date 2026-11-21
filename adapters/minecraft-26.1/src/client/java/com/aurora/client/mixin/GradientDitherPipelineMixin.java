package com.aurora.client.mixin;

import com.aurora.client.render.GradientDitherPipelines;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Gradient Dithering's draw half: every render pass (vanilla's and Sodium's) binds a world
 * pipeline's dithered twin while the feature is on. With an Iris shader pack in use the twin
 * is never chosen.
 */
@Mixin(RenderPass.class)
public abstract class GradientDitherPipelineMixin {
    @ModifyVariable(method = "setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V", at = @At("HEAD"), argsOnly = true)
    private RenderPipeline aurora$dither(RenderPipeline pipeline) {
        return GradientDitherPipelines.select(pipeline);
    }
}
