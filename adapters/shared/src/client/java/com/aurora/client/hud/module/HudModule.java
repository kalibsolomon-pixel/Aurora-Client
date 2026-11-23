package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.HudAnchor;
import com.aurora.client.hud.HudGrowth;
import com.aurora.client.util.HudBackgrounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Base class for a draggable, positionable HUD overlay.
 *
 * <p>Each module owns:
 * <ul>
 *   <li>an {@link HudAnchor} â€” which corner/edge/center it snaps to</li>
 *   <li>{@code offsetX/Y} â€” pixel offset from that anchor's origin</li>
 *   <li>a {@link #getWidth()} / {@link #getHeight()} that describe its
 *       INTRINSIC (scale=1) AABB. Effective on-screen size is multiplied
 *       by {@link #scale}.</li>
 *   <li>{@code scale} â€” uniform render scale; applied via matrix push so
 *       all rendering inside {@link #renderContent} scales together.
 *       Bitmap font scales with bilinear sampling â€” slight pixelation at
 *       non-integer scales is expected.</li>
 *   <li>{@code enabled} â€” whether to render at all (editor right-click toggle)</li>
 *   <li>{@code locked}  â€” when true, HUD editor refuses to drag/resize the
 *       module. Persisted via the module-layout map.</li>
 * </ul>
 *
 * <p>Subclasses implement {@link #renderContent(GuiGraphicsExtractor, Minecraft, int, int)}
 * where (x,y) is the computed top-left of the module's UNSCALED AABB. The
 * matrix scale set up in {@link #render} multiplies that AABB up to its
 * effective on-screen size.
 *
 * <p>Subclasses override {@link #featureRegistryId} when their HUD-module
 * id differs from the corresponding {@link com.aurora.client.screen.FeatureRegistry}
 * entry id (e.g. module {@code "armor"} â†’ registry {@code "armor_hud"}).
 */
public abstract class HudModule {
    protected final String id;
    public HudAnchor anchor = HudAnchor.TOP_LEFT;
    public int offsetX = 4;
    public int offsetY = 4;
    public boolean enabled = true;
    public boolean locked  = false;
    public float scale = 1.0f;

    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 3.0f;

    protected HudModule(String id) {
        this.id = id;
    }

    /**
     * Reset this module's layout fields to their factory defaults. Called
     * by the HUD editor's "Reset Positions" button. The base
     * implementation restores the field initializers declared above;
     * subclasses whose defaults differ (e.g. those with parameterized
     * constructors that set per-instance offsets) override this.
     */
    public void resetLayoutToDefaults() {
        if (restoreReleaseLayout()) return;
        // The module's OWN default home, captured at registration (before any
        // saved layout is applied). Resetting every module to TOP_LEFT (4,4)
        // piled CPS, Ping, Reach, Totem Pops and Info onto one spot
        // (premium-design audit, 2026-10-19).
        this.anchor  = defaultAnchor != null ? defaultAnchor : HudAnchor.TOP_LEFT;
        this.offsetX = defaultAnchor != null ? defaultOffsetX : 4;
        this.offsetY = defaultAnchor != null ? defaultOffsetY : 4;
        this.scale   = 1.0f;
        this.enabled = true;
        this.locked  = false;
    }

    private HudAnchor defaultAnchor;
    private int defaultOffsetX, defaultOffsetY;

    /** Reset geometry only; hiding/locking a HUD must survive Reset Positions. */
    protected final boolean restoreReleaseLayout() {
        var layout = com.aurora.client.config.ReleaseDefaults.layout(id);
        if (layout == null) return false;
        anchor = layout.anchor;
        offsetX = layout.offsetX;
        offsetY = layout.offsetY;
        scale = layout.scale;
        return true;
    }

    /** Records the constructor-assigned layout as this module's reset target (first call wins). */
    public final void captureDefaultLayout() {
        if (defaultAnchor != null) return;
        defaultAnchor = anchor;
        defaultOffsetX = offsetX;
        defaultOffsetY = offsetY;
    }

    public final String id() { return id; }

    /**
     * The corresponding entry id in {@link com.aurora.client.screen.FeatureRegistry}
     * â€” used by the HUD editor for the X-button (toggle off) and
     * shift+left-click (open settings). Defaults to {@link #id}; subclasses
     * with mismatched ids override.
     */
    public String featureRegistryId() { return id; }

    public String displayName() {
        StringBuilder sb = new StringBuilder(id.length());
        boolean up = true;
        for (char c : id.toCharArray()) {
            if (c == '_') { sb.append(' '); up = true; }
            else if (up) { sb.append(Character.toUpperCase(c)); up = false; }
            else sb.append(c);
        }
        return sb.toString();
    }

    /** Intrinsic width at scale=1. */
    public abstract int getWidth();
    /** Intrinsic height at scale=1. */
    public abstract int getHeight();

    /** Effective on-screen width (intrinsic * scale, rounded). */
    public final int getScaledWidth()  { return Math.round(getWidth()  * scale); }
    public final int getScaledHeight() { return Math.round(getHeight() * scale); }

    protected abstract void renderContent(GuiGraphicsExtractor ctx, Minecraft client, int x, int y);

    /** How the module grows horizontally as its content changes; see {@link HudGrowth}. */
    protected HudGrowth growthX() { return HudGrowth.ANCHORED; }
    /** How the module grows vertically as its content changes; see {@link HudGrowth}. */
    protected HudGrowth growthY() { return HudGrowth.ANCHORED; }
    /** Nominal intrinsic width the anchor places when the module grows horizontally. */
    protected int referenceWidth() { return getWidth(); }
    /** Nominal intrinsic height the anchor places when the module grows vertically. */
    protected int referenceHeight() { return getHeight(); }

    /** Screen x of the module's box, {@code sw} wide (scaled). Every placement goes through here. */
    public final int screenX(int screenW, int sw) {
        HudGrowth g = growthX();
        if (g == HudGrowth.ANCHORED) return anchor.applyX(screenW, sw) + offsetX;
        int rw = Math.round(referenceWidth() * scale);
        return g.start(anchor.applyX(screenW, rw) + offsetX, rw, sw, screenW);
    }

    /** Screen y of the module's box, {@code sh} tall (scaled). */
    public final int screenY(int screenH, int sh) {
        HudGrowth g = growthY();
        if (g == HudGrowth.ANCHORED) return anchor.applyY(screenH, sh) + offsetY;
        int rh = Math.round(referenceHeight() * scale);
        return g.start(anchor.applyY(screenH, rh) + offsetY, rh, sh, screenH);
    }

    /** The screen size of the last {@link #render}, for content that lays out toward the centre. */
    protected int lastScreenW, lastScreenH;

    /**
     * Which side of the screen centre the reference box lies on horizontally at the last
     * render: -1 left, 1 right, 0 straddling it (or not yet rendered).
     */
    protected final int sideX() {
        if (lastScreenW <= 0) return 0;
        int rw = Math.round(referenceWidth() * scale);
        int ref = anchor.applyX(lastScreenW, rw) + offsetX;
        return HudGrowth.before(ref, rw, lastScreenW) ? -1 : HudGrowth.after(ref, lastScreenW) ? 1 : 0;
    }

    /** As {@link #sideX()}, vertically: -1 above the centre, 1 below. */
    protected final int sideY() {
        if (lastScreenH <= 0) return 0;
        int rh = Math.round(referenceHeight() * scale);
        int ref = anchor.applyY(lastScreenH, rh) + offsetY;
        return HudGrowth.before(ref, rh, lastScreenH) ? -1 : HudGrowth.after(ref, lastScreenH) ? 1 : 0;
    }

    /** Sets the offsets so a box of {@code sw}×{@code sh} (scaled) sits at ({@code x}, {@code y}). */
    public final void moveTo(int x, int y, int sw, int sh, int screenW, int screenH) {
        HudGrowth gx = growthX();
        if (gx == HudGrowth.ANCHORED) {
            offsetX = x - anchor.applyX(screenW, sw);
        } else {
            int rw = Math.round(referenceWidth() * scale);
            offsetX = gx.refStart(x, rw, sw, screenW) - anchor.applyX(screenW, rw);
        }
        HudGrowth gy = growthY();
        if (gy == HudGrowth.ANCHORED) {
            offsetY = y - anchor.applyY(screenH, sh);
        } else {
            int rh = Math.round(referenceHeight() * scale);
            offsetY = gy.refStart(y, rh, sh, screenH) - anchor.applyY(screenH, rh);
        }
    }

    public boolean isConfigEnabled() { return true; }

    /** True while the module draws nothing (e.g. no active effects): the HUD editor then names it
     * inside its frame, so the frame never reads as an empty selection. */
    public boolean showsNothing() { return false; }

    protected AuroraConfig.HudBackground backgroundMode() {
        return AuroraConfig.HudBackground.NONE;
    }

    protected int backgroundColor() { return 0x80000000; }

    /**
     * Per-frame alpha multiplier in [0, 1] applied to the module's
     * background only. Defaults to fully opaque. Subclasses override to
     * fade their panel in/out without touching their text/icon content
     * (see {@link com.aurora.client.hud.module.PotionModule} fading the
     * panel away after the last effect expires).
     */
    protected float backgroundAlpha() { return 1.0f; }

    /**
     * Public render entrypoint. Resolves the screen-space anchor position
     * (using SCALED width/height so the module sits flush against the
     * intended edge), pushes a matrix scale, and delegates to
     * {@link #renderContent}.
     */
    public void render(GuiGraphicsExtractor ctx, Minecraft client, int screenW, int screenH) {
        if (!enabled) return;
        if (!isConfigEnabled()) return;
        lastScreenW = screenW;
        lastScreenH = screenH;

        int w = getWidth();
        int h = getHeight();
        boolean unitScale = Math.abs(scale - 1.0f) < 0.001f;
        int sw = unitScale ? w : Math.round(w * scale);
        int sh = unitScale ? h : Math.round(h * scale);
        int x = screenX(screenW, sw);
        int y = screenY(screenH, sh);
        float bgAlpha = backgroundAlpha();

        if (unitScale) {
            // Fast path — no matrix push if scale is effectively 1.
            HudBackgrounds.draw(ctx, x, y, w, h, backgroundMode(), backgroundColor(), bgAlpha);
            renderContent(ctx, client, x, y);
            return;
        }

        // Scaled path: translate to (x, y), scale uniformly, then renderContent
        // draws into its native unscaled AABB at (0, 0) post-transform.
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(scale, scale);
        HudBackgrounds.draw(ctx, 0, 0, w, h, backgroundMode(), backgroundColor(), bgAlpha);
        renderContent(ctx, client, 0, 0);
        ctx.pose().popMatrix();
    }

    /** Snap (offsetX, offsetY) to nearest screen edge / 4px grid using SCALED dimensions. */
    public void snap(int screenW, int screenH) {
        int w = getScaledWidth();
        int h = getScaledHeight();
        int x = screenX(screenW, w);
        int y = screenY(screenH, h);

        int threshold = 6;
        if (x < threshold) x = 0;
        if (y < threshold) y = 0;
        if (screenW - (x + w) < threshold) x = screenW - w;
        if (screenH - (y + h) < threshold) y = screenH - h;

        x = Math.round(x / 4f) * 4;
        y = Math.round(y / 4f) * 4;

        this.anchor = HudAnchor.nearest(x, y, w, h, screenW, screenH);
        moveTo(x, y, w, h, screenW, screenH);
    }
}
