package com.aurora.client.screen.setting;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Slider for the theme's panel-background <b>opacity</b> (alpha only — hue
 * and lightness are untouched): a {@link ThemeSliderSetting} over 0..1 in
 * 1% steps with a percent readout. The drag-aware commit discipline and the
 * row's rendering live in the base class.
 */
public class ThemeOpacitySetting extends ThemeSliderSetting {

    private static final double STEP = 0.01;

    public ThemeOpacitySetting(String label, DoubleSupplier getter, DoubleConsumer setter) {
        super(label, getter, setter, 0.0, 1.0, STEP, false);
        percent();
    }

    @Override public ThemeOpacitySetting description(String desc) { super.description(desc); return this; }
    @Override public ThemeOpacitySetting description(java.util.function.Supplier<String> desc) { super.description(desc); return this; }
}
