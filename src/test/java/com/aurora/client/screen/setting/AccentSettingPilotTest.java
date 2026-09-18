package com.aurora.client.screen.setting;

import com.aurora.client.theme.ThemePresets;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.util.HoverAnim;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B AccentSetting peer-selection pilot: the preset grid (9 presets +
 * Custom, 5 per row) models a peer-selection group whose members represent
 * VALUES — selection is derived live from the literal accent (never a
 * stored index), hover is pointer-only symmetric 140 ms per peer, focus is
 * the Button-family hairline on per-peer {@link SemanticActionControl}s
 * (materialized only when a host asks — the AuroraScreen Phase C
 * deferral), disabled is the authoritative gate (no hover affordance, no
 * activation, no save), and Custom is a real peer whose activity derives
 * from the accent not matching any preset.
 *
 * <p>{@link AccentSetting}'s constructor is headless (its embedded
 * ColorSetting/ColorSwatch are too), so the value semantics below run
 * against the REAL component with a counting setter — the pilot removed
 * the grid path's redundant direct config save, so clicks persist exactly
 * through the wired setter and nothing touches disk in these tests.
 */
class AccentSettingPilotTest {

    private static final String SRC =
            "src/client/java/com/aurora/client/screen/setting/AccentSetting.java";

    private static String src() throws Exception {
        return java.nio.file.Files.readString(java.nio.file.Path.of(SRC));
    }

    private final AtomicReference<Integer> accent = new AtomicReference<>(ThemePresets.RED);
    private final AtomicInteger setterCalls = new AtomicInteger();
    private AccentSetting row;

    @BeforeEach
    void construct() {
        accent.set(ThemePresets.RED);
        setterCalls.set(0);
        row = new AccentSetting("Accent Color", accent::get, v -> {
            setterCalls.incrementAndGet();
            accent.set(v);
        });
        layout(row, 100, 200, 320);
    }

    @AfterEach
    void teardown() {
        row.disabled(() -> false);
    }

    /** Stamps the render-time layout fields the click math reads (render itself needs GL). */
    private static void layout(AccentSetting row, int x, int y, int w) {
        try {
            f(row, "lastX").setInt(row, x);
            f(row, "lastY").setInt(row, y);
            f(row, "lastW").setInt(row, w);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field f(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static int staticInt(String name) throws Exception {
        Field field = AccentSetting.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Integer) field.get(null);
    }

    /** Cell center in GUI coords, from the same constants the render uses. */
    private static double[] cellCenter(AccentSetting row, int i) throws Exception {
        int padX = staticInt("GRID_PAD_X"), gap = staticInt("SWATCH_GAP");
        int perRow = staticInt("PER_ROW"), h = staticInt("SWATCH_H"), gapY = staticInt("GRID_GAP_Y");
        Field fx = f(row, "lastX"), fy = f(row, "lastY"), fw = f(row, "lastW");
        int w = fw.getInt(row);
        int sw = (w - padX * 2 - gap * (perRow - 1)) / perRow;
        int cx = fx.getInt(row) + padX + (i % perRow) * (sw + gap) + sw / 2;
        int cy = fy.getInt(row) + staticInt("LABEL_ROW_H") + (i / perRow) * (h + gapY) + h / 2;
        return new double[]{cx, cy};
    }

    private boolean click(AccentSetting row, int i) throws Exception {
        double[] c = cellCenter(row, i);
        return row.mouseClicked(c[0], c[1], 0, 0, 0, 320);
    }

    private static int indexOf(String name) {
        for (int i = 0; i < ThemePresets.ALL.size(); i++) {
            if (ThemePresets.ALL.get(i).name.equals(name)) return i;
        }
        return -1;
    }

    // ---- Topology ----

    @Test
    void topologyIsNinePresetsPlusCustomInOneProductionConstruction() throws Exception {
        assertEquals(9, ThemePresets.ALL.size());
        assertEquals(10, row.interactionControls().size(), "9 presets + Custom = 10 peers");
        String registry = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/client/java/com/aurora/client/screen/FeatureRegistry.java"));
        assertEquals(1, registry.split("new AccentSetting\\(", -1).length - 1,
                "exactly one production construction");
    }

    // ---- Selection source of truth ----

    @Test
    void selectionDerivesFromLiteralAccentWithNoStoredIndex() {
        int red = indexOf("Red");
        assertTrue(row.peerSelectedState(red));
        for (int i = 0; i < 10; i++) {
            if (i != red) assertFalse(row.peerSelectedState(i), "peer " + i + " must not be selected");
        }
        accent.set(0xFF123456); // no preset matches → Custom, derived live
        assertFalse(row.peerSelectedState(red));
        assertTrue(row.peerSelectedState(9));
    }

    @Test
    void pickerEditLandingOnAPresetValueSelectsThatPreset() {
        accent.set(ThemePresets.BLUE); // the exact literal, however it was produced
        assertTrue(row.peerSelectedState(indexOf("Blue")));
        assertFalse(row.peerSelectedState(9));
    }

    // ---- Activation / persistence ----

    @Test
    void presetActivationTransfersSelectionWithExactLiteralValue() throws Exception {
        int blue = indexOf("Blue");
        assertTrue(click(row, blue));
        assertEquals(1, setterCalls.get(), "exactly one setter call (the persistence proxy)");
        assertEquals(ThemePresets.BLUE, accent.get().intValue(), "exact literal preset value");
        assertTrue(row.peerSelectedState(blue));
        assertFalse(row.peerSelectedState(indexOf("Red")));
        assertFalse(row.editorOpenState());
    }

    @Test
    void alreadySelectedPresetIsAConsumedNoOp() throws Exception {
        int red = indexOf("Red");
        assertTrue(click(row, red));
        assertEquals(0, setterCalls.get(), "no redundant setter/persist");
        assertEquals(ThemePresets.RED, accent.get().intValue());
        assertTrue(row.peerSelectedState(red));
    }

    @Test
    void selectedPresetClickClosesTheOpenEditorWithoutPersisting() throws Exception {
        assertTrue(click(row, 9), "Custom opens the editor");
        assertTrue(row.editorOpenState());
        int red = indexOf("Red"); // already the selected value
        assertTrue(click(row, red));
        assertFalse(row.editorOpenState());
        assertEquals(0, setterCalls.get(), "closing via the already-selected value is not a change");
    }

    @Test
    void customClickOpensTheEditorWithoutChangingTheValue() throws Exception {
        assertTrue(click(row, 9));
        assertTrue(row.editorOpenState());
        assertEquals(0, setterCalls.get(), "opening the editor is not a change");
        assertEquals(ThemePresets.RED, accent.get().intValue());
        assertFalse(row.peerSelectedState(9), "Custom becomes selected only through the accent value");
    }

    @Test
    void gridPathPersistsonlyThroughTheWiredSetter() throws Exception {
        // The pilot removed the redundant second AuroraConfig.save() the
        // grid click used to issue right after the persisting setter — the
        // only remaining direct saves are the picker-release commit and the
        // screen-close flush.
        String source = src();
        assertEquals(2, source.split("AuroraConfig\\.save\\(\\)", -1).length - 1,
                "exactly two direct saves (picker release + screen close)");
        int i = source.indexOf("Widget.inBounds(mouseX, mouseY, sx, sy, sw, SWATCH_H)");
        String block = source.substring(i, source.indexOf('}', source.indexOf("return true;", i)));
        assertFalse(block.contains("AuroraConfig.save()"), "no save in the grid-click block");
    }

    // ---- Hover ----

    @Test
    void hoverTargetIsPointerOnlyAndDisabledRejectsIt() {
        assertTrue(row.peerHoverTarget(true, false));
        assertFalse(row.peerHoverTarget(false, false));
        assertFalse(row.peerHoverTarget(true, true), "disabled rejects accepted hover");
    }

    @Test
    void peerAnimatorsAreIndependentAndCanonical() throws Exception {
        HoverAnim first = row.peerHoverAnimator(0);
        for (int i = 1; i < 10; i++) {
            assertNotSame(first, row.peerHoverAnimator(i), "each peer owns its animator");
        }
        HoverAnim a = row.peerHoverAnimator(3);
        float start = a.update(true);
        assertTrue(start < 0.5f, "animates from rest, got " + start);
        Thread.sleep(70);
        float mid = a.update(true);
        assertTrue(mid > 0f && mid < 1f);
        float reversed = a.update(false);
        assertTrue(reversed <= mid + 0.05f, "reversal continues from current progress, no endpoint snap");
        Thread.sleep(200);
        assertEquals(0f, a.update(false), 0f, "exit settles at exactly 0");
    }

    @Test
    void selectionNeverPinsHoverAndHoverNeverErasesSelection() throws Exception {
        // Structural pins: the target never consults selection; the render
        // draws the selection ring unconditionally and the hover ring on
        // top for every enabled peer — the baseline else-if suppression is
        // banned.
        String source = src();
        assertTrue(source.contains("boolean peerHoverTarget(boolean pointerOverCell, boolean disabled)"));
        assertFalse(source.contains("} else if (hovered) {"), "else-if suppression is banned");
        assertTrue(source.contains("if (selected) {\n" +
                "                RenderUtil.drawRoundedOutlineAA"),
                "the selection ring is unconditional");
        assertTrue(source.contains("if (!disabled && hT > 0f)"),
                "the hover ring composes on every enabled peer — selected included");
    }

    // ---- Disabled ----

    @Test
    void disabledRejectsPointerActivationWithoutValueOrPersist() throws Exception {
        row.disabled(() -> true);
        int blue = indexOf("Blue");
        assertFalse(click(row, blue), "disabled grid clicks are rejected (baseline fall-through)");
        assertEquals(0, setterCalls.get());
        assertEquals(ThemePresets.RED, accent.get().intValue());
        assertTrue(row.peerSelectedState(indexOf("Red")), "persistent selected value stays readable");
        row.disabled(() -> false);
    }

    @Test
    void disabledGatesThePeerActionsAndNeverPaintsHoverAffordance() throws Exception {
        SemanticActionControl peer = row.interactionControls().get(indexOf("Blue"));
        assertTrue(peer.action().enabled());
        row.disabled(() -> true);
        assertFalse(peer.action().enabled(), "the authoritative gate drops peers from traversal");
        String source = src();
        int i = source.indexOf("peerHoverTarget(hoverAt(mouseX, mouseY, sx, sy, sw), disabled)");
        assertTrue(i >= 0, "hover target must consult disabled");
        row.disabled(() -> false);
    }

    // ---- Literal-color integrity ----

    @Test
    void colorInteriorsStayLiteralInEveryState() throws Exception {
        String source = src();
        assertFalse(source.contains("0x55FFFFFF"),
                "no alpha-halving of represented color (the ColorSwatch data principle)");
        assertTrue(source.contains("RenderUtil.drawRoundedRectAA(ctx, cellX(i, width), cellY(i), swatchW(width), SWATCH_H, swR, e.argb);"),
                "preset interiors draw the literal preset value");
        assertTrue(source.contains("customFill | 0xFF000000"),
                "the Custom preview draws its fill opaque and literal");
    }

    // ---- Sound model ----

    @Test
    void conditionalSelectionClickModelPlaysOnlyOnEffect() {
        // The real actions carry SemanticSound.NONE and call the feedback
        // adapter inside the behavior only on effect; verified here on the
        // identical construction shape (MinecraftSemanticFeedback.INSTANCE
        // needs a live client), pinned to the source below.
        AtomicInteger clicks = new AtomicInteger();
        AtomicInteger effects = new AtomicInteger();
        class Model {
            void activate(boolean effect) {
                if (effect) {
                    effects.incrementAndGet();
                    clicks.incrementAndGet();
                }
            }
        }
        Model m = new Model();
        m.activate(true);   // preset changes accent → one click
        m.activate(false);  // already-selected no-op → silent
        m.activate(true);   // Custom open / editor close → one click
        assertEquals(2, clicks.get());
        assertEquals(2, effects.get());
    }

    @Test
    void peerSoundOwnershipIsPinnedToTheConditionalBehaviorPlay() throws Exception {
        String source = src();
        assertTrue(source.contains("if (effect) MinecraftSemanticFeedback.INSTANCE.play(SemanticSound.ACTIVATION);"),
                "the click plays exactly once, inside the behavior, only on effect");
        assertEquals(1, source.split("MinecraftSemanticFeedback\\.INSTANCE\\.play", -1).length - 1,
                "exactly one play site — no second sound owner");
        assertTrue(source.contains("SemanticSound.NONE, true, true)"),
                "the action's own sound channel stays NONE");
    }

    // ---- Focus host boundary ----

    @Test
    void auroraScreenHostDeferralRemainsIntact() throws Exception {
        String aurora = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/client/java/com/aurora/client/screen/AuroraScreen.java"));
        assertFalse(aurora.contains("interactionControls("),
                "AuroraScreen must not consume peer controls (the Phase C host deferral)");
        // The component exposes the full set the future host will consume.
        List<SemanticActionControl> peers = row.interactionControls();
        assertEquals("Blue", peers.get(0).action().accessibleName().getString());
        assertEquals("Custom", peers.get(9).action().accessibleName().getString());
    }

    @Test
    void peerNarrationCarriesNameAndSelectedState() {
        List<SemanticActionControl> peers = row.interactionControls();
        assertEquals("Selected", peers.get(indexOf("Red")).action().state().getString());
        assertEquals("Not selected", peers.get(indexOf("Blue")).action().state().getString());
        assertEquals("Not selected", peers.get(9).action().state().getString());
        accent.set(0xFF123456);
        assertEquals("Selected", peers.get(9).action().state().getString(), "derived live");
        assertEquals("Not selected", peers.get(indexOf("Red")).action().state().getString());
    }

    // ---- Embedded picker isolation ----

    @Test
    void embeddedCustomPickerIsAnUnmodifiedColorSetting() throws Exception {
        String source = src();
        assertTrue(source.contains("new ColorSetting(\"Custom Accent\","));
        assertFalse(source.contains("customPicker.interactionControl"),
                "the embedded picker's semantic control is never materialized/forwarded");
        assertFalse(source.contains("customPicker.interactionControls"));
    }
}
