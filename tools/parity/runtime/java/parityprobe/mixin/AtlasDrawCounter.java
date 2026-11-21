package parityprobe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts GUI item atlas slot draws and times them (GUI Item Baking, GUI Flat Faces, ADR 0009). */
@Mixin(net.minecraft.client.gui.render.GuiItemAtlas.class)
public abstract class AtlasDrawCounter {
    @Unique private static long parityprobe$started;

    @Inject(method = "drawToSlot", at = @At("HEAD"))
    private void parityprobe$countDraw(CallbackInfo ci) {
        parityprobe.Counters.ATLAS_DRAWS.incrementAndGet();
        parityprobe$started = System.nanoTime();
    }

    @Inject(method = "drawToSlot", at = @At("RETURN"))
    private void parityprobe$timeDraw(CallbackInfo ci) {
        parityprobe.Counters.ATLAS_NANOS.addAndGet(System.nanoTime() - parityprobe$started);
    }
}
