package com.aurora.client.ui.component;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B ColorSwatch pilot: the canonical opt-in and the
 * represented-color-is-data invariant. Render-side chrome (halo, rings,
 * hairline) is runtime-verified by the DevPilot harness; these tests pin
 * the mechanics — the animator swap and the sample ARGB that must never
 * change with state. HoverAnim symmetric math itself is proven by
 * TogglePilotTest.
 */
class ColorSwatchPilotTest {

    private static final int[] DIFFICULT = {
            0xFF000000, // black
            0xFFFFFFFF, // white
            0xFFEB0029, // saturated red (the factory accent)
            0xFF00FFFF, // saturated cyan
            0xFF808080, // mid-gray
            0x8030A5FF, // translucent blue (stored alpha)
    };

    private static ColorSwatch swatch(AtomicInteger v) {
        v.set(0xFF808080);
        return new ColorSwatch(v::get, null);
    }

    @Test
    void canonicalOptInSwapsTheAnimatorLegacyDefaultStaysLegacy() {
        AtomicInteger v = new AtomicInteger();
        ColorSwatch legacy = swatch(v);
        assertEquals(1f, legacy.hoverAnimator().update(true), 0f,
                "default construction keeps the legacy snap-in (28 rows + the waypoint chips depend on it)");

        ColorSwatch canonical = swatch(v).canonicalStates();
        float first = canonical.hoverAnimator().update(true);
        assertTrue(first < 0.5f, "canonical opt-in must animate from rest, got " + first);
        assertTrue(canonical.canonical());
    }

    @Test
    void canonicalSampleStaysLiteralInEveryState() {
        AtomicInteger v = new AtomicInteger();
        ColorSwatch swatch = swatch(v).canonicalStates();
        for (int color : DIFFICULT) {
            v.set(color);
            int opaque = color | 0xFF000000;
            assertEquals(opaque, swatch.sampleArgb(color, false),
                    "enabled sample must be the literal opaque color");
            assertEquals(opaque, swatch.sampleArgb(color, true),
                    "disabled must NOT modify the represented color (data invariant)");
        }
    }

    @Test
    void legacyDisabledHalvesTheSampleAlpha() {
        AtomicInteger v = new AtomicInteger();
        ColorSwatch legacy = swatch(v);
        // The shipped treatment, pinned: disabled halves the sample alpha —
        // the data-invariant violation the canonical mode corrects. Keep
        // this pin so the legacy rows' behavior stays byte-defined.
        assertEquals(0x55808080, legacy.sampleArgb(0xFF808080, true));
        assertEquals(0xFF808080, legacy.sampleArgb(0xFF808080, false));
    }
}
