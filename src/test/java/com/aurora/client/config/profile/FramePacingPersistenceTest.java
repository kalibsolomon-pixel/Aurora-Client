package com.aurora.client.config.profile;

import com.aurora.client.config.AuroraConfig;
import com.google.gson.Gson;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class FramePacingPersistenceTest {
    @TempDir static Path temporary;
    private static Object priorConfigDir;
    private static Field configDir;
    private static final Gson GSON = new Gson();

    @BeforeAll static void isolateAllPersistenceFromDeveloperSettings() throws Exception {
        configDir = FabricLoaderImpl.class.getDeclaredField("configDir");
        configDir.setAccessible(true);
        priorConfigDir = configDir.get(FabricLoaderImpl.INSTANCE);
        configDir.set(FabricLoaderImpl.INSTANCE, temporary);
        // These owners cache their paths at initialization. Fail before any
        // write if another test initialized either with a different location.
        for (var owner : new Class<?>[]{AuroraConfig.class, ProfileManager.class}) {
            String name = owner == AuroraConfig.class ? "CONFIG_PATH" : "PROFILES_DIR";
            Field path = owner.getDeclaredField(name);
            path.setAccessible(true);
            assertEquals(temporary, ((Path) path.get(null)).getParent());
        }
    }

    @AfterAll static void restoreLoaderPath() throws Exception {
        configDir.set(FabricLoaderImpl.INSTANCE, priorConfigDir);
    }

    @Test void factoryDefaultsAreStillTheAuditedDefaults() {
        AuroraConfig cfg = new AuroraConfig();
        assertEquals(AuroraConfig.PacingStrategy.PARK, cfg.framePacingStrategy);
        assertTrue(cfg.smoothFramePacer);
        assertEquals(500, cfg.framePacerSpinThresholdMicros);
        assertEquals(2000, cfg.framePacerParkThresholdMicros);
        assertFalse(cfg.framePacerLowCpuMode);
        assertTrue(cfg.lowLatencyRender);
        assertFalse(cfg.adaptiveRenderSleeping);
        assertEquals(0, cfg.frameCapFps);
    }

    @ParameterizedTest @EnumSource(AuroraConfig.PacingStrategy.class)
    void realConfigPersistenceAndProfileRestorePreserveEachStrategyAndModifier(AuroraConfig.PacingStrategy strategy) throws Exception {
        for (boolean lowCpu : new boolean[]{false, true}) {
            var live = AuroraConfig.get();
            live.framePacingStrategy = strategy;
            live.framePacerLowCpuMode = lowCpu;
            live.framePacerSpinThresholdMicros = 1750;
            live.framePacerParkThresholdMicros = 6500;
            AuroraConfig.saveBlocking();
            String disk = Files.readString(temporary.resolve("aurora.json"));
            assertTrue(disk.contains("\"framePacingStrategy\": \"" + strategy + "\""));
            live.framePacingStrategy = AuroraConfig.PacingStrategy.PARK;
            live.framePacerLowCpuMode = !lowCpu;
            live.framePacerSpinThresholdMicros = 500;
            AuroraConfig.load();
            assertSettings(strategy, lowCpu, 1750, 6500);

            var manager = ProfileManager.getInstance();
            manager.saveCurrentBlocking();
            Profile restored = GSON.fromJson(Files.readString(temporary.resolve("profiles/default.json")), Profile.class);
            AuroraConfig.get().framePacingStrategy = AuroraConfig.PacingStrategy.SPIN;
            AuroraConfig.get().framePacerLowCpuMode = !lowCpu;
            AuroraConfig.get().framePacerParkThresholdMicros = 2000;
            manager.applyProfile(restored);
            assertSettings(strategy, lowCpu, 1750, 6500);
        }
    }

    @Test void olderProfilesKeepDefaultsAndRemovedStrategyKeepsTheExistingNullFallback() {
        var manager = ProfileManager.getInstance();
        manager.applyProfile(GSON.fromJson("{\"name\":\"old\",\"fields\":{\"framePacerLowCpuMode\":true}}", Profile.class));
        assertSettings(AuroraConfig.PacingStrategy.PARK, true, 500, 2000);
        manager.applyProfile(GSON.fromJson("{\"name\":\"old\",\"fields\":{\"framePacingStrategy\":\"YIELD\",\"framePacerSpinThresholdMicros\":1234}}", Profile.class));
        assertNull(AuroraConfig.get().framePacingStrategy);
        assertEquals(1234, AuroraConfig.get().framePacerSpinThresholdMicros);
        assertFalse(AuroraConfig.get().framePacerLowCpuMode);
    }

    private static void assertSettings(AuroraConfig.PacingStrategy strategy, boolean lowCpu, int spin, int park) {
        AuroraConfig cfg = AuroraConfig.get();
        assertEquals(strategy, cfg.framePacingStrategy);
        assertEquals(lowCpu, cfg.framePacerLowCpuMode);
        assertEquals(spin, cfg.framePacerSpinThresholdMicros);
        assertEquals(park, cfg.framePacerParkThresholdMicros);
    }
}
