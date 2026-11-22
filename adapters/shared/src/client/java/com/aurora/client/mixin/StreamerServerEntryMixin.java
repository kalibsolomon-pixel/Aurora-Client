package com.aurora.client.mixin;

import com.aurora.client.util.StreamerMode;
import com.aurora.client.util.StreamerPrivacy;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Masks server entries whose title is a raw IP or hostname on the Multiplayer
 * server list while Streamer Mode's Hide Server Address option is active,
 * restoring the real name right after drawing so {@code servers.dat} is never
 * altered.
 */
@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class StreamerServerEntryMixin {

    @Shadow private ServerData serverData;
    @Unique private String aurora$savedServerName;

    @Inject(method = "extractContent", at = @At("HEAD"))
    private void aurora$maskServerNameBeforeDraw(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                 boolean hovered, float partialTick, CallbackInfo ci) {
        aurora$savedServerName = null;
        if (!StreamerMode.hideServerAddress() || serverData == null) return;
        if (StreamerPrivacy.looksLikeServerAddress(serverData.name)) {
            aurora$savedServerName = serverData.name;
            serverData.name = StreamerPrivacy.HIDDEN_SERVER_LABEL;
        }
    }

    @Inject(method = "extractContent", at = @At("RETURN"))
    private void aurora$restoreServerNameAfterDraw(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                   boolean hovered, float partialTick, CallbackInfo ci) {
        if (aurora$savedServerName != null && serverData != null) {
            serverData.name = aurora$savedServerName;
            aurora$savedServerName = null;
        }
    }
}
