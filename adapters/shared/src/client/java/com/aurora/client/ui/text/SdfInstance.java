package com.aurora.client.ui.text;

import com.aurora.client.compat.EphemeraBridge;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.network.chat.Style;
import org.joml.Matrix4fc;

abstract class SdfInstance implements TextRenderable.Styled {
    final SdfAtlas.Cell cell;
    private final float penX, penY, advance, boldOffset, shadowOffset;
    private final float left, top, right, bottom, quadLeft, quadTop, quadRight, quadBottom;
    private final Style style;
    private final boolean bold, italic;
    private final int color, shadowColor;

    SdfInstance(SdfAtlas.Cell cell, float penX, float penY, int color, int shadowColor, Style style,
                float advance, float boldOffset, float shadowOffset, float left, float top, float right, float bottom,
                float quadLeft, float quadTop, float quadRight, float quadBottom) {
        this.cell = cell;
        this.penX = penX;
        this.penY = penY;
        this.color = color;
        this.shadowColor = shadowColor;
        this.style = style;
        this.bold = style.isBold();
        this.italic = style.isItalic();
        this.advance = advance;
        this.boldOffset = boldOffset;
        this.shadowOffset = shadowOffset;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.quadLeft = quadLeft;
        this.quadTop = quadTop;
        this.quadRight = quadRight;
        this.quadBottom = quadBottom;
    }

    @Override public Style style() { return style; }
    @Override public float activeRight() { return penX + advance; }
    @Override public float left() {
        return penX + left + (italic ? Math.min(shear(top), shear(bottom)) : 0) - thickness();
    }
    @Override public float top() { return penY + top - thickness(); }
    @Override public float right() {
        return penX + right + (shadowColor != 0 ? shadowOffset : 0)
                + (italic ? Math.max(shear(top), shear(bottom)) : 0) + thickness();
    }
    @Override public float bottom() {
        return penY + bottom + (shadowColor != 0 ? shadowOffset : 0) + thickness();
    }

    private static float shear(float height) { return 1f - 0.25f * height; }
    private float thickness() { return bold ? 0.1f : 0; }

    @Override public void render(Matrix4fc pose, VertexConsumer out, int light, boolean noDepth) {
        float u0 = cell.u0(), u1 = cell.u1(), v0 = cell.v0(), v1 = cell.v1();
        if (EphemeraBridge.writeSdfGlyph(out, pose, light, penX, penY,
                quadLeft, quadTop, quadRight, quadBottom, boldOffset, shadowOffset,
                color, shadowColor, bold, italic, u0, u1, v0, v1)) {
            return;
        }
        float topShift = italic ? shear(quadTop) : 0;
        float bottomShift = italic ? shear(quadBottom) : 0;
        if (shadowColor != 0) {
            quad(pose, out, light, penX + shadowOffset, penY + shadowOffset, shadowColor,
                    u0, u1, v0, v1, topShift, bottomShift);
            if (bold) quad(pose, out, light, penX + shadowOffset + boldOffset, penY + shadowOffset, shadowColor,
                    u0, u1, v0, v1, topShift, bottomShift);
        }
        quad(pose, out, light, penX, penY, color, u0, u1, v0, v1, topShift, bottomShift);
        if (bold) quad(pose, out, light, penX + boldOffset, penY, color, u0, u1, v0, v1, topShift, bottomShift);
    }

    private void quad(Matrix4fc pose, VertexConsumer out, int light, float x, float y, int argb,
                      float u0, float u1, float v0, float v1, float topShift, float bottomShift) {
        float x0 = x + quadLeft, x1 = x + quadRight, y0 = y + quadTop, y1 = y + quadBottom;
        vertex(pose, out, light, argb, x0 + topShift, y0, u0, v0);
        vertex(pose, out, light, argb, x0 + bottomShift, y1, u0, v1);
        vertex(pose, out, light, argb, x1 + bottomShift, y1, u1, v1);
        vertex(pose, out, light, argb, x1 + topShift, y0, u1, v0);
    }

    private static void vertex(Matrix4fc pose, VertexConsumer out, int light, int argb, float x, float y, float u, float v) {
        out.addVertex(pose, x, y, 0).setColor(argb).setUv(u, v).setLight(light);
    }
}
