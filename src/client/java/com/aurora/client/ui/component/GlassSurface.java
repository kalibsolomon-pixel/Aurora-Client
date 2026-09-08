package com.aurora.client.ui.component;

import com.aurora.client.AuroraClient;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.util.RenderUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
 *   <li><b>Rim finish</b> — {@link BlurPanelRenderer#drawRimFinish}, same
 *       rect and radius as the tint. The glass rim REPLACES the flat
 *       outline: a caller that gets {@code true} back must not draw its
 *       border on top (no double outline). WHEN it paints is the phase
 *       machinery's business (below): in place right after the tint, or —
 *       inside a declared glass pass — deferred to just after the dim so
 *       the highlight is not veiled with the body.</li>
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
 * is painted after the dim. A screen on this discipline brackets the
 * pass explicitly:
 *
 * <pre>
 * GlassSurface.beginGlassPass();          // opens the pass: rims are deferred from here
 * …surfaces (rows, buttons, fields)…       // via container/control/stainedControl
 * GlassSurface.overlayDim(g, w, h);        // closes the pass: dim fill, then the deferred rims
 * …content…
 * </pre>
 *
 * <p><b>Rim timing.</b> The rim finish is the above-fill half of the rim
 * (the half meant to stay visually prominent as opacity rises). Inside a
 * declared pass it is NOT painted with the body: each surface queues its
 * rim, and {@link #overlayDim} paints the queue right after the dim fill
 * — so the body (capture, blur, lighting, tint) is veiled like the world
 * while the highlight stays crisp above the dim. Surfaces painted with no
 * pass open (screens still on the legacy order, or a mis-ordered paint
 * after the dim) paint their rim in place exactly as before. Because the
 * flush happens outside whatever scissor the surface was painted under,
 * the pass tracks the clip: screens use {@link #enableScissor} /
 * {@link #disableScissor} (thin wrappers) around clipped surfaces, and
 * each queued rim re-applies the clip it was painted under.
 *
 * <p><b>Guards</b> — each reported as an ERROR with a stack trace in a
 * development environment, a one-shot WARN per screen class otherwise;
 * the surface always still paints (a mis-ordered screen keeps its glass,
 * it does not silently lose it):
 * <ul>
 *   <li>a surface BODY painted after this frame's {@link #overlayDim} —
 *       the ordering violation (the audit's B4);</li>
 *   <li>{@link #overlayDim} on a frame with no {@link #beginGlassPass} —
 *       a screen on the dim API but not on the pass API (its rims would be
 *       veiled with the body);</li>
 *   <li>{@link #beginGlassPass} never followed by {@link #overlayDim}
 *       (detected at the next frame boundary; the queued rims were painted
 *       nowhere);</li>
 *   <li>a surface queued under an untracked raw scissor (its deferred rim
 *       would escape the clip).</li>
 * </ul>
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
    //  Frame phase: glass pass → dim (+ deferred rims) → content
    // ------------------------------------------------------------------

    /** Frame epoch — bumped once per frame from the render-tick mixin. */
    private static long frame = 0L;
    /** Frame in which {@link #overlayDim} last painted (-1 = never). */
    private static long dimFrame = -1L;
    /** Frame in which {@link #beginGlassPass} was last called (-1 = never). */
    private static long passFrame = -1L;
    /** Screen classes already reported per guard this session. */
    private static final Set<String> reported = new HashSet<>();

    /** One deferred rim finish: the surface's rect + radius and the clip it was painted under. */
    private record PendingRim(float x, float y, float w, float h, float radius, int[] clip) {}

    /** Rims queued in the open glass pass, painted by {@link #overlayDim}. */
    private static final List<PendingRim> pendingRims = new ArrayList<>();
    /** Clip tracked through {@link #enableScissor}/{@link #disableScissor}; null = unclipped. */
    private static int[] trackedClip = null;

    /**
     * Frame boundary. Called from {@code MinecraftClientRenderMixin} right
     * after {@link BlurPanelRenderer#beginFrame()}, once per frame, before
     * any GUI rendering. Resets the phase so each frame starts before any
     * pass; a pass left open from the previous frame (rims queued, never
     * flushed) is reported here.
     */
    public static void beginFrame() {
        if (!pendingRims.isEmpty()) {
            report("glass pass opened (beginGlassPass) but overlayDim never painted — "
                    + pendingRims.size() + " deferred rim finish(es) were painted nowhere");
            pendingRims.clear();
        }
        trackedClip = null;
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
     * Opens this frame's glass pass. From here until {@link #overlayDim}
     * every surface's rim finish is deferred to just after the dim. Call it
     * at the top of {@code render}, before the first surface.
     */
    public static void beginGlassPass() {
        passFrame = frame;
        pendingRims.clear();
        trackedClip = null;
    }

    /** True while this frame's glass pass is open (rims are being deferred). */
    public static boolean passOpen() {
        return passFrame == frame && dimFrame != frame;
    }

    /**
     * Scissor wrapper for surfaces painted under a clip: forwards to
     * {@link GuiGraphics#enableScissor} and records the clip so a deferred
     * rim can re-apply it when it is finally painted after the dim. Use
     * this (not the raw call) around any glass surface inside the pass.
     */
    public static void enableScissor(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.enableScissor(x0, y0, x1, y1);
        trackedClip = new int[] {x0, y0, x1, y1};
    }

    /** Counterpart of {@link #enableScissor}. */
    public static void disableScissor(GuiGraphics g) {
        g.disableScissor();
        trackedClip = null;
    }

    /**
     * The screen's overlay dim — the ONE way a glass screen fills its
     * {@code OVERLAY_DIM}. Marks the end of this frame's glass pass: every
     * glass surface body must already be painted; anything painted
     * afterwards is reported as an ordering violation (see class javadoc).
     * Right after the fill, paints every rim finish deferred by the pass,
     * each under the clip its surface was painted with.
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
        if (passFrame != frame) {
            report("overlayDim painted with no beginGlassPass this frame — the screen's rim "
                    + "finishes are veiled with the surface bodies; open the pass at the top of render");
        }
        g.fill(0, 0, width, height, argb);
        dimFrame = frame;
        flushRims(g);
    }

    /** True once this frame's dim has been painted (the glass pass is over). */
    public static boolean dimPainted() {
        return dimFrame == frame;
    }

    /** Paints the deferred rims, each under its own recorded clip. */
    private static void flushRims(GuiGraphics g) {
        int[] active = null;
        for (PendingRim rim : pendingRims) {
            if (!sameClip(active, rim.clip)) {
                if (active != null) g.disableScissor();
                if (rim.clip != null) g.enableScissor(rim.clip[0], rim.clip[1], rim.clip[2], rim.clip[3]);
                active = rim.clip;
            }
            BlurPanelRenderer.drawRimFinish(g, rim.x, rim.y, rim.w, rim.h, rim.radius);
        }
        if (active != null) g.disableScissor();
        pendingRims.clear();
        trackedClip = null;
    }

    private static boolean sameClip(int[] a, int[] b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a[0] == b[0] && a[1] == b[1] && a[2] == b[2] && a[3] == b[3];
    }

    /**
     * Guard report: development environment → ERROR with a stack trace (so
     * the offending call site is one log line away); otherwise a WARN.
     * Either way once per (screen class, message) per session — the
     * violation repeats every frame and the first one is the evidence.
     */
    private static void report(String what) {
        Minecraft mc = Minecraft.getInstance();
        String screen = mc != null && mc.screen != null ? mc.screen.getClass().getName() : "<no screen>";
        if (!reported.add(screen + "|" + what)) return;
        String msg = "[GlassSurface] " + what + " on " + screen + " (AGENTS.md §6, layering contract)";
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            AuroraClient.LOGGER.error(msg, new IllegalStateException("glass layering"));
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
        return container(g, x, y, w, h, radius, BlurPanelRenderer.Priority.WINDOW);
    }

    /**
     * {@link #container(GuiGraphics, float, float, float, float, float)}
     * with an explicit degradation priority — for container-styled ROWS
     * (the Profiles list), which are repeated and must degrade before the
     * screen's real window under output-pool pressure
     * ({@link BlurPanelRenderer.Priority#ROW}).
     */
    public static boolean container(GuiGraphics g, float x, float y, float w, float h, float radius,
                                    BlurPanelRenderer.Priority priority) {
        return paint(g, x, y, w, h, radius, BlurPanelRenderer.Lighting.depressed(),
                ThemeManager.color(ThemeToken.WINDOW_FILL), priority);
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
                ThemeManager.surfaceColor(surface), BlurPanelRenderer.Priority.WINDOW);
    }

    /**
     * RAISED neutral glass — buttons, chips, segments, search fields, cards,
     * and rows that read as controls (e.g. the Waypoints list). Tint
     * {@code WINDOW_FILL}.
     *
     * @return whether glass drew; {@code false} ⇒ caller paints its flat look
     */
    public static boolean control(GuiGraphics g, float x, float y, float w, float h, float radius) {
        return control(g, x, y, w, h, radius, false, BlurPanelRenderer.Priority.CONTROL);
    }

    /**
     * {@link #control(GuiGraphics, float, float, float, float, float)} with
     * an explicit degradation priority — for control-styled ROWS (the
     * Waypoints list, {@link BlurPanelRenderer.Priority#ROW}).
     */
    public static boolean control(GuiGraphics g, float x, float y, float w, float h, float radius,
                                  BlurPanelRenderer.Priority priority) {
        return control(g, x, y, w, h, radius, false, priority);
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
        return control(g, x, y, w, h, radius, true, BlurPanelRenderer.Priority.CONTROL);
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
        return control(g, x, y, w, h, radius, stained, BlurPanelRenderer.Priority.CONTROL);
    }

    /**
     * The general control surface: neutral or stained, with an explicit
     * degradation priority (see {@link BlurPanelRenderer.Priority}). Every
     * other control entry point lands here. Repeated small controls inside
     * rows/cards pass {@link BlurPanelRenderer.Priority#DETAIL}.
     */
    public static boolean control(GuiGraphics g, float x, float y, float w, float h, float radius,
                                  boolean stained, BlurPanelRenderer.Priority priority) {
        return paint(g, x, y, w, h, radius, BlurPanelRenderer.Lighting.raised(),
                stained ? ThemeManager.stainedTint() : ThemeManager.color(ThemeToken.WINDOW_FILL),
                priority);
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

    /**
     * The idiom itself, in its one fixed order: gate → blur → tint → rim,
     * with the rim deferred past the dim while a glass pass is open.
     */
    private static boolean paint(GuiGraphics g, float x, float y, float w, float h, float radius,
                                 BlurPanelRenderer.Lighting lighting, int tint,
                                 BlurPanelRenderer.Priority priority) {
        if (!liveWorldBackdrop()) return false;
        if (dimPainted()) {
            report("glass surface body painted AFTER the overlay dim — every glass surface belongs "
                    + "in the glass pass, before GlassSurface.overlayDim");
        }
        if (!BlurPanelRenderer.renderPanel(g, x, y, w, h, radius,
                BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX, lighting, priority)) {
            return false;
        }
        RenderUtil.drawRoundedRectAA(g, x, y, w, h, radius, tint);
        if (passOpen()) {
            if (trackedClip == null && !cornersInScissor(g, x, y, w, h)) {
                report("glass surface painted under a raw scissor the pass is not tracking — use "
                        + "GlassSurface.enableScissor/disableScissor so its deferred rim keeps the clip");
            }
            pendingRims.add(new PendingRim(x, y, w, h, radius, trackedClip));
        } else {
            BlurPanelRenderer.drawRimFinish(g, x, y, w, h, radius);
        }
        return true;
    }

    /** Cheap probe for an untracked scissor: are the surface's corners inside the active clip? */
    private static boolean cornersInScissor(GuiGraphics g, float x, float y, float w, float h) {
        int x0 = (int) x, y0 = (int) y, x1 = (int) (x + w) - 1, y1 = (int) (y + h) - 1;
        return g.containsPointInScissor(x0, y0) && g.containsPointInScissor(x1, y0)
                && g.containsPointInScissor(x0, y1) && g.containsPointInScissor(x1, y1);
    }
}
