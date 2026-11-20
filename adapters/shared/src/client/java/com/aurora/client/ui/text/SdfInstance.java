package com.aurora.client.ui.text;

import com.aurora.client.ui.util.SdfTextMath;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.network.chat.Style;
import org.joml.Matrix4fc;

abstract class SdfInstance implements TextRenderable.Styled {
    record Bounds(float left, float top, float right, float bottom) {}

    /** Ink bounds, padded quad and UVs are shared by every draw of a baked glyph. */
    record Geometry(SdfAtlas.Cell cell, Bounds ink, Bounds quad, float u0, float v0, float u1, float v1) {
        Geometry(SdfAtlas.Cell cell, Bounds ink, Bounds quad) {
            this(cell, ink, quad,
                    SdfTextMath.insetUv(cell.x(), false, SdfAtlas.SIZE),
                    SdfTextMath.insetUv(cell.y(), false, SdfAtlas.SIZE),
                    SdfTextMath.insetUv(cell.x() + cell.width(), true, SdfAtlas.SIZE),
                    SdfTextMath.insetUv(cell.y() + cell.height(), true, SdfAtlas.SIZE));
        }
    }

    final Geometry geometry;
    private final float penX, penY, advance, boldOffset, shadowOffset;
    private final Style style;
    private final int color, shadowColor;

    SdfInstance(Geometry geometry, float penX, float penY, int color, int shadowColor, Style style,
                float advance, float boldOffset, float shadowOffset) {
        this.geometry = geometry;
        this.penX = penX;
        this.penY = penY;
        this.color = color;
        this.shadowColor = shadowColor;
        this.style = style;
        this.advance = advance;
        this.boldOffset = boldOffset;
        this.shadowOffset = shadowOffset;
    }

    @Override public Style style() { return style; }
    @Override public float activeRight() { return penX + advance; }
    @Override public float left() {
        Bounds ink = geometry.ink();
        return penX + ink.left() + (style.isItalic() ? Math.min(shear(ink.top()), shear(ink.bottom())) : 0) - thickness();
    }
    @Override public float top() { return penY + geometry.ink().top() - thickness(); }
    @Override public float right() {
        Bounds ink = geometry.ink();
        return penX + ink.right() + (shadowColor != 0 ? shadowOffset : 0)
                + (style.isItalic() ? Math.max(shear(ink.top()), shear(ink.bottom())) : 0) + thickness();
    }
    @Override public float bottom() {
        return penY + geometry.ink().bottom() + (shadowColor != 0 ? shadowOffset : 0) + thickness();
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
        Bounds quad = geometry.quad();
        float topShift = style.isItalic() ? shear(quad.top()) : 0;
        float bottomShift = style.isItalic() ? shear(quad.bottom()) : 0;
        float x0 = x + quad.left(), x1 = x + quad.right(), y0 = y + quad.top(), y1 = y + quad.bottom();
        vertex(pose, out, light, argb, x0 + topShift, y0, geometry.u0(), geometry.v0());
        vertex(pose, out, light, argb, x0 + bottomShift, y1, geometry.u0(), geometry.v1());
        vertex(pose, out, light, argb, x1 + bottomShift, y1, geometry.u1(), geometry.v1());
        vertex(pose, out, light, argb, x1 + topShift, y0, geometry.u1(), geometry.v0());
    }

    private static void vertex(Matrix4fc pose, VertexConsumer out, int light, int argb, float x, float y, float u, float v) {
        out.addVertex(pose, x, y, 0).setColor(argb).setUv(u, v).setLight(light);
    }
}
