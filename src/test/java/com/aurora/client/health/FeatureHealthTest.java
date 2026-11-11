package com.aurora.client.health;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler.ErrorAction;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A mixin that cannot apply turns its feature off for the session and nothing else (ADR 0004). */
class FeatureHealthTest {
    /** Stands in for AuroraConfig: gates are public boolean fields found by name. */
    public static final class FakeConfig {
        public boolean zoomEnabled = true;
        public boolean hitregEnabled = true;
        public boolean nametagPingEnabled = false;
        public boolean nametagTotemPopsEnabled = true;
        public boolean noFogEnabled = true;
    }

    @BeforeEach void clean() {
        MixinHealth.resetForTests();
        FeatureHealth.resetForTests();
    }

    @AfterEach void restore() {
        MixinHealth.resetForTests();
        FeatureHealth.resetForTests();
    }

    private static void fail(String mixin) {
        MixinHealth.noteFailure(mixin, "some.Target", MixinHealth.Cause.APPLY_FAILED, "injection point not found");
    }

    @Test void aHealthySessionChangesNothing() {
        FakeConfig cfg = new FakeConfig();
        FeatureHealth.enforce(cfg);
        assertTrue(cfg.zoomEnabled);
        assertFalse(FeatureHealth.anyIssues());
        assertNull(FeatureHealth.noteFor("zoom"));
        assertTrue(FeatureHealth.rememberedValues().isEmpty());
        assertEquals(true, FeatureHealth.persistedValue("zoomEnabled", true));
    }

    @Test void aFailedMixinTurnsItsFeatureOffAndSaysWhy() {
        FakeConfig cfg = new FakeConfig();
        fail("GameRendererMixin");
        FeatureHealth.enforce(cfg);

        assertFalse(cfg.zoomEnabled, "the gate of a failed mixin is held off");
        assertTrue(cfg.noFogEnabled, "an unrelated feature is untouched");
        String note = FeatureHealth.noteFor("zoom");
        assertNotNull(note);
        assertTrue(note.contains("zooming the view"), note);
        assertEquals("Not working in this session: zooming the view", FeatureHealth.headlineFor("zoom"));
        assertNull(FeatureHealth.noteFor("no_fog"));
        assertEquals(java.util.List.of("zoom"), FeatureHealth.affectedFeatures());
    }

    @Test void turningItBackOnDoesNotStick() {
        FakeConfig cfg = new FakeConfig();
        fail("GameRendererMixin");
        FeatureHealth.enforce(cfg);
        cfg.zoomEnabled = true; // the player clicks the toggle, or a profile is applied
        FeatureHealth.enforce(cfg);
        assertFalse(cfg.zoomEnabled);
    }

    @Test void thePlayersValueIsWhatGetsSaved() {
        FakeConfig cfg = new FakeConfig();
        fail("PlayerEntityRendererMixin");
        FeatureHealth.enforce(cfg);

        assertFalse(cfg.nametagTotemPopsEnabled);
        assertEquals(true, FeatureHealth.persistedValue("nametagTotemPopsEnabled", cfg.nametagTotemPopsEnabled),
                "a switch that was on is saved as on");
        assertEquals(false, FeatureHealth.persistedValue("nametagPingEnabled", cfg.nametagPingEnabled),
                "a switch the player had off stays off on disk");
        assertEquals(java.util.Map.of("nametagTotemPopsEnabled", true), FeatureHealth.rememberedValues());
    }

    @Test void aResetForgetsTheRememberedValue() {
        FakeConfig cfg = new FakeConfig();
        fail("GameRendererMixin");
        FeatureHealth.enforce(cfg);
        FeatureHealth.forget("zoomEnabled"); // a feature reset wrote the default over the field
        assertEquals(false, FeatureHealth.persistedValue("zoomEnabled", false));
    }

    @Test void aMixinThatAppliesAfterAllGivesTheFeatureBack() {
        FakeConfig cfg = new FakeConfig();
        MixinHealth.noteFailure("GameRendererMixin", null, MixinHealth.Cause.NOT_APPLIED, "target did not load");
        FeatureHealth.enforce(cfg);
        assertFalse(cfg.zoomEnabled);

        MixinHealth.noteApplied("GameRendererMixin");
        FeatureHealth.enforce(cfg);
        assertTrue(cfg.zoomEnabled, "the player's value comes back");
        assertTrue(FeatureHealth.rememberedValues().isEmpty());
        assertNull(FeatureHealth.noteFor("zoom"));
    }

    @Test void aRealApplyFailureIsNotClearedByALaterApply() {
        fail("GameRendererMixin");
        MixinHealth.noteApplied("GameRendererMixin"); // applied to a second target, say
        assertTrue(MixinHealth.failed("GameRendererMixin"));
    }

    @Test void anyBetterHitregMixinTurnsTheWholeFeatureOff() {
        FakeConfig cfg = new FakeConfig();
        fail("hitreg.SoundMixin");
        FeatureHealth.enforce(cfg);
        assertFalse(cfg.hitregEnabled);
        assertNotNull(FeatureHealth.noteFor("better_hitreg"));
    }

    @Test void aMixinThatServesNoSingleFeatureIsReportedAsGeneral() {
        fail("ScreenshotMixin");
        assertTrue(FeatureHealth.anyIssues());
        assertTrue(FeatureHealth.affectedFeatures().isEmpty());
        assertEquals(1, FeatureHealth.generalIssues().size());
        assertEquals("ScreenshotMixin", FeatureHealth.generalIssues().get(0).mixin());
    }

    @Test void theErrorHandlerRecordsAuroraMixinsAndLeavesOtherModsAlone() {
        AuroraMixinErrorHandler handler = new AuroraMixinErrorHandler();
        RuntimeException cause = new RuntimeException("Critical injection failure: could not find target\nmore");

        ErrorAction ours = handler.onApplyError("net.minecraft.client.renderer.GameRenderer", cause,
                mixinNamed("com.aurora.client.mixin.GameRendererMixin"), ErrorAction.WARN);
        assertEquals(ErrorAction.WARN, ours);
        MixinHealth.Failure recorded = MixinHealth.failure("GameRendererMixin");
        assertNotNull(recorded);
        assertEquals(MixinHealth.Cause.APPLY_FAILED, recorded.cause());
        assertEquals("Critical injection failure: could not find target", recorded.detail());

        ErrorAction theirs = handler.onApplyError("some.Target", cause,
                mixinNamed("net.othermod.mixin.SomeMixin"), ErrorAction.ERROR);
        assertEquals(ErrorAction.ERROR, theirs, "another mod's required mixin must still stop the game");
        assertEquals(1, MixinHealth.failures().size());

        ErrorAction nested = handler.onPrepareError(null, cause,
                mixinNamed("com.aurora.client.mixin.hitreg.ArmorMixin"), ErrorAction.WARN);
        assertEquals(ErrorAction.WARN, nested);
        assertTrue(MixinHealth.failed("hitreg.ArmorMixin"));
    }

    @Test void theDisableOptionSkipsAMixinAndRecordsIt() {
        String before = System.getProperty(AuroraMixinPlugin.DISABLE_PROPERTY);
        System.setProperty(AuroraMixinPlugin.DISABLE_PROPERTY, "GameRendererMixin, hitreg.ArmorMixin");
        try {
            AuroraMixinPlugin plugin = new AuroraMixinPlugin();
            // onLoad also registers the error handler with Mixin, which is not
            // bootstrapped in a unit test; the property parsing is what matters here.
            try { plugin.onLoad(AuroraMixinPlugin.MIXIN_PACKAGE); } catch (Throwable ignored) {}
            assertFalse(plugin.shouldApplyMixin("a.B", "com.aurora.client.mixin.GameRendererMixin"));
            assertFalse(plugin.shouldApplyMixin("a.B", "com.aurora.client.mixin.hitreg.ArmorMixin"));
            assertTrue(plugin.shouldApplyMixin("a.B", "com.aurora.client.mixin.MouseMixin"));
            assertEquals(MixinHealth.Cause.DISABLED_BY_OPTION, MixinHealth.failure("GameRendererMixin").cause());
            assertTrue(FeatureHealth.noteFor("zoom").contains(AuroraMixinPlugin.DISABLE_PROPERTY));
        } finally {
            if (before == null) System.clearProperty(AuroraMixinPlugin.DISABLE_PROPERTY);
            else System.setProperty(AuroraMixinPlugin.DISABLE_PROPERTY, before);
        }
    }

    private static IMixinInfo mixinNamed(String className) {
        return (IMixinInfo) Proxy.newProxyInstance(IMixinInfo.class.getClassLoader(), new Class<?>[]{IMixinInfo.class},
                (proxy, method, args) -> method.getName().equals("getClassName") ? className : null);
    }
}
