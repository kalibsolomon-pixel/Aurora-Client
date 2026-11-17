package com.aurora.client.ui.text;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Provider-owned 256px R8 pages, isolated from vanilla's glyph stitcher. */
final class SdfAtlas implements AutoCloseable {
    private static final int SIZE = 256;
    private static final int MAX_PAGES = 256;
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private final List<FontTexture> pages = new ArrayList<>();
    private final long owner = NEXT_ID.getAndIncrement();

    BakedGlyph add(GlyphInfo info, GlyphBitmap bitmap) throws IOException {
        for (FontTexture page : pages) {
            BakedGlyph glyph = page.add(info, bitmap);
            if (glyph != null) return glyph;
        }
        if (bitmap.getPixelWidth() > SIZE || bitmap.getPixelHeight() > SIZE) {
            throw new IOException("SDF glyph exceeds the 256px atlas page");
        }
        if (pages.size() >= MAX_PAGES) throw new IOException("SDF atlas page budget exhausted");

        Identifier id = Identifier.fromNamespaceAndPath("aurora",
                "sdf_inter_medium_" + owner + "_" + pages.size());
        GlyphRenderTypes raster = GlyphRenderTypes.createForGrayscaleTexture(id);
        GlyphRenderTypes types = new GlyphRenderTypes(raster.normal(), raster.seeThrough(),
                raster.polygonOffset(), SdfTextSupport.pipeline());
        FontTexture page = new FontTexture(id::toString, types, false); // false selects R8_UNORM
        try {
            BakedGlyph glyph = page.add(info, bitmap);
            if (glyph == null) throw new IOException("Could not allocate an SDF glyph on an empty page");
            pages.add(page);
            return glyph;
        } catch (Exception | LinkageError failure) {
            page.close();
            throw failure;
        }
    }

    @Override public void close() {
        for (FontTexture page : pages) page.close();
        pages.clear();
    }
}
