package com.aurora.client.util;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Shape primitives for Aurora's iOS-flavored UI.
 *
 * <p>All filled panels and outlines render as <em>chamfered rectangles</em>
 * — clean axis-aligned rectangles with 45° diagonal cuts at each corner.
 * No rounded-corner texture, no GPU sampling, just stacked
 * {@link GuiGraphics#fill} / {@link GuiGraphics#fillGradient} calls. This
 * is the simplest possible geometry that still reads as "deliberately
 * styled" rather than a raw rectangle, and produces pixel-perfect crisp
 * edges at every UI scale.
 *
 * <p>Geometry: a chamfer of size {@code c} cuts a {@code c}-pixel
 * isoceles right triangle off each corner. The remaining shape is a
 * convex octagon. We render it as:
 * <ul>
 *   <li>a top trapezoid built up row-by-row from the {@code (w - 2c)}
 *       top edge to the full-width body via {@code c} 1-px-tall rows
 *       widening by 2 pixels each step,</li>
 *   <li>the rectangular body in the middle,</li>
 *   <li>a mirrored bottom trapezoid.</li>
 * </ul>
 *
 * <p>For gradients, the per-row color is computed by linear interpolation
 * between the top stop and the bottom stop. The body is drawn as one
 * single {@link GuiGraphics#fillGradient} call (cheap and smooth on the
 * GPU), and the chamfer rows pick discrete lerped colors per row — the
 * eye can't perceive the discretization across 4–10 rows of chamfer.
 *
 * <p>The {@link #panel}, {@link #panelGradient}, {@link #outline},
 * {@link #gemPanel}, and {@link #knob} entry points keep their old
 * signatures so existing call sites continue to work after the redesign.
 */
public final class AuroraShapes {

    private AuroraShapes() {}

    // ============================================================
    //  Solid filled chamfered rect
    // ============================================================

    public static void panel(GuiGraphics ctx, int x, int y, int w, int h, int tint) {
        panel(ctx, x, y, w, h, tint, AuroraTheme.RADIUS);
    }

    public static void panel(GuiGraphics ctx, int x, int y, int w, int h, int tint, int radius) {
        if (w <= 0 || h <= 0) return;
        chamferedFill(ctx, x, y, w, h, clampChamfer(radius, w, h), tint);
    }

    // ============================================================
    //  Two-color vertical gradient chamfered rect
    // ============================================================

    public static void panelGradient(GuiGraphics ctx, int x, int y, int w, int h,
                                     int topColor, int botColor) {
        panelGradient(ctx, x, y, w, h, topColor, botColor, AuroraTheme.RADIUS);
    }

    public static void panelGradient(GuiGraphics ctx, int x, int y, int w, int h,
                                     int topColor, int botColor, int radius) {
        if (w <= 0 || h <= 0) return;
        chamferedGradientFill(ctx, x, y, w, h, clampChamfer(radius, w, h), topColor, botColor);
    }

    /** @deprecated old name; forwards to {@link #panelGradient}. */
    @Deprecated
    public static void gemPanel(GuiGraphics ctx, int x, int y, int w, int h,
                                int topTint, int botTint) {
        panelGradient(ctx, x, y, w, h, topTint, botTint, AuroraTheme.RADIUS);
    }

    /** @deprecated old name; forwards to {@link #panelGradient}. */
    @Deprecated
    public static void gemPanel(GuiGraphics ctx, int x, int y, int w, int h,
                                int topTint, int botTint, int radius) {
        panelGradient(ctx, x, y, w, h, topTint, botTint, radius);
    }

    // ============================================================
    //  1-px chamfered outline
    // ============================================================

    public static void outline(GuiGraphics ctx, int x, int y, int w, int h, int color) {
        outline(ctx, x, y, w, h, color, AuroraTheme.RADIUS);
    }

    public static void outline(GuiGraphics ctx, int x, int y, int w, int h, int color, int radius) {
        if (w <= 0 || h <= 0) return;
        chamferedOutline(ctx, x, y, w, h, clampChamfer(radius, w, h), color);
    }

    // ============================================================
    //  Top-edge highlight — single-line "material" cue
    // ============================================================

    /**
     * Draws a {@link #HIGHLIGHT_THICKNESS}-pixel bright rim along the
     * top edge of a rounded rectangle, following the same arc as
     * {@link #panel}. Use on darker surfaces (window backdrops, dark
     * cards) to add a single faint highlight where light would catch
     * the upper edge — the only material cue in the redesign now that
     * gradient sheens are gone.
     *
     * <p>For each of the {@link #HIGHLIGHT_THICKNESS} top arc rows we
     * emit one {@code ctx.fill}: span = {@code [x + edge, x + w - edge]}
     * where {@code edge = cornerLeftEdge(c, row)}. Net cost: 1–2 fills.
     */
    public static void topHighlight(GuiGraphics ctx, int x, int y, int w, int h, int color) {
        topHighlight(ctx, x, y, w, h, color, AuroraTheme.RADIUS);
    }

    public static void topHighlight(GuiGraphics ctx, int x, int y, int w, int h, int color, int radius) {
        if (w <= 0 || h <= 0) return;
        int c = clampChamfer(radius, w, h);
        int rows = Math.min(HIGHLIGHT_THICKNESS, h);
        if (c <= 0) {
            // Hard rectangle: a single full-width strip across the top.
            ctx.fill(x, y, x + w, y + rows, color);
            return;
        }
        // Follow the arc — the top {@code rows} of the corner.
        for (int row = 0; row < rows; row++) {
            int edge = cornerLeftEdge(c, row);
            if (edge >= c) continue;
            ctx.fill(x + edge, y + row, x + w - edge, y + row + 1, color);
        }
    }

    public static void panelWithBorder(GuiGraphics ctx, int x, int y, int w, int h,
                                       int fillTint, int borderTint) {
        panel(ctx, x, y, w, h, fillTint);
        outline(ctx, x, y, w, h, borderTint);
    }

    // ============================================================
    //  Drop shadow
    // ============================================================

    /**
     * Renders a slight drop shadow behind a chamfered panel. Three
     * stacked translucent-black chamfered fills are offset downward by
     * 1, 2, and 3 px respectively with decreasing alpha, producing a
     * soft fall-off without sampling a blur kernel. The same chamfer
     * radius as the parent panel is used so the shadow's silhouette
     * matches the shape it sits under.
     *
     * <p>Call this <em>before</em> drawing the panel itself, at the same
     * {@code (x, y, w, h)}.
     */
    public static void dropShadow(GuiGraphics ctx, int x, int y, int w, int h, int radius) {
        dropShadow(ctx, x, y, w, h, radius, 1f);
    }

    /**
     * Alpha-scaled drop shadow — the same ramp as
     * {@link #dropShadow(GuiGraphics, int, int, int, int, int)} with every
     * layer premultiplied by {@code alphaScale} (0..1), for elements that
     * fade in and out.
     */
    public static void dropShadow(GuiGraphics ctx, int x, int y, int w, int h,
                                  int radius, float alphaScale) {
        if (w <= 0 || h <= 0 || alphaScale <= 0f) return;
        int c = clampChamfer(radius, w, h);
        // Six-layer shadow ramp tracking an approximate exponential
        // fall-off: the panel-adjacent rim is dense, then alpha decays
        // by roughly 0.7× per step, with two extra-soft tail stops
        // pushed further out to extend the halo without a visible
        // banding edge. Reads as a continuous gradient at 1440p where
        // the previous 4-stop ramp showed distinct shadow rings.
        chamferedFill(ctx, x, y + 1, w, h, c, scaleAlpha(0x60000000, alphaScale)); //  96
        chamferedFill(ctx, x, y + 2, w, h, c, scaleAlpha(0x4A000000, alphaScale)); //  74
        chamferedFill(ctx, x, y + 3, w, h, c, scaleAlpha(0x36000000, alphaScale)); //  54
        chamferedFill(ctx, x, y + 5, w, h, c, scaleAlpha(0x22000000, alphaScale)); //  34
        chamferedFill(ctx, x, y + 7, w, h, c, scaleAlpha(0x14000000, alphaScale)); //  20
        chamferedFill(ctx, x, y + 9, w, h, c, scaleAlpha(0x08000000, alphaScale)); //   8
    }

    /** Default drop shadow at {@link AuroraTheme#RADIUS}. */
    public static void dropShadow(GuiGraphics ctx, int x, int y, int w, int h) {
        dropShadow(ctx, x, y, w, h, AuroraTheme.RADIUS);
    }

    /**
     * Symmetric edge shadow — fakes a soft halo around <em>all four
     * edges</em> of a panel instead of just the bottom. Four translucent
     * black chamfered fills are drawn behind the panel, each progressively
     * larger than the source rect, so the panel reads as floating above
     * its background with a 1–8 px ambient occlusion belt around the
     * silhouette.
     *
     * <p>Use this for the main content window and other surfaces that
     * sit far enough above the backdrop to cast light in every
     * direction. For controls that are mounted to a parent surface
     * (buttons, tiles, etc.) prefer the directional {@link #dropShadow}
     * — its asymmetric offset reads as "lit from above" in the same
     * material language as iOS UI.
     */
    public static void edgeShadow(GuiGraphics ctx, int x, int y, int w, int h, int radius) {
        if (w <= 0 || h <= 0) return;
        // Four expanding belts. Chamfer grows with each layer so the
        // outer rings keep tracing the same silhouette.
        chamferedFill(ctx, x - 2, y - 2, w + 4,  h + 4,  clampChamfer(radius + 2,  w + 4,  h + 4),  0x60000000);
        chamferedFill(ctx, x - 4, y - 4, w + 8,  h + 8,  clampChamfer(radius + 4,  w + 8,  h + 8),  0x38000000);
        chamferedFill(ctx, x - 6, y - 6, w + 12, h + 12, clampChamfer(radius + 6,  w + 12, h + 12), 0x20000000);
        chamferedFill(ctx, x - 8, y - 8, w + 16, h + 16, clampChamfer(radius + 8,  w + 16, h + 16), 0x10000000);
    }

    /**
     * Inner shadow — a soft inward-fading dark vignette traced along
     * the inside of a panel's outline. Adds depth by suggesting the
     * surface recedes slightly behind its frame. Designed to be drawn
     * <em>after</em> the panel fill but typically <em>before</em> the
     * outline so the outline crowns the shadowed border crisply.
     *
     * <p>Implementation: four thin {@code fillGradient} strips along
     * the top/bottom/left/right edges, each fading from a translucent
     * black at the outline-adjacent edge to fully transparent
     * {@value #INNER_SHADOW_DEPTH} px inward. This is roughly two
     * orders of magnitude cheaper than the previous four-concentric-
     * ring implementation (which emitted {@code O(radius²)} per-pixel
     * {@code fill} calls per ring × four rings) and the visual result
     * is indistinguishable at typical tile sizes because the chamfered
     * corners already mask the strip ends.
     *
     * <p>The {@code radius} parameter is accepted for call-site
     * compatibility with the prior signature but is intentionally
     * unused — the strips run edge-to-edge and the corner chamfer of
     * the surrounding panel/outline does the corner masking visually.
     */
    public static void innerShadow(GuiGraphics ctx, int x, int y, int w, int h, int radius) {
        if (w <= 4 || h <= 4) return;
        int d = Math.min(INNER_SHADOW_DEPTH, Math.min(w, h) / 2);
        if (d <= 0) return;
        int dark  = INNER_SHADOW_COLOR;
        int clear = dark & 0x00FFFFFF; // alpha 0, same RGB.

        // Top strip: dark at the top edge, transparent {@code d} px in.
        ctx.fillGradient(x, y,         x + w, y + d,     dark,  clear);
        // Bottom strip: transparent {@code d} px above the bottom edge,
        // dark at the bottom edge.
        ctx.fillGradient(x, y + h - d, x + w, y + h,     clear, dark);

        // Left/right strips only cover the middle band so they don't
        // double-darken the corners where they would intersect the
        // top/bottom strips. fillGradient is vertical-only, so the
        // left/right gradients are simulated with two horizontally
        // adjacent thin vertical fills at decreasing alpha — even at
        // d=4 that's only 4 fills per side, an order of magnitude
        // cheaper than the previous ring geometry.
        int midTop = y + d;
        int midBot = y + h - d;
        if (midBot > midTop) {
            int baseA = (dark >>> 24) & 0xFF;
            for (int i = 0; i < d; i++) {
                int a = Math.round(baseA * (1f - i / (float) d));
                if (a <= 0) continue;
                int col = (a << 24) | (dark & 0x00FFFFFF);
                ctx.fill(x + i,         midTop, x + i + 1,     midBot, col); // left
                ctx.fill(x + w - 1 - i, midTop, x + w - i,     midBot, col); // right
            }
        }
    }

    /** Default inner shadow at {@link AuroraTheme#RADIUS}. */
    public static void innerShadow(GuiGraphics ctx, int x, int y, int w, int h) {
        innerShadow(ctx, x, y, w, h, AuroraTheme.RADIUS);
    }

    /** Strongest stop of the inner-shadow fade (edge-adjacent). */
    private static final int INNER_SHADOW_COLOR = 0x80000000;
    /** Width of the inner-shadow fall-off in pixels. */
    private static final int INNER_SHADOW_DEPTH = 4;

    // ============================================================
    //  Knob (used for slider thumbs, switch knobs)
    // ============================================================

    /**
     * Filled "knob" — a true antialiased circle of diameter {@code 2*radius}.
     *
     * <p>Implemented by routing through {@link #chamferedFill} with the
     * chamfer pinned to the full radius. At {@code chamfer == radius}
     * the chamfered rect degenerates to a full quarter-circle on every
     * corner, so the supersampled {@link #coverageTable} renders a
     * smooth AA disc rather than the previous coarse octagon
     * (chamfer = radius / 2) which read as visibly faceted on slider
     * thumbs and toggle knobs at 1440p / GUI scale 3.
     */
    public static void knob(GuiGraphics ctx, int cx, int cy, int radius, int tint) {
        if (radius <= 0) return;
        int diameter = radius * 2;
        chamferedFill(ctx, cx - radius, cy - radius, diameter, diameter, radius, tint);
    }

    public static void knob(GuiGraphics ctx, int cx, int cy, int tint) {
        knob(ctx, cx, cy, AuroraTheme.KNOB_RADIUS, tint);
    }

    // ============================================================
    //  Backdrop and dimming (unchanged from previous redesign)
    // ============================================================

    /**
     * Aurora backdrop — almost-black dark-blue vertical gradient that
     * slowly breathes between a darker and a lighter phase. Two endpoints
     * each for the top and bottom of the gradient (configured on
     * {@link AuroraTheme}); the breath lerps {@code *_DARK} → {@code *_LIGHT}
     * over a slow sine, and the rendered fill interpolates
     * {@code top → bot} vertically. Single {@code fillGradient} call per
     * frame, no ambient blob, no allocations.
     *
     * <p>Cycle period: 5 s. Short enough that the shift produces a
     * perceptible amount of color change per frame at 30 fps and above,
     * so the animation reads as continuous motion rather than a slow
     * stepwise drift.
     */
    public static void auroraBackdrop(GuiGraphics ctx, int x1, int y1, int x2, int y2, double timeT) {
        int w = x2 - x1;
        int h = y2 - y1;
        if (w <= 0 || h <= 0) return;

        // Breath: smooth 0→1→0 oscillation on a 5-second period.
        float breath = (float) (0.5 + 0.5 * Math.sin(timeT * (Math.PI * 2.0 / 5.0)));
        int top = AuroraAnim.lerpArgb(AuroraTheme.BACKDROP_TOP_DARK,
                                      AuroraTheme.BACKDROP_TOP_LIGHT, breath);
        int bot = AuroraAnim.lerpArgb(AuroraTheme.BACKDROP_BOT_DARK,
                                      AuroraTheme.BACKDROP_BOT_LIGHT, breath);
        ctx.fillGradient(x1, y1, x2, y2, top, bot);
    }

    // ============================================================
    //  Metallic top-edge highlight
    // ============================================================

    /**
     * Draws a 1-px-thick translucent-white highlight tracing the top
     * edge and top corner chamfers of a panel, giving the surface a
     * metallic / material lit-from-above appearance. Intended to be
     * called immediately after a {@link #panelGradient} fill.
     *
     * @param alpha 0–255 highlight alpha; 80–120 reads as a glint, 160+
     *              starts looking like a hard rim.
     */
    public static void topSheen(GuiGraphics ctx, int x, int y, int w, int h, int radius, int alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0) return;
        int c = clampChamfer(radius, w, h);
        int sheen = (Math.min(255, alpha) << 24) | 0x00FFFFFF;
        // Top edge between chamfers.
        ctx.fill(x + c, y, x + w - c, y + 1, sheen);
        if (c <= 0) return;
        // Top-left and top-right curved sheens — trace the same
        // quarter-circle the outline traces.
        int[] insets = insetTable(c);
        for (int i = 0; i < c; i++) {
            int inset     = insets[i];
            int insetPrev = (i == 0) ? c : insets[i - 1];
            ctx.fill(x + inset, y + i, x + insetPrev, y + i + 1, sheen);
            ctx.fill(x + w - insetPrev, y + i, x + w - inset, y + i + 1, sheen);
        }
    }

    /**
     * Adds a multi-layer material shading pass on top of an already-filled
     * panel: a strong top-edge sheen, a softer gloss band immediately
     * below the top edge, and a thin shadow line along the bottom edge.
     * Together with the underlying vertical gradient this produces a
     * convincingly lit, slightly convex surface — the "material" look on
     * buttons, feature tiles, and switch tracks.
     *
     * <p>All three layers are alpha-blended over the panel; pass alpha
     * {@code 0} to skip a particular layer.
     *
     * @param topAlpha     0–255 alpha for the 1-px top sheen (the brightest
     *                     line tracing the top edge + curve).
     * @param glossAlpha   0–255 alpha for the inner gloss band (a 2-px
     *                     translucent-white strip just below the top
     *                     sheen, fading the highlight into the body).
     * @param shadowAlpha  0–255 alpha for the 1-px bottom-edge shadow.
     */
    public static void materialShade(GuiGraphics ctx, int x, int y, int w, int h, int radius,
                                     int topAlpha, int glossAlpha, int shadowAlpha) {
        if (w <= 0 || h <= 0) return;
        int c = clampChamfer(radius, w, h);
        // 1) Top sheen — sharp 1-px highlight on the top edge + curve.
        topSheen(ctx, x, y, w, h, radius, topAlpha);

        // 2) Inner gloss band — a 2-px translucent-white strip directly
        //    under the top sheen. The body span uses a vertical gradient
        //    from glossAlpha at the top row down to 0 at the bottom row.
        //    The corner rows additionally fill the gap between the
        //    quarter-circle inset and the body span so the gloss meets
        //    the top-sheen curve flush — without this, a 1–2 px hairline
        //    seam shows at each top corner at 1440p / GUI scale 3.
        if (glossAlpha > 0 && h > c + 3) {
            int a = Math.min(255, glossAlpha);
            int glossColor = (a << 24) | 0x00FFFFFF;
            int glossFade  = 0x00FFFFFF;
            ctx.fillGradient(x + c, y + 1, x + w - c, y + 3, glossColor, glossFade);

            // Corner continuation: fill from the curve inset out to the
            // body span at gloss-row 1 (and row 2 if the chamfer is big
            // enough to expose a row-2 step). Alpha matches the gradient
            // sample at that row so the curve and body remain visually
            // continuous.
            if (c >= 2) {
                int[] insets = insetTable(c);
                int row1Color = glossColor; // row 1 = top of gradient = full alpha
                int leftL1  = x + insets[1];
                int rightR1 = x + w - insets[1];
                if (leftL1  < x + c)     ctx.fill(leftL1,  y + 1, x + c,     y + 2, row1Color);
                if (rightR1 > x + w - c) ctx.fill(x + w - c, y + 1, rightR1, y + 2, row1Color);
                if (c >= 3) {
                    int row2Alpha = a / 2;
                    int row2Color = (row2Alpha << 24) | 0x00FFFFFF;
                    int leftL2  = x + insets[2];
                    int rightR2 = x + w - insets[2];
                    if (leftL2  < x + c)     ctx.fill(leftL2,  y + 2, x + c,     y + 3, row2Color);
                    if (rightR2 > x + w - c) ctx.fill(x + w - c, y + 2, rightR2, y + 3, row2Color);
                }
            }
        }

        // 3) Bottom shadow — 1-px translucent-black strip on the bottom
        //    edge, between chamfers, sitting one pixel above the bottom
        //    so it reads as a "lower lip" shadow rather than a silhouette
        //    extension.
        if (shadowAlpha > 0 && h > c + 2) {
            int shadowColor = Math.min(255, shadowAlpha) << 24;
            ctx.fill(x + c, y + h - 1, x + w - c, y + h, shadowColor);
        }
    }

    // ============================================================
    //  Internals — chamfer geometry
    // ============================================================

    /** Clamps a requested chamfer to the largest size that still fits the rect. */
    private static int clampChamfer(int requested, int w, int h) {
        return Mth.clamp(requested, 0, Math.min(w, h) / 2);
    }

    // ------------------------------------------------------------
    //  Circular-quadrant chamfer table
    //
    //  Each per-row chamfer inset is computed against a quarter-circle
    //  of radius `c` instead of a single 45° slope. The result is an
    //  octagon-style silhouette becoming a near-circular silhouette as
    //  `c` grows: at c = 14 the perimeter goes through ~10 distinct
    //  slopes per corner instead of one. Cached so repeated panels at
    //  the same radius don't recompute the table every frame.
    // ------------------------------------------------------------

    private static final int[][] INSET_CACHE = new int[64][];

    /**
     * Returns a length-{@code c} array where {@code [i]} is the chamfer
     * inset for top-row {@code i} (0 = topmost, {@code c-1} = first body
     * row). Inset = {@code c - sqrt(c² - (c - i - 0.5)²)} rounded.
     *
     * <p>Geometrically: pixel-quantized quarter-circle of radius {@code c}
     * centered at the inner corner. The +0.5 evaluates each row at its
     * vertical center to avoid the "flat top" artifact of a naïve
     * integer parameterisation.
     *
     * <p>Used only by {@link #topSheen} now — fill and outline use the
     * supersampled {@link #coverageTable} / {@link #strokeTable} for
     * antialiased corners.
     */
    private static int[] insetTable(int c) {
        if (c <= 0) return new int[0];
        if (c < INSET_CACHE.length && INSET_CACHE[c] != null) return INSET_CACHE[c];
        int[] table = new int[c];
        double cSq = (double) c * c;
        for (int i = 0; i < c; i++) {
            double dy = c - i - 0.5;
            double dx = Math.sqrt(Math.max(0.0, cSq - dy * dy));
            table[i] = Math.max(0, (int) Math.round(c - dx));
        }
        if (c < INSET_CACHE.length) INSET_CACHE[c] = table;
        return table;
    }

    // ============================================================
    //  Curve rasterization — direct scanline of the circle equation
    //
    //  Earlier iterations used an SS×SS-supersampled coverage table
    //  plus a pose.scale(1/SS) pass to gain sub-screen-pixel curve
    //  resolution. Both were dropped: the table consumed memory per
    //  radius, the pose tricks introduced fractional-pixel rounding
    //  artifacts, and the per-fill {@code GuiElementRenderState}
    //  submission cost in 1.21.11 made the whole approach a major
    //  perf hit.
    //
    //  The current renderer is equivalent to scanline-rasterizing a
    //  regular {@code 4 × c}-sided polygon inscribed in the corner
    //  arc: for each integer row {@code y} of the corner, compute the
    //  exact span where {@code (col - c)² + (row - c + 0.5)² ≤ c²}
    //  using one {@link Math#sqrt} call, then emit <b>one</b>
    //  {@code ctx.fill} that spans the whole row across both corners
    //  on that side. A radius-{@code c} corner emits exactly {@code c}
    //  fills (one per row) — the theoretical minimum for an integer
    //  pixel grid.
    //
    //  Total fill calls per panel: {@code 2c + 1}. Per outline:
    //  {@code 4 + 4c}. No tables, no caches, no pose ops, no
    //  per-pixel state submission. Identical visual result to the
    //  previous binary-thresholded coverage table at the same radius.
    // ============================================================

    /**
     * Outline stroke thickness in logical pixels. Halved from 4 to 2
     * per redesign iteration — the corners now span 72 logical pixels
     * (very smooth), so a 2-px outline still reads cleanly while
     * looking less heavy.
     */
    private static final int OUTLINE_THICKNESS = 2;

    /**
     * Top-edge highlight thickness for {@link #topHighlight}, in
     * logical pixels. One pixel reads as a thin silver rim catching
     * light at the top of dark surfaces — the only "material" cue in
     * the redesign now that gradient sheens have been removed.
     */
    private static final int HIGHLIGHT_THICKNESS = 1;

    /**
     * Returns the leftmost filled column of the corner row at index
     * {@code row} (0 = outermost row, {@code c-1} = innermost row
     * flush with the body), for a quarter-circle of radius {@code c}.
     *
     * <p>Solves {@code (col - c)² + (row + 0.5 - c)² = c²} for
     * {@code col}, choosing the smaller root and rounding to nearest
     * integer pixel. One {@link Math#sqrt} per call.
     */
    private static int cornerLeftEdge(int c, int row) {
        double dy = c - row - 0.5;
        double inside = (double) c * c - dy * dy;
        if (inside <= 0.0) return c;       // row entirely outside the disc
        double dx = Math.sqrt(inside);
        int edge = (int) Math.round(c - dx);
        if (edge < 0) edge = 0;
        if (edge > c) edge = c;
        return edge;
    }

    /**
     * Solid-color chamfered (rounded) rect. One {@code ctx.fill} per
     * arc row + one body fill — the minimum possible call count for
     * an integer-pixel scanline of a quarter-circle.
     */
    private static void drawRowAA(GuiGraphics ctx, int x, int yRow, int w, int c, int rowInCorner, int color) {
        double dy = c - rowInCorner - 0.5;
        double inside = (double) c * c - dy * dy;
        if (inside <= 0.0) return;
        double dx = Math.sqrt(inside);
        double exactX = c - dx;
        int edge = (int) exactX;
        float frac = (float) (exactX - edge);

        int xStart = x + edge;
        int xEnd = x + w - edge;
        if (xEnd - xStart <= 0) return;

        if (xEnd - xStart == 1) {
            ctx.fill(xStart, yRow, xEnd, yRow + 1, scaleAlpha(color, 1.0f - frac));
        } else {
            ctx.fill(xStart, yRow, xStart + 1, yRow + 1, scaleAlpha(color, 1.0f - frac));
            if (xEnd - 1 > xStart + 1) {
                ctx.fill(xStart + 1, yRow, xEnd - 1, yRow + 1, color);
            }
            ctx.fill(xEnd - 1, yRow, xEnd, yRow + 1, scaleAlpha(color, 1.0f - frac));
        }
    }

    private static int scaleAlpha(int argb, float scale) {
        float s = Math.max(0f, Math.min(1f, scale));
        int a = (argb >>> 24) & 0xFF;
        int newA = Math.round(a * s);
        return (newA << 24) | (argb & 0x00FFFFFF);
    }

    private static void chamferedFill(GuiGraphics ctx, int x, int y, int w, int h, int c, int color) {
        if (w <= 0 || h <= 0) return;
        if (c <= 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        // Top arc rows.
        for (int row = 0; row < c; row++) {
            drawRowAA(ctx, x, y + row, w, c, row, color);
        }
        // Body.
        if (h > 2 * c) {
            ctx.fill(x, y + c, x + w, y + h - c, color);
        }
        // Bottom arc rows — mirror of top.
        for (int row = 0; row < c; row++) {
            drawRowAA(ctx, x, y + h - c + row, w, c, c - 1 - row, color);
        }
    }

    /**
     * Chamfered rect with a vertical color gradient. Body is one
     * {@code fillGradient} call; arc rows each get a per-row lerped
     * color (the gradient between adjacent arc rows is imperceptible
     * across the typical 4–10 row arc).
     */
    private static void chamferedGradientFill(GuiGraphics ctx, int x, int y, int w, int h, int c,
                                              int topColor, int botColor) {
        if (w <= 0 || h <= 0) return;
        if (c <= 0) {
            ctx.fillGradient(x, y, x + w, y + h, topColor, botColor);
            return;
        }
        int hMinus1 = Math.max(1, h - 1);
        // Top arc rows.
        for (int row = 0; row < c; row++) {
            int rowColor = AuroraAnim.lerpArgb(topColor, botColor, row / (float) hMinus1);
            drawRowAA(ctx, x, y + row, w, c, row, rowColor);
        }
        // Body — one fillGradient between the arc-base colors.
        if (h > 2 * c) {
            int topMid = AuroraAnim.lerpArgb(topColor, botColor, c / (float) hMinus1);
            int botMid = AuroraAnim.lerpArgb(topColor, botColor, (h - c - 1) / (float) hMinus1);
            ctx.fillGradient(x, y + c, x + w, y + h - c, topMid, botMid);
        }
        // Bottom arc rows.
        for (int row = 0; row < c; row++) {
            int yAbs = h - c + row;
            int rowColor = AuroraAnim.lerpArgb(topColor, botColor, yAbs / (float) hMinus1);
            drawRowAA(ctx, x, y + yAbs, w, c, c - 1 - row, rowColor);
        }
    }

    /**
     * {@link #OUTLINE_THICKNESS}-px outline tracing the rounded-rect
     * perimeter. 4 straight strips along the sides + one fill per arc
     * row × 4 quadrants for the corners.
     */
    private static void chamferedOutline(GuiGraphics ctx, int x, int y, int w, int h, int c, int color) {
        if (w <= 0 || h <= 0) return;
        final int T = OUTLINE_THICKNESS;
        if (c <= 0) {
            // Hard rectangle: 4 axis-aligned T-px strips.
            ctx.fill(x,         y,         x + w, y + T,     color);
            ctx.fill(x,         y + h - T, x + w, y + h,     color);
            ctx.fill(x,         y + T,     x + T, y + h - T, color);
            ctx.fill(x + w - T, y + T,     x + w, y + h - T, color);
            return;
        }
        // Straight strips between the rounded corners.
        ctx.fill(x + c,     y,         x + w - c, y + T,     color); // top
        ctx.fill(x + c,     y + h - T, x + w - c, y + h,     color); // bottom
        ctx.fill(x,         y + c,     x + T,     y + h - c, color); // left
        ctx.fill(x + w - T, y + c,     x + w,     y + h - c, color); // right

        // Corner stroke — for each row, the band between the outer arc
        // (radius c) and the inner arc (radius c−T) is one contiguous
        // span. Emit one fill per row per quadrant (4 fills/row).
        // Total corner fills: 4c. No tables, just two sqrts per row.
        int innerR = c - T;
        long innerRSq = (long) innerR * innerR;
        for (int row = 0; row < c; row++) {
            // Exact (sub-pixel) outer arc edge for this row. Solves
            // (col - c)² + (row + 0.5 - c)² = c² for col.
            double dy = c - row - 0.5;
            double outerInside = (double) c * c - dy * dy;
            if (outerInside <= 0.0) continue;
            double outerDx = Math.sqrt(outerInside);
            double exactOuterX = c - outerDx;
            int outerEdge = (int) Math.floor(exactOuterX);
            if (outerEdge >= c) continue;

            // Exact inner arc edge. If the row lies fully outside the
            // inner disc, the band extends all the way to the body
            // (innerEdge = c, no fractional inner edge).
            double exactInnerX;
            int innerEdge;
            if (innerR <= 0) {
                exactInnerX = c;
                innerEdge = c;
            } else {
                double innerInside = innerRSq - dy * dy;
                if (innerInside <= 0.0) {
                    exactInnerX = c;
                    innerEdge = c;
                } else {
                    double innerDx = Math.sqrt(innerInside);
                    exactInnerX = c - innerDx;
                    innerEdge = (int) Math.floor(exactInnerX);
                    if (innerEdge < outerEdge) {
                        innerEdge = outerEdge;
                        exactInnerX = exactOuterX;
                    }
                    if (innerEdge > c) innerEdge = c;
                }
            }
            if (innerEdge <= outerEdge) continue;

            // Left band: [x + exactOuterX, x + exactInnerX]
            // Right band (mirrored): [x + w - exactInnerX, x + w - exactOuterX]
            double leftStart  = x + exactOuterX;
            double leftEnd    = x + exactInnerX;
            double rightStart = x + w - exactInnerX;
            double rightEnd   = x + w - exactOuterX;

            int topY    = y + row;
            int bottomY = y + h - 1 - row;
            // Top-left, top-right, bottom-left, bottom-right (AA bands).
            aaCornerBand(ctx, leftStart,  topY,    leftEnd,  color);
            aaCornerBand(ctx, rightStart, topY,    rightEnd, color);
            aaCornerBand(ctx, leftStart,  bottomY, leftEnd,  color);
            aaCornerBand(ctx, rightStart, bottomY, rightEnd, color);
        }
    }

    /**
     * Antialiased 1-px-tall horizontal band for one corner row of the
     * outline. The band spans the fractional interval
     * [{@code leftAbs}, {@code rightAbs}); the boundary pixel at each
     * end gets its alpha scaled by the fractional coverage so both the
     * outer and inner arc edges fade smoothly into the surface instead
     * of reading as a jagged integer staircase. This is the per-pixel
     * matching that makes small rounded controls (search bars, chips)
     * look crisp at any GUI scale.
     */
    private static void aaCornerBand(GuiGraphics ctx, double leftAbs, int rowY, double rightAbs, int color) {
        int leftInt  = (int) Math.floor(leftAbs);
        float leftFrac  = (float) (leftAbs - leftInt);
        int rightInt = (int) Math.floor(rightAbs);
        float rightFrac = (float) (rightAbs - rightInt);

        if (rightInt <= leftInt) {
            // Entire band falls within a single pixel — coverage is the
            // fractional overlap.
            float cov = Math.max(0f, Math.min(1f, (float) (rightAbs - leftAbs)));
            if (cov > 0.001f) {
                ctx.fill(leftInt, rowY, leftInt + 1, rowY + 1, scaleAlpha(color, cov));
            }
            return;
        }

        // Left boundary pixel — covered from leftAbs to leftInt+1.
        if (leftFrac > 0.001f) {
            ctx.fill(leftInt, rowY, leftInt + 1, rowY + 1, scaleAlpha(color, 1f - leftFrac));
        }
        // Fully-covered interior pixels.
        if (rightInt > leftInt + 1) {
            ctx.fill(leftInt + 1, rowY, rightInt, rowY + 1, color);
        }
        // Right boundary pixel — covered from rightInt to rightAbs.
        if (rightFrac > 0.001f) {
            ctx.fill(rightInt, rowY, rightInt + 1, rowY + 1, scaleAlpha(color, rightFrac));
        }
    }
}
