package com.aurora.client.screen.setting;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B ColorSetting rollout: the row-level canonical contract —
 * canonical-by-construction, the swatch-trigger open/close state machine
 * (which never writes the value), disabled rejection, the Escape adapter,
 * control existence, and the close lifecycle. The semantic feedback adapter and
 * picker drags touch the live Minecraft instance / config save — those are
 * runtime-verified by the DevPilot harness.
 */
class ColorSettingPilotTest {

    // ColorSetting assigns the swatch rect in render(); unrendered (headless)
    // it keeps the field defaults — swatch at (0, 0), 40x16. Clicks target
    // THAT rect: inside = (20, 8), outside = (100, 20).
    private static final int ROW_X = 0, ROW_Y = 0, ROW_W = 320;
    private static final double IN_X = 20, IN_Y = 8;
    private static final double OUT_X = 100, OUT_Y = 20;

    private static ColorSetting row(AtomicReference<Integer> v, AtomicInteger writes) {
        v.set(0xFF30A5FF);
        return new ColorSetting("Pilot", v::get, c -> {
            writes.incrementAndGet();
            v.set(c);
        });
    }

    @Test
    void constructionIsCanonicalByDefaultWithNoOptInEscape() {
        AtomicReference<Integer> v = new AtomicReference<>();
        AtomicInteger writes = new AtomicInteger();
        ColorSetting row = row(v, writes);
        var sw = (com.aurora.client.ui.component.ColorSwatch) field(row, "swatch");
        assertTrue(sw.canonical(), "every ColorSetting row is canonical by construction");
        // The pilot's opt-in mechanism is retired with the flag (the Enum
        // seam). Reintroducing an opt-in seam is a deliberate decision that
        // must update this pin.
        assertThrows(NoSuchMethodException.class,
                () -> ColorSetting.class.getMethod("canonicalStates"),
                "canonicalStates() was the pilot's isolation mechanism — the rollout removed it");
    }

    @Test
    void swatchClickTogglesTheEditorExactlyOnceAndNeverWritesTheValue() {
        AtomicReference<Integer> v = new AtomicReference<>();
        AtomicInteger writes = new AtomicInteger();
        ColorSetting row = row(v, writes);

        assertFalse(row.mouseClicked(OUT_X, OUT_Y, 0, ROW_X, ROW_Y, ROW_W)); // outside the swatch
        assertTrue(row.mouseClicked(IN_X, IN_Y, 0, ROW_X, ROW_Y, ROW_W));
        assertTrue((boolean) field(row, "expanded"), "first click opens the editor");
        assertTrue(row.mouseClicked(IN_X, IN_Y, 0, ROW_X, ROW_Y, ROW_W));
        assertFalse((boolean) field(row, "expanded"), "second click closes it");
        assertEquals(0, writes.get(), "opening/closing never writes the color value");
        assertEquals(0xFF30A5FF, v.get());

        // Right-click never opens.
        assertFalse(row.mouseClicked(IN_X, IN_Y, 1, ROW_X, ROW_Y, ROW_W));
        assertFalse((boolean) field(row, "expanded"));
    }

    @Test
    void disabledRejectsOpenAndKeyboardAndKeepsTheValue() {
        AtomicReference<Integer> v = new AtomicReference<>();
        AtomicInteger writes = new AtomicInteger();
        ColorSetting row = row(v, writes);
        row.disabled(() -> true);
        assertFalse(row.mouseClicked(IN_X, IN_Y, 0, ROW_X, ROW_Y, ROW_W));
        assertFalse((boolean) field(row, "expanded"), "a disabled color row must not open");
        assertFalse(row.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0));
        assertEquals(0, writes.get());

        row.disabled(() -> false);
        assertTrue(row.mouseClicked(IN_X, IN_Y, 0, ROW_X, ROW_Y, ROW_W));
        assertTrue((boolean) field(row, "expanded"));
    }

    @Test
    void escapeCollapsesAnExpandedPickerButFallsThroughWhenCollapsed() {
        AtomicReference<Integer> v = new AtomicReference<>();
        AtomicInteger writes = new AtomicInteger();
        ColorSetting row = row(v, writes);
        assertFalse(row.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0),
                "collapsed Escape is the screen's, not the picker's");

        row.mouseClicked(IN_X, IN_Y, 0, ROW_X, ROW_Y, ROW_W);
        assertTrue(row.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0),
                "Escape while expanded belongs to the picker");
        assertFalse((boolean) field(row, "expanded"));
        assertEquals(0, writes.get(), "dismissal never writes the value");
    }

    @Test
    void semanticControlExistsByDefaultAndMirrorsTheEnabledGate() {
        AtomicReference<Integer> v = new AtomicReference<>();
        AtomicInteger writes = new AtomicInteger();
        ColorSetting row = row(v, writes);
        var control = row.interactionControl();
        assertNotNull(control,
                "the semantic adapter is the default — every host that asks gets it");
        assertTrue(control.action().enabled());
        row.disabled(() -> true);
        assertFalse(control.action().enabled(),
                "the disabled gate must be the authoritative activation gate");
    }

    @Test
    void detailScreenCloseResetsCanonicalTransientState() {
        AtomicReference<Integer> v = new AtomicReference<>();
        AtomicInteger writes = new AtomicInteger();
        ColorSetting row = row(v, writes);
        row.mouseClicked(IN_X, IN_Y, 0, ROW_X, ROW_Y, ROW_W);
        row.onDetailScreenClose();
        assertFalse((boolean) field(row, "expanded"),
                "no stale expanded state or registry focus across screen opens");
    }

    private static Object field(Object target, String name) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
