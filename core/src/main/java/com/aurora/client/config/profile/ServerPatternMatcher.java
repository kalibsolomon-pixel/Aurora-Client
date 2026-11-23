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
        ParsedEndpoint parsed = parse(raw);
        if (parsed == null) return "";
        if (parsed.port != null) {
            return parsed.ipv6 ? "[" + parsed.host + "]:" + parsed.port : parsed.host + ":" + parsed.port;
        }
        return parsed.host;
    }

    /** Normalizes a live server address from {@code ServerData.ip}. */
    public static String normalizeServerAddress(String rawAddress) {
        return normalizePattern(rawAddress);
    }

    /** Returns true when {@code serverAddress} matches {@code pattern}. */
    public static boolean matches(String pattern, String serverAddress) {
        ParsedEndpoint p = parse(pattern);
        ParsedEndpoint addr = parse(serverAddress);
        if (p == null || addr == null) return false;

        if (p.port != null) {
            // Pattern specifies an explicit port (e.g. play.example.net:25566 or [::1]:25566)
            if (addr.port == null || !globMatches(p.port, addr.port)) return false;
            return hostMatches(p.host, addr.host);
        }
        // Portless pattern matches the host regardless of port
        return hostMatches(p.host, addr.host);
    }

    private static boolean hostMatches(String patternHost, String addrHost) {
        if (patternHost.startsWith("*.")) {
            String apex = patternHost.substring(2);
            if (!apex.isEmpty() && globMatches(apex, addrHost)) return true;
        }
        return globMatches(patternHost, addrHost);
    }

    /**
     * Specificity score used to break ties when multiple profiles match the
     * same server address. Exact host+port > exact host > subdomain/glob with
     * more literal characters.
     */
    public static int specificity(String pattern) {
        ParsedEndpoint p = parse(pattern);
        if (p == null) return -1;
        boolean wildcard = hasWildcard(p.host) || (p.port != null && hasWildcard(p.port));
        int literals = countLiterals(p.host);
        if (p.port != null) literals += 1 + countLiterals(p.port);
        int base = wildcard ? 0 : 10_000;
        int portBonus = p.port != null ? 5_000 : 0;
        return base + portBonus + literals;
    }

    private record ParsedEndpoint(String host, String port, boolean ipv6) {}

    /**
     * Parses bracketed hosts ({@code [::1]}, {@code [::1]:25566}), bare IPv6
     * literals ({@code ::1}, {@code 2001:db8::1}), and hostnames/IPv4 with
     * optional explicit port ({@code play.example.net:25566}) into a canonical
     * form where default port {@value #DEFAULT_PORT} is omitted.
     */
    private static ParsedEndpoint parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        if (s.indexOf(' ') >= 0) s = s.replace(" ", "");
        if (s.isEmpty()) return null;

        String host;
        String port = null;
        boolean ipv6 = false;

        if (s.charAt(0) == '[') {
            int close = s.indexOf(']');
            if (close < 0) return null;
            host = s.substring(1, close);
            ipv6 = host.indexOf(':') >= 0;
            String rest = s.substring(close + 1);
            if (rest.startsWith(":") && rest.length() > 1) {
                String candidate = rest.substring(1);
                if (!isPortToken(candidate)) return null;
                port = candidate;
            } else if (!rest.isEmpty()) {
                return null;
            }
        } else {
            int firstColon = s.indexOf(':');
            int lastColon = s.lastIndexOf(':');
            if (firstColon >= 0 && firstColon != lastColon) {
                // Multiple colons without brackets => bare IPv6 literal (no port)
                host = s;
                ipv6 = true;
            } else if (lastColon > 0 && lastColon < s.length() - 1) {
                String candidate = s.substring(lastColon + 1);
                if (isPortToken(candidate)) {
                    host = s.substring(0, lastColon);
                    port = candidate;
                } else {
                    host = s;
                }
            } else {
                host = s;
            }
        }

        if (!ipv6) {
            while (host.endsWith(".")) {
                host = host.substring(0, host.length() - 1);
            }
        } else if (!hasWildcard(host)) {
            String canonical = canonicalizeIpv6(host);
            if (canonical != null) host = canonical;
        }

        if (host.isEmpty()) return null;
        if (String.valueOf(DEFAULT_PORT).equals(port)) {
            port = null;
        }
        return new ParsedEndpoint(host, port, ipv6);
    }

    private static boolean hasWildcard(String s) {
        return s.indexOf('*') >= 0 || s.indexOf('?') >= 0;
    }

    private static int countLiterals(String s) {
        int literals = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '*' && c != '?') literals++;
        }
        return literals;
    }

    private static boolean isPortToken(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '*' && c != '?' && (c < '0' || c > '9')) return false;
        }
        return true;
    }

    /**
     * RFC 5952 canonicalization for pure IPv6 literals (including embedded IPv4
     * tails such as {@code ::ffff:127.0.0.1}). Returns null when {@code host}
     * is not a valid literal IPv6 address (caller keeps the lowercased input).
     */
    private static String canonicalizeIpv6(String host) {
        int zone = host.indexOf('%');
        String zoneSuffix = zone >= 0 ? host.substring(zone) : "";
        String addr = zone >= 0 ? host.substring(0, zone) : host;
        if (addr.isEmpty()) return null;

        int doubleColon = addr.indexOf("::");
        if (doubleColon >= 0 && addr.indexOf("::", doubleColon + 2) >= 0) return null;

        int[] groups = new int[8];
        int leftCount = 0;
        int rightCount = 0;
        int[] rightGroups = new int[8];

        if (doubleColon >= 0) {
            String leftPart = addr.substring(0, doubleColon);
            String rightPart = addr.substring(doubleColon + 2);
            if (!leftPart.isEmpty()) {
                leftCount = parseIpv6Segment(leftPart, groups, 0, false);
                if (leftCount < 0) return null;
            }
            if (!rightPart.isEmpty()) {
                rightCount = parseIpv6Segment(rightPart, rightGroups, 0, true);
                if (rightCount < 0) return null;
            }
            if (leftCount + rightCount >= 8) return null;
            int rightStart = 8 - rightCount;
            System.arraycopy(rightGroups, 0, groups, rightStart, rightCount);
        } else {
            int count = parseIpv6Segment(addr, groups, 0, true);
            if (count != 8) return null;
        }

        // Find the longest run of >= 2 zero groups (first wins ties, per RFC 5952).
        int bestStart = -1, bestLen = 1;
        int curStart = -1, curLen = 0;
        for (int i = 0; i < 8; i++) {
            if (groups[i] == 0) {
                if (curStart < 0) {
                    curStart = i;
                    curLen = 1;
                } else {
                    curLen++;
                }
                if (curLen > bestLen) {
                    bestStart = curStart;
                    bestLen = curLen;
                }
            } else {
                curStart = -1;
                curLen = 0;
            }
        }

        StringBuilder out = new StringBuilder(39 + zoneSuffix.length());
        for (int i = 0; i < 8; i++) {
            if (bestStart >= 0 && i == bestStart) {
                out.append("::");
                i += bestLen - 1;
                continue;
            }
            if (i > 0 && !(bestStart >= 0 && i == bestStart + bestLen)) {
                out.append(':');
            }
            out.append(Integer.toHexString(groups[i]));
        }
        out.append(zoneSuffix);
        return out.toString();
    }

    private static int parseIpv6Segment(String segment, int[] out, int offset, boolean allowIpv4Tail) {
        String[] parts = segment.split(":", -1);
        int idx = offset;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) return -1;
            if (allowIpv4Tail && i == parts.length - 1 && part.indexOf('.') >= 0) {
                String[] octets = part.split("\\.", -1);
                if (octets.length != 4 || idx + 2 > out.length) return -1;
                int[] b = new int[4];
                for (int j = 0; j < 4; j++) {
                    if (octets[j].isEmpty() || octets[j].length() > 3) return -1;
                    int val = 0;
                    for (int k = 0; k < octets[j].length(); k++) {
                        char c = octets[j].charAt(k);
                        if (c < '0' || c > '9') return -1;
                        val = val * 10 + (c - '0');
                    }
                    if (val > 255) return -1;
                    b[j] = val;
                }
                out[idx++] = (b[0] << 8) | b[1];
                out[idx++] = (b[2] << 8) | b[3];
            } else {
                if (part.length() > 4 || idx >= out.length) return -1;
                int val = 0;
                for (int k = 0; k < part.length(); k++) {
                    int digit = Character.digit(part.charAt(k), 16);
                    if (digit < 0) return -1;
                    val = (val << 4) | digit;
                }
                out[idx++] = val;
            }
        }
        return idx - offset;
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
