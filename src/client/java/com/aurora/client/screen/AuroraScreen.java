package com.aurora.client.screen;

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
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.ClipBand;
import com.aurora.client.ui.util.MaterialIconRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.ui.util.UiLayerCache;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.HoverAnim;
import com.aurora.client.util.ScrollFade;
import com.aurora.client.util.SmoothScroll;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
 * <p>Phase C-5 (2026-09-20): SegmentedControl conformance closed the last
 * deferred peer-selection surface on this screen. The Theme entry's three
 * segmented rows (Mode, Corner Style, Glass Style — 7 peers) materialize
 * their component-owned controls through the same generic C-2 hosting loop
 * (zero screen-side segment logic) and rove via the C-3 seam; the layout
 * pair adopted the peer semantic primitive on its existing 20×20 painter
 * (canonical 140 ms hover, focus hairline, one-click selection with a
 * silent no-op on the active layout, Left/Right ring).
 */
public class AuroraScreen extends Screen implements ThemedScreen {

    /** §8.3 canonical hover duration — sidebar chrome, cards, headers alike (C-2b). */
    private static final long HOVER_MS = 140L;

    private static final float BOX_W = 360;
    private static final float BOX_H = 240;

    // Sidebar nav tabs: one pitch drives every tab's Y (22px tab + 6px gap),
    // so the three gaps cannot drift apart. TAB_H must stay in sync with the
    // painted chip height below.
    private static final float TAB_FIRST_Y = 48;
    private static final float TAB_PITCH = 28;
    private static final float TAB_H = 22;

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
     * header (design language §6: spacing between groups must clearly
     * exceed the 4px row-to-row gap inside a group, so entries read as
     * distinct clusters — was a bare 8, only twice the row gap). The
     * render, scroll-height, and click walks must all use this.
     */
    private static final int SETTINGS_ENTRY_GAP = 16;

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
    /** Canonical hover animators for the pair — pointer-only (C-5). */
    private final HoverAnim[] layoutHovers = {
            HoverAnim.symmetric(HOVER_MS),
            HoverAnim.symmetric(HOVER_MS),
    };
    /** One control per module card, keyed by stable module id (finite: the ModuleManager set). */
    private final Map<String, SemanticActionControl> tileControls = new HashMap<>();
    /** Settings-header navigation, keyed by entry id — only entries with a detail screen. */
    private final Map<String, SemanticActionControl> headerNavControls = new HashMap<>();
    /** The four header toggles, keyed by entry id. */
    private final Map<String, SemanticActionControl> headerToggleControls = new HashMap<>();
    /** Canonical hover animators — pointer-only targets, keyed by stable identity (never list position). */
    private final HoverAnim[] tabHovers = {
            HoverAnim.symmetric(HOVER_MS),
            HoverAnim.symmetric(HOVER_MS),
    };
    private final HoverAnim profilesHover = HoverAnim.symmetric(HOVER_MS);
    private final Map<String, HoverAnim> tileHovers = new HashMap<>();
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

    private final UiLayerCache layerCache = new UiLayerCache();

    public AuroraScreen() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        super.init();
        semanticHost.beginRebuild();
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
        // Explicit traversal position: the vanilla search field is first.
        // It is visible/focusable on Modules and hidden on Settings.
        this.addWidget(searchField);

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
        for (Module m : ModuleManager.getInstance().getModules()) {
            tileControls.put(m.id, tileControl(m));
        }

        // HOSTABLE_NOW only: ask every inline component for the semantic
        // controls it already owns. This deterministically yields the two
        // Text & Fonts enums, Accent's ten visual-order peers, the three
        // segmented rows' seven peers, then the Interface enum (20 controls
        // total — C-5 added the segmented groups to C-2's thirteen).
        // Segmented peers materialize per SEGMENT, one control each.
        hostedSettingControls.clear();
        for (FeatureMetadata metadata : FeatureRegistry.settings()) {
            // Header chrome precedes the entry's rows in traversal order
            // (visual top-to-bottom): the navigation control for entries
            // with a detail screen, then the header toggle.
            if (metadata.hasDetail()) {
                headerNavControls.put(metadata.id, headerNavControl(metadata));
            }
            headerToggleControls.put(metadata.id, headerToggleControl(metadata));
            for (FeatureSetting setting : metadata.settings) {
                List<SemanticActionControl> controls = List.copyOf(setting.interactionControls());
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
        String name = i == 0 ? "Mods" : "Settings";
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
                    if (this.minecraft != null) this.minecraft.setScreen(new ProfileManagerScreen(this));
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
        control = new SemanticActionControl(SemanticAction.button(
                Component.literal(m.name),
                () -> Component.literal(m.description),
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
                        this.minecraft.setScreen(new FeatureDetailScreen(this, m));
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
            // families' teardown hook; uniform plumbing, none hosted here).
            for (FeatureMetadata metadata : FeatureRegistry.settings()) {
                for (FeatureSetting setting : metadata.settings) {
                    setting.onInteractionAvailabilityChanged(false);
                }
            }
        }
        selectedCategory = i;
        semanticHost.deactivateAll();
        focusedSettingVisible = false;
    }

    private float boxX() { return (this.width - BOX_W) / 2.0f; }
    private float boxY() { return (this.height - BOX_H) / 2.0f; }
    private float mainX() { return boxX() + 90; }
    private float mainY() { return boxY() + 12; }

    /**
     * The single authoritative CONTENT VIEWPORT (Phase C-1): the exact
     * rectangle the content scissor paints — {@code [mainX(), boxY()+36]}
     * to {@code [mainX()+254, boxY()+230]}. One truth drives the scissor,
     * the tile/row render cull, the pointer hit-test, the tile hover test,
     * and the focused-setting keyboard availability: a pixel is
     * interactive exactly when it is visible
     * ({@code rawBounds ∩ viewport} is the actionable region;
     * DESIGN_LANGUAGE §13.3). Before C-1 these were three disagreeing
     * bands — the scissor, a 12px-tighter cull band, and a 180px scroll
     * extent — which is how scrolled-off rows stayed clickable.
     *
     * <p>Two regions deliberately remain SEPARATE from this truth (see
     * {@link #viewTop()}: the scrollbar track band, and
     * {@link #computeMaxScroll()}'s 180px visible-height tuning — changing
     * either would change thumb placement or scroll physics, both outside
     * C-1's no-visual-change contract.
     */
    private ClipBand contentViewport() {
        return new ClipBand((int) mainX(), (int) (boxY() + CONTENT_TOP_INSET),
                CONTENT_W, (int) (BOX_H - CONTENT_TOP_INSET - CONTENT_BOT_INSET));
    }

    /** Content scissor's inset from the window's top edge (below the search/layout band). */
    private static final int CONTENT_TOP_INSET = 36;
    /** Content scissor's inset from the window's bottom edge. */
    private static final int CONTENT_BOT_INSET = 10;
    /** Content column width (drives the scissor, the row width, and the viewport's x extent). */
    private static final int CONTENT_W = 254;

    /**
     * The SCROLLBAR TRACK band — chrome geometry only (thumb placement,
     * the grab zone, and the drag mapping), NOT the content viewport. It
     * is deliberately inset from the content scissor (12px at the top so
     * the thumb clears the scroll-fade band, 2px at the bottom) and its
     * 180px height is the historical {@link #computeMaxScroll()} visible
     * height; preserving it keeps the thumb's pixels and the scroll extent
     * byte-identical (C-1 changes no scroll physics or thumb appearance).
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
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (liveWorldBackdrop()) return;
        if (GlassSurface.renderMenuPanorama(this, g, delta)) return;
        super.renderBackground(g, mouseX, mouseY, delta);
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
        return (Math.round(a * 255f) << 24) | (ThemeManager.color(t) & 0x00FFFFFF);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
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
        // suppression, failure) glassWindow is false and the cached chrome
        // below carries the old opaque fill + outline instead (see the glass
        // bit in the version hash, which forces the re-raster on the switch).
        GlassSurface.beginGlassPass();
        float radius = ThemeManager.current().roundness().radius();
        boolean glassWindow = GlassSurface.container(g, boxX(), boxY(), BOX_W, BOX_H, radius);

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

        // Chrome + tab materials — after the capture block above (a tint fill
        // painted during capture would rasterize into the cache). Tiles and
        // the Settings tab's inline rows paint under the tracked scissor so
        // their deferred rims keep the list clip.
        List<Module> mods = filteredModules(); // one fetch drives pass + content (§10)
        paintGlassPass(g, mods);

        // ---- 2. Dim — closes the glass pass, veils every surface like it ----
        // veils the world, flushes the deferred rim finishes, and from here
        // on any glass body paint is reported as an ordering violation.
        GlassSurface.overlayDim(g, this.width, this.height);

        // Cached chrome — one textured blit (a content layer above the dim,
        // exactly where it always sat).
        layerCache.blit(g, this.width, this.height);

        renderLive(g, mods, mouseX, mouseY, delta);

        // Availability has now been derived from this frame's component
        // bounds. An off-tab/off-viewport control cannot retain vanilla
        // focus even though its action already rejects activation.
        semanticHost.finishAvailabilitySweep();

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

    /**
     * Card/toggle geometry for module index {@code i}; null when the tile
     * shares no pixel with {@code vp} (the render cull and the click walk
     * share this one answer — a culled tile is neither painted nor
     * clickable). Returns {@code [cx,cy,cw,ch]}.
     */
    private float[] cardBounds(int i, List<Module> mods, ClipBand vp) {
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
        if (!vp.intersects(cx, cy, cw, ch)) return null;
        return new float[]{cx, cy, cw, ch};
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
    private void paintGlassPass(GuiGraphics g, List<Module> mods) {
        float bx = boxX(), by = boxY();
        ClipBand vp = contentViewport();
        // C-7: every control-scaled chrome radius on this screen (chips,
        // layout buttons, tiles) resolves through the SAME theme token in
        // BOTH parent-owned and fallback paths — Square mode squares them all.
        // The former literals (5/4/6) were pre-token survivals; the tile
        // literal 6 equals radiusSmall's ROUND value, so only the chips
        // (5→6) and layout buttons (4→6) shift one/two ROUND corner pixels.
        float ctrlRadius = ThemeManager.current().roundness().radiusSmall();
        for (int i = 0; i < 2; i++) {
            float catY = by + TAB_FIRST_Y + i * TAB_PITCH;
            chipGlass[i] = GlassSurface.control(g, bx + 8, catY, 64, TAB_H,
                    ctrlRadius, selectedCategory == i);
        }
        float profY = by + TAB_FIRST_Y + 2 * TAB_PITCH;
        profGlass = GlassSurface.control(g, bx + 8, profY, 64, TAB_H, ctrlRadius);

        if (selectedCategory == 0) {
            float mx = mainX(), my = mainY();
            layoutGlass[0] = GlassSurface.control(g, mx, my, 20, 20, ctrlRadius, !gridLayout);
            layoutGlass[1] = GlassSurface.control(g, mx + 24, my, 20, 20, ctrlRadius, gridLayout);
            // Search field — positioned here (the pass runs before
            // renderModulesLive positions it again) and driven through its
            // own split (EditBoxMixin carries the frame-stamp scheme).
            searchField.visible = true;
            searchField.setX(Math.round(mx + 48));
            searchField.setY(Math.round(my));
            searchField.setWidth(202);
            ((GlassEditBox) searchField).aurora$renderGlassPass(g);

            GlassSurface.enableScissor(g, vp.x, vp.y, vp.xEnd(), vp.yEnd());
            if (highlightedTileGlass.length < mods.size()) {
                highlightedTileGlass = new boolean[mods.size()];
            }
            for (int i = 0; i < mods.size(); i++) {
                float[] b = cardBounds(i, mods, vp);
                if (b == null) {
                    highlightedTileGlass[i] = false;
                    continue;
                }
                if (mods.get(i).isEnabled()) {
                    highlightedTileGlass[i] = GlassSurface.control(g,
                            b[0], b[1], b[2], b[3], ctrlRadius, true,
                            BlurPanelRenderer.Priority.ROW);
                } else {
                    highlightedTileGlass[i] = false;
                    MaterialSurface.embeddedControl(g, b[0], b[1], b[2], b[3], ctrlRadius, false);
                }
            }
            GlassSurface.disableScissor(g);
        } else {
            float mx = mainX(), my = mainY();
            GlassSurface.enableScissor(g, vp.x, vp.y, vp.xEnd(), vp.yEnd());
            float y = my + 38 - (float) scrolls[1].current();
            for (FeatureMetadata m : FeatureRegistry.settings()) {
                y += 22; // header row — plain text + an opaque toggle, no glass
                if (m.settingsDetailOnly) {
                    y += SETTINGS_HINT_H;
                } else {
                    for (FeatureSetting s : m.settings) {
                        int rh = s.height();
                        if (vp.intersects(mx + 4, y, 246, rh)) {
                            s.renderGlassPass(g, (int) (mx + 4), (int) y, 246);
                        }
                        y += rh + 4;
                    }
                }
                y += SETTINGS_ENTRY_GAP;
            }
            GlassSurface.disableScissor(g);
        }
    }

    private void renderLive(GuiGraphics g, List<Module> mods, int mouseX, int mouseY, float delta) {
        float bx = boxX(), by = boxY();
        Font tr = this.font;
        // Same token as paintGlassPass's glass surfaces — the flat fallback
        // corners and the glass corners derive from one radius (C-7).
        float ctrlRadius = ThemeManager.current().roundness().radiusSmall();

        g.drawString(tr, "AURORA", (int) (bx + 16), (int) (by + 16), ThemeManager.color(ThemeToken.ACCENT), false);

        String[] cats = {"Mods", "Settings"};
        for (int i = 0; i < 2; i++) {
            float catY = by + TAB_FIRST_Y + i * TAB_PITCH;
            boolean sel = selectedCategory == i;
            // Canonical hover (§8.3, C-2b): POINTER only, symmetric 140 ms —
            // never driven by selection. Selection is the persistent
            // channel: the stained glass tint (or the flat accent wash
            // below), visible at hover 0; the selected tab's label
            // treatment is fixed, so the channels can never alias.
            boolean hover = Widget.inBounds(mouseX, mouseY, bx + 8, catY, 64, TAB_H);
            float hoverT = tabHovers[i].update(hover);
            SemanticActionControl control = tabControls[i];
            if (control != null) {
                control.setBounds((int) (bx + 8), (int) catY, 64, (int) TAB_H);
                control.setAvailable(true); // static sidebar chrome — always interactive
                control.updatePointer(mouseX, mouseY);
            }
            // Raised glass was painted pre-dim: neutral for the inactive tab,
            // accent-stained for the selected tab. Preserve the established
            // flat fallback if the glass renderer declines this frame.
            if (!chipGlass[i]) {
                if (sel) {
                    RenderUtil.drawRoundedRectAA(g, bx + 8, catY, 64, 22, ctrlRadius,
                            alpha(ThemeToken.ACCENT, 0x26 / 255f));
                } else if (hoverT > 0f) {
                    RenderUtil.drawRoundedRectAA(g, bx + 8, catY, 64, 22, ctrlRadius,
                            AuroraAnim.lerpArgb(0x00000000,
                                    ThemeManager.surfaceColor(ThemeToken.SURFACE_VARIANT), hoverT));
                }
            }
            int txt = chipGlass[i] && sel ? ThemeManager.color(ThemeToken.ON_ACCENT)
                    : sel ? ThemeManager.color(ThemeToken.ON_BACKGROUND)
                    : AuroraAnim.lerpArgb(
                            ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED),
                            ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY), hoverT);
            g.drawString(tr, cats[i], (int) (bx + 16), (int) (catY + 7), txt, false);
            // Keyboard focus: the Button-family 1 px accent hairline — an
            // independent channel from both the selection tint and hover.
            if (control != null && control.isFocused()) {
                RenderUtil.drawRoundedOutlineAA(g, bx + 8, catY, 64, 22, ctrlRadius, 1.0f,
                        ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ACCENT), 0x99));
            }
        }

        float profY = by + TAB_FIRST_Y + 2 * TAB_PITCH;
        boolean pHover = Widget.inBounds(mouseX, mouseY, bx + 8, profY, 64, TAB_H);
        float profT = profilesHover.update(pHover);
        if (profilesControl != null) {
            profilesControl.setBounds((int) (bx + 8), (int) profY, 64, (int) TAB_H);
            profilesControl.setAvailable(true);
            profilesControl.updatePointer(mouseX, mouseY);
        }
        // Profiles is neutral raised glass; retain its hover fallback when
        // live glass is unavailable.
        if (!profGlass && profT > 0f) {
            RenderUtil.drawRoundedRectAA(g, bx + 8, profY, 64, 22, ctrlRadius,
                    AuroraAnim.lerpArgb(0x00000000,
                            ThemeManager.surfaceColor(ThemeToken.SURFACE_VARIANT), profT));
        }
        g.drawString(tr, "Profiles", (int) (bx + 16), (int) (profY + 7),
                AuroraAnim.lerpArgb(
                        ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED),
                        ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY), profT), false);
        if (profilesControl != null && profilesControl.isFocused()) {
            RenderUtil.drawRoundedOutlineAA(g, bx + 8, profY, 64, 22, ctrlRadius, 1.0f,
                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ACCENT), 0x99));
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
                scrolls[selectedCategory].current(), ThemeManager.color(ThemeToken.WINDOW_FILL));

        drawScrollbar(g);
    }

    private void renderModulesLive(GuiGraphics g, List<Module> mods, int mouseX, int mouseY, float delta) {
        float mx = mainX(), my = mainY();
        Font tr = this.font;
        ClipBand vp = contentViewport();

        float btnY = my, btnSize = 20;
        // The layout pair (C-5): canonical pointer-only hover per peer — never
        // gated on selection — plus bounds/pointer sync for the semantic
        // controls (Modules-tab chrome: always interactive while painted; the
        // Settings tab never re-marks them, so the frame-start sweep makes
        // them unavailable there).
        for (int i = 0; i < 2; i++) {
            float lx = i == 0 ? mx : mx + 24;
            boolean hover = Widget.inBounds(mouseX, mouseY, lx, btnY, btnSize, btnSize);
            float hoverT = layoutHovers[i].update(hover);
            SemanticActionControl control = layoutControls[i];
            if (control != null) {
                control.setBounds((int) lx, (int) btnY, (int) btnSize, (int) btnSize);
                control.setAvailable(true);
                control.updatePointer(mouseX, mouseY);
            }
            drawLayoutButton(g, lx, btnY, btnSize, (i == 1) == gridLayout, hoverT,
                    control != null && control.isFocused(), i == 0, layoutGlass[i]);
        }

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
        // not for rendering) so typed text + caret actually draw. Its glass
        // surface was already painted pre-dim (paintGlassPass drives the
        // EditBoxMixin split); renderWidget draws content only.
        searchField.render(g, mouseX, mouseY, delta);

        g.enableScissor(vp.x, vp.y, vp.xEnd(), vp.yEnd());
        // Same theme-resolved control radius the glass pass paints the tiles
        // with (C-7 — the former literal 6 equals radiusSmall's ROUND value,
        // so ROUND pixels are unchanged and SQUARE squares the tiles).
        float tileRadius = ThemeManager.current().roundness().radiusSmall();
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            float[] b = cardBounds(i, mods, vp);
            if (b == null) continue;
            float cx = b[0], cy = b[1], cw = b[2], ch = b[3];
            // Hover reads the VISIBLE intersection: the pointer must be on
            // a pixel the scissor actually paints. A tile scrolled past the
            // band's edge no longer lights up from a pointer resting on its
            // hidden half (render truth == hover truth, C-1). The target is
            // the POINTER only (C-2b): the enabled state below is the
            // persistent channel and never pins the animator.
            boolean hover = vp.contains(mouseX, mouseY)
                    && mouseX >= cx && mouseX <= cx + cw && mouseY >= cy && mouseY <= cy + ch;
            float hoverT = tileHover(m.id).update(hover);
            SemanticActionControl control = tileControls.get(m.id);
            if (control != null) {
                // The walk only reaches tiles that intersect the viewport,
                // so this re-mark IS the ClipBand truth (C-2 partial
                // visibility: any non-empty intersection is available).
                control.setBounds((int) cx, (int) cy, (int) cw, (int) ch);
                control.setAvailable(true);
                control.updatePointer(mouseX, mouseY);
            }
            boolean on = m.isEnabled();

            // Neutral tiles inherit the enclosing window. Enabled tiles are
            // isolated stained glass; if glass declines, preserve the old
            // accent-wash + bright-outline fallback instead of disappearing.
            boolean highlightedGlass = on && highlightedTileGlass(i);
            if (on && !highlightedGlass) {
                RenderUtil.drawRoundedRectAA(g, cx, cy, cw, ch, tileRadius,
                        ThemeManager.surfaceColor(ThemeToken.SURFACE));
                RenderUtil.drawRoundedRectAA(g, cx, cy, cw, ch, tileRadius,
                        alpha(ThemeToken.ACCENT, 0x14 / 255f));
                RenderUtil.drawRoundedOutlineAA(g, cx, cy, cw, ch, tileRadius, 1.0f,
                        ThemeManager.color(ThemeToken.ACCENT));
            }
            if (hoverT > 0f) {
                RenderUtil.drawRoundedRectAA(g, cx, cy, cw, ch, tileRadius,
                        alpha(ThemeToken.ON_BACKGROUND, (0x1A / 255f) * hoverT));
            }
            // Keyboard focus: the Button-family hairline — independent of
            // both the enabled stain and the hover wash.
            if (control != null && control.isFocused()) {
                RenderUtil.drawRoundedOutlineAA(g, cx, cy, cw, ch, tileRadius, 1.0f,
                        ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ACCENT), 0x99));
            }

            if (gridLayout) {
                drawTileIcon(g, tr, m, cx + cw / 2f, cy + 30, 28f, on, highlightedGlass);
                String name = fit(tr, m.name, (int) cw - 8);
                g.drawString(tr, name, (int) (cx + (cw - tr.width(name)) / 2f), (int) (cy + ch - 20),
                        ThemeManager.color(ThemeToken.ON_BACKGROUND), false);
            } else {
                drawTileIcon(g, tr, m, cx + 20, cy + ch / 2f, 16f, on, highlightedGlass);
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

    private void renderSettingsLive(GuiGraphics g, int mouseX, int mouseY, float delta, float ctrlRadius) {
        float mx = mainX(), my = mainY();
        Font tr = this.font;
        searchField.visible = false;
        ClipBand vp = contentViewport();

        g.enableScissor(vp.x, vp.y, vp.xEnd(), vp.yEnd());
        float y = my + 38 - (float) scrolls[1].current();
        for (FeatureMetadata m : FeatureRegistry.settings()) {
            int headerH = 22;
            if (vp.intersects(mx + 4, y, CONTENT_W - 4, headerH)) {
                // Header navigation (C-2b): the entries WITH a detail
                // screen navigate (same family as the sidebar tabs' action
                // contract); custom_title has none — its header is grouping
                // chrome and renders neither affordance nor control. Hover
                // is the chevron affordance brightening, pointer-only
                // canonical 140 ms — the row is text chrome, so no fill
                // wash is invented for it.
                boolean navHover = m.hasDetail()
                        && Widget.inBounds(mouseX, mouseY, mx + 4, y, CONTENT_W - 4 - 42, headerH);
                float navT = m.hasDetail() ? headerHover(m.id).update(navHover) : 0f;
                g.drawString(tr, m.displayName, (int) (mx + 4), (int) (y + 6), ThemeManager.color(ThemeToken.ON_BACKGROUND), false);
                // Detail-screen affordance: a header click opens the entry's
                // detail screen (the Settings tab's counterpart of the
                // Modules tab's right-click-to-detail) — the chevron marks
                // the row as navigable.
                if (m.hasDetail()) {
                    MaterialIconRenderer.drawIcon(g, tr, FeatureIcons.get("_dropdown_expand_more"),
                            mx + 254 - 40 - 12, y + 11, 9,
                            AuroraAnim.lerpArgb(
                                    ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED),
                                    ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY), navT));
                    SemanticActionControl nav = headerNavControls.get(m.id);
                    if (nav != null) {
                        // The navigation zone mirrors the click walk's rect
                        // (left of the toggle zone); the header shares a
                        // pixel with the viewport, so it is available.
                        nav.setBounds((int) (mx + 4), (int) y, CONTENT_W - 4 - 42, headerH);
                        nav.setAvailable(true);
                        nav.updatePointer(mouseX, mouseY);
                        if (nav.isFocused()) {
                            RenderUtil.drawRoundedOutlineAA(g, mx + 4, y, CONTENT_W - 4 - 42, headerH, ctrlRadius, 1.0f,
                                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ACCENT), 0x99));
                        }
                    }
                }
                ToggleSwitch t = sectionToggles.get(m.id);
                if (t != null) {
                    float tw = 28, th = 15;
                    float tx = mx + 254 - tw - 10;
                    t.layout(tx, y + 4, tw, th);
                    t.renderShapes(g, tx, y + 4, tw, th);
                    t.renderOverlay(g, tx, y + 4, tw, th, mouseX, mouseY);
                    SemanticActionControl tc = headerToggleControls.get(m.id);
                    if (tc != null) {
                        // The forgiving 40px zone IS the control (the C-1
                        // ruling) — pointer, keyboard, and narration share
                        // one target; the switch paints the focus hairline
                        // itself via focusedVisual (BooleanSetting's sync).
                        tc.setBounds((int) (mx + 254 - 40), (int) (y + 4), 40, 15);
                        tc.setAvailable(true);
                        tc.updatePointer(mouseX, mouseY);
                        t.focusedVisual(tc.isFocused());
                    }
                }
            }
            y += headerH;
            if (m.settingsDetailOnly) {
                // Detail-only entry: one muted hint line in place of the
                // inline rows — the merged list lives behind the header
                // click. The hint height must match the walks in
                // computeMaxScroll and mouseClicked.
                if (vp.intersects(mx + 4, y, CONTENT_W - 4, SETTINGS_HINT_H)) {
                    g.drawString(tr, "Click to configure", (int) (mx + 4), (int) (y + 1),
                            ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED), false);
                }
                y += SETTINGS_HINT_H;
            } else {
                for (FeatureSetting s : m.settings) {
                    int rh = s.height();
                    boolean visible = vp.intersects(mx + 4, y, 246, rh);
                    if (visible) {
                        s.render(g, (int) (mx + 4), (int) y, 246, mouseX, mouseY);
                    }
                    boolean anyControlAvailable = false;
                    List<SemanticActionControl> controls = hostedSettingControls.getOrDefault(s, List.of());
                    for (SemanticActionControl control : controls) {
                        // Partial-visibility policy: any non-empty
                        // intersection of the ACTIONABLE control rect with
                        // C-1's authoritative viewport is available. Edge
                        // contact alone is not (ClipBand is half-open).
                        boolean available = visible && vp.intersects(
                                control.getX(), control.getY(),
                                control.getWidth(), control.getHeight());
                        semanticHost.setAvailable(control, available);
                        anyControlAvailable |= available;
                    }
                    // C-1 render/input availability coupling: the row (and,
                    // when it holds the focused setting, keyboard routing)
                    // is available exactly while it shares a pixel with the
                    // viewport. onInteractionAvailabilityChanged is the
                    // FeatureDetailScreen hook — capture families cancel on
                    // false; this tab hosts none today, so it is uniform
                    // plumbing, not a behavior change.
                    s.onInteractionAvailabilityChanged(
                            controls.isEmpty() ? visible : anyControlAvailable);
                    if (s == FeatureSetting.getFocused()) {
                        focusedSettingVisible = visible;
                    }
                    y += rh + 4;
                }
            }
            y += SETTINGS_ENTRY_GAP;
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
    private void drawLayoutButton(GuiGraphics g, float x, float y, float size, boolean selected,
                                  float hoverT, boolean focused, boolean list, boolean btnGlass) {
        // Same theme-resolved control radius as the glass pass's surfaces of
        // this pair (C-7 — was a literal 4 that ignored Square mode).
        float ctrlRadius = ThemeManager.current().roundness().radiusSmall();
        // Surface: the list/grid pair is a segmented control — RAISED glass
        // (painted pre-dim by paintGlassPass), accent-STAINED on the active
        // one. Content here: the canonical hover wash on glass (both peers),
        // the flat fills + borders on decline, then the icon.
        if (btnGlass) {
            if (hoverT > 0f) {
                RenderUtil.drawRoundedRectAA(g, x, y, size, size, ctrlRadius,
                        alpha(ThemeToken.ON_BACKGROUND, (0x1A / 255f) * hoverT));
            }
        } else {
            int bg = selected ? alpha(ThemeToken.ACCENT, 0x26 / 255f)
                    : AuroraAnim.lerpArgb(
                            ThemeManager.surfaceColor(ThemeToken.SURFACE),
                            ThemeManager.surfaceColor(ThemeToken.SURFACE_VARIANT), hoverT);
            int border = selected ? ThemeManager.color(ThemeToken.ACCENT) : alpha(ThemeToken.ON_BACKGROUND, 0x08f);
            RenderUtil.drawRoundedRectAA(g, x, y, size, size, ctrlRadius, bg);
            RenderUtil.drawRoundedOutlineAA(g, x, y, size, size, ctrlRadius, 1.0f, border);
            if (!selected && hoverT > 0f) {
                RenderUtil.drawRoundedRectAA(g, x, y, size, size, ctrlRadius,
                        alpha(ThemeToken.ON_BACKGROUND, (0x1A / 255f) * hoverT));
            }
        }
        // Keyboard focus: the Button-family hairline — independent of both
        // the enabled stain and the hover wash.
        if (focused) {
            RenderUtil.drawRoundedOutlineAA(g, x, y, size, size, ctrlRadius, 1.0f,
                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ACCENT), 0x99));
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
            if (m.settingsDetailOnly) h += SETTINGS_HINT_H;
            else for (FeatureSetting s : m.settings) h += s.height() + 4;
            h += SETTINGS_ENTRY_GAP;
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

    private ScrollbarChrome.Thumb thumb(double maxScroll) {
        return ScrollbarChrome.thumb(scrolls[selectedCategory], viewTop(), viewBot() - viewTop(),
                maxScroll, 24, this.minecraft.getWindow().getGuiScale());
    }

    private void drawScrollbar(GuiGraphics g) {
        double maxScroll = computeMaxScroll();
        if (maxScroll <= 0) return;
        ScrollbarChrome.Thumb thumb = thumb(maxScroll);
        // The thumb's capsule radius is MECHANICAL — direct-manipulation
        // chrome in the toggle-track/slider-track family, Square-mode-exempt
        // by ruling (C-7; same at ManagerListScreen and the pack browser's
        // two tracks). Width/drag physics untouched.
        ScrollbarChrome.draw(g, (int) mainX() + 258, thumb,
                scrolls[selectedCategory].isDragging(), ThemeToken.ON_BACKGROUND);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent _ev, boolean _dbl) {
        double mouseX = _ev.x(); double mouseY = _ev.y(); int button = _ev.button();
        float bx = boxX(), by = boxY(), mx = mainX(), my = mainY();

        // Match the accepted FeatureDetailScreen pointer contract: capture
        // cancellation owns the click, otherwise pointer interaction drops
        // stale setting/semantic focus before the clicked target reclaims it.
        if (FeatureSetting.cancelActiveCapture()) return true;
        FeatureSetting.clearFocus();
        semanticHost.dropFocus();
        if (super.mouseClicked(_ev, _dbl)) return true;

        // Sidebar chrome (C-2b): tab and Profiles clicks route through their
        // semantic controls — the enabled gate, exactly-one activation click,
        // and sound ownership all live in the action. The click is consumed
        // either way (an already-selected tab is the silent no-op; a release
        // through would reach nothing else in the sidebar).
        for (int i = 0; i < 2; i++) {
            float catY = by + TAB_FIRST_Y + i * TAB_PITCH;
            if (Widget.inBounds(mouseX, mouseY, bx + 8, catY, 64, TAB_H)) {
                if (tabControls[i] != null && tabControls[i].activateFromPointer(mouseX, mouseY, button)) {
                    this.setFocused(tabControls[i]);
                }
                return true;
            }
        }
        float profY = by + TAB_FIRST_Y + 2 * TAB_PITCH;
        if (Widget.inBounds(mouseX, mouseY, bx + 8, profY, 64, TAB_H)) {
            if (profilesControl != null && profilesControl.activateFromPointer(mouseX, mouseY, button)) {
                this.setFocused(profilesControl);
            }
            return true;
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
            if (button == 0 && mouseX >= mx && mouseX <= mx + 20 && mouseY >= my && mouseY <= my + 20) {
                if (layoutControls[0] != null && layoutControls[0].activateFromPointer(mouseX, mouseY, button)) {
                    this.setFocused(layoutControls[0]);
                }
                return true;
            }
            if (button == 0 && mouseX >= mx + 24 && mouseX <= mx + 44 && mouseY >= my && mouseY <= my + 20) {
                if (layoutControls[1] != null && layoutControls[1].activateFromPointer(mouseX, mouseY, button)) {
                    this.setFocused(layoutControls[1]);
                }
                return true;
            }
            List<Module> mods = filteredModules();
            for (int i = 0; i < mods.size(); i++) {
                Module m = mods.get(i);
                float[] b = cardBounds(i, mods, vp);
                if (b == null) continue;
                if (vp.contains(mouseX, mouseY)
                        && mouseX >= b[0] && mouseX <= b[0] + b[2] && mouseY >= b[1] && mouseY <= b[1] + b[3]) {
                    if (button == 1) {
                        // Secondary (pointer-specific) card action: open the
                        // detail screen. One activation click at the site —
                        // parity with the Settings header that opens the same
                        // destination, so sound policy does not depend on the
                        // route (design language §1.1).
                        FeatureMetadata fm = findMeta(m.id);
                        if (fm != null && fm.hasDetail() && this.minecraft != null) {
                            MinecraftSemanticFeedback.INSTANCE
                                    .play(SemanticSound.ACTIVATION);
                            this.minecraft.setScreen(new FeatureDetailScreen(this, fm));
                        }
                        return true;
                    }
                    if (button == 0) {
                        // Primary card action: toggle through the semantic
                        // control (exactly-once activation + the one click).
                        SemanticActionControl control = tileControls.get(m.id);
                        if (control != null && control.activateFromPointer(mouseX, mouseY, button)) {
                            this.setFocused(control);
                        }
                        return true;
                    }
                }
            }
        } else if (vp.contains(mouseX, mouseY)) {
            float y = my + 38 - (float) scrolls[1].current();
            for (FeatureMetadata m : FeatureRegistry.settings()) {
                // The toggle zone is deliberately ~10px wider than the
                // painted 28px switch (a forgiving hit target that tiles
                // the header row's right edge against the navigation zone
                // to its left) — intentional, retained (C-1 ruling); the
                // viewport gate above now intersects it with the visible
                // band like every other target. The control owns the one
                // activation (toggle + save + the switch's thumb pulse).
                if (button == 0 && mouseX >= mx + 254 - 40 && mouseX <= mx + 254 && mouseY >= y + 4 && mouseY <= y + 19) {
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
                if (button == 0 && m.hasDetail() && this.minecraft != null
                        && mouseX >= mx + 4 && mouseX <= mx + 254 - 42
                        && mouseY >= y && mouseY <= y + 22 + (m.settingsDetailOnly ? SETTINGS_HINT_H : 0)) {
                    SemanticActionControl nav = headerNavControls.get(m.id);
                    if (nav != null && nav.activateFromPointer(mouseX, mouseY, button)) {
                        this.setFocused(nav);
                    }
                    return true;
                }
                y += 22;
                if (m.settingsDetailOnly) {
                    y += SETTINGS_HINT_H;
                } else {
                    for (FeatureSetting s : m.settings) {
                        int rh = s.height();
                        // Same visibility as the render walk: a row that
                        // shares no pixel with the viewport is not
                        // pointer-addressable (its EditBoxes and controls
                        // cannot gain focus from off-screen clicks either).
                        if (vp.intersects(mx + 4, y, 246, rh)
                                && s.mouseClicked(mouseX, mouseY, button, (int) (mx + 4), (int) y, 246)) {
                            activeDragSetting = s;
                            // Manual pointer ownership stays with the
                            // component. The host only mirrors focus onto
                            // the exact peer/trigger under the accepted click.
                            semanticHost.focusAt(mouseX, mouseY);
                            return true;
                        }
                        y += rh + 4;
                    }
                }
                y += SETTINGS_ENTRY_GAP;
            }
        }

        double maxScroll = computeMaxScroll();
        if (maxScroll > 0 && button == 0 && mouseX >= mx + 255 && mouseX <= mx + 262 && mouseY >= viewTop() && mouseY <= viewBot()) {
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
        if (focused != null && focusedSettingVisible && focused.onScroll(vertical)) return true;
        scrolls[selectedCategory].wheel(vertical, 30, computeMaxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent _kev) {
        // C-8 Escape rule for the Modules search field (the pack browser's
        // canonical text-field contract): Escape while typing UNFOCUSES the
        // field and is consumed — vanilla EditBox ignores Escape, so the
        // fall-through used to close the whole screen on the first press.
        // The second press (no active field) reaches the screen.
        if (searchField != null && searchField.isFocused()
                && _kev.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            searchField.setFocused(false);
            return true;
        }
        FeatureSetting focused = FeatureSetting.getFocused();
        if (focused != null && focusedSettingVisible && focused.onKeyPress(_kev)) return true;
        // C-3 roving interceptor — BEFORE super (Screen.keyPressed reaches the
        // container walk via invokespecial, so this is the only seam): while a
        // grouped control holds focus (sidebar tabs, the hosted Accent peers),
        // the group's arrows move focus silently and are consumed. Selection
        // never follows — Enter/Space/click stay the only selection paths.
        if (SemanticControlGroup.rove(this.getFocused(), _kev, this::setFocused)) return true;
        return super.keyPressed(_kev);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent _ev) {
        FeatureSetting focused = FeatureSetting.getFocused();
        if (focused != null && focusedSettingVisible && focused.onCharTyped(_ev)) return true;
        return super.charTyped(_ev);
    }

    /** Immediate off-tab/removal boundary; render will rebuild availability on return. */
    private void invalidateSettingsSemantics() {
        semanticHost.deactivateAll();
        focusedSettingVisible = false;
        for (FeatureMetadata metadata : FeatureRegistry.settings()) {
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
