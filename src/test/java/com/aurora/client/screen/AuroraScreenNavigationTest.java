package com.aurora.client.screen;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C-2b source-contract pins — AuroraScreen's manual navigation and
 * chrome adoption (AuroraScreen is not headless-instantiable, so the
 * contract is pinned against source, the established C-1/C-2 pattern).
 * Covered families: sidebar navigation tabs, the Profiles action, module
 * cards, Settings-header navigation, and the header toggles. The layout
 * pair's C-5 deferral is pinned as deliberately NOT adopted. C-3 adds the
 * roving-ring pins (sidebar tabs + the interceptor seam).
 */
class AuroraScreenNavigationTest {

    private static final Path SCREEN = Path.of(
            "src/client/java/com/aurora/client/screen/AuroraScreen.java");
    private static final Path REGISTRY = Path.of(
            "src/client/java/com/aurora/client/screen/FeatureRegistry.java");
    private static final Path MODULES = Path.of(
            "src/client/java/com/aurora/client/module/ModuleManager.java");

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException e) {
            return fail(path + " not readable: " + e);
        }
    }

    /** The method body between {@code name}'s opening brace and the next method at equal or lower depth is overkill — simple substring slices. */
    private static String bodyOf(String src, String signature) {
        int start = src.indexOf(signature);
        if (start < 0) fail("method not found: " + signature);
        return src.substring(start, src.indexOf("\n    }", start) + 1);
    }

    private static int count(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    // ------------------------------------------------------------------
    //  Inventory
    // ------------------------------------------------------------------

    @Test
    void exactManualControlInventory() {
        String src = read(SCREEN);
        String registry = read(REGISTRY);
        String modules = read(MODULES);
        // Sidebar: exactly two navigation tabs + one Profiles action.
        assertEquals(1, count(src, "private final SemanticActionControl[] tabControls"));
        assertTrue(src.contains("tabControls[0] = tabControl(0);"));
        assertTrue(src.contains("tabControls[1] = tabControl(1);"));
        assertTrue(src.contains("profilesControl = profilesActionControl();"));
        // Module cards: one control per ModuleManager module (37 tiles).
        assertTrue(src.contains("for (Module m : ModuleManager.getInstance().getModules()) {\n            tileControls.put(m.id, tileControl(m));\n        }"));
        assertEquals(37, count(modules, "new Module("), "the grid's tile inventory (ModuleManager)");
        // Settings headers: navigation only for entries with a detail screen;
        // header toggles for all four entries.
        assertTrue(src.contains("if (metadata.hasDetail()) {\n                headerNavControls.put(metadata.id, headerNavControl(metadata));\n            }"));
        assertTrue(src.contains("headerToggleControls.put(metadata.id, headerToggleControl(metadata));"));
        assertEquals(4, count(registry, "SETTINGS, \""),
                "four Settings-tab entries (custom_title, text_fonts, theme, interface)");
        // custom_title is registered without settings (grouping chrome, no
        // navigation zone) — the 5-arg add() leaves its settings empty.
        assertTrue(registry.contains("add(SETTINGS, \"custom_title\""));
    }

    @Test
    void layoutPairIsDeliberatelyDeferredToC5() {
        String src = read(SCREEN);
        // The pair keeps its manual immediate path (documented remaining
        // gap: canonical hover + peer semantic controls + C-3 arrows all
        // land with C-5's SegmentedControl conformance).
        assertTrue(src.contains("gridLayout = false; return true;"));
        assertTrue(src.contains("gridLayout = true; return true;"));
        assertFalse(src.contains("layoutControl"),
                "C-2b must not manufacture a one-off peer primitive for the layout pair");
        assertTrue(src.contains("peer selection is C-5's SegmentedControl"),
                "the deferral stays documented at the init site");
    }

    // ------------------------------------------------------------------
    //  C-3: arrow-key roving
    // ------------------------------------------------------------------

    @Test
    void sidebarTabsFormOneHorizontalRovingRingWithoutProfiles() {
        String src = read(SCREEN);
        assertTrue(src.contains(
                        "private final SemanticControlGroup sidebarTabGroup = SemanticControlGroup.horizontal();"),
                "the two nav tabs are one horizontal ring");
        assertEquals(1, count(src, "sidebarTabGroup.attach(control);"),
                "exactly the shared tabControl factory attaches its members");
        String profiles = bodyOf(src, "private SemanticActionControl profilesActionControl()");
        assertFalse(profiles.contains("sidebarTabGroup"),
                "Profiles is a plain action, not a navigation peer — it never joins the ring");
        // No screen-local arrow math: geometry, wrap, and eligibility rules
        // live in the shared primitive.
        for (String banned : new String[]{"GLFW_KEY_LEFT", "GLFW_KEY_RIGHT", "GLFW_KEY_UP", "GLFW_KEY_DOWN"}) {
            assertFalse(src.contains(banned), banned + " must not appear in the screen source");
        }
    }

    @Test
    void rovingInterceptsBeforeSuperInTheKeyPath() {
        String src = read(SCREEN);
        String body = bodyOf(src, "public boolean keyPressed(");
        int rove = body.indexOf("SemanticControlGroup.rove");
        int supp = body.indexOf("super.keyPressed");
        assertTrue(rove >= 0 && supp > rove,
                "the interceptor must run BEFORE super — Screen.keyPressed reaches the container walk via invokespecial");
        assertTrue(body.indexOf("focused.onKeyPress(_kev)") >= 0 && body.indexOf("focused.onKeyPress(_kev)") < rove,
                "an inline setting's own key handling (EditBoxes, captures) keeps priority over roving");
    }

    // ------------------------------------------------------------------
    //  Host integration + lifecycle
    // ------------------------------------------------------------------

    @Test
    void registrationIsInitOnlyInVisualOrder() {
        String src = read(SCREEN);
        int init = src.indexOf("protected void init()");
        int render = src.indexOf("public void render(", init);
        String initBody = src.substring(init, render);
        // Visual order: search field first (top of the content column),
        // then the sidebar, then module cards, then the Settings walk.
        int search = initBody.indexOf("this.addWidget(searchField)");
        int tabs = initBody.indexOf("tabControls[0] = tabControl(0);");
        int profiles = initBody.indexOf("profilesControl = profilesActionControl();");
        int tiles = initBody.indexOf("tileControls.put(m.id, tileControl(m));");
        int settings = initBody.indexOf("headerToggleControls.put(metadata.id, headerToggleControl(metadata));");
        assertTrue(search >= 0 && tabs > search && profiles > tabs && tiles > profiles && settings > tiles,
                "registration follows the visual traversal order");
        // Factories are identity-stable: a resize's rebuild re-registers the
        // SAME objects (each factory returns its stored instance first).
        for (String guard : new String[]{
                "SemanticActionControl control = tabControls[i];\n        if (control != null) return control;",
                "if (profilesControl != null) return profilesControl;",
                "SemanticActionControl control = tileControls.get(m.id);\n        if (control != null) return control;",
                "SemanticActionControl control = headerNavControls.get(m.id);\n        if (control != null) return control;",
                "SemanticActionControl control = headerToggleControls.get(m.id);\n        if (control != null) return control;"}) {
            assertTrue(src.contains(guard), "identity-stable factory guard missing: " + guard);
        }
    }

    @Test
    void noPerFrameConstructionOrRegistration() {
        String src = read(SCREEN);
        int render = src.indexOf("public void render(");
        String renderAndAfter = src.substring(render);
        assertFalse(renderAndAfter.contains("semanticHost.register("),
                "controls must not be registered in the frame path");
        assertFalse(renderAndAfter.contains("new SemanticAction("),
                "actions must not be constructed in the frame path");
        assertFalse(renderAndAfter.contains("new SemanticActionControl("),
                "controls must not be constructed in the frame path");
        // Animators are lazily created once per stable identity, never per
        // frame (computeIfAbsent over module/entry ids — finite by
        // inventory; the helpers sit with the fields, the advances sit in
        // the render walks).
        assertTrue(src.contains("tileHovers.computeIfAbsent(moduleId"));
        assertTrue(src.contains("headerHovers.computeIfAbsent(entryId"));
    }

    @Test
    void inactiveTabControlsAreUnreachable() {
        String src = read(SCREEN);
        // Tile availability is marked only inside the Modules content walk
        // (which runs only on the Modules tab); header/toggle availability
        // only inside the Settings walk. The frame-start sweep leaves every
        // other control unavailable, and finishAvailabilitySweep clears the
        // stale focus.
        String modules = bodyOf(src, "private void renderModulesLive(");
        String settings = bodyOf(src, "private void renderSettingsLive(");
        assertTrue(modules.contains("control.setAvailable(true);"));
        assertTrue(settings.contains("nav.setAvailable(true);"));
        assertTrue(settings.contains("tc.setAvailable(true);"));
        assertFalse(settings.contains("tileControls.get("),
                "the Settings walk must not reach module-card controls");
        assertFalse(modules.contains("headerNavControls.get("),
                "the Modules walk must not reach Settings-header controls");
        assertTrue(src.contains("semanticHost.beginAvailabilitySweep();"));
        assertTrue(src.contains("semanticHost.finishAvailabilitySweep();"));
    }

    @Test
    void tabSwitchInvalidatesAndNoOpsSilently() {
        String src = read(SCREEN);
        String select = bodyOf(src, "private void selectCategory(int i) {");
        assertTrue(select.contains("if (selectedCategory == i) return;"),
                "reactivating the selected tab is a no-op (the action's silent guard runs first)");
        assertTrue(select.contains("semanticHost.deactivateAll();"),
                "a real change invalidates every hosted control immediately");
        assertTrue(select.contains("setting.onInteractionAvailabilityChanged(false);"),
                "leaving the Settings tab notifies its rows");
        // One convergence point: pointer, Enter and Space all land in the
        // tab action, which is the only caller of selectCategory.
        assertEquals(1, count(src, "selectCategory(i);"),
                "selectCategory has exactly one caller — the tab action");
    }

    // ------------------------------------------------------------------
    //  Selected/value vs hover independence
    // ------------------------------------------------------------------

    @Test
    void selectedAndEnabledStateNeverAliasHover() {
        String src = read(SCREEN);
        // The banned conflation pattern (Phase B) must not appear anywhere.
        assertFalse(src.contains("hover || sel"));
        assertFalse(src.contains("sel || hover"));
        assertFalse(src.contains("hover || selected"));
        assertFalse(src.contains("hover || on"));
        assertFalse(src.contains("on || hover"));
        // Selection/enabled read through their own channels…
        assertTrue(src.contains("boolean sel = selectedCategory == i;"));
        assertTrue(src.contains("boolean on = m.isEnabled();"));
        // …and the animators are driven from pointer-only targets.
        assertTrue(src.contains("tabHovers[i].update(hover);"));
        assertTrue(src.contains("profilesHover.update(pHover);"));
        assertTrue(src.contains("tileHover(m.id).update(hover)"));
        assertTrue(src.contains("headerHover(m.id).update(navHover)"));
    }

    @Test
    void canonicalHoverVocabularyAcrossAdoptedFamilies() {
        String src = read(SCREEN);
        assertTrue(src.contains("private static final long HOVER_MS = 140L;"));
        // One animator family for every adopted surface: sidebar tabs,
        // Profiles, module cards, settings headers — all symmetric(140).
        assertTrue(src.contains("HoverAnim[] tabHovers = {\n            HoverAnim.symmetric(HOVER_MS),\n            HoverAnim.symmetric(HOVER_MS),\n    };"));
        assertTrue(src.contains("HoverAnim profilesHover = HoverAnim.symmetric(HOVER_MS);"));
        assertTrue(src.contains("k -> HoverAnim.symmetric(HOVER_MS)"));
        // ToggleSwitch keeps its own component-level canonical hover (the
        // painted 28x15 switch) — the header code must not drive it.
        assertFalse(src.contains("t.hoverAnim"));
    }

    // ------------------------------------------------------------------
    //  Activation, sound, and pointer ownership
    // ------------------------------------------------------------------

    @Test
    void moduleCardPrimaryActionIsTheSemanticToggle() {
        String src = read(SCREEN);
        // The action owns the toggle (exactly-once + one click + save via
        // Module.setEnabled's own save)…
        assertTrue(src.contains("m::toggle"));
        // …and the click walk routes through the control, never toggles raw.
        String click = bodyOf(src, "public boolean mouseClicked(");
        assertTrue(click.contains("SemanticActionControl control = tileControls.get(m.id);"));
        assertTrue(click.contains("control.activateFromPointer(mouseX, mouseY, button)"));
        assertFalse(click.contains("m.toggle();"),
                "the raw pointer toggle must not survive beside the semantic route");
        // Keyboard primary action only: Enter/Space map to the action (the
        // control's keyPressed); no keyboard chord for the secondary
        // navigation was invented.
        assertFalse(src.toLowerCase().contains("key chord"));
        assertFalse(src.contains("openDetailFromKeyboard"));
    }

    @Test
    void rightClickDetailKeepsSoundParityWithHeaderNavigation() {
        String src = read(SCREEN);
        // The pointer-specific secondary action plays exactly one click at
        // the site — the same destination the Settings header opens with
        // its action's click, so policy does not depend on the route.
        String click = bodyOf(src, "public boolean mouseClicked(");
        assertTrue(click.contains("MinecraftSemanticFeedback.INSTANCE"));
        assertTrue(click.contains(".play(SemanticSound.ACTIVATION);"));
        assertEquals(1, count(click, "play(SemanticSound.ACTIVATION)"),
                "the click walk itself plays no other sounds — ownership lives in the actions");
    }

    @Test
    void headerToggleAdapterOwnsTheSingleActivation() {
        String src = read(SCREEN);
        String factory = bodyOf(src, "private SemanticActionControl headerToggleControl(");
        // Exactly one activation source: the action runs the switch's own
        // toggle (thumb pulse + setter) plus one save — BooleanSetting's
        // wiring. The switch itself is never clicked, so no double-toggle.
        assertTrue(factory.contains("t.toggle();"));
        assertTrue(factory.contains("AuroraConfig.save();"));
        String click = bodyOf(src, "public boolean mouseClicked(");
        assertTrue(click.contains("SemanticActionControl tc = headerToggleControls.get(m.id);"));
        assertFalse(click.contains("m.setEnabled(!m.isEnabled())"),
                "the raw header-toggle path must not survive beside the adapter");
        // Focus paint: the switch renders the hairline via focusedVisual
        // (BooleanSetting's sync), never a second screen-side ring.
        assertTrue(src.contains("t.focusedVisual(tc.isFocused());"));
    }

    @Test
    void headerNavigationRoutesThroughTheControl() {
        String src = read(SCREEN);
        String click = bodyOf(src, "public boolean mouseClicked(");
        assertTrue(click.contains("SemanticActionControl nav = headerNavControls.get(m.id);"));
        assertTrue(click.contains("nav.activateFromPointer(mouseX, mouseY, button)"));
        // The navigation zone keeps priority below the toggle zone and
        // stays inside the C-1 viewport gate (pinned by the coupling test).
        assertTrue(click.contains("mouseX >= mx + 4 && mouseX <= mx + 254 - 42"));
    }

    @Test
    void soundOwnershipModel() {
        String src = read(SCREEN);
        // Tabs: NONE on the action, one ACTIVATION inside the behavior and
        // ONLY on a real destination change (the pack-nav contract).
        String tabFactory = bodyOf(src, "private SemanticActionControl tabControl(int i) {");
        assertTrue(tabFactory.contains("if (selectedCategory == i) return;"));
        assertTrue(tabFactory.contains("MinecraftSemanticFeedback.INSTANCE\n                            .play(SemanticSound.ACTIVATION);"));
        assertTrue(tabFactory.contains("SemanticSound.NONE, true, true)"));
        // Profiles/cards/headers/toggles: SemanticAction.button — exactly
        // one ACTIVATION per accepted activation, played by the adapter.
        assertEquals(4, count(src, "SemanticAction.button("),
                "profiles, tiles, header nav, header toggles construct via button()");
        // The host never plays sounds; hover/focus/rejection are silent.
        String hostSrc = read(Path.of(
                "src/client/java/com/aurora/client/ui/interaction/SemanticControlHost.java"));
        assertFalse(hostSrc.contains("play("));
    }

    // ------------------------------------------------------------------
    //  Narration, focus, availability
    // ------------------------------------------------------------------

    @Test
    void narrationMetadataForEveryAdoptedFamily() {
        String src = read(SCREEN);
        // Sidebar: name + Selected/Not selected.
        assertTrue(src.contains("Component.literal(selectedCategory == i ? \"Selected\" : \"Not selected\")"));
        // Module card: name + description + Enabled/Disabled state.
        assertTrue(src.contains("Component.literal(m.isEnabled() ? \"Enabled\" : \"Disabled\")"));
        assertTrue(src.contains("() -> Component.literal(m.description)"));
        // Header toggle: label + On/Off.
        assertTrue(src.contains("Component.literal(m.isEnabled() ? \"On\" : \"Off\")"));
        // Profiles + header nav: action labels with descriptions.
        assertTrue(src.contains("Component.literal(\"Opens the profile manager.\")"));
        assertTrue(src.contains("\"Opens the \" + m.displayName + \" settings.\""));
    }

    @Test
    void focusIsTheButtonFamilyHairlineAndNeverHover() {
        String src = read(SCREEN);
        // Chips, Profiles, cards, and header nav draw the shared 1px accent
        // hairline (0x99) from the control's vanilla focus only.
        assertEquals(4, count(src, "ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ACCENT), 0x99)"),
                "chips/profiles/tiles/header-nav hairline sites");
        for (String site : new String[]{
                "if (control != null && control.isFocused())",
                "if (profilesControl != null && profilesControl.isFocused())",
                "if (control != null && control.isFocused()) {\n                RenderUtil.drawRoundedOutlineAA(g, cx, cy, cw, ch, tileRadius",
                "if (nav.isFocused())"}) {
            assertTrue(src.contains(site), "focus site missing: " + site);
        }
    }

    @Test
    void clipBandAvailabilityForCardsAndHeaders() {
        String src = read(SCREEN);
        // Cards: the availability re-mark sits inside the render walk whose
        // cull is the viewport intersection (cardBounds returns null
        // otherwise) — hidden tiles are not focusable, actionable, narrated,
        // or hoverable.
        String modules = bodyOf(src, "private void renderModulesLive(");
        int cull = modules.indexOf("if (b == null) continue;");
        int mark = modules.indexOf("control.setAvailable(true);");
        assertTrue(cull >= 0 && mark > cull, "card availability follows the viewport cull");
        // Headers + toggles: marked inside the vp.intersects header branch.
        String settings = bodyOf(src, "private void renderSettingsLive(");
        int headerGate = settings.indexOf("if (vp.intersects(mx + 4, y, CONTENT_W - 4, headerH))");
        int navMark = settings.indexOf("nav.setAvailable(true);");
        int toggleMark = settings.indexOf("tc.setAvailable(true);");
        assertTrue(headerGate >= 0 && navMark > headerGate && toggleMark > headerGate,
                "header availability sits inside the viewport-intersection branch");
        // Sidebar chrome is static (never scrolled) — always available.
        String live = bodyOf(src, "private void renderLive(");
        assertTrue(live.contains("control.setAvailable(true); // static sidebar chrome — always interactive"));
    }

    @Test
    void pressTreatmentPerFamily() {
        String src = read(SCREEN);
        // Navigation/selection uses the transition as feedback; the card's
        // state change is the feedback; only the toggle keeps a mechanical
        // press (the component's own thumb pulse, fired by t.toggle()).
        assertEquals(4, count(src, "// no press animation"),
                "tabs, profiles, cards, header nav: no press animation by decision");
        assertFalse(src.contains("0.96"), "no Button-family press scale was copied onto chrome");
    }

    @Test
    void pointerOwnershipAndCaptureCancelFirstOrdering() {
        String src = read(SCREEN);
        String click = bodyOf(src, "public boolean mouseClicked(");
        // Capture cancellation owns the click, then stale focus drops, then
        // vanilla children, then the chrome routes — each accepted pointer
        // activation assigns focus exactly once and never replays.
        int cancel = click.indexOf("if (FeatureSetting.cancelActiveCapture()) return true;");
        int drop = click.indexOf("semanticHost.dropFocus();");
        int sup = click.indexOf("if (super.mouseClicked(_ev, _dbl)) return true;");
        int chrome = click.indexOf("activateFromPointer(mouseX, mouseY, button)");
        assertTrue(cancel >= 0 && drop > cancel && sup > drop && chrome > sup,
                "capture-cancel-first ordering preserved");
        assertEquals(5, count(click, "this.setFocused("),
                "tabs x2 are a loop, profiles, tile, toggle, nav — five focus sites (loop counted once)");
    }
}
