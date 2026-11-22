package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.PackTweaksMath;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side local time override on Minecraft 26.3 (Pack Tweaks, off by default).
 */
@Mixin(ClientClockManager.ClientClockInstance.class)
public abstract class LocalTimeMixin {

    @Inject(method = "totalTicks", at = @At("HEAD"), cancellable = true)
    private void aurora$overrideLocalTime(CallbackInfoReturnable<Long> cir) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.localTimeEnabled) return;
        cir.setReturnValue((long) PackTweaksMath.clampTimeTicks(cfg.localTimeTicks));
    }

    @Inject(method = "partialTick", at = @At("HEAD"), cancellable = true)
    private void aurora$freezePartialTick(CallbackInfoReturnable<Float> cir) {
        if (!AuroraConfig.get().localTimeEnabled) return;
        cir.setReturnValue(0.0f);
    }
}
