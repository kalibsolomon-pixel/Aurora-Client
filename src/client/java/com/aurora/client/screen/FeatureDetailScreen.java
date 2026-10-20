package com.aurora.client.screen;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.screen.setting.FeatureSetting;
import com.aurora.client.screen.setting.ThemePreviewSetting;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.RoundedPanel;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.interaction.SemanticControlGroup;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.SmoothScroll;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.UiLayerCache;
import net.minecraft.client.gui.GuiGraphics;
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
public class FeatureDetailScreen extends Screen implements ThemedScreen {
    @Override public boolean unifiedTheme() { return true; }
    @Override public boolean parentGlassOnly() { return true; }


    private int LIST_W = 320;
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

    private final Screen parent;
    private final FeatureMetadata meta;
    /** τ = 60 ms, snap 0.25, wheel step 30, no thumb — the screen's exact pre-R2 feel. */
    private final SmoothScroll scroll = new SmoothScroll(60.0).boundedMotion();
    private FeatureSetting activeDragSetting = null;
    private FeatureSetting fixedHeaderSetting;
    private int bodyTop() { return TOP_PAD + (fixedHeaderSetting == null ? 0 : fixedHeaderSetting.fixedHeaderHeight()); }
    private boolean compact() { return true; }
    private com.aurora.client.ui.util.UiDensity.Rect compactFrame;
    /** The largest frame the viewport allows; {@link #compactFrame} fits content inside it. */
    private com.aurora.client.ui.util.UiDensity.Rect maxFrame;

    /**
     * Content-fitted frame (premium-design audit, 2026-10-19): a three-row
     * screen no longer sits in a 300px window that is mostly empty well. The
     * frame's TOP is chosen once per init (centering the content-fitted frame
     * as opened) and never moves while the screen is open, so Done/Reset and
     * the title stay put; only the bottom edge follows rows that appear or
     * disappear, up to the viewport limit, beyond which the body scrolls.
     */
    private void fitFrame(boolean recenter) {
        int total = 0;
        for (FeatureSetting setting : meta.settings) if (setting.isVisible()) total += bodyHeight(setting) + ROW_GAP;
        if (total > 0) total -= ROW_GAP;
        int fixedHeader = fixedHeaderSetting == null ? 0 : fixedHeaderSetting.fixedHeaderHeight();
        // The body must also be tall enough for any dropdown's popup, which is
        // painted inside the body clip and would otherwise be cut off.
        int popup = 0;
        for (FeatureSetting setting : meta.settings)
            if (setting.isVisible() && setting instanceof com.aurora.client.screen.setting.EnumSetting<?> row)
                popup = Math.max(popup, row.popupHeight() + 30);
        int wanted = Math.max(total == 0 ? 64 : 80, 36 + fixedHeader + Math.max(total, popup) + 8);
        int top = recenter ? Math.max(maxFrame.y(), (this.height - Math.min(wanted, maxFrame.height())) / 2)
                : compactFrame.y();
        int limit = Math.min(maxFrame.y() + maxFrame.height(), this.height - 12) - top;
        compactFrame = new com.aurora.client.ui.util.UiDensity.Rect(maxFrame.x(), top, maxFrame.width(),
                Math.max(1, Math.min(wanted, limit)));
        TOP_PAD = compactFrame.y() + 36;
    }
    private void measureRows() {
        if (compact()) for (FeatureSetting setting : meta.settings) {
            if (setting.isVisible()) setting.measureCompact(LIST_W);
            else {
                for (SemanticActionControl control : setting.interactionControls()) {
                    control.setAvailable(false);
                    if (this.getFocused() == control) this.setFocused(null);
                }
                setting.onInteractionAvailabilityChanged(false);
                if (FeatureSetting.getFocused() == setting) FeatureSetting.clearFocus();
                if (activeDragSetting == setting) activeDragSetting = null;
            }
        }
    }
    private int bodyBottom() { return compact() ? compactFrame.y() + compactFrame.height() - 8 : Math.max(bodyTop() + 1, this.height - 30); }
    private int bodyHeight(FeatureSetting setting) { return setting.height() - (setting == fixedHeaderSetting ? setting.fixedHeaderHeight() : 0); }
    private int settingY(FeatureSetting setting, int bodyY) { return bodyY - (setting == fixedHeaderSetting ? setting.fixedHeaderHeight() : 0); }


    /** Independent caches: stationary chrome and scrolling body shapes. */
    private final UiLayerCache chromeCache = new UiLayerCache();
    private final UiLayerCache rowCache = new UiLayerCache();

    /** Shared themed window panel (glow + fill + outline), drawn into the static cache. */
    private final RoundedPanel windowPanel = new RoundedPanel(true);

    /**
     * The Done/Reset chrome buttons — kept as fields so the glass pass can
     * drive their surfaces pre-dim (ButtonWidget.renderGlassPass), the same
     * way ManagerListScreen drives its toolbar pair.
     */
    private ButtonWidget doneBtn;
    private ButtonWidget resetBtn;

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

    /** Features with a dedicated destination skip the generic settings wrapper. */
    public static Screen destination(Screen parent, FeatureMetadata meta) {
        return "resourcepack_browser".equals(meta.id)
                ? new ResourcePackBrowserScreen(parent) : new FeatureDetailScreen(parent, meta);
    }

    @Override
    protected void init() {
        if (compact()) {
            maxFrame = com.aurora.client.ui.util.UiDensity.detailFrame(this.width, this.height);
            compactFrame = maxFrame;
            LIST_W = compactFrame.width(); TOP_PAD = compactFrame.y() + 36;
            ROW_GAP = com.aurora.client.ui.util.UiDensity.ROW_GAP;
            measureRows();
            fitFrame(true);
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
                this::onClose).glassBackground(true).theming(com.aurora.client.ui.component.Button.Theming.OUTLINED);
        // Closing is neutral chrome, matching Reset and the other screens' Done buttons.
        this.addRenderableWidget(this.doneBtn);

        // Reset-to-defaults button immediately to the left of Done. Persists
        // immediately so the change is visible to other open screens. Phase A:
        // vanilla-backed semantic like Done (destructive — the semantic layer
        // only names it; colors/labels/behavior are untouched).
        int resetW = 70;
        int resetX = this.width - DONE_W - DONE_RIGHT_MARGIN - resetW - 6;
        this.resetBtn = ButtonWidget.semantic(
                resetX, DONE_TOP_MARGIN,
                resetW, DONE_H,
                Component.literal("Reset"),
                "Resets this feature's settings to their defaults and saves.",
                () -> {
                    meta.reset();
                    AuroraConfig.save();
                }).glassBackground(true).theming(com.aurora.client.ui.component.Button.Theming.OUTLINED);
        this.addRenderableWidget(this.resetBtn);

        // Custom-painted semantic controls join vanilla's child/narratable
        // lifecycle without joining its render list. Their setting remains
        // the sole pixel and clipped-pointer owner.
        if (compact()) {
            doneBtn.setX(compactFrame.x() + LIST_W - 64 - 8); doneBtn.setY(compactFrame.y() + 6);
            resetBtn.setX(doneBtn.getX() - 54 - 4); resetBtn.setY(doneBtn.getY()); resetBtn.setWidth(54);
        }
        registeredSettingControls.clear();
        for (FeatureSetting setting : meta.settings) {
            var controls = setting.interactionControls();
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

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // With a live world behind the screen, skip vanilla's background
        // sandwich (full-screen blur + dark gradient) — the glass panels
        // must sample the LIVE world, not an already-darkened,
        // already-blurred backdrop. With no level loaded the glass renderer
        // declines anyway (its menu-context guard) and the opaque fallback
        // wants the vanilla backdrop as before, so the override is
        // conditional on the same validity check the renderer uses.
        if(MaterialSurface.unified() && GlassSurface.renderMenuPanorama(this,g,delta)) return;
        if (liveWorldBackdrop()) return;
        super.renderBackground(g, mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        measureRows();
        fixedHeaderSetting = null;
        for (FeatureSetting setting : meta.settings) {
            if (!setting.isVisible()) continue;
            boolean pin = fixedHeaderSetting == null && setting.fixedHeaderHeight() > 0;
            setting.pinHeader(pin);
            if (pin) fixedHeaderSetting = setting;
        }
        if (compact()) fitFrame(false);
        int listX = (this.width - LIST_W) / 2;
        FeatureSetting.setHostBand(new com.aurora.client.ui.util.ClipBand(
                listX + 8, bodyTop(), LIST_W - 16, bodyBottom() - bodyTop()));
        int totalRowsH = 0;
        for (FeatureSetting setting : meta.settings) if (setting.isVisible()) totalRowsH += bodyHeight(setting) + ROW_GAP;
        if (totalRowsH > 0) totalRowsH -= ROW_GAP;
        double maxScroll = Math.max(0, totalRowsH - (bodyBottom() - bodyTop()));

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
        // suppression, tiny/clipped panel, failure) glassWindow is false and
        // the cached layer below carries the old opaque fill+outline instead
        // (see the glass bit in the version hash, which forces the re-raster
        // on the switch).
        GlassSurface.beginGlassPass();
        boolean glassWindow = false;
        {
            float radius = ThemeManager.current().roundness().radius();
            glassWindow = GlassSurface.container(ctx, listX, windowY, LIST_W, windowH, radius);
        }

        // ---- Static-layer caches: window chrome + rows' cacheable shapes ----
        // TWO caches, not one: the rows' shape layer is blitted under the
        // TOP_FADE_Y scissor (see below) while the window chrome blits
        // unscissored (the window panel itself is allowed to slide up under
        // the title band — only ROW content must stop at the boundary).
        // Version covers every input that can change cached pixels: theme
        // (generation stamp bumps on every reload — accent/mode/roundness/
        // opacity/blur/enabled, drag throttles, profile switches), scroll,
        // content height (expanded editors), framebuffer size, and GUI scale.
        // The row layer adds the settings' shape fingerprints.
        com.mojang.blaze3d.pipeline.RenderTarget main =
                this.minecraft != null ? this.minecraft.getMainRenderTarget() : null;
        int fbW = (main != null && main.width > 0) ? main.width : this.width;
        int fbH = (main != null && main.height > 0) ? main.height : this.height;
        int guiScale = Math.max(1, (int) this.minecraft.getWindow().getGuiScale());
        int fingerprint = 0;
        for (FeatureSetting s : meta.settings) if (s.isVisible()) fingerprint = fingerprint * 31 + s.shapeFingerprint();
        long chromeVersion = ThemeManager.generation() * 1_000_003L
                ^ ((long) totalRowsH * 31L)
                ^ ((long) fbW * 7919L)
                ^ ((long) fbH * 17L)
                ^ ((long) guiScale * 65537L)
                ^ ((long) windowH * 2_147_483_647L)
                ^ (glassWindow ? 0x5BD1B2C1L : 0L); // glass state changes cached fill presence
        long rowVersion = chromeVersion ^ ((long) fingerprint * 100_000_009L)
                ^ ((long) (int) scroll.current() * 104729L) ^ bodyTop();

        if (!chromeCache.isCurrent(chromeVersion)) {
            chromeCache.ensureSize(fbW, fbH);
            chromeCache.clear();
            RenderUtil.RectSink prev = RenderUtil.beginCapture(chromeCache.sink());
            try {
                drawWindowChromeShapes(ctx, listX, windowY, windowH, glassWindow);
            } finally {
                RenderUtil.endCapture(prev);
            }
            chromeCache.commit(chromeVersion);
        }
        if (!rowCache.isCurrent(rowVersion)) {
            rowCache.ensureSize(fbW, fbH);
            rowCache.clear();
            RenderUtil.RectSink prev = RenderUtil.beginCapture(rowCache.sink());
            try {
                int yy = bodyTop() - (int) scroll.current();
                for (FeatureSetting s : meta.settings) {
                    if (!s.isVisible()) continue;
                    int h = bodyHeight(s);
                    if (yy + h > bodyTop() && yy < bodyBottom()) {
                        s.renderShapes(ctx, listX, settingY(s, yy), LIST_W, 0, 0);
                    }
                    yy += h + ROW_GAP;
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
        if (fixedHeaderSetting != null) fixedHeaderSetting.renderFixedHeaderGlass(ctx, listX, TOP_PAD, LIST_W);
        GlassSurface.enableScissor(ctx, listX + 8, bodyTop(), listX + LIST_W - 8, bodyBottom());
        int gy = bodyTop() - (int) scroll.current();
        for (FeatureSetting s : meta.settings) {
            if (!s.isVisible()) continue;
            int gh = bodyHeight(s);
            var controls = s.interactionControls();
            if (!controls.isEmpty()) {
                // C-4 dynamic-set diff: mutation-time list changes (rows
                // added/removed) register/unregister here — zero allocation
                // on the unchanged path (identity compare against the
                // setting's own cached list).
                var prev = registeredSettingControls.put(s, controls);
                if (prev != null && prev != controls && !sameControlList(prev, controls)) {
                    for (SemanticActionControl c : controls) {
                        if (!containsControl(prev, c)) {
                            c.setFocused(false);
                            c.setAvailable(false);
                            this.addWidget(c);
                        }
                    }
                    for (SemanticActionControl c : prev) {
                        if (!containsControl(controls, c)) {
                            if (this.getFocused() == c) this.setFocused(null);
                            this.removeWidget(c);
                        }
                    }
                }
                boolean available = s == fixedHeaderSetting || gy + gh > bodyTop() && gy < bodyBottom();
                for (SemanticActionControl control : controls) {
                    control.setAvailable(available);
                }
                s.onInteractionAvailabilityChanged(available);
            }
            if (gy + gh > bodyTop() && gy < bodyBottom()) {
                s.renderGlassPass(ctx, listX, settingY(s, gy), LIST_W);
            }
            gy += gh + ROW_GAP;
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
        chromeCache.blit(ctx, this.width, this.height);

        // Only the body scrolls; the frame and search header have fixed geometry.
        ctx.enableScissor(listX + 8, bodyTop(), listX + LIST_W - 8, bodyBottom());

        // Rows' cached shape layer — the second blit, under the boundary.
        rowCache.blit(ctx, this.width, this.height);

        // Live overlay: text, hover feedback, animated values.
        int y = bodyTop() - (int) scroll.current();
        for (FeatureSetting s : meta.settings) {
            if (!s.isVisible()) continue;
            int h = bodyHeight(s);
            if (y + h > bodyTop() && y < bodyBottom()) {
                s.renderOverlay(ctx, listX, settingY(s, y), LIST_W, mouseX, mouseY);
            }
            y += h + ROW_GAP;
        }

        if (compact()) for (FeatureSetting setting : meta.settings)
            if (setting instanceof com.aurora.client.screen.setting.EnumSetting<?> row && setting.isVisible() && row.popupOpen())
                row.renderFloatingOverlay(ctx, mouseX, mouseY);
        ctx.disableScissor();
        if (compact()) {
            // Rows fade into the window tint at both clip edges while more content lies beyond.
            int fadeSurface = MaterialSurface.color(ThemeToken.WINDOW_FILL);
            if (totalRowsH == 0)
                ctx.drawString(this.font, "This feature has no additional settings.", listX + 12, bodyTop() + 2,
                        MaterialSurface.color(ThemeToken.ON_BACKGROUND_MUTED), false);
            com.aurora.client.util.ScrollFade.drawTop(ctx, listX + 8, LIST_W - 16, bodyTop(),
                    com.aurora.client.util.ScrollFade.FADE_PX, scroll.current(), fadeSurface);
            com.aurora.client.util.ScrollFade.drawBottom(ctx, listX + 8, LIST_W - 16, bodyBottom(),
                    com.aurora.client.util.ScrollFade.FADE_PX, maxScroll - scroll.current(), fadeSurface);
        }
        if (compact()) {
            int titleX = com.aurora.client.ui.util.UiDensity.detailTitleX(compactFrame);
            String breadcrumb = com.aurora.client.ui.util.AuroraFontRenderer.ellipsize(this.font, meta.displayName, Math.max(0, resetBtn.getX() - titleX - 4), 0);
            ctx.drawString(this.font, breadcrumb, titleX, (meta.subtitle == null || meta.subtitle.isEmpty() ? compactFrame.y() + 13 : compactFrame.y() + 7), MaterialSurface.color(ThemeToken.ON_BACKGROUND), false);
            if (maxScroll > 0) {
                var thumb = com.aurora.client.ui.component.ScrollbarChrome.thumb(scroll, bodyTop(), bodyBottom() - bodyTop(), maxScroll, 24, this.minecraft.getWindow().getGuiScale());
                com.aurora.client.ui.component.ScrollbarChrome.draw(ctx, listX + LIST_W - 4, thumb, scroll.isDragging(), ThemeToken.ON_BACKGROUND);
            }
        }

        if (fixedHeaderSetting != null)
            fixedHeaderSetting.renderFixedHeader(ctx, listX, TOP_PAD, LIST_W, mouseX, mouseY);

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
                control.setAvailable(control.isAvailable() && (header || visible));
                if (!control.isAvailable() && this.getFocused() == control) this.setFocused(null);
            }
        }

        // Children: Done button.
        super.render(ctx, mouseX, mouseY, delta);

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
        if (this.font != null) {
            int titleX = com.aurora.client.ui.util.UiDensity.detailTitleX(compactFrame);
            // Feature identity is already established by the navigation card.
            if (meta.subtitle != null && !meta.subtitle.isEmpty()) {
                ctx.drawString(this.font, AuroraFontRenderer.ellipsize(this.font, meta.subtitle, Math.max(0, resetBtn.getX() - titleX - 4), 0), titleX,
                        compactFrame.y() + 7 + this.font.lineHeight + 1,
                        ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_OVERLAY),0xAA), false);
            }
        }

        // Label-hover description tooltip floats above the rows.
        FeatureSetting.drawPendingTooltip(ctx, this.width, this.height);

        logFramePerf();
    }

    /**
     * Window glow + fill + outline — the screen's static chrome, captured
     * into the cache. On glass the fill + outline are omitted (the glass
     * material supplies its own directional rim; stacking the old outline
     * on top would read as a double border). The glow rings stay — they
     * read as a drop shadow, not an outline.
     */
    private void drawWindowChromeShapes(GuiGraphics ctx, int listX, int windowY, int windowH,
                                        boolean glassActive) {
        windowPanel.renderShapes(ctx, listX, windowY, LIST_W, windowH, glassActive);
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
        FeatureSetting.clearFocus();
        if (this.getFocused() instanceof SemanticActionControl) this.setFocused(null);
        if (super.mouseClicked(_ev, _doubleClicked)) return true;
        
        measureRows();
        int listX = (this.width - LIST_W) / 2;
        if (compact() && mouseX >= listX + LIST_W - 8 && mouseX < listX + LIST_W + 2 && mouseY >= bodyTop() && mouseY < bodyBottom()) {
            int extent = -ROW_GAP;
            for (FeatureSetting setting : meta.settings) if (setting.isVisible()) extent += bodyHeight(setting) + ROW_GAP;
            double max = Math.max(0, extent - (bodyBottom() - bodyTop()));
            if (button == 0 && max > 0) {
                scroll.beginThumbDrag(Math.max(0, Math.min(scroll.thumbHeight(bodyBottom() - bodyTop(), max, 24), mouseY - scroll.thumbY(bodyTop(), bodyBottom() - bodyTop(), max, 24))));
                return true;
            }
        }
        if (compact() && FeatureSetting.hostBand().contains(mouseX, mouseY)) {
            int popupY = bodyTop() - (int) scroll.current();
            for (FeatureSetting setting : meta.settings) {
                if (!setting.isVisible()) continue;
                if (setting instanceof com.aurora.client.screen.setting.EnumSetting<?> row && setting.isVisible() && row.popupOpen())
                    return row.mouseClicked(mouseX, mouseY, button, listX, popupY, LIST_W);
                popupY += bodyHeight(setting) + ROW_GAP;
            }
        }
        if (fixedHeaderSetting != null && mouseY >= TOP_PAD && mouseY < bodyTop()
                && fixedHeaderSetting.mouseClicked(mouseX, mouseY, button, listX, TOP_PAD, LIST_W)) return true;
        if (!FeatureSetting.hostBand().contains(mouseX, mouseY)) return false;
        int y = bodyTop() - (int) scroll.current();
        for (FeatureSetting s : meta.settings) {
            if (!s.isVisible()) continue;
            int h = bodyHeight(s);
            // Pointer dispatch and paint share the fixed body band.
            if (y + h > bodyTop() && y < bodyBottom()
                    && s.mouseClicked(mouseX, mouseY, button, listX, settingY(s, y), LIST_W)) {
                activeDragSetting = s;
                // Mirror focus onto the exact control under the accepted
                // click when the setting exposes several (ItemScale's rows,
                // Accent's peers — AuroraScreen's rule); single-control
                // settings keep the first-control focus the C-2 pilot set.
                var controls = s.interactionControls();
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
            y += h + ROW_GAP;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent _ev, double dx, double dy) {
        double mouseX = _ev.x(); double mouseY = _ev.y(); int button = _ev.button();
        if (compact() && scroll.isDragging()) {
            int extent = -ROW_GAP;
            for (FeatureSetting setting : meta.settings) if (setting.isVisible()) extent += bodyHeight(setting) + ROW_GAP;
            scroll.dragThumb(mouseY, bodyTop(), bodyBottom() - bodyTop(), Math.max(0, extent - (bodyBottom() - bodyTop())), 24, false);
            return true;
        }
        if (activeDragSetting != null
                && activeDragSetting.mouseDragged(mouseX, mouseY, button, dx, dy, 0, 0, LIST_W)) {
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

        // Input-time bound deliberately computed as before (no trailing-gap
        // subtraction) — the render-time advance() re-clamps each frame.
        int totalH = 0;
        for (FeatureSetting s : meta.settings) if (s.isVisible()) totalH += bodyHeight(s) + ROW_GAP;
        if (totalH > 0) totalH -= ROW_GAP;
        double maxScroll = Math.max(0, totalH - (bodyBottom() - bodyTop()));

        scroll.wheel(vertical, 30, maxScroll);
        return true;
    }

    private boolean sameControlList(java.util.List<SemanticActionControl> a,
                                    java.util.List<SemanticActionControl> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i) != b.get(i)) return false;
        }
        return true;
    }

    private boolean containsControl(java.util.List<SemanticActionControl> list,
                                    SemanticActionControl wanted) {
        for (SemanticActionControl c : list) {
            if (c == wanted) return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent _kev) {
        FeatureSetting focused = FeatureSetting.getFocused();
        if (focused != null && focused.onKeyPress(_kev)) return true;
        // C-3 roving interceptor — before super (the invokespecial seam): a
        // grouped hosted control (today: AccentSetting's peer grid on the
        // Theme screen) owns its group's arrows, moving focus silently.
        if (SemanticControlGroup.rove(this.getFocused(), _kev, this::setFocused)) return true;
        return super.keyPressed(_kev);
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
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    /**
     * Minecraft may replace a screen without invoking its onClose() method.
     * The removed() hook is therefore the final capture-lifecycle boundary;
     * the guard keeps the ordinary Done/Escape path exactly-once.
     */
    @Override
    public void removed() {
        if(unifiedTheme()) MaterialSurface.disposeTemplates();
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
        chromeCache.dispose();
        rowCache.dispose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
