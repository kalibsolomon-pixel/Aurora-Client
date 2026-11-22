package com.aurora.client.mixin;

import com.aurora.client.util.StreamerMode;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Redacts raw server addresses from the connecting status screen while
 * Streamer Mode's Hide Server Address option is active.
 */
@Mixin(ConnectScreen.class)
public abstract class StreamerConnectScreenMixin {

    @ModifyVariable(method = "updateStatus", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component aurora$redactConnectStatus(Component status) {
        return StreamerMode.redactServerComponent(status);
    }
}
