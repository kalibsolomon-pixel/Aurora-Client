package parityprobe.mixin;

import com.aurora.client.ui.component.Slider;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import parityprobe.UiBounds;

@Mixin(Slider.class)
abstract class UiBoundsSliders {
    @Inject(method="renderOverlay",at=@At("HEAD"))
    private void track(GuiGraphicsExtractor g,float x,float y,float w,float h,int mouseX,int mouseY,CallbackInfo ci) {
        UiBounds.face(g,x,y+h-12,w,4);
    }
}
