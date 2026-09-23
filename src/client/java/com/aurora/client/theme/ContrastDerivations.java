package com.aurora.client.theme;

import java.util.List;

/**
 * Phase D-1's resolve-time contrast-derivation foundation (2026-09-23) — the
 * semantic layer that turns DESIGN_LANGUAGE §3.6's normative thresholds into
 * concrete colors. Everything here is a <b>pure function</b> of its inputs:
 * no state, no framebuffer reads, no world observation, no per-frame
 * evaluation. Values are computed once per theme resolution (see
 * {@link #fromColors}, carried by {@link ResolvedTheme}) and never between
 * animation frames — animated intermediate states are covered by designing
 * against family endpoints (the worst case), which the tests verify.
 *
 * <p><b>Ownership</b> (§3.6): theme resolution owns semantic contrast
 * derivation; components consume semantic outputs and never solve contrast
 * locally. <b>Stored-preference invariant</b>: nothing in this class can
 * mutate the stored accent/theme — every method takes colors as values and
 * returns derived values. The stored accent is an input, never an adaptation
 * target.
 *
 * <p><b>Mode-deterministic backing assumption</b> (the D-0/D-1 formalization):
 * a translucent surface over the live world has no deterministic backing, so
 * derivations that must guarantee a ratio assume the world may be <i>any</i>
 * luminance and evaluate both neutral extremes {@link #WORST_BASE_BLACK} and
 * {@link #WORST_BASE_WHITE}, designing against whichever binds. This holds
 * in both DARK and LIGHT mode — the world behind a glass screen is not
 * mode-constrained — and it is conservative for every world pixel whenever
 * the foreground's luminance lies outside the composited backing band, a
 * property the D-1 tests verify across the stress catalog (when it does not
 * hold, the derivations report it via {@code coversAllWorlds=false} rather
 * than pretending a guarantee). Mode genuinely enters only where mode-locked
 * tokens do (e.g. the focus ring's adjacent surface).
 *
 * <p>D-1 established the foundation. Phase D-2 now consumes the stained
 * result through {@link AdaptiveOnAccentTreatment} for the production
 * text-bearing accent family; later Phase D outputs retain their own
 * semantic migration boundaries.
 */
public final class ContrastDerivations {

    // ------------------------------------------------------------------
    //  Normative thresholds (DESIGN_LANGUAGE §3.6)
    // ------------------------------------------------------------------

    /** Essential normal text on deterministic backings. */
    public static final double ESSENTIAL_TEXT_RATIO = 4.5;
    /** Essential non-text state indicators (focus, glyphs, selected-state boundaries). */
    public static final double NON_TEXT_INDICATOR_RATIO = 3.0;
    /** Project-specific floor for supplemental muted text (never the sole carrier of essential info). */
    public static final double SUPPLEMENTAL_MUTED_RATIO = 2.2;

    /**
     * Hard maximum lightness adjustment a backing adaptation may apply (the
     * D-0 design bound). The accent's identity — hue and saturation — is
     * never traded away to make a contrast test pass; if the bound cannot
     * satisfy the target, the derivation exposes failure instead.
     */
    public static final float MAX_BACKING_LIGHTNESS_SHIFT = 0.08f;

    /** The stained-tint visibility floor (mirrors {@code ThemeManager.stainedTint()}'s 140). */
    public static final int STAINED_VISIBILITY_FLOOR_ALPHA = 140;

    /**
     * The mode-deterministic worst-case neutral bases (see the class javadoc:
     * the world may be any luminance, so both extremes are evaluated and the
     * binding one designs the derivation).
     */
    public static final int WORST_BASE_BLACK = 0xFF000000;
    public static final int WORST_BASE_WHITE = 0xFFFFFFFF;
    public static final List<Integer> WORST_BASES = List.of(WORST_BASE_BLACK, WORST_BASE_WHITE);

    // ==================================================================
    //  Readable foreground (the generalized pickOnColor)
    // ==================================================================

    /**
     * The result of a readable-foreground decision: the binary near-light /
     * near-dark choice (D-0 keeps the binary mechanism deliberately — a
     * continuous derivation would break identity/stability for at most ~0.2
     * of ratio) plus everything a caller needs to test thresholds against
     * the <i>actual deterministic rendered backing</i>: both candidates,
     * both ratios, whether the choice was an exact tie, and whether the
     * better candidate clears a given threshold.
     *
     * @param chosen         the higher-contrast candidate (exact ties prefer light)
     * @param lightCandidate near-white, tinted toward the accent hue
     * @param darkCandidate  near-black, tinted toward the accent hue
     * @param lightContrast  contrastRatio(lightCandidate, backing)
     * @param darkContrast   contrastRatio(darkCandidate, backing)
     * @param tie            both ratios were exactly equal
     */
    public record ForegroundChoice(int chosen, int lightCandidate, int darkCandidate,
                                   double lightContrast, double darkContrast, boolean tie) {
        public double bestContrast() {
            return Math.max(lightContrast, darkContrast);
        }
        /** Whether the better candidate clears {@code threshold} against this backing. */
        public boolean meets(double threshold) {
            return bestContrast() >= threshold;
        }
    }

    /**
     * Choose the readable foreground for an arbitrary deterministic backing
     * — the backing the pixels actually render over (opaque token, or an
     * alpha-composited result), never a blindly-assumed raw accent. The
     * candidates are the same two finite semantic colors
     * {@link PaletteEngine#pickOnColor} uses, so this is a strict
     * generalization, not a second policy.
     */
    public static ForegroundChoice chooseForeground(int backingArgb, float hue) {
        int light = PaletteEngine.hsl(hue, 0.05f, 0.97f);
        int dark = PaletteEngine.hsl(hue, 0.10f, 0.08f);
        double lr = PaletteEngine.contrastRatio(backingArgb, light);
        double dr = PaletteEngine.contrastRatio(backingArgb, dark);
        return new ForegroundChoice(lr >= dr ? light : dark, light, dark, lr, dr, lr == dr);
    }

    // ==================================================================
    //  Readable stained backing (D-0's ON_ACCENT strategy, foundation)
    // ==================================================================

    /**
     * A stained-backing adaptation result.
     *
     * @param stainArgb       the adapted stain base (the accent — family
     *                        member 0 — after the lightness shift; identical
     *                        to the accent when {@code lightnessShift == 0})
     * @param requiredAlpha   the minimum stain alpha (0..1) at which the
     *                        policy is satisfied under the worst case
     * @param achievedRatio   the worst composited contrast ratio over every
     *                        family member × every worst-case base at
     *                        {@code (family, requiredAlpha)}
     * @param lightnessShift  the signed HSL-lightness shift applied to the
     *                        whole stain family; magnitude is bounded by
     *                        {@link #MAX_BACKING_LIGHTNESS_SHIFT}
     * @param guaranteed      every family member over every worst-case base
     *                        clears the target at {@code requiredAlpha}
     * @param coversAllWorlds no world luminance exists that crosses the
     *                        foreground inside the composited backing band at
     *                        {@code requiredAlpha} (see the class javadoc);
     *                        when false a world pixel can approach ratio 1:1
     *                        regardless of the endpoint ratios
     */
    public record StainedBacking(int stainArgb, double requiredAlpha, double achievedRatio,
                                 float lightnessShift, boolean guaranteed, boolean coversAllWorlds) {
        /** The D-2 contract: the derivation succeeded under its bounded policy. */
        public boolean sufficient() {
            return guaranteed && coversAllWorlds;
        }
    }

    /**
     * Single-stain convenience overload (the family is just the accent).
     */
    public static StainedBacking readableStainedBacking(int accentArgb, int onAccentArgb,
                                                        double minStainAlpha, double targetRatio) {
        return readableStainedBacking(new int[]{accentArgb}, onAccentArgb, minStainAlpha, targetRatio);
    }

    /**
     * Derive the readable backing for a TEXT-BEARING stained surface — the
     * D-0-recommended ON_ACCENT strategy, evaluated against the worst case
     * so that every animated intermediate state passes too (§3.6/§33:
     * nothing is derived per frame).
     *
     * <p>{@code stainFamily} is every opaque stain color the surface may
     * render with — member 0 MUST be the accent itself (hover/pressed/
     * gradient-top variants follow). The policy, in identity-preserving
     * preference order:
     * <ol>
     *   <li>raise the stain alpha from {@code minStainAlpha} toward opaque
     *       until the worst composited ratio clears {@code targetRatio};</li>
     *   <li>if even a fully opaque family cannot, shift the family's HSL
     *       <b>lightness only</b> — hue and saturation untouched — away from
     *       the foreground's luminance side, at most
     *       {@link #MAX_BACKING_LIGHTNESS_SHIFT}, minimally, retrying the
     *       alpha search at each step;</li>
     *   <li>if the bounded policy cannot satisfy the target, return the
     *       best effort with {@code guaranteed == false} — exposing the
     *       failure so a later layer (D-2/D-4) can add a scrim or other
     *       policy. The bound is never violated to make a test green.</li>
     * </ol>
     *
     * <p>The worst case is every family member composited over both neutral
     * extremes (mode-independent — the world is not mode-constrained; see
     * the class javadoc). Deterministic: same inputs, same record, every
     * time; no state is retained.
     */
    public static StainedBacking readableStainedBacking(int[] stainFamily, int onAccentArgb,
                                                        double minStainAlpha, double targetRatio) {
        int[] family = stainFamily.clone();
        double floor = Math.max(0d, Math.min(1d, minStainAlpha));
        int minA = (int) Math.ceil(floor * 255d);
        double fgLum = PaletteEngine.relativeLuminance(onAccentArgb);

        // Shift steps: 0 first (identity), then minimal steps away from the
        // foreground's luminance side, never past the hard bound.
        for (int step = 0; step <= SHIFT_STEPS; step++) {
            float dl = Math.min(step * SHIFT_STEP, MAX_BACKING_LIGHTNESS_SHIFT)
                    * (fgLum >= 0.5 ? -1f : 1f);
            int[] shifted = shiftFamilyLightness(family, dl);
            for (int a = minA; a <= 255; a++) {
                if (worstFamilyRatio(shifted, onAccentArgb, a) >= targetRatio
                        && familyCoversAllWorlds(shifted, onAccentArgb, a, fgLum)) {
                    return new StainedBacking(shifted[0], a / 255d,
                            worstFamilyRatio(shifted, onAccentArgb, a), dl, true, true);
                }
            }
        }

        // Bounded policy exhausted: report the best effort honestly
        // (maximum permitted shift, fully opaque, actual worst ratio).
        float dl = MAX_BACKING_LIGHTNESS_SHIFT * (fgLum >= 0.5 ? -1f : 1f);
        int[] shifted = shiftFamilyLightness(family, dl);
        double worst = worstFamilyRatio(shifted, onAccentArgb, 255);
        return new StainedBacking(shifted[0], 1d, worst, dl, false,
                familyCoversAllWorlds(shifted, onAccentArgb, 255, fgLum));
    }

    // HSL lightness derived from 8-bit RGB changes in half-channel steps at
    // its finest useful resolution. Searching 1/510 avoids skipping a
    // smaller representable solution while remaining tiny resolve-time work.
    private static final float SHIFT_STEP = 1f / 510f;
    private static final int SHIFT_STEPS = (int) Math.ceil(MAX_BACKING_LIGHTNESS_SHIFT / SHIFT_STEP);

    /** Worst composited ratio over family × worst-case bases at stain alpha {@code a} (0..255). */
    private static double worstFamilyRatio(int[] family, int fg, int a) {
        double worst = Double.MAX_VALUE;
        for (int member : family) {
            int stain = (a << 24) | (member & 0x00FFFFFF);
            for (int base : WORST_BASES) {
                worst = Math.min(worst,
                        PaletteEngine.contrastRatio(fg, PaletteEngine.composite(stain, base)));
            }
        }
        return worst;
    }

    /**
     * Whether the foreground's luminance lies strictly outside every
     * member's composited backing band at this alpha — i.e. no world pixel
     * can land on the foreground's luminance and collapse the ratio. When
     * the fg sits inside a band, contrast along the base-luminance axis
     * dips to 1:1 <i>between</i> the evaluated extremes, and endpoint ratios
     * alone would overclaim.
     */
    private static boolean familyCoversAllWorlds(int[] family, int fg, int a, double fgLum) {
        for (int member : family) {
            int stain = (a << 24) | (member & 0x00FFFFFF);
            double lo = PaletteEngine.relativeLuminance(PaletteEngine.composite(stain, WORST_BASE_BLACK));
            double hi = PaletteEngine.relativeLuminance(PaletteEngine.composite(stain, WORST_BASE_WHITE));
            double bandLo = Math.min(lo, hi), bandHi = Math.max(lo, hi);
            // Inclusive: a foreground ON the band's edge has ratio ~1:1
            // against that world too — touching counts as crossing.
            if (fgLum > bandLo - LUM_EPSILON && fgLum < bandHi + LUM_EPSILON) {
                return false;
            }
        }
        return true;
    }

    private static final double LUM_EPSILON = 1e-4;

    /**
     * Shift every member's HSL lightness by {@code dl}, preserving hue and
     * saturation (up to 8-bit quantization on the round trip). Members are
     * treated as a unit so hover/pressed/gradient variants of a shifted
     * accent stay a coherent family.
     */
    private static int[] shiftFamilyLightness(int[] family, float dl) {
        if (dl == 0f) {
            return family.clone();
        }
        int[] out = new int[family.length];
        for (int i = 0; i < family.length; i++) {
            float[] hsl = PaletteEngine.rgbToHsl(family[i] & 0x00FFFFFF);
            out[i] = PaletteEngine.hsl(hsl[0], hsl[1],
                    Math.max(0f, Math.min(1f, hsl[2] + dl)));
        }
        return out;
    }

    // ==================================================================
    //  Focus ring derivation
    // ==================================================================

    /**
     * A derived focus-ring color.
     *
     * @param colorArgb      the color to paint (for the translucent branch,
     *                       the 60%-alpha accent — painting it over the
     *                       adjacent surface yields the verified composite)
     * @param achievedRatio  the rendered contrast against {@code adjacent}
     * @param accentIdentity the result is the accent itself or an
     *                       alpha-only variant of it (hue/sat untouched)
     * @param met            the target was met under the bounded policy
     * @param policy         which branch won — {@code accent},
     *                       {@code accent@0x99}, {@code tinted-light},
     *                       {@code tinted-dark}, or {@code unmet}
     */
    public record FocusRing(int colorArgb, double achievedRatio, boolean accentIdentity,
                            boolean met, String policy) {}

    /**
     * Derive a focus-ring color for one deterministic adjacent backing.
     *
     * <p><b>Scope (the D-1 §23 limitation, do not overclaim):</b> the result
     * is guaranteed only against the {@code adjacentArgb} passed in — the
     * derivation the resolved theme carries uses the flat control surface
     * composited over the worst-case bases. A ring drawn against a
     * same-accent STAINED backing (accent-on-accent, where every candidate
     * shares the backing's hue) is NOT covered and needs the contextual
     * treatment planned for D-3/D-6; the D-1 tests pin that failure
     * exists so the limitation stays honest.
     *
     * <p>Candidate order (deterministic, identity first): the opaque accent;
     * the production 60%-alpha accent hairline evaluated as rendered; then
     * the hue-tinted near-light/near-dark candidates (accent-related through
     * the accent's hue). Failure is exposed, never painted over.
     */
    public static FocusRing focusRing(int accentArgb, int adjacentArgb, double targetRatio) {
        int accent = 0xFF000000 | accentArgb;
        double ra = PaletteEngine.contrastRatio(accent, adjacentArgb);
        if (ra >= targetRatio) {
            return new FocusRing(accent, ra, true, true, "accent");
        }
        int hairline = (0x99 << 24) | (accent & 0x00FFFFFF);
        double rb = PaletteEngine.contrastRatio(
                PaletteEngine.composite(hairline, adjacentArgb), adjacentArgb);
        if (rb >= targetRatio) {
            return new FocusRing(hairline, rb, true, true, "accent@0x99");
        }
        float hue = PaletteEngine.rgbToHsl(accent & 0x00FFFFFF)[0];
        ForegroundChoice tinted = chooseForeground(adjacentArgb, hue);
        boolean lightWon = tinted.chosen() == tinted.lightCandidate();
        if (tinted.bestContrast() >= targetRatio) {
            return new FocusRing(tinted.chosen(), tinted.bestContrast(), false, true,
                    lightWon ? "tinted-light" : "tinted-dark");
        }
        // Bounded policy cannot meet the target: expose the best candidate.
        if (ra >= rb && ra >= tinted.bestContrast()) {
            return new FocusRing(accent, ra, true, false, "unmet:accent");
        }
        if (rb >= tinted.bestContrast()) {
            return new FocusRing(hairline, rb, true, false, "unmet:accent@0x99");
        }
        return new FocusRing(tinted.chosen(), tinted.bestContrast(), false, false,
                lightWon ? "unmet:tinted-light" : "unmet:tinted-dark");
    }

    /**
     * Derive one stable ring for several deterministic adjacent backings.
     * Candidate quality is the minimum rendered ratio across the set, so a
     * result marked met really meets the target everywhere it claims to.
     * This is used by the resolve-time snapshot for the two controlled
     * extreme bases; callers with one known backing use the scalar overload.
     */
    public static FocusRing focusRing(int accentArgb, int[] adjacentArgb, double targetRatio) {
        if (adjacentArgb.length == 0) {
            throw new IllegalArgumentException("at least one adjacent backing is required");
        }
        int accent = 0xFF000000 | accentArgb;
        int hairline = (0x99 << 24) | (accent & 0x00FFFFFF);
        float hue = PaletteEngine.rgbToHsl(accent & 0x00FFFFFF)[0];
        ForegroundChoice pair = chooseForeground(adjacentArgb[0], hue);
        int light = pair.lightCandidate();
        int dark = pair.darkCandidate();

        double accentRatio = worstRenderedRatio(accent, adjacentArgb);
        if (accentRatio >= targetRatio) {
            return new FocusRing(accent, accentRatio, true, true, "accent");
        }
        double hairlineRatio = worstRenderedRatio(hairline, adjacentArgb);
        if (hairlineRatio >= targetRatio) {
            return new FocusRing(hairline, hairlineRatio, true, true, "accent@0x99");
        }
        double lightRatio = worstRenderedRatio(light, adjacentArgb);
        double darkRatio = worstRenderedRatio(dark, adjacentArgb);
        int tinted = lightRatio >= darkRatio ? light : dark;
        double tintedRatio = Math.max(lightRatio, darkRatio);
        String tintedPolicy = lightRatio >= darkRatio ? "tinted-light" : "tinted-dark";
        if (tintedRatio >= targetRatio) {
            return new FocusRing(tinted, tintedRatio, false, true, tintedPolicy);
        }
        if (accentRatio >= hairlineRatio && accentRatio >= tintedRatio) {
            return new FocusRing(accent, accentRatio, true, false, "unmet:accent");
        }
        if (hairlineRatio >= tintedRatio) {
            return new FocusRing(hairline, hairlineRatio, true, false, "unmet:accent@0x99");
        }
        return new FocusRing(tinted, tintedRatio, false, false, "unmet:" + tintedPolicy);
    }

    private static double worstRenderedRatio(int ringArgb, int[] adjacents) {
        double worst = Double.MAX_VALUE;
        for (int adjacent : adjacents) {
            int rendered = PaletteEngine.composite(ringArgb, adjacent);
            worst = Math.min(worst, PaletteEngine.contrastRatio(rendered, adjacent));
        }
        return worst;
    }

    // ==================================================================
    //  Selection separation
    // ==================================================================

    /**
     * The deterministic separation between a selected backing and its
     * container — plain opaque comparison. The composited overload answers
     * the real D-0 question ("stain/wash vs window/sidebar") for
     * translucent layers over a shared base.
     */
    public static double separationRatio(int selectedArgb, int containerArgb) {
        return PaletteEngine.contrastRatio(selectedArgb, containerArgb);
    }

    /**
     * Separation with both layers straight-alpha composited over a common
     * deterministic base first (the §3.6 composited-pair rule). For a
     * guarantee over arbitrary worlds, evaluate over
     * {@link #WORST_BASE_BLACK} and {@link #WORST_BASE_WHITE} and take the
     * minimum — {@link #fromColors} does exactly that for the carried
     * result.
     */
    public static double separationRatio(int selectedArgb, int containerArgb, int baseArgb) {
        return PaletteEngine.contrastRatio(
                PaletteEngine.composite(selectedArgb, baseArgb),
                PaletteEngine.composite(containerArgb, baseArgb));
    }

    // ==================================================================
    //  Essential-readability plate (the D-4 contract, math only)
    // ==================================================================

    /**
     * The minimum plate alpha (0..1, in 1/255 steps) such that
     * {@code fgArgb} over {@code plateRgb@alpha} over {@code worstBaseArgb}
     * meets {@code targetRatio} — the deterministic minimum-effective-backing
     * rule §3.4 deferred to Phase D-4. Returns {@code -1} when no alpha can
     * (e.g. the foreground cannot separate from the plate family at all).
     * A translucent foreground composites over the plated result, so the
     * muted tier works too. D-1 defines the math only; no production row
     * paints a plate yet.
     */
    public static double minimumReadableBacking(int fgArgb, int plateRgb, int worstBaseArgb,
                                                double targetRatio) {
        for (int a = 0; a <= 255; a++) {
            int plate = (a << 24) | (plateRgb & 0x00FFFFFF);
            int backing = PaletteEngine.composite(plate, worstBaseArgb);
            int visibleFg = PaletteEngine.composite(fgArgb, backing);
            if (PaletteEngine.contrastRatio(visibleFg, backing) >= targetRatio) {
                return a / 255d;
            }
        }
        return -1d;
    }

    // ==================================================================
    //  Resolve-time snapshot (carried by ResolvedTheme)
    // ==================================================================

    private final FocusRing focusRing;
    private final StainedBacking stainedTextBacking;
    private final double selectionSeparationRatio;

    private ContrastDerivations(FocusRing focusRing, StainedBacking stainedTextBacking,
                                double selectionSeparationRatio) {
        this.focusRing = focusRing;
        this.stainedTextBacking = stainedTextBacking;
        this.selectionSeparationRatio = selectionSeparationRatio;
    }

    /**
     * Compute every carried derivation from one resolved token array — the
     * single construction point used by {@link ResolvedTheme} for BOTH
     * resolution paths (derived and factory palette), so the two can never
     * disagree about policy. Pure: the array is only read.
     *
     * <ul>
     *   <li>{@link #stainedTextBacking()}: the text-bearing stained surface
     *       policy over the real accent family
     *       {ACCENT, ACCENT_HOVER, ACCENT_PRESSED, ACCENT_GRAD_TOP,
     *       ACCENT_GRAD_TOP_HOVER} at the stained visibility floor
     *       max(window alpha, 140), targeting essential text.</li>
     *   <li>{@link #focusRing()}: the focus ring against the flat control
     *       surface (SURFACE rgb at the window alpha) composited over both
     *       worst-case bases — one stable color whose recorded ratio is the
     *       minimum across both adjacencies is carried.</li>
     *   <li>{@link #selectionSeparationRatio()}: stained tint vs window
     *       fill, both composited, minimized over the worst-case bases.</li>
     * </ul>
     */
    static ContrastDerivations fromColors(int[] colors) {
        int accent = colors[ThemeToken.ACCENT.ordinal()];
        int onAccent = colors[ThemeToken.ON_ACCENT.ordinal()];
        int windowAlpha = (colors[ThemeToken.WINDOW_FILL.ordinal()] >>> 24) & 0xFF;

        int[] family = {
                colors[ThemeToken.ACCENT.ordinal()],
                colors[ThemeToken.ACCENT_HOVER.ordinal()],
                colors[ThemeToken.ACCENT_PRESSED.ordinal()],
                colors[ThemeToken.ACCENT_GRAD_TOP.ordinal()],
                colors[ThemeToken.ACCENT_GRAD_TOP_HOVER.ordinal()],
        };
        double stainFloor = Math.max(windowAlpha, STAINED_VISIBILITY_FLOOR_ALPHA) / 255d;
        StainedBacking stained = readableStainedBacking(
                family, onAccent, stainFloor, ESSENTIAL_TEXT_RATIO);

        // Focus ring: adjacent = flat control surface (SURFACE rgb at the
        // window alpha) over each worst-case base; carry one stable color
        // whose recorded ratio is the minimum across both adjacencies.
        int controlSurface = (windowAlpha << 24) | (colors[ThemeToken.SURFACE.ordinal()] & 0x00FFFFFF);
        int[] focusAdjacents = new int[WORST_BASES.size()];
        for (int i = 0; i < WORST_BASES.size(); i++) {
            focusAdjacents[i] = PaletteEngine.composite(controlSurface, WORST_BASES.get(i));
        }
        FocusRing ring = focusRing(accent, focusAdjacents, NON_TEXT_INDICATOR_RATIO);

        int stainTint = ((int) Math.round(stainFloor * 255d) << 24) | (accent & 0x00FFFFFF);
        int windowFill = colors[ThemeToken.WINDOW_FILL.ordinal()];
        double separation = Double.MAX_VALUE;
        for (int base : WORST_BASES) {
            separation = Math.min(separation, separationRatio(stainTint, windowFill, base));
        }

        return new ContrastDerivations(ring, stained, separation);
    }

    /** The derived focus ring against its documented adjacent backing (see {@link #focusRing}). */
    public FocusRing focusRing() {
        return focusRing;
    }

    /** The text-bearing stained-surface derivation for the current accent family and opacity. */
    public StainedBacking stainedTextBacking() {
        return stainedTextBacking;
    }

    /** Worst-case separation between the stained tint and the window fill (composited). */
    public double selectionSeparationRatio() {
        return selectionSeparationRatio;
    }
}
