package com.aurora.client.mixin;

import com.aurora.client.ui.text.SdfFontLoader;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Adds the explicit SDF font IDs without changing the codec or any raster provider definition. */
@Mixin(FontManager.class)
public abstract class SdfFontManagerMixin {
    @ModifyVariable(method = "safeLoad", at = @At("HEAD"), argsOnly = true)
    private GlyphProviderDefinition.Loader aurora$sdfLoader(GlyphProviderDefinition.Loader loader,
                                                            @Coerce Object builderId) {
        return SdfFontLoader.wrap(builderId, loader);
    }
}
