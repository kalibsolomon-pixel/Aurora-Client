package parityprobe.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import parityprobe.UiBounds;

@Mixin(GuiGraphicsExtractor.class)
abstract class UiBoundsText {
    @Inject(method="text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",at=@At("HEAD"))
    private void text(Font font,FormattedCharSequence text,int x,int y,int color,boolean shadow,CallbackInfo ci) {
        UiBounds.text((GuiGraphicsExtractor)(Object)this,font,text,x,y,color);
    }
}
