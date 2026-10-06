package com.aurora.client.ui.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WordWrapTest {
    private static int width(String value) { return value.codePointCount(0, value.length()); }

    @Test void wrapsOnlyBetweenCompleteWords() {
        assertEquals(java.util.List.of("Resourcepack", "Browser"), WordWrap.lines("Resourcepack Browser", 12, 2, WordWrapTest::width));
        assertEquals(java.util.List.of("Minecraft", "Reflex"), WordWrap.lines("Minecraft Reflex", 10, 2, WordWrapTest::width));
    }

    @Test void oversizedWordNeverLeaksOntoASecondLine() {
        assertEquals(java.util.List.of("Resour…", "Browser"), WordWrap.lines("Resourcepack Browser", 7, 2, WordWrapTest::width));
    }

    @Test void hiddenWordsHaveABoundedEllipsisAndUnicodeRemainsIntact() {
        var lines = WordWrap.lines("One two three four five", 7, 2, WordWrapTest::width);
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).endsWith("…"));
        assertTrue(lines.stream().allMatch(line -> width(line) <= 7));
        assertEquals(java.util.List.of("😀😀…"), WordWrap.lines("😀😀😀😀", 3, 1, WordWrapTest::width));
    }

    @Test void whitespaceAndEmptyColumnsStayBounded() {
        assertEquals(java.util.List.of("One two"), WordWrap.lines(" One\t two ", 8, 2, WordWrapTest::width));
        assertTrue(WordWrap.lines("One", 0, 2, WordWrapTest::width).isEmpty());
    }
}
