package com.aurora.client.screen.setting;

import com.aurora.client.ui.interaction.SemanticFeedback;
import com.aurora.client.util.AuroraKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B Keybind pilot: persistent capture is independent of hover/focus,
 * activation arms only a later event, disabled is authoritative, and every
 * capture/cancel path has exactly-once write/save ownership. Rendering and
 * real Minecraft child dispatch are covered by the DevPilot runtime mode.
 */
class KeybindPilotTest {
    private static final int ORIGINAL = GLFW.GLFW_KEY_J;
    private static final int CAPTURED = GLFW.GLFW_KEY_F7;

    @AfterEach
    void clearGlobalOwners() {
        KeybindSetting.cancelActiveCapture();
        FeatureSetting.clearFocus();
    }

    @Test
    void baselineLegacyNeighborReproducesTheDisabledBypass() {
        Fixture f = fixture(false);
        f.disabled.set(true);

        assertTrue(f.row.mouseClicked(1, 1, 0, 0, 0, 320),
                "baseline defect: legacy disabled row still enters listening");
        assertTrue(f.row.listeningState());
        assertTrue(f.row.hoverTarget(false, true),
                "baseline defect: legacy listening pins accepted-hover treatment");
        assertTrue(f.row.onKeyPress(CAPTURED, 0));
        assertEquals(CAPTURED, f.binding.get(),
                "baseline defect: disabled capture mutates the binding");
        assertEquals(1, f.saves.get(),
                "baseline defect: disabled capture persists the mutation");
    }

    @Test
    void canonicalOptInIsIsolatedAndUsesSymmetricPointerOnlyHover() {
        Fixture legacy = fixture(false);
        Fixture pilot = fixture(true);

        assertFalse(legacy.row.canonical());
        assertNull(legacy.row.interactionControl());
        assertTrue(pilot.row.canonical());
        assertNotNull(pilot.row.interactionControl());

        float first = pilot.row.hoverAnimator().update(true);
        assertTrue(first < 0.5f, "canonical hover must animate from rest, got " + first);
        assertTrue(pilot.row.hoverTarget(true, false));
        assertFalse(pilot.row.hoverTarget(false, false));
        assertFalse(pilot.row.hoverTarget(true, true));
    }

    @Test
    void canonicalHoverReversesContinuouslyMidFlight() throws Exception {
        Fixture f = fixture(true);
        var hover = f.row.hoverAnimator();
        hover.update(true);
        Thread.sleep(60);
        float mid = hover.update(true);
        assertTrue(mid > 0.05f && mid < 0.95f, "expected mid-flight, got " + mid);
        float reversed = hover.update(false);
        assertTrue(reversed <= mid + 0.05f, "reversal must not jump upward");
        assertTrue(reversed >= mid - 0.35f,
                "reversal must continue from the current eased value");
    }

    @Test
    void focusAndListeningAreIndependentChannels() {
        Fixture f = fixture(true);
        var control = f.row.interactionControl();
        control.setAvailable(true);
        control.setFocused(true);

        assertTrue(control.isFocused());
        assertFalse(f.row.listeningState(), "Tab focus alone must not arm capture");

        assertTrue(control.action().activate(SemanticFeedback.NONE, null));
        assertTrue(control.isFocused());
        assertTrue(f.row.listeningState());
        assertFalse(f.row.hoverTarget(false, false),
                "listening with the pointer away must allow hover to settle at zero");

        assertTrue(f.row.onKeyPress(CAPTURED, 0));
        assertFalse(f.row.listeningState());
        assertTrue(control.isFocused(), "successful capture keeps stable keyboard focus");
    }

    @Test
    void activationEventOnlyArmsAndASeparateEventCaptures() {
        Fixture f = fixture(true);
        var control = f.row.interactionControl();
        control.setAvailable(true);
        control.setFocused(true);

        assertTrue(control.action().activate(SemanticFeedback.NONE, null));
        assertTrue(f.row.listeningState());
        assertEquals(ORIGINAL, f.binding.get(),
                "the Enter/Space activation event must not self-bind");
        assertEquals(0, f.saves.get());

        assertTrue(f.row.onKeyPress(GLFW.GLFW_KEY_ENTER, 0));
        assertEquals(GLFW.GLFW_KEY_ENTER, f.binding.get());
        assertEquals(1, f.saves.get());
        assertFalse(f.row.listeningState());
    }

    @Test
    void successfulCaptureWritesAndSavesExactlyOnce() {
        Fixture f = fixture(true);
        begin(f);

        assertTrue(f.row.onKeyPress(CAPTURED, 0));
        assertEquals(CAPTURED, f.binding.get());
        assertEquals(1, f.writes.get());
        assertEquals(1, f.saves.get());
        assertFalse(f.row.listeningState());

        assertFalse(f.row.onKeyPress(CAPTURED, 0),
                "a repeat after capture no longer belongs to the control");
        assertEquals(1, f.writes.get());
        assertEquals(1, f.saves.get());
    }

    @Test
    void disabledCannotEnterListeningThroughPointerOrSemanticActivation() {
        Fixture f = fixture(true);
        f.disabled.set(true);

        assertFalse(f.row.mouseClicked(1, 1, 0, 0, 0, 320));
        assertFalse(f.row.listeningState());
        var control = f.row.interactionControl();
        assertFalse(control.action().enabled());
        assertFalse(control.action().activate(SemanticFeedback.NONE, null));
        assertFalse(f.row.listeningState());
        assertEquals(0, f.writes.get());
        assertEquals(0, f.saves.get());
    }

    @Test
    void enabledListeningThenDisabledCancelsWithoutMutationOrSave() {
        Fixture f = fixture(true);
        begin(f);
        assertTrue(f.row.listeningState());

        f.disabled.set(true);
        assertTrue(f.row.reconcileStateForTest());
        assertFalse(f.row.listeningState());
        assertEquals(ORIGINAL, f.binding.get());

        assertFalse(f.row.onKeyPress(CAPTURED, 0));
        assertEquals(ORIGINAL, f.binding.get());
        assertEquals(0, f.writes.get());
        assertEquals(0, f.saves.get());
    }

    @Test
    void disabledRaceInputIsConsumedButCannotMutateOrSave() {
        Fixture f = fixture(true);
        begin(f);
        f.disabled.set(true);

        assertTrue(f.row.onKeyPress(CAPTURED, 0),
                "an event delivered to the stale owner is consumed-but-inert");
        assertFalse(f.row.listeningState());
        assertEquals(ORIGINAL, f.binding.get());
        assertEquals(0, f.writes.get());
        assertEquals(0, f.saves.get());
    }

    @Test
    void pointerCancellationIsConsumedAndNeverBecomesAMouseBinding() {
        Fixture f = fixture(true);
        begin(f);

        assertTrue(KeybindSetting.cancelActiveCapture());
        assertFalse(f.row.listeningState());
        assertEquals(ORIGINAL, f.binding.get());
        assertEquals(0, f.writes.get());
        assertEquals(0, f.saves.get());
        assertFalse(KeybindSetting.cancelActiveCapture());
    }

    @Test
    void escapeAndBackspacePreserveTheExistingClearSemantics() {
        Fixture escape = fixture(true);
        begin(escape);
        assertTrue(escape.row.onKeyPress(GLFW.GLFW_KEY_ESCAPE, 0));
        assertEquals(AuroraKey.UNBOUND, escape.binding.get());
        assertEquals(1, escape.writes.get());
        assertEquals(1, escape.saves.get());

        Fixture backspace = fixture(true);
        begin(backspace);
        assertTrue(backspace.row.onKeyPress(GLFW.GLFW_KEY_BACKSPACE, 0));
        assertEquals(AuroraKey.UNBOUND, backspace.binding.get());
        assertEquals(1, backspace.writes.get());
        assertEquals(1, backspace.saves.get());

        Fixture delete = fixture(true);
        begin(delete);
        assertTrue(delete.row.onKeyPress(GLFW.GLFW_KEY_DELETE, 0));
        assertEquals(GLFW.GLFW_KEY_DELETE, delete.binding.get(),
                "Delete remains an ordinary bindable key");
    }

    @Test
    void viewportLossAndScreenLifecycleBothCancelCapture() {
        Fixture viewport = fixture(true);
        begin(viewport);
        viewport.row.onInteractionAvailabilityChanged(false);
        assertFalse(viewport.row.listeningState());
        assertEquals(0, viewport.saves.get());

        Fixture close = fixture(true);
        begin(close);
        close.row.onDetailScreenClose();
        assertFalse(close.row.listeningState());
        assertEquals(ORIGINAL, close.binding.get());
        assertEquals(0, close.saves.get());
    }

    private static void begin(Fixture f) {
        assertTrue(f.row.mouseClicked(1, 1, 0, 0, 0, 320));
        assertTrue(f.row.listeningState());
    }

    private static Fixture fixture(boolean canonical) {
        AtomicInteger binding = new AtomicInteger(ORIGINAL);
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger saves = new AtomicInteger();
        AtomicBoolean disabled = new AtomicBoolean(false);
        KeybindSetting row = new KeybindSetting("Pilot", binding::get, value -> {
            writes.incrementAndGet();
            binding.set(value);
        }, saves::incrementAndGet);
        if (canonical) row.canonicalStates();
        row.disabled(disabled::get);
        return new Fixture(row, binding, writes, saves, disabled);
    }

    private record Fixture(KeybindSetting row,
                           AtomicInteger binding,
                           AtomicInteger writes,
                           AtomicInteger saves,
                           AtomicBoolean disabled) {}
}
