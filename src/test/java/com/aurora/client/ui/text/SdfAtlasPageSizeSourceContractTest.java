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
        String instance = SourceTree.read("ui/text/SdfInstance.java");
        assertTrue(instance.contains("insetUv(cell.x(), false, cell.pageWidth())"), instance);
        assertTrue(instance.contains("insetUv(cell.x() + cell.width(), true, cell.pageWidth())"), instance);
        assertTrue(instance.contains("insetUv(cell.y(), false, cell.pageHeight())"), instance);
        assertTrue(instance.contains("insetUv(cell.y() + cell.height(), true, cell.pageHeight())"), instance);
        assertFalse(instance.contains("SdfAtlas.MAX_CELL"), "the cell limit is not a page size");
    }
}
