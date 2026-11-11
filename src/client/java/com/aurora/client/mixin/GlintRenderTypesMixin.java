package com.aurora.client.mixin;

import com.aurora.client.util.GlintRenderTypes;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep each native base/glint pass, including OIT, and substitute the mask shader only when enabled. */
@Mixin(RenderType.class)
public abstract class GlintRenderTypesMixin {
    @ModifyReturnValue(method = "prepare", at = @At("RETURN"))
    private PreparedRenderType aurora$maskGlint(PreparedRenderType original) {
        return GlintRenderTypes.alternative(original);
    }
}
