package com.aurora.client.ui.util;

import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.FontOption;

import java.util.List;
import java.util.Set;

/** Aurora font preparation deferred until its first actual text lookup. */
public interface DeferredFontSet {
    void aurora$defer(List<GlyphProvider.Conditional> providers, Set<FontOption> options);
    boolean aurora$pending();
}
