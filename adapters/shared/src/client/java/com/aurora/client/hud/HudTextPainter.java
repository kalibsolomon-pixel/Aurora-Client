package com.aurora.client.hud;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Every HUD module's text goes through here (2026-11-12), so the Text
 * section's HUD text shadow applies to all of it: Off draws as before, Normal
 * is Minecraft's drop shadow, Full is a one-pixel outline drawn as eight
 * offset copies in black (at the text colour's alpha) under the text.
 * Aurora's menus never shadow text; this is for the in-world HUD only.
 */
public final class HudTextPainter {
    private HudTextPainter() {}

    private static final int[] OUTLINE_DX = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] OUTLINE_DY = {-1, -1, -1, 0, 0, 1, 1, 1};

    private static AuroraConfig.TextShadow shadow() {
        AuroraConfig.TextShadow s = AuroraConfig.get().textHudShadow;
        return s == null ? AuroraConfig.TextShadow.OFF : s;
    }

    /** Black at the text colour's alpha — the outline's ink. */
    private static int outlineColor(int color) {
        return color & 0xFF000000;
    }

    public static void draw(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        switch (shadow()) {
            case OFF -> g.text(font, text, x, y, color, false);
            case NORMAL -> g.text(font, text, x, y, color, true);
            case FULL -> {
                int ink = outlineColor(color);
                for (int i = 0; i < OUTLINE_DX.length; i++) g.text(font, text, x + OUTLINE_DX[i], y + OUTLINE_DY[i], ink, false);
                g.text(font, text, x, y, color, false);
            }
        }
    }

    public static void draw(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color) {
        draw(g, font, text.getVisualOrderText(), x, y, color);
    }

    public static void draw(GuiGraphicsExtractor g, Font font, FormattedCharSequence text, int x, int y, int color) {
        switch (shadow()) {
            case OFF -> g.text(font, text, x, y, color, false);
            case NORMAL -> g.text(font, text, x, y, color, true);
            case FULL -> {
                int ink = outlineColor(color);
                // Coloured segments would keep their own colour; the outline
                // copies are recoloured to the ink.
                FormattedCharSequence outline = recolored(text, ink & 0xFFFFFF);
                for (int i = 0; i < OUTLINE_DX.length; i++) g.text(font, outline, x + OUTLINE_DX[i], y + OUTLINE_DY[i], ink, false);
                g.text(font, text, x, y, color, false);
            }
        }
    }

    /** {@code sequence} with every character's colour set to {@code rgb}; one Style per styled segment. */
    private static FormattedCharSequence recolored(FormattedCharSequence sequence, int rgb) {
        return visitor -> sequence.accept(new net.minecraft.util.FormattedCharSink() {
            private Style lastSource;
            private Style lastColored;

            @Override
            public boolean accept(int index, Style style, int codePoint) {
                if (style != lastSource) {
                    lastSource = style;
                    lastColored = style.withColor(rgb);
                }
                return visitor.accept(index, lastColored, codePoint);
            }
        });
    }
}
