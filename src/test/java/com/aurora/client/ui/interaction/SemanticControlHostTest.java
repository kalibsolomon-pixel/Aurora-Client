package com.aurora.client.ui.interaction;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SemanticControlHostTest {
    @Test
    void registrationIsIdentityIdempotentAndOrdered() {
        Fixture f = new Fixture();
        SemanticActionControl a = control("A", () -> true, new AtomicInteger());
        SemanticActionControl b = control("B", () -> true, new AtomicInteger());

        assertTrue(f.host.register(a));
        assertFalse(f.host.register(a));
        assertTrue(f.host.register(b));
        assertEquals(List.of(a, b), f.host.controls());
        assertEquals(2, f.host.size());
    }

    @Test
    void rebuildAddsEachControlOnceInTraversalOrder() {
        Fixture f = new Fixture();
        SemanticActionControl a = control("A", () -> true, new AtomicInteger());
        SemanticActionControl b = control("B", () -> true, new AtomicInteger());
        f.host.register(a);
        f.host.register(b);

        f.host.beginRebuild();
        f.host.finishRebuild();
        assertEquals(List.of(a, b), f.attached);

        // Minecraft clears Screen children before the next init; model that
        // and prove the retained registration list does not itself duplicate.
        f.attached.clear();
        f.focused.set(a);
        a.setFocused(true);
        f.host.beginRebuild();
        assertNull(f.focused.get(), "resize/re-init must not retain an old child focus owner");
        f.host.finishRebuild();
        assertEquals(List.of(a, b), f.attached);
    }

    @Test
    void liveRegistrationAndClearUseTheWidgetLifecycle() {
        Fixture f = new Fixture();
        f.host.beginRebuild();
        f.host.finishRebuild();
        SemanticActionControl a = control("A", () -> true, new AtomicInteger());
        SemanticActionControl b = control("B", () -> true, new AtomicInteger());

        f.host.register(a);
        f.host.register(b);
        assertEquals(List.of(a, b), f.attached);
        f.focused.set(b);
        b.setFocused(true);

        f.host.clear();
        assertTrue(f.attached.isEmpty());
        assertNull(f.focused.get());
        assertEquals(0, f.host.size());
        assertFalse(a.isAvailable());
        assertFalse(b.isFocused());
    }

    @Test
    void availabilitySweepRemovesStaleFocusButKeepsVisibleFocus() {
        Fixture f = new Fixture();
        SemanticActionControl a = control("A", () -> true, new AtomicInteger());
        SemanticActionControl b = control("B", () -> true, new AtomicInteger());
        f.host.register(a);
        f.host.register(b);
        f.focused.set(b);
        b.setFocused(true);

        f.host.beginAvailabilitySweep();
        f.host.setAvailable(a, true);
        f.host.finishAvailabilitySweep();
        assertTrue(a.isAvailable());
        assertFalse(b.isAvailable());
        assertNull(f.focused.get(), "the off-viewport owner must lose vanilla focus");

        f.focused.set(a);
        a.setFocused(true);
        f.host.beginAvailabilitySweep();
        f.host.setAvailable(a, true);
        f.host.finishAvailabilitySweep();
        assertSame(a, f.focused.get());
    }

    @Test
    void disabledAndUnavailableControlsCannotActivate() {
        Fixture f = new Fixture();
        AtomicInteger calls = new AtomicInteger();
        SemanticActionControl disabled = control("Disabled", () -> false, calls);
        SemanticActionControl hidden = control("Hidden", () -> true, calls);
        f.host.register(disabled);
        f.host.register(hidden);

        disabled.setBounds(0, 0, 20, 20);
        hidden.setBounds(0, 0, 20, 20);
        f.host.beginAvailabilitySweep();
        f.host.setAvailable(disabled, true);
        disabled.setFocused(true);
        hidden.setFocused(true);

        assertFalse(disabled.activateFromPointer(5, 5, 0));
        assertTrue(disabled.keyPressed(new KeyEvent(257, 0, 0)),
                "a targeted disabled selection is consumed");
        assertFalse(hidden.activateFromPointer(5, 5, 0));
        assertFalse(hidden.keyPressed(new KeyEvent(257, 0, 0)));
        assertEquals(0, calls.get());
    }

    @Test
    void pointerFocusUsesFirstRegisteredAvailableContainingControl() {
        Fixture f = new Fixture();
        SemanticActionControl hidden = control("Hidden", () -> true, new AtomicInteger());
        SemanticActionControl first = control("First", () -> true, new AtomicInteger());
        SemanticActionControl second = control("Second", () -> true, new AtomicInteger());
        for (SemanticActionControl c : List.of(hidden, first, second)) {
            c.setBounds(0, 0, 20, 20);
            f.host.register(c);
        }
        f.host.setAvailable(first, true);
        f.host.setAvailable(second, true);

        assertSame(first, f.host.focusAt(5, 5));
        assertSame(first, f.focused.get());
    }

    private static SemanticActionControl control(String name,
                                                 java.util.function.BooleanSupplier enabled,
                                                 AtomicInteger calls) {
        return new SemanticActionControl(SemanticAction.button(
                Component.literal(name), Component::empty, Component::empty,
                enabled, calls::incrementAndGet),
                SemanticFeedback.NONE, null,
                SemanticActionControl.PointerRouting.MANUAL);
    }

    private static final class Fixture {
        final List<SemanticActionControl> attached = new ArrayList<>();
        final AtomicReference<GuiEventListener> focused = new AtomicReference<>();
        final SemanticControlHost host = new SemanticControlHost(
                attached::add, attached::remove, focused::get, focused::set);
    }
}
