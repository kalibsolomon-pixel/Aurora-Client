package com.aurora.client.ui.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CubicBezierTest {
    @Test void endpointsAndMonotonicity() {
        for(var curve:new CubicBezier[]{CubicBezier.EASE,CubicBezier.EASE_OUT,CubicBezier.EMPHASIZED}) {
            assertEquals(0,curve.at(0)); assertEquals(1,curve.at(1));
            double previous=0;
            for(int i=1;i<=1000;i++){double next=curve.at(i/1000d);assertTrue(next>=previous);previous=next;}
        }
    }
    @Test void cssReferenceSamplesRequireInvertingX() {
        assertEquals(.802403, CubicBezier.EASE.at(.5), 1e-6);
        assertEquals(.684643,CubicBezier.EASE_OUT.at(.5),1e-6);
        assertEquals(.946079,CubicBezier.EMPHASIZED.at(.5),1e-6);
    }
    @Test void interruptedReverseAndRetargetNeverJump() {
        var t=new TimedTransition(120,CubicBezier.EASE);
        assertEquals(0,t.update(0,0)); assertEquals(0,t.update(1,0));
        float mid=t.update(1,40_000_000);
        assertTrue(mid>0 && mid<1); assertEquals(mid,t.update(0,40_000_000));
        assertTrue(t.update(0,60_000_000)<mid); assertEquals(0,t.update(0,200_000_000));
        t.update(1,200_000_000); float current=t.update(1,250_000_000);
        assertEquals(current,t.update(.7,250_000_000)); assertEquals(.7,t.update(.7,400_000_000),1e-6);
    }
}
