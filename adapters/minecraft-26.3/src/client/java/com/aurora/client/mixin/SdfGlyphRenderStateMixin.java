package com.aurora.client.mixin;

import com.aurora.client.ui.text.SdfTextSupport;
import com.mojang.blaze3d.systems.SamplerCache;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Vanilla hardcodes NEAREST for glyphs; only Aurora's SDF pipeline needs LINEAR clamping. */
@Mixin(GlyphRenderState.class)
public abstract class SdfGlyphRenderStateMixin {
    @Shadow private TextRenderable renderable;

    @ModifyArg(method = "textureSetup", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/SamplerCache;getClampToEdge(Lcom/mojang/renderpearl/api/textures/FilterMode;)Lcom/mojang/renderpearl/api/textures/GpuSampler;"))
    private FilterMode aurora$linearSdf(FilterMode original) {
        return renderable.guiPipeline() == SdfTextSupport.pipeline() ? FilterMode.LINEAR : original;
    }
}
