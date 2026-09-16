package com.aurora.client.screen;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.screen.setting.FeatureSetting;
import com.aurora.client.screen.setting.ThemePreviewSetting;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.RoundedPanel;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.ScrollFade;
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

    private static final int LIST_W = 320;
    private static final int TOP_PAD = 60;
    private static final int ROW_GAP = 6;

    /**
     * Top of the scroll-fade band (DESIGN_LANGUAGE §8): just below the
     * title / Done-Reset chrome band. Rows fade out as they approach it
     * instead of sliding over the title; above it they are fully hidden.
     */
    private static final int TOP_FADE_Y = 40;

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
    private final SmoothScroll scroll = new SmoothScroll(60.0);
    private FeatureSetting activeDragSetting = null;

    /**
     * Static-layer caches (§8 fix, 2026-09-12): the window chrome and the
     * rows' cacheable shapes rasterize into SEPARATE layers — the chrome
     * blits unscissored (the window panel may slide under the title band),
     * the row layer blits under the TOP_FADE_Y scissor so row content can
     * never paint over the title/subtitle/Done-Reset band at any Background
     * Opacity. Same raster triggers as the former single cache.
     */
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
    /** Guards lifecycle teardown because onClose() replaces the screen and
     *  Minecraft then calls removed() on the same instance. */
    private boolean transientStateClosed = false;

    public FeatureDetailScreen(Screen parent, FeatureMetadata meta) {
        super(Component.literal(meta.displayName));
        this.parent = parent;
        this.meta = meta;
    }

    @Override
    protected void init() {
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
                this::onClose).glassBackground(true);
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
                }).glassBackground(true);
        this.addRenderableWidget(this.resetBtn);

        // Custom-painted semantic controls join vanilla's child/narratable
        // lifecycle without joining its render list. Their setting remains
        // the sole pixel and clipped-pointer owner.
        for (FeatureSetting setting : meta.settings) {
            SemanticActionControl control = setting.interactionControl();
            if (control != null) {
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
        if (liveWorldBackdrop()) return;
        super.renderBackground(g, mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        int totalRowsH = 0;
        for (FeatureSetting s : meta.settings) totalRowsH += s.height() + ROW_GAP;
        if (totalRowsH > 0) totalRowsH -= ROW_GAP; // trailing gap not drawn
        double maxScroll = Math.max(0, totalRowsH - (this.height - TOP_PAD - 40));

        scroll.advance(maxScroll);

        int listX = (this.width - LIST_W) / 2;

        // ---- One unified container window ----
        final int WINDOW_PAD_TOP    = 10;
        final int WINDOW_PAD_BOTTOM = 10;

        int windowY = TOP_PAD - (int) scroll.current() - WINDOW_PAD_TOP;
        int windowH = totalRowsH + WINDOW_PAD_TOP + WINDOW_PAD_BOTTOM;

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
        for (FeatureSetting s : meta.settings) fingerprint = fingerprint * 31 + s.shapeFingerprint();
        long chromeVersion = ThemeManager.generation() * 1_000_003L
                ^ ((long) (int) scroll.current() * 104729L)
                ^ ((long) totalRowsH * 31L)
                ^ ((long) fbW * 7919L)
                ^ ((long) fbH * 17L)
                ^ ((long) guiScale * 65537L)
                ^ (glassWindow ? 0x5BD1B2C1L : 0L); // glass state changes cached fill presence
        long rowVersion = chromeVersion ^ ((long) fingerprint * 100_000_009L);

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
                int yy = TOP_PAD - (int) scroll.current();
                for (FeatureSetting s : meta.settings) {
                    int h = s.height();
                    if (yy + h > 0 && yy < this.height) {
                        s.renderShapes(ctx, listX, yy, LIST_W, 0, 0);
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
        int gy = TOP_PAD - (int) scroll.current();
        for (FeatureSetting s : meta.settings) {
            int gh = s.height();
            SemanticActionControl control = s.interactionControl();
            if (control != null) {
                boolean available = gy + gh > TOP_FADE_Y && gy < this.height;
                control.setAvailable(available);
                s.onInteractionAvailabilityChanged(available);
            }
            if (gy + gh > 0 && gy < this.height) {
                s.renderGlassPass(ctx, listX, gy, LIST_W);
            }
            gy += gh + ROW_GAP;
        }

        // Chrome buttons' surfaces — the ButtonWidget discipline from
        // ManagerListScreen; their super.render below then paints labels only.
        if (doneBtn != null) doneBtn.renderGlassPass(ctx);
        if (resetBtn != null) resetBtn.renderGlassPass(ctx);

        // ---- 2. Dim — closes the glass pass, veils every surface like it ----
        // veils the world, flushes the deferred rim finishes, and from here
        // on any glass body paint is reported as an ordering violation.
        GlassSurface.overlayDim(ctx, this.width, this.height);

        // Cached chrome — one textured blit (a content layer above the dim,
        // exactly where it always sat). UNSCISSED: the window panel is a
        // container, and containers may slide up under the title band; only
        // ROW content stops at the fade boundary.
        chromeCache.blit(ctx, this.width, this.height);

        // Design language §8 — top-edge scroll fade, the scissored shape.
        // Rows (cached shapes AND live overlay) are clipped to a boundary
        // that starts just below the title/subtitle/Done-Reset band and
        // descends from TOP_FADE_Y+FADE_PX to TOP_FADE_Y as the fade
        // engages (a resting list clips nothing — byte-identical at
        // rest). The scissor, not a painted cap, is what stops rows from
        // sliding over the title: the pilot's capped-fade variant veiled
        // by WINDOW_FILL's alpha — the user-tunable Background Opacity —
        // so at low opacity its "opaque" cover hid nothing and the
        // title/content overlap returned (reproduced on Minimap + Better
        // Hitreg at opacity 0.1). A GL scissor is opacity-independent and
        // is the same mechanism every other scrollable screen already
        // uses; the gradient below then only SOFTENS the boundary, fading
        // rows into the window's own tint (WINDOW_FILL verbatim).
        double fadeK = ScrollFade.engagement(scroll.current(), ScrollFade.FADE_PX);
        int fadeTop = TOP_FADE_Y + (int) Math.round((1.0 - fadeK) * ScrollFade.FADE_PX);
        ctx.enableScissor(0, fadeTop, this.width, this.height);

        // Rows' cached shape layer — the second blit, under the boundary.
        rowCache.blit(ctx, this.width, this.height);

        // Live overlay: text, hover feedback, animated values.
        int y = TOP_PAD - (int) scroll.current();
        for (FeatureSetting s : meta.settings) {
            int h = s.height();
            if (y + h > 0 && y < this.height) {
                s.renderOverlay(ctx, listX, y, LIST_W, mouseX, mouseY);
            }
            y += h + ROW_GAP;
        }

        ctx.disableScissor();

        // The §8 gradient — softens the scissor boundary into the window's
        // own tint. Painted after the scissor closes and before the chrome
        // buttons/title so those stay crisp on top of it.
        ScrollFade.drawTop(ctx, listX, LIST_W, TOP_FADE_Y, ScrollFade.FADE_PX,
                scroll.current(), ThemeManager.color(ThemeToken.WINDOW_FILL));

        // Children: Done button.
        super.render(ctx, mouseX, mouseY, delta);

        // Design language §1 — screen title + attached subtitle. One title
        // per detail screen: the feature's name, full-strength
        // ON_BACKGROUND, left-aligned above the window at the content
        // column's text margin. The spec believed this title already
        // existed ("no change to its existing token or position") — it
        // never did; the TITLE_* constants this implements were reserved
        // in the initial commit and never drawn, so the first clause (one
        // title per screen) is the evident intent being implemented. The
        // optional credit line (FeatureMetadata.subtitle) is ATTACHED
        // directly beneath the title in ON_BACKGROUND_SECONDARY, no extra
        // margin — replacing the old placement vertically centered on the
        // Done row. "Smaller" collapses to the app's single 9px type size;
        // Aurora has no large/bold text idiom and inventing one (pose-
        // scaled glyphs) would violate the no-new-visual-style constraint.
        if (this.font != null) {
            int titleX = listX + 12;
            ctx.drawString(this.font, meta.displayName, titleX, TITLE_Y,
                    ThemeManager.color(ThemeToken.ON_BACKGROUND), false);
            if (meta.subtitle != null && !meta.subtitle.isEmpty()) {
                ctx.drawString(this.font, meta.subtitle, titleX,
                        TITLE_Y + this.font.lineHeight + 1,
                        ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY), false);
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
        // A canonical keybind capture owns the next pointer event as a
        // cancellation gesture. Consume it before vanilla children or a
        // different row can act, so one click can never both cancel capture
        // and activate an unrelated control underneath.
        if (com.aurora.client.screen.setting.KeybindSetting.cancelActiveCapture()) return true;
        FeatureSetting.clearFocus();
        if (this.getFocused() instanceof SemanticActionControl) this.setFocused(null);
        if (super.mouseClicked(_ev, _doubleClicked)) return true;
        
        int listX = (this.width - LIST_W) / 2;
        int y = TOP_PAD - (int) scroll.current();
        for (FeatureSetting s : meta.settings) {
            int h = s.height();
            // Hit-testing agrees with the scroll fade (§8): the part of a
            // row hidden by the fade band (above TOP_FADE_Y) is not
            // clickable — the same render-truth rule ManagerListScreen
            // applies to its scissored clip band.
            if (mouseY >= TOP_FADE_Y && y + h > TOP_FADE_Y && y < this.height
                    && s.mouseClicked(mouseX, mouseY, button, listX, y, LIST_W)) {
                activeDragSetting = s;
                if (s.interactionControl() != null) this.setFocused(s.interactionControl());
                return true;
            }
            y += h + ROW_GAP;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent _ev, double dx, double dy) {
        double mouseX = _ev.x(); double mouseY = _ev.y(); int button = _ev.button();
        if (activeDragSetting != null
                && activeDragSetting.mouseDragged(mouseX, mouseY, button, dx, dy, 0, 0, LIST_W)) {
            return true;
        }
        return super.mouseDragged(_ev, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent _ev) {
        double mouseX = _ev.x(); double mouseY = _ev.y(); int button = _ev.button();
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
        for (FeatureSetting s : meta.settings) totalH += s.height() + ROW_GAP;
        double maxScroll = Math.max(0, totalH - (this.height - TOP_PAD - 40));

        scroll.wheel(vertical, 30, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent _kev) {
        FeatureSetting focused = FeatureSetting.getFocused();
        if (focused != null && focused.onKeyPress(_kev)) return true;
        return super.keyPressed(_kev);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent _ev) {
        FeatureSetting focused = FeatureSetting.getFocused();
        if (focused != null && focused.onCharTyped(_ev)) return true;
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
        closeTransientState();
        super.removed();
    }

    private void closeTransientState() {
        if (transientStateClosed) return;
        transientStateClosed = true;
        // Give settings a chance to clear transient state (e.g. a search
        // query) before the screen tears down. Without this, state leaks
        // into the next open because settings instances are reused.
        for (FeatureSetting s : meta.settings) {
            s.onDetailScreenClose();
        }
        chromeCache.dispose();
        rowCache.dispose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
