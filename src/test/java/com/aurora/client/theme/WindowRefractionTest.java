package com.aurora.client.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Owner, 2026-11-17: below 100% opacity the window edge bends light instead of reading as an opaque ring. */
class WindowRefractionTest {

    @Test void refractionFadesOutAsTheWindowBecomesOpaque() {
        assertEquals(1, WindowBevel.refractionWeight(.18), 1e-9);
        assertEquals(1, WindowBevel.refractionWeight(.35), 1e-9);
        assertEquals(0, WindowBevel.refractionWeight(1), 1e-9);
        double previous = 1;
        for (double o = .35; o <= 1; o += .05) {
            double w = WindowBevel.refractionWeight(o);
            assertTrue(w <= previous + 1e-12, "monotonic at " + o);
            previous = w;
        }
    }

    @Test void bendIsStrongestAtTheOuterEdgeAndZeroOnThePlateau() {
        assertEquals(3, WindowBevel.bendAt(0, 6, 3), 1e-9);
        assertEquals(0, WindowBevel.bendAt(6, 6, 3), 1e-9);
        assertEquals(0, WindowBevel.bendAt(9, 6, 3), 1e-9);
        assertTrue(WindowBevel.bendAt(1, 6, 3) > WindowBevel.bendAt(3, 6, 3));
        assertEquals(0, WindowBevel.bendAt(0, 0, 3), 1e-9);
    }

    @Test void levelsScaleTheBendAndOffBendsNothing() {
        assertEquals(0, WindowBevel.Level.OFF.bend(), 1e-9);
        assertTrue(WindowBevel.Level.SUBTLE.bend() < WindowBevel.Level.STANDARD.bend());
        assertTrue(WindowBevel.Level.STANDARD.bend() < WindowBevel.Level.DEEP.bend());
    }

    @Test void theRefractionBandMatchesTheDrawnFrameBand() {
        WindowBevel.rememberBand(41, 22, 336, 288, 6.5f);
        assertEquals(6.5f, WindowBevel.rememberedBand(41, 22, 336, 288), 1e-6);
        assertEquals(6.5f, WindowBevel.rememberedBand(41.2f, 22, 336, 288), 1e-6);
        assertEquals(-1, WindowBevel.rememberedBand(10, 10, 100, 80), 1e-6);
        WindowBevel.rememberBand(41, 22, 336, 288, 4f);
        assertEquals(4f, WindowBevel.rememberedBand(41, 22, 336, 288), 1e-6);
    }

    @Test void theRimIsNoMoreOpaqueThanAClearWindow() {
        int glass = 0x2e16171d; // ~18% graphite
        int rimAlpha = WindowBevel.finish(glass, 0xff3a3d4a, 24, WindowBevel.Level.STANDARD).color() >>> 24;
        // density + 10% of the clear remainder: about 26%, far below the former 92% ring.
        assertTrue(rimAlpha < 80, "rim alpha " + rimAlpha);
        int metal = 0xd9777777; // 85% grey
        int metalAlpha = WindowBevel.finish(metal, 0xff3a3d4a, 2, WindowBevel.Level.STANDARD).color() >>> 24;
        assertTrue(metalAlpha >= 0xd9, "an opaque window keeps a solid bevel: " + metalAlpha);
    }
}
