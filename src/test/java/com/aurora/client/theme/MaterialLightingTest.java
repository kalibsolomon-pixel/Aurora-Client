package com.aurora.client.theme;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialLightingTest {
    @Test void roundedSdfAndBandRespectDeviceScaleAndSquareGeometry() {
        assertEquals(-5, MaterialLighting.distance(5,5,10,10,0));
        assertEquals(0, MaterialLighting.distance(0,0,10,10,0));
        assertTrue(MaterialLighting.distance(0,0,10,10,4)>0);
        assertEquals(-5, MaterialLighting.distance(5,5,10,10,5)); // circle
        for(int scale=1;scale<=4;scale++) {
            assertEquals(scale, MaterialLighting.bevelWidth(0,scale,MaterialLighting.Profile.RAISED));
            assertEquals(3*scale, MaterialLighting.bevelWidth(10*scale,scale,MaterialLighting.Profile.RAISED));
            assertEquals(5.5*scale, MaterialLighting.bevelWidth(10*scale,scale,MaterialLighting.Profile.DOME));
        }
        assertEquals(0,MaterialLighting.pillowHeight(0));
        assertEquals(Math.sqrt(.75),MaterialLighting.pillowHeight(.5));
        assertEquals(1,MaterialLighting.pillowHeight(1));
    }

    private static double rim(double x,double y,double radius,MaterialLighting.Profile profile) {
        return MaterialLighting.curvatureDelta(x,y,80,80,radius,1,profile);
    }

    @Test void oneUpperLeftLightCurvesAroundCornersAndLeavesPlateauExact() {
        assertTrue(rim(4,4,10,MaterialLighting.Profile.RAISED)>rim(76,76,10,MaterialLighting.Profile.RAISED));
        assertTrue(rim(40,.5,10,MaterialLighting.Profile.RAISED)>0);
        assertTrue(rim(40,79.5,10,MaterialLighting.Profile.RAISED)<0);
        assertEquals(0,rim(40,40,10,MaterialLighting.Profile.RAISED));
        assertEquals(MaterialLighting.lightness(0x813987ae,7),
                MaterialLighting.face(0x813987ae,39,39,80,80,10,1,7,7,MaterialLighting.Profile.RAISED));
        assertTrue(rim(40,.5,10,MaterialLighting.Profile.INSET)<0);
        assertTrue(rim(40,79.5,10,MaterialLighting.Profile.INSET)>0);
    }

    @Test void squareCornerSwitchesFacetAtDiagonalWhileRoundedCornerIsContinuous() {
        double squareA=rim(.5,.5001,0,MaterialLighting.Profile.RAISED);
        double squareB=rim(.5001,.5,0,MaterialLighting.Profile.RAISED);
        assertTrue(Math.abs(squareA-squareB)>1);
        assertEquals(rim(4,4.0001,10,MaterialLighting.Profile.RAISED),
                rim(4.0001,4,10,MaterialLighting.Profile.RAISED),.01);
    }

    @Test void domeHighlightsUpperLeftAndPressedFaceFlattensWithoutSpecular() {
        assertTrue(rim(15,15,40,MaterialLighting.Profile.DOME)>rim(65,65,40,MaterialLighting.Profile.DOME));
        assertEquals(0,rim(40,40,40,MaterialLighting.Profile.DOME));
        assertTrue(rim(40,2,10,MaterialLighting.Profile.PRESSED)<rim(40,2,10,MaterialLighting.Profile.RAISED));
        for(var profile:MaterialLighting.Profile.values()) {
            int pixel=MaterialLighting.face(0x813987ae,0,0,80,80,0,3,5,-3,profile);
            assertEquals(129,pixel>>>24);
            assertTrue(Double.isFinite(rim(0,0,0,profile)));
        }
    }

    @Test void moduleElevationExceedsEightGreyscalePointsInBothOwnerThemes() {
        var live=ThemeDefinition.defaults();live.background=-14674651; // owner's #201525, read-only run/config
        int liveBase=ThemeResolver.resolve(live,true).color(ThemeToken.MATERIAL_RAISED);
        int factoryBase=ThemeResolver.factoryPalette().color(ThemeToken.MATERIAL_RAISED);
        for(int base:new int[]{liveBase,factoryBase}) {
            for(int radius:new int[]{0,10}) {
                double on=meanGrey(base,11,3,radius,MaterialLighting.Profile.RAISED);
                double off=meanGrey(base,-5,-5,radius,MaterialLighting.Profile.FLAT);
                double offHover=meanGrey(base,-3,-3,radius,MaterialLighting.Profile.FLAT);
                double onHover=meanGrey(base,13,5,radius,MaterialLighting.Profile.RAISED);
                assertTrue(on-off>=8,"rest ON/OFF");
                assertTrue(on-offHover>=8,"rest ON vs hovered OFF");
                assertTrue(onHover-offHover>=8,"hover ON/OFF");
                assertTrue(offHover-off>0 && offHover-off<on-off,"hover stays weaker than elevation");
                System.out.printf("Depth greyscale base=%08x radius=%d rest=%.3f rest-v-hover=%.3f hover=%.3f%n",
                        base,radius,on-off,on-offHover,onHover-offHover);
            }
        }
    }

    private static double meanGrey(int base,float top,float bottom,int radius,MaterialLighting.Profile profile) {
        double sum=0;int count=0;
        for(int y=0;y<240;y++) for(int x=0;x<240;x++) {
            if(MaterialLighting.distance(x+.5,y+.5,240,240,radius*3)>0) continue;
            int c=MaterialLighting.face(base,x,y,240,240,radius*3,3,top,bottom,profile);
            sum+=.299*(c>>>16&255)+.587*(c>>>8&255)+.114*(c&255);count++;
        }
        return sum/count;
    }

    @Test void measureEightyPixelTileBakeAtScaleThree() {
        int[] pixels=new int[240*240];long[] samples=new long[9];
        for(int run=-5;run<samples.length;run++) {
            long start=System.nanoTime();
            for(int y=0;y<240;y++) for(int x=0;x<240;x++)
                pixels[y*240+x]=MaterialLighting.distance(x+.5,y+.5,240,240,30)<=0
                        ? MaterialLighting.face(0xff1d1f27,x,y,240,240,30,3,11,3,MaterialLighting.Profile.RAISED) : 0;
            if(run>=0) samples[run]=System.nanoTime()-start;
        }
        java.util.Arrays.sort(samples);
        assertNotEquals(0,pixels[120*240+120]);
        System.out.printf("Depth pure bake 80x80 scale=3 median=%.3f ms (9 samples, 5 warmups)%n",samples[4]/1e6);
    }

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
