package com.aurora.client.render;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Smooth Fog: replaces Minecraft's piecewise-linear fog ramp with a C1-continuous
 * cubic Hermite curve ({@code t * t * (3.0 - 2.0 * t)}) across the exact same
 * {@code [fogStart, fogEnd]} distance bounds.
 *
 * <p>Because fog stays {@code 0.0} at or below {@code fogStart} and reaches
 * {@code 1.0} at the exact same {@code fogEnd}, the change is purely cosmetic:
 * it removes the harsh linear crease where distance fog starts and ends on
 * terrain, sky, clouds, and entities without revealing anything past vanilla's
 * fog boundary. The patch lives behind {@link #DEFINE} so pipelines that do not
 * carry it compile identically to vanilla, and composes cleanly on top of
 * {@link GradientDither}'s twin pipelines.
 *
 * <p>No Minecraft imports: this class belongs to the Minecraft-free core (ADR 0003).
 */
public final class SmoothFog {
    private SmoothFog() {}

    /** Defined only in Aurora's smooth-fog twin pipelines. */
    public static final String DEFINE = "AURORA_SMOOTH_FOG";

    static final String MARKER = "aurora_smooth_fog_t";

    /**
     * Fragment shaders whose preprocessed source inlines {@code fog.glsl}
     * (vanilla or Sodium) and gets the smooth-fog branch behind {@link #DEFINE}.
     */
    static final Set<String> SHADERS = Set.of(
            "minecraft:shaders/core/sky.fsh",
            "minecraft:shaders/core/terrain.fsh",
            "minecraft:shaders/core/clouds.fsh",
            "minecraft:shaders/core/rendertype_clouds.fsh",
            "minecraft:shaders/core/entity.fsh",
            "sodium:shaders/blocks/block_layer_opaque.fsh");

    private static final Pattern LINEAR_FOG_RETURN = Pattern.compile(
            "(float\\s+linear_fog_value\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?)"
                    + "return\\s*\\(\\s*vertexDistance\\s*-\\s*fogStart\\s*\\)\\s*/\\s*\\(\\s*fogEnd\\s*-\\s*fogStart\\s*\\)\\s*;");

    /** Whether the fragment shader file at {@code namespace:path} gets the smooth-fog branch. */
    public static boolean patchesShader(String namespace, String path) {
        return SHADERS.contains(namespace + ":" + path);
    }

    /**
     * Whether a pipeline should receive a smooth-fog twin. Accepts vanilla
     * world pipelines, Sodium's terrain pipelines, and Aurora's own
     * {@code gradient_dither/*} twins (so Smooth Fog and Gradient Dithering
     * compose in one pass), while excluding GUI, outline, and OIT passes.
     */
    public static boolean appliesTo(String namespace, String path, String fragmentShader, Set<String> defineNames) {
        boolean eligibleOwner = "minecraft".equals(namespace)
                || "sodium".equals(namespace)
                || (("aurora".equals(namespace) || "aurora_sodium".equals(namespace))
                        && path.startsWith("gradient_dither/"));
        if (!eligibleOwner) return false;
        if (path.contains("gui") || path.contains("outline") || path.contains("debug")) return false;
        int colon = fragmentShader.indexOf(':');
        if (colon < 0) return false;
        String shaderFile = fragmentShader.substring(0, colon + 1) + "shaders/"
                + fragmentShader.substring(colon + 1) + ".fsh";
        if (!SHADERS.contains(shaderFile)) return false;
        for (String name : defineNames) {
            if (name.startsWith("OIT")) return false;
        }
        return true;
    }

    /**
     * Inserts the {@link #DEFINE}-guarded cubic Hermite curve inside
     * {@code linear_fog_value}, or returns {@code source} unchanged if the
     * function is absent or already patched.
     */
    public static String patch(String source) {
        if (source == null || source.contains(MARKER)) return source;
        Matcher m = LINEAR_FOG_RETURN.matcher(source);
        if (!m.find()) return source;
        String replacement = m.group(1)
                + "#ifdef " + DEFINE + "\n"
                + "    float " + MARKER + " = (vertexDistance - fogStart) / (fogEnd - fogStart);\n"
                + "    return " + MARKER + " * " + MARKER + " * (3.0 - 2.0 * " + MARKER + ");\n"
                + "#else\n"
                + "    return (vertexDistance - fogStart) / (fogEnd - fogStart);\n"
                + "#endif";
        return source.substring(0, m.start()) + replacement + source.substring(m.end());
    }

    /** Pure Java reference curve for tests: identical to the GLSL formula. */
    public static float eval(float distance, float fogStart, float fogEnd) {
        if (distance <= fogStart) return 0.0f;
        if (distance >= fogEnd) return 1.0f;
        float t = (distance - fogStart) / (fogEnd - fogStart);
        return t * t * (3.0f - 2.0f * t);
    }
}
