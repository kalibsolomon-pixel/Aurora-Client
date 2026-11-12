package com.aurora.client.ui.render.blur;

import com.aurora.client.AuroraClient;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.ui.util.RenderUtil;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Pooled, deferred glass through Minecraft's Blaze3D API on every graphics backend.
 * Captures run after the world/panorama, before GUI drawing. Each panel owns its
 * sampled sub-rect and output; the caller applies WINDOW_FILL opacity once.
 */
public final class BlurPanelRenderer {


    /**
     * Blur radius, in device (screen) pixels, for harnesses that do not read
     * the theme. Panels use the theme's Blur Radius ({@code GlassSurface}).
     */
    public static final float DEFAULT_BLUR_RADIUS_PX = 24f;


    public static final float LIGHT_DIR_X = 0.24253563f;
    public static final float LIGHT_DIR_Y = -0.9701425f;

    public static final class Lighting {
        /** Depressed (recessed panel) treatment — both terms inverted vs raised. */
        public final boolean depressed;
        /**
         * Border stroke strength, 0..1. 0.45 reads bold at a glance, per the
         * "too subtle is a real failure mode" lesson. The LOW-opacity paint
         * weight; at high Background Opacity the shader ramps the weight
         * toward full and the target color toward the accent pastel (see
         * the composite shader).
         */
        public final float edgeStrength;
        /** Face gradient strength, 0..1 — the panel brightens/darkens by up to ±this at the two corners. */
        public final float gradStrength;

        public Lighting(boolean depressed, float edgeStrength, float gradStrength) {
            this.depressed = depressed;
            this.edgeStrength = edgeStrength;
            this.gradStrength = gradStrength;
        }

        /** Bold default preset for raised surfaces (buttons). */
        public static Lighting raised() { return new Lighting(false, 0.45f, 0.10f); }

        /** Bold default preset for depressed surfaces (window panels). */
        public static Lighting depressed() { return new Lighting(true, 0.45f, 0.10f); }
    }

    public record CaptureSource(com.mojang.renderpearl.api.textures.GpuTextureView textureView, int width, int height) {}
    public enum Priority { WINDOW, CONTROL, ROW, DETAIL; static final int COUNT = values().length; }
    private static final float MIN_EFFECTIVE_RADIUS_PX = 1.5f;
    private static final long SCREENSHOT_SUPPRESS_MS = 400L;
    private static boolean permanentlyDisabled;
    private static volatile long suppressGlassUntilMs;
    private static volatile long menuBackdropEpoch = -1;
    private static final int OUTPUT_POOL = 64;
    private static final PanelOutput[] outputs = new PanelOutput[OUTPUT_POOL];
    private static final int[] demandLastFrame = new int[Priority.COUNT];
    private static final int[] demandThisFrame = new int[Priority.COUNT];
    private static final int[] claimedThisFrame = new int[Priority.COUNT];
    private static volatile long poolEpoch;
    private static long slotEpoch = -1;
    private static int claimsThisFrame, outputCursor;
    private static long claimsTotal, lastOutputCallMs;
    private static String lastOutcome = "not run";
    private static final class PanelOutput { final PortableBlurRenderer.Output portable = new PortableBlurRenderer.Output(); }


    static final class GlassStats {
        static final boolean ENABLED = Boolean.getBoolean("aurora.glassStats");
        static final int REPORT_EVERY_FRAMES = 60;

        // ---- current frame (reset at beginFrame) ----
        static int panels;          // pool claims (panels actually rendered)
        static int declines;        // Declined outcomes (any reason)
        static int poolExhausted;   // Declined("output pool exhausted")
        static long frameTotalNs;   // wall time inside renderPanel, all attempts
        static long submissionNs;   // CPU command submission after extraction

        // ---- per-tier pool accounting (claims / pool declines, per interval) ----
        static final int[] tierClaims = new int[Priority.COUNT];
        static final int[] tierPoolDeclines = new int[Priority.COUNT];

        // ---- reporting interval accumulators ----
        static int framesWithPanels, framesSinceReport, sumDeclines, sumPoolExhausted;
        static long sumPanels, maxPanels, sumFrameNs, maxFrameNs;
        static long sumSubmissionNs;
        static long lastPoolWarnMs;

        static void claim(Priority p) { panels++; tierClaims[p.ordinal()]++; }

        static void poolDecline(Priority p) { tierPoolDeclines[p.ordinal()]++; }

        static void decline(String reason) {
            declines++;
            if (reason != null && reason.contains("pool exhausted")) poolExhausted++;
        }

        static void panelDone(long totalNs) { frameTotalNs += totalNs; }

        /** Called from {@link #beginFrame()} — closes out the frame that just finished. */
        static void flushFrame() {
            if (panels > 0) {
                framesWithPanels++;
                sumPanels += panels;
                if (panels > maxPanels) maxPanels = panels;
                sumFrameNs += frameTotalNs;
                if (frameTotalNs > maxFrameNs) maxFrameNs = frameTotalNs;
                sumSubmissionNs += submissionNs;
            }
            sumDeclines += declines;
            sumPoolExhausted += poolExhausted;
            panels = declines = poolExhausted = 0;
            frameTotalNs = submissionNs = 0;
            if (++framesSinceReport >= REPORT_EVERY_FRAMES) report();
        }

        private static void report() {
            try {
                if (ENABLED && framesWithPanels > 0) {
                    double f = framesWithPanels;
                    // Per-tier claims/poolDeclines over the interval (W=window C=control R=row D=detail).
                    StringBuilder tiers = new StringBuilder();
                    for (Priority t : Priority.values()) {
                        tiers.append(t.name().charAt(0)).append('=')
                                .append(tierClaims[t.ordinal()]).append('/')
                                .append(tierPoolDeclines[t.ordinal()]).append(' ');
                    }
                    AuroraClient.LOGGER.info(String.format(
                            "[GlassStats] frames=%d panels/frame avg=%.1f max=%d | ms/frame total=%.2f max=%.2f"
                                    + " | ms/frame submission=%.2f | declines=%d poolExhausted=%d | tiers claims/poolDeclines %s",
                            framesWithPanels, sumPanels / f, (int) maxPanels,
                            sumFrameNs / 1e6 / f, maxFrameNs / 1e6,
                            sumSubmissionNs / 1e6 / f, sumDeclines, sumPoolExhausted, tiers.toString().trim()));
                }
            } finally {
                java.util.Arrays.fill(tierClaims, 0);
                java.util.Arrays.fill(tierPoolDeclines, 0);
                framesSinceReport = 0;
                framesWithPanels = 0;
                sumPanels = maxPanels = sumFrameNs = maxFrameNs = 0;
                sumSubmissionNs = 0;
                sumDeclines = sumPoolExhausted = 0;
            }
        }
    }

    private BlurPanelRenderer() {}

    public static String lastOutcome() { return lastOutcome; }

    public static void noteScreenshotGrab() {
        suppressGlassUntilMs = System.currentTimeMillis() + SCREENSHOT_SUPPRESS_MS;
    }

    public static long capturesSubmitted() {
        return claimsTotal;
    }

    public static void noteMenuBackdropDrawn() {
        menuBackdropEpoch = poolEpoch;
    }

    public static boolean menuBackdropValid() {
        return menuBackdropEpoch == poolEpoch;
    }

    public static boolean renderPanel(GuiGraphicsExtractor g, float guiX, float guiY, float guiW, float guiH,
                                      float cornerRadiusGui, float blurRadiusPx) {
        return renderPanel(g, guiX, guiY, guiW, guiH, cornerRadiusGui, blurRadiusPx, null);
    }

    /**
     * Full variant with the lighting impression. Pass {@code lighting == null}
     * for the plain blur+tint look, or a {@link Lighting} preset (e.g.
     * {@link Lighting#raised()}) for the directional gradient + border
     * stroke. Lighting is applied inside the existing composite pass —
     * no additional render pass, no additional texture.
     *
     * @param lighting lighting parameters, or null to disable lighting
     */
    public static boolean renderPanel(GuiGraphicsExtractor g, float guiX, float guiY, float guiW, float guiH,
                                      float cornerRadiusGui, float blurRadiusPx, Lighting lighting) {
        return renderPanel(g, guiX, guiY, guiW, guiH, cornerRadiusGui, blurRadiusPx, lighting,
                Priority.CONTROL, null);
    }

    /**
     * {@link #renderPanel(GuiGraphicsExtractor, float, float, float, float, float, float, Lighting)}
     * with an explicit degradation {@link Priority}. The lighting-only
     * variants default to {@link Priority#CONTROL}; windows/containers pass
     * {@link Priority#WINDOW}, repeated rows/tiles/cards {@link Priority#ROW},
     * and repeated small elements inside them {@link Priority#DETAIL}. Only
     * consulted on frames whose demand exceeds the output pool.
     */
    public static boolean renderPanel(GuiGraphicsExtractor g, float guiX, float guiY, float guiW, float guiH,
                                      float cornerRadiusGui, float blurRadiusPx, Lighting lighting,
                                      Priority priority) {
        return renderPanel(g, guiX, guiY, guiW, guiH, cornerRadiusGui, blurRadiusPx, lighting,
                priority, null);
    }

    /**
     * Draws the rim's high-opacity finish ON TOP of the caller's panel fill:
     * a DIRECTIONAL border stroke — brightest on the light-facing edge
     * (top, ~14° right), tapering around the perimeter to near-zero on the
     * far sides — in the current accent's pastel tone
     * ({@code ResolvedTheme#rimPastel()}), with alpha following Background
     * Opacity in the SAME direction (opacity²): negligible while the panel
     * is translucent (the in-glass light catch owns that end of the range),
     * rising to a SOLID, fully-opaque pastel stroke at 100% opacity. This
     * gives the tile its cohesive "flat and pastel" read at high opacity
     * while keeping the established directional glass character; it never
     * covers the interior.
     *
     * <p>Why above the fill: the glass texture (with its embedded rim) is
     * blitted BEFORE the caller's translucent fill, so at high opacity the
     * opaque fill occludes it completely — no in-texture rim can survive
     * there. This pass is the rim's above-fill half; call it right after
     * drawing the panel's tint fill, with the same rect and corner radius
     * the fill used. Like every rim consumer this is a lighting-term
     * finish driven by the one opacity value — NOT an opacity application
     * point (it changes no fill alpha).
     */
    public static void drawRimFinish(GuiGraphicsExtractor g, float x, float y, float w, float h, float cornerRadiusGui) {
        double opacity = ThemeManager.current().backgroundOpacity();
        int alpha = Math.round((float) (opacity * opacity) * 255f);
        if (alpha <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getTextureManager() == null) return;
        int scale = Math.max(1, (int) mc.getWindow().getGuiScale());
        int devX = Math.round(x * scale);
        int devY = Math.round(y * scale);
        int devW = Math.round(w * scale);
        int devH = Math.round(h * scale);
        if (devW < 8 || devH < 8) return;
        float devRadiusF = Math.min(cornerRadiusGui * scale, Math.min(devW, devH) / 2f - 1f);
        int devRadius = Math.max(0, Math.round(devRadiusF));

        RimMask mask = rimMask(devW, devH, devRadius, scale);
        if (mask == null) return;

        int pastel = ThemeManager.current().rimPastel();
        int tint = (alpha << 24) | (pastel & 0x00FFFFFF);
        // Device-space placement under the 1/scale pose — the exact pattern
        // RenderUtil's fills use — so the mask lands pixel-aligned with the
        // fill drawn beneath it.
        g.pose().pushMatrix();
        g.pose().scale(1f / scale, 1f / scale);
        g.blit(RenderPipelines.GUI_TEXTURED, mask.id, devX, devY, 0f, 0f,
                devW, devH, devW, devH, devW, devH, tint);
        g.pose().popMatrix();
    }

    /** Cached directional rim masks, keyed by device rect + radius. */
    private static final java.util.HashMap<Long, RimMask> rimMasks = new java.util.HashMap<>();
    /**
     * Cache cap. Enforced by evicting only masks NOT used in the current
     * frame, and even those are destroyed one frame later — see
     * {@link #evictColdRimMasks()} for why deleting a mask mid-frame is a
     * GL error and a session-ending one at that.
     */
    private static final int RIM_MASK_CACHE_CAP = 16;
    /**
     * Masks evicted from {@link #rimMasks} but not yet destroyed. Drained at
     * the next {@link #beginFrame()}, i.e. only once the frame that could
     * still have had a pending blit of them has been fully submitted.
     */
    private static final java.util.ArrayList<RimMask> pendingRimRelease = new java.util.ArrayList<>();
    private static final java.util.concurrent.atomic.AtomicLong RIM_MASK_SEQ =
            new java.util.concurrent.atomic.AtomicLong();

    private static final class RimMask {
        NativeImage image;
        DynamicTexture texture;
        Identifier id;
        int w = -1, h = -1;
        /** Frame epoch this mask was last handed to a caller for blitting. */
        long lastEpoch = -1L;
    }

    private static RimMask rimMask(int devW, int devH, int devRadius, int scale) {
        long key = ((long) devW << 42) | ((long) devH << 16) | devRadius;
        RimMask mask = rimMasks.get(key);
        if (mask != null) { mask.lastEpoch = poolEpoch; return mask; }
        if (rimMasks.size() >= RIM_MASK_CACHE_CAP) evictColdRimMasks();
        mask = rasterizeRimMask(devW, devH, devRadius, scale);
        if (mask == null) return null;
        mask.lastEpoch = poolEpoch;
        rimMasks.put(key, mask);
        return mask;
    }

    /**
     * Cap enforcement that can never destroy a texture a pending blit still
     * references — the same deferred-GuiRenderState rule the output pool
     * documents, which this cache used to violate.
     *
     * <p>HISTORY (the "Reset kills glass for the session" bug): this used to
     * be a wholesale {@code releaseRimMasks()} on overflow, called from the
     * middle of a frame's rendering. 1.21.11's {@code GuiGraphicsExtractor} RECORDS
     * blits into a deferred {@code GuiRenderState} and there is no flush API,
     * so masks already blitted earlier in that same frame were still pending
     * when their {@code DynamicTexture} was closed. At submit time the driver
     * bound deleted texture names and raised
     * {@code GL_INVALID_OPERATION in glBindTexture(non-gen name)} — one per
     * dangling blit. That error then sat in the GL error queue until the next
     * frame's legacy capture polled {@code glGetError} and
     * misattributed it to its own {@code glBlitFramebuffer}, throwing and
     * latching {@link #permanentlyDisabled} for the rest of the session.
     * Theme "Reset" reproduced it reliably because it changes the corner
     * roundness: every cached key is radius-derived, so a whole new
     * generation of shapes is inserted on top of the existing set and crosses
     * the cap mid-frame.
     *
     * <p>Two rules keep that from recurring: only masks NOT used in the
     * current frame are evictable, and even those are merely queued —
     * {@link #beginFrame()} destroys them once the frame that could have
     * referenced them is fully submitted. If every entry is in use this
     * frame, the cache is simply allowed to run over the cap; it drains on
     * the next frame that has cold entries. Correctness beats the cap.
     */
    private static void evictColdRimMasks() {
        var it = rimMasks.entrySet().iterator();
        while (it.hasNext()) {
            RimMask m = it.next().getValue();
            if (m.lastEpoch == poolEpoch) continue; // blitted this frame — blit still pending
            pendingRimRelease.add(m);
            it.remove();
        }
    }

    /**
     * Rasterizes the directional stroke mask at device resolution: white RGB
     * everywhere, alpha = SDF band × light-facing term — the composite
     * shader's border-stroke math (rounded-rect SDF band with its soft
     * internal taper, multiplied by the facing smoothstep against the one
     * fixed light) evaluated per pixel. Rasterized ONCE per shape; opacity
     * and hue are applied later via the blit tint, so they act as a modifier
     * ON the directional falloff, never a replacement for it.
     */
    private static RimMask rasterizeRimMask(int devW, int devH, int devRadius, int scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getTextureManager() == null) return null;
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, devW, devH, false);
        float hx = devW * 0.5f, hy = devH * 0.5f;
        float innerX = hx - devRadius, innerY = hy - devRadius;
        float edgeWidth = Math.max(3f, 2f * scale);
        for (int py = 0; py < devH; py++) {
            for (int px = 0; px < devW; px++) {
                float dx = px + 0.5f - hx, dy = py + 0.5f - hy;
                float qx = Math.abs(dx) - innerX;
                float qy = Math.abs(dy) - innerY;
                float ox = Math.max(qx, 0f), oy = Math.max(qy, 0f);
                float d = (float) Math.sqrt(ox * ox + oy * oy)
                        + Math.min(Math.max(qx, qy), 0f) - devRadius;
                float coverage = Math.max(0f, Math.min(1f, 0.5f - d));
                float band = smoothstep(-edgeWidth, 0f, d) * coverage;
                int alpha = 0;
                if (band > 0f) {
                    float plen = (float) Math.sqrt(dx * dx + dy * dy);
                    float facing = plen > 0.5f
                            ? (dx * LIGHT_DIR_X + dy * LIGHT_DIR_Y) / plen
                            : 0f;
                    alpha = (int) (band * smoothstep(-0.35f, 0.55f, facing) * 255f + 0.5f);
                }
                image.setPixel(px, py, (alpha << 24) | 0x00FFFFFF);
            }
        }
        RimMask mask = new RimMask();
        mask.image = image;
        mask.w = devW;
        mask.h = devH;
        long seq = RIM_MASK_SEQ.incrementAndGet();
        mask.id = Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "rim_mask/" + seq);
        mask.texture = new DynamicTexture(() -> "aurora_rim_mask_" + seq, image);
        mc.getTextureManager().register(mask.id, mask.texture);
        mask.texture.upload();
        return mask;
    }

    private static float smoothstep(float a, float b, float v) {
        float t = Math.max(0f, Math.min(1f, (v - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }

    /**
     * Destroys one mask's texture/image. Only ever called where no blit of
     * it can still be pending: {@link #beginFrame()} (queued evictions) and
     * {@link #shutdown()}.
     */
    private static void destroyRimMask(RimMask mask) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getTextureManager() != null && mask.id != null) {
            try { mc.getTextureManager().release(mask.id); } catch (Throwable ignored) { }
        }
        try { if (mask.texture != null) mask.texture.close(); } catch (Throwable ignored) { }
        try { if (mask.image != null) mask.image.close(); } catch (Throwable ignored) { }
        mask.texture = null;
        mask.image = null;
        mask.id = null;
        mask.w = -1;
        mask.h = -1;
    }

    /** Destroys every rim mask, queued or live. Shutdown/reload only. */
    private static void releaseRimMasks() {
        for (RimMask mask : pendingRimRelease) destroyRimMask(mask);
        pendingRimRelease.clear();
        for (RimMask mask : rimMasks.values()) destroyRimMask(mask);
        rimMasks.clear();
    }

    /**
     * Full variant with the lighting impression and an optional synthetic
     * capture source (test harness only — see {@link CaptureSource}).
     * Returns {@code true} when the panel was drawn.
     *
     * @param lighting lighting parameters, or null to disable lighting
     * @param captureSource synthetic backdrop FBO, or null to capture the
     *                      live framebuffer behind the panel
     */
    public static boolean renderPanel(GuiGraphicsExtractor g, float guiX, float guiY, float guiW, float guiH,
                                      float cornerRadiusGui, float blurRadiusPx, Lighting lighting,
                                      CaptureSource captureSource) {
        return renderPanel(g, guiX, guiY, guiW, guiH, cornerRadiusGui, blurRadiusPx, lighting,
                Priority.CONTROL, captureSource);
    }

    /**
     * The complete variant: lighting, degradation priority and (harness
     * only) a synthetic capture source. Every other overload lands here.
     *
     * @param priority degradation priority under pool pressure (null =
     *                 {@link Priority#CONTROL}); see {@link Priority}
     */
    public static boolean renderPanel(GuiGraphicsExtractor g, float guiX, float guiY, float guiW, float guiH,
                                      float cornerRadiusGui, float blurRadiusPx, Lighting lighting,
                                      Priority priority, CaptureSource captureSource) {
        return renderPanelInternal(g,guiX,guiY,guiW,guiH,cornerRadiusGui,blurRadiusPx,
                lighting,priority,captureSource,false);
    }

    /** Unified pilot: bypasses only the retained legacy style preference.
     * Every capture, screenshot, scissor and capacity guard remains shared. */
    public static boolean renderUnifiedPanel(GuiGraphicsExtractor g,float x,float y,float w,float h,float r,
                                             float blur,Lighting lighting,Priority priority) {
        return renderPanelInternal(g,x,y,w,h,r,blur,lighting,priority,null,true);
    }



    private static boolean renderPanelInternal(GuiGraphicsExtractor g,float guiX,float guiY,float guiW,float guiH,
            float cornerRadiusGui,float blurRadiusPx,Lighting lighting,Priority priority,
            CaptureSource captureSource,boolean unified) {
        if (priority == null) priority = Priority.CONTROL;
        lastOutcome = "rendered";
        if (permanentlyDisabled) { lastOutcome = "disabled (earlier failure)"; return false; }
        // Glass depends on two hooks: the per-frame stamp that recycles its
        // output pool, and the pause around screenshots that prevents a
        // native crash. Without either, every caller takes its flat fallback.
        if (com.aurora.client.health.MixinHealth.failed("MinecraftClientRenderMixin")
                || com.aurora.client.health.MixinHealth.failed("ScreenshotMixin")
                || com.aurora.client.health.MixinHealth.failed("GuiRendererGlassMixin")) {
            lastOutcome = "disabled (a required hook could not be applied)";
            return false;
        }

        if (System.currentTimeMillis() < suppressGlassUntilMs) {
            // Screenshot interlock (see suppressGlassUntilMs): fall back to
            // the caller's opaque rendering while vanilla's grab machinery
            // is in flight. lastOutcome names the reason.
            lastOutcome = "suppressed (screenshot in flight)";
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc != null ? com.aurora.client.adapter.NativeUi.mainTarget(mc) : null;
        if (main == null || main.width <= 0 || main.height <= 0) { lastOutcome = "no main target"; return false; }
        // With no level, capture only a panorama explicitly declared this frame.
        // Otherwise the main target may be empty or contain a stale world frame.
        // The harness can provide its own capture texture.
        if (captureSource == null && mc.level == null && !menuBackdropValid()) {
            lastOutcome = "no level (menu context)";
            return false;
        }
        if (guiW < 4f || guiH < 4f) { lastOutcome = "panel too small"; return false; }
        // The requested radius is the theme's Blur Radius, independent of
        // Background Opacity since 2026-11-12 (before that the frost scaled
        // with the opacity). Below the effective minimum the panel declines
        // and the caller's flat fill carries the tint: Blur Radius 0 is
        // glass with no blur.
        float radius = Math.min(64f, Math.max(0f, blurRadiusPx));
        if (radius < MIN_EFFECTIVE_RADIUS_PX) { lastOutcome = "radius below minimum"; return false; }

        long perfStart = GlassStats.ENABLED ? System.nanoTime() : 0L;
        try {
            int scale = Math.max(1, (int) mc.getWindow().getGuiScale());

            // Panel rect in device pixels, clamped to the framebuffer.
            // Derived from (int)guiX * scale — the SAME quantization the
            // final GuiGraphicsExtractor.blit applies (it can only place the quad at
            // integer GUI coordinates) — NOT round(guiX * scale). HISTORY:
            // round() disagreed with the blit grid by up to one GUI pixel
            // for fractional panel positions (e.g. a centered panel at odd
            // GUI widths: (int)93.5 -> 186 device vs round(187.0) -> 187),
            // a constant ~2-device-px shift of the whole blurred backdrop
            // against the unblurred surroundings. With both sides derived
            // from the same quantization, the capture samples exactly the
            // region the quad covers and the backdrop pattern continues
            // pixel-exactly across the panel edge.
            int devX = (int) guiX * scale;
            int devY = (int) guiY * scale;
            int devW = (int) guiW * scale;
            int devH = (int) guiH * scale;
            int x0 = Math.max(0, devX);
            int y0 = Math.max(0, devY);
            int x1 = Math.min(main.width, devX + devW);
            int y1 = Math.min(main.height, devY + devH);
            if (x1 - x0 < 4 || y1 - y0 < 4) { lastOutcome = "panel offscreen"; return false; }

            // Padded capture region: extend by the blur radius on each side
            // so edge samples don't clamp to the region border.
            int pad = Math.min((int) Math.ceil(radius), Math.min(main.width, main.height) / 4);
            int regX = Math.max(0, x0 - pad);
            int regY = Math.max(0, y0 - pad);
            int regX1 = Math.min(main.width, x1 + pad);
            int regY1 = Math.min(main.height, y1 + pad);
            int regW = regX1 - regX;
            int regH = regY1 - regY;

            PanelOutput out = nextOutput(priority);
            return PortableBlurRenderer.render(g, out.portable, main, x0, y0, regX, regY,
                    regW, regH, x1 - x0, y1 - y0, scale, radius, cornerRadiusGui, lighting, unified, captureSource);
        } catch (Declined d) {
            // Declines escaping Frame.run before its own catch (e.g. pool
            // exhausted in nextOutput, before any GL state is touched) are
            // the same expected-transient path — never disable the renderer.
            if (GlassStats.ENABLED) GlassStats.decline(d.getMessage());
            lastOutcome = d.getMessage();
            return false;
        } catch (Throwable t) {
            AuroraClient.LOGGER.error("[BlurPanel] render failed - blur panels disabled for this session.", t);
            permanentlyDisabled = true;
            lastOutcome = "failed (see log)";
            return false;
        } finally {
            if (GlassStats.ENABLED) GlassStats.panelDone(System.nanoTime() - perfStart);
        }
    }

    private static PanelOutput nextOutput(Priority priority) {
        long nowMs = System.currentTimeMillis();
        if (slotEpoch != poolEpoch || nowMs - lastOutputCallMs > 250L) {
            // A staleness reset with no epoch signal is a frame boundary
            // this code has to infer; roll the demand forecast here too so
            // the epoch-less path degrades by priority as well.
            if (slotEpoch == poolEpoch) rollDemand();
            slotEpoch = poolEpoch;
            outputCursor = 0;
            claimsThisFrame = 0;
            java.util.Arrays.fill(claimedThisFrame, 0);
        }
        lastOutputCallMs = nowMs;
        final int tier = priority.ordinal();
        demandThisFrame[tier]++;
        // Slots the higher tiers are still expected to take this frame
        // (last frame's demand, less what they have already claimed).
        int reserved = 0;
        for (int u = 0; u < tier; u++) {
            reserved += Math.max(0, demandLastFrame[u] - claimedThisFrame[u]);
        }
        if (claimsThisFrame + reserved >= OUTPUT_POOL) {
            // Pool exhaustion is a real diagnosable condition (a screen
            // denser than the pool was sized for), not a transient hiccup:
            // surface it in the log, rate-limited so a permanently-dense
            // screen logs one line per interval instead of one per frame.
            if (nowMs - GlassStats.lastPoolWarnMs > 10_000L) {
                GlassStats.lastPoolWarnMs = nowMs;
                AuroraClient.LOGGER.warn("[BlurPanel] output pool exhausted ({} slots; demand last frame W={} C={} R={} D={}) - lowest-priority panels render their flat fallback",
                        OUTPUT_POOL, demandLastFrame[0], demandLastFrame[1], demandLastFrame[2], demandLastFrame[3]);
            }
            if (GlassStats.ENABLED) GlassStats.poolDecline(priority);
            throw new Declined("output pool exhausted this frame (" + priority + ")");
        }
        claimsThisFrame++;
        claimsTotal++;
        claimedThisFrame[tier]++;
        if (GlassStats.ENABLED) GlassStats.claim(priority);
        PanelOutput out = outputs[outputCursor];
        if (out == null) {
            out = new PanelOutput();
            outputs[outputCursor] = out;
        }
        outputCursor = (outputCursor + 1) % OUTPUT_POOL;
        return out;
    }

    private static void rollDemand() {
        System.arraycopy(demandThisFrame, 0, demandLastFrame, 0, Priority.COUNT);
        java.util.Arrays.fill(demandThisFrame, 0);
    }

    private static final class Declined extends RuntimeException {
        Declined(String msg) { super(msg); }
    }

    public static void runPendingCaptures() {
        long perfStart = GlassStats.ENABLED ? System.nanoTime() : 0L;
        try { com.aurora.client.diagnostics.Timed.glass(PortableBlurRenderer::runPending); }
        catch (Throwable error) {
            AuroraClient.LOGGER.error("[BlurPanel] render failed - blur panels disabled for this session.", error);
            permanentlyDisabled = true;
            lastOutcome = "failed (see log)";
        } finally {
            if (GlassStats.ENABLED) GlassStats.submissionNs += System.nanoTime() - perfStart;
        }
    }
    public static void beginFrame() {
        PortableBlurRenderer.beginFrame();
        GlassStats.flushFrame();
        for (RimMask mask : pendingRimRelease) destroyRimMask(mask);
        pendingRimRelease.clear();
        rollDemand();
        poolEpoch++;
    }
    public static void shutdown() {
        PortableBlurRenderer.shutdown();
        releaseRimMasks();
    }
}
