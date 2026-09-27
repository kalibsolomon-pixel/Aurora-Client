package com.aurora.client.launcher;

import java.util.Objects;

/** Only gameplay identity; no account, instance, path, renderer or Discord data. */
record ActivitySnapshot(State state, String worldDisplayName, String serverDisplayName, String serverAddress) {
    enum State { MAIN_MENU, SINGLEPLAYER, MULTIPLAYER }

    ActivitySnapshot {
        Objects.requireNonNull(state);
        worldDisplayName = state == State.SINGLEPLAYER ? displayText(worldDisplayName, 128) : null;
        serverDisplayName = state == State.MULTIPLAYER ? displayText(serverDisplayName, 128) : null;
        serverAddress = state == State.MULTIPLAYER ? displayText(serverAddress, 255) : null;
    }

    static ActivitySnapshot mainMenu() {
        return new ActivitySnapshot(State.MAIN_MENU, null, null, null);
    }

    /** The loaded level is authoritative; an open pause/settings screen is irrelevant. */
    static ActivitySnapshot project(boolean levelLoaded, boolean integratedServer,
                                    String worldName, String serverName, String address) {
        if (!levelLoaded) return mainMenu();
        return integratedServer
                ? new ActivitySnapshot(State.SINGLEPLAYER, worldName, null, null)
                : new ActivitySnapshot(State.MULTIPLAYER, null, serverName, address);
    }

    /** Bound both work and output; omit malformed UTF-16, controls and invisible formatting. */
    static String displayText(String value, int maxCodePoints) {
        if (value == null) return null;
        StringBuilder result = new StringBuilder();
        int count = 0;
        int end = Math.min(value.length(), 4096);
        for (int i = 0; i < end && count < maxCodePoints;) {
            int cp = i + 1 == end && Character.isHighSurrogate(value.charAt(i))
                    ? value.charAt(i) : value.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT
                    || Character.getType(cp) == Character.SURROGATE || cp == 0x2028 || cp == 0x2029) continue;
            result.appendCodePoint(cp);
            count++;
        }
        int start = 0;
        int finish = result.length();
        while (start < finish && whitespace(result.codePointAt(start))) start += Character.charCount(result.codePointAt(start));
        while (finish > start && whitespace(result.codePointBefore(finish))) finish -= Character.charCount(result.codePointBefore(finish));
        String text = result.substring(start, finish);
        return text.isEmpty() ? null : text;
    }

    private static boolean whitespace(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
    }

    @Override public String toString() { return "ActivitySnapshot[" + state + "]"; }
}
