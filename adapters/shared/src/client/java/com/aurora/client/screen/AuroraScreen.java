package com.aurora.client.screen;

import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.config.AuroraConfig;

import com.aurora.client.module.Module;
import com.aurora.client.module.ModuleManager;
import com.aurora.client.screen.setting.FeatureSetting;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.GlassEditBox;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.component.ScrollbarChrome;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.component.ToggleSwitch;
import com.aurora.client.ui.component.Widget;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.interaction.MinecraftSemanticFeedback;
import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.interaction.SemanticControlGroup;
import com.aurora.client.ui.interaction.SemanticControlHost;
import com.aurora.client.ui.interaction.SemanticSound;
import com.aurora.client.ui.layout.ScreenLayout;
import com.aurora.client.ui.util.ClipBand;
import com.aurora.client.ui.util.UiDensity;
import com.aurora.client.ui.util.MaterialIconRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.ui.util.TimedTransition;
import com.aurora.client.ui.util.CubicBezier;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.HoverAnim;
import com.aurora.client.util.ScrollFade;
import com.aurora.client.util.SmoothScroll;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
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
 *
 * <p>§6 convention 6, structural (2026-09-11 — the last of the big screens):
 * {@code GlassSurface.beginGlassPass} → window/sidebar/layout/search/inline
 * glass plus parent-owned embedded neutral-tile material under the tracked
 * scissor → {@code GlassSurface.overlayDim} → cached chrome
 * blit + content. The static cache never holds glass: panels are texture
 * blits that bypass the fill-capture sink, and the capture block runs
 * before the pass's tint fills.
 *
 * <p>Phase C-1 (2026-09-19): one authoritative content viewport
 * ({@link #contentViewport()}) couples the content scissor, the tile/row
 * render cull, pointer hit-testing, tile hover, and focused-setting
 * keyboard availability — a pixel is interactive exactly when it is
 * visible. Previously the Settings-tab click walk was ungated and tile
 * hit-testing used unclamped partial rects, so controls scrolled outside
 * the viewport remained addressable (even above the window or below it,
 * over the dim).
 *
 * <p>Phase C-2b (2026-09-20): the screen's manual chrome joined the frozen
 * semantic contracts through the C-2 host. The sidebar Mods/Settings tabs
 * are NAVIGATION (the pack-tabs contract: persistent selection through the
 * stained tint — never the hover animator —, pointer-only
 * {@code HoverAnim.symmetric(140)}, Tab focus + the Button-family hairline,
 * Enter/Space through one {@link #selectCategory} path, one activation
 * click on a real destination change, silent consumed no-op on the selected
 * tab). Profiles is a plain ACTION (opens the profile manager; no
 * persistent selection). The 37 module cards are COMPOUND CARDS —
 * left-click/Enter toggles the module (exactly one click; the enabled
 * state is the persistent stained channel, independent of hover), the
 * right-click detail navigation stays pointer-specific (one click, parity
 * with the Settings header that opens the same destination), and
 * availability is the C-2 partial-visibility rule over the C-1 ClipBand.
 * The Settings tab's headers are navigation for the three entries with a
 * detail screen (custom_title has none — its header is grouping chrome
 * with a toggle only); the four header toggles are the Phase-B Boolean
 * adapter (the existing {@link ToggleSwitch} stays painter/state, the
 * semantic control owns the single activation). The layout pair's C-2b
 * deferral is CLOSED by C-5 — see the C-5 paragraph below.
 *
 * <p>Phase C-3 (2026-09-20): arrow-key roving through the shared
 * {@link SemanticControlGroup} primitive. The sidebar Mods/Settings tabs
 * form one horizontal ring (Left/Right move focus silently with wrap;
 * Profiles stays outside it — an action, not a peer); the Settings tab's
 * hosted Accent peers rove as their component-declared 5×2 grid (the
 * group travels with {@code interactionControls()}, so the Theme detail
 * screen gets the identical ring). Arrows never select — Enter/Space/click
 * remain the only selection paths (selection has side effects like the
 * per-tab scroll reset), and cross-axis keys fall through to vanilla.
 *
 * <p>The list/grid pair shares the segmented control's peer semantics and
 * keeps its 20?20 painter
 * (canonical 140 ms hover, focus hairline, one-click selection with a
 * silent no-op on the active layout, Left/Right ring).
 */
public class AuroraScreen extends Screen implements ThemedScreen, LayoutScreen {
    @Override public boolean unifiedTheme() { return true; }
    @Override public boolean parentGlassOnly() { return true; }


    /** §8.3 canonical hover duration — sidebar chrome, cards, headers alike (C-2b). */
    private static final long HOVER_MS = 140L;

    private final ScreenLayout modulesLayout = new ScreenLayout();
    private final ScreenLayout settingsLayout = new ScreenLayout();
    private final ScreenLayout chromeLayout = new ScreenLayout();
    private List<Module> laidOutModules;
    private List<FeatureMetadata> laidOutPage;
    private ClipBand laidOutModulesBand, laidOutSettingsBand;
    private boolean laidOutGrid;
    private double modulesScale, settingsScale;
    private long settingsFontKey;
    private int[] settingHeights = new int[0];
    private final java.util.Map<FeatureSetting, Integer> hiddenSettingOffsets = new java.util.IdentityHashMap<>();

    @Override public ScreenLayout contentLayout() { return selectedCategory == 0 ? modulesLayout : settingsLayout; }
    @Override public ScreenLayout fixedLayout() { return chromeLayout; }
    @Override public double layoutScroll() {
        return selectedCategory == 0 ? scrolls[0].current() : (float) scrolls[1].current();
    }
    private ClipBand measuredContent;
    private float BOX_W = com.aurora.client.ui.util.UiDensity.MAIN_WIDTH;
    private float BOX_H = com.aurora.client.ui.util.UiDensity.MAIN_HEIGHT;
    /** The frame from the last init; decides between the labelled sidebar and the icon rail. */
    private com.aurora.client.ui.util.UiDensity.Rect frameRect =
            com.aurora.client.ui.util.UiDensity.window(com.aurora.client.ui.util.UiDensity.MAIN_WIDTH,
                    com.aurora.client.ui.util.UiDensity.MAIN_HEIGHT);
    /** Sidebar icons in the rail, in nav order: Features, Settings, Profiles. */
    private static final String[] RAIL_ICONS = {"apps", "settings", "manage_accounts"};

    // Sidebar nav tabs: one pitch drives every tab's Y (22px tab + 4px gap),
    // with SIDEBAR_GROUP_GAP separating the navigation tabs from the Profiles action.
    private static final float TAB_FIRST_Y = com.aurora.client.ui.util.UiDensity.HEADER + com.aurora.client.ui.util.UiDensity.HEADER_BODY_GAP;
    private static final float TAB_PITCH = com.aurora.client.ui.util.UiDensity.TAB_PITCH;
    private static final float TAB_H = com.aurora.client.ui.util.UiDensity.TAB_H;
    private static final float TAB_W = com.aurora.client.ui.util.UiDensity.TAB_W;
    /** Toolbar icon buttons (list/grid) and the step between them. */
    private static final float TOOL = com.aurora.client.ui.util.UiDensity.TOOL, TOOL_STEP = TOOL + 4;
    private static final int SEARCH_GAP = 8;

    /**
     * Height of the muted "Click to configure" hint row drawn under a
     * settingsDetailOnly entry on the Settings tab (no entry sets the flag
     * since Miscellaneous moved to the Modules grid 2026-09-10, but the
     * walk support stays). The render, scroll-height, and click walks must
     * all count it.
     */
    private static final int SETTINGS_HINT_H = 14;

    /**
     * Blank band after each Settings-tab entry, before the next entry's
     * header (design language §5.3: spacing between groups must clearly
     * exceed the row-to-row gap inside a group, so entries read as
     * distinct clusters). The render, scroll-height, and click walks must
     * all use this.
     */
    private static final int SETTINGS_ENTRY_GAP = 18;
    /** Settings-tab entry header row (caption + master toggle). */
    private static final int SETTINGS_HEADER_H = 20;
    /** The one-line group summary under a settings heading (UI polish 2026-11-14). */
    private static final int SUMMARY_H = 14;
    /** Breathing room between a settings group's heading/summary and its first row. */
    private static final int SETTINGS_SUMMARY_GAP = 4;
    /** SettingRowLayout's outer pad (8 px); subtracting it aligns row labels with group headings and controls with the right edge. */
    private static final int SETTING_PAD = 8;
    private static int settingX(float boxX) { return (int) boxX - SETTING_PAD; }
    private static int settingW(float boxW) { return (int) boxW + 2 * SETTING_PAD; }

    /** A settings group's heading row plus its summary line, the height every walk advances by. */
    private int headerBlockH(FeatureMetadata m) {
        return SETTINGS_HEADER_H + (m.pageSummary == null || m.pageSummary.isEmpty() ? SETTINGS_SUMMARY_GAP : SUMMARY_H + SETTINGS_SUMMARY_GAP);
    }

    /** The summary fitted to the content column (the shared ellipsize memo caches it). */
    private String pageSummaryLine(FeatureMetadata m, Font tr) {
        if (m.pageSummary == null || m.pageSummary.isEmpty()) return null;
        // A feature whose mixin is not working says so in place of its summary (2026-11-23).
        String broken = com.aurora.client.health.FeatureHealth.headlineFor(m.id);
        return com.aurora.client.ui.util.AuroraFontRenderer.ellipsize(tr, broken != null ? broken : m.pageSummary, contentWidth() - 8, 1);
    }

    /** Gap between inline rows inside an entry. */
    private static final int SETTINGS_ROW_GAP = com.aurora.client.ui.util.UiDensity.ROW_GAP;

    private int selectedCategory = 0; // 0: Mods, 1: Settings
    /**
     * The Settings tab's page, chosen from the sub-tabs across its top
     * ({@link FeatureRegistry#settingsPages()}, 2026-11-12). The sub-tabs sit
     * in the toolbar row the Modules tab fills with its layout pair and
     * search field, so the content viewport is identical on both tabs.
     */
    private int selectedPage = 0;
    private final List<SettingsPages.Page> pages = FeatureRegistry.settingsPages();
    /** Every entry on every page, in page order: init, invalidation and teardown walk all of them. */
    private final List<FeatureMetadata> allPageEntries = pages.stream()
            .flatMap(page -> page.entries().stream()).toList();
    /** Sub-tab height and the gap between sub-tabs (the toolbar row's metrics). */
    private static final float PAGE_TAB_H = com.aurora.client.ui.util.UiDensity.TOOL, PAGE_TAB_GAP = 10;
    /** Thickness of the selected sub-tab's underline; its ends are always round, it is a mark, not chrome. */
    private static final float TAB_RULE = 1.5f;
    /** Horizontal label padding inside a sub-tab, per side (4 px aligns "Appearance" with group headings at mx + 4). */
    private static final int PAGE_TAB_PAD = 4;
    private boolean gridLayout = AuroraConfig.get().moduleGridView; // false: list, true: grid
    private final Map<String, List<String>> moduleLabelLines = new java.util.HashMap<>();
    private long moduleLabelVersion = Long.MIN_VALUE;

    private EditBox searchField;
    private String searchQuery = "";
    private static boolean devQueryApplied;

    // ---- Search (2026-11-12) ----
    // One index over the Mods features and the Settings-tab entries: a
    // feature's name (weight 3), the names of the settings inside it (2) and
    // its description (1), matched by the shared SearchMatcher with the
    // owner's typo tolerance. Settings-tab entries appear as tiles too
    // (synthetic Modules, never toggled from the grid); a tile found through
    // a setting names that setting on a second line.
    private com.aurora.client.search.SearchIndex<Module> searchIndex;
    /** Settings-tab entries as result tiles, by id. */
    private final Map<String, Module> settingsResults = new HashMap<>();
    private List<com.aurora.client.search.SearchIndex.Hit<Module>> lastHits;
    private List<Module> lastResults = List.of();
    /** The setting a result matched through, by tile id (absent when its name or description matched). */
    private final Map<String, String> resultVia = new HashMap<>();
    /** A result tile's second line: the matched setting, or a Settings entry's page. */
    private final Map<String, String> resultSubtitle = new HashMap<>();
    /** The Settings-tab row (a FeatureSetting) or entry header (a FeatureMetadata) a result opened, outlined while it fades. */
    private Object settingsReveal;
    private long settingsRevealStartMs;
    private static final long REVEAL_MS = 1600L;

    /**
     * One scroll per tab — 60 ms τ, snap 0.1, wheel step 30, grab-where-clicked
     * thumb that keeps easing toward the moving target during drags (only the
     * active tab's position advances, the inactive one stays frozen).
     */
    private final SmoothScroll[] scrolls = {
            new SmoothScroll(60.0).boundedMotion().setSnapEpsilon(0.1),
            null, // the selected Settings page's scroll (pageScrolls), set below
    };
    /** One scroll per Settings page; {@code scrolls[1]} is the selected page's, so each page keeps its position. */
    private final SmoothScroll[] pageScrolls = new SmoothScroll[pages.size()];
    {
        for (int i = 0; i < pageScrolls.length; i++) {
            pageScrolls[i] = new SmoothScroll(60.0).boundedMotion().setSnapEpsilon(0.1);
        }
        scrolls[1] = pageScrolls[selectedPage];
    }
    private FeatureSetting activeDragSetting = null;

    /**
     * C-1 keyboard-availability truth: whether the setting currently
     * holding the {@code FeatureSetting} registry focus shares a pixel
     * with the content viewport. Refreshed by the Settings-tab render walk
     * every frame (reset to false at frame start — the Modules tab and any
     * row scrolled out of the band read unavailable). The key/char/scroll
     * routing below gates on it: an off-viewport focused row is neither
     * visible nor pointer-actionable, so it must not keep receiving action
     * keys or the wheel either (DESIGN_LANGUAGE §13.3).
     */
    private boolean focusedSettingVisible = false;

    private final Map<String, ToggleSwitch> sectionToggles = new HashMap<>();

    // ---- Phase C-2b: screen-owned semantic chrome ----
    //
    // The manual controls this screen painted before C-2b (sidebar nav
    // tabs, Profiles action, module cards, Settings headers + header
    // toggles) each gained a SemanticActionControl. All of them are created
    // ONCE per screen instance in init (factories below are
    // identity-stable, so a resize's rebuild re-registers the same objects
    // — never duplicates) and stay long-lived for the screen's lifetime;
    // the render walks stamp bounds + ClipBand availability + pointer
    // state per frame, exactly the pack-browser tab discipline. Sound
    // ownership lives in the actions (see each factory); the host never
    // plays sounds.
    /** Sidebar navigation tabs (Mods=0, Settings=1) — the pack-nav contract. */
    private final SemanticActionControl[] tabControls = new SemanticActionControl[2];
    /**
     * C-3: the sidebar tabs rove Left/Right as one horizontal ring. Profiles
     * is deliberately NOT a member — it is a plain action (C-2b ruling), not
     * a navigation peer, and grouping it would make arrows "select-ish" over
     * a control that opens another screen.
     */
    private final SemanticControlGroup sidebarTabGroup = SemanticControlGroup.horizontal();
    /** The Profiles chip — a plain action (opens the profile manager). */
    private SemanticActionControl profilesControl;
    /**
     * The list/grid layout pair (C-5) — the same peer-selection family as
     * {@code SegmentedControl}, adopted through the peer semantic primitive
     * rather than the text-segment component (the 20×20 vector-icon geometry
     * is not a text track; reusing the painter would be a visual redesign).
     * The manual painter stays; hover becomes the canonical pointer-only
     * symmetric 140 ms, focus gains the Button-family hairline, and clicks
     * route through the actions — one ACTIVATION on a genuine change, silent
     * consumed no-op on the already-selected layout.
     */
    private final SemanticActionControl[] layoutControls = new SemanticActionControl[2];
    /** C-5: the pair roves Left/Right as one horizontal ring. */
    private final SemanticControlGroup layoutGroup = SemanticControlGroup.horizontal();
    /**
     * The Settings tab's sub-tabs: navigation peers under the same contract
     * as the sidebar tabs (persistent selection through the stained tint,
     * pointer-only canonical hover, the Button-family focus hairline, one
     * activation click on a real change, silent no-op on the selected page).
     */
    private final SemanticActionControl[] pageControls = new SemanticActionControl[pages.size()];
    /** The sub-tabs rove Left/Right as one horizontal ring. */
    private final SemanticControlGroup pageGroup = SemanticControlGroup.horizontal();
    private final HoverAnim[] pageHovers = new HoverAnim[pages.size()];
    /** Unified-material hover keys, built once (no per-frame string concatenation). */
    private final String[] pagePilotIds = new String[pages.size()];
    {
        for (int i = 0; i < pageHovers.length; i++) {
            pageHovers[i] = HoverAnim.symmetric(HOVER_MS);
            pagePilotIds[i] = "page" + i;
        }
    }
    private final boolean[] pageGlass = new boolean[pages.size()];
    /** Sub-tab geometry and fitted labels, rebuilt only when the width or the font changes. */
    private final String[] pageTabLabel = new String[pages.size()];
    private long pageTabVersion = Long.MIN_VALUE;
    private ClipBand chromeFrame;
    private int chromeCategory = -1;
    /** Canonical hover animators for the pair — pointer-only (C-5). */
    private final HoverAnim[] layoutHovers = {
            HoverAnim.symmetric(HOVER_MS),
            HoverAnim.symmetric(HOVER_MS),
    };
    /** One control per module card, keyed by stable module id (finite: the ModuleManager set). */
    private final Map<String, SemanticActionControl> tileControls = new HashMap<>();
    /** Settings-header navigation, keyed by entry id — only entries with a detail screen. */
    private final Map<String, SemanticActionControl> headerNavControls = new HashMap<>();
    /** The header toggles (entries that have a switch: Theme, Custom Title), keyed by entry id. */
    private final Map<String, SemanticActionControl> headerToggleControls = new HashMap<>();
    /** Canonical hover animators — pointer-only targets, keyed by stable identity (never list position). */
    private final HoverAnim[] tabHovers = {
            HoverAnim.symmetric(HOVER_MS),
            HoverAnim.symmetric(HOVER_MS),
    };
    private final HoverAnim profilesHover = HoverAnim.symmetric(HOVER_MS);
    private final Map<String, HoverAnim> tileHovers = new HashMap<>();
    private static final String[] PILOT_TABS={"tab0","tab1"}, PILOT_LAYOUTS={"layout0","layout1"};
    private final Map<String,TimedTransition> pilotHovers=new HashMap<>();
    private final Map<String,TimedTransition> pilotHeaderHovers=new HashMap<>();
    private float pilotHover(String id,boolean hovered) {
        return pilotHover(pilotHovers,id,hovered);
    }
    private float pilotHover(Map<String,TimedTransition> animators,String id,boolean hovered) {
        return animators.computeIfAbsent(id,k->new TimedTransition(120,CubicBezier.EASE,0))
                .update(hovered ? 1 : 0,System.nanoTime());
    }
    private final Map<String, HoverAnim> headerHovers = new HashMap<>();

    /** The module card's hover animator (lazily created once per module, long-lived). */
    private HoverAnim tileHover(String moduleId) {
        return tileHovers.computeIfAbsent(moduleId, k -> HoverAnim.symmetric(HOVER_MS));
    }

    /** The settings header's hover animator (lazily created once per entry, long-lived). */
    private HoverAnim headerHover(String entryId) {
        return headerHovers.computeIfAbsent(entryId, k -> HoverAnim.symmetric(HOVER_MS));
    }

    /**
     * Phase C-2 host for the semantic contracts the inline Settings
     * components already expose. Registration follows registry row order;
     * component semantics/layout remain outside this helper.
     */
    private final SemanticControlHost semanticHost = new SemanticControlHost(
            control -> this.addWidget(control),
            control -> this.removeWidget(control),
            this::getFocused,
            this::setFocused);
    /** Stable per-setting views captured during init; never allocated in the frame path. */
    private final Map<FeatureSetting, List<SemanticActionControl>> hostedSettingControls =
            new IdentityHashMap<>();


    public AuroraScreen() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        var frame = com.aurora.client.ui.util.UiDensity.mainFrame(this.width, this.height);
        var body = com.aurora.client.ui.util.UiDensity.mainContent(frame);
        measuredContent = new ClipBand(body.x(), body.y(), body.width(), body.height());
        BOX_W = frame.width(); BOX_H = frame.height();
        frameRect = frame;
        super.init();
        semanticHost.beginRebuild();
        scrolls[0].resetClock();
        for (SmoothScroll s : pageScrolls) s.resetClock();
        sectionToggles.clear();

        for (FeatureMetadata m : allPageEntries) {
            if (!m.noSettingsToggle) {
                sectionToggles.put(m.id, new ToggleSwitch(m::isEnabled, m::setEnabled));
            }
            com.aurora.client.screen.setting.SectionHeaderSetting.bind(m.settings, m.id);
            com.aurora.client.screen.setting.SectionHeaderSetting.markLeading(m.settings);
        }

        searchField = new EditBox(this.font, 0, 0, 202, (int) TOOL, Component.literal("Search..."));
        searchField.setHint(Component.literal("Search features and settings"));
        searchField.setBordered(false);
        searchField.setTextColor(0xFFFFFFFF);
        searchField.setMaxLength(64);
        if (!devQueryApplied) {
            // Dev-only verification seam: posted keystrokes never reach the
            // game, so a test boot pre-fills the query once per session.
            devQueryApplied = true;
            String dev = System.getProperty("aurora.dev.searchQuery");
            if (dev != null) searchQuery = dev;
        }
        // A re-init (window resize) keeps the query the results are filtered by.
        searchField.setValue(searchQuery);
        searchField.setResponder(s -> this.searchQuery = s);
        // Explicit traversal position: the vanilla search field is first.
        // It is visible/focusable on Modules and hidden on Settings.
        this.addWidget(searchField);
        if (selectedCategory == 0) focusSearchIfInstant();

        // C-2b screen-owned chrome: the two navigation tabs, the Profiles
        // action, the layout pair (C-5 — the last deferred peer-selection
        // surface on this screen), all 37 module cards, and the Settings
        // headers + header toggles are created once and registered in visual
        // order (sidebar first; the layout pair tops the content column
        // before the module cards in ModuleManager order; the Settings walk
        // interleaves each entry's header chrome with its rows further
        // down). The factories are identity-stable, so a resize's
        // beginRebuild/finishRebuild re-registers these same objects without
        // accumulating children.
        tabControls[0] = tabControl(0);
        tabControls[1] = tabControl(1);
        profilesControl = profilesActionControl();
        layoutControls[0] = layoutControl(0);
        layoutControls[1] = layoutControl(1);
        // The Settings sub-tabs share the toolbar row with the layout pair;
        // only one set is ever available (each tab's walk marks its own).
        for (int i = 0; i < pageControls.length; i++) pageControls[i] = pageControl(i);
        for (Module m : ModuleManager.getInstance().getModules()) {
            tileControls.put(m.id, tileControl(m));

        }

        // HOSTABLE_NOW only: ask every inline component on every Settings
        // page for the semantic controls it already owns, in page order
        // (Accent's peers and the Text & Fonts
        // enums on Appearance; the Display Settings toggles, enums, HUD
        // Editor key and Copy Diagnostics on the other pages). Controls on
        // pages not shown stay unavailable through the availability sweep.
        // Segmented peers materialize per SEGMENT, one control each.
        hostedSettingControls.clear();
        for (FeatureMetadata metadata : allPageEntries) {
            // Header chrome precedes the entry's rows in traversal order
            // (visual top-to-bottom): the navigation control for entries
            // with a detail screen, then the header toggle for entries
            // that have one.
            if (metadata.hasDetail()) {
                headerNavControls.put(metadata.id, headerNavControl(metadata));
            }
            if (!metadata.noSettingsToggle) {
                headerToggleControls.put(metadata.id, headerToggleControl(metadata));
            }
            for (FeatureSetting setting : metadata.settings) {
                List<SemanticActionControl> controls = List.copyOf(setting.controlsWithReset());
                hostedSettingControls.put(setting, controls);
                for (SemanticActionControl control : controls) {
                    semanticHost.register(control);
                }
                setting.onInteractionAvailabilityChanged(false);
            }
        }
        semanticHost.finishRebuild();
    }

    // ------------------------------------------------------------------
    //  C-2b control factories — identity-stable (a second init re-uses the
    //  same instances), registered at creation. All live above render() so
    //  no registration can ever sit in the frame path.
    // ------------------------------------------------------------------

    /**
     * The sidebar navigation tab — the frozen pack-navigation contract.
     * Selected stays focusable (a disabled-style gate would strand Tab
     * traversal on the current tab) and narrates {@code Selected}; the
     * already-selected no-op lives in the behavior with the sound.
     */
    private SemanticActionControl tabControl(int i) {
        SemanticActionControl control = tabControls[i];
        if (control != null) return control;
        String name = i == 0 ? "Features" : "Settings";
        control = new SemanticActionControl(new SemanticAction(
                Component.literal(name),
                () -> Component.literal("Switches the main screen to the " + name + " tab."),
                () -> Component.literal(selectedCategory == i ? "Selected" : "Not selected"),
                () -> true,
                () -> {
                    if (selectedCategory == i) return; // reactivating the selected tab: silent consumed no-op
                    MinecraftSemanticFeedback.INSTANCE
                            .play(SemanticSound.ACTIVATION);
                    selectCategory(i);
                },
                SemanticSound.NONE, true, true),
                MinecraftSemanticFeedback.INSTANCE,
                null, // no press animation — the selection transition is the feedback
                SemanticActionControl.PointerRouting.MANUAL);
        tabControls[i] = control;
        sidebarTabGroup.attach(control); // C-3 ring; idempotent across re-inits
        semanticHost.register(control);
        return control;
    }

    /**
     * The Profiles chip — an ordinary ACTION (opens the profile manager; it
     * holds no persistent selection, so the navigation family would
     * misclassify it). Manual painter + the button-like action contract:
     * canonical hover, focus, Enter/Space, narration, one activation click.
     */
    private SemanticActionControl profilesActionControl() {
        if (profilesControl != null) return profilesControl;
        profilesControl = new SemanticActionControl(SemanticAction.button(
                Component.literal("Profiles"),
                () -> Component.literal("Opens the profile manager."),
                null,
                () -> true,
                () -> {
                    if (this.minecraft != null) com.aurora.client.adapter.NativeUi.setScreen(this.minecraft, new ProfileManagerScreen(this));
                }),
                MinecraftSemanticFeedback.INSTANCE,
                null, // no press animation — the screen transition is the feedback
                SemanticActionControl.PointerRouting.MANUAL);
        semanticHost.register(profilesControl);
        return profilesControl;
    }

    /**
     * One layout peer (0 = List, 1 = Grid) — the peer-selection value
     * contract on the existing manual painter (see {@link #layoutControls}).
     * The represented value is the {@code gridLayout} field, read live for
     * the Selected state (no stored index that could disagree); selecting
     * the already-active layout is a silent consumed no-op (no scroll reset,
     * no rebuild — exactly the baseline's behavior, now with the sound
     * discipline made explicit).
     */
    private SemanticActionControl layoutControl(int i) {
        SemanticActionControl control = layoutControls[i];
        if (control != null) return control;
        String name = i == 0 ? "List Layout" : "Grid Layout";
        final boolean grid = i == 1;
        control = new SemanticActionControl(new SemanticAction(
                Component.literal(name),
                () -> Component.literal("Switches the Modules tab between list and grid layouts."),
                () -> Component.literal(gridLayout == grid ? "Selected" : "Not selected"),
                () -> true,
                () -> {
                    if (gridLayout == grid) return; // reactivating the active layout: silent consumed no-op
                    MinecraftSemanticFeedback.INSTANCE
                            .play(SemanticSound.ACTIVATION);
                    gridLayout = grid;
                    AuroraConfig.get().moduleGridView = grid;
                    AuroraConfig.save();
                },
                SemanticSound.NONE, true, true),
                MinecraftSemanticFeedback.INSTANCE,
                null, // no press animation — the selection transfer is the feedback
                SemanticActionControl.PointerRouting.MANUAL);
        layoutControls[i] = control;
        layoutGroup.attach(control); // C-5 ring; idempotent across re-inits
        semanticHost.register(control);
        return control;
    }

    /**
     * One Settings sub-tab — the sidebar tabs' navigation contract, one
     * level down: selected stays focusable and narrates {@code Selected},
     * reactivating the selected page is a silent consumed no-op, and a real
     * change plays the one activation click.
     */
    private SemanticActionControl pageControl(int i) {
        SemanticActionControl control = pageControls[i];
        if (control != null) return control;
        String name = pages.get(i).label();
        control = new SemanticActionControl(new SemanticAction(
                Component.literal(name),
                () -> Component.literal("Shows the " + name + " settings."),
                () -> Component.literal(selectedPage == i ? "Selected" : "Not selected"),
                () -> true,
                () -> {
                    if (selectedPage == i) return; // reactivating the selected page: silent consumed no-op
                    MinecraftSemanticFeedback.INSTANCE
                            .play(SemanticSound.ACTIVATION);
                    selectSettingsPage(i);
                },
                SemanticSound.NONE, true, true),
                MinecraftSemanticFeedback.INSTANCE,
                null, // no press animation — the selection transition is the feedback
                SemanticActionControl.PointerRouting.MANUAL);
        pageControls[i] = control;
        pageGroup.attach(control); // roving ring; idempotent across re-inits
        semanticHost.register(control);
        return control;
    }

    /**
     * One module card — a COMPOUND CARD, not a plain button. The primary
     * action (left click / Enter / Space) toggles the module with exactly
     * one activation click; the module's enabled state is the persistent
     * represented channel (the stained tint / accent wash), never hover.
     * The right-click detail navigation stays pointer-specific (no keyboard
     * chord invented in C-2b) and plays its own single click at the site.
     */
    private SemanticActionControl tileControl(Module m) {
        SemanticActionControl control = tileControls.get(m.id);
        if (control != null) return control;
        control = isSettingsResult(m)
                ? new SemanticActionControl(SemanticAction.button(
                        Component.literal(m.name),
                        () -> Component.literal("Opens it on the Settings tab."),
                        () -> Component.empty(),
                        () -> true,
                        () -> openSettingsResult(m)),
                        MinecraftSemanticFeedback.INSTANCE,
                        null,
                        SemanticActionControl.PointerRouting.MANUAL)
                : new SemanticActionControl(SemanticAction.button(
                Component.literal(m.name),
                () -> Component.literal(m.hoverText()),
                () -> Component.literal(m.isEnabled() ? "Enabled" : "Disabled"),
                () -> true,
                m::toggle),
                MinecraftSemanticFeedback.INSTANCE,
                null, // no press animation — the state transition is the feedback
                SemanticActionControl.PointerRouting.MANUAL);
        tileControls.put(m.id, control);
        semanticHost.register(control);
        return control;
    }

    /**
     * A Settings-header navigation control — opens the entry's detail
     * screen (a subordinate destination, the same one the module cards'
     * right-click reaches). custom_title has no detail screen and gets no
     * control: its header is grouping chrome.
     */
    private SemanticActionControl headerNavControl(FeatureMetadata m) {
        SemanticActionControl control = headerNavControls.get(m.id);
        if (control != null) return control;
        control = new SemanticActionControl(SemanticAction.button(
                Component.literal(m.displayName),
                () -> Component.literal("Opens the " + m.displayName + " settings."),
                null,
                () -> true,
                () -> {
                    if (this.minecraft != null) {
                        com.aurora.client.adapter.NativeUi.setScreen(this.minecraft, FeatureDetailScreen.destination(this, m));
                    }
                }),
                MinecraftSemanticFeedback.INSTANCE,
                null, // no press animation — the navigation transition is the feedback
                SemanticActionControl.PointerRouting.MANUAL);
        headerNavControls.put(m.id, control);
        semanticHost.register(control);
        return control;
    }

    /**
     * A header toggle — the Phase-B Boolean adapter: the existing
     * {@link ToggleSwitch} stays the painter/state mechanism (its own
     * canonical hover wash + thumb press pulse), and this control owns the
     * ONE activation source (pointer, Enter, Space all converge on
     * toggle + save, exactly the {@code BooleanSetting} wiring). The
     * switch itself is never clicked directly, so it cannot double-toggle.
     */
    private SemanticActionControl headerToggleControl(FeatureMetadata m) {
        SemanticActionControl control = headerToggleControls.get(m.id);
        if (control != null) return control;
        control = new SemanticActionControl(SemanticAction.button(
                Component.literal(m.displayName),
                () -> Component.literal("Turns " + m.displayName + " on or off."),
                () -> Component.literal(m.isEnabled() ? "On" : "Off"),
                () -> true,
                () -> {
                    ToggleSwitch t = sectionToggles.get(m.id);
                    if (t != null) t.toggle();
                    com.aurora.client.config.AuroraConfig.save();
                }),
                MinecraftSemanticFeedback.INSTANCE,
                null,
                SemanticActionControl.PointerRouting.MANUAL);
        headerToggleControls.put(m.id, control);
        semanticHost.register(control);
        return control;
    }

    /**
     * The ONE tab-selection path (pointer, Enter, Space converge here).
     * Reactivating the already-selected tab never reaches this method's
     * body — the action's no-op guard consumes it silently first. A real
     * change switches immediately (no rebuild/reset: the per-tab scroll
     * positions are preserved by construction) and invalidates every
     * hosted control until the new tab's render walk re-marks it, so no
     * hidden control can retain focus or activate.
     */
    private void selectCategory(int i) {
        if (selectedCategory == i) return;
        if (i == 0) {
            // Leaving the Settings tab — notify its rows now (capture
            // families' teardown hook: the HUD Editor key row stops
            // listening).
            for (FeatureMetadata metadata : allPageEntries) {
                for (FeatureSetting setting : metadata.settings) {
                    setting.onInteractionAvailabilityChanged(false);
                }
            }
        }
        selectedCategory = i;
        semanticHost.deactivateAll();
        focusedSettingVisible = false;
        if (i == 0) focusSearchIfInstant();
    }

    /**
     * The one page-selection path (pointer, Enter, Space converge here
     * through the sub-tab action). The page being left notifies its rows,
     * every hosted control is invalidated until the new page's walk
     * re-marks it, and the new page resumes its own scroll position.
     */
    private void selectSettingsPage(int i) {
        if (selectedPage == i) return;
        for (FeatureMetadata metadata : pageEntries()) {
            for (FeatureSetting setting : metadata.settings) {
                setting.onInteractionAvailabilityChanged(false);
            }
        }
        FeatureSetting.clearFocus();
        selectedPage = i;
        pageScrolls[i].touchClock(); // a fresh dt, not the time spent on other pages
        scrolls[1] = pageScrolls[i];
        semanticHost.deactivateAll();
        focusedSettingVisible = false;
    }

    /** The entries on the selected Settings page, in order — the one list every Settings walk reads. */
    private List<FeatureMetadata> pageEntries() {
        return pages.get(selectedPage).entries();
    }

    /**
     * Lays the sub-tabs out left to right in the toolbar row, each as wide
     * as its label plus padding. When they cannot all fit, the padding
     * halves; when they still cannot, they share the row equally and a
     * label that does not fit ends in an ellipsis. Labels stay centred
     * throughout. The toolbar retains the arranged boxes and fitted labels.
     */
    private void arrangeChrome() {
        long version = FeatureSetting.compactMeasurementKey();
        if (chromeFrame == measuredContent && chromeCategory == selectedCategory && version == pageTabVersion) return;
        chromeFrame = measuredContent;
        chromeCategory = selectedCategory;
        pageTabVersion = version;
        var viewport = new ClipBand(0, 0, this.width, this.height);
        chromeLayout.begin(viewport);
        for (int index = 0; index < 3; index++) {
            String id = index == 2 ? "toolbar/profiles" : "toolbar/category/" + index;
            float left = boxX() + UiDensity.FRAME_EDGE + (narrowNavigation() ? index * (navW() + 4) : 0);
            float top = boxY() + (narrowNavigation() ? TAB_FIRST_Y
                    : TAB_FIRST_Y + index * TAB_PITCH + (index == 2 ? UiDensity.SIDEBAR_GROUP_GAP : 0));
            chromeLayout.place(id, id, left, top, navW(), TAB_H, true);
        }
        float left = boxX() + toolbarLeft();
        float top = boxY() + UiDensity.headerY(TOOL);
        // The toolbar ends where the scrollbar does, at the frame room.
        // The search field keeps SEARCH_GAP of air past the view toggles (council 2026-11-18: its
        // placeholder started flush against the grid button).
        int searchWidth = Math.max(1, (int) BOX_W - toolbarLeft() - (int) (2 * TOOL_STEP) - SEARCH_GAP - UiDensity.FRAME_EDGE);
        if (selectedCategory == 0) {
            chromeLayout.place("toolbar/list", "toolbar/list", left, top, TOOL, TOOL, true);
            chromeLayout.place("toolbar/grid", "toolbar/grid", left + TOOL_STEP, top, TOOL, TOOL, true);
            var search = chromeLayout.place("toolbar/search", "toolbar/search", Math.round(left + 2 * TOOL_STEP + SEARCH_GAP), Math.round(top), searchWidth, TOOL, true);
            searchField.setX((int) search.x());
            searchField.setY((int) search.top());
            searchField.setWidth((int) search.width());
        } else {
            arrangePageTabs(left, top, (int) (2 * TOOL_STEP) + SEARCH_GAP + searchWidth);
        }
        searchField.visible = selectedCategory == 0;
        chromeLayout.finish(viewport, 0);
    }

    private void arrangePageTabs(float left, float top, int row) {
        int n = pages.size();
        float gaps = PAGE_TAB_GAP * (n - 1);
        float labels = 0;
        for (int i = 0; i < n; i++) labels += this.font.width(pages.get(i).label());
        // Full padding, then half padding, before any label is shortened.
        int pad = labels + gaps + n * 2 * PAGE_TAB_PAD <= row ? PAGE_TAB_PAD
                : labels + gaps + n * PAGE_TAB_PAD <= row ? PAGE_TAB_PAD / 2 : -1;
        boolean fits = pad >= 0;
        float equal = Math.max(1, (row - gaps) / n);
        float x = 0;
        for (int i = 0; i < n; i++) {
            String label = pages.get(i).label();
            float w = fits ? this.font.width(label) + 2 * pad : equal;
            String id = "toolbar/page/" + i;
            chromeLayout.place(id, id, left + x, top, w, PAGE_TAB_H, true);
            // Squeezed tabs keep half the padding on each side for the label.
            pageTabLabel[i] = fits ? label
                    : com.aurora.client.ui.util.AuroraFontRenderer.ellipsize(this.font, label, Math.max(1, (int) w - PAGE_TAB_PAD), 1);
            x += w + PAGE_TAB_GAP;
        }
    }

    private float boxX() { return (float) Math.floor((this.width - BOX_W) / 2.0f); }
    private float boxY() { return (float) Math.floor((this.height - BOX_H) / 2.0f); }

    private float mainX() { return boxX() + UiDensity.contentLeft(frameRect); }
    private ClipBand contentViewport() {
        return selectedCategory == 1 && laidOutSettingsBand == measuredContent
                && laidOutPage == pageEntries() ? settingsLayout.viewport() : measuredContent;
    }

    private boolean narrowNavigation() { return BOX_W < 320; }
    /** A shrunk window shows the sidebar as icon squares and gives the content the room (owner, 2026-11-23). */
    private boolean railNavigation() { return UiDensity.railNavigation(frameRect); }
    private float navW() { return railNavigation() ? UiDensity.RAIL_W : TAB_W; }
    /** Toolbar start, relative to the frame: past the sidebar, or with the rail just past the AURORA title. */
    private int toolbarLeft() {
        if (!railNavigation()) return UiDensity.SIDEBAR + UiDensity.INSET;
        int titleRight = UiDensity.FRAME_EDGE + 8 + this.font.width(com.aurora.client.ui.util.AuroraFontRenderer.role(
                "AURORA", com.aurora.client.ui.util.AuroraFontRenderer.Role.TITLE));
        return Math.max(UiDensity.contentLeft(frameRect), titleRight + UiDensity.INSET);
    }
    private float navX(int index) { return chromeLayout.at(index).x(); }
    private float navY(int index) { return chromeLayout.at(index).top(); }
    private int contentWidth() { return contentViewport().width; }
    private int gridColumns() { return com.aurora.client.ui.util.UiDensity.columns(contentWidth()); }

    private float viewTop() { return contentViewport().y; }
    private float viewBot() { return contentViewport().yEnd(); }

    @Override
    public void onClose() {
        if (this.minecraft != null) com.aurora.client.adapter.NativeUi.setScreen(this.minecraft, null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Backdrop by context. Live world: skip vanilla's background sandwich
     * (full-screen blur + dark gradient) — the glass panels must sample the
     * LIVE world, not an already-darkened, already-blurred backdrop (same
     * reasoning as FeatureDetailScreen's no-op override on the Theme pilot).
     * No world (opened from the title screen, or from the pause-less menu
     * flow): the shared menu-panorama backdrop — panorama drawn and declared
     * capturable, so the screen's glass surfaces sample it exactly like the
     * title screen's buttons (2026-09-12; before this, vanilla's menu
     * background drew, the guard correctly declined, and the screen fell
     * back flat with no frosted glass at all under Frosted). The helper's
     * {@code false} (a live world after all) falls through to vanilla's
     * background for the flat look, as before glass.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (liveWorldBackdrop()) return;
        if (GlassSurface.renderMenuPanorama(this, g, delta)) return;
        super.extractBackground(g, mouseX, mouseY, delta);
    }

    /**
     * True when the main render target holds a live world — i.e. the glass
     * capture source is valid without a menu-backdrop declaration. Mirrors
     * {@code BlurPanelRenderer}'s own menu-context guard so this screen
     * never asks for glass (and never drops the vanilla backdrop) in a
     * context where glass cannot engage.
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
        return (Math.round(a * 255f) << 24) | (MaterialSurface.color(t) & 0x00FFFFFF);
    }

    private void refreshContentLayout() {
        boolean scoped = com.aurora.client.ui.util.AuroraFontRenderer.enterWindowScope(this);
        try {
            arrangeChrome();
            if (selectedCategory == 0) arrangeModules(filteredModules());
            else arrangeSettings();
        } finally {
            if (scoped) com.aurora.client.ui.util.AuroraFontRenderer.exitWindowScope();
        }
    }

    private void arrangeModules(List<Module> modules) {
        double scale = this.minecraft.getWindow().getGuiScale();
        if (laidOutModules == modules && laidOutModulesBand == measuredContent && laidOutGrid == gridLayout
                && modulesScale == scale) return;
        laidOutModules = modules;
        laidOutModulesBand = measuredContent;
        laidOutGrid = gridLayout;
        modulesScale = scale;
        int columns = gridLayout ? gridColumns() : 1;
        int width = gridLayout ? UiDensity.gridCardWidth(measuredContent.width, columns) : measuredContent.width;
        int height = gridLayout ? UiDensity.gridCardHeight(width) : UiDensity.LIST_HEIGHT;
        int rowGap = gridLayout ? UiDensity.GRID_GAP : UiDensity.LIST_GAP;
        int colGap = gridLayout ? UiDensity.gridColumnGap(measuredContent.width, columns, width) : rowGap;
        float left = mainX() + (gridLayout ? UiDensity.gridOffsetX(measuredContent.width, columns, width, colGap) : 0);
        modulesLayout.begin(measuredContent);
        for (int index = 0; index < modules.size(); index++) {
            Module module = modules.get(index);
            modulesLayout.cell(module, module.id, index, columns, left, measuredContent.y, width, height, colGap, rowGap);
        }
        modulesLayout.finish(measuredContent, UiDensity.extent((modules.size() + columns - 1) / columns, height, rowGap));
    }

    private void arrangeSettings() {
        List<FeatureMetadata> entries = pageEntries();
        double scale = this.minecraft.getWindow().getGuiScale();
        long fontKey = FeatureSetting.compactMeasurementKey();
        boolean changed = laidOutPage != entries || laidOutSettingsBand != measuredContent
                || settingsFontKey != fontKey || settingsScale != scale;
        int width = settingW(measuredContent.width);
        int count = 0, track = 0;
        for (int index = 0; index < entries.size(); index++) {
            FeatureMetadata entry = entries.get(index);
            if (entry.settingsDetailOnly) continue;
            count += entry.settings.size();
            track = Math.max(track, FeatureSetting.pageTrackWidth(entry.settings));
        }
        if (settingHeights.length < count) settingHeights = new int[count];
        int row = 0;
        for (int index = 0; index < entries.size(); index++) {
            FeatureMetadata entry = entries.get(index);
            if (entry.settingsDetailOnly) continue;
            com.aurora.client.screen.setting.SectionHeaderSetting.markLeading(entry.settings);
            for (int settingIndex = 0; settingIndex < entry.settings.size(); settingIndex++) {
                FeatureSetting setting = entry.settings.get(settingIndex);
                int height = -1;
                if (setting.isVisible()) {
                    setting.pageTrack(track);
                    setting.measureCompact(width);
                    height = setting.height();
                }
                changed |= settingHeights[row] != height;
                settingHeights[row++] = height;
            }
        }
        if (!changed) return;
        laidOutPage = entries;
        laidOutSettingsBand = measuredContent;
        settingsFontKey = fontKey;
        settingsScale = scale;
        settingsLayout.begin(measuredContent);
        hiddenSettingOffsets.clear();
        int offset = 0, fit = 0;
        boolean overflow = false;
        row = 0;
        for (int index = 0; index < entries.size(); index++) {
            FeatureMetadata entry = entries.get(index);
            int header = headerBlockH(entry);
            settingsLayout.place(entry, entry.id, mainX(), measuredContent.y + offset,
                    measuredContent.width, header + (entry.settingsDetailOnly ? SETTINGS_HINT_H : 0), true);
            offset += header;
            if (entry.settingsDetailOnly) {
                offset += SETTINGS_HINT_H;
                overflow |= offset > measuredContent.height;
                if (!overflow) fit = offset;
            } else for (int settingIndex = 0; settingIndex < entry.settings.size(); settingIndex++) {
                FeatureSetting setting = entry.settings.get(settingIndex);
                int height = settingHeights[row++];
                if (height < 0) {
                    hiddenSettingOffsets.put(setting, offset);
                    continue;
                }
                int nextTop = settingsLayout.stack(setting, entry.id + "/" + settingIndex, mainX(),
                        measuredContent.y + offset, measuredContent.width, height, SETTINGS_ROW_GAP, true);
                overflow |= offset + height > measuredContent.height;
                if (!overflow) fit = offset + height;
                offset = nextTop - measuredContent.y;
            }
            if (index + 1 < entries.size()) offset += SETTINGS_ENTRY_GAP;
        }
        ClipBand viewport = overflow && fit > 0 ? new ClipBand(measuredContent.x, measuredContent.y, measuredContent.width, fit)
                : measuredContent;
        settingsLayout.finish(viewport, offset);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        refreshContentLayout();
        tickSmoothScroll();
        semanticHost.beginAvailabilitySweep();
        // Frame-start reset: focused-setting keyboard availability is
        // re-derived by the Settings walk below (renderSettingsLive); on
        // the Modules tab no inline row is visible, so the registry focus
        // (if any survives a tab switch) reads unavailable.
        focusedSettingVisible = false;
        // C-8: publish the content viewport as this frame's host band so
        // floating row geometry (EnumSetting's popup) resolves placement
        // against the same clip the scissor, cull, and hit-tests use.
        FeatureSetting.setHostBand(contentViewport());

        // ---- 1. Glass pass — every glass surface paints BEFORE the dim ----
        // (§6 convention 6, structural: the ManagerListScreen skeleton, and
        // the last of the big screens to adopt it — 2026-09-10.) The window
        // is the DEPRESSED surface (main containers read as recessed); its
        // tint is the ordinary WINDOW_FILL fill, whose alpha already carries
        // the theme's Background Opacity — still exactly one application
        // point. The dim veils the panel and its surroundings equally. When
        // the renderer declines (menu context with no world, screenshot
        // suppression, failure), the bevel frame keeps the same cached face.
        GlassSurface.beginGlassPass();
        float radius = AuroraTheme.RADIUS;
        GlassSurface.container(g, boxX(), boxY(), BOX_W, BOX_H, radius);

        // Chrome + tab materials. Tiles and the Settings tab's inline rows
        // paint under the tracked scissor so their deferred rims keep the list clip.
        List<Module> mods = selectedCategory == 0 ? laidOutModules : List.of();
        paintGlassPass(g, mods);

        // ---- 2. Dim — closes the glass pass, veils every surface like it ----
        // veils the world, flushes the deferred rim finishes, and from here
        // on any glass body paint is reported as an ordering violation.
        GlassSurface.overlayDim(g, this.width, this.height);

        // The window frame: one cached bevel blit, a content layer above the dim.
        MaterialSurface.parent(g, boxX(), boxY(), BOX_W, BOX_H, radius, MaterialSurface.frameInset(radius));

        renderLive(g, mods, mouseX, mouseY, delta);

        // Availability has now been derived from this frame's component
        // bounds. An off-tab/off-viewport control cannot retain vanilla
        // focus even though its action already rejects activation.
        semanticHost.finishAvailabilitySweep();

        super.extractRenderState(g, mouseX, mouseY, delta);
        FeatureSetting.drawPendingTooltip(g, (int) boxX(), (int) boxY(), (int) BOX_W, (int) BOX_H);
    }


    /**
     * Card/toggle geometry for module index {@code i}; null when the tile
     * shares no pixel with {@code vp} (the render cull and the click walk
     * share this one answer — a culled tile is neither painted nor
     * clickable). Returns {@code [cx,cy,cw,ch]}.
     */
    private ScreenLayout.Box cardBounds(int index) {
        var box = modulesLayout.at(index);
        return modulesLayout.visible(box, layoutScroll()) ? box : null;
    }

    // Sidebar and view controls remain distinct raised objects: every peer
    // owns neutral glass, while the selected peer owns stained glass.
    private final boolean[] chipGlass = new boolean[2];
    private boolean profGlass = false;
    private final boolean[] layoutGlass = new boolean[2];

    // Enabled module cards are the deliberate Phase E exception to the
    // parent-owned embedded rule: their highlighted state is a distinct
    // raised object, so it keeps the frosted gradient and glossy glass rim.
    // The result is frame-local and selects the established flat fallback
    // when live glass is unavailable.
    private boolean[] highlightedTileGlass = new boolean[0];

    private boolean highlightedTileGlass(int i) {
        return i < highlightedTileGlass.length && highlightedTileGlass[i];
    }

    /**
     * The material pass (§6 convention 6): every surface on the screen, painted
     * between {@code beginGlassPass} and {@code overlayDim} so the dim veils
     * them like it veils the world. The window, sidebar tabs, Profiles,
     * layout buttons, and search field own glass. Neutral tiles alone inherit
     * the window through {@link MaterialSurface#embeddedControl}, while enabled
     * tiles remain distinct raised stained glass: their frost, gradient and
     * glossy rim are the visual highlight. The Settings tab's inline rows
     * drive the settings' own {@code renderGlassPass} under the same scissor
     * (the same walk {@code renderSettingsLive} performs). Hover never
     * changes a tint — the washes are content, painted after the dim.
     */
    private void paintGlassPass(GuiGraphicsExtractor g, List<Module> mods) {
        float bx = boxX(), by = boxY();
        ClipBand vp = contentViewport();
        // The parent-owned and fallback paths share the compact-control radius.
        float ctrlRadius = AuroraTheme.RADIUS_SMALL;
        for (int i = 0; i < 2; i++) {
            float catY = navY(i);
            float catX = navX(i);
            chipGlass[i] = GlassSurface.card(g, catX, catY, navW(), TAB_H, ctrlRadius);
        }
        float profY = navY(2);
        float profX = navX(2);
        profGlass = GlassSurface.card(g, profX, profY, navW(), TAB_H, ctrlRadius);

        if (selectedCategory == 0) {
            for (int index = 0; index < layoutGlass.length; index++) {
                var box = chromeLayout.at(index + 3);
                layoutGlass[index] = GlassSurface.card(g, box.x(), box.top(), box.width(), box.height(), ctrlRadius);
            }
            if (searchField instanceof GlassEditBox glassField) glassField.aurora$renderGlassPass(g);

            GlassSurface.enableScissor(g, vp.x, vp.y, vp.xEnd(), vp.yEnd());
            if (highlightedTileGlass.length < mods.size()) {
                highlightedTileGlass = new boolean[mods.size()];
            }
            for (int i = 0; i < mods.size(); i++) {
                var bounds = cardBounds(i);
                if (bounds == null) {
                    highlightedTileGlass[i] = false;
                    continue;
                }
                if (MaterialSurface.unified()) {
                    // Unified module faces are opaque cached rasters, painted after dim.
                    highlightedTileGlass[i]=false;continue;
                }
                if (tileOn(mods.get(i))) {
                    highlightedTileGlass[i] = GlassSurface.control(g,
                            bounds.x(), bounds.y(layoutScroll()), bounds.width(), bounds.height(), ctrlRadius, true,
                            BlurPanelRenderer.Priority.ROW);
                } else {
                    highlightedTileGlass[i] = false;
                    MaterialSurface.embeddedControl(g, bounds.x(), bounds.y(layoutScroll()), bounds.width(), bounds.height(), ctrlRadius, false);
                }
            }
            GlassSurface.disableScissor(g);
        } else {
            // Sub-tabs: raised peers in the toolbar row, like the sidebar
            // chips (neutral, the selected one stained by its painter).
            for (int i = 0; i < pageControls.length; i++) {
                var box = chromeLayout.at(i + 3);
                pageGlass[i] = GlassSurface.card(g, box.x(), box.top(), box.width(), box.height(), ctrlRadius);
            }
            GlassSurface.enableScissor(g, vp.x, vp.y, vp.xEnd(), vp.yEnd());
            for (FeatureMetadata entry : pageEntries()) {
                if (entry.settingsDetailOnly) continue;
                for (FeatureSetting setting : entry.settings) {
                    var box = settingsLayout.bounds(setting);
                    if (box != null && settingsLayout.visible(box, layoutScroll()))
                        setting.renderGlassPass(g, settingX(box.x()), (int) box.y(layoutScroll()), settingW(box.width()));
                }
            }
            GlassSurface.disableScissor(g);
        }
    }

    private void renderLive(GuiGraphicsExtractor g, List<Module> mods, int mouseX, int mouseY, float delta) {
        float bx = boxX(), by = boxY();
        Font tr = this.font;
        // Same token as paintGlassPass's glass surfaces — the flat fallback
        // corners and the glass corners derive from one radius (C-7).
        float ctrlRadius = AuroraTheme.RADIUS_SMALL;

        // Window identity: AURORA text only (owner ruling 2026-10-06), in the title role,
        // left-aligned to the sidebar items' label column and optically centred on the toolbar row.
        g.text(tr, com.aurora.client.ui.util.AuroraFontRenderer.role("AURORA", com.aurora.client.ui.util.AuroraFontRenderer.Role.TITLE),
                (int) bx + UiDensity.FRAME_EDGE + 8, AuroraFontRenderer.roleTextY(tr, by + UiDensity.HEADER_TOP, TOOL, com.aurora.client.ui.util.AuroraFontRenderer.Role.TITLE), MaterialSurface.color(ThemeToken.ON_BACKGROUND), false);

        String[] cats = {"Features", "Settings"};
        for (int i = 0; i < 2; i++) {
            float catY = navY(i);
            float catX = navX(i);
            boolean sel = selectedCategory == i;
            // Canonical hover (§8.3, C-2b): POINTER only, symmetric 140 ms —
            // never driven by selection. Selection is the persistent
            // channel: the stained glass tint (or the flat accent wash
            // below), visible at hover 0; the selected tab's label
            // treatment is fixed, so the channels can never alias.
            boolean hover = Widget.inBounds(mouseX, mouseY, catX, catY, navW(), TAB_H);
            float hoverT = MaterialSurface.unified() ? pilotHover(PILOT_TABS[i],hover) : tabHovers[i].update(hover);
            SemanticActionControl control = tabControls[i];
            if (control != null) {
                control.setBounds((int) (catX), (int) catY, (int) navW(), (int) TAB_H);
                control.setAvailable(true); // static sidebar chrome — always interactive
                control.updatePointer(mouseX, mouseY);
            }
            // Raised glass was painted pre-dim: neutral for the inactive tab,
            // accent-stained for the selected tab. Preserve the established
            // flat fallback if the glass renderer declines this frame.
            if(MaterialSurface.unified()) MaterialSurface.selection(g,catX,catY,navW(),TAB_H,ctrlRadius,sel,hoverT,true);
            else if (!chipGlass[i]) {
                if (sel) {
                    RenderUtil.drawRoundedRectAA(g, catX, catY, navW(), TAB_H, ctrlRadius,
                            alpha(ThemeToken.ACCENT, 0x26 / 255f));
                } else if (hoverT > 0f) {
                    RenderUtil.drawRoundedRectAA(g, catX, catY, navW(), TAB_H, ctrlRadius,
                            AuroraAnim.lerpArgb(0x00000000,
                                    ThemeManager.surfaceColor(ThemeToken.SURFACE_VARIANT), hoverT));
                }
            }
            int txt = chipGlass[i] && sel ? MaterialSurface.color(ThemeToken.ON_ACCENT)
                    : sel ? MaterialSurface.color(ThemeToken.ON_BACKGROUND)
                    : AuroraAnim.lerpArgb(
                            MaterialSurface.color(ThemeToken.ON_BACKGROUND_SECONDARY),
                            MaterialSurface.color(ThemeToken.ON_BACKGROUND), hoverT);
            if (railNavigation()) {
                MaterialIconRenderer.drawIcon(g, tr, com.aurora.client.screen.IconCatalog.glyph(RAIL_ICONS[i]),
                        catX + navW() / 2f, catY + TAB_H / 2f, 13f, txt);
                if (hover && control != null) com.aurora.client.ui.component.TooltipPresenter.offer(control,
                        cats[i], (int) catX, (int) catY, (int) navW(), (int) TAB_H);
            } else {
                g.text(tr, cats[i], (int) (catX + 8), AuroraFontRenderer.textY(tr, catY, TAB_H), txt, false);
            }
            // Keyboard focus: the Button-family 1 px accent hairline — an
            // independent channel from both the selection tint and hover.
            // A SELECTED tab never takes a second indicator: its accent edge
            // IS the treatment, so focus on a selected peer reads through the
            // selection alone (owner ruling 2026-10-30 — the ring 2px outside
            // the selection edge doubled the highlight on click).
            if (control != null && control.isFocused() && !sel) {
                RenderUtil.drawRoundedOutlineAA(g, catX, catY, navW(), TAB_H, ctrlRadius, 1.0f,
                        ThemeManager.withAlpha(MaterialSurface.color(ThemeToken.ACCENT), 0x99));
            }
        }

        if (!narrowNavigation() && MaterialSurface.unified()) {
            // Subtle rule between the navigation tabs and the Profiles manager action.
            int ruleY = Math.round(navY(1) + TAB_H + (navY(2) - (navY(1) + TAB_H)) / 2f);
            int ruleInset = railNavigation() ? 4 : 8;
            g.fill((int) (navX(0) + ruleInset), ruleY, (int) (navX(0) + navW() - ruleInset), ruleY + 1,
                    ThemeManager.withAlpha(MaterialSurface.color(ThemeToken.ON_BACKGROUND), 0x1A));
        }

        float profY = navY(2);
        float profX = navX(2);
        boolean pHover = Widget.inBounds(mouseX, mouseY, profX, profY, navW(), TAB_H);
        float profT = MaterialSurface.unified() ? pilotHover("profiles",pHover) : profilesHover.update(pHover);
        if (profilesControl != null) {
            profilesControl.setBounds((int) (profX), (int) profY, (int) navW(), (int) TAB_H);
            profilesControl.setAvailable(true);
            profilesControl.updatePointer(mouseX, mouseY);
        }
        // Profiles is neutral raised glass; retain its hover fallback when
        // live glass is unavailable.
        if(MaterialSurface.unified()) MaterialSurface.selection(g,profX,profY,navW(),TAB_H,ctrlRadius,false,profT,true);
        else if (!profGlass && profT > 0f) {
            RenderUtil.drawRoundedRectAA(g, profX, profY, navW(), TAB_H, ctrlRadius,
                    AuroraAnim.lerpArgb(0x00000000,
                            ThemeManager.surfaceColor(ThemeToken.SURFACE_VARIANT), profT));
        }
        int profText = AuroraAnim.lerpArgb(
                MaterialSurface.color(ThemeToken.ON_BACKGROUND_SECONDARY),
                MaterialSurface.color(ThemeToken.ON_BACKGROUND), profT);
        if (railNavigation()) {
            MaterialIconRenderer.drawIcon(g, tr, com.aurora.client.screen.IconCatalog.glyph(RAIL_ICONS[2]),
                    profX + navW() / 2f, profY + TAB_H / 2f, 13f, profText);
            if (pHover && profilesControl != null) com.aurora.client.ui.component.TooltipPresenter.offer(profilesControl,
                    "Profiles", (int) profX, (int) profY, (int) navW(), (int) TAB_H);
        } else {
            g.text(tr, "Profiles", (int) (profX + 8), AuroraFontRenderer.textY(tr, profY, TAB_H), profText, false);
        }
        if (profilesControl != null && profilesControl.isFocused()) {
            RenderUtil.drawRoundedOutlineAA(g, profX, profY, navW(), TAB_H, ctrlRadius, 1.0f,
                    ThemeManager.withAlpha(MaterialSurface.color(ThemeToken.ACCENT), 0x99));
        }

        if (selectedCategory == 0) renderModulesLive(g, mods, mouseX, mouseY, delta);
        else renderSettingsLive(g, mouseX, mouseY, delta, ctrlRadius);

        // Design language §8 — top-edge scroll fade. Both tabs scissor
        // their content to the same viewport, so one gradient serves
        // either: tiles / inline rows fade into the window's own tint
        // (WINDOW_FILL, verbatim — its alpha IS the Background Opacity,
        // the single application point) as they approach the viewport top,
        // instead of hard-cutting at the scissor. Painted outside the
        // content scissor, before the thumb, anchored at the band's own
        // top edge. Gradient-only: the scissor already hides anything
        // above the boundary.
        ClipBand vp = contentViewport();
        ScrollFade.drawTop(g, vp.x, vp.width, vp.y, ScrollFade.FADE_PX,
                scrolls[selectedCategory].current(), MaterialSurface.color(ThemeToken.WINDOW_FILL));
        // At rest a Settings page ends on a row boundary (contentViewport); the bottom fade
        // only marks rows cut by scrolling.
        if (selectedCategory == 0 || scrolls[1].current() > 0)
            ScrollFade.drawBottom(g, vp.x, vp.width, vp.y + vp.height, ScrollFade.BOTTOM_FADE_PX,
                    computeMaxScroll() - scrolls[selectedCategory].current(), MaterialSurface.color(ThemeToken.WINDOW_FILL));

        drawScrollbar(g);
    }

    private void renderModulesLive(GuiGraphicsExtractor g, List<Module> mods, int mouseX, int mouseY, float delta) {
        Font tr = this.font;
        ClipBand vp = contentViewport();

        // The layout pair (C-5): canonical pointer-only hover per peer — never
        // gated on selection — plus bounds/pointer sync for the semantic
        // controls (Modules-tab chrome: always interactive while painted; the
        // Settings tab never re-marks them, so the frame-start sweep makes
        // them unavailable there).
        for (int i = 0; i < 2; i++) {
            var box = chromeLayout.at(i + 3);
            float lx = box.x(), btnY = box.top(), btnSize = box.width();
            boolean hover = chromeLayout.contains(box, mouseX, mouseY, 0);
            float hoverT = MaterialSurface.unified() ? pilotHover(PILOT_LAYOUTS[i],hover) : layoutHovers[i].update(hover);
            SemanticActionControl control = layoutControls[i];
            if (control != null) {
                control.setBounds((int) lx, (int) btnY, (int) btnSize, (int) btnSize);
                control.setAvailable(true);
                control.updatePointer(mouseX, mouseY);
                // Icon-only: name it on hover, as the title screen's icon buttons do.
                if (hover) com.aurora.client.ui.component.TooltipPresenter.offer(control,
                        i == 0 ? "List view" : "Grid view", (int) lx, (int) btnY, (int) btnSize, (int) btnSize);
            }
            drawLayoutButton(g, lx, btnY, btnSize, (i == 1) == gridLayout, hoverT,
                    control != null && control.isFocused(), i == 0, layoutGlass[i]);
        }

        // Render the EditBox manually (addWidget registers it for input only,
        // not for rendering) so typed text + caret actually draw. Its glass
        // surface was already painted pre-dim (paintGlassPass drives the
        // EditBoxMixin split); renderWidget draws content only.
        searchField.extractRenderState(g, mouseX, mouseY, delta);

        g.enableScissor(vp.x, vp.y, vp.xEnd(), vp.yEnd());
        // Same theme-resolved control radius the glass pass paints the tiles
        // with (C-7 — the former literal 6 equals radiusSmall's ROUND value,
        // so ROUND pixels are unchanged and SQUARE squares the tiles).
        float tileRadius = AuroraTheme.RADIUS_SMALL;
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            var bounds = cardBounds(i);
            if (bounds == null) continue;
            float cx = bounds.x(), cy = bounds.y(layoutScroll()), cw = bounds.width(), ch = bounds.height();
            // Hover reads the VISIBLE intersection: the pointer must be on
            // a pixel the scissor actually paints. A tile scrolled past the
            // band's edge no longer lights up from a pointer resting on its
            // hidden half (render truth == hover truth, C-1). The target is
            // the POINTER only (C-2b): the enabled state below is the
            // persistent channel and never pins the animator.
            boolean hover = modulesLayout.contains(bounds, mouseX, mouseY, layoutScroll());
            float hoverT = MaterialSurface.unified() ? pilotHover(m.id,hover) : tileHover(m.id).update(hover);
            SemanticActionControl control = tileControls.get(m.id);
            if (control == null && isSettingsResult(m)) control = tileControl(m);
            if (control != null) {
                // The walk only reaches tiles that intersect the viewport,
                // so this re-mark IS the ClipBand truth (C-2 partial
                // visibility: any non-empty intersection is available).
                control.setBounds((int) cx, (int) cy, (int) cw, (int) ch);
                control.setAvailable(true);
                control.updatePointer(mouseX, mouseY);
            }
            boolean on = tileOn(m);
            String subtitle = resultSubtitle.get(m.id);
            // Names stay primary in both states (2026-11-14): an off feature is available, not
            // disabled; elevation and the icon's tone carry on/off.
            int nameColor = MaterialSurface.color(ThemeToken.ON_BACKGROUND);

            // Neutral tiles inherit the enclosing window. Enabled tiles are
            // isolated stained glass; if glass declines, preserve the old
            // accent-wash + bright-outline fallback instead of disappearing.
            boolean highlightedGlass = on && highlightedTileGlass(i) && !MaterialSurface.unified();
            // Feature state is elevation (owner ruling 2026-11-17), reinforced by icon colour.
            if(MaterialSurface.unified()) {
                MaterialSurface.module(g,cx,cy,cw,ch,tileRadius,on,hoverT);
                // Settings cue (owner decision 2026-11-14): a muted chevron fades in while the tile
                // is hovered or focused; clicking it opens the feature's settings, as right-click does.
                float cue = Math.max(hoverT, control != null && control.isFocused() ? 1f : 0f);
                if (cue > 0f && !isSettingsResult(m)) {
                    FeatureMetadata fm = findMeta(m.id);
                    if (fm != null && fm.hasDetail()) {
                        // Halfway from secondary to primary text: still quieter than the label, but
                        // legible on a lit face (council 2026-11-18).
                        int muted = com.aurora.client.util.AuroraAnim.lerpArgb(MaterialSurface.color(ThemeToken.ON_BACKGROUND_SECONDARY),
                                MaterialSurface.color(ThemeToken.ON_BACKGROUND), .5f);
                        MaterialIconRenderer.drawIcon(g, tr, com.aurora.client.screen.IconCatalog.glyph("chevron_right"),
                                chevronX(cx, cw) + CHEVRON_BOX / 2f, chevronY(cy, ch) + CHEVRON_BOX / 2f,
                                CHEVRON, ThemeManager.withAlpha(muted, Math.round((muted >>> 24) * cue)));
                    }
                }
            }
            else if (on && !highlightedGlass) {
                RenderUtil.drawRoundedRectAA(g, cx, cy, cw, ch, tileRadius,
                        ThemeManager.surfaceColor(ThemeToken.SURFACE));
                RenderUtil.drawRoundedRectAA(g, cx, cy, cw, ch, tileRadius,
                        alpha(ThemeToken.ACCENT, 0x14 / 255f));
                RenderUtil.drawRoundedOutlineAA(g, cx, cy, cw, ch, tileRadius, 1.0f,
                        MaterialSurface.color(ThemeToken.ACCENT));
            }
            if (!MaterialSurface.unified() && hoverT > 0f) {
                RenderUtil.drawRoundedRectAA(g, cx, cy, cw, ch, tileRadius,
                        alpha(ThemeToken.ON_BACKGROUND, (0x1A / 255f) * hoverT));
            }
            // Keyboard focus: the Button-family hairline — independent of
            // both the enabled stain and the hover wash. An enabled tile
            // already wears the accent edge; a second ring outside it doubled
            // the highlight, so a selected/enabled peer never takes one
            // (owner ruling 2026-10-30).
            if (control != null && control.isFocused()) {
                RenderUtil.drawRoundedOutlineAA(g, cx, cy, cw, ch, tileRadius, 1.0f,
                        ThemeManager.withAlpha(MaterialSurface.color(ThemeToken.ACCENT), 0x99));
            }

            if (gridLayout) {
                drawTileIcon(g, tr, m, cx + cw / 2f, cy + 30, 28f, on, highlightedGlass);
                int labelWidth = Math.max(0, (int) cw - 2 * com.aurora.client.ui.util.TextAlignment.FACE_PAD);
                long labelVersion = ThemeManager.generation() * 65537L + labelWidth * 31L
                        + com.aurora.client.ui.util.AuroraFontRenderer.styleKey() * 7L;
                if (labelVersion != moduleLabelVersion) { moduleLabelLines.clear(); moduleLabelVersion = labelVersion; }
                // A result with a second line gives its name one line.
                int nameLinesMax = subtitle == null ? 2 : 1;
                var lines = moduleLabelLines.computeIfAbsent(nameLinesMax + "|" + m.name,
                        key -> com.aurora.client.ui.util.WordWrap.lines(m.name, labelWidth, nameLinesMax, tr::width));
                int nameY = AuroraFontRenderer.blockY(tr, cy + ch - 29, 24, lines.size() + (subtitle == null ? 0 : 1));
                RenderUtil.drawCenteredLines(tr, g, lines, cx + com.aurora.client.ui.util.TextAlignment.FACE_PAD, nameY, labelWidth, nameColor);
                if (subtitle != null) {
                    drawSubtitle(g, tr, m, subtitle, labelWidth, cx + cw / 2f, nameY + lines.size() * AuroraFontRenderer.linePitch(tr),
                            mouseX, mouseY, cx, cw, cy + ch);
                }
            } else {
                drawTileIcon(g, tr, m, cx + 20, cy + ch / 2f, 16f, on, highlightedGlass);
                int nameWidth = Math.max(1, (int) cw - 64);
                long labelVersion = ThemeManager.generation() * 65537L + nameWidth * 31L
                        + com.aurora.client.ui.util.AuroraFontRenderer.styleKey() * 7L;
                if (labelVersion != moduleLabelVersion) { moduleLabelLines.clear(); moduleLabelVersion = labelVersion; }
                int nameLinesMax = subtitle == null ? 2 : 1;
                var nameLines = moduleLabelLines.computeIfAbsent(nameLinesMax + "|" + m.name,
                        key -> com.aurora.client.ui.util.WordWrap.lines(m.name, nameWidth, nameLinesMax, tr::width));
                int textLines = nameLines.size() + (subtitle == null ? 0 : 1);
                // List rows read left to right: icon, name on the icon's axis, state at the end.
                int lineY = AuroraFontRenderer.blockY(tr, cy, ch, textLines);
                for (String line : nameLines) {
                    g.text(tr, line, Math.round(cx + 38), lineY, nameColor, false);
                    lineY += AuroraFontRenderer.linePitch(tr);
                }
                if (subtitle != null) {
                    drawSubtitle(g, tr, m, subtitle, nameWidth, cx + 38 + nameWidth / 2f, lineY,
                            mouseX, mouseY, cx, cw, cy + ch);
                }
            }
        }
        if (mods.isEmpty()) {
            // Empty search: centered in the content well with the query echoed back,
            // plus a secondary hint, instead of a lone line pinned to the corner.
            ClipBand well = contentViewport();
            int cx = well.x + well.width / 2, cy = well.y + well.height / 2 - tr.lineHeight;
            String headline = com.aurora.client.ui.util.AuroraFontRenderer.ellipsize(tr,
                    "Nothing matches “" + searchQuery.trim() + "”", well.width - 16, 1);
            com.aurora.client.ui.util.AuroraFontRenderer.drawCentered(g, tr, headline, cx, cy,
                    MaterialSurface.color(ThemeToken.ON_BACKGROUND));
            com.aurora.client.ui.util.AuroraFontRenderer.drawCentered(g, tr, "Try a shorter or different search", cx,
                    cy + tr.lineHeight + 4, MaterialSurface.color(ThemeToken.ON_BACKGROUND_MUTED));
        }
        g.disableScissor();
    }

    private void renderSettingsLive(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, float ctrlRadius) {
        float mx = mainX();
        Font tr = this.font;
        searchField.visible = false;
        ClipBand vp = contentViewport();

        // Sub-tabs (toolbar row, never scrolled): the sidebar tabs' channels
        // one level down — selection is the stained face, hover is
        // pointer-only, focus is the hairline on an unselected peer.
        for (int i = 0; i < pageControls.length; i++) {
            var box = chromeLayout.at(i + 3);
            float tx = box.x(), my = box.top(), tw = box.width();
            boolean sel = selectedPage == i;
            boolean hover = chromeLayout.contains(box, mouseX, mouseY, 0);
            float hoverT = MaterialSurface.unified() ? pilotHover(pagePilotIds[i], hover) : pageHovers[i].update(hover);
            SemanticActionControl control = pageControls[i];
            if (control != null) {
                control.setBounds((int) tx, (int) my, (int) tw, (int) PAGE_TAB_H);
                control.setAvailable(true); // toolbar chrome — interactive whenever the Settings tab is shown
                control.updatePointer(mouseX, mouseY);
            }
            // One selection language per level (UI polish 2026-11-14): the sidebar's accent pill
            // owns location, so a sub-tab marks selection by text alone (heading role, primary
            // colour); hover keeps the flat wash.
            if (MaterialSurface.unified()) {
                if (!sel) MaterialSurface.selection(g, tx, my, tw, PAGE_TAB_H, ctrlRadius, false, hoverT, true);
            }
            else if (!pageGlass[i]) {
                if (sel) {
                    RenderUtil.drawRoundedRectAA(g, tx, my, tw, PAGE_TAB_H, ctrlRadius,
                            alpha(ThemeToken.ACCENT, 0x26 / 255f));
                } else if (hoverT > 0f) {
                    RenderUtil.drawRoundedRectAA(g, tx, my, tw, PAGE_TAB_H, ctrlRadius,
                            AuroraAnim.lerpArgb(0x00000000,
                                    ThemeManager.surfaceColor(ThemeToken.SURFACE_VARIANT), hoverT));
                }
            }
            int txt = pageGlass[i] && sel ? MaterialSurface.color(ThemeToken.ON_ACCENT)
                    : sel ? MaterialSurface.color(ThemeToken.ON_BACKGROUND)
                    : AuroraAnim.lerpArgb(
                            MaterialSurface.color(ThemeToken.ON_BACKGROUND_SECONDARY),
                            MaterialSurface.color(ThemeToken.ON_BACKGROUND), hoverT);
            if (sel && MaterialSurface.unified()) {
                // The selected sub-tab keeps the body weight (a heavier label shifted every label
                // as the selection moved) and takes primary text plus a persistent neutral
                // underline (2026-11-14). Neutral, so the sidebar keeps the one accent selection.
                com.aurora.client.ui.util.AuroraFontRenderer.drawCentered(g, tr, pageTabLabel[i],
                        (int) (tx + tw / 2f), AuroraFontRenderer.textY(tr, my, PAGE_TAB_H), txt);
                int lineW = tr.width(pageTabLabel[i]);
                RenderUtil.drawRoundedRectAA(g, tx + (tw - lineW) / 2f, my + PAGE_TAB_H - TAB_RULE, lineW, TAB_RULE, TAB_RULE / 2f, txt);
            }
            else com.aurora.client.ui.util.AuroraFontRenderer.drawCentered(g, tr, pageTabLabel[i],
                    (int) (tx + tw / 2f), AuroraFontRenderer.textY(tr, my, PAGE_TAB_H), txt);
            if (control != null && control.isFocused() && !sel) {
                RenderUtil.drawRoundedOutlineAA(g, tx, my, tw, PAGE_TAB_H, ctrlRadius, 1.0f,
                        ThemeManager.withAlpha(MaterialSurface.color(ThemeToken.ACCENT), 0x99));
            }
        }

        g.enableScissor(vp.x, vp.y, vp.xEnd(), vp.yEnd());
        List<FeatureMetadata> visibleEntries = pageEntries();
        for (int entryIndex = 0; entryIndex < visibleEntries.size(); entryIndex++) {
            FeatureMetadata m = visibleEntries.get(entryIndex);
            float y = settingsLayout.bounds(m).y(layoutScroll());
            if (entryIndex > 0 && MaterialSurface.unified()) {
                int ruleY = Math.round(y - SETTINGS_ENTRY_GAP / 2f);
                if (vp.intersects(mx + 4, ruleY, contentWidth() - 4, 1)) {
                    g.fill((int) (mx + 4), ruleY, (int) (mx + contentWidth()), ruleY + 1,
                            ThemeManager.withAlpha(MaterialSurface.color(ThemeToken.ON_BACKGROUND), 0x1A));
                }
            }
            // Section headings and spacing provide grouping, without nested frames.
            int headerH = SETTINGS_HEADER_H;
            var headerNav = headerNavControls.get(m.id);
            var headerToggle = headerToggleControls.get(m.id);
            if (headerNav != null) {
                headerNav.setBounds((int) (mx + 4), (int) y, contentWidth() - 46, headerH);
                headerNav.setScrollFocusTarget(true);
            }
            if (headerToggle != null) {
                headerToggle.setBounds((int) (mx + contentWidth() - 40), (int) y, 40, headerH);
                headerToggle.setScrollFocusTarget(true);
            }
            if (vp.intersects(mx + 4, y, contentWidth() - 4, headerH)) {
                // Header navigation (C-2b): the entries WITH a detail
                // screen navigate (same family as the sidebar tabs' action
                // contract); custom_title has none — its header is grouping
                // chrome and renders neither affordance nor control. Hover
                // is the chevron affordance brightening, pointer-only
                // canonical 140 ms — the row is text chrome, so no fill
                // wash is invented for it.
                boolean navHover = m.hasDetail()
                        && Widget.inBounds(mouseX, mouseY, mx + 4, y, contentWidth() - 4 - 42, headerH);
                float navT = m.hasDetail() ? (MaterialSurface.unified() ? pilotHover(pilotHeaderHovers,m.id,navHover) : headerHover(m.id).update(navHover)) : 0f;
                // Group heading in the heading role, so a group reads apart from its own rows.
                g.text(tr, com.aurora.client.ui.util.AuroraFontRenderer.role(m.displayName, com.aurora.client.ui.util.AuroraFontRenderer.Role.HEADING),
                        (int) (mx + 4), AuroraFontRenderer.roleTextY(tr, y, SETTINGS_HEADER_H, com.aurora.client.ui.util.AuroraFontRenderer.Role.HEADING), MaterialSurface.color(ThemeToken.ON_BACKGROUND), false);
                // Detail-screen affordance: a header click opens the entry's
                // detail screen (the Settings tab's counterpart of the
                // Modules tab's right-click-to-detail) — the chevron marks
                // the row as navigable.
                if (m.hasDetail()) {
                    MaterialIconRenderer.drawIcon(g, tr, FeatureIcons.get("_dropdown_expand_more"),
                            mx + contentWidth() - 40 - 12, y + SETTINGS_HEADER_H / 2f, 9,
                            AuroraAnim.lerpArgb(
                                    MaterialSurface.color(ThemeToken.ON_BACKGROUND_MUTED),
                                    MaterialSurface.color(ThemeToken.ON_BACKGROUND_SECONDARY), navT));
                    SemanticActionControl nav = headerNavControls.get(m.id);
                    if (nav != null) {
                        // The navigation zone mirrors the click walk's rect
                        // (left of the toggle zone); the header shares a
                        // pixel with the viewport, so it is available.
                        nav.setBounds((int) (mx + 4), (int) y, contentWidth() - 4 - 42, headerH);
                        nav.setAvailable(true);
                        nav.updatePointer(mouseX, mouseY);
                        if (nav.isFocused()) {
                            RenderUtil.drawRoundedOutlineAA(g, mx + 4, y, contentWidth() - 4 - 42, headerH, ctrlRadius, 1.0f,
                                    ThemeManager.withAlpha(MaterialSurface.color(ThemeToken.ACCENT), 0x99));
                        }
                    }
                }
                ToggleSwitch t = sectionToggles.get(m.id);
                if (t != null) {
                    float tw = com.aurora.client.ui.util.UiDensity.TOGGLE_W, th = com.aurora.client.ui.util.UiDensity.TOGGLE_H;
                    // Ends flush on the rows' control right edge (mx + contentWidth()).
                    float tx = mx + contentWidth() - tw;
                    t.layout(tx, y + (headerH - th) / 2, tw, th);
                    t.renderShapes(g, tx, y + (headerH - th) / 2, tw, th);
                    t.renderOverlay(g, tx, y + (headerH - th) / 2, tw, th, mouseX, mouseY);
                    SemanticActionControl tc = headerToggleControls.get(m.id);
                    if (tc != null) {
                        // The forgiving 40px zone IS the control (the C-1
                        // ruling) — pointer, keyboard, and narration share
                        // one target; the switch paints the focus hairline
                        // itself via focusedVisual (BooleanSetting's sync).
                        tc.setBounds((int) (mx + contentWidth() - 40), (int) y, 40, headerH);
                        tc.setAvailable(true);
                        tc.updatePointer(mouseX, mouseY);
                        t.focusedVisual(tc.isFocused());
                    }
                }
            }
            if (settingsReveal == m) paintReveal(g, mx + 4, y, contentWidth() - 4, headerH, ctrlRadius);
            String summary = pageSummaryLine(m, tr);
            if (summary != null && vp.intersects(mx + 4, y + headerH, contentWidth() - 4, SUMMARY_H))
                g.text(tr, summary, (int) (mx + 4), AuroraFontRenderer.textY(tr, y + headerH, SUMMARY_H),
                        com.aurora.client.health.FeatureHealth.headlineFor(m.id) != null
                                ? ThemeManager.color(ThemeToken.SEMANTIC_WARNING)
                                : MaterialSurface.color(ThemeToken.ON_BACKGROUND_SECONDARY), false);
            if (m.settingsDetailOnly) {
                y = settingsLayout.bounds(m).y(layoutScroll()) + headerBlockH(m);
                // Detail-only entry: one muted hint line in place of the
                // inline rows — the merged list lives behind the header
                // click. The hint height must match the walks in
                // computeMaxScroll and mouseClicked.
                if (vp.intersects(mx + 4, y, contentWidth() - 4, SETTINGS_HINT_H)) {
                    g.text(tr, "Click to configure", (int) (mx + 4), (int) (y + 1),
                            MaterialSurface.color(ThemeToken.ON_BACKGROUND_MUTED), false);
                }
            } else {
                for (FeatureSetting s : m.settings) {
                    var box = settingsLayout.bounds(s);
                    if (box == null) {
                        // Collapsed under its section caption (or hidden by its own condition):
                        // it takes no space, and none of its controls stay reachable, the same
                        // walk every other Settings-tab pass makes (pointer audit 2026-11-15).
                        for (SemanticActionControl control : hostedSettingControls.getOrDefault(s, List.of()))
                            semanticHost.setAvailable(control, false);
                        s.onInteractionAvailabilityChanged(false);
                        if (s == FeatureSetting.getFocused()) focusedSettingVisible = false;
                        continue;
                    }
                    y = box.y(layoutScroll());
                    int rh = (int) box.height();
                    boolean visible = settingsLayout.visible(box, layoutScroll());
                    if (visible) {
                        s.render(g, settingX(box.x()), (int) y, settingW(box.width()), mouseX, mouseY);
                        if (settingsReveal == s) paintReveal(g, box.x(), y, box.width(), rh, ctrlRadius);
                    }
                    boolean anyControlAvailable = false;
                    List<SemanticActionControl> controls = hostedSettingControls.getOrDefault(s, List.of());
                    for (SemanticActionControl control : controls) {
                        control.setScrollFocusTarget(s.controlVisible(control));
                        if (!visible) control.setBounds((int) mx, (int) y, contentWidth(), rh);
                        // Partial-visibility policy: any non-empty
                        // intersection of the ACTIONABLE control rect with
                        // C-1's authoritative viewport is available. Edge
                        // contact alone is not (ClipBand is half-open).
                        boolean available = visible && s.controlVisible(control) && vp.intersects(
                                control.getX(), control.getY(),
                                control.getWidth(), control.getHeight());
                        semanticHost.setAvailable(control, available);
                        anyControlAvailable |= available;
                    }
                    // C-1 render/input availability coupling: the row (and,
                    // when it holds the focused setting, keyboard routing)
                    // is available exactly while it shares a pixel with the
                    // viewport. onInteractionAvailabilityChanged is the
                    // FeatureDetailScreen hook — capture families (the HUD
                    // Editor key row on the Controls page) cancel on false.
                    s.onInteractionAvailabilityChanged(
                            controls.isEmpty() ? visible : anyControlAvailable);
                    if (s == FeatureSetting.getFocused()) {
                        focusedSettingVisible = visible;
                    }
                }
            }
        }
        // Compact EnumSettings keep their 18px row while expanded — the open
        // popup is a FLOATING overlay the HOST must paint after the row walk
        // (the FeatureDetailScreen discipline; before this, the Settings tab's
        // two text_fonts dropdowns expanded invisibly — clicks "did nothing"
        // and blind clicks could silently rewrite the config).
        for (FeatureMetadata m : pageEntries()) {
            for (FeatureSetting s : m.settings) {
                if (s instanceof com.aurora.client.screen.setting.EnumSetting<?> row
                        && s.isVisible() && row.popupVisible()) {
                    row.renderFloatingOverlay(g, mouseX, mouseY);
                }
                if (s instanceof com.aurora.client.screen.setting.ColorSetting row && s.isVisible()) row.renderFloatingEditor(g);
            }
        }
        g.disableScissor();
    }

    /**
     * One layout button's painter (C-5 conformance, visuals preserved): the
     * selected channel, hover channel, and focus channel are independent.
     * Hover is the caller-driven canonical {@code hoverT} (pointer-only,
     * symmetric 140 ms — was immediate AND suppressed on the selected peer);
     * the translucent ON_BACKGROUND wash composes over stained/neutral glass
     * and the flat fills alike, so selected+hover is representable. Focus is
     * the Button-family 1 px accent hairline. Rest states and the vector
     * icons are unchanged; the flat unselected base now lerps to its hover
     * endpoint instead of snapping.
     */
    private void drawLayoutButton(GuiGraphicsExtractor g, float x, float y, float size, boolean selected,
                                  float hoverT, boolean focused, boolean list, boolean btnGlass) {
        // Same theme-resolved control radius as the glass pass's surfaces of
        // this pair (C-7 — was a literal 4 that ignored Square mode).
        float ctrlRadius = AuroraTheme.RADIUS_SMALL;
        // Surface: the list/grid pair is a segmented control — RAISED glass
        // (painted pre-dim by paintGlassPass), accent-STAINED on the active
        // one. Content here: the canonical hover wash on glass (both peers),
        // the flat fills + borders on decline, then the icon.
        // The active layout is the opaque neutral field face, like the selected sub-tab: the
        // sidebar is the screen's one accent selection (UI polish 2026-11-14).
        if(MaterialSurface.unified()) {
            if (selected) MaterialSurface.field(g,x,y,size,size,ctrlRadius,0f,false);
            else MaterialSurface.selection(g,x,y,size,size,ctrlRadius,false,hoverT,true);
        }
        else if (btnGlass) {
            if (hoverT > 0f) {
                RenderUtil.drawRoundedRectAA(g, x, y, size, size, ctrlRadius,
                        alpha(ThemeToken.ON_BACKGROUND, (0x1A / 255f) * hoverT));
            }
        } else {
            int bg = selected ? alpha(ThemeToken.ACCENT, 0x26 / 255f)
                    : AuroraAnim.lerpArgb(
                            ThemeManager.surfaceColor(ThemeToken.SURFACE),
                            ThemeManager.surfaceColor(ThemeToken.SURFACE_VARIANT), hoverT);
            int border = selected ? MaterialSurface.color(ThemeToken.ACCENT) : alpha(ThemeToken.ON_BACKGROUND, 0x08f);
            RenderUtil.drawRoundedRectAA(g, x, y, size, size, ctrlRadius, bg);
            RenderUtil.drawRoundedOutlineAA(g, x, y, size, size, ctrlRadius, 1.0f, border);
            if (!selected && hoverT > 0f) {
                RenderUtil.drawRoundedRectAA(g, x, y, size, size, ctrlRadius,
                        alpha(ThemeToken.ON_BACKGROUND, (0x1A / 255f) * hoverT));
            }
        }
        // Keyboard focus: the Button-family hairline — independent of both
        // the enabled stain and the hover wash. A selected peer already wears
        // the accent edge; no second ring outside it (owner ruling 2026-10-30).
        if (focused) {
            RenderUtil.drawRoundedOutlineAA(g, x, y, size, size, ctrlRadius, 1.0f,
                    ThemeManager.withAlpha(MaterialSurface.color(ThemeToken.ACCENT), 0x99));
        }
        // Same contract as the tiles: on stained glass the icon takes the
        // contrast-derived ON_ACCENT; the flat fallback's ~15% accent wash is
        // dark enough that the accent glyph still reads there.
        int iconCol = MaterialSurface.unified() ? (selected ? MaterialSurface.color(ThemeToken.ON_BACKGROUND)
                        : AuroraAnim.lerpArgb(MaterialSurface.color(ThemeToken.ON_BACKGROUND_MUTED),
                                MaterialSurface.color(ThemeToken.ON_BACKGROUND_SECONDARY), hoverT))
                : selected && btnGlass ? MaterialSurface.color(ThemeToken.ON_ACCENT)
                : selected ? MaterialSurface.color(ThemeToken.ACCENT)
                : alpha(ThemeToken.ON_BACKGROUND, 0x53f);
        if (list) {
            for (int i = 0; i < 3; i++) {
                float k = size / 20f, dy = y + (5 + i * 4.5f) * k;
                RenderUtil.drawRoundedRectAA(g, x + 5 * k, dy, 2, 2, 1, iconCol);
                RenderUtil.drawRoundedRectAA(g, x + 9 * k, dy + 0.5f, 6 * k, 1, 0.5f, iconCol);
            }
        } else {
            float k = size / 20f, c = 4 * k;
            RenderUtil.drawRoundedRectAA(g, x + 5 * k, y + 5 * k, c, c, 1, iconCol);
            RenderUtil.drawRoundedRectAA(g, x + 11 * k, y + 5 * k, c, c, 1, iconCol);
            RenderUtil.drawRoundedRectAA(g, x + 5 * k, y + 11 * k, c, c, 1, iconCol);
            RenderUtil.drawRoundedRectAA(g, x + 11 * k, y + 11 * k, c, c, 1, iconCol);
        }
    }

    /**
     * A result tile's second line, centred under its name and fitted with a
     * bounded ellipsis (the shared ellipsize memo caches it). On a module found
     * through a setting it is a link to that setting: it turns accent while
     * the pointer is in the tile's lower band (the click walk's
     * {@link #inSubtitleBand} rect).
     */
    private void drawSubtitle(GuiGraphicsExtractor g, Font tr, Module m, String subtitle, int width, float centerX,
                              float y, int mouseX, int mouseY, float cx, float cw, float bottom) {
        String fitted = com.aurora.client.ui.util.AuroraFontRenderer.ellipsize(tr, subtitle, width, 1);
        boolean link = !isSettingsResult(m) && resultVia.containsKey(m.id)
                && inSubtitleBand(mouseX, mouseY, cx, cw, y, bottom);
        int color = link ? MaterialSurface.color(ThemeToken.ACCENT) : MaterialSurface.color(ThemeToken.ON_BACKGROUND_MUTED);
        if (gridLayout) com.aurora.client.ui.util.AuroraFontRenderer.drawCentered(g, tr, fitted, Math.round(centerX), Math.round(y), color);
        else g.text(tr, fitted, Math.round(centerX - width / 2f), Math.round(y), color, false);
        if (!fitted.equals(subtitle) && contentViewport().contains(mouseX, mouseY)) {
            int textWidth = tr.width(fitted);
            int textX = Math.round(centerX - (gridLayout ? textWidth : width) / 2f);
            FeatureSetting.hoverTooltip(m, subtitle, textX, Math.round(y), textWidth, tr.lineHeight, mouseX, mouseY);
        }
    }

    /** The settings-cue chevron's em size and its hit box: the tile's top-right corner, or a list
     * row's trailing end. Paint and click share this one rect. */
    private static final float CHEVRON = 13f;
    private static final float CHEVRON_BOX = 18f;
    private float chevronX(float x, float width) { return x + width - CHEVRON_BOX - (gridLayout ? 3 : 8); }
    private float chevronY(float y, float height) { return gridLayout ? y + 3 : y + (height - CHEVRON_BOX) / 2f; }



    /** The lower band of a tile from its subtitle line down — where a click opens the matched setting. */
    private static boolean inSubtitleBand(double mouseX, double mouseY, float cx, float cw, float subtitleY, float bottom) {
        return mouseX >= cx && mouseX <= cx + cw && mouseY >= subtitleY - 2 && mouseY <= bottom;
    }

    /** The y of a result tile's subtitle line, shared by paint and the click walk. */
    private float subtitleY(Module m, float cy, float ch, float cw) {
        if (gridLayout) return AuroraFontRenderer.blockY(this.font, cy + ch - 29, 24, 2) + AuroraFontRenderer.linePitch(this.font);
        int nameLinesMax = 1;
        int nameWidth = Math.max(1, (int) cw - 64);
        var nameLines = moduleLabelLines.computeIfAbsent(nameLinesMax + "|" + m.name,
                key -> com.aurora.client.ui.util.WordWrap.lines(m.name, nameWidth, nameLinesMax, this.font::width));
        return AuroraFontRenderer.blockY(this.font, cy, ch, nameLines.size() + 1) + nameLines.size() * AuroraFontRenderer.linePitch(this.font);
    }

    /**
     * The tiles the Modules tab shows: every module with no query, else the
     * ranked search results across both tabs. Memoized: the index answers a
     * repeated query from its own memo and the tile list is rebuilt only
     * when the hits change, so the per-frame callers cost nothing.
     */
    private List<Module> filteredModules() {
        String q = searchQuery == null ? "" : searchQuery;
        if (com.aurora.client.search.SearchMatcher.normalize(q).isEmpty()) {
            return ModuleManager.getInstance().getModules();
        }
        var hits = searchIndex().search(q, com.aurora.client.search.SearchMatcher.configuredTolerance());
        if (hits != lastHits) {
            lastHits = hits;
            List<Module> results = new ArrayList<>(hits.size());
            resultVia.clear();
            resultSubtitle.clear();
            for (var hit : hits) {
                Module m = hit.value();
                results.add(m);
                if (hit.via() != null) resultVia.put(m.id, hit.via());
                String subtitle = hit.via();
                // A Settings entry names its page unless a setting matched
                // (the tile's click goes to that page either way).
                if (isSettingsResult(m) && subtitle == null) {
                    int page = pageIndexOf(m.id);
                    subtitle = page < 0 ? "Settings" : "Settings \u203A " + pages.get(page).label();
                }
                if (subtitle != null) resultSubtitle.put(m.id, subtitle);
            }
            lastResults = results;
        }
        return lastResults;
    }

    private com.aurora.client.search.SearchIndex<Module> searchIndex() {
        if (searchIndex == null) {
            var builder = com.aurora.client.search.SearchIndex.<Module>builder();
            for (Module m : ModuleManager.getInstance().getModules()) addSearchEntry(builder, m, findMeta(m.id));
            for (FeatureMetadata fm : allPageEntries) {
                Module m = settingsResults.computeIfAbsent(fm.id,
                        id -> new Module(fm.id, fm.displayName, fm.description == null ? "" : fm.description));
                addSearchEntry(builder, m, fm);
            }
            searchIndex = builder.build();
        }
        return searchIndex;
    }

    private static void addSearchEntry(com.aurora.client.search.SearchIndex.Builder<Module> builder,
                                       Module m, FeatureMetadata fm) {
        var entry = builder.entry(m).field(m.name, 3, null).field(m.description, 1, null);
        if (fm == null) return;
        for (FeatureSetting setting : fm.settings) {
            String label = setting.label;
            if (label != null && !label.isBlank()) entry.field(label, 2, label);
        }
    }

    /** Whether {@code m} is a Settings-tab entry shown as a result tile (it navigates; it never toggles). */
    private boolean isSettingsResult(Module m) {
        return settingsResults.get(m.id) == m;
    }

    /** A tile's stained "on" state: a module's enabled state; never for a Settings result. */
    private boolean tileOn(Module m) {
        return !isSettingsResult(m) && m.isEnabled();
    }

    private int pageIndexOf(String id) {
        for (int i = 0; i < pages.size(); i++) {
            for (FeatureMetadata fm : pages.get(i).entries()) if (fm.id.equals(id)) return i;
        }
        return -1;
    }

    /** The first setting in {@code fm} labelled {@code label}, or null. */
    private static FeatureSetting settingNamed(FeatureMetadata fm, String label) {
        if (fm == null || label == null) return null;
        for (FeatureSetting setting : fm.settings) if (label.equals(setting.label)) return setting;
        return null;
    }

    /** Opens a module's detail screen, scrolled to and outlining the setting its result matched. */
    private void openDetail(Module m) {
        FeatureMetadata fm = findMeta(m.id);
        if (fm == null || !fm.hasDetail() || this.minecraft == null) return;
        Screen screen = FeatureDetailScreen.destination(this, fm);
        if (screen instanceof FeatureDetailScreen detail) detail.reveal(settingNamed(fm, resultVia.get(m.id)));
        com.aurora.client.adapter.NativeUi.setScreen(this.minecraft, screen);
    }

    /**
     * Opens a Settings-tab result: its page, scrolled so the matched row (or
     * the entry's header) sits a third of the way down, outlined while it
     * fades. A detail-only entry opens its detail screen instead.
     */
    private void openSettingsResult(Module m) {
        FeatureMetadata fm = findMeta(m.id);
        int page = pageIndexOf(m.id);
        if (fm == null || page < 0) return;
        if (fm.settingsDetailOnly) {
            openDetail(m);
            return;
        }
        FeatureSetting target = settingNamed(fm, resultVia.get(m.id));
        selectCategory(1);
        selectSettingsPage(page);
        arrangeSettings();
        var box = settingsLayout.bounds(target == null ? fm : target);
        float offset = box == null ? hiddenSettingOffsets.getOrDefault(target, 0) : box.top() - measuredContent.y;
        double max = computeMaxScroll();
        scrolls[1].set(Math.max(0, Math.min(max, offset - contentViewport().height / 3.0)));
        settingsReveal = target != null ? target : fm;
        settingsRevealStartMs = System.currentTimeMillis();
    }

    /** The search reveal outline: accent, fading over its last half. */
    private void paintReveal(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius) {
        long age = System.currentTimeMillis() - settingsRevealStartMs;
        if (age >= REVEAL_MS) {
            settingsReveal = null;
            return;
        }
        float fade = Math.min(1f, 2f * (1f - age / (float) REVEAL_MS));
        int accent = MaterialSurface.color(ThemeToken.ACCENT);
        RenderUtil.drawRoundedOutlineAA(g, x, y, w, h, radius, 1f,
                ThemeManager.withAlpha(accent, Math.round((accent >>> 24) * fade)));
    }

    /** Instant Search (Settings, General page): the field takes focus on open and on returning to the Modules tab. */
    private void focusSearchIfInstant() {
        if (searchField == null || !AuroraConfig.get().searchInstant) return;
        this.setFocused(searchField);
        searchField.setFocused(true);
    }

    private static String fit(Font tr, String text, int maxW) {
        if (tr.width(text) <= maxW) return text;
        return tr.plainSubstrByWidth(text, maxW - tr.width("...")) + "...";
    }

    /** Render a module's icon (FeatureIcons glyph via the material-symbols font). */
    private void drawTileIcon(GuiGraphicsExtractor g, Font tr, Module m, float cx, float cy, float targetPx,
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
            color = MaterialSurface.color(ThemeToken.ON_ACCENT);
        } else if (on) {
            // An enabled icon takes the accent, the feature's one state cue (owner decision
            // 2026-11-14); a per-module accent override still applies.
            int accent = ModuleAccentColors.get(m.id);
            color = accent != 0 ? accent : MaterialSurface.color(ThemeToken.ACCENT);
            // On the lit raised face the glyph is a quarter of the way to white, so the on icon
            // reads as illuminated beside the off grey rather than darker (council 2026-11-18).
            if (MaterialSurface.unified()) color = com.aurora.client.util.AuroraAnim.lerpArgb(color, color | 0x00ffffff, .25f);
        } else {
            // Off: three quarters of the way from secondary to muted text in unified windows, well
            // below the label's white so on and off part at a glance, without reading as unavailable.
            color = MaterialSurface.unified() ? neutral(com.aurora.client.util.AuroraAnim.lerpArgb(
                    MaterialSurface.color(ThemeToken.ON_BACKGROUND_SECONDARY),
                    MaterialSurface.color(ThemeToken.ON_BACKGROUND_MUTED), .75f))
                    : MaterialSurface.color(ThemeToken.ON_BACKGROUND_MUTED);
        }
        // Crisp native-resolution glyph (the shared font atlas is
        // point-sampled and degrades badly at these sizes); em size = the
        // box the old glyph-scaling math targeted, so the footprint is
        // unchanged.
        MaterialIconRenderer.drawIcon(g, tr, glyph, cx, cy, targetPx, color);
    }


    /** {@code argb} with its hue removed at the same luminance: the cool secondary text tone read
     * as a faint accent beside indigo icons (2026-11-14), so off icons use its grey. */
    private static int neutral(int argb) {
        int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
        int l = Math.round(0.2126f * r + 0.7152f * g + 0.0722f * b);
        return (argb & 0xFF000000) | l << 16 | l << 8 | l;
    }

    private FeatureMetadata findMeta(String id) {
        for (FeatureMetadata fm : FeatureRegistry.modules()) if (fm.id.equals(id)) return fm;
        for (FeatureMetadata fm : FeatureRegistry.settings()) if (fm.id.equals(id)) return fm;
        return null;
    }

    private double computeMaxScroll() { return contentLayout().maxScroll(); }

    private void tickSmoothScroll() {
        // Only the active tab's position moves (the other tab's glide stays
        // frozen mid-switch, as before); the inactive instance's clock is
        // still stamped so resuming it sees a fresh dt — the behavior the
        // old single shared clock gave.
        scrolls[1 - selectedCategory].touchClock();
        scrolls[selectedCategory].advance(computeMaxScroll());
    }

    private ScrollbarChrome.Thumb thumb(double maxScroll) {
        return ScrollbarChrome.thumb(scrolls[selectedCategory], viewTop(), viewBot() - viewTop(),
                maxScroll, 24, this.minecraft.getWindow().getGuiScale());
    }

    private void drawScrollbar(GuiGraphicsExtractor g) {
        double maxScroll = computeMaxScroll();
        if (maxScroll <= 0) return;
        ScrollbarChrome.Thumb thumb = thumb(maxScroll);
        ScrollbarChrome.track(g, (int) mainX() + contentWidth() + UiDensity.SCROLLBAR_GAP, viewTop(), viewBot() - viewTop(), ThemeToken.ON_BACKGROUND);
        ScrollbarChrome.draw(g, (int) mainX() + contentWidth() + UiDensity.SCROLLBAR_GAP, thumb,
                scrolls[selectedCategory].isDragging(), ThemeToken.ON_BACKGROUND);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent _ev, boolean _dbl) {
        refreshContentLayout();
        FeatureSetting.setHostBand(contentViewport());
        double mouseX = _ev.x(); double mouseY = _ev.y(); int button = _ev.button();
        float mx = mainX();

        // Match the accepted FeatureDetailScreen pointer contract: capture
        // cancellation owns the click, otherwise pointer interaction drops
        // stale setting/semantic focus before the clicked target reclaims it.
        if (FeatureSetting.cancelActiveCapture()) return true;
        FeatureSetting owner = FeatureSetting.getFocused();
        boolean scoped = com.aurora.client.ui.util.AuroraFontRenderer.enterWindowScope(this);
        try {
            if (owner instanceof com.aurora.client.screen.setting.EnumSetting<?>
                    || owner instanceof com.aurora.client.screen.setting.ColorSetting) {
                if (selectedCategory == 1 && contentViewport().contains(mouseX, mouseY)
                        && button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
                    var box = settingsLayout.bounds(owner);
                    if (box != null && owner.mouseClicked(mouseX, mouseY, button, settingX(box.x()),
                            (int) box.y(layoutScroll()), settingW(box.width()))) {
                        if (owner instanceof com.aurora.client.screen.setting.ColorSetting) activeDragSetting = owner;
                        return true;
                    }
                }
                if (owner.dismissPopup()) return true;
            }
            FeatureSetting.clearFocus();
            semanticHost.dropFocus();
            if (super.mouseClicked(_ev, _dbl)) return true;
        } finally {
            if (scoped) com.aurora.client.ui.util.AuroraFontRenderer.exitWindowScope();
        }

        // Sidebar chrome (C-2b): tab and Profiles clicks route through their
        // semantic controls — the enabled gate, exactly-one activation click,
        // and sound ownership all live in the action. The click is consumed
        // either way (an already-selected tab is the silent no-op; a release
        // through would reach nothing else in the sidebar).
        for (int i = 0; i < 2; i++) {
            float catY = navY(i);
            float catX = navX(i);
            if (Widget.inBounds(mouseX, mouseY, catX, catY, navW(), TAB_H)) {
                if (tabControls[i] != null && tabControls[i].activateFromPointer(mouseX, mouseY, button)) {
                    this.setFocused(tabControls[i]);
                }
                return true;
            }
        }
        float profY = navY(2);
        float profX = navX(2);
        if (Widget.inBounds(mouseX, mouseY, profX, profY, navW(), TAB_H)) {
            if (profilesControl != null && profilesControl.activateFromPointer(mouseX, mouseY, button)) {
                this.setFocused(profilesControl);
            }
            return true;
        }

        // Settings sub-tabs (toolbar row, outside the content viewport):
        // routed through their controls like the sidebar tabs, and
        // consumed either way (the selected page is the silent no-op).
        if (selectedCategory == 1) {
            for (int i = 0; i < pageControls.length; i++) {
                if (chromeLayout.contains(chromeLayout.at(i + 3), mouseX, mouseY, 0)) {
                    if (pageControls[i] != null && pageControls[i].activateFromPointer(mouseX, mouseY, button)) {
                        this.setFocused(pageControls[i]);
                    }
                    return true;
                }
            }
        }

        // C-1: the content viewport is the input truth for both tabs' walk-up
        // targets. A pointer outside the band is never on a pixel the
        // content scissor paints, so no scrolled row, header, or tile may
        // answer it; a partially clipped target answers only inside its
        // visible intersection (point ∈ viewport ∩ raw rect). Chrome tested
        // above (sidebar chips, layout buttons, search field via children)
        // and the scrollbar below live outside this gate by construction.
        ClipBand vp = contentViewport();
        if (selectedCategory == 0) {
            // Layout pair (C-5): both zones route through the peer controls —
            // the enabled gate, exactly-one activation click, and the silent
            // selected no-op all live in the action. Consumed either way (the
            // 4px gap between the zones stays inert, as before).
            if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
                for (int index = 0; index < layoutControls.length; index++) {
                    if (!chromeLayout.contains(chromeLayout.at(index + 3), mouseX, mouseY, 0)) continue;
                    var control = layoutControls[index];
                    if (control != null && control.activateFromPointer(mouseX, mouseY, button)) this.setFocused(control);
                    return true;
                }
            }
            var bounds = modulesLayout.hitTest(mouseX, mouseY, layoutScroll());
            if (bounds != null) {
                    Module m = laidOutModules.get(bounds.order());
                    float tileY = bounds.y(layoutScroll());
                    boolean settingLink = button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT
                            && !isSettingsResult(m) && (resultVia.containsKey(m.id)
                            && inSubtitleBand(mouseX, mouseY, bounds.x(), bounds.width(), subtitleY(m, bounds.y(layoutScroll()), bounds.height(), bounds.width()), bounds.y(layoutScroll()) + bounds.height())
                            || MaterialSurface.unified() && Widget.inBounds(mouseX, mouseY,
                            chevronX(bounds.x(), bounds.width()), chevronY(tileY, bounds.height()), CHEVRON_BOX, CHEVRON_BOX));
                    if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT || settingLink) {
                        // Secondary (pointer-specific) card action: open the
                        // detail screen. One activation click at the site —
                        // parity with the Settings header that opens the same
                        // destination, so sound policy does not depend on the
                        // route (design language §1.1).
                        // A search result's matched-setting line is a link
                        // to that setting: the same destination as the
                        // right click, revealing the row.
                        FeatureMetadata fm = findMeta(m.id);
                        boolean opens = isSettingsResult(m) || (fm != null && fm.hasDetail() && this.minecraft != null);
                        if (opens) {
                            MinecraftSemanticFeedback.INSTANCE
                                    .play(SemanticSound.ACTIVATION);
                            if (isSettingsResult(m)) openSettingsResult(m);
                            else openDetail(m);
                        }
                        return true;
                    }
                    if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
                        // Primary card action: toggle through the semantic
                        // control (exactly-once activation + the one click).
                        SemanticActionControl control = tileControls.get(m.id);
                        if (control != null && control.activateFromPointer(mouseX, mouseY, button)) {
                            this.setFocused(control);
                        }
                        return true;
                    }
            }
        } else if (vp.contains(mouseX, mouseY)) {
            for (FeatureMetadata m : pageEntries()) {
                float y = settingsLayout.bounds(m).y(layoutScroll());
                // The toggle zone is deliberately ~10px wider than the
                // painted 28px switch (a forgiving hit target that tiles
                // the header row's right edge against the navigation zone
                // to its left) — intentional, retained (C-1 ruling); the
                // viewport gate above now intersects it with the visible
                // band like every other target. The control owns the one
                // activation (toggle + save + the switch's thumb pulse).
                if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && mouseX >= mx + contentWidth() - 40 && mouseX <= mx + contentWidth() && mouseY >= y && mouseY <= y + SETTINGS_HEADER_H) {
                    SemanticActionControl tc = headerToggleControls.get(m.id);
                    if (tc != null && tc.activateFromPointer(mouseX, mouseY, button)) {
                        this.setFocused(tc);
                    }
                    return true;
                }
                // Header (and, for detail-only entries, the hint line under
                // it) opens the detail screen through its navigation
                // control — the toggle zone above keeps priority. Walk must
                // mirror renderSettingsLive.
                if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && m.hasDetail() && this.minecraft != null
                        && mouseX >= mx + 4 && mouseX <= mx + contentWidth() - 42
                        && mouseY >= y && mouseY < y + SETTINGS_HEADER_H + (m.settingsDetailOnly ? SETTINGS_HINT_H : 0)) {
                    SemanticActionControl nav = headerNavControls.get(m.id);
                    if (nav != null && nav.activateFromPointer(mouseX, mouseY, button)) {
                        this.setFocused(nav);
                    }
                    return true;
                }
            }
            var box = settingsLayout.hitTest(mouseX, mouseY, layoutScroll());
            if (box != null && box.key() instanceof FeatureSetting setting) {
                boolean settingScoped = com.aurora.client.ui.util.AuroraFontRenderer.enterWindowScope(this);
                boolean handled;
                try {
                    handled = setting.mouseClicked(mouseX, mouseY, button, settingX(box.x()), (int) box.y(layoutScroll()), settingW(box.width()));
                } finally {
                    if (settingScoped) com.aurora.client.ui.util.AuroraFontRenderer.exitWindowScope();
                }
                if (handled) {
                    activeDragSetting = setting;
                    if (FeatureSetting.getFocused() == setting) focusedSettingVisible = true;
                    semanticHost.focusAt(mouseX, mouseY);
                    return true;
                }
            }
        }

        double maxScroll = computeMaxScroll();
        if (maxScroll > 0 && button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && mouseX >= mx + contentWidth() && mouseX < mx + contentWidth() + UiDensity.SCROLLBAR_GAP + UiDensity.SCROLLBAR_W + 4 && mouseY >= viewTop() && mouseY <= viewBot()) {
            // Grab-where-clicked: the grab offset is the cursor's distance
            // from the thumb's top at click time — the screen's historical
            // drag semantics.
            ScrollbarChrome.Thumb thumb = thumb(maxScroll);
            scrolls[selectedCategory].beginThumbDrag(mouseY - thumb.y());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent _ev, double dx, double dy) {
        double mouseX = _ev.x(); double mouseY = _ev.y(); int button = _ev.button();
        SmoothScroll scroll = scrolls[selectedCategory];
        if (scroll.isDragging()) {
            refreshContentLayout();
            // jumpCurrent = false: only the target moves, the rendered
            // position keeps easing toward it while dragging (the screen's
            // historical feel).
            scroll.dragThumb(mouseY, viewTop(), viewBot() - viewTop(), computeMaxScroll(), 24, false);
            return true;
        }
        if (activeDragSetting != null && activeDragSetting.mouseDragged(mouseX, mouseY, button, dx, dy, 0, 0, settingW(contentWidth()))) {
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
        if (focused != null && focusedSettingVisible && focused.onScroll(vertical)) return true;
        refreshContentLayout();
        scrolls[selectedCategory].wheel(vertical, 30, computeMaxScroll());
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
        for (FeatureMetadata metadata : allPageEntries)
            for (FeatureSetting setting : metadata.settings)
                if (setting.resetKeyPressed(this.getFocused(), _kev)) return true;
        // C-8 Escape rule for the Modules search field (the pack browser's
        // canonical text-field contract): Escape while typing UNFOCUSES the
        // field and is consumed — vanilla EditBox ignores Escape, so the
        // fall-through used to close the whole screen on the first press.
        // The second press (no active field) reaches the screen.
        // Search (2026-11-12): Escape clears a non-empty query first; with
        // the query empty it falls through and closes the screen (Instant
        // Search keeps the field focused, so an unfocus-only step would cost
        // every close an extra press).
        if (selectedCategory == 0 && searchField != null && !searchQuery.isEmpty()
                && _kev.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            searchField.setValue("");
            return true;
        }
        FeatureSetting focused = FeatureSetting.getFocused();
        if (focused != null && focusedSettingVisible && focused.onKeyPress(_kev)) return true;
        // C-3 roving interceptor — BEFORE super (Screen.keyPressed reaches the
        // container walk via invokespecial, so this is the only seam): while a
        // grouped control holds focus (sidebar tabs, the hosted Accent peers),
        // the group's arrows move focus silently and are consumed. Selection
        // never follows — Enter/Space/click stay the only selection paths.
        var before = this.getFocused();
        boolean handled = SemanticControlGroup.rove(before, _kev, this::setFocused) || super.keyPressed(_kev);
        if (selectedCategory == 1 && com.aurora.client.ui.interaction.FocusReveal.navigation(_kev)) {
            com.aurora.client.ui.interaction.FocusReveal.afterNavigation(before, getFocused(), scrolls[1],
                    settingsLayout, layoutScroll());
        }
        return handled;
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent _ev) {
        FeatureSetting focused = FeatureSetting.getFocused();
        if (focused != null && focusedSettingVisible && focused.onCharTyped(_ev)) return true;
        if (FeatureSetting.hasActiveCapture()) return true;
        if (selectedCategory == 0 && searchField != null && searchField.visible) {
            this.setFocused(searchField);
            searchField.setFocused(true);
            return searchField.charTyped(_ev);
        }
        if (selectedCategory == 1) for (FeatureMetadata m : pageEntries())
            for (FeatureSetting s : m.settings) if (s.resumeSearch(_ev)) {
                this.setFocused(null);
                return true;
            }
        // Search covers both tabs: typing on the Settings tab with nothing
        // there to take it starts a search, whose results show on Modules.
        if (selectedCategory == 1 && searchField != null && this.getFocused() == null
                && !Character.isWhitespace(_ev.codepoint())) {
            selectCategory(0);
            this.setFocused(searchField);
            searchField.setFocused(true);
            return searchField.charTyped(_ev);
        }
        return super.charTyped(_ev);
    }

    /** Immediate off-tab/removal boundary; render will rebuild availability on return. */
    private void invalidateSettingsSemantics() {
        semanticHost.deactivateAll();
        focusedSettingVisible = false;
        for (FeatureMetadata metadata : allPageEntries) {
            for (FeatureSetting setting : metadata.settings) {
                setting.onInteractionAvailabilityChanged(false);
            }
        }
    }

    @Override
    public void removed() {
        invalidateSettingsSemantics();
        FeatureSetting.clearHostBand(); // C-8 — drop the published host band
        super.removed();
    }
}
