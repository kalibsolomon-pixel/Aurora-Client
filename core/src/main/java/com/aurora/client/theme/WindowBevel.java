package com.aurora.client.theme;

/** A window's independent frame, not a filter over the glass plateau. All geometry is in GUI pixels. */
public final class WindowBevel {
    private WindowBevel() {}

    public enum Level {
        OFF("Off", 0, 0, 0), SUBTLE("Subtle", .73, .72, 1.5),
        STANDARD("Standard", 1, 1, 2.5), DEEP("Deep", 1.25, 1.24, 3.5);

        private final String label;
        private final double width, gain, elevation;

        Level(String label, double width, double gain, double elevation) {
            this.label = label;
            this.width = width;
            this.gain = gain;
            this.elevation = elevation;
        }

        public String label() { return label; }
    }

    public static final int SHADOW_PAD = 7;

    public record Finish(int color, double rimGain, double specGain, double specPower, double ramp) {}

    public static Finish finish(int windowFill, int outline, int blurRadius, Level level) {
        double red = (windowFill >>> 16 & 255) / 255d;
        double green = (windowFill >>> 8 & 255) / 255d;
        double blue = (windowFill & 255) / 255d;
        double max = Math.max(red, Math.max(green, blue));
        double min = Math.min(red, Math.min(green, blue));
        double light = (max + min) * .5;
        double chroma = max - min;
        double saturation = chroma == 0 ? 0 : Math.clamp(chroma / (1 - Math.abs(2 * light - 1)), 0, 1);
        double density = (windowFill >>> 24) / 255d;
        double blur = (double) ThemeDefinition.normalizedBlurRadius(blurRadius) / ThemeDefinition.MAX_BLUR_RADIUS;
        double glass = (1 - density) * (.5 + .5 * blur);
        double gem = saturation * (1 - Math.abs(2 * density - 1));
        double metal = (1 - saturation) * density * (1 - .8 * blur);
        double tint = Math.clamp(.28 + .46 * gem + .12 * density - .12 * glass, .16, .82);
        int rgb = mix(windowFill, outline, tint, 16) << 16 | mix(windowFill, outline, tint, 8) << 8
                | mix(windowFill, outline, tint, 0);
        int color = MaterialLighting.lightness(0xeb000000 | rgb, (float) (6 + 8 * metal + 3 * glass - 5 * light));
        return new Finish(color, level.gain * (.9 + .24 * metal), level.gain * (.70 + .75 * gem + 1.05 * metal + .18 * glass),
                18 + 22 * metal + 7 * gem, level.gain * (2 + 8 * metal + 2 * gem));
    }

    private static int mix(int fill, int outline, double tint, int shift) {
        return (int) Math.round((fill >>> shift & 255) * tint + (outline >>> shift & 255) * (1 - tint));
    }

    public static double bandWidth(double shorterSide, double radius, double contentInset, Level level) {
        double target = Math.clamp(3.8 + shorterSide / 76 + .08 * Math.clamp(radius, 0, 20), 0, 8);
        return Math.max(0, Math.min(contentInset, Math.min(shorterSide * MaterialLighting.MAX_BAND_SHARE,
                Math.min(8, Math.min(target, contentInset) * level.width))));
    }

    /** SDF clearance to a content corner at known offsets from the two exterior edges. */
    public static float cornerClearance(float radius, float horizontal, float vertical) {
        double qx = radius - horizontal, qy = radius - vertical;
        double distance = Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - radius;
        return (float) Math.max(0, -distance);
    }

    /** Evaluates only the band and exterior shadow; the uncovered interior always returns zero. */
    public static final class Raster {
        private final double width, height, radius, band, aa, inset, offsetX, offsetY, softness, shadowAlpha;
        private final Finish finish;

        public Raster(double width, double height, double radius, double band, double pixelSize, Finish finish, Level level) {
            this.width = width;
            this.height = height;
            this.radius = Math.clamp(radius, 0, Math.min(width, height) * .5);
            this.band = band;
            this.aa = pixelSize;
            this.finish = finish;
            inset = Math.max(this.radius, band);
            offsetX = level.elevation * MaterialLighting.SHADOW_X;
            offsetY = level.elevation * MaterialLighting.SHADOW_Y;
            softness = level.elevation * 2;
            shadowAlpha = .21 + .035 * level.elevation;
        }

        public int pixel(double x, double y) {
            if (band <= 0 || y >= inset && y <= height - inset && x >= band && x <= width - band
                    || x >= inset && x <= width - inset && y >= band && y <= height - band) return 0;
            double distance = MaterialLighting.distance(x, y, width, height, radius);
            if (distance <= -band) return 0;
            double inside = Math.clamp(.5 - distance / aa, 0, 1);
            int shaded = 0;
            if (inside > 0) {
                double ramp = finish.ramp * (1 - 2 * Math.clamp(y / height, 0, 1));
                double ink = Math.clamp((distance + band) / Math.min(.9, band * .55), 0, 1);
                double rim = MaterialLighting.curvatureDelta(x, y, width, height, radius, 1, MaterialLighting.Profile.RAISED,
                        band, finish.rimGain, finish.specGain, finish.specPower);
                int alpha = (int) Math.round((finish.color >>> 24) * inside * ink);
                shaded = alpha == 0 ? 0 : alpha << 24 | MaterialLighting.lightness(finish.color, (float) (ramp + rim)) & 0xffffff;
            }
            if (inside == 1) return shaded;
            double shadowDistance = MaterialLighting.distance(x - offsetX, y - offsetY, width, height, radius);
            float shadow = (float) (shadowAlpha * (1 - inside) * Math.clamp(.5 - shadowDistance / softness, 0, 1));
            return MaterialLighting.shadowBehind(shaded, shadow);
        }
    }
}
