package dev.phenomena.gui.pixel;

import com.aurora.client.ui.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Dashboard primitives delegate to Aurora's native shape pipeline. */
public final class Pixel {
  private Pixel() {}
  public static float scale() { return Minecraft.getInstance().getWindow().getGuiScale(); }
  public static long fillsSubmitted() { return RenderUtil.fillsSubmitted(); }
  public static void fillLogical(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, int color) {
    RenderUtil.fillLogical(g, Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2), color);
  }
  public static void rounded(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
    RenderUtil.drawRoundedRectAA(g, x, y, w, h, radius, color);
  }
  public static void outline(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, float stroke, int color) {
    RenderUtil.drawRoundedOutlineAA(g, x, y, w, h, radius, stroke, color);
  }
  public static void circle(GuiGraphicsExtractor g, float x, float y, float radius, int color) {
    RenderUtil.drawCircleAA(g, x - radius, y - radius, radius * 2, radius * 2, color);
  }
  public static void line(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, float stroke, int color) {
    if (x1 == x2 || y1 == y2) {
      rounded(g, Math.min(x1, x2) - (x1 == x2 ? stroke / 2 : 0),
          Math.min(y1, y2) - (y1 == y2 ? stroke / 2 : 0),
          Math.max(stroke, Math.abs(x2 - x1)), Math.max(stroke, Math.abs(y2 - y1)), stroke / 2, color);
      return;
    }
    float dx = x2 - x1, dy = y2 - y1;
    g.pose().pushMatrix();
    try {
      g.pose().translate(x1, y1);
      g.pose().rotate((float) Math.atan2(dy, dx));
      rounded(g, 0, -stroke / 2, (float) Math.hypot(dx, dy), stroke, stroke / 2, color);
    } finally { g.pose().popMatrix(); }
  }
  public static void gradientV(GuiGraphicsExtractor g, float x, float y, float w, float h, int a, int b) {
    RenderUtil.drawRoundedRectGradientAA(g, x, y, w, h, 0, a, b, false);
  }
  public static void gradientH(GuiGraphicsExtractor g, float x, float y, float w, float h, int a, int b) {
    RenderUtil.drawRoundedRectGradientAA(g, x, y, w, h, 0, a, b, true);
  }
  public static int mix(int a, int b, double t) { return dev.phenomena.gui.theme.Theme.mix(a, b, t); }
}
