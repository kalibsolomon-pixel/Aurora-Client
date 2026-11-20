package com.aurora.client.util;

/**
 * GUI Glint Baking (ADR 0009). Added to {@code ItemStackRenderState} by
 * {@code ItemStackRenderStateGlintMixin}: set when the item has a standard
 * enchantment glint, which then does not mark it animated.
 * {@code GuiItemAtlasGlintMixin} redraws a glint-only item when the glint
 * moves.
 */
public interface GuiItemGlint {
    /** The item has a standard glint that did not mark it animated. */
    boolean aurora$glint();

    /** The glint is the item's only animation. */
    boolean aurora$glintOnly();

    void aurora$markGlint();
}
