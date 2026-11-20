package com.aurora.client.ui.text;

import com.aurora.client.ui.text.SdfInstance.Bounds;
import com.aurora.client.ui.text.SdfInstance.Geometry;
import com.aurora.client.ui.util.SdfTextMath;
import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.CodepointMap;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EmptyGlyph;
import net.minecraft.network.chat.Style;

import java.io.IOException;

final class SdfGlyphProvider implements GlyphProvider {
    private final GlyphProvider raster;
    private final SdfFace face;
    private final CodepointMap<UnbakedGlyph> glyphs = new CodepointMap<>(UnbakedGlyph[]::new, UnbakedGlyph[][]::new);

    SdfGlyphProvider(GlyphProvider raster, SdfFace face) {
        this.raster = raster;
        this.face = face;
    }

    @Override public IntSet getSupportedGlyphs() { return raster.getSupportedGlyphs(); }

    @Override public UnbakedGlyph getGlyph(int codePoint) {
        UnbakedGlyph glyph = glyphs.get(codePoint);
        if (glyph != null) return glyph;
        UnbakedGlyph original = raster.getGlyph(codePoint);
        if (original == null || original instanceof EmptyGlyph) return original;
        glyph = new Glyph(original, codePoint);
        glyphs.put(codePoint, glyph);
        return glyph;
    }

    private static Bounds padded(Bounds ink, SdfAtlas.Cell cell) {
        int pad = SdfTextMath.SPREAD + SdfTextMath.PADDING;
        float dx = pad * ((ink.right() - ink.left()) / (cell.width() - 2 * pad));
        float dy = pad * ((ink.bottom() - ink.top()) / (cell.height() - 2 * pad));
        return new Bounds(ink.left() - dx, ink.top() - dy, ink.right() + dx, ink.bottom() + dy);
    }

    private final class Glyph implements UnbakedGlyph {
        private final UnbakedGlyph original;
        private final int codePoint;

        private Glyph(UnbakedGlyph original, int codePoint) {
            this.original = original;
            this.codePoint = codePoint;
        }

        @Override public GlyphInfo info() { return original.info(); }

        @Override public BakedGlyph bake(Stitcher stitcher) {
            return original.bake(new Stitcher() {
                @Override public BakedGlyph stitch(GlyphInfo info, GlyphBitmap bitmap) {
                    BakedGlyph baked = stitcher.stitch(info, bitmap);
                    var ink = new Bounds(bitmap.getLeft(), bitmap.getTop(), bitmap.getRight(), bitmap.getBottom());
                    return new Pair(baked, ink, codePoint);
                }

                @Override public BakedGlyph getMissing() { return stitcher.getMissing(); }
            });
        }
    }

    private final class Pair implements BakedGlyph {
        private final BakedGlyph raster;
        private final Bounds bounds;
        private final int codePoint;
        private boolean attempted;
        private Geometry geometry;

        private Pair(BakedGlyph raster, Bounds bounds, int codePoint) {
            this.raster = raster;
            this.bounds = bounds;
            this.codePoint = codePoint;
        }

        @Override public GlyphInfo info() { return raster.info(); }

        @Override public TextRenderable.Styled createGlyph(float penX, float penY, int color, int shadowColor,
                                                           Style style, float boldOffset, float shadowOffset) {
            if (SdfTextSupport.drawing() && NativeSdfText.pipelineAvailable()) {
                if (!attempted) prepareCell();
                if (geometry != null) {
                    return NativeSdfText.glyph(geometry, penX, penY, color, shadowColor, style,
                            info().getAdvance(style.isBold()), boldOffset, shadowOffset);
                }
            }
            return raster.createGlyph(penX, penY, color, shadowColor, style, boldOffset, shadowOffset);
        }

        private void prepareCell() {
            boolean warm = !face.cached(codePoint);
            if (warm && !SdfTextSupport.canWarmGlyph()) return;
            attempted = true;
            long start = warm ? System.nanoTime() : 0;
            try {
                SdfAtlas.Cell cell = face.cell(codePoint);
                if (cell != null) geometry = new Geometry(cell, bounds, padded(bounds, cell));
            } catch (IOException | RuntimeException | LinkageError failure) {
                SdfTextSupport.fail("glyph rasterization or atlas upload", failure);
            } finally {
                if (warm) SdfTextSupport.recordWarmup(start);
            }
        }
    }
}
