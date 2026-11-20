package com.aurora.client.ui.component;

import com.aurora.client.adapter.NativeUi;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.OverlayLayout;
import com.aurora.client.ui.util.WordWrap;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.HoverAnim;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class TooltipPresenter {
    public static final long DWELL_MS = 1500;
    public static final long FADE_MS = 200;
    private static final int MAX_TEXT_WIDTH = 220;
    private static final int PAD_X = 6, PAD_Y = 4;
    private static HoverAnim fade = HoverAnim.symmetric(FADE_MS);
    private static Screen screen;
    private static Screen sourceScreen;
    private static Object hovered, previous;
    private static int hoveredPart, previousPart;
    private static String offered, showing;
    private static long started;
    private static int anchorX, anchorY, anchorW, anchorH;
    private static int shownX, shownY, shownW, shownH;
    private static Prepared layout;
    private static boolean suppressed;

    private record Prepared(String text, Font font, Language language, long generation, int style,
                            int widthLimit, int lineLimit, List<FormattedCharSequence> lines, int width) {}

    private TooltipPresenter() {}

    public static void suppressFrame() { suppressed = true; }

    public static void beginFrame(Screen current) {
        hovered = null;
        offered = null;
        sourceScreen = null;
        suppressed = false;
        if (screen != current) reset(current);
    }

    private static void reset(Screen current) {
        screen = current;
        previous = null;
        showing = null;
        layout = null;
        fade = HoverAnim.symmetric(FADE_MS);
    }

    public static boolean hit(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public static void offer(Object key, String text, int x, int y, int width, int height) {
        offer(key, 0, text, x, y, width, height);
    }

    public static void offer(Object key, int part, String text, int x, int y, int width, int height) {
        if (key == null || text == null || text.isBlank()
                || com.aurora.client.ui.util.RenderUtil.isCapturing()) return;
        sourceScreen = NativeUi.screen(Minecraft.getInstance());
        hovered = key;
        hoveredPart = part;
        offered = text;
        anchorX = x;
        anchorY = y;
        anchorW = width;
        anchorH = height;
    }

    public static void draw(GuiGraphicsExtractor graphics, int left, int top, int width, int height) {
        draw(graphics, left, top, width, height, AuroraConfig.get().reducedMotion);
    }

    public static void draw(GuiGraphicsExtractor graphics, int left, int top, int width, int height, boolean reduced) {
        var minecraft = Minecraft.getInstance();
        Screen current = NativeUi.screen(minecraft);
        if (screen != current) reset(current);
        if (sourceScreen != screen) {
            hovered = null;
            offered = null;
        }
        if (suppressed) {
            hovered = null;
            showing = null;
            layout = null;
            fade.update(false);
            suppressed = false;
        }
        long now = System.nanoTime();
        if (hovered != previous || hoveredPart != previousPart) started = now;
        previous = hovered;
        previousPart = hoveredPart;
        boolean claimed = hovered != null && (now - started >= DWELL_MS * 1_000_000 || showing != null);
        if (claimed) {
            showing = offered;
            shownX = anchorX;
            shownY = anchorY;
            shownW = anchorW;
            shownH = anchorH;
        }
        hovered = null;
        offered = null;
        sourceScreen = null;
        float alpha = fade.update(claimed);
        if (reduced) alpha = claimed ? 1 : 0;
        if (showing == null) return;
        if (!claimed && alpha == 0) {
            showing = null;
            layout = null;
            return;
        }
        if (alpha < .015f) return;

        Font font = minecraft.font;
        int pitch = AuroraFontRenderer.linePitch(font);
        int right = Math.min(left + width, graphics.guiWidth());
        int bottom = Math.min(top + height, graphics.guiHeight());
        left = Math.max(0, left);
        top = Math.max(0, top);
        int maxWidth = Math.min(MAX_TEXT_WIDTH, right - left - 2 * (OverlayLayout.MARGIN + PAD_X));
        int maxHeight = Math.max(shownY - top, bottom - shownY - shownH) - OverlayLayout.MARGIN - OverlayLayout.GAP;
        int maxLines = (maxHeight - 2 * PAD_Y) / pitch;
        if (maxWidth <= 0 || maxLines <= 0) return;

        long generation = ThemeManager.generation();
        int style = AuroraFontRenderer.styleKey();
        Language language = Language.getInstance();
        if (layout == null || !showing.equals(layout.text()) || font != layout.font() || language != layout.language()
                || generation != layout.generation() || style != layout.style()
                || maxWidth != layout.widthLimit() || (maxLines != layout.lineLimit() && layout.lines().size() >= Math.min(maxLines, layout.lineLimit()))) {
            var lines = WordWrap.lines(showing, maxWidth, maxLines, font::width).stream()
                    .map(line -> Component.literal(line).getVisualOrderText()).toList();
            int textWidth = 0;
            for (var line : lines) textWidth = Math.max(textWidth, font.width(line));
            layout = new Prepared(showing, font, language, generation, style, maxWidth, maxLines, lines, textWidth);
        }
        if (layout.lines().isEmpty()) return;
        int boxWidth = layout.width() + 2 * PAD_X;
        int boxHeight = layout.lines().size() * pitch + 2 * PAD_Y;
        long position = OverlayLayout.tooltip(left, top, right, bottom, shownX, shownY, shownW, shownH, boxWidth, boxHeight);
        if (position == OverlayLayout.NO_POSITION) return;
        int x = OverlayLayout.x(position), y = OverlayLayout.y(position);
        graphics.nextStratum();
        float radius = Math.min(AuroraTheme.RADIUS, Math.min(boxWidth, boxHeight) * .5f);
        MaterialSurface.overlayFace(graphics, x, y, boxWidth, boxHeight, radius,
                MaterialSurface.windowCornerClearance(radius, PAD_X, PAD_Y), alpha);
        int color = AuroraAnim.scaleAlpha(MaterialSurface.color(ThemeToken.ON_BACKGROUND), alpha);
        int textY = AuroraFontRenderer.blockY(font, y, boxHeight, layout.lines().size());
        for (int index = 0; index < layout.lines().size(); index++) {
            graphics.text(font, layout.lines().get(index), x + PAD_X, textY, color, false);
            textY += pitch;
        }
    }
}
