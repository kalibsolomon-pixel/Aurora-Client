package com.aurora.client.screen.setting;

import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraKey;
import com.aurora.client.util.AuroraTheme;
import com.aurora.client.util.HoverAnim;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Row that displays and rebinds a single Aurora keybind. Stores the bound
 * key as a GLFW int (see {@link AuroraKey#UNBOUND}). Click the right-
 * hand pill to enter listen mode, then any next key press is captured.
 *
 * <p>Special keys while listening:
 * <ul>
 *   <li>{@code ESC} — clear the binding (sets to {@link AuroraKey#UNBOUND}).</li>
 *   <li>{@code BACKSPACE} — same as ESC, mirrors vanilla controls UX.</li>
 * </ul>
 *
 * <p>Listen mode uses the existing {@link FeatureSetting#requestFocus()}
 * focus pipeline so the screen routes all key presses here until the
 * binding completes.
 */
public class KeybindSetting extends FeatureSetting {
    private static final int CONTROL_H = 28;
    private static final int BTN_W = 90;
    private static final int BTN_H = 18;

    private final IntSupplier getter;
    private final IntConsumer setter;

    private int lastBtnX, lastBtnY;
    private int lastWidth = 240;
    private boolean listening = false;
    private final HoverAnim hoverAnim = new HoverAnim(140L);

    public KeybindSetting(String label, IntSupplier getter, IntConsumer setter) {
        super(label);
        this.getter = getter;
        this.setter = setter;
    }

    @Override public KeybindSetting description(String desc) { super.description(desc); return this; }
    @Override public KeybindSetting description(Supplier<String> desc) { super.description(desc); return this; }

    @Override public int baseHeight() { return CONTROL_H; }
    @Override public int height()     { return CONTROL_H + descriptionHeight(lastWidth); }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;

        // Keybind rows intentionally skip the label-hover description tooltip.
        ctx.drawString(tr, label, x + 12, y + (CONTROL_H - tr.lineHeight) / 2,
                AuroraTheme.TEXT_PRIMARY, false);

        int btnX = x + width - BTN_W - 14;
        int btnY = y + (CONTROL_H - BTN_H) / 2;
        lastBtnX = btnX;
        lastBtnY = btnY;

        boolean hover = mouseX >= btnX && mouseX < btnX + BTN_W
                && mouseY >= btnY && mouseY < btnY + BTN_H;
        float hT = hoverAnim.update(hover || listening);

        int fillTint, borderTint, textColor;
        if (listening) {
            // Active accent fill so the user sees clearly that the next
            // key press will be captured.
            fillTint   = AuroraTheme.IOS_BLUE_PRESSED;
            borderTint = AuroraTheme.IOS_BLUE;
            textColor  = 0xFFFFFFFF;
        } else {
            fillTint   = lerpColor(AuroraTheme.PANEL_OFF, AuroraTheme.PANEL_OFF_HOVER, hT);
            borderTint = lerpColor(AuroraTheme.BORDER_OFF, AuroraTheme.BORDER_OFF_HOVER, hT);
            textColor  = lerpColor(AuroraTheme.TEXT_SECONDARY, AuroraTheme.TEXT_PRIMARY, hT);
        }

        RenderUtil.drawSquircle(ctx, btnX, btnY, BTN_W, BTN_H, AuroraTheme.RADIUS_SMALL, fillTint);
        RenderUtil.drawSquircleOutline(ctx, btnX, btnY, BTN_W, BTN_H, AuroraTheme.RADIUS_SMALL, 1.0f, borderTint);

        String labelText = listening ? "> press key <" : keyName(getter.getAsInt());
        // Trim long names so they fit in the pill.
        if (tr.width(labelText) > BTN_W - 8) {
            while (labelText.length() > 3 && tr.width(labelText + "\u2026") > BTN_W - 8) {
                labelText = labelText.substring(0, labelText.length() - 1);
            }
            labelText = labelText + "\u2026";
        }
        int labelW = tr.width(labelText);
        ctx.drawString(tr, labelText,
                btnX + (BTN_W - labelW) / 2,
                btnY + (BTN_H - tr.lineHeight) / 2 + 1,
                textColor, false);

        renderDescription(ctx, x, y + CONTROL_H, width);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (button != 0) return false;
        boolean onBtn = mouseX >= lastBtnX && mouseX < lastBtnX + BTN_W
                && mouseY >= lastBtnY && mouseY < lastBtnY + BTN_H;
        if (!onBtn) {
            // Click outside the pill cancels listen mode without committing.
            if (listening) {
                listening = false;
                releaseFocus();
                return true;
            }
            return false;
        }
        // Toggle listen mode on click.
        listening = !listening;
        if (listening) requestFocus(); else releaseFocus();
        return true;
    }

    @Override
    public boolean onKeyPress(int keyCode, int modifiers) {
        if (!listening) return false;

        // ESC / BACKSPACE clears the binding. Match vanilla controls UX.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            setter.accept(AuroraKey.UNBOUND);
        } else {
            setter.accept(keyCode);
        }
        com.aurora.client.config.AuroraConfig.save();
        listening = false;
        releaseFocus();
        return true;
    }

    /** Friendly name for a GLFW key code. Falls back to the raw int. */
    public static String keyName(int glfwKey) {
        if (glfwKey == AuroraKey.UNBOUND) return "Unbound";
        try {
            return InputConstants.Type.KEYSYM.getOrCreate(glfwKey).getDisplayName().getString();
        } catch (Exception e) {
            return "Key " + glfwKey;
        }
    }

    private static int lerpColor(int from, int to, float t) {
        if (t <= 0f) return from;
        if (t >= 1f) return to;
        int af = (from >>> 24) & 0xFF, ar = (from >>> 16) & 0xFF, ag = (from >>> 8) & 0xFF, ab = from & 0xFF;
        int bf = (to   >>> 24) & 0xFF, br = (to   >>> 16) & 0xFF, bg = (to   >>> 8) & 0xFF, bb = to   & 0xFF;
        int a = Math.round(af + (bf - af) * t);
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int b = Math.round(ab + (bb - ab) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
