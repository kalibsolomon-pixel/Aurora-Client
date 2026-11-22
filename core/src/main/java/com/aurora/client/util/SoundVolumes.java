package com.aurora.client.util;

/**
 * Pure per-sound volume attenuation math (0–100% only; never boosts above 100%,
 * keeping per-sound volume strictly cosmetic under ADR 0007).
 *
 * <p>Lives in the Minecraft-free core (ADR 0003).
 */
public final class SoundVolumes {
    public static final float MIN_SCALE = 0.0f;
    public static final float MAX_SCALE = 1.0f;
    public static final float DEFAULT_SCALE = 1.0f;

    private SoundVolumes() {}

    /** Clamps a per-sound volume multiplier strictly to {@code [0.0f, 1.0f]}. */
    public static float clampScale(double scale) {
        if (Double.isNaN(scale)) return DEFAULT_SCALE;
        if (scale <= MIN_SCALE) return MIN_SCALE;
        if (scale >= MAX_SCALE) return MAX_SCALE;
        return (float) scale;
    }

    /** True when the multiplier represents unchanged vanilla volume (100%). */
    public static boolean isNeutral(double scale) {
        return clampScale(scale) >= 0.999f;
    }

    /**
     * Attenuates a vanilla calculated sound volume by an optional override
     * scale in {@code [0.0, 1.0]}. Returns {@code vanillaVolume} unchanged when
     * {@code overrideScale} is {@code null}, and {@code 0.0f} when muted.
     */
    public static float attenuate(float vanillaVolume, Float overrideScale) {
        if (overrideScale == null) return vanillaVolume;
        float clamped = clampScale(overrideScale);
        if (clamped <= 0.0001f) return 0.0f;
        return vanillaVolume * clamped;
    }

    /**
     * Converts a registry sound id such as {@code "minecraft:entity.ender_dragon.growl"}
     * into a readable label such as {@code "Ender Dragon — Growl"}.
     */
    public static String humanizeSoundId(String id) {
        if (id == null || id.isBlank()) return "";
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        String[] parts = path.split("\\.");
        int start = (parts.length > 2 && (parts[0].equals("entity") || parts[0].equals("block") || parts[0].equals("item")))
                ? 1 : 0;
        StringBuilder sb = new StringBuilder(path.length() + 4);
        for (int i = start; i < parts.length; i++) {
            if (i > start) sb.append(i == parts.length - 1 ? " — " : " ");
            appendTitleWords(sb, parts[i]);
        }
        return sb.toString();
    }

    private static void appendTitleWords(StringBuilder sb, String segment) {
        boolean upper = true;
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c == '_') {
                sb.append(' ');
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
    }
}
