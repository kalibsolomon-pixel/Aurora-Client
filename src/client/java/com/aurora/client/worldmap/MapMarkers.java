package com.aurora.client.worldmap;

import com.aurora.client.config.AuroraConfig.Waypoint;
import com.aurora.client.config.AuroraConfig.WaypointMarker;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.ui.util.UiLayerCache;
import com.aurora.client.ui.util.NativeIconGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

/** Mapping silhouettes use Aurora's physical-pixel rasterizer and tintable cached layers.
 * The current pose is resolved before painting: fractional HUD scales retain native edges. */
public final class MapMarkers {
    private static final int MAX_TEMPLATES = 128, MAX_PIXELS = 4 * 1024 * 1024;
    private static final Long2ObjectLinkedOpenHashMap<Template> templates = new Long2ObjectLinkedOpenHashMap<>(32);
    private static long frame = -1;
    private static int pixels;
    private static final class Template {
        final UiLayerCache cache = new UiLayerCache();
        final int width, height;
        long used;
        Template(int width, int height) { this.width = width; this.height = height; }
    }
    private MapMarkers() {}
    public static float density(GuiGraphics g) {
        return Minecraft.getInstance().getWindow().getGuiScale() * (float) Math.hypot(g.pose().m00(), g.pose().m01());
    }
    public static int pixelX(GuiGraphics g, float x, float y) {
        return Math.round((g.pose().m00() * x + g.pose().m10() * y + g.pose().m20()) * Minecraft.getInstance().getWindow().getGuiScale());
    }
    public static int pixelY(GuiGraphics g, float x, float y) {
        return Math.round((g.pose().m01() * x + g.pose().m11() * y + g.pose().m21()) * Minecraft.getInstance().getWindow().getGuiScale());
    }
    public static void blitPixels(GuiGraphics g, UiLayerCache cache, int x, int y, int width, int height, int tint) {
        float gui = Minecraft.getInstance().getWindow().getGuiScale();
        g.pose().pushMatrix(); g.pose().identity(); g.pose().scale(1 / gui, 1 / gui);
        cache.blitAt(g, x, y, width, height, tint);
        g.pose().popMatrix();
    }
    /** Snaps a label's baseline to the device grid, preserving the configured font/scale. */
    public static void text(GuiGraphics g, net.minecraft.client.gui.Font font, String text,
                            float x, float y, int color, boolean shadow) {
        float gui = Minecraft.getInstance().getWindow().getGuiScale();
        float scale = density(g) / gui;
        int px = pixelX(g, x, y), py = pixelY(g, x, y);
        g.pose().pushMatrix(); g.pose().identity(); g.pose().translate(px / gui, py / gui); g.pose().scale(scale, scale);
        g.drawString(font, text, 0, 0, color, shadow);
        g.pose().popMatrix();
    }
    private static void prune() {
        long current = GlassSurface.frame();
        if (frame == current) return;
        frame = current;
        var it = templates.values().iterator();
        while (it.hasNext()) {
            Template t = it.next();
            if (current - t.used <= 120 && templates.size() <= MAX_TEMPLATES && pixels <= MAX_PIXELS) continue;
            pixels -= t.width * t.height; t.cache.dispose(); it.remove();
        }
    }
    public static void dispose() {
        for (Template t : templates.values()) t.cache.dispose();
        templates.clear(); pixels = 0; frame = -1;
    }
    private static void layer(GuiGraphics g, float cx, float cy, float width, float height,
                               float radius, int kind, int color) {
        prune();
        float d = density(g), gui = Minecraft.getInstance().getWindow().getGuiScale();
        int pw = Math.max(2, Math.round(width * d)), ph = Math.max(2, Math.round(height * d));
        int canvasW = NativeIconGeometry.canvasSize(pw, 1) + 2;
        int canvasH = NativeIconGeometry.canvasSize(ph, 1) + 2;
        int pr = Math.round(radius * d), stroke = Math.max(1, Math.round(d * .75f));
        long key = (long) pw << 44 | (long) ph << 28 | (long) pr << 16 | (long) stroke << 8 | kind;
        Template t = templates.getAndMoveToLast(key);
        if (t == null) {
            // Release only entries from completed frames; never a texture referenced this frame.
            var it = templates.values().iterator();
            while ((templates.size() >= MAX_TEMPLATES || pixels + canvasW * canvasH > MAX_PIXELS) && it.hasNext()) {
                Template old = it.next();
                if (old.used == frame) continue;
                pixels -= old.width * old.height; old.cache.dispose(); it.remove();
            }
            if (pixels + canvasW * canvasH > MAX_PIXELS || templates.size() >= MAX_TEMPLATES) {
                // Same engine/quality when an unusually large HUD exhausts the bounded cache.
                float x = pixelX(g, cx, cy) / gui, y = pixelY(g, cx, cy) / gui;
                g.pose().pushMatrix(); g.pose().identity();
                paint(g, x - pw / gui / 2, y - ph / gui / 2, pw / gui, ph / gui, pr / gui, stroke / gui, kind, color);
                g.pose().popMatrix(); return;
            }
            t = new Template(canvasW, canvasH); t.cache.ensureSize(canvasW, canvasH); t.cache.clear();
            RenderUtil.RectSink previous = RenderUtil.beginCapture(t.cache.sink());
            try { paint(g, (canvasW - pw) / (2 * gui), (canvasH - ph) / (2 * gui), pw / gui, ph / gui, pr / gui, stroke / gui, kind, -1); }
            finally { RenderUtil.endCapture(previous); }
            t.cache.commit(key); templates.putAndMoveToLast(key, t); pixels += canvasW * canvasH;
        }
        t.used = frame;
        blitPixels(g, t.cache, pixelX(g, cx, cy) - t.width / 2, pixelY(g, cx, cy) - t.height / 2, t.width, t.height, color);
    }
    private static void paint(GuiGraphics g, float x, float y, float w, float h, float r, float stroke, int kind, int color) {
        if (kind == 1) RenderUtil.drawRoundedOutlineAA(g, x, y, w, h, r, stroke, color);
        else if (kind == 2) {
            RenderUtil.drawRoundedRectAA(g, x + w * .4f, y, w * .2f, h, w * .1f, color);
            RenderUtil.drawRoundedRectAA(g, x, y + h * .4f, w, h * .2f, h * .1f, color);
        } else if (kind == 3) {
            RenderUtil.drawTriangleAA(g, x + w / 2, y, x + w, y + h / 2, x, y + h / 2, color);
            RenderUtil.drawTriangleAA(g, x, y + h / 2, x + w, y + h / 2, x + w / 2, y + h, color);
        } else RenderUtil.drawRoundedRectAA(g, x, y, w, h, r, color);
    }
    public static void waypoint(GuiGraphics g, float x, float y, Waypoint waypoint, boolean selected) {
        int color = waypoint.color | 0xFF000000;
        boolean square = waypoint.marker == WaypointMarker.SQUARE;
        if (selected) layer(g, x, y, 12, 12, square ? 2.5f : 6, 1, 0xFFFFFFFF);
        layer(g, x, y, 8, 8, square ? 1.5f : 4, 0, 0xD0181A1F);
        int kind = waypoint.death || waypoint.marker == WaypointMarker.CROSS ? 2 : square ? 0 : 3;
        layer(g, x, y, 5, 5, square ? .75f : 0, kind, color);
    }
    public static void dot(GuiGraphics g, float x, float y, int color) { layer(g, x, y, 3, 3, 1.5f, 0, color); }
    public static void labelPlate(GuiGraphics g, float cx, float cy, float width, float height, int color) {
        float radius = com.aurora.client.theme.ThemeManager.current().roundness().radiusSmall() * .5f;
        layer(g, cx, cy, width, height, radius, 0, 0xB0181A1F);
        layer(g, cx, cy, width, height, radius, 1, 0xD0000000 | (color & 0xffffff));
    }
    public static void player(GuiGraphics g, float x, float y, double dx, double dz) {
        float gui = Minecraft.getInstance().getWindow().getGuiScale(), scale = density(g) / gui;
        float cx = pixelX(g, x, y) / gui, cy = pixelY(g, x, y) / gui;
        g.pose().pushMatrix(); g.pose().identity();
        arrow(g, cx, cy, (float) dx, (float) dz, 6 * scale, 0xE0181A1F);
        arrow(g, cx, cy, (float) dx, (float) dz, 4.25f * scale, 0xFFFFFFFF);
        g.pose().popMatrix();
    }
    private static void arrow(GuiGraphics g, float x, float y, float dx, float dy, float size, int color) {
        RenderUtil.drawTriangleAA(g, x + dx * size, y + dy * size,
                x - dx * size * .6f - dy * size * .6f, y - dy * size * .6f + dx * size * .6f,
                x - dx * size * .6f + dy * size * .6f, y - dy * size * .6f - dx * size * .6f, color);
    }
}
