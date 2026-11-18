package com.aurora.client.ui.text;

import com.aurora.client.ui.util.SdfTextMath;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.network.chat.Style;
import org.joml.Matrix4fc;

abstract class SdfInstance implements TextRenderable.Styled {
    final SdfAtlas.Cell cell;
    private final float penX, penY, advance, boldOffset, shadowOffset;
    private final float left, top, right, bottom, quadLeft, quadTop, quadRight, quadBottom;
    private final Style style;
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
        return penX + left + (style.isItalic() ? Math.min(shear(top), shear(bottom)) : 0) - thickness();
    }
    @Override public float top() { return penY + top - thickness(); }
    @Override public float right() {
        return penX + right + (shadowColor != 0 ? shadowOffset : 0)
                + (style.isItalic() ? Math.max(shear(top), shear(bottom)) : 0) + thickness();
    }
    @Override public float bottom() {
        return penY + bottom + (shadowColor != 0 ? shadowOffset : 0) + thickness();
    }

    private static float shear(float height) { return 1f - 0.25f * height; }
    private float thickness() { return style.isBold() ? 0.1f : 0; }

    @Override public void render(Matrix4fc pose, VertexConsumer out, int light, boolean noDepth) {
        if (shadowColor != 0) {
            quad(pose, out, light, penX + shadowOffset, penY + shadowOffset, shadowColor);
            if (style.isBold()) quad(pose, out, light, penX + shadowOffset + boldOffset, penY + shadowOffset, shadowColor);
        }
        quad(pose, out, light, penX, penY, color);
        if (style.isBold()) quad(pose, out, light, penX + boldOffset, penY, color);
    }

    private void quad(Matrix4fc pose, VertexConsumer out, int light, float x, float y, int argb) {
        float u0 = SdfTextMath.insetUv(cell.x(), false, SdfAtlas.SIZE);
        float u1 = SdfTextMath.insetUv(cell.x() + cell.width(), true, SdfAtlas.SIZE);
        float v0 = SdfTextMath.insetUv(cell.y(), false, SdfAtlas.SIZE);
        float v1 = SdfTextMath.insetUv(cell.y() + cell.height(), true, SdfAtlas.SIZE);
        float topShift = style.isItalic() ? shear(quadTop) : 0;
        float bottomShift = style.isItalic() ? shear(quadBottom) : 0;
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
