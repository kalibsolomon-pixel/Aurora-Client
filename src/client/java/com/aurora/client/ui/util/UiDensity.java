package com.aurora.client.ui.util;

/** Compact pilot geometry in logical GUI pixels; never scales the font or pose. */
public final class UiDensity {
    private UiDensity() {}
    public static final int MAIN_WIDTH = 344, MAIN_HEIGHT = 224;
    public static final int HEADER = 32, SIDEBAR = 72, INSET = 8;
    public static final int CARD_WIDTH = 80, CARD_HEIGHT = 52, GRID_GAP = 6;
    public static final int LIST_HEIGHT = 32, ROW_GAP = 4;
    public static final int SIMPLE_ROW = 24, STACKED_ROW = 36, INLINE_SLIDER = 28;
    public static final int MIN_TRACK = 120, MARK = 28;
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
    public static Rect mainFrame(int width, int height) {
        int w = Math.min(MAIN_WIDTH, Math.max(1, width - 16));
        int h = Math.min(MAIN_HEIGHT, Math.max(1, height - 16));
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }
    public static Rect detailFrame(int width, int height) {
        int w = Math.min(MAIN_WIDTH, Math.max(1, width - 24));
        int h = Math.min(300, Math.max(1, height - 24));
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }
    public static Rect mainContent(Rect frame) {
        boolean horizontal = frame.width() < 320;
        int left = horizontal ? INSET : SIDEBAR + INSET;
        int top = horizontal ? 68 : HEADER + INSET;
        return new Rect(frame.x() + left, frame.y() + top, Math.max(1, frame.width() - left - 12),
                Math.max(1, frame.height() - top - INSET));
    }
    public static int columns(int width) { return Math.max(1, Math.min(3, (width + GRID_GAP) / (CARD_WIDTH + GRID_GAP))); }
    public static int extent(int count, int height, int gap) { return count == 0 ? 0 : count * height + (count - 1) * gap; }
}
