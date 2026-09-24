package com.aurora.client.ui.component;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.AuroraShapes;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Material treatments for surfaces that do not need another live-backdrop
 * glass sample.
 *
 * <p>A child already enclosed by window/panel glass uses
 * {@link #embeddedControl}: its parent owns blur and the outer rim, while
 * the child contributes only a restrained local tint. Selected/primary
 * children may still use the established stained tint because that tint
 * communicates state; neutral children never add another opacity-driven
 * {@code WINDOW_FILL} layer. This is Phase E's central nested-material rule.
 *
 * <p>A transient surface drawn above ordinary content uses
 * {@link #floating}: a stable semantic {@code SURFACE} backing, one
 * low-emphasis edge and the existing shadow. Neither path invokes the blur
 * renderer, allocates per frame, or creates another rim owner.
 */
public final class MaterialSurface {

    private MaterialSurface() {}

    /** Local neutral tint over a parent material; not a second opacity control. */
    static final int EMBEDDED_ALPHA = 0x24;

    public static void embeddedControl(GuiGraphics g, float x, float y, float w, float h,
                                       float radius, boolean stained) {
        int fill = stained
                ? ThemeManager.stainedTint()
                : ThemeManager.withAlpha(ThemeManager.color(ThemeToken.SURFACE_VARIANT), EMBEDDED_ALPHA);
        RenderUtil.drawRoundedRectAA(g, x, y, w, h, radius, fill);
    }

    public static void floating(GuiGraphics g, int x, int y, int w, int h,
                                int radius, float fade) {
        float t = Math.max(0f, Math.min(1f, fade));
        AuroraShapes.dropShadow(g, x, y, w, h, radius, t);
        AuroraShapes.panel(g, x, y, w, h,
                AuroraAnim.scaleAlpha(ThemeManager.color(ThemeToken.SURFACE), t), radius);
        AuroraShapes.outline(g, x, y, w, h,
                AuroraAnim.scaleAlpha(ThemeManager.color(ThemeToken.BORDER), 0.55f * t), radius);
    }
}
