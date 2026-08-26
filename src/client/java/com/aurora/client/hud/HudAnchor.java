package com.aurora.client.hud;

/**
 * Anchor point a {@link com.aurora.client.hud.module.HudModule} is pinned to.
 * Used so modules stay in the "same" visual corner across resolution changes.
 * Actual screen position is computed as {@code anchor.applyX(screenW, moduleW) + offsetX}.
 */
public enum HudAnchor {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    MIDDLE_LEFT,
    CENTER,
    MIDDLE_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT;

    public int applyX(int screenW, int moduleW) {
        return switch (this) {
            case TOP_LEFT, MIDDLE_LEFT, BOTTOM_LEFT -> 0;
            case TOP_CENTER, CENTER, BOTTOM_CENTER  -> (screenW - moduleW) / 2;
            case TOP_RIGHT, MIDDLE_RIGHT, BOTTOM_RIGHT -> screenW - moduleW;
        };
    }

    public int applyY(int screenH, int moduleH) {
        return switch (this) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> 0;
            case MIDDLE_LEFT, CENTER, MIDDLE_RIGHT -> (screenH - moduleH) / 2;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> screenH - moduleH;
        };
    }

    /** Pick the nearest anchor for a given pixel position — used by the drag editor. */
    public static HudAnchor nearest(int x, int y, int w, int h, int screenW, int screenH) {
        int cx = x + w / 2;
        int cy = y + h / 2;
        int col = cx < screenW / 3 ? 0 : (cx < 2 * screenW / 3 ? 1 : 2);
        int row = cy < screenH / 3 ? 0 : (cy < 2 * screenH / 3 ? 1 : 2);
        return values()[row * 3 + col];
    }
}
