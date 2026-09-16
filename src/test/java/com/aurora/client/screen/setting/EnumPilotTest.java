package com.aurora.client.screen.setting;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B Enum pilot: the canonical opt-in, the hover/expanded channel
 * split, the popup open/close/outside-dismiss state machine, and the narrow
 * keyboard adapter. Pure state paths only — the trigger's semantic
 * activation (real UI click sound), focus paint, and option selection save
 * are runtime-verified by the DevPilot harness (the semantic feedback
 * adapter touches the live Minecraft instance; selection writes config).
 * HoverAnim symmetric math itself is proven by TogglePilotTest.
 */
class EnumPilotTest {

    private enum EightValues { A, B, C, D, E, F, G, H }
    private enum TwoValues { X, Y }

    private static EnumSetting<EightValues> eight(AtomicReference<EightValues> v) {
        v.set(EightValues.A);
        return new EnumSetting<>("Pilot", EightValues.class, v::get, v::set);
    }

    // Row geometry constants mirrored from EnumSetting (BTN_W=100, BTN_H=18,
    // trigger at x + width - BTN_W - 14, y + 5).
    private static final int ROW_X = 0, ROW_Y = 0, ROW_W = 320;
    private static final int BTN_X = ROW_X + ROW_W - 100 - 14;
    private static final int BTN_Y = ROW_Y + 5;

    @Test
    void canonicalOptInSwapsTheHoverAnimatorLegacyDefaultStaysLegacy() {
        AtomicReference<EightValues> v = new AtomicReference<>();
        EnumSetting<EightValues> legacy = eight(v);
        assertEquals(1f, legacy.hoverAnimator().update(true), 0f,
                "default construction keeps the legacy snap-in (25 rows depend on it)");

        EnumSetting<EightValues> canonical = eight(v).canonicalStates();
        float first = canonical.hoverAnimator().update(true);
        assertTrue(first < 0.5f, "canonical opt-in must animate from rest, got " + first);
        assertTrue(canonical.canonical());
    }

    @Test
    void expandedDoesNotDriveTheCanonicalHoverTargetLegacyKeepsThePin() {
        AtomicReference<EightValues> v = new AtomicReference<>();
        EnumSetting<EightValues> canonical = eight(v).canonicalStates();
        // Open the popup (direct path — the semantic control is not created
        // until a semantic host asks for it).
        assertTrue(canonical.mouseClicked(BTN_X + 10, BTN_Y + 9, 0, ROW_X, ROW_Y, ROW_W));
        assertTrue(canonical.expandedState());
        assertFalse(canonical.hoverTarget(false, false),
                "canonical: expanded must NOT pin the hover target (§7 — expanded is not hover)");
        assertTrue(canonical.hoverTarget(true, false),
                "canonical: the pointer over the trigger still drives hover");
        assertFalse(canonical.hoverTarget(true, true),
                "disabled suppresses the hover target on every mode");

        EnumSetting<EightValues> legacy = eight(v);
        assertTrue(legacy.mouseClicked(BTN_X + 10, BTN_Y + 9, 0, ROW_X, ROW_Y, ROW_W));
        assertTrue(legacy.hoverTarget(false, false),
                "legacy: the shipped pin-at-1-while-expanded target is byte-preserved");
    }

    @Test
    void triggerClickTogglesExpandedExactlyOncePerClick() {
        AtomicReference<EightValues> v = new AtomicReference<>();
        EnumSetting<EightValues> row = eight(v).canonicalStates();
        // Outside the pill: not consumed while collapsed.
        assertFalse(row.mouseClicked(5, 5, 0, ROW_X, ROW_Y, ROW_W));

        assertTrue(row.mouseClicked(BTN_X + 10, BTN_Y + 9, 0, ROW_X, ROW_Y, ROW_W));
        assertTrue(row.expandedState(), "first click opens");
        assertTrue(row.mouseClicked(BTN_X + 10, BTN_Y + 9, 0, ROW_X, ROW_Y, ROW_W));
        assertFalse(row.expandedState(), "second click on the trigger closes");
        assertEquals(EightValues.A, v.get(), "trigger clicks never change the value");

        // Right-click never opens.
        assertFalse(row.mouseClicked(BTN_X + 10, BTN_Y + 9, 1, ROW_X, ROW_Y, ROW_W));
        assertFalse(row.expandedState());
    }

    @Test
    void outsideClickCollapsesAndConsumesWhileExpanded() {
        AtomicReference<EightValues> v = new AtomicReference<>();
        EnumSetting<EightValues> row = eight(v).canonicalStates();
        row.mouseClicked(BTN_X + 10, BTN_Y + 9, 0, ROW_X, ROW_Y, ROW_W);

        // A click far from trigger and popup: consumed (never falls through
        // to whatever renders underneath) and collapses.
        assertTrue(row.mouseClicked(40, 200, 0, ROW_X, ROW_Y, ROW_W));
        assertFalse(row.expandedState());
        assertEquals(EightValues.A, v.get(), "outside dismissal never changes the value");
    }

    @Test
    void popupFooterBandClickCollapsesWithoutSelecting() {
        AtomicReference<EightValues> v = new AtomicReference<>();
        EnumSetting<EightValues> row = eight(v).canonicalStates();
        row.mouseClicked(BTN_X + 10, BTN_Y + 9, 0, ROW_X, ROW_Y, ROW_W);

        // Inside the dropdown rect but in the 2px footer band below the last
        // option row: consumed, collapse, no selection (index >= visible).
        // (The symmetric 2px TOP band is NOT safe — (int) truncation maps it
        // to option 0, shipped behavior this pilot leaves untouched.)
        int footerBandY = BTN_Y + 18 + 2 + 5 * 16 + 3;
        assertTrue(row.mouseClicked(BTN_X + 10, footerBandY, 0, ROW_X, ROW_Y, ROW_W));
        assertFalse(row.expandedState());
        assertEquals(EightValues.A, v.get());
    }

    @Test
    void disabledRejectsOpenAndKeyboardAndKeepsTheValue() {
        AtomicReference<EightValues> v = new AtomicReference<>();
        EnumSetting<EightValues> row = eight(v).canonicalStates();
        row.disabled(() -> true);
        assertFalse(row.mouseClicked(BTN_X + 10, BTN_Y + 9, 0, ROW_X, ROW_Y, ROW_W));
        assertFalse(row.expandedState(), "a disabled enum must not open");
        assertFalse(row.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0));
        assertFalse(row.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, 0));
        assertEquals(EightValues.A, v.get());

        row.disabled(() -> false);
        assertTrue(row.mouseClicked(BTN_X + 10, BTN_Y + 9, 0, ROW_X, ROW_Y, ROW_W));
        assertTrue(row.expandedState());
    }

    @Test
    void escapeCollapsesAnExpandedPopupButFallsThroughWhenCollapsed() {
        AtomicReference<EightValues> v = new AtomicReference<>();
        EnumSetting<EightValues> row = eight(v).canonicalStates();
        // Collapsed: Escape is not the enum's to consume (screen close).
        assertFalse(row.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0));

        row.mouseClicked(BTN_X + 10, BTN_Y + 9, 0, ROW_X, ROW_Y, ROW_W);
        assertTrue(row.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0),
                "Escape while expanded belongs to the popup, not the screen");
        assertFalse(row.expandedState());
        assertEquals(EightValues.A, v.get());

        // The legacy row keeps no such adapter.
        EnumSetting<EightValues> legacy = eight(v);
        legacy.mouseClicked(BTN_X + 10, BTN_Y + 9, 0, ROW_X, ROW_Y, ROW_W);
        assertFalse(legacy.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0),
                "legacy rows never had a key path — byte-preserved");
    }

    @Test
    void arrowKeysScrollOnlyScrollableExpandedLists() {
        AtomicReference<EightValues> v = new AtomicReference<>();
        EnumSetting<EightValues> row = eight(v).canonicalStates();
        // Collapsed: arrows are not consumed.
        assertFalse(row.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, 0));

        row.mouseClicked(BTN_X + 10, BTN_Y + 9, 0, ROW_X, ROW_Y, ROW_W);
        assertTrue(row.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, 0), "8 options scroll");
        assertEquals(1, scrollOffset(row));
        assertTrue(row.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_UP, 0));
        assertEquals(0, scrollOffset(row));
        // Clamped, still consumed (the wheel's exact semantics).
        assertTrue(row.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_UP, 0));
        assertEquals(0, scrollOffset(row));

        AtomicReference<TwoValues> v2 = new AtomicReference<>(TwoValues.X);
        EnumSetting<TwoValues> two = new EnumSetting<>("Two", TwoValues.class, v2::get, v2::set)
                .canonicalStates();
        two.mouseClicked(BTN_X + 10, BTN_Y + 9, 0, ROW_X, ROW_Y, ROW_W);
        assertFalse(two.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, 0),
                "≤5 options never scroll — the key falls through");
    }

    @Test
    void semanticControlExistsOnlyForCanonicalRowsAndMirrorsTheEnabledGate() {
        AtomicReference<EightValues> v = new AtomicReference<>();
        EnumSetting<EightValues> legacy = eight(v);
        assertNull(legacy.interactionControl(),
                "legacy rows must not join the semantic lifecycle (pilot isolation)");

        EnumSetting<EightValues> canonical = eight(v).canonicalStates();
        var control = canonical.interactionControl();
        assertNotNull(control);
        assertTrue(control.action().enabled(), "enabled gate mirrors the row's supplier");
        canonical.disabled(() -> true);
        assertFalse(control.action().enabled(),
                "the disabled gate must be the authoritative activation gate");
    }

    @Test
    void detailScreenCloseResetsPopupState() {
        AtomicReference<EightValues> v = new AtomicReference<>();
        EnumSetting<EightValues> row = eight(v).canonicalStates();
        row.mouseClicked(BTN_X + 10, BTN_Y + 9, 0, ROW_X, ROW_Y, ROW_W);
        row.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, 0);
        row.onDetailScreenClose();
        assertFalse(row.expandedState(), "no stale expanded state across screen opens");
        assertEquals(0, scrollOffset(row), "scroll resets for the next visit");
    }

    private static int scrollOffset(EnumSetting<?> row) {
        try {
            var f = EnumSetting.class.getDeclaredField("scrollOffset");
            f.setAccessible(true);
            return f.getInt(row);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
