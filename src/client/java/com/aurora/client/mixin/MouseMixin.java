package com.aurora.client.mixin;

import com.aurora.client.feature.impl.ZoomFeature;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While zoom is held, intercepts MouseHandler scroll: adjusts zoom level instead
 * of letting the scroll fall through to vanilla (which would cycle hotbar
 * slots). Scroll events are forwarded to {@link ZoomFeature#onScroll}.
 */
@Mixin(MouseHandler.class)
public abstract class MouseMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 1)
    private void aurora$interceptScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ZoomFeature.isActive()) {
            ZoomFeature.onScroll(vertical);
            ci.cancel();
        }
    }
}