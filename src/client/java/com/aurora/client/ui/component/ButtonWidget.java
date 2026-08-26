package com.aurora.client.ui.component;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
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
 */
public class ButtonWidget extends AbstractButton {

    private final Runnable onPress;

    /** The single paint implementation (no-op action — see class javadoc). */
    private final Button painter;

    public ButtonWidget(int x, int y, int w, int h, Component label, Runnable onPress) {
        this(x, y, w, h, label, onPress, false);
    }

    public ButtonWidget(int x, int y, int w, int h, Component label, Runnable onPress, boolean primary) {
        super(x, y, w, h, label);
        this.onPress = onPress;
        this.painter = new Button(label, () -> {}, primary);
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
        if (onPress != null) onPress.run();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean dbl) {
        // Forward for hover/press animation state only (no-op action inside).
        painter.mouseClicked(ev.x(), ev.y(), ev.button());
        return super.mouseClicked(ev, dbl);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        painter.disabled(!this.active);
        painter.layout(getX(), getY(), getWidth(), getHeight());
        painter.render(g, getX(), getY(), getWidth(), getHeight(), mouseX, mouseY);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        this.defaultButtonNarrationText(builder);
    }
}