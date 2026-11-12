package parityprobe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts work that ADR 0009 optimizations remove but frame time cannot show:
 * sound listener updates applied on the sound thread. Read by the probe's
 * "count" command.
 */
@Mixin(com.mojang.blaze3d.audio.Listener.class)
public abstract class WorkCounters {
    @Inject(method = "setTransform", at = @At("HEAD"))
    private void parityprobe$countListener(CallbackInfo ci) {
        parityprobe.Counters.LISTENER_UPDATES.incrementAndGet();
    }
}
