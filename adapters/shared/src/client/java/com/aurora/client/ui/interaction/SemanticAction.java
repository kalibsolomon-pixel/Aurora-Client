package com.aurora.client.ui.interaction;

import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * One UI action's behavior and accessibility metadata, independent of its
 * painter and input route. This is the authoritative enabled-state gate:
 * every pointer and keyboard path must converge on {@link #activate}.
 */
public final class SemanticAction {
    private final Component accessibleName;
    private final Supplier<Component> description;
    private final Supplier<Component> state;
    private final BooleanSupplier enabled;
    private final Runnable onActivate;
    private final SemanticSound sound;
    private final boolean focusable;
    private final boolean keyboardEligible;

    public SemanticAction(Component accessibleName,
                          Supplier<Component> description,
                          Supplier<Component> state,
                          BooleanSupplier enabled,
                          Runnable onActivate,
                          SemanticSound sound,
                          boolean focusable,
                          boolean keyboardEligible) {
        this.accessibleName = Objects.requireNonNull(accessibleName, "accessibleName");
        this.description = description != null ? description : () -> Component.empty();
        this.state = state != null ? state : () -> Component.empty();
        this.enabled = enabled != null ? enabled : () -> true;
        this.onActivate = Objects.requireNonNull(onActivate, "onActivate");
        this.sound = sound != null ? sound : SemanticSound.NONE;
        this.focusable = focusable;
        this.keyboardEligible = keyboardEligible;
    }

    /** Standard button-like action: focusable, keyboard eligible, one UI click. */
    public static SemanticAction button(Component accessibleName,
                                        Supplier<Component> description,
                                        Supplier<Component> state,
                                        BooleanSupplier enabled,
                                        Runnable onActivate) {
        return new SemanticAction(accessibleName, description, state, enabled, onActivate,
                SemanticSound.ACTIVATION, true, true);
    }

    public Component accessibleName() { return accessibleName; }
    public Component description() { return nonNull(description.get()); }
    public Component state() { return nonNull(state.get()); }
    public boolean enabled() { return enabled.getAsBoolean(); }
    public boolean focusable() { return focusable; }
    public boolean keyboardEligible() { return keyboardEligible; }

    /**
     * Performs one accepted activation in a fixed order: visual feedback,
     * semantic sound, then behavior. Disabled actions reject all three.
     */
    public boolean activate(SemanticFeedback feedback, Runnable acceptedVisualFeedback) {
        if (!enabled()) return false;
        if (acceptedVisualFeedback != null) acceptedVisualFeedback.run();
        if (sound != SemanticSound.NONE) {
            (feedback != null ? feedback : SemanticFeedback.NONE).play(sound);
        }
        onActivate.run();
        return true;
    }

    private static Component nonNull(Component component) {
        return component != null ? component : Component.empty();
    }
}
