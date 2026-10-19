package mappingprobe.mixin;
import mappingprobe.Acceptance;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = Minecraft.class, priority = 20000)
public class FrameProbe {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void mapping$begin(boolean tick, CallbackInfo ci) { Acceptance.begin(); }
    @Inject(method = "runTick", at = @At("RETURN"))
    private void mapping$end(boolean tick, CallbackInfo ci) { Acceptance.end(); }
}
