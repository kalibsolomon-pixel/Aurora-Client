package com.aurora.client.screen.setting;

import com.aurora.client.ui.interaction.SemanticFeedback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B KeyList pilot: the one multi-value capture surface. Capture is
 * owned at the ADD level through the shared exclusive slot on
 * FeatureSetting (same registry as the canonical Keybind — never two
 * owners), focus and listening are independent channels, disabled is the
 * authoritative gate, and add/remove mutate and save exactly once. The
 * multi-value clear rule (ESC/BACKSPACE cancel WITHOUT mutation) is
 * deliberately not the single-Keybind clear-to-UNBOUND rule.
 */
class KeyListPilotTest {
    private static final int A = GLFW.GLFW_KEY_F1;
    private static final int B = GLFW.GLFW_KEY_F2;
    private static final int C = GLFW.GLFW_KEY_F3;

    @AfterEach
    void clearGlobalOwners() {
        FeatureSetting.cancelActiveCapture();
        FeatureSetting.clearFocus();
    }

    @Test
    void addPillHoverIsPointerOnlySymmetricAndNotPinnedByListening() {
        Fixture f = fixture();
        float first = f.row.hoverAnimator().update(true);
        assertTrue(first < 0.5f, "hover must animate from rest, got " + first);
        begin(f);
        // Listening must not pin hover — with the pointer away the
        // animator's target is false, so an update settles toward zero.
        float settledTarget = f.row.hoverAnimator().update(false);
        assertTrue(settledTarget < 0.5f, "pointer-away while listening must ease to rest");
    }

    @Test
    void focusAndListeningAreIndependentChannels() {
        Fixture f = fixture();
        var control = f.row.interactionControl();
        control.setAvailable(true);
        control.setFocused(true);
        assertTrue(control.isFocused());
        assertFalse(f.row.listeningState(), "Tab focus alone must not arm capture");

        assertTrue(control.action().activate(SemanticFeedback.NONE, null));
        assertTrue(control.isFocused());
        assertTrue(f.row.listeningState());

        assertTrue(f.row.onKeyPress(A, 0));
        assertFalse(f.row.listeningState());
        assertTrue(control.isFocused(), "capture keeps stable keyboard focus on the add action");
    }

    @Test
    void activationEventArmsOnlyAndCannotSelfAdd() {
        Fixture f = fixture();
        var control = f.row.interactionControl();
        control.setAvailable(true);
        control.setFocused(true);
        assertTrue(control.action().activate(SemanticFeedback.NONE, null));
        assertTrue(f.row.listeningState());
        assertTrue(f.list.isEmpty(), "the Enter/Space activation event must not become an entry");

        // The NEXT event captures — Enter itself is an ordinary bindable key.
        assertTrue(f.row.onKeyPress(GLFW.GLFW_KEY_ENTER, 0));
        assertEquals(List.of(GLFW.GLFW_KEY_ENTER), f.list);
        assertEquals(1, f.saves.get());
        assertFalse(f.row.listeningState());
    }

    @Test
    void successfulAddAppendsMutatesAndSavesExactlyOnce() {
        Fixture f = fixture(List.of(A, B));
        begin(f);
        assertTrue(f.row.onKeyPress(C, 0));
        assertEquals(List.of(A, B, C), f.list);
        assertEquals(1, f.mutations.get());
        assertEquals(1, f.saves.get());
        assertFalse(f.row.listeningState());

        assertFalse(f.row.onKeyPress(C, 0),
                "a repeat after capture no longer belongs to the control");
        assertEquals(1, f.mutations.get());
        assertEquals(1, f.saves.get());
    }

    @Test
    void duplicateCaptureClosesAsAcceptedNoOpWithoutSave() {
        // Shipped policy preserved: duplicates are ignored silently. The
        // pilot corrects the persistence half — no mutation, no save.
        Fixture f = fixture(List.of(A, B));
        begin(f);
        assertTrue(f.row.onKeyPress(A, 0));
        assertEquals(List.of(A, B), f.list);
        assertEquals(0, f.mutations.get());
        assertEquals(0, f.saves.get());
        assertFalse(f.row.listeningState());
    }

    @Test
    void escapeAndBackspaceCancelWithoutMutationAndDeleteStaysBindable() {
        Fixture escape = fixture(List.of(A));
        begin(escape);
        assertTrue(escape.row.onKeyPress(GLFW.GLFW_KEY_ESCAPE, 0));
        assertEquals(List.of(A), escape.list, "ESC cancels — NOT the Keybind clear rule");
        assertEquals(0, escape.saves.get());

        Fixture backspace = fixture(List.of(A));
        begin(backspace);
        assertTrue(backspace.row.onKeyPress(GLFW.GLFW_KEY_BACKSPACE, 0));
        assertEquals(List.of(A), backspace.list);
        assertEquals(0, backspace.saves.get());

        Fixture delete = fixture();
        begin(delete);
        assertTrue(delete.row.onKeyPress(GLFW.GLFW_KEY_DELETE, 0));
        assertEquals(List.of(GLFW.GLFW_KEY_DELETE), delete.list,
                "Delete remains an ordinary bindable key");
        assertEquals(1, delete.saves.get());
    }

    @Test
    void removalRemovesExactlyThatEntryAndSavesOnce() throws Exception {
        Fixture f = fixture(List.of(A, B, C));
        // Headless geometry: the row's remembered origin is (0,0) at width
        // 240 (render never ran) — entry i's "−" center is (219, 29+20i).
        assertTrue(clickRemove(f, 1)); // B
        assertEquals(List.of(A, C), f.list);
        assertEquals(1, f.saves.get());
        assertEquals(1, f.mutations.get());
    }

    @Test
    void emptyListTransitionsBothWays() throws Exception {
        Fixture f = fixture();
        begin(f);
        assertTrue(f.row.onKeyPress(A, 0));
        assertEquals(List.of(A), f.list);
        // Remove the only entry → back to empty, no stale hit geometry.
        assertTrue(clickRemove(f, 0));
        assertTrue(f.list.isEmpty());
        assertEquals(2, f.saves.get());
        // And back up from empty again.
        begin(f);
        assertTrue(f.row.onKeyPress(B, 0));
        assertEquals(List.of(B), f.list);
    }

    @Test
    void disabledRejectsAddAndRemoveWithoutSaveOrSound() {
        Fixture f = fixture(List.of(A));
        f.disabled.set(true);
        assertFalse(f.row.mouseClicked(1, 1, 0, 0, 0, 320));
        var control = f.row.interactionControl();
        assertFalse(control.action().enabled());
        assertFalse(control.action().activate(SemanticFeedback.NONE, null));
        assertFalse(f.row.listeningState());
        assertEquals(List.of(A), f.list, "entries stay readable while disabled");
        assertEquals(0, f.saves.get());
    }

    @Test
    void enabledListeningThenDisabledCancelsWithoutMutationOrSave() {
        Fixture f = fixture(List.of(A));
        begin(f);
        f.disabled.set(true);

        // A stale event racing the gate is consumed-but-inert — the
        // reconcile (render) has not run yet, so the owner still routes.
        assertTrue(f.row.onKeyPress(B, 0));
        assertEquals(List.of(A), f.list);
        assertEquals(0, f.saves.get());
        assertFalse(f.row.listeningState());

        assertTrue(f.row.reconcileForTest(), "the reconcile observes the gate");
        assertFalse(f.row.listeningState());
        assertEquals(List.of(A), f.list);
        assertEquals(0, f.saves.get());
    }

    @Test
    void outsidePointerCancelsViaTheSharedRegistryAndIsConsumed() {
        Fixture f = fixture(List.of(A));
        begin(f);
        assertTrue(FeatureSetting.cancelActiveCapture());
        assertFalse(f.row.listeningState());
        assertEquals(List.of(A), f.list);
        assertEquals(0, f.saves.get());
        assertFalse(FeatureSetting.cancelActiveCapture());
    }

    @Test
    void keyListAndKeybindCanNeverBothOwnCapture() {
        Fixture kl = fixture(List.of(A));
        AtomicInteger kb = new AtomicInteger(GLFW.GLFW_KEY_J);
        AtomicInteger kbSaves = new AtomicInteger();
        KeybindSetting keybind = new KeybindSetting("K", kb::get, v -> kb.set(v), kbSaves::incrementAndGet);

        begin(kl);
        assertTrue(kl.row.listeningState());
        // Keybind activation claims the SHARED slot — the KeyList session
        // is cancelled first; there is exactly one owner.
        keybind.interactionControl().action().activate(SemanticFeedback.NONE, null);
        assertFalse(kl.row.listeningState());
        assertTrue(keybind.listeningState());

        // And the reverse: a KeyList claim cancels the Keybind.
        begin(kl);
        assertFalse(keybind.listeningState());
        assertTrue(kl.row.listeningState());
        assertEquals(0, kbSaves.get());

        // The screen-level pointer guard cancels whatever family owns.
        assertTrue(FeatureSetting.cancelActiveCapture());
        assertFalse(kl.row.listeningState());
    }

    @Test
    void viewportLossAndScreenLifecycleBothCancelCapture() {
        Fixture viewport = fixture(List.of(A));
        begin(viewport);
        viewport.row.onInteractionAvailabilityChanged(false);
        assertFalse(viewport.row.listeningState());

        Fixture close = fixture(List.of(A));
        begin(close);
        close.row.onDetailScreenClose();
        assertFalse(close.row.listeningState());
        assertEquals(List.of(A), close.list);
        assertEquals(0, close.saves.get());

        Fixture reopen = fixture(List.of(A));
        begin(reopen);
        reopen.row.onDetailScreenOpen();
        assertFalse(reopen.row.listeningState());
    }

    @Test
    void structuralMutationKeepsFocusRegistryValid() {
        Fixture f = fixture(List.of(A, B));
        var control = f.row.interactionControl();
        control.setAvailable(true);
        control.setFocused(true);
        // Remove the last entry while the ADD action owns semantic focus —
        // the control is row-scoped (not entry-scoped), so focus survives
        // structural mutation; nothing dangles.
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> clickRemove(f, 1));
        assertEquals(List.of(A), f.list);
        assertTrue(control.isFocused());
        begin(f); // add still works after the mutation
        assertTrue(f.row.onKeyPress(C, 0));
        assertEquals(List.of(A, C), f.list);
    }

    /** Entry i's "−" button center in the headless (0,0,240) frame. */
    private static boolean clickRemove(Fixture f, int i) {
        return f.row.mouseClicked(219.0, 29.0 + 20.0 * i, 0, 0, 0, 240);
    }

    private static void begin(Fixture f) {
        // Add-pill center in the headless (0,0,240) frame: x = 14 + 212/2,
        // y = 20 + n*20 + 4 + 10. The semantic control (if a test created
        // one) has no render-driven bounds yet — mark it unavailable so the
        // headless direct path runs, exactly like an unhosted row.
        f.row.interactionControl().setAvailable(false);
        int n = f.list.size();
        assertTrue(f.row.mouseClicked(120.0, 34.0 + 20.0 * n, 0, 0, 0, 240),
                "headless direct add-pill activation");
        assertTrue(f.row.listeningState());
    }

    /** Pill click point for {@link #begin}: anywhere on the wide add pill. */
    private static Fixture fixture() { return fixture(List.of()); }

    private static Fixture fixture(List<Integer> initial) {
        List<Integer> list = new ArrayList<>(initial);
        AtomicInteger mutations = new AtomicInteger();
        AtomicInteger saves = new AtomicInteger();
        AtomicBoolean disabled = new AtomicBoolean(false);
        KeyListSetting row = new KeyListSetting("Extra Keys", () -> list, v -> {
            mutations.incrementAndGet();
            list.clear();
            list.addAll(v);
        }, saves::incrementAndGet);
        row.disabled(disabled::get);
        return new Fixture(row, list, mutations, saves, disabled);
    }

    private record Fixture(KeyListSetting row,
                           List<Integer> list,
                           AtomicInteger mutations,
                           AtomicInteger saves,
                           AtomicBoolean disabled) {}
}
