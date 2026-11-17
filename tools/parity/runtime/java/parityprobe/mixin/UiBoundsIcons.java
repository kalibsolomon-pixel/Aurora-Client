package parityprobe.mixin;

import com.aurora.client.ui.util.MaterialIconRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import parityprobe.UiBounds;

/** Uses the cached native canvas dimensions after draw, rather than an estimated em box. */
@Mixin(MaterialIconRenderer.class)
abstract class UiBoundsIcons {
    @Inject(method="draw",at=@At("RETURN"))
    private static void icon(GuiGraphicsExtractor g,Font font,String glyph,float cx,float cy,float em,int color,
                              boolean budgeted,CallbackInfoReturnable<Boolean> cir) throws ReflectiveOperationException {
        if(!UiBounds.active() || !cir.getReturnValueZ()) return;
        var type=MaterialIconRenderer.class;
        var cache=type.getDeclaredField("RASTERS");cache.setAccessible(true);
        int scale=net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        int emPx=Math.max(1,Math.min(512,Math.round(em*scale)));
        long key=((long)glyph.codePointAt(0)<<21)|emPx;
        var raster=((java.util.Map<?,?>)cache.get(null)).get(key);
        if(raster==null) return; // atlas fallback is recorded by UiBoundsText
        var width=raster.getClass().getDeclaredMethod("width");width.setAccessible(true);
        var height=raster.getClass().getDeclaredMethod("height");height.setAccessible(true);
        int w=(int)width.invoke(raster)/scale,h=(int)height.invoke(raster)/scale;
        UiBounds.icon(g,glyph,Math.round(cx-w/2f),Math.round(cy-h/2f),w,h);
    }
}
