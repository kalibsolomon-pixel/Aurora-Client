package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.AuroraItemPhysicsSnapshots;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the {@code ItemEntity}'s ground-contact flag and current
 * motion length-squared during state extraction so
 * {@link ItemEntityRendererSubmitMixin} can adjust the bob translate
 * and spin quaternion later in {@code submit}. The post-1.21.5 render
 * path only hands the renderer an {@link ItemEntityRenderState}
 * snapshot, so we stash what we need in a weak-keyed side map.
 *
 * <p>Hooked at TAIL of
 * {@code extractRenderState(ItemEntity, ItemEntityRenderState, float)}.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererExtractMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL"),
            require = 0)
    private void aurora$capturePhysics(ItemEntity entity,
                                       ItemEntityRenderState state,
                                       float partialTick,
                                       CallbackInfo ci) {
        if (entity == null || state == null) return;
        if (!AuroraConfig.get().itemPhysicsEnabled) return;
        Vec3 v = entity.getDeltaMovement();
        AuroraItemPhysicsSnapshots.updateAndPut(
                entity, state, entity.onGround(), v.lengthSqr(), System.nanoTime());
    }
}
