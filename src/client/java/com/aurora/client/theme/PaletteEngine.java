package com.aurora.client.theme;

/**
 * Stage 2's pure color-derivation engine: <b>accent + mode in, full token
 * set out</b>.
 *
 * <p>The whole point of COSMIC-style theming is that the user picks a
 * single accent color and everything else is computed. This class is that
 * computation, kept as a self-contained pure function with <b>no
 * Minecraft/Fabric dependencies</b> so it can be unit-tested (and eyeballed)
 * in isolation with plain {@code javac}.
 *
 * <p>Guiding rules:
 * <ul>
 *   <li><b>Backgrounds never inherit the accent's lightness</b> — only its
 *       hue plus a small saturation. Lightness is locked to the mode
 *       (dark surfaces ~6-15%, light surfaces ~90-97%). That is what keeps
 *       a near-white or near-black accent from washing out the whole UI.</li>
 *   <li><b>Text is always contrast-checked</b> against the surface it sits
 *       on via relative luminance ({@link #pickOnColor}), so no accent can
 *       ever produce illegible text.</li>
 *   <li>The math is deliberately simple HSL — no OKLCH — which is
 *       sufficient and cheap enough to run once per theme change.</li>
 * </ul>
 *
 * <p>{@link #derive} never throws for any {@code int} input: alpha is
 * normalized to opaque and a fully-transparent (sentinel/malformed) value
 * falls back to the default preset.
 */
public final class PaletteEngine {

    private PaletteEngine() {}

    /** Near-white used for hover/gradient lifting. */
    private static final int WHITE = 0xFFFFFFFF;
    /** Near-black used for pressed-state deepening. */
    private static final int BLACK = 0xFF000000;

    /**
     * Normalize an accent value to an opaque ARGB, guarding against
     * malformed config input. A fully-transparent accent (alpha 0 — the
     * typical "no value"/sentinel case from a hand-edited config) falls back
     * to the factory default so derivation always has a real color. Any
     * other value keeps its RGB and is forced opaque.
     */
    public static int normalizeAccent(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha == 0) {
            return ThemeDefinition.DEFAULT_ACCENT;
        }
        return 0xFF000000 | (argb & 0x00FFFFFF);
    }

    /**
     * Derive the complete token palette for an accent + mode.
     *
     * @param accentArgb the identity accent (RGB; alpha normalized away)
     * @param mode       the brightness mode ({@code null} = dark)
     * @return an array indexed by {@link ThemeToken#ordinal()}
     */
    public static int[] derive(int accentArgb, ThemeMode mode) {
        int accent = normalizeAccent(accentArgb);
        boolean dark = mode != ThemeMode.LIGHT;

        float[] hsl = rgbToHsl(accent & 0x00FFFFFF);
        float hue = hsl[0];
        float aSat = hsl[1];

        int[] c = new int[ThemeToken.values().length];

        // ---- Accent family (raw accent + simple light/dark shifts) ----
        c[ThemeToken.ACCENT.ordinal()]            = accent;
        c[ThemeToken.ACCENT_HOVER.ordinal()]      = blendColor(accent, WHITE, 0.15f);
        c[ThemeToken.ACCENT_PRESSED.ordinal()]    = blendColor(accent, BLACK, 0.28f);
        c[ThemeToken.ACCENT_GRAD_TOP.ordinal()]   = blendColor(accent, WHITE, 0.15f);
        c[ThemeToken.ACCENT_GRAD_TOP_HOVER.ordinal()] = blendColor(accent, WHITE, 0.28f);
        c[ThemeToken.ACCENT_GRAD_BOT.ordinal()]   = accent;

        // ---- Neutral surfaces: accent hue + subtle saturation, mode-locked lightness ----
        // Saturation floor is ZERO: a white/gray/black accent (aSat == 0) has
        // a meaningless hue (rgbToHsl returns 0° = red), so any forced floor
        // would tint every surface uniformly red/pink — a look disconnected
        // from the chosen accent (the audit's root-cause class). Neutral
        // accents now stay neutral; saturated accents keep a faint tint.
        float neutSat = clamp(aSat * 0.22f, 0f, 0.22f);
        if (dark) {
            c[ThemeToken.BACKGROUND.ordinal()]      = hsl(hue, neutSat, 0.06f);
            c[ThemeToken.SURFACE.ordinal()]         = hsl(hue, neutSat, 0.11f);
            c[ThemeToken.SURFACE_VARIANT.ordinal()] = hsl(hue, neutSat, 0.15f);
            c[ThemeToken.SURFACE_INSET.ordinal()]   = hsl(hue, neutSat, 0.08f);
            c[ThemeToken.WINDOW_FILL.ordinal()]     = hsl(hue, neutSat, 0.07f);
        } else {
            c[ThemeToken.BACKGROUND.ordinal()]      = hsl(hue, neutSat, 0.965f);
            c[ThemeToken.SURFACE.ordinal()]         = hsl(hue, neutSat, 0.94f);
            c[ThemeToken.SURFACE_VARIANT.ordinal()] = hsl(hue, neutSat, 0.90f);
            c[ThemeToken.SURFACE_INSET.ordinal()]   = hsl(hue, neutSat, 0.92f);
            c[ThemeToken.WINDOW_FILL.ordinal()]     = hsl(hue, neutSat, 0.955f);
        }

        // ---- Text / on-X: always contrast-checked against their paired surface ----
        int onBackground = pickOnColor(c[ThemeToken.BACKGROUND.ordinal()], hue);
        c[ThemeToken.ON_BACKGROUND.ordinal()]          = onBackground;
        c[ThemeToken.ON_BACKGROUND_SECONDARY.ordinal()] = (0x99 << 24) | (onBackground & 0x00FFFFFF);
        c[ThemeToken.ON_BACKGROUND_MUTED.ordinal()]     = (0x4D << 24) | (onBackground & 0x00FFFFFF);
        c[ThemeToken.ON_BACKGROUND_FAINT.ordinal()]     = (0x2E << 24) | (onBackground & 0x00FFFFFF);
        c[ThemeToken.ON_SURFACE.ordinal()]              = pickOnColor(c[ThemeToken.SURFACE.ordinal()], hue);
        c[ThemeToken.ON_ACCENT.ordinal()]               = pickOnColor(accent, hue);

        // ---- Neutral chrome: accent-tinted grays (borders, outlines) ----
        // "Tinted GRAYS" is the contract — chrome must never out-saturate the
        // accent itself. The old floor (0.2) forced a pastel hue even for
        // fully desaturated accents (hue falls back to 0° = red), which read
        // as a disconnected pink/magenta border style on every window/tile
        // outline; the floor is now zero and the scale halved so borders
        // follow the accent's own saturation. Lightness 0.55 keeps chrome
        // clearly darker than text on dark surfaces.
        float chromeSat = clamp(aSat * 0.35f, 0f, 0.18f);
        int secondary = hsl(hue, chromeSat, dark ? 0.55f : 0.45f);
        c[ThemeToken.SECONDARY_ACCENT.ordinal()]      = secondary;
        c[ThemeToken.BORDER_ACCENT_HOVER.ordinal()]   = hsl(hue, chromeSat, dark ? 0.65f : 0.38f);

        float borderSat = clamp(aSat * 0.15f, 0f, 0.15f);
        c[ThemeToken.BORDER.ordinal()]                = hsl(hue, borderSat, dark ? 0.24f : 0.82f);
        c[ThemeToken.BORDER_HOVER.ordinal()]          = hsl(hue, borderSat, dark ? 0.34f : 0.72f);

        c[ThemeToken.WINDOW_OUTLINE.ordinal()]        = (0x66 << 24) | (secondary & 0x00FFFFFF);
        c[ThemeToken.WINDOW_HIGHLIGHT.ordinal()]      = dark ? 0x48FFFFFF : 0x33FFFFFF;
        c[ThemeToken.TILE_OUTLINE.ordinal()]          = (0x55 << 24) | (secondary & 0x00FFFFFF);
        c[ThemeToken.TILE_OUTLINE_STRONG.ordinal()]   = (0xAA << 24) | (secondary & 0x00FFFFFF);

        // ---- Tiles ----
        c[ThemeToken.TILE_FILL.ordinal()]             = (0x22 << 24) | (accent & 0x00FFFFFF);
        c[ThemeToken.TILE_FILL_HOVER.ordinal()]       = (0x38 << 24) | (accent & 0x00FFFFFF);
        c[ThemeToken.TILE_GRAD_TOP.ordinal()]         = hsl(hue, neutSat, dark ? 0.13f : 0.93f);
        c[ThemeToken.TILE_GRAD_BOT.ordinal()]         = c[ThemeToken.TILE_GRAD_TOP.ordinal()];

        // ---- Backdrop (page gradient, two breathing phases) ----
        if (dark) {
            c[ThemeToken.BACKDROP_TOP_DARK.ordinal()]  = hsl(hue, neutSat, 0.06f);
            c[ThemeToken.BACKDROP_TOP_LIGHT.ordinal()] = hsl(hue, neutSat, 0.10f);
            c[ThemeToken.BACKDROP_BOT_DARK.ordinal()]  = hsl(hue, neutSat, 0.03f);
            c[ThemeToken.BACKDROP_BOT_LIGHT.ordinal()] = hsl(hue, neutSat, 0.06f);
        } else {
            c[ThemeToken.BACKDROP_TOP_DARK.ordinal()]  = hsl(hue, neutSat, 0.90f);
            c[ThemeToken.BACKDROP_TOP_LIGHT.ordinal()] = hsl(hue, neutSat, 0.95f);
            c[ThemeToken.BACKDROP_BOT_DARK.ordinal()]  = hsl(hue, neutSat, 0.84f);
            c[ThemeToken.BACKDROP_BOT_LIGHT.ordinal()] = hsl(hue, neutSat, 0.90f);
        }

        // ---- HUD panels: stay dark-tinted in both modes (they float over the 3D world) ----
        c[ThemeToken.HUD_BACKDROP_TOP.ordinal()]       = hsl(hue, neutSat, 0.10f);
        c[ThemeToken.HUD_BACKDROP_BOT.ordinal()]       = accent;

        // ---- Semantic status: fixed hues with mode-locked lightness. NOT
        // accent-derived — red/green/yellow carry fixed meanings regardless of
        // the accent, and per-mode lightness keeps them legible on their paired
        // surface (values track the iOS system-color reference palette). ----
        c[ThemeToken.SEMANTIC_ERROR.ordinal()]   = dark ? hsl(0f,   1.00f, 0.63f) : hsl(353f, 1.00f, 0.42f);
        c[ThemeToken.SEMANTIC_SUCCESS.ordinal()] = dark ? hsl(133f, 0.63f, 0.50f) : hsl(135f, 0.59f, 0.35f);
        c[ThemeToken.SEMANTIC_WARNING.ordinal()] = dark ? hsl(48f,  1.00f, 0.52f) : hsl(40f,  1.00f, 0.45f);

        // ---- Overlay dim: near-black tinted with the neutral hue, deliberately
        // dark in BOTH modes — its job is to dim the world behind a screen. ----
        c[ThemeToken.OVERLAY_DIM.ordinal()] = (0x99 << 24) | (hsl(hue, neutSat, 0.045f) & 0x00FFFFFF);
        // Text on the overlay: the overlay is locked dark in both modes, so the
        // contrast-checked pick lands near-white in both modes.
        c[ThemeToken.ON_OVERLAY.ordinal()] = pickOnColor(c[ThemeToken.OVERLAY_DIM.ordinal()], hue);

        return c;
    }

    // =====================================================================
    //  Color math helpers (all total — never throw for any int input)
    //  Phase D-1 (2026-09-23): the sRGB/luminance/contrast/composition
    //  primitives below are the ONE canonical implementation of the
    //  §3.6 normative metric. Nothing else in the codebase may grow a
    //  competing copy — including HSL-lightness proxies for contrast.
    // =====================================================================

    /**
     * Canonical sRGB {@code EOTF} for one 8-bit channel: the WCAG 2.x
     * transfer function used by the normative contrast metric. Values at or
     * below the 0.03928 knee (channel {@code <= 10}) use the linear segment
     * {@code v/12.92}; everything else uses {@code ((v+0.055)/1.055)^2.4}.
     * Exact at both boundary values (10 linear, 11 non-linear).
     */
    public static double srgbToLinear(int channel8) {
        double v = (channel8 & 0xFF) / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /**
     * WCAG relative luminance of an ARGB color (alpha ignored). The normative
     * perceptive metric behind DESIGN_LANGUAGE §3.6. Enough for a robust
     * light-vs-dark text choice without a full WCAG pass.
     */
    public static double relativeLuminance(int argb) {
        double r = srgbToLinear((argb >> 16) & 0xFF);
        double g = srgbToLinear((argb >> 8) & 0xFF);
        double b = srgbToLinear(argb & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    /** WCAG contrast ratio between two ARGB colors (1..21). Symmetric; 1.0 for identical colors. */
    public static double contrastRatio(int a, int b) {
        double la = relativeLuminance(a);
        double lb = relativeLuminance(b);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    /**
     * Canonical STRAIGHT-alpha composition: {@code foreground over
     * background}, in 8-bit sRGB, returning an ARGB whose alpha is the
     * composed coverage. This is what Minecraft's translucent GUI fills
     * visually do to the framebuffer beneath them, and it is the only
     * sanctioned way to turn a translucent token into the deterministic
     * backing a contrast ratio is measured against (§3.6: thresholds apply
     * to the composited pair — a ratio between two un-composited tokens
     * whose real rendering is translucent is not evidence).
     *
     * <p>Channel and output-alpha rounding: nearest integer. A fully opaque
     * foreground returns itself bit-for-bit (alpha normalized to 0xFF); a
     * fully transparent one returns {@code background} unchanged (including
     * a translucent background alpha). An opaque background always yields an
     * opaque result. Deterministic, allocation-free, and total for any
     * inputs. Nested composition composes associatively to within 1/255 per
     * channel (pinned by test).
     *
     * <p>Deliberately NOT merged into {@link #contrastRatio}: contrast of a
     * translucent color without stating its backing is not meaningful, and
     * the API keeps those two questions visibly separate.
     */
    public static int composite(int fgArgb, int bgArgb) {
        int fa = (fgArgb >>> 24) & 0xFF;
        if (fa == 255) {
            return 0xFF000000 | fgArgb;
        }
        if (fa == 0) {
            return bgArgb;
        }
        int ba = (bgArgb >>> 24) & 0xFF;
        double wFg = fa / 255.0;
        double rest = (1.0 - wFg) * (ba / 255.0);
        double outW = wFg + rest;
        int outA = (int) Math.round(outW * 255.0);
        // Normalize RGB by the exact composed alpha. Dividing by the
        // rounded 8-bit outA instead introduces a second quantization step
        // and is observably wrong for translucent-over-translucent layers.
        int r = (int) Math.round((((fgArgb >> 16) & 0xFF) * wFg + ((bgArgb >> 16) & 0xFF) * rest) / outW);
        int g = (int) Math.round((((fgArgb >> 8) & 0xFF) * wFg + ((bgArgb >> 8) & 0xFF) * rest) / outW);
        int b = (int) Math.round(((fgArgb & 0xFF) * wFg + (bgArgb & 0xFF) * rest) / outW);
        return (outA << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Pick the more legible text color for {@code bgArgb}: a near-white or
     * near-black both lightly tinted toward {@code hue}, choosing whichever
     * yields the higher contrast ratio. The binary choice is deliberate
     * (D-0: continuous foreground derivation would break identity/stability
     * for at most ~0.2 ratio of gain); the generalized, threshold-aware form
     * — including both candidates' ratios and neither-passes detection — is
     * {@link ContrastDerivations#chooseForeground}. Exact ties prefer the
     * light candidate (the historical {@code >=} behavior, preserved).
     */
    public static int pickOnColor(int bgArgb, float hue) {
        return ContrastDerivations.chooseForeground(bgArgb, hue).chosen();
    }

    /** RGB lerp of {@code over} onto {@code color} by {@code ratio}, keeping {@code color}'s alpha. */
    private static int blendColor(int color, int over, float ratio) {
        int a1 = (color >> 24) & 0xFF;
        int r1 = (color >> 16) & 0xFF;
        int g1 = (color >> 8) & 0xFF;
        int b1 = color & 0xFF;

        int r2 = (over >> 16) & 0xFF;
        int g2 = (over >> 8) & 0xFF;
        int b2 = over & 0xFF;

        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return (a1 << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Convert opaque RGB to {@code [h(0..360), s(0..1), l(0..1)]}. Canonical
     * (the resolve-time derivations in {@link ContrastDerivations} reuse it;
     * no second HSL implementation may exist).
     */
    public static float[] rgbToHsl(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f;
        float d = max - min;
        float h, s;
        if (d == 0f) {
            h = 0f;
            s = 0f;
        } else {
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) {
                h = (g - b) / d + (g < b ? 6f : 0f);
            } else if (max == g) {
                h = (b - r) / d + 2f;
            } else {
                h = (r - g) / d + 4f;
            }
            h /= 6f;
        }
        return new float[] { h * 360f, s, l };
    }

    /** Build an opaque ARGB color from HSL (canonical inverse of {@link #rgbToHsl}). */
    public static int hsl(float h, float s, float l) {
        h = ((h % 360f) + 360f) % 360f / 360f;
        s = clamp01(s);
        l = clamp01(l);
        float r, g, b;
        if (s == 0f) {
            r = g = b = l;
        } else {
            float q = l < 0.5f ? l * (1f + s) : l + s - l * s;
            float p = 2f * l - q;
            r = hueToRgb(p, q, h + 1f / 3f);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - 1f / 3f);
        }
        int ri = Math.round(clamp01(r) * 255f);
        int gi = Math.round(clamp01(g) * 255f);
        int bi = Math.round(clamp01(b) * 255f);
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0f) t += 1f;
        if (t > 1f) t -= 1f;
        if (t < 1f / 6f) return p + (q - p) * 6f * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f;
        return p;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float clamp01(float v) {
        return clamp(v, 0f, 1f);
    }
}
