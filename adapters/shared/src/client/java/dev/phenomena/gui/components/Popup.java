package dev.phenomena.gui.components;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.component.OverlayPose;
import com.aurora.client.ui.util.OverlayLayout;
import com.aurora.client.ui.util.OverlayMotion;
import com.aurora.client.util.AuroraAnim;
import com.mojang.blaze3d.platform.InputConstants;
import dev.phenomena.gui.pixel.Pixel;
import dev.phenomena.gui.theme.Theme;
import dev.phenomena.gui.ui.Layout.Rect;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;


/**
 * An overlay that receives all input while open: dropdown option lists and modal confirmation
 * dialogs. Rendered on its own stratum above the page. Escape always dismisses without acting.
 */
public abstract class Popup {
  protected Rect rect;

  public abstract void render(GuiGraphicsExtractor g, int mx, int my, int screenW, int screenH);

  /** Returns false when the popup should close. */
  public abstract boolean click(double x, double y);

  /** Returns false when the popup should close. */
  public abstract boolean key(int key);

  public void scroll(double amount) {}

  public String narration() {
    return "";
  }

  // ---- dropdown ------------------------------------------------------------

  public static final class Dropdown extends Popup {
    public static final int ROW = 13;
    private final List<String> options;
    private final IntConsumer pick;
    private final int current;
    private int highlighted, offset;
    private final int visible;
    private final int pivot;
    private final OverlayMotion motion = new OverlayMotion();
    private float progress;
    private boolean closing;

    public Dropdown(Rect anchor, List<String> options, int current, IntConsumer pick, Rect frame) {
      this.options = options;
      this.pick = pick;
      this.current = current;
      var font = Minecraft.getInstance().font;
      int w = anchor.w();
      for (var o : options) w = Math.max(w, font.width(o) + 16);
      w = Math.min(w, frame.w() - 16);
      int below = (frame.bottom() - anchor.bottom() - 11) / ROW, above = (anchor.y() - frame.y() - 11) / ROW;
      visible = Math.min(options.size(), Math.max(1, Math.max(below, above)));
      int h = visible * ROW + 2;
      int x = Math.max(frame.x() + 8, Math.min(anchor.x(), frame.right() - w - 8));
      int y = OverlayLayout.popupTop(anchor.bottom() + 1, anchor.y() - 1, h, frame.y() + 8, frame.bottom() - 8);
      rect = new Rect(x, y, w, h);
      pivot = y < anchor.y() ? y + h : y;
      highlighted = Math.max(0, current);
      offset = Math.max(0, Math.min(options.size() - visible, highlighted - visible / 2));
      motion.setOpen(true, System.nanoTime());
    }

    public void dismiss() {
      closing = true;
      motion.setOpen(false, System.nanoTime());
    }

    private float progress() { return motion.progress(System.nanoTime(), Theme.reducedMotion() || AuroraConfig.get().reducedMotion); }
    public boolean finished() { return closing && progress() == 0; }

    @Override
    public void render(GuiGraphicsExtractor g, int mx, int my, int sw, int sh) {
      progress = progress();
      if (progress < .015f) return;
      boolean posed = OverlayPose.push(g, rect.x(), pivot, progress);
      mx = (int) Math.floor(OverlayMotion.unscale(mx, rect.x(), progress));
      my = (int) Math.floor(OverlayMotion.unscale(my, pivot, progress));
      var font = Minecraft.getInstance().font;
      float radius = MaterialSurface.windowBevelEnabled() ? Theme.radiusSmall() : Theme.radius();
      MaterialSurface.overlayFace(g, rect.x(), rect.y(), rect.w(), rect.h(), radius,
          Math.min(1, MaterialSurface.windowCornerClearance(radius, 2, 2)), progress);
      for (int i = 0; i < visible; i++) {
        int idx = offset + i;
        int ry = rect.y() + 1 + i * ROW;
        boolean hover = !closing && mx >= rect.x() && mx < rect.right() && my >= ry && my < ry + ROW;
        if (hover) highlighted = idx;
        if (!posed && (hover || idx == highlighted) && idx != current)
          Pixel.rounded(
              g, rect.x() + 2, ry + 1, rect.w() - 4, ROW - 2, Theme.radiusSmall(), Theme.SURFACE_HOVER());
        if (!posed && idx == current) {
          // The committed value: subtle accent wash plus one accent edge, like a selected tab.
          Pixel.rounded(
              g,
              rect.x() + 2,
              ry + 1,
              rect.w() - 4,
              ROW - 2,
              Theme.radiusSmall(),
              Theme.alpha(Theme.accent(), 0x28));
          Pixel.rounded(g, rect.x() + 3, ry + 3, 1.5f, ROW - 6, .75f, Theme.accent());
        }
        g.text(
            font,
            KButton.fit(font, options.get(idx), rect.w() - 12),
            rect.x() + 8,
            ry + 3,
            AuroraAnim.scaleAlpha(idx == highlighted || idx == current ? Theme.TEXT() : Theme.MUTED(), progress),
            false);
      }
      if (options.size() > visible) {
        int th = Math.max(6, rect.h() * visible / options.size());
        int ty = rect.y() + (rect.h() - th) * offset / Math.max(1, options.size() - visible);
        Pixel.rounded(g, rect.right() - 3, ty, 2, th, 1, AuroraAnim.scaleAlpha(Theme.BORDER(), progress));
      }
      if (posed) g.pose().popMatrix();
    }

    @Override
    public boolean click(double x, double y) {
      x = OverlayMotion.unscale(x, rect.x(), progress);
      y = OverlayMotion.unscale(y, pivot, progress);
      if (!rect.contains(x, y)) return false;
      // Only the rows on screen: the list's last pixel row would otherwise pick the hidden next option.
      int row = (int) ((y - rect.y() - 1) / ROW);
      int i = offset + row;
      if (row >= 0 && row < visible && i < options.size()) {
        pick.accept(i);
        return false;
      }
      return true;
    }

    @Override
    public boolean key(int key) {
      switch (key) {
        case InputConstants.KEY_DOWN -> highlighted = Math.min(options.size() - 1, highlighted + 1);
        case InputConstants.KEY_UP -> highlighted = Math.max(0, highlighted - 1);
        case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_SPACE -> {
          pick.accept(highlighted);
          return false;
        }
        case InputConstants.KEY_ESCAPE, InputConstants.KEY_TAB -> {
          return false;
        }
        default -> {}
      }
      if (highlighted < offset) offset = highlighted;
      if (highlighted >= offset + visible) offset = highlighted - visible + 1;
      return true;
    }

    @Override
    public void scroll(double amount) {
      offset = Math.max(0, Math.min(options.size() - visible, offset - (int) Math.signum(amount)));
    }

    @Override
    public String narration() {
      return options.get(highlighted);
    }
  }

  // ---- confirmation --------------------------------------------------------

  public static final class Confirm extends Popup {
    private final String title, message, action;
    private final Runnable confirm;
    private int focus; // 0 cancel (default), 1 confirm
    private Rect cancelRect, confirmRect;

    public Confirm(String title, String message, String action, Runnable confirm) {
      this.title = title;
      this.message = message;
      this.action = action;
      this.confirm = confirm;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mx, int my, int sw, int sh) {
      var font = Minecraft.getInstance().font;
      Pixel.fillLogical(g, 0, 0, sw, sh, 0xC0000000);
      int w = Math.min(280, sw - 24);
      var lines = TextLayout.wrap(font, message, w - 20);
      int h = 26 + lines.size() * 10 + 30;
      rect = new Rect((sw - w) / 2, (sh - h) / 2, w, h);
      Pixel.rounded(g, rect.x() + 2, rect.y() + 3, w, h, Theme.radius(), 0x66000000);
      Pixel.rounded(g, rect.x(), rect.y(), w, h, Theme.radius(), Theme.SURFACE());
      Pixel.outline(g, rect.x(), rect.y(), w, h, Theme.radius(), 1, Theme.BORDER());
      g.text(font, title, rect.x() + 10, rect.y() + 9, Theme.TEXT(), false);
      for (int i = 0; i < lines.size(); i++)
        g.text(font, lines.get(i), rect.x() + 10, rect.y() + 24 + i * 10, Theme.MUTED(), false);
      int bw = Math.min(90, (w - 30) / 2), by = rect.bottom() - 24;
      cancelRect = new Rect(rect.right() - 10 - 2 * bw - 6, by, bw, 16);
      confirmRect = new Rect(rect.right() - 10 - bw, by, bw, 16);
      button(g, cancelRect, "Cancel", focus == 0, false, mx, my);
      button(g, confirmRect, action, focus == 1, true, mx, my);
    }

    private void button(GuiGraphicsExtractor g, Rect r, String text, boolean focused, boolean danger, int mx, int my) {
      var font = Minecraft.getInstance().font;
      boolean hover = r.contains(mx, my);
      boolean pressed = hover && Minecraft.getInstance().mouseHandler.isLeftPressed();
      // Same semantics as KButton: quiet neutral faces; destructive stays labeled-red and only
      // commits to solid red on press.
      Pixel.rounded(
          g,
          r.x(),
          r.y(),
          r.w(),
          r.h(),
          Theme.radiusSmall(),
          pressed ? Theme.SURFACE_PRESSED() : Theme.mix(Theme.SURFACE_2(), Theme.SURFACE_HOVER(), hover ? 1 : 0));
      Pixel.outline(g, r.x(), r.y(), r.w(), r.h(), Theme.radiusSmall(), 1, Theme.BORDER_SOFT());
      if (danger && pressed) Pixel.rounded(g, r.x(), r.y(), r.w(), r.h(), Theme.radiusSmall(), Theme.ERROR());
      String t = KButton.fit(font, text, r.w() - 6);
      g.text(
          font,
          t,
          r.x() + (r.w() - font.width(t)) / 2,
          r.y() + (r.h() - 8) / 2 + (pressed ? 1 : 0),
          danger ? (pressed ? 0xFF1A100E : Theme.ERROR()) : Theme.TEXT(),
          false);
      if (focused) KButton.focusRing(g, r.x(), r.y(), r.w(), r.h());
    }

    @Override
    public boolean click(double x, double y) {
      if (confirmRect != null && confirmRect.contains(x, y)) {
        confirm.run();
        return false;
      }
      if (cancelRect != null && cancelRect.contains(x, y)) return false;
      return true; // modal: clicks elsewhere are ignored
    }

    @Override
    public boolean key(int key) {
      switch (key) {
        case InputConstants.KEY_ESCAPE -> {
          return false;
        }
        case InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT, InputConstants.KEY_TAB -> focus = 1 - focus;
        case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_SPACE -> {
          if (focus == 1) confirm.run();
          return false;
        }
        default -> {}
      }
      return true;
    }

    @Override
    public String narration() {
      return title + ". " + message + (focus == 1 ? ". " + action : ". Cancel");
    }
  }
}
