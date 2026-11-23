package com.aurora.client.chat;

import com.aurora.client.config.AuroraConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure, Minecraft-free chat formatting, duplicate normalization, sender
 * detection, search matching, and history-scope utilities.
 */
public final class ChatFormatting {
    private static final DateTimeFormatter FMT_24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter FMT_24_SEC = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter FMT_12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);

    private static final Pattern LEADING_TIMESTAMP = Pattern.compile(
            "^\\s*\\[(?:\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s?[AP]M)?)\\]\\s*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_DUPLICATE = Pattern.compile("\\s*\\[x(\\d+)\\]\\s*$");
    private static final Pattern LEGACY_COLOR = Pattern.compile("\u00A7[0-9A-FK-ORa-fk-or]");
    private static final Pattern PREFIX_BRACKET = Pattern.compile("^\\s*\\[[^\\]]{1,24}\\]\\s*");

    private ChatFormatting() {}

    /** Formats a local clock timestamp prefix such as {@code "[22:15] "}. */
    public static String formatTimestamp(LocalTime time, AuroraConfig.ChatTimestampFormat format) {
        if (time == null) time = LocalTime.now();
        AuroraConfig.ChatTimestampFormat fmt = format == null ? AuroraConfig.ChatTimestampFormat.HH_MM : format;
        DateTimeFormatter dtf = switch (fmt) {
            case HH_MM -> FMT_24;
            case HH_MM_SS -> FMT_24_SEC;
            case H_MM_A -> FMT_12;
        };
        return "[" + time.format(dtf) + "] ";
    }

    /** Formats a compact duplicate suffix such as {@code " [x3]"}. */
    public static String duplicateBadge(int count) {
        return count > 1 ? " [x" + count + "]" : "";
    }

    /** Strips legacy section-sign formatting codes without changing plain text. */
    public static String stripFormatting(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        if (raw.indexOf('\u00A7') < 0) return raw;
        return LEGACY_COLOR.matcher(raw).replaceAll("");
    }

    /**
     * Produces a canonical comparison string for duplicate compacting: strips
     * legacy codes, leading Aurora timestamp prefixes, and trailing {@code [xN]}
     * badges, then trims whitespace.
     */
    public static String normalizeForDuplicate(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String s = stripFormatting(raw).trim();
        s = LEADING_TIMESTAMP.matcher(s).replaceFirst("");
        s = TRAILING_DUPLICATE.matcher(s).replaceFirst("");
        return s.trim();
    }

    /**
     * Produces clean clipboard text from a rendered chat message, removing
     * Aurora's leading timestamp and trailing duplicate count badge.
     */
    public static String extractCopyText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String s = stripFormatting(raw);
        s = LEADING_TIMESTAMP.matcher(s).replaceFirst("");
        s = TRAILING_DUPLICATE.matcher(s).replaceFirst("");
        return s.trim();
    }

    /**
     * Best-effort sender extraction against the set of online player names.
     * Inspects the leading sender header (e.g. {@code "<Steve> hi"},
     * {@code "[MVP+] Steve: gg"}, {@code "Steve » hello"}) so player heads
     * attach only to messages sent by an online player and never misfire on
     * mentions later in the message body.
     */
    public static String extractSenderName(String rawMessage, Collection<String> onlineNames) {
        if (rawMessage == null || rawMessage.isBlank() || onlineNames == null || onlineNames.isEmpty()) {
            return null;
        }
        String plain = stripFormatting(rawMessage);
        plain = LEADING_TIMESTAMP.matcher(plain).replaceFirst("").trim();
        if (plain.isEmpty()) return null;

        // Isolate the sender header before the chat separator (>, :, », ›), if present.
        int sep = findSenderSeparator(plain);
        String header = sep > 0 ? plain.substring(0, Math.min(sep, 64)) : plain.substring(0, Math.min(plain.length(), 40));

        // Strip leading rank/channel tags like [MVP+], [Team], [G].
        String stripped = header;
        for (int i = 0; i < 4; i++) {
            Matcher m = PREFIX_BRACKET.matcher(stripped);
            if (!m.find()) break;
            stripped = stripped.substring(m.end());
        }

        // Tokenize candidate Minecraft usernames ([A-Za-z0-9_]{1,16}) in the header.
        int len = stripped.length();
        int idx = 0;
        int tokensChecked = 0;
        while (idx < len && tokensChecked < 4) {
            while (idx < len && !isMcNameChar(stripped.charAt(idx))) idx++;
            if (idx >= len) break;
            int start = idx;
            while (idx < len && isMcNameChar(stripped.charAt(idx))) idx++;
            String token = stripped.substring(start, idx);
            tokensChecked++;
            for (String online : onlineNames) {
                if (online != null && online.equalsIgnoreCase(token)) {
                    return online;
                }
            }
        }
        return null;
    }

    private static int findSenderSeparator(String s) {
        int limit = Math.min(s.length(), 64);
        for (int i = 0; i < limit; i++) {
            char c = s.charAt(i);
            if (c == '>' || c == ':' || c == '\u00BB' || c == '\u203A') {
                return i;
            }
        }
        return -1;
    }

    private static boolean isMcNameChar(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_';
    }

    /**
     * Case-insensitive multi-term search: every whitespace-separated word in
     * {@code query} must appear in the message text.
     */
    public static boolean matchesSearch(String messageText, String query) {
        if (query == null || query.isBlank()) return true;
        if (messageText == null || messageText.isEmpty()) return false;
        String haystack = stripFormatting(messageText).toLowerCase(Locale.ROOT);
        String[] terms = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        for (String term : terms) {
            if (!term.isEmpty() && !haystack.contains(term)) return false;
        }
        return true;
    }

    private static final String SINGLEPLAYER_SAVE_PREFIX = "sp-save:";
    private static final boolean WINDOWS_FS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    /**
     * Builds a stable, collision-free scope key for a singleplayer world from
     * its actual save root directory. Survives display-name changes and keeps
     * worlds with identical display names isolated.
     */
    public static String singleplayerScope(Path worldRoot) {
        if (worldRoot == null) return null;
        String normalized = worldRoot.toAbsolutePath().normalize().toString();
        if (normalized.isBlank()) return null;
        if (WINDOWS_FS) normalized = normalized.toLowerCase(Locale.ROOT);
        return SINGLEPLAYER_SAVE_PREFIX + normalized;
    }

    /**
     * Converts a server address or singleplayer world identifier into a safe,
     * deterministic filename stem for per-server chat history storage.
     */
    public static String sanitizeScopeKey(String scope) {
        if (scope == null || scope.isBlank()) return "unknown";
        String trimmed = scope.trim();
        if (trimmed.startsWith(SINGLEPLAYER_SAVE_PREFIX)) {
            String rawPath = trimmed.substring(SINGLEPLAYER_SAVE_PREFIX.length());
            String leaf = rawPath;
            int slash = Math.max(rawPath.lastIndexOf('/'), rawPath.lastIndexOf('\\'));
            if (slash >= 0 && slash + 1 < rawPath.length()) {
                leaf = rawPath.substring(slash + 1);
            }
            String stem = sanitizeStem("sp_" + leaf.toLowerCase(Locale.ROOT));
            return stem + "-" + shortHash(trimmed);
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String stem = sanitizeStem(lower);
        return stem + "-" + shortHash(lower);
    }

    private static String sanitizeStem(String lower) {
        StringBuilder safe = new StringBuilder(Math.min(lower.length(), 40));
        for (int i = 0; i < lower.length() && safe.length() < 40; i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
                safe.append(c);
            } else {
                safe.append('_');
            }
        }
        String stem = safe.toString().replaceAll("^_+|_+$", "");
        return stem.isEmpty() ? "world" : stem;
    }

    private static String shortHash(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(12);
            for (int i = 0; i < 6; i++) {
                hex.append(String.format(Locale.ROOT, "%02x", digest[i] & 0xFF));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
