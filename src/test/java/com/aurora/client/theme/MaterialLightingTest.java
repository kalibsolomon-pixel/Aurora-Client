package com.aurora.client.theme;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialLightingTest {
    @Test void absoluteLightnessPointsOnNeutralAndSaturatedFaces() {
        assertEquals(0xff8d8d8d, MaterialLighting.lightness(0xff808080, 5));
        assertEquals(0xffff1a1a, MaterialLighting.lightness(0xffff0000, 5));
        assertEquals(0xffe60000, MaterialLighting.lightness(0xffff0000, -5));
    }

    @Test void preservesAlphaIncludingTransparentCoverage() {
        for (int alpha : new int[]{0, 1, 19, 84, 128, 254, 255}) {
            int c = alpha << 24 | 0x3987ae;
            assertEquals(alpha, MaterialLighting.lightness(c, 5) >>> 24);
            assertEquals(alpha, MaterialLighting.edge(c, 0x38ffffff) >>> 24);
        }
        assertEquals(0x00123456, MaterialLighting.lightness(0x00123456, 50));
    }

    @Test void hueAndSaturationSurviveAcrossAccentHues() {
        for (int c : new int[]{0xffc24671,0xff1fbcdf,0xffaf43de,0xff58c970,0xffba9a41}) {
            double[] before = hsl(c), after = hsl(MaterialLighting.lightness(c, 5));
            assertEquals(before[0], after[0], .008);
            assertEquals(before[1], after[1], .015);
            assertEquals(before[2] + .05, after[2], .003);
        }
    }

    @Test void clampsLightnessAtEndpointsAndKeepsZeroDeltaExact() {
        assertEquals(0x81ffffff, MaterialLighting.lightness(0x81aa33cc, 100));
        assertEquals(0x81000000, MaterialLighting.lightness(0x81aa33cc, -100));
        assertEquals(0xff123456, MaterialLighting.lightness(0xff123456, 0));
        assertEquals(0xff0d0d0d, MaterialLighting.lightness(0xff000000, 5));
        assertEquals(0xfff2f2f2, MaterialLighting.lightness(0xffffffff, -5));
    }

    @Test void verticalSamplingUsesPhysicalRowEndpoints() {
        assertEquals(5, MaterialLighting.verticalDelta(0, 12, 5, -3));
        assertEquals(-3, MaterialLighting.verticalDelta(11, 12, 5, -3));
        assertEquals(1, MaterialLighting.verticalDelta(1, 3, 5, -3));
        assertEquals(1, MaterialLighting.verticalDelta(0, 1, 5, -3));
        assertEquals(-3, MaterialLighting.verticalDelta(17, 20, -3, -3));
    }

    @Test void edgeOverlayUsesCoverageWithoutGrowingIt() {
        assertEquals(0x00808080, MaterialLighting.edge(0x00808080, 0xffffffff));
        assertEquals(0x818d8d8d, MaterialLighting.edge(0x81808080, 0x1affffff));
        assertEquals(0xff646464, MaterialLighting.edge(0xff808080, 0x38000000));
    }

    @Test void shadowIsBehindFaceAndBlackOutsideIt() {
        assertEquals(0xffabcdef, MaterialLighting.shadowBehind(0xffabcdef, .14f));
        assertEquals(0x24000000, MaterialLighting.shadowBehind(0, .14f));
        int edge = MaterialLighting.shadowBehind(0x80808080, .14f);
        assertTrue((edge >>> 24) > 128);
        assertTrue((edge & 255) < 128);
        assertEquals(0, MaterialLighting.shadowBehind(0, 0));
    }

    private static double[] hsl(int c) {
        double r = (c >>> 16 & 255) / 255d, g = (c >>> 8 & 255) / 255d, b = (c & 255) / 255d;
        double max = Math.max(r,Math.max(g,b)), min = Math.min(r,Math.min(g,b)), d = max-min;
        double l = (max+min)/2, s = d == 0 ? 0 : d/(1-Math.abs(2*l-1));
        double h = d == 0 ? 0 : max==r ? ((g-b)/d+6)%6 : max==g ? (b-r)/d+2 : (r-g)/d+4;
        return new double[]{h/6,s,l};
    }
}
