package com.aurora.client.hitreg.util;

import com.aurora.client.hitreg.Hit;
import com.aurora.client.hitreg.Hitreg;

import java.util.*;


/**
 * From BetterHitreg by Jass (modrinth.com/mod/betterhitreg), integrated into
 * Aurora with the author's permission. Logic is upstream's, moved verbatim —
 * see {@link com.aurora.client.hitreg.BetterHitreg} for the integration notes.
 * Aurora keeps only the ghost check that Safe Regs Only reads (2026-11-15);
 * the delay, ghost-ratio and misplace tracking were removed.
 */
public class HitTracker {
    public static final Deque<Hit> hits = new ArrayDeque<>();
    public static final Deque<Animation> animations = new ArrayDeque<>();

    private static boolean cleared;

    public static void add(Object type) {
        if (type instanceof Hit hit) hits.addLast(hit);
        else if (type instanceof Animation animation) animations.addLast(animation);
        cleared = false;
    }

    public static void process() {
        long now = System.currentTimeMillis();
        while (!hits.isEmpty()) {
            Hit hit = hits.peekFirst();
            if (hit == null) break;

            //only process hits older than 500ms
            if (hit.timestamp > now - 500) break;

            long maximumTimestamp = hit.timestamp + 500;

            //remove all animations older than the hit
            while (!animations.isEmpty() && animations.peekFirst().timestamp < hit.timestamp) animations.pollFirst();

            //is the oldest animation within 500ms of the hit, if so, mark it and remove it
            if (!animations.isEmpty() && animations.peekFirst().timestamp <= maximumTimestamp) {
                hit.wasAnimated = true;
                animations.pollFirst();
            }

            //for safe regs
            Hitreg.wasGhosted = !hit.wasAnimated;

            //finished processing this hit, remove it
            hits.pollFirst();
        }
    }

    public static void clear() {
        if (cleared) return;
        if (!hits.isEmpty()) hits.clear();
        if (!animations.isEmpty()) animations.clear();
        cleared = true;
    }
}