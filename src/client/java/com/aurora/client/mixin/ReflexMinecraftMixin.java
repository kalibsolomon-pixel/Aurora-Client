package com.aurora.client.mixin;

import com.aurora.client.util.reflex.CpuTimeCollector;
import com.aurora.client.util.reflex.ReflexScheduler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ReflexMinecraftMixin {
    @Unique
    private final CpuTimeCollector cpuTimeCollect = new CpuTimeCollector();

    @Inject(method = "runTick", at = @At(value = "HEAD", shift = At.Shift.AFTER))
    private void aurora$reflexAfterRender(boolean bl, CallbackInfo ci) {
        ReflexScheduler.getInstance().waitBeforeRender();
        cpuTimeCollect.startCollect();
        ReflexScheduler.getInstance().renderQueueAdd();
    }

    @Inject(
            method = "runTick",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;updateDisplay(Lcom/mojang/blaze3d/TracyFrameCapture;)V")
    )
    private void aurora$reflexBeforeFlush(CallbackInfo ci) {
        ReflexScheduler.getInstance().renderQueueEndInsert();
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
        Long cpuTime = null;
        if (!ReflexScheduler.getInstance().gpuTimeCollectorDeque.isEmpty()) {
            ReflexScheduler.getInstance().gpuTimeCollectorDeque.getFirst().startQueryCheck();
        }
        if(!ReflexScheduler.getInstance().gpuTimeCollectorDeque.isEmpty() && ReflexScheduler.getInstance().gpuTimeCollectorDeque.getFirst().startTimeSystem != null){
            if (cpuTimeCollect.startTime != null) {
                cpuTime = ReflexScheduler.getInstance().gpuTimeCollectorDeque.getFirst().startTimeSystem - cpuTimeCollect.startTime;
            }
        } else {
            cpuTimeCollect.endCollect();
            cpuTime = cpuTimeCollect.getCpuTime();
        }
        cpuTimeCollect.reset();
        if (cpuTime != null) {
            ReflexScheduler.getInstance().updateCpuTime(cpuTime);
        }
    }
}
