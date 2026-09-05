package com.aurora.client.screen.setting;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.util.CanvasTexture;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraShapes;
import com.aurora.client.util.AuroraTheme;
import com.aurora.client.util.GridDims;
import com.aurora.client.util.HoverAnim;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL11;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Pixel-canvas editor row for the crosshair CUSTOM style.
 *
 * <p><b>Rendering (performance fix, 2026-08-29):</b> the grid used to be
 * submitted as 4 {@code GuiGraphics.fill()} calls per cell — every frame
 * (4,356 submissions + ~8.7k allocations at the 33×33 preset, ~12.8 ms
 * CPU/frame measured). The whole grid is now rasterized once into a
 * {@link CanvasTexture} (NativeImage → DynamicTexture) and drawn as ONE
 * blit per frame; the raster regenerates only when the pixels, the
 * resolution, the cell scale or the disabled state actually change. The
 * raster math replicates the old fill sequence pixel-for-pixel (see
 * {@link CanvasTexture#rasterInto}).
 *
 * <p><b>Resolution (free-form):</b> width × height text fields with an
 * Apply button. Values must be integers in [1, {@link #MAX_DIM}]; anything
 * else is rejected with an inline message. A Default button restores the
 * vanilla 15×15 crosshair shape (replicated pixel-for-pixel from
 * {@code minecraft:textures/gui/sprites/hud/crosshair.png}). Growing the
 * grid first runs a <em>measured</em> cost check — see below.
 *
 * <p><b>Measured-cost warning:</b> before committing a larger
 * resolution, a synthetic raster benchmark runs the exact regeneration
 * work at the requested size on the user's own machine
 * ({@link CanvasTexture#measureRasterMs}, off-thread). If the measured
 * cost exceeds {@link #WARN_REGEN_MS} per redraw — the per-frame budget
 * hit taken while actively drawing at that size — a warning panel shows
 * the real number (with CPU/GPU strings as supplementary context only)
 * and offers Apply anyway / Cancel. The decision is never based on
 * hardware-name heuristics, only on the measured render time.
 */
public class PixelCanvasSetting extends FeatureSetting {

    /** Hard upper bound per axis. The cached-raster path handles far
     *  larger grids, but memory (pixels + texture) and per-edit raster
     *  cost scale with cell count; 128 was measured comfortably cheap
     *  (see the [canvas-perf] verification logs) and is far beyond any
     *  sensible crosshair resolution. Not silently clamped: entering a
     *  larger value shows an error. */
    public static final int MAX_DIM = 128;

    /** Warn threshold (ms) for the measured per-frame cost of a new
     *  resolution (see {@link #startBenchmark}). 6 ms ≈ 36% of a 60 fps
     *  frame. Calibrated from the measurements behind the caching fix,
     *  not guessed. Volatile/non-final only so the verification rig can
     *  exercise the warning mechanism; treat it as a constant. */
    public static volatile double WARN_THRESHOLD_MS = 6.0;

    /** Benchmark iterations — enough for a stable mean, fast enough to
     *  feel instant (<150 ms even at the cap). */
    private static final int BENCH_ITERATIONS = 16;

    /** Per-fill() submission cost in ms, measured once on this machine
     *  (512 transparent, fully off-screen fills — same allocation +
     *  submission work, zero visible output). {@code -1} = not yet
     *  measured. Feeds the composite HUD cost estimate. */
    private static volatile double measuredPerFillMs = -1.0;
    private static final int PERFILL_PROBE_FILLS = 512;

    private static final int CANVAS_PAD = 4;

    private static final int CLEAR_BTN_W = 56;
    private static final int CLEAR_BTN_H = 16;

    private static final int LABEL_H = 22;   // top label area (incl. clear button)
    private static final int SIZE_ROW_H = 18;
    private static final int WARN_PANEL_H = 56;

    private static final int FIELD_W = 30;
    private static final int FIELD_H = 16;
    private static final int APPLY_W = 46;
    private static final int DEFAULT_W = 56;

    private static final int STATUS_ERR  = 0xFFFF6B6B;
    private static final int STATUS_INFO = 0xFF9AA5B8;

    /**
     * The vanilla crosshair shape: 15×15, a 1-px-thick plus with 9-px
     * arms centered at (7,7) — 17 fully-opaque pixels, replicated from
     * {@code minecraft:textures/gui/sprites/hud/crosshair.png}. The
     * Default button commits this pattern at 15×15. (Vanilla blends the
     * sprite inverted; Aurora's custom canvas paints it with the
     * configured crosshair color, as with any painted pattern.)
     */
    private static final int VANILLA_SIZE = 15;

    private static boolean[] vanillaPattern() {
        boolean[] px = new boolean[VANILLA_SIZE * VANILLA_SIZE];
        for (int i = 3; i <= 11; i++) {
            px[7 * VANILLA_SIZE + i] = true;   // horizontal arm: row 7, cols 3..11
            px[i * VANILLA_SIZE + 7] = true;   // vertical arm: col 7, rows 3..11
        }
        return px;
    }

    private final Supplier<boolean[]> getter;
    private final Consumer<boolean[]> setter;
    private final IntSupplier widthGetter;
    private final IntConsumer widthSetter;
    private final IntSupplier heightGetter;
    private final IntConsumer heightSetter;
    private int lastWidth = 240;

    // Drag state
    private boolean dragging = false;
    private int dragButton = -1;
    private int lastCellX = -1, lastCellY = -1;

    private int canvasX, canvasY;
    private int clearBtnX, clearBtnY;
    private int sizeRowY;
    private int defaultBtnX, defaultBtnY;

    private final HoverAnim clearHoverAnim = new HoverAnim(140L);
    private final HoverAnim defaultHoverAnim = new HoverAnim(140L);
    private final HoverAnim applyHoverAnim = new HoverAnim(140L);

    // Free-form resolution input
    private final EditBox widthField;
    private final EditBox heightField;
    private int applyBtnX, applyBtnY;

    /** Inline status line under the size row: validation errors and
     *  confirmations. {@code null} = nothing shown. */
    private String status = null;
    private boolean statusIsError = false;

    // ---- Measured-cost warning state ----
    /** Resolution pending benchmark, or null. */
    private int[] benchPending = null;
    /** Benchmark result (consumed on the render thread), or null. */
    private volatile double[] benchResult = null;
    /** Measured resolution awaiting the user's Apply-anyway/Cancel call. */
    private int[] warnPending = null;
    private double warnMeasuredMs = 0.0;
    private int warnBtnApplyX, warnBtnCancelX, warnBtnY;

    // ---- Cached-grid state (performance fix) ----
    private final CanvasTexture texture = new CanvasTexture();
    /** Bumped on every mutation (paint/erase/clear/resize). */
    private long editVersion = 0;
    /** Last array instance rasterized — catches external swaps (profile
     *  apply, reset) that don't go through our mutators. */
    private boolean[] rasterArray = null;

    public PixelCanvasSetting(String label,
                              Supplier<boolean[]> getter, Consumer<boolean[]> setter,
                              IntSupplier widthGetter, IntConsumer widthSetter,
                              IntSupplier heightGetter, IntConsumer heightSetter) {
        super(label);
        this.getter = getter;
        this.setter = setter;
        this.widthGetter = widthGetter;
        this.widthSetter = widthSetter;
        this.heightGetter = heightGetter;
        this.heightSetter = heightSetter;
        Font font = Minecraft.getInstance().font;
        widthField = new EditBox(font, 0, 0, FIELD_W, FIELD_H, Component.literal("W"));
        heightField = new EditBox(font, 0, 0, FIELD_W, FIELD_H, Component.literal("H"));
        for (EditBox f : new EditBox[]{widthField, heightField}) {
            f.setMaxLength(3);
            f.setBordered(false);
            f.setTextColor(AuroraTheme.IOS_LABEL);
            // digits only, live-stripped
            f.setResponder(s -> {
                String clean = s.replaceAll("[^0-9]", "");
                if (clean.length() > 3) clean = clean.substring(0, 3);
                if (!clean.equals(s)) f.setValue(clean);
            });
        }
        GridDims d = currentDims();
        widthField.setValue(String.valueOf(d.w));
        heightField.setValue(String.valueOf(d.h));
    }

    private boolean[] pixels() {
        return getter.get();
    }

    /** Effective dims for the current pixel array (explicit fields win,
     *  legacy perfect-square fallback covers old configs/profiles). */
    private GridDims currentDims() {
        boolean[] px = pixels();
        return GridDims.resolve(px == null ? 0 : px.length,
                widthGetter.getAsInt(), heightGetter.getAsInt());
    }

    private int getCellPx() {
        GridDims d = currentDims();
        return CanvasTexture.cellPxFor(d.w, d.h);
    }

    private int getCanvasW() { return currentDims().w * getCellPx() + CANVAS_PAD * 2; }
    private int getCanvasH() { return currentDims().h * getCellPx() + CANVAS_PAD * 2; }

    private int getControlH() {
        // +16 below the canvas: breathing room + the transient status line
        int h = LABEL_H + SIZE_ROW_H + 8 + getCanvasH() + 16;
        if (warnPending != null || benchPending != null) h += WARN_PANEL_H + 6;
        return h;
    }

    @Override public int baseHeight() { return getControlH(); }
    @Override public int height()    { return getControlH() + descriptionHeight(lastWidth); }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;
        boolean disabled = isDisabled();

        probePerFillCost(ctx);
        consumeBenchmarkResult();
        decideBenchmark();

        // Label
        renderLabelWithTooltip(ctx, label, x + 12, y + (LABEL_H - tr.lineHeight) / 2,
                AuroraTheme.TEXT_PRIMARY, mouseX, mouseY, disabled);

        // Clear button
        clearBtnX = x + width - CLEAR_BTN_W - 14;
        clearBtnY = y + (LABEL_H - CLEAR_BTN_H) / 2;
        boolean clearHover = !disabled && mouseX >= clearBtnX && mouseX < clearBtnX + CLEAR_BTN_W
                && mouseY >= clearBtnY && mouseY < clearBtnY + CLEAR_BTN_H;
        float chT = clearHoverAnim.update(clearHover);

        int clearFill   = disabled ? 0x22FFFFFF : lerpColor(AuroraTheme.PANEL_OFF, AuroraTheme.PANEL_OFF_HOVER, chT);
        int clearBorder = disabled ? 0x33FFFFFF : lerpColor(AuroraTheme.BORDER_OFF, AuroraTheme.BORDER_OFF_HOVER, chT);
        int clearText   = disabled ? AuroraTheme.TEXT_DIM : lerpColor(AuroraTheme.TEXT_SECONDARY, AuroraTheme.TEXT_PRIMARY, chT);

        RenderUtil.drawSquircle(ctx, clearBtnX, clearBtnY, CLEAR_BTN_W, CLEAR_BTN_H, AuroraTheme.RADIUS_SMALL, clearFill);
        RenderUtil.drawSquircleOutline(ctx, clearBtnX, clearBtnY, CLEAR_BTN_W, CLEAR_BTN_H, AuroraTheme.RADIUS_SMALL, 1.0f, clearBorder);
        String clearLabel = "Clear";
        int clearLabelW = tr.width(clearLabel);
        ctx.drawString(tr, clearLabel,
                clearBtnX + (CLEAR_BTN_W - clearLabelW) / 2,
                clearBtnY + (CLEAR_BTN_H - tr.lineHeight) / 2 + 1,
                clearText, false);

        // ---- Free-form resolution row: [W] × [H] [Apply] ----
        sizeRowY = y + LABEL_H;
        int wx = x + 14;
        widthField.setX(wx);
        widthField.setY(sizeRowY + (SIZE_ROW_H - FIELD_H) / 2);
        widthField.setWidth(FIELD_W);
        int sepX = wx + FIELD_W + 2;
        ctx.drawString(tr, "×", sepX + 1, sizeRowY + (SIZE_ROW_H - tr.lineHeight) / 2,
                AuroraTheme.TEXT_SECONDARY, false);
        int hx = sepX + tr.width("×") + 4;
        heightField.setX(hx);
        heightField.setY(sizeRowY + (SIZE_ROW_H - FIELD_H) / 2);
        heightField.setWidth(FIELD_W);
        applyBtnX = hx + FIELD_W + 8;
        applyBtnY = sizeRowY + (SIZE_ROW_H - CLEAR_BTN_H) / 2;
        drawApplyButton(ctx, tr, mouseX, mouseY, disabled);

        // Default button — restores the vanilla 15×15 crosshair shape.
        defaultBtnX = x + width - DEFAULT_W - 14;
        defaultBtnY = sizeRowY + (SIZE_ROW_H - CLEAR_BTN_H) / 2;
        drawDefaultButton(ctx, tr, mouseX, mouseY, disabled);

        if (!disabled) {
            widthField.render(ctx, mouseX, mouseY, 0f);
            heightField.render(ctx, mouseX, mouseY, 0f);
        }

        // Status line — drawn under the canvas (never overlaps the fields
        // or buttons; transient until the next action).
        if (status != null) {
            ctx.drawString(tr, status, x + 14, canvasY + getCanvasH() + 5,
                    statusIsError ? STATUS_ERR : STATUS_INFO, false);
        }

        // ---- Canvas ----
        canvasX = x + (width - getCanvasW()) / 2;
        canvasY = sizeRowY + SIZE_ROW_H + 8;

        AuroraShapes.panel(ctx, canvasX, canvasY, getCanvasW(), getCanvasH(), AuroraTheme.PANEL_INSET, 0);
        AuroraShapes.outline(ctx, canvasX, canvasY, getCanvasW(), getCanvasH(), disabled ? 0x33FFFFFF : AuroraTheme.BORDER_OFF, 0);

        boolean[] pixels = pixels();
        GridDims dims = currentDims();
        int cellPx = CanvasTexture.cellPxFor(dims.w, dims.h);

        if (dims.matches(pixels)) {
            // Cached raster: ONE blit per frame; regenerate only when the
            // pixels, dims, cell scale or disabled state changed.
            if (pixels != rasterArray) editVersion++; // external swap (profile/reset)
            long key = dims.w * 1_000_003L * 31L
                    ^ dims.h * 7919L
                    ^ cellPx * 104729L
                    ^ (disabled ? 0x55555555L : 0L)
                    ^ editVersion * 1_000_000_007L;
            if (!texture.isCurrent(key)) {
                texture.regenerate(key, pixels, dims.w, dims.h, cellPx, disabled);
                rasterArray = pixels;
            }
            texture.blit(ctx, canvasX + CANVAS_PAD, canvasY + CANVAS_PAD, dims.w, dims.h, cellPx);
        }

        // ---- Warning panel (bench running / measured cost over threshold) ----
        if (benchPending != null || warnPending != null) {
            int wpy = canvasY + getCanvasH() + 6;
            int wpw = width - 28;
            RenderUtil.drawSquircle(ctx, x + 14, wpy, wpw, WARN_PANEL_H, AuroraTheme.RADIUS_SMALL, 0x66301808);
            RenderUtil.drawSquircleOutline(ctx, x + 14, wpy, wpw, WARN_PANEL_H, AuroraTheme.RADIUS_SMALL, 1.0f, 0xFFB98900);
            String l1;
            if (benchPending != null) {
                l1 = "Measuring render cost at " + benchPending[0] + "×" + benchPending[1] + "…";
            } else {
                l1 = warnPending[0] + "×" + warnPending[1] + " measured at "
                        + String.format(java.util.Locale.ROOT, "%.1f", warnMeasuredMs)
                        + " ms per redraw — may impact FPS while editing.";
            }
            ctx.drawString(tr, l1, x + 22, wpy + 6, AuroraTheme.TEXT_PRIMARY, false);
            String l2 = hardwareContext();
            ctx.drawString(tr, l2, x + 22, wpy + 6 + tr.lineHeight + 2, STATUS_INFO, false);

            warnBtnY = wpy + 6 + (tr.lineHeight + 2) * 2 + 2;
            warnBtnApplyX = x + 22;
            warnBtnCancelX = warnBtnApplyX + 92;
            drawWarnButton(ctx, tr, warnBtnApplyX, warnBtnY, 86, 16, "Apply Anyway",
                    mouseX, mouseY, warnPending == null);
            drawWarnButton(ctx, tr, warnBtnCancelX, warnBtnY, 64, 16, "Cancel",
                    mouseX, mouseY, warnPending == null);
        }

        renderDescription(ctx, x, y + getControlH(), width);
    }

    private void drawApplyButton(GuiGraphics ctx, Font tr, int mouseX, int mouseY, boolean disabled) {
        boolean hover = !disabled && mouseX >= applyBtnX && mouseX < applyBtnX + APPLY_W
                && mouseY >= applyBtnY && mouseY < applyBtnY + CLEAR_BTN_H;
        float t = applyHoverAnim.update(hover);
        int fill   = disabled ? 0x22FFFFFF : lerpColor(AuroraTheme.PANEL_OFF, AuroraTheme.PANEL_OFF_HOVER, t);
        int border = disabled ? 0x33FFFFFF : lerpColor(AuroraTheme.BORDER_OFF, AuroraTheme.BORDER_OFF_HOVER, t);
        int text   = disabled ? AuroraTheme.TEXT_DIM : lerpColor(AuroraTheme.TEXT_SECONDARY, AuroraTheme.TEXT_PRIMARY, t);
        RenderUtil.drawSquircle(ctx, applyBtnX, applyBtnY, APPLY_W, CLEAR_BTN_H, AuroraTheme.RADIUS_SMALL, fill);
        RenderUtil.drawSquircleOutline(ctx, applyBtnX, applyBtnY, APPLY_W, CLEAR_BTN_H, AuroraTheme.RADIUS_SMALL, 1.0f, border);
        String lbl = "Apply";
        ctx.drawString(tr, lbl, applyBtnX + (APPLY_W - tr.width(lbl)) / 2,
                applyBtnY + (CLEAR_BTN_H - tr.lineHeight) / 2 + 1, text, false);
    }

    private void drawDefaultButton(GuiGraphics ctx, Font tr, int mouseX, int mouseY, boolean disabled) {
        boolean hover = !disabled && mouseX >= defaultBtnX && mouseX < defaultBtnX + DEFAULT_W
                && mouseY >= defaultBtnY && mouseY < defaultBtnY + CLEAR_BTN_H;
        float t = defaultHoverAnim.update(hover);
        int fill   = disabled ? 0x22FFFFFF : lerpColor(AuroraTheme.PANEL_OFF, AuroraTheme.PANEL_OFF_HOVER, t);
        int border = disabled ? 0x33FFFFFF : lerpColor(AuroraTheme.BORDER_OFF, AuroraTheme.BORDER_OFF_HOVER, t);
        int text   = disabled ? AuroraTheme.TEXT_DIM : lerpColor(AuroraTheme.TEXT_SECONDARY, AuroraTheme.TEXT_PRIMARY, t);
        RenderUtil.drawSquircle(ctx, defaultBtnX, defaultBtnY, DEFAULT_W, CLEAR_BTN_H, AuroraTheme.RADIUS_SMALL, fill);
        RenderUtil.drawSquircleOutline(ctx, defaultBtnX, defaultBtnY, DEFAULT_W, CLEAR_BTN_H, AuroraTheme.RADIUS_SMALL, 1.0f, border);
        String lbl = "Default";
        ctx.drawString(tr, lbl, defaultBtnX + (DEFAULT_W - tr.width(lbl)) / 2,
                defaultBtnY + (CLEAR_BTN_H - tr.lineHeight) / 2 + 1, text, false);
    }

    private void drawWarnButton(GuiGraphics ctx, Font tr, int bx, int by, int bw, int bh,
                                String lbl, int mouseX, int mouseY, boolean enabled) {
        boolean hover = enabled && mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
        int fill = enabled ? (hover ? 0x66FFFFFF : 0x44FFFFFF) : 0x22FFFFFF;
        int text = enabled ? AuroraTheme.TEXT_PRIMARY : AuroraTheme.TEXT_DIM;
        RenderUtil.drawSquircle(ctx, bx, by, bw, bh, AuroraTheme.RADIUS_SMALL, fill);
        RenderUtil.drawSquircleOutline(ctx, bx, by, bw, bh, AuroraTheme.RADIUS_SMALL, 1.0f, 0x44FFFFFF);
        ctx.drawString(tr, lbl, bx + (bw - tr.width(lbl)) / 2,
                by + (bh - tr.lineHeight) / 2 + 1, text, false);
    }

    /** Supplementary context line for the warning — explicitly NOT the
     *  basis of the cost decision (that is the measured ms), just shown
     *  for orientation. Cached after first use. */
    private String hwContext = null;

    private String hardwareContext() {
        if (hwContext == null) {
            String gpu = "unknown GPU";
            try {
                String r = GlStateManager._getString(GL11.GL_RENDERER);
                if (r != null && !r.isBlank()) gpu = r.trim();
            } catch (Throwable ignored) {
            }
            hwContext = gpu + " · " + Runtime.getRuntime().availableProcessors() + " CPU cores (cost above is measured)";
        }
        return hwContext;
    }

    // ===== Resolution input: validation / benchmark / apply =====

    /** Parses and validates the two fields; null + status message on bad input. */
    private int[] parseFields() {
        String ws = widthField.getValue().trim();
        String hs = heightField.getValue().trim();
        if (ws.isEmpty() || hs.isEmpty()) {
            status = "Enter width and height."; statusIsError = true;
            return null;
        }
        int w, h;
        try {
            w = Integer.parseInt(ws);
            h = Integer.parseInt(hs);
        } catch (NumberFormatException e) {
            status = "Width and height must be numbers."; statusIsError = true;
            return null;
        }
        if (w <= 0 || h <= 0) {
            status = "Width and height must be at least 1."; statusIsError = true;
            return null;
        }
        if (w > MAX_DIM || h > MAX_DIM) {
            status = "Maximum resolution is " + MAX_DIM + "×" + MAX_DIM + "."; statusIsError = true;
            return null;
        }
        return new int[]{w, h};
    }

    private void onApplyClicked() {
        int[] wh = parseFields();
        if (wh == null) return;
        GridDims cur = currentDims();
        if (wh[0] == cur.w && wh[1] == cur.h) {
            status = "Already " + wh[0] + "×" + wh[1] + "."; statusIsError = false;
            return;
        }
        // Only growing the grid can raise the cost — shrinking is always safe.
        if (wh[0] * wh[1] > cur.w * cur.h) {
            startBenchmark(wh[0], wh[1]);
        } else {
            applyResize(wh[0], wh[1]);
        }
    }

    private void startBenchmark(int w, int h) {
        if (benchPending != null) return; // one flight at a time
        int cellPx = CanvasTexture.cellPxFor(w, h);
        benchPending = new int[]{w, h};
        status = null;
        Thread t = new Thread(() -> {
            // Measured-on-this-machine components at the requested size:
            // (a) the editor's per-edit raster regen, (b) the in-game
            // per-frame run scan. The scan's fill submissions are priced
            // in decideBenchmark with the locally measured per-fill cost.
            double regenMs = CanvasTexture.measureRasterMs(w, h, cellPx, BENCH_ITERATIONS);
            double[] scan = CanvasTexture.measureHudScan(w, h, BENCH_ITERATIONS);
            benchResult = new double[]{w, h, regenMs, scan[0], scan[1]};
        }, "Aurora-CanvasCostProbe");
        t.setDaemon(true);
        t.start();
    }

    /** Off-thread benchmark results awaiting a decision, or null. */
    private double[] pendingDecision = null;

    /** Render-thread handoff of the off-thread benchmark result. */
    private void consumeBenchmarkResult() {
        double[] res = benchResult;
        if (res == null) return;
        benchResult = null;
        pendingDecision = res;
        decideBenchmark();
    }

    /** Composites the measured parts and warns/applies. Re-entered each
     *  frame until the per-fill probe has finished, so the composite
     *  always prices the fill submissions with a real measurement. */
    private void decideBenchmark() {
        double[] res = pendingDecision;
        if (res == null) return;
        if (measuredPerFillMs <= 0) return; // probe still pending (it runs while benchPending != null)

        pendingDecision = null;
        int w = (int) res[0], h = (int) res[1];
        double regenMs = res[2], scanMs = res[3];
        double runs = res[4];
        double fillMs = runs * measuredPerFillMs;
        double totalMs = regenMs + scanMs + fillMs;

        if (benchPending != null && benchPending[0] == w && benchPending[1] == h) {
            benchPending = null;
        }
        AuroraClientLog.log(w, h, totalMs, regenMs, scanMs, fillMs, (long) runs, hardwareContext());
        if (totalMs > WARN_THRESHOLD_MS) {
            warnPending = new int[]{w, h};
            warnMeasuredMs = totalMs;
            status = null;
        } else {
            applyResize(w, h);
        }
    }

    /** One-time machine probe of the real per-fill() submission cost:
     *  512 fully transparent, off-screen fills — identical allocation +
     *  submission work, invisible output. Runs on the render thread the
     *  first time a benchmark is pending; ~1 ms once per session. */
    private void probePerFillCost(GuiGraphics ctx) {
        if (measuredPerFillMs > 0 || benchPending == null) return;
        long t0 = System.nanoTime();
        for (int i = 0; i < PERFILL_PROBE_FILLS; i++) {
            ctx.fill(-1000, -1000, -990, -990, 0x00000000);
        }
        measuredPerFillMs = (System.nanoTime() - t0) / 1_000_000.0 / PERFILL_PROBE_FILLS;
        com.aurora.client.AuroraClient.LOGGER.info(
                "[canvas-cost] per-fill() submission cost on this machine: {} ms",
                String.format(java.util.Locale.ROOT, "%.4f", measuredPerFillMs));
    }

    /** Commits the vanilla 15×15 crosshair shape (Default button). A
     *  fixed, curated pattern — skips the measured-cost flow, and cancels
     *  any in-flight benchmark/warning for a manual entry. */
    private void applyVanillaDefault() {
        benchPending = null;
        pendingDecision = null;
        benchResult = null;
        setter.accept(vanillaPattern());
        widthSetter.accept(VANILLA_SIZE);
        heightSetter.accept(VANILLA_SIZE);
        widthField.setValue(String.valueOf(VANILLA_SIZE));
        heightField.setValue(String.valueOf(VANILLA_SIZE));
        editVersion++;
        warnPending = null;
        widthField.setFocused(false);
        heightField.setFocused(false);
        releaseFocus();
        AuroraConfig.save();
        status = "Restored the vanilla " + VANILLA_SIZE + "×" + VANILLA_SIZE + " crosshair shape.";
        statusIsError = false;
    }

    /** Commits the new resolution: re-center-copies the old pattern,
     *  writes dims + pixels, saves. */
    private void applyResize(int newW, int newH) {
        GridDims old = currentDims();
        boolean[] oldPixels = pixels();
        boolean[] newPixels = new boolean[newW * newH];

        if (oldPixels != null && old.matches(oldPixels)) {
            int offX = newW / 2 - old.w / 2;
            int offY = newH / 2 - old.h / 2;
            for (int py = 0; py < old.h; py++) {
                for (int px = 0; px < old.w; px++) {
                    if (oldPixels[py * old.w + px]) {
                        int nx = px + offX;
                        int ny = py + offY;
                        if (nx >= 0 && nx < newW && ny >= 0 && ny < newH) {
                            newPixels[ny * newW + nx] = true;
                        }
                    }
                }
            }
        } else {
            newPixels[newH / 2 * newW + newW / 2] = true;
        }

        setter.accept(newPixels);
        widthSetter.accept(newW);
        heightSetter.accept(newH);
        editVersion++;
        warnPending = null;
        AuroraConfig.save();
        status = "Grid set to " + newW + "×" + newH + ".";
        statusIsError = false;
    }

    /** Tiny indirection so the benchmark log line reads cleanly. */
    private static final class AuroraClientLog {
        static void log(int w, int h, double totalMs, double regenMs, double scanMs,
                        double fillMs, long runs, String hw) {
            com.aurora.client.AuroraClient.LOGGER.info(
                    "[canvas-cost] {}x{} measured {} ms/frame total on this machine "
                            + "(redraw {} + in-game scan {} + {} fills×{} ) ({})",
                    w, h,
                    String.format(java.util.Locale.ROOT, "%.2f", totalMs),
                    String.format(java.util.Locale.ROOT, "%.2f", regenMs),
                    String.format(java.util.Locale.ROOT, "%.2f", scanMs),
                    String.format(java.util.Locale.ROOT, "%.2f", fillMs), runs, hw);
        }
    }

    // ===== Input routing =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (isDisabled()) return false;

        // Warning panel buttons.
        if ((warnPending != null || benchPending != null) && button == 0) {
            if (warnPending != null && mouseY >= warnBtnY && mouseY < warnBtnY + 16) {
                if (mouseX >= warnBtnApplyX && mouseX < warnBtnApplyX + 86) {
                    applyResize(warnPending[0], warnPending[1]);
                    return true;
                }
                if (mouseX >= warnBtnCancelX && mouseX < warnBtnCancelX + 64) {
                    warnPending = null;
                    status = "Kept current resolution.";
                    statusIsError = false;
                    return true;
                }
            }
        }

        // Resolution fields.
        if (button == 0 && mouseY >= sizeRowY && mouseY < sizeRowY + SIZE_ROW_H) {
            boolean onW = mouseX >= widthField.getX() && mouseX < widthField.getX() + FIELD_W;
            boolean onH = mouseX >= heightField.getX() && mouseX < heightField.getX() + FIELD_W;
            if (onW || onH) {
                widthField.setFocused(onW);
                heightField.setFocused(onH);
                requestFocus();
                return true;
            }
            widthField.setFocused(false);
            heightField.setFocused(false);

            if (mouseX >= applyBtnX && mouseX < applyBtnX + APPLY_W
                    && mouseY >= applyBtnY && mouseY < applyBtnY + CLEAR_BTN_H) {
                onApplyClicked();
                return true;
            }
        }

        // Default button: restore the vanilla 15×15 pattern.
        if (button == 0
                && mouseX >= defaultBtnX && mouseX < defaultBtnX + DEFAULT_W
                && mouseY >= defaultBtnY && mouseY < defaultBtnY + CLEAR_BTN_H) {
            applyVanillaDefault();
            return true;
        }

        // Clear button.
        if (button == 0
                && mouseX >= clearBtnX && mouseX < clearBtnX + CLEAR_BTN_W
                && mouseY >= clearBtnY && mouseY < clearBtnY + CLEAR_BTN_H) {
            boolean[] pixels = pixels();
            if (pixels != null) {
                for (int i = 0; i < pixels.length; i++) pixels[i] = false;
                editVersion++;
                AuroraConfig.save();
            }
            return true;
        }

        // Canvas hits.
        int[] cell = cellAt(mouseX, mouseY);
        if (cell == null) return false;
        if (button != 0 && button != 1) return false;

        widthField.setFocused(false);
        heightField.setFocused(false);
        releaseFocus();

        applyCell(cell[0], cell[1], button == 0);
        dragging = true;
        dragButton = button;
        lastCellX = cell[0];
        lastCellY = cell[1];
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy,
                                int rowX, int rowY, int rowWidth) {
        if (!dragging || button != dragButton || isDisabled()) return false;

        int[] cell = cellAt(mouseX, mouseY);
        if (cell == null) {
            return true;
        }
        if (cell[0] == lastCellX && cell[1] == lastCellY) return true;

        fillLine(lastCellX, lastCellY, cell[0], cell[1], button == 0);
        lastCellX = cell[0];
        lastCellY = cell[1];
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == dragButton) {
            dragging = false;
            dragButton = -1;
            lastCellX = -1;
            lastCellY = -1;
            AuroraConfig.save();
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyPress(net.minecraft.client.input.KeyEvent _kev) {
        for (EditBox f : new EditBox[]{widthField, heightField}) {
            if (f.isFocused()) {
                if (_kev.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN) {
                    onApplyClicked();
                    return true;
                }
                return f.keyPressed(_kev);
            }
        }
        return false;
    }

    @Override
    public boolean onCharTyped(net.minecraft.client.input.CharacterEvent _ev) {
        for (EditBox f : new EditBox[]{widthField, heightField}) {
            if (f.isFocused()) {
                return f.charTyped(_ev);
            }
        }
        return false;
    }

    // ===== Detail-screen lifecycle =====

    @Override
    public void onDetailScreenOpen() {
        // Normalize legacy configs: if the dim fields don't describe the
        // pixel array (old perfect-square data), adopt the resolved dims
        // so the schema converges on first visit.
        boolean[] px = pixels();
        GridDims d = GridDims.resolve(px == null ? 0 : px.length,
                widthGetter.getAsInt(), heightGetter.getAsInt());
        if (px != null && px.length != d.w * d.h) {
            // unresolvable array — reset to factory default canvas
            boolean[] fresh = new boolean[GridDims.DEFAULT * GridDims.DEFAULT];
            fresh[GridDims.DEFAULT / 2 * GridDims.DEFAULT + GridDims.DEFAULT / 2] = true;
            setter.accept(fresh);
            widthSetter.accept(GridDims.DEFAULT);
            heightSetter.accept(GridDims.DEFAULT);
            editVersion++;
            d = GridDims.of(GridDims.DEFAULT, GridDims.DEFAULT);
        } else if (widthGetter.getAsInt() != d.w || heightGetter.getAsInt() != d.h) {
            widthSetter.accept(d.w);
            heightSetter.accept(d.h);
        }
        widthField.setValue(String.valueOf(d.w));
        heightField.setValue(String.valueOf(d.h));
        status = null;
        warnPending = null;
        benchPending = null;
        benchResult = null;
    }

    @Override
    public void onDetailScreenClose() {
        widthField.setFocused(false);
        heightField.setFocused(false);
        releaseFocus();
        texture.dispose();
        rasterArray = null;
    }

    // ===== Painting =====

    private int[] cellAt(double mouseX, double mouseY) {
        int gridLeft = canvasX + CANVAS_PAD;
        int gridTop  = canvasY + CANVAS_PAD;
        int relX = (int) Math.floor(mouseX - gridLeft);
        int relY = (int) Math.floor(mouseY - gridTop);
        if (relX < 0 || relY < 0) return null;
        int cellPx = getCellPx();
        int gx = relX / cellPx;
        int gy = relY / cellPx;
        GridDims d = currentDims();
        if (gx < 0 || gx >= d.w) return null;
        if (gy < 0 || gy >= d.h) return null;
        return new int[] { gx, gy };
    }

    private void applyCell(int gx, int gy, boolean setOn) {
        boolean[] pixels = pixels();
        if (pixels == null) return;
        GridDims d = currentDims();
        int idx = gy * d.w + gx;
        if (idx < 0 || idx >= pixels.length) return;
        if (pixels[idx] != setOn) {
            pixels[idx] = setOn;
            editVersion++;
        }
    }

    private void fillLine(int x0, int y0, int x1, int y1, boolean setOn) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int cx = x0;
        int cy = y0;
        while (true) {
            applyCell(cx, cy, setOn);
            if (cx == x1 && cy == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; cx += sx; }
            if (e2 < dx)  { err += dx; cy += sy; }
        }
    }

    private static int lerpColor(int from, int to, float t) {
        if (t <= 0f) return from;
        if (t >= 1f) return to;
        int af = (from >>> 24) & 0xFF, ar = (from >>> 16) & 0xFF, ag = (from >>> 8) & 0xFF, ab = from & 0xFF;
        int bf = (to   >>> 24) & 0xFF, br = (to   >>> 16) & 0xFF, bg = (to   >>> 8) & 0xFF, bb = to   & 0xFF;
        int a = Math.round(af + (bf - af) * t);
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int b = Math.round(ab + (bb - ab) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
