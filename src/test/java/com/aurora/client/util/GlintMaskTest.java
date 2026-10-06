package com.aurora.client.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlintMaskTest {
    @Test void additiveModeRestoresOriginalRampAndAlphaWhileAlternativeSupportsBlack() {
        assertEquals(0xff000000, GlintMask.additive(0xff4b237b, 0xff000000, 100));
        assertEquals(0xff7b7b7b, GlintMask.additive(0xff4b237b, 0xffffffff, 100));
        assertEquals(0x807b0000, GlintMask.additive(0x804b237b, 0xffff0000, 100));
        assertTrue((GlintMask.colorize(0xff4b237b, 0xff000000, 100) >>> 24) > 0);
        assertEquals(0xff000000, GlintMask.additive(0xff4b237b, 0xffffffff, 0));
    }
    @Test void blackAndWhiteHaveIdenticalCoverageAndPreserveChosenRgb() {
        for (int source : new int[]{0xff000000, 0xff010101, 0xff4b237b, 0xffffffff, 0x804b237b}) {
            int black = GlintMask.colorize(source, 0xff000000, 100);
            int white = GlintMask.colorize(source, 0xffffffff, 100);
            assertEquals(black >>> 24, white >>> 24);
            assertEquals(0, black & 0xffffff);
            assertEquals(0xffffff, white & 0xffffff);
        }
        assertEquals(0, GlintMask.colorize(0xff000000, 0xffffffff, 100) >>> 24);
        assertEquals(255, GlintMask.colorize(0xffffffff, 0xff000000, 100) >>> 24);
    }

    @Test void intensityAndPickerOpacityControlCoverageInsteadOfDarkeningRgb() {
        assertEquals(0x80ff0000, GlintMask.colorize(0xffffffff, 0xffff0000, 50));
        assertEquals(0x40ff0000, GlintMask.colorize(0xffffffff, 0x80ff0000, 50));
        assertEquals(0, GlintMask.colorize(0xffffffff, 0xff000000, 0) >>> 24);
        assertEquals(255, GlintMask.colorize(0xffffffff, 0xff000000, 200) >>> 24);
    }

    @Test void texturesWithoutBlackKeepTheirPatternAfterBaseNormalization() {
        assertEquals(0, GlintMask.colorize(0xff303030, 0xff000000, 100, 48, 123) >>> 24);
        assertEquals(255, GlintMask.colorize(0xff7b7b7b, 0xff000000, 100, 48, 123) >>> 24);
        int middle = GlintMask.colorize(0xff555555, 0xff000000, 100, 48, 123) >>> 24;
        assertTrue(middle > 0 && middle < 255);
        assertEquals(255, GlintMask.colorize(0xff303030, 0xff000000, 100, 48, 48) >>> 24);
    }

    @Test void vanillaItemAndArmorTexturesProduceMovingMasksRatherThanSolidTints() throws Exception {
        for (String texture : new String[]{"enchanted_glint_item", "enchanted_glint_armor"}) {
            try (var stream = getClass().getClassLoader().getResourceAsStream(
                    "assets/minecraft/textures/misc/" + texture + ".png")) {
                assertNotNull(stream);
                var image = javax.imageio.ImageIO.read(stream);
                int base = 255, peak = 0;
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getRGB(x, y);
                    int value = Math.max((pixel >>> 16) & 255, Math.max((pixel >>> 8) & 255, pixel & 255));
                    base = Math.min(base, value); peak = Math.max(peak, value);
                }
                java.util.Set<Integer> coverages = new java.util.HashSet<>();
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    int black = GlintMask.colorize(image.getRGB(x, y), 0xff000000, 100, base, peak);
                    int white = GlintMask.colorize(image.getRGB(x, y), 0xffffffff, 100, base, peak);
                    assertEquals(black >>> 24, white >>> 24);
                    coverages.add(black >>> 24);
                }
                assertTrue(coverages.contains(0)); assertTrue(coverages.contains(255));
                assertTrue(coverages.size() > 10, "Pattern transitions must survive " + texture);
            }
        }
    }
}
