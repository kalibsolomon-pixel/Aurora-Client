package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
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
 * module detail) with Aurora's themed search bar, with the typed text
 * (or placeholder hint) vertically centered.
 *
 * <p>The injection fires at {@code HEAD} and {@code cancel()}s the
 * vanilla {@code renderWidget} so the default dark box + hairline
 * border never show through. This also fixes the text sitting too high —
 * the vanilla render positions text at a fixed offset that doesn't
 * account for our taller themed background; rendering the text ourselves
 * lets us vertically center it precisely against the theme box.
 *
 * <p>Only {@code displayPos} (the horizontal scroll offset of the visible
 * text) and {@code cursorPos} (the caret index) are shadowed — both are
 * confirmed by the LiquidBounce reference mixin; {@code displayPos} is
 * needed to correctly clip/scroll long queries, {@code cursorPos} to place
 * the caret where the user's cursor actually is (mid-string included).
 * The blinking caret uses {@link System#currentTimeMillis()} instead of
 * shadowing the vanilla {@code frame} counter, avoiding a fragile
 * private-field dependency.
 *
 * <p><b>Glass rollout:</b> on a screen with a live world behind it, the
 * field renders as RAISED glass (search fields are interactive controls
 * floating above the depressed window) with the neutral
 * {@code WINDOW_FILL} tint — whose alpha IS the Background Opacity
 * slider, the single opacity application point. Focus reads through the
 * caret + a subtle focus-ring outline only; the tint stays constant.
 * On decline (menu context, screenshot suppression, glass disabled) the
 * flat themed fill + outline below returns unchanged. This is the ONE
 * canonical search-bar implementation — Particles, Item Scale,
 * ResourcePacks and the Modules grid all render through it.
 */
@Mixin(EditBox.class)
public abstract class EditBoxMixin {

    /** Horizontal scroll offset of the first visible character. */
    @Shadow private int displayPos;
    /** Caret index within the value string. */
    @Shadow private int cursorPos;
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

        // --- 1. Background (fully replaces vanilla box) ---
        // Glass first: raised panel + neutral WINDOW_FILL tint. The glass
        // rim replaces the outline (no double outline). The focus cue is
        // the caret plus a subtle focus-ring outline drawn on top — focus
        // never changes the tint (the same contract as every glass control).
        // With no live world the renderer declines and the flat themed
        // fill + outline below draws instead, exactly as before glass.
        int fillCol = ThemeManager.surfaceColor(focused ? ThemeToken.SURFACE_VARIANT : ThemeToken.SURFACE);
        int borderCol = focused ? AuroraTheme.BORDER_ON_HOVER : AuroraTheme.BORDER_OFF;

        float radius = ThemeManager.current().roundness().radiusSmall();
        boolean glassOk = BlurPanelRenderer.renderPanel(ctx, x, y, w, h, radius,
                BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX, BlurPanelRenderer.Lighting.raised());
        if (glassOk) {
            RenderUtil.drawRoundedRectAA(ctx, x, y, w, h, radius,
                    ThemeManager.color(ThemeToken.WINDOW_FILL));
            BlurPanelRenderer.drawRimFinish(ctx, x, y, w, h, radius);
            // Focus ring on glass — a translucent hairline that brightens
            // the rim without stacking a second surface.
            if (focused) {
                RenderUtil.drawRoundedOutlineAA(ctx, x, y, w, h, radius, 1.0f,
                        ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ACCENT), 0x99));
            }
        } else {
            RenderUtil.drawRoundedRectAA(ctx, x, y, w, h, radius, fillCol);
        }

        // --- 2. Text / hint rendering, vertically centered ---
        int textCol = ThemeManager.color(ThemeToken.ON_BACKGROUND);
        int innerX = (int) x + 8;           // 8px left padding
        int innerRight = (int) (x + w) - 4; // 4px right padding
        int textY = (int) (y + (h - font.lineHeight) / 2) + 1; // +1 nudges past baseline offset

        ctx.enableScissor(innerX, (int) y, innerRight, (int) (y + h));
        if (value.isEmpty()) {
            // Placeholder hint text
            if (hint != null) {
                ctx.drawString(font, hint, innerX, textY,
                        ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED), false);
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
            // Caret X from the ACTUAL cursor position (mid-string included),
            // measured over the visible window: width of the text between
            // the scroll offset and the cursor.
            int safeDisp = Math.min(displayPos, value.length());
            int safeCursor = Math.max(safeDisp, Math.min(cursorPos, value.length()));
            int caretX = innerX + font.width(value.substring(safeDisp, safeCursor));
            int caretY1 = textY - 1;
            int caretY2 = textY + font.lineHeight;
            // Clamp caret inside the box
            caretX = Math.min(caretX, innerRight - 1);
            ctx.fill(caretX, caretY1, caretX + 1, caretY2, textCol);
        }

        // --- 4. Flat-path outline on top (after text) ---
        if (!glassOk) {
            RenderUtil.drawRoundedOutlineAA(ctx, x, y, w, h, radius, 1.0f, borderCol);
        }

        // Cancel the vanilla render so its background/border never appear.
        ci.cancel();
    }
}
