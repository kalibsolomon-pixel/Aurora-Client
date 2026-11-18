package com.aurora.client.mixin;

import com.aurora.client.ui.text.NativeSdfText;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Vanilla hardcodes NEAREST for glyphs; only Aurora's SDF pipeline needs LINEAR clamping. */
@Mixin(GlyphRenderState.class)
public abstract class SdfGlyphRenderStateMixin {
    @ModifyArg(method = "textureSetup", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/SamplerCache;getClampToEdge(Lcom/mojang/renderpearl/api/textures/FilterMode;)Lcom/mojang/renderpearl/api/textures/GpuSampler;"),
            require = 1)
    private FilterMode aurora$linearSdf(FilterMode original) {
        return NativeSdfText.isSdf(((GlyphRenderState) (Object) this).renderable()) ? FilterMode.LINEAR : original;
    }
}
