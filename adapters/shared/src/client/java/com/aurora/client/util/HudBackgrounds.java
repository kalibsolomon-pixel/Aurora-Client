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
    /** The vanilla hotbar texture: 182×22, slot cells on a 20 px pitch starting at x=1. */
    private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hud/hotbar");
    private static final int HOTBAR_SPRITE_W = 182;
    private static final int HOTBAR_SPRITE_H = 22;
    public static final int CELL_PITCH = 20;

    /**
     * One continuous vanilla hotbar strip of {@code cells} slots, drawn
     * 22 px tall at (x, y) and uniformly scaled horizontally by
     * {@code scaleX} (1.0 = native 20 px cells). One blit of the strip's
     * sub-region of the hotbar sprite — the armor HUD's VANILLA backing,
     * replacing the per-piece offhand-slot boxes.
     */
    public static void hotbarStrip(GuiGraphicsExtractor ctx, int x, int y, int cells, float scaleX, float alpha) {
        if (cells <= 0 || alpha <= 0.001f) return;
        int srcW = cells * CELL_PITCH + 2;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(scaleX, 1.0f);
        ctx.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, HOTBAR_SPRITE_W, HOTBAR_SPRITE_H,
                0, 0, 0, 0, srcW, HOTBAR_SPRITE_H, scaleAlpha(0xFFFFFFFF, alpha));
        ctx.pose().popMatrix();
    }

    /**
     * One vanilla hotbar slot cell — a 20×22 source crop that carries both
     * borders — drawn at (x, y) scaled to (w, h). Stacked with zero gap it
     * reads as a vertical hotbar strip.
     */
    public static void hotbarCell(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0.001f) return;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(w / (float) CELL_PITCH, h / (float) HOTBAR_SPRITE_H);
        ctx.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, HOTBAR_SPRITE_W, HOTBAR_SPRITE_H,
                1, 0, 0, 0, CELL_PITCH, HOTBAR_SPRITE_H, scaleAlpha(0xFFFFFFFF, alpha));
        ctx.pose().popMatrix();
    }

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
