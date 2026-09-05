package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.ui.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {

    @Unique private long aurora$hoverStartMs = 0;
    @Unique private boolean aurora$wasActive = false;
    @Unique private float aurora$hoverT = 0f;
    @Unique private static final long HOVER_MS = 140L;

    @Unique private long aurora$pressDownStartMs = -1L;
    @Unique private static final long PRESS_DOWN_MS = 90L;
    @Unique private static final long PRESS_UP_MS = 180L;

    @Unique private int aurora$cachedLabelW = -1;
    /** Message the width was measured for; the cache invalidates when the label changes. */
    @Unique private String aurora$cachedLabelMsg = null;

    @Unique
    private boolean aurora$shouldApplyStyle() {
        if (!AuroraConfig.get().customTitleScreen) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null) return false;
        if (!(mc.screen instanceof JoinMultiplayerScreen || mc.screen instanceof SelectWorldScreen)) return false;
        
        // Only apply to standard Buttons, not ImageButtons or other subclasses
        return ((Object) this).getClass() == Button.class;
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void aurora$renderCustomWidget(GuiGraphics ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (aurora$shouldApplyStyle()) {
            AbstractButton self = (AbstractButton) (Object) this;
            
            boolean active = self.isHovered() || self.isFocused();
            if (active != aurora$wasActive) {
                aurora$hoverStartMs = System.currentTimeMillis() - (long) ((1f - aurora$hoverT) * HOVER_MS);
                aurora$wasActive = active;
            }
            float targetT = active ? 1f : 0f;
            if (aurora$hoverT != targetT) {
                long elapsed = System.currentTimeMillis() - aurora$hoverStartMs;
                float raw = Math.min(1f, Math.max(0f, elapsed / (float) HOVER_MS));
                float t = active ? raw : (1f - raw);
                aurora$hoverT = AuroraAnim.easeOutCubic(t);
                if (raw >= 1f) aurora$hoverT = targetT;
            }

            int x = self.getX();
            int y = self.getY();
            int w = self.getWidth();
            int h = self.getHeight();
            int cx = x + w / 2;
            int cy = y + h / 2;

            float scale = aurora$currentScale();

            ctx.pose().pushMatrix();
            if (scale != 1.0f) {
                ctx.pose().translate(cx, cy);
                ctx.pose().scale(scale, scale);
                ctx.pose().translate(-cx, -cy);
            }

            // Same token set + AA engine + radiusSmall as the canonical
            // ui.component.Button, so vanilla-screen buttons are pixel
            // siblings of Aurora's own buttons in both Dark and Light mode
            // (the old hardcoded translucent-white fill vanished on light
            // surfaces).
            int bgCol = AuroraAnim.lerpArgb(
                    ThemeManager.color(ThemeToken.SURFACE),
                    ThemeManager.color(ThemeToken.SURFACE_VARIANT), aurora$hoverT);
            int borderCol = AuroraAnim.lerpArgb(
                    ThemeManager.color(ThemeToken.BORDER),
                    ThemeManager.color(ThemeToken.BORDER_HOVER), aurora$hoverT);
            float radius = ThemeManager.current().roundness().radiusSmall();
            RenderUtil.drawRoundedRectAA(ctx, x, y, w, h, radius, bgCol);
            RenderUtil.drawRoundedOutlineAA(ctx, x, y, w, h, radius, 1.0f, borderCol);

            Font tr = Minecraft.getInstance().font;
            var label = self.getMessage();
            String labelStr = label.getString();
            if (aurora$cachedLabelMsg == null || !aurora$cachedLabelMsg.equals(labelStr)) {
                aurora$cachedLabelMsg = labelStr;
                aurora$cachedLabelW = tr.width(label);
            }
            int textX = x + (w - aurora$cachedLabelW) / 2;
            int textY = y + (h - tr.lineHeight) / 2 + 1;

            int textCol = self.active ? ThemeManager.color(ThemeToken.ON_BACKGROUND)
                    : ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED);
            ctx.drawString(tr, self.getMessage(), textX, textY, textCol, false);

            ctx.pose().popMatrix();
            
            ci.cancel();
        }
    }

    @Inject(method = "onClick", at = @At("HEAD"))
    private void aurora$onClick(net.minecraft.client.input.MouseButtonEvent ev, boolean dbl, CallbackInfo ci) {
        if (aurora$shouldApplyStyle()) {
            aurora$pressDownStartMs = System.currentTimeMillis();
        }
    }

    @Unique
    private float aurora$currentScale() {
        if (aurora$pressDownStartMs > 0L) {
            long now = System.currentTimeMillis();
            long elapsed = now - aurora$pressDownStartMs;
            if (elapsed < PRESS_DOWN_MS) {
                float raw = Math.min(1f, Math.max(0f, elapsed / (float) PRESS_DOWN_MS));
                float eased = AuroraAnim.easeOutCubic(raw);
                return aurora$lerpFloat(1.0f, 0.96f, eased);
            } else if (elapsed < PRESS_DOWN_MS + PRESS_UP_MS) {
                float raw = Math.min(1f, Math.max(0f, (elapsed - PRESS_DOWN_MS) / (float) PRESS_UP_MS));
                float spring = AuroraAnim.springOvershoot(raw);
                return aurora$lerpFloat(0.96f, 1.00f, spring);
            } else {
                aurora$pressDownStartMs = -1L;
            }
        }
        return 1.0f;
    }
    
    @Unique
    private float aurora$lerpFloat(float a, float b, float t) {
        return a + (b - a) * t;
    }
}