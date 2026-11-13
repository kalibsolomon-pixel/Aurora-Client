package com.aurora.client.feature.impl;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MovementCadenceTest {
    @Test void waitsForSixGapsThenEasesEveryTickWithoutAnotherMovement() {
        var cadence = new MovementCadence();
        UUID id = UUID.randomUUID();
        for (int i = 0; i < 6; i++) {
            cadence.observe(id, i * 4, i * .1, 0, 0);
            cadence.finishTick();
            assertEquals(1, cadence.factor());
        }
        cadence.observe(id, 24, .6, 0, 0);
        cadence.finishTick();
        assertEquals(1.3f, cadence.factor());
        cadence.finishTick();
        assertEquals(1.57f, cadence.factor(), .000001f);
        cadence.clear();
        assertEquals(1, cadence.factor());
    }

    @Test void rejectsTeleportsRepeatedTicksAndLongGapsAndCapsBundling() {
        var cadence = new MovementCadence();
        UUID id = UUID.randomUUID();
        cadence.observe(id, 0, 0, 0, 0);
        cadence.observe(id, 4, 100, 0, 0);
        cadence.observe(id, 4, 100.1, 0, 0);
        cadence.observe(id, 100, 100.2, 0, 0);
        cadence.finishTick();
        assertEquals(1, cadence.factor());
        for (int i = 1; i <= 40; i++) {
            cadence.observe(id, 100 + i * 10, 100.2 + i * .1, 0, 0);
            cadence.finishTick();
        }
        assertTrue(cadence.factor() > 5.8f && cadence.factor() <= 6);
    }

    @Test void rollingWindowRecoversFromBundlingAndClearDropsWorldHistory() {
        var cadence = new MovementCadence();
        UUID id = UUID.randomUUID();
        for (int i = 0; i < 40; i++) {
            cadence.observe(id, i * 4, i * .1, 0, 0);
            cadence.finishTick();
        }
        assertTrue(cadence.factor() > 3.8f);
        for (int i = 0; i < 100; i++) {
            cadence.observe(id, 160 + i, 4 + i * .1, 0, 0);
            cadence.finishTick();
        }
        assertEquals(1, cadence.factor(), .002f);
        cadence.clear();
        cadence.observe(id, 1000, 50, 0, 0);
        cadence.finishTick();
        assertEquals(1, cadence.factor());
    }
}
