package com.aurora.client.screen.setting;

import com.aurora.client.ui.component.Slider;
import com.aurora.client.ui.component.ToggleSwitch;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B rollout invariants: every PRODUCTION construction path selects
 * the canonical state mode, and the intentionally-legacy consumers stay
 * legacy. Tests the factories, not individual feature rows — the pilots
 * already prove the treatments themselves.
 */
class RolloutStatesTest {

    @Test
    void doubleSliderFactoryIsCanonical() {
        AtomicReference<Double> v = new AtomicReference<>(0.5);
        SliderSetting row = SliderSetting.of("R", v::get, v::set, 0.0, 1.0);
        assertTrue(row.slider.canonical(), "SliderSetting.of must produce canonical state channels");
    }

    @Test
    void intSliderFactoryIsCanonical() {
        AtomicReference<Integer> v = new AtomicReference<>(30);
        SliderSetting row = SliderSetting.ofInt("R", v::get, v::set, 0, 2000);
        assertTrue(row.slider.canonical(), "SliderSetting.ofInt must produce canonical state channels");
    }

    @Test
    void themeOpacityPathIsCanonical() {
        // ThemeOpacitySetting overrides createSlider — the factory-level
        // canonicalStates call must cover it too (the super constructor
        // applies it AFTER the override runs).
        AtomicReference<Double> v = new AtomicReference<>(0.5);
        ThemeOpacitySetting row = new ThemeOpacitySetting("R", v::get, v::set);
        assertTrue(row.slider.canonical(), "ThemeOpacitySetting must produce canonical state channels");
    }

    @Test
    void themeOpacityRetainsFingerprintDelegation() {
        AtomicReference<Double> v = new AtomicReference<>(0.5);
        ThemeOpacitySetting row = new ThemeOpacitySetting("R", v::get, v::set);
        // SliderSetting delegates to the slider's disabled bit — the
        // opacity subclass must not lose that override.
        int enabled = row.shapeFingerprint();
        row.slider.disabled(true);
        assertNotEquals(enabled, row.shapeFingerprint(),
                "disabled flip must invalidate the row cache through delegation");
    }

    @Test
    void directSliderConstructionStaysLegacy() {
        // The theme-preview mock constructs Slider directly — that is the
        // intentionally-legacy path (no canonicalStates in a mock).
        AtomicReference<Double> v = new AtomicReference<>(0.6);
        Slider mock = new Slider(v::get, v::set, 0, 1, 0.01);
        assertFalse(mock.canonical(), "direct construction stays legacy by design");
    }

    @Test
    void productionBooleanToggleIsInteractivePreviewToggleIsNot() {
        AtomicReference<Boolean> v = new AtomicReference<>(false);
        BooleanSetting row = new BooleanSetting("R", v::get, v::set);
        assertFalse(row.togglePreviewMode(), "production toggles respond to the pointer");

        // The preview mock opts out — a decorative toggle never claims
        // interactivity (rollout rule: mocks stay mocks).
        ToggleSwitch mock = new com.aurora.client.ui.component.ToggleSwitch(() -> true, b -> {}).previewMode();
        assertTrue(mock.isPreviewMode());
    }
}
