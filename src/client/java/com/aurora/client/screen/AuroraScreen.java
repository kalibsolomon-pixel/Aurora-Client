package com.aurora.client.screen;

import com.aurora.client.module.Module;
import com.aurora.client.module.ModuleManager;
import com.aurora.client.screen.setting.FeatureSetting;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.component.ToggleSwitch;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.MaterialIconRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.ui.util.UiLayerCache;
import com.aurora.client.util.SmoothScroll;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aurora's main settings screen — the single canonical Modules + Settings
 * screen (Stage 4 consolidation).
 *
 * <p>Layout matches the pre-migration screen (left sidebar + content area,
 * 360x240 centered window), rendered through the shared themed components
 * and the high-res AA engine. Translucent fills are restored to their
 * pre-migration alpha values (token-scaled so they stay Dark/Light correct).
 * The static chrome (window, sidebar, settled cards/toggles) is rasterized
 * once into an off-screen cache and blitted each frame, so only live
 * overlays (hover, animated toggles, text, search glow) re-render per frame.
 */
public class AuroraScreen extends Screen implements ThemedScreen {

    private static final float BOX_W = 360;
    private static final float BOX_H = 240;

    // Sidebar nav tabs: one pitch drives every tab's Y (22px tab + 6px gap),
    // so the three gaps cannot drift apart. TAB_H must stay in sync with the
    // painted chip height below.
    private static final float TAB_FIRST_Y = 48;
    private static final float TAB_PITCH = 28;
    private static final float TAB_H = 22;

    private int selectedCategory = 0; // 0: Mods, 1: Settings
    private boolean gridLayout = true; // false: list, true: grid

    private EditBox searchField;
    private String searchQuery = "";

    /**
     * One scroll per tab — τ from the fps-keyed table below, snap 0.1,
     * wheel step 30, grab-where-clicked thumb that keeps easing toward the
     * moving target during drags (the screen's exact pre-R2 tuning; only
     * the active tab's position advances, the inactive one stays frozen).
     */
    private final SmoothScroll[] scrolls = {
            new SmoothScroll(AuroraScreen::scrollTauMs).setSnapEpsilon(0.1),
            new SmoothScroll(AuroraScreen::scrollTauMs).setSnapEpsilon(0.1),
    };
    private FeatureSetting activeDragSetting = null;

    private final Map<String, ToggleSwitch> sectionToggles = new HashMap<>();

    private final UiLayerCache layerCache = new UiLayerCache();

    public AuroraScreen() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        super.init();
        for (SmoothScroll s : scrolls) s.resetClock();
        sectionToggles.clear();

        for (FeatureMetadata m : FeatureRegistry.settings()) {
            sectionToggles.put(m.id, new ToggleSwitch(m::isEnabled, m::setEnabled));
        }

        searchField = new EditBox(this.font, 0, 0, 202, 20, Component.literal("Search..."));
        searchField.setHint(Component.literal("Search modules..."));
        searchField.setBordered(false);
        searchField.setTextColor(0xFFFFFFFF);
        searchField.setResponder(s -> this.searchQuery = s);
        this.addWidget(searchField);
    }

    private float boxX() { return (this.width - BOX_W) / 2.0f; }
    private float boxY() { return (this.height - BOX_H) / 2.0f; }
    private float mainX() { return boxX() + 90; }
    private float mainY() { return boxY() + 12; }

    /**
     * The scroll viewport every scroll-related number must agree on: the
     * scissor content window's resting bounds, the card cull, maxScroll's
     * 180px visible height, and the scrollbar track. Anchored to mainY()'s
     * own +36/+216 content offsets — never re-derived from BOX_H here, or
     * the +12 main-area offset gets counted twice and the track (thumb
     * included) slides below the window border.
     */
    private float viewTop() { return mainY() + 36; }
    private float viewBot() { return mainY() + 216; }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Glass pilot: when a live world is behind the screen, skip vanilla's
     * background sandwich (full-screen blur + dark gradient) — the glass
     * panels must sample the LIVE world, not an already-darkened,
     * already-blurred backdrop (same reasoning as FeatureDetailScreen's
     * no-op override on the Theme pilot). With no level loaded the glass
     * renderer declines anyway (its menu-context guard) and the opaque
     * fallback wants the vanilla backdrop as before, so the override is
     * conditional on the same validity check the renderer uses.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (liveWorldBackdrop()) return;
        super.renderBackground(g, mouseX, mouseY, delta);
    }

    /**
     * True when the main render target holds a live world — i.e. the glass
     * capture source is valid. Mirrors {@code BlurPanelRenderer}'s own
     * menu-context guard so this screen never asks for glass (and never
     * drops the vanilla backdrop) in a context where glass cannot engage.
     */
    private boolean liveWorldBackdrop() {
        return this.minecraft != null && this.minecraft.level != null;
    }

    /** Single canonical entry point (the pre-migration screen is gone). */
    public static Screen create() {
        return new AuroraScreen();
    }

    /** Token color with an alpha override — restores the pre-migration translucency, mode-aware. */
    private static int alpha(ThemeToken t, float a) {
        return (Math.round(a * 255f) << 24) | (ThemeManager.color(t) & 0x00FFFFFF);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        tickSmoothScroll();

        // ---- Glass pilot: live GPU glass UNDER the dim ----
        // Same layering contract as the Theme screen's window: the window
        // is the DEPRESSED surface (main containers read as recessed) and
        // its tint is the ordinary WINDOW_FILL fill, whose alpha already
        // carries the theme's Background Opacity — still exactly one
        // application point. Drawn before the overlay dim so the dim veils
        // the panel and its surroundings equally. When the renderer declines
        // (menu context with no world, screenshot suppression, failure)
        // glassWindow is false and the cached chrome below carries the old
        // opaque fill + outline instead (see the glass bit in the version
        // hash, which forces the re-raster on the switch).
        boolean glassWindow = false;
        if (liveWorldBackdrop()) {
            float radius = ThemeManager.current().roundness().radius();
            glassWindow = BlurPanelRenderer.renderPanel(g, boxX(), boxY(), BOX_W, BOX_H,
                    radius, BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX,
                    BlurPanelRenderer.Lighting.depressed(), BlurPanelRenderer.Priority.WINDOW);
            if (glassWindow) {
                RenderUtil.drawRoundedRectAA(g, boxX(), boxY(), BOX_W, BOX_H, radius,
                        ThemeManager.color(ThemeToken.WINDOW_FILL));
                BlurPanelRenderer.drawRimFinish(g, boxX(), boxY(), BOX_W, BOX_H, radius);
            }
        }

        g.fill(0, 0, this.width, this.height, ThemeManager.color(ThemeToken.OVERLAY_DIM));

        com.mojang.blaze3d.pipeline.RenderTarget main = this.minecraft != null ? this.minecraft.getMainRenderTarget() : null;
        int fbW = (main != null && main.width > 0) ? main.width : this.width;
        int fbH = (main != null && main.height > 0) ? main.height : this.height;
        int guiScale = Math.max(1, (int) this.minecraft.getWindow().getGuiScale());

        long version = ThemeManager.generation() * 1_000_003L
                ^ ((long) fbW * 7919L)
                ^ ((long) fbH * 17L)
                ^ ((long) guiScale * 65537L)
                ^ (glassWindow ? 0x5BD1B2C1L : 0L); // glass state changes cached fill presence

        if (!layerCache.isCurrent(version)) {
            layerCache.ensureSize(fbW, fbH);
            layerCache.clear();
            RenderUtil.RectSink prev = RenderUtil.beginCapture(layerCache.sink());
            try {
                captureStatic(g, glassWindow);
            } finally {
                RenderUtil.endCapture(prev);
            }
            layerCache.commit(version);
        }

        layerCache.blit(g, this.width, this.height);

        renderLive(g, mouseX, mouseY, delta);

        super.render(g, mouseX, mouseY, delta);
        FeatureSetting.drawPendingTooltip(g, this.width, this.height);
    }

    /** Static chrome + settled toggle tracks, rasterized once into the cache. */
    private void captureStatic(GuiGraphics g, boolean glassWindow) {
        float bx = boxX(), by = boxY();
        float radius = ThemeManager.current().roundness().radius();

        for (int i = 1; i <= 3; i++) {
            int a = Math.max(0, 35 - i * 10);
            RenderUtil.drawRoundedOutlineAA(g, bx - i * 2, by - i * 2, BOX_W + i * 4, BOX_H + i * 4,
                    radius + i * 2, 2.0f, (a << 24) | 0x000000);
        }
        if (!glassWindow) {
            // Glass pilot: the live glass material supplies the fill + rim;
            // stacking the old fill/outline on top would read as a double
            // surface. The glow rings above stay — they read as a drop
            // shadow, not an outline.
            RenderUtil.drawRoundedRectAA(g, bx, by, BOX_W, BOX_H, radius, ThemeManager.color(ThemeToken.WINDOW_FILL));
            RenderUtil.drawRoundedOutlineAA(g, bx, by, BOX_W, BOX_H, radius, 1.0f, alpha(ThemeToken.ON_BACKGROUND, 0x13 / 255f));
        }

        RenderUtil.drawRoundedRectAA(g, bx + 80, by + 12, 1, BOX_H - 24, 0.5f, alpha(ThemeToken.ON_BACKGROUND, 0x08f));
    }

    /** Card/toggle geometry for module index {@code i}; null when off-screen. [cx,cy,cw,ch,tx,ty,tw,th] */
    private float[] cardBounds(int i, List<Module> mods) {
        float mx = mainX(), my = mainY();
        float cx, cy, cw, ch;
        if (gridLayout) {
            cw = 80; ch = 80;
            cx = mx + 4 + (i % 3) * (cw + 4);
            cy = (float) (my + 38 + (i / 3) * (ch + 4) - scrolls[0].current());
        } else {
            cw = 250; ch = 48;
            cx = mx;
            cy = (float) (my + 38 + i * (ch + 6) - scrolls[0].current());
        }
        if (cy + ch < viewTop() || cy > viewBot()) return null;
        return new float[]{cx, cy, cw, ch};
    }

    private void renderLive(GuiGraphics g, int mouseX, int mouseY, float delta) {
        float bx = boxX(), by = boxY();
        Font tr = this.font;

        g.drawString(tr, "AURORA", (int) (bx + 16), (int) (by + 16), ThemeManager.color(ThemeToken.ACCENT), false);

        String[] cats = {"Mods", "Settings"};
        for (int i = 0; i < 2; i++) {
            float catY = by + TAB_FIRST_Y + i * TAB_PITCH;
            boolean hover = mouseX >= bx + 8 && mouseX <= bx + 72 && mouseY >= catY && mouseY <= catY + TAB_H;
            boolean sel = selectedCategory == i;
            // Glass pilot: the category pair is a segmented control — every
            // chip is RAISED glass, neutral when unselected and
            // accent-STAINED when selected (selection reads through the
            // tint alone, exactly like the Theme screen's segments). On
            // decline the flat wash/hover fills return unchanged.
            boolean chipGlass = liveWorldBackdrop() && BlurPanelRenderer.renderPanel(
                    g, bx + 8, catY, 64, TAB_H, 5,
                    BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX,
                    BlurPanelRenderer.Lighting.raised());
            if (chipGlass) {
                RenderUtil.drawRoundedRectAA(g, bx + 8, catY, 64, TAB_H, 5,
                        sel ? ThemeManager.stainedTint()
                             : ThemeManager.color(ThemeToken.WINDOW_FILL));
                BlurPanelRenderer.drawRimFinish(g, bx + 8, catY, 64, TAB_H, 5);
            } else if (sel) {
                RenderUtil.drawRoundedRectAA(g, bx + 8, catY, 64, 22, 5, alpha(ThemeToken.ACCENT, 0x26 / 255f));
            } else if (hover) {
                RenderUtil.drawRoundedRectAA(g, bx + 8, catY, 64, 22, 5, ThemeManager.surfaceColor(ThemeToken.SURFACE_VARIANT));
            }
            int txt = chipGlass && sel ? ThemeManager.color(ThemeToken.ON_ACCENT)
                    : sel ? ThemeManager.color(ThemeToken.ON_BACKGROUND)
                    : hover ? ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY)
                    : ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED);
            g.drawString(tr, cats[i], (int) (bx + 16), (int) (catY + 7), txt, false);
        }

        float profY = by + TAB_FIRST_Y + 2 * TAB_PITCH;
        boolean pHover = mouseX >= bx + 8 && mouseX <= bx + 72 && mouseY >= profY && mouseY <= profY + TAB_H;
        // Glass pilot: Profiles is a plain action button — neutral raised
        // glass (never stained: it is not a selected/primary state).
        boolean profGlass = liveWorldBackdrop() && BlurPanelRenderer.renderPanel(
                g, bx + 8, profY, 64, TAB_H, 5,
                BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX,
                BlurPanelRenderer.Lighting.raised());
        if (profGlass) {
            RenderUtil.drawRoundedRectAA(g, bx + 8, profY, 64, TAB_H, 5,
                    ThemeManager.color(ThemeToken.WINDOW_FILL));
            BlurPanelRenderer.drawRimFinish(g, bx + 8, profY, 64, TAB_H, 5);
        } else if (pHover) {
            RenderUtil.drawRoundedRectAA(g, bx + 8, profY, 64, 22, 5, ThemeManager.surfaceColor(ThemeToken.SURFACE_VARIANT));
        }
        g.drawString(tr, "Profiles", (int) (bx + 16), (int) (profY + 7),
                pHover ? ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY) : ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED), false);

        if (selectedCategory == 0) renderModulesLive(g, mouseX, mouseY, delta);
        else renderSettingsLive(g, mouseX, mouseY, delta);

        drawScrollbar(g);
    }

    private void renderModulesLive(GuiGraphics g, int mouseX, int mouseY, float delta) {
        float mx = mainX(), my = mainY();
        Font tr = this.font;

        float btnY = my, btnSize = 20;
        drawLayoutButton(g, mx, btnY, btnSize, !gridLayout, mouseX, mouseY, true);
        drawLayoutButton(g, mx + 24, btnY, btnSize, gridLayout, mouseX, mouseY, false);

        // The search bar occupies exactly the original placeholder's bounds —
        // top edge aligned with the list/grid buttons, 202x20. EditBoxMixin
        // draws the themed translucent surface + border at these bounds, so
        // no manual background is drawn here (that would double-composite).
        float searchBarX = mx + 48;
        searchField.visible = true;
        searchField.setX(Math.round(searchBarX));
        searchField.setY(Math.round(my));
        searchField.setWidth(202);

        // Render the EditBox manually (addWidget registers it for input only,
        // not for rendering) so typed text + caret actually draw.
        searchField.render(g, mouseX, mouseY, delta);

        List<Module> mods = filteredModules();
        g.enableScissor((int) mx, (int) (boxY() + 36), (int) (mx + 254), (int) (boxY() + BOX_H - 10));
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            float[] b = cardBounds(i, mods);
            if (b == null) continue;
            float cx = b[0], cy = b[1], cw = b[2], ch = b[3];
            boolean hover = mouseX >= cx && mouseX <= cx + cw && mouseY >= cy && mouseY <= cy + ch;
            boolean on = m.isEnabled();

            // Glass pilot: each tile is its own RAISED glass panel — its own
            // correctly-cropped, correctly-scaled backdrop slice (per-element
            // capture, never the whole scene stretched). Neutral tint when
            // off, accent-STAINED when on (the enabled state reads through
            // the tint, like a selected segment); hover adds a faint
            // mode-aware wash on top. On decline the flat surface fill +
            // accent wash/border return unchanged.
            boolean tileGlass = liveWorldBackdrop() && BlurPanelRenderer.renderPanel(
                    g, cx, cy, cw, ch, 6,
                    BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX,
                    BlurPanelRenderer.Lighting.raised(), BlurPanelRenderer.Priority.ROW);
            if (tileGlass) {
                RenderUtil.drawRoundedRectAA(g, cx, cy, cw, ch, 6,
                        on ? ThemeManager.stainedTint()
                           : ThemeManager.color(ThemeToken.WINDOW_FILL));
                BlurPanelRenderer.drawRimFinish(g, cx, cy, cw, ch, 6);
                if (hover) {
                    RenderUtil.drawRoundedRectAA(g, cx, cy, cw, ch, 6,
                            alpha(ThemeToken.ON_BACKGROUND, 0x1A / 255f));
                }
            } else {
                // Tile surface — same dark/translucent character as the panel
                // (surface RGB + the panel's opacity-driven alpha), hover lifts it.
                RenderUtil.drawRoundedRectAA(g, cx, cy, cw, ch, 6,
                        hover ? ThemeManager.surfaceColor(ThemeToken.SURFACE_VARIANT) : ThemeManager.surfaceColor(ThemeToken.SURFACE));
                if (on) {
                    // Accent wash + border = the enabled indicator (no switch widget).
                    RenderUtil.drawRoundedRectAA(g, cx, cy, cw, ch, 6, alpha(ThemeToken.ACCENT, 0x14 / 255f));
                    RenderUtil.drawRoundedOutlineAA(g, cx, cy, cw, ch, 6, 1.0f, ThemeManager.color(ThemeToken.ACCENT));
                }
            }

            if (gridLayout) {
                drawTileIcon(g, tr, m, cx + cw / 2f, cy + 30, 28f, on, tileGlass);
                String name = fit(tr, m.name, (int) cw - 8);
                g.drawString(tr, name, (int) (cx + (cw - tr.width(name)) / 2f), (int) (cy + ch - 20),
                        ThemeManager.color(ThemeToken.ON_BACKGROUND), false);
            } else {
                drawTileIcon(g, tr, m, cx + 20, cy + ch / 2f, 16f, on, tileGlass);
                String name = fit(tr, m.name, 130);
                g.drawString(tr, name, (int) (cx + 40), (int) (cy + 8),
                        ThemeManager.color(ThemeToken.ON_BACKGROUND), false);
                RenderUtil.drawWordWrapMaxLines(tr, g, m.description, cx + 40, cy + 20, 185, 1,
                        ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED));
            }
        }
        if (mods.isEmpty()) {
            g.drawString(tr, "No modules found.", (int) (mx + 12), (int) (my + 45), ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED), false);
        }
        g.disableScissor();
    }

    private void renderSettingsLive(GuiGraphics g, int mouseX, int mouseY, float delta) {
        float mx = mainX(), my = mainY();
        Font tr = this.font;
        searchField.visible = false;

        g.enableScissor((int) mx, (int) (boxY() + 36), (int) (mx + 254), (int) (boxY() + BOX_H - 10));
        float y = my + 38 - (float) scrolls[1].current();
        for (FeatureMetadata m : FeatureRegistry.settings()) {
            int headerH = 22;
            if (y + headerH > my + 36 && y < my + 216) {
                g.drawString(tr, m.displayName, (int) (mx + 4), (int) (y + 6), ThemeManager.color(ThemeToken.ON_BACKGROUND), false);
                ToggleSwitch t = sectionToggles.get(m.id);
                if (t != null) {
                    float tw = 28, th = 15;
                    float tx = mx + 254 - tw - 10;
                    t.layout(tx, y + 4, tw, th);
                    t.renderShapes(g, tx, y + 4, tw, th);
                    t.renderOverlay(g, tx, y + 4, tw, th, mouseX, mouseY);
                }
            }
            y += headerH;
            for (FeatureSetting s : m.settings) {
                int rh = s.height();
                if (y + rh > my + 36 && y < my + 216) {
                    s.render(g, (int) (mx + 4), (int) y, 246, mouseX, mouseY);
                }
                y += rh + 4;
            }
            y += 8;
        }
        g.disableScissor();
    }

    private void drawLayoutButton(GuiGraphics g, float x, float y, float size, boolean selected, int mouseX, int mouseY, boolean list) {
        boolean hover = mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;
        // Glass pilot: the list/grid pair is a segmented control — RAISED
        // glass on both, accent-STAINED on the active one (the genuinely
        // selected control), neutral on the inactive one. On decline the
        // flat fills + borders return unchanged.
        boolean btnGlass = liveWorldBackdrop() && BlurPanelRenderer.renderPanel(
                g, x, y, size, size, 4,
                BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX,
                BlurPanelRenderer.Lighting.raised());
        if (btnGlass) {
            RenderUtil.drawRoundedRectAA(g, x, y, size, size, 4,
                    selected ? ThemeManager.stainedTint()
                             : ThemeManager.color(ThemeToken.WINDOW_FILL));
            BlurPanelRenderer.drawRimFinish(g, x, y, size, size, 4);
            if (hover && !selected) {
                // Hover cue preserved on glass (the tint stays constant, like
                // every glass control): a faint mode-aware wash.
                RenderUtil.drawRoundedRectAA(g, x, y, size, size, 4,
                        alpha(ThemeToken.ON_BACKGROUND, 0x1A / 255f));
            }
        } else {
            int bg = selected ? alpha(ThemeToken.ACCENT, 0x26 / 255f) : (hover ? ThemeManager.surfaceColor(ThemeToken.SURFACE_VARIANT) : ThemeManager.surfaceColor(ThemeToken.SURFACE));
            int border = selected ? ThemeManager.color(ThemeToken.ACCENT) : alpha(ThemeToken.ON_BACKGROUND, 0x08f);
            RenderUtil.drawRoundedRectAA(g, x, y, size, size, 4, bg);
            RenderUtil.drawRoundedOutlineAA(g, x, y, size, size, 4, 1.0f, border);
        }
        // Same contract as the tiles: on stained glass the icon takes the
        // contrast-derived ON_ACCENT; the flat fallback's ~15% accent wash is
        // dark enough that the accent glyph still reads there.
        int iconCol = selected && btnGlass ? ThemeManager.color(ThemeToken.ON_ACCENT)
                : selected ? ThemeManager.color(ThemeToken.ACCENT)
                : alpha(ThemeToken.ON_BACKGROUND, 0x53f);
        if (list) {
            for (int i = 0; i < 3; i++) {
                float dy = y + 5 + i * 4.5f;
                RenderUtil.drawRoundedRectAA(g, x + 5, dy, 2, 2, 1, iconCol);
                RenderUtil.drawRoundedRectAA(g, x + 9, dy + 0.5f, 6, 1, 0.5f, iconCol);
            }
        } else {
            RenderUtil.drawRoundedRectAA(g, x + 5, y + 5, 4, 4, 1, iconCol);
            RenderUtil.drawRoundedRectAA(g, x + 11, y + 5, 4, 4, 1, iconCol);
            RenderUtil.drawRoundedRectAA(g, x + 5, y + 11, 4, 4, 1, iconCol);
            RenderUtil.drawRoundedRectAA(g, x + 11, y + 11, 4, 4, 1, iconCol);
        }
    }

    private List<Module> filteredModules() {
        List<Module> out = new ArrayList<>();
        String q = searchQuery == null ? "" : searchQuery.toLowerCase();
        for (Module m : ModuleManager.getInstance().getModules()) {
            if (q.isEmpty() || m.name.toLowerCase().contains(q) || m.description.toLowerCase().contains(q)) {
                out.add(m);
            }
        }
        return out;
    }

    private static String fit(Font tr, String text, int maxW) {
        if (tr.width(text) <= maxW) return text;
        return tr.plainSubstrByWidth(text, maxW - tr.width("...")) + "...";
    }

    /** Render a module's icon (FeatureIcons glyph via the material-symbols font). */
    private void drawTileIcon(GuiGraphics g, Font tr, Module m, float cx, float cy, float targetPx,
                              boolean on, boolean stainedGlass) {
        String glyph = FeatureIcons.get(m.id);
        if (glyph.isEmpty()) return;
        // Same contrast contract as the sidebar chips' text: a stained glass
        // tile's dominant tint IS the accent (stainedTint's reference), so the
        // glyph takes ON_ACCENT — the token PaletteEngine contrast-derives
        // against that same accent — never the accent itself. On the flat
        // fallback the wash is only ~8% accent over the surface, so the accent
        // glyph (per-module override honored) and the muted off-state glyph
        // still read and are kept unchanged.
        int color;
        if (on && stainedGlass) {
            color = ThemeManager.color(ThemeToken.ON_ACCENT);
        } else if (on) {
            int accent = ModuleAccentColors.get(m.id);
            color = accent != 0 ? accent : ThemeManager.color(ThemeToken.ACCENT);
        } else {
            color = ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED);
        }
        // Crisp native-resolution glyph (the shared font atlas is
        // point-sampled and degrades badly at these sizes); em size = the
        // box the old glyph-scaling math targeted, so the footprint is
        // unchanged.
        MaterialIconRenderer.drawIcon(g, tr, glyph, cx, cy, targetPx, color);
    }

    private FeatureMetadata findMeta(String id) {
        for (FeatureMetadata fm : FeatureRegistry.modules()) if (fm.id.equals(id)) return fm;
        for (FeatureMetadata fm : FeatureRegistry.settings()) if (fm.id.equals(id)) return fm;
        return null;
    }

    private double computeMaxScroll() {
        if (selectedCategory == 0) {
            List<Module> mods = filteredModules();
            if (gridLayout) {
                int rows = (mods.size() + 2) / 3;
                return Math.max(0, rows * (80 + 4) - 180);
            }
            return Math.max(0, mods.size() * (48 + 6) - 180);
        }
        double h = 0;
        for (FeatureMetadata m : FeatureRegistry.settings()) {
            h += 22;
            for (FeatureSetting s : m.settings) h += s.height() + 4;
            h += 8;
        }
        return Math.max(0, h - 180);
    }

    private void tickSmoothScroll() {
        // Only the active tab's position moves (the other tab's glide stays
        // frozen mid-switch, as before); the inactive instance's clock is
        // still stamped so resuming it sees a fresh dt — the behavior the
        // old single shared clock gave.
        scrolls[1 - selectedCategory].touchClock();
        scrolls[selectedCategory].advance(computeMaxScroll());
    }

    /**
     * τ (ms) for the scroll glide, keyed off the GUI fps cap — the screen's
     * historical table (snappier easing at low caps, lazier when uncapped).
     */
    private static double scrollTauMs() {
        int fps = com.aurora.client.config.AuroraConfig.get().guiFpsLimit;
        if (fps <= 15) return 24.0;
        if (fps <= 25) return 32.0;
        if (fps <= 30) return 38.0;
        if (fps <= 60) return 54.0;
        return 65.0;
    }

    /** The scrollbar thumb's int-truncated geometry — the exact pre-R2 cast pattern. */
    private int thumbHeightPx(double maxScroll) {
        SmoothScroll scroll = scrolls[selectedCategory];
        float trackH = viewBot() - viewTop();
        return Math.max(24, (int) scroll.thumbHeight(trackH, maxScroll, 24));
    }

    private int thumbYPx(double maxScroll, int thumbH) {
        SmoothScroll scroll = scrolls[selectedCategory];
        float trackH = viewBot() - viewTop();
        return (int) viewTop() + (int) ((trackH - thumbH) * scroll.ratio(maxScroll));
    }

    private void drawScrollbar(GuiGraphics g) {
        double maxScroll = computeMaxScroll();
        if (maxScroll <= 0) return;
        int thumbH = thumbHeightPx(maxScroll);
        int thumbY = thumbYPx(maxScroll, thumbH);
        // Painted exactly as before R2 — SmoothScroll supplies geometry and
        // drag state only: ON_BACKGROUND at 0x30 (0x55 while dragging), no
        // hover highlight.
        int col = ThemeManager.color(ThemeToken.ON_BACKGROUND) & 0x00FFFFFF;
        int a = scrolls[selectedCategory].isDragging() ? 0x55 : 0x30;
        RenderUtil.drawRoundedRectAA(g, mainX() + 258, thumbY, 3, thumbH, 2, (a << 24) | col);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent _ev, boolean _dbl) {
        double mouseX = _ev.x(); double mouseY = _ev.y(); int button = _ev.button();
        float bx = boxX(), by = boxY(), mx = mainX(), my = mainY();

        if (super.mouseClicked(_ev, _dbl)) return true;

        for (int i = 0; i < 2; i++) {
            float catY = by + TAB_FIRST_Y + i * TAB_PITCH;
            if (mouseX >= bx + 8 && mouseX <= bx + 72 && mouseY >= catY && mouseY <= catY + TAB_H) {
                selectedCategory = i;
                return true;
            }
        }
        float profY = by + TAB_FIRST_Y + 2 * TAB_PITCH;
        if (mouseX >= bx + 8 && mouseX <= bx + 72 && mouseY >= profY && mouseY <= profY + TAB_H) {
            if (this.minecraft != null) this.minecraft.setScreen(new ProfileManagerScreen(this));
            return true;
        }

        if (selectedCategory == 0) {
            if (button == 0 && mouseX >= mx && mouseX <= mx + 20 && mouseY >= my && mouseY <= my + 20) { gridLayout = false; return true; }
            if (button == 0 && mouseX >= mx + 24 && mouseX <= mx + 44 && mouseY >= my && mouseY <= my + 20) { gridLayout = true; return true; }
            List<Module> mods = filteredModules();
            for (int i = 0; i < mods.size(); i++) {
                Module m = mods.get(i);
                float[] b = cardBounds(i, mods);
                if (b == null) continue;
                if (mouseX >= b[0] && mouseX <= b[0] + b[2] && mouseY >= b[1] && mouseY <= b[1] + b[3]) {
                    if (button == 1) {
                        FeatureMetadata fm = findMeta(m.id);
                        if (fm != null && fm.hasDetail() && this.minecraft != null) {
                            this.minecraft.setScreen(new FeatureDetailScreen(this, fm));
                        }
                        return true;
                    }
                    if (button == 0) { m.toggle(); return true; }
                }
            }
        } else {
            float y = my + 38 - (float) scrolls[1].current();
            for (FeatureMetadata m : FeatureRegistry.settings()) {
                if (button == 0 && mouseX >= mx + 254 - 40 && mouseX <= mx + 254 && mouseY >= y + 4 && mouseY <= y + 19) {
                    m.setEnabled(!m.isEnabled());
                    com.aurora.client.config.AuroraConfig.save();
                    return true;
                }
                y += 22;
                for (FeatureSetting s : m.settings) {
                    int rh = s.height();
                    if (s.mouseClicked(mouseX, mouseY, button, (int) (mx + 4), (int) y, 246)) {
                        activeDragSetting = s;
                        return true;
                    }
                    y += rh + 4;
                }
                y += 8;
            }
        }

        double maxScroll = computeMaxScroll();
        if (maxScroll > 0 && button == 0 && mouseX >= mx + 255 && mouseX <= mx + 262 && mouseY >= viewTop() && mouseY <= viewBot()) {
            // Grab-where-clicked: the grab offset is the cursor's distance
            // from the thumb's top at click time — the screen's historical
            // drag semantics.
            scrolls[selectedCategory].beginThumbDrag(mouseY - thumbYPx(maxScroll, thumbHeightPx(maxScroll)));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent _ev, double dx, double dy) {
        double mouseX = _ev.x(); double mouseY = _ev.y(); int button = _ev.button();
        SmoothScroll scroll = scrolls[selectedCategory];
        if (scroll.isDragging()) {
            // jumpCurrent = false: only the target moves, the rendered
            // position keeps easing toward it while dragging (the screen's
            // historical feel).
            scroll.dragThumb(mouseY, viewTop(), viewBot() - viewTop(), computeMaxScroll(), 24, false);
            return true;
        }
        if (activeDragSetting != null && activeDragSetting.mouseDragged(mouseX, mouseY, button, dx, dy, 0, 0, 246)) {
            return true;
        }
        return super.mouseDragged(_ev, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent _ev) {
        double mouseX = _ev.x(); double mouseY = _ev.y(); int button = _ev.button();
        SmoothScroll scroll = scrolls[selectedCategory];
        if (scroll.isDragging()) { scroll.endThumbDrag(); return true; }
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
        scrolls[selectedCategory].wheel(vertical, 30, computeMaxScroll());
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
}





