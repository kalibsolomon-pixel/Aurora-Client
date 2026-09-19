package com.aurora.client.ui.util;

/**
 * The shared viewport/bounds truth for one scrolling host's content —
 * the Phase C-1 primitive (DESIGN_LANGUAGE §13.3: rendered visibility and
 * interactive availability must agree).
 *
 * <p>A ClipBand is the <b>meaningful visible interaction region</b> of a
 * scrollable content area: the exact rectangle the host's content scissor
 * paints. Whatever geometry the host derives from it — the scissor itself,
 * the render cull, the pointer hit-test, the hover test, the semantic /
 * keyboard availability sweep — describes the same pixels. A control's raw
 * bounds may extend past the band while it scrolls; only the intersection
 * is visible, and only the intersection is interactive:
 *
 * <pre>
 * visibleBounds = rawBounds ∩ band      (empty ⇒ not painted, not clickable,
 *                                        not hovered, not keyboard-available)
 * pointer at p is actionable ⟺ p ∈ band ∧ p ∈ rawBounds
 * </pre>
 *
 * <p>Semantics are deliberately pinned (unit-tested): the band is
 * <b>half-open</b> — {@code [x, x+w) × [y, y+h)} — the same convention as
 * {@code Widget.inBounds} and {@code SemanticActionControl.contains}. Edge
 * contact is therefore <i>not</i> intersection: a rectangle whose left edge
 * equals the band's right edge does not intersect. Degenerate bands
 * (non-positive width/height) contain and intersect nothing.
 *
 * <p>This is a value, not a framework: four ints, no dependencies (no
 * Minecraft imports — headless-testable), no retained layout, no widget
 * knowledge. Hosts compute it once per frame (it derives only from the
 * window geometry) and reuse the instance across their walks; it is safe to
 * share because nothing mutates it. Consumers so far: {@code AuroraScreen}
 * (Phase C-1); the pack browser's card grid, the manager inline editors and
 * the pack modal's animation geometry are the documented later adopters
 * (Phase C-8 / the C-1 rollout decision) — adopt per-host, never wholesale.
 */
public final class ClipBand {

    public final int x;
    public final int y;
    public final int width;
    public final int height;

    public ClipBand(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** Exclusive right edge ({@code x + width}); the first x NOT in the band. */
    public int xEnd() {
        return x + width;
    }

    /** Exclusive bottom edge ({@code y + height}); the first y NOT in the band. */
    public int yEnd() {
        return y + height;
    }

    /** A degenerate band (non-positive extent) is empty — nothing is inside it. */
    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }

    /**
     * Whether the point is on a pixel the band's scissor would paint.
     * Half-open: the exclusive edges are outside.
     */
    public boolean contains(double px, double py) {
        return px >= x && px < xEnd() && py >= y && py < yEnd();
    }

    /**
     * Whether the rectangle shares at least one pixel with the band — i.e.
     * whether any part of it is visible. Half-open on both: edge contact
     * (touching without overlap) is NOT intersection. Non-positive extent
     * rectangles never intersect.
     */
    public boolean intersects(double rx, double ry, double rw, double rh) {
        if (isEmpty() || rw <= 0 || rh <= 0) return false;
        return rx < xEnd() && rx + rw > x && ry < yEnd() && ry + rh > y;
    }

    /**
     * Clamps a y coordinate into the band's closed y-range
     * {@code [y, yEnd]} — the nearest y that is inside (or on the far edge
     * of) the band. Kept for callers that need to pull a coordinate into
     * the visible range; the point-gate form ({@link #contains}) is the
     * usual coupling primitive.
     */
    public double clampY(double v) {
        if (v < y) return y;
        if (v > yEnd()) return yEnd();
        return v;
    }
}
