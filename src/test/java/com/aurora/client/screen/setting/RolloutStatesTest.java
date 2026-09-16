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

    // ------------------------------------------------------------------
    // Keybind rollout invariants (2026-09-16): canonical capture states
    // are the row's only behavior — no opt-in, no legacy disabled bypass.
    // ------------------------------------------------------------------

    @Test
    void keybindConstructionIsCanonicalWithNoOptInEscape() {
        AtomicReference<Integer> v = new AtomicReference<>(org.lwjgl.glfw.GLFW.GLFW_KEY_J);
        KeybindSetting row = new KeybindSetting("R", v::get, v::set);
        assertNotNull(row.interactionControl(),
                "every construction produces the semantic adapter — canonical by default");
        float first = row.hoverAnimator().update(true);
        assertTrue(first < 0.5f, "every construction animates from rest — canonical by default");
        // The pilot's opt-in mechanism is retired with the flag (the
        // EnumSetting end state). Reintroducing an opt-in seam is a
        // deliberate decision that must update this pin together with
        // ARCHITECTURE.md. This also pins the removal of the legacy
        // branch — the historical disabled bypass (a disabled row could
        // enter listening, mutate config, and save) is unreachable.
        assertThrows(NoSuchMethodException.class,
                () -> KeybindSetting.class.getMethod("canonicalStates"),
                "canonicalStates() was the pilot's isolation mechanism — the rollout removed it");
    }

    @Test
    void productionKeybindInventoryHoldsAtSeventeenRows() {
        // Source-level inventory pin: the docs record "all 17 production
        // KeybindSetting rows construct in FeatureRegistry" (16 mirror an
        // Aurora KeyMapping, Stats Reset is Aurora-UI-only; the vanilla
        // Controls screen additionally exposes HUD Editor + Blur Test — 18
        // registered mappings, a separate configuration surface outside
        // this rollout). Any added/removed keybind row must consciously
        // update this count together with ARCHITECTURE.md.
        try {
            String src = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                    "src/client/java/com/aurora/client/screen/FeatureRegistry.java")));
            int count = src.split("new KeybindSetting", -1).length - 1;
            assertEquals(17, count,
                    "production KeybindSetting construction sites (inventory drift guard)");
        } catch (java.io.IOException e) {
            fail("FeatureRegistry.java not readable from the test working dir: " + e.getMessage());
        }
    }

    @Test
    void multipleKeybindRowsHoldExactlyOneCaptureOwner() {
        java.util.concurrent.atomic.AtomicInteger a = new java.util.concurrent.atomic.AtomicInteger(
                org.lwjgl.glfw.GLFW.GLFW_KEY_J);
        java.util.concurrent.atomic.AtomicInteger b = new java.util.concurrent.atomic.AtomicInteger(
                org.lwjgl.glfw.GLFW.GLFW_KEY_K);
        java.util.concurrent.atomic.AtomicInteger writes = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger saves = new java.util.concurrent.atomic.AtomicInteger();
        KeybindSetting rowA = new KeybindSetting("A", a::get, v -> { writes.incrementAndGet(); a.set(v); }, saves::incrementAndGet);
        KeybindSetting rowB = new KeybindSetting("B", b::get, v -> { writes.incrementAndGet(); b.set(v); }, saves::incrementAndGet);

        assertTrue(rowA.mouseClicked(1, 1, 0, 0, 0, 320));
        assertTrue(rowA.listeningState());
        // Activating B while A listens cancels A's ownership structurally
        // (beginListening) — never two simultaneous listening owners.
        rowB.interactionControl().action().activate(
                com.aurora.client.ui.interaction.SemanticFeedback.NONE, null);
        assertTrue(rowB.listeningState());
        assertFalse(rowA.listeningState(), "A must be torn down when B takes ownership");

        // The stale owner's key event is inert; B's is the one capture.
        assertFalse(rowA.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_F7, 0));
        assertTrue(rowB.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_F7, 0));
        assertEquals((int) org.lwjgl.glfw.GLFW.GLFW_KEY_F7, b.get());
        assertEquals((int) org.lwjgl.glfw.GLFW.GLFW_KEY_J, a.get(), "no double capture through the stale owner");
        assertEquals(1, writes.get(), "exactly one write");
        assertEquals(1, saves.get(), "exactly one save");
        assertFalse(rowB.listeningState());
    }

    @Test
    void keyListAddPillIsCanonicalAndStaysASeparateFamily() {
        // Phase B KeyList pilot (2026-09-16): the add pill runs the
        // canonical symmetric animator; the family boundary is semantic,
        // not animator state — KeyList keeps its own ESC/BACKSPACE-cancel
        // rule and multi-value capture semantics (see KeyListPilotTest).
        java.util.List<Integer> list = new java.util.ArrayList<>();
        KeyListSetting row = new KeyListSetting("Extra Keys", () -> list, v -> {});
        try {
            var f = KeyListSetting.class.getDeclaredField("hoverAnim");
            f.setAccessible(true);
            var anim = (com.aurora.client.util.HoverAnim) f.get(row);
            float first = anim.update(true);
            assertTrue(first < 0.5f,
                    "the add pill animates from rest (first sample " + first
                            + ") — canonical since the Phase B pilot");
        } catch (ReflectiveOperationException e) {
            fail(e.toString());
        }
    }
}
