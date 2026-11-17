package com.aurora.client.util;

/**
 * Parsing for user-typed hexadecimal color text, shared by every hex field
 * (the color picker, the waypoint editor's ARGB field and Pulsar's dashboard
 * hex fields) so acceptance rules and their messages stay identical. A
 * leading {@code #} is optional and surrounding whitespace is ignored; any
 * other shape returns {@code null} — the caller shows the field's message
 * instead of silently ignoring the value.
 */
public final class HexText {
    private HexText() {}

    /** @return {@code true} when {@code s} is non-empty and only hex digits. */
    public static boolean isHex(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    /** Optional {@code #}, then exactly 6 hex digits (RGB), as an opaque ARGB int; else {@code null}. */
    public static Integer parseRgb(String text) {
        Integer v = digits(text, 6);
        return v == null ? null : 0xFF000000 | v;
    }

    /** Optional {@code #}, then exactly 8 hex digits (ARGB); else {@code null}. */
    public static Integer parseArgb(String text) {
        return digits(text, 8);
    }

    /** Optional {@code #}, then exactly 6 (promoted to opaque) or 8 (ARGB) hex digits; else {@code null}. */
    public static Integer parseArgbOrRgb(String text) {
        Integer argb = parseArgb(text);
        return argb != null ? argb : parseRgb(text);
    }

    private static Integer digits(String text, int count) {
        if (text == null) return null;
        String v = text.trim();
        if (v.startsWith("#")) v = v.substring(1);
        if (v.length() != count || !isHex(v)) return null;
        return (int) Long.parseLong(v, 16);
    }
}
