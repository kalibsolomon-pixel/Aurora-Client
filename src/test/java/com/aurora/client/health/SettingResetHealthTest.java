package com.aurora.client.health;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.ConfigDirectory;
import com.aurora.client.screen.setting.BooleanSetting;
import com.aurora.client.ui.interaction.SemanticFeedback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SettingResetHealthTest {
    @TempDir Path temporary;
    private boolean original;
    @BeforeEach void setup() {
        MixinHealth.resetForTests();
        FeatureHealth.resetForTests();
        ConfigDirectory.set(temporary);
        original = AuroraConfig.get().perfDebugEntryCache;
    }
    @AfterEach void restore() {
        AuroraConfig.saveBlocking();
        MixinHealth.resetForTests();
        FeatureHealth.resetForTests();
        AuroraConfig.get().perfDebugEntryCache = original;
    }

    @Test void resetUsesStoredPreferenceAndKeepsFailedGateOff() {
        var cfg = AuroraConfig.get();
        cfg.perfDebugEntryCache = true;
        MixinHealth.noteFailure("DebugScreenEntryCacheMixin", "target", MixinHealth.Cause.APPLY_FAILED, "test");
        FeatureHealth.enforce(cfg);
        var row = new BooleanSetting("Cache", () -> cfg.perfDebugEntryCache, v -> cfg.perfDebugEntryCache = v)
                .resetDefault("perfDebugEntryCache", () -> true);
        assertFalse(cfg.perfDebugEntryCache);
        assertFalse(row.isModified(), "session gating must not look like a changed preference");
        // Exercise a false factory default too: forgetting the remembered true is essential.
        row.resetDefault("perfDebugEntryCache", () -> false);
        assertTrue(row.isModified());
        var reset = row.controlsWithReset().getLast();
        assertTrue(reset.action().activate(SemanticFeedback.NONE, null));
        assertFalse(cfg.perfDebugEntryCache);
        assertEquals(false, FeatureHealth.persistedValue("perfDebugEntryCache", cfg.perfDebugEntryCache));
        assertFalse(row.isModified());
    }
}
