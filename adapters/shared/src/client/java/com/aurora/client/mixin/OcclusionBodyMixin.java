package com.aurora.client.mixin;

import com.aurora.client.performance.EntityOcclusion;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Level extraction skips an entity that opaque terrain certainly hides ({@link EntityOcclusion}). */
@Mixin(EntityRenderDispatcher.class)
public abstract class OcclusionBodyMixin {
    @ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
    private boolean aurora$omitOccludedEntity(boolean visible, @Local(argsOnly = true) Entity entity) {
        return visible && !EntityOcclusion.hideBody(entity);
    }
}
