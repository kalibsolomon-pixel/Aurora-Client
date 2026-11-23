package com.aurora.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.HudAnchor;
import com.aurora.client.hud.module.HudModule;
import com.aurora.client.hud.module.HudModuleManager;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.component.Widget;

import com.aurora.client.util.AuroraTheme;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * In-game HUD editor. Opened via RShift.
 *
 * <p>Wave 3 controls:
 * <ul>
 *   <li><b>Left-click + drag (body)</b> — reposition.</li>
 *   <li><b>Left-click + drag (corner)</b> — resize uniformly. The opposite
 *       corner stays fixed. Works on every module that has scale.</li>
 *   <li><b>Shift + left-click</b> — open the module's Aurora settings detail.</li>
 *   <li><b>Right-click</b> — toggle editor visibility (existing behavior).</li>
 *   <li><b>Shift + right-click</b> — toggle lock. Locked modules refuse
 *       drag and resize input.</li>
 *   <li><b>Click the X icon</b> — disable the module (same as toggling its
 *       feature off in the modules grid).</li>
 *   <li><b>ESC</b> — save layout and exit.</li>
 *   <li><b>Reset Positions</b> — clears saved layouts. Destructive: press
 *       again within 3 s to confirm; ESC or any other click disarms (design
 *       language §8.7).</li>
 * </ul>
 *
 * <p>On the shared framework: buttons are the canonical themed
 * {@link ButtonWidget}, and every status color (module outlines, badges,
 * label plates, grid, dim) resolves through theme tokens — the semantic
 * status tokens carry the enabled/disabled/dragging/locked meanings, so
 * they read correctly in Dark and Light mode alike.
 *
 * <p>Glass rollout: the top-level chrome (the two floating action buttons)
 * is NEUTRAL raised glass — "Aurora Settings" is a navigation action, not a
 * primary/selected state, so it takes the neutral tint like the Theme
 * screen's Profiles button. The editor's overlay chrome (module outlines,
 * badges, label plates) is editor CONTENT — status semantics carried by the
 * semantic tokens — and stays opaque exactly where it is.
 */
public class HudEditorScreen extends Screen implements ThemedScreen {

    // ------------------------------------------------------------------
    //  Status colors — token-driven (semantic status + neutral chrome),
    //  resolved per draw so a theme/profile change mid-session recolors the
    //  editor immediately with zero cached state. The two pure whites are
    //  structural neutrals (badge glyph / resize handle — same convention as
    //  every knob and thumb in the component framework).
    // ------------------------------------------------------------------
    private int outlineEnabled()  { return ThemeManager.color(ThemeToken.ON_OVERLAY); }
    private int outlineDisabled() { return ThemeManager.color(ThemeToken.SEMANTIC_ERROR); }
    private int outlineDrag()     { return ThemeManager.color(ThemeToken.SEMANTIC_WARNING); }
    private int outlineLocked()   { return ThemeManager.color(ThemeToken.SECONDARY_ACCENT); }
    /** X badge fill — semantic-error RGB at the original 0xC0 strength. */
    private int xIconBg()         { return (0xC0 << 24) | (ThemeManager.color(ThemeToken.SEMANTIC_ERROR) & 0x00FFFFFF); }
    private static final int X_ICON_FG = 0xFFFFFFFF;
    private static final int CORNER_HANDLE = 0xFFFFFFFF;
    /** Lock badge fill — inset-surface RGB at the original 0xC0 strength. */
    private int lockIconBg()      { return (0xC0 << 24) | (ThemeManager.color(ThemeToken.SURFACE_INSET) & 0x00FFFFFF); }
    private int lockIconFg()      { return ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY); }
    /** Module label plate — overlay-dim RGB at the original 0xA0 strength. */
    private int labelBg()         { return (0xE6 << 24) | (MaterialSurface.color(ThemeToken.SURFACE_VARIANT) & 0x00FFFFFF); }

    private static final int CORNER_HIT_RADIUS = 6;
    // C-8 halo ruling (recorded, deliberate): the ±6 corner squares and the
    // findAt AABB halo are an intentional grab AFFORDANCE — corner handles
    // paint 4×4 at the corner, deliberately slightly outside the AABB, and
    // a forgiving zone is what makes them grabbable. It cannot hijack a
    // neighbor's painted controls: findAt keeps the LAST hit in manager
    // order, which is the render order — the visually TOPMOST module wins,
    // so pointer routing matches paint truth. Within one module the X
    // badge's rect is tested BEFORE the corner zones: the two painted
    // affordances occupy disjoint pixels (X spans [w-11, w-2)×[2, 11), the
    // TR handle paints [w-2, w+2)×[-2, 2)), and every pixel of each zone's
    // overlap with the other's RECT belongs to exactly the affordance
    // painted there — X pixels disable, handle-adjacent pixels resize.
    private static final int X_ICON_SIZE       = 9;
    private static final int LOCK_ICON_SIZE    = 9;

    private enum DragMode { NONE, MOVE, RESIZE }

    private DragMode dragMode = DragMode.NONE;
    private HudModule active;
    private int dragOffsetX, dragOffsetY;

    // Frozen original drag state (move + resize use these).
    private int dragW, dragH;

    // Resize: pivot stays fixed in screen space; original scale captured.
    private float resizeOrigScale;
    private int   resizePivotX, resizePivotY;
    private int   resizeOrigDiagonal;

    public HudEditorScreen() {
        super(Component.literal("Aurora HUD Editor"));
    }

    /** Press-again confirmation for Reset Positions (design language §8.7). */
    private final com.aurora.client.ui.interaction.ArmedAction resetConfirm = new com.aurora.client.ui.interaction.ArmedAction();
    /** The label/styling state {@link #syncResetButton} last applied (change detection only). */
    private boolean resetBtnArmed;
    private ButtonWidget resetBtn;
    /** Transient bottom-center confirmation after a completed reset (the manager screens' Toast). */
    private final com.aurora.client.ui.component.Toast resetToast = new com.aurora.client.ui.component.Toast();

    private java.util.List<String> guideLines = java.util.List.of();
    private int guideWidth = -1;
    /** The instruction pill's visibility: fades out while the cursor is over it. */
    private final com.aurora.client.ui.util.TimedTransition guideFade =
            new com.aurora.client.ui.util.TimedTransition(120, com.aurora.client.ui.util.CubicBezier.EASE, 1);
    private int guideStyle = -1;

    @Override
    protected void init() {
        int w = 120;
        int h = 20;

        // Owner corrections (2026-10-19): Aurora Settings sits over the
        // crosshair and Reset Positions is a compact corner action (bottom right). Both are the
        // raised, bevelled control face every Aurora window uses (owner, 2026-11-18).
        this.addRenderableWidget(ButtonWidget.semantic(
                (this.width - w) / 2, (this.height - h) / 2, w, h,
                Component.literal("Aurora Settings"),
                "Captures the current layout and opens Aurora's settings.",
                () -> {
                    AuroraClient.captureLayouts(AuroraClient.modules());
                    AuroraConfig.save();
                    if (this.minecraft != null) {
                        com.aurora.client.adapter.NativeUi.setScreen(this.minecraft, AuroraScreen.create());
                    }
                }).glassBackground(true));

        // Label width + the painter's 20 px ellipsis reserve + 2 px breathing room.
        // Sized from the WIDER of the two labels so "Confirm reset" (the armed
        // state, design language §8.7) never ellipsizes ("Reset Positions"
        // measures wider at both rasters, but the max is explicit).
        int resetW = Math.min(w, Math.max(this.font.width("Reset Positions"),
                this.font.width("Confirm reset")) + 22);
        int resetH = com.aurora.client.ui.util.UiDensity.HEADER_BUTTON_H;
        // Bottom-right corner (2026-11-18 council pass): the top-right corner is the default
        // Potion Effects anchor, whose editor frame ran through the button.
        resetBtn = ButtonWidget.semantic(
                this.width - resetW - 8, this.height - resetH - 8, resetW, resetH,
                Component.literal(resetConfirm.isArmed() ? "Confirm reset" : "Reset Positions"),
                "Moves every HUD module back to its default anchor, offset and scale. Press again to confirm.",
                this::resetPressed).glassBackground(true);
        resetBtn.destructive(resetConfirm.isArmed());
        resetBtnArmed = resetConfirm.isArmed();
        this.addRenderableWidget(resetBtn);

        // Phase C-4 pilot C: one icon action per HUD module's X badge —
        // the canonical contract (Tab/Enter/Space, focus hairline, canonical
        // hover, exactly one activation click) layered onto the existing
        // raster badge, which keeps its hand-drawn status-overlay look
        // (this screen's C-7 family ruling: plain fills, no radius in any
        // mode — the hairline is square here for the same reason). Long-
        // lived, keyed by the stable module id; registered after the two
        // floating buttons so Tab reaches them in visual reading order.
        HudModuleManager modules = AuroraClient.modules();
        if (modules != null) {
            for (HudModule m : modules.all()) {
                this.addWidget(xAction(m).interactionControl());
            }
        }
    }

    // ---- Phase C-4 pilot C: the X badge's semantic layer ----

    private final Map<String, com.aurora.client.ui.component.IconAction> xActions = new HashMap<>();

    /** The module's disable action — long-lived, keyed by the stable module id. */
    private com.aurora.client.ui.component.IconAction xAction(HudModule m) {
        return xActions.computeIfAbsent(m.id(), id ->
                new com.aurora.client.ui.component.IconAction(
                        com.aurora.client.screen.FeatureIcons.get("_action_close"),
                        com.aurora.client.ui.interaction.SemanticAction.button(
                                Component.literal("Disable " + m.displayName()),
                                () -> Component.literal("Turns off the " + m.displayName()
                                        + " HUD module (same as toggling it off in Aurora's settings)."),
                                null,
                                () -> true,
                                () -> disableViaRegistry(m)),
                        () -> 0xFFFFFFFF,
                        () -> 0xFFFFFFFF));
    }

    /** Reset Positions' press path: arm, or fire on the confirm press (design language §8.7). */
    private void resetPressed() {
        if (resetConfirm.press()) {
            resetLayouts();
            resetToast.flash("Reset HUD positions", 1500L);
        }
        syncResetButton();
    }

    /**
     * Re-labels and re-styles Reset Positions from the confirmation state —
     * called on press and once per frame, so the 3 s timeout restores the
     * label with no timer. Armed state is carried by the LABEL and the shared
     * destructive styling; never by a new tint (§6 convention 9).
     */
    private void syncResetButton() {
        if (resetBtn == null) return;
        boolean armed = resetConfirm.isArmed();
        if (armed == resetBtnArmed) return;
        resetBtnArmed = armed;
        resetBtn.setMessage(Component.literal(armed ? "Confirm reset" : "Reset Positions"));
        resetBtn.destructive(armed);
    }

    private void resetLayouts() {
        AuroraConfig.get().moduleLayouts.clear();
        HudModuleManager mgr = AuroraClient.modules();
        if (mgr == null) return;
        mgr.all().forEach(HudModule::resetLayoutToDefaults);
    }

    /**
     * Glass rollout: with a live world behind the screen, skip vanilla's
     * background sandwich — the glass buttons must sample the LIVE world.
     * The editor is only ever opened in-game, but the guard mirrors the
     * renderer's own menu-context check so the fallback keeps its vanilla
     * backdrop in any context where glass cannot engage.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (this.minecraft != null && this.minecraft.level != null) return;
        com.aurora.client.ui.component.GlassSurface.renderMenuPanorama(this,g,delta);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        syncResetButton(); // the confirm timeout restores the label with no timer
        // Themed overlay dim, alpha-scaled to this screen's original 0x60
        // strength (full-strength OVERLAY_DIM is 0x99).
        // Keep the editing canvas visible; the shared controls own their opaque faces.
        ctx.fill(0, 0, this.width, this.height,
                ThemeManager.withAlpha(ThemeManager.color(ThemeToken.OVERLAY_DIM), 0x60));

        HudModuleManager mgr = AuroraClient.modules();
        if (mgr != null) {
            applyDrag(mouseX, mouseY);
            mgr.renderAll(ctx);
            drawOutlines(ctx, mouseX, mouseY);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        // Post-reset confirmation (the manager screens' toast): 1.5 s, bottom-center.
        resetToast.render(ctx, this.font, this.width, this.height,
                AuroraTheme.RADIUS_SMALL);

        // Instructions: one compact floating pill above the hotbar instead of a
        // full-width line across the top that collided with corner modules.
        // Hidden while a drag is in progress so it never covers the target.
        if (dragMode != DragMode.NONE) return;
        int hintWidth = Math.max(1, Math.min(this.width - 32, 420));
        if (hintWidth != guideWidth || com.aurora.client.ui.util.AuroraFontRenderer.styleKey() != guideStyle) {
            guideWidth = hintWidth; guideStyle = com.aurora.client.ui.util.AuroraFontRenderer.styleKey();
            // Two balanced phrase groups; each wraps on its own only when narrow,
            // so no instruction is ever split across lines mid-phrase.
            java.util.List<String> lines = new java.util.ArrayList<>();
            // Three short pairs (council 2026-11-18): placing, then hiding, then the per-module
            // actions, instead of two dense lines of six gestures.
            for (String group : new String[]{
                    "Drag: move  ·  Corner: resize",
                    "Right-click: hide  ·  Shift+right-click: lock",
                    "Shift+click: settings  ·  X badge: disable"})
                lines.addAll(com.aurora.client.ui.util.WordWrap.lines(group, hintWidth - 16, 2, this.font::width));
            guideLines = lines;
        }
        int lineH = this.font.lineHeight + 2;
        int pillH = guideLines.size() * lineH + 8;
        int textW = 0;
        for (String line : guideLines) textW = Math.max(textW, this.font.width(line));
        int pillW = Math.min(hintWidth, textW + 16);
        int pillX = (this.width - pillW) / 2;
        // Clear of the vanilla hotbar (22) and the armor readouts that sit above it (12), plus 8
        // of air (council 2026-11-18: the pill covered the readouts).
        int pillY = this.height - 22 - 12 - 8 - pillH;
        // Fades out quickly while the cursor is over it, so modules can be placed and
        // configured in the space it covers (owner, 2026-11-18).
        boolean over = mouseX >= pillX && mouseX < pillX + pillW && mouseY >= pillY && mouseY < pillY + pillH;
        float visibility = guideFade.update(over ? 0 : 1, System.nanoTime());
        if (visibility <= 0.01f) return;
        // A container: the window frame bevel over the window tint, like tooltips.
        float radius = Math.min(AuroraTheme.RADIUS_SMALL, Math.min(pillW, pillH) * .5f);
        MaterialSurface.overlayFace(ctx, pillX, pillY, pillW, pillH, radius,
                MaterialSurface.windowCornerClearance(radius, 8, 5), visibility);
        // A near-opaque panel under the text: the window tint alone let snow, shield and grass
        // change the legend's contrast from one word to the next.
        MaterialSurface.panelBacking(ctx, pillX, pillY, pillW, pillH, radius, .85f * visibility);
        // Primary text: the pill floats over bright terrain, where secondary grey washed out.
        int text = MaterialSurface.color(ThemeToken.ON_BACKGROUND);
        int textAlpha = Math.round((text >>> 24) * visibility);
        // Below ~4 the font treats alpha as opaque; skip the text instead.
        if (textAlpha >= 8) com.aurora.client.ui.util.RenderUtil.drawCenteredLines(this.font, ctx, guideLines,
                (this.width - hintWidth) / 2, pillY + 5, hintWidth, ThemeManager.withAlpha(text, textAlpha));
    }

    @Override
    public void removed() {
        super.removed();
    }

    /** Apply the active drag (move or resize) using current MouseHandler position. */
    private void applyDrag(int mouseX, int mouseY) {
        if (active == null) return;
        switch (dragMode) {
            case MOVE -> {
                int newX = mouseX - dragOffsetX;
                int newY = mouseY - dragOffsetY;
                newX = Math.max(0, Math.min(this.width  - dragW, newX));
                newY = Math.max(0, Math.min(this.height - dragH, newY));

                // Snap-to-edge / snap-to-grid behaviour intentionally
                // removed — modules now follow the cursor pixel-for-
                // pixel for fully manual placement.

                active.moveTo(newX, newY, dragW, dragH, this.width, this.height);
            }
            case RESIZE -> {
                // New diagonal = distance from cursor to pivot. Scale ratio
                // = newDiag / origDiag * multiplied into the original scale
                // captured when the drag started.
                int dx = mouseX - resizePivotX;
                int dy = mouseY - resizePivotY;
                int newDiag = (int) Math.round(Math.sqrt(dx * dx + dy * dy));
                if (newDiag < 4) newDiag = 4;
                float ratio = newDiag / (float) Math.max(1, resizeOrigDiagonal);
                float newScale = clamp(resizeOrigScale * ratio, HudModule.MIN_SCALE, HudModule.MAX_SCALE);
                active.scale = newScale;

                // Re-derive offset so the pivot corner stays fixed.
                int sw = active.getScaledWidth();
                int sh = active.getScaledHeight();
                // resizePivotX/Y is the corner OPPOSITE to where the user grabbed.
                // Convert it back to a top-left position.
                int newTopLeftX = pivotIsRight() ? resizePivotX - sw : resizePivotX;
                int newTopLeftY = pivotIsBottom() ? resizePivotY - sh : resizePivotY;
                // C-8: a resize grows away from its fixed pivot and could
                // previously push the moving edge off-screen — the editor's
                // standing policy (MOVE clamps to the screen) now applies
                // to RESIZE too: the resulting AABB stays on-screen. When
                // the clamp bites, the pivot gives up its fixed position to
                // the screen edge (the honest resolution — the alternative,
                // shrinking the scale, would fight the user's gesture).
                newTopLeftX = Math.max(0, Math.min(this.width - sw, newTopLeftX));
                newTopLeftY = Math.max(0, Math.min(this.height - sh, newTopLeftY));
                active.moveTo(newTopLeftX, newTopLeftY, sw, sh, this.width, this.height);
            }
            default -> {}
        }
    }

    // Resize-pivot corner identification — set when grabbing started.
    private boolean grabbedTopLeft, grabbedTopRight, grabbedBottomLeft;
    private boolean pivotIsRight()  { return grabbedTopLeft   || grabbedBottomLeft; }
    private boolean pivotIsBottom() { return grabbedTopLeft   || grabbedTopRight; }

    private void drawOutlines(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        HudModuleManager mgr = AuroraClient.modules();
        if (mgr == null) return;

        // C-4 availability sweep (the manager-screen rule): every X action
        // starts the frame unavailable; the walk below re-marks the ones
        // whose badge actually paints (module enabled). A module disabled
        // mid-session cannot keep invisible focus or activation.
        for (com.aurora.client.ui.component.IconAction action : xActions.values()) {
            action.interactionControl().setAvailable(false);
        }

        // Premium-design audit (2026-10-19): only the module under the pointer
        // (or being dragged, or holding keyboard focus) shows its label,
        // corner handles and close badge. Resting modules get a quiet neutral
        // frame instead of a saturated status outline with a glow, so a busy
        // layout no longer stacks overlapping labels and badges.
        HudModule hoveredModule = dragMode == DragMode.NONE ? findAt(mouseX, mouseY) : active;
        for (HudModule m : mgr.all()) {
            if (!m.isConfigEnabled()) continue;

            int w = (m == active && dragMode == DragMode.MOVE) ? dragW : m.getScaledWidth();
            int h = (m == active && dragMode == DragMode.MOVE) ? dragH : m.getScaledHeight();
            int x = m.screenX(this.width, w);
            int y = m.screenY(this.height, h);

            com.aurora.client.ui.component.IconAction action = m.enabled ? xAction(m) : null;
            boolean emphasized = m == hoveredModule || m == active || (action != null && action.isFocused());
            int color = (m == active) ? outlineDrag()
                    : m.locked ? outlineLocked()
                      : !m.enabled ? outlineDisabled()
                        : outlineEnabled();
            // A dark keyline just outside the frame keeps it visible over white sky and snow.
            outline(ctx, x - 2, y - 2, x + w + 2, y + h + 2, emphasized ? 0x80000000 : 0x40000000);
            outline(ctx, x - 1, y - 1, x + w + 1, y + h + 1,
                    ThemeManager.withAlpha(color, emphasized ? 0xFF : 0x66));

            // Corner handles on every enabled, unlocked module (council 2026-11-18: the legend
            // says "Corner: resize", so the corners show where to grab before any hover).
            if (m.enabled && !m.locked) {
                // Clamped on-screen: a module docked to an edge keeps all four grips visible.
                int left = Math.max(3, x - 3), right = Math.min(this.width - 8, x + w - 1);
                int top = Math.max(3, y - 3), bottom = Math.min(this.height - 8, y + h - 1);
                // Resting grips are half strength, so the hovered or selected module stays clear.
                float grip = emphasized ? 1f : .5f;
                drawCornerHandle(ctx, left, top, grip);               // TL
                drawCornerHandle(ctx, right, top, grip);              // TR
                drawCornerHandle(ctx, left, bottom, grip);            // BL
                drawCornerHandle(ctx, right, bottom, grip);           // BR
            }

            // X icon (top-right INSIDE the AABB). Only on enabled modules — clicking
            // an X on a disabled module would be a no-op. C-4 pilot C: the badge
            // keeps the canonical channels — availability tied to the module being
            // enabled (keyboard focus can still reach it), pointer-only
            // symmetric-140 hover, and the square focus hairline. It paints on the
            // emphasized module only; any pointer that can click it hovers it.
            if (action != null) {
                int xIconX = x + w - X_ICON_SIZE - 2;
                int xIconY = y + 2;
                action.interactionControl().setAvailable(true);
                action.syncChannels(xIconX, xIconY, X_ICON_SIZE, X_ICON_SIZE, mouseX, mouseY);
                if (emphasized) drawXIcon(ctx, xIconX, xIconY, action.hoverT());
                if (action.isFocused()) {
                    int hair = ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ACCENT), 0x99);
                    ctx.fill(xIconX - 1, xIconY - 1, xIconX, xIconY + X_ICON_SIZE, hair);
                    ctx.fill(xIconX + X_ICON_SIZE, xIconY - 1, xIconX + X_ICON_SIZE + 1, xIconY + X_ICON_SIZE, hair);
                    ctx.fill(xIconX, xIconY - 1, xIconX + X_ICON_SIZE, xIconY, hair);
                    ctx.fill(xIconX, xIconY + X_ICON_SIZE, xIconX + X_ICON_SIZE, xIconY + X_ICON_SIZE + 1, hair);
                }
            }

            // Lock icon (top-left INSIDE the AABB) when locked — state, so always shown.
            if (m.locked) {
                drawLockIcon(ctx, x + 2, y + 2);
            }

            // A module with nothing to show names itself in its frame (council 2026-11-18: an empty
            // Potion Effects frame read as a stray selection rectangle).
            if (m.showsNothing()) {
                String name = m.displayName();
                int nameW = this.font.width(name);
                int nx = x + (w - nameW) / 2, ny = y + (h - this.font.lineHeight) / 2 + 1;
                // On the same plate as the hover label, so it reads over bright sky and snow.
                ctx.fill(nx - 4, ny - 3, nx + nameW + 4, ny + this.font.lineHeight + 2, labelBg());
                ctx.text(this.font, name, nx, ny, MaterialSurface.color(ThemeToken.ON_BACKGROUND_SECONDARY), false);
            }

            if (!emphasized) continue;
            // Label below (above when there is no room), clamped on-screen.
            String suffix = (!m.enabled ? " (hidden)" : "")
                    + (m.locked ? " (locked)" : "")
                    + (Math.abs(m.scale - 1f) > 0.01f ? String.format(" %.0f%%", m.scale * 100) : "");
            String label = m.displayName() + suffix;
            int labelW = this.font.width(label) + 8;
            int labelH = this.font.lineHeight + 5;
            int labelX = Math.max(2, Math.min(this.width - labelW - 2, x));
            int labelY = y + h + 3;
            if (labelY + labelH > this.height - 2) {
                labelY = y - labelH - 3;
            }
            ctx.fill(labelX, labelY, labelX + labelW, labelY + labelH, labelBg());
            ctx.text(this.font, label, labelX + 4, labelY + 3, MaterialSurface.color(ThemeToken.ON_BACKGROUND), false);
        }
    }

    private static void drawCornerHandle(GuiGraphicsExtractor ctx, int cx, int cy, float strength) {
        // A dark 1px ring keeps the white handle legible over bright terrain/sky.
        // 5 px light centre in a 1 px dark ring (council 2026-11-18: 4 px read as a speck).
        ctx.fill(cx - 1, cy - 1, cx + 6, cy + 6, ThemeManager.withAlpha(0xFF000000, Math.round(0xB0 * strength)));
        ctx.fill(cx, cy, cx + 5, cy + 5, ThemeManager.withAlpha(CORNER_HANDLE, Math.round((CORNER_HANDLE >>> 24) * strength)));
    }

    private void drawXIcon(GuiGraphicsExtractor ctx, int x, int y, float hoverT) {
        // Filled background square + diagonal cross "X" on top. C-4: the
        // canonical hover eases the background's alpha (0xC0 → 0xF0) —
        // pointer-only, symmetric 140 ms via the icon action.
        ctx.fill(x, y, x + X_ICON_SIZE, y + X_ICON_SIZE,
                ((int) (0xC0 + (0xF0 - 0xC0) * hoverT) << 24)
                        | (xIconBg() & 0x00FFFFFF));
        // Manual diagonal lines via 1-px fills.
        for (int i = 1; i < X_ICON_SIZE - 1; i++) {
            ctx.fill(x + i, y + i, x + i + 1, y + i + 1, X_ICON_FG);
            ctx.fill(x + (X_ICON_SIZE - 1 - i), y + i, x + (X_ICON_SIZE - i), y + i + 1, X_ICON_FG);
        }
    }

    private void drawLockIcon(GuiGraphicsExtractor ctx, int x, int y) {
        // Crude lock icon: filled body + shackle.
        ctx.fill(x, y, x + LOCK_ICON_SIZE, y + LOCK_ICON_SIZE, lockIconBg());
        // Body
        ctx.fill(x + 2, y + 4, x + LOCK_ICON_SIZE - 2, y + LOCK_ICON_SIZE - 1, lockIconFg());
        // Shackle (top arc, 2 px)
        ctx.fill(x + 3, y + 1, x + LOCK_ICON_SIZE - 3, y + 2, lockIconFg());
        ctx.fill(x + 2, y + 2, x + 3, y + 4, lockIconFg());
        ctx.fill(x + LOCK_ICON_SIZE - 3, y + 2, x + LOCK_ICON_SIZE - 2, y + 4, lockIconFg());
    }

    private static void outline(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
        ctx.fill(x1, y1, x2, y1 + 1, color);
        ctx.fill(x1, y2 - 1, x2, y2, color);
        ctx.fill(x1, y1, x1 + 1, y2, color);
        ctx.fill(x2 - 1, y1, x2, y2, color);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent _ev, boolean _doubleClicked) {
        double mouseX = _ev.x(); double mouseY = _ev.y(); int button = _ev.button();
        // An armed Reset Positions disarms on any click that is not its own
        // (design language §8.7) — before dispatch, so the button's own press
        // below is the only click that can arm or fire it.
        if (resetConfirm.isArmed() && !resetBtn.isMouseOver(mouseX, mouseY)) {
            resetConfirm.disarm();
            syncResetButton();
        }
        if (super.mouseClicked(_ev, _doubleClicked)) return true;

        HudModuleManager mgr = AuroraClient.modules();
        if (mgr == null) return false;

        int mx = (int) mouseX;
        int my = (int) mouseY;

        HudModule hit = findAt(mx, my);
        if (hit == null) return false;

        boolean shift = Minecraft.getInstance().options != null
                && (Minecraft.getInstance().hasShiftDown());

        // Compute the module's screen rectangle (using SCALED dims).
        int sw = hit.getScaledWidth();
        int sh = hit.getScaledHeight();
        int x = hit.screenX(this.width, sw);
        int y = hit.screenY(this.height, sh);

        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
            // Right-click: shift = toggle lock; otherwise hide/show
            if (shift) {
                hit.locked = !hit.locked;
            } else {
                hit.enabled = !hit.enabled;
            }
            return true;
        }

        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            // Shift+left = open settings detail, regardless of icon hits.
            if (shift) {
                openSettingsFor(hit);
                return true;
            }

            // X icon (only present on enabled modules). C-4 pilot C: routes
            // through the semantic action (exactly-once + the one click);
            // the direct call stays as the pre-materialization fallback.
            if (hit.enabled) {
                int xIconX = x + sw - X_ICON_SIZE - 2;
                int xIconY = y + 2;
                if (Widget.inBounds(mx, my, xIconX, xIconY, X_ICON_SIZE, X_ICON_SIZE)) {
                    if (!xAction(hit).clicked(mx, my, button)) {
                        disableViaRegistry(hit);
                    }
                    return true;
                }
            }

            // Locked modules ignore drag/resize.
            if (hit.locked) return true;

            // Corner-handle hit detection.
            grabbedTopLeft = grabbedTopRight = grabbedBottomLeft = false;
            boolean grabbedBottomRight = false;
            int cx, cy;

            cx = x; cy = y;
            if (within(mx, my, cx, cy, CORNER_HIT_RADIUS)) grabbedTopLeft = true;
            cx = x + sw; cy = y;
            if (within(mx, my, cx, cy, CORNER_HIT_RADIUS)) grabbedTopRight = true;
            cx = x; cy = y + sh;
            if (within(mx, my, cx, cy, CORNER_HIT_RADIUS)) grabbedBottomLeft = true;
            cx = x + sw; cy = y + sh;
            if (within(mx, my, cx, cy, CORNER_HIT_RADIUS)) grabbedBottomRight = true;

            if (grabbedTopLeft || grabbedTopRight || grabbedBottomLeft || grabbedBottomRight) {
                // Start a resize. Pivot = the corner OPPOSITE the grabbed one.
                if      (grabbedTopLeft)     { resizePivotX = x + sw; resizePivotY = y + sh; }
                else if (grabbedTopRight)    { resizePivotX = x;      resizePivotY = y + sh; }
                else if (grabbedBottomLeft)  { resizePivotX = x + sw; resizePivotY = y; }
                else /* bottomRight */       { resizePivotX = x;      resizePivotY = y; }
                int ddx = mx - resizePivotX;
                int ddy = my - resizePivotY;
                resizeOrigDiagonal = Math.max(1, (int) Math.round(Math.sqrt(ddx * ddx + ddy * ddy)));
                resizeOrigScale = hit.scale;
                active = hit;
                dragMode = DragMode.RESIZE;
                return true;
            }

            // Otherwise — a body drag (move).
            active = hit;
            dragMode = DragMode.MOVE;
            dragW = sw;
            dragH = sh;
            dragOffsetX = mx - x;
            dragOffsetY = my - y;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent _ev) {
        double mouseX = _ev.x(); double mouseY = _ev.y(); int button = _ev.button();
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && active != null) {
            // Snap-on-release intentionally removed — the module's
            // raw drag position is preserved as-is. The anchor is
            // re-resolved (without a grid snap) so the relative offset
            // remains consistent under future window-size changes.
            if (dragMode == DragMode.MOVE) reanchorWithoutSnap(active);
            active = null;
            dragMode = DragMode.NONE;
            return true;
        }
        return super.mouseReleased(_ev);
    }

    /**
     * Reassigns {@code module}'s anchor to whichever screen-quadrant
     * anchor is closest to its current pixel position, then recomputes
     * its offsets from that anchor — without any grid snapping. Keeps
     * the module visually pinned to the chosen screen region across
     * resolution changes while still respecting the user's exact pixel
     * placement.
     */
    private void reanchorWithoutSnap(HudModule module) {
        int w = module.getScaledWidth();
        int h = module.getScaledHeight();
        int x = module.screenX(this.width, w);
        int y = module.screenY(this.height, h);
        module.anchor  = HudAnchor.nearest(x, y, w, h, this.width, this.height);
        module.moveTo(x, y, w, h, this.width, this.height);
    }

    private HudModule findAt(int x, int y) {
        HudModuleManager mgr = AuroraClient.modules();
        if (mgr == null) return null;
        HudModule found = null;
        for (HudModule m : mgr.all()) {
            if (!m.isConfigEnabled()) continue;
            int w = m.getScaledWidth();
            int h = m.getScaledHeight();
            int mx = m.screenX(this.width, w);
            int my = m.screenY(this.height, h);
            // Hit testing INCLUDES a CORNER_HIT_RADIUS halo so users can grab
            // corner handles that sit slightly outside the AABB.
            if (x >= mx - CORNER_HIT_RADIUS && x < mx + w + CORNER_HIT_RADIUS
                    && y >= my - CORNER_HIT_RADIUS && y < my + h + CORNER_HIT_RADIUS) {
                // Keep overwriting: the render loop draws mgr.all() in this
                // same order (later entries overdraw earlier ones), so the
                // LAST hit is the visually topmost module — exactly what a
                // click should select.
                found = m;
            }
        }
        return found;
    }

    private static boolean within(int mx, int my, int cx, int cy, int radius) {
        return mx >= cx - radius && mx <= cx + radius
                && my >= cy - radius && my <= cy + radius;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * Find the FeatureMetadata matching this module's {@link HudModule#featureRegistryId}
     * and toggle its enable setter to false. Mirrors what the modules-grid X-tile
     * would do.
     *
     * <p>C-8 persistence ruling: the grid path ({@code Module.setEnabled})
     * saves the config IMMEDIATELY on every toggle, and a HUD-editor
     * disable is expected to persist like every other enable/disable path —
     * so the registry write saves too. (The editor previously relied on a
     * later full save — close, Aurora Settings, or the shutdown hook —
     * which lost the disable on a crash before any of those ran.) The
     * fallback editor-local flag flip stays session-only by design (no
     * config field to persist when the registry lookup misses).
     */
    private void disableViaRegistry(HudModule m) {
        String regId = m.featureRegistryId();
        for (FeatureMetadata fm : FeatureRegistry.modules()) {
            if (regId.equals(fm.id)) {
                fm.setEnabled(false);
                AuroraConfig.save();
                return;
            }
        }
        // Fallback: at least flip the editor-local enabled flag.
        m.enabled = false;
    }

    /** Open the Aurora detail screen for this module's feature, if it has settings. */
    private void openSettingsFor(HudModule m) {
        String regId = m.featureRegistryId();
        for (FeatureMetadata fm : FeatureRegistry.modules()) {
            if (regId.equals(fm.id) && !fm.settings.isEmpty()) {
                AuroraClient.captureLayouts(AuroraClient.modules());
                AuroraConfig.save();
                if (this.minecraft != null) {
                    com.aurora.client.adapter.NativeUi.setScreen(this.minecraft, FeatureDetailScreen.destination(this, fm));
                }
                return;
            }
        }
    }

    /**
     * Escape unwinds one layer at a time (design language §8.7): while Reset
     * Positions is armed, the first Escape disarms and CONSUMES the press;
     * the next Escape closes the editor (saves the layout) as before. The
     * only key handler this screen owns — no X-key or similar exists.
     */
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent _kev) {
        if (_kev.key() == InputConstants.KEY_ESCAPE && resetConfirm.isArmed()) {
            resetConfirm.disarm();
            syncResetButton();
            return true;
        }
        return super.keyPressed(_kev);
    }

    @Override
    public void onClose() {
        AuroraClient.captureLayouts(AuroraClient.modules());
        AuroraConfig.save();
        Minecraft client = Minecraft.getInstance();
        if (client != null) com.aurora.client.adapter.NativeUi.setScreen(client, null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
