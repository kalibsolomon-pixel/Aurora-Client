package com.aurora.client.mixin;

import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** A font set's providers, so a re-scaled Aurora font can replace its old provider in place. */
@Mixin(FontSet.class)
public interface FontSetAccessor {
    @Accessor("allProviders")
    List<GlyphProvider.Conditional> aurora$allProviders();
}
