package com.aurora.client.performance;

import java.util.Arrays;

/**
 * A set compared by identity that can be walked by slot index, so a per-frame
 * pass over it allocates no iterator. Linear probing with backward-shift
 * deletion; it grows by doubling and never shrinks.
 */
public final class IdentityTable<T> {
    private Object[] slots;
    private int size;

    public IdentityTable(int capacity) {
        if (capacity < 8 || Integer.bitCount(capacity) != 1) throw new IllegalArgumentException("capacity " + capacity);
        slots = new Object[capacity];
    }

    public int size() {
        return size;
    }

    /** Slot count for {@link #at}; slots may be empty. */
    public int capacity() {
        return slots.length;
    }

    /** The element in slot {@code index}, or null. */
    @SuppressWarnings("unchecked")
    public T at(int index) {
        return (T) slots[index];
    }

    public boolean contains(Object value) {
        if (value == null || size == 0) return false;
        Object[] s = slots;
        int mask = s.length - 1;
        for (int i = slot(value, mask); s[i] != null; i = (i + 1) & mask)
            if (s[i] == value) return true;
        return false;
    }

    public boolean add(T value) {
        if (value == null) return false;
        if ((size + 1) * 4 > slots.length * 3) grow();
        Object[] s = slots;
        int mask = s.length - 1;
        int i = slot(value, mask);
        for (; s[i] != null; i = (i + 1) & mask)
            if (s[i] == value) return false;
        s[i] = value;
        size++;
        return true;
    }

    public boolean remove(Object value) {
        if (value == null || size == 0) return false;
        Object[] s = slots;
        int mask = s.length - 1;
        int i = slot(value, mask);
        while (s[i] != value) {
            if (s[i] == null) return false;
            i = (i + 1) & mask;
        }
        // Backward-shift: move later members of the probe run into the gap.
        int gap = i;
        for (int j = (gap + 1) & mask; s[j] != null; j = (j + 1) & mask) {
            int home = slot(s[j], mask);
            if (((j - home) & mask) >= ((j - gap) & mask)) {
                s[gap] = s[j];
                gap = j;
            }
        }
        s[gap] = null;
        size--;
        return true;
    }

    public void clear() {
        if (size == 0) return;
        Arrays.fill(slots, null);
        size = 0;
    }

    private void grow() {
        Object[] old = slots;
        slots = new Object[old.length * 2];
        size = 0;
        for (Object value : old) if (value != null) reinsert(value);
    }

    private void reinsert(Object value) {
        int mask = slots.length - 1;
        int i = slot(value, mask);
        while (slots[i] != null) i = (i + 1) & mask;
        slots[i] = value;
        size++;
    }

    private static int slot(Object value, int mask) {
        int h = System.identityHashCode(value) * 0x9E3779B9;
        return (h ^ (h >>> 16)) & mask;
    }
}
