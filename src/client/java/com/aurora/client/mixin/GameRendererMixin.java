package com.aurora.client.mixin;

import com.aurora.client.feature.impl.ZoomFeature;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 26.1: the camera computes the FOV once per frame (GameRenderer.getFov is gone). The class
// keeps its name because FeatureCatalog binds features to it.
@Mixin(Camera.class)
public class GameRendererMixin {

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void aurora$applyZoom(CallbackInfoReturnable<Float> cir) {
        double divisor = ZoomFeature.getCurrentFovDivisor();
        if (divisor > 1.001) {
            float base = cir.getReturnValueF();
            cir.setReturnValue((float) (base / divisor));
        }
    }
}
