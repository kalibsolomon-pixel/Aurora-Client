package com.aurora.client.ui.util;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The one field-validation message painter (design language §11.12): a short,
 * specific line under the field it refuses, in the theme's error colour —
 * "Name already used", "Use 6 hex digits, like 3A7BFF". One painter so every
 * screen's rejection reads the same; WaypointEditScreen's save-time heading
 * keeps its own placement but the same token and message voice.
 *
 * <p>Fitting follows the overflow rules: measured with the active font in the
 * field's real column, wrapped between whole words ({@link WordWrap} through
 * the core), bounded ellipsis only when a single word cannot fit.
 */
public final class FieldMessage {
    private FieldMessage() {}

    /** Single line, ellipsized to {@code width}. */
    public static void draw(GuiGraphicsExtractor ctx, Font font, String message, int x, int y, int width) {
        if (message == null || message.isBlank() || width <= 0) return;
        ctx.text(font, AuroraFontRenderer.ellipsize(font, message, width, 0), x, y,
                ThemeManager.color(ThemeToken.STATUS_ERROR), false);
    }

    /** Word-wrapped to at most {@code maxLines} lines of {@code width}. */
    public static void drawWrapped(GuiGraphicsExtractor ctx, Font font, String message,
                                   int x, int y, int width, int maxLines) {
        if (message == null || message.isBlank() || width <= 0 || maxLines <= 0) return;
        int color = ThemeManager.color(ThemeToken.STATUS_ERROR);
        int line = 0;
        for (String text : WordWrap.lines(message, width, maxLines, font::width)) {
            ctx.text(font, text, x, y + line * font.lineHeight, color, false);
            line++;
        }
    }
}
