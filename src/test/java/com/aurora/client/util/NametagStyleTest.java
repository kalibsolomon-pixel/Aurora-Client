package com.aurora.client.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NametagStyleTest {

    @Test void scaleClampsWithinBounds() {
        assertEquals(NametagStyle.MIN_SCALE, NametagStyle.clampScale(0.1));
        assertEquals(NametagStyle.MAX_SCALE, NametagStyle.clampScale(5.0));
        assertEquals(NametagStyle.DEFAULT_SCALE, NametagStyle.clampScale(Double.NaN));
        assertEquals(1.25, NametagStyle.clampScale(1.25));
    }

    @Test void scaledComponentPreservesSignAndVanillaAtOne() {
        assertEquals(0.025f, NametagStyle.scaledComponent(0.025f, 1.0), 1e-6f);
        assertEquals(-0.025f, NametagStyle.scaledComponent(-0.025f, 1.0), 1e-6f);
        assertEquals(0.05f, NametagStyle.scaledComponent(0.025f, 2.0), 1e-6f);
        assertEquals(-0.0125f, NametagStyle.scaledComponent(-0.025f, 0.5), 1e-6f);
    }

    @Test void opacityClampsAndDefaultsOnNaN() {
        assertEquals(0.0, NametagStyle.clampOpacity(-1.0));
        assertEquals(1.0, NametagStyle.clampOpacity(2.5));
        assertEquals(NametagStyle.DEFAULT_BG_OPACITY, NametagStyle.clampOpacity(Double.NaN));
        assertEquals(0.6, NametagStyle.clampOpacity(0.6));
    }
}
