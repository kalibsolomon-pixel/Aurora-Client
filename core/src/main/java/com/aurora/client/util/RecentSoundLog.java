package com.aurora.client.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Bounded LRU ring of recently played sound ids (most-recent-first, up to 16
 * unique entries). Lets the Sound Volume Mixer surface noises the player just
 * heard without allocating per frame on repeated sounds.
 *
 * <p>Lives in the Minecraft-free core (ADR 0003).
 */
public final class RecentSoundLog {
    public static final int MAX_ENTRIES = 16;

    private static final Deque<String> RECENT = new ArrayDeque<>(MAX_ENTRIES + 1);
    private static volatile String lastHead;

    private RecentSoundLog() {}

    /** Records a played sound id; O(1) no-op when equal to the current head. */
    public static void record(String soundId) {
        if (soundId == null || soundId.isEmpty()) return;
        if (soundId.equals(lastHead)) return;
        synchronized (RECENT) {
            if (soundId.equals(RECENT.peekFirst())) {
                lastHead = soundId;
                return;
            }
            RECENT.remove(soundId);
            RECENT.addFirst(soundId);
            while (RECENT.size() > MAX_ENTRIES) {
                RECENT.removeLast();
            }
            lastHead = soundId;
        }
    }

    /** Returns an immutable snapshot of recent sound ids, newest first. */
    public static List<String> snapshot() {
        synchronized (RECENT) {
            return List.copyOf(new ArrayList<>(RECENT));
        }
    }

    /** Clears recorded history (used in tests or world reset). */
    public static void clear() {
        synchronized (RECENT) {
            RECENT.clear();
            lastHead = null;
        }
    }
}
