package com.aurora.client.screen;

import com.aurora.client.SourceTree;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The bundled Material Symbols name list and the renderer that draws it (2026-11-12). */
class IconCatalogTest {

    @Test void theFullSetIsBundled() {
        assertTrue(IconCatalog.all().size() > 4000, "every Material Symbols Rounded icon is named");
        assertEquals(new String(Character.toChars(0xF0D3)), IconCatalog.glyph("person"), "names map through the list");
        assertEquals("", IconCatalog.glyph("no_such_icon"));
        assertEquals("", IconCatalog.glyph(""));
        // FeatureIcons keeps some legacy codepoints (the font still maps them),
        // e.g. swords is the same in both.
        assertEquals(FeatureIcons.get("better_hitreg"), IconCatalog.glyph("swords"));
    }

    @Test void searchMatchesEveryWordAndIsMemoized() {
        var swords = IconCatalog.search("swords");
        assertTrue(swords.stream().anyMatch(i -> i.name().equals("swords")));
        var esports = IconCatalog.search("sports esports");
        assertTrue(esports.stream().anyMatch(i -> i.name().equals("sports_esports")));
        assertTrue(esports.stream().allMatch(i -> i.label().contains("sports") && i.label().contains("esports")));
        assertSame(esports, IconCatalog.search("  Sports_Esports "), "the same query is answered from the memo");
        assertEquals(IconCatalog.all().size(), IconCatalog.search("").size());
        assertTrue(IconCatalog.search("zzzz_nothing").isEmpty());
    }

    @Test void theRendererEvictsLeastRecentlyUsedBetweenFrames() {
        String src = SourceTree.read("ui/util/MaterialIconRenderer.java");
        assertTrue(src.contains("new java.util.LinkedHashMap<>(64, 0.75f, true)"), "access-ordered LRU");
        assertFalse(src.contains("releaseAllLocked"), "no clear-all");
        assertTrue(src.contains("PENDING_RELEASE.add(eldest.next().getValue());"), "evictions are queued");
        int frame = src.indexOf("public static void beginFrame()");
        assertTrue(frame >= 0 && src.indexOf("releasePendingLocked();", frame) > frame,
                "queued textures are destroyed at the frame boundary (glass rule 10)");
        assertTrue(SourceTree.read("ui/component/GlassSurface.java")
                .contains("com.aurora.client.ui.util.MaterialIconRenderer.beginFrame();"));
        assertTrue(src.contains("if (budgeted && rasterizedThisFrame >= FRAME_BUDGET) return false;"));
    }
}
