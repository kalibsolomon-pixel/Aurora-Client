package com.aurora.client.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundedScrollTest {
    @Test void wheelSettlesExactlyWithoutAnExponentialTail() {
        var scroll = new SmoothScroll(60).boundedMotion();
        scroll.advance(300,100);
        scroll.wheel(-1,30,300);
        double previous=0;
        for(int ms=110;ms<=190;ms+=10) {
            double position=scroll.advance(300,ms);
            assertTrue(position>=previous && position<=30);
            previous=position;
        }
        assertEquals(30,scroll.current());
        assertEquals(30,scroll.advance(300,1000));
    }
    @Test void reversalAndViewportShrinkCannotOvershoot() {
        var scroll=new SmoothScroll(80).boundedMotion();
        scroll.advance(300,100);scroll.wheel(-3,30,300);
        scroll.advance(300,130);scroll.wheel(1,30,300);
        for(int ms=140;ms<=230;ms+=10) assertTrue(scroll.advance(20,ms)<=20);
        assertEquals(20,scroll.current());
    }
    @Test void thumbDragPinsAllRenderedGeometryImmediately() {
        var scroll=new SmoothScroll(60).boundedMotion();
        scroll.advance(300,100);scroll.wheel(-1,30,300);
        scroll.beginThumbDrag(0);
        scroll.set(150);
        assertEquals(150,scroll.advance(300,110));
        scroll.endThumbDrag();
        assertEquals(150,scroll.advance(300,120));
    }
}
