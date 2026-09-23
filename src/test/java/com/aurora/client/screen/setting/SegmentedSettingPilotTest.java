package com.aurora.client.screen.setting;

import com.aurora.client.theme.GlassStyle;
import com.aurora.client.theme.ThemeMode;
import com.aurora.client.theme.ThemeRoundness;
import com.aurora.client.ui.component.SegmentedControl;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.interaction.SemanticControlGroup;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C-5 SegmentedSetting/SegmentedControl conformance: the segmented
 * track is a PEER-SELECTION family — the Accent contract. Selection derives
 * live from the represented enum (never a stored index), hover is
 * pointer-only symmetric 140 ms per segment and NEVER gated on selection
 * (the baseline suppressed it — the Phase-C conflation), focus is the
 * Button-family hairline, one {@link SemanticActionControl} per segment
 * materializes on the first host ask attached to a component-owned
 * horizontal {@link SemanticControlGroup} (the C-3 ring travels with the
 * controls), pointer and keyboard converge on one semantic value-change
 * path with exactly one activation click on a genuine change, and the
 * already-selected peer is a silent consumed no-op. The baseline's
 * double-save (constructor wrapper + persistThemeChange) is removed — the
 * setter is the one persistence path.
 *
 * <p>{@code SegmentedSetting}'s constructor is headless, so the value
 * semantics below run against the REAL component with a counting setter.
 */
class SegmentedSettingPilotTest {

    private static final String SRC =
            "src/client/java/com/aurora/client/ui/component/SegmentedControl.java";
    private static final String SETTING_SRC =
            "src/client/java/com/aurora/client/screen/setting/SegmentedSetting.java";

    private static String src(String path) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (java.io.IOException e) {
            return fail(path + " not readable: " + e);
        }
    }

    private final AtomicReference<ThemeMode> mode = new AtomicReference<>(ThemeMode.DARK);
    private final AtomicInteger setterCalls = new AtomicInteger();
    private SegmentedSetting<ThemeMode> row;

    @BeforeEach
    void construct() {
        mode.set(ThemeMode.DARK);
        setterCalls.set(0);
        row = new SegmentedSetting<>("Mode", ThemeMode.class,
                mode::get, v -> {
                    setterCalls.incrementAndGet();
                    mode.set(v);
                });
        layoutRow(row, 100, 16, 240);
    }

    @AfterEach
    void teardown() {
        row.disabled(() -> false);
    }

    /** Stamps the click math's layout fields (render itself needs GL). */
    private static void layoutRow(SegmentedSetting<?> row, int x, int y, int w) {
        try {
            f(row, "lastTrackX").setInt(row, x);
            f(row, "lastTrackY").setInt(row, y);
            f(row, "lastTrackW").setInt(row, w);
            ((SegmentedControl) f(row, "control").get(row)).layout(x, y, w, 20);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field f(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /** Segment center in GUI coords, from the same math the painter uses. */
    private static double[] segCenter(int i, int n) {
        float gap = 2f;
        float sw = (240 - gap * (n - 1)) / n;
        double sx = 100 + i * (sw + gap);
        return new double[]{sx + sw / 2, 26};
    }

    /**
     * Mirrors the host invariant the routing relies on: a control marked
     * available has current bounds (the render walk stamps them before the
     * availability sweep re-marks).
     */
    private static void materializeWithBounds(SegmentedSetting<?> row, int n) {
        float gap = 2f;
        float sw = (240 - gap * (n - 1)) / n;
        List<SemanticActionControl> peers = row.interactionControls();
        for (int i = 0; i < n; i++) {
            peers.get(i).setBounds((int) (100 + i * (sw + gap)), 16, (int) sw, 20);
            peers.get(i).setAvailable(true);
        }
    }

    // ---- Topology ----

    @Test
    void productionInventoryIsThreeThemeRowsWithSevenPeers() throws Exception {
        String registry = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/client/java/com/aurora/client/screen/FeatureRegistry.java"));
        assertEquals(3, registry.split("new SegmentedSetting\\<>\\(", -1).length - 1,
                "exactly three constructions");
        assertTrue(registry.contains("ThemeMode.class"));
        assertTrue(registry.contains("ThemeRoundness.class"));
        assertTrue(registry.contains("GlassStyle.class"));

        AtomicReference<ThemeRoundness> r = new AtomicReference<>(ThemeRoundness.ROUND);
        SegmentedSetting<ThemeRoundness> corner = new SegmentedSetting<>("Corner Style",
                ThemeRoundness.class, r::get, r::set);
        AtomicReference<GlassStyle> gs = new AtomicReference<>(GlassStyle.FROSTED);
        SegmentedSetting<GlassStyle> glass = new SegmentedSetting<>("Glass Style",
                GlassStyle.class, gs::get, gs::set);
        assertEquals(2, row.interactionControls().size(), "Mode: Dark/Light");
        assertEquals(3, corner.interactionControls().size(), "Corner Style: three peers");
        assertEquals(2, glass.interactionControls().size(), "Glass Style: two peers");
        assertEquals(7, row.interactionControls().size() + corner.interactionControls().size()
                        + glass.interactionControls().size(),
                "seven production segmented peers in total");
    }

    @Test
    void peersFormOneHorizontalRovingRingInVisualOrder() {
        List<SemanticActionControl> peers = row.interactionControls();
        for (SemanticActionControl peer : peers) peer.setAvailable(true);
        SemanticControlGroup group = peers.get(0).interactionGroup();
        assertNotNull(group, "materialized peers carry their ring");
        assertSame(group, peers.get(1).interactionGroup(), "one shared ring");
        assertEquals(List.of("Dark", "Light"),
                peers.stream().map(p -> p.action().accessibleName().getString()).toList(),
                "attach order is visual order");

        KeyEvent right = new KeyEvent(GLFW.GLFW_KEY_RIGHT, 0, 0);
        KeyEvent up = new KeyEvent(GLFW.GLFW_KEY_UP, 0, 0);
        assertSame(peers.get(1), group.rovingTarget(right, peers.get(0)));
        assertSame(peers.get(0), group.rovingTarget(new KeyEvent(GLFW.GLFW_KEY_LEFT, 0, 0), peers.get(1)),
                "wrap");
        assertNull(group.rovingTarget(up, peers.get(0)),
                "cross-axis keys are not the horizontal ring's");
        assertEquals(0, setterCalls.get(), "roving targets are pure — no setter runs");
    }

    // ---- Selection source of truth ----

    @Test
    void selectionDerivesLiveFromTheGetter() {
        List<SemanticActionControl> peers = row.interactionControls();
        assertEquals("Selected", peers.get(0).action().state().getString());
        assertEquals("Not selected", peers.get(1).action().state().getString());
        mode.set(ThemeMode.LIGHT);
        assertEquals("Not selected", peers.get(0).action().state().getString());
        assertEquals("Selected", peers.get(1).action().state().getString());
        assertEquals(0, setterCalls.get(), "reading state never commits");
    }

    // ---- Pointer convergence ----

    @Test
    void pointerSelectionCommitsExactlyOncePerPath() {
        // Legacy path (host never materialized controls): silent single commit.
        assertTrue(row.mouseClicked(segCenter(1, 2)[0], segCenter(1, 2)[1], 0, 0, 0, 240));
        assertEquals(1, setterCalls.get());
        assertEquals(ThemeMode.LIGHT, mode.get());
        assertTrue(row.mouseClicked(segCenter(1, 2)[0], segCenter(1, 2)[1], 0, 0, 0, 240),
                "reselecting the selected peer stays consumed");
        assertEquals(1, setterCalls.get(), "no second commit on the selected no-op");

        // Semantic path (materialized + available + current bounds — the
        // host invariant): the same value change, now through the action's
        // exactly-one activation click.
        materializeWithBounds(row, 2);
        List<SemanticActionControl> peers = row.interactionControls();
        assertTrue(row.mouseClicked(segCenter(0, 2)[0], segCenter(0, 2)[1], 0, 0, 0, 240));
        assertEquals(2, setterCalls.get());
        assertEquals(ThemeMode.DARK, mode.get());
        assertTrue(row.mouseClicked(segCenter(0, 2)[0], segCenter(0, 2)[1], 0, 0, 0, 240));
        assertEquals(2, setterCalls.get(), "selected reactivation is inert through the action too");
    }

    @Test
    void disabledIsTheAuthoritativeGate() throws Exception {
        materializeWithBounds(row, 2);
        List<SemanticActionControl> peers = row.interactionControls();
        row.disabled(() -> true);
        // The action's enabled gate reads the control's disabled field,
        // which renderOverlay syncs from the row every frame — mirror that
        // sync (the row-level guard below is the same-frame backstop).
        ((SegmentedControl) f(row, "control").get(row)).disabled(true);
        assertFalse(row.mouseClicked(segCenter(1, 2)[0], segCenter(1, 2)[1], 0, 0, 0, 240),
                "the row rejects pointer activation while disabled");
        assertFalse(peers.get(1).activateFromPointer(segCenter(1, 2)[0], segCenter(1, 2)[1], 0),
                "the semantic action's enabled gate rejects too");
        peers.get(1).setFocused(true);
        assertTrue(peers.get(1).keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)),
                "a targeted disabled selection is consumed (cannot fall through)");
        assertEquals(0, setterCalls.get());
        assertEquals("Not selected", peers.get(1).action().state().getString(),
                "state stays readable while disabled");
    }

    // ---- Keyboard activation ----

    @Test
    void keyboardSelectionRunsTheSemanticActionExactlyOnce() {
        materializeWithBounds(row, 2);
        List<SemanticActionControl> peers = row.interactionControls();
        peers.get(1).setFocused(true);
        assertTrue(peers.get(1).keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0)));
        assertEquals(1, setterCalls.get());
        assertEquals(ThemeMode.LIGHT, mode.get());
        assertTrue(peers.get(1).keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)),
                "selected reactivation stays consumed");
        assertEquals(1, setterCalls.get());
        assertFalse(peers.get(0).keyPressed(new KeyEvent(GLFW.GLFW_KEY_RIGHT, 0, 0)),
                "arrows are the group's, not the control's — the C-3 seam owns them");
        assertEquals(1, setterCalls.get(), "an arrow press never commits");
    }

    // ---- Source-contract pins ----

    @Test
    void hoverIsCanonicalPointerOnlyAndNeverGatedOnSelection() {
        String source = src(SRC);
        assertTrue(source.contains("private static final long HOVER_MS = 140L;"));
        assertTrue(source.contains("this.segmentHovers[i] = HoverAnim.symmetric(HOVER_MS);"),
                "one long-lived symmetric(140) animator per segment, created at construction");
        assertTrue(source.contains("float hoverT = segmentHovers[i].update(\n                    !disabled && inBounds(mouseX, mouseY, sx, y, sw, trackH));"),
                "the hover target is pointer + disabled only");
        assertFalse(source.contains("!isSelected && inBounds"),
                "hover must never be gated on selection (the Phase-C conflation)");
        assertFalse(source.contains("isFocused() && inBounds"),
                "focus must never be aliased into the hover target");
    }

    @Test
    void focusIsTheButtonFamilyHairlineAndSelectedKeepsItsFill() {
        String source = src(SRC);
        assertEquals(2, source.split(java.util.regex.Pattern.quote(
                "ThemeManager.semanticContrast().focus"), -1).length - 1,
                "one contextual focus site with neutral and selected outputs");
        assertTrue(source.contains("if (control != null && control.isFocused())"));
    }

    @Test
    void soundOwnershipIsTheConditionalBehaviorPlay() {
        String source = src(SRC);
        assertTrue(source.contains("if (index == clampedSelected()) return; // silent consumed no-op"));
        assertTrue(source.contains("MinecraftSemanticFeedback.INSTANCE.play(SemanticSound.ACTIVATION);"));
        assertEquals(1, source.split("MinecraftSemanticFeedback\\.INSTANCE\\.play", -1).length - 1,
                "exactly one play site — inside the behavior, only on genuine change");
        assertTrue(source.contains("SemanticSound.NONE, true, true)"),
                "the action's own sound channel stays NONE");
    }

    @Test
    void theSetterIsTheOnePersistencePath() {
        String setting = src(SETTING_SRC);
        assertFalse(setting.contains("AuroraConfig.save()"),
                "the constructor wrapper must not add a second save on top of the production setters' persistThemeChange");
        assertTrue(setting.contains("The setter is the ONE persistence path"),
                "the contract stays documented at the construction site");
    }

    @Test
    void noLocalArrowAlgorithmExists() {
        for (String path : new String[]{SRC, SETTING_SRC}) {
            String source = src(path);
            for (String banned : new String[]{"GLFW_KEY_LEFT", "GLFW_KEY_RIGHT", "GLFW_KEY_UP", "GLFW_KEY_DOWN"}) {
                assertFalse(source.contains(banned),
                        banned + " must not appear in " + path + " — arrows are the C-3 primitive's alone");
            }
        }
        // The host-facing seam is the same one every C-3 consumer uses.
        String setting = src(SETTING_SRC);
        assertTrue(setting.contains("control.interactionControls(label)"),
                "the row forwards the component's controls (group travels with them)");
    }

    @Test
    void hostsStayGenericAboutSegments() throws Exception {
        String aurora = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/client/java/com/aurora/client/screen/AuroraScreen.java"));
        String detail = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/client/java/com/aurora/client/screen/FeatureDetailScreen.java"));
        for (String host : new String[]{aurora, detail}) {
            assertTrue(host.contains("List.copyOf(setting.interactionControls())")
                            || host.contains("setting.interactionControls()"),
                    "both hosts materialize the component-owned set generically");
            assertFalse(host.contains("instanceof SegmentedSetting"));
        }
    }
}
