package com.aurora.client.screen;

import com.aurora.client.feature.impl.WaypointFeature;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.worldmap.WorldMapClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicit recoverable legacy import. Never offers deletion of map or waypoint data. */
public final class MappingDataScreen extends Screen implements ThemedScreen {
    private final Screen parent;
    private String status = "";
    private int x, y, w;
    public MappingDataScreen(Screen parent) { super(Component.literal("Mapping data")); this.parent = parent; }
    @Override protected void init() {
        w = Math.min(344, width - 16); x = (width - w) / 2; y = Math.max(0, (height - 160) / 2);
        var engine = WorldMapClient.get();
        var identity = com.aurora.client.worldmap.MapIdentity.current();
        ButtonWidget terrain = addRenderableWidget(ButtonWidget.semantic(x + 12, y + 72, w - 24, 20,
                Component.literal("Copy legacy terrain into viewed dimension"),
                "Copies matching old map files; existing regions and original files are preserved.",
                () -> { engine.importLegacy(); status = ""; }));
        terrain.active = engine != null && engine.identity() != null;
        ButtonWidget waypoints = addRenderableWidget(ButtonWidget.semantic(x + 12, y + 96, w - 24, 20,
                Component.literal("Copy legacy waypoints into this world"),
                "Copies the old display-name bucket once. Repeating the import does not duplicate markers.", () -> {
                    var feature = WaypointFeature.get();
                    if (feature != null) status = "Copied " + feature.importLegacy() + " waypoints; originals kept.";
                }));
        waypoints.active = identity != null;
        // Done is dismissal, not a third peer of the two import actions: a compact
        // trailing button like every other Aurora dialog footer.
        addRenderableWidget(ButtonWidget.semantic(x + w - 12 - 64, y + 132, 64, 20,
                Component.literal("Done"), "Return to map settings.", this::onClose));
    }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        MaterialSurface.floating(g, x, y, w, 160, Math.round(ThemeManager.current().roundness().radius()), 1);
        // Title shares the dialog's left content edge (no centered, shadowed heading).
        g.drawString(font, title, x + 12, y + 10, ThemeManager.color(ThemeToken.ON_SURFACE), false);
        String text = "Older folders used world display names. Only import if this is the original world. "
                + "Copied data stays isolated here; original files remain untouched.";
        int lineY = y + 24;
        for (var line : font.split(Component.literal(text), w - 24)) {
            g.drawString(font, line, x + 12, lineY, ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY), false); lineY += 10;
        }
        var engine = WorldMapClient.get();
        String message = status.isEmpty() && engine != null ? engine.importStatus() : status;
        g.drawString(font, com.aurora.client.ui.util.AuroraFontRenderer.ellipsize(font, message, w - 24 - 64 - 8, 1),
                x + 12, y + 138, ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY), false);
        super.render(g, mx, my, delta);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
