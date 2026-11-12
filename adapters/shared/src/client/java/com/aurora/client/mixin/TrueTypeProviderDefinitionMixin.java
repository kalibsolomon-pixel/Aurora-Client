package com.aurora.client.mixin;

import com.aurora.client.ui.util.AuroraFontQuality;
import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Font quality (2026-11-12): Aurora's TrueType fonts are rasterized at the
 * screen's real pixel size. Minecraft samples its font atlas with nearest
 * filtering, so a glyph drawn at its texture size is crisp and FreeType's own
 * anti-aliasing shows, while the shipped oversample of 8 at GUI scale 3 was
 * shrunk 2.67x by dropping texels (the choppy edges). Only {@code aurora:}
 * providers change; the GUI scale is read at load, and
 * {@link AuroraFontQuality} re-loads them in place when it changes. Harmless
 * alone: with the text renderer off the fonts are simply never drawn.
 */
@Mixin(TrueTypeGlyphProviderDefinition.class)
public abstract class TrueTypeProviderDefinitionMixin {

    @ModifyArg(method = "load",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/font/TrueTypeGlyphProvider;<init>(Ljava/nio/ByteBuffer;Lorg/lwjgl/util/freetype/FT_Face;FFFFLjava/lang/String;)V"),
            index = 3)
    private float aurora$screenPixelOversample(float oversample) {
        TrueTypeGlyphProviderDefinition self = (TrueTypeGlyphProviderDefinition) (Object) this;
        return "aurora".equals(self.location().getNamespace()) ? AuroraFontQuality.oversample() : oversample;
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void aurora$remember(net.minecraft.server.packs.resources.ResourceManager resources,
                                 CallbackInfoReturnable<GlyphProvider> cir) {
        TrueTypeGlyphProviderDefinition self = (TrueTypeGlyphProviderDefinition) (Object) this;
        if ("aurora".equals(self.location().getNamespace()) && cir.getReturnValue() != null) {
            AuroraFontQuality.register(cir.getReturnValue(), self);
        }
    }
}
