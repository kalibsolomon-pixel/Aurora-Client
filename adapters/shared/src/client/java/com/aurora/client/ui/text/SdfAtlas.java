package com.aurora.client.ui.text;

import com.aurora.client.ui.util.SdfTextMath;
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

    /**
     * A glyph's place on its page. A page never changes size once created, so the inset
     * texture coordinates are computed here once rather than per glyph draw.
     */
    static final class Cell {
        private final FontTexture page;
        private final int x, y, width, height;
        private final float u0, u1, v0, v1;

        Cell(FontTexture page, int x, int y, int width, int height, int pageWidth, int pageHeight) {
            this.page = page;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.u0 = SdfTextMath.insetUv(x, false, pageWidth);
            this.u1 = SdfTextMath.insetUv(x + width, true, pageWidth);
            this.v0 = SdfTextMath.insetUv(y, false, pageHeight);
            this.v1 = SdfTextMath.insetUv(y + height, true, pageHeight);
        }

        FontTexture page() { return page; }
        int x() { return x; }
        int y() { return y; }
        int width() { return width; }
        int height() { return height; }
        float u0() { return u0; }
        float u1() { return u1; }
        float v0() { return v0; }
        float v1() { return v1; }
    }

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
