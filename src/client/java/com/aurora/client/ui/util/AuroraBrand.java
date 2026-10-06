package com.aurora.client.ui.util;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Supplied white mark, cropped and padded once offline. No theme tint or effect pass. */
public final class AuroraBrand {
    private static final Identifier MARK = Identifier.fromNamespaceAndPath("aurora", "textures/ui/aurora_mark.png");
    private AuroraBrand() {}
    public static void draw(GuiGraphics graphics, int x, int y, int size) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, MARK, x, y, 0f, 0f, size, size, 256, 256, 256, 256);
    }
}
