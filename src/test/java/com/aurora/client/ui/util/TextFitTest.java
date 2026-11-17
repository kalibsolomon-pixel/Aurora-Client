package com.aurora.client.ui.util;

import org.junit.jupiter.api.Test;
import java.util.function.ToIntFunction;
import static org.junit.jupiter.api.Assertions.*;

/** Grapheme-safe ellipsis fitting (core, no Minecraft): boundaries, floor, fallbacks, memo. */
class TextFitTest {
    /** Six units per codepoint: emoji sequences cost their codepoint count, like the old loop charged. */
    private static final ToIntFunction<String> PER_CODEPOINT = s -> s.codePointCount(0, s.length()) * 6;

    /** Additive per-character widths (space 4, narrow letters 3, ellipsis 10, else 6): monotonic, like real font advances. */
    private static int variedWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            w += c == '…' ? 10 : c == ' ' ? 4 : (c == 'i' || c == 'l') ? 3 : 6;
        }
        return w;
    }

    @Test void fitsUnchanged() {
        assertEquals("Aurora", TextFit.ellipsize("Aurora", 100, 0, PER_CODEPOINT));
        assertEquals("Aurora", TextFit.ellipsize("Aurora", 36, 0, PER_CODEPOINT));
    }

    @Test void maxWAtOrBelowZeroIsEmpty() {
        assertEquals("", TextFit.ellipsize("Aurora", 0, 0, PER_CODEPOINT));
        assertEquals("", TextFit.ellipsize("Aurora", -3, 0, PER_CODEPOINT));
    }

    /** The old ellipsize, verbatim, over a width function: the oracle the ASCII behaviour must match. */
    private static String oldEllipsize(String s, int maxW, int minLen, ToIntFunction<String> width) {
        if (maxW <= 0) return "";
        if (width.applyAsInt(s) <= maxW) return s;
        String ell = "…";
        if (width.applyAsInt(ell) > maxW) return oldPrefixByWidth(s, maxW, width);
        while (s.length() > minLen && width.applyAsInt(s + ell) > maxW)
            s = s.substring(0, s.offsetByCodePoints(s.length(), -1));
        if (width.applyAsInt(s + ell) > maxW) return oldPrefixByWidth(s, maxW - width.applyAsInt(ell), width) + ell;
        return s + ell;
    }

    /** plainSubstrByWidth's shape: the longest codepoint prefix within the budget, for additive widths. */
    private static String oldPrefixByWidth(String s, int maxW, ToIntFunction<String> width) {
        int end = 0;
        for (int i = 0; i < s.length(); ) {
            int next = s.offsetByCodePoints(i, 1);
            if (width.applyAsInt(s.substring(0, next)) > maxW) break;
            end = next;
            i = next;
        }
        return s.substring(0, end);
    }

    @Test void asciiTruncationMatchesTheOldLoopExactly() {
        String[] texts = {"Aurora Client", "Resourcepack Browser", "MMMMM iiiii", "abcdefgh", "a"};
        for (String text : texts) {
            int limit = variedWidth(text) + 6;
            for (int maxW = -2; maxW <= limit; maxW++)
                for (int minLen = 0; minLen <= 4; minLen++)
                    assertEquals(oldEllipsize(text, maxW, minLen, TextFitTest::variedWidth),
                            TextFit.ellipsize(text, maxW, minLen, TextFitTest::variedWidth),
                            "\"" + text + "\" maxW=" + maxW + " minLen=" + minLen);
        }
    }

    @Test void zwjFamilyEmojiIsNeverSplit() {
        String family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"; // 7 codepoints, 1 cluster
        String s = "A" + family + "BCDE";
        assertEquals(s, TextFit.ellipsize(s, 72, 0, PER_CODEPOINT), "fits unchanged");
        // Keeping the family and appending the ellipsis costs 54; the B after it would cost 60.
        assertEquals("A" + family + "…", TextFit.ellipsize(s, 54, 0, PER_CODEPOINT), "the family survives whole");
        assertEquals("A…", TextFit.ellipsize(s, 53, 0, PER_CODEPOINT), "no room for the family: dropped whole, never cut");
        java.util.Set<String> legal = new java.util.HashSet<>();
        legal.add("");
        legal.add("…"); // maxW barely above the ellipsis width: the empty prefix plus the ellipsis
        legal.add(s);
        for (String prefix : new String[]{"A", "A" + family, "A" + family + "B", "A" + family + "BC", "A" + family + "BCD"}) {
            legal.add(prefix);
            legal.add(prefix + "…");
        }
        for (int maxW = 0; maxW <= 72; maxW++) {
            String fitted = TextFit.ellipsize(s, maxW, 0, PER_CODEPOINT);
            assertTrue(legal.contains(fitted), maxW + " -> " + fitted);
            assertFalse(fitted.contains("\u200D") && !fitted.contains(family), maxW + " split the sequence: " + fitted);
        }
    }

    @Test void flagPairIsNeverSplit() {
        String flag = "\uD83C\uDDFA\uD83C\uDDF8"; // two regional indicators, one cluster
        String s = "X" + flag + "YZ";
        assertEquals("X" + flag + "…", TextFit.ellipsize(s, 24, 0, PER_CODEPOINT));
        assertEquals("X…", TextFit.ellipsize(s, 23, 0, PER_CODEPOINT));
        for (int maxW = 0; maxW <= 30; maxW++)
            assertFalse(TextFit.ellipsize(s, maxW, 0, PER_CODEPOINT).contains("\uD83C\uDDFA")
                            && !TextFit.ellipsize(s, maxW, 0, PER_CODEPOINT).contains(flag),
                    maxW + " split the flag");
    }

    @Test void combiningMarkStaysOnItsBaseLetter() {
        // "e" + U+0301 is one cluster. The old loop returned "e…" at 12, stranding the mark.
        String s = "e\u0301fg";
        assertEquals(s, TextFit.ellipsize(s, 24, 0, PER_CODEPOINT), "fits unchanged");
        assertEquals("e\u0301…", TextFit.ellipsize(s, 18, 0, PER_CODEPOINT));
        assertEquals("…", TextFit.ellipsize(s, 12, 0, PER_CODEPOINT));
        for (int maxW = 0; maxW <= 24; maxW++)
            assertFalse(TextFit.ellipsize(s, maxW, 0, PER_CODEPOINT).endsWith("e…"),
                    maxW + " split the combining sequence");
    }

    @Test void minLenFloorsTheCutAtAClusterBoundary() {
        assertEquals("abc…", TextFit.ellipsize("abcdef", 27, 3, PER_CODEPOINT));
        // Even the floor cannot carry the ellipsis: a width-bounded prefix of the
        // floor text plus the ellipsis, exactly the old fallback.
        assertEquals("ab…", TextFit.ellipsize("abcdef", 20, 4, PER_CODEPOINT));
        assertEquals("ab…", TextFit.ellipsize("abcdef", 20, 2, PER_CODEPOINT));
        // minLen beyond the string: the floor is the whole text, fallback again.
        assertEquals("…", TextFit.ellipsize("abc", 6, 10, PER_CODEPOINT));
        // The floor snaps down to a cluster boundary: minLen 3 lands inside the flag
        // (chars 2..5), so the floor is the boundary at 2, never the flag's midpoint.
        assertEquals("ab…", TextFit.ellipsize("ab\uD83C\uDDFA\uD83C\uDDF8cd", 18, 3, PER_CODEPOINT));
    }

    @Test void ellipsisWiderThanMaxWFallsBackToABarePrefix() {
        // variedWidth charges the ellipsis 10: wider than narrow maxWs, cheap inside a fit.
        assertEquals("a", TextFit.ellipsize("abcdef", 9, 0, TextFitTest::variedWidth),
                "the ellipsis (10) does not fit: the longest bare prefix, no ellipsis");
        assertEquals("", TextFit.ellipsize("abcdef", 5, 0, TextFitTest::variedWidth));
        assertEquals("ab…", TextFit.ellipsize("abcdefgh", 24, 0, TextFitTest::variedWidth),
                "with room for it: varied(prefix) <= 24 - 10");
    }

    @Test void nonMonotonicWidthsNeverOverflow() {
        // A kerning-style bump in the middle: widths go up, down, then up again.
        ToIntFunction<String> bumpy = s -> {
            int cp = s.codePointCount(0, s.length());
            return cp * 6 + (cp >= 4 && cp <= 7 ? 100 : 0);
        };
        int ellW = bumpy.applyAsInt("…");
        for (int maxW = ellW; maxW <= 200; maxW++) {
            String fitted = TextFit.ellipsize("abcdefgh", maxW, 0, bumpy);
            boolean fits = fitted.endsWith("…")
                    ? bumpy.applyAsInt(fitted) <= maxW
                    || bumpy.applyAsInt(fitted.substring(0, fitted.length() - 1)) <= maxW - ellW
                    : bumpy.applyAsInt(fitted) <= maxW;
            assertTrue(fits, maxW + " -> " + fitted + " (" + bumpy.applyAsInt(fitted) + ")");
        }
    }

    @Test void memoHitsAndRefitsByEveryKeyPart() {
        int[] probes = {0};
        ToIntFunction<String> counting = s -> {
            probes[0]++;
            return PER_CODEPOINT.applyAsInt(s);
        };
        TextFit.Memo memo = new TextFit.Memo(8);
        assertEquals("abc…", memo.fit("abcdef", 27, 0, 1, counting));
        int afterFirst = probes[0];
        assertTrue(afterFirst > 0);
        assertEquals("abc…", memo.fit("abcdef", 27, 0, 1, counting));
        assertEquals(afterFirst, probes[0], "the same key fits from the memo without measuring");
        assertEquals("", memo.fit("abcdef", 0, 0, 1, counting));
        assertEquals(afterFirst, probes[0], "maxW <= 0 never reaches the measure");
        probes[0] = 0;
        memo.fit("abcdef", 26, 0, 1, counting);
        assertTrue(probes[0] > 0, "a different maxW re-fits");
        probes[0] = 0;
        memo.fit("abcdef", 27, 1, 1, counting);
        assertTrue(probes[0] > 0, "a different minLen re-fits");
        probes[0] = 0;
        memo.fit("abcdef", 27, 0, 2, counting);
        assertTrue(probes[0] > 0, "a different style key re-fits");
        probes[0] = 0;
        memo.fit("abcdeg", 27, 0, 1, counting);
        assertTrue(probes[0] > 0, "different text re-fits");
        probes[0] = 0;
        assertEquals("abc…", memo.fit("abcdef", 27, 0, 1, counting));
        assertEquals(0, probes[0], "the original key is still memoized");
    }

    @Test void memoEvictsAtItsCapacity() {
        TextFit.Memo memo = new TextFit.Memo(2);
        for (int i = 0; i < 5; i++) memo.fit("text" + i, 6, 0, 1, PER_CODEPOINT);
        assertEquals(2, memo.size());
    }
}
