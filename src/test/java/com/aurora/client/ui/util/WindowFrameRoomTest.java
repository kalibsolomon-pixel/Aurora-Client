package com.aurora.client.ui.util;

import com.aurora.client.SourceTree;
import com.aurora.client.theme.ThemeRoundness;
import com.aurora.client.theme.WindowBevel;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Owner, 2026-11-18: every window pads its content for the bevel band instead of thinning the band. */
class WindowFrameRoomTest {
    private static final int OUTLINE = 0xff3a3d4a;
    private static final Pattern CLEARANCE = Pattern.compile("windowCornerClearance\\([^,]+,\\s*(\\d+),\\s*(\\d+)\\)");

    @Test void theFrameRoomHoldsTheBandAndItsGap() {
        assertEquals(UiDensity.FRAME_EDGE, UiDensity.FRAME_BAND + UiDensity.FRAME_GAP);
        assertEquals(UiDensity.FRAME_EDGE + UiDensity.TOOL, UiDensity.HEADER);
        assertEquals(UiDensity.FRAME_EDGE, UiDensity.headerTop(UiDensity.HEADER_BUTTON_H));
        assertEquals(UiDensity.FRAME_EDGE + UiDensity.SCROLLBAR_W + UiDensity.SCROLLBAR_GAP, UiDensity.scrollRightInset());
        // A round theme's band takes the whole cap; a very round one narrows where the corner bends.
        assertEquals(UiDensity.FRAME_BAND, WindowBevel.frameInset(0), 1e-6);
        assertEquals(UiDensity.FRAME_BAND, WindowBevel.frameInset(ThemeRoundness.DEFAULT_RADIUS), 1e-6);
        assertTrue(WindowBevel.frameInset(20) < UiDensity.FRAME_BAND);
        assertTrue(WindowBevel.frameInset(20) > 0);
    }

    @Test void theStandardBandIsNoLongerClampedByContent() {
        // Before 2026-11-18 the main window passed 4 and details 3, so Standard drew 2.7-4 px.
        double band = WindowBevel.bandWidth(UiDensity.WINDOW_HEIGHT, ThemeRoundness.DEFAULT_RADIUS,
                WindowBevel.frameInset(ThemeRoundness.DEFAULT_RADIUS), WindowBevel.Level.STANDARD);
        assertEquals(UiDensity.FRAME_BAND, band, 1e-9);
    }

    @Test void contentAtTheFrameRoomStaysClearOfTheBandAtEveryLevelAndRadius() {
        var frame = UiDensity.window(640, 360);
        for (int radius : new int[]{0, ThemeRoundness.DEFAULT_RADIUS, 14, 20}) {
            double cap = WindowBevel.frameInset(radius);
            for (var level : WindowBevel.Level.values()) {
                var finish = WindowBevel.finish(0x5416171d, OUTLINE, 9, level);
                for (double pixel : new double[]{1, 1d / 3, .5}) {
                    double band = WindowBevel.rasterBandWidth(frame.height(), radius, cap, pixel, level);
                    var raster = new WindowBevel.Raster(frame.width(), frame.height(), radius, band, pixel, finish, level);
                    double e = UiDensity.FRAME_EDGE;
                    for (double x : new double[]{e, frame.width() - e, frame.width() * .5})
                        for (double y : new double[]{e, frame.height() - e, frame.height() * .5})
                            assertEquals(0, raster.pixel(x, y, 0), "radius " + radius + " " + level + " at " + x + "," + y);
                }
            }
        }
    }

    @Test void everyWindowFrameTakesItsBandCapFromTheFrameRoom() throws Exception {
        var call = Pattern.compile("(MaterialSurface\\.parent|GlassSurface\\.window)\\(([^;]*?)\\);", Pattern.DOTALL);
        int checked = 0;
        for (var path : (Iterable<java.nio.file.Path>) SourceTree.javaFiles()::iterator) {
            String name = path.getFileName().toString();
            if (name.equals("MaterialSurface.java") || name.equals("GlassSurface.java")) continue;
            var matcher = call.matcher(Files.readString(path));
            while (matcher.find()) {
                checked++;
                // A modal padded wider than the frame room may cap its band at its own clearance.
                var own = CLEARANCE.matcher(matcher.group(2));
                boolean wider = own.find() && Integer.parseInt(own.group(1)) >= UiDensity.FRAME_EDGE
                        && Integer.parseInt(own.group(2)) >= UiDensity.FRAME_EDGE;
                assertTrue(matcher.group(2).contains("frameInset(") || wider, name + ": " + matcher.group());
            }
        }
        assertTrue(checked >= 9, "window frames found: " + checked);
    }
}
