package com.aurora.client.screen;

import com.aurora.client.config.profile.ProfileHotkey;
import com.aurora.client.config.profile.ProfileManager;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.Button;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.MaterialIconRenderer;
import com.aurora.client.ui.util.UiDensity;
import com.aurora.client.util.AuroraKey;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * One profile's options (Profiles refresh, 2026-11-12): its icon (through
 * {@link IconPickerScreen}), name, hotkey, Duplicate and Delete. Opened from
 * the settings glyph on a profile card; a centered dialog on the shared
 * window frame, like {@link ColorPickerScreen}.
 *
 * <p>Delete is destructive and asks once more before it acts; it is hidden
 * for the {@code default} profile and the active profile, which the manager
 * refuses to delete.
 */
public class ProfileOptionsScreen extends Screen implements ThemedScreen {
    /** The window owns the one backdrop capture; its buttons use cached faces. */
    @Override public boolean parentGlassOnly() { return true; }

    private static final int ROW_H = 16;
    private static final int LABEL_W = 56;
    private static final int ICON_WELL = 40;

    private final Screen parent;
    private String name;

    private int panelX, panelY, panelW, panelH;
    private int colX, colW, iconRowY, nameRowY, hotkeyRowY;

    private EditBox nameField;
    private ButtonWidget hotkeyBtn;
    private ButtonWidget deleteBtn;
    private boolean listening;
    private final ProfileHotkey.Capture capture = new ProfileHotkey.Capture();
    private boolean deleteArmed;

    public ProfileOptionsScreen(Screen parent, String name) {
        super(Component.literal("Profile options"));
        this.parent = parent;
        this.name = name;
    }

    @Override
    protected void init() {
        var frame = UiDensity.window(this.width, this.height);
        panelX = frame.x(); panelY = frame.y(); panelW = frame.width(); panelH = frame.height();
        colW = Math.min(panelW - 32, 300);
        colX = panelX + (panelW - colW) / 2;
        iconRowY = panelY + UiDensity.HEADER + 12;
        nameRowY = iconRowY + ICON_WELL + 14;
        hotkeyRowY = nameRowY + ROW_H + 10;
        int actionsY = hotkeyRowY + ROW_H + 14;
        int fieldX = colX + LABEL_W, fieldW = colW - LABEL_W;

        this.addRenderableWidget(ButtonWidget.semantic(colX + ICON_WELL + 12, iconRowY + 4,
                colW - ICON_WELL - 12, ROW_H, Component.literal("Change icon"),
                "Opens the icon browser.", this::openIconPicker).glassBackground(true));

        nameField = new EditBox(this.font, fieldX, nameRowY, fieldW, ROW_H, Component.literal("Name"));
        nameField.setMaxLength(48);
        nameField.setValue(name);
        this.addRenderableWidget(nameField);

        hotkeyBtn = this.addRenderableWidget(ButtonWidget.semantic(fieldX, hotkeyRowY, fieldW, ROW_H,
                Component.literal(hotkeyLabel()),
                "Sets the key that switches to this profile in game. Hold Ctrl, Shift or Alt first for a combination. Esc or Backspace clears it.",
                () -> {
                    listening = !listening;
                    capture.reset();
                    hotkeyBtn.setMessage(Component.literal(hotkeyLabel()));
                }).glassBackground(true));

        boolean deletable = !"default".equals(name)
                && !name.equals(ProfileManager.getInstance().currentProfile());
        int half = (colW - 6) / 2;
        this.addRenderableWidget(ButtonWidget.semantic(colX, actionsY, deletable ? half : colW, ROW_H,
                Component.literal("Duplicate"),
                "Creates a copy of this profile, including all of its settings.",
                this::duplicate).glassBackground(true));
        deleteBtn = null;
        if (deletable) {
            deleteBtn = this.addRenderableWidget(ButtonWidget.semantic(colX + colW - half, actionsY, half, ROW_H,
                    Component.literal(deleteArmed ? "Confirm delete" : "Delete"),
                    "Deletes this profile and its file. Press again to confirm.",
                    this::delete).destructive(true));
        }

        int bh = UiDensity.HEADER_BUTTON_H, bw = UiDensity.HEADER_BUTTON_W;
        this.addRenderableWidget(ButtonWidget.semantic(panelX + panelW - 12 - bw, panelY + panelH - 8 - bh, bw, bh,
                Component.literal("Done"), "Saves the name and returns.", this::onClose)
                .glassStyle(Button.GlassStyle.STAINED).primary(true));
    }

    private String hotkeyLabel() {
        if (listening) return ProfileManagerScreen.captureLabel(capture.pending());
        return ProfileManagerScreen.hotkeyName(ProfileManager.getInstance().binding(name));
    }

    private void openIconPicker() {
        commitRename();
        String profile = name;
        com.aurora.client.adapter.NativeUi.setScreen(this.minecraft, new IconPickerScreen(this,
                "Icon for " + profile, ProfileManager.getInstance().icon(profile),
                icon -> ProfileManager.getInstance().setIcon(profile, icon)));
    }

    private void duplicate() {
        commitRename();
        String copy = uniqueName(name + " Copy");
        if (ProfileManager.getInstance().duplicate(name, copy)) {
            close("Duplicated → " + copy);
        }
    }

    private void delete() {
        if (!deleteArmed) {
            deleteArmed = true;
            if (deleteBtn != null) deleteBtn.setMessage(Component.literal("Confirm delete"));
            return;
        }
        if (ProfileManager.getInstance().delete(name)) {
            close("Deleted " + name);
        }
    }

    /** Applies an edited name; an empty or taken name keeps the old one. */
    private void commitRename() {
        if (nameField == null) return;
        String value = nameField.getValue().trim();
        if (value.isEmpty() || value.equals(name)) {
            nameField.setValue(name);
            return;
        }
        if (ProfileManager.getInstance().rename(name, value)) {
            name = value;
        } else {
            nameField.setValue(name);
        }
    }

    private static String uniqueName(String base) {
        var existing = ProfileManager.getInstance().listProfileNames();
        if (!existing.contains(base)) return base;
        for (int n = 2; n < 1000; n++) {
            String candidate = base + " " + n;
            if (!existing.contains(candidate)) return candidate;
        }
        return base + " " + System.currentTimeMillis();
    }

    /** Returns to the Profiles grid, with a toast there when there is news. */
    private void close(String notice) {
        if (notice != null && parent instanceof ProfileManagerScreen profiles) profiles.notice(notice);
        com.aurora.client.adapter.NativeUi.setScreen(this.minecraft, parent);
    }

    @Override
    public void onClose() {
        String before = name;
        commitRename();
        close(before.equals(name) ? null : "Renamed → " + name);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (listening) {
            boolean clear = key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_BACKSPACE;
            finishCapture(capture.press(clear ? AuroraKey.UNBOUND
                    : com.aurora.client.adapter.NativeKeyboard.toStored(key), clear));
            return true;
        }
        if (nameField != null && nameField.isFocused()
                && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)) {
            commitRename();
            nameField.setFocused(false);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        // A modifier released before a second key binds the modifier itself.
        if (listening) {
            finishCapture(capture.release(com.aurora.client.adapter.NativeKeyboard.toStored(event.key())));
            return true;
        }
        return super.keyReleased(event);
    }

    /** Applies a finished capture, or refreshes the "Ctrl + …" label while it waits (null). */
    private void finishCapture(ProfileHotkey done) {
        if (done != null) {
            listening = false;
            ProfileManager.getInstance().setHotkey(name, done);
        }
        hotkeyBtn.setMessage(Component.literal(hotkeyLabel()));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Waiting for a key: a click cancels instead (the Keybind setting's rule),
        // unless it lands on the hotkey button, whose own press toggles it off.
        if (listening && !hotkeyBtn.isMouseOver(event.x(), event.y())) {
            listening = false;
            capture.reset();
            hotkeyBtn.setMessage(Component.literal(hotkeyLabel()));
            return true;
        }
        if (deleteArmed && (deleteBtn == null || !deleteBtn.isMouseOver(event.x(), event.y()))) {
            deleteArmed = false;
            if (deleteBtn != null) deleteBtn.setMessage(Component.literal("Delete"));
        }
        if (nameField != null && !nameField.isMouseOver(event.x(), event.y()) && nameField.isFocused()) {
            commitRename();
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (GlassSurface.liveWorldBackdrop()) return;
        GlassSurface.renderMenuPanorama(this, g, delta);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        float radius = ThemeManager.current().roundness().radiusLarge();
        GlassSurface.beginGlassPass();
        GlassSurface.container(ctx, panelX, panelY, panelW, panelH, radius);
        GlassSurface.overlayDim(ctx, this.width, this.height);
        MaterialSurface.parent(ctx, panelX, panelY, panelW, panelH, radius);

        int onBackground = MaterialSurface.color(ThemeToken.ON_BACKGROUND);
        int secondary = ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY);
        ctx.text(this.font, AuroraFontRenderer.ellipsize(this.font, name, panelW - 24, 0),
                panelX + 12, panelY + (UiDensity.HEADER - this.font.lineHeight) / 2 + 1, onBackground, false);

        // The icon in a well, its name under the Change icon button.
        float small = ThemeManager.current().roundness().radiusSmall();
        MaterialSurface.selection(ctx, colX, iconRowY, ICON_WELL, ICON_WELL, small, false, 0);
        String icon = ProfileManager.getInstance().icon(name);
        MaterialIconRenderer.drawIcon(ctx, this.font, ProfileManagerScreen.profileGlyph(icon),
                colX + ICON_WELL / 2f, iconRowY + ICON_WELL / 2f, 26f, ThemeManager.color(ThemeToken.ACCENT));
        String iconName = IconCatalog.glyph(icon).isEmpty() ? "Default" : icon.replace('_', ' ');
        ctx.text(this.font, AuroraFontRenderer.ellipsize(this.font, iconName, colW - ICON_WELL - 12, 0),
                colX + ICON_WELL + 12, iconRowY + 4 + ROW_H + 6, secondary, false);

        int labelDy = (ROW_H - this.font.lineHeight) / 2 + 1;
        ctx.text(this.font, "Name", colX, nameRowY + labelDy, secondary, false);
        ctx.text(this.font, "Hotkey", colX, hotkeyRowY + labelDy, secondary, false);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        super.removed();
        MaterialSurface.disposeTemplates();
    }
}
