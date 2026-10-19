package research.mixin;

import research.Workload;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Software boundaries only; neither swap return nor runTick is physical scanout. */
@Mixin(value = Minecraft.class, priority = 20000)
public class FrameProbe {
    @Inject(method = "<init>", at = @At("HEAD"))
    private static void research$constructor(CallbackInfo ci) { Workload.event("minecraft-constructor-start"); }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void research$constructed(CallbackInfo ci) { Workload.event("minecraft-constructor-end"); }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void research$begin(boolean tick, CallbackInfo ci) { Workload.begin(); }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void research$end(boolean tick, CallbackInfo ci) { Workload.end(); research.InputTiming.endFrame(); }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;updateDisplay(Lcom/mojang/blaze3d/TracyFrameCapture;)V"))
    private void research$swap(CallbackInfo ci) { Workload.swap(); }
}
