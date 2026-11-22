package com.aurora.client.util;

import com.aurora.client.config.AuroraConfig;

/**
 * Pure math and clamping for Pack Tweaks additions: motion blur, colour
 * saturation, local time, local weather, custom skies, fire overlay, and hurt
 * camera scaling.
 *
 * <p>Lives in the Minecraft-free core (ADR 0003).
 */
public final class PackTweaksMath {
    public static final double MAX_MOTION_BLUR = 0.85;
    public static final double MIN_SATURATION = 0.0;
    public static final double MAX_SATURATION = 2.0;
    public static final int MAX_TIME_TICKS = 23999;
    public static final double MIN_FIRE_HEIGHT = -0.50;
    public static final double MAX_FIRE_HEIGHT = 0.20;
    public static final double DEFAULT_FIRE_OPACITY = 0.9;
    public static final double MIN_HURT_CAM = 0.0;
    public static final double MAX_HURT_CAM = 2.0;
    public static final double MAX_STAR_BRIGHTNESS = 3.0;

    private PackTweaksMath() {}

    /** Clamps configured motion blur strength to {@code [0.0, 0.85]}. */
    public static double clampMotionBlur(double strength) {
        if (Double.isNaN(strength) || strength <= 0.0) return 0.0;
        return Math.min(MAX_MOTION_BLUR, strength);
    }

    /**
     * Frame-rate-independent temporal history blend weight for motion blur.
     * At 60 FPS ({@code deltaSeconds = 1/60}) this equals {@code clampMotionBlur(strength)};
     * at higher framerates it rises smoothly so the trail duration in real time stays steady.
     */
    public static float motionBlurBlend(double strength, double deltaSeconds) {
        double s = clampMotionBlur(strength);
        if (s <= 0.001) return 0.0f;
        double dt = Double.isFinite(deltaSeconds) && deltaSeconds > 0.0
                ? Math.min(0.1, Math.max(0.0005, deltaSeconds))
                : (1.0 / 60.0);
        double weight = Math.pow(s, dt * 60.0);
        return (float) Math.min(0.92, Math.max(0.0, weight));
    }

    /** Clamps colour saturation multiplier to {@code [0.0, 2.0]} (1.0 = vanilla). */
    public static double clampSaturation(double saturation) {
        if (Double.isNaN(saturation)) return 1.0;
        if (saturation <= MIN_SATURATION) return MIN_SATURATION;
        return Math.min(MAX_SATURATION, saturation);
    }

    /** True when either motion blur or colour saturation needs a post-processing pass. */
    public static boolean isPostEffectActive(double motionBlurStrength, double saturation) {
        return clampMotionBlur(motionBlurStrength) > 0.001
                || Math.abs(clampSaturation(saturation) - 1.0) > 0.001;
    }

    /** Clamps local time-of-day ticks to {@code [0, 23999]}. */
    public static int clampTimeTicks(int ticks) {
        if (ticks < 0) return 0;
        return Math.min(MAX_TIME_TICKS, ticks);
    }

    /** Client-side rain level override for {@link AuroraConfig.LocalWeather}. */
    public static float rainLevel(AuroraConfig.LocalWeather mode) {
        if (mode == null || mode == AuroraConfig.LocalWeather.CLEAR) return 0.0f;
        return 1.0f;
    }

    /** Client-side thunder level override for {@link AuroraConfig.LocalWeather}. */
    public static float thunderLevel(AuroraConfig.LocalWeather mode) {
        return mode == AuroraConfig.LocalWeather.THUNDER ? 1.0f : 0.0f;
    }

    /** Sky dome RGB color (0x00RRGGBB) for the selected custom sky mode. */
    public static int skyRgb(AuroraConfig.CustomSkyMode mode, int customArgb, int vanillaRgb) {
        if (mode == null || mode == AuroraConfig.CustomSkyMode.VANILLA) return vanillaRgb & 0x00FFFFFF;
        return switch (mode) {
            case CLEAR_DAY -> 0x78A7FF;
            case SUNSET -> 0xE86A42;
            case AURORA_NIGHT -> 0x0B1530;
            case CUSTOM_COLOR -> customArgb & 0x00FFFFFF;
            case VANILLA -> vanillaRgb & 0x00FFFFFF;
        };
    }

    /** Sunrise/sunset horizon glow ARGB for the selected custom sky mode (0 = none). */
    public static int sunriseSunsetArgb(AuroraConfig.CustomSkyMode mode, int vanillaArgb) {
        if (mode == null || mode == AuroraConfig.CustomSkyMode.VANILLA) return vanillaArgb;
        return switch (mode) {
            case CLEAR_DAY, CUSTOM_COLOR -> 0;
            case SUNSET -> 0xCCFF6B35;
            case AURORA_NIGHT -> 0x6626D0CE;
            case VANILLA -> vanillaArgb;
        };
    }

    /** Effective star brightness in {@code [0.0, 1.0]}. */
    public static float starBrightness(float vanillaStars, AuroraConfig.CustomSkyMode mode,
                                       double boost, boolean showStars) {
        if (!showStars) return 0.0f;
        double clampedBoost = Double.isNaN(boost) ? 1.0 : Math.max(0.0, Math.min(MAX_STAR_BRIGHTNESS, boost));
        float base = mode == AuroraConfig.CustomSkyMode.AURORA_NIGHT
                ? Math.max(vanillaStars, 0.70f)
                : Math.max(0.0f, vanillaStars);
        return (float) Math.max(0.0, Math.min(1.0, base * clampedBoost));
    }

    /** Clamps first-person fire vertical offset to {@code [-0.50, 0.20]}. */
    public static double clampFireHeight(double offset) {
        if (Double.isNaN(offset)) return 0.0;
        if (offset < MIN_FIRE_HEIGHT) return MIN_FIRE_HEIGHT;
        return Math.min(MAX_FIRE_HEIGHT, offset);
    }

    /** Clamps first-person fire opacity to {@code [0.0, 1.0]}. */
    public static double clampFireOpacity(double opacity) {
        if (Double.isNaN(opacity)) return DEFAULT_FIRE_OPACITY;
        if (opacity <= 0.0) return 0.0;
        return Math.min(1.0, opacity);
    }

    /** Packs first-person fire white vertex color with clamped alpha (vanilla is 0xE5FFFFFF). */
    public static int fireColor(double opacity) {
        int alpha = (int) Math.round(clampFireOpacity(opacity) * 255.0);
        return ((alpha & 0xFF) << 24) | 0x00FFFFFF;
    }

    /** Clamps hurt camera strength multiplier to {@code [0.0, 2.0]}. */
    public static double clampHurtCamStrength(double strength) {
        if (Double.isNaN(strength)) return 1.0;
        if (strength <= MIN_HURT_CAM) return MIN_HURT_CAM;
        return Math.min(MAX_HURT_CAM, strength);
    }
}
