package com.aurora.client.util;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure, Minecraft-free privacy helpers for Streamer Mode.
 *
 * <p>All methods return the original string reference without allocating when
 * no replacement applies, so inactive or non-matching frames incur negligible
 * cost.
 */
public final class StreamerPrivacy {
    public static final String DEFAULT_SELF_ALIAS = "You";
    public static final String DEFAULT_OPPONENT_LABEL = "Opponent";
    public static final String HIDDEN_SERVER_LABEL = "Hidden Server";
    public static final int MAX_ALIAS_LENGTH = 24;

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9._-])(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?(?![A-Za-z0-9._-])");
    private static final Pattern HOST_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9._-])(?:localhost(?::\\d{1,5})?|(?:[A-Za-z0-9-]+\\.)+(?:com|net|org|gg|io|me|co|us|uk|eu|de|fr|ca|au|mc|xyz|club|fun|play|network|server|dev|app|pro|live|tv|lol|rip|wtf|cc|ru|br|jp|kr|cn|fr|pl|nl|se|no|fi|ch|at|es|it|info|biz|top|site|online|store|tech|cloud|host|space|world|games|gaming)(?::\\d{1,5})?)(?![A-Za-z0-9._-])",
            Pattern.CASE_INSENSITIVE);

    private static final ConcurrentHashMap<UUID, Integer> OPPONENT_IDS = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_OPPONENT = new AtomicInteger(1);

    private StreamerPrivacy() {}

    /**
     * Normalizes a user-supplied alias or opponent label: trims whitespace,
     * strips section-sign formatting codes and control characters, caps length,
     * and falls back to {@code fallback} when empty.
     */
    public static String sanitizeAlias(String raw, String fallback) {
        if (raw == null) return fallback;
        StringBuilder sb = new StringBuilder(Math.min(raw.length(), MAX_ALIAS_LENGTH));
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\u00A7') {
                // Skip legacy formatting code pair.
                if (i + 1 < raw.length()) i++;
                continue;
            }
            if (c < 0x20 || c == 0x7F) continue;
            sb.append(c);
            if (sb.length() >= MAX_ALIAS_LENGTH) break;
        }
        String trimmed = sb.toString().trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    /**
     * Case-insensitive whole-identifier replacement for a Minecraft player name.
     * Matches only when the surrounding characters are not Minecraft username
     * characters ({@code [A-Za-z0-9_]}), so {@code "Alex"} matches {@code "<Alex>"}
     * and {@code "Alex's"} without altering {@code "Alexander"}.
     */
    public static String replaceName(String text, String targetName, String replacement) {
        if (text == null || text.isEmpty() || targetName == null || targetName.isEmpty()) return text;
        int targetLen = targetName.length();
        int textLen = text.length();
        if (textLen < targetLen) return text;

        String safeReplacement = replacement == null ? "" : replacement;
        StringBuilder out = null;
        int copyFrom = 0;
        int i = 0;
        while (i <= textLen - targetLen) {
            if (text.regionMatches(true, i, targetName, 0, targetLen)
                    && (i == 0 || !isMcNameChar(text.charAt(i - 1)))
                    && (i + targetLen == textLen || !isMcNameChar(text.charAt(i + targetLen)))) {
                if (out == null) out = new StringBuilder(textLen);
                out.append(text, copyFrom, i).append(safeReplacement);
                i += targetLen;
                copyFrom = i;
            } else {
                i++;
            }
        }
        if (out == null) return text;
        out.append(text, copyFrom, textLen);
        return out.toString();
    }

    private static boolean isMcNameChar(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_';
    }

    /**
     * Replaces every character in an EditBox string with a bullet so caret and
     * selection coordinates stay 1:1 aligned while hiding the address.
     */
    public static String maskEditText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        return "\u2022".repeat(raw.length());
    }

    /**
     * Returns true when a server title looks like a raw IP address, localhost,
     * or dotted hostname rather than a friendly display name.
     */
    public static boolean looksLikeServerAddress(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.indexOf(' ') >= 0) return false;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.equals("localhost") || lower.startsWith("localhost:")) return true;
        if (IPV4_PATTERN.matcher(trimmed).matches()) return true;
        // Dotted hostname such as play.example.net or mc.server.gg:25565
        int colon = lower.indexOf(':');
        String host = colon >= 0 ? lower.substring(0, colon) : lower;
        if (host.indexOf('.') <= 0 || host.endsWith(".") || host.contains("..")) return false;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    /**
     * Redacts the active server's address (with or without port) as well as any
     * inline IPv4 addresses or hostnames from {@code text}.
     */
    public static String redactServerAddresses(String text, String activeServerIp) {
        if (text == null || text.isEmpty()) return text;
        String result = text;
        if (activeServerIp != null && !activeServerIp.isBlank()) {
            String trimmedIp = activeServerIp.trim();
            result = replaceLiteralIgnoreCase(result, trimmedIp, HIDDEN_SERVER_LABEL);
            int colon = trimmedIp.indexOf(':');
            if (colon > 0) {
                String hostOnly = trimmedIp.substring(0, colon);
                if (hostOnly.length() >= 4) {
                    result = replaceLiteralIgnoreCase(result, hostOnly, HIDDEN_SERVER_LABEL);
                }
            }
        }
        result = replaceRegex(result, IPV4_PATTERN, HIDDEN_SERVER_LABEL);
        result = replaceRegex(result, HOST_PATTERN, HIDDEN_SERVER_LABEL);
        return result;
    }

    private static String replaceLiteralIgnoreCase(String text, String needle, String replacement) {
        int nLen = needle.length();
        int tLen = text.length();
        if (nLen == 0 || tLen < nLen) return text;
        StringBuilder out = null;
        int copyFrom = 0;
        int i = 0;
        while (i <= tLen - nLen) {
            if (text.regionMatches(true, i, needle, 0, nLen)) {
                if (out == null) out = new StringBuilder(tLen);
                out.append(text, copyFrom, i).append(replacement);
                i += nLen;
                copyFrom = i;
            } else {
                i++;
            }
        }
        if (out == null) return text;
        out.append(text, copyFrom, tLen);
        return out.toString();
    }

    private static String replaceRegex(String text, Pattern pattern, String replacement) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) return text;
        StringBuilder sb = new StringBuilder(text.length());
        do {
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        } while (m.find());
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Returns a stable per-session display label for another player, e.g.
     * {@code "Opponent 1"} when numbered or {@code "Opponent"} when unnumbered.
     */
    public static String opponentTag(UUID uuid, String labelPrefix, boolean numbered) {
        String prefix = sanitizeAlias(labelPrefix, DEFAULT_OPPONENT_LABEL);
        if (!numbered || uuid == null) return prefix;
        int id = OPPONENT_IDS.computeIfAbsent(uuid, ignored -> NEXT_OPPONENT.getAndIncrement());
        return prefix + " " + id;
    }

    /** Clears per-session opponent numbering (called on world/server join). */
    public static void resetSession() {
        OPPONENT_IDS.clear();
        NEXT_OPPONENT.set(1);
    }
}
