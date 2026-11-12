package com.aurora.client.search;

import com.aurora.client.config.AuroraConfig;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The one text matcher behind every Aurora search field (2026-11-12):
 * feature search, profiles, waypoints, particle lists and the icon browser.
 *
 * <p>Text is normalized the same way on both sides ({@link Locale#ROOT}
 * lowercase, accents folded, {@code _ - / .} read as spaces). Every word of
 * the query must match some word of the target: exactly, as a prefix, as a
 * substring of the whole target, or within the configured typo tolerance
 * (bounded Damerau-Levenshtein, the optimal-string-alignment form, so a
 * swapped pair of letters counts as one typo). Short words allow fewer
 * typos than the setting does — 0 for up to 3 letters, 1 for up to 6 — so
 * a two-letter query never matches noise.
 *
 * <p>No Minecraft imports (ADR 0003).
 */
public final class SearchMatcher {
    private SearchMatcher() {}

    /** The highest typo tolerance the setting offers. */
    public static final int MAX_TOLERANCE = 3;

    private static final Pattern SEPARATORS = Pattern.compile("[\\s_\\-/.]+");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final String[] NO_WORDS = new String[0];

    /** A normalized query: its full text and its words, with the tolerance it was made for. */
    public record Query(String text, String[] words, int tolerance) {
        public boolean isEmpty() { return words.length == 0; }
    }

    /** A normalized search target, prepared once and matched many times. */
    public record Target(String text, String[] words) {}

    /** The typo tolerance the owner set (Settings → General → Search), clamped to 0–3. */
    public static int configuredTolerance() {
        AuroraConfig config = AuroraConfig.get();
        return config == null ? 0 : Math.max(0, Math.min(MAX_TOLERANCE, config.searchTypoTolerance));
    }

    /** Lowercase (ROOT), accents folded, separators collapsed to single spaces, trimmed. */
    public static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String s = raw.toLowerCase(Locale.ROOT);
        if (!isAscii(s)) s = MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        return SEPARATORS.matcher(s).replaceAll(" ").trim();
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) > 0x7F) return false;
        return true;
    }

    private static String[] words(String normalized) {
        return normalized.isEmpty() ? NO_WORDS : normalized.split(" ");
    }

    public static Query query(String raw, int tolerance) {
        String text = normalize(raw);
        return new Query(text, words(text), Math.max(0, Math.min(MAX_TOLERANCE, tolerance)));
    }

    public static Target target(String raw) {
        String text = normalize(raw);
        return new Target(text, words(text));
    }

    /** Typos a query word of this length may carry: 0 up to 3 letters, 1 up to 6, then 2 — never above the setting. */
    static int allowedTypos(int wordLength, int tolerance) {
        int cap = wordLength <= 3 ? 0 : wordLength <= 6 ? 1 : 2;
        return Math.min(cap, tolerance);
    }

    /**
     * How well {@code target} matches {@code query}: −1 for no match, else a
     * score where higher is better (an exact word beats a prefix, a prefix
     * beats a substring, and fewer typos beat more). An empty query matches
     * everything with 0.
     */
    public static int score(Query query, Target target) {
        if (query.isEmpty()) return 0;
        if (target.text().isEmpty()) return -1;
        int total = 0;
        for (String word : query.words()) {
            int best = wordScore(word, target, query.tolerance());
            if (best < 0) return -1;
            total += best;
        }
        int score = total / query.words().length;
        if (query.words().length > 1 && target.text().contains(query.text())) score += 20; // the phrase as typed
        return score;
    }

    /** Convenience for one-off checks: does any of {@code targets} match? */
    public static boolean matches(Query query, String... targets) {
        if (query.isEmpty()) return true;
        for (String t : targets) {
            if (t != null && score(query, target(t)) >= 0) return true;
        }
        return false;
    }

    private static int wordScore(String word, Target target, int tolerance) {
        int best = -1;
        for (String tw : target.words()) {
            if (tw.equals(word)) return 100;
            if (tw.startsWith(word)) best = Math.max(best, 80);
        }
        if (best >= 0) return best;
        if (target.text().contains(word)) return 60;
        int allowed = allowedTypos(word.length(), tolerance);
        if (allowed == 0) return -1;
        for (String tw : target.words()) {
            // Whole word, or the start of a longer word (a typo while still typing it).
            int d = distance(word, tw, allowed);
            if (tw.length() > word.length()) {
                d = Math.min(d, distance(word, tw.substring(0, word.length()), allowed));
            }
            if (d <= allowed) best = Math.max(best, 40 - 10 * d);
        }
        return best;
    }

    /**
     * Optimal-string-alignment distance between {@code a} and {@code b}, or
     * {@code max + 1} as soon as it is certain to exceed {@code max}.
     */
    static int distance(String a, String b, int max) {
        int n = a.length(), m = b.length();
        if (Math.abs(n - m) > max) return max + 1;
        int[] prev2 = new int[m + 1], prev = new int[m + 1], cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            int rowMin = cur[0];
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                char cb = b.charAt(j - 1);
                int cost = ca == cb ? 0 : 1;
                int v = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost);
                if (i > 1 && j > 1 && ca == b.charAt(j - 2) && a.charAt(i - 2) == cb) {
                    v = Math.min(v, prev2[j - 2] + 1);
                }
                cur[j] = v;
                rowMin = Math.min(rowMin, v);
            }
            if (rowMin > max) return max + 1;
            int[] t = prev2; prev2 = prev; prev = cur; cur = t;
        }
        return Math.min(prev[m], max + 1);
    }
}
