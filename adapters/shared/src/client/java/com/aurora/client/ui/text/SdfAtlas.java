package com.aurora.client.ui.text;

import net.minecraft.client.gui.font.FontTexture;

import java.util.ArrayList;
import java.util.List;

final class SdfAtlas implements AutoCloseable {
    /**
     * The largest cell generated: vanilla's font page size. Pages need not be that size
     * (ImmediatelyFast's font_atlas_resizing rewrites every 256 in {@link FontTexture}, default
     * 1024), so cells carry their page's actual size for texture coordinates.
     */
    static final int MAX_CELL = 256;
    static final int MAX_PAGES = 128;

    record Cell(FontTexture page, int x, int y, int width, int height, int pageWidth, int pageHeight) {}

    private final List<FontTexture> pages = new ArrayList<>();

    Cell add(SdfStrike.Bitmap bitmap) {
        for (FontTexture page : pages) {
            Cell cell = NativeSdfText.insert(page, bitmap);
            if (cell != null) return cell;
        }
        if (pages.size() >= MAX_PAGES) return null;
        FontTexture page = NativeSdfText.createPage(pages.size());
        try {
            Cell cell = NativeSdfText.insert(page, bitmap);
            if (cell == null) {
                // A page smaller than the cell (a font atlas size below vanilla's): this glyph keeps its bitmap.
                SdfFontSets.closeOne(page);
                return null;
            }
            pages.add(page);
            return cell;
        } catch (RuntimeException | LinkageError failure) {
            SdfFontSets.closeOne(page);
            throw failure;
        }
    }

    @Override public void close() {
        for (FontTexture page : pages) SdfFontSets.closeOne(page);
        pages.clear();
    }
}
