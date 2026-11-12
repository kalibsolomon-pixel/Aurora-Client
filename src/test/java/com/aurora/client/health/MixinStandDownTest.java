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
        MixinHealth.noteStoodDown("LoadingOverlayMixin", "handled by somemod");
        assertTrue(MixinHealth.stoodDown("LoadingOverlayMixin"));
        assertFalse(MixinHealth.failed("LoadingOverlayMixin"));
        assertFalse(FeatureHealth.anyIssues());
        assertTrue(FeatureHealth.blockedGates().isEmpty());
        assertEquals("All 80 Aurora mixins applied or stood down (1 stood down).",
                MixinAudit.summary(80, MixinHealth.standDowns().size()));
        assertEquals("All 80 Aurora mixins applied.", MixinAudit.summary(80, 0));
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
