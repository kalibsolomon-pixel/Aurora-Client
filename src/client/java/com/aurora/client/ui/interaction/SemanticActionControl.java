package com.aurora.client.ui.interaction;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Invisible screen-lifecycle adapter for a custom-painted semantic action.
 * It supplies focus traversal, keyboard activation, and narration while the
 * existing component remains the sole owner of pixels and pointer geometry.
 */
public final class SemanticActionControl extends AbstractWidget {
    public enum PointerRouting { MANUAL, WIDGET }

    private final SemanticAction action;
    private final SemanticFeedback feedback;
    private final Runnable acceptedVisualFeedback;
    private final PointerRouting pointerRouting;

    private boolean available;
    private boolean pointerHovered;

    public SemanticActionControl(SemanticAction action,
                                 SemanticFeedback feedback,
                                 Runnable acceptedVisualFeedback,
                                 PointerRouting pointerRouting) {
        super(0, 0, 0, 0, Objects.requireNonNull(action, "action").accessibleName());
        this.action = action;
        this.feedback = feedback != null ? feedback : SemanticFeedback.NONE;
        this.acceptedVisualFeedback = acceptedVisualFeedback;
        this.pointerRouting = pointerRouting != null ? pointerRouting : PointerRouting.MANUAL;
    }

    public SemanticAction action() {
        return action;
    }

    public void setBounds(int x, int y, int width, int height) {
        setX(x);
        setY(y);
        setWidth(width);
        setHeight(height);
    }

    public void setAvailable(boolean available) {
        this.available = available;
        if (!available) pointerHovered = false;
    }

    /** Whether the hosting screen's current-frame sweep marked this control interactable. */
    public boolean isAvailable() {
        return available;
    }

    public void updatePointer(double mouseX, double mouseY) {
        pointerHovered = available && contains(mouseX, mouseY);
    }

    /** Pointer entry point used by manually routed, clipped setting rows. */
    public boolean activateFromPointer(double mouseX, double mouseY, int button) {
        if (!available || button != 0 || !contains(mouseX, mouseY)) return false;
        return action.activate(feedback, acceptedVisualFeedback);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClicked) {
        if (pointerRouting != PointerRouting.WIDGET) return false;
        return activateFromPointer(event.x(), event.y(), event.button());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isFocused() || !available || !action.keyboardEligible() || !event.isSelection()) {
            return false;
        }
        // A selection key targeted this control even when the action rejects
        // it as disabled; consume it so it cannot activate another control.
        action.activate(feedback, acceptedVisualFeedback);
        return true;
    }

    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        if (!available || !action.enabled() || !action.focusable() || isFocused()) return null;
        return ComponentPath.leaf(this);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return pointerRouting == PointerRouting.WIDGET && available && contains(mouseX, mouseY);
    }

    @Override
    public boolean isActive() {
        // Disabled controls remain narration candidates so their disabled
        // state can be announced; focus traversal is gated separately above.
        return available;
    }

    @Override
    public NarratableEntry.NarrationPriority narrationPriority() {
        if (!available) return NarratableEntry.NarrationPriority.NONE;
        if (isFocused()) return NarratableEntry.NarrationPriority.FOCUSED;
        if (pointerHovered) return NarratableEntry.NarrationPriority.HOVERED;
        return NarratableEntry.NarrationPriority.NONE;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, wrapDefaultNarrationMessage(action.accessibleName()));
        addIfPresent(output, NarratedElementType.HINT, action.description());
        addIfPresent(output, NarratedElementType.HINT, action.state());
        if (!action.enabled()) {
            output.add(NarratedElementType.HINT,
                    Component.translatable("narration.aurora.control.disabled"));
        } else if (isFocused()) {
            output.add(NarratedElementType.USAGE,
                    Component.translatable("narration.button.usage.focused"));
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Intentionally invisible: the existing Aurora component owns pixels.
    }

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseY >= getY()
                && mouseX < getRight() && mouseY < getBottom();
    }

    private static void addIfPresent(NarrationElementOutput output,
                                     NarratedElementType type,
                                     Component component) {
        if (component != null && !component.getString().isBlank()) output.add(type, component);
    }
}
