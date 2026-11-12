package com.aurora.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.aurora.client.ui.component.MaterialSurface;
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
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    /** The window owns the one backdrop capture; child buttons/rows use cached faces
     *  (a per-button blur tinted each control by whatever sat behind it). */
    @Override public boolean parentGlassOnly() { return true; }

    private final Screen parent;
    private final IntConsumer onApply;

    private float hue, sat, lit, alpha;

    private int padX, padY, padSize;
    private int panelX, panelY, panelW, panelH;
    private int hueX, hueY, hueW, hueH;
    private int alphaX, alphaY, alphaW, alphaH;
    private int previewX, previewY, previewW, previewH;

    private DragTarget dragging = DragTarget.NONE;
    private EditBox hexField;
    private boolean syncingHex;

    /**
     * C-8 short-window geometry: the picker is SUPPORTABLE down to a
     * {@link #MIN_PAD}px pad — below that the responsive stack (pad +
     * preview + hex field above the pinned Apply/Cancel row) cannot fit
     * without overlapping, so the editing surfaces are skipped entirely and
     * a message renders in their place (the old code clamped the pad to a
     * 60px floor regardless of window height, which pushed the hex field
     * under the buttons on any window shorter than ~210 GUI px). The
     * buttons stay pinned and reachable in every configuration — Apply
     * commits the current (initial) color, Cancel discards.
     */
    private static final int MIN_PAD = 40;
    /** Whether this window can show the editing surfaces at all (set by init). */
    private boolean pickerSupportable = true;

    // ---- R8 (audit): cached editing surfaces; per-pixel gradients ----
    // The pad, hue strip, alpha strip and preview checkerboard rasterize
    // once into their own small buffers and blit per frame; the version keys
    // are deliberately INDEPENDENT so a drag only re-rasterizes the surfaces
    // whose values it actually changes:
    //   pad   ← hue only (sat/lit move the live markers, not the pad)
    //   hue   ← nothing  (value-independent gradient)
    //   alpha ← hue+sat+lit (alpha itself only moves the live marker)
    //   preview base ← nothing (checker + frame; the color fill stays live,
    //                   one plain fill)
    // Since 2026-10-30 the gradient surfaces are PER-DEVICE-PIXEL rasters
    // (UiLayerCache.writeArgb): every physical pixel carries its exactly
    // sampled HSL/alpha color — the former 40×40 GUI-cell grid and 1-GUI-px
    // strip rows banded visibly at every GUI scale. A hue drag re-rasterizes
    // the pad each frame (~device pad area in plain float math — measured in
    // the low single-digit ms at GUI scale 4, drag-only); strips are cheap.
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

        // Responsive with an explicit supportable minimum (C-8): the pad
        // shrinks with BOTH axes (the height term reserves the vertical
        // stack below it — preview + hex + gap — plus the pinned button
        // row, so an unclamped pad never overlaps them); below MIN_PAD the
        // editing surfaces are skipped (see pickerSupportable) instead of
        // generating overlapping or off-bounds controls.
        // One centered dialog (premium-design audit, 2026-10-19): the title
        // sits in the panel's header band, every editing surface and both
        // actions live INSIDE the panel (Apply previously straddled its
        // bottom edge), and the panel is centered on both axes. The pad
        // still shrinks with both axes; below MIN_PAD the editing surfaces
        // are skipped (see pickerSupportable).
        // Uniform window (compact pass, 2026-10-30): the shared Aurora frame. The pad and
        // strips fill its left side; preview + hex form the right column; the actions sit
        // on the bottom edge.
        var frame = com.aurora.client.ui.util.UiDensity.window(cw, ch);
        panelX = frame.x(); panelY = frame.y(); panelW = frame.width(); panelH = frame.height();
        int header = com.aurora.client.ui.util.UiDensity.HEADER;
        int bh = com.aurora.client.ui.util.UiDensity.HEADER_BUTTON_H;
        int maxPad = Math.min(panelH - header - 16 - bh - 16, panelW - 24 - 2 * 18 - 28 - 120);
        pickerSupportable = maxPad >= MIN_PAD;
        padSize = pickerSupportable ? maxPad : MIN_PAD; // unused when unsupported
        padX = panelX + 12;
        padY = panelY + header + 16;

        hueW = 18;
        hueH = padSize;
        hueX = padX + padSize + 14;
        hueY = padY;

        alphaW = 18;
        alphaH = padSize;
        alphaX = hueX + hueW + 12;
        alphaY = padY;

        previewX = alphaX + alphaW + 20;
        previewY = padY;
        // The preview is a SQUARE sample (owner ruling, 2026-10-30 — was a
        // wide previewW×24 rectangle); the hex field keeps the right
        // column's full width, not the sample's.
        int rightColW = panelX + panelW - 12 - previewX;
        previewW = Math.max(24, Math.min(48, rightColW));
        previewH = previewW;

        int hexY = previewY + previewH + 22;
        if (pickerSupportable) {
            hexField = new EditBox(
                    this.font,
                    previewX, hexY, Math.min(96, Math.max(previewW, rightColW)), 14,
                    Component.literal("Hex")
            );
            hexField.setMaxLength(9);
            hexField.setValue(formatHex(currentArgb()));
            hexField.setResponder(this::onHexChanged);
            this.addRenderableWidget(hexField);
        } else {
            hexField = null;
        }

        // Actions: right-aligned inside the panel, primary rightmost (desktop
        // convention); the unsupported fallback keeps them centered at the bottom.
        int btnW = 64, btnH = bh;
        int btnY = pickerSupportable ? panelY + panelH - 8 - btnH : ch - 32;
        int applyX = pickerSupportable ? panelX + panelW - 12 - btnW : cw / 2 + 4;
        int cancelX = pickerSupportable ? applyX - 6 - btnW : cw / 2 - 4 - btnW;
        this.addRenderableWidget(ButtonWidget.semantic(
                cancelX, btnY, btnW, btnH,
                Component.literal("Cancel"),
                "Discards any edits and returns.",
                () -> com.aurora.client.adapter.NativeUi.setScreen(this.minecraft, parent)).glassBackground(true));

        this.addRenderableWidget(ButtonWidget.semantic(
                applyX, btnY, btnW, btnH,
                Component.literal("Apply"),
                "Applies the selected color and returns.",
                () -> {
                    onApply.accept(currentArgb());
                    com.aurora.client.adapter.NativeUi.setScreen(this.minecraft, parent);
                }).glassStyle(Button.GlassStyle.STAINED).primary(true));
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
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (this.minecraft != null && this.minecraft.level != null) return;
        com.aurora.client.ui.component.GlassSurface.renderMenuPanorama(this,g,delta);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Themed overlay dim (token-driven; dark in both modes by design).
        var radius = ThemeManager.current().roundness().radiusLarge();
        com.aurora.client.ui.component.GlassSurface.beginGlassPass();
        if (pickerSupportable) com.aurora.client.ui.component.GlassSurface.container(ctx,panelX,panelY,panelW,panelH,radius);
        com.aurora.client.ui.component.GlassSurface.overlayDim(ctx,this.width,this.height);
        if (pickerSupportable) MaterialSurface.parent(ctx,panelX,panelY,panelW,panelH,radius);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
        Font tr = this.font;
        // Text directly on the overlay uses ON_OVERLAY (light in both modes).
        int onOverlay = ThemeManager.color(ThemeToken.ON_OVERLAY);
        int labelCol = pickerSupportable ? ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY)
                : ThemeManager.withAlpha(onOverlay, 0x99);

        if (pickerSupportable) ctx.text(tr, AuroraFontRenderer.ellipsize(tr, this.title.getString(), panelW - 24, 0),
                panelX + 12, panelY + (com.aurora.client.ui.util.UiDensity.HEADER - tr.lineHeight) / 2 + 1, MaterialSurface.color(ThemeToken.ON_BACKGROUND), false);
        else AuroraFontRenderer.drawCentered(ctx, tr, this.title, this.width / 2, 20, onOverlay);

        // C-8 short-window graceful fail: below the supportable minimum the
        // editing surfaces are skipped (they could not fit without
        // overlapping the pinned actions or leaving the usable bounds);
        // Apply commits the current color, Cancel discards.
        if (!pickerSupportable) {
            AuroraFontRenderer.drawCentered(ctx, tr,
                    Component.literal("Window too small for the color picker"),
                    this.width / 2, this.height / 2 - 20, onOverlay);
            AuroraFontRenderer.drawCentered(ctx, tr,
                    Component.literal("Resize the window, or use Apply / Cancel below"),
                    this.width / 2, this.height / 2 - 8, labelCol);
            return;
        }

        int scale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
        UiLayerCache.drawSurface(ctx, padCache,
                scale * 31L ^ padSize ^ bits(hue), padX, padY, padSize, padSize,
                () -> drawPadInto(ctx, 0, 0));

        int markX = padX + (int)(sat * padSize);
        int markY = padY + (int)((1f - lit) * padSize);
        ctx.fill(markX - 1, padY, markX + 1, padY + padSize, 0x80000000);
        ctx.fill(padX, markY - 1, padX + padSize, markY + 1, 0x80000000);
        ctx.fill(markX - 3, markY - 3, markX + 3, markY + 3, 0xFFFFFFFF);
        ctx.fill(markX - 2, markY - 2, markX + 2, markY + 2, currentArgb() | 0xFF000000);

        UiLayerCache.drawSurface(ctx, hueCache,
                scale * 31L ^ hueW * 7919L ^ hueH * 17L, hueX, hueY, hueW, hueH,
                () -> drawHueInto(ctx, 0, 0));
        int hueMark = hueY + (int)(hue * hueH);
        ctx.fill(hueX - 2, hueMark - 1, hueX + hueW + 2, hueMark + 1, 0xFFFFFFFF);

        UiLayerCache.drawSurface(ctx, alphaCache,
                scale * 31L ^ bits(hue) * 31L ^ bits(sat) * 17L ^ bits(lit), alphaX, alphaY, alphaW, alphaH,
                () -> drawAlphaInto(ctx, 0, 0));
        int alphaMark = alphaY + (int)((1f - alpha) * alphaH);
        ctx.fill(alphaX - 2, alphaMark - 1, alphaX + alphaW + 2, alphaMark + 1, 0xFFFFFFFF);

        UiLayerCache.drawSurface(ctx, previewCache,
                scale * 31L ^ previewW * 7919L ^ previewH * 17L, previewX, previewY, previewW, previewH,
                () -> drawCheckerboard(ctx, 0, 0, previewW, previewH));
        ctx.fill(previewX, previewY, previewX + previewW, previewY + previewH, currentArgb());
        // Content-frame white on the preview — structural neutral (see class
        // javadoc). The PREVIEW is a represented-color sample like every
        // ColorSetting swatch: its clipping follows theme roundness when
        // rectangular (C-7 — was a literal 4 that ignored Square mode; the
        // ColorSwatch min(h/2, radiusSmall) pattern). The pad/hue/alpha
        // strips follow the same small radius (owner ruling 2026-11-12: the
        // Corner Radius slider applies to every shape).
        RenderUtil.drawRoundedOutlineAA(ctx, previewX, previewY, previewW, previewH,
                Math.min(previewH / 2f, ThemeManager.current().roundness().radiusSmall()), 1.0f, 0xFFFFFFFF);

        // Strip captions centered over their strips; field captions share the content column.
        AuroraFontRenderer.drawCentered(ctx, tr, Component.literal("Hue"), hueX + hueW / 2, hueY - 12, labelCol);
        AuroraFontRenderer.drawCentered(ctx, tr, Component.literal("Alpha"), alphaX + alphaW / 2, alphaY - 12, labelCol);
        ctx.text(tr, Component.literal("Preview"), previewX, previewY - 12, labelCol, false);
        ctx.text(tr, Component.literal("Hex"), previewX, previewY + previewH + 10, labelCol, false);

        // (No bespoke hex-field outline: the hex EditBox is themed end-to-end by
        // the mod-wide EditBoxMixin — fill, border, text and caret — through
        // this screen's ThemedScreen marker. One implementation, no overlay.)
    }

    /** Version helper: stable key contribution for a float. */
    private static long bits(float f) { return Float.floatToIntBits(f) & 0xFFFFFFFFL; }

    /** Current GUI scale as an int ≥ 1. */
    private static int guiScale() {
        return Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
    }

    /**
     * Saturation/lightness pad at local coordinates — PER-DEVICE-PIXEL
     * gradient (2026-10-30): every physical pixel samples the exact HSL
     * value at its own center (was a 40×40 grid of GUI-pixel cells, which
     * read as blocky bands at every GUI scale). The gradient is the data
     * being edited, so it is written with {@link UiLayerCache#writeArgb};
     * only the structural white frame goes through the capture sink.
     */
    private void drawPadInto(GuiGraphicsExtractor ctx, int ox, int oy) {
        int scale = guiScale();
        int dev = padSize * scale;
        padCache.writeArgb((px, py) -> ColorEntryHelper.hslaToArgb(hue,
                (px + 0.5f) / dev, 1f - (py + 0.5f) / dev, 1f));
        // Rounded data-surface corners at the theme's small radius
        // (ColorSwatch's clipRounded discipline — same silhouette as the
        // outline drawn on top below).
        int radius = stripRadius();
        padCache.clipRounded(0, 0, dev, dev, radius * scale);
        // Content-frame white around the pad — structural neutral (see class
        // javadoc).
        RenderUtil.drawRoundedOutlineAA(ctx, ox, oy, padSize, padSize, radius, 1.0f, 0xFFFFFFFF);
    }

    /** Hue strip at local coordinates — one exact hue per device pixel row. */
    private void drawHueInto(GuiGraphicsExtractor ctx, int ox, int oy) {
        int scale = guiScale();
        int devW = hueW * scale, devH = hueH * scale;
        hueCache.writeArgb((px, py) -> ColorEntryHelper.hslaToArgb((py + 0.5f) / devH, 1f, 0.5f, 1f));
        int radius = stripRadius();
        hueCache.clipRounded(0, 0, devW, devH, radius * scale);
        // Content-frame white around the hue strip — structural neutral.
        RenderUtil.drawRoundedOutlineAA(ctx, ox, oy, hueW, hueH, radius, 1.0f, 0xFFFFFFFF);
    }

    /** Alpha strip (checkerboard + gradient) at local coordinates — the
     *  gradient is composited over the checker per device pixel. */
    private void drawAlphaInto(GuiGraphicsExtractor ctx, int ox, int oy) {
        int scale = guiScale();
        int devW = alphaW * scale, devH = alphaH * scale, cell = 4 * scale;
        alphaCache.writeArgb((px, py) -> {
            int bg = ((px / cell) + (py / cell)) % 2 == 0 ? 0xFF555555 : 0xFFAAAAAA;
            return compositeOver(ColorEntryHelper.hslaToArgb(hue, sat, lit, 1f - (py + 0.5f) / devH), bg);
        });
        int radius = stripRadius();
        alphaCache.clipRounded(0, 0, devW, devH, radius * scale);
        // Content-frame white around the alpha strip — structural neutral.
        RenderUtil.drawRoundedOutlineAA(ctx, ox, oy, alphaW, alphaH, radius, 1.0f, 0xFFFFFFFF);
    }

    /** Corner radius of the pad and strips: the theme's small radius (6 at the default). */
    private static int stripRadius() {
        return ThemeManager.current().roundness().radiusSmall();
    }

    /** Source-over composite of {@code src} (ARGB) over opaque {@code bg} —
     *  the same blend the GUI applies to translucent fills. */
    private static int compositeOver(int src, int bg) {
        int sa = (src >>> 24) & 0xFF;
        if (sa == 255) return src;
        int inv = 255 - sa;
        int r = ((src >>> 16) & 0xFF) * sa + ((bg >>> 16) & 0xFF) * inv;
        int g = ((src >>> 8) & 0xFF) * sa + ((bg >>> 8) & 0xFF) * inv;
        int b = (src & 0xFF) * sa + (bg & 0xFF) * inv;
        return 0xFF000000 | ((r / 255) << 16) | ((g / 255) << 8) | (b / 255);
    }

    private void drawCheckerboard(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
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
        MaterialSurface.disposeTemplates();
        padCache.dispose();
        hueCache.dispose();
        alphaCache.dispose();
        previewCache.dispose();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent _ev, boolean _doubleClicked) {
        double mouseX = _ev.x(); double mouseY = _ev.y(); int button = _ev.button();
        // Editing surfaces exist only in the supportable configuration
        // (C-8) — below the minimum there is nothing to drag; the buttons
        // are vanilla children and keep working.
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && pickerSupportable) {
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
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && pickerSupportable && dragging != DragTarget.NONE) {
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
        com.aurora.client.adapter.NativeUi.setScreen(this.minecraft, parent);
    }
}
