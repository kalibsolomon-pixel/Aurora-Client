package com.aurora.client.conformance;

import com.aurora.client.screen.FeatureIcons;
import com.aurora.client.screen.setting.AccentSetting;
import com.aurora.client.screen.setting.ColorSetting;
import com.aurora.client.screen.setting.EnumSetting;
import com.aurora.client.screen.setting.FeatureSetting;
import com.aurora.client.screen.setting.KeybindSetting;
import com.aurora.client.screen.setting.KeyListSetting;
import com.aurora.client.screen.setting.SegmentedSetting;
import com.aurora.client.theme.ThemeMode;
import com.aurora.client.theme.ThemePresets;
import com.aurora.client.ui.component.IconAction;
import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.interaction.SemanticFeedback;
import com.aurora.client.ui.interaction.SemanticSound;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The §15.2 behavioral conformance matrix — the shared button-like contract
 * run over REAL production family controls (not mocks): pointer, Enter and
 * Space converge on one exactly-once activation path; disabled is the
 * authoritative silent gate; unavailability removes activation AND traversal;
 * the accessible name is textual. Every family fixture below is the
 * production component's own {@code interactionControl(s())} artifact, so
 * the matrix is the regression net for family drift: a future control that
 * special-cases one of these channels fails here even though its bespoke
 * pilot tests still pass.
 *
 * <p>Each observable counts behavior EXECUTIONS from a reset state — one
 * accepted input = exactly one execution, so a double-firing toggle family
 * (which would return to its resting state) is caught, not masked. This
 * intentionally does NOT re-assert family-specific semantics (capture
 * teardown, popup placement, editor value invariants…) — those belong to
 * the per-family pilot suites. The exactly-once SOUND contract is verified
 * here only on the plain-action fixture, where a counting
 * {@link SemanticFeedback} can be injected; the production families play
 * through {@code MinecraftSemanticFeedback.INSTANCE} (a verified
 * null-instance-safe no-op headless), so their sound OWNERSHIP is pinned at
 * the source level by {@code ConformanceManifestTest}.
 */
class ButtonLikeConformanceTest {

    /** A production family control plus the observations the matrix needs. */
    private static final class Fixture {
        final String family;
        final SemanticActionControl control;
        /** Behavior executions observed from the reset state. */
        final Supplier<Integer> executions;
        /** Restores the pre-activation state (toggle families flip back, counters zero). */
        final Runnable reset;
        final Runnable disable;
        final Runnable enable;
        final String glyph; // non-null when the family is glyph-presented

        Fixture(String family, SemanticActionControl control, Supplier<Integer> executions,
                Runnable reset, Runnable disable, Runnable enable, String glyph) {
            this.family = family;
            this.control = control;
            this.executions = executions;
            this.reset = reset;
            this.disable = disable;
            this.enable = enable;
            this.glyph = glyph;
        }

        @Override public String toString() { return family; }
    }

    // ------------------------------------------------------------------
    //  Fixtures — production components, production controls
    // ------------------------------------------------------------------

    private static final AtomicInteger PLAIN_STEPS = new AtomicInteger();
    private static final AtomicInteger PLAIN_SOUNDS = new AtomicInteger();
    private static final AtomicBoolean PLAIN_ENABLED = new AtomicBoolean(true);
    private static final SemanticActionControl PLAIN_CONTROL = new SemanticActionControl(
            SemanticAction.button(Component.literal("Plain Action"),
                    () -> Component.literal("A plain semantic action."),
                    Component::empty,
                    PLAIN_ENABLED::get,
                    PLAIN_STEPS::incrementAndGet),
            sound -> {
                if (sound == SemanticSound.ACTIVATION) PLAIN_SOUNDS.incrementAndGet();
            },
            null, SemanticActionControl.PointerRouting.MANUAL);

    static List<Fixture> fixtures() {
        return List.of(
                plainAction(),
                enumTrigger(),
                colorTrigger(),
                keybindArm(),
                keylistAdd(),
                segmentedPeer(),
                accentPeer(),
                iconAction());
    }

    /** The generic adapter shape behind tiles, toggles, nav headers and row buttons. */
    private static Fixture plainAction() {
        return new Fixture("button-action (plain adapter)", PLAIN_CONTROL,
                PLAIN_STEPS::get,
                () -> PLAIN_STEPS.set(0),
                () -> PLAIN_ENABLED.set(false),
                () -> PLAIN_ENABLED.set(true),
                null);
    }

    private static Fixture enumTrigger() {
        AtomicReference<ThemeMode> v = new AtomicReference<>(ThemeMode.DARK);
        EnumSetting<ThemeMode> row = new EnumSetting<>("Mode", ThemeMode.class, v::get, v::set);
        AtomicBoolean disabled = new AtomicBoolean(false);
        row.disabled(disabled::get);
        return new Fixture("enum trigger", row.interactionControl(),
                () -> boolField(row, "expanded") ? 1 : 0,
                () -> setBool(row, "expanded", false),
                () -> disabled.set(true), () -> disabled.set(false),
                null);
    }

    private static Fixture colorTrigger() {
        AtomicInteger color = new AtomicInteger(0xFFE91E63);
        ColorSetting row = new ColorSetting("Color", color::get, c -> color.set(c));
        AtomicBoolean disabled = new AtomicBoolean(false);
        row.disabled(disabled::get);
        return new Fixture("color-swatch trigger", row.interactionControl(),
                () -> boolField(row, "expanded") ? 1 : 0,
                () -> setBool(row, "expanded", false),
                () -> disabled.set(true), () -> disabled.set(false),
                null);
    }

    private static Fixture keybindArm() {
        AtomicInteger binding = new AtomicInteger(GLFW.GLFW_KEY_J);
        KeybindSetting row = new KeybindSetting("Pilot Key", binding::get, binding::set);
        AtomicBoolean disabled = new AtomicBoolean(false);
        row.disabled(disabled::get);
        return new Fixture("keybind arm", row.interactionControl(),
                () -> boolField(row, "listening") ? 1 : 0,
                () -> setBool(row, "listening", false),
                () -> disabled.set(true), () -> disabled.set(false),
                null);
    }

    private static Fixture keylistAdd() {
        AtomicReference<List<Integer>> keys = new AtomicReference<>(List.of(GLFW.GLFW_KEY_H));
        KeyListSetting row = new KeyListSetting("Pilot Keys", keys::get, keys::set);
        AtomicBoolean disabled = new AtomicBoolean(false);
        row.disabled(disabled::get);
        // The add action hosts after the (one) chip control — visual order,
        // the C-4B hosting rule.
        return new Fixture("keylist add", row.interactionControls().get(1),
                () -> boolField(row, "listening") ? 1 : 0,
                () -> setBool(row, "listening", false),
                () -> disabled.set(true), () -> disabled.set(false),
                null);
    }

    private static Fixture segmentedPeer() {
        AtomicReference<ThemeMode> mode = new AtomicReference<>(ThemeMode.DARK);
        AtomicInteger setterCalls = new AtomicInteger();
        SegmentedSetting<ThemeMode> row = new SegmentedSetting<>("Mode", ThemeMode.class,
                mode::get, v -> {
                    setterCalls.incrementAndGet();
                    mode.set(v);
                });
        // The component's enabled gate mirrors the row's disabled supplier
        // once per frame in production (renderOverlay's sync); the fixture
        // performs that one production sync step directly.
        Runnable syncGate = () -> {
            try {
                Field f = row.getClass().getDeclaredField("control");
                f.setAccessible(true);
                ((com.aurora.client.ui.component.SegmentedControl) f.get(row))
                        .disabled(row.isDisabled());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        };
        return new Fixture("segmented peer", row.interactionControls().get(1),
                setterCalls::get,
                () -> {
                    mode.set(ThemeMode.DARK);
                    setterCalls.set(0);
                },
                () -> {
                    row.disabled(() -> true);
                    syncGate.run();
                },
                () -> {
                    row.disabled(() -> false);
                    syncGate.run();
                },
                null);
    }

    private static Fixture accentPeer() {
        AtomicInteger accent = new AtomicInteger(ThemePresets.RED);
        AtomicInteger setterCalls = new AtomicInteger();
        AccentSetting row = new AccentSetting("Accent Color", accent::get, v -> {
            setterCalls.incrementAndGet();
            accent.set(v);
        });
        AtomicBoolean disabled = new AtomicBoolean(false);
        row.disabled(disabled::get);
        // Visual-order peers: Blue, Indigo, … Red(4) — Indigo(1) is a genuine change.
        return new Fixture("accent peer", row.interactionControls().get(1),
                setterCalls::get,
                () -> {
                    accent.set(ThemePresets.RED);
                    setterCalls.set(0);
                },
                () -> disabled.set(true), () -> disabled.set(false),
                null);
    }

    private static Fixture iconAction() {
        AtomicInteger removals = new AtomicInteger();
        AtomicBoolean enabled = new AtomicBoolean(true);
        IconAction action = new IconAction(FeatureIcons.get("_action_close"),
                SemanticAction.button(Component.literal("Remove entry"),
                        () -> Component.literal("Removes one configured entry."),
                        Component::empty,
                        enabled::get,
                        removals::incrementAndGet),
                () -> 0xFFFFFFFF, () -> 0xFFEB0029);
        return new Fixture("icon action", action.interactionControl(),
                removals::get,
                () -> removals.set(0),
                () -> enabled.set(false), () -> enabled.set(true),
                FeatureIcons.get("_action_close"));
    }

    @AfterEach
    void teardownGlobalOwners() {
        KeybindSetting.cancelActiveCapture();
        FeatureSetting.clearFocus();
    }

    // ------------------------------------------------------------------
    //  The shared matrix
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("fixtures")
    void pointerActivationRunsTheBehaviorExactlyOnce(Fixture f) {
        f.reset.run();
        f.enable.run();
        f.control.setAvailable(true);
        f.control.setBounds(10, 20, 90, 18);

        assertTrue(f.control.activateFromPointer(55, 29, 0),
                f.family + ": in-bounds accepted pointer activation");
        assertEquals(1, f.executions.get(), f.family + ": exactly one behavior run");
        // Out-of-bounds and non-primary buttons never activate.
        assertFalse(f.control.activateFromPointer(500, 500, 0));
        assertFalse(f.control.activateFromPointer(55, 29, 1));
        assertEquals(1, f.executions.get(), f.family + ": rejected presses stay inert");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("fixtures")
    void keyboardEnterAndSpaceEachRunTheBehaviorExactlyOnce(Fixture f) {
        f.enable.run();
        f.control.setAvailable(true);
        f.control.setBounds(10, 20, 90, 18);
        f.control.setFocused(true);

        f.reset.run();
        assertTrue(f.control.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)),
                f.family + ": Enter is consumed by the focused control");
        assertEquals(1, f.executions.get(), f.family + ": Enter = exactly one activation");

        f.reset.run();
        assertTrue(f.control.keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0)),
                f.family + ": Space is consumed by the focused control");
        assertEquals(1, f.executions.get(), f.family + ": Space = exactly one activation");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("fixtures")
    void disabledIsAuthoritativeSilentAndConsuming(Fixture f) {
        f.reset.run();
        f.control.setAvailable(true);
        f.control.setBounds(10, 20, 90, 18);
        f.control.setFocused(true);
        f.disable.run();
        int soundsBefore = PLAIN_SOUNDS.get();

        assertFalse(f.control.activateFromPointer(55, 29, 0), f.family + ": pointer rejected");
        assertTrue(f.control.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)),
                f.family + ": the rejected selection key is still CONSUMED (cannot fall through)");
        assertTrue(f.control.keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0)));
        assertEquals(0, f.executions.get(), f.family + ": no behavior through the gate");
        assertEquals(soundsBefore, PLAIN_SOUNDS.get(), f.family + ": no sound through the gate");
        assertFalse(f.control.action().enabled(), f.family + ": the gate is the action's own");
        f.enable.run();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("fixtures")
    void unavailabilityRemovesActivationAndTraversal(Fixture f) {
        f.reset.run();
        f.enable.run();
        f.control.setBounds(10, 20, 90, 18);
        f.control.setAvailable(false);

        assertFalse(f.control.activateFromPointer(55, 29, 0),
                f.family + ": an unavailable control cannot activate");
        assertEquals(0, f.executions.get());
        assertNull(f.control.nextFocusPath(new FocusNavigationEvent.TabNavigation(true)),
                f.family + ": an unavailable control is invisible to traversal");
        assertEquals(net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority.NONE,
                f.control.narrationPriority(),
                f.family + ": an unavailable control narrates nothing");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("fixtures")
    void accessibleNameIsTextualAndGlyphIndependent(Fixture f) {
        String name = f.control.action().accessibleName().getString();
        assertFalse(name == null || name.isBlank(), f.family + ": accessible name is never blank");
        if (f.glyph != null) {
            assertNotEquals(f.glyph, name, f.family + ": the name must not be the glyph");
            assertFalse(name.startsWith("_"), f.family + ": the name is not an icon id");
        }
    }

    // ------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------

    private static boolean boolField(Object target, String name) {
        try {
            Field field = findField(target.getClass(), name);
            field.setAccessible(true);
            return field.getBoolean(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("fixture field " + name, e);
        }
    }

    private static void setBool(Object target, String name, boolean value) {
        try {
            Field field = findField(target.getClass(), name);
            field.setAccessible(true);
            field.setBoolean(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("fixture field " + name, e);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }
}
