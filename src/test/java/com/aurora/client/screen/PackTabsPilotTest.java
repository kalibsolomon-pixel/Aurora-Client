package com.aurora.client.screen;

import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.interaction.SemanticFeedback;
import com.aurora.client.ui.interaction.SemanticSound;
import com.aurora.client.util.HoverAnim;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationThunk;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B pack-navigation pilot: the resource-pack browser's category tabs
 * model OVERLAPPING state channels — selected (persistent
 * {@code activeCategory}, painted as the accent wash visible at hover 0),
 * hovered (pointer-only symmetric 140 ms), focused (the Button-family
 * hairline on the tab's {@link SemanticActionControl}), disabled
 * (N/A — every category is always available; only per-frame availability
 * under the clip band / modal exists). Cards converge on the same canonical
 * animator; the DONE install phase gains the canonical disabled look.
 *
 * <p>{@code ResourcePackBrowserScreen} cannot be constructed headless
 * (Screen init touches {@code Minecraft.getInstance()}), so the interaction
 * MODEL is tested on real {@link SemanticAction}s built exactly as the
 * screen builds them, and the screen's wiring is pinned at source level
 * (the established RolloutStatesTest pattern).
 */
class PackTabsPilotTest {

    private static final String SCREEN_SRC =
            "src/client/java/com/aurora/client/screen/ResourcePackBrowserScreen.java";

    private static String screenSource() throws Exception {
        return java.nio.file.Files.readString(java.nio.file.Path.of(SCREEN_SRC));
    }

    // ---- The tab action model, built exactly as the screen's tabControl ----

    /** A minimal stand-in for the screen's activeCategory/submitSearch pair. */
    private static final class CategoryFixture {
        final AtomicReference<String> active = new AtomicReference<>(null); // null = "All"
        final AtomicInteger submissions = new AtomicInteger();

        boolean isActive(String slug) {
            return (slug == null && active.get() == null)
                    || (slug != null && slug.equals(active.get()));
        }

        SemanticAction tabAction(String displayName, String slug, AtomicInteger clicks) {
            return new SemanticAction(
                    Component.literal(displayName),
                    () -> Component.literal("Filters the pack list to this category."),
                    () -> Component.literal(isActive(slug) ? "Selected" : "Not selected"),
                    () -> true,
                    () -> {
                        if (isActive(slug)) return; // silent no-op
                        clicks.incrementAndGet();
                        active.set(slug);
                        submissions.incrementAndGet();
                    },
                    SemanticSound.NONE, true, true);
        }
    }

    /** Feedback stub that counts ACTIVATION plays (the sound-ownership oracle). */
    private static final class CountingFeedback implements SemanticFeedback {
        final AtomicInteger activations = new AtomicInteger();

        @Override
        public void play(SemanticSound sound) {
            if (sound == SemanticSound.ACTIVATION) activations.incrementAndGet();
        }
    }

    @Test
    void acceptedSelectionPlaysExactlyOneClickAndReselectionIsSilentNoOp() {
        CategoryFixture fx = new CategoryFixture();
        CountingFeedback feedback = new CountingFeedback();
        AtomicInteger clicks = new AtomicInteger();

        SemanticAction all = fx.tabAction("All", null, clicks);
        SemanticAction simplistic = fx.tabAction("Simplistic", "simplistic", clicks);
        SemanticAction themed = fx.tabAction("Themed", "themed", clicks);

        SemanticActionControl allCtl = new SemanticActionControl(all,
                feedback, null, SemanticActionControl.PointerRouting.MANUAL);
        SemanticActionControl simpCtl = new SemanticActionControl(simplistic,
                feedback, null, SemanticActionControl.PointerRouting.MANUAL);
        SemanticActionControl themedCtl = new SemanticActionControl(themed,
                feedback, null, SemanticActionControl.PointerRouting.MANUAL);
        for (SemanticActionControl c : List.of(allCtl, simpCtl, themedCtl)) {
            c.setAvailable(true);
            c.setBounds(12, 70, 106, 22);
        }

        // Keyboard activation of an inactive tab: exactly one category change.
        simpCtl.setFocused(true);
        assertTrue(simpCtl.keyPressed(new net.minecraft.client.input.KeyEvent(
                org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0)));
        assertEquals(1, clicks.get(), "one category transition");
        assertTrue(fx.isActive("simplistic"));

        // Activating the already-selected tab (Space): consumed, no-op, no
        // submission, no repeated selection sound.
        assertTrue(simpCtl.keyPressed(new net.minecraft.client.input.KeyEvent(
                org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE, 0, 0)));
        assertEquals(1, clicks.get(), "reselection must not re-submit");
        assertEquals(1, fx.submissions.get());

        // Pointer path on a different inactive tab: one transition; pointer
        // hit on the selected tab: consumed no-op.
        assertTrue(themedCtl.activateFromPointer(60, 80, 0));
        assertEquals(2, clicks.get());
        assertTrue(themedCtl.activateFromPointer(60, 80, 0));
        assertEquals(2, clicks.get(), "selected-tab click is a no-op");
        assertEquals(2, fx.submissions.get());

        // The action's own sound channel stays NONE and the model never
        // routes through it — the click lives in the behavior. The screen
        // plays it through MinecraftSemanticFeedback; the counting here is
        // the same single-play discipline (one per accepted selection).
        assertEquals(0, feedback.activations.get(),
                "SemanticSound.NONE action must not double-own the click");
    }

    @Test
    void narrationCarriesCategoryNameAndSelectedNotSelectedState() {
        CategoryFixture fx = new CategoryFixture();
        SemanticAction selected = fx.tabAction("Simplistic", "simplistic", new AtomicInteger());
        SemanticAction unselected = fx.tabAction("Themed", "themed", new AtomicInteger());
        // "simplistic" is active; "Themed" is not.
        fx.active.set("simplistic");

        SemanticActionControl selCtl = new SemanticActionControl(selected,
                new CountingFeedback(), null, SemanticActionControl.PointerRouting.MANUAL);
        SemanticActionControl unselCtl = new SemanticActionControl(unselected,
                new CountingFeedback(), null, SemanticActionControl.PointerRouting.MANUAL);
        selCtl.setAvailable(true);
        unselCtl.setAvailable(true);

        CapturingNarration sel = new CapturingNarration();
        selCtl.updateNarration(sel);
        assertTrue(sel.joined(NarratedElementType.TITLE).contains("Simplistic"));
        assertTrue(sel.joined(NarratedElementType.HINT).contains("Selected"));
        assertFalse(sel.joined(NarratedElementType.HINT).contains("Not selected"));

        CapturingNarration unsel = new CapturingNarration();
        unselCtl.updateNarration(unsel);
        assertTrue(unsel.joined(NarratedElementType.TITLE).contains("Themed"));
        assertTrue(unsel.joined(NarratedElementType.HINT).contains("Not selected"));
    }

    @Test
    void selectedTabRemainsFocusableAndTraversable() throws Exception {
        // The enabled gate must stay () -> true: a disabled gate would drop
        // the SELECTED tab out of Tab traversal (you could never key onto
        // the current category) and narrate it as unavailable.
        CategoryFixture fx = new CategoryFixture();
        fx.active.set("simplistic");
        SemanticAction selected = fx.tabAction("Simplistic", "simplistic", new AtomicInteger());
        SemanticActionControl ctl = new SemanticActionControl(selected,
                new CountingFeedback(), null, SemanticActionControl.PointerRouting.MANUAL);
        ctl.setAvailable(true);
        ctl.setBounds(12, 70, 106, 22);
        assertNotNull(ctl.nextFocusPath(
                new net.minecraft.client.gui.navigation.FocusNavigationEvent.TabNavigation(true)),
                "the selected tab must remain in focus traversal");
        assertTrue(selected.enabled());
        // Source-level: the screen constructs the tab action with the
        // unconditional gate, not a selected-exclusion gate.
        assertTrue(screenSource().contains("                () -> true,\n" +
                "                () -> {\n" +
                "                    if (isActiveCategory(tab)) return;"),
                "tab enabled gate must be unconditional; the no-op lives in the behavior");
    }

    @Test
    void tabHoverAnimatorIsSymmetric140WithoutSelectionPinning() throws Exception {
        HoverAnim a = HoverAnim.symmetric(140L);
        float first = a.update(true);
        assertTrue(first < 0.5f, "hover must animate from rest, got " + first);
        Thread.sleep(70);
        float mid = a.update(true);
        assertTrue(mid > 0f && mid < 1f, "expected mid-flight, got " + mid);
        float reversed = a.update(false);
        assertTrue(reversed > 0f && reversed <= mid + 0.05f,
                "reversal must continue from current progress (the old local animator snapped to the endpoint): "
                        + reversed + " after " + mid);
        Thread.sleep(200);
        assertEquals(0f, a.update(false), 0f, "exit settles at exactly 0 — selection is never hover-pinned");
    }

    @Test
    void screenDrivesTabHoverFromThePointerOnly() throws Exception {
        String src = screenSource();
        assertTrue(src.contains("activeTabT = hoverAnim(\"tab:\" + i).update(hover);"),
                "the active tab's animator must advance from the pointer only");
        assertFalse(src.contains("hover || isActive"),
                "the hover||selected conflation is banned — selection reads through the persistent wash");
    }

    @Test
    void oneCanonicalAnimatorFamilyForTabsAndCards() throws Exception {
        String src = screenSource();
        assertTrue(src.contains("private final Map<String, HoverAnim> hovers = new HashMap<>();"),
                "tabs and cards share ONE animator map");
        assertTrue(src.contains("HoverAnim.symmetric(HOVER_MS)"),
                "the shared lazy creator constructs the canonical symmetric animator");
        assertTrue(src.contains("private static final long HOVER_MS      = 140L;"));
        assertFalse(src.contains("HoverEase"), "the local 150 ms animator family is retired");
        assertFalse(src.contains("easeOutCubic(t)"), "no local eased-hover copy may remain");
        assertTrue(src.contains("float t = hoverAnim(\"card:\" + p.projectId).update(cardHover);"),
                "card-body hover rides the same canonical animator");
        assertTrue(src.contains("hovers.keySet().removeIf(k -> k.startsWith(\"card:\")"),
                "card animator keys are pruned with their CardState");
    }

    @Test
    void alreadySelectedCategoryChangesNothing() throws Exception {
        String src = screenSource();
        int i = src.indexOf("private void selectCategory(CategoryTab tab) {");
        assertTrue(i >= 0, "selectCategory not found");
        String body = src.substring(i, src.indexOf('}', i));
        int guard = body.indexOf("if (isActiveCategory(tab)) return;");
        int submit = body.indexOf("submitSearch(");
        assertTrue(guard >= 0 && submit > guard,
                "the already-selected guard must precede submitSearch (no re-fetch, no scroll reset)");
    }

    @Test
    void donePhasePaintsTheCanonicalDisabledTreatment() throws Exception {
        String src = screenSource();
        int i = src.indexOf("case CardState.DONE ->");
        assertTrue(i >= 0, "DONE arm not found");
        String arm = src.substring(i, src.indexOf(';', i));
        assertTrue(arm.contains(".disabled(true)"),
                "DONE is terminal/unavailable — its painter must carry Button's disabled treatment");
        assertFalse(arm.contains("glassBackground"),
                "a disabled button never paints glass; the glass flag would be dead weight");
    }

    @Test
    void tabFocusIsTheButtonFamilyHairlineIndependentOfHoverAndSelection() throws Exception {
        String src = screenSource();
        int i = src.indexOf("if (control.isFocused())");
        assertTrue(i >= 0, "focus hairline gate not found");
        String draw = src.substring(i, src.indexOf(';', i));
        assertTrue(draw.contains("ThemeToken.ACCENT") && draw.contains("0x99"),
                "focus = the geometry-following 1 px accent hairline family");
        assertTrue(src.contains("control.setAvailable(inBand && !modalInteractive());"),
                "tab availability follows the clip band and the modal cover");
    }

    @Test
    void tabClicksRouteThroughTheSemanticControl() throws Exception {
        String src = screenSource();
        assertTrue(src.contains("control.activateFromPointer(mouseX, mouseY, 0)"),
                "pointer selection routes through the tab's semantic action");
        assertTrue(src.contains("tabControls[i]"),
                "the click walk resolves the per-tab control");
    }

    @Test
    void arrowsRoveThroughTheSharedGroupPrimitiveNotScreenLocalMath() throws Exception {
        // C-3 adopted (the Phase-B Decision-B deferral is lifted): the
        // sidebar's category tabs are one VERTICAL roving ring — the list is
        // a top-to-bottom stack, so the tab-list arrows are Up/Down. Arrows
        // move focus silently (manual activation: selection still needs
        // Enter/Space/click), and every geometry/wrap/eligibility rule lives
        // in the shared SemanticControlGroup primitive.
        String src = screenSource();
        assertTrue(src.contains(
                        "private final SemanticControlGroup categoryTabGroup = SemanticControlGroup.vertical();"),
                "the tabs form one vertical ring");
        assertTrue(src.contains("categoryTabGroup.attach(control);"),
                "each tab joins the ring at its creation site (category order)");
        assertTrue(src.contains(
                        "if (SemanticControlGroup.rove(this.getFocused(), _kev, this::setFocused)) return true;"),
                "the one interceptor seam, before super.keyPressed");
        // And still no screen-local arrow math of any kind.
        assertFalse(src.contains("GLFW_KEY_LEFT"));
        assertFalse(src.contains("GLFW_KEY_RIGHT"));
        assertFalse(src.contains("GLFW_KEY_UP"));
        assertFalse(src.contains("GLFW_KEY_DOWN"));
    }

    private static final class CapturingNarration implements NarrationElementOutput {
        private final Map<NarratedElementType, List<String>> values =
                new EnumMap<>(NarratedElementType.class);

        @Override
        public void add(NarratedElementType type, NarrationThunk<?> thunk) {
            thunk.getText(text -> values.computeIfAbsent(type, ignored -> new ArrayList<>()).add(text));
        }

        @Override
        public NarrationElementOutput nest() {
            return this;
        }

        String joined(NarratedElementType type) {
            return String.join(" ", values.getOrDefault(type, List.of()));
        }
    }
}
