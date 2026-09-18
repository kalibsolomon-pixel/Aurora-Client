package com.aurora.client.screen.setting;

import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.util.HoverAnim;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B PixelCanvas action migration: the three text actions (Clear,
 * Default, Apply) adopt the canonical state vocabulary — symmetric 140 ms
 * hover (no snap-in), three semantic controls (Tab focus, Enter/Space
 * activation, narration metadata, exactly-one activation click through the
 * feedback adapter), and the disabled gate shared by render and input.
 * The warning panel's Apply Anyway/Cancel stay manual (modal-confirmation
 * family) and the glyph-action family elsewhere stays untouched.
 *
 * <p>The {@link PixelCanvasSetting} constructor touches
 * {@code Minecraft.getInstance().font()} for its EditBoxes, so headless
 * construction is not possible; the animators/controls are inspected
 * reflectively, and the source file is pinned where behavior is
 * construction-time (the established RolloutStatesTest source-pinning
 * pattern).
 */
class PixelCanvasActionMigrationTest {

    @Test
    void allThreeAnimatorsAreDeclaredSymmetric() throws Exception {
        // Source-pinned: the field initializers must construct the §8.3
        // symmetric animator — the legacy snap-in constructor is banned
        // for these three actions.
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/client/java/com/aurora/client/screen/setting/PixelCanvasSetting.java"));
        for (String field : new String[]{"clearHoverAnim", "defaultHoverAnim", "applyHoverAnim"}) {
            int i = src.indexOf("HoverAnim " + field + " = ");
            assertTrue(i >= 0, field + " declaration not found");
            String decl = src.substring(i, src.indexOf(';', i));
            assertEquals("HoverAnim " + field + " = HoverAnim.symmetric(140L)", decl,
                    field + " must use the canonical symmetric constructor");
        }
    }

    @Test
    void legacyHoverAnimConstructorIsGoneFromPixelCanvas() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/client/java/com/aurora/client/screen/setting/PixelCanvasSetting.java"));
        assertFalse(src.contains("new HoverAnim("),
                "no legacy snap-in HoverAnim construction may remain in PixelCanvasSetting");
    }

    @Test
    void symmetricAnimatorDoesNotSnapAndReversesContinuously() throws Exception {
        HoverAnim a = HoverAnim.symmetric(140L);
        float first = a.update(true);
        assertTrue(first < 0.5f, "hover must animate from rest, got " + first);
        Thread.sleep(70);
        float mid = a.update(true);
        assertTrue(mid > 0f && mid < 1f, "expected mid-flight, got " + mid);
        float reversed = a.update(false);
        assertTrue(reversed > 0f && reversed <= mid + 0.05f,
                "reversal must continue from current progress: " + reversed + " after " + mid);
        Thread.sleep(200);
        assertEquals(0f, a.update(false), 0f, "exit settles at exactly 0");
    }

    @Test
    void interactionControlsExposeThreeActionsInVisualOrder() throws Exception {
        // interactionControls() is an instance method over lazily-created
        // fields and the constructor touches Minecraft — pin the SOURCE
        // contract instead (the RolloutStatesTest pattern): exactly three
        // controls, visual order Clear → Apply → Default, distinct
        // accessible names, authoritative disabled gate.
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/client/java/com/aurora/client/screen/setting/PixelCanvasSetting.java"));
        assertTrue(src.contains("return java.util.List.of(clearControl, applyControl, defaultControl)"),
                "traversal order must be visual/read: Clear, Apply, Default");
        assertTrue(src.contains("\"Clear Canvas\""));
        assertTrue(src.contains("\"Restore Default Shape\""));
        assertTrue(src.contains("\"Apply Resolution\""));
        assertTrue(src.contains("() -> !isDisabled()"),
                "every action's enabled gate must consult the authoritative row disabled state");
    }

    @Test
    void compactGlyphFamilyStaysOutsideThisMigration() throws Exception {
        // Scope pin: the deferred compact glyph actions keep their
        // immediate-hover manual treatment (ItemScale's +/x and the Effect
        // rows are the representatives). KeyList is excluded — its ADD
        // pill is legitimately canonical (pilot 7); only its "−" chips are
        // the deferred family, and those are pinned by KeyListPilotTest.
        for (String path : new String[]{
                "src/client/java/com/aurora/client/screen/setting/ItemScaleSetting.java",
                "src/client/java/com/aurora/client/screen/setting/EffectRowSetting.java"}) {
            String src = java.nio.file.Files.readString(java.nio.file.Path.of(path));
            assertFalse(src.contains("HoverAnim.symmetric"),
                    path + " glyph family must remain outside this migration");
        }
    }
}
