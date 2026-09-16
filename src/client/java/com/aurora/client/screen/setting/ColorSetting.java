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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Color setting — collapsed swatch + expanded HSL picker. Wave D rebuild
 * swaps {@link com.aurora.client.util.RoundedRect} chrome for AuroraShapes
 * + theme colors and adds a hover halo to the swatch ring. HSL math, pad
 * cache, drag state machine — all unchanged from prior waves.
 *
 * <p><b>Phase B pilot (2026-09-16):</b> {@link #canonicalStates()} opts the
 * row's swatch into the canonical state channels (see {@link ColorSwatch}'s
 * class javadoc — represented color is literal data, all interaction
 * treatment on chrome) and gives the swatch the Enum-trigger interaction
 * contract: a {@link SemanticActionControl} (focus traversal, Enter/Space
 * open/close with exactly one activation click, narration carrying the
 * current value as hex plus expanded/collapsed), and the narrow keyboard
 * adapter (Escape while expanded collapses the picker without closing the
 * screen). The expanded editor's own surfaces (pad/strips/preview) are
 * DATA by convention — untouched. Hosts never marking the control
 * available keep the legacy pointer-only path.
 */
public class ColorSetting extends FeatureSetting {
    private static final int ROW_H_COLLAPSED = 28;

    private static final int PAD_SIZE   = 130;
    private static final int STRIP_W    = 16;
    private static final int STRIP_GAP  = 10;
    private static final int PREVIEW_H  = 22;
    private static final int VPAD       = 14;
    private static final int PAD_STEPS  = 32;

    private static final int EXPANDED_BODY = PAD_SIZE + VPAD + PREVIEW_H + VPAD;
    private static final int ROW_H_EXPANDED = ROW_H_COLLAPSED + EXPANDED_BODY;

    private final IntSupplier getter;
    private final Consumer<Integer> setter;

    private boolean expanded = false;

    private float h, s, l, a;

    private int swatchX, swatchY, swatchW = 40, swatchH = 16;
    private int padX, padY;
    private int hueX, hueY;
    private int alphaX, alphaY;
    private int previewX, previewY, previewW;

    private enum DragTarget { NONE, PAD, HUE, ALPHA }
    private DragTarget dragging = DragTarget.NONE;

    private float cachedHueForPad = -1f;
    private final int[] padCache = new int[PAD_STEPS * PAD_STEPS];

    /** Shared themed swatch — owns the checkerboard + fill + ring/halo rendering. */
    private final ColorSwatch swatch;
    /** Phase B: focus/keyboard/narration adapter (lazy — see {@link #interactionControl()}). */
    private SemanticActionControl interactionControl;
    /** Phase B opt-in — see the class javadoc. */
    private boolean canonical;

    public ColorSetting(String label, IntSupplier getter, Consumer<Integer> setter) {
        super(label);
        this.getter = getter;
        this.setter = setter;
        this.swatch = new ColorSwatch(getter, null);
    }

    /**
     * Opts this row's swatch into the Phase B canonical state channels plus
     * the semantic open/close adapter. Must be called before first render.
     * Every other color row keeps the legacy behavior byte-for-byte.
     */
    public ColorSetting canonicalStates() {
        this.canonical = true;
        swatch.canonicalStates();
        return this;
    }

    /** True when this row runs the Phase B canonical state channels. */
    public boolean canonical() {
        return canonical;
    }

    @Override public int baseHeight() { return expanded ? ROW_H_EXPANDED : ROW_H_COLLAPSED; }
    @Override public int height() { return baseHeight(); }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        var tr = Minecraft.getInstance().font;
        boolean disabled = isDisabled();
        renderLabelWithTooltip(ctx, label, x + 12, y + (ROW_H_COLLAPSED - tr.lineHeight) / 2,
                AuroraTheme.TEXT_PRIMARY, mouseX, mouseY, disabled);

        swatchX = x + width - swatchW - 14;
        swatchY = y + (ROW_H_COLLAPSED - swatchH) / 2;
        swatch.disabled(disabled);
        swatch.layout(swatchX, swatchY, swatchW, swatchH);
        // Canonical rows: mirror the semantic control's geometry, pointer,
        // and vanilla focus into the swatch every frame (the BooleanSetting
        // sync discipline).
        if (canonical && interactionControl != null) {
            interactionControl.setBounds(swatchX, swatchY, swatchW, swatchH);
            interactionControl.updatePointer(mouseX, mouseY);
            swatch.focusedVisual(interactionControl.isFocused());
        }
        swatch.renderOverlay(ctx, swatchX, swatchY, swatchW, swatchH, mouseX, mouseY);
        

        // Hover halo around swatch Ã¢â‚¬â€  translucent white outer ring that brightens on hover.
        // Swatch ring Ã¢â‚¬â€  aurora cyan.

        if (!expanded) return;

        int totalW = PAD_SIZE + STRIP_GAP + STRIP_W + STRIP_GAP + STRIP_W;
        int startX = x + (width - totalW) / 2;
        int bodyTop = y + ROW_H_COLLAPSED + 8;

        padX = startX;
        padY = bodyTop;
        hueX = padX + PAD_SIZE + STRIP_GAP;
        hueY = padY;
        alphaX = hueX + STRIP_W + STRIP_GAP;
        alphaY = padY;
        previewX = padX;
        previewY = padY + PAD_SIZE + 8;
        previewW = totalW;

        drawSatLitPadCached(ctx);
        int cx = padX + Math.round(s * PAD_SIZE);
        int cy = padY + Math.round((1f - l) * PAD_SIZE);
        ctx.fill(cx - 1, padY, cx + 1, padY + PAD_SIZE, 0x80000000);
        ctx.fill(padX, cy - 1, padX + PAD_SIZE, cy + 1, 0x80000000);
        ctx.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0xFFFFFFFF);
        ctx.fill(cx - 2, cy - 2, cx + 2, cy + 2, ColorEntryHelper.hslaToArgb(h, s, l, 1f));

        drawHueStrip(ctx);
        int hueMark = hueY + Math.round(h * PAD_SIZE);
        ctx.fill(hueX - 2, hueMark - 1, hueX + STRIP_W + 2, hueMark + 1, 0xFFFFFFFF);

        drawAlphaStrip(ctx);
        int alphaMark = alphaY + Math.round((1f - a) * PAD_SIZE);
        ctx.fill(alphaX - 2, alphaMark - 1, alphaX + STRIP_W + 2, alphaMark + 1, 0xFFFFFFFF);

        drawCheckerboard(ctx, previewX, previewY, previewW, PREVIEW_H);
        ctx.fill(previewX, previewY, previewX + previewW, previewY + PREVIEW_H, ColorEntryHelper.hslaToArgb(h, s, l, a));
        AuroraShapes.outline(ctx, previewX, previewY, previewW, PREVIEW_H,
                AuroraTheme.ACCENT_BLUE, 0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (isDisabled()) return false;
        if (button != 0) return false;

        if (mouseX >= swatchX && mouseX < swatchX + swatchW
                && mouseY >= swatchY && mouseY < swatchY + swatchH) {
            // Semantic host: the control owns activation — exactly-once
            // behavior + one activation click on open/close. Legacy hosts
            // keep the direct path, byte-for-byte today's behavior.
            if (canonical && interactionControl != null && interactionControl.isAvailable()) {
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
        if (Widget.inBounds(mouseX, mouseY, alphaX, alphaY, STRIP_W, PAD_SIZE)) {
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
        h = hsla[0]; s = hsla[1]; l = hsla[2]; a = hsla[3];
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
     * The canonical row's semantic control (focus traversal, Enter/Space
     * activation, the activation click, narration with the current value as
     * hex — the established color-value vocabulary the picker's hex field
     * uses — plus expanded/collapsed). Legacy rows return null — the pilot
     * isolation.
     */
    @Override
    public SemanticActionControl interactionControl() {
        if (!canonical) return null;
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
     * Canonical keyboard adapter, the Enum-trigger narrow set: Escape while
     * expanded collapses the picker without closing the screen. Collapsed
     * Escape is not consumed (screen close stays vanilla). Picker VALUE
     * editing stays pointer-driven; keyboard color entry is future
     * accessibility work, deliberately not built here.
     */
    @Override
    public boolean onKeyPress(int keyCode, int modifiers) {
        if (!canonical || !expanded) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            expanded = false;
            releaseFocus();
            return true;
        }
        return false;
    }

    /**
     * Canonical rows reset transient popup state on screen close (the
     * EnumSetting lifecycle contract — without this, an expanded picker and
     * its registry focus leak across screen opens because settings are
     * long-lived singletons). Legacy rows keep the shipped
     * persists-across-close behavior.
     */
    @Override
    public void onDetailScreenClose() {
        if (!canonical) return;
        expanded = false;
        dragging = DragTarget.NONE;
        releaseFocus();
    }

    private void commit() { setter.accept(ColorEntryHelper.hslaToArgb(h, s, l, a)); }

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

    private void drawSatLitPadCached(GuiGraphics ctx) {
        if (Math.abs(h - cachedHueForPad) > 0.0001f) {
            for (int sx = 0; sx < PAD_STEPS; sx++) {
                float ss = sx / (float) (PAD_STEPS - 1);
                for (int sy = 0; sy < PAD_STEPS; sy++) {
                    float ll = 1f - (sy / (float) (PAD_STEPS - 1));
                    padCache[sy * PAD_STEPS + sx] = ColorEntryHelper.hslaToArgb(h, ss, ll, 1f);
                }
            }
            cachedHueForPad = h;
        }
        for (int sx = 0; sx < PAD_STEPS; sx++) {
            int x0 = padX + (sx * PAD_SIZE) / PAD_STEPS;
            int x1 = padX + ((sx + 1) * PAD_SIZE) / PAD_STEPS;
            for (int sy = 0; sy < PAD_STEPS; sy++) {
                int y0 = padY + (sy * PAD_SIZE) / PAD_STEPS;
                int y1 = padY + ((sy + 1) * PAD_SIZE) / PAD_STEPS;
                ctx.fill(x0, y0, x1, y1, padCache[sy * PAD_STEPS + sx]);
            }
        }
        AuroraShapes.outline(ctx, padX, padY, PAD_SIZE, PAD_SIZE, AuroraTheme.ACCENT_BLUE, 0);
    }

    private void drawHueStrip(GuiGraphics ctx) {
        for (int py = 0; py < PAD_SIZE; py++) {
            float hh = py / (float) PAD_SIZE;
            ctx.fill(hueX, hueY + py, hueX + STRIP_W, hueY + py + 1, ColorEntryHelper.hslaToArgb(hh, 1f, 0.5f, 1f));
        }
        AuroraShapes.outline(ctx, hueX, hueY, STRIP_W, PAD_SIZE, AuroraTheme.ACCENT_BLUE, 0);
    }

    private void drawAlphaStrip(GuiGraphics ctx) {
        drawCheckerboard(ctx, alphaX, alphaY, STRIP_W, PAD_SIZE);
        for (int py = 0; py < PAD_SIZE; py++) {
            float aa = 1f - (py / (float) PAD_SIZE);
            ctx.fill(alphaX, alphaY + py, alphaX + STRIP_W, alphaY + py + 1, ColorEntryHelper.hslaToArgb(h, s, l, aa));
        }
        AuroraShapes.outline(ctx, alphaX, alphaY, STRIP_W, PAD_SIZE, AuroraTheme.ACCENT_BLUE, 0);
    }

    private static void drawCheckerboard(GuiGraphics ctx, int x, int y, int w, int h) {
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
