package com.aurora.client.ui.component;

import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C-4 icon-action pilot: the primitive's behavioral contract (routing,
 * exactly-once, disabled rejection, focus independence, lazy lifecycle,
 * narration, geometry) plus source-contract pins for the three heterogeneous
 * pilot consumers (ItemScale remove, EffectExpiry add, HudEditor X) and the
 * font-subset prerequisite. IconAction's paint paths need GL — runtime boots
 * carry them; these tests cover the headless-exercisable surface.
 */
class IconActionPilotTest {

    private static final String SRC = "src/client/java/com/aurora/client/ui/component/IconAction.java";
    private static final String ITEM_SCALE =
            "src/client/java/com/aurora/client/screen/setting/ItemScaleSetting.java";
    private static final String EFFECT_EXPIRY =
            "src/client/java/com/aurora/client/screen/setting/EffectExpiryListSetting.java";
    private static final String HUD_EDITOR =
            "src/client/java/com/aurora/client/screen/HudEditorScreen.java";
    private static final String FEATURE_ICONS =
            "src/client/java/com/aurora/client/screen/FeatureIcons.java";

    private static String src(String path) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (java.io.IOException e) {
            return fail(path + " not readable: " + e);
        }
    }

    private static IconAction action(AtomicInteger calls, AtomicBoolean enabled) {
        return new IconAction("\uE5CD",
                SemanticAction.button(Component.literal("Remove item"),
                        () -> Component.literal("Removes the row."), null,
                        enabled::get, calls::incrementAndGet),
                () -> 0xFF888888, () -> 0xFFFF0000);
    }

    // ---- Primitive behavior ----

    @Test
    void controlIsLazyStableAndRoutedThrough() {
        AtomicInteger calls = new AtomicInteger();
        IconAction a = action(calls, new AtomicBoolean(true));
        assertFalse(a.materialized());
        SemanticActionControl first = a.interactionControl();
        assertSame(first, a.interactionControl(), "one long-lived control, never rebuilt");

        // Unhosted (never asked): the pointer route declines — callers keep
        // their legacy fallback for this window.
        assertFalse(a.clicked(5, 5, 0));

        // Hosted + available + in bounds: exactly one domain action.
        first.setAvailable(true);
        first.setBounds(0, 0, 16, 16);
        assertTrue(a.clicked(5, 5, 0));
        assertEquals(1, calls.get());
        // Out of bounds is rejected by the control's own geometry truth.
        assertFalse(a.clicked(50, 50, 0));
        assertEquals(1, calls.get());
        // Non-primary buttons are not activations.
        assertFalse(a.clicked(5, 5, 1));
        assertEquals(1, calls.get());
    }

    @Test
    void keyboardRunsTheSameActionExactlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        IconAction a = action(calls, new AtomicBoolean(true));
        SemanticActionControl control = a.interactionControl();
        control.setAvailable(true);
        control.setFocused(true);
        assertTrue(control.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)));
        assertTrue(control.keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0)));
        assertEquals(2, calls.get(), "Enter and Space each run the SAME action once");
    }

    @Test
    void disabledIsTheAuthoritativeGate() {
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean enabled = new AtomicBoolean(true);
        IconAction a = action(calls, enabled);
        SemanticActionControl control = a.interactionControl();
        control.setAvailable(true);
        control.setBounds(0, 0, 16, 16);
        enabled.set(false);
        assertFalse(a.clicked(5, 5, 0), "no pointer action while disabled");
        control.setFocused(true);
        assertTrue(control.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)),
                "a targeted disabled selection is consumed (cannot fall through)");
        assertEquals(0, calls.get());
        // The hover target is disabled-driven too — no affordance animation.
        a.syncChannels(0, 0, 16, 16, 5, 5);
        assertEquals(0f, a.hoverT(), "disabled actions have no hover target");
    }

    @Test
    void focusIsIndependentOfHoverAndNarrationIsTextual() {
        AtomicInteger calls = new AtomicInteger();
        IconAction a = action(calls, new AtomicBoolean(true));
        SemanticActionControl control = a.interactionControl();
        control.setAvailable(true);
        a.syncChannels(0, 0, 16, 16, -100, -100); // pointer parked away
        control.setFocused(true);
        assertEquals(0f, a.hoverT(), "keyboard focus at hoverT = 0");
        assertTrue(a.isFocused());
        assertEquals(0, calls.get(), "focus alone never activates");
        // The accessible label is textual and independent of the glyph.
        assertEquals("Remove item", control.action().accessibleName().getString());
        assertNotEquals(a.getClass().getSimpleName(), control.action().accessibleName().getString());
    }

    // ---- Source-contract pins: the primitive ----

    @Test
    void primitiveUsesTheCanonicalVocabulary() {
        String s = src(SRC);
        assertTrue(s.contains("HoverAnim.symmetric(HOVER_MS)"), "canonical symmetric 140 ms");
        assertTrue(s.contains("public static final long HOVER_MS = 140L;"));
        assertTrue(s.contains("MaterialIconRenderer.drawIcon"), "approved icon infrastructure");
        assertTrue(s.contains("SemanticActionControl"), "semantic control backbone");
        assertTrue(s.contains("ThemeManager.semanticContrast().focusNeutral()"),
                "resolve-time Button-family focus hairline");
        assertTrue(s.contains("roundness().radiusSmall()"), "C-7 radius token");
        assertTrue(s.contains("NATURAL_EM_GUI, Math.min(w, h) - 2"), "explicit finite per-instance em");
        assertFalse(s.contains("MinecraftSemanticFeedback.INSTANCE.play"),
                "no local click sound — ownership lives in the SemanticAction");
        assertTrue(s.contains("action.enabled() && Widget.inBounds"),
                "hover target is pointer + enabled only (never focus, never availability)");
    }

    // ---- Source-contract pins: the pilots ----

    @Test
    void pilotAItemScaleRemoveUsesThePrimitive() {
        String s = src(ITEM_SCALE);
        assertTrue(s.contains("rowRemoveActions.computeIfAbsent"),
                "one long-lived action per item id (no per-frame construction)");
        assertTrue(s.contains("rowRemoveAction(idStr).paint("), "the row paints through the primitive");
        assertTrue(s.contains("remove.clicked(mouseX, mouseY, button)"),
                "pointer routes through the semantic action");
        assertTrue(s.contains("private void removeItem(String idStr)"),
                "the ONE removal path — action and legacy fallback converge");
        assertTrue(s.contains("rebuildRowControlList();"), "mutation rebuilds the host-visible list");
        assertFalse(s.contains("ctx.drawString(tr, \"x\""), "the text x is gone");
        // Neighboring controls: the expand band and the delete zone stay
        // disjoint (the click walk keeps its zone order).
        assertTrue(s.contains("mouseX >= rightX - 16 && mouseX < rightX"));
        assertTrue(s.contains("mouseX >= rowX + 10 && mouseX < rightX - 16"));
    }

    @Test
    void pilotBEffectExpiryAddUsesThePrimitive() {
        String s = src(EFFECT_EXPIRY);
        assertTrue(s.contains("addAction.syncChannels"));
        assertTrue(s.contains("addAction.paintGlyph"));
        assertTrue(s.contains("addAction.clicked(mouseX, mouseY, button)"));
        assertTrue(s.contains("() -> foundEffect != null"),
                "the enabled gate IS the no-match state");
        assertTrue(s.contains("private void addFoundEffect()"), "the ONE add path");
        assertFalse(s.contains("drawCentered(ctx, tr, \"+\""), "the text + is gone");
    }

    @Test
    void pilotCHudEditorXUsesThePrimitive() {
        String s = src(HUD_EDITOR);
        assertTrue(s.contains("xActions.computeIfAbsent"),
                "one long-lived action per module id");
        assertTrue(s.contains("xAction(m).interactionControl()"));
        assertTrue(s.contains("action.interactionControl().setAvailable(false);"),
                "frame-start availability sweep");
        assertTrue(s.contains("xAction(hit).clicked(mx, my, button)"));
        assertTrue(s.contains("Component.literal(\"Disable \" + m.displayName())"),
                "the accessible label names the domain action, not the glyph");
        // The phantom X-KEY hint was C-8's item: resolved by REWORDING to
        // the real affordance (the badge click) — still no keyPressed.
        assertEquals(0, count(s, "keyPressed"));
        assertTrue(s.contains("X badge: disable"),
                "C-8 resolution: the hint now describes the badge click truthfully");
        // The badge keeps its status-overlay geometry: the hairline is square.
        assertFalse(s.contains("drawRoundedOutlineAA"), "no rounded chrome on this screen's overlays");
    }

    @Test
    void hostRegistersDynamicSetsThroughTheFrameDiff() throws Exception {
        String detail = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/client/java/com/aurora/client/screen/FeatureDetailScreen.java"));
        assertTrue(detail.contains("registeredSettingControls"));
        assertTrue(detail.contains("sameControlList(prev, controls)"));
        assertTrue(detail.contains("this.removeWidget(c);"),
                "removed rows unregister their stale control");
        assertTrue(detail.contains("if (this.getFocused() == c) this.setFocused(null);"),
                "a removed control that held focus drops it");
        // Pointer-mirror focus for multi-control settings.
        assertTrue(detail.contains("mouseX >= c.getX() && mouseX < c.getRight()"));
    }

    @Test
    void fontSubsetCarriesTheActionGlyphsDeterministically() {
        String icons = src(FEATURE_ICONS);
        assertTrue(icons.contains("\"_action_add\""));
        assertTrue(icons.contains("\"_action_close\""));
        try {
            var subset = new java.io.FileInputStream(
                    "src/client/resources/assets/aurora/font/material_symbols_rounded.ttf");
            subset.close();
        } catch (java.io.IOException e) {
            fail("subset font missing: " + e);
        }
    }

    private static int count(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
