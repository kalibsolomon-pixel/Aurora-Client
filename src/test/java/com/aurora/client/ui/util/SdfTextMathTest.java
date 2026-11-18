package com.aurora.client.ui.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SdfTextMathTest {
    @Test void decodesFreeTypesDocumentedByteMapping() {
        assertEquals(-8.0f, SdfTextMath.signedDistance(0, 8));
        assertEquals(0.0f, SdfTextMath.signedDistance(128, 8));
        assertEquals(127.0f / 16.0f, SdfTextMath.signedDistance(255, 8));
        assertEquals(-4.0f, SdfTextMath.signedDistance(64, 8));
        assertEquals(-8.0f, SdfTextMath.signedDistance(-1, 8), "input is unsigned-byte clamped");
    }

    @Test void coverageUsesDerivativesAndClampsAtBothEdges() {
        assertEquals(0.0f, SdfTextMath.coverage(-2.0f, 1.0f));
        assertEquals(0.2f, SdfTextMath.coverage(0.0f, 1.0f), 0.00001f);
        assertEquals(0.5f, SdfTextMath.coverage(SdfTextMath.EDGE_BIAS, 1.0f));
        assertEquals(1.0f, SdfTextMath.coverage(0.8f, 1.0f));
        assertEquals(1.0f, SdfTextMath.coverage(2.0f, 1.0f));
        assertEquals(0.5f, SdfTextMath.coverage(SdfTextMath.EDGE_BIAS, 0.0f), "zero derivatives are bounded");
    }

    @Test void paddingMovesBoundsButHasNoAdvanceParameter() {
        int padding = SdfTextMath.SPREAD + SdfTextMath.PADDING;
        assertEquals(28, SdfTextMath.paddedSize(10, padding));
        assertEquals(0.875f, SdfTextMath.quadStart(2, padding, 0.125f));
        assertEquals(16.5f / 256, SdfTextMath.insetUv(16, false, 256));
        assertEquals(43.5f / 256, SdfTextMath.insetUv(44, true, 256));
        assertEquals(0f, SdfTextMath.insetUv(-1, false, 256));
        assertEquals(1f, SdfTextMath.insetUv(257, true, 256));
    }
}
