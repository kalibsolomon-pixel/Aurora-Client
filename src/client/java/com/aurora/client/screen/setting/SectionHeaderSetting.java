package com.aurora.client.screen.setting;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.interaction.MinecraftSemanticFeedback;
import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.AuroraTheme;
import com.aurora.client.util.HoverAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A section caption that groups the rows after it — and, since the compact pass
 * (2026-10-30), an expandable category: clicking the caption (or Enter/Space when focused)
 * collapses or expands every row up to the next caption. Collapsed rows are simply not
 * visible, so painting, scroll extent, hit testing and keyboard availability all follow the
 * existing visibility path.
 *
 * <p>Owners call {@link #bind} once per screen open. Long lists (three or more sections)
 * open with only their first section expanded so the window shows the categories at a
 * glance; shorter lists open fully expanded. A player's choice is remembered for the session
 * per feature and caption.
 *
 * <p>The caption renders as authored in the muted caption color, with the shared
 * expand/collapse chevron at the row's trailing edge. Hover brightens the caption; focus is
 * the shared accent ring.
 */
public class SectionHeaderSetting extends FeatureSetting {

    private static final int ROW_H = com.aurora.client.ui.util.UiDensity.SECTION_ROW;
    /** Blank band above a non-leading caption (more space above a group than inside it). */
    private static final int SECTION_GAP_ABOVE = com.aurora.client.ui.util.UiDensity.SECTION_GAP;
    private static final int LABEL_Y = 3;
    /** Matches the row label indent so captions align with the labels under them. */
    private static final int LABEL_PAD = 12;
    /** Lists with at least this many sections open with only the first expanded. */
    static final int COLLAPSE_FROM = 3;

    /** Session memory of collapsed state, keyed "ownerId/caption". */
    private static final Map<String, Boolean> COLLAPSED = new HashMap<>();

    /** True when this caption is the first visible row of its list (no band above it). */
    private boolean leading;
    private String key;
    private boolean collapsed;
    /** True while the state is the automatic default (the player has not toggled it). */
    private boolean defaulted;
    private final HoverAnim hover = HoverAnim.symmetric(140);
    private final SemanticActionControl interactionControl;

    public SectionHeaderSetting(String label) {
        super(label);
        SemanticAction action = SemanticAction.button(Component.literal(label),
                () -> Component.literal(collapsed ? "Expand this section" : "Collapse this section"),
                () -> Component.literal(collapsed ? "Collapsed" : "Expanded"),
                () -> true, this::toggle);
        this.interactionControl = new SemanticActionControl(action, MinecraftSemanticFeedback.INSTANCE,
                () -> {}, SemanticActionControl.PointerRouting.MANUAL);
    }

    /**
     * Groups each row under the caption above it and restores (or defaults) each caption's
     * collapsed state. Call before measuring/painting the list.
     */
    public static void bind(List<? extends FeatureSetting> settings, String ownerId) {
        int sections = 0;
        for (FeatureSetting s : settings) if (s instanceof SectionHeaderSetting) sections++;
        SectionHeaderSetting current = null;
        int index = 0;
        for (FeatureSetting s : settings) {
            if (s instanceof SectionHeaderSetting h) {
                h.key = ownerId + "/" + h.label;
                h.defaulted = !COLLAPSED.containsKey(h.key);
                h.collapsed = COLLAPSED.getOrDefault(h.key, sections >= COLLAPSE_FROM && index > 0);
                h.section(null);
                current = h;
                index++;
            } else {
                s.section(current);
            }
        }
    }

    /**
     * Marks captions that need no band above them: the first visible row (the window header
     * already separates it) and a caption directly under a collapsed caption (a run of
     * collapsed categories reads as one compact list).
     */
    public static void markLeading(List<? extends FeatureSetting> settings) {
        boolean first = true;
        SectionHeaderSetting previousCollapsed = null;
        for (FeatureSetting s : settings) {
            if (!s.isVisible()) continue;
            if (s instanceof SectionHeaderSetting h) {
                h.leading = first || previousCollapsed != null;
                previousCollapsed = h.collapsed ? h : null;
            } else previousCollapsed = null;
            first = false;
        }
    }

    public boolean expanded() { return !collapsed; }

    /**
     * Applies an automatic default to every caption the player has not toggled: owners that
     * can measure (the detail screen) open everything when the fully expanded list fits the
     * window, and keep the collapsed default only when it would scroll.
     * Returns whether any caption was affected.
     */
    public static boolean setDefaultExpanded(List<? extends FeatureSetting> settings, boolean expandAll) {
        int sections = 0, index = 0;
        for (FeatureSetting s : settings) if (s instanceof SectionHeaderSetting) sections++;
        boolean any = false;
        for (FeatureSetting s : settings) {
            if (!(s instanceof SectionHeaderSetting h)) continue;
            if (h.defaulted) { h.collapsed = !expandAll && sections >= COLLAPSE_FROM && index > 0; any = true; }
            index++;
        }
        return any;
    }

    public void toggle() {
        collapsed = !collapsed;
        defaulted = false;
        // Spacing of the following captions depends on this state; owners re-mark every frame.
        if (key != null) COLLAPSED.put(key, collapsed);
    }

    private int gapAbove() { return leading ? 0 : SECTION_GAP_ABOVE; }

    @Override public int baseHeight() { return gapAbove() + (compactRow == null ? ROW_H : compactRow.height()); }
    @Override public int height()     { return baseHeight(); }

    @Override public SemanticActionControl interactionControl() { return interactionControl; }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        Font tr = Minecraft.getInstance().font;
        int top = y + gapAbove();
        int h = baseHeight() - gapAbove();
        interactionControl.setBounds(x, top, width, h);
        interactionControl.updatePointer(mouseX, mouseY);
        boolean over = mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < top + h;
        float t = hover.update(over);
        int color = AuroraAnim.lerpArgb(AuroraTheme.sectionCaption(),
                MaterialSurface.color(ThemeToken.ON_BACKGROUND), t * .6f);
        int textY = top + (h - tr.lineHeight) / 2 + 1;
        ctx.drawString(tr, label, x + LABEL_PAD, textY, color, false);
        // Trailing chevron (native-resolution icon): down = collapsed, up = expanded.
        com.aurora.client.ui.util.MaterialIconRenderer.drawIcon(ctx, tr,
                com.aurora.client.screen.FeatureIcons.get(collapsed ? "_dropdown_expand_more" : "_dropdown_expand_less"),
                x + width - 14, top + h / 2f, 9, color);
        if (interactionControl.isFocused()) {
            float r = ThemeManager.current().roundness().radiusSmall();
            RenderUtil.drawRoundedOutlineAA(ctx, x + 4, top, width - 8, h, r, 1f,
                    MaterialSurface.color(ThemeToken.ACCENT));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int rowX, int rowY, int rowWidth) {
        if (button != 0) return false;
        int top = rowY + gapAbove();
        int h = baseHeight() - gapAbove();
        if (mouseX < rowX || mouseX >= rowX + rowWidth || mouseY < top || mouseY >= top + h) return false;
        interactionControl.setBounds(rowX, top, rowWidth, h);
        return interactionControl.activateFromPointer(mouseX, mouseY, button);
    }
}
