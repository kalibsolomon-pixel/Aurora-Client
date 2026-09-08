package com.aurora.client.screen;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.component.Toast;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.ui.util.UiLayerCache;
import com.aurora.client.util.SmoothScroll;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * The shared foundation under Aurora's two "manager list" screens (audit R3):
 * {@link ProfileManagerScreen} and {@link WaypointManagerScreen}, which grew
 * up as line-for-line twins and had drifted into maintaining the same frame
 * twice — worst case R2 Part C, whose scrollbar/thumb code had to be written
 * once per screen ("cross-referenced but independently coded"). Everything
 * the two screens do identically lives here exactly once; everything they do
 * differently is a hook the subclass owns.
 *
 * <h2>What the base owns</h2>
 *
 * <ul>
 *   <li>The frame order — the layering contract (AGENTS.md §6), structural:
 *       glass pass (rows under a tracked scissor, toolbar buttons, inline
 *       editor field) → {@link GlassSurface#overlayDim} → content (title,
 *       empty state, rows) → scrollbar thumb → {@code super.render} → the
 *       subclass's tail (editor + toast, in the screen's own historical
 *       order).</li>
 *   <li>The {@link SmoothScroll} instance (τ = 60 ms, wheel step 30) and the
 *       full R2-C scrollbar treatment: 3px {@code ON_OVERLAY} capsule at
 *       0x30 alpha (0x55 hovered/dragged), grab-where-clicked thumb with the
 *       position pinned during drags. Painting and hit zones are identical
 *       on both screens by construction now.</li>
 *   <li>The inline rename editor ({@code EditBox}) lifecycle: open on row
 *       click, ENTER/KP_ENTER commit, ESCAPE cancel, click-outside commit —
 *       with the <b>apply</b> step left to {@link #applyRename}.</li>
 *   <li>Ellipsis truncation of row names, memoized per raw name (the
 *       per-character {@code font.width()} loop must not run every row,
 *       every frame).</li>
 *   <li>The toolbar's Done button (right) plus a screen-supplied action
 *       button (left); the toast; the flat row fallback and shadow-glow
 *       painters; {@code renderBackground} world-gating; {@code onClose}
 *       (commit → {@link #saveOnClose} → parent) and {@code isPauseScreen}.</li>
 * </ul>
 *
 * <h2>Per-frame cost discipline (the standing scale-check rule)</h2>
 *
 * The frame path fetches the row list <b>once</b> and hands it to every pass
 * ({@code ProfileManager.listProfileNames()} returns a fresh list per call —
 * see {@code 0a0caaa}); per-row widget caches are the subclass's own but are
 * reconciled in {@link #reconcileRowCaches} against a <b>set</b>, never a
 * list (the O(rows²) {@code retainAll(List)} bug class fixed in
 * {@code 0a0caaa}); the row loops are O(rows) integer arithmetic with a cheap
 * visibility test. Subclasses adding per-row state must keep those
 * properties — see AGENTS.md §10.
 *
 * <h2>Genuine differences that stayed screen-owned (do not "unify")</h2>
 *
 * <ul>
 *   <li><b>Row glass role:</b> Profile rows are the screen's containers and
 *       take DEPRESSED {@code GlassSurface.container}; Waypoint rows float
 *       over the world and take RAISED {@code GlassSurface.control}. Both
 *       are deliberate §6 rulings, recorded in the rollout table.</li>
 *   <li><b>Tail order:</b> Profile paints the editor above the toast,
 *       Waypoint the toast above the editor. The orders disagree historically;
 *       they are observable only when the last visible row's editor overlaps
 *       the toast band, so each screen keeps its own order in
 *       {@link #paintTail} rather than being forced to match.</li>
 *   <li>Rename apply semantics, the nameFitCache clearing policy, empty-state
 *       text, geometry constants and all row content/actions.</li>
 * </ul>
 *
 * <h2>Why a base class and not another helper</h2>
 *
 * {@code GlassSurface} and {@code SmoothScroll} are shared <i>utilities</i>
 * and stay composition-based; what is duplicated between these two screens
 * is <i>control flow</i> — the render frame itself and the input dispatch —
 * which composition can only absorb by reinventing this class as a lattice
 * of callbacks. A screen template (vanilla's own
 * {@code AbstractContainerScreen} shape) is the honest fit for that.
 */
public abstract class ManagerListScreen<T> extends Screen implements ThemedScreen {

    protected final Screen parent;

    /** Toast feedback ("Switched to X", "Copied: …"). */
    private final Toast toast = new Toast();

    /**
     * List scrolling (R2 Part C): τ = 60 ms easing, wheel step 30, and the
     * draggable hover-responsive thumb both manager screens adopted together.
     */
    protected final SmoothScroll scroll = new SmoothScroll(60.0);

    /** Inline rename editor state. {@code -1} = not editing. */
    protected int editingIndex = -1;
    protected EditBox nameField;

    // Which rows' glass drew this frame (glass pass), so the flat row body
    // is drawn for exactly the rows whose glass declined.
    protected final Map<T, Boolean> rowGlassDrawn = new HashMap<>();

    /**
     * Fitted (ellipsis-truncated) row names, keyed by the raw name —
     * memoizes the per-character {@code font.width()} truncation loop that
     * otherwise runs every row, every frame (the same cost the pack browser
     * memoizes via its text-fit cache). Keyed by content, so renames miss
     * once and repopulate; bounded by distinct names seen this screen-open.
     * The subclass decides whether to clear it on membership change.
     */
    protected final Map<String, String> nameFitCache = new HashMap<>();

    /** Toolbar buttons — kept so the glass pass can paint their surfaces pre-dim. */
    private ButtonWidget toolbarActionBtn;
    private ButtonWidget doneBtn;

    // ---- Static row-surface template (P2: kill the per-row fill volume) ----
    //
    // Both manager screens paint, per visible row and per frame, a large
    // number of small AA strip fills: six shadow-ring outlines plus the
    // rounded tint fills of the row surface and its glass buttons — on the
    // order of a thousand GuiGraphics.fill submissions per row. MC 1.21.11's
    // GuiRenderState runs an intersection search over the elements recorded
    // SO FAR for every submitted fill, so a frame of N disjoint fills costs
    // O(N²) — 15 rows ≈ 15–20k fills ≈ 300+ ms/frame (the 2–3 fps bug), while
    // the glass pipeline itself only accounts for ~15–20 ms.
    //
    // Rows on these screens are geometrically IDENTICAL, so the fix is a
    // row TEMPLATE: the row's static shape layer is rasterized ONCE — same
    // AA math, through RenderUtil.beginCapture into a small buffer — and
    // blitted per row (one O(1) texture submission each). Scroll never
    // re-rasterizes (rows differ only by blit position); a theme or
    // GUI-scale change re-rasterizes via the version key. Two variants:
    // with the glass tint fills (rows whose glass drew) and shadow-only
    // (declined rows, whose flat body paints live in the content pass as
    // before). Per frame the row's ordinary glass pass still runs — the
    // live-world panels and deferred rims are NOT cacheable — with its
    // RenderUtil fills redirected to DISCARD so they are not double-painted.
    //
    // Z-order parity: the template blits right after its row's glass pass,
    // before the next row's. The only overlaps are (a) adjacent rows' rings
    // and tints across the 4px row gap, which the per-row blit order keeps
    // identical to the live per-shape order, and (b) a ring against its OWN
    // panel, which never overlap (rings hug 1+px outside the panel rect).
    /** Ring overhang beyond the row rect (largest ring i=6 + AA edge). */
    private static final int ROW_TPL_PAD = 8;
    private UiLayerCache rowTplTinted;
    private UiLayerCache rowTplBare;

    protected ManagerListScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    // ------------------------------------------------------------------
    //  Geometry & content hooks
    // ------------------------------------------------------------------

    /** The centered list's width in pixels (constant per screen). */
    protected abstract int listWidth();

    /** One row's height in pixels (constant per screen). */
    protected abstract int rowHeight();

    /** The name column's width — drives the inline editor's width and fitName truncation. */
    protected abstract int nameColumnWidth();

    /** The inline rename editor's X position for a list at {@code listX} (each screen clears different leading columns). */
    protected abstract int editorFieldX(int listX);

    /** The live row list. Called once per frame by render and on demand by input paths — same as the pre-R3 screens. */
    protected abstract List<T> currentRows();

    /** Message drawn in the list area when there are no rows. */
    protected abstract Component emptyMessage();

    /** Rows painted above the list (the Profile screen's inline create row). */
    protected int leadingRowCount() { return 0; }

    protected int rowGap() { return 4; }
    protected int listTop() { return 56; }
    protected int listBottomPad() { return 50; }

    private int rowStep() { return rowHeight() + rowGap(); }

    // ------------------------------------------------------------------
    //  Toolbar
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        // Top toolbar: the screen's action button (left) + Done (right) —
        // the shared row both manager screens ship.
        toolbarActionBtn = createToolbarActionBtn();
        if (toolbarActionBtn != null) this.addRenderableWidget(toolbarActionBtn);
        doneBtn = this.addRenderableWidget(new ButtonWidget(
                this.width - 80 - 16, 16, 80, 22,
                Component.literal("Done"),
                this::onClose).glassBackground(true));
    }

    /** The toolbar's left action button, constructed but not registered (the base registers it). */
    protected abstract ButtonWidget createToolbarActionBtn();

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------

    /**
     * Glass rollout: with a live world behind the screen, skip vanilla's
     * background sandwich — the glass rows must sample the LIVE world. With
     * no level loaded the renderer declines anyway (its menu-context guard)
     * and the opaque fallback wants the vanilla backdrop as before.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (GlassSurface.liveWorldBackdrop()) return;
        super.renderBackground(g, mouseX, mouseY, delta);
    }

    /**
     * Frame order — the layering contract (AGENTS.md §6), structural here:
     * <ol>
     *   <li><b>Glass pass</b>: EVERY glass surface on the screen — the rows
     *       (via {@link #paintRowGlassPass}/
     *       {@link #paintLeadingRowGlassPass}), the toolbar's two buttons,
     *       and the inline editor field — paints its surface first.</li>
     *   <li><b>Dim</b>: {@link GlassSurface#overlayDim} veils the glass
     *       exactly as it veils the world, and from here on any glass call
     *       is reported as an ordering violation.</li>
     *   <li><b>Content</b>: title, empty state, row content — and the flat
     *       fallback for any surface whose glass declined. Then the
     *       scrollbar thumb, the vanilla widgets, and the screen's tail
     *       (editor + toast, in the screen's own historical order).</li>
     * </ol>
     */
    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        int listX = (this.width - listWidth()) / 2;
        List<T> rows = currentRows();
        // One list fetch per frame drives everything below — the easing
        // bound, the row passes and the scrollbar (list sources may return a
        // fresh copy per call, so hoisting matters on large lists).
        double maxScroll = maxScrollOf(rows);
        // Easing step before either pass, so the glass pass and the content
        // pass below both read the same this-frame position.
        scroll.advance(maxScroll);
        // Row-widget cache hygiene runs before either pass so both see the
        // same button instances.
        reconcileRowCaches(rows);

        // ---- 1. Glass pass (before the dim) ----
        // Opened explicitly so each surface's rim finish is deferred past the
        // dim (painted by overlayDim); the tracked scissor lets the deferred
        // row rims keep the list clip.
        GlassSurface.beginGlassPass();
        rowGlassDrawn.clear();
        onBeginRowGlassPass();
        GlassSurface.enableScissor(ctx, listX - 4, listClipTop(), listX + listWidth() + 4, listClipBottom());
        int gy = listTop() - (int) scroll.current();
        for (int l = 0; l < leadingRowCount(); l++) {
            if (rowPaintVisible(gy)) paintLeadingRowGlassPass(ctx, listX, gy, listWidth(), l);
            gy += rowStep();
        }
        for (T row : rows) {
            if (rowPaintVisible(gy)) paintRowGlassPass(ctx, listX, gy, listWidth(), row);
            gy += rowStep();
        }
        GlassSurface.disableScissor(ctx);
        if (toolbarActionBtn != null) toolbarActionBtn.renderGlassPass(ctx);
        if (doneBtn != null) doneBtn.renderGlassPass(ctx);
        paintEditorGlassPass(ctx, rows, listX);

        // ---- 2. Dim — end of the glass pass (token-driven; dark in both modes by design) ----
        GlassSurface.overlayDim(ctx, this.width, this.height);

        // ---- 3. Content ----
        ctx.drawString(this.font, this.title, 16, 42, ThemeManager.color(ThemeToken.ON_OVERLAY), false);

        ctx.enableScissor(listX - 4, listClipTop(), listX + listWidth() + 4, listClipBottom());
        if (rows.isEmpty()) {
            ctx.drawString(this.font, emptyMessage(), listX + 8, listTop() + 10,
                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_OVERLAY), 0x88), false);
        }

        int y = listTop() - (int) scroll.current();
        for (int l = 0; l < leadingRowCount(); l++) {
            if (rowPaintVisible(y)) paintLeadingRow(ctx, listX, y, listWidth(), l, mouseX, mouseY);
            y += rowStep();
        }
        for (int i = 0; i < rows.size(); i++) {
            T row = rows.get(i);
            if (rowPaintVisible(y)) paintRow(ctx, listX, y, listWidth(), row, i, mouseX, mouseY);
            y += rowStep();
        }

        ctx.disableScissor();

        // The list's scrollbar thumb — after the content scissor (it is
        // content, painted opaque over the dim like every other thumb).
        drawScrollbar(ctx, mouseX, mouseY, listX, maxScroll);

        super.render(ctx, mouseX, mouseY, delta);

        paintTail(ctx, mouseX, mouseY, delta, rows, listX);

        logFramePerf(rows.size());
    }

    /** Debug-only per-frame cost log: fill submissions + row count, every 2 s (matches FeatureDetailScreen's harness). */
    private long lastPerfLogMs;
    private long lastPerfFills = -1L;
    private void logFramePerf(int rows) {
        if (!com.aurora.client.AuroraClient.LOGGER.isDebugEnabled()) return;
        long now = System.currentTimeMillis();
        if (lastPerfLogMs == 0L) {
            lastPerfLogMs = now;
            lastPerfFills = RenderUtil.fillsSubmitted();
            return;
        }
        long dt = now - lastPerfLogMs;
        if (dt < 2000L) return;
        long fills = RenderUtil.fillsSubmitted();
        double fillsPerSec = lastPerfFills < 0 ? 0 : (fills - lastPerfFills) * 1000.0 / dt;
        com.aurora.client.AuroraClient.LOGGER.debug(
                "[ui-perf] {}: {} rows, {} fills submitted/sec ({} total)",
                getClass().getSimpleName(), rows, (long) fillsPerSec, fills);
        lastPerfLogMs = now;
        lastPerfFills = fills;
    }

    /**
     * Vertical band the row list is scissored to at render time (both
     * scissor calls in {@link #render}). Hit-testing clamps row geometry to
     * this same band so rows (or row parts) that are clipped away are never
     * clickable — the render clip is the source of truth.
     */
    protected int listClipTop() { return listTop() - 2; }
    protected int listClipBottom() { return listTop() + (this.height - listTop() - listBottomPad()); }

    /** The row loops' visibility test (a row paints if any part can show in the clip band). */
    private boolean rowPaintVisible(int rowY) {
        return rowY + rowHeight() > listTop() - rowHeight() && rowY < listClipBottom();
    }

    /**
     * Glass pass for one row surface. The subclass paints the row's glass
     * panel (its chosen §6 role), records whether glass drew in
     * {@link #rowGlassDrawn}, and lays out + glass-passes any row buttons.
     */
    protected abstract void paintRowGlassPass(GuiGraphics ctx, int x, int y, int w, T row);

    /** Content pass for one row: text, badges, swatches, button labels and flat fallbacks. */
    protected abstract void paintRow(GuiGraphics ctx, int x, int y, int w, T row, int index, int mouseX, int mouseY);

    /** Glass pass for leading row {@code leadIndex}; default nothing (no leading rows by default). */
    protected void paintLeadingRowGlassPass(GuiGraphics ctx, int x, int y, int w, int leadIndex) {}

    /** Content pass for leading row {@code leadIndex}; default nothing. */
    protected void paintLeadingRow(GuiGraphics ctx, int x, int y, int w, int leadIndex, int mouseX, int mouseY) {}

    /** Called at the top of each frame's glass pass, right after {@code rowGlassDrawn} clears. */
    protected void onBeginRowGlassPass() {}

    /** Reconcile per-row widget caches against the live row set (both screens reconcile against a SET — see class javadoc). */
    protected abstract void reconcileRowCaches(List<T> rows);

    /** Paints the inline editor's glass surface at its laid-out position (inside the glass pass). */
    protected abstract void paintEditorGlassPass(GuiGraphics ctx, List<T> rows, int listX);

    /**
     * The frame's tail, after {@code super.render}: the inline editor and
     * the toast, in the screen's own historical order (the two screens
     * disagree; the base does not force a winner).
     */
    protected abstract void paintTail(GuiGraphics ctx, int mouseX, int mouseY, float delta, List<T> rows, int listX);

    // ------------------------------------------------------------------
    //  Shared row painters
    // ------------------------------------------------------------------

    /** The soft glow rings — the row's drop shadow (structural black), part of the surface pass. */
    protected void paintRowShadow(GuiGraphics ctx, int x, int y, int w) {
        float radius = ThemeManager.current().roundness().radiusSmall();
        for (int i = 1; i <= 6; i++) {
            int shadowAlpha = Math.max(0, 30 - i * 5);
            RenderUtil.drawRoundedOutlineAA(ctx, x - i, y - i, w + i * 2, rowHeight() + i * 2, radius + i, 1.0f, (shadowAlpha << 24));
        }
    }

    /**
     * The flat row body (surface fill + hairline border) for a row whose
     * glass declined — identical for every row; selection is never carried
     * by the container surface on either screen.
     */
    protected void paintFlatRow(GuiGraphics ctx, int x, int y, int w) {
        float radius = ThemeManager.current().roundness().radiusSmall();
        RenderUtil.drawRoundedRectAA(ctx, x, y, w, rowHeight(), radius,
                ThemeManager.surfaceColor(ThemeToken.SURFACE));
        RenderUtil.drawRoundedOutlineAA(ctx, x, y, w, rowHeight(), radius, 1.0f,
                ThemeManager.color(ThemeToken.BORDER));
    }

    /**
     * Ellipsis-truncates a row name to the name column, memoized per raw
     * name (see {@link #nameFitCache}). The underlying loop calls
     * {@code font.width()} once per removed character; without the cache
     * every long-named row pays it every frame.
     */
    protected String fitName(String raw) {
        String cached = nameFitCache.get(raw);
        if (cached != null) return cached;
        String fitted = AuroraFontRenderer.ellipsize(this.font, raw, nameColumnWidth() - 6, 1);
        nameFitCache.put(raw, fitted);
        return fitted;
    }

    // ------------------------------------------------------------------
    // Static row-surface template (see field comment block)
    // ------------------------------------------------------------------

    /**
     * Runs one row's glass pass with its static shape fills template-supplied.
     * The row's surface part ({@code rowSurfacePass}: shadow rings + the row's
     * own glass panel) executes under a discard capture — its fills come from
     * the shared template blit right after, which must land BETWEEN the row
     * surface and the row's buttons so the row tint stays under the button
     * surfaces exactly as the live order painted them. {@code rowButtonsPass}
     * then runs unwrapped: the shared {@code Button} templates its own tint
     * (or flat) surface internally, per button and per state.
     * {@code glassDrew} is read after the surface pass to pick the template
     * variant (tinted vs shadow-only; declined rows paint their flat body
     * live in the content pass as before).
     */
    protected final void paintTemplatedRowSurface(GuiGraphics ctx, int x, int y,
                                                  Runnable rowSurfacePass, Runnable rowButtonsPass,
                                                  BooleanSupplier glassDrew) {
        RenderUtil.RectSink prev = RenderUtil.beginCapture(RenderUtil.DISCARD_SINK);
        try {
            rowSurfacePass.run();
        } finally {
            RenderUtil.endCapture(prev);
        }
        ensureRowTemplate(ctx, glassDrew.getAsBoolean()).blitAt(ctx,
                x - ROW_TPL_PAD, y - ROW_TPL_PAD,
                listWidth() + 2 * ROW_TPL_PAD, rowHeight() + 2 * ROW_TPL_PAD);
        rowButtonsPass.run();
    }

    /**
     * Version of the row template — everything that can change its pixels:
     * theme (generation bumps on every reload: colors, radius, opacity, glass
     * style), GUI scale (device resolution of the raster), and the row
     * geometry (constant per screen, but cheap to include). Scroll is
     * deliberately absent: rows differ only by blit position.
     */
    private long rowTemplateVersion() {
        int guiScale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
        return ThemeManager.generation() * 1_000_003L
                ^ (long) guiScale * 65537L
                ^ (long) listWidth() * 7919L
                ^ (long) rowHeight() * 104729L;
    }

    /** The (lazily rasterized, version-keyed) row template for either variant. */
    private UiLayerCache ensureRowTemplate(GuiGraphics ctx, boolean tinted) {
        UiLayerCache cache = tinted ? rowTplTinted : rowTplBare;
        long version = rowTemplateVersion();
        if (cache == null) cache = new UiLayerCache();
        if (!cache.isCurrent(version)) {
            int scale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
            cache.ensureSize((listWidth() + 2 * ROW_TPL_PAD) * scale,
                    (rowHeight() + 2 * ROW_TPL_PAD) * scale);
            cache.clear();
            RenderUtil.RectSink prev = RenderUtil.beginCapture(cache.sink());
            try {
                captureRowSurfaceShapes(ctx, ROW_TPL_PAD, ROW_TPL_PAD, listWidth(), tinted);
            } finally {
                RenderUtil.endCapture(prev);
            }
            cache.commit(version);
        }
        if (tinted) rowTplTinted = cache; else rowTplBare = cache;
        return cache;
    }

    /**
     * The row's cacheable static shapes at template-local coordinates: the
     * shadow rings plus, for the tinted variant, the row surface's neutral
     * fill — the SAME call {@code GlassSurface.container/control} makes after
     * a successful panel (WINDOW_FILL, whose alpha is the single opacity
     * application point; keep the two in lockstep). Row BUTTONS are not in
     * the template: the shared {@code Button} supplies its own resting
     * surface per size/variant, so per-button state (press, decline) never
     * invalidates the row raster.
     */
    protected void captureRowSurfaceShapes(GuiGraphics ctx, float x, float y, float w, boolean tinted) {
        paintRowShadow(ctx, (int) x, (int) y, (int) w);
        if (tinted) {
            RenderUtil.drawRoundedRectAA(ctx, x, y, w, rowHeight(),
                    ThemeManager.current().roundness().radiusSmall(),
                    ThemeManager.color(ThemeToken.WINDOW_FILL));
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (rowTplTinted != null) { rowTplTinted.dispose(); rowTplTinted = null; }
        if (rowTplBare != null) { rowTplBare.dispose(); rowTplBare = null; }
    }

    // ------------------------------------------------------------------
    // Scrolling & scrollbar (the R2 Part C treatment, shared verbatim)
    // ------------------------------------------------------------------

    /** Total scrollable overflow of the row list (input-event path refetches). */
    protected double maxScroll() {
        return maxScrollOf(currentRows());
    }

    /** {@link #maxScroll()} against an already-fetched list (the per-frame path). */
    protected double maxScrollOf(List<T> rows) {
        int total = (rows.size() + leadingRowCount()) * rowStep();
        return Math.max(0, total - (this.height - listTop() - listBottomPad()));
    }

    /** The thumb's current top/height in screen coordinates (int-truncated for painting). */
    private int thumbHeightPx(double maxScroll, int trackH) {
        return (int) scroll.thumbHeight(trackH, maxScroll, 24);
    }

    private int thumbYPx(double maxScroll, int trackH, int thumbH) {
        return listClipTop() + (int) ((trackH - thumbH) * scroll.ratio(maxScroll));
    }

    /**
     * The list's scrollbar thumb — the shared-look treatment both manager
     * screens adopted together (R2 Part C): a 3px capsule
     * ({@code ON_OVERLAY} at 0x30 alpha, 0x55 on hover/drag — the pack
     * browser's hover-responsive idiom in these screens' token vocabulary,
     * since everything here draws over the dim) running down the clip band,
     * six pixels right of the list. Geometry and drag state come from
     * {@link SmoothScroll}. Never glass — thumbs are opaque token surfaces
     * by the mod-wide conventions.
     */
    private void drawScrollbar(GuiGraphics ctx, int mouseX, int mouseY, int listX, double maxScroll) {
        if (maxScroll <= 0) return;
        int trackTop = listClipTop();
        int trackH = listClipBottom() - trackTop;
        int trackX = listX + listWidth() + 6;
        int thumbH = thumbHeightPx(maxScroll, trackH);
        int thumbY = thumbYPx(maxScroll, trackH, thumbH);
        boolean hover = mouseX >= trackX - 2 && mouseX <= trackX + 5
                && mouseY >= thumbY && mouseY <= thumbY + thumbH;
        int col = (hover || scroll.isDragging())
                ? ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_OVERLAY), 0x55)
                : ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_OVERLAY), 0x30);
        RenderUtil.drawRoundedRectAA(ctx, trackX, thumbY, 3, thumbH, 2, col);
    }

    // ------------------------------------------------------------------
    //  Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent _ev, boolean _doubleClicked) {
        double mouseX = _ev.x();
        double mouseY = _ev.y();
        int button = _ev.button();
        if (button != 0) return super.mouseClicked(_ev, _doubleClicked);

        // Editors first.
        if (editorClickFirst(_ev, _doubleClicked)) return true;

        // Screen-specific pre-row controls (the Profile create button).
        if (rowAreaClickFirst(mouseX, mouseY)) return true;

        int listX = (this.width - listWidth()) / 2;
        List<T> rows = currentRows();
        int y = listTop() - (int) scroll.current() + leadingRowCount() * rowStep();
        int clipTop = listClipTop();
        int clipBot = listClipBottom();
        for (int i = 0; i < rows.size(); i++) {
            T row = rows.get(i);
            int rowTop = y;
            int rowBot = y + rowHeight();

            // Hit-testing agrees with the render scissor: only the VISIBLE
            // part of the row is clickable (rows scrolled above the clip —
            // or below it — are not), and only within the row's horizontal
            // extent.
            if (mouseY >= Math.max(rowTop, clipTop) && mouseY < Math.min(rowBot, clipBot)
                    && mouseX >= listX && mouseX < listX + listWidth()) {
                if (rowClicked(mouseX, mouseY, listX, row, i)) return true;
            }
            y += rowStep();
        }

        // Scrollbar thumb grab — checked before the fallthrough commit so
        // grabbing the scroll never cancels an open editor (wheel scrolling
        // doesn't either; the thumb is a list control, not an outside click).
        double maxScroll = maxScroll();
        if (maxScroll > 0) {
            int trackTop = listClipTop();
            int trackH = listClipBottom() - trackTop;
            int trackX = listX + listWidth() + 6;
            int thumbH = thumbHeightPx(maxScroll, trackH);
            int thumbY = thumbYPx(maxScroll, trackH, thumbH);
            if (mouseX >= trackX - 3 && mouseX <= trackX + 6
                    && mouseY >= thumbY - 4 && mouseY <= thumbY + thumbH + 4) {
                // Grab-where-clicked, with the grab offset clamped into the
                // thumb so clicking the generous hit-padding grabs the
                // nearest edge instead of jumping the thumb.
                int grab = (int) Math.max(0, Math.min(thumbH, mouseY - thumbY));
                scroll.beginThumbDrag(grab);
                return true;
            }
        }

        // Click outside any row/field commits/cancels editors.
        commitEditors();
        return super.mouseClicked(_ev, _doubleClicked);
    }

    /** First shot at left clicks for any inline editor fields; {@code true} = consumed. */
    protected abstract boolean editorClickFirst(MouseButtonEvent _ev, boolean _doubleClicked);

    /** Pre-row-row controls that live in the list area but outside the rows (the Profile create button). */
    protected boolean rowAreaClickFirst(double mouseX, double mouseY) { return false; }

    /**
     * A click inside a visible row's band and horizontal extent: row buttons
     * first, then the name area (inline edit), then any row-body action.
     * Returns whether the click was consumed; an unconsumed click falls
     * through to the next row and eventually the outside-click commit.
     */
    protected abstract boolean rowClicked(double mouseX, double mouseY, int listX, T row, int index);

    @Override
    public boolean mouseDragged(MouseButtonEvent _ev, double dx, double dy) {
        if (scroll.isDragging() && _ev.button() == 0) {
            // Direct manipulation: the position pins to the cursor while
            // dragging and eases again on release.
            scroll.dragThumb(_ev.y(), listClipTop(), listClipBottom() - listClipTop(),
                    maxScroll(), 24, true);
            return true;
        }
        return super.mouseDragged(_ev, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent _ev) {
        if (scroll.isDragging()) {
            scroll.endThumbDrag();
            return true;
        }
        return super.mouseReleased(_ev);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll.wheel(vertical, 30, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent _kev) {
        if (nameField != null && nameField.isFocused()) {
            int key = _kev.key();
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                commitEdit();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                cancelEdit();
                return true;
            }
            if (nameField.keyPressed(_kev)) return true;
        }
        return super.keyPressed(_kev);
    }

    @Override
    public boolean charTyped(CharacterEvent _ev) {
        if (nameField != null && nameField.isFocused() && nameField.charTyped(_ev)) return true;
        return super.charTyped(_ev);
    }

    // ------------------------------------------------------------------
    //  Inline rename editor lifecycle
    // ------------------------------------------------------------------

    /** Opens the inline rename editor over row {@code index}, committing anything open first. */
    protected void startEditing(int index, String currentValue) {
        commitEditors();
        editingIndex = index;
        nameField = new EditBox(this.font, 0, 0, nameColumnWidth(), 16, Component.literal("Name"));
        nameField.setMaxLength(48);
        nameField.setValue(currentValue);
        nameField.setFocused(true);
        nameField.moveCursorToEnd(false);
    }

    /** Applies the edited value (non-empty, trimmed) to the row it was opened on. */
    protected abstract void applyRename(T row, String newValue);

    /** Commits/cancels every open editor on this screen (the Profile screen has two). */
    protected abstract void commitEditors();

    /**
     * Commits the rename editor: resolves the edited row in the live list,
     * closes the editor, and hands the trimmed value to
     * {@link #applyRename} when non-empty. Empty values cancel.
     */
    protected void commitEdit() {
        if (editingIndex < 0 || nameField == null) return;
        List<T> rows = currentRows();
        if (editingIndex >= rows.size()) { cancelEdit(); return; }
        T row = rows.get(editingIndex);
        String v = nameField.getValue().trim();
        cancelEdit();
        if (!v.isEmpty()) applyRename(row, v);
    }

    /** Closes the rename editor without applying. */
    protected void cancelEdit() {
        editingIndex = -1;
        nameField = null;
    }

    /**
     * Positions the rename editor over its row for this frame and returns
     * it, or {@code null} when none is visible. Shared by the glass pass
     * (which paints the field's surface at that position) and the content
     * pass (which renders it), so the two can never disagree.
     */
    protected EditBox layoutRenameField(List<T> rows, int listX) {
        if (nameField == null || editingIndex < 0 || editingIndex >= rows.size()) return null;
        int rowY = listTop() - (int) scroll.current()
                + leadingRowCount() * rowStep()
                + editingIndex * rowStep();
        if (rowY < listTop() - rowHeight() || rowY >= listClipBottom()) return null;
        nameField.setX(editorFieldX(listX));
        nameField.setY(rowY + (rowHeight() - 16) / 2);
        return nameField;
    }

    // ------------------------------------------------------------------
    //  Toast, close, misc
    // ------------------------------------------------------------------

    /** Transient bottom-center confirmation (1.5 s). */
    protected void flash(String text) {
        toast.flash(text, 1500L);
    }

    /** Renders the toast; call from the screen's tail (screen-owned order). */
    protected void renderToast(GuiGraphics ctx) {
        toast.render(ctx, this.font, this.width, this.height, 4);
    }

    /** Persist screen state on close if the screen owns any (the Waypoint screen saves the config). */
    protected void saveOnClose() {}

    @Override
    public void onClose() {
        commitEditors();
        saveOnClose();
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
