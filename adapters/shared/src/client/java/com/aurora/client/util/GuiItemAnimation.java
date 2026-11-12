package com.aurora.client.util;

/**
 * GUI Item Baking (ADR 0009). Added to {@code ItemStackRenderState} by
 * {@code ItemStackRenderStateAnimationMixin}: whether an animated item
 * changes every frame (enchantment glint, special models) or only when its
 * animated sprites advance, which {@link SpriteAnimationClock} counts.
 */
public interface GuiItemAnimation {
    /** True when the item must be redrawn every frame, as vanilla does for all animated items. */
    boolean aurora$everyFrame();

    /** Brackets the {@code setAnimated()} call made only because of an animated sprite. */
    void aurora$spriteAnimation(boolean inside);
}
