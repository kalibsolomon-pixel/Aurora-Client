package com.aurora.client.alerts;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * Item-level low-durability latch (pure seam: no Minecraft types).
 *
 * <p>The old armor alert tracked a boolean per {@code EquipmentSlot}, so
 * simply unequipping and re-equipping a low piece — or moving it around in
 * the inventory — re-fired the warning, while two identical items at the
 * same damage shared one slot's flag. This latch keys on an item
 * <em>fingerprint</em> instead:
 *
 * <pre>(registry id, max damage, damage)</pre>
 *
 * <p>Consequences of that key:
 * <ul>
 *   <li><b>Moving an item never re-fires.</b> Unequip → inventory →
 *       re-equip yields the same fingerprint; {@link #observe} sees a known
 *       key and stays silent. The observation keeps the entry's
 *       last-seen tick fresh so a prune does not drop it either.</li>
 *   <li><b>Repair re-arms naturally.</b> Repairing an item changes its
 *       damage, which is a different fingerprint. When that item drops
 *       below the threshold again it is an unknown key and latches fresh —
 *       no explicit "unlatch" bookkeeping. Replacement with a new item is
 *       the same thing (the old entry just stops being seen and prunes).</li>
 *   <li><b>Identical items are not suppressed.</b> Two copies of the same
 *       tool at the same damage share one fingerprint and one row (the
 *       caller counts matching stacks and shows "×2"); different damage
 *       levels are different keys and each latch.</li>
 * </ul>
 *
 * <p><b>Bounding.</b> Latched items the scanner no longer sees (dropped,
 * stored in a chest) are pruned once unseen for
 * {@link #PRUNE_AFTER_TICKS}; the map is additionally capped at
 * {@link #MAX_TRACKED} entries, evicting the least-recently-seen. The
 * caller resets the latch on world join / respawn (same contract as the
 * other per-world caches) and when its feature is switched off.
 */
public final class DurabilityLatch {

    /** Identity of a damageable item state: registry id + max damage + current damage. */
    public record Fingerprint(String itemId, int maxDamage, int damage) {
        /** Remaining durability, rounded to whole percent (0..100). */
        public int remainingPct() {
            if (maxDamage <= 0) return 100;
            return Math.round(100f * (maxDamage - damage) / maxDamage);
        }

        @Override public String toString() {
            return itemId + "/" + maxDamage + "/" + damage;
        }
    }

    /** How long an unseen entry may live before pruning (10 s at 20 tps). */
    public static final long PRUNE_AFTER_TICKS = 200L;
    /** Hard cap on tracked fingerprints; eviction drops the least-recently-seen. */
    public static final int MAX_TRACKED = 64;

    /** One latched item state: its key and the last tick it was observed. */
    public static final class Entry {
        public final Fingerprint fingerprint;
        long lastSeenTick;

        Entry(Fingerprint fingerprint, long tick) {
            this.fingerprint = fingerprint;
            this.lastSeenTick = tick;
        }

        public long lastSeenTick() { return lastSeenTick; }
    }

    private final LinkedHashMap<Fingerprint, Entry> entries = new LinkedHashMap<>();

    /**
     * Observe an item from a slot whose alert source is enabled. Returns
     * {@code true} exactly once per fingerprint — the caller should fire
     * (sound + row) on that call and never again while the key lives.
     */
    public boolean observe(Fingerprint fingerprint, long tick) {
        Entry existing = entries.get(fingerprint);
        if (existing != null) {
            existing.lastSeenTick = tick;
            return false;
        }
        entries.put(fingerprint, new Entry(fingerprint, tick));
        evictOverflow();
        return true;
    }

    /**
     * Observe an item from a slot whose alert source is off (untracked
     * equipment) or from the general inventory: keeps an already-latched
     * fingerprint alive across moves but never creates or fires one.
     */
    public void refresh(Fingerprint fingerprint, long tick) {
        Entry existing = entries.get(fingerprint);
        if (existing != null) existing.lastSeenTick = tick;
    }

    public boolean isLatched(Fingerprint fingerprint) {
        return entries.containsKey(fingerprint);
    }

    /** All live entries (latched or awaiting prune). */
    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    /** Drop entries unseen for {@code maxAgeTicks}; call once per tick. */
    public void prune(long tick, long maxAgeTicks) {
        entries.values().removeIf(e -> tick - e.lastSeenTick > maxAgeTicks);
    }

    /** Forget everything (world join / respawn / feature off / feature reset). */
    public void reset() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    /** Cap the map, evicting the least-recently-seen entries first. */
    private void evictOverflow() {
        while (entries.size() > MAX_TRACKED) {
            Fingerprint oldest = null;
            long oldestTick = Long.MAX_VALUE;
            for (var e : entries.entrySet()) {
                if (e.getValue().lastSeenTick < oldestTick) {
                    oldestTick = e.getValue().lastSeenTick;
                    oldest = e.getKey();
                }
            }
            if (oldest == null) break;
            entries.remove(oldest);
        }
    }
}
