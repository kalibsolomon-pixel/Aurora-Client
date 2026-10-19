package research.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import research.InputTiming;

@Mixin(Camera.class)
public class CameraProbe {
    @Inject(method = "setup", at = @At("RETURN"))
    private void research$camera(CallbackInfo ci) { InputTiming.camera(); }
}
