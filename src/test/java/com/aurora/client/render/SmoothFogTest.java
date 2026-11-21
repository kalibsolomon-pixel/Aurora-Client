package com.aurora.client.render;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.FeatureCatalog;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies Smooth Fog's shader patch against this line's real vanilla {@code fog.glsl}
 * and Sodium's fog layout, its curve endpoints, pipeline scoping, and fail-soft bindings.
 */
class SmoothFogTest {
    static String vanilla(String path) {
        try (InputStream stream = SmoothFogTest.class.getClassLoader().getResourceAsStream("assets/minecraft/" + path)) {
            return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test void vanillaAndSodiumFogArePatchedOnceAndIdempotently() {
        String fog = vanilla("shaders/include/fog.glsl");
        String patched = SmoothFog.patch(fog);
        assertTrue(patched.contains("#ifdef " + SmoothFog.DEFINE));
        assertTrue(patched.contains(SmoothFog.MARKER + " * " + SmoothFog.MARKER + " * (3.0 - 2.0 * " + SmoothFog.MARKER + ")"));
        assertTrue(patched.contains("#else\n    return (vertexDistance - fogStart) / (fogEnd - fogStart);\n#endif"));
        assertEquals(patched, SmoothFog.patch(patched), "patching twice changes nothing");

        // Sodium's fog.glsl uses the same linear_fog_value implementation.
        String sodiumFog = """
                float linear_fog_value(float vertexDistance, float fogStart, float fogEnd) {
                    if (vertexDistance <= fogStart) {
                        return 0.0;
                    } else if (vertexDistance >= fogEnd) {
                        return 1.0;
                    }

                    return (vertexDistance - fogStart) / (fogEnd - fogStart);
                }
                """;
        String patchedSodium = SmoothFog.patch(sodiumFog);
        assertTrue(patchedSodium.contains("#ifdef " + SmoothFog.DEFINE));
        assertEquals(patchedSodium, SmoothFog.patch(patchedSodium));
    }

    @Test void unmatchedSourcesStayUntouched() {
        assertNull(SmoothFog.patch(null));
        String custom = "#version 330\nvoid main() {}\n";
        assertSame(custom, SmoothFog.patch(custom));
    }

    @Test void curvePreservesExactVanillaEndpointsAndIsMonotonic() {
        assertEquals(0.0f, SmoothFog.eval(0.0f, 10.0f, 50.0f));
        assertEquals(0.0f, SmoothFog.eval(10.0f, 10.0f, 50.0f));
        assertEquals(0.5f, SmoothFog.eval(30.0f, 10.0f, 50.0f), 1e-6f);
        assertEquals(1.0f, SmoothFog.eval(50.0f, 10.0f, 50.0f));
        assertEquals(1.0f, SmoothFog.eval(99.0f, 10.0f, 50.0f));
        float prev = 0.0f;
        for (int i = 0; i <= 100; i++) {
            float d = 10.0f + 40.0f * (i / 100.0f);
            float v = SmoothFog.eval(d, 10.0f, 50.0f);
            assertTrue(v >= prev && v <= 1.0f, "monotonic at i=" + i);
            prev = v;
        }
    }

    @Test void onlyWorldPipelinesAreSmoothedAndDitheredTwinsCompose() {
        assertTrue(SmoothFog.appliesTo("minecraft", "pipeline/sky", "minecraft:core/sky", Set.of()));
        assertTrue(SmoothFog.appliesTo("minecraft", "pipeline/solid_terrain", "minecraft:core/terrain", Set.of()));
        assertTrue(SmoothFog.appliesTo("minecraft", "pipeline/entity_solid", "minecraft:core/entity", Set.of()));
        assertTrue(SmoothFog.appliesTo("sodium", "pipeline/solid_terrain", "sodium:blocks/block_layer_opaque", Set.of("USE_FOG")));
        assertTrue(SmoothFog.appliesTo("aurora", "gradient_dither/minecraft/pipeline/sky", "minecraft:core/sky", Set.of("AURORA_GRADIENT_DITHER")));
        assertTrue(SmoothFog.appliesTo("aurora", "gradient_dither/sodium/pipeline/solid_terrain", "sodium:blocks/block_layer_opaque", Set.of("AURORA_GRADIENT_DITHER")));

        assertFalse(SmoothFog.appliesTo("minecraft", "pipeline/gui", "minecraft:core/position_color", Set.of()));
        assertFalse(SmoothFog.appliesTo("minecraft", "pipeline/entity_outline_blit", "minecraft:core/entity", Set.of()));
        assertFalse(SmoothFog.appliesTo("minecraft", "pipeline/translucent_terrain", "minecraft:core/terrain", Set.of("OIT_ACCUMULATE")));
        assertFalse(SmoothFog.appliesTo("othermod", "pipeline/sky", "minecraft:core/sky", Set.of()));
    }

    @Test void featureIsCosmeticFailSoftAndStandsDownForShaderPacks() throws IOException {
        assertEquals(FeatureCatalog.FairPlay.COSMETIC, FeatureCatalog.require("smooth_fog").fairPlay());
        for (String mixin : List.of("SmoothFogSourceMixin", "SmoothFogPipelineMixin")) {
            var binding = FeatureCatalog.mixin(mixin);
            assertEquals(List.of("smooth_fog"), binding.features(), mixin);
            assertEquals(List.of("smoothFog"), binding.gates(), mixin);
            assertEquals(List.of("vulkanmod"), binding.yieldsTo(), mixin);
        }
        assertFalse(new AuroraConfig().smoothFog, "off in a fresh install");
        assertFalse(com.aurora.client.config.profile.ProfileFieldSet.EXCLUDED.contains("smoothFog"));

        for (String line : List.of("26.1", "26.2", "26.3")) {
            Path adapter = Path.of("adapters/minecraft-" + line + "/src/client/java/com/aurora/client");
            String pipelines = Files.readString(adapter.resolve("render/SmoothFogPipelines.java")).replace("\r\n", "\n");
            assertTrue(pipelines.contains("ShaderPackState.irisPackInUse()"), line);
            String mixin = Files.readString(adapter.resolve("mixin/SmoothFogPipelineMixin.java"));
            assertTrue(mixin.contains("priority = 1050"), line + ": runs after GradientDitherPipelineMixin");
        }
    }
}
