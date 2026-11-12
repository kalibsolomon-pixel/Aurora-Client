package com.aurora.client.ui.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShapeShaderGeometryTest {
    @Test void paddingCoversOnePhysicalPixelAcrossScaleRotationAndShear() {
        assertEquals(.5f, ShapeShaderGeometry.padding(2, 1, 0, 0, 1), 1e-6f);
        assertEquals(2f, ShapeShaderGeometry.padding(2, .25f, 0, 0, .25f), 1e-6f);
        assertEquals(.5f, ShapeShaderGeometry.padding(2, 0, 1, -1, 0), 1e-6f);
        float shearPadding = ShapeShaderGeometry.padding(2, 1, 0, 1, 1);
        // The smallest stretch of this shear is (sqrt(5)-1)/2.
        assertEquals((float) ((1 + Math.sqrt(5)) / 4), shearPadding, 1e-6f);
    }

    @Test void unsafeOrDegenerateTransformsDeclineTheShaderPath() {
        assertEquals(0, ShapeShaderGeometry.padding(2, 1, 0, 0, 0));
        assertEquals(0, ShapeShaderGeometry.padding(2, .001f, 0, 0, .001f));
        assertEquals(0, ShapeShaderGeometry.padding(Float.NaN, 1, 0, 0, 1));
        assertEquals(0, ShapeShaderGeometry.padding(2, Float.NaN, 0, 0, 1));
    }
}
