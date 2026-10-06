package com.aurora.client.ui.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompactGeometryTest {
    @Test void preferredMainFitsNineCardsAndFiveListRows() {
        var frame = UiDensity.mainFrame(640, 360);
        var body = UiDensity.mainContent(frame);
        assertEquals(344, frame.width()); assertEquals(224, frame.height());
        assertEquals(252, body.width()); assertEquals(176, body.height());
        assertEquals(3, UiDensity.columns(body.width()));
        assertTrue(UiDensity.extent(3, UiDensity.CARD_HEIGHT, UiDensity.GRID_GAP) <= body.height());
        assertTrue(UiDensity.extent(5, UiDensity.LIST_HEIGHT, UiDensity.ROW_GAP) <= body.height());
        assertTrue(UiDensity.extent(4, UiDensity.CARD_HEIGHT, UiDensity.GRID_GAP) > body.height());
        assertTrue(UiDensity.extent(6, UiDensity.LIST_HEIGHT, UiDensity.ROW_GAP) > body.height());
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
            assertTrue(detail.width() <= 344 && detail.height() <= 300);
            assertTrue(detail.x() >= 12 && detail.y() >= 12);
        }
    }
    @Test void sliderAllocationsDoNotOverlapAndFallbackBeforeTrackGetsTooShort() {
        var row = SettingRowLayout.measure(SettingRowLayout.Kind.SLIDER, "Particle Size %", 344, 9, s -> s.length()*6);
        assertTrue(row.inline()); assertEquals(28, row.height());
        assertEquals(144, row.control().width());
        assertTrue(row.label().x()+row.label().width() < row.value().x());
        assertTrue(row.value().x()+row.value().width() < row.control().x());
        var narrow = SettingRowLayout.measure(SettingRowLayout.Kind.SLIDER, "Particle Size %", 240, 9, s -> s.length()*6);
        assertFalse(narrow.inline()); assertEquals(36, narrow.height());
        assertTrue(narrow.control().width() >= UiDensity.MIN_TRACK);
        var longLabel = SettingRowLayout.measure(SettingRowLayout.Kind.SLIDER, "Underwater Fog Multiplier", 344, 9, s -> s.length()*6);
        assertFalse(longLabel.inline());
    }
    @Test void wholeWordsWrapWithoutEscapingTheMeasuredColumn() {
        var row = SettingRowLayout.measure(SettingRowLayout.Kind.BOOLEAN, "Hide Lava Orange Overlay", 180, 9, s -> s.length()*6);
        assertEquals(36, row.height()); assertEquals(2, row.lines().size());
        for (String line : row.lines()) assertTrue(line.length()*6 <= row.label().width());
        assertTrue(row.label().x()+row.label().width() < row.control().x());
        assertEquals(28, row.control().width()); assertEquals(15, row.control().height());
    }
    @Test void enumMeasurementHasNoPopupReservationAndControlTargetsAreHalfOpen() {
        var row = SettingRowLayout.measure(SettingRowLayout.Kind.ENUM, "Style", 344, 9, s -> s.length()*6);
        assertEquals(24, row.height()); assertEquals(18, row.control().height());
        assertEquals(24, row.controlHit().height());
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
