package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import com.aurora.client.media.MediaAction;
import com.aurora.client.media.SystemMediaBridge;
import com.aurora.client.util.AuroraKeybinds;
import net.minecraft.client.Minecraft;

/**
 * Handles in-game media control keybinds (cross-platform) and drives the
 * Windows-first Now Playing GSMTC polling heartbeat.
 */
public class MediaControlsFeature implements Feature {

    @Override public String id() { return "media_controls"; }

    @Override
    public void onTick(Minecraft client) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.mediaControlsEnabled) {
            SystemMediaBridge.tickPoll(false);
            return;
        }
        if (AuroraKeybinds.consumePress("key.aurora.media_play_pause")) {
            SystemMediaBridge.dispatch(MediaAction.PLAY_PAUSE);
        }
        if (AuroraKeybinds.consumePress("key.aurora.media_next")) {
            SystemMediaBridge.dispatch(MediaAction.NEXT);
        }
        if (AuroraKeybinds.consumePress("key.aurora.media_prev")) {
            SystemMediaBridge.dispatch(MediaAction.PREVIOUS);
        }
        if (AuroraKeybinds.consumePress("key.aurora.media_vol_up")) {
            SystemMediaBridge.dispatch(MediaAction.VOLUME_UP);
        }
        if (AuroraKeybinds.consumePress("key.aurora.media_vol_down")) {
            SystemMediaBridge.dispatch(MediaAction.VOLUME_DOWN);
        }
        SystemMediaBridge.tickPoll(cfg.mediaNowPlayingHudEnabled);
    }
}
