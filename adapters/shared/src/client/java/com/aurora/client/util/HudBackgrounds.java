package com.aurora.client.util;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** HUD backdrops resolved through Minecraft's resource-pack GUI atlas. */
public final class HudBackgrounds {
    private HudBackgrounds() {}
    public static final int SLOT_SIZE = 22;
    /** Native slot interior is 16px; reserve another pixel around each icon edge. */
    public static int iconSlotSize(int iconSize) {
        int size = (int) Math.ceil((iconSize + 2) * SLOT_SIZE / 16.0);
        return (size + 1) & ~1; // even dimensions keep the icon centered on whole pixels
    }
    /**
     * Fixed panel corner: floating HUD panels keep their compact boxy shape
     * unless the module's theme-corners switch is on (see {@link #radius}).
     */
    public static final int FIXED_RADIUS = 2;
    private static final Identifier OFFHAND = Identifier.withDefaultNamespace("hud/hotbar_offhand_left");

    /**
     * Corner radius for a HUD shape: {@code fixed} by default, or the
     * theme's compact-control radius (Corner Radius × 3/5) when the module's
     * theme-corners switch is on (owner ruling 2026-11-12: HUD shapes keep
     * fixed corners unless a player opts in per module).
     */
    public static float radius(boolean themeCorners, float fixed) {
        return themeCorners ? com.aurora.client.theme.ThemeManager.current().roundness().radiusSmall() : fixed;
    }

    public static void draw(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                            AuroraConfig.HudBackground mode, int color) {
        draw(ctx, x, y, w, h, mode, color, 1, false);
    }

    public static void draw(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                            AuroraConfig.HudBackground mode, int color, float alpha, boolean themeCorners) {
        if (mode == null || alpha <= 0.001f) return;
        switch (mode) {
            case SOLID -> {
                if (themeCorners) {
                    // One shader-drawn shape at any radius, clamped to a capsule.
                    float r = Math.min(radius(true, FIXED_RADIUS), Math.min(w, h) / 2f + 3f);
                    com.aurora.client.ui.util.RenderUtil.drawRoundedRectAA(ctx, x - 3, y - 3, w + 6, h + 6, r,
                            scaleAlpha(color, alpha));
                } else {
                    RoundedRect.fill(ctx, x - 3, y - 3, x + w + 3, y + h + 3, FIXED_RADIUS, scaleAlpha(color, alpha));
                }
            }
            case VANILLA -> slot(ctx, x - 3, y - 3, w + 6, h + 6, alpha);
            default -> {}
        }
    }

    /** Crop the 22x22 offhand slot, excluding its unused hotbar attachment margin. */
    public static void slot(GuiGraphicsExtractor ctx, int x, int y, int width, int height, float alpha) {
        if (width <= 0 || height <= 0 || alpha <= 0.001f) return;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(width / 22.0f, height / 22.0f);
        ctx.blitSprite(RenderPipelines.GUI_TEXTURED, OFFHAND, 29, 24, 0, 1,
                0, 0, 22, 22, scaleAlpha(0xFFFFFFFF, alpha));
        ctx.pose().popMatrix();
    }

    private static int scaleAlpha(int argb, float scale) {
        return (Math.round((argb >>> 24) * Math.clamp(scale, 0, 1)) << 24) | (argb & 0xffffff);
    }
}
