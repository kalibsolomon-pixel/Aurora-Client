package com.aurora.client.ui.interaction;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * An ordered peer group that owns ARROW-KEY ROVING FOCUS for its members
 * (Phase C-3). One group models one semantic cluster whose members are
 * traversed with arrows instead of Tab: navigation tabs, a peer-selection
 * value grid, later a segmented control's segments (C-5).
 *
 * <p><b>Why this is a keyPressed interceptor, not a traversal override.</b>
 * Verified against 1.21.11 bytecode: {@code Screen.keyPressed} invokes
 * {@code AbstractContainerEventHandler.keyPressed}/{@code nextFocusPath} via
 * <i>invokespecial</i>, so a Screen's own overrides of those methods never
 * run — and vanilla's container walk, while it does move focus spatially
 * among available children, is unscoped, unwrapping, and moves focus only.
 * The correct seam is therefore the screen's {@code keyPressed} BEFORE
 * {@code super}: {@link #rove(GuiEventListener, KeyEvent, Consumer)} consumes
 * the arrow while the focused child is a group member, which both scopes the
 * move to the group and prevents vanilla's spatial walk from escaping it.
 *
 * <p><b>Manual activation policy (the recommended one, adopted):</b> arrows
 * move focus SILENTLY — they never select. Selection has side effects
 * (category switch resets the pack grid, accent changes reload the theme), so
 * selecting on focus would make arrow browsing destructive; Enter/Space/click
 * remain the only selection paths, exactly the activation contract the
 * members' {@link SemanticAction}s already own. The optional
 * selection-follows-focus variant deliberately does not exist here yet; add it
 * as an explicit policy only if a consumer is ever verified to want it.
 *
 * <p><b>Movement rules.</b> Members are an ordered ring in
 * {@link #attach(SemanticActionControl)} order — attach in VISUAL order
 * (row-major for a grid). A step aims at the geometry's stride
 * ({@code horizontal()} ±1 on Left/Right, {@code vertical()} ±1 on Up/Down,
 * {@code grid(columns)} ±1 on Left/Right and ±columns on Up/Down), WRAPS in
 * both directions, and SKIPS members that are not traversal-eligible
 * (unavailable, action-disabled, or non-focusable — the same gate
 * {@code SemanticActionControl.nextFocusPath} applies, so arrows can never
 * land where Tab cannot) by continuing member-by-member in the step's
 * direction through the whole ring. Cross-axis keys are NOT owned: a
 * horizontal group lets Up/Down fall through to the screen, keeping
 * vanilla's directional behavior outside the group's axis. When a full lap
 * finds no other eligible member, the key falls through unchanged.
 *
 * <p><b>Lifecycle.</b> Membership is for the control's lifetime: attach once
 * at the control's creation site (idempotent by identity — a re-init that
 * rebuilds widget lists re-attaches the same instance harmlessly) and never
 * detach. All three C-3 consumers own long-lived control arrays that never
 * leave their group; if a future control can be pruned, grow a detach then.
 * A control belongs to at most one group — the back-reference
 * {@link SemanticActionControl#interactionGroup()} is what lets a host's
 * single rove call serve every group it hosts without a host-side registry.
 */
public final class SemanticControlGroup {

    private enum Geometry { HORIZONTAL, VERTICAL, GRID }

    private final Geometry geometry;
    /** GRID only: members per row — Up/Down step by this stride. */
    private final int columns;
    private final List<SemanticActionControl> members = new ArrayList<>();

    private SemanticControlGroup(Geometry geometry, int columns) {
        this.geometry = geometry;
        this.columns = columns;
    }

    /** A left-to-right ring: Left = previous, Right = next. */
    public static SemanticControlGroup horizontal() {
        return new SemanticControlGroup(Geometry.HORIZONTAL, 0);
    }

    /** A top-to-bottom ring: Up = previous, Down = next. */
    public static SemanticControlGroup vertical() {
        return new SemanticControlGroup(Geometry.VERTICAL, 0);
    }

    /**
     * A row-major grid ring: Left/Right step by one member, Up/Down by one
     * row ({@code columns} members). Wrap is over the ordered member list,
     * not the 2D shape — from the last member Right wraps to the first.
     */
    public static SemanticControlGroup grid(int columns) {
        if (columns < 1) throw new IllegalArgumentException("columns must be >= 1");
        return new SemanticControlGroup(Geometry.GRID, columns);
    }

    /** Adds one member (idempotent by identity); order here is roving order. */
    public void attach(SemanticActionControl control) {
        if (control == null || containsIdentity(control)) return;
        members.add(control);
        control.attachGroup(this);
    }

    public List<SemanticActionControl> members() {
        return Collections.unmodifiableList(members);
    }

    public int size() {
        return members.size();
    }

    /**
     * The member focus should rove to, or null when this key is not the
     * group's, {@code current} is not a member, or no other eligible member
     * exists (the caller lets the key fall through in all three cases).
     *
     * <p>Candidate order: the exact stride target first (grid Up/Down land
     * one row away), then onward MEMBER BY MEMBER in the step's direction,
     * wrapping through the entire ring. A per-member walk — not stride
     * multiples — is required because a stride that divides the member
     * count ({@code gcd(stride, n) > 1}, e.g. ±5 over 10 peers) visits only
     * a sub-orbit of the ring and would declare "nowhere to rove" while
     * eligible members sit one position off the orbit (found live by the
     * C-3 boots: DOWN inside a half-clipped accent grid dead-ended instead
     * of skipping the clipped row). {@code current} itself is skipped, not
     * terminated on — the members between it and the stride target remain
     * reachable as the last-resort arc, so the ring stays total for every
     * stride.
     */
    public SemanticActionControl rovingTarget(KeyEvent event, SemanticActionControl current) {
        int step = step(event.key());
        if (step == 0) return null;
        int from = indexOf(current);
        if (from < 0) return null;
        int n = members.size();
        int direction = Integer.signum(step);
        for (int k = 0; k < n; k++) {
            SemanticActionControl candidate = members.get(Math.floorMod(from + step + k * direction, n));
            if (candidate == current) continue;
            if (candidate.isAvailable()
                    && candidate.action().enabled()
                    && candidate.action().focusable()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The screen-side keyPressed interceptor. Call BEFORE
     * {@code super.keyPressed(event)}; when the screen's focused child is an
     * available member of a group and the key is that group's arrow, focus
     * moves silently to the roving target and the key is consumed. Every
     * other shape returns false untouched.
     */
    public static boolean rove(GuiEventListener focused, KeyEvent event,
                               Consumer<GuiEventListener> setFocused) {
        if (!(focused instanceof SemanticActionControl current) || !current.isAvailable()) {
            return false;
        }
        SemanticControlGroup group = current.interactionGroup();
        if (group == null) return false;
        SemanticActionControl target = group.rovingTarget(event, current);
        if (target == null) return false;
        setFocused.accept(target);
        return true;
    }

    private int step(int key) {
        return switch (geometry) {
            case HORIZONTAL -> key == GLFW.GLFW_KEY_LEFT ? -1
                    : key == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
            case VERTICAL -> key == GLFW.GLFW_KEY_UP ? -1
                    : key == GLFW.GLFW_KEY_DOWN ? 1 : 0;
            case GRID -> key == GLFW.GLFW_KEY_LEFT ? -1
                    : key == GLFW.GLFW_KEY_RIGHT ? 1
                    : key == GLFW.GLFW_KEY_UP ? -columns
                    : key == GLFW.GLFW_KEY_DOWN ? columns
                    : 0;
        };
    }

    private boolean containsIdentity(SemanticActionControl wanted) {
        return indexOf(wanted) >= 0;
    }

    private int indexOf(SemanticActionControl wanted) {
        if (wanted == null) return -1;
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i) == wanted) return i;
        }
        return -1;
    }
}
