package com.aurora.client.ui.text;

import com.aurora.client.SourceTree;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SDF glyph texture coordinates use each atlas page's own size. ImmediatelyFast's
 * font_atlas_resizing rewrites every 256 in vanilla's FontTexture, Aurora's pages
 * included (1024 by default); dividing by vanilla's 256 garbled every SDF glyph.
 */
class SdfAtlasPageSizeSourceContractTest {
    @Test void cellsCarryTheirPagesTextureSize() {
        String insert = SourceTree.read("ui/text/NativeSdfText.java");
        assertTrue(insert.contains("GpuTexture texture = page.getTexture();"), insert);
        assertTrue(insert.contains("texture.getWidth(0), texture.getHeight(0));"), insert);
    }

    @Test void texelCoordinatesDivideByThePageSize() {
        // Computed once per cell (a page keeps its size) and read by every glyph draw.
        String atlas = SourceTree.read("ui/text/SdfAtlas.java");
        assertTrue(atlas.contains("insetUv(x, false, pageWidth)"), atlas);
        assertTrue(atlas.contains("insetUv(x + width, true, pageWidth)"), atlas);
        assertTrue(atlas.contains("insetUv(y, false, pageHeight)"), atlas);
        assertTrue(atlas.contains("insetUv(y + height, true, pageHeight)"), atlas);
        String instance = SourceTree.read("ui/text/SdfInstance.java");
        assertTrue(instance.contains("float u0 = cell.u0(), u1 = cell.u1(), v0 = cell.v0(), v1 = cell.v1();"), instance);
        assertFalse(instance.contains("SdfAtlas.MAX_CELL"), "the cell limit is not a page size");
    }
}
