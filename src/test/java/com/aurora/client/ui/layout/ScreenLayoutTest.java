package com.aurora.client.ui.layout;

import com.aurora.client.ui.util.ClipBand;
import com.aurora.client.ui.util.SettingRowLayout;
import com.aurora.client.ui.util.UiDensity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScreenLayoutTest {
    @Test
    void stableIdsClipsFocusAndReveal() {
        var band = new ClipBand(20, 30, 100, 40);
        var layout = new ScreenLayout();
        layout.begin(band);
        var toggle = layout.place("toggle-key", "toggle", 20, 30, 100, 18, true);
        layout.place("caption", "caption", 20, 48, 100, 12, false);
        var end = layout.place("end-key", "end", 20, 100, 100, 18, true);
        layout.finish(band, 110);
        assertSame(toggle, layout.byId("toggle"));
        assertSame(toggle, layout.hitTest(20, 30, 0));
        assertNull(layout.hitTest(120, 30, 0));
        assertNull(layout.hitTest(20, 70, 0));
        assertFalse(layout.visible(end, 30));
        assertTrue(layout.visible(end, 31));
        assertSame(end, layout.hitTest(20, 69, 31));
        assertEquals(2, layout.focusCount());
        assertSame(end, layout.focusAt(1));
        assertEquals(56, layout.reveal(end, 0, 8));
        assertEquals(70, layout.maxScroll());

        layout.begin(band);
        layout.place("end-key", "end", 20, 30, 100, 18, true);
        layout.finish(band, 18);
        assertNull(layout.byId("toggle"));
        assertSame(end, layout.focusAt(0));
        assertEquals(0, layout.maxScroll());

        layout.begin(band);
        layout.place("end-key", "end", 20, 30, 100, 18, true);
        assertSame(toggle, layout.place("toggle-key", "toggle", 20, 50, 100, 18, true));
        assertSame(toggle, layout.focusAt(1));
        assertEquals(50, toggle.top());
    }

    @Test
    void overviewCardsAndManagerCellsMatchLegacyFormulas() {
        for (int[] viewport : new int[][]{{220, 200}, {319, 241}, {360, 312}, {853, 481}, {1280, 720}}) {
            var frame = UiDensity.window(viewport[0], viewport[1]);
            var content = UiDensity.mainContent(frame);
            int mainX = frame.x() + (frame.width() < 320 ? 8 : UiDensity.SIDEBAR + UiDensity.INSET);
            var band = new ClipBand(content.x(), content.y(), content.width(), content.height());
            for (boolean grid : new boolean[]{false, true}) {
                int columns = grid ? UiDensity.columns(content.width()) : 1;
                int height = grid ? UiDensity.CARD_HEIGHT : UiDensity.LIST_HEIGHT;
                int width = grid ? Math.min(UiDensity.CARD_WIDTH, content.width()) : content.width();
                int gap = grid ? UiDensity.GRID_GAP : UiDensity.LIST_GAP;
                var layout = new ScreenLayout();
                layout.begin(band);
                for (int slot = 0; slot < 39; slot++) {
                    var box = layout.cell(slot, null, slot, columns, mainX, content.y(), width, height, gap, gap);
                    for (double scroll : new double[]{0, 1.25, 38.75, 119.625, 250.99995}) {
                        float oldX = mainX + (grid ? slot % columns * (width + gap) : 0);
                        float oldY = (float) (content.y() + (grid ? slot / columns : slot) * (height + gap) - scroll);
                        assertBounds(box, scroll, oldX, oldY, width, height);
                        assertEquals(band.intersects(oldX, oldY, width, height), layout.visible(box, scroll));
                    }
                }
            }
            int width = frame.width() - 16, top = frame.y() + UiDensity.HEADER + 6;
            int bottom = frame.y() + frame.height() - 8, gap = 4, height = 34;
            int left = (viewport[0] - width) / 2;
            for (int columns : new int[]{1, 2, 3}) {
                int cellWidth = (width - gap * (columns - 1)) / columns;
                var clip = new ClipBand(left, top - 2, width, bottom - top + 2);
                var layout = new ScreenLayout();
                layout.begin(clip);
                for (int slot = 0; slot < 18; slot++) {
                    var box = layout.cell(slot, null, slot, columns, left, top, cellWidth, height, gap, gap);
                    for (double current : new double[]{0, 3.5, 37.75, 89.99}) {
                        int oldY = top - (int) current + slot / columns * (height + gap);
                        int oldX = left + slot % columns * (cellWidth + gap);
                        assertBounds(box, (int) current, oldX, oldY, cellWidth, height);
                    }
                }
                int legacyExtent = (18 + columns - 1) / columns * (height + gap);
                layout.finish(clip, legacyExtent, bottom - top);
                assertEquals(Math.max(0, legacyExtent - (bottom - top)), layout.maxScroll());
            }
        }
    }

    @Test
    void replacedObjectsResolveToTheSameLogicalBox() {
        var band = new ClipBand(0, 0, 200, 40);
        var layout = new ScreenLayout();
        Object oldWaypoint = new Object(), waypoint = new Object();
        layout.begin(band);
        var original = layout.cell(oldWaypoint, "waypoint/uuid", 0, 1, 0, 0, 200, 34, 4, 4);
        layout.begin(band);
        assertFalse(layout.visible(original, 0));
        assertSame(original, layout.cell(waypoint, "waypoint/uuid", 1, 1, 0, 0, 200, 34, 4, 4));
        assertNull(layout.bounds(oldWaypoint));
        assertSame(waypoint, layout.hitTest(10, 10, 38).key());
        assertSame(original, layout.byId("waypoint/uuid"));
        assertThrows(IllegalArgumentException.class,
                () -> layout.cell(new Object(), "waypoint/uuid", 2, 1, 0, 0, 200, 34, 4, 4));
    }

    @Test
    void detailStackMatchesLegacyWalkWhenSectionsCollapseAndHeaderStaysPinned() {
        int[][] features = {{18, 22, 34, 22, 18, 26}, {24, 150, 18, 24}, {98}};
        for (int[] viewport : new int[][]{{220, 180}, {854, 480}}) {
            var frame = UiDensity.detailFrame(viewport[0], viewport[1]);
            for (int feature = 0; feature < features.length; feature++) {
                var layout = new ScreenLayout();
                int fixed = feature == 2 ? 32 : 0;
                int top = frame.y() + UiDensity.HEADER + 12 + fixed;
                int width = frame.width() - 16, left = frame.x() + 8;
                var band = new ClipBand(left, top, width, Math.max(1, frame.y() + frame.height() - top - 12));
                for (boolean compact : new boolean[]{true, false}) for (boolean collapsed : new boolean[]{false, true, false}) {
                    layout.begin(band);
                    int nextTop = top, legacyExtent = -UiDensity.ROW_GAP;
                    for (int index = 0; index < features[feature].length; index++) {
                        boolean hidden = collapsed && index >= 2 && index < 4;
                        int height = features[feature][index] + (compact ? 0 : 8) - (index == 0 ? fixed : 0);
                        String id = "setting/" + index;
                        if (hidden) {
                            assertNull(layout.byId(id));
                            continue;
                        }
                        nextTop = layout.stack(id, id, left, nextTop, width, height, UiDensity.ROW_GAP, true);
                        for (double scroll : new double[]{0, 7.5, 38.625, 91.125}) {
                            int legacyY = top - (int) scroll + legacyExtent + UiDensity.ROW_GAP;
                            var box = layout.byId(id);
                            assertBounds(box, (int) scroll, left, legacyY, width, height);
                            assertEquals(band.intersects(left, legacyY, width, height), layout.visible(box, (int) scroll));
                            int visibleTop = Math.max(legacyY, band.y);
                            if (layout.visible(box, (int) scroll))
                                assertSame(box, layout.hitTest(left, visibleTop, (int) scroll));
                        }
                        legacyExtent += height + UiDensity.ROW_GAP;
                    }
                    layout.finish(band, nextTop - top - UiDensity.ROW_GAP);
                    assertEquals(legacyExtent, layout.extent());
                    assertEquals(Math.max(0, legacyExtent - band.height), layout.maxScroll());
                }
            }
        }
    }

    @Test
    void settingsRowsRetainOffsetsAcrossWidthFontAndScroll() {
        var kinds = SettingRowLayout.Kind.values();
        String[] names = {"Toggle with a long explanation", "Slider for a precise setting", "Dropdown with a long choice name",
                "Toggle this feature with a key combo", "Colour for the selected marker", "Restore default options",
                "Open another screen whenever available", "Section title"};
        for (int width : new int[]{124, 186, 254, 336}) for (int pitch : new int[]{9, 11}) {
            int top = 92;
            var layout = new ScreenLayout();
            ClipBand band = new ClipBand(28, top, width - 16, 132);
            layout.begin(band);
            int nextTop = top;
            for (int index = 0; index < kinds.length; index++) {
                var measured = SettingRowLayout.measure(kinds[index], names[index], width, pitch,
                        text -> text.length() * (pitch - 3), SettingRowLayout.TRACK_W);
                nextTop = layout.stack(index, null, band.x, nextTop, band.width, measured.height(), UiDensity.ROW_GAP, true);
            }
            layout.finish(band, nextTop - top - UiDensity.ROW_GAP);
            for (double scroll : new double[]{0, 0.25, 32.625, 119.875}) {
                int oldDetailY = top - (int) scroll;
                float oldInlineY = top - (float) scroll;
                for (int index = 0; index < layout.size(); index++) {
                    var box = layout.at(index);
                    assertEquals(oldDetailY, box.y((int) scroll));
                    assertEquals(oldInlineY, box.y((float) scroll));
                    oldDetailY += (int) box.height() + UiDensity.ROW_GAP;
                    oldInlineY += box.height() + UiDensity.ROW_GAP;
                }
            }
        }
    }

    private static void assertBounds(ScreenLayout.Box box, double scroll, float x, float y, float width, float height) {
        assertEquals(x, box.x());
        assertEquals(y, box.y(scroll));
        assertEquals(width, box.width());
        assertEquals(height, box.height());
    }
}
