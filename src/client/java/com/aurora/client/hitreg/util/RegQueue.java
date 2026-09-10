package com.aurora.client.hitreg.util;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * From BetterHitreg by Jass (modrinth.com/mod/betterhitreg), integrated into
 * Aurora with the author's permission. Logic is upstream's, moved verbatim —
 * see {@link com.aurora.client.hitreg.BetterHitreg} for the integration notes.
 */
public class RegQueue {
    private final int capacity;
    private final Deque<Integer> delayQueue;
    private long delaySum = 0L;
    private final Deque<Boolean> ghostQueue;
    private int ghostCount = 0;
    private final Deque<Boolean> inconsistencyQueue;
    private int inconsistencyCount = 0;

    public RegQueue(int capacity) {
        this.capacity = capacity;
        this.delayQueue = new ArrayDeque<>(capacity);
        this.ghostQueue = new ArrayDeque<>(capacity);
        this.inconsistencyQueue = new ArrayDeque<>(capacity);
    }

    public void addDelay(int value) {
        if (delayQueue.size() == capacity) delaySum -= delayQueue.removeFirst();
        delayQueue.addLast(value);
        delaySum += value;
    }

    public void addGhost(boolean ghosted) {
        if (ghostQueue.size() == capacity && ghostQueue.removeFirst()) ghostCount--;
        ghostQueue.addLast(ghosted);
        if (ghosted) ghostCount++;
    }

    public void addInconsistency(boolean misplaced) {
        if (inconsistencyQueue.size() == capacity && inconsistencyQueue.removeFirst()) inconsistencyCount--;
        inconsistencyQueue.addLast(misplaced);
        if (misplaced) inconsistencyCount++;
    }

    public int getAverageDelay() {
        if (delayQueue.isEmpty()) return 0;
        return (int) (delaySum / delayQueue.size());
    }

    public int getGhostRatio() {
        if (ghostQueue.isEmpty()) return 0;
        return (int) ((ghostCount * 100L) / ghostQueue.size());
    }

    public int getInconsistencyRatio() {
        if (inconsistencyQueue.isEmpty()) return 0;
        return (int) ((inconsistencyCount * 100L) / inconsistencyQueue.size());
    }

    // ---- Aurora integration additions (surface-level, read-only) ----
    // Per-metric sample sizes so UI rows (Better Hitreg's §4 live value
    // lines) can tell "no data yet" apart from a genuine zero figure —
    // getAverageDelay() returns 0 both ways. Pure reads of the verbatim
    // queues above; the tracking internals stay exactly as upstream
    // wrote them. The three queues fill independently (delays per reg,
    // ghosts per tracked hit, misplaces per knockback/crit hit), hence
    // one accessor per metric rather than a single size().
    public int delaySampleSize()         { return delayQueue.size(); }
    public int ghostSampleSize()         { return ghostQueue.size(); }
    public int inconsistencySampleSize() { return inconsistencyQueue.size(); }
}
