package com.aurora.client.ui.util;

public final class OverlayLayout {
    public static final int MARGIN = 8;
    public static final int GAP = 4;
    public static final long NO_POSITION = Long.MIN_VALUE;

    private OverlayLayout() {}

    public static long tooltip(int left, int top, int right, int bottom,
                               int anchorX, int anchorY, int anchorW, int anchorH, int width, int height) {
        left += MARGIN;
        top += MARGIN;
        right -= MARGIN;
        bottom -= MARGIN;
        if (width > right - left || height > bottom - top) return NO_POSITION;
        int afterX = anchorX + anchorW + GAP;
        int beforeX = anchorX - GAP - width;
        int afterY = anchorY + anchorH + GAP;
        int beforeY = anchorY - GAP - height;
        int x = clamp(afterX + width <= right ? afterX : beforeX, left, right - width);
        if (afterY >= top && afterY + height <= bottom) return position(x, afterY);
        if (beforeY >= top && beforeY + height <= bottom) return position(x, beforeY);
        int y = clamp(anchorY, top, bottom - height);
        if (afterX >= left && afterX + width <= right) return position(afterX, y);
        if (beforeX >= left && beforeX + width <= right) return position(beforeX, y);
        return NO_POSITION;
    }

    public static int popupTop(int below, int aboveEdge, int height, int top, int bottom) {
        if (below + height <= bottom) return Math.max(below, top);
        return clamp(aboveEdge - height, top, Math.max(top, bottom - height));
    }

    public static double scrollToReveal(double target, double maxScroll,
                                        double itemTop, double itemBottom, int top, int bottom, int margin) {
        if (bottom <= top || itemBottom <= itemTop) return target;
        double padding = Math.max(0, Math.min(margin, (bottom - top - (itemBottom - itemTop)) / 2));
        double minimum = itemBottom - bottom + padding;
        double maximum = itemTop - top - padding;
        return Math.max(0, Math.min(maxScroll, Math.min(maximum, Math.max(minimum, target))));
    }

    public static int x(long position) { return (int) (position >> 32); }
    public static int y(long position) { return (int) position; }

    private static long position(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
