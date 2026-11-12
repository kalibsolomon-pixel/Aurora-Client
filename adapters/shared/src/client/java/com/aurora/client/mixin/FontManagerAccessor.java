package com.aurora.client.mixin;

import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.Options;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** The font sets and provider list {@link com.aurora.client.ui.util.AuroraFontQuality} swaps re-scaled fonts into. */
@Mixin(FontManager.class)
public interface FontManagerAccessor {
    @Accessor("fontSets")
    Map<Identifier, FontSet> aurora$fontSets();

    @Accessor("providersToClose")
    List<GlyphProvider> aurora$providersToClose();

    @Invoker("getFontOptions")
    static Set<FontOption> aurora$fontOptions(Options options) {
        throw new AssertionError();
    }
}
