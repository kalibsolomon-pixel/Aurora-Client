package parityprobe.mixin;

import dev.phenomena.gui.screens.PhenomenaScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import parityprobe.ShapeBenchmark;

/** Opt-in fixture instrumentation, excluded from Aurora artifacts. */
@Mixin(PhenomenaScreen.class)
abstract class PulsarTiming {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void begin(GuiGraphicsExtractor graphics, int x, int y, float delta, CallbackInfo ci) {
        ShapeBenchmark.begin();
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void end(GuiGraphicsExtractor graphics, int x, int y, float delta, CallbackInfo ci) {
        ShapeBenchmark.end();
    }
}
