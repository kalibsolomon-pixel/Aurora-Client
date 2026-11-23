package com.aurora.client.ui.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The main window across every GUI scale Minecraft offers on common displays (owner, 2026-11-23:
 * Settings felt cramped at GUI scale 4 and above). The window keeps its 460×308 cap; a shrunk
 * window gets the 6 px margin on short screens and the icon rail, so its content keeps room.
 */
class WindowViewportMatrixTest {
    private static final int[][] DISPLAYS = {{1366, 768}, {1920, 1080}, {2560, 1440}, {3840, 2160}};

    /** Minecraft's logical GUI size: the framebuffer over the scale, rounded up, for every scale it allows. */
    private static List<int[]> viewports() {
        List<int[]> out = new ArrayList<>();
        for (int[] display : DISPLAYS) {
            for (int scale = 1; display[0] / scale >= 320 && display[1] / scale >= 240; scale++) {
                out.add(new int[]{-Math.floorDiv(-display[0], scale), -Math.floorDiv(-display[1], scale), scale});
            }
        }
        return out;
    }

    @Test void everyReachableViewportKeepsTheWindowInsideItsMargins() {
        for (int[] v : viewports()) {
            String at = v[0] + "x" + v[1] + " (scale " + v[2] + ")";
            var frame = UiDensity.window(v[0], v[1]);
            int margin = UiDensity.windowMargin(v[1]);
            assertTrue(frame.width() <= UiDensity.WINDOW_WIDTH && frame.height() <= UiDensity.WINDOW_HEIGHT, at);
            assertTrue(frame.x() >= margin && frame.y() >= margin, at);
            assertTrue(frame.x() + frame.width() <= v[0] - margin && frame.y() + frame.height() <= v[1] - margin, at);
            var body = UiDensity.mainContent(frame);
            assertTrue(body.x() >= frame.x() + UiDensity.FRAME_EDGE, at);
            assertTrue(body.x() + body.width() + UiDensity.SCROLLBAR_GAP + UiDensity.SCROLLBAR_W
                    <= frame.x() + frame.width() - UiDensity.FRAME_EDGE, at);
            assertTrue(body.y() + body.height() <= frame.y() + frame.height() - UiDensity.FRAME_EDGE, at);
        }
    }

    @Test void shrunkWindowsUseTheRailAndKeepRoomForContent() {
        for (int[] v : viewports()) {
            String at = v[0] + "x" + v[1] + " (scale " + v[2] + ")";
            var frame = UiDensity.window(v[0], v[1]);
            var body = UiDensity.mainContent(frame);
            boolean full = frame.width() == UiDensity.WINDOW_WIDTH && frame.height() == UiDensity.WINDOW_HEIGHT;
            assertEquals(!full && !UiDensity.topNavigation(frame), UiDensity.railNavigation(frame), at);
            if (full) {
                // The full window is unchanged: labelled sidebar, a 4x3 card well, eight list rows.
                assertEquals(new UiDensity.Rect(frame.x() + UiDensity.SIDEBAR + UiDensity.INSET,
                        frame.y() + UiDensity.HEADER + UiDensity.HEADER_BODY_GAP, 338, 252), body, at);
                continue;
            }
            // At least five list rows and three card columns at every reachable scale.
            assertTrue(body.height() >= UiDensity.extent(5, UiDensity.LIST_HEIGHT, UiDensity.LIST_GAP), at + " rows");
            assertTrue(UiDensity.columns(body.width()) >= 3, at + " columns");
            if (UiDensity.railNavigation(frame)) {
                int labelled = frame.width() - UiDensity.SIDEBAR - UiDensity.INSET - UiDensity.scrollRightInset();
                assertEquals(labelled + UiDensity.SIDEBAR + UiDensity.INSET - UiDensity.RAIL - UiDensity.RAIL_INSET,
                        body.width(), at + " rail width");
            }
        }
    }

    @Test void guiScaleFourAtTenEightyIsTheOwnersCrampedCase() {
        // 480x270: full width but short. Before 2026-11-23 the window was 456x246 with a 334x190 column.
        var frame = UiDensity.window(480, 270);
        assertEquals(new UiDensity.Rect(10, 6, 460, 258), frame);
        assertTrue(UiDensity.railNavigation(frame));
        var body = UiDensity.mainContent(frame);
        assertEquals(396, body.width());
        assertEquals(202, body.height());
    }
}
