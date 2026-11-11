package com.aurora.client.util;

/**
 * ARGB ↔ HSLA conversion helpers shared by the color picker screen and
 * the color settings widget. (Originally a Cloth Config entry builder;
 * that integration never shipped and was removed — audit D9.)
 */
public final class ColorEntryHelper {
    private ColorEntryHelper() {}

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

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0f) t += 1f;
        if (t > 1f) t -= 1f;
        if (t < 1f/6f) return p + (q - p) * 6f * t;
        if (t < 1f/2f) return q;
        if (t < 2f/3f) return p + (q - p) * (2f/3f - t) * 6f;
        return p;
    }
}
