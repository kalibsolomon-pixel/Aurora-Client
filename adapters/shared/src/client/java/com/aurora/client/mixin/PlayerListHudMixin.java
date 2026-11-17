package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
            method = "extractPingIcon",
            at = @At("HEAD"),
            cancellable = true
    )
    private void aurora$replacePingWithNumber(
            GuiGraphicsExtractor ctx,
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
        // Mark this as a HUD draw: in AURORA_ONLY the tab-list ping number
        // stays vanilla like the rest of the in-game HUD; All Text still
        // styles it (owner ruling 2026-11-17).
        com.aurora.client.ui.util.AuroraFontRenderer.setDrawingHud(true);
        try {
            ctx.text(client.font, Component, drawX, y, color, false);
        } finally {
            com.aurora.client.ui.util.AuroraFontRenderer.setDrawingHud(false);
        }

        ci.cancel();
    }
}
