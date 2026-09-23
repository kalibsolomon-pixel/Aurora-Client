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
import com.aurora.client.ui.component.GlassEditBox;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.component.Toast;
import com.aurora.client.ui.component.Widget;
import com.aurora.client.ui.interaction.MinecraftSemanticFeedback;
import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.interaction.SemanticControlGroup;
import com.aurora.client.ui.interaction.SemanticSound;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.util.ClipBand;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.ui.util.UiLayerCache;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.AuroraTheme;
import com.aurora.client.util.HoverAnim;
import com.aurora.client.util.ScrollFade;
import com.aurora.client.util.SmoothScroll;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *       their fill/outline over 140 ms via the canonical §8.3
 *       {@link HoverAnim#symmetric} vocabulary (Phase B pack-tabs pilot,
 *       2026-09-18; the screen's former local 150 ms animator is retired).
 *       Tabs model OVERLAPPING state channels: <b>selected</b> — the
 *       persistent {@code activeCategory}, painted as the accent wash that
 *       is visible at hover 0 (never through hover progress); <b>hover</b> —
 *       pointer-only, symmetric 140 ms, never pinned by selection or focus;
 *       <b>focused</b> — the Button-family 1 px accent hairline on the tab's
 *       own {@link SemanticActionControl} (Tab traversal + Enter/Space
 *       activation + Selected/Not-selected narration + exactly one
 *       selection click; reselecting the current category is a silent
 *       no-op); <b>disabled</b> — N/A (every category is always available;
 *       the only gating is per-frame availability under the sidebar clip
 *       band and the detail modal).</li>
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
 *   <li>§6 convention 6, structural (2026-09-11 — completing the mod-wide
 *       pre-dim rollout): beginGlassPass → every surface (sidebar, active
 *       tab under the tracked sidebar clip, card install buttons under the
 *       tracked grid clip, search field, Done) → GlassSurface.overlayDim at
 *       this screen's original 0x55 strength of the OVERLAY_DIM token →
 *       content. The detail modal is the screen's one ABOVE-the-dim layer
 *       (§9): its panel paints through GlassSurface.aboveDimContainer and
 *       its buttons drive their surfaces inside a GlassSurface above-dim
 *       zone, in the content phase, rims in place.</li>
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
    /** §8.3 canonical hover duration — tabs and card bodies alike (Phase B). */
    private static final long HOVER_MS      = 140L;
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
         * The phase whose button the glass pass drove THIS frame (−1 = none
         * yet). The content render looks its button up by THIS value, not the
         * live phase: a download thread flipping {@code phase} between the
         * pass and the render would otherwise hand the render an undriven
         * button instance, whose in-place surface paint is a post-dim
         * ordering violation. Both walks run in the same frame over the same
         * visible cards, so this is always fresh where it is read.
         */
        int drivenPhase = -1;

        /**
         * Lazily-created shared install buttons, indexed by the phase they
         * render — card-sized (80×18) and modal-sized (130×24) variants are
         * separate instances because both surfaces can show the same pack at
         * once. Held here (not a screen-level map) so they are pruned
         * together with the state when the result set changes.
         */
        final Button[] cardPhaseButtons = new Button[5];
        final Button[] modalPhaseButtons = new Button[5];

        /**
         * The phase buttons' semantic controls (Phase A full rollout), keyed
         * in lockstep with the button arrays above — created together by the
         * walks that drive the buttons, pruned together with the CardState
         * (the screen's prune site unregisters them, clearing focus if one
         * held it). Card-sized and modal-sized variants are separate controls
         * because their bounds (and glass passes) differ. RESOLVING/
         * DOWNLOADING/DONE-phase actions carry a disabled gate — matching
         * {@code handleInstallClick}'s own phase rule. Since the Phase B
         * pack-tabs pilot the DONE painter carries the matching disabled
         * treatment (visuals agree with the gate); RESOLVING/DOWNLOADING
         * keep enabled-style busy pixels — in-progress, not unavailable.
         */
        final SemanticActionControl[] cardPhaseControls = new SemanticActionControl[5];
        final SemanticActionControl[] modalPhaseControls = new SemanticActionControl[5];
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

    // ---- Canonical hover state (keyed by stable identity: "tab:"+index,
    // "card:"+projectId) ----
    // Phase B pack-tabs pilot (2026-09-18): the screen's former local
    // 150 ms hover animator is RETIRED — tabs and card bodies animate on the
    // §8.3 canonical
    // HoverAnim.symmetric(140) (smoothstep, pointer-only targets, continuous
    // mid-flight reversal — the old local animator restarted a reversal from
    // the far endpoint, so an interrupted enter visibly jumped). The animators
    // are keyed by stable identity (CATEGORIES array index / Modrinth project
    // id), never by list position, and card keys are pruned together with
    // their CardState when a result set changes. The former hover-OR-selected
    // target on the active tab is the conflation this
    // pilot removed: selection now reads through the persistent accent wash
    // (see renderSidebar), so hover progress returns to 0 the moment the
    // pointer leaves the tab while the tab stays visibly selected.
    private final Map<String, HoverAnim> hovers = new HashMap<>();

    private HoverAnim hoverAnim(String key) {
        return hovers.computeIfAbsent(key, k -> HoverAnim.symmetric(HOVER_MS));
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
     * The Close button's semantic control (Phase A) — created in lockstep
     * with the painter, one per screen.
     */
    private SemanticActionControl detailCloseControl;

    // ---- Phase A semantic-control lifecycle (the ManagerListScreen shape,
    // ported for this screen's manual chrome) ----
    //
    // The card + modal install buttons and the modal Close button are
    // custom-painted Buttons hit-tested manually by this screen; their
    // SemanticActionControls join vanilla's child/narratable lists so they
    // participate in focus traversal, Enter/Space activation, and narration.
    // registerSemanticControl records pre-init and attaches post-init;
    // init() re-adds every registered control after a resize's
    // rebuildWidgets; render() starts every control unavailable and the
    // card/modal walks re-mark what they actually show (render truth and
    // input truth agree); the result-set prune unregisters pruned cards'
    // controls; a click drops semantic focus first (the manager screens'
    // rule). While the detail modal is interactive, traversal is contained
    // to the modal's own controls (see nextFocusPath) — covered background
    // chrome can neither take focus nor activation.
    private final List<SemanticActionControl> semanticControls = new ArrayList<>();
    private boolean semanticWidgetsLive = false;

    /**
     * Per-category-tab semantic controls (Phase B pack-tabs pilot,
     * 2026-09-18) — one per SELECTABLE tab, lazily created by the sidebar
     * walk that first paints it, in lockstep with the CATEGORIES array
     * (header rows stay null). Tabs are navigation, and the
     * {@link SemanticActionControl} vocabulary models them exactly — focus
     * traversal, Enter/Space activation, narration with selection metadata,
     * the selection click — so no navigation-specific adapter was built:
     * this IS the existing semantic action infrastructure, one action per
     * tab, enabled-gate {@code () -> true} (disabled/unavailable is N/A in
     * the current topology — every category is always selectable; the only
     * gating is the per-frame AVAILABILITY sweep: a tab scrolled out of the
     * sidebar clip band or covered by the detail modal can neither take
     * focus nor activate, keeping render truth and input truth agreed).
     * Sound ownership: the action carries {@link SemanticSound#NONE} and
     * plays exactly one {@link SemanticSound#ACTIVATION} through
     * {@link MinecraftSemanticFeedback} inside its behavior, ONLY when the
     * category actually changes — activating the already-selected tab is a
     * silent consumed no-op (no re-fetch, no scroll reset, no repeated
     * click). No press animation by decision: the immediate selection
     * transition IS the feedback (§8.4 tabs acknowledge press via
     * selection; copying Button's scale would suggest a momentary action).
     */
    private final SemanticActionControl[] tabControls = new SemanticActionControl[CATEGORIES.length];

    /**
     * C-3: the category tabs rove Up/Down as one VERTICAL ring — the sidebar
     * is a top-to-bottom stack, so the tab-list arrows are Up/Down (the
     * geometry the audit's "Left/Right" shorthand actually described for a
     * vertical list). Attach order is category order: the sidebar walk
     * materializes controls in lockstep with {@code CATEGORIES}, and header
     * rows simply contribute no member. Arrows move focus silently with
     * wrap; the availability sweep still governs eligibility (a tab scrolled
     * out of the clip band or covered by the modal is skipped).
     */
    private final SemanticControlGroup categoryTabGroup = SemanticControlGroup.vertical();

    /** True while the modal is past its interaction gate — the same condition the click path uses. */
    private boolean modalInteractive() {
        return detailOpenT > 0.5f && detailProject != null;
    }

    // ---- C-8: the modal's ONE animation/bounds truth ----
    //
    // The open animation translates the sheet 16px upward from its resting
    // position (eased). Before C-8 the render/button-drive/content walks used
    // the animated position while handleDetailClick hit-tested the FINAL
    // position — up to 8px of painted-control ≠ clickable-control mismatch
    // through every sampled animation frame. Every consumer now derives from
    // these three helpers, so the interactive rect IS the painted rect at
    // each animation sample (and settles to the final rect on completion).
    private int detailModalW() { return Math.min(460, this.width - 40); }
    private int detailModalH() { return Math.min(360, this.height - 60); }
    private int detailModalX() { return (this.width - detailModalW()) / 2; }
    /** The eased sheet translate — the only place the animation enters geometry. */
    private int detailModalY() {
        float openT = AuroraAnim.easeOutCubic(detailOpenT);
        return (this.height - detailModalH()) / 2 + (int) ((1f - openT) * 16);
    }

    private void registerSemanticControl(SemanticActionControl control) {
        if (control == null || semanticControls.contains(control)) return;
        semanticControls.add(control);
        if (semanticWidgetsLive) {
            control.setFocused(false);
            control.setAvailable(false);
            this.addWidget(control);
        }
    }

    private void unregisterSemanticControl(SemanticActionControl control) {
        if (control == null) return;
        semanticControls.remove(control);
        if (semanticWidgetsLive) this.removeWidget(control);
    }

    /** True when {@code tab} is the persistent selected category (null slug = "All"). */
    private boolean isActiveCategory(CategoryTab tab) {
        return (tab.slug == null && activeCategory == null)
                || (tab.slug != null && tab.slug.equals(activeCategory));
    }

    /**
     * The one category-selection path — pointer clicks and keyboard
     * activation both land here. Activating the already-selected category is
     * a NO-OP by decision (Phase B): the baseline re-ran submitSearch, which
     * cleared the text-fit cache and reset the grid scroll to top on every
     * redundant click — redundant downstream work with a visible side
     * effect. Selection itself remains immediate (same tick), and hover
     * stays pointer-derived per tab (the animators are independent of this
     * state).
     */
    private void selectCategory(CategoryTab tab) {
        if (isActiveCategory(tab)) return;
        activeCategory = tab.slug;
        pendingQuery = searchField != null ? searchField.getValue() : "";
        submitSearch(pendingQuery, activeCategory);
    }

    /**
     * The tab's semantic control — focus traversal, Enter/Space activation,
     * exactly-one selection click, narration carrying
     * {@code Selected / Not selected}. Enabled is unconditionally true so
     * the SELECTED tab stays focusable and narrates normally (a disabled
     * gate would drop it out of Tab traversal — you could never key onto
     * the current category); the already-selected no-op lives in the
     * behavior with the sound (see the field javadoc above).
     */
    private SemanticActionControl tabControl(int i) {
        SemanticActionControl control = tabControls[i];
        if (control != null) return control;
        CategoryTab tab = CATEGORIES[i];
        control = new SemanticActionControl(new SemanticAction(
                Component.literal(tab.displayName),
                () -> Component.literal("Filters the pack list to this category."),
                () -> Component.literal(isActiveCategory(tab) ? "Selected" : "Not selected"),
                () -> true,
                () -> {
                    if (isActiveCategory(tab)) return; // silent no-op
                    MinecraftSemanticFeedback.INSTANCE.play(SemanticSound.ACTIVATION);
                    selectCategory(tab);
                },
                SemanticSound.NONE, true, true),
                MinecraftSemanticFeedback.INSTANCE,
                null, // no press animation — selection transition is the feedback
                SemanticActionControl.PointerRouting.MANUAL);
        tabControls[i] = control;
        categoryTabGroup.attach(control); // C-3 ring; idempotent across re-materialization
        registerSemanticControl(control);
        return control;
    }

    /**
     * The Done chrome button — kept as a field so the glass pass drives its
     * surface pre-dim (the ButtonWidget discipline, as on every migrated
     * screen).
     */
    private ButtonWidget doneBtn;

    /**
     * Whether the sidebar's depressed glass panel engaged this frame. Set by
     * the glass pass (paintGlassPass, before the overlay dim — the layering
     * contract) and consulted by {@link #renderSidebar} to suppress the flat
     * panel fill + outline.
     */
    private boolean sidebarGlass = false;

    /**
     * The ACTIVE category tab's glass + hover-lerp state from the glass pass
     * (the tab's accent wash is its glass tint, so the lerp is evaluated in
     * the pass; the content pass reads these for its flat fallback + label).
     */
    private boolean activeTabGlass = false;
    private float activeTabT = 0f;

    // ---- Loading spinner animation ----
    private float spinnerAngle = 0f;

    // ---- P2 static card template (see renderCard's comment) ----
    private UiLayerCache cardTpl;

    /** The at-rest card shape raster (body + outline + thumb inset + frame), lazily captured, version-keyed. */
    private UiLayerCache ensureCardTemplate(GuiGraphics g) {
        long version = cardTemplateVersion();
        if (cardTpl == null) cardTpl = new UiLayerCache();
        if (!cardTpl.isCurrent(version)) {
            int scale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
            cardTpl.ensureSize(CARD_W * scale, CARD_H * scale);
            cardTpl.clear();
            RenderUtil.RectSink prev = RenderUtil.beginCapture(cardTpl.sink());
            try {
                // The t == 0 colors of the live branch in renderCard — keep
                // the two in lockstep (theme-projected statics; the version
                // key's generation stamp re-rasterizes on any theme change).
                RenderUtil.drawRoundedRectAA(g, 0, 0, CARD_W, CARD_H, AuroraTheme.RADIUS,
                        AuroraTheme.IOS_SECONDARY_BG);
                RenderUtil.drawRoundedOutlineAA(g, 0, 0, CARD_W, CARD_H, AuroraTheme.RADIUS, 1.0f,
                        AuroraTheme.TILE_OUTLINE_OFF);
                int tx = THUMB_PAD;
                int ty = (CARD_H - THUMB_SIZE) / 2;
                RenderUtil.drawRoundedRectAA(g, tx - 1, ty - 1, THUMB_SIZE + 2, THUMB_SIZE + 2,
                        AuroraTheme.RADIUS_SMALL, ThemeManager.color(ThemeToken.SURFACE_INSET));
                RenderUtil.drawRoundedOutlineAA(g, tx, ty, THUMB_SIZE, THUMB_SIZE, AuroraTheme.RADIUS_SMALL, 1.0f,
                        AuroraAnim.scaleAlpha(AuroraTheme.TEXT_PRIMARY, 0.10f));
            } finally {
                RenderUtil.endCapture(prev);
            }
            cardTpl.commit(version);
        }
        return cardTpl;
    }

    private long cardTemplateVersion() {
        int scale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
        return ThemeManager.generation() * 1_000_003L
                ^ (long) scale * 65537L
                ^ (long) CARD_W * 7919L
                ^ (long) CARD_H * 104729L;
    }

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

        doneBtn = ButtonWidget.semantic(
                this.width - 80 - 16, 12, 80, 22,
                Component.literal("Done"),
                "Close this screen and return.",
                this::onClose).glassBackground(true);
        this.addRenderableWidget(doneBtn);

        // Custom-painted semantic controls (card/modal install buttons, the
        // modal Close) join the vanilla child/narratable lifecycle without
        // joining its render list. A rebuildWidgets (resize) cleared the
        // lists; every registered control re-adds here.
        for (SemanticActionControl control : semanticControls) {
            control.setFocused(false);
            control.setAvailable(false);
            this.addWidget(control);
        }
        semanticWidgetsLive = true;

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
            // the map doesn't grow unboundedly across many searches. The
            // pruned states' semantic controls (card + modal install buttons)
            // are unregistered too — removeWidget takes them out of the
            // vanilla child/narratable lists and clears screen focus if one
            // held it, so a vanished card can never leave a stale focusable
            // region behind (the manager-row lifecycle rule).
            Set<String> liveIds = pr.stream()
                    .map(p -> p.projectId)
                    .collect(java.util.stream.Collectors.toSet());
            for (Map.Entry<String, CardState> entry : cardStates.entrySet()) {
                if (!liveIds.contains(entry.getKey())) {
                    CardState removed = entry.getValue();
                    for (SemanticActionControl control : removed.cardPhaseControls) {
                        unregisterSemanticControl(control);
                    }
                    for (SemanticActionControl control : removed.modalPhaseControls) {
                        unregisterSemanticControl(control);
                    }
                }
            }
            cardStates.keySet().retainAll(liveIds);
            // Card hover animators are keyed by the same stable ids — prune
            // them with their CardState so vanished cards never leave stale
            // animator entries behind (tab animators are index-keyed and
            // live for the screen's lifetime).
            Set<String> liveHoverKeys = new java.util.HashSet<>(liveIds.size());
            for (String id : liveIds) liveHoverKeys.add("card:" + id);
            hovers.keySet().removeIf(k -> k.startsWith("card:") && !liveHoverKeys.contains(k));
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

        // Semantic availability is re-derived every frame (the manager-screen
        // rule): everything starts unavailable and the card/modal walks below
        // re-mark what they actually show, so a scrolled-out card's install
        // button (or a modal control while the modal is closed) can neither be
        // keyed nor pointer-activated while invisible.
        //
        // Modal focus containment (design language §13.3, keyboard side):
        // while the detail modal is interactive, the covered chrome (Done
        // button, search field) is made INACTIVE — vanilla's own traversal
        // gate (`AbstractWidget.nextFocusPath` returns null when inactive)
        // then skips them, so Tab/arrow traversal can only reach the modal's
        // two semantic controls, and vanilla's active guard blocks their
        // pointer activation beneath the modal. (1.21.11's Screen.keyPressed
        // invokes the CONTAINER's nextFocusPath non-virtually, so a
        // screen-level traversal override cannot intercept this — the
        // child-level gate is the mechanism that actually carries it.) The
        // Done button painting its existing disabled treatment while covered
        // is the sanctioned disabled-look sync (§16 unavailable).
        boolean modalInteractive = modalInteractive();
        for (SemanticActionControl control : semanticControls) {
            control.setAvailable(false);
        }
        if (doneBtn != null) doneBtn.active = !modalInteractive;
        if (searchField != null) searchField.active = !modalInteractive;

        // ---- 1. Glass pass — every surface BEFORE the dim (§6 convention 6 ----
        // structural; this screen completed the mod-wide rollout 2026-09-10).
        // The sidebar panel (a DEPRESSED container with the SURFACE-token
        // tint) was already pre-dim; its rim now defers past the dim like
        // every other surface's. The ACTIVE category tab is a selected
        // control — its historical accent-lerp wash IS its glass tint (the
        // explicit-tint GlassSurface.control), evaluated in the pass so the
        // hover lerp can never disagree with the label pass. Card install
        // buttons drive their shared-Button surfaces under the tracked grid
        // scissor (deferred rims keep the clip); the search field and Done
        // drive their own splits. The detail modal stays ABOVE the dim by
        // design (§9) and paints in the content phase through the above-dim
        // escape hatches.
        GlassSurface.beginGlassPass();
        paintGlassPass(g, mouseX, mouseY);

        // ---- 2. Dim — this screen's original 0x55 strength of the OVERLAY_DIM ----
        // token, through the argb overload built for exactly this; it closes
        // the glass pass and flushes the deferred rims.
        GlassSurface.overlayDim(g, this.width, this.height,
                ThemeManager.withAlpha(ThemeManager.color(ThemeToken.OVERLAY_DIM), 0x55));

        // ---- 3. Content ----
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
        // scroll offset so off-screen cards never enter renderCard (or the
        // glass pass's button walk — forEachVisibleCard, the one shared
        // culling walk). The GPU scissor only discards pixels at the
        // rasterizer — those cards would still cost CPU draw-call
        // submissions + state setup. Skipping them outright is strictly
        // cheaper and is the dominant perf win when a search returns many
        // results.
        forEachVisibleCard(list, cols, gridLeft, listBottom, (p, x, y) ->
                renderCard(g, p, x, y, mouseX, mouseY));

        g.disableScissor();

        // Design language §8 — top-edge scroll fade for the card grid. The
        // cards are deliberately FLAT (R9) and float over the screen's
        // dimmed-world backdrop with no container of their own, so the
        // surface they dissolve into is this screen's dim veil at its own
        // 0x55 strength — the same withAlpha the overlayDim call above
        // applies, keeping the fade's top color exactly what the backdrop
        // already is at that position.
        ScrollFade.drawTop(g, gridLeft - 4,
                cols * (CARD_W + CARD_GAP) - CARD_GAP + 8,
                LIST_TOP - 2, ScrollFade.FADE_PX,
                gridScroll.current(),
                ThemeManager.withAlpha(ThemeManager.color(ThemeToken.OVERLAY_DIM), 0x55));

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

    /** One visible card in the grid walk — see {@link #forEachVisibleCard}. */
    private interface CardVisitor {
        void visit(ModrinthProject p, int x, int y);
    }

    /**
     * The grid's viewport-culling walk — the ONE walk, shared by the content
     * render and the glass pass's per-card button driving so the two can
     * never drift apart.
     */
    private void forEachVisibleCard(List<ModrinthProject> list, int cols, int gridLeft, int listBottom,
                                    CardVisitor visitor) {
        if (list.isEmpty() || cols <= 0) return;
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
                visitor.visit(list.get(i), gridLeft + col * colW,
                        LIST_TOP + row * rowH - (int) gridScroll.current());
            }
        }
    }

    /**
     * The glass pass (§6 convention 6) — every surface except the modal's,
     * painted between beginGlassPass and overlayDim. The active tab and the
     * card buttons paint under their tracked scissors so their deferred rims
     * re-apply the clips after the dim.
     */
    private void paintGlassPass(GuiGraphics g, int mouseX, int mouseY) {
        int panelH = this.height - TOP_BAR_H - 16;
        int panelY = TOP_BAR_H;
        int panelW = SIDEBAR_W - SIDEBAR_PAD * 2;
        int panelX = SIDEBAR_PAD;
        // Sidebar — DEPRESSED container, SURFACE-token tint (whose alpha
        // carries the Background Opacity via surfaceColor — the single
        // application point). Flat panel returns in renderSidebar on decline.
        sidebarGlass = GlassSurface.container(g, panelX, panelY, panelW, panelH,
                AuroraTheme.RADIUS_LARGE, ThemeToken.SURFACE);
        paintActiveTabPass(g, mouseX, mouseY, panelX, panelY, panelW, panelH);
        paintCardButtonsPass(g);
        ((GlassEditBox) searchField).aurora$renderGlassPass(g);
        if (doneBtn != null) doneBtn.renderGlassPass(g);
    }

    /**
     * The ACTIVE category tab's surface. Its look is a raised glass panel
     * tinted by an accent wash whose strength lerps with hover (0.30→0.40) —
     * the wash IS the tint, so it paints here, in the pass, through the
     * explicit-tint GlassSurface.control; the hover ease for the active
     * tab's key advances here (once per frame — renderSidebar skips it for
     * the active tab and reads activeTabT). The target is the POINTER ONLY
     * (Phase B): the pre-pilot code drove this animator with hover OR
     * selected, so the persistent selected state was
     * expressed solely through a hover animator stuck at its endpoint —
     * selection is now the persistent 0.30 wash itself (visible at
     * hoverT == 0), and hover is the transient 0.30→0.40 strengthening on
     * top. The tab's semantic control is stamped here too (bounds +
     * availability under the same clip/modal gates the content walk uses).
     * Under the tracked sidebar clip so the deferred rim keeps it. Inactive
     * tabs stay flat by design (small transient rows inside an already-glass
     * container).
     */
    private void paintActiveTabPass(GuiGraphics g, int mouseX, int mouseY,
                                    int panelX, int panelY, int panelW, int panelH) {
        activeTabGlass = false;
        int clipTop = panelY + 26;
        int clipBot = panelY + panelH - 6;
        int tabY = clipTop - (int) sidebarScroll.current();
        int tabX = panelX + 4;
        int tabW = panelW - 8;
        for (int i = 0; i < CATEGORIES.length; i++) {
            CategoryTab tab = CATEGORIES[i];
            if (tab.section == Section.HEADER) {
                tabY += HEADER_H;
                continue;
            }
            if (isActiveCategory(tab)) {
                boolean visible = tabY + TAB_H > clipTop && tabY < clipBot;
                boolean hover = visible && Widget.inBounds(mouseX, mouseY, tabX, tabY, tabW, TAB_H)
                        && mouseY >= clipTop && mouseY < clipBot;
                activeTabT = hoverAnim("tab:" + i).update(hover);
                SemanticActionControl control = tabControl(i);
                control.setBounds(tabX, tabY, tabW, TAB_H);
                control.setAvailable(visible && !modalInteractive());
                if (visible) {
                    float tabR = Math.min(TAB_H / 2f, AuroraTheme.RADIUS_SMALL);
                    int fill = AuroraAnim.lerpArgb(
                            AuroraAnim.scaleAlpha(AuroraTheme.IOS_BLUE, 0.30f),
                            AuroraAnim.scaleAlpha(AuroraTheme.IOS_BLUE_HOVER, 0.40f), activeTabT);
                    GlassSurface.enableScissor(g, panelX, clipTop, panelX + panelW, clipBot);
                    activeTabGlass = GlassSurface.control(g, tabX, tabY, tabW, TAB_H, tabR, fill);
                    GlassSurface.disableScissor(g);
                }
                return;
            }
            tabY += TAB_H + TAB_GAP;
        }
    }

    /**
     * Per-card install buttons — the shared Button's own split, driven here
     * under the tracked grid scissor (the same culling walk the content
     * render uses); renderCard's button render then paints labels only.
     * Nothing bespoke: the buttons are ordinary shared-painter Buttons with
     * DETAIL priority. drivenPhase is stamped per card so a download thread
     * flipping the phase mid-frame cannot hand the content render an
     * undriven (post-dim-painting) button instance.
     */
    private void paintCardButtonsPass(GuiGraphics g) {
        int gridLeft = gridLeft();
        int cols = columns();
        int listBottom = this.height - LIST_BOTTOM_PAD;
        List<ModrinthProject> list = results;
        boolean modalInteractive = modalInteractive();
        GlassSurface.enableScissor(g, gridLeft - 4, LIST_TOP - 2,
                gridLeft + cols * (CARD_W + CARD_GAP) - CARD_GAP + 4, listBottom);
        forEachVisibleCard(list, cols, gridLeft, listBottom, (p, x, y) -> {
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
            // The card's semantic control is marked here too (same clip-band
            // rule the content pass and the click walk use), so a visible
            // card's install button is keyed and clickable exactly while it
            // renders — and never while the modal covers the grid.
            SemanticActionControl control = phaseControl(p, st, st.phase, installBtn, false);
            control.setBounds(btnX, btnY, btnW, btnH);
            control.setAvailable(!modalInteractive);
            installBtn.renderGlassPass(g, btnX, btnY, btnW, btnH);
            st.drivenPhase = st.phase;
        });
        GlassSurface.disableScissor(g);
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

            boolean isActive = isActiveCategory(tab);
            boolean inBand = tabY + TAB_H > clipTop && tabY < clipBot;
            boolean hover = inBand && Widget.inBounds(mouseX, mouseY, tabX, tabY, tabW, TAB_H)
                    && mouseY >= clipTop && mouseY < clipBot;

            // Semantic control sync (the EnumSetting/BooleanSetting
            // discipline): bounds, availability (clip band + modal cover),
            // and pointer-hover-for-narration mirror the painted geometry
            // every frame, so render truth and input truth agree — a tab
            // scrolled out of the band or covered by the detail modal can
            // neither be keyed nor clicked.
            SemanticActionControl control = tabControl(i);
            control.setBounds(tabX, tabY, tabW, TAB_H);
            control.setAvailable(inBand && !modalInteractive());
            control.updatePointer(mouseX, mouseY);

            // Canonical hover: the POINTER only, symmetric 140 ms. The
            // ACTIVE tab's ease is advanced by the glass pass (its tint is
            // the accent wash, so the lerp is evaluated there — renderSidebar
            // reads activeTabT); inactive tabs advance here. Selection is a
            // SEPARATE persistent channel: the active rest fill is the 0.30
            // accent wash itself (visible at hoverT == 0), hover composes on
            // top as the transient 0.30→0.40 strengthening — the four states
            // (inactive/active × rest/hover) never collapse.
            float t = isActive ? activeTabT : hoverAnim("tab:" + i).update(hover);

            int fill;
            int textCol;
            int outlineCol = 0;
            if (isActive) {
                // Persistent selected treatment: accent wash fill (0.30 at
                // rest, 0.40 settled-hover) + accent outline — independent
                // of the hover animator's endpoint.
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
                // Selected/primary element — its glass surface (panel +
                // accent-wash tint + rim) was painted pre-dim by the glass
                // pass; only the label renders here. On decline the flat
                // accent fill + outline return. Inactive tabs — including
                // their hover state — deliberately stay flat neutral: they
                // are small transient rows inside an already-glass sidebar
                // container, not genuine surfaces.
                if (!activeTabGlass) {
                    RenderUtil.drawRoundedRectAA(g, tabX, tabY, tabW, TAB_H, tabR, fill);
                    RenderUtil.drawRoundedOutlineAA(g, tabX, tabY, tabW, TAB_H, tabR, 1.0f, outlineCol);
                }
            } else if ((fill >>> 24) != 0) {
                RenderUtil.drawRoundedRectAA(g, tabX, tabY, tabW, TAB_H, tabR, fill);
            }

            // Keyboard focus: the Button-family 1 px accent hairline
            // (geometry-following, 0x99) — an independent channel from both
            // the wash (selection's carrier) and hover. It composes with the
            // selected outline (0x55 accent underneath) without either state
            // losing legibility: selection reads through the fill, focus
            // through the stroke.
            if (control.isFocused()) {
                RenderUtil.drawRoundedOutlineAA(g, tabX, tabY, tabW, TAB_H, tabR, 1.0f,
                        ThemeManager.semanticContrast().focusNeutral());
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

        // Design language §8 — top-edge scroll fade for the sidebar tabs.
        // The sidebar IS a container (depressed glass / flat SURFACE-token
        // panel — same token either way), so the tabs dissolve into
        // surfaceColor(SURFACE): the exact color the panel body already has
        // at the clip boundary in both the glass and fallback looks.
        ScrollFade.drawTop(g, panelX, panelW, clipTop, ScrollFade.FADE_PX,
                sidebarScroll.current(), ThemeManager.surfaceColor(ThemeToken.SURFACE));

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
        float t = hoverAnim("card:" + p.projectId).update(cardHover);

        // Card body — deliberately FLAT (audit R9/B1/B2, 2026-09-08): the
        // hover-lerp tint below is fully opaque, so when this was a per-card
        // renderPanel the captured blur was 100% occluded — pure wasted
        // capture→blur→readback per card, and a full grid of glass cards +
        // install buttons could exceed the 24-panel output pool (glass/flat
        // flicker). The flat look is the exact fill + hover outline this
        // screen always drew on renderer decline, so nothing changes
        // visually. Glass on this screen lives on the sidebar, detail modal,
        // active category tab and the shared-painter buttons.
        //
        // P2 (same audit): at rest (hover t == 0) every card's static shape
        // layer — body fill + outline, thumbnail inset + frame — is IDENTICAL,
        // so it comes from a one-shot template raster blitted per card (one
        // O(1) submission each) instead of ~350 AA strip fills per card per
        // frame; a full grid of those is what made GuiRenderState's per-fill
        // intersection search the screen's dominant cost. A hovered card
        // (t > 0, animating or settled) paints the live shapes for those
        // frames — one card's worth, bounded.
        if (t == 0f) {
            ensureCardTemplate(g).blitAt(g, x, y, CARD_W, CARD_H);
        } else {
            int fill = AuroraAnim.lerpArgb(AuroraTheme.IOS_SECONDARY_BG, AuroraTheme.IOS_TERTIARY_BG, t);
            RenderUtil.drawRoundedRectAA(g, x, y, CARD_W, CARD_H, AuroraTheme.RADIUS, fill);
            int outline = AuroraAnim.lerpArgb(AuroraTheme.TILE_OUTLINE_OFF, AuroraTheme.TILE_OUTLINE_ON, t);
            RenderUtil.drawRoundedOutlineAA(g, x, y, CARD_W, CARD_H, AuroraTheme.RADIUS, 1.0f, outline);

            // Thumbnail inset (the frame outline below is hover-tinted).
            int tx = x + THUMB_PAD;
            int ty = y + (CARD_H - THUMB_SIZE) / 2;
            RenderUtil.drawRoundedRectAA(g, tx - 1, ty - 1, THUMB_SIZE + 2, THUMB_SIZE + 2,
                    AuroraTheme.RADIUS_SMALL, ThemeManager.color(ThemeToken.SURFACE_INSET));
            RenderUtil.drawRoundedOutlineAA(g, tx, ty, THUMB_SIZE, THUMB_SIZE, AuroraTheme.RADIUS_SMALL, 1.0f,
                    AuroraAnim.scaleAlpha(AuroraTheme.TEXT_PRIMARY, 0.10f + 0.10f * t));
        }

        // Thumbnail (icon blit, or per-title letter placeholder — both live;
        // neither is template-identical across cards).
        int tx = x + THUMB_PAD;
        int ty = y + (CARD_H - THUMB_SIZE) / 2;
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
        // Its SURFACE was painted pre-dim by paintCardButtonsPass (the same
        // culling walk drove the shared split); this render paints the label
        // only. The lookup uses the pass's drivenPhase, not the live phase:
        // a download thread flipping the phase between the two walks would
        // otherwise hand this render an undriven instance, whose in-place
        // surface paint is a post-dim ordering violation. Clicks still route
        // through this screen's own rect hit-testing (see mouseClicked), so
        // the painter is constructed with a no-op action — the ButtonWidget
        // discipline.
        CardState st = cardStates.computeIfAbsent(p.projectId, k -> new CardState());
        int btnW = 80, btnH = 18;
        int btnX = x + CARD_W - btnW - THUMB_PAD;
        int btnY = y + CARD_H - btnH - 8;
        int renderPhase = st.drivenPhase >= 0 ? st.drivenPhase : st.phase;
        Button installBtn = st.cardPhaseButtons[renderPhase];
        if (installBtn == null) {
            installBtn = newPhaseButton(renderPhase);
            st.cardPhaseButtons[renderPhase] = installBtn;
        }
        installBtn.layout(btnX, btnY, btnW, btnH);
        // The control syncs again at content time (same rect): pointer hover
        // for narration and the provisional focus ring. Availability was
        // re-marked by the glass pass walk above (same clip, same gate).
        SemanticActionControl control = phaseControl(p, st, renderPhase, installBtn, false);
        control.setBounds(btnX, btnY, btnW, btnH);
        control.setAvailable(!modalInteractive());
        control.updatePointer(mouseX, mouseY);
        installBtn.focused(control.isFocused());
        installBtn.render(g, btnX, btnY, btnW, btnH, mouseX, mouseY);
    }

    /**
     * Shared install-phase button factory. The shared {@link Button} has no
     * success variant, so the phases map onto its variant language: Install
     * is the primary action → accent-STAINED glass (the glass conventions
     * reserve stained for primary/selected elements; the detail modal's old
     * hand-rolled Install was already an accent gradient); Resolving…/
     * Downloading… are neutral glass (busy states — their phase gate already
     * rejects activation and narrates the progress line, and their pixels
     * legitimately read as an in-progress control, not an unavailable one);
     * Installed ✓ carries the canonical Button DISABLED treatment
     * (inset fill, muted label — DONE is terminal and its action is
     * genuinely unavailable, so the painter finally agrees with the gate the
     * semantic action has enforced since Phase A); Retry is the component's
     * destructive treatment (semantic-error fill + outline + white label —
     * Button deliberately pins destructive to the flat look). The previous
     * success-green fills are not expressible through the shared painter
     * without modifying it, which this change is forbidden to do.
     */
    private static Button newPhaseButton(int phase) {
        // Per-card buttons: DETAIL priority — one per visible card, the
        // pool's largest repeated demand on a big grid (R10), and the first
        // surfaces to go flat when a frame over-subscribes the pool.
        return switch (phase) {
            case CardState.IDLE -> new Button(Component.literal("Install"), () -> {}, true)
                    .glassStyle(Button.GlassStyle.STAINED).priority(BlurPanelRenderer.Priority.DETAIL);
            case CardState.RESOLVING -> new Button(Component.literal("Resolving…"), () -> {})
                    .glassBackground(true).priority(BlurPanelRenderer.Priority.DETAIL);
            case CardState.DOWNLOADING -> new Button(Component.literal("Downloading…"), () -> {})
                    .glassBackground(true).priority(BlurPanelRenderer.Priority.DETAIL);
            case CardState.DONE -> new Button(Component.literal("Installed ✓"), () -> {})
                    .disabled(true);
            case CardState.FAILED -> new Button(Component.literal("Retry"), () -> {})
                    .destructive(true);
            default -> throw new IllegalArgumentException("phase " + phase);
        };
    }

    /**
     * The semantic control for one (CardState, phase) install button, created
     * in lockstep with its painter by whichever walk drives it first. The
     * action's enabled gate is the install handler's own phase rule (IDLE and
     * FAILED accept; RESOLVING/DOWNLOADING/DONE reject) — a rejected activation
     * is consumed-but-inert at the call sites, exactly like today's clicks on
     * a "Resolving…" button, and narration reports the phase state. DONE's
     * painter mirrors the gate with the canonical disabled treatment; the
     * busy phases keep enabled-style pixels (see newPhaseButton). The
     * accessible name carries the pack title.
     */
    private SemanticActionControl phaseControl(ModrinthProject p, CardState st, int phase,
                                               Button painter, boolean modal) {
        SemanticActionControl[] controls = modal ? st.modalPhaseControls : st.cardPhaseControls;
        SemanticActionControl control = controls[phase];
        if (control != null) return control;
        String title = p.title == null || p.title.isBlank() ? p.projectId : p.title;
        boolean actionable = phase == CardState.IDLE || phase == CardState.FAILED;
        String verb = phase == CardState.FAILED ? "Retry" : "Install";
        control = new SemanticActionControl(SemanticAction.button(
                Component.literal(verb + " " + title),
                () -> actionable
                        ? Component.literal("Downloads the pack into your resourcepacks folder. Enable it in Options → Resource Packs.")
                        : Component.empty(),
                () -> switch (phase) {
                    case CardState.RESOLVING -> Component.literal("Resolving a compatible version…");
                    case CardState.DOWNLOADING -> Component.literal("Downloading…");
                    case CardState.DONE -> Component.literal("Installed");
                    default -> Component.empty();
                },
                () -> st.phase == phase && actionable,
                () -> handleInstallClick(p)),
                MinecraftSemanticFeedback.INSTANCE,
                painter::triggerPressAnimation,
                SemanticActionControl.PointerRouting.MANUAL);
        controls[phase] = control;
        registerSemanticControl(control);
        return control;
    }

    private void renderDetailModal(GuiGraphics g, int mouseX, int mouseY) {
        float openT = AuroraAnim.easeOutCubic(detailOpenT);

        // Dim backdrop fades in with the modal.
        int dimAlpha = (int) (180 * openT);
        g.fill(0, 0, this.width, this.height, (dimAlpha << 24));

        // C-8: the modal's geometry comes from the ONE truth (the same
        // helpers handleDetailClick hit-tests against), so the painted sheet
        // and its interactive rect agree at every animation sample.
        int modalW = detailModalW();
        int modalH = detailModalH();
        int modalX = detailModalX();
        // Slight upward translate during open for a sheet-like feel.
        int modalY = detailModalY();

        g.enableScissor(0, 0, this.width, this.height);
        // Modal body — ABOVE the dim by design (§9's other named case: the
        // modal must float over the dimmed screen, its own translucent
        // backdrop already darkening what is behind it), so it paints here
        // in the content phase through the container-shaped above-dim escape
        // hatch: DEPRESSED glass behind the SURFACE-token tint, rim finish in
        // place, exempt from the glass-pass ordering. The manual drop shadow
        // + top sheen are gone — the glass system replaces them. The glass
        // captures the already-dimmed screen as its backdrop, exactly as it
        // did before this screen migrated. On decline the flat fill + outline
        // return.
        boolean modalGlass = GlassSurface.aboveDimContainer(g, modalX, modalY, modalW, modalH,
                AuroraTheme.RADIUS_LARGE, ThemeToken.SURFACE);
        if (!modalGlass) {
            RenderUtil.drawRoundedRectAA(g, modalX, modalY, modalW, modalH,
                    AuroraTheme.RADIUS_LARGE, ThemeManager.surfaceColor(ThemeToken.SURFACE));
            RenderUtil.drawRoundedOutlineAA(g, modalX, modalY, modalW, modalH,
                    AuroraTheme.RADIUS_LARGE, 1.0f, AuroraTheme.WINDOW_OUTLINE);
        }

        if (detailProject != null) {
            // The modal's own buttons are component-driven surfaces that must
            // stay WITH the modal above the dim (driving them in the screen's
            // glass pass would bury their glass under the modal's backdrop
            // fill + panel). The above-dim zone bracket exempts their
            // in-place painting from the ordering report; renderDetailContent
            // then renders labels only (the frame stamps match).
            GlassSurface.beginAboveDim();
            try {
                driveModalButtonsGlassPass(g, detailProject, modalX, modalY, modalW, modalH);
            } finally {
                GlassSurface.endAboveDim();
            }
            renderDetailContent(g, detailProject, modalX, modalY, modalW, modalH, mouseX, mouseY);
        }
        g.disableScissor();
    }

    /**
     * Drives the modal's install + Close button surfaces in place (above the
     * dim, inside the caller's above-dim zone). The geometry is the exact
     * math {@link #renderDetailContent} uses for its renders — keep the two
     * in lockstep. Their semantic controls' bounds and availability are
     * stamped here too (the modal is the only place they are interactive).
     */
    private void driveModalButtonsGlassPass(GuiGraphics g, ModrinthProject p,
                                            int mx, int my, int mw, int mh) {
        int pad = 18;
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
        SemanticActionControl installControl = phaseControl(p, st, st.phase, detailInstallBtn, true);
        installControl.setBounds(btnX, btnY, btnW, btnH);
        installControl.setAvailable(true);
        detailInstallBtn.renderGlassPass(g, btnX, btnY, btnW, btnH);

        int cbW = 60, cbH = 22;
        int cbX = mx + mw - cbW - pad;
        int cbY = my + pad;
        if (detailCloseButton == null) {
            detailCloseButton = new Button(Component.literal("Close"), () -> {}).glassBackground(true);
        }
        detailCloseButton.layout(cbX, cbY, cbW, cbH);
        SemanticActionControl closeControl = closeControl();
        closeControl.setBounds(cbX, cbY, cbW, cbH);
        closeControl.setAvailable(true);
        detailCloseButton.renderGlassPass(g, cbX, cbY, cbW, cbH);
    }

    /** The modal Close button's semantic control — created in lockstep with its painter. */
    private SemanticActionControl closeControl() {
        if (detailCloseControl == null) {
            detailCloseControl = new SemanticActionControl(SemanticAction.button(
                    Component.literal("Close"),
                    () -> Component.literal("Closes the pack details."),
                    () -> Component.empty(),
                    () -> true,
                    this::closeDetail),
                    MinecraftSemanticFeedback.INSTANCE,
                    () -> {
                        if (detailCloseButton != null) detailCloseButton.triggerPressAnimation();
                    },
                    SemanticActionControl.PointerRouting.MANUAL);
            registerSemanticControl(detailCloseControl);
        }
        return detailCloseControl;
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
        // surfaces can show the same pack simultaneously). Its semantic
        // control re-syncs here (same rect the glass pass computed): pointer
        // hover for narration and the provisional focus ring. Clicks route
        // through handleDetailClick's control routing.
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
        SemanticActionControl installControl = phaseControl(p, st, st.phase, detailInstallBtn, true);
        installControl.setBounds(btnX, btnY, btnW, btnH);
        installControl.setAvailable(true);
        installControl.updatePointer(mouseX, mouseY);
        detailInstallBtn.focused(installControl.isFocused());
        detailInstallBtn.render(g, btnX, btnY, btnW, btnH, mouseX, mouseY);

        // Close button (top-right of modal) — shared glass Button painter,
        // neutral raised like every other chrome action on this screen.
        // Clicks route through handleDetailClick's control routing.
        int cbW = 60, cbH = 22;
        int cbX = mx + mw - cbW - pad;
        int cbY = my + pad;
        if (detailCloseButton == null) {
            detailCloseButton = new Button(Component.literal("Close"), () -> {}).glassBackground(true);
        }
        detailCloseButton.layout(cbX, cbY, cbW, cbH);
        SemanticActionControl closeControl = closeControl();
        closeControl.setBounds(cbX, cbY, cbW, cbH);
        closeControl.setAvailable(true);
        closeControl.updatePointer(mouseX, mouseY);
        detailCloseButton.focused(closeControl.isFocused());
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

        // The manager screens' rule: a click drops a semantic control's
        // vanilla focus first; the routing below re-focuses the control it
        // lands on (accepted only).
        if (this.getFocused() instanceof SemanticActionControl) this.setFocused(null);

        // Detail modal gets first crack at clicks when open.
        if (detailOpenT > 0.5f && detailProject != null) {
            return handleDetailClick(mouseX, mouseY);
        }

        // Sidebar tab clicks (respecting the clip region + scroll offset).
        // Every hit routes through the tab's semantic control — the enabled
        // gate, exactly-one selection click, and sound ownership all live in
        // the action; the click is consumed either way (an already-selected
        // hit is the silent no-op; a release through would reach nothing
        // else in the sidebar). Header rows are not controls and never hit.
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
                SemanticActionControl control = tabControls[i];
                if (control != null && control.activateFromPointer(mouseX, mouseY, 0)) {
                    this.setFocused(control);
                } else if (control == null) {
                    selectCategory(tab); // pre-first-render click — cannot normally happen
                }
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
        // The install button routes through its semantic control (enabled
        // gate + exactly-once activation + sound + focus participation).
        // Clicks WITHIN the button rect are consumed even when the gate
        // rejects them (a "Resolving…" phase): an unconsumed rejected click
        // would fall through onto the card-body branch and open the detail
        // modal — the Profile-Create consume-but-inert rule.
        //
        // C-8 (the C-1 ClipBand rollout to this host): the walk is the SAME
        // culling walk the render runs (forEachVisibleCard — fully hidden
        // cards never enter it) and every hit additionally requires the
        // pointer inside the grid's clip band, so a partially visible card
        // is actionable only on the pixels the scissor actually paints —
        // never on its clipped-away slab above LIST_TOP or below the fold.
        List<ModrinthProject> list = results;
        int cols = columns();
        int gridLeft = gridLeft();
        int gridBottom = this.height - LIST_BOTTOM_PAD;
        ClipBand gridBand = new ClipBand(gridLeft - 4, LIST_TOP - 2,
                cols * (CARD_W + CARD_GAP) - CARD_GAP + 8, gridBottom - (LIST_TOP - 2));
        final boolean[] cardHit = {false};
        forEachVisibleCard(list, cols, gridLeft, gridBottom, (p, x, y) -> {
            if (cardHit[0]) return;
            int btnW = 80, btnH = 18;
            int btnX = x + CARD_W - btnW - THUMB_PAD;
            int btnY = y + CARD_H - btnH - 8;
            if (Widget.inBounds(mouseX, mouseY, btnX, btnY, btnW, btnH)
                    && gridBand.contains(mouseX, mouseY)) {
                CardState st = cardStates.get(p.projectId);
                if (st != null) {
                    SemanticActionControl control = st.cardPhaseControls[st.phase];
                    if (control != null && control.activateFromPointer(mouseX, mouseY, 0)) {
                        this.setFocused(control);
                    }
                }
                cardHit[0] = true;
                return;
            }
            if (Widget.inBounds(mouseX, mouseY, x, y, CARD_W, CARD_H)
                    && gridBand.contains(mouseX, mouseY)) {
                openDetail(p);
                cardHit[0] = true;
            }
        });
        if (cardHit[0]) return true;
        return super.mouseClicked(ev, dbl);
    }

    /**
     * Handles clicks inside the open pack-detail modal. The modal's buttons
     * route through their semantic controls (exactly-once activation + sound
     * + focus participation); the modal consumes every click it receives —
     * an accepted control click acts, everything else is inert — so no
     * obscured control behind the modal can ever receive it. C-8: geometry
     * derives from the SAME animated-position helpers the render paints
     * (detailModalX/Y/W/H), so during the open animation the hit rects sit
     * exactly on the painted buttons — never on their final-position ghosts.
     */
    private boolean handleDetailClick(double mouseX, double mouseY) {
        int modalW = detailModalW();
        int modalH = detailModalH();
        int modalX = detailModalX();
        int modalY = detailModalY();

        // Clicks outside the modal close it.
        if (mouseX < modalX || mouseX >= modalX + modalW
                || mouseY < modalY || mouseY >= modalY + modalH) {
            closeDetail();
            return true;
        }

        int pad = 18;
        // Close button — the semantic action IS closeDetail; a null control
        // (modal never rendered — cannot normally happen past the 0.5 gate)
        // keeps the legacy direct call.
        int cbW = 60, cbH = 22;
        int cbX = modalX + modalW - cbW - pad;
        int cbY = modalY + pad;
        if (Widget.inBounds(mouseX, mouseY, cbX, cbY, cbW, cbH)) {
            SemanticActionControl control = detailCloseControl;
            if (control == null) {
                closeDetail();
            } else {
                // The action is closeDetail itself, which clears focus —
                // no re-focus here (a control whose action tears its own
                // surface down must not end up focused).
                control.activateFromPointer(mouseX, mouseY, 0);
            }
            return true; // consumed either way
        }

        // Install button.
        int btnW = 130, btnH = 24;
        int btnX = modalX + pad;
        int btnY = modalY + modalH - pad - btnH;
        if (Widget.inBounds(mouseX, mouseY, btnX, btnY, btnW, btnH)) {
            if (detailProject != null) {
                CardState st = cardStates.get(detailProject.projectId);
                SemanticActionControl control = st == null ? null : st.modalPhaseControls[st.phase];
                if (control != null && control.activateFromPointer(mouseX, mouseY, 0)) {
                    this.setFocused(control);
                }
            }
            return true; // consumed either way (rejected/inert keeps parity)
        }
        return true; // consume clicks inside the modal
    }

    private void openDetail(ModrinthProject p) {
        detailProject = p;
        detailOpenTarget = true;
        // Focus containment (design language §13.3, keyboard side): the modal
        // is about to cover the screen, so nothing obscured may keep keyboard
        // focus (a focused search field would silently swallow typing). Focus
        // moves INTO the modal when its install control already exists; until
        // the first modal drive creates it (next frame), focus is null.
        this.clearFocus();
        CardState st = cardStates.get(p.projectId);
        if (st != null) {
            SemanticActionControl control = st.modalPhaseControls[st.phase];
            if (control != null) this.setFocused(control);
        }
    }

    private void closeDetail() {
        detailOpenTarget = false;
        // A closing modal's controls become unavailable; a covered or hidden
        // control must not remain meaningfully focused (§16).
        if (this.getFocused() instanceof SemanticActionControl) this.setFocused(null);
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
        // C-3 roving interceptor — before super (the invokespecial seam): a
        // focused category tab owns Up/Down, moving focus silently through
        // the visible ring instead of vanilla's unscoped spatial walk.
        if (SemanticControlGroup.rove(this.getFocused(), _kev, this::setFocused)) return true;
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
    public void removed() {
        super.removed();
        if (cardTpl != null) { cardTpl.dispose(); cardTpl = null; }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
