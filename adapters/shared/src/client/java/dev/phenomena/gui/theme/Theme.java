package dev.phenomena.gui.theme;

import com.aurora.client.util.AuroraTheme;
import dev.phenomena.PhenomenaClient;

/**
 * Spxctra design tokens: one restrained near-neutral surface ladder, a single working accent,
 * semantic status colors and the validated CVD-safe chart series. Named by role, not value.
 * Geometry (radius, focus, motion) lives here too so every component shares one vocabulary.
 */
public final class Theme {
  public static int BACKGROUND() { return com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.MATERIAL_CANVAS); }
  public static int SURFACE() { return com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.MATERIAL_PANEL); }
  public static int SURFACE_2() { return com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.MATERIAL_RAISED); }
  public static int SURFACE_HOVER() { return com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.MATERIAL_HOVER); }
  public static int SURFACE_PRESSED() { return com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.MATERIAL_PRESSED); }
  public static int INSET() { return com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.MATERIAL_SUNKEN); }
  public static int HEADER() { return com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.MATERIAL_PANEL); }
  public static int BORDER() { return com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.MATERIAL_BORDER_STRONG); }
  public static int BORDER_SOFT() { return com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.MATERIAL_BORDER); }
  public static int BEVEL_LIGHT() { return com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.WINDOW_HIGHLIGHT); }
  public static int BEVEL_DARK() { return com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.MATERIAL_BORDER); }
  public static int TEXT() { return com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.MATERIAL_TEXT); }
  public static int MUTED() { return com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.MATERIAL_TEXT_SECONDARY); }
  public static int DIM() { return com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.MATERIAL_TEXT_MUTED); }
  public static int GRID() { return com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.MATERIAL_BORDER); }
  public static int WARN() { return com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.STATUS_WARNING); }
  public static int ERROR() { return com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.STATUS_ERROR); }
  public static int OK() { return com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.STATUS_SUCCESS); }

  /** Validated dark-surface categorical slots (CVD-safe adjacent order). */
  public static final int SERIES_1 = 0xFF3987E5, SERIES_2 = 0xFFD95926, SERIES_3 = 0xFF199E70;

  /** Sequential ramp end for heat cells; low values blend toward the surface. */
  public static final int SEQ_HIGH = 0xFF86B6EF;

  // ---- geometry vocabulary (logical px) ----
  public static float radius() { return AuroraTheme.RADIUS; }
  public static float radiusSmall() { return AuroraTheme.RADIUS_SMALL; }
  /** Focus ring sits one gap outside the control edge so it stays visible on selected peers. */
  public static final float FOCUS_GAP = 1f;

  private Theme() {}

  public static int accent() {
    return com.aurora.client.theme.ThemeManager.color(com.aurora.client.theme.ThemeToken.ACCENT);
  }

  /** Accent lifted toward white for hover feedback on accent-filled controls. */
  public static int accentHover() {
    return mix(accent(), 0xFFFFFFFF, 0.15);
  }

  /** Accent deepened for pressed feedback on accent-filled controls. */
  public static int accentPressed() {
    return mix(accent(), 0xFF000000, 0.25);
  }

  public static boolean compact() {
    return true;
  }

  public static boolean reducedMotion() {
    var client = net.minecraft.client.Minecraft.getInstance();
    return (PhenomenaClient.config != null && PhenomenaClient.config.reducedMotion)
        || (client != null && client.options != null && client.options.screenEffectScale().get() <= 0.0);
  }

  /** Linear blend between two ARGB colors. */
  public static int mix(int a, int b, double t) {
    t = Math.max(0, Math.min(1, t));
    int r = 0;
    for (int shift = 0; shift <= 24; shift += 8) {
      int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
      r |= ((int) Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
    }
    return r;
  }

  public static int alpha(int color, int alpha) {
    return (color & 0xFFFFFF) | (alpha << 24);
  }

  /**
   * Eased hover progress for interaction transitions: a short, quiet fade of the widget's own
   * start timestamp ({@code 0} = never started, negative = already complete). Reduced motion
   * snaps to the end state — behavior stays correct with animation disabled.
   */
  public static float ease(long startedNanos) {
    if (reducedMotion()) return 1f;
    if (startedNanos == 0L) return 0f;
    if (startedNanos < 0L) return 1f;
    float t = Math.min(1f, (System.nanoTime() - startedNanos) / 120_000_000f);
    return t * t * (3 - 2 * t);
  }
}
