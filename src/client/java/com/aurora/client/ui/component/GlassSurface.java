package com.aurora.client.ui.component;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The one shared implementation of Aurora's glass-surface idiom — the
 * material every glass window, panel, row, button, chip and search field
 * is made of. Centralizes the integration conventions in AGENTS.md §6 /
 * ARCHITECTURE.md §3 (it does not change them):
 *
 * <ol>
 *   <li><b>Live-world gate.</b> Glass needs a valid world in the main render
 *       target; with no level loaded every entry point declines up front
 *       (the same test {@code liveWorldBackdrop()} makes at the screens).</li>
 *   <li><b>Blur pass</b> — {@link BlurPanelRenderer#renderPanel} with the
 *       default blur radius and the lighting preset the surface's ROLE
 *       dictates: containers are DEPRESSED (recessed), controls are RAISED.
 *       Callers never pick a {@code Lighting} themselves.</li>
 *   <li><b>Tint fill</b> — neutral surfaces tint with {@code WINDOW_FILL},
 *       whose alpha IS the theme's Background Opacity (the single opacity
 *       application point; nothing here multiplies a second factor);
 *       stained surfaces tint with {@link ThemeManager#stainedTint()}
 *       (accent RGB, same opacity discipline plus its fixed visibility
 *       floor). Stained is ONLY for selected/primary elements — never a
 *       whole-row stain on a container-like row.</li>
 *   <li><b>Rim finish</b> — {@link BlurPanelRenderer#drawRimFinish} right
 *       after the tint, same rect and radius. The glass rim REPLACES the
 *       flat outline: a caller that gets {@code true} back must not draw
 *       its border on top (no double outline).</li>
 * </ol>
 *
 * <p><b>Return value / fallback contract.</b> Every entry point returns
 * whether glass actually drew. On {@code false} NOTHING has been painted
 * and the caller draws its complete flat look (token fill + outline, or
 * whatever that screen's flat chrome is — fallback chrome legitimately
 * differs per screen, so it is deliberately not part of this helper).
 * Decline reasons are the renderer's: no world, Transparent glass style,
 * screenshot interlock, tiny rect, session latch, pool exhaustion.
 *
 * <p><b>Overlays on glass.</b> Hover/focus never change the tint; callers
 * that want a hover wash or focus hairline draw it AFTER a {@code true}
 * return, on top of the finished surface (see {@code AuroraScreen} tiles,
 * {@code EditBoxMixin}).
 *
 * <p><b>What this helper does NOT enforce</b> (still conventions at the
 * call site): the pre-dim layering rule — glass must be painted BEFORE the
 * screen's {@code OVERLAY_DIM} fill, which is a matter of where the caller
 * places the call — and the {@code renderBackground} world-gate that skips
 * vanilla's backdrop sandwich when a level is loaded.
 *
 * <p>Status: R1 pilot (2026-09-05). Piloted on {@code WaypointManagerScreen}
 * only; the remaining ~9 copies of the idiom are unchanged until this
 * signature is approved and the rollout is extended deliberately.
 */
public final class GlassSurface {

    private GlassSurface() {}

    /**
     * DEPRESSED neutral glass — windows, main panels, and rows that read as
     * containers (e.g. the Profiles list). Tint {@code WINDOW_FILL}.
     *
     * @return whether glass drew; {@code false} ⇒ caller paints its flat look
     */
    public static boolean container(GuiGraphics g, float x, float y, float w, float h, float radius) {
        return paint(g, x, y, w, h, radius, BlurPanelRenderer.Lighting.depressed(),
                ThemeManager.color(ThemeToken.WINDOW_FILL));
    }

    /**
     * DEPRESSED glass with a surface-token tint: RGB from {@code surface},
     * alpha from {@code WINDOW_FILL} (via {@link ThemeManager#surfaceColor}),
     * so the single opacity application point holds structurally. Covers
     * containers that historically tinted {@code SURFACE} rather than
     * {@code WINDOW_FILL} (the pack browser's sidebar and modal).
     *
     * @return whether glass drew; {@code false} ⇒ caller paints its flat look
     */
    public static boolean container(GuiGraphics g, float x, float y, float w, float h, float radius,
                                    ThemeToken surface) {
        return paint(g, x, y, w, h, radius, BlurPanelRenderer.Lighting.depressed(),
                ThemeManager.surfaceColor(surface));
    }

    /**
     * RAISED neutral glass — buttons, chips, segments, search fields, cards,
     * and rows that read as controls (e.g. the Waypoints list). Tint
     * {@code WINDOW_FILL}.
     *
     * @return whether glass drew; {@code false} ⇒ caller paints its flat look
     */
    public static boolean control(GuiGraphics g, float x, float y, float w, float h, float radius) {
        return paint(g, x, y, w, h, radius, BlurPanelRenderer.Lighting.raised(),
                ThemeManager.color(ThemeToken.WINDOW_FILL));
    }

    /**
     * RAISED accent-STAINED glass — selected/primary elements only (selected
     * segment, enabled tile, primary button, listening keybind pill). Tint
     * {@link ThemeManager#stainedTint()}. Content drawn on it takes
     * {@code ON_ACCENT}, never the accent itself.
     *
     * @return whether glass drew; {@code false} ⇒ caller paints its flat look
     */
    public static boolean stainedControl(GuiGraphics g, float x, float y, float w, float h, float radius) {
        return paint(g, x, y, w, h, radius, BlurPanelRenderer.Lighting.raised(),
                ThemeManager.stainedTint());
    }

    /**
     * Convenience for the "selection reads through the tint" pattern:
     * {@link #stainedControl} when {@code stained}, else {@link #control}.
     * Both are RAISED — selection never changes the lighting orientation.
     *
     * @return whether glass drew; {@code false} ⇒ caller paints its flat look
     */
    public static boolean control(GuiGraphics g, float x, float y, float w, float h, float radius,
                                  boolean stained) {
        return stained ? stainedControl(g, x, y, w, h, radius) : control(g, x, y, w, h, radius);
    }

    /**
     * True when glass can engage at all this frame: a level is loaded, so
     * the main render target holds a live world for the capture. Same test
     * the screens' {@code liveWorldBackdrop()} copies make; the renderer
     * re-checks it and declines regardless, so gating here changes no
     * pixels — it makes the convention explicit at the one place the
     * material is painted.
     */
    public static boolean liveWorldBackdrop() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null;
    }

    /** The idiom itself, in its one fixed order: gate → blur → tint → rim. */
    private static boolean paint(GuiGraphics g, float x, float y, float w, float h, float radius,
                                 BlurPanelRenderer.Lighting lighting, int tint) {
        if (!liveWorldBackdrop()) return false;
        if (!BlurPanelRenderer.renderPanel(g, x, y, w, h, radius,
                BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX, lighting)) {
            return false;
        }
        RenderUtil.drawRoundedRectAA(g, x, y, w, h, radius, tint);
        BlurPanelRenderer.drawRimFinish(g, x, y, w, h, radius);
        return true;
    }
}
