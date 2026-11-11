package com.aurora.client.screen.setting;

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
import org.lwjgl.glfw.GLFW;

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
    private static final int VPAD       = 14;

    private static final int EXPANDED_BODY = PAD_SIZE + VPAD + PREVIEW_S + VPAD;
    private static final int ROW_H_EXPANDED = ROW_H_COLLAPSED + EXPANDED_BODY;

    private final IntSupplier getter;
    private final Consumer<Integer> setter;

    private boolean expanded = false;
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
    /** Stable version contribution for a float. */
    private static long bits(float f) { return Float.floatToIntBits(f) & 0xFFFFFFFFL; }
    private static int guiScale() {
        return Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
    }

    /** Shared themed swatch — owns the checkerboard + fill + ring/halo rendering. */
    private final ColorSwatch swatch;
    /** Phase B: focus/keyboard/narration adapter (lazy — see {@link #interactionControl()}). */
    private SemanticActionControl interactionControl;

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
    public ColorSetting opaqueOnly() { opaque = true; return this; }

    @Override public int baseHeight() { return compactRow == null ? (expanded ? ROW_H_EXPANDED : ROW_H_COLLAPSED) : compactRow.height() + (expanded ? EXPANDED_BODY : 0); }
    @Override public int height() { return baseHeight(); }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY) {
        var tr = Minecraft.getInstance().font;
        boolean disabled = isDisabled();
        if (!expanded) { // a collapsed row owns no editing-surface memory
            padSurface.dispose();
            hueSurface.dispose();
            alphaSurface.dispose();
        }
        if (compactRow != null) renderCompactLabel(ctx, x, y, mouseX, mouseY, AuroraTheme.TEXT_PRIMARY);
        else renderWrappedLabel(ctx, label, x + 12, y + (ROW_H_COLLAPSED - tr.lineHeight) / 2, Math.max(0,width-(swatchW+38)), AuroraTheme.TEXT_PRIMARY, mouseX, mouseY, disabled);

        swatchX = controlX(x, width, swatchW);
        swatchY = controlY(y, ROW_H_COLLAPSED, swatchH);
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
        

        // Hover halo around swatch Ã¢â‚¬â€  translucent white outer ring that brightens on hover.
        // Swatch ring Ã¢â‚¬â€  aurora cyan.

        if (!expanded) return;

        int totalW = PAD_SIZE + STRIP_GAP + STRIP_W + (opaque ? 0 : STRIP_GAP + STRIP_W);
        int startX = x + (width - totalW) / 2;
        int bodyTop = y + (compactRow == null ? ROW_H_COLLAPSED : compactRow.height()) + 8;

        padX = startX;
        padY = bodyTop;
        hueX = padX + PAD_SIZE + STRIP_GAP;
        hueY = padY;
        alphaX = hueX + STRIP_W + STRIP_GAP;
        alphaY = padY;
        previewX = padX;
        previewY = padY + PAD_SIZE + 8;
        previewW = PREVIEW_S;

        drawSatLitPadCached(ctx);
        ctx.enableScissor(padX,padY,padX+PAD_SIZE,padY+PAD_SIZE);
        int cx = padX + Math.round(s * PAD_SIZE);
        int cy = padY + Math.round((1f - l) * PAD_SIZE);
        ctx.fill(cx - 1, padY, cx + 1, padY + PAD_SIZE, 0x80000000);
        ctx.fill(padX, cy - 1, padX + PAD_SIZE, cy + 1, 0x80000000);
        ctx.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0xFFFFFFFF);
        ctx.fill(cx - 2, cy - 2, cx + 2, cy + 2, ColorEntryHelper.hslaToArgb(h, s, l, 1f));
        ctx.disableScissor();

        drawHueStrip(ctx);
        int hueMark = hueY + Math.round(h * PAD_SIZE);
        ctx.fill(hueX - 2, hueMark - 1, hueX + STRIP_W + 2, hueMark + 1, 0xFFFFFFFF);

        if (!opaque) {
            drawAlphaStrip(ctx);
            int alphaMark = alphaY + Math.round((1f - a) * PAD_SIZE);
            ctx.fill(alphaX - 2, alphaMark - 1, alphaX + STRIP_W + 2, alphaMark + 1, 0xFFFFFFFF);
        }

        drawCheckerboard(ctx, previewX, previewY, previewW, PREVIEW_S);
        ctx.fill(previewX, previewY, previewX + previewW, previewY + PREVIEW_S, ColorEntryHelper.hslaToArgb(h, s, l, a));
        AuroraShapes.outline(ctx, previewX, previewY, previewW, PREVIEW_S,
                AuroraTheme.ACCENT_BLUE, 0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        syncCompactControl(rowX, rowY);
        if (isDisabled()) return false;
        if (button != 0) return false;
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

        if (Widget.inBounds(mouseX, mouseY, padX, padY, PAD_SIZE, PAD_SIZE)) {
            dragging = DragTarget.PAD;
            updateFromPad(mouseX, mouseY);
            return true;
        }
        if (Widget.inBounds(mouseX, mouseY, hueX, hueY, STRIP_W, PAD_SIZE)) {
            dragging = DragTarget.HUE;
            updateFromHue(mouseY);
            return true;
        }
        if (!opaque && Widget.inBounds(mouseX, mouseY, alphaX, alphaY, STRIP_W, PAD_SIZE)) {
            dragging = DragTarget.ALPHA;
            updateFromAlpha(mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy, int rowX, int rowY, int rowWidth) {
        if (button != 0 || dragging == DragTarget.NONE) return false;
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
     * Swatch activation: toggle the inline editor + (canonical) hold the
     * focus registry while expanded so the keyboard adapter can receive
     * Escape. The editor expanding under the swatch IS the press feedback —
     * no separate press treatment is added (the row's height doubles; a
     * scale/deform on top of that would only suggest the value changed).
     */
    private void toggleExpanded() {
        if (!expanded) {
            seedFromCurrent();
            expanded = true;
            requestFocus();
        } else {
            expanded = false;
            releaseFocus();
        }
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
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            expanded = false;
            releaseFocus();
            return true;
        }
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
        padSurface.dispose();
        hueSurface.dispose();
        alphaSurface.dispose();
        releaseFocus();
    }

    private void commit() { applyValue(ColorEntryHelper.hslaToArgb(h, s, l, a)); }

    /** The commit seam — subclasses (AccentSetting's throttled live apply)
     *  intercept here; the plain row writes straight through. */
    void applyValue(int argb) { setter.accept(argb); }

    private void updateFromPad(double mouseX, double mouseY) {
        s = clamp01((float) ((mouseX - padX) / PAD_SIZE));
        l = clamp01(1f - (float) ((mouseY - padY) / PAD_SIZE));
        commit();
    }

    private void updateFromHue(double mouseY) {
        h = clamp(((float) (mouseY - hueY)) / PAD_SIZE, 0f, 0.9999f);
        commit();
    }

    private void updateFromAlpha(double mouseY) {
        a = clamp01(1f - (float) ((mouseY - alphaY) / PAD_SIZE));
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
        com.aurora.client.ui.util.UiLayerCache.drawSurface(ctx, padSurface,
                scale * 31L ^ bits(h), padX, padY, PAD_SIZE, PAD_SIZE, () -> {
                    int dev = PAD_SIZE * scale;
                    padSurface.writeArgb((px, py) -> ColorEntryHelper.hslaToArgb(h,
                            (px + 0.5f) / dev, 1f - (py + 0.5f) / dev, 1f));
                    AuroraShapes.outline(ctx, 0, 0, PAD_SIZE, PAD_SIZE, AuroraTheme.ACCENT_BLUE, 0);
                });
    }

    private void drawHueStrip(GuiGraphicsExtractor ctx) {
        int scale = guiScale();
        com.aurora.client.ui.util.UiLayerCache.drawSurface(ctx, hueSurface,
                scale * 31L, hueX, hueY, STRIP_W, PAD_SIZE, () -> {
                    int devH = PAD_SIZE * scale;
                    hueSurface.writeArgb((px, py) -> ColorEntryHelper.hslaToArgb((py + 0.5f) / devH, 1f, 0.5f, 1f));
                    AuroraShapes.outline(ctx, 0, 0, STRIP_W, PAD_SIZE, AuroraTheme.ACCENT_BLUE, 0);
                });
    }

    private void drawAlphaStrip(GuiGraphicsExtractor ctx) {
        int scale = guiScale();
        com.aurora.client.ui.util.UiLayerCache.drawSurface(ctx, alphaSurface,
                scale * 31L ^ bits(h) * 31L ^ bits(s) * 17L ^ bits(l), alphaX, alphaY, STRIP_W, PAD_SIZE, () -> {
                    int devW = STRIP_W * scale, devH = PAD_SIZE * scale, cell = 4 * scale;
                    alphaSurface.writeArgb((px, py) -> {
                        int bg = ((px / cell) + (py / cell)) % 2 == 0 ? 0xFF555555 : 0xFFAAAAAA;
                        return compositeOver(ColorEntryHelper.hslaToArgb(h, s, l, 1f - (py + 0.5f) / devH), bg);
                    });
                    AuroraShapes.outline(ctx, 0, 0, STRIP_W, PAD_SIZE, AuroraTheme.ACCENT_BLUE, 0);
                });
    }

    private static void drawCheckerboard(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        int cell = 4;
        for (int dy = 0; dy < h; dy += cell) {
            for (int dx = 0; dx < w; dx += cell) {
                boolean dark = ((dx / cell) + (dy / cell)) % 2 == 0;
                int col = dark ? 0xFF555555 : 0xFFAAAAAA;
                ctx.fill(x + dx, y + dy,
                        Math.min(x + dx + cell, x + w),
                        Math.min(y + dy + cell, y + h),
                        col);
            }
        }
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
