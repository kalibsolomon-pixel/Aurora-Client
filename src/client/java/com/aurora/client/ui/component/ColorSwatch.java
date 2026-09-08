package com.aurora.client.ui.component;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.ui.util.UiLayerCache;
import com.aurora.client.util.HoverAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * Themed color swatch — a rounded swatch showing a color (optionally over a
 * checkerboard so alpha reads correctly), with hover + selection feedback.
 *
 * <p>Consolidates the swatch rendering previously duplicated across
 * {@code ColorSetting} (checkerboard + chamfered ring + halo) and
 * {@code AccentSetting} (preset grid + selection ring). All curves render
 * through the high-res AA engine; the base ring uses the {@code BORDER}
 * token, the hover halo uses {@code ON_BACKGROUND} (mode-aware), and the
 * selection ring uses {@code ACCENT}.
 */
public class ColorSwatch extends Widget {

    private final IntSupplier getter;
    private final Runnable onClick;

    private boolean checkerboard = true;
    private boolean selected = false;
    private boolean disabled = false;
    private final HoverAnim hoverAnim = new HoverAnim(140L);

    public ColorSwatch(IntSupplier getter, Runnable onClick) {
        this.getter = getter;
        this.onClick = onClick;
    }

    public ColorSwatch checkerboard(boolean c) {
        this.checkerboard = c;
        return this;
    }

    public ColorSwatch selected(boolean s) {
        this.selected = s;
        return this;
    }

    public ColorSwatch disabled(boolean d) {
        this.disabled = d;
        return this;
    }

    @Override
    public void renderOverlay(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        int color = getter.getAsInt();
        float radius = Math.min(h / 2f, ThemeManager.current().roundness().radiusSmall());

        boolean hover = !disabled && inBounds(mouseX, mouseY, x, y, w, h);
        float hT = hoverAnim.update(hover);
        boolean rest = !disabled && !selected && hT <= 0f;

        if (rest) {
            // Audit P2 residue: a grid/list of swatches re-submitting the
            // checkerboard + color fill + border per frame is the same
            // fill-volume class as the rows/cards — at rest the whole stack
            // is determined by (color, size, theme, scale), so it comes from
            // a shared per-color template (one blit). Hover/selection paint
            // the live shapes for those frames.
            blitSwatchTemplate(g, x, y, w, h, color, radius, checkerboard);
        } else {
            if (checkerboard) {
                drawCheckerboard(g, x, y, w, h, radius);
            }
            int drawColor = disabled ? (color & 0x55FFFFFF) : (color | 0xFF000000);
            RenderUtil.drawRoundedRectAA(g, x, y, w, h, radius, drawColor);
        }

        if (selected) {
            RenderUtil.drawRoundedOutlineAA(g, x - 2, y - 2, w + 4, h + 4, radius + 2, 1.5f,
                    ThemeManager.color(ThemeToken.ACCENT));
        } else {
            if (!rest) {
                RenderUtil.drawRoundedOutlineAA(g, x, y, w, h, radius, 1.0f,
                        ThemeManager.color(ThemeToken.BORDER));
            }
            if (hT > 0f) {
                int haloA = Math.round(90 * hT);
                int onBg = ThemeManager.color(ThemeToken.ON_BACKGROUND) & 0x00FFFFFF;
                RenderUtil.drawRoundedOutlineAA(g, x - 1, y - 1, w + 2, h + 2, radius + 1, 1.0f,
                        (haloA << 24) | onBg);
            }
        }
    }

    // ---- Per-color resting template (see renderOverlay's rest branch) ----
    // Session-static, LRU-capped: distinct colors are bounded by what the
    // user actually sees. Eviction only ever removes the least-recently-USED
    // entry, which by definition was not blitted this frame — so disposing
    // it mid-frame cannot destroy a texture with pending GuiRenderState
    // blits (AGENTS.md §6 convention 10).
    private static final int SWATCH_TPL_CAP = 24;
    private static final Map<Long, UiLayerCache> SWATCH_TEMPLATES =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, UiLayerCache> eldest) {
                    if (size() > SWATCH_TPL_CAP) {
                        eldest.getValue().dispose();
                        return true;
                    }
                    return false;
                }
            };

    private static void blitSwatchTemplate(GuiGraphics g, float x, float y, float w, float h,
                                           int color, float radius, boolean checkerboard) {
        int scale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
        long key = ThemeManager.generation() * 1_000_003L
                ^ (long) scale * 65537L
                ^ (long) Math.round(w) * 7919L
                ^ (long) Math.round(h) * 104729L
                ^ (color & 0xFFFFFFFFL) * 31L
                ^ (checkerboard ? 1L : 0L);
        UiLayerCache cache = SWATCH_TEMPLATES.get(key);
        if (cache == null) {
            cache = new UiLayerCache();
            SWATCH_TEMPLATES.put(key, cache);
        }
        if (!cache.isCurrent(key)) {
            int iw = Math.round(w) + 2, ih = Math.round(h) + 2;
            cache.ensureSize(iw * scale, ih * scale);
            cache.clear();
            RenderUtil.RectSink prev = RenderUtil.beginCapture(cache.sink());
            try {
                // The live at-rest stack — keep in lockstep with the else
                // branch above (checkerboard clipped to the same geometry,
                // opaque color fill, BORDER ring).
                if (checkerboard) {
                    drawCheckerboard(g, 1, 1, w, h, radius);
                }
                RenderUtil.drawRoundedRectAA(g, 1, 1, w, h, radius, color | 0xFF000000);
                RenderUtil.drawRoundedOutlineAA(g, 1, 1, w, h, radius, 1.0f,
                        ThemeManager.color(ThemeToken.BORDER));
            } finally {
                RenderUtil.endCapture(prev);
            }
            cache.commit(key);
        }
        cache.blitAt(g, Math.round(x) - 1, Math.round(y) - 1, Math.round(w) + 2, Math.round(h) + 2);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (disabled || button != 0 || !inBounds(mx, my, x, y, w, h)) return false;
        if (onClick != null) onClick.run();
        return true;
    }

    /**
     * Checkerboard clipped to the rounded-rect geometry. The old version drew
     * plain square cells across the full bounds, so at any non-zero radius
     * the square checker corners bled past the rounded color fill drawn on
     * top (invisible at Square only because the fill is square too). Each
     * row now skips cells outside the arc span using the same corner math as
     * {@code RenderUtil.drawRoundedRectAA}, so the checker silhouette matches
     * the fill exactly.
     */
    private static void drawCheckerboard(GuiGraphics g, float x, float y, float w, float h, float radius) {
        int cell = 4;
        float r = Math.min(radius, Math.min(w, h) / 2f);
        float cyTop = y + r;
        float cyBot = y + h - r;
        for (float dy = 0; dy < h; dy += cell) {
            float rowY = y + dy;
            // Clip conservatively: a checker row covers up to `cell` one-pixel
            // fill rows, each with its own arc span. Clip to the NARROWEST
            // span the row covers — the fill sub-row nearest the shape edge
            // (center at rowY+0.5 for a top corner row, rowY+cell-0.5 for a
            // bottom one) — mirroring drawRoundedRectAA's per-row math
            // (dy = cornerY - (rowY + 0.5); dx = sqrt(r² - dy²)). No checker
            // pixel can then fall outside the fill's boundary.
            float inset;
            if (r <= 0 || rowY >= cyTop && rowY + cell <= cyBot) {
                inset = 0f; // body row — full span
            } else if (rowY < cyTop) {
                float d = cyTop - (rowY + 0.5f);
                inset = d >= r ? r : Math.max(0f, (float) (r - Math.sqrt(r * r - d * d)));
            } else {
                float d = (rowY + cell - 0.5f) - cyBot;
                inset = d >= r ? r : Math.max(0f, (float) (r - Math.sqrt(r * r - d * d)));
            }
            float left = x + inset;
            float right = x + w - inset;
            if (right <= left) continue;
            // Absolute-grid cell indices keep the checker phase identical in
            // every row regardless of the row's arc inset.
            int i0 = (int) Math.floor((left - x) / cell);
            int i1 = (int) Math.ceil((right - x) / cell);
            int j = (int) (rowY - y) / cell;
            for (int i = i0; i < i1; i++) {
                float cellL = Math.max(x + i * cell, left);
                float cellR = Math.min(x + (i + 1) * cell, right);
                if (cellR <= cellL) continue;
                boolean dark = (i + j) % 2 == 0;
                int col = dark ? 0xFF555555 : 0xFFAAAAAA;
                // fillLogical: capture-aware so the resting template can
                // rasterize the checker identically (integer cells, no AA).
                RenderUtil.fillLogical(g, Math.round(cellL), Math.round(rowY),
                        Math.round(cellR), Math.round(Math.min(rowY + cell, y + h)),
                        col);
            }
        }
    }
}
