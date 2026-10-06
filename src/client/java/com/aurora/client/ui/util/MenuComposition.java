package com.aurora.client.ui.util;

/** Pixel-snapped title-menu composition. Supported logical viewports are at least 240×160. */
public final class MenuComposition {
    public static final int CARD_COUNT = 5, CARD_WIDTH = 180, CARD_HEIGHT = 22, CARD_GAP = 4;
    public static final int LOGO_SIZE = 48, LOGO_GAP = 12, MARGIN = 8;
    public static final int STACK_HEIGHT = CARD_COUNT * CARD_HEIGHT + (CARD_COUNT - 1) * CARD_GAP;
    private MenuComposition() {}

    public record Layout(UiDensity.Rect logo, UiDensity.Rect stack, UiDensity.Rect bounds) {
        /** Painted and interaction bounds are identical, half-open, and never overlap. */
        public UiDensity.Rect card(int index) {
            if (index < 0 || index >= CARD_COUNT) throw new IndexOutOfBoundsException(index);
            return new UiDensity.Rect(stack.x(), stack.y() + index * (CARD_HEIGHT + CARD_GAP),
                    stack.width(), CARD_HEIGHT);
        }
    }

    public static Layout layout(int width, int height) {
        int cardWidth = Math.min(CARD_WIDTH, Math.max(1, width - 2 * MARGIN));
        int mark = Math.min(LOGO_SIZE, Math.max(0, height - 2 * MARGIN - STACK_HEIGHT - LOGO_GAP));
        int brandBand = mark == 0 ? 0 : mark + LOGO_GAP;
        int total = STACK_HEIGHT + brandBand;
        int top = Math.max(0, (height - total) / 2);
        int left = (width - cardWidth) / 2;
        return new Layout(new UiDensity.Rect((width - mark) / 2, top, mark, mark),
                new UiDensity.Rect(left, top + brandBand, cardWidth, STACK_HEIGHT),
                new UiDensity.Rect(left, top, cardWidth, total));
    }
}
