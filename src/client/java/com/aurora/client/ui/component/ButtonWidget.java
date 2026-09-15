package com.aurora.client.ui.component;

import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticSound;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Vanilla-routable adapter around the canonical shared {@link Button}.
 *
 * <p>Screens that add widgets through Minecraft's {@code addRenderableWidget}
 * pipeline (focus traversal, narration, z-ordering) use this instead of wiring
 * the {@link Widget}-base button manually. The adapter contains <b>zero
 * painting of its own</b> — every pixel comes from {@link Button}, so there is
 * exactly one button implementation in the mod (the same discipline as
 * {@code EditBoxMixin} theming vanilla edit boxes through the token system).
 *
 * <p>Action routing: the shared button is constructed with a <b>no-op</b>
 * action; the real action fires exactly once, through vanilla's
 * {@code onPress} path, so keyboard activation and mouse clicks behave
 * identically to any other vanilla button. Mouse events are additionally
 * forwarded to the delegate purely so its hover/press animations advance.
 *
 * <p>Phase A semantic adoption ({@link #semantic}): an optional
 * {@link SemanticAction} becomes the single owner of the behavior callback.
 * Everything else stays vanilla's — pointer routing, keyboard activation,
 * focus, the UI click sound, and base narration — because vanilla already
 * provides all of them and the semantic layer must adapt that lifecycle
 * rather than stack duplicates on it. Concretely: the action's sound is
 * {@link SemanticSound#NONE} (vanilla plays {@code playDownSound} before
 * {@code onPress} on both the mouse and selection-key paths, so a semantic
 * click would double it), and the action's enabled gate mirrors vanilla's
 * {@code active} flag, which is the one authoritative gate (an inactive
 * widget rejects pointer and selection-key activation before onPress ever
 * runs). Vanilla-focus now also paints the provisional 1 px accent hairline
 * through the shared painter, so keyboard focus is visible on vanilla-backed
 * buttons the same as on semantic ones.</p>
 */
public class ButtonWidget extends AbstractButton {

    private final Runnable onPress;

    /** The single paint implementation (no-op action — see class javadoc). */
    private final Button painter;

    /**
     * Optional Phase A semantic action owning the behavior callback
     * (constructor of {@link #semantic}); null keeps the plain-Runnable
     * contract of every legacy construction site.
     */
    private SemanticAction semanticAction;

    public ButtonWidget(int x, int y, int w, int h, Component label, Runnable onPress) {
        this(x, y, w, h, label, onPress, false);
    }

    public ButtonWidget(int x, int y, int w, int h, Component label, Runnable onPress, boolean primary) {
        super(x, y, w, h, label);
        this.onPress = onPress;
        this.painter = new Button(label, () -> {}, primary);
    }

    /**
     * Vanilla-backed semantic button: behavior owned by a {@link SemanticAction},
     * every service (pointer, keyboard, focus, click sound, narration base)
     * remaining vanilla's. The action deliberately carries
     * {@link SemanticSound#NONE} — vanilla already plays the UI click before
     * {@code onPress} on both activation paths — and an always-true enabled
     * gate mirroring vanilla's {@code active} flag, the authoritative gate.
     * The description supplements the vanilla button narration.
     */
    public static ButtonWidget semantic(int x, int y, int w, int h, Component label,
                                        String description, Runnable onPress) {
        ButtonWidget widget = new ButtonWidget(x, y, w, h, label, onPress);
        widget.semanticAction = new SemanticAction(
                label,
                description != null ? () -> Component.literal(description) : null,
                null,
                () -> true,
                onPress,
                SemanticSound.NONE,
                true, true);
        return widget;
    }

    public ButtonWidget destructive(boolean d) {
        painter.destructive(d);
        return this;
    }

    /** Glass pilot — forwarded to the painter (see {@link Button#glassBackground}). */
    public ButtonWidget glassBackground(boolean g) {
        painter.glassBackground(g);
        return this;
    }

    /** Glass pilot — forwarded to the painter (see {@link Button.GlassStyle}). */
    public ButtonWidget glassStyle(Button.GlassStyle s) {
        painter.glassStyle(s);
        return this;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (semanticAction != null) {
            // Vanilla already gated on `active` and played its own click sound
            // on both the pointer and selection-key paths (AbstractWidget /
            // AbstractButton); the action adds NONE of its own.
            semanticAction.activate(null, null);
            return;
        }
        if (onPress != null) onPress.run();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean dbl) {
        // Forward for hover/press animation state only (no-op action inside).
        painter.mouseClicked(ev.x(), ev.y(), ev.button());
        return super.mouseClicked(ev, dbl);
    }

    /**
     * Glass pass — paint this button's surface BEFORE the screen's overlay
     * dim (see {@link Widget#renderGlassPass}). Vanilla renders the widget
     * itself (via {@code Screen.render}) after the dim, so a screen on the
     * pre-dim discipline calls this from its glass pass; the later
     * {@link #renderContents} then paints content only.
     */
    public void renderGlassPass(GuiGraphics g) {
        if (!this.visible) return;
        painter.disabled(!this.active);
        painter.layout(getX(), getY(), getWidth(), getHeight());
        painter.renderGlassPass(g, getX(), getY(), getWidth(), getHeight());
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        painter.disabled(!this.active);
        // Provisional Phase A focus treatment — the same 1 px accent hairline
        // the semantic controls draw, synced from vanilla's own focus so
        // keyboard position is visible on vanilla-backed chrome too.
        painter.focused(isFocused() && this.active);
        painter.layout(getX(), getY(), getWidth(), getHeight());
        painter.render(g, getX(), getY(), getWidth(), getHeight(), mouseX, mouseY);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        this.defaultButtonNarrationText(builder);
        if (semanticAction != null) {
            Component description = semanticAction.description();
            if (description != null && !description.getString().isBlank()) {
                builder.add(NarratedElementType.HINT, description);
            }
            Component state = semanticAction.state();
            if (state != null && !state.getString().isBlank()) {
                builder.add(NarratedElementType.HINT, state);
            }
        }
    }
}