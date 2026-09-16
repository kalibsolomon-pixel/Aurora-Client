package com.aurora.client.ui.component;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B Slider pilot: the canonical opt-in and the value/drag semantics
 * the motion channels ride on. Pure math paths — the Slider's input methods
 * are plain arithmetic (no MC state), so value correctness, drag capture,
 * clamping, and the grip lifecycle are testable headless. Hover symmetric
 * math is proven by TogglePilotTest and not duplicated; these tests prove
 * the Slider actually opted in and that drag/hover/disabled compose.
 */
class SliderPilotTest {

    private static Slider slider(AtomicReference<Double> value, double initial) {
        value.set(initial);
        return new Slider(value::get, value::set, 0.1, 1.0, 0.05);
    }

    @Test
    void canonicalOptInSwapsTheHoverAnimatorLegacyDefaultStaysLegacy() {
        AtomicReference<Double> v = new AtomicReference<>(0.5);
        Slider legacy = slider(v, 0.5);
        assertEquals(1f, legacy.hoverAnimator().update(true), 0f,
                "default construction keeps the legacy snap-in (62 rows depend on it)");

        Slider canonical = slider(v, 0.5).canonicalStates();
        float first = canonical.hoverAnimator().update(true);
        assertTrue(first < 0.5f, "canonical opt-in must animate from rest, got " + first);
        assertTrue(canonical.canonical());
    }

    @Test
    void clickJumpsToTheSnappedValueAndStartsTheDrag() {
        AtomicReference<Double> v = new AtomicReference<>(0.5);
        Slider s = slider(v, 0.5);
        s.layout(100, 10, 200, 36);

        // Click at 25% of the track (x=150): value = 0.1 + 0.25*0.9 = 0.325 → snapped to 0.35? No:
        // 0.325/0.05 = 6.5 → round-half-up = 7 → 0.35. Assert through the slider's own snap.
        assertTrue(s.mouseClicked(150, 10 + 36 - 12 + 2, 0));
        assertTrue(s.isDragging());
        assertEquals(snap(0.1 + 0.25 * 0.9), v.get(), 1e-9);
    }

    @Test
    void dragFollowsThePointerAndClampsAtBothEdges() {
        AtomicReference<Double> v = new AtomicReference<>(0.5);
        Slider s = slider(v, 0.5);
        s.layout(100, 10, 200, 36);
        s.mouseClicked(150, 36, 0);

        s.mouseDragged(400, 40, 0, 0, 0);   // beyond right edge (track is x=100..300)
        assertEquals(1.0, v.get(), 1e-9, "clamps to max");
        s.mouseDragged(20, 40, 0, 0, 0);    // beyond left edge
        assertEquals(0.1, v.get(), 1e-9, "clamps to min");
        s.mouseDragged(300, 40, 0, 0, 0);   // exact right edge of track
        assertEquals(1.0, v.get(), 1e-9);
        assertTrue(s.isDragging(), "drag survives pointer outside the bounds");
    }

    @Test
    void releaseEndsTheDragAndKeepsTheFinalClampedValue() {
        AtomicReference<Double> v = new AtomicReference<>(0.5);
        Slider s = slider(v, 0.5);
        s.layout(100, 10, 200, 36);
        s.mouseClicked(150, 36, 0);
        s.mouseDragged(-500, -500, 0, 0, 0);
        assertEquals(0.1, v.get(), 1e-9);
        assertTrue(s.mouseReleased(-500, -500, 0));
        assertFalse(s.isDragging());
        assertEquals(0.1, v.get(), 1e-9, "value remains the final clamped value");
    }

    @Test
    void dragGripAppearsOnAcceptedDragAndVanishesOnRelease() {
        AtomicReference<Double> v = new AtomicReference<>(0.5);
        Slider s = slider(v, 0.5).canonicalStates();
        s.layout(100, 10, 200, 36);
        assertEquals(7f, s.knobRadiusPx(), 0f, "rest radius");
        s.mouseClicked(150, 36, 0);
        assertEquals(8f, s.knobRadiusPx(), 0f, "grip radius while dragging (center unchanged)");
        s.mouseReleased(150, 36, 0);
        assertEquals(7f, s.knobRadiusPx(), 0f, "back to rest instantly on release");

        // Legacy mode never grips.
        Slider legacy = slider(v, 0.5);
        legacy.layout(100, 10, 200, 36);
        legacy.mouseClicked(150, 36, 0);
        assertEquals(7f, legacy.knobRadiusPx(), 0f, "legacy rows keep the exact rest radius");
    }

    @Test
    void disabledRejectsPointerAndKeyboardAndValueStays() {
        AtomicReference<Double> v = new AtomicReference<>(0.5);
        Slider s = slider(v, 0.5).canonicalStates().disabled(true);
        s.layout(100, 10, 200, 36);
        assertFalse(s.mouseClicked(150, 36, 0));
        assertFalse(s.isDragging());
        assertFalse(s.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0));
        assertEquals(0.5, v.get(), 1e-9);

        s.disabled(false);
        assertTrue(s.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0));
        assertEquals(0.55, v.get(), 1e-9, "one step per accepted key");
    }

    @Test
    void keyboardStepsClampAtBothEndsAndHonorModifiers() {
        AtomicReference<Double> v = new AtomicReference<>(0.55);
        Slider s = slider(v, 0.55);
        assertTrue(s.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT, 0));
        assertEquals(0.5, v.get(), 1e-9);
        assertTrue(s.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT,
                org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT)); // ×10
        assertEquals(1.0, v.get(), 1e-9, "shift ×10 lands on max");
        assertTrue(s.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0));
        assertEquals(1.0, v.get(), 1e-9, "clamped at max");
        // Ctrl ×0.1 makes a 0.005 delta, which snap(0.05) rounds away — the
        // key is consumed but the micro-step is sub-step: existing semantics.
        assertTrue(s.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT,
                org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL));
        assertEquals(1.0, v.get(), 1e-9, "sub-step micro-deltas round away under snap");
        for (int i = 0; i < 40; i++) s.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT, 0);
        assertEquals(0.1, v.get(), 1e-9, "clamped at min after repeated decrements");
    }

    @Test
    void setValueSnapsAndClampsLikeTheDragPath() {
        AtomicReference<Double> v = new AtomicReference<>(0.5);
        Slider s = slider(v, 0.5);
        s.setValue(0.4321);
        assertEquals(snap(0.4321), v.get(), 1e-9);
        s.setValue(99);
        assertEquals(1.0, v.get(), 1e-9);
        s.setValue(-5);
        assertEquals(0.1, v.get(), 1e-9);
    }

    @Test
    void cachedShapeFingerprintCarriesTheDisabledBit() {
        AtomicReference<Double> v = new AtomicReference<>(0.5);
        Slider s = slider(v, 0.5);
        int enabled = s.shapeFingerprint();
        s.disabled(true);
        assertNotEquals(enabled, s.shapeFingerprint(),
                "the cached track color depends on disabled — the bit must flip the fingerprint");
    }

    private static double snap(double raw) {
        double val = Math.round(raw / 0.05) * 0.05;
        return Math.max(0.1, Math.min(1.0, val));
    }
}
