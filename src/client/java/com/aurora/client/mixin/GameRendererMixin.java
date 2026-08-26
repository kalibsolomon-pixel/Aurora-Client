package com.aurora.client.mixin;

import com.aurora.client.feature.impl.ZoomFeature;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void aurora$applyZoom(CallbackInfoReturnable<Float> cir) {
        double divisor = ZoomFeature.getCurrentFovDivisor();
        if (divisor > 1.001) {
            float base = cir.getReturnValueF();
            cir.setReturnValue((float) (base / divisor));
        }
    }
}
