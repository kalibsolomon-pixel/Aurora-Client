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
    /** Slider track lengths (owner direction 2026-11-14): a dropdown's width, and half that for a
     * slider with at most {@link #SHORT_STEPS} positions. A track never spans the row. */
    public static final int TRACK_W = UiDensity.ENUM_W, SHORT_TRACK_W = UiDensity.ENUM_W / 2, SHORT_STEPS = 10;

    /** {@link #TRACK_W}, or {@link #SHORT_TRACK_W} for a slider with few positions. */
    public static int trackWidth(int steps) { return steps > 0 && steps <= SHORT_STEPS ? SHORT_TRACK_W : TRACK_W; }

    public static SettingRowLayout measure(Kind kind, String text, int width, int lineHeight,
                                           TextMetrics measure) {
        return measure(kind, text, width, lineHeight, measure, TRACK_W);
    }

    /** {@code trackW}: a slider's track length ({@link #trackWidth}); other kinds ignore it. */
    public static SettingRowLayout measure(Kind kind, String text, int width, int lineHeight,
                                           TextMetrics measure, int trackW) {
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
            // One inline slider row everywhere (owner direction 2026-11-14): the label, then the
            // value right-aligned against a bounded track whose knob ends on the control edge
            // switches and fields share. A full-width track was a long line with little meaning,
            // most of all for a slider with a handful of positions.
            inline = true;
            controlW = Math.max(1, Math.min(trackW, width - 2 * pad - 2 * KNOB_RESERVE - VALUE_W - 2 * gap));
            h = UiDensity.INLINE_SLIDER;
            int valueX = width - pad - KNOB_RESERVE - controlW - KNOB_RESERVE - gap - VALUE_W;
            labelW = Math.max(1, valueX - gap - pad);
            value = new UiDensity.Rect(valueX, 0, VALUE_W, h);
        }
        labelW = Math.max(1, labelW - 4); // previous design's 12px text inset
        List<String> lines = WordWrap.lines(text, labelW, 2, measure);
        if (lines.size() > 1) h = Math.max(h, Math.max(UiDensity.STACKED_ROW, lines.size() * (lineHeight + 1) + 4));
        int labelH = h;
        UiDensity.Rect label = new UiDensity.Rect(pad + 4, 0, labelW, labelH);
        if (kind == Kind.SLIDER && h > UiDensity.INLINE_SLIDER) value = new UiDensity.Rect(value.x(), 0, value.width(), h);
        UiDensity.Rect control = kind == Kind.SLIDER
                ? new UiDensity.Rect(width - pad - KNOB_RESERVE - controlW, h / 2 - 2, controlW, 4)
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
