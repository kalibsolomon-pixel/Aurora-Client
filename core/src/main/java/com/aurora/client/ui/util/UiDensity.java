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
    /** 336×288: the content well (252×248) shows a 3×3 grid of the 80×80 cards, the
     * third row just under its last 4 px — the owner's "approximately 3×3" target (2026-10-30). */
    public static final int WINDOW_WIDTH = 336, WINDOW_HEIGHT = 288, WINDOW_MARGIN = 12;
    /** Legacy names for the frame; kept so existing callers read the uniform size. */
    public static final int MAIN_WIDTH = WINDOW_WIDTH, MAIN_HEIGHT = WINDOW_HEIGHT;

    // ---- Frame room (owner, 2026-11-18) ----
    /** Every window keeps its controls, text and scroll clips {@value #FRAME_EDGE} px inside
     * its outer edge. The bevel band takes up to {@value #FRAME_BAND} px of that; the rest is
     * the gap to content, so a full-width band never crowds a control. */
    public static final int FRAME_EDGE = 10, FRAME_GAP = 4, FRAME_BAND = FRAME_EDGE - FRAME_GAP;
    /** Scrollbar track and its gap from the content it scrolls; the track ends at FRAME_EDGE. */
    public static final int SCROLLBAR_W = 3, SCROLLBAR_GAP = 3;

    // ---- Chrome ----
    /** Header buttons (Reset/Done/New/Done…) and toolbar icon buttons. */
    public static final int HEADER_BUTTON_H = 16, HEADER_BUTTON_W = 48, TOOL = 16;
    /** Title/toolbar band at the top of every window: the frame room, then one toolbar row. */
    public static final int HEADER = FRAME_EDGE + TOOL;
    /** Sidebar navigation items. */
    public static final int TAB_W = 52, TAB_H = 18, TAB_PITCH = 20;
    public static final int SIDEBAR = FRAME_EDGE + TAB_W, INSET = 6;

    // ---- Content ----
    /** Feature cards keep their original size (owner ruling, 2026-10-30). */
    public static final int CARD_WIDTH = 80, CARD_HEIGHT = CARD_WIDTH, GRID_GAP = 6;
    /** List rows 24 px (owner decision 2026-11-14; 32 left half of each row empty): nine visible. */
    public static final int LIST_HEIGHT = 24, LIST_GAP = 4, ROW_GAP = 2;
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
    /** Window titles start where the labels inside full-width rows do: the frame room plus a row's
     * 4 px text inset, so a title and the rows under it share one column. */
    public static final int TITLE_X = FRAME_EDGE + 4;
    /** Feature identity starts at the same content inset as the ordinary row labels. */
    public static int detailTitleX(Rect frame) { return frame.x() + TITLE_X; }
    /** Top of an item of the given height centred on the header's toolbar row. */
    public static float headerY(float itemHeight) { return FRAME_EDGE + (TOOL - itemHeight) / 2f; }
    /** Whole-pixel {@link #headerY}: header buttons, and text lines one pixel lower for their ink. */
    public static int headerTop(int itemHeight) { return FRAME_EDGE + (TOOL - itemHeight) / 2; }
    /** Right inset of a scrolling column: the frame room plus the scrollbar and its gap. */
    public static int scrollRightInset() { return FRAME_EDGE + SCROLLBAR_W + SCROLLBAR_GAP; }
    public static Rect mainContent(Rect frame) {
        boolean horizontal = frame.width() < 320;
        int left = horizontal ? FRAME_EDGE : SIDEBAR + INSET;
        int top = horizontal ? HEADER + FRAME_GAP + TAB_H + INSET : HEADER + FRAME_GAP;
        return new Rect(frame.x() + left, frame.y() + top, Math.max(1, frame.width() - left - scrollRightInset()),
                Math.max(1, frame.height() - top - FRAME_EDGE));
    }
    public static int columns(int width) { return Math.max(1, Math.min(8, (width + GRID_GAP) / (CARD_WIDTH + GRID_GAP))); }
    public static int extent(int count, int height, int gap) { return count == 0 ? 0 : count * height + (count - 1) * gap; }
}
