package com.aurora.client.ui.interaction;

import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationThunk;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SemanticActionControlTest {
    @Test
    void pointerEnterAndSpaceConvergeOnOneExactlyOncePath() {
        AtomicInteger behavior = new AtomicInteger();
        AtomicInteger sounds = new AtomicInteger();
        AtomicInteger visuals = new AtomicInteger();
        SemanticAction action = buttonAction(() -> true, behavior);
        SemanticActionControl control = control(action, sounds, visuals);
        control.setAvailable(true);
        control.setBounds(10, 20, 90, 18);

        assertTrue(control.activateFromPointer(11, 21, 0));
        assertEquals(1, behavior.get());
        assertEquals(1, sounds.get());
        assertEquals(1, visuals.get());

        control.setFocused(true);
        assertTrue(control.keyPressed(new KeyEvent(257, 0, 0))); // Enter
        assertEquals(2, behavior.get());
        assertEquals(2, sounds.get());
        assertEquals(2, visuals.get());

        assertTrue(control.keyPressed(new KeyEvent(32, 0, 0))); // Space
        assertEquals(3, behavior.get());
        assertEquals(3, sounds.get());
        assertEquals(3, visuals.get());
    }

    @Test
    void disabledActionRejectsPointerKeyboardSoundAndVisualFeedback() {
        AtomicBoolean enabled = new AtomicBoolean(false);
        AtomicInteger behavior = new AtomicInteger();
        AtomicInteger sounds = new AtomicInteger();
        AtomicInteger visuals = new AtomicInteger();
        SemanticAction action = buttonAction(enabled::get, behavior);
        SemanticActionControl control = control(action, sounds, visuals);
        control.setAvailable(true);
        control.setBounds(10, 20, 90, 18);
        control.setFocused(true);

        assertFalse(control.activateFromPointer(11, 21, 0));
        assertTrue(control.keyPressed(new KeyEvent(257, 0, 0)));
        assertTrue(control.keyPressed(new KeyEvent(32, 0, 0)));
        assertEquals(0, behavior.get());
        assertEquals(0, sounds.get());
        assertEquals(0, visuals.get());
        assertTrue(control.isActive(), "disabled controls remain narration candidates");
        assertNull(control.nextFocusPath(new FocusNavigationEvent.TabNavigation(true)),
                "disabled controls are skipped by focus traversal");
    }

    @Test
    void selectionKeyRequiresFocusAndAvailability() {
        AtomicInteger behavior = new AtomicInteger();
        SemanticActionControl control = control(buttonAction(() -> true, behavior),
                new AtomicInteger(), new AtomicInteger());
        control.setBounds(10, 20, 90, 18);
        control.setAvailable(true);

        assertFalse(control.keyPressed(new KeyEvent(257, 0, 0)));
        assertNotNull(control.nextFocusPath(new FocusNavigationEvent.TabNavigation(true)));
        control.setFocused(true);
        control.setAvailable(false);
        assertFalse(control.keyPressed(new KeyEvent(257, 0, 0)));
        assertEquals(0, behavior.get());
    }

    @Test
    void narrationCarriesRoleNameDescriptionStateAndDisabledMetadata() {
        SemanticAction action = SemanticAction.button(
                Component.literal("Test Sound"),
                () -> Component.literal("Plays the selected alert sound."),
                () -> Component.literal("Current sound: Pling"),
                () -> false,
                () -> fail("disabled narration must not activate behavior"));
        SemanticActionControl control = control(action, new AtomicInteger(), new AtomicInteger());
        control.setAvailable(true);
        control.setFocused(true);

        CapturingNarration narration = new CapturingNarration();
        control.updateNarration(narration);

        String title = narration.joined(NarratedElementType.TITLE);
        String hints = narration.joined(NarratedElementType.HINT);
        assertTrue(title.contains("Test Sound"));
        assertTrue(title.contains("gui.narrate.button") || title.toLowerCase().contains("button"));
        assertTrue(hints.contains("Plays the selected alert sound."));
        assertTrue(hints.contains("Current sound: Pling"));
        assertTrue(hints.contains("narration.aurora.control.disabled")
                || hints.contains("Disabled"));
        assertTrue(narration.joined(NarratedElementType.USAGE).isEmpty());
    }

    private static SemanticAction buttonAction(java.util.function.BooleanSupplier enabled,
                                               AtomicInteger behavior) {
        return SemanticAction.button(Component.literal("Test Sound"),
                () -> Component.literal("Preview the selected alert sound."),
                Component::empty,
                enabled,
                behavior::incrementAndGet);
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

    private static final class CapturingNarration implements NarrationElementOutput {
        private final Map<NarratedElementType, List<String>> values =
                new EnumMap<>(NarratedElementType.class);

        @Override
        public void add(NarratedElementType type, NarrationThunk<?> thunk) {
            thunk.getText(text -> values.computeIfAbsent(type, ignored -> new ArrayList<>()).add(text));
        }

        @Override
        public NarrationElementOutput nest() {
            return this;
        }

        String joined(NarratedElementType type) {
            return String.join(" ", values.getOrDefault(type, List.of()));
        }
    }
}
