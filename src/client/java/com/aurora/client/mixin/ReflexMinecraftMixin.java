package com.aurora.client.mixin;

import com.aurora.client.util.reflex.ReflexScheduler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reflex's three per-frame hooks. Deliberately thin: every body lives in
 * {@link ReflexScheduler} (beginFrame/beforeFlush/endFrame), wrapped by its
 * exception-safety guard — an unexpected failure inside Reflex disables its
 * pacing for the session with one ERROR log and must NEVER propagate into
 * vanilla's frame loop (see ReflexScheduler's 2026-09-13 note: the
 * disconnect-during-render crash).
 */
@Mixin(Minecraft.class)
public abstract class ReflexMinecraftMixin {
    @Inject(method = "runTick", at = @At(value = "HEAD", shift = At.Shift.AFTER))
    private void aurora$reflexAfterRender(boolean bl, CallbackInfo ci) {
        ReflexScheduler.getInstance().beginFrame();
    }

    @Inject(
            method = "runTick",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;updateDisplay(Lcom/mojang/blaze3d/TracyFrameCapture;)V")
    )
    private void aurora$reflexBeforeFlush(CallbackInfo ci) {
        ReflexScheduler.getInstance().beforeFlush();
    }

    @Inject(
            method = "runTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V",
                    shift = At.Shift.AFTER
            )
    )
    private void aurora$reflexAfterFlush(CallbackInfo ci) {
        ReflexScheduler.getInstance().endFrame();
    }
}
