package com.aurora.client.screen;
import com.aurora.client.ui.util.AuroraFontRenderer;

import com.aurora.client.modrinth.ModrinthApi;
import com.aurora.client.modrinth.ModrinthProject;
import com.aurora.client.modrinth.ModrinthVersion;
import com.aurora.client.modrinth.PackIconCache;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.Button;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.component.Toast;
import com.aurora.client.ui.component.Widget;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.AuroraTheme;
import com.aurora.client.util.SmoothScroll;
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
 *   <li>Glass material — the sidebar and detail modal render as DEPRESSED
 *       glass containers, the selected category tab as a RAISED glass tile
 *       (its renderPanel call captures its own backdrop slice — per-element
 *       UV), and the install/close buttons are the shared glass {@link
 *       Button} painter, the same pixels {@link ButtonWidget} wraps for the
 *       Done button. Cards are deliberately FLAT (audit R9, 2026-09-08):
 *       their hover-lerp tint is fully opaque, so a glass card's blur was
 *       completely occluded — every card paid capture→blur→readback for
 *       pixels identical to the flat fill, and a full grid of them plus
 *       their install buttons could exhaust the 24-panel output pool
 *       (audit B1/B2). Thumbnails, the search field and cards stay opaque
 *       per the mod-wide glass conventions; every glass element falls back
 *       to its flat fill when the renderer declines (no world, screenshot
 *       in flight, failure).</li>
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
    // (removed SCROLL_EASE — scroll easing is delta-time based in render(),
    //  via the shared SmoothScroll advanced at the top of render().)

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

        /**
         * Lazily-created shared install buttons, indexed by the phase they
         * render — card-sized (80×18) and modal-sized (130×24) variants are
         * separate instances because both surfaces can show the same pack at
         * once. Held here (not a screen-level map) so they are pruned
         * together with the state when the result set changes.
         */
        final Button[] cardPhaseButtons = new Button[5];
        final Button[] modalPhaseButtons = new Button[5];
    }

    // ---- Smooth scroll (target-based lerp, shared SmoothScroll) ----
    /**
     * Grid scroll — the screen's exact pre-R2 feel: τ = 80 ms, snap 0.25,
     * wheel step 40, center-on-cursor thumb (min 30) with no easing while
     * dragging. The shared component's 80 ms dt clamp replaces this
     * screen's old 64 ms (decided: only observable below ~15 fps) and its
     * nanoTime clock replaces currentTimeMillis (established unobservable).
     */
    private final SmoothScroll gridScroll = new SmoothScroll(80.0);
    /** Sidebar scroll — same glide; wheel step 28, thumb min 24. */
    private final SmoothScroll sidebarScroll = new SmoothScroll(80.0);

    // ---- Eased hover state (keyed by stable index: tab slug / project id / "card"+i) ----
    // One state object per key instead of three parallel maps. Deliberately
    // NOT util/HoverAnim: these hovers EASE IN from zero on first hover,
    // while HoverAnim (locked at 0 at rest) settles instantly on its first
    // target=true frame — adopting it would visibly change every card's
    // first hover-in. Same easing curve (easeOutCubic) as HoverAnim's
    // EASE_OUT_CUBIC.
    private final Map<String, HoverEase> hovers = new HashMap<>();

    private static final class HoverEase {
        float t;
        Long start;
        boolean active;
    }

    // ---- Toast feedback ----
    private final Toast toast = new Toast();

    // ---- Pack detail modal ----
    private ModrinthProject detailProject = null;
    private float detailOpenT = 0f;       // 0..1 open animation
    private boolean detailOpenTarget = false;
    /** Modal Close button — shared glass Button painter, laid out per frame. */
    private Button detailCloseButton;

    /**
     * Whether the sidebar's depressed glass panel engaged this frame. Set by
     * the glass pass at the top of {@link #render} (which runs BEFORE the
     * overlay dim, per the layering contract) and consulted by
     * {@link #renderSidebar} to suppress the flat panel fill + outline.
     */
    private boolean sidebarGlass = false;

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
                Component.literal("Done"), this::onClose).glassBackground(true));

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
            gridScroll.set(0);
            return;
        }

        loading = true;
        loadError = null;
        gridScroll.set(0);
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
        // Frame-rate-independent scroll easing (the shared SmoothScroll —
        // same tau = 80 ms glide). Advance toward the target every rendered
        // frame using the real elapsed wall-clock dt, so wheel scrolls glide
        // smoothly at any refresh rate (60/120/240 Hz) instead of stepping
        // at the 20 Hz tick rate. Eased in render(), not tick(), for exactly
        // that reason. The component also re-clamps each target against the
        // current maxScroll every frame (window resizes shrinking the grid).
        gridScroll.advance(maxScroll());
        sidebarScroll.advance(sidebarMaxScroll());

        // ---- Sidebar glass panel — UNDER the dim, per the layering contract ----
        // The sidebar is a main container: DEPRESSED glass with the ordinary
        // SURFACE tint (whose alpha already carries the theme's Background
        // Opacity — the single application point). Drawn before the overlay
        // dim so the dim veils the panel and its surroundings equally;
        // renderSidebar then suppresses the flat fill + outline when this
        // engaged. With no live world the renderer declines and the flat
        // panel returns, exactly as before glass.
        int panelH = this.height - TOP_BAR_H - 16;
        int panelY = TOP_BAR_H;
        int panelW = SIDEBAR_W - SIDEBAR_PAD * 2;
        int panelX = SIDEBAR_PAD;
        sidebarGlass = liveWorldBackdrop() && BlurPanelRenderer.renderPanel(
                g, panelX, panelY, panelW, panelH, AuroraTheme.RADIUS_LARGE,
                BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX, BlurPanelRenderer.Lighting.depressed());
        if (sidebarGlass) {
            RenderUtil.drawRoundedRectAA(g, panelX, panelY, panelW, panelH, AuroraTheme.RADIUS_LARGE,
                    ThemeManager.surfaceColor(ThemeToken.SURFACE));
            BlurPanelRenderer.drawRimFinish(g, panelX, panelY, panelW, panelH, AuroraTheme.RADIUS_LARGE);
        }

        // Themed backdrop — OVERLAY_DIM RGB at this screen's original 0x55
        // strength, so mode/accent derivation reaches even the dim layer.
        g.fill(0, 0, this.width, this.height,
                ThemeManager.withAlpha(ThemeManager.color(ThemeToken.OVERLAY_DIM), 0x55));

        // ---- Sidebar ----
        renderSidebar(g, mouseX, mouseY);

        // Result count subtitle — BELOW the search bar (which occupies
        // y 12..34), not inside it. The previous screen title (drawn at
        // y 16) and count (y 28) both sat inside the search field's
        // rectangle, stacking over its placeholder and typed text. The
        // redundant title is dropped — the search bar + category sidebar
        // already establish the context.
        int count = results.size();
        if (count > 0) {
            String countText = count + " pack" + (count == 1 ? "" : "s");
            g.drawString(this.font, Component.literal(countText),
                    SIDEBAR_W + 16, 38, AuroraTheme.TEXT_SECONDARY, false);
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
            double topRow0 = LIST_TOP - gridScroll.current();
            int firstRow = Math.max(0, (int) Math.floor((LIST_TOP - CARD_H - topRow0) / rowH));
            int lastRow = (int) Math.floor((listBottom - topRow0) / rowH);
            lastRow = Math.min(lastRow, (list.size() - 1) / cols);
            for (int row = firstRow; row <= lastRow; row++) {
                for (int col = 0; col < cols; col++) {
                    int i = row * cols + col;
                    if (i >= list.size()) break;
                    int x = gridLeft + col * colW;
                    int y = LIST_TOP + row * rowH - (int) gridScroll.current();
                    renderCard(g, list.get(i), x, y, mouseX, mouseY);
                }
            }
        }

        g.disableScissor();

        // Scrollbar thumb — only when content overflows.
        renderScrollbar(g, mouseX, mouseY, gridLeft, cols, listBottom);

        super.render(g, mouseX, mouseY, delta);

        // Toast
        toast.render(g, this.font, this.width, this.height, AuroraTheme.RADIUS_SMALL);

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

        // Sidebar panel — the live glass material was already rendered above
        // the dim (see render()); when it engaged, the flat fill + outline
        // are suppressed here (the glass rim replaces the outline — no
        // double outline).
        if (!sidebarGlass) {
            RenderUtil.drawRoundedRectAA(g, panelX, panelY, panelW, panelH, AuroraTheme.RADIUS_LARGE,
                    ThemeManager.surfaceColor(ThemeToken.SURFACE));
            RenderUtil.drawRoundedOutlineAA(g, panelX, panelY, panelW, panelH, AuroraTheme.RADIUS_LARGE,
                    1.0f, AuroraTheme.WINDOW_OUTLINE);
        }

        // "Categories" header
        g.drawString(this.font, Component.literal("Categories"),
                panelX + 10, panelY + 10, AuroraTheme.TEXT_SECONDARY, false);

        // Clip tabs to the sidebar body so they can scroll cleanly.
        int clipTop = panelY + 26;
        int clipBot = panelY + panelH - 6;
        g.enableScissor(panelX, clipTop, panelX + panelW, clipBot);

        // Tabs
        int tabY = clipTop - (int) sidebarScroll.current();
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
            boolean hover = Widget.inBounds(mouseX, mouseY, tabX, tabY, tabW, TAB_H)
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

            float tabR = Math.min(TAB_H / 2f, AuroraTheme.RADIUS_SMALL);
            if (isActive) {
                // Selected/primary element → accent-STAINED glass, raised
                // (the tint is the existing accent lerp on top of the glass
                // panel; the rim replaces the flat outline). Inactive tabs —
                // including their hover state — deliberately stay flat
                // neutral: they are small transient rows inside an
                // already-glass sidebar container, not genuine surfaces.
                boolean tabGlass = liveWorldBackdrop() && BlurPanelRenderer.renderPanel(
                        g, tabX, tabY, tabW, TAB_H, tabR,
                        BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX, BlurPanelRenderer.Lighting.raised());
                RenderUtil.drawRoundedRectAA(g, tabX, tabY, tabW, TAB_H, tabR, fill);
                if (tabGlass) {
                    BlurPanelRenderer.drawRimFinish(g, tabX, tabY, tabW, TAB_H, tabR);
                } else {
                    RenderUtil.drawRoundedOutlineAA(g, tabX, tabY, tabW, TAB_H, tabR, 1.0f, outlineCol);
                }
            } else if ((fill >>> 24) != 0) {
                RenderUtil.drawRoundedRectAA(g, tabX, tabY, tabW, TAB_H, tabR, fill);
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

        // Sidebar scroll indicator (only if content overflows) — painted
        // exactly as before R2; SmoothScroll supplies geometry/drag state.
        double sbMax = sidebarMaxScroll();
        if (sbMax > 0) {
            int trackTop = clipTop;
            int trackH = clipBot - clipTop;
            double thumbH = sidebarScroll.thumbHeight(trackH, sbMax, 24);
            int thumbY = trackTop + (int) ((trackH - thumbH) * sidebarScroll.ratio(sbMax));
            int trackX = panelX + panelW - 5;
            boolean sHover = mouseX >= trackX - 2 && mouseX <= trackX + 5
                    && mouseY >= thumbY && mouseY <= thumbY + thumbH;
            int col = (sHover || sidebarScroll.isDragging()) ? AuroraAnim.scaleAlpha(AuroraTheme.TEXT_PRIMARY, 0.55f)
                    : AuroraAnim.scaleAlpha(AuroraTheme.TEXT_PRIMARY, 0.30f);
            RenderUtil.drawRoundedRectAA(g, trackX, thumbY, 3, (int) thumbH, 2, col);
        }
    }

    private void renderCard(GuiGraphics g, ModrinthProject p, int x, int y, int mouseX, int mouseY) {
        boolean cardHover = Widget.inBounds(mouseX, mouseY, x, y, CARD_W, CARD_H);
        float t = updateHover("card:" + p.projectId, cardHover);

        // Card body — deliberately FLAT (audit R9/B1/B2, 2026-09-08): the
        // hover-lerp tint below is fully opaque, so when this was a per-card
        // renderPanel the captured blur was 100% occluded — pure wasted
        // capture→blur→readback per card, and a full grid of glass cards +
        // install buttons could exceed the 24-panel output pool (glass/flat
        // flicker). The flat look is the exact fill + hover outline this
        // screen always drew on renderer decline, so nothing changes
        // visually. Glass on this screen lives on the sidebar, detail modal,
        // active category tab and the shared-painter buttons.
        int fill = AuroraAnim.lerpArgb(AuroraTheme.IOS_SECONDARY_BG, AuroraTheme.IOS_TERTIARY_BG, t);
        RenderUtil.drawRoundedRectAA(g, x, y, CARD_W, CARD_H, AuroraTheme.RADIUS, fill);
        int outline = AuroraAnim.lerpArgb(AuroraTheme.TILE_OUTLINE_OFF, AuroraTheme.TILE_OUTLINE_ON, t);
        RenderUtil.drawRoundedOutlineAA(g, x, y, CARD_W, CARD_H, AuroraTheme.RADIUS, 1.0f, outline);

        // Thumbnail
        int tx = x + THUMB_PAD;
        int ty = y + (CARD_H - THUMB_SIZE) / 2;
        // Framed thumbnail: dark inset behind the icon so non-square icons
        // read cleanly, plus a subtle border.
        RenderUtil.drawRoundedRectAA(g, tx - 1, ty - 1, THUMB_SIZE + 2, THUMB_SIZE + 2,
                AuroraTheme.RADIUS_SMALL, ThemeManager.color(ThemeToken.SURFACE_INSET));
        Identifier icon = PackIconCache.getIfLoaded(p.iconUrl);
        if (icon != null) {
            PackIconCache.blitIcon(g, icon, tx, ty, THUMB_SIZE);
        } else {
            int bg = letterPlaceholderColor(p.title);
            RenderUtil.drawRoundedRectAA(g, tx, ty, THUMB_SIZE, THUMB_SIZE, AuroraTheme.RADIUS_SMALL, bg);
            String letter = (p.title == null || p.title.isEmpty()) ? "?"
                    : String.valueOf(Character.toUpperCase(p.title.charAt(0)));
            AuroraFontRenderer.drawCentered(g, this.font, Component.literal(letter),
                    tx + THUMB_SIZE / 2, ty + (THUMB_SIZE - this.font.lineHeight) / 2 + 1,
                    0xFFFFFFFF);
            PackIconCache.requestAsync(p.iconUrl);
        }
        // Thumbnail frame outline.
        RenderUtil.drawRoundedOutlineAA(g, tx, ty, THUMB_SIZE, THUMB_SIZE, AuroraTheme.RADIUS_SMALL, 1.0f,
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

        // Install button — the shared glass Button painter (the same
        // implementation ButtonWidget wraps for the Done button), laid out
        // imperatively per frame like ProfileManagerScreen's row buttons.
        // Clicks still route through this screen's own rect hit-testing
        // (see mouseClicked), so the painter is constructed with a no-op
        // action — the ButtonWidget discipline.
        CardState st = cardStates.computeIfAbsent(p.projectId, k -> new CardState());
        int btnW = 80, btnH = 18;
        int btnX = x + CARD_W - btnW - THUMB_PAD;
        int btnY = y + CARD_H - btnH - 8;
        Button installBtn = st.cardPhaseButtons[st.phase];
        if (installBtn == null) {
            installBtn = newPhaseButton(st.phase);
            st.cardPhaseButtons[st.phase] = installBtn;
        }
        installBtn.layout(btnX, btnY, btnW, btnH);
        installBtn.render(g, btnX, btnY, btnW, btnH, mouseX, mouseY);
    }

    /**
     * Shared install-phase button factory. The shared {@link Button} has no
     * success variant, so the phases map onto its variant language: Install
     * is the primary action → accent-STAINED glass (the glass conventions
     * reserve stained for primary/selected elements; the detail modal's old
     * hand-rolled Install was already an accent gradient); Resolving…/
     * Downloading…/Installed ✓ are neutral glass; Retry is the component's
     * destructive treatment (semantic-error fill + outline + white label —
     * Button deliberately pins destructive to the flat look). The previous
     * success-green fills are not expressible through the shared painter
     * without modifying it, which this change is forbidden to do.
     */
    private static Button newPhaseButton(int phase) {
        return switch (phase) {
            case CardState.IDLE -> new Button(Component.literal("Install"), () -> {}, true)
                    .glassStyle(Button.GlassStyle.STAINED);
            case CardState.RESOLVING -> new Button(Component.literal("Resolving…"), () -> {})
                    .glassBackground(true);
            case CardState.DOWNLOADING -> new Button(Component.literal("Downloading…"), () -> {})
                    .glassBackground(true);
            case CardState.DONE -> new Button(Component.literal("Installed ✓"), () -> {})
                    .glassBackground(true);
            case CardState.FAILED -> new Button(Component.literal("Retry"), () -> {})
                    .destructive(true);
            default -> throw new IllegalArgumentException("phase " + phase);
        };
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
        // Modal body — same treatment as the sidebar: DEPRESSED glass behind
        // the existing SURFACE tint, rim finish above it. The manual drop
        // shadow + top sheen are gone — the glass system replaces them. The
        // modal's own dim backdrop has already been filled, so the glass
        // correctly captures the dimmed screen as its backdrop. On decline
        // the flat fill + outline return.
        boolean modalGlass = liveWorldBackdrop() && BlurPanelRenderer.renderPanel(
                g, modalX, modalY, modalW, modalH, AuroraTheme.RADIUS_LARGE,
                BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX, BlurPanelRenderer.Lighting.depressed());
        RenderUtil.drawRoundedRectAA(g, modalX, modalY, modalW, modalH,
                AuroraTheme.RADIUS_LARGE, ThemeManager.surfaceColor(ThemeToken.SURFACE));
        if (modalGlass) {
            BlurPanelRenderer.drawRimFinish(g, modalX, modalY, modalW, modalH, AuroraTheme.RADIUS_LARGE);
        } else {
            RenderUtil.drawRoundedOutlineAA(g, modalX, modalY, modalW, modalH,
                    AuroraTheme.RADIUS_LARGE, 1.0f, AuroraTheme.WINDOW_OUTLINE);
        }

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
        RenderUtil.drawRoundedRectAA(g, mx + pad, my + pad, thumbSize, thumbSize, AuroraTheme.RADIUS_SMALL,
                ThemeManager.color(ThemeToken.SURFACE_INSET));
        Identifier icon = PackIconCache.getIfLoaded(p.iconUrl);
        if (icon != null) {
            PackIconCache.blitIcon(g, icon, mx + pad, my + pad, thumbSize);
        } else {
            int bg = letterPlaceholderColor(p.title);
            RenderUtil.drawRoundedRectAA(g, mx + pad, my + pad, thumbSize, thumbSize,
                    AuroraTheme.RADIUS_SMALL, bg);
            String letter = (p.title == null || p.title.isEmpty()) ? "?"
                    : String.valueOf(Character.toUpperCase(p.title.charAt(0)));
            AuroraFontRenderer.drawCentered(g, this.font, Component.literal(letter),
                    mx + pad + thumbSize / 2, my + pad + (thumbSize - this.font.lineHeight) / 2 + 1,
                    0xFFFFFFFF);
        }
        if (p.iconUrl != null) PackIconCache.requestAsync(p.iconUrl);

        int textX = mx + pad + thumbSize + 14;
        int textW = mw - (textX - mx) - pad;
        // Routed through the memoized fitText cache (distinct field keys) —
        // the modal re-renders every frame while open, and the raw
        // truncateToWidth loop is exactly the per-frame font.width() cost
        // the cache exists to avoid.
        g.drawString(this.font, fitText("dt:" + p.projectId, p.title, textW), textX, my + pad + 2, AuroraTheme.TEXT_PRIMARY, false);
        g.drawString(this.font, "by " + fitText("da:" + p.projectId, p.author, textW),
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

        // Install button row — the shared glass Button painter at this
        // modal's larger size (a separate instance from the card's, so both
        // surfaces can show the same pack simultaneously). Clicks route
        // through handleDetailClick's rect test; the painter's action is a
        // no-op (the ButtonWidget discipline).
        CardState st = cardStates.computeIfAbsent(p.projectId, k -> new CardState());
        int btnW = 130, btnH = 24;
        int btnX = mx + pad;
        int btnY = my + mh - pad - btnH;
        Button detailInstallBtn = st.modalPhaseButtons[st.phase];
        if (detailInstallBtn == null) {
            detailInstallBtn = newPhaseButton(st.phase);
            st.modalPhaseButtons[st.phase] = detailInstallBtn;
        }
        detailInstallBtn.layout(btnX, btnY, btnW, btnH);
        detailInstallBtn.render(g, btnX, btnY, btnW, btnH, mouseX, mouseY);

        // Close button (top-right of modal) — shared glass Button painter,
        // neutral raised like every other chrome action on this screen.
        // Clicks route through handleDetailClick's rect test (no-op action).
        int cbW = 60, cbH = 22;
        int cbX = mx + mw - cbW - pad;
        int cbY = my + pad;
        if (detailCloseButton == null) {
            detailCloseButton = new Button(Component.literal("Close"), () -> {}).glassBackground(true);
        }
        detailCloseButton.layout(cbX, cbY, cbW, cbH);
        detailCloseButton.render(g, cbX, cbY, cbW, cbH, mouseX, mouseY);
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

    /**
     * True when the main render target holds a live world — i.e. the glass
     * capture source is valid. Mirrors {@code BlurPanelRenderer}'s own
     * menu-context guard so this screen never asks for glass (or drops the
     * vanilla backdrop) in a context where glass cannot engage (no level
     * loaded: the renderer declines and the flat fallback wants the vanilla
     * backdrop).
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
        int tabY = clipTop - (int) sidebarScroll.current();
        for (int i = 0; i < CATEGORIES.length; i++) {
            CategoryTab tab = CATEGORIES[i];
            int rowH = tab.section == Section.HEADER ? HEADER_H : TAB_H;
            if (tab.section != Section.HEADER
                    && Widget.inBounds(mouseX, mouseY, tabX, tabY, tabW, rowH)
                    && mouseY >= clipTop && mouseY < clipBot) {
                activeCategory = tab.slug;
                pendingQuery = searchField != null ? searchField.getValue() : "";
                submitSearch(pendingQuery, activeCategory);
                return true;
            }
            tabY += tab.section == Section.HEADER ? HEADER_H : TAB_H + TAB_GAP;
        }

        // Sidebar scrollbar drag start (center-on-cursor — the grab offset
        // is half the thumb, the screen's historical drag math).
        double sbMax = sidebarMaxScroll();
        if (sbMax > 0) {
            int trackTop = clipTop;
            int trackH = clipBot - clipTop;
            double thumbH = sidebarScroll.thumbHeight(trackH, sbMax, 24);
            int thumbY = trackTop + (int) ((trackH - thumbH) * sidebarScroll.ratio(sbMax));
            int trackX = SIDEBAR_PAD + (SIDEBAR_W - SIDEBAR_PAD * 2) - 5;
            if (mouseX >= trackX - 2 && mouseX <= trackX + 5
                    && mouseY >= thumbY - 4 && mouseY <= thumbY + thumbH + 4) {
                sidebarScroll.beginThumbDrag(thumbH / 2.0);
                return true;
            }
        }

        // Grid scrollbar drag start (center-on-cursor, likewise).
        double ms = maxScroll();
        if (ms > 0) {
            int cols = columns();
            int gridLeft = gridLeft();
            int gridRight = gridLeft + cols * (CARD_W + CARD_GAP) - CARD_GAP;
            int trackX = gridRight + 8;
            int trackTop = LIST_TOP;
            int trackH = (this.height - LIST_BOTTOM_PAD) - LIST_TOP;
            double thumbH = gridScroll.thumbHeight(trackH, ms, 30);
            int thumbY = trackTop + (int) ((trackH - thumbH) * gridScroll.ratio(ms));
            if (mouseX >= trackX - 4 && mouseX <= trackX + 8
                    && mouseY >= thumbY - 4 && mouseY <= thumbY + thumbH + 4) {
                gridScroll.beginThumbDrag(thumbH / 2.0);
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
            int y = yBase - (int) gridScroll.current();
            int btnW = 80, btnH = 18;
            int btnX = x + CARD_W - btnW - THUMB_PAD;
            int btnY = y + CARD_H - btnH - 8;
            if (Widget.inBounds(mouseX, mouseY, btnX, btnY, btnW, btnH)) {
                handleInstallClick(list.get(i));
                return true;
            }
            if (Widget.inBounds(mouseX, mouseY, x, y, CARD_W, CARD_H)) {
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
        if (Widget.inBounds(mouseX, mouseY, cbX, cbY, cbW, cbH)) {
            closeDetail();
            return true;
        }

        // Install button.
        int btnW = 130, btnH = 24;
        int btnX = modalX + pad;
        int btnY = modalY + modalH - pad - btnH;
        if (Widget.inBounds(mouseX, mouseY, btnX, btnY, btnW, btnH)) {
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

        // Painted exactly as before R2; SmoothScroll supplies geometry/drag
        // state (the old inline thumb-height math reduced to the same
        // trackH / (trackH + maxScroll) product the component computes).
        double thumbH = gridScroll.thumbHeight(trackH, ms, 30);
        int thumbY = trackTop + (int) ((trackH - thumbH) * gridScroll.ratio(ms));

        boolean hover = mouseX >= trackX - 2 && mouseX <= trackX + trackW + 2
                && mouseY >= thumbY && mouseY <= thumbY + thumbH;
        int thumbCol = (hover || gridScroll.isDragging())
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
            sidebarScroll.wheel(vertical, 28, sidebarMaxScroll());
        } else {
            gridScroll.wheel(vertical, 40, maxScroll());
        }
        return true;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent ev, double dx, double dy) {
        if (gridScroll.isDragging() && ev.button() == 0) {
            double mouseY = ev.y();
            double ms = maxScroll();
            if (ms <= 0) return true;
            int trackTop = LIST_TOP;
            int trackH = (this.height - LIST_BOTTOM_PAD) - LIST_TOP;
            if (trackH - gridScroll.thumbHeight(trackH, ms, 30) <= 0) return true;
            // jumpCurrent = true: no easing while dragging (the screen's
            // historical center-on-cursor feel).
            gridScroll.dragThumb(mouseY, trackTop, trackH, ms, 30, true);
            return true;
        }
        if (sidebarScroll.isDragging() && ev.button() == 0) {
            double mouseY = ev.y();
            double sbMax = sidebarMaxScroll();
            if (sbMax <= 0) return true;
            int panelY = TOP_BAR_H;
            int panelH = this.height - TOP_BAR_H - 16;
            int trackTop = panelY + 26;
            int trackH = panelH - 26 - 6;
            if (trackH - sidebarScroll.thumbHeight(trackH, sbMax, 24) <= 0) return true;
            sidebarScroll.dragThumb(mouseY, trackTop, trackH, sbMax, 24, true);
            return true;
        }
        return super.mouseDragged(ev, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent ev) {
        if (ev.button() == 0) {
            gridScroll.endThumbDrag();
            sidebarScroll.endThumbDrag();
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
        HoverEase h = hovers.computeIfAbsent(key, k -> new HoverEase());
        if (active != h.active) {
            h.start = System.currentTimeMillis();
            h.active = active;
        }
        if (active && h.t >= 1f) return 1f;
        if (!active && h.t <= 0f) return 0f;

        if (h.start == null) return active ? 1f : 0f;
        float raw = AuroraAnim.clamp01((System.currentTimeMillis() - h.start) / (float) HOVER_MS);
        float t = active ? raw : (1f - raw);
        h.t = AuroraAnim.easeOutCubic(t);
        return h.t;
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
        return AuroraFontRenderer.ellipsize(this.font, s, maxW, 1);
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
        toast.flash(text, 3000L);
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