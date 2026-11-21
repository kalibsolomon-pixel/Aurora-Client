package com.aurora.client.screen.setting;

import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.component.ColorSwatch;
import com.aurora.client.ui.component.Widget;
import com.aurora.client.ui.interaction.MinecraftSemanticFeedback;
import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.util.AuroraShapes;
import com.aurora.client.util.AuroraTheme;
import com.aurora.client.util.ColorEntryHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Color setting — collapsed swatch + expanded HSL picker. Wave D rebuild
 * swaps {@link com.aurora.client.util.RoundedRect} chrome for AuroraShapes
 * + theme colors and adds a hover halo to the swatch ring. HSL math and the
 * drag state machine are unchanged from prior waves; the pad/strips render
 * per device pixel (2026-10-30 — see the surface fields below).
 *
 * <p><b>Phase B ROLLOUT (2026-09-16):</b> canonical swatch states are this
 * row class's only behavior (see {@link ColorSwatch}'s class javadoc —
 * represented color is literal data, all interaction treatment on chrome)
 * plus the Enum-trigger interaction contract: a
 * {@link SemanticActionControl} (focus traversal, Enter/Space open/close
 * with exactly one activation click, narration carrying the current value
 * as hex plus expanded/collapsed), and the narrow keyboard adapter (Escape
 * while expanded collapses the picker without closing the screen). The
 * expanded editor's own surfaces (pad/strips/preview) are DATA by
 * convention — untouched. The Waypoint manager's display chips construct
 * {@link ColorSwatch} directly and stay legacy by construction.
 */
public class ColorSetting extends FeatureSetting {
    private static final int ROW_H_COLLAPSED = 28;

    private static final int PAD_SIZE   = 130;
    private static final int STRIP_W    = 16;
    private static final int STRIP_GAP  = 10;
    /** Square preview sample (owner ruling, 2026-10-30 — was totalW×22). */
    private static final int PREVIEW_S  = 22;
    private static final int PANEL_PAD = 8;
    private static final int PANEL_HEIGHT = PAD_SIZE + PREVIEW_S + 3 * PANEL_PAD;

    private final IntSupplier getter;
    private final Consumer<Integer> setter;

    private boolean expanded = false;
    private final com.aurora.client.ui.util.OverlayMotion editorMotion = new com.aurora.client.ui.util.OverlayMotion();
    private int editorX, editorY, editorWidth, editorPivot, editorHeight, padSize = PAD_SIZE;
    private float editorProgress;
    /** Opaque colours only (theme accent/background): no opacity strip, alpha pinned to 1. */
    private boolean opaque;

    private float h, s, l, a;

    private int swatchX, swatchY, swatchW = com.aurora.client.ui.util.UiDensity.SWATCH_W, swatchH = com.aurora.client.ui.util.UiDensity.SWATCH_H;
    private int padX, padY;
    private int hueX, hueY;
    private int alphaX, alphaY;
    private int previewX, previewY, previewW;

    private enum DragTarget { NONE, PAD, HUE, ALPHA }
    private DragTarget dragging = DragTarget.NONE;

    // ---- Per-pixel editing surfaces (2026-10-30) ----
    // The pad/strips rasterize per DEVICE pixel (UiLayerCache.writeArgb) into
    // small buffers and blit per frame — the former 32×32 GUI-cell pad and
    // 1-GUI-px strip rows banded visibly at every GUI scale. Version keys:
    // pad ← scale+hue, hue ← scale, alpha ← scale+h/s/l. Buffers are dropped
    // while collapsed (dispose is idempotent) so a closed editor owns no
    // GPU memory.
    private final com.aurora.client.ui.util.UiLayerCache padSurface = new com.aurora.client.ui.util.UiLayerCache();
    private final com.aurora.client.ui.util.UiLayerCache hueSurface = new com.aurora.client.ui.util.UiLayerCache();
    private final com.aurora.client.ui.util.UiLayerCache alphaSurface = new com.aurora.client.ui.util.UiLayerCache();
    private final com.aurora.client.ui.util.UiLayerCache previewSurface = new com.aurora.client.ui.util.UiLayerCache();
    /** Stable version contribution for a float. */
    private static long bits(float f) { return Float.floatToIntBits(f) & 0xFFFFFFFFL; }
    private static int guiScale() {
        return Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
    }

    /** Shared themed swatch — owns the checkerboard + fill + ring/halo rendering. */
    private final ColorSwatch swatch;
    /** Phase B: focus/keyboard/narration adapter (lazy — see {@link #interactionControl()}). */
    private SemanticActionControl interactionControl;

    private static final int HEX_GAP = 8;

    /** {@code #RRGGBB}, or {@code #RRGGBBAA} when the colour is translucent. */
    static String hexOf(int argb) {
        String hex = String.format("#%06X", argb & 0xFFFFFF);
        int alpha = argb >>> 24;
        return alpha == 0xFF ? hex : hex + String.format("%02X", alpha);
    }

    public ColorSetting(String label, IntSupplier getter, Consumer<Integer> setter) {
        super(label);
        this.getter = getter;
        this.setter = setter;
        // Phase B ROLLOUT: canonical swatch states are this row class's only
        // behavior — every production color row constructs here (the Enum
        // seam). The Waypoint manager's data-only chips construct ColorSwatch
        // directly and stay legacy BY CONSTRUCTION (the component-level opt-in
        // exists exactly for them).
        this.swatch = new ColorSwatch(getter, null).canonicalStates();
    }

    /**
     * Hides the opacity strip and pins alpha to 1 — for colours whose
     * translucency is owned elsewhere (the theme colours; window opacity has
     * its own slider).
     */
    public ColorSetting resetDefault(IntSupplier supplier) {
        int value = supplier.getAsInt();
        installReset(() -> getter.getAsInt() != value, () -> {
            dragging = DragTarget.NONE;
            setExpanded(false);
            applyResetColor(value);
        }, () -> hexOf(value));
        return this;
    }

    /** Nullable overrides must restore inheritance, rather than pinning today's resolved colour. */
    public ColorSetting resetDefault(java.util.function.Supplier<Integer> stored,
                                     java.util.function.Supplier<Integer> supplier, IntSupplier display) {
        Integer value = supplier.get();
        int shown = display.getAsInt();
        installReset(() -> !java.util.Objects.equals(stored.get(), value),
                () -> { dragging = DragTarget.NONE; setExpanded(false); setter.accept(value); }, () -> hexOf(shown));
        return this;
    }

    protected void applyResetColor(int value) {
        applyValue(value);
    }

    public ColorSetting opaqueOnly() { opaque = true; return this; }

    @Override public int baseHeight() { return compactRow == null ? ROW_H_COLLAPSED : compactRow.height(); }
    @Override public int height() { return baseHeight(); }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY) {
        var tr = Minecraft.getInstance().font;
        boolean disabled = isDisabled();
        // The value as text beside the swatch (2026-11-14): a swatch close to the window tone (the
        // factory graphite background) read as an empty box. Same slot as a slider's value.
        String hex = com.aurora.client.ui.component.MaterialSurface.unified() ? hexOf(getter.getAsInt()) : null;
        int hexW = hex == null ? 0 : tr.width(hex) + HEX_GAP; // the compact layout reserves HEX_W itself
        if (compactRow != null) renderCompactLabel(ctx, x, y, mouseX, mouseY, AuroraTheme.TEXT_PRIMARY);
        else renderWrappedLabel(ctx, label, x + 12, AuroraFontRenderer.textY(tr, y, ROW_H_COLLAPSED), Math.max(0,width-(swatchW+38+hexW)), AuroraTheme.TEXT_PRIMARY, mouseX, mouseY, disabled);

        swatchX = controlX(x, width, swatchW);
        swatchY = controlY(y, ROW_H_COLLAPSED, swatchH);
        if (hex != null) {
            // A value is data: primary text, like slider values (council 2026-11-18).
            int secondary = com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.ON_BACKGROUND);
            int textY = compactRow != null ? AuroraFontRenderer.textY(tr, swatchY, swatchH) : AuroraFontRenderer.textY(tr, y, ROW_H_COLLAPSED);
            ctx.text(tr, hex, swatchX - HEX_GAP - tr.width(hex), textY,
                    disabled ? AuroraTheme.TEXT_DIM : secondary, false);
        }
        swatch.disabled(disabled);
        swatch.layout(swatchX, swatchY, swatchW, swatchH);
        // Mirror the semantic control's geometry, pointer, and vanilla focus
        // into the swatch every frame (the BooleanSetting sync discipline).
        if (interactionControl != null) {
            if (compactRow == null) interactionControl.setBounds(swatchX, swatchY, swatchW, swatchH); else syncCompactControl(x, y);
            interactionControl.updatePointer(mouseX, mouseY);
            swatch.focusedVisual(interactionControl.isFocused());
        }
        swatch.renderOverlay(ctx, swatchX, swatchY, swatchW, swatchH, mouseX, mouseY);
        

        // Hover halo around swatch â€â€  translucent white outer ring that brightens on hover.
        // Swatch ring â€â€  aurora cyan.

        if (compactRow == null) renderFloatingEditor(ctx);
    }

    public static int editorHeight() { return PANEL_HEIGHT; }

    @Override
    public boolean dismissPopup() {
        if (!expanded) return false;
        setExpanded(false);
        return true;
    }

    public void renderFloatingEditor(GuiGraphicsExtractor ctx) {
        if (!expanded && editorProgress == 0) return;
        editorProgress = editorMotion.progress(System.nanoTime(), reducedMotion());
        if (editorProgress <= 0) {
            if (!expanded) disposeEditor();
            return;
        }

        if (expanded) com.aurora.client.ui.component.TooltipPresenter.suppressFrame();
        var band = hostBand();
        int horizontalChrome = 2 * PANEL_PAD + (STRIP_GAP + STRIP_W) * (opaque ? 1 : 2);
        int verticalChrome = PREVIEW_S + 3 * PANEL_PAD;
        int below = swatchY + swatchH + 2, above = swatchY - 2;
        int availableSide = Math.max(band.yEnd() - below, above - band.y);
        int availableHeight = Math.min(band.height, Math.max(availableSide, 32 + verticalChrome));
        padSize = Math.min(PAD_SIZE, Math.min(band.width - horizontalChrome, availableHeight - verticalChrome));
        if (padSize < 8) {
            onDetailScreenClose();
            return;
        }
        editorWidth = padSize + horizontalChrome;
        editorHeight = padSize + verticalChrome;
        editorX = Math.max(band.x, Math.min(swatchX + swatchW - editorWidth, band.xEnd() - editorWidth));
        editorY = com.aurora.client.ui.util.OverlayLayout.popupTop(below, above, editorHeight, band.y, band.yEnd());
        boolean upward = editorY < swatchY;
        editorPivot = upward ? editorY + editorHeight : editorY;
        boolean posed = com.aurora.client.ui.component.OverlayPose.push(ctx, swatchX + swatchW, editorPivot, editorProgress);
        float radius = AuroraTheme.RADIUS_SMALL;
        com.aurora.client.ui.component.MaterialSurface.overlayFace(ctx, editorX, editorY, editorWidth, editorHeight,
                radius, com.aurora.client.ui.component.MaterialSurface.windowCornerClearance(radius, PANEL_PAD, PANEL_PAD), editorProgress);
        padX = editorX + PANEL_PAD;
        padY = editorY + PANEL_PAD;
        hueX = padX + padSize + STRIP_GAP;
        hueY = padY;
        alphaX = hueX + STRIP_W + STRIP_GAP;
        alphaY = padY;
        previewX = padX;
        previewY = padY + padSize + 8;
        previewW = PREVIEW_S;

        drawSatLitPadCached(ctx);
        int cx = Math.clamp(padX + Math.round(s * padSize), padX + 3, padX + padSize - 3);
        int cy = Math.clamp(padY + Math.round((1f - l) * padSize), padY + 3, padY + padSize - 3);
        ctx.fill(cx - 1, padY, cx + 1, padY + padSize, fade(0x80000000));
        ctx.fill(padX, cy - 1, padX + padSize, cy + 1, fade(0x80000000));
        ctx.fill(cx - 3, cy - 3, cx + 3, cy + 3, fade(0xFFFFFFFF));
        ctx.fill(cx - 2, cy - 2, cx + 2, cy + 2, fade(ColorEntryHelper.hslaToArgb(h, s, l, 1f)));

        drawHueStrip(ctx);
        int hueMark = hueY + Math.round(h * padSize);
        ctx.fill(hueX - 2, hueMark - 1, hueX + STRIP_W + 2, hueMark + 1, fade(0xFFFFFFFF));

        if (!opaque) {
            drawAlphaStrip(ctx);
            int alphaMark = alphaY + Math.round((1f - a) * padSize);
            ctx.fill(alphaX - 2, alphaMark - 1, alphaX + STRIP_W + 2, alphaMark + 1, fade(0xFFFFFFFF));
        }

        drawPreview(ctx);
        if (posed) ctx.pose().popMatrix();
    }

    private int fade(int color) { return com.aurora.client.util.AuroraAnim.scaleAlpha(color, editorProgress); }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (resetMouseClicked(mouseX, mouseY, button)) return true;
        syncCompactControl(rowX, rowY);
        if (isDisabled()) return false;
        if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;
        if (compactRow != null) { swatchX = controlX(rowX, rowWidth, swatchW); swatchY = controlY(rowY, ROW_H_COLLAPSED, swatchH); }

        if (compactRow != null ? compactControlContains(mouseX, mouseY, rowX, rowY) : (mouseX >= swatchX && mouseX < swatchX + swatchW
                && mouseY >= swatchY && mouseY < swatchY + swatchH)) {
            // Semantic host: the control owns activation — exactly-once
            // behavior + one activation click on open/close. Hosts that
            // never ask for the control keep the direct path.
            if (interactionControl != null && interactionControl.isAvailable()) {
                return interactionControl.activateFromPointer(mouseX, mouseY, button);
            }
            toggleExpanded();
            return true;
        }
        if (!expanded) return false;
        if (editorProgress <= 0) return dismissPopup();
        mouseX = com.aurora.client.ui.util.OverlayMotion.unscale(mouseX, swatchX + swatchW, editorProgress);
        mouseY = com.aurora.client.ui.util.OverlayMotion.unscale(mouseY, editorPivot, editorProgress);

        if (Widget.inBounds(mouseX, mouseY, padX, padY, padSize, padSize)) {
            dragging = DragTarget.PAD;
            updateFromPad(mouseX, mouseY);
            return true;
        }
        if (Widget.inBounds(mouseX, mouseY, hueX, hueY, STRIP_W, padSize)) {
            dragging = DragTarget.HUE;
            updateFromHue(mouseY);
            return true;
        }
        if (!opaque && Widget.inBounds(mouseX, mouseY, alphaX, alphaY, STRIP_W, padSize)) {
            dragging = DragTarget.ALPHA;
            updateFromAlpha(mouseY);
            return true;
        }
        if (Widget.inBounds(mouseX, mouseY, editorX, editorY, editorWidth, editorHeight)) return true;
        setExpanded(false);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy, int rowX, int rowY, int rowWidth) {
        if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT || dragging == DragTarget.NONE) return false;
        mouseX = com.aurora.client.ui.util.OverlayMotion.unscale(mouseX, swatchX + swatchW, editorProgress);
        mouseY = com.aurora.client.ui.util.OverlayMotion.unscale(mouseY, editorPivot, editorProgress);
        switch (dragging) {
            case PAD   -> updateFromPad(mouseX, mouseY);
            case HUE   -> updateFromHue(mouseY);
            case ALPHA -> updateFromAlpha(mouseY);
            default    -> {}
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != DragTarget.NONE) {
            dragging = DragTarget.NONE;
            com.aurora.client.config.AuroraConfig.save();
            return true;
        }
        return false;
    }

    private void seedFromCurrent() {
        int argb = getter.getAsInt();
        float[] hsla = ColorEntryHelper.argbToHsla(argb);
        h = hsla[0]; s = hsla[1]; l = hsla[2]; a = opaque ? 1f : hsla[3];
    }

    /**
     * Swatch activation toggles the anchored editor and holds the focus
     * registry so the keyboard adapter can dismiss it.
     */
    private void toggleExpanded() {
        if (!expanded) {
            seedFromCurrent();
            setExpanded(true);
            requestFocus();
        } else {
            setExpanded(false);
            releaseFocus();
        }
    }

    private void setExpanded(boolean open) {
        expanded = open;
        editorMotion.setOpen(open, System.nanoTime());
        if (!open) {
            dragging = DragTarget.NONE;
            releaseFocus();
            if (editorProgress == 0) disposeEditor();
        }
    }

    private static boolean reducedMotion() {
        return com.aurora.client.config.AuroraConfig.get().reducedMotion;
    }

    /**
     * The row's semantic control (focus traversal, Enter/Space activation,
     * the activation click, narration with the current value as hex — the
     * established color-value vocabulary the picker's hex field uses — plus
     * expanded/collapsed). Lazy by design: it materializes only when a host
     * asks for it.
     */
    @Override
    public SemanticActionControl interactionControl() {
        if (interactionControl == null) {
            interactionControl = new SemanticActionControl(SemanticAction.button(
                    Component.literal(label),
                    () -> {
                        String description = currentDescription();
                        return description != null
                                ? Component.literal(description)
                                : Component.empty();
                    },
                    () -> Component.literal(String.format("#%06X", getter.getAsInt() & 0x00FFFFFF)
                            + (expanded ? ", expanded" : ", collapsed")),
                    () -> !isDisabled(),
                    this::toggleExpanded),
                    MinecraftSemanticFeedback.INSTANCE,
                    null,
                    SemanticActionControl.PointerRouting.MANUAL);
        }
        return interactionControl;
    }

    /**
     * Keyboard adapter, the Enum-trigger narrow set: Escape while expanded
     * collapses the picker without closing the screen. Collapsed Escape is
     * not consumed (screen close stays vanilla). Picker VALUE editing stays
     * pointer-driven; keyboard color entry is future accessibility work,
     * deliberately not built here.
     */
    @Override
    public boolean onKeyPress(int keyCode, int modifiers) {
        if (!expanded) return false;
        if (keyCode == InputConstants.KEY_ESCAPE) return dismissPopup();
        if (keyCode == InputConstants.KEY_TAB) dismissPopup();
        return false;
    }

    /**
     * Reset transient popup state on screen close (the EnumSetting lifecycle
     * contract — without this, an expanded picker and its registry focus
     * leak across screen opens because settings are long-lived singletons).
     */
    @Override
    public void onDetailScreenClose() {
        expanded = false;
        dragging = DragTarget.NONE;
        editorMotion.reset();
        editorProgress = 0;
        disposeEditor();
        releaseFocus();
    }

    @Override
    public void onInteractionAvailabilityChanged(boolean available) {
        if (!available && (expanded || editorProgress != 0)) onDetailScreenClose();
    }

    private void disposeEditor() {
        padSurface.dispose();
        hueSurface.dispose();
        alphaSurface.dispose();
        previewSurface.dispose();
    }

    private void commit() { applyValue(ColorEntryHelper.hslaToArgb(h, s, l, a)); }

    /** The commit seam — subclasses (AccentSetting's throttled live apply)
     *  intercept here; the plain row writes straight through. */
    void applyValue(int argb) { setter.accept(argb); }

    private void updateFromPad(double mouseX, double mouseY) {
        s = clamp01((float) ((mouseX - padX) / padSize));
        l = clamp01(1f - (float) ((mouseY - padY) / padSize));
        commit();
    }

    private void updateFromHue(double mouseY) {
        h = clamp(((float) (mouseY - hueY)) / padSize, 0f, 0.9999f);
        commit();
    }

    private void updateFromAlpha(double mouseY) {
        a = clamp01(1f - (float) ((mouseY - alphaY) / padSize));
        commit();
    }

    /** Source-over composite of {@code src} (ARGB) over opaque {@code bg}. */
    private static int compositeOver(int src, int bg) {
        int sa = (src >>> 24) & 0xFF;
        if (sa == 255) return src;
        int inv = 255 - sa;
        int r = ((src >>> 16) & 0xFF) * sa + ((bg >>> 16) & 0xFF) * inv;
        int g = ((src >>> 8) & 0xFF) * sa + ((bg >>> 8) & 0xFF) * inv;
        int b = (src & 0xFF) * sa + (bg & 0xFF) * inv;
        return 0xFF000000 | ((r / 255) << 16) | ((g / 255) << 8) | (b / 255);
    }

    private void drawSatLitPadCached(GuiGraphicsExtractor ctx) {
        int scale = guiScale();
        long version = (scale * 31L + padSize) << 32 ^ bits(h);
        if (padSurface.isCurrent(version)) {
            padSurface.blitAt(ctx, padX, padY, padSize, padSize, fade(-1));
            return;
        }
        com.aurora.client.ui.util.UiLayerCache.drawSurface(ctx, padSurface,
                version, padX, padY, padSize, padSize, fade(-1), () -> {
                    int dev = padSize * scale;
                    padSurface.writeArgb((px, py) -> ColorEntryHelper.hslaToArgb(h,
                            (px + 0.5f) / dev, 1f - (py + 0.5f) / dev, 1f));
                    AuroraShapes.outline(ctx, 0, 0, padSize, padSize, AuroraTheme.ACCENT_BLUE, 0);
                });
    }

    private void drawHueStrip(GuiGraphicsExtractor ctx) {
        int scale = guiScale();
        long version = scale * 31L + padSize;
        if (hueSurface.isCurrent(version)) {
            hueSurface.blitAt(ctx, hueX, hueY, STRIP_W, padSize, fade(-1));
            return;
        }
        com.aurora.client.ui.util.UiLayerCache.drawSurface(ctx, hueSurface,
                version, hueX, hueY, STRIP_W, padSize, fade(-1), () -> {
                    int devH = padSize * scale;
                    hueSurface.writeArgb((px, py) -> ColorEntryHelper.hslaToArgb((py + 0.5f) / devH, 1f, 0.5f, 1f));
                    AuroraShapes.outline(ctx, 0, 0, STRIP_W, padSize, AuroraTheme.ACCENT_BLUE, 0);
                });
    }

    private void drawAlphaStrip(GuiGraphicsExtractor ctx) {
        int scale = guiScale();
        long version = (scale * 31L + padSize) << 32 ^ bits(h) * 31L ^ bits(s) * 17L ^ bits(l);
        if (alphaSurface.isCurrent(version)) {
            alphaSurface.blitAt(ctx, alphaX, alphaY, STRIP_W, padSize, fade(-1));
            return;
        }
        com.aurora.client.ui.util.UiLayerCache.drawSurface(ctx, alphaSurface,
                version, alphaX, alphaY, STRIP_W, padSize, fade(-1), () -> {
                    int devW = STRIP_W * scale, devH = padSize * scale, cell = 4 * scale;
                    alphaSurface.writeArgb((px, py) -> {
                        int bg = ((px / cell) + (py / cell)) % 2 == 0 ? 0xFF555555 : 0xFFAAAAAA;
                        return compositeOver(ColorEntryHelper.hslaToArgb(h, s, l, 1f - (py + 0.5f) / devH), bg);
                    });
                    AuroraShapes.outline(ctx, 0, 0, STRIP_W, padSize, AuroraTheme.ACCENT_BLUE, 0);
                });
    }

    private void drawPreview(GuiGraphicsExtractor ctx) {
        int scale = guiScale();
        long version = scale * 31L;
        if (previewSurface.isCurrent(version)) {
            previewSurface.blitAt(ctx, previewX, previewY, PREVIEW_S, PREVIEW_S, fade(-1));
        } else {
            com.aurora.client.ui.util.UiLayerCache.drawSurface(ctx, previewSurface, version,
                    previewX, previewY, PREVIEW_S, PREVIEW_S, fade(-1), () -> {
                        int cell = 4 * scale;
                        previewSurface.writeArgb((px, py) -> ((px / cell) + (py / cell)) % 2 == 0 ? 0xFF555555 : 0xFFAAAAAA);
                    });
        }
        ctx.fill(previewX, previewY, previewX + previewW, previewY + PREVIEW_S, fade(ColorEntryHelper.hslaToArgb(h, s, l, a)));
        AuroraShapes.outline(ctx, previewX, previewY, previewW, PREVIEW_S, fade(AuroraTheme.ACCENT_BLUE), 0);
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
