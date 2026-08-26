package com.aurora.client.hud;

import com.aurora.client.AuroraClient;
import com.aurora.client.hud.module.HudModuleManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;

/**
 * Top-level HUD layer. Delegates to HudModuleManager for draggable modules,
 * CrosshairRenderer for the custom crosshair, and SaturationBarRenderer for
 * the hunger saturation overlay.
 */
public class HudRenderer {
    private final CrosshairRenderer crosshair = new CrosshairRenderer();

    public void render(GuiGraphics ctx, DeltaTracker tickCounter) {
        com.aurora.client.ui.util.AuroraFontRenderer.setRenderingAuroraUI(true);
        try {
            HudModuleManager mgr = AuroraClient.modules();
            if (mgr != null) mgr.renderAll(ctx);
            crosshair.render(ctx);
        } finally {
            com.aurora.client.ui.util.AuroraFontRenderer.setRenderingAuroraUI(false);
        }
    }
}