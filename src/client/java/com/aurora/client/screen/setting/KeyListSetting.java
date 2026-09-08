package com.aurora.client.screen.setting;
import com.aurora.client.ui.util.AuroraFontRenderer;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.module.KeystrokesModule;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.component.Widget;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.AuroraTheme;
import com.aurora.client.util.HoverAnim;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Editor for a list of raw GLFW key codes (the Keystrokes overlay's
 * "Extra Keys"). One row per entry with a "−" remove button, plus an
 * "Add Key" pill that enters listen mode — the next key pressed is
 * appended. ESC cancels; duplicates and the
 * {@link KeystrokesModule#MAX_EXTRA_KEYS} cap are ignored silently.
 *
 * <p>Follows the {@link KeybindSetting} focus pipeline: while listening,
 * the owning screen routes key events here via {@code requestFocus()}.
 *
 * <p>Glass rollout: the "Add Key" pill matches {@link KeybindSetting}'s
 * pill — RAISED glass with the neutral {@code WINDOW_FILL} tint at rest
 * and the accent-STAINED tint while listening (the same contract as
 * every glass control; complete flat pill on decline).
 */
public class KeyListSetting extends FeatureSetting {
    private static final int ROW_H = 18;
    private static final int ADD_H = 20;
    private static final int BTN_SIZE = 14;

    private final Supplier<List<Integer>> getter;
    private final Consumer<List<Integer>> setter;

    private boolean listening = false;
    private int lastX, lastY, lastW;
    private final HoverAnim hoverAnim = new HoverAnim(140L);

    public KeyListSetting(String label, Supplier<List<Integer>> getter, Consumer<List<Integer>> setter) {
        super(label);
        this.getter = getter;
        this.setter = setter;
    }

    @Override public KeyListSetting description(String desc) { super.description(desc); return this; }

    @Override public int baseHeight() {
        return ROW_H + safeList().size() * (ROW_H + 2) + ADD_H + 8;
    }

    @Override public int height() { return baseHeight(); }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastX = x;
        lastY = y;
        lastW = width;

        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;

        renderLabelWithTooltip(ctx, label, x + 12, y + 4, AuroraTheme.TEXT_PRIMARY, mouseX, mouseY);

        List<Integer> items = safeList();
        int iy = y + ROW_H + 2;
        int err = ThemeManager.color(ThemeToken.SEMANTIC_ERROR);
        for (int i = 0; i < items.size(); i++) {
            // Mode-aware row tint (translucent ON_BACKGROUND, not white).
            ctx.fill(x + 10, iy, x + width - 10, iy + ROW_H,
                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_BACKGROUND), 0x22));

            int btnX = x + width - BTN_SIZE - 14;
            int btnY = iy + (ROW_H - BTN_SIZE) / 2;
            boolean btnHover = Widget.inBounds(mouseX, mouseY, btnX, btnY, BTN_SIZE, BTN_SIZE);
            ctx.fill(btnX, btnY, btnX + BTN_SIZE, btnY + BTN_SIZE,
                    ThemeManager.withAlpha(err, btnHover ? 0x66 : 0x33));
            AuroraFontRenderer.drawCentered(ctx, tr, "\u2212", btnX + BTN_SIZE / 2,
                    btnY + (BTN_SIZE - tr.lineHeight) / 2, 0xFFFFFFFF);

            Integer boxed = items.get(i);
            String display = boxed == null ? "?" : keyName(boxed);
            int maxW = width - BTN_SIZE - 44;
            display = AuroraFontRenderer.ellipsize(tr, display, maxW, 3);
            ctx.drawString(tr, display, x + 16, iy + (ROW_H - tr.lineHeight) / 2,
                    AuroraTheme.TEXT_SECONDARY, false);
            iy += ROW_H + 2;
        }

        // "Add Key" pill (or "> press key <" while listening).
        int addX = x + 14;
        int addW = width - 28;
        int addY = iy + 4;
        boolean addHover = Widget.inBounds(mouseX, mouseY, addX, addY, addW, ADD_H);
        float hT = hoverAnim.update(addHover || listening);

        int fillTint   = AuroraAnim.lerpArgb(AuroraTheme.PANEL_OFF, AuroraTheme.PANEL_OFF_HOVER, hT);
        int borderTint = AuroraAnim.lerpArgb(AuroraTheme.BORDER_OFF, AuroraTheme.BORDER_ON_HOVER, hT);
        int textColor  = AuroraAnim.lerpArgb(AuroraTheme.TEXT_SECONDARY, AuroraTheme.TEXT_PRIMARY, hT);

        // Glass: same contract as KeybindSetting's pill — raised glass,
        // neutral tint at rest, accent-stained tint while listening, and
        // the complete flat pill on decline.
        float glassR = Math.min(ADD_H / 2f, ThemeManager.current().roundness().radiusSmall());
        boolean glassOk = BlurPanelRenderer.renderPanel(ctx, addX, addY, addW, ADD_H, glassR,
                BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX, BlurPanelRenderer.Lighting.raised());
        if (glassOk) {
            RenderUtil.drawRoundedRectAA(ctx, addX, addY, addW, ADD_H, glassR,
                    listening ? ThemeManager.stainedTint()
                              : ThemeManager.color(ThemeToken.WINDOW_FILL));
            BlurPanelRenderer.drawRimFinish(ctx, addX, addY, addW, ADD_H, glassR);
        } else {
            RenderUtil.drawSquircle(ctx, addX, addY, addW, ADD_H, AuroraTheme.RADIUS_SMALL,
                    listening ? AuroraTheme.IOS_BLUE_PRESSED : fillTint);
            RenderUtil.drawSquircleOutline(ctx, addX, addY, addW, ADD_H, AuroraTheme.RADIUS_SMALL, 1.0f,
                    listening ? AuroraTheme.IOS_BLUE : borderTint);
        }

        String addText = listening ? "> press key <"
                : items.size() >= KeystrokesModule.MAX_EXTRA_KEYS ? "List full (12 max)"
                : "+ Add Key";
        int addTextW = tr.width(addText);
        int textCol = listening ? ThemeManager.color(ThemeToken.ON_ACCENT) : textColor;
        ctx.drawString(tr, addText, addX + (addW - addTextW) / 2,
                addY + (ADD_H - tr.lineHeight) / 2 + 1, textCol, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (button != 0) return false;

        List<Integer> items = safeList();
        int iy = lastY + ROW_H + 2;
        for (int i = 0; i < items.size(); i++) {
            int btnX = lastX + lastW - BTN_SIZE - 14;
            int btnY = iy + (ROW_H - BTN_SIZE) / 2;
            if (Widget.inBounds(mouseX, mouseY, btnX, btnY, BTN_SIZE, BTN_SIZE)) {
                List<Integer> copy = new ArrayList<>(items);
                if (i < copy.size()) {
                    copy.remove(i);
                    setter.accept(copy);
                    AuroraConfig.save();
                }
                return true;
            }
            iy += ROW_H + 2;
        }

        int addX = lastX + 14;
        int addW = lastW - 28;
        int addY = iy + 4;
        if (Widget.inBounds(mouseX, mouseY, addX, addY, addW, ADD_H)) {
            listening = !listening;
            if (listening) requestFocus(); else releaseFocus();
            return true;
        }

        // A click anywhere else cancels listen mode without committing.
        if (listening) {
            listening = false;
            releaseFocus();
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyPress(int keyCode, int modifiers) {
        if (!listening) return false;

        // ESC / BACKSPACE cancels without changing the list.
        if (keyCode != GLFW.GLFW_KEY_ESCAPE && keyCode != GLFW.GLFW_KEY_BACKSPACE) {
            List<Integer> copy = new ArrayList<>(safeList());
            if (!copy.contains(keyCode) && copy.size() < KeystrokesModule.MAX_EXTRA_KEYS) {
                copy.add(keyCode);
                setter.accept(copy);
            }
            AuroraConfig.save();
        }
        listening = false;
        releaseFocus();
        return true;
    }

    @Override
    public void onDetailScreenClose() {
        listening = false;
        releaseFocus();
    }

    private List<Integer> safeList() {
        List<Integer> list = getter.get();
        return list != null ? list : new ArrayList<>();
    }

    private static String keyName(int glfwKey) {
        try {
            return InputConstants.Type.KEYSYM.getOrCreate(glfwKey).getDisplayName().getString();
        } catch (Exception e) {
            return "Key " + glfwKey;
        }
    }
}
