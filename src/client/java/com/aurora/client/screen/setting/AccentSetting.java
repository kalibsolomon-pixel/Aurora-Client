package com.aurora.client.screen.setting;
import com.aurora.client.ui.util.AuroraFontRenderer;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.theme.PaletteEngine;
import com.aurora.client.theme.ThemePresets;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * COSMIC-style accent picker (Stage 3): a grid of the nine
 * {@link ThemePresets} swatches plus a distinct "Custom" slot. The active
 * accent wears a selection ring. Opening the Custom slot embeds the mod's
 * existing {@link ColorSetting} picker — <b>reused literally, unmodified in
 * style/layout</b> — exactly as the shield damage-tint feature edits its
 * colors; only the wiring differs (accent setter + throttled reload).
 *
 * <p>Every applied value goes through the single standard path: write the
 * definition field, one {@code ThemeManager.reload()}, then
 * {@code AuroraConfig.save()} at commit points. Picker drags apply live but
 * throttled to {@link #LIVE_RELOAD_MIN_MS} (the final value commits on
 * release).
 */
public class AccentSetting extends FeatureSetting {

    private static final int LABEL_ROW_H = 28;
    private static final int SWATCH_H = 24;
    private static final int SWATCH_GAP = 6;
    private static final int GRID_PAD_X = 12;
    private static final int GRID_GAP_Y = 6;
    /** 9 presets + the Custom slot = 10 cells, 5 per row. */
    private static final int PER_ROW = 5;
    private static final int EDITOR_TOP_GAP = 8;

    /** Minimum spacing between live (mid-drag) reloads — same budget as the opacity slider. */
    private static final long LIVE_RELOAD_MIN_MS = 100L;

    private final IntSupplier getter;
    private final IntConsumer setter;

    private boolean editorOpen;

    /**
     * The embedded damage-tint-style picker. A plain, unmodified
     * {@link ColorSetting} instance — recreated fresh each time the Custom
     * slot is opened so it always starts collapsed exactly as it does in
     * its original feature rows.
     */
    private ColorSetting customPicker;

    /** Deferred live-apply value while the throttle window rides out. */
    private int pendingRgb;
    private boolean liveApplyPending = false;
    private long lastLiveReloadMs = 0L;
    private long nextLiveReloadMs = 0L;

    private int lastX, lastY, lastW;

    public AccentSetting(String label, IntSupplier getter, IntConsumer setter) {
        super(label);
        this.getter = getter;
        this.setter = setter;
        // Auto-open the editor when the saved accent is already custom, so
        // the picker is right there on revisit.
        this.editorOpen = ThemePresets.matching(getter.getAsInt()) == null;
        this.customPicker = createCustomPicker();
    }

    /**
     * A fresh, unmodified {@link ColorSetting} wired to the accent through
     * the standard throttled apply path (its own commit/save behavior is
     * left exactly as-is).
     */
    private ColorSetting createCustomPicker() {
        return new ColorSetting("Custom Accent",
                this::currentAccent,
                this::applyAccentLive);
    }

    @Override public AccentSetting description(String desc) { super.description(desc); return this; }
    @Override public AccentSetting description(java.util.function.Supplier<String> desc) { super.description(desc); return this; }

    // ------------------------------------------------------------------
    //  Layout
    // ------------------------------------------------------------------

    /** Row top of the swatch grid, relative to the row top. */
    private static int gridTopRel() { return LABEL_ROW_H; }

    /** Row top of the custom editor (the embedded picker), relative to the row top. */
    private static int editorTopRel() { return LABEL_ROW_H + 2 * SWATCH_H + GRID_GAP_Y + EDITOR_TOP_GAP; }

    @Override public int baseHeight() {
        int h = LABEL_ROW_H + 2 * SWATCH_H + GRID_GAP_Y;
        if (editorOpen) {
            h += EDITOR_TOP_GAP + customPicker.height() + 2;
        }
        return h;
    }

    @Override public int height() { return baseHeight(); }

    private int swatchW(int rowWidth) {
        return (rowWidth - GRID_PAD_X * 2 - SWATCH_GAP * (PER_ROW - 1)) / PER_ROW;
    }

    private int cellX(int index, int rowWidth) {
        return lastX + GRID_PAD_X + (index % PER_ROW) * (swatchW(rowWidth) + SWATCH_GAP);
    }

    private int cellY(int index) {
        return lastY + gridTopRel() + (index / PER_ROW) * (SWATCH_H + GRID_GAP_Y);
    }

    // ------------------------------------------------------------------
    //  Apply paths — all funnel through the single setter (one reload per
    //  genuine change); save happens only at commit points. The throttle
    //  logic lives here so the shared picker stays persistence-agnostic.
    // ------------------------------------------------------------------

    private int currentAccent() {
        return getter.getAsInt();
    }

    /** Apply a full RGB now if the throttle allows; otherwise defer to the render flush. */
    private void applyAccentLive(int rgb) {
        int v = rgb & 0x00FFFFFF;
        if (v == (currentAccent() & 0x00FFFFFF)) {
            return; // no genuine change — must not fire a reload
        }
        long now = System.currentTimeMillis();
        if (now - lastLiveReloadMs >= LIVE_RELOAD_MIN_MS) {
            setter.accept(0xFF000000 | v);
            lastLiveReloadMs = now;
            liveApplyPending = false;
        } else {
            pendingRgb = v;
            liveApplyPending = true;
            nextLiveReloadMs = lastLiveReloadMs + LIVE_RELOAD_MIN_MS;
        }
    }

    /** Flush a deferred live apply if it still differs from the live config. */
    private void flushPendingLive() {
        if (liveApplyPending) {
            int v = 0xFF000000 | (pendingRgb & 0x00FFFFFF);
            if (v != (currentAccent() | 0xFF000000)) {
                setter.accept(v);
            }
            lastLiveReloadMs = System.currentTimeMillis();
            liveApplyPending = false;
        }
    }

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        renderShapes(ctx, x, y, width, mouseX, mouseY);
        renderOverlay(ctx, x, y, width, mouseX, mouseY);
    }

    @Override
    public void renderShapes(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastX = x;
        lastY = y;
        lastW = width;
        // The nine preset swatch fills are constant → cacheable static layer.
        float swR = Math.min(SWATCH_H / 2.0f, AuroraTheme.RADIUS_SMALL);
        for (int i = 0; i < ThemePresets.ALL.size(); i++) {
            ThemePresets.Entry e = ThemePresets.ALL.get(i);
            RenderUtil.drawRoundedRectAA(ctx, cellX(i, width), cellY(i), swatchW(width), SWATCH_H, swR, e.argb);
        }
    }

    @Override
    public void renderOverlay(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastX = x;
        lastY = y;
        lastW = width;

        Font tr = Minecraft.getInstance().font;
        boolean disabled = isDisabled();

        // Flush a deferred live apply once the throttle interval elapsed.
        if (liveApplyPending && System.currentTimeMillis() >= nextLiveReloadMs) {
            flushPendingLive();
        }

        renderLabelWithTooltip(ctx, label, x + GRID_PAD_X, y + (LABEL_ROW_H - tr.lineHeight) / 2,
                AuroraTheme.IOS_LABEL, mouseX, mouseY, disabled);

        int accent = currentAccent();
        ThemePresets.Entry preset = ThemePresets.matching(accent);

        float swR = Math.min(SWATCH_H / 2.0f, AuroraTheme.RADIUS_SMALL);

        // Preset selection ring + hover feedback (live, hover/state-dependent).
        for (int i = 0; i < ThemePresets.ALL.size(); i++) {
            ThemePresets.Entry e = ThemePresets.ALL.get(i);
            int sx = cellX(i, width);
            int sy = cellY(i);
            int sw = swatchW(width);
            boolean selected = preset == e;
            boolean hovered = hoverAt(mouseX, mouseY, sx, sy, sw);
            if (selected) {
                RenderUtil.drawRoundedOutlineAA(ctx, sx - 2, sy - 2, sw + 4, SWATCH_H + 4,
                        swR + 2, 1.5f, AuroraTheme.IOS_LABEL);
            } else if (hovered) {
                RenderUtil.drawRoundedOutlineAA(ctx, sx - 1, sy - 1, sw + 2, SWATCH_H + 2,
                        swR + 1, 1.0f, AuroraTheme.WINDOW_OUTLINE);
            }
        }

        // Custom slot — fill is the live accent value, so it stays live.
        int ci = ThemePresets.ALL.size();
        int cx = cellX(ci, width);
        int cy = cellY(ci);
        int cw = swatchW(width);
        boolean customActive = preset == null;
        int customFill = customActive ? accent : AuroraTheme.IOS_TERTIARY_BG;
        int draw = disabled ? (customFill & 0x55FFFFFF) : (customFill | 0xFF000000);
        RenderUtil.drawRoundedRectAA(ctx, cx, cy, cw, SWATCH_H, swR, draw);
        if (customActive) {
            RenderUtil.drawRoundedOutlineAA(ctx, cx - 2, cy - 2, cw + 4, SWATCH_H + 4,
                    swR + 2, 1.5f, AuroraTheme.IOS_LABEL);
        } else {
            RenderUtil.drawRoundedOutlineAA(ctx, cx, cy, cw, SWATCH_H, swR, 1.0f, AuroraTheme.BORDER_OFF);
            if (hoverAt(mouseX, mouseY, cx, cy, cw)) {
                RenderUtil.drawRoundedOutlineAA(ctx, cx - 1, cy - 1, cw + 2, SWATCH_H + 2,
                        swR + 1, 1.0f, AuroraTheme.WINDOW_OUTLINE);
            }
        }
        int customText = customActive
                ? PaletteEngine.pickOnColor(accent, 0f)
                : AuroraTheme.IOS_SECONDARY_LABEL;
        AuroraFontRenderer.drawCentered(ctx, tr, "Custom", cx + cw / 2,
                cy + (SWATCH_H - tr.lineHeight) / 2, disabled ? AuroraTheme.TEXT_DIM : customText);

        if (!editorOpen) return;
        // The embedded, unmodified damage-tint-style picker — rendered as a
        // normal settings row, exactly as it appears in its own feature.
        customPicker.render(ctx, x, y + editorTopRel(), width, mouseX, mouseY);
    }

    private static boolean hoverAt(double mouseX, double mouseY, int x, int y, int w) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + SWATCH_H;
    }

    // ------------------------------------------------------------------
    //  Interaction — embedded picker first (when open), then the swatch grid
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int rowX, int rowY, int rowWidth) {
        if (button != 0) return false;
        boolean disabled = isDisabled();

        // 1) The embedded, unmodified picker (label row / pad / strips).
        if (editorOpen && !disabled) {
            if (customPicker.mouseClicked(mouseX, mouseY, button,
                    lastX, lastY + editorTopRel(), lastW)) {
                return true;
            }
        }

        // 2) Swatch grid (presets + Custom slot).
        if (!disabled && mouseY >= lastY + gridTopRel() && mouseY < cellY(PER_ROW) + SWATCH_H) {
            int sw = swatchW(lastW);
            for (int i = 0; i <= ThemePresets.ALL.size(); i++) {
                int sx = cellX(i, lastW);
                int sy = cellY(i);
                if (mouseX >= sx && mouseX < sx + sw && mouseY >= sy && mouseY < sy + SWATCH_H) {
                    if (i < ThemePresets.ALL.size()) {
                        ThemePresets.Entry e = ThemePresets.ALL.get(i);
                        if ((e.argb | 0xFF000000) != (currentAccent() | 0xFF000000)) {
                            setter.accept(e.argb);      // one reload per genuine change
                            AuroraConfig.save();
                        }
                        editorOpen = false;
                    } else {
                        editorOpen = true;             // opening the editor is not a change
                        customPicker = createCustomPicker();   // starts collapsed, as in damage-tint
                    }
                    return true;
                }
            }
        }

        // Click inside our row but outside any control: drop focus.
        if (mouseX >= lastX && mouseX < lastX + lastW
                && mouseY >= lastY && mouseY < lastY + height()) {
            releaseFocus();
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy,
                                int rowX, int rowY, int rowWidth) {
        if (!editorOpen) return false;
        return customPicker.mouseDragged(mouseX, mouseY, button, dx, dy, rowX, rowY, rowWidth);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!editorOpen) return false;
        boolean handled = customPicker.mouseReleased(mouseX, mouseY, button);
        if (handled) {
            // The picker persists on its own release; flush anything the
            // throttle deferred so the saved value is the final one.
            flushPendingLive();
            AuroraConfig.save();
        }
        return handled;
    }

    @Override
    public void onDetailScreenClose() {
        // Commit any dangling throttle deferral so nothing is lost on
        // navigation. Note FeatureDetailScreen saves the config BEFORE this
        // hook runs, so anything committed here must save on its own.
        flushPendingLive();
        AuroraConfig.save();
    }
}