package com.aurora.client.hud;

/**
 * Pure geometry and placement math for Vanilla HUD control (Minecraft-free
 * core, ADR 0003).
 *
 * <p>Covers the six vanilla HUD elements configurable from Aurora's HUD editor:
 * boss bar, action bar, titles, subtitles, toasts, and advancement pop-ups.
 * At default anchor/offset/scale every element lands on vanilla's exact
 * coordinates (except advancement pop-ups, which sit one toast slot below
 * regular toasts so the two frames never overlap in the HUD editor).
 */
public final class VanillaHudLayout {
    private VanillaHudLayout() {}

    /** Vanilla HUD elements controllable from Aurora's HUD editor. */
    public enum Element {
        BOSS_BAR("vanilla_boss_bar", "Boss Bar", HudAnchor.TOP_CENTER, 0, 3, 182, 19),
        ACTION_BAR("vanilla_action_bar", "Action Bar", HudAnchor.BOTTOM_CENTER, 0, -62, 140, 12),
        TITLE("vanilla_title", "Title", HudAnchor.CENTER, 0, -22, 140, 36),
        SUBTITLE("vanilla_subtitle", "Subtitle", HudAnchor.CENTER, 0, 19, 140, 18),
        TOASTS("vanilla_toasts", "Toasts", HudAnchor.TOP_RIGHT, 0, 0, 160, 32),
        ADVANCEMENTS("vanilla_advancements", "Advancements", HudAnchor.TOP_RIGHT, 0, 36, 160, 32);

        public final String id;
        public final String displayName;
        public final HudAnchor defaultAnchor;
        public final int defaultOffsetX;
        public final int defaultOffsetY;
        public final int width;
        public final int height;

        Element(String id, String displayName, HudAnchor defaultAnchor,
                int defaultOffsetX, int defaultOffsetY, int width, int height) {
            this.id = id;
            this.displayName = displayName;
            this.defaultAnchor = defaultAnchor;
            this.defaultOffsetX = defaultOffsetX;
            this.defaultOffsetY = defaultOffsetY;
            this.width = width;
            this.height = height;
        }
    }

    /** Resolved screen-space placement and uniform scale for a controlled element. */
    public record Transform(float x, float y, float scale) {
        public boolean isIdentity(float vanillaX, float vanillaY) {
            return Math.abs(scale - 1.0f) < 0.001f
                    && Math.abs(x - vanillaX) < 0.01f
                    && Math.abs(y - vanillaY) < 0.01f;
        }
    }

    /** Top-left screen coordinate of a module box. */
    public static Transform topLeft(HudAnchor anchor, int offsetX, int offsetY, float scale,
                                    int intrinsicW, int intrinsicH, int screenW, int screenH) {
        int sw = Math.round(intrinsicW * scale);
        int sh = Math.round(intrinsicH * scale);
        float x = anchor.applyX(screenW, sw) + offsetX;
        float y = anchor.applyY(screenH, sh) + offsetY;
        return new Transform(x, y, scale);
    }

    /** Center screen coordinate of a module box (used for centered text like action bar, title, subtitle). */
    public static Transform center(HudAnchor anchor, int offsetX, int offsetY, float scale,
                                   int intrinsicW, int intrinsicH, int screenW, int screenH) {
        int sw = Math.round(intrinsicW * scale);
        int sh = Math.round(intrinsicH * scale);
        float x = anchor.applyX(screenW, sw) + offsetX + sw * 0.5f;
        float y = anchor.applyY(screenH, sh) + offsetY + sh * 0.5f;
        return new Transform(x, y, scale);
    }

    /**
     * Screen-space origin and scale for one sliding vanilla toast instance.
     *
     * <p>Right-anchored modules align right edges and slide from the right
     * (matching vanilla at {@code TOP_RIGHT (0, 0), scale 1}); left-anchored
     * modules align left edges and slide from the left; center-anchored modules
     * align centers and slide vertically. Bottom-anchored modules stack upward
     * so stacked toasts stay on screen.
     */
    public static Transform toast(HudAnchor anchor, int offsetX, int offsetY, float scale,
                                  int screenW, int screenH,
                                  int toastW, int toastH, int firstSlotIndex, float visiblePortion) {
        int modW = Math.round(Element.TOASTS.width * scale);
        int modH = Math.round(Element.TOASTS.height * scale);
        float modX = anchor.applyX(screenW, modW) + offsetX;
        float modY = anchor.applyY(screenH, modH) + offsetY;

        float scaledW = toastW * scale;
        float scaledH = toastH * scale;
        float baseX = switch (anchor) {
            case TOP_RIGHT, MIDDLE_RIGHT, BOTTOM_RIGHT -> modX + modW - scaledW;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> modX + (modW - scaledW) * 0.5f;
            case TOP_LEFT, MIDDLE_LEFT, BOTTOM_LEFT -> modX;
        };

        boolean bottom = anchor == HudAnchor.BOTTOM_LEFT
                || anchor == HudAnchor.BOTTOM_CENTER
                || anchor == HudAnchor.BOTTOM_RIGHT;
        float stackOffset = firstSlotIndex * scaledH;
        float baseY = bottom ? modY - stackOffset : modY + stackOffset;

        float slide = Math.max(0f, Math.min(1f, 1.0f - visiblePortion));
        float x = switch (anchor) {
            case TOP_RIGHT, MIDDLE_RIGHT, BOTTOM_RIGHT -> baseX + slide * scaledW;
            case TOP_LEFT, MIDDLE_LEFT, BOTTOM_LEFT -> baseX - slide * scaledW;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> baseX;
        };
        float y = switch (anchor) {
            case TOP_CENTER, CENTER -> baseY - slide * scaledH;
            case BOTTOM_CENTER -> baseY + slide * scaledH;
            default -> baseY;
        };
        return new Transform(x, y, scale);
    }

    /**
     * Vanilla title fade alpha in {@code [0, 255]}, mirroring Minecraft's
     * {@code Hud.extractTitle} / {@code Gui.extractTitle} math.
     */
    public static int titleAlpha(int titleTime, int fadeInTime, int stayTime, int fadeOutTime, float partialTick) {
        if (titleTime <= 0) return 0;
        float t = titleTime - partialTick;
        int alpha = 255;
        if (titleTime > fadeOutTime + stayTime && fadeInTime > 0) {
            float elapsed = (fadeInTime + stayTime + fadeOutTime) - t;
            alpha = (int) (elapsed * 255.0F / fadeInTime);
        }
        if (titleTime <= fadeOutTime && fadeOutTime > 0) {
            alpha = (int) (t * 255.0F / fadeOutTime);
        }
        return Math.max(0, Math.min(255, alpha));
    }
}
