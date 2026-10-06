package com.aurora.client.util;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.gui.GuiGraphics;
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
    /** Square-mode-exempt: floating HUD panels retain their compact boxy shape. */
    private static final int RADIUS = 2;
    private static final Identifier OFFHAND = Identifier.withDefaultNamespace("hud/hotbar_offhand_left");

    public static void draw(GuiGraphics ctx, int x, int y, int w, int h,
                            AuroraConfig.HudBackground mode, int color) {
        draw(ctx, x, y, w, h, mode, color, 1);
    }

    public static void draw(GuiGraphics ctx, int x, int y, int w, int h,
                            AuroraConfig.HudBackground mode, int color, float alpha) {
        if (mode == null || alpha <= 0.001f) return;
        switch (mode) {
            case SOLID -> RoundedRect.fill(ctx, x - 3, y - 3, x + w + 3, y + h + 3, RADIUS, scaleAlpha(color, alpha));
            case VANILLA -> slot(ctx, x - 3, y - 3, w + 6, h + 6, alpha);
            default -> {}
        }
    }

    /** Crop the 22x22 offhand slot, excluding its unused hotbar attachment margin. */
    public static void slot(GuiGraphics ctx, int x, int y, int width, int height, float alpha) {
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
