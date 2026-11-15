package dev.phenomena.gui.components;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.ToggleSwitch;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Native keyboard/narration routing around Aurora's shared opaque switch. */
public final class KToggle extends ButtonWidget {
  public static final int W = 24, H = 12;
  public final String id;
  /** What the narrator says before the state; the visible setting label once placed in a row. */
  private String spoken;
  private final BooleanSupplier getter;
  private final ToggleSwitch painter;
  private Boolean lastState;
  public KToggle(String id, BooleanSupplier getter, Runnable toggle) {
    super(0, 0, W, H, Component.literal(id), toggle);
    this.id = id; this.spoken = id; this.getter = getter;
    painter = new ToggleSwitch(getter, value -> toggle.run());
  }
  public KToggle content() { return this; }
  /** Narrate under the visible label of the row that shows this switch. */
  public KToggle narrate(String label) { spoken = label; lastState = null; setMessage(Component.literal(label)); return this; }
  @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    return Viewport.contains(event.x(), event.y()) && super.mouseClicked(event, doubleClick);
  }
  @Override public boolean isMouseOver(double x, double y) { return Viewport.contains(x,y) && super.isMouseOver(x,y); }
  @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
    boolean state = getter.getAsBoolean();
    if (lastState == null || state != lastState) {
      lastState = state; setMessage(Component.literal(spoken + (state ? ": On" : ": Off")));
    }
    painter.disabled(!active).focusedVisual(KButton.keyboardFocus(this));
    painter.render(g, getX(), getY(), width, height, mx, my);
  }
}
