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
import com.aurora.client.util.HexText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

/** One transactional editor for manager and world map. Cancel never mutates a waypoint. */
public final class WaypointEditScreen extends Screen implements ThemedScreen {
    /** The window owns the one backdrop capture; child buttons/rows use cached faces
     *  (a per-button blur tinted each control by whatever sat behind it). */
    @Override public boolean parentGlassOnly() { return true; }
    private final Screen parent;
    private final Waypoint original;
    private final Waypoint draft;
    private final boolean creating;
    private final String scope;
    private final EditBox[] fields = new EditBox[7];
    private final String[] labels = {"Name", "X / Y / Z", "Dimension", "Group", "Description", "Color (ARGB)", ""};
    private String error = "";
    /** Whether {@link #error} names a field problem (cleared live) rather than a save-time condition. */
    private boolean errorFromFields;
    private int left, top, panelWidth;
    private ButtonWidget visible, marker;

    public WaypointEditScreen(Screen parent, Waypoint waypoint, boolean creating) {
        super(Component.literal(creating ? "New waypoint" : "Edit waypoint"));
        this.parent = parent; this.original = waypoint; this.creating = creating;
        this.draft = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(waypoint), Waypoint.class);
        scope = WaypointFeature.scopeKey();
    }

    /** Field rows: label column, field height and pitch inside the uniform window. */
    private static final int LABEL_W = 100, FIELD_H = 14, PITCH = 18, CAPTION = 12;
    private static final String[] GROUPS = {"Location", "Details"};
    private int frameHeight;

    private int fieldY(int i) {
        int y0 = top + com.aurora.client.ui.util.UiDensity.HEADER + 4;
        return y0 + (i / 3) * (CAPTION + 3 * PITCH + 4) + CAPTION + (i % 3) * PITCH;
    }

    @Override protected void init() {
        remember();
        // Uniform window (compact pass, 2026-10-30): every field is visible at once under
        // two captions, so the former "Show details" switch is gone.
        var frame = com.aurora.client.ui.util.UiDensity.window(width, height);
        panelWidth = frame.width(); left = frame.x(); top = frame.y(); frameHeight = frame.height();
        String[] values = {draft.name, draft.x + " " + draft.y + " " + draft.z,
                draft.dimension, draft.group, draft.description, String.format("%08X", draft.color)};
        for (int i = 0; i < 6; i++) {
            fields[i] = new EditBox(font, left + 12 + LABEL_W, fieldY(i), panelWidth - 24 - LABEL_W, FIELD_H, Component.literal(labels[i]));
            fields[i].setMaxLength(i == 4 ? 256 : 128);
            fields[i].setValue(values[i] == null ? "" : values[i]);
            if (i == 1 || i == 2) {
                // The checked fields re-validate live, so a field error leaves
                // the moment the form becomes valid again (§11.12).
                fields[i].setResponder(value -> clearFieldError());
            } else if (i == 5) {
                fields[i].setResponder(value -> {
                    Integer argb = HexText.parseArgb(value);
                    if (argb != null) draft.color = argb;
                    clearFieldError();
                });
            }
            addRenderableWidget(fields[i]);
        }
        int bh = com.aurora.client.ui.util.UiDensity.HEADER_BUTTON_H;
        int half = (panelWidth - 28) / 2;
        int optionsY = fieldY(5) + PITCH + 6;
        visible = addRenderableWidget(ButtonWidget.semantic(left + 12, optionsY, half, bh,
                Component.literal(visibilityLabel()), "Shows this waypoint on maps and in-world.",
                () -> { draft.visible = !draft.visible; visible.setMessage(Component.literal(visibilityLabel())); }));
        marker = addRenderableWidget(ButtonWidget.semantic(left + 16 + half, optionsY, half, bh,
                Component.literal(markerLabel()), "Cycles the waypoint marker style.", () -> {
                    var all = AuroraConfig.WaypointMarker.values();
                    draft.marker = all[(draft.marker.ordinal() + 1) % all.length];
                    marker.setMessage(Component.literal(markerLabel()));
                }));
        WaypointFeature feature = WaypointFeature.get();
        boolean tracked = feature != null && original.id.equals(feature.navigationId());
        ButtonWidget navigate = addRenderableWidget(ButtonWidget.semantic(left + 12, optionsY + bh + 4, panelWidth - 24, bh,
                Component.literal(tracked ? "Stop navigation" : "Navigate to waypoint"),
                "Selects this waypoint for direction and distance indicators in every view.", () -> {
                    if (java.util.Objects.equals(scope, WaypointFeature.scopeKey()) && feature != null) {
                        feature.navigateTo(tracked ? null : original); onClose();
                    }
                }));
        navigate.active = !creating;
        // Actions anchored to the window's bottom edge; the primary action is rightmost.
        int bw = 72, by = top + frameHeight - 8 - bh;
        addRenderableWidget(ButtonWidget.semantic(left + panelWidth - 12 - bw - 4 - bw, by, bw, bh,
                Component.literal("Cancel"), "Discards this edit.", this::onClose));
        addRenderableWidget(ButtonWidget.semantic(left + panelWidth - 12 - bw, by, bw, bh,
                Component.literal("Save"), "Saves this waypoint in the current world.", this::save)
                .glassStyle(Button.GlassStyle.STAINED).primary(true));
        setInitialFocus(fields[0]);
    }

    private String visibilityLabel() { return draft.visible ? "Shown on maps" : "Hidden from maps"; }
    private String markerLabel() {
        String name = draft.marker.name();
        return "Marker: " + name.charAt(0) + name.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Names the first invalid field (reading order), or null when the form is
     * valid — the heading's error states are specific, not a checklist
     * (design language §11.12). Pure check: nothing is mutated.
     */
    private String fieldError() {
        String[] coordinates = fields[1].getValue().trim().split("[ ,]+");
        int x, y, z;
        try {
            if (coordinates.length != 3) throw new IllegalArgumentException();
            x = Integer.parseInt(coordinates[0]);
            y = Integer.parseInt(coordinates[1]);
            z = Integer.parseInt(coordinates[2]);
        } catch (RuntimeException invalid) {
            return "X / Y / Z: use three numbers";
        }
        if (Math.abs((long) x) > 30_000_000 || Math.abs((long) z) > 30_000_000 || y < -2048 || y > 2048)
            return "X / Y / Z: out of range";
        if (!fields[2].getValue().trim().matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            return "Dimension: like minecraft:overworld";
        if (HexText.parseArgb(fields[5].getValue()) == null)
            return "Color: 8 hex digits, like 6633CCFF";
        return null;
    }

    /** Live clear: a field error leaves as soon as the form validates again. */
    private void clearFieldError() {
        if (errorFromFields && fieldError() == null) {
            error = "";
            errorFromFields = false;
        }
    }

    private boolean remember() {
        if (fields[0] == null) return true;
        String problem = fieldError();
        if (problem != null) {
            error = problem;
            errorFromFields = true;
            return false;
        }
        draft.name = fields[0].getValue().trim();
        draft.dimension = fields[2].getValue().trim();
        draft.group = fields[3].getValue().trim();
        draft.description = fields[4].getValue().trim();
        String[] coordinates = fields[1].getValue().trim().split("[ ,]+");
        draft.x = Integer.parseInt(coordinates[0]);
        draft.y = Integer.parseInt(coordinates[1]);
        draft.z = Integer.parseInt(coordinates[2]);
        draft.color = HexText.parseArgb(fields[5].getValue());
        return true;
    }

    private void save() {
        if (!remember()) return;
        if (!java.util.Objects.equals(scope, WaypointFeature.scopeKey()) || scope == null) {
            error = "World changed; reopen the editor in that world.";
            errorFromFields = false;
            return;
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
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) { save(); return true; }
        return super.keyPressed(event);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        MaterialSurface.floatingPanel(g, left, top, panelWidth, frameHeight,
                Math.round(ThemeManager.current().roundness().radius()));
        int headerMid = top + com.aurora.client.ui.util.UiDensity.HEADER / 2;
        com.aurora.client.worldmap.MapMarkers.waypoint(g, left + 16, headerMid, draft, false);
        // A validation error replaces the heading in the semantic error color, so
        // it reads as feedback rather than as a renamed dialog.
        String heading = error.isEmpty() ? title.getString() : error;
        g.text(font, com.aurora.client.ui.util.AuroraFontRenderer.ellipsize(font, heading, panelWidth - 40, 1),
                left + 28, headerMid - font.lineHeight / 2 + 1, ThemeManager.color(error.isEmpty() ? ThemeToken.ON_SURFACE : ThemeToken.STATUS_ERROR), false);
        for (int group = 0; group < 2; group++)
            g.text(font, GROUPS[group], left + 12, fieldY(group * 3) - CAPTION,
                    MaterialSurface.color(ThemeToken.ON_BACKGROUND_MUTED), false);
        for (int i = 0; i < 6; i++) g.text(font, labels[i], left + 12, fieldY(i) + (FIELD_H - font.lineHeight) / 2 + 1,
                ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY), false);
        super.extractRenderState(g, mx, my, delta);
    }
    @Override public void onClose() { com.aurora.client.adapter.NativeUi.setScreen(minecraft, parent); }
    @Override public boolean isPauseScreen() { return false; }
}
