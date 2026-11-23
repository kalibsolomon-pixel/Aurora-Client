package com.aurora.client.hud;

/**
 * Which way a HUD module grows along one axis as its content changes size.
 *
 * <p>A module that grows keeps a <em>reference box</em> of its nominal size
 * where its anchor and offset put it, and its real box shares one edge with
 * that reference: {@link #AWAY_FROM_CENTER} keeps the edge facing the screen
 * centre (Armor HUD: new pieces extend outward), {@link #TOWARD_CENTER} the
 * edge facing the screen border (Potion HUD: new effects extend inward). A
 * reference box that straddles the screen centre grows both ways, like a
 * centre anchor. At the nominal size the real box is the reference box, so
 * a layout saved before a module grew this way keeps its place.
 */
public enum HudGrowth {
    /** The anchor alone decides: left anchors grow right, centre anchors both ways. */
    ANCHORED,
    AWAY_FROM_CENTER,
    TOWARD_CENTER;

    /**
     * Start coordinate of a box of {@code size} along one axis.
     *
     * @param refStart start of the reference box ({@code anchor.applyX(screen, refSize) + offset})
     * @param refSize  the reference (nominal) size, scaled
     * @param size     the real size, scaled
     * @param screen   the screen size along this axis
     */
    public int start(int refStart, int refSize, int size, int screen) {
        if (this == ANCHORED) return refStart;
        int mid = screen / 2;
        boolean before = refStart + refSize <= mid;   // left of / above the centre
        boolean after = refStart >= mid;              // right of / below the centre
        if (!before && !after) return refStart + (refSize - size) / 2;
        boolean keepEnd = (this == AWAY_FROM_CENTER) == before;
        return keepEnd ? refStart + refSize - size : refStart;
    }

    /**
     * Inverse of {@link #start}: the reference start that puts a box of {@code size} at
     * {@code start}, or as near it as the rule allows. Where the reference box crosses the
     * screen centre the rule switches edges, so a few positions are skipped there.
     */
    public int refStart(int start, int refSize, int size, int screen) {
        if (this == ANCHORED) return start;
        // Each side's rule gives one candidate; keep the one that maps back nearest.
        int[] candidates = {start + size - refSize, start, start - (refSize - size) / 2};
        int best = candidates[0];
        int bestError = Integer.MAX_VALUE;
        for (int ref : candidates) {
            int error = Math.abs(start(ref, refSize, size, screen) - start);
            if (error < bestError) {
                best = ref;
                bestError = error;
            }
        }
        return best;
    }

    /** Whether a reference box lies wholly after (right of / below) the screen centre. */
    public static boolean after(int refStart, int screen) {
        return refStart >= screen / 2;
    }

    /** Whether a reference box lies wholly before (left of / above) the screen centre. */
    public static boolean before(int refStart, int refSize, int screen) {
        return refStart + refSize <= screen / 2;
    }
}
