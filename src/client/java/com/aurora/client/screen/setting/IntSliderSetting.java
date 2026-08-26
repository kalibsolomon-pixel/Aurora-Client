package com.aurora.client.screen.setting;

import com.aurora.client.ui.component.Slider;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class IntSliderSetting extends FeatureSetting {
    private static final int CONTROL_H = 36;
    /** iOS UISlider track is 4 pt; we use 4 px which reads identically at the GUI's typical scales. */
    private static final int TRACK_H = 4;
    /** iOS UISlider thumb is 28 pt; this radius (=7) gives a 14 px thumb at GUI scale 2 → 28 device pixels. */
    private static final int KNOB_R = 7;

    private final IntSupplier getter;
    private final IntConsumer setter;
    private final int min, max;
    private int lastTrackX, lastTrackW;
    private int lastWidth = 240;

    private int cachedValue = Integer.MIN_VALUE;
    private String cachedValueStr = "";
    private int cachedValueStrW = 0;

    /** Shared themed slider — owns the track/fill/knob drawing + drag/key interaction. */
    private final Slider slider;

    public IntSliderSetting(String label, IntSupplier getter, IntConsumer setter, int min, int max) {
        super(label);
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
        this.slider = new Slider(getter::getAsInt, v -> setter.accept((int) Math.round(v)), min, max, 1);
    }

    @Override public IntSliderSetting description(String desc) { super.description(desc); return this; }
    @Override public IntSliderSetting description(Supplier<String> desc) { super.description(desc); return this; }

    @Override public int baseHeight() { return CONTROL_H; }
    @Override public int height() { return CONTROL_H + descriptionHeight(lastWidth); }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        Font tr = Minecraft.getInstance().font;
        int value = getter.getAsInt();
        boolean disabled = isDisabled();

        renderLabelWithTooltip(ctx, label, x + 12, y + 6, AuroraTheme.IOS_LABEL, mouseX, mouseY, disabled);

        if (value != cachedValue) {
            cachedValueStr = Integer.toString(value);
            cachedValueStrW = tr.width(cachedValueStr);
            cachedValue = value;
        }

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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY, int rowX, int rowY, int rowWidth) {
        return slider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
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