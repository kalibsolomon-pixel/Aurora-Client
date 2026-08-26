package you.jass.betterhitreg.utility;

import java.util.ArrayDeque;
import java.util.Deque;

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
}
