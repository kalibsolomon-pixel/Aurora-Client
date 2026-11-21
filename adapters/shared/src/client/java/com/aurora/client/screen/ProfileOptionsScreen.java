package com.aurora.client.screen;

import com.aurora.client.config.profile.ProfileManager;
import com.aurora.client.config.profile.ProfileNameValidator;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.Button;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.FieldMessage;
import com.aurora.client.ui.util.MaterialIconRenderer;
import com.aurora.client.ui.util.UiDensity;
import com.aurora.client.util.AuroraKey;
import com.mojang.blaze3d.platform.InputConstants;
import com.aurora.client.util.AuroraTheme;
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
 * <p>Delete is destructive and asks once more before it acts (press again
 * within 3 s, design language §8.7); it is hidden
 * for the {@code default} profile and the active profile, which the manager
 * refuses to delete.
 */
public class ProfileOptionsScreen extends Screen implements ThemedScreen {
    /** The window owns the one backdrop capture; its buttons use cached faces. */
    @Override public boolean parentGlassOnly() { return true; }

    private static final int ROW_H = UiDensity.HEADER_BUTTON_H;
    private static final int LABEL_W = 68;
    private static final int ICON_WELL = 48;

    private final Screen parent;
    private String name;
    /** Refusal message under the Name field (null = the field is valid); design language §11.12. */
    private String nameError;

    private int panelX, panelY, panelW, panelH;
    private int colX, colW, iconRowY, nameRowY, hotkeyRowY;

    private EditBox nameField;
    private ButtonWidget hotkeyBtn;
    private ButtonWidget deleteBtn;
    private boolean listening;
    private final com.aurora.client.util.KeyCombo.Capture capture = new com.aurora.client.util.KeyCombo.Capture();
    /**
     * Delete's press-again confirmation (design language §8.7): press 1 arms
     * (label "Confirm delete"), press 2 within 3 s deletes, and the timeout,
     * Escape or any other click disarms. Replaces the former ad-hoc
     * {@code deleteArmed} flag, which never disarmed on its own.
     */
    private final com.aurora.client.ui.interaction.ArmedAction deleteConfirm = new com.aurora.client.ui.interaction.ArmedAction();
    /** The label state {@link #syncDeleteButton} last applied (change detection only). */
    private boolean deleteBtnArmed;

    public ProfileOptionsScreen(Screen parent, String name) {
        super(Component.literal("Profile options"));
        this.parent = parent;
        this.name = name;
    }

    @Override
    protected void init() {
        var frame = UiDensity.window(this.width, this.height);
        panelX = frame.x(); panelY = frame.y(); panelW = frame.width(); panelH = frame.height();
        colW = Math.min(panelW - 2 * UiDensity.TITLE_X, 340);
        colX = panelX + (panelW - colW) / 2;
        iconRowY = panelY + UiDensity.HEADER + 14;
        nameRowY = iconRowY + ICON_WELL + 16;
        hotkeyRowY = nameRowY + ROW_H + 12;
        int actionsY = hotkeyRowY + ROW_H + 16;
        int fieldX = colX + LABEL_W, fieldW = colW - LABEL_W;

        this.addRenderableWidget(ButtonWidget.semantic(colX + ICON_WELL + 14, iconRowY + 4,
                colW - ICON_WELL - 14, ROW_H, Component.literal("Change icon"),
                "Opens the icon browser.", this::openIconPicker).glassBackground(true));

        nameField = new EditBox(this.font, fieldX, nameRowY, fieldW, ROW_H, Component.literal("Name"));
        nameField.setMaxLength(48);
        nameField.setValue(name);
        // Live recompute (design language §11.12): the message appears the
        // moment the typed name becomes refusable and clears when it is valid
        // again — the same field-to-message contract the hex fields follow.
        nameField.setResponder(value -> nameError = ProfileNameValidator.problem(
                value.trim(), ProfileManager.getInstance().listProfileNames(), name));
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
                    Component.literal(deleteConfirm.isArmed() ? "Confirm delete" : "Delete"),
                    "Deletes this profile and its file. Press again to confirm.",
                    this::delete).destructive(true));
            deleteBtnArmed = deleteConfirm.isArmed();
        }

        int bh = UiDensity.HEADER_BUTTON_H, bw = UiDensity.HEADER_BUTTON_W;
        this.addRenderableWidget(ButtonWidget.semantic(panelX + panelW - UiDensity.FRAME_EDGE - bw, panelY + panelH - UiDensity.FRAME_EDGE - bh, bw, bh,
                Component.literal("Done"), "Saves the name and returns.", this::done)
                .glassStyle(Button.GlassStyle.STAINED).primary(true));
    }

    private String hotkeyLabel() {
        if (listening) return ProfileManagerScreen.captureLabel(capture.pending());
        return ProfileManagerScreen.hotkeyName(ProfileManager.getInstance().hotkey(name));
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
        if (deleteConfirm.press()) {
            if (ProfileManager.getInstance().delete(name)) {
                close("Deleted " + name);
            }
        }
        syncDeleteButton();
    }

    /**
     * Re-labels Delete from the confirmation state — called on press, on the
     * disarming clicks below, and once per frame, so the 3 s timeout restores
     * "Delete" with no timer (the state the old flag never left on its own).
     */
    private void syncDeleteButton() {
        if (deleteBtn == null) return;
        boolean armed = deleteConfirm.isArmed();
        if (armed == deleteBtnArmed) return;
        deleteBtnArmed = armed;
        deleteBtn.setMessage(Component.literal(armed ? "Confirm delete" : "Delete"));
    }

    /**
     * Applies an edited name. A refused name never silently reverts: the
     * message under the field says why, the user's text stays for editing and
     * {@code false} comes back so Done keeps the screen open (design language
     * §11.12). Validation is {@link ProfileNameValidator} — the manager's own
     * rules, not a copy.
     */
    private boolean commitRename() {
        if (nameField == null) return true;
        String value = nameField.getValue().trim();
        if (value.equals(name)) {
            nameField.setValue(name);
            nameError = null;
            return true;
        }
        String problem = ProfileNameValidator.problem(value,
                ProfileManager.getInstance().listProfileNames(), name);
        if (problem == null && ProfileManager.getInstance().rename(name, value)) {
            name = value;
            nameError = null;
            return true;
        }
        nameError = problem != null ? problem : "Could not rename";
        return false;
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

    /**
     * Done: apply and return. A refused name keeps the screen open with the
     * user's text and the message under the field (design language §11.12) —
     * fix it or Escape to discard.
     */
    private void done() {
        String before = name;
        if (!commitRename()) return;
        close(before.equals(name) ? null : "Renamed → " + name);
    }

    /**
     * Escape/close: a valid name is applied as before; a refused one reverts
     * to the old name (an invalid value is never committed) and the screen
     * still closes — Escape keeps its unwinding meaning.
     */
    @Override
    public void onClose() {
        String before = name;
        if (!commitRename()) {
            nameField.setValue(name);
            nameError = null;
        }
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
            // A refused name keeps focus, the typed text and the message; a
            // valid one commits and releases the field.
            if (commitRename()) nameField.setFocused(false);
            return true;
        }
        // Escape unwinds one layer at a time (design language §8.7): an armed
        // Delete disarms and consumes the press; the next Escape closes. The
        // hotkey capture above keeps priority — its Escape clears the binding.
        if (deleteConfirm.isArmed() && key == InputConstants.KEY_ESCAPE) {
            deleteConfirm.disarm();
            syncDeleteButton();
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
    private void finishCapture(Integer done) {
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
        if (deleteConfirm.isArmed() && (deleteBtn == null || !deleteBtn.isMouseOver(event.x(), event.y()))) {
            deleteConfirm.disarm();
            syncDeleteButton();
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
        syncDeleteButton(); // the confirm timeout restores the label with no timer
        float radius = AuroraTheme.RADIUS_LARGE;
        GlassSurface.beginGlassPass();
        GlassSurface.container(ctx, panelX, panelY, panelW, panelH, radius);
        GlassSurface.overlayDim(ctx, this.width, this.height);
        MaterialSurface.parent(ctx, panelX, panelY, panelW, panelH, radius, MaterialSurface.frameInset(radius));

        int onBackground = MaterialSurface.color(ThemeToken.ON_BACKGROUND);
        int secondary = ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY);
        String fittedTitle = AuroraFontRenderer.ellipsizeRole(this.font, name, Math.max(0, panelW - 2 * UiDensity.TITLE_X), AuroraFontRenderer.Role.TITLE);
        ctx.text(this.font, AuroraFontRenderer.role(fittedTitle, AuroraFontRenderer.Role.TITLE),
                panelX + UiDensity.TITLE_X, AuroraFontRenderer.roleTextY(this.font, panelY + UiDensity.HEADER_TOP, UiDensity.TOOL, AuroraFontRenderer.Role.TITLE), onBackground, false);

        // The icon in a well, its name under the Change icon button.
        float small = AuroraTheme.RADIUS_SMALL;
        MaterialSurface.selection(ctx, colX, iconRowY, ICON_WELL, ICON_WELL, small, false, 0);
        String icon = ProfileManager.getInstance().icon(name);
        MaterialIconRenderer.drawIcon(ctx, this.font, ProfileManagerScreen.profileGlyph(icon),
                colX + ICON_WELL / 2f, iconRowY + ICON_WELL / 2f, 28f, ThemeManager.color(ThemeToken.ACCENT));
        String iconName = IconCatalog.glyph(icon).isEmpty() ? "Default" : icon.replace('_', ' ');
        ctx.text(this.font, AuroraFontRenderer.ellipsize(this.font, iconName, colW - ICON_WELL - 14, 0),
                colX + ICON_WELL + 14, iconRowY + 4 + ROW_H + 6, secondary, false);

        ctx.text(this.font, "Name", colX, AuroraFontRenderer.textY(this.font, nameRowY, ROW_H), secondary, false);
        ctx.text(this.font, "Hotkey", colX, AuroraFontRenderer.textY(this.font, hotkeyRowY, ROW_H), secondary, false);

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        // The refusal message sits in the gap under the Name field (the 10 px
        // band between the rows), fitted to the field's own column.
        if (nameError != null) {
            FieldMessage.draw(ctx, this.font, nameError, colX + LABEL_W, nameRowY + ROW_H, colW - LABEL_W);
        }

        if (!listening && hotkeyBtn != null) {
            int hk = ProfileManager.getInstance().hotkey(name);
            var conflicts = hk >= 0
                    ? com.aurora.client.util.KeybindConflictLookup.conflictsForProfile(name, hk)
                    : java.util.List.<String>of();
            if (!conflicts.isEmpty()) {
                int warnCol = MaterialSurface.color(ThemeToken.STATUS_WARNING);
                if (!hotkeyBtn.isFocused()) {
                    com.aurora.client.ui.util.RenderUtil.drawRoundedOutlineAA(ctx,
                            hotkeyBtn.getX(), hotkeyBtn.getY(), hotkeyBtn.getWidth(), hotkeyBtn.getHeight(),
                            AuroraTheme.RADIUS_SMALL, 1f, ThemeManager.withAlpha(warnCol, 0xCC));
                }
                String summary = com.aurora.client.util.KeybindConflicts.summary(conflicts);
                int msgX = colX + LABEL_W, msgY = hotkeyRowY + ROW_H + 2, msgW = colW - LABEL_W;
                String fitted = AuroraFontRenderer.ellipsize(this.font, summary, msgW, 0);
                ctx.text(this.font, fitted, msgX, msgY, warnCol, false);
                com.aurora.client.screen.setting.FeatureSetting.hoverTooltip(hotkeyBtn,
                        com.aurora.client.util.KeybindConflicts.tooltip(conflicts),
                        msgX, hotkeyRowY, msgW, ROW_H + 2 + this.font.lineHeight, mouseX, mouseY);
            }
        }
        com.aurora.client.screen.setting.FeatureSetting.drawPendingTooltip(ctx, panelX, panelY, panelW, panelH);
    }

    @Override
    public void removed() {
        super.removed();
    }
}
