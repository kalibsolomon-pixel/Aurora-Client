package com.aurora.client.config.profile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure, Minecraft-free server address pattern parser and matcher for
 * per-server profile auto-switching.
 *
 * <p>Supports exact hostnames/IPs ({@code minemen.club}, {@code 127.0.0.1:25565}),
 * subdomain wildcards ({@code *.hypixel.net} — matching both subdomains and the
 * apex domain), general glob wildcards ({@code *}, {@code ?}), and optional
 * explicit ports. Default Minecraft port {@code :25565} is normalized away so
 * {@code play.hypixel.net:25565} matches {@code *.hypixel.net}.
 */
public final class ServerPatternMatcher {
    private static final int DEFAULT_PORT = 25565;

    private ServerPatternMatcher() {}

    /**
     * Parses a user-entered list of server patterns separated by commas,
     * semicolons, or newlines. Normalizes to lowercase, trims whitespace,
     * strips trailing dots and default {@code :25565}, and deduplicates while
     * preserving order.
     */
    public static List<String> parsePatterns(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String[] parts = raw.split("[,;\\r\\n]+");
        Set<String> unique = new LinkedHashSet<>();
        for (String part : parts) {
            String normalized = normalizePattern(part);
            if (!normalized.isEmpty()) unique.add(normalized);
        }
        return List.copyOf(unique);
    }

    /** Normalizes a list of stored/edited patterns into a clean immutable list. */
    public static List<String> normalizeList(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) return List.of();
        Set<String> unique = new LinkedHashSet<>();
        for (String p : patterns) {
            String normalized = normalizePattern(p);
            if (!normalized.isEmpty()) unique.add(normalized);
        }
        return List.copyOf(unique);
    }

    /** Formats patterns for display in a single-line text field. */
    public static String formatPatterns(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) return "";
        return String.join(", ", patterns);
    }

    /** Normalizes a single pattern or server address for matching. */
    public static String normalizePattern(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return "";
        // Strip accidental whitespace inside an entry
        if (s.indexOf(' ') >= 0) s = s.replace(" ", "");
        // Strip default port :25565 when present
        int colon = s.lastIndexOf(':');
        if (colon > 0 && s.indexOf(']') < colon) {
            String portPart = s.substring(colon + 1);
            if (String.valueOf(DEFAULT_PORT).equals(portPart)) {
                s = s.substring(0, colon);
            }
        }
        while (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** Normalizes a live server address from {@code ServerData.ip}. */
    public static String normalizeServerAddress(String rawAddress) {
        return normalizePattern(rawAddress);
    }

    /** Returns true when {@code serverAddress} matches {@code pattern}. */
    public static boolean matches(String pattern, String serverAddress) {
        String p = normalizePattern(pattern);
        String addr = normalizeServerAddress(serverAddress);
        if (p.isEmpty() || addr.isEmpty()) return false;

        boolean patternHasPort = hasPort(p);
        boolean addrHasPort = hasPort(addr);

        if (patternHasPort) {
            // Pattern specifies an explicit port (e.g. play.example.net:25566)
            return globMatches(p, addr);
        }
        // Portless pattern matches the host regardless of port
        String host = addrHasPort ? stripPort(addr) : addr;
        if (p.startsWith("*.")) {
            String apex = p.substring(2);
            if (!apex.isEmpty() && globMatches(apex, host)) return true;
        }
        return globMatches(p, host);
    }

    /**
     * Specificity score used to break ties when multiple profiles match the
     * same server address. Exact host+port > exact host > subdomain/glob with
     * more literal characters.
     */
    public static int specificity(String pattern) {
        String p = normalizePattern(pattern);
        if (p.isEmpty()) return -1;
        boolean wildcard = p.indexOf('*') >= 0 || p.indexOf('?') >= 0;
        boolean port = hasPort(p);
        int literals = 0;
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c != '*' && c != '?') literals++;
        }
        int base = wildcard ? 0 : 10_000;
        int portBonus = port ? 5_000 : 0;
        return base + portBonus + literals;
    }

    private static boolean hasPort(String s) {
        int colon = s.lastIndexOf(':');
        if (colon <= 0 || colon == s.length() - 1) return false;
        if (s.indexOf(']') > colon) return false; // IPv6 literal without port
        for (int i = colon + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '*' && c != '?' && (c < '0' || c > '9')) return false;
        }
        return true;
    }

    private static String stripPort(String s) {
        int colon = s.lastIndexOf(':');
        return colon > 0 ? s.substring(0, colon) : s;
    }

    /** Standard O(N*M) wildcard matcher supporting {@code *} and {@code ?} with no regex compilation. */
    private static boolean globMatches(String pattern, String text) {
        int pi = 0, ti = 0, starIdx = -1, matchIdx = 0;
        int pLen = pattern.length(), tLen = text.length();
        while (ti < tLen) {
            if (pi < pLen && (pattern.charAt(pi) == '?' || pattern.charAt(pi) == text.charAt(ti))) {
                pi++;
                ti++;
            } else if (pi < pLen && pattern.charAt(pi) == '*') {
                starIdx = pi++;
                matchIdx = ti;
            } else if (starIdx != -1) {
                pi = starIdx + 1;
                ti = ++matchIdx;
            } else {
                return false;
            }
        }
        while (pi < pLen && pattern.charAt(pi) == '*') pi++;
        return pi == pLen;
    }
}
