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
    /** 460×308: balanced 3:2 landscape window; the content well (338×252) fits an exact 4×3 grid
     * of 80×80 cards (zero bottom clipping) and an exact 8-row list, while giving Settings tabs,
     * detail pages and the Profiles tools row generous breathing room. */
    public static final int WINDOW_WIDTH = 460, WINDOW_HEIGHT = 308, WINDOW_MARGIN = 12;
    /** Legacy names for the frame; kept so existing callers read the uniform size. */
    public static final int MAIN_WIDTH = WINDOW_WIDTH, MAIN_HEIGHT = WINDOW_HEIGHT;

    // ---- Frame room (owner, 2026-11-18) ----
    /** Every window keeps its controls, text and scroll clips {@value #FRAME_EDGE} px inside
     * its outer edge. The bevel band takes up to {@value #FRAME_BAND} px of that; the rest is
     * the gap to content, so a full-width band never crowds a control. */
    public static final int FRAME_EDGE = 12, FRAME_GAP = 6, FRAME_BAND = FRAME_EDGE - FRAME_GAP;
    /** Scrollbar track and its gap from the content it scrolls; the track ends at FRAME_EDGE. */
    public static final int SCROLLBAR_W = 3, SCROLLBAR_GAP = 5;

    // ---- Chrome ----
    /** Header buttons (Reset/Done/New/Done…) and toolbar icon buttons. */
    public static final int HEADER_BUTTON_H = 20, HEADER_BUTTON_W = 54, TOOL = 20;
    /** Top of the header row: the frame room plus a row's 4 px inset, the same inset as the title
     * column and the row controls, so a header button clears the lit top band as far as the title
     * does (owner, 2026-11-18: Done sat 4 px closer to the border than everything else). */
    public static final int HEADER_TOP = FRAME_EDGE + 4;
    /** Title/toolbar band at the top of every window: the header inset, then one toolbar row. */
    public static final int HEADER = HEADER_TOP + TOOL;
    /** Breathing band between the header/toolbar row and the scrolling body. */
    public static final int HEADER_BODY_GAP = 8;
    /** Sidebar navigation items. */
    public static final int TAB_W = 76, TAB_H = 22, TAB_PITCH = 26;
    /** Extra vertical separation between navigation tabs and the Profiles action. */
    public static final int SIDEBAR_GROUP_GAP = 10;
    public static final int SIDEBAR = FRAME_EDGE + TAB_W, INSET = 14;

    // ---- Content ----
    /** Feature cards keep their original size (owner ruling, 2026-10-30). */
    public static final int CARD_WIDTH = 80, CARD_HEIGHT = CARD_WIDTH, GRID_GAP = 6;
    /** Minimum responsive feature-card width when the window shrinks below {@link #WINDOW_WIDTH}. */
    public static final int MIN_CARD_WIDTH = 72;
    /** List rows 28 px with 4 px gap: eight full rows fit the 252 px well cleanly. */
    public static final int LIST_HEIGHT = 28, LIST_GAP = 4, ROW_GAP = 4;
    public static final int SIMPLE_ROW = 22, STACKED_ROW = 34, INLINE_SLIDER = 24;
    public static final int MIN_TRACK = 96, MARK = 28;
    /** Trailing field controls (dropdown, keybind, action button) and their widths. */
    public static final int FIELD_H = 18, ENUM_W = 104, KEY_W = 88;
    /** Icon-only navigation action (arrow_forward glyph, no text). */
    public static final int ACTION_W = 32;
    public static final int TOGGLE_W = 28, TOGGLE_H = 14;
    /** Color swatches are small SQUARES (owner ruling, 2026-10-30) — balanced with FIELD_H rows. */
    public static final int SWATCH_W = 16, SWATCH_H = 16;
    /** Section caption row and the band above a non-leading caption. */
    public static final int SECTION_ROW = 18, SECTION_GAP = 14;

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    /** The uniform window: {@value #WINDOW_WIDTH}×{@value #WINDOW_HEIGHT}, centered, shrunk to fit. */
    // Window history: 420×272 (4-column grid) → 336×288 (3×3 grid, 2026-10-30) → 336×292 (header inset, 2026-11-18).
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
    public static float headerY(float itemHeight) { return HEADER_TOP + (TOOL - itemHeight) / 2f; }
    /** Whole-pixel {@link #headerY}: header buttons, and text lines one pixel lower for their ink. */
    public static int headerTop(int itemHeight) { return HEADER_TOP + (TOOL - itemHeight) / 2; }
    /** Right inset of a scrolling column: the frame room plus the scrollbar and its gap. */
    public static int scrollRightInset() { return FRAME_EDGE + SCROLLBAR_W + SCROLLBAR_GAP; }
    public static Rect mainContent(Rect frame) {
        boolean horizontal = frame.width() < 320;
        int left = horizontal ? FRAME_EDGE : SIDEBAR + INSET;
        int top = horizontal ? HEADER + HEADER_BODY_GAP + TAB_H + HEADER_BODY_GAP : HEADER + HEADER_BODY_GAP;
        return new Rect(frame.x() + left, frame.y() + top, Math.max(1, frame.width() - left - scrollRightInset()),
                Math.max(1, frame.height() - top - FRAME_EDGE));
    }
    public static int columns(int width) {
        if (width >= extent(4, CARD_WIDTH, GRID_GAP)) {
            return Math.max(1, Math.min(8, (width + GRID_GAP) / (CARD_WIDTH + GRID_GAP)));
        }
        return Math.max(1, Math.min(4, (width + GRID_GAP) / (MIN_CARD_WIDTH + GRID_GAP)));
    }
    /** Responsive feature-card width that fills {@code width} across {@code columns} without trailing gaps. */
    public static int gridCardWidth(int width, int columns) {
        int cols = Math.max(1, columns);
        return Math.max(1, (width - (cols - 1) * GRID_GAP) / cols);
    }
    /** Keeps feature cards square as they scale down, never taller than {@link #CARD_HEIGHT}. */
    public static int gridCardHeight(int cardWidth) { return Math.min(CARD_HEIGHT, Math.max(1, cardWidth)); }
    /** Column gap for the responsive feature grid: absorbs a full extra pixel per gap when divisible. */
    public static int gridColumnGap(int width, int columns, int cardWidth) {
        if (columns <= 1) return GRID_GAP;
        int rem = width - extent(columns, cardWidth, GRID_GAP);
        return GRID_GAP + Math.max(0, rem / (columns - 1));
    }
    /** Horizontal centering inset (0 or 1 px) for any odd remainder left after {@link #gridColumnGap}. */
    public static int gridOffsetX(int width, int columns, int cardWidth, int columnGap) {
        return Math.max(0, (width - extent(columns, cardWidth, columnGap)) / 2);
    }
    public static int extent(int count, int height, int gap) { return count == 0 ? 0 : count * height + (count - 1) * gap; }
}
