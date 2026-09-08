package com.aurora.client.util;

import java.util.function.DoubleSupplier;

/**
 * The one shared smooth-scroll state machine (audit R2): a target-based
 * exponential lerp over wall-clock frame deltas, plus the optional
 * draggable-scrollbar-thumb geometry/drag state. Four screens shipped four
 * private copies of this math before this class existed; new scrolling
 * surfaces use it instead of growing a fifth.
 *
 * <h2>The motion model</h2>
 *
 * <p>Input (wheel, thumb drag, programmatic) moves a <b>target</b>; the
 * rendered position ({@link #current()}) approaches it every frame by
 * {@code alpha = 1 - e^(-dt/τ)} with dt the real elapsed wall-clock time —
 * the frame-rate-independent pattern used across Aurora's screens, so the
 * glide is identical at 60/120/240 Hz. The first frame after construction
 * (or {@link #resetClock()}) uses dt = 16 ms; later frames clamp dt at
 * 80 ms so a pause (window drag, breakpoint, GC) can't produce a huge
 * jump. When the remaining distance drops under the snap epsilon the
 * position locks exactly onto the target (keeps rest-state reads clean
 * and idle frames math-free for the caller's own short-circuits).
 *
 * <h2>τ is owned by the screen, not by this class</h2>
 *
 * <p>Every screen keeps its pre-consolidation feel constant — R2 is a
 * deduplication of the math, deliberately NOT a standardization of the
 * tuning. Pass a fixed τ ({@link #SmoothScroll(double)}) or a supplier
 * ({@link #SmoothScroll(DoubleSupplier)}) for adaptive tables such as
 * AuroraScreen's fps-keyed one. Same for the snap epsilon
 * ({@link #setSnapEpsilon(double)}; the historical values are 0.25 on the
 * detail screen and pack browser, 0.1 on AuroraScreen) and the wheel step,
 * which is a per-call argument for the same reason.
 *
 * <h2>maxScroll is a parameter, not state</h2>
 *
 * <p>Content-height math is bespoke per screen (setting rows, card grids,
 * profile rows) and is sometimes computed differently at input time than
 * at render time — FeatureDetailScreen's input-time bound is one trailing
 * row-gap larger than its render-time one; {@link #advance} re-clamps the
 * target against the render-time bound every frame, so that historical
 * slack is harmless and preserved. Callers pass their own bound to
 * {@link #advance} and {@link #wheel}.
 *
 * <h2>The thumb is opt-in</h2>
 *
 * <p>Screens with no visible scrollbar (FeatureDetailScreen today,
 * ProfileManagerScreen/WaypointManagerScreen) simply never call the thumb
 * methods. Screens with one get the shared geometry (thumb height from the
 * view/content ratio, minimum height, y position) and the shared drag
 * state machine; the <b>painting stays with the screen</b> because the two
 * shipped thumbs differ in color source, width and hover semantics — a
 * shared painter would silently change one of them. Center-on-cursor
 * dragging (the pack browser's semantics) is grab-offset dragging with the
 * grab offset fixed at {@code thumbHeight/2}, so one code path covers both
 * it and AuroraScreen's grab-where-clicked semantics; pass
 * {@code jumpCurrent=true} to pin the rendered position during drags (pack
 * browser) or {@code false} to keep easing toward the moving target
 * (AuroraScreen).
 *
 * <p>This class deliberately imports no Minecraft classes — the easing
 * core is plain double math (only the clock and τ are suppliers), which
 * keeps it compilable and differentially testable against a reference
 * implementation with a standalone {@code javac}.
 *
 * <h2>Rollout status</h2>
 *
 * <ul>
 *   <li><b>Piloted</b> on {@code FeatureDetailScreen} (2026-09-08, R2):
 *       fixed τ = 60 ms, snap 0.25, wheel step 30, no thumb.</li>
 *   <li>{@code AuroraScreen} (2026-09-08, R2): fps-adaptive τ table,
 *       snap 0.1, wheel step 30, grab-where-clicked thumb that keeps
 *       easing during drags; one instance per tab, the inactive one's
 *       clock stamped via {@link #touchClock()}.</li>
 *   <li>{@code ResourcePackBrowserScreen} (2026-09-08, R2): two tracks
 *       (grid + sidebar), τ = 80, snap 0.25, wheel steps 40/28, both
 *       thumbs center-on-cursor with the rendered position pinned during
 *       drags; accepted the component's 80 ms dt clamp over the screen's
 *       old 64 (decided — sub-15fps-only observable).</li>
 *   <li><b>Pending, deliberately not folded into the pilot</b>:
 *       {@code ProfileManagerScreen}/{@code WaypointManagerScreen}, which
 *       currently have NO easing and NO thumb at all — adopting this class
 *       there is a functional improvement, not a refactor, and deserves
 *       its own explicit decision rather than a silent behavior change.</li>
 * </ul>
 */
public final class SmoothScroll {

    /** First-frame dt (ms) before a clock reading exists. */
    private static final double FIRST_FRAME_DT_MS = 16.0;
    /** dt clamp (ms) — the historical value on the two nanoTime screens. */
    private static final double MAX_FRAME_DT_MS = 80.0;

    private double current = 0.0;
    private double target = 0.0;
    /** Wall-clock millis of the previous advance; 0 = next frame is the first. */
    private double lastTickMs = 0.0;

    private final DoubleSupplier tauMs;
    private double snapEpsilon = 0.25;

    /** Thumb drag state; grabOffsetY = cursor Y minus thumb top when the drag began. */
    private boolean dragging = false;
    private double grabOffsetY = 0.0;

    /** Fixed-τ construction (FeatureDetailScreen-style). */
    public SmoothScroll(double tauMs) {
        this(() -> tauMs);
    }

    /**
     * Adaptive-τ construction (e.g. AuroraScreen's fps-keyed table). The
     * supplier is read once per {@link #advance}, so a per-frame fps lookup
     * works unchanged.
     */
    public SmoothScroll(DoubleSupplier tauMs) {
        this.tauMs = tauMs;
    }

    /**
     * Distance under which the position snaps exactly onto the target.
     * Historical values: 0.25 (detail screen, pack browser), 0.1
     * (AuroraScreen). Chainable.
     */
    public SmoothScroll setSnapEpsilon(double eps) {
        this.snapEpsilon = eps;
        return this;
    }

    public double current() { return current; }
    public double target() { return target; }

    /** Jumps position and target to {@code v} (no easing). Unclamped — caller's business. */
    public void set(double v) {
        current = target = v;
    }

    /**
     * Zeroes the frame clock so the next {@link #advance} uses the nominal
     * first-frame dt instead of the elapsed-since-last-advance value
     * (AuroraScreen does this from {@code init()}; a fresh instance gets it
     * for free, an instance surviving a screen re-init does not).
     */
    public void resetClock() {
        lastTickMs = 0.0;
    }

    /**
     * Stamps the frame clock WITHOUT moving the position — for multi-track
     * screens whose inactive track stays frozen but must resume with a
     * fresh dt (AuroraScreen stamps its inactive tab's instance every
     * frame, reproducing its old single shared clock). Injectable-clock
     * variant for differential testing.
     */
    void touchClock(double nowMs) {
        lastTickMs = nowMs;
    }

    /** {@link #touchClock(double)} against the real clock. */
    public void touchClock() {
        touchClock(System.nanoTime() / 1_000_000.0);
    }

    /**
     * Advances the easing one frame against the wall clock. Clamps the
     * target into {@code [0, maxScroll]} first (input-time bounds may be
     * staler/larger — see class javadoc), then moves the current position
     * by the τ-weighted exponential factor and snaps once close enough.
     *
     * @return the new {@link #current()}
     */
    public double advance(double maxScroll) {
        return advance(maxScroll, System.nanoTime() / 1_000_000.0);
    }

    /**
     * Injectable-clock variant of {@link #advance(double)} for differential
     * testing against a reference implementation over scripted frame
     * timelines. {@code nowMs} must be monotonic across calls.
     */
    double advance(double maxScroll, double nowMs) {
        double dt = (lastTickMs == 0.0) ? FIRST_FRAME_DT_MS : Math.min(MAX_FRAME_DT_MS, nowMs - lastTickMs);
        lastTickMs = nowMs;

        if (target < 0) target = 0;
        if (target > maxScroll) target = maxScroll;

        double alpha = 1.0 - Math.exp(-dt / tauMs.getAsDouble());
        double diff = target - current;
        current += diff * alpha;

        if (Math.abs(diff) < snapEpsilon) {
            current = target;
        }
        return current;
    }

    /**
     * Wheel input: moves the target {@code -vertical * step} pixels and
     * clamps it into {@code [0, maxScroll]}. The step stays a per-call
     * argument (historical values: 30 on three screens, 28/40 on the pack
     * browser's sidebar/grid) — this class does not own tuning.
     *
     * @return the new {@link #target()}
     */
    public double wheel(double vertical, double step, double maxScroll) {
        target -= vertical * step;
        if (target < 0) target = 0;
        if (target > maxScroll) target = maxScroll;
        return target;
    }

    // ------------------------------------------------------------------
    //  Optional scrollbar thumb: geometry + drag state machine
    // ------------------------------------------------------------------

    /** Scroll progress {@code current / maxScroll} clamped to [0, 1]. */
    public double ratio(double maxScroll) {
        if (maxScroll <= 0) return 0.0;
        double r = current / maxScroll;
        return r < 0.0 ? 0.0 : (r > 1.0 ? 1.0 : r);
    }

    /**
     * Thumb height for a track of {@code trackH} pixels: the viewport's
     * fraction of the total content height ({@code trackH / (trackH +
     * maxScroll)}), floored at {@code minThumbH} — the formula all shipped
     * thumbs use.
     */
    public double thumbHeight(double trackH, double maxScroll, double minThumbH) {
        double viewRatio = trackH / (trackH + maxScroll);
        return Math.max(minThumbH, trackH * viewRatio);
    }

    /** Thumb top Y for a track spanning [{@code trackY}, {@code trackY} + {@code trackH}]. */
    public double thumbY(double trackY, double trackH, double maxScroll, double minThumbH) {
        double thumbH = thumbHeight(trackH, maxScroll, minThumbH);
        return trackY + (trackH - thumbH) * ratio(maxScroll);
    }

    public boolean isDragging() { return dragging; }

    /**
     * Starts a thumb drag. {@code grabOffsetY} is the cursor's distance from
     * the thumb's top at grab time ({@code mouseY - thumbY(...)}) for
     * grab-where-clicked semantics, or {@code thumbHeight(...) / 2} for
     * center-on-cursor semantics — both shipped variants are the same math.
     * Hit-testing is the screen's business (hit zones differ per screen).
     */
    public void beginThumbDrag(double grabOffsetY) {
        dragging = true;
        this.grabOffsetY = grabOffsetY;
    }

    /**
     * Maps an absolute cursor Y to a scroll value and applies it while a
     * drag is active. With {@code jumpCurrent=false} only the target moves
     * and the rendered position keeps easing toward it (AuroraScreen's
     * feel); with {@code true} position and target both jump — no easing
     * while dragging (the pack browser's feel).
     */
    public void dragThumb(double mouseY, double trackY, double trackH, double maxScroll,
                          double minThumbH, boolean jumpCurrent) {
        double thumbH = thumbHeight(trackH, maxScroll, minThumbH);
        double thumbTop = mouseY - grabOffsetY;
        double v = (thumbTop - trackY) / Math.max(1.0, trackH - thumbH) * maxScroll;
        if (v < 0) v = 0;
        if (v > maxScroll) v = maxScroll;
        target = v;
        if (jumpCurrent) current = v;
    }

    /** Ends a thumb drag (idempotent). */
    public void endThumbDrag() {
        dragging = false;
    }
}
