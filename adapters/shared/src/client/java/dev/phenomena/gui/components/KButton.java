package dev.phenomena.gui.components;

import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.util.AuroraFontRenderer;
import dev.phenomena.gui.pixel.Pixel;
import dev.phenomena.gui.theme.Theme;
import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Upstream page API with Aurora's canonical button painter and vanilla accessibility. */
public class KButton extends ButtonWidget {
  public enum Style { NORMAL, PRIMARY, SUBTLE, TAB, CHIP, DANGER }
  public final String id;
  private final Style style;
  private Supplier<String> label;
  private BooleanSupplier selected = () -> false;
  private boolean content;
  private String shown = "";

  public KButton(String id, Style style, Supplier<String> label, Runnable action) {
    super(0, 0, 60, 16, Component.literal(label.get()), action, style == Style.PRIMARY);
    this.id = id; this.style = style; this.label = label;
    destructive(style == Style.DANGER);
  }
  public KButton(String label, Runnable action) { this(label, Style.NORMAL, () -> label, action); }
  public KButton(String label, Style style, Runnable action) { this(label, style, () -> label, action); }
  public KButton selected(BooleanSupplier supplier) { selected = supplier; return this; }
  public KButton tooltip(String text) { setTooltip(Tooltip.create(Component.literal(text))); return this; }
  public KButton enabled(boolean enabled) { active = enabled; return this; }
  public KButton content() { content = true; return this; }
  public boolean isContent() { return content; }
  public void setLabel(Supplier<String> supplier) { label = supplier; }
  public KButton size(int w, int h) { setWidth(w); setHeight(h); return this; }
  public boolean isSelected() { return selected.getAsBoolean(); }
  @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    return (!content || Viewport.contains(event.x(), event.y())) && super.mouseClicked(event, doubleClick);
  }
  @Override public boolean isMouseOver(double x, double y) {
    return (!content || Viewport.contains(x, y)) && super.isMouseOver(x, y);
  }
  @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
    String text = label.get();
    if (!text.equals(shown)) { shown = text; setMessage(Component.literal(text)); }
    if (style != Style.TAB && style != Style.CHIP && style != Style.SUBTLE) {
      super.extractContents(g, mx, my, delta); return;
    }
    boolean selected = isSelected();
    boolean focus = keyboardFocus(this);
    if (style == Style.CHIP && selected || isHovered())
      Pixel.rounded(g, getX(), getY(), width, height, Theme.radiusSmall(), Theme.SURFACE_2());
    if (focus) focusRing(g, getX(), getY(), width, height);
    var font = Minecraft.getInstance().font;
    Component fitted = Component.literal(fit(font, text, width - 8));
    if (selected) fitted = AuroraFontRenderer.role(fitted.getString(), AuroraFontRenderer.Role.HEADING);
    g.text(font, fitted, getX() + (width - font.width(fitted)) / 2, getY() + (height - 8) / 2,
        active ? selected ? Theme.TEXT() : Theme.MUTED() : Theme.DIM(), false);
  }
  public static boolean keyboardFocus(net.minecraft.client.gui.components.AbstractWidget widget) {
    return widget.isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard();
  }
  public static void focusRing(GuiGraphicsExtractor g, float x, float y, float w, float h) {
    Pixel.outline(g, x - 2, y - 2, w + 4, h + 4, Theme.radiusSmall() + 2, 1, Theme.accent());
  }
  public static int onAccent(int ignored) {
    return com.aurora.client.theme.ThemeManager.color(com.aurora.client.theme.ThemeToken.ON_ACTION);
  }
  /** Ellipsized to {@code width}; the shared ellipsize memo (text, width, styleKey) does the caching. */
  public static String fit(net.minecraft.client.gui.Font font, String text, int width) {
    return AuroraFontRenderer.ellipsize(font, text, width, 0);
  }
}
