package com.aurora.client.screen;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.HudAnchor;
import com.aurora.client.hud.module.HudModule;
import com.aurora.client.hud.module.HudModuleManager;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.ThemedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
 *   <li><b>Reset Positions</b> — clears saved layouts.</li>
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
    private int outlineEnabled()  { return ThemeManager.color(ThemeToken.SEMANTIC_SUCCESS); }
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
    private int labelBg()         { return (0xA0 << 24) | (ThemeManager.color(ThemeToken.OVERLAY_DIM) & 0x00FFFFFF); }
    /** Alignment grid — ON_BACKGROUND RGB at a faint 0x20 alpha (mode-aware). */
    private int grid()            { return (0x20 << 24) | (ThemeManager.color(ThemeToken.ON_BACKGROUND) & 0x00FFFFFF); }

    private static final int CORNER_HIT_RADIUS = 6;
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

    @Override
    protected void init() {
        int w = 120;
        int h = 20;

        this.addRenderableWidget(new ButtonWidget(
                (this.width - w) / 2, (this.height - h) / 2, w, h,
                Component.literal("Aurora Settings"),
                () -> {
                    AuroraClient.captureLayouts(AuroraClient.modules());
                    AuroraConfig.save();
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(AuroraScreen.create());
                    }
                }).glassBackground(true));

        this.addRenderableWidget(new ButtonWidget(
                this.width - w - 8, 8, w, h,
                Component.literal("Reset Positions"),
                this::resetLayouts).glassBackground(true));
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
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (this.minecraft != null && this.minecraft.level != null) return;
        super.renderBackground(g, mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // Themed overlay dim, alpha-scaled to this screen's original 0x60
        // strength (full-strength OVERLAY_DIM is 0x99).
        ctx.fill(0, 0, this.width, this.height,
                ThemeManager.withAlpha(ThemeManager.color(ThemeToken.OVERLAY_DIM), 0x60));

        HudModuleManager mgr = AuroraClient.modules();
        if (mgr != null) {
            applyDrag(mouseX, mouseY);
            mgr.renderAll(ctx);
            drawOutlines(ctx, mouseX, mouseY);
        }

        super.render(ctx, mouseX, mouseY, delta);

        ctx.pose().pushMatrix();
        ctx.pose().translate(8.0f, 8.0f);
        ctx.pose().scale(0.75f, 0.75f);
        ctx.drawString(this.font,
                "Drag body: move  |  Drag corner: resize  |  RClick: hide  |  Shift+RClick: lock  |  Shift+LClick: settings  |  X: disable",
                0, 0, ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_OVERLAY), 0xDD), false);
        ctx.pose().popMatrix();
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

                active.offsetX = newX - active.anchor.applyX(this.width,  dragW);
                active.offsetY = newY - active.anchor.applyY(this.height, dragH);
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
                active.offsetX = newTopLeftX - active.anchor.applyX(this.width,  sw);
                active.offsetY = newTopLeftY - active.anchor.applyY(this.height, sh);
            }
            default -> {}
        }
    }

    // Resize-pivot corner identification — set when grabbing started.
    private boolean grabbedTopLeft, grabbedTopRight, grabbedBottomLeft;
    private boolean pivotIsRight()  { return grabbedTopLeft   || grabbedBottomLeft; }
    private boolean pivotIsBottom() { return grabbedTopLeft   || grabbedTopRight; }

    private void drawGrid(GuiGraphics ctx) {
        for (int x = 0; x < this.width; x += 32)
            ctx.fill(x, 0, x + 1, this.height, grid());
        for (int y = 0; y < this.height; y += 32)
            ctx.fill(0, y, this.width, y + 1, grid());
    }

    private void drawOutlines(GuiGraphics ctx, int mouseX, int mouseY) {
        HudModuleManager mgr = AuroraClient.modules();
        if (mgr == null) return;

        for (HudModule m : mgr.all()) {
            if (!m.isConfigEnabled()) continue;

            int w = (m == active && dragMode == DragMode.MOVE) ? dragW : m.getScaledWidth();
            int h = (m == active && dragMode == DragMode.MOVE) ? dragH : m.getScaledHeight();
            int x = m.anchor.applyX(this.width,  w) + m.offsetX;
            int y = m.anchor.applyY(this.height, h) + m.offsetY;

            int color = (m == active) ? outlineDrag()
                    : m.locked ? outlineLocked()
                      : m.enabled ? outlineEnabled()
                        : outlineDisabled();
            
            // Draw slight glowing effect around the outline for depth
            int rgb = color & 0x00FFFFFF;
            for (int i = 1; i <= 3; i++) {
                int glowAlpha = Math.max(0, 50 - i * 15);
                outline(ctx, x - 1 - i, y - 1 - i, x + w + 1 + i, y + h + 1 + i, (glowAlpha << 24) | rgb);
            }
            
            outline(ctx, x - 1, y - 1, x + w + 1, y + h + 1, color);

            // Corner handles (only on enabled, unlocked modules).
            if (m.enabled && !m.locked) {
                drawCornerHandle(ctx, x - 2, y - 2);                  // TL
                drawCornerHandle(ctx, x + w - 2, y - 2);              // TR
                drawCornerHandle(ctx, x - 2, y + h - 2);              // BL
                drawCornerHandle(ctx, x + w - 2, y + h - 2);          // BR
            }

            // X icon (top-right INSIDE the AABB). Only on enabled modules — clicking
            // an X on a disabled module would be a no-op.
            if (m.enabled) {
                int xIconX = x + w - X_ICON_SIZE - 2;
                int xIconY = y + 2;
                drawXIcon(ctx, xIconX, xIconY);
            }

            // Lock icon (top-left INSIDE the AABB) when locked.
            if (m.locked) {
                drawLockIcon(ctx, x + 2, y + 2);
            }

            // Label below.
            String suffix = (!m.enabled ? " (off)" : "")
                    + (m.locked ? " (locked)" : "")
                    + (Math.abs(m.scale - 1f) > 0.01f ? String.format(" %.0f%%", m.scale * 100) : "");
            String label = m.displayName() + suffix;
            int labelW = this.font.width(label) + 4;
            int labelX = x;
            int labelY = y + h + 2;
            if (labelY + this.font.lineHeight + 2 > this.height) {
                labelY = y - this.font.lineHeight - 4;
            }
            ctx.fill(labelX, labelY, labelX + labelW, labelY + this.font.lineHeight + 2, labelBg());
            ctx.drawString(this.font, label, labelX + 2, labelY + 2, 0xFFFFFFFF, false);
        }
    }

    private static void drawCornerHandle(GuiGraphics ctx, int cx, int cy) {
        ctx.fill(cx, cy, cx + 4, cy + 4, CORNER_HANDLE);
    }

    private void drawXIcon(GuiGraphics ctx, int x, int y) {
        // Filled background square + diagonal cross "X" on top.
        ctx.fill(x, y, x + X_ICON_SIZE, y + X_ICON_SIZE, xIconBg());
        // Manual diagonal lines via 1-px fills.
        for (int i = 1; i < X_ICON_SIZE - 1; i++) {
            ctx.fill(x + i, y + i, x + i + 1, y + i + 1, X_ICON_FG);
            ctx.fill(x + (X_ICON_SIZE - 1 - i), y + i, x + (X_ICON_SIZE - i), y + i + 1, X_ICON_FG);
        }
    }

    private void drawLockIcon(GuiGraphics ctx, int x, int y) {
        // Crude lock icon: filled body + shackle.
        ctx.fill(x, y, x + LOCK_ICON_SIZE, y + LOCK_ICON_SIZE, lockIconBg());
        // Body
        ctx.fill(x + 2, y + 4, x + LOCK_ICON_SIZE - 2, y + LOCK_ICON_SIZE - 1, lockIconFg());
        // Shackle (top arc, 2 px)
        ctx.fill(x + 3, y + 1, x + LOCK_ICON_SIZE - 3, y + 2, lockIconFg());
        ctx.fill(x + 2, y + 2, x + 3, y + 4, lockIconFg());
        ctx.fill(x + LOCK_ICON_SIZE - 3, y + 2, x + LOCK_ICON_SIZE - 2, y + 4, lockIconFg());
    }

    private static void outline(GuiGraphics ctx, int x1, int y1, int x2, int y2, int color) {
        ctx.fill(x1, y1, x2, y1 + 1, color);
        ctx.fill(x1, y2 - 1, x2, y2, color);
        ctx.fill(x1, y1, x1 + 1, y2, color);
        ctx.fill(x2 - 1, y1, x2, y2, color);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent _ev, boolean _doubleClicked) {
        double mouseX = _ev.x(); double mouseY = _ev.y(); int button = _ev.button();
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
        int x = hit.anchor.applyX(this.width,  sw) + hit.offsetX;
        int y = hit.anchor.applyY(this.height, sh) + hit.offsetY;

        if (button == 1) {
            // Right-click: shift = toggle lock; otherwise hide/show
            if (shift) {
                hit.locked = !hit.locked;
            } else {
                hit.enabled = !hit.enabled;
            }
            return true;
        }

        if (button == 0) {
            // Shift+left = open settings detail, regardless of icon hits.
            if (shift) {
                openSettingsFor(hit);
                return true;
            }

            // X icon (only present on enabled modules).
            if (hit.enabled) {
                int xIconX = x + sw - X_ICON_SIZE - 2;
                int xIconY = y + 2;
                if (mx >= xIconX && mx < xIconX + X_ICON_SIZE
                        && my >= xIconY && my < xIconY + X_ICON_SIZE) {
                    disableViaRegistry(hit);
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
        if (button == 0 && active != null) {
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
        int x = module.anchor.applyX(this.width,  w) + module.offsetX;
        int y = module.anchor.applyY(this.height, h) + module.offsetY;
        module.anchor  = HudAnchor.nearest(x, y, w, h, this.width, this.height);
        module.offsetX = x - module.anchor.applyX(this.width,  w);
        module.offsetY = y - module.anchor.applyY(this.height, h);
    }

    private HudModule findAt(int x, int y) {
        HudModuleManager mgr = AuroraClient.modules();
        if (mgr == null) return null;
        HudModule found = null;
        for (HudModule m : mgr.all()) {
            if (!m.isConfigEnabled()) continue;
            int w = m.getScaledWidth();
            int h = m.getScaledHeight();
            int mx = m.anchor.applyX(this.width,  w) + m.offsetX;
            int my = m.anchor.applyY(this.height, h) + m.offsetY;
            // Hit testing INCLUDES a CORNER_HIT_RADIUS halo so users can grab
            // corner handles that sit slightly outside the AABB.
            if (x >= mx - CORNER_HIT_RADIUS && x < mx + w + CORNER_HIT_RADIUS
                    && y >= my - CORNER_HIT_RADIUS && y < my + h + CORNER_HIT_RADIUS) {
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
     */
    private void disableViaRegistry(HudModule m) {
        String regId = m.featureRegistryId();
        for (FeatureMetadata fm : FeatureRegistry.modules()) {
            if (regId.equals(fm.id)) {
                fm.setEnabled(false);
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
                    this.minecraft.setScreen(new FeatureDetailScreen(this, fm));
                }
                return;
            }
        }
    }

    @Override
    public void onClose() {
        AuroraClient.captureLayouts(AuroraClient.modules());
        AuroraConfig.save();
        Minecraft client = Minecraft.getInstance();
        if (client != null) client.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
