package com.aurora.client.health;

import com.aurora.client.config.profile.ProfileFieldSet;
import com.aurora.client.feature.FeatureCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A mixin that yields to another mod is not applied and is not a failure (ADR 0009). */
class MixinStandDownTest {
    @BeforeEach void clean() {
        MixinHealth.resetForTests();
        FeatureHealth.resetForTests();
    }

    @AfterEach void restore() {
        MixinHealth.resetForTests();
        FeatureHealth.resetForTests();
    }

    @Test void aBindingStandsDownForTheModItYieldsToAndWithoutTheModItPatches() {
        var vanilla = new FeatureCatalog.MixinBinding("X", "x", List.of(), List.of(), List.of("sodium", "embeddium"), "");
        assertNull(vanilla.standDownReason(Set.of()::contains));
        assertEquals("handled by embeddium", vanilla.standDownReason(Set.of("embeddium")::contains));

        var patch = new FeatureCatalog.MixinBinding("Y", "y", List.of(), List.of(), List.of(), "sodium");
        assertEquals("only patches sodium, which is not installed", patch.standDownReason(Set.of()::contains));
        assertNull(patch.standDownReason(Set.of("sodium")::contains));
    }

    @Test void aStoodDownMixinBlocksNoGateAndReportsNoIssue() {
        MixinHealth.noteStoodDown("FontManagerDeferredMixin", "handled by somemod");
        assertTrue(MixinHealth.stoodDown("FontManagerDeferredMixin"));
        assertFalse(MixinHealth.failed("FontManagerDeferredMixin"));
        assertFalse(FeatureHealth.anyIssues());
        assertTrue(FeatureHealth.blockedGates().isEmpty());
        assertEquals("All 80 Aurora mixins applied or stood down (1 stood down).",
                MixinAudit.summary(80, MixinHealth.standDowns().size()));
        assertEquals("All 80 Aurora mixins applied.", MixinAudit.summary(80, 0));
    }

    /** ModernFix 5.27.20 only makes unihex providers lazy; font set preparation stays Aurora's. */
    @Test void fontPreparationRunsAlongsideModernFix() {
        for (String name : List.of("FontManagerDeferredMixin", "FontSetDeferredMixin")) {
            var binding = FeatureCatalog.mixin(name);
            assertNull(binding.standDownReason(Set.of()::contains));
            assertNull(binding.standDownReason(Set.of("modernfix")::contains));
        }
    }

    /** SDF text uses each atlas page's own size, so ImmediatelyFast's larger font pages need no stand-down. */
    @Test void sdfTextYieldsOnlyToModsThatOwnTextRendering() {
        for (String name : List.of("SdfFontManagerMixin", "SdfGlyphRenderStateMixin", "SdfGuiTextMixin")) {
            var binding = FeatureCatalog.mixin(name);
            assertNull(binding.standDownReason(Set.of("immediatelyfast", "modernfix", "sodium")::contains));
            assertEquals("handled by modernui", binding.standDownReason(Set.of("modernui")::contains));
            assertEquals("handled by caxton", binding.standDownReason(Set.of("caxton")::contains));
        }
    }

    @Test void dynamicFpsOwnsTheUnfocusedFrameLimit() {
        var limit = FeatureCatalog.mixin("FramerateLimitUnfocusedMixin");
        assertNull(limit.standDownReason(Set.of("sodium", "immediatelyfast")::contains));
        assertEquals("handled by dynamic_fps", limit.standDownReason(Set.of("dynamic_fps")::contains));
    }

    @Test void entityCullingOwnsEveryOcclusionHook() {
        for (String name : List.of("OcclusionFrameMixin", "OcclusionBodyMixin", "OcclusionChestMixin",
                "OcclusionDirtyMixin", "OcclusionLeashMixin")) {
            var binding = FeatureCatalog.mixin(name);
            assertEquals(List.of("perfEntityOcclusion"), binding.gates());
            assertEquals("handled by entityculling", binding.standDownReason(Set.of("entityculling")::contains));
            assertNull(binding.standDownReason(Set.of("sodium", "moreculling", "immediatelyfast",
                    "entity_model_features")::contains));
        }
    }

    @Test void glDispatchAndSmoothFogYieldToVulkanMod() {
        for (String name : List.of("GlTextureMipCacheMixin", "GlCommandEncoderDispatchMixin",
                "FrameBufferCacheMixin", "VertexArrayCacheSeparateMixin")) {
            var binding = FeatureCatalog.mixin(name);
            assertEquals(List.of("perfGlDispatch"), binding.gates());
            assertEquals("handled by vulkanmod", binding.standDownReason(Set.of("vulkanmod")::contains));
            assertNull(binding.standDownReason(Set.of("sodium", "iris", "immediatelyfast", "moreculling",
                    "entityculling")::contains));
        }
        for (String name : List.of("SmoothFogSourceMixin", "SmoothFogPipelineMixin")) {
            var binding = FeatureCatalog.mixin(name);
            assertEquals(List.of("smoothFog"), binding.gates());
            assertEquals("handled by vulkanmod", binding.standDownReason(Set.of("vulkanmod")::contains));
            assertNull(binding.standDownReason(Set.of("sodium", "iris", "immediatelyfast")::contains));
        }
    }

    @Test void guiAndTextOptimizationsYieldIndependently() {
        var text = FeatureCatalog.mixin("FormattedBidiReorderMixin");
        var layers = List.of("GuiRenderStateNodeMixin", "GuiRenderStateLayeringMixin", "GuiRendererElementOrderMixin");
        for (String owner : List.of("polytone", "gnetum")) {
            var loaded = Set.of(owner);
            assertNull(text.standDownReason(loaded::contains));
            for (String layer : layers) {
                assertEquals("handled by " + owner, FeatureCatalog.mixin(layer).standDownReason(loaded::contains));
            }
        }
        for (String owner : List.of("modernui", "caxton")) {
            var loaded = Set.of(owner);
            assertEquals("handled by " + owner, text.standDownReason(loaded::contains));
            for (String layer : layers) assertNull(FeatureCatalog.mixin(layer).standDownReason(loaded::contains));
        }
    }

    @Test void ephemeraOwnsEnginePerformanceHooksWhileKeepingAuroraFeaturesActive() {
        Set<String> loaded = Set.of("ephemera");
        for (String name : List.of(
                "LevelLoadTrackerMixin",
                "OcclusionFrameMixin", "OcclusionBodyMixin", "OcclusionChestMixin",
                "OcclusionDirtyMixin", "OcclusionLeashMixin",
                "SoundEngineListenerMixin", "DebugScreenEntryCacheMixin",
                "GlTextureMipCacheMixin", "GlCommandEncoderDispatchMixin",
                "FrameBufferCacheMixin", "VertexArrayCacheSeparateMixin",
                "SpriteContentsMipsAccessor", "TextureAtlasFreeMipsMixin",
                "GuiRenderStateNodeMixin", "GuiRenderStateLayeringMixin", "GuiRendererElementOrderMixin",
                "FormattedBidiReorderMixin", "FramerateLimitUnfocusedMixin")) {
            assertEquals("handled by ephemera", FeatureCatalog.mixin(name).standDownReason(loaded::contains),
                    name + " must yield to ephemera");
        }
        // Aurora keeps SDF text, world shader cosmetics, deferred Aurora fonts, and GUI item baking/glint/flat-faces.
        for (String kept : List.of(
                "SdfFontManagerMixin", "SdfGlyphRenderStateMixin", "SdfGuiTextMixin",
                "GradientDitherSourceMixin", "GradientDitherPipelineMixin",
                "SmoothFogSourceMixin", "SmoothFogPipelineMixin",
                "FontManagerDeferredMixin", "FontSetDeferredMixin",
                "GuiItemAtlasBakingMixin", "GuiItemAtlasGlintMixin", "GuiItemAtlasFlatFacesMixin")) {
            assertNull(FeatureCatalog.mixin(kept).standDownReason(loaded::contains),
                    kept + " must remain active alongside ephemera");
        }
    }

    @Test void everyPerformanceGateIsAPerfFieldKeptOutOfProfiles() {
        for (FeatureCatalog.MixinBinding b : FeatureCatalog.mixins()) {
            if (!b.features().contains("performance")) continue;
            assertFalse(b.gates().isEmpty(), b.mixin() + " needs a perf* gate (ADR 0009)");
            for (String gate : b.gates()) {
                assertTrue(gate.startsWith(ProfileFieldSet.PERFORMANCE_PREFIX), gate);
                assertFalse(ProfileFieldSet.isProfileScoped(gate), gate + " must stay out of profiles");
            }
        }
    }
}
