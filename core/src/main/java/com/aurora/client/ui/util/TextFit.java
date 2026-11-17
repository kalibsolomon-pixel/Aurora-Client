package com.aurora.client.ui.util;

import java.text.BreakIterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Grapheme-safe single-line text fitting: truncation happens only at extended
 * grapheme cluster boundaries, so a combining mark stays on its base letter, a
 * ZWJ emoji sequence and a regional-indicator flag pair keep all their
 * codepoints, and no surrogate is ever split. The fit itself is a binary
 * search over the cluster boundaries — width is monotonic in the prefix
 * length for these fonts — with a verification walk after the search so a
 * non-monotonic kerning dip cannot return an overflowing prefix.
 */
public final class TextFit {
    private TextFit() {}

    /** The single ellipsis character every fitted line appends. */
    public static final String ELLIPSIS = "…";

    /**
     * Truncates {@code s} to {@code maxW} measured units: drops trailing
     * clusters (never below a cluster boundary within {@code minLen} chars)
     * until {@code prefix + "…"} fits, then appends the ellipsis. Returns
     * {@code s} unchanged when it already fits, {@code ""} for
     * {@code maxW <= 0}, the longest cluster prefix that fits alone when the
     * ellipsis itself is wider than {@code maxW}, and a width-bounded
     * cluster prefix plus the ellipsis when even the floor cannot carry one.
     */
    public static String ellipsize(String s, int maxW, int minLen, ToIntFunction<String> width) {
        if (maxW <= 0) return "";
        if (width.applyAsInt(s) <= maxW) return s;
        int ellW = width.applyAsInt(ELLIPSIS);
        if (ellW > maxW) return prefixByWidth(s, maxW, width);
        int[] bounds = clusterBoundaries(s);
        int floor = floorIndex(bounds, minLen);
        int best = lastIndexOf(s, bounds, floor, maxW, width);
        String candidate = s.substring(0, bounds[best]) + ELLIPSIS;
        while (best > floor && width.applyAsInt(candidate) > maxW) {
            best--;
            candidate = s.substring(0, bounds[best]) + ELLIPSIS;
        }
        if (width.applyAsInt(candidate) > maxW)
            return prefixByWidth(s.substring(0, bounds[floor]), maxW - ellW, width) + ELLIPSIS;
        return candidate;
    }

    /**
     * The largest extended grapheme cluster boundary {@code end} of {@code s}
     * with {@code width(s.substring(0, end) + "…") <= maxW}, never below the
     * boundary that floors {@code minLen} chars. For callers that build their
     * own tail instead of the plain ellipsis.
     */
    public static int ellipsizedEnd(String s, int maxW, int minLen, ToIntFunction<String> width) {
        int[] bounds = clusterBoundaries(s);
        int floor = floorIndex(bounds, minLen);
        int best = lastIndexOf(s, bounds, floor, maxW, width);
        while (best > floor && width.applyAsInt(s.substring(0, bounds[best]) + ELLIPSIS) > maxW) best--;
        return bounds[best];
    }

    /** Binary search for the largest boundary index at or above {@code floor} whose prefix leaves room for the ellipsis. */
    private static int lastIndexOf(String s, int[] bounds, int floor, int maxW, ToIntFunction<String> width) {
        int lo = floor, hi = bounds.length - 1, best = floor;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (width.applyAsInt(s.substring(0, bounds[mid]) + ELLIPSIS) <= maxW) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return best;
    }

    /** The longest cluster prefix of {@code s} whose measured width stays within {@code budget}. */
    private static String prefixByWidth(String s, int budget, ToIntFunction<String> width) {
        int[] bounds = clusterBoundaries(s);
        int lo = 0, hi = bounds.length - 1, best = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (width.applyAsInt(s.substring(0, bounds[mid])) <= budget) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        while (best > 0 && width.applyAsInt(s.substring(0, bounds[best])) > budget) best--;
        return s.substring(0, bounds[best]);
    }

    /**
     * Extended grapheme cluster boundaries of {@code s} (JDK 20+ implements
     * UAX #29 extended clusters in the character instance), ascending, always
     * starting at 0 and ending at {@code s.length()}.
     */
    private static int[] clusterBoundaries(String s) {
        BreakIterator it = CLUSTERS.get();
        it.setText(s);
        int[] bounds = new int[s.length() + 1];
        int count = 1;
        for (int end = it.next(); end != BreakIterator.DONE; end = it.next()) bounds[count++] = end;
        if (bounds[count - 1] != s.length()) bounds[count++] = s.length();
        return java.util.Arrays.copyOf(bounds, count);
    }

    /** The index of the largest boundary at or below {@code minLen} chars: how far truncation may go down. */
    private static int floorIndex(int[] bounds, int minLen) {
        int floor = 0;
        while (floor + 1 < bounds.length && bounds[floor + 1] <= minLen) floor++;
        return floor;
    }

    private static final ThreadLocal<BreakIterator> CLUSTERS =
            ThreadLocal.withInitial(() -> BreakIterator.getCharacterInstance(Locale.ROOT));

    /**
     * A bounded memo around {@link #ellipsize} so per-frame callers re-fit
     * nothing: keyed by everything the fit depends on — text, {@code maxW},
     * {@code minLen} and the caller's style discriminator (Aurora packs font,
     * weight, case, renderer mode and raster scale into {@code styleKey()}),
     * so a change to any of them is a different key and re-fits. Access-order
     * eviction keeps the working set; one instance belongs to one thread (the
     * render thread), like every fitted-text cache in the mod.
     */
    public static final class Memo {
        private record Key(String text, int maxW, int minLen, int style) {}

        private final Map<Key, String> fitted;

        public Memo(int capacity) {
            fitted = new LinkedHashMap<>(16, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Key, String> eldest) {
                    return size() > capacity;
                }
            };
        }

        /** {@link #ellipsize} through the memo; {@code maxW <= 0} skips the key allocation entirely. */
        public String fit(String text, int maxW, int minLen, int style, ToIntFunction<String> width) {
            if (maxW <= 0) return "";
            Key key = new Key(text, maxW, minLen, style);
            String cached = fitted.get(key);
            if (cached != null) return cached;
            String result = ellipsize(text, maxW, minLen, width);
            fitted.put(key, result);
            return result;
        }

        public int size() {
            return fitted.size();
        }
    }
}
