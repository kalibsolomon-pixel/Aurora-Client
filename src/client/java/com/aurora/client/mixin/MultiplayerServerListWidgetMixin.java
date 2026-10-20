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
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.resources.Identifier;

/**
 * Replaces vanilla's ping bars icon with a numerical ping value (e.g. "23ms").
 * Vanilla's ping/pinging bars are simply not drawn (the incompatible and
 * unreachable status icons still are); the label sits in the gutter to the
 * right of the entry, past any "9999/9999" player count.
 *
 * Player count is not added here because vanilla already renders it.
 */
@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class MultiplayerServerListWidgetMixin {

    @Shadow @Final private ServerData serverData;

    /** Skips the status icon's blit when it is one of the ping / pinging bar sprites. */
    @WrapWithCondition(method = "renderContent", require = 0, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private boolean aurora$hidePingBars(GuiGraphics ctx, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
        return !aurora$isPingBars(sprite);
    }

    /** {@code server_list/ping_N} and {@code server_list/pinging_N} — the bars the label replaces. */
    @org.spongepowered.asm.mixin.Unique
    private static boolean aurora$isPingBars(Identifier sprite) {
        return sprite != null && sprite.getNamespace().equals("minecraft") && sprite.getPath().startsWith("server_list/ping");
    }

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