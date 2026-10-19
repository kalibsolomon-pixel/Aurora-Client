package com.aurora.client.screen;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.AuroraConfig.Waypoint;
import com.aurora.client.feature.impl.WaypointFeature;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.Button;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.component.ThemedScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** One transactional editor for manager and world map. Cancel never mutates a waypoint. */
public final class WaypointEditScreen extends Screen implements ThemedScreen {
    private final Screen parent;
    private final Waypoint original;
    private final Waypoint draft;
    private final boolean creating;
    private final String scope;
    private final EditBox[] fields = new EditBox[7];
    private final String[] labels = {"Name", "X / Y / Z", "Dimension", "Group", "Description", "Color (ARGB)", ""};
    private String error = "";
    private int left, top, panelWidth;
    private ButtonWidget visible, marker;
    private boolean details;

    public WaypointEditScreen(Screen parent, Waypoint waypoint, boolean creating) {
        super(Component.literal(creating ? "New waypoint" : "Edit waypoint"));
        this.parent = parent; this.original = waypoint; this.creating = creating;
        this.draft = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(waypoint), Waypoint.class);
        scope = WaypointFeature.scopeKey();
    }

    @Override protected void init() {
        remember();
        panelWidth = Math.min(344, width - 16);
        left = (width - panelWidth) / 2; top = Math.max(0, (height - 160) / 2);
        String[] values = {draft.name, draft.x + " " + draft.y + " " + draft.z,
                draft.dimension, draft.group, draft.description, String.format("%08X", draft.color)};
        for (int i = 0; i < 6; i++) {
            int y = top + 26 + (i % 3) * 21;
            fields[i] = new EditBox(font, left + 112, y, panelWidth - 124, 18, Component.literal(labels[i]));
            fields[i].setMaxLength(i == 4 ? 256 : 128);
            fields[i].setValue(values[i] == null ? "" : values[i]);
            if (i == 5) fields[i].setResponder(value -> {
                String hex = value.replace("#", "");
                if (hex.matches("[0-9a-fA-F]{8}")) draft.color = (int) Long.parseLong(hex, 16);
            });
            fields[i].visible = (i >= 3) == details;
            addRenderableWidget(fields[i]);
        }
        int half = (panelWidth - 28) / 2;
        visible = addRenderableWidget(ButtonWidget.semantic(left + 10, top + 91, half, 20,
                Component.literal(draft.visible ? "Visible" : "Hidden"), "Shows this waypoint on maps and in-world.",
                () -> { draft.visible = !draft.visible; visible.setMessage(Component.literal(draft.visible ? "Visible" : "Hidden")); }));
        marker = addRenderableWidget(ButtonWidget.semantic(left + 18 + half, top + 91, half, 20,
                Component.literal("Marker: " + draft.marker), "Cycles the waypoint marker style.", () -> {
                    var all = AuroraConfig.WaypointMarker.values();
                    draft.marker = all[(draft.marker.ordinal() + 1) % all.length];
                    marker.setMessage(Component.literal("Marker: " + draft.marker));
                }));
        addRenderableWidget(ButtonWidget.semantic(left + 10, top + 136, half, 20,
                Component.literal("Save"), "Saves this waypoint in the current world.", this::save)
                .glassStyle(Button.GlassStyle.STAINED).primary(true));
        addRenderableWidget(ButtonWidget.semantic(left + 18 + half, top + 136, half, 20,
                Component.literal("Cancel"), "Discards this edit.", this::onClose));
        addRenderableWidget(ButtonWidget.semantic(left + panelWidth - 72, top + 3, 64, 18,
                Component.literal(details ? "Location" : "Details"), "Switches editor fields.", () -> {
                    remember(); details = !details; rebuildWidgets();
                }));
        WaypointFeature feature = WaypointFeature.get();
        boolean tracked = feature != null && original.id.equals(feature.navigationId());
        ButtonWidget navigate = addRenderableWidget(ButtonWidget.semantic(left + 10, top + 113, panelWidth - 20, 20,
                Component.literal(tracked ? "Stop navigation" : "Navigate to waypoint"),
                "Selects this waypoint for direction and distance indicators in every view.", () -> {
                    if (java.util.Objects.equals(scope, WaypointFeature.scopeKey()) && feature != null) {
                        feature.navigateTo(tracked ? null : original); onClose();
                    }
                }));
        navigate.active = !creating;
        setInitialFocus(fields[details ? 3 : 0]);
    }

    private boolean remember() {
        if (fields[0] == null) return true;
        draft.name = fields[0].getValue().trim();
        draft.dimension = fields[2].getValue().trim();
        draft.group = fields[3].getValue().trim();
        draft.description = fields[4].getValue().trim();
        try {
            String[] coordinates = fields[1].getValue().trim().split("[ ,]+");
            if (coordinates.length != 3) throw new IllegalArgumentException();
            int x = Integer.parseInt(coordinates[0]), y = Integer.parseInt(coordinates[1]), z = Integer.parseInt(coordinates[2]);
            if (Math.abs((long) x) > 30_000_000 || Math.abs((long) z) > 30_000_000 || y < -2048 || y > 2048)
                throw new IllegalArgumentException();
            if (!draft.dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException();
            String color = fields[5].getValue().replace("#", "");
            if (!color.matches("[0-9a-fA-F]{8}")) throw new IllegalArgumentException();
            draft.color = (int) Long.parseLong(color, 16);
            draft.x = x; draft.y = y; draft.z = z;
            return true;
        } catch (RuntimeException invalid) {
            error = "Check coordinates, dimension and 8-digit color.";
            return false;
        }
    }

    private void save() {
        if (!remember()) return;
        if (!java.util.Objects.equals(scope, WaypointFeature.scopeKey()) || scope == null) {
            error = "World changed; reopen the editor in that world."; return;
        }
        if (draft.name.isBlank()) draft.name = "Waypoint";
        WaypointFeature feature = WaypointFeature.get();
        if (feature == null) return;
        if (creating) {
            feature.addWaypoint(draft);
            // Keep a newly created marker visible under the bounded nearest-marker budget.
            feature.navigateTo(draft);
        }
        else if (feature.currentWorldWaypoints().contains(original)) {
            original.name = draft.name; original.x = draft.x; original.y = draft.y; original.z = draft.z;
            original.dimension = draft.dimension; original.group = draft.group; original.description = draft.description;
            original.color = draft.color; original.visible = draft.visible; original.marker = draft.marker;
            feature.touch();
        }
        onClose();
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) { save(); return true; }
        return super.keyPressed(event);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        MaterialSurface.floating(g, left, top, panelWidth, 160,
                Math.round(ThemeManager.current().roundness().radius()), 1);
        com.aurora.client.worldmap.MapMarkers.waypoint(g, left + 16, top + 12, draft, false);
        String heading = error.isEmpty() ? title.getString() : error;
        g.drawString(font, com.aurora.client.ui.util.AuroraFontRenderer.ellipsize(font, heading, panelWidth - 112, 1),
                left + 28, top + 8, ThemeManager.color(ThemeToken.ON_SURFACE), false);
        for (int i = details ? 3 : 0; i < (details ? 6 : 3); i++) g.drawString(font, labels[i], left + 12, top + 31 + (i % 3) * 21,
                ThemeManager.color(ThemeToken.ON_SURFACE), false);
        super.render(g, mx, my, delta);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
