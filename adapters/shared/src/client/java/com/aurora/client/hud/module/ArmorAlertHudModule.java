package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.AlertHudState;
import com.aurora.client.util.CachedValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * The armor/tool durability alert as a movable, resizable HUD module
 * (replaces the shared popup card for that source). One row per latched
 * low item, fed each tick by {@code ArmorAlertFeature} through
 * {@link AlertHudState}: item icon, the fire-time slot label and the
 * remaining durability percent (plus "×N" when several identical stacks
 * share the fingerprint).
 *
 * <p>Rows persist while the latched item stays in the player's possession
 * — moving it through the inventory does not blink the row — and the
 * panel fades out once the list empties. Standard module rules apply:
 * background mode/color, text color, theme corners, layout, scale,
 * profile snapshot, per-prefix reset.
 */
public class ArmorAlertHudModule extends HudModule {
    public static final String ID = "armor_alert";

    private static final int ICON = 16;
    private static final int ROW_H = 18;
    private static final int ICON_GAP = 4;
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

    public ArmorAlertHudModule() {
        super(ID);
        // Middle left: the right edge belongs to the scoreboard sidebar, vanilla or Aurora.
        this.anchor = com.aurora.client.hud.HudAnchor.MIDDLE_LEFT;
        this.offsetX = 4;
        this.offsetY = -40;
    }

    @Override
    public boolean isConfigEnabled() {
        AuroraConfig cfg = AuroraConfig.get();
        return cfg.armorAlertHudEnabled && cfg.armorAlertEnabled;
    }

    @Override protected AuroraConfig.HudBackground backgroundMode() { return AuroraConfig.get().armorAlertBgMode; }
    @Override protected int backgroundColor() { return AuroraConfig.get().armorAlertBgColor; }
    @Override protected boolean themeCorners() { return AuroraConfig.get().armorAlertThemeCorners; }

    private List<AlertHudState.ArmorRow> rows() { return AlertHudState.armor(); }

    private String rowText(AlertHudState.ArmorRow row) {
        String text = row.label() + " · " + row.remainingPct() + "%";
        return row.count() > 1 ? text + " ×" + row.count() : text;
    }

    private int[] computeDims() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return new int[] {120, ROW_H};
        Font tr = client.font;
        int maxTextW = MIN_TEXT_W;
        for (AlertHudState.ArmorRow row : rows()) {
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
        List<AlertHudState.ArmorRow> rows = rows();
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
        List<AlertHudState.ArmorRow> rows = rows();
        if (rows.isEmpty()) return;
        if (client.player == null || client.font == null) return;
        Font tr = client.font;
        AuroraConfig cfg = AuroraConfig.get();
        int color = com.aurora.client.theme.HudText.color(cfg.armorAlertTextColor, cfg.hudColor);

        for (int i = 0; i < rows.size(); i++) {
            AlertHudState.ArmorRow row = rows.get(i);
            int rowY = y + i * ROW_H;
            int iconY = rowY + (ROW_H - ICON) / 2;
            if (row.stack() != null && !row.stack().isEmpty()) {
                ctx.item(row.stack(), x, iconY);
            }
            String text = rowText(row);
            int ty = rowY + (ROW_H - tr.lineHeight) / 2;
            com.aurora.client.hud.HudTextPainter.draw(ctx, tr, textCache.text(i, text),
                    x + ICON + ICON_GAP, ty, color);
        }
    }

    @Override public String featureRegistryId() { return "alerts"; }
}
