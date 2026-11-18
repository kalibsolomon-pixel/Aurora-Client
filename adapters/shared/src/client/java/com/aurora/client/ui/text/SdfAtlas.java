package com.aurora.client.ui.text;

import net.minecraft.client.gui.font.FontTexture;

import java.util.ArrayList;
import java.util.List;

final class SdfAtlas implements AutoCloseable {
    static final int SIZE = 256;
    static final int MAX_PAGES = 128;

    record Cell(FontTexture page, int x, int y, int width, int height) {}

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
            if (cell == null) throw new IllegalStateException("SDF cell did not fit an empty atlas page");
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
