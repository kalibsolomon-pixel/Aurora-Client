package parityprobe.mixin;

import com.aurora.client.screen.setting.FeatureSetting;
import com.aurora.client.ui.util.SettingRowLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import parityprobe.UiBounds;

@Mixin(FeatureSetting.class)
abstract class UiBoundsLabels {
    @Shadow protected SettingRowLayout compactRow;
    @Inject(method="renderCompactLabel",at=@At("HEAD"))
    private void label(GuiGraphicsExtractor g,int x,int y,int mouseX,int mouseY,int color,CallbackInfo ci) {
        if(compactRow==null) return;
        var box=compactRow.label();
        UiBounds.face(g,x+box.x(),y+box.y(),box.width(),box.height());
    }
}
