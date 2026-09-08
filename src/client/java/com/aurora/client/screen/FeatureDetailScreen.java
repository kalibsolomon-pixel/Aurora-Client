package com.aurora.client.screen;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.screen.setting.FeatureSetting;
import com.aurora.client.screen.setting.ThemePreviewSetting;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.RoundedPanel;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.util.RenderUtil;
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
 */
public class FeatureDetailScreen extends Screen implements ThemedScreen {

    private static final int LIST_W = 320;
    private static final int TOP_PAD = 60;
    private static final int ROW_GAP = 6;

    private static final int DONE_W = 64;
    private static final int DONE_H = 22;
    private static final int DONE_RIGHT_MARGIN = 16;
    private static final int DONE_TOP_MARGIN = 14;

    private static final int TITLE_X = 20;
    private static final int TITLE_Y = 18;
    private static final int TITLE_ACCENT_W = 100;

    private final Screen parent;
    private final FeatureMetadata meta;
    private double scrollY = 0;
    private double scrollTarget = 0;
    private double lastScrollTickMs = 0.0;
    private FeatureSetting activeDragSetting = null;

    /** Static-layer cache: window chrome + rows' cacheable shapes, blitted once per clean frame. */
    private final UiLayerCache layerCache = new UiLayerCache();

    /** Shared themed window panel (glow + fill + outline), drawn into the static cache. */
    private final RoundedPanel windowPanel = new RoundedPanel(true);

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

    public FeatureDetailScreen(Screen parent, FeatureMetadata meta) {
        super(Component.literal(meta.displayName));
        this.parent = parent;
        this.meta = meta;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(new ButtonWidget(
                this.width - DONE_W - DONE_RIGHT_MARGIN, DONE_TOP_MARGIN,
                DONE_W, DONE_H,
                Component.literal("Done"),
                this::onClose).glassBackground(true));

        // Reset-to-defaults button immediately to the left of Done. Persists
        // immediately so the change is visible to other open screens.
        int resetW = 70;
        int resetX = this.width - DONE_W - DONE_RIGHT_MARGIN - resetW - 6;
        this.addRenderableWidget(new ButtonWidget(
                resetX, DONE_TOP_MARGIN,
                resetW, DONE_H,
                Component.literal("Reset"),
                () -> {
                    meta.reset();
                    AuroraConfig.save();
                }).glassBackground(true));

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

    private void tickSmoothScroll(double maxScroll) {
        double now = System.nanoTime() / 1_000_000.0;
        double dt = (lastScrollTickMs == 0.0) ? 16.0 : Math.min(80.0, now - lastScrollTickMs);
        lastScrollTickMs = now;

        if (scrollTarget < 0) scrollTarget = 0;
        if (scrollTarget > maxScroll) scrollTarget = maxScroll;

        double alpha = 1.0 - Math.exp(-dt / 60.0);
        double diff = scrollTarget - scrollY;
        scrollY += diff * alpha;

        if (Math.abs(diff) < 0.25) {
            scrollY = scrollTarget;
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

        tickSmoothScroll(maxScroll);

        int listX = (this.width - LIST_W) / 2;

        // ---- One unified container window ----
        final int WINDOW_PAD_TOP    = 10;
        final int WINDOW_PAD_BOTTOM = 10;

        int windowY = TOP_PAD - (int) scrollY - WINDOW_PAD_TOP;
        int windowH = totalRowsH + WINDOW_PAD_TOP + WINDOW_PAD_BOTTOM;

        // ---- Live GPU glass UNDER the cached layer. Drawn before the ----
        // overlay dim so the dim veils the panel and its surroundings
        // equally — the backdrop must not read brighter inside the panel
        // than outside it (the alignment work's core principle). The window
        // panel uses the depressed treatment (main containers read as
        // recessed); its tint is the ordinary WINDOW_FILL fill, whose alpha
        // already carries the theme's Background Opacity — still exactly
        // one application point. When the renderer declines (no world,
        // screenshot suppression, tiny/clipped panel, failure) glassWindow
        // is false and the cached layer below carries the old opaque
        // fill+outline instead (see the glass bit in the version hash,
        // which forces the re-raster on the switch).
        boolean glassWindow = false;
        {
            float radius = ThemeManager.current().roundness().radius();
            glassWindow = BlurPanelRenderer.renderPanel(ctx, listX, windowY, LIST_W, windowH,
                    radius, BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX,
                    BlurPanelRenderer.Lighting.depressed());
            if (glassWindow) {
                RenderUtil.drawRoundedRectAA(ctx, listX, windowY, LIST_W, windowH, radius,
                        ThemeManager.color(ThemeToken.WINDOW_FILL));
                BlurPanelRenderer.drawRimFinish(ctx, listX, windowY, LIST_W, windowH, radius);
            }
        }

        // ---- Static-layer cache: window chrome + rows' cacheable shapes ----
        // Version covers every input that can change cached pixels: theme
        // (generation stamp bumps on every reload — accent/mode/roundness/
        // opacity/blur/enabled, drag throttles, profile switches), scroll,
        // content height (expanded editors), framebuffer size, and GUI scale.
        com.mojang.blaze3d.pipeline.RenderTarget main =
                this.minecraft != null ? this.minecraft.getMainRenderTarget() : null;
        int fbW = (main != null && main.width > 0) ? main.width : this.width;
        int fbH = (main != null && main.height > 0) ? main.height : this.height;
        int guiScale = Math.max(1, (int) this.minecraft.getWindow().getGuiScale());
        int fingerprint = 0;
        for (FeatureSetting s : meta.settings) fingerprint = fingerprint * 31 + s.shapeFingerprint();
        long version = ThemeManager.generation() * 1_000_003L
                ^ ((long) (int) scrollY * 104729L)
                ^ ((long) totalRowsH * 31L)
                ^ ((long) fbW * 7919L)
                ^ ((long) fbH * 17L)
                ^ ((long) guiScale * 65537L)
                ^ ((long) fingerprint * 100_000_009L)
                ^ (glassWindow ? 0x5BD1B2C1L : 0L); // glass state changes cached fill presence

        if (!layerCache.isCurrent(version)) {
            layerCache.ensureSize(fbW, fbH);
            layerCache.clear();
            RenderUtil.RectSink prev = RenderUtil.beginCapture(layerCache.sink());
            try {
                drawWindowChromeShapes(ctx, listX, windowY, windowH, glassWindow);
                int yy = TOP_PAD - (int) scrollY;
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
            layerCache.commit(version);
        }

        // Preview-card glass — after the capture pass above (which is what
        // refreshes the remembered rects) and before the dim + cached blit,
        // so the mock controls rasterized into the cache stack on top of the
        // glass. This is the generic per-setting glass pass
        // (FeatureSetting.renderGlassPass) — the preview card + toggle track
        // + accent chip override it; segments and buttons glass inside their
        // own overlay draws. Falls back per element when declined.
        for (FeatureSetting s : meta.settings) {
            s.renderGlassPass(ctx);
        }

        // Themed overlay dim — token-driven (OVERLAY_DIM), so mode/accent
        // changes and any future opacity work apply here like everywhere else.
        ctx.fill(0, 0, this.width, this.height, ThemeManager.color(ThemeToken.OVERLAY_DIM));
        // Cached chrome — one textured blit.
        layerCache.blit(ctx, this.width, this.height);

        // Live overlay: text, hover feedback, animated values.
        int y = TOP_PAD - (int) scrollY;
        for (FeatureSetting s : meta.settings) {
            int h = s.height();
            if (y + h > 0 && y < this.height) {
                s.renderOverlay(ctx, listX, y, LIST_W, mouseX, mouseY);
            }
            y += h + ROW_GAP;
        }

        // Children: Done button.
        super.render(ctx, mouseX, mouseY, delta);

        // Optional credit line (FeatureMetadata.subtitle) at the title
        // position, vertically centered on the Done/Reset row. The detail
        // screen draws no title of its own, so this reads as the screen's
        // small caption; only Better Hitreg sets one today.
        if (meta.subtitle != null && !meta.subtitle.isEmpty() && this.font != null) {
            int ty = DONE_TOP_MARGIN + (DONE_H - this.font.lineHeight) / 2;
            ctx.drawString(this.font, meta.subtitle, TITLE_X, ty,
                    com.aurora.client.util.AuroraTheme.TEXT_SECONDARY, false);
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
        FeatureSetting.clearFocus();
        if (super.mouseClicked(_ev, _doubleClicked)) return true;
        
        int listX = (this.width - LIST_W) / 2;
        int y = TOP_PAD - (int) scrollY;
        for (FeatureSetting s : meta.settings) {
            int h = s.height();
            if (s.mouseClicked(mouseX, mouseY, button, listX, y, LIST_W)) {
                activeDragSetting = s;
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

        int totalH = 0;
        for (FeatureSetting s : meta.settings) totalH += s.height() + ROW_GAP;
        double maxScroll = Math.max(0, totalH - (this.height - TOP_PAD - 40));

        scrollTarget -= vertical * 30;
        if (scrollTarget < 0) scrollTarget = 0;
        if (scrollTarget > maxScroll) scrollTarget = maxScroll;
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
        // Give settings a chance to clear transient state (e.g. a search
        // query) before the screen tears down. Without this, state leaks
        // into the next open because settings instances are reused.
        for (FeatureSetting s : meta.settings) {
            s.onDetailScreenClose();
        }
        layerCache.dispose();
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
