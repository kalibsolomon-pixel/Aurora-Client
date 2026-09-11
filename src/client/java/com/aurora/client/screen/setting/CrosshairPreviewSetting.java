package com.aurora.client.screen.setting;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.CrosshairRenderer;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.util.AuroraShapes;
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
 * <p>Parameters: the preview draws with fixed demo dimensions (size 7,
 * thickness 2, gap 2) at a stable size in the row — its job is the
 * SHAPE, the one thing no other control on the screen shows; Size /
 * Thickness / Gap sliders and the Color swatch already surface their own
 * values at a glance (§4's exclusion clause, applied to §5). Color is
 * the theme's {@code ON_BACKGROUND} token so the shape stays legible on
 * the inset panel in both modes rather than inheriting a user color
 * that could vanish against it. The {@code CUSTOM} preset previews the
 * user's real canvas (the pixel editor's own live view remains the
 * interactive path for editing it).
 *
 * <p>Non-interactive (never consumes clicks) and drawn in the live
 * overlay layer — the selected preset changes must reflect immediately,
 * which the owning screen's cached shape layer cannot promise.
 */
public class CrosshairPreviewSetting extends FeatureSetting {

    private static final int PREVIEW_H = 48;
    private static final int PAD_X = 12;
    private static final int PAD_Y = 5;

    /** Demo dimensions — sized so every preset (and CUSTOM at 3×7=21px) fits the box legibly. */
    private static final int DEMO_SIZE = 7;
    private static final int DEMO_THICK = 2;
    private static final int DEMO_GAP = 2;

    public CrosshairPreviewSetting(String label) {
        super(label);
    }

    @Override public int baseHeight() { return PREVIEW_H; }
    @Override public int height() { return PREVIEW_H; }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        // Inset well — the same backdrop treatment the pixel-canvas editor
        // uses for its drawing surface, marking this as a viewport rather
        // than a control.
        int bx = x + PAD_X;
        int by = y + PAD_Y;
        int bw = width - PAD_X * 2;
        int bh = PREVIEW_H - PAD_Y * 2;
        AuroraShapes.panel(ctx, bx, by, bw, bh, AuroraTheme.PANEL_INSET, 0);
        AuroraShapes.outline(ctx, bx, by, bw, bh, AuroraTheme.BORDER_OFF, 0);

        AuroraConfig cfg = AuroraConfig.get();
        int cx = x + width / 2;
        int cy = y + PREVIEW_H / 2;

        // The caller-owned half-pixel translate, same as the HUD path —
        // the shape code's centering contract.
        ctx.pose().pushMatrix();
        ctx.pose().translate(-0.5f, -0.5f);
        CrosshairRenderer.drawShape(ctx, cfg.crosshairStyle, cx, cy,
                DEMO_SIZE, DEMO_THICK, DEMO_GAP,
                ThemeManager.color(ThemeToken.ON_BACKGROUND),
                cfg.crosshairCustomPixels, cfg.crosshairCustomWidth, cfg.crosshairCustomHeight);
        ctx.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int rowX, int rowY, int rowWidth) {
        // Non-interactive preview: never consume.
        return false;
    }
}
