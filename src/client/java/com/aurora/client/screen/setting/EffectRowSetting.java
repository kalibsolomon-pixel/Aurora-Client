package com.aurora.client.screen.setting;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.Widget;
import com.aurora.client.util.AuroraShapes;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * One added-effect row in the Alerts → Per-Effect Alerts curated list —
 * the {@link ItemScaleSetting} per-item row adapted to effects: 38px
 * panel (ON_BACKGROUND wash, hover outline), the effect's vanilla 16×16
 * {@code mob_effect/} sprite, and its display name — presence in the list
 * already means "alerts", so unlike the item rows there is nothing to
 * expand and no chevron.
 *
 * <p>The container ({@link EffectExpiryListSetting}) owns the backing list,
 * performs removals, and — since the Phase C-4 rollout — owns the row's
 * trailing remove icon action (the canonical close glyph through the C-4
 * primitive, painted by the container in this row's trailing 16×16 zone;
 * the text "x" this row used to draw itself is gone).
 */
public class EffectRowSetting extends FeatureSetting {

    /** ItemScaleSetting's row header height / stride, matched exactly. */
    public static final int ROW_H = 38;
    public static final int STRIDE = 42;
    private static final int ICON_SIZE = 16;

    private final String effectId;
    private final Identifier spriteLoc;
    /** Package-private so the container can sort rows by display label. */
    String effectId() { return effectId; }

    public EffectRowSetting(String effectId, Identifier effectLoc, String displayName) {
        super(displayName);
        this.effectId = effectId;
        this.spriteLoc = Identifier.fromNamespaceAndPath(
                effectLoc.getNamespace(), "mob_effect/" + effectLoc.getPath());
    }

    @Override public int baseHeight() { return ROW_H; }
    @Override public int height() { return ROW_H; }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;

        // Header panel — the ItemScale row band; the hover outline matches
        // its footprint so there is no hover-without-click strip.
        boolean rowHover = Widget.inBounds(mouseX, mouseY, x + 10, y, width - 20, ROW_H);
        AuroraShapes.panel(ctx, x + 10, y, width - 20, ROW_H,
                ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_BACKGROUND), 0x1A),
                AuroraTheme.RADIUS_SMALL);
        if (rowHover) {
            AuroraShapes.outline(ctx, x + 10, y, width - 20, ROW_H,
                    AuroraTheme.BORDER_OFF, AuroraTheme.RADIUS_SMALL);
        }

        // Effect sprite — the same status-effect icon the vanilla
        // inventory effect list draws (PotionModule's icon path).
        ctx.blitSprite(RenderPipelines.GUI_TEXTURED, spriteLoc,
                x + 14, y + (ROW_H - ICON_SIZE) / 2, ICON_SIZE, ICON_SIZE);

        // Display name, indented past the icon (ItemScale row geometry),
        // vertically centered — no §4 subtitle: the row's presence in the
        // list IS its state (exclusion clause).
        int textY = y + (ROW_H - tr.lineHeight) / 2;
        ctx.drawString(tr, label, x + 20 + ICON_SIZE + 4, textY, AuroraTheme.IOS_LABEL, false);
        // The trailing remove control is the container's icon action (C-4):
        // it paints itself into this row's 16×16 zone from the container's
        // render loop, right after the row body.
    }

    /** True when (mouseX, mouseY) is on this row's remove control. */
    public boolean trashHit(double mouseX, double mouseY, int x, int y, int width) {
        int rightX = x + width - 24;
        return Widget.inBounds(mouseX, mouseY, rightX - 16, y + (ROW_H - 16) / 2, 16, 16);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int rowX, int rowY, int rowWidth) {
        return false; // the container performs removals (ItemScale's division of labor)
    }
}
