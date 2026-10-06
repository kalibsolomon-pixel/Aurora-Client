package com.aurora.client.hud;

import com.aurora.client.config.AuroraConfig.CrosshairStyle;
import java.util.ArrayList;
import java.util.List;

/** Device-pixel coverage, shared by the HUD and preview. Runs never overlap. */
public final class CrosshairRaster {
    public record Run(int x, int y, int width, int alpha) {}

    private CrosshairRaster() {}

    public static List<Run> raster(CrosshairStyle style, int size, int thickness, int gap, double scale) {
        double outer = style == CrosshairStyle.DOT
                ? (Math.max(1, size) + Math.max(1, thickness) - 1) / 2.0
                : Math.max(1, size) + 0.5;
        double inner = style == CrosshairStyle.DOT
                ? Math.min(Math.max(0, gap), Math.max(0, outer - 0.5))
                : Math.max(0, outer - Math.max(1, thickness));
        // Keep corner arcs visible even when a saved gap exceeds the shape size.
        double cut = Math.min(Math.max(0, gap), outer * 0.5);
        int extent = (int) Math.ceil(outer * scale + 0.5);
        List<Run> runs = new ArrayList<>();
        for (int y = -extent; y < extent; y++) {
            int start = -extent, previous = 0;
            for (int x = -extent; x <= extent; x++) {
                int alpha = 0;
                if (x < extent) {
                    double dx = (x + 0.5) / scale, dy = (y + 0.5) / scale;
                    double distance = style == CrosshairStyle.SQUARE
                            ? Math.max(Math.abs(dx), Math.abs(dy)) : Math.hypot(dx, dy);
                    double edge = distance - outer;
                    if (inner > 0) edge = Math.max(edge, inner - distance);
                    if (style != CrosshairStyle.DOT && cut > 0)
                        edge = Math.max(edge, cut - Math.min(Math.abs(dx), Math.abs(dy)));
                    alpha = (int) Math.round(255 * Math.max(0, Math.min(1, 0.5 - edge * scale)));
                }
                if (alpha != previous) {
                    if (previous > 0) runs.add(new Run(start, y, x - start, previous));
                    start = x;
                    previous = alpha;
                }
            }
        }
        return List.copyOf(runs);
    }
}
