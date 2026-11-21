package parityprobe.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code -Daurora.parity.earlySdf=true}: the SDF text and UI shape pipeline lookups a first draw makes, but before
 * the constructor returns. A mod that lets screens draw during the first reload (RRLS) reloads the Shader Loader
 * from the constructor, and its apply is a main-thread task that cannot run until the constructor is done,
 * so the lookups see only the vanilla startup shaders, as an early first draw does. Logs the answers.
 */
@Mixin(Minecraft.class)
public abstract class EarlySdfLookup {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void parityprobe$earlyShaders(CallbackInfo ci) {
        if (!Boolean.getBoolean("aurora.parity.earlySdf")) return;
        look("com.aurora.client.ui.text.NativeSdfText", "early-sdf");
        look("com.aurora.client.ui.util.NativeShapeRenderer", "early-shape");
    }

    private static void look(String owner, String label) {
        try {
            var lookup = Class.forName(owner).getDeclaredMethod("pipelineAvailable");
            lookup.setAccessible(true);
            System.out.println("[parity-probe] " + label + " pipelineAvailable=" + lookup.invoke(null));
        } catch (ReflectiveOperationException failure) {
            failure.printStackTrace();
        }
    }
}
