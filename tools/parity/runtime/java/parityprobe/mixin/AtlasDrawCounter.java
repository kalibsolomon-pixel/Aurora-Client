package parityprobe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts GUI item atlas slot draws (GUI Item Baking, ADR 0009). */
@Mixin(net.minecraft.client.gui.render.GuiItemAtlas.class)
public abstract class AtlasDrawCounter {
    @Inject(method = "drawToSlot", at = @At("HEAD"))
    private void parityprobe$countDraw(CallbackInfo ci) {
        parityprobe.Counters.ATLAS_DRAWS.incrementAndGet();
    }
}
