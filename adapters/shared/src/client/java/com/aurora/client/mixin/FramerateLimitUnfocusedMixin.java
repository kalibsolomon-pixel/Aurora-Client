package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Unfocused Frame Limit (ADR 0009, a visible trade-off with a Performance
 * row): while another window has focus, the game draws at most
 * {@code perfUnfocusedFps} frames a second. Vanilla only slows down when
 * minimized or after a minute idle. Aurora's frame pacer reads this
 * tracker's limit, so the lower value paces like any other.
 */
@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitUnfocusedMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void aurora$unfocusedLimit(CallbackInfoReturnable<Integer> cir) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.perfUnfocusedLimit || minecraft.isWindowActive()) return;
        int limit = Math.max(1, cfg.perfUnfocusedFps);
        if (cir.getReturnValueI() > limit) cir.setReturnValue(limit);
    }
}
