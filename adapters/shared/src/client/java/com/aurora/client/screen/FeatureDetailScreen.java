package com.aurora.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.screen.setting.FeatureSetting;
import com.aurora.client.screen.setting.ThemePreviewSetting;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.interaction.SemanticControlGroup;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.SmoothScroll;
import com.aurora.client.ui.layout.ScreenLayout;
import com.aurora.client.ui.util.ClipBand;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.UiLayerCache;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Detail screen for a single feature — lists the feature's settings as
 * rows. Reachable via right-click on a tile that has settings.
 *
 * <p>Modernized to use the high-performance 'new' renderer, matching AuroraScreen.
 *
 * <p>Glass rollout: every detail screen now uses the glass material (the
 * former {@code GLASS_PILOT_IDS} staged set is retired — glass is the
 * default look mod-wide). Per-setting glass flags default to on as well;
 * the registry's historical pilot flags are redundant no-ops.
 *
 * <p>§6 convention 6, structural (2026-09-10): this screen runs the guarded
 * frame skeleton — {@code GlassSurface.beginGlassPass} → every surface
 * (window, pills, segments, buttons, fields) → {@code GlassSurface.overlayDim}
 * → cached chrome blit + content — the same discipline
 * {@code ManagerListScreen} enforced first. The one deliberate exemption:
 * {@code EnumSetting}'s expanded popup is an above-the-dim surface and
 * paints in the content pass through {@code GlassSurface.aboveDimControl}.
 */
public class FeatureDetailScreen extends Screen implements ThemedScreen, LayoutScreen {
    @Override public boolean unifiedTheme() { return true; }
    @Override public boolean parentGlassOnly() { return true; }


    private int LIST_W = 320;
    // Rows keep the shared frame room on the left and the scrollbar's column on the right.
    private static final int ROW_LEFT = com.aurora.client.ui.util.UiDensity.FRAME_EDGE;
    private static final int ROW_RIGHT = com.aurora.client.ui.util.UiDensity.scrollRightInset();
    /** Settings paint from {@code SettingRowLayout}'s 8 px pad outside their row box. */
    private static final int SETTING_PAD = 8;
    private static int settingX(int listX) { return listX + ROW_LEFT - SETTING_PAD; }
    private int settingW() { return LIST_W - ROW_LEFT - ROW_RIGHT + 2 * SETTING_PAD; }
    private static final int SCROLLBAR_INSET = com.aurora.client.ui.util.UiDensity.FRAME_EDGE + com.aurora.client.ui.util.UiDensity.SCROLLBAR_W;
    private int TOP_PAD = 60;
    private int ROW_GAP = 6;

    private static final int DONE_W = 64;
    private static final int DONE_H = 22;
    private static final int DONE_RIGHT_MARGIN = 16;
    private static final int DONE_TOP_MARGIN = 14;

    /**
     * Title band: the feature name sits above the window, left-aligned at
     * the content column's text margin (window left + the 12px indent every
     * row label uses). Vertically it shares the Done/Reset row's band.
     */
    private static final int TITLE_Y = 16;
    /** Title band plus the feature's description (§12.1), measured in {@link #layoutHeader}. */
    private int headerH = com.aurora.client.ui.util.UiDensity.HEADER;
    private List<String> descriptionLines = List.of();
    private String headerLine;
    private int bodyOffset() { return headerH + 4; }

    /** Wraps the description (at most two lines, whole words) and the health/credit line. */
    private void layoutHeader() {
        int width = LIST_W - 24;
        // The catalog's one-sentence summary where there is one: the long registry description
        // is written for tooltips and would push the first setting down by a paragraph.
        var catalog = com.aurora.client.feature.FeatureCatalog.feature(meta.id);
        String text = catalog != null && !catalog.summary().isBlank() ? catalog.summary() : meta.description;
        descriptionLines = this.font == null ? List.of()
                : com.aurora.client.ui.util.WordWrap.lines(text, width, 2, this.font::width);
        headerLine = meta.headerLine() == null || meta.headerLine().isEmpty() || this.font == null ? null
                : AuroraFontRenderer.ellipsize(this.font, meta.headerLine(), width, 0);
        int lines = descriptionLines.size() + (headerLine == null ? 0 : 1);
        headerH = com.aurora.client.ui.util.UiDensity.HEADER + (lines == 0 ? 0 : lines * descriptionPitch() + com.aurora.client.ui.util.TextAlignment.DESCRIPTOR_GAP);
    }
    private int descriptionPitch() { return AuroraFontRenderer.linePitch(this.font); }

    private final Screen parent;
    private final FeatureMetadata meta;
    /** τ = 60 ms, snap 0.25, wheel step 30, no thumb — the screen's exact pre-R2 feel. */
    private final SmoothScroll scroll = new SmoothScroll(60.0).boundedMotion();
    private final ScreenLayout rowsLayout = new ScreenLayout();
    private final ScreenLayout fixedLayout = new ScreenLayout();
    private int[] rowHeights;
    private long layoutFontKey = Long.MIN_VALUE;
    private int layoutWidth = -1, layoutTop, layoutHeight, layoutPopup;
    private double layoutScale;
    private FeatureSetting layoutPinned;
    private final java.util.Set<SemanticActionControl> previousControls =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final java.util.Set<SemanticActionControl> currentControls =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    @Override public ScreenLayout contentLayout() { return rowsLayout; }
    @Override public ScreenLayout fixedLayout() { return fixedLayout; }
    @Override public double layoutScroll() { return (int) scroll.current(); }
    private FeatureSetting activeDragSetting = null;
    private FeatureSetting fixedHeaderSetting;
    private int bodyTop() { return TOP_PAD + (fixedHeaderSetting == null ? 0 : fixedHeaderSetting.fixedHeaderHeight()); }
    private boolean compact() { return true; }
    private com.aurora.client.ui.util.UiDensity.Rect compactFrame;
    /** The shared window frame ({@link com.aurora.client.ui.util.UiDensity#detailFrame}). */
    private com.aurora.client.ui.util.UiDensity.Rect maxFrame;
    /** Body height at rest: the frame's whole body, or less so a scrolling body ends on a row boundary. */
    private int restBody;
    /** A scrolling body whose rest edge cuts through a tall row, so the bottom fade shows at rest too. */
    private boolean restCut;

    private void refreshLayout() {
        long fontKey = FeatureSetting.compactMeasurementKey();
        if (compact() && layoutFontKey != fontKey) {
            layoutHeader();
            TOP_PAD = compactFrame.y() + bodyOffset();
        }
        var settings = meta.settings;
        com.aurora.client.screen.setting.SectionHeaderSetting.markLeading(settings);
        boolean changed = rowHeights == null || rowHeights.length != settings.size();
        if (changed) {
            rowHeights = new int[settings.size()];
            java.util.Arrays.fill(rowHeights, -1);
        }
        int track = compact() ? FeatureSetting.pageTrackWidth(settings) : 0;
        FeatureSetting pinned = null;
        int popup = 0;
        for (int index = 0; index < settings.size(); index++) {
            FeatureSetting setting = settings.get(index);
            int measured = -1;
            if (setting.isVisible()) {
                if (compact()) {
                    setting.pageTrack(track);
                    setting.measureCompact(settingW());
                }
                boolean pin = pinned == null && setting.fixedHeaderHeight() > 0;
                setting.pinHeader(pin);
                if (pin) pinned = setting;
                measured = setting.height() - (pin ? setting.fixedHeaderHeight() : 0);
                if (setting instanceof com.aurora.client.screen.setting.EnumSetting<?> row)
                    popup = Math.max(popup, row.popupHeight() + 30);
                if (setting instanceof com.aurora.client.screen.setting.ColorSetting)
                    popup = Math.max(popup, com.aurora.client.screen.setting.ColorSetting.editorHeight());
            } else if (rowHeights[index] >= 0) {
                for (SemanticActionControl control : setting.controlsWithReset()) {
                    control.setAvailable(false);
                    if (this.getFocused() == control) this.setFocused(null);
                }
                setting.onInteractionAvailabilityChanged(false);
                if (FeatureSetting.getFocused() == setting) FeatureSetting.clearFocus();
                if (activeDragSetting == setting) activeDragSetting = null;
            }
            changed |= measured != rowHeights[index];
            rowHeights[index] = measured;
        }
        fixedHeaderSetting = pinned;
        double scale = this.minecraft.getWindow().getGuiScale();
        int listX = (this.width - LIST_W) / 2;
        int top = bodyTop();
        if (!changed && layoutFontKey == fontKey && layoutWidth == LIST_W && layoutTop == top
                && layoutHeight == this.height && layoutScale == scale && layoutPinned == pinned
                && layoutPopup == popup && rowsLayout.viewport().x == listX + ROW_LEFT) {
            FeatureSetting.setHostBand(rowsLayout.viewport());
            return;
        }
        layoutFontKey = fontKey;
        layoutWidth = LIST_W;
        layoutTop = top;
        layoutHeight = this.height;
        layoutScale = scale;
        layoutPinned = pinned;
        layoutPopup = popup;
        int nextTop = top;
        rowsLayout.begin(null);
        for (int index = 0; index < settings.size(); index++) {
            int rowHeight = rowHeights[index];
            if (rowHeight < 0) continue;
            FeatureSetting setting = settings.get(index);
            nextTop = rowsLayout.stack(setting, meta.id + "/" + index, listX + ROW_LEFT, nextTop,
                    LIST_W - ROW_LEFT - ROW_RIGHT, rowHeight, ROW_GAP, true);
        }
        int extent = Math.max(0, nextTop - top - ROW_GAP);
        if (compact()) fitBody(extent, popup);
        rowsLayout.finish(new ClipBand(listX + ROW_LEFT, top, LIST_W - ROW_LEFT - ROW_RIGHT, bodyBottom() - top), extent);
        var fixedBand = new ClipBand(listX + ROW_LEFT, TOP_PAD, LIST_W - ROW_LEFT - ROW_RIGHT, top - TOP_PAD);
        fixedLayout.begin(fixedBand);
        if (pinned != null) fixedLayout.place(pinned, meta.id + "/pinned", fixedBand.x, fixedBand.y,
                fixedBand.width, fixedBand.height, true);
        fixedLayout.finish(fixedBand, 0);
        FeatureSetting.setHostBand(rowsLayout.viewport());
    }

    private void fitBody(int extent, int popup) {
        int top = bodyTop();
        int body = Math.min(compactFrame.y() + compactFrame.height(), this.height - 12) - top - BODY_BOTTOM - 2;
        restBody = body;
        restCut = false;
        if (Math.max(extent, popup) <= body) return;
        int fit = 0, beforeCaption = 0;
        boolean lastIsCaption = false;
        for (int index = 0; index < meta.settings.size(); index++) {
            FeatureSetting setting = meta.settings.get(index);
            ScreenLayout.Box box = rowsLayout.bounds(setting);
            if (box == null) continue;
            int bottom = (int) (box.top() + box.height()) - top;
            if (bottom > body) break;
            lastIsCaption = setting instanceof com.aurora.client.screen.setting.SectionHeaderSetting;
            if (lastIsCaption) beforeCaption = fit;
            fit = bottom;
        }
        if (lastIsCaption && beforeCaption > 0) fit = beforeCaption;
        if (fit > 0 && fit >= popup && body - fit <= com.aurora.client.ui.util.UiDensity.STACKED_ROW + ROW_GAP)
            restBody = fit;
        else restCut = true;
    }
    private int bodyBottom() { return compact() ? bodyTop() + restBody + 2 : Math.max(bodyTop() + 1, this.height - 30); }
    /** Inset below the last row, clear of the window's corner radius (12, was 8; 2026-11-14). */
    private static final int BODY_BOTTOM = 12;
    private int settingY(FeatureSetting setting, int bodyY) { return bodyY - (setting == fixedHeaderSetting ? setting.fixedHeaderHeight() : 0); }


    /** Independent caches: stationary chrome and scrolling body shapes. */
    private final UiLayerCache rowCache = new UiLayerCache();

    /** Shared themed window panel (glow + fill + outline), drawn into the static cache. */

    /**
     * The Done/Reset chrome buttons — kept as fields so the glass pass can
     * drive their surfaces pre-dim (ButtonWidget.renderGlassPass), the same
     * way ManagerListScreen drives its toolbar pair.
     */
    private ButtonWidget doneBtn;
    private ButtonWidget resetBtn;

    /**
     * Press-again confirmation for Reset (design language §8.7): the first
     * press arms (label "Confirm reset", the shared destructive styling), a
     * second press within the window resets, and the timeout, Escape or any
     * other click disarms. {@link #syncResetButton} polls the state each
     * frame so the timeout restores the label with no timer.
     */
    private final com.aurora.client.ui.interaction.ArmedAction resetConfirm = new com.aurora.client.ui.interaction.ArmedAction();
    /** The label/styling state {@link #syncResetButton} last applied (change detection only). */
    private boolean resetBtnArmed;
    /** Transient bottom-center confirmation after a completed reset (the manager screens' Toast). */
    private final com.aurora.client.ui.component.Toast resetToast = new com.aurora.client.ui.component.Toast();

    // Transient frame-timing instrumentation (debug level only).
    private long lastPerfLogMs = 0L;
    private long lastPerfFills = 0L;
    /**
     * True until the first {@link #init()} completes. Minecraft re-runs
     * {@code init()} on window resize as well as on open, so we gate the
     * one-shot {@link FeatureSetting#onDetailScreenOpen()} notifications
     * on this flag — otherwise a resize would wipe a search query the
     * player is mid-typing. A new screen instance is created for every
     * open, so this correctly resets per visit.
     */
    private boolean firstInit = true;

    /**
     * C-4: the controls this screen last registered per setting — the diff
     * baseline for DYNAMIC control sets (ItemScale's per-row removes). The
     * per-frame sweep below compares each setting's fresh
     * {@code interactionControls()} against it and registers/unregisters the
     * delta through the widget lifecycle, so a row added or removed
     * mid-session gets exactly one control and no semantic child outlives
     * its row (a removed control that held focus drops it here).
     */
    private final java.util.Map<FeatureSetting, java.util.List<SemanticActionControl>>
            registeredSettingControls = new java.util.IdentityHashMap<>();
    /** Guards lifecycle teardown because onClose() replaces the screen and
     *  Minecraft then calls removed() on the same instance. */
    private boolean transientStateClosed = false;

    public FeatureDetailScreen(Screen parent, FeatureMetadata meta) {
        super(Component.literal(meta.displayName));
        this.parent = parent;
        this.meta = meta;
    }

    /**
     * Search reveal (2026-11-12): a setting a search result matched. On the
     * first frame its section opens and the list scrolls it into view; it
     * then wears a fading accent outline for {@link #REVEAL_MS}.
     */
    private FeatureSetting pendingReveal;
    private FeatureSetting revealed;
    private long revealStartMs;
    private static final long REVEAL_MS = 1600L;

    /** Scrolls {@code setting} into view and briefly outlines it (see {@link #pendingReveal}). Returns this screen. */
    public FeatureDetailScreen reveal(FeatureSetting setting) {
        if (setting != null && meta.settings.contains(setting)) pendingReveal = setting;
        return this;
    }

    /** Opens the section the reveal target sits in, if it is collapsed. */
    private void expandRevealSection() {
        com.aurora.client.screen.setting.SectionHeaderSetting section = null;
        for (FeatureSetting setting : meta.settings) {
            if (setting == pendingReveal) break;
            if (setting instanceof com.aurora.client.screen.setting.SectionHeaderSetting header) section = header;
        }
        if (section != null && !section.expanded()) section.toggle();
    }

    /** The reveal outline: accent, fading out over its last half. */
    private void paintReveal(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        long age = System.currentTimeMillis() - revealStartMs;
        if (age >= REVEAL_MS) {
            revealed = null;
            return;
        }
        float fade = Math.min(1f, 2f * (1f - age / (float) REVEAL_MS));
        int accent = MaterialSurface.color(ThemeToken.ACCENT);
        com.aurora.client.ui.util.RenderUtil.drawRoundedOutlineAA(ctx, x, y, w, h,
                ThemeManager.current().roundness().radiusSmall(), 1f,
                ThemeManager.withAlpha(accent, Math.round((accent >>> 24) * fade)));
    }

    /** Features with a dedicated destination skip the generic settings wrapper. */
    public static Screen destination(Screen parent, FeatureMetadata meta) {
        return "resourcepack_browser".equals(meta.id)
                ? new ResourcePackBrowserScreen(parent) : new FeatureDetailScreen(parent, meta);
    }

    @Override
    protected void init() {
        com.aurora.client.screen.setting.SectionHeaderSetting.bind(meta.settings, meta.id);
        com.aurora.client.screen.setting.SectionHeaderSetting.markLeading(meta.settings);
        if (compact()) {
            maxFrame = com.aurora.client.ui.util.UiDensity.detailFrame(this.width, this.height);
            compactFrame = maxFrame;
            LIST_W = compactFrame.width(); layoutHeader(); TOP_PAD = compactFrame.y() + bodyOffset();
            ROW_GAP = com.aurora.client.ui.util.UiDensity.ROW_GAP;
            layoutFontKey = Long.MIN_VALUE;
            refreshLayout();
            // Collapse categories by default only when the fully expanded list would scroll.
            if (com.aurora.client.screen.setting.SectionHeaderSetting.setDefaultExpanded(meta.settings, true)) {
                com.aurora.client.screen.setting.SectionHeaderSetting.markLeading(meta.settings);
                refreshLayout();
                int total = 0;
                for (FeatureSetting setting : meta.settings) if (setting.isVisible()) total += setting.height() + ROW_GAP;
                if (total > bodyBottom() - TOP_PAD) {
                    com.aurora.client.screen.setting.SectionHeaderSetting.setDefaultExpanded(meta.settings, false);
                    com.aurora.client.screen.setting.SectionHeaderSetting.markLeading(meta.settings);
                    refreshLayout();
                }
            }
        }
        // Done — the rollout's vanilla-backed representative (Phase A): the
        // SemanticAction owns the behavior callback while vanilla keeps the
        // pointer/keyboard/focus routing AND the click sound (the action is
        // SemanticSound.NONE, so nothing doubles), and its description
        // supplements the button narration. Reset stays legacy (destructive,
        // excluded from this rollout).
        this.doneBtn = ButtonWidget.semantic(
                this.width - DONE_W - DONE_RIGHT_MARGIN, DONE_TOP_MARGIN,
                DONE_W, DONE_H,
                Component.literal("Done"),
                "Close this screen and return.",
                this::onClose).glassStyle(com.aurora.client.ui.component.Button.GlassStyle.STAINED).primary(true);
        // Done is the screen's primary action, accent-filled; Reset beside it is a quiet ghost
        // (owner decision 2026-11-14, superseding the 2026-10-19 neutral-Done ruling).
        this.addRenderableWidget(this.doneBtn);

        // Reset-to-defaults button immediately to the left of Done. Persists
        // immediately so the change is visible to other open screens. Phase A:
        // vanilla-backed semantic like Done (destructive — the semantic layer
        // only names it; colors/labels/behavior are untouched). Destructive
        // confirmation (design language §8.7): press again within 3 s to
        // fire; Escape or any other click disarms. The button is sized from
        // the WIDER of its two labels so "Confirm reset" never ellipsizes or
        // shifts Done ("Confirm reset" measures ~50 px at the compact 8 px
        // raster against the 28 px label room of a 48 px HEADER_BUTTON_W).
        int resetW = Math.max(70, this.font.width("Confirm reset") + 22);
        int resetX = this.width - DONE_W - DONE_RIGHT_MARGIN - resetW - 6;
        this.resetBtn = ButtonWidget.semantic(
                resetX, DONE_TOP_MARGIN,
                resetW, DONE_H,
                Component.literal(resetConfirm.isArmed() ? "Confirm reset" : "Reset"),
                "Resets this feature's settings to their defaults and saves. Press again to confirm.",
                () -> {
                    if (resetConfirm.press()) {
                        meta.reset();
                        AuroraConfig.save();
                        resetToast.flash("Reset " + meta.displayName, 1500L);
                    }
                    syncResetButton();
                }).glassBackground(true).theming(com.aurora.client.ui.component.Button.Theming.GHOST); // secondary to Done (2026-11-14)
        this.resetBtn.destructive(resetConfirm.isArmed());
        this.addRenderableWidget(this.resetBtn);

        // Custom-painted semantic controls join vanilla's child/narratable
        // lifecycle without joining its render list. Their setting remains
        // the sole pixel and clipped-pointer owner.
        if (compact()) {
            int bw = com.aurora.client.ui.util.UiDensity.HEADER_BUTTON_W, bh = com.aurora.client.ui.util.UiDensity.HEADER_BUTTON_H;
            doneBtn.setWidth(bw); doneBtn.setHeight(bh);
            doneBtn.setX(compactFrame.x() + LIST_W - bw - com.aurora.client.ui.util.UiDensity.FRAME_EDGE);
            doneBtn.setY(compactFrame.y() + Math.round(com.aurora.client.ui.util.UiDensity.headerY(bh)));
            // Reset keeps Done's height and position anchor but its OWN width — the wider
            // of its two labels (see init); Done's right-aligned slot never moves.
            int resetBw = Math.max(bw, this.font.width("Confirm reset") + 22);
            resetBtn.setWidth(resetBw); resetBtn.setHeight(bh);
            resetBtn.setX(doneBtn.getX() - resetBw - 4); resetBtn.setY(doneBtn.getY());
        }
        resetBtnArmed = resetConfirm.isArmed();
        registeredSettingControls.clear();
        for (FeatureSetting setting : meta.settings) {
            var controls = setting.controlsWithReset();
            registeredSettingControls.put(setting, controls);
            for (SemanticActionControl control : controls) {
                control.setFocused(false);
                control.setAvailable(false);
                this.addWidget(control);
            }
        }

        // Notify every setting the first time this screen instance is
        // initialized (i.e. on open) so stateful settings (e.g. a search
        // field) can claim initial focus / reset themselves. Skipped on
        // subsequent init() calls (window resize) so a player mid-typing
        // doesn't lose their query. Settings are long-lived singletons
        // shared across opens, so onDetailScreenOpen() is the natural
        // place to (re)initialize per-open transient state.
        if (firstInit) {
            firstInit = false;
            for (FeatureSetting s : meta.settings) {
                s.onDetailScreenOpen();
            }
        }
    }

    /**
     * True when the main render target holds a live world — i.e. the glass
     * capture source is valid. Mirrors {@code BlurPanelRenderer}'s own
     * menu-context guard so this screen never drops the vanilla backdrop in
     * a context where glass cannot engage (no level loaded: the renderer
     * declines and the opaque fallback wants the vanilla backdrop).
     */
    private boolean liveWorldBackdrop() {
        return this.minecraft != null && this.minecraft.level != null;
    }

    /**
     * Re-labels and re-styles Reset from the confirmation state — called on
     * press and once per frame, so the 3 s timeout restores "Reset" with no
     * timer. Armed state is carried by the LABEL and the shared destructive
     * styling (the resting ghost becomes the destructive field face with its
     * red label); never by a new tint (§6 convention 9).
     */
    private void syncResetButton() {
        if (resetBtn == null) return;
        boolean armed = resetConfirm.isArmed();
        if (armed == resetBtnArmed) return;
        resetBtnArmed = armed;
        resetBtn.setMessage(Component.literal(armed ? "Confirm reset" : "Reset"));
        resetBtn.destructive(armed);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // With a live world behind the screen, skip vanilla's background
        // sandwich (full-screen blur + dark gradient) — the glass panels
        // must sample the LIVE world, not an already-darkened,
        // already-blurred backdrop. With no level loaded the glass renderer
        // declines anyway (its menu-context guard) and the opaque fallback
        // wants the vanilla backdrop as before, so the override is
        // conditional on the same validity check the renderer uses.
        if(MaterialSurface.unified() && GlassSurface.renderMenuPanorama(this,g,delta)) return;
        if (liveWorldBackdrop()) return;
        super.extractBackground(g, mouseX, mouseY, delta);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        syncResetButton(); // the confirm timeout restores the label with no timer
        if (pendingReveal != null) expandRevealSection();
        refreshLayout();
        int listX = rowsLayout.viewport().x - 8;
        int totalRowsH = rowsLayout.extent();
        double maxScroll = rowsLayout.maxScroll();
        if (pendingReveal != null) {
            var target = rowsLayout.bounds(pendingReveal);
            float offset = target == null ? 0 : target.top() - bodyTop();
            // A third of the way down the body, so the rows above give context.
            scroll.set(Math.max(0, Math.min(maxScroll, offset - (bodyBottom() - bodyTop()) / 3.0)));
            revealed = pendingReveal;
            revealStartMs = System.currentTimeMillis();
            pendingReveal = null;
        }

        scroll.advance(maxScroll);

        // ---- One stationary container window ----
        final int WINDOW_PAD_TOP    = 10;
        final int WINDOW_PAD_BOTTOM = 10;

        int windowY = compact() ? compactFrame.y() : TOP_PAD - WINDOW_PAD_TOP;
        int windowH = compact() ? compactFrame.height() : bodyBottom() - windowY + WINDOW_PAD_BOTTOM;

        // ---- 1. Glass pass — every glass surface paints BEFORE the dim ----
        // (§6 convention 6, structural: the ManagerListScreen skeleton.) The
        // pass opens here so each surface's rim finish defers past the dim;
        // GlassSurface.overlayDim below closes it, paints the dim, and
        // flushes the deferred rims. Live GPU glass UNDER the cached layer:
        // the dim veils the panel and its surroundings equally — the backdrop
        // must not read brighter inside the panel than outside it. The window
        // panel uses the depressed treatment (main containers read as
        // recessed); its tint is the ordinary WINDOW_FILL fill, whose alpha
        // already carries the theme's Background Opacity — still exactly one
        // application point. When the renderer declines (no world, screenshot
        // suppression, tiny/clipped panel, failure), the unified material's
        // cached face stays the same. Legacy chrome includes that fallback.
        GlassSurface.beginGlassPass();
        float frameRadius = ThemeManager.current().roundness().radius();
        GlassSurface.container(ctx, listX, windowY, LIST_W, windowH, frameRadius);

        // ---- Static-layer caches: window chrome + rows' cacheable shapes ----
        // TWO caches, not one: the rows' shape layer is blitted under the
        // TOP_FADE_Y scissor (see below) while the window chrome blits
        // unscissored (the window panel itself is allowed to slide up under
        // the title band — only ROW content must stop at the boundary).
        // Bounds invalidate moved/resized captures. Chrome needs the theme
        // and raster scale; rows additionally depend on scroll and shapes.
        // Glass availability never changes the unified cached face or rows.
        int guiScale = Math.max(1, (int) this.minecraft.getWindow().getGuiScale());
        int rowX = listX + ROW_LEFT, rowY = bodyTop();
        int rowW = LIST_W - ROW_LEFT - ROW_RIGHT, rowH = Math.max(1, bodyBottom() - rowY);
        rowCache.ensureRegion(rowX * guiScale, rowY * guiScale, rowW * guiScale, rowH * guiScale);
        int fingerprint = 0;
        for (FeatureSetting s : meta.settings) if (s.isVisible()) fingerprint = fingerprint * 31 + s.shapeFingerprint();
        long baseVersion = ThemeManager.generation() * 1_000_003L ^ ((long) guiScale * 65537L);
        long rowVersion = baseVersion ^ ((long) fingerprint * 100_000_009L)
                ^ ((long) totalRowsH * 31L) ^ ((long) (int) scroll.current() * 104729L) ^ bodyTop();

        if (!rowCache.isCurrent(rowVersion)) {
            rowCache.clear();
            RenderUtil.RectSink prev = RenderUtil.beginCapture(rowCache.sink());
            try {
                for (FeatureSetting s : meta.settings) {
                    var box = rowsLayout.bounds(s);
                    if (box != null && rowsLayout.visible(box, layoutScroll()))
                        s.renderShapes(ctx, settingX(listX), settingY(s, (int) box.y(layoutScroll())), settingW(), 0, 0);
                }
            } finally {
                RenderUtil.endCapture(prev);
            }
            rowCache.commit(rowVersion);
        }

        // Per-setting glass pass — after the capture pass above (a tint fill
        // painted during capture would rasterize into the cache) and before
        // the dim + cached blit, so cached content rasterized into the cache
        // stacks on top of the glass. The screen walks each row's CURRENT
        // geometry (same walk the overlay loop below uses, so a scroll
        // frame's pass paints exactly where the content will) and every
        // glass-painting setting takes it from there: the preview card, the
        // pill widgets (enum trigger/keybind/key-list/item-scale "+"), the
        // segmented tracks and ButtonSetting rows via their embedded shared
        // components, and the search/numeric fields via EditBoxMixin's
        // frame-stamp split. Widgets gate on GlassSurface.passOpen()
        // themselves — which is now open — so on screens still on the legacy
        // frame order (AuroraScreen's inline Settings rows) the same widgets
        // keep painting in place inside render, unchanged. Falls back per
        // element when declined. EnumSetting's expanded popup is the one
        // deliberate above-the-dim surface (§9): it keeps painting in the
        // content pass through GlassSurface.aboveDimControl.
        if (fixedHeaderSetting != null)
            fixedHeaderSetting.renderFixedHeaderGlass(ctx, settingX(listX), (int) fixedLayout.at(0).top(), settingW());
        GlassSurface.enableScissor(ctx, listX + ROW_LEFT, bodyTop(), listX + LIST_W - ROW_RIGHT, bodyBottom());
        for (FeatureSetting s : meta.settings) {
            var box = rowsLayout.bounds(s);
            if (box == null) {
                var hidden = registeredSettingControls.get(s);
                if (hidden != null) for (var control : hidden) control.setScrollFocusTarget(false);
                continue;
            }
            int gy = (int) box.y(layoutScroll());
            int gh = (int) box.height();
            boolean rowVisible = rowsLayout.visible(box, layoutScroll());
            var controls = s.controlsWithReset();
            if (!controls.isEmpty()) {
                // C-4 dynamic-set diff: mutation-time list changes (rows
                // added/removed) register/unregister here — zero allocation
                // on the unchanged path (identity compare against the
                // setting's own cached list).
                var prev = registeredSettingControls.put(s, controls);
                if (prev != controls) {
                    previousControls.clear();
                    currentControls.clear();
                    if (prev != null) previousControls.addAll(prev);
                    currentControls.addAll(controls);
                    for (SemanticActionControl c : controls) {
                        if (!previousControls.contains(c)) {
                            c.setFocused(false);
                            c.setAvailable(false);
                            this.addWidget(c);
                        }
                    }
                    if (prev != null) for (SemanticActionControl c : prev) {
                        if (!currentControls.contains(c)) {
                            if (this.getFocused() == c) this.setFocused(null);
                            this.removeWidget(c);
                        }
                    }
                }
                boolean available = s == fixedHeaderSetting || rowVisible;
                for (SemanticActionControl control : controls) {
                    control.setAvailable(available);
                    boolean pinned = s == fixedHeaderSetting && s.fixedHeaderControls().contains(control);
                    control.setScrollFocusTarget(!pinned && s.controlVisible(control));
                    if (!available) control.setBounds(listX + ROW_LEFT, gy, LIST_W - ROW_LEFT - ROW_RIGHT, gh);
                }
                s.onInteractionAvailabilityChanged(available);
            }
            if (rowVisible) {
                s.renderGlassPass(ctx, settingX(listX), settingY(s, gy), settingW());
            }
        }

        GlassSurface.disableScissor(ctx);

        // Chrome buttons' surfaces — the ButtonWidget discipline from
        // ManagerListScreen; their super.render below then paints labels only.
        if (doneBtn != null) doneBtn.renderGlassPass(ctx);
        if (resetBtn != null) resetBtn.renderGlassPass(ctx);

        // ---- 2. Dim — closes the glass pass, veils every surface like it ----
        // veils the world, flushes the deferred rim finishes, and from here
        // on any glass body paint is reported as an ordering violation.
        GlassSurface.overlayDim(ctx, this.width, this.height);

        // Stationary frame chrome sits above dim and outside the body scissor.
        MaterialSurface.parent(ctx, listX, windowY, LIST_W, windowH, frameRadius,
                MaterialSurface.frameInset(frameRadius));

        // Only the body scrolls; the frame and search header have fixed geometry.
        ctx.enableScissor(listX + ROW_LEFT, bodyTop(), listX + LIST_W - ROW_RIGHT, bodyBottom());

        // Rows' cached shape layer — the second blit, under the boundary.
        rowCache.blitAt(ctx, rowX, rowY, rowW, rowH);

        // Live overlay: text, hover feedback, animated values.
        for (FeatureSetting s : meta.settings) {
            var box = rowsLayout.bounds(s);
            if (box == null) continue;
            int y = (int) box.y(layoutScroll());
            int h = (int) box.height();
            if (rowsLayout.visible(box, layoutScroll())) {
                s.renderOverlay(ctx, settingX(listX), settingY(s, y), settingW(), mouseX, mouseY);
                if (s == revealed) paintReveal(ctx, listX + ROW_LEFT, y, LIST_W - ROW_LEFT - ROW_RIGHT, h);
            }
        }

        if (compact()) for (FeatureSetting setting : meta.settings) {
            if (!setting.isVisible()) continue;
            if (setting instanceof com.aurora.client.screen.setting.EnumSetting<?> row && row.popupVisible())
                row.renderFloatingOverlay(ctx, mouseX, mouseY);
            if (setting instanceof com.aurora.client.screen.setting.ColorSetting row) row.renderFloatingEditor(ctx);
        }
        ctx.disableScissor();
        if (compact()) {
            // Rows fade into the window tint at both clip edges while more content lies beyond.
            int fadeSurface = MaterialSurface.color(ThemeToken.WINDOW_FILL);
            if (totalRowsH == 0)
                ctx.text(this.font, "This feature has no additional settings.", listX + 12, bodyTop() + 2,
                        MaterialSurface.color(ThemeToken.ON_BACKGROUND_MUTED), false);
            com.aurora.client.util.ScrollFade.drawTop(ctx, listX + ROW_LEFT, LIST_W - ROW_LEFT - ROW_RIGHT, bodyTop(),
                    com.aurora.client.util.ScrollFade.FADE_PX, scroll.current(), fadeSurface);
            // At rest the body usually ends on a row boundary (fitFrame): fade only rows cut by
            // scrolling, or a tall row the rest edge runs through.
            if (scroll.current() > 0 || restCut) com.aurora.client.util.ScrollFade.drawBottom(ctx, listX + ROW_LEFT, LIST_W - ROW_LEFT - ROW_RIGHT, bodyBottom(),
                    com.aurora.client.util.ScrollFade.BOTTOM_FADE_PX, maxScroll - scroll.current(), fadeSurface);
        }
        if (compact()) {
            int titleX = com.aurora.client.ui.util.UiDensity.detailTitleX(compactFrame);
            // Title role (§4.1): the feature name in the heavier cut, centred on the Reset/Done row;
            // its description sits beneath in secondary text, the health or credit line last.
            String breadcrumb = AuroraFontRenderer.ellipsizeRole(this.font, meta.displayName, Math.max(0, resetBtn.getX() - titleX - 6), AuroraFontRenderer.Role.TITLE);
            ctx.text(this.font, AuroraFontRenderer.role(breadcrumb, AuroraFontRenderer.Role.TITLE), titleX,
                    AuroraFontRenderer.roleTextY(this.font, compactFrame.y() + com.aurora.client.ui.util.UiDensity.FRAME_EDGE, com.aurora.client.ui.util.UiDensity.TOOL, AuroraFontRenderer.Role.TITLE),
                    MaterialSurface.color(ThemeToken.ON_BACKGROUND), false);
            int lineY = AuroraFontRenderer.textY(this.font, compactFrame.y() + com.aurora.client.ui.util.UiDensity.HEADER
                    + com.aurora.client.ui.util.TextAlignment.DESCRIPTOR_GAP, this.font.lineHeight);
            for (String line : descriptionLines) {
                ctx.text(this.font, line, titleX, lineY, MaterialSurface.color(ThemeToken.ON_BACKGROUND_SECONDARY), false);
                lineY += descriptionPitch();
            }
            if (headerLine != null) ctx.text(this.font, headerLine, titleX, lineY,
                    com.aurora.client.health.FeatureHealth.headlineFor(meta.id) != null
                            ? ThemeManager.color(ThemeToken.STATUS_WARNING) : MaterialSurface.color(ThemeToken.ON_BACKGROUND_MUTED), false);
            if (maxScroll > 0) {
                var thumb = com.aurora.client.ui.component.ScrollbarChrome.thumb(scroll, bodyTop(), bodyBottom() - bodyTop(), maxScroll, 24, this.minecraft.getWindow().getGuiScale());
                // Centred in the 8 px band between the control edge and the window border
                // (2026-11-14): at LIST_W - 4 the bar touched the border.
                com.aurora.client.ui.component.ScrollbarChrome.track(ctx, listX + LIST_W - SCROLLBAR_INSET, bodyTop(), bodyBottom() - bodyTop(), ThemeToken.ON_BACKGROUND);
                com.aurora.client.ui.component.ScrollbarChrome.draw(ctx, listX + LIST_W - SCROLLBAR_INSET, thumb, scroll.isDragging(), ThemeToken.ON_BACKGROUND);
            }
        }

        if (fixedHeaderSetting != null)
            fixedHeaderSetting.renderFixedHeader(ctx, settingX(listX), (int) fixedLayout.at(0).top(), settingW(), mouseX, mouseY);

        // Reconcile nested controls against their final painted bounds. Hidden rows
        // cannot retain keyboard availability; the pinned Add control owns its header.
        var bodyBand = FeatureSetting.hostBand();
        for (FeatureSetting setting : meta.settings) {
            if (!setting.isVisible()) continue;
            var controls = registeredSettingControls.get(setting);
            if (controls == null) continue;
            for (SemanticActionControl control : controls) {
                boolean header = setting == fixedHeaderSetting && setting.fixedHeaderControls().contains(control)
                        && control.getY() >= TOP_PAD && control.getBottom() <= bodyTop();
                boolean visible = bodyBand.intersects(control.getX(), control.getY(), control.getWidth(), control.getHeight());
                control.setAvailable(control.isAvailable() && (header || visible) && setting.controlVisible(control));
                if (!control.isAvailable() && !control.isScrollFocusTarget() && this.getFocused() == control) this.setFocused(null);
            }
        }

        // Children: Done button.
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        // Design language §1 — screen title + attached subtitle. One title
        // per detail screen: the feature's name, full-strength
        // ON_OVERLAY, left-aligned above the window at the content
        // column's text margin. The spec believed this title already
        // existed ("no change to its existing token or position") — it
        // never did; the TITLE_* constants this implements were reserved
        // in the initial commit and never drawn, so the first clause (one
        // title per screen) is the evident intent being implemented. The
        // optional credit line (FeatureMetadata.subtitle) is ATTACHED
        // directly beneath the title in subdued ON_OVERLAY, no extra
        // margin — replacing the old placement vertically centered on the
        // Done row. "Smaller" collapses to the app's single 9px type size;
        // Aurora has no large/bold text idiom and inventing one (pose-
        // scaled glyphs) would violate the no-new-visual-style constraint.

        // Label-hover description tooltip floats above the rows.
        FeatureSetting.drawPendingTooltip(ctx, listX, windowY, LIST_W, windowH);

        // Post-reset confirmation (the manager screens' toast): 1.5 s, bottom-center.
        resetToast.render(ctx, this.font, this.width, this.height,
                ThemeManager.current().roundness().radiusSmall());

        logFramePerf();
    }


    /** Debug-only per-frame cost log: fill submissions + frame time, every 2s. */
    private void logFramePerf() {
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
        double fillsPerSec = (fills - lastPerfFills) * 1000.0 / dt;
        com.aurora.client.AuroraClient.LOGGER.debug("[ui-perf] detail screen: {} fills submitted/sec ({} total)", (long) fillsPerSec, fills);
        lastPerfLogMs = now;
        lastPerfFills = fills;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent _ev, boolean _doubleClicked) {
        double mouseX = _ev.x(); double mouseY = _ev.y(); int button = _ev.button();
        // A canonical capture owner (Keybind or KeyList) owns the next
        // pointer event as a cancellation gesture. Consume it before
        // vanilla children or a different row can act, so one click can
        // never both cancel capture and activate an unrelated control.
        if (com.aurora.client.screen.setting.FeatureSetting.cancelActiveCapture()) return true;
        refreshLayout();
        int listX = rowsLayout.viewport().x - 8;
        FeatureSetting owner = FeatureSetting.getFocused();
        if (owner instanceof com.aurora.client.screen.setting.EnumSetting<?>
                || owner instanceof com.aurora.client.screen.setting.ColorSetting) {
            if (FeatureSetting.hostBand().contains(mouseX, mouseY) && button == InputConstants.MOUSE_BUTTON_LEFT) {
                var box = rowsLayout.bounds(owner);
                if (box != null && owner.mouseClicked(mouseX, mouseY, button, settingX(listX),
                        settingY(owner, (int) box.y(layoutScroll())), settingW())) {
                    if (owner instanceof com.aurora.client.screen.setting.ColorSetting) activeDragSetting = owner;
                    return true;
                }
            }
            if (owner.dismissPopup()) return true;
        }
        FeatureSetting.clearFocus();
        if (this.getFocused() instanceof SemanticActionControl) this.setFocused(null);
        // An armed Reset disarms on any click that is not its own (design
        // language §8.7) — before dispatch, so the button's own press below
        // is the only click that can arm or fire it.
        if (resetConfirm.isArmed() && !resetBtn.isMouseOver(mouseX, mouseY)) {
            resetConfirm.disarm();
            syncResetButton();
        }
        if (super.mouseClicked(_ev, _doubleClicked)) return true;
        
        if (compact() && mouseX >= listX + LIST_W - ROW_RIGHT && mouseX < listX + LIST_W + 2 && mouseY >= bodyTop() && mouseY < bodyBottom()) {
            double max = rowsLayout.maxScroll();
            if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && max > 0) {
                scroll.beginThumbDrag(Math.max(0, Math.min(scroll.thumbHeight(bodyBottom() - bodyTop(), max, 24), mouseY - scroll.thumbY(bodyTop(), bodyBottom() - bodyTop(), max, 24))));
                return true;
            }
        }
        var fixed = fixedLayout.hitTest(mouseX, mouseY, 0);
        if (fixed != null && fixedHeaderSetting.mouseClicked(mouseX, mouseY, button, settingX(listX), (int) fixed.top(), settingW())) return true;
        var box = rowsLayout.hitTest(mouseX, mouseY, layoutScroll());
        if (box != null && box.key() instanceof FeatureSetting s
                && s.mouseClicked(mouseX, mouseY, button, settingX(listX), settingY(s, (int) box.y(layoutScroll())), settingW())) {
            activeDragSetting = s;
            var controls = s.controlsWithReset();
            if (!controls.isEmpty()) {
                SemanticActionControl hit = null;
                for (SemanticActionControl c : controls) {
                    if (c.isAvailable() && mouseX >= c.getX() && mouseX < c.getRight()
                            && mouseY >= c.getY() && mouseY < c.getBottom()) {
                        hit = c;
                        break;
                    }
                }
                this.setFocused(hit != null ? hit : controls.get(0));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent _ev, double dx, double dy) {
        double mouseX = _ev.x(); double mouseY = _ev.y(); int button = _ev.button();
        if (compact() && scroll.isDragging()) {
            refreshLayout();
            scroll.dragThumb(mouseY, bodyTop(), rowsLayout.viewport().height, rowsLayout.maxScroll(), 24, false);
            return true;
        }
        if (activeDragSetting != null
                && activeDragSetting.mouseDragged(mouseX, mouseY, button, dx, dy, 0, 0, settingW())) {
            return true;
        }
        return super.mouseDragged(_ev, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent _ev) {
        double mouseX = _ev.x(); double mouseY = _ev.y(); int button = _ev.button();
        scroll.endThumbDrag();
        if (activeDragSetting != null) {
            activeDragSetting.mouseReleased(mouseX, mouseY, button);
            activeDragSetting = null;
        }
        return super.mouseReleased(_ev);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        FeatureSetting focused = FeatureSetting.getFocused();
        if (focused != null && focused.onScroll(vertical)) return true;

        refreshLayout();
        scroll.wheel(vertical, 30, rowsLayout.maxScroll());
        return true;
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyEvent _kev) {
        // A keybind combo's modifier released alone binds that key (KeyCombo).
        if (FeatureSetting.routeKeyRelease(_kev)) return true;
        return super.keyReleased(_kev);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent _kev) {
        for (FeatureSetting setting : meta.settings)
            if (setting.resetKeyPressed(this.getFocused(), _kev)) return true;
        FeatureSetting focused = FeatureSetting.getFocused();
        if (focused != null && focused.onKeyPress(_kev)) return true;
        // C-3 roving interceptor — before super (the invokespecial seam): a
        // grouped hosted control (today: AccentSetting's peer grid on the
        // Theme screen) owns its group's arrows, moving focus silently.
        var before = this.getFocused();
        if (SemanticControlGroup.rove(before, _kev, this::setFocused)) {
            revealKeyboardFocus(before);
            return true;
        }
        // Escape unwinds one layer at a time: an armed Reset disarms and
        // CONSUMES the press (design language §8.7); the next Escape closes
        // the screen as before. A focused setting above (popup, capture)
        // already consumed its own Escape in onKeyPress.
        if (_kev.key() == InputConstants.KEY_ESCAPE && resetConfirm.isArmed()) {
            resetConfirm.disarm();
            syncResetButton();
            return true;
        }
        boolean handled = super.keyPressed(_kev);
        if (com.aurora.client.ui.interaction.FocusReveal.navigation(_kev)) revealKeyboardFocus(before);
        return handled;
    }

    private void revealKeyboardFocus(net.minecraft.client.gui.components.events.GuiEventListener before) {
        com.aurora.client.ui.interaction.FocusReveal.afterNavigation(before, getFocused(), scroll,
                rowsLayout, layoutScroll());
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent _ev) {
        FeatureSetting focused = FeatureSetting.getFocused();
        if (focused != null && focused.onCharTyped(_ev)) return true;
        if (FeatureSetting.hasActiveCapture()) return true;
        for (FeatureSetting s : meta.settings) if (s.isVisible() && s.resumeSearch(_ev)) {
            this.setFocused(null); // the setting's EditBox now owns typing, not the former action
            return true;
        }
        return super.charTyped(_ev);
    }

    @Override
    public void onClose() {
        AuroraConfig.save();
        closeTransientState();
        if (this.minecraft != null) com.aurora.client.adapter.NativeUi.setScreen(this.minecraft, parent);
    }

    /**
     * Minecraft may replace a screen without invoking its onClose() method.
     * The removed() hook is therefore the final capture-lifecycle boundary;
     * the guard keeps the ordinary Done/Escape path exactly-once.
     */
    @Override
    public void removed() {
        closeTransientState();
        super.removed();
    }

    private void closeTransientState() {
        if (transientStateClosed) return;
        transientStateClosed = true;
        // Drop the published host band with the rest of the transient state
        // (C-8) — nothing of this screen may keep influencing another host's
        // popup placement.
        FeatureSetting.clearHostBand();
        // Give settings a chance to clear transient state (e.g. a search
        // query) before the screen tears down. Without this, state leaks
        // into the next open because settings instances are reused.
        for (FeatureSetting s : meta.settings) {
            s.pinHeader(false);
            s.onDetailScreenClose();
            s.clearCompactMeasurement();
        }
        rowCache.retire();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
