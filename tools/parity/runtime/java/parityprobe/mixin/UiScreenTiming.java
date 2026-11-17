package parityprobe.mixin;

import com.aurora.client.screen.AuroraScreen;
import com.aurora.client.screen.FeatureDetailScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import parityprobe.UiCacheBenchmark;

@Mixin({AuroraScreen.class, FeatureDetailScreen.class})
abstract class UiScreenTiming {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void begin(GuiGraphicsExtractor graphics, int x, int y, float delta, CallbackInfo ci) {
        UiCacheBenchmark.begin(this);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void end(GuiGraphicsExtractor graphics, int x, int y, float delta, CallbackInfo ci) {
        UiCacheBenchmark.end(this);
    }
}
