package com.aurora.client.screen.setting;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.AuroraFontRenderer;

import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A multi-line string-list editor used for server-address pattern lists
 * (compliance mode safe/strict lists). Renders one row per entry with a
 * small "âˆ’" remove button, plus an {@link EditBox} at the bottom to type
 * a new entry. Pressing Enter or clicking "+" appends the typed string.
 *
 * <p>Each entry is stored as-is in the backing {@code List<String>}; the
 * caller provides a {@link Supplier} getter and {@link Consumer} setter so
 * mutations land back on the config field directly.
 */
public class StringListSetting extends FeatureSetting {
    private static final int ROW_H = 18;
    private static final int INPUT_H = 20;
    private static final int BTN_SIZE = 14;

    private final Supplier<List<String>> getter;
    private final Consumer<List<String>> setter;

    private EditBox inputField;
    private int lastX, lastY, lastW;

    public StringListSetting(String label,
                              Supplier<List<String>> getter,
                              Consumer<List<String>> setter) {
        super(label);
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public int baseHeight() {
        List<String> items = safeList();
        return ROW_H + items.size() * (ROW_H + 2) + INPUT_H + 8;
    }

    @Override
    public int height() {
        return baseHeight();
    }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastX = x;
        lastY = y;
        lastW = width;

        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;

        renderLabelWithTooltip(ctx, label, x + 12, y + 4,
                AuroraTheme.TEXT_PRIMARY, mouseX, mouseY);

        List<String> items = safeList();
        int iy = y + ROW_H + 2;
        int err = ThemeManager.color(ThemeToken.SEMANTIC_ERROR);
        int ok = ThemeManager.color(ThemeToken.SEMANTIC_SUCCESS);

        for (int i = 0; i < items.size(); i++) {
            String entry = items.get(i);
            int btnX = x + width - BTN_SIZE - 14;
            int btnY = iy + (ROW_H - BTN_SIZE) / 2;

            // Mode-aware row tint (translucent ON_BACKGROUND, not white).
            ctx.fill(x + 10, iy, x + width - 10, iy + ROW_H,
                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_BACKGROUND), 0x22));

            boolean btnHover = mouseX >= btnX && mouseX < btnX + BTN_SIZE
                    && mouseY >= btnY && mouseY < btnY + BTN_SIZE;
            ctx.fill(btnX, btnY, btnX + BTN_SIZE, btnY + BTN_SIZE,
                    ThemeManager.withAlpha(err, btnHover ? 0x66 : 0x33));
            AuroraFontRenderer.drawCentered(ctx, tr, "\u2212", btnX + BTN_SIZE / 2,
                    btnY + (BTN_SIZE - tr.lineHeight) / 2, 0xFFFFFFFF);

            // Width-based truncation (a char count overflows with wide glyphs).
            int maxTextW = (btnX - 6) - (x + 16);
            String display = tr.width(entry) <= maxTextW ? entry
                    : tr.plainSubstrByWidth(entry, maxTextW - tr.width("...")) + "...";
            ctx.drawString(tr, display, x + 16, iy + (ROW_H - tr.lineHeight) / 2,
                    AuroraTheme.TEXT_SECONDARY, false);

            iy += ROW_H + 2;
        }

        int inputY = iy + 4;
        if (inputField == null) {
            inputField = new EditBox(tr, x + 14, inputY, width - 14 - BTN_SIZE - 18,
                    INPUT_H, net.minecraft.network.chat.Component.literal(""));
            inputField.setMaxLength(100);
            inputField.setHint(net.minecraft.network.chat.Component.literal("Add address..."));
        } else {
            inputField.setX(x + 14);
            inputField.setY(inputY);
            inputField.setWidth(width - 14 - BTN_SIZE - 18);
        }
        inputField.render(ctx, mouseX, mouseY, 0);

        int addBtnX = x + width - BTN_SIZE - 14;
        int addBtnY = inputY + (INPUT_H - BTN_SIZE) / 2;
        boolean addHover = mouseX >= addBtnX && mouseX < addBtnX + BTN_SIZE
                && mouseY >= addBtnY && mouseY < addBtnY + BTN_SIZE;
        ctx.fill(addBtnX, addBtnY, addBtnX + BTN_SIZE, addBtnY + BTN_SIZE,
                ThemeManager.withAlpha(ok, addHover ? 0x66 : 0x33));
        AuroraFontRenderer.drawCentered(ctx, tr, "+", addBtnX + BTN_SIZE / 2,
                addBtnY + (BTN_SIZE - tr.lineHeight) / 2, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (button != 0) return false;
        if (inputField == null) return false;

        List<String> items = safeList();
        int iy = lastY + ROW_H + 2;

        for (int i = 0; i < items.size(); i++) {
            int btnX = lastX + lastW - BTN_SIZE - 14;
            int btnY = iy + (ROW_H - BTN_SIZE) / 2;
            if (mouseX >= btnX && mouseX < btnX + BTN_SIZE
                    && mouseY >= btnY && mouseY < btnY + BTN_SIZE) {
                List<String> copy = new ArrayList<>(items);
                if (i < copy.size()) {
                    copy.remove(i);
                    setter.accept(copy);
                }
                return true;
            }
            iy += ROW_H + 2;
        }

        int inputY = iy + 4;
        int addBtnX = lastX + lastW - BTN_SIZE - 14;
        int addBtnY = inputY + (INPUT_H - BTN_SIZE) / 2;
        if (mouseX >= addBtnX && mouseX < addBtnX + BTN_SIZE
                && mouseY >= addBtnY && mouseY < addBtnY + BTN_SIZE) {
            addEntry();
            return true;
        }

        if (inputField.getX() > 0 && mouseX >= inputField.getX() && mouseX < inputField.getX() + inputField.getWidth()
                && mouseY >= inputField.getY() && mouseY < inputField.getY() + inputField.getHeight()) {
            inputField.setFocused(true);
            requestFocus();
            return true;
        }

        inputField.setFocused(false);
        return false;
    }

    @Override
    public boolean onKeyPress(net.minecraft.client.input.KeyEvent kev) {
        if (inputField != null && inputField.isFocused()) {
            int keyCode = kev.key();
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                addEntry();
                return true;
            }
            return inputField.keyPressed(kev);
        }
        return false;
    }

    @Override
    public boolean onCharTyped(net.minecraft.client.input.CharacterEvent ev) {
        if (inputField != null && inputField.isFocused()) {
            return inputField.charTyped(ev);
        }
        return false;
    }

    private void addEntry() {
        if (inputField == null) return;
        String text = inputField.getValue().trim();
        if (text.isEmpty()) return;
        List<String> copy = new ArrayList<>(safeList());
        copy.add(text);
        setter.accept(copy);
        inputField.setValue("");
        // Persist immediately — matches how sibling widgets save at commit.
        com.aurora.client.config.AuroraConfig.save();
    }

    /**
     * Reset the input state on screen close so reopening starts clean and
     * this row cannot hold the static focus registry across screens — same
     * lifecycle contract as ParticleConfigSetting.
     */
    @Override
    public void onDetailScreenClose() {
        if (inputField != null) {
            inputField.setValue("");
            inputField.setFocused(false);
            inputField.moveCursorToEnd(false);
        }
        releaseFocus();
    }

    private List<String> safeList() {
        List<String> list = getter.get();
        return list != null ? list : new ArrayList<>();
    }
}
