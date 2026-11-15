package dev.phenomena.gui.components;

import dev.phenomena.gui.pixel.Pixel;
import dev.phenomena.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Vanilla text field restricted to the viewport and drawn with the dashboard's flat style. The
 * widget rectangle is the text line; the drawn frame (and the click area) extends {@link #PAD}
 * pixels around it.
 */
public final class KTextField extends EditBox {
  public static final int PAD = 0;
  public final String id;
  private java.util.function.Predicate<String> inputFilter;

  public void setFilter(java.util.function.Predicate<String> filter) { inputFilter = filter; }

  @Override public void insertText(String input) {
    if (inputFilter == null || inputFilter.test(input)) super.insertText(input);
  }

  @Override public void setValue(String value) {
    if (inputFilter == null || inputFilter.test(value)) super.setValue(value);
  }

  public KTextField(String id, String value, int maxLength) {
    super(Minecraft.getInstance().font, 0, 0, 100, 14, Component.literal(id));
    this.id = id;
    setMaxLength(maxLength);
    setValue(value);
    setBordered(true);
    setTextColor(Theme.TEXT());
    setTextShadow(false);
  }

  private boolean inFrame(double x, double y) {
    return x >= getX() - PAD
        && x < getX() + width + PAD
        && y >= getY() - PAD
        && y < getY() + height + PAD;
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
    if (!Viewport.contains(e.x(), e.y()) || !isActive() || !inFrame(e.x(), e.y())) return false;
    double cx = Math.max(getX(), Math.min(getX() + width - 1, e.x()));
    double cy = Math.max(getY(), Math.min(getY() + height - 1, e.y()));
    return super.mouseClicked(new MouseButtonEvent(cx, cy, e.buttonInfo()), doubleClick);
  }

  @Override
  public boolean isMouseOver(double x, double y) {
    return Viewport.contains(x, y) && isActive() && inFrame(x, y);
  }

  @Override
  public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float d) {
    super.extractWidgetRenderState(g, mx, my, d);
  }
}
