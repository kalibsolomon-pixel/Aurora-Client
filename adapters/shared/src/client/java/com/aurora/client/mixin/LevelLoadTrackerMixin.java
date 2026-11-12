package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skip Level Loading Delay (ADR 0009): the loading screen closes as soon as
 * the level is ready. Vanilla passes a 500 ms close delay only when it
 * creates a new singleplayer world ({@code Minecraft.doWorldLoad}); every
 * multiplayer join already uses 0, so what a server receives, and when, is
 * unchanged.
 */
@Mixin(LevelLoadTracker.class)
public abstract class LevelLoadTrackerMixin {
    @Shadow @Final @Mutable private long closeDelayMs;

    @Inject(method = "<init>(J)V", at = @At("RETURN"))
    private void aurora$noCloseDelay(long closeDelayMs, CallbackInfo ci) {
        if (AuroraConfig.get().perfSkipLevelLoadDelay) this.closeDelayMs = 0L;
    }
}
