package guiprobe.mixin;
import guiprobe.GuiAudit;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = Minecraft.class, priority = 20000)
public class FrameHook {
    // Drive/capture after the frame rendered, before present (valid on every backend).
    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;updateDisplay(Lcom/mojang/blaze3d/TracyFrameCapture;)V"))
    private void gui$frame(boolean tick, CallbackInfo ci) { GuiAudit.frame(); }
}
