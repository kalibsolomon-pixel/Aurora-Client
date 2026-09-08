package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.ToggleSprintFeature;
import com.aurora.client.hud.HudAnchor;
import com.aurora.client.util.CachedValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Single-row HUD module used when the Toggle Sprint/Sneak feature is in
 * {@link AuroraConfig.ToggleSprintDisplayMode#INDIVIDUAL} mode.
 *
 * <p>Two instances are registered — one for sprint, one for sneak — so
 * the user can drag, resize and show/hide each independently in the HUD
 * editor. Both stay dormant (invisible) until that display mode is
 * selected.
 */
public class ToggleIndividualModule extends HudModule {
    public static final String SPRINT_ID = "toggle_sprint";
    public static final String SNEAK_ID  = "toggle_sneak";

    // State colors come from the HUD layer's shared status palette
    // (com.aurora.client.theme.HudStatus); label text stays plain white.
    private static final int COLOR_ON    = com.aurora.client.theme.HudStatus.ON;
    private static final int COLOR_OFF   = com.aurora.client.theme.HudStatus.OFF;
    private static final int COLOR_LABEL = 0xFFFFFFFF;

    /** Which toggle this instance renders. */
    public enum Kind { SPRINT, SNEAK }

    private final Kind kind;
    private final String label;
    private final Component labelComponent;
    private final int defaultOffsetY;

    private int cachedLabelW = -1;

    private final CachedValue<Integer> cachedWidth = new CachedValue<>(150L, this::computeWidth);

    public ToggleIndividualModule(Kind kind) {
        super(kind == Kind.SPRINT ? SPRINT_ID : SNEAK_ID);
        this.kind = kind;
        this.label = (kind == Kind.SPRINT ? "Sprint: " : "Sneak: ");
        this.labelComponent = Component.literal(label);
        // Stack the two modules vertically by default (sprint above sneak)
        // so they don't overlap on first use.
        this.defaultOffsetY = (kind == Kind.SPRINT ? 4 : 26);
        // Distinct default anchor/offset so the two halves start apart.
        this.anchor  = HudAnchor.TOP_LEFT;
        this.offsetX = 4;
        this.offsetY = defaultOffsetY;
    }

    @Override
    public String featureRegistryId() {
        // These module ids don't map to any FeatureRegistry entry, so the
        // HUD editor's X-button falls back to hiding just this one module
        // rather than disabling the whole feature.
        return id();
    }

    @Override
    public String displayName() {
        return (kind == Kind.SPRINT ? "Sprint" : "Sneak") + " Toggle";
    }

    /**
     * Restore the per-instance defaults set in the constructor (the
     * sprint half sits above the sneak half so they don't overlap).
     * Called by the HUD editor's "Reset Positions" button.
     */
    @Override
    public void resetLayoutToDefaults() {
        this.anchor  = HudAnchor.TOP_LEFT;
        this.offsetX = 4;
        this.offsetY = defaultOffsetY;
        this.scale   = 1.0f;
        this.enabled = true;
        this.locked  = false;
    }

    private boolean isOn() {
        return kind == Kind.SPRINT
                ? ToggleSprintFeature.isSprintToggled()
                : ToggleSprintFeature.isSneakToggled();
    }

    @Override protected AuroraConfig.HudBackground backgroundMode() { return AuroraConfig.get().sprintHudBgMode; }
    @Override protected int backgroundColor() { return AuroraConfig.get().sprintHudBgColor; }

    private int computeWidth() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return 50;
        Font tr = client.font;
        ensureLabelWidth(tr);
        return cachedLabelW + tr.width(isOn() ? "ON" : "OFF") + 4;
    }

    private void ensureLabelWidth(Font tr) {
        if (cachedLabelW < 0) cachedLabelW = tr.width(label);
    }

    @Override public int getWidth()  { return cachedWidth.get(); }

    @Override
    public int getHeight() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return 12;
        return client.font.lineHeight + 2;
    }

    @Override
    public boolean isConfigEnabled() {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.toggleSprintSneakEnabled) return false;
        if (!cfg.toggleSprintSneakHudEnabled) return false;
        return cfg.toggleSprintSneakDisplayMode == AuroraConfig.ToggleSprintDisplayMode.INDIVIDUAL;
    }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft client, int x, int y) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.toggleSprintSneakEnabled) return;
        if (!cfg.toggleSprintSneakHudEnabled) return;

        Font tr = client.font;
        if (tr == null) return;
        ensureLabelWidth(tr);

        boolean on = isOn();
        ctx.drawString(tr, labelComponent, x, y, COLOR_LABEL, false);
        ctx.drawString(tr, on ? "ON" : "OFF", x + cachedLabelW, y, on ? COLOR_ON : COLOR_OFF, false);
    }
}