package com.aurora.client.mixin;

import com.aurora.client.util.StreamerMode;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Redacts the local player's username, online opponents' usernames (when
 * enabled), and server addresses from incoming chat messages while Streamer
 * Mode is active.
 */
@Mixin(ChatComponent.class)
public abstract class StreamerChatMixin {

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Component aurora$redactIncomingChat(Component message) {
        return StreamerMode.redactComponent(message);
    }
}
