package com.aurora.client.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TabListStyleTest {

    @Test void pingColumnWidthExpandsOnlyForNumericPing() {
        assertEquals(TabListStyle.VANILLA_PING_WIDTH, TabListStyle.pingColumnWidth(false, false));
        assertEquals(TabListStyle.VANILLA_PING_WIDTH, TabListStyle.pingColumnWidth(false, true));
        assertEquals(TabListStyle.NUMERIC_PING_WIDTH, TabListStyle.pingColumnWidth(true, false));
        assertEquals(TabListStyle.NUMERIC_PING_MS_WIDTH, TabListStyle.pingColumnWidth(true, true));
    }

    @Test void formatPingHandlesUnknownAndSuffix() {
        assertNull(TabListStyle.formatPing(-1, false));
        assertNull(TabListStyle.formatPing(-50, true));
        assertEquals("0", TabListStyle.formatPing(0, false));
        assertEquals("0ms", TabListStyle.formatPing(0, true));
        assertEquals("42", TabListStyle.formatPing(42, false));
        assertEquals("42ms", TabListStyle.formatPing(42, true));
    }

    @Test void opacityAndFillColorsClampAndScaleCleanly() {
        assertEquals(0.0, TabListStyle.clampOpacity(-0.5));
        assertEquals(1.0, TabListStyle.clampOpacity(1.8));
        assertEquals(TabListStyle.DEFAULT_BG_OPACITY, TabListStyle.clampOpacity(Double.NaN));

        assertEquals(0x00000000, TabListStyle.panelFillColor(0.0));
        assertEquals(0x80000000, TabListStyle.panelFillColor(0.5));
        assertEquals(0xFF000000, TabListStyle.panelFillColor(1.0));

        assertEquals(0x00FFFFFF, TabListStyle.rowFillColor(0.0));
        assertEquals(0x20FFFFFF, TabListStyle.rowFillColor(0.5));
        assertEquals(0x40FFFFFF, TabListStyle.rowFillColor(1.0));
    }

    @Test void selfRowColorsPreserveRgbAndSetExpectedAlpha() {
        assertEquals(0x385B8DEF, TabListStyle.selfRowWashColor(0xFF5B8DEF));
        assertEquals(0xFF5B8DEF, TabListStyle.selfRowEdgeColor(0x125B8DEF));
    }
}
