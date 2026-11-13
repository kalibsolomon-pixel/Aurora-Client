package com.aurora.client.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The time-driven Hit Color modes run on a fixed palette, so the overlay re-uploads per step, not per millisecond. */
class HitColorModesTest {

    @Test
    void rainbowColorIsConstantWithinAStep() {
        // Step 1 starts at the first millisecond whose phase reaches 1/RAINBOW_STEPS.
        long stepMs = Math.ceilDiv(HitColorModes.RAINBOW_PERIOD_MS, HitColorModes.RAINBOW_STEPS);
        assertEquals(HitColorModes.rainbow(0, 0xFF000000), HitColorModes.rainbow(stepMs - 1, 0xFF000000));
        assertEquals(1, HitColorModes.rainbowStep(stepMs));
    }

    @Test
    void oneCycleVisitsEveryPaletteStepAndNoMore() {
        Set<Integer> rainbow = new HashSet<>();
        Set<Integer> pulse = new HashSet<>();
        for (long t = 0; t < HitColorModes.RAINBOW_PERIOD_MS; t++) {
            rainbow.add(HitColorModes.rainbowStep(t));
            pulse.add(HitColorModes.pulseStep(t, 1.0));
        }
        assertEquals(HitColorModes.RAINBOW_STEPS, rainbow.size());
        assertEquals(HitColorModes.PULSE_STEPS, pulse.size());
    }

    @Test
    void stepsAreWellDefinedBeforeTheEpoch() {
        int step = HitColorModes.rainbowStep(-1);
        assertTrue(step >= 0 && step < HitColorModes.RAINBOW_STEPS);
        int p = HitColorModes.pulseStep(-1, 2.0);
        assertTrue(p >= 0 && p < HitColorModes.PULSE_STEPS);
    }

    @Test
    void pulseKeepsItsAlphaRangeAndColor() {
        int color = 0xC0FF2040;
        int min = 255, max = 0;
        for (long t = 0; t < 1000; t++) {
            int c = HitColorModes.pulse(color, t, 1.0);
            assertEquals(color & 0xFFFFFF, c & 0xFFFFFF);
            int a = c >>> 24;
            min = Math.min(min, a);
            max = Math.max(max, a);
        }
        assertEquals(Math.round(0xC0 * 0.25f), min);
        assertEquals(0xC0, max);
    }
}
