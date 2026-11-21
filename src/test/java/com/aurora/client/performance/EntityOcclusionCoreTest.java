package com.aurora.client.performance;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure core contracts for {@link OcclusionSweep}, {@link SectionStability}, and {@link IdentityTable}. */
class EntityOcclusionCoreTest {

    // ---- OcclusionSweep ----

    @Test void solidWallHidesBoxInAllSixDirectionsAndNegativeCoords() {
        OcclusionSweep sweep = new OcclusionSweep(16, 36);
        // +Z: eye at (0.5, 0.5, 0.5), wall at z=5, box at z=[10, 11]
        assertTrue(sweep.hidden((x, y, z) -> z == 5, 0.5, 0.5, 0.5, 0.25, 0, 0, 10, 1, 1, 11));
        // -Z: eye at (0.5, 0.5, 0.5), wall at z=-6, box at z=[-11, -10]
        assertTrue(sweep.hidden((x, y, z) -> z == -6, 0.5, 0.5, 0.5, 0.25, 0, 0, -11, 1, 1, -10));
        // +X: eye at (-10.5, 2.5, -3.5), wall at x=-5, box at x=[-1, 0]
        assertTrue(sweep.hidden((x, y, z) -> x == -5, -10.5, 2.5, -3.5, 0.25, -1, 2, -4, 0, 3, -3));
        // -X: eye at (10.5, 2.5, -3.5), wall at x=4, box at x=[-1, 0]
        assertTrue(sweep.hidden((x, y, z) -> x == 4, 10.5, 2.5, -3.5, 0.25, -1, 2, -4, 0, 3, -3));
        // +Y (ceiling): eye at (0.5, 0.5, 0.5), ceiling at y=4, box at y=[8, 9]
        assertTrue(sweep.hidden((x, y, z) -> y == 4, 0.5, 0.5, 0.5, 0.25, 0, 8, 0, 1, 9, 1));
        // -Y (floor): eye at (0.5, 10.5, 0.5), floor at y=5, box at y=[0, 1]
        assertTrue(sweep.hidden((x, y, z) -> y == 5, 0.5, 10.5, 0.5, 0.25, 0, 0, 0, 1, 1, 1));
    }

    @Test void singleHoleInSeparatingLayerPreventsOcclusion() {
        OcclusionSweep sweep = new OcclusionSweep(16, 36);
        // Wall at z=5 covering everything except (0, 0, 5), which sits on the sightline.
        assertFalse(sweep.hidden((x, y, z) -> z == 5 && !(x == 0 && y == 0),
                0.5, 0.5, 0.5, 0.25, 0, 0, 10, 1, 1, 11));
        // Hole on the edge of the projected shadow still rejects occlusion.
        assertFalse(sweep.hidden((x, y, z) -> z == 5 && !(x == 1 && y == 1),
                0.5, 0.5, 0.5, 0.25, 0, 0, 10, 2, 2, 11));
    }

    @Test void wallEdgePeekingBoxIsNeverCulled() {
        OcclusionSweep sweep = new OcclusionSweep(16, 36);
        // Wall at z=5 covers x in [0..5], y in [0..5]. Box extends to x=12 so rays from x=2.5 cross x=6 at z=5.
        assertFalse(sweep.hidden((x, y, z) -> z == 5 && x >= 0 && x <= 5 && y >= 0 && y <= 5,
                2.5, 2.5, 0.5, 0.25, 4.0, 2.0, 10.0, 12.0, 3.0, 11.0));
        // Same wall, box strictly behind x=[2..3] is hidden.
        assertTrue(sweep.hidden((x, y, z) -> z == 5 && x >= 0 && x <= 5 && y >= 0 && y <= 5,
                2.5, 2.5, 0.5, 0.25, 2.0, 2.0, 10.0, 3.0, 3.0, 11.0));
    }

    @Test void layerTooCloseToEyeOrOverlappingBoxIsRejected() {
        OcclusionSweep sweep = new OcclusionSweep(16, 36);
        // Eye cube reaches z = 0.5 + 0.25 = 0.75; NEAR = 0.5 requires plane >= ceil(1.25) = 2.
        // Wall at z=1 is within NEAR of eye and must not occlude.
        assertFalse(sweep.hidden((x, y, z) -> z == 1, 0.5, 0.5, 0.5, 0.25, 0, 0, 5, 1, 1, 6));
        // Wall at z=2 IS >= eyeFar + NEAR and strictly before boxNear=5.
        assertTrue(sweep.hidden((x, y, z) -> z == 2, 0.5, 0.5, 0.5, 0.25, 0, 0, 5, 1, 1, 6));
        // Box overlapping the wall's near face (boxNear = 5.0, wall at z=5) is NOT hidden because plane <= boxNear - EDGE.
        assertFalse(sweep.hidden((x, y, z) -> z == 5, 0.5, 0.5, 0.5, 0.25, 0, 0, 5.0, 1, 1, 6.0));
        // Box strictly behind near face (boxNear = 5.01, wall at z=5 whose front face is z=5) IS hidden.
        assertTrue(sweep.hidden((x, y, z) -> z == 5, 0.5, 0.5, 0.5, 0.25, 0, 0, 5.01, 1, 1, 6.01));
    }

    @Test void eyeRadiusConservativelyExpandsProjectedFootprint() {
        OcclusionSweep sweep = new OcclusionSweep(16, 36);
        // With eyeRadius = 0, a narrow 1x1 pillar at (0, 0, 2) hides a tiny box at (0.5, 0.5, 4).
        OcclusionSweep.Cells pillar = (x, y, z) -> z == 2 && x == 0 && y == 0;
        assertTrue(sweep.hidden(pillar, 0.5, 0.5, 0.5, 0.0, 0.4, 0.4, 4.0, 0.6, 0.6, 4.2));
        // With eyeRadius = 0.8, an eye point at (1.3, 0.5, 1.3) sees past x = 1.0 of the pillar (crosses z=2 at x=1.13).
        assertFalse(sweep.hidden(pillar, 0.5, 0.5, 0.5, 0.8, 0.4, 0.4, 4.0, 0.6, 0.6, 4.2));
    }

    @Test void negativeMirrorLayerIndexMatchesRealWallFace() {
        OcclusionSweep sweep = new OcclusionSweep(16, 36);
        // Eye at z = 10.5, box at z in [1, 2]. Looking in -Z direction.
        // A wall at z = 5 occupies [5, 6]; its +Z face at z=6 lies between box (<=2) and eye (>=10.25).
        assertTrue(sweep.hidden((x, y, z) -> z == 5, 0.5, 0.5, 10.5, 0.25, 0, 0, 1, 1, 1, 2));
        // Wall at z = 10 contains the eye -> must NOT occlude.
        assertFalse(sweep.hidden((x, y, z) -> z == 10, 0.5, 0.5, 10.5, 0.25, 0, 0, 1, 1, 1, 2));
        // Wall at z = 9 occupies [9, 10]; its near face to eye is z=10, distance 0.25 from eyeFar=10.25 < NEAR(0.5) -> must NOT occlude.
        assertFalse(sweep.hidden((x, y, z) -> z == 9, 0.5, 0.5, 10.5, 0.25, 0, 0, 1, 1, 1, 2));
        // Wall at z = 8 occupies [8, 9]; near face z=9, distance 1.25 >= NEAR -> occludes!
        assertTrue(sweep.hidden((x, y, z) -> z == 8, 0.5, 0.5, 10.5, 0.25, 0, 0, 1, 1, 1, 2));
    }

    @Test void invalidInputsAndBoundsNeverCullAndRespectProbeLimit() {
        OcclusionSweep sweep = new OcclusionSweep(4, 9);
        OcclusionSweep.Cells allSolid = (x, y, z) -> true;
        // NaN, Infinity, inverted box, negative eye radius, huge coordinates beyond int safety
        assertFalse(sweep.hidden(allSolid, Double.NaN, 0, 0, 0.25, 0, 0, 10, 1, 1, 11));
        assertFalse(sweep.hidden(allSolid, 0, Double.POSITIVE_INFINITY, 0, 0.25, 0, 0, 10, 1, 1, 11));
        assertFalse(sweep.hidden(allSolid, 0, 0, 0, -0.1, 0, 0, 10, 1, 1, 11));
        assertFalse(sweep.hidden(allSolid, 0, 0, 0, 0.25, 2, 0, 10, 1, 1, 11));
        assertFalse(sweep.hidden(allSolid, 1e12, 0, 0, 0.25, 1e12, 0, 10, 1e12 + 1, 1, 11));
        assertEquals(0, sweep.probes());

        // Box too wide for maxCells=9 (10x10 projection) -> skipped, answers false
        assertFalse(sweep.hidden(allSolid, 5, 5, 0, 0.25, 0, 0, 4, 10, 10, 5));

        // Probe counter tracks work and resets cleanly
        sweep.resetProbes();
        assertTrue(sweep.hidden(allSolid, 0.5, 0.5, 0.5, 0.25, 0, 0, 10, 1, 1, 11));
        assertTrue(sweep.probes() > 0 && sweep.probes() <= 9);
        sweep.resetProbes();
        assertEquals(0, sweep.probes());
    }

    // ---- SectionStability ----

    @Test void sectionAndColumnStabilityExpiryAndReset() {
        SectionStability st = new SectionStability(16);
        assertTrue(st.settled(0, 0, 0, 1000, 500));

        st.markSection(1, 2, 3, 1000, 500);
        assertFalse(st.settled(1, 2, 3, 1000, 500));
        assertFalse(st.settled(1, 2, 3, 1499, 500));
        assertTrue(st.settled(1, 2, 3, 1500, 500));
        assertTrue(st.settled(1, 2, 4, 1000, 500));

        // Column mark affects every section Y in that chunk column
        st.markColumn(-2, 5, 2000, 500);
        assertFalse(st.settled(-2, -4, 5, 2400, 500));
        assertFalse(st.settled(-2, 12, 5, 2400, 500));
        assertTrue(st.settled(-2, 12, 5, 2500, 500));
        assertTrue(st.settled(-1, 12, 5, 2400, 500));

        // Reset unsettles every section until now + window
        st.reset(3000, 1000);
        assertFalse(st.settled(99, 99, 99, 3999, 1000));
        assertTrue(st.settled(99, 99, 99, 4000, 1000));
    }

    @Test void sectionStabilityCompactsExpiredAndFailsClosedOnLiveOverflow() {
        SectionStability st = new SectionStability(16); // limit = 12
        for (int i = 0; i < 12; i++) st.markSection(i, 0, 0, 1000, 500);
        assertEquals(12, st.size());
        // After expiry, adding a 13th compacts out all 12 expired entries instead of resetting everything.
        st.markSection(100, 0, 0, 2000, 500);
        assertEquals(1, st.size());
        assertFalse(st.settled(100, 0, 0, 2200, 500));
        assertTrue(st.settled(200, 0, 0, 2200, 500));

        // Filling with 13 live entries within one window must unsettle EVERY section (never forget a change).
        for (int i = 0; i < 13; i++) st.markSection(i, 1, 0, 3000, 500);
        assertFalse(st.settled(999, 9, 999, 3499, 500));
        assertTrue(st.settled(999, 9, 999, 3500, 500));
    }

    // ---- IdentityTable ----

    @Test void identityTableUsesReferenceIdentityBackwardShiftAndGrowth() {
        IdentityTable<String> table = new IdentityTable<>(8);
        String a = new String("same");
        String b = new String("same");
        assertTrue(table.add(a));
        assertFalse(table.add(a));
        assertTrue(table.add(b));
        assertEquals(2, table.size());
        assertTrue(table.contains(a));
        assertTrue(table.contains(b));

        assertTrue(table.remove(a));
        assertFalse(table.contains(a));
        assertTrue(table.contains(b));
        assertEquals(1, table.size());

        // Stress growth + deletions + index traversal
        table.clear();
        assertEquals(0, table.size());
        Object[] items = new Object[100];
        IdentityTable<Object> big = new IdentityTable<>(8);
        for (int i = 0; i < items.length; i++) {
            items[i] = new Object();
            assertTrue(big.add(items[i]));
        }
        assertEquals(100, big.size());
        for (int i = 0; i < items.length; i += 2) assertTrue(big.remove(items[i]));
        assertEquals(50, big.size());
        Set<Object> seen = new HashSet<>();
        for (int i = 0; i < big.capacity(); i++) {
            Object v = big.at(i);
            if (v != null) seen.add(v);
        }
        assertEquals(50, seen.size());
        for (int i = 0; i < items.length; i++) {
            assertEquals(i % 2 == 1, big.contains(items[i]));
            assertEquals(i % 2 == 1, seen.contains(items[i]));
        }
        assertThrows(IllegalArgumentException.class, () -> new IdentityTable<>(6));
    }
}
