package com.aurora.client.ui.text;

import com.mojang.blaze3d.font.GlyphInfo;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;

/** A baked pair which can change to raster even after a late shader failure. */
final class SdfBakedGlyph implements BakedGlyph {
    private final GlyphInfo info;
    private final BakedGlyph sdf;
    private final BakedGlyph raster;

    SdfBakedGlyph(GlyphInfo info, BakedGlyph sdf, BakedGlyph raster) {
        this.info = info;
        this.sdf = sdf;
        this.raster = raster;
    }

    @Override public GlyphInfo info() { return info; }

    @Override public TextRenderable.Styled createGlyph(float x, float y, int color, int shadowColor,
                                                       Style style, float boldOffset, float shadowOffset) {
        return new SdfRenderable(
                sdf.createGlyph(x, y, color, shadowColor, style, boldOffset, shadowOffset),
                raster.createGlyph(x, y, color, shadowColor, style, boldOffset, shadowOffset));
    }
}
