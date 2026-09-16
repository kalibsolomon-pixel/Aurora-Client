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

    // ------------------------------------------------------------------
    // Enum rollout invariants (2026-09-16): canonical states are the
    // component's only behavior — no opt-in, no legacy escape.
    // ------------------------------------------------------------------

    private enum RolloutEnum { A, B }

    @Test
    void enumConstructionIsCanonicalWithNoOptInEscape() {
        AtomicReference<RolloutEnum> v = new AtomicReference<>(RolloutEnum.A);
        EnumSetting<RolloutEnum> row = new EnumSetting<>("R", RolloutEnum.class, v::get, v::set);
        float first = row.hoverAnimator().update(true);
        assertTrue(first < 0.5f, "every construction animates from rest — canonical by default");
        // The pilot's opt-in mechanism is retired with the flag (the
        // Button/ToggleSwitch end state). Reintroducing an opt-in seam is a
        // deliberate decision that must update this pin.
        assertThrows(NoSuchMethodException.class,
                () -> EnumSetting.class.getMethod("canonicalStates"),
                "canonicalStates() was the pilot's isolation mechanism — the rollout removed it");
    }

    @Test
    void productionEnumInventoryHoldsAtTwentySixRows() {
        // Source-level inventory pin: the docs record "all 26 production
        // EnumSetting rows construct in FeatureRegistry" (FeatureRegistry
        // itself is not headless-loadable — its lambdas capture the live
        // config). Any added/removed enum row must consciously update this
        // count together with ARCHITECTURE.md.
        try {
            String src = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                    "src/client/java/com/aurora/client/screen/FeatureRegistry.java")));
            int count = src.split("new EnumSetting<>", -1).length - 1;
            assertEquals(26, count,
                    "production EnumSetting construction sites (inventory drift guard)");
        } catch (java.io.IOException e) {
            fail("FeatureRegistry.java not readable from the test working dir: " + e.getMessage());
        }
    }

    @Test
    void colorSettingConstructionIsCanonicalWithDirectSwatchesLegacy() {
        AtomicReference<Integer> v = new AtomicReference<>(0xFF808080);
        ColorSetting row = new ColorSetting("R", v::get, v::set);
        try {
            var swField = ColorSetting.class.getDeclaredField("swatch");
            swField.setAccessible(true);
            var sw = swField.get(row);
            assertTrue((boolean) sw.getClass().getMethod("canonical").invoke(sw),
                    "every ColorSetting row constructs its swatch canonical (rollout seam)");
        } catch (ReflectiveOperationException e) {
            fail(e.toString());
        }
        // The D-class isolation falls out of the seam: a directly-constructed
        // ColorSwatch (the Waypoint display chips) stays legacy.
        var chip = new com.aurora.client.ui.component.ColorSwatch(v::get, null);
        assertFalse(chip.canonical(),
                "direct ColorSwatch construction stays legacy by design (data-only chips)");
    }

    @Test
    void productionColorRowInventoryHoldsAtTwentySixRows() {
        // Same source-level inventory pin as the enums: 26 ColorSetting rows
        // construct in FeatureRegistry (28 overall — the other two are
        // AccentSetting's embedded picker and ParticleRowSetting's overlay
        // row, both ColorSetting-class and both canonical by construction).
        try {
            String src = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                    "src/client/java/com/aurora/client/screen/FeatureRegistry.java")));
            int count = src.split("new ColorSetting", -1).length - 1;
            assertEquals(26, count,
                    "production ColorSetting construction sites (inventory drift guard)");
        } catch (java.io.IOException e) {
            fail("FeatureRegistry.java not readable from the test working dir: " + e.getMessage());
        }
    }

    @Test
    void accentSettingCustomPickerIsCanonicalItsPresetGridIsNotColorSetting() {
        // §8 classification pin: AccentSetting's Custom slot embeds a plain
        // ColorSetting (canonical by construction like every other row);
        // its preset grid is hand-rolled rendering, NOT a ColorSetting —
        // the peer-selection family stays outside this rollout.
        AtomicReference<Integer> accent = new AtomicReference<>(0xFFEB0029); // a preset value
        AccentSetting row = new AccentSetting("Accent", accent::get, accent::set);
        try {
            var f = AccentSetting.class.getDeclaredField("customPicker");
            f.setAccessible(true);
            var picker = f.get(row);
            var swF = ColorSetting.class.getDeclaredField("swatch");
            swF.setAccessible(true);
            var sw = swF.get(picker);
            assertTrue((boolean) sw.getClass().getMethod("canonical").invoke(sw),
                    "the embedded Custom picker is a ColorSetting — canonical by construction");
        } catch (ReflectiveOperationException e) {
            fail(e.toString());
        }
    }
}
