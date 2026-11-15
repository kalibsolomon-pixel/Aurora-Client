package dev.phenomena.gui.components;

import dev.phenomena.gui.pixel.Pixel;
import dev.phenomena.gui.theme.Theme;
import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;

/** Vanilla slider (arrow keys, drag, narration) with stepping, formatting and viewport clipping. */
public final class KSlider extends AbstractSliderButton {
  public final String id;
  /** What the narrator says before the value; the visible setting label once placed in a row. */
  private String spoken;
  private final double min, max, step;
  private final DoubleFunction<String> format;
  private final DoubleConsumer setter;

  public KSlider(
      String id,
      double current,
      double min,
      double max,
      double step,
      DoubleFunction<String> format,
      DoubleConsumer setter) {
    super(0, 0, 100, 16, Component.empty(), max > min ? (clamp(current, min, max) - min) / (max - min) : 0);
    this.id = id;
    this.spoken = id;
    this.min = min;
    this.max = max;
    this.step = step;
    this.format = format;
    this.setter = setter;
    updateMessage();
  }

  private static double clamp(double v, double lo, double hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  public double current() {
    double v = min + value * (max - min);
    if (step > 0) v = Math.round((v - min) / step) * step + min;
    return clamp(v, min, max);
  }

  /** Narrate under the visible label of the row that shows this slider. */
  public KSlider narrate(String label) {
    spoken = label;
    updateMessage();
    return this;
  }

  @Override
  protected void updateMessage() {
    setMessage(Component.literal(spoken + ": " + format.apply(current())));
  }

  @Override
  protected void applyValue() {
    setter.accept(current());
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
    if (!Viewport.contains(e.x(), e.y()) || e.x() > getX() + trackWidth() + 4) return false;
    return super.mouseClicked(e, doubleClick);
  }

  private int trackWidth() { return Math.max(20, Math.min(84, width - 71)); }

  private void fromMouse(MouseButtonEvent event) {
    setValue((event.x() - getX()) / trackWidth());
  }

  @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { fromMouse(event); }
  @Override protected void onDrag(MouseButtonEvent event, double dx, double dy) { fromMouse(event); }

  @Override public boolean keyPressed(KeyEvent event) {
    if (!active || !isFocused()) return false;
    int direction = switch (event.key()) {
      case InputConstants.KEY_LEFT, InputConstants.KEY_DOWN -> -1;
      case InputConstants.KEY_RIGHT, InputConstants.KEY_UP -> 1;
      default -> 0;
    };
    if (direction == 0) return super.keyPressed(event);
    setValue((current() + direction * step - min) / (max - min));
    return true;
  }

  @Override
  public boolean isMouseOver(double x, double y) {
    return Viewport.contains(x, y) && super.isMouseOver(x, y);
  }

  @Override
  public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float d) {
    if (painter == null) painter = new com.aurora.client.ui.component.Slider(() -> value, v -> {}, 0, 1, 0).canonicalStates();
    painter.disabled(!active).focusedVisual(KButton.keyboardFocus(this));
    int valueWidth = 65;
    painter.render(g, getX(), getY(), trackWidth(), height, mx, my);
    var font = Minecraft.getInstance().font;
    String text = KButton.fit(font, format.apply(current()), valueWidth);
    g.text(font, text, getRight() - font.width(text), getY() + (height - 8) / 2, Theme.TEXT(), false);
  }

  private com.aurora.client.ui.component.Slider painter;

  private long hoverSince;
}
