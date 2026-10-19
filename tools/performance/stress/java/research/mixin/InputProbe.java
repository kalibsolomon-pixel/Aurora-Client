package research.mixin;

import research.Workload;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class InputProbe {
    @Inject(method = "onMove", at = @At("HEAD"))
    private void research$input(long window, double x, double y, CallbackInfo ci) { Workload.input(); research.InputTiming.arrival(); }

    @Inject(method = "handleAccumulatedMovement", at = @At("RETURN"))
    private void research$applied(CallbackInfo ci) { Workload.applied(); research.InputTiming.applied(); }
}
