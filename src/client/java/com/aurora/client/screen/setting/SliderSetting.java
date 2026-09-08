package com.aurora.client.screen.setting;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.component.Slider;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * One labeled slider row — the single parameterized implementation behind
 * every slider setting (integer or double range, optional percent readout).
 * Track/fill/knob drawing and drag/key interaction live in the shared
 * {@link Slider} control; this class owns the row chrome: the label with
 * its description tooltip, the right-aligned value readout (cached by
 * value), focus claiming, and config persistence at commit points
 * (release / consumed key press).
 *
 * <p>Pixel/layout facts inherited from the widgets this consolidates: the
 * row is 36 px tall, the label sits at {@code (x+12, y+6)}, the value
 * readout is right-aligned 14 px from the row's right edge, and the track
 * spans the row inset 12 px per side ({@link #trackInset()} total; the
 * theme-opacity slider keeps its own historical 26 px inset).
 *
 * <p>{@link ThemeOpacitySetting} extends this row with drag-aware
 * throttled live-apply: it reuses the drawing pieces and the value cache
 * but owns its interaction and splits rendering across the cacheable shape
 * layer and the live overlay.
 */
public class SliderSetting extends FeatureSetting {

    protected static final int CONTROL_H = 36;
    private static final double STEP = 0.01;

    /** Shared themed slider — owns the track/fill/knob drawing + drag/key interaction. */
    protected final Slider slider;

    /** Track geometry from the last layout — hit-testing for subclasses with custom interaction. */
    protected int lastTrackX, lastTrackW;
    protected int lastWidth = 240;

    private final boolean intMode;
    private boolean percent = false;

    private long cachedKey = Long.MIN_VALUE;
    private String cachedStr = "";
    private int cachedStrW = 0;

    /**
     * Integer-range slider row. A factory (not an overloaded constructor —
     * {@code IntSupplier}/{@code DoubleSupplier} lambdas are both
     * applicable to int getters and make the overloads ambiguous) so call
     * sites state their range type.
     */
    public static SliderSetting ofInt(String label, IntSupplier getter, IntConsumer setter, int min, int max) {
        Slider slider = new Slider(getter::getAsInt, v -> setter.accept((int) Math.round(v)), min, max, 1);
        return new SliderSetting(label, slider, true);
    }

    /** Double-range slider row (two-decimal readout; see {@link #percent()}). */
    public static SliderSetting of(String label, DoubleSupplier getter, DoubleConsumer setter, double min, double max) {
        return new SliderSetting(label, getter, setter, min, max, false);
    }

    private SliderSetting(String label, Slider slider, boolean intMode) {
        super(label);
        this.intMode = intMode;
        this.slider = slider;
    }

    SliderSetting(String label, DoubleSupplier getter, DoubleConsumer setter, double min, double max, boolean intMode) {
        super(label);
        this.intMode = intMode;
        this.slider = createSlider(getter, setter, min, max);
    }

    /**
     * Builds the renderer's slider for double-range rows. Subclass hook:
     * {@link ThemeOpacitySetting} swaps in a pending-aware display getter.
     * Runs during construction, so overrides must not eagerly read subclass
     * state — capturing it inside the returned slider's lambdas is fine
     * (they evaluate at render time).
     */
    protected Slider createSlider(DoubleSupplier getter, DoubleConsumer setter, double min, double max) {
        return new Slider(getter, setter, min, max, STEP);
    }
    @Override public SliderSetting description(String desc) { super.description(desc); return this; }
    @Override public SliderSetting description(Supplier<String> desc) { super.description(desc); return this; }

    /**
     * Render the value readout as a percentage (value * 100, rounded, with "%")
     * instead of the raw two-decimal form. Double ranges only; ignored for ints.
     */
    public SliderSetting percent() { this.percent = true; this.cachedKey = Long.MIN_VALUE; return this; }

    @Override public int baseHeight() { return CONTROL_H; }
    @Override public int height() { return CONTROL_H + descriptionHeight(lastWidth); }

    // ------------------------------------------------------------------
    //  Row pieces — shared by render() and the shapes/overlay split a
    //  subclass declares for the owning screen's static-layer cache.
    // ------------------------------------------------------------------

    /** Total horizontal inset of the track from the row (both sides combined). */
    protected int trackInset() { return 24; }

    private void layoutTrack(int x, int y, int width) {
        lastWidth = width;
        int trackX = x + 12;
        int trackW = width - trackInset();
        lastTrackX = trackX;
        lastTrackW = trackW;
        slider.layout(trackX, y, trackW, CONTROL_H);
    }

    /** Label + cached value readout. Reads the value through the slider's getter. */
    protected void drawLabelRow(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        Font tr = Minecraft.getInstance().font;
        boolean disabled = isDisabled();
        double value = slider.value();

        renderLabelWithTooltip(ctx, label, x + 12, y + 6, AuroraTheme.IOS_LABEL, mouseX, mouseY, disabled);

        long key = valueKey(value);
        if (key != cachedKey) {
            cachedStr = valueText(value);
            cachedStrW = tr.width(cachedStr);
            cachedKey = key;
        }

        // Highlight the value in accent when this slider holds focus, so the
        // user can see at a glance which slider scroll/keys go to.
        boolean focused = (!disabled && FeatureSetting.getFocused() == this);
        int valueColor = disabled ? AuroraTheme.TEXT_DIM : (focused ? AuroraTheme.IOS_BLUE : AuroraTheme.IOS_SECONDARY_LABEL);
        ctx.drawString(tr, cachedStr, x + width - cachedStrW - 14, y + 6, valueColor, false);
    }

    /** Cacheable static track layer (depends only on the roundness token). */
    protected void drawTrackShapes(GuiGraphics ctx, int x, int y, int width) {
        layoutTrack(x, y, width);
        slider.disabled(isDisabled());
        slider.renderShapes(ctx, lastTrackX, y, lastTrackW, CONTROL_H);
    }

    /** Live track layer: fill, knob, hover ring — tracks value and hover. */
    protected void drawTrackOverlay(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        layoutTrack(x, y, width);
        slider.disabled(isDisabled());
        slider.renderOverlay(ctx, lastTrackX, y, lastTrackW, CONTROL_H, mouseX, mouseY);
    }

    private long valueKey(double v) {
        return intMode ? (long) v : Math.round(v * 100.0);
    }

    private String valueText(double v) {
        if (intMode) return Integer.toString((int) v);
        return percent ? Math.round(v * 100.0) + "%" : String.format("%.2f", v);
    }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        drawLabelRow(ctx, x, y, width, mouseX, mouseY);
        drawTrackShapes(ctx, x, y, width);
        drawTrackOverlay(ctx, x, y, width, mouseX, mouseY);
    }

    // ------------------------------------------------------------------
    //  Interaction — delegated to the shared slider; focus is claimed on
    //  grab and the config persists at commit points only.
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (isDisabled()) return false;
        boolean handled = slider.mouseClicked(mouseX, mouseY, button);
        if (handled) requestFocus();
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY, int rowX, int rowY, int rowWidth) {
        return slider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (slider.mouseReleased(mouseX, mouseY, button)) {
            AuroraConfig.save();
            return true;
        }
        return false;
    }

    @Override
    public boolean onScroll(double vertical) {
        return false;
    }

    @Override
    public boolean onKeyPress(int keyCode, int modifiers) {
        if (isDisabled()) return false;
        if (slider.onKeyPress(keyCode, modifiers)) {
            AuroraConfig.save();
            return true;
        }
        return false;
    }
}
