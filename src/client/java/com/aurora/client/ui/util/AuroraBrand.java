package com.aurora.client.ui.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Supplied ribbon SVG, aspect-fitted offline by tools/prepare_brand.py. No theme tint or effect pass. */
public final class AuroraBrand {
    private static final Identifier MARK = Identifier.fromNamespaceAndPath("aurora", "textures/ui/aurora_mark.png");
    private AuroraBrand() {}
    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int size) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, MARK, x, y, 0f, 0f, size, size, 256, 256, 256, 256);
    }
}
