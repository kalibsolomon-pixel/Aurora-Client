package parityprobe.mixin;

import com.aurora.client.screen.ManagerListScreen;
import com.aurora.client.screen.WaypointManagerScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import parityprobe.ShapeBenchmark;
import parityprobe.UiBounds;

@Mixin(ManagerListScreen.class)
abstract class ManagerListTiming {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void begin(GuiGraphicsExtractor graphics, int x, int y, float delta, CallbackInfo ci) {
        if ((Object) this instanceof WaypointManagerScreen) ShapeBenchmark.begin();
        UiBounds.begin(this);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void end(GuiGraphicsExtractor graphics, int x, int y, float delta, CallbackInfo ci) {
        UiBounds.end(this);
        if ((Object) this instanceof WaypointManagerScreen) ShapeBenchmark.end();
    }
}
