package com.aurora.client.theme;

import com.google.gson.JsonParser;
import com.aurora.client.ui.util.UiDensity;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class WindowBevelTest {
    private static final int OUTLINE = 0xff777777;

    @Test void finishesDistinguishClearGlassTintedGemAndTightMetal() {
        var glass = resolvedFinish(0xff16171d, .18, 24);
        var gem = resolvedFinish(0xff67249b, .50, 12);
        var metal = resolvedFinish(0xff777777, .85, 2);
        assertTrue(gem.specGain() > glass.specGain());
        assertTrue(metal.specGain() > gem.specGain());
        assertTrue(metal.specPower() > gem.specPower());
        assertTrue(metal.ramp() > gem.ramp());
        assertTrue((gem.color() & 255) - (gem.color() >>> 8 & 255) > 30);
        // The rim follows its window's own fill (owner, 2026-11-17): clear glass, solid metal.
        assertTrue((glass.color() >>> 24) < (metal.color() >>> 24));
        assertTrue((metal.color() >>> 24) <= 0xeb);
        assertTrue(resolvedFinish(0xff777777, .85, 32).specPower() < metal.specPower());
    }

    private static WindowBevel.Finish resolvedFinish(int color, double opacity, int blur) {
        var definition = ThemeDefinition.defaults();
        definition.background = color;
        definition.backgroundOpacity = opacity;
        definition.blurRadius = blur;
        var theme = ThemeResolver.resolve(definition, true);
        return WindowBevel.finish(theme.color(ThemeToken.WINDOW_FILL), theme.color(ThemeToken.MATERIAL_BORDER_STRONG), blur);
    }

    @Test void oneStrengthSlightlyLighterThanTheFormerSubtleLevel() {
        // Owner, 2026-11-18: no levels; Subtle (width .73, gain .72, elevation 1.5, bend .7) read
        // best, and the single strength is a little lighter still.
        assertTrue(WindowBevel.WIDTH < .73 && WindowBevel.WIDTH > .5);
        assertTrue(WindowBevel.GAIN < .72 && WindowBevel.GAIN > .5);
        assertTrue(WindowBevel.ELEVATION < 1.5 && WindowBevel.ELEVATION > 1);
        assertTrue(WindowBevel.BEND < .7 && WindowBevel.BEND > 0);
    }

    @Test void finishVariesGraduallyAcrossTheOpacityRangeWithoutASecondAlpha() {
        var previous = WindowBevel.finish(0x008322a2, OUTLINE, 9);
        for (int alpha = 1; alpha <= 255; alpha++) {
            var finish = WindowBevel.finish(alpha << 24 | 0x8322a2, OUTLINE, 9);
            // Derived from WINDOW_FILL itself (not a second opacity control): never below the fill,
            // capped at the former solid 92%, continuous across the range.
            int expected = (int) Math.round(255 * Math.min(.92, alpha / 255d + (1 - alpha / 255d) * .10));
            assertEquals(expected, finish.color() >>> 24);
            assertTrue(Math.abs(finish.specGain() - previous.specGain()) < .015);
            assertTrue(Math.abs(finish.specPower() - previous.specPower()) < .1);
            for (int shift : new int[]{0, 8, 16})
                assertTrue(Math.abs((finish.color() >>> shift & 255) - (previous.color() >>> shift & 255)) <= 2);
            previous = finish;
        }
    }

    @Test void bandRespectsStraightAndCornerClearancesAndSmallSurfaces() {
        // The band is the natural width (at most 8 px) scaled by the one strength, and never wider
        // than the content clearance it is given.
        assertEquals(8 * WindowBevel.WIDTH, WindowBevel.bandWidth(480, 10, 16), 1e-9);
        assertEquals(4 * WindowBevel.WIDTH, WindowBevel.bandWidth(288, 10, WindowBevel.cornerClearance(10, 12, 4)), 1e-9);
        assertEquals(4 * WindowBevel.WIDTH, WindowBevel.bandWidth(288, 0, WindowBevel.cornerClearance(0, 12, 4)), 1e-9);
        assertTrue(WindowBevel.cornerClearance(20, 12, 4) < 4);
        assertEquals(0, WindowBevel.bandWidth(288, 10, 0));
        assertTrue(WindowBevel.bandWidth(10, 10, 6) <= 10 * MaterialLighting.MAX_BAND_SHARE);
        assertTrue(WindowBevel.bandWidth(48, 10, 8) > WindowBevel.bandWidth(48, 0, 8));
    }

    @Test void mainAndDenseOverlayContentStayOutsideTheBand() {
        for (int viewport : new int[]{300, 500}) {
            var frame = UiDensity.window(viewport, viewport);
            assertEquals(viewport == 300, frame.width() < 320);
            for (int radius : new int[]{0, ThemeRoundness.DEFAULT_RADIUS}) {
                // Main window content keeps the shared frame room (2026-11-18).
                assertContentClear(frame.width(), frame.height(), radius, UiDensity.FRAME_EDGE, UiDensity.FRAME_EDGE);
                assertContentClear(84, 48, ThemeRoundness.of(radius).radiusSmall(), 2, 2);
            }
        }
    }

    private static void assertContentClear(int width, int height, float radius, float left, float top) {
        int fill = 0x5416171d;
        var finish = WindowBevel.finish(fill, OUTLINE, 9);
        double clearance = WindowBevel.cornerClearance(radius, left, top);
        for (double pixelSize : new double[]{1, 1d / 3, 1.5}) {
            double band = WindowBevel.rasterBandWidth(Math.min(width, height), radius, clearance, pixelSize);
            var raster = new WindowBevel.Raster(width, height, radius, band, pixelSize, finish);
            if (clearance < pixelSize / Math.sqrt(2)) assertEquals(0, band);
            for (double dx : new double[]{left, width - left, width * .5}) {
                for (double dy : new double[]{top, height - top, height * .5}) {
                    for (double offsetX : new double[]{-pixelSize * .5, pixelSize * .5}) {
                        for (double offsetY : new double[]{-pixelSize * .5, pixelSize * .5}) {
                            assertEquals(0, raster.pixel(dx + offsetX, dy + offsetY, 0), "bevel overlaps content backing");
                        }
                    }
                }
            }
        }
    }

    @Test void plateauPreservesTheExactTintAndLightAndShadowAreDirectional() {
        int fill = 0x80808080;
        var finish = WindowBevel.finish(fill, OUTLINE, 12);
        for (float radius : new float[]{0, 10}) {
            var raster = new WindowBevel.Raster(50, 40, radius, 4, .25, finish);
            for (int row = 0; row < 40; row++) for (int column = 0; column < 50; column++) {
                if (MaterialLighting.distance(column + .5, row + .5, 50, 40, radius) <= -4)
                    assertEquals(fill, raster.pixel(column + .5, row + .5, fill));
            }
            assertTrue((raster.pixel(25, 1, fill) & 255) > (raster.pixel(25, 39, fill) & 255));
            assertTrue((raster.pixel(25, 41, fill) >>> 24) > (raster.pixel(25, -1, fill) >>> 24));
            assertTrue((raster.pixel(51, 20, fill) >>> 24) > (raster.pixel(-1, 20, fill) >>> 24));
        }
    }

    @Test void retiredBevelLevelsLeaveConfigsAndProfiles() {
        var log = LoggerFactory.getLogger(WindowBevelTest.class);
        for (String member : new String[]{"", ",\"windowBevel\":null", ",\"windowBevel\":{}", ",\"windowBevel\":\"DEEP\""}) {
            String theme = "{\"cornerRadius\":10,\"blurRadius\":9" + member + "}";
            var config = JsonParser.parseString(ThemeMigrator.migrateConfigJson("{\"theme\":" + theme + "}", log)).getAsJsonObject();
            assertFalse(config.getAsJsonObject("theme").has("windowBevel"), member);
            assertEquals(10, config.getAsJsonObject("theme").get("cornerRadius").getAsInt());
            var fields = new HashMap<>(java.util.Map.of("theme", JsonParser.parseString(theme)));
            ThemeMigrator.migrateProfileFields(fields, log);
            assertFalse(fields.get("theme").getAsJsonObject().has("windowBevel"), member);
        }
    }
}
