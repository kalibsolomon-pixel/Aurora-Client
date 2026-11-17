package com.aurora.client.ui.util;

import java.util.ArrayList;
import java.util.List;
import com.aurora.client.port.TextMetrics;

/** Measured wrapping at whitespace only. A word is never divided between lines. */
public final class WordWrap {
    private WordWrap() {}

    public static List<String> lines(String text, int maxWidth, int maxLines, TextMetrics measure) {
        if (text == null || text.isBlank() || maxWidth <= 0 || maxLines <= 0) return List.of();
        String[] words = text.strip().split("(?U)\\s+");
        List<String> result = new ArrayList<>(maxLines);
        String line = "";
        for (int i = 0; i < words.length; i++) {
            String candidate = line.isEmpty() ? words[i] : line + " " + words[i];
            if (!line.isEmpty() && measure.width(candidate) > maxWidth) {
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

    private static String fit(String text, int maxWidth, TextMetrics measure) {
        if (measure.width(text) <= maxWidth) return text;
        if (measure.width("…") > maxWidth) return "";
        // Cluster-boundary truncation (TextFit); trailing whitespace between
        // the cut and the ellipsis still folds away.
        int end = TextFit.ellipsizedEnd(text, maxWidth, 0, measure::width);
        return text.substring(0, end).stripTrailing() + "…";
    }
}
