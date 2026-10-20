package com.aurora.client.worldmap;

/**
 * World-aligned colour grid behind the minimap. One cell is one sample of the
 * map engine; the grid is anchored to whole cells in world space, so moving the
 * player never changes what a cell holds — only which cells are in view.
 *
 * <p>Moving by a few cells reuses the overlap ({@link System#arraycopy}) and
 * samples just the newly exposed strips; the minimap then resamples this grid
 * per frame at device resolution with a sub-pixel affine transform, so terrain
 * glides instead of stepping at the engine sampling cadence.
 */
public final class MinimapGrid {
    /** Colour source for one world-aligned cell. */
    @FunctionalInterface
    public interface Sampler { int sample(int cellX, int cellZ); }

    private int[] cells = new int[0], scratch = new int[0];
    private int n, originX, originZ;
    private boolean valid;

    public int size() { return n; }
    public int originX() { return originX; }
    public int originZ() { return originZ; }
    public boolean valid() { return valid; }
    public int[] cells() { return cells; }
    public void invalidate() { valid = false; }

    /** Samples every cell of an {@code n×n} grid whose first cell is {@code (ox, oz)}. */
    public void reset(int n, int ox, int oz, Sampler sampler) {
        if (n <= 0) throw new IllegalArgumentException("Grid size must be positive");
        if (cells.length != n * n) { cells = new int[n * n]; scratch = new int[n * n]; }
        this.n = n; originX = ox; originZ = oz;
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) cells[j * n + i] = sampler.sample(ox + i, oz + j);
        valid = true;
    }

    /**
     * Re-anchors the grid at {@code (ox, oz)}, keeping overlapping cells.
     * Returns {@code false} when the origin is unchanged (nothing sampled).
     */
    public boolean moveTo(int ox, int oz, Sampler sampler) {
        if (!valid) throw new IllegalStateException("Grid must be reset before it can move");
        int dx = ox - originX, dz = oz - originZ;
        if (dx == 0 && dz == 0) return false;
        if (Math.abs(dx) >= n || Math.abs(dz) >= n) { reset(n, ox, oz, sampler); return true; }
        int from = Math.max(0, -dx), to = Math.min(n, n - dx);
        for (int j = 0; j < n; j++) {
            int row = j * n, old = j + dz;
            if (old < 0 || old >= n) {
                for (int i = 0; i < n; i++) scratch[row + i] = sampler.sample(ox + i, oz + j);
                continue;
            }
            System.arraycopy(cells, old * n + from + dx, scratch, row + from, to - from);
            for (int i = 0; i < from; i++) scratch[row + i] = sampler.sample(ox + i, oz + j);
            for (int i = to; i < n; i++) scratch[row + i] = sampler.sample(ox + i, oz + j);
        }
        int[] swap = cells; cells = scratch; scratch = swap;
        originX = ox; originZ = oz;
        return true;
    }
}
