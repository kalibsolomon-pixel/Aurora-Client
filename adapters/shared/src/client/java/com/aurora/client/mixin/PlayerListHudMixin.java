package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.util.TabListStyle;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tab list polish across Minecraft 26.1.2, 26.2 and 26.3:
 * <ul>
 *   <li>Replaces vanilla's 5-bar ping icon with colored numeric latency
 *       (optional {@code ms} suffix, optional text shadow, and widened ping
 *       slot so 3-digit values never collide with player names).</li>
 *   <li>Optionally hides server header and/or footer banners.</li>
 *   <li>Optionally scales tab-list panel and row background opacity.</li>
 *   <li>Optionally highlights the local player's own row with the theme accent.</li>
 * </ul>
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerListHudMixin {

    @ModifyConstant(
            method = "extractRenderState",
            constant = @Constant(intValue = 13)
    )
    private int aurora$widenPingColumn(int original) {
        AuroraConfig cfg = AuroraConfig.get();
        return TabListStyle.pingColumnWidth(cfg.tabPingEnabled, cfg.tabPingShowMs);
    }

    @ModifyExpressionValue(
            method = "extractRenderState",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;header:Lnet/minecraft/network/chat/Component;")
    )
    private Component aurora$maybeHideHeader(Component original) {
        return AuroraConfig.get().tabListHideHeader ? null : original;
    }

    @ModifyExpressionValue(
            method = "extractRenderState",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;footer:Lnet/minecraft/network/chat/Component;")
    )
    private Component aurora$maybeHideFooter(Component original) {
        return AuroraConfig.get().tabListHideFooter ? null : original;
    }

    @ModifyConstant(
            method = "extractRenderState",
            constant = @Constant(intValue = -2147483648)
    )
    private int aurora$customPanelFill(int original) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.tabListCustomBackground) return original;
        return TabListStyle.panelFillColor(cfg.tabListBgOpacity);
    }

    @ModifyExpressionValue(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundColor(I)I")
    )
    private int aurora$customRowFill(int original) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.tabListCustomBackground) return original;
        return TabListStyle.rowFillColor(cfg.tabListBgOpacity);
    }

    @Inject(
            method = "extractPingIcon",
            at = @At("HEAD"),
            cancellable = true
    )
    private void aurora$replacePingAndHighlightSelf(
            GuiGraphicsExtractor ctx,
            int width,
            int x,
            int y,
            PlayerInfo entry,
            CallbackInfo ci
    ) {
        AuroraConfig cfg = AuroraConfig.get();
        Minecraft client = Minecraft.getInstance();

        if (cfg.tabListHighlightSelf && client != null && client.player != null && entry != null) {
            if (client.player.getUUID().equals(entry.getProfile().id())) {
                int accent = ThemeManager.color(ThemeToken.ACCENT);
                ctx.fill(x, y, x + width, y + 8, TabListStyle.selfRowWashColor(accent));
                ctx.fill(x - 1, y, x, y + 8, TabListStyle.selfRowEdgeColor(accent));
            }
        }

        if (!cfg.tabPingEnabled) return;
        if (client == null || client.font == null) return;

        int latency = entry.getLatency();
        String text = TabListStyle.formatPing(latency, cfg.tabPingShowMs);
        // For unknown/offline latency, just cancel and draw nothing.
        if (text == null) {
            ci.cancel();
            return;
        }

        int color = com.aurora.client.hud.module.PingModule.pingColor(latency);
        int textW = client.font.width(text);

        // Right-align the number within the row so it sits cleanly at the right edge.
        int drawX = x + width - textW;
        // Mark this as a HUD draw: in AURORA_ONLY the tab-list ping number
        // stays vanilla like the rest of the in-game HUD; All Text still
        // styles it (owner ruling 2026-11-17).
        com.aurora.client.ui.util.AuroraFontRenderer.setDrawingHud(true);
        try {
            ctx.text(client.font, text, drawX, y, color, cfg.tabPingShadow);
        } finally {
            com.aurora.client.ui.util.AuroraFontRenderer.setDrawingHud(false);
        }

        ci.cancel();
    }
}
