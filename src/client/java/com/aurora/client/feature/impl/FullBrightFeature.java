package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

/**
 * Full Bright via gamma override. Vanilla caps gamma at 1.0; we bypass that
 * by directly writing the backing value (via SimpleOptionMixin) when our
 * feature is enabled, and restore the user's original gamma on disable.
 *
 * <p>The target gamma is configurable via {@code cfg.fullBrightGamma}, a
 * percentage (100..1500). 100% = vanilla full-bright (gamma 1.0), 500% =
 * the legacy hardcoded value, 1500% = brightest available. The slider
 * stores percent rather than the raw double so the UI can display whole
 * numbers and keep the IntSliderSetting widget reusable.
 */
public class FullBrightFeature implements Feature {
    public static final String ID = "full_bright";

    @Override public String id() { return ID; }

    private boolean wasEnabled = false;
    private double savedGamma = 1.0;

    @Override public void onRegister() {}

    @Override
    public void onTick(Minecraft client) {
        if (client == null || client.options == null) return;
        AuroraConfig cfg = AuroraConfig.get();

        OptionInstance<Double> gamma = client.options.gamma();
        double currentGamma = gamma.get();
        double targetGamma = Math.max(100, cfg.fullBrightGamma) / 100.0;

        if (cfg.fullBright) {
            if (!wasEnabled) {
                savedGamma = currentGamma;
                wasEnabled = true;
            }
            // Re-apply on every tick: handles slider changes mid-flight,
            // and re-asserts our value if vanilla or another mod has
            // written gamma since last tick.
            if (Math.abs(currentGamma - targetGamma) > 1e-6) {
                @SuppressWarnings("unchecked")
                com.aurora.client.mixin.SimpleOptionMixin<Double> forced =
                        (com.aurora.client.mixin.SimpleOptionMixin<Double>) (Object) gamma;
                forced.aurora$forceSetValue(targetGamma);
            }
        } else if (wasEnabled) {
            @SuppressWarnings("unchecked")
            com.aurora.client.mixin.SimpleOptionMixin<Double> forced =
                    (com.aurora.client.mixin.SimpleOptionMixin<Double>) (Object) gamma;
            forced.aurora$forceSetValue(savedGamma);
            wasEnabled = false;
        }
    }
}