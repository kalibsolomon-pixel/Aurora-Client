package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla ping-bars icon with a colored ping number in the tab list.
 * Cancels the vanilla render at HEAD so no icon is drawn, then draws our number
 * right-aligned to where the icon would have ended.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerListHudMixin {

    @Inject(
            method = "renderPingIcon",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void aurora$replacePingWithNumber(
            GuiGraphics ctx,
            int width,
            int x,
            int y,
            PlayerInfo entry,
            CallbackInfo ci
    ) {
        if (!AuroraConfig.get().tabPingEnabled) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return;

        int latency = entry.getLatency();
        // For unknown/offline latency, just cancel and draw nothing.
        if (latency < 0) {
            ci.cancel();
            return;
        }

        String Component = Integer.toString(latency);
        int color = com.aurora.client.hud.module.PingModule.pingColor(latency);
        int textW = client.font.width(Component);

        // Right-align the number within the icon slot so it sits where the
        // icon's right edge was. (x + width) is the right edge of the slot.
        int drawX = x + width - textW;
        // Flag this as an Aurora-owned render so the configured custom font
        // applies to our ping number in AURORA_ONLY mode.
        com.aurora.client.ui.util.AuroraFontRenderer.setRenderingAuroraUI(true);
        try {
            ctx.drawString(client.font, Component, drawX, y, color, false);
        } finally {
            com.aurora.client.ui.util.AuroraFontRenderer.setRenderingAuroraUI(false);
        }

        ci.cancel();
    }
}