package com.aurora.client.screen;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.Button;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.util.ColorEntryHelper;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.UiLayerCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/**
 * Full-screen color picker with live drag preview.
 *
 * <p>Built on the shared component framework: buttons are the canonical themed
 * {@link ButtonWidget}, the hex field is themed end-to-end by the mod-wide
 * {@code EditBoxMixin} (via {@link ThemedScreen}), chrome colors come from the
 * theme tokens, and every curve renders through the high-res AA engine. The
 * pad/hue/alpha gradients and their markers are <b>content</b> (they display
 * the user's color, not theme chrome), so their white marker/outline rings are
 * structural neutrals — the same convention as every knob/thumb in the
 * framework.
 *
 * <p>Glass rollout — chrome only: Apply takes accent-STAINED raised glass
 * (the screen's single primary action) and Cancel neutral raised glass. The
 * color-picking surfaces (saturation/lightness pad, hue strip, alpha strip,
 * preview swatch) remain <b>untinted, full-fidelity color</b> — glass on
 * them would blur and tint the very values the user is editing. The hex
 * field stays opaque (text entry is never glass).
 */
public class ColorPickerScreen extends Screen implements ThemedScreen {

    private final Screen parent;
    private final IntConsumer onApply;

    private float hue, sat, lit, alpha;

    private int padX, padY, padSize;
    private int hueX, hueY, hueW, hueH;
    private int alphaX, alphaY, alphaW, alphaH;
    private int previewX, previewY, previewW, previewH;

    private DragTarget dragging = DragTarget.NONE;
    private EditBox hexField;
    private boolean syncingHex;

    // ---- R8 (audit): cached editing surfaces ----
    // The pad, hue strip, alpha strip and preview checkerboard are ~2800
    // plain fill submissions EVERY frame at rest — pure static cost through
    // the same GuiRenderState path that made the list screens crawl. Each
    // surface rasterizes once into its own small buffer (identical output:
    // the integer-cell loops and AA outlines go through the capture sink at
    // exact device alignment) and blits per frame; the version keys are
    // deliberately INDEPENDENT so a drag only re-rasterizes the surfaces
    // whose values it actually changes:
    //   pad   ← hue only (sat/lit move the live markers, not the pad)
    //   hue   ← nothing  (value-independent gradient)
    //   alpha ← hue+sat+lit (alpha itself only moves the live marker)
    //   preview base ← nothing (checker + frame; the color fill stays live,
    //                   one plain fill)
    private final UiLayerCache padCache = new UiLayerCache();
    private final UiLayerCache hueCache = new UiLayerCache();
    private final UiLayerCache alphaCache = new UiLayerCache();
    private final UiLayerCache previewCache = new UiLayerCache();

    private enum DragTarget { NONE, PAD, HUE, ALPHA }

    public ColorPickerScreen(Screen parent, String title, int initial, IntConsumer onApply) {
        super(Component.literal(title));
        this.parent = parent;
        this.onApply = onApply;

        float[] hsla = ColorEntryHelper.argbToHsla(initial);
        this.hue = hsla[0];
        this.sat = hsla[1];
        this.lit = hsla[2];
        this.alpha = hsla[3];
    }

    @Override
    protected void init() {
        int cw = this.width;
        int ch = this.height;

        // Clamped to a sane minimum: on a very short window cw/ch can shrink
        // the computed size to zero or below, which inverts the pad's fill
        // loops. Below the clamp the picker gets cramped, never broken.
        padSize = Math.max(60, Math.min(240, Math.min(cw - 200, ch - 160)));
        padX = (cw - padSize - 80) / 2;
        padY = 48;

        hueW = 18;
        hueH = padSize;
        hueX = padX + padSize + 20;
        hueY = padY;

        alphaW = 18;
        alphaH = padSize;
        alphaX = hueX + hueW + 12;
        alphaY = padY;

        previewX = padX;
        previewY = padY + padSize + 14;
        previewW = padSize;
        previewH = 28;

        hexField = new EditBox(
                this.font,
                previewX, previewY + previewH + 10, previewW, 18,
                Component.literal("Hex")
        );
        hexField.setMaxLength(9);
        hexField.setValue(formatHex(currentArgb()));
        hexField.setResponder(this::onHexChanged);
        this.addRenderableWidget(hexField);

        int btnY = ch - 32;
        this.addRenderableWidget(new ButtonWidget(
                cw / 2 - 110, btnY, 100, 20,
                Component.literal("Apply"),
                () -> {
                    onApply.accept(currentArgb());
                    this.minecraft.setScreen(parent);
                },
                true).glassStyle(Button.GlassStyle.STAINED));

        this.addRenderableWidget(new ButtonWidget(
                cw / 2 + 10, btnY, 100, 20,
                Component.literal("Cancel"),
                () -> this.minecraft.setScreen(parent)).glassBackground(true));
    }

    private int currentArgb() {
        return ColorEntryHelper.hslaToArgb(hue, sat, lit, alpha);
    }

    private String formatHex(int argb) {
        return String.format("#%08X", argb);
    }

    private void onHexChanged(String s) {
        if (syncingHex) return;
        String v = s.trim();
        if (v.startsWith("#")) v = v.substring(1);
        if (v.length() != 8 && v.length() != 6) return;
        try {
            long parsed = Long.parseLong(v, 16);
            int argb = (int) (v.length() == 6 ? (0xFF000000L | parsed) : parsed);
            float[] hsla = ColorEntryHelper.argbToHsla(argb);
            hue = hsla[0]; sat = hsla[1]; lit = hsla[2]; alpha = hsla[3];
        } catch (NumberFormatException ignored) {}
    }

    private void syncHexFromState() {
        if (hexField == null) return;
        syncingHex = true;
        try {
            hexField.setValue(formatHex(currentArgb()));
        } finally {
            syncingHex = false;
        }
    }

    /**
     * Glass rollout: with a live world behind the screen, skip vanilla's
     * background sandwich — the glass buttons must sample the LIVE world.
     * With no level loaded the renderer declines anyway and the opaque
     * fallback wants the vanilla backdrop as before.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (this.minecraft != null && this.minecraft.level != null) return;
        super.renderBackground(g, mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // Themed overlay dim (token-driven; dark in both modes by design).
        ctx.fill(0, 0, this.width, this.height, ThemeManager.color(ThemeToken.OVERLAY_DIM));

        super.render(ctx, mouseX, mouseY, delta);
        Font tr = this.font;
        // Text directly on the overlay uses ON_OVERLAY (light in both modes).
        int onOverlay = ThemeManager.color(ThemeToken.ON_OVERLAY);
        int labelCol = ThemeManager.withAlpha(onOverlay, 0x99);

        AuroraFontRenderer.drawCentered(ctx, tr, this.title, this.width / 2, 20, onOverlay);

        int scale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
        drawCachedSurface(ctx, padCache,
                scale * 31L ^ padSize ^ bits(hue), padX, padY, padSize, padSize,
                () -> drawPadInto(ctx, 0, 0));

        int markX = padX + (int)(sat * padSize);
        int markY = padY + (int)((1f - lit) * padSize);
        ctx.fill(markX - 1, padY, markX + 1, padY + padSize, 0x80000000);
        ctx.fill(padX, markY - 1, padX + padSize, markY + 1, 0x80000000);
        ctx.fill(markX - 3, markY - 3, markX + 3, markY + 3, 0xFFFFFFFF);
        ctx.fill(markX - 2, markY - 2, markX + 2, markY + 2, currentArgb() | 0xFF000000);

        drawCachedSurface(ctx, hueCache,
                scale * 31L ^ hueW * 7919L ^ hueH * 17L, hueX, hueY, hueW, hueH,
                () -> drawHueInto(ctx, 0, 0));
        int hueMark = hueY + (int)(hue * hueH);
        ctx.fill(hueX - 2, hueMark - 1, hueX + hueW + 2, hueMark + 1, 0xFFFFFFFF);

        drawCachedSurface(ctx, alphaCache,
                scale * 31L ^ bits(hue) * 31L ^ bits(sat) * 17L ^ bits(lit), alphaX, alphaY, alphaW, alphaH,
                () -> drawAlphaInto(ctx, 0, 0));
        int alphaMark = alphaY + (int)((1f - alpha) * alphaH);
        ctx.fill(alphaX - 2, alphaMark - 1, alphaX + alphaW + 2, alphaMark + 1, 0xFFFFFFFF);

        drawCachedSurface(ctx, previewCache,
                scale * 31L ^ previewW * 7919L ^ previewH * 17L, previewX, previewY, previewW, previewH,
                () -> drawCheckerboard(ctx, 0, 0, previewW, previewH));
        ctx.fill(previewX, previewY, previewX + previewW, previewY + previewH, currentArgb());
        // Content-frame white on the preview — structural neutral (see class javadoc).
        RenderUtil.drawRoundedOutlineAA(ctx, previewX, previewY, previewW, previewH, 4, 1.0f, 0xFFFFFFFF);

        ctx.drawString(tr, Component.literal("Hue"),   hueX - 4, hueY - 12, labelCol, false);
        ctx.drawString(tr, Component.literal("Alpha"), alphaX - 8, alphaY - 12, labelCol, false);
        ctx.drawString(tr, Component.literal("Preview"), previewX, previewY - 12, labelCol, false);
        ctx.drawString(tr, Component.literal("Hex:"), previewX, previewY + previewH + 12, labelCol, false);

        // (No bespoke hex-field outline: the hex EditBox is themed end-to-end by
        // the mod-wide EditBoxMixin — fill, border, text and caret — through
        // this screen's ThemedScreen marker. One implementation, no overlay.)
    }

    /** Version helper: stable key contribution for a float. */
    private static long bits(float f) { return Float.floatToIntBits(f) & 0xFFFFFFFFL; }

    /**
     * The cache discipline for one editing surface: if the buffer is not
     * current for {@code version}, rasterize {@code raster} (drawing at
     * LOCAL coordinates — the capture sink maps them to the buffer's
     * physical pixels) and commit; then one positional blit at {@code (x,y)}.
     */
    private static void drawCachedSurface(GuiGraphics ctx, UiLayerCache cache, long version,
                                          int x, int y, int wLogical, int hLogical, Runnable raster) {
        if (!cache.isCurrent(version)) {
            int scale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
            cache.ensureSize(wLogical * scale, hLogical * scale);
            cache.clear();
            RenderUtil.RectSink prev = RenderUtil.beginCapture(cache.sink());
            try {
                raster.run();
            } finally {
                RenderUtil.endCapture(prev);
            }
            cache.commit(version);
        }
        cache.blitAt(ctx, x, y, wLogical, hLogical);
    }

    /** Saturation/lightness pad at local coordinates (capture-aware cells + the fixed white frame). */
    private void drawPadInto(GuiGraphics ctx, int ox, int oy) {
        int steps = 40;
        for (int sx = 0; sx < steps; sx++) {
            float s = sx / (float)(steps - 1);
            int x0 = ox + (sx * padSize) / steps;
            int x1 = ox + ((sx + 1) * padSize) / steps;
            for (int sy = 0; sy < steps; sy++) {
                float l = 1f - (sy / (float)(steps - 1));
                int argb = ColorEntryHelper.hslaToArgb(hue, s, l, 1f);
                int y0 = oy + (sy * padSize) / steps;
                int y1 = oy + ((sy + 1) * padSize) / steps;
                RenderUtil.fillLogical(ctx, x0, y0, x1, y1, argb);
            }
        }
        // Content-frame white around the pad — structural neutral (see class javadoc).
        RenderUtil.drawRoundedOutlineAA(ctx, ox, oy, padSize, padSize, 6, 1.0f, 0xFFFFFFFF);
    }

    /** Hue strip at local coordinates. */
    private void drawHueInto(GuiGraphics ctx, int ox, int oy) {
        for (int py = 0; py < hueH; py++) {
            float h = py / (float) hueH;
            int argb = ColorEntryHelper.hslaToArgb(h, 1f, 0.5f, 1f);
            RenderUtil.fillLogical(ctx, ox, oy + py, ox + hueW, oy + py + 1, argb);
        }
        // Content-frame white around the hue strip — structural neutral.
        RenderUtil.drawRoundedOutlineAA(ctx, ox, oy, hueW, hueH, 6, 1.0f, 0xFFFFFFFF);
    }

    /** Alpha strip (checkerboard + gradient) at local coordinates. */
    private void drawAlphaInto(GuiGraphics ctx, int ox, int oy) {
        drawCheckerboard(ctx, ox, oy, alphaW, alphaH);
        for (int py = 0; py < alphaH; py++) {
            float a = 1f - (py / (float) alphaH);
            int argb = ColorEntryHelper.hslaToArgb(hue, sat, lit, a);
            RenderUtil.fillLogical(ctx, ox, oy + py, ox + alphaW, oy + py + 1, argb);
        }
        // Content-frame white around the alpha strip — structural neutral.
        RenderUtil.drawRoundedOutlineAA(ctx, ox, oy, alphaW, alphaH, 6, 1.0f, 0xFFFFFFFF);
    }

    private void drawCheckerboard(GuiGraphics ctx, int x, int y, int w, int h) {
        int cell = 4;
        for (int dy = 0; dy < h; dy += cell) {
            for (int dx = 0; dx < w; dx += cell) {
                boolean dark = ((dx / cell) + (dy / cell)) % 2 == 0;
                // Checkerboard convention shared with the ColorSwatch component.
                int col = dark ? 0xFF555555 : 0xFFAAAAAA;
                RenderUtil.fillLogical(ctx, x + dx, y + dy,
                        Math.min(x + dx + cell, x + w),
                        Math.min(y + dy + cell, y + h),
                        col);
            }
        }
    }

    @Override
    public void removed() {
        super.removed();
        padCache.dispose();
        hueCache.dispose();
        alphaCache.dispose();
        previewCache.dispose();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent _ev, boolean _doubleClicked) {
        double mouseX = _ev.x(); double mouseY = _ev.y(); int button = _ev.button();
        if (button == 0) {
            if (inBounds(mouseX, mouseY, padX, padY, padSize, padSize)) {
                dragging = DragTarget.PAD;
                updateFromPad(mouseX, mouseY);
                return true;
            }
            if (inBounds(mouseX, mouseY, hueX, hueY, hueW, hueH)) {
                dragging = DragTarget.HUE;
                updateFromHue(mouseY);
                return true;
            }
            if (inBounds(mouseX, mouseY, alphaX, alphaY, alphaW, alphaH)) {
                dragging = DragTarget.ALPHA;
                updateFromAlpha(mouseY);
                return true;
            }
        }
        return super.mouseClicked(_ev, _doubleClicked);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent _ev, double dx, double dy) {
        double mouseX = _ev.x(); double mouseY = _ev.y(); int button = _ev.button();
        if (button == 0 && dragging != DragTarget.NONE) {
            switch (dragging) {
                case PAD -> updateFromPad(mouseX, mouseY);
                case HUE -> updateFromHue(mouseY);
                case ALPHA -> updateFromAlpha(mouseY);
                default -> {}
            }
            return true;
        }
        return super.mouseDragged(_ev, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent _ev) {
        double mouseX = _ev.x(); double mouseY = _ev.y(); int button = _ev.button();
        dragging = DragTarget.NONE;
        return super.mouseReleased(_ev);
    }

    private void updateFromPad(double mouseX, double mouseY) {
        float s = (float) ((mouseX - padX) / padSize);
        float l = 1f - (float) ((mouseY - padY) / padSize);
        sat = clamp01(s);
        lit = clamp01(l);
        syncHexFromState();
    }

    private void updateFromHue(double mouseY) {
        float h = (float) ((mouseY - hueY) / hueH);
        hue = clamp(h, 0f, 0.9999f);
        syncHexFromState();
    }

    private void updateFromAlpha(double mouseY) {
        float a = 1f - (float) ((mouseY - alphaY) / alphaH);
        alpha = clamp01(a);
        syncHexFromState();
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private boolean inBounds(double x, double y, int bx, int by, int bw, int bh) {
        return x >= bx && x < bx + bw && y >= by && y < by + bh;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
