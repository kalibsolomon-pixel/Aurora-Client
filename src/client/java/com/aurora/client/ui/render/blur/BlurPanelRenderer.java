package com.aurora.client.ui.render.blur;

import com.aurora.client.AuroraClient;
import com.aurora.client.theme.GlassStyle;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.ui.util.RenderUtil;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Translucent blurred panel renderer — the deliberately conservative
 * rebuild of the removed glass system, now with a simple lighting
 * IMPRESSION layered on top of the verified blur+tint baseline. Scope:
 * <ol>
 *   <li><b>Blur</b> — a two-pass separable Gaussian over the backdrop the
 *       panel sits on (a padded region captured from the main render
 *       target via {@code glBlitFramebuffer}, blurred at quarter
 *       resolution).</li>
 *   <li><b>Tint</b> — NOT in this class. The tint is the panel's ordinary
 *       translucent fill, drawn by the caller on top of this renderer's
 *       output (see the single-opacity note below).</li>
 *   <li><b>Lighting impression</b> — exactly two well-understood 2D
 *       compositing terms in the existing composite pass (see
 *       {@link Lighting}): a full-surface directional gradient and a
 *       fixed-width directional border stroke, both plain lerp/smoothstep
 *       math against one fixed top-slightly-right light. Raised and
 *       depressed treatments are simple inversions of the same two
 *       terms.</li>
 * </ol>
 *
 * <p><b>Explicitly out of scope, by decision:</b> SDF-normal-based
 * lighting, specular-exponent highlights, refraction/distortion, and
 * dome/bevel geometry. Those caused the previous attempt's failures;
 * refraction in particular was never once cleanly confirmed working and
 * deserves its own careful, later, standalone task if ever revisited.
 *
 * <h2>Alpha / blend convention (read before touching)</h2>
 * The composite shader outputs <b>STRAIGHT (non-premultiplied) alpha</b>:
 * RGB is the blurred backdrop sample, A is the rounded-rect coverage mask
 * (commented at the shader's output line below). The texture is uploaded
 * straight-alpha and drawn through {@code GuiGraphics.blit} with the
 * standard GUI pipeline (src-alpha / 1-minus-src-alpha) — the same path
 * every vanilla GUI texture takes, so no custom blend state exists
 * anywhere in this class.
 *
 * <h2>Background Opacity: exactly ONE application point</h2>
 * This renderer applies <b>no</b> opacity of its own. The theme's
 * Background Opacity is applied exactly once, in the {@code WINDOW_FILL}
 * token's alpha (resolved in {@code ThemeResolver}), which the caller's
 * translucent panel fill uses when tinting on top of this blur. Do NOT
 * multiply any opacity factor into this pipeline — a second application
 * point here is precisely the bug class that broke the previous attempt.
 *
 * <p>The rim highlight is a TWO-HALF system driven by the one Background
 * Opacity value. IN-GLASS half: the composite pass blends the stroke's
 * target color toward the panel's own per-pixel base as opacity rises
 * ({@code uRimBlend}) — the bright glass light catch owns the translucent
 * end and retires as the fill takes over the read. ABOVE-FILL half:
 * {@link #drawRimFinish} draws a DIRECTIONAL pastel border of the current
 * accent ({@code ResolvedTheme#rimPastel()}) on top of the caller's fill —
 * brightest facing the light, fading around the perimeter, exactly like
 * the in-glass stroke — with alpha ramping to fully opaque at 100% opacity
 * (a solid pastel rim is the only rim that can survive an opaque fill).
 * Both halves are lighting-term
 * derivations, NOT second opacity applications — no alpha in this pipeline
 * is multiplied by them. The FROST RADIUS is the third such derivation
 * ({@link #frostRadiusPx}): the blur a panel is rendered with scales with
 * the same value, so the slider's bottom is clear glass and full frost
 * arrives with the fill — again a material property, never an alpha.
 * Never add others.
 *
 * <h2>Intermediate precision</h2>
 * Every intermediate target (capture, both blur-chain textures, the
 * composite) is RGBA8. That is adequate and deliberately not
 * "upgraded": inputs are already 8-bit LDR framebuffer colors, a Gaussian
 * is a convex weighted average (it cannot exceed its inputs' min/max),
 * and the result is displayed on an 8-bit target. No HDR math exists in
 * this chain.
 *
 * <h2>Final blit path</h2>
 * The composite is read back ({@code glReadPixels}) into a
 * {@link NativeImage}-backed {@link DynamicTexture} and drawn with the
 * standard {@code GuiGraphics.blit} so it composites in correct GUI draw
 * order — the identical, proven mechanism {@code UiLayerCache} uses. The
 * per-frame readback costs ~1-2 ms for one panel-sized region; simple and
 * proven beats fast and fragile for this foundation pass.
 *
 * <h2>Screenshot (F2) interlock — root-caused and fixed</h2>
 * Taking a vanilla screenshot (F2) while this renderer is actively
 * rendering used to crash the JVM — first observed as
 * STATUS_HEAP_CORRUPTION (0xc0000374), and finally reproduced as a clean
 * EXCEPTION_ACCESS_VIOLATION inside nvoglv64 during this pipeline's
 * glReadPixels (hs_err 5156: write fault at the exact end of the staging
 * buffer, on the first frame after a grab). Root cause: vanilla's grab
 * path sets GL_PACK_* pixel-store state (row length / skips / alignment)
 * and never resets it; a stale PACK_ROW_LENGTH wider than the panel makes
 * the driver write panH rows x (staleRowLength*4) bytes into a
 * panW*panH*4 destination — an out-of-bounds native write. Three defenses:
 * <ol>
 *   <li><b>Pixel-store reset (the fix):</b> every PACK/UNPACK parameter is
 *       forced to its neutral default at pipeline entry, so our reads and
 *       uploads are tightly-packed no matter what any foreign code left
 *       behind.</li>
 *   <li><b>Suppression (isolation):</b> the Screenshot mixin calls
 *       {@link #noteScreenshotGrab()} at the head of vanilla's grab path;
 *       while the stamp is fresh (400 ms) every {@code renderPanel} call
 *       returns false, so callers draw their opaque fallback and this
 *       pipeline performs no GL work during the grab and its async PNG
 *       encode.</li>
 *   <li><b>Buffer hygiene:</b> PIXEL_PACK/PIXEL_UNPACK bindings are forced
 *       to zero at entry and exit, so no leftover pack buffer can
 *       re-interpret this pipeline's pointer-based reads/writes as offsets
 *       into it.</li>
 * </ol>
 * Verified by driving the real grab path (deferred to the F2-equivalent
 * frame-start context) five times while panels render: all grabs saved,
 * JVM stable. The [S] key on the test screen remains as a permanent crash
 * canary. Residual risk: a screenshot path that bypasses
 * {@code Screenshot.grab}/{@code takeScreenshot} (none known).
 */
public final class BlurPanelRenderer {

    /**
     * Factory default blur radius, in device (screen) pixels — the FULL-FROST
     * radius, reached at 100% Background Opacity. The radius a panel actually
     * blurs with follows the opacity: see {@link #frostRadiusPx}.
     */
    public static final float DEFAULT_BLUR_RADIUS_PX = 24f;

    /**
     * Floor of the frost curve, as a fraction of the requested radius (4 px
     * at the default): at the very bottom of the slider the glass is still
     * glass — a hint of frost keeps the material and its light catch — but
     * the backdrop reads through it almost unblurred.
     */
    public static final float MIN_FROST_FRACTION = 1f / 6f;

    /**
     * The blur radius a panel is rendered with, derived from Background
     * Opacity: {@code requested × max(MIN_FROST_FRACTION, √opacity)}.
     *
     * <p>Why: the tint fill's alpha already reaches 0 at the slider's
     * minimum (linear, no floor — {@code ThemeResolver.applyBackgroundOpacity}),
     * yet a panel at 0% still read as a solid frosted slab, because the
     * blur was a constant: at every opacity the backdrop inside the panel
     * was replaced by a 24 px Gaussian of itself, which on any detailed
     * backdrop destroys exactly the detail that would make the panel read
     * as see-through. The frost is what "opaque" looked like at the low
     * end. So the frost now follows the one opacity value like every other
     * material term: clear glass at the bottom (√ keeps the ramp steep
     * where it is visible — 0.05 → 5 px, 0.25 → 12 px, 0.5 → 17 px), the
     * established full frost from ~70% up, where the fill dominates the
     * read anyway.
     *
     * <p>This is a lighting-term-class derivation from the single opacity
     * value — the same category as {@code uRimBlend} and the rim finish's
     * alpha — NOT an opacity application point: no alpha anywhere in this
     * pipeline is multiplied by it (AGENTS.md §6, convention 1).
     */
    public static float frostRadiusPx(float requestedRadiusPx) {
        double opacity = ThemeManager.current().backgroundOpacity();
        float k = (float) Math.max(MIN_FROST_FRACTION, Math.sqrt(Math.max(0d, Math.min(1d, opacity))));
        return requestedRadiusPx * k;
    }

    // ==================================================================================
    // Lighting impression — one fixed virtual light (gradient + outline only)
    // ==================================================================================

    /**
     * Unit vector TOWARD the single fixed virtual light shared by every
     * glass panel: above the panel, offset ~14° right of straight-up (the
     * task's suggested starting vector {@code normalize(0.25, -1.0)}).
     * Panel space follows the screen convention — +x right, +y DOWN — so
     * "up and slightly right" is (0.2425, -0.9701).
     */
    public static final float LIGHT_DIR_X = 0.24253563f;
    public static final float LIGHT_DIR_Y = -0.9701425f;

    /**
     * Lighting impression parameters for {@link #renderPanel}. {@code null}
     * disables lighting entirely (the plain blur+tint look — the "before"
     * half of a before/after comparison).
     *
     * <p>Scope is deliberately TWO terms only: a full-surface directional
     * gradient (Element 2, drawn first) and a fixed-width directional
     * border stroke (Element 1, drawn on top) — plain smoothstep/lerp math
     * against {@link #LIGHT_DIR_X}/{@link #LIGHT_DIR_Y}. No SDF-normal
     * reconstruction, no specular exponent, no refraction — the complexity
     * class that sank the previous glass attempt.
     *
     * <p><b>Raised vs depressed</b> is a simple inversion of both terms:
     * raised shows a bright stroke on the light-facing edge with the face
     * brighter at the light-facing corner; depressed shows a shadow on the
     * light-facing edge (the recess blocks direct light there) with a
     * subtle highlight on the far edge, and the gradient's bright/dark
     * corners swap.
     */
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

    /**
     * Optional SYNTHETIC capture source for the test harness. When present,
     * {@code renderPanel} captures its blur samples from this FBO (mapped
     * proportionally over the panel's screen region) instead of the live
     * framebuffer.
     *
     * <p>Why this exists: batched {@code GuiGraphics} fills (like the test
     * screen's SOLID/STRIPES backdrop) only reach the framebuffer when the
     * GUI batch flushes — AFTER the immediate GL capture inside
     * {@code renderPanel} — so without this override a "static backdrop"
     * test would actually blur the mid-frame world. The harness renders its
     * synthetic backdrop into its own FBO every frame and passes it here,
     * making the static controls truly static. Real screens always pass
     * {@code null} (live capture).
     */
    public static final class CaptureSource {
        public final int fbo;
        public final int width;
        public final int height;

        public CaptureSource(int fbo, int width, int height) {
            this.fbo = fbo;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Degradation priority of a panel when a frame asks for more concurrent
     * glass panels than the output pool holds ({@link #OUTPUT_POOL}). Under
     * pressure the pool declines panels LOWEST priority first — the same
     * panels every frame — instead of whichever panels happen to render
     * after the pool ran dry (render order, which on a list screen meant the
     * bottom rows AND the toolbar's Done button flickered flat while the top
     * rows' Copy/Color buttons kept their glass). Declared highest first:
     * <ol>
     *   <li>{@link #WINDOW} — the screen's surface: windows, main panels,
     *       sidebars, modals, preview cards. One or two per screen, the
     *       largest element, and the one whose flat fallback changes the
     *       whole screen's read.</li>
     *   <li>{@link #CONTROL} — standalone interactive controls: toolbar and
     *       Done/Reset buttons, chips, segments, search fields, setting
     *       widgets, popups. Bounded per screen and individually
     *       noticeable. The default for every call that names no priority.</li>
     *   <li>{@link #ROW} — repeated list rows, tiles and cards, whose count
     *       scales with the list and the viewport. Within a tier the pool is
     *       first-come, so rows degrade from the bottom of the list up.</li>
     *   <li>{@link #DETAIL} — repeated small elements INSIDE rows and cards
     *       (per-row Copy/Color/Duplicate, per-card Install): the most
     *       numerous, smallest, and least missed when flat. Declined first.</li>
     * </ol>
     * The mechanism is a reservation, not a sort (see {@link #nextOutput}):
     * a claim of tier {@code t} is refused while the slots still expected by
     * every HIGHER tier this frame — last frame's demand, less what that tier
     * has already claimed — would not fit alongside it. Frames whose total
     * demand fits the pool are untouched by the rule.
     */
    public enum Priority {
        WINDOW, CONTROL, ROW, DETAIL;

        /** Number of tiers — sizes the per-tier accounting arrays. */
        static final int COUNT = values().length;
    }


    /** Blur-chain resolution relative to the capture region's screen size. */
    private static final float CHAIN_SCALE = 0.25f;

    /**
     * Below this radius a panel is visually unblurred; callers may pass 0
     * to force the plain fallback path (the "before" half of a
     * before/after screenshot).
     */
    private static final float MIN_EFFECTIVE_RADIUS_PX = 1.5f;

    /**
     * How long after a vanilla screenshot grab the glass pipeline stays
     * suppressed. The GL copy inside grab is synchronous, but the PNG
     * encode/flush of the returned NativeImage runs on other threads;
     * 400 ms comfortably covers that window at any resolution.
     */
    private static final long SCREENSHOT_SUPPRESS_MS = 400L;

    private static final AtomicLong SEQ = new AtomicLong();

    // ---- GL objects (created lazily on the render thread) ----
    private static boolean initialized = false;
    private static boolean permanentlyDisabled = false;

    /**
     * Screenshot interlock: glass rendering is suppressed while
     * {@code System.currentTimeMillis() < suppressGlassUntilMs}. Stamped by
     * {@link #noteScreenshotGrab()} (called from the Screenshot mixin at the
     * head of vanilla's grab path) so the whole glass pipeline — GL passes,
     * per-frame CPU readback, texture uploads — is completely idle while
     * vanilla's screenshot machinery (GPU copy + async PNG encode on other
     * threads) is in flight. Callers automatically draw their opaque
     * fallback for those frames via renderPanel's false return.
     */
    private static volatile long suppressGlassUntilMs = 0L;

    /** Helper FBO that wraps the main target's color texture for live-world capture. */
    private static int worldReaderFbo;
    /** One-shot diagnostic guard for the live-world capture path. */
    private static boolean loggedWorldProbe = false;
    private static int blurProgram;
    private static int compositeProgram;
    private static int emptyVao;
    private static final int[] blurFbo = new int[2];
    private static final int[] blurTex = new int[2];
    private static int chainW, chainH;
    private static int compositeFbo;
    private static int compositeTex;
    private static int outW, outH;

    // ---- output texture pool (registered with the TextureManager) ----
    // 1.21.11's GuiGraphics RECORDS blits into a deferred GuiRenderState;
    // there is no flush API. Two glass panels blitted in the same frame
    // therefore cannot share one texture object — the second upload would
    // retroactively change the first (still-pending) blit. Each concurrent
    // panel per frame takes its own pooled output texture instead. The full
    // Theme screen treatment is 13 concurrent panels (window, card, Done,
    // Reset, 2 preview buttons, 5 segments, toggle track, chip); the Mods
    // grid pilot reaches ~15 (window, 2 category chips + Profiles chip, 2
    // layout toggles, up to 9 visible tiles). 24 leaves headroom for both —
    // exhaustion declines per frame, which would read as glass/flat flicker
    // (now by Priority, lowest first — see nextOutput).
    private static final int OUTPUT_POOL = 24;
    private static final PanelOutput[] outputs = new PanelOutput[OUTPUT_POOL];

    // ---- over-subscription accounting (see Priority / nextOutput) ----
    /** Claims that reached {@link #nextOutput} LAST frame, per tier — the forecast the reservation works from. */
    private static final int[] demandLastFrame = new int[Priority.COUNT];
    /** Claims that reached {@link #nextOutput} so far THIS frame, per tier (declined ones included). */
    private static final int[] demandThisFrame = new int[Priority.COUNT];
    /** Slots actually claimed so far this frame, per tier. */
    private static final int[] claimedThisFrame = new int[Priority.COUNT];

    /** Current frame epoch — bumped by {@link #beginFrame()} once per frame. */
    private static volatile long poolEpoch = 0;
    /** Epoch the output cursor currently belongs to (-1 = none yet). */
    private static long slotEpoch = -1;
    /** Output slots claimed in the current epoch (pool-exhaustion guard). */
    private static int claimsThisFrame = 0;
    /** Round-robin cursor over {@link #outputs} within the current epoch. */
    private static int outputCursor = 0;
    /** Staleness safety net for paths that render without beginFrame(). */
    private static long lastOutputCallMs = 0L;
    /** One-shot guard for the incomplete-reader warning. */
    private static boolean loggedReaderIncomplete = false;

    /** One pooled output slot: NativeImage + registered DynamicTexture. */
    private static final class PanelOutput {
        NativeImage image;
        DynamicTexture texture;
        Identifier id;
        int w = -1, h = -1;
    }

    /** Cached readback staging buffer — reused every frame (see readback). */
    private static ByteBuffer readBuffer;

    // ---- uniform locations ----
    private static int uBlurInput, uBlurDir, uBlurTexel, uBlurScale, uBlurSigma;
    private static int uCompInput, uCompUvRect, uCompHalfSize, uCompRadius;
    private static int uLight, uEdgeWidth, uEdgeStrength, uGradStrength, uDepressed, uRimBlend;

    private static String lastOutcome = "not run";

    // ==================================================================================
    // Perf instrumentation — opt-in via -Daurora.glassStats=true, zero cost otherwise
    // ==================================================================================

    /**
     * Per-phase wall-clock timing and pool accounting for the glass
     * pipeline, aggregated per frame and reported as one log line per
     * {@link #REPORT_EVERY_FRAMES} frames while the property is set. GL
     * calls are asynchronous, so the phase numbers are CPU wall time —
     * EXCEPT readback, where the synchronous {@code glReadPixels} stalls
     * until the GPU has finished everything previously submitted (which is
     * precisely why per-panel readback is the suspected scaling cost: it
     * serializes CPU and GPU once per panel).
     *
     * <p>Readback is further split into its three real components:
     * {@code readGl} (the glReadPixels stall), {@code loop} (the per-pixel
     * RGBA→ARGB Java conversion) and {@code upload} (the DynamicTexture
     * re-upload of the full panel).
     */
    static final class GlassStats {
        static final boolean ENABLED = Boolean.getBoolean("aurora.glassStats");
        static final int REPORT_EVERY_FRAMES = 60;

        // ---- current frame (reset at beginFrame) ----
        static int panels;          // pool claims (panels actually rendered)
        static int declines;        // Declined outcomes (any reason)
        static int poolExhausted;   // Declined("output pool exhausted")
        static long frameTotalNs;   // wall time inside renderPanel, all attempts
        static long captureNs, blurNs, compositeNs, otherNs, blitNs;
        static long readGlNs, readLoopNs, uploadNs;
        static long readPixels;     // device pixels read back this frame

        // ---- per-tier pool accounting (claims / pool declines, per interval) ----
        static final int[] tierClaims = new int[Priority.COUNT];
        static final int[] tierPoolDeclines = new int[Priority.COUNT];

        // ---- reporting interval accumulators ----
        static int framesWithPanels, framesSinceReport, sumDeclines, sumPoolExhausted;
        static long sumPanels, maxPanels, sumFrameNs, maxFrameNs;
        static long sumCaptureNs, sumBlurNs, sumCompositeNs, sumOtherNs, sumBlitNs;
        static long sumReadGlNs, sumReadLoopNs, sumUploadNs, sumReadPixels;
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
                sumCaptureNs += captureNs;
                sumBlurNs += blurNs;
                sumCompositeNs += compositeNs;
                sumOtherNs += otherNs;
                sumBlitNs += blitNs;
                sumReadGlNs += readGlNs;
                sumReadLoopNs += readLoopNs;
                sumUploadNs += uploadNs;
                sumReadPixels += readPixels;
            }
            sumDeclines += declines;
            sumPoolExhausted += poolExhausted;
            panels = declines = poolExhausted = 0;
            frameTotalNs = captureNs = blurNs = compositeNs = otherNs = blitNs = 0;
            readGlNs = readLoopNs = uploadNs = readPixels = 0;
            if (++framesSinceReport >= REPORT_EVERY_FRAMES) report();
        }

        private static void report() {
            try {
                if (ENABLED && framesWithPanels > 0) {
                    double f = framesWithPanels;
                    double p = sumPanels;
                    // Per-tier claims/poolDeclines over the interval (W=window C=control R=row D=detail).
                    StringBuilder tiers = new StringBuilder();
                    for (Priority t : Priority.values()) {
                        tiers.append(t.name().charAt(0)).append('=')
                                .append(tierClaims[t.ordinal()]).append('/')
                                .append(tierPoolDeclines[t.ordinal()]).append(' ');
                    }
                    AuroraClient.LOGGER.info(String.format(
                            "[GlassStats] frames=%d panels/frame avg=%.1f max=%d | ms/frame total=%.2f max=%.2f"
                                    + " | ms/panel capture=%.3f blur=%.3f comp=%.3f readGL=%.3f loop=%.3f upload=%.3f blit=%.3f other=%.3f"
                                    + " | declines=%d poolExhausted=%d readback=%.2fMpx/frame | tiers claims/poolDeclines %s",
                            framesWithPanels, sumPanels / f, (int) maxPanels,
                            sumFrameNs / 1e6 / f, maxFrameNs / 1e6,
                            sumCaptureNs / 1e6 / p, sumBlurNs / 1e6 / p, sumCompositeNs / 1e6 / p,
                            sumReadGlNs / 1e6 / p, sumReadLoopNs / 1e6 / p, sumUploadNs / 1e6 / p,
                            sumBlitNs / 1e6 / p, sumOtherNs / 1e6 / p,
                            sumDeclines, sumPoolExhausted, sumReadPixels / 1e6 / f, tiers.toString().trim()));
                }
            } finally {
                java.util.Arrays.fill(tierClaims, 0);
                java.util.Arrays.fill(tierPoolDeclines, 0);
                framesSinceReport = 0;
                framesWithPanels = 0;
                sumPanels = maxPanels = sumFrameNs = maxFrameNs = 0;
                sumCaptureNs = sumBlurNs = sumCompositeNs = sumOtherNs = sumBlitNs = 0;
                sumReadGlNs = sumReadLoopNs = sumUploadNs = sumReadPixels = 0;
                sumDeclines = sumPoolExhausted = 0;
            }
        }
    }

    private BlurPanelRenderer() {}

    /** Outcome of the LAST renderPanel call — status text only, never a control input. */
    public static String lastOutcome() { return lastOutcome; }

    /**
     * Screenshot interlock entry point — called by the Screenshot mixin at
     * the head of vanilla's grab path. Suppresses all glass rendering for
     * {@link #SCREENSHOT_SUPPRESS_MS} so the pipeline is fully idle while
     * vanilla's screenshot GPU copy and async PNG encode run.
     */
    public static void noteScreenshotGrab() {
        suppressGlassUntilMs = System.currentTimeMillis() + SCREENSHOT_SUPPRESS_MS;
    }

    // ==================================================================================
    // Public entry point
    // ==================================================================================

    /**
     * Renders the blurred backdrop for one panel this frame and blits it in
     * correct GUI order. Returns {@code true} when blur was drawn; on
     * {@code false} the caller should simply draw its normal translucent
     * panel instead (the pre-blur look).
     *
     * <p>Re-captures and re-blurs on EVERY call, by design: the backdrop is
     * live (the world moves behind the panel), so caching would freeze it.
     *
     * @param g the screen's GuiGraphics
     * @param guiX/guiY/guiW/guiH panel rect in GUI-space units
     * @param cornerRadiusGui rounded-corner radius in GUI units (matches the
     *                        theme corner style the caller draws with)
     * @param blurRadiusPx blur radius in device pixels (clamped 0..64);
     *                     values below {@link #MIN_EFFECTIVE_RADIUS_PX}
     *                     return {@code false} — the "before" case of a
     *                     before/after screenshot
     */
    public static boolean renderPanel(GuiGraphics g, float guiX, float guiY, float guiW, float guiH,
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
    public static boolean renderPanel(GuiGraphics g, float guiX, float guiY, float guiW, float guiH,
                                      float cornerRadiusGui, float blurRadiusPx, Lighting lighting) {
        return renderPanel(g, guiX, guiY, guiW, guiH, cornerRadiusGui, blurRadiusPx, lighting,
                Priority.CONTROL, null);
    }

    /**
     * {@link #renderPanel(GuiGraphics, float, float, float, float, float, float, Lighting)}
     * with an explicit degradation {@link Priority}. The lighting-only
     * variants default to {@link Priority#CONTROL}; windows/containers pass
     * {@link Priority#WINDOW}, repeated rows/tiles/cards {@link Priority#ROW},
     * and repeated small elements inside them {@link Priority#DETAIL}. Only
     * consulted on frames whose demand exceeds the output pool.
     */
    public static boolean renderPanel(GuiGraphics g, float guiX, float guiY, float guiW, float guiH,
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
    public static void drawRimFinish(GuiGraphics g, float x, float y, float w, float h, float cornerRadiusGui) {
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
     * middle of a frame's rendering. 1.21.11's {@code GuiGraphics} RECORDS
     * blits into a deferred {@code GuiRenderState} and there is no flush API,
     * so masks already blitted earlier in that same frame were still pending
     * when their {@code DynamicTexture} was closed. At submit time the driver
     * bound deleted texture names and raised
     * {@code GL_INVALID_OPERATION in glBindTexture(non-gen name)} — one per
     * dangling blit. That error then sat in the GL error queue until the next
     * frame's {@link Frame#capture()} polled {@code glGetError} and
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
    public static boolean renderPanel(GuiGraphics g, float guiX, float guiY, float guiW, float guiH,
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
    public static boolean renderPanel(GuiGraphics g, float guiX, float guiY, float guiW, float guiH,
                                      float cornerRadiusGui, float blurRadiusPx, Lighting lighting,
                                      Priority priority, CaptureSource captureSource) {
        if (priority == null) priority = Priority.CONTROL;
        lastOutcome = "rendered";
        if (permanentlyDisabled) { lastOutcome = "disabled (earlier failure)"; return false; }
        // Glass Style: the user's Transparent choice is expressed through the
        // SAME fallback contract every glass consumer already implements —
        // decline, and the caller draws its flat translucent fill (§6's
        // fallback contract). That is why this is one guard here instead of a
        // branch at ~20 call sites, and why Corner Style and Background
        // Opacity keep their exact meanings in both styles: they live in the
        // caller's fill, which this renderer never touches. Placed before any
        // GL state is read or written, so Transparent also pays none of the
        // capture/blur/readback cost.
        // The synthetic harness (BlurTestScreen) is exempt, like the
        // menu-context guard below: it exists to exercise this pipeline.
        if (captureSource == null && ThemeManager.current().glassStyle() == GlassStyle.TRANSPARENT) {
            lastOutcome = "transparent style (glass off)";
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
        RenderTarget main = mc != null ? mc.getMainRenderTarget() : null;
        if (main == null || main.width <= 0 || main.height <= 0) { lastOutcome = "no main target"; return false; }
        // Menu-context guard: with no level loaded the main render target
        // holds no valid world — it is never written while the menu renders
        // (verified by probe: rgba=(0,0,0,0), reader FBO complete so no GL
        // error fires) or holds a stale frame after leaving a world. Never
        // run the capture/blur pipeline against an unvalidated backdrop:
        // decline, and the caller draws its opaque fallback. The synthetic
        // harness CaptureSource is exempt (it supplies its own valid FBO).
        if (captureSource == null && mc.level == null) {
            lastOutcome = "no level (menu context)";
            return false;
        }
        if (guiW < 4f || guiH < 4f) { lastOutcome = "panel too small"; return false; }
        // The requested radius is the full-frost radius; the frost actually
        // applied follows Background Opacity (see frostRadiusPx). The
        // below-minimum decline still keys off the REQUESTED radius so the
        // harness's "before" case (a deliberately tiny radius) and the
        // opacity curve's own floor never interact.
        float requested = Math.min(64f, Math.max(0f, blurRadiusPx));
        if (requested < MIN_EFFECTIVE_RADIUS_PX) { lastOutcome = "radius below minimum"; return false; }
        float radius = frostRadiusPx(requested);
        if (!ensureReady()) { lastOutcome = "init failed (see log)"; return false; }

        long perfStart = GlassStats.ENABLED ? System.nanoTime() : 0L;
        try {
            int scale = Math.max(1, (int) mc.getWindow().getGuiScale());

            // Panel rect in device pixels, clamped to the framebuffer.
            // Derived from (int)guiX * scale — the SAME quantization the
            // final GuiGraphics.blit applies (it can only place the quad at
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

            int cw = Math.max(4, Math.round(regW * CHAIN_SCALE));
            int ch = Math.max(4, Math.round(regH * CHAIN_SCALE));
            int panW = x1 - x0;
            int panH = y1 - y0;

            Frame frame = new Frame(main, x0, y0, regX, regY, regW, regH,
                    cw, ch, panW, panH, scale, radius, cornerRadiusGui, lighting, priority, captureSource);
            return frame.run(g, guiX, guiY, guiW, guiH);
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

    /**
     * One frame's GL work, kept in a small inner class so the parameter
     * list of {@link #renderPanel} stays readable. All fields are plain
     * locals-in-disguise; nothing escapes {@link #run}.
     */
    private static final class Frame {
        final RenderTarget main;
        final int x0, y0;          // panel rect (device px, clamped)
        final int regX, regY;      // padded capture region origin (device px)
        final int regW, regH;      // padded capture region size (device px)
        final int cw, ch;          // chain texture size
        final int panW, panH;      // panel size (device px)
        final int scale;
        final float radius;
        final float cornerRadiusGui;
        /** Lighting params, or null for the plain blur+tint look. */
        final Lighting lighting;
        /** Degradation priority under pool pressure (see {@link Priority}). */
        final Priority priority;
        /** Synthetic capture source (test harness), or null for live capture. */
        final CaptureSource capture;
        /** Pooled output texture for this call — one per concurrent panel per frame. */
        PanelOutput out;
        /** Game's draw target at entry — snapshotted BEFORE any of our GL binds. */
        int srcFbo;

        Frame(RenderTarget main, int x0, int y0, int regX, int regY,
              int regW, int regH, int cw, int ch, int panW, int panH,
              int scale, float radius, float cornerRadiusGui, Lighting lighting,
              Priority priority, CaptureSource capture) {
            this.main = main; this.x0 = x0; this.y0 = y0;
            this.regX = regX; this.regY = regY;
            this.regW = regW; this.regH = regH;
            this.cw = cw; this.ch = ch;
            this.panW = panW; this.panH = panH;
            this.scale = scale; this.radius = radius;
            this.cornerRadiusGui = cornerRadiusGui;
            this.lighting = lighting;
            this.priority = priority;
            this.capture = capture;
        }

        boolean run(GuiGraphics g, float guiX, float guiY, float guiW, float guiH) {
            // Claim this call's pooled output texture FIRST (see the output
            // pool note above): each concurrent panel in a frame needs its
            // own texture object, because 1.21.11's GuiGraphics defers all
            // blits and a shared texture would show the LAST upload in
            // every pending blit.
            out = nextOutput(priority);
            // Snapshot the source target BEFORE any of our own GL calls can
            // change the binding — this is the framebuffer holding everything
            // visually behind the panel right now.
            srcFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            SavedGlState saved = SavedGlState.save();
            try {
                // ENTRY hygiene (the exit-side restore in the finally block
                // protects the game from us; this protects us from the game):
                // a PIXEL_(UN)PACK_BUFFER left bound by an earlier foreign
                // operation (e.g. vanilla's screenshot grab) would make our
                // pointer-based glReadPixels / texture uploads reinterpret a
                // direct-buffer ADDRESS as an offset into that buffer — the
                // out-of-bounds write that manifests as heap corruption.
                // Both are forced to zero before ANY of our GL work.
                GL21.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
                GL21.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
                // Pixel-store hygiene — the OTHER half of the same lesson,
                // and the true root cause of the screenshot crash: foreign
                // code (vanilla's grab path in particular) sets GL_PACK_*
                // pixel-store state — row length / skips / alignment — and
                // does NOT reset it. glReadPixels honors that state, so a
                // stale PACK_ROW_LENGTH wider than this panel makes the
                // driver write panH rows x (staleRowLength*4) bytes into a
                // panW*panH*4 destination: an out-of-bounds native write
                // that manifested as STATUS_HEAP_CORRUPTION (0xc0000374) in
                // earlier sessions and, once the staging buffer became a
                // cached direct allocation, as a clean EXCEPTION_ACCESS_
                // VIOLATION inside nvoglv64 during readback on the first
                // frame after a grab (hs_err 5156: write fault at the exact
                // end of the buffer). Forcing every PACK/UNPACK parameter
                // back to its neutral default makes our reads and uploads
                // tightly-packed no matter what ran before us.
                GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
                GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL12.GL_PACK_IMAGE_HEIGHT, 0);
                GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
                GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
                GL11.glPixelStorei(GL12.GL_PACK_SKIP_IMAGES, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
                GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);
                // Error-queue hygiene — the attribution half of the same
                // lesson. glGetError reports the OLDEST error still queued
                // from ANY GL call, not just ours, and this pipeline treats
                // a GL error after its capture blit as a defect worth
                // disabling the renderer for the session. Without draining
                // first, an error raised by foreign code (or by an earlier
                // frame) is misattributed to our blit and costs glass until
                // restart — exactly how the rim-mask eviction bug manifested.
                // Drain here so every glGetError check below reports only
                // errors OUR calls produced; the latch keeps its full value
                // and stops guessing.
                drainGlErrors();
                final boolean perf = GlassStats.ENABLED;
                long t = perf ? System.nanoTime() : 0L;
                ensureChainTextures(cw, ch);
                ensureCompositeTarget(panW, panH);
                if (perf) { GlassStats.otherNs += System.nanoTime() - t; t = System.nanoTime(); }
                capture();
                if (perf) { GlassStats.captureNs += System.nanoTime() - t; t = System.nanoTime(); }
                blur();
                if (perf) { GlassStats.blurNs += System.nanoTime() - t; t = System.nanoTime(); }
                composite();
                if (perf) { GlassStats.compositeNs += System.nanoTime() - t; t = System.nanoTime(); }
                readback();
                if (perf) { t = System.nanoTime(); }
                blit(g, guiX, guiY, guiW, guiH);
                if (perf) { GlassStats.blitNs += System.nanoTime() - t; }
                return true;
            } catch (Declined d) {
                // Expected, transient decline (reader unavailable, pool
                // exhausted): restore below, then let the caller draw its
                // opaque fallback. Never disables the renderer.
                if (GlassStats.ENABLED) GlassStats.decline(d.getMessage());
                lastOutcome = d.getMessage();
                return false;
            } finally {
                saved.restore();
                // Pixel-buffer hygiene: the texture upload path may leave a
                // PBO bound; a lingering PIXEL_(UN)PACK_BUFFER reinterprets
                // the next pointer-based glReadPixels/glTexSubImage elsewhere
                // in the game (e.g. vanilla's screenshot grab) as an offset
                // into that buffer — the exact shape of the heap corruption
                // observed when taking screenshots while this panel renders.
                // Leaving both at zero is the safe neutral state.
                GL21.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
                GL21.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            }
        }

        // --- 1. Capture: blit the padded region out of the framebuffer
        // the GUI is CURRENTLY drawing into (the same target that holds
        // everything visually behind this panel — world plus any vanilla
        // screen background), or from the harness's synthetic backdrop FBO
        // when one is supplied (batched fills haven't flushed yet, so a
        // synthetic static backdrop must be captured from its own target).
        // The SOURCE Y range is deliberately written bottom-up (GL
        // framebuffer origin is bottom-left, screen origin is top-left) so
        // the stored texture is top-down — the orientation every later
        // step assumes. GL errors fail loudly here rather than capturing
        // garbage/black silently.
        private void capture() {
            int readFbo, srcW, srcH;
            if (capture != null) {
                readFbo = capture.fbo;
                srcW = capture.width;
                srcH = capture.height;
            } else {
                // Live-world capture. HISTORY: this used to blit from the
                // framebuffer that happened to be bound at GUI draw time
                // (srcFbo). On MC 1.21.11's GpuTexture-era renderer, GUI
                // drawing RECORDS into a deferred GuiRenderState — at the
                // point a mid-frame screen renders, nothing of this frame's
                // GUI has been submitted to any framebuffer, and the ambient
                // binding is an empty GUI-layer target. The capture read
                // transparent black, so live-world panels showed the lighting
                // gradient over black instead of the scene. The world (plus
                // everything drawn before this screen) lives in the MAIN
                // target's color texture; we wrap that GL texture in our own
                // reader FBO and blit from it instead.
                Integer reader = bindWorldReader();
                if (reader == null) {
                    // Reader unavailable or validation failed: DECLINE rather
                    // than fall back to the ambient binding — on 1.21.11 that
                    // target is an empty GUI-layer buffer (the Part 0 lesson),
                    // and an incomplete attachment reads undefined memory.
                    // Never capture from an unvalidated source.
                    throw new Declined("world reader unavailable/incomplete");
                }
                readFbo = reader;
                srcW = main.width;
                srcH = main.height;
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, blurFbo[0]);
            int pad = Math.min((int) Math.ceil(radius), Math.min(main.width, main.height) / 4);
            int regX1 = Math.min(main.width, x0 + panW + pad);
            int regY1 = Math.min(main.height, y0 + panH + pad);
            int rX = Math.max(0, x0 - pad);
            int rY = Math.max(0, y0 - pad);
            // Map the screen-space region proportionally onto the source.
            int sX0 = Math.round((float) rX * srcW / main.width);
            int sX1 = Math.round((float) regX1 * srcW / main.width);
            int sY0 = Math.round((float) rY * srcH / main.height);
            int sY1 = Math.round((float) regY1 * srcH / main.height);
            GL30.glBlitFramebuffer(
                    sX0, srcH - sY1, sX1, srcH - sY0,
                    0, 0, cw, ch,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            int err = GL11.glGetError();
            if (err != GL11.GL_NO_ERROR) {
                throw new IllegalStateException("capture blit failed (GL error 0x"
                        + Integer.toHexString(err) + ", srcFbo " + readFbo + ")");
            }
        }

        /**
         * Attach the main target's color texture to our persistent reader
         * FBO and return its id, or null when the color texture is not a GL
         * texture (non-GL backend — the caller falls back to the ambient
         * binding). Re-attached every frame so a main-target resize (new
         * texture object) can never leave a stale attachment bound.
         */
        private Integer bindWorldReader() {
            GpuTexture color = main.getColorTexture();
            if (!(color instanceof GlTexture gl)) return null;
            if (worldReaderFbo == 0) worldReaderFbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, worldReaderFbo);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, gl.glId(), 0);
            // Validate the attachment EVERY frame before any capture reads
            // from it: an incomplete read framebuffer makes glBlitFramebuffer
            // fail with GL_INVALID_FRAMEBUFFER_OPERATION and glReadPixels
            // return undefined memory — never blit from an unvalidated
            // source. (Complete-but-empty content is guarded earlier by the
            // no-level check in renderPanel.)
            int readerStatus = GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);
            if (readerStatus != GL30.GL_FRAMEBUFFER_COMPLETE) {
                if (!loggedReaderIncomplete) {
                    loggedReaderIncomplete = true;
                    AuroraClient.LOGGER.warn("[BlurPanel] world reader incomplete (0x{}) - glass declined",
                            Integer.toHexString(readerStatus));
                }
                return null;
            }
            if (!loggedWorldProbe) {
                // One-shot diagnostic: what did the reader actually see? The
                // probe pixel is read in GL bottom-up row order; reported in
                // screen (top-down) coordinates for readability.
                loggedWorldProbe = true;
                int px = Math.min(main.width - 1, x0 + panW / 2);
                int pyTop = Math.min(main.height - 1, y0 + panH / 2);
                ByteBuffer probe = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
                GL11.glReadPixels(px, main.height - 1 - pyTop, 1, 1,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, probe);
                AuroraClient.LOGGER.info("[BlurPanel] world capture probe: ambientFbo={} readerFbo={} mainTexGlId={}"
                                + " center=({},{}) rgba=({},{},{},{})",
                        srcFbo, worldReaderFbo, gl.glId(), px, pyTop,
                        probe.get(0) & 0xFF, probe.get(1) & 0xFF, probe.get(2) & 0xFF, probe.get(3) & 0xFF);
            }
            return worldReaderFbo;
        }

        // --- 2. Separable Gaussian at chain resolution: horizontal pass
        // into slot 1, vertical pass back into slot 0 (slot 0 is free
        // once the H pass has consumed the capture). Chain-space sigma
        // maps the requested device-pixel radius down to quarter res.
        private void blur() {
            float sigmaChain = Math.max(0.65f, radius * CHAIN_SCALE / 2.0f);
            runBlurPass(blurFbo[1], blurTex[0], cw, ch, 1f, 0f, sigmaChain);
            runBlurPass(blurFbo[0], blurTex[1], cw, ch, 0f, 1f, sigmaChain);
        }

        // --- 3. Composite: masked bilinear upscale of the blurred chain
        // into the panel-sized target. STRAIGHT alpha out; no tint and no
        // opacity in this pass (see class javadoc for both decisions).
        private void composite() {
            // Panel sub-rect in chain UV space, derived from the TRUE
            // capture-region size — NOT from CHAIN_SCALE. HISTORY: this was
            // originally (x0 - regX) * CHAIN_SCALE / cw, which silently
            // assumes cw == regW/4 exactly. cw is Math.round(regW *
            // CHAIN_SCALE), and regW = panW + 2*ceil(radius) is
            // RADIUS-DEPENDENT, so at radii where that rounding lands off
            // (e.g. regW 490 -> 122.5 -> 123) the sampled sub-rect was
            // scaled/offset by a fraction of a percent — enough for the
            // backdrop to read subtly compressed and shifted behind the
            // panel, appearing and disappearing as the radius changed the
            // rounding parity. The form below is exact for ANY cw: the
            // capture blit scales region edges-to-edges onto the chain, so
            // chain texel t center maps to region pixel
            // (t + 0.5) * regW / cw - 0.5, and region pixel p center
            // samples at chain uv (p + 0.5) / regW — exactly what
            // mix(uvRect, vUv) produces with these u/v values.
            float u0 = (x0 - regX) * (float) cw / regW;
            float v0 = (y0 - regY) * (float) ch / regH;
            float u1 = (x0 + panW - regX) * (float) cw / regW;
            float v1 = (y0 + panH - regY) * (float) ch / regH;
            float devRadius = Math.max(0f, Math.min(cornerRadiusGui * scale,
                    Math.min(panW, panH) / 2f - 1f));

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, compositeFbo);
            GL11.glViewport(0, 0, panW, panH);
            prepareFullscreenDraw();
            GL20.glUseProgram(compositeProgram);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, blurTex[0]);
            GL20.glUniform1i(uCompInput, 0);
            // Normalize the panel's sub-rect by the chain texture's ACTUAL
            // size (chainW/chainH), not this panel's logical cw/ch — the
            // textures are grow-only and shared, so cw/ch are sub-rect
            // extents, never the texture bounds. Dividing by cw/ch here made
            // every smaller panel sample the whole texture (the window's
            // capture) squished into it — the per-element UV bug.
            GL20.glUniform4f(uCompUvRect,
                    u0 / chainW, v0 / chainH, u1 / chainW, v1 / chainH);
            GL20.glUniform2f(uCompHalfSize, panW * 0.5f, panH * 0.5f);
            GL20.glUniform1f(uCompRadius, devRadius);
            // Lighting impression (gradient + outline only; see Lighting).
            // Zero strengths make both shader terms no-ops — one shader,
            // no variants. Stroke width is ~2 GUI px so the outline reads
            // bold at every scale. NOTE the Y negation: the light constants
            // are defined in SCREEN convention (+y down), but the shader's
            // p/uv space is FBO-oriented (+y up) — without this flip the
            // light would come from the bottom-left.
            GL20.glUniform2f(uLight, LIGHT_DIR_X, -LIGHT_DIR_Y);
            GL20.glUniform1f(uEdgeWidth, Math.max(3f, 2f * scale));
            GL20.glUniform1f(uEdgeStrength, lighting != null ? lighting.edgeStrength : 0f);
            GL20.glUniform1f(uGradStrength, lighting != null ? lighting.gradStrength : 0f);
            GL20.glUniform1f(uDepressed, lighting != null && lighting.depressed ? 1f : 0f);
            // In-glass rim retirement: follows Background Opacity — as the
            // fill takes over the read, the glass-embedded rim blends into
            // the panel base. The rim that stays VISIBLE at high opacity is
            // the over-fill pastel border drawn by {@link #drawRimFinish}
            // (this texture is occluded by the opaque fill there). Both
            // values ride the cached ResolvedTheme; these are cached field
            // reads per panel, and NOT opacity application points — no
            // alpha in this pipeline is multiplied by them.
            GL20.glUniform1f(uRimBlend,
                    (float) ThemeManager.current().backgroundOpacity());
            GL30.glBindVertexArray(emptyVao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        }

        // --- 4. Read back (GL rows are bottom-up) into the top-down
        // NativeImage, converting RGBA bytes to ARGB ints. The staging
        // buffer is CACHED per capacity: allocating/freeing ~0.5-2 MB of
        // native memory every frame churns the heap concurrently with the
        // game's own async buffer work (screenshot grabs map GPU buffers
        // from other threads), which we measured as a crash trigger.
        private void readback() {
            ensureOutputTexture(out, panW, panH);
            int cap = panW * panH * 4;
            if (readBuffer == null || readBuffer.capacity() < cap) {
                readBuffer = ByteBuffer.allocateDirect(cap).order(ByteOrder.nativeOrder());
            }
            final boolean perf = GlassStats.ENABLED;
            long t = perf ? System.nanoTime() : 0L;
            readBuffer.clear();
            GL11.glReadPixels(0, 0, panW, panH, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, readBuffer);
            ByteBuffer buf = readBuffer;
            buf.rewind();
            if (perf) {
                GlassStats.readGlNs += System.nanoTime() - t;
                GlassStats.readPixels += (long) panW * panH;
                t = System.nanoTime();
            }
            for (int row = 0; row < panH; row++) {
                int base = (panH - 1 - row) * panW * 4; // flip to top-down
                for (int col = 0; col < panW; col++) {
                    int i = base + col * 4;
                    int r = buf.get(i) & 0xFF;
                    int gg = buf.get(i + 1) & 0xFF;
                    int b = buf.get(i + 2) & 0xFF;
                    int a = buf.get(i + 3) & 0xFF;
                    out.image.setPixel(col, row, (a << 24) | (r << 16) | (gg << 8) | b);
                }
            }
            if (perf) { GlassStats.readLoopNs += System.nanoTime() - t; t = System.nanoTime(); }
            out.texture.upload();
            if (perf) { GlassStats.uploadNs += System.nanoTime() - t; }
        }

        // --- 5. Blit through the normal GUI path (straight-alpha texture,
        // standard src-alpha GUI blending) so the panel composites in
        // correct draw order — the same mechanism UiLayerCache uses.
        private void blit(GuiGraphics g, float guiX, float guiY, float guiW, float guiH) {
            // The quad is placed at the panel's CLIPPED device rect mapped
            // back to GUI units and sized to the clipped texture — a panel
            // partially offscreen (e.g. a long scrolled settings window)
            // then shows exactly its visible part instead of stretching the
            // clipped texture over the full requested rect. For unclipped
            // panels (integer GUI coords, integer scale) these values are
            // bit-identical to the previous (int)guiX/(int)guiW form that
            // the alignment verification depended on.
            g.blit(RenderPipelines.GUI_TEXTURED, out.id,
                    Math.round(x0 / (float) scale), Math.round(y0 / (float) scale), 0f, 0f,
                    Math.round(panW / (float) scale), Math.round(panH / (float) scale),
                    panW, panH, panW, panH, -1);
        }

    }

    /** Releases GPU/texture resources. Idempotent; safe on shutdown/reload. */
    public static void shutdown() {
        releaseOutputs();
        releaseRimMasks();
        deleteChain();
        if (worldReaderFbo != 0) { GL30.glDeleteFramebuffers(worldReaderFbo); worldReaderFbo = 0; }
        if (compositeFbo != 0) { GL30.glDeleteFramebuffers(compositeFbo); compositeFbo = 0; }
        if (compositeTex != 0) { GL11.glDeleteTextures(compositeTex); compositeTex = 0; }
        if (blurProgram != 0) { GL20.glDeleteProgram(blurProgram); blurProgram = 0; }
        if (compositeProgram != 0) { GL20.glDeleteProgram(compositeProgram); compositeProgram = 0; }
        if (emptyVao != 0) { GL30.glDeleteVertexArrays(emptyVao); emptyVao = 0; }
        initialized = false;
        permanentlyDisabled = false;
    }

    // ==================================================================================
    // GL plumbing
    // ==================================================================================

    private static boolean ensureReady() {
        if (initialized) return blurProgram != 0;
        initialized = true;
        try {
            blurProgram = createProgram(FULLSCREEN_VS, BLUR_FS);
            compositeProgram = createProgram(FULLSCREEN_VS, COMPOSITE_FS);
            uBlurInput = GL20.glGetUniformLocation(blurProgram, "uInput");
            uBlurDir = GL20.glGetUniformLocation(blurProgram, "uDir");
            uBlurTexel = GL20.glGetUniformLocation(blurProgram, "uTexel");
            uBlurScale = GL20.glGetUniformLocation(blurProgram, "uScale");
            uBlurSigma = GL20.glGetUniformLocation(blurProgram, "uSigma");
            uCompInput = GL20.glGetUniformLocation(compositeProgram, "uInput");
            uCompUvRect = GL20.glGetUniformLocation(compositeProgram, "uUvRect");
            uCompHalfSize = GL20.glGetUniformLocation(compositeProgram, "uHalfSize");
            uCompRadius = GL20.glGetUniformLocation(compositeProgram, "uRadius");
            uLight = GL20.glGetUniformLocation(compositeProgram, "uLight");
            uEdgeWidth = GL20.glGetUniformLocation(compositeProgram, "uEdgeWidth");
            uEdgeStrength = GL20.glGetUniformLocation(compositeProgram, "uEdgeStrength");
            uGradStrength = GL20.glGetUniformLocation(compositeProgram, "uGradStrength");
            uDepressed = GL20.glGetUniformLocation(compositeProgram, "uDepressed");
            uRimBlend = GL20.glGetUniformLocation(compositeProgram, "uRimBlend");
            emptyVao = GL30.glGenVertexArrays();
            AuroraClient.LOGGER.info("[BlurPanel] programs ready (blur={}, composite={}).", blurProgram, compositeProgram);
            return true;
        } catch (Throwable t) {
            AuroraClient.LOGGER.error("[BlurPanel] shader compile failed - blur panels disabled.", t);
            blurProgram = 0;
            compositeProgram = 0;
            return false;
        }
    }

    /**
     * Standard state for our fullscreen passes: blending off, depth test
     * and face culling off (MC's pipelines may leave culling or depth
     * enabled in a direction that would swallow an attributeless
     * fullscreen triangle), and the depth mask left untouched (our FBOs
     * have no depth attachment anyway).
     */
    private static void prepareFullscreenDraw() {
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);   // MC's GUI leaves scissor rects active
        GL11.glColorMask(true, true, true, true); // and occasionally masked color writes
        // MC's render system uses separate GL sampler objects; a leftover
        // sampler on unit 0 makes our manually-bound textures sample black.
        GL33.glBindSampler(0, 0);
    }

    private static void ensureChainTextures(int w, int h) {
        // GROW-ONLY capacity: panels of many different sizes run per frame
        // (window, card, buttons, segments, toggle, chip…), sequentially, and
        // each pass viewports/blits into just its own (0,0,w,h) sub-rect —
        // the attachment only needs to be big enough. Reallocating on every
        // size change (the old equality check) meant ~2 chain textures
        // deleted+re-created per PANEL per FRAME (~26+/frame with the full
        // Theme screen treatment) — the same churn class as the output-pool
        // flicker bug. Capacity shrinks never; shutdown frees.
        if (w <= chainW && h <= chainH && blurFbo[0] != 0) return;
        deleteChain();
        for (int i = 0; i < 2; i++) {
            blurTex[i] = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, blurTex[i]);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
            blurFbo[i] = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, blurFbo[i]);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, blurTex[i], 0);
        }
        chainW = w;
        chainH = h;
    }

    private static void deleteChain() {
        for (int i = 0; i < 2; i++) {
            if (blurFbo[i] != 0) { GL30.glDeleteFramebuffers(blurFbo[i]); blurFbo[i] = 0; }
            if (blurTex[i] != 0) { GL11.glDeleteTextures(blurTex[i]); blurTex[i] = 0; }
        }
        chainW = 0;
        chainH = 0;
    }

    private static void ensureCompositeTarget(int w, int h) {
        // GROW-ONLY capacity — same rationale as ensureChainTextures: one
        // shared sequential target, sub-rect viewport per panel, no per-size
        // reallocation churn.
        if (w <= outW && h <= outH && compositeFbo != 0) return;
        if (compositeFbo != 0) GL30.glDeleteFramebuffers(compositeFbo);
        if (compositeTex != 0) GL11.glDeleteTextures(compositeTex);
        compositeTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, compositeTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        compositeFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, compositeFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, compositeTex, 0);
        outW = w;
        outH = h;
    }

    private static void runBlurPass(int targetFbo, int inputTex, int w, int h,
                                    float dirX, float dirY, float sigma) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFbo);
        GL11.glViewport(0, 0, w, h);
        prepareFullscreenDraw();
        GL20.glUseProgram(blurProgram);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, inputTex);
        GL20.glUniform1i(uBlurInput, 0);
        GL20.glUniform2f(uBlurDir, dirX, dirY);
        // Sub-rect contract (see BLUR_FS): texel = one REAL texture texel;
        // scale confines the pass to this panel's (w x h) sub-rect of the
        // grow-only shared chain texture. When w==chainW both reduce to the
        // historical exact-size behavior.
        GL20.glUniform2f(uBlurTexel, 1f / chainW, 1f / chainH);
        GL20.glUniform2f(uBlurScale, w / (float) chainW, h / (float) chainH);
        GL20.glUniform1f(uBlurSigma, sigma);
        GL30.glBindVertexArray(emptyVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    /**
     * Next pooled output slot for this renderPanel call. Slots are indexed
     * by an EXACT frame epoch stamped by {@link #beginFrame()} (called once
     * per frame from the client render-tick mixin, before any GUI
     * rendering): the first call of a new epoch claims slot 0, later calls
     * of the SAME epoch advance monotonically — concurrent panels in a
     * frame always claim distinct slots, and no call can claim a slot whose
     * recorded blit is still pending.
     *
     * <p>HISTORY — do not replace the epoch with a timing heuristic: the
     * original &gt;1ms-gap detection assumed intra-frame calls sit under 1ms
     * apart, but one panel's capture+blur+composite+CPU readback legitimately
     * takes longer, which reset the cursor MID-frame — every panel then
     * shared slot 0 and each call released+reallocated the texture the
     * previous call's recorded blit still referenced; the driver served
     * those dangling references from recycled texture names (measured:
     * resets == panels &gt;&gt; fps, allocs == releases == panels; visible
     * as panels showing flat black / arbitrary VRAM instead of their
     * blurred backdrop, and as corner-style-correlated flicker when only
     * some calls crossed the threshold). A 250 ms staleness reset remains
     * as a safety net for any path that renders without the epoch signal;
     * exhausting the pool within one epoch declines the extra panel (one
     * frame of opaque fallback) rather than stomping a live slot.
     *
     * <p>OVER-SUBSCRIPTION (R10): which panel is declined is a
     * {@link Priority} decision, not a render-order accident. Rendering is
     * immediate-mode, so a panel cannot know how many more important
     * panels the frame still has to draw after it; the forecast is LAST
     * frame's per-tier demand ({@link #demandLastFrame} — every call that
     * reached this point, declined or not), which is stable frame to frame
     * on a static screen. A claim of tier {@code t} is refused while
     * {@code claimsThisFrame + reserved >= OUTPUT_POOL}, where
     * {@code reserved} is the sum over every HIGHER tier of
     * {@code max(0, demandLastFrame - claimedThisFrame)} — the slots those
     * tiers are still expected to take. The reservation shrinks as the
     * higher tiers claim, so an over-estimate never strands a slot for
     * long, and within a tier the pool stays first-come (rows degrade from
     * the bottom of the list up). The first frame of a screen (no
     * forecast) and any frame whose total demand fits the pool behave
     * exactly as before: the rule only refuses a claim when the pool is
     * genuinely short. The slot ASSIGNMENT (round-robin cursor) is
     * unchanged; only admission is.
     */
    /**
     * Empties the GL error queue so a later {@code glGetError} can only
     * report errors raised after this point. Bounded so a driver that keeps
     * returning an error can never spin the render thread.
     */
    private static void drainGlErrors() {
        for (int i = 0; i < 64; i++) {
            if (GL11.glGetError() == GL11.GL_NO_ERROR) return;
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

    /**
     * Exact frame-boundary signal for the output pool (see
     * {@link #nextOutput}) — called once per frame from the client
     * render-tick mixin, before any GUI rendering. Deliberately trivial.
     */
    public static void beginFrame() {
        GlassStats.flushFrame();
        // Drain queued rim-mask evictions FIRST. Everything in the queue was
        // last blitted in an epoch older than the frame that just finished,
        // so no GuiRenderState can still reference it (see
        // evictColdRimMasks). Destroying them anywhere inside a frame is the
        // bug this ordering exists to prevent.
        if (!pendingRimRelease.isEmpty()) {
            for (RimMask mask : pendingRimRelease) destroyRimMask(mask);
            pendingRimRelease.clear();
        }
        rollDemand();
        poolEpoch++;
    }

    /**
     * Frame boundary for the over-subscription forecast: the frame that
     * just finished becomes {@link #demandLastFrame}. A frame with no
     * panels forecasts zero, so the next screen's first frame is plain
     * first-come (there is nothing better to forecast from).
     */
    private static void rollDemand() {
        System.arraycopy(demandThisFrame, 0, demandLastFrame, 0, Priority.COUNT);
        java.util.Arrays.fill(demandThisFrame, 0);
    }

    /**
     * Non-error decline: {@code renderPanel} returns false and the caller
     * draws its opaque fallback for this panel/frame. Thrown for conditions
     * that are expected to be transient (reader unavailable, pool exhausted
     * for the frame) — never a defect and never a reason to disable the
     * renderer.
     */
    private static final class Declined extends RuntimeException {
        Declined(String msg) { super(msg); }
    }

    private static void ensureOutputTexture(PanelOutput out, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getTextureManager() == null) throw new IllegalStateException("no TextureManager");
        if (out.image != null && w == out.w && h == out.h && out.texture != null) {
            return; // reuse; pixels are rewritten every frame anyway
        }
        releaseOutputTexture(out);
        out.image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        long id = SEQ.incrementAndGet();
        out.id = Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "blur_panel/" + id);
        out.texture = new DynamicTexture(() -> "aurora_blur_panel_" + id, out.image);
        mc.getTextureManager().register(out.id, out.texture);
        out.w = w;
        out.h = h;
    }

    private static void releaseOutputTexture(PanelOutput out) {
        if (out.texture != null) {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.getTextureManager() != null && out.id != null) {
                    mc.getTextureManager().release(out.id);
                }
            } catch (Throwable ignored) {
            }
            try { out.texture.close(); } catch (Throwable ignored) { }
            out.texture = null;
            out.id = null;
        }
        if (out.image != null) {
            try { out.image.close(); } catch (Throwable ignored) { }
            out.image = null;
        }
        out.w = -1;
        out.h = -1;
    }

    /** Releases every pooled output texture (shutdown / resource reload). */
    private static void releaseOutputs() {
        for (PanelOutput out : outputs) {
            if (out != null) releaseOutputTexture(out);
        }
    }

    private static int createProgram(String vsSrc, String fsSrc) {
        int vs = compile(GL20.GL_VERTEX_SHADER, vsSrc);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, fsSrc);
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("link failed: " + GL20.glGetProgramInfoLog(p, 4096));
        }
        GL20.glDetachShader(p, vs);
        GL20.glDetachShader(p, fs);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        return p;
    }

    private static int compile(int type, String src) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("compile failed: " + GL20.glGetShaderInfoLog(s, 4096));
        }
        return s;
    }

    // ==================================================================================
    // Shaders (inline by choice — no resource files, no loading path to go stale)
    // ==================================================================================

    /** Attribute-less fullscreen triangle; vUv spans (0,0)..(1,1). */
    private static final String FULLSCREEN_VS = """
            #version 150
            out vec2 vUv;
            void main() {
                vec2 pos = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                vUv = pos;
                gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    /**
     * One separable Gaussian pass. 9 taps at 1-pixel spacing, per-tap
     * weights computed from {@code uSigma} and normalized in-shader — a
     * convex weighted average, so RGBA8 intermediates are sufficient
     * (see the class javadoc's precision note).
     *
     * <p><b>Sub-rect contract:</b> the chain textures are GROW-ONLY and
     * shared by every panel size in a frame, so one panel's capture occupies
     * only the top-left {@code (logicalW x logicalH)} sub-rect of the
     * texture. {@code uScale} = logical/texture size confines {@code vUv} to
     * exactly that sub-rect, and {@code uTexel} is the size of one REAL
     * texture texel. Without this, a small panel's [0..1] UV sweep samples
     * the whole texture — the largest panel's capture squished into the
     * small element (the UV bug this contract exists to prevent).
     */
    private static final String BLUR_FS = """
            #version 150
            in vec2 vUv;
            out vec4 fragColor;
            uniform sampler2D uInput;
            uniform vec2 uDir;      // (1,0) horizontal pass, (0,1) vertical pass
            uniform vec2 uTexel;    // 1/width, 1/height of the TEXTURE (not the logical sub-rect)
            uniform vec2 uScale;    // sub-rect scale = logical size / texture size
            uniform float uSigma;
            void main() {
                vec2 base = vUv * uScale;
                vec3 sum = vec3(0.0);
                float wsum = 0.0;
                for (int t = -4; t <= 4; ++t) {
                    float w = exp(-0.5 * (float(t) * float(t)) / (uSigma * uSigma));
                    sum += texture(uInput, base + uDir * uTexel * float(t)).rgb * w;
                    wsum += w;
                }
                fragColor = vec4(sum / wsum, 1.0);
            }
            """;

    /**
     * Composite: bilinear upscale of the blurred chain into the panel,
     * masked by an exact rounded-rect SDF with a 1-pixel antialiased edge,
     * plus the two lighting-impression terms (see {@link Lighting}).
     *
     * <p><b>ALPHA CONVENTION — the single definition point:</b> the output
     * is <b>STRAIGHT (non-premultiplied) alpha</b> — RGB is the blurred
     * backdrop color with the lighting terms baked in, A is the shape
     * coverage. It is uploaded as-is and blended by the standard GUI
     * src-alpha/1-minus-src-alpha path. If you change this line's
     * convention you MUST change the upload/blit side to match; they are
     * documented as a pair.
     *
     * <p>No tint, no opacity, and nothing beyond the two declared lighting
     * terms here — no SDF-normal reconstruction, no specular exponent, no
     * refraction/distortion. The tint (carrying the theme's Background
     * Opacity at its ONE application point) is drawn by the caller as a
     * separate translucent fill on top of this texture.
     */
    private static final String COMPOSITE_FS = """
            #version 150
            in vec2 vUv;
            out vec4 fragColor;
            uniform sampler2D uInput;
            uniform vec4 uUvRect;   // panel's sub-rect in chain UV space
            uniform vec2 uHalfSize; // panel half-size in device pixels
            uniform float uRadius;  // corner radius in device pixels
            // ---- Lighting impression: gradient + outline ONLY. Plain
            // ---- smoothstep/lerp math; no normals, no specular, no refraction.
            uniform vec2 uLight;         // unit vector TOWARD the light (+y is UP in this FBO-oriented space)
            uniform float uEdgeWidth;    // border stroke width, device px (~2 GUI px)
            uniform float uEdgeStrength; // 0 disables the border stroke term
            uniform float uGradStrength; // 0 disables the face gradient term
            uniform float uDepressed;    // 0 = raised, 1 = depressed (both terms inverted)
            uniform float uRimBlend;     // 0 = full glass rim .. 1 = rim retired into the panel base (Background Opacity)
            void main() {
                vec2 uv = mix(uUvRect.xy, uUvRect.zw, vUv);
                vec3 blurred = texture(uInput, uv).rgb;
                vec2 p = (vUv - 0.5) * 2.0 * uHalfSize;       // device px from center
                vec2 q = abs(p) - (uHalfSize - uRadius);
                float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - uRadius;
                float coverage = clamp(0.5 - d, 0.0, 1.0);    // ~1px AA edge

                vec3 lit = blurred;

                // ELEMENT 2 (applied first, so it sits UNDER the stroke):
                // full-surface directional gradient — a plain two-point
                // lerp along the light axis. t runs 0 at the far corner to
                // 1 at the light-facing corner; depressed swaps bright/dark.
                float t = clamp(dot(p / uHalfSize, uLight) * 0.5 + 0.5, 0.0, 1.0);
                float g = (mix(t, 1.0 - t, uDepressed) - 0.5) * 2.0;   // -1 .. +1
                lit += vec3(g * uGradStrength);

                // ELEMENT 1: directional border stroke — a fixed-width band
                // cut from the SAME rounded-rect distance the mask already
                // computed (no normal reconstruction). edgeAmt is a simple
                // smoothstep of how much this border point faces the light.
                //   raised:    bright stroke toward the light, fading to
                //              nothing on the opposite edge.
                //   depressed: shadow toward the light (the recess blocks
                //              direct light there) + a subtle (0.45x)
                //              highlight on the far edge.
                // Stroke width is a UNIFORM (~2 GUI px) so it stays fixed
                // in GUI units at every scale and panel size.
                //
                // HISTORY — do not "simplify" this line: an earlier version
                // read (1.0 - smoothstep(-w, 0.0, d)), which is 1 across the
                // ENTIRE interior (d is negative inside the shape) and only
                // fades OUTSIDE — i.e. it washed the whole panel face with
                // directional falloff instead of drawing a border stroke.
                // That single inversion caused every "lighting too strong /
                // gradient huge" measurement; the width value was never the
                // problem. Correct band: 0 in the deep interior, rising to 1
                // at the shape edge (d in [-w, 0]), 0-alpha outside.
                float band = smoothstep(-uEdgeWidth, 0.0, d) * coverage;
                float plen = length(p);
                float facing = (plen > 0.5) ? dot(p, uLight) / plen : 0.0; // -1 far .. +1 light
                float edgeAmt = smoothstep(-0.35, 0.55, facing);
                // The stroke is a TARGET-COLOR composite. At full glass
                // (uRimBlend = 0) the band is pulled toward a distinct
                // light/shadow color — the established translucent look. As
                // Background Opacity rises the TARGET COLOR blends toward
                // the panel's OWN per-pixel base (litBase), so the in-glass
                // rim gently retires as the fill takes over the read. The
                // rim that stays VISIBLE at high opacity is drawn ABOVE the
                // caller's fill by drawRimFinish (the caller's fill is
                // opaque there and would occlude anything in this texture).
                // (uEdgeStrength lives in the TARGET, not the weight, so at
                // uRimBlend = 0 this is bit-equivalent to the old
                // `lit += band * edgeAmt * uEdgeStrength` form.)
                vec3 litBase = lit;
                if (uDepressed < 0.5) {
                    vec3 hi = clamp(litBase + vec3(uEdgeStrength), 0.0, 1.0);
                    lit = mix(litBase, mix(hi, litBase, uRimBlend), band * edgeAmt);
                } else {
                    vec3 lo = clamp(litBase - vec3(uEdgeStrength), 0.0, 1.0);
                    vec3 far = clamp(litBase + vec3(uEdgeStrength * 0.45), 0.0, 1.0);
                    lit = mix(litBase, mix(lo, litBase, uRimBlend), band * edgeAmt);
                    lit = mix(lit, mix(far, litBase, uRimBlend), band * (1.0 - edgeAmt));
                }

                lit = clamp(lit, 0.0, 1.0);
                // STRAIGHT alpha out (see the convention comment above).
                fragColor = vec4(lit, coverage);
            }
            """;

    // ==================================================================================
    // GL state save/restore — raw GL must never leak state into MC's renderer
    // ==================================================================================

    /**
     * Point-in-time GL state capture/restore for the immediate-mode passes.
     * Package-visible: {@link BlurTestScreen}'s synthetic-backdrop pass uses
     * the same hygiene (see HISTORY there — a blend-state leak from that pass
     * once made the whole end-of-frame GUI batch flush render opaque).
     *
     * <p>Instances are POOLED: dense screens run {@code renderPanel} ~30× per
     * frame, and each save used to allocate a fresh direct ByteBuffer for the
     * color-write mask (plus an int[4]) — ~2000 direct-buffer allocations per
     * second on the pack browser, pure churn for four bytes of state. All
     * users are strictly sequential on the render thread (save … restore,
     * never nested across panels), so a free-list is safe; a user that fails
     * to restore simply leaks one instance back to GC, never to another
     * caller.
     */
    static final class SavedGlState {
        private static final java.util.ArrayDeque<SavedGlState> POOL = new java.util.ArrayDeque<>();
        int program;
        int texture;
        int readFbo;
        int drawFbo;
        int vao;
        final int[] viewport = new int[4];
        final java.nio.ByteBuffer colorMask = java.nio.ByteBuffer.allocateDirect(4);
        boolean blend;
        boolean depthTest;
        boolean cullFace;
        boolean scissorTest;
        int sampler0;

        private SavedGlState() {}

        private void capture() {
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            colorMask.clear();
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
            blend = GL11.glGetBoolean(GL11.GL_BLEND);
            depthTest = GL11.glGetBoolean(GL11.GL_DEPTH_TEST);
            cullFace = GL11.glGetBoolean(GL11.GL_CULL_FACE);
            scissorTest = GL11.glGetBoolean(GL11.GL_SCISSOR_TEST);
            sampler0 = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        }

        static SavedGlState save() {
            SavedGlState s = POOL.pollFirst();
            if (s == null) s = new SavedGlState();
            s.capture();
            return s;
        }

        void restore() {
            GL20.glUseProgram(program);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL30.glBindVertexArray(vao);
            if (blend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
            if (depthTest) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (cullFace) GL11.glEnable(GL11.GL_CULL_FACE); else GL11.glDisable(GL11.GL_CULL_FACE);
            if (scissorTest) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL33.glBindSampler(0, sampler0);
            GL11.glColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0,
                    colorMask.get(2) != 0, colorMask.get(3) != 0);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            POOL.addLast(this);
        }
    }





}
