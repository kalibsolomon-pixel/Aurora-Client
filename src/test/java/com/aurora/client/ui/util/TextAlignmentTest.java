package com.aurora.client.ui.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** One optical centring rule for text and glyphs on faces (owner request, 2026-11-17). */
class TextAlignmentTest {

    @Test void textInkCentresOnTheBoxForEveryHeight() {
        // Vanilla 9 px line: cap/x ink spans 0..7 from the draw origin (descenders excluded).
        var ink = new TextAlignment.Ink(0, 7);
        for (int h = 10; h <= 40; h++) {
            int y = TextAlignment.textY(100, h, ink);
            double inkCentre = y + ink.center();
            assertEquals(100 + h / 2.0, inkCentre, .5, "box height " + h);
        }
    }

    @Test void customFontInkWithBearingCentresToo() {
        // An 8 px Aurora body face whose ink starts one pixel below the origin.
        var ink = new TextAlignment.Ink(1, 7);
        assertEquals(20 + 9, TextAlignment.textY(20, 18, ink) + ink.center(), .5);
    }

    @Test void multilineBlocksCentreOnTheirMiddleLine() {
        var ink = new TextAlignment.Ink(0, 7);
        int pitch = 11;
        int first = TextAlignment.blockY(0, 40, 3, ink, pitch);
        double middleInk = first + pitch + ink.center();
        assertEquals(20, middleInk, .5);
    }

    @Test void horizontalCentringUsesTheDrawnWidthAndEqualSidePadding() {
        assertEquals(10 + (60 - 24) / 2, TextAlignment.centeredX(10, 60, 24));
        assertEquals(84 - 16 - 2 * TextAlignment.FACE_PAD, TextAlignment.faceTextWidth(84, 16));
        assertEquals(0, TextAlignment.faceTextWidth(10, 16));
    }

    @Test void inkRejectsEmptyBounds() {
        assertThrows(IllegalArgumentException.class, () -> new TextAlignment.Ink(3, 3));
    }

    /** Rows and components centre through AuroraFontRenderer.textY/TextAlignment, never by hand. */
    @Test void noHandRolledLineHeightCentring() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String dir : List.of("adapters/shared/src/client/java/com/aurora/client/screen/setting",
                "adapters/shared/src/client/java/com/aurora/client/ui/component")) {
            try (Stream<Path> files = Files.walk(Path.of(dir))) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String src = Files.readString(p);
                    if (src.matches("(?s).*lineHeight\\s*\\)\\s*/\\s*2.*")) offenders.add(p.toString());
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Hand-rolled (h - lineHeight) / 2 centring in " + offenders);
    }
}
