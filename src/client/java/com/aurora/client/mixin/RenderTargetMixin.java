package com.aurora.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Empty mixin kept for config compatibility. The previous accessor-based
 * version targeted texture fields that caused crashes on MC 1.21.11's
 * rewritten GPU API. FSR now uses a post-process approach that doesn't
 * need to modify RenderTarget at all.
 */
@Mixin(RenderTarget.class)
public class RenderTargetMixin {
    // intentionally empty — see class Javadoc.
}