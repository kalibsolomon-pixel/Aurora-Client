package com.aurora.client.ui.component;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Segmented control — an iOS-style split track where the selected segment is
 * filled with the accent. Used for the Theme screen's Mode and Corner Style
 * rows, and the main screen's tab strip.
 *
 * <p>The track renders in the cacheable shape layer; the selected/hover
 * segment fills and labels render live. The selected fill is {@code ACCENT};
 * text uses {@code ON_ACCENT} (selected) and {@code ON_BACKGROUND_SECONDARY}
 * / {@code ON_BACKGROUND} (hover). Track corner radius follows the roundness
 * token. Labels are centered and non-shadowed.
 */
public class SegmentedControl extends Widget {

    private static final float SEG_GAP = 2f;

    private final String[] options;
    private final IntSupplier selectedIndex;
    private final IntConsumer onSelect;

    private boolean disabled = false;
    private float trackH = 20f;

    /**
     * Glass pilot (Theme screen only): each segment becomes its own RAISED
     * glass panel — neutral tint (WINDOW_FILL) for unselected, accent-stained
     * tint ({@link ThemeManager#stainedTint()}) for selected. Selection reads
     * through tint alone; both states share the raised orientation (the
     * hierarchy rule: controls float above the recessed window). The flat
     * track base is suppressed from the cached shape layer while this is on;
     * whenever the glass renderer declines (screenshot suppression, failure)
     * the overlay redraws the full flat track + fills as the fallback.
     */
    private boolean glassEnabled = false;

    public SegmentedControl(String[] options, IntSupplier selectedIndex, IntConsumer onSelect) {
        this.options = options;
        this.selectedIndex = selectedIndex;
        this.onSelect = onSelect;
    }

    public SegmentedControl trackHeight(float h) {
        this.trackH = h;
        return this;
    }

    public SegmentedControl disabled(boolean d) {
        this.disabled = d;
        return this;
    }

    /** Glass pilot — see {@link #glassEnabled}. */
    public SegmentedControl glassEnabled(boolean g) {
        this.glassEnabled = g;
        return this;
    }

    @Override
    public void renderShapes(GuiGraphics g, float x, float y, float w, float h) {
        if (glassEnabled) return; // segments are live glass; track base suppressed
        float radius = Math.min(trackH / 2f, ThemeManager.current().roundness().radiusSmall());
        RenderUtil.drawRoundedRectAA(g, x, y, w, trackH, radius,
                ThemeManager.color(ThemeToken.SURFACE_VARIANT));
    }

    @Override
    public void renderOverlay(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;

        float radius = Math.min(trackH / 2f, ThemeManager.current().roundness().radiusSmall());
        int selected = Math.max(0, Math.min(options.length - 1, selectedIndex.getAsInt()));

        // Glass pilot path: every segment is a live raised-glass panel with
        // the tint carrying the selection state. Hover feedback stays in the
        // LABEL color only — a flat hover fill would occlude the glass. On
        // decline, fall back to the complete flat look (track base + fills).
        boolean glassOk = false;
        if (glassEnabled) {
            glassOk = true;
            for (int i = 0; i < options.length; i++) {
                int sx = segStart(i, x, w);
                int sw = segWidth(i, x, w);
                boolean isSelected = i == selected;
                if (!BlurPanelRenderer.renderPanel(g, sx, y, sw, trackH, radius,
                        BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX,
                        BlurPanelRenderer.Lighting.raised())) {
                    glassOk = false;
                    break;
                }
                RenderUtil.drawRoundedRectAA(g, sx, y, sw, trackH, radius,
                        isSelected ? ThemeManager.stainedTint()
                                   : ThemeManager.color(ThemeToken.WINDOW_FILL));
            }
            if (!glassOk) {
                // Flat fallback: track base first (it was suppressed from the
                // cached layer), then the flat segment fills.
                RenderUtil.drawRoundedRectAA(g, x, y, w, trackH, radius,
                        ThemeManager.color(ThemeToken.SURFACE_VARIANT));
            }
        }

        for (int i = 0; i < options.length; i++) {
            int sx = segStart(i, x, w);
            int sw = segWidth(i, x, w);
            boolean isSelected = i == selected;
            boolean hover = !disabled && !isSelected && inBounds(mouseX, mouseY, sx, y, sw, trackH);

            if (!glassOk) {
                if (isSelected) {
                    RenderUtil.drawRoundedRectAA(g, sx, y, sw, trackH, radius,
                            ThemeManager.color(ThemeToken.ACCENT));
                } else if (hover) {
                    RenderUtil.drawRoundedRectAA(g, sx, y, sw, trackH, radius,
                            ThemeManager.color(ThemeToken.SURFACE_VARIANT));
                }
            }

            int color = disabled ? ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED)
                    : isSelected ? ThemeManager.color(ThemeToken.ON_ACCENT)
                    : hover ? ThemeManager.color(ThemeToken.ON_BACKGROUND)
                    : ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY);

            AuroraFontRenderer.drawCentered(g, tr, options[i], Math.round(sx + sw / 2f),
                    Math.round(y + (trackH - tr.lineHeight) / 2f), color);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (disabled || button != 0 || !inBounds(mx, my, x, y, w, trackH)) return false;
        for (int i = 0; i < options.length; i++) {
            int sx = segStart(i, x, w);
            int sw = segWidth(i, x, w);
            if (mx >= sx && mx < sx + sw) {
                if (i != selectedIndex.getAsInt()) {
                    onSelect.accept(i);
                }
                return true;
            }
        }
        return false;
    }

    private int segStart(int i, float x, float w) {
        return Math.round(x + i * (segWidth() + SEG_GAP));
    }

    private float segWidth() {
        return (w - SEG_GAP * (options.length - 1)) / options.length;
    }

    /** Width of segment {@code i} — the last absorbs integer-division slack. */
    private int segWidth(int i, float x, float w) {
        return i == options.length - 1
                ? Math.round(x + w) - segStart(i, x, w)
                : Math.round(segWidth());
    }
}
