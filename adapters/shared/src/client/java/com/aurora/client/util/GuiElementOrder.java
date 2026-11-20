package com.aurora.client.util;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

import java.util.Comparator;

/**
 * GUI Layering (ADR 0009): {@code GuiRenderer}'s element order, written out.
 * Vanilla chains key comparators that box every key and rehash each texture
 * setup on every comparison; this compares the same keys in the same order
 * (scissor nulls first by top, bottom, left, right; pipeline sort key;
 * texture sort key nulls first) and skips a key two elements share.
 */
public final class GuiElementOrder implements Comparator<GuiElementRenderState> {
    public static final GuiElementOrder INSTANCE = new GuiElementOrder();

    private GuiElementOrder() {}

    @Override public int compare(GuiElementRenderState a, GuiElementRenderState b) {
        ScreenRectangle sa = a.scissorArea(), sb = b.scissorArea();
        if (sa != sb) {
            if (sa == null) return -1;
            if (sb == null) return 1;
            int c = Integer.compare(sa.top(), sb.top());
            if (c == 0) c = Integer.compare(sa.bottom(), sb.bottom());
            if (c == 0) c = Integer.compare(sa.left(), sb.left());
            if (c == 0) c = Integer.compare(sa.right(), sb.right());
            if (c != 0) return c;
        }
        RenderPipeline pa = a.pipeline(), pb = b.pipeline();
        if (pa != pb) {
            int c = Integer.compare(pa.getSortKey(), pb.getSortKey());
            if (c != 0) return c;
        }
        TextureSetup ta = a.textureSetup(), tb = b.textureSetup();
        if (ta == tb) return 0;
        if (ta == null) return -1;
        if (tb == null) return 1;
        return Integer.compare(ta.getSortKey(), tb.getSortKey());
    }
}
