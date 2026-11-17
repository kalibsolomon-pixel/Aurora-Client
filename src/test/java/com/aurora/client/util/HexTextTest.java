package com.aurora.client.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link HexText}: the shared parse behind every hex field, so acceptance
 * (and therefore the refusal message) is identical everywhere.
 */
class HexTextTest {

    @Test
    void parsesSixDigitsAsOpaqueArgb() {
        assertEquals(0xFF3A7BFF, HexText.parseRgb("3A7BFF"));
        assertEquals(0xFF3A7BFF, HexText.parseRgb("3a7bff"));
        assertEquals(0xFF3A7BFF, HexText.parseRgb("#3A7BFF"));
        assertEquals(0xFF3A7BFF, HexText.parseRgb(" 3A7BFF "));
    }

    @Test
    void parsesEightDigitsIncludingFullAlpha() {
        assertEquals(0x6633CCFF, HexText.parseArgb("6633CCFF"));
        assertEquals(-1, HexText.parseArgb("FFFFFFFF")); // (int) cast parity with the old picker parse
        assertEquals(0x6633CCFF, HexText.parseArgb("#6633CCFF"));
    }

    @Test
    void looseParseAcceptsEitherLength() {
        assertEquals(0xFF3A7BFF, HexText.parseArgbOrRgb("3A7BFF"));
        assertEquals(0x6633CCFF, HexText.parseArgbOrRgb("6633CCFF"));
    }

    @Test
    void refusesEverythingElse() {
        assertNull(HexText.parseRgb(null));
        assertNull(HexText.parseRgb(""));
        assertNull(HexText.parseRgb("#"));
        assertNull(HexText.parseRgb("3A7BF"));   // too short
        assertNull(HexText.parseRgb("3A7BFFF")); // too long
        assertNull(HexText.parseRgb("3A7BGF"));  // not a hex digit
        assertNull(HexText.parseRgb("3A7B F"));  // interior whitespace
        assertNull(HexText.parseArgb("3A7BFF")); // 8-digit field, 6-digit text
        assertNull(HexText.parseRgb("6633CCFF")); // 6-digit field, 8-digit text
    }

    @Test
    void isHexAcceptsOnlyHexDigits() {
        assertTrue(HexText.isHex("0123456789abcdefABCDEF"));
        assertFalse(HexText.isHex(""));
        assertFalse(HexText.isHex(null));
        assertFalse(HexText.isHex("0x1F"));
    }
}
