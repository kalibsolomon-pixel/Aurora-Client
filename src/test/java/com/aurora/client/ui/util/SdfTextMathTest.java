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
        assertEquals(0.0f, SdfTextMath.coverage(-0.5f, 1.0f));
        assertEquals(0.5f, SdfTextMath.coverage(0.0f, 1.0f));
        assertEquals(1.0f, SdfTextMath.coverage(0.5f, 1.0f));
        assertEquals(1.0f, SdfTextMath.coverage(2.0f, 1.0f));
        assertEquals(0.5f, SdfTextMath.coverage(0.0f, 0.0f), "zero derivatives are bounded");
    }

    @Test void paddingMovesBoundsButHasNoAdvanceParameter() {
        assertEquals(12, SdfTextMath.paddedSize(10, 1));
        assertEquals(0.5f, SdfTextMath.paddedLeftBearing(5, 1, 8));
        assertEquals(1.375f, SdfTextMath.paddedTopBearing(10, 1, 8));
        assertEquals(72.0f, 9.0f * 2.0f * SdfTextMath.STRIKE_MULTIPLIER);
    }
}
