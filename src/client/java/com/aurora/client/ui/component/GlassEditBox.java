package com.aurora.client.ui.component;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Glass-pass hook for the mod-wide themed {@code EditBox} — implemented
 * onto every vanilla {@code EditBox} by {@code EditBoxMixin}, so a screen
 * that keeps its glass surfaces under the overlay dim (see
 * {@link GlassSurface}) can paint a text field's raised-glass surface in
 * its glass pass and let the field's own render draw only content (text,
 * hint, caret, focus ring) afterwards:
 *
 * <pre>
 * ((GlassEditBox) nameField).aurora$renderGlassPass(g);   // before overlayDim
 * …
 * GlassSurface.overlayDim(g, width, height);
 * …
 * nameField.render(g, mouseX, mouseY, delta);             // content only
 * </pre>
 *
 * <p>The field must already be positioned ({@code setX}/{@code setY}) when
 * the pass runs — the surface is painted at its current bounds. On a
 * screen that never calls the pass the mixin paints the surface in place
 * during {@code render} exactly as before (the legacy, unguarded order).
 */
public interface GlassEditBox {

    /**
     * Paint this field's glass surface for the current frame (pre-dim).
     * Same eligibility as the in-place path: only on themed screens; the
     * renderer may decline, in which case the field's render draws its
     * flat fill instead.
     */
    void aurora$renderGlassPass(GuiGraphics g);
}
