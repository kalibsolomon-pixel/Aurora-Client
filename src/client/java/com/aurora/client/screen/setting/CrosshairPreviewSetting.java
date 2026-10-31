package com.aurora.client.screen.setting;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.CrosshairRenderer;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Design language §5 live preview for the crosshair preset selector: the
 * currently-selected preset's actual shape, drawn by the SAME shape code
 * the in-game HUD uses ({@link CrosshairRenderer#drawShape} — one
 * implementation, two call sites, so the preview can never drift from
 * what renders in-game). Placed leading (before the Style enum it
 * demonstrates, the spec's default) and updates live because the shape
 * is re-read from the config every frame, exactly like every other
 * settings row.
 *
 * <p>Parameters: size, thickness and gap follow the current config, so
 * adjusting a slider shows the same geometry as the HUD. Color is
 * the theme's {@code ON_BACKGROUND} token so the shape stays legible on
 * the inset panel in both modes rather than inheriting a user color
 * that could vanish against it. The {@code CUSTOM} preset previews the
 * user's real canvas (the pixel editor's own live view remains the
 * interactive path for editing it).
 *
 * <p>Non-interactive (never consumes clicks) and drawn in the live
 * overlay layer — the selected preset changes must reflect immediately,
 * which the owning screen's cached shape layer cannot promise.
 *
 * <p>Owner ruling 2026-10-30 (evening): the preview is a SMALL SQUARE
 * display box (was a well spanning the window's width) — same treatment
 * for the main preset and the entity-in-reach indicator selector, whose
 * preview is new (the long-noted §5 follow-up).
 */
public class CrosshairPreviewSetting extends FeatureSetting {

    private static final int BOX = 36;
    private static final int PAD_Y = 4;
    private static final int PREVIEW_H = BOX + PAD_Y * 2;

    /** True = preview the entity-in-reach indicator's shape fields. */
    private final boolean indicator;

    public CrosshairPreviewSetting(String label) {
        this(label, false);
    }

    public CrosshairPreviewSetting(String label, boolean indicator) {
        super(label);
        this.indicator = indicator;
    }

    @Override public int baseHeight() { return PREVIEW_H; }
    @Override public int height() { return PREVIEW_H; }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        AuroraConfig cfg = AuroraConfig.get();
        // Inset well — the pixel-canvas editor's backdrop treatment, marking
        // this as a viewport rather than a control. A centered SQUARE: the
        // specimen display box (owner ruling 2026-10-30).
        int bx = x + (width - BOX) / 2;
        int by = y + PAD_Y;
        // One rounded inset tone, no outline: the well reads by tone alone and follows the
        // theme's corner style like every other rectangular chrome (Spxctra 2026-10-30).
        com.aurora.client.ui.util.RenderUtil.drawRoundedRectAA(ctx, bx, by, BOX, BOX, AuroraTheme.RADIUS_SMALL,
                com.aurora.client.ui.component.MaterialSurface.color(ThemeToken.SURFACE_INSET));

        int cx = bx + BOX / 2;
        int cy = by + BOX / 2;

        // The caller-owned half-pixel translate, same as the HUD path —
        // the shape code's centering contract.
        ctx.enableScissor(bx, by, bx + BOX, by + BOX);
        ctx.pose().pushMatrix();
        ctx.pose().translate(-0.5f, -0.5f);
        if (indicator) {
            CrosshairRenderer.drawShape(ctx, cfg.crosshairIndicatorStyle, cx, cy,
                    Math.max(1, cfg.crosshairIndicatorSize), Math.max(1, cfg.crosshairIndicatorThickness),
                    Math.max(0, cfg.crosshairIndicatorGap),
                    ThemeManager.color(ThemeToken.ON_BACKGROUND),
                    cfg.crosshairCustomPixels, cfg.crosshairCustomWidth, cfg.crosshairCustomHeight);
        } else {
            CrosshairRenderer.drawShape(ctx, cfg.crosshairStyle, cx, cy,
                    Math.max(1, cfg.crosshairSize), Math.max(1, cfg.crosshairThickness), Math.max(0, cfg.crosshairGap),
                    ThemeManager.color(ThemeToken.ON_BACKGROUND),
                    cfg.crosshairCustomPixels, cfg.crosshairCustomWidth, cfg.crosshairCustomHeight);
        }
        ctx.pose().popMatrix();
        ctx.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int rowX, int rowY, int rowWidth) {
        // Non-interactive preview: never consume.
        return false;
    }
}
