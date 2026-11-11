package com.aurora.client.ui.util;

/**
 * Shared Aurora geometry in logical GUI pixels; never scales the font or pose.
 *
 * <p>Compact pass (2026-10-30): every windowed Aurora screen uses the SAME frame
 * ({@link #window}) — the main screen, feature details, managers, editors and pickers no
 * longer each pick a size — and rows, controls, cards and gaps shrink together with the
 * compact UI text (see {@code AuroraFontRenderer.compactUi}) so a window shows more options
 * before it scrolls.
 */
public final class UiDensity {
    private UiDensity() {}

    // ---- The one window frame ----
    /** 336×288: the content well (254×252) shows EXACTLY a 3×3 grid of the
     * 80×80 cards — the owner's "approximately 3×3" target (2026-10-30). */
    public static final int WINDOW_WIDTH = 336, WINDOW_HEIGHT = 288, WINDOW_MARGIN = 12;
    /** Legacy names for the frame; kept so existing callers read the uniform size. */
    public static final int MAIN_WIDTH = WINDOW_WIDTH, MAIN_HEIGHT = WINDOW_HEIGHT;

    // ---- Chrome ----
    /** Title/toolbar band at the top of every window. */
    public static final int HEADER = 24;
    public static final int SIDEBAR = 64, INSET = 6;
    /** Header buttons (Reset/Done/New/Done…) and toolbar icon buttons. */
    public static final int HEADER_BUTTON_H = 16, HEADER_BUTTON_W = 48, TOOL = 16;
    /** Sidebar navigation items. */
    public static final int TAB_W = 52, TAB_H = 18, TAB_PITCH = 20;

    // ---- Content ----
    /** Feature cards keep their original size (owner ruling, 2026-10-30). */
    public static final int CARD_WIDTH = 80, CARD_HEIGHT = CARD_WIDTH, GRID_GAP = 6;
    public static final int LIST_HEIGHT = 32, LIST_GAP = 4, ROW_GAP = 2;
    public static final int SIMPLE_ROW = 18, STACKED_ROW = 28, INLINE_SLIDER = 20;
    public static final int MIN_TRACK = 96, MARK = 28;
    /** Trailing field controls (dropdown, keybind, action button) and their widths. */
    public static final int FIELD_H = 14, ENUM_W = 84, KEY_W = 72;
    /** Icon-only navigation action (arrow_forward glyph, no text). */
    public static final int ACTION_W = 28;
    public static final int TOGGLE_W = 24, TOGGLE_H = 12;
    /** Color swatches are small SQUARES (owner ruling, 2026-10-30) — one
     * size for the field-height family, matching FIELD_H rows. */
    public static final int SWATCH_W = 14, SWATCH_H = 14;
    /** Section caption row and the band above a non-leading caption. */
    public static final int SECTION_ROW = 14, SECTION_GAP = 8;

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    /** The uniform window: {@value #WINDOW_WIDTH}×{@value #WINDOW_HEIGHT}, centered, shrunk to fit. */
    // Window history: 420×272 (4-column grid) → 336×288 (3×3 grid, 2026-10-30).
    public static Rect window(int width, int height) {
        int w = Math.min(WINDOW_WIDTH, Math.max(1, width - 2 * WINDOW_MARGIN));
        int h = Math.min(WINDOW_HEIGHT, Math.max(1, height - 2 * WINDOW_MARGIN));
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }
    public static Rect mainFrame(int width, int height) { return window(width, height); }
    public static Rect detailFrame(int width, int height) { return window(width, height); }
    /** Feature identity starts at the same content inset as the ordinary row labels. */
    public static int detailTitleX(Rect frame) { return frame.x() + 12; }
    public static Rect mainContent(Rect frame) {
        boolean horizontal = frame.width() < 320;
        int left = horizontal ? INSET : SIDEBAR + INSET;
        int top = horizontal ? HEADER + TAB_H + 2 * INSET : HEADER + INSET;
        return new Rect(frame.x() + left, frame.y() + top, Math.max(1, frame.width() - left - 12),
                Math.max(1, frame.height() - top - INSET));
    }
    public static int columns(int width) { return Math.max(1, Math.min(8, (width + GRID_GAP) / (CARD_WIDTH + GRID_GAP))); }
    public static int extent(int count, int height, int gap) { return count == 0 ? 0 : count * height + (count - 1) * gap; }
}
