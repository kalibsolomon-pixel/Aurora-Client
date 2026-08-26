package com.aurora.client.ui.component;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Themed rounded panel / container — a window or card surface rendered
 * through the mod's high-res AA engine. Fully static, so it lives in the
 * cached shape layer (invalidated by the theme generation stamp + the
 * roundness token it reads).
 *
 * <p>Fill and outline come from the {@code WINDOW_FILL} / {@code WINDOW_OUTLINE}
 * tokens; the corner radius follows the theme's roundness family. The
 * optional drop glow is a stack of black AA outline rings (a structural
 * neutral — correct in both modes).
 */
public class RoundedPanel extends Widget {

    private final boolean glow;
    private final ThemeToken fillToken;

    public RoundedPanel() {
        this(false, ThemeToken.WINDOW_FILL);
    }

    public RoundedPanel(boolean glow) {
        this(glow, ThemeToken.WINDOW_FILL);
    }

    /** @param fillToken the surface token for the panel body (e.g. WINDOW_FILL for window chrome, SURFACE for a card). */
    public RoundedPanel(boolean glow, ThemeToken fillToken) {
        this.glow = glow;
        this.fillToken = fillToken;
    }

    @Override
    public void renderShapes(GuiGraphics g, float x, float y, float w, float h) {
        renderShapes(g, x, y, w, h, false);
    }

    /**
     * Glass-pilot variant: when {@code glassActive} the fill and outline are
     * omitted — the glass material draws the surface live on the GPU and
     * supplies its own directional rim, so stacking this outline on top
     * would read as a double border. The optional glow rings are still
     * drawn (they read as a drop shadow around the panel, not an outline).
     */
    public void renderShapes(GuiGraphics g, float x, float y, float w, float h, boolean glassActive) {
        float radius = ThemeManager.current().roundness().radius();

        if (glow) {
            for (int i = 1; i <= 8; i++) {
                int a = Math.max(0, 40 - i * 5);
                RenderUtil.drawRoundedOutlineAA(g, x - i, y - i, w + i * 2f, h + i * 2f,
                        radius + i, 1.0f, (a << 24) | 0x000000);
            }
        }

        if (!glassActive) {
            RenderUtil.drawRoundedRectAA(g, x, y, w, h, radius, ThemeManager.color(fillToken));
            RenderUtil.drawRoundedOutlineAA(g, x, y, w, h, radius, 1.0f, ThemeManager.color(ThemeToken.WINDOW_OUTLINE));
        }
    }
}
