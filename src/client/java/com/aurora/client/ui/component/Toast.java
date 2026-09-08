package com.aurora.client.ui.component;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.RenderUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Transient bottom-center toast ("Switched to X", "Installed Y"). Shared
 * by the profile/waypoint manager screens and the pack browser, which
 * previously carried three copies of the same fade math: alpha holds at
 * 220 for the toast's lifetime and eases out over the final 250 ms; the
 * pill is 18 px tall, 32 px above the screen bottom, with 8 px horizontal
 * padding around the text. The corner radius stays a per-caller decision
 * (the manager screens pin a literal 4; the pack browser follows the
 * theme's small-radius token) so adoption changes no pixels.
 */
public final class Toast {

    private static final long FADE_TAIL_MS = 250L;
    private static final int BASE_ALPHA = 220;
    private static final int BG_ALPHA = 160;

    private String text;
    private long untilMs;

    /** Show {@code text} for {@code durationMs}, replacing any current toast. */
    public void flash(String text, long durationMs) {
        this.text = text;
        this.untilMs = System.currentTimeMillis() + durationMs;
    }

    /** Draw the toast if one is active; call once per frame after the screen's content. */
    public void render(GuiGraphics g, Font font, int screenW, int screenH, int radius) {
        if (text == null || System.currentTimeMillis() >= untilMs) return;
        long remaining = untilMs - System.currentTimeMillis();
        int alpha = (int) Math.min(255, remaining > FADE_TAIL_MS ? BASE_ALPHA : remaining * BASE_ALPHA / FADE_TAIL_MS);
        int textColor = ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_OVERLAY), alpha);
        int bgColor = ThemeManager.withAlpha(ThemeManager.color(ThemeToken.OVERLAY_DIM), alpha * BG_ALPHA / 255);
        int tw = font.width(text) + 16;
        int tx = (screenW - tw) / 2;
        int ty = screenH - 32;
        RenderUtil.drawRoundedRectAA(g, tx, ty, tw, 18, radius, bgColor);
        AuroraFontRenderer.drawCentered(g, font, Component.literal(text), screenW / 2, ty + 5, textColor);
    }
}
