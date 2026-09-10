package com.aurora.client.screen.setting;

import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Non-interactive explanatory footer rendered below a section's last row,
 * before the next section header (design language §3: body text belongs
 * to a group, positioned immediately after it — not inline with the rows,
 * not tooltip-only). Small, left-aligned at the row-label indent, wrapped
 * to the list width, in {@code ON_BACKGROUND_MUTED}.
 *
 * <p>The spec's restraint rule applies: only add a footer where a group's
 * purpose genuinely isn't self-evident from its header + row labels alone
 * — never one per section reflexively. First user: Better Hitreg's
 * Tracking section, whose "Alert" toggles no longer alert anything (chat
 * alerts were retired at integration; the toggles now gate live figures).
 */
public class SectionFooterSetting extends FeatureSetting {

    /** Blank band above the text, inside the row. */
    private static final int PAD_ABOVE = 4;
    /** Blank band below the text, inside the row. */
    private static final int PAD_BELOW = 2;
    /** Matches {@code BooleanSetting#LABEL_PAD} (=12) so the footer lines
     *  up with the group's row labels. */
    private static final int LABEL_PAD = 12;

    private int lastWidth = 240;
    private int cachedWidth = -1;
    private List<FormattedCharSequence> cachedLines = List.of();

    public SectionFooterSetting(String text) {
        super(text);
    }

    private void ensureLines(Font tr, int width) {
        if (width == cachedWidth && !cachedLines.isEmpty()) return;
        cachedLines = tr.split(Component.literal(label), Math.max(40, width - LABEL_PAD - 4));
        cachedWidth = width;
    }

    @Override
    public int baseHeight() {
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return PAD_ABOVE + PAD_BELOW + 9;
        ensureLines(tr, lastWidth);
        return PAD_ABOVE + PAD_BELOW + cachedLines.size() * (tr.lineHeight + 1);
    }

    @Override
    public int height() { return baseHeight(); }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;
        lastWidth = width;
        ensureLines(tr, width);
        int ty = y + PAD_ABOVE;
        for (FormattedCharSequence line : cachedLines) {
            ctx.drawString(tr, line, x + LABEL_PAD, ty, AuroraTheme.TEXT_DIM, false);
            ty += tr.lineHeight + 1;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int rowX, int rowY, int rowWidth) {
        // Non-interactive: never consume.
        return false;
    }
}
