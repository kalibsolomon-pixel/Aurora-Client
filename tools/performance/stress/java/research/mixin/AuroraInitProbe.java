package research.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import research.Workload;

@Pseudo
@Mixin(targets = "com.aurora.client.AuroraClient", remap = false)
public class AuroraInitProbe {
    @Inject(method = "onInitializeClient", at = @At("HEAD"))
    private void research$begin(CallbackInfo ci) { Workload.event("aurora-initialize-start"); }

    @Inject(method = "onInitializeClient", at = @At("RETURN"))
    private void research$end(CallbackInfo ci) { Workload.event("aurora-initialize-end"); }
}
