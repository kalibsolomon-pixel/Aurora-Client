package com.aurora.client.hud;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Rolling 1-second clicks-per-second tracker.
 * Holds timestamps in a deque and evicts entries older than 1000 ms on every query.
 */
public class CpsTracker {
    private final Deque<Long> clicks = new ArrayDeque<>();

    public void click() {
        clicks.addLast(System.currentTimeMillis());
    }

    public int get() {
        long cutoff = System.currentTimeMillis() - 1000L;
        while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) {
            clicks.pollFirst();
        }
        return clicks.size();
    }
}
