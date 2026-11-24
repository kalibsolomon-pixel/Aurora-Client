package com.aurora.client.ui.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Supplied ribbon SVG, rasterized offline into power-of-two tiers with linear
 * filtering (.mcmeta) and RGB edge-bleed by tools/prepare_brand.py. No theme
 * tint or effect pass.
 */
public final class AuroraBrand {
    private static final int[] TIERS = {16, 32, 64, 128, 256, 512};
    private static final Identifier[] MARKS = {
        Identifier.fromNamespaceAndPath("aurora", "textures/ui/aurora_mark_16.png"),
        Identifier.fromNamespaceAndPath("aurora", "textures/ui/aurora_mark_32.png"),
        Identifier.fromNamespaceAndPath("aurora", "textures/ui/aurora_mark_64.png"),
        Identifier.fromNamespaceAndPath("aurora", "textures/ui/aurora_mark_128.png"),
        Identifier.fromNamespaceAndPath("aurora", "textures/ui/aurora_mark.png"),
        Identifier.fromNamespaceAndPath("aurora", "textures/ui/aurora_mark_512.png")
    };

    private AuroraBrand() {}

    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int size) {
        if (size <= 0) return;
        int tierIndex = selectTierIndex(size, guiScale());
        int texSize = TIERS[tierIndex];
        graphics.blit(RenderPipelines.GUI_TEXTURED, MARKS[tierIndex],
                x, y, 0f, 0f, size, size, texSize, texSize, texSize, texSize);
    }

    static int selectTierIndex(int logicalSize, int guiScale) {
        int physical = Math.max(1, logicalSize) * Math.max(1, guiScale);
        for (int i = 0; i < TIERS.length; i++) {
            if (TIERS[i] >= physical) return i;
        }
        return TIERS.length - 1;
    }

    private static int guiScale() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getWindow() != null ? Math.max(1, (int) mc.getWindow().getGuiScale()) : 2;
    }
}
