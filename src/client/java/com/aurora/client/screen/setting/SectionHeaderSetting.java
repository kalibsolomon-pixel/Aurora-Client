package com.aurora.client.screen.setting;

import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Non-interactive divider row used to delineate sub-feature sections
 * inside a single feature's settings list. Renders the muted label as
 * authored (no forced capitals — Spxctra 2026-10-30) aligned with the rest of the settings rows (no trailing
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
    private static final int SECTION_GAP_ABOVE = 18;
    /** Vertical baseline for the label inside {@link #ROW_H}. */
    private static final int LABEL_Y = 6;
    /**
     * Horizontal padding before the label. Matches {@code BooleanSetting#LABEL_PAD}
     * (=12) so the section caption lines up vertically with the row labels
     * underneath it instead of floating against the panel edge.
     */
    private static final int LABEL_PAD = 12;

    /**
     * True when this caption is the first visible row of its list: the window's header band
     * already separates it, so the extra band above would only be dead space.
     */
    private boolean leading;

    public SectionHeaderSetting(String label) {
        super(label);
    }

    /** Marks the first visible row (if it is a caption) as leading; clears every other caption. */
    public static void markLeading(java.util.List<? extends FeatureSetting> settings) {
        boolean first = true;
        for (FeatureSetting s : settings) {
            if (!s.isVisible()) continue;
            if (s instanceof SectionHeaderSetting h) h.leading = first;
            first = false;
        }
    }

    private int gapAbove() { return leading ? 0 : SECTION_GAP_ABOVE; }

    @Override public int baseHeight() { return gapAbove() + (compactRow == null ? ROW_H : compactRow.height()); }
    @Override public int height()     { return baseHeight(); }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        if (compactRow != null) { renderCompactLabel(ctx, x, y + gapAbove(), mouseX, mouseY, AuroraTheme.sectionCaption()); return; }
        Font tr = Minecraft.getInstance().font;
        // Section label — muted, as authored, aligned with the indent of
        // the regular setting rows below it. No separator; the caption
        // alone is enough to read as a section break.
        ctx.drawString(tr, label, x + LABEL_PAD, y + gapAbove() + LABEL_Y,
                AuroraTheme.sectionCaption(), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int rowX, int rowY, int rowWidth) {
        // Non-interactive: never consume.
        return false;
    }
}
