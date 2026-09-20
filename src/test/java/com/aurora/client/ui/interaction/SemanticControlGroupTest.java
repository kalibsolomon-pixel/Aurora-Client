package com.aurora.client.ui.interaction;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C-3 behavioral contract of the roving group primitive: geometry
 * (horizontal / vertical / grid strides), wrap in both directions, the
 * traversal-eligibility skip, the manual-activation policy (arrows move
 * focus silently — no behavior, no selection), and the screen-side
 * {@code rove} interceptor's gate + focus hand-off.
 */
class SemanticControlGroupTest {

    private static SemanticActionControl control(String name,
                                                 java.util.function.BooleanSupplier enabled,
                                                 AtomicInteger activations) {
        return new SemanticActionControl(SemanticAction.button(
                Component.literal(name), Component::empty, Component::empty,
                enabled, activations::incrementAndGet),
                SemanticFeedback.NONE, null,
                SemanticActionControl.PointerRouting.MANUAL);
    }

    private static KeyEvent key(int keyCode) {
        return new KeyEvent(keyCode, 0, 0);
    }

    /** available + in-bounds: eligible for focus (the nextFocusPath gate). */
    private static void makeEligible(SemanticActionControl c) {
        c.setAvailable(true);
        c.setBounds(0, 0, 10, 10);
    }

    @Test
    void horizontalRingMovesLeftRightAndWrapsBothWays() {
        SemanticControlGroup group = SemanticControlGroup.horizontal();
        SemanticActionControl[] m = new SemanticActionControl[3];
        for (int i = 0; i < 3; i++) {
            m[i] = control("m" + i, () -> true, new AtomicInteger());
            makeEligible(m[i]);
            group.attach(m[i]);
        }

        assertSame(m[1], group.rovingTarget(key(GLFW.GLFW_KEY_RIGHT), m[0]));
        assertSame(m[2], group.rovingTarget(key(GLFW.GLFW_KEY_RIGHT), m[1]));
        assertSame(m[0], group.rovingTarget(key(GLFW.GLFW_KEY_RIGHT), m[2]), "wrap forward");
        assertSame(m[2], group.rovingTarget(key(GLFW.GLFW_KEY_LEFT), m[0]), "wrap backward");
        assertSame(m[0], group.rovingTarget(key(GLFW.GLFW_KEY_LEFT), m[1]));
        // Cross-axis keys are NOT the group's — they fall through untouched.
        assertNull(group.rovingTarget(key(GLFW.GLFW_KEY_UP), m[0]));
        assertNull(group.rovingTarget(key(GLFW.GLFW_KEY_DOWN), m[0]));
    }

    @Test
    void verticalRingOwnsUpDownOnly() {
        SemanticControlGroup group = SemanticControlGroup.vertical();
        SemanticActionControl top = control("top", () -> true, new AtomicInteger());
        SemanticActionControl bottom = control("bottom", () -> true, new AtomicInteger());
        makeEligible(top);
        makeEligible(bottom);
        group.attach(top);
        group.attach(bottom);

        assertSame(bottom, group.rovingTarget(key(GLFW.GLFW_KEY_DOWN), top));
        assertSame(top, group.rovingTarget(key(GLFW.GLFW_KEY_UP), bottom));
        assertNull(group.rovingTarget(key(GLFW.GLFW_KEY_LEFT), top));
        assertNull(group.rovingTarget(key(GLFW.GLFW_KEY_RIGHT), top));
    }

    @Test
    void gridStepsByOneAndByRowWithListWrap() {
        // 10 peers in the AccentSetting shape: 5 per row, 2 rows.
        SemanticControlGroup group = SemanticControlGroup.grid(5);
        SemanticActionControl[] m = new SemanticActionControl[10];
        for (int i = 0; i < 10; i++) {
            m[i] = control("m" + i, () -> true, new AtomicInteger());
            makeEligible(m[i]);
            group.attach(m[i]);
        }

        assertSame(m[5], group.rovingTarget(key(GLFW.GLFW_KEY_RIGHT), m[4]), "row tail continues into next row");
        assertSame(m[4], group.rovingTarget(key(GLFW.GLFW_KEY_LEFT), m[5]));
        assertSame(m[7], group.rovingTarget(key(GLFW.GLFW_KEY_DOWN), m[2]));
        assertSame(m[2], group.rovingTarget(key(GLFW.GLFW_KEY_UP), m[7]));
        assertSame(m[9], group.rovingTarget(key(GLFW.GLFW_KEY_LEFT), m[0]), "wrap backward over the ring");
        assertSame(m[0], group.rovingTarget(key(GLFW.GLFW_KEY_RIGHT), m[9]), "wrap forward over the ring");
        assertSame(m[4], group.rovingTarget(key(GLFW.GLFW_KEY_DOWN), m[9]), "row wrap is over the ordered list");
    }

    @Test
    void rovingSkipsUnavailableDisabledAndUnfocusableMembers() {
        SemanticControlGroup group = SemanticControlGroup.horizontal();
        AtomicInteger silent = new AtomicInteger();
        SemanticActionControl a = control("a", () -> true, new AtomicInteger());
        SemanticActionControl hidden = control("hidden", () -> true, new AtomicInteger());
        SemanticActionControl disabled = control("disabled", () -> false, new AtomicInteger());
        SemanticActionControl unfocusable = new SemanticActionControl(new SemanticAction(
                Component.literal("unfocusable"), null, null, () -> true,
                silent::incrementAndGet, SemanticSound.ACTIVATION, false, true),
                SemanticFeedback.NONE, null, SemanticActionControl.PointerRouting.MANUAL);
        SemanticActionControl b = control("b", () -> true, new AtomicInteger());

        makeEligible(a);
        makeEligible(hidden);   // then made unavailable below
        makeEligible(disabled);
        makeEligible(unfocusable);
        makeEligible(b);
        hidden.setAvailable(false);
        for (SemanticActionControl c : new SemanticActionControl[]{a, hidden, disabled, unfocusable, b}) {
            group.attach(c);
        }

        assertSame(b, group.rovingTarget(key(GLFW.GLFW_KEY_RIGHT), a),
                "unavailable, action-disabled, and non-focusable members are all skipped");
        assertEquals(0, silent.get());
        // The reverse direction skips the same members.
        assertSame(a, group.rovingTarget(key(GLFW.GLFW_KEY_LEFT), b));
    }

    @Test
    void gridRovingWalksTheFullRingWhenTheStrideTargetIsClipped() {
        // The stride-divides-ring trap (found live by the C-3 boots): grid(5)
        // over 10 members, DOWN = +5, and gcd(5, 10) = 5 — a stride-multiple
        // iteration only ever visits {from, from+5} and dead-ends when the
        // direct target is clipped. The walk must continue member-by-member.
        SemanticControlGroup group = SemanticControlGroup.grid(5);
        SemanticActionControl[] m = new SemanticActionControl[10];
        for (int i = 0; i < 10; i++) {
            m[i] = control("m" + i, () -> true, new AtomicInteger());
            makeEligible(m[i]);
            group.attach(m[i]);
        }
        KeyEvent down = key(GLFW.GLFW_KEY_DOWN);
        KeyEvent up = key(GLFW.GLFW_KEY_UP);

        // Direct row target clipped: skip to the next member in direction.
        m[6].setAvailable(false);
        assertSame(m[7], group.rovingTarget(down, m[1]),
                "DOWN past a clipped direct target continues to the next member");
        assertSame(m[0], group.rovingTarget(up, m[5]),
                "UP past a clipped direct target continues backward");

        // Whole second row clipped: the ring wraps through to row one —
        // total, deterministic, never a dead end while any peer is eligible.
        for (int i = 5; i < 10; i++) m[i].setAvailable(false);
        assertSame(m[0], group.rovingTarget(down, m[1]),
                "DOWN with the lower row clipped wraps to the first eligible member");
        assertSame(m[1], group.rovingTarget(down, m[0]),
                "the arc between current and the stride target stays reachable (current is skipped, not a terminator)");
    }

    @Test
    void fullLapWithoutAnotherEligibleMemberFallsThrough() {
        SemanticControlGroup group = SemanticControlGroup.horizontal();
        AtomicInteger calls = new AtomicInteger();
        SemanticActionControl only = control("only", () -> true, calls);
        makeEligible(only);
        group.attach(only);
        assertNull(group.rovingTarget(key(GLFW.GLFW_KEY_RIGHT), only),
                "a degenerate ring has nowhere to rove — the key falls through");

        SemanticActionControl disabled = control("disabled", () -> false, calls);
        makeEligible(disabled);
        group.attach(disabled);
        assertNull(group.rovingTarget(key(GLFW.GLFW_KEY_RIGHT), only),
                "eligible current + ineligible rest also falls through");
    }

    @Test
    void foreignKeysAndNonMembersYieldNoTarget() {
        SemanticControlGroup group = SemanticControlGroup.horizontal();
        SemanticActionControl a = control("a", () -> true, new AtomicInteger());
        makeEligible(a);
        group.attach(a);
        SemanticActionControl stranger = control("stranger", () -> true, new AtomicInteger());
        makeEligible(stranger);

        assertNull(group.rovingTarget(key(GLFW.GLFW_KEY_TAB), a));
        assertNull(group.rovingTarget(key(GLFW.GLFW_KEY_ENTER), a));
        assertNull(group.rovingTarget(key(GLFW.GLFW_KEY_SPACE), a));
        assertNull(group.rovingTarget(key(GLFW.GLFW_KEY_RIGHT), stranger),
                "a control that was never attached is not roved");
        assertNull(group.rovingTarget(key(GLFW.GLFW_KEY_RIGHT), null));
    }

    @Test
    void attachIsIdentityIdempotentAndPreservesFirstOrder() {
        SemanticControlGroup group = SemanticControlGroup.vertical();
        SemanticActionControl a = control("a", () -> true, new AtomicInteger());
        SemanticActionControl b = control("b", () -> true, new AtomicInteger());
        makeEligible(a);
        makeEligible(b);

        group.attach(a);
        group.attach(b);
        group.attach(a); // re-init / re-materialization path
        group.attach(null);
        assertEquals(2, group.size());
        assertEquals(java.util.List.of(a, b), group.members(),
                "roving order is first-attach order (visual order)");
        assertSame(group, a.interactionGroup());
        assertSame(group, b.interactionGroup());
    }

    @Test
    void gridRejectsNonPositiveColumns() {
        assertThrows(IllegalArgumentException.class, () -> SemanticControlGroup.grid(0));
        assertThrows(IllegalArgumentException.class, () -> SemanticControlGroup.grid(-3));
    }

    @Test
    void roveMovesFocusSilentlyThroughTheScreenConsumer() {
        SemanticControlGroup group = SemanticControlGroup.horizontal();
        AtomicInteger callsA = new AtomicInteger();
        AtomicInteger callsB = new AtomicInteger();
        SemanticActionControl a = control("a", () -> true, callsA);
        SemanticActionControl b = control("b", () -> true, callsB);
        makeEligible(a);
        makeEligible(b);
        group.attach(a);
        group.attach(b);

        AtomicReference<GuiEventListener> focused = new AtomicReference<>(a);
        assertTrue(SemanticControlGroup.rove(focused.get(), key(GLFW.GLFW_KEY_RIGHT), focused::set));
        assertSame(b, focused.get(), "the consumer receives the roving target — the screen's setFocused");
        assertEquals(0, callsA.get() + callsB.get(),
                "manual activation: an arrow NEVER runs a behavior — selection stays on Enter/Space/click");
        // The move is one keypress one step: the new focus owner's LEFT goes back.
        assertTrue(SemanticControlGroup.rove(focused.get(), key(GLFW.GLFW_KEY_LEFT), focused::set));
        assertSame(a, focused.get());
    }

    @Test
    void roveIgnoresUngroupedUnavailableAndNullFocus() {
        AtomicInteger calls = new AtomicInteger();
        SemanticActionControl plain = control("plain", () -> true, calls);
        makeEligible(plain);
        AtomicReference<GuiEventListener> focused = new AtomicReference<>(plain);
        assertFalse(SemanticControlGroup.rove(focused.get(), key(GLFW.GLFW_KEY_RIGHT), focused::set));
        assertSame(plain, focused.get(), "an ungrouped focused control is untouched");

        SemanticControlGroup group = SemanticControlGroup.horizontal();
        SemanticActionControl a = control("a", () -> true, calls);
        SemanticActionControl b = control("b", () -> true, calls);
        makeEligible(a);
        makeEligible(b);
        group.attach(a);
        group.attach(b);
        a.setAvailable(false); // e.g. scrolled out of the clip band between frames
        focused.set(a);
        assertFalse(SemanticControlGroup.rove(focused.get(), key(GLFW.GLFW_KEY_RIGHT), focused::set));
        assertSame(a, focused.get(), "an unavailable focus owner does not rove");

        focused.set(null);
        assertFalse(SemanticControlGroup.rove(focused.get(), key(GLFW.GLFW_KEY_RIGHT), focused::set));
    }
}
