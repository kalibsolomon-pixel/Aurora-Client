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
        var esports = IconCatalog.search("sports esports", 0);
        assertTrue(esports.stream().anyMatch(i -> i.name().equals("sports_esports")));
        assertTrue(esports.stream().allMatch(i -> i.label().contains("sports") && i.label().contains("esports")),
                "with no typo tolerance every word must appear");
        assertSame(esports, IconCatalog.search("  Sports_Esports ", 0), "the same query is answered from the memo");
        assertEquals("sports_esports", IconCatalog.search("sports esprots", 1).get(0).name(),
                "a swapped pair of letters is one typo, and the closest match ranks first");
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

    @Test void theBrowserDrawsOnlyVisibleRowsUnderTheBudget() {
        String src = SourceTree.read("screen/IconPickerScreen.java");
        assertTrue(src.contains("for (int row = Math.max(0, firstRow); row <= lastRow; row++)"),
                "only the rows inside the viewport draw");
        assertTrue(src.contains("MaterialIconRenderer.drawIconBudgeted("), "new glyphs rasterize under the frame budget");
        assertTrue(src.contains("IconCatalog.search("), "search goes through the memoized catalog");
    }

    @Test void profileCardsReconcileAgainstASetAndSearchIsMemoized() {
        String src = SourceTree.read("screen/ProfileManagerScreen.java");
        assertTrue(src.contains("Set<String> live = new HashSet<>(all);"));
        assertTrue(src.contains("if (!query.equals(memoQuery) || tolerance != memoTolerance || !all.equals(memoSource))"),
                "the filtered list rebuilds only when the query or the profiles change");
        assertTrue(src.contains("com.aurora.client.adapter.NativeUi.openPath("), "Open folder goes through the version adapter");
    }
}
