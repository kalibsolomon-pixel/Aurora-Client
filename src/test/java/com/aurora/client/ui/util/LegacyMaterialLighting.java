package com.aurora.client.ui.util;

/** Pure captured-pixel lighting. Deltas are absolute HSL lightness points (0..100). */
public final class LegacyMaterialLighting {
    private LegacyMaterialLighting() {}

    // Tuning comes from production: this reference pins the algorithm, not the constants.
    public static final double SLOPE_GAIN = com.aurora.client.theme.MaterialLighting.SLOPE_GAIN,
            RIM_GAIN = com.aurora.client.theme.MaterialLighting.RIM_GAIN,
            SPEC_GAIN = com.aurora.client.theme.MaterialLighting.SPEC_GAIN;
    public static final int SPEC_POWER = 24;
    /** Planar facet slope (45 degrees) and the pillow slope cap at the silhouette. */
    public static final double FACET_SLOPE = 1.0, MAX_SLOPE = 3.0;
    public static final double LIGHT_LENGTH = Math.sqrt(.35 * .35 + .75 * .75 + .55 * .55);
    public static final double LIGHT_X = -.35 / LIGHT_LENGTH, LIGHT_Y = -.75 / LIGHT_LENGTH,
            LIGHT_Z = .55 / LIGHT_LENGTH;
    private static final double SHADOW_LENGTH = Math.hypot(.35, .75);
    public static final double SHADOW_X = .35 / SHADOW_LENGTH, SHADOW_Y = .75 / SHADOW_LENGTH;

    /** Signed height and specular policy. DOME retains the caller's exact silhouette. */
    public enum Profile {
        FLAT(0, false), RAISED(1, true), INSET(-1, false), PRESSED(.4, false), DOME(1, true);
        final double height;
        final boolean specular;
        Profile(double height, boolean specular) { this.height = height; this.specular = specular; }
    }

    /** Rounded-box SDF in device pixels, negative inside; radius zero is an exact box. */
    public static double distance(double x, double y, double width, double height, double radius) {
        double r = Math.clamp(radius, 0, Math.min(width, height) * .5);
        double qx = Math.abs(x - width * .5) - (width * .5 - r);
        double qy = Math.abs(y - height * .5) - (height * .5 - r);
        return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - r;
    }

    public static double bevelWidth(double radius, int scale, Profile profile) {
        if (profile == Profile.DOME && radius >= scale) return radius * .55; // plateau radius = .45 * radius
        double rounded = Math.clamp(Math.round(radius * .8), Math.round(1.5 * scale), 4 * scale);
        // Gem facets (owner, 2026-11-17): a 2.25 GUI px planar chamfer at radius 0 that
        // blends into the rounded band as the radius reaches 2 GUI px.
        return 2.25 * scale + (rounded - 2.25 * scale) * facetBlend(radius, scale);
    }

    /** Largest share of a face's shorter side the bevel band may take, so small controls keep a
     * flat plateau for their text (a 14 px field gets ~3 px, not 4). Domes are exempt. */
    public static final double MAX_BAND_SHARE = .22;

    public static double bevelWidth(double radius, int scale, Profile profile, double shorterSide) {
        double band = bevelWidth(radius, scale, profile);
        return profile == Profile.DOME ? band : Math.min(band, shorterSide * MAX_BAND_SHARE);
    }

    /** 0 at a sharp corner (planar gem facets), 1 from a 2 GUI px radius (pillowed roll-off). */
    public static double facetBlend(double radius, int scale) {
        return Math.clamp(radius / (2.0 * scale), 0, 1);
    }

    public static double pillowHeight(double t) {
        t = Math.clamp(t, 0, 1);
        return Math.sqrt(1 - (1 - t) * (1 - t));
    }

    /** Local pixel centre, own face geometry, device units. No arrays or per-pixel objects.
     * The outward SDF gradient is the physical normal's XY direction: height decreases
     * outwards. Equivalently n = normalize(-gradient(-d) * h'/b, 1). This sign lights
     * the upper-left rim under the UI's upper-left light; inset reverses the slope.
     */
    public static double curvatureDelta(double x, double y, double width, double height,
                                        double radius, int scale, Profile profile) {
        if (profile == Profile.FLAT) return 0;
        double r = Math.clamp(radius, 0, Math.min(width, height) * .5);
        double dx = x - width * .5, dy = y - height * .5;
        double qx = Math.abs(dx) - (width * .5 - r), qy = Math.abs(dy) - (height * .5 - r);
        double ox = Math.max(qx, 0), oy = Math.max(qy, 0), len = Math.hypot(ox, oy);
        double d = len + Math.min(Math.max(qx, qy), 0) - r;
        double b = bevelWidth(r, scale, profile, Math.min(width, height)), t = Math.clamp(-d / b, 0, 1);
        if (t >= 1) return 0; // plateau: exactly the original ramp, with no specular term
        double gx, gy;
        if (len > 0) { gx = Math.copySign(ox / len, dx); gy = Math.copySign(oy / len, dy); }
        else if (qx > qy) { gx = Math.signum(dx); gy = 0; }
        else { gx = 0; gy = Math.signum(dy); } // square medial diagonal switches facets
        // The bevel is as tall as it is wide (matching the approved prototype): the pillow's own
        // slope, capped where it would spike at the silhouette, blended with a 45-degree planar
        // facet as the corner sharpens.
        double pillow = Math.min(MAX_SLOPE, (1 - t) / Math.max(1e-6, pillowHeight(t)));
        double k = facetBlend(r, scale);
        double slope = (FACET_SLOPE + (pillow - FACET_SLOPE) * k) * SLOPE_GAIN * profile.height;
        double invLength = 1 / Math.sqrt(slope * slope + 1);
        double nx = gx * slope * invLength, ny = gy * slope * invLength, nz = invLength;
        double dot = nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z;
        double diffuse = dot - LIGHT_Z;
        double reflectedZ = 2 * dot * nz - LIGHT_Z;
        double specular = profile.specular ? Math.pow(Math.max(0, reflectedZ), SPEC_POWER) : 0;
        // The shadow-side gain (2026-11-18) is an equation change, not an optimization, so the
        // reference follows it.
        double shade = 1 + (com.aurora.client.theme.MaterialLighting.SHADE_GAIN - 1) * k;
        return RIM_GAIN * (diffuse < 0 ? diffuse * shade : diffuse) + SPEC_GAIN * specular;
    }

    /** Baked face lightness; alpha/AA, hue and saturation pass through unchanged. */
    public static int face(int argb, int column, int row, float width, float height, float radius,
                           int scale, float top, float bottom, Profile profile) {
        if ((argb >>> 24) == 0) return argb;
        float delta = verticalDelta(row, height, top, bottom)
                + (float) curvatureDelta(column + .5, row + .5, width, height, radius, scale, profile);
        return lightness(argb, delta);
    }

    public static float verticalDelta(int row, float height, float top, float bottom) {
        return height <= 1 ? (top + bottom) * .5f : top + (bottom - top) * row / (height - 1f);
    }

    /** Preserve hue, HSL saturation and alpha; only lightness changes, with endpoint clamping. */
    public static int lightness(int argb, float points) {
        if ((argb >>> 24) == 0 || points == 0) return argb;
        double r = ((argb >>> 16) & 255) / 255d;
        double g = ((argb >>> 8) & 255) / 255d;
        double b = (argb & 255) / 255d;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double light = (max + min) * .5, chroma = max - min;
        double next = Math.max(0, Math.min(1, light + points / 100d));
        // Scaling chroma around the new lightness preserves H and S without hue conversion.
        double ratio = chroma == 0 ? 0 : (1 - Math.abs(2 * next - 1)) / (1 - Math.abs(2 * light - 1));
        return (argb & 0xff000000) | channel(next + (r - light) * ratio) << 16
                | channel(next + (g - light) * ratio) << 8 | channel(next + (b - light) * ratio);
    }

    /** A black shadow behind a straight-alpha pixel, preserving its already-lit colour. */
    public static int shadowBehind(int argb, float shadowAlpha) {
        double a = (argb >>> 24) / 255d;
        double out = a + Math.max(0, Math.min(1, shadowAlpha)) * (1 - a);
        if (out == 0) return 0;
        double ratio = a / out;
        return (int) Math.round(out * 255) << 24
                | (int) Math.round(((argb >>> 16) & 255) * ratio) << 16
                | (int) Math.round(((argb >>> 8) & 255) * ratio) << 8
                | (int) Math.round((argb & 255) * ratio);
    }

    private static int channel(double value) { return (int) Math.round(Math.max(0, Math.min(1, value)) * 255); }
}
