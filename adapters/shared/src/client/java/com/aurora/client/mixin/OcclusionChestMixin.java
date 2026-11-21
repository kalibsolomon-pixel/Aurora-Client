package com.aurora.client.mixin;

import com.aurora.client.performance.EntityOcclusion;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Level extraction skips a block entity that opaque terrain certainly hides ({@link EntityOcclusion}). */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class OcclusionChestMixin {
    @ModifyExpressionValue(method = "tryExtractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;shouldRender(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/phys/Vec3;)Z"))
    private boolean aurora$omitOccludedBlockEntity(boolean visible, @Local(argsOnly = true) BlockEntity blockEntity) {
        return visible && !EntityOcclusion.hideChest(blockEntity);
    }
}
