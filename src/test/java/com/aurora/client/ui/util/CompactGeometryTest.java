package com.aurora.client.ui.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompactGeometryTest {
    @Test void uniformWindowShowsAThreeByThreeCardGridAndSevenListRows() {
        var frame = UiDensity.mainFrame(640, 360);
        var body = UiDensity.mainContent(frame);
        assertEquals(336, frame.width()); assertEquals(288, frame.height());
        assertEquals(254, body.width()); assertEquals(252, body.height());
        assertEquals(80, UiDensity.CARD_WIDTH); // feature cards keep their original size
        assertEquals(3, UiDensity.columns(body.width()));
        // the owner's 3×3 target: three card rows fit, a fourth does not
        assertTrue(UiDensity.extent(3, UiDensity.CARD_HEIGHT, UiDensity.GRID_GAP) <= body.height());
        assertTrue(UiDensity.extent(4, UiDensity.CARD_HEIGHT, UiDensity.GRID_GAP) > body.height());
        assertTrue(UiDensity.extent(7, UiDensity.LIST_HEIGHT, UiDensity.LIST_GAP) <= body.height());
        assertTrue(UiDensity.extent(8, UiDensity.LIST_HEIGHT, UiDensity.LIST_GAP) > body.height());
    }
    @Test void everyWindowedScreenSharesOneFrame() {
        for (int[] size : new int[][]{{640,360},{480,270},{960,540},{1280,720}}) {
            var main = UiDensity.mainFrame(size[0], size[1]);
            assertEquals(main, UiDensity.detailFrame(size[0], size[1]));
            assertEquals(main, UiDensity.window(size[0], size[1]));
        }
    }
    @Test void constrainedFramesAndTheirContentStayInsideTheViewport() {
        for (int[] size : new int[][]{{640,360},{320,240},{240,180},{800,600}}) {
            var frame = UiDensity.mainFrame(size[0], size[1]);
            var body = UiDensity.mainContent(frame);
            assertTrue(frame.x() >= 8 && frame.y() >= 8);
            assertTrue(frame.x()+frame.width() <= size[0]-8);
            assertTrue(frame.y()+frame.height() <= size[1]-8);
            assertTrue(body.x() >= frame.x() && body.y() >= frame.y());
            assertTrue(body.x()+body.width() <= frame.x()+frame.width());
            assertTrue(body.y()+body.height() <= frame.y()+frame.height());
            var detail = UiDensity.detailFrame(size[0], size[1]);
            assertTrue(detail.width() <= UiDensity.WINDOW_WIDTH && detail.height() <= UiDensity.WINDOW_HEIGHT);
            assertTrue(detail.x() >= 12 && detail.y() >= 12);
        }
    }
    @Test void sliderAllocationsDoNotOverlapAndTracksStayBounded() {
        // Every slider row is inline with a bounded track (owner direction 2026-11-14): label,
        // then the value right-aligned against the track, whose knob ends on the control edge.
        var row = SettingRowLayout.measure(SettingRowLayout.Kind.SLIDER, "Particle Size %", 344, 9, s -> s.length()*6);
        assertTrue(row.inline()); assertEquals(UiDensity.INLINE_SLIDER, row.height());
        assertEquals(SettingRowLayout.TRACK_W, row.control().width());
        assertEquals(344 - 8 - SettingRowLayout.KNOB_RESERVE, row.control().x() + row.control().width());
        assertTrue(row.label().x()+row.label().width() < row.value().x());
        assertTrue(row.value().x()+row.value().width() < row.control().x() - SettingRowLayout.KNOB_RESERVE);
        var few = SettingRowLayout.measure(SettingRowLayout.Kind.SLIDER, "Gap", 344, 9, s -> s.length()*6,
                SettingRowLayout.trackWidth(10));
        assertEquals(SettingRowLayout.SHORT_TRACK_W, few.control().width());
        assertEquals(SettingRowLayout.TRACK_W, SettingRowLayout.trackWidth(100));
        var longLabel = SettingRowLayout.measure(SettingRowLayout.Kind.SLIDER, "Underwater Fog Multiplier", 240, 9, s -> s.length()*6);
        for (String line : longLabel.lines()) assertTrue(line.length()*6 <= longLabel.label().width());
        assertTrue(longLabel.label().x()+longLabel.label().width() < longLabel.value().x());
    }
    @Test void wholeWordsWrapWithoutEscapingTheMeasuredColumn() {
        var row = SettingRowLayout.measure(SettingRowLayout.Kind.BOOLEAN, "Hide Lava Orange Overlay", 180, 9, s -> s.length()*6);
        assertEquals(UiDensity.STACKED_ROW, row.height()); assertEquals(2, row.lines().size());
        for (String line : row.lines()) assertTrue(line.length()*6 <= row.label().width());
        assertTrue(row.label().x()+row.label().width() < row.control().x());
        assertEquals(UiDensity.TOGGLE_W, row.control().width()); assertEquals(UiDensity.TOGGLE_H, row.control().height());
    }
    @Test void enumMeasurementHasNoPopupReservationAndControlTargetsAreHalfOpen() {
        var row = SettingRowLayout.measure(SettingRowLayout.Kind.ENUM, "Style", 344, 9, s -> s.length()*6);
        assertEquals(UiDensity.SIMPLE_ROW, row.height()); assertEquals(UiDensity.FIELD_H, row.control().height());
        assertEquals(UiDensity.SIMPLE_ROW, row.controlHit().height());
        var hit = row.controlHit();
        assertTrue(hit.contains(hit.x(), hit.y()));
        assertFalse(hit.contains(hit.x()+hit.width(), hit.y()));
        assertFalse(hit.contains(hit.x(), hit.y()+hit.height()));
        assertEquals(0, UiDensity.extent(0, 24, 4));
        assertEquals(52, UiDensity.extent(2, 24, 4));
    }
    @Test void everyAdapterKeepsThePreviousTwelvePixelLabelInset() {
        for (var kind : SettingRowLayout.Kind.values()) {
            var row = SettingRowLayout.measure(kind, "Setting label", 344, 9, s -> s.length()*6);
            assertEquals(12, row.label().x());
            assertTrue(row.label().width() > 0);
            assertTrue(row.control().x()+row.control().width() <= 344);
        }
    }
}
