package com.aurora.client.util;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.ScreenArea;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * GUI Layering (ADR 0009): a {@code GuiRenderState} layer list that also
 * tracks the union of its elements' bounds. Queries outside the union can
 * skip the entire layer. Within the union, the first two elements use their
 * existing rectangles and additional elements use packed coordinates.
 *
 * <p>Bounds are read once, when an element is appended. Any other change to
 * the list stops the index, and {@link #indexed()} sends the caller back to
 * vanilla's own scan, so the answer is always the one vanilla would give.
 */
public final class GuiAreaList<E extends ScreenArea> extends ArrayList<E> {
    // Most HUD layers hold only a couple of elements. Their immutable bounds
    // already exist; allocate the flat index only for additional rectangles.
    private ScreenRectangle first, second;
    private int[] rects;
    private int count;
    private int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
    private boolean indexed = true;

    public GuiAreaList() { super(3); }

    @Override public boolean add(E element) {
        super.add(element);
        if (indexed) record(element.bounds());
        return true;
    }

    private void record(ScreenRectangle bounds) {
        if (bounds == null) return;
        int l = bounds.left(), t = bounds.top(), r = bounds.right(), b = bounds.bottom();
        if (count == 0) {
            first = bounds;
            left = l; top = t; right = r; bottom = b;
            count = 1;
            return;
        }
        if (count == 1) second = bounds;
        else {
            int i = (count - 2) * 4;
            if (rects == null) rects = new int[8];
            else if (i == rects.length) rects = java.util.Arrays.copyOf(rects, i * 2);
            rects[i] = l; rects[i + 1] = t; rects[i + 2] = r; rects[i + 3] = b;
        }
        count++;
        left = Math.min(left, l); top = Math.min(top, t);
        right = Math.max(right, r); bottom = Math.max(bottom, b);
    }

    /** Whether {@link #intersects} still describes every element. */
    public boolean indexed() { return indexed; }

    /** {@code ScreenRectangle.intersects} against each element's bounds, as vanilla's scan tests them. */
    public boolean intersects(ScreenRectangle query) {
        int ql = query.left(), qt = query.top(), qr = query.right(), qb = query.bottom();
        if (left >= qr || right <= ql || top >= qb || bottom <= qt) return false;
        if (count == 1) return true; // The union is the only rectangle.
        // Newest first: an element usually lands on or next to what was just drawn.
        for (int i = (count - 3) * 4; i >= 0; i -= 4) {
            if (rects[i] < qr && rects[i + 2] > ql && rects[i + 1] < qb && rects[i + 3] > qt) return true;
        }
        return second.intersects(query) || first.intersects(query);
    }

    // Anything but appending (or reordering) makes the index stale.
    @Override public void add(int index, E element) { indexed = false; super.add(index, element); }
    @Override public boolean addAll(Collection<? extends E> c) { indexed = false; return super.addAll(c); }
    @Override public boolean addAll(int index, Collection<? extends E> c) { indexed = false; return super.addAll(index, c); }
    @Override public E set(int index, E element) { indexed = false; return super.set(index, element); }
    @Override public E remove(int index) { indexed = false; return super.remove(index); }
    @Override public boolean remove(Object o) { indexed = false; return super.remove(o); }
    @Override public boolean removeAll(Collection<?> c) { indexed = false; return super.removeAll(c); }
    @Override public boolean retainAll(Collection<?> c) { indexed = false; return super.retainAll(c); }
    @Override public boolean removeIf(Predicate<? super E> filter) { indexed = false; return super.removeIf(filter); }
    @Override public void replaceAll(UnaryOperator<E> operator) { indexed = false; super.replaceAll(operator); }
    @Override protected void removeRange(int from, int to) { indexed = false; super.removeRange(from, to); }
    @Override public List<E> subList(int from, int to) { indexed = false; return super.subList(from, to); }
    @Override public E removeFirst() { indexed = false; return super.removeFirst(); }
    @Override public E removeLast() { indexed = false; return super.removeLast(); }
    @Override public void addFirst(E element) { indexed = false; super.addFirst(element); }
    @Override public void addLast(E element) { indexed = false; super.addLast(element); }
    /** A copy gets its own bounds array, so later changes to either list stay apart. */
    @Override public Object clone() {
        GuiAreaList<?> copy = (GuiAreaList<?>) super.clone();
        if (rects != null) copy.rects = rects.clone();
        return copy;
    }
    @Override public void clear() {
        super.clear();
        first = second = null;
        count = 0;
        left = top = Integer.MAX_VALUE;
        right = bottom = Integer.MIN_VALUE;
        indexed = true;
    }
}
