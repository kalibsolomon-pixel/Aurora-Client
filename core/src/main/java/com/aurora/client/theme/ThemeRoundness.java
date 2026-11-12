package com.aurora.client.theme;

/**
 * The corner radius a theme renders with: one pixel value chosen on the
 * Corner Radius slider (2026-11-12; it replaced the Round / Slightly Round /
 * Square choice). Every radius in the UI derives from it.
 *
 * <p>A non-color token: {@link ResolvedTheme#project()} publishes the three
 * sizes onto {@code AuroraTheme.RADIUS*}. {@link #DEFAULT_RADIUS} reproduces
 * the historical constants (10/14/6), so the factory look is unchanged.
 *
 * <ul>
 *   <li>{@link #radius()} — panels and buttons (the slider value);</li>
 *   <li>{@link #radiusLarge()} — major sheets, {@code radius × 7/5};</li>
 *   <li>{@link #radiusSmall()} — compact controls, {@code radius × 3/5};</li>
 *   <li>{@link #capsule(float)} — shapes whose fully round form is a pill
 *       or circle (switch tracks and thumbs, slider tracks and knobs,
 *       scrollbar thumbs): a full capsule at the default radius or above,
 *       flattening in proportion below it, square at 0.</li>
 * </ul>
 *
 * <p>Instances are interned, so reading the theme's value allocates nothing.
 */
public final class ThemeRoundness {

    public static final int MIN_RADIUS = 0;
    public static final int MAX_RADIUS = 20;
    /** Factory radius: the historical "Round" family. */
    public static final int DEFAULT_RADIUS = 10;

    private static final ThemeRoundness[] VALUES = new ThemeRoundness[MAX_RADIUS + 1];
    static {
        for (int r = MIN_RADIUS; r <= MAX_RADIUS; r++) VALUES[r] = new ThemeRoundness(r);
    }

    /** The radii the retired Corner Style choices stood for (migration and tests). */
    public static final ThemeRoundness ROUND = of(DEFAULT_RADIUS);
    public static final ThemeRoundness SLIGHTLY_ROUND = of(6);
    public static final ThemeRoundness SQUARE = of(0);

    private final int radius;

    private ThemeRoundness(int radius) {
        this.radius = radius;
    }

    /** The interned value for {@code radius}, clamped to {@link #MIN_RADIUS}..{@link #MAX_RADIUS}. */
    public static ThemeRoundness of(int radius) {
        return VALUES[clamp(radius)];
    }

    /** Clamp a persisted or requested radius into the supported range. */
    public static int clamp(int radius) {
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
    }

    /**
     * The radius a retired Corner Style name stood for — {@code ROUND} 10,
     * {@code SLIGHTLY_ROUND} 6, {@code SQUARE} 0 — or the default for a
     * null or unknown name. Total; never throws.
     */
    public static int legacyRadius(String name) {
        if (name != null) {
            if (name.equalsIgnoreCase("SQUARE")) return SQUARE.radius;
            if (name.equalsIgnoreCase("SLIGHTLY_ROUND")) return SLIGHTLY_ROUND.radius;
        }
        return DEFAULT_RADIUS;
    }

    /** Standard radius for panels and buttons (historical {@code RADIUS}). */
    public int radius() {
        return radius;
    }

    /** Larger radius for major sheets and modals (historical {@code RADIUS_LARGE}). */
    public int radiusLarge() {
        return radius * 7 / 5;
    }

    /** Tighter radius for compact controls (historical {@code RADIUS_SMALL}). */
    public int radiusSmall() {
        return radius * 3 / 5;
    }

    /**
     * Corner radius for a shape whose fully round form is a capsule of the
     * given thickness (its shorter side): {@code thickness / 2} at
     * {@link #DEFAULT_RADIUS} or above, scaled down in proportion below it.
     */
    public float capsule(float thickness) {
        return thickness / 2f * Math.min(1f, radius / (float) DEFAULT_RADIUS);
    }

    /** True at radius 0: every corner is square. */
    public boolean square() {
        return radius == 0;
    }

    @Override
    public String toString() {
        return radius + " px";
    }
}
