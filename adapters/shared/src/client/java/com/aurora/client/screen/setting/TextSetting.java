package com.aurora.client.screen.setting;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.interaction.MinecraftSemanticFeedback;
import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.util.AuroraTheme;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Single-line text setting row backed by an opaque themed {@link EditBox}
 * (obeying glass rule 4: non-search text fields are always opaque, never glass).
 */
public class TextSetting extends FeatureSetting {
    private static final int MIN_ROW_H = 30;
    private static final int FIELD_W = 132;
    private static final int FIELD_H = 18;
    private static final int LABEL_PAD = 12;
    private static final int RIGHT_PAD = 14;

    private final Supplier<String> getter;
    private final Consumer<String> setter;
    private final Component labelText;
    private final EditBox field;

    private int lastWidth = 240;
    private int cachedLabelArea = -1;
    private List<String> cachedLines;
    private int cachedStyleKey = -1;
    private int cachedBaseHeight = MIN_ROW_H;
    private boolean syncing;
    private SemanticActionControl interactionControl;

    public TextSetting(String label, Supplier<String> getter, Consumer<String> setter) {
        super(label);
        this.getter = getter;
        this.setter = setter;
        this.labelText = Component.literal(label);

        Font font = Minecraft.getInstance().font;
        this.field = new EditBox(font, 0, 0, FIELD_W, FIELD_H, this.labelText);
        this.field.setBordered(false);
        this.field.setMaxLength(24);
        this.field.setTextColor(AuroraTheme.IOS_LABEL);
        String initial = getter.get();
        this.field.setValue(initial == null ? "" : initial);
        this.field.setResponder(value -> {
            if (syncing || isDisabled()) return;
            setter.accept(value);
            AuroraConfig.save();
        });
    }

    public TextSetting placeholder(String hint) {
        this.field.setHint(Component.literal(hint == null ? "" : hint));
        return this;
    }

    public TextSetting maxLength(int maxLength) {
        this.field.setMaxLength(Math.max(1, maxLength));
        return this;
    }

    public TextSetting resetDefault(Supplier<String> supplier) {
        String def = supplier.get() == null ? "" : supplier.get();
        installReset(() -> !Objects.equals(getter.get() == null ? "" : getter.get(), def),
                () -> {
                    setter.accept(def);
                    syncing = true;
                    try {
                        field.setValue(def);
                    } finally {
                        syncing = false;
                    }
                },
                () -> def.isEmpty() ? "(empty)" : def);
        return this;
    }

    @Override public TextSetting description(String desc) { super.description(desc); return this; }
    @Override public TextSetting description(Supplier<String> desc) { super.description(desc); return this; }

    private int fieldWidth(int rowWidth) {
        return Math.max(84, Math.min(FIELD_W, rowWidth / 2 - 16));
    }

    private void ensureLayout(Font tr) {
        int fw = fieldWidth(lastWidth);
        int labelArea = Math.max(40, lastWidth - LABEL_PAD - fw - RIGHT_PAD - 12);
        int styleKey = AuroraFontRenderer.styleKey();
        if (labelArea == cachedLabelArea && cachedLines != null && styleKey == cachedStyleKey) return;
        cachedStyleKey = styleKey;
        cachedLines = com.aurora.client.ui.util.WordWrap.lines(label, labelArea, 2, tr::width);
        int blockH = cachedLines.size() * AuroraFontRenderer.linePitch(tr);
        cachedBaseHeight = cachedLines.isEmpty() ? MIN_ROW_H : Math.max(MIN_ROW_H, blockH + 12);
        cachedLabelArea = labelArea;
    }

    @Override
    public int baseHeight() {
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return MIN_ROW_H;
        ensureLayout(tr);
        return cachedBaseHeight;
    }

    @Override
    public int height() {
        return baseHeight() + descriptionHeight(lastWidth);
    }

    @Override
    public SemanticActionControl interactionControl() {
        if (interactionControl == null) {
            interactionControl = new SemanticActionControl(SemanticAction.button(
                    labelText,
                    () -> {
                        String d = currentDescription();
                        return d != null ? Component.literal(d) : Component.empty();
                    },
                    () -> {
                        String v = getter.get();
                        return Component.literal(v == null ? "" : v);
                    },
                    () -> !isDisabled(),
                    this::focusField),
                    MinecraftSemanticFeedback.INSTANCE,
                    null,
                    SemanticActionControl.PointerRouting.MANUAL);
        }
        return interactionControl;
    }

    private void focusField() {
        if (isDisabled()) return;
        field.setFocused(true);
        field.moveCursorToEnd(false);
        requestFocus();
    }

    private void unfocusField() {
        field.setFocused(false);
        releaseFocus();
    }

    @Override
    public void onDetailScreenClose() {
        unfocusField();
    }

    @Override
    public void onInteractionAvailabilityChanged(boolean available) {
        if (!available && field.isFocused()) unfocusField();
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;
        ensureLayout(tr);

        boolean disabled = isDisabled();
        if ((disabled || FeatureSetting.getFocused() != this) && field.isFocused()) {
            field.setFocused(false);
        }
        field.active = !disabled;
        field.setEditable(!disabled);

        if (!field.isFocused()) {
            String live = getter.get();
            if (live == null) live = "";
            if (!live.equals(field.getValue())) {
                syncing = true;
                try {
                    field.setValue(live);
                } finally {
                    syncing = false;
                }
            }
        }

        int h = baseHeight();
        int fw = fieldWidth(width);
        int fx = x + width - fw - RIGHT_PAD;
        int fy = y + (h - FIELD_H) / 2;

        field.setX(fx);
        field.setY(fy);
        field.setWidth(fw);

        if (interactionControl != null) {
            interactionControl.setBounds(fx, fy, fw, FIELD_H);
        }

        renderWrappedLabel(ctx, label, x + LABEL_PAD, AuroraFontRenderer.textY(tr, y, h),
                cachedLabelArea, MaterialSurface.color(ThemeToken.ON_BACKGROUND), mouseX, mouseY, disabled);

        field.extractRenderState(ctx, mouseX, mouseY, 0f);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (resetMouseClicked(mouseX, mouseY, button)) return true;
        if (isDisabled()) return false;
        int h = baseHeight();
        int fw = fieldWidth(rowWidth);
        int fx = rowX + rowWidth - fw - RIGHT_PAD;
        int fy = rowY + (h - FIELD_H) / 2;
        field.setX(fx);
        field.setY(fy);
        field.setWidth(fw);

        if (mouseX >= fx && mouseX < fx + fw && mouseY >= fy && mouseY < fy + FIELD_H) {
            field.setFocused(true);
            requestFocus();
            field.mouseClicked(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0)), false);
            return true;
        }
        if (field.isFocused()) {
            unfocusField();
        }
        return false;
    }

    @Override
    public boolean onKeyPress(KeyEvent kev) {
        if (!field.isFocused()) return false;
        if (kev.key() == InputConstants.KEY_ESCAPE
                || kev.key() == InputConstants.KEY_RETURN
                || kev.key() == InputConstants.KEY_NUMPADENTER) {
            unfocusField();
            return true;
        }
        return field.keyPressed(kev);
    }

    @Override
    public boolean onCharTyped(CharacterEvent ev) {
        if (!field.isFocused()) return false;
        return field.charTyped(ev);
    }
}
