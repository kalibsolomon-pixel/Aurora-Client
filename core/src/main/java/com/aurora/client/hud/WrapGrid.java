package com.aurora.client.hud;

/**
 * Items laid out in lines of at most {@code perLine}, wrapping into further
 * lines: the Potion HUD's effects. {@code along} is the length of a full line
 * (fewer when there are fewer items), {@code lines} the number of lines.
 *
 * <p>Positions can be flipped per axis, so item 0 sits at the edge nearest
 * the screen border and new items and lines extend toward the centre.
 */
public record WrapGrid(int perLine, int along, int lines) {

    public static WrapGrid of(int count, int perLine) {
        int p = Math.max(1, perLine);
        int n = Math.max(1, count);
        return new WrapGrid(p, Math.min(n, p), (n + p - 1) / p);
    }

    /** Slot of item {@code i} along its line, 0-based; flipped counts from the line's far end. */
    public int alongSlot(int i, boolean flip) {
        int a = i % perLine;
        return flip ? along - 1 - a : a;
    }

    /** Line of item {@code i}, 0-based; flipped counts from the last line. */
    public int lineSlot(int i, boolean flip) {
        int l = i / perLine;
        return flip ? lines - 1 - l : l;
    }
}
