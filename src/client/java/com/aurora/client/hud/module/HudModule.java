package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.HudAnchor;
import com.aurora.client.util.HudBackgrounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

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
 * <p>Subclasses implement {@link #renderContent(GuiGraphics, Minecraft, int, int)}
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
        this.anchor  = HudAnchor.TOP_LEFT;
        this.offsetX = 4;
        this.offsetY = 4;
        this.scale   = 1.0f;
        this.enabled = true;
        this.locked  = false;
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

    protected abstract void renderContent(GuiGraphics ctx, Minecraft client, int x, int y);

    public boolean isConfigEnabled() { return true; }

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
    public void render(GuiGraphics ctx, Minecraft client, int screenW, int screenH) {
        if (!enabled) return;
        if (!isConfigEnabled()) return;

        int sw = getScaledWidth();
        int sh = getScaledHeight();
        int x = anchor.applyX(screenW, sw) + offsetX;
        int y = anchor.applyY(screenH, sh) + offsetY;
        float bgAlpha = backgroundAlpha();

        if (Math.abs(scale - 1.0f) < 0.001f) {
            // Fast path â€” no matrix push if scale is effectively 1.
            HudBackgrounds.draw(ctx, x, y, getWidth(), getHeight(), backgroundMode(), backgroundColor(), bgAlpha);
            renderContent(ctx, client, x, y);
            return;
        }

        // Scaled path: translate to (x, y), scale uniformly, then renderContent
        // draws into its native unscaled AABB at (0, 0) post-transform.
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(scale, scale);
        HudBackgrounds.draw(ctx, 0, 0, getWidth(), getHeight(), backgroundMode(), backgroundColor(), bgAlpha);
        renderContent(ctx, client, 0, 0);
        ctx.pose().popMatrix();
    }

    /** Snap (offsetX, offsetY) to nearest screen edge / 4px grid using SCALED dimensions. */
    public void snap(int screenW, int screenH) {
        int w = getScaledWidth();
        int h = getScaledHeight();
        int x = anchor.applyX(screenW, w) + offsetX;
        int y = anchor.applyY(screenH, h) + offsetY;

        int threshold = 6;
        if (x < threshold) x = 0;
        if (y < threshold) y = 0;
        if (screenW - (x + w) < threshold) x = screenW - w;
        if (screenH - (y + h) < threshold) y = screenH - h;

        x = Math.round(x / 4f) * 4;
        y = Math.round(y / 4f) * 4;

        this.anchor = HudAnchor.nearest(x, y, w, h, screenW, screenH);
        this.offsetX = x - anchor.applyX(screenW, w);
        this.offsetY = y - anchor.applyY(screenH, h);
    }
}