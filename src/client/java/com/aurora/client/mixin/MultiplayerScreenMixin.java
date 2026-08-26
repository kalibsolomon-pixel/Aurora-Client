package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a "Refresh All" button to the Multiplayer screen and auto-refreshes
 * pings on a configurable interval. Vanilla pings every server once on screen
 * open and never again ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â Aurora keeps them current.
 */
@Mixin(JoinMultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {

    @Shadow
    protected ServerSelectionList serverSelectionList;
    @Shadow
    private ServerList servers;

    @Unique
    private long aurora$lastRefreshTime;

    protected MultiplayerScreenMixin(Component title) {
        super(title);
    }

    // TEMPORARILY DISABLED ----------------------------------------------------
    // The "Refresh All" button + auto-refresh tick are turned off while we
    // isolate a multiplayer-screen bug (server IPs not saving / general
    // breakage). The injects still apply but their bodies are no-ops, so
    // vanilla's screen runs unmodified by Aurora here.
    //
    // To re-enable: restore the original method bodies. Original logic is
    // preserved in version control.

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void aurora$addRefreshButton(CallbackInfo ci) {
        // disabled
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void aurora$autoRefreshTick(CallbackInfo ci) {
        // disabled
    }

    /**
     * Re-pings every saved server. We do this by clearing each entry's
     * cached ping data and asking vanilla to re-resolve, which is exactly
     * what happens on initial screen open. Implementation: re-construct
     * the server list widget so vanilla's pinger fires fresh.
     */
    @Unique
    private void aurora$refreshAllPings() {
        if (serverSelectionList == null || servers == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;

        // Reset each ServerData's cached state so vanilla re-pings on next render.
        for (int i = 0; i < servers.size(); i++) {
            ServerData info = servers.get(i);
            info.ping = -2L;          // -2 = "pinging" (vanilla convention)
            info.version = null;
            info.protocol = 0;
            info.motd = null;
            info.playerList = java.util.Collections.emptyList();
        }
        // Rebuild the widget rows so they re-trigger their pinger.
        serverSelectionList.updateOnlineServers(servers);
    }
}