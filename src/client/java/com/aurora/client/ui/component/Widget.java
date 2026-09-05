package com.aurora.client.ui.component;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Base class for Aurora's reusable, theme-aware widgets.
 *
 * <p>Every widget follows one render discipline: {@link #render} draws the
 * full widget; {@link #renderShapes} draws only the cacheable static
 * geometry (no hover/animation/value state) and {@link #renderOverlay}
 * draws the live layer. This mirrors {@code FeatureSetting}'s split so a
 * screen can rasterize a widget's static chrome into its
 * {@code UiLayerCache} once and re-blit it, invalidating via
 * {@link #shapeFingerprint()} + the theme generation stamp.
 *
 * <p>Construction API is uniform across all widgets: state accessors and
 * callbacks are supplied at construction, geometry via {@link #layout}
 * each frame, and interaction via the {@code mouse*}/{@code on*} hooks.
 */
public abstract class Widget {

    /** Layout box in logical (GUI-scaled) pixels, set each frame before render/hit-test. */
    protected float x, y, w, h;

    /** Position the widget for the current frame. */
    public final void layout(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return w; }
    public float getHeight() { return h; }

    /** Full render: static shapes, then the live overlay. */
    public void render(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        renderShapes(g, x, y, w, h);
        renderOverlay(g, x, y, w, h, mouseX, mouseY);
    }

    /** Cacheable static geometry (no hover/animation/value state). Default no-op. */
    public void renderShapes(GuiGraphics g, float x, float y, float w, float h) {
    }

    /**
     * Glass pass: paint this widget's glass SURFACE for the current frame —
     * called by a screen BEFORE its {@link GlassSurface#overlayDim} so the
     * dim veils the surface like it veils the world (the layering contract,
     * AGENTS.md §6). A widget that paints glass implements this, stamps the
     * frame ({@link GlassSurface#frame()}) and remembers whether the glass
     * drew, so its {@link #renderOverlay} (which runs after the dim) paints
     * only content on top — or the flat fallback when the glass declined.
     * A screen that never calls this gets the widget's legacy behavior:
     * surface painted in place during {@link #renderOverlay}. Default no-op.
     */
    public void renderGlassPass(GuiGraphics g, float x, float y, float w, float h) {
    }

    /** Live geometry: hover, animation, text. Default no-op. */
    public void renderOverlay(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
    }

    /** Stable fingerprint of the cacheable shape layer. Default 0 (theme generation stamp handles theme changes). */
    public int shapeFingerprint() {
        return 0;
    }

    public boolean mouseClicked(double mx, double my, int button) { return false; }
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) { return false; }
    public boolean mouseReleased(double mx, double my, int button) { return false; }
    public boolean onScroll(double vertical) { return false; }
    public boolean onKeyPress(int keyCode, int modifiers) { return false; }

    /** Axis-aligned hit test, exclusive of the far edges. */
    protected static boolean inBounds(double mx, double my, float bx, float by, float bw, float bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }
}
