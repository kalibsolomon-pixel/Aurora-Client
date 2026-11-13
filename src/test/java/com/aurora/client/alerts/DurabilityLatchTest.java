package com.aurora.client.alerts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavior of the item-fingerprint durability latch: firing exactly once
 * per item state, silence across moves, re-arm on repair/replacement,
 * bounding and reset.
 */
class DurabilityLatchTest {

    private static DurabilityLatch.Fingerprint fp(String id, int max, int dmg) {
        return new DurabilityLatch.Fingerprint(id, max, dmg);
    }

    @Test
    void firesExactlyOncePerFingerprint() {
        DurabilityLatch latch = new DurabilityLatch();
        var f = fp("minecraft:diamond_helmet", 363, 300);
        assertTrue(latch.observe(f, 10));
        assertFalse(latch.observe(f, 11));
        assertFalse(latch.observe(f, 12));
        assertTrue(latch.isLatched(f));
    }

    @Test
    void movingBetweenSlotsNeverRefires() {
        DurabilityLatch latch = new DurabilityLatch();
        // Unequip (armor slot, observed) then re-equip later: same item
        // state, so the same fingerprint — no second fire even though the
        // slot went empty in between (and refresh-only in the gap).
        var f = fp("minecraft:diamond_chestplate", 528, 480);
        assertTrue(latch.observe(f, 10));
        latch.refresh(f, 20);   // in inventory, hands off
        latch.refresh(f, 40);
        assertFalse(latch.observe(f, 50));
    }

    @Test
    void repairChangesFingerprintAndRearms() {
        DurabilityLatch latch = new DurabilityLatch();
        var broken = fp("minecraft:iron_pickaxe", 250, 240);
        var repaired = fp("minecraft:iron_pickaxe", 250, 10);
        assertNotEquals(broken, repaired);
        assertTrue(latch.observe(broken, 10));
        // The repaired state is an unknown key: it latches only when it
        // itself drops below the threshold again.
        latch.refresh(repaired, 20);
        assertFalse(latch.isLatched(repaired));
        assertTrue(latch.observe(repaired, 30));
    }

    @Test
    void differentDamageLevelsLatchIndependently() {
        DurabilityLatch latch = new DurabilityLatch();
        var a = fp("minecraft:diamond_sword", 1561, 1400);
        var b = fp("minecraft:diamond_sword", 1561, 1500);
        assertTrue(latch.observe(a, 10));
        assertTrue(latch.observe(b, 10));
        assertEquals(2, latch.size());
    }

    @Test
    void pruneDropsUnseenEntriesOnlyAfterMaxAge() {
        DurabilityLatch latch = new DurabilityLatch();
        var f = fp("minecraft:bow", 384, 350);
        assertTrue(latch.observe(f, 0));
        latch.prune(100, DurabilityLatch.PRUNE_AFTER_TICKS);
        assertTrue(latch.isLatched(f));
        latch.prune(201, DurabilityLatch.PRUNE_AFTER_TICKS);
        assertFalse(latch.isLatched(f));
        // Re-observation after the item comes back fires again.
        assertTrue(latch.observe(f, 210));
    }

    @Test
    void refreshKeepsALatchedItemAlivePastPruneWindow() {
        DurabilityLatch latch = new DurabilityLatch();
        var f = fp("minecraft:elytra", 432, 420);
        assertTrue(latch.observe(f, 0));
        for (long t = 20; t <= 400; t += 20) latch.refresh(f, t);
        latch.prune(400, DurabilityLatch.PRUNE_AFTER_TICKS);
        assertTrue(latch.isLatched(f));
    }

    @Test
    void capEvictsLeastRecentlySeen() {
        DurabilityLatch latch = new DurabilityLatch();
        var first = fp("minecraft:iron_helmet", 165, 100);
        assertTrue(latch.observe(first, 0));
        for (int i = 0; i < DurabilityLatch.MAX_TRACKED + 5; i++) {
            latch.observe(fp("minecraft:stone_sword", 131, 90 - i), 10 + i);
        }
        assertFalse(latch.isLatched(first));
        assertEquals(DurabilityLatch.MAX_TRACKED, latch.size());
    }

    @Test
    void resetForgetsEverything() {
        DurabilityLatch latch = new DurabilityLatch();
        var f = fp("minecraft:diamond_helmet", 363, 350);
        latch.observe(f, 5);
        latch.reset();
        assertEquals(0, latch.size());
        assertFalse(latch.isLatched(f));
        assertTrue(latch.observe(f, 6));
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
