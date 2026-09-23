package com.aurora.client.screen.setting;

import com.aurora.client.ui.component.IconAction;
import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C-4B rollout: the remaining compact-action consumers join the C-4A
 * primitive. Covers the stateful-disclosure contract (glyph flips with
 * Expanded/Collapsed while the control — and with it keyboard focus —
 * survives the toggle), the KeyList scanning-exception ruling plus its
 * narrow semantic adapter (chips keep the immediate-hover painter and gain
 * keyboard/narration/sound through the primitive's custom-painter mode),
 * the one-domain-path rule for every migrated family (no legacy pointer
 * mutation beside the action), the dynamic-list lifecycle (fresh list
 * swap, stale controls pruned, no per-frame construction), and the
 * font-subset rule (the rollout needs NO new glyphs — the disclosure pair
 * predates it; remove/check stay out until a consumer exists).
 */
class IconActionRolloutTest {

    private static final String SRC = "src/client/java/com/aurora/client/ui/component/IconAction.java";
    private static final String ITEM_SCALE =
            "src/client/java/com/aurora/client/screen/setting/ItemScaleSetting.java";
    private static final String EFFECT_EXPIRY =
            "src/client/java/com/aurora/client/screen/setting/EffectExpiryListSetting.java";
    private static final String EFFECT_ROW =
            "src/client/java/com/aurora/client/screen/setting/EffectRowSetting.java";
    private static final String PARTICLE_ROW =
            "src/client/java/com/aurora/client/screen/setting/ParticleRowSetting.java";
    private static final String SEARCH_LIST =
            "src/client/java/com/aurora/client/screen/setting/SearchListSetting.java";
    private static final String KEY_LIST =
            "src/client/java/com/aurora/client/screen/setting/KeyListSetting.java";
    private static final String FEATURE_ICONS =
            "src/client/java/com/aurora/client/screen/FeatureIcons.java";

    private static String src(String path) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (java.io.IOException e) {
            return fail(path + " not readable: " + e);
        }
    }

    // ---- Behavioral: the stateful disclosure contract ----

    @Test
    void disclosureGlyphTracksStateWhileTheControlSurvives() {
        AtomicInteger toggles = new AtomicInteger();
        var expanded = new java.util.concurrent.atomic.AtomicBoolean(false);
        IconAction disclosure = new IconAction(
                () -> expanded.get() ? "\uE5C6" : "\uE5CF",
                SemanticAction.button(
                        Component.literal("Toggle Stick"),
                        () -> Component.literal("Shows or hides the scale sliders."),
                        () -> Component.literal(expanded.get() ? "Expanded" : "Collapsed"),
                        () -> true,
                        () -> {
                            expanded.set(!expanded.get());
                            toggles.incrementAndGet();
                        }),
                () -> 0xFF888888, () -> 0xFFFFFFFF);

        // Collapsed → collapsed glyph; accessibility state agrees with it.
        assertEquals("\uE5CF", disclosure.currentGlyph());
        SemanticActionControl control = disclosure.interactionControl();
        assertEquals("Collapsed", control.action().state().getString());
        assertEquals("Toggle Stick", control.action().accessibleName().getString(),
                "the accessible name carries the row label, never the glyph");

        // Toggling flips glyph AND narration state — and the control is the
        // SAME instance (focus can survive the disclosure).
        assertTrue(control.action().activate(null, null));
        assertEquals(1, toggles.get());
        assertTrue(expanded.get());
        assertSame(control, disclosure.interactionControl(), "one control across the toggle");
        assertEquals("\uE5C6", disclosure.currentGlyph(), "expanded → expanded glyph");
        assertEquals("Expanded", control.action().state().getString());

        assertTrue(control.action().activate(null, null));
        assertEquals("\uE5CF", disclosure.currentGlyph(), "collapsed → collapsed glyph");
        assertEquals("Collapsed", control.action().state().getString(),
                "accessibility state agrees with the visible glyph");
        assertEquals(2, toggles.get(), "exactly one toggle per activation");
    }

    // ---- Behavioral: the KeyList chip adapter (exemption + accessibility) ----

    @Test
    void keyListChipControlsAreHostedInVisualOrderWithTheAddLast() {
        KeyListFixture f = new KeyListFixture(List.of(GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K));
        List<SemanticActionControl> controls = f.row.interactionControls();
        assertEquals(3, controls.size(), "two chips + the add action");
        assertEquals("Remove key " + KeybindSetting.keyName(GLFW.GLFW_KEY_J),
                controls.get(0).action().accessibleName().getString());
        assertEquals("Remove key " + KeybindSetting.keyName(GLFW.GLFW_KEY_K),
                controls.get(1).action().accessibleName().getString());
        assertEquals("Extra Keys: Add Key", controls.get(2).action().accessibleName().getString());
        // Stable across asks without mutation — no per-frame construction.
        List<SemanticActionControl> again = f.row.interactionControls();
        assertSame(controls, again);
        for (int i = 0; i < controls.size(); i++) {
            assertSame(controls.get(i), again.get(i));
        }
    }

    @Test
    void keyListChipKeyboardRemovalRunsTheOnePathExactlyOnce() {
        KeyListFixture f = new KeyListFixture(List.of(GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K));
        SemanticActionControl chipK = f.row.interactionControls().get(1);
        chipK.setAvailable(true);
        chipK.setFocused(true);
        assertTrue(chipK.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)));
        assertEquals(List.of(GLFW.GLFW_KEY_J), f.list, "exactly that entry removed (by value)");
        assertEquals(1, f.saves.get(), "one save per removal");
        // The stale control is gone from the rebuilt list; the surviving
        // chip's control is untouched.
        List<SemanticActionControl> after = f.row.interactionControls();
        assertEquals(2, after.size());
        assertFalse(after.contains(chipK), "stale control pruned with its entry");
        // Enter on the dead control can never replay the removal.
        chipK.setAvailable(true);
        assertTrue(chipK.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)));
        assertEquals(1, f.saves.get(), "the removed action is no longer wired to the list");
        assertEquals(List.of(GLFW.GLFW_KEY_J), f.list);
    }

    @Test
    void keyListChipLifecycleAddRemoveReAddHasNoDuplicates() {
        KeyListFixture f = new KeyListFixture(List.of());
        // capture-add through the row's own capture path (begin → key)
        beginCapture(f);
        assertTrue(f.row.onKeyPress(GLFW.GLFW_KEY_J, 0));
        beginCapture(f);
        assertTrue(f.row.onKeyPress(GLFW.GLFW_KEY_K, 0));
        List<SemanticActionControl> two = f.row.interactionControls();
        assertEquals(3, two.size());
        // remove both through their actions' behavior
        two.get(0).action().activate(null, null);
        two.get(1).action().activate(null, null);
        assertTrue(f.list.isEmpty());
        List<SemanticActionControl> empty = f.row.interactionControls();
        assertEquals(1, empty.size(), "the add action alone remains");
        // re-add: exactly one new control, no duplicates
        beginCapture(f);
        assertTrue(f.row.onKeyPress(GLFW.GLFW_KEY_K, 0));
        List<SemanticActionControl> re = f.row.interactionControls();
        assertEquals(2, re.size());
        assertSame(re.get(re.size() - 1), empty.get(0), "the add control is stable");
        assertEquals("Remove key " + KeybindSetting.keyName(GLFW.GLFW_KEY_K),
                re.get(0).action().accessibleName().getString());
    }

    /** Arms capture through the add control's behavior (headless — no render bounds). */
    private static void beginCapture(KeyListFixture f) {
        f.row.interactionControl().action().activate(null, null);
        assertTrue(f.row.listeningState());
    }

    @Test
    void keyListChipGateFollowsTheRowDisabledState() {
        KeyListFixture f = new KeyListFixture(List.of(GLFW.GLFW_KEY_J));
        f.disabled.set(true);
        SemanticActionControl chip = f.row.interactionControls().get(0);
        assertFalse(chip.action().enabled(), "the row's disabled gate is authoritative");
        chip.setAvailable(true);
        chip.setFocused(true);
        assertTrue(chip.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)),
                "a targeted disabled selection is consumed (cannot fall through)");
        assertEquals(List.of(GLFW.GLFW_KEY_J), f.list);
        assertEquals(0, f.saves.get());
    }

    /** KeyListPilotTest's fixture shape — setter installs a fresh value into the same list. */
    private static final class KeyListFixture {
        final KeyListSetting row;
        final List<Integer> list;
        final AtomicInteger saves = new AtomicInteger();
        final java.util.concurrent.atomic.AtomicBoolean disabled =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        KeyListFixture(List<Integer> initial) {
            List<Integer> l = new ArrayList<>(initial);
            this.list = l;
            this.row = new KeyListSetting("Extra Keys", () -> l, v -> {
                l.clear();
                l.addAll(v);
            }, saves::incrementAndGet);
            row.disabled(disabled::get);
        }
    }

    // ---- Source pins: the stateful primitive ----

    @Test
    void primitiveGainedOnlyTheStatefulGlyphForm() {
        String s = src(SRC);
        assertTrue(s.contains("java.util.function.Supplier<String> glyph"),
                "the disclosure incompatibility is answered by a glyph supplier");
        assertTrue(s.contains("public IconAction(String glyph, SemanticAction action"),
                "the fixed-glyph constructor remains the canonical form");
        assertTrue(s.contains("public String currentGlyph()"),
                "state is readable for deterministic disclosure tests");
        // The canonical vocabulary is untouched (C-4A pins keep asserting it).
        assertTrue(s.contains("HoverAnim.symmetric(HOVER_MS)"));
        assertTrue(s.contains("ThemeManager.semanticContrast().focusNeutral()"));
    }

    // ---- Source pins: ItemScale add + disclosures ----

    @Test
    void itemScaleAddAndDisclosuresUseThePrimitive() {
        String s = src(ITEM_SCALE);
        // Add: the EffectExpiry pilot B shape — one animator drives pill + glyph.
        assertTrue(s.contains("addAction.syncChannels(plusX, plusY, 24, 20, mouseX, mouseY)"));
        assertTrue(s.contains("addAction.paintGlyph(ctx, tr, plusX, plusY, 24, 20)"));
        assertTrue(s.contains("addAction.clicked(mouseX, mouseY, button)"),
                "pointer routes through the semantic action");
        assertTrue(s.contains("private boolean canAddFoundItem()"),
                "the enabled gate IS the can-add state");
        assertTrue(s.contains("private void addFoundItem()"), "the ONE add path");
        assertFalse(s.contains("drawCentered(ctx, tr, \"+\""), "the text + is gone");
        // Disclosure: stateful glyph supplier, one toggle path, no static glyph.
        assertTrue(s.contains("() -> expandedStates.getOrDefault(id, false) ? CHEV_UP : CHEV_DOWN"),
                "collapsed → collapsed glyph, expanded → expanded glyph");
        assertTrue(s.contains("expandedStates.getOrDefault(id, false) ? \"Expanded\" : \"Collapsed\""),
                "narration state agrees with the glyph");
        assertEquals(1, count(s, "expandedStates.put(idStr, !"),
                "the ONE toggle path — no legacy pointer mutation beside the action");
        assertTrue(s.contains("disclose.clicked(mouseX, mouseY, button)"));
        assertFalse(s.contains("String chev = expanded ? CHEV_UP : CHEV_DOWN"),
                "the static chevron draw is gone");
        assertFalse(s.contains("MaterialIconRenderer.drawIcon"), "no direct icon draws remain");
        // Host-visible controls: the add control + two per row (disclosure
        // and remove) — the add MUST host or it has no Tab/keyboard route.
        assertTrue(s.contains("!= AuroraConfig.get().itemScales.size() * 2 + 1"));
        assertTrue(s.contains("fresh.add(addAction.interactionControl());"),
                "the add action hosts first (it sits above the row list)");
        assertTrue(s.contains("fresh.add(rowDiscloseAction(id).interactionControl());"),
                "reading order per row: disclosure, then remove");
        // Both per-row action maps are pruned by the removal.
        assertTrue(s.contains("rowDiscloseActions.remove(idStr);"));
        assertTrue(s.contains("rowRemoveActions.remove(idStr);"));
    }

    // ---- Source pins: EffectRow remove ----

    @Test
    void effectRowRemoveUsesThePrimitiveUnderContainerOwnership() {
        String row = src(EFFECT_ROW);
        assertFalse(row.contains("drawString(tr, \"x\""), "the text x is gone");
        assertTrue(row.contains("the container's icon action (C-4)"),
                "the row documents where its remove control went");

        String s = src(EFFECT_EXPIRY);
        assertTrue(s.contains("rowRemoveActions.computeIfAbsent"),
                "one long-lived action per effect id (no per-frame construction)");
        assertTrue(s.contains("Component.literal(\"Remove effect \" + name)"),
                "domain-specific narration, never the glyph");
        assertTrue(s.contains("remove.clicked(mouseX, mouseY, button)"),
                "pointer routes through the semantic action");
        assertTrue(s.contains("private void removeEffect(String effectId)"),
                "the ONE removal path — action and legacy fallback converge");
        assertTrue(s.contains("rowRemoveActions.remove(effectId);"),
                "the stale action is pruned with its row");
        assertTrue(s.contains("!= AuroraConfig.get().effectExpiryIncludedEffects.size() + 1"),
                "add + one control per row");
        assertTrue(s.contains("rebuildRowControlList();\n        AuroraConfig.save();"),
                "exactly one save, on the single removal path");
    }

    // ---- Source pins: Particle disclosures ----

    @Test
    void particleDisclosuresUseTheMaterialGlyphPath() {
        String s = src(PARTICLE_ROW);
        assertFalse(s.contains("\"v\" : \">\""), "the ASCII disclosure is gone");
        assertTrue(s.contains("() -> expanded ? CHEV_UP : CHEV_DOWN"),
                "collapsed → collapsed glyph, expanded → expanded glyph");
        assertTrue(s.contains("expanded ? \"Expanded\" : \"Collapsed\""),
                "narration state agrees with the glyph");
        assertTrue(s.contains("disclosure.syncChannels(x, y, width, HEADER_H, mouseX, mouseY)"),
                "the header is the action's pointer target (the zone that always toggled)");
        assertTrue(s.contains("disclosure.clicked(mouseX, mouseY, button)"));
        assertEquals(1, count(s, "expanded = !expanded;"),
                "the ONE toggle path — no legacy pointer mutation beside the action");
        assertTrue(s.contains("public SemanticActionControl interactionControl()"),
                "the row exposes its control to the host");
    }

    @Test
    void searchListAggregatesRowControlsWithNoPerFrameRebuild() {
        String s = src(SEARCH_LIST);
        assertTrue(s.contains("rowControlsDirty"), "rebuild only on a real refilter");
        assertTrue(s.contains("fresh.addAll(row.interactionControls());"),
                "the filtered rows' controls, in visual order");
        assertTrue(s.contains("rowControls = fresh; // fresh instance — the host diff sees the swap"));
    }

    // ---- Source pins: the KeyList ruling ----

    @Test
    void keyListKeepsTheScanningPainterAndGainsTheSemanticAdapter() {
        String s = src(KEY_LIST);
        // The exemption: the scanning painter is verbatim — immediate hover,
        // the − glyph, no IconAction painter adoption.
        assertTrue(s.contains("AuroraFontRenderer.drawCentered(ctx, tr, \"\\u2212\""),
                "the chip keeps its − glyph (no font expansion, §12)");
        assertTrue(s.contains("ThemeManager.withAlpha(err, btnHover ? 0x66 : 0x33)"),
                "the immediate hover tint is the documented scanning exception");
        // The narrow adapter: semantic channels through the primitive's
        // custom-painter mode (the HudEditor X-badge precedent).
        assertTrue(s.contains("chip.syncChannels(btnX, btnY, BTN_SIZE, BTN_SIZE, mouseX, mouseY)"));
        assertTrue(s.contains("chip.paintHairline(ctx, btnX, btnY, BTN_SIZE, BTN_SIZE)"),
                "keyboard focus is visible on the chip");
        assertTrue(s.contains("chip.clicked(mouseX, mouseY, button)"),
                "pointer routes through the semantic action");
        assertTrue(s.contains("Component.literal(\"Remove key \" + KeybindSetting.keyName(v))"),
                "domain-specific narration, independent of hover");
        assertTrue(s.contains("private void removeEntry(int value)"),
                "the ONE removal path for pointer and keyboard");
        assertTrue(s.contains("copy.remove(Integer.valueOf(value));"),
                "removal is BY VALUE (identity, not a shifting row index)");
        assertTrue(s.contains("cachedChipVersion != chipsVersion"),
                "controls rebuild only on mutation — never per frame");
        assertTrue(s.contains("chipRemoveActions.keySet().retainAll"),
                "externally-shrunk lists cannot keep dead chip actions");
    }

    // ---- Source pins: the font subset stays untouched (§12) ----

    @Test
    void rolloutNeedsNoNewFontGlyphs() {
        String icons = src(FEATURE_ICONS);
        // The disclosure pair predates the rollout (EnumSetting's subset).
        assertTrue(icons.contains("\"_dropdown_expand_more\""));
        assertTrue(icons.contains("\"_dropdown_expand_less\""));
        // The C-4A action glyphs remain the only additions.
        assertTrue(icons.contains("\"_action_add\""));
        assertTrue(icons.contains("\"_action_close\""));
        // No remove/check-class glyphs were added for this rollout.
        assertFalse(icons.contains("\"_action_remove\""), "no remove glyph — no consumer required it");
        assertFalse(icons.contains("\"_action_check\""), "no check glyph — no consumer required it");
    }

    private static int count(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
