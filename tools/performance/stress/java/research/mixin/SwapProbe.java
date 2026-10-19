package research.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import research.InputTiming;

/** Explicit research-only CPU brackets around the actual GLFW swap call. */
@Mixin(value = RenderSystem.class, remap = false)
public class SwapProbe {
    @Inject(method = "flipFrame", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V"))
    private static void research$swapEntry(CallbackInfo ci) { InputTiming.swapEntry(); }

    @Inject(method = "flipFrame", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V", shift = At.Shift.AFTER))
    private static void research$swapReturn(CallbackInfo ci) { InputTiming.swapReturn(); }
}
