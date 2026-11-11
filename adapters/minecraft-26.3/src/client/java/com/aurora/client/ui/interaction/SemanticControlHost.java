package com.aurora.client.ui.interaction;

import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Small lifecycle owner for custom-painted semantic controls hosted by a
 * {@code Screen}. Components still own their actions, bounds, pixels, and
 * pointer activation; this helper owns only the repeated host mechanics:
 * deterministic identity registration, widget-list rebuilds, availability
 * reset, and stale vanilla-focus cleanup.
 *
 * <p>The add/remove callbacks deliberately stay screen-supplied because
 * Minecraft exposes those operations as protected methods. No layout or
 * concrete setting type enters this class.
 */
public final class SemanticControlHost {
    private final Consumer<SemanticActionControl> addWidget;
    private final Consumer<SemanticActionControl> removeWidget;
    private final Supplier<GuiEventListener> focused;
    private final Consumer<GuiEventListener> setFocused;
    private final List<SemanticActionControl> controls = new ArrayList<>();
    private boolean widgetsLive;

    public SemanticControlHost(Consumer<SemanticActionControl> addWidget,
                               Consumer<SemanticActionControl> removeWidget,
                               Supplier<GuiEventListener> focused,
                               Consumer<GuiEventListener> setFocused) {
        this.addWidget = addWidget;
        this.removeWidget = removeWidget;
        this.focused = focused;
        this.setFocused = setFocused;
    }

    /** Registers once by identity and preserves first-registration order. */
    public boolean register(SemanticActionControl control) {
        if (control == null || containsIdentity(control)) return false;
        controls.add(control);
        reset(control);
        if (widgetsLive) addWidget.accept(control);
        return true;
    }

    /** Removes one dynamic control and clears focus if it owned it. */
    public boolean unregister(SemanticActionControl control) {
        int index = indexOfIdentity(control);
        if (index < 0) return false;
        controls.remove(index);
        if (focused.get() == control) setFocused.accept(null);
        reset(control);
        if (widgetsLive) removeWidget.accept(control);
        return true;
    }

    /**
     * Starts a Screen widget rebuild. Minecraft has already cleared the old
     * child list before {@code init}; the controls remain registered here so
     * {@link #finishRebuild()} can re-add each exactly once.
     */
    public void beginRebuild() {
        widgetsLive = false;
        GuiEventListener current = focused.get();
        if (current instanceof SemanticActionControl control && containsIdentity(control)) {
            setFocused.accept(null);
        }
        for (SemanticActionControl control : controls) reset(control);
    }

    /** Re-adds the registered controls in deterministic traversal order. */
    public void finishRebuild() {
        for (SemanticActionControl control : controls) {
            reset(control);
            addWidget.accept(control);
        }
        widgetsLive = true;
    }

    /** Removes every registration; intended for a true component rebuild. */
    public void clear() {
        if (focused.get() instanceof SemanticActionControl control && containsIdentity(control)) {
            setFocused.accept(null);
        }
        if (widgetsLive) {
            for (SemanticActionControl control : controls) removeWidget.accept(control);
        }
        for (SemanticActionControl control : controls) reset(control);
        controls.clear();
    }

    /** Frame-start default: nothing is interactive until the host proves it visible. */
    public void beginAvailabilitySweep() {
        for (SemanticActionControl control : controls) control.setAvailable(false);
    }

    /** Marks one registered control with the host's current bounds truth. */
    public void setAvailable(SemanticActionControl control, boolean available) {
        if (containsIdentity(control)) control.setAvailable(available);
    }

    /**
     * Drops vanilla focus when its semantic owner remained unavailable after
     * the sweep. Availability already rejects activation; clearing focus also
     * prevents invisible focus chrome/ownership from lingering.
     */
    public void finishAvailabilitySweep() {
        GuiEventListener current = focused.get();
        if (current instanceof SemanticActionControl control
                && containsIdentity(control) && !control.isAvailable()) {
            setFocused.accept(null);
        }
    }

    /** Immediately invalidates the whole host (tab switch/removal boundary). */
    public void deactivateAll() {
        beginAvailabilitySweep();
        finishAvailabilitySweep();
    }

    /** Applies the accepted pointer-to-semantic-focus rule without activating. */
    public SemanticActionControl focusAt(double mouseX, double mouseY) {
        for (SemanticActionControl control : controls) {
            if (control.isAvailable()
                    && mouseX >= control.getX() && mouseX < control.getRight()
                    && mouseY >= control.getY() && mouseY < control.getBottom()) {
                setFocused.accept(control);
                return control;
            }
        }
        return null;
    }

    /** Drops focus only when this host owns it. */
    public void dropFocus() {
        GuiEventListener current = focused.get();
        if (current instanceof SemanticActionControl control && containsIdentity(control)) {
            setFocused.accept(null);
        }
    }

    public int size() {
        return controls.size();
    }

    public List<SemanticActionControl> controls() {
        return Collections.unmodifiableList(controls);
    }

    private boolean containsIdentity(SemanticActionControl wanted) {
        return indexOfIdentity(wanted) >= 0;
    }

    private int indexOfIdentity(SemanticActionControl wanted) {
        if (wanted == null) return -1;
        for (int i = 0; i < controls.size(); i++) {
            if (controls.get(i) == wanted) return i;
        }
        return -1;
    }

    private static void reset(SemanticActionControl control) {
        control.setAvailable(false);
        control.setFocused(false);
    }
}
