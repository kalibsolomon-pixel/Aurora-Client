package com.aurora.client.ui.util;

/**
 * Pixel-snapped title-menu composition (OneClient-style redesign, owner 2026-11-12).
 *
 * <p>Three regions: an icon row at the top right (Aurora, Fullscreen, Quit);
 * a centred column with the mark, the wordmark, three full cards and a row of
 * two half cards; and a right column with the player model above the account
 * card. When the window is too narrow or short for the right column it
 * collapses into an account chip in the top bar, left of the icons, and the
 * model is hidden. Supported logical viewports are at least 240×160.
 */
public final class MenuComposition {
    public static final int CARD_COUNT = 5, FULL_CARDS = 3, CARD_WIDTH = 200, CARD_HEIGHT = 24, CARD_GAP = 6;
    public static final int LOGO_SIZE = 56, LOGO_GAP = 6, WORDMARK_HEIGHT = 20, BRAND_GAP = 14, MARGIN = 8;
    public static final int ICON_COUNT = 3, ICON_SIZE = 20, ICON_GAP = 4;
    /** Space kept clear above and below the centre column for the icon row and the footer line. */
    public static final int TOP_BAND = MARGIN + ICON_SIZE + 4;
    public static final int SIDE_WIDTH = 140, SIDE_INSET = 16, SIDE_GAP = 8, MODEL_WIDTH = 80, MODEL_HEIGHT = 128;
    public static final int ACCOUNT_HEIGHT = 24, CHIP_WIDTH = 116, CHIP_GAP = 6;
    /** Least space between the centre column and the right column. */
    public static final int COLUMN_GAP = 16;
    public static final int STACK_HEIGHT = (FULL_CARDS + 1) * CARD_HEIGHT + FULL_CARDS * CARD_GAP;
    public static final int SIDE_HEIGHT = MODEL_HEIGHT + SIDE_GAP + ACCOUNT_HEIGHT;
    private static final UiDensity.Rect NONE = new UiDensity.Rect(0, 0, 0, 0);
    private MenuComposition() {}

    /**
     * One frame's geometry. Painted and interaction bounds are identical and
     * half-open. {@code model} is empty and {@code account} is the top-bar chip
     * when {@code compact}; a zero-width rect is not drawn.
     */
    public record Layout(UiDensity.Rect logo, UiDensity.Rect wordmark, UiDensity.Rect stack, UiDensity.Rect bounds,
                         UiDensity.Rect icons, UiDensity.Rect model, UiDensity.Rect account, boolean compact) {
        /** Cards 0–2 span the column; 3 and 4 share the last row (Settings, Mods). */
        public UiDensity.Rect card(int index) {
            if (index < 0 || index >= CARD_COUNT) throw new IndexOutOfBoundsException(index);
            int row = Math.min(index, FULL_CARDS);
            int y = stack.y() + row * (CARD_HEIGHT + CARD_GAP);
            if (index < FULL_CARDS) return new UiDensity.Rect(stack.x(), y, stack.width(), CARD_HEIGHT);
            int half = (stack.width() - CARD_GAP) / 2;
            int x = index == FULL_CARDS ? stack.x() : stack.x() + stack.width() - half;
            return new UiDensity.Rect(x, y, half, CARD_HEIGHT);
        }

        /** Top-bar icon {@code index}, left to right. */
        public UiDensity.Rect icon(int index) {
            if (index < 0 || index >= ICON_COUNT) throw new IndexOutOfBoundsException(index);
            return new UiDensity.Rect(icons.x() + index * (ICON_SIZE + ICON_GAP), icons.y(), ICON_SIZE, ICON_SIZE);
        }
    }

    public static Layout layout(int width, int height) {
        int cardWidth = Math.min(CARD_WIDTH, Math.max(1, width - 2 * MARGIN));
        // Keep the column clear of the icon row and footer when the height allows; centring is on the screen.
        int reserve = height - 2 * TOP_BAND >= STACK_HEIGHT ? TOP_BAND : MARGIN;
        int room = Math.max(0, height - 2 * reserve - STACK_HEIGHT);
        int word = room >= WORDMARK_HEIGHT + BRAND_GAP ? WORDMARK_HEIGHT : 0;
        int mark = word == 0 ? 0 : Math.min(LOGO_SIZE, room - WORDMARK_HEIGHT - BRAND_GAP - LOGO_GAP);
        if (mark < 16) mark = 0; // a postage-stamp mark reads worse than none
        int brandBand = word == 0 ? 0 : (mark == 0 ? 0 : mark + LOGO_GAP) + WORDMARK_HEIGHT + BRAND_GAP;
        int total = STACK_HEIGHT + brandBand;
        int top = Math.max(0, (height - total) / 2);
        int left = (width - cardWidth) / 2;
        var logo = new UiDensity.Rect((width - mark) / 2, top, mark, mark);
        int wordY = top + (mark == 0 ? 0 : mark + LOGO_GAP);
        var wordmark = new UiDensity.Rect(left, wordY, word == 0 ? 0 : cardWidth, word);
        var stack = new UiDensity.Rect(left, top + brandBand, cardWidth, STACK_HEIGHT);
        var bounds = new UiDensity.Rect(left, top, cardWidth, total);

        int rowWidth = ICON_COUNT * ICON_SIZE + (ICON_COUNT - 1) * ICON_GAP;
        var icons = new UiDensity.Rect(width - MARGIN - rowWidth, MARGIN, rowWidth, ICON_SIZE);

        // Centred in the space right of the column, so a wide window never strands it at the edge (owner 2026-11-12).
        int columnRight = left + cardWidth;
        int edgeX = width - MARGIN - SIDE_INSET - SIDE_WIDTH;
        int sideX = Math.min(edgeX, columnRight + (width - MARGIN - columnRight - SIDE_WIDTH) / 2);
        boolean fits = sideX >= columnRight + COLUMN_GAP && height - 2 * TOP_BAND >= SIDE_HEIGHT;
        if (fits) {
            int sideY = (height - SIDE_HEIGHT) / 2;
            var model = new UiDensity.Rect(sideX + (SIDE_WIDTH - MODEL_WIDTH) / 2, sideY, MODEL_WIDTH, MODEL_HEIGHT);
            var account = new UiDensity.Rect(sideX, sideY + MODEL_HEIGHT + SIDE_GAP, SIDE_WIDTH, ACCOUNT_HEIGHT);
            return new Layout(logo, wordmark, stack, bounds, icons, model, account, false);
        }
        int chipWidth = Math.min(CHIP_WIDTH, icons.x() - CHIP_GAP - MARGIN);
        var chip = chipWidth < 2 * ICON_SIZE ? NONE
                : new UiDensity.Rect(icons.x() - CHIP_GAP - chipWidth, MARGIN, chipWidth, ICON_SIZE);
        return new Layout(logo, wordmark, stack, bounds, icons, NONE, chip, true);
    }
}
