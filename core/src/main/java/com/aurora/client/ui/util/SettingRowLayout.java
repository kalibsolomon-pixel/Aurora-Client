package com.aurora.client.ui.util;

import java.util.List;
import com.aurora.client.port.TextMetrics;

/** Immutable, pre-paint row measurement shared by layout, paint and pointer routing. */
public record SettingRowLayout(int height, List<String> lines, UiDensity.Rect label,
                               UiDensity.Rect control, UiDensity.Rect value, boolean inline) {
    public enum Kind { BOOLEAN, SLIDER, ENUM, KEYBIND, COLOR, BUTTON, ACTION, SECTION }
    /** Slider value readout column. */
    public static final int VALUE_W = 36;
    /** A colour row's hex value column, wide enough for {@code #RRGGBBAA}. */
    public static final int HEX_W = 50;
    /** Track inset for the knob's radius, so a knob at either end lands on the row's control edge,
     * the same right edge every toggle, field and swatch ends on (UI polish 2026-11-14; was 8). */
    public static final int KNOB_RESERVE = 5;
    public static SettingRowLayout measure(Kind kind, String text, int width, int lineHeight,
                                           TextMetrics measure) {
        int pad = 8, gap = 6;
        int controlW = switch (kind) {
            case BOOLEAN -> UiDensity.TOGGLE_W; case ENUM -> UiDensity.ENUM_W; case KEYBIND, BUTTON -> UiDensity.KEY_W;
            case ACTION -> UiDensity.ACTION_W;
            case COLOR -> UiDensity.SWATCH_W; case SLIDER -> 152; case SECTION -> 0;
        };
        int controlH = switch (kind) {
            case BOOLEAN -> UiDensity.TOGGLE_H; case COLOR -> UiDensity.SWATCH_H; default -> UiDensity.FIELD_H;
        };
        int labelW = Math.max(1, width - 2 * pad - controlW - gap);
        boolean inline = true;
        int h = UiDensity.SIMPLE_ROW;
        UiDensity.Rect value = new UiDensity.Rect(0, 0, 0, 0);
        if (kind == Kind.SECTION) labelW = Math.max(1, width - 16);
        // A colour row prints its value (#RRGGBB, or #RRGGBBAA) just before the swatch (2026-11-14).
        if (kind == Kind.COLOR) labelW = Math.max(1, labelW - HEX_W - gap);
        if (kind == Kind.SLIDER) {
            labelW = Math.min(112, Math.max(1, width / 3));
            // Reserve the unchanged 7px knob and its AA edge inside the host clip.
            controlW = width - 2 * pad - labelW - 2 * gap - VALUE_W - 8;
            // One slider row everywhere (UI polish 2026-11-14): label and value on one line, the
            // full-width track beneath. Inline rows survived only on wide windows with short
            // labels, so one page mixed two layouts and pages disagreed with each other.
            inline = false;
            h = inline ? UiDensity.INLINE_SLIDER : UiDensity.STACKED_ROW;
            if (!inline) labelW = Math.max(1, width - 2 * pad - gap - VALUE_W);
            value = new UiDensity.Rect(inline ? pad + labelW + gap : width - pad - VALUE_W,
                    0, VALUE_W, inline ? h : 16);
        }
        labelW = Math.max(1, labelW - 4); // previous design's 12px text inset
        List<String> lines = WordWrap.lines(text, labelW, 2, measure);
        if (lines.size() > 1) h = Math.max(h, Math.max(UiDensity.STACKED_ROW, lines.size() * (lineHeight + 1) + 4));
        int labelH = kind == Kind.SLIDER && !inline ? h - 12 : h;
        UiDensity.Rect label = new UiDensity.Rect(pad + 4, 0, labelW, labelH);
        UiDensity.Rect control = kind == Kind.SLIDER
                ? new UiDensity.Rect(inline ? width - pad - KNOB_RESERVE - controlW : pad + KNOB_RESERVE, inline ? h / 2 - 2 : h - 9,
                    inline ? controlW : Math.max(1, width - 2 * pad - 2 * KNOB_RESERVE), 4)
                : new UiDensity.Rect(Math.max(pad, width - pad - controlW), (h - controlH) / 2, controlW, controlH);
        return new SettingRowLayout(h, List.copyOf(lines), label, control, value, inline);
    }

    /** Reserve live status/credit content without changing control dimensions. */
    public SettingRowLayout withSubtitle(int subtitleHeight, int lineHeight) {
        if (subtitleHeight <= 0) return this;
        int h = Math.max(height, lines.size() * (lineHeight + 1) + subtitleHeight + 4);
        return new SettingRowLayout(h, lines, new UiDensity.Rect(label.x(), 0, label.width(), h),
                new UiDensity.Rect(control.x(), (h - control.height()) / 2, control.width(), control.height()), value, inline);
    }

    /** A row-high (≥18px) pointer target around the control visual. */
    public UiDensity.Rect controlHit() {
        int h = Math.max(UiDensity.SIMPLE_ROW, control.height());
        return new UiDensity.Rect(control.x(), Math.max(0, control.y() - (h - control.height()) / 2), control.width(), h);
    }
}
