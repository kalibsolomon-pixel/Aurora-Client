package com.aurora.client.ui.component;

import com.aurora.client.ui.util.OverlayMotion;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class OverlayPose {
    private OverlayPose() {}

    public static boolean push(GuiGraphicsExtractor graphics, int x, int y, float progress) {
        if (progress >= 1) return false;
        graphics.pose().pushMatrix();
        float scale = OverlayMotion.scale(progress);
        graphics.pose().translate(x, y).scale(scale, scale).translate(-x, -y);
        return true;
    }
}
