package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.AlertHudState;
import com.aurora.client.util.CachedValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.List;

/**
 * The effect-expiry alert as a movable, resizable HUD module (replaces the
 * shared popup card for that source). One row per latched expiring effect,
 * fed each tick by {@code StatusAlertFeature} through
 * {@link AlertHudState}: the vanilla status-effect sprite, the localized
 * name and a live seconds-left readout. A row stays up while its effect
 * remains at or below the threshold and goes when the effect is
 * re-applied, milked or expires; the panel fades out once the list
 * empties. Standard module rules apply: background mode/color, text
 * color, theme corners, layout, scale, profile snapshot, per-prefix
 * reset.
 */
public class EffectExpiryHudModule extends HudModule {
    public static final String ID = "potion_alert";

    private static final int ICON = 18;
    private static final int ROW_H = 22;
    private static final int ICON_GAP = 5;
    private static final int MIN_TEXT_W = 50;

    private final CachedValue<int[]> dimsCache = new CachedValue<>(150L, this::computeDims);
    private final HudTextCache textCache = new HudTextCache(8);

    /** Post-expiry fade, same contract as {@link PotionModule}. */
    private static final long FADE_MS = 300L;
    private long lastRowsPresentMs = 0L;
    private long cachedFadeTMs = Long.MIN_VALUE;
    private float cachedFadeT;
    private int lastNonEmptyW = 0;
    private int lastNonEmptyH = 0;

    public EffectExpiryHudModule() {
        super(ID);
        // Middle left: the right edge belongs to the scoreboard sidebar, vanilla or Aurora.
        this.anchor = com.aurora.client.hud.HudAnchor.MIDDLE_LEFT;
        this.offsetX = 4;
        this.offsetY = 40;
    }

    @Override
    public boolean isConfigEnabled() {
        AuroraConfig cfg = AuroraConfig.get();
        return cfg.effectExpiryHudEnabled && cfg.effectExpiryAlertEnabled;
    }

    @Override protected AuroraConfig.HudBackground backgroundMode() { return AuroraConfig.get().effectExpiryBgMode; }
    @Override protected int backgroundColor() { return AuroraConfig.get().effectExpiryBgColor; }
    @Override protected boolean themeCorners() { return AuroraConfig.get().effectExpiryThemeCorners; }

    private List<AlertHudState.EffectRow> rows() { return AlertHudState.effects(); }

    private String rowText(AlertHudState.EffectRow row) {
        return row.name() + " · " + row.secondsLeft() + "s";
    }

    private int[] computeDims() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return new int[] {120, ROW_H};
        Font tr = client.font;
        int maxTextW = MIN_TEXT_W;
        for (AlertHudState.EffectRow row : rows()) {
            maxTextW = Math.max(maxTextW, tr.width(rowText(row)));
        }
        int width = ICON + ICON_GAP + maxTextW;
        int n = rows().size();
        int height = n > 0 ? ROW_H * n : ROW_H;
        return new int[] {width, height};
    }

    private float rowsFadeT() {
        long now = System.currentTimeMillis();
        if (now == cachedFadeTMs) return cachedFadeT;
        cachedFadeTMs = now;
        List<AlertHudState.EffectRow> rows = rows();
        if (!rows.isEmpty()) {
            lastRowsPresentMs = now;
            int[] d = dimsCache.get();
            lastNonEmptyW = d[0];
            lastNonEmptyH = d[1];
            cachedFadeT = 1.0f;
            return cachedFadeT;
        }
        if (lastRowsPresentMs == 0L) { cachedFadeT = 0.0f; return cachedFadeT; }
        long dt = now - lastRowsPresentMs;
        cachedFadeT = dt >= FADE_MS ? 0.0f : 1.0f - dt / (float) FADE_MS;
        return cachedFadeT;
    }

    @Override protected float backgroundAlpha() { return rowsFadeT(); }

    @Override public int getWidth() {
        if (rowsFadeT() > 0f && lastNonEmptyW > 0) return lastNonEmptyW;
        return dimsCache.get()[0];
    }

    @Override public int getHeight() {
        if (rowsFadeT() > 0f && lastNonEmptyH > 0) return lastNonEmptyH;
        return dimsCache.get()[1];
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, Minecraft client, int x, int y) {
        List<AlertHudState.EffectRow> rows = rows();
        if (rows.isEmpty()) return;
        if (client.player == null || client.font == null) return;
        Font tr = client.font;
        AuroraConfig cfg = AuroraConfig.get();
        int color = com.aurora.client.theme.HudText.color(cfg.effectExpiryTextColor, cfg.hudColor);

        for (int i = 0; i < rows.size(); i++) {
            AlertHudState.EffectRow row = rows.get(i);
            int rowY = y + i * ROW_H;
            if (row.sprite() != null) {
                ctx.blitSprite(RenderPipelines.GUI_TEXTURED, row.sprite(),
                        x, rowY + (ROW_H - ICON) / 2, ICON, ICON);
            }
            String text = rowText(row);
            int ty = rowY + (ROW_H - tr.lineHeight) / 2;
            com.aurora.client.hud.HudTextPainter.draw(ctx, tr, textCache.text(i, text),
                    x + ICON + ICON_GAP, ty, color);
        }
    }

    @Override public String featureRegistryId() { return "alerts"; }
}
