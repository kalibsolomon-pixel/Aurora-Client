package com.aurora.client.util;

import com.aurora.client.config.AuroraConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackTweaksMathTest {

    @Test void motionBlurBlendIsZeroWhenOffAndFrameRateIndependent() {
        assertEquals(0.0f, PackTweaksMath.motionBlurBlend(0.0, 1.0 / 60.0));
        assertEquals(0.0f, PackTweaksMath.motionBlurBlend(-0.2, 1.0 / 60.0));
        assertEquals(0.5f, PackTweaksMath.motionBlurBlend(0.5, 1.0 / 60.0), 1e-4f);
        float at120 = PackTweaksMath.motionBlurBlend(0.5, 1.0 / 120.0);
        assertEquals(0.5f, at120 * at120, 1e-3f, "two 120 FPS frames should decay like one 60 FPS frame");
        assertTrue(PackTweaksMath.motionBlurBlend(5.0, 1.0 / 60.0) <= 0.851f);
    }

    @Test void postEffectActiveOnlyWhenNonNeutral() {
        assertFalse(PackTweaksMath.isPostEffectActive(0.0, 1.0));
        assertTrue(PackTweaksMath.isPostEffectActive(0.25, 1.0));
        assertTrue(PackTweaksMath.isPostEffectActive(0.0, 1.3));
        assertTrue(PackTweaksMath.isPostEffectActive(0.0, 0.0));
    }

    @Test void localTimeAndWeatherBounds() {
        assertEquals(0, PackTweaksMath.clampTimeTicks(-100));
        assertEquals(6000, PackTweaksMath.clampTimeTicks(6000));
        assertEquals(23999, PackTweaksMath.clampTimeTicks(50000));

        assertEquals(0.0f, PackTweaksMath.rainLevel(AuroraConfig.LocalWeather.CLEAR));
        assertEquals(0.0f, PackTweaksMath.thunderLevel(AuroraConfig.LocalWeather.CLEAR));
        assertEquals(1.0f, PackTweaksMath.rainLevel(AuroraConfig.LocalWeather.RAIN));
        assertEquals(0.0f, PackTweaksMath.thunderLevel(AuroraConfig.LocalWeather.RAIN));
        assertEquals(1.0f, PackTweaksMath.rainLevel(AuroraConfig.LocalWeather.THUNDER));
        assertEquals(1.0f, PackTweaksMath.thunderLevel(AuroraConfig.LocalWeather.THUNDER));
    }

    @Test void customSkyAndStarsComputeExpectedValues() {
        assertEquals(0x123456, PackTweaksMath.skyRgb(AuroraConfig.CustomSkyMode.VANILLA, 0xFF0000, 0x123456));
        assertEquals(0xABCDEF, PackTweaksMath.skyRgb(AuroraConfig.CustomSkyMode.CUSTOM_COLOR, 0xFFABCDEF, 0x123456));
        assertEquals(0, PackTweaksMath.sunriseSunsetArgb(AuroraConfig.CustomSkyMode.CLEAR_DAY, 0x55FF0000));
        assertEquals(0.0f, PackTweaksMath.starBrightness(0.8f, AuroraConfig.CustomSkyMode.AURORA_NIGHT, 2.0, false));
        assertTrue(PackTweaksMath.starBrightness(0.0f, AuroraConfig.CustomSkyMode.AURORA_NIGHT, 1.0, true) >= 0.69f);
    }

    @Test void fireAndHurtCamClampCleanly() {
        assertEquals(-0.50, PackTweaksMath.clampFireHeight(-2.0));
        assertEquals(0.20, PackTweaksMath.clampFireHeight(1.0));
        assertEquals(0x00FFFFFF, PackTweaksMath.fireColor(0.0));
        assertEquals(0xFFFFFFFF, PackTweaksMath.fireColor(1.0));
        assertEquals(0xE6FFFFFF, PackTweaksMath.fireColor(0.9));
        assertEquals(0.0, PackTweaksMath.clampHurtCamStrength(-1.0));
        assertEquals(2.0, PackTweaksMath.clampHurtCamStrength(5.0));
    }
}
