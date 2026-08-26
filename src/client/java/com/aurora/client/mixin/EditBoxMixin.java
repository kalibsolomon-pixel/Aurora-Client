package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.screen.AuroraScreen;
import com.aurora.client.screen.FeatureDetailScreen;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fully replaces the vanilla {@link EditBox} rendering on every screen
 * that contains a search bar (world-selection, multiplayer, main menu,
 * module detail) with Aurora's themed search bar: a hi-res AA rounded-
 * rect background + outline whose colors come from {@link AuroraTheme},
 * with the typed text (or placeholder hint) vertically centered.
 *
 * <p>The injection fires at {@code HEAD} and {@code cancel()}s the
 * vanilla {@code renderWidget} so the default dark box + hairline
 * border never show through. This also fixes the text sitting too high —
 * the vanilla render positions text at a fixed offset that doesn't
 * account for our taller themed background; rendering the text ourselves
 * lets us vertically center it precisely against the theme box.
 *
 * <p>Only {@code displayPos} (the horizontal scroll offset of the visible
 * text) is shadowed — it's confirmed by the LiquidBounce reference mixin
 * and is needed to correctly clip/scroll long queries. The blinking
 * caret uses {@link System#currentTimeMillis()} instead of shadowing the
 * vanilla {@code frame} counter, avoiding a fragile private-field
 * dependency.
 */
@Mixin(EditBox.class)
public abstract class EditBoxMixin {

    /** Horizontal scroll offset of the first visible character. */
    @Shadow private int displayPos;
    /** Placeholder hint text shown when the field is empty. */
    @Shadow private Component hint;

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void aurora$renderCustomStyle(GuiGraphics ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!AuroraConfig.get().customTitleScreen) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null) return;
        // Take over rendering on vanilla screens that got Aurora search bars
        // via mixins, and on every Aurora screen marked with ThemedScreen —
        // so ALL of the mod's text fields (search bars, hex fields, inline
        // rename rows, the map's waypoint prompt) share this one canonical
        // themed text-field implementation.
        boolean isThemedScreen = mc.screen instanceof JoinMultiplayerScreen
                || mc.screen instanceof SelectWorldScreen
                || mc.screen instanceof com.aurora.client.ui.component.ThemedScreen;
        if (!isThemedScreen) return;

        EditBox self = (EditBox) (Object) this;
        Font font = mc.font;
        float x = self.getX();
        float y = self.getY();
        float w = self.getWidth();
        float h = self.getHeight();
        boolean focused = self.isFocused();
        String value = self.getValue();

        // --- 1. Themed background (fully replaces vanilla box) ---
        // Token-driven translucent surface, tracking the panel's opacity so
        // the field matches the rest of the themed UI in both modes.
        int fillCol = ThemeManager.surfaceColor(focused ? ThemeToken.SURFACE_VARIANT : ThemeToken.SURFACE);
        int borderCol = focused ? AuroraTheme.BORDER_ON_HOVER : AuroraTheme.BORDER_OFF;
        RenderUtil.drawRoundedRectAA(ctx, x, y, w, h, AuroraTheme.RADIUS_SMALL, fillCol);

        // --- 2. Text / hint rendering, vertically centered ---
        int textCol = AuroraTheme.IOS_LABEL;
        int innerX = (int) x + 8;           // 8px left padding
        int innerRight = (int) (x + w) - 4; // 4px right padding
        int textY = (int) (y + (h - font.lineHeight) / 2) + 1; // +1 nudges past baseline offset

        ctx.enableScissor(innerX, (int) y, innerRight, (int) (y + h));
        if (value.isEmpty()) {
            // Placeholder hint text
            if (hint != null) {
                ctx.drawString(font, hint, innerX, textY, AuroraTheme.IOS_TERTIARY_LABEL, false);
            }
        } else {
            // Render the value text, scrolled by displayPos so long
            // queries stay visible as the user types.
            int safeDisp = Math.min(displayPos, value.length());
            String visible = value.substring(safeDisp);
            ctx.drawString(font, visible, innerX - safeDisp, textY, textCol, false);
        }
        ctx.disableScissor();

        // --- 3. Blinking caret when focused ---
        // Use wall-clock time for the blink cycle (~530ms on, ~530ms off)
        // instead of shadowing the vanilla `frame` counter.
        if (focused && (System.currentTimeMillis() / 530L) % 2L == 0L) {
            // Approximate caret X from the cursor position. EditBox doesn't
            // expose cursorPos publicly, so we fall back to placing the
            // caret at the end of the visible text — accurate when typing
            // at the tail (the common case for a search field).
            int safeDisp = Math.min(displayPos, value.length());
            String visible = value.substring(safeDisp);
            int caretX = innerX + font.width(visible);
            int caretY1 = textY - 1;
            int caretY2 = textY + font.lineHeight;
            // Clamp caret inside the box
            caretX = Math.min(caretX, innerRight - 1);
            ctx.fill(caretX, caretY1, caretX + 1, caretY2, textCol);
        }

        // --- 4. Outline on top (after text) ---
        RenderUtil.drawRoundedOutlineAA(ctx, x, y, w, h, AuroraTheme.RADIUS_SMALL, 1.0f, borderCol);

        // Cancel the vanilla render so its background/border never appear.
        ci.cancel();
    }
}