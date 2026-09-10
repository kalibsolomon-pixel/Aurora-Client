package com.aurora.client.screen.setting;

import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Non-interactive divider row used to delineate sub-feature sections
 * inside a single feature's settings list. Renders a small uppercase
 * label aligned with the rest of the settings rows (no trailing
 * separator line), with more blank space packed above it than sits
 * below it (design language §2's section-header rhythm).
 *
 * <p>Used by the {@code pack_tweaks} feature to label its Totem / Shield
 * / Item Scaling / Water &amp; Lava sub-sections so each block of sliders
 * is visually distinct.
 */
public class SectionHeaderSetting extends FeatureSetting {

    /** Total row height in pixels. */
    private static final int ROW_H = 18;
    /**
     * Extra blank space packed above the caption, inside the row (design
     * language §2: more space above a section header than below it — the
     * header reads as "starting something new," its rows follow close
     * behind). The owning screen's uniform row gap still applies on top of
     * this, so the blank band before a header is this + the row gap while
     * the band after it is the row gap alone.
     */
    private static final int SECTION_GAP_ABOVE = 10;
    /** Vertical baseline for the label inside {@link #ROW_H}. */
    private static final int LABEL_Y = 6;
    /**
     * Horizontal padding before the label. Matches {@code BooleanSetting#LABEL_PAD}
     * (=12) so the section caption lines up vertically with the row labels
     * underneath it instead of floating against the panel edge.
     */
    private static final int LABEL_PAD = 12;

    public SectionHeaderSetting(String label) {
        super(label);
    }

    @Override public int baseHeight() { return SECTION_GAP_ABOVE + ROW_H; }
    @Override public int height()     { return SECTION_GAP_ABOVE + ROW_H; }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        Font tr = Minecraft.getInstance().font;
        // Section label — dimmed uppercase, aligned with the indent of
        // the regular setting rows below it. No strikethrough separator;
        // the caption alone is enough to read as a section break.
        String text = label.toUpperCase();
        ctx.drawString(tr, text, x + LABEL_PAD, y + SECTION_GAP_ABOVE + LABEL_Y,
                AuroraTheme.sectionCaption(), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int rowX, int rowY, int rowWidth) {
        // Non-interactive: never consume.
        return false;
    }
}
