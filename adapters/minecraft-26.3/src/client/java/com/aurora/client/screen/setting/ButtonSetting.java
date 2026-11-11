package com.aurora.client.screen.setting;

import com.aurora.client.ui.component.Button;
import com.aurora.client.ui.interaction.MinecraftSemanticFeedback;
import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * Single-action button row. Used for things like a "Preview" sound button
 * sitting next to a sound selector — the button does its work and returns
 * (no state change tracked here).
 *
 * <p>Every ButtonSetting is semantic (Phase A complete): the row's shared
 * Button keeps the pixels, hover, and press animation, while a
 * {@link SemanticActionControl} owns the activation convergence (enabled
 * gate, exactly-once behavior, the semantic click), focus participation,
 * and narration. The former non-semantic constructors were removed once the
 * last registry consumer migrated — the {@code semantic()} factories are the
 * only construction path.
 */
public class ButtonSetting extends FeatureSetting {
    private static final int CONTROL_H = 28;
    private static final int BTN_H = com.aurora.client.ui.util.UiDensity.FIELD_H;
    /** The icon-only navigation face (arrow_forward) — the owner's "[->"
     *  symbol replacing the old default "Preview" text on every button whose
     *  job is to take the user to another screen. */
    private static final String NAVIGATE_GLYPH =
            com.aurora.client.screen.FeatureIcons.get("_action_navigate");

    private final Button button;
    private final SemanticActionControl interactionControl;
    /** True = icon-only navigation face; false = the explicit text label. */
    private final boolean iconFace;

    private int lastWidth = 240;

    private ButtonSetting(String label, String buttonText,
                          Supplier<String> accessibleState,
                          Runnable onPress,
                          SemanticActionControl.PointerRouting pointerRouting) {
        this(label, buttonText, false, accessibleState, onPress, pointerRouting);
    }

    private ButtonSetting(String label, String buttonText, boolean iconFace,
                          Supplier<String> accessibleState,
                          Runnable onPress,
                          SemanticActionControl.PointerRouting pointerRouting) {
        super(label);
        this.iconFace = iconFace;
        this.button = new Button(iconFace ? "" : buttonText, () -> {}).glassBackground(true);
        SemanticAction action = SemanticAction.button(
                Component.literal(label),
                () -> {
                    String description = currentDescription();
                    return description != null
                            ? Component.literal(description)
                            : Component.empty();
                },
                () -> {
                    String state = accessibleState != null ? accessibleState.get() : null;
                    return state != null
                            ? Component.literal(state)
                            : Component.empty();
                },
                () -> !isDisabled(),
                onPress);
        this.interactionControl = new SemanticActionControl(action,
                MinecraftSemanticFeedback.INSTANCE,
                this.button::triggerPressAnimation,
                pointerRouting);
    }

    /** The semantic construction path — every ButtonSetting is semantic.
     *  The no-text factory is the NAVIGATION face: an arrow_forward glyph
     *  instead of the old default "Preview" label (owner ruling 2026-10-30 —
     *  the arrow means "goes to another screen"). Non-navigation actions
     *  pass an explicit buttonText. */
    public static ButtonSetting semantic(String label, Runnable onPress) {
        return new ButtonSetting(label, "", true, null, onPress,
                SemanticActionControl.PointerRouting.MANUAL);
    }

    public static ButtonSetting semantic(String label, String buttonText, Runnable onPress) {
        return new ButtonSetting(label, buttonText, null, onPress,
                SemanticActionControl.PointerRouting.MANUAL);
    }

    public static ButtonSetting semantic(String label, String buttonText,
                                         Supplier<String> accessibleState,
                                         Runnable onPress) {
        return new ButtonSetting(label, buttonText, accessibleState, onPress,
                SemanticActionControl.PointerRouting.MANUAL);
    }

    @Override public ButtonSetting description(String desc) { super.description(desc); return this; }
    @Override public ButtonSetting description(java.util.function.Supplier<String> desc) { super.description(desc); return this; }

    @Override public int baseHeight() { return compactRow == null ? CONTROL_H : compactRow.height(); }
    @Override public int height() { return baseHeight() + descriptionHeight(lastWidth); }

    /** Compact layout kind: the narrow icon column for the navigation face. */
    com.aurora.client.ui.util.SettingRowLayout.Kind compactKind() {
        return iconFace
                ? com.aurora.client.ui.util.SettingRowLayout.Kind.ACTION
                : com.aurora.client.ui.util.SettingRowLayout.Kind.BUTTON;
    }

    private int btnW() { return iconFace ? com.aurora.client.ui.util.UiDensity.ACTION_W
            : com.aurora.client.ui.util.UiDensity.KEY_W; }

    /** The navigation arrow drawn where a text button carries its label. */
    private void drawFace(GuiGraphicsExtractor ctx, int btnX, int btnY, int w, int h) {
        if (!iconFace) return;
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;
        int color = isDisabled()
                ? com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.ON_BACKGROUND_MUTED)
                : com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.MATERIAL_TEXT);
        com.aurora.client.ui.util.MaterialIconRenderer.drawIcon(ctx, tr, NAVIGATE_GLYPH,
                btnX + w / 2f, btnY + h / 2f, 9, color);
    }

    /**
     * Pre-dim surface (§6 convention 6): the embedded shared Button already
     * carries the split — this drives its glass pass at the row's live
     * geometry (the same rect render computes), so on a screen running the
     * structural pass the button's surface paints pre-dim and its
     * renderOverlay paints the label only. The Button itself stamps the
     * frame; when no pass ran, it keeps painting in place (legacy order).
     */
    @Override
    public void renderGlassPass(GuiGraphicsExtractor ctx, int x, int y, int width) {
        if (!com.aurora.client.ui.component.GlassSurface.passOpen()) return; // legacy frame order
        int w = btnW();
        int btnX = controlX(x, width, w);
        int btnY = controlY(y, CONTROL_H, BTN_H);
        button.layout(btnX, btnY, w, BTN_H);
        interactionControl.setBounds(btnX, btnY, w, BTN_H);
        boolean disabled = isDisabled();
        button.disabled(disabled).focused(interactionControl.isFocused() && !disabled);
        button.renderGlassPass(ctx, btnX, btnY, w, BTN_H);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;

        // Left side of the row is the label; the button sits on the right.
        if (compactRow != null) renderCompactLabel(ctx, x, y, mouseX, mouseY, AuroraTheme.TEXT_PRIMARY);
        else renderWrappedLabel(ctx, label, x + 12, y + (CONTROL_H - tr.lineHeight) / 2, Math.max(0,width-(btnW()+38)), AuroraTheme.TEXT_PRIMARY, mouseX, mouseY, isDisabled());

        int w = btnW();
        int btnX = controlX(x, width, w);
        int btnY = controlY(y, CONTROL_H, BTN_H);

        button.layout(btnX, btnY, w, BTN_H);
        syncInteraction(btnX, btnY, mouseX, mouseY);
        syncCompactControl(x, y);
        button.render(ctx, btnX, btnY, w, BTN_H, mouseX, mouseY);
        drawFace(ctx, btnX, btnY, w, BTN_H);

        renderDescription(ctx, x, y + CONTROL_H, width);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        syncCompactControl(rowX, rowY);
        if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;
        return interactionControl.activateFromPointer(mouseX, mouseY, button);
    }

    @Override
    public SemanticActionControl interactionControl() {
        return interactionControl;
    }

    private void syncInteraction(int x, int y, int mouseX, int mouseY) {
        interactionControl.setBounds(x, y, btnW(), BTN_H);
        interactionControl.updatePointer(mouseX, mouseY);
        boolean disabled = isDisabled();
        button.disabled(disabled).focused(interactionControl.isFocused() && !disabled);
    }
}
