package com.aurora.client.screen;

import com.aurora.client.feature.impl.WaypointFeature;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.worldmap.WorldMapClient;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicit recoverable legacy import. Never offers deletion of map or waypoint data. */
public final class MappingDataScreen extends Screen implements ThemedScreen {
    /** The window owns the one backdrop capture; child buttons/rows use cached faces
     *  (a per-button blur tinted each control by whatever sat behind it). */
    @Override public boolean parentGlassOnly() { return true; }
    private final Screen parent;
    private String status = "";
    private int x, y, w, h;
    public MappingDataScreen(Screen parent) { super(Component.literal("Mapping data")); this.parent = parent; }
    private static final String EXPLANATION = "Older folders used world display names. Only import if this is the original world. "
            + "Copied data stays isolated here; original files remain untouched.";
    @Override protected void init() {
        // Uniform window (compact pass, 2026-10-30).
        var frame = com.aurora.client.ui.util.UiDensity.window(width, height);
        w = frame.width(); h = frame.height(); x = frame.x(); y = frame.y();
        int edge = com.aurora.client.ui.util.UiDensity.TITLE_X;
        int bh = com.aurora.client.ui.util.UiDensity.HEADER_BUTTON_H;
        int textLines = font.split(Component.literal(EXPLANATION), Math.max(1, w - 2 * edge)).size();
        int buttonsTop = y + com.aurora.client.ui.util.UiDensity.HEADER + 8 + textLines * 11 + 10;
        var engine = WorldMapClient.get();
        var identity = com.aurora.client.worldmap.MapIdentity.current();
        ButtonWidget terrain = addRenderableWidget(ButtonWidget.semantic(x + edge, buttonsTop, w - 2 * edge, bh,
                Component.literal("Copy legacy terrain into viewed dimension"),
                "Copies matching old map files; existing regions and original files are preserved.",
                () -> { engine.importLegacy(); status = ""; }));
        terrain.active = engine != null && engine.identity() != null;
        ButtonWidget waypoints = addRenderableWidget(ButtonWidget.semantic(x + edge, buttonsTop + bh + 6, w - 2 * edge, bh,
                Component.literal("Copy legacy waypoints into this world"),
                "Copies the old display-name bucket once. Repeating the import does not duplicate markers.", () -> {
                    var feature = WaypointFeature.get();
                    if (feature != null) status = "Copied " + feature.importLegacy() + " waypoints; originals kept.";
                }));
        waypoints.active = identity != null;
        // Done is dismissal, not a third peer of the two import actions: a compact
        // trailing button like every other Aurora dialog footer.
        addRenderableWidget(ButtonWidget.semantic(x + w - com.aurora.client.ui.util.UiDensity.FRAME_EDGE - 64, y + h - com.aurora.client.ui.util.UiDensity.FRAME_EDGE - bh, 64, bh,
                Component.literal("Done"), "Return to map settings.", this::onClose)
                .glassStyle(com.aurora.client.ui.component.Button.GlassStyle.STAINED).primary(true));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int radius = AuroraTheme.RADIUS;
        int edge = com.aurora.client.ui.util.UiDensity.TITLE_X;
        com.aurora.client.ui.component.GlassSurface.window(g, x, y, w, h, radius, com.aurora.client.ui.component.MaterialSurface.frameInset(radius), width, height);
        // Title shares the dialog's left content edge in the shared TITLE role.
        g.text(font, com.aurora.client.ui.util.AuroraFontRenderer.role(title.getString(), com.aurora.client.ui.util.AuroraFontRenderer.Role.TITLE),
                x + edge, com.aurora.client.ui.util.AuroraFontRenderer.roleTextY(font, y + com.aurora.client.ui.util.UiDensity.HEADER_TOP, com.aurora.client.ui.util.UiDensity.TOOL, com.aurora.client.ui.util.AuroraFontRenderer.Role.TITLE),
                ThemeManager.color(ThemeToken.ON_SURFACE), false);
        int lineY = y + com.aurora.client.ui.util.UiDensity.HEADER + 8;
        for (var line : font.split(Component.literal(EXPLANATION), Math.max(1, w - 2 * edge))) {
            g.text(font, line, x + edge, lineY, ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY), false); lineY += 11;
        }
        var engine = WorldMapClient.get();
        String message = status.isEmpty() && engine != null ? engine.importStatus() : status;
        g.text(font, com.aurora.client.ui.util.AuroraFontRenderer.ellipsize(font, message, Math.max(0, w - edge - com.aurora.client.ui.util.UiDensity.FRAME_EDGE - 64 - 8), 1),
                x + edge, com.aurora.client.ui.util.AuroraFontRenderer.textY(font, y + h - com.aurora.client.ui.util.UiDensity.FRAME_EDGE - com.aurora.client.ui.util.UiDensity.HEADER_BUTTON_H, com.aurora.client.ui.util.UiDensity.HEADER_BUTTON_H),
                ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY), false);
        super.extractRenderState(g, mx, my, delta);
    }
    @Override public void onClose() { com.aurora.client.adapter.NativeUi.setScreen(minecraft, parent); }
    @Override public boolean isPauseScreen() { return false; }
}
