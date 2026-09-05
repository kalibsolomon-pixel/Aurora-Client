package com.aurora.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla's ping bars icon with a numerical ping value (e.g. "23ms")
 * in the same screen position. The icon is occluded by drawing a small filled
 * rectangle over it (matching the entry's hover state isn't worth it Ã¢ the
 * vanilla list rows don't have a strong background, so the cover blends in).
 *
 * Player count is not added here because vanilla already renders it.
 */
@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class MultiplayerServerListWidgetMixin {

    @Shadow @Final private ServerData serverData;

    @Inject(method = "renderContent", at = @At("TAIL"), require = 0)
    private void aurora$replacePingIcon(
            GuiGraphics ctx, int mouseX, int mouseY, boolean hovered, float tickDelta,
            CallbackInfo ci) {

        if (serverData == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return;

        // Pull row geometry from the Entry's own layout getters (1.21.11 stopped
        // passing top/left/width/height into renderContent). The getters live
        // on AbstractSelectionList.Entry, two levels above OnlineServerEntry, and
        // Mixin only resolves @Shadow methods against the target class's own
        // method table, so shadowing them fails at apply time. Call the public
        // inherited methods through a cast to the public ServerSelectionList.Entry
        // superclass instead (AbstractSelectionList.Entry itself is protected).
        ServerSelectionList.Entry self = (ServerSelectionList.Entry) (Object) this;
        int x = self.getContentX();
        int y = self.getContentY();
        int entryWidth = self.getContentWidth();

        long ping = serverData.ping;

        // Vanilla ping icon sits at the right edge. Player count text sits
        // to its left and can be up to ~50px wide ("9999/9999"). To avoid
        // overlap, we cover only the small icon area and place our label
        // OUTSIDE the entry's right edge in the gutter, where there's space.
        int iconRight = x + entryWidth - 5;
        int iconLeft  = iconRight - 10;
        int iconTop   = y + 1;
        int iconBottom = iconTop + 8;
        ctx.fill(iconLeft - 1, iconTop - 1, iconRight + 1, iconBottom + 1, 0xFF000000);

        String label;
        int color;
        if (ping == -2L) {
            label = "...";
            color = 0xFFAAAAAA;
        } else if (ping <= 0) {
            label = "?";
            color = 0xFF888888;
        } else {
            label = ping + "ms";
            color = ping < 50 ? 0xFF55FF55
                    : ping < 150 ? 0xFFFFFF55
                      : 0xFFFF5555;
        }

        // Place label in the gutter to the right of the entry, well past
        // any "9999/9999"-style player count.
        int textX = x + entryWidth + 6;
        int textY = y + 1;
        // Flag this as an Aurora-owned render so the configured custom font
        // applies to our latency label in AURORA_ONLY mode.
        com.aurora.client.ui.util.AuroraFontRenderer.setRenderingAuroraUI(true);
        try {
            ctx.drawString(client.font, label, textX, textY, color, false);
        } finally {
            com.aurora.client.ui.util.AuroraFontRenderer.setRenderingAuroraUI(false);
        }
    }
}