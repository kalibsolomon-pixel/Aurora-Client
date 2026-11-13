package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.util.DeferredFontSet;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Set;

/** Avoids scanning every fallback codepoint for every unused Aurora face. */
@Mixin(FontManager.class)
public abstract class FontManagerDeferredMixin {
    @WrapOperation(method = "createFontSet", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/font/FontSet;reload(Ljava/util/List;Ljava/util/Set;)V"))
    private void aurora$prepareOnUse(FontSet font, List<GlyphProvider.Conditional> providers,
            Set<FontOption> options, Operation<Void> original, @Local(argsOnly = true) Identifier id) {
        AuroraConfig config = AuroraConfig.get();
        String activeFamily = AuroraFontRenderer.familyPath(config.activeFont);
        boolean active = config.textRendererMode != AuroraConfig.TextRendererMode.OFF
                && activeFamily != null && id.getPath().startsWith(activeFamily + "_");
        if (config.perfDeferAuroraFonts && "aurora".equals(id.getNamespace()) && !active
                && font instanceof DeferredFontSet deferred) {
            deferred.aurora$defer(providers, options);
        } else original.call(font, providers, options);
    }
}
