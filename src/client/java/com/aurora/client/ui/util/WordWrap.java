package com.aurora.client.ui.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Measured wrapping at whitespace only. A word is never divided between lines. */
public final class WordWrap {
    private WordWrap() {}

    public static List<String> lines(String text, int maxWidth, int maxLines, ToIntFunction<String> measure) {
        if (text == null || text.isBlank() || maxWidth <= 0 || maxLines <= 0) return List.of();
        String[] words = text.strip().split("(?U)\\s+");
        List<String> result = new ArrayList<>(maxLines);
        String line = "";
        for (int i = 0; i < words.length; i++) {
            String candidate = line.isEmpty() ? words[i] : line + " " + words[i];
            if (!line.isEmpty() && measure.applyAsInt(candidate) > maxWidth) {
                result.add(line);
                line = "";
                if (result.size() == maxLines) {
                    result.set(maxLines - 1, fit(result.get(maxLines - 1) + " …", maxWidth, measure));
                    return List.copyOf(result);
                }
                candidate = words[i];
            }
            // An oversized word remains on one line; ellipsis provides a bounded fallback.
            line = fit(candidate, maxWidth, measure);
        }
        if (!line.isEmpty()) result.add(line);
        return List.copyOf(result);
    }

    private static String fit(String text, int maxWidth, ToIntFunction<String> measure) {
        if (measure.applyAsInt(text) <= maxWidth) return text;
        if (measure.applyAsInt("…") > maxWidth) return "";
        int end = text.length();
        while (end > 0 && measure.applyAsInt(text.substring(0, end) + "…") > maxWidth)
            end = text.offsetByCodePoints(end, -1);
        return text.substring(0, end).stripTrailing() + "…";
    }
}
