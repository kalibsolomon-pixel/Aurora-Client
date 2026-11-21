package com.aurora.client.performance;

/**
 * Decides whether opaque terrain certainly hides a box from the camera.
 *
 * <p>The test is a sufficient condition, never a guess. A layer of cells one
 * cell thick and perpendicular to an axis must lie wholly between every eye
 * position and the box, and every cell of that layer that a line from any eye
 * position to any point of the box crosses must be opaque. Each such line then
 * enters an opaque cell through the layer's near face before it reaches the
 * box. Callers pass the eye as a cube (view bobbing, rounding), the box grown
 * by everything the renderer may draw around it (model overhang, shadow), and
 * a cell predicate that answers true only for full opaque cubes the renderer
 * has certainly drawn. A layer too close to the eye, too wide to check, or not
 * found within the bounds answers false: a miss only costs an optimization.
 *
 * <p>Work is bounded per call by {@code maxLayers} layers per axis and
 * {@code maxCells} cells per layer; {@link #probes()} counts every predicate
 * call so the caller can bound a frame. Nothing here allocates.
 */
public final class OcclusionSweep {
    @FunctionalInterface
    public interface Cells {
        boolean opaque(int x, int y, int z);
    }

    /**
     * Least distance from any eye position to the layer's near face. A crossing
     * point the viewer can see lies at least this far from the eye, so the
     * near clip plane (0.05) cannot cut it away at any field of view or aspect
     * ratio the game allows.
     */
    static final double NEAR = 0.5;
    /** Cell ranges grow by this much so a crossing on a cell edge checks both cells. */
    static final double EDGE = 1.0e-3;
    /** Coordinate magnitude bound well below integer overflow (Minecraft's world border is 3e7). */
    static final double MAX_COORD = 3.2e7;

    private final int maxLayers;
    private final int maxCells;
    private int probes;

    public OcclusionSweep(int maxLayers, int maxCells) {
        this.maxLayers = maxLayers;
        this.maxCells = maxCells;
    }

    public int probes() {
        return probes;
    }

    public void resetProbes() {
        probes = 0;
    }

    /**
     * True when opaque cells hide every point of {@code [x0,x1]×[y0,y1]×[z0,z1]}
     * from every eye position in the cube of half-size {@code eyeRadius} around
     * {@code (ex, ey, ez)}.
     */
    public boolean hidden(Cells cells, double ex, double ey, double ez, double eyeRadius,
                          double x0, double y0, double z0, double x1, double y1, double z1) {
        if (!(eyeRadius >= 0) || eyeRadius > MAX_COORD || !inRange(ex, ey, ez) || !inRange(x0, y0, z0)
                || !inRange(x1, y1, z1) || x1 < x0 || y1 < y0 || z1 < z0) return false;
        // Try the axes in order of how far the box lies from the eye along them.
        double gx = gap(ex, eyeRadius, x0, x1), gy = gap(ey, eyeRadius, y0, y1), gz = gap(ez, eyeRadius, z0, z1);
        int first = gx >= gy ? (gx >= gz ? 0 : 2) : (gy >= gz ? 1 : 2);
        int last = gx < gy ? (gx < gz ? 0 : 2) : (gy < gz ? 1 : 2);
        int middle = 3 - first - last;
        return axis(cells, first, ex, ey, ez, eyeRadius, x0, y0, z0, x1, y1, z1)
                || axis(cells, middle, ex, ey, ez, eyeRadius, x0, y0, z0, x1, y1, z1)
                || axis(cells, last, ex, ey, ez, eyeRadius, x0, y0, z0, x1, y1, z1);
    }

    private static boolean inRange(double a, double b, double c) {
        return Math.abs(a) <= MAX_COORD && Math.abs(b) <= MAX_COORD && Math.abs(c) <= MAX_COORD;
    }

    private static double gap(double eye, double r, double lo, double hi) {
        return Math.max(lo - (eye + r), (eye - r) - hi);
    }

    private boolean axis(Cells cells, int axis, double ex, double ey, double ez, double r,
                         double x0, double y0, double z0, double x1, double y1, double z1) {
        double e = pick(axis, ex, ey, ez), lo = pick(axis, x0, y0, z0), hi = pick(axis, x1, y1, z1);
        int ua = axis == 0 ? 1 : 0, va = axis == 2 ? 1 : 2;
        double eu = pick(ua, ex, ey, ez), ev = pick(va, ex, ey, ez);
        double bu0 = pick(ua, x0, y0, z0), bu1 = pick(ua, x1, y1, z1);
        double bv0 = pick(va, x0, y0, z0), bv1 = pick(va, x1, y1, z1);
        // Mirror the negative direction onto the positive one: depth d' = -d.
        int sign = lo - (e + r) >= (e - r) - hi ? 1 : -1;
        double eyeNear = sign > 0 ? e - r : -(e + r), eyeFar = sign > 0 ? e + r : -(e - r);
        double boxNear = sign > 0 ? lo : -hi, boxFar = sign > 0 ? hi : -lo;
        // Integer planes p (mirrored) with eyeFar + NEAR <= p <= boxNear - EDGE.
        int pLo = (int) Math.ceil(eyeFar + NEAR), pHi = (int) Math.floor(boxNear - EDGE);
        if (pHi < pLo) return false;
        int count = Math.min(pHi - pLo + 1, maxLayers);
        for (int n = 0; n < count; n++) {
            // Alternate between the layer nearest the eye and the one nearest the box.
            int plane = (n & 1) == 0 ? pLo + (n >> 1) : pHi - (n >> 1);
            double tMax = (plane - eyeNear) / (boxNear - eyeNear);
            double tMin = (plane - eyeFar) / (boxFar - eyeFar);
            double uMin = Math.min(lerp(tMin, eu - r, bu0), lerp(tMax, eu - r, bu0));
            double uMax = Math.max(lerp(tMin, eu + r, bu1), lerp(tMax, eu + r, bu1));
            double vMin = Math.min(lerp(tMin, ev - r, bv0), lerp(tMax, ev - r, bv0));
            double vMax = Math.max(lerp(tMin, ev + r, bv1), lerp(tMax, ev + r, bv1));
            int i0 = (int) Math.floor(uMin - EDGE), i1 = (int) Math.floor(uMax + EDGE);
            int j0 = (int) Math.floor(vMin - EDGE), j1 = (int) Math.floor(vMax + EDGE);
            if ((long) (i1 - i0 + 1) * (j1 - j0 + 1) > maxCells) continue;
            // The mirrored plane p is the near face of real layer p, or of -p-1 when mirrored.
            int layer = sign > 0 ? plane : -plane - 1;
            if (layerOpaque(cells, axis, layer, i0, i1, j0, j1)) return true;
        }
        return false;
    }

    private boolean layerOpaque(Cells cells, int axis, int layer, int i0, int i1, int j0, int j1) {
        // The centre cell rejects most open layers in one probe.
        int ic = (i0 + i1) >> 1, jc = (j0 + j1) >> 1;
        if (!probe(cells, axis, layer, ic, jc)) return false;
        for (int i = i0; i <= i1; i++)
            for (int j = j0; j <= j1; j++)
                if ((i != ic || j != jc) && !probe(cells, axis, layer, i, j)) return false;
        return true;
    }

    private boolean probe(Cells cells, int axis, int layer, int u, int v) {
        probes++;
        return switch (axis) {
            case 0 -> cells.opaque(layer, u, v);
            case 1 -> cells.opaque(u, layer, v);
            default -> cells.opaque(u, v, layer);
        };
    }

    private static double lerp(double t, double eye, double box) {
        return eye + t * (box - eye);
    }

    private static double pick(int axis, double x, double y, double z) {
        return axis == 0 ? x : axis == 1 ? y : z;
    }
}
