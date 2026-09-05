package com.aurora.client.screen.setting;

import com.aurora.client.ui.component.Slider;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public class DoubleSliderSetting extends FeatureSetting {
    private static final int CONTROL_H = 36;
    /** iOS UISlider track is 4 pt; reads identical at typical GUI scales. */
    private static final int TRACK_H = 4;
    /** iOS thumb radius (=7 → 14 px diameter). */
    private static final int KNOB_R = 7;
    private static final double STEP = 0.01;

    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final double min, max;
    /**
     * When true the value readout is rendered as a percentage
     * ({@code Math.round(value * 100)} + "%") instead of the default
     * two-decimal raw form. Useful for normalized 0..1 sliders where
     * "75%" is more intuitive than "0.75".
     */
    private boolean percent = false;
    private int lastTrackX, lastTrackW;
    private int lastWidth = 240;

    private long cachedValueKey = Long.MIN_VALUE;
    private String cachedValueStr = "";
    private int cachedValueStrW = 0;

    /** Shared themed slider — owns the track/fill/knob drawing + drag/key interaction. */
    private final Slider slider;

    public DoubleSliderSetting(String label, DoubleSupplier getter, DoubleConsumer setter,
                               double min, double max) {
        super(label);
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
        this.slider = new Slider(getter, setter, min, max, STEP);
    }

    @Override public DoubleSliderSetting description(String desc) { super.description(desc); return this; }
    @Override public DoubleSliderSetting description(Supplier<String> desc) { super.description(desc); return this; }

    /** Render the value readout as a percentage (value * 100, rounded, with "%"). */
    public DoubleSliderSetting percent() { this.percent = true; this.cachedValueKey = Long.MIN_VALUE; return this; }

    @Override public int baseHeight() { return CONTROL_H; }
    @Override public int height() { return CONTROL_H + descriptionHeight(lastWidth); }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        Font tr = Minecraft.getInstance().font;
        double value = getter.getAsDouble();
        boolean disabled = isDisabled();

        renderLabelWithTooltip(ctx, label, x + 12, y + 6, AuroraTheme.IOS_LABEL, mouseX, mouseY, disabled);

        long key = Math.round(value * 100.0);
        if (key != cachedValueKey) {
            cachedValueStr = percent
                    ? Math.round(value * 100.0) + "%"
                    : String.format("%.2f", value);
            cachedValueStrW = tr.width(cachedValueStr);
            cachedValueKey = key;
        }

        // Highlight value Component in accent color when this slider holds focus,
        // so the user can see at a glance which slider scroll/keys go to.
        boolean focused = (!disabled && FeatureSetting.getFocused() == this);
        int valueColor = disabled ? AuroraTheme.TEXT_DIM : (focused ? AuroraTheme.IOS_BLUE : AuroraTheme.IOS_SECONDARY_LABEL);
        ctx.drawString(tr, cachedValueStr,
                x + width - cachedValueStrW - 14, y + 6, valueColor, false);

        int trackX = x + 12;
        int trackW = width - 24;
        lastTrackX = trackX;
        lastTrackW = trackW;

        slider.disabled(disabled);
        slider.layout(trackX, y, trackW, CONTROL_H);
        slider.renderShapes(ctx, trackX, y, trackW, CONTROL_H);
        slider.renderOverlay(ctx, trackX, y, trackW, CONTROL_H, mouseX, mouseY);

        renderDescription(ctx, x, y + CONTROL_H, width);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (isDisabled()) return false;
        boolean handled = slider.mouseClicked(mouseX, mouseY, button);
        if (handled) requestFocus();
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy, int rowX, int rowY, int rowWidth) {
        return slider.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (slider.mouseReleased(mouseX, mouseY, button)) {
            com.aurora.client.config.AuroraConfig.save();
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
            com.aurora.client.config.AuroraConfig.save();
            return true;
        }
        return false;
    }
}