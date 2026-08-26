package com.aurora.client.screen;
import com.aurora.client.ui.util.AuroraFontRenderer;

import com.aurora.client.modrinth.ModrinthApi;
import com.aurora.client.modrinth.ModrinthProject;
import com.aurora.client.modrinth.ModrinthVersion;
import com.aurora.client.modrinth.PackIconCache;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.AuroraShapes;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-game browser for Modrinth resource packs.
 *
 * <p>Layout: a left sidebar with category tabs matching Modrinth's own
 * resourcepack site (resolutions, categories, features), a search bar at
 * the top of the main panel, and a scrollable grid of cards below. Each
 * card shows a thumbnail (or letter placeholder), title, author, download
 * count, and an Install button. Previously-viewed categories and searches
 * are cached for instant re-display.
 *
 * <p><b>Resourcify-inspired enhancements:</b>
 * <ul>
 *   <li>Theme integration — every color routes through {@link AuroraTheme},
 *       so the browser respects the user's configured accent + background
 *       palette instead of hardcoding white-on-near-black.</li>
 *   <li>Eased hover feedback — cards, tabs and install buttons animate
 *       their fill/outline over ~150 ms via {@link AuroraAnim#easeOutCubic},
 *       matching the rest of Aurora's iOS-style control language.</li>
 *   <li>Smooth (target-based) scrolling for both the card grid and the
 *       category sidebar — wheel input sets a target offset and the render
 *       loop lerps toward it, so scrolling glides instead of snapping.</li>
 *   <li>Material cards — drop shadow + top sheen via {@link AuroraShapes}
 *       give tiles depth; thumbnails are framed with a rounded inset.</li>
 *   <li>Animated loading spinner instead of static "Loading…" text.</li>
 *   <li>Pack detail modal — clicking a card body opens a centered detail
 *       sheet (Resourcify's signature affordance) showing a large preview,
 *       full description and a primary Install action.</li>
 * </ul>
 *
 * <p>Threading: all network I/O runs on {@link ModrinthApi#IO_EXECUTOR}.
 * Results are marshalled back into render-thread state via a single
 * {@link AtomicReference} snapshot that {@link #tick()} drains.
 */
public class ResourcePackBrowserScreen extends Screen implements ThemedScreen {

    // ---- Layout constants ----
    private static final int SIDEBAR_W      = 130;
    private static final int SIDEBAR_PAD    = 8;
    private static final int TOP_BAR_H      = 44;
    private static final int CARD_W         = 220;
    private static final int CARD_H         = 96;
    private static final int CARD_GAP       = 10;
    private static final int LIST_TOP       = TOP_BAR_H + 12;
    private static final int LIST_BOTTOM_PAD = 36;
    private static final int THUMB_SIZE     = 64;
    private static final int THUMB_PAD      = 10;
    private static final int TAB_H          = 22;
    private static final int TAB_GAP        = 4;
    private static final int HEADER_H       = 16;       // sidebar section header row height
    private static final int HOVER_MS       = 150;      // eased-hover duration
    // (removed SCROLL_EASE — scroll easing is now delta-time based in render(),
    //  see the lastFrameMs / scrollAlpha block at the top of render().)

    /**
     * Category tabs matching Modrinth's resourcepack category API
     * ({@code /v2/tag/category?category=resourcepack}). Slugs are sent
     * directly as facets; a wrong slug silently returns zero results.
     * Grouped by Modrinth's own {@code header} field (resolutions /
     * categories / features) to mirror modrinth.com/resourcepacks.
     */
    private static final CategoryTab[] CATEGORIES = {
            new CategoryTab("All", null, Section.NONE),
            // ---- Categories ----
            new CategoryTab("Categories", null, Section.HEADER),
            new CategoryTab("Vanilla-like", "vanilla-like", Section.NONE),
            new CategoryTab("Simplistic", "simplistic", Section.NONE),
            new CategoryTab("Realistic", "realistic", Section.NONE),
            new CategoryTab("Themed", "themed", Section.NONE),
            new CategoryTab("Modded", "modded", Section.NONE),
            new CategoryTab("Combat", "combat", Section.NONE),
            new CategoryTab("Cursed", "cursed", Section.NONE),
            new CategoryTab("Decoration", "decoration", Section.NONE),
            new CategoryTab("Tweaks", "tweaks", Section.NONE),
            new CategoryTab("Utility", "utility", Section.NONE),
            // ---- Features ----
            new CategoryTab("Features", null, Section.HEADER),
            new CategoryTab("GUI", "gui", Section.NONE),
            new CategoryTab("Audio", "audio", Section.NONE),
            new CategoryTab("Blocks", "blocks", Section.NONE),
            new CategoryTab("Entities", "entities", Section.NONE),
            new CategoryTab("Equipment", "equipment", Section.NONE),
            new CategoryTab("Fonts", "fonts", Section.NONE),
            new CategoryTab("Models", "models", Section.NONE),
            new CategoryTab("Environment", "environment", Section.NONE),
            new CategoryTab("Core Shaders", "core-shaders", Section.NONE),
            new CategoryTab("Locale", "locale", Section.NONE),
    };

    private enum Section { NONE, HEADER }

    private record CategoryTab(String displayName, String slug, Section section) {}

    private final Screen parent;

    // ---- Search state ----
    private EditBox searchField;
    private String pendingQuery = "";
    private String activeCategory = null; // null = "All"
    private long lastTypedMs = 0L;
    private static final long DEBOUNCE_MS = 400L;

    // ---- Result state (render-thread owned) ----
    private volatile List<ModrinthProject> results = Collections.emptyList();
    private volatile String loadError = null;
    private volatile boolean loading = false;
    private CompletableFuture<?> activeSearch = null;

    private final AtomicReference<List<ModrinthProject>> pendingResults = new AtomicReference<>();
    private final AtomicReference<String> pendingError = new AtomicReference<>();

    /**
     * LRU cache of recent search results keyed by {@code query|category}.
     * Mirrors how Resourcify works — once a category or search has been
     * fetched, returning to it shows instantly without another network
     * round-trip. Capped at 32 entries to bound memory.
     */
    private final Map<String, List<ModrinthProject>> resultCache =
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<ModrinthProject>> eldest) {
                    return size() > 32;
                }
            };

    // ---- Per-card install state ----
    private final Map<String, CardState> cardStates = new HashMap<>();

    /**
     * Per-card truncated-text cache, keyed by {@code <field>:<projectId>@<width>}.
     * {@link #truncateToWidth} runs a {@code font.width()} loop that dominates
     * per-frame CPU when 40 cards each truncate 3-4 fields; caching the fitted
     * strings (and clearing on every result-set change) keeps the hot render
     * path allocation-free and removes ~120 font-width calls per frame.
     */
    private final Map<String, String> textFitCache = new HashMap<>();

    private static final class CardState {
        static final int IDLE = 0, RESOLVING = 1, DOWNLOADING = 2, DONE = 3, FAILED = 4;
        volatile int phase = IDLE;
        String message = "";
    }

    // ---- Smooth scroll (target-based lerp) ----
    private double scrollY = 0;
    private double scrollTargetY = 0;
    private boolean scrollbarDragging = false;
    /**
     * Wall-clock millis of the previous frame, used to drive frame-rate-
     * independent scroll easing in {@link #render}. The previous design eased
     * scroll in {@code tick()} (20 Hz), which caused visible stepping on
     * high-refresh displays because the rendered scroll position only changed
     * ~20×/s. Easing in render with a real dt keeps motion silky at 60 / 120 /
     * 240 Hz alike.
     */
    private long lastFrameMs = 0L;

    // ---- Sidebar scroll ----
    private double sidebarScroll = 0;
    private double sidebarScrollTarget = 0;
    private boolean sidebarDragging = false;

    // ---- Eased hover state (keyed by stable index: tab slug / project id / "card"+i) ----
    private final Map<String, Float> hoverT = new HashMap<>();
    private final Map<String, Long> hoverStart = new HashMap<>();
    private final Map<String, Boolean> hoverActive = new HashMap<>();

    // ---- Toast feedback ----
    private String toastText = null;
    private long toastUntilMs = 0L;

    // ---- Pack detail modal ----
    private ModrinthProject detailProject = null;
    private float detailOpenT = 0f;       // 0..1 open animation
    private boolean detailOpenTarget = false;
    private float detailSpinnerAngle = 0f;

    // ---- Loading spinner animation ----
    private float spinnerAngle = 0f;

    public ResourcePackBrowserScreen(Screen parent) {
        super(Component.literal("Resource Packs — Modrinth"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Search bar — positioned to the right of the sidebar.
        int fieldX = SIDEBAR_W + 16;
        int fieldW = Math.min(420, this.width - fieldX - 120);
        searchField = new EditBox(Minecraft.getInstance().font,
                fieldX, 12, fieldW, 22, Component.literal("Search resource packs"));
        searchField.setMaxLength(80);
        searchField.setHint(Component.literal("Search resource packs on Modrinth…"));
        searchField.setResponder(s -> { lastTypedMs = System.currentTimeMillis(); });
        this.addRenderableWidget(searchField);

        this.addRenderableWidget(new ButtonWidget(
                this.width - 80 - 16, 12, 80, 22,
                Component.literal("Done"), this::onClose));

        // Focus the search field immediately so typing works without a click.
        this.setInitialFocus(searchField);

        // Kick off an initial empty-query feed so the screen isn't blank.
        if (results.isEmpty() && !loading) {
            submitSearch("", null);
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Debounced search trigger.
        if (System.currentTimeMillis() - lastTypedMs >= DEBOUNCE_MS
                && searchField != null
                && !searchField.getValue().equals(pendingQuery)) {
            pendingQuery = searchField.getValue();
            submitSearch(pendingQuery, activeCategory);
        }
        // Drain pending results onto the render thread.
        List<ModrinthProject> pr = pendingResults.getAndSet(null);
        if (pr != null) {
            results = pr;
            loading = false;
            // Prune install state for cards no longer in the result set so
            // the map doesn't grow unboundedly across many searches.
            cardStates.keySet().retainAll(pr.stream()
                    .map(p -> p.projectId)
                    .collect(java.util.stream.Collectors.toList()));
        }
        String err = pendingError.getAndSet(null);
        if (err != null) {
            loadError = err;
            loading = false;
        }

        // NOTE: scroll easing moved into render() so it advances every frame
        // (delta-time based) instead of at the 20 Hz tick rate — high-refresh
        // displays no longer see scroll stepping.

        // Spinner advance.
        spinnerAngle = (spinnerAngle + 18f) % 360f;
        detailSpinnerAngle = (detailSpinnerAngle + 18f) % 360f;

        // Detail modal open/close easing.
        float detailTarget = detailOpenTarget ? 1f : 0f;
        if (detailOpenT != detailTarget) {
            float dir = detailTarget > detailOpenT ? 1f : -1f;
            detailOpenT += dir * (1f / 8f); // ~8 ticks = 400ms open/close
            detailOpenT = AuroraAnim.clamp01(detailOpenT);
            if (detailOpenT == 0f && !detailOpenTarget) {
                detailProject = null; // fully closed — drop reference
            }
        }
    }

    private static String cacheKey(String query, String category) {
        return query + "|" + (category == null ? "" : category);
    }

    private void submitSearch(String query, String category) {
        // New result set incoming — invalidate the per-card text-fit cache so
        // stale truncations from a previous search can't bleed through.
        textFitCache.clear();
        // Cache hit — show instantly, no network round-trip.
        String key = cacheKey(query, category);
        List<ModrinthProject> cached = resultCache.get(key);
        if (cached != null) {
            results = cached;
            loading = false;
            loadError = null;
            scrollY = scrollTargetY = 0;
            return;
        }

        loading = true;
        loadError = null;
        scrollY = scrollTargetY = 0;
        final String q = query;
        final String cat = category;
        final CompletableFuture<List<ModrinthProject>> future = ModrinthApi.search(q, cat);
        activeSearch = future;
        future.whenComplete((list, err) -> {
            if (activeSearch != future) return;
            if (err != null) {
                pendingError.set(err.getMessage() == null ? err.toString() : err.getMessage());
            } else {
                List<ModrinthProject> safe = list == null ? Collections.emptyList() : list;
                // Populate cache for instant re-display next time.
                resultCache.put(cacheKey(q, cat), safe);
                pendingResults.set(safe);
            }
        });
    }

    // ------------------------------------------------------------------
    //  Rendering
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Frame-rate-independent scroll easing. Advance toward the target every
        // rendered frame using the real elapsed wall-clock dt, so wheel scrolls
        // glide smoothly at any refresh rate (60/120/240 Hz) instead of stepping
        // at the 20 Hz tick rate. tau ≈ 80 ms reads as snappy but still eased.
        long nowMs = System.currentTimeMillis();
        long dtMs = lastFrameMs == 0L ? 16L : Math.min(64L, nowMs - lastFrameMs);
        lastFrameMs = nowMs;
        double scrollAlpha = 1.0 - Math.exp(-dtMs / 80.0);
        scrollY += (scrollTargetY - scrollY) * scrollAlpha;
        if (Math.abs(scrollTargetY - scrollY) < 0.25) scrollY = scrollTargetY;
        sidebarScroll += (sidebarScrollTarget - sidebarScroll) * scrollAlpha;
        if (Math.abs(sidebarScrollTarget - sidebarScroll) < 0.25) sidebarScroll = sidebarScrollTarget;

        // Themed backdrop — OVERLAY_DIM RGB at this screen's original 0x55
        // strength, so mode/accent derivation reaches even the dim layer.
        g.fill(0, 0, this.width, this.height,
                ThemeManager.withAlpha(ThemeManager.color(ThemeToken.OVERLAY_DIM), 0x55));

        // ---- Sidebar ----
        renderSidebar(g, mouseX, mouseY);

        // ---- Title ----
        g.drawString(this.font, this.title, SIDEBAR_W + 16, 16, AuroraTheme.TEXT_PRIMARY, false);

        // Result count subtitle
        int count = results.size();
        if (count > 0) {
            String countText = count + " pack" + (count == 1 ? "" : "s");
            g.drawString(this.font, Component.literal(countText),
                    SIDEBAR_W + 16, 28, AuroraTheme.TEXT_SECONDARY, false);
        }

        // ---- Grid region ----
        int gridLeft = gridLeft();
        int cols = columns();
        int listBottom = this.height - LIST_BOTTOM_PAD;

        g.enableScissor(gridLeft - 4, LIST_TOP - 2,
                gridLeft + cols * (CARD_W + CARD_GAP) - CARD_GAP + 4, listBottom);

        List<ModrinthProject> list = results;
        if (loading && list.isEmpty()) {
            renderSpinner(g, this.width / 2, LIST_TOP + 24, spinnerAngle);
            AuroraFontRenderer.drawCentered(g, this.font, Component.literal("Loading…"),
                    this.width / 2, LIST_TOP + 44, AuroraTheme.TEXT_SECONDARY);
        } else if (loadError != null && list.isEmpty()) {
            AuroraFontRenderer.drawCentered(g, this.font,
                    Component.literal("Couldn't reach Modrinth: " + loadError),
                    this.width / 2, LIST_TOP + 20, AuroraTheme.SEMANTIC_ERROR);
            AuroraFontRenderer.drawCentered(g, this.font, Component.literal("Check your connection and retry."),
                    this.width / 2, LIST_TOP + 34, AuroraTheme.TEXT_SECONDARY);
        } else if (list.isEmpty()) {
            String msg = pendingQuery.isEmpty() ? "No packs in this category." :
                    "No packs matched \"" + pendingQuery + "\".";
            AuroraFontRenderer.drawCentered(g, this.font, Component.literal(msg),
                    this.width / 2, LIST_TOP + 20, AuroraTheme.TEXT_SECONDARY);
        }

        // Viewport culling: compute the first/last visible row from the
        // scroll offset so off-screen cards never enter renderCard. The GPU
        // scissor only discards pixels at the rasterizer — those cards would
        // still cost CPU draw-call submissions + state setup. Skipping them
        // outright is strictly cheaper and is the dominant perf win when a
        // search returns many results.
        if (!list.isEmpty() && cols > 0) {
            int rowH = CARD_H + CARD_GAP;
            int colW = CARD_W + CARD_GAP;
            double topRow0 = LIST_TOP - scrollY;
            int firstRow = Math.max(0, (int) Math.floor((LIST_TOP - CARD_H - topRow0) / rowH));
            int lastRow = (int) Math.floor((listBottom - topRow0) / rowH);
            lastRow = Math.min(lastRow, (list.size() - 1) / cols);
            for (int row = firstRow; row <= lastRow; row++) {
                for (int col = 0; col < cols; col++) {
                    int i = row * cols + col;
                    if (i >= list.size()) break;
                    int x = gridLeft + col * colW;
                    int y = LIST_TOP + row * rowH - (int) scrollY;
                    renderCard(g, list.get(i), x, y, mouseX, mouseY);
                }
            }
        }

        g.disableScissor();

        // Scrollbar thumb — only when content overflows.
        renderScrollbar(g, mouseX, mouseY, gridLeft, cols, listBottom);

        super.render(g, mouseX, mouseY, delta);

        // Toast
        if (toastText != null && System.currentTimeMillis() < toastUntilMs) {
            long remaining = toastUntilMs - System.currentTimeMillis();
            int alpha = (int) Math.min(255, remaining > 250 ? 220 : remaining * 220 / 250);
            int textColor = (alpha << 24) | (ThemeManager.color(ThemeToken.ON_OVERLAY) & 0x00FFFFFF);
            int bgColor = ThemeManager.withAlpha(ThemeManager.color(ThemeToken.OVERLAY_DIM), alpha * 160 / 255);
            int tw = this.font.width(toastText) + 16;
            int tx = (this.width - tw) / 2;
            int ty = this.height - 32;
            RenderUtil.drawRoundedRectAA(g, tx, ty, tw, 18, 4, bgColor);
            AuroraFontRenderer.drawCentered(g, this.font, Component.literal(toastText),
                    this.width / 2, ty + 5, textColor);
        }

        // Detail modal (drawn last so it overlays everything).
        if (detailProject != null || detailOpenT > 0f) {
            renderDetailModal(g, mouseX, mouseY);
        }
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        int panelH = this.height - TOP_BAR_H - 16;
        int panelY = TOP_BAR_H;
        int panelW = SIDEBAR_W - SIDEBAR_PAD * 2;
        int panelX = SIDEBAR_PAD;

        // Sidebar panel — opacity-tracked themed surface.
        RenderUtil.drawRoundedRectAA(g, panelX, panelY, panelW, panelH, 8,
                ThemeManager.surfaceColor(ThemeToken.SURFACE));
        RenderUtil.drawRoundedOutlineAA(g, panelX, panelY, panelW, panelH, 8, 1.0f, AuroraTheme.WINDOW_OUTLINE);

        // "Categories" header
        g.drawString(this.font, Component.literal("Categories"),
                panelX + 10, panelY + 10, AuroraTheme.TEXT_SECONDARY, false);

        // Clip tabs to the sidebar body so they can scroll cleanly.
        int clipTop = panelY + 26;
        int clipBot = panelY + panelH - 6;
        g.enableScissor(panelX, clipTop, panelX + panelW, clipBot);

        // Tabs
        int tabY = clipTop - (int) sidebarScroll;
        int tabX = panelX + 4;
        int tabW = panelW - 8;

        // Also render the sidebar scrollbar if the tabs overflow.
        for (int i = 0; i < CATEGORIES.length; i++) {
            CategoryTab tab = CATEGORIES[i];
            if (tab.section == Section.HEADER) {
                // Section header label (e.g. "Categories" / "Features").
                // Skip drawing if scrolled out of view.
                if (tabY + HEADER_H > clipTop && tabY < clipBot) {
                    g.drawString(this.font, Component.literal(tab.displayName),
                            tabX + 6, tabY + (HEADER_H - this.font.lineHeight) / 2 + 1,
                            AuroraTheme.TEXT_DIM, false);
                }
                tabY += HEADER_H;
                continue;
            }

            boolean isActive = (tab.slug == null && activeCategory == null)
                    || (tab.slug != null && tab.slug.equals(activeCategory));
            boolean hover = mouseX >= tabX && mouseX < tabX + tabW
                    && mouseY >= tabY && mouseY < tabY + TAB_H
                    && mouseY >= clipTop && mouseY < clipBot;

            // Eased hover / active transition.
            String key = "tab:" + i;
            float t = updateHover(key, hover || isActive);

            int fill;
            int textCol;
            int outlineCol = 0;
            if (isActive) {
                // Accent-tinted active fill, lerping in on hover.
                int accentFill = AuroraAnim.lerpArgb(
                        AuroraAnim.scaleAlpha(AuroraTheme.IOS_BLUE, 0.30f),
                        AuroraAnim.scaleAlpha(AuroraTheme.IOS_BLUE_HOVER, 0.40f), t);
                fill = accentFill;
                textCol = AuroraTheme.TEXT_PRIMARY;
                outlineCol = AuroraAnim.scaleAlpha(AuroraTheme.IOS_BLUE, 0.55f);
            } else {
                int base = 0x00000000;
                int hov = AuroraTheme.TILE_FILL;
                fill = AuroraAnim.lerpArgb(base, hov, t);
                textCol = AuroraAnim.lerpArgb(AuroraTheme.TEXT_SECONDARY, AuroraTheme.TEXT_PRIMARY, t);
            }

            if ((fill >>> 24) != 0) {
                RenderUtil.drawRoundedRectAA(g, tabX, tabY, tabW, TAB_H, 4, fill);
            }
            if (isActive) {
                RenderUtil.drawRoundedOutlineAA(g, tabX, tabY, tabW, TAB_H, 4, 1.0f, outlineCol);
            }

            String label = tab.displayName;
            int maxLabelW = tabW - 12;
            if (this.font.width(label) > maxLabelW) {
                label = truncateToWidth(label, maxLabelW);
            }
            g.drawString(this.font, label, tabX + 6,
                    tabY + (TAB_H - this.font.lineHeight) / 2 + 1, textCol, false);

            tabY += TAB_H + TAB_GAP;
        }

        g.disableScissor();

        // Sidebar scroll indicator (only if content overflows).
        double sbMax = sidebarMaxScroll();
        if (sbMax > 0) {
            int trackTop = clipTop;
            int trackH = clipBot - clipTop;
            double thumbH = Math.max(24, trackH * (trackH / (trackH + sbMax)));
            double ratio = sidebarScroll / sbMax;
            int thumbY = trackTop + (int) ((trackH - thumbH) * ratio);
            int trackX = panelX + panelW - 5;
            boolean sHover = mouseX >= trackX - 2 && mouseX <= trackX + 5
                    && mouseY >= thumbY && mouseY <= thumbY + thumbH;
            int col = (sHover || sidebarDragging) ? AuroraAnim.scaleAlpha(AuroraTheme.TEXT_PRIMARY, 0.55f)
                    : AuroraAnim.scaleAlpha(AuroraTheme.TEXT_PRIMARY, 0.30f);
            RenderUtil.drawRoundedRectAA(g, trackX, thumbY, 3, (int) thumbH, 2, col);
        }
    }

    private void renderCard(GuiGraphics g, ModrinthProject p, int x, int y, int mouseX, int mouseY) {
        boolean cardHover = mouseX >= x && mouseX < x + CARD_W
                && mouseY >= y && mouseY < y + CARD_H;
        float t = updateHover("card:" + p.projectId, cardHover);

        // Cheap two-layer card shadow (2 fills). The full
        // AuroraShapes.dropShadow ramp emits ~100 fills/card which dominates
        // frame time at 40 results; two soft offsets read nearly identically
        // at card scale and keep per-card draw count low.
        renderCardShadow(g, x, y, CARD_W, CARD_H);

        // Card body — theme surface, lifted slightly on hover.
        int fill = AuroraAnim.lerpArgb(AuroraTheme.IOS_SECONDARY_BG, AuroraTheme.IOS_TERTIARY_BG, t);
        RenderUtil.drawRoundedRectAA(g, x, y, CARD_W, CARD_H, 8, fill);

        // Top-edge sheen — material cue.
        AuroraShapes.topSheen(g, x, y, CARD_W, CARD_H, 8, (int) (40 + 40 * t));

        // Outline brightens with hover.
        int outline = AuroraAnim.lerpArgb(AuroraTheme.TILE_OUTLINE_OFF, AuroraTheme.TILE_OUTLINE_ON, t);
        RenderUtil.drawRoundedOutlineAA(g, x, y, CARD_W, CARD_H, 8, 1.0f, outline);

        // Thumbnail
        int tx = x + THUMB_PAD;
        int ty = y + (CARD_H - THUMB_SIZE) / 2;
        // Framed thumbnail: dark inset behind the icon so non-square icons
        // read cleanly, plus a subtle border.
        RenderUtil.drawRoundedRectAA(g, tx - 1, ty - 1, THUMB_SIZE + 2, THUMB_SIZE + 2, 7,
                ThemeManager.color(ThemeToken.SURFACE_INSET));
        Identifier icon = PackIconCache.getIfLoaded(p.iconUrl);
        if (icon != null) {
            PackIconCache.blitIcon(g, icon, tx, ty, THUMB_SIZE);
        } else {
            int bg = letterPlaceholderColor(p.title);
            RenderUtil.drawRoundedRectAA(g, tx, ty, THUMB_SIZE, THUMB_SIZE, 6, bg);
            String letter = (p.title == null || p.title.isEmpty()) ? "?"
                    : String.valueOf(Character.toUpperCase(p.title.charAt(0)));
            AuroraFontRenderer.drawCentered(g, this.font, Component.literal(letter),
                    tx + THUMB_SIZE / 2, ty + (THUMB_SIZE - this.font.lineHeight) / 2 + 1,
                    0xFFFFFFFF);
            PackIconCache.requestAsync(p.iconUrl);
        }
        // Thumbnail frame outline.
        RenderUtil.drawRoundedOutlineAA(g, tx, ty, THUMB_SIZE, THUMB_SIZE, 6, 1.0f,
                AuroraAnim.scaleAlpha(AuroraTheme.TEXT_PRIMARY, 0.10f + 0.10f * t));

        // Text
        int textX = tx + THUMB_SIZE + THUMB_PAD;
        int textW = CARD_W - (textX - x) - THUMB_PAD;
        g.drawString(this.font, fitText("t:" + p.projectId, p.title, textW),
                textX, y + 12, AuroraTheme.TEXT_PRIMARY, false);
        g.drawString(this.font, "by " + fitText("a:" + p.projectId, p.author, textW),
                textX, y + 12 + this.font.lineHeight + 2, AuroraTheme.TEXT_SECONDARY, false);
        g.drawString(this.font, formatCount(p.downloads) + " downloads",
                textX, y + 12 + (this.font.lineHeight + 2) * 2, AuroraTheme.TEXT_DIM, false);
        g.drawString(this.font, fitText("d:" + p.projectId, p.description, textW),
                textX, y + 12 + (this.font.lineHeight + 2) * 3, AuroraTheme.TEXT_SECONDARY, false);

        // Install button
        CardState st = cardStates.computeIfAbsent(p.projectId, k -> new CardState());
        int btnW = 80, btnH = 18;
        int btnX = x + CARD_W - btnW - THUMB_PAD;
        int btnY = y + CARD_H - btnH - 8;
        boolean hover = mouseX >= btnX && mouseX < btnX + btnW
                && mouseY >= btnY && mouseY < btnY + btnH;
        float bt = updateHover("btn:" + p.projectId, hover && st.phase != CardState.DOWNLOADING);

        switch (st.phase) {
            case CardState.IDLE -> {
                // Install affordance = success semantics — the semantic token
                // replaces the stale (unprojected) legacy IOS_GREEN statics.
                int fillBase = AuroraAnim.scaleAlpha(AuroraTheme.SEMANTIC_SUCCESS, 0.22f);
                int fillHover = AuroraAnim.scaleAlpha(AuroraTheme.SEMANTIC_SUCCESS, 0.40f);
                RenderUtil.drawRoundedRectAA(g, btnX, btnY, btnW, btnH, 4, AuroraAnim.lerpArgb(fillBase, fillHover, bt));
                RenderUtil.drawRoundedOutlineAA(g, btnX, btnY, btnW, btnH, 4, 1.0f,
                        AuroraAnim.scaleAlpha(AuroraTheme.SEMANTIC_SUCCESS, 0.45f));
                AuroraFontRenderer.drawCentered(g, this.font, Component.literal("Install"),
                        btnX + btnW / 2, btnY + (btnH - this.font.lineHeight) / 2 + 1, AuroraTheme.TEXT_PRIMARY);
            }
            case CardState.RESOLVING, CardState.DOWNLOADING -> {
                RenderUtil.drawRoundedRectAA(g, btnX, btnY, btnW, btnH, 4, AuroraTheme.TILE_FILL);
                String label = st.phase == CardState.RESOLVING ? "Resolving…" : "Downloading…";
                AuroraFontRenderer.drawCentered(g, this.font, Component.literal(label),
                        btnX + btnW / 2, btnY + (btnH - this.font.lineHeight) / 2 + 1, AuroraTheme.TEXT_PRIMARY);
            }
            case CardState.DONE -> {
                RenderUtil.drawRoundedRectAA(g, btnX, btnY, btnW, btnH, 4,
                        AuroraAnim.scaleAlpha(AuroraTheme.SEMANTIC_SUCCESS, 0.30f));
                AuroraFontRenderer.drawCentered(g, this.font, Component.literal("Installed ✓"),
                        btnX + btnW / 2, btnY + (btnH - this.font.lineHeight) / 2 + 1,
                        AuroraAnim.lerpArgb(AuroraTheme.SEMANTIC_SUCCESS, 0xFFFFFFFF, 0.65f));
            }
            case CardState.FAILED -> {
                int fillBase = AuroraAnim.scaleAlpha(AuroraTheme.SEMANTIC_ERROR, 0.40f);
                int fillHover = AuroraAnim.scaleAlpha(AuroraTheme.SEMANTIC_ERROR, 0.60f);
                RenderUtil.drawRoundedRectAA(g, btnX, btnY, btnW, btnH, 4, AuroraAnim.lerpArgb(fillBase, fillHover, bt));
                AuroraFontRenderer.drawCentered(g, this.font, Component.literal("Retry"),
                        btnX + btnW / 2, btnY + (btnH - this.font.lineHeight) / 2 + 1, AuroraTheme.TEXT_PRIMARY);
            }
        }
    }

    private void renderDetailModal(GuiGraphics g, int mouseX, int mouseY) {
        float openT = AuroraAnim.easeOutCubic(detailOpenT);

        // Dim backdrop fades in with the modal.
        int dimAlpha = (int) (180 * openT);
        g.fill(0, 0, this.width, this.height, (dimAlpha << 24));

        int modalW = Math.min(460, this.width - 40);
        int modalH = Math.min(360, this.height - 60);
        int modalX = (this.width - modalW) / 2;
        // Slight upward translate during open for a sheet-like feel.
        int modalY = (this.height - modalH) / 2 + (int) ((1f - openT) * 16);

        g.enableScissor(0, 0, this.width, this.height);
        // Shadow + body.
        AuroraShapes.dropShadow(g, modalX, modalY, modalW, modalH, AuroraTheme.RADIUS_LARGE);
        RenderUtil.drawRoundedRectAA(g, modalX, modalY, modalW, modalH,
                AuroraTheme.RADIUS_LARGE, ThemeManager.surfaceColor(ThemeToken.SURFACE));
        RenderUtil.drawRoundedOutlineAA(g, modalX, modalY, modalW, modalH,
                AuroraTheme.RADIUS_LARGE, 1.0f, AuroraTheme.WINDOW_OUTLINE);
        AuroraShapes.topSheen(g, modalX, modalY, modalW, modalH, AuroraTheme.RADIUS_LARGE, 80);

        if (detailProject != null) {
            renderDetailContent(g, detailProject, modalX, modalY, modalW, modalH, mouseX, mouseY);
        }
        g.disableScissor();
    }

    private void renderDetailContent(GuiGraphics g, ModrinthProject p, int mx, int my, int mw, int mh, int mouseX, int mouseY) {
        int pad = 18;
        int thumbSize = 88;

        // Large preview thumbnail (inset surface token — the old IOS_GRAY_6
        // static was never theme-projected).
        RenderUtil.drawRoundedRectAA(g, mx + pad, my + pad, thumbSize, thumbSize, 10,
                ThemeManager.color(ThemeToken.SURFACE_INSET));
        Identifier icon = PackIconCache.getIfLoaded(p.iconUrl);
        if (icon != null) {
            PackIconCache.blitIcon(g, icon, mx + pad, my + pad, thumbSize);
        } else {
            int bg = letterPlaceholderColor(p.title);
            RenderUtil.drawRoundedRectAA(g, mx + pad, my + pad, thumbSize, thumbSize, 10, bg);
            String letter = (p.title == null || p.title.isEmpty()) ? "?"
                    : String.valueOf(Character.toUpperCase(p.title.charAt(0)));
            AuroraFontRenderer.drawCentered(g, this.font, Component.literal(letter),
                    mx + pad + thumbSize / 2, my + pad + (thumbSize - this.font.lineHeight) / 2 + 1,
                    0xFFFFFFFF);
        }
        if (p.iconUrl != null) PackIconCache.requestAsync(p.iconUrl);

        int textX = mx + pad + thumbSize + 14;
        int textW = mw - (textX - mx) - pad;
        g.drawString(this.font, truncateToWidth(p.title, textW), textX, my + pad + 2, AuroraTheme.TEXT_PRIMARY, false);
        g.drawString(this.font, "by " + truncateToWidth(p.author, textW),
                textX, my + pad + 4 + this.font.lineHeight, AuroraTheme.TEXT_SECONDARY, false);
        g.drawString(this.font, formatCount(p.downloads) + " downloads  ·  "
                        + formatCount(p.followers) + " followers",
                textX, my + pad + 6 + this.font.lineHeight * 2, AuroraTheme.TEXT_DIM, false);

        // Description block (wrapped).
        int descY = my + pad + thumbSize + 14;
        int descW = mw - pad * 2;
        int descBottom = my + mh - 60;
        RenderUtil.drawWordWrapMaxLines(this.font, g, p.description,
                mx + pad, descY, descW, (descBottom - descY) / (this.font.lineHeight + 2),
                AuroraTheme.TEXT_SECONDARY);

        // Install button row.
        CardState st = cardStates.computeIfAbsent(p.projectId, k -> new CardState());
        int btnW = 130, btnH = 24;
        int btnX = mx + pad;
        int btnY = my + mh - pad - btnH;
        boolean hover = mouseX >= btnX && mouseX < btnX + btnW
                && mouseY >= btnY && mouseY < btnY + btnH;
        float bt = updateHover("detailbtn:" + p.projectId, hover && st.phase != CardState.DOWNLOADING);

        switch (st.phase) {
            case CardState.IDLE -> {
                int fillBase = AuroraTheme.IOS_BLUE_GRAD_BOT;
                int fillHover = AuroraTheme.IOS_BLUE_GRAD_TOP_HOVER;
                RenderUtil.drawRoundedRectAA(g, btnX, btnY, btnW, btnH, 6, AuroraAnim.lerpArgb(fillBase, fillHover, bt));
                RenderUtil.drawRoundedOutlineAA(g, btnX, btnY, btnW, btnH, 6, 1.0f,
                        AuroraAnim.scaleAlpha(0xFFFFFFFF, 0.25f));
                AuroraFontRenderer.drawCentered(g, this.font, Component.literal("Install"),
                        btnX + btnW / 2, btnY + (btnH - this.font.lineHeight) / 2 + 1, 0xFFFFFFFF);
            }
            case CardState.RESOLVING, CardState.DOWNLOADING -> {
                RenderUtil.drawRoundedRectAA(g, btnX, btnY, btnW, btnH, 6, AuroraTheme.TILE_FILL);
                renderSpinner(g, btnX + 14, btnY + btnH / 2, detailSpinnerAngle);
                String label = st.phase == CardState.RESOLVING ? "Resolving…" : "Downloading…";
                g.drawString(this.font, Component.literal(label),
                        btnX + 30, btnY + (btnH - this.font.lineHeight) / 2 + 1, AuroraTheme.TEXT_PRIMARY, false);
            }
            case CardState.DONE -> {
                RenderUtil.drawRoundedRectAA(g, btnX, btnY, btnW, btnH, 6,
                        AuroraAnim.scaleAlpha(AuroraTheme.SEMANTIC_SUCCESS, 0.45f));
                AuroraFontRenderer.drawCentered(g, this.font, Component.literal("Installed ✓"),
                        btnX + btnW / 2, btnY + (btnH - this.font.lineHeight) / 2 + 1,
                        AuroraAnim.lerpArgb(AuroraTheme.SEMANTIC_SUCCESS, 0xFFFFFFFF, 0.65f));
            }
            case CardState.FAILED -> {
                RenderUtil.drawRoundedRectAA(g, btnX, btnY, btnW, btnH, 6,
                        AuroraAnim.lerpArgb(AuroraAnim.scaleAlpha(AuroraTheme.SEMANTIC_ERROR, 0.55f),
                                AuroraAnim.scaleAlpha(AuroraTheme.SEMANTIC_ERROR, 0.75f), bt));
                AuroraFontRenderer.drawCentered(g, this.font, Component.literal("Retry Install"),
                        btnX + btnW / 2, btnY + (btnH - this.font.lineHeight) / 2 + 1, 0xFFFFFFFF);
            }
        }

        // Close button (top-right of modal).
        int cbW = 60, cbH = 22;
        int cbX = mx + mw - cbW - pad;
        int cbY = my + pad;
        boolean cbHover = mouseX >= cbX && mouseX < cbX + cbW
                && mouseY >= cbY && mouseY < cbY + cbH;
        float cbt = updateHover("detailclose", cbHover);
        RenderUtil.drawRoundedRectAA(g, cbX, cbY, cbW, cbH, 6,
                AuroraAnim.lerpArgb(AuroraTheme.TILE_FILL, AuroraTheme.TILE_FILL_HOVER, cbt));
        RenderUtil.drawRoundedOutlineAA(g, cbX, cbY, cbW, cbH, 6, 1.0f, AuroraTheme.TILE_OUTLINE_OFF);
        AuroraFontRenderer.drawCentered(g, this.font, Component.literal("Close"),
                cbX + cbW / 2, cbY + (cbH - this.font.lineHeight) / 2 + 1, AuroraTheme.TEXT_PRIMARY);
    }

    /**
     * Two-layer rounded drop shadow — a tight darker layer immediately under
     * the card and a softer wider layer one pixel further down. Visually
     * equivalent to a short ambient-occlusion halo at card scale but only
     * costs two rounded fills instead of {@link AuroraShapes#dropShadow}'s
     * six layers (~100 fills). Used per-card where draw-call count matters.
     */
    private void renderCardShadow(GuiGraphics g, int x, int y, int w, int h) {
        RenderUtil.drawRoundedRectAA(g, x + 1, y + 2, w, h, 8, 0x55000000);
        RenderUtil.drawRoundedRectAA(g, x, y + 4, w, h, 9, 0x22000000);
    }

    /** Small animated circular spinner, used by the loading + install-in-progress states. */
    private void renderSpinner(GuiGraphics g, int cx, int cy, float angleDeg) {
        float r = 6f;
        int segments = 8;
        for (int i = 0; i < segments; i++) {
            float a = (float) Math.toRadians(angleDeg + i * (360f / segments));
            int x = cx + (int) (Math.cos(a) * r);
            int y = cy + (int) (Math.sin(a) * r);
            // Fade tail segments to transparent for a classic spinner look.
            float fade = 1f - (i / (float) segments);
            int col = AuroraAnim.scaleAlpha(AuroraTheme.TEXT_PRIMARY, fade * 0.9f);
            g.fill(x, y, x + 2, y + 2, col);
        }
    }

    // ------------------------------------------------------------------
    //  Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent ev, boolean dbl) {
        double mouseX = ev.x();
        double mouseY = ev.y();
        if (ev.button() != 0) return super.mouseClicked(ev, dbl);

        // Detail modal gets first crack at clicks when open.
        if (detailOpenT > 0.5f && detailProject != null) {
            return handleDetailClick(mouseX, mouseY);
        }

        // Sidebar tab clicks (respecting the clip region + scroll offset).
        int panelY = TOP_BAR_H;
        int panelH = this.height - TOP_BAR_H - 16;
        int clipTop = panelY + 26;
        int clipBot = panelY + panelH - 6;
        int tabX = SIDEBAR_PAD + 4;
        int tabW = (SIDEBAR_W - SIDEBAR_PAD * 2) - 8;
        int tabY = clipTop - (int) sidebarScroll;
        for (int i = 0; i < CATEGORIES.length; i++) {
            CategoryTab tab = CATEGORIES[i];
            int rowH = tab.section == Section.HEADER ? HEADER_H : TAB_H;
            if (tab.section != Section.HEADER
                    && mouseX >= tabX && mouseX < tabX + tabW
                    && mouseY >= tabY && mouseY < tabY + rowH
                    && mouseY >= clipTop && mouseY < clipBot) {
                activeCategory = tab.slug;
                pendingQuery = searchField != null ? searchField.getValue() : "";
                submitSearch(pendingQuery, activeCategory);
                return true;
            }
            tabY += tab.section == Section.HEADER ? HEADER_H : TAB_H + TAB_GAP;
        }

        // Sidebar scrollbar drag start.
        double sbMax = sidebarMaxScroll();
        if (sbMax > 0) {
            int trackTop = clipTop;
            int trackH = clipBot - clipTop;
            double thumbH = Math.max(24, trackH * (trackH / (trackH + sbMax)));
            double ratio = sidebarScroll / sbMax;
            int thumbY = trackTop + (int) ((trackH - thumbH) * ratio);
            int trackX = SIDEBAR_PAD + (SIDEBAR_W - SIDEBAR_PAD * 2) - 5;
            if (mouseX >= trackX - 2 && mouseX <= trackX + 5
                    && mouseY >= thumbY - 4 && mouseY <= thumbY + thumbH + 4) {
                sidebarDragging = true;
                return true;
            }
        }

        // Grid scrollbar drag start.
        double ms = maxScroll();
        if (ms > 0) {
            int cols = columns();
            int gridLeft = gridLeft();
            int gridRight = gridLeft + cols * (CARD_W + CARD_GAP) - CARD_GAP;
            int trackX = gridRight + 8;
            int trackTop = LIST_TOP;
            int trackH = (this.height - LIST_BOTTOM_PAD) - LIST_TOP;
            double thumbH = Math.max(30, trackH * ((double) (this.height - LIST_TOP - LIST_BOTTOM_PAD)
                    / (trackH + ms)));
            double ratio = scrollY / ms;
            int thumbY = trackTop + (int) ((trackH - thumbH) * ratio);
            if (mouseX >= trackX - 4 && mouseX <= trackX + 8
                    && mouseY >= thumbY - 4 && mouseY <= thumbY + thumbH + 4) {
                scrollbarDragging = true;
                return true;
            }
        }

        // Search field
        if (searchField != null && searchField.mouseClicked(ev, dbl)) return true;

        // Card clicks: install button first, then card body (opens detail).
        List<ModrinthProject> list = results;
        int cols = columns();
        int gridLeft = gridLeft();
        for (int i = 0; i < list.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = gridLeft + col * (CARD_W + CARD_GAP);
            int yBase = LIST_TOP + row * (CARD_H + CARD_GAP);
            int y = yBase - (int) scrollY;
            int btnW = 80, btnH = 18;
            int btnX = x + CARD_W - btnW - THUMB_PAD;
            int btnY = y + CARD_H - btnH - 8;
            if (mouseX >= btnX && mouseX < btnX + btnW
                    && mouseY >= btnY && mouseY < btnY + btnH) {
                handleInstallClick(list.get(i));
                return true;
            }
            if (mouseX >= x && mouseX < x + CARD_W
                    && mouseY >= y && mouseY < y + CARD_H) {
                openDetail(list.get(i));
                return true;
            }
        }
        return super.mouseClicked(ev, dbl);
    }

    /** Handles clicks inside the open pack-detail modal. */
    private boolean handleDetailClick(double mouseX, double mouseY) {
        int modalW = Math.min(460, this.width - 40);
        int modalH = Math.min(360, this.height - 60);
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2;

        // Clicks outside the modal close it.
        if (mouseX < modalX || mouseX >= modalX + modalW
                || mouseY < modalY || mouseY >= modalY + modalH) {
            closeDetail();
            return true;
        }

        int pad = 18;
        // Close button.
        int cbW = 60, cbH = 22;
        int cbX = modalX + modalW - cbW - pad;
        int cbY = modalY + pad;
        if (mouseX >= cbX && mouseX < cbX + cbW && mouseY >= cbY && mouseY < cbY + cbH) {
            closeDetail();
            return true;
        }

        // Install button.
        int btnW = 130, btnH = 24;
        int btnX = modalX + pad;
        int btnY = modalY + modalH - pad - btnH;
        if (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
            if (detailProject != null) handleInstallClick(detailProject);
            return true;
        }
        return true; // consume clicks inside the modal
    }

    private void openDetail(ModrinthProject p) {
        detailProject = p;
        detailOpenTarget = true;
    }

    private void closeDetail() {
        detailOpenTarget = false;
    }

    private double maxScroll() {
        List<ModrinthProject> list = results;
        int cols = columns();
        int rows = (list.size() + cols - 1) / cols;
        int totalH = rows * (CARD_H + CARD_GAP);
        return Math.max(0, totalH - (this.height - LIST_TOP - LIST_BOTTOM_PAD));
    }

    private double sidebarMaxScroll() {
        // Compute the total content height of the sidebar tabs + headers.
        int contentH = 0;
        for (CategoryTab tab : CATEGORIES) {
            contentH += tab.section == Section.HEADER ? HEADER_H : TAB_H + TAB_GAP;
        }
        int panelH = this.height - TOP_BAR_H - 16;
        int visibleH = panelH - 26 - 6; // header area + bottom pad
        return Math.max(0, contentH - visibleH);
    }

    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY, int gridLeft, int cols, int listBottom) {
        double ms = maxScroll();
        if (ms <= 0) return;

        int gridRight = gridLeft + cols * (CARD_W + CARD_GAP) - CARD_GAP;
        int trackX = gridRight + 8;
        int trackW = 4;
        int trackTop = LIST_TOP;
        int trackH = listBottom - LIST_TOP;

        double ratio = scrollY / ms;
        double thumbH = Math.max(30, trackH * ((double) (this.height - LIST_TOP - LIST_BOTTOM_PAD)
                / (trackH + ms)));
        int thumbY = trackTop + (int) ((trackH - thumbH) * ratio);

        boolean hover = mouseX >= trackX - 2 && mouseX <= trackX + trackW + 2
                && mouseY >= thumbY && mouseY <= thumbY + thumbH;
        int thumbCol = (hover || scrollbarDragging)
                ? AuroraAnim.scaleAlpha(AuroraTheme.TEXT_PRIMARY, 0.55f)
                : AuroraAnim.scaleAlpha(AuroraTheme.TEXT_PRIMARY, 0.30f);
        RenderUtil.drawRoundedRectAA(g, trackX, thumbY, trackW, (int) thumbH, 2, thumbCol);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        // If the detail modal is open, let it consume scroll without bubbling.
        if (detailOpenT > 0.5f) return true;

        // Route wheel over the sidebar to the sidebar scroll, else to the grid.
        if (mouseX < SIDEBAR_W) {
            double sbMax = sidebarMaxScroll();
            sidebarScrollTarget -= vertical * 28;
            if (sidebarScrollTarget < 0) sidebarScrollTarget = 0;
            if (sidebarScrollTarget > sbMax) sidebarScrollTarget = sbMax;
        } else {
            double ms = maxScroll();
            scrollTargetY -= vertical * 40;
            if (scrollTargetY < 0) scrollTargetY = 0;
            if (scrollTargetY > ms) scrollTargetY = ms;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent ev, double dx, double dy) {
        if (scrollbarDragging && ev.button() == 0) {
            double mouseY = ev.y();
            double ms = maxScroll();
            if (ms <= 0) return true;
            int trackTop = LIST_TOP;
            int trackH = (this.height - LIST_BOTTOM_PAD) - LIST_TOP;
            double thumbH = Math.max(30, trackH * ((double) (this.height - LIST_TOP - LIST_BOTTOM_PAD)
                    / (trackH + ms)));
            double usable = trackH - thumbH;
            if (usable <= 0) return true;
            double relY = mouseY - trackTop - thumbH / 2.0;
            double v = (relY / usable) * ms;
            if (v < 0) v = 0;
            if (v > ms) v = ms;
            scrollY = scrollTargetY = v;
            return true;
        }
        if (sidebarDragging && ev.button() == 0) {
            double mouseY = ev.y();
            double sbMax = sidebarMaxScroll();
            if (sbMax <= 0) return true;
            int panelY = TOP_BAR_H;
            int panelH = this.height - TOP_BAR_H - 16;
            int trackTop = panelY + 26;
            int trackH = panelH - 26 - 6;
            double thumbH = Math.max(24, trackH * (trackH / (trackH + sbMax)));
            double usable = trackH - thumbH;
            if (usable <= 0) return true;
            double relY = mouseY - trackTop - thumbH / 2.0;
            double v = (relY / usable) * sbMax;
            if (v < 0) v = 0;
            if (v > sbMax) v = sbMax;
            sidebarScroll = sidebarScrollTarget = v;
            return true;
        }
        return super.mouseDragged(ev, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent ev) {
        if (ev.button() == 0) {
            scrollbarDragging = false;
            sidebarDragging = false;
        }
        return super.mouseReleased(ev);
    }

    // ---- Keyboard input delegation (fixes search bar not working) ----

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent _kev) {
        // ESC closes the detail modal first, then the screen.
        if (_kev.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && detailOpenT > 0f) {
            closeDetail();
            return true;
        }
        if (searchField != null && searchField.isFocused()) {
            int key = _kev.key();
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                searchField.setFocused(false);
                return true;
            }
            if (searchField.keyPressed(_kev)) return true;
        }
        return super.keyPressed(_kev);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent _ev) {
        if (searchField != null && searchField.isFocused()) {
            if (searchField.charTyped(_ev)) return true;
        }
        return super.charTyped(_ev);
    }

    // ------------------------------------------------------------------
    //  Install flow
    // ------------------------------------------------------------------

    private void handleInstallClick(ModrinthProject p) {
        CardState st = cardStates.computeIfAbsent(p.projectId, k -> new CardState());
        if (st.phase == CardState.RESOLVING || st.phase == CardState.DOWNLOADING) return;

        st.phase = CardState.RESOLVING;
        st.message = "";
        ModrinthApi.bestVersion(p).whenComplete((optVersion, err) -> {
            if (err != null || optVersion == null || optVersion.isEmpty()) {
                st.phase = CardState.FAILED;
                st.message = err == null ? "No compatible version" : err.toString();
                Minecraft.getInstance().execute(() ->
                        flashToast("No compatible version for this MC version"));
                return;
            }
            ModrinthVersion version = optVersion.get();
            st.phase = CardState.DOWNLOADING;
            ModrinthApi.install(version).whenComplete((status, err2) -> {
                Minecraft.getInstance().execute(() -> {
                    if (err2 != null) {
                        st.phase = CardState.FAILED;
                        flashToast("Install failed: " + err2);
                    } else if (status == ModrinthApi.InstallStatus.ALREADY_INSTALLED) {
                        st.phase = CardState.DONE;
                        flashToast("Already installed — enable it in Options → Resource Packs");
                    } else if (status == ModrinthApi.InstallStatus.FAILED) {
                        st.phase = CardState.FAILED;
                        flashToast("Download failed — check your connection");
                    } else {
                        st.phase = CardState.DONE;
                        flashToast("Installed — enable it in Options → Resource Packs");
                    }
                });
            });
        });
    }

    // ------------------------------------------------------------------
    //  Hover easing helper
    // ------------------------------------------------------------------

    /**
     * Advances an eased hover animation for {@code key} and returns the
     * current normalized value {@code [0,1]}. Tracks active/inactive
     * transitions so a hover-out plays the reverse curve smoothly.
     */
    private float updateHover(String key, boolean active) {
        boolean wasActive = hoverActive.getOrDefault(key, false);
        if (active != wasActive) {
            hoverStart.put(key, System.currentTimeMillis());
            hoverActive.put(key, active);
        }
        float cur = hoverT.getOrDefault(key, 0f);
        if (active && cur >= 1f) return 1f;
        if (!active && cur <= 0f) return 0f;

        Long start = hoverStart.get(key);
        if (start == null) return active ? 1f : 0f;
        float raw = AuroraAnim.clamp01((System.currentTimeMillis() - start) / (float) HOVER_MS);
        float t = active ? raw : (1f - raw);
        float eased = AuroraAnim.easeOutCubic(t);
        hoverT.put(key, eased);
        return eased;
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private int gridLeft() {
        int cols = columns();
        int gridW = cols * (CARD_W + CARD_GAP) - CARD_GAP;
        // Center the grid in the area to the right of the sidebar.
        int areaLeft = SIDEBAR_W + 16;
        int areaW = this.width - areaLeft - 16;
        return areaLeft + (areaW - gridW) / 2;
    }

    private int columns() {
        int areaLeft = SIDEBAR_W + 16;
        int avail = this.width - areaLeft - 32;
        int c = Math.max(1, avail / (CARD_W + CARD_GAP));
        return Math.min(c, 6);
    }

    private String truncateToWidth(String s, int maxW) {
        if (s == null) return "";
        if (this.font.width(s) <= maxW) return s;
        String ell = "…";
        while (s.length() > 1 && this.font.width(s + ell) > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + ell;
    }

    /**
     * Memoized {@link #truncateToWidth}. Card text (title / author /
     * description) is re-truncated identically every frame because the card
     * width is constant; the {@code font.width()} loop inside truncate is the
     * single most expensive per-card operation, so caching by project id +
     * field + width removes ~120 of those calls per frame for a 40-result
     * page. Cleared whenever a new result set arrives
     * (see {@link #submitSearch}).
     */
    private String fitText(String fieldKey, String text, int maxW) {
        String key = fieldKey + "@" + maxW;
        String cached = textFitCache.get(key);
        if (cached != null) return cached;
        String fitted = truncateToWidth(text, maxW);
        textFitCache.put(key, fitted);
        return fitted;
    }

    private static String formatCount(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return String.format("%.1fk", n / 1000.0);
        return String.format("%.1fM", n / 1_000_000.0);
    }

    private static int letterPlaceholderColor(String title) {
        int h = (title == null ? 0 : title.hashCode());
        int hue = Math.floorMod(h, 360);
        return hsbToRgb(hue / 360f, 0.35f, 0.30f);
    }

    private static int hsbToRgb(float h, float s, float b) {
        float f = h * 6f;
        int i = (int) Math.floor(f);
        float f1 = f - i;
        float p = b * (1 - s);
        float q = b * (1 - s * f1);
        float t = b * (1 - s * (1 - f1));
        float r, g, bl;
        switch (i % 6) {
            case 0 -> { r = b; g = t; bl = p; }
            case 1 -> { r = q; g = b; bl = p; }
            case 2 -> { r = p; g = b; bl = t; }
            case 3 -> { r = p; g = q; bl = b; }
            case 4 -> { r = t; g = p; bl = b; }
            default -> { r = b; g = p; bl = q; }
        }
        return 0xFF000000
                | ((int) (r * 255) << 16)
                | ((int) (g * 255) << 8)
                | (int) (bl * 255);
    }

    private void flashToast(String text) {
        toastText = text;
        toastUntilMs = System.currentTimeMillis() + 3000L;
    }

    @Override
    public void onClose() {
        // NOTE: we intentionally do NOT clear PackIconCache here. Thumbnails
        // are cached by URL for the whole session and bounded by an LRU cap
        // inside the cache, so re-opening the browser is instant instead of
        // re-downloading every icon. Earlier versions called clear() here,
        // which made every re-open pay the full network cost again.
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}