package parityprobe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts text decoration quads kept for drawing (Merge Text Decorations, ADR 0009). */
@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public abstract class EffectCounter {
    @Inject(method = "addEffect", at = @At("TAIL"))
    private void parityprobe$countEffect(CallbackInfo ci) {
        parityprobe.Counters.TEXT_EFFECTS.incrementAndGet();
    }
}
