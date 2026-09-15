package com.aurora.client.ui.interaction;

import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase A limited-rollout tests: generic service behavior exercised by the
 * rollout's new topologies — multiple controls coexisting on one screen,
 * dynamic enabled state on a long-lived action, the vanilla-backed
 * (semantic-sound-free) ownership mode, per-frame bounds updates, and the
 * pointer-only non-focusable control shape. Deliberately not tied to any
 * specific feature setting: these cover the shared {@link SemanticAction} /
 * {@link SemanticActionControl} contract only.
 */
class SemanticRolloutTest {

    // ------------------------------------------------------------------
    // Multiple semantic controls coexist without traversal/ownership problems
    // ------------------------------------------------------------------

    @Test
    void multipleControlsActivateIndependentlyAndSkipUnavailablePeers() {
        AtomicInteger behaviorA = new AtomicInteger();
        AtomicInteger behaviorB = new AtomicInteger();
        SemanticActionControl a = control(buttonAction(() -> true, behaviorA));
        SemanticActionControl b = control(buttonAction(() -> true, behaviorB));
        a.setAvailable(true);
        a.setBounds(10, 20, 90, 18);
        b.setAvailable(true);
        b.setBounds(10, 60, 90, 18);

        // Both are traversal-eligible while available.
        assertNotNull(a.nextFocusPath(new FocusNavigationEvent.TabNavigation(true)));
        assertNotNull(b.nextFocusPath(new FocusNavigationEvent.TabNavigation(true)));

        // Focus A: Enter activates exactly A, exactly once.
        a.setFocused(true);
        assertTrue(a.keyPressed(new KeyEvent(257, 0, 0)));
        assertEquals(1, behaviorA.get());
        assertEquals(0, behaviorB.get());

        // Focus moves to B: Space activates exactly B, exactly once.
        a.setFocused(false);
        b.setFocused(true);
        assertTrue(b.keyPressed(new KeyEvent(32, 0, 0)));
        assertEquals(1, behaviorA.get());
        assertEquals(1, behaviorB.get());

        // A control that becomes unavailable drops out of traversal but its
        // peer is still reachable and fully operable.
        b.setAvailable(false);
        assertNull(b.nextFocusPath(new FocusNavigationEvent.TabNavigation(true)));
        assertNotNull(a.nextFocusPath(new FocusNavigationEvent.TabNavigation(true)));
        // Re-available and unfocused, it is traversal-eligible again — the
        // same long-lived instance, no reconstruction.
        b.setAvailable(true);
        b.setFocused(false);
        assertNotNull(b.nextFocusPath(new FocusNavigationEvent.TabNavigation(true)));
    }

    // ------------------------------------------------------------------
    // Dynamic enabled state on a long-lived action (no reconstruction)
    // ------------------------------------------------------------------

    @Test
    void dynamicEnabledFlipsOnTheSameLongLivedControl() {
        AtomicBoolean enabled = new AtomicBoolean(true);
        AtomicInteger behavior = new AtomicInteger();
        AtomicInteger sounds = new AtomicInteger();
        AtomicInteger visuals = new AtomicInteger();
        SemanticActionControl c = control(buttonAction(enabled::get, behavior), sounds, visuals);
        c.setAvailable(true);
        c.setBounds(10, 20, 90, 18);

        // Enabled: pointer → accepted once (action + sound + visual).
        assertTrue(c.activateFromPointer(11, 21, 0));
        assertEquals(1, behavior.get());
        assertEquals(1, sounds.get());
        assertEquals(1, visuals.get());

        // Disabled (state changed at runtime, same instances): pointer is
        // rejected, and selection keys are CONSUMED without acting (so they
        // cannot fall through and activate another control) — no
        // accepted-action feedback of any kind.
        enabled.set(false);
        assertFalse(c.activateFromPointer(11, 21, 0));
        c.setFocused(true);
        assertTrue(c.keyPressed(new KeyEvent(257, 0, 0)));   // Enter: consumed, inert
        assertTrue(c.keyPressed(new KeyEvent(32, 0, 0)));    // Space: consumed, inert
        assertTrue(c.keyPressed(new KeyEvent(335, 0, 0)));   // keypad Enter: consumed, inert
        assertEquals(1, behavior.get());
        assertEquals(1, sounds.get());
        assertEquals(1, visuals.get());

        // Re-enabled: the same control serves again — no reconstruction.
        enabled.set(true);
        assertTrue(c.activateFromPointer(11, 21, 0));
        assertEquals(2, behavior.get());
        assertEquals(2, sounds.get());
        assertEquals(2, visuals.get());
    }

    // ------------------------------------------------------------------
    // Vanilla-backed ownership: activation without a semantic sound
    // ------------------------------------------------------------------

    @Test
    void vanillaBackedActionNeverPlaysSemanticFeedbackAndActivatesOnce() {
        AtomicInteger behavior = new AtomicInteger();
        AtomicInteger sounds = new AtomicInteger();
        SemanticAction action = new SemanticAction(
                Component.literal("Done"),
                () -> Component.literal("Close this screen and return."),
                null,
                () -> true,
                behavior::incrementAndGet,
                SemanticSound.NONE,   // vanilla plays its own UI click before onPress
                true, true);
        SemanticFeedback countingFeedback = sound -> {
            if (sound == SemanticSound.ACTIVATION) sounds.incrementAndGet();
        };

        // The vanilla-backed route calls activate(null, null): no feedback
        // adapter, no visual feedback runnable — vanilla owns both.
        assertTrue(action.activate(null, null));
        assertTrue(action.activate(countingFeedback, null));
        assertEquals(2, behavior.get(), "behavior runs once per accepted activation");
        assertEquals(0, sounds.get(), "SemanticSound.NONE must not dispatch any feedback");
    }

    // ------------------------------------------------------------------
    // Bounds follow layout/scroll without reconstructing the control
    // ------------------------------------------------------------------

    @Test
    void movedBoundsRejectTheOldRegionAndAcceptTheNewOne() {
        AtomicInteger behavior = new AtomicInteger();
        SemanticActionControl c = control(buttonAction(() -> true, behavior));
        c.setAvailable(true);
        c.setBounds(10, 20, 90, 18);

        assertTrue(c.activateFromPointer(11, 21, 0));
        assertEquals(1, behavior.get());

        // The row scrolled / the layout moved: the stale region must not
        // stay operable, the new one must work with the same instance.
        c.setBounds(10, 60, 90, 18);
        assertFalse(c.activateFromPointer(11, 21, 0));
        assertTrue(c.activateFromPointer(11, 61, 0));
        assertEquals(2, behavior.get());
    }

    // ------------------------------------------------------------------
    // The pointer-only, non-focusable control shape (a form's confirm
    // button whose text field owns the keyboard commit)
    // ------------------------------------------------------------------

    @Test
    void pointerOnlyControlIsTraversalInvisibleButPointerOperable() {
        AtomicBoolean enabled = new AtomicBoolean(true);
        AtomicInteger behavior = new AtomicInteger();
        SemanticAction action = new SemanticAction(
                Component.literal("Create"),
                () -> Component.literal("Creates a new profile with the entered name."),
                null,
                enabled::get,
                behavior::incrementAndGet,
                SemanticSound.ACTIVATION,
                false, false);
        SemanticActionControl c = new SemanticActionControl(action,
                sound -> { },
                null,
                SemanticActionControl.PointerRouting.MANUAL);
        c.setAvailable(true);
        c.setBounds(10, 20, 90, 18);

        // Invisible to focus traversal (the field is the keyboard path)…
        assertNull(c.nextFocusPath(new FocusNavigationEvent.TabNavigation(true)));
        // …and to selection keys even if something else focused it…
        c.setFocused(true);
        assertFalse(c.keyPressed(new KeyEvent(257, 0, 0)));
        assertEquals(0, behavior.get());
        // …but fully operable by pointer, gated by the dynamic condition.
        assertTrue(c.activateFromPointer(11, 21, 0));
        assertEquals(1, behavior.get());
        enabled.set(false);
        assertFalse(c.activateFromPointer(11, 21, 0));
        assertEquals(1, behavior.get());
    }

    // ------------------------------------------------------------------
    // Multiple vanilla-backed (SemanticSound.NONE) actions coexisting:
    // each activates exactly once and none dispatches semantic feedback,
    // even when an adapter is wired — vanilla owns the click sound.
    // ------------------------------------------------------------------

    @Test
    void multipleVanillaBackedActionsActivateOnceEachWithoutSemanticFeedback() {
        AtomicInteger behaviorA = new AtomicInteger();
        AtomicInteger behaviorB = new AtomicInteger();
        AtomicInteger sounds = new AtomicInteger();
        SemanticFeedback countingFeedback = sound -> {
            if (sound == SemanticSound.ACTIVATION) sounds.incrementAndGet();
        };
        SemanticAction a = new SemanticAction(Component.literal("Apply"),
                () -> Component.literal("Applies the selected color and returns."), null,
                () -> true, behaviorA::incrementAndGet, SemanticSound.NONE, true, true);
        SemanticAction b = new SemanticAction(Component.literal("Cancel"),
                () -> Component.literal("Discards any edits and returns."), null,
                () -> true, behaviorB::incrementAndGet, SemanticSound.NONE, true, true);

        // The vanilla-backed route calls activate(null, null) — vanilla owns
        // sound and visuals; passing an adapter must still stay silent.
        assertTrue(a.activate(null, null));
        assertTrue(b.activate(countingFeedback, null));
        assertEquals(1, behaviorA.get());
        assertEquals(1, behaviorB.get());
        assertEquals(0, sounds.get(), "SemanticSound.NONE must never dispatch feedback");
    }

    // ------------------------------------------------------------------
    // Traversal direction symmetry: backward (Shift+Tab) obeys the same
    // availability rules as forward Tab.
    // ------------------------------------------------------------------

    @Test
    void backwardTraversalFollowsTheSameAvailabilityRules() {
        AtomicInteger behavior = new AtomicInteger();
        SemanticActionControl c = control(buttonAction(() -> true, behavior));
        c.setAvailable(true);
        c.setBounds(10, 20, 90, 18);

        assertNotNull(c.nextFocusPath(new FocusNavigationEvent.TabNavigation(false)));
        c.setAvailable(false);
        assertNull(c.nextFocusPath(new FocusNavigationEvent.TabNavigation(false)));
        c.setAvailable(true);
        assertNotNull(c.nextFocusPath(new FocusNavigationEvent.TabNavigation(false)));
        assertEquals(0, behavior.get());
    }

    // ------------------------------------------------------------------
    // The install-phase shape (Phase A pack-browser rollout): one
    // long-lived control whose gate reads external phase state — accepted
    // while the phase is actionable, rejected-without-feedback while not,
    // traversal-eligible again when the phase returns, all without
    // reconstruction.
    // ------------------------------------------------------------------

    @Test
    void phaseGatedInstallRejectsWhileInactiveAndRecoversWithoutReconstruction() {
        AtomicInteger phase = new AtomicInteger(0); // 0 = IDLE, 1 = RESOLVING
        AtomicInteger behavior = new AtomicInteger();
        AtomicInteger sounds = new AtomicInteger();
        AtomicInteger visuals = new AtomicInteger();
        SemanticAction action = new SemanticAction(
                Component.literal("Install Test Pack"),
                () -> Component.literal("Downloads the pack into your resourcepacks folder."),
                () -> phase.get() == 0 ? Component.empty() : Component.literal("Resolving a compatible version…"),
                () -> phase.get() == 0,
                behavior::incrementAndGet,
                SemanticSound.ACTIVATION,
                true, true);
        SemanticActionControl c = control(action, sounds, visuals);
        c.setAvailable(true);
        c.setBounds(10, 20, 80, 18);

        // IDLE: accepted exactly once (action + sound + visual).
        assertTrue(c.activateFromPointer(11, 21, 0));
        assertEquals(1, behavior.get());
        assertEquals(1, sounds.get());
        assertEquals(1, visuals.get());

        // RESOLVING: same instance, rejected — no behavior, no success
        // feedback of any kind; still narratable; traversal skips it.
        phase.set(1);
        assertFalse(c.activateFromPointer(11, 21, 0));
        c.setFocused(true);
        assertTrue(c.keyPressed(new KeyEvent(257, 0, 0)), "selection key consumed so it cannot fall through");
        assertEquals(1, behavior.get());
        assertEquals(1, sounds.get());
        assertEquals(1, visuals.get());
        assertTrue(c.isActive(), "phase-gated controls remain narration candidates");
        assertNull(c.nextFocusPath(new FocusNavigationEvent.TabNavigation(true)));

        // Back to IDLE (a failed install's Retry): the same control serves.
        phase.set(0);
        assertTrue(c.activateFromPointer(11, 21, 0));
        assertEquals(2, behavior.get());
        assertEquals(2, sounds.get());
        assertEquals(2, visuals.get());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static SemanticAction buttonAction(java.util.function.BooleanSupplier enabled,
                                               AtomicInteger behavior) {
        return SemanticAction.button(Component.literal("Action"),
                () -> Component.literal("What this action does."),
                Component::empty,
                enabled,
                behavior::incrementAndGet);
    }

    private static SemanticActionControl control(SemanticAction action) {
        return control(action, new AtomicInteger(), new AtomicInteger());
    }

    private static SemanticActionControl control(SemanticAction action,
                                                 AtomicInteger sounds,
                                                 AtomicInteger visuals) {
        return new SemanticActionControl(action,
                sound -> {
                    if (sound == SemanticSound.ACTIVATION) sounds.incrementAndGet();
                },
                visuals::incrementAndGet,
                SemanticActionControl.PointerRouting.MANUAL);
    }
}
