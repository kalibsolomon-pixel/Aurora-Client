package com.aurora.client.alerts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Item-level low-durability latch (pure seam: no Minecraft types).
 *
 * <p>The old armor alert tracked a boolean per {@code EquipmentSlot}, so
 * unequipping and re-equipping a low piece re-fired the warning. Item stacks
 * carry no identity, so this latch <em>follows</em> each low item from tick
 * to tick instead: every latched entry is matched to the low stack of the
 * same kind (registry id + max damage) whose damage is closest to the
 * entry's last known damage. Wear changes damage by a few points per tick,
 * so the nearest stack is the same item.
 *
 * <ul>
 *   <li><b>Fires once per item.</b> Further wear, moving the item to the
 *       inventory or another slot and re-equipping it all keep the match:
 *       no second warning.</li>
 *   <li><b>Re-arms only above the threshold.</b> When the player holds a
 *       stack of a latched kind above the threshold (mended, or replaced by
 *       a better copy) and the entry found no low stack this tick, the entry
 *       is released. That item, or its replacement, warns again the next
 *       time it drops below the threshold. Partial mending that stays below
 *       the threshold does not re-arm.</li>
 *   <li><b>Only equipped items arm.</b> A low stack that matches no entry
 *       latches only when it sits in a tracked slot; the general inventory
 *       keeps existing entries alive but never fires.</li>
 * </ul>
 *
 * <p><b>Bounding.</b> Entries unmatched for {@link #PRUNE_AFTER_TICKS}
 * (dropped, stored in a chest) are pruned; the map is capped at
 * {@link #MAX_TRACKED} entries, evicting the least-recently-seen. The caller
 * resets the latch on world join / respawn and when its feature is off.
 */
public final class DurabilityLatch {

    /** One damageable stack at or below the threshold: registry id, max damage, damage. */
    public record Fingerprint(String itemId, int maxDamage, int damage) {
        /** Remaining durability, rounded to whole percent (0..100). */
        public int remainingPct() {
            if (maxDamage <= 0) return 100;
            return Math.round(100f * (maxDamage - damage) / maxDamage);
        }

        /** The kind this stack belongs to: what a mended or replacement stack shares with it. */
        public Kind kind() { return new Kind(itemId, maxDamage); }

        @Override public String toString() {
            return itemId + "/" + maxDamage + "/" + damage;
        }
    }

    /** Registry id + max damage: the part of a stack that wear and mending never change. */
    public record Kind(String itemId, int maxDamage) {}

    /** A low stack seen this tick; {@code tracked} when its slot's alert source is on. */
    public record Observation(Fingerprint fingerprint, boolean tracked, Object payload) {}

    /** How long an unmatched entry may live before pruning (10 s at 20 tps). */
    public static final long PRUNE_AFTER_TICKS = 200L;
    /** Hard cap on tracked items; eviction drops the least-recently-seen. */
    public static final int MAX_TRACKED = 64;

    /** One latched item: its latest state, the last tick it matched, and the caller's fire-time payload. */
    public static final class Entry {
        Fingerprint fingerprint;
        long lastSeenTick;
        boolean present;
        final Object payload;

        Entry(Fingerprint fingerprint, long tick, Object payload) {
            this.fingerprint = fingerprint;
            this.lastSeenTick = tick;
            this.present = true;
            this.payload = payload;
        }

        public Fingerprint fingerprint() { return fingerprint; }
        public long lastSeenTick() { return lastSeenTick; }
        /** Whether a low stack matched this entry on the latest update. */
        public boolean present() { return present; }
        public Object payload() { return payload; }
    }

    private final List<Entry> entries = new ArrayList<>();

    /**
     * Reconcile one tick's scan. {@code low} lists every stack at or below the
     * threshold (equipment and inventory); {@code healthyKinds} lists the
     * kinds the player holds above the threshold. Returns the entries that
     * latched this tick: the caller fires (sound) once for each.
     */
    public List<Entry> update(List<Observation> low, Set<Kind> healthyKinds, long tick) {
        for (Entry e : entries) e.present = false;
        boolean[] used = new boolean[low.size()];

        // Most recently seen entries pick first, so a long-latched item keeps its match.
        List<Entry> order = new ArrayList<>(entries);
        order.sort((a, b) -> Long.compare(b.lastSeenTick, a.lastSeenTick));
        for (Entry e : order) {
            int best = -1;
            int bestDistance = Integer.MAX_VALUE;
            for (int i = 0; i < low.size(); i++) {
                if (used[i]) continue;
                Fingerprint f = low.get(i).fingerprint();
                if (!f.kind().equals(e.fingerprint.kind())) continue;
                int distance = Math.abs(f.damage() - e.fingerprint.damage());
                if (distance < bestDistance) {
                    best = i;
                    bestDistance = distance;
                }
            }
            if (best < 0) continue;
            used[best] = true;
            e.fingerprint = low.get(best).fingerprint();
            e.lastSeenTick = tick;
            e.present = true;
        }

        // An unmatched entry whose kind is held above the threshold was mended or replaced.
        entries.removeIf(e -> !e.present && healthyKinds.contains(e.fingerprint.kind()));
        entries.removeIf(e -> tick - e.lastSeenTick > PRUNE_AFTER_TICKS);

        List<Entry> fired = new ArrayList<>();
        for (int i = 0; i < low.size(); i++) {
            Observation o = low.get(i);
            if (used[i] || !o.tracked()) continue;
            Entry e = new Entry(o.fingerprint(), tick, o.payload());
            entries.add(e);
            fired.add(e);
        }
        evictOverflow();
        fired.retainAll(entries);
        return fired;
    }

    /** All live entries, present or awaiting prune. */
    public Collection<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /** Forget everything (world join / respawn / feature off / feature reset). */
    public void reset() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    /** Cap the list, evicting the least-recently-seen entries first. */
    private void evictOverflow() {
        while (entries.size() > MAX_TRACKED) {
            Entry oldest = entries.get(0);
            for (Entry e : entries) {
                if (e.lastSeenTick < oldest.lastSeenTick) oldest = e;
            }
            entries.remove(oldest);
        }
    }
}
