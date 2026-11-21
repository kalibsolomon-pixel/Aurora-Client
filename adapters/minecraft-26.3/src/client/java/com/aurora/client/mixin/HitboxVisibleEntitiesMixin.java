package com.aurora.client.mixin;

import com.aurora.client.hud.HitboxVisibleEntities;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Records entities that actually survive main-world visibility culling
 * (vanilla, Sodium, EntityCulling, and Aurora occlusion) into
 * {@link HitboxVisibleEntities}.
 */
@Mixin(LevelExtractor.class)
public abstract class HitboxVisibleEntitiesMixin {
    @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    private void aurora$beginVisibleHitboxes(Camera camera, Frustum frustum, DeltaTracker tracker,
                                             LevelRenderState renderState, CallbackInfo ci) {
        HitboxVisibleEntities.beginFrame();
    }

    @Inject(method = "extractVisibleEntities", at = @At("RETURN"))
    private void aurora$endVisibleHitboxes(Camera camera, Frustum frustum, DeltaTracker tracker,
                                           LevelRenderState renderState, CallbackInfo ci) {
        HitboxVisibleEntities.endFrame();
    }

    @Inject(method = "extractEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"))
    private void aurora$recordVisibleHitboxEntity(Entity entity, float partialTick,
                                                  CallbackInfoReturnable<EntityRenderState> cir) {
        HitboxVisibleEntities.record(entity);
    }
}
