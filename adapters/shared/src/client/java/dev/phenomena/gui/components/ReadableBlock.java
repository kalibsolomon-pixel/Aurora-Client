package dev.phenomena.gui.components;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Native keyboard/narrator access to the measurements and explanations drawn on screen. */
public final class ReadableBlock extends AbstractWidget implements Blocks.Block {
  private final Blocks.Block block;
  private final List<String> descriptions;
  private final BiConsumer<Integer, Integer> reveal;
  private int cursor;

  public ReadableBlock(Blocks.Block block, List<String> descriptions, BiConsumer<Integer, Integer> reveal) {
    super(0, 0, 100, 16, Component.literal(descriptions.getFirst()));
    this.block = block;
    this.descriptions = List.copyOf(descriptions);
    this.reveal = reveal;
  }

  public String description() {
    var current = narration();
    return current.get(Math.min(cursor, current.size() - 1));
  }
  @Override public List<String> narration() { return block.liveNarration() ? block.narration() : descriptions; }

  @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    return Viewport.contains(event.x(), event.y()) && super.mouseClicked(event, doubleClick);
  }

  @Override public boolean isMouseOver(double x, double y) {
    return Viewport.contains(x, y) && super.isMouseOver(x, y);
  }

  @Override public boolean keyPressed(KeyEvent event) {
    int step = switch (event.key()) {
      case InputConstants.KEY_LEFT -> -1;
      case InputConstants.KEY_RIGHT -> 1;
      case InputConstants.KEY_UP -> block instanceof Blocks.StatGrid grid ? -grid.columns() : -1;
      case InputConstants.KEY_DOWN -> block instanceof Blocks.StatGrid grid ? grid.columns() : 1;
      default -> 0;
    };
    if (step == 0 || !isFocused()) return false;
    cursor = Math.max(0, Math.min(narration().size() - 1, cursor + step));
    setMessage(Component.literal(description()));
    revealFocus();
    return true;
  }

  public void revealFocus() {
    int top = getY(), bottom = top + height;
    if (block instanceof Blocks.StatGrid grid) {
      top += cursor / grid.columns() * (grid.cardHeight() + Blocks.StatGrid.GAP);
      bottom = top + grid.cardHeight();
    } else if (block instanceof Blocks.Table) {
      top += Blocks.Table.ROW + 2 + cursor * Blocks.Table.ROW;
      bottom = top + Blocks.Table.ROW;
    } else if (block instanceof Blocks.Lines) {
      top += cursor * Blocks.Lines.ROW;
      bottom = top + Blocks.Lines.ROW;
    }
    reveal.accept(top, bottom);
  }

  @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
    render(g, getX(), getY(), width, height, mx, my);
  }

  @Override public void render(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
    block.render(g, x, y, w, h, mx, my);
    if (!KButton.keyboardFocus(this)) return;
    int fx = x, fy = y, fw = w, fh = h;
    if (block instanceof Blocks.StatGrid grid) {
      fw = (w - Blocks.StatGrid.GAP * (grid.columns() - 1)) / grid.columns();
      fh = grid.cardHeight();
      fx += cursor % grid.columns() * (fw + Blocks.StatGrid.GAP);
      fy += cursor / grid.columns() * (fh + Blocks.StatGrid.GAP);
    } else if (block instanceof Blocks.Table) {
      fy += Blocks.Table.ROW + 2 + cursor * Blocks.Table.ROW;
      fh = Blocks.Table.ROW;
    } else if (block instanceof Blocks.Lines) {
      fy += cursor * Blocks.Lines.ROW;
      fh = Blocks.Lines.ROW;
    }
    KButton.focusRing(g, fx + 2, fy + 2, fw - 4, fh - 4);
  }

  @Override protected void updateWidgetNarration(NarrationElementOutput out) {
    out.add(NarratedElementType.TITLE, Component.literal(description()));
    int count = narration().size();
    if (count > 1)
      out.add(NarratedElementType.USAGE, Component.literal("Use arrow keys to read measurements. "
          + (cursor + 1) + " of " + count + ". Tab moves to the next group."));
  }

  @Override public void close() { block.close(); }
}
