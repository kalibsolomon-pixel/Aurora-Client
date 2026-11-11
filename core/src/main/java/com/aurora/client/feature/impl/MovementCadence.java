package com.aurora.client.feature.impl;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Bounded movement-gap policy; the adapter supplies only eligible moving entities. */
public final class MovementCadence {
    private static final int WINDOW = 32, MIN_SAMPLES = 6, MAX_ENTITIES = 256;
    private final LinkedHashMap<UUID, Position> positions = new LinkedHashMap<>();
    private final int[] gaps = new int[WINDOW], scratch = new int[WINDOW];
    private int count, next;
    private boolean dirty;
    private float median = 1, factor = 1;

    public void observe(UUID id, long tick, double x, double y, double z) {
        Position previous = positions.get(id);
        if (previous == null) {
            previous = new Position();
            positions.put(id, previous);
            if (positions.size() > MAX_ENTITIES) positions.remove(positions.keySet().iterator().next());
        } else {
            long gap = tick - previous.tick;
            double dx = x - previous.x, dy = y - previous.y, dz = z - previous.z;
            double stepSq = dx * dx + dy * dy + dz * dz;
            if (gap >= 1 && gap <= 20 && stepSq > 1e-6 && stepSq < 6.25) {
                gaps[next] = (int) gap;
                next = (next + 1) % WINDOW;
                count = Math.min(WINDOW, count + 1);
                dirty = true;
            }
        }
        previous.tick = tick;
        previous.x = x; previous.y = y; previous.z = z;
    }

    /** Preserve the existing per-render-frame easing, even when the window did not change. */
    public void finishFrame() {
        if (dirty && count >= MIN_SAMPLES) {
            System.arraycopy(gaps, 0, scratch, 0, count);
            Arrays.sort(scratch, 0, count);
            median = Math.min(6, scratch[count / 2]);
        }
        dirty = false;
        factor += (median - factor) * 0.1f;
    }
    public float factor() { return factor; }
    public void clear() {
        positions.clear();
        count = next = 0;
        dirty = false;
        median = factor = 1;
    }

    private static final class Position {
        long tick;
        double x, y, z;
    }
}
