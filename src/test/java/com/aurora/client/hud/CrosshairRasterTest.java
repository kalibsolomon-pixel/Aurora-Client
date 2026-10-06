package com.aurora.client.hud;

import com.aurora.client.config.AuroraConfig.CrosshairStyle;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CrosshairRasterTest {
    private static Map<String, Integer> pixels(CrosshairStyle style, int size, int thickness, int gap, int scale) {
        Map<String, Integer> pixels = new HashMap<>();
        for (var run : CrosshairRaster.raster(style, size, thickness, gap, scale)) {
            assertTrue(run.width() > 0);
            for (int x = run.x(); x < run.x() + run.width(); x++)
                assertNull(pixels.put(x + "," + run.y(), run.alpha()), "Runs must not double-blend pixels");
        }
        return pixels;
    }

    @Test void shapesStaySymmetricAndVisibleAcrossGuiScalesAndExtremeGaps() {
        for (var style : new CrosshairStyle[]{CrosshairStyle.DOT, CrosshairStyle.CIRCLE, CrosshairStyle.SQUARE})
            for (int scale : new int[]{1, 2, 3, 4})
                for (int size : new int[]{1, 5, 20})
                    for (int gap : new int[]{0, 2, 10}) {
                        var pixels = pixels(style, size, 1, gap, scale);
                        assertFalse(pixels.isEmpty(), style + " must not vanish");
                        for (var entry : pixels.entrySet()) {
                            String[] xy = entry.getKey().split(",");
                            int x = Integer.parseInt(xy[0]), y = Integer.parseInt(xy[1]);
                            assertEquals(entry.getValue(), pixels.get((-x - 1) + "," + y));
                            assertEquals(entry.getValue(), pixels.get(x + "," + (-y - 1)));
                            assertEquals(entry.getValue(), pixels.get(y + "," + x));
                        }
                    }
    }

    @Test void dotIsFilledRoundAndAntialiasedRatherThanSquare() {
        var pixels = pixels(CrosshairStyle.DOT, 10, 1, 0, 3);
        assertEquals(255, pixels.get("0,0"));
        assertNull(pixels.get("14,14"));
        assertTrue(pixels.values().stream().anyMatch(alpha -> alpha > 0 && alpha < 255));
    }

    @Test void circleThicknessAddsCoverageWithoutChangingItsOuterBounds() {
        var thin = pixels(CrosshairStyle.CIRCLE, 10, 1, 0, 3);
        var thick = pixels(CrosshairStyle.CIRCLE, 10, 3, 0, 3);
        assertTrue(thick.size() > thin.size());
        assertEquals(thin.keySet().stream().mapToInt(k -> Integer.parseInt(k.split(",")[0])).max(),
                thick.keySet().stream().mapToInt(k -> Integer.parseInt(k.split(",")[0])).max());
        assertNull(thick.get("0,0"));
    }

}
