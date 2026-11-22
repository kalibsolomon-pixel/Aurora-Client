package com.aurora.client.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundVolumesTest {

    @Test void scaleStrictlyClampsBetweenZeroAndOneHundredPercent() {
        assertEquals(0.0f, SoundVolumes.clampScale(-0.5));
        assertEquals(0.0f, SoundVolumes.clampScale(0.0));
        assertEquals(0.35f, SoundVolumes.clampScale(0.35), 1e-5f);
        assertEquals(1.0f, SoundVolumes.clampScale(1.0));
        assertEquals(1.0f, SoundVolumes.clampScale(2.5), "never boosts above 100%");
        assertEquals(1.0f, SoundVolumes.clampScale(Double.NaN));
    }

    @Test void attenuateRespectsNullMuteAndCap() {
        assertEquals(0.8f, SoundVolumes.attenuate(0.8f, null), 1e-6f);
        assertEquals(0.0f, SoundVolumes.attenuate(0.8f, 0.0f), 1e-6f);
        assertEquals(0.4f, SoundVolumes.attenuate(0.8f, 0.5f), 1e-6f);
        assertEquals(0.8f, SoundVolumes.attenuate(0.8f, 3.0f), 1e-6f, "attenuation never amplifies");
        assertTrue(SoundVolumes.isNeutral(1.0));
        assertTrue(SoundVolumes.isNeutral(1.5));
        assertFalse(SoundVolumes.isNeutral(0.5));
    }

    @Test void humanizeProducesReadableLabels() {
        assertEquals("Ender Dragon — Growl", SoundVolumes.humanizeSoundId("minecraft:entity.ender_dragon.growl"));
        assertEquals("Portal — Ambient", SoundVolumes.humanizeSoundId("minecraft:block.portal.ambient"));
        assertEquals("Weather — Rain", SoundVolumes.humanizeSoundId("minecraft:weather.rain"));
    }

    @Test void recentSoundLogDeduplicatesAndCapsAtMax() {
        RecentSoundLog.clear();
        RecentSoundLog.record("minecraft:weather.rain");
        RecentSoundLog.record("minecraft:weather.rain");
        RecentSoundLog.record("minecraft:block.portal.ambient");
        RecentSoundLog.record("minecraft:weather.rain");
        assertEquals(List.of("minecraft:weather.rain", "minecraft:block.portal.ambient"), RecentSoundLog.snapshot());

        for (int i = 0; i < 25; i++) {
            RecentSoundLog.record("minecraft:test.sound_" + i);
        }
        List<String> snap = RecentSoundLog.snapshot();
        assertEquals(RecentSoundLog.MAX_ENTRIES, snap.size());
        assertEquals("minecraft:test.sound_24", snap.get(0));
        RecentSoundLog.clear();
    }
}
