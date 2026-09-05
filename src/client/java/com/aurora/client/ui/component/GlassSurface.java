package com.aurora.client.ui.component;

import com.aurora.client.AuroraClient;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.util.RenderUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashSet;
import java.util.Set;

/**
 * The one shared implementation of Aurora's glass-surface idiom — the
 * material every glass window, panel, row, button, chip and search field
 * is made of — plus the screen-level ordering guard that keeps every
 * glass surface UNDER the screen's overlay dim. Centralizes the
 * integration conventions in AGENTS.md §6 / ARCHITECTURE.md §3 (it does
 * not change them):
 *
 * <ol>
 *   <li><b>Live-world gate.</b> Glass needs a valid world in the main render
 *       target; with no level loaded every entry point declines up front
 *       (the same test {@link #liveWorldBackdrop()} makes for the screens'
 *       {@code renderBackground} overrides).</li>
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
 * return, on top of the finished surface — and, under the layering rule
 * below, after the dim (they are content, not surface).
 *
 * <h2>The layering rule, made structural</h2>
 *
 * <p>Every glass surface — containers AND controls — is painted in the
 * screen's <b>glass pass</b>, BEFORE the screen's {@code OVERLAY_DIM}
 * fill, so the dim veils the glass exactly as it veils the world behind
 * it. Content (text, icons, badges, hover washes, focus rings, carets)
 * is painted after the dim. Screens paint their dim through
 * {@link #overlayDim}, which stamps the current frame; any glass surface
 * painted later in that same frame is an ordering violation and is
 * reported through {@link #reportPostDimGlass} — an ERROR with a stack
 * trace in a development environment, a one-shot WARN per screen class
 * otherwise. The surface still paints (a mis-ordered screen keeps its
 * glass; it does not silently lose it), but the defect is visible in the
 * log on the very first frame instead of shipping as a subtle shade
 * difference (the audit's B4). Screens that still fill their dim with a
 * raw {@code GuiGraphics.fill} are unguarded — migrating a screen means
 * replacing that fill with {@link #overlayDim} and moving its controls'
 * surfaces into the glass pass ({@link Widget#renderGlassPass},
 * {@link GlassEditBox#aurora$renderGlassPass}).
 *
 * <p>Rollout status (2026-09-05): the guarded discipline is live on
 * {@code ProfileManagerScreen} and {@code WaypointManagerScreen} only.
 * {@code AuroraScreen}, {@code FeatureDetailScreen}, the pack browser and
 * the setting widgets still paint controls after their raw dim fill and
 * are pending a separate, explicitly approved follow-up.
 */
public final class GlassSurface {

    private GlassSurface() {}

    // ------------------------------------------------------------------
    //  Frame phase: glass pass → dim → content
    // ------------------------------------------------------------------

    /** Frame epoch — bumped once per frame from the render-tick mixin. */
    private static long frame = 0L;
    /** Frame in which {@link #overlayDim} last painted (-1 = never). */
    private static long dimFrame = -1L;
    /** Screen classes already reported for post-dim glass this session. */
    private static final Set<String> reportedPostDim = new HashSet<>();

    /**
     * Frame boundary. Called from {@code MinecraftClientRenderMixin} right
     * after {@link BlurPanelRenderer#beginFrame()}, once per frame, before
     * any GUI rendering. Resets the dim stamp so each frame starts in the
     * glass pass.
     */
    public static void beginFrame() {
        frame++;
    }

    /**
     * Current frame epoch. Widgets that split their painting into a glass
     * pass and a content pass stamp their pass with this so the content
     * pass can tell "my surface was painted pre-dim this frame" from "no
     * glass pass ran — paint in place" (the legacy behavior screens not yet
     * on the discipline still rely on).
     */
    public static long frame() {
        return frame;
    }

    /**
     * The screen's overlay dim — the ONE way a glass screen fills its
     * {@code OVERLAY_DIM}. Marks the end of this frame's glass pass: every
     * glass surface must already be painted; anything painted afterwards
     * is reported as an ordering violation (see class javadoc).
     */
    public static void overlayDim(GuiGraphics g, int width, int height) {
        overlayDim(g, width, height, ThemeManager.color(ThemeToken.OVERLAY_DIM));
    }

    /**
     * {@link #overlayDim(GuiGraphics, int, int)} with an explicit color, for
     * screens whose dim is a derived strength of the {@code OVERLAY_DIM}
     * token (e.g. the pack browser's {@code withAlpha(…, 0x55)}).
     */
    public static void overlayDim(GuiGraphics g, int width, int height, int argb) {
        g.fill(0, 0, width, height, argb);
        dimFrame = frame;
    }

    /** True once this frame's dim has been painted (the glass pass is over). */
    public static boolean dimPainted() {
        return dimFrame == frame;
    }

    /**
     * Ordering-violation report: a glass surface is being painted after this
     * frame's {@link #overlayDim}. Development environment: ERROR with a
     * stack trace (so the offending call site is one log line away);
     * otherwise a WARN. Either way once per screen class per session — the
     * violation repeats every frame and the first one is the evidence.
     */
    private static void reportPostDimGlass() {
        Minecraft mc = Minecraft.getInstance();
        String screen = mc != null && mc.screen != null ? mc.screen.getClass().getName() : "<no screen>";
        if (!reportedPostDim.add(screen)) return;
        String msg = "[GlassSurface] glass painted AFTER the overlay dim on " + screen
                + " — every glass surface belongs in the glass pass, before GlassSurface.overlayDim"
                + " (AGENTS.md §6, layering contract)";
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            AuroraClient.LOGGER.error(msg, new IllegalStateException("post-dim glass"));
        } else {
            AuroraClient.LOGGER.warn(msg);
        }
    }

    // ------------------------------------------------------------------
    //  Surfaces
    // ------------------------------------------------------------------

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
     * the main render target holds a live world for the capture. The same
     * test gates every surface here, and it is the test a glass screen's
     * {@code renderBackground} override makes to skip vanilla's backdrop
     * sandwich (the glass must sample the LIVE world; with no level the
     * renderer declines anyway and the flat look wants the vanilla
     * backdrop as before).
     */
    public static boolean liveWorldBackdrop() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null;
    }

    /** The idiom itself, in its one fixed order: gate → blur → tint → rim. */
    private static boolean paint(GuiGraphics g, float x, float y, float w, float h, float radius,
                                 BlurPanelRenderer.Lighting lighting, int tint) {
        if (!liveWorldBackdrop()) return false;
        if (dimPainted()) reportPostDimGlass();
        if (!BlurPanelRenderer.renderPanel(g, x, y, w, h, radius,
                BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX, lighting)) {
            return false;
        }
        RenderUtil.drawRoundedRectAA(g, x, y, w, h, radius, tint);
        BlurPanelRenderer.drawRimFinish(g, x, y, w, h, radius);
        return true;
    }
}
