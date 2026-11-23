package com.aurora.client.media;

import java.util.Locale;

/**
 * Immutable snapshot of the active desktop media session (Now Playing HUD).
 *
 * <p>Pure core model — holds no OS or Minecraft types so formatting, progress
 * interpolation and parser edge cases are unit-tested headlessly.
 */
public record MediaTrackState(
        boolean active,
        boolean playing,
        String title,
        String artist,
        String album,
        long positionMs,
        long durationMs,
        long updatedAtMs) {

    public static final MediaTrackState EMPTY =
            new MediaTrackState(false, false, "", "", "", 0L, 0L, 0L);

    /** Identifies the track (not its playback position), e.g. to match it with its cover art. */
    public String trackKey() {
        return title + '\n' + artist + '\n' + album;
    }

    /** Sample track shown in the HUD Editor when no desktop player is active. */
    public static final MediaTrackState SAMPLE =
            new MediaTrackState(true, true, "Sweden", "C418", "Minecraft - Volume Alpha", 102_000L, 215_000L, 0L);

    /**
     * Parses newline-delimited {@code KEY=VALUE} lines returned by the Windows
     * GSMTC helper process. Missing or malformed fields degrade safely to empty/zero.
     */
    public static MediaTrackState parseLines(String raw, long nowMs) {
        if (raw == null || raw.isBlank()) return EMPTY;
        String status = "";
        String title = "";
        String artist = "";
        String album = "";
        long pos = 0L;
        long dur = 0L;

        for (String line : raw.split("\\R")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim().toUpperCase(Locale.ROOT);
            String val = line.substring(eq + 1).trim();
            switch (key) {
                case "STATUS" -> status = val;
                case "TITLE" -> title = sanitize(val);
                case "ARTIST" -> artist = sanitize(val);
                case "ALBUM" -> album = sanitize(val);
                case "POS" -> pos = parseLongSafe(val);
                case "DUR" -> dur = parseLongSafe(val);
                default -> {}
            }
        }

        if (title.isEmpty() && artist.isEmpty()) return EMPTY;
        if ("CLOSED".equalsIgnoreCase(status) || "STOPPED".equalsIgnoreCase(status)) return EMPTY;
        boolean playing = "PLAYING".equalsIgnoreCase(status) || "4".equals(status);
        long clampedDur = Math.max(0L, dur);
        long clampedPos = clampedDur > 0L ? Math.max(0L, Math.min(pos, clampedDur)) : Math.max(0L, pos);
        return new MediaTrackState(true, playing, title, artist, album, clampedPos, clampedDur, nowMs);
    }

    /** Returns smoothly interpolated playback position in ms at {@code nowMs}. */
    public long effectivePositionMs(long nowMs) {
        if (!active) return 0L;
        long base = Math.max(0L, positionMs);
        if (!playing || updatedAtMs <= 0L || nowMs <= updatedAtMs) {
            return durationMs > 0L ? Math.min(base, durationMs) : base;
        }
        long elapsed = Math.min(nowMs - updatedAtMs, 10_000L);
        long est = base + elapsed;
        return durationMs > 0L ? Math.min(est, durationMs) : est;
    }

    /** Returns normalized playback progress in {@code [0, 1]}, or {@code -1f} if duration is unknown. */
    public float progress(long nowMs) {
        if (!active || durationMs <= 0L) return -1f;
        return Math.max(0f, Math.min(1f, (float) effectivePositionMs(nowMs) / (float) durationMs));
    }

    /** Formats a millisecond timestamp as {@code M:SS} or {@code H:MM:SS}. */
    public static String formatTime(long ms) {
        long totalSec = Math.max(0L, ms / 1000L);
        long hours = totalSec / 3600L;
        long minutes = (totalSec % 3600L) / 60L;
        long seconds = totalSec % 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    /** Compact single-line summary ({@code "Title — Artist"} or {@code "Title"}). */
    public String compactLine() {
        if (!active) return "";
        if (artist == null || artist.isEmpty()) return title;
        if (title == null || title.isEmpty()) return artist;
        return title + " \u2014 " + artist;
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static long parseLongSafe(String s) {
        if (s == null || s.isEmpty()) return 0L;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
