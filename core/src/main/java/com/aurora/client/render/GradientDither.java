package com.aurora.client.render;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gradient Dithering: which world pipelines get an ordered dither and the GLSL that adds it.
 *
 * <p>Sky, fog and smooth lighting are shaded in floating point and stored in the 8-bit main
 * target, which turns slow gradients into visible bands. The adapter gives each eligible
 * pipeline a twin that compiles with {@link #DEFINE}; the patched fragment source then adds an
 * 8x8 Bayer offset to the colour just before it is stored. Offsets lie strictly inside half an
 * 8-bit step, so a colour that is already an exact step is never changed and the average of
 * every 8x8 tile stays the shaded value. Without the define the patched source compiles to the
 * original shader, so GUI pipelines that share a shader file are untouched.
 *
 * <p>No Minecraft imports: this class belongs to the Minecraft-free core (ADR 0003).
 */
public final class GradientDither {
    private GradientDither() {}

    /** Defined only in Aurora's twin pipelines; its value is a {@link Mode}. */
    public static final String DEFINE = "AURORA_GRADIENT_DITHER";

    /** How the stored colour is formed from the fragment, which decides where the offset goes. */
    public enum Mode {
        /** No blending: the fragment colour is stored as is. */
        OPAQUE(1),
        /**
         * {@code src * a + dst * (1 - a)}: the offset is divided by alpha (bounded so the
         * source never clips) and lands on the blended result at full size.
         */
        STRAIGHT_ALPHA(2);

        public final int value;

        Mode(int value) { this.value = value; }
    }

    /** The blend a pipeline's single colour target uses, as far as the dither cares. */
    public enum Blend { NONE, STRAIGHT_ALPHA, OTHER }

    /**
     * Fragment shaders whose sources carry the dither, as {@code namespace:path} of the file:
     * the sky disc, the sunrise fan, the End sky, terrain (vanilla and Sodium) and clouds.
     */
    static final Set<String> SHADERS = Set.of(
            "minecraft:shaders/core/sky.fsh",
            "minecraft:shaders/core/position_color.fsh",
            "minecraft:shaders/core/position_tex_color.fsh",
            "minecraft:shaders/core/terrain.fsh",
            "minecraft:shaders/core/clouds.fsh",
            "minecraft:shaders/core/rendertype_clouds.fsh",
            "sodium:shaders/blocks/block_layer_opaque.fsh");

    /** Vanilla pipelines that draw only into the world, by location. */
    static final Set<String> VANILLA_PIPELINES = Set.of(
            "pipeline/sky", "pipeline/sunrise_sunset", "pipeline/end_sky",
            "pipeline/solid_terrain", "pipeline/cutout_terrain", "pipeline/translucent_terrain",
            "pipeline/solid_terrain_multidraw", "pipeline/cutout_terrain_multidraw",
            "pipeline/translucent_terrain_multidraw",
            "pipeline/clouds", "pipeline/flat_clouds");

    static final String SODIUM_FRAGMENT = "sodium:blocks/block_layer_opaque";

    /** Whether the fragment shader file at {@code namespace:path} gets the dither epilogue. */
    public static boolean patchesShader(String namespace, String path) {
        return SHADERS.contains(namespace + ":" + path);
    }

    /**
     * The mode for a pipeline's twin, or null when the pipeline keeps its original shaders: it
     * is not a world pipeline Aurora dithers, its fragment shader ({@code namespace:path} of
     * the shader id, as a pipeline names it) is not one that carries the dither, it is an
     * order-independent transparency pass (whose targets are not 8-bit colour), or its target
     * is not a single 8-bit colour buffer with an opaque or straight-alpha blend.
     */
    public static Mode modeFor(String namespace, String path, String fragmentShader,
                               Set<String> defineNames, boolean singleRgba8Target, Blend blend) {
        boolean world = "minecraft".equals(namespace) ? VANILLA_PIPELINES.contains(path)
                : "sodium".equals(namespace) && SODIUM_FRAGMENT.equals(fragmentShader);
        int colon = fragmentShader.indexOf(':');
        if (!world || colon < 0 || !singleRgba8Target
                || !SHADERS.contains(fragmentShader.substring(0, colon + 1) + "shaders/" + fragmentShader.substring(colon + 1) + ".fsh")) {
            return null;
        }
        for (String name : defineNames) if (name.startsWith("OIT")) return null;
        return switch (blend) {
            case NONE -> Mode.OPAQUE;
            case STRAIGHT_ALPHA -> Mode.STRAIGHT_ALPHA;
            case OTHER -> null;
        };
    }

    private static final Pattern MAIN = Pattern.compile("(?m)^[ \\t]*void[ \\t]+main[ \\t]*\\([ \\t]*(?:void)?[ \\t]*\\)");
    private static final Pattern OUTPUT = Pattern.compile("\\bout\\s+vec4\\s+fragColor\\s*;");
    static final String MARKER = "aurora_gradient_dither";

    /** The original {@code main} under another name, kept when the twin compiles. */
    private static final String MAIN_SWITCH = """

            #if defined(AURORA_GRADIENT_DITHER) && !defined(OIT_ALPHA_ONLY) && !defined(OIT_ACCUMULATE)
            #define AURORA_GRADIENT_DITHER_ACTIVE
            void aurora_gradient_dither_main()
            #else
            void main()
            #endif
            """;

    /**
     * The new {@code main}: the 8x8 Bayer rank of the pixel, built from the bits of its
     * coordinates rather than read from a 64-entry table (some drivers copy a dynamically
     * indexed constant array into every invocation), centred on zero, in 8-bit steps.
     *
     * <p>Opaque passes round to the step themselves, so what is stored does not depend on how
     * the driver converts float to 8-bit: AMD's exports colour through a truncated half float,
     * which moves its rounding point by up to an eighth of a step (measured 2026-11-20 on an
     * RX 9070 XT), and would otherwise turn the darkest offsets into a step down. Blended passes
     * cannot see the colour beneath, so they add the offset and leave rounding to the blend;
     * they use the matrix transposed, so their error does not line up with the opaque pass's.
     */
    private static final String EPILOGUE = """

            #ifdef AURORA_GRADIENT_DITHER_ACTIVE
            void main() {
                aurora_gradient_dither_main();
                ivec2 cell = ivec2(gl_FragCoord.xy) & 7;
            #if AURORA_GRADIENT_DITHER == 2
                cell = cell.yx;
            #endif
                int mixed = cell.x ^ cell.y;
                int rank = ((mixed & 1) << 5) | ((cell.y & 1) << 4) | ((mixed & 2) << 2)
                        | ((cell.y & 2) << 1) | ((mixed & 4) >> 1) | ((cell.y & 4) >> 2);
                float offset = (float(rank) + 0.5) / 64.0 - 0.5;
            #if AURORA_GRADIENT_DITHER == 2
                vec3 headroom = clamp(min(fragColor.rgb, 1.0 - fragColor.rgb), 0.0, 1.0) * 510.0;
                fragColor.rgb += offset / 255.0 * min(vec3(1.0 / max(fragColor.a, 1.0 / 4096.0)), headroom);
            #else
                fragColor.rgb = floor(fragColor.rgb * 255.0 + offset + 0.5) / 255.0;
            #endif
            }
            #endif
            """;

    /**
     * The rank the epilogue gives the pixel in column {@code x}, row {@code y} of its 8x8 cell
     * (0 to 63, each once per cell): the same bit arithmetic, for tests.
     */
    static int bayerRank(int x, int y) {
        int mixed = (x ^ y) & 7;
        y &= 7;
        return ((mixed & 1) << 5) | ((y & 1) << 4) | ((mixed & 2) << 2)
                | ((y & 2) << 1) | ((mixed & 4) >> 1) | ((y & 4) >> 2);
    }

    /**
     * The fragment source with the dither added behind {@link #DEFINE}, or the source unchanged
     * when it does not have exactly one {@code main} and a {@code vec4 fragColor} output (a
     * resource pack's replacement shader, say) or is already patched.
     */
    public static String patch(String source) {
        if (source == null || source.contains(MARKER) || !OUTPUT.matcher(source).find()) return source;
        Matcher main = MAIN.matcher(source);
        if (!main.find()) return source;
        int start = main.start(), end = main.end();
        if (main.find()) return source;
        String body = source.substring(0, start) + MAIN_SWITCH + source.substring(end);
        return body + (body.endsWith("\n") ? "" : "\n") + EPILOGUE;
    }
}
