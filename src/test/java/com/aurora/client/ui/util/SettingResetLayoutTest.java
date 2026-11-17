package com.aurora.client.ui.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SettingResetLayoutTest {
    @Test void resetFitsEverySimpleColumnWithoutMovingValuesOrControls() {
        for (int width : new int[]{190, 216, 246, 312, 344, 640}) {
            for (int lineHeight : new int[]{8, 9}) {
                for (var kind : new SettingRowLayout.Kind[]{SettingRowLayout.Kind.BOOLEAN, SettingRowLayout.Kind.SLIDER,
                        SettingRowLayout.Kind.ENUM, SettingRowLayout.Kind.KEYBIND, SettingRowLayout.Kind.COLOR}) {
                    var row = SettingRowLayout.measure(kind, "Disable VSync (causes tearing without VRR)", width, lineHeight, s -> s.length() * 5);
                    var label = row.label();
                    var reset = SettingRowLayout.reset(label.x(), label.y(), label.width(), label.height(), Integer.MAX_VALUE);
                    assertEquals(label.width() - 22, reset.label().width());
                    assertTrue(reset.action().x() >= label.x() + reset.label().width() + 4);
                    assertTrue(reset.action().x() + reset.action().width() <= label.x() + label.width());
                    assertTrue(reset.action().x() + reset.action().width() < row.control().x());
                    if (kind == SettingRowLayout.Kind.SLIDER)
                        assertTrue(reset.action().x() + reset.action().width() < row.value().x());
                    assertEquals(18, reset.action().width());
                    assertEquals(18, reset.action().height());
                    assertTrue(reset.action().contains(reset.action().x(), reset.action().y()));
                    assertFalse(reset.action().contains(reset.action().x() + 18, reset.action().y()));
                    assertEquals(label, row.label()); // source row geometry is immutable
                }
            }
        }
    }

    @Test void shortLabelsPutTheIconImmediatelyAfterTextWithoutAnEmptySlot() {
        var reset = SettingRowLayout.reset(12, 0, 220, 28, 24);
        assertEquals(40, reset.action().x());
        assertEquals(5, reset.action().y());
        assertEquals(198, reset.label().width());
    }

    @Test void normalAndSegmentedHeadersUseTheSameTargetAsCompactRows() {
        for (int height : new int[]{12, 18, 20, 28, 36}) {
            var reset = SettingRowLayout.reset(12, 6, 120, height, 200);
            assertEquals(98, reset.label().width());
            assertEquals(114, reset.action().x());
            assertEquals(6 + (height - 18) / 2, reset.action().y());
        }
    }
}
