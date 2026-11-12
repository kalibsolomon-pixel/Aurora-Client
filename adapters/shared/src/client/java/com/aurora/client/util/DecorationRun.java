package com.aurora.client.util;

import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;

/**
 * An underline or strikethrough being extended across consecutive
 * characters, for Merge Text Decorations ({@code FontDecorationMergeMixin}).
 * {@link #create} makes the one quad that replaces a character's worth each.
 */
public final class DecorationRun {
    public boolean active;
    public float x1;
    private EffectGlyph glyph;
    private float x0, y0, y1, depth, shadowOffset;
    private int color, shadowColor;

    public void start(EffectGlyph glyph, float x0, float y0, float x1, float y1, float depth,
                      int color, int shadowColor, float shadowOffset) {
        this.active = true;
        this.glyph = glyph;
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.depth = depth;
        this.color = color;
        this.shadowColor = shadowColor;
        this.shadowOffset = shadowOffset;
    }

    /** True when a quad starting at {@code x0} with these properties continues this run exactly. */
    public boolean continuesWith(EffectGlyph glyph, float x0, float y0, float y1, float depth,
                                 int color, int shadowColor, float shadowOffset) {
        return active && glyph == this.glyph && x0 == this.x1 && y0 == this.y0 && y1 == this.y1 && depth == this.depth
                && color == this.color && shadowColor == this.shadowColor && shadowOffset == this.shadowOffset;
    }

    /** The merged quad; the run is finished afterwards. */
    public TextRenderable create() {
        active = false;
        return glyph.createEffect(x0, y0, x1, y1, depth, color, shadowColor, shadowOffset);
    }
}
