package com.aurora.client.pulsar;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.ConfigDirectory;
import com.aurora.client.config.profile.Profile;
import com.aurora.client.config.profile.ProfileManager;
import com.google.gson.Gson;
import dev.phenomena.config.Config;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real config/profile schema without Minecraft or a live configuration directory. */
class PulsarSettingsIntegrationTest {
    private static final Gson GSON = new Gson();
    @TempDir Path directory;
    private AuroraConfig previous;
    private Path previousDirectory;

    @BeforeEach void isolate() throws Exception {
        previousDirectory = (Path) directoryField().get(null);
        previous = AuroraConfig.get();
        ConfigDirectory.set(directory);
        instanceField().set(null, new AuroraConfig());
    }

    @AfterEach void restore() throws Exception {
        assertTrue(AuroraConfig.flushPending(5000));
        instanceField().set(null, previous);
        directoryField().set(null, previousDirectory);
    }

    @Test void repeatedFeatureResetDoesNotShareNestedFactoryObjects() {
        Config defaults = new AuroraConfig().pulsarSettings;
        AuroraConfig live = AuroraConfig.get();
        live.pulsarSettings.feedback[0].volume = .91f;
        live.pulsarSettings.analytics.x = .8;
        AuroraConfig.resetByPrefix("pulsar");
        Config first = live.pulsarSettings;
        assertEquals(defaults.feedback[0].volume, first.feedback[0].volume);
        assertEquals(defaults.analytics.x, first.analytics.x);
        first.feedback[0].volume = .99f;
        first.analytics.x = .9;
        AuroraConfig.resetByPrefix("pulsar");
        assertNotSame(first, live.pulsarSettings);
        assertNotSame(first.feedback, live.pulsarSettings.feedback);
        assertNotSame(first.feedback[0], live.pulsarSettings.feedback[0]);
        assertNotSame(first.analytics, live.pulsarSettings.analytics);
        assertEquals(defaults.feedback[0].volume, live.pulsarSettings.feedback[0].volume);
        assertEquals(defaults.analytics.x, live.pulsarSettings.analytics.x);
    }

    @Test void profileCaptureAndRepeatedApplyIsolateFeedbackAndHudObjects() throws Exception {
        AuroraConfig live = AuroraConfig.get();
        live.pulsarSettings.feedback[0].volume = .61f;
        live.pulsarSettings.analytics.x = .31;
        Profile captured = capture();
        live.pulsarSettings.feedback[0].volume = .98f;
        live.pulsarSettings.analytics.x = .95;
        apply(captured);
        Config restored = live.pulsarSettings;
        assertEquals(.61f, restored.feedback[0].volume);
        assertEquals(.31, restored.analytics.x);
        restored.feedback[0].volume = .77f;
        restored.analytics.x = .88;
        apply(captured);
        assertNotSame(restored.feedback[0], live.pulsarSettings.feedback[0]);
        assertNotSame(restored.analytics, live.pulsarSettings.analytics);
        assertEquals(.61f, live.pulsarSettings.feedback[0].volume);
        assertEquals(.31, live.pulsarSettings.analytics.x);
    }

    @Test void oldAndNullSettingsProfilesGetIndependentDefaults() throws Exception {
        Profile old = profile("{}");
        Config defaults = new AuroraConfig().pulsarSettings;
        apply(old);
        AuroraConfig.get().pulsarSettings.feedback[1].pitch = 1.9f;
        AuroraConfig.get().pulsarSettings.direction.x = .93;
        apply(old);
        assertEquals(defaults.feedback[1].pitch, AuroraConfig.get().pulsarSettings.feedback[1].pitch);
        assertEquals(defaults.direction.x, AuroraConfig.get().pulsarSettings.direction.x);
        apply(profile("{\"pulsarSettings\":null}"));
        assertNotNull(AuroraConfig.get().pulsarSettings);
        assertEquals(defaults.perfectReachTarget, AuroraConfig.get().pulsarSettings.perfectReachTarget);
    }

    @Test void bridgeRetainsControllerConfigAndPreservesUnsavedDashboardEdits() {
        Config stable = new Config();
        AuroraConfig.get().pulsarSettings.perfectReachTarget = 4.5;
        PulsarConfigBridge.reload(stable);
        assertSame(stable, AuroraConfig.get().pulsarSettings);
        assertEquals(4.5, stable.perfectReachTarget);
        stable.feedback[0].volume = .72f;
        stable.analytics.x = .47;
        PulsarConfigBridge.reload(stable);
        assertSame(stable, AuroraConfig.get().pulsarSettings);
        assertEquals(.72f, stable.feedback[0].volume);
        assertEquals(.47, stable.analytics.x);
    }

    @Test void profileReloadUpdatesTheStableConfigWithoutChangingCapturedProfile() throws Exception {
        Config stable = new Config();
        PulsarConfigBridge.reload(stable);
        stable.feedback[0].pitch = 1.7f;
        Profile outgoing = capture();
        apply(profile("{\"pulsarSettings\":{\"perfectReachTarget\":4,\"feedback\":null}}"));
        assertNotSame(stable, AuroraConfig.get().pulsarSettings);
        PulsarConfigBridge.reload(stable);
        assertSame(stable, AuroraConfig.get().pulsarSettings);
        assertEquals(4, stable.perfectReachTarget);
        stable.feedback[0].pitch = .6f;
        apply(outgoing);
        PulsarConfigBridge.reload(stable);
        assertEquals(1.7f, stable.feedback[0].pitch);
    }

    @Test void profileValidationRepairsNullsAndBoundsNestedValues() throws Exception {
        apply(profile("{\"pulsarSettings\":{\"perfectReachTarget\":100,\"retention\":-1,"
                + "\"timingToleranceTicks\":-1,\"feedback\":[null],\"soundChannel\":null,"
                + "\"density\":null,\"analytics\":{\"x\":7,\"opacity\":-2,\"scale\":7}}}"));
        Config settings = AuroraConfig.get().pulsarSettings;
        assertEquals(10, settings.perfectReachTarget);
        assertEquals(1, settings.retention);
        assertEquals(0, settings.timingToleranceTicks);
        assertEquals(4, settings.feedback.length);
        for (Config.Feedback feedback : settings.feedback) assertNotNull(feedback);
        assertEquals(Config.SoundChannel.UI, settings.soundChannel);
        assertEquals(Config.Density.COMFORTABLE, settings.density);
        assertEquals(1, settings.analytics.x);
        assertEquals(.1f, settings.analytics.opacity);
        assertEquals(2, settings.analytics.scale);
    }

    @Test void legacyNestedSchemaMigratesAndKeepsPersistentHudDormant() throws Exception {
        apply(profile("{\"pulsarSettings\":{\"schemaVersion\":1,\"display\":\"BOTH\",\"hudOverlay\":true}}"));
        Config settings = AuroraConfig.get().pulsarSettings;
        assertEquals(Config.SCHEMA, settings.schemaVersion);
        assertTrue(settings.chatSummary);
        assertFalse(settings.hudOverlay);
        assertNull(settings.display);
    }

    private static Profile profile(String fields) {
        return GSON.fromJson("{\"name\":\"pulsar-test\",\"keybindVersion\":1,\"fields\":" + fields + "}", Profile.class);
    }

    private static Profile capture() throws Exception {
        Method method = ProfileManager.class.getDeclaredMethod("captureCurrent");
        method.setAccessible(true);
        return (Profile) method.invoke(ProfileManager.getInstance());
    }

    private static void apply(Profile profile) throws Exception {
        Method method = ProfileManager.class.getDeclaredMethod("applyProfile", Profile.class);
        method.setAccessible(true);
        try {
            method.invoke(ProfileManager.getInstance(), profile);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof Exception cause) throw cause;
            throw exception;
        }
    }

    private static Field instanceField() throws Exception {
        Field field = AuroraConfig.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        return field;
    }

    private static Field directoryField() throws Exception {
        Field field = ConfigDirectory.class.getDeclaredField("directory");
        field.setAccessible(true);
        return field;
    }
}
