package parityprobe.mixin;

import com.aurora.client.ui.component.MaterialSurface;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import parityprobe.UiBounds;

@Mixin(MaterialSurface.class)
abstract class UiBoundsFaces {
    @Inject(method="depth",at=@At("HEAD"))
    private static void face(GuiGraphicsExtractor g,float x,float y,float w,float h,float radius,
                             int fill,int edge,int kind,@Coerce Object light,float opacity,CallbackInfo ci) {
        if(opacity>0) UiBounds.face(g,x,y,w,h);
    }
}
