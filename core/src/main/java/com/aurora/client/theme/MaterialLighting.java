package com.aurora.client.theme;

/** Pure captured-pixel lighting. Deltas are absolute HSL lightness points (0..100). */
public final class MaterialLighting {
    private MaterialLighting() {}

    public static final double SLOPE_GAIN = 1.0, RIM_GAIN = 14, SPEC_GAIN = 8;
    public static final int SPEC_POWER = 24;
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
        if (radius < scale) return scale; // SQUARE: a tight machined chamfer, never a rounded corner.
        if (profile == Profile.DOME) return radius * .55; // plateau radius = .45 * radius
        return Math.clamp(Math.round(radius * .6), scale, 3 * scale);
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
        double b = bevelWidth(r, scale, profile), t = Math.clamp(-d / b, 0, 1);
        if (t >= 1) return 0; // plateau: exactly the original ramp, with no specular term
        double gx, gy;
        if (len > 0) { gx = Math.copySign(ox / len, dx); gy = Math.copySign(oy / len, dy); }
        else if (qx > qy) { gx = Math.signum(dx); gy = 0; }
        else { gx = 0; gy = Math.signum(dy); } // square medial diagonal switches facets
        double slope = (1 - t) / Math.max(1e-6, pillowHeight(t)) / b * SLOPE_GAIN * profile.height;
        double invLength = 1 / Math.sqrt(slope * slope + 1);
        double nx = gx * slope * invLength, ny = gy * slope * invLength, nz = invLength;
        double dot = nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z;
        double diffuse = dot - LIGHT_Z;
        double reflectedZ = 2 * dot * nz - LIGHT_Z;
        double specular = profile.specular ? Math.pow(Math.max(0, reflectedZ), SPEC_POWER) : 0;
        return RIM_GAIN * diffuse + SPEC_GAIN * specular;
    }

    /** Baked face lightness; alpha/AA, hue and saturation pass through unchanged. */
    public static int face(int argb, int column, int row, int width, int height, float radius,
                           int scale, float top, float bottom, Profile profile) {
        if ((argb >>> 24) == 0) return argb;
        float delta = verticalDelta(row, height, top, bottom)
                + (float) curvatureDelta(column + .5, row + .5, width, height, radius, scale, profile);
        return lightness(argb, delta);
    }

    public static float verticalDelta(int row, int height, float top, float bottom) {
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

    /** Source-atop overlay: edge ink changes RGB inside coverage, never enlarges the silhouette. */
    public static int edge(int argb, int overlay) {
        if ((argb >>> 24) == 0) return argb;
        double a = (overlay >>> 24) / 255d;
        int r = (int) Math.round(((argb >>> 16) & 255) * (1 - a) + ((overlay >>> 16) & 255) * a);
        int g = (int) Math.round(((argb >>> 8) & 255) * (1 - a) + ((overlay >>> 8) & 255) * a);
        int b = (int) Math.round((argb & 255) * (1 - a) + (overlay & 255) * a);
        return (argb & 0xff000000) | r << 16 | g << 8 | b;
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
