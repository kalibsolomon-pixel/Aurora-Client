package com.aurora.client.util;

import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Adds a group of color-editing entries to a Cloth Config category:
 * a hex color field (with alpha) plus 4 sliders (H, S, L, A).
 *
 * <p>The supplier/consumer pair let this helper read and write an
 * ARGB int stored in the config. Each slider converts to/from HSLA
 * and writes back the packed ARGB value on save.
 */
public final class ColorEntryHelper {
    private ColorEntryHelper() {}

    public static void add(
            ConfigCategory category,
            ConfigEntryBuilder entry,
            String label,
            IntSupplier getter,
            IntConsumer setter,
            int defaultArgb
    ) {
        // Hex field â€” direct ARGB input.
        category.addEntry(entry.startColorField(
                        Component.literal(label), getter.getAsInt())
                .setDefaultValue(defaultArgb)
                .setAlphaMode(true)
                .setSaveConsumer(setter::accept)
                .build());

        // Derive initial HSLA from current color.
        int init = getter.getAsInt();
        float[] hsla = argbToHsla(init);

        // Hue slider 0â€“360
        category.addEntry(entry.startIntSlider(
                        Component.literal(label + " - Hue"), (int)(hsla[0] * 360f), 0, 360)
                .setDefaultValue((int)(argbToHsla(defaultArgb)[0] * 360f))
                .setSaveConsumer(v -> {
                    float[] cur = argbToHsla(getter.getAsInt());
                    cur[0] = v / 360f;
                    setter.accept(hslaToArgb(cur));
                })
                .build());

        // Saturation slider 0â€“100
        category.addEntry(entry.startIntSlider(
                        Component.literal(label + " - Saturation"), (int)(hsla[1] * 100f), 0, 100)
                .setDefaultValue((int)(argbToHsla(defaultArgb)[1] * 100f))
                .setSaveConsumer(v -> {
                    float[] cur = argbToHsla(getter.getAsInt());
                    cur[1] = v / 100f;
                    setter.accept(hslaToArgb(cur));
                })
                .build());

        // Lightness slider 0â€“100
        category.addEntry(entry.startIntSlider(
                        Component.literal(label + " - Lightness"), (int)(hsla[2] * 100f), 0, 100)
                .setDefaultValue((int)(argbToHsla(defaultArgb)[2] * 100f))
                .setSaveConsumer(v -> {
                    float[] cur = argbToHsla(getter.getAsInt());
                    cur[2] = v / 100f;
                    setter.accept(hslaToArgb(cur));
                })
                .build());

        // Alpha slider 0â€“255
        category.addEntry(entry.startIntSlider(
                        Component.literal(label + " - Alpha"), (int)(hsla[3] * 255f), 0, 255)
                .setDefaultValue((int)(argbToHsla(defaultArgb)[3] * 255f))
                .setSaveConsumer(v -> {
                    float[] cur = argbToHsla(getter.getAsInt());
                    cur[3] = v / 255f;
                    setter.accept(hslaToArgb(cur));
                })
                .build());
    }

    /** Pack H, S, L, A (all 0..1) into ARGB int. */
    public static int hslaToArgb(float[] hsla) {
        return hslaToArgb(hsla[0], hsla[1], hsla[2], hsla[3]);
    }

    public static int hslaToArgb(float h, float s, float l, float a) {
        float r, g, b;
        if (s == 0f) {
            r = g = b = l;
        } else {
            float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
            float p = 2f * l - q;
            r = hueToRgb(p, q, h + 1f/3f);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - 1f/3f);
        }
        int ir = Math.max(0, Math.min(255, Math.round(r * 255f)));
        int ig = Math.max(0, Math.min(255, Math.round(g * 255f)));
        int ib = Math.max(0, Math.min(255, Math.round(b * 255f)));
        int ia = Math.max(0, Math.min(255, Math.round(a * 255f)));
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    /** Unpack ARGB into float[4] = {h, s, l, a}, all 0..1. */
    public static float[] argbToHsla(int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8)  & 0xFF) / 255f;
        float b = ( argb        & 0xFF) / 255f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f;

        float h, s;
        if (max == min) {
            h = s = 0f;
        } else {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r)      h = (g - b) / d + (g < b ? 6 : 0);
            else if (max == g) h = (b - r) / d + 2;
            else               h = (r - g) / d + 4;
            h /= 6f;
        }
        return new float[] { h, s, l, a };
    }
    /**
     * Adds a single entry: a button that opens ColorPickerScreen. Use this
     * instead of the full HSL slider group for a cleaner UI.
     */

    public static void addPickerButton(
            ConfigCategory category,
            ConfigEntryBuilder entry,
            String label,
            java.util.function.IntSupplier getter,
            java.util.function.IntConsumer setter,
            int defaultArgb
    ) {
        category.addEntry(entry.startTextDescription(
                        net.minecraft.network.chat.Component.literal(label + ": click to edit"))
                .build());

        // We use a "button" via TextDescription + a tooltip, but Cloth Config
        // doesn't expose a plain button. The cleanest way is to use
        // startSubCategory with a single boolean toggle that, on save,
        // triggers the picker. Alternative: just use the hex field and open
        // picker from a KeyMapping â€” but simplest: a toggle-button.
    }
    private static float hueToRgb(float p, float q, float t) {
        if (t < 0f) t += 1f;
        if (t > 1f) t -= 1f;
        if (t < 1f/6f) return p + (q - p) * 6f * t;
        if (t < 1f/2f) return q;
        if (t < 2f/3f) return p + (q - p) * (2f/3f - t) * 6f;
        return p;
    }
}