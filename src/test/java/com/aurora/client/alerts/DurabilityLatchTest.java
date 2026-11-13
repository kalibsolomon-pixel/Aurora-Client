package com.aurora.client.alerts;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavior of the item-following durability latch: one warning per item,
 * silence through further wear and moves, re-arm only once the item (or a
 * replacement) is held above the threshold, bounding and reset.
 */
class DurabilityLatchTest {

    private static final String HELMET = "minecraft:diamond_helmet";
    private static final DurabilityLatch.Kind HELMET_KIND = new DurabilityLatch.Kind(HELMET, 363);

    private static DurabilityLatch.Fingerprint fp(String id, int max, int dmg) {
        return new DurabilityLatch.Fingerprint(id, max, dmg);
    }

    private static DurabilityLatch.Observation worn(int damage) {
        return new DurabilityLatch.Observation(fp(HELMET, 363, damage), true, "Helmet");
    }

    private static DurabilityLatch.Observation stored(int damage) {
        return new DurabilityLatch.Observation(fp(HELMET, 363, damage), false, null);
    }

    private static int fires(DurabilityLatch latch, long tick, DurabilityLatch.Observation... low) {
        return latch.update(List.of(low), Set.of(), tick).size();
    }

    @Test
    void firesOnceThroughFurtherWear() {
        DurabilityLatch latch = new DurabilityLatch();
        assertEquals(1, fires(latch, 1, worn(340)));
        for (int t = 2, damage = 341; damage < 363; t++, damage++) {
            assertEquals(0, fires(latch, t, worn(damage)), "wear to damage " + damage);
        }
        assertEquals(1, latch.size());
    }

    @Test
    void unequipAndReequipNeverRefires() {
        DurabilityLatch latch = new DurabilityLatch();
        assertEquals(1, fires(latch, 1, worn(340)));
        assertEquals(0, fires(latch, 2, stored(340)));
        assertEquals(0, fires(latch, 3));               // briefly on the cursor
        assertEquals(0, fires(latch, 4, worn(340)));
        assertEquals(0, fires(latch, 5, worn(342)));
    }

    @Test
    void partialMendingBelowTheThresholdDoesNotRearm() {
        DurabilityLatch latch = new DurabilityLatch();
        assertEquals(1, fires(latch, 1, worn(345)));
        assertEquals(0, fires(latch, 2, worn(338)));
        assertEquals(0, fires(latch, 3, worn(343)));
    }

    @Test
    void mendingAboveTheThresholdRearms() {
        DurabilityLatch latch = new DurabilityLatch();
        assertEquals(1, fires(latch, 1, worn(345)));
        latch.update(List.of(), Set.of(HELMET_KIND), 2);  // mended: held, but no longer low
        assertEquals(0, latch.size());
        assertEquals(1, fires(latch, 3, worn(345)));
    }

    @Test
    void replacementAboveTheThresholdRearms() {
        DurabilityLatch latch = new DurabilityLatch();
        assertEquals(1, fires(latch, 1, worn(345)));
        // The low helmet goes into a chest and a fresh one is worn.
        latch.update(List.of(), Set.of(HELMET_KIND), 2);
        assertEquals(1, fires(latch, 300, worn(340)));
    }

    @Test
    void healthyCopyDoesNotReleaseALowItemStillHeld() {
        DurabilityLatch latch = new DurabilityLatch();
        assertEquals(1, fires(latch, 1, worn(345)));
        latch.update(List.of(stored(345)), Set.of(HELMET_KIND), 2);
        assertEquals(0, fires(latch, 3, worn(345)));
    }

    @Test
    void inventoryAloneNeverArms() {
        DurabilityLatch latch = new DurabilityLatch();
        assertEquals(0, fires(latch, 1, stored(350)));
        assertEquals(0, latch.size());
        assertEquals(1, fires(latch, 2, worn(350)));
    }

    @Test
    void aSecondLowCopyWarnsSeparately() {
        DurabilityLatch latch = new DurabilityLatch();
        assertEquals(1, fires(latch, 1, worn(345)));
        // Swap: the latched helmet goes to the inventory, another low one is worn.
        assertEquals(1, fires(latch, 2, stored(345), worn(320)));
        assertEquals(2, latch.size());
        assertEquals(0, fires(latch, 3, worn(345), stored(321)));
    }

    @Test
    void differentKindsLatchIndependently() {
        DurabilityLatch latch = new DurabilityLatch();
        var sword = new DurabilityLatch.Observation(fp("minecraft:diamond_sword", 1561, 1500), true, "Main Hand");
        assertEquals(2, fires(latch, 1, worn(345), sword));
        latch.update(List.of(sword), Set.of(HELMET_KIND), 2);
        assertEquals(1, latch.size());
    }

    @Test
    void unmatchedEntriesPruneAfterMaxAge() {
        DurabilityLatch latch = new DurabilityLatch();
        assertEquals(1, fires(latch, 0, worn(345)));
        fires(latch, DurabilityLatch.PRUNE_AFTER_TICKS);
        assertEquals(1, latch.size());
        assertFalse(latch.entries().iterator().next().present());
        fires(latch, DurabilityLatch.PRUNE_AFTER_TICKS + 1);
        assertEquals(0, latch.size());
        assertEquals(1, fires(latch, 210, worn(345)));
    }

    @Test
    void capEvictsLeastRecentlySeen() {
        DurabilityLatch latch = new DurabilityLatch();
        assertEquals(1, fires(latch, 0, worn(345)));
        List<DurabilityLatch.Observation> swords = new ArrayList<>();
        for (int i = 0; i < DurabilityLatch.MAX_TRACKED; i++) {
            swords.add(new DurabilityLatch.Observation(fp("minecraft:stone_sword", 131, 100 + i % 31), true, null));
        }
        latch.update(swords, Set.of(), 10);
        assertEquals(DurabilityLatch.MAX_TRACKED, latch.size());
        assertTrue(latch.entries().stream().noneMatch(e -> e.fingerprint().itemId().equals(HELMET)));
    }

    @Test
    void resetForgetsEverything() {
        DurabilityLatch latch = new DurabilityLatch();
        fires(latch, 5, worn(350));
        latch.reset();
        assertEquals(0, latch.size());
        assertEquals(1, fires(latch, 6, worn(350)));
    }

    @Test
    void remainingPctRoundsAndGuardsZeroMax() {
        assertEquals(50, fp("x", 100, 50).remainingPct());
        assertEquals(0, fp("x", 100, 100).remainingPct());
        assertEquals(100, fp("x", 100, 0).remainingPct());
        assertEquals(17, fp("x", 6, 5).remainingPct());
        assertEquals(100, fp("x", 0, 0).remainingPct());
    }
}
