package com.aurora.client.screen.setting;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B ColorSetting pilot: the row-level canonical contract — opt-in
 * forwarding, the swatch-trigger open/close state machine (which never
 * writes the value), disabled rejection, the Escape adapter, control
 * isolation, and the close lifecycle. The semantic feedback adapter and
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
    void canonicalForwardsToTheSwatch() {
        AtomicReference<Integer> v = new AtomicReference<>();
        AtomicInteger writes = new AtomicInteger();
        ColorSetting legacy = row(v, writes);
        assertFalse(legacy.canonical());

        ColorSetting canonical = row(v, writes).canonicalStates();
        assertTrue(canonical.canonical());
        var sw = (com.aurora.client.ui.component.ColorSwatch)
                field(canonical, "swatch");
        assertTrue(sw.canonical(), "canonicalStates() must reach the swatch");
    }

    @Test
    void swatchClickTogglesTheEditorExactlyOnceAndNeverWritesTheValue() {
        AtomicReference<Integer> v = new AtomicReference<>();
        AtomicInteger writes = new AtomicInteger();
        ColorSetting row = row(v, writes).canonicalStates();

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
        ColorSetting row = row(v, writes).canonicalStates();
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
        ColorSetting row = row(v, writes).canonicalStates();
        assertFalse(row.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0),
                "collapsed Escape is the screen's, not the picker's");

        row.mouseClicked(IN_X, IN_Y, 0, ROW_X, ROW_Y, ROW_W);
        assertTrue(row.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0),
                "Escape while expanded belongs to the picker");
        assertFalse((boolean) field(row, "expanded"));
        assertEquals(0, writes.get(), "dismissal never writes the value");

        // The legacy row keeps no key path.
        ColorSetting legacy = row(v, writes);
        legacy.mouseClicked(IN_X, IN_Y, 0, ROW_X, ROW_Y, ROW_W);
        assertTrue((boolean) field(legacy, "expanded"));
        assertFalse(legacy.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0),
                "legacy rows never had a key path — byte-preserved");
    }

    @Test
    void semanticControlExistsOnlyForCanonicalRowsAndMirrorsTheEnabledGate() {
        AtomicReference<Integer> v = new AtomicReference<>();
        AtomicInteger writes = new AtomicInteger();
        assertNull(row(v, writes).interactionControl(),
                "legacy rows must not join the semantic lifecycle (pilot isolation)");

        ColorSetting canonical = row(v, writes).canonicalStates();
        var control = canonical.interactionControl();
        assertNotNull(control);
        assertTrue(control.action().enabled());
        canonical.disabled(() -> true);
        assertFalse(control.action().enabled(),
                "the disabled gate must be the authoritative activation gate");
    }

    @Test
    void detailScreenCloseResetsCanonicalTransientState() {
        AtomicReference<Integer> v = new AtomicReference<>();
        AtomicInteger writes = new AtomicInteger();
        ColorSetting row = row(v, writes).canonicalStates();
        row.mouseClicked(IN_X, IN_Y, 0, ROW_X, ROW_Y, ROW_W);
        row.onDetailScreenClose();
        assertFalse((boolean) field(row, "expanded"),
                "no stale expanded state or registry focus across screen opens");

        // Legacy rows keep the shipped persists-across-close behavior.
        ColorSetting legacy = row(v, writes);
        legacy.mouseClicked(IN_X, IN_Y, 0, ROW_X, ROW_Y, ROW_W);
        legacy.onDetailScreenClose();
        assertTrue((boolean) field(legacy, "expanded"),
                "legacy behavior is byte-preserved (documented, not fixed here)");
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
