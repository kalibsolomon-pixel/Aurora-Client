package com.aurora.client.ui.util;

import java.util.List;
import java.util.function.ToIntFunction;

/** Immutable, pre-paint row measurement shared by layout, paint and pointer routing. */
public record SettingRowLayout(int height, List<String> lines, UiDensity.Rect label,
                               UiDensity.Rect control, UiDensity.Rect value, boolean inline) {
    public enum Kind { BOOLEAN, SLIDER, ENUM, KEYBIND, COLOR, BUTTON, SECTION }
    public static SettingRowLayout measure(Kind kind, String text, int width, int lineHeight,
                                           ToIntFunction<String> measure) {
        int pad = 8, gap = 8;
        int controlW = switch (kind) {
            case BOOLEAN -> 28; case ENUM -> 100; case KEYBIND, BUTTON -> 90;
            case COLOR -> 40; case SLIDER -> 152; case SECTION -> 0;
        };
        int controlH = switch (kind) { case BOOLEAN -> 15; case COLOR -> 16; default -> 18; };
        int labelW = Math.max(1, width - 2 * pad - controlW - gap);
        boolean inline = true;
        int h = UiDensity.SIMPLE_ROW;
        UiDensity.Rect value = new UiDensity.Rect(0, 0, 0, 0);
        if (kind == Kind.SECTION) labelW = Math.max(1, width - 16);
        if (kind == Kind.SLIDER) {
            labelW = Math.min(112, Math.max(1, width / 3));
            // Reserve the unchanged 7px knob and its AA edge inside the host clip.
            controlW = width - 2 * pad - labelW - 2 * gap - 48 - 8;
            inline = controlW >= UiDensity.MIN_TRACK && measure.applyAsInt(text) <= labelW - 4;
            h = inline ? UiDensity.INLINE_SLIDER : UiDensity.STACKED_ROW;
            if (!inline) labelW = Math.max(1, width - 2 * pad - gap - 48);
            value = new UiDensity.Rect(inline ? pad + labelW + gap : width - pad - 48,
                    inline ? 0 : 0, 48, inline ? h : 20);
        }
        labelW = Math.max(1, labelW - 4); // previous design's 12px text inset
        List<String> lines = WordWrap.lines(text, labelW, 2, measure);
        if (lines.size() > 1) h = Math.max(h, Math.max(UiDensity.STACKED_ROW, lines.size() * (lineHeight + 1) + 8));
        int labelH = kind == Kind.SLIDER && !inline ? h - 16 : h;
        UiDensity.Rect label = new UiDensity.Rect(pad + 4, 0, labelW, labelH);
        UiDensity.Rect control = kind == Kind.SLIDER
                ? new UiDensity.Rect(inline ? width - pad - 8 - controlW : pad + 8, inline ? h / 2 - 2 : h - 12,
                    inline ? controlW : Math.max(1, width - 2 * pad - 16), 4)
                : new UiDensity.Rect(Math.max(pad, width - pad - controlW), (h - controlH) / 2, controlW, controlH);
        return new SettingRowLayout(h, List.copyOf(lines), label, control, value, inline);
    }

    /** Reserve live status/credit content without changing control dimensions. */
    public SettingRowLayout withSubtitle(int subtitleHeight, int lineHeight) {
        if (subtitleHeight <= 0) return this;
        int h = Math.max(height, lines.size() * (lineHeight + 1) + subtitleHeight + 8);
        return new SettingRowLayout(h, lines, new UiDensity.Rect(label.x(), 0, label.width(), h),
                new UiDensity.Rect(control.x(), (h - control.height()) / 2, control.width(), control.height()), value, inline);
    }

    /** A 24px-high pointer target around the unchanged control visual. */
    public UiDensity.Rect controlHit() {
        int h = Math.max(24, control.height());
        return new UiDensity.Rect(control.x(), Math.max(0, control.y() - (h - control.height()) / 2), control.width(), h);
    }
}
