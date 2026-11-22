package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.PackTweaksMath;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side local time override on Minecraft 26.2 (Pack Tweaks, off by default).
 */
@Mixin(ClientClockManager.class)
public abstract class LocalTimeMixin {

    @Inject(method = "getTotalTicks", at = @At("HEAD"), cancellable = true)
    private void aurora$overrideLocalTime(Holder<WorldClock> clock, CallbackInfoReturnable<Long> cir) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.localTimeEnabled) return;
        cir.setReturnValue((long) PackTweaksMath.clampTimeTicks(cfg.localTimeTicks));
    }
}
