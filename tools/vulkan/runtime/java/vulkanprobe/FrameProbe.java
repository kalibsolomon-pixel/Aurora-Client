package vulkanprobe.mixin;
import vulkanprobe.Acceptance;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = Minecraft.class, priority = 20000)
public class FrameProbe {
    // Vulkan clears at frame head. Drive/capture only once the current GUI and
    // world have actually rendered, before presenting the completed target.
    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;updateDisplay(Lcom/mojang/blaze3d/TracyFrameCapture;)V"))
    private void vulkan$head(boolean tick, CallbackInfo ci) { Acceptance.frame(); }
}
