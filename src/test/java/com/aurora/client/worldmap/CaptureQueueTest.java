package com.aurora.client.worldmap;

import com.aurora.client.worldmap.capture.CaptureQueue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CaptureQueueTest {
    @Test void fullQueueKeepsInsertionOrderAndAcceptsPreviouslyPolledWork() {
        var queue = new CaptureQueue();
        for (int i = 0; i < 8192; i++) queue.add(i);
        queue.add(Long.MAX_VALUE);
        assertEquals(0L, queue.poll().longValue());
        queue.add(Long.MIN_VALUE);
        queue.requeue(1); // A duplicate must keep its place.
        queue.requeue(0); // Another producer used the slot while this chunk was being captured.
        assertEquals(8193, queue.size());
        for (int i = 1; i < 8192; i++) assertEquals(i, queue.poll().longValue());
        assertEquals(Long.MIN_VALUE, queue.poll().longValue());
        assertEquals(0L, queue.poll().longValue());
        assertNull(queue.poll());
        queue.add(0);
        queue.clear();
        queue.add(0);
        assertEquals(0L, queue.poll().longValue());
    }
}
