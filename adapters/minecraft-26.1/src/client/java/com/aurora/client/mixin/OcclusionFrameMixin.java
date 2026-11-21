package com.aurora.client.mixin;

import com.aurora.client.performance.EntityOcclusion;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opens and closes {@link EntityOcclusion} around level extraction, the only window in which it skips anything. */
@Mixin(LevelRenderer.class)
public abstract class OcclusionFrameMixin {
    @Inject(method = "extractLevel", at = @At("HEAD"))
    private void aurora$openOcclusion(DeltaTracker tracker, Camera camera, float delta, CallbackInfo ci) {
        EntityOcclusion.begin(camera);
    }

    @Inject(method = "extractLevel", at = @At("RETURN"))
    private void aurora$closeOcclusion(CallbackInfo ci) {
        EntityOcclusion.end();
    }
}
