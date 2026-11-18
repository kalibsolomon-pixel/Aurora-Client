package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.text.SdfTextSupport;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiTextRenderState.class)
public abstract class SdfGuiTextMixin {
    @Redirect(method = "ensurePrepared", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"),
            require = 1)
    private Font.PreparedText aurora$prepareGui(Font font, FormattedCharSequence text, float x, float y,
                                               int color, boolean shadow, boolean includeEmpty, int background) {
        if (!SdfTextSupport.enabled(AuroraConfig.get()) || !RenderSystem.isOnRenderThread()) {
            return font.prepareText(text, x, y, color, shadow, includeEmpty, background);
        }
        boolean previous = SdfTextSupport.beginGui();
        try {
            return font.prepareText(text, x, y, color, shadow, includeEmpty, background);
        } finally {
            SdfTextSupport.endGui(previous);
        }
    }
}
