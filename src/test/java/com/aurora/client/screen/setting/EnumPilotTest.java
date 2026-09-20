package com.aurora.client.screen.setting;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B Enum state coverage (pilot semantics, now the rollout default):
 * the pointer-only hover target, the popup open/close/outside-dismiss
 * state machine, and the narrow keyboard adapter. Pure state paths only —
 * the trigger's semantic activation (real UI click sound), focus paint,
 * and option selection save are runtime-verified by the DevPilot harness
 * (the semantic feedback adapter touches the live Minecraft instance;
 * selection writes config). HoverAnim symmetric math itself is proven by
 * TogglePilotTest.
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
    void constructionIsSymmetricCanonicalByDefault() {
        AtomicReference<EightValues> v = new AtomicReference<>();
        EnumSetting<EightValues> row = eight(v);
        float first = row.hoverAnimator().update(true);
        assertTrue(first < 0.5f, "rollout default must animate from rest, got " + first);
        // Structural pin: the animator is the symmetric construction — the
        // snap-in legacy animator must not silently return.
        assertFalse(animatorBackDatesFromRest(row),
                "the default animator must be HoverAnim.symmetric (no snap-in path)");
    }

    private static boolean animatorBackDatesFromRest(EnumSetting<?> row) {
        try {
            var anim = row.hoverAnimator();
            var f = anim.getClass().getDeclaredField("backDateFromRest");
            f.setAccessible(true);
            return f.getBoolean(anim);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void expandedNeverDrivesTheTriggerHoverTarget() {
        AtomicReference<EightValues> v = new AtomicReference<>();
        EnumSetting<EightValues> row = eight(v);
        // Open the popup (direct path — the semantic control is not created
        // until a semantic host asks for it).
        assertTrue(row.mouseClicked(BTN_X + 10, BTN_Y + 9, 0, ROW_X, ROW_Y, ROW_W));
        assertTrue(row.expandedState());
        assertFalse(row.hoverTarget(false, false),
                "expanded must NOT drive the hover target (expanded is not hover)");
        assertTrue(row.hoverTarget(true, false),
                "the pointer over the trigger still drives hover");
        assertFalse(row.hoverTarget(true, true),
                "disabled suppresses the hover target");
    }

    @Test
    void triggerClickTogglesExpandedExactlyOncePerClick() {
        AtomicReference<EightValues> v = new AtomicReference<>();
        EnumSetting<EightValues> row = eight(v);
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
        EnumSetting<EightValues> row = eight(v);
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
        EnumSetting<EightValues> row = eight(v);
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
        EnumSetting<EightValues> row = eight(v);
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
        EnumSetting<EightValues> row = eight(v);
        // Collapsed: Escape is not the enum's to consume (screen close).
        assertFalse(row.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0));

        row.mouseClicked(BTN_X + 10, BTN_Y + 9, 0, ROW_X, ROW_Y, ROW_W);
        assertTrue(row.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0),
                "Escape while expanded belongs to the popup, not the screen");
        assertFalse(row.expandedState());
        assertEquals(EightValues.A, v.get());
    }

    @Test
    void arrowKeysScrollOnlyScrollableExpandedLists() {
        AtomicReference<EightValues> v = new AtomicReference<>();
        EnumSetting<EightValues> row = eight(v);
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
        EnumSetting<TwoValues> two = new EnumSetting<>("Two", TwoValues.class, v2::get, v2::set);
        two.mouseClicked(BTN_X + 10, BTN_Y + 9, 0, ROW_X, ROW_Y, ROW_W);
        assertFalse(two.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, 0),
                "≤5 options never scroll — the key falls through");
    }

    @Test
    void semanticControlExistsByDefaultAndMirrorsTheEnabledGate() {
        AtomicReference<EightValues> v = new AtomicReference<>();
        EnumSetting<EightValues> row = eight(v);
        var control = row.interactionControl();
        assertNotNull(control,
                "the semantic adapter is the default — every host that asks gets it");
        assertTrue(control.action().enabled(), "enabled gate mirrors the row's supplier");
        row.disabled(() -> true);
        assertFalse(control.action().enabled(),
                "the disabled gate must be the authoritative activation gate");
    }

    @Test
    void detailScreenCloseResetsPopupState() {
        AtomicReference<EightValues> v = new AtomicReference<>();
        EnumSetting<EightValues> row = eight(v);
        row.mouseClicked(BTN_X + 10, BTN_Y + 9, 0, ROW_X, ROW_Y, ROW_W);
        row.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, 0);
        row.onDetailScreenClose();
        assertFalse(row.expandedState(), "no stale expanded state across screen opens");
        assertEquals(0, scrollOffset(row), "scroll resets for the next visit");
    }

    @Test
    void losingHostAvailabilityCollapsesPopupAndReleasesKeyboardOwnership() {
        AtomicReference<EightValues> v = new AtomicReference<>();
        EnumSetting<EightValues> row = eight(v);
        row.mouseClicked(BTN_X + 10, BTN_Y + 9, 0, ROW_X, ROW_Y, ROW_W);
        row.onKeyPress(org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN, 0);
        assertTrue(row.expandedState());
        assertSame(row, FeatureSetting.getFocused());

        row.onInteractionAvailabilityChanged(false);
        assertFalse(row.expandedState(), "an off-viewport popup owner must collapse");
        assertEquals(0, scrollOffset(row));
        assertNull(FeatureSetting.getFocused(), "hidden popup must release routed keys/wheel");
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
