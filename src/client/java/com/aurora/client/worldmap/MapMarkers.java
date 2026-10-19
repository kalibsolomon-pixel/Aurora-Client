package com.aurora.client.worldmap;

import com.aurora.client.config.AuroraConfig.Waypoint;
import com.aurora.client.config.AuroraConfig.WaypointMarker;
import net.minecraft.client.gui.GuiGraphics;

/** Shared small marker silhouettes; represented waypoint color is never themed. */
public final class MapMarkers {
    private MapMarkers() {}
    public static void waypoint(GuiGraphics g, int x, int y, Waypoint waypoint, boolean selected) {
        int color = waypoint.color | 0xFF000000;
        if (selected) {
            g.fill(x - 5, y - 5, x + 6, y - 4, 0xFFFFFFFF);
            g.fill(x - 5, y + 5, x + 6, y + 6, 0xFFFFFFFF);
            g.fill(x - 5, y - 4, x - 4, y + 5, 0xFFFFFFFF);
            g.fill(x + 5, y - 4, x + 6, y + 5, 0xFFFFFFFF);
        }
        g.fill(x - 3, y - 3, x + 4, y + 4, 0xC0000000);
        if (waypoint.death || waypoint.marker == WaypointMarker.CROSS) {
            g.fill(x - 2, y, x + 3, y + 1, color);
            g.fill(x, y - 2, x + 1, y + 3, color);
        } else if (waypoint.marker == WaypointMarker.SQUARE) {
            g.fill(x - 2, y - 2, x + 3, y + 3, color);
        } else {
            g.fill(x, y - 3, x + 1, y + 4, color);
            g.fill(x - 1, y - 2, x + 2, y + 3, color);
            g.fill(x - 2, y - 1, x + 3, y + 2, color);
        }
    }

    public static void player(GuiGraphics g, int x, int y, double dx, double dz) {
        g.fill(x - 2, y - 2, x + 3, y + 3, 0xB0000000);
        g.fill(x - 1, y - 1, x + 2, y + 2, 0xFFFFFFFF);
        for (int i = 2; i <= 6; i++) {
            int sx = x + (int) Math.round(dx * i), sy = y + (int) Math.round(dz * i);
            g.fill(sx, sy, sx + 1, sy + 1, 0xFFFFFFFF);
        }
    }
}
