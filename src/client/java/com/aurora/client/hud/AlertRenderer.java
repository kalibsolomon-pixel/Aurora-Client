package com.aurora.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Renders the active {@link AlertManager.Alert} popup: centered on screen,
 * icon + title + optional subtitle, with a smoothstep fade-in/fade-out.
 *
 * <p>Replaces {@code ArmorAlertRenderer}; any feature that fires an alert
 * through {@link AlertManager} gets its popup rendered here automatically.
 */
public final class AlertRenderer {

    public void render(GuiGraphics ctx) {
        AlertManager.Alert alert = AlertManager.getActive();
        if (alert == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return;

        long age = alert.ageMs();
        long total = alert.durationMs;
        float alpha = computeAlpha(age, total);
        if (alpha <= 0.01f) return;

        Font tr = client.font;

        int textW = tr.width(alert.title);
        int subW = alert.subtitle != null && !alert.subtitle.isEmpty()
                ? tr.width(alert.subtitle) : 0;
        int contentW = Math.max(textW, subW);

        boolean hasIcon = alert.iconStack != null && !alert.iconStack.isEmpty();
        int iconSize = 24;
        int padding = 6;
        int gap = hasIcon ? padding : 0;

        int boxW = contentW + gap + (hasIcon ? iconSize : 0) + padding * 3;
        int titleH = tr.lineHeight;
        int subH = subW > 0 ? tr.lineHeight + 2 : 0;
        int boxH = Math.max(iconSize, titleH + subH) + padding * 2;

        int bgAlpha = Math.round(0xC0 * alpha);
        int bg = (bgAlpha << 24);
        int borderAlpha = Math.round(0xFF * alpha);
        int borderColor = (borderAlpha << 24) | (alert.color & 0x00FFFFFF);
        int textAlpha = Math.round(0xFF * alpha);
        int textColor = (textAlpha << 24) | (alert.color & 0x00FFFFFF);

        int screenW = ctx.guiWidth();
        int screenH = ctx.guiHeight();
        int cx = screenW / 2;
        int cy = screenH / 3; // upper third — out of aim area

        int x = cx - boxW / 2;
        int y = cy - boxH / 2;

        // Background
        ctx.fill(x, y, x + boxW, y + boxH, bg);
        // Border (4 edges)
        ctx.fill(x, y, x + boxW, y + 1, borderColor);
        ctx.fill(x, y + boxH - 1, x + boxW, y + boxH, borderColor);
        ctx.fill(x, y, x + 1, y + boxH, borderColor);
        ctx.fill(x + boxW - 1, y, x + boxW, y + boxH, borderColor);

        int contentX = x + padding;
        if (hasIcon) {
            int iconY = y + (boxH - iconSize) / 2;
            ctx.renderItem(alert.iconStack,
                    contentX + (iconSize - 16) / 2,
                    iconY + (iconSize - 16) / 2);
            contentX += iconSize + gap;
        }

        // Title
        int titleY = y + padding + (subH > 0 ? 0 : (boxH - titleH - padding * 2) / 2);
        ctx.drawString(tr, alert.title, contentX, titleY, textColor, false);

        // Subtitle
        if (subW > 0) {
            int subColor = (Math.round(0xAA * alpha) << 24) | 0x00FFFFFF;
            ctx.drawString(tr, alert.subtitle, contentX, titleY + tr.lineHeight + 2, subColor, false);
        }
    }

    /**
     * Smoothstep fade: 0→1 over the first 300ms, hold, then 1→0 over the
     * last 600ms of the alert duration.
     */
    private static float computeAlpha(long age, long total) {
        if (age < 0 || age >= total) return 0f;
        long fadeIn = 300L;
        long fadeOut = 600L;
        if (age < fadeIn) {
            float t = age / (float) fadeIn;
            return t * t * (3 - 2 * t);
        }
        if (age > total - fadeOut) {
            float t = (total - age) / (float) fadeOut;
            return t * t * (3 - 2 * t);
        }
        return 1f;
    }
}