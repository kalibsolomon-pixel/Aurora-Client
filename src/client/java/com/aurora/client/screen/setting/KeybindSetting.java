package com.aurora.client.screen.setting;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.Widget;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraAnim;
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
 *
 * <p>Glass rollout: the pill renders as RAISED glass — neutral
 * {@code WINDOW_FILL} tint at rest, accent-STAINED tint while listening
 * (the active state reads through the tint, never through the lighting
 * orientation; the stained alpha follows the Background Opacity slider
 * through {@link ThemeManager#stainedTint()}'s fixed floor — single
 * application point). On decline (menu context, screenshot suppression,
 * failure) the complete flat pill returns unchanged.
 */
public class KeybindSetting extends FeatureSetting {
    private static final int CONTROL_H = 28;
    private static final int BTN_W = 90;
    private static final int BTN_H = 18;

    private final IntSupplier getter;
    private final IntConsumer setter;

    /**
     * Glass-pass bookkeeping (the {@code Button} scheme, §6 convention 6):
     * the frame in which {@link #renderGlassPass} painted the pill pre-dim
     * and whether it drew. In that frame {@link #render} paints content
     * only (or the flat pill on decline); otherwise the surface paints in
     * place — the legacy order, pixel-identical.
     */
    private long glassPassFrame = -1L;
    private boolean passDrewPill = false;

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

    /**
     * Pre-dim surface (the split's surface half): the pill's raised glass,
     * neutral at rest / accent-stained while listening — the tint state is
     * read here so it can never disagree with the content pass.
     */
    @Override
    public void renderGlassPass(GuiGraphics ctx, int x, int y, int width) {
        if (!GlassSurface.passOpen()) return; // legacy frame order — render paints in place
        glassPassFrame = GlassSurface.frame();
        int btnX = x + width - BTN_W - 14;
        int btnY = y + (CONTROL_H - BTN_H) / 2;
        float glassR = Math.min(BTN_H / 2f, ThemeManager.current().roundness().radiusSmall());
        passDrewPill = GlassSurface.control(ctx, btnX, btnY, BTN_W, BTN_H, glassR, listening);
    }

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

        boolean hover = Widget.inBounds(mouseX, mouseY, btnX, btnY, BTN_W, BTN_H);
        float hT = hoverAnim.update(hover || listening);

        int fillTint   = AuroraAnim.lerpArgb(AuroraTheme.PANEL_OFF, AuroraTheme.PANEL_OFF_HOVER, hT);
        int borderTint = AuroraAnim.lerpArgb(AuroraTheme.BORDER_OFF, AuroraTheme.BORDER_OFF_HOVER, hT);
        int textColor  = AuroraAnim.lerpArgb(AuroraTheme.TEXT_SECONDARY, AuroraTheme.TEXT_PRIMARY, hT);

        // Glass: raised glass replaces the flat fill + outline (the glass
        // rim replaces the border — no double outline). Neutral tint at
        // rest; the ACCENT-STAINED tint while listening is the "next key
        // press will be captured" cue (the same selected-control tint the
        // segmented controls use). On decline the complete flat pill
        // (fill + outline) returns. Hover keeps the text-color cue; the
        // tint stays constant, exactly like every other glass control. If
        // the screen ran this row's glass pass this frame the surface is
        // already on screen UNDER the dim and only its result matters
        // here; otherwise (legacy frame order) it is painted in place now
        // through the shared GlassSurface helper (identical calls).
        float glassR = Math.min(BTN_H / 2f, ThemeManager.current().roundness().radiusSmall());
        boolean glassOk;
        if (glassPassFrame == GlassSurface.frame()) {
            glassOk = passDrewPill;
        } else {
            glassOk = GlassSurface.control(ctx, btnX, btnY, BTN_W, BTN_H, glassR, listening);
        }
        if (!glassOk) {
            RenderUtil.drawSquircle(ctx, btnX, btnY, BTN_W, BTN_H, AuroraTheme.RADIUS_SMALL,
                    listening ? AuroraTheme.IOS_BLUE_PRESSED : fillTint);
            RenderUtil.drawSquircleOutline(ctx, btnX, btnY, BTN_W, BTN_H, AuroraTheme.RADIUS_SMALL, 1.0f,
                    listening ? AuroraTheme.IOS_BLUE : borderTint);
        }

        String labelText = listening ? "> press key <" : keyName(getter.getAsInt());
        // Trim long names so they fit in the pill (keep at least 3 chars + ellipsis).
        labelText = AuroraFontRenderer.ellipsize(tr, labelText, BTN_W - 8, 3);
        int labelW = tr.width(labelText);
        int textCol = listening ? ThemeManager.color(ThemeToken.ON_ACCENT) : textColor;
        ctx.drawString(tr, labelText,
                btnX + (BTN_W - labelW) / 2,
                btnY + (BTN_H - tr.lineHeight) / 2 + 1,
                textCol, false);

        renderDescription(ctx, x, y + CONTROL_H, width);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (button != 0) return false;
        boolean onBtn = Widget.inBounds(mouseX, mouseY, lastBtnX, lastBtnY, BTN_W, BTN_H);
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

    /**
     * Closing the detail screen while listening must not leave this row
     * holding the static focus registry into the next screen — same
     * lifecycle contract as KeyListSetting.
     */
    @Override
    public void onDetailScreenClose() {
        listening = false;
        releaseFocus();
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
}
