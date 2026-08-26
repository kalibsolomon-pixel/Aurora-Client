package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.ToggleSprintFeature;
import com.aurora.client.util.CachedValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * HUD module for the Toggle Sprint/Sneak feature.
 *
 * <p>Rendering depends on {@link AuroraConfig.ToggleSprintDisplayMode}:
 * <ul>
 *   <li>{@code BOTH} — two rows, {@code "Sprint: ON/OFF"} and
 *       {@code "Sneak: ON/OFF"}.</li>
 *   <li>{@code BRACKETED_TEXT} — a single compact line listing only the
 *       currently active toggles inside brackets, e.g.
 *       {@code "[Sprint]"} / {@code "[Sneak]"} / {@code "[Sprint] [Sneak]"}.
 *       Renders nothing while neither toggle is active.</li>
 *   <li>{@code INDIVIDUAL} — this module is dormant; two separate
 *       {@link ToggleIndividualModule}s take over.</li>
 * </ul>
 */
public class ToggleSprintSneakModule extends HudModule {
    public static final String ID = "toggle_sprint_sneak";

    private static final int COLOR_ON    = 0xFF55FF55;
    private static final int COLOR_OFF   = 0xFFAAAAAA;
    private static final int COLOR_LABEL = 0xFFFFFFFF;

    // Pre-built strings and Component instances — these never change at runtime.
    private static final String LABEL_SPRINT = "Sprint: ";
    private static final String LABEL_SNEAK  = "Sneak: ";
    private static final Component TEXT_SPRINT_LABEL = Component.literal(LABEL_SPRINT);
    private static final Component TEXT_SNEAK_LABEL  = Component.literal(LABEL_SNEAK);
    private static final Component TEXT_ON  = Component.literal("ON");
    private static final Component TEXT_OFF = Component.literal("OFF");

    // Bracketed-text fragments.
    private static final String BRACKET_SPRINT = "[Sprint]";
    private static final String BRACKET_SNEAK  = "[Sneak]";
    private static final String SEPARATOR      = " ";

    // Label widths are constant after the font loads; cache them on first use.
    // -1 = uncomputed.
    private int sprintLabelW = -1;
    private int sneakLabelW  = -1;

    private final CachedValue<Integer> cachedWidth = new CachedValue<>(150L, this::computeWidth);

    public ToggleSprintSneakModule() {
        super(ID);
    }

    @Override protected AuroraConfig.HudBackground backgroundMode() { return AuroraConfig.get().sprintHudBgMode; }
    @Override protected int backgroundColor() { return AuroraConfig.get().sprintHudBgColor; }

    /**
     * In BRACKETED_TEXT mode, hide the background panel when neither
     * toggle is active — otherwise a tiny empty rectangle would linger
     * on screen. Both/Individual modes always keep their background.
     */
    @Override
    protected float backgroundAlpha() {
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.toggleSprintSneakDisplayMode != AuroraConfig.ToggleSprintDisplayMode.BRACKETED_TEXT) {
            return 1.0f;
        }
        boolean anyActive = ToggleSprintFeature.isSprintToggled() || ToggleSprintFeature.isSneakToggled();
        return anyActive ? 1.0f : 0.0f;
    }

    private int computeWidth() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return 80;
        Font tr = client.font;
        ensureLabelWidths(tr);
        AuroraConfig.ToggleSprintDisplayMode mode = AuroraConfig.get().toggleSprintSneakDisplayMode;
        if (mode == AuroraConfig.ToggleSprintDisplayMode.BRACKETED_TEXT) {
            return computeBracketedWidth(tr) + 4;
        }
        // Width depends on which state string ("ON" / "OFF") is wider for each row.
        int sprintW = sprintLabelW + tr.width(ToggleSprintFeature.isSprintToggled() ? "ON" : "OFF");
        int sneakW  = sneakLabelW  + tr.width(ToggleSprintFeature.isSneakToggled()  ? "ON" : "OFF");
        return Math.max(sprintW, sneakW) + 4;
    }

    /**
     * Width of the bracketed-text line for the currently active toggles.
     * Returns {@code 0} when neither toggle is on (nothing to draw).
     */
    private int computeBracketedWidth(Font tr) {
        boolean sprint = ToggleSprintFeature.isSprintToggled();
        boolean sneak  = ToggleSprintFeature.isSneakToggled();
        if (!sprint && !sneak) return 0;
        int w = 0;
        if (sprint) w += tr.width(BRACKET_SPRINT);
        if (sprint && sneak) w += tr.width(SEPARATOR);
        if (sneak)  w += tr.width(BRACKET_SNEAK);
        return w;
    }

    private void ensureLabelWidths(Font tr) {
        if (sprintLabelW < 0) sprintLabelW = tr.width(LABEL_SPRINT);
        if (sneakLabelW  < 0) sneakLabelW  = tr.width(LABEL_SNEAK);
    }

    @Override public int getWidth()  { return cachedWidth.get(); }
    @Override
    public int getHeight() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return 22;
        AuroraConfig.ToggleSprintDisplayMode mode = AuroraConfig.get().toggleSprintSneakDisplayMode;
        if (mode == AuroraConfig.ToggleSprintDisplayMode.BRACKETED_TEXT) {
            return client.font.lineHeight + 2;
        }
        return client.font.lineHeight * 2 + 4;
    }

    @Override
    public boolean isConfigEnabled() {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.toggleSprintSneakEnabled) return false;
        if (!cfg.toggleSprintSneakHudEnabled) return false;
        // In INDIVIDUAL mode the combined module hides so the two
        // individual modules take over.
        return cfg.toggleSprintSneakDisplayMode != AuroraConfig.ToggleSprintDisplayMode.INDIVIDUAL;
    }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft client, int x, int y) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.toggleSprintSneakEnabled) return;
        if (!cfg.toggleSprintSneakHudEnabled) return;

        Font tr = client.font;
        if (tr == null) return;

        if (cfg.toggleSprintSneakDisplayMode == AuroraConfig.ToggleSprintDisplayMode.BRACKETED_TEXT) {
            renderBracketed(ctx, tr, x, y);
            return;
        }

        // BOTH (classic two-row layout).
        ensureLabelWidths(tr);
        int row2Y = y + tr.lineHeight + 2;
        drawRow(ctx, tr, x, y,     TEXT_SPRINT_LABEL, sprintLabelW, ToggleSprintFeature.isSprintToggled());
        drawRow(ctx, tr, x, row2Y, TEXT_SNEAK_LABEL,  sneakLabelW,  ToggleSprintFeature.isSneakToggled());
    }

    /**
     * Renders the compact single-line bracketed status. Draws nothing
     * when neither toggle is active.
     */
    private void renderBracketed(GuiGraphics ctx, Font tr, int x, int y) {
        boolean sprint = ToggleSprintFeature.isSprintToggled();
        boolean sneak  = ToggleSprintFeature.isSneakToggled();
        if (!sprint && !sneak) return;

        int drawX = x;
        if (sprint) {
            ctx.drawString(tr, BRACKET_SPRINT, drawX, y, COLOR_ON, false);
            drawX += tr.width(BRACKET_SPRINT);
        }
        if (sprint && sneak) {
            ctx.drawString(tr, SEPARATOR, drawX, y, COLOR_LABEL, false);
            drawX += tr.width(SEPARATOR);
        }
        if (sneak) {
            ctx.drawString(tr, BRACKET_SNEAK, drawX, y, COLOR_ON, false);
        }
    }

    private static void drawRow(GuiGraphics ctx, Font tr, int x, int y,
                                Component label, int labelWidth, boolean on) {
        ctx.drawString(tr, label, x, y, COLOR_LABEL, false);
        ctx.drawString(tr, on ? TEXT_ON : TEXT_OFF, x + labelWidth, y, on ? COLOR_ON : COLOR_OFF, false);
    }
}