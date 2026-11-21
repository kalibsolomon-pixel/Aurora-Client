package com.aurora.client.render;

import com.aurora.client.SourceTree;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.FeatureCatalog;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gradient Dithering's shader patch and pipeline choice against this line's real vanilla
 * shaders. The pixel behaviour is checked on a GPU in {@link GradientDitherShaderGpuTest}.
 */
class GradientDitherTest {
    private static final Pattern MAIN = Pattern.compile("(?m)^[ \\t]*void[ \\t]+main[ \\t]*\\(");

    static String vanilla(String path) {
        try (InputStream stream = GradientDitherTest.class.getClassLoader().getResourceAsStream("assets/minecraft/" + path)) {
            return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** The vanilla files this line has; the clouds shader was renamed in 26.3. */
    static List<String> vanillaShaders() {
        List<String> found = new ArrayList<>();
        for (String id : GradientDither.SHADERS) {
            if (!id.startsWith("minecraft:")) continue;
            String path = id.substring("minecraft:".length());
            if (vanilla(path) != null) found.add(path);
        }
        return found;
    }

    @Test void everyVanillaWorldShaderIsPatchedOnce() {
        List<String> shaders = vanillaShaders();
        assertTrue(shaders.containsAll(List.of("shaders/core/sky.fsh", "shaders/core/position_color.fsh",
                "shaders/core/position_tex_color.fsh", "shaders/core/terrain.fsh")), shaders.toString());
        assertEquals(5, shaders.size(), "one clouds shader per line: " + shaders);
        for (String path : shaders) {
            String source = vanilla(path), patched = GradientDither.patch(source);
            assertTrue(patched.contains(GradientDither.MARKER), path + " was not patched");
            assertEquals(2, MAIN.matcher(patched).results().count(), path + ": the original main plus the dithered one");
            assertTrue(patched.contains("#else\nvoid main()\n#endif"), path + ": without the define the original main keeps its name");
            assertEquals(patched, GradientDither.patch(patched), path + ": patching twice changes nothing");
            assertTrue(patched.startsWith(source.substring(0, source.indexOf('\n') + 1)), path + ": #version stays the first line");
        }
    }

    @Test void sourcesItCannotReadAreLeftAlone() {
        assertNull(GradientDither.patch(null));
        String noOutput = "#version 330\nout vec4 color;\nvoid main() { color = vec4(1); }\n";
        assertSame(noOutput, GradientDither.patch(noOutput));
        String twoMains = "#version 330\nout vec4 fragColor;\nvoid main() {}\n#ifdef X\nvoid main() {}\n#endif\n";
        assertSame(twoMains, GradientDither.patch(twoMains));
    }

    @Test void theOffsetStaysInsideHalfAStep() {
        String patched = GradientDither.patch(vanilla("shaders/core/sky.fsh"));
        assertTrue(patched.contains("float offset = (float(rank) + 0.5) / 64.0 - 0.5;"));
        // Opaque passes round to the 8-bit step in the shader, whatever the driver's conversion.
        assertTrue(patched.contains("fragColor.rgb = floor(fragColor.rgb * 255.0 + offset + 0.5) / 255.0;"));
        // Blended passes divide by alpha but never by more than keeps the source inside [0, 1].
        assertTrue(patched.contains("vec3 headroom = clamp(min(fragColor.rgb, 1.0 - fragColor.rgb), 0.0, 1.0) * 510.0;\n"
                + "    fragColor.rgb += offset / 255.0 * min(vec3(1.0 / max(fragColor.a, 1.0 / 4096.0)), headroom);"));
        assertTrue(patched.contains("!defined(OIT_ALPHA_ONLY) && !defined(OIT_ACCUMULATE)"));
    }

    /** The 8x8 Bayer matrix by its recursive definition: M(2n) = [[4M, 4M+2], [4M+3, 4M+1]]. */
    private static int[][] bayer(int size) {
        int[][] m = {{0, 2}, {3, 1}};
        while (m.length < size) {
            int n = m.length;
            int[][] next = new int[2 * n][2 * n];
            for (int y = 0; y < 2 * n; y++) for (int x = 0; x < 2 * n; x++) {
                next[y][x] = 4 * m[y % n][x % n] + new int[][]{{0, 2}, {3, 1}}[y / n][x / n];
            }
            m = next;
        }
        return m;
    }

    @Test void theRankIsTheBayerMatrixBuiltFromCoordinateBits() {
        int[][] matrix = bayer(8);
        boolean[] seen = new boolean[64];
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            assertEquals(matrix[y][x], GradientDither.bayerRank(x, y), x + "," + y);
            assertEquals(matrix[y][x], GradientDither.bayerRank(x + 8, y + 16), "the pattern repeats every 8 pixels");
            seen[GradientDither.bayerRank(x, y)] = true;
        }
        for (boolean rank : seen) assertTrue(rank, "every rank once per cell, so each cell averages to zero");
        // The shader computes the same expression as bayerRank.
        String patched = GradientDither.patch(vanilla("shaders/core/sky.fsh"));
        assertTrue(patched.contains("int mixed = cell.x ^ cell.y;\n"
                + "    int rank = ((mixed & 1) << 5) | ((cell.y & 1) << 4) | ((mixed & 2) << 2)\n"
                + "            | ((cell.y & 2) << 1) | ((mixed & 4) >> 1) | ((cell.y & 4) >> 2);"), patched);
        assertTrue(patched.contains("#if AURORA_GRADIENT_DITHER == 2\n    cell = cell.yx;\n#endif"), "blended passes transpose it");
    }

    @Test void onlyWorldPipelinesGetATwin() {
        var none = GradientDither.Blend.NONE;
        var alpha = GradientDither.Blend.STRAIGHT_ALPHA;
        assertEquals(GradientDither.Mode.OPAQUE, GradientDither.modeFor("minecraft", "pipeline/sky", "minecraft:core/sky", Set.of(), true, none));
        assertEquals(GradientDither.Mode.STRAIGHT_ALPHA, GradientDither.modeFor("minecraft", "pipeline/sunrise_sunset", "minecraft:core/position_color", Set.of(), true, alpha));
        assertEquals(GradientDither.Mode.OPAQUE, GradientDither.modeFor("sodium", "pipeline/solid_terrain", "sodium:blocks/block_layer_opaque", Set.of("USE_FOG"), true, none));
        // GUI pipelines share position_color and position_tex_color but never get a twin.
        for (String gui : List.of("pipeline/gui", "pipeline/gui_textured", "pipeline/gui_text", "pipeline/debug_quads", "pipeline/stars")) {
            assertNull(GradientDither.modeFor("minecraft", gui, "minecraft:core/position_color", Set.of(), true, alpha), gui);
        }
        assertNull(GradientDither.modeFor("minecraft", "pipeline/translucent_terrain", "minecraft:core/terrain", Set.of("OIT_ACCUMULATE"), true, alpha));
        assertNull(GradientDither.modeFor("minecraft", "pipeline/sky", "minecraft:core/sky", Set.of(), false, none));
        assertNull(GradientDither.modeFor("minecraft", "pipeline/sky", "minecraft:core/sky", Set.of(), true, GradientDither.Blend.OTHER));
        assertNull(GradientDither.modeFor("sodium", "pipeline/solid_terrain", "sodium:blocks/other", Set.of(), true, none));
        assertNull(GradientDither.modeFor("othermod", "pipeline/sky", "minecraft:core/sky", Set.of(), true, none));
        // A world pipeline a resource pack or mod points at an unpatched shader keeps it.
        assertNull(GradientDither.modeFor("minecraft", "pipeline/sky", "minecraft:core/stars", Set.of(), true, none));
    }

    @Test void everyLineSwapsPipelinesOnlyWhileOnAndWithoutAShaderPack() throws IOException {
        for (String line : List.of("26.1", "26.2", "26.3")) {
            Path adapter = Path.of("adapters/minecraft-" + line + "/src/client/java/com/aurora/client");
            String pipelines = Files.readString(adapter.resolve("render/GradientDitherPipelines.java")).replace("\r\n", "\n");
            assertTrue(pipelines.contains("if (!AuroraConfig.get().gradientDither || pipeline instanceof Dithered || !RenderSystem.isOnRenderThread()) return pipeline;"), line);
            assertTrue(pipelines.contains("if (twin == KEEP || standingDown()) return pipeline;"), line + ": an Iris shader pack keeps every original");
            assertTrue(pipelines.contains("boolean pack = ShaderPackState.irisPackInUse();"), line);
            assertTrue(pipelines.contains("return twin.ready ? twin.pipeline : pipeline;"), line + ": a twin that did not compile draws as the original");
            String source = Files.readString(adapter.resolve("mixin/GradientDitherSourceMixin.java"));
            assertTrue(source.contains("GradientDitherPipelines.patchSource(location, source)"), line);
        }
        assertTrue(SourceTree.read("render/ShaderPackState.java").contains("\"isShaderPackInUse\""));
    }

    @Test void theFeatureIsCosmeticAndFailSoft() {
        assertEquals(FeatureCatalog.FairPlay.COSMETIC, FeatureCatalog.require("gradient_dither").fairPlay());
        for (String mixin : List.of("GradientDitherSourceMixin", "GradientDitherPipelineMixin")) {
            var binding = FeatureCatalog.mixin(mixin);
            assertEquals(List.of("gradient_dither"), binding.features(), mixin);
            assertEquals(List.of("gradientDither"), binding.gates(), mixin + ": a broken half turns the feature off");
            assertEquals(List.of("vulkanmod"), binding.yieldsTo(), mixin);
        }
        assertTrue(new AuroraConfig().gradientDither, "on in a fresh install");
        assertFalse(com.aurora.client.config.profile.ProfileFieldSet.EXCLUDED.contains("gradientDither"));
    }
}
